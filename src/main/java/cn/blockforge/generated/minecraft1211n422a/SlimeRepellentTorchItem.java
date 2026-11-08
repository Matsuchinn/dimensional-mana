package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 史莱姆驱逐火把的方块物品：只为了把作用范围写在提示框里。
 *
 * <p>说明栏和实际数值都来自 {@link SlimeRepellentData} 的常量，改范围时两边一起变。</p>
 */
public final class SlimeRepellentTorchItem extends BlockItem {
    public SlimeRepellentTorchItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.slime_repellent_torch.1")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.slime_repellent_torch.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.slime_repellent_torch.3")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.slime_repellent_torch.4")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
