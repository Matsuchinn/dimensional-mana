package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * r282：露天魔晶矿簇（Open-air Mana Crystal Cluster）。
 *
 * <p>地表魔素浓度够高的地方才有资格生成：蚀魔荒原自己钉在 13.50 ± 2.00，门槛 14.00，
 * 也就是荒原上大约四分之一的地块够格；别的地表群系平均只有 10 左右，只有浓度偏高
 * （≥11.50）的一成地块再掷一次 15% 的骰子才会长出矿簇——这是 r284 新加的，
 * 目的是让玩家跑几千格就能撞见一座，而不是几万格才有一座。</p>
 *
 * <p>一个区域基本只出一处：结构集 {@code structure_set} 里用的间距是 26 区块
 * （约 400 格）、至少隔开 10 区块，比荒原本身还大，所以一片普通荒原顶多一座；
 * 只有特别大的荒原才可能装下第二座。</p>
 *
 * <p>形状由种子 + 结构坐标算出来（{@link Variant#at}），四种各 25%：破土刺天型、
 * 寄生巨树型、陨石塌陷型、环形祭坛型。守卫（3～7 只随机魔素变异体）
 * 在构件里只排队，等区块加载后由 {@link WorldgenSpawnQueue} 真正刷出来，
 * 另外 {@link CrystalClusterData} 每秒兜底核对一次，保证玩家的档里一定站得住人。</p>
 *
 * <p>r283：候选区块的筛选顺序改成"最便宜的先做"。{@code /locate} 每命中一个候选区块
 * 都会调一次这里，一次搜索会试掉几千个区块；以前一上来就采两次高度图（每次都要建
 * 一整列噪声区块），光是判断"这里不该有结构"就要花掉几秒钟。现在先用群系噪声 +
 * 自己那套浓度噪声一票否决，普通群系九成的地块在这一步就出局，高度图一次都不采。</p>
 */
public final class OpenAirManaCrystalClusterStructure extends Structure {
    public static final MapCodec<OpenAirManaCrystalClusterStructure> CODEC =
            simpleCodec(OpenAirManaCrystalClusterStructure::new);

    /** 荒原门槛：地表魔素浓度至少 14.00（设计稿的原始要求）。 */
    public static final double WASTELAND_MIN_CONCENTRATION = 14.0;

    /** 普通群系门槛：先要求浓度 ≥ 11.50，再按 {@link #NORMAL_CHANCE} 掷一次骰子。 */
    public static final double NORMAL_MIN_CONCENTRATION = 11.5;

    /**
     * 普通群系里通过浓度那一关之后，真正长出矿簇的概率。
     *
     * <p>普通地表群系的浓度平均只有 10 左右，约一成地块能到 11.50；再乘 15% 之后，
     * 配合 26 区块（416 格）的候选网格，整张地图上大约 1.6% 的候选格会长出矿簇，
     * 均摊下来离最近一座 2000 格上下（荒原上会再密一点）。
     * 玩家跑五六千格基本一定会撞见一座，用
     * {@code /dimensional_mana locate_cluster} 随叫随到。</p>
     */
    public static final double NORMAL_CHANCE = 0.15;

    /**
     * 廉价预筛的采样高度：从世界最高处往下退 8 格、128 格。
     *
     * <p>为什么要采"高空"而不是"地表附近"：群系的三维参数里那个 depth 是相对地表算的
     * （地表处 depth≈0，往下越挖越大，0.2~0.9 那一段会被溶洞群系抢走）。所以在 y=63
     * 这种固定高度采到的多半是"地下群系"，海拔一高就全错。反过来，**在地表以上**
     * depth&lt;0，所有地表群系都回到同一档，谁离得近就只看另外五个气候参数 ——
     * 采到的就是这一列真正的地表群系。两次采样比一次高度图便宜两个数量级。</p>
     */
    private static final int[] PROBE_BELOW_TOP = {8, 128};


    public OpenAirManaCrystalClusterStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();

        // 第一步是廉价预筛：群系、浓度、普通群系的随机骰子全在内存里算完，
        // 约 98% 的候选区块在这里就出局了，一次高度图都不用采。
        if (!passesCheapGate(context.biomeSource(), context.randomState().sampler(), context.seed(),
                x, z, context.heightAccessor().getMaxBuildHeight())) {
            return Optional.empty();
        }

        // 第二步才付高度图的钱：一次 getBaseHeight 要建一整列噪声区块，很贵。
        int surface = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        int floor = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
        // 高度图给的是"地表上方那一格空气"，减一才是能站人的方块。
        BlockPos center = new BlockPos(x, floor - 1, z);
        // 水面和浅滩上不冒矿簇：柱子一半泡在水里不好看，也容易被水冲掉。
        if (surface - floor > 1 || floor <= context.chunkGenerator().getSeaLevel()) return Optional.empty();
        if (center.getY() < context.heightAccessor().getMinBuildHeight() + 8) return Optional.empty();

        Holder<Biome> biome = context.biomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(floor), QuartPos.fromBlock(z),
                context.randomState().sampler());
        // 复核一次浓度（用真正的地表群系，比高空采样更准）；随机那一步不再重掷，
        // 否则同一格在 /locate 和实际生成时可能给出不同答案。
        double concentration = ManaConcentration.surfaceAt(context.seed(), Level.OVERWORLD, biome, center);
        double minimum = CorruptedWastelandBiome.is(biome)
                ? WASTELAND_MIN_CONCENTRATION : NORMAL_MIN_CONCENTRATION;
        if (concentration < minimum) return Optional.empty();

        Variant variant = Variant.at(context.seed(), x, z);
        long layoutSeed = context.random().nextLong();
        // 不用 onTopOfChunkCenter：它会再采一次高度图，而我们自己的构件坐标已经定死了，
        // 拿 center 当定位点还能让 /locate 直接报出真正的地表高度。
        return Optional.of(new GenerationStub(center,
                (StructurePiecesBuilder builder) -> builder.addPiece(
                        new OpenAirManaCrystalClusterPiece(center, variant, layoutSeed))));
    }

    /**
     * 最便宜的一票否决：只采群系噪声，判断这一列够不够格长矿簇。
     *
     * <p>规则分两档：</p>
     * <ul>
     *   <li><b>蚀魔荒原</b>：浓度 ≥ {@link #WASTELAND_MIN_CONCENTRATION}（14.00）。
     *       荒原固定在 13.50±2.00，所以约四分之一的地块够格，荒原依旧是矿簇的主场。</li>
     *   <li><b>其他地表群系</b>：浓度 ≥ {@link #NORMAL_MIN_CONCENTRATION}（11.50）
     *       之后，再按 {@link ManaConcentration#unitHash} 掷一次
     *       {@link #NORMAL_CHANCE} 的骰子。别处平均浓度只有 10 左右，所以只有
     *       "魔素偏高的普通地块"里的一小撮会长出矿簇。</li>
     * </ul>
     *
     * <p>采样点放在地表以上（见 {@link #PROBE_BELOW_TOP}），这样不管山多高，采到的
     * 都是地表群系；两个高度里只要有一个是荒原就按荒原算。<b>整段只做几次噪声采样，
     * 不采高度图、不生成区块</b>，所以 {@code /locate} 和
     * {@code /dimensional_mana locate_cluster} 都先用它筛一遍，通过了才进贵的那条路。</p>
     *
     * @param maxBuildHeight 世界最高建造高度（主世界 320），采样点从它往下退
     */
    public static boolean passesCheapGate(BiomeSource source, Climate.Sampler sampler, long seed,
                                          int blockX, int blockZ, int maxBuildHeight) {
        int quartX = QuartPos.fromBlock(blockX);
        int quartZ = QuartPos.fromBlock(blockZ);
        Holder<Biome> first = null;
        for (int below : PROBE_BELOW_TOP) {
            int y = maxBuildHeight - below;
            Holder<Biome> probe = source.getNoiseBiome(quartX, QuartPos.fromBlock(y), quartZ, sampler);
            if (first == null) first = probe;
            if (CorruptedWastelandBiome.is(probe)) {
                return concentrationAt(seed, probe, blockX, blockZ) >= WASTELAND_MIN_CONCENTRATION;
            }
        }
        if (first == null) return false;
        Holder<Biome> biome = first;
        if (concentrationAt(seed, biome, blockX, blockZ) < NORMAL_MIN_CONCENTRATION) return false;
        return ManaConcentration.unitHash(seed, blockX, blockZ) < NORMAL_CHANCE;
    }

    /** 浓度只和 x/z、群系有关，y 随便给一格都行（这里用 0，省得为了它先采高度图）。 */
    private static double concentrationAt(long seed, Holder<Biome> biome, int blockX, int blockZ) {
        return ManaConcentration.surfaceAt(seed, Level.OVERWORLD, biome, new BlockPos(blockX, 0, blockZ));
    }

    @Override
    public StructureType<?> type() {
        return GeneratedMod.OPEN_AIR_MANA_CRYSTAL_CLUSTER_STRUCTURE_TYPE.get();
    }

    /**
     * 四种外观形态，出现概率各占四分之一。
     *
     * <p>r285：以前是 40% / 25% / 20% / 15%（破土刺天最常见、环形祭坛最少），玩家实际遇到
     * 的却是"破土刺天、环形祭坛一大把，寄生巨树、陨石塌陷难得一见"，四种观感差太多。
     * 现在直接均分，四种各 25%。</p>
     *
     * <p>形态不再每次掷骰子，而是由<b>种子 + 结构坐标</b>算出来（{@link #at}）：
     * 世界生成和 {@code /dimensional_mana locate_cluster} 报的是同一个形态，
     * 同一座矿簇重进存档也不会变（以前 {@code /locate} 那次掷的骰子和真正生成的那次无关）。</p>
     */
    public enum Variant {
        /** 破土刺天型：巨大矿柱从地底刺出地表，底部碎裂、中间高级、顶端极品。 */
        SPIKE,
        /** 寄生巨树型：一根巨大蚀纹木枯树，矿柱顺着树干螺旋缠绕，树冠结出魔晶。 */
        PARASITE_TREE,
        /** 陨石塌陷型：陨石坑般的凹陷，坑底平滑蚀脉石，矿柱放射状向外倾斜。 */
        CRATER,
        /** 环形祭坛型：6 根均匀矿柱围成一圈，中心平滑蚀脉石，下面埋着魔晶块。 */
        RING_ALTAR;

        /** 四种形态的权重，各 25%。 */
        private static final double SHARE = 0.25;

        /**
         * 由 (seed, x, z) 定出这座矿簇的形态：同一格永远同一个答案，
         * 世界生成与 {@code /locate} 因此完全一致。
         */
        public static Variant at(long seed, int x, int z) {
            double roll = ManaConcentration.unitHash(seed ^ 0x0C1A57E2L, x, z);
            if (roll < SHARE) return SPIKE;
            if (roll < SHARE * 2) return PARASITE_TREE;
            if (roll < SHARE * 3) return CRATER;
            return RING_ALTAR;
        }

        /** 指令 / 更新说明里用的中文名（不走语言文件，服务器端也能直接打日志）。 */
        public String displayName() {
            return switch (this) {
                case SPIKE -> "破土刺天型";
                case PARASITE_TREE -> "寄生巨树型";
                case CRATER -> "陨石塌陷型";
                case RING_ALTAR -> "环形祭坛型";
            };
        }

        public static Variant byId(int id) {
            Variant[] values = values();
            return values[Math.floorMod(id, values.length)];
        }
    }
}
