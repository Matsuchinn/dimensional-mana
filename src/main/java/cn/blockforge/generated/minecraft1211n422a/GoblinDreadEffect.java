package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 【敬而远之】：击杀哥布林营地首领后挂在自己身上的「哥布林克星」威压。
 *
 * <p>效果本身不结算任何数值，只是一个标签，真正干活的是三处：</p>
 * <ul>
 *   <li>{@link GoblinSpawning} —— 持有者 64 格内不再自然刷出哥布林（巡逻队与营地都算）；</li>
 *   <li>{@link Goblin} 的「望风而逃」Goal —— 成年哥布林（战士 / 弓箭手 / 萨满 / 首领）看到持有者掉头就跑，
 *       移速 +50%；幼崽太蠢不跑，方便玩家追上献祭；</li>
 *   <li>{@link GoblinDread} —— 施加、刷新、献祭解除与播报。</li>
 * </ul>
 *
 * <p>图标是 {@code textures/mob_effect/goblin_dread.png} 里那张破碎的哥布林面具。</p>
 */
public final class GoblinDreadEffect extends MobEffect {
    /** 效果颜色：哥布林皮的暗橄榄绿。 */
    public static final int COLOR = 0x6E8B3D;

    public GoblinDreadEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
