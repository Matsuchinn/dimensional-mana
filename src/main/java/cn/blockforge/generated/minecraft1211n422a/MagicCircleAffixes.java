package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 输出节点重构（规范 v2.2 第一部分）的词缀唯一数据源。
 *
 * <p>输出节点有两种身份，由连接关系自动判定：</p>
 * <ul>
 *   <li><b>终端模式</b>：没有下游输出连接 → 直接结算效果（伤害 / 治疗 / 信号 / 召唤 …），
 *       主协议由语义槽的七种原版粉决定（规范 3.1~3.7）；</li>
 *   <li><b>前置模式</b>：连了下游输出节点 → 自身不结算，把「元素 + 语义 + 染料 + 功率」
 *       编译成一份词缀（规范 4.1 / 4.2 / 5），塞给下游终端。</li>
 * </ul>
 *
 * <p>词缀容量由<b>终端</b>功率槽决定：下 1 / 中 2 / 高 3 / 极 4 / 特 5 / 超导 6，
 * 核心糖模式 +1、超导连线 +1，硬上限 8（规范 6.1）。每条词缀复杂度 1~3，
 * 终端基础值 = 原基础值 + Σ(复杂度 × 复杂度税单价)——只改基础值，不动 5 乘区公式（规范 6.2）。
 * 词缀标签命中终端主协议时数值 ×1.15（同意义加成，同标签只生效最高的一个，规范 6.3）。</p>
 */
public final class MagicCircleAffixes {

    // ---------------------------------------------------------------- 终端主协议

    /** 终端七大协议分支（规范 3.1~3.7）；NONE = 语义槽没放原版粉，由前置词缀驱动。 */
    public enum Protocol {
        NONE("none"),
        SIGNAL("signal"),     // 红石 —— 信号 / 逻辑执行器
        LIFE("life"),         // 糖 —— 生命 / 治疗 / 召唤执行器
        DAMAGE("damage"),     // 火药 —— 伤害 / 爆炸 / 扩散执行器
        PURIFY("purify"),     // 烈焰 —— 净化 / 高温执行器
        LIGHT("light"),       // 萤石 —— 照明 / 显形 / 投影执行器
        SCAN("scan"),         // 青金 —— 精准 / 洞察 / 经验执行器
        CHARGE("charge");     // 紫水晶 —— 充能 / 探测 / 传送 / 共鸣执行器

        private final String id;

        Protocol(String id) { this.id = id; }

        public String id() { return id; }

        public String translationKey() { return "gui.dimensional_mana.magiccircle.protocol." + id; }

        /** 语义槽物品 → 主协议；空槽或不认识的粉返回 NONE（词缀驱动）。 */
        public static Protocol ofSemantic(String semanticItemId) {
            if (semanticItemId == null || semanticItemId.isEmpty()) return NONE;
            return switch (semanticItemId) {
                case "minecraft:redstone" -> SIGNAL;
                case "minecraft:sugar" -> LIFE;
                case "minecraft:gunpowder" -> DAMAGE;
                case "minecraft:blaze_powder" -> PURIFY;
                case "minecraft:glowstone_dust" -> LIGHT;
                case "dimensional_mana:lapis_dust" -> SCAN;
                case "dimensional_mana:amethyst_dust" -> CHARGE;
                default -> NONE;
            };
        }

        /** 报错文案里给玩家看的短名（终端#3（糖+木·友好召唤）那种）。 */
        public String shortName() {
            return switch (this) {
                case NONE -> "词缀驱动";
                case SIGNAL -> "红石";
                case LIFE -> "糖";
                case DAMAGE -> "火药";
                case PURIFY -> "烈焰";
                case LIGHT -> "萤石";
                case SCAN -> "青金";
                case CHARGE -> "紫晶";
            };
        }
    }

    // ---------------------------------------------------------------- 词缀

    /**
     * 一条词缀：稳定短 id + 归类标签 + 复杂度 1~3。运行时 {@link MagicCircleRites} 按标签改变终端行为。
     */
    public record Affix(String id, String tag, int complexity) {

        public String translationKey() { return "affix.dimensional_mana." + id; }

        /** 界面里显示的行：「爆炸（复杂度 2）」。 */
        public String describe() {
            return cn.blockforge.generated.minecraft1211n422a.MagicCircleAffixes
                    .affixName(id) + "（复杂度 " + complexity + "）";
        }
    }

    /** 规范 4.1：现有核心词缀，键 = 语义|元素。 */
    private static final Map<String, Affix> BY_SEMANTIC_ELEMENT = new LinkedHashMap<>();
    /** 规范 4.2：空语义的基础词缀（元素自带），键 = 元素。 */
    private static final Map<String, Affix> BY_EMPTY_SEMANTIC = new LinkedHashMap<>();

