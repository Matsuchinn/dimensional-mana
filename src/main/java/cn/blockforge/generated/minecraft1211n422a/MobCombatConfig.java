package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版生物属性/抗性/技能系统的配置。
 *
 * <p>通用数值按「友好生物」与「中立/敌对生物（按难度）」两套公式给出；
 * 生物专配用 {@code special_mobs} 与 {@code resistances} 两条字符串列表描述，
 * 每行一只生物，写法如 {@code wither m=120000 q=1000}。列表里删掉某只生物就会退回内置默认值。</p>
 *
 * <p>行内格式刻意写得很短：游戏内配置界面的输入框只放得下 32 个字符，
 * 所以支持短键名（m / q / mr / qr）、省略 {@code minecraft:}、用空格代替竖线，
 * 以及把一只生物拆成多行（同 ID 的行会自动合并）。改不动的长行也可以用
 * {@code /dimensional_mana mob set} 直接写，没有字数限制。</p>
 */
public final class MobCombatConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue STACK_WITH_EXISTING;
    public static final ModConfigSpec.BooleanValue OWN_MOBS_INDEPENDENT;
    public static final ModConfigSpec.BooleanValue SPECIAL_SKILLS_HARD_ONLY;
    public static final ModConfigSpec.BooleanValue RESISTANCES_HARD_ONLY;
    public static final ModConfigSpec.BooleanValue SPECIAL_SKILLS_ENABLED;

    public static final ModConfigSpec.DoubleValue FRIENDLY_MANA_RATIO;
    public static final ModConfigSpec.DoubleValue FRIENDLY_BATTLE_QI_RATIO;
    public static final ModConfigSpec.IntValue FRIENDLY_FULL_RESTORE_TICKS;

    public static final ModConfigSpec.DoubleValue PEACE_MANA_RATIO;
    public static final ModConfigSpec.DoubleValue PEACE_BATTLE_QI_RATIO;
    public static final ModConfigSpec.DoubleValue PEACE_MANA_REGEN;
    public static final ModConfigSpec.DoubleValue PEACE_BATTLE_QI_REGEN;
    public static final ModConfigSpec.DoubleValue EASY_MANA_RATIO;
    public static final ModConfigSpec.DoubleValue EASY_MANA_FLAT;
    public static final ModConfigSpec.DoubleValue EASY_BATTLE_QI_RATIO;
    public static final ModConfigSpec.DoubleValue EASY_BATTLE_QI_FLAT;
    public static final ModConfigSpec.DoubleValue EASY_REGEN;
    public static final ModConfigSpec.DoubleValue NORMAL_MANA_RATIO;
    public static final ModConfigSpec.DoubleValue NORMAL_MANA_FLAT;
    public static final ModConfigSpec.DoubleValue NORMAL_BATTLE_QI_RATIO;
    public static final ModConfigSpec.DoubleValue NORMAL_BATTLE_QI_FLAT;
    public static final ModConfigSpec.DoubleValue NORMAL_REGEN;
    public static final ModConfigSpec.DoubleValue HARD_MANA_RATIO;
    public static final ModConfigSpec.DoubleValue HARD_MANA_FLAT;
    public static final ModConfigSpec.DoubleValue HARD_BATTLE_QI_RATIO;
    public static final ModConfigSpec.DoubleValue HARD_BATTLE_QI_FLAT;
    public static final ModConfigSpec.DoubleValue HARD_REGEN;

    public static final ModConfigSpec.DoubleValue ENVIRONMENT_REGEN_SCALE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPECIAL_MOBS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESISTANCES;

    /**
     * 内置默认生物专配；配置列表里缺失的条目会回退到这里。
     *
     * <p>每一行都控制在 32 个字符以内：游戏内配置界面的输入框只放得下 32 个字符，
     * 超过就会被截断。一只生物可以拆成多行写，同一个 ID 的多行会自动合并。</p>
     */
    private static final List<String> DEFAULT_SPECIAL_MOBS = List.of(
            "wither m=120000 q=1000",
            "wither mr=50 qr=50",
            "ender_dragon m=300000 q=5000",
            "ender_dragon mr=1000 qr=2500",
            "blaze m=500 q=200",
            "ghast m=1500 q=0",
            "piglin_brute m=100 q=1500",
            "zombified_piglin m=300 q=500",
            "enderman m=2000 q=500",
            "shulker m=200 q=2500",
            "witch m=5000 q=100",
            "evoker m=8000 q=500",
            "ravager m=0 q=3000",
            "vindicator m=0 q=800",
            "phantom m=500 q=200",
            "breeze m=800 q=100",
            "bogged m=600 q=300",
            "iron_golem m=4000 q=0",
            "warden m=8000 q=1000 qr=40",
            "elder_guardian m=5000 q=1800",
            "elder_guardian qr=30");

    /** 内置默认抗性：正数是抗性（减伤），负数是易伤；同样每行不超过 32 个字符。 */
    private static final List<String> DEFAULT_RESISTANCES = List.of(
            "wither all=0.35 light=-0.15",
            "ender_dragon all=0.65",
            "ender_dragon arcane=-0.2",
            "blaze fire=1 ice=-1 water=-0.75",
            "ghast fire=0.8 explosion=0.5",
            "piglin_brute metal=0.75",
            "piglin_brute lightning=-0.3",
            "zombified_piglin fire=0.8",
            "zombified_piglin water=-0.5",
            "enderman arcane=0.5 water=-2",
            "enderman lightning=-0.5",
            "shulker physical=0.99",
            "shulker fire=-0.3",
            "witch all=0.25",
            "witch magic_crit=0.5",
            "evoker arcane=0.8",
            "evoker metal=-0.3",
            "ravager physical=0.8",
            "ravager all=-0.15",
            "vindicator physical=0.2",
            "vindicator metal=-0.3",
            "phantom wind=0.5",
            "phantom lightning=-0.75",
            "phantom sound=-0.5",
            "breeze wind=1",
            "breeze water=-0.5",
            "breeze lightning=-0.3",
            "bogged poison=1",
            "bogged fire=-0.75",
            "bogged light=-0.5",
            "iron_golem all=0.5",
            "iron_golem lightning=-0.15",
            "iron_golem melt=0.25",
            "warden all=0.25",
            "elder_guardian water=0.95",
            "elder_guardian fire=-0.15");

    /** 游戏内配置界面的输入框上限：一行超过 32 个字符，在那里就改不动了。 */
    static final int MAX_LINE_LENGTH = 32;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("原版生物的魔素 / 斗气 / 元素抗性 / 专属技能设置。",
                        "Vanilla mob mana, Battle Qi, elemental resistance and special-skill settings.",
                        "改完保存后重载配置（或重进世界）即可生效，不需要重开存档。")
                .push("entity_system");
        ENABLED = b.comment("是否让本模组接管原版生物的魔素与斗气资源池（默认开启）。",
                        "关掉后原版生物只保留固定基础值，抗性表与专属技能也不会触发。",
                        "Whether the mod manages vanilla mob mana / Battle Qi pools.")
                .translation("config.dimensional_mana.mobs.entity_system.enabled")
                .define("enabled", true);
        STACK_WITH_EXISTING = b.comment("新数值怎么算到生物头上：",
                        "  true  = 叠加：在生物原有数值（含其他模组赋予的初始值）上再加通用数值；",
                        "  false = 独立：丢弃原值，只用本系统按难度 / 血量 / 专配算出的数值。",
                        "默认叠加，这样旧存档与其他模组的生物不会被本模组压回原版强度。",
                        "true = stack on top of existing values, false = replace them.")
                .translation("config.dimensional_mana.mobs.entity_system.stack_with_existing")
                .define("stack_with_existing", true);
        OWN_MOBS_INDEPENDENT = b.comment("本模组生物是否独立计算魔素与斗气（默认关闭）。",
                        "false：哥布林、元素史莱姆、冒险者等的自身基础值，再叠加全局通用公式与恢复量。",
                        "true：只用自身基础值和自身恢复，不参与全局通用资源计算。",
                        "切换后已有生物也会更新，不重复叠加，不改变生命、攻击力或固有技能。")
                .translation("config.dimensional_mana.mobs.entity_system.own_mobs_independent")
                .define("own_mobs_independent", false);
        SPECIAL_SKILLS_ENABLED = b.comment("是否启用原版特殊生物的专属技能（凋灵风暴、高压火球、破甲重劈等 18 只怪物的技能）。",
                        "关闭后它们只剩普通攻击与下方 profiles 里登记的抗性。",
                        "Whether special vanilla mobs can use their unique skills.")
                .translation("config.dimensional_mana.mobs.entity_system.special_skills_enabled")
                .define("special_skills_enabled", true);
        SPECIAL_SKILLS_HARD_ONLY = b.comment("专属技能是否只在困难 / 极限难度生效（默认开启）。",
                        "普通与简单难度下这些生物不会放技能，方便新手与建筑党；",
                        "元素抗性由下面单独的 resistances_hard_only 控制，默认任何难度都生效。",
                        "Only let special mobs use their unique skills on Hard difficulty.")
                .translation("config.dimensional_mana.mobs.entity_system.special_skills_hard_only")
                .define("special_skills_hard_only", true);
        RESISTANCES_HARD_ONLY = b.comment("元素 / 物理 / 爆炸抗性与易伤是否只在困难 / 极限难度生效（默认关闭）。",
                        "默认关闭 = 任何难度都会按 profiles 里的抗性表结算，",
                        "  例如烈焰人在简单难度也照样免疫火焰、怕冰。",
                        "打开后普通与简单难度下这些生物保持原版强度。",
                        "Only apply resistances and weaknesses on Hard difficulty.")
                .translation("config.dimensional_mana.mobs.entity_system.resistances_hard_only")
                .define("resistances_hard_only", false);
        ENVIRONMENT_REGEN_SCALE = b.comment("环境魔素浓度对生物魔素恢复的影响强度（默认 1.0）。",
                        "  1.0 = 完全跟着所在维度 / 区块的浓度走（末地最慢）；",
                        "  0.0 = 环境完全不产生影响，只按生物自己的每秒恢复量走。",
                        "只影响魔素恢复，斗气恢复与环境浓度无关。",
                        "How strongly biome / dimension mana density scales mob mana regeneration.")
                .translation("config.dimensional_mana.mobs.entity_system.environment_regen_scale")
                .defineInRange("environment_regen_scale", 1.0, 0.0, 10.0);
        b.pop();

        b.push("friendly");
        b.comment("友好生物（牛羊、村民等不主动攻击的生物）：资源池按最大生命值换算，",
                        "生成时给满，之后每天一次性回满，中途不会慢慢自然恢复。",
                        "Friendly mobs: pools derived from max health and refilled once per day.");
        FRIENDLY_MANA_RATIO = b.comment("友好生物的魔素上限 = 最大生命值 × 该系数（默认 0.8）。",
                        "例：20 点血的羊 → 魔素 16 点。",
                        "Friendly mob max mana = max health × this ratio.")
                .translation("config.dimensional_mana.mobs.friendly.mana_ratio")
                .defineInRange("mana_ratio", 0.8, 0.0, 1000.0);
        FRIENDLY_BATTLE_QI_RATIO = b.comment("友好生物的斗气上限 = 最大生命值 × 该系数（默认 0.35）。",
                        "例：20 点血的羊 → 斗气 7 点。",
                        "Friendly mob max Battle Qi = max health × this ratio.")
                .translation("config.dimensional_mana.mobs.friendly.battle_qi_ratio")
                .defineInRange("battle_qi_ratio", 0.35, 0.0, 1000.0);
        FRIENDLY_FULL_RESTORE_TICKS = b.comment("友好生物每隔多少刻把魔素与斗气一次性回满（默认 24000 刻 = 游戏内一天）。",
                        "调小 = 回得更勤；调大 = 一天回不满。20 刻 = 1 秒。",
                        "How often (in ticks) friendly mobs fully restore their pools.")
                .translation("config.dimensional_mana.mobs.friendly.full_restore_ticks")
                .defineInRange("full_restore_ticks", 24000, 20, 2400000);
        b.pop();

        b.push("hostile");
        b.comment("敌对与中立生物（僵尸、骷髅、猪灵、末影人等）的资源池按难度分四档：",
                        "  初始魔素 = 最大生命值 × ratio + flat，初始斗气同理；",
                        "  regen 是每秒自然恢复量，受环境魔素浓度影响。",
                        "生物生成时的世界难度决定套用哪一档；中途改难度不会重算已有生物的资源上限。",
                        "Hostile / neutral mobs: pools are computed per difficulty (health × ratio + flat).");
        PEACE_MANA_RATIO = b.comment("和平难度：魔素 = 血量 × 该系数（默认 0.5，即一半血量）。")
                .translation("config.dimensional_mana.mobs.hostile.peace_mana_ratio")
                .defineInRange("peace_mana_ratio", 0.5, 0.0, 1000.0);
        PEACE_BATTLE_QI_RATIO = b.comment("和平难度：斗气 = 血量 × 该系数（默认 0.5）。")
                .translation("config.dimensional_mana.mobs.hostile.peace_battle_qi_ratio")
                .defineInRange("peace_battle_qi_ratio", 0.5, 0.0, 1000.0);
        PEACE_MANA_REGEN = b.comment("和平难度：每秒自然恢复多少魔素（默认 0，即不恢复）。")
                .translation("config.dimensional_mana.mobs.hostile.peace_mana_regen")
                .defineInRange("peace_mana_regen", 0.0, 0.0, 1000000.0);
        PEACE_BATTLE_QI_REGEN = b.comment("和平难度：每秒自然恢复多少斗气（默认 0，即不恢复）。")
                .translation("config.dimensional_mana.mobs.hostile.peace_battle_qi_regen")
                .defineInRange("peace_battle_qi_regen", 0.0, 0.0, 1000000.0);
        EASY_MANA_RATIO = b.comment("简单难度：魔素 = 血量 × 该系数（默认 0.8）。")
                .translation("config.dimensional_mana.mobs.hostile.easy_mana_ratio")
                .defineInRange("easy_mana_ratio", 0.8, 0.0, 1000.0);
        EASY_MANA_FLAT = b.comment("简单难度：在血量换算的魔素上再加这个固定值（默认 100）。")
                .translation("config.dimensional_mana.mobs.hostile.easy_mana_flat")
                .defineInRange("easy_mana_flat", 100.0, 0.0, 100000000.0);
        EASY_BATTLE_QI_RATIO = b.comment("简单难度：斗气 = 血量 × 该系数（默认 0.6）。")
                .translation("config.dimensional_mana.mobs.hostile.easy_battle_qi_ratio")
                .defineInRange("easy_battle_qi_ratio", 0.6, 0.0, 1000.0);
        EASY_BATTLE_QI_FLAT = b.comment("简单难度：在血量换算的斗气上再加这个固定值（默认 50）。")
                .translation("config.dimensional_mana.mobs.hostile.easy_battle_qi_flat")
                .defineInRange("easy_battle_qi_flat", 50.0, 0.0, 100000000.0);
        EASY_REGEN = b.comment("简单难度：每秒恢复多少魔素与斗气（默认 0.1）。")
                .translation("config.dimensional_mana.mobs.hostile.easy_regen_per_second")
                .defineInRange("easy_regen_per_second", 0.1, 0.0, 1000000.0);
        NORMAL_MANA_RATIO = b.comment("普通难度：魔素 = 血量 × 该系数（默认 1.1）。")
                .translation("config.dimensional_mana.mobs.hostile.normal_mana_ratio")
                .defineInRange("normal_mana_ratio", 1.1, 0.0, 1000.0);
        NORMAL_MANA_FLAT = b.comment("普通难度：在血量换算的魔素上再加这个固定值（默认 300）。")
                .translation("config.dimensional_mana.mobs.hostile.normal_mana_flat")
                .defineInRange("normal_mana_flat", 300.0, 0.0, 100000000.0);
        NORMAL_BATTLE_QI_RATIO = b.comment("普通难度：斗气 = 血量 × 该系数（默认 0.7）。")
                .translation("config.dimensional_mana.mobs.hostile.normal_battle_qi_ratio")
                .defineInRange("normal_battle_qi_ratio", 0.7, 0.0, 1000.0);
        NORMAL_BATTLE_QI_FLAT = b.comment("普通难度：在血量换算的斗气上再加这个固定值（默认 250）。")
                .translation("config.dimensional_mana.mobs.hostile.normal_battle_qi_flat")
                .defineInRange("normal_battle_qi_flat", 250.0, 0.0, 100000000.0);
        NORMAL_REGEN = b.comment("普通难度：每秒恢复多少魔素与斗气（默认 0.8）。")
                .translation("config.dimensional_mana.mobs.hostile.normal_regen_per_second")
                .defineInRange("normal_regen_per_second", 0.8, 0.0, 1000000.0);
        HARD_MANA_RATIO = b.comment("困难 / 极限难度：魔素 = 血量 × 该系数（默认 1.4）。")
                .translation("config.dimensional_mana.mobs.hostile.hard_mana_ratio")
                .defineInRange("hard_mana_ratio", 1.4, 0.0, 1000.0);
        HARD_MANA_FLAT = b.comment("困难 / 极限难度：在血量换算的魔素上再加这个固定值（默认 500）。")
                .translation("config.dimensional_mana.mobs.hostile.hard_mana_flat")
                .defineInRange("hard_mana_flat", 500.0, 0.0, 100000000.0);
        HARD_BATTLE_QI_RATIO = b.comment("困难 / 极限难度：斗气 = 血量 × 该系数（默认 0.8）。")
                .translation("config.dimensional_mana.mobs.hostile.hard_battle_qi_ratio")
                .defineInRange("hard_battle_qi_ratio", 0.8, 0.0, 1000.0);
        HARD_BATTLE_QI_FLAT = b.comment("困难 / 极限难度：在血量换算的斗气上再加这个固定值（默认 450）。")
                .translation("config.dimensional_mana.mobs.hostile.hard_battle_qi_flat")
                .defineInRange("hard_battle_qi_flat", 450.0, 0.0, 100000000.0);
        HARD_REGEN = b.comment("困难 / 极限难度：每秒恢复多少魔素与斗气（默认 1.5）。")
                .translation("config.dimensional_mana.mobs.hostile.hard_regen_per_second")
                .defineInRange("hard_regen_per_second", 1.5, 0.0, 1000000.0);
        b.pop();

        b.push("profiles");
        SPECIAL_MOBS = b.comment("生物专配：一行一只生物，例如  wither m=120000 q=1000",
                        "分隔符用 | 、逗号、分号或空格都行；minecraft: 前缀可以省略。",
                        "★ 游戏内配置界面的输入框只放得下 32 个字符，写太长会被截断 ——",
                        "  所以每行尽量只写一两个键，一只生物可以拆成多行，同 ID 的多行会自动合并。",
                        "  也可以在游戏里用 /dimensional_mana mob set <生物> <键> <数值> 改，没有字数限制。",
                        "可用的键（括号里是短写法）：",
                        "  mana (m)             这只生物的魔素上限（不写就按难度公式算）",
                        "  battle_qi (q)        这只生物的斗气上限（不写就按难度公式算）",
                        "  mana_regen (mr)      每秒魔素恢复，叠加在难度恢复量之上",
                        "  battle_qi_regen (qr) 每秒斗气恢复，叠加在难度恢复量之上",
                        "  off / on             把这只生物的专配与专属技能整体关掉 / 重新打开",
                        "说明：列表里删掉某一行只会退回内置默认值，不会让那只生物失去专配。",
                        "Per-mob stat overrides, one mob per line: entity_id key=value key=value")
                .translation("config.dimensional_mana.mobs.profiles.special_mobs")
                .defineListAllowEmpty("special_mobs", DEFAULT_SPECIAL_MOBS, o -> o instanceof String);
        RESISTANCES = b.comment("生物抗性：一行一只生物，例如  blaze fire=1 ice=-1",
                        "写法同上：分隔符随便，minecraft: 可省略，每行建议不超过 32 个字符。",
                        "数值含义：正数 = 抗性（按比例减伤），负数 = 易伤（按比例增伤），",
                        "  0.75 表示只吃 25% 伤害，-0.5 表示多受 50% 伤害，1 = 完全免疫。",
                        "12 种元素名：arcane 奥术 / metal 金 / nature 木 / water 水 / fire 火 / earth 土 /",
                        "  ice 冰 / lightning 雷 / poison 毒 / sound 音 / light 光 / wind 风。",
                        "特殊键：all 全种类抗性（含物理、爆炸与全部元素）、physical 单列物理、",
                        "  explosion 单列爆炸、magic_crit 法术暴击抗性（把暴击降级为普通命中的概率）、melt 融化反应易伤。",
                        "  只写 all 时物理和爆炸也会吃这个值；想单独取消某种，显式写 physical=0 / explosion=0。",
                        "默认任何难度都生效；想让抗性只在困难 / 极限难度结算，打开 resistances_hard_only。",
                        "Per-mob elemental resistances; positive = resistance, negative = vulnerability.")
                .translation("config.dimensional_mana.mobs.profiles.resistances")
                .defineListAllowEmpty("resistances", DEFAULT_RESISTANCES, o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    static List<String> defaultSpecialMobs() {
        return DEFAULT_SPECIAL_MOBS;
    }

    static List<String> defaultResistances() {
        return DEFAULT_RESISTANCES;
    }

    // ------------------------------------------------------------ 指令写入

    /**
     * 指令改配置：把一只生物的某个键写进对应的列表。
     *
     * <p>整条列表会被重排成「一只生物一个键一行」的短行，这样之后在游戏内配置界面里
     * 也能直接改（那个输入框只有 32 个字符），同时不影响其他生物的条目。</p>
     */
    static void writeMobValue(boolean stat, String mobId, String key, double value) {
        ModConfigSpec.ConfigValue<List<? extends String>> target = stat ? SPECIAL_MOBS : RESISTANCES;
        List<String> lines = new ArrayList<>(target.get());
        java.util.LinkedHashMap<String, Double> pairs = mobPairs(lines, mobId);
        pairs.put(key, value);
        writeMobLines(target, lines, mobId, pairs);
    }

    /** 指令改配置：删掉一只生物的某个键，让它回到内置默认值；本来就没有这个键时返回 false。 */
    static boolean removeMobValue(boolean stat, String mobId, String key) {
        ModConfigSpec.ConfigValue<List<? extends String>> target = stat ? SPECIAL_MOBS : RESISTANCES;
        List<String> lines = new ArrayList<>(target.get());
        java.util.LinkedHashMap<String, Double> pairs = mobPairs(lines, mobId);
        if (pairs.remove(key) == null) return false;
        writeMobLines(target, lines, mobId, pairs);
        return true;
    }

    /** 指令改配置：把一只生物从两条列表里彻底删掉，回到内置默认值。 */
    static void resetMob(String mobId) {
        List<ModConfigSpec.ConfigValue<List<? extends String>>> targets = List.of(SPECIAL_MOBS, RESISTANCES);
        for (ModConfigSpec.ConfigValue<List<? extends String>> target : targets) {
            List<String> lines = new ArrayList<>(target.get());
            if (!lines.removeIf(line -> mobId.equals(MobProfile.lineId(line)))) continue;
            target.set(lines);
        }
        SPEC.save();
        MobProfile.invalidate();
    }

    private static void writeMobLines(ModConfigSpec.ConfigValue<List<? extends String>> target,
                                      List<String> lines, String mobId,
                                      java.util.LinkedHashMap<String, Double> pairs) {
        lines.removeIf(line -> mobId.equals(MobProfile.lineId(line)));
        String writtenId = shortId(mobId);
        for (java.util.Map.Entry<String, Double> entry : pairs.entrySet()) {
            lines.add(writtenId + "|" + entry.getKey() + "=" + shortNumber(entry.getValue()));
        }
        target.set(lines);
        SPEC.save();
        MobProfile.invalidate();
    }

    /** 写配置时省掉 minecraft: 前缀，行更短；其它模组的生物保留完整 ID。 */
    static String shortId(String mobId) {
        return mobId.startsWith("minecraft:") ? mobId.substring("minecraft:".length()) : mobId;
    }

    /**
     * 把两条列表里超过 32 个字符的行拆成「一只生物一个键一行」。
     *
     * <p>游戏内配置界面的输入框只放得下 32 个字符，老版本写下的长行在那里根本改不动，
     * 这个整理会把它们拆短并立刻保存；认不出内容的行保持原样，不会丢数据。
     * 返回被整理过的列表条数（0 表示没有超长行）。</p>
     */
    static int compactLists() {
        int rebuilt = 0;
        for (ModConfigSpec.ConfigValue<List<? extends String>> target : List.of(SPECIAL_MOBS, RESISTANCES)) {
            List<String> lines = new ArrayList<>(target.get());
            boolean touched = false;
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.length() <= MAX_LINE_LENGTH) continue;
                List<String> split = splitLongLine(line);
                if (split == null) continue;
                lines.remove(index);
                lines.addAll(index, split);
                index += split.size() - 1;
                touched = true;
            }
            if (!touched) continue;
            target.set(lines);
            rebuilt++;
        }
        if (rebuilt > 0) {
            SPEC.save();
            MobProfile.invalidate();
        }
        return rebuilt;
    }

    /** 拆一行；只要有一个词认不出来就返回 null，让调用方保留原行。 */
    private static List<String> splitLongLine(String line) {
        String[] tokens = MobProfile.tokens(line);
        if (tokens.length < 2) return null;
        String id = MobProfile.normalizeId(tokens[0]);
        if (id == null) return null;
        String writtenId = shortId(id);
        List<String> result = new ArrayList<>(tokens.length - 1);
        for (int i = 1; i < tokens.length; i++) {
            String[] pair = MobProfile.pair(tokens[i]);
            if (pair == null) {
                String flag = tokens[i].toLowerCase(java.util.Locale.ROOT);
                if (!flag.equals("on") && !flag.equals("off")) return null;
                result.add(writtenId + "|" + flag);
                continue;
            }
            Double value = MobProfile.number(pair[1]);
            if (value == null) return null;
            result.add(writtenId + "|" + MobProfile.shortKey(pair[0]) + "=" + shortNumber(value));
        }
        return result;
    }

    /** 收集一只生物在一条列表里已经登记的键值，用来做局部改写，避免冲掉别的键。 */
    private static java.util.LinkedHashMap<String, Double> mobPairs(List<String> lines, String mobId) {
        java.util.LinkedHashMap<String, Double> pairs = new java.util.LinkedHashMap<>();
        for (String line : lines) {
            if (!mobId.equals(MobProfile.lineId(line))) continue;
            for (String token : MobProfile.tokens(line)) {
                String[] pair = MobProfile.pair(token);
                if (pair == null) continue;
                Double value = MobProfile.number(pair[1]);
                if (value == null) continue;
                pairs.put(MobProfile.shortKey(pair[0]), value);
            }
        }
        return pairs;
    }

    /** 写进配置的数字尽量短：120000 而不是 120000.0，0.5 而不是 0.500。 */
    static String shortNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15) return String.valueOf((long) value);
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private MobCombatConfig() {}
}
