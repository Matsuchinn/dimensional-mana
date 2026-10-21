package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 带自发光亮度的普通材料物品：提示里会写出亮度，物品栏里也按这个亮度发光。 */
public final class GlowingItem extends Item {
    public GlowingItem(Properties properties) {
        super(properties);
    }

    /** 这个物品堆的亮度等级。 */
    public static int luminance(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? 0 : ItemGlow.of(id.getPath());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int luminance = luminance(stack);
        if (luminance > 0) {
            tooltip.add(Component.translatable("tooltip.dimensional_mana.luminance", luminance)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
