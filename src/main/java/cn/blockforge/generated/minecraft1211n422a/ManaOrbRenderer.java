package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 魔素凝聚球：一颗缓慢自转、上下浮动的奥术元素结晶，外面裹着奥术光晕。 */
public final class ManaOrbRenderer extends EntityRenderer<ManaOrb> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/item/arcane_element_crystal.png");
    private final ItemRenderer items;

    public ManaOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ManaOrb entity) { return TEXTURE; }

    @Override
    public void render(ManaOrb entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partial;
        pose.pushPose();
        pose.translate(0, 0.35 + Math.sin(age * 0.12) * 0.08, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 4.5F));
        pose.mulPose(Axis.XP.rotationDegrees(22.0F));
        // 体积缩小到原来的二分之一：1.35 → 0.675。
        pose.scale(0.675F, 0.675F, 0.675F);
        items.renderStatic(new ItemStack(GeneratedMod.MOD_ITEMS.get("arcane_element_crystal").get()),
                ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
