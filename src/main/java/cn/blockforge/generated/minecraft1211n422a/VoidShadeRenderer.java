package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * 虚空蚀影：沿用原版末影人的骨架与发光眼层，只换一张「被虚空沤烂」的深紫贴图。
 *
 * <p>贴图由 {@code tools/make_void_shade_texture.py} 按末影人标准 UV 生成（64×32）：
 * 深紫近黑的皮肤、几道亮紫空间裂隙、脸上两颗紫白眼睛。原版的眼睛发光层照旧挂着，
 * 所以它在暗处会像原版末影人一样亮着一对眼睛。</p>
 */
public final class VoidShadeRenderer extends EndermanRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/void_shade.png");

    public VoidShadeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(EnderMan entity) {
        return TEXTURE;
    }
}
