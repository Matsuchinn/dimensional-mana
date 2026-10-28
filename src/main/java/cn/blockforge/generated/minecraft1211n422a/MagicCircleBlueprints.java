package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学蓝图（设计文档 9.1 + v2.2 规范 8）：r209 按验收测试单重排成 <b>五张</b>，
 * 全部走同一条规范链路：
 *
 * <pre>触发 → 核心 →（前置输出 →）声明条件 → 终端输出</pre>
 *
 * <ul>
 *   <li><b>触发在核心前</b>：链上第一个点是触发，核心永远接在触发下游；</li>
 *   <li><b>前置输出必须接核心</b>：想给终端挂词缀，前置直接连核心，不能绕；</li>
 *   <li><b>声明条件插在前置和终端中间</b>：条件节点必须悬在通往终端的必经路上
 *       （没有前置时就是核心 → 条件 → 终端）。像旧版那样从边上并一根线到终端，
 *       图分析会按「条件排在终端下游」报 cond_dead——这正是 r208 四张案例全报错的原因。</li>
 * </ul>
 *
 * <p>五张阵（编号对应测试单）：</p>
 * <ol>
 *   <li><b>最小伤害阵</b>（2×2）：触发→核心→条件(伤害边界)→终端(火药+火)，测「缺声明会拦」；</li>
 *   <li><b>爆炸火球阵</b>（3×3）：前置(火+火药→爆炸词缀)→条件→终端，测前置词缀与同意义 ×1.15；</li>
 *   <li><b>治疗阵</b>（2×2）：糖终端 + 治疗边界（灰染料 2 档 = 单次封顶 20 点）；</li>
 *   <li><b>守望召唤阵</b>（3×3）：前置(糖+木)编译成友好召唤词缀 → 召唤边界（黑染料 3 档 = 4 只、
 *       300 秒、守卫锚点）→ 终端(糖、元素空)执行；</li>
 *   <li><b>掘矿阵</b>（3×3）：青金+金终端，一块条件声明「交互 + 信息」两组（中级功率才放得下）。</li>
 * </ol>
 */
public final class MagicCircleBlueprints {

    public static final String SPARK_ITEM = "worn_blueprint_spark";
    public static final String FIREBALL_ITEM = "worn_blueprint_fireball";
    public static final String HEALING_ITEM = "worn_blueprint_healing";
    public static final String MINE_ITEM = "worn_blueprint_mine";
    public static final String GUARD_ITEM = "worn_blueprint_guard";

    private static final String FIRE = "dimensional_mana:fire_element_crystal_dust";
    private static final String NATURE = "dimensional_mana:nature_element_crystal_dust";
    private static final String METAL = "dimensional_mana:metal_element_crystal_dust";
    private static final String LESSER = "dimensional_mana:lesser_mana_crystal_dust";
    private static final String MEDIUM = "dimensional_mana:medium_mana_crystal_dust";
    private static final String REDSTONE = "minecraft:redstone";
    private static final String GUNPOWDER = "minecraft:gunpowder";
    private static final String SUGAR = "minecraft:sugar";
    private static final String LAPIS = "dimensional_mana:lapis_dust";
    private static final String WHITE_DYE = "minecraft:white_dye";
    private static final String GRAY_DYE = "minecraft:gray_dye";
    private static final String BLACK_DYE = "minecraft:black_dye";
    private static final String ORANGE_DYE = "minecraft:orange_dye";

    private static final Map<String, MagicCircleBlueprint> WORN = new LinkedHashMap<>();

    static {
        WORN.put(SPARK_ITEM, build(SPARK_ITEM, "最小伤害阵", 2, sparkLayer()));
        WORN.put(FIREBALL_ITEM, build(FIREBALL_ITEM, "爆炸火球阵", 3, fireballLayer()));
        WORN.put(HEALING_ITEM, build(HEALING_ITEM, "治疗阵", 2, healingLayer()));
        WORN.put(GUARD_ITEM, build(GUARD_ITEM, "守望召唤阵", 3, guardLayer()));
        WORN.put(MINE_ITEM, build(MINE_ITEM, "掘矿阵", 3, mineLayer()));
    }

