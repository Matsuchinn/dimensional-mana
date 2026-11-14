package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * 蚀魔荒原的刷怪接线。
 *
 * <p>需求里写的是「<b>白天也会生成少量变异生物</b>」。原版那套自然刷怪把怪物锁死在黑暗里，
 * 白天在荒原上走一整圈只有蜘蛛和苦力怕，变异体一只都碰不到——所以这里是两道路子一起上：</p>
 *
 * <ol>
 *   <li><b>放开原版刷怪判定</b>（{@link #registerSpawnPlacements}）：晶化行尸与魔蚀骸骨是
 *       MONSTER 类别，默认谓词 {@code checkMonsterSpawnRules} 要求环境全黑。这里换成
 *       {@link Monster#checkAnyLightMonsterSpawnRules}（不看光照，只看地形与方块），
 *       原版刷怪器在白天也愿意刷它们。</li>
 *   <li><b>自己补一个「白天小股」刷怪器</b>（{@link #tickLevel}）：光靠第 1 步并不稳，
 *       白天原版怪物刷新上限被夜里剩下的怪占着，加上刷怪器本身的随机性，实际很难碰到。
 *       所以每隔一阵子、在玩家所在荒原周围 24～48 格找一个能站人的地面，补 1 只变异体，
 *       玩家 64 格内最多同时 3 只，保证「少量但确实有」。</li>
 * </ol>
 *
 * <p>防晒：晶化行尸已覆写 {@code isSunBurnTick()} 返回 false（魔蚀骸骨本来就不烧），
 * 白天站得住。</p>
 *
 * <p>两道路子都过 {@link SpawnControl#hostileSpawningAllowed} 这道统一闸门，
 * 玩家关掉 {@code doMobSpawning} 或调和平难度后都不会再刷。</p>
 */
public final class ErosionWastelandSpawning {
    /** 白天补刷的间隔：400 刻 = 20 秒看一轮。 */
    private static final int DAY_SPAWN_INTERVAL = 400;
    /** 每一轮真的补刷的概率，压得低一点才是「少量」。（r91：0.45 → 0.6，玩家反映荒原太冷清。） */
    private static final float DAY_SPAWN_CHANCE = 0.6F;
    /** 玩家 64 格内最多同时存在这么多只白天补刷的变异体。 */
    private static final int DAY_LOCAL_CAP = 3;
    /**
     * 秽蚀蠹单独一条补刷路线：附近 48 格最多同时 2 只。
     *
     * <p>它本来是「随便补一只变异体」里的 4/10，名额一被行尸骸骨占满就再也不露头；
     * 魔素史莱姆数量压下来以后，这里再给它一个独立的小名额，荒原上就稳定有「少量」秽蚀蠹。</p>
     */
    private static final int SILVERFISH_LOCAL_CAP = 2;
    /** 每轮先按这个概率试着补一只秽蚀蠹，没补上再走变异体的名额判定。 */
    private static final float SILVERFISH_SPAWN_CHANCE = 0.35F;
    /** 补刷点离玩家的水平距离区间（格）：太近会突然糊脸，太远又看不见。 */
    private static final double DAY_MIN_DISTANCE = 24.0;
    private static final double DAY_MAX_DISTANCE = 48.0;

    private ErosionWastelandSpawning() { }

    /** 由 GeneratedMod 构造器注册到模组总线。 */
    static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(GeneratedMod.CRYSTALLIZED_ZOMBIE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkAnyLightMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GeneratedMod.CORRUPTED_SKELETON.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkAnyLightMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** 由 GeneratedMod 构造器注册到 NeoForge 总线：荒原白天的「小股变异体」补刷。 */
    static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % DAY_SPAWN_INTERVAL != 0) return;
        // 只补白天的份：夜里原版刷怪器本来就忙，再补就把荒原塞满了。
        if (!level.isDay()) return;
        if (!SpawnControl.hostileSpawningAllowed(level)) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            // 玩家得站在荒原里；站在别的群系边上不替他刷荒原的怪。
            if (!CorruptedWastelandBiome.is(level.getBiome(player.blockPosition()))) continue;
            RandomSource random = player.getRandom();
            // r106：荒原白天补刷也走「开局爬坡 × 荒原倍率」，头几天荒原不会立刻挤满变异体。
            // r107：荒原本身浓度就 13.5，按位置判断时永远走满强度，不再被开局爬坡压低。
            if (!SpawnPacing.roll(level, random, DAY_SPAWN_CHANCE, SpawnPacing.wastelandRate(),
                    player.blockPosition())) continue;
            // 先给秽蚀蠹留名额：它是荒原生态里最稀疏的一环，被变异体的上限挤掉就整片荒原都看不见。
            // r105 权重：这里的补刷概率也跟着配置里的秽蚀蠹权重缩放。
            // 概率 = 原来的 35% × 荒原倍率 × 开局爬坡 × 秽蚀蠹自己的权重。
            float silverfishChance = SILVERFISH_SPAWN_CHANCE
                    * SpawnPacing.multiplier(level, SpawnPacing.wastelandRate(), player.blockPosition());
            if (countNearbySilverfish(level, player) < SILVERFISH_LOCAL_CAP
                    && MobSpawnWeights.roll(random, silverfishChance, "corrupted_silverfish")
                    && spawnDayMob(level, player, random, GeneratedMod.CORRUPTED_SILVERFISH.get())) continue;
            if (countNearbyMutants(level, player) >= DAY_LOCAL_CAP) continue;
            spawnDayMutant(level, player, random);
        }
    }

    /**
     * 玩家 64 格内现有的魔素变异体数量。
     *
     * <p>r285：以前只数晶化行尸和魔蚀骸骨，聚魔爆裂体、魔蚀织网者、虚空蚀影完全不算，
     * 于是"最多 3 只"的闸门对它们形同虚设；再加上矿簇守卫也在这片范围里，
     * 玩家看到的变异体就远超设计。现在五种变异体一起数，矿簇守卫也算在内。</p>
     */
    private static int countNearbyMutants(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0),
                mob -> mob.isAlive() && CrystalClusterData.isManaMutant(mob)).size();
    }

    /** 玩家 48 格内现有的秽蚀蠹数量。 */
    private static int countNearbySilverfish(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(CorruptedSilverfishEntity.class,
                player.getBoundingBox().inflate(48.0), mob -> mob.isAlive()).size();
    }

    /**
     * 变异三兄弟按 4 : 3 : 3 抽——r91 把秽蚀蠹也拉进白昼轮换，别只看得见行尸和骸骨。
     *
     * <p>r105：这张比例表改成「基准份额 × 配置权重」，三种都被调到 0 时这一轮就不补刷。</p>
     */
    private static void spawnDayMutant(ServerLevel level, ServerPlayer player, RandomSource random) {
        List<MobSpawnWeights.Candidate<EntityType<? extends Mob>>> pool = List.of(
                MobSpawnWeights.candidate(GeneratedMod.CORRUPTED_SILVERFISH.get(), "corrupted_silverfish", 4),
                MobSpawnWeights.candidate(GeneratedMod.CRYSTALLIZED_ZOMBIE.get(), "crystallized_zombie", 3),
                MobSpawnWeights.candidate(GeneratedMod.CORRUPTED_SKELETON.get(), "corrupted_skeleton", 3));
        EntityType<? extends Mob> type = MobSpawnWeights.pick(random, pool);
        if (type == null) return;
        spawnDayMob(level, player, random, type);
    }

    /** 在玩家周围找一块能站人的荒原地面，塞一只指定种类的变异生物进去。 */
    private static boolean spawnDayMob(ServerLevel level, ServerPlayer player, RandomSource random,
                                       EntityType<? extends Mob> type) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = DAY_MIN_DISTANCE + random.nextDouble() * (DAY_MAX_DISTANCE - DAY_MIN_DISTANCE);
            BlockPos probe = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0.0,
                    player.getZ() + Math.sin(angle) * distance);
            // r107：只在已经加载的区块里找落点，不为了补一只怪去生成远处地形。
            if (!SectionScan.loadedColumn(level, probe.getX(), probe.getZ())) continue;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
            if (!CorruptedWastelandBiome.is(level.getBiome(pos))) continue;
            if (!hasRoom(level, pos)) continue;
            Mob mob = type.create(level);
            if (mob == null) continue;
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            if (level.addFreshEntity(mob)) {
                // 蠹虫补刷要有「破土」的登场：尘土 + 音效，不然凭空冒出来很出戏。
                if (mob instanceof CorruptedSilverfishEntity silverfish) silverfish.emergeFromGround();
                return true;
            }
        }
        return false;
    }

    /** 脚下是实心地面、身上两格是空的。 */
    private static boolean hasRoom(ServerLevel level, BlockPos pos) {
        // 只读同一列的几格；这一列的区块没加载完就直接放弃，不替玩家生成地形。
        if (!ChunkSafety.ready(level, pos)) return false;
        return level.getBlockState(pos.below()).isSolid()
                && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above());
    }
}
