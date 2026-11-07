package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔晶砂轮的全部计算：两种剥离方向的产物、原版砂轮的经验返还、以及词条回收的材料。
 *
 * <p>这里刻意不碰世界、不碰玩家，只吃两个物品栈吐结果，所以菜单、方块实体、
 * 界面提示和自测清单都能直接调同一份实现，不会出现「界面说有、实际不给」的分叉。</p>
 *
 * <p>两个模式的区别只有一句话：</p>
 * <ul>
 *   <li><b>模式 A（仅去除附魔）</b>：评级、主词条、副词条全部留着，只把非诅咒附魔洗掉，
 *       经验按原版砂轮那一套算：Σ 附魔最低造价 → ÷2 向上取整 → 再加 0~i 的随机。</li>
 *   <li><b>模式 B（仅去词条与评级）</b>：附魔和耐久一点不动，把整套词条（含评级）抹掉，
 *       按评级和满级词条返还材料。</li>
 * </ul>
 *
 * <p>两个模式都<strong>不写</strong>{@code minecraft:repair_cost}（铁砧累积惩罚），
 * 这是需求里明确要求保留的：砂轮只剥东西，不帮玩家洗惩罚。
 * 唯一会碰这个组件的地方是「两件同类装备合并修复」——那是原版砂轮的修复功能，
 * 这里也是把两个输入里较大的惩罚原样搬到成品上，同样不算重置。</p>
 */
public final class ManaGrindstoneLogic {
    /** 模式 A：只去附魔。 */
    public static final int MODE_ENCHANTMENTS = 0;
    /** 模式 B：只去词条与评级。 */
    public static final int MODE_AFFIXES = 1;

    /** 评级 → 返还的武备强化石粉数量；不在表里的评级（C / B / A）不返还。 */
    private static final String GRADE_S = "S";
    private static final String GRADE_SS = "SS";
    private static final String GRADE_SSS = "SSS";

    private ManaGrindstoneLogic() { }

    /** 模式取值兜底：NBT 里读到脏数据也不会让界面和逻辑对不上。 */
    public static int clampMode(int mode) {
        return mode == MODE_AFFIXES ? MODE_AFFIXES : MODE_ENCHANTMENTS;
    }

    /** 另一个模式（切换按钮用）。 */
    public static int otherMode(int mode) {
        return clampMode(mode) == MODE_AFFIXES ? MODE_ENCHANTMENTS : MODE_AFFIXES;
    }

    /** 这一格能不能放进魔晶砂轮：可损伤 / 带附魔 / 可砂轮修复 / 带武备词条。 */
    public static boolean canAccept(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(stack)
                || stack.canGrindstoneRepair() || EquipmentAffixSystem.hasAffix(stack);
    }

    /**
     * 算成品。
     *
     * <p>两个槽都有东西时走原版砂轮的「同类装备合并修复」：耐久相加再送 5%% 上限、
     * 附魔取高等级合并、非诅咒附魔全部洗掉；带了武备词条的装备合并后保留左边那件的词条。</p>
     *
     * <p>只有一个槽有东西时按当前模式剥离：A 只洗附魔，B 只洗词条。
     * 没有可剥的东西就返回空栈，按钮和成品槽都空着，玩家不会白拿。</p>
     */
    public static ItemStack compute(ItemStack top, ItemStack bottom, int mode) {
        if (top.isEmpty() && bottom.isEmpty()) return ItemStack.EMPTY;
        // 原版砂轮一次只处理一件，堆叠的物品不给磨，避免一次吞掉一整组。
        if (top.getCount() > 1 || bottom.getCount() > 1) return ItemStack.EMPTY;
        if (!top.isEmpty() && !bottom.isEmpty()) return merge(top, bottom);
        ItemStack single = top.isEmpty() ? bottom : top;
        return clampMode(mode) == MODE_AFFIXES ? stripAffixesOnly(single) : stripEnchantsOnly(single);
    }

