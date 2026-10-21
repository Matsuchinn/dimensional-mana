package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** 奥法涌动 I：每秒恢复 4 魔素，最多达到魔素上限的 130%。 */
public final class ArcaneSurgeEffect extends MobEffect {
    public ArcaneSurgeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9D4EDD);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) ManaData.restoreManaToRatio(entity, 4.0, 1.30);
        return true;
    }
}
