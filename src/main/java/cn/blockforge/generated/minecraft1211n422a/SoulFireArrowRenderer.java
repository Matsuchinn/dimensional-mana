package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 魂火箭矢的渲染：两片交叉的自发光贴图。
 *
 * <p>没有走原版 {@code ArrowRenderer}，因为它只会画单面片，而且没法方便地放大。
 * 这里自己发两个互相垂直的四边形：贴图朝局部 +X 轴，再用实体同步下来的
 * yaw / pitch 把 +X 转到飞行方向上，于是从任何角度看都是一根紫焰骨箭。
 * 巨型魂火箭矢用另一张贴图并整体放大 1.75 倍，远远就看得出「这一发很大」。</p>
 *
 * <p>旋转推导：先绕 Y 转 {@code yaw - 90°}、再绕 Z 转 {@code pitch}，
 * 局部 +X 就会落在 {@code (cosφ·sin yaw, sinφ, cosφ·cos yaw)} 上，
 * 正好是原版箭矢用 {@code xRot = atan2(dy, 水平距离)}、{@code yRot = atan2(dx, dz)}
 * 描述的那个方向。</p>
 */
public final class SoulFireArrowRenderer extends EntityRenderer<SoulFireArrowEntity> {
    private static final ResourceLocation NORMAL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/soul_fire_arrow.png");
    private static final ResourceLocation GIANT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/giant_soul_fire_arrow.png");

    /** 箭的长度（格）。 */
    private static final float LENGTH = 1.5F;
    /** 箭的宽度（格）。 */
    private static final float THICKNESS = 0.42F;
    /** 巨型箭整体放大多少。 */
    private static final float GIANT_SCALE = 1.75F;

    public SoulFireArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(SoulFireArrowEntity entity) {
        return entity instanceof GiantSoulFireArrowEntity ? GIANT_TEXTURE : NORMAL_TEXTURE;
    }

    @Override
    public void render(SoulFireArrowEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        pose.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(pitch));
        float scale = entity instanceof GiantSoulFireArrowEntity ? GIANT_SCALE : 1.0F;
        pose.scale(scale, scale, scale);

        VertexConsumer consumer = buffers.getBuffer(
                RenderType.entityTranslucentEmissive(getTextureLocation(entity)));
        quad(pose, consumer, packedLight);
        // 第二片绕飞行轴转 90°，两片交叉成一个「十字箭」。
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        quad(pose, consumer, packedLight);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    private static void quad(PoseStack pose, VertexConsumer consumer, int light) {
        float halfLength = LENGTH * 0.5F;
        float halfThickness = THICKNESS * 0.5F;
        PoseStack.Pose entry = pose.last();
        vertex(consumer, entry, -halfLength, -halfThickness, 0.0F, 0.0F, 1.0F, light);
        vertex(consumer, entry, halfLength, -halfThickness, 0.0F, 1.0F, 1.0F, light);
        vertex(consumer, entry, halfLength, halfThickness, 0.0F, 1.0F, 0.0F, light);
        vertex(consumer, entry, -halfLength, halfThickness, 0.0F, 0.0F, 0.0F, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose entry, float x, float y, float z,
                               float u, float v, int light) {
        consumer.addVertex(entry, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0.0F, 0.0F, 1.0F);
    }
}
