package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 毒系追加法术：猛毒攻心与鸩羽千夜。
 *
 * <p>猛毒攻心（中级·伤害）：读条三秒后先向前冲刺两格，再射出一根最多贯穿 7 个敌人的毒刺，
 * 射程 70 格。命中的敌人受到 14 点毒属性伤害与毒附着，同时挂上持续 18 秒的“猛毒”：
 * 期间目标只要还在中毒，中毒伤害就翻倍，中毒的结算间隔再缩短 4 刻，
 * 而且每一次中毒伤害都有 30% 概率把中毒时长延长 1 秒。</p>
 *
 * <p>鸩羽千夜（高级·削弱）：读条五点五秒后，在施法位置放出一团毒雾，基础持续 30 秒。
 * 毒雾从第 4 秒开始每秒向外扩散 2 格，每次扩散消耗 80 魔素并把毒雾持续时间延长 1 秒，
 * 最多扩散 20 秒，扩散期间再按一次施法键可以提前停手。雾中的敌人每 10 刻被挂上一次 30 秒中毒；
 * 已经有中毒的敌人再吃一次，就刷新中毒时长并叠加 1 层“鸩毒”。毒雾被方块挡住，墙后的人吃不到。
 * 冷却在毒雾彻底消散之后才开始计算。</p>
 *
 * <p>鸩毒：持续 60 秒，每层让目标的毒易伤 +4%（加算）、移速 -1%，最多 50 层。
 * 期间目标任何方式的回血速率减半；每次受到毒属性伤害都有 15% 概率永久扣掉 0.55% 生命上限
 * （鸩毒结束时上限恢复，但已经被压掉的血量不会补回）。受到光属性伤害时额外承受 30% 毒伤、
 * 不再附着毒，每 10 刻最多触发一次。</p>
 *
 * <p>鸩羽千夜给出的中毒与鸩毒都是强制状态：牛奶、蜂蜜、不死图腾、{@code /effect clear}
 * 以及别的模组的清除都不放行，只能等它自己到期；真被强行抹掉时，下一个游戏刻按原层数补回。</p>
 */
public final class PoisonSpells {
    // ───────────────────────── 猛毒攻心 ─────────────────────────
    private static final int HEART_DASH_BLOCKS = 2;
    private static final double NEEDLE_RANGE = 70.0;
    /** 70 格 / 秒 → 每刻 3.5 格。 */
    private static final double NEEDLE_SPEED = 70.0 / 20.0;
    private static final int NEEDLE_PIERCE = 7;
    private static final float NEEDLE_DAMAGE = 14.0F;
    private static final int NEEDLE_MAX_AGE = 40;
    private static final double NEEDLE_HIT_RADIUS = 0.55;
    /** 猛毒持续 18 秒。 */
    private static final int FIERCE_TICKS = 360;
    /** 猛毒让中毒的结算间隔缩短 4 刻。 */
    private static final int FIERCE_INTERVAL_SHORTEN = 4;
    /** 猛毒让中毒伤害翻倍：每次额外结算与中毒同值的伤害。 */
    private static final float FIERCE_ECHO_DAMAGE = 1.0F;
    /** 每次中毒伤害有 30% 概率把中毒延长 1 秒。 */
    private static final float FIERCE_EXTEND_CHANCE = 0.30F;
    private static final int FIERCE_EXTEND_TICKS = 20;

    // ───────────────────────── 鸩羽千夜 ─────────────────────────
    /** 毒雾基础存在 30 秒。 */
    private static final int MIST_BASE_TICKS = 600;
    /** 第 4 秒开始扩散。 */
    private static final int MIST_SPREAD_DELAY = 80;
    private static final int MIST_SPREAD_INTERVAL = 20;
    private static final int MIST_MAX_STEPS = 20;
    private static final double MIST_SPREAD_COST = 80.0;
    private static final double MIST_BASE_RADIUS = 3.0;
    private static final double MIST_RADIUS_STEP = 2.0;
    private static final int MIST_POISON_INTERVAL = 10;
    /** 雾中的敌人每次被附加 30 秒中毒。 */
    private static final int MIST_POISON_TICKS = 600;
    private static final int MIST_VISUAL_REFRESH = 20;
    private static final int MIST_VISUAL_TICKS = 40;

    // ───────────────────────── 鸩毒 ─────────────────────────
    private static final int ZHEN_TICKS = 1200;
    private static final int ZHEN_MAX_LAYERS = 50;
    private static final float ZHEN_POISON_VULNERABILITY = 0.04F;
    private static final double ZHEN_SLOW_PER_LAYER = 0.01;
    /** 受到毒属性伤害时 15% 概率扣掉 0.55% 生命上限。 */
    private static final float ZHEN_HEALTH_CHANCE = 0.15F;
    private static final float ZHEN_HEALTH_STEP = 0.0055F;
    /** 生命上限最多被压到原值的 10%，避免出现上限归零之类的怪状态。 */
    private static final double ZHEN_HEALTH_CAP = 0.90;
    /** 受到光属性伤害时额外造成 30% 毒伤，每 10 刻最多一次。 */
    private static final float ZHEN_LIGHT_RATIO = 0.30F;
    private static final long ZHEN_LIGHT_COOLDOWN = 10L;

