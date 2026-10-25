package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 卢梅莉亚专用方块：比其它四种多一套「贴墙」形态。
 *
 * <p>同一株草药有两种长相和状态：站在地上时是普通的十字模型（{@code wall_mounted=false}），
 * 长在岩壁上时换成贴墙模型，并记下它贴着的是哪一面（{@code facing}）。
 * 玩家往岩壁上放时自动选贴墙形态；随机刻还会顺着墙面往上爬一格，最多三格。</p>
 */
public class LumeliaPlantBlock extends ManaPlantBlock {
    /** 是否为贴墙形态。 */
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    /** 贴墙时朝向哪一面，也就是叶片伸出来的方向。 */
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

    private static final int MAX_WALL_HEIGHT = 3;

    private static final VoxelShape GROUND_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);
    private static final VoxelShape NORTH_SHAPE = Block.box(2.0, 0.0, 13.0, 14.0, 13.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(2.0, 0.0, 0.0, 14.0, 13.0, 3.0);
    private static final VoxelShape WEST_SHAPE = Block.box(13.0, 0.0, 2.0, 16.0, 13.0, 14.0);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 0.0, 2.0, 3.0, 13.0, 14.0);

    public LumeliaPlantBlock(ManaPlants.Kind kind, BlockBehaviour.Properties properties) {
        super(kind, properties);
        registerDefaultState(defaultBlockState().setValue(WALL_MOUNTED, false).setValue(FACING, Direction.NORTH));
    }

    public LumeliaPlantBlock(BlockBehaviour.Properties properties) {
        this(ManaPlants.Kind.LUMELIA, properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return simpleCodec(LumeliaPlantBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WALL_MOUNTED, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(WALL_MOUNTED)) return GROUND_SHAPE;
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> GROUND_SHAPE;
        };
    }

    /** 贴墙时看背后的墙，站在地上时照旧看脚下的土。 */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(WALL_MOUNTED)) {
            Direction facing = state.getValue(FACING);
            BlockPos support = pos.relative(facing.getOpposite());
            return level.getBlockState(support).isFaceSturdy(level, support, facing);
        }
        return super.canSurvive(state, level, pos);
    }

    /** 背后的墙被挖掉，贴墙的那株立刻消失，不会浮在空中。 */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WALL_MOUNTED) && direction == state.getValue(FACING).getOpposite()) {
            return canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** 玩家往岩壁上放：脚下放不了、背后有实心墙，就自动贴墙。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (ManaPlants.canPlantAt(level, pos)) return defaultBlockState();
        Direction wall = ManaPlants.wallFacing(level, pos);
        if (wall == null) return defaultBlockState();
        return defaultBlockState().setValue(WALL_MOUNTED, true).setValue(FACING, wall);
    }

    /** 贴墙的植株会继续向上爬；地面上的照旧由父类往高处长。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WALL_MOUNTED)) {
            super.randomTick(state, level, pos, random);
            return;
        }
        if (random.nextFloat() > 0.35F) return;
        int height = 1;
        while (height < MAX_WALL_HEIGHT && level.getBlockState(pos.below(height)).getBlock() == this) height++;
        if (height >= MAX_WALL_HEIGHT) return;
        BlockPos above = pos.above();
        if (!level.getBlockState(above).canBeReplaced()) return;
        if (!canSurvive(state, level, above)) return;
        level.setBlock(above, state, Block.UPDATE_CLIENTS);
    }
}
