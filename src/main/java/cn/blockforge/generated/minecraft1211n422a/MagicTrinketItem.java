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

/** 古老宝匣与高等魔法刻蚀共用的魔法饰品实现。 */
public final class MagicTrinketItem extends Item implements ICurioItem {
    public static final String MAGIC_SLOT = "magic_accessory";
    private static final ResourceLocation CASKET_SLOT_BONUS = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "ancient_casket_slot_bonus");

    public enum Kind { ANCIENT_CASKET, GREATER_MAGIC_ETCHING }

    private final Kind kind;

    public MagicTrinketItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (kind == Kind.GREATER_MAGIC_ETCHING) return MAGIC_SLOT.equals(slotContext.identifier());
        if (!MAGIC_SLOT.equals(slotContext.identifier()) && !"curio".equals(slotContext.identifier())) return false;
        // 验证已装备的原槽时允许它自己通过；尝试在另一个栏位放第二件时则拒绝。
        boolean alreadyInThisSlot = CuriosApi.getCuriosInventory(slotContext.entity())
                .flatMap(handler -> handler.findCurio(slotContext.identifier(), slotContext.index()))
                .map(result -> result.stack().is(this)).orElse(false);
        return alreadyInThisSlot || !isEquipped(slotContext.entity(), "ancient_casket");
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create(
                ICurioItem.super.getAttributeModifiers(slotContext, id, stack));
        if (kind == Kind.ANCIENT_CASKET) {
            CuriosApi.addSlotModifier(modifiers, MAGIC_SLOT, CASKET_SLOT_BONUS, 1.0,
                    AttributeModifier.Operation.ADD_VALUE);
        }
        return modifiers;
    }

    public static boolean isEquipped(LivingEntity entity, String itemId) {
        Item item = GeneratedMod.MOD_ITEMS.get(itemId).get();
        return CuriosApi.getCuriosInventory(entity).map(handler -> handler.isEquipped(item)).orElse(false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (kind == Kind.ANCIENT_CASKET) {
            tooltip.add(Component.translatable("tooltip.dimensional_mana.ancient_casket.capacity").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.ancient_casket.slot").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.ancient_casket.unique").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.dimensional_mana.greater_magic_etching.damage").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.greater_magic_etching.cost").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.greater_magic_etching.true_damage").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.greater_magic_etching.unhealable").withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.greater_magic_etching.nonstacking").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
