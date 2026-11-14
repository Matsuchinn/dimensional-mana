package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.Optional;

/** 环境、天气、矿脉与巨大声响共同驱动的元素史莱姆生成系统。 */
public final class ElementalSlimeSpawning {
    /** 基础元素生物应当常见：每个玩家（包括旁观测试者）每 4 秒获得一次环境刷新机会。 */
    private static final int CHECK_INTERVAL = 80;
    private static final float ATTEMPT_CHANCE = 0.85F;
    /**
     * 水和木史莱姆的额外稀释概率：抽中这两种时，有两成五的几率这一次直接不刷。
     * 它们分别是海洋 / 河流和森林 / 平原的默认元素，几乎走两步就能撞见；这里只降数量，
     * 不改变群系对应关系（海边还是水史莱姆、森林还是木史莱姆，只是密度低一些）。
     */
    private static final float COMMON_SLIME_THIN_CHANCE = 0.25F;
    private static final int LOCAL_CAP = 16;
    private static final double LOCAL_RADIUS = 56.0;
    /**
     * 蚀魔荒原的生态名单只有四种：变异体、魔素史莱姆、秽蚀蠹、秽蚀蜂。
     *
     * <p>其它元素史莱姆在这片群系里几乎不出现——木、风两种本来是最容易抽中的
     * （森林/平原兜底 + 高空风点），玩家在荒原上看到的却是一片绿史莱姆和风史莱姆。
     * 抽中以后只留 4% 的几率真的刷出来。</p>
     */
    private static final float WASTELAND_OTHER_SLIME_CHANCE = 0.04F;
    /**
     * 魔素史莱姆是荒原的「少量点缀」，不是主角：抽中以后再按这个几率放弃这一次。
     *
     * <p>以前魔素史莱姆几乎占满了整个荒原的刷怪容量（矿簇、魔晶洞穴两个判定在荒原里
     * 一抽一个准），结果本该少量生成的秽蚀蠹被挤得刷不出来。</p>
     */
    private static final float WASTELAND_MANA_THIN_CHANCE = 0.70F;
    /** 荒原里元素史莱姆的本地保有量上限：身边一两三只就是「少量」，其余容量留给变异体。 */
    private static final int WASTELAND_LOCAL_CAP = 3;
    private static final double WASTELAND_LOCAL_RADIUS = 48.0;
    /** 荒原里的魔晶洞穴也按荒原的规矩补刷，顶多补到两三只。 */
    private static final int WASTELAND_CAVE_CAP = 3;
    /**
     * 沙漠里挑中土史莱姆的比例：沙漠不刷木史莱姆，改成有概率出土壤史莱姆。
     * 没中签的那几次直接放弃，所以沙漠比森林稀疏一些，不会变成一片土史莱姆。
     */
    private static final float DESERT_EARTH_CHANCE = 0.55F;
    private static final int TRIES = 10;
    private static final int NORMAL_CAVE_CAP = 7;
    private static final int LARGE_CAVE_CAP = 14;
    /** 洞穴补刷离玩家至少这么远：洞穴很窄，贴在脸前刷出一只出来很吓人。 */
    private static final double CAVE_MIN_PLAYER_DISTANCE = 18.0;
    /**
     * 玩家海拔超过 100 时抽一次「高空风史莱姆」的概率。
     *
     * <p>以前是 50%，加上它们在玩家身边来去自由地飘，观感就是整片天空都是风史莱姆。
     * 现在压到 16%，再加上下面「附近最多几只」的上限，高空风景里只剩零星几只。</p>
     */
    private static final float WIND_HIGH_AIR_CHANCE = 0.16F;
    /** 附近 96 格内最多允许这么多只风史莱姆在飞；再多就不再往高空刷。 */
    private static final int WIND_HIGH_AIR_CAP = 5;
    private static final double WIND_HIGH_AIR_RADIUS = 96.0;
    /**
     * 高空落点离地表的高度：以前固定 +32，效果是「离地三十多格的一层漂浮史莱姆」。
     * 现在改成 8～18 格，它们是「屋顶上方掠过」的一群，而不是满天都是。
     */
    private static final int WIND_AIR_MIN_OFFSET = 8;
    private static final int WIND_AIR_MAX_OFFSET = 18;

