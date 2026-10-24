package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一只生物的生物专配：固定资源、恢复速度与抗性表。
 *
 * <p>数据来自 {@link MobCombatConfig} 里的两条字符串列表；配置改动后下一次查询会自动重建缓存。</p>
 */
public final class MobProfile {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("dimensional_mana/mobs");
    private static final Map<String, MobProfile> CACHE = new HashMap<>();
    private static boolean dirty = true;

    private final Double mana;
    private final Double battleQi;
    private final Double manaRegen;
    private final Double battleQiRegen;
    private final Map<CombatElement, Double> elementResistances;
    private final Map<String, Double> specialResistances;
    private final double allResistance;
    private final boolean disabled;

    private MobProfile(Double mana, Double battleQi, Double manaRegen, Double battleQiRegen,
                       Map<CombatElement, Double> elementResistances, Map<String, Double> specialResistances,
                       double allResistance, boolean disabled) {
        this.mana = mana;
        this.battleQi = battleQi;
        this.manaRegen = manaRegen;
        this.battleQiRegen = battleQiRegen;
        this.elementResistances = elementResistances;
        this.specialResistances = specialResistances;
        this.allResistance = allResistance;
        this.disabled = disabled;
    }

    /** 配置里可以用 enabled=false 把某只生物的专配与技能整体关掉。 */
    public boolean disabled() { return disabled; }

    public Double mana() { return mana; }
    public Double battleQi() { return battleQi; }
    public Double manaRegen() { return manaRegen; }
    public Double battleQiRegen() { return battleQiRegen; }

    /** 某个元素的实际抗性（正数减伤、负数易伤）；未单列时使用 all。 */
    public double resistance(CombatElement element) {
        Double specific = element == null ? null : elementResistances.get(element);
        return specific == null ? allResistance : specific;
    }

    public double special(String key) {
        return specialResistances.getOrDefault(key, 0.0);
    }

    /**
     * 物理 / 爆炸这类「伤害类型抗性」：单独登记了就用自己的，没登记就吃 {@code all} 总抗性。
     *
     * <p>{@code all=0.25} 的语义是「全种类抗性 25%」，物理和爆炸当然也算；
     * 之前这里只读 {@link #special(String)}，导致总抗性对空手/剑砍/箭矢和爆炸完全不生效。
     * 想单独取消某一种，显式写 {@code physical=0} / {@code explosion=0} 即可。</p>
     */
    public double typeResistance(String key) {
        Double specific = specialResistances.get(key);
        return specific == null ? allResistance : specific;
    }

    /** 逐元素登记的显式抗性（不含 all 兜底），供鉴定 HUD 列出「冰 100% / 火 -75%」这类条目。 */
    public Map<CombatElement, Double> elementResistances() {
        return elementResistances;
    }

    /** all 兜底值：没有单独登记的元素都按这个算。 */
    public double allResistance() {
        return allResistance;
    }

    /** 特殊键（physical / explosion / magic_crit / melt 等）的全部登记项。 */
    public Map<String, Double> specialResistances() {
        return specialResistances;
    }

    public boolean hasSpecial(String key) {
        return specialResistances.containsKey(key) && specialResistances.get(key) != 0.0;
    }

    public boolean isEmpty() {
        return !disabled && mana == null && battleQi == null && manaRegen == null && battleQiRegen == null
                && elementResistances.isEmpty() && specialResistances.isEmpty() && allResistance == 0.0;
    }

    /** 按实体类型取专配；没有任何专配时返回 null。 */
    public static MobProfile of(EntityType<?> type) {
        if (type == null) return null;
        return byId(key(type));
    }

    /** 按实体 ID 取专配，可以省略 {@code minecraft:}；没有专配时返回 null。 */
    public static MobProfile byId(String id) {
        String normalized = normalizeId(id);
        if (normalized == null) return null;
        if (dirty) ensureBuilt();
        return CACHE.get(normalized);
    }

