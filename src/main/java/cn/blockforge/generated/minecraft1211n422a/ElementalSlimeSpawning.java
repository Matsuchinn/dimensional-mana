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
    private static final int LOCAL_CAP = 16;
    private static final double LOCAL_RADIUS = 56.0;
    private static final int TRIES = 10;
    private static final int NORMAL_CAVE_CAP = 7;
    private static final int LARGE_CAVE_CAP = 14;

    private ElementalSlimeSpawning() { }

    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        WorldgenSpawnQueue.tick(level);
        if (level.getGameTime() % 20 != 0) return;
        for (ServerPlayer player : level.players()) {
            // 旁观模式也作为环境生成锚点：方便找生物，同时不会成为史莱姆的攻击目标。
            if (level.getGameTime() % 40 == 0 && !player.isSpectator()) tickJukeboxMusic(level, player, 40);
            if (level.getGameTime() % CHECK_INTERVAL != 0) continue;
            RandomSource random = player.getRandom();
            if (random.nextFloat() > ATTEMPT_CHANCE) continue;
            // 洞穴定额守卫优先补齐，不受周围普通环境史莱姆上限影响。
            if (topUpManaCave(level, player, random)) continue;
            if (localCount(level, player.position(), LOCAL_RADIUS) >= LOCAL_CAP) continue;
            SpawnPick pick = findPick(level, player, random);
            if (pick == null) continue;
            spawn(level, pick.variant(), pick.pos(), pick.forcedTier(), 1 + random.nextInt(3),
                    false, pick.fluidSurface(), pick.airborne());
        }
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
        int radius = large ? 40 : 28;
        int existing = level.getEntitiesOfClass(ElementalSlime.class, new AABB(cave).inflate(radius),
                slime -> slime.variant() == ElementalSlimeVariant.MANA).size();
        if (existing >= cap) return false;
        BlockPos spot = randomCaveFloor(level, cave, random, large ? 13 : 7, existing < guaranteedElites ? 3 : 2);
        if (spot == null) return false;
        int count = Math.min(existing < guaranteedElites ? 2 : 1, cap - existing);
        spawn(level, ElementalSlimeVariant.MANA, spot,
                existing < guaranteedElites ? ElementalSlimeTier.ELITE : null, count, true, false, false);
        return true;
    }

    /** 闪电落地时 50% 生成 1～4 个雷史莱姆。 */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LightningBolt bolt)) return;
        // 雷史莱姆 50% 与“巨大声响召来音史莱姆”25% 是两次独立判定，不能串成 12.5%。
        if (level.random.nextFloat() < 0.50F)
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
        if (level.random.nextFloat() >= 0.25F) return;
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double distance = 64.0 + level.random.nextDouble() * 24.0;
        int x = (int)Math.floor(source.x + Math.cos(angle) * distance);
        int z = (int)Math.floor(source.z + Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        spawn(level, ElementalSlimeVariant.SOUND, new BlockPos(x, y, z), null, 1);
    }

    private static void tickJukeboxMusic(ServerLevel level, Player player, int ticks) {
        BlockPos origin = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-16, -6, -16), origin.offset(16, 6, 16))) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.JUKEBOX) && state.getValue(JukeboxBlock.HAS_RECORD)) musicPulse(level, pos.getCenter(), ticks, 100);
        }
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
        // 风史莱姆：玩家海拔超过 100 就有机会出现在「离地约 32 格」的高空，
        // 而不是像旧版那样要求 Y>250（主世界几乎没有这种落脚点，等于永远刷不出）。
        if (origin.getY() > 100 && random.nextFloat() < 0.50F) {
            SpawnPick wind = findHighAirSpot(level, origin, random);
            if (wind != null) return wind;
        }

        boolean underground = origin.getY()
                < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()) - 10;
        for (int i = 0; i < TRIES; i++) {
            BlockPos candidate = candidatePos(level, origin, underground, random);

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

    /** 在玩家周围找一根柱子，把落点定在地表往上 32 格；找不到（头顶被山挡住）就回落普通判定。 */
    private static SpawnPick findHighAirSpot(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int i = 0; i < TRIES; i++) {
            int x = origin.getX() + random.nextInt(65) - 32;
            int z = origin.getZ() + random.nextInt(65) - 32;
            if (!level.hasChunkAt(new BlockPos(x, origin.getY(), z))) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = Math.min(surface + 32, level.getMaxBuildHeight() - 4);
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

    private static BlockPos candidatePos(ServerLevel level, BlockPos origin, boolean underground, RandomSource random) {
        int x = origin.getX() + random.nextInt(65) - 32;
        int z = origin.getZ() + random.nextInt(65) - 32;
        if (underground) return new BlockPos(x, origin.getY() + random.nextInt(25) - 8, z);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    /** 下界在玩家周围 48 格内抽 24 根柱子，向下最多搜索 80 格寻找岩浆海/池表面。 */
    private static BlockPos findNetherLavaSurface(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = origin.getX() + random.nextInt(97) - 48;
            int z = origin.getZ() + random.nextInt(97) - 48;
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
                    || nearAny(level, pos, 7, 2, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE))
                return pick(ElementalSlimeVariant.METAL, pos, false);
            return pick(ElementalSlimeVariant.EARTH, pos, false);
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

    private static boolean nearAny(ServerLevel level, BlockPos center, int radius, int step, Block... blocks) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x += step)
            for (int y = -radius; y <= radius; y += step)
                for (int z = -radius; z <= radius; z += step) {
                    cursor.setWithOffset(center, x, y, z);
                    if (!level.isLoaded(cursor)) continue;
                    var state = level.getBlockState(cursor);
                    for (Block block : blocks) if (state.is(block)) return true;
                }
        return false;
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
        for (int i = 0; i < count; i++) {
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
                placed = level.noCollision(slime) && (fluidSurface || airborne || hasFullGroundSupport(level, slime));
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
