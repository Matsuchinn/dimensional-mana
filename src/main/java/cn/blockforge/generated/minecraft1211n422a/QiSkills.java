package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 三项斗气能力（拔刀斩 / 瞬步 / 剑气附加）的服务端结算，以及斗气技能的击杀掉落与自然恢复。
 *
 * <p>斗气技能没有冷却，只按斗气本身收费：够就放，不够就提示。所有判定都在服务端，
 * 客户端只发「施放」动作与轮盘选择。</p>
 */
public final class QiSkills {
    /** 拔刀斩收刀蓄力：0.5 秒。 */
    public static final int IAI_CHARGE_TICKS = 10;
    /** 拔刀斩的扇形半径：前方 5 格。 */
    public static final double IAI_RANGE = 5.0;
    /** 扇形张角的一半（±60°，合计 120°）。 */
    public static final double IAI_HALF_ANGLE = Math.toRadians(60.0);
    /** 拔刀斩击飞目标的水平 / 垂直初速度。 */
    private static final double IAI_KNOCKBACK = 0.85;
    /** 瞬步距离。 */
    public static final double FLASH_STEP_DISTANCE = 6.0;
    /** 剑气附加的持续时间：10 秒内打出下一刀就生效。 */
    public static final int SWORD_QI_TICKS = 200;
    /** 完美格挡后击杀破绽生物掉技能书的概率。 */
    public static final float SKILL_BOOK_CHANCE = 0.05F;

