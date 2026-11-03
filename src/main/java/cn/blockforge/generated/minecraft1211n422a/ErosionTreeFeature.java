package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.AcaciaFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.ForkingTrunkPlacer;

/**
 * 蚀纹木的树特征：先自己检查一遍地面和邻居，通过之后交给原版的 {@code minecraft:tree} 去摆。
 *
 * <p><b>为什么要包一层</b>：原先直接用原版树特征配 JSON，有两个治不好的毛病——
 * 原版不会检查树根底下是什么，落在别人家树冠上也会照长，还会顺手在树干下面塞一块泥土
 * （{@code dirt_provider}），于是玩家看到“嵌在别的树里、根部带着一坨土”的蚀纹木；
 * 树高也只能靠 JSON 里那几个数字慢慢调。</p>
 *
 * <p>现在这一层负责三件事：脚下必须是土（{@code #minecraft:dirt}，本模组的秽蚀土与秽蚀草块
 * 都在里面）、树根周围三格内不能有别的原木或树叶、位置本身得是空的。三项都过了才把
 * {@link #treeConfiguration()} 交给原版树——所以树干、树冠的形状仍然是原版金合欢那套
 * （分叉的树干 + 顶部平铺的树冠），只是把基础高度从 4～9 抬到 7～12，不再是顶着大灯泡的矮树。</p>
 */
public final class ErosionTreeFeature extends Feature<NoneFeatureConfiguration> {
    /** 树最高能长多高，用来限定“周围有没有别的树”的检查范围。 */
    private static final int MAX_TREE_HEIGHT = 12;
    /** 检查周围几格内有没有别的树。 */
    private static final int CLEAR_RADIUS = 3;
    /** 基础高度 / 两段随机增量：最终高度 = 7 + [0,2] + [0,3]，也就是 7～12。 */
    private static final int BASE_HEIGHT = 7;
    private static final int HEIGHT_RAND_A = 2;
    private static final int HEIGHT_RAND_B = 3;

    /** 树配置是常量，但要在方块注册完成之后才拿得到，所以懒加载一次。 */
    private static TreeConfiguration configuration;

    public ErosionTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        // 只长在真正的土上：脚下不是土（别人家的树冠、石头尖、水面）就不长，
        // 顺带也就不会出现“树根下压着一块秽蚀土”的画面。
        if (!level.getBlockState(origin.below()).is(BlockTags.DIRT)) return false;
        if (!level.getBlockState(origin).canBeReplaced()) return false;
        if (!neighboursClear(level, origin)) return false;
        return Feature.TREE.place(configuration(), level, context.chunkGenerator(), context.random(), origin);
    }

    /** 树干周围三格内已经有原木或树叶，就换地方长，别和别的树缠在一起。 */
    private static boolean neighboursClear(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= MAX_TREE_HEIGHT; dy++) {
            for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
                for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
                    cursor.setWithOffset(origin, dx, dy, dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) return false;
                }
            }
        }
        return true;
    }

    /**
     * 原版金合欢那套树形，参数换成蚀纹木的方块：
     * 分叉树干 + 顶部平铺的树冠，基础高度抬到 7 格，最高能到 12 格。
     */
    private static TreeConfiguration configuration() {
        if (configuration == null) {
            BlockState log = ErosionWasteland.EROSION_LOG.get().defaultBlockState();
            BlockState leaves = ErosionWasteland.EROSION_LEAVES.get().defaultBlockState();
            configuration = new TreeConfiguration.TreeConfigurationBuilder(
                    BlockStateProvider.simple(log),
                    new ForkingTrunkPlacer(BASE_HEIGHT, HEIGHT_RAND_A, HEIGHT_RAND_B),
                    BlockStateProvider.simple(leaves),
                    new AcaciaFoliagePlacer(ConstantInt.of(2), ConstantInt.of(0)),
                    new TwoLayersFeatureSize(1, 0, 2))
                    .dirt(BlockStateProvider.simple(ErosionWasteland.EROSION_DIRT.get().defaultBlockState()))
                    .ignoreVines()
                    .build();
        }
        return configuration;
    }
}
