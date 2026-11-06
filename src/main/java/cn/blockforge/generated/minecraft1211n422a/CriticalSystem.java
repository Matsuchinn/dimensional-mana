package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 生物暴击系统：所有生物的近战命中都会按世界难度掷一次暴击。
 *
 * <p>概率与倍率来自 {@link CriticalConfig}：和平 0% / 1.0 倍，简单 5% / 1.25 倍，
 * 普通 10% / 1.5 倍，困难 15% / 1.75 倍。判定放在 {@code LivingIncomingDamageEvent}，
 * 也就是护甲、抗性与斗气减伤之前，所以暴击确实是「这一下打得更重」，
 * 而不是绕开防御的额外伤害。</p>
 *
 * <p>玩家的跳劈（原版暴击）照旧生效，本系统只负责补上它没有的那部分：
 * 玩家的平地近战也有难度概率暴击、生物打玩家会暴击。</p>
 *
 * <p>r333：玩家射出的<b>箭矢与投掷三叉戟</b>也吃这套判定，物理暴击率、物理暴击伤害、
 * 处决必暴都照算——以前弹射物被「只处理贴脸命中」这一条挡在外面，弓弩手堆了暴击却毫无效果。
 * 生物放的箭仍然不暴击，避免骷髅随难度白嫖一份爆发。法术伤害也不在这里结算，
 * 法术暴击走 {@link SpellAttributes} 自己那条线。</p>
 *
 * <p>本类同时记录「这一下是不是暴击、打了多少」，供饰品读取：
 * 危险手镯的重掷、无名护符的暴击伤害抗性、力量护符的力量都靠这份记录。</p>
 */
public final class CriticalSystem {
    /** 记录只保留很短时间：受击结算、饰品触发都在同一刻到下一两刻内完成。 */
    private static final int RECORD_TICKS = 40;
    private static final Map<UUID, Hit> HITS = new HashMap<>();

