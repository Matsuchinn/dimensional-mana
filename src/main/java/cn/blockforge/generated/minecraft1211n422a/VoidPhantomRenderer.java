package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * 虚无幻影：一张「半透明的末影人影子」，整体缩到六成大小，走半透明渲染层。
 *
 * <p>贴图 {@code void_phantom.png} 由同一个生成脚本产出：像素和虚空蚀影一一对应，
 * 但压成冷灰紫并把不透明度降到 0.35~0.66，再配合渲染层的半透明混合，
 * 看起来就是一道正在散开的影子；身上的末影粒子写在
 * {@link VoidPhantomEntity#tick()} 的客户端分支里。</p>
 */
public final class VoidPhantomRenderer extends EndermanRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/void_phantom.png");

    public VoidPhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    @Override
    public ResourceLocation getTextureLocation(EnderMan entity) {
        return TEXTURE;
    }

    /** 半透明渲染层：身体用带混合的实体渲染类型，影子才有「虚」的感觉。 */
    @Override
    protected RenderType getRenderType(EnderMan entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (!bodyVisible) {
            return glowing ? RenderType.outline(TEXTURE) : null;
        }
        return RenderType.entityTranslucent(TEXTURE);
    }

    @Override
    protected void scale(EnderMan entity, PoseStack pose, float partialTick) {
        pose.scale(0.62F, 0.62F, 0.62F);
    }
}
