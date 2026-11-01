package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚魔爆裂体（ManaCreeper）：被魔素灌到晶体化、核里塞着一颗「魔核」的苦力怕。
 *
 * <p>继承原版 {@link Creeper}，但整套节奏都是另写的：</p>
 * <ul>
 *   <li>80 点生命、8000 点魔素，移速是原版苦力怕的 1.5 倍（0.25 → 0.375）；</li>
 *   <li>脚步无声（{@link #playStepSound} 空实现），爆炸伤害免疫（{@link #isInvulnerableTo}）；</li>
 *   <li>目标进到 8 格且魔素 ≥ 1000 时停下蓄力 50 刻，蓄满打出「魔核爆裂」；</li>
 *   <li>魔素 &lt; 1000 时恢复成原版苦力怕的追人 + 引信，靠近后直接「紊乱爆炸」；</li>
 *   <li>魔核爆裂后自身最大生命减半，并进入 200 刻的 {@code CoreCooldown}（不能动、灰白、易伤 50%）；</li>
 *   <li>r252：魔核爆裂之后只要安静满 300 秒，就开始脱战恢复——每秒 +1 最大生命、+5 当前生命，
 *       一路长回 80/80；中途再炸一次会重新起算。</li>
 * </ul>
 *
 * <p>元素抗性写在 {@link MobCombatConfig#ownMobProfiles()}：奥术 50%，
 * 金 / 雷 / 音三种元素各易伤 35%。</p>
 */
public final class ManaCreeper extends Creeper {
    // ---------------------------------------------------------------- 设计稿数值
    /** 魔素池上限。 */
    public static final double MANA_POOL = 8000.0;
    /** 魔核爆裂的魔素消耗（低于它就得退回原版苦力怕的玩法）。 */
    public static final float CORE_BURST_COST = 1000.0F;
    /** 魔核爆裂的起手距离（格）：进到这个范围才停下来蓄力。 */
    public static final double CORE_BURST_RANGE = 8.0D;
    /** 蓄力时长：50 刻 = 2.5 秒，对应设计稿要求的「膨胀动画 + 更响的 primed 音效」。 */
    public static final int CORE_CHARGE_TICKS = 50;
    /** 魔核爆裂半径；圈内每只生物固定吃 16 点爆炸伤害。 */
    public static final float CORE_BURST_RADIUS = 4.0F;
    /** 魔核爆裂抽几种元素、每种多少伤害。 */
    public static final int CORE_ELEMENT_COUNT = 6;
    public static final float CORE_ELEMENT_DAMAGE = 8.0F;
    /** 紊乱爆炸的引信时长：和原版苦力怕一样 30 刻。 */
    public static final int CHAOS_FUSE_TICKS = 30;
    /** 魔素归零时的定身时长。 */
    public static final int CHAOS_FREEZE_TICKS = 20;
    /** 紊乱爆炸半径与固定爆炸伤害。 */
    public static final float CHAOS_RADIUS = 4.0F;
    public static final float CHAOS_DAMAGE = 32.0F;
    /** 紊乱爆炸里「十二种元素各 1 点」的单发伤害。 */
    public static final float CHAOS_ELEMENT_DAMAGE = 1.0F;
    /** 魔核冷却的持续时间：200 刻 = 10 秒。 */
    public static final int COOLDOWN_TICKS = 200;
    /** 冷却期间受到的伤害倍率（+50%）。 */
    public static final float COOLDOWN_DAMAGE_MULTIPLIER = 1.5F;

    // ---------------------------------------------------------------- 脱战缓慢恢复（r252）
    /** 距离上一次爆炸满 300 秒（6000 刻）后开始脱战恢复。 */
    public static final int OUT_OF_COMBAT_REGEN_DELAY = 6000;
    /** 恢复期每秒回多少生命值：5 点。 */
    public static final float REGEN_HEALTH_PER_SECOND = 5.0F;
    /** 恢复期每秒回多少最大生命值：1 点（慢慢把魔核爆裂砍掉的上限补回来）。 */
    public static final double REGEN_MAX_HEALTH_PER_SECOND = 1.0D;
    /** 生命与最大生命的恢复封顶，就是初始属性里那条 80。 */
    public static final double BASE_MAX_HEALTH = 80.0D;

    /** 蓄力进度（剩余刻数，同步给客户端做膨胀动画）。0 = 没在蓄力。 */
    private static final EntityDataAccessor<Integer> CHARGE_TICKS =
            SynchedEntityData.defineId(ManaCreeper.class, EntityDataSerializers.INT);
    /** 紊乱爆炸的引信剩余刻数。0 = 没点火。 */
    private static final EntityDataAccessor<Integer> FUSE_TICKS =
            SynchedEntityData.defineId(ManaCreeper.class, EntityDataSerializers.INT);
    /** 魔素归零后的定身剩余刻数。 */
    private static final EntityDataAccessor<Integer> FREEZE_TICKS =
            SynchedEntityData.defineId(ManaCreeper.class, EntityDataSerializers.INT);
    /**
     * 魔核冷却中（{@code 1}）与否（{@code 0}）的唯一状态位。
     *
     * <p>1.21.1 的原版只把状态效果的「粒子标记」同步给客户端，<b>不同步效果列表</b>，
     * 所以客户端拿 {@code hasEffect} 永远查不到冷却。这里改成和晶化行尸
     * （{@code CrystallizedZombie.CRYSTAL_STATE}）一样的做法：服务端算，
     * 写进这个同步字段，<b>两端都读它</b>，谁都不去碰客户端的 {@code hasEffect}。</p>
     */
    private static final EntityDataAccessor<Byte> COOL_STATE =
            SynchedEntityData.defineId(ManaCreeper.class, EntityDataSerializers.BYTE);

    /** 最大生命减半用的修饰符 id。 */
    private static final ResourceLocation HEALTH_HALVE_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "mana_creeper_core_halve");

    /** 紫色魔晶粒子（和晶化行尸同一系）。 */
    static final ParticleOptions CRYSTAL_DUST =
            new DustParticleOptions(new Vector3f(0.72F, 0.42F, 1.0F), 1.3F);
    /** 蓄力末段的爆白粒子。 */
    static final ParticleOptions WARNING_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.94F, 1.0F), 1.7F);

    /** 紊乱爆炸是否已经炸过（防止死亡事件和 tick 各炸一次）。 */
    private boolean chaosBlown;

    /** 服务端冷却剩余刻数；0 = 不在冷却。这个数是服务端逻辑的计时器，状态位 {@link #COOL_STATE} 由它算出来。 */
    private int cooldownTicks;

    /** 上一次爆炸（魔核爆裂 / 紊乱爆炸）发生的 gameTime；0 = 出生到现在没炸过。r252 的脱战恢复按它起算。 */
    private long lastExplosionAt;

    public ManaCreeper(EntityType<? extends ManaCreeper> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------- 属性
    public static AttributeSupplier.Builder createAttributes() {
        return Creeper.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                // 原版苦力怕 0.25 的 1.5 倍。
                .add(Attributes.MOVEMENT_SPEED, 0.375)
                .add(Attributes.FOLLOW_RANGE, 35.0)
                // 爆炸免疫之外，再给一点击退抗性，免得被不断推离蓄力位置。
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        // 不要原版苦力怕的 SwellGoal（那套 30 刻就自爆，和我们的两段技能冲突）。
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new ManaCreeperCoreBurstGoal(this));
        this.goalSelector.addGoal(3, new ManaCreeperChaosGoal(this));
        // 原版那个会追人的 MeleeAttackGoal 写在 Creeper#registerGoals() 里，我们没有调用父类实现，
        // 于是「魔素充足 + 目标在 8 格外」这段距离以前没有任何 Goal 管，它只会原地闲逛。
        // 这里补一个只负责接近的 Goal：压到 8 格内交给核爆，3 格内交给引信。
        this.goalSelector.addGoal(4, new ManaCreeperApproachGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.85D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // 会看「走不到」黑名单的版本：被墙挡住的目标不会被每 10 刻重新拉回来。
        this.targetSelector.addGoal(2, new PathAwareTargetGoal<>(this, Player.class, true));
    }

    // ---------------------------------------------------------------- 生成与存档
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        ManaData.setMobPools(this, MANA_POOL, 0);
        return result;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE_TICKS, 0);
        builder.define(FUSE_TICKS, 0);
        builder.define(FREEZE_TICKS, 0);
        builder.define(COOL_STATE, (byte) 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CoreChargeTicks", entityData.get(CHARGE_TICKS));
        tag.putInt("ChaosFuseTicks", entityData.get(FUSE_TICKS));
        tag.putInt("ManaFreezeTicks", entityData.get(FREEZE_TICKS));
        tag.putBoolean("ChaosBlown", chaosBlown);
        tag.putInt("CoreCooldownTicks", cooldownTicks);
        tag.putLong("ManaCreeperLastExplosion", lastExplosionAt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CHARGE_TICKS, tag.getInt("CoreChargeTicks"));
        entityData.set(FUSE_TICKS, tag.getInt("ChaosFuseTicks"));
        entityData.set(FREEZE_TICKS, tag.getInt("ManaFreezeTicks"));
        chaosBlown = tag.getBoolean("ChaosBlown");
        cooldownTicks = Math.max(0, tag.getInt("CoreCooldownTicks"));
        entityData.set(COOL_STATE, (byte) (cooldownTicks > 0 ? 1 : 0));
        lastExplosionAt = tag.getLong("ManaCreeperLastExplosion");
    }

    // ---------------------------------------------------------------- 状态查询（渲染与 Goal 共用）
    public int chargeTicks() { return entityData.get(CHARGE_TICKS); }
    public int fuseTicks() { return entityData.get(FUSE_TICKS); }
    public int freezeTicks() { return entityData.get(FREEZE_TICKS); }

    /** 正在蓄力打魔核爆裂。 */
    public boolean isCoreCharging() { return chargeTicks() > 0; }

    /** 正在点紊乱爆炸的引信。 */
    public boolean isChaosFusing() { return fuseTicks() > 0; }

    /** 魔素归零后的定身阶段。 */
    public boolean isManaFrozen() { return freezeTicks() > 0; }

    /**
     * 处于魔核冷却中（不能再蓄力、不能动、灰白、易伤）。
     *
     * <p>两端读同一个同步字段 {@link #COOL_STATE}：服务端在 {@link #tick()} 里按计时器
     * {@link #cooldownTicks} 与 {@code CoreCooldown} 效果算好后写入，客户端直接读，
     * 渲染器据此换灰白贴图。不再有任何一端去查 {@code hasEffect}，
     * 从根上避开「客户端查不到怪物状态效果」这个坑。</p>
     */
    public boolean isCoreCooling() {
        return entityData.get(COOL_STATE) == (byte) 1;
    }

    private void setChargeTicks(int ticks) { entityData.set(CHARGE_TICKS, Math.max(0, ticks)); }
    private void setFuseTicks(int ticks) { entityData.set(FUSE_TICKS, Math.max(0, ticks)); }
    private void setFreezeTicks(int ticks) { entityData.set(FREEZE_TICKS, Math.max(0, ticks)); }

    // ---------------------------------------------------------------- 物理与伤害拦截
    /** 移动不发出脚步声（设计稿要求）。 */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // 有意留空：这只生物走路是静音的。
    }

    /** 爆炸伤害完全免疫。 */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source != null && source.is(DamageTypeTags.IS_EXPLOSION)) return true;
        return super.isInvulnerableTo(source);
    }

    /**
     * 关掉原版苦力怕的膨胀计时：{@code Creeper#tick} 每刻把 {@code getSwellDir()} 加进
     * {@code swell}，加到 30 就直接自爆。我们的两段爆炸都自己算，所以这里恒返回 0。
     */
    @Override
    public int getSwellDir() {
        return 0;
    }

    /** 膨胀动画改由我们自己的蓄力 / 引信进度驱动（原版模型直接读这个值）。 */
    @Override
    public float getSwelling(float partialTicks) {
        if (isCoreCharging()) {
            return Math.min(1.0F, (CORE_CHARGE_TICKS - chargeTicks() + partialTicks) / (float) CORE_CHARGE_TICKS);
        }
        if (isChaosFusing()) {
            return Math.min(1.0F, (CHAOS_FUSE_TICKS - fuseTicks() + partialTicks) / (float) CHAOS_FUSE_TICKS);
        }
        return 0.0F;
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        // 冷却状态位：服务端算、两端读（灰白贴图、定身、易伤全靠它）。
        syncCooldownState();
        // 脱战恢复（r252）：距上次爆炸满 300 秒后，每秒补一格血条上限、回 5 点当前生命。
        if (isAlive() && tickCount % 20 == 0) outOfCombatRegen();
        // 定身阶段：原地 20 刻，什么 AI 都不跑。
        if (isManaFrozen()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            setFreezeTicks(freezeTicks() - 1);
            if (freezeTicks() <= 0) chaosDetonate();
            return;
        }
        // 蓄力：站住不动，倒计时到 0 就打魔核爆裂。
        if (isCoreCharging()) {
            getNavigation().stop();
            setChargeTicks(chargeTicks() - 1);
            if (chargeTicks() <= 0) coreBurst();
            return;
        }
        // 紊乱爆炸引信：站住不动，倒计时到 0 就炸。
        if (isChaosFusing()) {
            getNavigation().stop();
            setFuseTicks(fuseTicks() - 1);
            if (fuseTicks() <= 0) chaosDetonate();
            return;
        }
        // 冷却期：被钉在原地（缓慢 255 之外再补一层保险）。
        if (isCoreCooling()) {
            getNavigation().stop();
            return;
        }
        // 魔素被抽干 → 先定身 20 刻，再紊乱爆炸。
        ManaData.Snapshot snapshot = ManaData.get(this);
        if (!chaosBlown && snapshot.maxMana() > 0.0 && snapshot.mana() <= 0.0001) {
            beginChaosFreeze();
        }
    }

    /**
     * 每刻刷新冷却状态位（只在服务端跑）。
     *
     * <p>冷却时长有两个来源：</p>
     * <ul>
     *   <li>自己放完魔核爆裂 → {@link #cooldownTicks} 从 200 往下数；</li>
     *   <li>外部挂上的 {@code CoreCooldown} 效果（比如用 {@code /effect} 手动触发来验货）
     *       → 按效果剩余时长接管。</li>
     * </ul>
     * <p>结果写进同步字段 {@link #COOL_STATE}，客户端渲染器直接读它换灰白贴图。</p>
     */
    private void syncCooldownState() {
        if (cooldownTicks > 0) cooldownTicks--;
        if (cooldownTicks <= 0) {
            MobEffectInstance effect = getEffect(GeneratedMod.CORE_COOLDOWN);
            if (effect != null) cooldownTicks = Math.max(1, Math.min(COOLDOWN_TICKS, effect.getDuration()));
        }
        byte state = (byte) (cooldownTicks > 0 ? 1 : 0);
        if (entityData.get(COOL_STATE) != state) entityData.set(COOL_STATE, state);
    }

    /** 蓄力 / 引信 / 定身期间不跑原版苦力怕的 AI。 */
    @Override
    protected void customServerAiStep() {
        if (isCoreCharging() || isChaosFusing() || isManaFrozen() || isCoreCooling()) {
            getNavigation().stop();
            return;
        }
        super.customServerAiStep();
    }

    // ---------------------------------------------------------------- 主动技能：魔核爆裂
    /** 蓄力条件：目标在 8 格内、魔素够 1000、不在冷却 / 定身 / 引信阶段。 */
    public boolean canBeginCoreBurst(LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level()) return false;
        if (isCoreCooling() || isManaFrozen() || isChaosFusing() || isCoreCharging() || chaosBlown) return false;
        if (distanceToSqr(target) > CORE_BURST_RANGE * CORE_BURST_RANGE) return false;
        return ManaData.get(this).mana() >= CORE_BURST_COST;
    }

    /** 开始蓄力：站定、放蓄力音效、给客户端放膨胀动画。 */
    public void beginCoreCharge() {
        if (level().isClientSide || isCoreCooling() || isChaosFusing() || isManaFrozen()) return;
        setChargeTicks(CORE_CHARGE_TICKS);
        getNavigation().stop();
        // 「更大的 creeper.primed」：专用音效比原版更响、更低。
        level().playSound(null, getX(), getY(), getZ(),
                GeneratedMod.MANA_CREEPER_PRIMED.get(), SoundSource.HOSTILE, 1.8F, 0.55F);
        spawnDust(18, 0.9D);
    }

    /** 蓄力被打断（目标跑远、被控住）：安静地收回去，不扣魔素。 */
    public void cancelCoreCharge() {
        if (isCoreCharging()) setChargeTicks(0);
    }

    /**
     * 魔核爆裂：16 点爆炸伤害（不破坏方块）+ 随机 6 种元素各 8 点，
     * 然后最大生命减半并进入 200 刻的 CoreCooldown。
     */
    public void coreBurst() {
        if (level().isClientSide || chaosBlown) return;
        if (!(level() instanceof ServerLevel level)) return;
        setChargeTicks(0);
        // 扣魔素：扣不动（被抽干）就退回升级前的状态。
        if (!ManaData.spendMana(this, CORE_BURST_COST)) {
            setChargeTicks(0);
            return;
        }
        Vec3 center = getBoundingBox().getCenter();

        // ① 不破坏方块的爆炸：圈内每只生物固定 16 点，同类不吃自己的核爆。
        level.explode(this, level.damageSources().explosion(this, this),
                new ExplosionDamageCalculator() {
                    @Override
                    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                        return entity instanceof LivingEntity && !(entity instanceof ManaCreeper);
                    }

                    @Override
                    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
                        return 16.0F;
                    }
                },
                center.x, center.y, center.z, CORE_BURST_RADIUS, false, Level.ExplosionInteraction.NONE);

        // ② 随机 6 种元素各 8 点（附带元素附着）：
        //    十二种 = 十一种基础元素 + 奥术。
        List<String> pool = new ArrayList<>();
        for (ElementType element : ElementType.values()) pool.add(element.id());
        pool.add(MagicEquipment.ARCANE);
        RandomSource random = level.getRandom();
        List<String> picked = new ArrayList<>(CORE_ELEMENT_COUNT);
        for (int i = 0; i < CORE_ELEMENT_COUNT && !pool.isEmpty(); i++) {
            picked.add(pool.remove(random.nextInt(pool.size())));
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(CORE_BURST_RADIUS + 1.0D),
                entity -> entity != this && !(entity instanceof ManaCreeper) && entity.isAlive())) {
            for (String elementId : picked) {
                hitElement(level, victim, elementId, CORE_ELEMENT_DAMAGE);
            }
        }

        // ③ 最大生命减半（最低保留 1 点），并记下爆炸时刻——r252 的脱战恢复就从这一刻起算 300 秒。
        lastExplosionAt = level().getGameTime();
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double current = maxHealth.getValue();
            double halved = Math.max(1.0D, current / 2.0D);
            // r252：修饰符量按属性基础值（80）折算。以前拿「当前值」算差值，
            // 第二次爆裂会把上限不减反增地抬回去，和减半的设计正好相反。
            maxHealth.removeModifier(HEALTH_HALVE_ID);
            double delta = halved - maxHealth.getBaseValue();
            if (delta < 0.0D) {
                maxHealth.addPermanentModifier(new AttributeModifier(
                        HEALTH_HALVE_ID, delta, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        if (getHealth() <= 0.0F) setHealth(1.0F);

        // ④ CoreCooldown：10 秒不能动、灰白、受伤 +50%。
        applyCooldown();

        // ⑤ 收尾表现：白色冲击粒子 + 爆发音效。
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 3, 1.2, 1.2, 1.2, 0.0);
        spawnDust(48, 2.2D);
        level.playSound(null, center.x, center.y, center.z,
                GeneratedMod.MANA_CREEPER_BURST.get(), SoundSource.HOSTILE, 2.0F, 0.8F);
    }

    /** 挂上魔核冷却：计时器 + 自定义状态 + 缓慢 255 的双保险。 */
    private void applyCooldown() {
        addEffect(new MobEffectInstance(GeneratedMod.CORE_COOLDOWN, COOLDOWN_TICKS, 0, false, true, true));
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, COOLDOWN_TICKS, 255, false, true, true));
        // 立刻写状态位：爆裂的那一帧就该变灰白，不用等下一刻 tick() 补。
        cooldownTicks = COOLDOWN_TICKS;
        entityData.set(COOL_STATE, (byte) 1);
    }

    /**
     * 脱战缓慢恢复（r252）：距离上一次爆炸满 {@link #OUT_OF_COMBAT_REGEN_DELAY}（300 秒）后，
     * 每秒把最大生命补 1 点、当前生命回 5 点，都到 80 封顶。
     *
     * <p>魔核爆裂砍掉的那半条血不是永久的——只要它安静 5 分钟，晶体壳会自己重新长回去。
     * 中途再炸一次就重新起算。紊乱爆炸会把本体一并消散，所以真正走这条路的只有魔核爆裂的幸存者。</p>
     */
    private void outOfCombatRegen() {
        // 0 = 出生到现在没炸过，没什么可恢复的。
        if (lastExplosionAt <= 0L) return;
        if (level().getGameTime() - lastExplosionAt < OUT_OF_COMBAT_REGEN_DELAY) return;
        if (chaosBlown || isDeadOrDying()) return;

        // ① 最大生命每秒 +1，把减半修饰符逐秒往回抹，抹平为止（80 封顶）。
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && getMaxHealth() < BASE_MAX_HEALTH) {
            AttributeModifier halve = maxHealth.getModifier(HEALTH_HALVE_ID);
            if (halve != null) {
                double delta = Math.min(0.0D, halve.amount() + REGEN_MAX_HEALTH_PER_SECOND);
                maxHealth.removeModifier(HEALTH_HALVE_ID);
                if (delta < 0.0D) {
                    maxHealth.addPermanentModifier(new AttributeModifier(
                            HEALTH_HALVE_ID, delta, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }
        // ② 当前生命每秒 +5（heal 自己会夹在最大生命上，上限长到哪儿血就能回到哪儿，80 封顶）。
        if (getHealth() < BASE_MAX_HEALTH) heal(REGEN_HEALTH_PER_SECOND);
    }

    // ---------------------------------------------------------------- 被动：紊乱爆炸
    /** 魔素归零：原地定身 20 刻。 */
    public void beginChaosFreeze() {
        if (level().isClientSide || chaosBlown || isManaFrozen()) return;
        setChargeTicks(0);
        setFuseTicks(0);
        setFreezeTicks(CHAOS_FREEZE_TICKS);
        getNavigation().stop();
        setTarget(null);
        setPersistenceRequired();
        level().playSound(null, getX(), getY(), getZ(),
                GeneratedMod.MANA_CREEPER_HURT.get(), SoundSource.HOSTILE, 1.2F, 0.45F);
    }

    /** 原版苦力怕式的引信：靠近目标后点火 30 刻。 */
    public void beginChaosFuse() {
        if (level().isClientSide || chaosBlown || isChaosFusing() || isManaFrozen() || isCoreCooling()) return;
        setFuseTicks(CHAOS_FUSE_TICKS);
        getNavigation().stop();
        level().playSound(null, getX(), getY(), getZ(),
                GeneratedMod.MANA_CREEPER_PRIMED.get(), SoundSource.HOSTILE, 1.3F, 0.75F);
    }

    public void cancelChaosFuse() {
        if (isChaosFusing()) setFuseTicks(0);
    }

    /**
     * 紊乱爆炸：半径 4 格、会破坏方块的爆炸（32 点）+ 十二种元素各 1 点，
     * 最后掉 1~2 个畸变魔晶片与 2~4 份随机元素结晶粉末并消散。
     */
    public void chaosDetonate() {
        if (level().isClientSide || chaosBlown || !(level() instanceof ServerLevel level)) return;
        chaosBlown = true;
        setChargeTicks(0);
        setFuseTicks(0);
        setFreezeTicks(0);
        Vec3 center = getBoundingBox().getCenter();

        // 会炸方块的爆炸：同类不吃自己的紊乱爆炸。
        level.explode(this, level.damageSources().explosion(this, this),
                new ExplosionDamageCalculator() {
                    @Override
                    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                        return entity instanceof LivingEntity && !(entity instanceof ManaCreeper);
                    }

                    @Override
                    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
                        return CHAOS_DAMAGE;
                    }
                },
                center.x, center.y, center.z, CHAOS_RADIUS, false, Level.ExplosionInteraction.MOB);

        // 十二种元素各 1 点（爆炸之后再补，免得被爆炸的无敌帧吞掉）。
        List<String> all = new ArrayList<>();
        for (ElementType element : ElementType.values()) all.add(element.id());
        all.add(MagicEquipment.ARCANE);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(CHAOS_RADIUS + 1.0D),
                entity -> entity != this && !(entity instanceof ManaCreeper) && entity.isAlive())) {
            for (String elementId : all) hitElement(level, victim, elementId, CHAOS_ELEMENT_DAMAGE);
        }

        dropChaosLoot(level, center);
        spawnDust(64, 3.0D);
        level.playSound(null, center.x, center.y, center.z,
                GeneratedMod.MANA_CREEPER_BURST.get(), SoundSource.HOSTILE, 2.2F, 0.65F);
        // 死亡触发的那一次已经进入原版的死亡流程，交给它收尾；活着自爆才手动移除。
        if (!isDeadOrDying()) discard();
    }

    /** 掉落：1~2 个畸变魔晶片 + 2~4 份随机元素结晶粉末。 */
    private void dropChaosLoot(ServerLevel level, Vec3 center) {
        RandomSource random = level.getRandom();
        int shards = 1 + random.nextInt(2);
        ItemEntity shardDrops = new ItemEntity(level, center.x, center.y, center.z,
                new ItemStack(GeneratedMod.MOD_ITEMS.get(DistortedCrystalShardEntity.ITEM_ID).get(), shards));
        shardDrops.setPickUpDelay(20);
        shardDrops.setDeltaMovement(random.triangle(0.0D, 0.08D), 0.18D, random.triangle(0.0D, 0.08D));
        level.addFreshEntity(shardDrops);

        int dust = 2 + random.nextInt(3);
        ElementalSlimeVariant[] variants = ElementalSlimeVariant.values();
        for (int i = 0; i < dust; i++) {
            ElementalSlimeVariant variant = variants[random.nextInt(variants.length)];
            ItemEntity powder = new ItemEntity(level, center.x, center.y, center.z,
                    new ItemStack(GeneratedMod.MOD_ITEMS.get(variant.powderId()).get(), 1));
            powder.setPickUpDelay(20);
            powder.setDeltaMovement(random.triangle(0.0D, 0.08D), 0.2D, random.triangle(0.0D, 0.08D));
            level.addFreshEntity(powder);
        }
    }

    /**
     * 一次元素伤害的落地：先过反应系统（附带元素附着、增幅反应），再用统一入口结算，
     * 保证同一发里的六种元素不会被受击冷却吞掉。
     */
    private void hitElement(ServerLevel level, LivingEntity victim, String elementId, float amount) {
        if (MagicEquipment.ARCANE.equals(elementId)) {
            float damage = ElementReactionSystem.arcaneDamage(this, victim, amount);
            SpellDamage.hitResolvedElement(level, this, victim, MagicEquipment.ARCANE, damage);
            return;
        }
        ElementType element = elementById(elementId);
        if (element == null) return;
        float damage = ElementReactionSystem.elementalDamage(this, victim, element, amount);
        SpellDamage.hitResolvedElement(level, this, victim, element.id(), damage);
    }

    private static ElementType elementById(String id) {
        for (ElementType element : ElementType.values()) {
            if (element.id().equals(id)) return element;
        }
        return null;
    }

    /** 撒一圈魔晶尘。 */
    private void spawnDust(int count, double radius) {
        if (!(level() instanceof ServerLevel level)) return;
        RandomSource random = level.getRandom();
        Vec3 center = getBoundingBox().getCenter();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double r = random.nextDouble() * radius;
            level.sendParticles(CRYSTAL_DUST,
                    center.x + Math.cos(angle) * r,
                    center.y - 0.4D + random.nextDouble() * 1.6D,
                    center.z + Math.sin(angle) * r, 1, 0.05D, 0.05D, 0.05D, 0.01D);
        }
    }

    /** 客户端表现：蓄力时越蓄越亮的紫白粒子，引信阶段冒白烟。 */
    private void clientParticles() {
        if (isCoreCharging()) {
            double progress = 1.0D - chargeTicks() / (double) CORE_CHARGE_TICKS;
            int count = 1 + (int) (progress * 4.0D);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double r = 0.45D + random.nextDouble() * 0.5D;
                level().addParticle(progress > 0.7D ? WARNING_DUST : CRYSTAL_DUST,
                        getX() + Math.cos(angle) * r,
                        getY(0.4D + random.nextDouble() * 1.2D),
                        getZ() + Math.sin(angle) * r, 0.0D, 0.02D, 0.0D);
            }
            if (random.nextFloat() < 0.35F) {
                level().addParticle(ParticleTypes.END_ROD,
                        getX() + random.triangle(0.0D, 0.35D),
                        getY(1.0D + random.nextDouble() * 0.6D),
                        getZ() + random.triangle(0.0D, 0.35D), 0.0D, 0.03D, 0.0D);
            }
        } else if (isChaosFusing()) {
            level().addParticle(ParticleTypes.SMOKE,
                    getX() + random.triangle(0.0D, 0.35D),
                    getY(1.2D + random.nextDouble() * 0.8D),
                    getZ() + random.triangle(0.0D, 0.35D), 0.0D, 0.03D, 0.0D);
        } else if (isManaFrozen()) {
            level().addParticle(WARNING_DUST,
                    getX() + random.triangle(0.0D, 0.3D),
                    getY(0.6D + random.nextDouble() * 1.0D),
                    getZ() + random.triangle(0.0D, 0.3D), 0.0D, 0.0D, 0.0D);
        } else if (isCoreCooling()) {
            // 冷却期：不冒紫光，只往下坠灰色碎屑，和灰白贴图配套。
            if (random.nextFloat() < 0.3F) {
                level().addParticle(ParticleTypes.ASH,
                        getX() + random.triangle(0.0D, 0.4D),
                        getY(0.8D + random.nextDouble() * 0.8D),
                        getZ() + random.triangle(0.0D, 0.4D), 0.0D, -0.02D, 0.0D);
            }
        }
    }

    // ---------------------------------------------------------------- 声音：更恐怖的苦力怕
    @Override
    protected SoundEvent getAmbientSound() {
        return GeneratedMod.MANA_CREEPER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GeneratedMod.MANA_CREEPER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GeneratedMod.MANA_CREEPER_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    /** 整体压低的嗓音，比原版苦力怕闷一大截。 */
    @Override
    public float getVoicePitch() {
        return 0.55F;
    }

    /**
     * 打火石点火：原版那套「30 刻后自爆」被我们关掉了（{@link #getSwellDir()} 恒为 0），
     * 所以点着火石改成直接点燃紊乱爆炸的引信，手感才不会像「点了没用」。
     */
    @Override
    public void ignite() {
        if (level().isClientSide) return;
        beginChaosFuse();
    }

    /** 摔落不受伤（原版苦力怕的 5 格豁免沿用），但落地也不会有脚步声。 */
    @Override
    public int getMaxFallDistance() {
        return 5;
    }
}
