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

/** 八张像素原画加过渡帧；贴片仅平移，火焰始终以世界向上为纵轴。 */
final class ClientSpellSprites {
    private static final Vec3 UP = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    private static final double GOLDEN_ANGLE = 2.399963229728653;
    private final MultiBufferSource source;
    private final Matrix4f matrix;
    private final Vec3 camera, cameraRight, cameraUp;
    private final int particleStep;
    private int light;

    ClientSpellSprites(MultiBufferSource source, Matrix4f matrix, Camera camera) {
        this.source = source;
        this.matrix = matrix;
        this.camera = camera.getPosition();
        var left = camera.getLeftVector();
        var up = camera.getUpVector();
        cameraRight = new Vec3(-left.x(), -left.y(), -left.z());
        cameraUp = new Vec3(up.x(), up.y(), up.z());
        ParticleStatus setting = Minecraft.getInstance().options.particles().get();
        particleStep = setting == ParticleStatus.MINIMAL ? 3 : setting == ParticleStatus.DECREASED ? 2 : 1;
    }

    void lighting(ClientLevel level, Vec3 at) {
        light = LevelRenderer.getLightColor(level, BlockPos.containing(at));
    }

    void charge(SpellDefinition spell, Vec3 hand, Vec3 feet, Vec3 direction,
                double age, double duration, double alpha) {
        int row = mediumRow(spell.medium());
        boolean energy = row >= 8;
        // 加速施法时也走完八张原画；正常施法约每秒十六张原画，过渡贴图约每秒六十四帧。
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
            // 世界 Y 轴锁定：无论向上或向下瞄准，火焰尖端都朝天而不是朝镜头歪倒。
            double width = spell.medium().equals("wind") ? 0.36 : 0.27;
            double height = spell.medium().equals("fire") ? 0.34 : 0.27;
            Vec3 center = spell.medium().equals("fire") ? hand.add(0, 0.14, 0) : hand;
            animated(row, center, right, UP, width, height, frameClock, alpha, emissive(row));
        }
        // 蓄力小粒子沿固定轨迹向手心聚拢；不让主贴片在轨道上摇晃。
        for (int i = 0; i < 9; i += particleStep) {
            double life = (age + i * 2.35) % 18.0 / 18.0;
            double angle = i * GOLDEN_ANGLE + spell.ordinal() * 0.37;
            double radius = 0.44 * (1 - life) + 0.055;
            Vec3 pos = hand.add(right.scale(Math.cos(angle) * radius))
                    .add(UP.scale(Math.sin(angle) * radius * 0.7 + (row == 0 ? life * 0.30 : 0)));
            particle(spell, (i / particleStep) % 3, pos, 0.043, alpha * envelope(life) * 0.80);
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
        }
        if (projectile && age < 8) {
            Vec3 direction = end.subtract(start);
            for (int i = 0; i < 9; i += particleStep) {
                double local = age - i * 0.38;
                if (local < 0 || local > 4.4) continue;
                double along = Math.min(1, local / 4);
                Vec3 pos = start.add(direction.scale(along)).add(0, -i * 0.012, 0);
                particle(spell, (i / particleStep) % 3, pos, 0.048, alpha * (1 - local / 4.4) * 0.65);
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

    void shatter(SpellDefinition spell, Vec3 at, double age, double duration, double alpha) {
        burst(spell, at, age, duration, 15, alpha * 0.8);
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
            particle(spell, (i / particleStep) % 3, pos, 0.045 + (i % 3) * 0.015,
                    alpha * Math.min(1, age / 1.5 + 0.2) * (1 - life) * (1 - life));
        }
    }

    void flame(Vec3 base, double size, double age, double alpha) {
        // 释放后的火焰使用完整逐帧原画；大量火法只保留少数主火焰，其余改用不同火星图案。
        animated(0, base.add(0, size * 0.25, 0), uprightRight(base, Z), UP,
                size * 0.85, size * 1.15, age * 0.8, alpha, true);
    }

    void fireParticle(Vec3 position, int variant, double size, double alpha) {
        particle(SpellDefinition.FIREBALL, Math.floorMod(variant, 3), position, size, alpha);
    }

    private void particle(SpellDefinition spell, int variant, Vec3 position, double size, double alpha) {
        int tile = spell.visualBase().ordinal() * 3 + variant;
        plane(PARTICLES, tile, position, cameraRight, cameraUp, size, size, 0, alpha,
                emissive(mediumRow(spell.medium())));
    }

    private void animated(int row, Vec3 p, Vec3 right, Vec3 up, double width, double height,
                          double clock, double alpha, boolean emissive) {
        int cycle = row == 2 || row == 3 || row == 6 ? 56 : 32;
        int frame = Math.floorMod((int)Math.floor(clock * 4), cycle);
        plane(FRAMES, row * 56 + frame, p, right, up, width, height, 0, alpha, emissive);
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
        if (alpha < 0.005) return;
        Vec3 r = right.scale(Math.cos(spin)).add(up.scale(Math.sin(spin))).scale(width);
        Vec3 v = up.scale(Math.cos(spin)).subtract(right.scale(Math.sin(spin))).scale(height);
        int columns = atlas.width / atlas.tileWidth;
        int x = tile % columns * atlas.tileWidth, y = tile / columns * atlas.tileHeight;
        // 小量内缩阻止图集串边；最近邻采样保留像素方块边缘。
        float u0 = (x + 0.02F) / atlas.width, u1 = (x + atlas.tileWidth - 0.02F) / atlas.width;
        float v0 = (y + 0.02F) / atlas.height, v1 = (y + atlas.tileHeight - 0.02F) / atlas.height;
        VertexConsumer out = source.getBuffer(atlas.type);
        vertex(out, center.subtract(r).add(v), u0, v0, alpha, emissive);
        vertex(out, center.add(r).add(v), u1, v0, alpha, emissive);
        vertex(out, center.add(r).subtract(v), u1, v1, alpha, emissive);
        vertex(out, center.subtract(r).subtract(v), u0, v1, alpha, emissive);
    }

    private void vertex(VertexConsumer out, Vec3 at, float u, float v, double alpha, boolean emissive) {
        Vec3 p = at.subtract(camera);
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z)
                .setColor(255, 255, 255, (int)(Math.clamp(alpha, 0, 1) * 255))
                .setUv(u, v).setLight(emissive ? 0xF000F0 : light);
    }

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
}
