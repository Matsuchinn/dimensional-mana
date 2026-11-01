package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

import java.util.function.Predicate;

/**
 * 会看 {@link MobPathing} 黑名单的「找最近目标」Goal，替掉直接用的原版
 * {@link NearestAttackableTargetGoal}。
 *
 * <p>为什么需要它：怪被围墙 / 围栏挡在外面时，玩家明明看得见（隔着栏杆），原版目标选择会
 * 一遍遍把这个人重新选成目标；{@link MobPathing#chase} 刚判定「走不到」放手，下一 tick 又被
 * 选回来 —— 于是怪永远贴着墙追一个够不着的人。这里在选中之后再问一句：这个人是不是刚被
 * 判定走不到？是就当作没看见，怪才会老老实实回去巡逻 / 采东西 / 闲逛。</p>
 *
 * <p>黑名单 10 秒后自动过期：玩家翻过墙来或者拆了墙，怪照常会重新盯上你。</p>
 */
public final class PathAwareTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
    public PathAwareTargetGoal(Mob mob, Class<T> targetType, boolean mustSee) {
        super(mob, targetType, mustSee);
    }

    public PathAwareTargetGoal(Mob mob, Class<T> targetType, boolean mustSee, boolean mustReach) {
        super(mob, targetType, mustSee, mustReach);
    }

    public PathAwareTargetGoal(Mob mob, Class<T> targetType, int randomInterval, boolean mustSee,
                               boolean mustReach, Predicate<LivingEntity> predicate) {
        super(mob, targetType, randomInterval, mustSee, mustReach, predicate);
    }

    /**
     * 原版的 {@code canUse} 只是把候选者塞进 {@code this.target} 字段（真正 setTarget 在
     * {@code start()} 里），所以这里拦在「选中之后、开打之前」最合适。
     */
    @Override
    public boolean canUse() {
        if (!super.canUse()) return false;
        if (this.target != null && MobPathing.isUnreachable(this.mob, this.target)) {
            // 刚被判定走不到：当作没看见，这次不选它。
            this.target = null;
            return false;
        }
        return true;
    }
}
