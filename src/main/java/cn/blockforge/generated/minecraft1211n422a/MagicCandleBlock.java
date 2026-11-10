package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 魔法蜡烛：完美继承原版蜡烛（一格最多叠 4 根、打火石 / 火焰弹点燃、淋雨熄灭、
 * 亮度随根数增长），外加一圈魔素光环——
 * 每根点燃的蜡烛对环境魔素浓度 +0.15，每根未点燃的 −0.15，作用范围是它周围的
 * 5×5×5 立方。结算与缓存见 {@link MagicCandleAura}。
 *
 * <p>额外支持烈焰粉右键点燃（原版两种点火方式照旧可用）；点燃后在原版火苗粒子上
 * 再叠一层灵魂火，紫蓝色的火星一眼就能和「休眠」状态区分开。</p>
 */
public final class MagicCandleBlock extends CandleBlock {
    public MagicCandleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // 烈焰粉点烛：一次消耗 1 份，只在「未点燃、没进水」时成立，点火手感对齐打火石。
        if (stack.is(Items.BLAZE_POWDER) && canLight(state)) {
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, state.setValue(LIT, Boolean.TRUE));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.4F);
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        super.animateTick(state, level, pos, random);
        // 点燃状态在原版橘黄火苗之外补一缕灵魂火：暗紫偏蓝，说明「这根蜡烛在干活」。
        if (!isLit(state) || random.nextInt(3) != 0) return;
        for (net.minecraft.world.phys.Vec3 offset : getParticleOffsets(state)) {
            if (random.nextInt(6) != 0) continue;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + offset.x + random.triangle(0.0D, 0.06D),
                    pos.getY() + offset.y + 0.12D + random.nextDouble() * 0.08D,
                    pos.getZ() + offset.z + random.triangle(0.0D, 0.06D),
                    0.0D, 0.012D, 0.0D);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) MagicCandleAura.onPlaced(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) MagicCandleAura.onRemoved(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
