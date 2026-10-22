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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 保存已生成魔晶洞穴的中心与规模，供浓度、检测器和洞穴守卫补刷使用。 */
public final class ManaCaveData extends SavedData {
    private static final String FILE_NAME = GeneratedMod.MOD_ID + "_mana_caves";
    private static final Factory<ManaCaveData> FACTORY = new Factory<>(
            ManaCaveData::new,
            ManaCaveData::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );
    private final List<BlockPos> caves = new ArrayList<>();
    private final Set<BlockPos> largeCaves = new HashSet<>();

    public static ManaCaveData get(ServerLevel level) {
        return (ManaCaveData) level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public synchronized void add(BlockPos pos, boolean large) {
        for (BlockPos existing : caves) {
            if (existing.distSqr(pos) < 64 * 64) {
                if (large && largeCaves.add(existing)) setDirty();
                return;
            }
        }
        BlockPos stored = pos.immutable();
        caves.add(stored);
        if (large) largeCaves.add(stored);
        setDirty();
    }

    public synchronized Optional<BlockPos> nearest(BlockPos origin) {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos cave : caves) {
            double distance = cave.distSqr(origin);
            if (distance < best) {
                best = distance;
                nearest = cave;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public synchronized boolean isLarge(BlockPos cave) {
        return largeCaves.contains(cave);
    }

    public synchronized boolean isNearCave(BlockPos pos, int radius) {
        long radiusSquared = (long)radius * radius;
        for (BlockPos cave : caves) {
            if (cave.distSqr(pos) <= radiusSquared) return true;
        }
        return false;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos cave : caves) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", cave.getX());
            entry.putInt("y", cave.getY());
            entry.putInt("z", cave.getZ());
            entry.putBoolean("large", largeCaves.contains(cave));
            list.add(entry);
        }
        tag.put("caves", list);
        return tag;
    }

    private static ManaCaveData load(CompoundTag tag, HolderLookup.Provider registries) {
        ManaCaveData data = new ManaCaveData();
        ListTag list = tag.getList("caves", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos cave = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            data.caves.add(cave);
            if (entry.getBoolean("large")) data.largeCaves.add(cave);
        }
        return data;
    }
}
