package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

import static cn.blockforge.generated.minecraft1211n422a.SpellRenderTypes.Material.*;

/** 原创材质动画：有重量的实体、会生长的枝条、液滴和烟火，不改变服务端判定。 */
final class ClientSpellMaterials {
    private static final Vec3 UP = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    private final MultiBufferSource source;
    private final Matrix4f matrix;
    private final Vec3 camera;
    private final ClientSpellSprites sprites;
    private int light;

    ClientSpellMaterials(MultiBufferSource source, Matrix4f matrix, Vec3 camera, ClientSpellSprites sprites) {
        this.source = source; this.matrix = matrix; this.camera = camera; this.sprites = sprites;
    }
    void lighting(ClientLevel level, Vec3 at) {
        light = LevelRenderer.getLightColor(level, BlockPos.containing(at));
    }
    static Vec3 ground(ClientLevel level, Vec3 at) {
        HitResult hit = level.clip(new ClipContext(at.add(0, 0.2, 0), at.add(0, -5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation().add(0, 0.025, 0);
    }
    boolean draw(SpellDefinition spell, Vec3 start, Vec3 end, Vec3 anchor, Vec3 floor,
                 double age, double duration, double alpha) {
        Vec3 dir = end.subtract(start).normalize();
        if (dir.lengthSqr() < 0.01) dir = Z;
        Vec3 right = side(dir), vertical = dir.cross(right).normalize();
        // 释放后四刻内完成飞行，命中后主体消失，避免弹体悬停在命中点。
        double travel = Math.min(1, age / 4.0), hitAge = Math.max(0, age - 4);
        Vec3 head = start.lerp(end, travel);
        switch (spell) {
            case FIREBALL -> {
                if (age < 4) {
                    // 一枚逐帧火焰核心，尾迹混用三种已有火星和烟雾，不再复制五枚同图火焰。
                    flame(head, 0.58, age, alpha);
                    for (int i = 1; i <= 7; i++) {
                        double back = i * 0.12;
                        Vec3 p = head.subtract(dir.scale(back)).add(right.scale(Math.sin(i * 2.15) * 0.045));
                        sprites.fireParticle(p, i, 0.08 + (i % 3) * 0.022, alpha * (0.78 - i * 0.075));
                        if (i % 2 == 0) puff(p.add(0, 0.02, 0), 0.08 + i * 0.018, 0x8C837E, alpha * 0.20);
                    }
                } else {
                    double spread = Math.min(1, hitAge / 5);
                    for (int i = 0; i < 11; i++) {
                        double a = i * 2.399963;
                        Vec3 p = end.add(Math.cos(a) * spread * (0.45 + i % 3 * 0.20),
                                0.08 + hitAge * (0.025 + i % 2 * 0.012), Math.sin(a) * spread * (0.45 + i % 3 * 0.20));
                        if (i < 3 && hitAge < 8) flame(p, 0.40 * (1 - hitAge / 11), age + i * 5, alpha * 0.85);
                        else sprites.fireParticle(p, i, 0.10 + i % 3 * 0.025, alpha * Math.max(0, 1 - hitAge / 10));
                        if (i % 2 == 0) puff(p.add(0, 0.15 + hitAge * 0.025, 0),
                                0.15 + hitAge * 0.025, 0x8C837E, alpha * 0.36);
                    }
                }
            }
            case FLAME_BLESSING -> {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + age * 0.018;
                    Vec3 p = anchor.add(Math.cos(a) * 0.66, -0.45, Math.sin(a) * 0.66);
                    flame(p, 0.28, age + i * 3, alpha * 0.85);
                }
            }
            case WATER_BOLT, POISON_BOLT -> {
                boolean poison = spell == SpellDefinition.POISON_BOLT;
                int tint = poison ? 0x9AC245 : 0xB4EBFF;
                if (age < 4) {
                    blob(head, 0.23, 0.30, age, tint, alpha * 0.9);
                    for (int i = 1; i < 5; i++) blob(head.subtract(dir.scale(i * 0.16)), 0.13 - i * 0.018,
                            0.14, age + i, tint, alpha * 0.7);
                } else {
                    droplets(end, hitAge, 10, tint, alpha, 0.09);
                    if (poison) {
                        for (int i = 0; i < 5; i++) {
                            double a = i * 1.256;
                            puff(end.add(Math.cos(a) * 0.4, hitAge * 0.035, Math.sin(a) * 0.4),
                                    0.22 + hitAge * 0.014, 0x8BAA4C, alpha * 0.3);
                        }
                    }
                }
            }
            case MOISTURE -> {
                for (int i = 0; i < 12; i++) {
                    double a = i * 2.399, fall = (age * 0.045 + i * 0.17) % 1.8;
                    blob(anchor.add(Math.cos(a) * 0.6, 1.0 - fall, Math.sin(a) * 0.6),
                            0.045, 0.105, age + i, 0xBAF5FF, alpha * 0.65);
                }
            }
            case ICE_SPIKE -> {
                if (age < 4) {
                    // 一枚长主锥配三枚错位副刃，轮廓更接近天然冰晶而不是单根柱体。
                    crystal(ICE, head, dir, 0.21, 0.88, 0xF5FDFF, alpha);
                    crystal(ICE, head.add(right.scale(0.23)).subtract(dir.scale(0.24)).add(vertical.scale(0.04)),
                            dir.add(right.scale(0.10)).normalize(), 0.085, 0.48, 0xBDDFFF, alpha * 0.92);
                    crystal(ICE, head.subtract(right.scale(0.20)).subtract(dir.scale(0.31)).subtract(vertical.scale(0.05)),
                            dir.subtract(right.scale(0.09)).normalize(), 0.075, 0.40, 0xD8F4FF, alpha * 0.88);
                    crystal(ICE, head.subtract(dir.scale(0.38)).add(vertical.scale(0.17)),
                            dir.add(vertical.scale(0.12)).normalize(), 0.055, 0.30, 0x9DD8F5, alpha * 0.78);
                } else debris(ICE, end, hitAge, 13, 0xDDFAFF, alpha, 0.11);
            }
            case ICE_SHIELD -> {
                double form = ease(age / 9);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    Vec3 p = anchor.add(Math.cos(a) * 0.85, -0.6 + form * 0.5, Math.sin(a) * 0.85);
                    crystal(ICE, p, new Vec3(Math.cos(a) * 0.16, 1, Math.sin(a) * 0.16).normalize(),
                            0.29 * form, 0.76 * form, 0xE6F8FF, alpha * 0.82);
                }
                if (age > duration - 9) debris(ICE, anchor, age - duration + 9, 8, 0xD9F4FF, alpha, 0.08);
            }
            case ROCKFALL -> {
                double drop = Math.min(1, age / 7.0);
                // 大小不一的主岩与碎岩错位坠落，避免三块等大的方盒看起来像复制模型。
                double[][] rocks = {
                        {-0.62, 0.00, 0.54, 0.46, 0.49},
                        { 0.06, 0.38, 0.68, 0.57, 0.60},
                        { 0.70,-0.18, 0.43, 0.52, 0.40},
                        {-0.08,-0.55, 0.34, 0.31, 0.38}
                };
                for (int i = 0; i < rocks.length; i++) {
                    double[] rock = rocks[i];
                    double local = Math.clamp((age - i * 0.22) / 7.0, 0, 1);
                    Vec3 p = end.add(rock[0], 4.15 * (1 - local * local) + rock[4], rock[1]);
                    if (age < 9) {
                        double r = rock[2], h = rock[3];
                        box(STONE, p, r, h, r * (0.78 + i * 0.05), age * (0.09 + i * 0.01) + i,
                                age * (0.055 + i * 0.008), 0xFFFFFF, alpha);
                        box(STONE, p.add(0, h * 0.68, 0), r * 0.72, h * 0.44, r * 0.62,
                                age * 0.07 - i, -age * 0.04, 0xD8CABC, alpha * 0.88);
                    }
                }
                if (age >= 7) {
                    debris(STONE, end, age - 7, 16, 0xC8BDB0, alpha, 0.19);
                    dust(end, age - 7, alpha, 0xB8A28A);
                }
            }
            case STONE_ARMOR, IRON_WALL -> {
                boolean iron = spell == SpellDefinition.IRON_WALL;
                var mat = iron ? STEEL : STONE;
                double form = ease(age / 10), dissolve = Math.max(0, (age - duration + 10) / 10);
                int count = iron ? 4 : 6;
                for (int i = 0; i < count; i++) {
                    double a = i * Math.PI * 2 / count;
                    double r = 0.79 + (1 - form) * 0.45;
                    Vec3 p = anchor.add(Math.cos(a) * r, -0.9 * (1 - form) - dissolve * 0.7, Math.sin(a) * r);
                    box(mat, p, iron ? 0.42 : 0.28, (iron ? 0.62 : 0.43) * form, 0.10,
                            Math.PI / 2 - a, iron ? 0 : Math.sin(i * 3.1) * 0.15, 0xFFFFFF, alpha * 0.92);
                    if (!iron) box(mat, p.add(0, -0.53, 0), 0.22, 0.12 * form, 0.14, Math.PI / 2 - a, 0, 0xC4BAA8, alpha);
                }
                if (age < 12) dust(floor, age, alpha * 0.55, iron ? 0xC3CAD0 : 0xA79179);
            }
            case THORNS -> {
                double growth = ease(age / 12);
                for (int strand = 0; strand < 3; strand++) {
                    Vec3 last = floor.add(Math.cos(strand * 2.094) * 0.5, 0, Math.sin(strand * 2.094) * 0.5);
                    for (int i = 1; i <= 16; i++) {
                        double f = i / 16.0;
                        if (f > growth) break;
                        double a = strand * 2.094 + f * Math.PI * 2.8;
                        Vec3 p = floor.add(Math.cos(a) * (0.55 - f * 0.13), f * 1.7, Math.sin(a) * (0.55 - f * 0.13));
                        stem(last, p, 0.065 * (1 - f * 0.45), alpha);
                        if (i % 3 == 0) {
                            Vec3 outward = new Vec3(Math.cos(a), 0.35, Math.sin(a)).normalize();
                            crystal(VINE, p, outward, 0.05, 0.20, 0xDDD7A9, alpha);
                            leaf(p.add(outward.scale(0.15)), 0.16, a, alpha);
                        }
                        last = p;
                    }
                }
            }
            case REJUVENATION -> {
                for (int i = 0; i < 9; i++) {
                    double a = i * 2.399 + age * 0.035, rise = (age * 0.024 + i * 0.17) % 1.7;
                    leaf(anchor.add(Math.cos(a) * 0.58, rise - 0.7, Math.sin(a) * 0.58), 0.18,
                            a + Math.sin(age * 0.15 + i) * 0.4, alpha * Math.sin(Math.PI * rise / 1.7));
                }
            }
            case SHARP_EDGE -> {
                if (age < 4) for (int i = 0; i < 3; i++) {
                    Vec3 p = head.add(right.scale((i - 1) * 0.26));
                    crystal(STEEL, p, dir, 0.09, 0.75, 0xFFFFFF, alpha);
                }
                if (age >= 4) debris(STEEL, end, hitAge, 7, 0xD6DFE6, alpha, 0.055);
            }
            case CORROSION -> {
                for (int i = 0; i < 12; i++) {
                    double a = i * 2.399, fall = (age * 0.04 + i * 0.19) % 1.7;
                    blob(anchor.add(Math.cos(a) * 0.56, 0.8 - fall, Math.sin(a) * 0.56), 0.065, 0.13,
                            age + i, 0xA6C543, alpha * 0.8);
                }
                for (int i = 0; i < 4; i++) puff(floor.add(Math.sin(i * 3) * 0.5,
                        0.15 + (age * 0.02 + i * 0.2) % 0.6, Math.cos(i * 3) * 0.5), 0.3, 0x718943, alpha * 0.25);
            }
            case WIND_BLADE -> {
                double life = Math.max(0, 1 - hitAge / 9);
                // 斩击以视线左右方向横向展开；只保留轻微上扬弧度，避免看成竖立的风环。
                Vec3 slashSide = new Vec3(-dir.z, 0, dir.x);
                if (slashSide.lengthSqr() < 0.01) slashSide = X;
                else slashSide = slashSide.normalize();
                for (int j = 0; j < 2; j++) for (int i = 0; i < 13; i++) {
                    double across = i / 6.0 - 1.0;
                    double bow = (1 - across * across) * 0.16 - 0.08;
                    Vec3 p = head.subtract(dir.scale(j * 0.24))
                            .add(slashSide.scale(across * (0.92 - j * 0.08)))
                            .add(0, bow - j * 0.035, 0);
                    double taper = Math.max(0, 1 - Math.abs(across) * 0.55);
                    puff(p, (0.10 + taper * 0.10) * (1 - j * 0.12), 0xE0E6DC,
                            alpha * life * (0.50 - j * 0.13));
                }
            }
            case GALE_DASH -> {
                for (int i = 0; i < 9; i++) {
                    double f = i / 9.0, local = age - i * 1.4;
                    if (local < 0 || local > 15) continue;
                    Vec3 p = start.lerp(end, f).add(right.scale(Math.sin(i * 2.4) * 0.45)).add(0, -0.45 + local * 0.018, 0);
                    puff(p, 0.17 + local * 0.015, 0xC9D0C5, alpha * (1 - local / 15) * 0.4);
                    if (i % 3 == 0) leaf(p.add(0, 0.15, 0), 0.1, age * 0.15 + i, alpha * 0.7);
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private void droplets(Vec3 at, double age, int count, int color, double alpha, double size) {
        for (int i = 0; i < count; i++) {
            double a = i * 2.399, speed = 0.045 + (i % 3) * 0.015;
            Vec3 p = at.add(Math.cos(a) * speed * age, age * 0.075 - age * age * 0.006,
                    Math.sin(a) * speed * age);
            blob(p, size, size * 1.45, age + i, color, alpha * Math.max(0, 1 - age / 18));
        }
    }
    private void debris(SpellRenderTypes.Material mat, Vec3 at, double age, int count, int color, double alpha, double size) {
        for (int i = 0; i < count; i++) {
            double a = i * 2.399, speed = 0.065 + (i % 3) * 0.025;
            Vec3 p = at.add(Math.cos(a) * speed * age, Math.max(0.04, age * (0.14 + i % 2 * 0.04) - age * age * 0.009),
                    Math.sin(a) * speed * age);
            double s = size * (0.65 + i % 3 * 0.22);
            if (mat == ICE) crystal(mat, p, new Vec3(Math.sin(a + age * 0.1), 0.7, Math.cos(a)).normalize(), s, s * 2, color, alpha);
            else box(mat, p, s, s * 0.7, s * 0.8, a + age * 0.13, age * 0.08, color, alpha);
        }
    }
    private void dust(Vec3 at, double age, double alpha, int color) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, r = 0.3 + age * 0.06;
            puff(at.add(Math.cos(a) * r, 0.12 + age * 0.016, Math.sin(a) * r),
                    0.2 + age * 0.012, color, alpha * Math.max(0, 1 - age / 28) * 0.45);
        }
    }
    void drawSun(BlazingSun sun, float partial) {
        Vec3 at = sun.getPosition(partial);
        Vec3 dir = sun.direction();
        if (dir.lengthSqr() < 0.01) dir = Z;
        double age = sun.tickCount + partial;
        double radius = sun.radius();
        double core = Math.clamp(radius, 0.7, 3.0);
        // 核心：几层交错火焰，亮度始终写满，不随世界光照变暗。
        for (int i = 0; i < 4; i++) {
            double a = age * 0.06 + i * Math.PI / 2.0;
            Vec3 p = at.add(Math.cos(a) * radius * 0.32, Math.sin(a * 1.3) * radius * 0.20,
                    Math.sin(a) * radius * 0.32);
            flame(p, core * (1.30 - i * 0.09), age + i * 7.0, 0.96);
        }
        // 环绕火星与烟，让大半径时仍能看出球形体积。
        for (int i = 0; i < 14; i++) {
            double a = age * 0.09 + i * 2.399963;
            double h = Math.sin(age * 0.07 + i) * radius * 0.55;
            Vec3 p = at.add(Math.cos(a) * radius * 1.28, h, Math.sin(a) * radius * 1.28);
            sprites.fireParticle(p, i, 0.075 + radius * 0.042, 0.82);
            if (i % 3 == 0) puff(p, 0.14 + radius * 0.035, 0x7A4030, 0.20);
        }
        if (sun.isReleased()) {
            // 飞行拖尾：火舌逐节变小，烟在后方散开。
            for (int i = 1; i <= 7; i++) {
                Vec3 p = at.subtract(dir.scale(i * 0.36)).add(
                        side(dir).scale(Math.sin(age * 0.22 + i) * 0.06));
                sprites.fireParticle(p, i + 3, 0.11 + radius * 0.040, Math.max(0.14, 0.72 - i * 0.075));
                if (i % 2 == 0) puff(p.add(0, 0.10, 0), 0.16 + i * 0.025, 0x6E4A3C, 0.24);
            }
        }
    }

    void drawProjectile(SpellProjectile entity, float partial) {
        Vec3 at = entity.getPosition(partial);
        Vec3 dir = entity.getDeltaMovement().normalize();
        if (dir.lengthSqr() < 0.01) dir = entity.getLookAngle();
        double age = entity.tickCount + partial;
        switch (entity.spell()) {
            case ROCK_ORBS -> box(STONE, at, 0.5, 0.5, 0.5, age * 0.10, age * 0.075, 0xFFFFFF, 1);
            case PYROBLAST -> {
                flame(at, 0.85, age, 1);
                for (int i = 1; i <= 9; i++) {
                    Vec3 p = at.subtract(dir.scale(i * 0.19)).add(side(dir).scale(Math.sin(age * 0.24 + i * 2.1) * 0.07));
                    sprites.fireParticle(p, i, 0.10 + i % 3 * 0.035, Math.max(0.16, 0.92 - i * 0.08));
                    if (i % 3 == 0) puff(p, 0.12 + i * 0.015, 0x665A57, 0.28);
                }
            }
            case WATER_PYTHON -> {
                Vec3 side = side(dir);
                // 蛇头至尾尖总长五格；尾部波浪在局部侧轴上摆动。
                for (int i = 0; i <= 20; i++) {
                    double t = i / 20.0;
                    Vec3 p = at.subtract(dir.scale(Math.min(5, age * 0.65) * t)).add(side.scale(Math.sin(age * 0.32 - t * 8) * 0.24 * t));
                    blob(p, 0.42 * (1 - t) + 0.055, 0.34 * (1 - t) + 0.05, age + i, 0x8CDFFF, 0.86);
                }
                crystal(ICE, at.add(dir.scale(0.2)), dir, 0.16, 0.45, 0xDEF9FF, 0.8);
            }
            case FLAME_SLASH, GALE_SLASHES -> {
                Vec3 sideways = side(dir);
                if (entity.spell() == SpellDefinition.GALE_SLASHES && entity.index() == 1) sideways = dir.cross(sideways).normalize();
                for (int i = 0; i <= 20; i++) {
                    double t = i / 20.0, offset = (t - 0.5) * 5;
                    Vec3 p = at.add(sideways.scale(offset)).subtract(dir.scale(offset * offset * 0.12));
                    if (entity.spell() == SpellDefinition.FLAME_SLASH) {
                        // 斩击主体仅放七簇逐帧火焰，间隙用三种火星图案和烟边连接。
                        if (i % 3 == 0) flame(p, 0.30 + 0.14 * Math.sin(Math.PI * t), age + i * 1.7, 0.92);
                        else sprites.fireParticle(p.add(0, Math.sin(i * 1.8) * 0.06, 0), i,
                                0.105 + i % 3 * 0.025, 0.88);
                        if (i % 4 == 2) puff(p.subtract(dir.scale(0.05)), 0.13, 0x665A57, 0.20);
                    } else crystal(LIQUID, p, dir, 0.07, 0.3, 0xC9FFE9, 0.86);
                }
            }
            default -> { }
        }
    }
    boolean drawNew(SpellDefinition spell, Vec3 start, Vec3 end, Vec3 anchor, Vec3 floor,
                    double age, double duration, double alpha, boolean attached, int phase) {
        switch (spell) {
            case WATER_PRISON -> {
                blob(anchor, 0.78, 1.2, age * 0.15, 0x7FD8FF, alpha * 0.40);
                droplets(anchor.add(0, 0.8, 0), age % 16, 6, 0xC6F3FF, alpha, 0.06);
            }
            case ICE_COFFIN -> {
                if (attached) {
                    box(ICE, anchor, 0.63, 1.15, 0.63, 0, 0, 0xC7F0FF, alpha * 0.55);
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2 + Math.PI / 4;
                        crystal(ICE, floor.add(Math.cos(a) * 0.7, 0.4, Math.sin(a) * 0.7), UP, 0.18, 0.9, 0xD7F7FF, alpha);
                    }
                } else debris(ICE, end.add(0, 0.6, 0), age, 24, 0xE3FAFF, alpha, 0.14);
            }
            case ICE_SLIDE -> {
                if (start.distanceToSqr(end) < 0.01) {
                    box(ICE, end.add(0, 0.015, 0), 0.72, 0.025, 0.72, 0, 0, 0xC0EAFF, alpha * 0.75);
                    for (int i = 0; i < 5; i++) crystal(ICE, end.add(Math.sin(i * 2.4) * 0.6, 0.03, Math.cos(i * 2.4) * 0.6), UP, 0.04, 0.14, 0xEEFFFF, alpha);
                }
            }
            case MUD_SWAMP -> {
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                    if (x * x + z * z > 9) continue;
                    box(STONE, floor.add(x * 0.86, 0.02, z * 0.86), 0.48, 0.035, 0.48, 0, 0, 0x5C4833, alpha * 0.8);
                    if ((x + z) % 3 == 0) blob(floor.add(x * 0.86, 0.05, z * 0.86), 0.23, 0.06, age + x, 0x716346, alpha * 0.7);
                }
            }
            case PLAGUE -> {
                for (int i = 0; i < 36; i++) {
                    double a = i * 2.399 + age * 0.003, radius = 5.8 * Math.sqrt((i + 1) / 36.0);
                    puff(end.add(Math.cos(a) * radius, 0.4 + Math.sin(age * 0.035 + i) * 0.3 + (i % 3) * 0.65, Math.sin(a) * radius),
                            0.85, 0x9BAD40, alpha * 0.3);
                }
            }
            case PYROBLAST -> {
                if (duration > 10) for (int i = 0; i < 24; i++) {
                    double a = i * 2.399, r = Math.min(4, age * (0.16 + i % 4 * 0.027));
                    Vec3 p = end.add(Math.cos(a) * r, Math.sin(i * 1.4) * r * 0.42 + age * 0.025, Math.sin(a) * r);
                    // 爆心使用少量大火焰，外圈改用已有三种火星与烟雾，轮廓更丰富且顶点更少。
                    if (i < 5) flame(p, 0.62 * Math.max(0.12, 1 - age / duration), age + i * 4, alpha * 0.92);
                    else sprites.fireParticle(p, i, 0.12 + i % 3 * 0.045,
                            alpha * Math.max(0, 1 - age / duration) * 0.9);
                    if (i % 2 == 0) puff(p.add(0, age * 0.04, 0),
                            0.28 + age * 0.024 + i % 3 * 0.04, i % 4 == 0 ? 0x6E625E : 0x8C6F52, alpha * 0.30);
                }
            }
            case BOULDER -> {
                if (phase == SpellVisualPayload.CHARGE) {
                    box(STONE, floor.add(0, 0.02, 0), 1.0, 0.02, 1.0, age * 0.02, 0, 0xB89A72, alpha * 0.45);
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2.0 + age * 0.02;
                        crystal(STONE, floor.add(Math.cos(a) * 1.1, 0.02, Math.sin(a) * 1.1), UP,
                                0.09, 0.32, 0xE4D4BC, alpha * 0.6);
                    }
                } else if (phase == SpellVisualPayload.RELEASE && attached) {
                    double form = ease(age / 8), dissolve = Math.max(0, (age - duration + 12) / 12);
                    int count = 8;
                    for (int i = 0; i < count; i++) {
                        double a = i * Math.PI * 2 / count + age * 0.008;
                        Vec3 p = anchor.add(Math.cos(a) * 0.68, -0.25 + (i % 2) * 0.55 - dissolve * 0.9,
                                Math.sin(a) * 0.68);
                        box(STONE, p, 0.26, 0.22 * form, 0.11, Math.PI / 2 - a, Math.sin(i * 2.1) * 0.25,
                                0xD9C7B0, alpha * 0.9);
                    }
                    crystal(STONE, anchor.add(0, 0.45, 0), UP, 0.12, 0.35 * form, 0xF0E2CC, alpha * 0.7);
                } else if (phase == SpellVisualPayload.SHATTER) {
                    debris(STONE, end.add(0, 0.4, 0), age % 16, 14, 0xCDBCA6, alpha, 0.1);
                }
            }
            case MYRIAD_METEORS -> {
                if (phase == SpellVisualPayload.CHARGE) {
                    box(STONE, floor.add(0, 0.02, 0), 1.2, 0.02, 1.2, age * 0.03, 0, 0xB89A72, alpha * 0.45);
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2.0 - age * 0.03;
                        crystal(STONE, floor.add(Math.cos(a) * 1.3, 0.02, Math.sin(a) * 1.3), UP,
                                0.11, 0.42, 0xE4D4BC, alpha * 0.65);
                    }
                    return true;
                }
                if (phase != SpellVisualPayload.METEOR_IMPACT) return true;
                debris(STONE, end.add(0, 0.3, 0), age, 30, 0xC8B79F, alpha, 0.16);
                dust(end, age, alpha, 0xA98F70);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3.0 + age * 0.01;
                    double r = 0.8 + age * 0.05;
                    box(STONE, end.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 0.16, 0.10, 0.12,
                            a, age * 0.1, 0xE0D2BE, alpha * 0.8);
                }
            }
            case VINE_SERVANTS, POISON_VINE, SWORD_SUMMON, ROCK_ORBS, WATER_PYTHON, FLAME_SLASH, GALE_SLASHES -> { }
            default -> { return false; }
        }
        return true;
    }
    private void flame(Vec3 p, double size, double age, double alpha) {
        sprites.flame(p, size, age, alpha);
    }
    private void puff(Vec3 p, double size, int color, double alpha) {
        sprite(SMOKE, p, size, size, 0, color, alpha, false);
    }
    private void leaf(Vec3 p, double size, double spin, double alpha) {
        sprite(LEAF, p, size, size, spin, 0xFFFFFF, alpha, false);
    }
    private void sprite(SpellRenderTypes.Material mat, Vec3 p, double width, double height, double spin,
                        int color, double alpha, boolean emissive) {
        Vec3 normal = camera.subtract(p).normalize();
        if (normal.lengthSqr() < 0.01) normal = Z;
        Vec3 u = side(normal), v = normal.cross(u).normalize();
        Vec3 r = u.scale(Math.cos(spin)).add(v.scale(Math.sin(spin))).scale(width);
        Vec3 up = v.scale(Math.cos(spin)).subtract(u.scale(Math.sin(spin))).scale(height);
        quad(mat, p.subtract(r).add(up), p.add(r).add(up), p.add(r).subtract(up), p.subtract(r).subtract(up), color, alpha, emissive, false);
    }
    private void stem(Vec3 a, Vec3 b, double width, double alpha) {
        Vec3 d = b.subtract(a).normalize(), u = side(d).scale(width), v = d.cross(u);
        Vec3[] q = {a.add(u).add(v), a.subtract(u).add(v), a.subtract(u).subtract(v), a.add(u).subtract(v)};
        Vec3 delta = b.subtract(a);
        for (int i = 0; i < 4; i++) quad(VINE, q[i], q[(i + 1) % 4], q[(i + 1) % 4].add(delta), q[i].add(delta), 0xFFFFFF, alpha, false, true);
    }
    private void box(SpellRenderTypes.Material mat, Vec3 p, double hx, double hy, double hz, double yaw,
                     double tilt, int color, double alpha) {
        Vec3[] q = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? -hx : hx, y = (i & 2) == 0 ? -hy : hy, z = (i & 4) == 0 ? -hz : hz;
            double xx = x * Math.cos(tilt) - y * Math.sin(tilt), yy = x * Math.sin(tilt) + y * Math.cos(tilt);
            q[i] = p.add(xx * Math.cos(yaw) + z * Math.sin(yaw), yy, -xx * Math.sin(yaw) + z * Math.cos(yaw));
        }
        int[][] faces = {{0,2,3,1},{4,5,7,6},{0,4,6,2},{1,3,7,5},{2,6,7,3},{0,1,5,4}};
        for (int[] f : faces) quad(mat, q[f[0]], q[f[1]], q[f[2]], q[f[3]], color, alpha, false, true);
    }
    private void crystal(SpellRenderTypes.Material mat, Vec3 p, Vec3 axis, double radius, double length, int color, double alpha) {
        Vec3 u = side(axis), v = axis.cross(u).normalize();
        Vec3 tip = p.add(axis.scale(length)), base = p.subtract(axis.scale(length * 0.65));
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5, b = (i + 1) * Math.PI * 2 / 5;
            Vec3 p1 = p.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            Vec3 p2 = p.add(u.scale(Math.cos(b) * radius)).add(v.scale(Math.sin(b) * radius));
            quad(mat, tip, p1, p2, p2, color, alpha, false, true);
            quad(mat, base, p2, p1, p1, color, alpha, false, true);
        }
    }
    private void blob(Vec3 p, double radius, double height, double age, int color, double alpha) {
        double wobble = 1 + Math.sin(age * 0.45) * 0.10;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) {
            double a = x * Math.PI / 4, b = (x + 1) * Math.PI / 4;
            double low = -Math.PI / 2 + y * Math.PI / 4, high = low + Math.PI / 4;
            quad(LIQUID, sphere(p, a, high, radius * wobble, height), sphere(p, b, high, radius * wobble, height),
                    sphere(p, b, low, radius * wobble, height), sphere(p, a, low, radius * wobble, height), color, alpha, false, true);
        }
    }
    private static Vec3 sphere(Vec3 p, double a, double b, double r, double h) {
        return p.add(Math.cos(a) * Math.cos(b) * r, Math.sin(b) * h, Math.sin(a) * Math.cos(b) * r);
    }
    private void quad(SpellRenderTypes.Material mat, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                      int color, double alpha, boolean emissive, boolean shade) {
        if (alpha < 0.005) return;
        if (shade) {
            Vec3 n = b.subtract(a).cross(c.subtract(a)).normalize();
            double factor = 0.65 + 0.25 * Math.abs(n.y) + 0.10 * Math.abs(n.x);
            color = ((int)((color >> 16 & 255) * factor) << 16) | ((int)((color >> 8 & 255) * factor) << 8) | (int)((color & 255) * factor);
        }
        VertexConsumer out = source.getBuffer(mat.type);
        vertex(out, a, 0, 0, color, alpha, emissive); vertex(out, b, 1, 0, color, alpha, emissive);
        vertex(out, c, 1, 1, color, alpha, emissive); vertex(out, d, 0, 1, color, alpha, emissive);
    }
    private void vertex(VertexConsumer out, Vec3 p, float u, float v, int color, double alpha, boolean emissive) {
        Vec3 r = p.subtract(camera);
        out.addVertex(matrix, (float)r.x, (float)r.y, (float)r.z)
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, (int)(Math.clamp(alpha, 0, 1) * 255))
                .setUv(u, v).setLight(emissive ? 0xF000F0 : light);
    }
    private static Vec3 side(Vec3 dir) { return dir.cross(Math.abs(dir.y) > 0.9 ? X : UP).normalize(); }
    private static double ease(double value) { double t = Math.clamp(value, 0, 1); return t * t * (3 - 2 * t); }
}
