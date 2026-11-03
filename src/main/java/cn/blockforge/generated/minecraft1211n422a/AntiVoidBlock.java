package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 反虚空方块：维度上下边界的屏障式封印。
 *
 * <p>它仍然保留完整碰撞箱和不可破坏逻辑，但客户端不绘制方块本身，
 * 同时让天空光穿过封印层，避免启用光影后实体与粒子变成黑色。</p>
 */
public final class AntiVoidBlock extends Block {
    public AntiVoidBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
