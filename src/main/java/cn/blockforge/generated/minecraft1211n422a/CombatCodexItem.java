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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 战斗手册：和《万法知识之书》一起在首次进入世界时发放的物理系手册。
 *
 * <p>右键打开三页界面：基础战斗信息（实时读玩家属性）、斗气与技能加点、能力一览。
 * 外观是暗红皮革封面加交叉双剑徽记，和蓝色魔法阵的万法知识之书成对。</p>
 */
public final class CombatCodexItem extends Item {
    public static final String ITEM_ID = "combat_codex";

    public CombatCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, player.blockPosition(), GeneratedMod.CODEX_PAGE_TURN.get(), SoundSource.PLAYERS,
                    0.8F, 0.82F + level.random.nextFloat() * 0.08F);
            PacketDistributor.sendToPlayer(serverPlayer, new OpenCombatCodexPayload(true));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.combat_codex.1").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.combat_codex.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.combat_codex.3").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 玩家背包或饰品栏里有没有战斗手册。 */
    public static boolean hasAccess(Player player) {
        if (player == null) return false;
        return player.getInventory().contains(stack -> stack.getItem() instanceof CombatCodexItem);
    }
}