    private static final String REDSTONE = "minecraft:redstone";
    private static final String SUGAR = "minecraft:sugar";
    private static final String GUNPOWDER = "minecraft:gunpowder";
    private static final String BLAZE = "minecraft:blaze_powder";
    private static final String GLOWSTONE = "minecraft:glowstone_dust";
    private static final String LAPIS = "dimensional_mana:lapis_dust";
    private static final String AMETHYST = "dimensional_mana:amethyst_dust";
    private static final String ARCANE = MagicCirclePowders.ARCANE_ITEM;

    /** 元素粉物品 id → 元素短名（写表时好认）。 */
    private static final String FIRE = "dimensional_mana:fire_element_crystal_dust";
    private static final String WATER = "dimensional_mana:water_element_crystal_dust";
    private static final String ICE = "dimensional_mana:ice_element_crystal_dust";
    private static final String LIGHTNING = "dimensional_mana:lightning_element_crystal_dust";
    private static final String WIND = "dimensional_mana:wind_element_crystal_dust";
    private static final String EARTH = "dimensional_mana:earth_element_crystal_dust";
    private static final String NATURE = "dimensional_mana:nature_element_crystal_dust";
    private static final String METAL = "dimensional_mana:metal_element_crystal_dust";
    private static final String LIGHT = "dimensional_mana:light_element_crystal_dust";
    private static final String POISON = "dimensional_mana:poison_element_crystal_dust";
    private static final String SOUND = "dimensional_mana:sound_element_crystal_dust";

    static {
        // ---- 4.1 现有核心词缀（20 条）----
        core("", METAL, "mine", "mine", 1);
        core("", NATURE, "heal_basic", "heal", 1);
        core("", FIRE, "damage_basic", "damage", 1);
        core("", EARTH, "plant", "plant", 1);
        core("", SOUND, "silence", "silence", 1);
        core(REDSTONE, ARCANE, "boost", "boost", 1);
        core(REDSTONE, POISON, "debuff", "debuff", 2);
        core(REDSTONE, LIGHT, "buff", "buff", 2);
        core(SUGAR, NATURE, "summon_friendly", "summon", 2);
        core(SUGAR, POISON, "summon_hostile", "summon", 3);
        core(SUGAR, ARCANE, "summon_special", "summon", 3);
        core(GUNPOWDER, FIRE, "explosion", "explosion", 2);
        core(GUNPOWDER, WIND, "spread", "spread", 2);
        core(BLAZE, LIGHT, "purify", "purify", 1);
        core(GLOWSTONE, WIND, "expand", "expand", 1);
        core(LAPIS, METAL, "silk_mine", "mine", 2);
        core(AMETHYST, ARCANE, "charge", "charge", 1);
        core(AMETHYST, METAL, "detect_metal", "detect", 2);
        core(AMETHYST, POISON, "teleport_random", "teleport", 3);
        core(AMETHYST, WIND, "teleport_directed", "teleport", 3);

        // ---- 4.2 空语义的基础词缀（第二阶段前先接上能落地的那几条）----
        empty(ARCANE, "amplify", "boost", 2);        // 奥术增幅：下游 +10%
        empty(WATER, "irrigate", "irrigate", 1);     // 灌溉
        empty(ICE, "freeze", "freeze", 2);           // 冻结
        empty(LIGHTNING, "zap", "charge", 2);        // 短时充能 / 感电
        empty(POISON, "corrode", "debuff", 2);       // 腐蚀降护甲
        empty(LIGHT, "glow", "light", 1);            // 照明
        empty(WIND, "gust", "spread", 1);            // 吹散击退
        empty(METAL, "pierce", "pierce", 2);         // 破甲 / 穿透
        empty(NATURE, "grow", "plant", 1);           // 生长催熟
        empty(EARTH, "steady", "stabilize", 1);      // 稳固：紊乱 −1
        empty(SOUND, "resonate", "resonance", 2);    // 震荡
    }

    private static void core(String semantic, String element, String id, String tag, int complexity) {
        Affix affix = new Affix(id, tag, complexity);
        BY_SEMANTIC_ELEMENT.put(key(semantic, element), affix);
        if (semantic.isEmpty()) BY_EMPTY_SEMANTIC.put(element, affix);
    }

    /** 4.2 的扩展基础词缀：只补 4.1 没定义过的元素，不覆盖「现有核心词缀」。 */
    private static void empty(String element, String id, String tag, int complexity) {
        BY_EMPTY_SEMANTIC.putIfAbsent(element, new Affix(id, tag, complexity));
    }

