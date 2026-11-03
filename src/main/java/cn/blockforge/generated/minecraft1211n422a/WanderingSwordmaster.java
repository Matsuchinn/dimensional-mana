package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.InstancedAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 流浪剑士（WanderingSwordmaster）：村庄里的中立剑术大师。
 *
 * <p>这只会用长剑、会招架、会后撤、会喝药、会放技能的高智商生物，全部战斗动作由
 * {@link SwordmasterCombatGoal} 这一条状态机驱动：<b>一个 Goal 全程握着移动权</b>，
 * 按距离与资源决定「连招 / 剑气 / 突刺 / 喝药 / 集气 / 后撤」，中间没有谁都不管的空档。</p>
 *
 * <p>动画与判定解耦：GeckoLib 的 {@link AnimationController} 只读 {@link #action()} 决定演什么，
 * 真正的伤害在 {@link #tickAction()} 的固定 tick 上结算（见各 {@code COMBO_*} 分支）。</p>
 *
 * <p>数值：150 生命、20 护甲、走速 0.3 / 追击 0.45、1000 魔素、6000 斗气（每秒回 10）。</p>
 *
 * <p>阵营（r269 起）：只对纯粹的敌对生物（亡灵、灾厄、本模组的变异怪与哥布林）主动出手；
 * 猪灵族 / 僵尸猪灵 / 元素史莱姆这类中立生物绝不主动招惹——但玩家、村民、铁傀儡被它们打，
 * 或雇主先动手时，剑士立刻反击（护主例外，见 {@link #isRetaliationTarget}）。
 * 玩家与中立生物不主动招惹，被打才会反击。雇佣后跟随雇主并保护他，见 {@link #hire(Player)}。</p>
 */
public final class WanderingSwordmaster extends net.minecraft.world.entity.PathfinderMob
        implements GeoEntity, ManaRegenSource {
    // ---------------------------------------------------------------- 设计稿数值
    public static final double MAX_HEALTH = 150.0D;
    public static final double ARMOR = 20.0D;
    /** 走路速度：设计稿「玩家 1.2 倍，约 0.3」。 */
    public static final double WALK_SPEED = 0.30D;
    /** 追击速度：设计稿「玩家 1.8 倍，约 0.45」。 */
    public static final double RUN_SPEED = 0.45D;
    public static final double MANA_POOL = 1000.0D;
    public static final int QI_POOL = 6000;
    /** 基础斗气恢复：每秒 10 点。 */
    public static final double QI_REGEN_PER_SECOND = 10.0D;
    /** 一次普通攻击的伤害。 */
    public static final float COMBO_DAMAGE = 12.0F;
    /** 第三段斜劈的双倍物理伤害。 */
    public static final float FINISHER_DAMAGE = 24.0F;
    /** 长剑的攻击距离：4 格（原版铁剑 2 倍）。 */
    public static final double ATTACK_RANGE = 4.0D;
    /** 攻击判定的视线夹角余弦：约 55°，背后的目标打不到。 */
    public static final double ATTACK_DOT = 0.55D;
    /** 白昼一文字：斗气消耗 / 前摇 / 射程 / 物理 / 光元素。 */
    public static final int IAI_COST = 1000;
    public static final int IAI_WINDUP = 30;
    public static final double IAI_RANGE = 16.0D;
    public static final float IAI_PHYSICAL = 24.0F;
    public static final float IAI_LIGHT = 32.0F;
    public static final float IAI_PERCENT_HEALTH = 0.05F;
    /** 逆牙穿霄：斗气消耗 / 前摇 / 冲刺速度（128 m/s = 6.4 格/刻）/ 最多四刻追踪冲撞。 */
    public static final int CHARGE_COST = 1500;
    public static final int CHARGE_WINDUP = 15;
    public static final double CHARGE_SPEED_PER_TICK = 6.4D;
    public static final int CHARGE_MAX_TICKS = 4;
    public static final float CHARGE_DAMAGE = 48.0F;
    /** 逆牙穿霄把目标顶飞 16 格，同时打掉一半最大斗气。 */
    public static final float CHARGE_QI_DRAIN_RATIO = 0.5F;
    /** 喝药：血量低于 40% 就找机会喝，耗时 30 刻（1.5 秒）。
     *  喝的是模组里的「回复药剂」（healing_draught）：回 50% 最大生命，顺带洗掉一个负面状态。 */
    public static final float POTION_HEALTH_RATIO = 0.40F;
    public static final int POTION_TICKS = 30;
    /** 药剂回血比例：只在拿不到药剂物品时当备用。 */
    public static final float POTION_HEAL_RATIO = 0.50F;
    /** 敌人贴到这个距离内，才值得「先后撤再喝」；安全时就地喝。 */
    public static final double POTION_THREAT_RADIUS = 8.0D;
    /** 脱战后没目标、没人打，静了 4 秒就自己掏药喝。 */
    public static final int POTION_OUT_OF_COMBAT_TICKS = 80;
    /** 脱战补血的门槛：掉到 85% 以下才喝，蹭破点皮不值得演一套喝药。 */
    public static final float POTION_RECOVERY_RATIO = 0.85F;
    /** 集气：斗气低于 1000，每秒 50 点；被打断会扣回已恢复的量。 */
    public static final int GATHER_THRESHOLD = 1000;
    public static final double GATHER_PER_TICK = 2.5D;
    public static final int GATHER_MAX_TICKS = 240;
    /**
     * 魔斗气凝聚（r263 新增）：斗气极低时把魔素烧成斗气 —— 500 魔素换 2500 斗气，
     * 用集气那套动作演 2 秒。比长时间打坐快得多，代价是吃魔素。
     */
    public static final double CONDENSE_MANA_COST = 500.0D;
    public static final int CONDENSE_QI_GAIN = 2500;
    /** 斗气低于这个数（六分之一池）才叫「极低」，才值得凝聚。 */
    public static final int CONDENSE_THRESHOLD = 1000;
    public static final int CONDENSE_TICKS = 40;
    public static final int CONDENSE_COOLDOWN = 200;
    /** 斗气高于这个数，剑士会更积极地丢主动技能（白昼一文字 / 逆牙穿霄）。 */
    public static final int HIGH_QI_AGGRESSION = 2500;
    /** 逆牙穿霄的最近起手距离：原来要 16 格开外，现在 3 格贴脸也能撞；最远距离不变。 */
    public static final double CHARGE_MIN_RANGE = 3.0D;
    /** 逆牙穿霄的命中半径：设计稿要求「攻击距离延长 1 倍」，1.8 格 → 3.6 格。 */
    public static final double CHARGE_HIT_RADIUS = 3.6D;
    /** 完美格挡回 10% 斗气；假动作格挡真的挡到一下回 50%。 */
    public static final float PERFECT_PARRY_QI_RESTORE = 0.10F;
    public static final float FEINT_PARRY_QI_RESTORE = 0.50F;
    /** 假动作举剑之后有多少刻算「假动作格挡窗口」。 */
    public static final int FEINT_GUARD_WINDOW = 15;
    /** 后撤时允许抬升的最大高度：斜着跃过背后的方块，总位移长度仍然按设计稿。 */
    public static final double MAX_BACKSTEP_JUMP = 6.0D;
    /** 格挡：远程格挡 50%、耗 2 斗气；完美格挡每 12 秒必定对同一目标再触发一次。 */
    public static final int RANGED_GUARD_COST = 2;
    public static final int PERFECT_PARry_INTERVAL = 240;
    /** 瞬身闪避：60%、3 格、5 秒冷却、5 刻无敌。 */
    public static final float BLINK_CHANCE = 0.60F;
    public static final double BLINK_DISTANCE = 3.0D;
    public static final int BLINK_COOLDOWN = 100;
    public static final int BLINK_INVULNERABLE_TICKS = 5;
    /** 后撤：短 4 格、2 秒冷却；大 16 格、连续 4 次后 4 秒冷却。 */
    public static final int SHORT_BACKSTEP_COOLDOWN = 40;
    public static final int BIG_BACKSTEP_CHAIN_LIMIT = 4;
    public static final int BIG_BACKSTEP_COOLDOWN = 80;
    /** 雇佣时长：1 游戏日 = 24000 tick。 */
    public static final int HIRE_DURATION = 24000;
    /** 雇佣价格：64 个金锭。 */
    public static final int HIRE_PRICE = 64;

    /** 一整套动作。序号会同步给客户端，用来挑 GeckoLib 动画。 */
    public enum Action {
        NONE,
        COMBO_1, COMBO_2, COMBO_3,
        IAI_SLASH, CHARGE_SKILL,
        POTION, GATHER,
        BACKSTEP_SHORT, BACKSTEP_LONG,
        BLINK_DODGE, GUARD,
        /** 魔斗气凝聚：斗气极低时烧魔素换斗气，演出沿用集气动作。 */
        CONDENSE
    }

    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(WanderingSwordmaster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_TICKS =
            SynchedEntityData.defineId(WanderingSwordmaster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HIRED =
            SynchedEntityData.defineId(WanderingSwordmaster.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GUARDING =
            SynchedEntityData.defineId(WanderingSwordmaster.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RUNNING =
            SynchedEntityData.defineId(WanderingSwordmaster.class, EntityDataSerializers.BOOLEAN);

    /** 两种粒子：斗气红与剑气金。 */
    private static final DustParticleOptions QI_DUST = new DustParticleOptions(new Vector3f(0.95F, 0.18F, 0.18F), 1.1F);
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.25F), 1.25F);

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_HIRE_END = "HireEndTime";
    private static final String TAG_HOME = "HomePos";
    private static final ResourceLocationHolder SPEED = new ResourceLocationHolder();

    private final AnimatableInstanceCache animatableCache = new InstancedAnimatableInstanceCache(this);
    /** 上半身：待机/移动的身体摆动与全部战斗动作。 */
    private final AnimationController<WanderingSwordmaster> mainController;
    /** 下半身：只动两条腿，常驻循环，出招时不会被上半身盖掉（跑动放技能腿还在跑）。 */
    private final AnimationController<WanderingSwordmaster> legsController;

    // ---------------------------------------------------------------- 服务端战斗状态
    private Action action = Action.NONE;
    private int actionElapsed;
    private int actionTicks;
    private LivingEntity actionTarget;
    private UUID ownerUUID;
    private long hireEndTime;
    private BlockPos homePos;
    /** 追出去多远还回得去（解除雇佣后回家）。 */
    private int comboCd;
    private int iaiCd;
    private int chargeCd;
    private int potionCd;
    private int gatherCd;
    private int shortBackstepCd;
    private int bigBackstepCd;
    private int bigBackstepChain;
    private int blinkCd;
    private int guardFlourishCd;
    /** 战术假动作独立冷却：防御受击仍可立刻举剑，但 AI 不会连续演同一套假动作。 */
    private int feintCd;
    private UUID lastFeintTarget;
    private int pendingBigBacksteps;
    private int pendingIai;
    private boolean pendingPotion;
    private boolean pendingGather;
    private boolean pendingCondense;
    /** 魔斗气凝聚的冷却。 */
    private int condenseCd;
    /** 假动作举剑之后的有效窗口：这段时间挡到近战就是「假动作格挡」。 */
    private int feintGuardUntil;
    private int dodgeGraceUntil;
    private int lastParriedTargetSeed;
    /** 每只生物上一次被完美格挡的时刻：12 秒间隔必能再对同一目标 100% 触发。 */
    private final Map<UUID, Long> perfectParryAt = new HashMap<>();
    private double qiRegenRemainder;
    private double manaRegenRemainder;
    private double gatherAccum;
    private double gatherRestored;
    private int gatherTicks;
    private float skillQiBefore;
    private Vec3 dashVec = Vec3.ZERO;
    private int dashTicks;
    private boolean dashKeepGravity;
    private double speedModifierValue = WALK_SPEED;
    private int ambientCooldown;
    private int targetRefreshCooldown;
    private long lastActionStart;
    /** 卡位检测用的上次位置与连续卡住次数（r256）。 */
    private Vec3 lastStuckPos = Vec3.ZERO;
    private int stuckTicks;
    /** 连续几轮脱困都还在原地（r264）：到上限就判定目标走不到，放手。 */
    private int unstuckBursts;
    /** 连续脱困轮数上限：3 轮 × 30 刻 ≈ 4.5 秒还贴着墙，就别追了。 */
    private static final int MAX_UNSTUCK_BURSTS = 3;
    /** 冲刺被挡住的连续刻数：用来判断「这段后撤/冲锋是不是根本没动」。 */
    private int dashBlockedTicks;

    public WanderingSwordmaster(EntityType<? extends WanderingSwordmaster> type, Level level) {
        super(type, level);
        this.mainController = new AnimationController<>(this, "main", 2, this::animationPredicate);
        this.legsController = new AnimationController<>(this, "legs", 4, this::legsPredicate);
        this.setPersistenceRequired();
        this.xpReward = 30;
        // 固定的 1000 魔素 / 6000 斗气池；生成与读档都靠 ManaData 的持久数据。
        ManaData.setMobPools(this, MANA_POOL, QI_POOL);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 一个战斗 Goal 全程握着移动权，避免多套招各自只管一段距离。
        this.goalSelector.addGoal(1, new SwordmasterCombatGoal(this));
        this.goalSelector.addGoal(2, new SwordmasterFollowGoal(this));
        this.goalSelector.addGoal(3, new SwordmasterPatrolGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        // 被任何生物打都会反击；同类之间不内讧。
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(WanderingSwordmaster.class));
        this.targetSelector.addGoal(2, new SwordmasterTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.ATTACK_DAMAGE, COMBO_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.STEP_HEIGHT, 0.8D);
    }

    @Override
    public void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, 0);
        builder.define(DATA_ACTION_TICKS, 0);
        builder.define(DATA_HIRED, false);
        builder.define(DATA_GUARDING, false);
        builder.define(DATA_RUNNING, false);
    }

    // ---------------------------------------------------------------- 动画
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 先加下半身、后加上半身：GeckoLib 按注册顺序覆盖同一根骨骼，
        // 连招里带腿部关键帧时会盖住跑步的腿，没有腿部关键帧时（喝药/格挡）腿继续保持跑动。
        controllers.add(legsController);
        controllers.add(mainController);
    }

    /** 下半身只关心「走还是跑」，与当前在出什么招无关。 */
    private PlayState legsPredicate(AnimationState<WanderingSwordmaster> state) {
        Actions anims = Actions.INSTANCE;
        if (state.isMoving()) {
            return state.setAndContinue(isRunning() ? anims.legsRun : anims.legsWalk);
        }
        return state.setAndContinue(anims.legsIdle);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    /** 动作 → 一次性动画；不在动作里时按移动状态挑待机 / 走 / 跑。 */
    private PlayState animationPredicate(AnimationState<WanderingSwordmaster> state) {
        Actions anims = Actions.INSTANCE;
        switch (clientAction()) {
            case COMBO_1 -> { return state.setAndContinue(anims.combo1); }
            case COMBO_2 -> { return state.setAndContinue(anims.combo2); }
            case COMBO_3 -> { return state.setAndContinue(anims.combo3); }
            case IAI_SLASH -> { return state.setAndContinue(anims.iaiSlash); }
            case CHARGE_SKILL -> { return state.setAndContinue(anims.chargeSkill); }
            case POTION -> { return state.setAndContinue(anims.potion); }
            case GATHER, CONDENSE -> { return state.setAndContinue(anims.gather); }
            case BACKSTEP_SHORT -> { return state.setAndContinue(anims.backstepShort); }
            case BACKSTEP_LONG -> { return state.setAndContinue(anims.backstepLong); }
            case BLINK_DODGE -> { return state.setAndContinue(anims.blinkDodge); }
            case GUARD -> { return state.setAndContinue(anims.guard); }
            default -> { }
        }
        if (isGuarding()) return state.setAndContinue(anims.guard);
        if (isHired() && getTarget() == null && !state.isMoving()) {
            // 雇佣待机：收剑、双手抱胸的「保镖站姿」；跟随时仍然走/跑。
            return state.setAndContinue(anims.hiredIdle);
        }
        if (state.isMoving()) {
            return state.setAndContinue(isRunning() ? anims.run : anims.walk);
        }
        return state.setAndContinue(anims.idle);
    }

    /** 动画名与 RawAnimation 的集中定义，避免字符串散落在判定代码里。 */
    private static final class Actions {
        static final Actions INSTANCE = new Actions();
        final RawAnimation legsIdle = RawAnimation.begin().thenLoop("legs_idle");
        final RawAnimation legsWalk = RawAnimation.begin().thenLoop("legs_walk");
        final RawAnimation legsRun = RawAnimation.begin().thenLoop("legs_run");
        final RawAnimation idle = RawAnimation.begin().thenLoop("idle");
        final RawAnimation walk = RawAnimation.begin().thenLoop("walk");
        final RawAnimation run = RawAnimation.begin().thenLoop("run");
        final RawAnimation hiredIdle = RawAnimation.begin().thenLoop("hired_idle");
        final RawAnimation combo1 = RawAnimation.begin().thenPlayAndHold("combo_1");
        final RawAnimation combo2 = RawAnimation.begin().thenPlayAndHold("combo_2");
        final RawAnimation combo3 = RawAnimation.begin().thenPlayAndHold("combo_3");
        final RawAnimation iaiSlash = RawAnimation.begin().thenPlayAndHold("iai_slash");
        final RawAnimation chargeSkill = RawAnimation.begin().thenPlayAndHold("charge_skill");
        final RawAnimation potion = RawAnimation.begin().thenPlayAndHold("potion");
        final RawAnimation gather = RawAnimation.begin().thenLoop("gather");
        final RawAnimation backstepShort = RawAnimation.begin().thenPlayAndHold("backstep_short");
        final RawAnimation backstepLong = RawAnimation.begin().thenPlayAndHold("backstep_long");
        final RawAnimation blinkDodge = RawAnimation.begin().thenPlayAndHold("blink_dodge");
        final RawAnimation guard = RawAnimation.begin().thenLoop("guard");
    }

    // ---------------------------------------------------------------- 同步读数
    public Action clientAction() {
        int id = entityData.get(DATA_ACTION);
        Action[] values = Action.values();
        return id >= 0 && id < values.length ? values[id] : Action.NONE;
    }

    public Action action() { return action; }

    public int actionTicksRemaining() { return entityData.get(DATA_ACTION_TICKS); }

    public boolean isHired() { return entityData.get(DATA_HIRED); }

    public boolean isGuarding() { return entityData.get(DATA_GUARDING); }

    public boolean isRunning() { return entityData.get(DATA_RUNNING); }

    public boolean isBusy() { return action != Action.NONE; }

    /**
     * 客户端刀光有效帧：只记录真正高速挥剑或冲锋的片段，绝不把待机呼吸当成斩击。
     * 这里使用同步的“剩余刻数”，多人游戏里每个客户端看到的开关都一致。
     */
    public boolean shouldRenderSwordTrail() {
        int remaining = actionTicksRemaining();
        return switch (clientAction()) {
            case COMBO_1, COMBO_2 -> remaining >= 5 && remaining <= 9;
            case COMBO_3 -> remaining >= 7 && remaining <= 13;
            case IAI_SLASH -> remaining >= 6 && remaining <= 12;
            case CHARGE_SKILL -> remaining > 0 && remaining <= 30;
            default -> false;
        };
    }

    public BlockPos homePos() { return homePos; }

    public UUID ownerUUID() { return ownerUUID; }

    public void setHome(BlockPos pos) {
        if (pos == null) return;
        this.homePos = pos.immutable();
        this.setPersistenceRequired();
    }

    public int blinkCooldown() { return blinkCd; }

    public int iaiCooldown() { return iaiCd; }

    public int chargeCooldown() { return chargeCd; }

    public boolean canUseIai() {
        return iaiCd <= 0 && !isBusy() && battleQi() >= IAI_COST;
    }

    /** 斗气充足（> 2500）：主动技能用得更勤、冷却也更短。 */
    public boolean highQiAggression() {
        return battleQi() > HIGH_QI_AGGRESSION;
    }

    /** 逆牙穿霄的起手距离判定：近到 3 格也能撞（原来是 16 格才能起手），最远不变。 */
    public boolean canChargeAt(double distance) {
        return distance >= CHARGE_MIN_RANGE && canUseCharge();
    }

    public boolean canUseCharge() {
        if (chargeCd > 0 || isBusy() || battleQi() < CHARGE_COST) return false;
        LivingEntity target = getTarget();
        if (target == null) return true;
        // 本体冲上去撞击的技能，正前方 3 格内就是墙的话冲不出去，别浪费 1500 斗气。
        Vec3 toward = target.position().subtract(position());
        toward = new Vec3(toward.x, 0.0D, toward.z);
        if (toward.lengthSqr() < 1.0E-4D) return true;
        return freeRunLength(toward.normalize(), 3.0D) >= 2.5D || hasLineOfSight(target);
    }

    public boolean lowHealth() {
        return getHealth() < getMaxHealth() * POTION_HEALTH_RATIO;
    }

    public boolean lowQi() {
        return battleQi() < GATHER_THRESHOLD;
    }

    public int battleQi() {
        return ManaData.get(this).battleQi();
    }

    public int maxBattleQi() {
        return ManaData.get(this).maxBattleQi();
    }

    public double mana() {
        return ManaData.get(this).mana();
    }

    /** 静默花斗气：技能与格挡的「主动支付」不该把自己打进斗气溃散。 */
    public boolean spendQi(int amount) {
        if (amount <= 0) return true;
        if (battleQi() < amount) return false;
        ManaData.spendBattleQiQuiet(this, amount);
        return true;
    }

    public void addQi(int amount) {
        if (amount == 0) return;
        int current = battleQi();
        int next = Math.max(0, Math.min(maxBattleQi(), current + amount));
        if (next == current) return;
        if (next > current) {
            // 从低往高写回不会触发溃散，直接用主动恢复的数值接口。
            ManaData.setBattleQi(this, next);
        } else {
            ManaData.spendBattleQiQuiet(this, current - next);
        }
    }

    // ---------------------------------------------------------------- 雇佣
    /** 手持 64 个金锭右键：扣金锭、进入雇佣状态 1 游戏日。 */
    public boolean hire(Player player) {
        if (level().isClientSide || player == null) return false;
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.GOLD_INGOT) || held.getCount() < HIRE_PRICE) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.swordmaster.need_gold", HIRE_PRICE), true);
            return false;
        }
        if (isHired()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.swordmaster.already_hired"), true);
            return false;
        }
        held.shrink(HIRE_PRICE);
        this.ownerUUID = player.getUUID();
        this.hireEndTime = level().getGameTime() + HIRE_DURATION;
        entityData.set(DATA_HIRED, true);
        // 头顶「保镖: 玩家名」，跟着人走。
        setCustomName(Component.translatable("entity.dimensional_mana.wandering_swordmaster.hired",
                player.getName()).withStyle(ChatFormatting.GOLD));
        setCustomNameVisible(true);
        setPersistenceRequired();
        getNavigation().stop();
        setTarget(null);
        if (level() instanceof ServerLevel serverLevel) {
            // r256 起剑士不再有人声台词；成交用一个清脆的非人声提示音代替。
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9F, 1.35F);
            serverLevel.sendParticles(GOLD_DUST, getX(), getY() + 1.0D, getZ(), 24, 0.6D, 0.8D, 0.6D, 0.02D);
        }
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.swordmaster.hired", getName()), false);
        return true;
    }

    /** 解除雇佣：清名字、清归属，回村庄巡逻（{@link SwordmasterPatrolGoal} 会把他领回家）。 */
    public void dismiss() {
        if (!entityData.get(DATA_HIRED) && ownerUUID == null) return;
        this.ownerUUID = null;
        this.hireEndTime = 0L;
        entityData.set(DATA_HIRED, false);
        setCustomName(null);
        setCustomNameVisible(false);
        setTarget(null);
        getNavigation().stop();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0D, getZ(), 12, 0.4D, 0.5D, 0.4D, 0.02D);
            // 被带得太远就直接送回村庄，别让他在野外花半小时走回去（死亡时不传送）。
            if (isAlive() && homePos != null && blockPosition().distSqr(homePos) > 128.0D * 128.0D) {
                teleportTo(homePos.getX() + 0.5D, homePos.getY(), homePos.getZ() + 0.5D);
                serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5D, getZ(),
                        10, 0.3D, 0.4D, 0.3D, 0.05D);
            }
        }
    }

    public Player owner() {
        if (ownerUUID == null || !(level() instanceof ServerLevel level)) return null;
        Player player = level.getPlayerByUUID(ownerUUID);
        return player != null && player.isAlive() ? player : null;
    }

    /** 雇佣期满、雇主死亡或掉线（离线不清，离线期间计时照走）。 */
    private void tickHire() {
        if (!entityData.get(DATA_HIRED)) return;
        if (ownerUUID == null) { dismiss(); return; }
        if (level().getGameTime() >= hireEndTime) { dismiss(); return; }
        Player owner = owner();
        if (owner == null) {
            // 雇主不在这个世界（换维度/下线）时先不解雇，等回来；但死亡状态一定要解。
            Player found = level().getPlayerByUUID(ownerUUID);
            if (found != null && !found.isAlive()) dismiss();
        }
    }

    /** 这次伤害是不是雇主发出来的（直接伤害、间接伤害、AOE、爆炸、弓箭都算）。 */
    public boolean isOwnerSource(DamageSource source) {
        if (ownerUUID == null || source == null) return false;
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        if (ownerMatches(direct) || ownerMatches(attacker)) return true;
        // 弓/法术弹射物：真实归属在 owner 上。
        if (direct instanceof Projectile projectile && ownerMatches(projectile.getOwner())) return true;
        return false;
    }

    private boolean ownerMatches(Entity entity) {
        return entity != null && ownerUUID.equals(entity.getUUID());
    }

    // ---------------------------------------------------------------- 生成
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        ManaData.setMobPools(this, MANA_POOL, QI_POOL);
        setPersistenceRequired();
        if (homePos == null) homePos = blockPosition().immutable();
        return data;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---------------------------------------------------------------- 存档
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID(TAG_OWNER, ownerUUID);
        tag.putLong(TAG_HIRE_END, hireEndTime);
        if (homePos != null) tag.putLong(TAG_HOME, homePos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ownerUUID = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        this.hireEndTime = tag.getLong(TAG_HIRE_END);
        this.homePos = tag.contains(TAG_HOME) ? BlockPos.of(tag.getLong(TAG_HOME)) : null;
        entityData.set(DATA_HIRED, ownerUUID != null);
        if (ownerUUID != null && (!tag.contains(TAG_HIRE_END) || hireEndTime <= 0L)) {
            hireEndTime = level().getGameTime() + HIRE_DURATION;
        }
        if (ownerUUID != null && level() instanceof ServerLevel level) {
            Player owner = level.getPlayerByUUID(ownerUUID);
            if (owner != null) {
                setCustomName(Component.translatable("entity.dimensional_mana.wandering_swordmaster.hired",
                        owner.getName()).withStyle(ChatFormatting.GOLD));
                setCustomNameVisible(true);
            }
        }
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickHire();
        tickCooldowns();
        tickQiRegen();
        tickManaRegen();
        tickAction();
        tickDash();
        tickGather();
        tickStuckEscape();
        // 先清掉已经失效的目标，再判断要不要脱战补血，免得拿一个死掉的目标当敌人。
        // r269：不是"见谁打谁"口径的目标（猪灵、元素史莱姆、动手的玩家），只要反击例外
        // 还成立就继续保留；例外一消失就正常脱战。
        LivingEntity held = getTarget();
        if (held != null && (!isValidTarget(held, true)
                || (!isHostileTarget(held) && !isRetaliationTarget(held)))) setTarget(null);
        tickPotionRecovery();
        if (--targetRefreshCooldown <= 0) {
            targetRefreshCooldown = 20;
            if (getTarget() == null) {
                LivingEntity found = findCombatTarget();
                if (found != null) setTarget(found);
            }
        }
    }

    private void tickCooldowns() {
        if (comboCd > 0) comboCd--;
        if (iaiCd > 0) iaiCd--;
        if (chargeCd > 0) chargeCd--;
        if (potionCd > 0) potionCd--;
        if (gatherCd > 0) gatherCd--;
        if (condenseCd > 0) condenseCd--;
        if (shortBackstepCd > 0) shortBackstepCd--;
        if (bigBackstepCd > 0) bigBackstepCd--;
        if (blinkCd > 0) blinkCd--;
        if (feintCd > 0) feintCd--;
        if (guardFlourishCd > 0) {
            guardFlourishCd--;
            if (guardFlourishCd <= 0) entityData.set(DATA_GUARDING, false);
        }
        if (ambientCooldown > 0) ambientCooldown--;
    }

    /** 魔素小回：每秒 2 点，保证【魔斗气凝聚】不会因为一辈子不回魔素而变成一次性技能。 */
    private void tickManaRegen() {
        if (level().isClientSide) return;
        // 每秒采样一次：魔素浓度要读噪声与洞穴数据，没必要每刻都算。
        if (tickCount % 20 == 0) manaRegenRemainder += Math.max(0.0D, manaRegenPerSecond());
        if (manaRegenRemainder < 1.0D) return;
        int whole = (int)manaRegenRemainder;
        manaRegenRemainder -= whole;
        if (mana() < ManaData.get(this).maxMana()) ManaData.restoreMana(this, whole, false);
    }

    /**
     * 每秒魔素恢复：直接用全局（按难度）的 {@code regen} 配置，再乘所在位置的魔素浓度 ——
     * 和原版生物走的是同一套设置。以前剑士是「友好生物」，全局给的恢复量是 0，
     * 所以魔素一辈子不动，专业鉴定眼镜上也一直显示 0.0/s。
     */
    @Override
    public double manaRegenPerSecond() {
        double regen = Math.max(0.0D, MobCombatSystem.regenPerSecond(this));
        if (level() instanceof ServerLevel serverLevel) {
            regen *= ManaConcentration.regenerationMultiplier(
                    ManaConcentration.at(serverLevel, blockPosition()));
        }
        return Math.max(0.0D, regen);
    }

    /** 每秒斗气恢复：他自己的 10 点，再按设计稿叠加全局设置里的那部分。 */
    @Override
    public double battleQiRegenPerSecond() {
        return QI_REGEN_PER_SECOND + Math.max(0.0D, MobCombatSystem.regenPerSecond(this));
    }

    private void tickQiRegen() {
        if (isBusy() && (action == Action.GATHER || action == Action.CONDENSE)) return;
        if (tickCount % 20 == 0) qiRegenRemainder += Math.max(0.0D, battleQiRegenPerSecond());
        if (qiRegenRemainder < 1.0D) return;
        int whole = (int)qiRegenRemainder;
        qiRegenRemainder -= whole;
        if (battleQi() < maxBattleQi()) addQi(whole);
    }

    // ---------------------------------------------------------------- 动作框架
    public void startAction(Action next, int ticks, LivingEntity target) {
        if (level().isClientSide) return;
        this.action = next;
        this.actionElapsed = 0;
        this.actionTicks = Math.max(1, ticks);
        this.actionTarget = target;
        this.lastActionStart = level().getGameTime();
        setSprinting(false);
        getNavigation().stop();
        getLookControl().setLookAt(target != null ? target : this, 40, 40);
        syncAction();
    }

    public void endAction() {
        if (action == Action.NONE) return;
        this.action = Action.NONE;
        this.actionElapsed = 0;
        this.actionTicks = 0;
        this.actionTarget = null;
        entityData.set(DATA_GUARDING, false);
        syncAction();
        onActionFinished();
    }

    private void syncAction() {
        entityData.set(DATA_ACTION, action.ordinal());
        entityData.set(DATA_ACTION_TICKS, actionTicks);
    }

    /** 动作收尾后的续接：读指令反制的剑气、连续大后撤、喝药、集气都在这里排队。 */
    private void onActionFinished() {
        if (pendingIai > 0) {
            pendingIai = 0;
            if (canUseIai()) {
                startIaiSlash(actionTarget);
                return;
            }
        }
        if (pendingBigBacksteps > 0) {
            pendingBigBacksteps--;
            // 后撤退不了（冲刺中/冷却/连续上限）就继续往下走，别让喝药或集气卡死。
            if (startBigBackstep()) return;
        }
        if (pendingPotion) {
            pendingPotion = false;
            startPotionDrink();
            return;
        }
        if (pendingGather) {
            pendingGather = false;
            startGather();
            return;
        }
        if (pendingCondense) {
            pendingCondense = false;
            startCondense();
        }
    }

    /** 每刻推进动作；命中点与动画无关，全部用固定 tick 结算。 */
    private void tickAction() {
        if (action == Action.NONE) return;
        actionElapsed++;
        if (actionTicks > 0) {
            actionTicks--;
            entityData.set(DATA_ACTION_TICKS, actionTicks);
        }
        LivingEntity target = actionTarget;

        switch (action) {
            case COMBO_1 -> {
                if (actionElapsed < 7 && isParrying(target) && target instanceof Player) {
                    // 读指令：目标摆出招架 → 强行取消前摇、短后撤、反手一道剑气。
                    cancelComboIntoCounter(target);
                    return;
                }
                if (actionElapsed == 7) hitComboArc(target, 0);
                if (actionElapsed >= 14) {
                    if (canContinueCombo(target) && !blocksWithShield(target)) {
                        startAction(Action.COMBO_2, 14, target);
                    } else {
                        endAction();
                    }
                }
            }
            case COMBO_2 -> {
                if (actionElapsed == 7) hitComboArc(target, 1);
                if (actionElapsed >= 14) {
                    if (canContinueCombo(target)) {
                        startAction(Action.COMBO_3, 22, target);
                    } else {
                        endAction();
                    }
                }
            }
            case COMBO_3 -> {
                // 动画里高举到 0.55 秒才砸下来，命中点必须跟着往后挪到第 12 刻。
                if (actionElapsed == 12) hitComboArc(target, 2);
                if (actionElapsed >= 22) {
                    boolean shield = blocksWithShield(target);
                    endAction();
                    // 连招结束必定短后撤；对方举盾时更是非撤不可。
                    shortBackstep();
                    if (shield) playShieldRetreatFlourish();
                }
            }
            case IAI_SLASH -> {
                if (actionElapsed == IAI_WINDUP) releaseIaiSlash(target);
                if (actionElapsed >= IAI_WINDUP + 10) endAction();
            }
            case CHARGE_SKILL -> {
                if (actionElapsed == CHARGE_WINDUP) launchCharge(target);
                if (actionElapsed >= CHARGE_WINDUP + 30) endAction();
            }
            case POTION -> {
                if (actionElapsed >= POTION_TICKS) {
                    finishPotion();
                    endAction();
                }
            }
            case GATHER -> {
                // 集气由 tickGather 单独推进；这里只负责超时收工。
            }
            case CONDENSE -> {
                // 魔斗气凝聚：2 秒念完才结算，中途被打断魔素不扣、斗气也不给。
                if (actionElapsed >= CONDENSE_TICKS) {
                    finishCondense();
                    endAction();
                }
            }
            case BACKSTEP_SHORT, BACKSTEP_LONG, BLINK_DODGE, GUARD -> {
                // actionTicks 是递减的剩余刻数，归零才收尾（不能用 actionElapsed 比较，
                // 否则两个数从两头往中间走，动作会在中途被截断）。
                if (actionTicks <= 0) endAction();
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- 连招
    public void startCombo(LivingEntity target) {
        if (comboCd > 0 || target == null) return;
        startAction(Action.COMBO_1, 14, target);
    }

    /** 现在能不能起手三段连招（不在动作里、连招冷却已好）。 */
    public boolean canStartCombo() {
        return comboCd <= 0 && action == Action.NONE;
    }

    /** 还能不能继续追这个目标：活着且在 64 格内，跑丢了就放弃。 */
    public boolean canContinueTargeting(LivingEntity target) {
        return target != null && target.isAlive() && distanceToSqr(target) <= 64.0D * 64.0D;
    }

    /**
     * 战术假动作是否可用：至少隔 4 秒；同一个目标再额外拉长到 6 秒。
     * 防御受击调用的真格挡不走这个冷却，所以聪明不等于失去防御能力。
     */
    public boolean canFeint(LivingEntity target) {
        return target != null && feintCd <= 0 && guardFlourishCd <= 0 && action == Action.NONE;
    }

    public void playTacticalFeint(LivingEntity target) {
        if (!canFeint(target)) return;
        boolean repeatedTarget = lastFeintTarget != null && lastFeintTarget.equals(target.getUUID());
        lastFeintTarget = target.getUUID();
        feintCd = (repeatedTarget ? 120 : 80) + getRandom().nextInt(41);
        // 卖破绽：举剑一直举到窗口结束，这段时间里真的挡到近战就按「假动作格挡」结算（回 50% 斗气）。
        playGuardFlourish(FEINT_GUARD_WINDOW);
        feintGuardUntil = tickCount + FEINT_GUARD_WINDOW;
    }

    /** 假动作举剑后的格挡窗口还开着吗？ */
    public boolean feintGuardActive() {
        return tickCount <= feintGuardUntil && (action == Action.GUARD || isGuarding());
    }

    /**
     * 假动作格挡真的挡到了一下：瞬间恢复 50% 斗气，顺手把攻击者震退并定住半秒。
     * 这是「卖破绽骗对方先出手」这套 AI 的收益点，斗气越缺越值钱。
     */
    public void playFeintBlock(LivingEntity attacker) {
        feintGuardUntil = 0;
        addQi((int)Math.round(maxBattleQi() * FEINT_PARRY_QI_RESTORE));
        if (attacker != null) {
            NewSpellSystem.control(this, attacker, NewSpellContent.STUN, 10);
            Vec3 away = attacker.position().subtract(position());
            if (away.lengthSqr() > 1.0E-4D) {
                away = new Vec3(away.x, 0.0D, away.z).normalize();
                attacker.setDeltaMovement(attacker.getDeltaMovement().add(away.scale(0.45D)).add(0.0D, 0.2D, 0.0D));
                attacker.hurtMarked = true;
            }
        }
        playGuardFlourish();
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 at = position().add(getLookAngle().scale(0.6D)).add(0.0D, 1.1D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.35D, 0.3D, 0.35D, 0.4D);
            serverLevel.sendParticles(GOLD_DUST, at.x, at.y, at.z, 12, 0.4D, 0.35D, 0.4D, 0.02D);
            serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK,
                    SoundSource.NEUTRAL, 1.1F, 1.15F);
            ParrySounds.perfect(serverLevel, at, getRandom());
        }
    }

    /** 读指令反制：要么能后撤，要么能反手剑气，才值得中断连招。 */
    public boolean canCounterParry() {
        return action == Action.NONE && (canUseIai() || shortBackstepCd <= 0);
    }

    /** 玩家/生物摆出招架：短后撤 + 反手甩一道白昼一文字。 */
    public void counterParry(LivingEntity target) {
        if (action != Action.NONE || target == null) return;
        this.actionTarget = target;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.NOTE, getX(), getEyeY() + 0.9D, getZ(), 3, 0.18D, 0.1D, 0.18D, 0.0D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.NEUTRAL, 0.6F, 0.8F);
        }
        boolean stepped = shortBackstep();
        if (canUseIai()) {
            if (stepped) {
                pendingIai = 1;
            } else {
                startIaiSlash(target);
            }
        }
    }

    /**
     * 普攻不是“命中刻只测目标中心点”：按长剑实际长度检查目标碰撞箱，并放宽高速移动时的边缘余量。
     * 这样小体型怪、上坡怪和刚被上一段击退的怪都能稳定吃到下一段。
     */
    private void hitComboArc(LivingEntity target, int stage) {
        if (!canHitWithSword(target)) return;
        hitCombo(target, stage);
    }

    private boolean canContinueCombo(LivingEntity target) {
        if (!isValidTarget(target, true)) return false;
        double followRange = ATTACK_RANGE + 2.0D + target.getBbWidth() * 0.5D;
        return distanceToSqr(target) <= followRange * followRange;
    }

    private void hitCombo(LivingEntity target, int stage) {
        float amount = stage == 2 ? FINISHER_DAMAGE : COMBO_DAMAGE;
        boolean landed = dealPhysical(target, amount);
        Vec3 away = target.position().subtract(position());
        if (away.lengthSqr() < 1.0E-4) away = getLookAngle();
        away = new Vec3(away.x, 0.0D, away.z).normalize();
        if (stage == 0) {
            // 第一段：横斩，击退 1 格。
            target.setDeltaMovement(target.getDeltaMovement().add(away.scale(0.55D)));
        } else if (stage == 1) {
            // 第二段：上挑，挑飞 2 格高。
            target.setDeltaMovement(target.getDeltaMovement().add(away.scale(0.25D)).add(0.0D, 0.62D, 0.0D));
        } else {
            // 第三段：斜劈，双倍伤害 + 落点冲击波。
            target.setDeltaMovement(target.getDeltaMovement().add(away.scale(0.35D)).add(0.0D, 0.15D, 0.0D));
        }
        target.hurtMarked = true;
        // Hit-Stop：命中瞬间给目标 2 刻重度迟缓（砍到肉上的顿挫感），只影响这一瞬间。
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 255, false, false));
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 at = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
            if (stage == 2) {
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 4, 0.5D, 0.35D, 0.5D, 0.0D);
                serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 18, 0.4D, 0.4D, 0.4D, 0.35D);
                serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.getX(), target.getY() + 0.1D,
                        target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7F, 1.6F);
            } else {
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 2, 0.35D, 0.25D, 0.35D, 0.0D);
                serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3D, 0.3D, 0.3D, 0.2D);
            }
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.0F, stage == 2 ? 0.75F : 1.1F);
        }
        if (landed) comboCd = 4;
    }

    /** 读指令反制：取消连招 → 短后撤 → 反手一道白昼一文字（斗气够的话）。 */
    private void cancelComboIntoCounter(LivingEntity target) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.NOTE, getX(), getEyeY() + 0.9D, getZ(), 3, 0.18D, 0.1D, 0.18D, 0.0D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.NEUTRAL, 0.6F, 0.8F);
        }
        this.action = Action.NONE;
        syncAction();
        boolean stepped = shortBackstep();
        if (canUseIai()) {
            if (stepped) {
                // 后撤的 dash 一结束就接剑气。
                pendingIai = 1;
                this.actionTarget = target;
            } else {
                startIaiSlash(target);
            }
        }
    }

    // ---------------------------------------------------------------- 白昼一文字
    public void startIaiSlash(LivingEntity target) {
        if (!canUseIai()) return;
        skillQiBefore = battleQi();
        if (!spendQi(IAI_COST)) return;
        iaiCd = highQiAggression() ? 120 : 200;
        this.actionTarget = target;
        startAction(Action.IAI_SLASH, IAI_WINDUP + 10, target);
        faceTarget(target);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                    SoundSource.HOSTILE, 0.7F, 0.6F);
        }
    }

    /** 前摇的金色闪烁：拔刀术的蓄势。 */
    public void playIaiWindup() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 at = position().add(getLookAngle().scale(0.7D)).add(0.0D, 1.1D, 0.0D);
        serverLevel.sendParticles(GOLD_DUST, at.x, at.y, at.z, 10, 0.3D, 0.4D, 0.3D, 0.02D);
        serverLevel.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 3, 0.18D, 0.25D, 0.18D, 0.01D);
    }

    /**
     * 1 秒前摇结束：16 格贯穿金色剑气。
     *
     * <p>r263 起剑气按目标的**真实高度**瞄准：目标在高空就斜着往天上劈，不再水平射出去
     * 从脚底下穿过去。命中判定也从「水平投影后的距离」改成三维点到线段的距离。</p>
     */
    private void releaseIaiSlash(LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 from = getEyePosition();
        Vec3 dir = iaiDirection(target);
        Vec3 end = from.add(dir.scale(IAI_RANGE));
        // 剑气粒子：一条金色光柱，末端留一点空间裂痕；方向带高度，所以能斜着指向空中。
        for (double step = 0.0D; step <= IAI_RANGE; step += 0.55D) {
            Vec3 point = from.add(dir.scale(step));
            serverLevel.sendParticles(GOLD_DUST, point.x, point.y - 0.15D, point.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.END_ROD, point.x, point.y - 0.1D, point.z, 1,
                    0.02D, 0.02D, 0.02D, 0.0D);
        }
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y - 0.2D, end.z, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.6F, 0.65F);
        serverLevel.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 0.8F, 1.8F);

        AABB area = new AABB(from, end).inflate(2.0D);
        for (LivingEntity candidate : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                entity -> isValidDamageTarget(entity))) {
            if (!inIaiLine(from, dir, candidate)) continue;
            float percent = candidate.getMaxHealth() * IAI_PERCENT_HEALTH;
            // 玩家举着招架、且剑士这次施放前斗气高于 1000：这一剑可以被挡下，伤害减半。
            boolean guarded = candidate instanceof ServerPlayer player && parriedByPlayer(player, IAI_COST);
            float scale = guarded ? 0.5F : 1.0F;
            if (guarded && level() instanceof ServerLevel) {
                Vec3 shield = playerPosition(candidate);
                for (int i = 0; i < 14; i++) {
                    double angle = i * (Math.PI * 2.0D / 14.0D);
                    serverLevel.sendParticles(GOLD_DUST, shield.x + Math.cos(angle) * 1.0D,
                            shield.y, shield.z + Math.sin(angle) * 1.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                serverLevel.playSound(null, shield.x, shield.y, shield.z, SoundEvents.SHIELD_BLOCK,
                        SoundSource.NEUTRAL, 1.0F, 1.7F);
            }
            dealPhysical(candidate, (IAI_PHYSICAL + percent) * scale);
            SpellDamage.hitElement(serverLevel, this, candidate, ElementType.LIGHT.id(), IAI_LIGHT * scale);
            if (guarded && candidate instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.swordmaster.iai_guarded"), true);
            }
        }
    }

    /** 剑气的方向：朝目标碰撞箱中心（含高度）瞄；目标没了就顺着视线走。 */
    private Vec3 iaiDirection(LivingEntity target) {
        Vec3 look = getLookAngle();
        if (target == null || !target.isAlive()) {
            return look.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : look.normalize();
        }
        Vec3 aim = target.getBoundingBox().getCenter().subtract(getEyePosition());
        if (aim.lengthSqr() < 1.0E-6D) return look;
        return aim.normalize();
    }

    /** 三维点到线段的距离小于 1.1 格、且在前方 16 格内，就算被剑气贯穿（空中目标照样算）。 */
    private boolean inIaiLine(Vec3 from, Vec3 dir, LivingEntity candidate) {
        Vec3 center = candidate.getBoundingBox().getCenter();
        Vec3 delta = center.subtract(from);
        double along = delta.dot(dir);
        if (along < -0.5D || along > IAI_RANGE + 1.0D) return false;
        double perpendicular = delta.subtract(dir.scale(along)).length();
        return perpendicular <= 1.1D + candidate.getBbWidth() * 0.5D;
    }

    // ---------------------------------------------------------------- 逆牙穿霄
    public void startChargeSkill(LivingEntity target) {
        if (!canUseCharge()) return;
        skillQiBefore = battleQi();
        if (!spendQi(CHARGE_COST)) return;
        chargeCd = highQiAggression() ? 150 : 240;
        this.actionTarget = target;
        startAction(Action.CHARGE_SKILL, CHARGE_WINDUP + 30, target);
        faceTarget(target);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW,
                    SoundSource.HOSTILE, 1.0F, 0.8F);
        }
    }

    private void launchCharge(LivingEntity target) {
        if (target == null || !target.isAlive()) { endAction(); return; }
        // 不是放射线：预读目标当前速度，剑士本人朝预计落点冲过去；tickDash 里还会逐刻修正。
        // r263：连高度一起算 —— 目标在天上就直接斜着撞上去，不再贴着地面冲锋。
        Vec3 predicted = target.getBoundingBox().getCenter()
                .add(target.getDeltaMovement().scale(2.0D));
        Vec3 dir = predicted.subtract(position().add(0.0D, getBbHeight() * 0.5D, 0.0D));
        if (dir.lengthSqr() < 1.0E-4D) dir = getLookAngle();
        startDash(dir.normalize().scale(CHARGE_SPEED_PER_TICK), CHARGE_MAX_TICKS, true);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP,
                    SoundSource.HOSTILE, 0.9F, 1.9F);
        }
    }

    /** 冲刺每刻检查是否撞上目标：命中就结算 48 物理 + 击退 16 格 + 打掉一半斗气。 */
    private boolean checkChargeImpact() {
        LivingEntity target = actionTarget;
        // r269：动作进行中的目标已由锁定环节把关，这里用战斗上下文口径，护主例外目标也能正常撞中。
        if (!isValidTarget(target, true)) return false;
        // 攻击距离延长 1 倍：命中半径 1.8 → 3.6 格，空中目标按三维距离一起算。
        if (distanceTo(target) > CHARGE_HIT_RADIUS + target.getBbWidth() * 0.5D) return false;
        // 撞上的这一刻停止冲刺。
        endDash();
        applyChargeImpact(target);
        endAction();
        return true;
    }

    private void applyChargeImpact(LivingEntity target) {
        // 玩家举着招架、且剑士施放前斗气 > 1500：这一撞可以被挡下，伤害与斗气损耗都减半。
        boolean guarded = target instanceof ServerPlayer player && parriedByPlayer(player, CHARGE_COST);
        float scale = guarded ? 0.5F : 1.0F;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0D, target.getZ(),
                    6, 0.4D, 0.5D, 0.4D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0D, target.getZ(),
                    26, 0.5D, 0.6D, 0.5D, 0.5D);
            serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND,
                    SoundSource.HOSTILE, 1.2F, 1.9F);
        }
        if (guarded && target instanceof ServerPlayer player) {
            Vec3 shield = playerPosition(player);
            if (level() instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 18; i++) {
                    double angle = i * (Math.PI * 2.0D / 18.0D);
                    serverLevel.sendParticles(GOLD_DUST, shield.x + Math.cos(angle) * 1.2D,
                            shield.y + 0.2D, shield.z + Math.sin(angle) * 1.2D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                serverLevel.playSound(null, shield.x, shield.y, shield.z, SoundEvents.SHIELD_BLOCK,
                        SoundSource.NEUTRAL, 1.2F, 1.6F);
            }
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.swordmaster.charge_guarded"), true);
        }
        dealPhysical(target, CHARGE_DAMAGE * scale);
        Vec3 away = target.position().subtract(position());
        if (away.lengthSqr() < 1.0E-4) away = getLookAngle();
        away = new Vec3(away.x, 0.0D, away.z).normalize();
        // 精确推行约 16 格：复用法术系统的逐刻碰撞位移，不依赖原版阻力“猜”初速度。
        // 撞墙会提前停止，玩家也由该系统同步传送位置，不会只在服务端移动。
        NewSpellSystem.knock(position(), target, guarded ? 8.0D : 16.0D);
        target.setDeltaMovement(target.getDeltaMovement().add(away.scale(0.15D)).add(0.0D, 0.42D, 0.0D));
        target.hurtMarked = true;
        drainBattleQi(target, guarded ? 0.5F : 1.0F);
    }

    /** 直接扣目标一半斗气（scale 用于被招架时减半）；归零由 setBattleQi 自动触发斗气溃散。 */
    private void drainBattleQi(LivingEntity target, float scale) {
        if (target == null) return;
        int current = ManaData.get(target).battleQi();
        if (current <= 0) return;
        int next = Math.max(0, current - (int)(current * CHARGE_QI_DRAIN_RATIO * scale));
        ManaData.setBattleQi(target, next);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0D,
                    target.getZ(), 24, 0.4D, 0.5D, 0.4D, 0.25D);
            serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2F, 0.8F);
        }
    }

    // ---------------------------------------------------------------- 喝药 / 集气
    public boolean canStartPotion() {
        return potionCd <= 0 && !isBusy() && lowHealth() && getHealth() < getMaxHealth();
    }

    /**
     * 血量告急：先看看「现在危不危险」，再决定要不要后撤。
     *
     * <p>以前不管离得多远都连撤两次大后撤（每次 16 格），喝一瓶药要跑到天涯海角。
     * 现在只有敌人真的贴在 8 格内、或者刚挨过打，才先撤一次拉开距离；
     * 安全时就地喝完接着打。后撤失败（背后是墙）也照样马上喝，不再干等。</p>
     */
    public void beginPotionEscape() {
        if (!canStartPotion()) return;
        if (potionThreatNearby()) {
            pendingBigBacksteps = 1;
            pendingPotion = true;
            if (startBigBackstep()) return;   // 撤完由 onActionFinished 接着喝
            pendingBigBacksteps = 0;
        }
        pendingPotion = false;
        startPotionDrink();
    }

    /** 身边有没有正在打的敌人（或者刚挨过打、还在交战中）。 */
    private boolean potionThreatNearby() {
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && distanceTo(target) <= POTION_THREAT_RADIUS) return true;
        return tickCount - getLastHurtByMobTimestamp() < POTION_OUT_OF_COMBAT_TICKS;
    }

    /**
     * 脱战自愈：没有目标、没人打、静了 4 秒、血掉了 15% 以上，就自己掏药喝。
     *
     * <p>以前只有「血量低于 40% 且在打架」才会喝，打完一架半血晃回村里也不补，
     * 玩家看着就像他从来不回血。现在脱战会自己喝，喝完进冷却，不会连着灌。</p>
     */
    private void tickPotionRecovery() {
        if (level().isClientSide || isBusy() || potionCd > 0) return;
        if (getTarget() != null || getHealth() >= getMaxHealth() * POTION_RECOVERY_RATIO) return;
        if (tickCount - getLastHurtByMobTimestamp() < POTION_OUT_OF_COMBAT_TICKS) return;
        startPotionDrink();
    }

    private void startPotionDrink() {
        potionCd = 400;
        startAction(Action.POTION, POTION_TICKS, null);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_DRINK,
                    SoundSource.NEUTRAL, 0.8F, 1.0F);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BOTTLE_FILL,
                    SoundSource.NEUTRAL, 0.8F, 1.2F);
        }
    }

    private void finishPotion() {
        // 喝的就是模组里的「回复药剂」：结算整个交给物品本体，
        // 回多少血、洗几个负面状态，都和玩家自己喝那瓶药一模一样（50% 最大生命 + 洗 1 个负面状态）。
        if (!drinkHealingDraught()) heal(getMaxHealth() * POTION_HEAL_RATIO);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, getX(), getY() + 1.4D, getZ(), 6, 0.4D, 0.4D, 0.4D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0D, getZ(), 14, 0.5D, 0.6D, 0.5D, 0.02D);
            // 空瓶不再丢出去：瓶子由药剂物品自己回收，地上不会再散落玻璃瓶。
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE,
                    SoundSource.NEUTRAL, 0.7F, 1.4F);
        }
    }

    /** 把模组里的回复药剂用在他自己身上；拿不到物品就返回 false，交给备用回血。 */
    private boolean drinkHealingDraught() {
        if (!GeneratedMod.MOD_ITEMS.containsKey(ManaPlants.HEALING_DRAUGHT)) return false;
        ItemStack draught = new ItemStack(GeneratedMod.MOD_ITEMS.get(ManaPlants.HEALING_DRAUGHT).get());
        draught.finishUsingItem(level(), this);
        return true;
    }

    /** 喝药被打断：没有治疗、瓶子也不掉。 */
    public void interruptPotion() {
        if (action != Action.POTION) return;
        endAction();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.2D, getZ(), 8, 0.3D, 0.3D, 0.3D, 0.02D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK,
                    SoundSource.NEUTRAL, 0.8F, 1.4F);
        }
    }

    public boolean canStartGather() {
        return gatherCd <= 0 && !isBusy() && lowQi();
    }

    /**
     * 魔斗气凝聚能不能起手：斗气「极低」（低于 1000）、手上魔素够 500、不在冷却里。
     *
     * <p>和长时间打坐的集气不同，这一招是拿魔素换斗气的爆发手段：2 秒换 2500 斗气，
     * 冷却 10 秒，所以不会变成无限回气。</p>
     */
    public boolean canStartCondense() {
        return condenseCd <= 0 && !isBusy() && battleQi() < CONDENSE_THRESHOLD
                && mana() >= CONDENSE_MANA_COST;
    }

    /** 凝聚前先看情况：敌人贴脸就先大后撤一步，安全时原地念。 */
    public void beginCondenseEscape() {
        if (!canStartCondense()) return;
        if (potionThreatNearby()) {
            // 只撤一次就念（喝药才会连撤两次）：他只需要拉开能念完 2 秒的距离。
            pendingBigBacksteps = 0;
            pendingCondense = true;
            if (startBigBackstep()) return;
        }
        pendingCondense = false;
        startCondense();
    }

    /** 开始凝聚：沿用集气的动作动画，念满 2 秒才结算。 */
    private void startCondense() {
        if (!canStartCondense()) return;
        condenseCd = CONDENSE_COOLDOWN;
        startAction(Action.CONDENSE, CONDENSE_TICKS, null);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.NEUTRAL, 0.8F, 1.5F);
        }
    }

    /** 凝聚完成：扣 500 魔素、补 2500 斗气（魔素不够就什么都不发生）。 */
    private void finishCondense() {
        if (mana() < CONDENSE_MANA_COST) return;
        if (!ManaData.spendMana(this, CONDENSE_MANA_COST)) return;
        addQi(CONDENSE_QI_GAIN);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(QI_DUST, getX(), getY() + 1.0D, getZ(),
                    40, 0.6D, 0.9D, 0.6D, 0.12D);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.2D, getZ(),
                    24, 0.7D, 0.8D, 0.7D, 0.2D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.NEUTRAL, 0.9F, 1.4F);
        }
    }

    /** 凝聚被打断：白念，魔素和斗气都不动。 */
    public void interruptCondense() {
        if (action != Action.CONDENSE) return;
        endAction();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.2D, getZ(),
                    8, 0.3D, 0.3D, 0.3D, 0.02D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.NEUTRAL, 0.7F, 1.5F);
        }
    }

    /** 集气前也先大后撤两次拉开距离，然后原地打坐。 */
    public void beginGatherEscape() {
        if (!canStartGather()) return;
        pendingBigBacksteps = 2;
        pendingGather = true;
        startBigBackstep();
    }

    private void startGather() {
        gatherCd = 300;
        gatherRestored = 0.0D;
        gatherAccum = 0.0D;
        gatherTicks = 0;
        startAction(Action.GATHER, GATHER_MAX_TICKS, null);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_PLACE,
                    SoundSource.NEUTRAL, 0.7F, 1.6F);
        }
    }

    private void tickGather() {
        if (action != Action.GATHER) return;
        gatherTicks++;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (battleQi() >= maxBattleQi() || gatherTicks >= GATHER_MAX_TICKS || gatherRestored > 900.0D) {
            endAction();
            return;
        }
        double before = battleQi();
        gatherAccum += GATHER_PER_TICK;
        int whole = (int)gatherAccum;
        gatherAccum -= whole;
        if (whole > 0) addQi(whole);
        gatherRestored += Math.max(0.0D, battleQi() - before);
        // 红色斗气一圈一圈从地面升起来。
        if (gatherTicks % 4 == 0) {
            double angle = gatherTicks * 0.55D;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                serverLevel.sendParticles(QI_DUST, getX() + Math.cos(a) * 0.8D, getY() + 0.1D,
                        getZ() + Math.sin(a) * 0.8D, 2, 0.05D, 0.15D, 0.05D, 0.02D);
            }
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.2D, getZ(),
                    2, 0.15D, 0.1D, 0.15D, 0.01D);
        }
        if (gatherTicks % 20 == 0) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.NEUTRAL, 0.5F, 1.6F);
        }
    }

    /** 集气被打断：扣回这次已经恢复的量，并给一个愤怒低吼的反馈。 */
    public void interruptGather(String reason) {
        if (action != Action.GATHER) return;
        int lost = (int)Math.round(gatherRestored);
        if (lost > 0) addQi(-lost);
        endAction();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.1D, getZ(), 3, 0.2D, 0.1D, 0.2D, 0.0D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR,
                    SoundSource.NEUTRAL, 0.45F, 1.9F);
        }
    }

    // ---------------------------------------------------------------- 空间探测 / 脱困
    /**
     * 从当前位置沿 dir 还能自由走多远（最多 max 格）。
     *
     * <p>后撤、冲撞、绕障全靠它：每 0.25 格拿碰撞箱探一次，撞到东西就返回上一次通畅的距离。
     * 以前后撤只检查「背后 2 格」，被墙、台阶、树挡住照样起跳，结果原地播一段后撤动画人就没了位移。</p>
     */
    public double freeRunLength(Vec3 dir, double max) {
        Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
        if (flat.lengthSqr() < 1.0E-6D) return 0.0D;
        flat = flat.normalize();
        double reach = 0.0D;
        for (double probe = 0.25D; probe <= max + 1.0E-6D; probe += 0.25D) {
            AABB box = getBoundingBox().move(flat.x * probe, 0.0D, flat.z * probe);
            if (!level().noCollision(this, box)) break;
            reach = probe;
        }
        return reach;
    }

    /** 前方一格高、上方是空的？那就不是死路，抬腿能跨上去（step_height 只有 0.8，走不上去，得跳）。 */
    private boolean canHopOver(Vec3 dir) {
        Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
        if (flat.lengthSqr() < 1.0E-6D) return false;
        flat = flat.normalize();
        AABB raised = getBoundingBox().move(flat.x * 1.05D, 1.05D, flat.z * 1.05D);
        return level().noCollision(this, raised);
    }

    /** 把水平向量按角度转一圈（脱困、绕障都用同一套）。 */
    private static Vec3 rotateFlat(Vec3 v, double degrees) {
        Vec3 flat = new Vec3(v.x, 0.0D, v.z);
        if (flat.lengthSqr() < 1.0E-6D) return new Vec3(0.0D, 0.0D, 1.0D);
        flat = flat.normalize();
        double angle = Math.toRadians(degrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(flat.x * cos - flat.z * sin, 0.0D, flat.x * sin + flat.z * cos).normalize();
    }

    /**
     * 沿 dir 找一块站得住的空地并瞬身过去（实在被方块围死的最后手段）。
     * 优先挑脚下有地面的落点，找不到就退而求其次挑任意空位。
     */
    private boolean blinkAlong(Vec3 dir, double maxDistance) {
        Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
        if (flat.lengthSqr() < 1.0E-6D) return false;
        flat = flat.normalize();
        Vec3 grounded = null;
        Vec3 fallback = null;
        for (double distance = maxDistance; distance >= 1.0D; distance -= 0.5D) {
            Vec3 at = position().add(flat.scale(distance));
            AABB box = getBoundingBox().move(at.x - getX(), at.y - getY(), at.z - getZ());
            if (!level().noCollision(this, box)) continue;
            // 脚下 1.2 格内有实心方块 = 有地面，优先。
            AABB below = box.move(0.0D, -1.2D, 0.0D);
            if (!level().noCollision(this, below) && grounded == null) {
                grounded = at;
                break;
            }
            if (fallback == null) fallback = at;
        }
        Vec3 destination = grounded != null ? grounded : fallback;
        if (destination == null) return false;
        teleportTo(destination.x, destination.y, destination.z);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.6D, getZ(),
                    10, 0.3D, 0.4D, 0.3D, 0.04D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.NEUTRAL, 0.5F, 1.9F);
        }
        return true;
    }

    /** 被方块围住时的脱困：先跳，再侧移，最后瞬身到最近的空地。 */
    private void unstuck() {
        Vec3 toward = getTarget() != null ? getTarget().position().subtract(position()) : getLookAngle();
        toward = new Vec3(toward.x, 0.0D, toward.z);
        if (toward.lengthSqr() < 1.0E-4D) toward = new Vec3(0.0D, 0.0D, 1.0D);
        toward = toward.normalize();
        // 1) 面前只是 1 格高的坎：跳一下就能过去，别用瞬身。
        if (onGround() && canHopOver(toward) && freeRunLength(toward, 2.0D) >= 0.5D) {
            setDeltaMovement(getDeltaMovement().x, 0.42D, getDeltaMovement().z);
            move(MoverType.SELF, toward.scale(0.35D));
            return;
        }
        // 2) 侧后方找个空位走一步；还不行就瞬过去。
        for (double angle : new double[]{45.0D, -45.0D, 90.0D, -90.0D, 135.0D, -135.0D, 180.0D}) {
            Vec3 dir = rotateFlat(toward, angle);
            if (freeRunLength(dir, 1.6D) >= 1.0D) {
                move(MoverType.SELF, dir.scale(0.9D));
                setDeltaMovement(getDeltaMovement().x, 0.32D, getDeltaMovement().z);
                return;
            }
        }
        if (!blinkAlong(toward, 4.0D)) blinkAlong(rotateFlat(toward, 90.0D), 4.0D);
    }

    /**
     * 卡住检测：追人时连续 30 刻几乎没挪窝，就主动脱困，而不是在原地一直推墙。
     *
     * <p>r264：脱困也不是无限的。以前被墙围死时，它每 30 刻「跳一下 / 侧移一步 / 瞬身 4 格」
     * 再重新追，如此循环到天荒地老，玩家看到的就是它在墙边漫无目的地游走、别的什么都不干。
     * 现在连续 3 轮脱困还在原地，就认定这个目标走不到：清目标 + 记 10 秒黑名单
     * （{@link MobPathing}），老老实实回去巡逻，等玩家自己翻过来。</p>
     */
    private void tickStuckEscape() {
        if (tickCount % 10 != 0) return;
        if (isBusy() || getTarget() == null) {
            lastStuckPos = position();
            stuckTicks = 0;
            unstuckBursts = 0;
            return;
        }
        if (lastStuckPos.distanceToSqr(position()) > 0.09D) {
            lastStuckPos = position();
            stuckTicks = 0;
            unstuckBursts = 0;
            return;
        }
        if (++stuckTicks < 3) return;
        stuckTicks = 0;
        LivingEntity stuckTarget = getTarget();
        unstuck();
        // 脱困后重新寻路，否则原版导航还停在「到不了」的旧路径上。
        getNavigation().stop();
        if (stuckTarget == null) return;
        if (++unstuckBursts >= MAX_UNSTUCK_BURSTS) {
            // 连着几轮都脱不出去：放弃目标，别再对着墙演下去。
            unstuckBursts = 0;
            setTarget(null);
            MobPathing.markUnreachable(this, stuckTarget, MobPathing.UNREACHABLE_TICKS);
            return;
        }
        MobPathing.chase(this, stuckTarget, 1.05D);
    }

    // ---------------------------------------------------------------- 后撤
    /** 短后撤：4 格，2 秒冷却，落地横剑防御。 */
    public boolean shortBackstep() {
        if (shortBackstepCd > 0 || dashTicks > 0) return false;
        // 先看清路：背后真退得开才起跳，全被堵死就干脆不撤（留给 AI 换个战术），
        // 绝不再演一次「原地后撤」给玩家看。
        Vec3 away = retreatDirection(4.0D, 5);
        if (away == null) return false;
        shortBackstepCd = SHORT_BACKSTEP_COOLDOWN;
        startDash(away, 5, true);
        startAction(Action.BACKSTEP_SHORT, 10, actionTarget);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.NEUTRAL, 0.7F, 1.6F);
        }
        return true;
    }

    /** 大后撤：16 格，连续 4 次后进入 4 秒冷却。 */
    public boolean startBigBackstep() {
        if (bigBackstepCd > 0 || dashTicks > 0 || bigBackstepChain >= BIG_BACKSTEP_CHAIN_LIMIT) return false;
        bigBackstepChain++;
        if (bigBackstepChain >= BIG_BACKSTEP_CHAIN_LIMIT) {
            bigBackstepChain = 0;
            bigBackstepCd = BIG_BACKSTEP_COOLDOWN;
        }
        Vec3 away = retreatDirection(16.0D, 12);
        if (away == null) {
            // 背后完全被封死：改成瞬身拉开距离，保证「危机脱战」这条设计意图不会因为一堵墙失灵。
            Vec3 look = getLookAngle();
            Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
            if (!blinkAlong(back, 6.0D) && !blinkAlong(rotateFlat(back, 90.0D), 6.0D)) return false;
            startAction(Action.BACKSTEP_LONG, 16, actionTarget);
            return true;
        }
        startDash(away, 12, true);
        startAction(Action.BACKSTEP_LONG, 16, actionTarget);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.NEUTRAL, 0.6F, 1.7F);
            serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.6D, getZ(), 14, 0.3D, 0.3D, 0.3D, 0.05D);
        }
        return true;
    }

    /**
     * 后撤方向：在背后 ±120° 的七个方向里挑「真正退得开」的那一条。
     *
     * <p>不再固定正后方：站在墙角、背靠岩壁、背后是台阶时，会自动改成侧后方撤退。</p>
     *
     * <p><b>r263：背后有方块挡路时改成斜着往后上方撤。</b> 水平方向能退多少就退多少，
     * 剩下那段距离全部换算成高度（{@code h² + v² = 原距离²}），所以「总距离」不变，
     * 只是把一条平的后撤改成一条向上斜的抛物线，跳过背后的坎和矮墙，不再原地撞墙失灵。
     * 头顶也被封死（山洞里）才返回 {@code null}，交给调用方用瞬身。</p>
     *
     * @param distance 设计稿上想退开的格数
     * @param ticks    分几刻退完（每刻位移 = 实际能退的距离 / 刻数）
     */
    private Vec3 retreatDirection(double distance, int ticks) {
        Vec3 look = getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
        if (back.lengthSqr() < 1.0E-4D) back = new Vec3(0.0D, 0.0D, 1.0D);
        back = back.normalize();
        Vec3 best = null;
        double bestReach = 0.0D;
        for (double angle : new double[]{0.0D, 40.0D, -40.0D, 80.0D, -80.0D, 120.0D, -120.0D}) {
            Vec3 dir = rotateFlat(back, angle);
            double reach = freeRunLength(dir, distance);
            if (reach > bestReach) {
                bestReach = reach;
                best = dir;
            }
        }
        if (best == null) return null;
        double horizontal = Math.min(distance, bestReach);
        double rise = 0.0D;
        if (horizontal < distance) {
            // 水平退不满：把剩下的距离变成高度，斜着跃出去（总位移长度仍是设计稿的格数）。
            rise = Math.sqrt(Math.max(0.0D, distance * distance - horizontal * horizontal));
            rise = Math.min(rise, Math.min(MAX_BACKSTEP_JUMP, distance));
            rise = Math.min(rise, freeRise(rise));
        }
        // 横着退不动、头顶也封死：这条路彻底死了，让调用方改用瞬身。
        if (horizontal < 0.75D && rise < 0.75D) return null;
        Vec3 total = new Vec3(best.x * horizontal, rise, best.z * horizontal);
        return total.scale(1.0D / Math.max(1, ticks));
    }

    /** 头顶往上最多还能升多高（撞到天花板就停）。 */
    private double freeRise(double max) {
        double reach = 0.0D;
        for (double probe = 0.25D; probe <= max + 1.0E-6D; probe += 0.25D) {
            if (!level().noCollision(this, getBoundingBox().move(0.0D, probe, 0.0D))) break;
            reach = probe;
        }
        return reach;
    }

    private boolean isCornered(Vec3 away) {
        return freeRunLength(away, 2.0D) < 1.0D;
    }

    /** 被逼入死角或需要拉开距离放技能时，优先大后撤。 */
    public boolean wantsBigBackstep() {
        Vec3 look = getLookAngle();
        Vec3 away = new Vec3(-look.x, 0.0D, -look.z).normalize();
        return bigBackstepCd <= 0 && bigBackstepChain < BIG_BACKSTEP_CHAIN_LIMIT
                && (isCornered(away) || distanceToTarget() > 6.0D && getTarget() != null);
    }

    // ---------------------------------------------------------------- 冲刺位移
    public void startDash(Vec3 perTick, int ticks, boolean keepGravity) {
        this.dashVec = perTick;
        this.dashTicks = ticks;
        this.dashKeepGravity = keepGravity;
        setSprinting(false);
        getNavigation().stop();
    }

    public void endDash() {
        this.dashTicks = 0;
        this.dashVec = Vec3.ZERO;
        this.dashBlockedTicks = 0;
    }

    /**
     * 冲刺走一步，撞上东西会自己想办法。
     *
     * <p>老的实现是直接 {@code move(MoverType.SELF, dashVec)}：被墙一挡就原地不动，
     * 后撤变成原地踏步、冲撞变成原地冲刺。现在按「抬腿跨坎 → 贴墙侧滑 → 报告失败」三级兜底，
     * 返回这一步实际挪动的水平距离。</p>
     */
    private double dashStep(Vec3 step) {
        Vec3 before = position();
        double want = step.length();
        move(MoverType.SELF, step);
        if (before.distanceTo(position()) >= want * 0.55D) return before.distanceTo(position());

        // 1) 前方只是 1 格高的坎：起跳跨过去。
        if (onGround() && canHopOver(step)) {
            setDeltaMovement(getDeltaMovement().x, 0.42D, getDeltaMovement().z);
            move(MoverType.SELF, step);
            if (before.distanceTo(position()) >= want * 0.55D) return before.distanceTo(position());
        }

        // 2) 贴墙侧滑：沿墙往两侧各滑一段，取能挪开的那边。
        for (double angle : new double[]{90.0D, -90.0D, 60.0D, -60.0D}) {
            Vec3 side = rotateFlat(step, angle).scale(want);
            Vec3 mark = position();
            move(MoverType.SELF, side);
            if (mark.distanceTo(position()) >= want * 0.45D) break;
        }
        return before.distanceTo(position());
    }

    private void tickDash() {
        if (dashTicks <= 0) return;
        if (level().isClientSide) return;
        dashTicks--;
        if (action == Action.CHARGE_SKILL && actionElapsed > CHARGE_WINDUP) {
            // 高智商追踪冲撞：每刻根据目标新位置微调方向，但真正移动的是剑士本人，不生成任何射线。
            LivingEntity target = actionTarget;
            if (isValidTarget(target, true)) {
                Vec3 predicted = target.getBoundingBox().getCenter()
                        .add(target.getDeltaMovement().scale(1.5D));
                Vec3 correction = predicted.subtract(position().add(0.0D, getBbHeight() * 0.5D, 0.0D));
                if (correction.lengthSqr() > 1.0E-4D) {
                    dashVec = correction.normalize().scale(CHARGE_SPEED_PER_TICK);
                    faceTarget(target);
                }
            }
            // 6.4 格/刻一帧就能跨过目标，拆成小步逐段检查实体碰撞与墙体。
            double length = dashVec.length();
            int steps = Math.max(1, (int)Math.ceil(length / 0.55D));
            Vec3 step = dashVec.scale(1.0D / steps);
            boolean hit = false;
            double moved = 0.0D;
            for (int i = 0; i < steps; i++) {
                moved += dashStep(step);
                if (checkChargeImpact()) {
                    hit = true;
                    break;
                }
            }
            if (hit) return;
            // 整段冲撞几乎没位移 = 撞在方块上了：立刻收招，别让「逆牙穿霄」贴在墙上蓄完再原地放空。
            if (moved < length * 0.35D) {
                if (++dashBlockedTicks >= 2) {
                    endDash();
                    endAction();
                    return;
                }
            } else {
                dashBlockedTicks = 0;
            }
        } else {
            // 冲刺期间接管位移：撞墙会先抬腿/侧滑，实在走不动就收掉这段动作。
            double moved = dashStep(dashVec);
            if (moved < dashVec.length() * 0.4D) {
                if (++dashBlockedTicks >= 2) {
                    // 后撤被彻底堵死：瞬身到最近的空地，保证「拉开距离」这个战术目标仍然达成。
                    Vec3 dir = dashVec.lengthSqr() > 1.0E-6D
                            ? new Vec3(dashVec.x, 0.0D, dashVec.z).normalize()
                            : getLookAngle();
                    blinkAlong(dir, 5.0D);
                    if (freeRunLength(dir, 5.0D) < 1.0D) blinkAlong(rotateFlat(dir, 90.0D), 5.0D);
                    endDash();
                    endAction();
                    return;
                }
            } else {
                dashBlockedTicks = 0;
            }
        }
        if (!dashKeepGravity) setDeltaMovement(getDeltaMovement().x, 0.0D, getDeltaMovement().z);
        if (level() instanceof ServerLevel serverLevel && dashTicks % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2D, getZ(),
                    2, 0.12D, 0.05D, 0.12D, 0.01D);
        }
        if (dashTicks <= 0) {
            if (action == Action.COMBO_3) endAction();
            if (action == Action.BACKSTEP_SHORT || action == Action.BACKSTEP_LONG) endAction();
        }
    }

    // ---------------------------------------------------------------- 防御
    public boolean isParrying(LivingEntity target) {
        return target instanceof Player player && BattleQiParry.isParrying(player);
    }

    /** 玩家举着招架，且剑士这次施放技能前的斗气高于该技能的消耗门槛。 */
    private boolean parriedByPlayer(ServerPlayer player, int threshold) {
        return skillQiBefore > threshold && BattleQiParry.inStance(player);
    }

    public boolean blocksWithShield(LivingEntity target) {
        if (target == null || !target.isBlocking()) return false;
        // r263：掉落长剑不再有自己的「举剑当盾」，它和普通剑一样走斗气招架；
        // 这里只认原版盾，玩家举盾时连招才会收手后撤。
        return target.getUseItem().is(Items.SHIELD);
    }

    private void playShieldRetreatFlourish() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK,
                    SoundSource.NEUTRAL, 0.8F, 1.5F);
        }
    }

    /** 近战完美格挡的成功率：首次 100%，其余 50%，对同一目标每 12 秒保底一次 100%。 */
    public boolean rollPerfectParry(LivingEntity attacker) {
        long now = level().getGameTime();
        Long last = perfectParryAt.get(attacker.getUUID());
        boolean guaranteed = last == null || now - last >= PERFECT_PARry_INTERVAL;
        boolean success = guaranteed || getRandom().nextFloat() < 0.5F;
        if (success) perfectParryAt.put(attacker.getUUID(), now);
        return success;
    }

    /** 完美格挡成功：与玩家完美招架一致，攻击者眩晕 1 秒并获得 1 秒招架破绽（+75% 受伤）。 */
    public void playPerfectParry(LivingEntity attacker) {
        // 完美格挡回 10% 斗气（r263）。
        addQi((int)Math.round(maxBattleQi() * PERFECT_PARRY_QI_RESTORE));
        NewSpellSystem.control(this, attacker, NewSpellContent.STUN, BattleQiParry.STUN_TICKS);
        attacker.removeEffect(NewSpellContent.PARRY_VULNERABLE);
        NewSpellSystem.effect(attacker, NewSpellContent.PARRY_VULNERABLE,
                BattleQiParry.VULNERABILITY_TICKS, 0);
        Vec3 away = attacker.position().subtract(position());
        if (away.lengthSqr() > 1.0E-4) {
            away = new Vec3(away.x, 0.0D, away.z).normalize();
            attacker.setDeltaMovement(attacker.getDeltaMovement().add(away.scale(0.8D)).add(0.0D, 0.2D, 0.0D));
            attacker.hurtMarked = true;
        }
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 at = position().add(getLookAngle().scale(0.7D)).add(0.0D, 1.1D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 26, 0.5D, 0.4D, 0.5D, 0.6D);
            serverLevel.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 14, 0.45D, 0.4D, 0.45D, 0.25D);
            serverLevel.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 16, 0.5D, 0.4D, 0.5D, 0.3D);
            serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK,
                    SoundSource.NEUTRAL, 1.3F, 0.7F);
            serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND,
                    SoundSource.NEUTRAL, 0.6F, 1.9F);
            ParrySounds.perfect(serverLevel, at, getRandom());
        }
        playGuardFlourish();
    }

    /** 普通格挡（斗气低于 1000 或没掷中完美格挡）：伤害打五折，不再弹开。 */
    public void playNormalParry() {
        playGuardFlourish();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK,
                    SoundSource.NEUTRAL, 0.9F, 1.1F);
            Vec3 at = position().add(getLookAngle().scale(0.6D)).add(0.0D, 1.0D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3D, 0.3D, 0.3D, 0.25D);
        }
    }

    /** 举剑格挡动画（很短的一个防御姿态）。 */
    public void playGuardFlourish() {
        playGuardFlourish(10);
    }

    /** 指定举剑多少刻：假动作要一直举到窗口结束，不能举两下就放下。 */
    public void playGuardFlourish(int ticks) {
        entityData.set(DATA_GUARDING, true);
        guardFlourishCd = Math.max(guardFlourishCd, ticks + 2);
        if (action == Action.NONE) {
            startAction(Action.GUARD, ticks, getTarget());
        } else if (action == Action.GUARD) {
            // 已经在举剑：把剩余刻数拉长，别让窗口还没过就收刀。
            this.actionTicks = Math.max(this.actionTicks, ticks);
            entityData.set(DATA_ACTION_TICKS, actionTicks);
        }
    }

    /** 远程箭矢格挡：50% 概率、花 2 点斗气。 */
    public boolean tryRangedGuard() {
        if (battleQi() < RANGED_GUARD_COST) return false;
        if (getRandom().nextFloat() >= 0.5F) return false;
        spendQi(RANGED_GUARD_COST);
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 at = position().add(0.0D, 1.1D, 0.0D).add(getLookAngle().scale(0.5D));
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 18, 0.3D, 0.3D, 0.3D, 0.3D);
            serverLevel.sendParticles(GOLD_DUST, at.x, at.y, at.z, 10, 0.3D, 0.3D, 0.3D, 0.02D);
            serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK,
                    SoundSource.NEUTRAL, 1.0F, 1.5F);
        }
        playGuardFlourish();
        return true;
    }

    /** 雇佣友伤豁免的金色光盾。 */
    public void playOwnerShield(Vec3 at) {
        if (level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 18; i++) {
                double angle = i * (Math.PI * 2.0D / 18.0D);
                double radius = 1.1D;
                serverLevel.sendParticles(GOLD_DUST,
                        getX() + Math.cos(angle) * radius, getY() + 1.0D, getZ() + Math.sin(angle) * radius,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            serverLevel.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.0D, getZ(), 16, 0.8D, 0.8D, 0.8D, 0.4D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK,
                    SoundSource.NEUTRAL, 1.0F, 1.7F);
        }
    }

    // ---------------------------------------------------------------- 瞬身闪避
    /** 被远程 / 法术锁定时侧移 3 格；60% 概率、5 秒冷却。 */
    public boolean tryBlinkDodge(Entity threat) {
        if (blinkCd > 0 || threat == null) return false;
        if (getRandom().nextFloat() >= BLINK_CHANCE) return false;
        Vec3 from = threat.position();
        Vec3 delta = position().subtract(from);
        Vec3 side = new Vec3(-delta.z, 0.0D, delta.x);
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize().scale(BLINK_DISTANCE);
        if (getRandom().nextBoolean()) side = side.scale(-1.0D);
        if (!tryTeleportSide(side)) {
            side = side.scale(-1.0D);
            if (!tryTeleportSide(side)) return false;
        }
        blinkCd = BLINK_COOLDOWN;
        this.invulnerableTime = Math.max(this.invulnerableTime, BLINK_INVULNERABLE_TICKS);
        dodgeGraceUntil = tickCount + BLINK_INVULNERABLE_TICKS;
        entityData.set(DATA_ACTION, Action.BLINK_DODGE.ordinal());
        entityData.set(DATA_ACTION_TICKS, 9);
        startAction(Action.BLINK_DODGE, 9, actionTarget);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5D, getZ(), 12, 0.3D, 0.5D, 0.3D, 0.05D);
            serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.8D, getZ(), 6, 0.3D, 0.4D, 0.3D, 0.02D);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.NEUTRAL, 0.55F, 1.9F);
        }
        return true;
    }

    private boolean tryTeleportSide(Vec3 offset) {
        Vec3 destination = position().add(offset);
        AABB box = getBoundingBox().move(offset);
        if (!level().noCollision(this, box)) return false;
        teleportTo(destination.x, destination.y, destination.z);
        return true;
    }

    public boolean dodging() {
        return tickCount <= dodgeGraceUntil;
    }

    // ---------------------------------------------------------------- 目标
    /** 目标选择入口：雇佣时优先雇主受到/发起的威胁，其次护主例外，最后才按巡逻口径挑敌对生物。 */
    public LivingEntity findCombatTarget() {
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        Player owner = owner();
        if (owner != null) {
            LivingEntity attacker = owner.getLastHurtByMob();
            if (isValidTarget(attacker, true)) return attacker;
            LivingEntity victim = owner.getLastHurtMob();
            if (isValidTarget(victim, true)) return victim;
            LivingEntity guard = findAttackerOf(owner);
            if (guard != null) return guard;
        }
        // r269 护主例外：猪灵这类平时不主动打的中立生物，一旦在欺负玩家 / 村民 / 铁傀儡，
        // 立刻视为敌对目标反击；这个优先级排在普通巡逻目标之前。
        LivingEntity protect = findGuardTarget(serverLevel);
        if (protect != null) return protect;
        double range = 48.0D;
        LivingEntity best = null;
        double bestDistance = range * range;
        for (LivingEntity candidate : serverLevel.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(range), this::isHostileTarget)) {
            if (!isValidTarget(candidate, false)) continue;
            double distance = distanceToSqr(candidate);
            if (distance < bestDistance) { bestDistance = distance; best = candidate; }
        }
        return best;
    }

    private LivingEntity findAttackerOf(Player owner) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().inflate(24.0D), entity -> entity instanceof net.minecraft.world.entity.Mob mob
                        && mob.getTarget() == owner)) {
            if (!isValidTarget(candidate, true)) continue;
            double distance = distanceToSqr(candidate);
            if (distance < bestDistance) { bestDistance = distance; best = candidate; }
        }
        return best;
    }

    /** 护主扫描半径：剑士在这个范围内看到"中立生物欺负被保护者"就出手。 */
    private static final double GUARD_RADIUS = 24.0D;
    /** 反击例外的记忆窗口：例外条件瞬时中断（被击退、停顿）时，目标再多保留 5 秒。 */
    private static final int COUNTER_MEMORY = 100;
    private UUID counterTargetId;
    private int counterTargetUntil;

    /** 护主例外锁定：正在威胁玩家 / 村民 / 铁傀儡，但按巡逻口径不会主动打的生物。 */
    private LivingEntity findGuardTarget(ServerLevel serverLevel) {
        LivingEntity best = null;
        double bestDistance = GUARD_RADIUS * GUARD_RADIUS;
        for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class,
                getBoundingBox().inflate(GUARD_RADIUS),
                m -> m != this && m.isAlive() && !(m instanceof WanderingSwordmaster)
                        // 傀儡不打：铁傀儡追打玩家是它自己在执法，别把村庄守卫内讧起来。
                        && !(m instanceof AbstractGolem)
                        && !isHostileTarget(m) && isValidTarget(m, true))) {
            if (!isThreateningGuarded(mob)) continue;
            double distance = distanceToSqr(mob);
            if (distance < bestDistance) { bestDistance = distance; best = mob; }
        }
        return best;
    }

    /** 这只生物现在是不是在欺负被保护者（追着打，或 5 秒内实际打伤过）。 */
    private boolean isThreateningGuarded(Mob mob) {
        Player owner = owner();
        if (owner != null) {
            if (mob.getTarget() == owner) return true;
            if (owner.getLastHurtByMob() == mob) return true;
            if (owner.getLastHurtMob() == mob) return true;   // 雇主先手打了它
        }
        if (level() instanceof ServerLevel serverLevel) {
            for (LivingEntity guarded : serverLevel.getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(GUARD_RADIUS), WanderingSwordmaster::isGuardedVictim)) {
                if (mob.getTarget() == guarded || guarded.getLastHurtByMob() == mob) return true;
            }
        }
        return false;
    }

    /** 被保护者：村庄里的玩家、村民、铁傀儡（雪傀儡也算村民一方的守卫）。 */
    private static boolean isGuardedVictim(LivingEntity entity) {
        if (entity instanceof Villager || entity instanceof AbstractGolem) return true;
        return entity instanceof Player player && !player.isSpectator();
    }

    /**
     * r269 反击例外：巡逻口径不该主动打的当前目标，什么时候允许继续打。
     * 成立条件：打了剑士本人 / 打了雇主 / 被雇主先手打 / 正在欺负玩家、村民、铁傀儡。
     * 条件成立时刷新 5 秒记忆，免得战斗中一次击退就把目标清空。
     */
    private boolean isRetaliationTarget(LivingEntity target) {
        boolean live = false;
        if (getLastHurtByMob() == target) live = true;                       // 它打了我
        Player owner = owner();
        if (owner != null && (owner.getLastHurtByMob() == target             // 它打了雇主
                || owner.getLastHurtMob() == target)) live = true;           // 雇主打了它
        if (!live && target instanceof Mob mob) live = isThreateningGuarded(mob);
        if (live) {
            counterTargetId = target.getUUID();
            counterTargetUntil = tickCount + COUNTER_MEMORY;
            return true;
        }
        return counterTargetId != null && target.getUUID().equals(counterTargetId)
                && tickCount <= counterTargetUntil;
    }

    private boolean isHostileTarget(LivingEntity candidate) {
        if (candidate == this || !candidate.isAlive() || candidate instanceof WanderingSwordmaster) return false;
        // r269「不浪费时间」名单：猪灵族（含蛮兵）、僵尸猪灵、元素史莱姆平时绝不主动招惹，
        // 只有在 {@link #isRetaliationTarget} 的护主 / 反击例外成立时才会交手。
        if (isNonAggroNeutral(candidate)) return false;
        // 原版敌对怪走 Enemy；本模组哥布林不是 Enemy 接口，必须明确列入剑士的敌对阵营。
        return candidate instanceof Enemy || candidate instanceof Goblin
                || candidate.getType().getCategory() == MobCategory.MONSTER;
    }

    /** 平时不主动攻击的中立生物：原版按 MONSTER 注册、但本质中立，最容易拖住剑士的时间。 */
    public static boolean isNonAggroNeutral(LivingEntity entity) {
        return entity instanceof AbstractPiglin || entity instanceof ZombifiedPiglin
                || entity instanceof ElementalSlime;
    }

    /**
     * 能不能把这只生物当成目标。
     *
     * @param hiredDefense false = 巡逻口径，只允许"见谁打谁"的纯敌对生物；
     *                     true = 战斗上下文（已锁定目标的延续、护主 / 反击例外），
     *                     中立生物在 {@link #isRetaliationTarget} 场景下也算合法目标。
     */
    public boolean isValidTarget(LivingEntity candidate, boolean hiredDefense) {
        if (candidate == null || !candidate.isAlive() || candidate == this) return false;
        if (candidate instanceof WanderingSwordmaster) return false;
        if (isHired() && ownerUUID != null && ownerUUID.equals(candidate.getUUID())) return false;
        if (candidate.isSpectator()) return false;
        if (candidate instanceof Player player && player.isCreative()) return false;
        if (!hiredDefense && !isHostileTarget(candidate)) return false;
        // 雇佣状态下保护雇主：雇主发起的攻击目标与攻击雇主的目标都算。
        return true;
    }

    private boolean isValidDamageTarget(LivingEntity candidate) {
        if (candidate == this || !candidate.isAlive() || candidate.isSpectator()) return false;
        if (candidate instanceof WanderingSwordmaster) return false;
        if (isHired() && ownerUUID != null && ownerUUID.equals(candidate.getUUID())) return false;
        return true;
    }

    public double distanceToTarget() {
        LivingEntity target = getTarget();
        return target == null ? Double.MAX_VALUE : distanceTo(target);
    }

    // ---------------------------------------------------------------- 攻击判定
    /** 4 格长剑判定：按目标碰撞箱最近点算距离，避免小怪、上下坡和大型怪中心点导致假性打空。 */
    public boolean canReach(LivingEntity target) {
        return canHitWithSword(target);
    }

    private boolean canHitWithSword(LivingEntity target) {
        if (!isValidTarget(target, true)) return false;
        Vec3 swordOrigin = position().add(0.0D, getBbHeight() * 0.58D, 0.0D);
        double range = ATTACK_RANGE + 0.85D;
        if (target.getBoundingBox().inflate(0.35D).distanceToSqr(swordOrigin) > range * range) return false;
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(swordOrigin);
        if (toTarget.lengthSqr() < 1.0E-6D) return true;
        Vec3 flatLook = getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        Vec3 flatTarget = toTarget.multiply(1.0D, 0.0D, 1.0D);
        if (flatLook.lengthSqr() < 1.0E-6D || flatTarget.lengthSqr() < 1.0E-6D) return true;
        // 约 78° 的前方扇形；动作进行中实体朝向同步有延迟，不能再用过窄的 55° 点判定。
        return flatTarget.normalize().dot(flatLook.normalize()) >= 0.20D;
    }

    /**
     * 结算一次物理伤害：临时清掉受击无敌帧，保证三段连招不会互相吞伤害；
     * 结算后把原保护时间与新产生的保护时间取最大值放回去。
     */
    public boolean dealPhysical(LivingEntity target, float amount) {
        if (target == null || amount <= 0.0F) return false;
        if (!isValidDamageTarget(target)) return false;
        int before = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            return target.hurt(damageSources().mobAttack(this), amount);
        } finally {
            target.invulnerableTime = Math.max(before, target.invulnerableTime);
        }
    }

    public void faceTarget(LivingEntity target) {
        if (target == null) return;
        getLookControl().setLookAt(target, 60, 60);
        Vec3 delta = target.position().subtract(position());
        float yaw = (float)(Math.atan2(delta.z, delta.x) * 180.0D / Math.PI) - 90.0F;
        setYRot(yaw);
        yHeadRot = yaw;
    }

    // ---------------------------------------------------------------- 移动速度
    public void applyMoveSpeed(double speed) {
        if (Math.abs(speed - speedModifierValue) < 1.0E-4) return;
        AttributeInstance attribute = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(SPEED.id);
        double delta = speed - WALK_SPEED;
        if (Math.abs(delta) > 1.0E-4) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(SPEED.id, delta,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        speedModifierValue = speed;
        entityData.set(DATA_RUNNING, speed > (WALK_SPEED + RUN_SPEED) * 0.5D);
    }

    public void setRunning(boolean running) {
        applyMoveSpeed(running ? RUN_SPEED : WALK_SPEED);
    }

    // ---------------------------------------------------------------- 音效
    // r256：剑士的人声台词整套删除（用户要求「不要了」），沉吟 / 受伤 / 倒下都不再出声。
    // 挥剑、格挡、绝技这些「武器音」仍然保留，玩家该听到的反馈一点没少。
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // 之前借用铁砧脚步声，太吵且不像人；这里交还方块自带的脚步声。
        super.playStepSound(pos, state);
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    // ---------------------------------------------------------------- 死亡掉落
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        // 必定掉落：耐久用掉一半的专属长剑 + 1 个剑士徽记。
        ItemStack blade = SwordmasterWeaponItem.weatheredBlade(level);
        spawnAtLocation(blade, 1.2F);
        if (GeneratedMod.MOD_ITEMS.containsKey(SwordmasterSigilItem.ITEM_ID)) {
            spawnAtLocation(new ItemStack(GeneratedMod.MOD_ITEMS.get(SwordmasterSigilItem.ITEM_ID).get()), 1.0F);
        }
        entityData.set(DATA_HIRED, false);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 集气、喝药的打断放在真正受击时；事件那边还会补一次针对特定来源的判断。
        if (action == Action.GATHER && amount > 10.0F) interruptGather("damage");
        if (action == Action.CONDENSE) interruptCondense();
        if (action == Action.POTION) interruptPotion();
        return super.hurt(source, amount);
    }

    // ---------------------------------------------------------------- 动画粒子
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (action == Action.CONDENSE && actionElapsed % 3 == 0 && level() instanceof ServerLevel serverLevel) {
            // 魔斗气凝聚：赤红斗气从脚下螺旋升上来，外面再裹一层蓝色魔素光点。
            double angle = actionElapsed * 0.55D;
            double radius = 0.85D - Math.min(0.45D, actionElapsed * 0.01D);
            serverLevel.sendParticles(QI_DUST, getX() + Math.cos(angle) * radius,
                    getY() + 0.2D + actionElapsed * 0.03D, getZ() + Math.sin(angle) * radius,
                    2, 0.04D, 0.08D, 0.04D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.1D, getZ(),
                    1, 0.4D, 0.5D, 0.4D, 0.05D);
        }
        if (action == Action.IAI_SLASH && actionElapsed < IAI_WINDUP && actionElapsed % 3 == 0) playIaiWindup();
        if (action == Action.CHARGE_SKILL && actionElapsed < CHARGE_WINDUP && actionElapsed % 4 == 0) {
            if (level() instanceof ServerLevel serverLevel) {
                Vec3 at = getEyePosition().add(getLookAngle().scale(1.0D));
                serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.15D, 0.15D, 0.15D, 0.15D);
            }
        }
        // 待机时剑尖偶尔划地的小火花（村庄巡逻的细节）。
        if (action == Action.NONE && ambientCooldown <= 0 && !isHired()) {
            ambientCooldown = 60 + getRandom().nextInt(80);
            if (level() instanceof ServerLevel serverLevel) {
                Vec3 at = position().add(getLookAngle().scale(0.9D)).add(0.0D, 0.1D, 0.0D);
                serverLevel.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 2, 0.1D, 0.05D, 0.1D, 0.01D);
            }
        }
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        if (entity instanceof WanderingSwordmaster) return true;
        if (isHired() && ownerUUID != null && ownerUUID.equals(entity.getUUID())) return true;
        return super.isAlliedTo(entity);
    }

    /** 避免 ResourceLocation 常量在静态初始化时拿不到 GeneratedMod.MOD_ID 的问题。 */
    private static final class ResourceLocationHolder {
        final net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        GeneratedMod.MOD_ID, "swordmaster_speed");
    }

    private static Vec3 playerPosition(LivingEntity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
    }
}
