package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 魔素不够 1000 时的备用玩法：恢复成原版苦力怕的「追人 + 贴身引信」，引信走完直接
 * 触发紊乱爆炸（{@link ManaCreeper#chaosDetonate()}）。
 *
 * <p>贴到 3 格内就停步点火（30 刻，和原版苦力怕一样），被目标拉开到 3 格以外就撤火重新追。</p>
 */
public final class ManaCreeperChaosGoal extends Goal {
    /** 开始点引信的距离。 */
    private static final double IGNITE_RANGE_SQR = 3.0D * 3.0D;
    /** 追人速度倍率：叠在本体已经 1.5 倍原版苦力怕的移速上。 */
    private static final double CHASE_SPEED = 1.0D;

    private final ManaCreeper creeper;

    public ManaCreeperChaosGoal(ManaCreeper creeper) {
        this.creeper = creeper;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = creeper.getTarget();
        if (target == null || !target.isAlive() || !creeper.canAttack(target)) return false;
        if (creeper.isCoreCooling() || creeper.isManaFrozen() || creeper.isChaosFusing()) return false;
        // 魔素还够用的时候，主技能那条 Goal 优先；这里只在「打不起核爆」时接管。
        return ManaData.get(creeper).mana() < ManaCreeper.CORE_BURST_COST;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = creeper.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (creeper.isCoreCooling() || creeper.isManaFrozen() || creeper.isCoreCharging()) return false;
        return ManaData.get(creeper).mana() < ManaCreeper.CORE_BURST_COST;
    }

    @Override
    public void tick() {
        LivingEntity target = creeper.getTarget();
        if (target == null) return;
        creeper.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = creeper.distanceToSqr(target);
        if (distance > IGNITE_RANGE_SQR) {
            // 像原版苦力怕一样贴上去；走不到就由 MobPathing 认命放手，不再贴墙。
            MobPathing.chase(creeper, target, CHASE_SPEED);
            creeper.cancelChaosFuse();
        } else if (!creeper.isChaosFusing()) {
            creeper.getNavigation().stop();
            creeper.beginChaosFuse();
        }
    }

    @Override
    public void stop() {
        // 目标丢了（跑远 / 死了）就撤火，别在空地放空炮。
        creeper.cancelChaosFuse();
    }
}
