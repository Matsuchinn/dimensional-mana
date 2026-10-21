package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 炽阳之怒的火球与法阵统一在 ClientSpellVisuals 的透明批次里绘制。 */
public final class BlazingSunRenderer extends EntityRenderer<BlazingSun> {
    public BlazingSunRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(BlazingSun entity) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/effect/stone.png");
    }
}
