package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 两张教学蓝图（设计文档 9.1）：<b>残破的蓝图：火球阵</b> 与 <b>残破的蓝图：治疗阵</b>。
 *
 * <p>它们的图形写死在代码里，不依赖存档 / NBT，所以从创造物品栏拿出来就是完整的一份；
 * 右键绘制台基座可以「拆解研究」进画布，右键普通方块表面可以「贴面投影」先看效果。</p>
 *
 * <p>两张都是 2×2 基座（32×32 画布）的起步阵：</p>
 * <ul>
 *   <li>火球阵：核心 + 红石触发 + 火输出 + 范围，教会「元素 + 触发」；</li>
 *   <li>治疗阵：核心 + 红石触发 + 木治愈输出 + 持续，教会「非伤害输出」。</li>
 * </ul>
 */
public final class MagicCircleBlueprints {

    public static final String FIREBALL_ITEM = "worn_blueprint_fireball";
    public static final String HEALING_ITEM = "worn_blueprint_healing";

    private static final String ARCANE = "dimensional_mana:mana_element_crystal_dust";
    private static final String FIRE = "dimensional_mana:fire_element_crystal_dust";
    private static final String WATER = "dimensional_mana:water_element_crystal_dust";
    private static final String NATURE = "dimensional_mana:nature_element_crystal_dust";
    private static final String LESSER = "dimensional_mana:lesser_mana_crystal_dust";
    private static final String REDSTONE = "minecraft:redstone";
    private static final String GLOWSTONE = "minecraft:glowstone_dust";
    private static final String RED_DYE = "minecraft:red_dye";
    private static final String GREEN_DYE = "minecraft:green_dye";

    private static final Map<String, MagicCircleBlueprint> WORN = new LinkedHashMap<>();

    static {
        WORN.put(FIREBALL_ITEM, build(FIREBALL_ITEM, "火球阵", fireballLayer()));
        WORN.put(HEALING_ITEM, build(HEALING_ITEM, "治疗阵", healingLayer()));
    }

    private static MagicCircleBlueprint build(String itemId, String name, MagicCircleNetwork.Layer layer) {
        return new MagicCircleBlueprint(itemId, name, "次元物语", 2, 2,
                MagicCircleBlueprint.Carrier.WORN, List.of(layer));
    }

    /** 2×2 火球阵：核心(奥术+红石+下级魔晶粉) → 触发(红石) → 火输出 → 范围(萤石)。 */
    private static MagicCircleNetwork.Layer fireballLayer() {
        MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
        layer.add(node(MagicCircleBlockType.CORE, 8, 8, ARCANE, REDSTONE, LESSER, RED_DYE));
        layer.add(node(MagicCircleBlockType.TRIGGER, 24, 8, "", REDSTONE, "", ""));
        layer.add(node(MagicCircleBlockType.OUTPUT, 24, 24, FIRE, REDSTONE, LESSER, RED_DYE));
        layer.add(node(MagicCircleBlockType.AREA, 8, 24, "", GLOWSTONE, "", ""));
        layer.addLink(0, 1);
        layer.addLink(1, 2);
        layer.addLink(1, 3);
        return layer;
    }

    /** 2×2 治疗阵：核心(水+下级) → 触发(红石) → 木/治愈输出 → 持续(萤石)。 */
    private static MagicCircleNetwork.Layer healingLayer() {
        MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
        layer.add(node(MagicCircleBlockType.CORE, 8, 8, WATER, REDSTONE, LESSER, GREEN_DYE));
        layer.add(node(MagicCircleBlockType.TRIGGER, 24, 8, "", REDSTONE, "", ""));
        layer.add(node(MagicCircleBlockType.OUTPUT, 24, 24, NATURE, GREEN_DYE, LESSER, GREEN_DYE));
        layer.add(node(MagicCircleBlockType.DURATION, 8, 24, "", GLOWSTONE, "", ""));
        layer.addLink(0, 1);
        layer.addLink(1, 2);
        layer.addLink(1, 3);
        return layer;
    }

    private static MagicCircleNetwork.Node node(MagicCircleBlockType type, int x, int y,
                                                String element, String semantic, String power, String dye) {
        return new MagicCircleNetwork.Node(type, x, y, element, semantic, power, dye, "", false);
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
