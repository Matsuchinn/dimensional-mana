package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 原版史莱姆模型与蹦跳动画，按变种切换独立像素皮肤；土史莱姆额外显示鞍具。 */
public final class ElementalSlimeRenderer extends SlimeRenderer {
    private final ItemRenderer itemRenderer;

    public ElementalSlimeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(Slime entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        if (!(entity instanceof ElementalSlime slime)
                || slime.variant() != ElementalSlimeVariant.EARTH || !slime.isSaddled()) return;

        // 直接复用原版鞍的物品模型与贴图，贴在真实身体顶面；不会再出现“有鞍状态但完全看不见”。
        // 史莱姆的模型只画到“身体高度的一半”那一层（8 像素），再被原版的挤压/拉伸倍率缩放，
        // 所以不能用碰撞箱高度乘一个固定比例——那会把鞍压进身体里（旧版 0.76 就是这样陷进去的）。
        float size = slime.getSize();
        float squish = Mth.lerp(partialTick, slime.oSquish, slime.squish);
        float squash = squish / (size * 0.5F + 1.0F);
        float bodyTop = 0.5F * size * (squash + 1.0F);
        pose.pushPose();
        pose.translate(0.0, bodyTop + 0.02, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        float scale = slime.getBbWidth() * 0.92F;
        pose.scale(scale, scale, scale);
        itemRenderer.renderStatic(slime, new ItemStack(Items.SADDLE), ItemDisplayContext.FIXED, false,
                pose, buffers, slime.level(), packedLight,
                LivingEntityRenderer.getOverlayCoords(slime, 0.0F), slime.getId());
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        if (entity instanceof ElementalSlime slime) {
            return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
                    "textures/entity/elemental_slime/" + slime.variant().id() + ".png");
        }
        return super.getTextureLocation(entity);
    }
}
