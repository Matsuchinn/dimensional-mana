package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;
import org.joml.Vector3f;

import java.util.Set;

/**
 * 魔蚀（MagicErosion）：魔蚀织网者的蛛网、毒牙与毒液留下的持续侵蚀。
 *
 * <p>发作节奏按等级走：基础每 30 刻咬一口，每升 1 级间隔少 4 刻，最快 2 刻一次。
 * 每次发作造成 2 点真实伤害（{@code minecraft:magic}，绕过护甲），并把受击无敌帧
 * 直接清零——所以这一口永远不会被上一口吞掉；同时从目标的 {@link ManaData} 里扣 20 点魔素。</p>
 *
 * <p>叠加规则：重复施加刷新持续时间、等级 +1；持续时间传 {@code <= 0} 表示无限时长，
 * 一旦变成无限就不会再被有限的重复施加改回有限。</p>
 *
 * <p>净化限制：本效果是「强制状态」——{@code fillEffectCures} 留空，牛奶、蜂蜜、
 * 不死图腾、{@code /effect clear} 都解不掉（{@link CorruptedWeaverEvents#onEffectRemove}
 * 里还拦了一道）。只有 {@link #cleanse(LivingEntity)} 这条白名单通道能清：
 * 补魔剂、光系净化法术与死亡复活走的就是它。</p>
 */
public final class MagicErosionEffect extends MobEffect {
    /** 基础发作间隔（刻）。 */
    public static final int BASE_INTERVAL = 30;
    /** 每提升 1 级减少的间隔（刻）。 */
    public static final int INTERVAL_STEP = 4;
    /** 最短间隔（刻）。 */
    public static final int MIN_INTERVAL = 2;
    /** 每次发作的真实伤害。 */
    public static final float TICK_DAMAGE = 2.0F;
    /** 每次发作扣掉的魔素。 */
    public static final double TICK_MANA_DRAIN = 20.0D;
    /** 默认持续时间：10 秒。 */
    public static final int DEFAULT_DURATION = 200;

    private static final ParticleOptions EROSION_DUST =
            new DustParticleOptions(new Vector3f(0.55F, 0.16F, 0.78F), 1.1F);

    /** 「这次移除是净化白名单发起的」：只在服务端主线程同步触发，用 ThreadLocal 最省事。 */
    private static final ThreadLocal<Boolean> CLEANSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public MagicErosionEffect() {
        super(MobEffectCategory.HARMFUL, 0x7B2FBE);
    }

    /** 某一等级下的发作间隔（刻）。 */
    public static int interval(int amplifier) {
        return Math.max(MIN_INTERVAL, BASE_INTERVAL - Math.max(0, amplifier) * INTERVAL_STEP);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = interval(amplifier);
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        // 真实伤害：MAGIC 绕过护甲；受击前后都把无敌帧清零，保证每一下都实打实落下。
        entity.invulnerableTime = 0;
        try {
            entity.hurt(entity.damageSources().magic(), TICK_DAMAGE);
        } finally {
            entity.invulnerableTime = 0;
        }
        if (entity.isAlive()) ManaData.drainMana(entity, TICK_MANA_DRAIN);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(EROSION_DUST, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D, entity.getZ(),
                    4, 0.22D, 0.28D, 0.22D, 0.0D);
        }
        return true;
    }

    /**
     * 给目标挂上（或升一级）魔蚀。
     *
     * @param durationTicks 持续时间；{@code <= 0} 表示无限时长。
     */
    public static void apply(LivingEntity target, int durationTicks) {
        if (target == null || target.level().isClientSide) return;
        MobEffectInstance current = target.getEffect(GeneratedMod.MAGIC_EROSION);
        int level = current == null ? 0 : current.getAmplifier() + 1;
        int duration;
        if (durationTicks <= 0 || (current != null && current.isInfiniteDuration())) duration = -1;
        else duration = Math.max(20, durationTicks);
        target.addEffect(new MobEffectInstance(GeneratedMod.MAGIC_EROSION, duration, level, false, true, true));
    }

    /** 强制状态不受任何常规 cure 影响：这里刻意不填任何 cure。 */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }

    /**
     * 白名单净化：补魔剂、光系 / 净化法术、复活走这里。
     *
     * @return 目标身上确实有魔蚀并被清掉时返回 true。
     */
    public static boolean cleanse(LivingEntity target) {
        if (target == null || target.level().isClientSide) return false;
        if (!target.hasEffect(GeneratedMod.MAGIC_EROSION)) return false;
        CLEANSING.set(Boolean.TRUE);
        try {
            target.removeEffect(GeneratedMod.MAGIC_EROSION);
        } finally {
            CLEANSING.set(Boolean.FALSE);
        }
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                    12, 0.3D, 0.35D, 0.3D, 0.02D);
        }
        return true;
    }

    /** 当前线程是否正在走白名单净化。 */
    public static boolean cleansing() {
        return Boolean.TRUE.equals(CLEANSING.get());
    }
}
