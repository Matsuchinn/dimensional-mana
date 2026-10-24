package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 哥布林渲染器。
 *
 * <p>五种哥布林是五个独立实体类型，每个类型在注册时就确定了自己的 {@link GoblinVariant}，
 * 所以渲染器可以直接烤好对应体型的模型（幼崽 / 成年 / 首领），
 * 不用在每一帧里再判断该换哪套骨骼。</p>
 */
public final class GoblinRenderer extends MobRenderer<Goblin, GoblinModel> {
    private final GoblinVariant variant;

    public GoblinRenderer(EntityRendererProvider.Context context, GoblinVariant variant) {
        super(context, new GoblinModel(context.bakeLayer(variant.shape().layer())), 0.4F);
        this.variant = variant;
        this.shadowRadius = 0.35F;
        addLayer(new GoblinArmorLayer(this));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    protected void scale(Goblin entity, PoseStack poseStack, float partialTick) {
        float scale = variant.scale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(Goblin entity) {
        return variant.texture();
    }
}
