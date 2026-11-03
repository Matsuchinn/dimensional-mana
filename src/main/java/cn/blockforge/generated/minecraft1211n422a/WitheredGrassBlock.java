package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 秽生草：荒原上成片铺开的枯紫色杂草。
 *
 * <p>掉落规则写在方块战利品表里——只有剪刀能整丛剪下来，其他工具铲过去就什么都没有了。</p>
 *
 * <p><b>r276 修复</b>：以前 {@code getShape} 也返回 {@code Shapes.empty()}。而 {@code getShape}
 * 不只管碰撞，它同时是<b>准星判定框</b>：判定框为空就等于这丛草根本点不到，手打、拿剪刀、
 * 拿任何工具都会穿过去——方块完全无法交互（创造模式敲不掉、剪刀也剪不下来）。
 * 现在把两件事拆开：<b>碰撞箱</b>留空（走路照旧不挡路），<b>准星框</b>给一个 2×12×2 的小方框。
 * 于是手打能敲掉（按战利品表不掉东西）、剪刀能整丛剪下来。</p>
 */
public class WitheredGrassBlock extends BushBlock {
    public static final MapCodec<WitheredGrassBlock> CODEC = simpleCodec(WitheredGrassBlock::new);
    /** 准星判定框：只为了让这一丛草「点得到」，不参与碰撞。 */
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

    @Override
    public MapCodec<WitheredGrassBlock> codec() {
        return CODEC;
    }

    public WitheredGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 回到原设计：草不挡路，走过去踩的是底下的土（准星框见 {@link #SHAPE}）。 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    /** 荒原的草不挑地方，秽蚀土 / 秽蚀草块上都站得住（父类只认「顶面实心」）。 */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ErosionWasteland.EROSION_DIRT.get())
                || state.is(ErosionWasteland.EROSION_GRASS.get())
                || super.mayPlaceOn(state, level, pos);
    }
}
