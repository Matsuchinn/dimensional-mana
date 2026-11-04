package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * 虚无聚合体的渲染：两颗抛光多面体晶体，不是原版的骷髅头模型。
 *
 * <p>结构是「外亮内暗」两层：外层几乎透明的加色晶体壳，内层是高亮度的小晶体 +
 * 旋转的线框。外层用正弦轻微呼吸、内层按 tick 滚动转动，看上去就像里面有光在流。</p>
 *
 * <p>三层各自以不同速度绕不同轴自旋（每秒 30～65 度），并带 partialTick 插值，
 * 所以转动是连续的，不会一格一格地跳。</p>
 *
 * <p>蓝色 = 魔法吸收（冰蓝），红色 = 物理吸收（血红）；距离超过 64 格不画。</p>
 */
public final class CorruptedWitherAggregateRenderer extends EntityRenderer<CorruptedWitherAggregate> {
    private static final float FX_RADIUS = 64.0F;

    public CorruptedWitherAggregateRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * 自己判剔除：实体包围盒只有一个点大小，原版会按它 + 0.5 格做视锥剔除，
     * 晶体边缘擦着屏幕时会被整颗剔掉。这里把包围盒放大到晶体实际大小再多留一格。
     */
    @Override
    public boolean shouldRender(CorruptedWitherAggregate aggregate, Frustum frustum, double camX,
                                double camY, double camZ) {
        if (!aggregate.shouldRender(camX, camY, camZ)) return false;
        AABB box = aggregate.getBoundingBoxForCulling().inflate(1.5D);
        return frustum.isVisible(box);
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedWitherAggregate entity) {
        // 纯顶点色渲染，没有贴图；返回一个占位资源路径避免破坏接口契约。
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    @Override
    public void render(CorruptedWitherAggregate aggregate, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        var player = Minecraft.getInstance().player;
        if (player == null || aggregate.distanceToSqr(player) > FX_RADIUS * FX_RADIUS) return;
        VertexConsumer glow = buffers.getBuffer(SpellRenderTypes.GLOW);
        float time = aggregate.tickCount + partialTick;
        // 转速别贴着「每刻上百度的瞎转」：二十面体面数少，转速一快就会频闪，
        // 看上去一格一格地跳（就是「一顿一顿」的原因）。这里换成慢慢滚，视觉才连续。
        float breath = 0.88F + 0.12F * Mth.sin(time * 0.06F);
        float r;
        float g;
        float b;
        if (aggregate.isBlue()) {
            r = 0.26F;
            g = 0.55F;
            b = 1.0F;
        } else {
            r = 1.0F;
            g = 0.20F;
            b = 0.28F;
        }
        pose.pushPose();
        PoseStack.Pose snapshot = pose.last();
        float outer = 1.05F * breath;
        // 注意：icosahedron 的角度参数是「度」，不是弧度。之前按弧度写成了 time * 0.085，
        // 实际只有 1.7°/秒，看上去就是根本不转（「聚合体的自旋没了」）。这里换算成
        // 每秒 30～60 度：肉眼看得清，又不会快成频闪。
        CorruptedWitherFx.icosahedron(glow, snapshot, 0.0F, 0.0F, 0.0F, outer,
                time * 1.8F, time * 1.05F, r, g, b, 0.16F);
        CorruptedWitherFx.icosahedron(glow, snapshot, 0.0F, 0.0F, 0.0F, 0.72F * breath,
                -time * 2.6F, time * 1.55F,
                Math.min(1.0F, r + 0.25F), Math.min(1.0F, g + 0.25F), Math.min(1.0F, b + 0.15F), 0.55F);
        CorruptedWitherFx.icosahedronWireframe(glow, snapshot, 0.0F, 0.0F, 0.0F, 0.92F * breath, 0.018F,
                -time * 3.3F, time * 2.1F, 1.0F, 1.0F, 1.0F, 0.42F);
        pose.popPose();
    }
}
