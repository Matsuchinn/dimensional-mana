package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;

/**
 * 史莱姆的「畏缩」AI：闻到驱逐火把的植物气息就掉头。
 *
 * <p>只挂在史莱姆身上（原版史莱姆、元素史莱姆、熔岩史莱姆都继承 {@link Slime}），
 * 而且<b>不占用任何 Goal 旗标</b>：它不会打断史莱姆原本的随机跳、攻击或看向玩家，
 * 只是在旁边每刻补一点“往反方向走”的推力，所以看起来是“被熏得不想靠近”，
 * 而不是傻站着。</p>
 *
 * <p>性能：找火把每 20 刻才做一次（各史莱姆错开刻数避免同一刻一起算），
 * 平时就是读一下缓存字段；没有火把的世界里这个 Goal 一次 {@code canUse} 都不会通过。</p>
 */
public final class SlimeDreadGoal extends Goal {
    /** 多久重新找一次火把：1 秒。 */
    private static final int REFRESH_TICKS = 20;
    /** 状态每次续多久：3 秒，离开范围后会自己掉。 */
    private static final int DREAD_DURATION = 60;
    /** 每刻往反方向推多快，以及旧速度保留多少（越大越“滑”）。 */
    private static final double PUSH = 0.09D;
    private static final double DAMPING = 0.55D;

    private final Slime slime;
    private BlockPos torch;
    private int refresh;

    public SlimeDreadGoal(Slime slime) {
        this.slime = slime;
    }

    @Override
    public boolean canUse() {
        if (!(this.slime.level() instanceof ServerLevel level)) return false;
        if (--this.refresh > 0) return this.torch != null;
        // 按实体 id 错开，避免上百只史莱姆在同一刻一起查数据。
        this.refresh = REFRESH_TICKS + Math.floorMod(this.slime.getId(), REFRESH_TICKS);
        this.torch = SlimeRepellentData.get(level).nearestTorch(level,
                this.slime.getX(), this.slime.getY(), this.slime.getZ());
        return this.torch != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (this.torch == null || !(this.slime.level() instanceof ServerLevel)) return;
        Vec3 center = Vec3.atCenterOf(this.torch);
        double distanceSqr = this.slime.position().distanceToSqr(center);
        // 隐形状态：不显示粒子、不显示图标，纯粹是个标记。
        this.slime.addEffect(new MobEffectInstance(GeneratedMod.SLIME_DREAD, DREAD_DURATION, 0,
                false, false, false));
        if (distanceSqr > SlimeRepellentData.REPEL_RADIUS * SlimeRepellentData.REPEL_RADIUS) return;

        Vec3 away = this.slime.position().subtract(center);
        Vec3 flat = new Vec3(away.x, 0.0D, away.z);
        if (flat.lengthSqr() < 1.0E-4D) flat = new Vec3(1.0D, 0.0D, 0.0D);
        flat = flat.normalize();

        Vec3 motion = this.slime.getDeltaMovement();
        this.slime.setDeltaMovement(
                motion.x * DAMPING + flat.x * PUSH,
                motion.y,
                motion.z * DAMPING + flat.z * PUSH);
        // 服务端改速度要让客户端跟着同步，否则只在本机算，看起来是“卡住不动”。
        this.slime.hurtMarked = true;

        float yaw = (float)(Mth.atan2(flat.z, flat.x) * (180.0D / Math.PI)) - 90.0F;
        this.slime.setYRot(yaw);
        this.slime.yBodyRot = yaw;
        this.slime.yHeadRot = yaw;
    }
}
