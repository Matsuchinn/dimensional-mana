package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 「大型蚀魔荒原」盖层：把原来的多噪声群系源整个包住，在地表及以上的高度先问一句——
 * 这里是不是落在某一块<b>大型</b>荒原团里？是就直接给荒原群系；不是才交给原来的噪声去挑。
 *
 * <p>多噪声参数表天生只能切「碎片」：温度、湿度、怪异度这些场互相交叠，切片出来就是
 * 满地小斑块（玩家反馈蚀魔荒原全是碎片化出现）。团块的大小和位置靠参数切片是切不出
 * 「保证有、保证隔够」的，所以这层用<b>确定性的网格布局</b>自己算：</p>
 *
 * <ul>
 *   <li>世界平面按 {@code cell}（默认 36000 格）划格，每格掷一次「有 / 没有」
 *     （掷骰子的种子来自<b>本世界的噪声场</b>在同一格点上的取值，换张图就是另一套布局，
 *     同一张图永远同一套）；有则团心落在格内带 {@code jitter} 抖动的点上；</li>
 *   <li>每块团一个半径（{@code minRadius}~{@code maxRadius}，默认 1500~3500，
 *     直径 3~7 千格——这就是「大型」），边缘再加两道正弦起伏，边界不圆得假；</li>
 *   <li>相邻团心距离最少 {@code cell - 2*jitter} = 22000 格，通常 36000~51000 格，
 *     符合「大型与大型之间通常至少 2~5 万格」；</li>
 *   <li><b>出生点保底</b>：额外强制一块团，团心在离世界原点 10000~20000 格的环上
 *     （方向由本世界噪声掷出）。玩家出生点正常就在原点附近，所以「出生点周围 1~2 万格
 *     必有一块大型蚀魔荒原」是构造上保证的；落在保底团 2 万格内的网格团会被撤掉，
 *     免得和保底团挤在一起；</li>
 *   <li>只在地表附近生效：噪声格的方块高度 ≥ {@code minBlockY}（默认 62，海平面一线）
 *     才盖——洞穴、深层域名照常走原来的深度参数，不会被整层刷成荒原。</li>
 * </ul>
 *
 * <p>性能：每格团心只做一次哈希 + 缓存（线程内 LRU），一次查询最多翻 5×5 个格，
 * 全部走「平方距离粗筛 → 边缘环带才算反正切」的路子，比多噪声本身便宜。</p>
 */
