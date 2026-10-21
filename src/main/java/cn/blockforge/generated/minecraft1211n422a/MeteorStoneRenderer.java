package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** 把万法千陨的巨石实体画成一格见方的石块，缓慢翻滚以便看出它正在飞行；落地溅出的小碎石用同一套画法缩小绘制。 */
public final class MeteorStoneRenderer extends EntityRenderer<MeteorStone> {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
            "textures/effect/stone.png");

    public MeteorStoneRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.35F;
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorStone entity) {
        return TEXTURE;
    }

    @Override
    public void render(MeteorStone entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float age = entity.tickCount + partial;
        pose.translate(0, entity.getBbHeight() * 0.5, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 13.0F));
        pose.mulPose(Axis.XP.rotationDegrees(18.0F + (float)Math.sin(age * 0.17) * 11.0F));
        float scale = 0.82F * entity.size();
        pose.scale(scale, scale, scale);
        pose.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(STONE, pose, buffers, light,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
