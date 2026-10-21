package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 空白法术卷轴：冒险者卖的小玩意，右键把“正在研读的法术”往前推一次，
 * 也就是把一次研读心得抄到纸上（等于一次研读成功）。
 *
 * <p>研读进度仍然是原版的 3 次成功；没有正在研读的法术时卷轴不会被消耗。</p>
 */
public final class BlankSpellScrollItem extends Item {
    public BlankSpellScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.sidedSuccess(stack, true);

        SpellData.Snapshot snapshot = SpellData.get(serverPlayer);
        SpellDefinition learning = snapshot.learning();
        if (learning == null || snapshot.learned(learning)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.blank_scroll_no_target"), true);
            return InteractionResultHolder.fail(stack);
        }
        int progress = SpellData.recordLearningSuccess(serverPlayer, learning);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.blank_scroll_used",
                Component.translatable(learning.translationKey()), progress, SpellData.REQUIRED_SUCCESSES), false);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.9F, 1.15F);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.blank_spell_scroll")
                .withStyle(ChatFormatting.GRAY));
    }
}
