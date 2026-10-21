package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class ElementResonanceStoneItem extends Item {
    private static final int USE_TICKS = 100;
    private static final double MANA_COST = 500.0;

    public ElementResonanceStoneItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (!ElementTalentData.get(player).chosen()) {
                player.displayClientMessage(Component.translatable("message.dimensional_mana.resonance.no_choice")
                        .withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            if (ManaData.get(player).mana() < SpellAttributes.reducedManaCost(player, MANA_COST)) {
                player.displayClientMessage(Component.translatable("message.dimensional_mana.resonance.no_mana")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return USE_TICKS; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player
                && ElementTalentData.get(player).chosen()
                && ManaData.get(player).mana() >= SpellAttributes.reducedManaCost(player, MANA_COST)) {
            ManaData.spendMana(player, MANA_COST);
            int refunded = ElementTalentData.reset(player);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.resonance.reset", refunded), false);
        }
        return stack;
    }
}
