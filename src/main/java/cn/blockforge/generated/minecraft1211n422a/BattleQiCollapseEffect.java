package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 斗气溃散（Battle Qi Collapse）：斗气被一次伤害打空或被技能消耗到 0 后进入的虚弱窗口。
 * 持续 4 秒，期间受到的伤害提高 25%，自己打出的近战伤害降低 45%。
 */
public final class BattleQiCollapseEffect extends MobEffect {
    public static final int DURATION_TICKS = 4 * 20;
    /** 全种类易伤：受到伤害 ×1.25。 */
    public static final float DAMAGE_TAKEN_MULTIPLIER = 1.25F;
    /** 近战伤害 ×0.55，也就是 -45%。 */
    public static final float MELEE_DAMAGE_MULTIPLIER = 0.55F;

    public BattleQiCollapseEffect() {
        super(MobEffectCategory.HARMFUL, 0xC9A227);
    }

    /** 刷新式施加：已经溃散时再次被打空只重置 4 秒计时。 */
    public static void apply(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(GeneratedMod.BATTLE_QI_COLLAPSE, DURATION_TICKS, 0, false, true, true));
    }
}