    private static String key(String semantic, String element) {
        return semantic + "|" + element;
    }

    /**
     * 把一块<b>前置输出</b>编译成词缀。空语义时按元素查基础词缀表（规范 4.2）；
     * 语义 + 元素都对不上表 = 这前置换法（规范 4.3 的高级扩展，本版本先拒绝并报错）。
     *
     * @return 词缀；配不出合法词缀返回 null（调用方报错）。
     */
    public static Affix compile(MagicCircleNetwork.Node prefix) {
        String semantic = prefix.semantic();
        String element = prefix.element();
        if (semantic == null || semantic.isEmpty()) {
            if (element == null || element.isEmpty()) return null;
            return BY_EMPTY_SEMANTIC.get(element);
        }
        Affix exact = BY_SEMANTIC_ELEMENT.get(key(semantic, element == null ? "" : element));
        if (exact != null) return exact;
        // 语义粉 + 空元素：只承认「语义自带的协议词缀」——烈焰 = 净化、青金 = 精准、红石 = 信号，
        // 火药 = 伤害。糖空元素没有召唤对象，不成词缀。
        if (element == null || element.isEmpty()) {
            return switch (semantic) {
                case BLAZE -> BY_SEMANTIC_ELEMENT.get(key(BLAZE, LIGHT));
                case LAPIS -> BY_SEMANTIC_ELEMENT.get(key(LAPIS, METAL));
                case REDSTONE -> new Affix("signal_basic", "signal", 1);
                case GUNPOWDER -> new Affix("damage_basic", "damage", 1);
                default -> null;
            };
        }
        return null;
    }

    /** 染料槽 → 前置的修饰标签（规范 5）。返回 null = 无修饰。 */
    public static String dyeModifierTag(String dyeItemId) {
        if (dyeItemId == null || dyeItemId.isEmpty()) return null;
        return switch (dyeItemId) {
            case "minecraft:white_dye" -> "guard";
            case "minecraft:black_dye" -> "curse";
            case "minecraft:gray_dye" -> "insight";
            case "minecraft:light_gray_dye" -> "swift";
            case "minecraft:brown_dye" -> "bind";
            case "minecraft:red_dye" -> "amplify";
            case "minecraft:orange_dye" -> "burst";
            case "minecraft:yellow_dye" -> "delay";
            case "minecraft:lime_dye" -> "awaken";
            case "minecraft:green_dye" -> "mend";
            case "minecraft:cyan_dye" -> "flow";
            case "minecraft:light_blue_dye" -> "ward";
            case "minecraft:blue_dye" -> "confuse";
            case "minecraft:purple_dye" -> "arcane_tune";
            case "minecraft:magenta_dye" -> "charm";
            case "minecraft:pink_dye" -> "blessing";
            default -> null;
        };
    }

    /** 染料修饰标签的中文名（报错与预览用，平台短词不进语言文件）。 */
    public static String dyeModifierName(String tag) {
        if (tag == null) return "";
        return switch (tag) {
            case "guard" -> "守护/净化";
            case "curse" -> "诅咒/吞噬";
            case "insight" -> "洞察/隐匿";
            case "swift" -> "迅捷/迁跃";
            case "bind" -> "稳固/束缚";
            case "amplify" -> "破灭/增幅";
            case "burst" -> "爆裂/扩散";
            case "delay" -> "充能/时序";
            case "awaken" -> "生长/唤灵";
            case "mend" -> "治愈/生命";
            case "flow" -> "迁跃/流动";
            case "ward" -> "冻结/防护";
            case "confuse" -> "紊乱/心灵";
            case "arcane_tune" -> "奥术/共鸣";
            case "charm" -> "心灵/魅惑";
            case "blessing" -> "祝福/增益";
            default -> tag;
        };
    }

    /** 会额外 +1 紊乱的染料修饰（规范 5 末：蓝 / 品红 / 黑）。 */
    public static boolean disorderDye(String tag) {
        return "confuse".equals(tag) || "charm".equals(tag) || "curse".equals(tag);
    }

    /** 互冲的染料对（规范 5：白+黑、红+绿、橙+灰、淡蓝+红）。 */
    public static boolean dyeConflict(String a, String b) {
        return pair(a, b, "minecraft:white_dye", "minecraft:black_dye")
                || pair(a, b, "minecraft:red_dye", "minecraft:green_dye")
                || pair(a, b, "minecraft:orange_dye", "minecraft:gray_dye")
                || pair(a, b, "minecraft:light_blue_dye", "minecraft:red_dye");
    }

