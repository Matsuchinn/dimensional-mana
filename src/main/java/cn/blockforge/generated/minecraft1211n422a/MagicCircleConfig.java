package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 「次元物语：魔导」魔法阵系统的全部可调系数。
 *
 * <p>对应设计文档第 7 节（消耗公式）与第 14 节（召唤 / 挖矿 / 安全 / 解锁）。
 * 服务器可以整体调高或调低，不改代码。</p>
 */
public final class MagicCircleConfig {
    public static final ModConfigSpec SPEC;

    // ---- 规模与性能 ----
    public static final ModConfigSpec.IntValue MAX_BASES_PER_SIDE;
    public static final ModConfigSpec.IntValue MAX_LAYERS;
    public static final ModConfigSpec.IntValue MAX_FUNCTIONAL_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_CONNECTIONS;
    public static final ModConfigSpec.IntValue MAX_LINKS_PER_ANCHOR;
    public static final ModConfigSpec.IntValue MAX_LINK_DISTANCE;
    public static final ModConfigSpec.DoubleValue TEST_RUN_COST_RATIO;

    // ---- 消耗与强度系数 ----
    public static final ModConfigSpec.DoubleValue SCALE_PER_EXTRA_BASE;
    public static final ModConfigSpec.DoubleValue LAYER_STEP;
    public static final ModConfigSpec.DoubleValue OVERLAP_STEP;
    public static final ModConfigSpec.DoubleValue EFFICIENCY_PER_SUPERCONDUCTOR;
    public static final ModConfigSpec.DoubleValue EFFICIENCY_PER_STABILIZER;
    public static final ModConfigSpec.DoubleValue EFFICIENCY_CAP;
    public static final ModConfigSpec.IntValue DISORDER_SUPPRESS_PER_STABILIZER;
    public static final ModConfigSpec.DoubleValue OVERCLOCK_MANA_DRAIN;
    public static final ModConfigSpec.DoubleValue STRENGTH_PER_BASE;
    public static final ModConfigSpec.DoubleValue STRENGTH_PER_LAYER;
    public static final ModConfigSpec.DoubleValue STRENGTH_PER_AMPLIFIER;
    public static final ModConfigSpec.DoubleValue CONFLICT_PENALTY;

    // ---- 识别失败后的手动框选（文档 5.4：系统代劳的代价） ----
    public static final ModConfigSpec.IntValue HAND_DRAWN_COMPLEXITY;
    public static final ModConfigSpec.DoubleValue HAND_DRAWN_COST_FACTOR;

    // ---- 解锁 ----
    public static final ModConfigSpec.BooleanValue POWDER_UNLOCK_BY_LEVEL;
    public static final ModConfigSpec.ConfigValue<String> UNLOCK_MODE;

    // ---- 创造模式 ----
    public static final ModConfigSpec.BooleanValue CREATIVE_FREE_POWDER;

    // ---- 安全 ----
    public static final ModConfigSpec.BooleanValue ALLOW_PVP;
    public static final ModConfigSpec.BooleanValue ALLOW_PLAYER_DAMAGE_IN_TEST;
    public static final ModConfigSpec.BooleanValue OVERLOAD_BACKLASH;

    // ---- 召唤 ----
    public static final ModConfigSpec.BooleanValue SUMMON_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SUMMON_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SUMMON_BLACKLIST;
    public static final ModConfigSpec.IntValue MAX_SUMMONS_PER_NETWORK;
    public static final ModConfigSpec.IntValue SUMMON_LIFETIME_SECONDS;

    // ---- 挖矿 ----
    public static final ModConfigSpec.BooleanValue MINING_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MINING_BLOCK_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MINING_BLOCK_WHITELIST;
    public static final ModConfigSpec.BooleanValue REQUIRE_LOADED_CHUNK;
    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_TRIGGER;
    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_MINUTE;
    public static final ModConfigSpec.BooleanValue ALLOW_SILK_TOUCH;
    public static final ModConfigSpec.BooleanValue DROP_TO_INVENTORY;

