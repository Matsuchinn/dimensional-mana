package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 招架识破：这是织网者最难缠的一点——它不跟持刀玩家硬碰硬。
 *
 * <p>只要近战距离内的目标玩家正摆着本模组的斗气招架姿态，就立刻取消攻击意图，
 * 花 200 魔素强制后跳并反手吐一发蛛丝球。冷却 5 秒，魔素不够时识破不成立
 * （那时它会退回普通近战，玩家也确实能弹到它）。</p>
 */
public final class CorruptedWeaverParryAvoidGoal extends Goal {
    private final CorruptedWeaverEntity weaver;

    public CorruptedWeaverParryAvoidGoal(CorruptedWeaverEntity weaver) {
        this.weaver = weaver;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = weaver.getTarget();
        if (!(target instanceof ServerPlayer player) || !player.isAlive()) return false;
        if (!weaver.targetCloserThan(CorruptedWeaverEntity.PARRY_RANGE)) return false;
        if (!weaver.ready(weaver.parryReadyAt())) return false;
        if (!weaver.hasMana(CorruptedWeaverEntity.PARRY_COST)) return false;
        return BattleQiParry.inStance(player);
    }

    @Override
    public void start() {
        LivingEntity target = weaver.getTarget();
        if (target == null) return;
        weaver.getNavigation().stop();
        weaver.parryAvoid(target);
    }

    @Override
    public boolean canContinueToUse() {
        // 一次性动作：后跳与吐网都在 start 里完成。
        return false;
    }
}
