package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * 原版 Slime 的 MoveControl 是包内私有的，外部没法调 setDirection，所以这里照抄一份。
 *
 * <p>关键点：Slime 的移动不是靠寻路，而是「攒几 tick 再蹦一下」。跳跃间隔由 jumpDelay 控制，
 * 落地瞬间 Slime.tick 会把 targetSquish 压成 -0.5，起跳再拉成 1.0，渲染器据此做出果冻被压扁/拉长的弹性。
 * 之前的目标每 tick 都调 jump()，等于一落地就弹，弹性动画根本没时间播完，看起来就僵硬。</p>
 */
public final class ElementalSlimeMoveControl extends MoveControl {
    private final ElementalSlime slime;
    private float targetYaw;
    private int jumpDelay;
    private boolean aggressive;

    public ElementalSlimeMoveControl(ElementalSlime slime) {
        super(slime);
        this.slime = slime;
        this.targetYaw = slime.getYRot();
    }

    /** 目标朝向（度）。原版每 tick 最多转 90 度，所以史莱姆转身是个慢弧度。 */
    public void setHeading(float yaw, boolean aggressive) {
        this.targetYaw = yaw;
        this.aggressive = aggressive;
    }

    /** 只要一个前进速度；跳跃节奏由内部 jumpDelay 决定。 */
    public void setWantedMovement(double speed) {
        this.speedModifier = speed;
        this.operation = Operation.MOVE_TO;
    }

    @Override
    public void tick() {
        // 被骑乘时移动完全由骑手控制：这里必须停手，否则 AI 的转向/跳跃会和玩家输入打架。
        if (this.mob.isVehicle()) {
            this.operation = Operation.WAIT;
            this.mob.setXxa(0.0F);
            this.mob.setZza(0.0F);
            this.mob.setSpeed(0.0F);
            return;
        }
        // 风史莱姆飞行时朝向与速度都由 ElementalSlime#tickWindFlight 直接给，这里再插手会互相拉扯。
        if (this.slime.isWindFloating()) {
            this.operation = Operation.WAIT;
            this.mob.setXxa(0.0F);
            this.mob.setZza(0.0F);
            this.mob.setSpeed(0.0F);
            return;
        }
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.targetYaw, 90.0F));
        this.mob.yHeadRot = this.mob.getYRot();
        this.mob.yBodyRot = this.mob.getYRot();
        if (this.operation != Operation.MOVE_TO) {
            this.mob.setZza(0.0F);
            return;
        }
        this.operation = Operation.WAIT;
        // 液面移动改为持续划行：不触发陆地跳跃，避免浮力和 jump() 每刻互相顶出上下抖动。
        if (this.slime.isFluidWalking()) {
            // +50% 液体速度由 ElementalSlime#travel 统一乘一次，这里不能再叠乘。
            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            this.mob.setXxa(0.0F);
            this.mob.setZza(1.0F);
            return;
        }
        if (this.mob.onGround()) {
            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            if (this.jumpDelay-- <= 0) {
                this.jumpDelay = this.slime.elementJumpDelay();
                if (this.aggressive) this.jumpDelay /= 3;
                this.slime.getJumpControl().jump();
                this.slime.playSlimeJumpSound();
            } else {
                // 蓄力间隔里把横向输入清零，史莱姆才会「停一下再蹦」。
                this.mob.setXxa(0.0F);
                this.mob.setZza(0.0F);
                this.mob.setSpeed(0.0F);
            }
        } else {
            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        }
    }
}
