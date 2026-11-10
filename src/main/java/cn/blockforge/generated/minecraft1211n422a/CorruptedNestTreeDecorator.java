package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/**
 * 蚀纹木挂巢装饰器（r91）：照原版 {@code BeehiveDecorator} 的路子，
 * 树成形之后按概率在树干侧面挂一个秽蚀蜂巢——<b>种树苗也走这条路</b>，
 * 因为树苗生长和世界生成共用 {@code ErosionTreeFeature} 的同一份树配置，
 * 装饰器挂在配置上就两边都生效（原版橡树的"5% 带蜂巢"就是这么做的）。
 *
 * <p>和 {@code CorruptedNestFeature}（世界生成期的野生巢）的区别：那边扫整个区块、
 * 条件苛刻一点；这边是"这棵树自己中奖"，概率独立。两个来源互不知道对方的存在，
 * 同区块同时中奖时顶多蜂巢挨得近一点，原版蜂巢装饰器同样不管这个。</p>
 *
 * <p>新巢入住 2～3 只秽蚀蜂（比野生成少一点——玩家种的树中奖率本来就高），
 * 最短滞留 600 刻，防止刚挂上就被路过的蜂掏空。</p>
 */
public final class CorruptedNestTreeDecorator extends TreeDecorator {
    public static final MapCodec<CorruptedNestTreeDecorator> CODEC = RecordCodecBuilder.mapCodec(
            group -> group.group(
                    Codec.floatRange(0.0F, 1.0F).fieldOf("probability")
                            .forGetter(CorruptedNestTreeDecorator::probability))
                    .apply(group, CorruptedNestTreeDecorator::new));

    private final float probability;

    public CorruptedNestTreeDecorator(float probability) {
        this.probability = probability;
    }

    public float probability() {
        return probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return GeneratedMod.CORRUPTED_NEST_TREE_DECORATOR.get();
    }

    @Override
    public void place(Context context) {
        RandomSource random = context.random();
        if (random.nextFloat() >= this.probability) return;
        // 收集候选：树干侧面、左右上空着的地方（巢不能嵌进木头或叶子墙里）。
        List<BlockPos> spots = new ArrayList<>();
        List<Direction> facings = new ArrayList<>();
        for (BlockPos log : context.logs()) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos spot = log.relative(side);
                if (context.isAir(spot) && context.isAir(spot.above())) {
                    spots.add(spot);
                    facings.add(side);
                }
            }
        }
        if (spots.isEmpty()) return;
        int pick = random.nextInt(spots.size());
        BlockPos spot = spots.get(pick);
        // 入口朝外，正对离开树干的那一面（与 CorruptedNestFeature 同一套摆法）。
        context.setBlock(spot, CorruptedBeeContent.nestBlock().defaultBlockState()
                .setValue(BeehiveBlock.FACING, facings.get(pick)));
        context.level().getBlockEntity(spot, BlockEntityType.BEEHIVE).ifPresent(hive -> {
            String entityKey = BuiltInRegistries.ENTITY_TYPE
                    .getKey(GeneratedMod.CORRUPTED_BEE.get()).toString();
            int bees = 2 + random.nextInt(2);
            for (int i = 0; i < bees; i++) {
                CompoundTag identity = new CompoundTag();
                identity.putString("id", entityKey);
                hive.storeBee(new BeehiveBlockEntity.Occupant(CustomData.of(identity), 0, 600));
            }
        });
    }
}
