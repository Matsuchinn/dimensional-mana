package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 虚空蚀影（Void Shade）：被魔素泡到「空间开始漏风」的末影人。
 *
 * <p>「它的存在本身，就是对空间法则的嘲弄。」继承原版 {@link EnderMan}，
 * 所以对视激怒、随机瞬移、搬方块这些末影人的老毛病全都在；这里改的是更狠的那几样：</p>
 *
 * <ul>
 *   <li><b>底子</b>：200 生命、0.375 移速（原版 1.25 倍）、8 物理 + 36 奥术的近战，
 *       10000 魔素 / 2000 斗气，两边都是每秒 20 点恢复；</li>
 *   <li><b>不怕水</b>：重写 {@link #isSensitiveToWater()} 返回 false，雨天和泡水都不再掉血、不再逃跑；</li>
 *   <li><b>末影凝视</b>：被激怒之后还敢跟它对视的玩家 / 生物，瞬间吃黑暗 8 秒、缓慢 III 8 秒、虚弱 II 8 秒
 *       （{@link #tickGazePenalty()}）；头上扣着雕刻南瓜（与原版末影人同一套规矩）或者自己身上
 *       挂着光元素附着的，豁免这套负面状态（{@link #gazeExempt(LivingEntity)}）；</li>
 *   <li><b>虚无幻影</b>：每次挨打，在原地留一个 {@link VoidPhantomEntity}（最多 8 个，内置 20 刻冷却）；
 *       幻影死掉会自爆，并把生前打出的伤害的一半回喂给本体；</li>
 *   <li><b>魔能爆</b>：8~64 格内、魔素够 500 时站定蓄力 2 秒，然后打出一条 64 格、<b>无视方块</b>的
 *       紫色穿透光线（16 爆炸 + 24 奥术 + 击飞 8 格），能被盾牌挡下、能被斗气招架弹掉；</li>
 *   <li><b>虚空凝聚</b>：魔素够 3000 且场上至少 2 个幻影时，蓄力 3.5 秒把幻影烧成
 *       {@link MagicEyeEntity}（魔蚀末影之眼）绕着自己转 1 分钟，期间它们会自动开火；
 *       这段时间里本体挨打会取消伤害、炸掉一颗眼睛、朝攻击者喷一道 30° 扇形爆破，并把伤害的 25% 吸血吸回来。</li>
 * </ul>
 *
 * <p>数值上的分工：攻击强度、抗性（水 / 光 / 火各 50%，奥术易伤 25%，音易伤 75%）走
 * {@link MobCombatConfig#ownMobProfiles()}；魔素池与斗气池走 {@link ManaData#setMobPools}；
 * 自然生成和其它魔素变异体一样，是高魔素区块里替换原版末影人（见 {@link VoidShadeEvents}）。</p>
 */
public final class VoidShadeEntity extends EnderMan implements ManaRegenSource {
    // ---------------------------------------------------------------- 设计稿数值
    /** 魔素池上限。 */
    public static final double MANA_POOL = 10000.0D;
    /** 斗气池上限。 */
    public static final int BATTLE_QI_POOL = 2000;
    /** 每秒魔素恢复。 */
    public static final double MANA_REGEN_PER_SECOND = 20.0D;
    /** 每秒斗气恢复。 */
    public static final double QI_REGEN_PER_SECOND = 20.0D;

    /** 近战的两段伤害。 */
    public static final float MELEE_PHYSICAL = 8.0F;
    public static final float MELEE_ARCANE = 36.0F;

    /**
     * 场上最多同时存在多少个虚无幻影：4 个（设计稿的硬上限）。
     *
     * <p>只数 32 格内的活幻影是不够的——幻影会追着玩家跑出这个圈，本体转头又撕新的出来，
     * 于是看起来像「无限分身」。所以计数半径放到 64 格，攒满 4 个就彻底不再生成。</p>
     */
    public static final int MAX_PHANTOMS = 4;
    /** 数幻影时看多远：追出去的分身也要算进上限。 */
    public static final double PHANTOM_COUNT_RADIUS = 64.0D;
    /** 幻影生成冷却：20 刻（设计稿要求必须有 Tick 冷却）。 */
    public static final int PHANTOM_COOLDOWN_TICKS = 20;

    /** 魔能爆的魔素消耗。 */
    public static final float BLAST_COST = 500.0F;
    /** 魔能爆的触发距离下限 / 上限。 */
    public static final double BLAST_MIN_RANGE = 8.0D;
    public static final double BLAST_MAX_RANGE = 64.0D;
    /** 魔能爆蓄力：40 刻 = 2 秒。 */
    public static final int BLAST_CHARGE_TICKS = 40;
    /** 魔能爆冷却。 */
    public static final int BLAST_COOLDOWN_TICKS = 160;
    /** 魔能爆的两段伤害。 */
    public static final float BLAST_EXPLOSION = 16.0F;
    public static final float BLAST_ARCANE = 24.0F;
    /** 击飞的横向 / 纵向初速度（约 8 格远）。 */
    public static final double BLAST_KNOCKBACK = 1.65D;
    public static final double BLAST_LIFT = 0.95D;

    /** 虚空凝聚的魔素消耗。 */
    public static final float CONDENSE_COST = 3000.0F;
    /** 虚空凝聚需要的存活幻影数。 */
    public static final int CONDENSE_MIN_PHANTOMS = 2;
    /** 虚空凝聚蓄力：70 刻 = 3.5 秒。 */
    public static final int CONDENSE_CHARGE_TICKS = 70;
    /** 魔蚀末影之眼的持续时间：1200 刻 = 1 分钟。 */
    public static final int EYE_DURATION_TICKS = 1200;
    /** 受击反击的内置冷却：30 刻。 */
    public static final int EYE_COUNTER_COOLDOWN_TICKS = 30;
    /** 受击反击的扇形：半角 15°（合计 30°）、长 16 格。 */
    public static final double EYE_COUNTER_RANGE = 16.0D;
    public static final double EYE_COUNTER_HALF_ANGLE_DEGREES = 15.0D;
    /** 受击反击的伤害与吸血比例。 */
    public static final float EYE_COUNTER_ARCANE = 32.0F;
    public static final double EYE_COUNTER_LIFESTEAL = 0.25D;

    /** 凝视惩罚：黑暗 / 缓慢 III / 虚弱 II，各 160 刻；南瓜头与光附着豁免（{@link #gazeExempt(LivingEntity)}）。 */
    public static final int GAZE_EFFECT_TICKS = 160;
    public static final int GAZE_SLOWNESS_AMPLIFIER = 2;
    public static final int GAZE_WEAKNESS_AMPLIFIER = 1;
    /** 凝视判定的距离与视角余弦阈值。 */
    private static final double GAZE_RANGE = 48.0D;
    private static final double GAZE_COS = 0.93D;

    /** 紫色魔法尘：蓄力时绕着它打转的那圈粒子。 */
    private static final ParticleOptions ARCANE_DUST =
            new DustParticleOptions(new Vector3f(0.62F, 0.32F, 1.0F), 1.25F);
    private static final ParticleOptions VOID_DUST =
            new DustParticleOptions(new Vector3f(0.36F, 0.12F, 0.62F), 1.6F);

    // ---------------------------------------------------------------- 同步数据
    private static final EntityDataAccessor<Boolean> DATA_CASTING = SynchedEntityData.defineId(
            VoidShadeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CAST_TICKS = SynchedEntityData.defineId(
            VoidShadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CAST_TOTAL = SynchedEntityData.defineId(
            VoidShadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CAST_KIND = SynchedEntityData.defineId(
            VoidShadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_EYES = SynchedEntityData.defineId(
            VoidShadeEntity.class, EntityDataSerializers.BOOLEAN);

    /** 正在放的技能。 */
    public enum CastKind {
        BLAST(BLAST_CHARGE_TICKS),
        CONDENSE(CONDENSE_CHARGE_TICKS);

        final int ticks;

        CastKind(int ticks) {
            this.ticks = ticks;
        }
    }

    // ---------------------------------------------------------------- 运行时状态
    private int phantomCooldown;
    private long blastReadyAt;
    private long eyeCounterReadyAt;
    private long eyesUntil;

    public VoidShadeEntity(EntityType<? extends VoidShadeEntity> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        // 原版末影人把水当成不可走的方块；虚空蚀影不怕水，这里把惩罚抹掉，它踩着水照追。
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0.0F);
        // 200 生命的精英：魔素池 10000、斗气池 2000。
        ManaData.setMobPools(this, MANA_POOL, BATTLE_QI_POOL);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return EnderMan.createAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                // 原版末影人 0.3 的 1.25 倍。
                .add(Attributes.MOVEMENT_SPEED, 0.375D)
                .add(Attributes.ATTACK_DAMAGE, MELEE_PHYSICAL)
                // 魔能爆最远 64 格，索敌距离给足。
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
    }

    @Override
    protected void registerGoals() {
        // 原版末影人那套目标 / 走位照旧，另外把两个主动技能插在最前面。
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new VoidShadeCondenseGoal(this));
        this.goalSelector.addGoal(3, new VoidShadeBlastGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ---------------------------------------------------------------- 存档
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ShadePhantomCooldown", phantomCooldown);
        tag.putLong("ShadeBlastReady", blastReadyAt);
        tag.putLong("ShadeEyeCounterReady", eyeCounterReadyAt);
        tag.putLong("ShadeEyesUntil", eyesUntil);
        // 设计稿里点名的状态标签：眼睛挂身期间为 true，存档 / 指令都能直接看到。
        tag.putBoolean("hasEyes", hasEyes());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        phantomCooldown = tag.getInt("ShadePhantomCooldown");
        blastReadyAt = tag.getLong("ShadeBlastReady");
        eyeCounterReadyAt = tag.getLong("ShadeEyeCounterReady");
        eyesUntil = tag.getLong("ShadeEyesUntil");
        entityData.set(DATA_EYES, tag.getBoolean("hasEyes") && eyesUntil > level().getGameTime());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CASTING, Boolean.FALSE);
        builder.define(DATA_CAST_TICKS, 0);
        builder.define(DATA_CAST_TOTAL, 0);
        builder.define(DATA_CAST_KIND, 0);
        builder.define(DATA_EYES, Boolean.FALSE);
    }

    // ---------------------------------------------------------------- 基础表现
    /** 不怕水：重写之后雨天、泡水都不掉血，也不会为了躲雨瞬移。 */
    @Override
    public boolean isSensitiveToWater() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return GeneratedMod.VOID_SHADE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return GeneratedMod.VOID_SHADE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GeneratedMod.VOID_SHADE_DEATH.get();
    }

    // ---------------------------------------------------------------- 近战
    /** 近战：8 物理 + 36 奥术。 */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        boolean hit = living.hurt(level().damageSources().mobAttack(this), MELEE_PHYSICAL);
        if (level() instanceof ServerLevel serverLevel) {
            SpellDamage.hitElement(serverLevel, this, living, MagicEquipment.ARCANE, MELEE_ARCANE);
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    living.getX(), living.getY() + living.getBbHeight() * 0.5D, living.getZ(),
                    12, 0.35D, 0.45D, 0.35D, 0.35D);
        }
        return hit;
    }

    // ---------------------------------------------------------------- 技能状态机

    public boolean isCasting() {
        return entityData.get(DATA_CASTING);
    }

    public CastKind castKind() {
        return CastKind.values()[Math.max(0, Math.min(CastKind.values().length - 1,
                entityData.get(DATA_CAST_KIND)))];
    }

    public int castTicks() {
        return entityData.get(DATA_CAST_TICKS);
    }

    public float castProgress(float partialTick) {
        int total = entityData.get(DATA_CAST_TOTAL);
        if (total <= 0) return 0.0F;
        float done = total - entityData.get(DATA_CAST_TICKS) + partialTick;
        return Math.max(0.0F, Math.min(1.0F, done / (float) total));
    }

    public boolean hasEyes() {
        return entityData.get(DATA_EYES);
    }

    public long blastReadyAt() {
        return blastReadyAt;
    }

    public long eyeCounterReadyAt() {
        return eyeCounterReadyAt;
    }

    public boolean blastReady() {
        return level().getGameTime() >= blastReadyAt;
    }

    public void startBlastCooldown() {
        blastReadyAt = level().getGameTime() + BLAST_COOLDOWN_TICKS;
    }

    /** 开始一次蓄力；kind 决定蓄满之后放哪一招。 */
    public void beginCast(CastKind kind) {
        if (isCasting()) return;
        entityData.set(DATA_CAST_KIND, kind.ordinal());
        entityData.set(DATA_CAST_TOTAL, kind.ticks);
        entityData.set(DATA_CAST_TICKS, kind.ticks);
        entityData.set(DATA_CASTING, true);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    kind == CastKind.CONDENSE
                            ? GeneratedMod.VOID_SHADE_CHARGE.get()
                            : SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, kind == CastKind.CONDENSE ? 1.8F : 1.2F,
                    kind == CastKind.CONDENSE ? 0.62F : 0.78F);
        }
    }

    /** 蓄力被打断（目标没了、Goal 收手、被瞬移打断……）。 */
    public void cancelCast() {
        if (!isCasting()) return;
        entityData.set(DATA_CASTING, false);
        entityData.set(DATA_CAST_TICKS, 0);
        entityData.set(DATA_CAST_TOTAL, 0);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(VOID_DUST, getX(), getY(1.0D), getZ(), 16, 0.4D, 0.6D, 0.4D, 0.05D);
        }
    }

    /** 蓄力期间站定不动（和魔蚀骸骨一个套路）。 */
    @Override
    protected void customServerAiStep() {
        if (isCasting()) {
            getNavigation().stop();
            LivingEntity target = getTarget();
            if (target != null && target.isAlive()) getLookControl().setLookAt(target, 30.0F, 30.0F);
            return;
        }
        super.customServerAiStep();
    }

    private void tickCasting() {
        if (!isCasting()) return;
        if (tickCount % 4 == 0) {
            ServerLevel serverLevel = (ServerLevel) level();
            float progress = castProgress(0.0F);
            double radius = 0.8D + progress * 1.2D;
            double angle = tickCount * 0.35D;
            serverLevel.sendParticles(ARCANE_DUST,
                    getX() + Math.cos(angle) * radius, getY(1.2D), getZ() + Math.sin(angle) * radius,
                    4, 0.05D, 0.15D, 0.05D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    getX(), getY(1.3D), getZ(), 3, 0.35D, 0.6D, 0.35D, 0.25D);
        }
        int left = castTicks() - 1;
        entityData.set(DATA_CAST_TICKS, left);
        if (left > 0) return;
        CastKind kind = castKind();
        entityData.set(DATA_CASTING, false);
        entityData.set(DATA_CAST_TICKS, 0);
        entityData.set(DATA_CAST_TOTAL, 0);
        if (kind == CastKind.BLAST) {
            finishBlast();
        } else {
            finishCondense();
        }
    }

    // ---------------------------------------------------------------- 魔能爆
    private void finishBlast() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        LivingEntity target = getTarget();
        Vec3 from = getEyePosition();
        Vec3 dir;
        if (target != null && target.isAlive()) {
            dir = target.getEyePosition().subtract(from).normalize();
        } else {
            dir = getViewVector(1.0F);
        }
        serverLevel.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.VOID_SHADE_CAST.get(), SoundSource.HOSTILE, 1.8F, 0.8F);
        // 视觉上的光线：沿射线撒一串紫白粒子，一路拉满 64 格。
        for (int i = 1; i <= 64; i++) {
            Vec3 point = from.add(dir.scale(i));
            serverLevel.sendParticles(i % 2 == 0 ? ParticleTypes.PORTAL : ARCANE_DUST,
                    point.x, point.y, point.z, 2, 0.06D, 0.06D, 0.06D, 0.0D);
        }
        // 射线只找实体、不看方块：这一招本来就无视方块阻挡。
        AABB box = getBoundingBox().expandTowards(dir.scale(BLAST_MAX_RANGE)).inflate(1.5D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(serverLevel, this, from,
                from.add(dir.scale(BLAST_MAX_RANGE)), box,
                entity -> entity instanceof LivingEntity living && living.isAlive()
                        && living != this && !isFriendly(entity));
        if (hit == null) return;
        if (!(hit.getEntity() instanceof LivingEntity victim)) return;
        applyBlastTo(victim, dir);
    }

    private void applyBlastTo(LivingEntity victim, Vec3 dir) {
        if (victim instanceof Player player && blockedByDefense(player)) {
            // 举盾 / 招架：整发光线被挡下，只掉盾牌耐久与一点斗气。
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.8F);
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY(1.2D), player.getZ(), 18, 0.4D, 0.6D, 0.4D, 0.28D);
            }
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            victim.hurt(serverLevel.damageSources().explosion(this, this), BLAST_EXPLOSION);
            SpellDamage.hitElement(serverLevel, this, victim, MagicEquipment.ARCANE, BLAST_ARCANE);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.4F, 1.25F);
        }
        Vec3 push = new Vec3(dir.x, 0.0D, dir.z);
        if (push.lengthSqr() < 1.0E-6D) push = new Vec3(0.0D, 0.0D, 1.0D);
        push = push.normalize().scale(BLAST_KNOCKBACK);
        victim.setDeltaMovement(victim.getDeltaMovement().add(push.x, BLAST_LIFT, push.z));
        victim.hurtMarked = true;
    }

    /** 举盾，或者正摆着斗气招架。 */
    public static boolean blockedByDefense(Player player) {
        if (player == null) return false;
        if (player.isBlocking() && player.getUseItem().is(Items.SHIELD)) {
            ItemStack shield = player.getUseItem();
            shield.hurtAndBreak(6, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            return true;
        }
        return BattleQiParry.isParrying(player);
    }

    // ---------------------------------------------------------------- 虚空凝聚
    /** 蓄力开始时把 16 格内的幻影都标记成「正在被献祭」。 */
    public void markSacrifices() {
        for (VoidPhantomEntity phantom : nearbyPhantoms(16.0D)) {
            phantom.setSacrificing(true);
        }
    }

    private void finishCondense() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        List<VoidPhantomEntity> phantoms = nearbyPhantoms(16.0D);
        int index = 0;
        for (VoidPhantomEntity phantom : phantoms) {
            if (!phantom.isSacrificing()) continue;
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    phantom.getX(), phantom.getY(0.8D), phantom.getZ(), 24, 0.3D, 0.4D, 0.3D, 0.4D);
            phantom.discard();
            MagicEyeEntity eye = GeneratedMod.MAGIC_EYE.get().create(serverLevel);
            if (eye != null) {
                eye.setup(serverLevel, this, index++);
                serverLevel.addFreshEntity(eye);
            }
        }
        if (index == 0) return;
        eyesUntil = level().getGameTime() + EYE_DURATION_TICKS;
        entityData.set(DATA_EYES, true);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.VOID_SHADE_CAST.get(), SoundSource.HOSTILE, 2.0F, 0.55F);
        serverLevel.sendParticles(ParticleTypes.FLASH, getX(), getY(1.6D), getZ(), 2, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** 眼睛里还剩下的那些（没眼睛时返回空表）。 */
    public List<MagicEyeEntity> nearbyEyes() {
        return level().getEntitiesOfClass(MagicEyeEntity.class, getBoundingBox().inflate(32.0D),
                eye -> eye.isAlive() && eye.isOwnedBy(this));
    }

    public List<VoidPhantomEntity> nearbyPhantoms(double radius) {
        return level().getEntitiesOfClass(VoidPhantomEntity.class, getBoundingBox().inflate(radius),
                phantom -> phantom.isAlive() && phantom.isOwnedBy(this));
    }

    /**
     * 反击：本体受击时取消伤害、炸掉一颗眼睛，朝攻击者方向打一道 30° 扇形爆破，
     * 伤害的 25% 吸回自己身上。
     */
    public boolean counterAttack(LivingEntity attacker) {
        if (attacker == null || !attacker.isAlive()) return false;
        long now = level().getGameTime();
        if (now < eyeCounterReadyAt) return false;
        List<MagicEyeEntity> eyes = nearbyEyes();
        if (eyes.isEmpty()) return false;
        MagicEyeEntity eye = eyes.get(0);
        Vec3 center = eye.position();
        eye.discard();
        eyeCounterReadyAt = now + EYE_COUNTER_COOLDOWN_TICKS;
        if (eyes.size() <= 1) {
            eyesUntil = now;
            entityData.set(DATA_EYES, false);
        }
        if (!(level() instanceof ServerLevel serverLevel)) return true;
        Vec3 origin = getEyePosition();
        Vec3 dir = attacker.getEyePosition().subtract(origin).normalize();
        double cosLimit = Math.cos(Math.toRadians(EYE_COUNTER_HALF_ANGLE_DEGREES));
        float healed = 0.0F;
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(EYE_COUNTER_RANGE + 1.0D),
                living -> living != this && living.isAlive() && !isFriendly(living))) {
            Vec3 toVictim = victim.getEyePosition().subtract(origin);
            if (toVictim.lengthSqr() > EYE_COUNTER_RANGE * EYE_COUNTER_RANGE) continue;
            if (toVictim.normalize().dot(dir) < cosLimit) continue;
            SpellDamage.hitElement(serverLevel, this, victim, MagicEquipment.ARCANE, EYE_COUNTER_ARCANE);
            Vec3 push = new Vec3(victim.getX() - getX(), 0.0D, victim.getZ() - getZ());
            if (push.lengthSqr() > 1.0E-6D) {
                push = push.normalize().scale(1.5D);
                victim.setDeltaMovement(victim.getDeltaMovement().add(push.x, 0.85D, push.z));
                victim.hurtMarked = true;
            }
            healed += EYE_COUNTER_ARCANE * (float) EYE_COUNTER_LIFESTEAL;
            serverLevel.sendParticles(ARCANE_DUST,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                    16, 0.4D, 0.5D, 0.4D, 0.08D);
        }
        if (healed > 0.0F) heal(healed);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.6F, 0.9F);
        return true;
    }

    // ---------------------------------------------------------------- 虚无幻影
    /** 挨打时在原地留一个分身。 */
    public void spawnPhantom() {
        if (level().isClientSide || hasEyes()) return;
        if (phantomCooldown > 0) return;
        if (nearbyPhantoms(PHANTOM_COUNT_RADIUS).size() >= MAX_PHANTOMS) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        VoidPhantomEntity phantom = GeneratedMod.VOID_PHANTOM.get().create(serverLevel);
        if (phantom == null) return;
        phantom.setup(serverLevel, this);
        phantom.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) phantom.setTarget(target);
        serverLevel.addFreshEntity(phantom);
        phantomCooldown = PHANTOM_COOLDOWN_TICKS;
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                getX(), getY(0.8D), getZ(), 20, 0.35D, 0.55D, 0.35D, 0.35D);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.9F, 0.6F);
    }

    /**
     * 魔蚀末影之眼的轮次射击：每 20 刻只有一颗眼睛开火，按顺序轮着来。
     *
     * <p>设计稿要求「轮次射击」而不是齐射 —— 六颗眼睛一起打，一瞬间就是 48 点奥术，
     * 玩家根本来不及反应；轮着打则是每 20 刻一刀，节奏清晰、也给了走位空间。</p>
     */
    private void tickEyeVolley() {
        if (tickCount % MagicEyeEntity.FIRE_INTERVAL != 0) return;
        List<MagicEyeEntity> eyes = nearbyEyes();
        if (eyes.isEmpty()) return;
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        MagicEyeEntity eye = eyes.get(Math.floorMod(tickCount / MagicEyeEntity.FIRE_INTERVAL, eyes.size()));
        if (eye.distanceToSqr(target) > MagicEyeEntity.FIRE_RANGE * MagicEyeEntity.FIRE_RANGE) return;
        eye.fire(this, target);
    }

    /** 本体死亡：场上所有眼睛、幻影一起清掉，别留残骸。 */
    public void clearSummons() {
        long now = level().getGameTime();
        eyesUntil = now;
        entityData.set(DATA_EYES, false);
        for (MagicEyeEntity eye : nearbyEyes()) eye.discard();
        for (VoidPhantomEntity phantom : nearbyPhantoms(64.0D)) phantom.discard();
    }

    private boolean isFriendly(Entity entity) {
        // 自己的眼睛和幻影永远不吃自己的技能。
        if (entity instanceof MagicEyeEntity eye) return eye.isOwnedBy(this);
        if (entity instanceof VoidPhantomEntity phantom) return phantom.isOwnedBy(this);
        return false;
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        tickCasting();
        if (phantomCooldown > 0) phantomCooldown--;
        if (tickCount % 20 == 0) {
            if (ManaData.get(this).mana() < ManaData.get(this).maxMana()) {
                ManaData.restoreMana(this, MANA_REGEN_PER_SECOND, false);
            }
            ManaData.Snapshot snapshot = ManaData.get(this);
            if (snapshot.battleQi() < snapshot.maxBattleQi()) {
                ManaData.setBattleQi(this, Math.min(snapshot.maxBattleQi(),
                        snapshot.battleQi() + (int) Math.round(QI_REGEN_PER_SECOND)));
            }
        }
        if (hasEyes() && level().getGameTime() >= eyesUntil) {
            entityData.set(DATA_EYES, false);
            for (MagicEyeEntity eye : nearbyEyes()) eye.discard();
        } else if (hasEyes()) {
            tickEyeVolley();
        }
        tickGazePenalty();
    }

    /**
     * 末影凝视：被激怒之后，还盯着它看的玩家 / 生物会被塞一套负面状态。
     *
     * <p>原版的 {@code isLookingAtMe} 在 {@code monster} 包里是包内可见的，外部继承改不了，
     * 所以这里用视线向量自己算一遍：对方的视线够正、且它自己也正对着对方，才算「对上眼」。</p>
     */
    private void tickGazePenalty() {
        if (tickCount % 10 != 0) return;
        LivingEntity current = getTarget();
        boolean enraged = current != null && current.isAlive();
        for (Entity entity : level().getEntitiesOfClass(Entity.class,
                getBoundingBox().inflate(GAZE_RANGE))) {
            if (!(entity instanceof LivingEntity viewer)) continue;
            if (entity == this || !viewer.isAlive()) continue;
            if (entity instanceof MagicEyeEntity || entity instanceof VoidPhantomEntity) continue;
            if (!staringAtMe(viewer)) continue;
            // r285：只有玩家瞪它才算宣战。以前随便一只敌对生物（变异体、骷髅……）扫过来
            // 都会被它当成挑衅，结果它整天跟别的怪打成一团，玩家反倒没人管。
            if (!enraged && viewer instanceof Player player) {
                setTarget(player);
                enraged = true;
            }
            if (!enraged) continue;
            // 豁免：雕刻南瓜头挡住「对上眼」，光元素附着扛住虚空侵蚀——都不吃这套负面状态。
            if (gazeExempt(viewer)) continue;
            viewer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, GAZE_EFFECT_TICKS, 0, false, true));
            viewer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GAZE_EFFECT_TICKS,
                    GAZE_SLOWNESS_AMPLIFIER, false, true));
            viewer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, GAZE_EFFECT_TICKS,
                    GAZE_WEAKNESS_AMPLIFIER, false, true));
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                        viewer.getX(), viewer.getY(viewer.getBbHeight() * 0.8D), viewer.getZ(),
                        8, 0.25D, 0.25D, 0.25D, 0.0D);
                serverLevel.playSound(null, viewer.getX(), viewer.getY(), viewer.getZ(),
                        SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1.0F, 0.55F);
            }
        }
    }

    /**
     * 凝视豁免：两种情况不吃凝视的负面状态——
     *
     * <ul>
     *   <li><b>雕刻南瓜头</b>：头上扣着南瓜就算没跟它对眼，跟原版末影人（以及它继承来的
     *       {@code isLookingAtMe}）用的是同一件道具、同一套规矩；</li>
     *   <li><b>光元素附着</b>：自己身上挂着 {@link ElementType#LIGHT} 附着（光球、曜光裂界、
     *       日光环境附着等都能挂上）的，虚空侵蚀压不住光，凝视同样豁免。</li>
     * </ul>
     *
     * <p>只挡 {@link #tickGazePenalty()} 这套黑暗 / 缓慢 / 虚弱；它继承原版的「对视激怒」本来
     * 就认南瓜头，光附着则不改激怒——带光的人瞪它，它照样会记住是谁在跟它对视。</p>
     */
    private static boolean gazeExempt(LivingEntity viewer) {
        if (viewer.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) return true;
        return viewer.hasEffect(GeneratedMod.elementEffect(ElementType.LIGHT));
    }

    private boolean staringAtMe(LivingEntity viewer) {
        Vec3 toMe = getEyePosition().subtract(viewer.getEyePosition());
        if (toMe.lengthSqr() > GAZE_RANGE * GAZE_RANGE || toMe.lengthSqr() < 1.0E-4D) return false;
        Vec3 look = viewer.getViewVector(1.0F);
        if (look.normalize().dot(toMe.normalize()) < GAZE_COS) return false;
        // 它自己也得正面对着对方（背对着人不可能被「对上眼」）。
        Vec3 myLook = getViewVector(1.0F).normalize();
        if (myLook.dot(toMe.normalize().scale(-1.0D)) < 0.7D) return false;
        return viewer.hasLineOfSight(this);
    }

    // ---------------------------------------------------------------- 客户端粒子
    private void clientParticles() {
        float progress = isCasting() ? castProgress(0.0F) : 0.0F;
        if (isCasting()) {
            int count = 1 + (int) (progress * 4.0F);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 0.5D + random.nextDouble() * 0.8D;
                level().addParticle(ARCANE_DUST,
                        getX() + Math.cos(angle) * radius, getY(0.4D + random.nextDouble() * 1.6D),
                        getZ() + Math.sin(angle) * radius, 0.0D, 0.02D, 0.0D);
            }
        }
        if (hasEyes() && random.nextFloat() < 0.25F) {
            level().addParticle(ParticleTypes.PORTAL,
                    getX() + random.triangle(0.0D, 0.9D), getY(1.0D + random.nextDouble() * 1.2D),
                    getZ() + random.triangle(0.0D, 0.9D), 0.0D, 0.02D, 0.0D);
        }
        // 平时也有一层很淡的虚空尘，远看是「身上在漏东西」。
        if (random.nextFloat() < 0.08F) {
            level().addParticle(ParticleTypes.ASH,
                    getX() + random.triangle(0.0D, 0.5D), getY(random.nextDouble() * 1.8D),
                    getZ() + random.triangle(0.0D, 0.5D), 0.0D, -0.01D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 恢复（眼镜读取）
    @Override
    public double manaRegenPerSecond() {
        return Math.max(0.0D, MANA_REGEN_PER_SECOND + systemManaRegenPerSecond());
    }

    @Override
    public double battleQiRegenPerSecond() {
        return Math.max(0.0D, QI_REGEN_PER_SECOND + systemBattleQiRegenPerSecond());
    }

    /**
     * 全局设置那一份恢复：本模组生物默认也被 {@link MobCombatSystem} 接管，
     * 那一份是它按难度 + 环境给的，眼镜上要和实际一起报出来才不会「少算」。
     */
    private double systemManaRegenPerSecond() {
        if (!MobCombatSystem.enabled() || !MobCombatSystem.isManagedMob(this)) return 0.0D;
        double regen = Math.max(0.0D,
                getPersistentData().getCompound(ManaData.ROOT_TAG).getDouble("mobManaRegen"));
        if (level() instanceof ServerLevel serverLevel) {
            regen *= ManaConcentration.regenerationMultiplier(
                    ManaConcentration.at(serverLevel, blockPosition()));
        }
        return regen;
    }

    private double systemBattleQiRegenPerSecond() {
        if (!MobCombatSystem.enabled() || !MobCombatSystem.isManagedMob(this)) return 0.0D;
        return Math.max(0.0D,
                getPersistentData().getCompound(ManaData.ROOT_TAG).getDouble("mobBattleQiRegen"));
    }
}
