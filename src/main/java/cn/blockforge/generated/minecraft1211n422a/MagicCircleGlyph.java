package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayList;
import java.util.List;

/**
 * 图案模板与识别，对应设计文档 5.2 / 5.3 节。
 *
 * <p>服务端把吸附后的格点链压成 10 个特征（闭合性、环数、交叉、端点、对称、
 * 长宽比、填充率、方向熵、斜向占比、方向种类），再和下面的模板做加权比对，
 * 取相似度最高的 3 个候选。
 * 全部候选都低于 {@link #MIN_SCORE} 时界面会走「手动框选」降级。</p>
 */
public enum MagicCircleGlyph {
    CIRCLE("circle", MagicCircleBlockType.AREA, "圆",
            1.0, 0.5, 0.0, 0.0, 1.0, 0.95, 0.20, 0.85, 0.30, 1.0),
    DOUBLE_CIRCLE("double_circle", MagicCircleBlockType.CORE, "双圆",
            1.0, 1.0, 0.5, 0.0, 1.0, 1.0, 0.25, 0.90, 0.25, 1.0),
    TRIANGLE("triangle", MagicCircleBlockType.OUTPUT, "三角",
            1.0, 0.5, 0.0, 0.0, 1.0, 0.85, 0.15, 0.70, 0.40, 0.60),
    SQUARE("square", MagicCircleBlockType.STABILIZE, "方",
            1.0, 0.5, 0.0, 0.0, 1.0, 1.0, 0.20, 0.65, 0.0, 0.5),
    LINE("line", MagicCircleBlockType.LINK, "直线",
            0.0, 0.0, 0.0, 0.5, 1.0, 0.05, 1.0, 0.0, 0.0, 0.125),
    ARROW("arrow", MagicCircleBlockType.DIRECTION, "箭头",
            0.0, 0.0, 0.0, 0.5, 0.50, 0.60, 0.40, 0.30, 0.60, 0.5),
    CROSS("cross", MagicCircleBlockType.TRIGGER, "叉",
            0.0, 0.5, 0.5, 0.5, 1.0, 1.0, 0.30, 0.50, 0.70, 0.40),
    SPIRAL("spiral", MagicCircleBlockType.DURATION, "螺旋",
            0.0, 0.0, 0.0, 0.5, 0.50, 1.0, 0.20, 0.95, 0.40, 1.0),
    STAR("star", MagicCircleBlockType.OUTPUT, "星",
            1.0, 0.5, 1.0, 0.0, 1.0, 0.95, 0.20, 0.95, 0.40, 1.0),
    ARC("arc", MagicCircleBlockType.FILTER, "弧",
            0.0, 0.0, 0.0, 0.5, 0.50, 0.55, 0.20, 0.75, 0.45, 0.65),
    WAVE("wave", MagicCircleBlockType.AMPLIFY, "波浪",
            0.0, 0.0, 0.0, 0.5, 0.50, 0.45, 0.20, 0.70, 0.60, 0.60),
    DIAMOND("diamond", MagicCircleBlockType.CONDITION, "菱形",
            1.0, 0.5, 0.0, 0.0, 1.0, 1.0, 0.15, 0.65, 1.0, 0.5),
    EYE("eye", MagicCircleBlockType.SUPPLY, "眼睛形",
            1.0, 0.5, 0.0, 0.0, 1.0, 0.40, 0.25, 0.85, 0.60, 1.0);

    /** 候选最低相似度：低于它就不显示，改用「手动框选」。 */
    public static final double MIN_SCORE = 0.55;
    /** 特征权重：闭合、环、交叉、端点、对称、长宽比、填充率、方向熵、斜向占比、方向种类。 */
    private static final double[] WEIGHTS = {1.5, 2.0, 1.5, 1.5, 0.5, 1.2, 0.4, 1.2, 1.2, 1.5};

    private final String id;
    private final MagicCircleBlockType blockType;
    private final String fallbackName;
    private final double[] expected;

    MagicCircleGlyph(String id, MagicCircleBlockType blockType, String fallbackName,
                     double closedness, double loops, double crossings, double endpoints,
                     double symmetry, double aspect, double fill, double entropy, double diagonal,
                     double directionKinds) {
        this.id = id;
        this.blockType = blockType;
        this.fallbackName = fallbackName;
        this.expected = new double[]{closedness, loops, crossings, endpoints, symmetry, aspect, fill, entropy,
                diagonal, directionKinds};
    }

    public String id() { return id; }
    public MagicCircleBlockType blockType() { return blockType; }
    public String fallbackName() { return fallbackName; }
    public String translationKey() { return "gui.dimensional_mana.magiccircle.glyph." + id; }

    public static MagicCircleGlyph byId(String id) {
        if (id == null) return null;
        for (MagicCircleGlyph glyph : values()) if (glyph.id.equals(id)) return glyph;
        return null;
    }

