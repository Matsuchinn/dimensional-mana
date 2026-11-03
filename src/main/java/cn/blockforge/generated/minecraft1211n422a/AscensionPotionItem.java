package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectCategory;
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

/**
 * 升华药水：凡人躯体的极限，短暂触碰神明的领域。
 *
 * <p>一口喝下十件事：生命跃迁（最大生命 +50%，180 秒）、神圣庇护（120 秒内任何新负面效果直接被拦）、
 * 破灭之力（30 秒全类型伤害 +50%）、生命涌动（30 秒生命恢复 ×4）、死斗意志（15 秒内拒绝死亡）、
 * 抗性提升Ⅳ（120 秒）、生命恢复Ⅱ（30 秒）、伤害吸收Ⅳ（60 秒），
 * 外加喝下瞬间的「净化之躯」；180 秒药效结束后进入 15 秒「药效反噬」。
 * 除了这几条状态，真正的判定都在 {@link AscensionEvents} 里。</p>
 */
public final class AscensionPotionItem extends Item {
    /** 注册名：酿造台、JEI 与创造页共用这一个 id。 */
    public static final String ITEM_ID = "ascension_potion";
    /** 饮用时长：2 秒。 */
    public static final int USE_TICKS = 40;

    public AscensionPotionItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            drink(player);
        }
        if (!(entity instanceof Player player) || !player.getAbilities().instabuild) {
            stack.shrink(1);
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (entity instanceof Player bottleOwner) {
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (!bottleOwner.getInventory().add(bottle)) bottleOwner.drop(bottle, false);
            }
        }
        return stack;
    }

    private static void drink(Player player) {
        // 净化之躯先结算：先把身上的脏东西洗掉，再开始升华，免得刚拿到的庇护被旧负面塞满。
        purify(player);
        float beforeMax = player.getMaxHealth();
        player.addEffect(new MobEffectInstance(GeneratedMod.ASCENSION,
                AscensionEffect.ASCENSION_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(GeneratedMod.ASCENSION_WARD,
                AscensionEffect.WARD_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(GeneratedMod.ASCENSION_MIGHT,
                AscensionEffect.MIGHT_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(GeneratedMod.ASCENSION_REBIRTH,
                AscensionEffect.REBIRTH_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(GeneratedMod.ASCENSION_DEATHWARD,
                AscensionEffect.DEATHWARD_TICKS, 0, false, true, true));
        // 三条原版状态：抗性提升Ⅳ、生命恢复Ⅱ、伤害吸收Ⅳ。
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                AscensionEffect.RESISTANCE_TICKS, AscensionEffect.RESISTANCE_AMPLIFIER, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                AscensionEffect.REGENERATION_TICKS, AscensionEffect.REGENERATION_AMPLIFIER, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                AscensionEffect.ABSORPTION_TICKS, AscensionEffect.ABSORPTION_AMPLIFIER, false, true, true));
        // 生命跃迁立刻生效：把新多出来的那一截血补上，血条不会停在半空。
        float gained = player.getMaxHealth() - beforeMax;
        if (gained > 0.0F) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + gained));
        }
        player.playSound(SoundEvents.TOTEM_USE, 1.0F, 1.25F);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.6F);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(),
                    42, 0.55D, 0.85D, 0.55D, 0.06D);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(),
                    18, 0.45D, 0.7D, 0.45D, 0.12D);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.dimensional_mana.ascension.begins"), false);
        }
    }

    /**
     * 净化之躯：喝下瞬间把本模组留下的永久损伤补回来。
     *
     * <p>具体三件事：洗掉身上所有负面状态（魔石病、魔蚀、药效反噬这些都算）；
     * 把归返之门（反虚空回溯之门）扣掉的魔素上限债务逐笔赎回归还；
     * 最后把被魔石病一点点磨掉的血补满。</p>
     */
    public static void purify(Player player) {
        for (MobEffectInstance instance : List.copyOf(player.getActiveEffects())) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(instance.getEffect());
            }
        }
        MagicErosionEffect.cleanse(player);
        if (player instanceof ServerPlayer serverPlayer) {
            int debts = DeathLawPenalty.debtCount(serverPlayer);
            if (debts > 0) DeathLawPenalty.relieveLawDebts(serverPlayer, debts);
        }
        player.setHealth(player.getMaxHealth());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.flavor")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.liftoff")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.rebirth")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.ward")
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.purify")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.might")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.deathward")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.resistance")
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.regeneration")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.absorption")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.backlash")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.ascension.brew")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
