package cn.blockforge.generated.minecraft1211n422a;

/** 无游戏依赖的时间轴；运行时和离屏预览调用完全相同的公式，不累加帧状态。 */
public final class ArcaneMotion {
    public static final int TEXTURE_SIZE = 64;
    public static final long SIGIL_LIFE_MS = 8000L;
    public static final long SIGIL_CYCLE_MS = SIGIL_LIFE_MS * 3L;
    public static final long CONDUIT_PERIOD_MS = 1500L;
    public static final float RING_ALPHA = 0.09F;
    public static final float CORE_ALPHA = 0.06F;
    private static final float[] X = {0.27F, 0.75F, 0.52F};
    private static final float[] Y = {0.28F, 0.33F, 0.73F};
    private ArcaneMotion() { }
    public record Sigil(float x, float y, float size, float angle, float life) { }
    public record Flame(float width, float height, float alpha) { }

    public static float clamp(float n) { return Math.max(0.0F, Math.min(1.0F, n)); }
    public static float sine(float n) { return (float) (0.5 - 0.5 * Math.cos(Math.PI * clamp(n))); }

    public static Sigil sigil(long millis, int index, int width, int height) {
        long age = Math.floorMod(millis - index * SIGIL_LIFE_MS, SIGIL_CYCLE_MS);
        float phase = age / (float) SIGIL_LIFE_MS;
        float life = age >= SIGIL_LIFE_MS ? 0.0F
                : phase < 0.25F ? sine(phase / 0.25F)
                : phase > 0.75F ? sine((1.0F - phase) / 0.25F) : 1.0F;
        float eased = sine(phase);
        float size = Math.min(width, height) * (0.44F + 0.02F * eased);
        return new Sigil(width * X[index], height * Y[index], size, index * 0.65F + eased * 0.60F, life);
    }

    public static Flame flame(long millis, float fuel, float availableHeight) {
        double seconds = Math.floorMod(millis, 60000L) / 1000.0;
        float p = clamp(fuel);
        float flicker = (float) (0.96 + 0.04 * Math.sin(seconds * 6.2) * Math.cos(seconds * 3.8 + 0.7));
        float h = Math.max(0, availableHeight) * (0.72F + 0.28F * (float) Math.sqrt(p)) * flicker;
        return new Flame(15.0F * (0.98F + 0.02F * flicker), h, sine(p / 0.12F) * (0.65F + 0.25F * p) * flicker);
    }

    public static float spark(long millis) {
        return Math.floorMod(millis, CONDUIT_PERIOD_MS) / (float) CONDUIT_PERIOD_MS;
    }
}
