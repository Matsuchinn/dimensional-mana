package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 【虚无反噬】聚合体命中后的爆炸特效（纯客户端）。
 *
 * <p>服务端命中那一 tick 把「炸点、地面高度、红 / 蓝」打进
 * {@link CorruptedWitherBurstPayload} 发过来；这里把它塞进一个 {@@code List}，
 * 每客户端 tick 只做一件事：{{@code age++}、超时就从列表里删掉。渲染阶段不再做任何
 * 实体搜索、不做 AABB、不新建对象（每个爆炸的碎玻璃 / 流星参数在收包时一次算完）。</p>
 *
 * <p>整套东西没有一个原版粒子，全部是 {@code Tesselator} 式的四边形面片：</p>
 * <ul>
 *   <li><b>红色（物理）</b>：0.2 秒的十字星芒 → 0.6 秒从中心扩到 5 格的暗红金属裂纹环
 *       → 十几片全息碎玻璃缓慢旋转下落；</li>
 *   <li><b>蓝色（魔法）</b>：半径 5 格的半透明发光半球 + 两层高速反向旋转、随后碎成线段的
 *       六边形 / 八边形魔法阵 + 三圈紫色水波扭曲环 + 最多 20 条向下坠落的光柱，1.5 秒淡出。</li>
 * </ul>
 *
 * <p>图案来自 {@link CorruptedWitherRenderTypes#BURST_TEXTURE} 的 2×2 图集，颜色全部走顶点
 * ARGB 染色，UV 只负责挑图集里的哪一块。</p>
 */
public final class CorruptedWitherBurstFx {
    /** 同时最多挂多少个爆炸实例：再多就直接丢掉最老的，防止连发时把列表撑爆。 */
    private static final int MAX_BURSTS = 24;
    /** 超过这个距离的爆炸不画（和本体特效统一的 64 格上再留一点余量）。 */
    private static final float FX_RANGE = 96.0F;
    private static final float TAU = (float)(Math.PI * 2.0D);
    /** 满亮度光照：爆炸是自发光，不该被洞穴里的黑暗压暗。 */
    private static final int FULL_LIGHT = 0xF000F0;

    /** 红色：星芒 0.2 秒。 */
    private static final float RED_STAR_TICKS = 4.0F;
    /** 红色：裂纹环 0.6 秒。 */
    private static final float RED_RING_TICKS = 12.0F;
    /** 红色：碎玻璃 0.8 秒。 */
    private static final float RED_SHARD_TICKS = 16.0F;
    /** 红色：整个爆炸活 16 刻。 */
    private static final int RED_LIFE = 16;
    /** 蓝色：整体 1.5 秒。 */
    private static final int BLUE_LIFE = 30;
    /** 蓝色：半球在 0.5 秒内撑到最大，最后 0.8 秒淡出。 */
    private static final float BLUE_SHELL_GROW_TICKS = 10.0F;
    /** 蓝色：魔法阵从这一刻开始「碎成线」。 */
    private static final float BLUE_BREAK_START = 14.0F;
    private static final float BLUE_BREAK_TICKS = 12.0F;

    private static final List<Burst> BURSTS = new ArrayList<>();

    // ---------------------------------------------------------------- 数据
    /** 图集里的四块图案，UV 直接写死（0～1 是整张 128×128）。 */
    private enum Sprite {
        GLOW(0.0F, 0.0F, 0.5F, 0.5F),
        STAR(0.5F, 0.0F, 1.0F, 0.5F),
        SHARD(0.0F, 0.5F, 0.5F, 1.0F),
        STREAK(0.5F, 0.5F, 1.0F, 1.0F),
        /** 柔光贴图正中间那一小块：几乎不透明、颜色均匀，当作「纯色带」用。
         *  环带 / 半球如果直接铺满整张柔光图，采到的全是边缘透明像素，会几乎看不见。 */
        CORE(0.19F, 0.19F, 0.31F, 0.31F);

        final float u0, v0, u1, v1;

        Sprite(float u0, float v0, float u1, float v1) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }

    /** 一片碎玻璃：参数在收包时算好，渲染时只按年龄平移 / 旋转。 */
    private record Shard(float angle, float radius, float height, float size, float spin, float fall,
                         float delay) { }

    /** 一条下坠光柱。 */
    private record Meteor(float angle, float radius, float startY, float length, float speed, float delay) { }

    /** 一个爆炸实例。 */
    private static final class Burst {
        final Vec3 pos;
        final float groundY;
        final boolean blue;
        final Shard[] shards;
        final Meteor[] meteors;
        int age;

        Burst(Vec3 pos, float groundY, boolean blue) {
            this.pos = pos;
            this.groundY = groundY;
            this.blue = blue;
            Random random = new Random();
            if (blue) {
                this.shards = new Shard[0];
                this.meteors = new Meteor[20];
                for (int i = 0; i < meteors.length; i++) {
                    meteors[i] = new Meteor(random.nextFloat() * TAU,
                            0.4F + random.nextFloat() * 4.4F,
                            3.5F + random.nextFloat() * 5.5F,
                            1.2F + random.nextFloat() * 2.2F,
                            0.22F + random.nextFloat() * 0.22F,
                            random.nextFloat() * 6.0F);
                }
            } else {
                this.meteors = new Meteor[0];
                this.shards = new Shard[12];
                for (int i = 0; i < shards.length; i++) {
                    shards[i] = new Shard(random.nextFloat() * TAU,
                            0.8F + random.nextFloat() * 3.8F,
                            0.6F + random.nextFloat() * 2.6F,
                            0.22F + random.nextFloat() * 0.42F,
                            (random.nextFloat() - 0.5F) * 0.06F,
                            0.05F + random.nextFloat() * 0.08F,
                            random.nextFloat() * 6.0F);
                }
            }
        }

        int life() {
            return blue ? BLUE_LIFE : RED_LIFE;
        }
    }

    // ---------------------------------------------------------------- 生命周期
    public static void accept(CorruptedWitherBurstPayload payload) {
        if (BURSTS.size() >= MAX_BURSTS) BURSTS.remove(0);
        BURSTS.add(new Burst(payload.position(), payload.groundY(), payload.blue()));
    }

    /** 客户端每刻推进年龄；活过生命周期的立刻从列表里拿掉。 */
    public static void tick(ClientTickEvent.Post event) {
        if (BURSTS.isEmpty()) return;
        for (Iterator<Burst> iterator = BURSTS.iterator(); iterator.hasNext(); ) {
            Burst burst = iterator.next();
            if (++burst.age > burst.life()) iterator.remove();
        }
    }

    /** 退出世界时清空。 */
    public static void clear() {
        BURSTS.clear();
    }

    // ---------------------------------------------------------------- 渲染入口
    public static void render(RenderLevelStageEvent event) {
        if (BURSTS.isEmpty()) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Vec3 camera = event.getCamera().getPosition();
        Vec3 right = new Vec3(event.getCamera().getLeftVector()).scale(-1.0D);
        Vec3 up = new Vec3(event.getCamera().getUpVector());
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            PoseStack.Pose snapshot = pose.last();
            try (ByteBufferBuilder memory = new ByteBufferBuilder(1_048_576)) {
                MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
                VertexConsumer glow = source.getBuffer(CorruptedWitherRenderTypes.BURST_GLOW);
                VertexConsumer metal = source.getBuffer(CorruptedWitherRenderTypes.BURST_METAL);
                for (Burst burst : BURSTS) {
                    if (burst.pos.distanceToSqr(camera) > FX_RANGE * FX_RANGE) continue;
                    float time = burst.age + partial;
                    if (burst.blue) {
                        renderBlue(snapshot, glow, burst, time, right, up);
                    } else {
                        renderRed(snapshot, glow, metal, burst, time, right, up);
                    }
                }
                source.endBatch();
            }
        } finally {
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- 红色：物理爆炸
    private static void renderRed(PoseStack.Pose pose, VertexConsumer glow, VertexConsumer metal,
                                  Burst burst, float time, Vec3 right, Vec3 up) {
        Vec3 ground = new Vec3(burst.pos.x, burst.groundY + 0.02D, burst.pos.z);
        Vec3 spark = new Vec3(burst.pos.x, burst.pos.y + 0.7D, burst.pos.z);

        // 1) 十字星芒：0.2 秒内极速扩张，同时迅速淡掉。
        float star = Mth.clamp(time / RED_STAR_TICKS, 0.0F, 1.0F);
        if (time < RED_STAR_TICKS) {
            float grow = 0.7F + star * 7.5F;
            float fade = (1.0F - star) * (1.0F - star);
            billboard(glow, pose, spark, right, up, grow, grow, Sprite.STAR,
                    1.0F, 0.34F, 0.26F, 0.95F * fade);
            // 两根交叉光条把「十字」的骨架拉出来。
            billboard(glow, pose, spark, right, up, grow * 2.6F, grow * 0.12F, Sprite.GLOW,
                    1.0F, 0.55F, 0.36F, 0.70F * fade);
            billboard(glow, pose, spark, right, up, grow * 0.12F, grow * 2.6F, Sprite.GLOW,
                    1.0F, 0.55F, 0.36F, 0.70F * fade);
        }

        // 2) 暗红金属裂纹环：从中心扩到 5 格再淡出。
        float ring = Mth.clamp(time / RED_RING_TICKS, 0.0F, 1.0F);
        if (ring < 1.0F) {
            float radius = 0.5F + easeOutCubic(ring) * 4.9F;
            float fade = 1.0F - ring;
            // 环面本体：暗红、普通混合，才有「金属被烧红」的暗部。
            groundRing(metal, pose, ground, radius * 0.70F, radius, 56, Sprite.CORE,
                    0.42F, 0.05F, 0.07F, 0.90F * fade);
            // 内侧高光带：亮橙红，制造金属反光。
            groundRing(metal, pose, ground, radius * 0.70F, radius * 0.77F, 56, Sprite.CORE,
                    1.0F, 0.34F, 0.22F, 0.65F * fade);
            // 放射状裂纹：长短、粗细、明暗都错开，才不像一张均匀的圆盘。
            int cracks = 36;
            for (int i = 0; i < cracks; i++) {
                float angle = i * TAU / cracks + 0.37F;
                float inner = radius * (0.12F + 0.30F * hash(i * 7 + 3));
                float length = radius * (0.42F + 0.62F * hash(i * 13 + 1));
                float shine = 0.55F + 0.45F * Mth.sin(angle * 5.0F + time * 0.8F + i * 1.7F);
                groundSpike(metal, pose, ground, angle, inner, inner + length,
                        0.045F + 0.045F * hash(i * 5 + 2),
                        0.35F + 0.65F * shine, 0.05F + 0.14F * shine, 0.05F + 0.06F * shine,
                        0.85F * fade);
            }
            // 外缘一圈细亮边，让 5 格边界看得清楚。
            groundRing(glow, pose, ground, radius * 0.96F, radius * 1.02F, 64, Sprite.CORE,
                    1.0F, 0.32F, 0.20F, 0.55F * fade);
        }

        // 3) 全息碎玻璃：十几片在 5 格内边转边慢慢往下掉。
        for (Shard shard : burst.shards) {
            float local = time - shard.delay();
            if (local <= 0.0F || local >= RED_SHARD_TICKS) continue;
            float progress = local / RED_SHARD_TICKS;
            float spread = shard.radius() * (0.9F + progress * 0.45F);
            float x = (float)burst.pos.x + Mth.cos(shard.angle()) * spread;
            float z = (float)burst.pos.z + Mth.sin(shard.angle()) * spread;
            float y = burst.groundY + shard.height() - local * shard.fall();
            // 缓慢自转：每秒约 15～40 度，慢慢翻着往下掉。
            float spin = shard.angle() + local * shard.spin();
            float cos = Mth.cos(spin);
            float sin = Mth.sin(spin);
            Vec3 shardRight = right.scale(cos).add(up.scale(sin));
            Vec3 shardUp = up.scale(cos).subtract(right.scale(sin));
            float size = shard.size() * (1.0F + progress * 0.8F);
            float alpha = (progress < 0.15F ? progress / 0.15F : 1.0F - progress) * 0.85F;
            billboard(glow, pose, new Vec3(x, y, z), shardRight, shardUp, size, size * 0.6F,
                    Sprite.SHARD, 0.95F, 0.72F, 0.85F, alpha);
        }
    }

    // ---------------------------------------------------------------- 蓝色：魔法爆炸
    private static void renderBlue(PoseStack.Pose pose, VertexConsumer glow, Burst burst, float time,
                                   Vec3 right, Vec3 up) {
        Vec3 center = burst.pos;
        Vec3 ground = new Vec3(burst.pos.x, burst.groundY + 0.03D, burst.pos.z);

        // 1) 半径 5 格的半透明发光半球。
        float shellFade = 1.0F - Mth.clamp((time - 12.0F) / 16.0F, 0.0F, 1.0F);
        if (shellFade > 0.01F) {
            float radius = 5.0F * easeOutCubic(Mth.clamp(time / BLUE_SHELL_GROW_TICKS, 0.0F, 1.0F));
            if (radius > 0.2F) hemisphere(glow, pose, center, radius, 0.34F * shellFade);
        }

        // 2) 两层六边形 / 八边形魔法阵：极快反向旋转，后半段碎成往外飘的线段。
        if (time > 2.0F) {
            float breakT = Mth.clamp((time - BLUE_BREAK_START) / BLUE_BREAK_TICKS, 0.0F, 1.0F);
            float alpha = (1.0F - breakT) * 0.95F;
            Vec3 ringCenter = new Vec3(center.x, center.y + 1.1D, center.z);
            billboardPolygon(glow, pose, ringCenter, right, up, 2.55F, 6,
                    time * 0.85F, 0.055F, breakT, 0.66F, 0.48F, 1.0F, alpha);
            billboardPolygon(glow, pose, ringCenter, right, up, 3.65F, 8,
                    -time * 1.20F, 0.045F, breakT, 0.88F, 0.70F, 1.0F, alpha * 0.85F);
        }

        // 3) 紫色水波纹：三圈错开扩散到 5 格，半径带正弦抖动。
        for (int i = 0; i < 3; i++) {
            float ripple = Mth.clamp((time - i * 3.0F) / 15.0F, 0.0F, 1.0F);
            if (ripple <= 0.0F || ripple >= 1.0F) continue;
            float radius = 0.4F + easeOutCubic(ripple) * 4.8F;
            wavyRing(glow, pose, ground, radius, 0.06F + 0.06F * (1.0F - ripple), 64,
                    time * 0.18F + i * 2.1F, 0.55F, 0.24F, 1.0F, (1.0F - ripple) * 0.32F);
        }

        // 4) 残留的「流星雨」：最多 20 条平行竖直光柱向下坠，1.5 秒内淡出。
        float fallFade = 1.0F - Mth.clamp((time - 18.0F) / 12.0F, 0.0F, 1.0F);
        if (fallFade > 0.01F) {
            for (Meteor meteor : burst.meteors) {
                float local = time - meteor.delay();
                if (local <= 0.0F) continue;
                float y = (float)burst.pos.y + meteor.startY() - local * meteor.speed();
                if (y < burst.groundY - 0.6F) continue;
                float alpha = Math.min(1.0F, local / 4.0F) * fallFade * 0.72F;
                float x = (float)burst.pos.x + Mth.cos(meteor.angle()) * meteor.radius();
                float z = (float)burst.pos.z + Mth.sin(meteor.angle()) * meteor.radius();
                billboard(glow, pose, new Vec3(x, y - meteor.length() * 0.5F, z), right, up,
                        0.045F, meteor.length() * 0.5F, Sprite.STREAK, 0.60F, 0.42F, 1.0F, alpha);
            }
        }
    }

    // ---------------------------------------------------------------- 几何工具
    private static void vertex(VertexConsumer out, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               float r, float g, float b, float a) {
        out.addVertex(pose, x, y, z)
                .setColor(Mth.clamp(r, 0.0F, 1.0F), Mth.clamp(g, 0.0F, 1.0F),
                        Mth.clamp(b, 0.0F, 1.0F), Mth.clamp(a, 0.0F, 1.0F))
                .setUv(u, v)
                .setLight(FULL_LIGHT);
    }

    /** 相机平面上的广告牌四边形：{@code right / up} 是相机的横向和纵向单位向量。 */
    private static void billboard(VertexConsumer out, PoseStack.Pose pose, Vec3 center, Vec3 right, Vec3 up,
                                  float halfWidth, float halfHeight, Sprite sprite,
                                  float r, float g, float b, float a) {
        Vec3 x0 = center.add(right.scale(-halfWidth)).add(up.scale(-halfHeight));
        Vec3 x1 = center.add(right.scale(halfWidth)).add(up.scale(-halfHeight));
        Vec3 x2 = center.add(right.scale(halfWidth)).add(up.scale(halfHeight));
        Vec3 x3 = center.add(right.scale(-halfWidth)).add(up.scale(halfHeight));
        vertex(out, pose, (float)x0.x, (float)x0.y, (float)x0.z, sprite.u0, sprite.v1, r, g, b, a);
        vertex(out, pose, (float)x1.x, (float)x1.y, (float)x1.z, sprite.u1, sprite.v1, r, g, b, a);
        vertex(out, pose, (float)x2.x, (float)x2.y, (float)x2.z, sprite.u1, sprite.v0, r, g, b, a);
        vertex(out, pose, (float)x3.x, (float)x3.y, (float)x3.z, sprite.u0, sprite.v0, r, g, b, a);
    }

    /** 水平圆环带（地面裂纹环、水波纹共用）。 */
    private static void groundRing(VertexConsumer out, PoseStack.Pose pose, Vec3 center,
                                   float inner, float outer, int segments, Sprite sprite,
                                   float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            float angle0 = i * TAU / segments;
            float angle1 = (i + 1) * TAU / segments;
            float c0 = Mth.cos(angle0);
            float s0 = Mth.sin(angle0);
            float c1 = Mth.cos(angle1);
            float s1 = Mth.sin(angle1);
            float x = (float)center.x;
            float y = (float)center.y;
            float z = (float)center.z;
            vertex(out, pose, x + c0 * inner, y, z + s0 * inner, sprite.u0, sprite.v1, r, g, b, a);
            vertex(out, pose, x + c0 * outer, y, z + s0 * outer, sprite.u0, sprite.v0, r, g, b, a);
            vertex(out, pose, x + c1 * outer, y, z + s1 * outer, sprite.u1, sprite.v0, r, g, b, a);
            vertex(out, pose, x + c1 * inner, y, z + s1 * inner, sprite.u1, sprite.v1, r, g, b, a);
        }
    }

    /** 从内圈射向外圈的一道裂纹尖刺（地面上的窄条）。 */
    private static void groundSpike(VertexConsumer out, PoseStack.Pose pose, Vec3 center, float angle,
                                    float inner, float outer, float halfWidth,
                                    float r, float g, float b, float a) {
        float dirX = Mth.cos(angle);
        float dirZ = Mth.sin(angle);
        float sideX = -dirZ;
        float sideZ = dirX;
        float x = (float)center.x;
        float y = (float)center.y;
        float z = (float)center.z;
        vertex(out, pose, x + dirX * inner - sideX * halfWidth, y, z + dirZ * inner - sideZ * halfWidth,
                Sprite.CORE.u0, Sprite.CORE.v0, r, g, b, a);
        vertex(out, pose, x + dirX * inner + sideX * halfWidth, y, z + dirZ * inner + sideZ * halfWidth,
                Sprite.CORE.u1, Sprite.CORE.v0, r, g, b, a);
        vertex(out, pose, x + dirX * outer + sideX * halfWidth * 0.15F, y,
                z + dirZ * outer + sideZ * halfWidth * 0.15F, Sprite.CORE.u1, Sprite.CORE.v1, r, g, b, a);
        vertex(out, pose, x + dirX * outer - sideX * halfWidth * 0.15F, y,
                z + dirZ * outer - sideZ * halfWidth * 0.15F, Sprite.CORE.u0, Sprite.CORE.v1, r, g, b, a);
    }

    /** 半径带正弦扰动的波纹环，看起来像水面上扩散的圈。 */
    private static void wavyRing(VertexConsumer out, PoseStack.Pose pose, Vec3 center, float radius,
                                 float thickness, int segments, float phase,
                                 float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            float angle0 = i * TAU / segments;
            float angle1 = (i + 1) * TAU / segments;
            float wobble0 = 1.0F + 0.07F * Mth.sin(angle0 * 8.0F + phase);
            float wobble1 = 1.0F + 0.07F * Mth.sin(angle1 * 8.0F + phase);
            float inner0 = radius - thickness;
            float inner1 = radius - thickness;
            float c0 = Mth.cos(angle0);
            float s0 = Mth.sin(angle0);
            float c1 = Mth.cos(angle1);
            float s1 = Mth.sin(angle1);
            float x = (float)center.x;
            float y = (float)center.y;
            float z = (float)center.z;
            vertex(out, pose, x + c0 * inner0, y, z + s0 * inner0, Sprite.CORE.u0, Sprite.CORE.v1, r, g, b, a);
            vertex(out, pose, x + c0 * radius * wobble0, y, z + s0 * radius * wobble0,
                    Sprite.CORE.u0, Sprite.CORE.v0, r, g, b, a);
            vertex(out, pose, x + c1 * radius * wobble1, y, z + s1 * radius * wobble1,
                    Sprite.CORE.u1, Sprite.CORE.v0, r, g, b, a);
            vertex(out, pose, x + c1 * inner1, y, z + s1 * inner1, Sprite.CORE.u1, Sprite.CORE.v1, r, g, b, a);
        }
    }

    /** 半球网格：纬线 7 段、经线 28 段，越靠顶部越透明。 */
    private static void hemisphere(VertexConsumer out, PoseStack.Pose pose, Vec3 center, float radius,
                                   float alpha) {
        int latitude = 7;
        int longitude = 28;
        for (int i = 0; i < latitude; i++) {
            double phi0 = Math.PI * 0.5D * i / latitude;
            double phi1 = Math.PI * 0.5D * (i + 1) / latitude;
            float ring0 = (float)Math.cos(phi0) * radius;
            float ring1 = (float)Math.cos(phi1) * radius;
            float y0 = (float)Math.sin(phi0) * radius;
            float y1 = (float)Math.sin(phi1) * radius;
            float alpha0 = alpha * (1.0F - 0.55F * i / (float)latitude);
            float alpha1 = alpha * (1.0F - 0.55F * (i + 1) / (float)latitude);
            float v0 = Sprite.CORE.v0 + (Sprite.CORE.v1 - Sprite.CORE.v0) * i / (float)latitude;
            float v1 = Sprite.CORE.v0 + (Sprite.CORE.v1 - Sprite.CORE.v0) * (i + 1) / (float)latitude;
            for (int j = 0; j < longitude; j++) {
                float angle0 = j * TAU / longitude;
                float angle1 = (j + 1) * TAU / longitude;
                float c0 = Mth.cos(angle0);
                float s0 = Mth.sin(angle0);
                float c1 = Mth.cos(angle1);
                float s1 = Mth.sin(angle1);
                float u0 = Sprite.CORE.u0 + (Sprite.CORE.u1 - Sprite.CORE.u0) * j / (float)longitude;
                float u1 = Sprite.CORE.u0 + (Sprite.CORE.u1 - Sprite.CORE.u0) * (j + 1) / (float)longitude;
                float x = (float)center.x;
                float y = (float)center.y;
                float z = (float)center.z;
                // 颜色统一偏冷紫，透明度按纬度衰减：下面实、上面虚。
                vertex(out, pose, x + c0 * ring0, y, z + s0 * ring0, u0, v1, 0.44F, 0.40F, 1.0F, alpha0);
                vertex(out, pose, x + c0 * ring1, y + y1, z + s0 * ring1, u0, v0, 0.44F, 0.40F, 1.0F, alpha1);
                vertex(out, pose, x + c1 * ring1, y + y1, z + s1 * ring1, u1, v0, 0.44F, 0.40F, 1.0F, alpha1);
                vertex(out, pose, x + c1 * ring0, y, z + s1 * ring0, u1, v1, 0.44F, 0.40F, 1.0F, alpha0);
            }
        }
    }

    /**
     * 相机平面上的多边形「魔法阵」：每条边画成一根细光条；{@code breakT} 越大，
     * 光条越往外飘、越短、越淡，整圈就像被打碎成一根根线段。
     */
    private static void billboardPolygon(VertexConsumer out, PoseStack.Pose pose, Vec3 center,
                                         Vec3 right, Vec3 up, float radius, int sides, float rotation,
                                         float halfWidth, float breakT,
                                         float r, float g, float b, float a) {
        Vec3 look = right.cross(up);
        int count = Math.max(3, sides);
        for (int i = 0; i < count; i++) {
            float angle0 = rotation + i * TAU / count;
            float angle1 = rotation + (i + 1) * TAU / count;
            float push = breakT * (0.35F + 0.55F * (i % 3));
            float grown = radius * (1.0F + push * 0.28F);
            Vec3 p0 = center.add(right.scale(Mth.cos(angle0) * grown)).add(up.scale(Mth.sin(angle0) * grown));
            Vec3 p1 = center.add(right.scale(Mth.cos(angle1) * grown)).add(up.scale(Mth.sin(angle1) * grown));
            // 破碎：朝边的中点收缩，并整体往外、往上飘一点。
            float shrink = 1.0F - 0.72F * breakT;
            Vec3 mid = p0.add(p1).scale(0.5D);
            Vec3 a0 = mid.add(p0.subtract(mid).scale(shrink)).add(0.0D, breakT * 0.5D, 0.0D);
            Vec3 a1 = mid.add(p1.subtract(mid).scale(shrink)).add(0.0D, breakT * 0.5D, 0.0D);
            Vec3 direction = a1.subtract(a0);
            if (direction.lengthSqr() < 1.0E-6D) continue;
            Vec3 side = direction.cross(look).normalize().scale(halfWidth);
            float alpha = a * (1.0F - breakT * 0.85F);
            Vec3 q0 = a0.subtract(side);
            Vec3 q1 = a0.add(side);
            Vec3 q2 = a1.add(side);
            Vec3 q3 = a1.subtract(side);
            vertex(out, pose, (float)q0.x, (float)q0.y, (float)q0.z, Sprite.STREAK.u0, Sprite.STREAK.v1, r, g, b, alpha);
            vertex(out, pose, (float)q1.x, (float)q1.y, (float)q1.z, Sprite.STREAK.u1, Sprite.STREAK.v1, r, g, b, alpha);
            vertex(out, pose, (float)q2.x, (float)q2.y, (float)q2.z, Sprite.STREAK.u1, Sprite.STREAK.v0, r, g, b, alpha);
            vertex(out, pose, (float)q3.x, (float)q3.y, (float)q3.z, Sprite.STREAK.u0, Sprite.STREAK.v0, r, g, b, alpha);
        }
    }

    private static float easeOutCubic(float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }

    /** 给裂纹用的稳定伪随机：同一个下标每帧返回同一个数，不会闪。 */
    private static float hash(int seed) {
        float value = Mth.sin(seed * 12.9898F) * 43758.5453F;
        return value - (float)Math.floor(value);
    }

    private CorruptedWitherBurstFx() { }
}
