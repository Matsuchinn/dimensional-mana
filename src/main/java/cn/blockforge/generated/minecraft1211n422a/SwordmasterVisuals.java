package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 流浪剑士的客户端视觉：刀光拖尾、瞬身残影、蓄力金光与冲刺气流。
 *
 * <p>三条硬性约束（防止把客户端拖垮）：
 * <ol>
 *   <li>拖尾节点池每只剑士最多 {@value #MAX_NODES} 个，超过就丢最旧的；</li>
 *   <li>摄像机离剑士超过 {@value #TRAIL_RANGE} 格就停止记录并停止绘制；</li>
 *   <li>残影和粒子只在 {@value #EFFECT_RANGE} 格内生成，残影上限 {@value #MAX_GHOSTS} 个。</li>
 * </ol>
 *
 * <p>刀光坐标怎么来的：{@link SwordmasterRenderer} 在渲染到 {@code sword} 骨骼时，把剑柄/剑尖两个
 * 模型空间坐标乘上当前 PoseStack 的矩阵，就得到了「真实的」剑刃两端世界坐标（不是心算的角度），
 * 再在 {@link RenderLevelStageEvent} 的 AFTER_ENTITIES 阶段用 {@link RenderType#lightning()} 画成
 * 带透明度渐变的四边形条带。</p>
 */
public final class SwordmasterVisuals {
    private static final int MAX_NODES = 8;
    private static final long NODE_LIFETIME_MS = 140L;
    /** 刀光从剑身靠近剑尖的这一段开始画（0 = 剑柄、1 = 剑尖），不再整把剑都拖一条白光。 */
    private static final float TRAIL_BASE_RATIO = 0.6F;
    /**
     * 相邻两个采样点最多隔这么远（格）。超过就当成「剑尖跳了一下」——瞬身、冲刺、
     * 或者中途换了剑——直接把这串历史断掉重来，绝不许一条刀光横跨十几格。
     */
    private static final double TRAIL_MAX_GAP = 1.8D;
    /** 刀光最浓处的透明度：以前是 0.8，整条刀光像一块发光的板子挡住了人。 */
    private static final float TRAIL_MAX_ALPHA = 0.42F;
    private static final double TRAIL_RANGE = 24.0D;
    private static final double EFFECT_RANGE = 32.0D;
    private static final int MAX_GHOSTS = 12;
    private static final long GHOST_LIFETIME_MS = 500L;
    private static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.28F), 1.35F);

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static final Map<Integer, Vec3> LAST_TIP = new HashMap<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final Map<Integer, WanderingSwordmaster.Action> LAST_ACTION = new HashMap<>();

    /** 剑柄 / 剑尖在模型空间的位置，第一次渲染时从剑骨骼的方块包围盒算出来。 */
    private static boolean boundsReady;
    private static float baseX, baseY, baseZ, tipX, tipY, tipZ;

    /** 渲染残影时临时打开：让 {@link SwordmasterRenderer} 换成半透明蓝光材质。 */
    static boolean ghostPass;
    static float ghostAlpha = 0.5F;

    private record Node(Vec3 base, Vec3 tip, long stamp) {}

    private static final class Trail {
        final Deque<Node> nodes = new ArrayDeque<>();
        Vec3 lastPos = Vec3.ZERO;
    }

    private record Ghost(int entityId, Vec3 at, float yaw, long stamp) {}

    private SwordmasterVisuals() { }

    // ---------------------------------------------------------------- 拖尾采集
    /**
     * 由渲染器在画到剑骨骼时调用（每帧一次）。
     *
     * @param pose 当前 PoseStack 的矩阵，已经把「模型空间 → 摄像机相对坐标」都算进去了
     */
    public static void captureSword(WanderingSwordmaster entity, GeoBone bone, Matrix4f pose) {
        if (ghostPass || !"sword".equals(bone.getName())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (entity.position().distanceToSqr(camera) > TRAIL_RANGE * TRAIL_RANGE) {
            // 离得太远：丢掉历史，也不再更新，省掉每帧的矩阵乘法和显存。
            TRAILS.remove(entity.getId());
            return;
        }
        if (!boundsReady && !computeBounds(bone)) return;

        Vector3f base = pose.transformPosition(new Vector3f(baseX, baseY, baseZ));
        Vector3f tip = pose.transformPosition(new Vector3f(tipX, tipY, tipZ));
        // 渲染时的坐标是「相对摄像机」的，这里加回摄像机位置，存成世界坐标，
        // 玩家走动时旧节点才不会跟着画面一起漂。
        Vec3 baseWorld = new Vec3(base.x, base.y, base.z).add(camera);
        Vec3 tipWorld = new Vec3(tip.x, tip.y, tip.z).add(camera);
        LAST_TIP.put(entity.getId(), tipWorld);

        // 刀光只属于真正挥剑/冲锋的有效帧。待机、走路、格挡和蓄力阶段仍更新剑尖位置，
        // 供蓄力粒子使用，但立即清掉历史节点，绝不会在角色头顶留下常驻光柱。
        if (!entity.shouldRenderSwordTrail()) {
            TRAILS.remove(entity.getId());
            return;
        }

        Trail trail = TRAILS.computeIfAbsent(entity.getId(), id -> new Trail());
        Node last = trail.nodes.peekLast();
        if (last != null && last.tip().distanceToSqr(tipWorld) > TRAIL_MAX_GAP * TRAIL_MAX_GAP) {
            trail.nodes.clear();
        }
        trail.lastPos = entity.position();
        trail.nodes.addLast(new Node(baseWorld, tipWorld, Util.getMillis()));
        while (trail.nodes.size() > MAX_NODES) trail.nodes.removeFirst();
    }

    /**
     * 剑骨骼的方块包围盒 → 刀光起点（剑身中后段）与剑尖两个点。
     *
     * <p><b>坑在哪：</b>{@link GeoCube#pivot()} 不是「方块最小角」，也不是格——它是方块自己的
     * 旋转中心，单位是像素，而且 {@code .geo.json} 里没写 {@code pivot} 时会被解析成全零。
     * 早先直接拿 {@code pivot} 当最小角、再配上已经除以 16 的 {@link GeoCube#size()}，
     * 等于把两个不同的坐标系混着加，算出来的「剑尖」落在身体中下方、根本不在剑上
     * （实测比真剑尖低 1.45 格），刀光自然整条离开了武器。</p>
     *
     * <p>现在改成直接读 {@link GeoCube#quads()} 的顶点：那就是渲染时喂给顶点缓冲区的同一批
     * 数字，单位是格、方向也已经是游戏用的那一套，不含任何换算歧义。</p>
     */
    private static boolean computeBounds(GeoBone bone) {
        List<GeoCube> cubes = bone.getCubes();
        if (cubes == null || cubes.isEmpty()) return false;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (GeoCube cube : cubes) {
            GeoQuad[] quads = cube.quads();
            if (quads == null) continue;
            for (GeoQuad quad : quads) {
                if (quad == null) continue;
                for (GeoVertex vertex : quad.vertices()) {
                    Vector3f point = vertex.position();
                    minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                    minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                    minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
                }
            }
        }
        if (minY > maxY) return false;
        float cx = (minX + maxX) * 0.5F;
        float cz = (minZ + maxZ) * 0.5F;
        baseX = cx;
        baseY = minY + (maxY - minY) * TRAIL_BASE_RATIO;   // 刀光起点：剑身中后段，不是剑柄
        baseZ = cz;
        tipX = cx;
        tipY = maxY;                            // 剑尖
        tipZ = cz;
        boundsReady = true;
        return true;
    }

    // ---------------------------------------------------------------- 每刻
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        reset(minecraft.level);
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        long nowMs = Util.getMillis();
        GHOSTS.removeIf(ghost -> nowMs - ghost.stamp > GHOST_LIFETIME_MS);

        double rangeSqr = EFFECT_RANGE * EFFECT_RANGE;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof WanderingSwordmaster swordmaster)) continue;
            int id = swordmaster.getId();
            if (swordmaster.distanceToSqr(minecraft.player) > rangeSqr || !swordmaster.isAlive()) {
                LAST_ACTION.remove(id);
                continue;
            }
            WanderingSwordmaster.Action action = swordmaster.clientAction();
            WanderingSwordmaster.Action previous = LAST_ACTION.put(id, action);

            switch (action) {
                case CHARGE_SKILL -> {
                    chargeEffects(level, swordmaster, id, nowMs);
                    // 只有「冲出去」那一段留残影，蓄力原地不动时不留。
                    boolean dashing = swordmaster.actionTicksRemaining() <= 29;
                    if (dashing && (previous != WanderingSwordmaster.Action.CHARGE_SKILL
                            || level.getGameTime() % 3 == 0)) {
                        addGhost(swordmaster, nowMs);
                        windPuff(level, swordmaster, 3);
                    }
                }
                case BLINK_DODGE, BACKSTEP_LONG -> {
                    if (level.getGameTime() % 2 == 0) addGhost(swordmaster, nowMs);
                    windPuff(level, swordmaster, 5);
                }
                default -> { }
            }
        }
    }

    /** 蓄力白昼一文字（其实是逆牙穿霄）：剑尖金色光点 + 脚下不断收缩的金色圆环。 */
    private static void chargeEffects(ClientLevel level, WanderingSwordmaster swordmaster,
                                      int id, long nowMs) {
        Vec3 tip = LAST_TIP.get(id);
        if (tip == null) {
            tip = swordmaster.getEyePosition().add(swordmaster.getLookAngle().scale(1.2D));
        }
        if (level.random.nextFloat() < 0.75F) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
                    tip.x + (level.random.nextDouble() - 0.5D) * 0.25D,
                    tip.y + (level.random.nextDouble() - 0.5D) * 0.25D,
                    tip.z + (level.random.nextDouble() - 0.5D) * 0.25D,
                    0.0D, 0.01D, 0.0D);
        }
        level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, tip.x, tip.y, tip.z,
                0.0D, 0.02D, 0.0D);
        // 圆环：一轮 15 刻内半径从 2.1 收到 0.35，贴着地面转。
        double phase = (level.getGameTime() % 15) / 15.0D;
        double radius = 2.1D * (1.0D - phase) + 0.35D;
        double y = swordmaster.getY() + 0.08D;
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI * 2.0D / 10.0D + level.getGameTime() * 0.15D;
            level.addParticle(GOLD_DUST,
                    swordmaster.getX() + Math.cos(angle) * radius,
                    y,
                    swordmaster.getZ() + Math.sin(angle) * radius,
                    0.0D, 0.0D, 0.0D);
        }
    }

    /** 风阻粒子：一圈往身后飘的白雾，配合残影看就是「拉出一串虚影」。 */
    private static void windPuff(ClientLevel level, WanderingSwordmaster swordmaster, int count) {
        Vec3 at = swordmaster.position().add(0.0D, swordmaster.getBbHeight() * 0.5D, 0.0D);
        for (int i = 0; i < count; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    at.x + (level.random.nextDouble() - 0.5D) * 0.8D,
                    at.y + (level.random.nextDouble() - 0.5D) * 0.9D,
                    at.z + (level.random.nextDouble() - 0.5D) * 0.8D,
                    0.0D, 0.01D, 0.0D);
        }
    }

    private static void addGhost(WanderingSwordmaster swordmaster, long nowMs) {
        if (GHOSTS.size() >= MAX_GHOSTS) GHOSTS.remove(0);
        GHOSTS.add(new Ghost(swordmaster.getId(), swordmaster.position(),
                swordmaster.getYRot(), nowMs));
    }

    // ---------------------------------------------------------------- 渲染
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!TRAILS.isEmpty()) renderTrails(event);
        if (!GHOSTS.isEmpty()) renderGhosts(event);
    }

    private static void renderTrails(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f(event.getPoseStack().last().pose());
        long nowMs = Util.getMillis();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        boolean any = false;
        int budget = 0;

        for (Trail trail : TRAILS.values()) {
            // 时间窗：太旧的节点直接丢掉，手上这把剑 170ms 内大概 10 个节点。
            while (!trail.nodes.isEmpty() && nowMs - trail.nodes.peekFirst().stamp() > NODE_LIFETIME_MS) {
                trail.nodes.removeFirst();
            }
            if (trail.nodes.size() < 2) continue;
            if (trail.lastPos.distanceToSqr(camera) > TRAIL_RANGE * TRAIL_RANGE) continue;
            // 顶点预算：整帧最多 9 段 × 10 只剑士。
            if (budget >= 90) break;
            any |= trailStrip(trail, out, matrix, camera, nowMs);
            budget++;
        }
        if (any) buffers.endBatch(RenderType.lightning());
    }

    /** 把一段拖尾画成一条四边形带：剑身侧 alpha 见 {@link #TRAIL_MAX_ALPHA}、剑尖侧 0.0，再按节点新旧整体淡出。 */
    private static boolean trailStrip(Trail trail, VertexConsumer out, Matrix4f matrix,
                                      Vec3 camera, long nowMs) {
        Iterator<Node> iterator = trail.nodes.iterator();
        Node previous = iterator.next();
        boolean drew = false;
        while (iterator.hasNext()) {
            Node current = iterator.next();
            // 越旧的节点越淡；剑柄侧浓、剑尖侧收到 0，就是一条从剑身向剑尖消散的细刀光。
            float age = Math.clamp((nowMs - current.stamp()) / (float) NODE_LIFETIME_MS, 0.0F, 1.0F);
            float alpha = TRAIL_MAX_ALPHA * (1.0F - age);
            emitQuad(out, matrix, camera, previous.base(), previous.tip(), current.tip(), current.base(),
                    alpha, 0.0F);
            // 反着再来一遍，避免被背面剔除掉。
            emitQuad(out, matrix, camera, current.base(), current.tip(), previous.tip(), previous.base(),
                    alpha, 0.0F);
            previous = current;
            drew = true;
        }
        return drew;
    }

    private static void emitQuad(VertexConsumer out, Matrix4f matrix, Vec3 camera,
                                 Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alphaBase, float alphaTip) {
        vertex(out, matrix, camera, a, alphaBase);
        vertex(out, matrix, camera, b, alphaTip);
        vertex(out, matrix, camera, c, alphaTip);
        vertex(out, matrix, camera, d, alphaBase);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 camera, Vec3 at, float alpha) {
        Vec3 relative = at.subtract(camera);
        out.addVertex(matrix, (float) relative.x, (float) relative.y, (float) relative.z)
                .setColor(1.0F, 0.97F, 0.86F, Math.clamp(alpha, 0.0F, 1.0F));
    }

    /** 残影：不生成真实实体，只是在客户端把同一套 GeckoLib 模型按旧坐标重画一遍。 */
    private static void renderGhosts(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        long nowMs = Util.getMillis();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for (Ghost ghost : GHOSTS) {
            if (!(level.getEntity(ghost.entityId()) instanceof WanderingSwordmaster swordmaster)) continue;
            float life = 1.0F - Math.clamp((nowMs - ghost.stamp()) / (float) GHOST_LIFETIME_MS, 0.0F, 1.0F);
            if (life <= 0.01F) continue;
            poseStack.pushPose();
            // 越旧缩得越小、越淡，0.5 秒内消失。
            float scale = 0.88F + 0.12F * life;
            poseStack.scale(scale, scale, scale);
            ghostPass = true;
            ghostAlpha = 0.45F * life;
            try {
                dispatcher.render(swordmaster, ghost.at().x - camera.x, ghost.at().y - camera.y,
                        ghost.at().z - camera.z, ghost.yaw(), partialTick, poseStack, buffers,
                        LightTexture.FULL_BRIGHT);
            } finally {
                ghostPass = false;
            }
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static void reset(ClientLevel next) {
        if (lastLevel != next) {
            lastLevel = next;
            TRAILS.clear();
            GHOSTS.clear();
            LAST_TIP.clear();
            LAST_ACTION.clear();
        }
    }

    private static ClientLevel lastLevel;
}
