package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static cn.blockforge.generated.minecraft1211n422a.SpellRenderTypes.SpriteAtlas.*;

/** 元素逐帧动画、曲线聚能和分层尾迹；保留清晰像素核心，柔光只作衬托。 */
final class ClientSpellSprites {
    private static final Vec3 UP = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    private static final double GOLDEN_ANGLE = 2.399963229728653;
    private final MultiBufferSource source;
    private final Matrix4f matrix;
    private final Vec3 camera, cameraRight, cameraUp;
    private final int particleStep;
    private final ClientSpellView view;
    private boolean detail = true;
    private int detailBudget = 2400;
    private final double animationClock;
    private int light;
    private final ClientSpellSurfaces surfaces;

    ClientSpellSprites(MultiBufferSource source, Matrix4f matrix, Camera camera, ClientSpellView view) {
        this.view = view;
        var level = Minecraft.getInstance().level;
        animationClock = (level == null ? 0.0 : (double)level.getGameTime()) + camera.getPartialTickTime();
        this.source = source;
        this.matrix = matrix;
        this.camera = camera.getPosition();
        var left = camera.getLeftVector();
        var up = camera.getUpVector();
        cameraRight = new Vec3(-left.x(), -left.y(), -left.z());
        cameraUp = new Vec3(up.x(), up.y(), up.z());
        ParticleStatus setting = Minecraft.getInstance().options.particles().get();
        particleStep = setting == ParticleStatus.MINIMAL ? 3 : setting == ParticleStatus.DECREASED ? 2 : 1;
        detailBudget /= particleStep;
        this.surfaces = new ClientSpellSurfaces(source, matrix, this.camera, view);
    }

    ClientSpellView view() { return view; }
    ClientSpellSurfaces surfaces() { return surfaces; }
    Vec3 chargeOrb(Vec3 fallback) { return view.chargeOrb(fallback); }
    void scope(int owner, int anchor) { view.scope(owner, anchor); }
    void detail(boolean value) { detail = value; }
    private int step(Vec3 at) { return Math.max(particleStep, at.distanceToSqr(camera) > 32 * 32 ? 3 : 1); }
    private boolean decoration(Vec3 at) {
        if (detailBudget <= 0 || at.distanceToSqr(camera) > 64 * 64) return false;
        detailBudget--;
        return true;
    }

    void lighting(ClientLevel level, Vec3 at) {
        light = LevelRenderer.getLightColor(level, BlockPos.containing(at));
    }

