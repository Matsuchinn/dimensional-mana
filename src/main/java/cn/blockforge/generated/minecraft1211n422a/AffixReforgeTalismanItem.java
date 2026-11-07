package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 词条重铸符：武备加工台的第三种材料。
 *
 * <p>和超导魔晶（洗练，可能掉品级）不一样，它只重抽主词条与副词条的内容，
 * 装备当前是几级就还是几级，副词条条数与等级区间也跟着品级走。
 * 物品本身没有右键功能，用法写在提示里，避免玩家拿着它到处乱点。</p>
 */
public final class AffixReforgeTalismanItem extends Item {
    public AffixReforgeTalismanItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.affix_reforge_talisman")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.affix_reforge_talisman.recipe")
                .withStyle(ChatFormatting.GRAY));
    }
}
