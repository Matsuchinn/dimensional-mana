package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 狂暴啃咬（主动技能）：目标贴到 1 格左右时，消耗 50 魔素咬住对方 5 秒（100 刻），
 * 每 10 刻咬一口：4 点毒属性 + 8 点物理；每口 50% 上 2 秒中毒 I、50% 上 2 秒凋零，
 * 造成伤害的那一口给自己回 2 点生命并从目标魔素池里抽 2 点。咬满（或目标脱逃）后进入 12 秒冷却。
 *
 * <p>r238：啃咬期间同步 {@link CrystallizedZombie#setBiting(boolean)} 让客户端放
 * 「头部上下啃咬」动画，每口都响一次啃咬音效，并挂上 +50% 移速的临时修饰符——
 * 只有被目标拉开一点、需要迈步追上去时才看得出来。</p>
 */
public final class CrystallizedZombieBiteGoal extends Goal {
    /** 中心距在这个范围内算「目标距离在 1 格内」（两只 0.6 宽的身体贴脸时中心距约 1.2）。 */
    private static final double ENGAGE_RANGE_SQR = 1.7F * 1.7F;
    /** 咬住中被拉开到这么远就松口。 */
    private static final double BREAK_RANGE_SQR = 3.2F * 3.2F;
    /** 咬住时距离超过这个值才迈步贴回去（贴脸时该钉住就钉住）。 */
    private static final double KEEP_RANGE_SQR = 1.45F * 1.45F;

    private final CrystallizedZombie zombie;
    private LivingEntity target;
    private int ticksRemaining;

    public CrystallizedZombieBiteGoal(CrystallizedZombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (zombie.isCrystalShock()) return false;
        long now = zombie.level().getGameTime();
        if (now < zombie.biteCooldownUntil()) return false;
        LivingEntity victim = zombie.getTarget();
        if (victim == null || !victim.isAlive() || victim.level() != zombie.level()) return false;
        if (zombie.distanceToSqr(victim) > ENGAGE_RANGE_SQR) return false;
        if (ManaData.get(zombie).mana() < CrystallizedZombie.BITE_MANA_COST) return false;
        return zombie.canAttack(victim);
    }

    @Override
    public void start() {
        target = zombie.getTarget();
        // 一次啃咬的入场费：50 魔素；扣不到（被别人抢先抽干）就进个短冷却回头再来。
        if (target == null || !ManaData.spendMana(zombie, CrystallizedZombie.BITE_MANA_COST)) {
            zombie.setBiteCooldownUntil(zombie.level().getGameTime() + 40L);
            target = null;
            ticksRemaining = 0;
            return;
        }
        ticksRemaining = CrystallizedZombie.BITE_TOTAL_TICKS;
        zombie.getNavigation().stop();
        // 抬起双臂的僵尸攻击姿态，整个啃咬期间保持。
        zombie.setAggressive(true);
        // 客户端据此播放「头部上下啃咬」动画；服务端同步挂上 +50% 移速。
        zombie.setBiting(true);
        zombie.beginBiteFrenzy();
        // 起手一声低吼，随后每一口是啃咬声。
        zombie.level().playSound(null, zombie.getX(), zombie.getY(), zombie.getZ(),
                GeneratedMod.CRYSTALLIZED_ZOMBIE_SHATTER.get(), SoundSource.HOSTILE, 0.8F, 1.5F);
        // 第 1 口立刻咬下，之后每 10 刻一口。
        biteOnce();
    }

    @Override
    public boolean canContinueToUse() {
        if (ticksRemaining <= 0 || target == null) return false;
        if (zombie.isCrystalShock()) return false;
        return target.isAlive() && zombie.distanceToSqr(target) <= BREAK_RANGE_SQR;
    }

    @Override
    public void tick() {
        if (target == null) return;
        ticksRemaining--;
        zombie.getLookControl().setLookAt(target.position());
        // 贴上了就钉在原地撕咬；被蹭开一点就迈步贴回去（这时候的 1.5 倍速看得见）。
        if (zombie.distanceToSqr(target) > KEEP_RANGE_SQR) {
            MobPathing.chase(zombie, target, 1.0D);
        } else {
            zombie.getNavigation().stop();
        }
        if (ticksRemaining % CrystallizedZombie.BITE_INTERVAL == 0 && ticksRemaining > 0) {
            biteOnce();
        }
    }

    /** 一次撕咬：4 毒 + 8 物理，概率挂中毒 / 凋零，回血 2 点，抽 2 点魔素，响一声啃咬。 */
    private void biteOnce() {
        if (target == null || !(zombie.level() instanceof ServerLevel level)) return;
        RandomSource random = zombie.getRandom();
        // 伤害走统一的「一次暴击、两段落地」入口：毒元素 4 点 + 物理 8 点。
        // r252：记住这一口是不是真的咬进了肉（弹反、无敌帧、免伤都算没造成伤害）。
        boolean dealtDamage = SpellDamage.hitSplitElement(level, zombie, target, ElementType.POISON.id(), 4.0F,
                level.damageSources().mobAttack(zombie), 8.0F);
        if (random.nextFloat() < 0.5F) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0));
        }
        if (random.nextFloat() < 0.5F) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
        }
        // 每次造成回 2 点生命；这一口没打出伤害就不回（r252 起的明确规则）。
        if (dealtDamage && zombie.getHealth() < zombie.getMaxHealth()) zombie.heal(2.0F);
        // 抽取目标 2 点魔素：目标有多少抽多少（上限 2），加进自己的池子。
        double available = ManaData.get(target).mana();
        double stolen = Math.min(2.0, Math.max(0.0, available));
        if (stolen > 0.0) {
            ManaData.drainMana(target, stolen);
            ManaData.restoreMana(zombie, stolen, false);
        }
        // 每口一声晶石牙啃咬：在撕咬点上方响，音高每口随机浮动一点，听着不像复读。
        Vec3 bitePoint = target.getBoundingBox().getCenter().add(0, 0.3, 0);
        level.playSound(null, bitePoint.x, bitePoint.y, bitePoint.z,
                GeneratedMod.CRYSTALLIZED_ZOMBIE_BITE.get(), SoundSource.HOSTILE,
                1.0F, 0.88F + random.nextFloat() * 0.18F);
        // 紫色晶尘从撕咬点溅出。
        level.sendParticles(CrystallizedZombie.CRYSTAL_DUST,
                bitePoint.x, bitePoint.y, bitePoint.z, 6, 0.22, 0.22, 0.22, 0.01);
    }

    @Override
    public void stop() {
        // 持续 5 秒（或被拉开）后统一进入 12 秒冷却；目标留着，普通近战继续打。
        zombie.setBiteCooldownUntil(zombie.level().getGameTime() + CrystallizedZombie.BITE_COOLDOWN_TICKS);
        zombie.setAggressive(false);
        // 收势：停动画、摘掉 +50% 移速。
        zombie.endBiteFrenzy();
        target = null;
        ticksRemaining = 0;
    }
}