    /** 这件装备是不是还有「可剥的非诅咒附魔」。 */
    public static boolean hasRemovableEnchant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (!holder.is(EnchantmentTags.CURSE)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 模式 A：只去附魔
    // ------------------------------------------------------------------

    /**
     * 只洗非诅咒附魔：评级、主副词条、耐久上限、铁砧惩罚全部原样保留。
     *
     * <p>附魔书洗空之后按原版行为变回一本普通书；魔精钢装备的「附魔 +1」账本
     * 也跟着清（{@link MojiangSteelEvents#clearBoostIfUnenchanted}），
     * 否则重新附魔就再也 +1 不了了——r342 刚修过的那个坑。</p>
     */
    private static ItemStack stripEnchantsOnly(ItemStack stack) {
        if (!hasRemovableEnchant(stack)) return ItemStack.EMPTY;
        ItemStack output = stack.copyWithCount(1);
        ItemEnchantments remaining = EnchantmentHelper.updateEnchantments(output,
                mutable -> mutable.removeIf(holder -> !holder.is(EnchantmentTags.CURSE)));
        if (output.is(Items.ENCHANTED_BOOK) && remaining.isEmpty()) {
            output = new ItemStack(Items.BOOK, output.getCount());
        }
        MojiangSteelEvents.clearBoostIfUnenchanted(output);
        return output;
    }

    // ------------------------------------------------------------------
    // 模式 B：只去词条与评级
    // ------------------------------------------------------------------

    /**
     * 只抹词条与评级：附魔一条不动，耐久只把「评级加成」那部分还原
     * （{@link EquipmentAffixSystem#stripAffixes} 会把 MAX_DAMAGE 退回原版上限），
     * 已经掉的那点耐久原样留着。
     */
    private static ItemStack stripAffixesOnly(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !EquipmentAffixSystem.hasAffix(stack)) return ItemStack.EMPTY;
        ItemStack output = stack.copyWithCount(1);
        EquipmentAffixSystem.stripAffixes(output);
        return output;
    }

    /**
     * 模式 B 的材料返还清单（每一份都是数量 1 的物品栈）。
     *
     * <ul>
     *   <li>评级 S / SS / SSS → 1 / 2 / 3 份武备强化石粉，C / B / A 不返还；</li>
     *   <li>主词条满级（Lv.12）→ 额外 1 份特级魔晶粉；</li>
     *   <li>每一条满级（+4）副词条 → 额外 1 份极品魔晶粉。</li>
     * </ul>
     *
     * <p>要在成品被算出来之前调用（那时词条还在），菜单里是先取清单再发货。</p>
     */
    public static List<ItemStack> refunds(ItemStack stack) {
        List<ItemStack> result = new ArrayList<>();
        if (stack == null || stack.isEmpty() || !EquipmentAffixSystem.hasAffix(stack)) return result;
        EquipmentAffixSystem.View view = EquipmentAffixSystem.view(stack);
        if (view.empty()) return result;
        int stones = switch (view.gradeName() == null ? "" : view.gradeName()) {
            case GRADE_S -> 1;
            case GRADE_SS -> 2;
            case GRADE_SSS -> 3;
            default -> 0;
        };
        for (int i = 0; i < stones; i++) {
            ItemStack stone = itemOf(ArmamentForge.ENHANCE_STONE_DUST);
            if (!stone.isEmpty()) result.add(stone);
        }
        if (view.mainLevel() >= EquipmentAffixSystem.MAIN_MAX_LEVEL) {
            ItemStack dust = itemOf("special_mana_crystal_dust");
            if (!dust.isEmpty()) result.add(dust);
        }
        for (EquipmentAffixSystem.AffixLine line : view.subs()) {
            if (line.level() < EquipmentAffixSystem.SUB_MAX_LEVEL) continue;
            ItemStack dust = itemOf("supreme_mana_crystal_dust");
            if (!dust.isEmpty()) result.add(dust);
        }
        return result;
    }

