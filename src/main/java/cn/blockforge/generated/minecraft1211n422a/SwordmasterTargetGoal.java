package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 目标选择：巡逻口径只主动挑 {@link net.minecraft.world.entity.monster.Enemy} 标记的敌对生物
 * （亡灵、灾厄、本模组的变异怪与哥布林），玩家与中立生物永远不会被主动盯上；
 * 猪灵族 / 僵尸猪灵 / 元素史莱姆这类"中立但按敌对注册"的生物也绝不主动招惹（r269）。
 * 被打了由 {@link net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal} 负责反击；
 * 被保护者（玩家 / 村民 / 铁傀儡）挨了中立生物的打、或雇主先动手，则走护主例外，
 * 见 {@link WanderingSwordmaster#findCombatTarget()}。
 *
 * <p>雇佣状态下，雇主受到攻击的目标、雇主正在打的目标都会排在普通敌人前面。</p>
 */
public final class SwordmasterTargetGoal extends net.minecraft.world.entity.ai.goal.target.TargetGoal {
    private final WanderingSwordmaster mob;
    private int cooldown;

    public SwordmasterTargetGoal(WanderingSwordmaster mob) {
        super(mob, false);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        if (mob.isBusy()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 10;
        LivingEntity found = mob.findCombatTarget();
        if (found == null) return false;
        // 刚被判定「走不到」的目标（被墙 / 围栏挡在外面）先别选，不然它又多贴一轮墙。
        if (MobPathing.isUnreachable(mob, found)) return false;
        mob.setTarget(found);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return target != null && mob.isValidTarget(target, true) && mob.canContinueTargeting(target);
    }

    @Override
    public void start() {
        mob.setTarget(mob.getTarget());
    }

    @Override
    public void stop() {
        // 目标选择 Goal 放弃时不主动清空目标，交给战斗 Goal 判断脱战。
    }
}
