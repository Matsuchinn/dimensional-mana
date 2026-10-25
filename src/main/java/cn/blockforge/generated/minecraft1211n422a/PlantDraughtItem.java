package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 植物药剂：回复药剂与完全回复药剂。
 *
 * <p>两瓶都是“喝 2 秒、瞬间回血、顺手洗掉几个负面状态”。完全回复药剂多一条：
 * 回血溢出的部分不会浪费，会变成不会自己消失的伤害吸收金心。</p>
 */
public final class PlantDraughtItem extends Item {
    /** 溢出转化的吸收上限，与 MAX_ABSORPTION 属性的上限保持一致。 */
    private static final float MAX_ABSORPTION = 2048.0F;
    private static final ResourceLocation ABSORPTION_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "draught_overflow_absorption");

    private final double healRatio;
    private final int debuffs;
    private final boolean overflowToAbsorption;

    public PlantDraughtItem(Properties properties, double healRatio, int debuffs, boolean overflowToAbsorption) {
        super(properties);
        this.healRatio = healRatio;
        this.debuffs = debuffs;
        this.overflowToAbsorption = overflowToAbsorption;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide) return stack;
        heal(entity);
        for (int i = 0; i < debuffs; i++) {
            if (!clearOneDebuff(entity)) break;
        }
        entity.playSound(SoundEvents.GENERIC_DRINK, 0.9F, 1.1F);
        entity.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
        if (!(entity instanceof Player player) || !player.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (stack.isEmpty()) return bottle;
            if (entity instanceof Player player) {
                if (!player.getInventory().add(bottle)) player.drop(bottle, false);
            }
        }
        return stack;
    }

    private void heal(LivingEntity entity) {
        float max = entity.getMaxHealth();
        float missing = Math.max(0.0F, max - entity.getHealth());
        float amount = max * (float) healRatio;
        entity.heal(amount);
        if (!overflowToAbsorption) return;
        float overflow = amount - missing;
        if (overflow <= 0.0F) return;
        grantAbsorption(entity, overflow);
    }

    /**
     * 把溢出的治疗量变成伤害吸收金心。
     *
     * <p>1.21 起 {@code setAbsorptionAmount} 会被 MAX_ABSORPTION 属性夹住，而生物默认的
     * 该属性是 0——直接设置等于没加。所以先给属性挂一个“临时上限修饰符”，
     * 上限刚好等于这次要给的吸收量，再设吸收值；修饰符不写进存档，玩家换维度、
     * 重进游戏都不会残留，但吸收值本身会被打掉，用掉多少就少多少。</p>
     */
    private static void grantAbsorption(LivingEntity entity, float overflow) {
        float total = Math.min(entity.getAbsorptionAmount() + overflow, MAX_ABSORPTION);
        AttributeInstance cap = entity.getAttribute(Attributes.MAX_ABSORPTION);
        if (cap != null) {
            cap.addOrUpdateTransientModifier(new AttributeModifier(ABSORPTION_MODIFIER,
                    total, AttributeModifier.Operation.ADD_VALUE));
        }
        entity.setAbsorptionAmount(total);
    }

    /** 随机洗掉一个负面状态；没有负面状态就返回 false。 */
    private static boolean clearOneDebuff(LivingEntity entity) {
        List<Holder<MobEffect>> harmful = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) harmful.add(instance.getEffect());
        }
        if (harmful.isEmpty()) return false;
        Holder<MobEffect> picked = harmful.get(entity.getRandom().nextInt(harmful.size()));
        entity.removeEffect(picked);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dimensional_mana.draught.heal", percent(healRatio))
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.draught.debuff", debuffs)
                .withStyle(ChatFormatting.GREEN));
        if (overflowToAbsorption) {
            tooltip.add(Component.translatable("tooltip.dimensional_mana.draught.absorption")
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.dimensional_mana.potion.drink_time", "2.0")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String percent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 40;
    }
}