    private static MagicCircleBlueprint build(String itemId, String name, int baseSize,
                                              MagicCircleNetwork.Layer layer) {
        return new MagicCircleBlueprint(itemId, name, "次元物语", baseSize, baseSize,
                MagicCircleBlueprint.Carrier.WORN, List.of(layer));
    }

    /**
     * 法阵 1 · 最小伤害阵（2×2）：触发 → 核心 → 条件(伤害边界) → 终端(火药+火)。
     * 条件染料白色 = 0 档，真实伤害 0%；PvP / 友伤 / 方块破坏默认全开（r211），
     * 新手一发就能看到掉血。
     * 删掉条件节点再试运行，就会被 E1「缺伤害边界」拦下，这是它的教学点。
     */
    private static MagicCircleNetwork.Layer sparkLayer() {
        MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
        layer.add(node(MagicCircleBlockType.TRIGGER, 5, 16, "", REDSTONE, "", ""));
        layer.add(node(MagicCircleBlockType.CORE, 12, 16, "", REDSTONE, LESSER, ""));
        layer.add(decl(LESSER, WHITE_DYE, GUNPOWDER, "c1|da;0;0;7", 20, 16));
        layer.add(node(MagicCircleBlockType.OUTPUT, 27, 16, FIRE, GUNPOWDER, LESSER, ""));
        linkChain(layer);
        return layer;
    }

    /**
     * 法阵 2 · 爆炸火球阵（3×3）：触发 → 核心；核心 → 前置(火+火药=爆炸词缀) → 条件 → 终端。
     * 爆炸词缀复杂度 2，前置功率至少要<b>中级</b>（下级只放行复杂度 1，会报「词缀复杂度超上限」，
     * 这是规范 1.4 的原样执法，不是 bug）。终端吃同意义加成 ×1.15，伤害比法阵 1 高一截。
     */
    private static MagicCircleNetwork.Layer fireballLayer() {
        MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
        layer.add(node(MagicCircleBlockType.TRIGGER, 7, 24, "", REDSTONE, "", ""));
        layer.add(node(MagicCircleBlockType.CORE, 15, 24, "", REDSTONE, LESSER, ""));
        layer.add(node(MagicCircleBlockType.OUTPUT, 23, 16, FIRE, GUNPOWDER, MEDIUM, ""));
        layer.add(decl(LESSER, WHITE_DYE, GUNPOWDER, "c1|da;0;0;7", 31, 24));
        layer.add(node(MagicCircleBlockType.OUTPUT, 39, 24, FIRE, GUNPOWDER, LESSER, ""));
        linkChain(layer);
        return layer;
    }

    /**
     * 法阵 3 · 治疗阵（2×2）：触发 → 核心 → 条件(糖=治疗边界, 灰染料 2 档) → 终端(糖, 元素空)。
     * 一发的总治疗被边界声明夹在 20 点以内；元素槽空着 = 治疗分支，放了木元素就变召唤分支。
     */
    private static MagicCircleNetwork.Layer healingLayer() {
        MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
        layer.add(node(MagicCircleBlockType.TRIGGER, 5, 16, "", REDSTONE, "", ""));
        layer.add(node(MagicCircleBlockType.CORE, 12, 16, "", REDSTONE, LESSER, ""));
        layer.add(decl(MEDIUM, GRAY_DYE, SUGAR, "c1|he;20;0;0", 20, 16));
        layer.add(node(MagicCircleBlockType.OUTPUT, 27, 16, "", SUGAR, LESSER, ""));
        linkChain(layer);
        return layer;
    }

