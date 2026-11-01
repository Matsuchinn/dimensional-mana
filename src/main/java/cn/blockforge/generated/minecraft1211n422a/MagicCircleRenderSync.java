package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端→客户端的法阵渲染包广播器（性能设计的第二道闸门）。
 *
 * <p>阵图存在维度存档 {@link MagicCircleNetworks} 里，客户端世界渲染却拿不到 SavedData，
 * 所以每个维度每 {@value #INTERVAL} 刻做一次「指纹对比」：算一遍结构哈希，
 * 没变的阵盘直接跳过；变了才把摊平好的 {@link MagicCircleRenderPacket}
 * 写进原点基座的方块实体并发 updateTag。客户端渲染器每帧零计算、只读缓存。</p>
 *
 * <p>指纹用的是渲染包自身的 {@link MagicCircleRenderPacket#signature()}：
 * 节点位置、颜色、图形、层号、连线全在里面，改一个粉末槽都会翻出来。</p>
 */
public final class MagicCircleRenderSync {
    /** 刷新间隔（刻）。绘制台是"改完看一眼"的东西，半秒的延迟肉眼不可感，换来零突发开销。 */
    private static final int INTERVAL = 10;

    /** 维度 → (阵盘 id → 上次指纹)。level 侧单线程读写，Map 只为跨维度隔离。 */
    private static final Map<String, Map<String, Long>> SIGNATURES = new ConcurrentHashMap<>();

    /** 每个维度的原点基座缓存：阵盘没变就不重新扫 bases 找最小角。 */
    private static final Map<String, Map<String, BlockPos>> ORIGINS = new ConcurrentHashMap<>();

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL != 0) return;
        String dim = level.dimension().location().toString();
        MagicCircleNetworks data = MagicCircleNetworks.get(level);
        Map<String, Long> known = SIGNATURES.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        Map<String, BlockPos> origins = ORIGINS.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        List<String> alive = new ArrayList<>();

        for (MagicCircleNetwork network : data.all()) {
            alive.add(network.id());
            MagicCircleRenderPacket packet = MagicCircleRenderPacket.build(
                    network.layers(), network.baseWidth(), network.baseHeight());
            long signature = packet.signature();
            if (signature == known.getOrDefault(network.id(), 0L)) continue;

            BlockPos origin = originOf(network);
            // 原点区块没加载：这轮不算账（known 不更新），等区块回来自动补发。
            if (origin == null || !publish(level, origin, packet)) continue;
            known.put(network.id(), signature);
            BlockPos previous = origins.put(network.id(), origin);
            if (previous != null && !previous.equals(origin)) {
                publish(level, previous, MagicCircleRenderPacket.EMPTY); // 换原点，旧的擦干净
            }
        }

        // 阵盘被拆 / 注销：指纹表跟着删，别越积越多
        Iterator<String> it = known.keySet().iterator();
        while (it.hasNext()) if (!alive.contains(it.next())) it.remove();
        origins.keySet().retainAll(alive);
    }

    /** 写包并广播；原点基座的方块实体没加载（区块卸载）时返回 false，本轮不算同步过。 */
    private static boolean publish(ServerLevel level, BlockPos pos, MagicCircleRenderPacket packet) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof MagicCircleBaseBlockEntity base) {
            base.publishRenderPacket(packet);
            return true;
        }
        return false;
    }

    /** 西北角那块基座是渲染原点：整个画布以它为 (0,0) 铺开。 */
    private static BlockPos originOf(MagicCircleNetwork network) {
        BlockPos best = null;
        for (BlockPos base : network.bases()) {
            if (best == null || base.getX() < best.getX()
                    || (base.getX() == best.getX() && base.getZ() < best.getZ())) {
                best = base;
            }
        }
        return best;
    }

    /** 服务器停服时清表，换档不误用旧维度指纹。 */
    public static void clearAll() {
        SIGNATURES.clear();
        ORIGINS.clear();
    }

    private MagicCircleRenderSync() {}
}
