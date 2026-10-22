package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public final class ManaFurnaceBlock extends AbstractFurnaceBlock {
    private static final MapCodec<ManaFurnaceBlock> MANA_FORGE_CODEC = simpleCodec(
            properties -> new ManaFurnaceBlock(properties, ManaCookingRecipe.FurnaceKind.MANA_FORGE));
    private static final MapCodec<ManaFurnaceBlock> PROFESSIONAL_CODEC = simpleCodec(
            properties -> new ManaFurnaceBlock(properties, ManaCookingRecipe.FurnaceKind.PROFESSIONAL));

    private final ManaCookingRecipe.FurnaceKind kind;

    public ManaFurnaceBlock(BlockBehaviour.Properties properties, ManaCookingRecipe.FurnaceKind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected MapCodec<? extends AbstractFurnaceBlock> codec() {
        return kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE ? MANA_FORGE_CODEC : PROFESSIONAL_CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaFurnaceBlockEntity(kind, pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityType<? extends AbstractFurnaceBlockEntity> expected = kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE
                ? GeneratedMod.MANA_FORGE_BLOCK_ENTITY.get()
                : GeneratedMod.PROFESSIONAL_BLOCK_ENTITY.get();
        return level.isClientSide ? null : createFurnaceTicker(level, type, expected);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;

        if (random.nextDouble() < 0.12) {
            level.playLocalSound(pos,
                    kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE
                            ? GeneratedMod.MANA_FORGE_BURN.get()
                            : GeneratedMod.PROFESSIONAL_FURNACE_BURN.get(),
                    SoundSource.BLOCKS, 0.72F, 0.92F + random.nextFloat() * 0.16F, false);
        }

        Direction direction = state.getValue(FACING);
        double x = pos.getX() + 0.5 - direction.getStepX() * 0.52;
        double y = pos.getY() + 0.52 + random.nextDouble() * 0.18;
        double z = pos.getZ() + 0.5 - direction.getStepZ() * 0.52;
        double side = (random.nextDouble() - 0.5) * 0.55;
        if (direction.getAxis() == Direction.Axis.X) z += side;
        else x += side;
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.015, 0.0);
        if (random.nextDouble() < 0.45) level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
        // 联动时再从背面往魔晶加工台飘一串紫色魔素，让玩家一眼看出两台机器连上了。
        if (random.nextDouble() < 0.35
                && level.getBlockState(pos.relative(direction.getOpposite())).getBlock() instanceof CrystalProcessorBlock) {
            level.addParticle(new DustParticleOptions(new Vector3f(0.68F, 0.41F, 1.0F), 0.85F),
                    pos.getX() + 0.5 - direction.getStepX() * 0.55,
                    pos.getY() + 0.5 + random.nextDouble() * 0.3,
                    pos.getZ() + 0.5 - direction.getStepZ() * 0.55,
                    -direction.getStepX() * 0.055, 0.014, -direction.getStepZ() * 0.055);
        }
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ManaFurnaceBlockEntity furnace && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(furnace, pos);
        }
    }
}
