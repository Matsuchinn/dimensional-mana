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
import net.minecraft.world.item.Items;

/**
 * 魔蚀末影之眼：一颗绕着虚空蚀影慢慢自转、上下浮动的末影之眼。
 *
 * <p>和 {@link ManaOrbRenderer} 一个套路 —— 直接用物品模型当几何体，不改模型、不加资源；
 * 外面那一圈紫色光尘写在 {@link MagicEyeEntity#tick()} 的客户端分支里。</p>
 */
public final class MagicEyeRenderer extends EntityRenderer<MagicEyeEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/void_shade.png");
    private final ItemRenderer items;

    public MagicEyeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(MagicEyeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MagicEyeEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partial;
        pose.pushPose();
        // 自转 + 轻微点头，看起来像一颗「在找目标」的眼球。
        pose.mulPose(Axis.YP.rotationDegrees(age * 9.0F));
        pose.mulPose(Axis.XP.rotationDegrees(18.0F + (float) Math.sin(age * 0.15F) * 10.0F));
        pose.scale(0.85F, 0.85F, 0.85F);
        items.renderStatic(new ItemStack(Items.ENDER_EYE), ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
