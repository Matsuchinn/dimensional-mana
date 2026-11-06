package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * 凋零幻影的渲染：两层模型错位、偏红偏蓝，做出全息故障感。
 *
 * <p>本体照常按凋零骨架绘制，再在左右各偏移几厘米叠两层半透明外壳：一层偏红、一层偏蓝。
 * 偏移量随 tick 高频摆动，看起来就像信号不稳的全息投影。被砍中爆炸时，原地画一圈
 * 纯几何、瞬间放大并淡出的冲击波（水平圆环 + 球面光壳），不产生任何粒子。</p>
 */
public final class CorruptedWitherPhantomRenderer extends WitherBossRenderer {
    /** 幻影贴图跟本体一致：玩家一眼就知道这是魔蚀凋零的假身。 */
    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/corrupted_wither.png");
    private static final float FX_RADIUS = 64.0F;

    public CorruptedWitherPhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
        // 红 / 蓝错位壳：走 RenderLayer 才能和模型共用同一套变换。
        this.addLayer(new CorruptedWitherPhantomGlitchLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WitherBoss boss) {
        return BASE_TEXTURE;
    }

    @Override
    public boolean shouldRender(WitherBoss boss, Frustum frustum, double camX, double camY, double camZ) {
        // camX / camY / camZ 是相机坐标，不是距离：要拿本体位置减相机位置才是距离。
        // 以前这里直接把相机坐标平方相加当距离用，结果只有站在世界原点附近幻影才画得出来。
        double dx = boss.getX() - camX;
        double dy = boss.getY() - camY;
        double dz = boss.getZ() - camZ;
        if (dx * dx + dy * dy + dz * dz > FX_RADIUS * FX_RADIUS) return false;
        return super.shouldRender(boss, frustum, camX, camY, camZ);
    }

    @Override
    public void render(WitherBoss boss, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        // 全息错位的那两层壳不在这里画：它们必须用和模型一样的姿势，所以搬进了
        // CorruptedWitherPhantomGlitchLayer。以前写在这里时坐标系没翻转、没放大，
        // 整层壳被倒过来画到了幻影脚底下（就是「实心紫壳位置不对」）。
        super.render(boss, entityYaw, partialTick, pose, buffers, packedLight);
        if (!(boss instanceof CorruptedWitherPhantom phantom)) return;
        var player = Minecraft.getInstance().player;
        if (player == null || phantom.distanceToSqr(player) > FX_RADIUS * FX_RADIUS) return;
        float time = phantom.tickCount + partialTick;
        if (phantom.isExploding()) {
            drawShockwave(pose, buffers, phantom, time);
        }
    }

    /** 爆炸冲击波：一圈水平光环 + 一层球面光壳，同时放大并淡出。 */
    private void drawShockwave(PoseStack pose, MultiBufferSource buffers, CorruptedWitherPhantom phantom,
                               float time) {
        float t = Mth.clamp(phantom.shockwaveRatio(), 0.0F, 1.0F);
        float radius = 0.6F + t * 6.5F;
        float alpha = (1.0F - t) * 0.85F;
        VertexConsumer glow = buffers.getBuffer(CorruptedWitherRenderTypes.glow());
        pose.pushPose();
        pose.translate(0.0D, 1.6D, 0.0D);
        PoseStack.Pose snapshot = pose.last();
        CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, 0.0F, radius, 0.22F, 0.0F, 0.0F,
                0.85F, 0.55F, 1.0F, alpha);
        CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, 0.0F, radius * 0.72F, 0.10F, time * 90.0F, 90.0F,
                1.0F, 0.85F, 1.0F, alpha * 0.7F);
        CorruptedWitherFx.orb(glow, snapshot, 0.0F, 0.0F, 0.0F, radius * 0.5F, 0.9F, 0.6F, 1.0F, alpha * 0.35F);
        pose.popPose();
    }
}
