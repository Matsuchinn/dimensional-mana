package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 哥布林专用护甲渲染层。
 *
 * <p>原版 {@code HumanoidArmorLayer} 画的是玩家那套 64×32 的护甲贴图，套在哥布林的大头胖肚上会错位，
 * 而且布甲 / 铁甲都得沿用玩家的材质（于是变成「玩家护甲穿了哥布林」）。这里改成：
 * 护甲骨骼直接长在哥布林自己的头 / 身 / 腿上，贴图也是按各体型单独画的
 * （{@code textures/models/armor/goblin_<体型>_cloth|iron.png}），穿上哪套就换哪套材质。</p>
 */
public final class GoblinArmorLayer extends RenderLayer<Goblin, GoblinModel> {
    private static final ResourceLocation CLOTH_FALLBACK =
            GoblinShape.ADULT.armorTexture("cloth");

    public GoblinArmorLayer(RenderLayerParent<Goblin, GoblinModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, Goblin entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        GoblinModel model = getParentModel();
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = entity.getItemBySlot(EquipmentSlot.FEET);
        if (head.isEmpty() && chest.isEmpty() && legs.isEmpty() && feet.isEmpty()) return;

        if (!head.isEmpty()) {
            ResourceLocation texture = textureFor(entity, head);
            VertexConsumer consumer = buffer.getBuffer(RenderType.armorCutoutNoCull(texture));
            model.renderArmorPart(model.head, model.helmet, poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
            model.renderArmorPart(model.head, model.helmetRim, poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        }
        if (!chest.isEmpty()) {
            ResourceLocation texture = textureFor(entity, chest);
            VertexConsumer consumer = buffer.getBuffer(RenderType.armorCutoutNoCull(texture));
            model.renderArmorPart(model.body, model.chestPlate, poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
            model.renderArmorPart(model.body, model.bellyPlate, poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        }
        if (!legs.isEmpty()) {
            ResourceLocation texture = textureFor(entity, legs);
            VertexConsumer consumer = buffer.getBuffer(RenderType.armorCutoutNoCull(texture));
            model.renderArmorPart(model.rightLeg, model.rightLeggings, poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
            model.renderArmorPart(model.leftLeg, model.leftLeggings, poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        }
        if (!feet.isEmpty()) {
            ResourceLocation texture = textureFor(entity, feet);
            VertexConsumer consumer = buffer.getBuffer(RenderType.armorCutoutNoCull(texture));
            model.renderArmorPart(model.rightLeg, model.rightBoot, poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
            model.renderArmorPart(model.leftLeg, model.leftBoot, poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        }
    }

    /** 布甲 / 粗铁甲各一套贴图；不认识的原版护甲一律按铁甲画。 */
    private static ResourceLocation textureFor(Goblin entity, ItemStack stack) {
        String material = "iron";
        if (stack.getItem() instanceof RoughArmorItem rough) {
            material = rough.set() == RoughSet.CLOTH ? "cloth" : "iron";
        }
        GoblinShape shape = entity.variant().shape();
        return shape == null ? CLOTH_FALLBACK : shape.armorTexture(material);
    }
}
