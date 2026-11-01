package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 流浪剑士的事件接线：雇佣交互、友伤豁免、完美格挡、假动作格挡、远程格挡、瞬身闪避、喝药/集气打断。
 *
 * <p>r263：掉落长剑不再有自己的「举剑当盾」，那一整段（{@code onShieldBlock}）删掉了；
 * 玩家用这把剑右键走的是模组自带的斗气招架 {@link BattleQiParry}。</p>
 *
 * <p>所有判定都排在伤害真正落地之前（{@link LivingIncomingDamageEvent} 是 1.21.1 里护甲结算
 * 之前的受击入口），拦截就是 {@code event.setCanceled(true)}，不会再回头补一次伤害。</p>
 */
public final class SwordmasterEvents {
    private static final double PARRY_FRONT_DOT = 0.25D;
    private static final float GATHER_INTERRUPT_DAMAGE = 10.0F;

    private SwordmasterEvents() { }

    // ---------------------------------------------------------------- 受击
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof WanderingSwordmaster swordmaster)) return;
        if (swordmaster.level().isClientSide) return;
        DamageSource source = event.getSource();
        if (LethalDamage.isLethal(source, event.getAmount())) return;

        // 集气：单次伤害超过 10 点打断，并把这次已经恢复的斗气扣回去。
        if (swordmaster.action() == WanderingSwordmaster.Action.GATHER
                && event.getAmount() > GATHER_INTERRUPT_DAMAGE) {
            swordmaster.interruptGather("damage");
        }
        // 魔斗气凝聚：任何伤害都会打断（白念，魔素和斗气都不动）。
        if (swordmaster.action() == WanderingSwordmaster.Action.CONDENSE) {
            swordmaster.interruptCondense();
        }
        // 喝药：任何伤害都会打断，前摇期间的治疗不会落地。
        if (swordmaster.action() == WanderingSwordmaster.Action.POTION) {
            swordmaster.interruptPotion();
        }

        // 1) 雇佣友伤豁免：雇主发出的直接伤害、间接伤害、AOE、爆炸、弓箭全部拦下。
        if (swordmaster.isHired() && swordmaster.isOwnerSource(source)) {
            event.setCanceled(true);
            Vec3 at = source.getDirectEntity() != null
                    ? source.getDirectEntity().position()
                    : swordmaster.position().add(0.0D, 1.0D, 0.0D);
            swordmaster.playOwnerShield(at);
            swordmaster.playGuardFlourish();
            return;
        }

        // 2) 法术/远程锁定：60% 概率侧移 3 格，离开命中点。
        Entity threat = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        if ((source.getDirectEntity() instanceof Projectile || SpellDamage.isSpell(source))
                && swordmaster.tryBlinkDodge(threat)) {
            event.setCanceled(true);
            return;
        }

        // 3) 远程箭矢格挡：50% 概率消耗 2 点斗气挡下。
        if (source.getDirectEntity() instanceof Projectile && swordmaster.tryRangedGuard()) {
            event.setCanceled(true);
            return;
        }

        // 4) 近战格挡：正面才算。
        if (!(source.getDirectEntity() instanceof LivingEntity attacker)
                || source.getEntity() != attacker
                || SpellDamage.isSpell(source)) {
            return;
        }
        if (!facing(swordmaster, attacker)) return;
        // 4a) 假动作格挡（r263）：他故意举剑卖个破绽，对方真砍上来并且被挡下 → 瞬间回 50% 斗气。
        if (swordmaster.feintGuardActive()) {
            event.setCanceled(true);
            swordmaster.playFeintBlock(attacker);
            return;
        }
        // 4b) 近战完美格挡：斗气 ≥ 1000 才有资格；首次 100%，其余 50%，12 秒保底。
        if (swordmaster.battleQi() < WanderingSwordmaster.IAI_COST) return;
        if (swordmaster.rollPerfectParry(attacker)) {
            event.setCanceled(true);
            swordmaster.playPerfectParry(attacker);
        }
    }

    /** 攻击者是不是在剑士正面（用视线点积判断，背后的偷袭挡不了）。 */
    private static boolean facing(WanderingSwordmaster swordmaster, LivingEntity attacker) {
        Vec3 toAttacker = attacker.position().add(0.0D, attacker.getBbHeight() * 0.5D, 0.0D)
                .subtract(swordmaster.getEyePosition());
        if (toAttacker.lengthSqr() < 1.0E-4) return true;
        return swordmaster.getLookAngle().dot(toAttacker.normalize()) >= PARRY_FRONT_DOT;
    }

    // ---------------------------------------------------------------- 弹射物预判闪避
    /**
     * 箭矢/法术弹射物即将命中：60% 概率侧移 3 格，并取消这次命中。
     *
     * <p>只处理「真的会打到自己」的射线结果；从身边飞过去的箭不会触发，避免无意义地消耗冷却。</p>
     */
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof WanderingSwordmaster swordmaster)) return;
        if (swordmaster.level().isClientSide) return;
        Projectile projectile = event.getProjectile();
        // 雇佣期间雇主射来的箭由友伤豁免处理，不闪避。
        if (swordmaster.isHired() && swordmaster.ownerUUID() != null
                && projectile.getOwner() != null
                && swordmaster.ownerUUID().equals(projectile.getOwner().getUUID())) {
            return;
        }
        if (swordmaster.tryBlinkDodge(projectile)) {
            event.setCanceled(true);
            projectile.discard();
        }
    }

    // ---------------------------------------------------------------- 雇佣交互
    /** 手持 64 个金锭右键：雇佣；已经有人雇了就给一句提示。 */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getTarget() instanceof WanderingSwordmaster swordmaster)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSecondaryUseActive()) return;
        if (swordmaster.isHired()) {
            if (player.getMainHandItem().is(Items.GOLD_INGOT)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dimensional_mana.swordmaster.already_hired"), true);
                event.setCanceled(true);
            }
            return;
        }
        if (player.getMainHandItem().is(Items.GOLD_INGOT)) {
            swordmaster.hire(player);
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 死亡
    /** 剑士死亡：清掉雇佣名字，并让这座村庄进入补充冷却（10 分钟后可以再刷新一位）。 */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof WanderingSwordmaster swordmaster
                && swordmaster.level() instanceof ServerLevel level) {
            swordmaster.dismiss();
            SwordmasterVillageData.get(level).markDefeated(level, swordmaster.homePos(), level.getGameTime());
        }
        // 雇主死亡：所有维度、所有受雇于他的剑士立刻解除雇佣。
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            for (ServerLevel level : player.getServer().getAllLevels()) {
                for (WanderingSwordmaster swordmaster : level.getEntitiesOfClass(WanderingSwordmaster.class,
                        new net.minecraft.world.phys.AABB(-3.0E7D, level.getMinBuildHeight(), -3.0E7D,
                                3.0E7D, level.getMaxBuildHeight(), 3.0E7D),
                        WanderingSwordmaster::isHired)) {
                    if (player.getUUID().equals(swordmaster.ownerUUID())) swordmaster.dismiss();
                }
            }
        }
    }

}
