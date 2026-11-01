package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 虚空蚀影的「虚空凝聚」：魔素够 3000、场上至少 2 个虚无幻影时，站定蓄力 3.5 秒，
 * 把这些幻影烧成绕着自己转的魔蚀末影之眼。
 *
 * <p>蓄力一开始就给 16 格内的幻影打上「正在被献祭」的标记 —— 它们在这期间被玩家打死也没关系：
 * 被打死的照常自爆、照常回喂本体，只是不会再变成眼睛；活到最后的那批才会变成眼睛。</p>
 */
public final class VoidShadeCondenseGoal extends Goal {
    private final VoidShadeEntity shade;
    private LivingEntity chargeTarget;

    public VoidShadeCondenseGoal(VoidShadeEntity shade) {
        this.shade = shade;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (shade.isCasting() || shade.hasEyes()) return false;
        if (ManaData.get(shade).mana() < VoidShadeEntity.CONDENSE_COST) return false;
        if (shade.nearbyPhantoms(16.0D).size() < VoidShadeEntity.CONDENSE_MIN_PHANTOMS) return false;
        LivingEntity target = shade.getTarget();
        if (target == null || !target.isAlive()) return false;
        chargeTarget = target;
        return true;
    }

    @Override
    public void start() {
        ManaData.spendMana(shade, VoidShadeEntity.CONDENSE_COST);
        shade.markSacrifices();
        shade.beginCast(VoidShadeEntity.CastKind.CONDENSE);
    }

    @Override
    public boolean canContinueToUse() {
        return shade.isCasting() && shade.castKind() == VoidShadeEntity.CastKind.CONDENSE;
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
        if (shade.isCasting() && shade.castKind() == VoidShadeEntity.CastKind.CONDENSE) shade.cancelCast();
        chargeTarget = null;
    }
}