    /**
     * 法阵 4 · 守望召唤阵（3×3，r211 修正版）：触发 → 核心；核心 → 前置(糖+木=友好召唤词缀)
     * → 条件(召唤边界) → 终端(糖、元素空)。
     *
     * <p>召唤词缀由<b>前置输出</b>提供（糖语义 + 木元素 = summon_friendly，复杂度 2，
     * 前置功率至少中级）；终端只负责执行，元素槽<b>故意留空</b>——放了木元素终端会自带
     * 元素附着，试运行的表现就不是「纯守卫」了。条件走召唤边界：最大 4 只、寿命 300 秒、
     * AI 守卫锚点、归属 owner+同队、锚点失效消散、不许碰 Boss（黑染料 3 档 = 4 只）。</p>
     */
    private static MagicCircleNetwork.Layer guardLayer() {
        MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
        layer.add(node(MagicCircleBlockType.TRIGGER, 7, 24, "", REDSTONE, "", ""));
        layer.add(node(MagicCircleBlockType.CORE, 15, 24, "", REDSTONE, LESSER, ""));
        layer.add(node(MagicCircleBlockType.OUTPUT, 23, 16, NATURE, SUGAR, MEDIUM, ""));
        layer.add(decl(MEDIUM, BLACK_DYE, SUGAR, "c1|su;4;300;2;0;0;0", 31, 24));
        layer.add(node(MagicCircleBlockType.OUTPUT, 39, 24, "", SUGAR, LESSER, ""));
        linkChain(layer);
        return layer;
    }

    /**
     * 法阵 5 · 掘矿阵（3×3）：触发 → 核心(中级) → 条件(青金, 中级=2 组, 橙染料 6 档) → 终端(青金+金)。
     * 一块条件节点声明两组边界：交互（每分钟 128 块、单次 32 块、区块加载开、精准采集开、掉落原地）
     * + 信息（扫描半径 16 格）。条件功率换回下级就只能放 1 组，第二组会被容量拦下——也是教学点。
     */
    private static MagicCircleNetwork.Layer mineLayer() {
        MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
        layer.add(node(MagicCircleBlockType.TRIGGER, 7, 24, "", REDSTONE, "", ""));
        layer.add(node(MagicCircleBlockType.CORE, 15, 24, "", REDSTONE, MEDIUM, ""));
        layer.add(decl(MEDIUM, ORANGE_DYE, LAPIS, "c1|in;128;32;3|nf;16;0;0;0", 25, 24));
        layer.add(node(MagicCircleBlockType.OUTPUT, 35, 24, METAL, LAPIS, LESSER, ""));
        linkChain(layer);
        return layer;
    }

    /** 按列表顺序把每一块串到下一块：触发 → 核心 →（前置）→ 条件 → 终端。 */
    private static void linkChain(MagicCircleNetwork.Layer layer) {
        int size = layer.nodes().size();
        for (int i = 0; i + 1 < size; i++) {
            layer.addLink(i, i + 1);
        }
    }

    private static MagicCircleNetwork.Node node(MagicCircleBlockType type, int x, int y,
                                                String element, String semantic, String power, String dye) {
        return new MagicCircleNetwork.Node(type, x, y, element, semantic, power, dye, "", false);
    }

    /** 声明模式的条件节点：原版语义粉定家族，declare 串带边界组参数。 */
    private static MagicCircleNetwork.Node decl(String power, String dye, String semantic,
                                                String declare, int x, int y) {
        return new MagicCircleNetwork.Node(MagicCircleBlockType.CONDITION, x, y, "", semantic,
                power, dye, "", false, declare);
    }

    /** 按物品 id 取教学蓝图；不是教学蓝图返回 null。 */
    public static MagicCircleBlueprint worn(String itemId) {
        return itemId == null ? null : WORN.get(itemId);
    }

    public static boolean isWorn(String id) {
        return id != null && WORN.containsKey(id);
    }

    public static Collection<MagicCircleBlueprint> allWorn() {
        return List.copyOf(WORN.values());
    }

    /**
     * 蓝图该变成哪个物品：教学蓝图还原成它自己的物品，普通蓝图按载体还原。
     *
     * <p>投影被挖掉时用它把蓝图退给玩家，避免拆一次投影就白丢一份阵图。</p>
     */
    public static ItemStack stackFor(MagicCircleBlueprint blueprint) {
        if (blueprint == null) return ItemStack.EMPTY;
        if (isWorn(blueprint.id())) {
            var holder = GeneratedMod.MOD_ITEMS.get(blueprint.id());
            if (holder != null) return new ItemStack(holder.get());
        }
        MagicCircleBlueprint.Carrier carrier = blueprint.carrier();
        if (!carrier.placeable()) carrier = MagicCircleBlueprint.Carrier.SILK;
        return MagicCircleBlueprint.createStack(carrier, blueprint);
    }

    private MagicCircleBlueprints() {}
}
