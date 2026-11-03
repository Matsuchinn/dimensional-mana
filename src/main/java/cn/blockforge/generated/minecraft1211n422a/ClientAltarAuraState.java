package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * 古代魔法祭坛的"魔素富集区"光晕（纯客户端渲染）。
 *
 * <p>服务端每 100 刻把玩家附近登记的祭坛中心推过来（{@link AltarAuraPayload}）；
 * 这里在地面上画三层蓝色光：中央的小光圈、一到两道从中央缓缓漂向边界的脉冲光环、
 * 以及半径 12 格的边界圈——玩家不用开魔素探测器，肉眼就看得出加成区的范围。</p>
 *
 * <p><b>光影适配</b>：光晕全部画在 {@link SpellRenderTypes#GLOW} 通道上——
 * 该通道按 {@code dimensional_mana-rendercompat.toml} 的 {@code spell_effects_compat}
 * 选择输出层（默认半透明层，光影包按普通透明体处理，不会再出现"实心盘子"）。
 * 判定与 {@link AncientAltarData#AURA_RADIUS} 严格同口径：画到哪儿，加成就到哪儿。</p>
 */
public final class ClientAltarAuraState {
    /** 幽蓝色，和祭坛哭泣黑曜石的底色对得上。 */
    private static final float R = 0.42F, G = 0.72F, B = 1.0F;
    /** 服务端推一次管 24 秒：每 100 刻推一次，这里留两段余量，断流自动清屏。 */
    private static final long TTL_MILLIS = 24_000L;
    /** 超过这个距离不画，避免整座存档的祭坛同屏。 */
    private static final double RENDER_RANGE = 112.0;
    /** 一道脉冲从中央走到边界用 6 秒，同时跑两道、相位错开一半。 */
    private static final float PULSE_TICKS = 120.0F;

    private static volatile List<BlockPos> altars = List.of();
    private static volatile long lastPacketAt;

    public static void accept(AltarAuraPayload payload) {
        altars = List.copyOf(payload.altars());
        lastPacketAt = System.currentTimeMillis();
    }

    public static void tick(ClientTickEvent.Post event) {
        if (!altars.isEmpty() && System.currentTimeMillis() - lastPacketAt > TTL_MILLIS) {
            altars = List.of();
        }
    }

    /** 退出世界时清屏，免得下一个存档先闪一帧上个世界的祭坛。 */
    public static void clear() {
        altars = List.of();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        List<BlockPos> known = altars;
        if (known.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel world = minecraft.level;
        if (world == null) return;
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double age = world.getGameTime() + partial;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            try (ByteBufferBuilder memory = new ByteBufferBuilder(131072)) {
                var source = net.minecraft.client.renderer.MultiBufferSource.immediate(memory);
                VertexConsumer glow = source.getBuffer(SpellRenderTypes.GLOW);
                PoseStack.Pose snapped = pose.last();
                for (BlockPos altar : known) {
                    Vec3 center = Vec3.atCenterOf(altar);
                    if (center.distanceToSqr(camera) > RENDER_RANGE * RENDER_RANGE) continue;
                    drawAura(snapped, glow, center, age);
                }
                source.endBatch();
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawAura(PoseStack.Pose pose, VertexConsumer glow, Vec3 center, double age) {
        double y = center.y + 0.12;
        int radius = AncientAltarData.AURA_RADIUS;
        // 呼吸的亮度基线：微弱但不能像死图，慢速起伏最耐看。
        float breath = (float)(0.85 + 0.15 * Math.sin(age * 0.055));

        // 1) 边界圈：影响范围的"墙"，最稳的一道光。
        ring(pose, glow, center.x, y, center.z, radius - 0.05, 0.16, 0.20F * breath);
        // 2) 两道由内向外的脉冲：把"中央 → 边界"的流动画出来。
        for (int wave = 0; wave < 2; wave++) {
            double phase = ((age + wave * PULSE_TICKS * 0.5) % PULSE_TICKS) / PULSE_TICKS;
            double r = 0.5 + phase * (radius - 0.6);
            float alpha = (float)((1.0 - phase) * (1.0 - phase) * 0.18 * breath);
            ring(pose, glow, center.x, y, center.z, r, 0.22, alpha);
        }
        // 3) 中央光圈：碑台下聚拢的魔素，给脉冲一个出发点。
        ring(pose, glow, center.x, y, center.z, 0.55, 0.5, 0.30F * breath);
        ring(pose, glow, center.x, y, center.z, 0.2, 0.24, 0.42F * breath);
    }

    /** 地面水平光环：内外两条弧线之间的细长四边形带，加色通道下像一条光。 */
    private static void ring(PoseStack.Pose pose, VertexConsumer out, double cx, double cy, double cz,
                             double radius, double width, float alpha) {
        if (alpha <= 0.01F) return;
        double inner = Math.max(0.02, radius - width);
        double outer = radius + width;
        final int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            double cos0 = Math.cos(a0), sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1), sin1 = Math.sin(a1);
            vertex(out, pose, cx + cos0 * inner, cy, cz + sin0 * inner, alpha);
            vertex(out, pose, cx + cos0 * outer, cy, cz + sin0 * outer, alpha);
            vertex(out, pose, cx + cos1 * outer, cy, cz + sin1 * outer, alpha);
            vertex(out, pose, cx + cos1 * inner, cy, cz + sin1 * inner, alpha);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, double x, double y, double z, float alpha) {
        // GLOW 是 POSITION_COLOR 格式（雷击着色器自发光），不吃 lightmap，也不写法线。
        out.addVertex(pose, (float)x, (float)y, (float)z)
                .setColor(R, G, B, Math.clamp(alpha, 0.0F, 1.0F));
    }

    private ClientAltarAuraState() { }
}
