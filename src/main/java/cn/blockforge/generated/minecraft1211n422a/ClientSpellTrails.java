package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** 客户端逐刻记录飞行路径，尾迹独立存活；不发包、不影响弹体与伤害。 */
final class ClientSpellTrails {
    private record Point(SpellDefinition spell, Vec3 at, Vec3 direction, double born,
                         int life, int seed, double size) { }
    private record Sample(Entity entity, Vec3 at) { }
    private static final ArrayDeque<Point> POINTS = new ArrayDeque<>();
    private static final Map<Integer, Sample> LAST = new HashMap<>();
    private static ClientLevel currentLevel;
    private static long sampledAt = Long.MIN_VALUE;
    private static int sequence;
    private static final int MAX_POINTS = 640;

    static void clear() {
        POINTS.clear(); LAST.clear(); currentLevel = null; sampledAt = Long.MIN_VALUE; sequence = 0;
    }

    static boolean hasPoints() { return !POINTS.isEmpty(); }

    static void tick(ClientLevel level) {
        if (level != currentLevel) { clear(); currentLevel = level; }
        if (level == null) return;
        long now = level.getGameTime();
        if (now == sampledAt) return;
        sampledAt = now;
        POINTS.removeIf(p -> now - p.born > p.life);
        var mc = Minecraft.getInstance();
        var setting = mc.options.particles().get();
        int cap = setting == ParticleStatus.MINIMAL ? 160 : setting == ParticleStatus.DECREASED ? 360 : MAX_POINTS;
        int emissions = setting == ParticleStatus.MINIMAL ? 1 : setting == ParticleStatus.DECREASED ? 2 : 4;
        var seen = new HashSet<Integer>();
        int tracked = 0;
        for (Entity entity : level.entitiesForRendering()) {
            SpellDefinition spell;
            double size;
            if (entity instanceof SpellProjectile projectile) {
                spell = projectile.spell();
                // 斩击由整段弧光加残影表现，再沿航线撒粒子就又会变回「一粒一粒」。
                if (spell == SpellDefinition.FLAME_SLASH || spell == SpellDefinition.GALE_SLASHES
                        || spell == SpellDefinition.WIND_BLADE) continue;
                if (spell == SpellDefinition.ROCK_ORBS && projectile.getDeltaMovement().lengthSqr() < 0.001) continue;
                size = spell == SpellDefinition.PYROBLAST ? 0.24 : 0.15;
            } else if (entity instanceof BlazingSun sun && sun.isReleased()) {
                spell = SpellDefinition.BLAZING_SUN; size = Math.min(0.7, sun.radius() * 0.25);
            } else continue;
            // 中级投射射程五个区块、基础投射八个区块，采样半径要盖住整条航线，
            // 否则玩家打远处时尾迹会在半路凭空断掉。
            if (mc.getCameraEntity() == null || entity.distanceToSqr(mc.getCameraEntity()) > 192 * 192) continue;
            if (++tracked > 96) break;
            seen.add(entity.getId());
            Vec3 at = entity.position();
            Sample previous = LAST.put(entity.getId(), new Sample(entity, at));
            if (previous == null || previous.entity != entity) continue;
            Vec3 delta = at.subtract(previous.at);
            double distance = delta.length();
            if (distance < 0.025 || distance > 8) continue;
            int count = Math.min(emissions, Math.max(1, (int)Math.ceil(distance / 0.22)));
            Vec3 direction = delta.scale(1 / distance);
            for (int i = 0; i < count; i++) {
                double t = (i + 0.5) / count;
                POINTS.addLast(new Point(spell, previous.at.lerp(at, t), direction,
                        now - 1 + t, spell.medium().equals("fire") ? 16 : 12, sequence++, size));
                while (POINTS.size() > cap) POINTS.removeFirst();
            }
        }
        LAST.keySet().removeIf(id -> !seen.contains(id));
        while (POINTS.size() > cap) POINTS.removeFirst();
    }

    static void render(ClientSpellSprites sprites, ClientLevel level, Vec3 camera, float partial) {
        double now = (double)level.getGameTime() + partial;
        for (Point point : POINTS) {
            double age = now - point.born;
            if (age < 0 || age >= point.life || point.at.distanceToSqr(camera) > 192 * 192) continue;
            // 飞行轨迹不属于贴身护罩，不把准星正前方的弹道抹掉。
            sprites.scope(-1, -1);
            sprites.lighting(level, point.at);
            sprites.trailSample(point.spell, point.at, point.direction, age, point.life,
                    point.seed, point.size, 0.95);
        }
        sprites.scope(-1, -1);
    }

    private ClientSpellTrails() { }
}
