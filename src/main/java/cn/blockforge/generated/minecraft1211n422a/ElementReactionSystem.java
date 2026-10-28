package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 元素附着、反应判定、消耗规则以及持续/控制反应的服务器权威实现。 */
public final class ElementReactionSystem {
    private static final long REACTION_COOLDOWN = 10L;
    public static final int ARCANE_ATTACHMENT_TICKS = 140;
    private static final int ECHO_TICKS = 200;
    /** 冻结反应持续 6 秒：期间像细雪一样持续掉冰冻伤害，身上挂满冰晶。 */
    private static final int FREEZE_TICKS = 120;
    private static final int FREEZE_DAMAGE_INTERVAL = 20;
    private static final float FREEZE_DAMAGE = 2.0F;
    private static final Map<UUID, Long> LAST_REACTION = new HashMap<>();
    private static final Map<UUID, TimedCasterState> BURNING = new HashMap<>();
    private static final Map<UUID, TimedCasterState> CORRUPTED = new HashMap<>();
    private static final Map<UUID, TimedCasterState> SPREADING = new HashMap<>();
    private static final Map<UUID, Long> FROZEN = new HashMap<>();
    /** 零下领域的深层冻结：4 次直接攻击才会解除，每 4 秒刷新冰附着。 */
    private static final Map<UUID, DeepFreeze> DEEP_FROZEN = new HashMap<>();
    private static final int DEEP_FREEZE_HITS = 4;
    private static final int DEEP_FREEZE_REFRESH = 80;
    private static final Map<UUID, Long> BLINDED = new HashMap<>();
    private static final List<PendingAreaDamage> PENDING_DAMAGE = new ArrayList<>();
    private static final List<PendingEchoDamage> PENDING_ECHO_DAMAGE = new ArrayList<>();
    private static final ResourceLocation SUPERCONDUCT_ARMOR = id("superconduct_armor");
    private static final ResourceLocation MIRE_SPEED = id("mire_speed");
    private static final ResourceLocation MIRE_JUMP = id("mire_jump");
    private static final ResourceLocation SMELT_ARMOR = id("smelt_armor");

    private record TimedCasterState(UUID caster, long expiresAt, long nextAt, boolean firstDamage) {
        TimedCasterState next(long time) { return new TimedCasterState(caster, expiresAt, time, false); }
    }
    private record PendingAreaDamage(UUID caster, UUID center, ServerLevel level, float damage, long at,
                                     boolean protectPlayers) { }
    private record PendingEchoDamage(UUID caster, UUID target, ServerLevel level, float damage, long at) { }
    private record DeepFreeze(UUID caster, long expiresAt, long nextRefresh, int hitsLeft,
                              float damageBonus, float reactionBonus) {
        DeepFreeze refresh(long time) { return new DeepFreeze(caster, expiresAt, time, hitsLeft, damageBonus, reactionBonus); }
        DeepFreeze hit() { return new DeepFreeze(caster, expiresAt, nextRefresh, hitsLeft - 1, damageBonus, reactionBonus); }
    }
    private record Match(Reaction reaction, ElementType existing) { }

    /** 一次元素伤害的结算结果：最终伤害，以及这一下实际触发的反应（没反应时为 {@code null}）。 */
    public record ElementalHit(float damage, Reaction reaction) { }

    /** 计算直接元素伤害，并在真正 hurt 前完成附着和本次伤害的增幅反应。 */
    public static float elementalDamage(LivingEntity caster, LivingEntity target, ElementType incoming, float damage) {
        return elementalHit(caster, target, incoming, damage, false, false).damage();
    }

