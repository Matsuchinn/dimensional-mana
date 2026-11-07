package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 【虚无凋零射线】的本体：一根蓄力 7 秒、持续射击 10 秒的纯黑光束。
 *
 * <p>自己不做寻路，只跟着主人的眼睛走。蓄力期间每秒甩出一圈「扭曲光环」，把扫过区域的
 * 环境魔素浓度砍半（{@link CorruptedZoneData}，10 分钟后自然恢复）；开火后每刻沿光轴
 * 打一条破坏方块、对第一个命中的生物造成其当前生命 90% 的真实伤害。</p>
 *
 * <p><b>追猎</b>：光斑每刻都会检查当前目标。目标被打死 / 消失后，立刻在 64 格内重新挑
 * 一个最近的生物并把光轴转过去；周围一只生物都没有时，光轴以每秒 30° 缓慢自转，
 * 边转边粉碎方块，把「尽情破坏」演出来。</p>
 *
 * <p><b>持续笼罩</b>：同一个生物被光斑连续罩住超过 4 秒后，每秒额外吃 1000 点真实伤害
 * （走 {@code dimensional_mana:wither_beam_true}，绕过护甲、抗性、药效、附魔、盾牌与受击冷却）。
 * 这是给「限伤 / 高减伤 Boss」准备的手段：光斑一直粘着它，伤害就会越来越狠。</p>
 *
 * <p>方向的追踪速度锁在 45°/s（= 2.25°/刻）：玩家得跑得快才甩得掉光斑，但直线冲刺仍然能躲开。</p>
 */
public final class CorruptedWitherBeam extends Entity {
    /** 蓄力刻数：7 秒。 */
    public static final int CHARGE_TICKS = 140;
    /** 射击刻数：10 秒。 */
    public static final int FIRE_TICKS = 200;
    /** 每刻最多转多少度：45°/s（原来是 15°/s，玩家随便绕两步就甩开了，追不上）。 */
    public static final double MAX_TURN_PER_TICK = 45.0D / 20.0D;
    /** 光束长度。 */
    public static final double LENGTH = 64.0D;
    /** 命中伤害 = 目标当前生命的这个比例。 */
    public static final double DAMAGE_RATIO = 0.90D;
    /** 扭曲光环的半径与掉浓度时长（10 分钟 = 12000 刻）。 */
    public static final double HALO_RADIUS = 12.0D;
    public static final int ZONE_DURATION = 12_000;
    /** 被光斑连续笼罩多少刻之后开始吃额外真伤：4 秒。 */
    public static final int DWELL_TICKS = 80;
    /** 持续笼罩额外真伤的结算间隔：每秒一次。 */
    public static final int DWELL_DAMAGE_INTERVAL = 20;
    /** 持续笼罩每秒的额外真实伤害。 */
    public static final float DWELL_DAMAGE = 1_000.0F;
    /** 没有目标时的自转速度：每秒 30°（每刻 1.5°），十秒的射击期转大约六分之五圈。 */
    public static final double IDLE_SPIN_PER_TICK = 1.5D;
    /** 击杀目标后重新索敌的半径。 */
    public static final double RETARGET_RANGE = 64.0D;

