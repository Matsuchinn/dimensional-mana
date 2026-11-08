package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 畏缩：史莱姆待在驱逐火把 5×5 区块范围内时挂上的隐形标记。
 *
 * <p>它不结算任何数值，也不显示粒子或图标（施加时用 {@code visible=false}），
 * 只是一个“这货闻到植物气息了”的状态。真正让史莱姆掉头的是 {@link SlimeDreadGoal}，
 * 有了这个状态，别的模组 / 指令（比如清除效果的药水）也能看出它为什么在往外跑。</p>
 */
public final class SlimeDreadEffect extends MobEffect {
    /** 效果颜色：偏草药的暗绿。 */
    public static final int COLOR = 0x4E7A38;

    public SlimeDreadEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
