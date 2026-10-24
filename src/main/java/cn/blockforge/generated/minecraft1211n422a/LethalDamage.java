package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * 「必杀伤害」识别与数值收窄。
 *
 * <p>{@code /kill} 用的是 {@code generic_kill} 伤害，数值是 {@link Float#MAX_VALUE}。
 * 这种伤害本来不该进入任何伤害修正链：本模组有大量乘区（斗气减伤、暴击、易伤、
 * 元素抗性、披风、金钟罩……），{@code Float.MAX_VALUE} 被乘上任何一个大于 1 的系数就会
 * 溢出成 {@code Infinity}，而 {@code Infinity} 再碰到任意一个 0 倍率（例如 100% 抗性、
 * 100% 魔法减伤）就会变成 {@code NaN}。</p>
 *
 * <p>血量一旦写成 {@code NaN}，{@code NaN <= 0}、{@code NaN > 0} 全都是 {@code false}：
 * 原版永远判不出死亡、也不会播放死亡动画，实体就这样「站着不动、血条空空、怎么打都不死」
 * —— 也就是玩家说的幽灵状态。</p>
 *
 * <p>所以这里做两件事：认出必杀伤害，并把它的数值收窄成一个「足够杀死任何生物、
 * 又不会被后续乘法溢出」的有限值。收窄之后即使被原版的 100% 减伤乘以 0，结果也只是 0，
 * 不会再产生 {@code NaN}。</p>
 */
public final class LethalDamage {
    /**
     * 收窄后的必杀伤害数值：一百万点。
     *
     * <p>原版最大生命值是 1024，本模组最肉的生物也只有几十万点，一百万点足够一击必杀；
     * 距离 {@link Float#MAX_VALUE} 又差了三十多个数量级，乘几十倍也不可能溢出。</p>
     */
    public static final float SAFE_LETHAL = 1.0E6F;

    /**
     * 这一下是不是「必杀」伤害。
     *
     * <p>除了 {@code /kill} 的 {@code generic_kill}，其它模组的即死手段、以及数值上根本
     * 不可能由正常战斗打出来的伤害（一百万以上）也按必杀处理，避免同样的溢出问题。</p>
     */
    public static boolean isLethal(DamageSource source, float amount) {
        if (source != null && source.is(DamageTypes.GENERIC_KILL)) return true;
        return amount >= SAFE_LETHAL;
    }

    /** 把 NaN / 无穷大修正成有限值：NaN 归零，无穷大收窄成 {@link #SAFE_LETHAL}。 */
    public static float finite(float damage) {
        if (Float.isNaN(damage)) return 0.0F;
        if (damage == Float.POSITIVE_INFINITY) return SAFE_LETHAL;
        if (damage == Float.NEGATIVE_INFINITY) return 0.0F;
        return damage;
    }

    private LethalDamage() { }
}
