package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

import java.util.List;

/**
 * 魔导调试棒：绘制台的「钥匙」，也是看阵盘状态、试运行的操作工具。
 *
 * <p>第 1 阶段已经能用的是：<b>右键</b>任意基座把连通的整片基座注册成阵盘（潜行右键 =
 * 按现在的形状重新注册），右键已注册的基座免费预览启动 / 维持 / 触发消耗、强度、
 * 复杂度与缺失项。基座只有 3 像素厚，视角稍微偏一点就会点到旁边的方块，所以这里
 * 还做了三处兜底：点到基座旁边、站在基座上右键空气、以及指令。</p>
 */
public final class MagicCircleWandItem extends Item {
    public static final String ITEM_ID = "arcane_debug_wand";
    /** 点到基座旁边多近，就当作是想点那块基座。 */
    private static final int NEAR_BASE_RADIUS = 2;
    /** 再远一点只提醒，不擅自替玩家注册。 */
    private static final int HINT_BASE_RADIUS = 8;

    public MagicCircleWandItem(Properties properties) {
        super(properties);
    }

    /**
     * 原版规矩：潜行 + 右键手持物品时，方块自己的 {@code useItemOn} 会被跳过（这样才方便拿着方块
     * 贴箱子放）。调试棒是纯工具，不需要这条规矩——返回 true 让潜行右键也能正常走到基座方块，
     * 「潜行右键 = 重新注册」才真的能用。
     */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }

    /**
     * 右键方块。真正的注册走基座方块的 {@code useItemOn}，这里负责兜底：
     * 方块侧没吃下这次点击、或者玩家差一点点没点中 3 像素厚的基座板。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        if (player == null) return InteractionResult.PASS;
        // 投影锚点：右键 = 免费预览，Shift + 右键 = 半价试运行（第 3 阶段）。
        if (level.getBlockState(clicked).getBlock() instanceof MagicCircleProjectionBlock) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                if (player.isShiftKeyDown()) {
                    MagicCircleProjectionEvents.testRun(serverLevel, clicked, serverPlayer);
                } else {
                    MagicCircleProjectionEvents.preview(serverLevel, clicked, serverPlayer);
                }
            }
            return InteractionResult.SUCCESS;
        }
        // 客户端与服务器用同一套判断，客户端只在「确实要处理」时才吃掉这次点击，避免两边不同步。
        boolean onBase = MagicCircleNetworks.isBase(level, clicked)
                || MagicCircleNetworks.nearestBase(level, clicked, NEAR_BASE_RADIUS) != null;
        if (level.isClientSide) return onBase ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (MagicCircleNetworks.isBase(serverLevel, clicked)) {
            MagicCircleNetworks.handleWandUse(serverLevel, clicked, serverPlayer, player.isShiftKeyDown());
            return InteractionResult.SUCCESS;
        }
        BlockPos near = MagicCircleNetworks.nearestBase(serverLevel, clicked, NEAR_BASE_RADIUS);
        if (near != null) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.near_base").withStyle(ChatFormatting.GRAY), false);
            MagicCircleNetworks.handleWandUse(serverLevel, near, serverPlayer, player.isShiftKeyDown());
            return InteractionResult.SUCCESS;
        }
        if (MagicCircleNetworks.nearestBase(serverLevel, clicked, HINT_BASE_RADIUS) != null) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.aim_hint").withStyle(ChatFormatting.YELLOW), false);
        }
        return InteractionResult.PASS;
    }

    /**
     * 右键空气：站在基座上（3 像素厚的板没有碰撞箱，脚下这一格就是基座）时也能注册；
     * <b>潜行</b>右键空气（脚下没有基座）则打开远程法阵轮盘，站着不动就能开关附近的阵。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
            BlockPos base = MagicCircleNetworks.baseUnderPlayer(serverLevel, serverPlayer);
            if (base != null) {
                MagicCircleNetworks.handleWandUse(serverLevel, base, serverPlayer, player.isShiftKeyDown());
            } else if (player.isShiftKeyDown()) {
                // 轮盘也走客户端的开法：服务端只负责清单（客户端会自己发 remote_refresh）。
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.remote.opening").withStyle(ChatFormatting.GRAY), false);
            } else {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.wand_hint").withStyle(ChatFormatting.GRAY), false);
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.status.powders",
                        MagicCirclePowders.unlockedCount(serverPlayer), MagicCirclePowders.all().size())
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            }
        } else if (level.isClientSide && player.isShiftKeyDown()
                && MagicCircleNetworks.baseUnderPlayer(level, player) == null) {
            ClientMagicCircleScreens.openRemoteWheel();
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dimensional_mana.arcane_debug_wand.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.arcane_debug_wand.2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.arcane_debug_wand.3")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.arcane_debug_wand.4")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.arcane_debug_wand.5")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
