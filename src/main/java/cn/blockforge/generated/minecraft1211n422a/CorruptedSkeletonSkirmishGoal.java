package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 魔蚀骸骨的「拉开」：目标贴到 5 格内就掉头、加速 65% 跑一段，把距离重新拉开再回头射。
 *
 * <p>细节：</p>
 * <ul>
 *   <li>最多跑 {@link CorruptedSkeleton#SKIRMISH_TICKS} 刻（2 秒），或者跑到离目标
 *       {@link CorruptedSkeleton#SKIRMISH_ESCAPE_DISTANCE} 格以外就收工；</li>
 *   <li>每 8 刻重算一次落脚点：先试正后方，被墙挡住就往斜后方偏 —— 加上寻路本身会绕，
 *       所以「有障碍物会绕开」；</li>
 *   <li>如果连续十几刻几乎没挪窝（被逼到墙角、卡在方块里），或者根本找不到能站的地方，
 *       就直接收工回头射击 —— 即「跑不掉会回头射击」；</li>
 *   <li>收工后进 {@link CorruptedSkeleton#SKIRMISH_COOLDOWN_TICKS} 刻冷却（4 秒），
 *       这段时间老老实实站着射。</li>
 * </ul>
 */
public final class CorruptedSkeletonSkirmishGoal extends Goal {
    /** 触发距离：5 格。 */
    private static final double TRIGGER_RANGE_SQR = CorruptedSkeleton.SKIRMISH_RANGE * CorruptedSkeleton.SKIRMISH_RANGE;
    /** 逃到这个距离就算成功。 */
    private static final double ESCAPE_RANGE_SQR =
            CorruptedSkeleton.SKIRMISH_ESCAPE_DISTANCE * CorruptedSkeleton.SKIRMISH_ESCAPE_DISTANCE;
    /** 每次逃跑目标点多远。 */
    private static final double RUN_DISTANCE = 12.0D;
    /** 多久重算一次逃跑点。 */
    private static final int REPATH_TICKS = 8;
    /** 连续多少刻几乎没动就算「跑不掉」。 */
    private static final int STUCK_LIMIT = 15;

    private final CorruptedSkeleton skeleton;
    private int timer;
    private int repath;
    private int stuckTicks;
    private Vec3 lastPos;

    public CorruptedSkeletonSkirmishGoal(CorruptedSkeleton skeleton) {
        this.skeleton = skeleton;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!skeleton.isSkirmishReady()) return false;
        LivingEntity target = skeleton.getTarget();
        if (target == null || !target.isAlive() || target.level() != skeleton.level()) return false;
        return skeleton.distanceToSqr(target) <= TRIGGER_RANGE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = skeleton.getTarget();
        if (target == null || !target.isAlive() || target.level() != skeleton.level()) return false;
        if (skeleton.isCasting()) return false;
        if (timer >= CorruptedSkeleton.SKIRMISH_TICKS) return false;
        // 已经拉开了，收工回头射。
        if (skeleton.distanceToSqr(target) >= ESCAPE_RANGE_SQR) return false;
        // 跑不掉就别硬跑，回头射。
        return stuckTicks <= STUCK_LIMIT;
    }

    @Override
    public void start() {
        timer = 0;
        repath = 0;
        stuckTicks = 0;
        lastPos = skeleton.position();
        skeleton.beginSkirmish();
    }

    @Override
    public void stop() {
        skeleton.endSkirmish();
    }

    @Override
    public void tick() {
        timer++;
        LivingEntity target = skeleton.getTarget();
        if (target == null) return;

        if (--repath <= 0) {
            repath = REPATH_TICKS;
            Vec3 point = CorruptedSkeleton.retreatPoint(skeleton, target, RUN_DISTANCE);
            if (point == null) {
                // 四面都被堵死：跑不掉，直接收工（下一轮由射击 Goal 接管）。
                stuckTicks = STUCK_LIMIT + 1;
                skeleton.getNavigation().stop();
                return;
            }
            skeleton.getNavigation().moveTo(point.x, point.y, point.z, 1.25D);
            // 转头跑：视线朝逃跑方向，而不是继续盯着玩家。
            skeleton.getLookControl().setLookAt(point.x, point.y + 1.6D, point.z, 30.0F, 30.0F);
        }

        // 卡住检测：几乎没挪窝就累计，挪动了就清零。
        Vec3 now = skeleton.position();
        if (lastPos != null && now.distanceToSqr(lastPos) < 0.02D) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPos = now;
    }
}
