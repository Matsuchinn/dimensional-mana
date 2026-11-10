package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.model.BeeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** 秽蚀蜂渲染：沿用原版蜜蜂模型（翅膀扑动等动画是模型里写死的骨骼），只换紫黑色贴图。 */
public final class CorruptedBeeRenderer extends MobRenderer<CorruptedBeeEntity, BeeModel<CorruptedBeeEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/entity/corrupted_bee.png");

    public CorruptedBeeRenderer(EntityRendererProvider.Context context) {
        super(context, new BeeModel<>(context.bakeLayer(ModelLayers.BEE)), 0.35F);
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedBeeEntity entity) {
        return TEXTURE;
    }
}
