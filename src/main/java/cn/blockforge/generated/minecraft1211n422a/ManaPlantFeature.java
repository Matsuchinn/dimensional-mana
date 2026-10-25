package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
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
        return false;
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
