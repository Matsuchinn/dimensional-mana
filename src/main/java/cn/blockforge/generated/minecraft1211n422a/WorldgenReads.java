package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelReader;

/**
 * 世界生成阶段的安全读取工具。
 *
 * <p>区块生成时，游戏只把“正在生成的那块区块和它依赖的邻近区块”摆出来（{@link WorldGenRegion}）。
 * 读到范围外的区块，游戏不会返回 null，而是直接抛
 * {@code IllegalStateException: Requested chunk unavailable during world generation} 把存档弄崩。
 * 所以凡是范围可能超过一个区块的读取（比如找十六格外的群系、扫大范围找方块），先问一句这里可不可读。</p>
 *
 * <p>已经生成好的正常世界不是 {@link WorldGenRegion}，这些检查恒为 true，不影响运行时逻辑。</p>
 */
public final class WorldgenReads {
    private WorldgenReads() {
    }

    /** 这一格所在的区块在当前生成区域里吗？ */
    public static boolean chunkLoaded(LevelReader level, BlockPos pos) {
        if (!(level instanceof WorldGenRegion region)) return true;
        return region.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * 这一格能不能安全地问群系？
     *
     * <p>问群系时游戏要做一次“群系缩放”，顺带采样这格周围约四格，所以把那一圈区块也检查进去，
     * 免得格子本身可读、缩放采样却跨到还没生成的区块。</p>
     */
    public static boolean biomeReadable(LevelReader level, BlockPos pos) {
        if (!(level instanceof WorldGenRegion region)) return true;
        int minX = (pos.getX() - 4) >> 4;
        int maxX = (pos.getX() + 4) >> 4;
        int minZ = (pos.getZ() - 4) >> 4;
        int maxZ = (pos.getZ() + 4) >> 4;
        return region.hasChunk(minX, minZ) && region.hasChunk(maxX, minZ)
                && region.hasChunk(minX, maxZ) && region.hasChunk(maxX, maxZ);
    }
}
