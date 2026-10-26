package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 投影 / 刻印锚点的客户端渲染：把蓝图画成「发光的点 + 连线 + 一层薄光晕」。
 *
 * <p>用的是不带贴图的纯色顶点格式（POSITION_COLOR），所以不需要额外贴图资源，
 * 颜色全部来自粉末：有元素粉就用元素色，没有就按功能块类型上色。
 * 每层稍微往外挪 1/200 格，叠 4 层也不会互相打架；亮度按游戏刻做呼吸脉冲，
 * 远处看上去就是一片一直在闪的魔法阵。</p>
 *
 * <p>第 4 阶段加了两样：运行中的刻印阵亮一档、呼吸更快；异面连线画成一条暗线，
 * 每 2 秒有一道光脉冲从上游游到下游（设计文档 12.1 / 12.2）。</p>
 */
public final class MagicCircleProjectionRenderer
        implements BlockEntityRenderer<MagicCircleProjectionBlockEntity> {

    /** 光点边长，单位是「画布像素」，2 像素在 32 画布上就是一个明显的小方块。 */
    private static final float NODE_PIXELS = 3.0F;
    private static final float LAYER_LIFT = 0.005F;
    /** 连线用带深度测试的线；缓存成静态常量，别每帧新建 RenderType（那样合不了批）。 */
    private static final RenderType LINE_TYPE = RenderType.lines();
    /** 超过这个距离就不画连线了，只留锚点本身（文档 12.1：16~32 格只画锚点）。 */
    private static final double MAX_LINK_RENDER_DISTANCE = 32.0;

    public MagicCircleProjectionRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MagicCircleProjectionBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (entity.getLevel() == null) return;
        boolean running = entity.running();
        Direction facing = entity.getBlockState().hasProperty(MagicCircleProjectionBlock.FACING)
                ? entity.getBlockState().getValue(MagicCircleProjectionBlock.FACING)
                : Direction.UP;

        long gameTime = entity.getLevel().getGameTime();
        float phase = (gameTime + partialTick) * (running ? 0.16F : 0.09F);
        float pulse = 0.72F + 0.28F * Mth.sin(phase);

        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        VertexConsumer lines = buffers.getBuffer(LINE_TYPE);

        // 异面连线先画：它在锚点之间，被锚点盖住反而更自然。
        drawLinks(quads, lines, poseStack, entity, gameTime, partialTick);

        if (entity.isEmpty()) return;
        int baseW = entity.baseW();
        int baseH = entity.baseH();
        int glow = running ? 0x33FFE9A8 : 0x22FFFFFF;

        for (int layerIndex = 0; layerIndex < entity.layers().size(); layerIndex++) {
            MagicCircleBlueprint blueprint = entity.layers().get(layerIndex);
            float lift = LAYER_LIFT * (layerIndex + 1);
            // 先画层与层之间的「底盘光晕」，让贴着墙的那一层有厚度感。
            drawQuad(quads, poseStack, facing, baseW, baseH,
                    0, 0, baseW * MagicCircleNetwork.TILE, baseH * MagicCircleNetwork.TILE,
                    lift, glow, pulse * 0.35F);
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
                for (MagicCircleNetwork.Link link : layer.links()) {
                    int count = layer.nodes().size();
                    if (link.from() < 0 || link.to() < 0 || link.from() >= count || link.to() >= count) continue;
                    MagicCircleNetwork.Node from = layer.nodes().get(link.from());
                    MagicCircleNetwork.Node to = layer.nodes().get(link.to());
                    drawLine(lines, poseStack, facing, baseW, baseH,
                            from.x(), from.y(), to.x(), to.y(), lift, 0x66B6F0FF, pulse);
                }
                for (MagicCircleNetwork.Node node : layer.nodes()) {
                    int color = MagicCircleBlueprint.colorOf(node);
                    float half = NODE_PIXELS * 0.5F;
                    drawQuad(quads, poseStack, facing, baseW, baseH,
                            node.x() - half, node.y() - half, node.x() + half, node.y() + half,
                            lift, color, pulse);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 异面连线

    /**
     * 画这个锚点连出去的线。为避免一条线被两端的渲染各画一遍，只在
     * 「本锚点坐标 < 对方坐标」的那一头画。
     */
    private static void drawLinks(VertexConsumer quads, VertexConsumer lines, PoseStack poseStack,
                                  MagicCircleProjectionBlockEntity entity, long gameTime, float partialTick) {
        if (entity.links().isEmpty()) return;
        BlockPos self = entity.getBlockPos();
        Vec3 own = new Vec3(0.5, 0.5, 0.5);
        boolean running = entity.running();
        for (BlockPos other : entity.links()) {
            if (self.compareTo(other) >= 0) continue; // 另一端会画
            Vec3 target = new Vec3(other.getX() - self.getX() + 0.5,
                    other.getY() - self.getY() + 0.5,
                    other.getZ() - self.getZ() + 0.5);
            double distance = own.distanceTo(target);
            if (distance > MAX_LINK_RENDER_DISTANCE) continue;
            int baseAlpha = running ? 0x88 : 0x44;
            drawSegment(lines, poseStack, own, target, (baseAlpha << 24) | 0x7FE8FF);
            if (!running) continue;
            // 光脉冲：每 2 秒（40 刻）从上游流到下游，宽 0.3 秒。
            float t = ((gameTime % 40) + partialTick) / 40.0F;
            double phase = Math.max(0.0, Math.min(1.0, t / 0.3));
            if (t > 0.3F) continue;
            Vec3 point = own.lerp(target, phase);
            drawCross(quads, poseStack, point, 0.045F, 0xF2FFFFFF);
        }
    }

    /** 一条从 a 到 b 的线；法线取线段方向，避免 lines 格式缺元素。 */
    private static void drawSegment(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, int argb) {
        Vec3 normal = b.subtract(a).normalize();
        if (normal.lengthSqr() < 1.0E-6) normal = new Vec3(0, 1, 0);
        int alpha = (argb >>> 24) & 0xFF;
        int cr = (argb >>> 16) & 0xFF;
        int cg = (argb >>> 8) & 0xFF;
        int cb = argb & 0xFF;
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z)
                .setColor(cr, cg, cb, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z)
                .setColor(cr, cg, cb, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** 脉冲用一个小十字面片，任何角度看都能看到一点亮。 */
    private static void drawCross(VertexConsumer consumer, PoseStack poseStack, Vec3 center, float half, int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int cr = (argb >>> 16) & 0xFF;
        int cg = (argb >>> 8) & 0xFF;
        int cb = argb & 0xFF;
        PoseStack.Pose pose = poseStack.last();
        float x = (float) center.x;
        float y = (float) center.y;
        float z = (float) center.z;
        // XY 面
        quad(consumer, pose, x - half, y - half, z, x + half, y - half, z, x + half, y + half, z,
                x - half, y + half, z, cr, cg, cb, alpha);
        // XZ 面
        quad(consumer, pose, x - half, y, z - half, x + half, y, z - half, x + half, y, z + half,
                x - half, y, z + half, cr, cg, cb, alpha);
        // YZ 面
        quad(consumer, pose, x, y - half, z - half, x, y + half, z - half, x, y + half, z + half,
                x, y - half, z + half, cr, cg, cb, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int cr, int cg, int cb, int alpha) {
        consumer.addVertex(pose, x1, y1, z1).setColor(cr, cg, cb, alpha);
        consumer.addVertex(pose, x2, y2, z2).setColor(cr, cg, cb, alpha);
        consumer.addVertex(pose, x3, y3, z3).setColor(cr, cg, cb, alpha);
        consumer.addVertex(pose, x4, y4, z4).setColor(cr, cg, cb, alpha);
    }

    // ------------------------------------------------------------------ 画布内的点与线

    private static void drawQuad(VertexConsumer consumer, PoseStack poseStack, Direction facing,
                                 int baseW, int baseH, float x1, float y1, float x2, float y2,
                                 float lift, int argb, float alphaScale) {
        float[] a = MagicCircleProjectionGeometry.local(facing, baseW, baseH, x1, y1, lift);
        float[] b = MagicCircleProjectionGeometry.local(facing, baseW, baseH, x2, y1, lift);
        float[] c = MagicCircleProjectionGeometry.local(facing, baseW, baseH, x2, y2, lift);
        float[] d = MagicCircleProjectionGeometry.local(facing, baseW, baseH, x1, y2, lift);
        int alpha = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * alphaScale)));
        int cr = (argb >>> 16) & 0xFF;
        int cg = (argb >>> 8) & 0xFF;
        int cb = argb & 0xFF;
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, a[0], a[1], a[2]).setColor(cr, cg, cb, alpha);
        consumer.addVertex(pose, b[0], b[1], b[2]).setColor(cr, cg, cb, alpha);
        consumer.addVertex(pose, c[0], c[1], c[2]).setColor(cr, cg, cb, alpha);
        consumer.addVertex(pose, d[0], d[1], d[2]).setColor(cr, cg, cb, alpha);
    }

    private static void drawLine(VertexConsumer consumer, PoseStack poseStack, Direction facing,
                                 int baseW, int baseH, float x1, float y1, float x2, float y2,
                                 float lift, int argb, float alphaScale) {
        float[] a = MagicCircleProjectionGeometry.local(facing, baseW, baseH, x1, y1, lift + 0.001F);
        float[] b = MagicCircleProjectionGeometry.local(facing, baseW, baseH, x2, y2, lift + 0.001F);
        int alpha = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * alphaScale)));
        int cr = (argb >>> 16) & 0xFF;
        int cg = (argb >>> 8) & 0xFF;
        int cb = argb & 0xFF;
        // RenderType.lines() 的顶点格式是 POSITION_COLOR_NORMAL，比纯色 quad 多一个法线元素。
        // 少了它，提交顶点时就会抛 "Missing elements in vertex: Normal"，所以两个端点都得写明法线。
        // 法线取投影面朝外的方向（贴地朝上、贴墙朝外），和底板正面一致。
        Direction normalDir = facing == null ? Direction.UP : facing;
        float nx = normalDir.getStepX();
        float ny = normalDir.getStepY();
        float nz = normalDir.getStepZ();
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, a[0], a[1], a[2]).setColor(cr, cg, cb, alpha).setNormal(nx, ny, nz);
        consumer.addVertex(pose, b[0], b[1], b[2]).setColor(cr, cg, cb, alpha).setNormal(nx, ny, nz);
    }

    /** 投影贴墙上时也希望能从远一点看见，默认 64 格够用，这里放宽到 96 格。 */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
