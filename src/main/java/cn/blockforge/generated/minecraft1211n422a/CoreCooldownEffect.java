package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 魔核冷却（CoreCooldown）：聚魔爆裂体放完「魔核爆裂」后进入的 200 刻虚弱窗口。
 *
 * <p>三段表现分别由三处负责：</p>
 * <ul>
 *   <li>不能移动 —— 施加时同时挂上缓慢 255，{@link ManaCreeper} 里再停一次寻路；</li>
 *   <li>渲染灰白 —— {@link ManaCreeperRenderer} 换一张去色的贴图；</li>
 *   <li>受伤 +50% —— {@link ManaCreeperEvents#onIncomingDamage} 统一乘 1.5。</li>
 * </ul>
 *
 * <p>这个效果本身只是个标记，不需要每刻结算。</p>
 */
public final class CoreCooldownEffect extends MobEffect {
    /** 效果颜色：偏冷的灰白，和灰白贴图对上。 */
    public static final int COLOR = 0xA8AEB8;

    public CoreCooldownEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
