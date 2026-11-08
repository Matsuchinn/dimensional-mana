package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * 盾牌专属词条【弹反】与三叉戟专属词条【戳刺】的实战结算。
 *
 * <table border="1">
 *   <caption>两个专属词条</caption>
 *   <tr><th>词条</th><th>结算位置</th><th>怎么算</th></tr>
 *   <tr><td>弹反（仅盾牌）</td><td>格挡事件</td><td>成功格挡后把这一下被挡住的伤害 × 词条百分比，
 *       以独立伤害类型反弹给攻击者</td></tr>
 *   <tr><td>戳刺（仅三叉戟）</td><td>受击事件</td><td>激流冲刺撞到目标时额外增伤，独立乘区；
 *       普通投掷走 {@link EquipmentAffixSystem#onRangedIncomingDamage}</td></tr>
 * </table>
 *
 * <p><b>弹反不会形成无限循环</b>，这里做了三层隔离：</p>
 * <ol>
 *   <li>反弹伤害用的是独立伤害类型 {@code dimensional_mana:parry_reflect}，它挂在
 *       {@code minecraft:bypasses_shield} 标签上，原版根本不会为它触发格挡，
 *       所以对方的盾牌不可能再弹回来；</li>
 *   <li>反弹伤害的伤害源不带任何实体（{@code DamageSource#getEntity()} 为空），
 *       于是不会触发物理吸血、连击、处决、见切、暴击、元素附加这些「攻击者收益」；</li>
 *   <li>{@link #REFLECTING} 兜底记录正在结算弹反的实体，万一别的模组把伤害类型换了，
 *       也不会在同一刻反复弹。</li>
 * </ol>
 */
public final class ShieldTridentEffects {
    /** 弹反用的独立伤害类型。 */
    private static final ResourceKey<DamageType> REFLECT = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "parry_reflect"));
    /** 正在结算弹反的实体，用来兜底断掉递归。 */
    private static final java.util.Set<java.util.UUID> REFLECTING = new java.util.HashSet<>();
    /** 弹反伤害的上限：防止异常数值（例如被别的模组叠到天文数字）直接把服务器打崩。 */
    private static final float MAX_REFLECT_DAMAGE = 10000.0F;

    /**
     * 弹反：每次成功格挡后，把这一下被挡住的伤害按词条百分比反弹给攻击者。
     *
     * <p>放在 {@code LivingShieldBlockEvent}：这时候盾牌确实挡住了这一下，
     * {@link LivingShieldBlockEvent#getBlockedDamage()} 就是真正被挡下来的数字
     * （已经被别的模组改过也照样读最新的值）。</p>
     */
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked()) return;
        if (!(event.getEntity() instanceof Player blocker)) return;
        if (!(blocker.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getDamageSource();
        // 弹反伤害自己不可能再被弹（正常情况下它根本不吃格挡，这里是第二层保险）。
        if (isReflect(source)) return;
        double percent = EquipmentAffixSystem.parryReflectPercent(blocker);
        if (percent <= 0.0D) return;
        Entity attackerEntity = source == null ? null : source.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker) || attacker == blocker) return;
        if (!attacker.isAlive() || REFLECTING.contains(attacker.getUUID())) return;

        float blocked = event.getBlockedDamage();
        if (blocked <= 0.0F) return;
        float reflect = (float)Math.min(MAX_REFLECT_DAMAGE, blocked * percent / 100.0D);
        if (reflect <= 0.0F) return;
        if (!level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).containsKey(REFLECT)) return;

        boolean harmed;
        REFLECTING.add(attacker.getUUID());
        try {
            // 无实体伤害源：不触发吸血、暴击、连击这类攻击者收益，也进不了任何格挡判定。
            harmed = attacker.hurt(level.damageSources().source(REFLECT), reflect);
        } finally {
            REFLECTING.remove(attacker.getUUID());
        }
        // 表现：盾面一圈金属火星，攻击者身上再闪一下，配一个「铛」的格挡音。
        if (!harmed) return;
        var center = attacker.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 14, 0.3, 0.35, 0.3, 0.12);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, blocker.getX(), blocker.getY() + 1.0, blocker.getZ(),
                8, 0.4, 0.4, 0.4, 0.08);
        level.playSound(null, blocker.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0F, 0.75F);
    }

    /**
     * 戳刺：激流（Riptide）冲刺撞到目标时额外增伤。
     *
     * <p>激流的伤害不是 {@code ThrownTrident} 弹射物，而是「玩家本体高速撞上去」，
     * 伤害源是 {@code playerAttack(玩家)}、直接实体也是玩家，所以只能在这里按
     * {@link LivingEntity#isAutoSpinAttack()} 认出来。普通近战平A 时这个标记是 false，
     * 因此近战永远吃不到戳刺。</p>
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide || event.getAmount() <= 0.0F) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        // 投掷三叉戟是弹射物伤害，交给 onRangedIncomingDamage，别在这里重复乘一次。
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.ThrownTrident) return;
        if (!(source.getEntity() instanceof Player attacker) || source.getDirectEntity() != attacker) return;
        if (attacker == event.getEntity()) return;
        if (!attacker.isAutoSpinAttack()) return;
        if (!holdsTrident(attacker)) return;
        double percent = EquipmentAffixSystem.tridentThrustPercent(attacker);
        if (percent <= 0.0D) return;
        event.setAmount((float)(event.getAmount() * (1.0D + percent / 100.0D)));
    }

    /** 主手拿三叉戟就算（激流冲刺时武器一定在主手，副手再兜一层容错）。 */
    private static boolean holdsTrident(Player player) {
        ItemStack main = player.getMainHandItem();
        if (EquipmentAffixSystem.isTridentLike(main)) return true;
        return EquipmentAffixSystem.isTridentLike(player.getOffhandItem());
    }

    /** 这一下是不是弹反打出的伤害。 */
    public static boolean isReflect(DamageSource source) {
        return source != null && source.is(REFLECT);
    }

    private ShieldTridentEffects() { }
}
