package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 魔法阵 41 种粉末的唯一数据源。
 *
 * <p>四个分类对应绘制台粉末盘的四个标签页：原版粉（7）→ 魔晶粉（6）→ 元素粉（12）→ 染料（16）。
 * 每种粉带一个消耗系数与「按魔法等级解锁」的门槛；系数直接参与
 * {@link MagicCircleCost} 的单块成本计算。</p>
 */
public final class MagicCirclePowders {

    /** 奥术粉的物品 id；它没有 {@link ElementType} 对应项，法阵里按「高级媒介」单独处理。 */
    public static final String ARCANE_ITEM = "dimensional_mana:mana_element_crystal_dust";

    public static boolean isArcane(String itemId) {
        return ARCANE_ITEM.equals(itemId);
    }

    /**
     * 功率槽提供的效率（设计规范 v2.1）：下级 +1%、中级 +2%、高级 +4%、极品 +6%、特级 +8%、超导 +12%。
     * 每点效率让该节点的魔素消耗降低 1%；不是魔晶粉或空槽返回 0。
     */
    public static double powerEfficiency(String itemId) {
        if (itemId == null || itemId.isEmpty()) return 0.0;
        return switch (itemId) {
            case "dimensional_mana:lesser_mana_crystal_dust" -> 0.01;
            case "dimensional_mana:medium_mana_crystal_dust" -> 0.02;
            case "dimensional_mana:advanced_mana_crystal_dust" -> 0.04;
            case "dimensional_mana:supreme_mana_crystal_dust" -> 0.06;
            case "dimensional_mana:special_mana_crystal_dust" -> 0.08;
            case "dimensional_mana:superconducting_crystal_dust" -> 0.12;
            default -> 0.0;
        };
    }

    /** 功能块「功率槽效率」的判定：任何魔晶粉都算，界面统计超导数量时用。 */
    public static boolean isSuperconductor(String itemId) {
        return "dimensional_mana:superconducting_crystal_dust".equals(itemId);
    }

    /** 粉末盘的四个标签页，顺序就是界面里的排列顺序。 */
    public enum Category {
        VANILLA("vanilla", "原版粉"),
        MANA_CRYSTAL("crystal", "魔晶粉"),
        ELEMENT("element", "元素粉"),
        DYE("dye", "染料");

        private final String id;
        private final String fallbackName;

        Category(String id, String fallbackName) {
            this.id = id;
            this.fallbackName = fallbackName;
        }

        public String id() { return id; }
        public String fallbackName() { return fallbackName; }
        public String translationKey() { return "gui.dimensional_mana.magiccircle.category." + id; }
    }

    /** 功能块上的四个插槽：元素决定属性，语义决定做什么，功率决定多强，染料决定学派修饰。 */
    public enum Slot {
        ELEMENT("element"),
        SEMANTIC("semantic"),
        POWER("power"),
        DYE("dye");

        private final String id;

        Slot(String id) { this.id = id; }
        public String id() { return id; }
        public String translationKey() { return "gui.dimensional_mana.magiccircle.slot." + id; }
    }

    /**
     * 一种粉末。{@code itemId} 指向真实物品，所以粉末本身就是玩家背包里的那堆东西，
     * 不需要另造一套 41 个新物品。
     */
    public record Powder(String itemId, Category category, Slot slot, double coefficient, int requiredLevel) {
        public Item item() {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        }

        public ItemStack stack() {
            Item item = item();
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }

        public Component name() {
            ItemStack stack = stack();
            return stack.isEmpty() ? Component.literal(itemId) : stack.getHoverName();
        }

        public Component coloredName(ChatFormatting formatting) {
            return name().copy().withStyle(formatting);
        }

        public String shortId() {
            ResourceLocation id = ResourceLocation.parse(itemId);
            return id.getPath();
        }
    }

    private static final List<Powder> ALL = new ArrayList<>();
    private static final Map<String, Powder> BY_ITEM = new LinkedHashMap<>();
    private static final Map<Category, List<Powder>> BY_CATEGORY = new EnumMap<>(Category.class);
    private static final Map<ElementType, Powder> BY_ELEMENT = new EnumMap<>(ElementType.class);
    private static final Map<String, ElementType> ELEMENT_BY_ITEM = new LinkedHashMap<>();

