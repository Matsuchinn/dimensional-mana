package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 魔素变异体之间的「认亲」规则：同族不误伤，挨到的那一下反过来变成奶。
 *
 * <p>五种变异体（晶化行尸、聚魔爆裂体、魔蚀骸骨、魔蚀织网者、虚空蚀影）同源，打起来
 * 不分敌我：近战互砍、爆炸波及、溅射魔法全算在内，<b>它们之间造成的伤害一律不结算</b>；
 * 被那一击"蹭到"的那只不会掉血，反而按这次伤害的 25% 回血，并冒一小团绿色粒子。</p>
 *
 * <p>不参与这套的：</p>
 * <ul>
 *   <li>自伤（聚魔爆裂体自己的爆炸）——它照常吃伤害，不然它永远炸不死；</li>
 *   <li>环境伤害（摔落、岩浆、窒息）——没有攻击者，跟"同族"无关；</li>
 *   <li>玩家和其它生物造成的伤害——正常结算。</li>
 * </ul>
 */
public final class ManaMutantAlliance {
    /** 被同族波及后按伤害的多少回血。 */
    public static final float HEAL_RATIO = 0.25F;

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (!CrystalClusterData.isManaMutant(victim)) return;
        Entity attacker = event.getSource().getEntity();
        // 没有攻击者 = 环境伤害；攻击者是它自己 = 自爆，都照常结算。
        if (attacker == null || attacker == victim) return;
        if (!CrystalClusterData.isManaMutant(attacker)) return;
        float amount = event.getAmount();
        event.setCanceled(true);
        if (amount <= 0.0F) return;
        if (victim.getHealth() >= victim.getMaxHealth()) return;
        victim.heal(Math.max(1.0F, amount * HEAL_RATIO));
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.6D, victim.getZ(),
                    3, 0.25D, 0.25D, 0.25D, 0.01D);
        }
    }

    private ManaMutantAlliance() {
    }
}
