package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class ManaCrystalCaveFeature extends Feature<NoneFeatureConfiguration> {
    public ManaCrystalCaveFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos center = context.origin();
        int radiusX = 8 + random.nextInt(4);
        int radiusY = 6 + random.nextInt(3);
        int radiusZ = 8 + random.nextInt(4);
        int changed = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    double distance = ellipsoidDistance(x, y, z, radiusX, radiusY, radiusZ);
                    double roughness = (random.nextDouble() - 0.5) * 0.12;
                    double shapedDistance = distance + roughness;
                    if (shapedDistance > 1.0) continue;

                    cursor.setWithOffset(center, x, y, z);
                    BlockState current = level.getBlockState(cursor);
                    if (!canReplace(current)) continue;

                    BlockState replacement;
                    if (shapedDistance > 0.82) {
                        replacement = Blocks.SMOOTH_BASALT.defaultBlockState();
                    } else if (shapedDistance > 0.70) {
                        replacement = Blocks.CALCITE.defaultBlockState();
                    } else if (shapedDistance > 0.53) {
                        replacement = innerWallState(random);
                    } else if (y < -radiusY * 0.42 && x * x + z * z < 10) {
                        replacement = GeneratedMod.PURE_LIQUID_MANA_BLOCK.get().defaultBlockState();
                    } else {
                        replacement = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlock(cursor, replacement, Block.UPDATE_CLIENTS);
                    changed++;
                }
            }
        }

        decorateInterior(level, random, center, radiusX, radiusY, radiusZ);
        if (changed > 0) ManaCaveData.get(level.getLevel()).add(center);
        return changed > 0;
    }

    private static BlockState innerWallState(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 48) return Blocks.AMETHYST_BLOCK.defaultBlockState();
        if (roll < 63) return Blocks.BUDDING_AMETHYST.defaultBlockState();
        if (roll < 78) return GeneratedMod.MOD_BLOCKS.get("lesser_mana_crystal_ore").get().defaultBlockState();
        if (roll < 88) return GeneratedMod.MOD_BLOCKS.get("medium_mana_crystal_ore").get().defaultBlockState();
        if (roll < 95) return GeneratedMod.MOD_BLOCKS.get("advanced_mana_crystal_ore").get().defaultBlockState();
        return GeneratedMod.MOD_BLOCKS.get("supreme_mana_crystal_ore").get().defaultBlockState();
    }

    private static void decorateInterior(WorldGenLevel level, RandomSource random, BlockPos center,
                                         int radiusX, int radiusY, int radiusZ) {
        int attempts = 90 + random.nextInt(41);
        for (int i = 0; i < attempts; i++) {
            BlockPos cavityPos = center.offset(
                    random.nextInt(radiusX * 2 - 3) - radiusX + 2,
                    random.nextInt(radiusY * 2 - 3) - radiusY + 2,
                    random.nextInt(radiusZ * 2 - 3) - radiusZ + 2
            );
            if (!level.getBlockState(cavityPos).isAir()) continue;

            Direction facing = Direction.getRandom(random);
            BlockPos supportPos = cavityPos.relative(facing.getOpposite());
            BlockState support = level.getBlockState(supportPos);
            if (!support.isFaceSturdy(level, supportPos, facing)) continue;

            BlockState cluster;
            int roll = random.nextInt(100);
            if (roll < 3) {
                cluster = GeneratedMod.MOD_BLOCKS.get("special_mana_crystal_cluster").get().defaultBlockState();
            } else if (roll < 70) {
                cluster = Blocks.AMETHYST_CLUSTER.defaultBlockState();
            } else if (roll < 88) {
                cluster = Blocks.LARGE_AMETHYST_BUD.defaultBlockState();
            } else {
                cluster = Blocks.MEDIUM_AMETHYST_BUD.defaultBlockState();
            }
            cluster = cluster.setValue(AmethystClusterBlock.FACING, facing)
                    .setValue(AmethystClusterBlock.WATERLOGGED, false);
            level.setBlock(cavityPos, cluster, Block.UPDATE_CLIENTS);
        }

        int specialCount = 1 + random.nextInt(3);
        for (int i = 0; i < specialCount; i++) {
            placeGuaranteedSpecialCluster(level, random, center, radiusX, radiusY, radiusZ);
        }
    }

    private static void placeGuaranteedSpecialCluster(WorldGenLevel level, RandomSource random, BlockPos center,
                                                      int radiusX, int radiusY, int radiusZ) {
        for (int attempt = 0; attempt < 180; attempt++) {
            BlockPos cavityPos = center.offset(
                    random.nextInt(radiusX * 2 - 3) - radiusX + 2,
                    random.nextInt(radiusY * 2 - 3) - radiusY + 2,
                    random.nextInt(radiusZ * 2 - 3) - radiusZ + 2
            );
            if (!level.getBlockState(cavityPos).isAir()) continue;

            for (Direction facing : Direction.values()) {
                BlockPos supportPos = cavityPos.relative(facing.getOpposite());
                BlockState support = level.getBlockState(supportPos);
                if (!support.isFaceSturdy(level, supportPos, facing)) continue;
                BlockState cluster = GeneratedMod.MOD_BLOCKS.get("special_mana_crystal_cluster").get()
                        .defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, facing)
                        .setValue(AmethystClusterBlock.WATERLOGGED, false);
                level.setBlock(cavityPos, cluster, Block.UPDATE_CLIENTS);
                return;
            }
        }
    }

    private static boolean canReplace(BlockState state) {
        return state.isAir()
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.WATER);
    }

    private static double ellipsoidDistance(int x, int y, int z, int radiusX, int radiusY, int radiusZ) {
        double nx = (double) x / radiusX;
        double ny = (double) y / radiusY;
        double nz = (double) z / radiusZ;
        return nx * nx + ny * ny + nz * nz;
    }
}
