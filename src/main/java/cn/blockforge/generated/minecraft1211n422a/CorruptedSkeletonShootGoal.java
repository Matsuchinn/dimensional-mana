package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 魔蚀骸骨的远程射击：替掉原版骷髅那套「射原版箭」的 {@code RangedBowAttackGoal}。
 *
 * <p>32 格内发现目标就抬弓：先拉弓 20 刻（原版拉弓姿势由 {@code startUsingItem} 自动给出），
 * 拉满后射出一根 {@link SoulFireArrowEntity}，两发之间隔 34 刻。</p>
 *
 * <p><b>走位</b>分四档，都是「边动边打」：</p>
 * <ul>
 *   <li>箭的路径被方块挡住：先挪到侧面的空档（{@link CorruptedSkeleton#clearShotPoint}），
 *       实在找不到空档就往目标靠近，近到能看见为止；</li>
 *   <li>24 格以外：一边接近一边射；</li>
 *   <li>10 ~ 24 格：站定端弓，专心输出；</li>
 *   <li>10 格以内：<b>一边后退一边射</b>——身体始终对着玩家，脚往后滑，
 *       读起来就是「倒退着放箭」，玩家贴不上来。</li>
 * </ul>
 *
 * <p>目标进到 5 格内由 {@link CorruptedSkeletonSkirmishGoal} 接管（加速掉头跑一段），
 * 那个 Goal 跑完或者跑不掉，才轮到这里继续射。</p>
 */
public final class CorruptedSkeletonShootGoal extends Goal {
    /** 射击的最大距离（格）：设计稿要求 32 格内就能射。 */
    private static final double MAX_RANGE_SQR = CorruptedSkeleton.SHOOT_RANGE * CorruptedSkeleton.SHOOT_RANGE;
    /** 目标跑出这个距离就放弃（比射程宽一点，免得在边界上来回抖）。 */
    private static final double CHASE_RANGE_SQR = 36.0D * 36.0D;
    /** 超过这个距离才往前走。 */
    private static final double APPROACH_RANGE_SQR = 24.0D * 24.0D;
    /** 进到这个距离内就一边后退一边射。 */
    private static final double BACKSTEP_RANGE_SQR = CorruptedSkeleton.BACKSTEP_RANGE * CorruptedSkeleton.BACKSTEP_RANGE;
    /** 两发之间的间隔。 */
    private static final int INTERVAL = 34;
    /** 提前多少刻开始拉弓。 */
    private static final int WINDUP = 20;
    /** 至少看见目标这么多刻才肯放箭，避免隔着墙角乱射。 */
    private static final int MIN_SIGHT_TICKS = 5;
    /** 后撤点 / 找空档多久重算一次（刻）。 */
    private static final int REPATH_TICKS = 8;
    /** 后退时每次退多远。 */
    private static final double BACKSTEP_DISTANCE = 9.0D;
    /** 后退速度（格/刻）：约 2.8 格/秒，玩家跑得过来，但不至于白送。 */
    private static final double BACKSTEP_SPEED = 0.14D;

    private final CorruptedSkeleton skeleton;
    private int cooldown = 10;
    private int seeTime;
    /** 后退方向（水平单位向量），null = 后方没地方退。 */
    private Vec3 backstepDir;
    /** 距离下次重算后退方向还有几刻。 */
    private int backstepCooldown;
    /** 距离下次重算「找空档」还有几刻。 */
    private int repositionCooldown;

    public CorruptedSkeletonShootGoal(CorruptedSkeleton skeleton) {
        this.skeleton = skeleton;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (skeleton.isCasting() || skeleton.isSkirmishing()) return false;
        LivingEntity target = skeleton.getTarget();
        if (target == null || !target.isAlive() || target.level() != skeleton.level()) return false;
        if (skeleton.distanceToSqr(target) > MAX_RANGE_SQR) return false;
        return skeleton.canAttack(target);
    }

    @Override
    public boolean canContinueToUse() {
        if (skeleton.isCasting() || skeleton.isSkirmishing()) return false;
        LivingEntity target = skeleton.getTarget();
        if (target == null || !target.isAlive() || target.level() != skeleton.level()) return false;
        if (skeleton.distanceToSqr(target) > CHASE_RANGE_SQR) return false;
        return skeleton.canAttack(target);
    }

    @Override
    public void start() {
        cooldown = 10;
        seeTime = 0;
        backstepCooldown = 0;
        repositionCooldown = 0;
        backstepDir = null;
    }

    @Override
    public void stop() {
        seeTime = 0;
        backstepDir = null;
        skeleton.stopUsingItem();
    }

    @Override
    public void tick() {
        LivingEntity target = skeleton.getTarget();
        if (target == null) return;
        // 一直盯着目标：后退的时候身体是正对玩家的，看起来才是「倒退着放箭」。
        skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distanceSqr = skeleton.distanceToSqr(target);
        // 真的飞得过去吗（墙面、矮墙挡着都算挡住），比单纯的「看得见」更严格。
        boolean clearShot = CorruptedSkeleton.hasClearShot(skeleton, target);
        seeTime = clearShot ? seeTime + 1 : 0;

        if (backstepCooldown > 0) backstepCooldown--;
        if (repositionCooldown > 0) repositionCooldown--;

        if (!clearShot) {
            // ① 视线被挡：换个角度。
            reposition(target);
        } else if (distanceSqr > APPROACH_RANGE_SQR) {
            // ② 太远：一边走近一边射。
            backstepDir = null;
            // 节流 + 连败判定：目标被墙挡死时不会再每刻算一遍路、贴着墙磨。
            MobPathing.chase(skeleton, target, 1.0D);
        } else if (distanceSqr < BACKSTEP_RANGE_SQR) {
            // ③ 太近：边退边射。
            backstep(target);
        } else {
            // ④ 舒服的距离：站定端弓。
            backstepDir = null;
            skeleton.getNavigation().stop();
        }

        if (cooldown > 0) cooldown--;
        boolean readyToDraw = cooldown <= WINDUP && seeTime >= MIN_SIGHT_TICKS;
        if (readyToDraw && !skeleton.isUsingItem()) {
            skeleton.startUsingItem(InteractionHand.MAIN_HAND);
        }
        if (cooldown <= 0) {
            if (clearShot) skeleton.shootSoulFireArrow(target, 0.0F);
            skeleton.stopUsingItem();
            cooldown = INTERVAL;
        }
    }

    /**
     * 射击路径被方块挡住：先找侧面的空档挪过去，找不到就往目标靠近一点。
     * 挪的这几刻身体朝路径方向（正常走路），一旦看见目标就立刻恢复射击。
     */
    private void reposition(LivingEntity target) {
        backstepDir = null;
        if (repositionCooldown > 0) return;
        repositionCooldown = REPATH_TICKS;
        Vec3 point = CorruptedSkeleton.clearShotPoint(skeleton, target);
        if (point != null) {
            MobPathing.moveToPoint(skeleton, point, 1.15D);
        } else {
            // 侧后方全是墙：往目标身上靠，接近了总能看见；真靠不过去 MobPathing 会自己认命。
            MobPathing.chase(skeleton, target, 1.15D);
        }
    }

    /**
     * 边退边射：身体保持对着玩家（每刻 setLookAt），脚往后滑。
     *
     * <p>不能用寻路，因为原版移动是「沿着身体朝向走」的 —— 一旦寻路，
     * 身体就会被掰去朝后退方向，看起来就成了「背对着人逃跑」。所以这里直接给速度，
     * 顺便把自动前冲清零；后退点每 8 刻重挑一次，被墙挡住就往斜后方换角度。</p>
     */
    private void backstep(LivingEntity target) {
        skeleton.stopInPlace();
        if (backstepCooldown <= 0) {
            backstepCooldown = REPATH_TICKS;
            Vec3 point = CorruptedSkeleton.retreatPoint(skeleton, target, BACKSTEP_DISTANCE);
            backstepDir = point == null ? null
                    : new Vec3(point.x - skeleton.getX(), 0.0D, point.z - skeleton.getZ()).normalize();
        }
        if (backstepDir == null) return;
        skeleton.setDeltaMovement(backstepDir.x * BACKSTEP_SPEED,
                skeleton.getDeltaMovement().y, backstepDir.z * BACKSTEP_SPEED);
    }
}
