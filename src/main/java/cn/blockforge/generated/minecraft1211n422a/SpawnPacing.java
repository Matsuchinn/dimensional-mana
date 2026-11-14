package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组刷怪的「节奏」：开局爬坡、整体倍率、离玩家多远才刷。
 *
 * <p>玩家反馈的三件事都收在这里，全部可以在 {@code dimensional_mana-spawns.toml} 里调：</p>
 * <ol>
 *   <li><b>开局刷得少</b>：参考原版的区域难度，进存档头几天只刷一小部分，
 *       玩满 {@code full_strength_days} 天（r107 起默认 3 天）线性涨回正常量。
 *       第一天就是「新手还在砍树」的那几格。<b>魔素浓度高于 12 的地方不吃这条曲线</b>
 *       ——魔晶洞穴、矿簇、祭坛这些高危区从第一天起就是满强度。</li>
 *   <li><b>总量偏多</b>：元素史莱姆和哥布林巡逻各有一个总倍率，直接乘在原来的概率上，
 *       和每种生物自己的权重是相乘关系。r107 起默认全是 1.0（模组本来的量），
 *       要压数量由玩家自己往下调。</li>
 *   <li><b>刷得太近</b>：落点从「玩家周围一个圆环」里挑，默认至少 28 格才刷史莱姆、
 *       40 格才刷哥布林巡逻队，最远 128 格——房顶、院子、刚出门的脚边都不再出怪。</li>
 * </ol>
 *
 * <p>爬坡只作用于模组自己的环境刷怪；结构（营地、矿簇、祭坛）、刷怪蛋、指令与法术召唤不受影响。</p>
 */
public final class SpawnPacing {
    /** 世界开服后多少刻算一天（原版的日夜周期）。 */
    public static final long TICKS_PER_DAY = 24_000L;

    /**
     * 魔素浓度高于这个值的地方不走开局爬坡。
     *
     * <p>12.00 正好是紫水晶晶洞的浓度下限，也是「魔晶洞穴（15）、古代祭坛（+1）、
     * 露天魔晶矿簇（+4）、蚀魔荒原（13.5）」这些高危区共同的下界；普通荒野一般只有个位数。</p>
     */
    public static final double CURVE_IMMUNE_CONCENTRATION = 12.0;

    private SpawnPacing() { }

    // ------------------------------------------------------------ 开局爬坡

    /** 爬坡是否开着；配置还没加载时按开启处理。 */
    public static boolean curveEnabled() {
        return !loaded() || SpawnConfig.PACING_ENABLED.get();
    }

    /** 这个世界玩到第几天（向下取整）。 */
    public static long daysPlayed(ServerLevel level) {
        return Math.max(0L, level.getDayTime()) / TICKS_PER_DAY;
    }

    /**
     * 当前世界的刷怪强度倍率：第 1 天 = {@code start_scale}，
     * 之后每天线性增长，第 {@code full_strength_days} 天达到 1.0。
     */
    public static float intensity(ServerLevel level) {
        if (!curveEnabled()) return 1.0F;
        float start = clamp01(doubleValue(SpawnConfig.PACING_START_SCALE, SpawnConfig.DEFAULT_PACING_START));
        int fullDays = intValue(SpawnConfig.PACING_FULL_STRENGTH_DAYS, SpawnConfig.DEFAULT_PACING_FULL_DAYS);
        float progress = fullDays <= 0
                ? 1.0F
                : Math.min(1.0F, daysPlayed(level) / (float) fullDays);
        return start + (1.0F - start) * progress;
    }

    /**
     * 某个位置这一次刷怪的强度倍率：<b>魔素浓度高于
     * {@value #CURVE_IMMUNE_CONCENTRATION} 的地方不吃开局爬坡</b>，恒按满强度算。
     *
     * <p>魔晶洞穴、露天魔晶矿簇、古代祭坛、紫水晶晶洞附近本来就该是危险区，进存档第一天也照样凶；
     * 如果这些地方也被「新手保护期」压成三成，玩家挖到矿脉反而空荡荡的，等于把高浓度当成了安全区，
     * 和世界设定正好相反。普通荒野才走天数曲线。</p>
     */
    public static float intensityAt(ServerLevel level, BlockPos pos) {
        if (!curveEnabled()) return 1.0F;
        if (pos != null && ManaConcentration.at(level, pos) > CURVE_IMMUNE_CONCENTRATION) return 1.0F;
        return intensity(level);
    }

