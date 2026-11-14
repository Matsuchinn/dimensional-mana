package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 秽蚀蠹的事件接线：死亡蜕皮的致命伤拦截、三条生成路径、以及动物类自然生成的地面判定。
 *
 * <p><b>三条生成路径</b>：</p>
 * <ol>
 *   <li><b>末地传送门遗迹</b>：遗迹里那个原版蠹虫刷怪笼吐出来的蠹虫，只要周围有一圈末地传送门框架，
 *       就整只换成秽蚀蠹（{@link #onFinalizeSpawn}）；另外每隔 20 秒还有一轮「遗迹里自己爬出来」
 *       的补充生成（{@link #tickLevel}），否则玩家把刷怪笼拆了遗迹就彻底空了。</li>
 *   <li><b>魔晶矿</b>：魔素浓度 ≥ 15 的区块里挖魔晶矿，8% 概率当场钻一只出来，
 *       周围 32 格最多 10 只（{@link #onBlockBreak}）。</li>
 *   <li><b>蚀魔荒原地表</b>：在群系 JSON 里登记成 creature 类的少量自然生成，白天也站得住。</li>
 * </ol>
 */
public final class CorruptedSilverfishEvents {
    /** 挖矿钻出的魔素浓度门槛。 */
    static final double MINE_CONCENTRATION = 15.0D;
    /** 挖矿钻出的概率：8%。 */
    static final float MINE_CHANCE = 0.08F;
    /** 挖矿点 32 格内最多同时存在多少只。 */
    static final int MINE_LOCAL_CAP = 10;
    /** 遗迹刷怪笼 16 格内最多同时存在多少只（原版刷怪笼数完自己这一类就不刷了，替换后会一直吐）。 */
    private static final int SPAWNER_LOCAL_CAP = 5;
    /** 末地传送门框架的搜索半径（格）。 */
    private static final int PORTAL_SCAN_RADIUS = 16;
    /** 遗迹自然补充生成的间隔（刻）：20 秒看一轮。 */
    private static final int PORTAL_SPAWN_INTERVAL = 400;
    /** 每一轮真的补一只的概率。 */
    private static final float PORTAL_SPAWN_CHANCE = 0.35F;
    /** 遗迹 24 格内最多同时存在多少只。 */
    private static final int PORTAL_LOCAL_CAP = 4;

    /** 算作「魔晶矿」的五种矿石方块 id。 */
    private static final String[] MANA_ORES = {
            "lesser_mana_crystal_ore", "medium_mana_crystal_ore", "advanced_mana_crystal_ore",
            "supreme_mana_crystal_ore", "special_mana_crystal_cluster"
    };

    private CorruptedSilverfishEvents() { }

    // ---------------------------------------------------------------- 死亡蜕皮
    /**
     * 致命伤拦截：前三阶的致命一击被【死亡蜕皮】顶掉，第四次才真的死。
     *
     * <p>{@link LethalDamage#isLethal} 的必杀（/kill、虚空）不参与：不然这只会变成打不死的怪。</p>
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof CorruptedSilverfishEntity silverfish)) return;
        if (silverfish.level().isClientSide) return;
        if (!(silverfish.level() instanceof ServerLevel level)) return;
        float amount = event.getAmount();
        if (amount <= 0.0F || amount < silverfish.getHealth()) return;
        if (LethalDamage.isLethal(event.getSource(), amount)) return;
        if (!silverfish.canMolt()) return;
        event.setCanceled(true);
        silverfish.molt(level);
    }

    // ---------------------------------------------------------------- 自然生成的地面判定
    /** 动物类的地面生成判定：踩实心地面、不看光照（荒原白天也刷）。 */
    static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(GeneratedMod.CORRUPTED_SILVERFISH.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CorruptedSilverfishEntity::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    // ---------------------------------------------------------------- 末地传送门遗迹
    /**
     * 遗迹刷怪笼替换：原版蠹虫刷怪笼吐出来的蠹虫，只要离末地传送门框架够近，就换成秽蚀蠹。
     *
     * <p>数量闸门：16 格内已经有 {@value #SPAWNER_LOCAL_CAP} 只就不再替换，直接取消这一次生成。
     * 原版刷怪笼的「附近同类上限」数的是它自己的实体类型，替换之后它数不到任何同类，
     * 不拦的话会在遗迹里无限吐怪。</p>
     */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled()) return;
        Mob mob = event.getEntity();
        if (mob.getClass() != Silverfish.class) return;
        if (event.getSpawnType() != MobSpawnType.SPAWNER && event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (!nearEndPortal(level, mob.blockPosition())) return;
        // r105 权重：秽蚀蠹调到 0 时遗迹刷怪笼保持原版蠹虫，调低时替换率按比例下降。
        if (!MobSpawnWeights.roll(level.getRandom(), 1.0F, "corrupted_silverfish")) return;
        if (localCount(level, mob.blockPosition(), 16.0D) >= SPAWNER_LOCAL_CAP) {
            event.setSpawnCancelled(true);
            return;
        }
        Mob replacement = GeneratedMod.CORRUPTED_SILVERFISH.get().create(level);
        if (replacement == null) return;
        event.setSpawnCancelled(true);
        replacement.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        // 替换概率上面已经乘过一次权重，这里不再让 NATURAL 权重门二次衰减。
        MobSpawnWeights.bypassing(() -> {
            replacement.finalizeSpawn(level, event.getDifficulty(), event.getSpawnType(), null);
            if (replacement instanceof CorruptedSilverfishEntity bug) bug.emergeFromGround();
            level.addFreshEntity(replacement);
        });
    }

    /**
     * 遗迹的「自己爬出来」：每 20 秒一轮，玩家身边 16 格内有末地传送门框架时，
     * 35% 概率在旁边补一只（24 格内最多 4 只）。
     */
    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % PORTAL_SPAWN_INTERVAL != 0) return;
        if (!SpawnControl.hostileSpawningAllowed(level)) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) continue;
            RandomSource random = player.getRandom();
            // r105 权重：遗迹补刷概率同样按秽蚀蠹权重缩放。
            if (!MobSpawnWeights.roll(random, PORTAL_SPAWN_CHANCE, "corrupted_silverfish")) continue;
            BlockPos portal = findPortalFrame(level, player.blockPosition());
            if (portal == null) continue;
            if (localCount(level, portal, 24.0D) >= PORTAL_LOCAL_CAP) continue;
            BlockPos spot = findGround(level, portal, random);
            if (spot == null) continue;
            CorruptedSilverfishEntity bug = GeneratedMod.CORRUPTED_SILVERFISH.get().create(level);
            if (bug == null) continue;
            bug.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            // 这是模组自己补的一只（概率已乘权重），不让 NATURAL 权重门再衰减一次。
            MobSpawnWeights.bypassing(() -> {
                bug.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.NATURAL, null);
                level.addFreshEntity(bug);
                bug.emergeFromGround();
            });
        }
    }

    /** 附近有没有末地传送门框架（末地传送门遗迹的唯一硬标志）。 */
    private static boolean nearEndPortal(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -PORTAL_SCAN_RADIUS; dx <= PORTAL_SCAN_RADIUS; dx += 2) {
            for (int dy = -8; dy <= 8; dy += 2) {
                for (int dz = -PORTAL_SCAN_RADIUS; dz <= PORTAL_SCAN_RADIUS; dz += 2) {
                    cursor.setWithOffset(center, dx, dy, dz);
                    if (!level.isLoaded(cursor)) continue;
                    if (level.getBlockState(cursor).is(Blocks.END_PORTAL_FRAME)) return true;
                }
            }
        }
        return false;
    }

    private static BlockPos findPortalFrame(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -PORTAL_SCAN_RADIUS; dx <= PORTAL_SCAN_RADIUS; dx += 2) {
            for (int dy = -8; dy <= 8; dy += 2) {
                for (int dz = -PORTAL_SCAN_RADIUS; dz <= PORTAL_SCAN_RADIUS; dz += 2) {
                    cursor.setWithOffset(center, dx, dy, dz);
                    if (!level.isLoaded(cursor)) continue;
                    if (level.getBlockState(cursor).is(Blocks.END_PORTAL_FRAME)) return cursor.immutable();
                }
            }
        }
        return null;
    }

    /** 在传送门框架旁边找一个「脚下是实心方块、自己这两格空着」的落点。 */
    private static BlockPos findGround(ServerLevel level, BlockPos near, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos base = near.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            for (int dy = 4; dy >= -5; dy--) {
                BlockPos candidate = base.offset(0, dy, 0);
                if (!level.isLoaded(candidate)) break;
                if (!level.isEmptyBlock(candidate)) continue;
                if (!level.getBlockState(candidate.below()).blocksMotion()) continue;
                return candidate;
            }
        }
        return null;
    }

    private static int localCount(ServerLevel level, BlockPos pos, double radius) {
        return level.getEntitiesOfClass(CorruptedSilverfishEntity.class,
                new AABB(pos).inflate(radius), bug -> bug.isAlive()).size();
    }

    // ---------------------------------------------------------------- 挖魔晶矿
    /**
     * 挖魔晶矿：魔素浓度 ≥ 15 的区块里有 8% 概率直接钻一只出来（周围 32 格最多 10 只）。
     *
     * <p>只认五种魔晶矿石，别的方块（磨制魔晶块、魔晶簇结构方块）挖了不算。</p>
     */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (!isManaOre(state)) return;
        if (ManaConcentration.at(level, pos) < MINE_CONCENTRATION) return;
        if (level.random.nextFloat() >= MINE_CHANCE) return;
        if (localCount(level, pos, 32.0D) >= MINE_LOCAL_CAP) return;
        CorruptedSilverfishEntity bug = GeneratedMod.CORRUPTED_SILVERFISH.get().create(level);
        if (bug == null) return;
        bug.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        bug.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.TRIGGERED, null);
        level.addFreshEntity(bug);
        bug.emergeFromGround();
        // 顺手把它咬的那一口矿尘补上，看起来就是「从这块矿里钻出来的」。
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                24, 0.3D, 0.3D, 0.3D, 0.06D);
    }

    private static boolean isManaOre(BlockState state) {
        for (String id : MANA_ORES) {
            var holder = GeneratedMod.MOD_BLOCKS.get(id);
            if (holder != null && state.is(holder.get())) return true;
        }
        return false;
    }
}
