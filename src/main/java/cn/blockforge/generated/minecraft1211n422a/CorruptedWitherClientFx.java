package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 魔蚀凋零的客户端特效总控：屏幕暗角、镜头高频抖动、腐化雨、头颅拖尾。
 *
 * <p>数据来自服务端每 5 刻推一次的 {@link CorruptedWitherFxPayload}，两秒收不到就清屏；
 * 拖尾则是纯客户端记录——每个客户端自己维护本体最近 20 刻的位置，服务端不用背这个包。</p>
 *
 * <p>所有几何都是平滑面片（{@link CorruptedWitherFx}），走
 * {@link SpellRenderTypes#GLOW} 的自发光通道，不会出现原版那种马赛克粒子。</p>
 */
public final class CorruptedWitherClientFx {
    /** 拖尾保留多少刻。 */
    private static final int TRAIL_POINTS = 20;
    /** 数据过期时间：两秒半。 */
    private static final long PACKET_TTL_MILLIS = 2_500L;
    /** 雨点数量。 */
    private static final int RAIN_STREAKS = 150;
    /** 雨的渲染距离。 */
    private static final double RAIN_RADIUS = 26.0D;
    /** 最近一次收到包的时间（毫秒）。 */
    private static long lastPacketAt;
    /** 蓄力进度 0～1。 */
    private static volatile float charge;
    /** 镜头抖动强度 0～1。 */
    private static volatile float shake;
    /** 是否在下腐化雨。 */
    private static volatile boolean rain;
    /** 每个本体最近的位置轨迹。 */
    private static final Map<UUID, Deque<Vec3>> TRAILS = new HashMap<>();
    private static int ticker;

    public static void accept(CorruptedWitherFxPayload payload) {
        charge = payload.charge();
        shake = payload.shake();
        rain = payload.rain();
        lastPacketAt = System.currentTimeMillis();
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            TRAILS.clear();
            return;
        }
        if (System.currentTimeMillis() - lastPacketAt > PACKET_TTL_MILLIS) {
            charge = 0.0F;
            shake = 0.0F;
            rain = false;
        }
        ticker++;
        if (ticker % 1 != 0) return;
        List<CorruptedWither> withers = level.getEntitiesOfClass(CorruptedWither.class,
                minecraft.player.getBoundingBox().inflate(128.0D));
        java.util.Set<UUID> alive = new java.util.HashSet<>();
        for (CorruptedWither wither : withers) {
            UUID id = wither.getUUID();
            alive.add(id);
            Deque<Vec3> trail = TRAILS.computeIfAbsent(id, key -> new ArrayDeque<>(TRAIL_POINTS));
            trail.addLast(new Vec3(wither.getX(), wither.getY() + 2.0D, wither.getZ()));
            while (trail.size() > TRAIL_POINTS) trail.removeFirst();
        }
        TRAILS.keySet().removeIf(id -> !alive.contains(id));
    }

    /** 退出世界时清屏。 */
    public static void clear() {
        TRAILS.clear();
        charge = 0.0F;
        shake = 0.0F;
        rain = false;
        lastPacketAt = 0L;
    }

    // ---------------------------------------------------------------- 镜头
    /** 射线蓄力期间的轻微高频抖动，越接近发射越明显。 */
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.01F) return;
        double now = System.nanoTime() / 1.0E9D;
        float amplitude = shake * 0.55F;
        event.setYaw(event.getYaw() + (float)(Math.sin(now * 47.0D) * amplitude));
        event.setPitch(event.getPitch() + (float)(Math.sin(now * 61.0D + 1.7D) * amplitude));
        event.setRoll(event.getRoll() + (float)(Math.sin(now * 53.0D + 0.6D) * amplitude * 0.6D));
    }

    /** 腐化雨期间天空整体压暗、偏紫。 */
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!active()) return;
        event.setRed(event.getRed() * 0.38F + 0.05F);
        event.setGreen(event.getGreen() * 0.26F + 0.02F);
        event.setBlue(event.getBlue() * 0.42F + 0.10F);
    }

    /** 腐化雨期间把雾拉近，视线被压住。 */
    public static void fogDistance(ViewportEvent.RenderFog event) {
        if (!active()) return;
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 128.0F));
    }

    private static boolean active() {
        return rain && System.currentTimeMillis() - lastPacketAt <= PACKET_TTL_MILLIS;
    }

    // ---------------------------------------------------------------- 世界层特效
    public static void renderWorld(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        // 拖尾与腐化雨都放在「天气之后」这一层：原版的云和雨在它之前就已经画完，
        // 凋零的紫色光效才能稳稳压在它们上面，不会出现「飞在云里整层特效被云糊住」。
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        renderTrails(event);
        renderRain(event);
    }

    /** 本体身后的条带拖尾：越远越细越淡，代替「一路烟尘粒子」。 */
    private static void renderTrails(RenderLevelStageEvent event) {
        if (TRAILS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            PoseStack.Pose snapshot = pose.last();
            try (var memory = new com.mojang.blaze3d.vertex.ByteBufferBuilder(65_536)) {
                MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
                VertexConsumer glow = source.getBuffer(CorruptedWitherRenderTypes.glow());
                for (Deque<Vec3> trail : TRAILS.values()) {
                    drawTrail(snapshot, glow, new ArrayList<>(trail));
                }
                source.endBatch();
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawTrail(PoseStack.Pose pose, VertexConsumer glow, List<Vec3> points) {
        if (points.size() < 2) return;
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            Vec3 segment = b.subtract(a);
            double horizontal = Math.sqrt(segment.x * segment.x + segment.z * segment.z);
            double sideX;
            double sideZ;
            if (horizontal < 1.0E-3D) {
                sideX = 1.0D;
                sideZ = 0.0D;
            } else {
                sideX = -segment.z / horizontal;
                sideZ = segment.x / horizontal;
            }
            float t = i / (float)points.size();
            float width = 0.30F * (1.0F - t) + 0.02F;
            float alpha = 0.30F * (1.0F - t) * (1.0F - t);
            CorruptedWitherFx.ribbon(glow, pose,
                    (float)a.x, (float)a.y, (float)a.z, (float)b.x, (float)b.y, (float)b.z,
                    (float)sideX, (float)sideZ, width, width * 0.92F,
                    0.62F, 0.24F, 0.95F, alpha, alpha * 0.92F);
        }
    }

    /** 腐化魔素雨：一串朝下落的紫色细长面片，绕镜头均匀铺开。 */
    private static void renderRain(RenderLevelStageEvent event) {
        if (!active()) return;
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = Minecraft.getInstance().level.getGameTime() + partial;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            PoseStack.Pose snapshot = pose.last();
            Vec3 look = new Vec3(event.getCamera().getLookVector());
            Vec3 right = new Vec3(-look.z, 0.0D, look.x);
            if (right.lengthSqr() < 1.0E-4D) right = new Vec3(1.0D, 0.0D, 0.0D);
            right = right.normalize();
            try (var memory = new com.mojang.blaze3d.vertex.ByteBufferBuilder(98_304)) {
                MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
                VertexConsumer glow = source.getBuffer(CorruptedWitherRenderTypes.glow());
                for (int i = 0; i < RAIN_STREAKS; i++) {
                    double angle = (i * 0.618033988749D % 1.0D) * Math.PI * 2.0D;
                    double radius = 3.0D + ((i * 37) % 23);
                    double x = camera.x + Math.cos(angle) * radius;
                    double z = camera.z + Math.sin(angle) * radius;
                    double fall = (time * 0.75D + (i % 19) * 1.9D) % 24.0D;
                    double y = camera.y + 12.0D - fall;
                    double lengthRatio = 1.2D + (i % 5) * 0.35D;
                    float width = 0.018F + (i % 4) * 0.006F;
                    float alpha = 0.26F * (1.0F - (float)(fall / 24.0D)) + 0.04F;
                    CorruptedWitherFx.quad(glow, snapshot,
                            (float)(x - right.x * width), (float)y, (float)(z - right.z * width),
                            (float)(x + right.x * width), (float)y, (float)(z + right.z * width),
                            (float)(x + right.x * width), (float)(y - lengthRatio), (float)(z + right.z * width),
                            (float)(x - right.x * width), (float)(y - lengthRatio), (float)(z - right.z * width),
                            0.62F, 0.22F, 0.95F, alpha);
                }
                source.endBatch();
            }
        } finally {
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- HUD
    /** 屏幕边缘的紫色暗角：射线蓄力越久越深，平时不下雨就完全不画。 */
    public static void renderHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float strength = Math.max(charge, rain ? 0.35F : 0.0F);
        if (strength <= 0.01F) return;
        vignette(graphics, width, height, 0x7A2BC8, strength, 0.62D);
        if (charge > 0.55F) {
            // 快发射时再压一层更深的紫，屏幕像被能量挤住。
            vignette(graphics, width, height, 0x3A0A66, (charge - 0.55F) / 0.45F, 0.45D);
        }
    }

    private static void vignette(GuiGraphics graphics, int width, int height, int rgb,
                                 double strength, double maxAlpha) {
        int layers = 10;
        for (int i = 0; i < layers; i++) {
            double falloff = 1.0D - i / (double)layers;
            int alpha = (int)Math.round(255.0D * maxAlpha * strength * falloff * falloff * 0.34D);
            if (alpha <= 0) continue;
            int color = (alpha << 24) | (rgb & 0xFFFFFF);
            int band = 3 + i * 5;
            graphics.fill(0, 0, width, band, color);
            graphics.fill(0, height - band, width, height, color);
            graphics.fill(0, 0, band, height, color);
            graphics.fill(width - band, 0, width, height, color);
        }
    }

    private static float easeCharge() {
        return Mth.clamp(charge, 0.0F, 1.0F);
    }

    private CorruptedWitherClientFx() { }
}
