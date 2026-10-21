package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 冻结反应的客户端外观：被冻结的生物身上裹一层半透明冰壳，身体四周长出冰晶，
 * 颜色和霜冰一致，配合原版的冻伤发蓝观感。
 */
public final class ClientFrozenVisuals {
    private static final Vec3 X = new Vec3(1, 0, 0), UP = new Vec3(0, 1, 0);
    /** 一次冻结最多渲染这么多冰壳，防止极端情况下顶点数失控。 */
    private static final int MAX_SHELLS = 32;
    private static final Map<Integer, Long> FROZEN = new HashMap<>();
    private static ClientLevel level;

    public static void accept(FrozenVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        if (minecraft.level == null) return;
        FROZEN.put(payload.entityId(), minecraft.level.getGameTime() + Math.max(20, payload.ticks()));
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        if (minecraft.level == null) return;
        long now = minecraft.level.getGameTime();
        FROZEN.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || FROZEN.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        ClientLevel world = minecraft.level;
        if (world == null) return;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f(event.getPoseStack().last().pose());
        long now = world.getGameTime();
        int drawn = 0;
        try (ByteBufferBuilder memory = new ByteBufferBuilder(16384)) {
            MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
            VertexConsumer out = source.getBuffer(SpellRenderTypes.Material.ICE.type);
            for (Map.Entry<Integer, Long> entry : FROZEN.entrySet()) {
                if (drawn >= MAX_SHELLS) break;
                Entity entity = world.getEntity(entry.getKey());
                if (!(entity instanceof LivingEntity living) || !entity.isAlive()) continue;
                // 第一人称不给玩家自己套壳，否则整块冰壳会把镜头糊住。
                if (minecraft.player == living && minecraft.options.getCameraType().isFirstPerson()) continue;
                if (entity.position().distanceToSqr(camera) > 64.0 * 64.0) continue;
                float pulse = (float)(0.86 + 0.14 * Math.sin(now * 0.35 + entry.getKey()));
                drawShell(out, matrix, camera, world, living.getBoundingBox(), pulse);
                drawn++;
            }
            source.endBatch();
        }
    }

    /** 一层贴着身体轮廓的冰壳，外加五根从身上长出来的冰棱。 */
    private static void drawShell(VertexConsumer out, Matrix4f matrix, Vec3 camera, ClientLevel level,
                                  AABB box, float pulse) {
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(box.getCenter()));
        Vec3 min = new Vec3(box.minX - 0.05, box.minY - 0.02, box.minZ - 0.05);
        Vec3 max = new Vec3(box.maxX + 0.05, box.maxY + 0.02, box.maxZ + 0.05);
        Vec3[] corner = new Vec3[8];
        for (int i = 0; i < 8; i++)
            corner[i] = new Vec3((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y,
                    (i & 4) == 0 ? min.z : max.z);
        int[][] faces = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}, {2, 6, 7, 3}, {0, 1, 5, 4}};
        for (int[] face : faces)
            quad(out, matrix, camera, corner[face[0]], corner[face[1]], corner[face[2]], corner[face[3]],
                    0x9FD8F5, 0.30F * pulse, light);

        double cx = box.getCenter().x, cz = box.getCenter().z;
        double height = box.getYsize();
        double radius = Math.max(box.getXsize(), box.getZsize()) * 0.5 + 0.04;
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2.0 / 5.0 + 0.4;
            Vec3 base = new Vec3(cx + Math.cos(angle) * radius,
                    box.minY + height * (0.28 + 0.16 * (i % 3)),
                    cz + Math.sin(angle) * radius);
            Vec3 axis = new Vec3(Math.cos(angle) * 0.5, 0.9, Math.sin(angle) * 0.5).normalize();
            crystal(out, matrix, camera, base, axis, 0.045, 0.20 + 0.05 * (i % 2),
                    0xDCF5FF, 0.55F * pulse, light);
        }
    }

    /** 四棱锥冰晶：上面四个三角面收出尖端，下面补一圈底面。 */
    private static void crystal(VertexConsumer out, Matrix4f matrix, Vec3 camera, Vec3 base, Vec3 axis,
                                double radius, double length, int color, float alpha, int light) {
        Vec3 side = axis.cross(Math.abs(axis.y) > 0.9 ? X : UP).normalize();
        Vec3 other = axis.cross(side).normalize();
        Vec3 tip = base.add(axis.scale(length));
        Vec3 bottom = base.subtract(axis.scale(length * 0.3));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0, b = (i + 1) * Math.PI / 2.0;
            Vec3 p1 = base.add(side.scale(Math.cos(a) * radius)).add(other.scale(Math.sin(a) * radius));
            Vec3 p2 = base.add(side.scale(Math.cos(b) * radius)).add(other.scale(Math.sin(b) * radius));
            triangle(out, matrix, camera, tip, p1, p2, color, alpha, light);
            triangle(out, matrix, camera, bottom, p2, p1, color, alpha * 0.75F, light);
        }
    }

    private static void quad(VertexConsumer out, Matrix4f matrix, Vec3 camera,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha, int light) {
        vertex(out, matrix, camera, a, 0.0F, 0.0F, color, alpha, light);
        vertex(out, matrix, camera, b, 1.0F, 0.0F, color, alpha, light);
        vertex(out, matrix, camera, c, 1.0F, 1.0F, color, alpha, light);
        vertex(out, matrix, camera, d, 0.0F, 1.0F, color, alpha, light);
    }

    private static void triangle(VertexConsumer out, Matrix4f matrix, Vec3 camera,
                                 Vec3 a, Vec3 b, Vec3 c, int color, float alpha, int light) {
        vertex(out, matrix, camera, a, 0.5F, 0.0F, color, alpha, light);
        vertex(out, matrix, camera, b, 1.0F, 0.5F, color, alpha, light);
        vertex(out, matrix, camera, c, 0.0F, 0.5F, color, alpha, light);
        vertex(out, matrix, camera, c, 0.0F, 0.5F, color, alpha, light);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 camera,
                               Vec3 at, float u, float v, int color, float alpha, int light) {
        Vec3 relative = at.subtract(camera);
        out.addVertex(matrix, (float)relative.x, (float)relative.y, (float)relative.z)
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255,
                        (int)(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F))
                .setUv(u, v).setLight(light);
    }

    private static void reset(ClientLevel next) {
        if (level != next) { level = next; FROZEN.clear(); }
    }

    private ClientFrozenVisuals() { }
}
