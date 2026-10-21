package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 音系追加法术：次声与断魂鸣弦。
 *
 * <p>次声（中级·控制）：读条二点八秒后发出一圈次声波，半径八格内的生物受到 20 点音属性伤害、
 * 被眩晕八秒并获得音附着，另有 60% 概率额外获得虚弱与反胃十秒。次声波穿过方块，不做通视判定。</p>
 *
 * <p>断魂鸣弦（高级·伤害）：读条七秒后，施法者身边出现一颗缓慢旋转的震荡鸣球，持续三十秒。
 * 前摇期间按住 Alt + 左键可以额外投入 680 魔素追加一颗，最多三颗。每颗鸣球每 20 刻放出一圈
 * 高频声波（水平半径 16 格、上下各 24 格），追加的鸣球在主球之后每隔 4 刻依次跟上。
 * 声波无视方块、盾牌、护甲、抗性、药水减伤与金钟罩，只吃斗气减伤；被掠过的生物获得四刻“爆震”，
 * 期间每两刻受到一次 16 点音伤并附着音元素。鸣球本身碰到生物会造成 48 点音伤、击退五格并挂上爆震。</p>
 *
 * <p>鸣球持续期间施法者受到的伤害减少 8%；任何打向施法者的攻击都会沿着来向反射 50% 的伤害，
 * 反射射程八格，表现与音效都模仿监守者的声波攻击。</p>
 *
 * <p>“爆震”是强制状态：牛奶、蜂蜜、不死图腾与 {@code /effect clear} 都清不掉，只能等四刻自己走完；
 * 强制记录写在实体自身数据里，万一被别的模组整个抹掉，下一个游戏刻就按剩余时长补回来。</p>
 */
public final class SonicSpells {
    // ───────────────────────── 次声 ─────────────────────────
    private static final double INFRASOUND_RADIUS = 8.0;
    private static final float INFRASOUND_DAMAGE = 20.0F;
    /** 眩晕 8 秒。 */
    private static final int INFRASOUND_STUN_TICKS = 160;
    private static final float INFRASOUND_DEBUFF_CHANCE = 0.60F;
    private static final int INFRASOUND_DEBUFF_TICKS = 200;
    private static final int INFRASOUND_VISUAL_TICKS = 10;

    // ───────────────────────── 断魂鸣弦 ─────────────────────────
    /** 额外鸣球：680 魔素一颗，最多凑满三颗。 */
    public static final int ORB_MAX = 3;
    private static final double ORB_EXTRA_COST = 680.0;
    /** 鸣球持续三十秒。 */
    private static final int ORB_TICKS = 600;
    private static final int WAVE_INTERVAL = 20;
    /** 追加鸣球在上一颗之后每隔 4 刻跟进。 */
    private static final int ORB_WAVE_OFFSET = 4;
    private static final double WAVE_HORIZONTAL = 16.0;
    private static final double WAVE_VERTICAL = 24.0;
    private static final int WAVE_VISUAL_TICKS = 10;

    /** 爆震：四刻内每两刻结算一次 16 点音伤。 */
    private static final int BLAST_TICKS = 4;
    private static final int BLAST_INTERVAL = 2;
    private static final float BLAST_DAMAGE = 16.0F;

    private static final float TOUCH_DAMAGE = 48.0F;
    private static final int TOUCH_KNOCKBACK = 5;
    /** 同一颗鸣球对同一个目标十刻内只碰一次，避免大体型生物贴着球被每刻结算。 */
    private static final long TOUCH_COOLDOWN = 10L;

    /** 鸣球绕着施法者缓慢公转。 */
    private static final double ORBIT_RADIUS = 2.3;
    private static final double ORBIT_SPEED = 0.021;
    private static final double ORB_HIT_RADIUS = 0.95;

    private static final float DAMAGE_TAKEN = 0.92F;
    private static final float REFLECT_RATIO = 0.5F;
    private static final double REFLECT_RANGE = 8.0;
    private static final int REFLECT_VISUAL_TICKS = 8;

