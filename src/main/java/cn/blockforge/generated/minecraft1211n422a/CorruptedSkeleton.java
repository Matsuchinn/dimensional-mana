package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 魔蚀骸骨（CorruptedSkeleton）：被高浓度魔素泡透了的一具枯骨，眼窝里烧着魂火。
 *
 * <p>继承原版 {@link Skeleton}，但整套 AI 都是自己的：</p>
 * <ul>
 *   <li>80 点生命、4000 点魔素，移速是原版骷髅的 1.35 倍（0.25 → 0.3375）；</li>
 *   <li>远程：{@link CorruptedSkeletonShootGoal} 取代原版射箭 Goal，32 格内发射
 *       {@link SoulFireArrowEntity}（4 物理 + 8 奥术 + 8 火焰）；</li>
 *   <li>走位：10 格内一边后退一边射，5 格内掉头加速 65% 拉开一段（4 秒冷却），
 *       见 {@link CorruptedSkeletonSkirmishGoal}；</li>
 *   <li>主动技能：{@link CorruptedSkeletonChargeGoal} —— 目标进到 16 格内、魔素够 400 时，
 *       站定蓄力 80 刻（4 秒），蓄满射出 {@link GiantSoulFireArrowEntity}，冷却 8 秒；</li>
 *   <li>死亡：{@link CorruptedSkeletonEvents#onLivingDeath} 触发一轮 8 根魂火箭矢的死亡弹幕，
 *       掉落魂火残渣与畸变魔晶片。</li>
 * </ul>
 *
 * <p>元素抗性写在 {@link MobCombatConfig#ownMobProfiles()}：奥术免疫（代码里也拦一道）、
 * 火 / 水 / 冰各 50%，光属性易伤 50%。</p>
 */
public final class CorruptedSkeleton extends Skeleton {
    // ---------------------------------------------------------------- 设计稿数值
    /** 魔素池上限。 */
    public static final double MANA_POOL = 4000.0;
    /** 巨型魂火箭矢的魔素消耗。 */
    public static final float SKILL_MANA_COST = 400.0F;
    /** 蓄力时长：80 刻 = 4 秒。 */
    public static final int SKILL_CHARGE_TICKS = 80;
    /** 主动技能冷却：160 刻 = 8 秒。 */
    public static final int SKILL_COOLDOWN_TICKS = 160;
    /** 主动技能的触发距离（格）。 */
    public static final double SKILL_RANGE = 16.0D;
    /** 远程射击的最大距离（格）。 */
    public static final double SHOOT_RANGE = 32.0D;
    /** 目标进到 10 格内，就一边后退一边射。 */
    public static final double BACKSTEP_RANGE = 10.0D;
    /** 目标进到 5 格内，掉头加速拉开。 */
    public static final double SKIRMISH_RANGE = 5.0D;
    /** 拉开时移动速度提高 65%。 */
    public static final double SKIRMISH_SPEED_BONUS = 0.65D;
    /** 一次拉开最多跑 40 刻（2 秒）。 */
    public static final int SKIRMISH_TICKS = 40;
    /** 拉开之后的冷却：80 刻 = 4 秒。 */
    public static final int SKIRMISH_COOLDOWN_TICKS = 80;
    /** 逃到离目标这么远就算拉开成功。 */
    public static final double SKIRMISH_ESCAPE_DISTANCE = 12.0D;
    /** 死亡弹幕的箭数。 */
    public static final int DEATH_VOLLEY_COUNT = 8;
    /** 死亡弹幕的初速：比普通射击（1.2 格/刻）还快一截，讲究一个「炸开」。 */
    public static final double DEATH_VOLLEY_SPEED = 1.4;
    /** 死亡弹幕先直飞多少刻再开始追踪击杀者（11 刻 × 1.4 格 ≈ 16 格）。 */
    public static final int DEATH_VOLLEY_TRACK_DELAY = 11;

    /** 蓄力进度（剩余刻数，同步给客户端做粒子和动作）。0 = 没在蓄力。 */
    private static final EntityDataAccessor<Integer> CHARGE_TICKS =
            SynchedEntityData.defineId(CorruptedSkeleton.class, EntityDataSerializers.INT);

    /** 魂火紫焰粒子（比晶化行尸更偏冷、更亮）。 */
    static final ParticleOptions SOUL_DUST =
            new DustParticleOptions(new Vector3f(0.62F, 0.40F, 1.0F), 1.35F);

    /** 主动技能的下一次可用时刻（gameTime，服务端）。 */
    private long skillCooldownUntil;
    /** 拉开（风筝）的下一次可用时刻（gameTime，服务端）。 */
    private long skirmishCooldownUntil;
    /** 正在拉开（加速状态已生效）。 */
    private boolean skirmishing;
    /** 拉开加速的属性修饰符 id。 */
    private static final ResourceLocation SKIRMISH_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "corrupted_skeleton_skirmish_speed");
    /** 蓄力期间锁住的目标，用来决定巨型箭往哪打。 */
    private LivingEntity chargeTarget;

    public CorruptedSkeleton(EntityType<? extends CorruptedSkeleton> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    // ---------------------------------------------------------------- 属性
    public static AttributeSupplier.Builder createAttributes() {
        return Skeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                // 原版骷髅 0.25 的 1.35 倍。
                .add(Attributes.MOVEMENT_SPEED, 0.3375)
                // 要能在 32 格外发现玩家。
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35);
    }

    @Override
    protected void registerGoals() {
        // 原版骷髅的射箭 Goal 会被父类构造器 / reassessWeaponGoal 塞进来，这里先清空再摆自己的。
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new CorruptedSkeletonSkirmishGoal(this));
        this.goalSelector.addGoal(3, new CorruptedSkeletonChargeGoal(this));
        this.goalSelector.addGoal(4, new CorruptedSkeletonShootGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // 用会看「走不到」黑名单的版本：隔着围栏盯着玩家、追又追不到时不会一直重选他。
        this.targetSelector.addGoal(2, new PathAwareTargetGoal<>(this, Player.class, true));
    }

    /**
     * 原版骷髅换武器时会自己把射箭 Goal 加回去（{@code reassessWeaponGoal}），
     * 那套打的是原版箭，所以这里彻底关掉；射击节奏全交给我们的两个 Goal。
     */
    @Override
    public void reassessWeaponGoal() {
    }

    // ---------------------------------------------------------------- 生成与存档
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        ManaData.setMobPools(this, MANA_POOL, 0);
        setBaby(false);
        // 手里握一把弓：蓄力动画（拉弓）要有个东西可拉，掉落表里也不会掉它。
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        return result;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE_TICKS, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("SoulSkillCooldownUntil", skillCooldownUntil);
        tag.putLong("SoulSkirmishCooldownUntil", skirmishCooldownUntil);
        tag.putInt("SoulChargeTicks", entityData.get(CHARGE_TICKS));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        skillCooldownUntil = tag.getLong("SoulSkillCooldownUntil");
        skirmishCooldownUntil = tag.getLong("SoulSkirmishCooldownUntil");
        // 读档时可能正处在加速状态：直接把加速摘掉，交给 Goal 重新判断。
        skirmishing = false;
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SKIRMISH_SPEED_ID);
        entityData.set(CHARGE_TICKS, Math.max(0, tag.getInt("SoulChargeTicks")));
    }

    // ---------------------------------------------------------------- 状态
    /** 蓄力剩余刻数（0 = 没在蓄力）。 */
    public int chargeTicks() {
        return entityData.get(CHARGE_TICKS);
    }

    /** 正在为巨型魂火箭矢蓄力。 */
    public boolean isCasting() {
        return chargeTicks() > 0;
    }

    public long skillCooldownUntil() {
        return skillCooldownUntil;
    }

    public void setSkillCooldownUntil(long until) {
        this.skillCooldownUntil = until;
    }

    // ---------------------------------------------------------------- 拉开（风筝）
    /** 正在加速拉开距离。 */
    public boolean isSkirmishing() {
        return skirmishing;
    }

    /** 拉开还有没有冷却好。 */
    public boolean isSkirmishReady() {
        return !skirmishing && !isCasting() && level().getGameTime() >= skirmishCooldownUntil;
    }

    /** 开始拉开：先停下正在拉的弓，再挂上 +65% 移速。 */
    public void beginSkirmish() {
        if (skirmishing) return;
        skirmishing = true;
        stopUsingItem();
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SKIRMISH_SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(
                    SKIRMISH_SPEED_ID, SKIRMISH_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        level().playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_SKELETON_AMBIENT.get(), SoundSource.HOSTILE, 1.2F, 1.45F);
    }

    /** 结束拉开：摘掉加速，开始 4 秒冷却（跑不掉被提前叫停也一样算冷却）。 */
    public void endSkirmish() {
        if (!skirmishing) return;
        skirmishing = false;
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SKIRMISH_SPEED_ID);
        skirmishCooldownUntil = level().getGameTime() + SKIRMISH_COOLDOWN_TICKS;
        getNavigation().stop();
    }

    /**
     * 挑一个「背对目标、脚下有地」的落脚点，用来后撤或拉开。
     *
     * <p>先试正后方，不行就左右各偏一点再试 —— 正后方是墙的时候它会自动往斜后方绕，
     * 加上寻路本身会绕开障碍，读起来就是「有障碍物会绕开」。一个都找不到就返回 null，
     * 那就原地打（跑不掉就回头射击）。</p>
     */
    public static Vec3 retreatPoint(CorruptedSkeleton self, LivingEntity target, double distance) {
        Vec3 away = self.position().subtract(target.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(0.0D, 0.0D, 1.0D);
        away = away.normalize();
        double[] angles = {0.0D, 35.0D, -35.0D, 70.0D, -70.0D, 110.0D, -110.0D, 150.0D, -150.0D};
        for (double degrees : angles) {
            Vec3 dir = away.yRot((float) Math.toRadians(degrees));
            Vec3 point = snapToGround(self, self.position().add(dir.scale(distance)));
            if (point != null) return point;
        }
        return null;
    }

    /**
     * 把候选点对到附近的地面上：脚下往上 1 格、往下 3 格里找第一块能站的方块，
     * 再确认它上面两格是空的。地形有起伏也能用，不会因为坡地就放弃。
     */
    private static Vec3 snapToGround(CorruptedSkeleton self, Vec3 candidate) {
        BlockPos base = BlockPos.containing(candidate.x, candidate.y, candidate.z);
        Level level = self.level();
        for (int dy = 0; dy >= -3; dy--) {
            BlockPos ground = base.offset(0, dy, 0);
            BlockState state = level.getBlockState(ground);
            if (state.blocksMotion() && state.getFluidState().isEmpty()
                    && level.isEmptyBlock(ground.above()) && level.isEmptyBlock(ground.above(2))) {
                return new Vec3(candidate.x, ground.getY() + 1.0D, candidate.z);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 视线
    /** 从眼睛到目标中心这段路有没有被方块挡住 —— 也就是「这一箭飞得过去吗」。 */
    public static boolean hasClearShot(LivingEntity self, LivingEntity target) {
        return hasClearShotFrom(self, new Vec3(self.getX(), self.getEyeY(), self.getZ()), target);
    }

    /** 从某个点上，视线到目标中心通不通。 */
    private static boolean hasClearShotFrom(LivingEntity self, Vec3 from, LivingEntity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        BlockHitResult hit = self.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * 射击路径被挡住时换角度：先试左右两侧 4~7 格，再试斜后方，找第一个
     * 「站得住、而且看得到目标」的位置。都找不到就返回 null（那就先往目标靠近）。
     *
     * <p>这就是玩家看到的「有墙挡着，它会挪到侧面的空档再射」。</p>
     */
    public static Vec3 clearShotPoint(CorruptedSkeleton self, LivingEntity target) {
        Vec3 toTarget = target.position().subtract(self.position());
        toTarget = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (toTarget.lengthSqr() < 1.0E-4) return null;
        toTarget = toTarget.normalize();
        Vec3 side = new Vec3(-toTarget.z, 0.0D, toTarget.x);
        double eyeOffset = self.getEyeY() - self.getY();
        // {横向偏移, 沿目标方向的前后偏移}
        double[][] tries = {
                {4.0D, 0.0D}, {-4.0D, 0.0D}, {7.0D, 1.0D}, {-7.0D, 1.0D},
                {3.0D, -3.0D}, {-3.0D, -3.0D}, {6.0D, 3.0D}, {-6.0D, 3.0D},
        };
        for (double[] t : tries) {
            Vec3 point = snapToGround(self, self.position().add(side.scale(t[0])).add(toTarget.scale(t[1])));
            if (point == null) continue;
            Vec3 from = new Vec3(point.x, point.y + eyeOffset, point.z);
            if (hasClearShotFrom(self, from, target)) return point;
        }
        return null;
    }

    /** 蓄力进度 0~1，客户端拿它做越蓄越亮的粒子。 */
    public float chargeProgress(float partialTick) {
        return Mth.clamp(1.0F - (chargeTicks() - partialTick) / (float) SKILL_CHARGE_TICKS, 0.0F, 1.0F);
    }

    /**
     * 「正在攻击」的标记 —— 原版骷髅抬弓的姿势就靠它。
     *
     * <p>{@code SkeletonModel.prepareMobModel} 的判断是：手里握着弓 <b>且</b> {@code isAggressive()}
     * 为真，才把手臂摆成 {@code BOW_AND_ARROW}（双手抬起端弓）。原版骷髅是靠在
     * {@code RangedBowAttackGoal} 里每刻打开这个标记的，而我们把那个 Goal 换成了自己的射击 Goal，
     * 于是它的手永远是垂着的。这里补回来：拉弓（isUsingItem）或蓄力大招期间都算在攻击。</p>
     */
    @Override
    public boolean isAggressive() {
        return super.isAggressive() || isUsingItem() || isCasting();
    }

    private void setChargeTicks(int ticks) {
        entityData.set(CHARGE_TICKS, Math.max(0, ticks));
    }

    // ---------------------------------------------------------------- 远程
    /** 普通射击：往目标身上射一根魂火箭矢。 */
    public void shootSoulFireArrow(LivingEntity target, float inaccuracy) {
        if (level().isClientSide || target == null) return;
        Vec3 from = new Vec3(getX(), getEyeY() - 0.1D, getZ());
        // 无重力 + 全程追踪，所以直接瞄目标（带一点提前量），不需要像原版骷髅那样抬枪补重力。
        Vec3 to = aimPoint(target);
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-4) return;
        SoulFireArrowEntity.shoot((ServerLevel) level(), this, from, direction,
                SoulFireArrowEntity.SPEED, target, 0);
        level().playSound(null, getX(), getY(), getZ(),
                GeneratedMod.SOUL_FIRE_ARROW_SHOOT.get(), SoundSource.HOSTILE, 1.0F, 1.1F);
        if (inaccuracy > 0.0F) {
            // 音效之外不再做随机偏航：这只怪是「精准」的那一类，inaccuracy 只留给难度加成。
            level().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 0.25F, 0.6F);
        }
    }

    /** 射击瞄准点：目标中心 + 一点提前量（按箭速估算飞行时间），32 格外的移动目标也咬得住。 */
    private Vec3 aimPoint(LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 velocity = target.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-6) return center;
        double eta = Math.min(position().distanceTo(center) / SoulFireArrowEntity.SPEED, 20.0D);
        return center.add(velocity.scale(eta * 0.7D));
    }

    /** 主动技能：开始蓄力（扣 400 魔素）。 */
    public boolean beginCast(LivingEntity target) {
        if (level().isClientSide || isCasting()) return false;
        if (!ManaData.spendMana(this, SKILL_MANA_COST)) return false;
        chargeTarget = target;
        setChargeTicks(SKILL_CHARGE_TICKS);
        getNavigation().stop();
        // 拉弓：原版要靠 isUsingItem 才会抬臂，蓄力动画直接白拿。
        startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        level().playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_SKELETON_CHARGE.get(), SoundSource.HOSTILE, 1.4F, 0.85F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY() + 1.2D, getZ(), 14, 0.35D, 0.35D, 0.35D, 0.01D);
        }
        return true;
    }

    /** 蓄力被打断（比如死亡）：不发射、不扣已经扣掉的魔素。 */
    public void cancelCast() {
        if (!isCasting()) return;
        setChargeTicks(0);
        chargeTarget = null;
        stopUsingItem();
    }

    /** 蓄满：射出巨型魂火箭矢，进入 8 秒冷却。 */
    private void finishCast() {
        setChargeTicks(0);
        stopUsingItem();
        LivingEntity target = chargeTarget;
        if (target != null && (!target.isAlive() || target.level() != level())) target = getTarget();
        chargeTarget = null;
        skillCooldownUntil = level().getGameTime() + SKILL_COOLDOWN_TICKS;
        if (target == null || !(level() instanceof ServerLevel serverLevel)) return;
        Vec3 from = new Vec3(getX(), getEyeY() - 0.05D, getZ());
        Vec3 direction = target.getBoundingBox().getCenter().subtract(from);
        GiantSoulFireArrowEntity arrow = GeneratedMod.GIANT_SOUL_FIRE_ARROW.get().create(serverLevel);
        if (arrow == null) return;
        arrow.setPos(from.x, from.y, from.z);
        arrow.launch(this, direction, SoulFireArrowEntity.SPEED, target, 0);
        serverLevel.addFreshEntity(arrow);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_SKELETON_CAST.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
        // 后坐力：往后退半步，读起来像「这一发把骨头震了一下」。
        Vec3 back = direction.normalize().scale(-0.22D);
        setDeltaMovement(getDeltaMovement().add(back.x, 0.06D, back.z));
    }

    // ---------------------------------------------------------------- 死亡弹幕
    /**
     * 死亡时在身体中心一次性放出 8 根魂火箭矢，分别朝 8 个 45° 方向飞出去 16 格，
     * 之后掉头追着击杀者打。
     */
    public void deathVolley(LivingEntity killer) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 center = getBoundingBox().getCenter();
        for (int i = 0; i < DEATH_VOLLEY_COUNT; i++) {
            double angle = Math.PI * 2.0D * i / DEATH_VOLLEY_COUNT;
            Vec3 dir = new Vec3(Math.cos(angle), 0.18D, Math.sin(angle));
            SoulFireArrowEntity.shoot(serverLevel, this, center, dir,
                    DEATH_VOLLEY_SPEED, killer, DEATH_VOLLEY_TRACK_DELAY);
        }
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z,
                40, 0.6D, 0.6D, 0.6D, 0.06D);
        serverLevel.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z,
                30, 0.5D, 0.5D, 0.5D, 0.04D);
        serverLevel.playSound(null, center.x, center.y, center.z,
                GeneratedMod.CORRUPTED_SKELETON_DEATH.get(), SoundSource.HOSTILE, 1.8F, 0.75F);
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        if (isCasting()) {
            getNavigation().stop();
            // 蓄力期间一直盯着目标（它跑了也照样蓄满，巨型箭会追上去）。
            LivingEntity target = chargeTarget != null && chargeTarget.isAlive() ? chargeTarget : getTarget();
            if (target != null) {
                chargeTarget = target;
                getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            setChargeTicks(chargeTicks() - 1);
            if (chargeTicks() <= 0) finishCast();
        }
    }

    /** 蓄力时不跑普通 AI（站定拉弓）。 */
    @Override
    protected void customServerAiStep() {
        if (isCasting()) {
            getNavigation().stop();
            return;
        }
        super.customServerAiStep();
    }

    /** 客户端：蓄力时越蓄越亮的紫白魂火从脚下一圈圈往上冒。 */
    private void clientParticles() {
        if (!isCasting()) return;
        double progress = chargeProgress(0.0F);
        int count = 1 + (int) (progress * 5.0D);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.5D + random.nextDouble() * 0.6D;
            level().addParticle(progress > 0.75D ? ParticleTypes.SOUL_FIRE_FLAME : SOUL_DUST,
                    getX() + Math.cos(angle) * radius,
                    getY(0.2D + random.nextDouble() * 1.5D),
                    getZ() + Math.sin(angle) * radius,
                    0.0D, 0.03D, 0.0D);
        }
        if (random.nextFloat() < 0.4F) {
            level().addParticle(ParticleTypes.SOUL,
                    getX() + random.triangle(0.0D, 0.35D),
                    getY(1.4D + random.nextDouble() * 0.5D),
                    getZ() + random.triangle(0.0D, 0.35D), 0.0D, 0.02D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 抗性与免疫
    /**
     * 奥术伤害完全免疫。
     *
     * <p>{@link MobCombatConfig} 里也写了 {@code arcane=1}，但那条只在「抗性系统开启」时生效；
     * 这里再拦一道，任何来源的奥术伤害都进不来。</p>
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source != null && MagicEquipment.ARCANE.equals(SpellDamage.elementIdOf(source))) return true;
        return super.isInvulnerableTo(source);
    }

    /** 魔晶化的骨头不会在粉末雪里变成沼骸。 */
    @Override
    public boolean canFreeze() {
        return false;
    }

    /** 也不会被太阳晒着：这套骨头里灌的是魔素，不是普通的亡灵。 */
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    // ---------------------------------------------------------------- 声音
    @Override
    protected SoundEvent getAmbientSound() {
        return GeneratedMod.CORRUPTED_SKELETON_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GeneratedMod.CORRUPTED_SKELETON_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GeneratedMod.CORRUPTED_SKELETON_DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.1F;
    }
}
