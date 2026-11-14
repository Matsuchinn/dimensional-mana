package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在相邻古代魔法祭坛布点区域之间，稀疏且可复现地生成冒险者。
 *
 * <p><b>r400 死锁修复：</b>区块加载回调（在区块「补成完整状态」那一步发出，本身就排在服务器
 * 主线程的任务队列上）里不再查地表高度图。以前这里对新加载的区块立刻
 * {@code getHeightmapPos}，只要高度图所在的区块还没就绪，游戏就会让主线程停下等它生成完，
 * 而那块区块同样排在主线程队列里 —— 世界就此卡死。现在回调里只做纯算术（区块坐标取模 + 种子随机），
 * 把候选列记进队列，落到地表的那一步在 {@link #tickLevel} 里做。</p>
 */
public final class AdventurerSpawning {
    private static final int ALTAR_SPACING_CHUNKS = 56;
    private static final int MIDPOINT = ALTAR_SPACING_CHUNKS / 2;
    private static final long SALT = 0x41D7E917A11L;
    /** 每刻最多结算几列候选落点。 */
    private static final int COLUMNS_PER_TICK = 4;
    /** 排队最久等多少刻；超时就丢掉，下次区块加载再来。 */
    private static final long QUEUE_TTL = 1200L;

    /** 待落地候选列：键为 (x, z) 打包成的 long，值为排进来的游戏刻。 */
    private static final Map<Long, Long> PENDING = new ConcurrentHashMap<>();

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) return;
        ChunkPos chunk = event.getChunk().getPos();
        int localX = Math.floorMod(chunk.x, ALTAR_SPACING_CHUNKS);
        int localZ = Math.floorMod(chunk.z, ALTAR_SPACING_CHUNKS);
        // 一类位于东西相邻祭坛区域之间，另一类位于南北相邻区域之间。
        boolean betweenEastWest = localX == 0 && localZ == MIDPOINT;
        boolean betweenNorthSouth = localZ == 0 && localX == MIDPOINT;
        if (!betweenEastWest && !betweenNorthSouth) return;

        RandomSource random = RandomSource.create(level.getSeed() ^ chunk.toLong() ^ SALT);
        // r105 权重：60% 的基准概率再乘配置权重；调到 0 时祭坛之间不再有冒险者。
        if (!MobSpawnWeights.roll(random, 0.60F, "adventurer")) return;
        int count = 1 + random.nextInt(2);
        long now = level.getGameTime();
        for (int i = 0; i < count; i++) {
            int x = chunk.getMinBlockX() + 4 + random.nextInt(8);
            int z = chunk.getMinBlockZ() + 4 + random.nextInt(8);
            if (PENDING.size() > 512) PENDING.clear();
            PENDING.put(BlockPos.asLong(x, 0, z), now);
        }
    }

    /** 服务器刻结算候选列：区块加载好了才查地表高度，再交给生成队列。 */
    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD || PENDING.isEmpty()) return;
        long now = level.getGameTime();
        int budget = COLUMNS_PER_TICK;
        Iterator<Map.Entry<Long, Long>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext() && budget > 0) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (now - entry.getValue() > QUEUE_TTL) {
                iterator.remove();
                continue;
            }
            BlockPos column = BlockPos.of(entry.getKey());
            if (!ChunkSafety.readyColumn(level, column.getX(), column.getZ())) continue;
            iterator.remove();
            budget--;
            // 区块加载回调里不直接加实体：先排队，等服务器刻、区块真正进入世界后再生成。
            WorldgenSpawnQueue.queueAdventurer(level,
                    level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
        }
    }

    private AdventurerSpawning() { }
}
