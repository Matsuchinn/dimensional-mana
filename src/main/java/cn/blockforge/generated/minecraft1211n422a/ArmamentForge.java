package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 武备加工台的数值核心：升级消耗、魔晶面额、溢出找零、洗练材料判定。
 *
 * <p>全部集中在「没有世界、没有玩家」的纯计算里，菜单、界面和注释都能直接调用，
 * 以后要调价或者加新面额也只需要改这一处。</p>
 */
public final class ArmamentForge {
    /** 武备等级上限，和词条系统的主词条上限保持一致。 */
    public static final int MAX_LEVEL = EquipmentAffixSystem.MAIN_MAX_LEVEL;

    /** 一种材料：物品 id + 它能顶多少魔素。 */
    public record Denomination(String itemId, int value) { }

    /** 可以放进材料格的五种完整魔晶，按面额从大到小。 */
    private static final List<Denomination> CRYSTALS = List.of(
            new Denomination("special_mana_crystal", 12_000),
            new Denomination("supreme_mana_crystal", 4_000),
            new Denomination("advanced_mana_crystal", 1_600),
            new Denomination("medium_mana_crystal", 400),
            new Denomination("lesser_mana_crystal", 100));

    /**
     * 找零面额，从大到小：极品 4000 → 高级 1600 → 中级 400 → 下级 100 → 下级粉 25。
     * 只返还完整结晶和下级粉，最后不足 25 的零头抹掉（和需求里的算例一致）。
     */
    private static final List<Denomination> CHANGE = List.of(
            new Denomination("supreme_mana_crystal", 4_000),
            new Denomination("advanced_mana_crystal", 1_600),
            new Denomination("medium_mana_crystal", 400),
            new Denomination("lesser_mana_crystal", 100),
            new Denomination("lesser_mana_crystal_dust", 25));

    /** 洗练材料：超导魔晶，一颗重置一次。 */
    private static final String REROLL_MATERIAL = "superconducting_crystal";

    /** 副词条强化材料：武备强化石，一颗顶一次副词条成长。 */
    public static final String ENHANCE_STONE = "armament_enhancement_stone";

    /** 用锻造模板赋予出来的武备，每次敲击升级多花 50% 魔素。 */
    public static final double TEMPLATE_SURCHARGE = 1.5D;

    private ArmamentForge() { }

    /** 升到 {@code nextLevel} 级需要的魔素：2^N + 100N（1→2 是 204，11→12 是 5296）。 */
    public static int upgradeCost(int nextLevel) {
        int level = Math.max(1, Math.min(MAX_LEVEL, nextLevel));
        return (1 << level) + 100 * level;
    }

    /**
     * 按装备算升级价：普通武备就是 {@link #upgradeCost(int)}，
     * 锻造模板打造出来的武备（{@link EquipmentAffixSystem#isTemplateForged}）再加 50%，向上取整。
     */
    public static int upgradeCost(ItemStack equipment, int nextLevel) {
        int base = upgradeCost(nextLevel);
        if (!EquipmentAffixSystem.isTemplateForged(equipment)) return base;
        return (int) Math.ceil(base * TEMPLATE_SURCHARGE);
    }

    /** 是不是武备强化石。 */
    public static boolean isEnhanceStone(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.is(GeneratedMod.MOD_ITEMS.get(ENHANCE_STONE).get());
    }

    /**
     * 是不是锻造模板：原版 {@code minecraft:trim_templates} 标签里的全部模板，
     * 再加上本模组的魔精钢锻造模板（它不在原版标签里）。注册名以 {@code _smithing_template} 兜底，
     * 这样别的模组加的模板也能用。
     */
    public static boolean isSmithingTemplate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(net.minecraft.tags.ItemTags.TRIM_TEMPLATES)) return true;
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().endsWith("_smithing_template");
    }

    /** 这堆材料是哪种魔晶；不是魔晶返回 {@code null}。 */
    public static Denomination crystalOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Denomination crystal : CRYSTALS) {
            if (stack.is(GeneratedMod.MOD_ITEMS.get(crystal.itemId()).get())) return crystal;
        }
        return null;
    }

    /** 这一颗魔晶顶多少魔素；不是魔晶返回 0。 */
    public static int crystalValue(ItemStack stack) {
        Denomination crystal = crystalOf(stack);
        return crystal == null ? 0 : crystal.value();
    }

    /** 是不是超导魔晶（洗练用）。 */
    public static boolean isRerollMaterial(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.is(GeneratedMod.MOD_ITEMS.get(REROLL_MATERIAL).get());
    }

    /** 材料格只收五种魔晶、超导魔晶、武备强化石和锻造模板；魔晶粉、其它杂物一律不收。 */
    public static boolean acceptsMaterial(ItemStack stack) {
        return crystalValue(stack) > 0 || isRerollMaterial(stack) || isEnhanceStone(stack) || isSmithingTemplate(stack);
    }

    /**
     * 溢出找零：超出的魔素按 50% 向下取整折算，再按面额从大到小换成物品。
     *
     * <p>算例：特级（12000）敲 1→2（204），溢出 11796，打五折 5898，
     * 返还极品 ×1、高级 ×1、下级 ×2、下级粉 ×3，剩下 23 点抹零。</p>
     *
     * @param overflow 材料面额减去消耗后的差额（小于 0 按 0 处理）
     * @return 每一份都是数量 1 的物品栈，直接塞背包即可
     */
    public static List<ItemStack> changeFor(int overflow) {
        int budget = Math.max(0, overflow) / 2;
        List<ItemStack> result = new ArrayList<>();
        for (Denomination denomination : CHANGE) {
            while (budget >= denomination.value()) {
                budget -= denomination.value();
                result.add(new ItemStack(GeneratedMod.MOD_ITEMS.get(denomination.itemId()).get()));
            }
        }
        return result;
    }

    /** 找零总价值（已经打过五折），界面里给玩家看个大概。 */
    public static int refundValue(int overflow) {
        return Math.max(0, overflow) / 2;
    }
}
