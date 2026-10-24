package cn.blockforge.generated.minecraft1211n422a;

import java.util.Arrays;

import static cn.blockforge.generated.minecraft1211n422a.GoblinCampLayout.Size;

/**
 * 营地选址的地形门槛：只读高度数组，不依赖任何 Minecraft 类，离线脚本能直接跑同一份代码。
 *
 * <p>「开新档连着七八个都是小型」就是这里筛掉的。采样点固定 9×9，但占地越大、采样间距越宽
 * （小型 6 格、大型 10 格），同一座小山包在大型营地里量出来的相邻高差和总高差都更大，
 * 中大型于是被同一套容差反复拒掉，通过率被压到个位数，玩家只会看到小型。</p>
 *
 * <p>两处改动：容差按档位放大；总高差改成分位数高差（掐掉最高、最低各 10% 的采样点），
 * 一个山头或一个坑不再一票否决整块地，真正崎岖的地形照样算不过。</p>
 */
final class GoblinCampTerrainGate {
    private GoblinCampTerrainGate() {}

    /** 允许的分位数总高差：小型 8 格，每大一档多 2 格。 */
    static int relief(Size size) { return 8 + 2 * size.ordinal(); }

    /** 相邻采样点之间允许的高差：小型 3 格，每大一档多 1 格（采样间距也是按档变宽的）。 */
    static int step(Size size) { return 3 + size.ordinal(); }

    /** 允许落在水面以下的采样点数量；营地自己会补土整平，几个小水洼不影响。 */
    static int wetLimit(Size size) { return 2 + size.ordinal(); }

    /** 掐头去尾的比例：81 个采样点里各去掉 8 个。 */
    private static final double TRIM = 0.10;

    /**
     * @param heights    9×9 采样点的地表方块高度，按 {@code gz * grid + gx} 排列
     * @param wetSamples 水面（或水淹）采样点数量
     */
    static boolean accepts(int[] heights, int grid, int wetSamples, Size size) {
        if (grid < 2 || heights.length != grid * grid) return false;
        if (wetSamples > wetLimit(size)) return false;
        int step = step(size);
        for (int i = 0; i < heights.length; i++) {
            if (i % grid > 0 && Math.abs(heights[i] - heights[i - 1]) > step) return false;
            if (i >= grid && Math.abs(heights[i] - heights[i - grid]) > step) return false;
        }
        return spread(heights) <= relief(size);
    }

    /** 掐掉最高、最低各 10% 之后的高差。 */
    static int spread(int[] heights) {
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        int trim = (int)Math.floor(sorted.length * TRIM);
        return sorted[sorted.length - 1 - trim] - sorted[trim];
    }
}