    void charge(SpellDefinition spell, Vec3 hand, Vec3 feet, Vec3 direction,
                double age, double duration, double alpha) {
        hand = view.hand(hand);
        int row = mediumRow(spell.medium());
        boolean energy = row >= 8;
        // 加速施法也走完八张原画；通常每秒48张含像素位移补间的动画帧。
        double frameClock = age * Math.max(0.8, 8.0 / Math.max(1, duration));
        Vec3 right = uprightRight(hand, direction);
        if (energy) {
            int sigil = row - 8;
            // 光环与阵纹本身是完整像素贴图，缓慢刚性旋转，不做正弦缩放或变形。
            plane(SIGILS, sigil, feet.add(0, 0.015, 0), X, Z, 0.83, 0.83,
                    age * 0.010, alpha * 0.48, true);
            Vec3 normal = camera.subtract(hand).normalize();
            if (normal.lengthSqr() < 0.01) normal = Z;
            Vec3 halo = hand.subtract(normal.scale(0.025));
            plane(SIGILS, sigil, halo, cameraRight, cameraUp, 0.39, 0.39,
                    -age * 0.014, alpha * 0.80, true);
            if (spell.isIntermediate()) {
                plane(SIGILS, sigil, feet.add(0, 0.035, 0), X, Z, 1.20, 1.20,
                        -age * 0.008, alpha * 0.55, true);
                plane(SIGILS, sigil, halo, cameraRight, cameraUp, 0.55, 0.55,
                        age * 0.018, alpha * 0.50, true);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + age * 0.012;
                    plane(SIGILS, sigil, feet.add(Math.cos(a) * 1.25, 0.05, Math.sin(a) * 1.25),
                            X, Z, 0.20, 0.20, -a, alpha * 0.65, true);
                }
            } else if (spell == SpellDefinition.MULTI_BARRIER_RAY) {
                advancedCharge(feet, halo, age, alpha);
            }
            animated(row, hand, cameraRight, cameraUp, 0.20, 0.20, frameClock, alpha, true);
        } else {
            // 世界火焰仍然朝上；本地手边主体用镜头竖轴，抬头低头也不退化成一条线。
            double width = spell.medium().equals("wind") ? 0.36 : 0.27;
            double height = spell.medium().equals("fire") ? 0.34 : 0.27;
            Vec3 castingUp = view.ownEffect ? cameraUp : UP;
            Vec3 castingRight = view.ownEffect ? cameraRight : right;
            Vec3 center = spell.medium().equals("fire") ? hand.add(castingUp.scale(0.08)) : hand;
            animated(row, center, castingRight, castingUp, width, height, frameClock, alpha, emissive(row));
        }
        // 每条聚能弧有独立周期、弯曲方向和动画相位，法术按学派改变路径。
        int paths = spell.isAdvanced() ? 15 : spell.isIntermediate() ? 11 : 8;
        int chargeStep = step(hand);
        for (int i = 0; i < paths; i += chargeStep) {
            double period = 17 + i % 4 * 3 + spell.ordinal() % 5;
            double life = (age + i * 2.35) % period / period;
            double angle = i * GOLDEN_ANGLE + spell.ordinal() * 0.37 + life * (row == 7 ? 4.8 : 1.8);
            double radius = 0.46 * (1 - life) * (1 - life) + 0.055;
            Vec3 pos;
            switch (spell.school()) {
                case "healing", "blessing" -> pos = hand.add(right.scale(Math.cos(angle) * radius * 0.65))
                        .add(cameraUp.scale(-0.35 + life * 0.4));
                case "protection", "control" -> pos = hand.add(right.scale(Math.cos(angle) * (0.31 + radius * 0.2)))
                        .add(cameraUp.scale(Math.sin(angle) * (0.28 + radius * 0.15)));
                case "movement" -> pos = hand.subtract(direction.scale((1 - life) * 0.65))
                        .add(right.scale(Math.cos(angle) * 0.14)).add(cameraUp.scale(Math.sin(angle) * 0.14));
                case "summon" -> pos = hand.add(right.scale(Math.cos(angle) * radius))
                        .add(cameraUp.scale(Math.sin(angle) * radius)).add(direction.scale(Math.sin(life * Math.PI) * 0.12));
                case "curse", "chaos" -> pos = hand.add(right.scale(Math.cos(angle) * radius))
                        .add(cameraUp.scale(0.35 - life * 0.4));
                default -> pos = hand.add(right.scale(Math.cos(angle) * radius))
                        .add(cameraUp.scale(Math.sin(angle) * radius * 0.70));
            }
            mote(spell, i / chargeStep, pos, 0.055 + 0.02 * (1 - life), age * 1.3 + i * 5,
                    alpha * envelope(life), life);
        }
        chargeAura(spell, hand, feet, age, duration, alpha);
    }

    /** 前摇靠清晰的元素核心、聚能粒子和不闭合的轨道建立层次，不铺全身光幕。 */
    private void chargeAura(SpellDefinition spell, Vec3 hand, Vec3 feet,
                            double age, double duration, double alpha) {
        int tint = color(spell);
        double progress = Math.clamp(age / Math.max(1, duration * 0.8), 0, 1);
        double swell = 0.60 + 0.30 * progress;
        glow(GLOW_SOFT, hand, swell, 0, tint, alpha * 0.36);
        if (spell.isArcane() || spell.medium().equals("sound") || spell.medium().equals("light")) {
            orbitArcs(hand, cameraRight, cameraUp, 0.28 + progress * 0.08,
                    age * 0.04, tint, alpha * 0.75, 3);
        }
        if (!minimal()) {
            orbitArcs(feet.add(0, 0.035, 0), X, Z, 0.72 + progress * 0.35,
                    -age * 0.017, tint, alpha * 0.60, spell.isAdvanced() ? 5 : 3);
            if (spell.isAdvanced()) fieldMotes(spell, feet, 1.3, age, alpha * 0.75);
        }
    }

    /** 法术的元素色；奥术没有元素媒介，用统一的紫。 */
    static int color(SpellDefinition spell) {
        ElementType element = spell.element();
        return element == null ? 0xAE69FF : element.color() & 0xFFFFFF;
    }

    /** 释放光晕按学派分流：防护分段轨道、治疗上升、诅咒下沉、其他元素自然逸散。 */
    void spellAura(SpellDefinition spell, Vec3 anchor, Vec3 floor, double age,
                   double alpha, boolean groundRing) {
        if (alpha < 0.02) return;
        double grade = spell.isAdvanced() ? 1.4 : spell.isIntermediate() ? 1.1 : 0.8;
        if (spell.school().equals("protection")) {
            orbitArcs(anchor, X, Z, grade * 0.95, age * 0.023, color(spell), alpha * 0.7, 4);
        } else if (spell.school().equals("destruction")) {
            coreGlow(spell, anchor, age, alpha * 0.65);
        }
        if (groundRing) fieldMotes(spell, floor, grade, age, alpha);
    }

    /** 最少档减少烟尘和轨道弧段，完整保留主体动画。 */
    boolean minimal() { return particleStep >= 3; }

    /** 已有专属造型的阶段只补小底光和三颗分相位的元素细节，避免糊掉轮廓。 */
    void coreGlow(SpellDefinition spell, Vec3 anchor, double age, double alpha) {
        if (alpha < 0.02) return;
        int tint = color(spell);
        double grade = spell.isAdvanced() ? 1.35 : spell.isIntermediate() ? 1.0 : 0.75;
        glow(GLOW_SOFT, anchor, 0.85 * grade, 0.0, tint, alpha * 0.24);
        for (int i = 0; i < 3; i += step(anchor)) {
            double angle = i * GOLDEN_ANGLE + age * 0.027;
            Vec3 p = anchor.add(Math.cos(angle) * grade * 0.4, Math.sin(angle * 1.7) * grade * 0.3,
                    Math.sin(angle) * grade * 0.4);
            mote(spell, i, p, 0.07, age + i * 7, alpha * 0.7, (age * 0.037 + i * 0.31) % 1);
        }
    }

    /** 高级奥术阵：地面双环加外圈六枚副阵，手心再叠一层反向内阵。 */
    private void advancedCharge(Vec3 feet, Vec3 halo, double age, double alpha) {
        plane(SIGIL_ADVANCED, 0, feet.add(0, 0.035, 0), X, Z, 1.34, 1.34, age * 0.006, alpha * 0.50, true);
        plane(SIGIL_ADVANCED, 1, feet.add(0, 0.055, 0), X, Z, 1.02, 1.02, -age * 0.011, alpha * 0.60, true);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + age * 0.014;
            plane(SIGIL_ADVANCED, 1, feet.add(Math.cos(a) * 1.62, 0.075, Math.sin(a) * 1.62),
                    X, Z, 0.30, 0.30, -a * 1.7, alpha * 0.72, true);
        }
        plane(SIGIL_ADVANCED, 0, halo, cameraRight, cameraUp, 0.42, 0.42, age * 0.020, alpha * 0.88, true);
        plane(SIGIL_ADVANCED, 1, halo, cameraRight, cameraUp, 0.30, 0.30, -age * 0.028, alpha * 0.92, true);
    }

    /** 悬空的多重结界法阵：贴图平面正对指定法线，可正反两层独立旋转。 */
    void advancedSigil(int tile, Vec3 center, Vec3 normal, double radius, double spin, double alpha) {
        sigilPlane(SIGIL_ADVANCED, tile, center, normal, radius, spin, alpha);
    }

    /** 冰系高级法阵：用法与奥术高级阵相同，图集换成雪花与十二芒星那套阵纹。 */
    /**
     * 万剑归一的天空裂缝：8 帧动态贴图铺在整道裂口上。
     *
     * <p>主面水平铺开（从地面仰视时最清楚），再叠一层绕长轴对称的竖面，
     * 这样斜着看、从侧面看也不会退化成一条线。帧率固定 5 帧/秒，8 帧循环约 1.6 秒。</p>
     */
    void skyRift(Vec3 center, Vec3 dir, Vec3 side, double halfLength, double halfWidth,
                 double clock, double alpha) {
        int frame = Math.floorMod((int)Math.floor(clock * 0.25), 8);
        // 水平主面
        plane(SKY_RIFT, frame, center, dir, side, halfLength, halfWidth, 0.0, alpha, true);
        // 绕长轴转 90° 的竖面，斜视时补上轮廓
        plane(SKY_RIFT, frame, center, dir, UP, halfLength, halfWidth * 0.62, 0.0, alpha * 0.55, true);
    }

    void frostSigil(int tile, Vec3 center, Vec3 normal, double radius, double spin, double alpha) {
        sigilPlane(SIGIL_FROST, tile, center, normal, radius, spin, alpha);
    }

    /** 元素能量阵：SIGILS 图集的四格阵纹（0 奥术、1 光、2 雷、3 声），雷球悬阵用它。 */
    void energySigil(int tile, Vec3 center, Vec3 normal, double radius, double spin, double alpha) {
        sigilPlane(SIGILS, tile, center, normal, radius, spin, alpha);
    }

    private void sigilPlane(SpellRenderTypes.SpriteAtlas atlas, int tile, Vec3 center, Vec3 normal,
                            double radius, double spin, double alpha) {
        Vec3 n = normal.lengthSqr() < 0.0001 ? Z : normal.normalize();
        Vec3 right = UP.cross(n);
        if (right.lengthSqr() < 0.0001) right = X;
        right = right.normalize();
        Vec3 up = n.cross(right).normalize();
        plane(atlas, tile, center, right, up, radius, radius, spin, alpha, true);
    }

    void release(SpellDefinition spell, Vec3 start, Vec3 end, Vec3 anchor, Vec3 floor,
                 double age, double duration, double alpha, boolean attached, double bodyAlpha) {
        boolean projectile = switch (spell) {
            case FIREBALL, WATER_BOLT, ICE_SPIKE, WIND_BLADE, SHARP_EDGE, LIGHT_BOLT, POISON_BOLT -> true;
            default -> false;
        };
        double delay = spell == SpellDefinition.ROCKFALL ? 7 : projectile ? 4 : 0;
        Vec3 hit = spell == SpellDefinition.ROCKFALL ? end.add(0, 0.18, 0) : attached ? anchor : end;
        double hitAge = age - delay;
        if (hitAge >= 0 && hitAge < 20) {
            burst(spell, hit, hitAge, 20, 18, alpha * (attached ? bodyAlpha : 1));
            impactGlow(spell, hit, hitAge, 20, alpha * (attached ? bodyAlpha : 1));
        }
        if (projectile && age < 16) {
            // 出生位置固定在实际航线上；弹头离开后烟尘仍留在原处，绝不整串粘着弹头走。
            int count = 22;
            Vec3 middle = start.lerp(end, 0.5);
            Vec3 trailDirection = end.subtract(start).normalize();
            int trailStep = step(middle);
            for (int i = 0; i < count; i += trailStep) {
                double born = i * 4.0 / count;
                double local = age - born;
                if (local < 0 || local > 12) continue;
                Vec3 origin = start.lerp(end, born / 4.0);
                trailSample(spell, origin, trailDirection, local, 12,
                        i / trailStep + spell.ordinal() * 37, 0.20, alpha);
            }
        }
        // 治疗、护甲和控制法术留有稀疏附着粒子；命中爆发只执行一次。
        if (attached && age > 10) {
            for (int i = 0; i < 9; i += particleStep) {
                double life = (age - 10 + i * 2.4) % 28 / 28;
                double angle = i * GOLDEN_ANGLE + spell.ordinal() * 0.2;
                double rise = spell.school().equals("curse") ? 1 - life : life;
                Vec3 pos = floor.add(Math.cos(angle) * 0.62, 0.15 + rise * 1.45, Math.sin(angle) * 0.62);
                particle(spell, (i / particleStep) % 3, pos, 0.052, alpha * bodyAlpha * envelope(life) * 0.7);
            }
        }
        if (spell == SpellDefinition.SONIC_WAVE || spell == SpellDefinition.GALE_DASH) {
            for (int i = 0; i < 12; i += particleStep) {
                double life = Math.min(1, age / Math.min(duration, 24));
                double angle = i * Math.PI / 6;
                Vec3 pos = spell == SpellDefinition.GALE_DASH ? start.lerp(end, i / 12.0)
                        : start.add(Math.cos(angle) * life * 3.8, -0.35, Math.sin(angle) * life * 3.8);
                particle(spell, (i / particleStep) % 3, pos, 0.065, alpha * (1 - life));
            }
        }
    }

    /**
     * 弹体从出手飞到命中点要用几刻。
     *
     * <p>和 {@link SpellRange#BASIC_SPEED} 用同一个速度：两格一刻。
     * 只用于远处命中声与通用时间轴的对齐，不改变 r386 的视觉节奏。</p>
     */
    static double flightTicks(Vec3 start, Vec3 end) {
        return Math.max(4, SpellRange.flightTicks(start.distanceTo(end)));
    }

    void shatter(SpellDefinition spell, Vec3 at, double age, double duration, double alpha) {
        burst(spell, at, age, duration, 15, alpha * 0.8);
        impactGlow(spell, at, age, duration, alpha * 0.8);
    }

    /**
     * 命中闪光：一个迅速鼓起又散掉的柔光球，外加一圈向外扩的流光环。
     *
     * <p>贴片爆发只有零散火星，缺一个「亮一下」的瞬间；补上这层之后
     * 每一次命中都有明确的闪光与扩散，观感立刻扎实了。</p>
     */
    void impactGlow(SpellDefinition spell, Vec3 at, double age, double duration, double alpha) {
        double life = Math.clamp(age / Math.max(1.0, duration), 0, 1);
        if (life >= 1) return;
        int tint = color(spell);
        int hot = mix(tint, 0xFFFFFF, 0.55);
        double swell = 0.45 + life * 1.35;
        double fade = (1 - life) * (1 - life);
        // 中心：先亮一下再散开
        glow(GLOW_SOFT, at, swell * 1.5, 0.0, tint, alpha * 0.68 * fade);
        glow(GLOW_SOFT, at, swell * 0.7, 0.0, hot, alpha * 0.62 * (1 - life));
        glow(GLOW_STAR, at, swell * 1.9, age * 0.06, hot, alpha * 0.34 * fade);
        // 撞击主体只播放一次从聚拢到破碎的像素动画，不贴一张平移光板。
        motion(mediumRow(spell.medium()), at, cameraRight, cameraUp, swell * 0.5, swell * 0.5,
                Math.min(23, life * 24), 0, alpha * (1 - life), 0xFFFFFF, true);
        double ring = 0.45 + Math.sqrt(life) * 2.2;
        if (!minimal()) orbitArcs(at, X, Z, ring, age * 0.03, hot, alpha * fade * 0.55, 5);
    }

    private void burst(SpellDefinition spell, Vec3 at, double age, double duration, int count, double alpha) {
        int row = mediumRow(spell.medium());
        double life = age / duration;
        if (life < 0 || life >= 1) return;
        double drift = (1 - Math.exp(-age * 0.065)) / 0.065;
        boolean falling = row == 1 || row == 2 || row == 3 || row == 6;
        for (int i = 0; i < count; i += particleStep) {
            double angle = i * GOLDEN_ANGLE + spell.ordinal() * 0.31;
            double speed = 0.045 + (i % 4) * 0.018;
            double lift = (0.035 + (i % 5) * 0.018) * drift;
            double y = falling ? lift - 0.003 * age * age : lift + age * 0.012;
            Vec3 pos = at.add(Math.cos(angle) * speed * drift, y, Math.sin(angle) * speed * drift);
            mote(spell, (i / particleStep) % 3, pos, 0.065 + (i % 3) * 0.025,
                    age * 1.5 + i * 2.1,
                    alpha * Math.min(1, age / 1.5 + 0.2) * (1 - life) * (1 - life), life);
        }
    }

    /** 兼容旧调用点：一簇火回到 r386 的单一原画，不再按种子换轮廓。 */
    void flame(Vec3 base, double size, double age, double alpha, int seed) {
        flame(base, size, age, alpha);
    }

    void flame(Vec3 base, double size, double age, double alpha) {
        // 火焰先垫一层柔光：逐帧原画本身是实心像素，没有外发光；
        // 加一层会呼吸的光晕之后，火焰才像在「亮」，而不是贴上去的一张图。
        double breathe = 0.85 + 0.15 * Math.sin(age * 0.7);
        glow(GLOW_SOFT, base.add(0, size * 0.30, 0), size * 1.85 * breathe, 0.0, 0xFF8A3C, alpha * 0.42);
        glow(GLOW_SOFT, base.add(0, size * 0.18, 0), size * 0.95, 0.0, 0xFFE2A8, alpha * 0.30);
        // 释放后的火焰使用完整逐帧原画；大量火法只保留少数主火焰，其余改用不同火星图案。
        animated(0, base.add(0, size * 0.25, 0), uprightRight(base, Z), UP,
                size * 0.85, size * 1.15, age * 0.8, alpha, true);
    }

    /** 弹体底光只衬托像素核心，不再叠同款星芒和整条滚动流光。 */
    void headGlow(SpellDefinition spell, Vec3 head, Vec3 direction, double age, double alpha) {
        if (alpha < 0.02) return;
        int tint = color(spell);
        int hot = mix(tint, 0xFFFFFF, 0.6);
        glow(GLOW_SOFT, head, 0.95, 0.0, tint, alpha * 0.36);
        if (spell.medium().equals("light") || spell.medium().equals("lightning"))
            glow(GLOW_SPARK, head, 0.45, age * 0.05, hot, alpha * 0.40);
    }

    /** 兼容旧调用点：种子参数在 r386 观感里不参与外形选择。 */
    void smokeMotion(Vec3 at, double size, int tint, double alpha, int seed) {
        smokeMotion(at, size, tint, alpha);
    }

    void smokeMotion(Vec3 at, double size, int tint, double alpha) {
        if (!decoration(at)) return;
        motion(13, at, cameraRight, cameraUp, size, size, animationClock * 0.85 + size * 11,
                0, alpha, tint, false);
    }

    void fireParticle(Vec3 position, int variant, double size, double alpha) {
        particle(SpellDefinition.FIREBALL, Math.floorMod(variant, 3), position, size, alpha);
    }

    private void particle(SpellDefinition spell, int variant, Vec3 position, double size, double alpha) {
        double clock = animationClock * (1.8 + variant * 0.11) + variant * 7 + spell.ordinal() * 2.3;
        mote(spell, variant, position, size, clock, alpha, (clock % 24) / 24);
    }

    private void animated(int row, Vec3 p, Vec3 right, Vec3 up, double width, double height,
                          double clock, double alpha, boolean emissive) {
        motion(row, p, right, up, width, height, clock * 3, row == 0 ? Math.PI / 4 : 0,
                alpha, 0xFFFFFF, emissive);
    }

    private void motion(int row, Vec3 at, Vec3 right, Vec3 up, double width, double height,
                        double clock, double spin, double alpha, int tint, boolean emissive) {
        int frame = Math.floorMod((int)Math.floor(clock % 24.0), 24);
        plane(MOTION, row * 24 + frame, at, right, up, width, height, spin, alpha, emissive, tint);
    }

    /** 小粒子也逐帧变化：元素碎片、飞散火星/水滴和闪屑三层，不再只是三张静态图。 */
    private void mote(SpellDefinition spell, int variant, Vec3 at, double size,
                      double clock, double alpha, double life) {
        if (!decoration(at) || alpha < 0.01) return;
        int medium = mediumRow(spell.medium());
        int v = Math.floorMod(variant, 3);
        int row = v == 0 ? medium : medium == 0 ? (v == 1 ? 12 : 13)
                : medium == 1 || medium == 5 ? (v == 1 ? 14 : medium)
                : medium == 3 ? (v == 1 ? 13 : medium) : (v == 1 ? medium : 15);
        int tint = row == 14 || row == 15 ? mix(color(spell), 0xFFFFFF, 0.4)
                : row == 13 && medium == 5 ? 0x99BB66 : 0xFFFFFF;
        double frame = row >= 12 ? Math.min(23, Math.max(0, life) * 23) : clock;
        motion(row, at, cameraRight, cameraUp, size, size, frame,
                medium == 6 ? clock * 0.04 : 0, alpha, tint, row != 13);
    }

    /** 单颗弹体的像素主体，火球尾焰沿速度方向，斜视时也不会退化成薄线。 */
    void projectileCore(SpellDefinition spell, Vec3 at, Vec3 dir, double size, double age, double alpha) {
        int row = mediumRow(spell.medium());
        Vec3 axis = dir.lengthSqr() < 0.001 ? Z : dir.normalize();
        double x = axis.dot(cameraRight), y = axis.dot(cameraUp);
        double spin = x*x + y*y < 0.02 ? 0 : Math.atan2(-y, -x) - Math.PI / 4;
        // 火球原画火尾朝右上，逆速度对齐；其余元素不强行转成同一球形。
        motion(row, at, cameraRight, cameraUp, size, size,
                age * 2.4 + spell.ordinal() * 3.1, row == 0 ? spin : 0, alpha, 0xFFFFFF, true);
        headGlow(spell, at, dir, age, alpha);
    }

    /** 一段留在世界中的尾迹：初生亮屑、滞后副粒子、末端扩散烟尘。 */
    void trailSample(SpellDefinition spell, Vec3 origin, Vec3 dir, double age, double duration,
                     int seed, double size, double alpha) {
        double life = Math.clamp(age / duration, 0, 1);
        if (life >= 1 || alpha < 0.01) return;
        int row = mediumRow(spell.medium());
        double a = seed * GOLDEN_ANGLE;
        double speed = 0.013 + (seed & 3) * 0.008;
        boolean fall = row == 1 || row == 2 || row == 3 || row == 6 || row == 5;
        Vec3 drift = new Vec3(Math.cos(a) * speed, fall ? 0.018 : 0.028, Math.sin(a) * speed);
        Vec3 p = origin.add(drift.scale(age)).subtract(dir.scale(age * 0.012))
                .add(0, fall ? -0.0025 * age * age : 0.0008 * age * age, 0);
        double fade = alpha * Math.min(1, (1 - life) * 2.2);
        mote(spell, seed % 3, p, size * (0.85 - life * 0.45), age * 2.2 + seed * 2.7, fade, life);
        if (!minimal() && (seed & 1) == 0) {
            Vec3 q = p.add(Math.sin(a) * age * 0.016, age * 0.015, Math.cos(a) * age * 0.016);
            mote(spell, 1, q, size * 0.42, age * 2.5 + seed, fade * 0.85, life);
        }
        if ((row == 0 || row == 3 || row == 5) && age > 1 && (seed & 1) == 0 && !minimal()
                && decoration(p)) {
            motion(13, p.add(0, age * 0.022, 0), cameraRight, cameraUp,
                    size * (0.7 + life), size * (0.7 + life), life * 23, 0,
                    fade * 0.42, row == 5 ? 0x88B65A : row == 3 ? 0xB5A38A : 0xFFFFFF, false);
        }
    }

    /** 不同学派决定逸散方向；每条轨迹不同周期，避免几十个法术同步闪同一帧。 */
    void fieldMotes(SpellDefinition spell, Vec3 base, double radius, double age, double alpha) {
        if (alpha < 0.01) return;
        int row = mediumRow(spell.medium());
        int count = spell.isAdvanced() ? 22 : 12;
        int density = step(base);
        for (int i = 0; i < count; i += density) {
            double period = 24 + i % 5 * 4;
            double life = (age + i * 3.17 + spell.ordinal()) % period / period;
            double angle = i * GOLDEN_ANGLE + spell.ordinal() * .31;
            double r = radius * (0.35 + (i % 5) * 0.13);
            double y;
            switch (row) {
                case 0 -> { y = life * 1.6; r *= 1 - life * 0.3; }
                case 1 -> { y = Math.sin(life * Math.PI) * 0.9; r *= 0.5 + life; }
                case 2, 3, 6 -> { y = 0.15 + Math.sin(life * Math.PI) * 0.45; }
                case 4 -> { y = life * 1.3; angle += Math.sin(life * 5) * 0.32; }
                case 5 -> { y = life * 0.65; r *= 0.65 + life * 0.5; }
                case 7 -> { y = life * 1.8; angle += life * 5.5; }
                case 10 -> { y = 0.2 + (i % 4) * 0.32; angle += Math.floor(age * .7 + i) * .08; }
                case 11 -> { y = 0.3 + (i % 3) * .23; r *= 0.35 + life; }
                default -> { y = life * 1.5; angle += life * 1.6; }
            }
            if (spell.school().equals("curse")) y = 1.6 - y;
            if (spell.school().equals("healing")) y += life * 0.5;
            Vec3 p = base.add(Math.cos(angle) * r, y + 0.06, Math.sin(angle) * r);
            mote(spell, i / density, p, 0.075 + (i % 3) * 0.025, age * 1.9 + i * 5.3,
                    alpha * envelope(life) * 0.85, life);
        }
    }

    /** 断开的弧段沿曲线运动，UV固定取光带中心，不滚动整张图。 */
    void orbitArcs(Vec3 center, Vec3 u, Vec3 v, double radius, double clock,
                   int tint, double alpha, int arcs) {
        if (minimal() || !decoration(center)) return;
        int segments = detail ? 8 : 5;
        for (int arc = 0; arc < arcs; arc++) {
            double base = clock * (arc % 2 == 0 ? 1 : -0.73) + arc * Math.PI * 2 / arcs;
            double span = 0.65 + arc % 3 * 0.15;
            for (int i = 0; i < segments; i++) {
                double a = base + i * span / segments, b = base + (i + 1) * span / segments;
                Vec3 p = center.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
                Vec3 q = center.add(u.scale(Math.cos(b) * radius)).add(v.scale(Math.sin(b) * radius));
                ribbon(p, q, Math.max(0.015, radius * 0.023), tint,
                        alpha * Math.sin(Math.PI * (i + 0.5) / segments));
            }
        }
    }

    private void ribbon(Vec3 a, Vec3 b, double width, int color, double alpha) {
        Vec3 delta = b.subtract(a);
        double length = delta.length();
        if (length < 0.001) return;
        Vec3 along = delta.scale(1 / length);
        Vec3 side = along.cross(camera.subtract(a));
        if (side.lengthSqr() < 0.001) side = along.cross(UP);
        if (side.lengthSqr() < 0.001) side = X;
        plane(GLOW, GLOW_BAND_H, a.lerp(b, .5), along, side.normalize(),
                length * .63, width, 0, alpha, true, color);
    }

    /** 束流细节分成独立脉冲与粒子，变化发生在形状和轨迹，而不是UV平移。 */
    void beamMotes(SpellDefinition spell, Vec3 start, Vec3 end, double age, double alpha) {
        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < .01) return;
        Vec3 axis = delta.normalize();
        Vec3 side = axis.cross(UP);
        if (side.lengthSqr() < .01) side = X;
        side = side.normalize();
        Vec3 up = axis.cross(side).normalize();
        Vec3 middle = start.lerp(end, .5);
        int beamStep = step(middle);
        for (int i = 0; i < 18; i += beamStep) {
            double t = (age * (0.035 + i % 3 * .004) + i * .173) % 1;
            double angle = age * .13 + i * GOLDEN_ANGLE;
            Vec3 p = start.lerp(end, t).add(side.scale(Math.cos(angle) * .13)).add(up.scale(Math.sin(angle) * .13));
            mote(spell, i / beamStep, p, .09, age * 2.3 + i, alpha * envelope(t), t);
        }
    }

    private Vec3 uprightRight(Vec3 at, Vec3 fallbackDirection) {
        Vec3 normal = camera.subtract(at);
        Vec3 right = UP.cross(normal);
        if (right.lengthSqr() < 0.0001) right = UP.cross(fallbackDirection);
        if (right.lengthSqr() < 0.0001) right = X;
        return right.normalize();
    }

    private void plane(SpellRenderTypes.SpriteAtlas atlas, int tile, Vec3 center, Vec3 right, Vec3 up,
                       double width, double height, double spin, double alpha, boolean emissive) {
        plane(atlas, tile, center, right, up, width, height, spin, alpha, emissive, 0xFFFFFF);
    }

    /** 带顶点染色的贴片：柔光层靠乘法染上元素色，白色像素就变成对应元素的光。 */
    private void plane(SpellRenderTypes.SpriteAtlas atlas, int tile, Vec3 center, Vec3 right, Vec3 up,
                       double width, double height, double spin, double alpha, boolean emissive, int rgb) {
        if (alpha < 0.005) return;
        Vec3 r = right.scale(Math.cos(spin)).add(up.scale(Math.sin(spin))).scale(width);
        Vec3 v = up.scale(Math.cos(spin)).subtract(right.scale(Math.sin(spin))).scale(height);
        int columns = atlas.width / atlas.tileWidth;
        int x = tile % columns * atlas.tileWidth, y = tile / columns * atlas.tileHeight;
        // 图集内缩阻止串边；柔光图集是渐变放大，内缩要多留几像素才不会被邻格高光染边。
        // 再往里挪半像素，让 UV 正好落在像素中心——双线性过滤下这一点很关键。
        float inset = Math.max(0.02F, atlas.pad) + 0.5F;
        float u0 = (x + inset) / atlas.width, u1 = (x + atlas.tileWidth - inset) / atlas.width;
        float v0 = (y + inset) / atlas.height, v1 = (y + atlas.tileHeight - inset) / atlas.height;
        VertexConsumer out = source.getBuffer(atlas.type);
        if (view.ownScreen && center.distanceToSqr(camera) < 64 && Math.max(width, height) > 0.35) {
            // 大光片细分后只挖掉准星前的区域，不能靠四个外角把整张光片压暗。
            int n = 6;
            for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                double a = i / (double)n, b = (i + 1) / (double)n;
                double c = j / (double)n, d = (j + 1) / (double)n;
                gridVertex(out, center, r, v, a, c, u0, u1, v0, v1, alpha, emissive, rgb);
                gridVertex(out, center, r, v, b, c, u0, u1, v0, v1, alpha, emissive, rgb);
                gridVertex(out, center, r, v, b, d, u0, u1, v0, v1, alpha, emissive, rgb);
                gridVertex(out, center, r, v, a, d, u0, u1, v0, v1, alpha, emissive, rgb);
            }
            return;
        }
        vertex(out, center.subtract(r).add(v), u0, v0, alpha, emissive, rgb);
        vertex(out, center.add(r).add(v), u1, v0, alpha, emissive, rgb);
        vertex(out, center.add(r).subtract(v), u1, v1, alpha, emissive, rgb);
        vertex(out, center.subtract(r).subtract(v), u0, v1, alpha, emissive, rgb);
    }

    private void gridVertex(VertexConsumer out, Vec3 c, Vec3 r, Vec3 v, double s, double t,
                            float u0, float u1, float v0, float v1, double alpha, boolean emissive, int rgb) {
        double x = s * 2 - 1, y = 1 - t * 2;
        vertex(out, c.x + r.x*x + v.x*y, c.y + r.y*x + v.y*y, c.z + r.z*x + v.z*y,
                (float)(u0 + (u1-u0)*s), (float)(v0 + (v1-v0)*t), alpha, emissive, rgb);
    }

    private void vertex(VertexConsumer out, Vec3 at, float u, float v, double alpha, boolean emissive) {
        vertex(out, at, u, v, alpha, emissive, 0xFFFFFF);
    }

    private void vertex(VertexConsumer out, Vec3 at, float u, float v, double alpha, boolean emissive, int rgb) {
        vertex(out, at.x, at.y, at.z, u, v, alpha, emissive, rgb);
    }

    /** 标量坐标版本：流光网格逐顶点现算位置，不产生任何临时 Vec3。 */
    private void vertex(VertexConsumer out, double x, double y, double z,
                        float u, float v, double alpha, boolean emissive, int rgb) {
        out.addVertex(matrix, (float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                        (int)(Math.clamp(alpha * view.visibility(x, y, z), 0, 1) * 255))
                .setUv(u, v).setLight(emissive ? 0xF000F0 : light);
    }

    // ---------------------------------------------------------------- 柔光层

    /**
     * 柔光图集的十六格，按用途起名，读法术代码时不用记编号。
     *
     * <p>这些都是白色亮度图，靠顶点色染成元素色；加色混合叠在场景上，
     * 所以「光」不会挡住后面的方块，只是让那一块变亮。</p>
     */
    static final int GLOW_SOFT = 0, GLOW_RING = 1, GLOW_STAR = 2, GLOW_SUN = 3,
            GLOW_HEX = 4, GLOW_SLASH = 5, GLOW_CRESCENT = 6, GLOW_SPARK = 7,
            GLOW_DOUBLE_RING = 8, GLOW_TRI = 9, GLOW_SNOW = 10, GLOW_SWIRL = 11,
            GLOW_BAND_H = 12, GLOW_BAND_V = 13, GLOW_CLOUD = 14, GLOW_RUNE = 15;

    /** 一张正对镜头的柔光；{@code size} 是直径（格）。 */
    void glow(int tile, Vec3 center, double size, double spin, int rgb, double alpha) {
        plane(GLOW, tile, center, cameraRight, cameraUp, size * 0.5, size * 0.5, spin, alpha, true, rgb);
    }

    /** 贴在指定平面上的柔光：地面光环、竖直光幕用这个。 */
    void glowPlane(int tile, Vec3 center, Vec3 right, Vec3 up,
                   double width, double height, double spin, int rgb, double alpha) {
        plane(GLOW, tile, center, right, up, width * 0.5, height * 0.5, spin, alpha, true, rgb);
    }

    /**
     * 一圈会呼吸的柔光：核心 + 外层光环，两层转速不同，看上去像有能量的光晕。
     *
     * @param pulse 0~1 的呼吸相位，调用方用年龄算出来
     */
    void halo(Vec3 center, double size, int rgb, double alpha, double pulse) {
        double breathe = 0.82 + 0.18 * Math.sin(pulse * Math.PI * 2);
        glow(GLOW_SOFT, center, size * breathe, 0.0, rgb, alpha * 0.55);
        glow(GLOW_RING, center, size * (1.12 + 0.08 * breathe), pulse * 0.6, rgb, alpha * 0.42);
        glow(GLOW_STAR, center, size * 0.9, -pulse * 0.4, mix(rgb, 0xFFFFFF, 0.55), alpha * 0.34);
    }

    /** 两色按比例混合，用来把淡光调成偏白的「过曝」色。 */
    static int mix(int a, int b, double t) {
        t = Math.clamp(t, 0, 1);
        int r = (int)(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int)(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int)((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }

    private static boolean emissive(int row) { return row == 0 || row == 5 || row >= 8; }
    private static double envelope(double life) { return Math.min(1, life * 5) * Math.min(1, (1 - life) * 4); }
    private static int mediumRow(String medium) {
        return switch (medium) {
            case "fire" -> 0;
            case "water" -> 1;
            case "ice" -> 2;
            case "earth" -> 3;
            case "nature" -> 4;
            case "poison" -> 5;
            case "metal" -> 6;
            case "wind" -> 7;
            case "arcane" -> 8;
            case "light" -> 9;
            case "lightning" -> 10;
            case "sound" -> 11;
            default -> throw new IllegalArgumentException("Unknown spell medium: " + medium);
        };
    }

    // ------------------------------------------------- 后续单独要求保留的部分

void venomMembrane(Vec3 center, double radius, double height, double clock, double alpha) {
        surfaces.venomDome(center, radius, height, clock, alpha, minimal());
    }

    void domainMotes(SpellDefinition spell, Vec3 center, double radius, double height,
                     double clock, double alpha, double density) {
        if (alpha < 0.02 || !decoration(center)) return;
        int stride = step(center);
        // 边界：粒子沿圆周慢慢爬升，到顶淡出、从底部补上，像一圈流动的墙。
        int edge = (int)Math.round(Math.min(48, 18 + radius * 2.2) * density);
        for (int i = 0; i < edge; i += stride) {
            double angle = i * GOLDEN_ANGLE + clock * (0.008 + (i % 3) * 0.0025);
            double climb = (clock * 0.010 + i * 0.137) % 1.0;
            double r = radius * (0.90 + 0.08 * seedChance(i, 4));
            double y = height * (0.02 + 0.96 * climb);
            if (spell.medium().equals("poison")) r *= Math.sqrt(Math.max(0, 1 - (y / height) * (y / height)));
            if (spell.medium().equals("nature")) {
                // 叶片从树冠缓缓下落，不再像所有元素一样沿边界爬升。
                y = height * (0.85 - 0.75 * climb);
                r *= 0.65 + 0.20 * Math.sin(i * 1.7);
            }
            Vec3 p = center.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
            double life = Math.sin(climb * Math.PI);
            mote(spell, i, p, 0.07 + seedChance(i, 2) * 0.09,
                    clock + i * 4.7, alpha * (0.35 + 0.65 * life), climb);
        }
        // 半空：疏一点、慢一点，给领域一点厚度，而不是只剩一圈边。
        int inner = (int)Math.round(Math.min(26, 10 + radius * 1.4) * density);
        for (int i = 0; i < inner; i += stride) {
            double angle = i * 2.399 + clock * 0.006 + spell.ordinal() * 0.37;
            double r = radius * (0.18 + 0.68 * seedChance(i, 5));
            double y = height * (0.10 + 0.78 * seedChance(i, 6)) + 0.22 * Math.sin(clock * 0.045 + i * 1.7);
            if (spell.medium().equals("poison")) r *= Math.sqrt(Math.max(0, 1 - (y / height) * (y / height)));
            Vec3 p = center.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
            double life = (clock * 0.012 + i * 0.21) % 1.0;
            mote(spell, i + 131, p, 0.06 + seedChance(i, 2) * 0.07,
                    clock * 0.85 + i * 3.3, alpha * 0.55 * envelope(life), life);
        }
        // 贴地：只铺在离地半格内，把领域的「地面」垫起来。
        int ground = (int)Math.round(Math.min(30, 12 + radius) * density);
        for (int i = 0; i < ground; i += stride) {
            double angle = i * 2.399 + clock * 0.004;
            double r = radius * Math.sqrt(seedChance(i, 7));
            Vec3 p = center.add(Math.cos(angle) * r, 0.06 + seedChance(i, 8) * 0.18, Math.sin(angle) * r);
            double life = (clock * 0.02 + i * 0.17) % 1.0;
            mote(spell, i + 271, p, 0.05 + seedChance(i, 2) * 0.06,
                    clock * 1.4 + i * 2.9, alpha * 0.42 * envelope(life), life);
        }
    }

    void burstMotes(SpellDefinition spell, Vec3 center, double radius, double age,
                    double duration, double alpha) {
        if (alpha < 0.02 || !decoration(center)) return;
        int stride = step(center);
        double t = Math.clamp(age / Math.max(1.0, duration), 0.0, 1.0);
        int count = (int)Math.round(Math.min(120, 42 + radius * 5.0));
        for (int i = 0; i < count; i += stride) {
            double u = seedChance(i, 1) * 2 - 1;
            double phi = seedChance(i, 2) * Math.PI * 2;
            double ring = Math.sqrt(Math.max(0, 1 - u * u));
            Vec3 dir = new Vec3(ring * Math.cos(phi), u * 0.72 + 0.28, ring * Math.sin(phi)).normalize();
            double speed = radius * (0.45 + 1.15 * seedChance(i, 3));
            double life = 0.55 + 0.45 * seedChance(i, 4);
            double local = Math.min(1, t / life);
            Vec3 p = center.add(dir.scale(speed * (1 - Math.pow(1 - local, 2.2))))
                    .add(0, -2.2 * local * local * (0.5 + seedChance(i, 5)), 0);
            mote(spell, i, p, (0.09 + seedChance(i, 2) * 0.13) * (1.15 - local * 0.55),
                    age * 2.6 + i * 3.1, alpha * (1 - local) * (1 - local), local);
        }
    }

    void vineMotes(Vec3 start, Vec3 end, double age, double alpha) {
        if (alpha < 0.02 || !decoration(start)) return;
        int stride = step(start);
        int count = (int)Math.min(28, 8 + start.distanceTo(end) * 2.0);
        for (int i = 0; i < count; i += stride) {
            double t = (i + 0.5) / count;
            double sway = Math.sin(age * 0.06 + i * 1.7) * 0.28;
            Vec3 p = start.lerp(end, t).add(0, 0.24 + 0.5 * seedChance(i, 2) + sway, 0);
            double life = (age * 0.02 + i * 0.13) % 1.0;
            mote(SpellDefinition.THORN_IMPALE, i, p, 0.07 + seedChance(i, 3) * 0.05,
                    age + i * 4.1, alpha * 0.55 * envelope(life), life);
        }
    }

    void horizontalSlash(int tile, Vec3 at, Vec3 across, Vec3 forward,
                         double halfWidth, double halfDepth, double alpha) {
        Vec3 normal = across.cross(forward).normalize();
        slash(tile, at.add(normal.scale(0.018)), across, forward.add(normal.scale(0.07)).normalize(),
                halfWidth, halfDepth, alpha, false);
        slash(tile, at.subtract(normal.scale(0.018)), across, forward.subtract(normal.scale(0.07)).normalize(),
                halfWidth, halfDepth, alpha * 0.58, false);
    }

    void slash(int tile, Vec3 at, Vec3 longAxis, Vec3 shortAxis,
               double halfWidth, double halfHeight, double alpha, boolean mirror) {
        if (alpha < 0.01) return;
        int columns = SLASH.width / SLASH.tileWidth;
        int x = tile % columns * SLASH.tileWidth, y = tile / columns * SLASH.tileHeight;
        float inset = 0.5F;
        float u0 = (x + inset) / SLASH.width, u1 = (x + SLASH.tileWidth - inset) / SLASH.width;
        if (mirror) { float swap = u0; u0 = u1; u1 = swap; }
        float v0 = (y + inset) / SLASH.height, v1 = (y + SLASH.tileHeight - inset) / SLASH.height;
        VertexConsumer out = source.getBuffer(SLASH.renderType());
        double rx = longAxis.x * halfWidth, ry = longAxis.y * halfWidth, rz = longAxis.z * halfWidth;
        double vx = shortAxis.x * halfHeight, vy = shortAxis.y * halfHeight, vz = shortAxis.z * halfHeight;
        vertex(out, at.x - rx + vx, at.y - ry + vy, at.z - rz + vz, u0, v0, alpha, true, 0xFFFFFF);
        vertex(out, at.x + rx + vx, at.y + ry + vy, at.z + rz + vz, u1, v0, alpha, true, 0xFFFFFF);
        vertex(out, at.x + rx - vx, at.y + ry - vy, at.z + rz - vz, u1, v1, alpha, true, 0xFFFFFF);
        vertex(out, at.x - rx - vx, at.y - ry - vy, at.z - rz - vz, u0, v1, alpha, true, 0xFFFFFF);
    }

    private static double seedChance(int seed, int salt) {
        return Math.floorMod(seed * 1664525L + salt * 1013904223L, 1000L) / 1000.0;
    }
}
