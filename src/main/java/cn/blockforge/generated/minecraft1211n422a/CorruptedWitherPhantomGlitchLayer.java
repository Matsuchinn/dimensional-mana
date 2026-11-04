package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * 凋零幻影的全息错位层：在模型本身上再叠左右各偏一点的红 / 蓝紫两层，做出信号不稳的色差。
 *
 * <p><b>为什么是 RenderLayer：</b>凋零模型的顶点要先做 {@code scale(-1,-1,1)} 翻转、
 * {@code scale(2,2,2)} 放大、再 {@code translate(0,-1.501,0)} 落地，这些变换都在父类
 * {@code LivingEntityRenderer.render} 内部压好。以前这段壳子写在 {@code super.render()}
 * 之后，用的是一套没翻转、没放大、没落地的坐标系，于是整层壳被上下颠倒地画到了幻影脚底下——
 * 这就是「实心紫壳在幻影身上的位置不对」。改成 RenderLayer 之后，它和模型共用同一套变换。</p>
 */
public final class CorruptedWitherPhantomGlitchLayer
        extends RenderLayer<WitherBoss, WitherBossModel<WitherBoss>> {
    /** 全息错位用的实心紫壳（不能换成透明叠加层，否则那两层色差就看不见了）。 */
    private static final ResourceLocation SHELL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/corrupted_wither_shell.png");
    private static final float FX_RADIUS = 64.0F;

    public CorruptedWitherPhantomGlitchLayer(RenderLayerParent<WitherBoss, WitherBossModel<WitherBoss>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, WitherBoss boss,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!(boss instanceof CorruptedWitherPhantom phantom)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || phantom.distanceToSqr(minecraft.player) > FX_RADIUS * FX_RADIUS) return;

        float glitch = (float)(Math.sin(ageInTicks * 3.7D) * 0.03D);
        // 红色 / 蓝色双层错位：就是全息投影的色差。模型空间里 0.03 已经是 6 厘米左右的偏移。
        for (int pass = 0; pass < 2; pass++) {
            boolean red = pass == 0;
            pose.pushPose();
            pose.translate(red ? glitch + 0.035F : -glitch - 0.035F, 0.0F, 0.0F);
            pose.scale(1.015F, 1.015F, 1.015F);
            VertexConsumer shell = buffers.getBuffer(RenderType.entityTranslucentEmissive(SHELL_TEXTURE));
            getParentModel().renderToBuffer(pose, shell, packedLight, OverlayTexture.NO_OVERLAY,
                    red ? 0x4DFF2F45 : 0x4D302FFF);
            pose.popPose();
        }
    }
}
