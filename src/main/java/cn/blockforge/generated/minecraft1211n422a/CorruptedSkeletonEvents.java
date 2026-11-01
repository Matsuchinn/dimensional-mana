package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 魔蚀骸骨的事件接线：两件事。
 *
 * <ol>
 *   <li>死亡时炸出一轮魂火箭矢的死亡弹幕。
 *       「击杀者」优先取伤害来源里的生物；如果这只骸骨是被 /kill、坠落这类没有凶手的伤害杀掉的，
 *       就退回它最后记住的仇恨对象。两者都没有时箭矢照样射出去，只是不会掉头追踪。</li>
 *   <li>r252：作为击杀者收尾时魂火大盛——击杀任何目标后把血量直接回满，
 *       配一圈魂火粒子做反馈（近战、巨矢、死亡弹幕都算它头上，走伤害来源的归属）。</li>
 * </ol>
 */
public final class CorruptedSkeletonEvents {
    private CorruptedSkeletonEvents() { }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        LivingEntity killer = event.getSource().getEntity() instanceof LivingEntity living ? living : null;

        // ① 骸骨自己死了：死亡弹幕。
        if (victim instanceof CorruptedSkeleton skeleton) {
            skeleton.deathVolley(killer != null ? killer : skeleton.getLastHurtByMob());
        }

        // ② 骸骨杀死了别人：回满血（r252）。
        if (killer instanceof CorruptedSkeleton hunter && hunter.isAlive() && hunter != victim) {
            if (hunter.getHealth() < hunter.getMaxHealth()) {
                hunter.setHealth(hunter.getMaxHealth());
            }
            if (hunter.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        hunter.getX(), hunter.getY(0.5D), hunter.getZ(),
                        22, 0.45D, 0.7D, 0.45D, 0.03D);
            }
        }
    }
}