    private static final ResourceLocation SWORDSMAN_SPEED_ID = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "swordsman_parry_speed");
    private static final AttributeModifier SWORDSMAN_SPEED = new AttributeModifier(SWORDSMAN_SPEED_ID, 0.25,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private static final Map<UUID, Long> PENDING_IAI = new HashMap<>();
    private static final Map<UUID, Long> PENDING_SWORD_QI = new HashMap<>();
    /** 剑士护符的完美格挡加速到期时刻。 */
    private static final Map<UUID, Long> SPEED_UNTIL = new HashMap<>();

    /** 键盘 / 轮盘发出的施放请求：读当前选中的能力再交给 {@link #cast}。 */
    public static void castSelected(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return;
        if (NewSpellSystem.immobilized(player)) return;
        QiProgression.Ability ability = QiProgression.selectedAbility(player);
        if (ability == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.qi_no_ability"), true);
            return;
        }
        cast(player, ability);
    }

    /** 真正施放：先验收斗气，再按技能分派。返回是否放出来了。 */
    public static boolean cast(ServerPlayer player, QiProgression.Ability ability) {
        if (player == null || ability == null || player.level().isClientSide) return false;
        if (NewSpellSystem.immobilized(player)) return false;
        ManaData.Snapshot qi = ManaData.get(player);
        if (qi.battleQi() < ability.cost()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.qi_insufficient", ability.cost(), qi.battleQi()), true);
            return false;
        }
        switch (ability) {
            case IAI_SLASH -> startIai(player);
            case FLASH_STEP -> flashStep(player);
            case SWORD_QI -> attachSwordQi(player);
        }
        return true;
    }

    // ---------------------------------------------------------------- 拔刀斩

    private static void startIai(ServerPlayer player) {
        ManaData.spendBattleQiQuiet(player, QiProgression.Ability.IAI_SLASH.cost());
        PENDING_IAI.put(player.getUUID(), player.level().getGameTime() + IAI_CHARGE_TICKS);
        if (player.level() instanceof ServerLevel level) {
            // 收刀入鞘：短促的金属摩擦声，音调压得比出刀低。
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.7F, 0.65F);
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.1, player.getZ(),
                    18, 0.25, 0.35, 0.25, 0.6);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.qi_iai_charge"), true);
    }

    /** 0.5 秒蓄力走完：按玩家朝向划出 120° 扇形剑气。 */
    private static void executeIai(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) return;
        Vec3 origin = player.position();
        Vec3 forward = forwardOf(player);
        float damage = (float)((0.8 * player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 2.0)
                * QiProgression.swordAuraMultiplier(player));
        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(IAI_RANGE + 1.5),
                candidate -> candidate != player && candidate.isAlive() && NewSpellSystem.enemy(player, candidate))) {
            Vec3 delta = target.position().subtract(origin);
            Vec3 flat = new Vec3(delta.x, 0.0, delta.z);
            double distance = flat.length();
            if (distance > IAI_RANGE + target.getBbWidth() * 0.5) continue;
            if (distance > 0.01) {
                double dot = Math.max(-1.0, Math.min(1.0, flat.normalize().dot(forward)));
                if (Math.acos(dot) > IAI_HALF_ANGLE) continue;
            }
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                Vec3 push = new Vec3(delta.x, 0.0, delta.z);
                push = push.lengthSqr() < 1.0E-6 ? forward : push.normalize();
                target.setDeltaMovement(push.x * IAI_KNOCKBACK, IAI_KNOCKBACK, push.z * IAI_KNOCKBACK);
                target.hurtMarked = true;
                hits++;
            }
        }
        sweepVisuals(level, player, origin, forward, hits);
        if (hits > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.qi_iai_hit", hits), true);
        }
    }

    /** 扇形斩的视觉：三层横扫粒子叠出刀光，再沿扇形铺一圈细碎的剑芒。 */
    private static void sweepVisuals(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 forward, int hits) {
        Vec3 center = origin.add(forward.scale(IAI_RANGE * 0.5)).add(0.0, 1.0, 0.0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 3 + Math.min(3, hits),
                0.5, 0.3, 0.5, 0.0);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 24, 1.2, 0.4, 1.2, 0.18);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, center.x, center.y, center.z, 16, 1.0, 0.3, 1.0, 0.1);
        for (int step = 0; step <= 12; step++) {
            double angle = -IAI_HALF_ANGLE + (IAI_HALF_ANGLE * 2.0) * step / 12.0;
            Vec3 direction = forward.yRot((float)-angle);
            for (double radius = 1.5; radius <= IAI_RANGE; radius += 1.2) {
                Vec3 at = origin.add(direction.scale(radius)).add(0.0, 1.0, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.85F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.45F, 1.9F);
    }

    // ---------------------------------------------------------------- 瞬步

    private static void flashStep(ServerPlayer player) {
        ManaData.spendBattleQiQuiet(player, QiProgression.Ability.FLASH_STEP.cost());
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 wanted = eye.add(look.scale(FLASH_STEP_DISTANCE));
        // 墙和地面会拦住瞬步：碰到就停在障碍前 0.4 格，不会穿进方块里。
        HitResult clip = level.clip(new ClipContext(eye, wanted, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 destination = clip.getType() == HitResult.Type.MISS ? wanted
                : clip.getLocation().subtract(look.scale(0.4));
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.3, 0.6, 0.3, 0.25);
        player.teleportTo(destination.x, destination.y - player.getEyeHeight(), destination.z);
        player.resetFallDistance();
        // 落地后脚下再炸一圈残影，告诉周围玩家「人已经换位置了」。
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
                16, 0.35, 0.15, 0.35, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.3, 0.5, 0.3, 0.04);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.45F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.7F);
    }

    // ---------------------------------------------------------------- 剑气附加

    private static void attachSwordQi(ServerPlayer player) {
        ManaData.spendBattleQiQuiet(player, QiProgression.Ability.SWORD_QI.cost());
        PENDING_SWORD_QI.put(player.getUUID(), player.level().getGameTime() + SWORD_QI_TICKS);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.1, player.getZ(),
                    26, 0.35, 0.5, 0.35, 0.5);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.1, player.getZ(),
                    10, 0.3, 0.4, 0.3, 0.05);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.qi_sword_qi_ready"), true);
    }

    /**
     * 近战命中后结算剑气附加：下一刀附加一段无视护甲的剑气伤害，然后状态消耗掉。
     *
     * <p>只在真正打死人的那一刀之后还没结算时补一次，不会顺延到以后的攻击。</p>
     */
    public static void onMeleeHit(ServerPlayer player, LivingEntity victim, float taken) {
        if (player == null || victim == null || !victim.isAlive() || player.level().isClientSide) return;
        Long until = PENDING_SWORD_QI.get(player.getUUID());
        if (until == null) return;
        if (player.level().getGameTime() > until) {
            PENDING_SWORD_QI.remove(player.getUUID());
            return;
        }
        PENDING_SWORD_QI.remove(player.getUUID());
        float damage = (float)((1.5 * player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 2.0)
                * QiProgression.swordAuraMultiplier(player));
        // 无视护甲的物理段：走模组的穿透伤害类型，同样无视受击冷却，不会被前一刀的无敌帧吞掉。
        SpellDamage.hitResolved(player.level(), player, victim, damage);
        if (player.level() instanceof ServerLevel level) {
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0.0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 18, 0.4, 0.4, 0.4, 0.2);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 0.8F, 1.25F);
        }
    }

    // ---------------------------------------------------------------- 击杀、格挡与恢复

    /** 玩家击杀生物：给斗气经验；如果这只生物正处在完美格挡打出的破绽里，还有概率掉一本技能书。 */
    public static void onKill(ServerPlayer player, LivingEntity victim) {
        if (player == null || victim == null) return;
        QiProgression.addExperience(player, QiProgression.KILL_EXPERIENCE);
        if (!victim.hasEffect(NewSpellContent.PARRY_VULNERABLE)) return;
        if (player.getRandom().nextFloat() >= SKILL_BOOK_CHANCE) return;
        QiProgression.Ability[] abilities = QiProgression.Ability.values();
        QiProgression.Ability reward = abilities[player.getRandom().nextInt(abilities.length)];
        var holder = GeneratedMod.MOD_ITEMS.get(reward.manualId());
        if (holder == null) return;
        ItemStack book = new ItemStack(holder.get());
        if (player.level() instanceof ServerLevel level) {
            ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY() + 0.5, victim.getZ(), book);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            level.sendParticles(ParticleTypes.ENCHANT, victim.getX(), victim.getY() + 0.8, victim.getZ(),
                    24, 0.4, 0.4, 0.4, 0.5);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.qi_skill_book_drop", book.getHoverName()), false);
    }

    /** 完美格挡：给斗气经验，并把剑士护符的 2 秒加速挂上。 */
    public static void onPerfectParry(ServerPlayer player) {
        if (player == null) return;
        QiProgression.addExperience(player, QiProgression.PERFECT_PARRY_EXPERIENCE);
        if (!SwordsmanCharmItem.worn(player)) return;
        SPEED_UNTIL.put(player.getUUID(), player.level().getGameTime() + SwordsmanCharmItem.SPEED_TICKS);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(SWORDSMAN_SPEED);
    }

    /** 每刻维护：拔刀斩的蓄力、剑士护符的加速到期、剑气的过期标记。 */
    public static void tick(ServerPlayer player) {
        if (player == null) return;
        long now = player.level().getGameTime();
        Long iai = PENDING_IAI.get(player.getUUID());
        if (iai != null && (now >= iai || !player.isAlive())) {
            PENDING_IAI.remove(player.getUUID());
            if (player.isAlive()) executeIai(player);
        }
        Long speedUntil = SPEED_UNTIL.get(player.getUUID());
        if (speedUntil != null && now >= speedUntil) {
            SPEED_UNTIL.remove(player.getUUID());
            removeSwordsmanSpeed(player);
        }
        Long swordQi = PENDING_SWORD_QI.get(player.getUUID());
        if (swordQi != null && now > swordQi) PENDING_SWORD_QI.remove(player.getUUID());
        tickRegen(player, now);
    }

    /**
     * 自然斗气恢复：只有戴着剑士护符才有，1.0 点/秒，并且同步消耗等量魔素（1 点斗气 = 1 点魔素）。
     *
     * <p>「回气」不再参与自然恢复，它只在玩家凝聚斗气时生效（见 {@link ManaData#condenseBattleQi}）。
     * 魔素不够付的那一秒不恢复，也不把额度攒到以后；斗气溃散期间同样不恢复。</p>
     */
    private static void tickRegen(ServerPlayer player, long now) {
        if (now % 20L != 0L) return;
        if (!SwordsmanCharmItem.worn(player)) return;
        if (player.hasEffect(GeneratedMod.BATTLE_QI_COLLAPSE)) return;
        ManaData.Snapshot data = ManaData.get(player);
        if (data.battleQi() >= data.maxBattleQi()) return;
        var root = player.getPersistentData().getCompound(ManaData.ROOT_TAG);
        double carry = root.getDouble("qiRegenCarry") + SwordsmanCharmItem.REGEN_PER_SECOND;
        int whole = (int)Math.floor(carry);
        root.putDouble("qiRegenCarry", carry - whole);
        if (whole <= 0) return;
        // 每恢复 1 点斗气就要付 1 点魔素；付不起的部分这一秒直接作废，不会欠着下次一起补。
        int affordable = whole;
        if (ManaCheats.manaCostEnabled(player)) {
            affordable = Math.min(whole, (int)Math.floor(data.mana()));
        }
        if (affordable <= 0) return;
        ManaData.drainMana(player, affordable);
        ManaData.setBattleQi(player, Math.min(data.maxBattleQi(), data.battleQi() + affordable));
    }

    /** 这一击是不是带着剑气附加（界面 / HUD 显示用）。 */
    public static boolean swordQiReady(ServerPlayer player) {
        Long until = PENDING_SWORD_QI.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    /** 剑士护符的加速是否还在生效。 */
    public static boolean speedBoosted(ServerPlayer player) {
        Long until = SPEED_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    /** 掉线、换维度、死亡时清掉只存在内存里的临时状态。 */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        PENDING_IAI.remove(id);
        PENDING_SWORD_QI.remove(id);
        SPEED_UNTIL.remove(id);
        removeSwordsmanSpeed(player);
    }

    private static void removeSwordsmanSpeed(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SWORDSMAN_SPEED_ID) != null) speed.removeModifier(SWORDSMAN_SPEED_ID);
    }

    /** 玩家朝向的水平单位向量；视线完全垂直时退回 yaw 解算，保证扇形判定有个方向。 */
    private static Vec3 forwardOf(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() > 1.0E-4) return flat.normalize();
        double yaw = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private QiSkills() {}
}
