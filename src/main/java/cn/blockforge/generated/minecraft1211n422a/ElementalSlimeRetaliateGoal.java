package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** 受伤后优先锁定真正的伤害来源，包含非寻路 Mob 的 Slime 兼容实现。 */
public final class ElementalSlimeRetaliateGoal extends Goal {
    private final ElementalSlime slime;
    private int lastTimestamp;
    private LivingEntity attacker;

    public ElementalSlimeRetaliateGoal(ElementalSlime slime) {
        this.slime = slime;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int timestamp = slime.getLastHurtByMobTimestamp();
        if (timestamp == lastTimestamp) return false;
        attacker = slime.getLastHurtByMob();
        return attacker != null && slime.canAttack(attacker);
    }

    @Override
    public void start() {
        slime.setTarget(attacker);
        lastTimestamp = slime.getLastHurtByMobTimestamp();
        attacker = null;
    }
}