    // 强制状态在实体持久数据里的键：到期刻 + 等级/层数。
    private static final String FORCED_POISON_UNTIL = "DimensionalManaForcedPoisonUntil";
    private static final String FORCED_POISON_AMP = "DimensionalManaForcedPoisonAmp";
    private static final String FIERCE_UNTIL = "DimensionalManaFiercePoisonUntil";
    private static final String FIERCE_CASTER = "DimensionalManaFiercePoisonCaster";
    private static final String ZHEN_UNTIL = "DimensionalManaZhenPoisonUntil";
    private static final String ZHEN_LAYERS = "DimensionalManaZhenPoisonLayers";
    private static final String ZHEN_HEALTH_STACKS = "DimensionalManaZhenHealthStacks";

    private static final ResourceLocation ZHEN_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "zhen_poison_health_loss");
    private static final ResourceLocation ZHEN_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "zhen_poison_slow");

    private static final List<Needle> NEEDLES = new ArrayList<>();
    private static final Map<UUID, Mist> MISTS = new HashMap<>();
    private static final List<PendingPoisonHit> PENDING = new ArrayList<>();
    private static final List<PendingHealthLoss> PENDING_LOSS = new ArrayList<>();
    private static final Map<UUID, Long> FIERCE_NEXT = new HashMap<>();
    private static final Map<UUID, Long> LIGHT_TRIGGER = new HashMap<>();
    private static final Map<UUID, Long> SPREAD_NOTICE = new HashMap<>();

    /** 一根正在飞的毒刺：命中的敌人各自只结算一次。 */
    private static final class Needle {
        final ServerLevel level;
        final UUID owner;
        final Vec3 direction;
        final Set<UUID> hits = new HashSet<>();
        Vec3 position;
        double traveled;
        int age;

        Needle(ServerLevel level, UUID owner, Vec3 position, Vec3 direction) {
            this.level = level;
            this.owner = owner;
            this.position = position;
            this.direction = direction;
        }
    }

    /** 一团正在持续的毒雾：中心固定，半径随扩散步数增长。 */
    private static final class Mist {
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final int ownerEntityId;
        final long born;
        long until;
        long nextSpreadAt;
        long nextPoisonAt;
        long lastVisualAt;
        int steps;
        boolean stopped;

        Mist(ServerLevel level, UUID owner, int ownerEntityId, Vec3 center, long now) {
            this.level = level;
            this.owner = owner;
            this.ownerEntityId = ownerEntityId;
            this.center = center;
            this.born = now;
            this.until = now + MIST_BASE_TICKS;
            this.nextSpreadAt = now + MIST_SPREAD_DELAY;
            this.nextPoisonAt = now;
            this.lastVisualAt = now;
        }

        double radius() {
            return MIST_BASE_RADIUS + MIST_RADIUS_STEP * steps;
        }
    }

    /** 光伤联动延后一刻结算，避免在元素反应的伤害计算里嵌套 hurt。 */
    private record PendingPoisonHit(ServerLevel level, UUID caster, UUID target, float amount, long at) { }
    /** 中毒伤害扣生命上限延后一刻掷骰，不在伤害计算途中改动属性。 */
    private record PendingHealthLoss(ServerLevel level, UUID target, long at) { }

    // ───────────────────────── 猛毒攻心 ─────────────────────────

    /** 读条结束：向前冲刺两格，再从眼睛位置射出毒刺。 */
    public static void beginVenomHeart(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        Vec3 dashEnd = dash(player, flat.normalize(), HEART_DASH_BLOCKS);
        Vec3 start = player.getEyePosition();
        // 视觉与命中用同一条航线：先按方块算出真正的终点，客户端照着这条线飞。
        Vec3 visualEnd = NewSpellSystem.blockEnd(player, start, start.add(look.normalize().scale(NEEDLE_RANGE)));
        double distance = start.distanceTo(visualEnd);
        int flight = Math.max(2, (int)Math.ceil(distance / NEEDLE_SPEED));
        NEEDLES.add(new Needle(level, player.getUUID(), start, look.normalize()));
        sendNeedleVisual(player, start, visualEnd, flight);
        level.sendParticles(ParticleTypes.EFFECT, start.x, start.y, start.z, 14, 0.22, 0.22, 0.22, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.9F, 0.72F);
        level.playSound(null, player.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 0.55F, 1.55F);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.venom_heart_cast", (int)NEEDLE_RANGE, NEEDLE_PIERCE), true);
        SpellSyncPayload.send(player);
        // 冲刺的破风声与残影：落点离原位超过半格才算真的冲出去了。
        if (dashEnd.distanceToSqr(player.position()) < 0.25)
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.8, player.getZ(),
                    1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.85F, 0.75F);
        level.sendParticles(ParticleTypes.SNEEZE, dashEnd.x, dashEnd.y + 0.6, dashEnd.z, 12, 0.3, 0.5, 0.3, 0.02);
    }

    /** 向指定方向冲刺；撞墙时只走到最后一段安全位置。 */
    private static Vec3 dash(ServerPlayer player, Vec3 direction, int blocks) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position();
        Vec3 last = start;
        for (int step = 1; step <= blocks * 5; step++) {
            Vec3 next = start.add(direction.scale(step / 5.0));
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(next))
                    || !level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(next))
                    || !level.noCollision(player, player.getBoundingBox().move(next.subtract(start)))) break;
            last = next;
        }
        if (last.distanceToSqr(start) < 0.01) return start;
        player.teleportTo(last.x, last.y, last.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        return last;
    }

    /** 毒刺每刻前进一段：段内碰到的敌人逐个结算，最多贯穿 7 个。 */
    private static void tickNeedles(ServerLevel level) {
        Iterator<Needle> iterator = NEEDLES.iterator();
        while (iterator.hasNext()) {
            Needle needle = iterator.next();
            if (needle.level != level) continue;
            Entity ownerEntity = level.getEntity(needle.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive() || owner.serverLevel() != level) {
                iterator.remove();
                continue;
            }
            if (++needle.age > NEEDLE_MAX_AGE) { iterator.remove(); continue; }
            Vec3 start = needle.position;
            Vec3 intended = start.add(needle.direction.scale(NEEDLE_SPEED));
            Vec3 end = NewSpellSystem.blockEnd(owner, start, intended);
            boolean blocked = end.distanceToSqr(intended) > 0.0001;
            if (needleHits(owner, needle, start, end)) { iterator.remove(); continue; }
            needle.position = end;
            needle.traveled += start.distanceTo(end);
            if (blocked || needle.traveled >= NEEDLE_RANGE - 0.01 || needle.hits.size() >= NEEDLE_PIERCE) {
                if (blocked) {
                    level.sendParticles(ParticleTypes.EFFECT, end.x, end.y, end.z, 8, 0.18, 0.18, 0.18, 0.03);
                    level.playSound(null, net.minecraft.core.BlockPos.containing(end), SoundEvents.SLIME_BLOCK_STEP,
                            SoundSource.PLAYERS, 0.45F, 1.45F);
                }
                iterator.remove();
            }
        }
    }

    /** 结算这一段航线上的命中；返回 true 表示毒刺已经贯穿满 7 个目标。 */
    private static boolean needleHits(ServerPlayer owner, Needle needle, Vec3 start, Vec3 end) {
        List<LivingEntity> victims = needle.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(NEEDLE_HIT_RADIUS + 1.2),
                entity -> NewSpellSystem.enemy(owner, entity) && !needle.hits.contains(entity.getUUID()));
        for (LivingEntity victim : victims) {
            AABB bounds = victim.getBoundingBox().inflate(NEEDLE_HIT_RADIUS);
            if (!bounds.contains(start) && !bounds.contains(end) && bounds.clip(start, end).isEmpty()) continue;
            if (!needle.hits.add(victim.getUUID())) continue;
            if (!NewSpellSystem.clear(owner, start, victim.getBoundingBox().getCenter())) continue;
            NewSpellSystem.damage(owner, victim, NEEDLE_DAMAGE, ElementType.POISON);
            applyFiercePoison(owner, victim);
            Vec3 at = victim.getBoundingBox().getCenter();
            needle.level.sendParticles(ParticleTypes.EFFECT, at.x, at.y, at.z, 16, 0.32, 0.42, 0.32, 0.03);
            needle.level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 6, 0.28, 0.3, 0.28, 0.06);
            needle.level.playSound(null, victim.blockPosition(), SoundEvents.SPIDER_HURT,
                    SoundSource.PLAYERS, 0.75F, 1.35F);
            if (needle.hits.size() >= NEEDLE_PIERCE) return true;
        }
        return false;
    }

    /** 给目标挂上 18 秒的猛毒，并把施法者记在目标身上用于后续中毒结算。 */
    private static void applyFiercePoison(LivingEntity caster, LivingEntity victim) {
        if (victim == null || victim.level().isClientSide || !victim.isAlive()) return;
        victim.addEffect(new MobEffectInstance(NewSpellContent.FIERCE_POISON, FIERCE_TICKS, 0, false, true, true));
        CompoundTag data = victim.getPersistentData();
        long until = victim.level().getGameTime() + FIERCE_TICKS;
        data.putLong(FIERCE_UNTIL, Math.max(until, data.getLong(FIERCE_UNTIL)));
        if (caster != null && caster != victim) data.putUUID(FIERCE_CASTER, caster.getUUID());
        FIERCE_NEXT.remove(victim.getUUID());
    }

    /**
     * 猛毒维护：目标还在中毒时，按“间隔 -4 刻”额外补一次同样数值的中毒伤害。
     *
     * <p>原版中毒的伤害是 1 点、间隔 25 刻（每级 +1 减半），这里不改原版计时器，
     * 只在旁边多结算一条更快的中毒伤害：两条加起来中毒伤害翻倍，受击间隔也短了 4 刻。
     * 每次结算还有 30% 概率把中毒延长 1 秒。</p>
     */
    private static void tickFiercePoison(LivingEntity entity, CompoundTag data, long now) {
        MobEffectInstance fierce = entity.getEffect(NewSpellContent.FIERCE_POISON);
        if (fierce == null) {
            FIERCE_NEXT.remove(entity.getUUID());
            data.remove(FIERCE_UNTIL);
            data.remove(FIERCE_CASTER);
            return;
        }
        MobEffectInstance poison = entity.getEffect(MobEffects.POISON);
        if (poison == null) { FIERCE_NEXT.remove(entity.getUUID()); return; }
        long next = FIERCE_NEXT.getOrDefault(entity.getUUID(), now);
        if (now < next) return;
        int amplifier = Math.clamp(poison.getAmplifier(), 0, 4);
        FIERCE_NEXT.put(entity.getUUID(), now + Math.max(1, (25 >> amplifier) - FIERCE_INTERVAL_SHORTEN));
        UUID casterId = data.hasUUID(FIERCE_CASTER) ? data.getUUID(FIERCE_CASTER) : null;
        LivingEntity caster = casterId == null || !(entity.level() instanceof ServerLevel level) ? null
                : level.getEntity(casterId) instanceof LivingEntity living ? living : null;
        // 施法者已经死亡或离开：中毒照常结算，只是不再归属到某个玩家。
        if (caster == null) caster = entity;
        // 原版中毒在 1 点生命以下不结算伤害，这里保持一致。
        if (entity.getHealth() > 1.0F) {
            float value = SpellAttributes.calculateSecondarySpellDamage(caster, entity, FIERCE_ECHO_DAMAGE, false);
            value = ElementReactionSystem.elementalDamage(caster, entity, ElementType.POISON, value);
            if (value > 0.0F) {
                SpellDamage.hitElement(entity.level(), caster, entity, ElementType.POISON.id(), value);
                if (entity.level() instanceof ServerLevel level) {
                    Vec3 at = entity.getBoundingBox().getCenter();
                    level.sendParticles(ParticleTypes.EFFECT, at.x, at.y, at.z, 4, 0.24, 0.3, 0.24, 0.02);
                }
            }
        }
        if (entity.getRandom().nextFloat() < FIERCE_EXTEND_CHANCE) extendPoison(entity, FIERCE_EXTEND_TICKS);
    }

    /** 中毒延长：原版效果与强制标记一起延后，延长出来的这段时间同样清不掉。 */
    private static void extendPoison(LivingEntity entity, int ticks) {
        MobEffectInstance poison = entity.getEffect(MobEffects.POISON);
        if (poison == null) return;
        entity.addEffect(new MobEffectInstance(MobEffects.POISON, poison.getDuration() + ticks,
                poison.getAmplifier(), poison.isAmbient(), true, true));
        CompoundTag data = entity.getPersistentData();
        long marker = data.getLong(FORCED_POISON_UNTIL);
        if (marker > 0) data.putLong(FORCED_POISON_UNTIL, marker + ticks);
        if (entity.level() instanceof ServerLevel level) {
            Vec3 at = entity.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SNEEZE, at.x, at.y + 0.4, at.z, 3, 0.2, 0.25, 0.2, 0.01);
        }
    }

    // ───────────────────────── 鸩羽千夜 ─────────────────────────

    /** 读条结束：在施法位置放出毒雾。 */
    public static void beginVenomNight(ServerPlayer player) {
        Mist old = MISTS.remove(player.getUUID());
        if (old != null) sendMistVisual(old, true);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 center = player.position();
        Mist mist = new Mist(level, player.getUUID(), player.getId(), center, now);
        MISTS.put(player.getUUID(), mist);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.venom_night_start",
                MIST_BASE_TICKS / 20, (int)MIST_BASE_RADIUS), true);
        sendMistVisual(mist, true);
        level.sendParticles(ParticleTypes.SNEEZE, center.x, center.y + 0.8, center.z, 60, 1.8, 0.9, 1.8, 0.06);
        level.sendParticles(ParticleTypes.EFFECT, center.x, center.y + 0.6, center.z, 40, 1.5, 0.8, 1.5, 0.04);
        level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0F, 0.65F);
        level.playSound(null, player.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 1.15F, 0.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                SoundSource.PLAYERS, 0.85F, 0.70F);
        SpellSyncPayload.send(player);
    }

    /** 毒雾还在不在（用来拦住第二次施法）。 */
    public static boolean hasMist(ServerPlayer player) {
        return MISTS.containsKey(player.getUUID());
    }

    /** 扩散期间按施法键：停止扩散，毒雾照常存在到时长结束。 */
    public static boolean stopMistSpread(ServerPlayer player) {
        Mist mist = MISTS.get(player.getUUID());
        if (mist == null) return false;
        if (!mist.stopped && mist.steps < MIST_MAX_STEPS) {
            mist.stopped = true;
            player.displayClientMessage(Component.translatable("message.dimensional_mana.venom_night_stop",
                    (int)Math.ceil(mist.radius()), Math.max(0, (int)(mist.until - player.level().getGameTime())) / 20), true);
            player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.7F, 1.25F);
        } else {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.venom_night_active",
                    (int)Math.ceil(mist.radius()),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            Math.max(0, mist.until - player.level().getGameTime()) / 20.0)), true);
        }
        return true;
    }

    /** 毒雾每刻：扩散、给雾里的敌人挂毒、刷新视觉与粒子。 */
    private static void tickMists(ServerLevel level, long now) {
        for (Mist mist : new ArrayList<>(MISTS.values())) {
            if (mist.level != level) continue;
            if (now >= mist.until) { endMist(mist, true); continue; }
            if (!mist.stopped && mist.steps < MIST_MAX_STEPS && now >= mist.nextSpreadAt) {
                mist.nextSpreadAt = now + MIST_SPREAD_INTERVAL;
                if (spreadStep(mist)) {
                    mist.steps++;
                    mist.until += 20;
                    sendMistVisual(mist, false);
                    announceSpread(mist);
                } else {
                    mist.stopped = true;
                }
            }
            if (now >= mist.nextPoisonAt) {
                mist.nextPoisonAt = now + MIST_POISON_INTERVAL;
                poisonInMist(mist, now);
            }
            if (now >= mist.lastVisualAt + MIST_VISUAL_REFRESH) sendMistVisual(mist, false);
            if (now % 2 == 0) mistParticles(mist);
        }
    }

    /** 扩散一步：扣 80 魔素、半径 +2。魔素不够或施法者不在就停下。 */
    private static boolean spreadStep(Mist mist) {
        if (!(mist.level.getEntity(mist.owner) instanceof ServerPlayer owner) || !owner.isAlive()
                || owner.serverLevel() != mist.level) return false;
        double cost = SpellAttributes.reducedManaCost(owner, MIST_SPREAD_COST);
        if (ManaCheats.manaCostEnabled(owner) && !ManaCheats.hasMana(owner, cost)) {
            notifyMana(owner, (int)Math.ceil(cost));
            return false;
        }
        if (!ManaData.spendMana(owner, MIST_SPREAD_COST)) {
            notifyMana(owner, (int)Math.ceil(cost));
            return false;
        }
        SpellCasting.syncMana(owner);
        return true;
    }

    private static void announceSpread(Mist mist) {
        Entity entity = mist.level.getEntity(mist.owner);
        if (!(entity instanceof ServerPlayer owner)) return;
        // 每秒一次太吵：每 5 步报一次，其余只播声音。
        long now = mist.level.getGameTime();
        Long last = SPREAD_NOTICE.get(owner.getUUID());
        boolean report = last == null || now - last >= 100 || mist.steps >= MIST_MAX_STEPS;
        if (report) {
            SPREAD_NOTICE.put(owner.getUUID(), now);
            owner.displayClientMessage(Component.translatable("message.dimensional_mana.venom_night_spread",
                    mist.steps, (int)Math.ceil(mist.radius()), (int)(mist.until - now) / 20), true);
        }
        owner.playNotifySound(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 0.45F, 0.85F);
    }

    private static void notifyMana(ServerPlayer player, int cost) {
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.venom_night_no_mana", cost), true);
        player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.6F, 0.65F);
    }

    /** 雾里的敌人每 10 刻：挂 30 秒中毒；本来就在中毒的，再叠一层鸩毒。 */
    private static void poisonInMist(Mist mist, long now) {
        LivingEntity caster = mist.level.getEntity(mist.owner) instanceof LivingEntity living
                && living.isAlive() ? living : null;
        // 施法者已经不在这个世界里：雾只保留视觉，不再对任何人下毒（免得敌我一起遭殃）。
        if (caster == null) return;
        double radius = mist.radius();
        Vec3 eye = mist.center.add(0, 0.9, 0);
        List<LivingEntity> victims = mist.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(mist.center, mist.center).inflate(radius),
                entity -> entity.isAlive() && !entity.isSpectator() && entity != caster
                        && NewSpellSystem.enemy(caster, entity));
        for (LivingEntity victim : victims) {
            Vec3 at = victim.getBoundingBox().getCenter();
            if (at.distanceToSqr(mist.center) > radius * radius) continue;
            // 毒雾会被方块挡住：墙后、地板下的人吃不到毒。
            if (!NewSpellSystem.clear(victim, eye, at)) continue;
            boolean alreadyPoisoned = victim.hasEffect(MobEffects.POISON);
            applyForcedPoison(victim, MIST_POISON_TICKS);
            if (alreadyPoisoned) addZhenLayer(victim, now);
        }
    }

    private static void mistParticles(Mist mist) {
        ServerLevel level = mist.level;
        double radius = mist.radius();
        for (int i = 0; i < 4; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(level.random.nextDouble()) * radius;
            double y = mist.center.y + level.random.nextDouble() * 1.8;
            level.sendParticles(ParticleTypes.SNEEZE, mist.center.x + Math.cos(a) * r, y,
                    mist.center.z + Math.sin(a) * r, 1, 0.1, 0.05, 0.1, 0.005);
        }
        if (level.random.nextInt(3) == 0) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.EFFECT, mist.center.x + Math.cos(a) * radius * 0.85,
                    mist.center.y + 0.35, mist.center.z + Math.sin(a) * radius * 0.85, 2, 0.2, 0.1, 0.2, 0.01);
        }
    }

    /** 毒雾结束：撤掉视觉，真的走完全程才开始算 55 秒冷却。 */
    private static void endMist(Mist mist, boolean cooldown) {
        if (MISTS.get(mist.owner) == mist) MISTS.remove(mist.owner);
        Entity entity = mist.level.getEntity(mist.owner);
        if (!(entity instanceof ServerPlayer owner)) return;
        SpellVisualPayload.send(owner, SpellDefinition.VENOM_NIGHT, SpellVisualPayload.VENOM_MIST_END,
                owner.getId(), mist.center, mist.center, 1);
        SPREAD_NOTICE.remove(owner.getUUID());
        owner.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.85F, 0.55F);
        if (cooldown) {
            owner.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.venom_night_end"), true);
            SpellCasting.startCooldown(owner, SpellDefinition.VENOM_NIGHT);
            SpellSyncPayload.send(owner);
        }
    }

    private static void sendMistVisual(Mist mist, boolean force) {
        long now = mist.level.getGameTime();
        if (!force && now < mist.lastVisualAt + MIST_VISUAL_REFRESH) return;
        mist.lastVisualAt = now;
        Vec3 start = mist.center.add(0, 0.05, 0);
        Vec3 end = start.add(0, mist.radius(), 0);
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.VENOM_NIGHT.id(),
                SpellVisualPayload.VENOM_MIST, mist.ownerEntityId, -1, start, end, MIST_VISUAL_TICKS);
        PacketDistributor.sendToPlayersNear(mist.level, null, mist.center.x, mist.center.y, mist.center.z,
                mist.radius() + 96.0, payload);
    }

    /** 毒刺的飞行视觉：从出刺点到航线终点，时长就是预计飞行刻数。 */
    private static void sendNeedleVisual(ServerPlayer owner, Vec3 from, Vec3 to, int ticks) {
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.VENOM_HEART.id(),
                SpellVisualPayload.VENOM_NEEDLE, owner.getId(), -1, from, to, Math.max(2, ticks));
        PacketDistributor.sendToPlayer(owner, payload);
        if (owner.level() instanceof ServerLevel level)
            PacketDistributor.sendToPlayersNear(level, owner, to.x, to.y, to.z, 96.0, payload);
    }

    // ───────────────────────── 鸩毒 ─────────────────────────

    /** 叠一层鸩毒：层数 +1（上限 50）、时长刷新 60 秒。 */
    private static void addZhenLayer(LivingEntity victim, long now) {
        MobEffectInstance current = victim.getEffect(NewSpellContent.ZHEN_POISON);
        int layers = Math.min(ZHEN_MAX_LAYERS, (current == null ? 0 : current.getAmplifier() + 1) + 1);
        victim.addEffect(new MobEffectInstance(NewSpellContent.ZHEN_POISON, ZHEN_TICKS, layers - 1,
                false, true, true));
        CompoundTag data = victim.getPersistentData();
        data.putLong(ZHEN_UNTIL, now + ZHEN_TICKS);
        data.putInt(ZHEN_LAYERS, layers);
        updateZhenModifiers(victim, data);
        if (victim.level() instanceof ServerLevel level) {
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.EFFECT, at.x, at.y, at.z, 5, 0.3, 0.4, 0.3, 0.02);
            if (level.random.nextInt(4) == 0)
                level.playSound(null, victim.blockPosition(), SoundEvents.SPIDER_HURT,
                        SoundSource.PLAYERS, 0.32F, 1.75F);
        }
    }

    /** 鸩毒的层数（没有这个状态时是 0）。 */
    public static int zhenLayers(LivingEntity entity) {
        MobEffectInstance zhen = entity == null ? null : entity.getEffect(NewSpellContent.ZHEN_POISON);
        return zhen == null ? 0 : Math.clamp(zhen.getAmplifier() + 1, 1, ZHEN_MAX_LAYERS);
    }

    /** 毒易伤：每层 +4%，加算。 */
    public static float poisonVulnerability(LivingEntity entity) {
        int layers = zhenLayers(entity);
        return layers == 0 ? 1.0F : 1.0F + ZHEN_POISON_VULNERABILITY * layers;
    }

    /** 鸩毒的强制维护、减速与生命上限扣除。 */
    private static void tickZhen(LivingEntity entity, CompoundTag data, long now) {
        MobEffectInstance zhen = entity.getEffect(NewSpellContent.ZHEN_POISON);
        long until = data.getLong(ZHEN_UNTIL);
        if (!entity.isAlive() || until <= 0 || now >= until) {
            if (until > 0 || data.contains(ZHEN_LAYERS) || zhen != null) endZhen(entity, data);
            return;
        }
        int layers = Math.clamp(data.getInt(ZHEN_LAYERS), 1, ZHEN_MAX_LAYERS);
        int remain = (int)Math.min(Integer.MAX_VALUE, until - now);
        // 被牛奶之外的强行清除抹掉时，按原层数与剩余时长补回。
        if (zhen == null || zhen.getAmplifier() + 1 < layers || zhen.getDuration() + 2 < remain) {
            entity.addEffect(new MobEffectInstance(NewSpellContent.ZHEN_POISON, remain, layers - 1,
                    false, true, true));
        }
        if (now % 20 == 0) updateZhenModifiers(entity, data);
    }

    /** 鸩毒结束：撤掉减速与生命上限扣除，上限恢复、血不补。 */
    private static void endZhen(LivingEntity entity, CompoundTag data) {
        data.remove(ZHEN_UNTIL);
        data.remove(ZHEN_LAYERS);
        data.remove(ZHEN_HEALTH_STACKS);
        entity.removeEffect(NewSpellContent.ZHEN_POISON);
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(ZHEN_SLOW_ID);
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.removeModifier(ZHEN_HEALTH_ID);
        if (entity.level() instanceof ServerLevel level && entity.isAlive()) {
            Vec3 at = entity.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.EFFECT, at.x, at.y, at.z, 12, 0.5, 0.7, 0.5, 0.03);
            level.playSound(null, entity.blockPosition(), SoundEvents.SPIDER_DEATH, SoundSource.PLAYERS, 0.35F, 1.65F);
        }
    }

    /** 减速 -1%/层、生命上限 -min(90%, 0.55%×次数)，每 20 刻兜底同步一次。 */
    private static void updateZhenModifiers(LivingEntity entity, CompoundTag data) {
        int layers = Math.clamp(data.getInt(ZHEN_LAYERS), 1, ZHEN_MAX_LAYERS);
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
            speed.addOrUpdateTransientModifier(new AttributeModifier(ZHEN_SLOW_ID,
                    -ZHEN_SLOW_PER_LAYER * layers, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        int stacks = Math.max(0, data.getInt(ZHEN_HEALTH_STACKS));
        if (stacks <= 0) { health.removeModifier(ZHEN_HEALTH_ID); return; }
        double loss = Math.min(ZHEN_HEALTH_CAP, ZHEN_HEALTH_STEP * stacks);
        health.addOrUpdateTransientModifier(new AttributeModifier(ZHEN_HEALTH_ID, -loss,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /**
     * 受到毒属性伤害时的登记：身上有鸩毒就排一次“扣生命上限”的掷骰。
     *
     * <p>由 {@link ElementReactionSystem#elementalDamage} 在毒属性伤害结算前调用，
     * 所以本模组任何途径造成的毒伤都会算数；具体的 15% 掷骰延后一刻执行，
     * 不在伤害计算途中改动最大生命属性。</p>
     */
    public static void onPoisonDamage(LivingEntity target) {
        if (target == null || target.level().isClientSide || !target.isAlive()) return;
        if (!target.hasEffect(NewSpellContent.ZHEN_POISON)) return;
        if (!(target.level() instanceof ServerLevel level)) return;
        PENDING_LOSS.add(new PendingHealthLoss(level, target.getUUID(), level.getGameTime() + 1));
    }

    /** 扣生命上限：15% 概率、每次 0.55%，鸩毒结束后统一恢复。 */
    private static void tickHealthLoss(ServerLevel level, long now) {
        Iterator<PendingHealthLoss> iterator = PENDING_LOSS.iterator();
        while (iterator.hasNext()) {
            PendingHealthLoss pending = iterator.next();
            if (pending.level() != level || pending.at() > now) continue;
            iterator.remove();
            if (!(level.getEntity(pending.target()) instanceof LivingEntity target)) continue;
            if (!target.isAlive() || !target.hasEffect(NewSpellContent.ZHEN_POISON)) continue;
            if (target.getRandom().nextFloat() >= ZHEN_HEALTH_CHANCE) continue;
            CompoundTag data = target.getPersistentData();
            data.putInt(ZHEN_HEALTH_STACKS, data.getInt(ZHEN_HEALTH_STACKS) + 1);
            updateZhenModifiers(target, data);
            Vec3 at = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 0.3, at.z, 4, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE,
                    SoundSource.PLAYERS, 0.30F, 0.75F);
        }
    }

    /** 光伤联动：受到光属性伤害时额外造成 30% 的毒伤，不附着毒，每 10 刻最多一次。 */
    public static void onLightDamage(LivingEntity caster, LivingEntity target, float lightDamage) {
        if (target == null || target.level().isClientSide || lightDamage <= 0.0F) return;
        if (!target.hasEffect(NewSpellContent.ZHEN_POISON)) return;
        long now = target.level().getGameTime();
        Long last = LIGHT_TRIGGER.get(target.getUUID());
        if (last != null && now - last < ZHEN_LIGHT_COOLDOWN) return;
        LIGHT_TRIGGER.put(target.getUUID(), now);
        LivingEntity source = caster == null || caster == target ? target : caster;
        PENDING.add(new PendingPoisonHit((ServerLevel)target.level(), source.getUUID(), target.getUUID(),
                lightDamage * ZHEN_LIGHT_RATIO, now + 1));
    }

    /** 光伤联动延后一刻落地：这段毒伤不附着毒，只吃毒易伤与扣上限的规则。 */
    private static void tickPending(ServerLevel level, long now) {
        Iterator<PendingPoisonHit> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingPoisonHit pending = iterator.next();
            if (pending.level() != level || pending.at() > now) continue;
            iterator.remove();
            if (!(level.getEntity(pending.caster()) instanceof LivingEntity caster)) continue;
            if (!(level.getEntity(pending.target()) instanceof LivingEntity target)) continue;
            if (!target.isAlive() || target.isSpectator()) continue;
            float value = pending.amount() * poisonVulnerability(target);
            if (value <= 0.0F) continue;
            SpellDamage.hitResolved(level, caster, target, value);
            onPoisonDamage(target);
            Vec3 at = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.EFFECT, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0.03);
            level.playSound(null, target.blockPosition(), SoundEvents.SPIDER_AMBIENT,
                    SoundSource.PLAYERS, 0.35F, 1.55F);
        }
    }

    // ───────────────────────── 强制中毒 ─────────────────────────

    /** 挂上强制中毒：到期刻写进实体自身数据，任何清除手段都会被拒。 */
    public static void applyForcedPoison(LivingEntity victim, int ticks) {
        if (victim == null || victim.level().isClientSide || !victim.isAlive()) return;
        long now = victim.level().getGameTime();
        CompoundTag data = victim.getPersistentData();
        long until = Math.max(now + ticks, data.getLong(FORCED_POISON_UNTIL));
        data.putLong(FORCED_POISON_UNTIL, until);
        data.putInt(FORCED_POISON_AMP, 0);
        MobEffectInstance current = victim.getEffect(MobEffects.POISON);
        if (current != null && current.getDuration() + 2 >= until - now) return;
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, (int)Math.max(1, until - now), 0,
                false, true, true));
    }

    /**
     * 强制中毒无视“亡灵、蜘蛛不会中毒”这类免疫。
     *
     * <p>原版在 {@code MobEffectEvent.Applicable} 的默认结果里才去问 {@code canBeAffected}，
     * 因此只要我们的强制记录还在，就直接把结果改成“可以附加”，
     * 让僵尸、骷髅、蜘蛛这些常见敌人也吃得到毒雾的中毒与鸩毒。</p>
     */
    public static void allowForcedPoison(MobEffectEvent.Applicable event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !instance.is(MobEffects.POISON)) return;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity.getPersistentData().getLong(FORCED_POISON_UNTIL) > entity.level().getGameTime())
            event.setResult(MobEffectEvent.Applicable.Result.APPLY);
    }

    /** 强制中毒维护：目标还活着、还没到期时，效果被抹掉就按原剩余时长补回。 */
    private static void tickForcedPoison(LivingEntity entity, CompoundTag data, long now) {
        long until = data.getLong(FORCED_POISON_UNTIL);
        if (until <= 0) return;
        if (!entity.isAlive() || now >= until) {
            data.remove(FORCED_POISON_UNTIL);
            data.remove(FORCED_POISON_AMP);
            return;
        }
        int amplifier = Math.clamp(data.getInt(FORCED_POISON_AMP), 0, 4);
        int remain = (int)Math.min(Integer.MAX_VALUE, until - now);
        MobEffectInstance current = entity.getEffect(MobEffects.POISON);
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() + 2 >= remain) return;
        entity.addEffect(new MobEffectInstance(MobEffects.POISON, remain, amplifier, false, true, true));
    }

    /**
     * 移除否决：鸩羽千夜给出的中毒与鸩毒只能等自己到期。
     *
     * <p>牛奶、蜂蜜、不死图腾、{@code /effect clear} 以及别的模组的清除都会走
     * {@code MobEffectEvent.Remove}，这里在目标存活、且强制记录仍在时直接取消。</p>
     */
    public static boolean vetoRemoval(LivingEntity entity, Holder<MobEffect> effect) {
        if (entity == null || effect == null || !entity.isAlive()) return false;
        long now = entity.level().getGameTime();
        CompoundTag data = entity.getPersistentData();
        if (effect.value() == MobEffects.POISON.value()) return data.getLong(FORCED_POISON_UNTIL) > now;
        if (effect.is(NewSpellContent.ZHEN_POISON.getKey())) return data.getLong(ZHEN_UNTIL) > now;
        return false;
    }

    // ───────────────────────── 事件入口 ─────────────────────────

    /** 每刻维护：猛毒补伤、鸩毒层数与生命上限、强制中毒补回。 */
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        long now = entity.level().getGameTime();
        CompoundTag data = entity.getPersistentData();
        tickForcedPoison(entity, data, now);
        tickFiercePoison(entity, data, now);
        tickZhen(entity, data, now);
    }

    /** 每个世界刻：毒刺飞行、毒雾扩散与光伤联动结算。 */
    public static void tickLevel(ServerLevel level, long now) {
        tickNeedles(level);
        tickMists(level, now);
        tickPending(level, now);
        tickHealthLoss(level, now);
        if (now % 200 != 0) return;
        SPREAD_NOTICE.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null);
        LIGHT_TRIGGER.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null);
    }

    /** 下线、死亡或换维度：毒雾收起并开始算冷却，已经挂上的中毒与鸩毒继续自己走。 */
    public static void clear(ServerPlayer player) {
        Mist mist = MISTS.remove(player.getUUID());
        SPREAD_NOTICE.remove(player.getUUID());
        LIGHT_TRIGGER.remove(player.getUUID());
        FIERCE_NEXT.remove(player.getUUID());
        NEEDLES.removeIf(needle -> needle.owner.equals(player.getUUID()));
        if (mist == null) return;
        endMist(mist, false);
        SpellCasting.startCooldown(player, SpellDefinition.VENOM_NIGHT);
    }

    public static void clearAll() {
        NEEDLES.clear();
        MISTS.clear();
        PENDING.clear();
        PENDING_LOSS.clear();
        FIERCE_NEXT.clear();
        LIGHT_TRIGGER.clear();
        SPREAD_NOTICE.clear();
    }

    private PoisonSpells() { }
}
