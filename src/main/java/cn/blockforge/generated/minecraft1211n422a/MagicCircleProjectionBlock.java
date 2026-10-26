package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 法阵投影锚点：把一份蓝图「贴」在方块表面（地面、墙面、天花板都行）。
 *
 * <p>它没有碰撞箱、只有 1 像素厚的底板，真正画出来的法阵由
 * {@code MagicCircleProjectionRenderer} 在客户端画（光点 + 连线 + 呼吸脉冲）。
 * 同一个锚点可以叠最多 {@link MagicCircleConfig#MAX_LAYERS} 层蓝图，
 * 挖掉时会把里面的蓝图退还给玩家。</p>
 *
 * <p>第 4 阶段两种锚点共用这一个方块，靠 {@link #SEALED} 区分：</p>
 * <ul>
 *   <li>{@code sealed = false}：蓝图投影锚点，只能免费预览 + 半价试运行；</li>
 *   <li>{@code sealed = true}：刻印石板贴出来的实体锚点，能正式启动、按时扣维持、触发真实效果。</li>
 * </ul>
 */
public final class MagicCircleProjectionBlock extends BaseEntityBlock {
    /** 方块注册 id；右键交互那边要靠它从 MOD_BLOCKS 里取方块。 */
    public static final String BLOCK_ID = "magic_circle_projection";
    public static final MapCodec<MagicCircleProjectionBlock> CODEC = simpleCodec(MagicCircleProjectionBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** true = 刻印石板贴出来的实体锚点；false = 蓝图投影。只影响模型与能不能正式运行。 */
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
    /** 一层 1 像素厚的底板，贴在「表面」那一侧。 */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0));
        SHAPES.put(Direction.DOWN, Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0));
        SHAPES.put(Direction.NORTH, Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0));
        SHAPES.put(Direction.SOUTH, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0));
        SHAPES.put(Direction.WEST, Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0));
        SHAPES.put(Direction.EAST, Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0));
    }

    public MagicCircleProjectionBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(SEALED, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEALED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.UP));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagicCircleProjectionBlockEntity(pos, state);
    }

    /**
     * 正式阵要自己跑：计时触发的倒计时、红石沿、维持成本都靠这个 ticker。
     * 没刻印的投影锚点进来会立刻 return，不浪费 tick。
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == GeneratedMod.MAGIC_CIRCLE_PROJECTION_BLOCK_ENTITY.get()
                ? (tickLevel, pos, tickState, blockEntity) -> {
                    if (blockEntity instanceof MagicCircleProjectionBlockEntity anchor
                            && tickLevel instanceof ServerLevel serverLevel) {
                        MagicCircleRites.tickAnchor(serverLevel, pos, tickState, anchor);
                    }
                }
                : null;
    }

    /** 红石触发：贴着红石线的刻印阵，信号一到就自己打一次。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                  BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) return;
        if (!entity.running() || !MagicCircleRites.usesRedstone(entity)) return;
        if (serverLevel.hasNeighborSignal(pos)) {
            MagicCircleRites.triggerBySignal(serverLevel, pos, entity);
        }
    }

    /**
     * 手持魔导调试棒右键：不潜行 = 免费预览，潜行 = 试运行（投影锚点）或启动 / 停止（刻印锚点）。
     *
     * <p>放在方块这一侧而不是调试棒的 {@code useOn} 里，是因为方块自己的 {@code useItemOn}
     * 先跑：它一旦处理了这次点击，调试棒的 {@code useOn} 根本不会被调用。</p>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(GeneratedMod.arcaneDebugWandItem())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                MagicCircleProjectionEvents.testRun(serverLevel, pos, serverPlayer);
            } else {
                MagicCircleProjectionEvents.preview(serverLevel, pos, serverPlayer);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 空手右键：刻印阵运行中 = 触发一次；否则报一下这个锚点的层数、块数、画布与实际消耗。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)
                || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!(serverLevel.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) {
            return InteractionResult.SUCCESS;
        }
        if (entity.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.anchor_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return InteractionResult.SUCCESS;
        }
        if (entity.sealed() && entity.running()) {
            // 正式阵运行中：空手右键就是「打一发」。
            MagicCircleRites.trigger(serverLevel, pos, entity, serverPlayer, false);
            return InteractionResult.SUCCESS;
        }
        serverPlayer.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.anchor_info",
                entity.layerCount(), entity.maxLayers(),
                entity.baseW() * MagicCircleNetwork.TILE, entity.baseH() * MagicCircleNetwork.TILE,
                entity.evaluate().nodeCount()).withStyle(ChatFormatting.AQUA), false);
        serverPlayer.displayClientMessage(Component.translatable(
                entity.sealed()
                        ? "message.dimensional_mana.magiccircle.slate.hint_off"
                        : "message.dimensional_mana.magiccircle.blueprint.test_hint")
                .withStyle(ChatFormatting.GRAY), false);
        return InteractionResult.SUCCESS;
    }

    /** 挖掉锚点：里面每一层都退成物品（刻印石板退回刻印石板），连线两边也一起断掉。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity) {
            boolean sealed = entity.sealed();
            List<MagicCircleBlueprint> layers = List.copyOf(entity.layers());
            for (MagicCircleBlueprint blueprint : layers) {
                ItemStack drop = sealed
                        ? EtchedSlateItem.stackFor(blueprint)
                        : MagicCircleBlueprints.stackFor(blueprint);
                if (!drop.isEmpty()) Block.popResource(serverLevel, pos, drop);
            }
            MagicCircleRites.unlinkAll(serverLevel, pos, entity);
            entity.clearLayers();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
