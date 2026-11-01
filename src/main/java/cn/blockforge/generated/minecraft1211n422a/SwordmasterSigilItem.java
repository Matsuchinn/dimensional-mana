package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 【剑士徽记】：流浪剑士死亡时必掉的纪念品。
 *
 * <p>它没有合成或战斗用途，纯粹是「这个村庄里那位剑士被你打倒了」的实物凭证；
 * 放进「次元物语：斗气」创造页，和长剑、刷怪蛋摆在一起。</p>
 */
public final class SwordmasterSigilItem extends Item {
    public static final String ITEM_ID = "swordmaster_sigil";

    public SwordmasterSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordmaster_sigil.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordmaster_sigil.2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
