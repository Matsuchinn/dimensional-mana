package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * 蛛丝球（WebProjectileEntity）：魔蚀织网者远距离封锁走位用的黏丝团。
 *
 * <p>初速 16 m/s（0.8 格/刻），带轻微重力，所以飞行轨迹是抛物线。
 * 撞到生物或方块后，在落点铺一张 {@link CorruptedWebBlock}；落点不可替换
 * （基岩、箱子、已有方块）时改为落在它上方那一格，再不行就只散一团丝。</p>
 */
public final class WebProjectileEntity extends Projectile {
    /** 16 m/s = 0.8 格/刻。 */
    public static final double SPEED = 0.8D;
    /** 轻微重力：抛物线但不至于飞不远。 */
    public static final double GRAVITY = 0.022D;
    /** 最多飞 6 秒。 */
    private static final int MAX_AGE = 120;

    private static final DustParticleOptions WEB_DUST =
            new DustParticleOptions(new Vector3f(0.66F, 0.32F, 0.88F), 1.0F);

    private int age;
    private UUID weaverId;

    public WebProjectileEntity(EntityType<? extends WebProjectileEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** 发射：{@code direction} 会先归一化，{@code speed} 用格/刻。 */
    public void launch(LivingEntity caster, Vec3 direction, double speed) {
        setOwner(caster);
        if (caster instanceof CorruptedWeaverEntity weaver) weaverId = weaver.getUUID();
        setDeltaMovement(direction.normalize().scale(speed));
        updateRotation();
    }

    /** 让织网者死亡时能认出自己的弹射物。 */
    public UUID weaverId() {
        return weaverId;
    }

    /** 发射它的那只织网者：优先按记下的 UUID 找，找不到再退回原版 owner。 */
    private CorruptedWeaverEntity ownerWeaver() {
        if (level() instanceof ServerLevel serverLevel && weaverId != null
                && serverLevel.getEntity(weaverId) instanceof CorruptedWeaverEntity found) {
            return found;
        }
        return getOwner() instanceof CorruptedWeaverEntity weaver ? weaver : null;
    }

    public void setWeaverId(UUID id) {
        this.weaverId = id;
    }

    /**
     * 被斗气完美格挡弹回来：掉头，归属换成弹反的玩家。
     *
     * <p>蛛丝球原本记着「我是哪只织网者吐的」。掉头之后这条关系必须改掉：</p>
     * <ul>
     *   <li>不改的话，弹回去的丝团还会给<b>虫群意识</b>送一次「首次命中」，等于白送对面一层增益；</li>
     *   <li>改成弹反玩家的 UUID 之后，落地的网按「玩家的网」计时消失，不会变成一张永久的蛛网。</li>
     * </ul>
     */
    public void reflectBy(LivingEntity parrier) {
        setOwner(parrier);
        this.weaverId = parrier.getUUID();
        Vec3 motion = getDeltaMovement();
        Vec3 back = motion.lengthSqr() < 1.0E-4 ? getLookAngle().scale(-1.0) : motion.scale(-1.0);
        setDeltaMovement(back);
        updateRotation();
        setPos(position().add(back.normalize().scale(0.5)));
        hasImpulse = true;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(WEB_DUST, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            if (tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.ITEM_SNOWBALL, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        if (++age > MAX_AGE || !level().hasChunkAt(blockPosition())) {
            discard();
            return;
        }
        Vec3 start = position();
        Vec3 delta = getDeltaMovement();
        Vec3 end = start.add(delta);

        LivingEntity victim = firstTarget(start, end);
        if (victim != null) {
            // 斗气招架：这一发飞到玩家身上时先问招架系统 —— 完美格挡会让蛛丝球原地掉头。
            if (victim instanceof ServerPlayer player && BattleQiParry.onIncomingProjectile(player, this)) return;
            setPos(victim.getBoundingBox().getCenter());
            // 虫群意识：蛛丝球黏住目标也算「首次命中」，和近战噬咬共用同一套 30 秒冷却。
            if (ownerWeaver() instanceof CorruptedWeaverEntity weaver) HiveMind.onHiveStrike(weaver, victim);
            splat(BlockPos.containing(victim.position()));
            return;
        }
        BlockHitResult blockHit = level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            setPos(blockHit.getLocation());
            splat(blockHit.getBlockPos().relative(blockHit.getDirection()));
            return;
        }
        setPos(end);
        setDeltaMovement(delta.add(0.0D, -GRAVITY, 0.0D));
        updateRotation();
    }

    /** 落点铺网：先试落点，再试落点上方一格，最后试弹体所在格。 */
    private void splat(BlockPos pos) {
        if (level() instanceof ServerLevel serverLevel) {
            boolean placed = CorruptedWebBlock.place(serverLevel, pos, weaverId);
            if (!placed) placed = CorruptedWebBlock.place(serverLevel, pos.above(), weaverId);
            if (!placed) placed = CorruptedWebBlock.place(serverLevel, blockPosition(), weaverId);
            serverLevel.sendParticles(WEB_DUST, getX(), getY(), getZ(), 10, 0.2D, 0.2D, 0.2D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL, getX(), getY(), getZ(), 6, 0.2D, 0.2D, 0.2D, 0.03D);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    GeneratedMod.CORRUPTED_WEAVER_WEB.get(), SoundSource.HOSTILE, 1.0F, 1.2F);
        }
        discard();
    }

    /** 航线上第一个能黏住的生物（织网者自己和同类不算）。 */
    private LivingEntity firstTarget(Vec3 start, Vec3 end) {
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(0.45D),
                candidate -> candidate.isAlive() && !candidate.isSpectator()
                        && !(candidate instanceof CorruptedWeaverEntity)
                        && candidate != getOwner());
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (candidate.getBoundingBox().inflate(0.15D).clip(start, end).isEmpty()) continue;
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(start);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity living)) return;
        if (ownerWeaver() instanceof CorruptedWeaverEntity weaver) HiveMind.onHiveStrike(weaver, living);
        splat(BlockPos.containing(living.position()));
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        splat(result.getBlockPos());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WebAge", age);
        if (weaverId != null) tag.putUUID("WebWeaver", weaverId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        age = tag.getInt("WebAge");
        if (tag.hasUUID("WebWeaver")) weaverId = tag.getUUID("WebWeaver");
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isInWater() {
        return false;
    }

    @Override
    public boolean canHitEntity(Entity target) {
        return target instanceof LivingEntity living && !(living instanceof CorruptedWeaverEntity)
                && living != getOwner() && super.canHitEntity(target);
    }
}
