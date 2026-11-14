package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模组自己那几套野外刷怪的开关与生物权重（配置文件 {@code dimensional_mana-spawns.toml}）。
 *
 * <p>原版的世界设置 / 游戏规则 {@code doMobSpawning} 只关得掉原版的自然刷怪，
 * 管不到模组自己往世界里塞的哥布林巡逻队、营地居民、魔晶洞穴史莱姆和祭坛守卫；
 * 玩家想把整个世界的生物关掉时，就会觉得「关了还是刷」。
 * 这里给一个总开关，并默认跟随原版规则，具体判定见 {@link SpawnControl}。</p>
 *
 * <p>r105 起多了一张 {@code [spawn_weights]} 表：本模组每一种会自然生成的生物一个权重值，
 * 默认 100。它的含义是「这种生物的出现量相对默认值的百分比」——
 * 50 就是只刷原来的一半，0 就是再也不自动生成，200 就是翻倍（上限 1000）。
 * 读取与判定在 {@link MobSpawnWeights}，各生成器按它抽取 / 缩放概率。</p>
 *
 * <p>r107 把默认值收回到「模组本来的量」：三个总倍率都是 1.0，环带上限是 128 格，
 * 开局爬坡改为 3 天到满。玩家已有的旧配置文件在 {@link #migrateLegacyDefaults()} 里
 * 一次性翻新——只有六项都还是旧默认值（说明从没被手动改过）时才动。</p>
 */
public final class SpawnConfig {
    public static final ModConfigSpec SPEC;

    /** 模组自己的野外刷怪总开关；关掉后巡逻、营地、洞穴、守卫都不再生成。 */
    public static final ModConfigSpec.BooleanValue MOD_SPAWNING_ENABLED;

    /** 是否跟随原版的刷怪规则（doMobSpawning 与和平难度）。 */
    public static final ModConfigSpec.BooleanValue FOLLOW_VANILLA_SPAWNING_RULES;

    /** 默认权重：100 表示「和以前一样」，配置文件里每个生物都有自己的一个数。 */
    public static final int DEFAULT_WEIGHT = 100;

    /** 实体 id（不带命名空间）→ 权重配置项。顺序就是 toml 里的书写顺序。 */
    public static final Map<String, ModConfigSpec.IntValue> WEIGHTS;

    /** 实体 id → 中文名，指令里列出来用。 */
    public static final Map<String, String> NAMES;

    /** 开局刷怪爬坡总开关：刚进存档刷得少，随时间回到正常量。 */
    public static final ModConfigSpec.BooleanValue PACING_ENABLED;
    /** 第 1 天的刷怪倍率（0.30 = 只有正常量的三成）。 */
    public static final ModConfigSpec.DoubleValue PACING_START_SCALE;
    /** 第几天恢复到 100%。 */
    public static final ModConfigSpec.IntValue PACING_FULL_STRENGTH_DAYS;

    /** 元素史莱姆整体倍率（再乘到每种史莱姆自己的权重上）。 */
    public static final ModConfigSpec.DoubleValue RATE_ELEMENTAL_SLIME;
    /** 哥布林巡逻队整体倍率。 */
    public static final ModConfigSpec.DoubleValue RATE_GOBLIN_PATROL;
    /** 蚀魔荒原白天补刷整体倍率。 */
    public static final ModConfigSpec.DoubleValue RATE_WASTELAND;

    /** 元素史莱姆离玩家的最小 / 最大水平距离（格）。 */
    public static final ModConfigSpec.DoubleValue SLIME_MIN_DISTANCE;
    public static final ModConfigSpec.DoubleValue SLIME_MAX_DISTANCE;
    /** 哥布林巡逻离玩家的最小 / 最大水平距离（格）。 */
    public static final ModConfigSpec.DoubleValue GOBLIN_MIN_DISTANCE;
    public static final ModConfigSpec.DoubleValue GOBLIN_MAX_DISTANCE;

    // ---- 默认值集中在这里：改默认值只动这一段，指令 reset 和老配置迁移都从这里取 ----

    /** 开局爬坡第 1 天的倍率。 */
    public static final double DEFAULT_PACING_START = 0.30;
    /** 开局爬坡第几天回到 100%。r107 按反馈从 10 天收紧到 3 天。 */
    public static final int DEFAULT_PACING_FULL_DAYS = 3;
    /** 各套刷怪系统的默认倍率：1.0 = 模组原来的量，调多少由玩家自己决定。 */
    public static final double DEFAULT_RATE = 1.0;
    /** 元素史莱姆的默认环带：近处不刷在房顶院子，最远和原版自然刷怪一样是 128 格。 */
    public static final double DEFAULT_SLIME_MIN = 28.0;
    public static final double DEFAULT_SLIME_MAX = 128.0;
    /** 哥布林巡逻队的默认环带。 */
    public static final double DEFAULT_GOBLIN_MIN = 40.0;
    public static final double DEFAULT_GOBLIN_MAX = 128.0;

    /**
     * r106 那一版的默认值。老配置文件里会原样留着这几个数：六项全都还是它们时，
     * 说明这份文件从来没被玩家动过，可以整体翻新成新默认值（见 {@link #migrateLegacyDefaults()}）。
     */
    private static final double LEGACY_RATE_SLIME = 0.6;
    private static final double LEGACY_RATE_GOBLIN = 0.8;
    private static final double LEGACY_RATE_WASTELAND = 0.8;
    private static final double LEGACY_SLIME_MAX = 48.0;
    private static final double LEGACY_GOBLIN_MAX = 64.0;
    private static final int LEGACY_FULL_DAYS = 10;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("模组自己的野外刷怪：哥布林巡逻、哥布林营地居民、魔晶洞穴史莱姆、祭坛守卫与冒险者")
                .push("world_spawning");
        MOD_SPAWNING_ENABLED = b.comment("模组刷怪总开关。false = 这些生物只在法术召唤、刷怪蛋、结构命令里出现，"
                        + "不再随世界自动生成")
                .translation("config.dimensional_mana.spawn.mod_spawning_enabled")
                .define("modSpawningEnabled", true);
        FOLLOW_VANILLA_SPAWNING_RULES = b.comment("跟随原版规则：/gamerule doMobSpawning false 时模组也停止刷怪，"
                        + "和平难度下不生成敌对生物。true = 想彻底关掉模组刷怪，建议连同上一项一起关闭")
                .translation("config.dimensional_mana.spawn.follow_vanilla_spawning_rules")
                .define("followVanillaSpawningRules", true);
        b.pop();

        b.comment("开局刷怪爬坡：参考原版的区域难度，刚进新存档时刷得少，随时间慢慢回到正常量。",
                        "只影响模组自己的环境刷怪（元素史莱姆、哥布林巡逻、荒原补刷）；"
                        + "刷怪蛋、指令、法术召唤和结构里本来就该有的生物不受影响。",
                        "魔素浓度高于 12.00 的地方不吃这条曲线：魔晶洞穴、露天魔晶矿簇、古代祭坛、"
                        + "蚀魔荒原这些高危区从进存档第一天起就是满强度（浓度可以在游戏里用感知类道具看）。")
                .push("spawn_pacing");
        PACING_ENABLED = b.comment("false = 关掉爬坡，从第一天起就是正常量。")
                .translation("config.dimensional_mana.spawn.pacing_enabled")
                .define("enabled", true);
        PACING_START_SCALE = b.comment("进入世界第 1 天的刷怪倍率。0.30 = 只有正常量的三成；"
                        + "调成 1.0 等于没有过渡期。")
                .translation("config.dimensional_mana.spawn.pacing_start_scale")
                .defineInRange("start_scale", DEFAULT_PACING_START, 0.0, 1.0);
        PACING_FULL_STRENGTH_DAYS = b.comment("玩满几天后恢复到正常量（100%）。默认 3 天（72,000 刻）；"
                        + "进世界当天是三成，之后每天线性上涨。")
                .translation("config.dimensional_mana.spawn.pacing_full_strength_days")
                .defineInRange("full_strength_days", DEFAULT_PACING_FULL_DAYS, 1, 365);
        b.pop();

        b.comment("各套刷怪系统的总倍率。1.0 = 原来的量，0.5 = 只刷一半，0 = 这套系统不再自动刷。"
                        + "它和下面 [spawn_weights] 里每种生物的权重是相乘的关系。",
                        "默认全是 1.0（模组本来的量）；觉得哪一种太多，就把它调到 0.5 左右试试。")
                .push("spawn_rate");
        RATE_ELEMENTAL_SLIME = b.comment("元素史莱姆总量倍率（默认 1.0 = 原来的量）。")
                .translation("config.dimensional_mana.spawn.rate_elemental_slime")
                .defineInRange("elemental_slime", DEFAULT_RATE, 0.0, 5.0);
        RATE_GOBLIN_PATROL = b.comment("哥布林巡逻队总量倍率（默认 1.0 = 原来的量）。营地是结构，不受这里影响。")
                .translation("config.dimensional_mana.spawn.rate_goblin_patrol")
                .defineInRange("goblin_patrol", DEFAULT_RATE, 0.0, 5.0);
        RATE_WASTELAND = b.comment("蚀魔荒原白天补刷总量倍率（默认 1.0 = 原来的量）。")
                .translation("config.dimensional_mana.spawn.rate_wasteland")
                .defineInRange("wasteland_day", DEFAULT_RATE, 0.0, 5.0);
        b.pop();

        b.comment("刷怪距离：以玩家为圆心，只在「最小距离～最大距离」这个环形里挑落点。",
                        "把最小距离调大，就不会再刷在房顶、院子或者刚出门的脚边；"
                        + "最大距离负责「别刷得太远」，默认 128 格和原版自然刷怪的最远距离一致。",
                        "落点必须落在已经加载的区域里：没加载的区块这一轮直接跳过，"
                        + "不会为了刷一只怪去生成地形——这正是以前玩久了卡顿、地形刷不出来的原因之一。")
                .push("spawn_distance");
        SLIME_MIN_DISTANCE = b.comment("元素史莱姆离玩家至少多少格（默认 28）。")
                .translation("config.dimensional_mana.spawn.slime_min_distance")
                .defineInRange("elemental_slime_min", DEFAULT_SLIME_MIN, 0.0, 256.0);
        SLIME_MAX_DISTANCE = b.comment("元素史莱姆最远在多少格内生成（默认 128，比最小距离小则按最小距离算）。")
                .translation("config.dimensional_mana.spawn.slime_max_distance")
                .defineInRange("elemental_slime_max", DEFAULT_SLIME_MAX, 1.0, 256.0);
        GOBLIN_MIN_DISTANCE = b.comment("哥布林巡逻队离玩家至少多少格（默认 40）。")
                .translation("config.dimensional_mana.spawn.goblin_min_distance")
                .defineInRange("goblin_patrol_min", DEFAULT_GOBLIN_MIN, 0.0, 256.0);
        GOBLIN_MAX_DISTANCE = b.comment("哥布林巡逻队最远在多少格内生成（默认 128）。")
                .translation("config.dimensional_mana.spawn.goblin_max_distance")
                .defineInRange("goblin_patrol_max", DEFAULT_GOBLIN_MAX, 1.0, 256.0);
        b.pop();

        b.comment("本模组生物的自然生成权重。每一行对应一种生物，100 = 保持默认，"
                        + "50 = 只刷一半，0 = 不再自然生成，200 = 翻倍；上限 1000。"
                        + "改动可以用 /dimensional_mana spawnweights set <生物> <权重> 直接写进本文件，"
                        + "也可以在游戏内的配置界面里改。",
                        "刷怪蛋、指令召唤、法术召唤不受这里影响；这里只管「世界自己刷出来」的量。")
                .push("spawn_weights");
        Map<String, ModConfigSpec.IntValue> weights = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        b.comment("元素史莱姆：按群系与地形各自出场，权重只调整该种的出现量。").push("elemental_slime");
        addWeight(b, weights, names, "mana_elemental_slime", "魔素史莱姆", "魔晶洞穴与魔晶矿脉周围");
        addWeight(b, weights, names, "fire_elemental_slime", "火史莱姆", "岩浆与下界火海");
        addWeight(b, weights, names, "water_elemental_slime", "水史莱姆", "海洋、河流与雨天");
        addWeight(b, weights, names, "ice_elemental_slime", "冰史莱姆", "冰原、冻洋与积雪地面");
        addWeight(b, weights, names, "lightning_elemental_slime", "雷史莱姆", "落雷与巨响引来");
        addWeight(b, weights, names, "nature_elemental_slime", "木史莱姆", "森林、平原与草甸");
        addWeight(b, weights, names, "poison_elemental_slime", "毒史莱姆", "沼泽与污浊水域");
        addWeight(b, weights, names, "metal_elemental_slime", "金史莱姆", "矿洞与铁路附近");
        addWeight(b, weights, names, "wind_elemental_slime", "风史莱姆", "高空与开阔地带");
        addWeight(b, weights, names, "earth_elemental_slime", "土史莱姆", "地下深处");
        addWeight(b, weights, names, "light_elemental_slime", "光史莱姆", "正午晴天的露天位置");
        addWeight(b, weights, names, "sound_elemental_slime", "音史莱姆", "钟声与爆炸等巨响引来");
        b.pop();

        b.comment("哥布林：巡逻队与营地按权重在几种成员之间抽取，首领单独判定。").push("goblin");
        addWeight(b, weights, names, "goblin_warrior", "哥布林战士", "巡逻队与营地的主力");
        addWeight(b, weights, names, "goblin_archer", "哥布林弓手", "巡逻队与营地的远程位");
        addWeight(b, weights, names, "goblin_shaman", "哥布林萨满", "巡逻队与营地的施法位");
        addWeight(b, weights, names, "goblin_young", "哥布林幼崽", "营地里的非战斗成员");
        addWeight(b, weights, names, "goblin_chief", "哥布林首领", "每座营地一名，调到 0 则营地无首领");
        b.pop();

        b.comment("蚀魔荒原的魔素变异体：荒原白天补刷与露天魔晶矿簇守卫共用这张权重表。")
                .push("wasteland_mutant");
        addWeight(b, weights, names, "crystallized_zombie", "晶化行尸", "高魔素区块里替换普通僵尸");
        addWeight(b, weights, names, "corrupted_skeleton", "魔蚀骸骨", "高魔素区块里替换普通骷髅");
        addWeight(b, weights, names, "corrupted_silverfish", "秽蚀蠹", "末地传送门遗迹与荒原地表");
        addWeight(b, weights, names, "mana_creeper", "聚魔爆裂体", "高魔素区块里替换普通苦力怕");
        addWeight(b, weights, names, "corrupted_weaver", "魔蚀织网者", "高魔素区块里替换普通蜘蛛");
        addWeight(b, weights, names, "void_shade", "虚空蚀影", "高魔素区块里替换末影人");
        b.pop();

        b.comment("模组 NPC：结构点位上按各自概率出场。").push("npc");
        addWeight(b, weights, names, "wandering_witch", "游荡女巫", "古代魔法祭坛的守卫");
        addWeight(b, weights, names, "wandering_swordmaster", "流浪剑士", "村庄里的常驻剑术大师");
        addWeight(b, weights, names, "adventurer", "冒险者", "相邻祭坛之间的路上");
        b.pop();

        WEIGHTS = Collections.unmodifiableMap(weights);
        NAMES = Collections.unmodifiableMap(names);
        b.pop();
        SPEC = b.build();
    }

    private SpawnConfig() { }

    /** 指令写入：把一种生物的权重写进配置并落盘；配置文件没加载完或 id 不认识时返回 false。 */
    public static boolean setWeight(String mobKey, int weight) {
        ModConfigSpec.IntValue config = WEIGHTS.get(mobKey);
        if (config == null || !SPEC.isLoaded()) return false;
        config.set(Math.max(0, Math.min(1000, weight)));
        SPEC.save();
        return true;
    }

    /** 指令重置：权重、倍率、距离、爬坡全部恢复默认，返回改动的项数。 */
    public static int resetAll() {
        if (!SPEC.isLoaded()) return 0;
        for (ModConfigSpec.IntValue config : WEIGHTS.values()) config.set(DEFAULT_WEIGHT);
        RATE_ELEMENTAL_SLIME.set(DEFAULT_RATE);
        RATE_GOBLIN_PATROL.set(DEFAULT_RATE);
        RATE_WASTELAND.set(DEFAULT_RATE);
        SLIME_MIN_DISTANCE.set(DEFAULT_SLIME_MIN);
        SLIME_MAX_DISTANCE.set(DEFAULT_SLIME_MAX);
        GOBLIN_MIN_DISTANCE.set(DEFAULT_GOBLIN_MIN);
        GOBLIN_MAX_DISTANCE.set(DEFAULT_GOBLIN_MAX);
        PACING_START_SCALE.set(DEFAULT_PACING_START);
        PACING_FULL_STRENGTH_DAYS.set(DEFAULT_PACING_FULL_DAYS);
        SPEC.save();
        return WEIGHTS.size() + 9;
    }

    /**
     * 老配置文件迁移（服务器启动时跑一次）。
     *
     * <p>r106 生成的默认值是「史莱姆 0.6、哥布林 0.8、荒原 0.8、最远 48 / 64 格、第 10 天满强度」。
     * 这一版把这些默认值都收回原样，但玩家硬盘上的文件早就把旧数写死了，光改代码不会跟着变。
     * 所以这里做一次整体翻新：<b>只有这六项全都还是旧默认值</b>（= 这份文件从来没被手动改过）
     * 才一起改成新默认值；只要玩家动过其中任何一项，就认为他在自己调，我们一项都不碰。</p>
     *
     * <p>翻新之后这六项都不再等于旧值，所以这个迁移天然只生效一次，不会每次开服都把玩家
     * 手动调回去。想一键拉回本版默认值，用 {@code /dimensional_mana spawnweights reset}。</p>
     */
    public static void migrateLegacyDefaults() {
        if (!SPEC.isLoaded()) return;
        boolean untouched = Double.compare(RATE_ELEMENTAL_SLIME.get(), LEGACY_RATE_SLIME) == 0
                && Double.compare(RATE_GOBLIN_PATROL.get(), LEGACY_RATE_GOBLIN) == 0
                && Double.compare(RATE_WASTELAND.get(), LEGACY_RATE_WASTELAND) == 0
                && Double.compare(SLIME_MAX_DISTANCE.get(), LEGACY_SLIME_MAX) == 0
                && Double.compare(GOBLIN_MAX_DISTANCE.get(), LEGACY_GOBLIN_MAX) == 0
                && PACING_FULL_STRENGTH_DAYS.get() == LEGACY_FULL_DAYS;
        if (!untouched) return;
        RATE_ELEMENTAL_SLIME.set(DEFAULT_RATE);
        RATE_GOBLIN_PATROL.set(DEFAULT_RATE);
        RATE_WASTELAND.set(DEFAULT_RATE);
        SLIME_MAX_DISTANCE.set(DEFAULT_SLIME_MAX);
        GOBLIN_MAX_DISTANCE.set(DEFAULT_GOBLIN_MAX);
        PACING_FULL_STRENGTH_DAYS.set(DEFAULT_PACING_FULL_DAYS);
        SPEC.save();
        org.slf4j.LoggerFactory.getLogger("dimensional_mana").info(
                "dimensional_mana-spawns.toml 是旧版本的默认值，已翻新成本版默认值："
                        + "倍率 1.0、最远 128 格、开局爬坡 3 天到满。");
    }

    /** 某种生物当前的权重；配置文件还没加载完时返回它写下的默认值。 */
    public static int weightOf(String mobKey) {
        ModConfigSpec.IntValue value = WEIGHTS.get(mobKey);
        if (value == null) return DEFAULT_WEIGHT;
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** 这个 id 是不是本模组配置表里登记过的生物。 */
    public static boolean hasWeight(String mobKey) {
        return WEIGHTS.containsKey(mobKey);
    }

    private static void addWeight(ModConfigSpec.Builder builder, Map<String, ModConfigSpec.IntValue> sink,
                                  Map<String, String> names, String key, String name, String where) {
        names.put(key, name);
        sink.put(key, builder.comment(name + "（" + key + "）：主要出现在" + where + "。"
                        + "100 = 保持默认，50 = 只刷一半，0 = 不再自然生成，200 = 翻倍（上限 1000）。")
                .translation("config.dimensional_mana.spawn.weight." + key)
                .defineInRange(key, DEFAULT_WEIGHT, 0, 1000));
    }
}
