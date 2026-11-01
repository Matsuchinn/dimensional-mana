package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 虚空蚀影的「魔能爆」：8~64 格内、魔素够 500、冷却好了，就站定蓄力 2 秒再开火。
 *
 * <p>这个 Goal 只负责「什么时候开始、什么时候承认被打断」，真正的蓄力倒计时与命中判定
 * 写在 {@link VoidShadeEntity#beginCast} / {@link VoidShadeEntity#finishBlast} 里 —— 这样
 * 蓄力状态是同步过的，客户端也能照着画粒子与发光。</p>
 */
public final class VoidShadeBlastGoal extends Goal {
    private final VoidShadeEntity shade;
    private LivingEntity chargeTarget;

    public VoidShadeBlastGoal(VoidShadeEntity shade) {
        this.shade = shade;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (shade.isCasting() || shade.hasEyes()) return false;
        if (!shade.blastReady()) return false;
        LivingEntity target = shade.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distance = shade.distanceTo(target);
        if (distance < VoidShadeEntity.BLAST_MIN_RANGE || distance > VoidShadeEntity.BLAST_MAX_RANGE) return false;
        if (ManaData.get(shade).mana() < VoidShadeEntity.BLAST_COST) return false;
        chargeTarget = target;
        return true;
    }

    @Override
    public void start() {
        ManaData.spendMana(shade, VoidShadeEntity.BLAST_COST);
        shade.startBlastCooldown();
        shade.beginCast(VoidShadeEntity.CastKind.BLAST);
    }

    @Override
    public boolean canContinueToUse() {
        if (!shade.isCasting()) return false;
        if (shade.castKind() != VoidShadeEntity.CastKind.BLAST) return false;
        // 蓄力期间目标跑出 64 格就放弃（否则光线会朝空气打）。
        LivingEntity target = chargeTarget;
        return target != null && target.isAlive() && target.level() == shade.level()
                && shade.distanceTo(target) <= VoidShadeEntity.BLAST_MAX_RANGE + 8.0D;
    }

    @Override
    public void tick() {
        LivingEntity target = chargeTarget != null && chargeTarget.isAlive() ? chargeTarget : shade.getTarget();
        if (target != null) {
            shade.getLookControl().setLookAt(target, 30.0F, 30.0F);
            shade.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        if (shade.isCasting() && shade.castKind() == VoidShadeEntity.CastKind.BLAST) shade.cancelCast();
        chargeTarget = null;
    }
}
