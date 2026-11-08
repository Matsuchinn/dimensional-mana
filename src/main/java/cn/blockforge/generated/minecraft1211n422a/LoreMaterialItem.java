package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 合成材料的说明栏：风味描述 + 获取来源 + 用途。
 *
 * <p>净化核心、光辉核心这类只在掉落表里出现的材料，玩家拿到手最想知道的就是
 * 「这玩意哪来的、能做什么」。三条信息各占一行，分别用
 * {@code tooltip.dimensional_mana.<id>.1}、{@code .obtain}、{@code .use} 三个语言键，
 * 这样加新材料时只改语言文件、不用再写一个物品类。</p>
 */
public final class LoreMaterialItem extends Item {
    private final String id;
    private final int descriptionLines;

    public LoreMaterialItem(Properties properties, String id, int descriptionLines) {
        super(properties);
        this.id = id;
        this.descriptionLines = descriptionLines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (int index = 1; index <= descriptionLines; index++) {
            tooltip.add(Component.translatable("tooltip.dimensional_mana." + id + "." + index)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        tooltip.add(Component.translatable("tooltip.dimensional_mana." + id + ".obtain")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana." + id + ".use")
                .withStyle(ChatFormatting.BLUE));
    }
}
