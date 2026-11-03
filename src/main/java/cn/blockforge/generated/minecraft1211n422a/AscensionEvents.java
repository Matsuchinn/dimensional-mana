package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * 升华药水的判定链：神圣庇护拦负面、破灭之力加伤害、死斗意志拒绝死亡、药效到期转反噬。
 *
 * <p>四个事件分别在 {@link GeneratedMod} 里接线：前三个挂在 {@link LivingDamageEvent} /
 * {@link MobEffectEvent.Applicable}，药效到期走 {@link MobEffectEvent.Expired}。
 * 真正的状态由 {@link AscensionEffect} 定义，喝药的动作在 {@link AscensionPotionItem}。</p>
 */
public final class AscensionEvents {
    /** 破灭之力：全类型伤害 ×1.5。 */
    public static final float MIGHT_DAMAGE_MULTIPLIER = 1.5F;
    /** 死斗意志每次保命消耗的最大魔素比例：10%。 */
    public static final double DEATHWARD_MANA_COST = 0.10D;
    /** 药效反噬的虚弱 / 缓慢等级：Ⅲ（amplifier 2）。 */
    public static final int BACKLASH_AMPLIFIER = 2;

    /**
     * 神圣庇护：期间新施加的任何负面效果直接被拦下。
     *
     * <p>只拦「新施加」的：喝药之前就已经挂在身上的负面状态不会被凭空清掉，
     * 那是「净化之躯」在喝下瞬间负责的事（见 {@link AscensionPotionItem#purify}）。</p>
     */
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (!player.hasEffect(GeneratedMod.ASCENSION_WARD)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;
        if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** 破灭之力：玩家打出的所有伤害 +50%（近战、弓箭、法术、真实伤害都走这条事件）。 */
    public static void onIncomingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!attacker.hasEffect(GeneratedMod.ASCENSION_MIGHT)) return;
        float amount = event.getNewDamage();
        // 必杀伤害不参与乘区，免得 Float.MAX_VALUE 溢出成 Infinity。
        if (LethalDamage.isLethal(event.getSource(), amount)) return;
        event.setNewDamage(amount * MIGHT_DAMAGE_MULTIPLIER);
    }

    /**
     * 死斗意志：这一下足以打死玩家时，把伤害压到「只剩 1 点血」，并扣 10% 最大魔素。
     *
     * <p>注册在最低优先级，拿到的是过完护甲、抗性、吸收盾的最终伤害，
     * 这样不会出现「锁了血还是死」的假死。魔素不够 10% 时效果失效，这一下按原样打死。</p>
     */
    public static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator() || player.isDeadOrDying()) return;
        if (!player.hasEffect(GeneratedMod.ASCENSION_DEATHWARD)) return;
        float damage = event.getNewDamage();
        if (damage <= 0.0F || LethalDamage.isLethal(event.getSource(), damage)) return;
        float total = player.getHealth() + player.getAbsorptionAmount();
        if (damage < total) return;

        ManaData.Snapshot data = ManaData.get(player);
        double cost = data.maxMana() * DEATHWARD_MANA_COST;
        if (cost <= 0.0D || data.mana() + 1.0E-6D < cost) {
            player.displayClientMessage(
                    Component.translatable("message.dimensional_mana.ascension.deathward_fail"), true);
            return;
        }

        ManaData.setMana(player, data.mana() - cost, true);
        event.setNewDamage(Math.max(0.0F, total - 1.0F));
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(),
                    30, 0.45D, 0.7D, 0.45D, 0.2D);
        }
        player.displayClientMessage(
                Component.translatable("message.dimensional_mana.ascension.deathward_saved",
                        (int) Math.ceil(cost)), true);
    }

    /**
     * 药效反噬：180 秒的「升华」一到期，立刻挂上 15 秒虚弱Ⅲ、缓慢Ⅲ，并把魔素清零。
     *
     * <p>用「效果到期」而不是自己计时：/effect clear、牛奶这些提前结束药效的方式
     * 在原版里不算到期，不会平白吃一发反噬。</p>
     */
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !instance.getEffect().is(GeneratedMod.ASCENSION)) return;
        player.addEffect(new MobEffectInstance(GeneratedMod.ASCENSION_BACKLASH,
                AscensionEffect.BACKLASH_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                AscensionEffect.BACKLASH_TICKS, BACKLASH_AMPLIFIER, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                AscensionEffect.BACKLASH_TICKS, BACKLASH_AMPLIFIER, false, true, true));
        ManaData.setMana(player, 0.0D, true);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.9F, 0.6F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(),
                    24, 0.4D, 0.7D, 0.4D, 0.03D);
        }
        player.displayClientMessage(
                Component.translatable("message.dimensional_mana.ascension.backlash"), false);
    }

    private AscensionEvents() {
    }
}
