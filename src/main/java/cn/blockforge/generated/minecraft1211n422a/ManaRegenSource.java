package cn.blockforge.generated.minecraft1211n422a;

/**
 * 「自己算恢复量」的生物接口。
 *
 * <p>模组里有些生物不走 {@link MobCombatSystem} 的通用恢复逻辑，而是自己在 tick 里回魔素 / 斗气
 * （流浪剑士的斗气每秒 10 点、哥布林萨满的施法回魔……）。这些恢复量从来不会写进通用 tag，
 * 于是专业鉴定眼镜读到的永远是 {@code mobManaRegen = 0} —— HUD 上显示「M恢复 0.0/s」，
 * 明明生物在回，玩家却以为坏了。</p>
 *
 * <p>实现这个接口之后，{@link ProfessionalAppraisalSystem} 会优先问生物本人「你每秒到底回多少」，
 * 数字和实际 tick 里加的一致；新加会自己恢复的生物时，实现这两个方法就能自动接上眼镜。</p>
 */
public interface ManaRegenSource {
    /** 每秒实际恢复的魔素（已经算上环境魔素浓度之类的倍率）。 */
    double manaRegenPerSecond();

    /** 每秒实际恢复的斗气（自己那部分 + 全局设置叠加之后的值）。 */
    double battleQiRegenPerSecond();
}
