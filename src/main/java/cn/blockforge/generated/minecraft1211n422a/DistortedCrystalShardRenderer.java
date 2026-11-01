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

/** 畸变魔晶片：一枚沿飞行轴急转的紫黑结晶，拖着一层奥术光。 */
public final class DistortedCrystalShardRenderer extends EntityRenderer<DistortedCrystalShardEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/item/distorted_crystal_shard.png");
    private final ItemRenderer items;

    public DistortedCrystalShardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(DistortedCrystalShardEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(DistortedCrystalShardEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partial;
        ItemStack stack = new ItemStack(GeneratedMod.MOD_ITEMS
                .get(DistortedCrystalShardEntity.ITEM_ID).get());
        pose.pushPose();
        pose.translate(0.0D, 0.1D, 0.0D);
        // 对准飞行方向再绕轴自旋：碎片像子弹一样拧着飞。
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(age * 32.0F));
        pose.scale(0.42F, 0.42F, 0.42F);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
