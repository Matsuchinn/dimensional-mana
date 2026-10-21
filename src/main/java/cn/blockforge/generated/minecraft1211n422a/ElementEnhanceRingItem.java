package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 元素强化戒指：奥术元素结晶戒指 + 超导魔晶在原版锻造台上升级而来。
 *
 * <p>和单元素戒指不同，这枚戒指不认元素：所有属性的法术伤害、附着时长、元素反应伤害
 * 都按 +18% 结算，乘区由 {@link MagicEquipment} 统一提供，因此十二种元素与奥术全都吃满。</p>
 */
public final class ElementEnhanceRingItem extends Item implements ICurioItem {
    public ElementEnhanceRingItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return MagicEquipment.RING_SLOT.equals(slotContext.identifier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_enhance_ring.line1")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_enhance_ring.line2")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_enhance_ring.line3")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.accessory.stackable")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
