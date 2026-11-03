package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * 秽蚀草块：蚀魔荒原的「草皮」。
 *
 * <p><b>崩溃修复记录</b>：原来这里继承的是原版的草方块父类
 * （{@code SpreadingSnowyDirtBlock}），那个父类自带「草向泥土蔓延」的随机刻。我们的随机刻
 * 在草皮被压住、退化回秽蚀土时，顺手把父类的 {@code snowy} 属性也设到了土上；
 * 可是 {@link ErosionDirtBlock 秽蚀土} 是普通方块、没有这个属性，于是服务端抛
 * {@code IllegalArgumentException: Cannot set property snowy as it does not exist in
 * Block{dimensional_mana:erosion_dirt}} 直接崩服。现在改成继承普通 {@link Block}，
 * 随机刻自己接管，退化时只换默认状态，绝不再碰 {@code snowy}。</p>
 *
 * <p>{@code snowy} 属性本身<b>保留</b>了：旧存档里已经生成的草皮方块状态带它，
 * 直接删属性会让老世界读区块时冒出「未知属性」。荒原不下雪，这个属性在实际游戏里恒为
 * {@code false}，只承担存档兼容和 {@code /setblock} 的兼容作用。</p>
 *
 * <p>随机刻行为：</p>
 * <ul>
 *   <li>光照足够时，草皮会慢慢爬满附近的<b>秽蚀土</b>；</li>
 *   <li>同一轮检查里还有小概率在相邻的草皮上长出一丛<b>秽生草</b>；</li>
 *   <li>被不透明方块压住太久会退化回<b>秽蚀土</b>。</li>
 * </ul>
 */
public class ErosionGrassBlock extends Block {
    /** 只为旧存档 / 原版逻辑兼容保留；代码里不会再把它设到别的方块上。 */
    public static final BooleanProperty SNOWY = BlockStateProperties.SNOWY;

    public static final com.mojang.serialization.MapCodec<ErosionGrassBlock> CODEC =
            simpleCodec(ErosionGrassBlock::new);

    @Override
    public com.mojang.serialization.MapCodec<ErosionGrassBlock> codec() {
        return CODEC;
    }

    public ErosionGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SNOWY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SNOWY);
    }

    /** 上方盖了雪就翻成 snowy —— 和原版草方块一致（荒原不下雪，基本触发不到）。 */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) {
            return state.setValue(SNOWY, isSnow(neighborState));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private static boolean isSnow(BlockState state) {
        return state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canBeGrass(state, level, pos)) {
            // 顶被盖死 → 退化回秽蚀土。土没有 snowy，只换默认状态（崩溃就修在这）。
            level.setBlockAndUpdate(pos, ErosionWasteland.EROSION_DIRT.get().defaultBlockState());
            return;
        }
        if (level.getMaxLocalRawBrightness(pos.above()) < 9) return;
        BlockState grass = defaultBlockState();
        for (int i = 0; i < 4; i++) {
            BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
            BlockState below = level.getBlockState(target);
            // 草爬土。
            if (below.is(ErosionWasteland.EROSION_DIRT.get()) && canPropagate(grass, level, target)) {
                level.setBlockAndUpdate(target, grass);
                continue;
            }
            // 偶尔在草皮上冒出一丛秽生草（稀疏，符合荒原气质）。
            if (below.is(this) && random.nextFloat() < 0.04F) {
                BlockPos above = target.above();
                BlockState tuft = ErosionWasteland.WITHERED_GRASS.get().defaultBlockState();
                if (level.isEmptyBlock(above) && tuft.canSurvive(level, above)) {
                    level.setBlock(above, tuft, Block.UPDATE_ALL);
                }
            }
        }
    }

    // ── 以下两段是原版草方块的 private 判定，原版源码内联 ──

    private static boolean canBeGrass(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.is(Blocks.SNOW)
                && aboveState.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) == 1) {
            return true;
        }
        if (aboveState.getFluidState().getAmount() == 8) return false;
        int lightBlock = LightEngine.getLightBlockInto(level, state, pos, aboveState, above,
                Direction.UP, aboveState.getLightBlock(level, above));
        return lightBlock < level.getMaxLightLevel();
    }

    private static boolean canPropagate(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        return canBeGrass(state, level, pos) && !level.getFluidState(above).is(FluidTags.WATER);
    }
}