    private ElementalSlimeSpawning() { }

    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // r290：反虚空是「绝对纯净空间」，不允许任何生命。它的超平坦地形会被这套系统
        // 误判成地下（头顶 256 格封印层挡着视线），兜底规则于是刷出土元素史莱姆——整段跳过。
        if (level.dimension().equals(AntiVoidSystem.ANTI_VOID)) return;
        WorldgenSpawnQueue.tick(level);
        if (level.getGameTime() % 20 != 0) return;
        for (ServerPlayer player : level.players()) {
            // 旁观模式也作为环境生成锚点：方便找生物，同时不会成为史莱姆的攻击目标。
            if (level.getGameTime() % 40 == 0 && !player.isSpectator()) tickJukeboxMusic(level, player, 40);
            if (level.getGameTime() % CHECK_INTERVAL != 0) continue;
            RandomSource random = player.getRandom();
            // r107：魔晶洞穴的定额补刷按洞穴自己的浓度算——洞里恒定 15 以上，
            // 属于「高魔素区域」，所以它不吃开局爬坡，先单独处理（补上了这一轮就结束）。
            // 玩家身边的「元素史莱姆总量倍率」仍然生效（调到 0 就彻底不刷、调 0.5 就少补一半），
            // 只是不再乘开局那几天的新手折扣。没有洞穴时这里只是几次列表查询，几乎不花钱。
            float caveRate = SpawnPacing.elementalRate();
            if (caveRate > 0.0F && random.nextFloat() < Math.min(1.0F, caveRate)
                    && topUpManaCave(level, player, random)) continue;
            // r106：每 4 秒一次的尝试再乘「开局爬坡 × 元素史莱姆总倍率」，
            // 新存档头几天几乎不刷，到设定的天数后回到正常量。
            // r107：位置一起传进去——玩家站在魔素浓度 > 12 的地方（矿簇、祭坛、晶洞）时
            // 不吃开局爬坡，那些高危区从第一天起就是满强度。
            if (!SpawnPacing.roll(level, random, ATTEMPT_CHANCE, SpawnPacing.elementalRate(),
                    player.blockPosition())) continue;
            // 蚀魔荒原的保有量单独算：这里只留少量魔素史莱姆，剩下的刷怪容量给变异体和秽蚀蠹。
            boolean wasteland = CorruptedWastelandBiome.is(level.getBiome(player.blockPosition()));
            int cap = wasteland ? WASTELAND_LOCAL_CAP : LOCAL_CAP;
            double radius = wasteland ? WASTELAND_LOCAL_RADIUS : LOCAL_RADIUS;
            if (localCount(level, player.position(), radius) >= cap) continue;
            SpawnPick pick = findPick(level, player, random);
            if (pick == null) continue;
            if (isCommonVariant(pick.variant()) && random.nextFloat() < COMMON_SLIME_THIN_CHANCE) continue;
            // r105 权重：抽中的这一种在配置里被调低时，按比例跳过这一次（0 = 彻底不刷）。
            if (!MobSpawnWeights.roll(random, 1.0F, pick.variant().entityId())) continue;
            spawn(level, pick.variant(), pick.pos(), pick.forcedTier(), 1 + random.nextInt(3),
                    false, pick.fluidSurface(), pick.airborne());
        }
    }

    /** 水和木史莱姆是最常见的两种地表元素，单独走一道数量稀释。 */
    private static boolean isCommonVariant(ElementalSlimeVariant variant) {
        return variant == ElementalSlimeVariant.WATER || variant == ElementalSlimeVariant.NATURE;
    }

    /** 普通洞穴固定 2～4 精英 + 1～3 其他；大型洞穴两项数量都翻倍。 */
    public static void spawnManaCave(ServerLevel level, BlockPos center, RandomSource random, boolean large) {
        int multiplier = large ? 2 : 1;
        int spread = large ? 13 : 7;
        int elites = (2 + random.nextInt(3)) * multiplier;
        for (int i = 0; i < elites; i++) {
            BlockPos spot = randomCaveFloor(level, center, random, spread, 3);
            if (spot == null) {
                // 洞顶太矮放不下 2 格高的精英体时，退一步至少补一只成熟体，别让整个洞穴空着。
                spot = randomCaveFloor(level, center, random, spread, 2);
                if (spot != null) spawnCave(level, spot, ElementalSlimeTier.MATURE);
                continue;
            }
            spawnCave(level, spot, ElementalSlimeTier.ELITE);
        }
        int others = (1 + random.nextInt(3)) * multiplier;
        for (int i = 0; i < others; i++) {
            BlockPos spot = randomCaveFloor(level, center, random, spread, 2);
            if (spot != null) spawnCave(level, spot, null);
        }
    }

    private static BlockPos randomCaveFloor(ServerLevel level, BlockPos center, RandomSource random, int spread, int clear) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos start = center.offset(random.nextInt(spread * 2 + 1) - spread, 4,
                    random.nextInt(spread * 2 + 1) - spread);
            BlockPos floor = findFloor(level, start, clear, 20);
            if (floor != null) return floor;
        }
        return null;
    }

    private static void spawnCave(ServerLevel level, BlockPos pos, ElementalSlimeTier forcedTier) {
        spawn(level, ElementalSlimeVariant.MANA, pos, forcedTier, 1, true, false, false);
    }

    /** 玩家靠近已记录洞穴时自动补齐；大型洞穴的保有量也是普通洞穴的两倍。 */
    private static boolean topUpManaCave(ServerLevel level, ServerPlayer player, RandomSource random) {
        Optional<BlockPos> nearest = ManaCaveData.get(level).nearest(player.blockPosition());
        if (nearest.isEmpty()) return false;
        BlockPos cave = nearest.get();
        if (cave.distSqr(player.blockPosition()) > 72 * 72) return false;
        boolean large = ManaCaveData.get(level).isLarge(cave);
        int cap = large ? LARGE_CAVE_CAP : NORMAL_CAVE_CAP;
        int guaranteedElites = large ? 4 : 2;
        // 蚀魔荒原里的洞穴只是荒原的一部分：那里魔素史莱姆只保留少量，不给精英、也不补满。
        if (CorruptedWastelandBiome.is(level.getBiome(cave))) {
            cap = Math.min(cap, WASTELAND_CAVE_CAP);
            guaranteedElites = 0;
        }
        int radius = large ? 40 : 28;
        int existing = level.getEntitiesOfClass(ElementalSlime.class, new AABB(cave).inflate(radius),
                slime -> slime.variant() == ElementalSlimeVariant.MANA).size();
        if (existing >= cap) return false;
        BlockPos spot = randomCaveFloor(level, cave, random, large ? 13 : 7, existing < guaranteedElites ? 3 : 2);
        if (spot == null) return false;
        // 洞穴里的史莱姆也不能贴着玩家的脸刷：18 格以内这个周期先不补（洞穴很窄，抓得住的）。
        if (spot.distSqr(player.blockPosition()) < CAVE_MIN_PLAYER_DISTANCE * CAVE_MIN_PLAYER_DISTANCE) return false;
        int count = Math.min(existing < guaranteedElites ? 2 : 1, cap - existing);
        spawn(level, ElementalSlimeVariant.MANA, spot,
                existing < guaranteedElites ? ElementalSlimeTier.ELITE : null, count, true, false, false);
        return true;
    }

    /** 闪电落地时 50% 生成 1～4 个雷史莱姆。 */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LightningBolt bolt)) return;
        // 雷史莱姆 50% 与“巨大声响召来音史莱姆”25% 是两次独立判定，不能串成 12.5%。
        // r105 权重：这两条事件召怪也按各自权重缩放概率。
        if (MobSpawnWeights.roll(level.random, 0.50F, ElementalSlimeVariant.LIGHTNING.entityId()))
            spawn(level, ElementalSlimeVariant.LIGHTNING, bolt.blockPosition(), null, 1 + level.random.nextInt(4));
        loudSound(level, bolt.position());
    }

    /** TNT 等爆炸属于巨大声响；25% 在 64～88 格外生成音史莱姆。 */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) loudSound(level, event.getExplosion().center());
    }

    /** 钟声与音符盒由声音事件识别；音符盒还累计驯服所需的 10 秒乐曲。 */
    public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String path = event.getSound().value().getLocation().getPath();
        Vec3 at = event.getPosition();
        if (path.contains("bell")) loudSound(level, at);
        if (path.contains("note_block")) musicPulse(level, at, 20, 200);
    }

    private static void loudSound(ServerLevel level, Vec3 source) {
        // r105 权重：巨响召来的音史莱姆同样受权重控制。
        if (!MobSpawnWeights.roll(level.random, 0.25F, ElementalSlimeVariant.SOUND.entityId())) return;
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double distance = 64.0 + level.random.nextDouble() * 24.0;
        int x = (int)Math.floor(source.x + Math.cos(angle) * distance);
        int z = (int)Math.floor(source.z + Math.sin(angle) * distance);
        // r107：落点在没加载的区块里就放弃这一声。以前这里直接问高度图，等于让游戏在主线程上
        // 去生成 64～88 格外的地形；钟声、爆炸一多就成了持续的卡顿来源。
        if (!SectionScan.loadedColumn(level, x, z)) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        spawn(level, ElementalSlimeVariant.SOUND, new BlockPos(x, y, z), null, 1);
    }

    /** 找唱片机用的方块判定：调色板预筛和实际扫描必须一致。 */
    private static final java.util.function.Predicate<BlockState> JUKEBOX = state -> state.is(Blocks.JUKEBOX);

    /** 唱片机检测的扫描半径（水平 16 格、垂直 6 格，和原来的搜索范围一致）。 */
    private static final int JUKEBOX_RANGE_XZ = 16;
    private static final int JUKEBOX_RANGE_Y = 6;

    private static void tickJukeboxMusic(ServerLevel level, Player player, int ticks) {
        BlockPos origin = player.blockPosition();
        // 覆盖到的 3×3 个区块必须都加载好，否则下面逐格读方块会把主线程卡在区块加载上。
        if (!ChunkSafety.readyArea(level,
                origin.offset(-JUKEBOX_RANGE_XZ, -JUKEBOX_RANGE_Y, -JUKEBOX_RANGE_XZ),
                origin.offset(JUKEBOX_RANGE_XZ, JUKEBOX_RANGE_Y, JUKEBOX_RANGE_XZ))) return;
        // 原版这里是 33×13×33 ≈ 1.4 万个方块逐格查一遍，一个唱片机都没有的普通场景也要付这笔钱。
        // 先问区块段的调色板：附近根本没有唱片机方块时直接返回。
        if (!sectionsMayHaveJukebox(level, origin)) return;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-JUKEBOX_RANGE_XZ, -JUKEBOX_RANGE_Y, -JUKEBOX_RANGE_XZ),
                origin.offset(JUKEBOX_RANGE_XZ, JUKEBOX_RANGE_Y, JUKEBOX_RANGE_XZ))) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.JUKEBOX) && state.getValue(JukeboxBlock.HAS_RECORD)) musicPulse(level, pos.getCenter(), ticks, 100);
        }
    }

    /**
     * 扫描范围覆盖到的区块段里，有没有任何一段「可能出现」唱片机方块。
     *
     * <p>只查已经加载的区块；没加载的区块本来也站不到人，不去为了看一眼而强行加载它。</p>
     */
    private static boolean sectionsMayHaveJukebox(ServerLevel level, BlockPos origin) {
        int minChunkX = (origin.getX() - JUKEBOX_RANGE_XZ) >> 4;
        int maxChunkX = (origin.getX() + JUKEBOX_RANGE_XZ) >> 4;
        int minChunkZ = (origin.getZ() - JUKEBOX_RANGE_XZ) >> 4;
        int maxChunkZ = (origin.getZ() + JUKEBOX_RANGE_XZ) >> 4;
        int minSectionY = (origin.getY() - JUKEBOX_RANGE_Y) >> 4;
        int maxSectionY = (origin.getY() + JUKEBOX_RANGE_Y) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int index = level.getSectionIndex(sectionY << 4);
                    if (index < 0 || index >= sections.length) continue;
                    LevelChunkSection section = sections[index];
                    if (section == null || section.hasOnlyAir()) continue;
                    if (section.maybeHas(JUKEBOX)) return true;
                }
            }
        }
        return false;
    }

    private static void musicPulse(ServerLevel level, Vec3 at, int ticks, int required) {
        List<ElementalSlime> slimes = level.getEntitiesOfClass(ElementalSlime.class,
                new AABB(at, at).inflate(16), slime -> slime.variant() == ElementalSlimeVariant.SOUND);
        for (ElementalSlime slime : slimes) {
            Player nearest = level.getNearestPlayer(slime, 16);
            if (nearest != null) slime.hearMusic(nearest, ticks, required, at);
        }
    }

    private record SpawnPick(ElementalSlimeVariant variant, BlockPos pos,
                             ElementalSlimeTier forcedTier, boolean fluidSurface, boolean airborne) { }

    private static SpawnPick findPick(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos origin = player.blockPosition();
        // 下界单独优先搜索岩浆海：通用候选柱只向下扫 18 格，玩家站在高岸时很容易永远碰不到液面。
        if (level.dimension() == Level.NETHER) {
            BlockPos lava = findNetherLavaSurface(level, origin, random);
            if (lava != null) return pick(ElementalSlimeVariant.FIRE, lava, true);
        }
        // 风史莱姆：玩家海拔超过 100 就有机会出现在「离地 8～18 格」的空中。
        // 以前是 50% 概率生成在离地 32 格处，玩家抬头就是一片漂浮的风史莱姆；
        // 现在概率压低、高度贴近屋顶，而且附近已经有一群时就不再补。
        if (origin.getY() > 100 && random.nextFloat() < WIND_HIGH_AIR_CHANCE
                && countFloatingWindSlimes(level, origin) < WIND_HIGH_AIR_CAP) {
            SpawnPick wind = findHighAirSpot(level, origin, random);
            if (wind != null) return wind;
        }

        boolean underground = origin.getY()
                < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()) - 10;
        for (int i = 0; i < TRIES; i++) {
            BlockPos candidate = candidatePos(level, origin, underground, random);
            // 环带里抽到的那一列还没加载：这一轮不刷，下一轮重新抽。
            if (candidate == null) continue;

            // 液面优先于岸边：火和水史莱姆现在能直接站在岩浆/水面出生。
            BlockPos lava = findFluidSurface(level, candidate, true);
            if (lava != null) return pick(ElementalSlimeVariant.FIRE, lava, true);
            BlockPos water = findFluidSurface(level, candidate, false);
            if (water != null) return pick(waterSurfaceVariant(level, water, random), water, true);

            BlockPos spot = findFloor(level, candidate, 2);
            if (spot == null) continue;
            // 设计稿的“雨天 10% 凭空生成幼崽”不再错误地要求旁边必须有水；
            // 但冰面/雪地上不能刷水史莱姆，否则它会站在原地被冰雪持续磨血。
            if (level.isRainingAt(spot) && !standsOnFrozenGround(level, spot) && random.nextFloat() < 0.10F) {
                // 沼泽的雨天幼崽也换成毒史莱姆，别把水史莱姆的占比重新拉起来。
                ElementalSlimeVariant rainCub = isSwamp(level.getBiome(spot))
                        ? ElementalSlimeVariant.POISON : ElementalSlimeVariant.WATER;
                return new SpawnPick(rainCub, spot, ElementalSlimeTier.CUB, false, false);
            }
            SpawnPick pick = variantAt(level, spot, underground, random);
            if (pick != null) return pick;
        }
        return null;
    }

    /** 附近已经在飘的风史莱姆有几只：给「漫天风史莱姆」设一个实实在在的数量上限。 */
    private static int countFloatingWindSlimes(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(ElementalSlime.class, new AABB(origin).inflate(WIND_HIGH_AIR_RADIUS),
                slime -> slime.variant() == ElementalSlimeVariant.WIND).size();
    }

    /** 在玩家周围找一根柱子，把落点定在地表往上 8～18 格；找不到（头顶被山挡住）就回落普通判定。 */
    private static SpawnPick findHighAirSpot(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int i = 0; i < TRIES; i++) {
            // r106：高空风史莱姆同样只在环带里生成，免得直接落在玩家房顶上。
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = SpawnPacing.pickDistance(random, SpawnPacing.slimeMinDistance(),
                    SpawnPacing.slimeMaxDistance());
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!SectionScan.loadedColumn(level, x, z)) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int offset = WIND_AIR_MIN_OFFSET + random.nextInt(WIND_AIR_MAX_OFFSET - WIND_AIR_MIN_OFFSET + 1);
            int y = Math.min(surface + offset, level.getMaxBuildHeight() - 4);
            BlockPos air = new BlockPos(x, y, z);
            if (level.isEmptyBlock(air) && level.isEmptyBlock(air.above()))
                return new SpawnPick(ElementalSlimeVariant.WIND, air, null, false, true);
        }
        return null;
    }

    /** 冰雪群系的水面：冰史莱姆占多数，水史莱姆只留一小部分，避免冰原被水史莱姆占满。 */
    private static ElementalSlimeVariant waterSurfaceVariant(ServerLevel level, BlockPos pos, RandomSource random) {
        // 沼泽的水面同样换掉大半水史莱姆：毒史莱姆才是这里的主角。
        if (isSwamp(level.getBiome(pos)))
            return random.nextFloat() < 0.80F ? ElementalSlimeVariant.POISON : ElementalSlimeVariant.WATER;
        return isColdPosition(level, pos) && random.nextFloat() < 0.78F
                ? ElementalSlimeVariant.ICE : ElementalSlimeVariant.WATER;
    }

    private static boolean isSwamp(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP);
    }

    /**
     * 沙漠类群系：普通沙漠，加上恶地与侵蚀恶地（两块台地）。
     *
     * <p>它们的地表都是沙 / 红沙，没有树也不常有水，木史莱姆在这里不合适；
     * 有树的繁茂恶地（wooded badlands）除外——那里真的长着树。</p>
     */
    private static boolean isDesert(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.is(Biomes.DESERT) || biome.is(Biomes.BADLANDS) || biome.is(Biomes.ERODED_BADLANDS);
    }

    /**
     * 在玩家周围的环带里抽一根柱子。
     *
     * <p>r106：以前是在 ±32 格的方块范围内均匀抽，玩家脚下、房顶、院子里都会中签。
     * 现在改成「最小距离～最大距离」的环带（默认 28～128 格），近处不再刷史莱姆。</p>
     *
     * <p>r107：落点所在区块没加载时返回 {@code null}，这一轮直接跳过。
     * 以前这里无脑问高度图，落在没生成的区块上时游戏会在服务器主线程里当场生成那块地形；
     * 每 4 秒给每个玩家「预生成」一次远处地形，玩久了就是卡顿、TPC 掉到个位数。
     * 刷怪只该往已经在那里世界里放生物，不该替玩家花钱生成地形。</p>
     */
    private static BlockPos candidatePos(ServerLevel level, BlockPos origin, boolean underground, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = SpawnPacing.pickDistance(random, SpawnPacing.slimeMinDistance(), SpawnPacing.slimeMaxDistance());
        int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (underground) {
            if (!SectionScan.loadedColumn(level, x, z)) return null;
            return new BlockPos(x, origin.getY() + random.nextInt(25) - 8, z);
        }
        if (!SectionScan.loadedColumn(level, x, z)) return null;
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    /** 下界在玩家周围的环带里抽 24 根柱子，向下最多搜索 80 格寻找岩浆海/池表面。 */
    private static BlockPos findNetherLavaSurface(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            // r106：下界岩浆海同样只在环带里找，不在玩家脚边刷火史莱姆。
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = SpawnPacing.pickDistance(random, SpawnPacing.slimeMinDistance(),
                    SpawnPacing.slimeMaxDistance() + 16.0D);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            // r107：这一列没加载就直接换一根柱子，别在没加载的区块里逐格空转。
            if (!SectionScan.loadedColumn(level, x, z)) continue;
            int top = Math.min(level.getMaxBuildHeight() - 2, origin.getY() + 12);
            int bottom = Math.max(level.getMinBuildHeight(), origin.getY() - 80);
            for (int y = top; y >= bottom; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos) || !level.getFluidState(pos).is(FluidTags.LAVA)) continue;
                if (!level.getFluidState(pos.above()).is(FluidTags.LAVA)
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return pos;
            }
        }
        return null;
    }

    /** 在候选柱里寻找最上层水/岩浆，返回液体所在格；上方必须能容纳史莱姆。 */
    private static BlockPos findFluidSurface(ServerLevel level, BlockPos start, boolean lava) {
        int top = Math.min(level.getMaxBuildHeight() - 2, start.getY() + 2);
        int bottom = Math.max(level.getMinBuildHeight(), start.getY() - 18);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(start.getX(), y, start.getZ());
            if (!level.isLoaded(pos)) continue;
            FluidState fluid = level.getFluidState(pos);
            if (!(lava ? fluid.is(FluidTags.LAVA) : fluid.is(FluidTags.WATER))) continue;
            FluidState above = level.getFluidState(pos.above());
            if (!(lava ? above.is(FluidTags.LAVA) : above.is(FluidTags.WATER))
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return pos;
        }
        return null;
    }

    public static BlockPos findFloor(ServerLevel level, BlockPos start, int clear) {
        return findFloor(level, start, clear, 12);
    }

    private static BlockPos findFloor(ServerLevel level, BlockPos start, int clear, int maxDrop) {
        // 整条竖扫都落在同一列（同一个区块）里：区块没加载完就直接放弃，
        // 不能靠 getBlockState 让游戏在主线程上同步生成它（那会把服务器刻卡死）。
        if (!ChunkSafety.readyColumn(level, start.getX(), start.getZ())) return null;
        for (int drop = 0; drop <= maxDrop; drop++) {
            BlockPos pos = start.below(drop);
            if (pos.getY() - 1 < level.getMinBuildHeight()) return null;
            boolean empty = true;
            for (int h = 0; h < clear; h++) {
                if (!level.getBlockState(pos.above(h)).getCollisionShape(level, pos.above(h)).isEmpty()) {
                    empty = false;
                    break;
                }
            }
            if (!empty) continue;
            BlockPos floor = pos.below();
            if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return pos;
        }
        return null;
    }

    private static SpawnPick variantAt(ServerLevel level, BlockPos pos, boolean underground, RandomSource random) {
        var biome = level.getBiome(pos);
        if (near(level, pos, Blocks.LAVA, 5, 1)) return pick(ElementalSlimeVariant.FIRE, pos, false);
        if (ManaCaveData.get(level).isNearCave(pos, 56)) return pick(ElementalSlimeVariant.MANA, pos, false);
        if (manaOreNear(level, pos, random)) return pick(ElementalSlimeVariant.MANA, pos, false);
        // 寒冷群系必须先判：否则冻洋/冻河会被下面的「海洋/河流」规则抢走，全变成水史莱姆。
        if (isColdPosition(level, pos)) {
            // 冰原这类地区冰史莱姆占多数；水史莱姆只出现在真正泡着水的位置，
            // 因为站在冰面/雪地上的水史莱姆会每秒掉 1 血，等于刷出来就慢慢等死。
            if (inOrOnWater(level, pos) && random.nextFloat() < 0.22F)
                return pick(ElementalSlimeVariant.WATER, pos, false);
            return pick(ElementalSlimeVariant.ICE, pos, false);
        }
        // 沼泽必须先于「海洋/河流/附近有水」判定：沼泽遍地是水，否则整片沼泽都会变成水史莱姆。
        // 这里的期望是毒史莱姆占大头，只有真正泡在水里的一小部分位置留给水史莱姆。
        if (isSwamp(biome)) {
            if (inOrOnWater(level, pos) && random.nextFloat() < 0.20F)
                return pick(ElementalSlimeVariant.WATER, pos, false);
            return pick(ElementalSlimeVariant.POISON, pos, false);
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || near(level, pos, Blocks.WATER, 6, 2)) {
            // 冻洋/冻河的表面是海冰，普通海洋群系边缘也可能盖着雪：这些地面同样换成冰史莱姆。
            if (standsOnFrozenGround(level, pos) && !inOrOnWater(level, pos))
                return pick(ElementalSlimeVariant.ICE, pos, false);
            return pick(ElementalSlimeVariant.WATER, pos, false);
        }
        long day = Math.floorMod(level.getDayTime(), 24_000L);
        if (day >= 5_500 && day <= 6_500 && level.canSeeSky(pos)
                && level.getRawBrightness(pos, level.getSkyDarken()) >= 13 && random.nextFloat() < 0.25F)
            return pick(ElementalSlimeVariant.LIGHT, pos, false);
        if (underground) {
            if (biome.is(BiomeTags.HAS_MINESHAFT)
                    || near(level, pos, Blocks.RAIL, 9, 2)
                    // r108：金史莱姆的规矩是「长在铁矿、金矿旁边」。以前只认铁矿，
                    // 玩家在金矿洞里转半天一只都碰不到；金矿（含深层金矿）现在同样算数。
                    || nearAny(level, pos, 7, 2, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE))
                return pick(ElementalSlimeVariant.METAL, pos, false);
            return pick(ElementalSlimeVariant.EARTH, pos, false);
        }
        // r108：沙漠里不刷木史莱姆——这里既没有树也没有水，木史莱姆站在沙丘上很出戏。
        // 改成就近刷土史莱姆，但只放行一部分（有概率出现，不是遍地都是）。
        // 必须放在森林/兜底判定之前，否则沙漠会掉到最后的「主世界露天地表 → 木」那条里。
        if (isDesert(biome)) {
            return random.nextFloat() < DESERT_EARTH_CHANCE ? pick(ElementalSlimeVariant.EARTH, pos, false) : null;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(Biomes.PLAINS) || biome.is(Biomes.MEADOW))
            return pick(ElementalSlimeVariant.NATURE, pos, false);
        // 作为基础元素生物，主世界其余正常地表也以木元素兜底；特殊环境仍由上面的元素优先覆盖。
        if (level.dimension() == Level.OVERWORLD && level.canSeeSky(pos)) return pick(ElementalSlimeVariant.NATURE, pos, false);
        return null;
    }

    private static SpawnPick pick(ElementalSlimeVariant variant, BlockPos pos, boolean fluidSurface) {
        return new SpawnPick(variant, pos, null, fluidSurface, false);
    }

    /** 冰原这类寒冷地区：覆雪群系、冻洋、冻河、冰刺。 */
    /** 落点脚下是不是冰/雪类方块（含薄雪层、细雪）；水史莱姆站上去会持续掉血。 */
    static boolean standsOnFrozenGround(ServerLevel level, BlockPos pos) {
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos check = pos.offset(0, dy, 0);
            if (ElementalSlime.isSnowOrIce(level.getBlockState(check))) return true;
        }
        return false;
    }

    /** 脚下一格或脚下是不是水：水面行走、泡在水里都算，这种位置水史莱姆不会被冰雪磨血。 */
    private static boolean inOrOnWater(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER)
                || level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    private static boolean isColdPosition(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.value().coldEnoughToSnow(pos)
                || biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN)
                || biome.is(Biomes.FROZEN_RIVER) || biome.is(Biomes.ICE_SPIKES);
    }

    /** 每次靠近矿脉的有效环境判定，分别按下/中/高/极品/特级 0.25/2.5/15/50/100% 判定。 */
    private static boolean manaOreNear(ServerLevel level, BlockPos center, RandomSource random) {
        String[] ores = {"lesser_mana_crystal_ore", "medium_mana_crystal_ore", "advanced_mana_crystal_ore",
                "supreme_mana_crystal_ore", "special_mana_crystal_cluster"};
        float[] chances = {0.0025F, 0.025F, 0.15F, 0.50F, 1.0F};
        for (int i = ores.length - 1; i >= 0; i--) {
            var holder = GeneratedMod.MOD_BLOCKS.get(ores[i]);
            if (holder != null && near(level, center, holder.get(), 6, 1)) return random.nextFloat() < chances[i];
        }
        return false;
    }

    private static boolean near(ServerLevel level, BlockPos center, Block block, int radius, int step) {
        return nearAny(level, center, radius, step, block);
    }

    /**
     * 附近有没有这几种方块。
     *
     * <p>r107：改走 {@link SectionScan}——先问区块段的调色板，附近根本没有目标方块时
     * 只花几次调色板比对。以前这里对每个刷怪候选逐格查 13×13×13 = 2197 格，
     * 而「附近有岩浆 / 水 / 铁轨 / 魔晶矿」在绝大多数位置都是否定的，钱全白花。</p>
     */
    private static boolean nearAny(ServerLevel level, BlockPos center, int radius, int step, Block... blocks) {
        return SectionScan.anyNearAny(level, center, radius, step, blocks);
    }

    private static int localCount(ServerLevel level, Vec3 at, double radius) {
        return level.getEntitiesOfClass(ElementalSlime.class, new AABB(at, at).inflate(radius),
                slime -> !slime.isTamed()).size();
    }

    public static void spawn(ServerLevel level, ElementalSlimeVariant variant, BlockPos pos,
                             ElementalSlimeTier forcedTier, int count) {
        spawn(level, variant, pos, forcedTier, count, false, false, false);
    }

    public static void spawn(ServerLevel level, ElementalSlimeVariant variant, BlockPos pos,
                             ElementalSlimeTier forcedTier, int count, boolean persistent) {
        spawn(level, variant, pos, forcedTier, count, persistent, false, false);
    }

    private static void spawn(ServerLevel level, ElementalSlimeVariant variant, BlockPos pos,
                              ElementalSlimeTier forcedTier, int count, boolean persistent,
                              boolean fluidSurface, boolean airborne) {
        // 野外刷怪的统一闸门：环境刷新、音乐/雷击/魔晶洞穴补刷全都从这里过。
        if (!SpawnControl.hostileSpawningAllowed(level)) return;
        // r105 权重：调到 0 的史莱姆连洞穴定额、落雷召怪、声响召怪一起停掉。
        if (!MobSpawnWeights.enabled(variant.entityId())) return;
        // r290：连雷击、爆炸这类「声音召怪」的旁路也一并封死——反虚空里任何途径都不许出史莱姆。
        if (level.dimension().equals(AntiVoidSystem.ANTI_VOID)) return;
        // 史莱姆驱逐火把：这里是所有元素史莱姆刷怪的公共出口，先按中心点拦一次；
        // 落点会随机偏 ±3 格，所以下面挑到实际落脚点后还会再拦一次。
        if (SlimeRepellentTorchEvents.spawnBlocked(level, pos)) return;
        // 蚀魔荒原的生态闸门：环境刷新、洞穴补刷、雷击召怪、声响召怪全都要过这一关。
        // 逐只判定，一次刷三只也不会整批漏过去。
        boolean wasteland = CorruptedWastelandBiome.is(level.getBiome(pos));
        for (int i = 0; i < count; i++) {
            if (wasteland && !wastelandSpawnAllowed(level, variant)) continue;
            ElementalSlime slime = GeneratedMod.elementalSlimeType(variant).create(level);
            if (slime == null) continue;
            ElementalSlimeTier tier = forcedTier != null ? forcedTier : ElementalSlimeTier.roll(level.random);
            slime.setTier(tier, true);
            boolean placed = false;
            // 洞穴/树下的落脚点很紧，多试几次再放弃，否则定额刷新会整批失败。
            for (int attempt = 0; attempt < 12 && !placed; attempt++) {
                BlockPos offset = pos.offset(level.random.nextInt(7) - 3, fluidSurface ? 0 : 3,
                        level.random.nextInt(7) - 3);
                double targetY;
                if (fluidSurface) {
                    BlockPos fluid = findFluidSurface(level, offset.above(2), variant == ElementalSlimeVariant.FIRE);
                    if (fluid == null) continue;
                    FluidState state = level.getFluidState(fluid);
                    offset = fluid;
                    targetY = fluid.getY() + state.getHeight(level, fluid) - 0.02;
                } else if (airborne) {
                    // 空中落点会在中心 ±3 格里随机偏，靠区块边缘时可能跨到相邻区块。
                    // 先确认那一片真正加载好了：空中的风史莱姆不值得让主线程停下来等地形生成。
                    if (!ChunkSafety.readyAround(level, offset, 1)) continue;
                    if (!level.isEmptyBlock(offset) || !level.isEmptyBlock(offset.above())) continue;
                    targetY = offset.getY();
                } else {
                    BlockPos floor = findFloor(level, offset, tier == ElementalSlimeTier.ELITE ? 3 : 2, 16);
                    if (floor == null) continue;
                    // 兜底：不管上面哪条规则挑出来的，水史莱姆都不在冰/雪地面上落脚。
                    if (variant == ElementalSlimeVariant.WATER && standsOnFrozenGround(level, floor)) continue;
                    offset = floor;
                    targetY = floor.getY();
                }
                slime.moveTo(offset.getX() + 0.5, targetY, offset.getZ() + 0.5,
                        level.random.nextFloat() * 360.0F, 0);
                // 落点周围（按这只史莱姆的真实碰撞箱算）的区块必须都已加载好。
                // noCollision / 四角支撑检查都会读方块，跨到没加载的区块就会同步加载 → 卡死服务器刻。
                if (!ChunkSafety.readyBox(level, slime.getBoundingBox())) continue;
                placed = level.noCollision(slime) && (fluidSurface || airborne || hasFullGroundSupport(level, slime));
                // 落脚点落在驱逐火把的 5×5 区块里就不许刷，换下一个落点。
                if (placed && SlimeRepellentTorchEvents.spawnBlocked(level, offset)) placed = false;
            }
            if (!placed) {
                slime.discard();
                continue;
            }
            slime.finalizeSpawn(level, level.getCurrentDifficultyAt(slime.blockPosition()), MobSpawnType.EVENT, null);
            slime.setTier(tier, true);
            if (persistent || variant == ElementalSlimeVariant.SOUND) slime.setPersistenceRequired();
            level.addFreshEntity(slime);
        }
    }

    /**
     * 蚀魔荒原里这一只元素史莱姆放不放行。
     *
     * <p>荒原的生态只认变异体、魔素史莱姆、秽蚀蠹、秽蚀蜂：别的元素史莱姆（木、风最常见，
     * 还有水、冰、火……）只留极小概率，魔素史莱姆也要再稀释七成。
     * 这样荒原的刷怪容量才腾得出来给变异体，和「本来就该少量生成」的秽蚀蠹。</p>
     */
    private static boolean wastelandSpawnAllowed(ServerLevel level, ElementalSlimeVariant variant) {
        if (variant != ElementalSlimeVariant.MANA)
            return level.random.nextFloat() < WASTELAND_OTHER_SLIME_CHANCE;
        return level.random.nextFloat() >= WASTELAND_MANA_THIN_CHANCE;
    }

    /**
     * 检查碰撞箱四角下面有没有完整上表面，避免成熟/精英体只靠中心一格支撑而挂在方块边缘。
     *
     * <p>魔晶洞穴的地面是挖出来的不规则岩壁，四个角全实心往往一个点都找不到；
     * 旧版要求四角全中，加上当时碰撞箱偏大，洞穴定额刷新几乎全部失败。
     * 现在按「至少两个角有支撑」判定，中心那格已由 findFloor 保证。</p>
     */
    private static boolean hasFullGroundSupport(ServerLevel level, ElementalSlime slime) {
        AABB box = slime.getBoundingBox();
        double inset = Math.min(0.08, box.getXsize() * 0.18);
        double[] xs = {box.minX + inset, box.maxX - inset};
        double[] zs = {box.minZ + inset, box.maxZ - inset};
        int y = (int)Math.floor(box.minY - 0.01);
        int supported = 0;
        for (double x : xs) for (double z : zs) {
            BlockPos floor = BlockPos.containing(x, y, z);
            if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) supported++;
        }
        return supported >= 2;
    }
}
