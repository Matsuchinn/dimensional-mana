package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 畸变魔晶片：晶化行尸「晶爆尸潮」炸开的紫色碎片。
 *
 * <p>初速 16 m/s（0.8 格/刻），带轻微下坠，<b>无穿透</b>——撞到第一个生物就碎：
 * 造成 24 点奥术伤害，并附加 25 刻瞬间伤害 II。打到方块或在 100 刻内没打中任何东西同样碎裂。</p>
 */
public final class DistortedCrystalShardEntity extends Projectile {
    /** 掉落物形态（0~4 个畸变魔晶片）与弹片共用的物品 id。 */
    public static final String ITEM_ID = "distorted_crystal_shard";
    /** 16 m/s = 0.8 格/刻。 */
    private static final double SPEED = 0.8;
    private static final int MAX_AGE = 100;

    /** 引爆者引用：本体已经 discard，这里只留 Java 引用做伤害归属。 */
    private transient LivingEntity blastCaster;
    private UUID casterId;
    private int age;

    public DistortedCrystalShardEntity(EntityType<? extends DistortedCrystalShardEntity> type,
                                       net.minecraft.world.level.Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** 起爆时调用：{@code direction} 必须是单位向量。 */
    public void launch(LivingEntity caster, Vec3 direction) {
        blastCaster = caster;
        casterId = caster == null ? null : caster.getUUID();
        setDeltaMovement(direction.normalize().scale(SPEED));
        applyRotationFrom(direction);
    }

    private void applyRotationFrom(Vec3 direction) {
        Vec3 d = direction.normalize();
        setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        setXRot((float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y)))));
    }

    /**
     * 被斗气完美格挡弹回来：掉头，归属换成弹反的玩家，原来的施法者关系作废。
     *
     * <p>{@link #firstTarget} 会跳过 {@code getOwner()}，所以掉头之后它不会再打回弹反的人，
     * 只会沿原路飞回去撞上谁算谁的。</p>
     */
    public void reflectBy(LivingEntity parrier) {
        setOwner(parrier);
        this.blastCaster = null;
        this.casterId = null;
        Vec3 motion = getDeltaMovement();
        Vec3 back = motion.lengthSqr() < 1.0E-4 ? getLookAngle().scale(-1.0) : motion.scale(-1.0);
        setDeltaMovement(back);
        applyRotationFrom(back);
        setPos(position().add(back.normalize().scale(0.5)));
        hasImpulse = true;
    }

    /** 伤害归属：优先内存引用，重载存档后按 UUID 找回。 */
    private LivingEntity caster() {
        if (blastCaster != null) return blastCaster;
        if (casterId == null || !(level() instanceof ServerLevel level)) return null;
        Entity found = level.getEntity(casterId);
        if (found instanceof LivingEntity living) blastCaster = living;
        return blastCaster;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 2 == 0) {
                level().addParticle(CrystallizedZombie.CRYSTAL_DUST,
                        getX(), getY(), getZ(), 0.02, 0.02, 0.02);
            }
            return;
        }
        if (++age > MAX_AGE || !level().hasChunkAt(blockPosition())) {
            shatter();
            discard();
            return;
        }
        Vec3 start = position();
        Vec3 delta = getDeltaMovement();
        Vec3 end = start.add(delta);

        // 无穿透：取航线上第一个能命中的生物。
        LivingEntity victim = firstTarget(start, end);
        if (victim != null) {
            // 斗气招架：这一发飞到玩家身上时先问招架系统 —— 完美格挡会让晶片原地掉头。
            if (victim instanceof ServerPlayer player && BattleQiParry.onIncomingProjectile(player, this)) return;
            setPos(victim.getBoundingBox().getCenter());
            hitLiving(victim);
            shatter();
            discard();
            return;
        }
        // 撞方块同样碎裂。
        HitResult block = level().clip(new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) {
            setPos(block.getLocation());
            shatter();
            discard();
            return;
        }
        setPos(end);
        // 轻微下坠，让弹道有一点抛物线味道。
        Vec3 next = delta.add(0, -0.012, 0);
        setDeltaMovement(next);
        applyRotationFrom(next);
    }

    private LivingEntity firstTarget(Vec3 start, Vec3 end) {
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(0.45),
                entity -> entity.isAlive() && !(entity instanceof CrystallizedZombie)
                        && entity != getOwner()
                        && (caster() == null || entity != caster()) && !entity.isSpectator());
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (candidate.getBoundingBox().inflate(0.12).clip(start, end).isEmpty()) continue;
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(start);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 命中生物：24 点奥术 + 25 刻瞬间伤害 II。 */
    private void hitLiving(LivingEntity victim) {
        if (!(level() instanceof ServerLevel level)) return;
        LivingEntity caster = caster();
        if (caster != null) {
            SpellDamage.hitElement(level, caster, victim, MagicEquipment.ARCANE, 24.0F);
        } else {
            SpellDamage.apply(victim, SpellDamage.elementSource(level, null, MagicEquipment.ARCANE), 24.0F);
        }
        // 瞬间伤害 II（对亡灵是治疗，和原版行为一致）：25 刻。
        victim.addEffect(new MobEffectInstance(MobEffects.HARM, 25, 1, false, true));
    }

    private void shatter() {
        if (!(level() instanceof ServerLevel level)) return;
        level.sendParticles(CrystallizedZombie.CRYSTAL_DUST,
                getX(), getY(), getZ(), 8, 0.14, 0.14, 0.14, 0.01);
        level.sendParticles(ParticleTypes.END_ROD,
                getX(), getY(), getZ(), 4, 0.1, 0.1, 0.1, 0.01);
        level.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CRYSTALLIZED_ZOMBIE_SHATTER.get(), SoundSource.HOSTILE, 0.45F, 1.7F);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // 命中判定在 tick 里手动做了（要挑航线上的第一个目标），这里只兜底。
        if (result.getEntity() instanceof LivingEntity victim && victim != caster()
                && !(victim instanceof CrystallizedZombie)) {
            hitLiving(victim);
            shatter();
            discard();
        }
    }

    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult result) {
        shatter();
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ShardAge", age);
        if (casterId != null) tag.putUUID("ShardCaster", casterId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        age = tag.getInt("ShardAge");
        if (tag.hasUUID("ShardCaster")) casterId = tag.getUUID("ShardCaster");
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
