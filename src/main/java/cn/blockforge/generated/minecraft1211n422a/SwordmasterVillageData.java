package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 村庄剑士的补充账本。
 *
 * <p>每个村庄按「村庄中心所在区块」记一个时间戳：正常村庄常驻 1 名，生成一次后 20 分钟内
 * 不会重复刷；剑士被打死后 10 分钟后才允许补充。这样区块反复加载不会刷出一排剑士，
 * 玩家也不会在同一个村庄里「杀掉一个、转角又遇见一个」。</p>
 */
public final class SwordmasterVillageData extends SavedData {
    private static final String FILE_NAME = GeneratedMod.MOD_ID + "_swordmaster_villages";
    private static final long SPAWN_COOLDOWN = 20_000L;
    private static final long DEFEAT_COOLDOWN = 12_000L;

    private static final Factory<SwordmasterVillageData> FACTORY = new Factory<>(
            SwordmasterVillageData::new,
            SwordmasterVillageData::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    /** 村庄区块 → 下一次允许刷新的 gameTime。 */
    private final Map<Long, Long> villages = new HashMap<>();

    public static SwordmasterVillageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    /** 这个村庄现在能不能刷一位剑士。 */
    public boolean canSpawn(long villageKey, long now) {
        Long next = villages.get(villageKey);
        return next == null || now >= next;
    }

    /** 记一笔「这座村庄刚刷过」。 */
    public void markSpawned(long villageKey, long now) {
        villages.put(villageKey, now + SPAWN_COOLDOWN);
        setDirty();
    }

    /** 剑士死亡：村庄进入补充冷却。带一点随机抖动，避免同一片村庄同时补人。 */
    public void markDefeated(ServerLevel level, BlockPos home, long now) {
        if (home == null) return;
        villages.put(new ChunkPos(home).toLong(), now + DEFEAT_COOLDOWN + level.random.nextInt(2000));
        setDirty();
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
