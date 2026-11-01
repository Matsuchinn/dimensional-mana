package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 【久经风霜的剑士长剑】：流浪剑士的专属武器。
 *
 * <p>数值按设计稿：攻击力 12、攻击距离 4 格（原版铁剑的 2 倍）、攻速为原版铁剑的 0.8 倍。
 * 攻速由武器的 ATTACK_SPEED 修饰符表达（铁剑 −2.4 → 这里 −1.92）；攻击距离在 1.21.1 是
 * {@link Attributes#ENTITY_INTERACTION_RANGE} 属性，铁剑默认 3 格，这里 +1 正好 4 格。</p>
 *
 * <p>自带附魔【耐久 III】【锋利 V】。附魔是数据驱动的注册表内容，创建物品的静态时刻拿不到
 * 注册表，所以由 {@link #inventoryTick} 在服务端补一次；已经在铁砧上被玩家改过附魔的剑不会被
 * 强行改回去（只补到至少 3 / 5 级）。</p>
 *
 * <p><b>招架：</b>r263 起这把剑没有任何自己的一套格挡。它只是一把普通的剑 ——
 * 归属 {@code #minecraft:swords} 物品标签，于是模组自带的「斗气招架」
 * （{@link BattleQiParry}：主手剑、副手空、长按右键 5 刻进姿态、姿态下按左键弹反）
 * 对它和对原版铁剑完全一样地生效。以前那套「举剑当盾」的格挡已经删掉，
 * 玩家用它右键不会再举盾，也不会再走另一套减伤/耐久算法。</p>
 *
 * <p>剑士手里的那把不用这个 ItemStack 做渲染：他的剑是 GeckoLib 模型的一部分，见
 * {@code wandering_swordmaster.geo.json}。物品只用于掉落与玩家拾取。</p>
 */
public final class SwordmasterWeaponItem extends SwordItem {
    public static final String ITEM_ID = "weathered_swordmaster_blade";
    /** 设计稿：攻击力 12（玩家基础 1 点 + 武器 11 点；铁质本身带 2 点，所以这里填 9）。 */
    public static final float ATTACK_DAMAGE = 12.0F;
    /** 设计稿：攻速是铁剑的 0.8 倍。铁剑实际攻速 1.6，0.8 倍 = 1.28，对应修饰符 1.28 − 4.0 = −2.72。 */
    public static final float ATTACK_SPEED = -2.72F;
    /** 设计稿：4 格攻击距离。玩家默认交互距离是 3 格，这里补 1。 */
    public static final double RANGE_BONUS = 1.0D;
    public static final int UNBREAKING_LEVEL = 3;
    public static final int SHARPNESS_LEVEL = 5;

    private static final ResourceLocation RANGE_ID = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "swordmaster_blade_range");

    public SwordmasterWeaponItem(Properties properties) {
        super(Tiers.IRON, properties);
    }

    /** 物品属性的唯一出口：注册时和创造模式物品都走这里，避免两边数值漂移。 */
    public static Properties bladeProperties() {
        return new Item.Properties()
                .rarity(Rarity.EPIC)
                .attributes(SwordItem.createAttributes(Tiers.IRON, 9.0F, ATTACK_SPEED)
                        .withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                                new AttributeModifier(RANGE_ID, RANGE_BONUS,
                                        AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND));
    }

    /** 掉落用的那把：耐久砍掉一半，附魔拉满。 */
    public static ItemStack weatheredBlade(Level level) {
        ItemStack stack = new ItemStack(GeneratedMod.MOD_ITEMS.get(ITEM_ID).get());
        stack.setDamageValue(stack.getMaxDamage() / 2);
        applyEnchantments(stack, level);
        return stack;
    }

    /** 给剑补上【耐久 III】【锋利 V】；已有一模一样或更高等级的附魔时不动。 */
    public static void applyEnchantments(ItemStack stack, Level level) {
        if (level == null || level.isClientSide) return;
        var registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> unbreaking = registry.getHolderOrThrow(Enchantments.UNBREAKING);
        Holder<Enchantment> sharpness = registry.getHolderOrThrow(Enchantments.SHARPNESS);
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.getLevel(unbreaking) >= UNBREAKING_LEVEL
                && current.getLevel(sharpness) >= SHARPNESS_LEVEL) return;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        if (mutable.getLevel(unbreaking) < UNBREAKING_LEVEL) mutable.set(unbreaking, UNBREAKING_LEVEL);
        if (mutable.getLevel(sharpness) < SHARPNESS_LEVEL) mutable.set(sharpness, SHARPNESS_LEVEL);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        // 物品刚被拿到手上时补附魔；只在服务端做，避免客户端反复改组件。
        applyEnchantments(stack, level);
    }

    public static boolean isBlade(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordmasterWeaponItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordmaster_blade.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordmaster_blade.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordmaster_blade.3")
                .withStyle(ChatFormatting.DARK_GRAY));
        // r263：不再有单独的「举剑当盾」，右键走的是模组自带的斗气招架。
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordmaster_blade.block")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordmaster_blade.parry")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