    /** 配置加载或热重载后调用，下一次查询重新解析。 */
    public static synchronized void invalidate() {
        dirty = true;
    }

    public static String key(EntityType<?> type) {
        ResourceLocation id = type.builtInRegistryHolder().key().location();
        return id.toString();
    }

    /** 配置列表里登记过的生物 ID（已补全命名空间），给 /dimensional_mana mob list 用。 */
    public static java.util.LinkedHashSet<String> configuredIds() {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (String line : statLines()) {
            String id = lineId(line);
            if (id != null) ids.add(id);
        }
        for (String line : resistanceLines()) {
            String id = lineId(line);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private static List<? extends String> statLines() {
        try {
            if (MobCombatConfig.SPEC.isLoaded()) return MobCombatConfig.SPECIAL_MOBS.get();
        } catch (IllegalStateException notLoadedYet) {
            // 配置还没建好：退回内置默认值。
        }
        return MobCombatConfig.defaultSpecialMobs();
    }

    private static List<? extends String> resistanceLines() {
        try {
            if (MobCombatConfig.SPEC.isLoaded()) return MobCombatConfig.RESISTANCES.get();
        } catch (IllegalStateException notLoadedYet) {
            // 配置还没建好：退回内置默认值。
        }
        return MobCombatConfig.defaultResistances();
    }

    private static synchronized void ensureBuilt() {
        List<? extends String> mobs = statLines();
        List<? extends String> resistances = resistanceLines();
        dirty = false;
        CACHE.clear();

        Map<String, Builder> builders = new LinkedHashMap<>();
        // 先铺一层内置默认值，再用配置文件覆盖：删掉某一行只会回到默认值，不会让生物失去专配。
        for (String line : MobCombatConfig.defaultSpecialMobs()) parseLine(line, builders);
        for (String line : MobCombatConfig.defaultResistances()) parseLine(line, builders);
        for (String line : mobs) parseLine(line, builders);
        for (String line : resistances) parseLine(line, builders);
        for (Map.Entry<String, Builder> entry : builders.entrySet()) {
            MobProfile profile = entry.getValue().build();
            if (!profile.isEmpty()) CACHE.put(entry.getKey(), profile);
        }
        LOGGER.info("Loaded {} mob combat profiles ({} stat lines, {} resistance lines)",
                CACHE.size(), mobs.size(), resistances.size());
    }

    private static Builder builder(Map<String, Builder> builders, String id) {
        return builders.computeIfAbsent(id.toLowerCase(Locale.ROOT), ignored -> new Builder());
    }

    /**
     * 解析一行配置。special_mobs 与 resistances 两条列表走同一套键：
     * 先按键名判断这是资源键还是抗性键，所以一只生物的值写在哪条列表里都认。
     */
    private static void parseLine(String line, Map<String, Builder> builders) {
        String[] parts = tokens(line);
        if (parts.length == 0) return;
        String id = normalizeId(parts[0]);
        if (id == null) return;
        Builder builder = builder(builders, id);
        for (int i = 1; i < parts.length; i++) {
            String[] pair = pair(parts[i]);
            if (pair == null) {
                applyFlag(builder, parts[i]);
                continue;
            }
            Double value = number(pair[1]);
            if (value == null) continue;
            apply(builder, pair[0].toLowerCase(Locale.ROOT), value);
        }
    }

    private static void apply(Builder builder, String key, double value) {
        switch (key) {
            case "mana", "m" -> builder.mana = value;
            case "battle_qi", "battleqi", "qi", "q" -> builder.battleQi = value;
            case "mana_regen", "mr" -> builder.manaRegen = value;
            case "battle_qi_regen", "battleqi_regen", "qi_regen", "qr" -> builder.battleQiRegen = value;
            case "enabled", "enable", "on" -> builder.disabled = value == 0.0;
            case "disabled", "disable", "off" -> builder.disabled = value != 0.0;
            case "all" -> builder.all = value;
            default -> {
                CombatElement element = CombatElement.byId(key);
                if (element != null) {
                    builder.elements.put(element, value);
                } else {
                    builder.specials.put(key, value);
                }
            }
        }
    }

    /** 只用空格分隔时允许直接写 {@code off} / {@code on} 开关这只生物的专配。 */
    private static void applyFlag(Builder builder, String token) {
        switch (token.toLowerCase(Locale.ROOT)) {
            case "off", "disable", "disabled" -> builder.disabled = true;
            case "on", "enable", "enabled" -> builder.disabled = false;
            default -> { }
        }
    }

    /** 这个键写进 special_mobs（资源类）；否则当抗性键写进 resistances。 */
    public static boolean isStatKey(String key) {
        if (key == null) return false;
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "mana", "m", "battle_qi", "battleqi", "qi", "q", "mana_regen", "mr",
                 "battle_qi_regen", "battleqi_regen", "qi_regen", "qr",
                 "enabled", "enable", "on", "disabled", "disable", "off" -> true;
            default -> false;
        };
    }

    /** 认不认识这个键：资源键、12 元素、物理/爆炸/法术暴击/融化之外的一律拒绝，避免写进无意义的条目。 */
    public static boolean knownKey(String key) {
        if (key == null) return false;
        if (isStatKey(key)) return true;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.equals("all") || CombatElement.byId(normalized) != null) return true;
        return switch (normalized) {
            case "physical", "explosion", "magic_crit", "melt" -> true;
            default -> false;
        };
    }

