package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * 魔力雷达的客户端画面：给扫描到的每只怪物套一个不做深度测试的元素色轮廓。
 *
 * <p>轮廓颜色与「该往它身上招呼什么元素」直接挂钩：有易伤就亮易伤元素的颜色并闪烁，
 * 只有抗性就亮抗性元素的颜色，两者都没有则用默认紫色。混战里十几只怪挤在一起时，
 * 一眼就能挑出该先打谁。</p>
 */
public final class ClientManaRadarState {
    /** 最远画到多少格：32 格扫描半径，留一倍多的余量给「扫完再跑开」的情况。 */
    private static final double RENDER_RANGE = 80.0;
    private static final int OUTLINE_BUFFER = 262144;

    private static List<ManaRadarPayload.Target> targets = List.of();
    private static int remaining;
    private static ClientLevel level;

    public static void accept(ManaRadarPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        if (minecraft.level == null) return;
        targets = payload.targets();
        remaining = Math.max(1, payload.ticks());
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        if (minecraft.level == null) return;
        if (remaining <= 0) return;
        if (--remaining <= 0) clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        if (remaining <= 0 || targets.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        ClientLevel world = minecraft.level;
        if (world == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 易伤目标的轮廓每 4 刻亮暗切换一次，静止看也很扎眼。
        boolean blinkOn = (world.getGameTime() / 4L) % 2L == 0L;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            try (ByteBufferBuilder memory = new ByteBufferBuilder(OUTLINE_BUFFER)) {
                MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
                VertexConsumer lines = source.getBuffer(SpellRenderTypes.SENSE_OUTLINE);
                for (ManaRadarPayload.Target target : targets) {
                    Entity entity = world.getEntity(target.entityId());
                    if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                    Vec3 smooth = living.getPosition(partial);
                    AABB box = living.getBoundingBox().move(smooth.subtract(living.position()));
                    if (box.getCenter().distanceToSqr(camera) > RENDER_RANGE * RENDER_RANGE) continue;
                    float alpha = target.vulnerable() && !blinkOn ? 0.25F : 1.0F;
                    drawBox(pose, lines, box, target.color(), alpha);
                }
                source.endBatch();
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawBox(PoseStack pose, VertexConsumer lines, AABB box, int rgb, float alpha) {
        float red = ((rgb >> 16) & 255) / 255F;
        float green = ((rgb >> 8) & 255) / 255F;
        float blue = (rgb & 255) / 255F;
        LevelRenderer.renderLineBox(pose, lines, box.inflate(0.06), red, green, blue, 0.30F * alpha);
        LevelRenderer.renderLineBox(pose, lines, box.inflate(0.02), red, green, blue, 0.95F * alpha);
    }

    private static void clear() {
        targets = List.of();
        remaining = 0;
    }

    private static void reset(ClientLevel current) {
        if (level != current) {
            level = current;
            clear();
        }
    }

    private ClientManaRadarState() {}
}
