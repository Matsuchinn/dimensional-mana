package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 远古诅咒：远古守卫者施加的 45 秒负面状态。
 *
 * <p>受诅咒期间最大生命值临时 -15%（走属性修饰符，状态结束自动恢复）；
 * 来自远古守卫者的伤害提高 200%，治疗量减少 50% 由伤害/治疗事件读取。</p>
 */
public final class AncientCurseEffect extends MobEffect {
    public static final double MAX_HEALTH_MULTIPLIER = -0.15;
    public static final float HEALING_MULTIPLIER = 0.50F;

    public AncientCurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x2F6B5A);
        addAttributeModifier(Attributes.MAX_HEALTH,
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "ancient_curse_health"),
                MAX_HEALTH_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
