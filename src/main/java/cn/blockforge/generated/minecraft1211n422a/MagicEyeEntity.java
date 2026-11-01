package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 魔蚀末影之眼（Magic Eye）：虚空蚀影用「虚空凝聚」把虚无幻影烧成的一颗眼球。
 *
 * <p>它不是生物，是一颗绕着本体转的浮游炮：</p>
 * <ul>
 *   <li>绕本体做圆周运动（半径 {@value #ORBIT_RADIUS} 格、高 1.8 格），自己不寻路、不碰撞；</li>
 *   <li>每 20 刻朝本体的仇恨目标打一条 <b>会被方块挡住</b>的紫色光束，32 格内命中造成
 *       8 点奥术伤害，并且强制无视无敌帧；</li>
 *   <li>存活 1200 刻（1 分钟）后自行消失；本体死了也会被 {@link VoidShadeEntity#clearSummons()} 清掉；</li>
 *   <li>本体挨打时会被炸掉一颗，并换来一道扇形爆破（见
 *       {@link VoidShadeEntity#counterAttack}）。</li>
 * </ul>
 */
public final class MagicEyeEntity extends Entity {
    /** 环绕半径 / 高度。 */
    public static final double ORBIT_RADIUS = 2.2D;
    public static final double ORBIT_HEIGHT = 1.9D;
    /** 射击射程（间隔由本体按「轮次」统一调度，见 {@link VoidShadeEntity#tickEyeVolley()}）。 */
    public static final int FIRE_INTERVAL = 20;
    public static final double FIRE_RANGE = 32.0D;
    /** 单发伤害。 */
    public static final float BEAM_ARCANE = 8.0F;
    /** 存活时长。 */
    public static final int LIFETIME = VoidShadeEntity.EYE_DURATION_TICKS;

    private static final DustParticleOptions EYE_DUST =
            new DustParticleOptions(new Vector3f(0.72F, 0.42F, 1.0F), 1.1F);

    private UUID ownerId;
    private int orbitIndex;
    private int life = LIFETIME;

    public MagicEyeEntity(EntityType<? extends MagicEyeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(ServerLevel level, VoidShadeEntity owner, int index) {
        this.ownerId = owner.getUUID();
        this.orbitIndex = index;
        this.life = LIFETIME;
        reposition(level, owner);
    }

    public boolean isOwnedBy(Entity entity) {
        return entity != null && ownerId != null && ownerId.equals(entity.getUUID());
    }

    public VoidShadeEntity owner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(ownerId);
        return entity instanceof VoidShadeEntity shade ? shade : null;
    }

    // ---------------------------------------------------------------- Entity 必需实现
    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("EyeOwner", ownerId);
        tag.putInt("EyeOrbit", orbitIndex);
        tag.putInt("EyeLife", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("EyeOwner")) ownerId = tag.getUUID("EyeOwner");
        orbitIndex = tag.getInt("EyeOrbit");
        life = tag.contains("EyeLife") ? tag.getInt("EyeLife") : LIFETIME;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextFloat() < 0.5F) {
                level().addParticle(EYE_DUST,
                        getX() + random.triangle(0.0D, 0.22D),
                        getY() + random.triangle(0.0D, 0.22D),
                        getZ() + random.triangle(0.0D, 0.22D), 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        VoidShadeEntity owner = owner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        if (--life <= 0) {
            discard();
            return;
        }
        reposition(level(), owner);
    }

    private void reposition(Level level, VoidShadeEntity owner) {
        double angle = Math.toRadians((tickCount * 6.0D) + orbitIndex * (360.0D / 6.0D));
        double x = owner.getX() + Math.cos(angle) * ORBIT_RADIUS;
        double z = owner.getZ() + Math.sin(angle) * ORBIT_RADIUS;
        double y = owner.getY() + ORBIT_HEIGHT + Math.sin((tickCount + orbitIndex * 20) * 0.12D) * 0.22D;
        setPos(x, y, z);
    }

    /** 射一发紫色光束：会被方块挡住，命中强制无视无敌帧。由本体轮次调度调用。 */
    public void fire(VoidShadeEntity owner, LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 from = position();
        Vec3 aim = target.getEyePosition().subtract(from);
        if (aim.lengthSqr() < 1.0E-4D) return;
        Vec3 dir = aim.normalize();
        Vec3 end = from.add(dir.scale(FIRE_RANGE));
        BlockHitResult blockHit = serverLevel.clip(new ClipContext(from, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = blockHit.getType() != HitResult.Type.MISS;
        if (blocked) end = blockHit.getLocation();
        AABB search = new AABB(from, end).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(serverLevel, this, from, end, search,
                entity -> entity instanceof LivingEntity living && living.isAlive()
                        && living != owner && !isOwnedBy(entity)
                        && !(entity instanceof Player player && (player.isSpectator() || player.isCreative())));
        // 光束的视觉：从眼睛一路拉到命中点，被墙挡下就停在墙面上。
        double length = from.distanceTo(end);
        for (double d = 0.4D; d < length; d += 0.45D) {
            Vec3 point = from.add(dir.scale(d));
            serverLevel.sendParticles(EYE_DUST, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENDER_EYE_LAUNCH, SoundSource.HOSTILE, 0.9F, 1.6F);
        if (entityHit == null) {
            if (blocked) {
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        end.x, end.y, end.z, 6, 0.15D, 0.15D, 0.15D, 0.05D);
            }
            return;
        }
        if (!(entityHit.getEntity() instanceof LivingEntity victim)) return;
        SpellDamage.hitElement(serverLevel, owner, victim, MagicEquipment.ARCANE, BEAM_ARCANE);
        // 设计稿要求强制无视无敌帧：法术伤害本来就走 bypasses_cooldown，这里再兜一手。
        victim.invulnerableTime = 0;
        serverLevel.sendParticles(ParticleTypes.WITCH,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                12, 0.3D, 0.4D, 0.3D, 0.02D);
    }
}