    /** 返还材料的物品；注册表里没有就返回空栈（老存档 / 卸载模块时不至于崩）。 */
    private static ItemStack itemOf(String id) {
        var holder = GeneratedMod.MOD_ITEMS.get(id);
        return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get());
    }

    // ------------------------------------------------------------------
    // 原版砂轮的经验与「合并修复」
    // ------------------------------------------------------------------

    /**
     * 原版砂轮的返还经验：两槽里非诅咒附魔的最低造价之和 → 折半向上取整 → 再加 0~i 的随机。
     * 完全没有附魔时给 0，不会凭空刷经验。
     */
    public static int experience(ItemStack top, ItemStack bottom, RandomSource random) {
        int total = enchantmentCost(top) + enchantmentCost(bottom);
        if (total <= 0) return 0;
        int half = (int)Math.ceil(total / 2.0D);
        return half + random.nextInt(half);
    }

    /** 一件物品上非诅咒附魔的「最低造价」之和，就是原版砂轮的算经验方式。 */
    private static int enchantmentCost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int total = 0;
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(EnchantmentTags.CURSE)) continue;
            total += holder.value().getMinCost(entry.getIntValue());
        }
        return total;
    }

    /**
     * 原版砂轮的「两件同类装备合并修复」：耐久相加再送 5%% 上限、附魔合并取高、非诅咒附魔洗掉。
     *
     * <p>和原版唯一的差别有两处，都是本模组的既有设定：</p>
     * <ul>
     *   <li>合并以「带词条的那件」为基底（两件都有词条时取上面那件），所以白板那件只贡献耐久，
     *       不会把一件 S 级武备合成白板；</li>
     *   <li>带评级的武备按自己的（已修正）耐久上限算，不会因为跟另一件合并就把上限拉成两者最大值；</li>
     *   <li>铁砧累积惩罚取两个输入里较大的那个原样带过去，不吃砂轮白洗。</li>
     * </ul>
     */
    private static ItemStack merge(ItemStack left, ItemStack right) {
        if (!left.is(right.getItem())) return ItemStack.EMPTY;
        // 白板放上面、武备放下面时，照样以武备为基底，评级别被合并吃掉。
        if (!EquipmentAffixSystem.hasAffix(left) && EquipmentAffixSystem.hasAffix(right)) {
            ItemStack swap = left;
            left = right;
            right = swap;
        }
        boolean affixed = EquipmentAffixSystem.hasAffix(left) || EquipmentAffixSystem.hasAffix(right);
        int maxDamage = affixed ? left.getMaxDamage() : Math.max(left.getMaxDamage(), right.getMaxDamage());
        int leftRemaining = left.getMaxDamage() - left.getDamageValue();
        int rightRemaining = right.getMaxDamage() - right.getDamageValue();
        int repaired = leftRemaining + rightRemaining + maxDamage * 5 / 100;
        int count = 1;
        if (!left.isDamageableItem() || !left.isRepairable()) {
            if (left.getMaxStackSize() < 2 || !ItemStack.matches(left, right)) return ItemStack.EMPTY;
            count = 2;
        }
        ItemStack output = left.copyWithCount(count);
        if (output.isDamageableItem()) {
            output.set(DataComponents.MAX_DAMAGE, maxDamage);
            output.setDamageValue(Math.max(maxDamage - repaired, 0));
            if (!right.isRepairable()) output.setDamageValue(left.getDamageValue());
        }
        mergeEnchants(output, right);
        return removeNonCurses(output, Math.max(penalty(left), penalty(right)));
    }

    private static void mergeEnchants(ItemStack target, ItemStack source) {
        EnchantmentHelper.updateEnchantments(target, mutable -> {
            for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(source).entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                if (!holder.is(EnchantmentTags.CURSE) || mutable.getLevel(holder) == 0) {
                    mutable.upgrade(holder, entry.getIntValue());
                }
            }
        });
    }

    /**
     * 洗掉非诅咒附魔，并把铁砧累积惩罚原样写回（{@code repairCost} 为 0 时直接去掉组件）。
     * 附魔书洗空后变回普通书，和原版一致。
     */
    private static ItemStack removeNonCurses(ItemStack stack, int repairCost) {
        ItemEnchantments remaining = EnchantmentHelper.updateEnchantments(stack,
                mutable -> mutable.removeIf(holder -> !holder.is(EnchantmentTags.CURSE)));
        ItemStack output = stack;
        if (output.is(Items.ENCHANTED_BOOK) && remaining.isEmpty()) {
            output = new ItemStack(Items.BOOK, output.getCount());
        }
        if (repairCost <= 0) output.remove(DataComponents.REPAIR_COST);
        else output.set(DataComponents.REPAIR_COST, repairCost);
        MojiangSteelEvents.clearBoostIfUnenchanted(output);
        return output;
    }

    private static int penalty(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return Math.max(0, stack.getOrDefault(DataComponents.REPAIR_COST, 0));
    }
}
