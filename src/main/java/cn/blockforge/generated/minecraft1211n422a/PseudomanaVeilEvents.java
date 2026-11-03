package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 蔽魔药水的服务端钩子：挡索敌、管破隐。
 *
 * <p>索敌拦截挂在 {@link LivingChangeTargetEvent} 上——原版与模组生物的
 * 寻敌 Goal（{@code NearestAttackableTargetGoal} 一族，包括魔素变异体自用的
 * {@code PathAwareTargetGoal}）最终都要走 {@code Mob#setTarget}，
 * NeoForge 在这里统一发事件，所以一处否决就能盖住全部「主动攻击」。</p>
 *
 * <p>只管「新锁定的目标」：已经盯上持有者的生物不会被这层假面劝退；
 * 但持有者一挨打就破隐，假面也就保不住了。</p>
 */
public final class PseudomanaVeilEvents {

    /** 蔽魔期间，敌对生物不会主动把持有者选成攻击目标。 */
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide) return;
        if (!(mob instanceof Mob)) return;
        LivingEntity target = event.getNewAboutToBeSetTarget();
        // 解除目标（null）不在拦截范围；只挡「把蔽魔者设为新目标」这一步。
        if (target == null || target == mob) return;
        if (!target.hasEffect(GeneratedMod.PSEUDOMANA_VEIL)) return;
        event.setCanceled(true);
    }

    /** 破隐：持有者攻击别人、或自己受到伤害，假面立刻碎裂。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        // 挨打破隐（含弹射物、魔法、被挤落崖等一切伤害来源）。
        PseudomanaVeilEffect.breakVeil(event.getEntity());
        // 打人破隐：持弓、持法杖、喷毒都算「攻击者」发起的伤害。
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker != event.getEntity()) {
            PseudomanaVeilEffect.breakVeil(attacker);
        }
    }

    private PseudomanaVeilEvents() {
    }
}
