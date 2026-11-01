package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 聚魔爆裂体的「主动接近」Goal。
 *
 * <p><b>修的是什么：</b>它的两招各自有条件——{@link ManaCreeperCoreBurstGoal} 只在 8 格内起手，
 * {@link ManaCreeperChaosGoal} 只在魔素不足 1000 时才接管。于是「魔素充足 + 目标在 8 格以外」
 * 这一大段距离上<b>没有任何 Goal 在推动它</b>：目标选出来了，它却只会用
 * {@code WaterAvoidingRandomStrollGoal} 到处闲逛，看起来就是「站着不追人」。</p>
 *
 * <p>根因是原版的 {@code MeleeAttackGoal}（会追人的那个）写在 {@code Creeper#registerGoals()} 里，
 * 而 {@link ManaCreeper#registerGoals()} 没有调用父类实现，把它连同 {@code SwellGoal} 一起丢了。</p>
 *
 * <p>现在由本 Goal 补上这一段：一路压到 8 格内交给魔核爆裂，贴到 3 格内交给紊乱引信。
 * 优先级排在两个技能之后，只在「两个技能都够不着」的时候跑，不会抢技能的动作。</p>
 */
public final class ManaCreeperApproachGoal extends Goal {
    /** 追人速度倍率。 */
    private static final double CHASE_SPEED = 1.15D;
    /** 多久重算一次路径（刻）。 */
    private static final int REPATH_MIN = 5;
    private static final int REPATH_MAX = 11;
    /** 路径算不出来时多等几刻，省点 CPU。 */
    private static final int PATH_FAIL_PENALTY = 10;

    private final ManaCreeper creeper;
    private int pathTicks;

    public ManaCreeperApproachGoal(ManaCreeper creeper) {
        this.creeper = creeper;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = creeper.getTarget();
        if (target == null || !target.isAlive() || target.level() != creeper.level()) return false;
        if (!creeper.canAttack(target)) return false;
        if (creeper.isCoreCharging() || creeper.isChaosFusing()
                || creeper.isManaFrozen() || creeper.isCoreCooling()) return false;
        // 魔素不够 1000：交给 ChaosGoal，它自己会贴上去点引信。
        if (ManaData.get(creeper).mana() < ManaCreeper.CORE_BURST_COST) return false;
        // 已经进到核爆射程：交给 CoreBurstGoal，别再往前挤。
        double range = ManaCreeper.CORE_BURST_RANGE;
        return creeper.distanceToSqr(target) > range * range;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        pathTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = creeper.getTarget();
        if (target == null) return;
        creeper.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--pathTicks > 0) return;
        pathTicks = REPATH_MIN + creeper.getRandom().nextInt(REPATH_MAX - REPATH_MIN + 1);
        // 走不到就认命（MobPathing 会清目标 + 记黑名单），不再对着墙一下下地顶。
        if (!MobPathing.chase(creeper, target, CHASE_SPEED)) pathTicks += PATH_FAIL_PENALTY;
    }

    @Override
    public void stop() {
        creeper.getNavigation().stop();
    }
}