    private static boolean pair(String a, String b, String x, String y) {
        return (x.equals(a) && y.equals(b)) || (y.equals(a) && x.equals(b));
    }

    // ---------------------------------------------------------------- 兼容矩阵（规范 6.4）

    private static final Map<Protocol, Set<String>> ACCEPT = new LinkedHashMap<>();
    private static final Map<Protocol, Set<String>> REJECT = new LinkedHashMap<>();

    static {
        ACCEPT.put(Protocol.SIGNAL, Set.of("signal", "logic", "delay", "boost", "stabilize", "charge"));
        REJECT.put(Protocol.SIGNAL, Set.of("damage", "heal", "summon", "mine", "plant", "teleport"));
        ACCEPT.put(Protocol.LIFE, Set.of("heal", "buff", "summon", "purify", "boost", "expand", "plant",
                "irrigate", "stabilize", "charge"));
        REJECT.put(Protocol.LIFE, Set.of("damage", "explosion", "pierce", "mine"));
        ACCEPT.put(Protocol.DAMAGE, Set.of("damage", "explosion", "spread", "debuff", "freeze", "teleport",
                "pierce", "boost", "expand", "resonance", "charge"));
        REJECT.put(Protocol.DAMAGE, Set.of("heal", "plant", "mine", "irrigate"));
        ACCEPT.put(Protocol.PURIFY, Set.of("purify", "buff", "freeze", "irrigate", "spread", "expand",
                "stabilize", "light"));
        REJECT.put(Protocol.PURIFY, Set.of("damage", "explosion"));
        ACCEPT.put(Protocol.LIGHT, Set.of("light", "expand", "boost", "delay", "resonance", "charge",
                "insight"));
        REJECT.put(Protocol.LIGHT, Set.of("mine", "summon"));
        ACCEPT.put(Protocol.SCAN, Set.of("mine", "plant", "detect", "boost", "delay", "expand", "charge"));
        REJECT.put(Protocol.SCAN, Set.of("explosion", "curse"));
        ACCEPT.put(Protocol.CHARGE, Set.of("charge", "detect", "teleport", "stabilize", "resonance",
                "boost", "delay"));
        REJECT.put(Protocol.CHARGE, Set.of("mine", "explosion"));
    }

    /** 终端协协议能不能收这条词缀标签。REJECT 优先（端口标红），没进任何表 = 中立可收。 */
    public static boolean compatible(Protocol protocol, String tag) {
        if (protocol == Protocol.NONE || tag == null) return true;
        if (REJECT.getOrDefault(protocol, Set.of()).contains(tag)) return false;
        return true;
    }

    // ---------------------------------------------------------------- 容量（规范 6.1）

    /** 功率粉对应的词缀容量：下 1 / 中 2 / 高 3 / 极 4 / 特 5 / 超导 6；空槽 1。 */
    public static int baseCapacity(String powerItemId) {
        if (powerItemId == null || powerItemId.isEmpty()) return 1;
        return switch (powerItemId) {
            case "dimensional_mana:lesser_mana_crystal_dust" -> 1;
            case "dimensional_mana:medium_mana_crystal_dust" -> 2;
            case "dimensional_mana:advanced_mana_crystal_dust" -> 3;
            case "dimensional_mana:supreme_mana_crystal_dust" -> 4;
            case "dimensional_mana:special_mana_crystal_dust" -> 5;
            case "dimensional_mana:superconducting_crystal_dust" -> 6;
            default -> 1;
        };
    }

    /**
     * 终端词缀容量：功率槽基础值 + 核心糖模式 +1 + 超导连线 +1，封顶 min(8, 配置上限)。
     *
     * @param coreSugar     本层核心语义是不是糖（效率模式）
     * @param superConduct  这块终端自己的功率槽是不是超导粉
     */
    public static int capacity(String terminalPower, boolean coreSugar, boolean superConduct) {
        int cap = baseCapacity(terminalPower) + (coreSugar ? 1 : 0) + (superConduct ? 1 : 0);
        return Math.min(MagicCircleConfig.MAX_PREFIX_PER_TERMINAL.get(), Math.min(8, cap));
    }

    /** 复杂度税：Σ(词缀复杂度 × 配置单价)，加在终端节点基础值上（规范 6.2）。 */
    public static int complexityTax(List<Affix> affixes) {
        int tax = 0;
        for (Affix affix : affixes) tax += affix.complexity() * MagicCircleConfig.COMPLEXITY_TAX_PER_POINT.get();
        return tax;
    }

