package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 风系追加法术：风爆步与风葬回廊。
 *
 * <p>风爆步（中级·移动）给自己缠绕狂风：移速 +150%，碰到生物就把它击退七格、造成 15 点风伤
 * 并附加 8 秒“风袭”；风袭期间目标每触发一次扩散反应都会再挨 48 点爆炸伤害。</p>
 *
 * <p>风葬回廊（高级·伤害）进入“风狂”持续状态：每秒 100 魔素，期间不停在身边生成风暴场，
 * 风暴场每秒斩击场内的敌人并给施法者叠“风葬”。再按施法键会向后瞬退三格并挥出风十字斩，
 * 然后把全部层数当作斩击次数，在命中的敌人之间分摊。</p>
 */
public final class WindSpells {
    /** 风狂期间视角转向只剩原来的 76%（客户端做灵敏度缩放）。 */
    public static final double TURN_PENALTY = 0.24;

    private static final int STEP_TICKS = 160;
    private static final int STEP_TOUCH_INTERVAL = 20;
    private static final double STEP_TOUCH_RADIUS = 1.1;
    private static final float STEP_TOUCH_DAMAGE = 15.0F;
    private static final double STEP_KNOCK_BLOCKS = 7.0;
    private static final int ASSAULT_TICKS = 160;
    private static final float ASSAULT_BLAST = 48.0F;
    private static final Map<UUID, Map<UUID, Long>> STEP_NEXT_HIT = new HashMap<>();

    private static final int FRENZY_MAX_TICKS = 960;
    private static final double FRENZY_MANA_PER_TICK = 5.0;
    private static final int STORM_FIELD_INTERVAL = 8;
    private static final int STORM_FIELD_TICKS = 160;
    private static final double STORM_SPAWN_RADIUS = 4.0;
    private static final double STORM_REFRESH_RADIUS = 2.5;
    private static final double STORM_SLASH_RADIUS = 2.5;
    private static final int STORM_SLASH_INTERVAL = 20;
    private static final float STORM_SLASH_DAMAGE = 8.0F;
    private static final int STORM_MAX_FIELDS = 3;

    private static final double CROSS_BACKSTEP = 3.0;
    private static final double CROSS_RANGE = 15.0;
    private static final double CROSS_RADIUS = 5.0;
    private static final float CROSS_DAMAGE = 108.0F;
    private static final int CROSS_BREAK_LIMIT = 64;
    /** 黑曜石硬度为 50，因此“黑曜石硬度以下”用严格小于。 */
    private static final float CROSS_MAX_HARDNESS = 50.0F;

    private static final float BURIAL_SLASH_DAMAGE = 16.0F;
    private static final int BURIAL_SLASH_INTERVAL = 2;
    private static final int BURIAL_MAX_STACKS = 1024;
    /** 风葬斩击只让抗性生效 45%，等于无视 55% 的全抗性。 */
    private static final double RESISTANCE_KEPT = 0.45;

    private static final Map<UUID, Frenzy> FRENZIES = new HashMap<>();
    private static final List<StormField> FIELDS = new ArrayList<>();
    private static final List<Burial> BURIALS = new ArrayList<>();

    private static final class Frenzy {
        private final ServerLevel level;
        private final UUID owner;
        private final long born;
        private long nextFieldAt;
        private int stacks;

        private Frenzy(ServerLevel level, UUID owner, long born) {
            this.level = level;
            this.owner = owner;
            this.born = born;
            this.nextFieldAt = born + STORM_FIELD_INTERVAL;
        }

        private void addStack() {
            if (stacks < BURIAL_MAX_STACKS) stacks++;
        }
    }

    private static final class StormField {
        private final ServerLevel level;
        private final UUID owner;
        private final BlockPos pos;
        private final Vec3 center;
        private final long born;
        private long until;
        private long nextSlash;

