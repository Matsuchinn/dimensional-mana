package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * 植物相关的两个事件钩子：'？'到期变成诅咒、加料食物吃完补魔素与生命。
 */
public final class PlantEvents {
    /** “？”到期：换成维斯珀德索恩诅咒。 */
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        Level level = event.getEntity().level();
        if (level.isClientSide) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !instance.getEffect().is(GeneratedMod.VESPER_UNKNOWN)) return;
        PlantEffects.revealCurse(event.getEntity());
    }

    /** 吃完东西之后结算“加料食物”的额外回复。 */
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide) return;
        PlantEffects.fortifiedFoodEaten(event.getEntity(), event.getItem());
    }

    private PlantEvents() {
    }
}