    private static final String BLAST_UNTIL = "DimensionalManaSonicBlastUntil";
    private static final String BLAST_NEXT = "DimensionalManaSonicBlastNext";
    private static final String BLAST_CASTER = "DimensionalManaSonicBlastCaster";

    /** 一颗正在绕着施法者旋转的鸣球；orbs 是同时存在的总颗数。 */
    private static final class SonicField {
        final ServerLevel level;
        final UUID owner;
        final int ownerEntityId;
        final long born;
        final long until;
        final int orbs;
        long nextWave;
        long nextVisual;
        double angle;

        SonicField(ServerLevel level, UUID owner, int ownerEntityId, long now, int orbs) {
            this.level = level;
            this.owner = owner;
            this.ownerEntityId = ownerEntityId;
            this.born = now;
            this.until = now + ORB_TICKS;
            this.orbs = orbs;
            this.nextWave = now + WAVE_INTERVAL;
            this.nextVisual = now;
            this.angle = now * ORBIT_SPEED;
        }
    }

    private record ScheduledWave(ServerLevel level, UUID owner, int orb, long at) { }
    private record PendingReflect(ServerLevel level, UUID caster, UUID target, float amount, long at) { }

    /** 读条期间已经追加的鸣球数量。 */
    private static final Map<UUID, Integer> EXTRAS = new HashMap<>();
    private static final Map<UUID, SonicField> FIELDS = new HashMap<>();
    private static final List<ScheduledWave> WAVES = new ArrayList<>();
    private static final List<PendingReflect> REFLECTS = new ArrayList<>();
    private static final Map<UUID, Long> TOUCH_NEXT = new HashMap<>();
    private static final Map<UUID, Long> ORB_NOTICE = new HashMap<>();

    // ───────────────────────── 次声 ─────────────────────────

