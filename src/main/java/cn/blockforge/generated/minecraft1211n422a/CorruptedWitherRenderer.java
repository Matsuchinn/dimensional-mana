package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * 魔蚀凋零本体的渲染。
 *
 * <p>底子是原版凋零的骨架（三个头、飘浮的肋骨），在此基础上叠三层东西：</p>
 * <ol>
 *   <li><b>自发光叠加</b>：同一副模型微微放大 0.4%，用 {@code entityTranslucentEmissive}
 *       再画一遍「只有齿缝、眼窝、裂缝有颜色」的贴图（{@link CorruptedWitherGlowLayer}）；</li>
 *   <li><b>三道能量光环</b>：绕着本体转的细圆环，二阶段变成刺眼的白紫、转速翻倍；</li>
 *   <li><b>二阶段灼烧</b>：光环之间再叠一圈反向旋转的暗紫环，制造「空间在被拧」的错觉。</li>
 * </ol>
 *
 * <p>距离超过 64 格时只画本体，外壳和光环全部跳过（设计稿的性能约束）。</p>
 */
public final class CorruptedWitherRenderer extends WitherBossRenderer {
    /** 魔蚀凋零自己的贴图：焦骨 + 发光的齿缝眼窝，不再借用原版凋零那张。 */
    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/corrupted_wither.png");
    private static final float FX_RADIUS = 64.0F;

    public CorruptedWitherRenderer(EntityRendererProvider.Context context) {
        super(context);
        // 自发光叠加层：走 RenderLayer，才能拿到和模型一致的姿势（见类注释）。
        this.addLayer(new CorruptedWitherGlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WitherBoss wither) {
        return BASE_TEXTURE;
    }

    @Override
    public void render(WitherBoss boss, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        super.render(boss, entityYaw, partialTick, pose, buffers, packedLight);
        if (!(boss instanceof CorruptedWither wither)) return;
        var player = Minecraft.getInstance().player;
        if (player == null || wither.distanceToSqr(player) > FX_RADIUS * FX_RADIUS) return;
        boolean phaseTwo = wither.isPhaseTwo();
        float time = wither.tickCount + partialTick;

        // 1) 自发光叠加层不在这里画：它必须和模型共用父类那套「翻转 + 放大 2 倍 + 落地」的变换，
        //    所以搬进了 CorruptedWitherGlowLayer（由父渲染器在正确姿势下回调）。
        //    以前在这儿直接调用 getModel().renderToBuffer 时用的是没翻转的坐标系，
        //    整副骨架被倒过来画到了脚下——就是「本体身体下方不明的东西」。

        // 2) 三道能量光环 + 二阶段的反向暗环：全部是平滑圆环面片。
        pose.pushPose();
        pose.translate(0.0D, 2.0D, 0.0D);
        PoseStack.Pose snapshot = pose.last();
        VertexConsumer glow = buffers.getBuffer(SpellRenderTypes.GLOW);
        // 射线蓄力 / 射击时身体上的光环也跟着加速：一眼就能看出「它在攒大招」。
        float spinRate = phaseTwo ? 3.2F : 1.4F;
        if (wither.isCharging()) spinRate *= 2.6F;
        float spin = time * spinRate;
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.12F);
        for (int i = 0; i < 3; i++) {
            float radius = 2.15F + i * 0.32F;
            float yaw = spin * (i % 2 == 0 ? 1.0F : -0.85F) + i * 55.0F;
            float pitch = 22.0F + i * 24.0F;
            float alpha = (phaseTwo ? 0.55F : 0.32F) * pulse;
            if (phaseTwo) {
                CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, 0.0F, radius, 0.035F, yaw, pitch,
                        0.92F, 0.78F, 1.0F, alpha);
                CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, 0.0F, radius + 0.16F, 0.012F,
                        -yaw, pitch, 1.0F, 1.0F, 1.0F, alpha * 0.55F);
            } else {
                CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, 0.0F, radius, 0.028F, yaw, pitch,
                        0.58F, 0.30F, 0.95F, alpha);
            }
        }
        if (phaseTwo) {
            CorruptedWitherFx.torus(glow, snapshot, 0.0F, 0.0F, 0.0F, 3.1F, 0.02F, -spin * 2.4F, 72.0F,
                    0.55F, 0.16F, 0.85F, 0.30F * pulse);
        }
        pose.popPose();
    }
}