    /** 前置功率槽允许的<b>单条</b>词缀复杂度上限（规范 1.4：前置功率只决定自身效率与复杂度上限）。 */
    public static int prefixComplexityCap(String powerItemId) {
        return baseCapacity(powerItemId);
    }

    // ---------------------------------------------------------------- 同意义加成（规范 6.3）

    /**
     * 词缀标签与终端主协议相同时返回加成倍率：标签就是协议英文名的那条（damage ↔ 火药、
     * heal ↔ 糖的 heal、mine ↔ 青金…）。同标签只生效一个（取最高），不叠加。
     */
    public static double sameTagBonus(Protocol protocol, List<Affix> affixes) {
        // 「同意义」按协议认一族标签：火药终端吃伤害 / 爆炸都算同意义（爆炸就是火药的特化），
        // 糖终端吃治疗 / 召唤同理。验收单法阵 2 的 ×1.15 靠这条。
        java.util.Set<String> protocolTags = switch (protocol) {
            case DAMAGE -> java.util.Set.of("damage", "explosion");
            case LIFE -> java.util.Set.of("heal", "summon");
            case SIGNAL -> java.util.Set.of("signal");
            case PURIFY -> java.util.Set.of("purify");
            case LIGHT -> java.util.Set.of("light");
            case SCAN -> java.util.Set.of("mine");
            case CHARGE -> java.util.Set.of("charge");
            case NONE -> java.util.Set.of();
        };
        if (protocolTags.isEmpty()) return 1.0;
        boolean hit = affixes.stream().anyMatch(a -> protocolTags.contains(a.tag()));
        return hit ? MagicCircleConfig.SAME_TAG_BONUS.get() : 1.0;
    }

    /** 词缀 → 结算时用到的规范化效果集合（Rites 分发用）。 */
    public static List<Effect> effectsOf(Affix affix, String dyeTag) {
        List<Effect> list = new ArrayList<>();
        if (affix != null) list.add(Effect.of(affix));
        if (dyeTag != null) list.add(Effect.ofDye(dyeTag));
        return list;
    }

    /** 结算效果：词缀来源 + 染料修饰的合集。 */
    public record Effect(String kind, boolean fromDye) {
        static Effect of(Affix affix) { return new Effect(affix.tag(), false); }

        static Effect ofDye(String tag) { return new Effect(tag, true); }

        /** 效果强度：染料修饰按规范 5 的数值，词缀本体按标签。 */
        public double magnitude() {
            return switch (kind) {
                case "boost", "amplify" -> 0.10;
                case "burst", "expand", "spread" -> 1.0;     // 范围 +1 格
                case "awaken", "delay" -> 0.25;              // 持续 +25%
                case "mend" -> 0.20;                          // 治疗 +20%
                default -> 0.0;
            };
        }
    }

    /** 元素特化：终端元素槽在七大协议下各自决定分支（规范 3.x）。返回值给 Rites 分发。 */
    public static String elementBranch(Protocol protocol, ElementType element) {
        String e = element == null ? "none" : element.name().toLowerCase(Locale.ROOT);
        return protocol.id() + ":" + e;
    }

    /** 词缀短中文名（平台内固定词，蓝图明细 / 试运行预览 / 报错共用）。 */
    public static String affixName(String affixId) {
        return switch (affixId) {
            case "mine" -> "挖掘";
            case "heal_basic" -> "治疗";
            case "damage_basic" -> "伤害";
            case "plant" -> "种植";
            case "silence" -> "静音";
            case "boost" -> "效果提升";
            case "debuff" -> "负面效果附加";
            case "buff" -> "正面效果附加";
            case "summon_friendly" -> "友好生物召唤";
            case "summon_hostile" -> "敌对生物召唤";
            case "summon_special" -> "特殊召唤";
            case "explosion" -> "爆炸";
            case "spread" -> "强扩散";
            case "purify" -> "净化";
            case "expand" -> "扩大";
            case "silk_mine" -> "精准采集挖掘";
            case "charge" -> "充能";
            case "detect_metal" -> "探测金属";
            case "teleport_random" -> "随机传送";
            case "teleport_directed" -> "定向传送";
            case "amplify" -> "增幅";
            case "irrigate" -> "灌溉";
            case "freeze" -> "冻结";
            case "zap" -> "感电";
            case "corrode" -> "腐蚀";
            case "glow" -> "照明";
            case "gust" -> "吹散";
            case "pierce" -> "破甲";
            case "grow" -> "生长";
            case "steady" -> "稳固";
            case "resonate" -> "静音与震荡";
            case "signal_basic" -> "信号";
            default -> affixId;
        };
    }

    private MagicCircleAffixes() {}
}
