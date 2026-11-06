package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 武备强化石：会发光的锻造材料，物品提示里写清它在武备加工台里的用法。 */
public final class ArmamentStoneItem extends Item {
    public ArmamentStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.armament_enhancement_stone")
                .withStyle(ChatFormatting.GRAY));
    }
}
