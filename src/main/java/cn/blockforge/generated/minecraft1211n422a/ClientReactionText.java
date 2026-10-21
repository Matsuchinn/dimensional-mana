package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端反应跳字。
 * <p>做法换了：不再往世界透明层里塞字体缓冲（那一层受渲染目标、姿态栈和透明度链影响，
 * 跳字经常整段不显示），而是每帧记下世界渲染用的投影矩阵，在 HUD 层把世界坐标自己算成
 * 屏幕坐标，再用 {@link GuiGraphics} 画字。只要投影算对，文字就一定能显示出来。
 */
public final class ClientReactionText {
    private static final List<FloatingText> ACTIVE = new ArrayList<>();
    /** 世界渲染这一帧实际用的视图矩阵和投影矩阵，顶点坐标相对相机。 */
    private static final Matrix4f LAST_MODELVIEW = new Matrix4f();
    private static final Matrix4f LAST_PROJECTION = new Matrix4f();
    private static boolean hasMatrices;
    private static ClientLevel level;
    private record FloatingText(int entityId, String reactionId, int color, long born,
                                float drift, float phase, Vec3 fallbackHead) { }

    public static void accept(ReactionVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        if (minecraft.level == null || payload.reactionId().length() > 24) return;
        if (ACTIVE.size() >= 48) ACTIVE.removeFirst();
        int seed = payload.entityId() * 31 + payload.reactionId().hashCode() * 17
                + (int)minecraft.level.getGameTime();
        float drift = ((seed & 15) - 7.5F) / 18.0F;
        float phase = ((seed >>> 4) & 31) * 0.37F;
        ACTIVE.add(new FloatingText(payload.entityId(), payload.reactionId(), payload.color(),
                minecraft.level.getGameTime(), drift, phase,
                new Vec3(payload.x(), payload.y(), payload.z())));
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        if (minecraft.level != null) ACTIVE.removeIf(text -> minecraft.level.getGameTime() - text.born() >= 28);
    }

    /** 世界渲染阶段只记录这一帧的矩阵，真正画字放到 HUD 层。 */
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        LAST_MODELVIEW.set(event.getModelViewMatrix());
        LAST_PROJECTION.set(event.getProjectionMatrix());
        hasMatrices = true;
    }

    public static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        ClientLevel world = minecraft.level;
        if (world == null || minecraft.player == null || minecraft.options.hideGui) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        int selfStack = 0;
        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        for (int index = 0; index < ACTIVE.size(); index++) {
            FloatingText text = ACTIVE.get(index);
            float age = world.getGameTime() - text.born() + partial;
            if (age < 0.0F) continue;
            Vec3 head = currentHead(world, text, partial);
            if (head.distanceToSqr(cameraPos) > 72.0 * 72.0) continue;
            int stack = 0;
            for (int before = 0; before < index; before++)
                if (ACTIVE.get(before).entityId() == text.entityId()) stack++;
            Vec3 point = head.add(0, 0.18 + age * 0.034 + stack * 0.25, 0);
            float fade = fade(age);
            float wobble = (float)Math.sin(age * 0.42F + text.phase()) * 4.0F + text.drift() * age * 0.6F;
            if (text.entityId() == minecraft.player.getId() && firstPerson) {
                // 自己的反应固定贴在屏幕最上方，明确和中央准心分开。
                drawText(graphics, minecraft, text, fade,
                        width / 2.0F + wobble, 14.0F + selfStack * 13.0F - Math.min(age, 10) * 0.18F,
                        popScale(age) * 1.12F);
                selfStack++;
                continue;
            }
            // 世界渲染的顶点坐标相对相机，所以先减相机位置，再过视图矩阵和投影矩阵。
            Vec3 relative = point.subtract(cameraPos);
            Vector4f clip = new Vector4f((float)relative.x, (float)relative.y, (float)relative.z, 1.0F);
            if (hasMatrices) {
                LAST_MODELVIEW.transform(clip);
                LAST_PROJECTION.transform(clip);
            } else {
                // 极端情况下还没抓到矩阵（进世界第一帧），临时用相机朝向兜底。
                new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf())).transform(clip);
                minecraft.gameRenderer.getProjectionMatrix(minecraft.options.fov().get()).transform(clip);
            }
            if (clip.w <= 0.05F) continue;
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (Float.isNaN(ndcX) || Float.isNaN(ndcY)) continue;
            if (Math.abs(ndcX) > 1.15F || Math.abs(ndcY) > 1.15F) continue;
            float screenX = (ndcX * 0.5F + 0.5F) * width;
            float screenY = (0.5F - ndcY * 0.5F) * height;
            // 越远字越小，远距离也读得清，近距离不会糊住屏幕。
            float distance = (float)Math.sqrt(head.distanceToSqr(cameraPos));
            float scale = popScale(age) * Mth.clamp(1.30F - distance * 0.016F, 0.55F, 1.25F);
            drawText(graphics, minecraft, text, fade, screenX + wobble, screenY, scale);
        }
    }

    private static void drawText(GuiGraphics graphics, Minecraft minecraft, FloatingText text,
                                 float fade, float x, float y, float scale) {
        Component label = Component.translatable("reaction.dimensional_mana." + text.reactionId());
        int width = minecraft.font.width(label);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(minecraft.font, label, Math.round(-width / 2.0F), 0, color(text, fade), true);
        pose.popPose();
    }

    private static Vec3 currentHead(ClientLevel level, FloatingText text, float partial) {
        Entity entity = level.getEntity(text.entityId());
        return entity instanceof LivingEntity living && entity.isAlive()
                ? entity.getPosition(partial).add(0, living.getBbHeight(), 0) : text.fallbackHead();
    }

    private static float fade(float age) {
        return Math.max(0.0F, Math.min(1.0F, (28.0F - age) / 8.0F));
    }
    private static float popScale(float age) { return age < 4.0F ? 1.42F - age * 0.105F : 1.0F; }
    private static int color(FloatingText text, float fade) {
        int alpha = Math.max(4, Math.min(255, (int)(fade * 255.0F)));
        return alpha << 24 | text.color() & 0xFFFFFF;
    }
    private static void reset(ClientLevel next) {
        if (level != next) { level = next; ACTIVE.clear(); }
    }
    private ClientReactionText() { }
}
