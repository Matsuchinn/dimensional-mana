package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

/**
 * 魔蚀凋零这一套特效共用的平滑几何工具。
 *
 * <p>全部是「面片」：圆环、二十面体、圆柱、条带、广告牌。没有一个方块粒子，
 * 顶点只带位置和颜色，配合 {@link SpellRenderTypes#GLOW}（加色自发光）或
 * {@link CorruptedWitherRenderTypes#beamCore()}（普通混合的纯黑）出图。</p>
 */
public final class CorruptedWitherFx {
    /** 一圈圆环默认切多少段：段数越高越圆，64 段已经看不出棱角。 */
    public static final int RING_SEGMENTS = 64;

    // ---------------------------------------------------------------- 基础面片
    public static void quad(VertexConsumer out, PoseStack.Pose pose,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            float x4, float y4, float z4,
                            float r, float g, float b, float a) {
        vertex(out, pose, x1, y1, z1, r, g, b, a);
        vertex(out, pose, x2, y2, z2, r, g, b, a);
        vertex(out, pose, x3, y3, z3, r, g, b, a);
        vertex(out, pose, x4, y4, z4, r, g, b, a);
    }

    public static void vertex(VertexConsumer out, PoseStack.Pose pose,
                              float x, float y, float z, float r, float g, float b, float a) {
        out.addVertex(pose, x, y, z).setColor(Mth.clamp(r, 0.0F, 1.0F), Mth.clamp(g, 0.0F, 1.0F),
                Mth.clamp(b, 0.0F, 1.0F), Mth.clamp(a, 0.0F, 1.0F));
    }