    static {
        for (Category category : Category.values()) BY_CATEGORY.put(category, new ArrayList<>());

        // ---- 原版粉 7 种：决定「怎么触发、怎么修饰」 ----
        powder("minecraft:redstone", Category.VANILLA, Slot.SEMANTIC, 1.10, 0);
        powder("minecraft:sugar", Category.VANILLA, Slot.SEMANTIC, 0.90, 0);
        powder("minecraft:gunpowder", Category.VANILLA, Slot.SEMANTIC, 1.20, 0);
        powder("minecraft:blaze_powder", Category.VANILLA, Slot.SEMANTIC, 1.15, 0);
        powder("minecraft:glowstone_dust", Category.VANILLA, Slot.SEMANTIC, 1.00, 0);
        powder("dimensional_mana:lapis_dust", Category.VANILLA, Slot.SEMANTIC, 1.10, 0);
        powder("dimensional_mana:amethyst_dust", Category.VANILLA, Slot.SEMANTIC, 1.00, 0);

        // ---- 魔晶粉 5 级 + 超导：决定「多强、多大规模」 ----
        powder("dimensional_mana:lesser_mana_crystal_dust", Category.MANA_CRYSTAL, Slot.POWER, 1.0, 0);
        powder("dimensional_mana:medium_mana_crystal_dust", Category.MANA_CRYSTAL, Slot.POWER, 1.4, 0);
        powder("dimensional_mana:advanced_mana_crystal_dust", Category.MANA_CRYSTAL, Slot.POWER, 2.0, 3);
        powder("dimensional_mana:supreme_mana_crystal_dust", Category.MANA_CRYSTAL, Slot.POWER, 3.0, 6);
        powder("dimensional_mana:special_mana_crystal_dust", Category.MANA_CRYSTAL, Slot.POWER, 4.5, 9);
        powder("dimensional_mana:superconducting_crystal_dust", Category.MANA_CRYSTAL, Slot.POWER, 5.0, 12);

        // ---- 元素粉 12 种：决定「是什么伤害 / 属性」；木 = 现有的自然元素 ----
        powder("dimensional_mana:mana_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.40, 2);
        powder("dimensional_mana:fire_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.00, 5);
        powder("dimensional_mana:water_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.00, 5);
        powder("dimensional_mana:earth_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.00, 5);
        powder("dimensional_mana:nature_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 0.90, 5);
        powder("dimensional_mana:ice_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.10, 8);
        powder("dimensional_mana:lightning_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.20, 8);
        powder("dimensional_mana:wind_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 0.90, 8);
        powder("dimensional_mana:metal_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.00, 8);
        powder("dimensional_mana:light_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.10, 11);
        powder("dimensional_mana:poison_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 0.90, 11);
        powder("dimensional_mana:sound_element_crystal_dust", Category.ELEMENT, Slot.ELEMENT, 1.00, 11);

        // ---- 染料 16 种：学派 / 修饰语义 ----
        powder("minecraft:white_dye", Category.DYE, Slot.DYE, 1.00, 4);
        powder("minecraft:black_dye", Category.DYE, Slot.DYE, 1.10, 4);
        powder("minecraft:gray_dye", Category.DYE, Slot.DYE, 0.90, 4);
        powder("minecraft:light_gray_dye", Category.DYE, Slot.DYE, 0.90, 4);
        powder("minecraft:brown_dye", Category.DYE, Slot.DYE, 1.00, 10);
        powder("minecraft:red_dye", Category.DYE, Slot.DYE, 1.20, 7);
        powder("minecraft:orange_dye", Category.DYE, Slot.DYE, 1.25, 7);
        powder("minecraft:yellow_dye", Category.DYE, Slot.DYE, 1.00, 7);
        powder("minecraft:lime_dye", Category.DYE, Slot.DYE, 1.10, 10);
        powder("minecraft:green_dye", Category.DYE, Slot.DYE, 1.00, 7);
        powder("minecraft:cyan_dye", Category.DYE, Slot.DYE, 0.95, 10);
        powder("minecraft:light_blue_dye", Category.DYE, Slot.DYE, 1.05, 10);
        powder("minecraft:blue_dye", Category.DYE, Slot.DYE, 1.00, 10);
        powder("minecraft:purple_dye", Category.DYE, Slot.DYE, 1.30, 10);
        powder("minecraft:magenta_dye", Category.DYE, Slot.DYE, 1.10, 10);
        powder("minecraft:pink_dye", Category.DYE, Slot.DYE, 0.95, 10);

        // 元素粉 ↔ 现有元素天赋系统的对应关系：天赋点亮的元素，2 级就能直接用它的粉。
        for (ElementalSlimeVariant variant : ElementalSlimeVariant.values()) {
            ElementType element = variant.element();
            if (element == null) continue;
            Powder powder = get("dimensional_mana:" + variant.powderId());
            if (powder != null) {
                BY_ELEMENT.put(element, powder);
                ELEMENT_BY_ITEM.put(powder.itemId(), element);
            }
        }
    }

    private static void powder(String itemId, Category category, Slot slot, double coefficient, int requiredLevel) {
        Powder powder = new Powder(itemId, category, slot, coefficient, requiredLevel);
        ALL.add(powder);
        BY_ITEM.put(itemId, powder);
        BY_CATEGORY.get(category).add(powder);
    }

    public static List<Powder> all() { return Collections.unmodifiableList(ALL); }

    public static List<Powder> byCategory(Category category) {
        return Collections.unmodifiableList(BY_CATEGORY.get(category));
    }

    public static Powder get(String itemId) { return BY_ITEM.get(itemId); }

    public static Powder of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return BY_ITEM.get(id.toString());
    }

    /** 该元素对应的粉末；奥术等没有元素天赋入口的返回 null。 */
    public static Powder ofElement(ElementType element) { return BY_ELEMENT.get(element); }

    /** 反过来：这份粉末代表哪个元素（蓝图投影上色、试运行粒子用）。 */
    public static ElementType elementOf(String itemId) {
        return itemId == null || itemId.isEmpty() ? null : ELEMENT_BY_ITEM.get(itemId);
    }

    /** 没填粉末时按 1.0 计。 */
    public static double coefficient(String itemId) {
        Powder powder = itemId == null || itemId.isEmpty() ? null : BY_ITEM.get(itemId);
        return powder == null ? 1.0 : powder.coefficient();
    }

    /** 创造模式的「粉末无限」：不扣背包、也放开等级限制；服务端扣粉与界面显示共用这一个判断。 */
    public static boolean creativeFree(Player player) {
        return player != null && player.isCreative() && MagicCircleConfig.CREATIVE_FREE_POWDER.get();
    }

    /**
     * 粉末的短名：在功能块列表 / 蓝图明细里一眼看出这一格放的是什么。
     *
     * <p>元素粉用元素简称（火 / 水 / 冰……），奥术单列；原版粉、魔晶粉、染料按物品 id 给固定缩写。
     * 空槽返回「-」。这里有意不读语言文件：短名要能塞进一两行，太长反而看不清。</p>
     */
    public static String shortName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "-";
        Powder powder = BY_ITEM.get(itemId);
        if (powder == null) return "-";
        ElementType element = elementOf(itemId);
        if (element != null) {
            return switch (element) {
                case FIRE -> "火";
                case WATER -> "水";
                case ICE -> "冰";
                case LIGHTNING -> "雷";
                case WIND -> "风";
                case EARTH -> "土";
                case NATURE -> "木";
                case METAL -> "金";
                case LIGHT -> "光";
                case POISON -> "毒";
                case SOUND -> "音";
            };
        }
        if ("dimensional_mana:mana_element_crystal_dust".equals(itemId)) return "奥术";
        String path = ResourceLocation.parse(itemId).getPath();
        return switch (path) {
            case "redstone" -> "红石";
            case "sugar" -> "糖";
            case "gunpowder" -> "火药";
            case "blaze_powder" -> "烈焰";
            case "glowstone_dust" -> "萤石";
            case "lapis_dust" -> "青金";
            case "amethyst_dust" -> "紫晶";
            case "lesser_mana_crystal_dust" -> "下级";
            case "medium_mana_crystal_dust" -> "中级";
            case "advanced_mana_crystal_dust" -> "高级";
            case "supreme_mana_crystal_dust" -> "极品";
            case "special_mana_crystal_dust" -> "特级";
            case "superconducting_crystal_dust" -> "超导";
            default -> dyeShortName(path);
        };
    }

    /** 16 种染料的短名；不是染料就回「?」。 */
    private static String dyeShortName(String path) {
        return switch (path) {
            case "white_dye" -> "白";
            case "black_dye" -> "黑";
            case "gray_dye" -> "灰";
            case "light_gray_dye" -> "淡灰";
            case "brown_dye" -> "棕";
            case "red_dye" -> "红";
            case "orange_dye" -> "橙";
            case "yellow_dye" -> "黄";
            case "lime_dye" -> "黄绿";
            case "green_dye" -> "绿";
            case "cyan_dye" -> "青";
            case "light_blue_dye" -> "淡蓝";
            case "blue_dye" -> "蓝";
            case "purple_dye" -> "紫";
            case "magenta_dye" -> "品红";
            case "pink_dye" -> "粉";
            default -> "?";
        };
    }

    /** 该粉末在这个玩家身上是否已解锁（绘制台粉末盘用灰显表示）。 */
    public static boolean unlocked(Powder powder, Player player) {
        if (powder == null) return false;
        if (player == null) return true;
        if (creativeFree(player)) return true;
        if (!MagicCircleConfig.POWDER_UNLOCK_BY_LEVEL.get()) return true;
        if ("ALL".equalsIgnoreCase(MagicCircleConfig.UNLOCK_MODE.get())) return true;
        int level = MagicProgression.get(player).magicLevel();
        if (powder.category() == Category.ELEMENT && powder.requiredLevel() > 2) {
            // 天赋点亮的元素提前到 2 级，没点的按「5 / 8 / 11 级分批」解锁。
            for (Map.Entry<ElementType, Powder> entry : BY_ELEMENT.entrySet()) {
                if (!entry.getValue().itemId().equals(powder.itemId())) continue;
                if (ElementTalentData.get(player).isUnlocked(entry.getKey())) return level >= 2;
                break;
            }
        }
        return level >= powder.requiredLevel();
    }

    public static List<Powder> unlocked(Player player) {
        List<Powder> open = new ArrayList<>(ALL.size());
        for (Powder powder : ALL) if (unlocked(powder, player)) open.add(powder);
        return open;
    }

    public static int unlockedCount(Player player) { return unlocked(player).size(); }

    private MagicCirclePowders() {}
}