    /** 读条结束：以施法者为中心放出半径八格的次声波。 */
    public static void beginInfrasound(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(INFRASOUND_RADIUS),
                entity -> NewSpellSystem.enemy(player, entity)
                        && entity.getBoundingBox().getCenter().distanceToSqr(center) <= INFRASOUND_RADIUS * INFRASOUND_RADIUS);
        int stunned = 0;
        for (LivingEntity victim : victims) {
            boolean aliveBefore = victim.isAlive();
            sonicDamage(player, victim, INFRASOUND_DAMAGE);
            if (!aliveBefore || !victim.isAlive()) continue;
            NewSpellSystem.control(player, victim, NewSpellContent.STUN, INFRASOUND_STUN_TICKS);
            stunned++;
            if (player.getRandom().nextFloat() < INFRASOUND_DEBUFF_CHANCE) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, INFRASOUND_DEBUFF_TICKS, 0, false, true, true));
                victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, INFRASOUND_DEBUFF_TICKS, 0, false, true, true));
            }
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.3, 0.4, 0.3, 0.05);
        }
        SpellVisualPayload.send(player, SpellDefinition.INFRASOUND, SpellVisualPayload.SONIC_WAVE,
                -1, center, center.add(0, INFRASOUND_RADIUS, 0), INFRASOUND_VISUAL_TICKS);
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 40,
                INFRASOUND_RADIUS * 0.45, 0.6, INFRASOUND_RADIUS * 0.45, 0.03);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.9F, 0.85F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.85F, 0.62F);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.infrasound_cast", (int)INFRASOUND_RADIUS,
                stunned, INFRASOUND_STUN_TICKS / 20), true);
        SpellSyncPayload.send(player);
    }

    // ───────────────────────── 断魂鸣弦 ─────────────────────────

    /** 读条结束：在施法者身边生成主鸣球与读条期间追加的鸣球。 */
    public static void beginSoulChord(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int extras = Math.clamp(EXTRAS.getOrDefault(player.getUUID(), 0), 0, ORB_MAX - 1);
        EXTRAS.remove(player.getUUID());
        SonicField old = FIELDS.remove(player.getUUID());
        if (old != null) sendEndVisual(old);
        SonicField field = new SonicField(level, player.getUUID(), player.getId(), now, 1 + extras);
        FIELDS.put(player.getUUID(), field);
        sendOrbVisual(field, true);
        Vec3 at = player.position().add(0, player.getBbHeight() * 0.7, 0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 38, 1.1, 0.9, 1.1, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.PLAYERS, 1.0F, 1.15F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.0F, 0.75F);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.soul_chord_cast", field.orbs, ORB_TICKS / 20), true);
        SpellSyncPayload.send(player);
    }

    /** 前摇期间已经追加了几颗鸣球，同步给客户端，按住 Alt + 左键时不用猜还能不能再补。 */
    public static int extras(ServerPlayer player) {
        if (!castingSoulChord(player)) return 0;
        return Math.clamp(EXTRAS.getOrDefault(player.getUUID(), 0), 0, ORB_MAX - 1);
    }

    /** 读条开始：上一次被中断的施法留下的追加数量不能算进这一次，前摇从零开始计。 */
    public static void beginCharge(ServerPlayer player) {
        if (player != null) EXTRAS.remove(player.getUUID());
    }

    /** 前摇期间 Alt + 左键：追加一颗鸣球，消耗 680 魔素。 */
    public static void addOrb(ServerPlayer player) {
        if (!castingSoulChord(player)) return;
        int extras = EXTRAS.getOrDefault(player.getUUID(), 0);
        if (extras >= ORB_MAX - 1) {
            // 按住不放时客户端会每隔几刻再问一次，提示按十刻节流，免得刷屏。
            notifyOnce(player, ORB_NOTICE, 10, "message.dimensional_mana.soul_chord_full", ORB_MAX);
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, ORB_EXTRA_COST);
        if (!ManaCheats.hasMana(player, cost)) {
            notifyMana(player, (int)Math.ceil(cost));
            return;
        }
        if (!ManaData.spendMana(player, ORB_EXTRA_COST)) return;
        SpellCasting.syncMana(player);
        EXTRAS.put(player.getUUID(), extras + 1);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.soul_chord_add", extras + 1, ORB_MAX - 1), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.8F, 0.95F + extras * 0.07F);
        player.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS,
                0.55F, 1.35F + extras * 0.05F);
        SpellSyncPayload.send(player);
    }

    /** 判断一次伤害是不是高频声波。 */
    public static boolean isSonic(DamageSource source) {
        return SpellDamage.isSonic(source);
    }

    private static void notifyMana(ServerPlayer player, int cost) {
        notifyOnce(player, ORB_NOTICE, 10, "message.dimensional_mana.soul_chord_no_mana", cost);
    }

    /** 同一类提示十刻内只弹一次：按住 Alt + 左键时会连续发请求，不能每刻刷一条。 */
    private static void notifyOnce(ServerPlayer player, Map<UUID, Long> throttles, int ticks, String key, int value) {
        long now = player.level().getGameTime();
        Long last = throttles.get(player.getUUID());
        if (last != null && now - last < ticks) return;
        throttles.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(key, value), true);
    }

    private static boolean castingSoulChord(ServerPlayer player) {
        SpellCasting.CastSnapshot cast = SpellCasting.snapshot(player);
        return SpellDefinition.SOUL_CHORD.id().equals(cast.spellId()) && cast.totalTicks() > 0;
    }

    // ───────────────────────── 声波 ─────────────────────────

    /** 所有音属性伤害的统一出口：吃法术强度与音附着，但按声波规则穿透防护。 */
    private static boolean sonicDamage(LivingEntity caster, LivingEntity target, float amount) {
        if (caster == null || target == null || target.level().isClientSide || !target.isAlive()) return false;
        if (!(caster.level() instanceof ServerLevel level)) return false;
        float value = SpellAttributes.calculateSpellDamage(caster, target, amount);
        value = ElementReactionSystem.elementalDamage(caster, target, ElementType.SOUND, value);
        if (value <= 0.0F) return false;
        boolean landed = SpellDamage.hitSonic(level, caster, target, value);
        if (landed) caster.setLastHurtMob(target);
        if (target.hasEffect(NewSpellContent.POLARITY_DISORDER)) target.invulnerableTime = 0;
        return landed;
    }

    /**
     * 反射波的伤害：直接按“已结算伤害 ×50%”落地，不再乘一次法术强度，
     * 否则反射会比它反射的那一击还高。
     */
    private static boolean sonicReflectDamage(LivingEntity caster, LivingEntity target, float amount) {
        if (caster == null || target == null || target.level().isClientSide || !target.isAlive()) return false;
        if (!(caster.level() instanceof ServerLevel level)) return false;
        float value = ElementReactionSystem.elementalDamage(caster, target, ElementType.SOUND, amount);
        if (value <= 0.0F) return false;
        boolean landed = SpellDamage.hitSonic(level, caster, target, value);
        if (landed) caster.setLastHurtMob(target);
        if (target.hasEffect(NewSpellContent.POLARITY_DISORDER)) target.invulnerableTime = 0;
        return landed;
    }

    /** 挂上四刻爆震：强制状态，不叠加，重复触发刷新时长并重置结算计时器。 */
    private static void applyBlast(LivingEntity caster, LivingEntity victim) {
        if (victim == null || victim.level().isClientSide || !victim.isAlive()) return;
        long now = victim.level().getGameTime();
        victim.addEffect(new MobEffectInstance(NewSpellContent.SONIC_BLAST, BLAST_TICKS, 0, false, true, true));
        CompoundTag data = victim.getPersistentData();
        data.putLong(BLAST_UNTIL, now + BLAST_TICKS);
        data.putLong(BLAST_NEXT, now);
        if (caster != null && caster != victim) data.putUUID(BLAST_CASTER, caster.getUUID());
    }

    /** 爆震是否仍在强制期内：移除事件据此否决牛奶、{@code /effect clear} 一类的清除。 */
    public static boolean blastForced(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        return entity.getPersistentData().getLong(BLAST_UNTIL) > entity.level().getGameTime();
    }

    /** 爆震维护：四刻内每两刻一次 16 点音伤，并刷新音附着。 */
    private static void tickBlast(LivingEntity entity, CompoundTag data, long now) {
        long until = data.getLong(BLAST_UNTIL);
        if (until <= 0) return;
        if (!entity.isAlive() || now >= until) {
            // 死亡或正常到期：撤掉强制记录，效果交给原版计时器自己收尾。
            data.remove(BLAST_UNTIL);
            data.remove(BLAST_NEXT);
            data.remove(BLAST_CASTER);
            return;
        }
        int remain = (int)Math.min(Integer.MAX_VALUE, until - now);
        MobEffectInstance blast = entity.getEffect(NewSpellContent.SONIC_BLAST);
        // 强制状态：牛奶与蜂蜜根本进不了移除流程；万一被别的模组整个抹掉，就地按剩余时长补回来。
        // 效果只剩两三刻时也补一次，避免它在最后一次结算前先到期。
        if (blast == null || blast.getDuration() + 2 < remain) {
            entity.addEffect(new MobEffectInstance(NewSpellContent.SONIC_BLAST, remain, 0, false, true, true));
        }
        long next = data.getLong(BLAST_NEXT);
        if (next <= 0) next = now;
        if (now < next) return;
        data.putLong(BLAST_NEXT, now + BLAST_INTERVAL);
        UUID casterId = data.hasUUID(BLAST_CASTER) ? data.getUUID(BLAST_CASTER) : null;
        LivingEntity caster = entity;
        if (casterId != null && entity.level() instanceof ServerLevel level) {
            Entity source = level.getEntity(casterId);
            if (source instanceof LivingEntity living) caster = living;
        }
        sonicDamage(caster, entity, BLAST_DAMAGE);
        if (entity.level() instanceof ServerLevel level) {
            Vec3 at = entity.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 5, 0.25, 0.35, 0.25, 0.04);
            level.playSound(null, entity.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT,
                    SoundSource.PLAYERS, 0.35F, 1.55F);
        }
    }

    /** 一颗鸣球放出一圈高频声波：圆柱范围内所有敌人被掠过并挂上四刻爆震。 */
    private static void emitWave(SonicField field, Vec3 center, LivingEntity caster) {
        ServerLevel level = field.level;
        AABB box = new AABB(center.add(-WAVE_HORIZONTAL, -WAVE_VERTICAL, -WAVE_HORIZONTAL),
                center.add(WAVE_HORIZONTAL, WAVE_VERTICAL, WAVE_HORIZONTAL));
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box, entity -> {
            if (!NewSpellSystem.enemy(caster, entity)) return false;
            Vec3 at = entity.getBoundingBox().getCenter();
            double dx = at.x - center.x, dz = at.z - center.z;
            return dx * dx + dz * dz <= WAVE_HORIZONTAL * WAVE_HORIZONTAL
                    && Math.abs(at.y - center.y) <= WAVE_VERTICAL;
        });
        for (LivingEntity victim : victims) {
            // 声波本身不直接掉血：被掠过的目标统一挂上四刻爆震，由爆震每两刻结算 16 点音伤。
            applyBlast(caster, victim);
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.25, 0.3, 0.25, 0.04);
        }
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.SOUL_CHORD.id(),
                SpellVisualPayload.SONIC_WAVE, -1, -1, center,
                center.add(0, WAVE_HORIZONTAL, 0), WAVE_VISUAL_TICKS);
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z,
                WAVE_HORIZONTAL + 48.0, payload);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.55F, 1.35F);
    }

    // ───────────────────────── 反射 ─────────────────────────

    /**
     * 鸣球存在时受到的伤害：先减 8%，再把 50% 沿着攻击来向反射回去。
     *
     * <p>返回调整后的伤害，由 {@link NewSpellSystem#incoming} 继续走斗气等其他结算。
     * 反射伤害延后一刻落地，避免在原版受击事件里嵌套一次 hurt。</p>
     */
    public static float onIncomingDamage(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof ServerPlayer player) || amount <= 0.0F) return amount;
        SonicField field = FIELDS.get(player.getUUID());
        if (field == null || field.level != player.serverLevel()) return amount;
        if (source == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof LivingEntity attacker && attacker != player && attacker.isAlive()
                && attacker.distanceToSqr(player) <= REFLECT_RANGE * REFLECT_RANGE) {
            long now = player.level().getGameTime();
            REFLECTS.add(new PendingReflect(player.serverLevel(), player.getUUID(), attacker.getUUID(),
                    amount * REFLECT_RATIO, now + 1));
        }
        return amount * DAMAGE_TAKEN;
    }

    /** 反射波延后一刻落地：目标还在八格内就吃 50% 伤害，视觉与音效模仿监守者声波。 */
    private static void tickReflects(ServerLevel level, long now) {
        Iterator<PendingReflect> iterator = REFLECTS.iterator();
        while (iterator.hasNext()) {
            PendingReflect pending = iterator.next();
            if (pending.level() != level || pending.at() > now) continue;
            iterator.remove();
            if (!(level.getEntity(pending.caster()) instanceof ServerPlayer caster) || !caster.isAlive()) continue;
            if (!(level.getEntity(pending.target()) instanceof LivingEntity target) || !target.isAlive()) continue;
            if (target.distanceToSqr(caster) > REFLECT_RANGE * REFLECT_RANGE) continue;
            sonicReflectDamage(caster, target, pending.amount());
            Vec3 start = caster.getEyePosition();
            Vec3 aim = target.getBoundingBox().getCenter();
            Vec3 direction = aim.subtract(start);
            Vec3 end = direction.length() > REFLECT_RANGE
                    ? start.add(direction.normalize().scale(REFLECT_RANGE)) : aim;
            SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.SOUL_CHORD.id(),
                    SpellVisualPayload.SONIC_REFLECT, caster.getId(), -1, start, end, REFLECT_VISUAL_TICKS);
            PacketDistributor.sendToPlayersNear(level, null, start.x, start.y, start.z, 64.0, payload);
            level.playSound(null, start.x, start.y, start.z, SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, 0.75F, 1.15F);
            level.sendParticles(ParticleTypes.SONIC_BOOM, start.x, start.y, start.z, 1, 0, 0, 0, 0);
        }
    }

    // ───────────────────────── 鸣球 ─────────────────────────

    /** 世界刻：旋转鸣球、按轮次放声波、检查触碰、刷新视觉。 */
    public static void tickLevel(ServerLevel level, long now) {
        for (SonicField field : new ArrayList<>(FIELDS.values())) {
            if (field.level != level) continue;
            Entity ownerEntity = level.getEntity(field.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive()
                    || owner.serverLevel() != level || now >= field.until) {
                endField(field);
                continue;
            }
            field.angle += ORBIT_SPEED;
            if (now >= field.nextWave) {
                // 以当前刻为基准重排下一轮，服务器卡顿后不会攒出一串补发的声波。
                field.nextWave = now + WAVE_INTERVAL;
                for (int i = 0; i < field.orbs; i++)
                    WAVES.add(new ScheduledWave(level, field.owner, i, now + (long)ORB_WAVE_OFFSET * i));
            }
            for (int i = 0; i < field.orbs; i++) touchOrb(field, owner, i, now);
            if (now >= field.nextVisual) {
                field.nextVisual = now + 20;
                sendOrbVisual(field, false);
            }
        }
        Iterator<ScheduledWave> waves = WAVES.iterator();
        while (waves.hasNext()) {
            ScheduledWave wave = waves.next();
            if (wave.level() != level || wave.at() > now) continue;
            waves.remove();
            SonicField field = FIELDS.get(wave.owner());
            if (field == null || field.level != level || wave.orb() >= field.orbs) continue;
            if (!(level.getEntity(field.owner) instanceof LivingEntity caster) || !caster.isAlive()) continue;
            emitWave(field, orbPosition(field, wave.orb()), caster);
        }
        tickReflects(level, now);
        if (now % 200 == 0) {
            // 只清理已经不在这个世界里的玩家（下线、换维度）：读条期间攒下的追加数量一律不动，
            // 否则读条中途被“清理”一次，读条结束时就只剩主球一颗了。
            EXTRAS.keySet().removeIf(id -> !(level.getEntity(id) instanceof ServerPlayer));
            TOUCH_NEXT.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null);
            ORB_NOTICE.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null);
        }
    }

    /** 鸣球碰到生物：48 点音伤、击退五格并挂上爆震。 */
    private static void touchOrb(SonicField field, ServerPlayer owner, int index, long now) {
        ServerLevel level = field.level;
        Vec3 orb = orbPosition(field, index);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(orb, orb).inflate(ORB_HIT_RADIUS + 1.0),
                entity -> NewSpellSystem.enemy(owner, entity)
                        && entity.getBoundingBox().distanceToSqr(orb) <= ORB_HIT_RADIUS * ORB_HIT_RADIUS);
        for (LivingEntity victim : victims) {
            long next = TOUCH_NEXT.getOrDefault(victim.getUUID(), 0L);
            if (now < next) continue;
            TOUCH_NEXT.put(victim.getUUID(), now + TOUCH_COOLDOWN);
            sonicDamage(owner, victim, TOUCH_DAMAGE);
            if (!victim.isAlive()) continue;
            NewSpellSystem.knock(orb, victim, TOUCH_KNOCKBACK);
            applyBlast(owner, victim);
            level.sendParticles(ParticleTypes.SONIC_BOOM, orb.x, orb.y, orb.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, orb.x, orb.y, orb.z, 10, 0.35, 0.35, 0.35, 0.08);
            level.playSound(null, orb.x, orb.y, orb.z, SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, 0.45F, 1.55F);
            SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.SOUL_CHORD.id(),
                    SpellVisualPayload.SONIC_TOUCH, -1, -1, orb, orb, 6);
            PacketDistributor.sendToPlayersNear(level, null, orb.x, orb.y, orb.z, 48.0, payload);
        }
    }

    /** 第 index 颗鸣球此刻的位置：绕施法者缓慢公转，并做小幅上下浮动。 */
    public static Vec3 orbPosition(SonicField field, int index) {
        Entity entity = field.level.getEntity(field.owner);
        Vec3 center = entity == null ? Vec3.ZERO
                : entity.position().add(0, entity.getBbHeight() * 0.65, 0);
        double angle = field.angle + index * (Math.PI * 2.0 / 3.0);
        double bob = Math.sin(field.angle * 1.7 + index * 1.4) * 0.18;
        return center.add(Math.cos(angle) * ORBIT_RADIUS, bob, Math.sin(angle) * ORBIT_RADIUS);
    }

    private static void sendOrbVisual(SonicField field, boolean force) {
        Entity owner = field.level.getEntity(field.owner);
        Vec3 start = owner == null ? Vec3.ZERO : owner.position();
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.SOUL_CHORD.id(),
                SpellVisualPayload.SONIC_ORB, field.ownerEntityId, -1, start,
                start.add(0, field.orbs, 0), ORB_TICKS);
        PacketDistributor.sendToPlayersNear(field.level, null, start.x, start.y, start.z, 96.0, payload);
    }

    private static void sendEndVisual(SonicField field) {
        Entity owner = field.level.getEntity(field.owner);
        Vec3 at = owner == null ? Vec3.ZERO : owner.position();
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.SOUL_CHORD.id(),
                SpellVisualPayload.SONIC_ORB_END, field.ownerEntityId, -1, at, at, 1);
        PacketDistributor.sendToPlayersNear(field.level, null, at.x, at.y, at.z, 96.0, payload);
    }

    private static void endField(SonicField field) {
        if (FIELDS.get(field.owner) == field) FIELDS.remove(field.owner);
        WAVES.removeIf(wave -> wave.owner().equals(field.owner) && wave.level() == field.level);
        sendEndVisual(field);
        if (field.level.getEntity(field.owner) instanceof ServerPlayer owner) {
            owner.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.soul_chord_end"), true);
        }
    }

    // ───────────────────────── 事件入口 ─────────────────────────

    /** 每实体刻：维护爆震的两跳伤害。 */
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        tickBlast(entity, entity.getPersistentData(), entity.level().getGameTime());
    }

    /** 下线、死亡或换维度：鸣球与待反射的波一起收起。 */
    public static void clear(ServerPlayer player) {
        EXTRAS.remove(player.getUUID());
        ORB_NOTICE.remove(player.getUUID());
        SonicField field = FIELDS.remove(player.getUUID());
        if (field != null) sendEndVisual(field);
        WAVES.removeIf(wave -> wave.owner().equals(player.getUUID()));
        REFLECTS.removeIf(pending -> pending.caster().equals(player.getUUID()));
    }

    public static void clearAll() {
        EXTRAS.clear();
        FIELDS.clear();
        WAVES.clear();
        REFLECTS.clear();
        TOUCH_NEXT.clear();
        ORB_NOTICE.clear();
    }

    private SonicSpells() { }
}
