package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 【虚无凋零射线】的渲染：一根纯黑柱体，外面裹两层发光面片，再套几层向内收缩的旋转 Quad。
 *
 * <ul>
 *   <li>主体：{@link CorruptedWitherRenderTypes#beamCore()} 的纯黑圆柱，普通混合、不反光；</li>
 *   <li>内层：纯白加色柱，比黑柱略细，负责「刺眼的核心」；</li>
 *   <li>外层：半透明紫色，半径带正弦噪音，一路抖动像被空间撕开；</li>
 *   <li>扭曲层：三层半径不断向内收缩、高速旋转的半透明面片，模拟热浪扭曲；</li>
 *   <li>蓄力：端头一颗越来越亮的能量球 + 三道扩张光环，对应屏幕暗角逐渐加深。</li>
 * </ul>
 *
 * <p>所有几何都在局部空间一次成批提交，渲染里只做平移和旋转；距离超过 64 格直接跳过。</p>
 */
public final class CorruptedWitherBeamRenderer extends EntityRenderer<CorruptedWitherBeam> {
    private static final float FX_RADIUS = 64.0F;
    private static final int SEGMENTS = 20;

    public CorruptedWitherBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * 自己判剔除。
     *
     * <p>原版 {@code EntityRenderer.shouldRender} 是拿「实体包围盒 + 0.5 格」去和视锥比的。
     * 光束实体的包围盒只是它起点那一个小点（眼睛位置），所以只要<strong>凋零本体一出画面，
     * 整根六十多格长的光束就被整根剔掉</strong>——这就是「玩家没看着凋零时射线不渲染」。
     * 这里改成用「起点到端点」这一整段做包围盒，再把相机到线段的最近距离拿来判 64 格。</p>
     */
    @Override
    public boolean shouldRender(CorruptedWitherBeam beam, Frustum frustum, double camX, double camY,
                                double camZ) {
        Vec3 origin = beam.origin();
        Vec3 tip = beam.isFiring() ? beam.endPoint() : origin;
        AABB box = new AABB(origin, tip).inflate(2.5D);
        if (!frustum.isVisible(box)) return false;
        return closestPointDistanceSqr(origin, tip, new Vec3(camX, camY, camZ)) <= FX_RADIUS * FX_RADIUS;
    }

    /** 相机到「光束中轴」这一段线段的最近距离平方。 */
    private static double closestPointDistanceSqr(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        if (lengthSqr < 1.0E-6D) return p.distanceToSqr(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / lengthSqr, 0.0D, 1.0D);
        return p.distanceToSqr(a.add(ab.scale(t)));
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedWitherBeam entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    @Override
    public void render(CorruptedWitherBeam beam, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        var player = Minecraft.getInstance().player;
        if (player == null || beam.distanceToSqr(player) > FX_RADIUS * FX_RADIUS) return;
        float time = beam.tickCount + partialTick;
        float charge = beam.chargeRatio();
        pose.pushPose();
        // 局部 +Z 对准光轴：先绕 Y 转水平朝向，再绕 X 抬头。
        // （PoseStack.mulPose 是右乘，后写的先作用在向量上；顺序写反的话
        //   仰角会被 cos(偏航) 吃掉，朝东/西时射线就会变成水平的。）
        // 角度走 smoothAim*：服务端每刻才同步一次，这里按 partialTick 帧间插值，
        // 自转和追猎转向都是连续平滑的，不再有 50ms 一跳的台阶感。
        pose.mulPose(Axis.YP.rotation((float)Math.toRadians(-beam.smoothAimYaw(partialTick))));
        pose.mulPose(Axis.XP.rotation((float)Math.toRadians(beam.smoothAimPitch(partialTick))));
        PoseStack.Pose snapshot = pose.last();

        if (!beam.isFiring()) {
            // 蓄力阶段：端头能量球 + 两道向外扩张、逐渐变淡的光环。
            float orbRadius = 0.35F + charge * 1.1F;
            VertexConsumer glow = buffers.getBuffer(CorruptedWitherRenderTypes.glow());
            CorruptedWitherFx.orb(glow, snapshot, 0.0F, 0.0F, 1.2F, orbRadius,
                    0.95F, 0.85F, 1.0F, 0.35F + charge * 0.5F);
            // 蓄力光圈：一圈「本来就很快、蓄力越满转得越快」的竖直光环。
            // 竖直环面绕竖轴转，一圈 30 段左右最耐看；太快会频闪，这里按蓄力进度 40°/刻 → 90°/刻。
            float spinSpeed = 40.0F + charge * 50.0F;
            for (int i = 0; i < 2; i++) {
                float phase = ((time + i * 20.0F) % 40.0F) / 40.0F;
                float ringRadius = 0.4F + phase * (1.5F + charge * 2.5F);
                float alpha = (1.0F - phase) * (0.25F + charge * 0.35F);
                // 内外两圈反向转，视觉上像两道光环在互相拧。
                float yaw = (i == 0 ? 1.0F : -1.0F) * time * spinSpeed + i * 45.0F;
                CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, 0.0F, ringRadius, 0.03F,
                        yaw, 90.0F, 0.72F, 0.42F, 1.0F, alpha);
            }
            pose.popPose();
            return;
        }

        float length = (float)CorruptedWitherBeam.LENGTH;
        // 主体：纯黑不反光的粗柱（普通混合，不能用加色，否则黑色等于没画）。
        VertexConsumer core = buffers.getBuffer(CorruptedWitherRenderTypes.beamCore());
        CorruptedWitherFx.cylinder(core, snapshot, length, 0.62F, SEGMENTS, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);

        VertexConsumer glow = buffers.getBuffer(CorruptedWitherRenderTypes.glow());
        // 内层纯白：略细、几乎不透明。
        CorruptedWitherFx.cylinder(glow, snapshot, length, 0.34F, SEGMENTS, 0.0F, 1.0F, 1.0F, 1.0F, 0.85F);
        // 外层紫光：半径带正弦噪音，并随距离慢慢收细，营造「能量散逸」。
        CorruptedWitherFx.cylinder(glow, snapshot, length, 0.86F, SEGMENTS, 0.16F,
                0.62F, 0.22F, 0.95F, 0.38F);
        CorruptedWitherFx.cylinder(glow, snapshot, length, 1.08F, SEGMENTS, 0.28F,
                0.38F, 0.10F, 0.72F, 0.20F);

        // 空间扭曲：三层向内收缩、反向旋转的透明面片，靠近光轴的半径按正弦抖动。
        for (int layer = 0; layer < 3; layer++) {
            float spin = time * (18.0F + layer * 9.0F) + layer * 60.0F;
            float radius = 1.35F + layer * 0.38F;
            float alpha = 0.16F - layer * 0.04F;
            CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, length * 0.35F, radius, 0.10F, spin, 90.0F,
                    0.55F, 0.28F, 0.85F, alpha);
            CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, length * 0.7F, radius * 0.8F, 0.08F, -spin * 1.6F, 90.0F,
                    0.72F, 0.42F, 1.0F, alpha);
        }

        // 端头光斑：命中点那一团圆光。
        float endGlow = 2.6F + Mth.sin(time * 0.4F) * 0.35F;
        CorruptedWitherFx.orb(glow, snapshot, 0.0F, 0.0F, length, endGlow, 0.9F, 0.8F, 1.0F, 0.28F);
        pose.popPose();
    }
}
