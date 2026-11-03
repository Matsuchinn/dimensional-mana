package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 升华药水（Potion of Ascension）的六种状态。
 *
 * <p>「生命跃迁」走属性修饰符，药效一结束最大生命自己就退回去；
 * 「生命涌动」在自己的 tick 里回血；「神圣庇护」「破灭之力」「死斗意志」本身只是计时标记，
 * 真正的拦截与增伤在 {@link AscensionEvents} 里读这三个效果；「药效反噬」是结算惩罚的标记，
 * 由 {@link AscensionEvents#onEffectExpired} 在主药效到期时挂上。</p>
 */
public final class AscensionEffect {
    /** 生命跃迁：180 秒。 */
    public static final int ASCENSION_TICKS = 180 * 20;
    /** 神圣庇护：120 秒。 */
    public static final int WARD_TICKS = 120 * 20;
    /** 破灭之力：30 秒。 */
    public static final int MIGHT_TICKS = 30 * 20;
    /** 生命涌动：30 秒。 */
    public static final int REBIRTH_TICKS = 30 * 20;
    /** 死斗意志：15 秒。 */
    public static final int DEATHWARD_TICKS = 15 * 20;
    /** 药效反噬：15 秒。 */
    public static final int BACKLASH_TICKS = 15 * 20;

    /** 抗性提升Ⅳ：120 秒（原版等级 4 对应 amplifier 3，减伤 80%）。 */
    public static final int RESISTANCE_TICKS = 120 * 20;
    /** 抗性提升的 amplifier：3 = 抗性提升Ⅳ。 */
    public static final int RESISTANCE_AMPLIFIER = 3;
    /** 生命恢复Ⅱ：30 秒。 */
    public static final int REGENERATION_TICKS = 30 * 20;
    /** 生命恢复的 amplifier：1 = 生命恢复Ⅱ。 */
    public static final int REGENERATION_AMPLIFIER = 1;
    /** 伤害吸收Ⅳ：60 秒（额外 16 点吸收伤害）。 */
    public static final int ABSORPTION_TICKS = 60 * 20;
    /** 伤害吸收的 amplifier：3 = 伤害吸收Ⅳ。 */
    public static final int ABSORPTION_AMPLIFIER = 3;

    /** 生命跃迁：最大生命值 +50%。 */
    public static final class Ascension extends MobEffect {
        public static final double MAX_HEALTH_MULTIPLIER = 0.50D;

        public Ascension() {
            super(MobEffectCategory.BENEFICIAL, 0xFFD85A);
            addAttributeModifier(Attributes.MAX_HEALTH,
                    ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "ascension_max_health"),
                    MAX_HEALTH_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    /**
     * 生命涌动：生命恢复速度 ×4。
     *
     * <p>原版「生命恢复Ⅰ」是每 50 刻回 1 点，这里每 25 刻回 2 点，正好是 4 倍速。
     * 用 heal 而不是直接改血量：吸血、治疗削弱这类现成的治疗修正也能照常参与。</p>
     */
    public static final class Rebirth extends MobEffect {
        /** 每 25 刻回 2 点生命（每 12.5 刻 1 点，原版生命恢复Ⅰ的 4 倍）。 */
        public static final int HEAL_INTERVAL = 25;
        /** 每次结算回复的生命点数。 */
        public static final float HEAL_AMOUNT = 2.0F;

        public Rebirth() {
            super(MobEffectCategory.BENEFICIAL, 0x7DFF9A);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % HEAL_INTERVAL == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide && entity.isAlive() && entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(HEAL_AMOUNT);
            }
            return true;
        }
    }

    /** 神圣庇护：期间新施加的负面效果直接被拦在门外（见 {@link AscensionEvents#onEffectApplicable}）。 */
    public static final class Ward extends MobEffect {
        public Ward() {
            super(MobEffectCategory.BENEFICIAL, 0xF4F0FF);
        }
    }

    /** 破灭之力：造成的全类型伤害 +50%（见 {@link AscensionEvents#onIncomingDamage}）。 */
    public static final class Might extends MobEffect {
        public Might() {
            super(MobEffectCategory.BENEFICIAL, 0xFF6B4A);
        }
    }

    /** 死斗意志：受到致命伤害时把血量锁在 1 点（见 {@link AscensionEvents#onFatalDamage}）。 */
    public static final class Deathward extends MobEffect {
        public Deathward() {
            super(MobEffectCategory.BENEFICIAL, 0x8A0F1E);
        }
    }

    /** 药效反噬：虚弱Ⅲ、缓慢Ⅲ、魔素清零的 15 秒惩罚窗口。 */
    public static final class Backlash extends MobEffect {
        public Backlash() {
            super(MobEffectCategory.HARMFUL, 0x5B3E8E);
        }
    }

    private AscensionEffect() {
    }
}
