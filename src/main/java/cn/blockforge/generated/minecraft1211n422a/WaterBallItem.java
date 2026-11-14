package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 水球：水元素史莱姆法术弹体的外观物品（见 {@link ElementalSlimeVariant#projectileIcon()}）。
 *
 * <p>以前水史莱姆吐出来的弹体渲染的是一桶原版水桶，画面上像它把桶丢过来了。现在换成这颗
 * 半透明的水球：颜色和施法的那只史莱姆、以及命中时炸开的蓝色碎屑对得上，一眼就知道是水元素的法术。</p>
 *
 * <p>物品本身没有功能，只作为弹体模型和「次元物语：自然」页里的一件小道具存在。</p>
 */
public final class WaterBallItem extends Item {
    public static final String ITEM_ID = "water_ball";

    public WaterBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.water_ball")
                .withStyle(ChatFormatting.GRAY));
    }
}
