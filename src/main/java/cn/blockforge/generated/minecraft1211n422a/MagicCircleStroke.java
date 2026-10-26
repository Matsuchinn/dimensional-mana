package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayList;
import java.util.List;

/**
 * 绘制台笔画的客户端预处理管线，对应设计文档 5.1 节：
 *
 * <pre>
 * 原始鼠标点列
 * → 去抖（距离 &lt; 2 像素的点丢弃）
 * → 八方向吸附（只允许走到相邻 8 格，跳太远时补中间格）
 * → 网格化（裁到画布范围）
 * → Douglas-Peucker 简化（epsilon = 1 格左右）
 * → 有序格点链，发给服务端识别
 * </pre>
 *
 * <p>服务端不信客户端，收到点列后会再走一遍同样的吸附与简化，所以这里的规则
 * 客户端与服务端共用，保证「画的时候看到的线」和「识别时的线」是同一根。</p>
 */
public final class MagicCircleStroke {

    /** 画布上的一个格点。 */
    public record Point(int x, int y) {
        public boolean adjacent(Point other) {
            return Math.abs(x - other.x) <= 1 && Math.abs(y - other.y) <= 1 && !equals(other);
        }
    }

    /** 吸附结果。{@code patched} 表示鼠标跳得太远、补过中间格（界面会把线画成红色提示）。 */
    public record Snapped(List<Point> points, boolean patched) {
        public boolean isEmpty() { return points.isEmpty(); }
    }

    /** 去抖距离：小于这个像素间距的点直接丢弃，免得手抖产生一堆重复点。 */
    private static final int JITTER_DISTANCE = 2;
    /** 网络包最多收这么多点，超了二次简化。 */
    private static final int HARD_POINT_LIMIT = 2048;
    /** 单条笔画的编码长度上限，防止有人手搓畸形包。 */
    public static final int MAX_ENCODED_LENGTH = 8192;

    /** 去抖 + 八方向吸附 + 裁边。 */
    public static Snapped snap(List<Point> raw, int canvasWidth, int canvasHeight) {
        if (raw == null || raw.isEmpty()) return new Snapped(List.of(), false);
        int width = Math.max(1, canvasWidth);
        int height = Math.max(1, canvasHeight);

        // 1. 去抖：只保留与上一个保留点相距 ≥ 2 像素的点。
        List<Point> cleaned = new ArrayList<>();
        for (Point point : raw) {
            if (point == null) continue;
            Point clamped = clamp(point, width, height);
            if (!cleaned.isEmpty() && squaredDistance(cleaned.get(cleaned.size() - 1), clamped)
                    < JITTER_DISTANCE * JITTER_DISTANCE) {
                continue;
            }
            cleaned.add(clamped);
        }
        if (cleaned.isEmpty()) return new Snapped(List.of(), false);

        // 2. 八方向吸附：从当前笔尖走到目标格，只走相邻 8 格；跳太远就补中间格。
        List<Point> snapped = new ArrayList<>();
        snapped.add(cleaned.get(0));
        boolean patched = false;
        Point cursor = cleaned.get(0);
        for (int i = 1; i < cleaned.size(); i++) {
            Point target = cleaned.get(i);
            List<Point> path = walk(cursor, target);
            if (path.size() > 2) patched = true;
            for (Point step : path) {
                Point last = snapped.get(snapped.size() - 1);
                if (!step.equals(last)) snapped.add(step);
            }
            cursor = target;
        }
        return new Snapped(List.copyOf(snapped), patched);
    }

    /** 从 {@code from}（不含）走到 {@code to}（含）的一串相邻格点。 */
    public static List<Point> walk(Point from, Point to) {
        List<Point> path = new ArrayList<>();
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return path;
        for (int step = 1; step <= steps; step++) {
            int x = from.x + (int) Math.round((double) dx * step / steps);
            int y = from.y + (int) Math.round((double) dy * step / steps);
            path.add(new Point(x, y));
        }
        return path;
    }

