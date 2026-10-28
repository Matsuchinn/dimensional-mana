package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 记录每一处结构（村庄 / 哥布林聚落 / 古代魔法祭坛）已经掉过几份<b>法阵案例残片</b>。
 *
 * <p>键是「结构 id + 结构起点区块坐标」的组合串，值是已生成份数；上限由
 * {@link BlueprintFragmentLimitCondition} 携带的 max 控制（村庄、哥布林聚落都是每处最多 2）。</p>
 */
public final class BlueprintFragmentSavedData extends SavedData {
    private static final String DATA_NAME = "dimensional_mana_blueprint_fragments";

    private final Map<String, Integer> counts = new HashMap<>();

    public static BlueprintFragmentSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BlueprintFragmentSavedData::new, BlueprintFragmentSavedData::load),
                DATA_NAME);
    }

    public static BlueprintFragmentSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BlueprintFragmentSavedData data = new BlueprintFragmentSavedData();
        CompoundTag stored = tag.getCompound("Counts");
        for (String key : stored.getAllKeys()) {
            data.counts.put(key, stored.getInt(key));
        }
        return data;
    }

    /** 该结构还能不能再掉一份；能则记一次并返回 true。 */
    public boolean tryClaim(String structureKey, int max) {
        int generated = counts.getOrDefault(structureKey, 0);
        if (generated >= max) return false;
        counts.put(structureKey, generated + 1);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag stored = new CompoundTag();
        counts.forEach((key, value) -> stored.putInt(key, value));
        tag.put("Counts", stored);
        return tag;
    }
}
