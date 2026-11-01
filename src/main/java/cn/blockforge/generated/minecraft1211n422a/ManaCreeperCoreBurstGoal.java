package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 主动技能「魔核爆裂」的蓄力 Goal。
 *
 * <p>启动条件全部写在 {@link ManaCreeper#canBeginCoreBurst}：目标在 8 格内、魔素 ≥ 1000、
 * 不在冷却 / 定身 / 引信阶段。一旦起手就站住不动，50 刻后由
 * {@link ManaCreeper#coreBurst()} 结算；目标拉开太远则安静取消，不扣魔素。</p>
 */
public final class ManaCreeperCoreBurstGoal extends Goal {
    /** 目标跑到这么远就放弃蓄力（比启动的 8 格宽一点，免得在边界上来回抖）。 */
    private static final double ABORT_RANGE_SQR = 12.0D * 12.0D;

    private final ManaCreeper creeper;

    public ManaCreeperCoreBurstGoal(ManaCreeper creeper) {
        this.creeper = creeper;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = creeper.getTarget();
        return target != null && creeper.canAttack(target) && creeper.canBeginCoreBurst(target);
    }

    @Override
    public void start() {
        creeper.beginCoreCharge();
    }

    @Override
    public boolean canContinueToUse() {
        if (!creeper.isCoreCharging()) return false;
        LivingEntity target = creeper.getTarget();
        return target != null && target.isAlive() && creeper.distanceToSqr(target) <= ABORT_RANGE_SQR;
    }

    @Override
    public void tick() {
        LivingEntity target = creeper.getTarget();
        // 蓄力期间钉在原地，只转头盯着目标。
        creeper.getNavigation().stop();
        if (target != null) creeper.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    @Override
    public void stop() {
        creeper.cancelCoreCharge();
    }
}
