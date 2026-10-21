package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ManaPotionItem extends Item {
    private final double percentage;
    private final int flat;
    private final int useTicks;
    private final boolean dangerous;

    public ManaPotionItem(Properties properties, double percentage, int flat, int useTicks, boolean dangerous) {
        super(properties);
        this.percentage = percentage;
        this.flat = flat;
        this.useTicks = useTicks;
        this.dangerous = dangerous;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            ManaData.Snapshot d = ManaData.get(entity);
            double amount = d.maxMana() * percentage + flat;
            ManaData.restoreMana(entity, amount, true);
            if (dangerous) entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 3600, 0));
            entity.playSound(SoundEvents.GENERIC_DRINK, 0.8F, 1.15F);
            if (!(entity instanceof Player player) || !player.getAbilities().instabuild) {
                stack.shrink(1);
                if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
                if (entity instanceof Player player) {
                    ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                    if (!player.getInventory().add(bottle)) player.drop(bottle, false);
                }
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (dangerous) {
            tooltip.add(Component.translatable("tooltip.dimensional_mana.pure_liquid.restore").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.pure_liquid.wither").withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.pure_liquid.warning").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.dimensional_mana.potion.restore", percent(percentage), flat)
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.potion.overcharge").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.dimensional_mana.potion.drink_time",
                        String.format(java.util.Locale.ROOT, "%.1f", useTicks / 20.0))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 百分比先格式化成字符串再交给翻译：语言文件只认 %s，写 %.2f 会让整行退回未替换的原文。 */
    private static String percent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", ratio * 100.0);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return useTicks;
    }
}