public final class WastelandOverlayBiomeSource extends BiomeSource {
    public static final MapCodec<WastelandOverlayBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("base").forGetter(s -> s.base),
                    Biome.LIST_CODEC.fieldOf("large").forGetter(s -> s.large),
                    Codec.INT.optionalFieldOf("cell", 36000).forGetter(s -> s.cell),
                    Codec.INT.optionalFieldOf("jitter", 7000).forGetter(s -> s.jitter),
                    Codec.doubleRange(0.0, 1.0).optionalFieldOf("presence", 0.6)
                            .forGetter(s -> s.presence),
                    Codec.INT.optionalFieldOf("min_radius", 1500).forGetter(s -> s.minRadius),
                    Codec.INT.optionalFieldOf("max_radius", 3500).forGetter(s -> s.maxRadius),
                    Codec.INT.optionalFieldOf("min_block_y", 62).forGetter(s -> s.minBlockY),
                    Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("guaranteed_min", 10000)
                            .forGetter(s -> s.guaranteedMin),
                    Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("guaranteed_max", 20000)
                            .forGetter(s -> s.guaranteedMax),
                    Codec.INT.optionalFieldOf("guaranteed_min_radius", 1500).forGetter(s -> s.guaranteedMinR),
                    Codec.INT.optionalFieldOf("guaranteed_max_radius", 3000).forGetter(s -> s.guaranteedMaxR)
            ).apply(instance, WastelandOverlayBiomeSource::new));

    /** 团心离保底团心多近就撤（保证「大型之间至少 2 万格」在保底团这里也成立）。 */
    private static final int GUARANTEED_KEEPOUT = 20000;

    private final BiomeSource base;
    private final net.minecraft.core.HolderSet<Biome> large;
    private final int cell;
    private final int jitter;
    private final double presence;
    private final int minRadius;
    private final int maxRadius;
    private final int minBlockY;
    private final int guaranteedMin;
    private final int guaranteedMax;
    private final int guaranteedMinR;
    private final int guaranteedMaxR;
    /** 从 large 里定死取第一个群系当覆盖群系（延迟解析，解码时注册表可能还没齐）。 */
    private volatile Holder<Biome> overlayBiome;

    /**
     * 团心缓存：生成是按区块推进的，同一批团心会被反复查到，命中率极高。
     * 每个实例（≈每个世界）一份 + 每线程一份——不同世界噪声种子不同，缓存绝不能串。
     */
    private final ThreadLocal<java.util.Map<Long, Cell>> cellsByThread =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Cell> eldest) { return size() > 512; }
            });

    public WastelandOverlayBiomeSource(BiomeSource base, net.minecraft.core.HolderSet<Biome> large,
                                       int cell, int jitter, double presence, int minRadius, int maxRadius,
                                       int minBlockY, int guaranteedMin, int guaranteedMax,
                                       int guaranteedMinR, int guaranteedMaxR) {
        this.base = base;
        this.large = large;
        this.cell = Math.max(1000, cell);
        this.jitter = Math.max(0, Math.min(jitter, this.cell / 2 - 100));
        this.presence = presence;
        this.minRadius = Math.max(100, minRadius);
        this.maxRadius = Math.max(this.minRadius, maxRadius);
        this.minBlockY = minBlockY;
        this.guaranteedMin = Math.max(0, guaranteedMin);
        this.guaranteedMax = Math.max(this.guaranteedMin, guaranteedMax);
        this.guaranteedMinR = Math.max(100, guaranteedMinR);
        this.guaranteedMaxR = Math.max(this.guaranteedMinR, guaranteedMaxR);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        // base 的 collectPossibleBiomes 是 protected，外部类型调不到；公开的 possibleBiomes() 是它的缓存结果。
        return Stream.concat(base.possibleBiomes().stream(), large.stream());
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        if (quartY * 4 >= minBlockY && overLargeRegion(quartX * 4, quartZ * 4, sampler)) {
            return overlayBiome(sampler);
        }
        return base.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }

    // ---------------------------------------------------------------- 团块布局

    /** 一个候选团心：坐标、半径、边缘起伏相位，全由「本世界噪声在这个格点上的取值」定死。 */
    private record Cell(boolean present, double cx, double cz, double radius, double phase1, double phase2) {
        static final Cell MISSING = new Cell(false, 0, 0, 0, 0, 0);
    }

    private boolean overLargeRegion(int worldX, int worldZ, Climate.Sampler sampler) {
        // 保底团先查：它就 1 个，命中率最高的一刀。
        Cell home = guaranteedCenter(sampler);
        if (inside(home, worldX, worldZ)) return true;
        int cx = Math.floorDiv(worldX, cell);
        int cz = Math.floorDiv(worldZ, cell);
        // 团心到本块边缘最远距离 = cell/2 + jitter + 半径，小于一个 cell 宽：
        // 3×3 邻域在默认参数下数学上已经足够，再远的格不可能够到这一点。
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (inside(cellOf(cx + dx, cz + dz, sampler), worldX, worldZ)) return true;
            }
        }
        return false;
    }

    /** 平方距离粗筛，只有贴着起伏边缘的那一小圈才付反正切的钱。 */
    private static boolean inside(Cell c, int worldX, int worldZ) {
        if (!c.present()) return false;
        double dx = worldX - c.cx();
        double dz = worldZ - c.cz();
        double dist2 = dx * dx + dz * dz;
        double lo = c.radius() * 0.82D, hi = c.radius() * 1.18D;
        if (dist2 < lo * lo) return true;
        if (dist2 > hi * hi) return false;
        double angle = Math.atan2(dz, dx);
        double wobbled = c.radius() * (1.0D
                + 0.10D * Math.sin(3.0D * angle + c.phase1())
                + 0.06D * Math.sin(5.0D * angle + c.phase2()));
        return dist2 < wobbled * wobbled;
    }

    private Cell cellOf(int cx, int cz, Climate.Sampler sampler) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL) ^ 0x5A5A00000000L;
        Cell cached = cellsByThread.get().get(key);
        if (cached != null) return cached;
        Cell made = computeCell(cx, cz, sampler);
        cellsByThread.get().put(key, made);
        return made;
    }

    private Cell computeCell(int cx, int cz, Climate.Sampler sampler) {
        Climate.TargetPoint at = sampler.sample(cx * cell, 64, cz * cell);
        long hash = hash(at) ^ (0x9E3779B185EBCA87L * (cx + 1) + 0xC2B2AE3D27D4EB4FL * (cz + 1));
        double roll = unsignedUnit(hash);
        if (roll >= presence) return Cell.MISSING;
        double jx = jitter * (unsignedUnit(hash ^ 0xBF58476D1CE4E5B9L) * 2.0 - 1.0);
        double jz = jitter * (unsignedUnit(hash ^ 0x94D049BB133111EBL) * 2.0 - 1.0);
        double radius = minRadius + unsignedUnit(hash ^ 0xD6E8FEB86659FD93L) * (maxRadius - minRadius);
        double p1 = unsignedUnit(hash ^ 0xA0761D6478BD642FL) * Math.PI * 2.0;
        double p2 = unsignedUnit(hash ^ 0xE7037ED1A0B428DBL) * Math.PI * 2.0;
        double cxWorld = (cx + 0.5) * cell + jx;
        double czWorld = (cz + 0.5) * cell + jz;
        // 和出生点保底团挤在一起的网格团撤掉，保「大型之间至少 2 万格」。
        Cell home = guaranteedCenter(sampler);
        double gdx = cxWorld - home.cx(), gdz = czWorld - home.cz();
        if (home.present() && gdx * gdx + gdz * gdz < (long) GUARANTEED_KEEPOUT * GUARANTEED_KEEPOUT) {
            return Cell.MISSING;
        }
        return new Cell(true, cxWorld, czWorld, radius, p1, p2);
    }

    /**
     * 出生点保底团：团心在离原点 {@code guaranteedMin~guaranteedMax} 的环上，
     * 方向和大小由本世界噪声定。玩家出生点正常就在原点附近（±几百格），
     * 所以这块团必然在出生点 1~2 万格上下。缓存固定 key，一次世界一次哈希。
     */
    private Cell guaranteedCenter(Climate.Sampler sampler) {
        long key = -1L; // -1 不可能被真实格 key 撞上（真实 key 都混了 <<32 的格坐标）
        Cell cached = cellsByThread.get().get(key);
        if (cached != null) return cached;
        Climate.TargetPoint at = sampler.sample(0, 64, 0);
        long hash = hash(at) ^ 0x7A57F384L;
        double angle = unsignedUnit(hash) * Math.PI * 2.0;
        double dist = guaranteedMin + unsignedUnit(hash ^ 0x2545F4914F6CDD1DL) * (guaranteedMax - guaranteedMin);
        double radius = guaranteedMinR + unsignedUnit(hash ^ 0x4C49423F1F5A2F1DL) * (guaranteedMaxR - guaranteedMinR);
        Cell made = new Cell(true, Math.cos(angle) * dist, Math.sin(angle) * dist, radius,
                unsignedUnit(hash ^ 0x1F123BB5L) * Math.PI * 2.0, unsignedUnit(hash ^ 0x7F4A52D5L) * Math.PI * 2.0);
        cellsByThread.get().put(key, made);
        return made;
    }

    // ---------------------------------------------------------------- 工具

    /** 把噪声场的几个分量搅成一个 long——同世界同格点永远同一个数。 */
    private static long hash(Climate.TargetPoint p) {
        long h = 0x9E3779B97F4A7C15L;
        h = mix(h, p.temperature());
        h = mix(h, p.humidity());
        h = mix(h, p.continentalness());
        h = mix(h, p.erosion());
        return h;
    }

    private static long mix(long h, long v) {
        h ^= v + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
        return h;
    }

    /** 无符号归一化到 [0,1)。 */
    private static double unsignedUnit(long v) {
        return Double.longBitsToDouble((v & 0x000FFFFFFFFFFFFFL) | 0x3FF0000000000000L) - 1.0;
    }

    private Holder<Biome> overlayBiome(Climate.Sampler sampler) {
        Holder<Biome> holder = overlayBiome;
        if (holder == null) {
            Iterator<Holder<Biome>> it = large.stream().iterator();
            holder = it.hasNext() ? it.next() : base.getNoiseBiome(0, 64, 0, sampler);
            overlayBiome = holder;
        }
        return holder;
    }
}
