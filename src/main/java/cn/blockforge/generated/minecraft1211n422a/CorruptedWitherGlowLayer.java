package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * 魔蚀凋零本体的自发光叠加层。
 *
 * <p><b>为什么必须写成 RenderLayer：</b>凋零模型的顶点是「歪着」定义的——原版
 * {@code LivingEntityRenderer} 画模型前会先做 {@code scale(-1,-1,1)}（把模型的 Y 轴翻过来）、
 * 再 {@code scale(2,2,2)}（凋零体型翻倍）、最后 {@code translate(0,-1.501,0)} 把脚踩到地面。
 * 这三个变换是父类 render 方法内部 {@code pushPose/popPose} 之间的东西。</p>
 *
 * <p>以前这段叠加层写在 {@code render()} 里、调完 {@code super.render()} 之后——那时父类已经
 * popPose 了，所以模型是按「没翻转、没放大、没落地」的原始坐标系画的：整副骨架被上下颠倒，
 * 从身体底部长出来一大截紫色柱状物。这正是「本体身体下方不明的东西」和「幻影身上的紫壳
 * 位置不对」两个问题的根因。</p>
 *
 * <p>{@link RenderLayer} 由父类在「模型变换已经压好」的姿势下调用（原版盔甲层就是这么接的），
 * 所以这里只做一点点放大，叠加层就严丝合缝地贴在模型上。</p>
 */
public final class CorruptedWitherGlowLayer extends RenderLayer<WitherBoss, WitherBossModel<WitherBoss>> {
    /** 自发光叠加层：只有齿缝 / 眼窝 / 裂缝有颜色，其余全透明。 */
    private static final ResourceLocation GLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/corrupted_wither_glow.png");
    /** 超过这个距离就不画叠加层（设计稿的性能约束）。 */
    private static final float FX_RADIUS = 64.0F;

    public CorruptedWitherGlowLayer(RenderLayerParent<WitherBoss, WitherBossModel<WitherBoss>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, WitherBoss boss,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!(boss instanceof CorruptedWither wither)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || wither.distanceToSqr(minecraft.player) > FX_RADIUS * FX_RADIUS) return;

        boolean phaseTwo = wither.isPhaseTwo();
        // ageInTicks 已经是「tickCount + partialTick」，呼吸式明暗跟着它走就是连续的。
        float pulse = 0.78F + 0.22F * Mth.sin(ageInTicks * 0.09F);
        int alpha = Mth.clamp((int)(pulse * 255.0F), 0, 255);
        pose.pushPose();
        // 只放大 0.4%：纯粹的「描边」，不会把肋骨糊成一块紫色。
        pose.scale(1.004F, 1.004F, 1.004F);
        VertexConsumer shell = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW_TEXTURE));
        getParentModel().renderToBuffer(pose, shell, packedLight, OverlayTexture.NO_OVERLAY,
                alpha << 24 | 0xFFFFFF);
        pose.popPose();
        // 二阶段多刷一层更亮的，让眼睛和齿缝像在烧起来。
        if (phaseTwo) {
            pose.pushPose();
            pose.scale(1.008F, 1.008F, 1.008F);
            VertexConsumer hot = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW_TEXTURE));
            int hotAlpha = Mth.clamp((int)((0.35F + 0.35F * Mth.sin(ageInTicks * 0.21F)) * 255.0F), 0, 255);
            getParentModel().renderToBuffer(pose, hot, packedLight, OverlayTexture.NO_OVERLAY,
                    hotAlpha << 24 | 0xFF9CF5);
            pose.popPose();
        }
    }
}
