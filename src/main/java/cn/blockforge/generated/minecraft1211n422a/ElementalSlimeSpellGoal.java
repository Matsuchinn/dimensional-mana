package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** 无前摇初级元素法术：冷却就绪立即命中，冷却未好时本 Goal 让位给近战 Goal。 */
public final class ElementalSlimeSpellGoal extends Goal {
    private final ElementalSlime slime;

    public ElementalSlimeSpellGoal(ElementalSlime slime) {
        this.slime = slime;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = slime.getTarget();
        return target != null && slime.canAttack(target) && slime.isSpellReady()
                && slime.distanceToSqr(target) <= 16.0 * 16.0 && slime.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        LivingEntity target = slime.getTarget();
        if (target == null) return;
        slime.getLookControl().setLookAt(target, 30.0F, 30.0F);
        slime.castBasicSpell(target);
    }
}