    /** 一次命中的结果；victim 为键，attacker 用于击杀回血之类的反向查找。 */
    public record Hit(long tick, boolean critical, float amount, UUID attacker, boolean melee, double multiplier) { }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !CriticalConfig.ENABLED.get()) return;
        DamageSource source = event.getSource();
        // 必杀伤害不参与暴击：把它再乘 1.25~1.75 倍会直接溢出成无穷大，后面的 0 倍率就会算出 NaN。
        if (LethalDamage.isLethal(source, event.getAmount())) return;
        // 刻蚀真伤是「绕过一切」的定值伤害，不参与暴击。
        if (MagicEtchingSystem.isTrueDamage(source)) return;
        // 法术有自己的暴击（法术暴击率 / 法术暴击伤害，见 SpellAttributes），物理暴击不掺和。
        // 这条以前漏了：法术的伤害源同样是「施法者本人命中」，于是法术会顺带吃一份物理暴击。
        if (SpellDamage.isSpell(source)) return;
        Entity direct = source.getDirectEntity();
        Entity owner = source.getEntity();
        LivingEntity attacker;
        boolean melee;
        if (direct instanceof LivingEntity living && direct == owner) {
            // 贴脸命中：攻击者就是命中实体。
            attacker = living;
            melee = true;
        } else if (direct instanceof AbstractArrow && owner instanceof Player shooter) {
            // 弓弩箭矢 / 投掷三叉戟：命中的是弹射物本身，攻击者要从射手回推，
            // 这样物理暴击率、物理暴击伤害（以及处决必暴）才会算在箭矢上。
            // 只认玩家射出的弹射物：生物放箭维持原样，不给骷髅随难度白嫖一份暴击。
            attacker = shooter;
            melee = false;
        } else {
            return;
        }
        if (attacker == victim) return;
        float amount = event.getAmount();
        if (amount <= 0.0F) return;
        boolean playerAttacker = attacker instanceof Player;
        if (playerAttacker && !CriticalConfig.PLAYER_ATTACKS_CAN_CRIT.get()) return;

        long now = victim.level().getGameTime();
        Difficulty difficulty = victim.level().getDifficulty();
        double chance = chance(difficulty, attacker);
        double multiplier = multiplier(difficulty, attacker);

        // 玩家跳劈由原版自己乘过 1.5 倍，这里只补记「这是暴击」，不重复乘（箭矢没有这一说）。
        boolean vanillaCrit = melee && playerAttacker && vanillaPlayerCritical((Player)attacker);
        boolean critical = vanillaCrit;
        boolean fromSystem = false;
        // 「处决」：目标血量低于阈值时这一下必定暴击，不看难度概率。
        if (!critical && playerAttacker && attacker instanceof net.minecraft.server.level.ServerPlayer executor) {
            double threshold = QiProgression.executeThreshold(executor);
            if (threshold > 0.0 && victim.getHealth() < victim.getMaxHealth() * threshold) {
                critical = true;
                fromSystem = true;
            }
        }
        if (!critical) {
            critical = victim.getRandom().nextDouble() < chance;
            fromSystem = critical;
        }
        if (!critical) {
            // 风险手镯：没暴击就再掷一次。打人 7 刻冷却，挨打 14 刻冷却，互不干扰。
            if (playerAttacker && RelicEffects.riskReroll(attacker, false)
                    && victim.getRandom().nextDouble() < chance) {
                critical = true;
                fromSystem = true;
            } else if (victim instanceof Player player && RelicEffects.riskReroll(player, true)
                    && victim.getRandom().nextDouble() < chance) {
                critical = true;
                fromSystem = true;
            }
        }

        float finalAmount = amount;
        double appliedMultiplier = 1.0;
        if (fromSystem || vanillaCrit) {
            double applied = fromSystem ? multiplier
                    : 1.0 + Math.max(0.0, SpellAttributes.value(attacker,
                    SpellAttributes.PHYSICAL_CRIT_DAMAGE, 0.0));
            // 无名护符：受暴击时减免 25% 的暴击加成部分。
            if (victim instanceof Player player && RelicEffects.worn(player, RelicEffects.NAMELESS_CHARM)) {
                applied = 1.0 + (applied - 1.0) * 0.75;
            }
            appliedMultiplier = applied;
            finalAmount = (float)(amount * applied);
            event.setAmount(finalAmount);
            if (CriticalConfig.SHOW_EFFECTS.get() && victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6,
                        victim.getZ(), 12, 0.35, 0.35, 0.35, 0.16);
                level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS, 0.9F, 1.0F);
            }
        }

        Hit previous = HITS.get(victim.getUUID());
        // 同一刻内多次命中时保留伤害更高的那份，避免饰品读到被无敌帧吞掉的那一下。
        if (previous == null || previous.tick() != now || finalAmount >= previous.amount()) {
            HITS.put(victim.getUUID(), new Hit(now, critical, finalAmount, attacker.getUUID(), melee, appliedMultiplier));
        }
        // 力量护符只认近战暴击，箭矢暴击不触发它。
        if (critical && melee) RelicEffects.onMeleeCritical(attacker, victim, finalAmount);
        prune(now);
    }

    /** 最近的命中；超出记录时间窗口就当作没有。 */
    public static Hit hit(LivingEntity victim) {
        if (victim == null) return null;
        Hit hit = HITS.get(victim.getUUID());
        if (hit == null) return null;
        return victim.level().getGameTime() - hit.tick() > RECORD_TICKS ? null : hit;
    }

    /** 这一下是不是暴击（只认最近一刻，避免旧记录误伤）。 */
    public static boolean wasCritical(LivingEntity victim) {
        Hit hit = hit(victim);
        return hit != null && hit.critical();
    }

    /** 碎冰等需要独立暴击的额外伤害使用最近一次物理命中的暴击倍率。 */
    public static float recentCriticalMultiplier(LivingEntity victim) {
        Hit hit = hit(victim);
        return hit == null || !hit.critical() ? 1.0F : (float) hit.multiplier();
    }

    public static double chance(Difficulty difficulty) {
        return chance(difficulty, null);
    }

    public static double chance(Difficulty difficulty, LivingEntity attacker) {
        double base = switch (difficulty) {
            case PEACEFUL -> CriticalConfig.PEACE_CHANCE.get();
            case EASY -> CriticalConfig.EASY_CHANCE.get();
            case NORMAL -> CriticalConfig.NORMAL_CHANCE.get();
            case HARD -> CriticalConfig.HARD_CHANCE.get();
        };
        double affix = attacker == null ? 0.0 : SpellAttributes.value(attacker, SpellAttributes.PHYSICAL_CRIT_CHANCE, 0.0);
        return Math.max(0.0, Math.min(1.0, base + affix));
    }

    public static double multiplier(Difficulty difficulty) {
        return multiplier(difficulty, null);
    }

    public static double multiplier(Difficulty difficulty, LivingEntity attacker) {
        double base = switch (difficulty) {
            case PEACEFUL -> CriticalConfig.PEACE_DAMAGE.get();
            case EASY -> CriticalConfig.EASY_DAMAGE.get();
            case NORMAL -> CriticalConfig.NORMAL_DAMAGE.get();
            case HARD -> CriticalConfig.HARD_DAMAGE.get();
        };
        double affix = attacker == null ? 0.0 : SpellAttributes.value(attacker, SpellAttributes.PHYSICAL_CRIT_DAMAGE, 0.0);
        return Math.max(1.0, base + affix);
    }

    /**
     * 原版跳劈暴击的判定条件（与 {@code Player#crit} 一致）：
     * 正在下落、不在攀爬/水里/骑乘、没失明、也不是疾跑。
     */
    private static boolean vanillaPlayerCritical(Player player) {
        return player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable()
                && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger() && !player.isSprinting();
    }

    private static void prune(long now) {
        if (HITS.size() < 128) return;
        Iterator<Map.Entry<UUID, Hit>> iterator = HITS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().tick() > RECORD_TICKS) iterator.remove();
        }
    }

    private CriticalSystem() { }
}
