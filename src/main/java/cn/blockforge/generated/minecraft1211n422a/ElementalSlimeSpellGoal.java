package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 元素史莱姆的初级法术：进入蓄力前摇，前摇结束才射出一颗看得见、飞得慢的弹体。
 *
 * <p>r400 起不再「抬手即命中」：本 Goal 只负责起手（{@link ElementalSlime#castBasicSpell}），
 * 之后由史莱姆自己的蓄力推进发射 {@link SlimeSpellBolt}。弹体必须真正飞到目标身上才结算伤害，
 * 路上可以走位躲开，也可以举招架打掉。冷却未好、目标超距或没有视线时，本 Goal 让位给近战 Goal。</p>
 */
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
