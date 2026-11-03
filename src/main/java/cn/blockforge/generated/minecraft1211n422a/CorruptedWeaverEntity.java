package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 魔蚀织网者（CorruptedWeaver）：被魔素泡透、织出魔晶蛛网的变异蜘蛛。
 *
 * <p>它继承原版 {@link Spider}，但打法全换成了 {@link CorruptedWeaverCombatGoal} 里的一条
 * 状态机：<b>一个 Goal 全程握着移动权，按距离挑招，永远有一条「先压上去」的兜底路</b>。
 * 以前拆成远程 / 空袭 / 近战三个独立 Goal，各自只管一段距离，4~8 格和「远程在冷却」的时候
 * 谁都不管，看起来就是站在原地发愣——现在这些空档都被兜底路堵上了。</p>
 *
 * <ul>
 *   <li>＞16 格：花 100 魔素吐蛛丝球封走位，立刻前压；</li>
 *   <li>8~16 格且在地上：花 80 魔素高跳，在空中喷一条 32 格的毒液射线，落地顺势贴身；</li>
 *   <li>＜3 格：毒牙噬咬（2 物理 + 6 奥术 + 2 毒 + 魔蚀），后跳吐网，
 *       <b>20 刻内回扑接二段噬咬</b>（伤害 ×1.5、魔蚀升一级）——这是它的连招；</li>
 *   <li>目标玩家举着招架：{@link CorruptedWeaverParryAvoidGoal} 花 200 魔素强制后跳、
 *       反手一记蛛丝球，绝不上嘴；</li>
 *   <li>血量 &lt; 30%（或刚吃过光 / 火）：{@link CorruptedWeaverRetreatGoal} 回自己织的网里，
 *       附近没有网就<b>就地自己织一张</b>蹲进去，贴着网每 2 刻回 1 血、2 魔素。</li>
 * </ul>
 *
 * <p>以上所有战术对<b>任何目标</b>都生效（玩家、铁傀儡、任何还手的生物），只有「招架识破」是玩家专属。
 * 自然站桩的另一半原因是目标选择：原版蜘蛛本来就会主动猎杀铁傀儡，我们之前把这套目标 Goal 一起删了，
 * 现在补回来了。</p>
 *
 * <p>性能上的硬约束：距离与状态每 10 刻才刷新一次（{@link #TACTIC_INTERVAL}），
 * 每只织网者最多同时留 5 张网，死亡时由 {@link CorruptedWeaverEvents} 清干净自己的网与弹射物。</p>
 *
 * <p>抗性 / 易伤写在 {@link CorruptedWeaverEvents#onIncomingDamage}：奥术 −35%、音 −65%、
 * 光 +35%、火 +65%，不做任何免疫。</p>
 */
public final class CorruptedWeaverEntity extends Spider {
    // ---------------------------------------------------------------- 设计稿数值
    /** 魔素池上限。 */
    public static final double MANA_POOL = 5000.0D;
    /** 远程拦截消耗。 */
    public static final float SNIPER_COST = 100.0F;
    /** 空中轰炸消耗。 */
    public static final float BOMBARD_COST = 80.0F;
    /** 招架识破消耗。 */
    public static final float PARRY_COST = 200.0F;

    /** 超过这个距离就放蛛丝球。 */
    public static final double SNIPER_RANGE = 16.0D;
    /** 空中轰炸的距离下限 / 上限。 */
    public static final double BOMBARD_MIN_RANGE = 8.0D;
    public static final double BOMBARD_MAX_RANGE = 16.0D;
    /** 近战游击的触发距离。 */
    public static final double MELEE_RANGE = 4.0D;
    /** 招架识破的触发距离。 */
    public static final double PARRY_RANGE = 5.0D;
    /** 毒液射线最长 32 格。 */
    public static final double VENOM_RANGE = 32.0D;
    /** 毒液射线命中伤害。 */
    public static final float VENOM_DAMAGE = 8.0F;
    /** 毒牙噬咬的三段伤害。 */
    public static final float BITE_PHYSICAL = 2.0F;
    public static final float BITE_ARCANE = 6.0F;
    public static final float BITE_POISON = 2.0F;
    /** 噬咬附加的魔蚀时长：10 秒。 */
    public static final int BITE_EROSION_TICKS = 200;

    /** 各战术的冷却（刻）。 */
    public static final int SNIPER_COOLDOWN = 60;
    public static final int BOMBARD_COOLDOWN = 120;
    public static final int PARRY_COOLDOWN = 100;

    /** 连招：起手咬完之后，多久之内补上第二口就算「二段」（连招窗口）。 */
    public static final int CHAIN_WINDOW = 70;
    /** 连招：第一口与第二口之间的最短间隔（后跳落地 + 回扑的时间）。 */
    public static final int CHAIN_FIRST_GAP = 20;
    /** 连招：整套打完之后的歇口，避免一直粘着咬（二段有 1.5 倍加成，歇口要补回来）。 */
    public static final int CHAIN_REST = 70;
    /** 二段噬咬的伤害倍率。 */
    public static final float CHAIN_FINISHER_MULTIPLIER = 1.5F;

    /** 不在网里时的被动回蓝：每秒 6 点，免得魔素打空之后永远打不出招。 */
    public static final double PASSIVE_MANA_PER_SECOND = 6.0D;
    /** 贴着网每 2 刻回多少魔素（r252：按「接触」结算，节奏从每秒 50 改成每 2 刻 2）。 */
    public static final double WEB_MANA_PER_2_TICKS = 2.0D;
    /** 贴着网每 2 刻回多少血（r252：每秒 2 → 每 2 刻 1，回血能力比原来强 5 倍）。 */
    public static final float WEB_HEAL_PER_2_TICKS = 1.0F;
    /** 网内回血回蓝的结算间隔：2 刻。 */
    public static final int WEB_REGEN_INTERVAL = 2;

    /** 血量低于 30% 触发撤退。 */
    public static final double RETREAT_TRIGGER_RATIO = 0.30D;
    /** 血量回到 65% 才结束撤退。 */
    public static final double RETREAT_EXIT_RATIO = 0.65D;
    /** 撤退最多撑 15 秒，免得它永远躲着不出来。 */
    public static final int RETREAT_MAX_TICKS = 300;
    /** 被光 / 火打中后的回避时长上限：2 秒，只够跳开吐张网，不是躲 10 秒不出来。 */
    public static final int LIGHT_FIRE_DODGE_TICKS = 40;
    /** 挨了光 / 火伤害后 10 秒内都记着这笔（但只回避 2 秒，见上）。 */
    public static final int LIGHT_FIRE_MEMORY_TICKS = 200;
    /** 战术状态刷新间隔：10 刻。 */
    public static final int TACTIC_INTERVAL = 10;

    /** 魔晶紫尘：身上时不时飘出来的碎晶。 */
    static final ParticleOptions CRYSTAL_DUST =
            new DustParticleOptions(new Vector3f(0.66F, 0.34F, 0.92F), 1.1F);
    /** 毒液射线的荧光绿。 */
    private static final ParticleOptions VENOM_DUST =
            new DustParticleOptions(new Vector3f(0.42F, 0.86F, 0.30F), 1.2F);

    private static final String WEBS_TAG = "WeaverWebs";

    /** 空中轰炸的起跳标记：同步到客户端，渲染器据此做后仰动作。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> BOMB_JUMPING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CorruptedWeaverEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    /** 每 10 刻刷新的目标距离缓存。 */
    private double cachedTargetDistance = Double.MAX_VALUE;
    /** 最近一次吃光 / 火伤害后，到什么时候还想着撤退（gameTime）。 */
    private long lightFireHurtUntil;
    /** 四种战术的下一次可用时刻（gameTime）。 */
    private long sniperReadyAt;
    private long bombardReadyAt;
    private long hitRunReadyAt;
    private long parryReadyAt;
    /** 撤退中。 */
    private int retreatTicks;
    /** 连招：当前这一轮已经咬了几口（0 = 起手，1 = 等着补二段）。 */
    private int chainBites;
    /** 连招窗口的截止时刻（gameTime）；过了这个点就得从头起手。 */
    private long chainExpireAt;
    /** 自己吐出来的网（服务端记账，最多 5 个）。 */
    private final List<BlockPos> webs = new ArrayList<>();
    /** 自己射出去的蛛丝球（服务端记账，死亡时统一清掉）。 */
    private final List<java.util.UUID> shots = new ArrayList<>();

    public CorruptedWeaverEntity(EntityType<? extends CorruptedWeaverEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    // ---------------------------------------------------------------- 属性
    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                // 原版蜘蛛 0.3 的 1.5 倍。
                .add(Attributes.MOVEMENT_SPEED, 0.45D)
                // 起跳速度是原版的 √3 倍：跳跃高度与速度平方成正比，正好是 3 倍高。
                .add(Attributes.JUMP_STRENGTH, 0.42D * Math.sqrt(3.0D))
                // 32 格的索敌距离 + 一点余量。
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D);
    }

    @Override
    protected void registerGoals() {
        // 原版蜘蛛的近战 / 目标 Goal 全部清掉，换成「按距离换战术 + 连招」的一套。
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // 优先级数字越小越先：识破招架 > 撤退 > 主战斗（连招状态机） > 闲逛。
        this.goalSelector.addGoal(2, new CorruptedWeaverParryAvoidGoal(this));
        this.goalSelector.addGoal(3, new CorruptedWeaverRetreatGoal(this));
        this.goalSelector.addGoal(4, new CorruptedWeaverCombatGoal(this));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // 会看「走不到」黑名单的版本（见 MobPathing）。
        this.targetSelector.addGoal(2, new PathAwareTargetGoal<>(this, Player.class, true));
        // 原版蜘蛛本来就主动猎杀铁傀儡（SpiderTargetGoal），之前被 removeAllGoals 一起删掉了，
        // 结果它被铁傀儡打只能靠「挨打反击」，追不上就站着挨揍。这里按原版的顺序补回来。
        this.targetSelector.addGoal(3, new PathAwareTargetGoal<>(this, net.minecraft.world.entity.animal.IronGolem.class, true));
    }

    // ---------------------------------------------------------------- 生成与存档
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        ManaData.setMobPools(this, MANA_POOL, 0);
        return result;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOMB_JUMPING, Boolean.FALSE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("WeaverSniperReady", sniperReadyAt);
        tag.putLong("WeaverBombardReady", bombardReadyAt);
        tag.putLong("WeaverHitRunReady", hitRunReadyAt);
        tag.putLong("WeaverParryReady", parryReadyAt);
        tag.putLong("WeaverLightFireUntil", lightFireHurtUntil);
        tag.putInt("WeaverRetreatTicks", retreatTicks);
        long[] positions = new long[webs.size()];
        for (int i = 0; i < webs.size(); i++) positions[i] = webs.get(i).asLong();
        tag.putLongArray(WEBS_TAG, positions);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sniperReadyAt = tag.getLong("WeaverSniperReady");
        bombardReadyAt = tag.getLong("WeaverBombardReady");
        hitRunReadyAt = tag.getLong("WeaverHitRunReady");
        parryReadyAt = tag.getLong("WeaverParryReady");
        lightFireHurtUntil = tag.getLong("WeaverLightFireUntil");
        retreatTicks = tag.getInt("WeaverRetreatTicks");
        webs.clear();
        for (long packed : tag.getLongArray(WEBS_TAG)) webs.add(BlockPos.of(packed));
    }

    // ---------------------------------------------------------------- 战术状态（每 10 刻刷新）
    /** 目标距离缓存值；没有目标时是 {@link Double#MAX_VALUE}。 */
    public double targetDistance() {
        return cachedTargetDistance;
    }

    /** 目标在 [{@code min}, {@code max}] 格以内。 */
    public boolean targetWithin(double min, double max) {
        return cachedTargetDistance >= min && cachedTargetDistance <= max;
    }

    /** 目标在 {@code range} 格以内。 */
    public boolean targetCloserThan(double range) {
        return cachedTargetDistance <= range;
    }

    /** 目标在 {@code range} 格以外。 */
    public boolean targetFartherThan(double range) {
        return cachedTargetDistance > range;
    }

    private void refreshTactics() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || target.level() != level()) {
            cachedTargetDistance = Double.MAX_VALUE;
        } else {
            cachedTargetDistance = Math.sqrt(distanceToSqr(target));
        }
        // 连招窗口过了就作废，下一口重新按起手算。
        if (chainBites != 0 && level().getGameTime() > chainExpireAt) chainBites = 0;
        // 顺手清掉已经消失的网，别让记账列表越攒越长。
        Iterator<BlockPos> it = webs.iterator();
        while (it.hasNext()) {
            if (!(level().getBlockState(it.next()).getBlock() instanceof CorruptedWebBlock)) it.remove();
        }
        shots.removeIf(id -> !(level() instanceof ServerLevel serverLevel) || serverLevel.getEntity(id) == null);
    }

    /** 某招是不是冷却好了（{@code readyAt} 存的是 gameTime）。 */
    public boolean ready(long readyAt) {
        return level().getGameTime() >= readyAt;
    }

    public long sniperReadyAt() { return sniperReadyAt; }
    public long bombardReadyAt() { return bombardReadyAt; }
    public long hitRunReadyAt() { return hitRunReadyAt; }
    public long parryReadyAt() { return parryReadyAt; }

    public void cooldownSniper() { sniperReadyAt = level().getGameTime() + SNIPER_COOLDOWN; }
    public void cooldownBombard() { bombardReadyAt = level().getGameTime() + BOMBARD_COOLDOWN; }
    public void cooldownHitRun(int ticks) { hitRunReadyAt = level().getGameTime() + ticks; }
    public void cooldownParry() { parryReadyAt = level().getGameTime() + PARRY_COOLDOWN; }

    // ---------------------------------------------------------------- 连招记账
    /** 连招还没断：上一口咬完在窗口内，下一口算「二段」。 */
    public boolean chainPending() {
        return chainBites > 0 && level().getGameTime() <= chainExpireAt;
    }

    /** 这一口是不是连招的二段（伤害更高、魔蚀更重）。 */
    public boolean chainFinisherReady() {
        return chainPending();
    }

    public int chainBites() { return chainBites; }

    /** 魔素够不够。 */
    public boolean hasMana(double amount) {
        return ManaData.get(this).mana() >= amount;
    }

    /** 花魔素；不够就返回 false（这一招这次不放）。 */
    public boolean spend(double amount) {
        return ManaData.spendMana(this, amount);
    }

    // ---------------------------------------------------------------- 撤退
    /**
     * 该不该躲起来回血。
     *
     * <p>两种来源，时长完全不同：</p>
     * <ul>
     *   <li>血量 ≤ 30%：真撤退，最多撑 {@link #RETREAT_MAX_TICKS}（15 秒）；</li>
     *   <li>吃过光 / 火：只是「跳开吐张网」的短回避，最多 {@link #LIGHT_FIRE_DODGE_TICKS}
     *       （2 秒）。以前这里直接跟 10 秒的记忆走，玩家拿火焰武器点它一下，它就在网里杵十秒不动，
     *       看起来就是「呆住」——这就是那次卡死的真凶。</li>
     * </ul>
     */
    public boolean wantsToRetreat() {
        if (getHealth() <= getMaxHealth() * RETREAT_TRIGGER_RATIO) return true;
        return level().getGameTime() < lightFireHurtUntil && retreatTicks < LIGHT_FIRE_DODGE_TICKS;
    }

    /** 被光 / 火属性打中：记一笔，接下来 10 秒都想撤退。 */
    public void markLightOrFireHurt() {
        lightFireHurtUntil = level().getGameTime() + LIGHT_FIRE_MEMORY_TICKS;
    }

    public boolean retreatRecovered() {
        return getHealth() >= getMaxHealth() * RETREAT_EXIT_RATIO && !wantsToRetreat();
    }

    public int retreatTicks() { return retreatTicks; }
    public void tickRetreat() { retreatTicks++; }
    public void resetRetreat() { retreatTicks = 0; }

    /** 自己吐出来的网的位置（撤退时回网里蹲着用）。 */
    public List<BlockPos> knownWebs() {
        return webs;
    }

    /**
     * 是不是贴着（自己的或别的）魔蚀蛛网。
     *
     * <p>r252 修 bug：以前只采样脚下正中心那一格，两种很常见的情况会漏判——
     * ① 1.4 格宽的大蜘蛛导航停在网块边上时，脚下中心点还落在隔壁格；
     * ② 跳跃 / 后空中吐的网浮在半空，它落回地面后网在头顶格，永远「站不进去」。
     * 现在改成扫整个碰撞箱相交的格子，头顶再多让半格，身体挨到网就算接触。</p>
     */
    public boolean standingInWeb() {
        AABB box = getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY - 0.001D), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY + 0.5D), Mth.floor(box.maxZ))) {
            if (level().getBlockState(pos).getBlock() instanceof CorruptedWebBlock) return true;
        }
        return false;
    }

    /**
     * 找一面最近的、朝得上去的墙：从 2 格扫到 5 格，挑「旁边是实心方块、自己上方两格空着」
     * 的落点。蜘蛛贴墙就会自动往上爬，所以只要导航过去就行。
     */
    public Vec3 findClimbPoint() {
        LivingEntity target = getTarget();
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos base = blockPosition();
        for (int radius = 2; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos ground = base.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos candidate = ground.offset(0, dy, 0);
                        if (!level().getBlockState(candidate.below()).blocksMotion()) continue;
                        if (!level().isEmptyBlock(candidate) || !level().isEmptyBlock(candidate.above())) continue;
                        // 靠墙：四邻至少有一个实心方块，蜘蛛才爬得上去。
                        boolean againstWall = false;
                        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            if (level().getBlockState(candidate.relative(direction)).blocksMotion()) {
                                againstWall = true;
                                break;
                            }
                        }
                        if (!againstWall) continue;
                        double score = distanceToSqr(candidate.getX() + 0.5D, candidate.getY() + 0.5D, candidate.getZ() + 0.5D);
                        // 离目标远一点更安全，但别为了找墙跑太远。
                        if (target != null) {
                            score -= target.distanceToSqr(candidate.getX() + 0.5D, candidate.getY() + 0.5D, candidate.getZ() + 0.5D) * 0.15D;
                        }
                        if (score < bestScore) {
                            bestScore = score;
                            best = new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                        }
                    }
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 蛛丝球
    /** 花 100 魔素吐一发蛛丝球，抛物线飞向目标。 */
    public void shootWeb(LivingEntity target) {
        if (level().isClientSide || target == null) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 from = new Vec3(getX(), getEyeY() - 0.05D, getZ());
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 delta = to.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double time = Math.max(1.0D, horizontal / WebProjectileEntity.SPEED);
        // 抛物线补偿：多抬 0.5·g·t²，剩下的交给弹体的重力。
        Vec3 velocity = new Vec3(delta.x / time,
                delta.y / time + 0.5D * WebProjectileEntity.GRAVITY * time,
                delta.z / time);
        if (velocity.lengthSqr() < 1.0E-6) return;
        WebProjectileEntity web = GeneratedMod.WEB_PROJECTILE.get().create(serverLevel);
        if (web == null) return;
        web.setPos(from.x, from.y, from.z);
        web.launch(this, velocity, WebProjectileEntity.SPEED);
        serverLevel.addFreshEntity(web);
        shots.add(web.getUUID());
        serverLevel.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_WEAVER_WEB.get(), SoundSource.HOSTILE, 1.0F, 1.1F);
        serverLevel.sendParticles(CRYSTAL_DUST, from.x, from.y, from.z, 6, 0.15D, 0.15D, 0.15D, 0.02D);
    }

    // ---------------------------------------------------------------- 毒液射线
    /**
     * 毒液喷射：一条 32 格射线，遇到方块直接截断，对沿线所有生物造成 8 点毒伤并附加魔蚀。
     *
     * <p>全程只用 {@link Level#clip} 做射线检测 + 一次范围检索，不生成任何小实体。</p>
     */
    public void venomSpray(LivingEntity aimAt) {
        if (level().isClientSide || !(level() instanceof ServerLevel serverLevel)) return;
        Vec3 from = new Vec3(getX(), getEyeY() - 0.1D, getZ());
        Vec3 direction;
        if (aimAt != null && aimAt.isAlive()) {
            direction = aimAt.getBoundingBox().getCenter().subtract(from).normalize();
        } else {
            direction = getLookAngle().normalize();
        }
        if (direction.lengthSqr() < 1.0E-6) return;
        Vec3 end = from.add(direction.scale(VENOM_RANGE));
        BlockHitResult blockHit = serverLevel.clip(new ClipContext(from, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(from, stop).inflate(0.6D), candidate -> validVenomTarget(candidate))) {
            if (victim.getBoundingBox().inflate(0.3D).clip(from, stop).isEmpty()) continue;
            // 8 点毒伤 + 魔蚀；毒元素走无视受击冷却的法术伤害类型，射线扫过时不会互相吞伤害。
            SpellDamage.hitElement(serverLevel, this, victim, ElementType.POISON.id(), VENOM_DAMAGE);
            MagicErosionEffect.apply(victim, MagicErosionEffect.DEFAULT_DURATION);
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0, false, true));
        }

        // 射线本体：一串绿色毒雾粒子 + 落点一片毒液飞溅。
        int steps = Math.max(2, (int)(from.distanceTo(stop) * 3.0D));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.lerp(stop, i / (double)steps);
            serverLevel.sendParticles(VENOM_DUST, point.x, point.y, point.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        serverLevel.sendParticles(ParticleTypes.WITCH, stop.x, stop.y, stop.z, 12, 0.25D, 0.25D, 0.25D, 0.02D);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_WEAVER_VENOM.get(), SoundSource.HOSTILE, 1.2F, 0.9F);
    }

    private boolean validVenomTarget(LivingEntity candidate) {
        return candidate.isAlive() && !candidate.isSpectator() && candidate != this
                && !(candidate instanceof CorruptedWeaverEntity)
                && !(candidate instanceof net.minecraft.world.entity.player.Player player && player.isCreative());
    }

    // ---------------------------------------------------------------- 毒牙噬咬
    /**
     * 毒牙噬咬：2 点物理 + 6 点奥术 + 2 点毒，附加 10 秒魔蚀，然后立刻后跳、在脚下吐网。
     *
     * <p><b>连招规则：</b>上一口咬完在 {@link #CHAIN_WINDOW} 之内补上的第二口算「二段」——
     * 三段伤害全部 ×{@link #CHAIN_FINISHER_MULTIPLIER}，魔蚀持续时间拉长到 1.5 倍（等级本来就会
     * 自动升一级），中毒也升到 II。二段之后清空连招、进入 {@link #CHAIN_REST} 的歇口。
     * 这个放大只作用在「同一轮连招」里，不存在无限叠。</p>
     *
     * <p>如果目标玩家正举着招架，这一口不下嘴 —— 直接改走识破流程（和
     * {@link CorruptedWeaverParryAvoidGoal} 同一套动作），免得被弹刀。</p>
     */
    public void bite(LivingEntity target) {
        if (level().isClientSide || target == null || !target.isAlive()) return;
        if (inParryStance(target) && hasMana(PARRY_COST)) {
            parryAvoid(target);
            return;
        }
        boolean finisher = chainFinisherReady();
        float scale = finisher ? CHAIN_FINISHER_MULTIPLIER : 1.0F;
        target.hurt(level().damageSources().mobAttack(this), BITE_PHYSICAL * scale);
        if (level() instanceof ServerLevel serverLevel) {
            SpellDamage.hitElement(serverLevel, this, target, MagicEquipment.ARCANE, BITE_ARCANE * scale);
            SpellDamage.hitElement(serverLevel, this, target, ElementType.POISON.id(), BITE_POISON * scale);
        }
        MagicErosionEffect.apply(target, finisher ? BITE_EROSION_TICKS * 3 / 2 : BITE_EROSION_TICKS);
        target.addEffect(new MobEffectInstance(MobEffects.POISON, finisher ? 160 : 100, finisher ? 1 : 0, false, true));
        // 咬完立刻后跳，二段跳得更远一点，看起来更有「收招」的感觉。
        backJump(target, finisher ? 0.64D : 0.52D);
        placeWebHere();
        // 连招记账：二段之后重置，起手则打开窗口等回扑。
        chainBites = finisher ? 0 : 1;
        chainExpireAt = level().getGameTime() + CHAIN_WINDOW;
        cooldownHitRun(finisher ? CHAIN_REST : CHAIN_FIRST_GAP);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    GeneratedMod.CORRUPTED_WEAVER_BITE.get(), SoundSource.HOSTILE,
                    finisher ? 1.45F : 1.2F, finisher ? 0.72F : 0.95F);
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    finisher ? 20 : 10, 0.3D, 0.3D, 0.3D, 0.02D);
            if (finisher) {
                // 二段收招：一圈向外炸开的魔晶紫尘，给玩家一个「这一套打完了」的信号。
                Vec3 center = getBoundingBox().getCenter();
                serverLevel.sendParticles(CRYSTAL_DUST, center.x, center.y + 0.3D, center.z,
                        18, 0.6D, 0.4D, 0.6D, 0.06D);
            }
        }
    }

    /** 噬咬前摇的表现：紫尘往口器聚 + 一声压低的嘶响，玩家看得见它要下嘴了。 */
    public void playBiteWindup() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 mouth = new Vec3(getX(), getEyeY() - 0.2D, getZ()).add(getLookAngle().scale(0.55D));
        serverLevel.sendParticles(CRYSTAL_DUST, mouth.x, mouth.y, mouth.z, 7, 0.16D, 0.16D, 0.16D, 0.01D);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_WEAVER_AMBIENT.get(), SoundSource.HOSTILE, 0.7F, 1.7F);
    }

    /** 扑空：空咬一声，然后立刻收招，不会僵在原地。 */
    public void playBiteWhiff() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_WEAVER_WEB.get(), SoundSource.HOSTILE, 0.6F, 1.8F);
        Vec3 mouth = new Vec3(getX(), getEyeY() - 0.2D, getZ()).add(getLookAngle().scale(0.6D));
        serverLevel.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 4, 0.1D, 0.1D, 0.1D, 0.01D);
    }

    /** 目标玩家是不是正摆着招架姿态（本模组的斗气招架）。 */
    public boolean inParryStance(LivingEntity target) {
        return target instanceof ServerPlayer player && BattleQiParry.inStance(player);
    }

    // ---------------------------------------------------------------- 招架识破
    /** 取消攻击意图 → 强制后跳 → 反手一记蛛丝球。消耗 200 魔素。 */
    public void parryAvoid(LivingEntity target) {
        if (!spend(PARRY_COST)) return;
        shootWeb(target);
        backJump(target, 0.68D);
        placeWebHere();
        cooldownParry();
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 center = getBoundingBox().getCenter();
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z,
                    8, 0.3D, 0.3D, 0.3D, 0.02D);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    GeneratedMod.CORRUPTED_WEAVER_AMBIENT.get(), SoundSource.HOSTILE, 1.0F, 1.6F);
        }
    }

    // ---------------------------------------------------------------- 跳跃与吐网
    /** 背对目标后跳。 */
    public void backJump(LivingEntity target, double strength) {
        Vec3 away = position().subtract(target.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(0.0D, 0.0D, 1.0D);
        away = away.normalize().scale(strength);
        setDeltaMovement(away.x, 0.42D, away.z);
        hasImpulse = true;
        hurtMarked = true;
    }

    /**
     * 朝目标前扑：追不上、被围住、站在坑里上不去的时候用，比干站着等人走过来自然得多。
     *
     * <p>和 {@link #performBombJump()} 的区别是它带水平冲量：既跨得了沟，也能越过一格矮墙。</p>
     */
    public void leapToward(LivingEntity target) {
        if (level().isClientSide || target == null) return;
        Vec3 toward = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
        if (toward.lengthSqr() < 1.0E-4) return;
        toward = toward.normalize().scale(0.44D);
        setDeltaMovement(toward.x, 0.42D, toward.z);
        hasImpulse = true;
        hurtMarked = true;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    GeneratedMod.CORRUPTED_WEAVER_WEB.get(), SoundSource.HOSTILE, 0.8F, 1.45F);
            serverLevel.sendParticles(CRYSTAL_DUST, getX(), getY() + 0.1D, getZ(), 8, 0.25D, 0.05D, 0.25D, 0.03D);
        }
    }

    /** 空中轰炸的起跳：直接把起跳速度灌进垂直速度（JUMP_STRENGTH 是原版 √3 倍）。 */
    public void performBombJump() {
        double power = getJumpPower();
        setDeltaMovement(getDeltaMovement().x * 0.2D, power, getDeltaMovement().z * 0.2D);
        hasImpulse = true;
        hurtMarked = true;
        setBombJumping(true);
        getNavigation().stop();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    GeneratedMod.CORRUPTED_WEAVER_AMBIENT.get(), SoundSource.HOSTILE, 1.1F, 1.35F);
        }
    }

    /** 还在飞的蛛丝球 UUID（死亡清理用）。 */
    public List<java.util.UUID> knownShots() {
        return shots;
    }

    public boolean isBombJumping() {
        return entityData.get(BOMB_JUMPING);
    }

    private void setBombJumping(boolean jumping) {
        entityData.set(BOMB_JUMPING, jumping);
    }

    public void finishBombJump() {
        setBombJumping(false);
    }

    /**
     * 在脚下吐一张网。
     *
     * <p>r252：落点会往下沉到地面格——它跳很高去空中轰炸 / 前扑时正好咬一口，
     * 以前网会浮在半空，落回地面后再也「站不进」自己那张网，回血自然永远触发不了。</p>
     */
    public void placeWebHere() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = webAnchorPos();
        if (CorruptedWebBlock.place(serverLevel, pos, getUUID())) trackWeb(pos);
    }

    /** 吐网落点：从脚下这一格开始，最多往下探 8 格，沉到第一个「下面垫着东西」的格子。 */
    private BlockPos webAnchorPos() {
        BlockPos pos = blockPosition();
        for (int i = 0; i < 8 && level().isEmptyBlock(pos.below()); i++) pos = pos.below();
        return pos;
    }

    /** 记账：自己吐的网，最多留 {@link CorruptedWebBlock#MAX_PER_WEAVER} 张。 */
    private void trackWeb(BlockPos pos) {
        webs.add(pos.immutable());
        while (webs.size() > CorruptedWebBlock.MAX_PER_WEAVER) webs.remove(0);
    }

    /**
     * 撤退时附近找不到现成的网：<b>就地自己织一张</b>，蹲进去回血回蓝。
     *
     * <p>这就是「找不到窝就自己搭一个」的本能，省得它血量见底还在外面一路乱跑。
     * 两条硬限制，防止把世界铺满：</p>
     * <ul>
     *   <li>不能泡在水里 / 岩浆里织（下一刻就被溶掉）；</li>
     *   <li>数量上限直接沿用统一规则：每只织网者最多同时 5 张、30 秒后自动消散
     *       （{@link CorruptedWebBlock#MAX_PER_WEAVER}、{@link CorruptedWebBlock#LIFETIME_TICKS}），
     *       超出时 {@link CorruptedWebBlock#place} 会先抹掉最早那张。这里不再额外卡，
     *       免得「5 张旧网都在 24 格外」时反而织不出救命的那一张。</li>
     * </ul>
     *
     * @return 现在身上确实贴着网（新织的或本来就有的）就返回 true。
     */
    public boolean weaveRecoveryWeb() {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        if (standingInWeb()) return true;
        // 泡在水里 / 岩浆里织网，下一刻就被溶掉了，别白费。
        if (isInWater() || isInLava()) return false;
        placeWebHere();
        return standingInWeb();
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        if (tickCount % TACTIC_INTERVAL == 0) refreshTactics();
        if (isAlive() && tickCount % WEB_REGEN_INTERVAL == 0) {
            if (standingInWeb()) {
                // 贴着网：每 2 刻回 1 血、2 魔素（r252 从「每秒才结算一次」改成这个节奏，回血强了 5 倍）。
                if (getHealth() < getMaxHealth()) heal(WEB_HEAL_PER_2_TICKS);
                ManaData.restoreMana(this, WEB_MANA_PER_2_TICKS, false);
            } else if (tickCount % 20 == 0) {
                // 网外也慢慢回一点，免得魔素打空之后连蛛丝球都放不出、只能干挨打。
                ManaData.restoreMana(this, PASSIVE_MANA_PER_SECOND, false);
            }
        }
        if (isBombJumping() && onGround() && tickCount > 5) setBombJumping(false);
    }

    /**
     * 免疫摔落伤害。
     *
     * <p>织网者本来就会主动「冲天一跳」（8~16 格时高跳喷毒），以前跳上矿柱顶再掉下来
     * 会摔掉小半管血，看起来像是被地形卡着扣血。它又没有翅膀，跳得高是技能，不该付摔落费。
     * 注意只免疫摔落：岩浆、窒息、火焰照常结算。</p>
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier,
                                   net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    /** 客户端：身上时不时冒一点魔晶紫尘，眼睛发紫光的感觉靠贴图。 */
    private void clientParticles() {
        if (random.nextInt(5) != 0) return;
        level().addParticle(CRYSTAL_DUST,
                getX() + random.triangle(0.0D, 0.35D),
                getY(0.25D + random.nextDouble() * 0.5D),
                getZ() + random.triangle(0.0D, 0.35D),
                0.0D, 0.01D, 0.0D);
    }

    // ---------------------------------------------------------------- 声音
    @Override
    protected SoundEvent getAmbientSound() {
        return GeneratedMod.CORRUPTED_WEAVER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return GeneratedMod.CORRUPTED_WEAVER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GeneratedMod.CORRUPTED_WEAVER_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.15F;
    }

    @Override
    public float getVoicePitch() {
        return 0.62F;
    }
}
