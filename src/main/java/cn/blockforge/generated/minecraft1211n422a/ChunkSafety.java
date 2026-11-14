package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 运行时「这块地能不能碰」的统一闸门。
 *
 * <p>这是本轮（死锁修复）最核心的一条规矩：<b>只要不是世界生成阶段，就绝不允许让游戏为了
 * 「看一眼」而同步加载区块。</b></p>
 *
 * <p>为什么这么严重：{@code ServerLevel.getBlockState(...)} 落在没加载的区块上时，游戏会走
 * {@code ServerChunkCache.getChunk(x, z, FULL, true)}。这个方法在服务器主线程上会
 * {@code managedBlock(...)} + {@code join()}，也就是<b>把主线程停在那里等区块生成完</b>；
 * 而区块「补成完整状态」的那一步（NeoForge 里的 {@code ChunkStatusTasks.full}，区块加载事件就在
 * 那里面发出）本身又排在同一根主线程的任务队列里。主线程一边等区块、一边又是唯一能把这块区块
 * 推完的人，于是就卡死了：游戏没崩、没有 crash-report，世界却再也不往前走。
 * 装了地形模组（Tectonic 这类）以后区块生成变慢、并发更乱，撞上的概率就大幅上升。</p>
 *
 * <p>所以：刷怪、种花、粒子、结构补录这些「顺手做一下」的运行时逻辑，一律先问
 * {@link #ready} / {@link #readyAround}。区块没加载完就<b>直接放弃这一次</b>，
 * 下一轮刷怪、下一次区块加载会再来，玩家根本感觉不到，但主线程永远不会被卡住。</p>
 *
 * <p>判定用 {@code getChunkSource().getChunkNow(...)}：它只返回「已经是完整状态、真正在世界里」
 * 的区块，不会触发任何加载；在工作线程上它干脆返回 null（宁可少做一次，也不跨线程碰世界）。</p>
 */
public final class ChunkSafety {
    private ChunkSafety() {
    }

    /** 这一格所在的区块是不是已经完整加载好了。 */
    public static boolean ready(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        return readyColumn(level, pos.getX(), pos.getZ());
    }

    /** 这一列（x、z）所在的区块是不是已经完整加载好了。 */
    public static boolean readyColumn(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }

    /**
     * 以 {@code center} 为中心、半径 {@code radius} 格的立方体，所在的区块是不是全都加载好了。
     *
     * <p>用于「要写方块 / 要扫一圈方块」之前的一次性检查：只查覆盖到的区块（最多四个），
     * 不像逐格检查那样每次读方块都问一遍。</p>
     */
    public static boolean readyAround(ServerLevel level, BlockPos center, int radius) {
        return readyArea(level,
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius));
    }

    /** 两个角点围成的范围（按水平坐标算区块）是不是全都加载好了。 */
    public static boolean readyArea(ServerLevel level, BlockPos a, BlockPos b) {
        int minChunkX = Math.min(a.getX(), b.getX()) >> 4;
        int maxChunkX = Math.max(a.getX(), b.getX()) >> 4;
        int minChunkZ = Math.min(a.getZ(), b.getZ()) >> 4;
        int maxChunkZ = Math.max(a.getZ(), b.getZ()) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return false;
            }
        }
        return true;
    }

    /** 已经加载好的区块，没有就返回 null（调用方一律走「跳过」分支）。 */
    public static LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** 一个碰撞箱覆盖到的区块是不是全都加载好了（放生物、查碰撞之前用）。 */
    public static boolean readyBox(ServerLevel level, net.minecraft.world.phys.AABB box) {
        if (box == null) return true;
        return readyArea(level,
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ));
    }

    /**
     * 安全地读一格方块：区块没加载好时返回 {@code null}，绝不触发同步加载。
     *
     * <p>适合单格读取（不需要成片判断）的地方；成片扫描请先用 {@link #readyAround} 过一遍，
     * 再直接读方块，免得每一格都问一次区块。</p>
     */
    public static BlockState blockOrNull(ServerLevel level, BlockPos pos) {
        if (!ready(level, pos)) return null;
        return level.getBlockState(pos);
    }
}
