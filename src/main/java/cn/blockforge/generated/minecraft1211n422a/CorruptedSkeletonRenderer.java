package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 魔蚀骸骨的渲染：沿用原版骷髅骨架，换一张「骨头发黑、缝隙里全是紫晶与魂火」的贴图。
 *
 * <p>蓄力（{@link CorruptedSkeleton#isCasting()}）期间整只骨架上浮轻微颤抖。
 * 抬弓拉弦的手臂动作由原版 {@code SkeletonModel} 自己摆：手里握着弓、并且
 * {@link CorruptedSkeleton#isAggressive()} 为真时，双臂就会抬成端弓的姿势
 * （见 {@code CorruptedSkeleton#isAggressive()} 的说明）。</p>
 */
public final class CorruptedSkeletonRenderer extends SkeletonRenderer<CorruptedSkeleton> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/corrupted_skeleton.png");

    public CorruptedSkeletonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedSkeleton skeleton) {
        return TEXTURE;
    }

    @Override
    public void render(CorruptedSkeleton skeleton, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        if (skeleton.isCasting()) {
            float t = (skeleton.tickCount + partialTick) * 0.9F;
            float amplitude = 0.012F + 0.02F * skeleton.chargeProgress(partialTick);
            pose.translate(Math.sin(t * 2.3F) * amplitude,
                    Math.abs(Math.sin(t * 1.9F)) * amplitude,
                    Math.cos(t * 2.7F) * amplitude);
        }
        super.render(skeleton, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
