package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 被【虚无凋零射线】的扭曲光环抽干过的区域。
 *
 * <p>光环每扫过一次，就把一块半径 12 格的球形区域登记进来，该区域的环境魔素浓度
 * 直接乘 0.5（{@link ManaConcentration#at} 在最后一步乘这个系数）；登记 12000 刻
 * （10 分钟）后自动过期，浓度自然恢复。</p>
 *
 * <p>只存中心、半径和过期刻数，跟着存档走；一场战斗最多几十个区域，
 * 每刻遍历一次的开销可以忽略。</p>
 */
public final class CorruptedZoneData extends SavedData {
    /** 浓度乘区：砍掉一半。 */
    public static final double CONCENTRATION_FACTOR = 0.5D;

    private static final String FILE_NAME = GeneratedMod.MOD_ID + "_corrupted_zones";
    private static final Factory<CorruptedZoneData> FACTORY = new Factory<>(
            CorruptedZoneData::new,
            CorruptedZoneData::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    /** 一块被抽干的区域。 */
    private record Zone(BlockPos center, double radius, long expireAt) { }

    private final List<Zone> zones = new ArrayList<>();

    public static CorruptedZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public synchronized void add(BlockPos center, double radius, int durationTicks, long gameTime) {
        if (center == null) return;
        BlockPos immutable = center.immutable();
        zones.removeIf(zone -> zone.center().equals(immutable));
        zones.add(new Zone(immutable, radius, gameTime + durationTicks));
        setDirty();
    }

    /** 这个坐标的浓度乘区；不在任何被抽干的区域里就是 1.0。 */
    public synchronized double factorAt(BlockPos pos, long gameTime) {
        if (zones.isEmpty()) return 1.0D;
        zones.removeIf(zone -> zone.expireAt() <= gameTime);
        if (zones.isEmpty()) return 1.0D;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        for (Zone zone : zones) {
            double dx = x - (zone.center().getX() + 0.5D);
            double dy = y - (zone.center().getY() + 0.5D);
            double dz = z - (zone.center().getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz <= zone.radius() * zone.radius()) return CONCENTRATION_FACTOR;
        }
        return 1.0D;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Zone zone : zones) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", zone.center().getX());
            entry.putInt("y", zone.center().getY());
            entry.putInt("z", zone.center().getZ());
            entry.putDouble("r", zone.radius());
            entry.putLong("expire", zone.expireAt());
            list.add(entry);
        }
        tag.put("zones", list);
        return tag;
    }

    private static CorruptedZoneData load(CompoundTag tag, HolderLookup.Provider registries) {
        CorruptedZoneData data = new CorruptedZoneData();
        ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.zones.add(new Zone(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")),
                    entry.getDouble("r"), entry.getLong("expire")));
        }
        return data;
    }

    private CorruptedZoneData() { }
}
