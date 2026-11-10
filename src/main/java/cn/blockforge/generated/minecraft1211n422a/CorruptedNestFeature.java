package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.WorldGenLevel;

/**
 * 秽蚀蜂巢的生成：世界生成「表层修饰」阶段扫描整个区块——
 * 数够 3 株四种目标植物（暮刺荆棘 / 血纹花 / 瑞斯特利亚 / 卢梅莉亚）的花气林子，
 * 再在树上找挂巢的位置。
 *
 * <p>密度（r92 重调）：r91 是「每处候选挂点 16% × 最多试 72 次 × 每区块最多 2 个」，
 * 实际效果是<b>只要区块里有花有树就几乎必出 2 窝</b>，玩家小地图上一片黑点，
 * 蜂也铺天盖地。现在改成「<b>每个区块只掷一次骰，10% 中，且最多 1 窝</b>」：
 * 走一圈看到一两窝的概率刚好，也省掉了逐格试 72 次的世界生成开销。</p>
 *
 * <p>放在 {@code top_layer_modification} 这一步是刻意的：树和植物都是更早的
 * {@code vegetal_decoration} 落的，轮到巢时整个区块的树冠和花丛都已经在场，
 * 不用赌多个 feature 同一步骤内的先后顺序。</p>
 *
 * <p>巢里预住 4 只秽蚀蜂。原版 {@code addOccupant} 有 3 只的容量闸，
 * 生成期用 {@code storeBee} 直接写入可以绕过，存进 {@code Bees} 列表。</p>
 */
public final class CorruptedNestFeature extends Feature<NoneFeatureConfiguration> {
    /** 整个区块掷一次骰：10% 才挂窝（原来是有花有树就几乎必出两窝）。 */
    private static final float CHUNK_CHANCE = 0.10F;
    /** 区块里至少要几株目标植物才考虑挂巢。 */
    private static final int MIN_PLANTS = 3;
    /** 一次生成最多试几处候选挂点，免得大森林把生成耗时拖爆。 */
    private static final int MAX_ATTEMPTS = 24;
    /** 一个区块最多挂几个巢：一窝就够，玩家要的是一处见一两个。 */
    private static final int MAX_PER_CHUNK = 1;
    /** 新巢入住的蜂数（r92：4 → 3，跟原版蜂巢对齐，少一点就不会一出门被围）。 */
    private static final int OCCUPANTS = 3;

    private static final ManaPlants.Kind[] FAVORED = {
            ManaPlants.Kind.VESPER_THORN,
            ManaPlants.Kind.BLOOD_KISS,
            ManaPlants.Kind.RESTELIA,
            ManaPlants.Kind.LUMELIA,
    };

    public CorruptedNestFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15;
        int z0 = context.origin().getZ() & ~15;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos under = new BlockPos.MutableBlockPos();

        int plants = 0;
        List<BlockPos> treeTrunks = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0 + x, z0 + z);
                // 只扫地表上下这一段：花贴着地皮，树冠最高也就二十来格。
                for (int y = surface - 3; y < surface + 26; y++) {
                    cursor.set(x0 + x, y, z0 + z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    for (ManaPlants.Kind kind : FAVORED) {
                        if (ManaPlants.isPlant(state, kind)) {
                            // 卢梅莉亚一串垂好多格，数「株」不数「格」：脚下不是同种才算一株。
                            under.set(x0 + x, y - 1, z0 + z);
                            if (!ManaPlants.isPlant(level.getBlockState(under), kind)) plants++;
                        }
                    }
                    if (state.is(BlockTags.LOGS) && treeTrunks.size() < 256) {
                        treeTrunks.add(cursor.immutable());
                    }
                }
            }
        }
        if (plants < MIN_PLANTS || treeTrunks.isEmpty()) return false;
        // 整块地只掷这一次：不中就是这片林子没窝，而不是「每棵树上都掷一次」。
        if (random.nextFloat() >= CHUNK_CHANCE) return false;

        int placed = 0;
        List<BlockPos> taken = new ArrayList<>();
        for (int attempt = 0; attempt < MAX_ATTEMPTS && placed < MAX_PER_CHUNK; attempt++) {
            BlockPos trunk = treeTrunks.get(random.nextInt(treeTrunks.size()));
            Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos spot = trunk.relative(side);
            if (!level.isEmptyBlock(spot)) continue;
            if (taken.contains(spot)) continue;
            // 要挂在树荫底下：顶上得是叶子或树干，别吊在半空。
            BlockState canopy = level.getBlockState(spot.above());
            if (!(canopy.is(BlockTags.LEAVES) || canopy.is(BlockTags.LOGS))) continue;
            if (hangNest(level, spot, side, random)) {
                placed++;
                taken.add(spot.immutable());
            }
        }
        return placed > 0;
    }

    private boolean hangNest(WorldGenLevel level, BlockPos spot, Direction facing, RandomSource random) {
        // 入口朝外，正对离开树干的那一面。
        BlockState nest = CorruptedBeeContent.nestBlock().defaultBlockState()
                .setValue(BeehiveBlock.FACING, facing);
        setBlock(level, spot, nest);
        if (level.getBlockEntity(spot) instanceof BeehiveBlockEntity hive) {
            String entityKey = BuiltInRegistries.ENTITY_TYPE
                    .getKey(GeneratedMod.CORRUPTED_BEE.get()).toString();
            for (int i = 0; i < OCCUPANTS; i++) {
                CompoundTag identity = new CompoundTag();
                identity.putString("id", entityKey);
                // 最短滞留 600 刻：刚生成的蜂巢不会被过路的蜜蜂一秒掏空。
                hive.storeBee(new BeehiveBlockEntity.Occupant(CustomData.of(identity), 0, 600));
            }
        }
        return true;
    }
}
