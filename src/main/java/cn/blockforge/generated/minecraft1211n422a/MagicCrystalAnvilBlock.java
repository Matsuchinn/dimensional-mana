package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;

/** 魔晶铁砧：保留原版铁砧的碰撞与朝向，但使用独立标题、音效和打开逻辑。 */
public final class MagicCrystalAnvilBlock extends AnvilBlock {
    public MagicCrystalAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new MagicCrystalAnvilMenu(
                            containerId, inventory, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("container.dimensional_mana.magic_crystal_anvil")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 音效事件在运行时才取值，避开模组注册阶段的 DeferredHolder 时序问题。 */
    @Override
    protected SoundType getSoundType(BlockState state) {
        return new SoundType(1.0F, 0.92F,
                GeneratedMod.MAGIC_CRYSTAL_ANVIL_BREAK.get(),
                GeneratedMod.MAGIC_CRYSTAL_ANVIL_PLACE.get(),
                GeneratedMod.MAGIC_CRYSTAL_ANVIL_PLACE.get(),
                GeneratedMod.MAGIC_CRYSTAL_ANVIL_BREAK.get(),
                GeneratedMod.MAGIC_CRYSTAL_ANVIL_PLACE.get());
    }
}
