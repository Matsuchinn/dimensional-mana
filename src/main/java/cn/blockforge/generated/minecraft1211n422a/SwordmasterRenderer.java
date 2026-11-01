package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * 流浪剑士渲染器：全部动作由 GeckoLib 骨骼动画驱动，不做原版 HumanoidModel 的拼装。
 *
 * <p>影子半径 0.45、贴身 1.9 格高。除了画模型，这里还兼职两件事：</p>
 * <ol>
 *   <li>渲染到 {@code sword} 骨骼时把剑柄/剑尖的真实坐标交给 {@link SwordmasterVisuals}，
 *       刀光拖尾就是拿这些点连出来的，所以剑怎么转刀光就怎么走；</li>
 *   <li>画残影时把整只模型换成半透明的青白色，做出「瞬身」的虚影。</li>
 * </ol>
 */
public final class SwordmasterRenderer extends GeoEntityRenderer<WanderingSwordmaster> {
    private static final String SWORD_BONE = "sword";

    public SwordmasterRenderer(EntityRendererProvider.Context context) {
        super(context, new SwordmasterModel());
        this.shadowRadius = 0.45F;
    }

    /** 剑骨骼的变换已经压进 PoseStack 了，此刻取矩阵就是「剑在屏幕上的真实位置」。 */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
                                  int packedLight, int packedOverlay, int packedColor) {
        if (SWORD_BONE.equals(bone.getName())) {
            WanderingSwordmaster animatable = getAnimatable();
            if (animatable != null) {
                SwordmasterVisuals.captureSword(animatable, bone, poseStack.last().pose());
            }
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, packedColor);
    }

    /** 残影：普通渲染用整色，残影用半透明冰蓝色。 */
    @Override
    public Color getRenderColor(WanderingSwordmaster animatable, float partialTick, int packedLight) {
        if (SwordmasterVisuals.ghostPass) {
            return Color.ofRGBA(0.62F, 0.84F, 1.0F, SwordmasterVisuals.ghostAlpha);
        }
        return Color.WHITE;
    }

    /** 残影要能看到背后的世界，材质必须换成半透明；本体仍然是不透明剪影。 */
    @Override
    public RenderType getRenderType(WanderingSwordmaster animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        if (SwordmasterVisuals.ghostPass) {
            return RenderType.entityTranslucent(texture);
        }
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }
}
