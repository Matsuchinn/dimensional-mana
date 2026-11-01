package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * 雇佣后的跟随：没有敌人时以 2~4 格的间距跟着雇主走。
 *
 * <p>雇主跑远（比如骑龙、坐船）时会加速追；跟丢了不传送，靠导航自己找路，
 * 避免在村庄里瞬间闪现吓到人。</p>
 */
public final class SwordmasterFollowGoal extends Goal {
    private final WanderingSwordmaster mob;
    private int repathCooldown;

    public SwordmasterFollowGoal(WanderingSwordmaster mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.isHired() || mob.isBusy() || mob.getTarget() != null) return false;
        Player owner = mob.owner();
        return owner != null && owner.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        if (!mob.isHired() || mob.getTarget() != null) return false;
        Player owner = mob.owner();
        return owner != null && owner.isAlive();
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        mob.setRunning(false);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player owner = mob.owner();
        if (owner == null) return;
        mob.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        double distance = mob.distanceTo(owner);
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 10;
        if (distance > 16.0D) {
            mob.setRunning(true);
            MobPathing.chase(mob, owner, 1.2D);
        } else if (distance > 4.0D) {
            mob.setRunning(true);
            MobPathing.chase(mob, owner, 1.0D);
        } else if (distance > 2.0D) {
            mob.setRunning(false);
            MobPathing.chase(mob, owner, 0.8D);
        } else {
            mob.setRunning(false);
            mob.getNavigation().stop();
        }
    }
}
