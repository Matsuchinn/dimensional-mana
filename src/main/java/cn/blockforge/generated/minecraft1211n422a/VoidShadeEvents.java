package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 虚空蚀影那一套的事件接线。
 *
 * <p>四件事：</p>
 * <ol>
 *   <li><b>受击分身</b>：{@link LivingDamageEvent.Post} 里，真正掉血之后在原地留一个虚无幻影
 *       （上限 8、内置 20 刻冷却，逻辑在 {@link VoidShadeEntity#spawnPhantom()}）；</li>
 *   <li><b>受击反击</b>：{@link LivingIncomingDamageEvent} 里，本体身上还有魔蚀末影之眼时，
 *       取消这次伤害、炸掉一颗眼睛、朝攻击者打一道 30° 扇形爆破（30 刻冷却）；</li>
 *   <li><b>幻影自爆</b>：虚无幻影死亡时炸一圈并把伤害的一半回喂本体；</li>
 *   <li><b>清理</b>：本体死亡时把场上所有眼睛与幻影一起清掉，不留残骸。</li>
 * </ol>
 *
 * <p>刷怪替换走 {@link CrystallizedZombieEvents#onFinalizeSpawn}（原版末影人 → 虚空蚀影），
 * 条件和其它魔素变异体完全一致：主世界、区块魔素浓度 ≥ 12、50% 概率。</p>
 */
public final class VoidShadeEvents {
    private VoidShadeEvents() { }

    /** 受击反击：身上还有眼睛时，这一下直接免掉，代价是炸掉一颗眼睛。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof VoidShadeEntity shade) || shade.level().isClientSide) return;
        if (event.getAmount() <= 0.0F) return;
        if (shade.nearbyEyes().isEmpty()) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity attacker)) return;
        if (shade.counterAttack(attacker)) event.setCanceled(true);
    }

    /** 掉血之后留一个分身。 */
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof VoidShadeEntity shade) || shade.level().isClientSide) return;
        if (event.getNewDamage() <= 0.0F) return;
        shade.spawnPhantom();
    }

    /** 幻影死亡：自爆 + 回喂本体。 */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof VoidPhantomEntity phantom) {
            phantom.explodeAndFeed();
        }
        if (event.getEntity() instanceof VoidShadeEntity shade) {
            shade.clearSummons();
        }
    }
}
