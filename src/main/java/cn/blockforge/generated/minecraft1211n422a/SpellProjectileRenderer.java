package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 弹体的透明材质统一在天气之后的法术渲染批次绘制，避免透明层互相吞掉。 */
public final class SpellProjectileRenderer extends EntityRenderer<SpellProjectile> {
    public SpellProjectileRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(SpellProjectile entity) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/effect/stone.png");
    }
}
