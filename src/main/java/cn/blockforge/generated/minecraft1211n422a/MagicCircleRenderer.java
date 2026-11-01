package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 绘制台（阵盘基座）的动态法阵渲染。
 *
 * <p><b>触发时机</b>：基座的方块实体里存有有效渲染包（= 阵图有功能块）就常驻渲染，
 * 空台不画。渲染包只挂在原点基座上，所以一片阵盘只有一份绘制、不会 N 块基座叠 N 层。</p>
 *
 * <p><b>视角与距离剔除</b>（对应性能要求）：</p>
 * <ul>
 *   <li>{@link #shouldRender} 里做两段剔除——先看摄像机距离（超过 {@link #VIEW_RANGE} 格
 *       直接不画），再用 {@link Frustum#isVisible(AABB)} 判断渲染包围盒是否在视锥内；
 *       包围盒用 {@link MagicCircleBaseBlockEntity#getRenderBoundingBox()}（整片悬空阵的盒子）。</li>
 *   <li>渲染内再按距离降档：&gt; {@value #FAR_RANGE} 格只画底盘层，节点和流光全跳过；
 *       &gt; {@value #MID_RANGE} 格时间轴按 5 刻量化——远处阵的自转每 5 刻才跳一步，
 *       省掉大量无意义的三角函数。</li>
 * </ul>
 *
 * <p><b>平滑旋转</b>：时间轴用 {@code level.getGameTime() + partialTick} 组合——
 * partialTick 是当前帧在两个游戏刻之间的插值比例，每一帧连续变化，
 * 转起来跟帧率走、低帧率也不抖；绝不用 {@code System.currentTimeMillis()}。</p>
 */
public final class MagicCircleRenderer implements BlockEntityRenderer<MagicCircleBaseBlockEntity> {
    /** 摄像机超过这个距离整个阵不画（也小于默认 64 格的实体视野，双保险）。 */
    private static final double VIEW_RANGE = 64.0;
    /** 中距阈值：超过后旋转时间轴 5 刻一跳。 */
    private static final double MID_RANGE = 16.0;
    /** 远距阈值：超过后只画底盘层。 */
    private static final double FAR_RANGE = 32.0;

    public MagicCircleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MagicCircleBaseBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        MagicCircleRenderPacket packet = entity.renderPacket();
        if (packet.isEmpty() || entity.getLevel() == null) return;

        // ---- 摄像机距离：算一次，给 LOD 用 ----
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        net.minecraft.core.BlockPos origin = entity.getBlockPos();
        double originX = origin.getX();
        double originY = origin.getY();
        double originZ = origin.getZ();
        double dx = camPos.x - (originX + 0.5);
        double dz = camPos.z - (originZ + 0.5);
        double dy = camPos.y - originY;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // ---- 平滑时间轴：游戏刻 + 帧插值 ----
        double time = entity.getLevel().getGameTime() + partialTick;
        if (distance > MID_RANGE) {
            time = Math.floor(time / 5.0) * 5.0; // 远距离：角度每 5 刻才更新一次
        }

        // 画布以原点基座的角为 (0,0) 铺 baseW×baseH 格；引擎按中心绘制，先移到阵盘中心，
        // 再整体悬空 +0.2 格（需求指定）。
        poseStack.pushPose();
        poseStack.translate(packet.baseW * 0.5F, 0.2F, packet.baseH * 0.5F);
        if (distance > FAR_RANGE) {
            MagicCircleArt.renderPlateOnly(packet, poseStack, buffers, null,
                    packet.baseW, packet.baseH, time, false, 0.0F);
        } else {
            MagicCircleArt.render(packet, poseStack, buffers, null,
                    packet.baseW, packet.baseH, time, false, 0.0F);
        }
        poseStack.popPose();
    }

    /** 距离 + 视锥双重剔除：不通过的阵连顶点都不用生成。 */
    @Override
    public boolean shouldRender(MagicCircleBaseBlockEntity entity, Vec3 camPos) {
        if (entity.renderPacket().isEmpty()) return false;
        Vec3 anchor = Vec3.atBottomCenterOf(entity.getBlockPos());
        if (anchor.distanceToSqr(camPos) > VIEW_RANGE * VIEW_RANGE) return false;
        Frustum frustum = Minecraft.getInstance().levelRenderer == null ? null
                : Minecraft.getInstance().levelRenderer.getFrustum();
        // frustum 偶发为 null（首帧 / 切换世界），判不到就放行，宁多画勿闪烁。
        return frustum == null || frustum.isVisible(entity.renderBounds());
    }

    /** NeoForge 扩展：让引擎按整片阵的盒子做剔除，而不是单格基座。 */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(MagicCircleBaseBlockEntity entity) {
        return entity.renderBounds();
    }

    /** 阵盘可能比较大，按画布尺寸放宽视野，别让大块头提前消失。 */
    @Override
    public int getViewDistance() {
        return (int) VIEW_RANGE;
    }

    /** 渲染盒超出原点基座自身一格，必须允许离屏渲染，否则引擎会按单格盒剔掉。 */
    @Override
    public boolean shouldRenderOffScreen(MagicCircleBaseBlockEntity entity) {
        return true;
    }
}
