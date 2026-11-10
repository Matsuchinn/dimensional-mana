package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 秽蚀蠹的渲染：模型沿用原版蠹虫（七节身子 + 六条腿 + 两根触须），只换贴图，
 * 并按当前阶数把整只缩一圈——每蜕一次皮小 0.15，四阶只有原来的一半多一点。
 *
 * <p>原版 {@code SilverfishRenderer} 不带头类型参数（写死 {@code Silverfish}），
 * 没法直接拿来给子类用，所以这里用一层薄薄的 {@link Adapter} 把原版
 * {@link SilverfishModel} 包成 {@code EntityModel<CorruptedSilverfishEntity>}。</p>
 */
public final class CorruptedSilverfishRenderer
        extends MobRenderer<CorruptedSilverfishEntity, EntityModel<CorruptedSilverfishEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/corrupted_silverfish.png");

    public CorruptedSilverfishRenderer(EntityRendererProvider.Context context) {
        super(context, new Adapter(context.bakeLayer(ModelLayers.SILVERFISH)), 0.28F);
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedSilverfishEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(CorruptedSilverfishEntity entity, PoseStack pose, float partialTick) {
        float scale = entity.bodyScale();
        pose.scale(scale, scale, scale);
    }

    @Override
    protected float getShadowRadius(CorruptedSilverfishEntity entity) {
        return 0.28F * entity.bodyScale();
    }

    /** 原版蠹虫模型 + 我们的实体类型：借一层包装绕开原版渲染器写死的泛型。 */
    private static final class Adapter extends EntityModel<CorruptedSilverfishEntity> {
        private final SilverfishModel model;

        private Adapter(net.minecraft.client.model.geom.ModelPart root) {
            this.model = new SilverfishModel(root);
        }

        @Override
        public void setupAnim(CorruptedSilverfishEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            this.model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight,
                                   int packedOverlay, int color) {
            this.model.renderToBuffer(pose, buffer, packedLight, packedOverlay, color);
        }
    }
}
