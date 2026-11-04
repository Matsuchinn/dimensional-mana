package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.minecraft.world.entity.projectile.WitherSkull;

/**
 * 【侵蚀凋零】骷髅头的渲染：直接用原版凋零骷髅头的模型与贴图。
 *
 * <p>外形沿用原版是有意的——玩家一眼就知道这是凋零系的东西；紫色灵魂的拖尾由
 * {@link CorruptedWitherSkull#tick()} 里的少量紫雾补足，主力光效交给本体渲染器。</p>
 */
public final class CorruptedWitherSkullRenderer extends WitherSkullRenderer {

    public CorruptedWitherSkullRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(WitherSkull skull, Frustum frustum, double camX, double camY, double camZ) {
        // 64 格外不再绘制。camX / camY / camZ 是相机坐标，得先换成「骷髅到相机」的位移。
        double dx = skull.getX() - camX;
        double dy = skull.getY() - camY;
        double dz = skull.getZ() - camZ;
        if (dx * dx + dy * dy + dz * dz > 64.0D * 64.0D) return false;
        return super.shouldRender(skull, frustum, camX, camY, camZ);
    }

    @Override
    public ResourceLocation getTextureLocation(WitherSkull skull) {
        // 跟本体共用一张魔蚀贴图，飞出来的骷髅头才像从它身上掉的。
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
                "textures/entity/corrupted_wither.png");
    }
}
