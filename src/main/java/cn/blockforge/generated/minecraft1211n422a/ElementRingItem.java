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
 * 元素结晶戒指：十二种元素各一枚，同一副外观、宝石颜色随元素变化。
 *
 * <p>效果不占用通用属性，而是在 {@link ElementReactionSystem} 里按施法者的元素逐次计算：
 * 对应元素法术伤害 +14%、附着时长 +14%、元素反应伤害 +7%，多枚同元素戒指完全叠加。</p>
 */
public final class ElementRingItem extends Item implements ICurioItem {
    private static final int ARCANE_COLOR = 0xFFC85BFF;

    private final ElementType element;
    private final String elementId;

    public ElementRingItem(Properties properties, ElementType element) {
        super(properties);
        this.element = element;
        this.elementId = element == null ? MagicEquipment.ARCANE : element.id();
        CuriosApi.registerCurio(this, this);
    }

    public ElementType element() { return element; }
    public String elementId() { return elementId; }
    /** 宝石层染色用的颜色；客户端只染第二层，金属指环保持原色。 */
    public int tintColor() { return element == null ? ARCANE_COLOR : element.color(); }

    public Component elementName() {
        return element == null ? Component.translatable("gui.dimensional_mana.medium.arcane")
                : Component.translatable(element.translationKey());
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return MagicEquipment.RING_SLOT.equals(slotContext.identifier());
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.dimensional_mana.element_ring.name", elementName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_ring.damage", elementName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_ring.attachment", elementName())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_ring.reaction", elementName())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.accessory.stackable")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
