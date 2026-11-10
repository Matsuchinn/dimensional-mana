package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 毒领域的连续穹膜：单层封顶、UV 跨面连续，不用线框也不用粒子贴片拼外壳。 */
final class ClientSpellSurfaces {
    private final MultiBufferSource source;
    private final Matrix4f matrix;
    private final Vec3 camera;
    private final ClientSpellView view;

    ClientSpellSurfaces(MultiBufferSource source, Matrix4f matrix, Vec3 camera, ClientSpellView view) {
        this.source = source; this.matrix = matrix; this.camera = camera; this.view = view;
    }

    /** 单层封顶穹膜，UV 连续跨面，没有经纬线、叠壳或逐格亮斑。 */
    void venomDome(Vec3 center, double radius, double height, double clock, double alpha, boolean minimal) {
        if (radius < 0.15 || height < 0.1 || alpha < 0.01) return;
        int segments = minimal ? 32 : 48, rings = minimal ? 8 : 12;
        VertexConsumer out = source.getBuffer(SpellRenderTypes.Surface.VENOM_MEMBRANE.type);
        double uScroll = clock * 0.0009, vScroll = clock * 0.00045;
        for (int ring = 0; ring < rings; ring++) {
            double v0 = ring / (double)rings, v1 = (ring + 1) / (double)rings;
            for (int sector = 0; sector < segments; sector++) {
                double u0 = sector / (double)segments, u1 = (sector + 1) / (double)segments;
                domeVertex(out, center, radius, height, u0, v0, uScroll, vScroll, alpha);
                domeVertex(out, center, radius, height, u1, v0, uScroll, vScroll, alpha);
                domeVertex(out, center, radius, height, u1, v1, uScroll, vScroll, alpha);
                domeVertex(out, center, radius, height, u0, v1, uScroll, vScroll, alpha);
            }
        }
    }

    private void domeVertex(VertexConsumer out, Vec3 center, double radius, double height,
                            double u, double v, double us, double vs, double alpha) {
        double azimuth = u * Math.PI * 2, elevation = v * Math.PI * 0.5;
        double ca = Math.cos(azimuth), sa = Math.sin(azimuth), ce = Math.cos(elevation), se = Math.sin(elevation);
        Vec3 point = center.add(ca * ce * radius, se * height, sa * ce * radius);
        Vec3 normal = new Vec3(ca * ce / radius, se / height, sa * ce / radius).normalize();
        double grazing = 1 - Math.abs(normal.dot(camera.subtract(point).normalize()));
        // 正面薄透、掠射边缘稍厚；底部接地淡入，顶部保持闭合。
        double edge = Math.min(1, 0.30 + v * 12);
        double opacity = alpha * edge * (0.14 + 0.25 * grazing * grazing);
        vertex(out, point, normal, u * 3 + us, v * 1.5 + vs, opacity, 0xDBEEBC);
    }

    private void vertex(VertexConsumer out, Vec3 point, Vec3 normal, double u, double v, double alpha, int tint) {
        var n = new org.joml.Vector3f((float)normal.x, (float)normal.y, (float)normal.z);
        matrix.transformDirection(n).normalize();
        Vec3 p = point.subtract(camera);
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z)
                .setColor(tint >> 16 & 255, tint >> 8 & 255, tint & 255,
                        (int)(255 * Math.clamp(alpha * view.visibility(point), 0, 1)))
                .setUv((float)u, (float)v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                .setNormal(n.x, n.y, n.z);
    }
}
