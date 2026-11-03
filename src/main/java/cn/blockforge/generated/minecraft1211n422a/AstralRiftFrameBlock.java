package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 星之间隙（r290 前叫“星穹裂隙框”）：反虚空新手仪式之门的本体。
 *
 * <p>r290 起它不再是围着空气的空心门框——这整块方块就是门：
 * 贴图是 16 帧循环的“万剑归宗”流光动画（深空靛蓝底，白色剑形光束汇向中心），
 * 自带 12 级光照；碰撞箱清空，可以直接从门里穿过去，
 * 走门传送的判定照旧在 {@link AntiVoidSystem}。</p>
 *
 * <p>硬度维持“生存模式要下界合金镐才撬得动”（{@link AntiVoidSystem#onBlockBreak}），
 * 创造与 {@code /fill} 不受影响。旧存档里留下的空心门框不会自动改建，
 * 但新一次仪式（{@code /dimensional_mana antvoid resetintro}）铺的就是门板。</p>
 */
public final class AstralRiftFrameBlock extends Block {
    public AstralRiftFrameBlock(Properties properties) {
        super(properties);
    }

    /** 门本身是“光”，不是“墙”：不设碰撞，人和实体直接穿过。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
