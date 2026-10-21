package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Witch;

/** 沿用原版女巫模型，只替换为更深的祭坛守卫配色。 */
public final class WanderingWitchRenderer extends WitchRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/wandering_witch.png");

    public WanderingWitchRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Witch entity) {
        return TEXTURE;
    }
}
