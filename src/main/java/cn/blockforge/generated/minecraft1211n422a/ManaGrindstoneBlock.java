package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 魔晶砂轮：建立在原版砂轮那套功能之上的进阶工作方块（洗附魔 / 洗词条 / 合并修复）。
 *
 * <p>造型沿用原版砂轮的三段式：两条蚀纹木腿夹一根转轴，中间一颗魔晶砂盘。
 * 砂轮盘是竖着立起来的，所以有<b>水平朝向</b>：放下去时盘面朝向你，
 * 摆成一排也整齐。模型只有腿、轴、盘三块占位，所以方块属性里必须带
 * {@code noOcclusion()}，否则游戏会把它当实心立方体，把四周贴着它的方块面全剔掉，
 * 看起来就是砂轮旁边的地面“变透明”了。</p>
 *
 * <p>和原版砂轮最大的差别是「剥离方向可选」——打开界面按「模式切换」就能在
 * <b>只去附魔</b>和<b>只去词条与评级</b>之间来回切，具体规则见
 * {@link ManaGrindstoneLogic} 与界面上的说明。</p>
 */
public final class ManaGrindstoneBlock extends BaseEntityBlock {
    public static final MapCodec<ManaGrindstoneBlock> CODEC = simpleCodec(ManaGrindstoneBlock::new);
    public static final String BLOCK_ID = "mana_grindstone";
    /** 水平朝向：砂轮盘面朝玩家，放下去就看得见正反面。 */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ManaGrindstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                              Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ManaGrindstoneBlockEntity grindstone) {
            serverPlayer.openMenu(grindstone, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ManaGrindstoneBlockEntity grindstone) {
            // 只吐两个输入格：成品槽里那份是「算出来的预览」，跟着掉出来就等于白送一件。
            for (int slot : new int[]{ManaGrindstoneBlockEntity.INPUT_TOP, ManaGrindstoneBlockEntity.INPUT_BOTTOM}) {
                net.minecraft.world.level.block.Block.popResource(level, pos, grindstone.getItem(slot));
            }
            grindstone.clearContent();
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaGrindstoneBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, GeneratedMod.MANA_GRINDSTONE_BLOCK_ENTITY.get(),
                ManaGrindstoneBlockEntity::serverTick);
    }
}
