package cn.blockforge.generated.minecraft1211n422a;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 魔素增强护符：护符槽装备。强度、暴击率走真实属性；
 * 每秒回魔与“暴击回魔”在 {@link MagicEquipment} 与 {@link SpellAttributes} 里结算。
 */
public final class ManaEnhanceAmuletItem extends Item implements ICurioItem {
    public ManaEnhanceAmuletItem(Properties properties) {
        super(AccessoryAttributeMirror.apply(properties, MagicEquipment.MANA_AMULET, specs()));
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return MagicEquipment.AMULET_SLOT.equals(slotContext.identifier());
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create(
                ICurioItem.super.getAttributeModifiers(slotContext, id, stack));
        if (!MagicEquipment.AMULET_SLOT.equals(slotContext.identifier())) return modifiers;
        for (AccessoryAttributeMirror.Spec spec : specs()) {
            modifiers.put(spec.attribute(), new AttributeModifier(id, spec.amount(), spec.operation()));
        }
        return modifiers;
    }

    /** 魔素强度 +64、法术暴击率 +8%：Curios 生效的数值与复制进物品数据的数值共用这一份。 */
    private static List<AccessoryAttributeMirror.Spec> specs() {
        return List.of(
                new AccessoryAttributeMirror.Spec(SpellAttributes.SPELL_POWER, 64.0,
                        AttributeModifier.Operation.ADD_VALUE),
                new AccessoryAttributeMirror.Spec(SpellAttributes.SPELL_CRIT_CHANCE, 0.08,
                        AttributeModifier.Operation.ADD_VALUE));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.mana_enhance_amulet.line1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.mana_enhance_amulet.line2")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.mana_enhance_amulet.line3")
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.mana_enhance_amulet.line4")
                .withStyle(ChatFormatting.GOLD));
    }
}
