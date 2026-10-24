package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 原版特殊生物的专属技能。
 *
 * <p>技能的触发点分散在受击、死亡、状态施加、实体入场、爆炸与每刻 tick 里；
 * 所有技能都只在服务端结算，并且统一受 {@link MobCombatConfig} 的开关和「仅困难/极限」限制。
 * 技能伤害全部按施法者「当前魔素」实时换算，不再叠固定的基础伤害；
 * 所以把怪物魔素抽干，它的技能也会随之变弱。</p>
 */
public final class MobSkills {
    private static final Map<String, Long> NEXT_READY = new HashMap<>();
    private static final Map<UUID, List<Long>> RETORT_STACKS = new HashMap<>();
    private static final Map<UUID, Long> MANA_REGEN_DEBUFF = new HashMap<>();
    private static final Map<UUID, MarkedCloud> MARKED_CLOUDS = new HashMap<>();
    private static final String TAG_FIREBALL = "mobSkillFireball";
    private static final String TAG_WIND_CHARGE = "mobSkillWindCharge";
    private static final String TAG_INVULNERABLE_UNTIL = "mobInvulnerableUntil";
    private static final String TAG_RAGE_UNTIL = "piglinRageUntil";
    private static final String TAG_LAST_CURSE = "elderGuardianCurseReady";
    /** 铁傀儡精准打击附带的眩晕：1.8 秒。重复命中不叠加，已经被眩晕的目标不再续时。 */
    private static final int GOLEM_PRECISE_STUN_TICKS = 36;
    private static final net.minecraft.resources.ResourceLocation RAGE_MODIFIER_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "piglin_rage_speed");

    private record MarkedCloud(ResourceKey<Level> dimension, UUID cloud, long expiresAt, double mana,
                               UUID owner) { }

    // ---------------------------------------------------------------- 入口

    /** 生物是否处在可以放专属技能的状态（开关 + 难度）。 */
    public static boolean skillsActive(LivingEntity entity) {
        if (!MobCombatConfig.SPECIAL_SKILLS_ENABLED.get()) return false;
        if (entity == null || entity.level().isClientSide) return false;
        MobProfile profile = MobProfile.of(entity.getType());
        if (profile == null || profile.disabled()) return false;
        if (MobCombatConfig.SPECIAL_SKILLS_HARD_ONLY.get()
                && entity.level().getDifficulty() != Difficulty.HARD) return false;
        return true;
    }

    /** 攻击方命中前修正伤害；返回新的伤害值。 */
    public static float modifyAttack(LivingEntity attacker, LivingEntity target, DamageSource source, float amount) {
        if (target == null || source == null || amount <= 0.0F) return amount;
        long now = target.level().getGameTime();
        // 女巫：受到物理攻击时消耗魔素抵消 50% 伤害。
        if (target.getType() == EntityType.WITCH && isPhysical(source) && skillsActive(target)
                && ready(target, "witch_shield", now, 300) && consumeMana(target, 500)) {
            startCooldown(target, "witch_shield", now, 300);
            amount *= 0.5F;
            aura(target, 0xE8D4FF, 14);
        }
        if (attacker == null || attacker == target || !skillsActive(attacker)) return amount;
        EntityType<?> type = attacker.getType();
        boolean melee = isMelee(attacker, source);

        if (type == EntityType.PIGLIN_BRUTE && melee) {
            if (ready(attacker, "brute_cleave", now, 800) && attacker.getRandom().nextFloat() < 0.20F
                    && consumeBattleQi(attacker, 200)) {
                int qi = ManaData.get(attacker).battleQi();
                amount += 2.0F + (float)(qi * 0.0035);
                // 破甲重劈只是把玩家（或玩家类目标）的斗气清零，不抽干其他生物的斗气池。
                if (target instanceof Player) ManaData.setBattleQi(target, 0);
                startCooldown(attacker, "brute_cleave", now, 800);
                burst(attacker, ParticleTypes.CRIT, 18);
            }
        } else if (type == EntityType.IRON_GOLEM && melee) {
            if (ready(attacker, "golem_precise", now, 80) && attacker.getRandom().nextFloat() < 0.80F
                    && consumeMana(attacker, 80)) {
                amount *= 1.75F;
                // 已经处在眩晕里的目标不再续时，免得连续命中把眩晕越拖越长。
                if (attacker.getRandom().nextFloat() < 0.50F
                        && !target.hasEffect(NewSpellContent.STUN)) {
                    NewSpellSystem.control(attacker, target, NewSpellContent.STUN, GOLEM_PRECISE_STUN_TICKS);
                }
                startCooldown(attacker, "golem_precise", now, 80);
                burst(attacker, ParticleTypes.CRIT, 10);
            }
        } else if (type == EntityType.RAVAGER && melee) {
            if (ready(attacker, "ravager_charge", now, 300) && consumeBattleQi(attacker, 200)) {
                amount = ignoreArmorEnchantments(attacker, target, source, amount);
                startCooldown(attacker, "ravager_charge", now, 300);
            }
        } else if (type == EntityType.VINDICATOR && melee) {
            if (ready(attacker, "vindicator_axe", now, 160) && consumeBattleQi(attacker, 100)) {
                amount = ignoreHalfArmor(target, amount);
                startCooldown(attacker, "vindicator_axe", now, 160);
                burst(attacker, ParticleTypes.SWEEP_ATTACK, 4);
            }
        } else if (type == EntityType.PHANTOM) {
            boolean diving = attacker.getDeltaMovement().y < -0.03;
            if (diving && ready(attacker, "phantom_screech", now, 100) && consumeMana(attacker, 50)) {
                ManaData.Snapshot data = ManaData.get(target);
                ManaData.setMana(target, Math.max(0.0, data.mana() - 20.0), true);
                startCooldown(attacker, "phantom_screech", now, 100);
                target.level().playSound(null, target.blockPosition(), SoundEvents.PHANTOM_SWOOP,
                        SoundSource.HOSTILE, 1.0F, 0.7F);
            }
        } else if (type == EntityType.WARDEN && source.is(DamageTypes.SONIC_BOOM)) {
            if (ready(attacker, "warden_shockwave", now, 300) && attacker.getRandom().nextFloat() < 0.75F
                    && consumeMana(attacker, 400)) {
                double mana = ManaData.get(attacker).mana();
                amount += (float)(mana * 0.0185);
                target.addEffect(new MobEffectInstance(GeneratedMod.WARDEN_MARK, 300, 0, false, true, true));
                startCooldown(attacker, "warden_shockwave", now, 300);
            }
        } else if (type == EntityType.BOGGED && source.getDirectEntity() instanceof Arrow arrow
                && arrow.getOwner() == attacker) {
            if (ready(attacker, "bogged_poison_arrow", now, 160) && attacker.getRandom().nextFloat() < 0.45F
                    && consumeMana(attacker, 40)) {
                MANA_REGEN_DEBUFF.put(target.getUUID(), now + 100);
                startCooldown(attacker, "bogged_poison_arrow", now, 160);
                aura(target, 0x7FA84A, 10);
            }
        }
        return amount;
    }

    /** 斗气被一次伤害打空、或被技能消耗到 0 时的生物反应。 */
    public static void onBattleQiBroken(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        long now = entity.level().getGameTime();
        if (entity.getType() == EntityType.BLAZE && skillsActive(entity)
                && ready(entity, "blaze_nova", now, 60) && consumeMana(entity, 100)) {
            startCooldown(entity, "blaze_nova", now, 60);
            if (entity.level() instanceof ServerLevel level) {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                        entity.getBoundingBox().inflate(3.0), other -> other != entity && other.isAlive()
                                && entity.canAttack(other))) {
                    SpellDamage.hitElement(level, entity, other, CombatElement.FIRE.id(), 16.0F);
                    Vec3 push = other.position().subtract(entity.position());
                    if (push.lengthSqr() > 0.0001) other.push(push.x * 0.45, 0.25, push.z * 0.45);
                }
                level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + 0.6, entity.getZ(),
                        60, 2.4, 0.8, 2.4, 0.05);
                level.playSound(null, entity.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.6F);
            }
        } else if (entity.getType() == EntityType.ENDERMAN && skillsActive(entity)) {
            if (entity.isInWaterRainOrBubble()) return;
            if (!ready(entity, "enderman_warp", now, 400) || !consumeMana(entity, 100)) return;
            startCooldown(entity, "enderman_warp", now, 400);
            teleportRandomly(entity);
            entity.setInvulnerable(true);
            entity.getPersistentData().putLong(TAG_INVULNERABLE_UNTIL, now + 40);
            if (entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                        40, 0.6, 1.0, 0.6, 0.25);
            }
        }
    }

    /** 受击落地后：僵尸猪灵的反击怒火。 */
    public static void onHurt(LivingEntity victim, DamageSource source, float damage) {
        if (victim == null || victim.level().isClientSide || damage <= 0.0F) return;
        if (victim.getType() != EntityType.ZOMBIFIED_PIGLIN || !skillsActive(victim)) return;
        long now = victim.level().getGameTime();
        if (!ready(victim, "piglin_rage", now, 500) || !consumeBattleQi(victim, 50)) return;
        startCooldown(victim, "piglin_rage", now, 500);
        AttributeInstance speed = victim.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) {
            AttributeModifier modifier = new AttributeModifier(RAGE_MODIFIER_ID,
                    0.30, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            speed.addOrUpdateTransientModifier(modifier);
            victim.getPersistentData().putLong(TAG_RAGE_UNTIL, now + 200);
        }
        aura(victim, 0xFFA552, 12);
    }

    /** 死亡时：唤魔者的魔力爆炸。 */
    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity == null || entity.level().isClientSide) return;
        if (entity.getType() != EntityType.EVOKER || !skillsActive(entity)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        ManaData.Snapshot data = ManaData.get(entity);
        if (data.mana() <= data.maxMana() * 0.20) return;
        float damage = (float)(data.mana() * 0.01);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, entity.getX(), entity.getY() + 0.8, entity.getZ(),
                1, 0, 0, 0, 0);
        level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4F, 0.8F);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                entity.getBoundingBox().inflate(4.0), other -> other != entity && other.isAlive())) {
            other.hurt(level.damageSources().explosion(entity, entity), damage);
            other.addEffect(new MobEffectInstance(GeneratedMod.MANA_EXHAUSTION, 100, 0, false, true));
        }
    }

    /** 状态被施加时：远古守卫者的远古诅咒。 */
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().value() != MobEffects.DIG_SLOWDOWN.value()) return;
        if (target.level().isClientSide) return;
        // 原版的远古守卫者施加的是 6000 刻挖掘疲劳；用它的刷新时机识别来源。
        if (instance.getDuration() < 5900) return;
        LivingEntity guardian = findNearbyGuardian(target);
        if (guardian == null || !skillsActive(guardian)) return;
        long now = target.level().getGameTime();
        if (guardian.getPersistentData().getLong(TAG_LAST_CURSE) > now) return;
        if (!ready(guardian, "elder_curse", now, 2400) || !consumeMana(guardian, 500)) return;
        if (guardian.getRandom().nextFloat() >= 0.95F) return;
        startCooldown(guardian, "elder_curse", now, 2400);
        guardian.getPersistentData().putLong(TAG_LAST_CURSE, now + 2400);
        target.addEffect(new MobEffectInstance(GeneratedMod.ANCIENT_CURSE, 900, 0, false, true, true));
        aura(target, 0x2F6B5A, 16);
    }

    /** 治疗时：远古诅咒让治疗量减半。 */
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(GeneratedMod.ANCIENT_CURSE)) {
            event.setAmount(event.getAmount() * AncientCurseEffect.HEALING_MULTIPLIER);
        }
    }

    /** 实体入场：恶魂火球与末影龙龙息、微风怪风弹的标记。 */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity instanceof LargeFireball fireball && fireball.getOwner() instanceof LivingEntity owner
                && owner.getType() == EntityType.GHAST && skillsActive(owner)) {
            long now = event.getLevel().getGameTime();
            if (ready(owner, "ghast_fireball", now, 200) && owner.getRandom().nextFloat() < 0.40F
                    && consumeMana(owner, 200)) {
                startCooldown(owner, "ghast_fireball", now, 200);
                fireball.getPersistentData().putBoolean(TAG_FIREBALL, true);
                fireball.getPersistentData().putDouble("mobSkillMana", ManaData.get(owner).mana());
            }
        } else if (entity instanceof AbstractWindCharge windCharge && windCharge.getOwner() instanceof LivingEntity owner
                && owner.getType() == EntityType.BREEZE && skillsActive(owner)) {
            long now = event.getLevel().getGameTime();
            if (ready(owner, "breeze_volley", now, 160) && owner.getRandom().nextFloat() < 0.50F
                    && consumeMana(owner, 30)) {
                startCooldown(owner, "breeze_volley", now, 160);
                windCharge.getPersistentData().putBoolean(TAG_WIND_CHARGE, true);
            }
        } else if (entity instanceof AreaEffectCloud cloud && isDragonBreath(cloud)
                && event.getLevel() instanceof ServerLevel level) {
            EnderDragon dragon = level.getEntitiesOfClass(EnderDragon.class,
                    cloud.getBoundingBox().inflate(48.0)).stream().findFirst().orElse(null);
            if (dragon == null || !skillsActive(dragon)) return;
            long now = level.getGameTime();
            if (!ready(dragon, "dragon_breath", now, 500) || dragon.getRandom().nextFloat() >= 0.25F) return;
            if (!consumeMana(dragon, 10000)) return;
            startCooldown(dragon, "dragon_breath", now, 500);
            cloud.setRadius(cloud.getRadius() * 1.5F);
            double mana = ManaData.get(dragon).mana();
            MARKED_CLOUDS.put(cloud.getUUID(), new MarkedCloud(level.dimension(), cloud.getUUID(),
                    now + Math.max(60, cloud.getDuration()), mana, dragon.getUUID()));
        }
    }

    /** 爆炸：恶魂高压火球的追加火伤、微风怪风弹的魔法抗拒。 */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source == null) return;
        if (source instanceof LargeFireball fireball && fireball.getPersistentData().getBoolean(TAG_FIREBALL)) {
            fireball.getPersistentData().remove(TAG_FIREBALL);
            double mana = fireball.getPersistentData().getDouble("mobSkillMana");
            float damage = (float)(8.0 + mana * 0.0155);
            for (Entity affected : event.getAffectedEntities()) {
                if (affected instanceof LivingEntity target && target.isAlive() && target != fireball.getOwner()) {
                    SpellDamage.hitElement(level, fireball.getOwner() instanceof LivingEntity living ? living : null,
                            target, CombatElement.FIRE.id(), damage);
                }
            }
        } else if (source instanceof AbstractWindCharge charge && charge.getPersistentData().getBoolean(TAG_WIND_CHARGE)) {
            charge.getPersistentData().remove(TAG_WIND_CHARGE);
            // 风弹本身不造成伤害，受影响实体列表可能是空的，直接按爆炸中心扫玩家。
            Vec3 center = event.getExplosion().center();
            for (Player player : level.getEntitiesOfClass(Player.class,
                    new AABB(center, center).inflate(3.0), Player::isAlive)) {
                applyRetort(player, 1);
                SpellCasting.interruptCast(player);
            }
        }
    }

    /** 每只生物每秒的专属技能维护（潜影贝减伤、凋灵风暴、增益清理）。 */
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide || !MobCombatSystem.isManagedMob(entity)) return;
        long now = entity.level().getGameTime();
        CompoundTagSupport.clearInvulnerability(entity, now);
        clearRage(entity, now);
        if (entity.tickCount % 20 != 0) return;
        if (entity.getType() == EntityType.SHULKER && entity instanceof Shulker shulker) {
            // 闭壳每秒消耗 51 点斗气维持 99% 物理减伤；斗气耗尽后不再减免（需要重新充能）。
            if (MobCombatSystem.shulkerClosed(shulker) && skillsActive(shulker)) {
                ManaData.Snapshot data = ManaData.get(shulker);
                if (data.battleQi() >= 51) ManaData.setBattleQi(shulker, data.battleQi() - 51);
            }
        }
        if (entity.getType() == EntityType.WITHER && skillsActive(entity)) witherStorm(entity, now);
    }

    /** 每刻维护反魔法区域与被标记的龙息云。 */
    public static void tickLevel(ServerLevel level) {
        MobCombatSystem.tickLevel(level);
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, MarkedCloud>> iterator = MARKED_CLOUDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MarkedCloud> entry = iterator.next();
            MarkedCloud mark = entry.getValue();
            if (mark.expiresAt() <= now) {
                iterator.remove();
                continue;
            }
            if (!mark.dimension().equals(level.dimension())) continue;
            Entity entity = level.getEntity(mark.cloud());
            if (!(entity instanceof AreaEffectCloud cloud) || !cloud.isAlive()) {
                iterator.remove();
                continue;
            }
            applyAntiMagic(level, cloud, now);
            if (cloud.tickCount % 12 != 0) continue;
            LivingEntity caster = level.getEntity(mark.owner()) instanceof LivingEntity living ? living : null;
            // 实时结算：每次伤害都读施法者「此刻」的魔素，被抽魔就会变弱、回满就会变强。
            double liveMana = caster != null ? ManaData.get(caster).mana() : mark.mana();
            float fire = Math.max(1.0F, (float)(liveMana * 0.0008));
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    cloud.getBoundingBox().inflate(cloud.getRadius()),
                    target -> target.isAlive() && target.position().distanceToSqr(cloud.position())
                            <= (cloud.getRadius() + 0.5) * (cloud.getRadius() + 0.5))) {
                SpellDamage.hitResolvedElement(level, caster, target, CombatElement.FIRE.id(), fire);
                SpellDamage.hitResolvedElement(level, caster, target, CombatElement.ARCANE.id(), fire * 0.07F);
            }
        }
        if (now % 20 == 0) {
            RETORT_STACKS.values().forEach(stacks -> stacks.removeIf(expiresAt -> expiresAt <= now));
            RETORT_STACKS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            MANA_REGEN_DEBUFF.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        if (now % 600 == 0) {
            // 冷却表按 UUID 记录，定期清掉早就结束的条目，避免长期开服越积越多。
            NEXT_READY.entrySet().removeIf(entry -> entry.getValue() < now - 24000L);
        }
    }

    public static void clearAll() {
        NEXT_READY.clear();
        RETORT_STACKS.clear();
        MANA_REGEN_DEBUFF.clear();
        MARKED_CLOUDS.clear();
    }

    // ---------------------------------------------------------------- 魔法抗拒

    /** 当前魔法抗拒层数（每层独立计时，过期自动移除）。 */
    public static int retortStacks(LivingEntity entity) {
        if (entity == null) return 0;
        List<Long> stacks = RETORT_STACKS.get(entity.getUUID());
        if (stacks == null) return 0;
        long now = entity.level().getGameTime();
        stacks.removeIf(expiresAt -> expiresAt <= now);
        return Math.min(MagicRetortEffect.MAX_STACKS, stacks.size());
    }

    public static void applyRetort(LivingEntity entity, int count) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        List<Long> stacks = RETORT_STACKS.computeIfAbsent(entity.getUUID(), ignored -> new ArrayList<>());
        stacks.removeIf(expiresAt -> expiresAt <= now);
        for (int i = 0; i < count && stacks.size() < MagicRetortEffect.MAX_STACKS; i++) {
            stacks.add(now + 60L);
        }
        int size = retortStacks(entity);
        if (size > 0) {
            int longest = 60;
            for (long expiresAt : stacks) longest = Math.max(longest, (int)(expiresAt - now));
            entity.addEffect(new MobEffectInstance(GeneratedMod.MAGIC_RETORT, longest, size - 1, false, true, true));
            level.sendParticles(ParticleTypes.GUST, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    8, 0.4, 0.6, 0.4, 0.05);
        }
    }

    /** 剧毒射击：沼骸命中后降低 30% 魔素恢复速度。 */
    public static double manaRegenMultiplier(LivingEntity entity) {
        if (entity == null) return 1.0;
        Long expiresAt = MANA_REGEN_DEBUFF.get(entity.getUUID());
        if (expiresAt == null) return 1.0;
        if (expiresAt <= entity.level().getGameTime()) {
            MANA_REGEN_DEBUFF.remove(entity.getUUID());
            return 1.0;
        }
        return 0.70;
    }

    // ---------------------------------------------------------------- 技能实现

    private static void witherStorm(LivingEntity wither, long now) {
        if (wither.getHealth() >= wither.getMaxHealth() * 0.5F) return;
        if (!ready(wither, "wither_storm", now, 300)) return;
        ManaData.Snapshot data = ManaData.get(wither);
        if (data.mana() < 5000 || data.exhaustionTicks() > 0) return;
        ManaData.spendMana(wither, 5000);
        startCooldown(wither, "wither_storm", now, 300);
        if (!(wither.level() instanceof ServerLevel level)) return;
        float arcane = (float)(data.mana() * 0.0075);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                wither.getBoundingBox().inflate(16.0), target -> target != wither && target.isAlive())) {
            SpellDamage.hitElement(level, wither, target, CombatElement.ARCANE.id(), arcane);
            ElementReactionSystem.applyArcane(wither, target);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 1, false, true));
            ManaData.Snapshot targetData = ManaData.get(target);
            ManaData.setMana(target, targetData.mana() * 0.80, true);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, wither.getX(), wither.getY() + 1.5, wither.getZ(),
                240, 10.0, 4.0, 10.0, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, wither.getX(), wither.getY() + 1.5, wither.getZ(),
                120, 8.0, 3.0, 8.0, 0.03);
        level.playSound(null, wither.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private static void applyAntiMagic(ServerLevel level, AreaEffectCloud cloud, long now) {
        MobCombatSystem.addAntiMagicZone(level, cloud.position(), cloud.getRadius() + 2.0, 40);
        for (Player player : level.getEntitiesOfClass(Player.class,
                cloud.getBoundingBox().inflate(cloud.getRadius() + 2.0), Player::isAlive)) {
            if (now % 20 != 0) continue;
            SpellCasting.interruptCast(player);
            List<MobEffectInstance> beneficial = new ArrayList<>();
            for (MobEffectInstance instance : player.getActiveEffects()) {
                if (instance.getEffect().value().isBeneficial()) beneficial.add(instance);
            }
            for (MobEffectInstance instance : beneficial) player.removeEffect(instance.getEffect());
        }
    }

    private static boolean isDragonBreath(AreaEffectCloud cloud) {
        return cloud.getParticle() != null && cloud.getParticle().getType() == ParticleTypes.DRAGON_BREATH;
    }

    private static LivingEntity findNearbyGuardian(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return null;
        return level.getEntitiesOfClass(net.minecraft.world.entity.monster.ElderGuardian.class,
                        target.getBoundingBox().inflate(64.0), guardian -> guardian.isAlive())
                .stream().findFirst().orElse(null);
    }

    private static void teleportRandomly(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        for (int attempt = 0; attempt < 24; attempt++) {
            double x = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 64.0;
            double y = entity.getY() + (entity.getRandom().nextInt(32) - 16);
            double z = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 64.0;
            if (y < level.getMinBuildHeight() + 1 || y > level.getMaxBuildHeight() - 2) continue;
            Vec3 pos = new Vec3(x, y, z);
            AABB box = entity.getBoundingBox().move(pos.subtract(entity.position()));
            if (!level.noCollision(entity, box)) continue;
            if (!level.isEmptyBlock(net.minecraft.core.BlockPos.containing(x, y, z))) continue;
            if (!level.isEmptyBlock(net.minecraft.core.BlockPos.containing(x, y + 1, z))) continue;
            entity.teleportTo(x, y, z);
            return;
        }
    }

    private static float ignoreArmorEnchantments(LivingEntity attacker, LivingEntity target, DamageSource source,
                                                 float amount) {
        if (!(target.level() instanceof ServerLevel level)) return amount;
        float protection = EnchantmentHelper.getDamageProtection(level, target, source);
        if (protection <= 0.0F) return amount;
        double reduction = Math.min(0.80, protection / 25.0);
        return amount / (float)(1.0 - reduction);
    }

    private static float ignoreHalfArmor(LivingEntity target, float amount) {
        double armor = target.getArmorValue();
        double toughness = target.getAttributes().hasAttribute(Attributes.ARMOR_TOUGHNESS)
                ? target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0.0;
        double reduction = Math.min(20.0, Math.max(armor / 5.0, armor - amount / (2.0 + toughness / 4.0))) / 25.0;
        return amount * (float)(1.0 + reduction * 0.5);
    }

    private static boolean isMelee(LivingEntity attacker, DamageSource source) {
        if (source.getDirectEntity() != attacker) return false;
        return !source.is(DamageTypeTags.IS_PROJECTILE);
    }

    private static boolean isPhysical(DamageSource source) {
        return CombatElement.kindOf(source) == CombatElement.Kind.PHYSICAL;
    }

    // ---------------------------------------------------------------- 通用工具

    private static String key(LivingEntity entity, String skill) {
        return entity.getUUID() + "|" + skill;
    }

    private static boolean ready(LivingEntity entity, String skill, long now, int cooldownTicks) {
        Long next = NEXT_READY.get(key(entity, skill));
        return next == null || now >= next;
    }

    private static void startCooldown(LivingEntity entity, String skill, long now, int cooldownTicks) {
        NEXT_READY.put(key(entity, skill), now + cooldownTicks);
    }

    private static boolean consumeMana(LivingEntity entity, double amount) {
        ManaData.Snapshot data = ManaData.get(entity);
        if (data.exhaustionTicks() > 0 || data.mana() < amount) return false;
        ManaData.spendMana(entity, amount);
        return true;
    }

    private static boolean consumeBattleQi(LivingEntity entity, int amount) {
        ManaData.Snapshot data = ManaData.get(entity);
        if (data.battleQi() < amount) return false;
        ManaData.setBattleQi(entity, data.battleQi() - amount);
        return true;
    }

    private static void aura(LivingEntity entity, int color, int count) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                        new org.joml.Vector3f((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F), 1.0F),
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), count, 0.5, 0.5, 0.5, 0.02);
    }

    private static void burst(LivingEntity entity, net.minecraft.core.particles.SimpleParticleType particle, int count) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        level.sendParticles(particle, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                count, 0.6, 0.6, 0.6, 0.1);
    }

    private static void clearRage(LivingEntity entity, long now) {
        long until = entity.getPersistentData().getLong(TAG_RAGE_UNTIL);
        if (until <= 0 || now < until) return;
        AttributeInstance speed = entity.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) speed.removeModifier(RAGE_MODIFIER_ID);
        entity.getPersistentData().remove(TAG_RAGE_UNTIL);
    }

    /** 小型辅助：把「无敌帧到期」也写进持久数据，重进存档后仍然会自动解除。 */
    private static final class CompoundTagSupport {
        private static void clearInvulnerability(LivingEntity entity, long now) {
            long until = entity.getPersistentData().getLong(TAG_INVULNERABLE_UNTIL);
            if (until <= 0) return;
            if (now >= until) {
                entity.getPersistentData().remove(TAG_INVULNERABLE_UNTIL);
                entity.setInvulnerable(false);
            }
        }
    }

    // ---------------------------------------------------------------- 技能监控

    /** 一只生物的主技能冷却状态；专业鉴定眼镜的 HUD 用它显示「已就绪 / CD: x.x s」。 */
    public record SkillStatus(String id, int totalTicks, int remainingTicks) {
        public boolean ready() { return remainingTicks <= 0; }
    }

    private record MainSkill(String id, int cooldownTicks) { }

    /**
     * 每只特殊生物只报一个最能代表它的主动技能；被动型（冷却写 0）永远显示为就绪。
     * 技能名本身走语言文件（{@code skill.dimensional_mana.<id>}），这里只报冷却。
     */
    private static final Map<EntityType<?>, MainSkill> MAIN_SKILLS = Map.<EntityType<?>, MainSkill>ofEntries(
            Map.entry(EntityType.WITHER, new MainSkill("wither_storm", 300)),
            Map.entry(EntityType.ENDER_DRAGON, new MainSkill("dragon_breath", 500)),
            Map.entry(EntityType.BLAZE, new MainSkill("blaze_nova", 60)),
            Map.entry(EntityType.GHAST, new MainSkill("ghast_fireball", 200)),
            Map.entry(EntityType.PIGLIN_BRUTE, new MainSkill("brute_cleave", 800)),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, new MainSkill("piglin_rage", 500)),
            Map.entry(EntityType.ENDERMAN, new MainSkill("enderman_warp", 400)),
            Map.entry(EntityType.SHULKER, new MainSkill("shulker_shell", 0)),
            Map.entry(EntityType.WITCH, new MainSkill("witch_shield", 300)),
            Map.entry(EntityType.EVOKER, new MainSkill("evoker_death_burst", 0)),
            Map.entry(EntityType.RAVAGER, new MainSkill("ravager_charge", 300)),
            Map.entry(EntityType.VINDICATOR, new MainSkill("vindicator_axe", 160)),
            Map.entry(EntityType.PHANTOM, new MainSkill("phantom_screech", 100)),
            Map.entry(EntityType.BREEZE, new MainSkill("breeze_volley", 160)),
            Map.entry(EntityType.BOGGED, new MainSkill("bogged_poison_arrow", 160)),
            Map.entry(EntityType.IRON_GOLEM, new MainSkill("golem_precise", 80)),
            Map.entry(EntityType.WARDEN, new MainSkill("warden_shockwave", 300)),
            Map.entry(EntityType.ELDER_GUARDIAN, new MainSkill("elder_curse", 2400))
    );

    /** 目标的主技能状态；没有专属技能的生物返回 null。 */
    public static SkillStatus mainSkill(LivingEntity entity) {
        if (entity == null) return null;
        MainSkill slot = MAIN_SKILLS.get(entity.getType());
        if (slot == null) return null;
        long now = entity.level().getGameTime();
        Long next = NEXT_READY.get(key(entity, slot.id()));
        int remaining = next == null ? 0 : (int)Math.max(0L, next - now);
        return new SkillStatus(slot.id(), slot.cooldownTicks(), remaining);
    }

    private MobSkills() {}
}
