package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashSet;
import java.util.Set;

/** 持久记录已安置居民的营地，清空营地后读档不凭空刷回首领。 */
public final class GoblinCampData extends SavedData {
    private static final Factory<GoblinCampData> FACTORY = new Factory<>(GoblinCampData::new,
            GoblinCampData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Set<Long> populated = new HashSet<>();
    public static GoblinCampData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, GeneratedMod.MOD_ID+"_goblin_camps");
    }
    public boolean contains(BlockPos center) { return populated.contains(center.asLong()); }
    public void mark(BlockPos center) { if(populated.add(center.asLong())) setDirty(); }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLongArray("populated",populated.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
    private static GoblinCampData load(CompoundTag tag, HolderLookup.Provider provider) {
        GoblinCampData data=new GoblinCampData();
        for(long p:tag.getLongArray("populated")) data.populated.add(p);
        return data;
    }
}
