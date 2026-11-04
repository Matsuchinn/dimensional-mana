package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 魔蚀凋零（Corrupted Wither）：被魔素泡透、又被凋零之心撑开的凋零变异体。
 *
 * <p>继承原版 {@link WitherBoss}，因此外形、浮空运动和受击动画都沿用凋零；但原来的
 * 三头骷髅弹幕、半血破方块这些逻辑全部由本类接管（重写 {@link #customServerAiStep()}），
 * 换成设计稿里的一套「限伤 + 聚合体 + 幻影 + 射线」。</p>
 *
 * <p>数值（见各常量）：1000 血、30 万魔素 / 5000 斗气、移速是原版凋零的 1.25 倍，
 * 血量低于 30% 进入二阶段（1.45 倍移速、接触即粉碎方块、双资源每秒 50 回复）。</p>
 *
 * <p>受击链路（限伤、双抗、聚合体吸收、幻影分裂、击杀回魔）统一写在
 * {@link CorruptedWitherEvents}，因为 1.21.1 的受击入口是事件而不是实体的 {@code hurt}。</p>
 *
 * <p>渲染（光环、发光外壳、拖尾、聚合体、射线、故障幻影、屏幕暗角与抖动）全部在客户端
 * {@link CorruptedWitherRenderer} / {@link CorruptedWitherClientFx} 一侧，本类只同步
 * 「二阶段 / 正在蓄力 / 降雨」三个状态位。</p>
 */
public final class CorruptedWither extends WitherBoss {
    // ---------------------------------------------------------------- 设计稿数值
    /** 生命上限。 */
    public static final float MAX_HEALTH = 2000.0F;
    /** 魔素池上限。 */
    public static final double MAX_MANA = 300_000.0D;
    /** 斗气池上限。 */
    public static final int MAX_QI = 5_000;
    /** 原版凋零的移动速度（{@code WitherBoss.createAttributes()} 里的 0.6）。 */
    public static final double VANILLA_WITHER_SPEED = 0.6D;
    /** 一阶段移速倍率。 */
    public static final double PHASE1_SPEED_MULTIPLIER = 1.25D;
    /** 二阶段移速倍率。 */
    public static final double PHASE2_SPEED_MULTIPLIER = 1.45D;
    /** 进入二阶段的血量比例。 */
    public static final double PHASE2_TRIGGER_RATIO = 0.30D;
    /** 一阶段每秒回魔 / 回斗气。 */
    public static final double PHASE1_REGEN_PER_SECOND = 1.0D;
    /** 二阶段每秒回魔 / 回斗气。 */
    public static final double PHASE2_REGEN_PER_SECOND = 50.0D;
    /** 限伤：单次伤害只结算 1 点（冷却缩到 1 秒，连击可以穿透）。 */
    public static final float DAMAGE_CAP = 1.0F;
    /** 限伤被动的冷却：1 秒。 */
    public static final int DAMAGE_CAP_COOLDOWN = 20;
    /** 一阶段：光易伤 +15%，其余全属性减伤 35%。 */
    public static final double PHASE1_LIGHT_VULNERABILITY = 1.15D;
    public static final double PHASE1_RESIST = 0.65D;
    /** 二阶段：光易伤 +45%，全属性减伤只剩 5%。 */
    public static final double PHASE2_LIGHT_VULNERABILITY = 1.45D;
    public static final double PHASE2_RESIST = 0.95D;
    /** 虚无暴食：魔法 / 物理各自额外减伤 25%。 */
    public static final double ABSORB_REDUCTION = 0.25D;
    /** 每颗聚合体最多吞下多少伤害，以及最多同时存在几颗。 */
    public static final float ABSORB_PER_AGGREGATE = 100.0F;
    public static final int MAX_AGGREGATES_PER_COLOR = 6;
    /** 普通攻击【侵蚀凋零】的魔素消耗、伤害与状态时长。 */
    public static final float EROSION_SKULL_COST = 500.0F;
    public static final float EROSION_SKULL_DAMAGE = 10.0F;
    public static final int EROSION_STATUS_TICKS = 200;
    public static final int EROSION_SKULL_COOLDOWN = 60;
    /** 普通攻击打满这么多次之后，下一次普通攻击升级成「原地倾泻骷髅雨」。 */
    public static final int BARRAGE_EVERY = 10;
    /** 一次倾泻喷出多少颗骷髅头。 */
    public static final int BARRAGE_SKULLS = 36;
    /** 倾泻时两颗之间隔多少刻：2 刻 = 每秒 10 颗，一轮 3.6 秒喷完。 */
    public static final int BARRAGE_INTERVAL = 2;
    /** 【虚无反噬】：每颗聚合体 500 魔素，冷却 10 秒，前摇 3.5 秒。 */
    public static final float VOLLEY_COST_PER_AGGREGATE = 500.0F;
    public static final int VOLLEY_COOLDOWN = 200;
    public static final int VOLLEY_CHARGE_TICKS = 70;
    /** 【虚无凋零射线】：1 万魔素、7 秒原地蓄力、10 秒持续射击，冷却 30 秒。 */
    public static final float RAY_COST = 10_000.0F;
    public static final int RAY_COOLDOWN = 600;
    public static final int RAY_TOTAL_TICKS = 140 + 200;
    /** 【凋零幻影】：二阶段受击分裂，同时最多 2 个，冷却 5 秒。 */
    public static final int MAX_PHANTOMS = 2;
    public static final int PHANTOM_COOLDOWN = 100;
    /** 腐化魔素雨：召唤后持续 5 分钟，每 10 秒给露天玩家叠一层魔蚀。 */
    public static final int RAIN_DURATION_TICKS = 6_000;
    public static final int RAIN_STACK_INTERVAL = 200;
    /** 下雨时最多影响到多远的玩家。 */
    public static final double RAIN_RANGE = 64.0D;
    /** 画面同步包（暗角 / 抖动 / 降雨）的发送间隔。 */
    public static final int FX_SYNC_INTERVAL = 5;

    private static final EntityDataAccessor<Boolean> DATA_PHASE_TWO =
            SynchedEntityData.defineId(CorruptedWither.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CHARGING =
            SynchedEntityData.defineId(CorruptedWither.class, EntityDataSerializers.BOOLEAN);

    /** 自己的 Boss 血条；不走原版那条私有血条，避免同屏两根。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.dimensional_mana.corrupted_wither"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private int damageCapCooldown;
    private int skullCooldown;
    /** 距离上一次倾泻已经打了多少发普通攻击。 */
    private int erosionShots;
    /** 倾泻还剩多少刻。 */
    private int barrageTicks;
    /** 这一轮倾泻已经喷出多少颗。 */
    private int barrageFired;
    /** 正在开火的射线实体 id（用来让身体跟着光束转）。 */
    private int beamEntityId = -1;
    private int volleyCooldown;
    private int volleyCharge;
    private int rayCooldown;
    private int rayTicks;
    private int phantomCooldown;
    private int blockBreakTimer;
    private int regenAccumulator;
    private int rainTicks;
    private int fxTimer;
    /** 魔素 / 斗气池只初始化一次。 */
    private boolean poolsInitialized;
    /** 击杀生物回魔的累计值（小数部分先攒着）。 */
    private double killManaAccumulator;
    /** 最近一次施放技能的玩家，用于给射线下达瞄准目标。 */
    private UUID rayTargetId;

    public CorruptedWither(EntityType<? extends CorruptedWither> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setNoGravity(true);
        this.moveControl = new FlyingMoveControl(this, 12, true);
        this.bossEvent.setDarkenScreen(false);
        this.bossEvent.setCreateWorldFog(false);
        this.bossEvent.setPlayBossMusic(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WitherBoss.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, VANILLA_WITHER_SPEED * PHASE1_SPEED_MULTIPLIER)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ---------------------------------------------------------------- 同步数据 / 存档
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE_TWO, Boolean.FALSE);
        builder.define(DATA_CHARGING, Boolean.FALSE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CorruptPhaseTwo", isPhaseTwo());
        tag.putInt("CorruptRain", rainTicks);
        tag.putInt("CorruptSkullCd", skullCooldown);
        tag.putInt("CorruptErosionShots", erosionShots);
        tag.putInt("CorruptVolleyCd", volleyCooldown);
        tag.putInt("CorruptRayCd", rayCooldown);
        tag.putInt("CorruptCapCd", damageCapCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE_TWO, tag.getBoolean("CorruptPhaseTwo"));
        this.rainTicks = tag.getInt("CorruptRain");
        this.skullCooldown = tag.getInt("CorruptSkullCd");
        this.erosionShots = tag.getInt("CorruptErosionShots");
        this.volleyCooldown = tag.getInt("CorruptVolleyCd");
        this.rayCooldown = tag.getInt("CorruptRayCd");
        this.damageCapCooldown = tag.getInt("CorruptCapCd");
        applyPhaseAttributes();
    }

    // ---------------------------------------------------------------- 对外状态
    public boolean isPhaseTwo() {
        return this.entityData.get(DATA_PHASE_TWO);
    }

    /** 正在蓄力（虚无反噬或射线）：蓄力时屏幕暗角会加深。 */
    public boolean isCharging() {
        return this.entityData.get(DATA_CHARGING);
    }

    public boolean isRainingCorruption() {
        return this.rainTicks > 0;
    }

    /** 召唤成功后由仪式调用：压暗天空、开始腐化雨。 */
    public void beginCorruptedRain() {
        this.rainTicks = RAIN_DURATION_TICKS;
        if (level() instanceof ServerLevel serverLevel) {
            if (!serverLevel.isRaining()) {
                serverLevel.setWeatherParameters(0, RAIN_DURATION_TICKS, true, true);
            }
        }
    }

    /** 击杀生物回 1% 魔素（由 {@link CorruptedWitherEvents} 的死亡事件调用）。 */
    public void onKillManaRestore() {
        ManaData.restoreMana(this, MAX_MANA * 0.01D, false);
    }

    /** 限伤被动是否可用（不在 4 秒冷却里）。 */
    public boolean damageCapReady() {
        return this.damageCapCooldown <= 0;
    }

    /** 限伤被动结算一次后进入冷却。 */
    public void triggerDamageCapCooldown() {
        this.damageCapCooldown = DAMAGE_CAP_COOLDOWN;
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        // 召唤动画：原版倒计时由父类 aiStep 推进；这里再兜一手，保证 11 秒内一定能被打。
        if (getInvulnerableTicks() > 0 && tickCount % 2 == 0) {
            setInvulnerableTicks(Math.max(0, getInvulnerableTicks() - 1));
        }
        if (damageCapCooldown > 0) damageCapCooldown--;
        if (skullCooldown > 0) skullCooldown--;
        if (volleyCooldown > 0) volleyCooldown--;
        if (rayCooldown > 0) rayCooldown--;
        if (phantomCooldown > 0) phantomCooldown--;
        if (rayTicks > 0) {
            rayTicks--;
            if (rayTicks == 0) this.entityData.set(DATA_CHARGING, Boolean.FALSE);
        }
        if (!poolsInitialized) {
            poolsInitialized = true;
            // 召唤仪式是直接 addFreshEntity，不经过 finalizeSpawn，这里补一次池子初始化。
            ManaData.setMobPools(this, MAX_MANA, MAX_QI);
        }
        if (barrageTicks > 0) tickBarrage();
        tickVolleyHeartbeat();
        tickBossBar();
        tickRegen();
        tickRain();
        tickPhase();
        // 一阶段就允许撞碎地形：它的飞行不再走原版凋零的寻路，遇到墙也不会绕，
        // 不给它破块能力就会一直顶着墙抖，看起来像「不会动了」。
        tickTerrainCrush();
        tickSummons();
        if (--fxTimer <= 0) {
            fxTimer = FX_SYNC_INTERVAL;
            syncScreenFx();
        }
    }

    @Override
    protected void customServerAiStep() {
        if (level().isClientSide) return;
        if (getInvulnerableTicks() > 0) return;   // 原版召唤动画期间不行动
        LivingEntity target = resolveTarget();
        if (rayTicks > 0) {
            // 蓄力 / 射击期间原地不动，而且只跟着光束的方向转头：
            // 不调用 setLookAt(目标)，否则打死一个目标就会立刻扭头去瞄下一个，头甩来甩去很出戏。
            this.setDeltaMovement(Vec3.ZERO);
            lookAlongBeam();
            return;
        }
        if (barrageTicks > 0) {
            // 倾泻期间同样是原地不动，头跟着目标（目标没了就保持当前朝向）。
            this.setDeltaMovement(Vec3.ZERO);
            Vec3 aim = target != null
                    ? target.getEyePosition()
                    : position().add(getLookAngle().scale(8.0D));
            this.getLookControl().setLookAt(aim.x, aim.y, aim.z, 30.0F, 30.0F);
            return;
        }
        if (target == null) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.96D));
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        moveToward(target);
        tickCombat(target);
    }

    /** 目标失效就清掉；每 20 刻重新挑最近的合法生物（仇恨一切生物）。 */
    private LivingEntity resolveTarget() {
        // 射线蓄力 / 射击期间锁死目标：打完就打完，不会立刻扭头去追别的生物。
        // （目标死了就返回 null，光束保持最后一个方向继续扫。）
        if (rayTicks > 0) {
            LivingEntity locked = getTarget();
            return locked != null && locked.isAlive() ? locked : null;
        }
        LivingEntity current = getTarget();
        if (current != null && (!current.isAlive() || !isValidTarget(current))) {
            setTarget(null);
            current = null;
        }
        if (current != null && tickCount % 20 != 0) return current;
        if (!(level() instanceof ServerLevel serverLevel)) return current;
        AABB box = getBoundingBox().inflate(64.0D);
        List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class, box, this::isValidTarget);
        LivingEntity best = current;
        double bestDistance = current == null ? Double.MAX_VALUE : distanceToSqr(current);
        for (LivingEntity candidate : candidates) {
            double distance = distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best != current) setTarget(best);
        return best;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == this || !entity.isAlive()) return false;
        // 自己的幻影也是 WitherBoss，这里连召唤物一起排掉，免得本体跟分身对打。
        if (entity instanceof WitherBoss) return false;
        if (isOwnSummon(entity)) return false;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (CrystalClusterData.isManaMutant(entity)) return false;
        return true;
    }

    /** 保持 7～14 格的浮空距离，太近就往后撤。 */
    private void moveToward(LivingEntity target) {
        Vec3 offset = target.position().subtract(position());
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        Vec3 flat = horizontal < 0.01D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(offset.x / horizontal, 0.0D, offset.z / horizontal);
        double wantedX;
        double wantedZ;
        if (horizontal > 16.0D) {
            wantedX = target.getX();
            wantedZ = target.getZ();
        } else if (horizontal < 7.0D) {
            wantedX = getX() - flat.x * 6.0D;
            wantedZ = getZ() - flat.z * 6.0D;
        } else {
            wantedX = getX() + flat.x * 2.0D;
            wantedZ = getZ() + flat.z * 2.0D;
        }
        double wantedY = Math.max(target.getY() + 5.0D, getY() - 2.0D);
        wantedY = Math.min(wantedY, target.getY() + 9.0D);
        this.getMoveControl().setWantedPosition(wantedX, wantedY, wantedZ, 1.0D);
        // 兜底：被地形卡住时自己抬一点，免得原地蹭墙。
        if (this.horizontalCollision) this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.18D, 0.0D));
    }

    // ---------------------------------------------------------------- 技能
    private void tickCombat(LivingEntity target) {
        double distance = distanceTo(target);
        // 二阶段的大招优先：射线期间别的技能全停。
        if (isPhaseTwo() && rayCooldown <= 0 && distance < 48.0D && rayTicks == 0) {
            startRay(target);
            return;
        }
        if (volleyCharge > 0) {
            volleyCharge--;
            if (volleyCharge == 0) launchVolley(target);
            return;
        }
        if (volleyCooldown <= 0 && distance < 28.0D && aggregateCount(true) + aggregateCount(false) > 0) {
            volleyCharge = VOLLEY_CHARGE_TICKS;
            this.entityData.set(DATA_CHARGING, Boolean.TRUE);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("charge"),
                        SoundSource.HOSTILE, 2.0F, 1.15F);
            }
            return;
        }
        if (skullCooldown <= 0 && distance < 32.0D && distance > 2.0D) fireErosionSkull(target);
    }

    /** 【侵蚀凋零】：射出一颗裹着紫色灵魂的骷髅头。 */
    private void fireErosionSkull(LivingEntity target) {
        if (!ManaData.spendMana(this, EROSION_SKULL_COST)) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        this.skullCooldown = EROSION_SKULL_COOLDOWN;
        Vec3 from = new Vec3(getX(), getY() + 2.6D, getZ());
        Vec3 dir = target.getEyePosition().subtract(from);
        if (dir.lengthSqr() < 1.0E-4D) return;
        CorruptedWitherSkull skull = GeneratedMod.CORRUPTED_WITHER_SKULL.get().create(serverLevel);
        if (skull == null) return;
        skull.setOwner(this);
        skull.setPos(from.x, from.y, from.z);
        skull.shoot(dir.x, dir.y, dir.z, 1.25F, 0.5F);
        serverLevel.addFreshEntity(skull);
        serverLevel.playSound(null, from.x, from.y, from.z, CorruptedWitherSounds.get("shoot"),
                SoundSource.HOSTILE, 2.0F, 0.9F);
        // 普通攻击的「附赠品」：身边随机位置再甩 1～3 颗原版凋零头。
        fireVanillaSkulls(serverLevel, target);
        // 每打满 BARRAGE_EVERY 发，下一发升级成原地倾泻。
        if (++this.erosionShots >= BARRAGE_EVERY) {
            this.erosionShots = 0;
            startBarrage();
        }
    }

    /**
     * 每次普通攻击额外甩出 1～3 颗<b>原版</b>凋零头：从本体身边 1.8～4 格、上下错开的位置突然冒出来，
     * 再直直射向同一个目标。
     *
     * <p>用原版 {@link WitherSkull} 而不是自己的侵蚀头，原因有两个：</p>
     * <ul>
     *   <li>原版凋零头的爆炸天然会破坏地形，补上「普通攻击也能炸方块」；</li>
     *   <li>黑色的小骷髅头和紫色的侵蚀头在画面上能一眼分开，弹幕更有层次。</li>
     * </ul>
     *
     * <p>爆炸可能波及自己的幻影，受击事件里已经统一拦掉了（见
     * {@link CorruptedWitherEvents#protectOwnSummons}）。</p>
     */
    private void fireVanillaSkulls(ServerLevel serverLevel, LivingEntity target) {
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.8D + random.nextDouble() * 2.2D;
            Vec3 spawn = new Vec3(
                    getX() + Math.cos(angle) * radius,
                    getY() + 1.2D + random.nextDouble() * 2.4D,
                    getZ() + Math.sin(angle) * radius);
            Vec3 aim = target.getEyePosition().subtract(spawn);
            if (aim.lengthSqr() < 1.0E-4D) continue;
            WitherSkull skull = new WitherSkull(serverLevel, this, aim);
            skull.setPos(spawn.x, spawn.y, spawn.z);
            // 保持原版黑骷髅头：惯性大、飞得稳；蓝色危险头减速太快，中远距离容易半路掉下去。
            skull.setDangerous(false);
            skull.shoot(aim.x, aim.y, aim.z, 1.15F, 1.4F);
            serverLevel.addFreshEntity(skull);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spawn.x, spawn.y, spawn.z,
                    6, 0.3D, 0.3D, 0.3D, 0.02D);
        }
        serverLevel.playSound(null, getX(), getY() + 2.4D, getZ(), CorruptedWitherSounds.get("shoot"),
                SoundSource.HOSTILE, 1.5F, 1.3F);
    }

    // ---------------------------------------------------------------- 普通攻击升级：倾泻骷髅雨
    /**
     * 每 10 次普通攻击之后的「第 11 次」：原地不动，朝目标方向连续喷出 36 颗凋零骷髅头。
     *
     * <p>每颗都照常扣 500 魔素（一轮 18000），魔素撑不住就提前收手。
     * 36 颗带一点随机散布，打出去是一个漏斗形的弹幕，而不是一根直线。</p>
     */
    private void startBarrage() {
        this.barrageTicks = BARRAGE_SKULLS * BARRAGE_INTERVAL;
        this.barrageFired = 0;
        this.entityData.set(DATA_CHARGING, Boolean.TRUE);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("charge"),
                    SoundSource.HOSTILE, 2.2F, 1.3F);
            for (ServerPlayer player : serverLevel.getPlayers(p -> p.distanceToSqr(this) < 48.0D * 48.0D)) {
                player.displayClientMessage(
                        Component.translatable("message.dimensional_mana.corrupted_wither.barrage"), true);
            }
        }
    }

    /** 倾泻的每刻推进：每 BARRAGE_INTERVAL 刻吐一颗，喷完 36 颗就收手。 */
    private void tickBarrage() {
        if (barrageTicks <= 0) return;
        if (!(level() instanceof ServerLevel serverLevel)) {
            endBarrage();
            return;
        }
        if (barrageTicks % BARRAGE_INTERVAL == 0) {
            if (barrageFired >= BARRAGE_SKULLS || !ManaData.spendMana(this, EROSION_SKULL_COST)) {
                endBarrage();
                return;
            }
            spitSkull(serverLevel, getTarget());
            this.barrageFired++;
            if (this.barrageFired >= BARRAGE_SKULLS) {
                endBarrage();
                return;
            }
        }
        this.barrageTicks--;
    }

    private void endBarrage() {
        this.barrageTicks = 0;
        this.barrageFired = 0;
        this.entityData.set(DATA_CHARGING, Boolean.FALSE);
    }

    /** 倾泻中的一颗：方向和普通攻击一样指向目标眼睛，只是加了一点随机散布。 */
    private void spitSkull(ServerLevel serverLevel, LivingEntity target) {
        Vec3 from = new Vec3(getX(), getY() + 2.6D, getZ());
        Vec3 aim = target == null ? getLookAngle().scale(10.0D) : target.getEyePosition().subtract(from);
        if (aim.lengthSqr() < 1.0E-4D) return;
        double spread = 0.05D;
        Vec3 dir = aim.add(random.nextGaussian() * spread,
                random.nextGaussian() * spread * 0.7D,
                random.nextGaussian() * spread);
        CorruptedWitherSkull skull = GeneratedMod.CORRUPTED_WITHER_SKULL.get().create(serverLevel);
        if (skull == null) return;
        skull.setOwner(this);
        skull.setPos(from.x, from.y, from.z);
        skull.shoot(dir.x, dir.y, dir.z, 1.35F, 0.35F);
        serverLevel.addFreshEntity(skull);
        // 每 4 颗响一次，36 连发也不会把耳朵糊住。
        if (barrageFired % 4 == 0) {
            serverLevel.playSound(null, from.x, from.y, from.z, CorruptedWitherSounds.get("shoot"),
                    SoundSource.HOSTILE, 1.7F, 1.05F + barrageFired * 0.01F);
        }
    }

    /**
     * 【虚无反噬】蓄力的心跳：每 12 刻一记「咚」，音调随蓄力进度升高。
     *
     * <p>用的就是 {@code sounds/corrupted_wither/pulse.ogg}——那颗是真实心跳录音铺底的低频冲击，
     * 之前只给射线蓄力用过，现在反噬也能听见「它快憋不住了」。</p>
     */
    private void tickVolleyHeartbeat() {
        if (volleyCharge <= 0 || volleyCharge % 12 != 0) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        float progress = 1.0F - volleyCharge / (float)VOLLEY_CHARGE_TICKS;
        serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("pulse"),
                SoundSource.HOSTILE, 1.1F + progress * 1.1F, 0.85F + progress * 0.55F);
    }

    /** 身体跟着正在开火的光束转：LookControl 上限给 40°，够快又不会瞬移。 */
    private void lookAlongBeam() {
        CorruptedWitherBeam beam = activeBeam();
        if (beam == null) return;
        Vec3 tip = beam.endPoint();
        this.getLookControl().setLookAt(tip.x, tip.y, tip.z, 40.0F, 40.0F);
    }

    private CorruptedWitherBeam activeBeam() {
        if (beamEntityId < 0 || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(beamEntityId);
        return entity instanceof CorruptedWitherBeam beam ? beam : null;
    }

    /** 【虚无反噬】：所有聚合体一起飞出，每颗 100 点（蓝=奥术，红=物理）。 */
    private void launchVolley(LivingEntity target) {
        this.entityData.set(DATA_CHARGING, Boolean.FALSE);
        if (!(level() instanceof ServerLevel serverLevel)) return;
        List<CorruptedWitherAggregate> aggregates = ownedAggregates();
        if (aggregates.isEmpty()) return;
        float cost = aggregates.size() * VOLLEY_COST_PER_AGGREGATE;
        if (!ManaData.spendMana(this, cost)) return;
        this.volleyCooldown = VOLLEY_COOLDOWN;
        for (CorruptedWitherAggregate aggregate : aggregates) aggregate.launch(target);
        serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("phase"),
                SoundSource.HOSTILE, 1.4F, 1.1F);
    }

    /** 【虚无凋零射线】：造一根蓄力 7 秒、射击 10 秒的光束实体。 */
    private void startRay(LivingEntity target) {
        if (!ManaData.spendMana(this, RAY_COST)) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        CorruptedWitherBeam beam = GeneratedMod.CORRUPTED_WITHER_BEAM.get().create(serverLevel);
        if (beam == null) return;
        beam.setup(serverLevel, this, target);
        serverLevel.addFreshEntity(beam);
        this.beamEntityId = beam.getId();
        this.rayTargetId = target.getUUID();
        this.rayTicks = RAY_TOTAL_TICKS;
        this.rayCooldown = RAY_COOLDOWN;
        this.entityData.set(DATA_CHARGING, Boolean.TRUE);
        this.setDeltaMovement(Vec3.ZERO);
    }

    // ---------------------------------------------------------------- 聚合体（虚无暴食）
    public List<CorruptedWitherAggregate> ownedAggregates() {
        if (!(level() instanceof ServerLevel serverLevel)) return List.of();
        List<CorruptedWitherAggregate> result = new ArrayList<>(ownedAggregatesOf(serverLevel, true));
        result.addAll(ownedAggregatesOf(serverLevel, false));
        return result;
    }

    private List<CorruptedWitherAggregate> ownedAggregatesOf(ServerLevel serverLevel, boolean blue) {
        return serverLevel.getEntitiesOfClass(CorruptedWitherAggregate.class, getBoundingBox().inflate(24.0D),
                aggregate -> aggregate.isOwnedBy(this) && aggregate.isBlue() == blue && aggregate.isOrbiting());
    }

    public int aggregateCount(boolean blue) {
        if (!(level() instanceof ServerLevel serverLevel)) return 0;
        return ownedAggregatesOf(serverLevel, blue).size();
    }

    /**
     * 吸收一次伤害：优先塞进没满的聚合体，满了就开一颗新的（最多 6 颗）。
     *
     * @return 真正被吞下的伤害；聚合体全满时返回 0，调用方据此判定「这次减伤失效」。
     */
    public float absorb(boolean blue, float amount) {
        if (amount <= 0.0F || !(level() instanceof ServerLevel serverLevel)) return 0.0F;
        float remaining = amount;
        for (CorruptedWitherAggregate aggregate : ownedAggregatesOf(serverLevel, blue)) {
            if (remaining <= 0.0F) break;
            remaining -= aggregate.store(remaining);
        }
        while (remaining > 0.0F && aggregateCount(blue) < MAX_AGGREGATES_PER_COLOR) {
            CorruptedWitherAggregate aggregate = spawnAggregate(serverLevel, blue);
            if (aggregate == null) break;
            remaining -= aggregate.store(remaining);
        }
        return amount - Math.max(0.0F, remaining);
    }

    private CorruptedWitherAggregate spawnAggregate(ServerLevel serverLevel, boolean blue) {
        CorruptedWitherAggregate aggregate = GeneratedMod.CORRUPTED_WITHER_AGGREGATE.get().create(serverLevel);
        if (aggregate == null) return null;
        aggregate.setup(serverLevel, this, blue, aggregateCount(blue) + aggregateCount(!blue));
        aggregate.setPos(getX(), getY() + 2.0D, getZ());
        serverLevel.addFreshEntity(aggregate);
        return aggregate;
    }

    // ---------------------------------------------------------------- 凋零幻影
    /**
     * 二阶段受击分裂（由受击事件调用，冷却在这里判）。
     *
     * @param attacker 这一下是谁打的；分身的身体和三个头一出场就对准他。
     */
    public void trySpawnPhantom(Entity attacker) {
        if (!isPhaseTwo() || phantomCooldown > 0) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (phantomCount() >= MAX_PHANTOMS) return;
        CorruptedWitherPhantom phantom = GeneratedMod.CORRUPTED_WITHER_PHANTOM.get().create(serverLevel);
        if (phantom == null) return;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        // 先摆好位置再 setup：setup 里要按「分身自己的位置」算朝向攻击者的角度。
        phantom.setPos(getX() + Math.cos(angle) * 3.0D, getY(), getZ() + Math.sin(angle) * 3.0D);
        phantom.setup(serverLevel, this, attacker);
        serverLevel.addFreshEntity(phantom);
        this.phantomCooldown = PHANTOM_COOLDOWN;
        serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("phase"),
                SoundSource.HOSTILE, 0.9F, 1.5F);
    }

    /**
     * 这个实体算不算「自己人」：本体、本体分出来的幻影、本体收着的聚合体、本体的骷髅头。
     *
     * <p>二阶段的 36 连发骷髅雨、虚无反噬的 5 格爆炸、虚无射线都是一路横扫的，
     * 幻影挡在弹道上会被自己的攻击打成重伤（幻影挨一下就会炸开），所以所有友伤入口
     * 都走这一个判断。</p>
     */
    public boolean isOwnSummon(Entity entity) {
        if (entity == null) return false;
        if (entity == this) return true;
        if (entity instanceof CorruptedWitherPhantom phantom) return phantom.isOwnedBy(this);
        if (entity instanceof CorruptedWitherAggregate aggregate) return aggregate.isOwnedBy(this);
        if (entity instanceof CorruptedWitherSkull skull) return skull.getOwner() == this;
        return false;
    }

    public int phantomCount() {
        if (!(level() instanceof ServerLevel serverLevel)) return 0;
        return serverLevel.getEntitiesOfClass(CorruptedWitherPhantom.class, getBoundingBox().inflate(32.0D),
                phantom -> phantom.isOwnedBy(this)).size();
    }

    // ---------------------------------------------------------------- 阶段 / 资源 / 地形
    private void tickPhase() {
        if (isPhaseTwo() || getHealth() > getMaxHealth() * PHASE2_TRIGGER_RATIO) return;
        this.entityData.set(DATA_PHASE_TWO, Boolean.TRUE);
        applyPhaseAttributes();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("phase"),
                    SoundSource.HOSTILE, 3.0F, 0.8F);
            for (ServerPlayer player : serverLevel.getPlayers(player -> player.distanceToSqr(this) < 96.0D * 96.0D)) {
                player.displayClientMessage(Component.translatable("message.dimensional_mana.corrupted_wither.phase2"), false);
            }
        }
    }

    private void applyPhaseAttributes() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double multiplier = isPhaseTwo() ? PHASE2_SPEED_MULTIPLIER : PHASE1_SPEED_MULTIPLIER;
        speed.setBaseValue(VANILLA_WITHER_SPEED * multiplier);
    }

    private void tickRegen() {
        if (tickCount % 20 != 0) return;
        double perSecond = isPhaseTwo() ? PHASE2_REGEN_PER_SECOND : PHASE1_REGEN_PER_SECOND;
        if (ManaData.get(this).mana() < MAX_MANA) ManaData.restoreMana(this, perSecond, false);
        int qi = ManaData.get(this).battleQi();
        if (qi < MAX_QI) ManaData.setBattleQi(this, (int)Math.min(MAX_QI, qi + perSecond));
        this.regenAccumulator = 0;
    }

    /**
     * 飞行途中接触到的方块直接粉碎（基岩硬度 -1，天然排除）。
     *
     * <p>一阶段也生效，只是频率低一点、判定范围小一点；二阶段恢复成原来的「贴脸就碎」。
     * 这样被墙卡住时它会自己啃开一条路，而不是顶着墙原地抖，移动看起来才自然。</p>
     */
    private void tickTerrainCrush() {
        int interval = isPhaseTwo() ? 2 : 4;
        if (++blockBreakTimer < interval || !(level() instanceof ServerLevel serverLevel)) return;
        blockBreakTimer = 0;
        if (!serverLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) return;
        AABB box = getBoundingBox().inflate(isPhaseTwo() ? 0.6D : 0.35D);
        int minX = (int)Math.floor(box.minX);
        int maxX = (int)Math.floor(box.maxX);
        int minY = (int)Math.floor(box.minY);
        int maxY = (int)Math.floor(box.maxY);
        int minZ = (int)Math.floor(box.minZ);
        int maxZ = (int)Math.floor(box.maxZ);
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getFluidState().isSource()) continue;
                    if (state.getDestroySpeed(serverLevel, pos) < 0.0F) continue;
                    // 黑曜石、远古残骸这类高抗爆方块留下，玩家还能靠它们挡一挡。
                    if (state.getBlock().getExplosionResistance() > 50.0F) continue;
                    if (state.getBlock() instanceof CorruptedWitherSkullBlock) continue;
                    serverLevel.destroyBlock(pos.immutable(), false);
                }
            }
        }
    }

    /** 腐化魔素雨：每 10 秒给露天、没防护的玩家叠一层魔蚀。 */
    private void tickRain() {
        if (rainTicks > 0) rainTicks--;
        if (rainTicks <= 0 || tickCount % RAIN_STACK_INTERVAL != 0) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer player : serverLevel.getPlayers(player -> player.distanceToSqr(this) < RAIN_RANGE * RAIN_RANGE)) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (!serverLevel.canSeeSky(player.blockPosition())) continue;
            if (CorruptedWitherEvents.protectedFromCorruptionRain(player)) continue;
            // 和织网者同一套叠加：淋一次雨升一级。
            MagicErosionEffect.apply(player, EROSION_STATUS_TICKS);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.corrupted_wither.rain"), true);
        }
    }

    // ---------------------------------------------------------------- 屏幕特效同步
    private void syncScreenFx() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        float charge = 0.0F;
        if (rayTicks > 0) {
            int chargeTicks = RAY_TOTAL_TICKS - rayTicks;
            charge = Math.min(1.0F, chargeTicks / 140.0F);
        } else if (volleyCharge > 0) {
            charge = 1.0F - volleyCharge / (float)VOLLEY_CHARGE_TICKS;
        } else if (barrageTicks > 0) {
            // 骷髅雨：画面常驻一层中等强度的暗角 + 轻微抖动。
            charge = 0.55F;
        }
        float shake = charge * 0.85F + (rayTicks > 0 && rayTicks <= 200 ? 0.35F : 0.0F);
        CorruptedWitherFxPayload payload = new CorruptedWitherFxPayload(charge, shake, rainTicks > 0);
        for (ServerPlayer player : serverLevel.getPlayers(player -> player.distanceToSqr(this) < 96.0D * 96.0D)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // ---------------------------------------------------------------- Boss 血条 / 生命周期
    private void tickBossBar() {
        this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F, getHealth() / getMaxHealth())));
        this.bossEvent.setColor(isPhaseTwo() ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.PURPLE);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        // 故意不调用 super：原版那条私有血条不要显示，只留我们自己这条紫色血条。
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        this.bossEvent.removePlayer(player);
    }

    private void tickSummons() {
        if (tickCount % 20 != 0) return;
        // 魔素耗尽：按设计稿退化回普通凋零。
        if (tickCount > 200 && ManaData.get(this).mana() <= 0.5D && !isRemoved()) decayIntoVanillaWither();
    }

    private void decayIntoVanillaWither() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        WitherBoss wither = EntityType.WITHER.create(serverLevel);
        if (wither == null) return;
        wither.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
        wither.setHealth(Math.max(1.0F, Math.min(wither.getMaxHealth(), getHealth() * 0.5F)));
        serverLevel.addFreshEntity(wither);
        serverLevel.playSound(null, getX(), getY(), getZ(), CorruptedWitherSounds.get("death"),
                SoundSource.HOSTILE, 2.0F, 0.8F);
        this.discard();
    }

    // ---------------------------------------------------------------- 专属声线
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

    /** 体型大，声音传得远一点。 */
    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.bossEvent.removeAllPlayers();
        this.bossEvent.setVisible(false);
        if (level() instanceof ServerLevel serverLevel) {
            for (CorruptedWitherAggregate aggregate : ownedAggregates()) aggregate.discard();
            serverLevel.getEntitiesOfClass(CorruptedWitherPhantom.class, getBoundingBox().inflate(48.0D),
                    phantom -> phantom.isOwnedBy(this)).forEach(Entity::discard);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    /** 死亡掉落按设计稿走战利品表；这里只在被玩家击杀时补一份经验。 */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (recentlyHit) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5D, getZ(), 2,
                    0.4D, 0.6D, 0.4D, 0.0D);
        }
    }

    /** 蓄力 / 射击时给客户端的射线目标（渲染不做，仅调试与瞄准保持用）。 */
    public UUID rayTargetId() {
        return rayTargetId;
    }
}
