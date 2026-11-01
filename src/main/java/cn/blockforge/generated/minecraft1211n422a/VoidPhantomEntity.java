package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 虚无幻影（Void Phantom）：虚空蚀影挨打时撕下来的一道影子。
 *
 * <p>设计稿里的定位是「自杀式分身」：</p>
 * <ul>
 *   <li>生命 10、移速 0.4，只会近战，每一口 8 点奥术伤害，<b>自己扣 1 点血</b>；</li>
 *   <li>死亡时对周围 4 格炸一圈 16 点奥术伤害，并把<b>生前打出的总伤害的一半</b>
 *       回喂给 32 格内的本体；</li>
 *   <li>最多同时存在 8 个（限制写在本体 {@link VoidShadeEntity#MAX_PHANTOMS} 那边），
 *       本体开了眼睛状态时不再产生新的幻影。</li>
 * </ul>
 *
 * <p>渲染上半透明、带末影粒子（{@link VoidPhantomRenderer}）；它不参与自然生成，
 * 只能由本体在 {@link VoidShadeEntity#spawnPhantom()} 里造出来。</p>
 */
public final class VoidPhantomEntity extends EnderMan {
    /** 生命上限。 */
    public static final float MAX_HEALTH = 10.0F;
    /** 移速：0.4（比原版末影人 0.3 快三成多，追得上逃跑的目标）。 */
    public static final double MOVEMENT_SPEED = 0.4D;
    /** 每一口咬的奥术伤害。 */
    public static final float BITE_ARCANE = 8.0F;
    /** 每咬一口自己掉多少血。 */
    public static final float SELF_DAMAGE = 1.0F;
    /** 死亡自爆的半径与伤害。 */
    public static final double DEATH_RADIUS = 4.0D;
    public static final float DEATH_ARCANE = 16.0F;
    /** 回喂本体的距离与比例。 */
    public static final double FEED_RANGE = 32.0D;
    public static final double FEED_RATIO = 0.5D;

    private static final DustParticleOptions VOID_DUST =
            new DustParticleOptions(new Vector3f(0.44F, 0.18F, 0.78F), 1.3F);

    private UUID ownerId;
    private double damageDealt;
    private boolean sacrificing;
    /** 找不到本体之后还要苟活多久再消散（刻）。 */
    private int orphanTicks;

    public VoidPhantomEntity(EntityType<? extends VoidPhantomEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        // 影子不怕水，也不该绕开水走。
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return EnderMan.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    public void setup(ServerLevel level, VoidShadeEntity owner) {
        this.ownerId = owner.getUUID();
        this.setTarget(owner.getTarget());
    }

    public boolean isOwnedBy(Entity entity) {
        return entity != null && ownerId != null && ownerId.equals(entity.getUUID());
    }

    public boolean isSacrificing() {
        return sacrificing;
    }

    public void setSacrificing(boolean value) {
        this.sacrificing = value;
    }

    /** 本体；本体没了返回 null。 */
    public VoidShadeEntity owner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(ownerId);
        return entity instanceof VoidShadeEntity shade ? shade : null;
    }

    /** 幻影是魔素凝出来的，不怕水。 */
    @Override
    public boolean isSensitiveToWater() {
        return false;
    }

    /** 幻影不瞬移：它靠走位贴上去咬，瞬移会让「自杀式分身」变得没法预判。 */
    @Override
    protected boolean teleport() {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ---------------------------------------------------------------- 存档
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("PhantomOwner", ownerId);
        tag.putDouble("PhantomDamage", damageDealt);
        tag.putBoolean("PhantomSacrificing", sacrificing);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("PhantomOwner")) ownerId = tag.getUUID("PhantomOwner");
        damageDealt = tag.getDouble("PhantomDamage");
        sacrificing = tag.getBoolean("PhantomSacrificing");
    }

    // ---------------------------------------------------------------- 战斗
    /** 一口 8 点奥术伤害，然后自己掉 1 点血（扣到 0 就当场炸掉）。 */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        if (level().isClientSide) return false;
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        boolean hit = SpellDamage.hitElement(serverLevel, this, living, MagicEquipment.ARCANE, BITE_ARCANE);
        if (hit) {
            damageDealt += BITE_ARCANE;
            serverLevel.sendParticles(VOID_DUST,
                    living.getX(), living.getY() + living.getBbHeight() * 0.5D, living.getZ(),
                    10, 0.3D, 0.4D, 0.3D, 0.04D);
        }
        // 自杀式攻击：咬完自己少一格血（不触发护甲 / 抗性，直接扣）。
        setHealth(Math.max(0.0F, getHealth() - SELF_DAMAGE));
        if (getHealth() <= 0.0F) {
            die(serverLevel.damageSources().magic());
        }
        return hit;
    }

    // ---------------------------------------------------------------- 死亡结算
    /**
     * 死亡：4 格半径 16 点奥术爆炸，并把生前总伤害的 50% 回喂给 32 格内的本体。
     *
     * <p>由 {@link VoidShadeEvents#onLivingDeath} 调用 —— 走事件而不是 {@code die()}，
     * 是因为 {{@code /kill} 这类来源也要正常结算。</p>
     */
    public void explodeAndFeed() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 center = position();
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(DEATH_RADIUS))) {
            if (victim == this || !victim.isAlive()) continue;
            // 自爆不伤自己人：本体、同门幻影、魔蚀末影之眼一律不吃。
            if (isAlly(victim)) continue;
            SpellDamage.hitElement(serverLevel, this, victim, MagicEquipment.ARCANE, DEATH_ARCANE);
        }
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5D, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.5D, center.z,
                30, 0.5D, 0.5D, 0.5D, 0.35D);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.1F, 0.5F);
        // 回喂：总伤害的一半，前提是本尊还在 32 格内。
        VoidShadeEntity owner = owner();
        if (owner != null && owner.distanceToSqr(this) <= FEED_RANGE * FEED_RANGE) {
            float heal = (float) (damageDealt * FEED_RATIO);
            if (heal > 0.0F) {
                owner.heal(heal);
                serverLevel.sendParticles(ParticleTypes.HEART,
                        owner.getX(), owner.getY(1.6D), owner.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        }
    }

    /** 自己人：本体、同门的其它幻影、本体身上的魔蚀末影之眼。 */
    private boolean isAlly(Entity entity) {
        if (isOwnedBy(entity)) return true;
        if (entity instanceof MagicEyeEntity eye) return eye.isOwnedBy(this);
        if (entity instanceof VoidPhantomEntity phantom) {
            return ownerId != null && ownerId.equals(phantom.ownerId);
        }
        return false;
    }

    /** 不参与自然消失（怪物默认会在 128 格外被清掉，这里保持安静）。 */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // 幻影不掉东西：它本身就是魔素凝出来的。
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextFloat() < 0.35F) {
                level().addParticle(ParticleTypes.PORTAL,
                        getX() + random.triangle(0.0D, 0.35D),
                        getY(0.2D + random.nextDouble() * 1.4D),
                        getZ() + random.triangle(0.0D, 0.35D), 0.0D, 0.01D, 0.0D);
            }
            return;
        }
        // 每次挨打/咬一口都要保证目标一致：本体在打谁，幻影就打谁。
        VoidShadeEntity owner = owner();
        if (owner != null) {
            orphanTicks = 0;
            if (getTarget() == null || !getTarget().isAlive()) {
                LivingEntity ownerTarget = owner.getTarget();
                if (ownerTarget != null && ownerTarget.isAlive()) setTarget(ownerTarget);
            }
            if (owner.hasEyes() && !sacrificing) {
                // 本体进入眼睛状态之后，残留的幻影自行消散（设计稿：这段时间不能有幻影）。
                discard();
                return;
            }
        } else if (++orphanTicks > 100) {
            discard();
        }
    }
}
