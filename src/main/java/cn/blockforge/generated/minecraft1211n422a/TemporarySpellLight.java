package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 微光的真实方块光源；计划刻随存档保存，重启或区块卸载不会留下永久光源。 */
public class TemporarySpellLight extends Block {
    public static final MapCodec<TemporarySpellLight> CODEC = simpleCodec(TemporarySpellLight::new);
    public TemporarySpellLight(BlockBehaviour.Properties properties) { super(properties); }
    @Override public MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 30);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean maintained = !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(8.5),
                e -> e.isAlive() && e.hasEffect(NewSpellContent.GLIMMER) && e.distanceToSqr(Vec3.atCenterOf(pos)) <= 72.25
                        && NewSpellSystem.clear(e, e.getEyePosition(), Vec3.atCenterOf(pos))).isEmpty();
        if (!maintained) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        else level.scheduleTick(pos, this, 30);
    }
    public static void illuminate(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        BlockPos center = entity.blockPosition().above();
        for (int x = -8; x <= 8; x += 4) for (int z = -8; z <= 8; z += 4) {
            if (x * x + z * z > 64) continue;
            BlockPos pos = center.offset(x, 0, z);
            if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir()
                    && NewSpellSystem.clear(entity, entity.getEyePosition(), Vec3.atCenterOf(pos)))
                level.setBlock(pos, NewSpellContent.TEMPORARY_LIGHT.get().defaultBlockState(), 3);
        }
    }
}
