package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 灵魂的十字路口：死亡重生后浮现在膜上空的两扇巨门。
 *
 * <p>同一个类做两种颜色——血红的「回溯之门」（回死亡点，代价是魔素上限永久 -1%）、
 * 亮蓝的「归乡之门」（回绑定重生点，无代价）。门只为刚死过的人亮起：
 * 手里没有“待选择”标记时点它，只会得到一句“门没有回应”。</p>
 *
 * <p>回溯之门有二次确认（10 秒内再点一次才生效），防止拿着工具路过误触
 * 就把永久惩罚付了。门本身硬度 -1、不可破坏、无物品形态，由
 * {@link AntiVoidSystem} 在玩家重生时按固定坐标浮现。</p>
 */
public final class RiftGateBlock extends Block {
    public enum Kind { CRIMSON, AZURE }

    public final Kind kind;

    public RiftGateBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 潜行 + 手持方块：留给“想拿它当脚手架”的创造玩家，其它情况一律按门处理。
        if (player.isShiftKeyDown() && !stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            AntiVoidSystem.onGateUse(serverPlayer, pos, kind);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            AntiVoidSystem.onGateUse(serverPlayer, pos, kind);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 门是光幕不是墙：不设碰撞，人和实体直接走进去。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** 身体接触门幕即触发（走门即传送），右键点击的老办法也仍然有效。 */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            AntiVoidSystem.onGateContact(serverPlayer, pos, kind);
        }
    }
}
