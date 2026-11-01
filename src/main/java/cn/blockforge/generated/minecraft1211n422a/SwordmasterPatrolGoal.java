package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * 村庄巡逻：没有敌人、也没被雇佣时，在生成他的村庄中心附近慢慢走动。
 *
 * <p>设计稿里「剑尖在地上划过留下细微火花」不是这个 Goal 负责的，粒子在
 * {@link WanderingSwordmaster#aiStep()} 里放。这里只管走位：离家超过 40 格就往回走，
 * 否则每 6~12 秒在 12 格内挑一个新落脚点。</p>
 */
public final class SwordmasterPatrolGoal extends Goal {
    /** 一个巡逻点最多花多久走过去（刻）：12 秒还到不了就作废重挑。 */
    private static final int WAYPOINT_TIMEOUT = 240;
    private final WanderingSwordmaster mob;
    private int waitTicks;
    private int repathCooldown;
    /** 当前这个巡逻点已经走了多少刻。 */
    private int waypointTicks;
    private BlockPos waypoint;

    public SwordmasterPatrolGoal(WanderingSwordmaster mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.isHired() || mob.isBusy() || mob.getTarget() != null) return false;
        return mob.homePos() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        waitTicks = 0;
        repathCooldown = 0;
        waypointTicks = 0;
        waypoint = null;
        mob.setRunning(false);
    }

    @Override
    public void stop() {
        mob.setRunning(false);
        mob.getNavigation().stop();
        waypointTicks = 0;
        waypoint = null;
    }

    @Override
    public void tick() {
        BlockPos home = mob.homePos();
        if (home == null) return;
        double distanceHome = mob.blockPosition().distSqr(home);
        if (waitTicks > 0) {
            waitTicks--;
            mob.getNavigation().stop();
            return;
        }
        // 巡逻点计时每刻都走：不能放在下面那个 20 刻一次的节流里，否则 12 秒会变成 240 秒。
        if (waypoint != null && waypointTicks < WAYPOINT_TIMEOUT) waypointTicks++;
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 20;
        if (distanceHome > 40.0D * 40.0D) {
            // 走远了先回家，别在野外迷路。
            waypoint = home;
            waypointTicks = 0;
            mob.setRunning(true);
            MobPathing.moveToPoint(mob, waypoint.getCenter(), 1.1D);
            return;
        }
        // r264：巡逻点迟早要放弃。以前挑到一个围墙外面 / 坑里的点，它会一直朝那儿走，
        // 走不到也永远不换，结果就是在墙边来回磨。现在 12 秒还没走到就作废，重新挑。
        if (waypoint != null && waypointTicks >= WAYPOINT_TIMEOUT) {
            waypoint = null;
            waypointTicks = 0;
            waitTicks = 40 + mob.getRandom().nextInt(40);
            mob.getNavigation().stop();
            return;
        }
        if (waypoint == null || mob.blockPosition().closerThan(waypoint, 2.5D)) {
            if (waypoint != null) {
                // 到了就站在村口东张西望一会儿，看起来像在巡逻。
                waitTicks = 60 + mob.getRandom().nextInt(100);
                waypoint = null;
                waypointTicks = 0;
                return;
            }
            // 挑一个「真的走得到」的点：连试 6 个，全都不通就原地歇一会儿再来。
            BlockPos picked = null;
            for (int attempt = 0; attempt < 6 && picked == null; attempt++) {
                int x = home.getX() + mob.getRandom().nextInt(25) - 12;
                int z = home.getZ() + mob.getRandom().nextInt(25) - 12;
                BlockPos candidate = mob.level().getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (MobPathing.isReachable(mob, candidate.getCenter())) picked = candidate;
            }
            if (picked == null) {
                waitTicks = 40 + mob.getRandom().nextInt(60);
                return;
            }
            waypoint = picked;
            waypointTicks = 0;
            mob.setRunning(false);
            MobPathing.moveToPoint(mob, waypoint.getCenter(), 0.8D);
        }
    }
}
