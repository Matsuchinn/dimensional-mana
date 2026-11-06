package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 元素精通强化：元素精通药水的效果本体。
 *
 * <p>本身不跳伤、不改面板，只在 {@link ElementMastery} 计算总精通时提供一段数值：
 * Ⅰ 级 +8，之后每升 1 级再多 +8（Ⅵ 级 +48）。加成直接从效果等级读出来，
 * 所以药水等级一改、效果一到期，精通数值立刻跟着变。</p>
 */
public final class ElementalMasteryEffect extends MobEffect {
    /** 每一级提供的元素精通。 */
    public static final float BONUS_PER_LEVEL = 8.0F;
    /** 最高等级（Ⅰ~Ⅵ）。 */
    public static final int MAX_LEVEL = 6;

    public ElementalMasteryEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8C6BFF);
    }

    /** 某一等级（amplifier 从 0 开始）提供的元素精通。 */
    public static float bonus(int amplifier) {
        return BONUS_PER_LEVEL * (Math.max(0, amplifier) + 1);
    }

    /** 这个效果不按刻结算，纯属性型。 */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    /** 加成值挂在缓存里，效果一变就立刻失效重算，免得打完药水面板还是旧数字。 */
    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        ElementMastery.invalidate(entity);
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        ElementMastery.invalidate(entity);
    }
}
