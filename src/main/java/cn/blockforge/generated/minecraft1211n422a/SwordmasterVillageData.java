package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 村庄剑士的刷新账本：<b>一座村庄这辈子只自然刷过一名剑士</b>。
 *
 * <p>以前是「刷过之后冷却 20 分钟、剑士被杀死再等 10 分钟就能补一位」，本意是让村庄不至于空掉，
 * 实际效果却是玩家杀掉一个、过一会儿转角又遇见一个，村庄永远在补人。现在改成一次性记账：
 * 只要这座村庄已经自然刷出过剑士（不管后来是死了、被雇走了还是走丢了），这里就永久留下记录，
 * 区块再怎么反复加载都不会再补第二个。想再要一名，只能靠剑士刷怪蛋或召回符咒。</p>
 *
 * <p>记录按「村庄中心所在区块」的 long 值当 key，值只存首次刷新的 gameTime，方便排查用；
 * 判断只看 key 在不在。旧存档里已经存过的村庄本来就有记录，升级后直接按「刷过」处理，
 * 不会额外多刷一名。</p>
 */
public final class SwordmasterVillageData extends SavedData {
    private static final String FILE_NAME = GeneratedMod.MOD_ID + "_swordmaster_villages";

    private static final Factory<SwordmasterVillageData> FACTORY = new Factory<>(
            SwordmasterVillageData::new,
            SwordmasterVillageData::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    /** 村庄区块 → 首次刷出剑士时的 gameTime（仅作记录，存在即代表「刷过，不再刷」）。 */
    private final Map<Long, Long> villages = new HashMap<>();

    public static SwordmasterVillageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    /** 这座村庄还没刷过剑士才返回 true；刷过一次之后永远返回 false。 */
    public boolean canSpawn(long villageKey) {
        return !villages.containsKey(villageKey);
    }

    /** 记一笔「这座村庄已经刷过剑士」，此后不再自然刷新。重复调用只会保留第一次的时间。 */
    public void markSpawned(long villageKey, long now) {
        if (villages.putIfAbsent(villageKey, now) == null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] keys = new long[villages.size()];
        long[] values = new long[villages.size()];
        int index = 0;
        for (Map.Entry<Long, Long> entry : villages.entrySet()) {
            keys[index] = entry.getKey();
            values[index] = entry.getValue();
            index++;
        }
        tag.put("Villages", new LongArrayTag(keys));
        tag.put("NextSpawn", new LongArrayTag(values));
        return tag;
    }

    private static SwordmasterVillageData load(CompoundTag tag, HolderLookup.Provider registries) {
        SwordmasterVillageData data = new SwordmasterVillageData();
        long[] keys = tag.getLongArray("Villages");
        long[] values = tag.getLongArray("NextSpawn");
        int size = Math.min(keys.length, values.length);
        for (int i = 0; i < size; i++) data.villages.put(keys[i], values[i]);
        return data;
    }
}
