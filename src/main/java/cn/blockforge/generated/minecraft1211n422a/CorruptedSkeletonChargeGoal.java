package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 魔蚀骸骨的主动技能：目标进到 16 格内、魔素够 400 时开拉弓蓄力 4 秒，蓄满射一发
 * {@link GiantSoulFireArrowEntity}，之后冷却 8 秒。
 *
 * <p>真正的倒计时和发射写在 {@link CorruptedSkeleton#tick()} 里 ——
 * Goal 可能因为目标跑开、被控住而中断，但「已经付过魔素的蓄力」不该白白消失；
 * 让实体自己数刻，玩家看到的那 4 秒就一定会走完。</p>
 */
public final class CorruptedSkeletonChargeGoal extends Goal {
    /** 触发距离：16 格。 */
    private static final double TRIGGER_RANGE_SQR = CorruptedSkeleton.SKILL_RANGE * CorruptedSkeleton.SKILL_RANGE;
    /** 蓄力开始后目标跑出这个距离就不管了（箭已经蓄上，照样会射出去）。 */
    private static final double ABORT_RANGE_SQR = 32.0D * 32.0D;

    private final CorruptedSkeleton skeleton;

    public CorruptedSkeletonChargeGoal(CorruptedSkeleton skeleton) {
        this.skeleton = skeleton;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (skeleton.isCasting() || skeleton.isSkirmishing()) return false;
        if (skeleton.level().getGameTime() < skeleton.skillCooldownUntil()) return false;
        LivingEntity target = skeleton.getTarget();
        if (target == null || !target.isAlive() || target.level() != skeleton.level()) return false;
        if (skeleton.distanceToSqr(target) > TRIGGER_RANGE_SQR) return false;
        if (ManaData.get(skeleton).mana() < CorruptedSkeleton.SKILL_MANA_COST) return false;
        if (!skeleton.canAttack(target)) return false;
        // 看不见目标就别开大招：隔着墙蓄力然后射出去会很莫名其妙。
        return skeleton.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        if (!skeleton.isCasting()) return false;
        LivingEntity target = skeleton.getTarget();
        if (target == null || !target.isAlive()) return true;
        return skeleton.distanceToSqr(target) <= ABORT_RANGE_SQR;
    }

    @Override
    public void start() {
        LivingEntity target = skeleton.getTarget();
        skeleton.getNavigation().stop();
        if (!skeleton.beginCast(target)) {
            // 魔素被别的东西抽干了：进个短冷却，过几刻再看。
            skeleton.setSkillCooldownUntil(skeleton.level().getGameTime() + 40L);
        }
    }

    @Override
    public void stop() {
        skeleton.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = skeleton.getTarget();
        if (target != null) skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
        skeleton.getNavigation().stop();
    }
}
