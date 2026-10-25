package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.joml.Vector3f;

/**
 * 维斯珀德索恩（暮刺荆棘）：黄昏开花，其余时间闭花。
 *
 * <p>两种形态各有一套模型和贴图：闭花时是一丛暗紫荆棘，黄昏时换成开满紫花的模型，
 * 掉落也跟着变——只有开花状态才会额外掉一朵「暮刺花」。状态由游戏时间决定，
 * 每 5 秒校准一次（黄昏只有三分钟），区块刚生成时先按闭花放，随机刻会自己纠正。</p>
 */
public class VesperThornBlock extends ManaPlantBlock {
    /** 是否正在开花。 */
    public static final BooleanProperty BLOOMING = BooleanProperty.create("blooming");
    /** 开合状态多久校准一次：5 秒，足够跟上三分钟的黄昏。 */
    private static final int CHECK_INTERVAL = 100;

    public VesperThornBlock(BlockBehaviour.Properties properties) {
        super(ManaPlants.Kind.VESPER_THORN, properties);
        registerDefaultState(defaultBlockState().setValue(BLOOMING, false));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return simpleCodec(VesperThornBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BLOOMING);
    }

    /** 玩家放下去的那一刻就按当前时间摆好样子，并开始校准。 */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(BLOOMING, ManaPlants.isDusk(context.getLevel()));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) sync(server, pos, state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        sync(level, pos, state);
    }

    /** 计划刻到点：重新判断开没开花，再把下一次校准排上。 */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        sync(level, pos, level.getBlockState(pos));
    }

    /** 开花时花瓣周围飘一点紫光，隔着几格也看得出这丛现在是开着的。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BLOOMING) || random.nextInt(6) != 0) return;
        level.addParticle(new DustParticleOptions(new Vector3f(0.78F, 0.55F, 0.92F), 0.9F),
                pos.getX() + 0.2 + random.nextDouble() * 0.6,
                pos.getY() + 0.3 + random.nextDouble() * 0.6,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                0.0, 0.01, 0.0);
    }

    private static void sync(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof VesperThornBlock)) return;
        boolean blooming = ManaPlants.isDusk(level);
        if (state.getValue(BLOOMING) != blooming) {
            level.setBlock(pos, state.setValue(BLOOMING, blooming), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, state.getBlock(), CHECK_INTERVAL);
    }
}