    /** 持续笼罩真伤用的专用伤害类型：数据包里挂满了穿透标签。 */
    private static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "wither_beam_true"));

    private static final EntityDataAccessor<Boolean> DATA_FIRING =
            SynchedEntityData.defineId(CorruptedWitherBeam.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(CorruptedWitherBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
            SynchedEntityData.defineId(CorruptedWitherBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE =
            SynchedEntityData.defineId(CorruptedWitherBeam.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private UUID targetId;
    private int chargeTicks = CHARGE_TICKS;
    private int fireTicks = FIRE_TICKS;
    private int haloCooldown;
    private float yaw;
    private float pitch;
    /** 本刻开始时的角度（只在客户端渲染用）：服务端每刻才同步一次朝向，渲染器拿它跟当前值插值，转起来才不是一跳一跳的。 */
    private float prevAimYaw;
    private float prevAimPitch;
    /** 每个目标被光斑连续罩住的刻数（同一时刻只保留当前目标）。 */
    private final Map<UUID, Integer> dwellTicks = new HashMap<>();

    public CorruptedWitherBeam(EntityType<? extends CorruptedWitherBeam> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(ServerLevel level, CorruptedWither owner, LivingEntity target) {
        this.ownerId = owner.getUUID();
        this.targetId = target == null ? null : target.getUUID();
        Vec3 from = eyeOf(owner);
        Vec3 aim = target == null ? owner.getLookAngle() : target.getEyePosition().subtract(from);
        this.yaw = (float)(Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        this.pitch = (float)(Math.toDegrees(-Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        setPos(from.x, from.y, from.z);
        this.entityData.set(DATA_YAW, yaw);
        this.entityData.set(DATA_PITCH, pitch);
        this.entityData.set(DATA_FIRING, Boolean.FALSE);
        this.entityData.set(DATA_CHARGE, CHARGE_TICKS);
        level.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("charge"),
                SoundSource.HOSTILE, 3.0F, 0.85F);
    }

    public boolean isFiring() {
        return this.entityData.get(DATA_FIRING);
    }

    /** 0～1，蓄力进度；射击后恒为 1。 */
    public float chargeRatio() {
        int left = this.entityData.get(DATA_CHARGE);
        return Math.min(1.0F, 1.0F - left / (float)CHARGE_TICKS);
    }

    public float aimYaw() {
        return this.entityData.get(DATA_YAW);
    }

    public float aimPitch() {
        return this.entityData.get(DATA_PITCH);
    }

    /** 渲染用的偏航角：从本刻开始的角度平滑插值到当前同步值，消除每刻一跳的台阶。 */
    public float smoothAimYaw(float partialTick) {
        return Mth.rotLerp(partialTick, this.prevAimYaw, aimYaw());
    }

    /** 渲染用的俯仰角：同样按 partialTick 插值。 */
    public float smoothAimPitch(float partialTick) {
        return Mth.lerp(partialTick, this.prevAimPitch, aimPitch());
    }

    public Vec3 direction() {
        double yawRad = Math.toRadians(aimYaw());
        double pitchRad = Math.toRadians(aimPitch());
        double cosPitch = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
    }

    public Vec3 origin() {
        return position();
    }

    public Vec3 endPoint() {
        return origin().add(direction().scale(LENGTH));
    }

    private static Vec3 eyeOf(CorruptedWither owner) {
        return new Vec3(owner.getX(), owner.getY() + 2.8D, owner.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FIRING, Boolean.FALSE);
        builder.define(DATA_YAW, 0.0F);
        builder.define(DATA_PITCH, 0.0F);
        builder.define(DATA_CHARGE, CHARGE_TICKS);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("BeamOwner", ownerId);
        if (targetId != null) tag.putUUID("BeamTarget", targetId);
        tag.putFloat("BeamYaw", yaw);
        tag.putFloat("BeamPitch", pitch);
        tag.putInt("BeamCharge", chargeTicks);
        tag.putInt("BeamFire", fireTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("BeamOwner")) ownerId = tag.getUUID("BeamOwner");
        if (tag.hasUUID("BeamTarget")) targetId = tag.getUUID("BeamTarget");
        yaw = tag.getFloat("BeamYaw");
        pitch = tag.getFloat("BeamPitch");
        chargeTicks = tag.getInt("BeamCharge");
        fireTicks = tag.getInt("BeamFire");
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
        // 放宽到 128 格：具体的「只画 64 格内」由 CorruptedWitherBeamRenderer.shouldRender
        // 用「相机到线段最近点」重新判定（这里的 distance 只量得到起点）。
        return distance < 16384.0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // 记下本刻开始时的角度作为插值起点：下一刻的同步包到达前，渲染器
            // 都从它平滑过渡到当前值（见 smoothAimYaw / smoothAimPitch）。
            this.prevAimYaw = aimYaw();
            this.prevAimPitch = aimPitch();
            return;
        }
        CorruptedWither owner = owner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) return;
        // 光束始终从主人眼睛发出。
        setPos(eyeOf(owner).x, eyeOf(owner).y, eyeOf(owner).z);
        // 蓄力时只跟当前目标；开火后目标没了就重新索敌，连人也找不到就缓慢自转。
        updateAim(serverLevel, owner, chargeTicks <= 0);
        if (chargeTicks > 0) {
            chargeTicks--;
            this.entityData.set(DATA_CHARGE, chargeTicks);
            if (--haloCooldown <= 0) {
                haloCooldown = 20;
                CorruptedZoneData.get(serverLevel).add(blockPosition(), HALO_RADIUS, ZONE_DURATION,
                        serverLevel.getGameTime());
                serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("pulse"),
                        SoundSource.HOSTILE, 1.6F, 0.8F);
            }
            if (chargeTicks == 0) {
                this.entityData.set(DATA_FIRING, Boolean.TRUE);
                serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("beam"),
                        SoundSource.HOSTILE, 4.0F, 0.9F);
            }
            return;
        }
        if (--fireTicks <= 0) {
            discard();
            return;
        }
        fire(serverLevel, owner);
    }

    private CorruptedWither owner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(ownerId);
        return entity instanceof CorruptedWither wither ? wither : null;
    }

    private LivingEntity target(ServerLevel level) {
        if (targetId == null) return null;
        Entity entity = level.getEntity(targetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** 朝目标转头，每刻最多 {@link #MAX_TURN_PER_TICK} 度；没有目标时按需自转。 */
    private void updateAim(ServerLevel serverLevel, CorruptedWither owner, boolean firing) {
        LivingEntity target = resolveTarget(serverLevel, owner);
        if (target == null) {
            if (firing) spinIdle();
            return;
        }
        Vec3 delta = target.getEyePosition().subtract(origin());
        if (delta.lengthSqr() < 1.0E-4D) return;
        float wantedYaw = (float)(Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        float wantedPitch = (float)(Math.toDegrees(-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        yaw = turnTowards(yaw, wantedYaw, (float)MAX_TURN_PER_TICK);
        pitch = turnTowards(pitch, wantedPitch, (float)MAX_TURN_PER_TICK);
        this.entityData.set(DATA_YAW, yaw);
        this.entityData.set(DATA_PITCH, pitch);
    }

    /**
     * 重新挑一个目标：当前目标还活着就继续锁着，死了 / 消失了就立刻找附近最近的生物。
     *
     * <p>这是「击杀目标后继续追下一个」的关键：旧目标一失效，下一刻光轴就会以
     * {@link #MAX_TURN_PER_TICK} 的速度转过去，而不是停在原地。</p>
     */
    private LivingEntity resolveTarget(ServerLevel level, CorruptedWither owner) {
        LivingEntity current = target(level);
        if (current != null && isTargetable(current, owner)) return current;
        if (current != null) this.targetId = null;
        AABB box = getBoundingBox().inflate(RETARGET_RANGE);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> isTargetable(entity, owner))) {
            double distance = distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        this.targetId = best == null ? null : best.getUUID();
        return best;
    }

    /** 能被光斑盯上的对象：排除本体、自己人、音元素史莱姆和观战 / 创造玩家。 */
    private boolean isTargetable(LivingEntity entity, CorruptedWither owner) {
        if (!entity.isAlive() || entity.isRemoved()) return false;
        if (entity == owner || owner.isOwnSummon(entity)) return false;
        if (entity instanceof CorruptedWither || entity instanceof CorruptedWitherPhantom) return false;
        if (entity instanceof Player player) return !player.isCreative() && !player.isSpectator();
        return !CorruptedWither.isUntouchable(entity);
    }

    /** 周围一只生物都没有：光轴慢慢自转，边转边把挡路的方块磨掉。 */
    private void spinIdle() {
        yaw = (float)Mth.wrapDegrees(yaw + IDLE_SPIN_PER_TICK);
        // 俯仰角缓慢上下摆，光斑不会一直贴着同一条水平面，破坏范围更立体。
        pitch = (float)(Math.sin(tickCount * 0.02D) * 12.0D);
        this.entityData.set(DATA_YAW, yaw);
        this.entityData.set(DATA_PITCH, pitch);
    }

    private static float turnTowards(float current, float wanted, float maxStep) {
        float delta = net.minecraft.util.Mth.wrapDegrees(wanted - current);
        return current + net.minecraft.util.Mth.clamp(delta, -maxStep, maxStep);
    }

    /** 开火：先打生物（被生物挡住就不再穿过去），再沿光轴粉碎方块。 */
    private void fire(ServerLevel serverLevel, CorruptedWither owner) {
        Vec3 from = origin();
        Vec3 dir = direction();
        Vec3 end = from.add(dir.scale(LENGTH));
        AABB search = new AABB(from, end).inflate(1.5D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(serverLevel, this, from, end, search,
                entity -> entity instanceof LivingEntity living && isTargetable(living, owner));
        double damageDistance = LENGTH;
        LivingEntity victim = null;
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            victim = living;
            damageDistance = from.distanceTo(hit.getLocation());
            float amount = Math.max(1.0F, victim.getHealth() * (float)DAMAGE_RATIO);
            victim.invulnerableTime = 0;
            victim.hurt(serverLevel.damageSources().magic(), amount);
            victim.invulnerableTime = 0;
            CorruptedWitherEvents.sealHealing(victim, 100);
        }
        // 持续笼罩：同一个生物被光斑连着罩住超过 4 秒，之后每秒再额外吃 1000 点真伤。
        tickDwell(serverLevel, owner, victim);
        // 每 2 刻碎一次、每次最多碎 60 格：既能开路，又不会一口气刷掉半座山。
        if (tickCount % 2 == 0) crushBlocks(serverLevel, from, dir, Math.min(LENGTH, damageDistance + 1.5D));
        if (tickCount % 4 == 0) {
            // 这条是循环层，每 4 刻叠一次；音量压到 1.0，免得几层叠起来糊成噪音。
            serverLevel.playSound(null, from.x, from.y, from.z, CorruptedWitherSounds.get("beam_loop"),
                    SoundSource.HOSTILE, 1.0F, 0.9F);
        }
    }

    /**
     * 持续笼罩计数：只保留「这一刻正被光斑照着」的那个生物。
     *
     * <p>目标离开光斑（或被换掉）计数立刻清零，所以「连续」是字面意义上的连续；
     * 计数超过 {@link #DWELL_TICKS}（4 秒）之后，每 {@link #DWELL_DAMAGE_INTERVAL} 刻
     * （1 秒）再补一发 {@link #DWELL_DAMAGE} 点的真实伤害。</p>
     */
    private void tickDwell(ServerLevel level, CorruptedWither owner, LivingEntity victim) {
        if (victim == null || !victim.isAlive()) {
            dwellTicks.clear();
            return;
        }
        UUID id = victim.getUUID();
        dwellTicks.keySet().removeIf(other -> !other.equals(id));
        int ticks = dwellTicks.merge(id, 1, Integer::sum);
        if (ticks < DWELL_TICKS || ticks % DWELL_DAMAGE_INTERVAL != 0) return;
        // 真实伤害：专用伤害类型绕开护甲 / 抗性 / 药效 / 附魔 / 盾牌 / 受击冷却。
        victim.invulnerableTime = 0;
        victim.hurt(trueDamage(level, owner), DWELL_DAMAGE);
        victim.invulnerableTime = 0;
        CorruptedWitherEvents.sealHealing(victim, DWELL_DAMAGE_INTERVAL);
    }

    /** 持续笼罩真伤的伤害源；数据包缺失时退回原版魔法伤害，宁可少一层穿透也不能哑火。 */
    public static DamageSource trueDamage(ServerLevel level, LivingEntity owner) {
        if (level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).containsKey(TRUE_DAMAGE)) {
            return level.damageSources().source(TRUE_DAMAGE, owner);
        }
        return level.damageSources().magic();
    }

    /** 是不是射线持续笼罩的额外真伤（伤害链里要整段放行，不吃斗气 / 抗性等乘区）。 */
    public static boolean isTrueDamage(DamageSource source) {
        return source != null && source.is(TRUE_DAMAGE);
    }

    /** 沿光轴粉碎方块（基岩硬度 -1，天然挡得住）。 */
    private void crushBlocks(ServerLevel serverLevel, Vec3 from, Vec3 dir, double length) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int budget = 60;
        for (double d = 1.0D; d <= length && budget > 0; d += 1.2D) {
            Vec3 point = from.add(dir.scale(d));
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        pos.set((int)Math.floor(point.x) + ox, (int)Math.floor(point.y) + oy,
                                (int)Math.floor(point.z) + oz);
                        BlockState state = serverLevel.getBlockState(pos);
                        if (state.isAir()) continue;
                        if (!state.getFluidState().isEmpty()) continue;
                        if (state.getDestroySpeed(serverLevel, pos) < 0.0F) continue;
                        serverLevel.destroyBlock(pos.immutable(), false);
                        budget--;
                    }
                }
            }
        }
    }
}
