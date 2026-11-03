package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 蔽魔（Pseudomana Veil）：一层骗过魔素感知的假面。
 *
 * <p>持有期间，敌对生物（含魔素变异体）不会把持有者选为攻击目标——
 * 拦截点在 {@link PseudomanaVeilEvents} 对 {@code LivingChangeTargetEvent} 的否决上，
 * 所有走 {@code setTarget} 的索敌 AI 都会被挡下。已经锁定持有者的生物不受影响：
 * 蔽魔遮的是「新仇」，不销「旧恨」。</p>
 *
 * <p>破隐：一旦持有者主动攻击、释放法术或受到伤害，效果立刻中断
 * （{@link #breakVeil(LivingEntity)}）。破隐会散出一圈青色碎光作为提示。</p>
 */
public final class PseudomanaVeilEffect extends MobEffect {
    /** 瓶身 / 状态图标的基色：雾面的青灰蓝，像一块蒙尘的魔晶。 */
    public static final int COLOR = 0x4F7FA6;

    private static final ParticleOptions BREAK_SPARK = ParticleTypes.END_ROD;

    public PseudomanaVeilEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }

    /** 是否正处于蔽魔状态。 */
    public static boolean veiled(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide && entity.hasEffect(GeneratedMod.PSEUDOMANA_VEIL);
    }

    /**
     * 破隐：摘掉假面。服务端调用；没有效果时是免费的空操作。
     *
     * @return 确实摘掉了一层蔽魔时返回 true。
     */
    public static boolean breakVeil(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return false;
        if (!entity.hasEffect(GeneratedMod.PSEUDOMANA_VEIL)) return false;
        entity.removeEffect(GeneratedMod.PSEUDOMANA_VEIL);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(BREAK_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D, entity.getZ(),
                    18, 0.35D, 0.45D, 0.35D, 0.06D);
            level.sendParticles(ParticleTypes.ASH, entity.getX(), entity.getY() + entity.getBbHeight() * 0.4D, entity.getZ(),
                    8, 0.3D, 0.3D, 0.3D, 0.01D);
        }
        return true;
    }
}
