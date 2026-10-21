package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** 炽阳之怒的亮度 15 临时光源；火球离开后由计划刻自动清理。 */
public final class BlazingSunLight extends TemporarySpellLight {
    public static final MapCodec<BlazingSunLight> CODEC = simpleCodec(BlazingSunLight::new);
    public BlazingSunLight(BlockBehaviour.Properties properties) { super(properties); }
    @Override public MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                     BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 10);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean maintained = !level.getEntitiesOfClass(BlazingSun.class, new AABB(pos).inflate(6.0),
                sun -> sun.isAlive() && sun.level() == level).isEmpty();
        if (!maintained) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        else level.scheduleTick(pos, this, 10);
    }
}
