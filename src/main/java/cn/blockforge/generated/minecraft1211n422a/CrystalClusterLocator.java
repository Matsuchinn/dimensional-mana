package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.Optional;

/**
 * r283：给 {@code /dimensional_mana locate_cluster} 用的快速搜索。
 *
 * <p>原版的 {@code /locate structure} 会一格一格地翻结构网格：每翻一个候选区块都要读一次
 * 区块存档文件（{@code StructureCheck}），凑巧够条件更要真去生成那个区块。露天魔晶矿簇
 * 只在浓度够高的地方出现，实在稀有，一搜就是几千上万个候选区块，于是游戏一卡好久 —— 这条
 * 指令以前直接套用那套搜索（半径还写了 200），所以一按下去就跟死机一样。</p>
 *
 * <p>这里走同一张结构网格（{@code random_spread}），但完全在内存里算：由近到远一圈一圈地试，
 * 先用群系噪声 + 浓度噪声一票否决（{@link OpenAirManaCrystalClusterStructure#passesCheapGate}），
 * 通过了才用结构自己的 {@code findValidGenerationPoint} 精确复核一次，
 * 整个过程不读存档、不生成区块。</p>
 */
public final class CrystalClusterLocator {
    /** 只装露天魔晶矿簇的结构标签。 */
    public static final TagKey<Structure> CRYSTAL_CLUSTER_TAG = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "mana_crystal_clusters"));

    /** 默认搜索半径（单位是结构网格的格子，一格 = 26 区块 ≈ 416 格）。 */
    public static final int DEFAULT_CELLS = 80;
    /** 上限：再往外基本就是整个世界都翻一遍了，没有意义。 */
    public static final int MAX_CELLS = 400;
    /** 时间预算：超了就带着"没找到"收手，宁可没结果也不能把服务器线程卡住。 */
    private static final long BUDGET_NANOS = 500_000_000L;

    /** 搜索结果：{@code pos} 为 null 表示没找到，{@code timedOut} 表示是超时收手的。 */
    public record Result(BlockPos pos, int cells, boolean timedOut) {
        public boolean found() {
            return pos != null;
        }
    }

    public static Result find(ServerLevel level, BlockPos origin, int maxCells) {
        int limit = Math.max(1, Math.min(MAX_CELLS, maxCells));
        Optional<Holder<Structure>> structure = taggedStructure(level);
        if (structure.isEmpty()) return new Result(null, 0, false);

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Search search = new Search(level, generator, randomState, state, structure.get().value(), origin);
        long deadline = System.nanoTime() + BUDGET_NANOS;

        for (StructurePlacement placement : state.getPlacementsForStructure(structure.get())) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
            // spacing 是数据包里的数字，0 会让 floorDiv 直接崩，防御一下。
            if (spread.spacing() <= 0) continue;
            search.spread = spread;
            for (int ring = 0; ring <= limit; ring++) {
                BlockPos hit = search.ring(ring);
                if (hit != null) return new Result(hit, ring, false);
                if (System.nanoTime() > deadline) return new Result(null, ring, true);
            }
        }
        return new Result(null, limit, false);
    }

    /** 从结构标签里取出（唯一的）那个结构；标签没装东西时返回空。 */
    private static Optional<Holder<Structure>> taggedStructure(ServerLevel level) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<HolderSet.Named<Structure>> tagged = registry.getTag(CRYSTAL_CLUSTER_TAG);
        if (tagged.isEmpty()) return Optional.empty();
        for (Holder<Structure> holder : tagged.get()) {
            return Optional.of(holder);
        }
        return Optional.empty();
    }

    /** 一次搜索用到的上下文，省得每个候选区块都把参数重新凑一遍。 */
    private static final class Search {
        private final ServerLevel level;
        private final ChunkGenerator generator;
        private final RandomState randomState;
        private final ChunkGeneratorStructureState state;
        private final Structure structure;
        private final BlockPos origin;
        private final int originChunkX;
        private final int originChunkZ;
        private final int maxBuildHeight;
        private final long seed;

        private RandomSpreadStructurePlacement spread;

        Search(ServerLevel level, ChunkGenerator generator, RandomState randomState,
               ChunkGeneratorStructureState state, Structure structure, BlockPos origin) {
            this.level = level;
            this.generator = generator;
            this.randomState = randomState;
            this.state = state;
            this.structure = structure;
            this.origin = origin;
            this.originChunkX = SectionPos.blockToSectionCoord(origin.getX());
            this.originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());
            this.maxBuildHeight = level.getMaxBuildHeight();
            this.seed = level.getSeed();
        }

        /** 第 ring 圈（切比雪夫距离）里最近的一个矿簇；这一圈没有就返回 null。 */
        BlockPos ring(int ring) {
            BlockPos best = null;
            // 上下两条边。
            for (int dk = -ring; dk <= ring; dk++) {
                best = closer(best, evaluate(-ring, dk));
                if (ring > 0) best = closer(best, evaluate(ring, dk));
            }
            // 左右两条边（四个角上面已经算过）。
            for (int dj = -ring + 1; dj <= ring - 1; dj++) {
                best = closer(best, evaluate(dj, -ring));
                if (ring > 0) best = closer(best, evaluate(dj, ring));
            }
            return best;
        }

        private BlockPos closer(BlockPos best, BlockPos candidate) {
            if (candidate == null) return best;
            if (best == null) return candidate;
            return origin.distSqr(candidate) < origin.distSqr(best) ? candidate : best;
        }

        /**
         * 试一个网格格子：先按结构网格算出这一格的候选区块，再过三道筛
         * （放置规则 → 群系噪声 → 结构自己的精确判定）。
         */
        private BlockPos evaluate(int dj, int dk) {
            int gridX = originChunkX + spread.spacing() * dj;
            int gridZ = originChunkZ + spread.spacing() * dk;
            ChunkPos candidate = spread.getPotentialStructureChunk(seed, gridX, gridZ);
            if (!spread.isStructureChunk(state, candidate.x, candidate.z)) return null;
            int blockX = candidate.getMiddleBlockX();
            int blockZ = candidate.getMiddleBlockZ();
            if (!OpenAirManaCrystalClusterStructure.passesCheapGate(
                    generator.getBiomeSource(), randomState.sampler(), seed, blockX, blockZ, maxBuildHeight)) {
                return null;
            }
            // 精确复核：和真正生成时走的是同一段代码，所以判定结果不会跟实地对不上。
            Structure.GenerationContext context = new Structure.GenerationContext(
                    level.registryAccess(), generator, generator.getBiomeSource(), randomState,
                    level.getServer().getStructureManager(), seed, candidate, level,
                    structure.biomes()::contains);
            return structure.findValidGenerationPoint(context).map(Structure.GenerationStub::position).orElse(null);
        }
    }

    private CrystalClusterLocator() {
    }
}
