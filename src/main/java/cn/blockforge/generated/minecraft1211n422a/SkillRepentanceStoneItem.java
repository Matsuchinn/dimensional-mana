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

public final class SkillRepentanceStoneItem extends Item {
    private static final int USE_TICKS = 60;
    private static final double MANA_COST = 200.0;

    public SkillRepentanceStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && ManaData.get(player).mana() < SpellAttributes.reducedManaCost(player, MANA_COST)) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.repentance.no_mana")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && !level.isClientSide) {
            if (MagicProgression.get(player).spentSkillPoints() <= 0) {
                player.displayClientMessage(Component.translatable("message.dimensional_mana.repentance.empty")
                        .withStyle(ChatFormatting.GRAY), true);
                return stack;
            }
            if (!ManaData.spendMana(player, MANA_COST)) return stack;
            if (MagicProgression.resetSkills(player)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.displayClientMessage(Component.translatable("message.dimensional_mana.repentance.success")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                MagicProgression.awardAdvancement(player, "repentance");
                ProgressSyncPayload.send(player);
            }
        }
        return stack;
    }
}