    /**
     * 同 {@link #elementalDamage}，但把「这一下反应了什么」也一起交回来。
     *
     * <p>法阵一次触发会把所有输出块挨个结算，同一刻就连着打好几发。正常的元素伤害共用
     * 一条「同一目标 0.5 秒内只反应一次」的全局冷却，那样只有第一块能反应，后面几块都会
     * 退化成普通附着。{@code ignoreReactionCooldown} 为 {@code true} 时不再看这条冷却，
     * 每一发都重新按 {@link Reaction#priorityOrder()} 匹配：附着、消耗、反应本身照常结算。</p>
     *
     * <p>这时也不写回冷却——法阵让得开，也不能反过来把别的法术的反应时间占掉。</p>
     *
     * @param protectPlayers 连锁（感电、震荡）与超载的范围爆炸不波及玩家，用于法阵在 PvP
     *                       关闭时「不伤及玩家」这条设定；普通法术传 {@code false}，行为不变
     */
    public static ElementalHit elementalHit(LivingEntity caster, LivingEntity target, ElementType incoming,
                                            float damage, boolean ignoreReactionCooldown,
                                            boolean protectPlayers) {
        if (incoming == null || !target.isAlive()) return new ElementalHit(damage, null);
        long now = target.level().getGameTime();
        // 元素结晶戒指：对应元素的法术伤害 +14%（每枚独立叠加）。
        double ringDamage = MagicEquipment.spellDamageMultiplier(caster, incoming.id());
        if (ringDamage != 1.0) damage *= (float)ringDamage;
        // 共鸣核心：充能提供的对应元素增伤是独立乘区。
        double coreDamage = ResonanceCore.spellDamageMultiplier(caster, incoming.id());
        if (coreDamage != 1.0) damage *= (float)coreDamage;
        if (incoming == ElementType.WATER || incoming == ElementType.ICE)
            NewSpellSystem.damageFlameCage(target, damage);
        if (incoming == ElementType.SOUND && target.hasEffect(NewSpellContent.TINNITUS)) damage *= 1.30F;
        if (incoming == ElementType.FIRE && FROZEN.getOrDefault(target.getUUID(), 0L) > now) damage *= 1.5F;
        if (incoming == ElementType.FIRE && target.hasEffect(NewSpellContent.FIRE_VULNERABILITY)) damage *= 1.35F;
        if (incoming == ElementType.LIGHT && target.getType().is(EntityTypeTags.UNDEAD)) damage *= 1.30F;
        // 鸩毒：每层 +4% 毒易伤；受到光属性伤害时额外追加 30% 毒伤（不附着毒，每 10 刻一次）。
        if (incoming == ElementType.POISON) {
            damage *= PoisonSpells.poisonVulnerability(target);
            if (damage > 0.0F) PoisonSpells.onPoisonDamage(target);
        }
        if (incoming == ElementType.LIGHT) PoisonSpells.onLightDamage(caster, target, damage);
        // 植入寄生期间挨了木属性伤害：33% 概率呆滞 16 刻。
        if (incoming == ElementType.NATURE && damage > 0.0F) WoodSpells.stingOnWoodHit(target);

        Match match = findMatch(target, incoming);
        if (match == null || (!ignoreReactionCooldown && reactionCoolingDown(target, now))) {
            attach(target, incoming, attachmentTicks(caster, incoming));
            return new ElementalHit(damage, null);
        }

        Reaction reaction = match.reaction();
        // 冻结反应对两类目标不成立，直接当普通附着处理（不消耗附着、不弹「冻结」文字）：
        // 1. 冻结免疫的生物（冰史莱姆等）——旧版照挂冰封，冰史莱姆被水打一下就被定住；
        // 2. 水史莱姆身上的天气附着（下雪、零伤害）——它只吃「冰雪/降雪每秒 1 点」的固定伤害，
        //    否则会被冻在原地，根本没法执行「远离冰雪」。
        if (reaction == Reaction.FREEZE && !canFreezeByReaction(target, damage)) {
            attach(target, incoming, attachmentTicks(caster, incoming));
            return new ElementalHit(damage, null);
        }
        int existingTicks = remaining(target, match.existing());
        // 同元素重复施加应先刷新到较长时长，再作为本次反应的一方参与消耗。
        int incomingTicks = Math.max(remaining(target, incoming), attachmentTicks(caster, incoming));
        if (reaction == Reaction.CHARGE) charge(target, incoming, incomingTicks);
        else consume(target, match.existing(), existingTicks, incoming, incomingTicks);
        if (!ignoreReactionCooldown) LAST_REACTION.put(target.getUUID(), now);
        announceReaction(caster, target, reaction);
        // 零下领域的深层冻结会额外放大元素反应伤害（与“受到伤害增加”分开乘算）。
        if (deepFrozen(target)) damage *= deepFreezeReactionMultiplier(target);

        boolean echo = reaction != Reaction.CHARGE && target.hasEffect(GeneratedMod.ARCANE_ECHO);
        float beforeReaction = damage;
        float reactionDamage = 0.0F;
        switch (reaction) {
            case VAPORIZE -> { damage *= 2.0F; reactionDamage = damage; }
            case MELT -> {
                // 铁傀儡的「融化反应易伤 25%」：在融化反应本身的倍率上再乘一次独立乘区。
                damage *= (float)(1.8 * (1.0 + MobCombatSystem.reactionVulnerability(target, "melt")));
                reactionDamage = damage;
            }
            case OVERLOAD -> {
                reactionDamage = damage * 2.5F;
                PENDING_DAMAGE.add(new PendingAreaDamage(caster.getUUID(), target.getUUID(), server(target),
                        reactionDamage, now + 1, protectPlayers));
                knockFrom(target.position(), target, 1.1);
            }
            case ELECTRO_CHARGED -> {
                if (target instanceof ElementalSlime slime && slime.variant() == ElementalSlimeVariant.LIGHTNING) {
                    // 感电可能由水伤触发：整次反应对雷史莱姆不扣血，并恢复 2.5% 最大生命。
                    slime.absorbElectroCharged();
                    damage = 0.0F;
                    reactionDamage = 0.0F;
                } else {
                    reactionDamage = damage * 0.24F;
                    chain(caster, target, ElementType.WATER, ElementType.LIGHTNING, reactionDamage, 5, 8.0, 0.90F,
                            protectPlayers);
                }
            }
            case SUPERCONDUCT -> {
                damage *= 1.5F;
                reactionDamage = damage;
                // 金钟罩期间免疫破甲：超导的降护甲挂不上，反应本身的伤害加成照常。
                if (!MetalSpells.immuneToArmorBreak(target))
                    timedModifier(target, Attributes.ARMOR, SUPERCONDUCT_ARMOR, -0.50,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 160);
            }
            case CATALYZE -> { damage *= 1.4F; reactionDamage = damage; }
            case FREEZE -> {
                reactionDamage = damage;
                // 雨衣：受到的冻结时长减半（定身、减速与冰壳外观一起缩短）。
                int frozenTicks = RainCoatItem.freezeTicks(target, FREEZE_TICKS);
                FROZEN.put(target.getUUID(), now + frozenTicks);
                freezeSolid(target);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, frozenTicks, 9, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, frozenTicks, 9, false, false, true));
            }
            case CHARGE -> target.addEffect(new MobEffectInstance(GeneratedMod.ARCANE_ECHO, ECHO_TICKS, 0, false, true, true));
            case BURNING -> BURNING.put(target.getUUID(), new TimedCasterState(caster.getUUID(), now + 120, now + 20, true));
            case CORRUPTION -> corruptPoison(caster, target, now);
            case SWIRL -> {
                ElementType spread = incoming == ElementType.WIND ? match.existing() : incoming;
                int duration = incoming == ElementType.WIND ? existingTicks : incomingTicks;
                spreadAttachment(target, spread, 5.0, duration);
                // 风爆步留下的“风袭”：扩散反应本身不额外结算伤害，但每次扩散都会引爆一次 48 点爆炸伤。
                WindSpells.windAssaultBlast(caster, target);
            }
            case MIRE -> {
                reactionDamage = damage;
                // 雨靴：泥沼的减速与跳跃压制都挂不上，反应本身的伤害照常结算。
                if (!RainBootsItem.worn(target)) {
                    timedModifier(target, Attributes.MOVEMENT_SPEED, MIRE_SPEED, -0.60,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 100);
                    timedModifier(target, Attributes.JUMP_STRENGTH, MIRE_JUMP, -0.50,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 100);
                }
            }
            case SPREAD -> SPREADING.put(target.getUUID(), new TimedCasterState(caster.getUUID(), now + 60, now + 20, false));
            case SMELT -> {
                reactionDamage = damage;
                // 金钟罩期间免疫破甲：熔蚀的降护甲挂不上，反应本身的伤害照样结算。
                if (!MetalSpells.immuneToArmorBreak(target))
                    timedModifier(target, Attributes.ARMOR, SMELT_ARMOR, -0.40,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 120);
            }
            case PURIFY -> { damage = purify(target, damage); reactionDamage = damage; }
            case SHOCKWAVE -> {
                reactionDamage = damage * 0.15F;
                chain(caster, target, null, ElementType.SOUND, reactionDamage, 5, 8.0, 1.0F, protectPlayers);
            }
            case BLIND -> {
                reactionDamage = damage;
                BLINDED.put(target.getUUID(), now + 60);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, true, true));
            }
        }
        // 元素结晶戒指：戒指元素参与了本次反应时，反应造成的伤害 +7%（每枚独立叠加）。
        double ringReaction = MagicEquipment.reactionMultiplier(caster, incoming.id(), match.existing().id());
        if (ringReaction != 1.0) {
            reactionDamage = (float)(reactionDamage * ringReaction);
            if (damage != beforeReaction) damage *= (float)ringReaction;
        }
        if (echo && reactionDamage > 0.0F) {
            target.removeEffect(GeneratedMod.ARCANE_ECHO);
            PENDING_ECHO_DAMAGE.add(new PendingEchoDamage(
                    caster.getUUID(), target.getUUID(), server(target), reactionDamage * 0.30F, now + 1));
        }
        // 植入寄生：每次元素反应都会额外触发一次强制激发（反应伤害 ×0.55 的木伤）。
        WoodSpells.parasiteReaction(caster, target, reactionDamage);
        return new ElementalHit(damage, reaction);
    }

    /** 奥术伤害留下 7 秒奥术附着；存在元素时按同一优先级触发充能。 */
    public static float arcaneDamage(LivingEntity caster, LivingEntity target, float damage) {
        applyArcane(caster, target);
        double ringDamage = MagicEquipment.spellDamageMultiplier(caster, MagicEquipment.ARCANE);
        if (ringDamage != 1.0) damage = (float)(damage * ringDamage);
        double coreDamage = ResonanceCore.spellDamageMultiplier(caster, MagicEquipment.ARCANE);
        return coreDamage == 1.0 ? damage : (float)(damage * coreDamage);
    }

    public static void applyArcane(LivingEntity caster, LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        long now = target.level().getGameTime();
        ElementType element = firstActiveElement(target);
        if (element == null || reactionCoolingDown(target, now)) {
            attachArcane(caster, target);
            return;
        }
        charge(target, element, remaining(target, element));
        LAST_REACTION.put(target.getUUID(), now);
        announceReaction(caster, target, Reaction.CHARGE);
        target.addEffect(new MobEffectInstance(GeneratedMod.ARCANE_ECHO, ECHO_TICKS, 0, false, true, true));
    }

    /** 供生物施法 AI 判断某个元素现在能否和目标身上的附着组成反应。 */
    public static boolean wouldReact(LivingEntity target, ElementType incoming) {
        if (target == null || !target.isAlive() || incoming == null || findMatch(target, incoming) == null) return false;
        return !reactionCoolingDown(target, target.level().getGameTime());
    }

    public static boolean wouldReactArcane(LivingEntity target) {
        if (target == null || !target.isAlive() || firstActiveElement(target) == null) return false;
        return !reactionCoolingDown(target, target.level().getGameTime());
    }

    /** 无直接伤害的元素法术也能施加附着并触发控制、持续或次级反应。 */
    public static void apply(LivingEntity caster, LivingEntity target, ElementType element) {
        elementalDamage(caster, target, element, 0.0F);
    }

    /** 持续环境只在附着不足一秒时补满，不应在每一刻重复触发元素反应。 */
    public static void refresh(LivingEntity target, ElementType element) {
        if (element != null && target.isAlive() && remaining(target, element) <= 20) {
            attach(target, element, element.attachmentTicks());
        }
    }

    /** 每个生物每刻调用一次，维护持续反应与过期属性。 */
    public static void tick(LivingEntity living) {
        if (!(living.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        // 原版灼烧/中毒每次生效期间持续刷新对应长附着。
        if (living.getRemainingFireTicks() > 0) attach(living, ElementType.FIRE, ElementType.FIRE.attachmentTicks());
        if (living.hasEffect(MobEffects.POISON)) attach(living, ElementType.POISON, ElementType.POISON.attachmentTicks());
        tickBurning(level, living, now);
        tickCorruption(level, living, now);
        tickSpreading(level, living, now);
        if (FROZEN.getOrDefault(living.getUUID(), 0L) > now) {
            living.setDeltaMovement(0, Math.min(0, living.getDeltaMovement().y), 0);
            living.hurtMarked = true;
            // 持续期间静默维持冰冻值；每秒补发一次冰壳包（中途走进视野也能看到），
            // 碎裂声和冰晶爆发只在触发那一刻演一次，否则会一秒响二十次。
            keepFrozen(living, now);
            if (living.canFreeze() && now % FREEZE_DAMAGE_INTERVAL == 0)
                SpellDamage.apply(living, level.damageSources().freeze(), FREEZE_DAMAGE);
            if (now % 4 == 0) freezeCrystals(level, living);
        } else {
            // 冰晶状态结束：清掉冰冻值，客户端的发蓝外观跟着恢复（真在细雪里的话原版下一帧会再冻上）。
            if (FROZEN.remove(living.getUUID()) != null && living.getTicksFrozen() > 0)
                living.setTicksFrozen(0);
        }
        if (BLINDED.getOrDefault(living.getUUID(), 0L) <= now) BLINDED.remove(living.getUUID());
        DeepFreeze deep = DEEP_FROZEN.get(living.getUUID());
        if (deep != null) {
            if (!living.isAlive() || now > deep.expiresAt()) {
                clearDeepFreeze(living);
            } else {
                // 牛奶只会抹掉图标，真正状态仍由这里维护；每刻补回效果与冰冻值。
                if (!living.hasEffect(NewSpellContent.DEEP_FREEZE))
                    living.addEffect(new MobEffectInstance(NewSpellContent.DEEP_FREEZE,
                            (int)Math.max(1, deep.expiresAt() - now), 0, false, false, true));
                keepFrozen(living, now);
                if (now >= deep.nextRefresh()) {
                    attach(living, ElementType.ICE, ElementType.ICE.attachmentTicks());
                    DEEP_FROZEN.put(living.getUUID(), deep.refresh(now + DEEP_FREEZE_REFRESH));
                }
                if (now % 20 == 0) FrozenVisualPayload.send(living, (int)Math.max(1, deep.expiresAt() - now));
                if (now % 4 == 0) freezeCrystals(level, living);
            }
        }
        // 金钟罩期间免疫破甲：开启前已经挂上的超导/熔蚀降护甲也立刻收回。
        if (MetalSpells.immuneToArmorBreak(living)) clearArmorBreakModifiers(living);
        cleanupModifier(living, SUPERCONDUCT_ARMOR, now);
        cleanupModifier(living, MIRE_SPEED, now);
        cleanupModifier(living, MIRE_JUMP, now);
        cleanupModifier(living, SMELT_ARMOR, now);
        tickPending(level, now);
        tickPendingEcho(level, now);
    }

    /**
     * 冻结反应能不能真正生效：冻结免疫的生物（冰史莱姆等）不成立；
     * 水史莱姆只吃「冰雪/降雪每秒 1 点」的固定环境伤害，零伤害的天气冰附着也不会把它冻住。
     */
    private static boolean canFreezeByReaction(LivingEntity target, float damage) {
        if (!target.canFreeze()) return false;
        return !(damage <= 0.0F && target instanceof ElementalSlime slime
                && slime.variant() == ElementalSlimeVariant.WATER);
    }

    /**
     * 冻结反应命中：把冰冻值顶到满值，客户端据此把生物渲染成泛蓝的冰晶状态；
     * 同时给附近客户端发一次冰壳特效，并补上碎裂声。只有刚开始冻结时才调用。
     */
    private static void freezeSolid(LivingEntity target) {
        keepFrozen(target, target.level().getGameTime());
        FrozenVisualPayload.send(target, FREEZE_TICKS);
        if (target.level() instanceof ServerLevel level) {
            AABB box = target.getBoundingBox();
            Vec3 at = box.getCenter();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    at.x, at.y, at.z, 14, box.getXsize() * 0.55, box.getYsize() * 0.45, box.getZsize() * 0.55, 0.02);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.7F, 1.7F);
        }
    }

    /** 每刻悄悄把冰冻值补满（原版不在细雪里会每刻减 2），不发声、不喷粒子。 */
    private static void keepFrozen(LivingEntity target, long now) {
        if (!target.canFreeze()) return;
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 4));
        // 每秒补发一次冰壳，玩家中途走进视野也能看到完整的冰晶状态。
        if (now % 20 == 0) {
            long until = FROZEN.getOrDefault(target.getUUID(), now);
            FrozenVisualPayload.send(target, (int)Math.max(1, Math.min(FREEZE_TICKS, until - now)));
        }
    }

    /** 冻结期间持续飘冰晶，让“身上结冰”这件事一直看得出来。 */
    private static void freezeCrystals(ServerLevel level, LivingEntity living) {
        AABB box = living.getBoundingBox();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                box.getCenter().x, box.minY + box.getYsize() * 0.6, box.getCenter().z, 3,
                box.getXsize() * 0.5, box.getYsize() * 0.4, box.getZsize() * 0.5, 0.0);
    }

    /** 零下领域的深层冻结：20 秒起步、不易被解除，期间每 4 秒刷新冰附着。 */
    public static void deepFreeze(LivingEntity caster, LivingEntity target, int ticks, float damageBonus, float reactionBonus) {
        if (target == null || !target.isAlive()) return;
        // 冰史莱姆这类「冻结免疫」生物连着深冻也不吃，不然冰系大招照样把它冻成冰块。
        if (!target.canFreeze()) return;
        long now = target.level().getGameTime();
        int duration = Math.max(20, RainCoatItem.freezeTicks(target, ticks));
        DEEP_FROZEN.put(target.getUUID(), new DeepFreeze(caster.getUUID(), now + duration, now,
                DEEP_FREEZE_HITS, damageBonus, reactionBonus));
        target.addEffect(new MobEffectInstance(NewSpellContent.DEEP_FREEZE, duration, 0, false, false, true));
        attach(target, ElementType.ICE, ElementType.ICE.attachmentTicks());
        keepFrozen(target, now);
        FrozenVisualPayload.send(target, duration);
        if (target.level() instanceof ServerLevel level) {
            AABB box = target.getBoundingBox();
            Vec3 at = box.getCenter();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    at.x, at.y, at.z, 18, box.getXsize() * 0.55, box.getYsize() * 0.5, box.getZsize() * 0.55, 0.02);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.4F);
        }
    }

    public static boolean deepFrozen(LivingEntity target) {
        return activeDeepFreeze(target) != null;
    }

    /** 深层冻结期间受到的所有伤害提高（物理、元素、反应都算）。 */
    public static float deepFreezeDamageMultiplier(LivingEntity target) {
        DeepFreeze deep = activeDeepFreeze(target);
        return deep == null ? 1.0F : 1.0F + deep.damageBonus();
    }

    private static float deepFreezeReactionMultiplier(LivingEntity target) {
        DeepFreeze deep = activeDeepFreeze(target);
        return deep == null ? 1.0F : 1.0F + deep.reactionBonus();
    }

    private static DeepFreeze activeDeepFreeze(LivingEntity target) {
        DeepFreeze deep = target == null ? null : DEEP_FROZEN.get(target.getUUID());
        return deep == null || target.level().getGameTime() > deep.expiresAt() ? null : deep;
    }

    /** 只统计玩家或生物本体的直接攻击；法术弹体、灼烧、中毒等 DOT 不计入 4 次。 */
    public static void deepFreezeHit(LivingEntity target, DamageSource source) {
        DeepFreeze deep = activeDeepFreeze(target);
        if (deep == null || !directAttack(source)) return;
        DeepFreeze next = deep.hit();
        if (next.hitsLeft() <= 0) clearDeepFreeze(target);
        else DEEP_FROZEN.put(target.getUUID(), next);
    }

    private static boolean directAttack(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof LivingEntity) || direct != source.getEntity()) return false;
        String id = source.getMsgId();
        // 玩家近战是 player，生物近战是 mob；其余全部视为非直接攻击。
        return "player".equals(id) || "mob".equals(id);
    }

    private static void clearDeepFreeze(LivingEntity target) {
        if (DEEP_FROZEN.remove(target.getUUID()) == null) return;
        target.removeEffect(NewSpellContent.DEEP_FREEZE);
        if (target.getTicksFrozen() > 0) target.setTicksFrozen(0);
        // 提前解冻时让冰壳在一秒内消失，不要继续挂满整个冻结时长。
        FrozenVisualPayload.send(target, 20);
        if (target.level() instanceof ServerLevel level) {
            Vec3 at = target.getBoundingBox().getCenter();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    at.x, at.y, at.z, 22, 0.4, 0.6, 0.4, 0.06);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.9F);
        }
    }

    /** 致盲期间，带攻击者的命中有 50% 几率落空。 */
    public static boolean attackMisses(LivingEntity attacker) {        return BLINDED.getOrDefault(attacker.getUUID(), 0L) > attacker.level().getGameTime()
                && attacker.getRandom().nextBoolean();
    }

    /** 超导对物理打击补足“护甲降到零以下则转为易伤”的效果。 */
    public static float modifyPhysicalDamage(LivingEntity victim, DamageSource source, float damage) {
        if (victim.getPersistentData().getLong(modifierKey(SUPERCONDUCT_ARMOR)) <= victim.level().getGameTime()) return damage;
        // 法术伤害（通用或十二种元素）不吃这条物理易伤，和以前按 msgId 排掉间接魔法的规则一致。
        if (source.getDirectEntity() instanceof LivingEntity && !SpellDamage.isSpell(source)
                && victim.getArmorValue() <= 0) return damage * 1.5F;
        return damage;
    }

    public static void clearAll() {
        LAST_REACTION.clear(); BURNING.clear(); CORRUPTED.clear(); SPREADING.clear();
        FROZEN.clear(); DEEP_FROZEN.clear(); BLINDED.clear(); PENDING_DAMAGE.clear(); PENDING_ECHO_DAMAGE.clear();
    }

    private static void awardReactionAdvancements(LivingEntity caster, Reaction reaction) {
        if (!(caster instanceof ServerPlayer player)) return;
        // 兼容数据包重载后尚未重新登录的玩家，确保独立反应页先于子进度解锁。
        MagicProgression.awardAdvancement(player, "reaction_root");
        MagicProgression.awardAdvancement(player, "first_reaction");
        MagicProgression.awardAdvancement(player, "reaction_" + reaction.id());
        MagicProgression.awardAdvancementCriterion(player, "reaction_master", reaction.id());
    }

    private static Match findMatch(LivingEntity target, ElementType incoming) {
        Map<ElementType, Integer> active = active(target);
        // 先按反应优先级，再按稳定的元素枚举顺序匹配；结果不受附着加入先后影响。
        // 充能放在冻结之后，因此火、水、奥术共存时仍会优先得到蒸发。
        for (Reaction reaction : Reaction.priorityOrder()) {
            if (reaction == Reaction.CHARGE) {
                if (target.hasEffect(GeneratedMod.ARCANE_ATTACHMENT)) return new Match(reaction, incoming);
                continue;
            }
            for (ElementType existing : ElementType.values()) {
                if (active.containsKey(existing) && existing != incoming
                        && reactionFor(incoming, existing) == reaction) {
                    return new Match(reaction, existing);
                }
            }
        }
        return null;
    }

    /** 两种元素相撞会得到哪个反应；没有反应返回 {@code null}（同元素也不算）。 */
    public static Reaction reactionFor(ElementType a, ElementType b) {
        if (a == null || b == null || a == b) return null;
        if (pair(a, b, ElementType.FIRE, ElementType.WATER)) return Reaction.VAPORIZE;
        if (pair(a, b, ElementType.FIRE, ElementType.ICE)) return Reaction.MELT;
        if (pair(a, b, ElementType.FIRE, ElementType.LIGHTNING)) return Reaction.OVERLOAD;
        if (pair(a, b, ElementType.WATER, ElementType.LIGHTNING)) return Reaction.ELECTRO_CHARGED;
        if (pair(a, b, ElementType.ICE, ElementType.LIGHTNING)) return Reaction.SUPERCONDUCT;
        if (pair(a, b, ElementType.NATURE, ElementType.LIGHTNING)) return Reaction.CATALYZE;
        if (pair(a, b, ElementType.WATER, ElementType.ICE)) return Reaction.FREEZE;
        if (pair(a, b, ElementType.FIRE, ElementType.NATURE)) return Reaction.BURNING;
        if (pair(a, b, ElementType.NATURE, ElementType.POISON)) return Reaction.CORRUPTION;
        if (pair(a, b, ElementType.WATER, ElementType.EARTH)) return Reaction.MIRE;
        if (pair(a, b, ElementType.WATER, ElementType.NATURE)) return Reaction.SPREAD;
        if (pair(a, b, ElementType.FIRE, ElementType.METAL)) return Reaction.SMELT;
        if (pair(a, b, ElementType.LIGHT, ElementType.POISON)) return Reaction.PURIFY;
        if (pair(a, b, ElementType.LIGHTNING, ElementType.SOUND)) return Reaction.SHOCKWAVE;
        if (pair(a, b, ElementType.LIGHT, ElementType.FIRE)) return Reaction.BLIND;
        if (a == ElementType.WIND || b == ElementType.WIND) return Reaction.SWIRL;
        return null;
    }

    private static void charge(LivingEntity target, ElementType element, int elementTicks) {
        target.removeEffect(GeneratedMod.ARCANE_ATTACHMENT);
        int cap = element.shortAttachment() ? 15 * 20 : 25 * 20;
        replace(target, element, Math.min(cap, Math.max(1, elementTicks) * 2));
    }

    private static void attachArcane(LivingEntity caster, LivingEntity target) {
        int ticks = Math.max(1, Math.round(ARCANE_ATTACHMENT_TICKS
                * (float)MagicEquipment.attachmentMultiplier(caster, MagicEquipment.ARCANE)));
        MobEffectInstance old = target.getEffect(GeneratedMod.ARCANE_ATTACHMENT);
        int duration = old == null ? ticks : Math.max(old.getDuration(), ticks);
        target.addEffect(new MobEffectInstance(GeneratedMod.ARCANE_ATTACHMENT, duration, 0, false, true, true));
    }

    /** 附着时长 = 元素基础时长 × 施法者对应元素戒指加成。 */
    private static int attachmentTicks(LivingEntity caster, ElementType element) {
        return Math.max(1, Math.round(element.attachmentTicks()
                * (float)MagicEquipment.attachmentMultiplier(caster, element.id())));
    }

    private static ElementType firstActiveElement(LivingEntity target) {
        for (ElementType element : ElementType.values()) if (remaining(target, element) > 0) return element;
        return null;
    }

    private static boolean reactionCoolingDown(LivingEntity target, long now) {
        return LAST_REACTION.getOrDefault(target.getUUID(), Long.MIN_VALUE) + REACTION_COOLDOWN > now;
    }

    private static void announceReaction(LivingEntity caster, LivingEntity target, Reaction reaction) {
        awardReactionAdvancements(caster, reaction);
        ReactionVisualPayload.send(target, reaction);
        reactionParticles(target, reaction.color());
        // 法阵的「元素反应检测」触发块要记得这里刚在哪个位置发生过什么反应。
        if (!target.level().isClientSide) {
            MagicCircleTriggers.recordReaction(target.blockPosition(), reaction, target.level().getGameTime());
        }
    }

    private static void consume(LivingEntity target, ElementType existing, int existingTicks,
                                ElementType incoming, int incomingTicks) {
        boolean existingShort = existing.shortAttachment();
        boolean incomingShort = incoming.shortAttachment();
        if (existingShort && incomingShort) {
            // 短 + 短：双方都完全消耗。incoming 可能本来就已附着，因此也必须显式移除。
            remove(target, existing);
            remove(target, incoming);
            return;
        }
        if (existingShort != incomingShort) {
            // 短 + 长：短附着消失，长附着只保留自身本次参与反应前剩余时间的 50%。
            ElementType shortElement = existingShort ? existing : incoming;
            ElementType longElement = existingShort ? incoming : existing;
            int longTicks = existingShort ? incomingTicks : existingTicks;
            remove(target, shortElement);
            replace(target, longElement, scaledTicks(longTicks, 0.5F));
            return;
        }

        // 长 + 长：剩余时间少的一方是弱势元素并消失；强势元素保留 80%。
        // 剩余时间恰好相等时，以元素的标准附着时长判定；仍相等才用固定枚举顺序兜底，
        // 保证 A + B 与 B + A 得到完全相同的结果。
        boolean existingIsWeaker = existingTicks < incomingTicks
                || existingTicks == incomingTicks && weakerThan(existing, incoming);
        ElementType weaker = existingIsWeaker ? existing : incoming;
        ElementType stronger = existingIsWeaker ? incoming : existing;
        int strongerTicks = existingIsWeaker ? incomingTicks : existingTicks;
        remove(target, weaker);
        replace(target, stronger, scaledTicks(strongerTicks, 0.8F));
    }

    private static boolean weakerThan(ElementType first, ElementType second) {
        int durationComparison = Integer.compare(first.attachmentTicks(), second.attachmentTicks());
        return durationComparison != 0 ? durationComparison < 0 : first.ordinal() < second.ordinal();
    }

    private static int scaledTicks(int ticks, float factor) {
        return Math.max(1, (int)(ticks * factor));
    }

    /** 给目标挂上元素附着（已有附着时取更长时长）；法阵触发块的元素槽也走这里。 */
    public static void attach(LivingEntity target, ElementType element, int ticks) {
        Holder<MobEffect> effect = GeneratedMod.elementEffect(element);
        int adjusted = targetAttachmentTicks(target, element, ticks);
        MobEffectInstance old = target.getEffect(effect);
        int duration = old == null ? adjusted : Math.max(old.getDuration(), adjusted);
        target.addEffect(new MobEffectInstance(effect, duration, 0, false, true, true));
    }

    /** 精确改写反应后的剩余时长；与 attach 的“只取更长时长”语义分开。 */
    private static void replace(LivingEntity target, ElementType element, int ticks) {
        remove(target, element);
        Holder<MobEffect> effect = GeneratedMod.elementEffect(element);
        target.addEffect(new MobEffectInstance(effect,
                Math.max(1, targetAttachmentTicks(target, element, ticks)), 0, false, true, true));
    }

    /**
     * 附着真正落到目标身上时的时长。
     *
     * <p>{@link #attachmentTicks} 算的是「施法者」的加成（元素戒指），这里再算「目标」的减免：
     * 穿着雨靴的玩家自己受到的水元素附着减半，穿着雨衣的玩家受到的冰、水附着只剩一成，
     * 别的元素、别的生物都不受影响。</p>
     */
    private static int targetAttachmentTicks(LivingEntity target, ElementType element, int ticks) {
        float multiplier = RainBootsItem.attachmentMultiplier(target, element)
                * RainCoatItem.attachmentMultiplier(target, element);
        return multiplier == 1.0F ? ticks : Math.max(1, Math.round(ticks * multiplier));
    }

    private static void remove(LivingEntity target, ElementType element) {
        target.removeEffect(GeneratedMod.elementEffect(element));
    }

    private static int remaining(LivingEntity target, ElementType element) {
        MobEffectInstance effect = target.getEffect(GeneratedMod.elementEffect(element));
        return effect == null ? 0 : effect.getDuration();
    }

    private static Map<ElementType, Integer> active(LivingEntity target) {
        Map<ElementType, Integer> result = new EnumMap<>(ElementType.class);
        for (ElementType element : ElementType.values()) {
            int ticks = remaining(target, element);
            if (ticks > 0) result.put(element, ticks);
        }
        return result;
    }

    private static void chain(LivingEntity caster, LivingEntity first, ElementType requiredAttachment,
                              ElementType damageElement, float firstDamage, int maxTargets, double radius,
                              float decay, boolean protectPlayers) {
        ServerLevel level = server(first);
        LivingEntity current = first;
        Set<UUID> hit = new HashSet<>();
        hit.add(first.getUUID());
        float damage = firstDamage;
        for (int i = 0; i < maxTargets; i++) {
            LivingEntity from = current;
            LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(radius), entity ->
                    entity.isAlive() && entity != caster && !hit.contains(entity.getUUID())
                            && (!protectPlayers || !(entity instanceof Player))
                            && (requiredAttachment == null || remaining(entity, requiredAttachment) > 0)).stream()
                    .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(from))).orElse(null);
            if (next == null) break;
            damage *= i == 0 ? 1.0F : decay;
            // damage 已经来自本次实际伤害，不能再次乘魔素强度或暴击。
            float dealt = (float)SpellAttributes.applySpellResistance(next, damage);
            // 感电等带元素的连锁必须保留元素伤害类型：雷史莱姆才能正确免疫并回血。
            if (damageElement != null) SpellDamage.hitResolvedElement(level, caster, next, damageElement.id(), dealt);
            else SpellDamage.hitResolved(level, caster, next, dealt);
            lightning(level, from.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), damageElement.color());
            hit.add(next.getUUID());
            current = next;
        }
    }

    private static void spreadAttachment(LivingEntity source, ElementType element, double radius, int duration) {
        ServerLevel level = server(source);
        level.getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(radius),
                entity -> entity != source && entity.isAlive()).forEach(entity -> attach(entity, element, duration));
    }

    private static void corruptPoison(LivingEntity caster, LivingEntity target, long now) {
        MobEffectInstance poison = target.getEffect(MobEffects.POISON);
        if (poison != null) target.addEffect(new MobEffectInstance(MobEffects.POISON,
                Math.max(1, (int)(poison.getDuration() * 1.5F)), poison.getAmplifier(), poison.isAmbient(), true, true));
        CORRUPTED.put(target.getUUID(), new TimedCasterState(caster.getUUID(), now + 120, now + 25, true));
    }

    private static float purify(LivingEntity target, float damage) {
        MobEffectInstance poison = target.getEffect(MobEffects.POISON);
        int seconds = poison == null ? 0 : (int)Math.ceil(poison.getDuration() / 20.0);
        target.removeEffect(MobEffects.POISON);
        CORRUPTED.remove(target.getUUID());
        // 净化：按中毒剩余秒数强化本次光伤害，额外倍率上限 230%（每剩 1 秒 +10%）。
        return damage * (1.0F + Math.min(2.3F, seconds * 0.10F));
    }

    private static void tickBurning(ServerLevel level, LivingEntity target, long now) {
        TimedCasterState state = BURNING.get(target.getUUID());
        if (state == null) return;
        if (!target.isAlive() || now > state.expiresAt()) { BURNING.remove(target.getUUID()); return; }
        if (now < state.nextAt()) return;
        LivingEntity caster = living(level.getEntity(state.caster()));
        if (caster != null) SpellDamage.hitElement(level, caster, target, ElementType.FIRE.id(),
                SpellAttributes.calculateSecondarySpellDamage(caster, target, 4.0F, state.firstDamage()));
        BURNING.put(target.getUUID(), state.next(now + 20));
        attach(target, ElementType.FIRE, ElementType.FIRE.attachmentTicks());
    }

    private static void tickCorruption(ServerLevel level, LivingEntity target, long now) {
        TimedCasterState state = CORRUPTED.get(target.getUUID());
        if (state == null) return;
        if (!target.isAlive() || now > state.expiresAt() || !target.hasEffect(MobEffects.POISON)) {
            CORRUPTED.remove(target.getUUID()); return;
        }
        if (now < state.nextAt()) return;
        LivingEntity caster = living(level.getEntity(state.caster()));
        if (caster != null) SpellDamage.hitElement(level, caster, target, ElementType.POISON.id(),
                SpellAttributes.calculateSecondarySpellDamage(caster, target, 1.0F, state.firstDamage()));
        CORRUPTED.put(target.getUUID(), state.next(now + 25));
    }

    private static void tickSpreading(ServerLevel level, LivingEntity source, long now) {
        TimedCasterState state = SPREADING.get(source.getUUID());
        if (state == null) return;
        if (!source.isAlive() || now > state.expiresAt()) { SPREADING.remove(source.getUUID()); return; }
        if (now < state.nextAt()) return;
        spreadAttachment(source, ElementType.NATURE, 2.0, ElementType.NATURE.attachmentTicks());
        SPREADING.put(source.getUUID(), state.next(now + 20));
    }

    private static void tickPending(ServerLevel level, long now) {
        Iterator<PendingAreaDamage> iterator = PENDING_DAMAGE.iterator();
        while (iterator.hasNext()) {
            PendingAreaDamage pending = iterator.next();
            if (pending.level() != level || pending.at() > now) continue;
            iterator.remove();
            LivingEntity caster = living(level.getEntity(pending.caster()));
            LivingEntity center = living(level.getEntity(pending.center()));
            if (caster == null || center == null) continue;
            Vec3 point = center.getBoundingBox().getCenter();
            level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(3),
                    entity -> entity != caster && entity.isAlive()
                            && (!pending.protectPlayers() || !(entity instanceof Player))).forEach(entity -> {
                // 反应爆炸沿用触发时已算好的伤害，不再重复掷暴击或乘装备最终倍率。
                SpellDamage.hitResolved(level, caster, entity, pending.damage());
                knockFrom(point, entity, 1.4);
            });
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, point.x, point.y, point.z,
                    8, 1.0, 0.7, 1.0, 0.05);
            level.playSound(null, point.x, point.y, point.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.15F);
        }
    }

    private static void tickPendingEcho(ServerLevel level, long now) {
        Iterator<PendingEchoDamage> iterator = PENDING_ECHO_DAMAGE.iterator();
        while (iterator.hasNext()) {
            PendingEchoDamage pending = iterator.next();
            if (pending.level() != level || pending.at() > now) continue;
            iterator.remove();
            LivingEntity caster = living(level.getEntity(pending.caster()));
            LivingEntity target = living(level.getEntity(pending.target()));
            if (caster == null || target == null || !target.isAlive()) continue;
            // 数值直接来自已经完成倍率与抗性结算的反应伤害，不再计算元素倍率、暴击或附着。
            SpellDamage.hitResolved(level, caster, target, pending.damage());
            reactionParticles(target, Reaction.CHARGE.color());
        }
    }

    private static void timedModifier(LivingEntity target, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                      ResourceLocation id, double amount, AttributeModifier.Operation operation, int duration) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) return;
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        target.getPersistentData().putLong(modifierKey(id), target.level().getGameTime() + duration);
    }

    /** 金钟罩期间清掉全部降护甲修正：超导 -50% 与熔蚀 -40% 都算破甲。 */
    private static void clearArmorBreakModifiers(LivingEntity target) {
        for (AttributeInstance instance : target.getAttributes().getSyncableAttributes()) {
            instance.removeModifier(SUPERCONDUCT_ARMOR);
            instance.removeModifier(SMELT_ARMOR);
        }
        target.getPersistentData().remove(modifierKey(SUPERCONDUCT_ARMOR));
        target.getPersistentData().remove(modifierKey(SMELT_ARMOR));
    }

    private static void cleanupModifier(LivingEntity target, ResourceLocation id, long now) {
        String key = modifierKey(id);
        long until = target.getPersistentData().getLong(key);
        if (until <= 0 || now < until) return;
        for (AttributeInstance instance : target.getAttributes().getSyncableAttributes()) instance.removeModifier(id);
        target.getPersistentData().remove(key);
    }

    /**
     * 雨靴：把身上已经挂着的泥沼减速摘掉。
     *
     * <p>包含两部分：水 + 土反应给的两条属性修正（移速 -60%、跳跃 -50%），
     * 以及土泥沼法术留下的「土泥沼」效果（它自带 -50% 移速与跳跃压制）。
     * 穿着雨靴时每刻调用一次，所以战斗中临时换上也能立刻生效。</p>
     */
    public static void stripMudSlow(LivingEntity living) {
        if (living == null || living.level().isClientSide) return;
        // 便宜的先判断：没挂过泥沼修正、身上也没有土泥沼效果就什么都不做，不用每刻遍历属性。
        net.minecraft.nbt.CompoundTag data = living.getPersistentData();
        boolean hasMire = data.contains(modifierKey(MIRE_SPEED)) || data.contains(modifierKey(MIRE_JUMP));
        boolean hasMud = living.hasEffect(NewSpellContent.MUD);
        if (!hasMire && !hasMud) return;
        if (hasMire) {
            for (AttributeInstance instance : living.getAttributes().getSyncableAttributes()) {
                if (instance.getModifier(MIRE_SPEED) != null) instance.removeModifier(MIRE_SPEED);
                if (instance.getModifier(MIRE_JUMP) != null) instance.removeModifier(MIRE_JUMP);
            }
            data.remove(modifierKey(MIRE_SPEED));
            data.remove(modifierKey(MIRE_JUMP));
        }
        if (hasMud) living.removeEffect(NewSpellContent.MUD);
    }

    private static void reactionParticles(LivingEntity target, int color) {
        ServerLevel level = server(target);
        Vec3 center = target.getBoundingBox().getCenter();
        Vector3f rgb = new Vector3f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F);
        level.sendParticles(new DustParticleOptions(rgb, 1.2F), center.x, center.y, center.z,
                18, 0.45, 0.55, 0.45, 0.04);
    }

    private static void lightning(ServerLevel level, Vec3 start, Vec3 end, int color) {
        Vector3f rgb = new Vector3f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F);
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.8F);
        for (int i = 0; i <= 18; i++) {
            double t = i / 18.0;
            Vec3 point = start.lerp(end, t).add(0, Math.sin(i * 7.13) * 0.10, Math.cos(i * 5.71) * 0.10);
            level.sendParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    private static void knockFrom(Vec3 center, LivingEntity target, double strength) {
        Vec3 delta = target.position().subtract(center);
        if (delta.horizontalDistanceSqr() < 0.001) delta = new Vec3(0.1, 0, 0);
        target.knockback(strength, -delta.x, -delta.z);
    }

    private static boolean pair(ElementType a, ElementType b, ElementType x, ElementType y) {
        return a == x && b == y || a == y && b == x;
    }
    private static LivingEntity living(Entity entity) { return entity instanceof LivingEntity value ? value : null; }
    private static ServerLevel server(LivingEntity entity) { return (ServerLevel)entity.level(); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, path); }
    private static String modifierKey(ResourceLocation id) { return "DimensionalManaReaction_" + id.getPath(); }
    private ElementReactionSystem() { }
}
