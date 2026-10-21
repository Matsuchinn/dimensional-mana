package cn.blockforge.generated.minecraft1211n422a;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/** 护符和戒指共用的 Curios 饰品实现。 */
public final class MagicAccessoryItem extends Item implements ICurioItem {
    public enum Kind {
        AMULET("charm"), RING("ring");
        private final String slot;
        Kind(String slot) { this.slot = slot; }
    }

    private final Kind kind;

    public MagicAccessoryItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return kind.slot.equals(slotContext.identifier());
    }

    /** 智慧戒指的 3% 魔素强度使用 Curios 真实属性修正，装备、卸下及状态页显示都会自动同步。 */
    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create(
                ICurioItem.super.getAttributeModifiers(slotContext, id, stack));
        if (kind == Kind.RING && kind.slot.equals(slotContext.identifier())) {
            modifiers.put(SpellAttributes.SPELL_POWER, new AttributeModifier(
                    id, 0.03, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        return modifiers;
    }

    public static int equippedCount(LivingEntity entity, String itemId, String slot) {
        Item item = GeneratedMod.MOD_ITEMS.get(itemId).get();
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> (int)handler.findCurios(stack -> stack.is(item)).stream()
                        .filter(result -> slot.equals(result.slotContext().identifier()))
                        .count())
                .orElse(0);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = kind == Kind.AMULET ? "tooltip.dimensional_mana.magic_amulet.effect"
                : "tooltip.dimensional_mana.wisdom_ring.effect";
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.accessory.stackable").withStyle(ChatFormatting.DARK_GRAY));
    }
}
