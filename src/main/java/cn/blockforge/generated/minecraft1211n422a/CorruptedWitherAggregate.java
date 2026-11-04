package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * 虚无聚合体：魔蚀凋零「虚无暴食」吃下伤害后凝出来的一颗晶体头颅。
 *
 * <p>蓝色吞魔法伤害、红色吞物理伤害，每颗最多装 100 点；平时绕着本体做贝塞尔式轨道运动，
 * 【虚无反噬】时全部飞出去，落地炸出一片 5 格范围的「虚无反噬」。</p>
 *
 * <p>命中时<b>不走原版 {@code Level.explode}</b>：伤害、实体搜索、方块破坏、地面高度全在服务端
 * 这一 tick 一次算完（{@link #detonate}）。方块由 {@link CorruptedWitherBlast} 按预算炸出
 * 一个球形坑（基岩、黑曜石留下），再叠一层自制的爆炸低频音；随后给 64 格内的玩家发
 * {@link CorruptedWitherBurstPayload}，由客户端 {@link CorruptedWitherBurstFx} 画红 / 蓝
 * 两套纯几何爆炸特效。</p>
 *
 * <p>渲染在 {@link CorruptedWitherAggregateRenderer}：抛光二十面体 + 内层流光，不发光的
 * 原版粒子一颗都不用。</p>
 */
public final class CorruptedWitherAggregate extends Entity {
    /** 环绕半径。 */
    public static final double ORBIT_RADIUS = 4.2D;
    /** 飞行速度（格 / 刻）。 */
    public static final double LAUNCH_SPEED = 1.1D;
    /** 飞弹最长存活刻数，超过就消散。 */
    public static final int MAX_LAUNCH_TICKS = 80;
    /** 单颗打出的伤害。 */
    public static final float LAUNCH_DAMAGE = 100.0F;
    /** 【虚无反噬】的爆炸半径（设计稿的 5×5）。 */
    public static final double BLAST_RADIUS = 5.0D;
    /** 真正被炸掉的方块半径：比伤害半径小，坑的边缘才不会被削得太夸张。 */
    public static final double CRATER_RADIUS = 3.0D;
    /** 一颗聚合体最多炸掉多少格方块。 */
    public static final int CRATER_BUDGET = 150;
    /** 爆炸特效同步半径。 */
    public static final double BURST_SYNC_RANGE = 64.0D;

    private static final EntityDataAccessor<Boolean> DATA_BLUE =
            SynchedEntityData.defineId(CorruptedWitherAggregate.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ORBITING =
            SynchedEntityData.defineId(CorruptedWitherAggregate.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerId;
    private UUID targetId;
    private int orbitIndex;
    private int launchTicks;
    /** 已经吞下的伤害（服务端权威值）。 */
    private float stored;

    public CorruptedWitherAggregate(EntityType<? extends CorruptedWitherAggregate> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(ServerLevel level, CorruptedWither owner, boolean blue, int index) {
        this.ownerId = owner.getUUID();
        this.orbitIndex = index;
        this.stored = 0.0F;
        this.launchTicks = 0;
        this.entityData.set(DATA_BLUE, blue);
        this.entityData.set(DATA_ORBITING, Boolean.TRUE);
        reposition(owner);
    }

    public boolean isBlue() {
        return this.entityData.get(DATA_BLUE);
    }

    public boolean isOrbiting() {
        return this.entityData.get(DATA_ORBITING);
    }

    public float stored() {
        return stored;
    }

    /** 吞下一段伤害，返回真正装进去的数值（超过 100 的部分留在外面）。 */
    public float store(float amount) {
        if (amount <= 0.0F) return 0.0F;
        float room = CorruptedWither.ABSORB_PER_AGGREGATE - stored;
        if (room <= 0.0F) return 0.0F;
        float taken = Math.min(room, amount);
        stored += taken;
        return taken;
    }

    public boolean isOwnedBy(Entity entity) {
        return entity != null && ownerId != null && ownerId.equals(entity.getUUID());
    }

    public CorruptedWither owner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(ownerId);
        return entity instanceof CorruptedWither wither ? wither : null;
    }

    /** 【虚无反噬】：脱离轨道，直冲目标。 */
    public void launch(LivingEntity target) {
        this.targetId = target == null ? null : target.getUUID();
        this.entityData.set(DATA_ORBITING, Boolean.FALSE);
        this.launchTicks = 0;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("shoot"),
                    SoundSource.HOSTILE, 1.0F, isBlue() ? 1.6F : 0.8F);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLUE, Boolean.TRUE);
        builder.define(DATA_ORBITING, Boolean.TRUE);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("AggOwner", ownerId);
        tag.putBoolean("AggBlue", isBlue());
        tag.putBoolean("AggOrbit", isOrbiting());
        tag.putInt("AggIndex", orbitIndex);
        tag.putFloat("AggStored", stored);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("AggOwner")) ownerId = tag.getUUID("AggOwner");
        this.entityData.set(DATA_BLUE, tag.getBoolean("AggBlue"));
        this.entityData.set(DATA_ORBITING, tag.getBoolean("AggOrbit"));
        orbitIndex = tag.getInt("AggIndex");
        stored = tag.getFloat("AggStored");
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
        // 超过 64 格不再渲染（设计稿的性能约束）。
        return distance < 4096.0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        CorruptedWither owner = owner();
        if (!isOrbiting()) {
            tickLaunch();
            return;
        }
        if (owner == null || !owner.isAlive() || isRemoved()) {
            discard();
            return;
        }
        reposition(owner);
    }

    private void reposition(CorruptedWither owner) {
        // 公转速度从 4.5°/刻降到 3.0°/刻：一圈约 2 秒，配合客户端插值看上去是平滑的滑行。
        double angle = Math.toRadians(tickCount * 3.0D + orbitIndex * 60.0D);
        double radius = ORBIT_RADIUS + Math.sin(tickCount * 0.05D + orbitIndex) * 0.5D;
        double x = owner.getX() + Math.cos(angle) * radius;
        double z = owner.getZ() + Math.sin(angle) * radius;
        double y = owner.getY() + 2.4D + Math.sin(tickCount * 0.07D + orbitIndex * 1.7D) * 0.7D;
        setPos(x, y, z);
    }

    private void tickLaunch() {
        if (++launchTicks > MAX_LAUNCH_TICKS) {
            discard();
            return;
        }
        LivingEntity target = resolveTarget();
        if (target == null) {
            discard();
            return;
        }
        Vec3 from = position();
        Vec3 to = target.getEyePosition();
        Vec3 step = to.subtract(from);
        if (step.lengthSqr() < 0.6D) {
            detonate(to);
            return;
        }
        Vec3 direction = step.normalize().scale(LAUNCH_SPEED);
        Vec3 next = from.add(direction);
        BlockHitResult blockHit = level().clip(new ClipContext(from, next, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            detonate(blockHit.getLocation());
            return;
        }
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, from, next,
                new AABB(from, next).inflate(0.8D),
                entity -> entity instanceof LivingEntity living && living.isAlive() && !isFriendly(living));
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity) {
            // 用射线命中点而不是目标脚底：炸点就在「贴脸」的位置，特效和伤害才对得上。
            detonate(entityHit.getLocation());
            return;
        }
        setPos(next.x, next.y, next.z);
    }

    private LivingEntity resolveTarget() {
        if (targetId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(targetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /**
     * 【虚无反噬】爆炸：5 格内的范围伤害 + 发包触发客户端特效。
     *
     * <p>这里<strong>不调用任何 {@code Level.explode}</strong>：原版爆炸的伤害、击退、方块判定
     * 都不可控。伤害、实体筛选、方块破坏、地面高度都在这一 tick 一次性算完：
     * 方块交给 {@link CorruptedWitherBlast#breakBlocks} 按半径和预算炸，
     * 再播自制的爆炸低频声 + 少量原版爆炸粒子，主力观感仍由客户端几何特效负责。</p>
     */
    private void detonate(Vec3 at) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            discard();
            return;
        }
        boolean blue = isBlue();
        CorruptedWither owner = owner();
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(BLAST_RADIUS), this::canBurst);
        // 以命中点为球心再做一次距离筛选：AABB 是方的，5×5 的爆炸得是圆的。
        double radiusSqr = BLAST_RADIUS * BLAST_RADIUS;
        for (LivingEntity victim : victims) {
            Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
            if (center.distanceToSqr(at) > radiusSqr) continue;
            // 红色（物理）可以被盾牌或完美格挡挡下；蓝色（奥术）不吃物理格挡。
            if (!blue && blocked(victim)) {
                serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 1.3F);
                continue;
            }
            victim.invulnerableTime = 0;
            if (blue) {
                SpellDamage.hitElement(serverLevel, owner, victim, MagicEquipment.ARCANE, LAUNCH_DAMAGE);
            } else {
                DamageSource source = owner == null
                        ? serverLevel.damageSources().magic()
                        : serverLevel.damageSources().mobProjectile(this, owner);
                victim.hurt(source, LAUNCH_DAMAGE);
            }
            victim.invulnerableTime = 0;
        }
        // 先发包：客户端画地面裂纹环要用「炸之前」的地面高度，必须赶在破块之前探。
        sendBurst(serverLevel, at, blue);
        // 再炸地形，最后叠音频：低频爆炸铺底，phase / beam 两层当作红蓝的区别。
        CorruptedWitherBlast.breakBlocks(serverLevel, at, CRATER_RADIUS, CRATER_BUDGET);
        CorruptedWitherBlast.playBlastSound(serverLevel, at, blue ? 2.6F : 2.9F, blue ? 1.35F : 0.72F);
        serverLevel.playSound(null, at.x, at.y, at.z, CorruptedWitherSounds.get("phase"),
                SoundSource.HOSTILE, 2.2F, blue ? 1.4F : 0.75F);
        serverLevel.playSound(null, at.x, at.y, at.z, CorruptedWitherSounds.get("beam"),
                SoundSource.HOSTILE, 1.3F, blue ? 1.6F : 0.55F);
        discard();
    }

    /** 爆炸候选：活着、不是施法者、不是自己的同伴（本体 / 其他聚合体 / 它的幻影）。 */
    private boolean canBurst(LivingEntity entity) {
        if (!entity.isAlive()) return false;
        return !isFriendly(entity);
    }

    /** 是不是施法者一方的（本体、它的幻影、它收着的别的聚合体）。 */
    private boolean isFriendly(Entity entity) {
        CorruptedWither owner = owner();
        return owner != null && owner.isOwnSummon(entity);
    }

    /** 盾牌举着，或者斗气完美招架，都算挡下（只对红色生效，调用方已判）。 */
    private static boolean blocked(LivingEntity victim) {
        if (!(victim instanceof Player player)) return false;
        return player.isBlocking() || BattleQiParry.isParrying(player);
    }

    /** 把「炸点 + 地面高度 + 颜色」发给周围 64 格内的玩家。 */
    private void sendBurst(ServerLevel serverLevel, Vec3 at, boolean blue) {
        float ground = (float)groundYAt(serverLevel, at);
        CorruptedWitherBurstPayload payload = new CorruptedWitherBurstPayload(at, ground, blue);
        double rangeSqr = BURST_SYNC_RANGE * BURST_SYNC_RANGE;
        for (ServerPlayer player : serverLevel.getPlayers(player -> player.distanceToSqr(at) < rangeSqr)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** 服务端提前往下探一次地面：客户端画裂纹环时就不用再查世界了。 */
    private double groundYAt(ServerLevel serverLevel, Vec3 at) {
        Vec3 from = at.add(0.0D, 0.2D, 0.0D);
        Vec3 to = at.add(0.0D, -8.0D, 0.0D);
        BlockHitResult hit = serverLevel.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? at.y - 1.0D : hit.getLocation().y;
    }
}
