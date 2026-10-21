package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 记录每一处幽匿古城已经生成过几枚远古魔导残片。
 *
 * <p>键是古城的结构起点区块坐标，值是已生成的残片数量；上限由
 * {@link #MAX_PER_STRUCTURE} 控制，保证同一处古城最多两枚。</p>
 */
public final class AncientFragmentSavedData extends SavedData {
    public static final int MAX_PER_STRUCTURE = 2;
    private static final String DATA_NAME = "dimensional_mana_ancient_fragments";

    private final Map<Long, Integer> counts = new HashMap<>();

    public static AncientFragmentSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AncientFragmentSavedData::new, AncientFragmentSavedData::load),
                DATA_NAME);
    }

    public static AncientFragmentSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AncientFragmentSavedData data = new AncientFragmentSavedData();
        CompoundTag stored = tag.getCompound("Counts");
        for (String key : stored.getAllKeys()) {
            try {
                data.counts.put(Long.parseLong(key), stored.getInt(key));
            } catch (NumberFormatException ignored) {
                // 损坏的键直接跳过，不影响整张表的读取。
            }
        }
        return data;
    }

    /** 该结构还能不能再生成一枚；能则记一次并返回 true。 */
    public boolean tryClaim(long structureKey) {
        int generated = counts.getOrDefault(structureKey, 0);
        if (generated >= MAX_PER_STRUCTURE) return false;
        counts.put(structureKey, generated + 1);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag stored = new CompoundTag();
        counts.forEach((key, value) -> stored.putInt(Long.toString(key), value));
        tag.put("Counts", stored);
        return tag;
    }
}
