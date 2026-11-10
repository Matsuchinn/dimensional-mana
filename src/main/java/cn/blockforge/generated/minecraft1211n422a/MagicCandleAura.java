package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 魔法蜡烛的魔素光环结算（区块缓存版）。
 *
 * <p>规则：每根<b>点燃</b>的蜡烛给周围 5×5×5 立方内的环境魔素浓度 +0.15，
 * 每根<b>未点燃</b>的 −0.15；一格最多 4 根，所以单格蜡烛的贡献在 ±0.6。
 * 同一区域内有点燃也有未点燃时先互相抵消，再整体加到基准浓度上；
 * 光环的总加成封顶 +16.0，负加成由 {@link ManaConcentration} 的最终下限 0 兜底。</p>
 *
 * <p><b>为什么用缓存</b>：浓度查询是「每只生物每秒一次」级别的调用，绝不能每 tick
 * 遍历区块找蜡烛。这里给每个区块维护一份「已知蜡烛位置清单」：</p>
 * <ul>
 *   <li>清单里的位置每次查询都直接读<b>活方块状态</b>——点燃 / 熄灭 / 加根数都不动清单；</li>
 *   <li>放蜡烛走 {@link #onPlaced} 立刻登记，拆蜡烛走 {@link #onRemoved} 立刻销号；</li>
 *   <li>兜底：每 30 秒对访问过的区块用调色板预筛重扫一遍，抓住活塞推移、结构放置
 *       这类没走方块钩子的变化（{@code maybeHas} 判定「这一段里根本没有蜡烛」只需比对
 *       调色板里的几种方块，代价可以忽略）。</li>
 * </ul>
 *
 * <p>缓存按维度实例组织在 {@link WeakHashMap} 里：服务器关闭、Level 被回收后自动清掉，
 * 不需要落盘——它只是查询加速器，丢了最坏也只是等下一次兜底重扫。</p>
 */
public final class MagicCandleAura {
    /** 每根蜡烛的浓度增减。 */
    public static final double PER_CANDLE = 0.15D;
    /** 光环总加成的上限（−方向不设下限，由浓度本身的 0 下限兜底）。 */
    public static final double MAX_BONUS = 16.0D;
    /** 光环半径（切比雪夫距离）：5×5×5。 */
    public static final int RADIUS = 2;
    /** 兜底重扫间隔：30 秒。 */
    private static final long RESCAN_INTERVAL_TICKS = 600L;

    private static final class ChunkIndex {
        final List<BlockPos> candles = new ArrayList<>();
        long nextRescan;
    }

    private static final Map<ServerLevel, Map<ChunkPos, ChunkIndex>> INDEXES = new WeakHashMap<>();

    private MagicCandleAura() { }

    /** 服务器停止时清缓存（WeakHashMap 会自己回收，这里再主动清一次，避免跨存档残留）。 */
    public static void clear() {
        synchronized (INDEXES) {
            INDEXES.clear();
        }
    }

    /** 放下一根蜡烛：立刻登记进所在区块的清单。 */
    public static void onPlaced(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        synchronized (INDEXES) {
            ChunkIndex index = indexFor(server, new ChunkPos(pos), level.getGameTime(), false);
            if (!index.candles.contains(pos)) index.candles.add(pos.immutable());
        }
    }

    /** 拆掉一格蜡烛：从清单里销号。 */
    public static void onRemoved(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        synchronized (INDEXES) {
            Map<ChunkPos, ChunkIndex> byChunk = INDEXES.get(server);
            if (byChunk == null) return;
            ChunkIndex index = byChunk.get(new ChunkPos(pos));
            if (index != null) index.candles.removeIf(existing -> existing.equals(pos));
        }
    }

    /**
     * 查询点的蜡烛光环增减：扫过半径覆盖到的区块（最多 2×2 个），
     * 对清单里的蜡烛按活方块状态算 ±0.15 × 根数，抵消后封顶 +16。
     */
    public static double bonusAt(ServerLevel level, BlockPos pos) {
        double sum = 0.0D;
        long now = level.getGameTime();
        int minChunkX = (pos.getX() - RADIUS) >> 4;
        int maxChunkX = (pos.getX() + RADIUS) >> 4;
        int minChunkZ = (pos.getZ() - RADIUS) >> 4;
        int maxChunkZ = (pos.getZ() + RADIUS) >> 4;
        synchronized (INDEXES) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) continue; // 没加载的区块里没有可算的蜡烛
                    ChunkIndex index = indexFor(level, chunk.getPos(), now, true);
                    Iterator<BlockPos> it = index.candles.iterator();
                    while (it.hasNext()) {
                        BlockPos candle = it.next();
                        if (Math.max(Math.abs(candle.getX() - pos.getX()),
                                Math.max(Math.abs(candle.getY() - pos.getY()),
                                        Math.abs(candle.getZ() - pos.getZ()))) > RADIUS) continue;
                        BlockState state = level.getBlockState(candle);
                        if (!(state.getBlock() instanceof MagicCandleBlock)) {
                            it.remove(); // 清单里的位置已经不是蜡烛：顺手清掉
                            continue;
                        }
                        int count = state.getValue(net.minecraft.world.level.block.CandleBlock.CANDLES);
                        boolean lit = state.getValue(net.minecraft.world.level.block.CandleBlock.LIT);
                        sum += (lit ? count : -count) * PER_CANDLE;
                    }
                }
            }
        }
        return Math.min(MAX_BONUS, sum);
    }

    /**
     * 取区块清单；{@code allowRescan} 时顺带做兜底重扫（查询路径）。
     * 调用方必须持有 {@link #INDEXES} 的锁。
     */
    private static ChunkIndex indexFor(ServerLevel level, ChunkPos chunkPos, long now, boolean allowRescan) {
        Map<ChunkPos, ChunkIndex> byChunk = INDEXES.computeIfAbsent(level, k -> new java.util.HashMap<>());
        ChunkIndex index = byChunk.get(chunkPos);
        if (index == null) {
            index = new ChunkIndex();
            byChunk.put(chunkPos, index);
            rescan(level, chunkPos, index);
            index.nextRescan = now + RESCAN_INTERVAL_TICKS;
            return index;
        }
        if (allowRescan && now >= index.nextRescan) {
            rescan(level, chunkPos, index);
            index.nextRescan = now + RESCAN_INTERVAL_TICKS;
        }
        return index;
    }

    /** 调色板预筛 + 实扫：重建该区块的蜡烛位置清单。 */
    private static void rescan(ServerLevel level, ChunkPos chunkPos, ChunkIndex index) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        index.candles.clear();
        if (chunk == null) return;
        LevelChunkSection[] sections = chunk.getSections();
        int baseSection = level.getMinSection();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(state -> state.getBlock() instanceof MagicCandleBlock)) continue;
            int sectionY = (baseSection + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (section.getBlockState(x, y, z).getBlock() instanceof MagicCandleBlock) {
                            index.candles.add(new BlockPos(chunkPos.getMinBlockX() + x, sectionY + y,
                                    chunkPos.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
    }

}
