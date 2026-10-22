package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 魔素感知的客户端画面：把服务端标记到的魔晶矿与高魔素生物画成穿墙轮廓。
 *
 * <p>线框用的 {@link SpellRenderTypes#SENSE_OUTLINE} 关掉了深度测试，所以矿哪怕埋在
 * 三十格岩石下面、生物哪怕躲在山背后，外框也照样浮在屏幕上；曲线不做遮挡判断，
 * 因此玩家一眼就能看出“哪些方块值得挖、哪只怪身上魔素很高”。</p>
 *
 * <p>时长记成“还剩多少刻”自己往下数，不跟世界时间比大小；服务端在技能持续期间
 * 每秒补发一次剩余时长，所以换维度、重进世界或客户端时间跳变也不会让框提前消失。</p>
 */
public final class ClientManaSenseState {
    private static final int ORE_COLOR = 0xFFE08A;
    private static final int MOB_COLOR = 0xC77DFF;
    /** 区块扫描最远也就二十来格，留到 160 格足够覆盖抬头看山体、登高回望的场景。 */
    private static final double RENDER_RANGE = 160.0;
    /** 每十刻复核一次矿还在不在：逐帧查方块太浪费，挖掉后最多半秒框也就跟着没了。 */
    private static final int ORE_CHECK_INTERVAL = 10;
    /** 满打满算 96 个矿加几十只生物，两圈线框加起来一百多 KB，给足缓冲不跟容量较劲。 */
    private static final int OUTLINE_BUFFER = 262144;

    private static List<BlockPos> ores = List.of();
    private static List<UUID> mobs = List.of();
    private static int remaining;
    private static ClientLevel level;
    private static int checkClock;

    public static void accept(ManaSensePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        if (minecraft.level == null) return;
        ores = payload.ores();
        mobs = payload.mobs();
        remaining = Math.max(1, payload.ticks());
        checkClock = 0;
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        if (minecraft.level == null) return;
        if (remaining <= 0) return;
        if (--remaining <= 0) {
            clear();
            return;
        }
        if (++checkClock >= ORE_CHECK_INTERVAL) {
            checkClock = 0;
            pruneMinedOres(minecraft.level);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        if (remaining <= 0 || (ores.isEmpty() && mobs.isEmpty())) return;
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        ClientLevel world = minecraft.level;
        if (world == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double age = world.getGameTime() + partial;
        // 缓慢呼吸的亮度，避免一屏静止的线框看着像调试框。
        float pulse = (float)(0.72 + 0.28 * Math.sin(age * 0.18));
        Vec3 camera = event.getCamera().getPosition();
        // 事件里的 pose 是“相机相对坐标 → 屏幕”，所以先把世界坐标整体平移到相机原点。
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            try (ByteBufferBuilder memory = new ByteBufferBuilder(OUTLINE_BUFFER)) {
                MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
                VertexConsumer lines = source.getBuffer(SpellRenderTypes.SENSE_OUTLINE);
                for (BlockPos pos : ores) {
                    if (Vec3.atCenterOf(pos).distanceToSqr(camera) > RENDER_RANGE * RENDER_RANGE) continue;
                    drawBox(pose, lines, new AABB(pos), ORE_COLOR, pulse, 0.02);
                }
                for (UUID id : mobs) {
                    Entity entity = find(world, id);
                    if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                    // 第一人称不给自己套框，否则整个镜头都被自己的轮廓线糊住。
                    if (living == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) continue;
                    Vec3 smooth = living.getPosition(partial);
                    AABB box = living.getBoundingBox().move(smooth.subtract(living.position()));
                    if (box.getCenter().distanceToSqr(camera) > RENDER_RANGE * RENDER_RANGE) continue;
                    drawBox(pose, lines, box, MOB_COLOR, pulse, 0.05);
                }
                source.endBatch();
            }
        } finally {
            pose.popPose();
        }
    }

    /** 外圈淡、内圈实：既像发光描边，也能在被方块挡住时保留清晰的外轮廓。 */
    private static void drawBox(PoseStack pose, VertexConsumer lines, AABB box, int rgb, float pulse, double grow) {
        float red = ((rgb >> 16) & 255) / 255F;
        float green = ((rgb >> 8) & 255) / 255F;
        float blue = (rgb & 255) / 255F;
        LevelRenderer.renderLineBox(pose, lines, box.inflate(grow + 0.015), red, green, blue, 0.30F * pulse);
        LevelRenderer.renderLineBox(pose, lines, box.inflate(grow * 0.5), red, green, blue, 0.95F);
    }

    private static Entity find(ClientLevel world, UUID id) {
        for (Entity entity : world.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) return entity;
        }
        return null;
    }

    /** 挖掉的矿不再画框；区块还没送到客户端时先留着，别因为“暂时看不见”就把整片框抹掉。 */
    private static void pruneMinedOres(ClientLevel world) {
        if (ores.isEmpty()) return;
        List<BlockPos> alive = new ArrayList<>(ores.size());
        for (BlockPos pos : ores) {
            if (!world.hasChunkAt(pos) || stillOre(world, pos)) alive.add(pos);
        }
        if (alive.size() != ores.size()) ores = List.copyOf(alive);
    }

    /** 与服务端扫描的口径保持一致：各级魔晶矿和特级魔晶簇。 */
    private static boolean stillOre(ClientLevel world, BlockPos pos) {
        String id = world.getBlockState(pos).getBlock().builtInRegistryHolder().key().location().toString();
        if (!id.startsWith(GeneratedMod.MOD_ID + ":")) return false;
        return id.endsWith("_mana_crystal_ore")
                || id.equals(GeneratedMod.MOD_ID + ":special_mana_crystal_cluster");
    }

    private static void clear() {
        ores = List.of();
        mobs = List.of();
        remaining = 0;
        checkClock = 0;
    }

    private static void reset(ClientLevel current) {
        if (level != current) {
            level = current;
            clear();
        }
    }

    private ClientManaSenseState() { }
}
