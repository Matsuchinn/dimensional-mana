package cn.blockforge.generated.minecraft1211n422a;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * 魔法蜡烛的手持形态：方块物品照旧，说明栏直接把「休眠吸魔、点燃散魔」写在脸上，
 * 免得玩家在基地插了一堆没点的蜡烛、发现回蓝变慢后来报 Bug。
 */
public final class MagicCandleItem extends net.minecraft.world.item.BlockItem {
    public MagicCandleItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dimensional_mana.magic_candle.mode")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.magic_candle.range")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
