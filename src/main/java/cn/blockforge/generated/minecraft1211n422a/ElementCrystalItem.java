package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 元素结晶：十二种元素（含奥术）各一枚，颜色和外形由此处的元素决定。 */
public final class ElementCrystalItem extends Item {
    private static final int ARCANE_COLOR = 0xFFC85BFF;

    private final ElementType element;
    private final String elementId;

    public ElementCrystalItem(Properties properties, ElementType element) {
        super(properties);
        this.element = element;
        this.elementId = element == null ? MagicEquipment.ARCANE : element.id();
    }

    public ElementType element() { return element; }
    public String elementId() { return elementId; }
    public int color() { return element == null ? ARCANE_COLOR : element.color(); }

    public Component elementName() {
        return element == null ? Component.translatable("gui.dimensional_mana.medium.arcane")
                : Component.translatable(element.translationKey());
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.dimensional_mana.element_crystal.name", elementName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_crystal.use", elementName())
                .withStyle(ChatFormatting.GRAY));
    }
}
