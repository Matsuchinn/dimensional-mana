package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 魔法抗拒：微风怪的风弹命中后留下的施法干扰。
 *
 * <p>本身不带任何属性，层数由 {@link MobSkills} 按「每层独立计时」登记；
 * 施法时每层把前摇延长 1 秒，最多 10 层。</p>
 */
public final class MagicRetortEffect extends MobEffect {
    public static final int MAX_STACKS = 10;
    public static final int TICKS_PER_STACK = 20;

    public MagicRetortEffect() {
        super(MobEffectCategory.HARMFUL, 0x8FE3C0);
    }
}
