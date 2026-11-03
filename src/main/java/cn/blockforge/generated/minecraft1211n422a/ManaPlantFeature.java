package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界生成阶段的魔法植物：区块生成时就把植物种好，而不是等玩家靠近再每两秒算一次。
 *
 * <p>条件里能用地形阶段数据的都用：群系（{@link #environmentFits}）、高度、地表浓度
 * （{@link ManaConcentration#surfaceAt}）。洞穴上方那种要读运行时洞穴表的蕾薇莉亚不在这里生成，
 * 由区块加载时补一株，见 {@link ManaPlantSpawning#onChunkLoad}。</p>
 */
public final class ManaPlantFeature extends Feature<NoneFeatureConfiguration> {
    /** 一次 feature 调用里最多试几个位置，成功一株就收手。 */
    private static final int ATTEMPTS = 4;
    /** 在 feature 原点附近多大范围里找地方，单位是格。 */
    private static final int SPREAD = 2;
    /** 找岩壁的次数：地表那一株没种成，就再试几次“贴墙”的位置。 */
    private static final int WALL_ATTEMPTS = 3;
    /** 从地表往下一格一格找岩壁，最多向下找这么多格。 */
    private static final int WALL_SCAN_DEPTH = 6;
    /** 岩壁上垂下来的一串最多几段。 */
    private static final int WALL_VINE_MAX = 3;
    /**
     * 贴墙形态允许的最低高度。
     *
     * <p>地面上的卢梅莉亚按设定只长在 100 格往上的地表，可荒原的断崖大多在 60～100 格之间，
     * 照那条线走玩家永远见不到岩壁上的植株。贴墙这一种跟着崖壁走，从海平面往上都算数。</p>
     */
    private static final int WALL_MIN_Y = 62;

    public ManaPlantFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        long seed = level.getSeed();
        ResourceKey<Level> dimension = level.getLevel().dimension();

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = origin.getX() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
            int z = origin.getZ() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
            // 只在原点所在区块里动手，跨区块写方块在生成阶段会被丢掉。
            if ((x >> 4) != (origin.getX() >> 4) || (z >> 4) != (origin.getZ() >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState here = level.getBlockState(pos);
            // 只种在空气里：水面、树叶、雪层都放过。
            if (!here.canBeReplaced() || !level.getFluidState(pos).isEmpty()) continue;

            Holder<Biome> biome = level.getBiome(pos);
            double mana = ManaConcentration.surfaceAt(seed, dimension, biome, pos);
            ManaPlants.Kind kind = pickKind(level, biome, pos, mana, random);
            if (kind == null) continue;
            if (ManaPlants.place(level, kind, pos)) return true;
        }
        // 地表没种成：再到附近的崖壁上挂一串卢梅莉亚。
        return placeOnCliff(level, random, origin, seed, dimension);
    }

    /**
     * 岩壁上的卢梅莉亚。
     *
     * <p>以前只在地表高度找位置，而“贴墙”那段代码虽然写好了，却永远没有调用者——荒原的断崖
     * 上自然一株都长不出来。这里从地表往下扫几格，找“这一格是空的、背后是实心岩壁”的位置，
     * 贴墙种下去；顺手顺着墙面往上接 1～3 段，标好 above/below，渲染出来就是一串垂下来的藤。</p>
     *
     * <p>只种卢梅莉亚：其他植物没有贴墙形态。</p>
     */
    private static boolean placeOnCliff(WorldGenLevel level, RandomSource random, BlockPos origin,
                                        long seed, ResourceKey<Level> dimension) {
        for (int attempt = 0; attempt < WALL_ATTEMPTS; attempt++) {
            int x = origin.getX() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
            int z = origin.getZ() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
            if ((x >> 4) != (origin.getX() >> 4) || (z >> 4) != (origin.getZ() >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            for (int drop = 0; drop < WALL_SCAN_DEPTH; drop++) {
                BlockPos pos = new BlockPos(x, top - drop, z);
                if (pos.getY() < WALL_MIN_Y) break;
                BlockState here = level.getBlockState(pos);
                if (!here.canBeReplaced() || !level.getFluidState(pos).isEmpty()) continue;
                // 脚下有土的位置交给地上那套，免得把地面植株全变成贴墙形态。
                if (ManaPlants.canPlantAt(level, pos)) continue;
                Direction wall = ManaPlants.wallFacing(level, pos);
                if (wall == null) continue;
                Holder<Biome> biome = level.getBiome(pos);
                if (!ManaPlants.Kind.LUMELIA.manaFits(ManaConcentration.surfaceAt(seed, dimension, biome, pos))) {
                    continue;
                }
                int length = 1 + random.nextInt(WALL_VINE_MAX);
                if (plantWallVine(level, pos, wall, length)) return true;
            }
        }
        return false;
    }

    /** 顺着同一面墙往上摆一串，最后统一按邻居重算 above/below，让它们接成一株。 */
    private static boolean plantWallVine(WorldGenLevel level, BlockPos base, Direction wall, int length) {
        int placed = 0;
        for (int i = 0; i < length; i++) {
            BlockPos pos = base.above(i);
            BlockState here = level.getBlockState(pos);
            if (!here.canBeReplaced() || !level.getFluidState(pos).isEmpty()) break;
            if (ManaPlants.wallFacing(level, pos) != wall) break;
            level.setBlock(pos, wallState().setValue(LumeliaPlantBlock.FACING, wall),
                    Block.UPDATE_CLIENTS);
            placed++;
        }
        if (placed == 0) return false;
        for (int i = 0; i < placed; i++) {
            BlockPos pos = base.above(i);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LumeliaPlantBlock) {
                level.setBlock(pos, LumeliaPlantBlock.stackState(state, level, pos), Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }

    private static BlockState wallState() {
        return GeneratedMod.MOD_BLOCKS.get(ManaPlants.Kind.LUMELIA.id()).get().defaultBlockState()
                .setValue(LumeliaPlantBlock.WALL_MOUNTED, true);
    }

    /** 按“条件符合 + 权重”抽一种植物；一株都不符合就返回 null。 */
    private static ManaPlants.Kind pickKind(WorldGenLevel level, Holder<Biome> biome, BlockPos pos,
                                            double mana, RandomSource random) {
        List<ManaPlants.Kind> candidates = new ArrayList<>();
        for (ManaPlants.Kind kind : ManaPlants.Kind.values()) {
            // 蕾薇莉亚要“魔晶洞穴正上方”，这项数据生成阶段还没有，交给区块加载阶段补种。
            if (kind == ManaPlants.Kind.LEVILIA) continue;
            if (!kind.heightFits(pos.getY()) || !kind.manaFits(mana)) continue;
            if (!environmentFits(level, biome, kind, pos)) continue;
            for (int i = 0; i < weightOf(kind); i++) candidates.add(kind);
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /** 瑞斯特莉亚最常见，卢梅莉亚次之；两种“有条件的”植物更稀有。 */
    private static int weightOf(ManaPlants.Kind kind) {
        return switch (kind) {
            case RESTELIA -> 6;
            case LUMELIA -> 3;
            // 暮刺荆棘在草原与森林交界处本来就难碰上，权重调高，玩家在交界处绕一圈能见到成片的花。
            case VESPER_THORN -> 4;
            case BLOOD_KISS -> 1;
            default -> 1;
        };
    }

    private static boolean environmentFits(WorldGenLevel level, Holder<Biome> biome,
                                           ManaPlants.Kind kind, BlockPos pos) {
        return switch (kind) {
            // 周边有其他花朵和野草，才长这一片回复露草；先看群系，免得每次都扫一圈方块。
            // 地形阶段同一步骤的花草可能还没铺，所以草地、森林、花海这类群系也直接算合格。
            case RESTELIA -> isFloweryBiome(biome) || ManaPlants.neighbourFlora(level, pos, 4) >= 2;
            // 卢梅莉亚：浓度和高度已经把范围卡得很死，岩壁挂不挂得住由放置时再判断。
            case LUMELIA -> true;
            case BLOOD_KISS -> biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP);
            // 草原与森林的交汇处。
            case VESPER_THORN -> ManaPlants.atBiomeBorder(level, pos);
            default -> false;
        };
    }

    private static boolean isFloweryBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_FOREST)
                || biome.is(Biomes.PLAINS)
                || biome.is(Biomes.SUNFLOWER_PLAINS)
                || biome.is(Biomes.MEADOW)
                || biome.is(Biomes.FLOWER_FOREST)
                || biome.is(Biomes.BIRCH_FOREST)
                || biome.is(Biomes.DARK_FOREST)
                || biome.is(Biomes.SWAMP);
    }
}
