package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class ManaDetectorItem extends Item {
    private static final int ANALYSIS_TICKS = 60;
    private static final double MANA_COST = 10.0;

    public ManaDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && ManaData.get(player).mana() < MANA_COST) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.detector.no_mana")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return ANALYSIS_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && level instanceof ServerLevel serverLevel) {
            if (!ManaData.spendMana(player, MANA_COST)) return stack;
            BlockPos pos = player.blockPosition();
            double concentration = ManaConcentration.at(serverLevel, pos);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.detector.concentration",
                    String.format("%.2f", concentration)).withStyle(ChatFormatting.AQUA), false);
            ManaCaveData.get(serverLevel).nearest(pos).ifPresentOrElse(cave -> {
                int distance = (int)Math.round(Math.sqrt(cave.distSqr(pos)));
                player.displayClientMessage(Component.translatable("message.dimensional_mana.detector.cave", distance)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }, () -> player.displayClientMessage(Component.translatable("message.dimensional_mana.detector.no_cave")
                    .withStyle(ChatFormatting.GRAY), false));
            player.getCooldowns().addCooldown(this, 20);
        }
        return stack;
    }
}
