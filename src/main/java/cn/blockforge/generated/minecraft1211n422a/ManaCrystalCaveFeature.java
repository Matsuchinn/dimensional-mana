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
        // 22% 为大型洞穴；普通洞穴也比旧版扩大约三成，大型洞穴的横向直径可达 32～40 格。
        boolean large = random.nextFloat() < 0.22F;
        int radiusX = large ? 16 + random.nextInt(5) : 10 + random.nextInt(4);
        int radiusY = large ? 10 + random.nextInt(3) : 7 + random.nextInt(3);
        int radiusZ = large ? 16 + random.nextInt(5) : 10 + random.nextInt(4);
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
                    // 大型洞穴的半径超过区块宽度，朝外那几格所在区块还没生成时，
                    // 读它会把游戏崩掉（Requested chunk unavailable）。
                    if (!WorldgenReads.chunkLoaded(level, cursor)) continue;
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
        // 每座魔晶洞穴都埋一口宝物箱：优先放在液态魔晶池边的干燥地面上，
        // 池边实在找不到空位才退回“泡在池子里”的旧做法。
        placeRelicCache(level, random, center, radiusX, radiusY, radiusZ);
        if (changed > 0) {
            // 这里还在区块生成阶段，不能直接改 ServerLevel 的实体和存档数据；
            // 先把洞穴位置排进队列，等服务器刻、区块加载后再补刷史莱姆。
            WorldgenSpawnQueue.queueManaCave(level.getLevel(), center, large);
        }
        return changed > 0;
    }

    /**
     * 池边宝箱：先找到液态魔晶池的液面，再在池子周围的洞穴地面上找一块
     * “脚下实心、头上两格空”的位置放箱子。
     *
     * <p>旧版直接把箱子塞进液面那一格，箱子整个泡在不透明的液态魔晶里，玩家从池面上
     * 几乎看不见，看起来就像“宝箱没生成”。现在优先放在池边的干燥地面上；只有池边
     * 一点空地都没有时才退回液面方案。</p>
     *
     * <p>箱子位必须在当前区块内：世界生成阶段写到隔壁未生成区块的方块会被丢弃。</p>
     */
    private static void placeRelicCache(WorldGenLevel level, RandomSource random, BlockPos center,
                                        int radiusX, int radiusY, int radiusZ) {
        BlockPos liquid = findLiquidSurface(level, center, radiusY);
        if (liquid == null) return;
        for (int attempt = 0; attempt < 160; attempt++) {
            int dx = random.nextInt(radiusX * 2 + 1) - radiusX;
            int dz = random.nextInt(radiusZ * 2 + 1) - radiusZ;
            // 离池子太近的地方还是液体，直接跳过。
            if (dx * dx + dz * dz < 4) continue;
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            if ((x >> 4) != (center.getX() >> 4) || (z >> 4) != (center.getZ() >> 4)) continue;
            for (int dy = -8; dy <= 6; dy++) {
                BlockPos pos = new BlockPos(x, liquid.getY() + dy, z);
                BlockPos below = pos.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
                if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
                placeChest(level, random, pos, facingTowards(dx, dz));
                return;
            }
        }
        // 池边没有干燥落脚点：把箱子放在液面最上一格，至少从池边能看到箱顶。
        placeChest(level, random, liquid, Direction.Plane.HORIZONTAL.getRandomDirection(random));
    }

    /** 从洞穴中心往下找第一个纯态液态魔晶方块，也就是液面最上面那一格。 */
    private static BlockPos findLiquidSurface(WorldGenLevel level, BlockPos center, int radiusY) {
        int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int[] offset : offsets) {
            for (int depth = 0; depth <= radiusY; depth++) {
                cursor.set(center.getX() + offset[0], center.getY() - depth, center.getZ() + offset[1]);
                if (!WorldgenReads.chunkLoaded(level, cursor)) continue;
                if (level.getBlockState(cursor).is(GeneratedMod.PURE_LIQUID_MANA_BLOCK.get())) {
                    return cursor.immutable();
                }
            }
        }
        return null;
    }

    /** 箱子正面朝向池子中心，玩家从池边过来时正好看到正面。 */
    private static Direction facingTowards(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.WEST : Direction.EAST;
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static void placeChest(WorldGenLevel level, RandomSource random, BlockPos pos, Direction facing) {
        BlockState chest = Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing);
        level.setBlock(pos.immutable(), chest, Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container) {
            container.setLootTable(CACHE_LOOT, random.nextLong());
        }
    }

    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> CACHE_LOOT =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            GeneratedMod.MOD_ID, "chests/mana_crystal_cave_cache"));

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
        int attempts = Math.max(110, (radiusX * radiusY * radiusZ) / 9);
        for (int i = 0; i < attempts; i++) {
            BlockPos cavityPos = center.offset(
                    random.nextInt(radiusX * 2 - 3) - radiusX + 2,
                    random.nextInt(radiusY * 2 - 3) - radiusY + 2,
                    random.nextInt(radiusZ * 2 - 3) - radiusZ + 2
            );
            if (!WorldgenReads.chunkLoaded(level, cavityPos)) continue;
            if (!level.getBlockState(cavityPos).isAir()) continue;

            Direction facing = Direction.getRandom(random);
            BlockPos supportPos = cavityPos.relative(facing.getOpposite());
            if (!WorldgenReads.chunkLoaded(level, supportPos)) continue;
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
            if (!WorldgenReads.chunkLoaded(level, cavityPos)) continue;
            if (!level.getBlockState(cavityPos).isAir()) continue;

            for (Direction facing : Direction.values()) {
                BlockPos supportPos = cavityPos.relative(facing.getOpposite());
                if (!WorldgenReads.chunkLoaded(level, supportPos)) continue;
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
