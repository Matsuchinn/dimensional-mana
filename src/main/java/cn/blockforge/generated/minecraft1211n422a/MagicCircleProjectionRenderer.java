package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
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
 * <p>颜色全部走顶点色（有元素粉就用元素色，没有就按功能块类型上色），每层稍微往外挪
 * 1/200 格，叠 4 层也不会互相打架；亮度按游戏刻做呼吸脉冲。</p>
 *
 * <p>第 4 阶段加了两样：运行中的刻印阵亮一档、呼吸更快；异面连线画成一条暗线，
 * 每 2 秒有一道光脉冲从上游游到下游（设计文档 12.1 / 12.2）。</p>
 *
 * <h2>1.21.1 的缓冲生命周期（r360 崩溃修复）</h2>
 *
 * <p>Sodium 环境下放投影必崩 {@code IllegalStateException: Not building!}，根因不是
 * 「手写 BufferBuilder」（这里从头到尾用的都是 {@link VertexConsumer}），而是
 * <b>跨 RenderType 持有 VertexConsumer</b>：1.21.1 的
 * {@code MultiBufferSource.BufferSource#getBuffer(RenderType)} 在你要<b>另一种</b>
 * RenderType 时，会立刻把前一种的 BufferBuilder {@code build()} 掉（提交并绘制），
 * 之后那个 builder 的 {@code building} 就是 false。旧实现先取 {@code debugQuads} 再取
 * {@code lines}，然后在层循环里两种类型交替写，写进已作废的 builder 就会在
 * {@code BufferBuilder.beginVertex()} 里抛 "Not building!"。没有 Sodium 时只是被刷新时机
 * 掩盖了，写法本身在任何环境下都是错的。</p>
 *
 * <p>现在两条规矩同时执行，任何环境都不会再踩：</p>
 * <ol>
 *   <li><b>自己开缓冲</b>：每次 render 用一个新的 {@link ByteBufferBuilder} 起一个独立的
 *       {@code MultiBufferSource}，画完自己 {@code endBatch()} 并关掉。全局那个
 *       BufferSource 是公共资源，别的模组（Sodium 会把刷新点拆得更碎）随时可能在中间
 *       把它刷掉，我们不共享它就没这个风险。</li>
 *   <li><b>取完马上写、写完再换类型</b>：每个同类型的批次都是
 *       {@code buffers.getBuffer(type)} 当场取、写完不收，绝不在换了类型之后回头用旧引用。
 *       同一类型的一次性整批提交，绘制调用数也没变多。</li>
 * </ol>
 */
public final class MagicCircleProjectionRenderer
        implements BlockEntityRenderer<MagicCircleProjectionBlockEntity> {

    /** 光点边长，单位是「画布像素」，2 像素在 32 画布上就是一个明显的小方块。 */
    private static final float NODE_PIXELS = 3.0F;
    private static final float LAYER_LIFT = 0.005F;
    /** 连线用带深度测试的线；缓存成静态常量，别每帧新建 RenderType（那样合不了批）。 */
    private static final RenderType LINE_TYPE = RenderType.lines();
    /** 点 / 面片用的纯色四边面通道（半透明、不剔除、按距离排序）。 */
    private static final RenderType QUAD_TYPE = RenderType.debugQuads();
    /**
     * 自己那块顶点缓冲的起始大小；几何量很小，超了 {@link ByteBufferBuilder} 会自己扩容，
     * 所以给个够用的小数字就行，不必每帧申请几 MB。
     */
    private static final int OWN_BUFFER_BYTES = 16 * 1024;
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
        // 刻印石板启动后：改画「分层立体阵」——底盘 / 连线 / 节点三层（需求：石板仅 Active 时渲染）。
        boolean layered = running && entity.sealed() && !entity.renderPacket().isEmpty();

        // 注意：参数 buffers 我们故意不用（原因见类注释）。自己的缓冲自己收，
        // 这样 Sodium 之类的渲染优化模组在任意时刻刷新全局缓冲都影响不到这里。
        try (ByteBufferBuilder memory = new ByteBufferBuilder(OWN_BUFFER_BYTES)) {
            MultiBufferSource.BufferSource own = MultiBufferSource.immediate(memory);
            try {
                draw(entity, partialTick, poseStack, own, facing, running, layered);
            } finally {
                own.endBatch();
            }
        }
    }

    private static void draw(MagicCircleProjectionBlockEntity entity, float partialTick, PoseStack poseStack,
                             MultiBufferSource buffers, Direction facing, boolean running, boolean layered) {
        long gameTime = entity.getLevel().getGameTime();

        // 异面连线先画：它在锚点之间，被锚点盖住反而更自然。
        // 线（lines 通道）与脉冲（四边面通道）分成两批，中间不交叉写。
        drawLinkLines(buffers, poseStack, entity);
        drawLinkPulses(buffers, poseStack, entity, gameTime, partialTick);

        if (layered) {
            renderLayered(entity, partialTick, poseStack, buffers, facing);
            return;
        }
        if (entity.isEmpty()) return;

        float phase = (gameTime + partialTick) * (running ? 0.16F : 0.09F);
        float pulse = 0.72F + 0.28F * Mth.sin(phase);
        int glow = running ? 0x33FFE9A8 : 0x22FFFFFF;
        int baseW = entity.baseW();
        int baseH = entity.baseH();
        float spanW = baseW * MagicCircleNetwork.TILE;
        float spanH = baseH * MagicCircleNetwork.TILE;

        for (int layerIndex = 0; layerIndex < entity.layers().size(); layerIndex++) {
            MagicCircleBlueprint blueprint = entity.layers().get(layerIndex);
            float lift = LAYER_LIFT * (layerIndex + 1);
            // 先画层与层之间的「底盘光晕」，让贴着墙的那一层有厚度感。
            drawQuad(buffers.getBuffer(QUAD_TYPE), poseStack, facing, baseW, baseH,
                    0, 0, spanW, spanH, lift, glow, pulse * 0.35F);
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
                drawLayerLinks(buffers, poseStack, facing, baseW, baseH, layer, lift, pulse);
                drawLayerNodes(buffers, poseStack, facing, baseW, baseH, layer, lift, pulse);
            }
        }
    }

    // ------------------------------------------------------------------ 画布内的点与线

    private static void drawLayerLinks(MultiBufferSource buffers, PoseStack poseStack, Direction facing,
                                       int baseW, int baseH, MagicCircleNetwork.Layer layer,
                                       float lift, float pulse) {
        if (layer.links().isEmpty()) return;
        int count = layer.nodes().size();
        // 当场取、写完不收：整批线一次提交，中间不碰别的类型。
        VertexConsumer lines = buffers.getBuffer(LINE_TYPE);
        for (MagicCircleNetwork.Link link : layer.links()) {
            if (link.from() < 0 || link.to() < 0 || link.from() >= count || link.to() >= count) continue;
            MagicCircleNetwork.Node from = layer.nodes().get(link.from());
            MagicCircleNetwork.Node to = layer.nodes().get(link.to());
            drawLine(lines, poseStack, facing, baseW, baseH,
                    from.x(), from.y(), to.x(), to.y(), lift, 0x66B6F0FF, pulse);
        }
    }

    private static void drawLayerNodes(MultiBufferSource buffers, PoseStack poseStack, Direction facing,
                                       int baseW, int baseH, MagicCircleNetwork.Layer layer,
                                       float lift, float pulse) {
        if (layer.nodes().isEmpty()) return;
        VertexConsumer quads = buffers.getBuffer(QUAD_TYPE);
        float half = NODE_PIXELS * 0.5F;
        for (MagicCircleNetwork.Node node : layer.nodes()) {
            drawQuad(quads, poseStack, facing, baseW, baseH,
                    node.x() - half, node.y() - half, node.x() + half, node.y() + half,
                    lift, MagicCircleBlueprint.colorOf(node), pulse);
        }
    }

    // ------------------------------------------------------------------ 异面连线

    /**
     * 画这个锚点连出去的线。为避免一条线被两端的渲染各画一遍，只在
     * 「本锚点坐标 &lt; 对方坐标」的那一头画。
     */
    private static void drawLinkLines(MultiBufferSource buffers, PoseStack poseStack,
                                      MagicCircleProjectionBlockEntity entity) {
        if (entity.links().isEmpty()) return;
        BlockPos self = entity.getBlockPos();
        Vec3 own = new Vec3(0.5, 0.5, 0.5);
        int baseAlpha = entity.running() ? 0x88 : 0x44;
        VertexConsumer lines = buffers.getBuffer(LINE_TYPE);
        for (BlockPos other : entity.links()) {
            if (self.compareTo(other) >= 0) continue; // 另一端会画
            Vec3 target = linkTarget(self, other);
            if (own.distanceTo(target) > MAX_LINK_RENDER_DISTANCE) continue;
            drawSegment(lines, poseStack, own, target, (baseAlpha << 24) | 0x7FE8FF);
        }
    }

    /** 光脉冲：每 2 秒（40 刻）从上游流到下游，宽 0.3 秒；用小十字面片，任何角度都看得见。 */
    private static void drawLinkPulses(MultiBufferSource buffers, PoseStack poseStack,
                                       MagicCircleProjectionBlockEntity entity, long gameTime, float partialTick) {
        if (!entity.running() || entity.links().isEmpty()) return;
        float t = ((gameTime % 40) + partialTick) / 40.0F;
        if (t > 0.3F) return;
        double phase = Math.max(0.0, Math.min(1.0, t / 0.3));

        BlockPos self = entity.getBlockPos();
        Vec3 own = new Vec3(0.5, 0.5, 0.5);
        VertexConsumer quads = null;
        for (BlockPos other : entity.links()) {
            if (self.compareTo(other) >= 0) continue;
            Vec3 target = linkTarget(self, other);
            if (own.distanceTo(target) > MAX_LINK_RENDER_DISTANCE) continue;
            if (quads == null) quads = buffers.getBuffer(QUAD_TYPE);
            drawCross(quads, poseStack, own.lerp(target, phase), 0.045F, 0xF2FFFFFF);
        }
    }

    /** 对方锚点相对本锚点（本锚点方块角）的位置，取方块中心。 */
    private static Vec3 linkTarget(BlockPos self, BlockPos other) {
        return new Vec3(other.getX() - self.getX() + 0.5,
                other.getY() - self.getY() + 0.5,
                other.getZ() - self.getZ() + 0.5);
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

    /**
     * 石板启动态：把渲染包画成三层立体阵。石板只有 1 格，画布按比例缩到贴面 0.98 格；
     * 距离降档与 {@link MagicCircleRenderer} 同规则（&gt;32 只画底盘、&gt;16 旋转 5 刻一跳）。
     *
     * <p>{@link MagicCircleArt} 内部是按「底盘 → 图集」的顺序一次只用一种 RenderType，
     * 和这里的「取完马上写」规矩一致，所以直接把自己的 BufferSource 交给它。</p>
     */
    private static void renderLayered(MagicCircleProjectionBlockEntity entity, float partialTick,
                                      PoseStack poseStack, MultiBufferSource buffers, Direction facing) {
        MagicCircleRenderPacket packet = entity.renderPacket();
        if (packet.isEmpty()) return;
        double time = entity.getLevel().getGameTime() + partialTick;

        // 距离 LOD：以方块中心到摄像机为准。
        net.minecraft.client.Camera camera =
                net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 center = entity.getBlockPos().getCenter();
        double distance = center.distanceTo(camera.getPosition());
        if (distance > LOD_SPIN_RANGE) time = Math.floor(time / 5.0) * 5.0;

        poseStack.pushPose();
        // 画布中心落在锚点方块中心，再沿法线方向移到贴面前外一点。
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.translate(facing.getStepX() * 0.5F, facing.getStepY() * 0.5F, facing.getStepZ() * 0.5F);
        float lift = 0.06F; // 贴面抬 0.06 格，不扎进底板
        if (distance > LOD_PLATE_RANGE) {
            MagicCircleArt.renderPlateOnly(packet, poseStack, buffers, facing, 0.98F, 0.98F, time, true, lift);
        } else {
            MagicCircleArt.render(packet, poseStack, buffers, facing, 0.98F, 0.98F, time, true, lift);
        }
        poseStack.popPose();
    }

    /** 石板渲染的距离阈值：中距旋转降频、远距只画底盘（与绘制台同一套规则）。 */
    private static final double LOD_SPIN_RANGE = 16.0;
    private static final double LOD_PLATE_RANGE = 32.0;

    /** 投影贴墙上时也希望能从远一点看见，默认 64 格够用，这里放宽到 96 格。 */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
