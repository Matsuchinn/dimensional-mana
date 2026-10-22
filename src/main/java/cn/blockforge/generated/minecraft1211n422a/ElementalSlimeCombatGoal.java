package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Slime 专用追击/近战 Goal；不依赖寻路。法术冷却期间才会近身攻击。
 * 朝向交给 MoveControl 转（每 tick 最多 90°），跳跃间隔由它内部计时，追击时缩短到三分之一。
 */
public final class ElementalSlimeCombatGoal extends Goal {
    /** 连续两秒原地不动就认为卡住了。 */
    private static final int STUCK_LIMIT = 40;
    private final ElementalSlime slime;
    private int attackCooldown;
    /** 卡住检测用的上一帧位置。 */
    private double lastX;
    private double lastZ;
    private int stuckTicks;
    /** 卡住后侧向绕行的角度与剩余刻数。 */
    private float detourYaw;
    private int detourTicks;
    /** 连续卡住几次；实在追不到就放弃目标，回去闲逛，而不是原地罚站。 */
    private int stuckBursts;

    public ElementalSlimeCombatGoal(ElementalSlime slime) {
        this.slime = slime;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = slime.getTarget();
        // isSpellReady() 已经包含射程与视线判定，所以「法术打不到」时这里必定接手追击。
        return target != null && slime.canAttack(target) && !slime.isSpellReady();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        lastX = slime.getX();
        lastZ = slime.getZ();
        stuckTicks = 0;
        stuckBursts = 0;
        detourTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = slime.getTarget();
        if (target == null) return;
        ElementalSlimeMoveControl move = slime.slimeMoveControl();
        if (move != null) {
            double dx = target.getX() - slime.getX();
            double dz = target.getZ() - slime.getZ();
            float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            if (detourTicks > 0) {
                detourTicks--;
                yaw += detourYaw;
            }
            move.setHeading(yaw, true);
            move.setWantedMovement(1.20);
        }
        trackStuck(target);
        if (attackCooldown > 0) attackCooldown--;
        if (attackCooldown <= 0 && slime.isWithinMeleeAttackRange(target)) {
            attackCooldown = 20;
            slime.doHurtTarget(target);
        }
    }

    /**
     * 史莱姆不会寻路，只会往前蹦；撞上 1 格坎、树干或栏杆时会一直贴墙。
     * 这里做两件事：卡住就斜着绕一下并强制起跳；连续几次都过不去就放弃目标。
     */
    private void trackStuck(LivingEntity target) {
        double moved = (slime.getX() - lastX) * (slime.getX() - lastX)
                + (slime.getZ() - lastZ) * (slime.getZ() - lastZ);
        lastX = slime.getX();
        lastZ = slime.getZ();
        if (moved < 0.0025) stuckTicks++;
        else stuckTicks = 0;
        if (stuckTicks < STUCK_LIMIT) return;
        stuckTicks = 0;
        stuckBursts++;
        detourTicks = 30;
        detourYaw = (slime.getRandom().nextBoolean() ? 1.0F : -1.0F)
                * (55.0F + slime.getRandom().nextFloat() * 45.0F);
        slime.getJumpControl().jump();
        boolean far = slime.distanceToSqr(target) > 24.0 * 24.0;
        if (stuckBursts >= 6 || (stuckBursts >= 3 && far)) {
            // 追不到就散：放弃目标后会回到闲逛 Goal，至少不会站着不动。
            slime.setTarget(null);
            stuckBursts = 0;
        }
    }
}
