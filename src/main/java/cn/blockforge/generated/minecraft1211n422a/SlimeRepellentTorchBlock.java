package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 史莱姆驱逐火把：一束散发幽光的魔法火把，燃烧的火焰里带着驱赶史莱姆的植物气息。
 *
 * <p>方块本体只做三件事：亮度和原版火把一样是 14、放下/拆掉时把自己的坐标登记进
 * {@link SlimeRepellentData}（那份数据负责 5×5 区块的“禁刷”索引和 16 格的排斥判定）、
 * 以及每隔几刻飘一点淡绿萤光，让“植物气息”看得出来。</p>
 *
 * <p>真正的驱逐逻辑不在方块里跑：每刻遍历方块会很费性能，所以刷怪拦截挂在
 * {@link SlimeRepellentTorchEvents} 的生成事件上，靠近判定挂在史莱姆自己的
 * {@link SlimeDreadGoal} 上——都是“有事件才干活”，没有火把的世界完全没有额外开销。</p>
 */
public class SlimeRepellentTorchBlock extends TorchBlock {
    public static final String BLOCK_ID = "slime_repellent_torch";

    public SlimeRepellentTorchBlock(BlockBehaviour.Properties properties) {
        // 粒子用魂火蓝绿火苗，和贴图的淡绿魔火对得上，也比橙色火苗更“幽光”。
        super(ParticleTypes.SOUL_FIRE_FLAME, properties);
    }

    @Override
    public MapCodec<? extends TorchBlock> codec() {
        return simpleCodec(SlimeRepellentTorchBlock::new);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) SlimeRepellentData.get(server).addTorch(pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 被别的方块顶掉、被炸掉、被玩家拆掉都会走到这里：及时把禁刷标记撤掉。
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            SlimeRepellentData.get(server).removeTorch(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 火苗之外再飘几粒淡绿萤光，对应“驱赶史莱姆的植物气息”。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(6) != 0) return;
        level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.25D + random.nextDouble() * 0.5D,
                pos.getY() + 0.55D + random.nextDouble() * 0.4D,
                pos.getZ() + 0.25D + random.nextDouble() * 0.5D,
                0.0D, 0.01D, 0.0D);
    }
}
