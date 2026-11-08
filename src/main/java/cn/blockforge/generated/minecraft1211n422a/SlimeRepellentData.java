package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 史莱姆驱逐火把的存档数据：记下世界里所有驱逐火把的坐标，并把「5×5 区块禁刷」做成一张索引表。
 *
 * <p>为什么不用每刻扫方块：一个火把要管 25 个区块，如果刷怪时去逐个火把算距离、或者每刻
 * 遍历一遍区块里的方块，火把一多就会明显掉帧。这里改成<b>放下时登记一次</b>：
 * 每个火把在索引里写上自己覆盖的 25 个区块 key，刷怪检查就变成一次
 * {@code Map.get(区块 key)}，命中后才对那一个区块里的少量火把做距离/加载校验，
 * 常数级别，和世界大小无关。火把被破坏时把索引置空、下次查询重建，坏掉的残留条目
 * 也会在查询时顺手清掉。</p>
 *
 * <p>「火把所在区块没加载 → 驱逐暂时失效」也是在这里判定的：命中索引后还要确认
 * 至少有<b>一把火把自己所在的区块是加载的</b>，才算这个区块被禁刷。</p>
 */
public final class SlimeRepellentData extends SavedData {
    /** 存档里的数据文件名。 */
    private static final String DATA_ID = "dimensional_mana_slime_repellent_torches";
    /** 驱逐范围：以火把所在区块为中心，半径 2 区块 = 5×5 区块。 */
    public static final int CHUNK_RADIUS = 2;
    /** 已存在的史莱姆会主动躲开的距离（格）：拒绝靠近火把 16 格以内。 */
    public static final double REPEL_RADIUS = 16.0D;

    private final Set<BlockPos> torches = new LinkedHashSet<>();
    /** 区块 key → 覆盖该区块的火把；{@code null} 表示需要重建。 */
    private Map<Long, List<BlockPos>> index;
    /** 查询时发现的、已经不在原地的火把，攒够一批再一起删，避免边遍历边改集合。 */
    private final List<BlockPos> stale = new ArrayList<>();

    private SlimeRepellentData() {
    }

    public static SlimeRepellentData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SlimeRepellentData::new, SlimeRepellentData::load), DATA_ID);
    }

    /** 放下火把：登记坐标并让索引失效。 */
    public void addTorch(BlockPos pos) {
        if (this.torches.add(pos.immutable())) {
            this.index = null;
            setDirty();
        }
    }

    /** 火把没了：立刻撤销它覆盖的 25 个区块的禁刷标记。 */
    public void removeTorch(BlockPos pos) {
        if (this.torches.remove(pos)) {
            this.index = null;
            setDirty();
        }
    }

    /** 当前世界里登记了多少把驱逐火把（调试 / 指令用）。 */
    public int torchCount() {
        return this.torches.size();
    }

    /** 这个位置是否落在某把「自己所在区块已加载」的驱逐火把的 5×5 区块范围内。 */
    public boolean isWarded(ServerLevel level, BlockPos pos) {
        List<BlockPos> covering = index().get(ChunkPos.asLong(pos));
        if (covering == null) return false;
        boolean warded = false;
        for (BlockPos torch : covering) {
            if (isTorchLive(level, torch)) {
                warded = true;
                break;
            }
        }
        flushStale();
        return warded;
    }

    /**
     * 找脚下这个区块里最近的一把「生效中」的驱逐火把，没有就返回 {@code null}。
     *
     * <p>史莱姆的畏缩 AI 每 20 刻问一次；因为先按区块命中索引，正常情况只会检查
     * 一两把火把。</p>
     */
    public BlockPos nearestTorch(ServerLevel level, double x, double y, double z) {
        List<BlockPos> covering = index().get(ChunkPos.asLong(BlockPos.containing(x, y, z)));
        if (covering == null) return null;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos torch : covering) {
            if (!isTorchLive(level, torch)) continue;
            double dx = torch.getX() + 0.5D - x;
            double dy = torch.getY() + 0.5D - y;
            double dz = torch.getZ() + 0.5D - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = torch;
            }
        }
        flushStale();
        return best;
    }

    /**
     * 火把还在不在、还生不生效。
     *
     * <p>区块没加载时直接判「不生效」——这就是需求里「火把所在区块未加载则驱逐暂时失效」，
     * 而且不带 {@code isLoaded} 检查的话 {@code getBlockState} 会把区块强行读出来。</p>
     */
    private boolean isTorchLive(ServerLevel level, BlockPos torch) {
        if (!level.isLoaded(torch)) return false;
        if (level.getBlockState(torch).is(GeneratedMod.SLIME_REPELLENT_TORCH.get())) return true;
        this.stale.add(torch);
        return false;
    }

    private void flushStale() {
        if (this.stale.isEmpty()) return;
        this.torches.removeAll(this.stale);
        this.stale.clear();
        this.index = null;
        setDirty();
    }

    private Map<Long, List<BlockPos>> index() {
        if (this.index != null) return this.index;
        Map<Long, List<BlockPos>> built = new HashMap<>();
        for (BlockPos torch : this.torches) {
            int centerX = torch.getX() >> 4;
            int centerZ = torch.getZ() >> 4;
            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                    built.computeIfAbsent(ChunkPos.asLong(centerX + dx, centerZ + dz),
                            key -> new ArrayList<>(2)).add(torch);
                }
            }
        }
        this.index = built;
        return built;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] packed = new long[this.torches.size()];
        int cursor = 0;
        for (BlockPos torch : this.torches) packed[cursor++] = torch.asLong();
        tag.putLongArray("torches", packed);
        return tag;
    }

    private static SlimeRepellentData load(CompoundTag tag, HolderLookup.Provider registries) {
        SlimeRepellentData data = new SlimeRepellentData();
        for (long packed : tag.getLongArray("torches")) data.torches.add(BlockPos.of(packed));
        return data;
    }
}