    // ---- v2.2：输出节点重构 + 条件节点声明模式 ----
    public static final ModConfigSpec.BooleanValue DECLARE_MODE_ENABLED;
    public static final ModConfigSpec.IntValue MAX_DECLARE_NODES_PER_TERMINAL;
    public static final ModConfigSpec.IntValue MAX_DECLARE_NODES_PER_PREFIX_PATH;
    public static final ModConfigSpec.IntValue CONDITION_BASE_COST;
    public static final ModConfigSpec.IntValue CONDITION_EXTRA_GROUP_COST;
    public static final ModConfigSpec.IntValue RUNTIME_FAIL_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue COMPLEXITY_TAX_PER_POINT;
    public static final ModConfigSpec.IntValue MAX_PREFIX_PER_TERMINAL;
    public static final ModConfigSpec.BooleanValue COMPATIBILITY_STRICT;
    public static final ModConfigSpec.BooleanValue ALLOW_ELEMENT_HEAL;
    public static final ModConfigSpec.DoubleValue MIN_TOTAL_EFFICIENCY;
    public static final ModConfigSpec.IntValue DISORDER_DECLARE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue MISFIRE_CHANCE;
    public static final ModConfigSpec.DoubleValue SAME_TAG_BONUS;
    public static final ModConfigSpec.DoubleValue DYE_TERMINAL_STEP;
    public static final ModConfigSpec.IntValue IMPORT_MAX_CHARS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("魔法阵的规模、层数与性能上限").push("magiccircle.performance");
        MAX_BASES_PER_SIDE = b.comment("绘制台基座拼成的矩形最长边（以基座个数计）；16 表示最多 16×16 个基座")
                .translation("config.dimensional_mana.magiccircle.max_bases_per_side")
                .defineInRange("maxBasesPerSide", 16, 1, 64);
        MAX_LAYERS = b.comment("同一个锚点最多叠加几层蓝图")
                .translation("config.dimensional_mana.magiccircle.max_layers")
                .defineInRange("maxLayers", 4, 1, 16);
        MAX_FUNCTIONAL_BLOCKS = b.comment("单个法阵网络最多容纳的功能块数量，超过后拒绝编辑")
                .translation("config.dimensional_mana.magiccircle.max_functional_blocks")
                .defineInRange("maxFunctionalBlocksPerNetwork", 128, 1, 4096);
        MAX_CONNECTIONS = b.comment("单个法阵网络最多容纳的连线数量")
                .translation("config.dimensional_mana.magiccircle.max_connections")
                .defineInRange("maxConnectionsPerNetwork", 64, 0, 4096);
        MAX_LINKS_PER_ANCHOR = b.comment("一个锚点最多连接几个其他锚点")
                .translation("config.dimensional_mana.magiccircle.max_links_per_anchor")
                .defineInRange("maxLinksPerAnchor", 8, 0, 64);
        MAX_LINK_DISTANCE = b.comment("异面法阵连线的最大距离（格）")
                .translation("config.dimensional_mana.magiccircle.max_link_distance")
                .defineInRange("maxLinkDistance", 16, 1, 64);
        TEST_RUN_COST_RATIO = b.comment("试运行需要支付的启动成本比例；0.5 表示半价试运行")
                .translation("config.dimensional_mana.magiccircle.test_run_cost_ratio")
                .defineInRange("testRunCostRatio", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("节点消耗 = 基础值 × (1-功率效率) × (1-总效率减免) × (1+紊乱×每点惩罚) × (1+全局损耗)；再乘规模 / 层数 / 重叠")
                .push("magiccircle.cost");
        SCALE_PER_EXTRA_BASE = b.comment("每多一个基座的规模系数增量：S = 1 + 该值 × (基座数 - 1)")
                .translation("config.dimensional_mana.magiccircle.scale_per_extra_base")
                .defineInRange("scalePerExtraBase", 0.15, 0.0, 10.0);
        LAYER_STEP = b.comment("每多叠一层的层数系数增量：D = 1 + 该值 × (层数 - 1)")
                .translation("config.dimensional_mana.magiccircle.layer_step")
                .defineInRange("layerStep", 0.30, 0.0, 10.0);
        OVERLAP_STEP = b.comment("每个重叠功能块的重量系数增量：O = 1 + 该值 × 重叠块数")
                .translation("config.dimensional_mana.magiccircle.overlap_step")
                .defineInRange("overlapStep", 0.20, 0.0, 10.0);
        EFFICIENCY_PER_SUPERCONDUCTOR = b.comment("每份超导魔晶粉给全阵带来的额外效率减免（连线超导加成）")
                .translation("config.dimensional_mana.magiccircle.efficiency_per_superconductor")
                .defineInRange("efficiencyPerSuperconductor", 0.04, 0.0, 10.0);
        EFFICIENCY_PER_STABILIZER = b.comment("每个稳定功能块给全阵带来的额外效率减免")
                .translation("config.dimensional_mana.magiccircle.efficiency_per_stabilizer")
                .defineInRange("efficiencyPerStabilizer", 0.02, 0.0, 10.0);
        EFFICIENCY_CAP = b.comment("总效率减免上限（防止零消耗）；0.75 表示最多省 75%")
                .translation("config.dimensional_mana.magiccircle.efficiency_cap")
                .defineInRange("efficiencyCap", 0.75, 0.0, 0.95);
        DISORDER_SUPPRESS_PER_STABILIZER = b.comment("每个稳定功能块压制的紊乱点数（总紊乱先加核心加成再减这个，最低到 0）")
                .translation("config.dimensional_mana.magiccircle.disorder_suppress_per_stabilizer")
                .defineInRange("disorderSuppressPerStabilizer", 1, 0, 100);
        OVERCLOCK_MANA_DRAIN = b.comment("核心超频（烈焰粉）模式的全局魔素损耗；0.25 表示全阵消耗 +25%")
                .translation("config.dimensional_mana.magiccircle.overclock_mana_drain")
                .defineInRange("overclockManaDrain", 0.25, 0.0, 10.0);
        STRENGTH_PER_BASE = b.comment("每多一个基座的强度增量：P 的第一项")
                .translation("config.dimensional_mana.magiccircle.strength_per_base")
                .defineInRange("strengthPerBase", 0.10, 0.0, 10.0);
        STRENGTH_PER_LAYER = b.comment("每多叠一层的强度增量：P 的第二项")
                .translation("config.dimensional_mana.magiccircle.strength_per_layer")
                .defineInRange("strengthPerLayer", 0.15, 0.0, 10.0);
        STRENGTH_PER_AMPLIFIER = b.comment("每个增幅块带来的强度增量")
                .translation("config.dimensional_mana.magiccircle.strength_per_amplifier")
                .defineInRange("strengthPerAmplifier", 0.05, 0.0, 10.0);
        CONFLICT_PENALTY = b.comment("每一点紊乱值让节点消耗增加的倍率；0.10 表示 +10%")
                .translation("config.dimensional_mana.magiccircle.conflict_penalty")
                .defineInRange("conflictPenalty", 0.10, 0.0, 10.0);
        HAND_DRAWN_COMPLEXITY = b.comment("手动框选的功能块额外增加的复杂度（系统代劳的代价）")
                .translation("config.dimensional_mana.magiccircle.hand_drawn_complexity")
                .defineInRange("handDrawnComplexity", 1, 0, 100);
        HAND_DRAWN_COST_FACTOR = b.comment("手动框选的功能块消耗系数倍率；1.05 表示贵 5%")
                .translation("config.dimensional_mana.magiccircle.hand_drawn_cost_factor")
                .defineInRange("handDrawnCostFactor", 1.05, 1.0, 10.0);
        b.pop();

        b.comment("粉末按魔法等级解锁：未解锁的粉末仍显示但灰掉").push("magiccircle.unlock");
        POWDER_UNLOCK_BY_LEVEL = b.comment("是否按魔法等级限制粉末；关掉则所有粉末立即可用")
                .translation("config.dimensional_mana.magiccircle.powder_unlock_by_level")
                .define("powderUnlockByLevel", true);
        UNLOCK_MODE = b.comment("解锁模式：LEVEL = 按玩家魔法等级；ALL = 全部开放；SERVER = 只按服务器进度（等同 LEVEL）")
                .translation("config.dimensional_mana.magiccircle.unlock_mode")
                .define("unlockMode", "LEVEL");
        b.pop();

        b.comment("创造模式：粉末无限，方便搭阵试玩、不用先备料").push("magiccircle.creative");
        CREATIVE_FREE_POWDER = b.comment("创造模式下粉末不消耗：不检查背包也不扣粉（相当于直接从创造物品栏取）；"
                        + "顺带放开魔法等级限制，所有粉末都能选。关掉则创造模式照常按生存规则扣粉")
                .translation("config.dimensional_mana.magiccircle.creative_free_powder")
                .define("creativeFreePowder", true);
        b.pop();

        b.comment("安全开关：PVP、试运行伤害与过载反噬").push("magiccircle.safety");
        ALLOW_PVP = b.comment("允许法阵在 PvP 中伤害其他玩家")
                .translation("config.dimensional_mana.magiccircle.allow_pvp")
                .define("allowPvp", false);
        ALLOW_PLAYER_DAMAGE_IN_TEST = b.comment("试运行时允许对玩家造成伤害")
                .translation("config.dimensional_mana.magiccircle.allow_player_damage_in_test")
                .define("allowPlayerDamageInTest", false);
        OVERLOAD_BACKLASH = b.comment("紊乱值过高时允许反噬（随机元素反应 / 反向效果 / 短暂虚弱）")
                .translation("config.dimensional_mana.magiccircle.overload_backlash")
                .define("overloadBacklash", true);
        b.pop();

        b.comment("召唤功能块的生物黑白名单与生命周期").push("magiccircle.summon");
        SUMMON_ENABLED = b.comment("是否允许召唤功能块")
                .translation("config.dimensional_mana.magiccircle.summon_enabled")
                .define("enabled", true);
        SUMMON_WHITELIST = b.comment("白名单：为空表示除黑名单外都允许召唤")
                .translation("config.dimensional_mana.magiccircle.summon_whitelist")
                .defineListAllowEmpty("whitelist", List.of(), o -> o instanceof String);
        SUMMON_BLACKLIST = b.comment("黑名单：命中直接哑火并提示「该生物被法则禁止召唤」")
                .translation("config.dimensional_mana.magiccircle.summon_blacklist")
                .defineListAllowEmpty("blacklist", List.of(
                        "minecraft:wither", "minecraft:ender_dragon", "minecraft:warden",
                        "minecraft:elder_guardian", "minecraft:evoker"), o -> o instanceof String);
        MAX_SUMMONS_PER_NETWORK = b.comment("每个法阵网络同时存在的召唤物上限")
                .translation("config.dimensional_mana.magiccircle.max_summons_per_network")
                .defineInRange("maxSummonsPerNetwork", 8, 0, 256);
        SUMMON_LIFETIME_SECONDS = b.comment("召唤物生命周期（秒），到点消散；再次触发会刷新")
                .translation("config.dimensional_mana.magiccircle.summon_lifetime_seconds")
                .defineInRange("summonLifetimeSeconds", 300, 1, 86400);
        b.pop();

        b.comment("挖矿功能块的黑白名单、区块与频率限制").push("magiccircle.mining");
        MINING_ENABLED = b.comment("是否允许挖矿功能块；关掉后挖矿阵变成纯装饰")
                .translation("config.dimensional_mana.magiccircle.mining_enabled")
                .define("enabled", true);
        MINING_BLOCK_BLACKLIST = b.comment("禁止被挖矿阵破坏的方块")
                .translation("config.dimensional_mana.magiccircle.mining_block_blacklist")
                .defineListAllowEmpty("blockBlacklist", List.of(
                        "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:end_portal",
                        "minecraft:nether_portal", "minecraft:barrier", "minecraft:command_block",
                        "minecraft:structure_block", "minecraft:jigsaw"), o -> o instanceof String);
        MINING_BLOCK_WHITELIST = b.comment("白名单；为空表示除黑名单外都允许")
                .translation("config.dimensional_mana.magiccircle.mining_block_whitelist")
                .defineListAllowEmpty("blockWhitelist", List.of(), o -> o instanceof String);
        REQUIRE_LOADED_CHUNK = b.comment("目标方块所在区块必须已加载才执行")
                .translation("config.dimensional_mana.magiccircle.require_loaded_chunk")
                .define("requireLoadedChunk", true);
        MAX_BLOCKS_PER_TRIGGER = b.comment("单次触发最多破坏的方块数")
                .translation("config.dimensional_mana.magiccircle.max_blocks_per_trigger")
                .defineInRange("maxBlocksPerTrigger", 32, 0, 1024);
        MAX_BLOCKS_PER_MINUTE = b.comment("每分钟累计最多破坏的方块数")
                .translation("config.dimensional_mana.magiccircle.max_blocks_per_minute")
                .defineInRange("maxBlocksPerMinute", 256, 0, 100000);
        ALLOW_SILK_TOUCH = b.comment("精准采集粉（青金石粉）是否让挖矿阵带精准采集")
                .translation("config.dimensional_mana.magiccircle.allow_silk_touch")
                .define("allowSilkTouch", true);
        DROP_TO_INVENTORY = b.comment("true = 产物直接进背包；false = 原地掉落")
                .translation("config.dimensional_mana.magiccircle.drop_to_inventory")
                .define("dropToInventory", false);
        b.pop();

        b.comment("v2.2 输出节点重构 + 条件节点声明模式（规范第 10~20 节）").push("magiccircle.condition");
        DECLARE_MODE_ENABLED = b.comment("总开关：true = 输出节点按 v2.2 走「终端执行 / 前置编译词缀」，"
                        + "并且缺声明的终端 E1 报错；false = 退回 v2.1 的旧结算（输出块直接按元素打）")
                .translation("config.dimensional_mana.magiccircle.declare_mode_enabled")
                .define("declareModeEnabled", true);
        MAX_DECLARE_NODES_PER_TERMINAL = b.comment("一个终端最多接几个声明条件节点")
                .translation("config.dimensional_mana.magiccircle.max_declare_nodes_per_terminal")
                .defineInRange("maxDeclareNodesPerTerminal", 3, 1, 16);
        MAX_DECLARE_NODES_PER_PREFIX_PATH = b.comment("一条「前置→终端」路径最多经过几个声明条件节点")
                .translation("config.dimensional_mana.magiccircle.max_declare_nodes_per_prefix_path")
                .defineInRange("maxDeclareNodesPerPrefixPath", 2, 0, 8);
        CONDITION_BASE_COST = b.comment("条件节点（声明模式）基础魔素：第 21 节，默认 4")
                .translation("config.dimensional_mana.magiccircle.condition_base_cost")
                .defineInRange("conditionBaseCost", 4, 0, 1000);
        CONDITION_EXTRA_GROUP_COST = b.comment("每多声明一个边界组给条件节点加的基础魔素，默认 2")
                .translation("config.dimensional_mana.magiccircle.condition_extra_group_cost")
                .defineInRange("conditionExtraGroupCost", 2, 0, 1000);
        RUNTIME_FAIL_COOLDOWN_TICKS = b.comment("运行时条件不满足哑火后的冷却（刻），默认 40 = 2 秒")
                .translation("config.dimensional_mana.magiccircle.runtime_fail_cooldown_ticks")
                .defineInRange("runtimeFailCooldownTicks", 40, 0, 20000);
        b.pop();

        b.comment("v2.2 词缀系统：容量、复杂度税、兼容矩阵").push("magiccircle.output");
        COMPLEXITY_TAX_PER_POINT = b.comment("每个复杂度点给终端节点基础值加的魔素（复杂度税），默认 2")
                .translation("config.dimensional_mana.magiccircle.complexity_tax_per_point")
                .defineInRange("complexityTaxPerPoint", 2, 0, 100);
        MAX_PREFIX_PER_TERMINAL = b.comment("一个终端最多能收多少个前置词缀（硬上限，默认 8）")
                .translation("config.dimensional_mana.magiccircle.max_prefix_per_terminal")
                .defineInRange("maxPrefixPerTerminal", 8, 1, 64);
        COMPATIBILITY_STRICT = b.comment("严格兼容：不兼容的词缀直接 E1；关掉后要靠「强制兼容」声明放行（紊乱+1）")
                .translation("config.dimensional_mana.magiccircle.compatibility_strict")
                .define("compatibilityStrict", true);
        ALLOW_ELEMENT_HEAL = b.comment("糖终端的元素治疗扩展（糖+水/光/木 的额外治疗），默认关")
                .translation("config.dimensional_mana.magiccircle.allow_element_heal")
                .define("allowElementHeal", false);
        SAME_TAG_BONUS = b.comment("同意义加成：词缀标签和终端主协议相同时的数值倍率，默认 1.15")
                .translation("config.dimensional_mana.magiccircle.same_tag_bonus")
                .defineInRange("sameTagBonus", 1.15, 1.0, 3.0);
        DYE_TERMINAL_STEP = b.comment("终端染料槽的加值步长：治疗/伤害 += 染料消耗系数 × 该值 × 原值，默认 0.1")
                .translation("config.dimensional_mana.magiccircle.dye_terminal_step")
                .defineInRange("dyeTerminalStep", 0.10, 0.0, 1.0);
        b.pop();

        b.comment("v2.2 效率与紊乱：E_total 允许负值（消耗上浮），紊乱过线必须声明紊乱策略").push("magiccircle.efficiency");
        MIN_TOTAL_EFFICIENCY = b.comment("总效率减免下限；-1.00 表示最多贵到 2 倍（负减免 = 加价）")
                .translation("config.dimensional_mana.magiccircle.min_total_efficiency")
                .defineInRange("minTotalEfficiency", -1.00, -5.0, 0.0);
        DISORDER_DECLARE_THRESHOLD = b.comment("总紊乱超过这个数还想启动，就必须声明「紊乱策略」边界组，默认 4")
                .translation("config.dimensional_mana.magiccircle.disorder_declare_threshold")
                .defineInRange("disorderDeclareThreshold", 4, 0, 100);
        MISFIRE_CHANCE = b.comment("冲突词缀且没有稳定块时，每次触发的哑火概率，默认 0.15")
                .translation("config.dimensional_mana.magiccircle.misfire_chance")
                .defineInRange("misfireChance", 0.15, 0.0, 1.0);
        IMPORT_MAX_CHARS = b.comment("法阵码导入 / 导出的最大字符数（服务器限额），默认 16384")
                .translation("config.dimensional_mana.magiccircle.import_max_chars")
                .defineInRange("importMaxChars", 16384, 256, 65536);
        b.pop();

        SPEC = b.build();
    }

    private MagicCircleConfig() {}
}
