package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AppraisalOrbItem extends Item {
    public AppraisalOrbItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ManaData.Snapshot d = ManaData.get(player);
            player.displayClientMessage(Component.literal("◆ 魔素鉴定 ◆").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            player.displayClientMessage(Component.literal(String.format("魔素：%.1f / %.1f", d.mana(), d.maxMana())).withStyle(ChatFormatting.AQUA), false);
            player.displayClientMessage(Component.literal(String.format("亲和：%.3f",
                    d.affinity() * MagicEquipment.affinityMultiplier(player))).withStyle(ChatFormatting.BLUE), false);
            player.displayClientMessage(Component.literal("斗气：" + d.battleQi() + " / " + d.maxBattleQi()).withStyle(ChatFormatting.GOLD), false);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
