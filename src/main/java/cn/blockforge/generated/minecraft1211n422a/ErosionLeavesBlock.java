package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 蚀纹树叶：紫灰色的树冠。每隔一会儿就往下撒落叶粒子（自定义的 {@code erosion_leaf}，
 * 紫色版樱花落叶）。叶子本体掉苗 / 掉棍的规则在战利品表里。
 */
public class ErosionLeavesBlock extends LeavesBlock {
    public static final MapCodec<ErosionLeavesBlock> CODEC = simpleCodec(ErosionLeavesBlock::new);

    @Override
    public MapCodec<ErosionLeavesBlock> codec() {
        return CODEC;
    }

    public ErosionLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(12) == 0) {
            BlockPos below = pos.below();
            if (!isFaceFull(level.getBlockState(below).getCollisionShape(level, below),
                    net.minecraft.core.Direction.UP)) {
                ParticleUtils.spawnParticleBelow(level, pos, random, ErosionWasteland.EROSION_LEAF.get());
            }
        }
    }
}
