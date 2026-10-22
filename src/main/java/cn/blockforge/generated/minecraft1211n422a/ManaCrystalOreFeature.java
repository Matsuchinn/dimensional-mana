package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

import java.util.List;

public final class ManaCrystalOreFeature extends Feature<NoneFeatureConfiguration> {
    private static final double[] ATTEMPTS_PER_CHUNK = {12.0, 6.0, 3.0, 1.5};
    private static final int[] MIN_Y = {-48, -48, -48, -56};
    private static final int[] MAX_Y = {72, 40, 24, 8};
    private static final int[] MAX_VEIN_SIZE = {16, 9, 7, 4};

    public ManaCrystalOreFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        boolean placed = false;

        for (int tier = 0; tier < ATTEMPTS_PER_CHUNK.length; tier++) {
            int attempts = stochasticRound(random, ATTEMPTS_PER_CHUNK[tier]);
            for (int attempt = 0; attempt < attempts; attempt++) {
                BlockPos target = randomPosition(random, origin, MIN_Y[tier], MAX_Y[tier]);
                placed |= placeTier(level, context, random, target, tier);

                if (tier < 3 && random.nextDouble() < 0.05) {
                    int companionTier = random.nextInt(2);
                    if (companionTier >= tier) companionTier++;
                    BlockPos companionTarget = target.offset(
                            random.nextInt(9) - 4,
                            random.nextInt(7) - 3,
                            random.nextInt(9) - 4
                    );
                    placed |= placeTier(level, context, random, companionTarget, companionTier);
                }

                if (isNearAmethystGeode(level, target)) {
                    BlockPos bonusTarget = target.offset(
                            random.nextInt(17) - 8,
                            random.nextInt(13) - 6,
                            random.nextInt(17) - 8
                    );
                    placed |= placeTier(level, context, random, bonusTarget, tier);
                }
            }
        }

        if (random.nextInt(24) == 0) {
            BlockPos clusterTarget = randomPosition(random, origin, -56, 32);
            placed |= placeSpecialCluster(level, random, clusterTarget);
        }
        return placed;
    }

    private boolean placeTier(WorldGenLevel level, FeaturePlaceContext<NoneFeatureConfiguration> context,
                              RandomSource random, BlockPos target, int tier) {
        BlockState ore = switch (tier) {
            case 0 -> GeneratedMod.MOD_BLOCKS.get("lesser_mana_crystal_ore").get().defaultBlockState();
            case 1 -> GeneratedMod.MOD_BLOCKS.get("medium_mana_crystal_ore").get().defaultBlockState();
            case 2 -> GeneratedMod.MOD_BLOCKS.get("advanced_mana_crystal_ore").get().defaultBlockState();
            default -> GeneratedMod.MOD_BLOCKS.get("supreme_mana_crystal_ore").get().defaultBlockState();
        };
        List<OreConfiguration.TargetBlockState> targets = List.of(
                OreConfiguration.target(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), ore),
                OreConfiguration.target(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), ore)
        );
        OreConfiguration config = new OreConfiguration(targets, 1 + random.nextInt(MAX_VEIN_SIZE[tier]), 0.15F);
        return Feature.ORE.place(config, level, context.chunkGenerator(), random, target);
    }

    private boolean placeSpecialCluster(WorldGenLevel level, RandomSource random, BlockPos center) {
        for (int search = 0; search < 32; search++) {
            BlockPos supportPos = center.offset(
                    random.nextInt(13) - 6,
                    random.nextInt(13) - 6,
                    random.nextInt(13) - 6
            );
            BlockState support = level.getBlockState(supportPos);
            if (!support.isSolid()) continue;

            Direction direction = Direction.getRandom(random);
            BlockPos clusterPos = supportPos.relative(direction);
            if (!level.getBlockState(clusterPos).isAir()
                    || !support.isFaceSturdy(level, supportPos, direction)) continue;

            BlockState cluster = GeneratedMod.MOD_BLOCKS.get("special_mana_crystal_cluster").get()
                    .defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, direction)
                    .setValue(AmethystClusterBlock.WATERLOGGED, false);
            level.setBlock(clusterPos, cluster, Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }

    private static int stochasticRound(RandomSource random, double value) {
        int whole = (int) value;
        return whole + (random.nextDouble() < value - whole ? 1 : 0);
    }

    private static BlockPos randomPosition(RandomSource random, BlockPos origin, int minY, int maxY) {
        return new BlockPos(
                origin.getX() + random.nextInt(16),
                minY + random.nextInt(maxY - minY + 1),
                origin.getZ() + random.nextInt(16)
        );
    }

    /**
     * 附近有没有紫水晶晶洞。这个判断原来每区块要扫 22 次 × 729 格 ≈ 1.6 万次方块读取，
     * 新世界生成出生点区块时会明显变慢，所以：
     * 1. 晶洞只生成在 Y=-58~30，高出 32 直接跳过（约两成矿脉尝试）；
     * 2. 晶洞直径 5~7 格，用步长 4 的 5×5×5 网格（125 次）就足够碰到，精度对「额外矿脉」这种彩蛋够用。
     * 这一步不消耗随机数，因此不会改变同一世界种子的矿石分布。
     */
    private static final int GEODE_MAX_Y = 32;
    private static final int GEODE_STEP = 4;

    private static boolean isNearAmethystGeode(WorldGenLevel level, BlockPos center) {
        if (center.getY() > GEODE_MAX_Y) return false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -8; x <= 8; x += GEODE_STEP) {
            for (int y = -8; y <= 8; y += GEODE_STEP) {
                for (int z = -8; z <= 8; z += GEODE_STEP) {
                    cursor.setWithOffset(center, x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
