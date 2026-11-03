package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 蚀纹木门：拆掉任何一半，整樘门一起消失、只掉一个门物品。
 *
 * <p>原版的门靠“邻居更新”把剩下的半樘收成空气（{@code DoorBlock#updateShape}），
 * 生存模式下破坏掉落又依赖掉落表里的 {@code half=lower} 条件。实测这套组合在
 * 本模组的门上古怪地不生效——挖掉一半会留下悬着的半扇门。这里不再依赖它：
 * 玩家破坏任何一半时，{@link #playerWillDestroy} 直接把另一半一起清掉，
 * 并补一次掉落（挖上半扇时下半扇没了，东西得还回来，见掉落表的 half=lower 限制）。</p>
 *
 * <p>非玩家的移除（爆炸走 {@code onExplosionHit}、活塞与水流走 updateShape）
 * 沿用原版路径，配合掉落表条件同样不会掉两份。</p>
 */
public class ErosionDoorBlock extends DoorBlock {
    public ErosionDoorBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player != null) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos partnerPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState partner = level.getBlockState(partnerPos);
            if (partner.is(state.getBlock())) {
                level.setBlock(partnerPos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE);
                // 另一半也播一次破碎粒子，看不出“先拆谁”。
                level.levelEvent(player, 2001, partnerPos, Block.getId(partner));
                // 门物品只挂在“下半扇”的掉落表上（half=lower 条件）。
                // 挖的是上半扇时下半扇已经跟着没了，这里手动补一个，生存模式才补。
                if (half == DoubleBlockHalf.UPPER && !player.isCreative()) {
                    popResource(level, pos, new ItemStack(state.getBlock()));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
