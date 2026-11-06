package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 合成方格修装备（4.2 的后半段）。
 *
 * <p>玩家在合成格里放两件<b>完全相同的可损坏装备</b>，就能把耐久拼回去（耐久相加，
 * 额外补 5% 上限）。代价按规范执行：</p>
 * <ul>
 *   <li>累积惩罚重置为 {@code 0}（砂轮那条路是 {@code 2^(诅咒数-1)}，两条路不一样）；</li>
 *   <li>所有非诅咒附魔丢失，诅咒附魔留下；</li>
 *   <li>所有词条丢失（包括词条前缀名和传说光效）。</li>
 * </ul>
 */
public final class EquipmentRecombineRecipe extends CustomRecipe {
    /** 合成修复额外补的耐久，按上限的 5%。 */
    private static final float BONUS_RATIO = 0.05F;

    public EquipmentRecombineRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pair(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int[] pair = pair(input);
        if (pair == null) return ItemStack.EMPTY;
        ItemStack first = input.getItem(pair[0]);
        ItemStack second = input.getItem(pair[1]);
        ItemStack output = first.copyWithCount(1);
        int max = output.getMaxDamage();
        int damage = first.getDamageValue() + second.getDamageValue() - Math.round(max * BONUS_RATIO);
        output.setDamageValue(Math.max(0, Math.min(max - 1, damage)));
        output.remove(DataComponents.REPAIR_COST);
        EquipmentAffixSystem.stripAffixes(output);
        EquipmentAffixSystem.stripNonCurseEnchantments(output);
        return output;
    }

    /** 找出“恰好两件、同类、同一件物品、都可损坏”的两个格子。 */
    private static int[] pair(CraftingInput input) {
        int first = -1;
        int second = -1;
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).isEmpty()) continue;
            if (first < 0) {
                first = i;
            } else if (second < 0) {
                second = i;
            } else {
                return null;   // 超过两件不是这套配方
            }
        }
        if (first < 0 || second < 0) return null;
        ItemStack a = input.getItem(first);
        ItemStack b = input.getItem(second);
        if (!a.isDamageableItem() || !b.isDamageableItem()) return null;
        if (a.getCount() != 1 || b.getCount() != 1) return null;
        if (!ItemStack.isSameItemSameComponents(a, b)) return null;
        return new int[]{first, second};
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return GeneratedMod.EQUIPMENT_RECOMBINE_SERIALIZER.get();
    }
}
