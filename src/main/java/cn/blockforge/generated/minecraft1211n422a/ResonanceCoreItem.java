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
 * 魔力通透·共鸣核心：魔法饰品（护符槽）。
 *
 * <p>魔素强度 +100、法术暴击率 +15% 走 Curios 真实属性修正；魔素亲和 ×2.0 在回魔计算里结算，
 * 充能与元素共鸣由 {@link ResonanceCore} 负责。</p>
 *
 * <p>名字是红色加粗，其中“共鸣”两个字单独上色；品质用自定义名字表达“传说”，
 * 因此物品本身的 rarity 组件保持 COMMON，避免原版的品质颜色覆盖掉红色粗体。</p>
 */
public final class ResonanceCoreItem extends Item implements ICurioItem {
    public ResonanceCoreItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return ResonanceCore.SLOT.equals(slotContext.identifier());
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create(
                ICurioItem.super.getAttributeModifiers(slotContext, id, stack));
        if (!ResonanceCore.SLOT.equals(slotContext.identifier())) return modifiers;
        modifiers.put(SpellAttributes.SPELL_POWER,
                new AttributeModifier(id, 100.0, AttributeModifier.Operation.ADD_VALUE));
        modifiers.put(SpellAttributes.SPELL_CRIT_CHANCE,
                new AttributeModifier(id, 0.15, AttributeModifier.Operation.ADD_VALUE));
        return modifiers;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.empty()
                .append(Component.translatable("item.dimensional_mana.resonance_core.name.prefix"))
                .append(Component.translatable("item.dimensional_mana.resonance_core.name.left")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.translatable("item.dimensional_mana.resonance_core.name.right")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("item.dimensional_mana.resonance_core.name.suffix"))
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.quality")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.slot")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.luminance", ItemGlow.RESONANCE_CORE)
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.stats")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.charge")
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.charge_cap")
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.decay")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.resonance")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.resonance_detail")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.resonance_core.transparency")
                .withStyle(ChatFormatting.AQUA));
    }
}
