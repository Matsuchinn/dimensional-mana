package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 粗糙小圆盾：举盾时仍有 10% 的概率被强力攻击破防。
 * 破防的判定写在 {@link RoughGearEvents#onShieldBlock}。
 */
public final class RoughBucklerItem extends ShieldItem {
    public RoughBucklerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.IRON_INGOT) || super.isValidRepairItem(stack, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rough_gear").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rough_buckler").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rough_repair").withStyle(ChatFormatting.DARK_GREEN));
    }
}
