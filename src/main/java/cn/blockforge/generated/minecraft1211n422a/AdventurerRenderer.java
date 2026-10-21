package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** 使用原版史蒂夫比例与手臂动画的冒险者渲染器。 */
public final class AdventurerRenderer extends HumanoidMobRenderer<Adventurer, PlayerModel<Adventurer>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/adventurer.png");

    private static final class AdventurerModel extends PlayerModel<Adventurer> {
        AdventurerModel(ModelPart root) { super(root, false); }
        @Override public void setupAnim(Adventurer entity, float limbSwing, float limbAmount,
                                        float age, float yaw, float pitch) {
            rightArmPose = entity.isCastingSpell() ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.ITEM;
            leftArmPose = entity.isCastingSpell() ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.EMPTY;
            super.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
        }
    }

    public AdventurerRenderer(EntityRendererProvider.Context context) {
        super(context, new AdventurerModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        shadowRadius = 0.35F;
    }

    @Override
    public ResourceLocation getTextureLocation(Adventurer entity) {
        return TEXTURE;
    }
}
