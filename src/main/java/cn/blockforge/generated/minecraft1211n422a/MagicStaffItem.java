package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;

/** 可手持的通用法杖；双持时只采用加成更高的一把，避免属性重复叠加。 */
public final class MagicStaffItem extends Item {
    private static final ResourceLocation SPELL_POWER_MODIFIER = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "held_staff_spell_power");

    public record StaffStats(double spellDamage, double spellPower, double spellHaste,
                             double manaCostReduction, double manaRegeneration) {
        private static final StaffStats NONE = new StaffStats(0, 0, 0, 0, 0);
    }

    private final StaffStats stats;

    public MagicStaffItem(Properties properties, StaffStats stats) {
        super(properties);
        this.stats = stats;
    }

    public static ItemAttributeModifiers meleeAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 1.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public static StaffStats heldStats(LivingEntity entity) {
        StaffStats main = stats(entity.getMainHandItem());
        StaffStats off = stats(entity.getOffhandItem());
        return main.spellDamage() >= off.spellDamage() ? main : off;
    }

    /** 将当前生效法杖的魔素强度写入真实同步属性；双持时仍只采用品质更高的一把。 */
    public static void syncSpellPowerAttribute(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        AttributeInstance instance = entity.getAttribute(SpellAttributes.SPELL_POWER);
        if (instance == null) return;
        double amount = heldStats(entity).spellPower();
        AttributeModifier current = instance.getModifier(SPELL_POWER_MODIFIER);
        if (amount <= 0.0) {
            if (current != null) instance.removeModifier(SPELL_POWER_MODIFIER);
            return;
        }
        if (current != null && current.amount() == amount
                && current.operation() == AttributeModifier.Operation.ADD_VALUE) return;
        instance.addOrUpdateTransientModifier(new AttributeModifier(
                SPELL_POWER_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static StaffStats stats(ItemStack stack) {
        return stack.getItem() instanceof MagicStaffItem staff ? staff.stats : StaffStats.NONE;
    }

    /** 成功施法后消耗实际提供加成的法杖 1 点耐久。 */
    public static void damageHeldStaff(LivingEntity caster) {
        ItemStack main = caster.getMainHandItem();
        ItemStack off = caster.getOffhandItem();
        ItemStack chosen = stats(main).spellDamage() >= stats(off).spellDamage() ? main : off;
        if (!(chosen.getItem() instanceof MagicStaffItem) || !(caster.level() instanceof net.minecraft.server.level.ServerLevel)) return;
        EquipmentSlot slot = chosen == main ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        chosen.hurtAndBreak(1, caster, slot);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.staff.spell_damage", format(stats.spellDamage())).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.staff.spell_power", format(stats.spellPower())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.staff.spell_haste", percent(stats.spellHaste())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.staff.mana_cost", percent(stats.manaCostReduction())).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.staff.mana_regeneration", percent(stats.manaRegeneration())).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.staff.non_stacking").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f", value);
    }

    private static String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", value * 100.0);
    }
}