    /** 写回配置时用的短键名：够短，游戏内配置界面的 32 字符输入框也放得下。 */
    public static String shortKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mana", "m" -> "m";
            case "battle_qi", "battleqi", "qi", "q" -> "q";
            case "mana_regen", "mr" -> "mr";
            case "battle_qi_regen", "battleqi_regen", "qi_regen", "qr" -> "qr";
            case "enabled", "enable", "on" -> "on";
            case "disabled", "disable", "off" -> "off";
            case "all", "*" -> "all";
            default -> normalized;
        };
    }

    /** 补全命名空间：{@code wither} 与 {@code minecraft:wither} 是同一只生物。 */
    static String normalizeId(String raw) {
        if (raw == null) return null;
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) return null;
        if (id.indexOf(':') < 0) id = "minecraft:" + id;
        return id;
    }

    /** 一行配置里的生物 ID（已补全命名空间）；空行或坏行返回 null。 */
    static String lineId(String line) {
        String[] parts = tokens(line);
        return parts.length == 0 ? null : normalizeId(parts[0]);
    }

    /** 分隔符：竖线、逗号、分号、空格都行；一只生物也可以拆成多行，同 ID 的行会自动合并。 */
    static String[] tokens(String line) {
        if (line == null || line.isBlank()) return new String[0];
        List<String> tokens = new java.util.ArrayList<>();
        for (String token : line.trim().split("[|,;\\s]+")) {
            if (!token.isBlank()) tokens.add(token);
        }
        return tokens.toArray(new String[0]);
    }

    /** 键值分隔用 {@code =} 或 {@code :}；没有分隔符或值被截断成空的词直接忽略。 */
    static String[] pair(String token) {
        int split = token.indexOf('=');
        if (split < 0) split = token.indexOf(':');
        if (split <= 0 || split == token.length() - 1) return null;
        return new String[]{token.substring(0, split).trim(), token.substring(split + 1).trim()};
    }

    static Double number(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static final class Builder {
        private boolean disabled;
        private Double mana;
        private Double battleQi;
        private Double manaRegen;
        private Double battleQiRegen;
        private double all;
        private final Map<CombatElement, Double> elements = new EnumMap<>(CombatElement.class);
        private final Map<String, Double> specials = new HashMap<>();

        private MobProfile build() {
            return new MobProfile(mana, battleQi, manaRegen, battleQiRegen,
                    Collections.unmodifiableMap(elements), Collections.unmodifiableMap(specials), all, disabled);
        }
    }
}