    // ------------------------------------------------------------ 整体倍率

    /** 元素史莱姆总量倍率。 */
    public static float elementalRate() {
        return Math.max(0.0F, doubleValue(SpawnConfig.RATE_ELEMENTAL_SLIME, SpawnConfig.DEFAULT_RATE));
    }

    /** 哥布林巡逻队总量倍率。 */
    public static float goblinRate() {
        return Math.max(0.0F, doubleValue(SpawnConfig.RATE_GOBLIN_PATROL, SpawnConfig.DEFAULT_RATE));
    }

    /** 蚀魔荒原白天补刷总量倍率。 */
    public static float wastelandRate() {
        return Math.max(0.0F, doubleValue(SpawnConfig.RATE_WASTELAND, SpawnConfig.DEFAULT_RATE));
    }

    /** 爬坡 × 某一套系统的倍率；给要自己算概率的地方用。 */
    public static float multiplier(ServerLevel level, float rate) {
        return intensity(level) * Math.max(0.0F, rate);
    }

    /** 同上，但按「这一次刷怪发生在哪」算：高魔素区域不吃爬坡。 */
    public static float multiplier(ServerLevel level, float rate, BlockPos pos) {
        return intensityAt(level, pos) * Math.max(0.0F, rate);
    }

    /**
     * 按「基础概率 × 爬坡强度 × 系统倍率」抽一次。
     *
     * <p>倍率调到 0 时直接返回 false（那一套系统彻底不刷）。</p>
     */
    public static boolean roll(ServerLevel level, RandomSource random, float baseChance, float rate) {
        return roll(level, random, baseChance, rate, null);
    }

    /**
     * 同上，但把「这次刷怪发生在哪」传进来：该位置魔素浓度高于
     * {@value #CURVE_IMMUNE_CONCENTRATION} 时不乘开局爬坡，永远按满强度抽。
     *
     * <p>{@code pos} 传 {@code null} 等于走普通曲线。</p>
     */
    public static boolean roll(ServerLevel level, RandomSource random, float baseChance, float rate, BlockPos pos) {
        if (baseChance <= 0.0F || rate <= 0.0F) return false;
        float chance = baseChance * intensityAt(level, pos) * rate;
        if (chance <= 0.0F) return false;
        return random.nextFloat() < Math.min(1.0F, chance);
    }

    // ------------------------------------------------------------ 刷怪距离

    /**
     * 在「最小距离～最大距离」的环带里抽一个半径。
     *
     * <p>最大距离比最小距离还小时按最小距离算，不会出现负数半径。</p>
     */
    public static double pickDistance(RandomSource random, double min, double max) {
        double low = Math.max(0.0D, min);
        double high = Math.max(low, max);
        if (high <= low) return low;
        return low + random.nextDouble() * (high - low);
    }

    /** 元素史莱姆离玩家的最小水平距离。 */
    public static double slimeMinDistance() {
        return Math.max(0.0D, doubleValue(SpawnConfig.SLIME_MIN_DISTANCE, SpawnConfig.DEFAULT_SLIME_MIN));
    }

    /** 元素史莱姆落点的最大水平距离。 */
    public static double slimeMaxDistance() {
        return Math.max(slimeMinDistance(),
                doubleValue(SpawnConfig.SLIME_MAX_DISTANCE, SpawnConfig.DEFAULT_SLIME_MAX));
    }

    /** 哥布林巡逻队离玩家的最小水平距离。 */
    public static double goblinMinDistance() {
        return Math.max(0.0D, doubleValue(SpawnConfig.GOBLIN_MIN_DISTANCE, SpawnConfig.DEFAULT_GOBLIN_MIN));
    }

    /** 哥布林巡逻队落点的最大水平距离。 */
    public static double goblinMaxDistance() {
        return Math.max(goblinMinDistance(),
                doubleValue(SpawnConfig.GOBLIN_MAX_DISTANCE, SpawnConfig.DEFAULT_GOBLIN_MAX));
    }

    // ------------------------------------------------------------ 配置读取

    private static boolean loaded() {
        return SpawnConfig.SPEC.isLoaded();
    }

    private static float doubleValue(ModConfigSpec.DoubleValue value, double fallback) {
        return loaded() ? (float) (double) value.get() : (float) fallback;
    }

    private static int intValue(ModConfigSpec.IntValue value, int fallback) {
        return loaded() ? value.get() : fallback;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
