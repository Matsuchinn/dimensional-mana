package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * 把饰品的面板数值复制一份到物品自己的属性数据（{@code attribute_modifiers} 组件）。
 *
 * <p>饰品加成平时由 Curios 直接挂到玩家属性上，进游戏确实生效；但「属性检查」类 mod
 * 多半只读<b>物品</b>的属性数据组件，看不到 Curios 的运行时接口，所以在它们眼里饰品是「没有属性」的。
 * 这里在物品注册时把同一份数值写进组件，外部工具就能读到。</p>
 *
 * <p>不会因此翻倍或走歪：</p>
 * <ul>
 *   <li>组件设成不在提示框里显示，提示框不会多出第二段属性；</li>
 *   <li>装备槽记成「护甲」，而饰品穿不进护甲栏，原版装备系统不会替它再结算一次；</li>
 *   <li>真正生效的仍旧是 Curios（各物品的 {@code getAttributeModifiers}），组件只是给外部看的副本。</li>
 * </ul>
 */
public final class AccessoryAttributeMirror {
    /** 副本记在哪个装备槽组：饰品进不了护甲栏，原版装备系统就不会二次结算这份数值。 */
    private static final EquipmentSlotGroup SLOT = EquipmentSlotGroup.ARMOR;

    /** 一条要复制进物品数据的面板加成。 */
    public record Spec(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) { }

    private AccessoryAttributeMirror() { }

    /**
     * @param properties 物品属性
     * @param ownerId    物品 id，用来给修正起一个稳定的名字
     * @param specs      这件物品提供的面板加成，空清单表示不改动
     * @return 带属性副本的物品属性
     */
    public static Item.Properties apply(Item.Properties properties, String ownerId, List<Spec> specs) {
        if (specs.isEmpty()) return properties;
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
        for (Spec spec : specs) {
            String attribute = spec.attribute().unwrapKey()
                    .map(key -> key.location().getPath()).orElse("attribute");
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
                    ownerId + "/" + attribute);
            entries.add(new ItemAttributeModifiers.Entry(spec.attribute(),
                    new AttributeModifier(id, spec.amount(), spec.operation()), SLOT));
        }
        return properties.component(DataComponents.ATTRIBUTE_MODIFIERS,
                new ItemAttributeModifiers(entries, false));
    }
}
