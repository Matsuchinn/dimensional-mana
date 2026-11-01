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

/** 蛛丝球：一团边飞边转的魔蚀蛛网（直接渲染蛛网方块物品的模型）。 */
public final class WebProjectileRenderer extends EntityRenderer<WebProjectileEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/block/corrupted_web.png");
    private final ItemRenderer items;

    public WebProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(WebProjectileEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(WebProjectileEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partial;
        ItemStack stack = new ItemStack(GeneratedMod.MOD_ITEMS.get(CorruptedWebBlock.BLOCK_ID).get());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 24.0F));
        pose.mulPose(Axis.XP.rotationDegrees(age * 17.0F));
        pose.scale(0.34F, 0.34F, 0.34F);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