    /** 一次识别的一个候选。 */
    public record Match(MagicCircleGlyph glyph, double score) {
        public String label() { return glyph.fallbackName(); }
    }

    /**
     * 在格点链上做特征提取 + 模板比对，返回最多 3 个相似度 &gt; 0.55 的候选。
     */
    public static List<Match> recognize(List<MagicCircleStroke.Point> points, int canvasWidth, int canvasHeight) {
        if (points == null || points.size() < 2) return List.of();
        double[] observed = extract(points, canvasWidth, canvasHeight);
        List<Match> matches = new ArrayList<>();
        for (MagicCircleGlyph glyph : values()) {
            double score = similarity(observed, glyph.expected);
            if (score >= MIN_SCORE) matches.add(new Match(glyph, score));
        }
        matches.sort((a, b) -> Double.compare(b.score(), a.score()));
        return matches.size() > 3 ? List.copyOf(matches.subList(0, 3)) : List.copyOf(matches);
    }

    private static double similarity(double[] observed, double[] expected) {
        double weighted = 0.0;
        double total = 0.0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            weighted += WEIGHTS[i] * (1.0 - Math.min(1.0, Math.abs(observed[i] - expected[i])));
            total += WEIGHTS[i];
        }
        return total <= 0.0 ? 0.0 : weighted / total;
    }

    /**
     * 8 个特征，全部归一到 0..1。
     */
    public static double[] extract(List<MagicCircleStroke.Point> points, int canvasWidth, int canvasHeight) {
        // 去掉连续重复点，但保留原始顺序；同一个格点被走到两次（回折/自交）也要算进度数。
        List<MagicCircleStroke.Point> chain = new ArrayList<>();
        for (MagicCircleStroke.Point point : points) {
            if (chain.isEmpty() || !chain.get(chain.size() - 1).equals(point)) chain.add(point);
        }
        if (chain.size() < 2) return new double[]{0, 0, 0, 0, 1, 1, 0, 0};

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        // 用 LinkedHashSet 去重：玩家在 64×64 画布上画满时点数上千，List.contains 会变成 O(n²)。
        java.util.LinkedHashSet<MagicCircleStroke.Point> uniqueSet = new java.util.LinkedHashSet<>();
        for (MagicCircleStroke.Point point : chain) {
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
            uniqueSet.add(point);
        }
        List<MagicCircleStroke.Point> unique = new ArrayList<>(uniqueSet);
        int width = Math.max(1, maxX - minX + 1);
        int height = Math.max(1, maxY - minY + 1);

        // 链上相邻点构成的边；首尾相邻再补一条闭合边。
        List<long[]> edges = new ArrayList<>();
        java.util.Map<MagicCircleStroke.Point, Integer> degrees = new java.util.HashMap<>();
        for (MagicCircleStroke.Point point : unique) degrees.put(point, 0);
        for (int i = 1; i < chain.size(); i++) {
            MagicCircleStroke.Point a = chain.get(i - 1);
            MagicCircleStroke.Point b = chain.get(i);
            if (a.equals(b)) continue;
            edges.add(new long[]{a.x(), a.y(), b.x(), b.y()});
            degrees.merge(a, 1, Integer::sum);
            degrees.merge(b, 1, Integer::sum);
        }
        MagicCircleStroke.Point first = chain.get(0);
        MagicCircleStroke.Point last = chain.get(chain.size() - 1);
        // 首尾重合（一笔画回原点）也算闭合；这时不需要再补一条边，环已经在链里了。
        boolean closed = first.equals(last) || first.adjacent(last);
        if (closed && !first.equals(last)) {
            edges.add(new long[]{last.x(), last.y(), first.x(), first.y()});
            degrees.merge(first, 1, Integer::sum);
            degrees.merge(last, 1, Integer::sum);
        }

        int endpoints = 0;
        int crossings = 0;
        for (int degree : degrees.values()) {
            if (degree == 1) endpoints++;
            if (degree >= 3) crossings++;
        }
        int vertices = unique.size();
        int loops = Math.max(0, edges.size() - vertices + components(chain, unique));

        double closedness = closed ? 1.0 : 0.0;
        double loopScore = Math.min(1.0, loops / 2.0);
        double crossingScore = Math.min(1.0, crossings / 2.0);
        double endpointScore = Math.min(1.0, endpoints / 4.0);
        double symmetry = symmetry(unique, minX, minY, width, height);
        double aspect = (double) Math.min(width, height) / Math.max(width, height);
        double fill = Math.min(1.0, (double) vertices / (width * height));
        double entropy = directionEntropy(chain);

        return new double[]{closedness, loopScore, crossingScore, endpointScore, symmetry, aspect, fill, entropy,
                diagonalRatio(chain), directionKinds(chain) / 8.0};
    }

    /** 顺序链被切成几段（正常一笔画是 1）。 */
    private static int components(List<MagicCircleStroke.Point> chain, List<MagicCircleStroke.Point> unique) {
        java.util.Map<MagicCircleStroke.Point, Integer> index = new java.util.HashMap<>();
        for (int i = 0; i < unique.size(); i++) index.put(unique.get(i), i);
        int[] parent = new int[unique.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (int i = 1; i < chain.size(); i++) {
            int a = index.getOrDefault(chain.get(i - 1), -1);
            int b = index.getOrDefault(chain.get(i), -1);
            if (a >= 0 && b >= 0) union(parent, a, b);
        }
        java.util.Set<Integer> roots = new java.util.HashSet<>();
        for (int i = 0; i < parent.length; i++) roots.add(find(parent, i));
        return roots.size();
    }

    private static int find(int[] parent, int value) {
        int root = value;
        while (parent[root] != root) root = parent[root];
        while (parent[value] != root) {
            int next = parent[value];
            parent[value] = root;
            value = next;
        }
        return root;
    }

    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) parent[rootB] = rootA;
    }

    /** 把点栅格化后，算水平 / 垂直 / 中心三种镜像里最高的命中率。 */
    private static double symmetry(List<MagicCircleStroke.Point> unique, int minX, int minY, int width, int height) {
        boolean[][] grid = new boolean[height][width];
        for (MagicCircleStroke.Point point : unique) {
            int x = Math.min(width - 1, Math.max(0, point.x() - minX));
            int y = Math.min(height - 1, Math.max(0, point.y() - minY));
            grid[y][x] = true;
        }
        int total = unique.size();
        if (total == 0) return 0.0;
        int horizontal = 0, vertical = 0, central = 0;
        for (MagicCircleStroke.Point point : unique) {
            int x = Math.min(width - 1, Math.max(0, point.x() - minX));
            int y = Math.min(height - 1, Math.max(0, point.y() - minY));
            if (grid[y][width - 1 - x]) vertical++;
            if (grid[height - 1 - y][x]) horizontal++;
            if (grid[height - 1 - y][width - 1 - x]) central++;
        }
        return Math.max(horizontal, Math.max(vertical, central)) / (double) total;
    }

    /** 斜向（四个对角方向）步数占比：方框是 0，菱形和叉是 1，圆在中间。 */
    private static double diagonalRatio(List<MagicCircleStroke.Point> chain) {
        int moves = 0;
        int diagonal = 0;
        for (int i = 1; i < chain.size(); i++) {
            int dx = Integer.signum(chain.get(i).x() - chain.get(i - 1).x());
            int dy = Integer.signum(chain.get(i).y() - chain.get(i - 1).y());
            if (dx == 0 && dy == 0) continue;
            moves++;
            if (dx != 0 && dy != 0) diagonal++;
        }
        return moves == 0 ? 0.0 : (double) diagonal / moves;
    }

    /** 用到了几个方向（1..8）：方框只有 4 个，圆和螺旋会用满 8 个。 */
    private static int directionKinds(List<MagicCircleStroke.Point> chain) {
        boolean[] used = new boolean[8];
        for (int i = 1; i < chain.size(); i++) {
            int dx = Integer.signum(chain.get(i).x() - chain.get(i - 1).x());
            int dy = Integer.signum(chain.get(i).y() - chain.get(i - 1).y());
            if (dx == 0 && dy == 0) continue;
            used[directionIndex(dx, dy)] = true;
        }
        int kinds = 0;
        for (boolean value : used) if (value) kinds++;
        return kinds;
    }

    /** 方向序列的分布熵（0 = 直线，1 = 八个方向均匀分布）。 */
    private static double directionEntropy(List<MagicCircleStroke.Point> chain) {
        int[] histogram = new int[8];
        int moves = 0;
        for (int i = 1; i < chain.size(); i++) {
            int dx = Integer.signum(chain.get(i).x() - chain.get(i - 1).x());
            int dy = Integer.signum(chain.get(i).y() - chain.get(i - 1).y());
            if (dx == 0 && dy == 0) continue;
            histogram[directionIndex(dx, dy)]++;
            moves++;
        }
        if (moves == 0) return 0.0;
        double entropy = 0.0;
        for (int count : histogram) {
            if (count == 0) continue;
            double p = (double) count / moves;
            entropy -= p * (Math.log(p) / Math.log(2.0));
        }
        return Math.min(1.0, entropy / 3.0);
    }

    private static int directionIndex(int dx, int dy) {
        if (dx > 0 && dy == 0) return 0;
        if (dx > 0 && dy > 0) return 1;
        if (dx == 0 && dy > 0) return 2;
        if (dx < 0 && dy > 0) return 3;
        if (dx < 0 && dy == 0) return 4;
        if (dx < 0 && dy < 0) return 5;
        if (dx == 0 && dy < 0) return 6;
        return 7;
    }
}
