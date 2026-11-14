package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 大范围「附近有没有某种方块」的扫描器：先问区块段的调色板，再逐格看。
 *
 * <p>刷怪落点的判定里有一堆「6～9 格内有没有岩浆 / 水 / 铁轨 / 魔晶矿」的检查。
 * 半径 6 格就是 13×13×13 = 2197 次 {@code getBlockState}，而一次刷怪尝试最多要跑十轮，
 * 每 4 秒对每个玩家来一遍——绝大多数时候这些方块根本不存在，白花的钱全在查空气。</p>
 *
 * <p>区块段（16×16×16）自带调色板，{@link LevelChunkSection#maybeHas(Predicate)} 能在
 * 「这一段里不可能有匹配的方块」时直接回答 false，代价只是一次调色板比对。
 * 这里先按段筛一遍：一段都没有就直接返回；有才去逐格看，而且只查被筛中的那几段。
 * 判定结果和原来逐格扫完全一致（调色板从不误判「没有」），只是把常见情况的成本从几千次
 * 方块读取降到几次。</p>
 *
 * <p>没加载的区块不碰：既不会为了看一眼而让游戏去生成地形，也不会读到半成品区块。</p>
 */
public final class SectionScan {
    private SectionScan() { }

    /**
     * 这一列方块所在的区块是不是已经完整加载好了。
     *
     * <p>刷怪只往「已经在那里」的地方放生物：不替玩家预生成远处的地形，也不会读到半成品区块。
     * 用 {@code getChunkNow} 而不是 {@code hasChunkAt}，是因为前者明确只返回已经加载完的整块区块。</p>
     */
    public static boolean loadedColumn(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }

    /**
     * 以 {@code center} 为中心、半径 {@code radius} 格的立方体里，有没有方块满足 {@code test}。
     *
     * @param step 采样步长（1 = 逐格；2 = 隔一格），和原来手写扫描的步长保持一致
     */
    public static boolean anyNear(ServerLevel level, BlockPos center, int radius, int step, Predicate<BlockState> test) {
        if (radius < 0 || step < 1) return false;
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        if (minY > maxY) return false;
        int minSection = Math.max(0, (minY - level.getMinBuildHeight()) >> 4);
        int maxSection = Math.min(level.getSectionsCount() - 1, (maxY - level.getMinBuildHeight()) >> 4);
        if (minSection > maxSection) return false;

        // 第一遍：挑出「调色板里可能有目标方块」的段。一段都没有就不用逐格看了。
        Set<LevelChunkSection> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = minSection; index <= maxSection && index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    if (section == null || section.hasOnlyAir()) continue;
                    if (section.maybeHas(test)) candidates.add(section);
                }
            }
        }
        if (candidates.isEmpty()) return false;

        // 第二遍：只查被筛中的段，并且直接从段里读方块，不再走一次世界的坐标查找。
        // 坐标按区块夹紧并对齐到原来的取样格点，保证和逐格扫的取样位置完全一致。
        int baseY = level.getMinBuildHeight();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            int chunkMinX = chunkX << 4;
            int startX = alignUp(Math.max(minX, chunkMinX), minX, step);
            int endX = Math.min(maxX, chunkMinX + 15);
            if (startX > endX) continue;
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                int chunkMinZ = chunkZ << 4;
                int startZ = alignUp(Math.max(minZ, chunkMinZ), minZ, step);
                int endZ = Math.min(maxZ, chunkMinZ + 15);
                if (startZ > endZ) continue;
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int y = minY; y <= maxY; y += step) {
                    int index = (y - baseY) >> 4;
                    if (index < 0 || index >= sections.length) continue;
                    LevelChunkSection section = sections[index];
                    if (section == null || !candidates.contains(section)) continue;
                    for (int x = startX; x <= endX; x += step) {
                        for (int z = startZ; z <= endZ; z += step) {
                            if (test.test(section.getBlockState(x & 15, y & 15, z & 15))) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** 把 {@code value} 抬到「与 {@code origin} 同余（模 {@code step}）」的最小值。 */
    private static int alignUp(int value, int origin, int step) {
        if (step <= 1) return value;
        int remainder = Math.floorMod(value - origin, step);
        return remainder == 0 ? value : value + (step - remainder);
    }

    /** 几种方块里的任意一种（对应原来的 {@code nearAny(..., Blocks...)}）。 */
    public static boolean anyNearAny(ServerLevel level, BlockPos center, int radius, int step, net.minecraft.world.level.block.Block... blocks) {
        if (blocks.length == 0) return false;
        Predicate<BlockState> test = blocks.length == 1
                ? state -> state.is(blocks[0])
                : state -> {
                    for (net.minecraft.world.level.block.Block block : blocks) {
                        if (state.is(block)) return true;
                    }
                    return false;
                };
        return anyNear(level, center, radius, step, test);
    }
}
