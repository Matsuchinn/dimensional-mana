package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 流浪剑士的战术状态机：一条 Goal 全程握着移动权。
 *
 * <p>距离分层（设计稿第五条）：</p>
 * <ul>
 *   <li>&gt; 16 格：斗气够就【逆牙穿霄】冲刺贴脸，否则全速追击；</li>
 *   <li>4 ~ 16 格：走位逼近，途中用【白昼一文字】穿透剑气消耗；目标正瞄准他时举剑做假动作；</li>
 *   <li>&lt; 4 格：三段连招；目标摆招架就强行取消、短后撤、反手剑气；目标举盾则连招后必后撤；</li>
 *   <li>危机：血量 &lt; 40% 时喝【回复药剂】（敌人贴脸才先撤一次，安全时就地喝）；斗气 &lt; 1000 且距离 &gt; 10 格集气。</li>
 * </ul>
 *
 * <p><b>动画与判定解耦：</b>这个 Goal 只负责「什么时候按什么键」，所有实际伤害都由
 * {@link WanderingSwordmaster#tickAction()} 在固定 tick 上结算；动画只看同步过去的
 * {@link WanderingSwordmaster#action()}。</p>
 */
public final class SwordmasterCombatGoal extends Goal {
    private static final int DECISION_INTERVAL = 6;
    /** 一轮环绕结束后至少歇多久（刻）：没贴近就歇满这个数改走普通寻路，防「绕圈不停手」。 */
    private static final int CIRCLE_REST = 45;
    private final WanderingSwordmaster mob;
    private int decisionCooldown;
    private int targetCheckCooldown;
    private int circleTicks;
    private int circleCooldown;
    private double circleStartDistance;
    private float circleDirection = 1.0F;

    public SwordmasterCombatGoal(WanderingSwordmaster mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        if (mob.isBusy()) return true;
        if (mob.getTarget() != null) return true;
        if (mob.lowHealth() && mob.canStartPotion()) return true;
        if (mob.canStartCondense()) return true;
        if (mob.lowQi() && mob.canStartGather()) return true;
        if (targetCheckCooldown > 0) { targetCheckCooldown--; return false; }
        targetCheckCooldown = 10;
        LivingEntity found = mob.findCombatTarget();
        // 刚被寻路判定「走不到」的人别立刻再捡回来打（r300）：以前 10 刻后重新选目标、
        // 追两步又放弃，反复几轮，看起来就是一直在原地磨。
        if (found == null || MobPathing.isUnreachable(mob, found)) return false;
        mob.setTarget(found);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.isBusy()) return true;
        if (mob.lowHealth() && mob.canStartPotion()) return true;
        if (mob.canStartCondense()) return true;
        if (mob.lowQi() && mob.canStartGather()) return true;
        LivingEntity target = mob.getTarget();
        return target != null && mob.isValidTarget(target, true) && mob.canContinueTargeting(target);
    }

    @Override
    public void start() {
        decisionCooldown = 0;
        circleTicks = 0;
        // 刚接战先歇 10 刻再考虑环绕，起手就是绕圈很怪。
        circleCooldown = 10;
        circleStartDistance = 0.0D;
        circleDirection = mob.getRandom().nextBoolean() ? 1.0F : -1.0F;
        mob.setSprinting(false);
    }

    @Override
    public void stop() {
        mob.setRunning(false);
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // 动作进行中：把移动权交还给实体自己的动作计时，只保持朝向。
        if (mob.isBusy()) {
            LivingEntity actionTarget = mob.getTarget();
            if (actionTarget != null && mob.action() != WanderingSwordmaster.Action.GATHER) {
                mob.faceTarget(actionTarget);
            }
            mob.getNavigation().stop();
            return;
        }

        // 1) 危机处理优先：喝药 / 集气。喝药要不要先撤、撤几次，由实体自己看情况判断
        //    （敌人贴脸才撤一次，安全时就地喝完接着打）。
        LivingEntity target = mob.getTarget();
        double distance = target == null ? Double.MAX_VALUE : mob.distanceTo(target);
        if (mob.lowHealth() && mob.canStartPotion()) {
            mob.beginPotionEscape();
            return;
        }
        // 斗气极低：有魔素就先用【魔斗气凝聚】（2 秒 / 500 魔素换 2500 斗气），
        // 魔素不够才退回慢慢打坐的集气。
        if (mob.canStartCondense() && distance > 4.0D) {
            mob.beginCondenseEscape();
            return;
        }
        if (mob.lowQi() && mob.canStartGather() && distance > 10.0D) {
            mob.beginGatherEscape();
            return;
        }

        // 2) 没有目标：结束战斗，回村庄巡逻 / 跟随雇主。
        if (target == null || !mob.isValidTarget(target, true) || !mob.canContinueTargeting(target)) {
            mob.setTarget(null);
            mob.setRunning(false);
            mob.getNavigation().stop();
            return;
        }

        // 朝向处理（r300，「原地打转」的头号修法）：
        // 导航正在沿路径走的时候，身体朝向交给 MoveControl/LookControl 自己慢慢转（原版怪就这套，不会打转）。
        // 以前是每刻用 faceTarget 硬写 YRot 对准目标，跟导航「朝路径点转」在同一刻对着干，
        // 身体朝向每刻来回跳几十度，玩家看到的就是贴墙原地转圈。
        // 硬锁定只留给「位移由自己接管」的场合：环绕走位、贴身站桩出招。
        if (circleTicks > 0 || !mob.getNavigation().isInProgress()) {
            mob.faceTarget(target);
        } else {
            mob.faceTargetSoft(target);
        }
        if (decisionCooldown > 0) decisionCooldown--;
        if (circleCooldown > 0) circleCooldown--;

        if (distance > 16.0D) {
            // 远距离：有视线才用本人冲撞；没视线先寻路，不对着墙浪费技能。
            if (mob.canUseCharge() && mob.hasLineOfSight(target) && decisionCooldown <= 0) {
                decisionCooldown = DECISION_INTERVAL * 2;
                mob.startChargeSkill(target);
                return;
            }
            mob.setRunning(true);
            if (decisionCooldown <= 0) {
                decisionCooldown = DECISION_INTERVAL;
                // 交给 MobPathing：路被墙挡住时不会每刻重算，连败三次就放弃这个目标。
                MobPathing.chase(mob, target, 1.15D);
            }
            return;
        }

        if (distance > 4.0D) {
            // 中距离博弈：根据目标是否逼近、是否正瞄准来选剑气、环绕或逼近，不再掷骰子连演假动作。
            mob.setRunning(distance > 8.0D);
            boolean targetClosing = target.getDeltaMovement().dot(mob.position().subtract(target.position())) > 0.08D;
            boolean aimed = aimingAtMe(target);
            // 斗气 > 2500：决策更勤、技能更凶（r263）。
            boolean aggressive = mob.highQiAggression();
            if (decisionCooldown <= 0) {
                decisionCooldown = aggressive ? 4 : DECISION_INTERVAL;
                // 3 格开外就能用【逆牙穿霄】撞过去，不用等到 16 格；斗气充足时中距离也会主动开。
                if (aggressive && mob.canChargeAt(distance) && mob.hasLineOfSight(target)
                        && mob.getRandom().nextFloat() < 0.6F) {
                    mob.startChargeSkill(target);
                    return;
                }
                // 斗气充足时不再挑「对方没瞄准我」才甩剑气，冷却也缩短了一半。
                boolean iaiWindow = aggressive || ((targetClosing || distance >= 9.0D) && !aimed);
                if (mob.canUseIai() && mob.hasLineOfSight(target) && iaiWindow) {
                    mob.startIaiSlash(target);
                    return;
                }
                if (aimed && distance <= 10.0D && mob.canFeint(target)
                        && target.getAttackAnim(0.0F) < 0.1F) {
                    mob.playTacticalFeint(target);
                    if (circleCooldown <= 0 && mob.canCircleAround(circleDirection > 0.0F)) {
                        circleTicks = 12;
                        circleDirection = -circleDirection;
                        circleStartDistance = distance;
                    }
                    return;
                }
                if (circleTicks <= 0 && circleCooldown <= 0 && aimed && distance < 8.0D) {
                    // 挑一侧还有路的再绕（r300）：以前贴着墙也照样起绕，横移一步就撞住，
                    // 人还每刻硬转对准目标 —— 那就是「原地打转」。两侧都堵死就改普通逼近。
                    boolean clockwise = mob.getRandom().nextBoolean();
                    if (mob.canCircleAround(clockwise)) {
                        circleDirection = clockwise ? 1.0F : -1.0F;
                    } else if (mob.canCircleAround(!clockwise)) {
                        circleDirection = clockwise ? -1.0F : 1.0F;
                    } else {
                        circleDirection = 0.0F;
                    }
                    if (circleDirection == 0.0F) {
                        // 没地方绕：歇一阵，这段时间正常寻路逼近。
                        circleCooldown = CIRCLE_REST;
                    } else {
                        circleTicks = 10 + mob.getRandom().nextInt(9);
                        circleStartDistance = distance;
                    }
                }
            }
            if (circleTicks > 0) {
                circleTicks--;
                // 一边横移一边略向前压迫，逼玩家先出招；目标真挥剑后下一轮直接反制。
                // r300：环绕改成自控位移（circleStep），不再走 MoveControl.strafe ——
                // strafe 被写死在移动速度的 25%，绕得比走还慢，几乎看不出在动。
                mob.getNavigation().stop();
                if (!mob.circleStep(circleDirection > 0.0F)) {
                    // 撞上东西：换边再来；两边都堵死就收手，回去走正常逼近，别空转。
                    circleDirection = -circleDirection;
                    if (!mob.circleStep(circleDirection > 0.0F)) {
                        circleTicks = 0;
                        circleCooldown = CIRCLE_REST;
                    }
                }
                // 已经压到近战距离：环绕的目的达到了，直接收。
                if (circleTicks > 0 && distance <= 5.0D) circleTicks = 0;
                if (circleTicks <= 0) {
                    // 一轮结束：贴进了 ≥1 格就很快能再绕；白绕（比如目标骑在墙上）就歇久一点。
                    circleCooldown = distance < circleStartDistance - 1.0D ? 15 : CIRCLE_REST;
                }
            } else if (decisionCooldown <= 1 || mob.getNavigation().isDone()) {
                MobPathing.chase(mob, target, distance > 8.0D ? 1.12D : 0.92D);
            }
            return;
        }

        // 3) 近战博弈：目标摆招架 → 读指令反制；否则三段连招。
        mob.setRunning(false);
        mob.getNavigation().stop();
        if (decisionCooldown > 0) return;
        decisionCooldown = DECISION_INTERVAL;

        if (mob.isParrying(target) && mob.canCounterParry()) {
            mob.counterParry(target);
            return;
        }
        if (mob.wantsBigBackstep() && mob.getRandom().nextFloat() < 0.35F) {
            mob.startBigBackstep();
            return;
        }
        // 贴身也可能直接放【逆牙穿霄】：近战里技能不再只是摆设，斗气足就动不动撞一下。
        if (mob.highQiAggression() && mob.canChargeAt(distance) && mob.hasLineOfSight(target)
                && mob.getRandom().nextFloat() < 0.5F) {
            mob.startChargeSkill(target);
            return;
        }
        if (mob.canStartCombo()) {
            mob.startCombo(target);
        }
    }

    /** 目标是不是正把视线对准他（用来决定要不要做举剑格挡的假动作）。 */
    private boolean aimingAtMe(LivingEntity target) {
        Vec3 toMe = mob.position().subtract(target.getEyePosition());
        if (toMe.lengthSqr() < 1.0E-4) return true;
        return target.getLookAngle().dot(toMe.normalize()) >= 0.55D;
    }
}
