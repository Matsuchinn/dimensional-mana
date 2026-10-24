package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * 斗气技能书：右键永久解锁一项斗气能力。
 *
 * <p>三本书分别是拔刀斩、瞬步、剑气附加。学会以后书会消耗掉，能力自动装进第一个空槽；
 * 已经会了的话书不会被吃掉，只提示「已经掌握」。获取途径独立于魔法技能：
 * 完美格挡后击杀破绽中的生物有 5% 概率掉一本随机的。</p>
 */
public final class QiSkillBookItem extends Item {
    private final QiProgression.Ability ability;

    public QiSkillBookItem(Properties properties, QiProgression.Ability ability) {
        super(properties);
        this.ability = ability;
    }

    public QiProgression.Ability ability() { return ability; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (QiProgression.learn(serverPlayer, ability)) {
                level.playSound(null, player.blockPosition(), GeneratedMod.CODEX_PAGE_TURN.get(), SoundSource.PLAYERS,
                        0.9F, 1.05F + level.random.nextFloat() * 0.1F);
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.qi_learned", Component.translatable(ability.translation()),
                        Component.keybind("key.dimensional_mana.qi_cast")), false);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            } else {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.qi_already_learned",
                        Component.translatable(ability.translation())), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.qi_manual.header").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.qi_manual." + ability.id() + ".1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.qi_manual." + ability.id() + ".2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.qi_manual.cost", ability.cost())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.qi_manual.learn").withStyle(ChatFormatting.DARK_GRAY));
    }
}
