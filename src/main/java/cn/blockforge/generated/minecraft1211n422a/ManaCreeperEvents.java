package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 聚魔爆裂体的事件接线。
 *
 * <p>两件事：</p>
 * <ul>
 *   <li>CoreCooldown 期间受到的伤害 ×1.5（设计稿的「受伤增加 50%」）；</li>
 *   <li>死亡时照常引爆一次紊乱爆炸 —— 被打死也得炸，不能安静地消散。</li>
 * </ul>
 *
 * <p>「魔素归零 → 定身 20 刻 → 爆炸」这条不挂在全局事件上，而是在
 * {@link ManaCreeper#tick()} 里每刻查一次自己的魔素：效果和监听玩家 tick 一样，
 * 但不用替每一刻扫描全世界。</p>
 */
public final class ManaCreeperEvents {
    private ManaCreeperEvents() { }

    /** 魔核冷却期间：受到的任何伤害放大 50%。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ManaCreeper creeper)) return;
        if (event.isCanceled() || !creeper.isCoreCooling()) return;
        event.setAmount(event.getAmount() * ManaCreeper.COOLDOWN_DAMAGE_MULTIPLIER);
    }

    /** 死亡 → 紊乱爆炸（只炸一次，重复调用由实体内部挡掉）。 */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof ManaCreeper creeper) creeper.chaosDetonate();
    }
}
