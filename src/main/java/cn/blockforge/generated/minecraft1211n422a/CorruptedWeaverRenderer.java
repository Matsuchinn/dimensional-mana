package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 魔蚀织网者的渲染：沿用原版蜘蛛骨架，换成「黑紫甲壳 + 魔晶簇 + 紫光八眼」的贴图。
 *
 * <p>空中轰炸期间整只蜘蛛会轻微后仰、带一点上浮，让「跳起来喷毒」这个动作读得出来。</p>
 */
public final class CorruptedWeaverRenderer extends SpiderRenderer<CorruptedWeaverEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/corrupted_weaver.png");

    public CorruptedWeaverRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedWeaverEntity weaver) {
        return TEXTURE;
    }

    @Override
    public void render(CorruptedWeaverEntity weaver, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        if (weaver.isBombJumping()) {
            float t = (weaver.tickCount + partialTick) * 0.6F;
            pose.translate(0.0D, 0.06D + Math.abs(Math.sin(t)) * 0.05D, 0.0D);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-8.0F));
        }
        super.render(weaver, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