        private StormField(ServerLevel level, UUID owner, BlockPos pos, long born) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5);
            this.born = born;
            this.until = born + STORM_FIELD_TICKS;
            this.nextSlash = born + STORM_SLASH_INTERVAL;
        }

        private void refresh(long now) {
            until = now + STORM_FIELD_TICKS;
        }
    }

    private static final class Burial {
        private final ServerLevel level;
        private final UUID owner;
        private final List<UUID> targets;
        private long nextAt;
        private int remaining;
        private int cursor;

        private Burial(ServerLevel level, UUID owner, List<UUID> targets, long nextAt, int remaining) {
            this.level = level;
            this.owner = owner;
            this.targets = targets;
            this.nextAt = nextAt;
            this.remaining = remaining;
        }
    }

    // ───────────────────────── 风爆步 ─────────────────────────

    /** 前摇结束：狂风缠身 8 秒，移动速度 +150%，碰到敌人就把它撞开。 */
    public static void beginBurstStep(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        NewSpellSystem.effect(player, NewSpellContent.WIND_STEP, STEP_TICKS);
        STEP_NEXT_HIT.put(player.getUUID(), new HashMap<>());
        player.displayClientMessage(Component.translatable("message.dimensional_mana.wind_burst_step_cast",
                STEP_TICKS / 20), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.15F, 1.25F);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.85F, 1.45F);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.9, player.getZ(),
                1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.9, player.getZ(),
                12, 0.6, 0.7, 0.6, 0.08);
        NewSpellSystem.visual(player, SpellDefinition.WIND_BURST_STEP, player.position(), player.position(), -1, 40);
    }

    /** 每刻检查身上有没有敌人：撞到就击退七格、15 点风伤并附加风袭。 */
    private static void tickBurstStep(ServerPlayer player, long now) {
        if (!player.hasEffect(NewSpellContent.WIND_STEP)) {
            STEP_NEXT_HIT.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();
        // 每隔 1.5 秒补一发包，让缠身狂风在 8 秒里持续可见（单包视觉持续 2 秒，两份重叠过渡）。
        if (now % 30 == 0)
            NewSpellSystem.visual(player, SpellDefinition.WIND_BURST_STEP, player.position(), player.position(), -1, 40);
        Map<UUID, Long> nextHits = STEP_NEXT_HIT.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        double half = player.getBbHeight() * 0.5;
        if (now % 4 == 0)
            level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + half, player.getZ(),
                    3, 0.45, player.getBbHeight() * 0.35, 0.45, 0.05);
        Vec3 center = player.position().add(0, half, 0);
        double radius = STEP_TOUCH_RADIUS + player.getBbWidth() * 0.5;
        for (LivingEntity victim : NewSpellSystem.enemies(player, center, radius)) {
            long next = nextHits.getOrDefault(victim.getUUID(), 0L);
            if (now < next) continue;
            nextHits.put(victim.getUUID(), now + STEP_TOUCH_INTERVAL);
            NewSpellSystem.damage(player, victim, STEP_TOUCH_DAMAGE, ElementType.WIND);
            NewSpellSystem.knock(player.position(), victim, STEP_KNOCK_BLOCKS);
            NewSpellSystem.effect(victim, NewSpellContent.WIND_ASSAULT, ASSAULT_TICKS);
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 2, 0.5, 0.5, 0.5, 0.0);
            level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 4, 0.4, 0.4, 0.4, 0.06);
            level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                    0.85F, 1.35F);
        }
    }

    /**
     * 风袭引爆：目标身上的风袭还在时，每次扩散反应都额外吃 48 点爆炸伤害。
     * 这里故意不走元素伤害通道，避免引爆本身又附着风元素、自己触发下一轮扩散。
     */
    public static void windAssaultBlast(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null || !target.isAlive()) return;
        if (!target.hasEffect(NewSpellContent.WIND_ASSAULT)) return;
        if (!(target.level() instanceof ServerLevel level)) return;
        Vec3 at = target.getBoundingBox().getCenter();
        if (SpellDamage.hit(level, caster, target, ASSAULT_BLAST)) caster.setLastHurtMob(target);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 8, 0.6, 0.5, 0.6, 0.12);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.65F, 1.55F);
    }

    // ───────────────────────── 风葬回廊 ─────────────────────────

    /** 前摇结束：进入风狂。层数与风暴场都从零开始，最多持续 48 秒。 */
    public static void beginBurialCorridor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        FRENZIES.remove(player.getUUID());
        removeFieldsOf(player.getUUID());
        NewSpellSystem.effect(player, NewSpellContent.WIND_FRENZY, FRENZY_MAX_TICKS);
        FRENZIES.put(player.getUUID(), new Frenzy(level, player.getUUID(), now));
        player.displayClientMessage(Component.translatable("message.dimensional_mana.wind_burial_start",
                FRENZY_MAX_TICKS / 20), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.35F, 0.85F);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.75F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.1F, 0.72F);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, player.getX(), player.getY() + 0.9, player.getZ(),
                2, 0, 0, 0, 0);
    }

    /** 风狂期间每刻维护：扣魔素、生成风暴场、动画；再按施法键由 {@link #releaseCrossSlash} 收尾。 */
    private static void tickFrenzy(ServerPlayer player, long now) {
        Frenzy frenzy = FRENZIES.get(player.getUUID());
        if (frenzy == null) return;
        if (frenzy.level != player.serverLevel() || !player.isAlive() || player.isSpectator()
                || NewSpellSystem.cannotCast(player)) {
            endFrenzy(player, "message.dimensional_mana.wind_burial_interrupted");
            return;
        }
        if (now - frenzy.born >= FRENZY_MAX_TICKS) {
            endFrenzy(player, "message.dimensional_mana.wind_burial_expired");
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, FRENZY_MANA_PER_TICK);
        if (!ManaCheats.hasMana(player, cost) || !ManaData.spendMana(player, FRENZY_MANA_PER_TICK)) {
            endFrenzy(player, "message.dimensional_mana.wind_burial_no_mana");
            return;
        }
        if (now % 4 == 0) SpellCasting.syncMana(player);
        ServerLevel level = frenzy.level;
        double half = player.getBbHeight() * 0.5;
        if (now % 2 == 0)
            level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + half, player.getZ(),
                    4, 0.7, player.getBbHeight() * 0.4, 0.7, 0.10);
        if (now % 10 == 0)
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + half, player.getZ(),
                    1, 0.8, 0.5, 0.8, 0.0);
        // 风葬层数每 10 刻同步一次，HUD 才能实时看到攒了多少层。
        if (now % 10 == 0) SpellSyncPayload.send(player);
        if (now < frenzy.nextFieldAt) return;
        frenzy.nextFieldAt = now + STORM_FIELD_INTERVAL;
        spawnStormField(player, frenzy, now);
    }

    /** 在身周四格内的空气方块里开一个风暴场；踩到同一片区域只刷新时长，不叠第二层。 */
    private static void spawnStormField(ServerPlayer player, Frenzy frenzy, long now) {
        ServerLevel level = frenzy.level;
        BlockPos origin = player.blockPosition();
        BlockPos picked = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = player.getRandom().nextInt(9) - 4;
            int dz = player.getRandom().nextInt(9) - 4;
            int dy = player.getRandom().nextInt(3) - 1;
            if (dx * dx + dz * dz > STORM_SPAWN_RADIUS * STORM_SPAWN_RADIUS) continue;
            BlockPos candidate = origin.offset(dx, dy, dz);
            if (!level.hasChunkAt(candidate) || !level.getWorldBorder().isWithinBounds(candidate)) continue;
            if (!level.getBlockState(candidate).isAir()) continue;
            picked = candidate;
            break;
        }
        if (picked == null) picked = origin;
        for (StormField field : FIELDS) {
            if (field.level != level || !field.owner.equals(frenzy.owner)) continue;
            if (field.pos.distSqr(picked) > STORM_REFRESH_RADIUS * STORM_REFRESH_RADIUS) continue;
            // 反复掠过同一片区域：只刷新时长与斩击节奏，不新增风暴场。
            field.refresh(now);
            return;
        }
        List<StormField> mine = new ArrayList<>();
        for (StormField field : FIELDS)
            if (field.level == level && field.owner.equals(frenzy.owner)) mine.add(field);
        // 同一区域最多三个风暴场，超过就先撤掉最早那个。
        while (mine.size() >= STORM_MAX_FIELDS) {
            FIELDS.remove(mine.remove(0));
        }
        StormField created = new StormField(level, frenzy.owner, picked, now);
        FIELDS.add(created);
        NewSpellSystem.visual(player, SpellDefinition.WIND_BURIAL_CORRIDOR, created.center, created.center, -1, 40);
    }

    /** 风暴场每秒斩一次场内的敌人：8 点风伤、缓慢 II 一秒，并给施法者叠一层风葬。 */
    private static void tickStormFields(ServerLevel level, long now) {
        Iterator<StormField> iterator = FIELDS.iterator();
        while (iterator.hasNext()) {
            StormField field = iterator.next();
            if (field.level != level) continue;
            if (now > field.until) { iterator.remove(); continue; }
            if (now < field.nextSlash) continue;
            field.nextSlash = now + STORM_SLASH_INTERVAL;
            Entity ownerEntity = level.getEntity(field.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive()) {
                iterator.remove();
                continue;
            }
            Frenzy frenzy = FRENZIES.get(field.owner);
            // 风暴场本体每 2 秒刷新一次视觉，覆盖它 8 秒的寿命。
            if (now % 40 == 0)
                NewSpellSystem.visual(owner, SpellDefinition.WIND_BURIAL_CORRIDOR, field.center, field.center, -1, 40);
            List<LivingEntity> victims = NewSpellSystem.enemies(owner, field.center, STORM_SLASH_RADIUS);
            if (!victims.isEmpty())
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, field.center.x, field.center.y, field.center.z,
                        2, 1.2, 0.4, 1.2, 0.0);
            for (LivingEntity victim : victims) {
                NewSpellSystem.damage(owner, victim, STORM_SLASH_DAMAGE, ElementType.WIND);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, true, true));
                if (frenzy != null) frenzy.addStack();
                Vec3 at = victim.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 3, 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    /**
     * 再按一次施法键：向后瞬退三格，朝准心挥出风十字斩，然后把风葬层数当成总斩击次数
     * 分摊给被斩中的敌人。冷却从这一刻开始计算。
     *
     * @return 玩家当前是否正处于风狂中（是则这次按键被消耗掉）
     */
    public static boolean releaseCrossSlash(ServerPlayer player) {
        Frenzy frenzy = FRENZIES.remove(player.getUUID());
        if (frenzy == null) return false;
        ServerLevel level = frenzy.level;
        long now = level.getGameTime();
        player.removeEffect(NewSpellContent.WIND_FRENZY);
        backstep(player, frenzy);
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        Vec3 reach = eye.add(dir.normalize().scale(CROSS_RANGE));
        Vec3 end = NewSpellSystem.blockEnd(player, eye, reach);
        List<LivingEntity> hits = crossTargets(player, eye, end, dir);
        for (LivingEntity victim : hits) {
            NewSpellSystem.damage(player, victim, CROSS_DAMAGE, ElementType.WIND);
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 4, 0.9, 0.9, 0.9, 0.0);
        }
        int broken = breakAlongSlash(level, player, eye, end);
        NewSpellSystem.visual(player, SpellDefinition.WIND_BURIAL_CORRIDOR, eye, end, -1, 24);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.75F);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.25F, 0.85F);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.35F);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        if (!hits.isEmpty() && frenzy.stacks > 0) {
            List<UUID> ids = new ArrayList<>();
            for (LivingEntity victim : hits) ids.add(victim.getUUID());
            BURIALS.add(new Burial(level, player.getUUID(), List.copyOf(ids), now + BURIAL_SLASH_INTERVAL,
                    Math.min(BURIAL_MAX_STACKS, frenzy.stacks)));
        }
        player.displayClientMessage(Component.translatable("message.dimensional_mana.wind_burial_cross",
                hits.size(), frenzy.stacks, broken), true);
        SpellCasting.startCooldown(player, SpellDefinition.WIND_BURIAL_CORRIDOR);
        SpellCasting.syncMana(player);
        SpellSyncPayload.send(player);
        return true;
    }

    /** 向后瞬退三格，遇到方块就停在撞墙前，避免瞬移到墙里。 */
    private static void backstep(ServerPlayer player, Frenzy frenzy) {
        ServerLevel level = frenzy.level;
        Vec3 dir = player.getLookAngle().multiply(-1, 0, -1);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 start = player.position();
        Vec3 last = start;
        for (double step = 0.2; step <= CROSS_BACKSTEP + 0.01; step += 0.2) {
            Vec3 next = start.add(dir.scale(step));
            if (!level.hasChunkAt(BlockPos.containing(next)) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(next))) break;
            if (!level.noCollision(player, player.getBoundingBox().move(next.subtract(start)))) break;
            last = next;
        }
        if (last.distanceToSqr(start) < 0.01) return;
        level.sendParticles(ParticleTypes.GUST, start.x, start.y + player.getBbHeight() * 0.5, start.z,
                10, 0.4, 0.5, 0.4, 0.10);
        level.sendParticles(ParticleTypes.GUST, last.x, last.y + player.getBbHeight() * 0.5, last.z,
                10, 0.4, 0.5, 0.4, 0.10);
        player.teleportTo(last.x, last.y, last.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    /** 风十字斩的命中范围：射程一条线、半径五格，身后的目标不算。 */
    private static List<LivingEntity> crossTargets(ServerPlayer player, Vec3 start, Vec3 end, Vec3 dir) {
        List<LivingEntity> hits = new ArrayList<>();
        AABB box = new AABB(start, end).inflate(CROSS_RADIUS + 1.0);
        for (LivingEntity victim : player.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> NewSpellSystem.enemy(player, entity))) {
            Vec3 center = victim.getBoundingBox().getCenter();
            if (center.subtract(start).dot(dir) < 0.0) continue;
            if (distanceToSegment(center, start, end) > CROSS_RADIUS + victim.getBbWidth() * 0.5) continue;
            hits.add(victim);
        }
        return hits;
    }

    /** 斩击沿线切掉最多六十四个“黑曜石硬度以下”的方块，先中心后向外扩一圈。 */
    private static int breakAlongSlash(ServerLevel level, ServerPlayer player, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.5) return 0;
        Vec3 step = delta.scale(1.0 / length);
        HashSet<BlockPos> visited = new HashSet<>();
        int broken = 0;
        for (double distance = 0.5; distance <= length + 0.001 && broken < CROSS_BREAK_LIMIT; distance += 0.5) {
            Vec3 center = start.add(step.scale(distance));
            // ring 0 是斩击线本身，ring 1 把缺口向两侧扩一格，64 个名额优先切出一条整齐的风刃隧道。
            for (int ring = 0; ring <= 1 && broken < CROSS_BREAK_LIMIT; ring++) {
                for (int dx = -ring; dx <= ring && broken < CROSS_BREAK_LIMIT; dx++) {
                    for (int dy = -ring; dy <= ring && broken < CROSS_BREAK_LIMIT; dy++) {
                        for (int dz = -ring; dz <= ring && broken < CROSS_BREAK_LIMIT; dz++) {
                            if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != ring) continue;
                            BlockPos pos = BlockPos.containing(center).offset(dx, dy, dz);
                            if (!visited.add(pos)) continue;
                            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                            BlockState state = level.getBlockState(pos);
                            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                            float hardness = state.getDestroySpeed(level, pos);
                            if (hardness < 0.0F || hardness >= CROSS_MAX_HARDNESS) continue;
                            if (!level.destroyBlock(pos, false, player)) continue;
                            broken++;
                            level.sendParticles(ParticleTypes.GUST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    4, 0.3, 0.3, 0.3, 0.08);
                        }
                    }
                }
            }
        }
        return broken;
    }

    /** 风葬斩击：每 2 刻斩一次，伤害 16，在风十字斩命中的敌人之间轮流分摊。 */
    private static void tickBurials(ServerLevel level, long now) {
        Iterator<Burial> iterator = BURIALS.iterator();
        while (iterator.hasNext()) {
            Burial burial = iterator.next();
            if (burial.level != level) continue;
            if (burial.remaining <= 0) { iterator.remove(); continue; }
            if (now < burial.nextAt) continue;
            burial.nextAt = now + BURIAL_SLASH_INTERVAL;
            Entity ownerEntity = level.getEntity(burial.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive()) { iterator.remove(); continue; }
            LivingEntity victim = pickBurialTarget(level, burial);
            if (victim == null) { iterator.remove(); continue; }
            burial.remaining--;
            float value = burialDamage(owner, victim);
            value = ElementReactionSystem.elementalDamage(owner, victim, ElementType.WIND, value);
            if (SpellDamage.hitElement(level, owner, victim, ElementType.WIND.id(), value)) owner.setLastHurtMob(victim);
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 2, 0.35, 0.5, 0.35, 0.0);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.3, 0.4, 0.3, 0.15);
            if (burial.remaining % 8 == 0)
                level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
    }

    /** 轮流挑一个还活着的分摊目标。 */
    private static LivingEntity pickBurialTarget(ServerLevel level, Burial burial) {
        for (int attempt = 0; attempt < burial.targets.size(); attempt++) {
            UUID id = burial.targets.get(burial.cursor % burial.targets.size());
            burial.cursor++;
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living && living.isAlive() && living.level() == level) return living;
        }
        return null;
    }

    /**
     * 风葬斩击的伤害：护甲本来就被法术伤害完全穿透，这里再让法术抗性与抗性提升只生效 45%，
     * 等于“无视 55% 全抗性”。
     */
    private static float burialDamage(ServerPlayer owner, LivingEntity victim) {
        double value = SpellAttributes.calculateUnresistedSpellDamage(owner, victim, BURIAL_SLASH_DAMAGE);
        double spellResistance = SpellAttributes.value(victim, SpellAttributes.SPELL_RESISTANCE, 0.0);
        value *= 1.0 - Math.min(0.95, spellResistance) * RESISTANCE_KEPT;
        MobEffectInstance resistance = victim.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null) {
            double reduction = Math.min(0.99, 0.2 * (resistance.getAmplifier() + 1));
            if (reduction > 0.0 && reduction < 1.0)
                value *= (1.0 - reduction * RESISTANCE_KEPT) / (1.0 - reduction);
        }
        return (float)Math.max(0.0, value);
    }

    /** 风狂被打断、超时或魔素耗尽：收起状态、撤掉风暴场并开始计算冷却。 */
    private static void endFrenzy(ServerPlayer player, String messageKey) {
        Frenzy frenzy = FRENZIES.remove(player.getUUID());
        if (frenzy == null) return;
        player.removeEffect(NewSpellContent.WIND_FRENZY);
        removeFieldsOf(player.getUUID());
        player.displayClientMessage(Component.translatable(messageKey), true);
        SpellCasting.startCooldown(player, SpellDefinition.WIND_BURIAL_CORRIDOR);
        SpellCasting.syncMana(player);
        SpellSyncPayload.send(player);
    }

    private static void removeFieldsOf(UUID owner) {
        FIELDS.removeIf(field -> field.owner.equals(owner));
    }

    /** 两个线段的距离：把点投影到风十字斩的射线上，超过一半长度就夹到端点。 */
    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double lengthSqr = delta.lengthSqr();
        if (lengthSqr < 1.0E-6) return point.distanceTo(start);
        double t = point.subtract(start).dot(delta) / lengthSqr;
        t = Math.clamp(t, 0.0, 1.0);
        return point.distanceTo(start.add(delta.scale(t)));
    }

    public static boolean inFrenzy(ServerPlayer player) {
        return FRENZIES.containsKey(player.getUUID());
    }

    public static int frenzyStacks(ServerPlayer player) {
        Frenzy frenzy = FRENZIES.get(player.getUUID());
        return frenzy == null ? 0 : frenzy.stacks;
    }

    public static int frenzyTicks(ServerPlayer player) {
        Frenzy frenzy = FRENZIES.get(player.getUUID());
        if (frenzy == null) return 0;
        return (int)Math.max(0L, FRENZY_MAX_TICKS - (player.level().getGameTime() - frenzy.born));
    }

    // ───────────────────────── 事件入口 ─────────────────────────

    public static void tickPlayer(ServerPlayer player, long now) {
        tickBurstStep(player, now);
        tickFrenzy(player, now);
    }

    public static void tickLevel(ServerLevel level, long now) {
        tickStormFields(level, now);
        tickBurials(level, now);
    }

    /** 下线、死亡或换维度：风狂与风袭的临时状态一并清掉，不让它们跨世界残留。 */
    public static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        if (FRENZIES.remove(id) != null) player.removeEffect(NewSpellContent.WIND_FRENZY);
        STEP_NEXT_HIT.remove(id);
        removeFieldsOf(id);
        BURIALS.removeIf(burial -> burial.owner.equals(id));
    }

    public static void clearAll() {
        FRENZIES.clear();
        STEP_NEXT_HIT.clear();
        FIELDS.clear();
        BURIALS.clear();
    }

    private WindSpells() { }
}
