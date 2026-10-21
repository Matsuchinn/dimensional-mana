package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class PureLiquidManaBlock extends LiquidBlock {
    public PureLiquidManaBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        reactWithNeighbors(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        reactWithNeighbors(level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(18) == 0) {
            double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
            double y = pos.getY() + 0.86;
            double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
            level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.025, 0.0);
            level.addParticle(ParticleTypes.PORTAL, x, y + 0.03, z, 0.0, 0.02, 0.0);
            level.playLocalSound(pos, GeneratedMod.PURE_LIQUID_MANA_BUBBLE.get(), SoundSource.BLOCKS,
                    0.38F, 0.9F + random.nextFloat() * 0.25F, false);
        }
    }

    private static void reactWithNeighbors(Level level, BlockPos pos) {
        if (level.isClientSide || !level.getBlockState(pos).is(GeneratedMod.PURE_LIQUID_MANA_BLOCK.get())) return;

        boolean touchesWater = false;
        boolean changedLava = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.getFluidState().is(FluidTags.WATER)) touchesWater = true;
            if (neighbor.getFluidState().is(FluidTags.LAVA)) {
                BlockState reaction = neighbor.getFluidState().isSource()
                        ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.BASALT.defaultBlockState();
                level.setBlockAndUpdate(neighborPos, reaction);
                changedLava = true;
            }
        }

        if (changedLava) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.35F);
        }
        if (touchesWater && level.getBlockState(pos).is(GeneratedMod.PURE_LIQUID_MANA_BLOCK.get())) {
            level.setBlockAndUpdate(pos, Blocks.CALCITE.defaultBlockState());
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 0.75F);
        }
    }
}
