package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 【侵蚀凋零】射出的骷髅头：裹着紫色灵魂的凋零骷髅头。
 *
 * <p>沿用原版 {@link WitherSkull} 的外形与飞行手感（客户端直接用原版的骷髅渲染器），
 * 只把命中结算换掉：10 点真实伤害（走 {@code magic} 伤害，绕开护甲）、【凋零Ⅲ】+
 * 【魔蚀Ⅲ】各 10 秒，并且在状态期间封住目标的治疗（{@link CorruptedWitherEvents#sealHealing}）。</p>
 *
 * <p><b>会炸地形</b>：原版 {@code WitherSkull.onHit} 命中后会调一次
 * {@code Level.explode(..., ExplosionInteraction.MOB)}，但那次爆炸会连自己的幻影、
 * 聚合体一起炸。这里自己接管命中：只对敌人结算侵蚀伤害，再调
 * {@link CorruptedWitherBlast#breakBlocks} 按预算炸一小圈方块，并补上爆炸音效。</p>
 *
 * <p><b>魂火拖尾</b>：客户端飞行时沿上一刻到当前刻的轨迹撒一串
 * {@link ParticleTypes#SOUL_FIRE_FLAME 魂火}，速度越快撒得越密，远看就是一条蓝白色的火尾。</p>
 */
public final class CorruptedWitherSkull extends WitherSkull {
    /** 命中方块时炸掉的地形半径。 */
    private static final double BLOCK_CRATER_RADIUS = 1.7D;
    /** 命中方块时最多炸多少格。 */
    private static final int BLOCK_CRATER_BUDGET = 28;
    /** 命中生物时在脚下炸出的坑小一点。 */
    private static final double ENTITY_CRATER_RADIUS = 1.2D;
    private static final int ENTITY_CRATER_BUDGET = 14;

    public CorruptedWitherSkull(EntityType<? extends CorruptedWitherSkull> type, Level level) {
        super(type, level);
    }

    /**
     * 自己分派命中，<b>故意不调用父类的 {@code onHit}</b>：父类在末尾还会再调一次
     * {@code Level.explode(..., MOB)}，那一下会绕开这里的友军过滤、把幻影和聚合体一起炸。
     * 地形破坏交给 {@link CorruptedWitherBlast}，只动方块、不动自己人。
     */
    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            onHitEntity((EntityHitResult)result);
        } else if (result.getType() == HitResult.Type.BLOCK) {
            onHitBlock((BlockHitResult)result);
        } else {
            discard();
        }
    }

    /**
     * 魂火拖尾：只在自己这一种骷髅头上画，原版凋零头（{@link net.minecraft.world.entity.projectile.WitherSkull}）
     * 不受影响。
     */
    @Override
    public void tick() {
        Vec3 previous = position();
        super.tick();
        if (!level().isClientSide) return;
        Vec3 current = position();
        double distance = previous.distanceTo(current);
        // 每格撒 3 颗左右；高速飞行时轨迹是连续的，不会出现一颗颗断开的点。
        // 36 连发时同屏可能有几十颗骷髅头，上限压到 8 颗 / 刻，避免粒子把帧数吃掉。
        int steps = Math.min(8, Math.max(2, (int)Math.ceil(distance * 3.0D)));
        for (int i = 0; i < steps; i++) {
            double t = (i + random.nextDouble()) / steps;
            Vec3 point = previous.lerp(current, t);
            double jitter = 0.16D;
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    point.x + (random.nextDouble() - 0.5D) * jitter,
                    point.y + (random.nextDouble() - 0.5D) * jitter,
                    point.z + (random.nextDouble() - 0.5D) * jitter,
                    0.0D, 0.01D, 0.0D);
            // 每三颗掺一颗普通魂烟，尾迹边缘会散开一点，不是一根死板的蓝线。
            if (i % 3 == 0) {
                level().addParticle(ParticleTypes.SOUL, point.x, point.y, point.z,
                        0.0D, 0.015D, 0.0D);
            }
        }
    }

    /** 施法者本人和它的召唤物（幻影 / 聚合体）+ 自家的别的骷髅头。 */
    private boolean isFriendly(Entity entity) {
        if (entity == null) return false;
        Entity owner = getOwner();
        if (owner == null) return false;
        if (entity == owner) return true;
        return owner instanceof CorruptedWither wither && wither.isOwnSummon(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            discard();
            return;
        }
        if (!(hit.getEntity() instanceof LivingEntity target)) {
            discard();
            return;
        }
        // 挡在弹道上的自家幻影 / 聚合体不算命中：直接穿过去消散，别把分身打成重伤。
        if (isFriendly(target)) {
            discard();
            return;
        }
        Vec3 at = hit.getLocation();
        target.invulnerableTime = 0;
        target.hurt(serverLevel.damageSources().magic(), CorruptedWither.EROSION_SKULL_DAMAGE);
        target.invulnerableTime = 0;
        target.addEffect(new MobEffectInstance(MobEffects.WITHER,
                CorruptedWither.EROSION_STATUS_TICKS, 2, false, true, true));
        // 魔蚀走织网者那套叠加规则：每挨一发升一级，时间刷新。
        MagicErosionEffect.apply(target, CorruptedWither.EROSION_STATUS_TICKS);
        CorruptedWitherEvents.sealHealing(target, CorruptedWither.EROSION_STATUS_TICKS);
        serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                CorruptedWitherSounds.get("hurt"), SoundSource.HOSTILE, 1.4F, 1.15F);
        // 打在生物身上也会在脚下啃出一个小坑：这就是「撞击会破坏方块」。
        CorruptedWitherBlast.breakBlocks(serverLevel, at, ENTITY_CRATER_RADIUS, ENTITY_CRATER_BUDGET);
        CorruptedWitherBlast.playImpact(serverLevel, at, 1.1F, 1.35F);
        discard();
    }

    /**
     * 打在方块上：保留方块自己的 {@code onProjectileHit} 反应（按钮、音符盒之类），
     * 然后把落点周围炸出一个小坑、给一圈侵蚀冲击，并播爆炸音效。
     */
    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!(level() instanceof ServerLevel serverLevel)) {
            discard();
            return;
        }
        Vec3 at = hit.getLocation();
        double radius = 1.8D;
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(radius),
                entity -> entity.isAlive() && !isFriendly(entity));
        for (LivingEntity victim : victims) {
            Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
            if (center.distanceToSqr(at) > radius * radius) continue;
            victim.invulnerableTime = 0;
            victim.hurt(serverLevel.damageSources().magic(), CorruptedWither.EROSION_SKULL_DAMAGE * 0.6F);
            victim.invulnerableTime = 0;
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER,
                    CorruptedWither.EROSION_STATUS_TICKS, 2, false, true, true));
            MagicErosionEffect.apply(victim, CorruptedWither.EROSION_STATUS_TICKS);
        }
        serverLevel.playSound(null, at.x, at.y, at.z, CorruptedWitherSounds.get("hurt"),
                SoundSource.HOSTILE, 1.1F, 1.5F);
        CorruptedWitherBlast.breakBlocks(serverLevel, at, BLOCK_CRATER_RADIUS, BLOCK_CRATER_BUDGET);
        CorruptedWitherBlast.playImpact(serverLevel, at, 1.3F, 1.2F);
        discard();
    }
}