    /**
     * Douglas-Peucker 简化：把一根「贪吃蛇像素线」压成折线，保留拐角。
     */
    public static List<Point> simplify(List<Point> points, double epsilon) {
        if (points == null || points.size() <= 2) return points == null ? List.of() : List.copyOf(points);
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifySegment(points, 0, points.size() - 1, epsilon, keep);
        List<Point> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) if (keep[i]) result.add(points.get(i));
        return List.copyOf(result);
    }

    private static void simplifySegment(List<Point> points, int start, int end, double epsilon, boolean[] keep) {
        if (end <= start + 1) return;
        Point first = points.get(start);
        Point last = points.get(end);
        double maxDistance = -1.0;
        int furthest = -1;
        for (int i = start + 1; i < end; i++) {
            double distance = distanceToSegment(points.get(i), first, last);
            if (distance > maxDistance) {
                maxDistance = distance;
                furthest = i;
            }
        }
        if (maxDistance > epsilon) {
            keep[furthest] = true;
            simplifySegment(points, start, furthest, epsilon, keep);
            simplifySegment(points, furthest, end, epsilon, keep);
        }
    }

    /** 按画布边长取简化阈值，对应文档 5.1 的表格。 */
    public static double epsilonFor(int canvasSize) {
        if (canvasSize <= 16) return 0.8;
        if (canvasSize <= 32) return 1.0;
        if (canvasSize <= 48) return 1.2;
        return 1.5;
    }

    /** 按画布边长取最大点数，对应文档 5.1 的表格。 */
    public static int maxPointsFor(int canvasSize) {
        if (canvasSize <= 16) return 64;
        if (canvasSize <= 32) return 128;
        if (canvasSize <= 48) return 192;
        return 256;
    }

    /** 一步到位的服务端预处理：吸附 → 简化 → 点数封顶。 */
    public static List<Point> prepare(List<Point> raw, int canvasWidth, int canvasHeight) {
        Snapped snapped = snap(raw, canvasWidth, canvasHeight);
        return simplifyForNetwork(snapped.points(), Math.min(canvasWidth, canvasHeight));
    }

    /** 把吸附后的完整链压到网络能收的点数以内（识别用完整链，存下来用这份）。 */
    public static List<Point> simplifyForNetwork(List<Point> snapped, int canvasSize) {
        double epsilon = epsilonFor(canvasSize);
        int limit = maxPointsFor(canvasSize);
        List<Point> simplified = simplify(snapped, epsilon);
        while (simplified.size() > limit && simplified.size() > 2) {
            epsilon *= 1.5;
            simplified = simplify(snapped, epsilon);
        }
        return simplified.size() > HARD_POINT_LIMIT
                ? List.copyOf(simplified.subList(0, HARD_POINT_LIMIT)) : simplified;
    }

    /** 编码成 "x,y;x,y;..."，界面回显和网络包都用它。 */
    public static String encode(List<Point> points) {
        if (points == null || points.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(points.size() * 6);
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            if (i > 0) builder.append(';');
            builder.append(point.x()).append(',').append(point.y());
        }
        return builder.toString();
    }

    /** 解析客户端送来的点列；任何异常都当作空笔画，不让畸形包把服务端打崩。 */
    public static List<Point> decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) return List.of();
        List<Point> points = new ArrayList<>();
        for (String part : encoded.split(";")) {
            if (part.isEmpty()) continue;
            int comma = part.indexOf(',');
            if (comma <= 0) return List.of();
            try {
                int x = Integer.parseInt(part.substring(0, comma).trim());
                int y = Integer.parseInt(part.substring(comma + 1).trim());
                points.add(new Point(x, y));
                if (points.size() > HARD_POINT_LIMIT) break;
            } catch (NumberFormatException exception) {
                return List.of();
            }
        }
        return points;
    }

    /** 笔画的外框（含端点）。空笔画返回全 0。 */
    public static int[] bounds(List<Point> points) {
        if (points == null || points.isEmpty()) return new int[]{0, 0, 0, 0};
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Point point : points) {
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    private static Point clamp(Point point, int width, int height) {
        return new Point(Math.max(0, Math.min(width - 1, point.x())),
                Math.max(0, Math.min(height - 1, point.y())));
    }

    private static int squaredDistance(Point a, Point b) {
        int dx = a.x() - b.x();
        int dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    private static double distanceToSegment(Point point, Point start, Point end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        if (dx == 0 && dy == 0) return Math.hypot(point.x() - start.x(), point.y() - start.y());
        double t = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        double px = start.x() + t * dx;
        double py = start.y() + t * dy;
        return Math.hypot(point.x() - px, point.y() - py);
    }

    private MagicCircleStroke() {}
}