    // ---------------------------------------------------------------- 圆环 / 光环
    /**
     * 一道有厚度的平滑圆环（Torus 的近似：内外两条弧线之间的带状面片）。
     *
     * @param yawDeg   绕 Y 轴旋转，控制环面朝向
     * @param pitchDeg 绕 X 轴倾斜
     */
    public static void torus(VertexConsumer out, PoseStack.Pose pose,
                             float cx, float cy, float cz, float radius, float thickness,
                             float yawDeg, float pitchDeg,
                             float r, float g, float b, float a) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        float cosYaw = (float)Math.cos(yaw);
        float sinYaw = (float)Math.sin(yaw);
        float cosPitch = (float)Math.cos(pitch);
        float sinPitch = (float)Math.sin(pitch);
        float inner = Math.max(0.02F, radius - thickness);
        float outer = radius + thickness;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / RING_SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / RING_SEGMENTS;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            // 先在 XZ 平面画圆，再按 pitch 绕 X 倾斜、按 yaw 绕 Y 转。
            float in0x = c0 * inner, in0z = s0 * inner;
            float in1x = c1 * inner, in1z = s1 * inner;
            float out0x = c0 * outer, out0z = s0 * outer;
            float out1x = c1 * outer, out1z = s1 * outer;
            // 倾斜：y 由 z 决定；旋转：绕 Y 转。
            float x0 = cx + (in0x * cosYaw + in0z * sinYaw);
            float z0 = cz + (-in0x * sinYaw + in0z * cosYaw);
            float y0 = cy + in0z * sinPitch;
            float x1 = cx + (in1x * cosYaw + in1z * sinYaw);
            float z1 = cz + (-in1x * sinYaw + in1z * cosYaw);
            float y1 = cy + in1z * sinPitch;
            float x2 = cx + (out1x * cosYaw + out1z * sinYaw);
            float z2 = cz + (-out1x * sinYaw + out1z * cosYaw);
            float y2 = cy + out1z * sinPitch;
            float x3 = cx + (out0x * cosYaw + out0z * sinYaw);
            float z3 = cz + (-out0x * sinYaw + out0z * cosYaw);
            float y3 = cy + out0z * sinPitch;
            quad(out, pose, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a);
        }
    }

    // ---------------------------------------------------------------- 二十面体（聚合体）
    /** 正二十面体的 12 个顶点。 */
    private static final float[][] ICO_VERTICES = buildIcosahedronVertices();
    /** 正二十面体的 20 个三角面（顶点下标）。 */
    private static final int[][] ICO_FACES = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
            {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
            {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
    };

    private static float[][] buildIcosahedronVertices() {
        float t = (1.0F + (float)Math.sqrt(5.0F)) / 2.0F;
        return new float[][]{
                {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
                {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}
        };
    }

    /** 画一颗实心二十面体（加色通道下就是一块抛光晶体）。 */
    public static void icosahedron(VertexConsumer out, PoseStack.Pose pose,
                                   float cx, float cy, float cz, float radius,
                                   float yawDeg, float pitchDeg,
                                   float r, float g, float b, float a) {
        float[] rotated = new float[12 * 3];
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        float cosYaw = (float)Math.cos(yaw);
        float sinYaw = (float)Math.sin(yaw);
        float cosPitch = (float)Math.cos(pitch);
        float sinPitch = (float)Math.sin(pitch);
        for (int i = 0; i < 12; i++) {
            float x = ICO_VERTICES[i][0] * radius * 0.5F;
            float y = ICO_VERTICES[i][1] * radius * 0.5F;
            float z = ICO_VERTICES[i][2] * radius * 0.5F;
            float x1 = x * cosYaw + z * sinYaw;
            float z1 = -x * sinYaw + z * cosYaw;
            float y1 = y * cosPitch - z1 * sinPitch;
            float z2 = y * sinPitch + z1 * cosPitch;
            rotated[i * 3] = cx + x1;
            rotated[i * 3 + 1] = cy + y1;
            rotated[i * 3 + 2] = cz + z2;
        }
        for (int[] face : ICO_FACES) {
            vertex(out, pose, rotated[face[0] * 3], rotated[face[0] * 3 + 1], rotated[face[0] * 3 + 2], r, g, b, a);
            vertex(out, pose, rotated[face[1] * 3], rotated[face[1] * 3 + 1], rotated[face[1] * 3 + 2], r, g, b, a);
            vertex(out, pose, rotated[face[2] * 3], rotated[face[2] * 3 + 1], rotated[face[2] * 3 + 2], r, g, b, a);
            // 三角面补成退化四边形，保证顶点数按四的倍数走（QUADS 批次要求）。
            vertex(out, pose, rotated[face[2] * 3], rotated[face[2] * 3 + 1], rotated[face[2] * 3 + 2], r, g, b, a);
        }
    }

    /** 二十面体的棱：用细长的四边面片描出线框（避免 LINES 批次打断 QUADS）。 */
    public static void icosahedronWireframe(VertexConsumer out, PoseStack.Pose pose,
                                            float cx, float cy, float cz, float radius, float width,
                                            float yawDeg, float pitchDeg,
                                            float r, float g, float b, float a) {
        int[][] edges = {
                {0, 11}, {0, 5}, {0, 1}, {0, 7}, {0, 10}, {1, 5}, {5, 11}, {11, 10}, {10, 7}, {7, 1},
                {3, 9}, {9, 4}, {4, 2}, {2, 6}, {6, 8}, {8, 3}, {4, 9}, {2, 4}, {6, 2}, {8, 6},
                {9, 8}, {3, 4}, {3, 2}, {3, 6}, {3, 8}, {1, 9}, {5, 9}, {11, 4}, {10, 2}, {7, 6}
        };
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        float cosYaw = (float)Math.cos(yaw);
        float sinYaw = (float)Math.sin(yaw);
        float cosPitch = (float)Math.cos(pitch);
        float sinPitch = (float)Math.sin(pitch);
        float[] points = new float[12 * 3];
        for (int i = 0; i < 12; i++) {
            float x = ICO_VERTICES[i][0] * radius * 0.5F;
            float y = ICO_VERTICES[i][1] * radius * 0.5F;
            float z = ICO_VERTICES[i][2] * radius * 0.5F;
            float x1 = x * cosYaw + z * sinYaw;
            float z1 = -x * sinYaw + z * cosYaw;
            float y1 = y * cosPitch - z1 * sinPitch;
            float z2 = y * sinPitch + z1 * cosPitch;
            points[i * 3] = cx + x1;
            points[i * 3 + 1] = cy + y1;
            points[i * 3 + 2] = cz + z2;
        }
        for (int[] edge : edges) {
            float ax = points[edge[0] * 3], ay = points[edge[0] * 3 + 1], az = points[edge[0] * 3 + 2];
            float bx = points[edge[1] * 3], by = points[edge[1] * 3 + 1], bz = points[edge[1] * 3 + 2];
            // 线框朝向无所谓，给一条随机的侧向偏移即可（晶体本身就是多面的）。
            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-4F) continue;
            float sx = -dy / len * width, sy = dx / len * width, sz = 0.0F;
            quad(out, pose, ax - sx, ay - sy, az - sz, ax + sx, ay + sy, az + sz,
                    bx + sx, by + sy, bz + sz, bx - sx, by - sy, bz - sz, r, g, b, a);
        }
    }

    // ---------------------------------------------------------------- 圆柱（射线）
    /** 沿局部 +Z 轴的圆柱面；{@code noise} > 0 时按正弦扰动半径，用来做外层紫光的毛边。 */
    public static void cylinder(VertexConsumer out, PoseStack.Pose pose, float length, float radius,
                                int segments, float noise, float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            float rad0 = radius;
            float rad1 = radius;
            if (noise > 0.0F) {
                rad0 = radius * (1.0F + noise * Mth.sin((float)(a0 * 3.0 + length * 0.35F)));
                rad1 = radius * (1.0F + noise * Mth.sin((float)(a1 * 3.0 + length * 0.35F)));
            }
            quad(out, pose,
                    c0 * rad0, s0 * rad0, 0.0F,
                    c1 * rad1, s1 * rad1, 0.0F,
                    c1 * rad1, s1 * rad1, length,
                    c0 * rad0, s0 * rad0, length,
                    r, g, b, a);
        }
    }

    /** 射线端头的发光球：三圈正交圆拼出来，够圆又便宜。 */
    public static void orb(VertexConsumer out, PoseStack.Pose pose, float cx, float cy, float cz,
                           float radius, float r, float g, float b, float a) {
        torus(out, pose, cx, cy, cz, radius, radius * 0.5F, 0.0F, 0.0F, r, g, b, a);
        torus(out, pose, cx, cy, cz, radius, radius * 0.5F, 0.0F, 90.0F, r, g, b, a);
        torus(out, pose, cx, cy, cz, radius, radius * 0.5F, 90.0F, 90.0F, r, g, b, a);
    }

    // ---------------------------------------------------------------- 条带（拖尾 / 雨）
    /** 以 {@code side} 为宽度方向，从 a 到 b 拉一条渐隐渐细的面片。 */
    public static void ribbon(VertexConsumer out, PoseStack.Pose pose,
                              float ax, float ay, float az, float bx, float by, float bz,
                              float sideX, float sideZ, float widthA, float widthB,
                              float r, float g, float b, float alphaA, float alphaB) {
        quad(out, pose,
                ax - sideX * widthA, ay, az - sideZ * widthA,
                ax + sideX * widthA, ay, az + sideZ * widthA,
                bx + sideX * widthB, by, bz + sideZ * widthB,
                bx - sideX * widthB, by, bz - sideZ * widthB,
                r, g, b, (alphaA + alphaB) * 0.5F);
    }

    private CorruptedWitherFx() { }
}
