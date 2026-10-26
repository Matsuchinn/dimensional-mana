package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 魔法阵绘制台基座：一块 3 像素高的石质薄板，可以并排拼成任意矩形阵面。
 *
 * <p>它本身没有碰撞箱，玩家可以直接站上去画；真正的阵图存在
 * {@link MagicCircleNetworks} 里。手持魔导调试棒右键它：没注册就把连通的一整片
 * 注册成阵盘，注册过就打开 {@link MagicCircleDrawingScreen 绘制台界面}；
 * 潜行 + 右键 = 按现在的形状重新注册（形状没变时保留阵图）。</p>
 */
public final class MagicCircleBaseBlock extends BaseEntityBlock {
    public static final MapCodec<MagicCircleBaseBlock> CODEC = simpleCodec(MagicCircleBaseBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

    public MagicCircleBaseBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagicCircleBaseBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(GeneratedMod.arcaneDebugWandItem())) {
            if (!stack.isEmpty() && !(stack.getItem() instanceof net.minecraft.world.item.BlockItem)) {
                // 手里拿的不是调试棒：方块类物品照常放置；其它物品（粉末、刻印石胚……）点了没反应，
                // 这里补一句提示，免得玩家以为是「注册坏了」。跳过后面的方块默认交互，避免提示两遍。
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(Component.translatable(
                            "message.dimensional_mana.magiccircle.empty_hand_hint")
                            .withStyle(ChatFormatting.GRAY), false);
                }
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
            // 没注册过就直接注册（不用先潜行），注册过就报状态；潜行 = 按现在的形状重新注册。
            MagicCircleNetworks.handleWandUse(serverLevel, pos, serverPlayer, player.isShiftKeyDown());
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 空手右键：告诉玩家该拿什么工具来注册，省得干瞪眼。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.empty_hand_hint")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return InteractionResult.SUCCESS;
    }

    /** 拆掉任何一块基座就让整个阵盘失效，避免留下指向空气的锚点。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos) instanceof MagicCircleBaseBlockEntity entity) {
            String networkId = entity.networkId();
            if (!networkId.isEmpty()) {
                entity.setNetworkId("");
                MagicCircleNetworks.invalidate(serverLevel, networkId);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
