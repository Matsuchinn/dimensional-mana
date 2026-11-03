package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * 魔石病：在没有魔素变异体变种的生物在高魔素区（浓度 &gt; 15）里滞留 120 秒后得到的慢性病。
 *
 * <p>病程由本效果自己走表（不依赖外部扫描）：每 {@value #STEP_INTERVAL} 刻（10 秒）升一级，
 * 同时把缓慢抬到同一等级——缓慢 10 级（amplifier {@value #MAX_AMPLIFIER}）是上限。
 * 到顶之后每秒（每 {@value #TICK_DAMAGE_INTERVAL} 刻）扣 1% 最大生命值的真实伤害，
 * 一直磨到死亡；死亡时由 {@link ManaStoneSystem} 额外撒 1～4 份魔晶粉。</p>
 *
 * <p>病程到顶前生物不会死于本效果，只是越来越慢、越来越重——给救助（金苹果喂食）留出时间窗。
 * 本效果持续时间用有限大数（{@value #DEFAULT_DURATION} 刻 ≈ 13.9 小时）而不是无限标记：
 * 原版无限效果的 duration 不递减，{@code duration % n} 的走表会失灵，所以这里用“实际上到不了期”的长计时。</p>
 *
 * <p>净化：金苹果喂食（{@link ManaStoneSystem#cure}）；牛奶之类的常规净化照原版规矩也能解，
 * 但病程里磨掉的血不会回来。</p>
 */
public final class ManaStoneDiseaseEffect extends MobEffect {
    /** 升一级缓慢的间隔：10 秒。 */
    public static final int STEP_INTERVAL = 200;
    /** 到顶后的扣血间隔：1 秒。 */
    public static final int TICK_DAMAGE_INTERVAL = 20;
    /** 缓慢上限：10 级（amplifier 9）。 */
    public static final int MAX_AMPLIFIER = 9;
    /** 到顶后每秒扣除的最大生命值比例。 */
    public static final float DAMAGE_RATIO = 0.01F;
    /** 实际意义上的“永久”：13.9 小时，正常流程里等不到它自然到期。 */
    public static final int DEFAULT_DURATION = 999_999;

    /** 石化灰紫色的尘埃，发作时飘在生物身上。 */
    private static final ParticleOptions STONE_DUST =
            new DustParticleOptions(new Vector3f(0.52F, 0.46F, 0.60F), 1.4F);

    public ManaStoneDiseaseEffect() {
        super(MobEffectCategory.HARMFUL, 0x86799B);
    }

    /** 每刻都过一遍表：20 刻一跳足够覆盖“升级”和“扣血”两种节奏。 */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % TICK_DAMAGE_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        MobEffectInstance self = entity.getEffect(GeneratedMod.MANA_STONE_DISEASE);
        if (self == null) return true;
        int duration = self.getDuration();
        int amp = self.getAmplifier();
        int slowAmp = Math.min(MAX_AMPLIFIER, amp);

        // 每 10 秒升一级（缓慢跟着抬），到顶后停住不再升。
        if (duration % STEP_INTERVAL == 0 && amp < MAX_AMPLIFIER) {
            slowAmp = Math.min(MAX_AMPLIFIER, amp + 1);
            entity.addEffect(new MobEffectInstance(GeneratedMod.MANA_STONE_DISEASE,
                    DEFAULT_DURATION, amp + 1, false, true, true));
        }
        // 缓慢常驻：每次走表都续到 12 秒，图标不会闪断。
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                STEP_INTERVAL / 2 + 40, slowAmp, false, true, true));

        // 石化灰紫的尘埃：病到哪一级身上都在飘，玩家一眼就能看出哪只怪病了。
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(STONE_DUST, entity.getX(), entity.getY() + entity.getBbHeight() * 0.55D,
                    entity.getZ(), amp >= MAX_AMPLIFIER ? 4 : 2, 0.28D, 0.34D, 0.28D, 0.0D);
        }

        // 到顶：每秒 1% 最大生命值的真实伤害，绕过护甲与无敌帧。
        if (amp >= MAX_AMPLIFIER) {
            entity.invulnerableTime = 0;
            try {
                entity.hurt(entity.damageSources().magic(), Math.max(0.5F, entity.getMaxHealth() * DAMAGE_RATIO));
            } finally {
                entity.invulnerableTime = 0;
            }
        }
        return true;
    }

    /** 挂上魔石病（只在服务端；重复施加由原版效果合并规则处理）。 */
    public static void apply(LivingEntity target) {
        if (target == null || target.level().isClientSide) return;
        target.addEffect(new MobEffectInstance(GeneratedMod.MANA_STONE_DISEASE,
                DEFAULT_DURATION, 0, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                STEP_INTERVAL / 2 + 40, 0, false, true, true));
    }
}
