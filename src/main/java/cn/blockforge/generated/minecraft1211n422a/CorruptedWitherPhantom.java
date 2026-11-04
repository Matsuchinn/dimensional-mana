package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * 凋零幻影（Corrupted Wither Phantom）：魔蚀凋零二阶段挨打时原地分出来的一模一样的影子。
 *
 * <p>它没有 AI、不移动、不掉落，唯一的作用是当诱饵：玩家砍到它，它当场炸开，造成
 * 20 点真实伤害并附加【凋零Ⅲ】+【魔蚀Ⅲ】各 10 秒。爆炸后还会留 10 刻的「正在爆」状态，
 * 让客户端把冲击波圆环画完再消失（{@link CorruptedWitherPhantomRenderer}）。</p>
 *
 * <p><b>朝向</b>：它没有 AI，{@link #aiStep()} 以前是空实现，连带把「身体 / 头跟着 yaw 走」
 * 的那一步也省掉了——所以分身在游戏里永远朝着出生时的固定方向。现在这里手动补上跟随，
 * 并且每 5 刻把身体和三个头一起转向最近的攻击者（优先打它的那个玩家）。</p>
 */
public final class CorruptedWitherPhantom extends WitherBoss {
    /** 幻影血量：砍一下就碎。 */
    public static final float MAX_HEALTH = 20.0F;
    /** 爆炸半径与真实伤害。 */
    public static final double BLAST_RADIUS = 4.0D;
    public static final float BLAST_DAMAGE = 20.0F;
    /** 附加状态时长：10 秒。 */
    public static final int STATUS_TICKS = 200;
    /** 爆炸后残留多少刻用于画冲击波。 */
    public static final int SHOCKWAVE_TICKS = 10;
    /** 存活上限：12 秒没人碰就自己散掉。 */
    public static final int LIFETIME = 240;

    private static final EntityDataAccessor<Boolean> DATA_EXPLODING =
            SynchedEntityData.defineId(CorruptedWitherPhantom.class, EntityDataSerializers.BOOLEAN);
    /** 三个头要瞄准的世界偏航角（同步给客户端，渲染时不再查实体）。 */
    private static final EntityDataAccessor<Float> DATA_FACE_YAW =
            SynchedEntityData.defineId(CorruptedWitherPhantom.class, EntityDataSerializers.FLOAT);
    /** 三个头要瞄准的俯仰角。 */
    private static final EntityDataAccessor<Float> DATA_FACE_PITCH =
            SynchedEntityData.defineId(CorruptedWitherPhantom.class, EntityDataSerializers.FLOAT);

    /** 转头的节流：每 5 刻重算一次目标，既跟得上又不用每刻搜一遍实体。 */
    private static final int FACE_INTERVAL = 5;
    /** 每次重算最多转多少度。 */
    private static final float FACE_STEP = 34.0F;
    /** 找攻击者的最大距离。 */
    private static final double FACE_RANGE = 64.0D;

    private UUID ownerId;
    private UUID faceTargetId;
    private int faceTimer;
    private int shockwaveTicks;

    public CorruptedWitherPhantom(EntityType<? extends CorruptedWitherPhantom> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setNoGravity(true);
        this.setNoAi(true);
        this.setInvulnerableTicks(0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WitherBoss.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    /**
     * @param attacker 刚打了本体那一下的「凶手」；分身一出场就把身体和三个头转向他。
     */
    public void setup(ServerLevel level, CorruptedWither owner, Entity attacker) {
        this.ownerId = owner.getUUID();
        this.setHealth(MAX_HEALTH);
        LivingEntity target = attacker instanceof LivingEntity living ? living : resolveFaceTarget(level);
        if (target != null) {
            this.faceTargetId = target.getUUID();
            // 出生就正对攻击者（之后的微调走 tickFacing 的平滑旋转）。
            aimAt(target);
        }
    }

    public boolean isOwnedBy(Entity entity) {
        return entity != null && ownerId != null && ownerId.equals(entity.getUUID());
    }

    public boolean isExploding() {
        return this.entityData.get(DATA_EXPLODING);
    }

    /** 冲击波进度 0～1，给渲染用。 */
    public float shockwaveRatio() {
        if (shockwaveTicks <= 0) return 0.0F;
        return 1.0F - shockwaveTicks / (float)SHOCKWAVE_TICKS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EXPLODING, Boolean.FALSE);
        builder.define(DATA_FACE_YAW, 0.0F);
        builder.define(DATA_FACE_PITCH, 0.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("PhantomOwner", ownerId);
        if (faceTargetId != null) tag.putUUID("PhantomFace", faceTargetId);
        tag.putInt("PhantomShockwave", shockwaveTicks);
        tag.putBoolean("PhantomExploding", isExploding());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("PhantomOwner")) ownerId = tag.getUUID("PhantomOwner");
        if (tag.hasUUID("PhantomFace")) faceTargetId = tag.getUUID("PhantomFace");
        shockwaveTicks = tag.getInt("PhantomShockwave");
        this.entityData.set(DATA_EXPLODING, tag.getBoolean("PhantomExploding"));
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * 幻影不自己动，也不吃重力。
     *
     * <p>注意这里<b>不能</b>整个空着：父类 {@code LivingEntity.aiStep()} 里还有一步
     * 「身体朝向、头部朝向跟随 {@code yRot}」。省掉它，{@code yBodyRot} 就会永远停在出生值，
     * 分身不管怎么转 yaw 都朝着同一个方向。所以只补回这一段，别的一概不要。</p>
     */
    @Override
    public void aiStep() {
        // 故意不调 super：原版凋零的浮空 / 三头弹幕都不要。
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    @Override
    protected void customServerAiStep() {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (isExploding()) {
            // 冲击波画完再消失：10 刻刚好够白光扩到最大。
            if (--shockwaveTicks <= 0) discard();
            return;
        }
        CorruptedWither owner = owner();
        if (owner == null || !owner.isAlive() || tickCount > LIFETIME) {
            discard();
            return;
        }
        tickFacing();
    }

    // ---------------------------------------------------------------- 朝向攻击者
    /** 每 5 刻重算一次「该看谁」，然后让身体和三个头平滑转过去。 */
    private void tickFacing() {
        if (--faceTimer > 0) return;
        faceTimer = FACE_INTERVAL;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        LivingEntity target = resolveFaceTarget(serverLevel);
        if (target == null) return;
        this.faceTargetId = target.getUUID();
        float wanted = yawTo(target);
        this.setYRot(Mth.approachDegrees(this.getYRot(), wanted, FACE_STEP));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        // 头跟着身体转，只把俯仰角重新瞄一下：不然三个头会在空中突然甩过去。
        setHeadAim(this.getYRot(), pitchTo(target));
    }

    /** 出生那一下直接对准攻击者（之后由 {@link #tickFacing()} 平滑跟随）。 */
    private void aimAt(LivingEntity target) {
        float wanted = yawTo(target);
        this.setYRot(wanted);
        this.yBodyRot = wanted;
        this.yHeadRot = wanted;
        setHeadAim(wanted, pitchTo(target));
    }

    private void setHeadAim(float yaw, float pitch) {
        this.entityData.set(DATA_FACE_YAW, yaw);
        this.entityData.set(DATA_FACE_PITCH, Mth.clamp(pitch, -55.0F, 55.0F));
    }

    private float yawTo(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        return (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
    }

    private float pitchTo(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getEyeY() - (getY() + 2.6D);
        return (float)(-Math.atan2(dy, horizontal) * 180.0D / Math.PI);
    }

    /**
     * 先找正在打它的玩家，找不到就退而求其次找最近的生物。
     *
     * <p>全在服务端每 5 刻做一次，客户端渲染时只读同步过来的角度。</p>
     */
    private LivingEntity resolveFaceTarget(ServerLevel serverLevel) {
        LivingEntity current = entityById(serverLevel, faceTargetId);
        if (current != null && isFaceable(current)) return current;
        Player player = serverLevel.getNearestPlayer(getX(), getY(), getZ(), FACE_RANGE,
                entity -> entity instanceof Player candidate && isFaceable(candidate));
        if (player != null) return player;
        List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(32.0D), this::isFaceable);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private LivingEntity entityById(ServerLevel level, UUID id) {
        if (id == null) return null;
        Entity entity = level.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    /** 能被分身「瞪」的对象：活着的生物，不包括召唤它的本体和别的幻影。 */
    private boolean isFaceable(LivingEntity entity) {
        if (entity == this || !entity.isAlive()) return false;
        if (entity.isRemoved()) return false;
        if (entity instanceof CorruptedWitherPhantom phantom) {
            return ownerId != null && !ownerId.equals(phantom.ownerId);
        }
        if (entity instanceof Player player) return !player.isCreative() && !player.isSpectator();
        return !(entity instanceof CorruptedWither);
    }

    /** 本体是谁；不存在（本体已死 / 跨维度）时返回 null。事件层要用它做友伤过滤。 */
    public CorruptedWither owner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(ownerId);
        return entity instanceof CorruptedWither wither ? wither : null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isExploding()) return false;
        explode();
        return true;
    }

    /** 被砍中：真实伤害 + 双状态，然后留 10 刻画冲击波。 */
    private void explode() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        this.entityData.set(DATA_EXPLODING, Boolean.TRUE);
        this.shockwaveTicks = SHOCKWAVE_TICKS;
        CorruptedWither owner = owner();
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(blockPosition()).inflate(BLAST_RADIUS),
                entity -> entity.isAlive() && entity != owner && !(entity instanceof CorruptedWitherPhantom));
        for (LivingEntity victim : victims) {
            victim.invulnerableTime = 0;
            victim.hurt(serverLevel.damageSources().magic(), BLAST_DAMAGE);
            victim.invulnerableTime = 0;
            victim.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, STATUS_TICKS, 2, false, true, true));
            // 魔蚀走织网者那套叠加规则：每爆一次升一级，时间刷新。
            MagicErosionEffect.apply(victim, STATUS_TICKS);
        }
        serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("phase"),
                SoundSource.HOSTILE, 1.3F, 1.35F);
    }

    @Override
    public void checkDespawn() {
        // 不参与原版消失机制。
    }

    /**
     * 幻影不挂 boss 血条。
     *
     * <p>它继承自原版凋零，父类会自动往每个看到它的玩家屏幕上塞一根凋零血条；
     * 那根血条的标题取的是实体名字（{@code entity.dimensional_mana.corrupted_wither_phantom}），
     * 于是屏幕上会多出一条「英文码」。这里不让它加人，血条就永远不会出现。</p>
     */
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        // 故意不调 super：父类会把玩家塞进它的 ServerBossEvent。
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        // 同上，没有加过就不用删。
    }

    // ---------------------------------------------------------------- 三个头的朝向
    /** 头部的偏航角是**世界坐标**（父类模型会自己减掉身体朝向），直接返回瞄准角。 */
    @Override
    public float getHeadYRot(int head) {
        return this.entityData.get(DATA_FACE_YAW);
    }

    /** 俯仰角沿用原版约定：目标在上面时为负。 */
    @Override
    public float getHeadXRot(int head) {
        return this.entityData.get(DATA_FACE_PITCH);
    }

    // ---------------------------------------------------------------- 专属声线
    /** 分身也是魔蚀凋零，别让它在旁边一直用原版凋零的叫声。 */
    @Override
    protected SoundEvent getAmbientSound() {
        return CorruptedWitherSounds.get("ambient");
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return CorruptedWitherSounds.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return CorruptedWitherSounds.get("death");
    }

    @Override
    protected float getSoundVolume() {
        return 1.4F;
    }
}
