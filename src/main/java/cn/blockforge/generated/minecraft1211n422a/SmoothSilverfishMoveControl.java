package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 秽蚀蠹专用移动控制（r91 二次重做，治抽搐）。
 *
 * <p>上一轮只把原版 90°/刻的转向上限收紧到 35°，还是抽搐，原因有三：</p>
 * <ol>
 *   <li><b>近战死区太小</b>：原版只在距目标点 &lt;1e-4 格时才停转向，蠹虫贴脸时
 *       atan2 的期望角每刻乱跳，35°/刻照样来回甩——这是「站在脸上原地抽搐」的主因；</li>
 *   <li><b>身体平滑只做在服务端</b>：原版 {@code Silverfish.tick()} 两边都硬对齐身体，
 *       客户端平滑在实体侧补（见 {@link CorruptedSilverfishEntity#tick()}）；</li>
 *   <li><b>重新寻路瞬间的大回摆</b>：路径每 7 刻重算，节点方向可能直接掉转 180°，
 *       这里要求大角度转向连续 3 刻同向才真正执行，滤掉单刻噪声。</li>
 * </ol>
 */
public final class SmoothSilverfishMoveControl extends MoveControl {
    /** 每刻最大转向角（原版 MOVE_TO 分支是 90°）。 */
    private static final float MAX_TURN_PER_TICK = 35.0F;
    /** 死区半径 0.6 格：够近就停转停走，直接进 MeleeAttack 的咬人口径。 */
    private static final double DEADZONE_SQR = 0.36D;
    /** 超过这个角度差算「大回摆」，需要连续确认。 */
    private static final float SWING_THRESHOLD = 100.0F;
    /** 大回摆的确认刻数。 */
    private static final int SWING_CONFIRM_TICKS = 3;
    /** 转向死区：偏差不超过这个角度就当没偏差，专门滤掉寻路的逐刻小抖动。 */
    private static final float TURN_DEADBAND = 6.0F;

    /** 连续几刻都要求同方向大回摆（0 = 还没攒够）。 */
    private int swingHold;

    public SmoothSilverfishMoveControl(Mob mob) {
        super(mob);
    }

    /**
     * 叫停领航：把控制切回 WAIT。
     *
     * <p>r92：停栖啃草时蠹虫是「关重力、自己写速度」的，可移动控制在
     * {@code PathNavigation} 最后一次 {@code setWantedPosition} 之后仍然留着 MOVE_TO——
     * 它每刻还会推进 {@code zza}、顺手改朝向。这里显式切回 WAIT（原版 WAIT 分支只把
     * 输入清零，不碰 {@code deltaMovement}），悬停速度才不会被它搅乱。</p>
     */
    public void setWait() {
        this.operation = Operation.WAIT;
        this.swingHold = 0;
        this.mob.setZza(0.0F);
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            this.swingHold = 0;
            super.tick();
            return;
        }
        this.operation = Operation.WAIT;
        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        double dy = this.wantedY - this.mob.getY();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < DEADZONE_SQR) {
            // 已到点：不转身也不推进——抽搐的最大来源就是这一段“到了还转”。
            this.mob.setZza(0.0F);
            this.swingHold = 0;
            return;
        }
        float wantedRot = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float delta = Mth.wrapDegrees(wantedRot - this.mob.getYRot());
        if (Math.abs(delta) > SWING_THRESHOLD) {
            if (++this.swingHold < SWING_CONFIRM_TICKS) {
                // 单刻冒出来的反向（多半是寻路重算噪声）：先站住，等它稳定。
                this.mob.setZza(0.0F);
                return;
            }
        } else {
            this.swingHold = 0;
        }
        // r92：小于 6° 的偏差一律不动朝向。追怪时 moveTo 每刻都在被重设，
        // 目标点前后差几厘米，atan2 就左右摆几度——35°/刻的上限拦不住这种
        // 每刻翻向的小抖，渲染出来正是「边走边轻轻左右抽搐」。
        if (Math.abs(delta) > TURN_DEADBAND) {
            this.mob.setYRot(Mth.rotateIfNecessary(this.mob.getYRot(), wantedRot, MAX_TURN_PER_TICK));
        }
        this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        // 起跳照搬原版判据，但同样受死区保护（上面已 return 的够近场景不会走到这里）。
        BlockPos blockpos = this.mob.blockPosition();
        BlockState blockstate = this.mob.level().getBlockState(blockpos);
        Vec3 center = Vec3.atBottomCenterOf(blockpos);
        if (dy > this.mob.maxUpStep() && dx * dx + dz * dz < Math.max(1.0F, this.mob.getBbWidth())
                || !blockstate.getCollisionShape(this.mob.level(), blockpos).isEmpty()
                        && this.mob.getY() < center.y() + blockstate.getCollisionShape(this.mob.level(), blockpos)
                                .max(Direction.Axis.Y)
                && !blockstate.is(net.minecraft.tags.BlockTags.DOORS) && !blockstate.is(net.minecraft.tags.BlockTags.FENCES)) {
            this.mob.getJumpControl().jump();
            this.operation = Operation.JUMPING;
        }
    }
}
