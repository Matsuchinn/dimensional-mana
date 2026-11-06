package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 元素精通的缓存与统一数值公式。
 *
 * <p>精通的来源分四层：存档里写的基础值、生物天生自带的（史莱姆 / 冒险者）、
 * 饰品（元素精通护符）和药水效果。后两层每次重算都要问 Curios 与效果表，
 * 所以这里缓存结果，但**最多只信 20 刻**：换装、喝药这类操作未必都会发事件，
 * 一秒内自动重算一次最省心，而同一刻里的多次查询依然走缓存。</p>
 */
public final class ElementMastery {
    private static final String ROOT = "DimensionalMana";
    private static final String TOTAL_KEY = "elementalMastery";
    private static final String CHEAT_KEY = "elementalMasteryCheat";
    /** 缓存有效期（刻）：到期就重算，兜住“来源变了但没发事件”的情况。 */
    private static final int CACHE_TICKS = 20;
    private static final Map<UUID, Entry> CACHE = new HashMap<>();
    /** 上次推给客户端的精通值：每秒比对一次，没变就不发包。 */
    private static final Map<UUID, Float> LAST_SYNCED = new HashMap<>();

    /** 缓存项：算出来的值与算它时实体的 tickCount。 */
    private record Entry(float value, int tick) { }

    public static float get(LivingEntity entity) {
        if (entity == null) return 0.0F;
        UUID id = entity.getUUID();
        int now = entity.tickCount;
        Entry cached = CACHE.get(id);
        if (cached != null) {
            int age = now - cached.tick();
            if (age >= 0 && age < CACHE_TICKS) return cached.value();
        }
        float value = calculate(entity);
        CACHE.put(id, new Entry(value, now));
        return value;
    }

    /** 供装备、药水或其它属性系统在数值变化后写入总精通并失效缓存。 */
    public static void setCalculated(LivingEntity entity, float value) {
        if (entity == null) return;
        root(entity).putFloat(TOTAL_KEY, Math.max(0.0F, value));
        invalidate(entity);
    }

    /** 测试用修改值，与未来装备/药水计算出的基础值相加。 */
    public static void setCheat(Player player, float value) {
        if (player == null) return;
        root(player).putFloat(CHEAT_KEY, Math.max(0.0F, value));
        invalidate(player);
    }

    public static boolean clearCheat(Player player) {
        if (player == null) return false;
        var tag = root(player);
        boolean present = tag.contains(CHEAT_KEY);
        tag.remove(CHEAT_KEY);
        invalidate(player);
        return present;
    }

    public static float cheatValue(Player player) {
        return player == null ? 0.0F : root(player).getFloat(CHEAT_KEY);
    }

    public static void invalidate(LivingEntity entity) {
        if (entity != null) CACHE.remove(entity.getUUID());
    }

    /**
     * 每秒调用一次：算出最新精通并和上次推给客户端的值比一比。
     *
     * @return 值变了（或从没推过）就返回 {@code true}，调用方据此决定要不要发包
     */
    public static boolean changedSinceSync(LivingEntity entity) {
        if (entity == null) return false;
        float value = get(entity);
        Float last = LAST_SYNCED.put(entity.getUUID(), value);
        return last == null || last.floatValue() != value;
    }

    /** 玩家离开 / 数据被重置时清掉两份缓存。 */
    public static void forget(java.util.UUID id) {
        if (id == null) return;
        CACHE.remove(id);
        LAST_SYNCED.remove(id);
    }

    public static void clear() {
        CACHE.clear();
        LAST_SYNCED.clear();
    }

    public static int level(float mastery) {
        return Math.min(10, (int)(Math.max(0.0F, mastery) / 50.0F));
    }

    public static int level(LivingEntity entity) { return level(get(entity)); }

    public static float amplifyMultiplier(float mastery) {
        return 1.0F + (2.78F * mastery) / (mastery + 1400.0F);
    }

    public static float dramaticMultiplier(float mastery) {
        return 1.0F + (16.0F * mastery) / (mastery + 2000.0F);
    }

    public static float dotMultiplier(float mastery) {
        return 1.0F + (5.0F * mastery) / (mastery + 1200.0F);
    }

    public static float chargeEchoMultiplier(float mastery) {
        return 1.0F + (10.0F * mastery) / (mastery + 1600.0F);
    }

    public static int extendDuration(int baseTicks, float mastery) {
        return baseTicks + (int)(baseTicks * Math.min(2.0F, Math.max(0.0F, mastery) * 0.01F));
    }

    public static float freezeDamage(float mastery) {
        return 1.6F * dotMultiplier(mastery);
    }

    private static float calculate(LivingEntity entity) {
        var root = root(entity);
        float calculated = Math.max(0.0F, root.getFloat(TOTAL_KEY));
        float cheat = entity instanceof Player player ? cheatValue(player) : 0.0F;
        float affix = entity instanceof Player player ? EquipmentAffixSystem.elementalMasteryBonus(player) : 0.0F;
        return calculated + intrinsic(entity) + gearBonus(entity) + potionBonus(entity) + affix + cheat;
    }

    /**
     * 生物天生自带的精通：元素史莱姆按体型给（幼崽 8 / 成熟 24 / 精英 72），
     * 冒险者本身会施法，直接给满 100。这部分不写存档，跟着实体本身走。
     */
    public static float intrinsic(LivingEntity entity) {
        if (entity instanceof ElementalSlime slime) {
            return switch (slime.tier()) {
                case CUB -> 8.0F;
                case MATURE -> 24.0F;
                case ELITE -> 72.0F;
            };
        }
        if (entity instanceof Adventurer) return 100.0F;
        return 0.0F;
    }

    /**
     * 元素精通护符：戴一件 +50，戴几件算几件（三个栏位各能放一件）。
     * 只有玩家有饰品栏，生物直接跳过，省掉每只怪一次 Curios 查询。
     */
    public static float gearBonus(LivingEntity entity) {
        if (!(entity instanceof Player)) return 0.0F;
        return ElementalMasteryCharmItem.bonusOf(entity);
    }

    /** 元素精通药水：效果等级 Ⅰ 给 +8，每升一级再多 +8。 */
    public static float potionBonus(LivingEntity entity) {
        if (entity == null) return 0.0F;
        var instance = entity.getEffect(GeneratedMod.ELEMENTAL_MASTERY);
        return instance == null ? 0.0F : ElementalMasteryEffect.bonus(instance.getAmplifier());
    }

    private static net.minecraft.nbt.CompoundTag root(LivingEntity entity) {
        ManaData.init(entity);
        return entity.getPersistentData().getCompound(ROOT);
    }

    private ElementMastery() { }
}
