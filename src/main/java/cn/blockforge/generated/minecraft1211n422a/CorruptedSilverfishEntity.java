package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 秽蚀蠹（Corrupted Silverfish）：被魔晶粉尘泡透的蠹虫，身上挂着一点自己不灭的魂火。
 *
 * <p><b>四阶蜕皮</b>：它不是打死一次就死的怪。前三次致命伤会被【死亡蜕皮】顶掉——原地炸开
 * 一层蜕皮碎屑 + 魂火，体型缩一圈、血量回满到下一阶，然后继续咬人。第四次才真的死。
 * 每一阶的伤害都写在 {@link #doHurtTarget} 里：物理 + 奥术 + 毒，并附带 2 秒剧毒。</p>
 *
 * <p><b>钻地</b>：织网者的虫群呼救（{@link HiveMind}）叫到它时，它会从地下以每秒十余格的速度
 * 直接钻到目标身边再破土而出（{@link #beginDigRush}）；挖魔晶矿把它掀出来、以及刷怪笼里
 * 生出来的时候也是同一个「钻出来」的表现。</p>
 *
 * <p><b>荒原上的另一面</b>：它也被登记成「动物类」，会在蚀魔荒原地表少量自然生成，
 * 主动去啃长得比一格还高的卢梅莉亚（{@link CorruptedSilverfishEatGoal}，啃完 30 秒冷却）。
 * 高草、藤蔓、卢梅莉亚这类不是实心方块的植物它能顺着往上爬——这是原版蠹虫没有的本事。</p>
 *
 * <p><b>吃草能养伤（r92）</b>：卢梅莉亚本身是回血的魔法药草，蜕过皮的蠹虫（血上限已经掉到
 * 12 / 6 / 3）啃完一整株，上限会补回一阶的 24 点，并在 30 秒里一点点回满
 * （{@link #eatLumelia()}）。再蜕一次皮又打回原形——新长的皮没吃过草。</p>
 *
 * <p><b>脚下更利索（r92）</b>：台阶高度 0.6 → 1.0，荒原上那种一格高的坎寻路肯走了、也会自动迈上去；
 * 爬草时的「头顶还接着一段」探测修好了（以前 0.3 格高的身体把两次探测都落在同一格里，
 * 于是粘在一格高的草上上不去），另外爬到草尖还能顺着台沿翻上坎。</p>
 */
public final class CorruptedSilverfishEntity extends Silverfish {
    /** 一共四阶，前三次致命伤各蜕一层皮。 */
    public static final int MAX_STAGE = 4;
    /** 啃食一株卢梅莉亚之后的冷却：30 秒。 */
    public static final int EAT_COOLDOWN_TICKS = 600;
    /**
     * 吃完卢梅莉亚之后把身体补满要多久：30 秒（600 刻）。
     *
     * <p>和啃食冷却一样长——「吃完这一株，30 秒之后又是一条 24 血的完整虫」。</p>
     */
    public static final int LUMELIA_HEAL_TICKS = 600;
    /** 剧毒持续 2 秒。 */
    public static final int POISON_TICKS = 40;
    /** 钻地突进的时长上限 / 每刻位移（格）。 */
    public static final int DIG_RUSH_TICKS = 80;
    public static final double DIG_RUSH_SPEED = 0.55D;
    /** 每蜕一层皮体型缩 0.15；第三次之后只有原来的 0.55。 */
    public static final float SHRINK_PER_MOLT = 0.15F;
    /** 每次蜕皮 30% 掉一份魔晶粉；彻底死亡 50% 掉一份魂火残渣。 */
    public static final float MOLT_DROP_CHANCE = 0.30F;
    public static final float DEATH_DROP_CHANCE = 0.50F;

    /** 蜕皮的碎屑色（淡紫灰，像一层被撕下来的旧壳）。 */
    private static final DustParticleOptions MOLT_DUST =
            new DustParticleOptions(new Vector3f(0.72F, 0.64F, 0.86F), 1.05F);
    /** 魂火蓝。 */
    private static final DustParticleOptions SOUL_DUST =
            new DustParticleOptions(new Vector3f(0.34F, 0.78F, 0.98F), 1.15F);

    /** 当前阶数（1~4），同步到客户端给渲染器缩体型、也给玩家看。 */
    private static final EntityDataAccessor<Integer> DATA_STAGE =
            SynchedEntityData.defineId(CorruptedSilverfishEntity.class, EntityDataSerializers.INT);

    /** 已经从地下钻出来没有（钻地过程中不寻路、不结算伤害）。 */
    private static final EntityDataAccessor<Boolean> DATA_BURROWED =
            SynchedEntityData.defineId(CorruptedSilverfishEntity.class, EntityDataSerializers.BOOLEAN);

    /** 下次可以啃食的时刻（gameTime），写在 NBT 里。 */
    private long eatReadyAt;
    /** 钻地突进的剩余刻数 / 追赶的目标。 */
    private int digRushTicks;
    private UUID digRushVictim;
    /** 蜕皮那一瞬间的僵直（刻），防止一次伤害链里连蜕两层。 */
    private int moltLockTicks;
    /**
     * 吃过卢梅莉亚、身体已经修补回一阶的 24 点血上限。
     *
     * <p>四阶蜕皮的代价是血上限一阶比一阶低（24 / 12 / 6 / 3），而卢梅莉亚是回血的魔法药草：
     * 蜕过皮的蠹虫啃完一整株，会把上限补回 24 并一点点回满（{@link #eatLumelia()}）。
     * 再蜕一次皮就又回到那一阶的上限——新长的皮没吃过草。</p>
     */
    private boolean fed;
    /** 卢梅莉亚的恢复还要走几刻；0 = 没在恢复。 */
    private int healTicks;
    /**
     * 正在「停栖」啃卢梅莉亚（爬藤 / 悬停阶段）。停栖时竖直位置完全由
     * {@link CorruptedSilverfishEatGoal} 的速度控制接管，实体自身的 {@code climbPlants}
     * 让位，避免两套升力打架把它抖成抽搐。
     */
    private boolean perching;

    public CorruptedSilverfishEntity(EntityType<? extends CorruptedSilverfishEntity> type, Level level) {
        super(type, level);
        this.xpReward = 6;
        // 原版 MOVE_TO 每刻硬转最多 90°，蠹虫又是「身体=头部」的朝向模型，追击时就是
        // 原地左右甩。换成 35° 的平滑转向（r378 移动 AI 优化）。
        this.moveControl = new SmoothSilverfishMoveControl(this);
    }

    // ---------------------------------------------------------------- 属性
    /**
     * 初始 24 血、原版蠹虫 0.25 移速的 1.15 倍；后面每阶的属性由 {@link #applyStage} 覆盖。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, maxHealthForStage(1))
                .add(Attributes.MOVEMENT_SPEED, 0.2875D)
                .add(Attributes.ATTACK_DAMAGE, physicalForStage(1))
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                // r92：台阶高度 0.6 → 1.0。荒原全是断崖碎坡，0.6 的台阶高对这只小虫等于
                // 「一格高的坎永远上不去」：寻路连路都不给，玩家看到的是一只在坎下面打转的虫。
                // 抬到 1.0 之后，一格台阶对它是平地——寻路肯走，迈腿也会自动跨上去。
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    /** 各阶血量：24 / 12 / 6 / 3。 */
    public static float maxHealthForStage(int stage) {
        return switch (stage) {
            case 1 -> 24.0F;
            case 2 -> 12.0F;
            case 3 -> 6.0F;
            default -> 3.0F;
        };
    }

    /** 各阶物理伤害：2 / 1 / 0 / 0。 */
    public static float physicalForStage(int stage) {
        return switch (stage) {
            case 1 -> 2.0F;
            case 2 -> 1.0F;
            default -> 0.0F;
        };
    }

    /** 奥术伤害：四阶都是 2。 */
    public static float arcaneForStage(int stage) {
        return 2.0F;
    }

    /** 毒元素伤害：前两阶 1，三阶 1，四阶 0。 */
    public static float poisonDamageForStage(int stage) {
        return stage >= 4 ? 0.0F : 1.0F;
    }

    /** 附加剧毒的等级：三阶是剧毒 III、二阶 II、一阶 I，四阶没有毒。 */
    public static int poisonAmplifierForStage(int stage) {
        return switch (stage) {
            case 1 -> 2;   // 剧毒 III
            case 2 -> 1;   // 剧毒 II
            case 3 -> 0;   // 剧毒 I
            default -> -1; // 四阶无毒
        };
    }

    /** 体型缩放（渲染用）：每蜕一层皮缩 0.15。 */
    public float bodyScale() {
        return 1.0F - SHRINK_PER_MOLT * (stage() - 1);
    }

    /**
     * 荒原地表自然生成的判定：地面照原版怪物规则（踩实心方块、不看光照），
     * 再额外掷一次 60% 的骰子——荒原群系里只有它这一种 creature，不加这一道它会占满生物名额。
     * （r91：35% 太紧，玩家在荒原走半天碰不到一只，放宽到 60%。）
     */
    public static boolean checkSpawnRules(EntityType<? extends CorruptedSilverfishEntity> type,
                                          net.minecraft.world.level.LevelAccessor level, MobSpawnType spawnType,
                                          BlockPos pos, net.minecraft.util.RandomSource random) {
        if (spawnType == MobSpawnType.NATURAL && random.nextFloat() > 0.60F) return false;
        return Monster.checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected void registerGoals() {
        // 先让原版把 friendsGoal 这些字段建出来（它的 hurt() 会用到），再把整套 Goal 清掉换掉。
        super.registerGoals();
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // 啃卢梅莉亚排在近战之前（2 对 3）。听起来反了，其实是 r92 的修法：
        // 目标选择半径是 40 格，玩家站在荒原上看着它，它就永远有个「活人目标」；
        // 只要近战优先，它就一直在追人，看起来像「不吃草了」。现在啃食目标自己判断
        // 「8 格内有没有活人 / 是不是刚挨打」，有就让位给近战；远处的目标不再拦它吃草。
        this.goalSelector.addGoal(2, new CorruptedSilverfishEatGoal(this));
        // 咬人：移速已经在属性里，这里只要一路跟到底。
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new PathAwareTargetGoal<>(this, Player.class, true));
    }

    /**
     * 生成收尾：<b>把啃食冷却清零</b>。
     *
     * <p>r92：玩家观察到「一刷出来就是 30 秒冷却」，原因就是 {@code eatReadyAt} 这个普通字段
     * 在各条生成路径上并不总是从 0 开始——刷怪笼替换、虫群召唤（{@code beginDigRush} 会先写一个
     * 未来时刻）、白昼补刷、刷怪蛋，随便哪一条碰过它，落地的虫就先站三十秒才肯下嘴。
     * 生成流程的最后一步显式归零，谁也带不进冷却来。读档走的是 {@code readAdditionalSaveData}，
     * 不经过这里，所以存档里的冷却照常保留。</p>
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        eatReadyAt = 0L;
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    // ---------------------------------------------------------------- 同步数据 / 存档
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STAGE, 1);
        builder.define(DATA_BURROWED, Boolean.TRUE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CorruptedSilverfishStage", stage());
        tag.putLong("CorruptedSilverfishEatReady", eatReadyAt);
        tag.putBoolean("CorruptedSilverfishFed", fed);
        tag.putInt("CorruptedSilverfishHealTicks", healTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        eatReadyAt = tag.getLong("CorruptedSilverfishEatReady");
        fed = tag.getBoolean("CorruptedSilverfishFed");
        healTicks = tag.getInt("CorruptedSilverfishHealTicks");
        int saved = tag.getInt("CorruptedSilverfishStage");
        setStage(saved < 1 ? 1 : Math.min(saved, MAX_STAGE));
        // 先把阶与「吃过草」读回来，再按它们补齐上限：吃过草的蜕皮个体上限是 24。
        applyStageAttributes();
        // 读档时只把血量上限补齐，不回满血（否则每次重进存档都是一管满血）。
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    /** 阶数（1~4）。 */
    public int stage() {
        return entityData.get(DATA_STAGE);
    }

    private void setStage(int stage) {
        entityData.set(DATA_STAGE, stage);
    }

    /** 已经钻出来了（不是在土里赶路）。 */
    public boolean hasBurrowedOut() {
        return entityData.get(DATA_BURROWED);
    }

    private void setBurrowedOut(boolean out) {
        entityData.set(DATA_BURROWED, out);
    }

    /** 按当前阶把血量上限 / 移速 / 攻击力换过来。吃过卢梅莉亚的用一阶那 24 点上限。 */
    private void applyStageAttributes() {
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        float wantedMax = fed ? maxHealthForStage(1) : maxHealthForStage(stage());
        if (maxHealth != null && Math.abs(maxHealth.getBaseValue() - wantedMax) > 1.0E-4F) {
            maxHealth.setBaseValue(wantedMax);
        }
        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(physicalForStage(stage()));
    }

    // ---------------------------------------------------------------- 蜕皮
    /** 还能不能靠蜕皮顶掉这一次致命伤。 */
    public boolean canMolt() {
        return stage() < MAX_STAGE && moltLockTicks <= 0;
    }

    /**
     * 死亡蜕皮：免掉这一次致命伤，体型缩一圈、血量回满到下一阶，30% 掉一份魔晶粉。
     */
    public void molt(ServerLevel level) {
        if (!canMolt()) return;
        moltLockTicks = 10;
        // 蜕掉一层旧皮，草的修补也一并蜕掉：上限回到这一阶该有的小个子。
        fed = false;
        healTicks = 0;
        int next = stage() + 1;
        setStage(next);
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(maxHealthForStage(next));
        setHealth(getMaxHealth());
        applyStageAttributes();

        Vec3 center = getBoundingBox().getCenter();
        // 蜕皮碎屑 + 魂火：玩家一眼就知道「这一下没打死，它变小了」。
        level.sendParticles(MOLT_DUST, center.x, center.y, center.z,
                26, getBbWidth() * 0.6D, getBbHeight() * 0.5D, getBbWidth() * 0.6D, 0.06D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z,
                14, getBbWidth() * 0.45D, getBbHeight() * 0.45D, getBbWidth() * 0.45D, 0.03D);
        level.sendParticles(SOUL_DUST, center.x, center.y + 0.1D, center.z, 10, 0.25D, 0.2D, 0.25D, 0.02D);
        level.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_SILVERFISH_MOLT.get(), SoundSource.HOSTILE, 1.25F, 0.9F);
        if (random.nextFloat() < MOLT_DROP_CHANCE) dropCrystalPowder();
    }

    /**
     * 被虫群呼救叫来的那一只：不管它原来蜕到第几阶，一律回到一阶（24 血）重新入场。
     * 否则一只残血三阶的小虫被叫过来，玩家连「它从地下钻出来」都看不清就没了。
     */
    public void resetToStageOne() {
        int before = stage();
        setStage(1);
        // 回到一阶就是完整的一只：上限 24，草补的「吃过」标记也没意义了。
        fed = false;
        healTicks = 0;
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(maxHealthForStage(1));
        setHealth(getMaxHealth());
        applyStageAttributes();
        moltLockTicks = 0;
        setBurrowedOut(true);
        if (before != 1 && level() instanceof ServerLevel server) {
            Vec3 center = getBoundingBox().getCenter();
            server.sendParticles(MOLT_DUST, center.x, center.y, center.z,
                    12, 0.2D, 0.15D, 0.2D, 0.03D);
        }
    }

    /** 30% 掉的那一份魔晶粉：下 / 中 / 高 按 3 : 2 : 1 抽。 */
    private void dropCrystalPowder() {
        String id = switch (random.nextInt(6)) {
            case 0, 1, 2 -> "lesser_mana_crystal_dust";
            case 3, 4 -> "medium_mana_crystal_dust";
            default -> "advanced_mana_crystal_dust";
        };
        var holder = GeneratedMod.MOD_ITEMS.get(id);
        if (holder != null) spawnAtLocation(new ItemStack(holder.get()));
    }

    @Override
    public void die(DamageSource source) {
        boolean server = !level().isClientSide;
        super.die(source);
        if (!server) return;
        // 第四次死亡：50% 掉一份魂火残渣。
        if (random.nextFloat() < DEATH_DROP_CHANCE) {
            var residue = GeneratedMod.MOD_ITEMS.get(GeneratedMod.SOUL_FIRE_RESIDUE_ID);
            if (residue != null) spawnAtLocation(new ItemStack(residue.get()));
        }
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 center = getBoundingBox().getCenter();
            serverLevel.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z,
                    12, 0.25D, 0.2D, 0.25D, 0.02D);
        }
    }

    // ---------------------------------------------------------------- 攻击
    /**
     * 一口三段的伤害：物理 → 奥术 → 毒，再挂 2 秒剧毒（三阶 III / 二阶 II / 一阶 I）。
     * 四阶只有奥术，也没有毒。
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (level().isClientSide || !(target instanceof LivingEntity living)) return false;
        int stage = stage();
        float physical = physicalForStage(stage);
        if (physical > 0.0F) living.hurt(damageSources().mobAttack(this), physical);
        if (level() instanceof ServerLevel server) {
            SpellDamage.hitElement(server, this, living, MagicEquipment.ARCANE, arcaneForStage(stage));
            float poison = poisonDamageForStage(stage);
            if (poison > 0.0F) SpellDamage.hitElement(server, this, living, ElementType.POISON.id(), poison);
            int amplifier = poisonAmplifierForStage(stage);
            if (amplifier >= 0) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, amplifier, false, true));
            }
            server.playSound(null, getX(), getY(), getZ(),
                    GeneratedMod.CORRUPTED_SILVERFISH_BITE.get(), SoundSource.HOSTILE, 0.9F, 1.15F);
            server.sendParticles(ParticleTypes.WITCH, living.getX(),
                    living.getY() + living.getBbHeight() * 0.5D, living.getZ(),
                    8, 0.25D, 0.25D, 0.25D, 0.02D);
        }
        return true;
    }

    // ---------------------------------------------------------------- 钻地
    /** 从地下钻出来（刷怪笼 / 挖矿 / 被召唤都会走这里）。 */
    public void emergeFromGround() {
        if (!(level() instanceof ServerLevel server)) return;
        setBurrowedOut(true);
        Vec3 center = getBoundingBox().getCenter();
        server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                center.x, getY() + 0.05D, center.z, 18, 0.25D, 0.05D, 0.25D, 0.04D);
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z,
                8, 0.2D, 0.15D, 0.2D, 0.02D);
        server.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CORRUPTED_SILVERFISH_BURROW.get(), SoundSource.HOSTILE, 1.1F, 1.0F);
    }

    /**
     * 钻地突进：被虫群呼救叫到时，直接从地里一路钻到目标脚边再破土。
     *
     * <p>蠹虫本来就是 {@code noPhysics} 的（原版它就是这么钻进石头里的），所以这段位移
     * 不寻路、不碰撞，只是每刻朝目标推 {@link #DIG_RUSH_SPEED} 格，身后拖一串土屑与魂火。
     * 到 2 格以内（或时间用完）就破土收工。</p>
     */
    public void beginDigRush(LivingEntity victim) {
        if (level().isClientSide || victim == null || !victim.isAlive()) return;
        this.digRushTicks = DIG_RUSH_TICKS;
        this.digRushVictim = victim.getUUID();
        this.setBurrowedOut(false);
        this.setTarget(victim);
        this.eatReadyAt = level().getGameTime() + 100L;
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(),
                    GeneratedMod.CORRUPTED_SILVERFISH_BURROW.get(), SoundSource.HOSTILE, 0.9F, 0.85F);
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    getX(), getY() + 0.05D, getZ(), 12, 0.2D, 0.05D, 0.2D, 0.03D);
        }
    }

    /** 正在钻地赶路（这期间不寻路、不啃草）。 */
    public boolean isDigRushing() {
        return digRushTicks > 0;
    }

    /** 啃食目标把它挂在植株上时置 true；停栖期间 climbPlants 让位。 */
    public void setPerching(boolean perching) {
        this.perching = perching;
    }

    public boolean isPerching() {
        return perching;
    }

    /** 把移动控制按住（切回 WAIT）：停栖悬停时不能让领航还在往前推。 */
    public void haltMoveControl() {
        if (this.moveControl instanceof SmoothSilverfishMoveControl smooth) {
            smooth.setWait();
        }
    }

    private void tickDigRush() {
        if (digRushTicks <= 0) return;
        if (!(level() instanceof ServerLevel server)) {
            digRushTicks = 0;
            return;
        }
        digRushTicks--;
        LivingEntity victim = digRushVictim != null
                && server.getEntity(digRushVictim) instanceof LivingEntity living ? living : null;
        if (victim == null || !victim.isAlive()) {
            endDigRush(server);
            return;
        }
        if (distanceTo(victim) <= 2.2D || digRushTicks <= 0) {
            endDigRush(server);
            return;
        }
        Vec3 delta = victim.position().subtract(position());
        if (delta.lengthSqr() < 1.0E-4D) {
            endDigRush(server);
            return;
        }
        delta = delta.normalize().scale(DIG_RUSH_SPEED);
        setPos(getX() + delta.x, getY() + delta.y, getZ() + delta.z);
        hasImpulse = true;
        hurtMarked = true;
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.1D, getZ(),
                2, 0.12D, 0.08D, 0.12D, 0.005D);
        if (tickCount % 3 == 0) {
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    getX(), getY() + 0.02D, getZ(), 4, 0.15D, 0.02D, 0.15D, 0.02D);
        }
    }

    /** 破土：站到目标脚下那一格（那一格一定是能站的），抖一圈土再开打。 */
    private void endDigRush(ServerLevel server) {
        digRushTicks = 0;
        LivingEntity victim = digRushVictim != null
                && server.getEntity(digRushVictim) instanceof LivingEntity living ? living : null;
        digRushVictim = null;
        if (victim != null && victim.isAlive()) {
            BlockPos seat = victim.blockPosition();
            setPos(seat.getX() + 0.5D, seat.getY(), seat.getZ() + 0.5D);
            setDeltaMovement(Vec3.ZERO);
            setTarget(victim);
        }
        setBurrowedOut(true);
        emergeFromGround();
    }

    // ---------------------------------------------------------------- 啃食
    /** 30 秒冷却好了没有。 */
    public boolean canEat() {
        return level().getGameTime() >= eatReadyAt;
    }

    public void setEatCooldown() {
        eatReadyAt = level().getGameTime() + EAT_COOLDOWN_TICKS;
    }

    /**
     * 啃完一整株卢梅莉亚：进 30 秒冷却；<b>蜕过皮的</b>还会把身体补回一阶的 24 血满状态。
     *
     * <p>卢梅莉亚本来就是回血的魔法药草（{@code lumeliaTouch} 会给活物回血），对这只靠
     * 蜕皮把血上限一阶阶耗掉的虫子来说就是「把身子重新养回来」：上限从 12 / 6 / 3 抬回 24，
     * 血量在接下来的 30 秒里一点点涨上去（不是瞬间满血，玩家还有时间打断）。</p>
     *
     * <p>一阶的个体上限本来就是 24，不需要这一步；再蜕一次皮标记会被清掉（见 {@link #molt}）。</p>
     */
    public void eatLumelia() {
        setEatCooldown();
        if (stage() <= 1) return;
        fed = true;
        healTicks = LUMELIA_HEAL_TICKS;
        applyStageAttributes();
        if (level() instanceof ServerLevel server) {
            Vec3 center = getBoundingBox().getCenter();
            server.sendParticles(ParticleTypes.ENCHANT, center.x, center.y, center.z,
                    12, 0.3D, 0.3D, 0.3D, 0.04D);
        }
    }

    /** 吃过草之后的回血：每刻 24/600 点，正好 30 秒从空血补到满。 */
    private void tickLumeliaHeal() {
        if (healTicks <= 0) return;
        float max = getMaxHealth();
        if (getHealth() >= max) {
            healTicks = 0;
            return;
        }
        healTicks--;
        setHealth(Math.min(max, getHealth() + maxHealthForStage(1) / LUMELIA_HEAL_TICKS));
        if (healTicks % 20 == 0 && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANT,
                    getX(), getY() + getBbHeight() * 0.6D, getZ(),
                    2, 0.25D, 0.2D, 0.25D, 0.01D);
        }
    }

    /** 还会不会回血（调试 / 图鉴用）。 */
    public boolean isHealingFromLumelia() {
        return healTicks > 0;
    }

    // ---------------------------------------------------------------- 攀爬
    /**
     * 顺着植物往上爬，爬到顶再翻上旁边的台沿。
     *
     * <p>高草、藤蔓、卢梅莉亚都不是实心方块，没有碰撞可以踩，原版蠹虫那套「贴墙爬」用不上。
     * 这里换成：身体旁边有植物、<b>脚底那格的上面一格还接着一段</b>，就给自己一点向上的速度——
     * 看起来就是顺着高草 / 藤蔓 / 卢梅莉亚一节一节爬上去。</p>
     *
     * <p><b>r92 修「爬上一格高的卢梅莉亚就卡住、上不去高处」</b>：以前「头顶还接着一段」的探测点
     * 是 {@code getY() + getBbHeight() + 0.35}，而蠹虫只有 0.3 格高，这个高度还落在<b>脚底那一格</b>里
     * ——两次探测其实一直在看同一个方块。于是 1 格高的卢梅莉亚也被当成「能爬的高株」：它爬到草尖上
     * 升力就断了，掉下来再爬、再掉，看起来就是粘在草上不进不退，也上不去旁边的坎。
     * 现在明确查<b>脚底那格的上面一格</b>，1 格高的草只是走过去，不再爬。</p>
     *
     * <p>顺手补了「翻台沿」：贴着植物爬到顶、正前方是一个能上去的台沿（实心、头顶空着、高度 ≤ 1.9 格）
     * 时多给一点升力把它托上去——荒原断崖上正好有草的时候，它能顺着草翻上坎，而不是在坎下面来回蹭。</p>
     */
    private void climbPlants() {
        if (level().isClientSide || tickCount % 2 != 0) return;
        AABB box = getBoundingBox().inflate(0.3D, 0.05D, 0.3D);
        // 身体这一圈要有植株可抓，否则不算「在爬草」。
        if (!plantAt(box, getY() + 0.1D)) return;
        // 上面还接着一段（真正的高株）：照旧往上爬。
        boolean moreAbove = plantAt(box, getY() + 1.05D);
        // 没有更高的一段，但前面是个能翻上去的台沿：托它一把（每 4 刻给一次，够它翻上去）。
        boolean mantle = !moreAbove && tickCount % 4 == 0 && mantleAhead();
        if (!moreAbove && !mantle) return;
        double lift = mantle ? 0.32D : 0.18D;
        Vec3 delta = getDeltaMovement();
        if (delta.y >= lift) return;
        setDeltaMovement(delta.x, lift, delta.z);
        hasImpulse = true;
        hurtMarked = true;
    }

    /**
     * 正前方是不是一个「能翻上去的台沿」：朝向那格挡路、台面不高于 1.9 格、台面上方是空的。
     * 另外要求它确实在往前走（有寻路目标或已经在动），免得贴着墙站着也往上飘。
     */
    private boolean mantleAhead() {
        boolean walking = !getNavigation().isDone()
                || getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
        if (!walking) return false;
        BlockPos feet = blockPosition();
        BlockPos ahead = feet.relative(Direction.fromYRot(getYRot()));
        BlockState front = level().getBlockState(ahead);
        double top = ahead.getY() + front.getCollisionShape(level(), ahead).max(Direction.Axis.Y);
        if (top <= getY() + 0.05D) return false;      // 前面是平的，用不着翻
        if (top - getY() > 1.9D) return false;        // 太高，翻不上去
        BlockPos onTop = ahead.above();
        if (!level().getBlockState(onTop).getCollisionShape(level(), onTop).isEmpty()) return false;
        return level().getFluidState(onTop).isEmpty();
    }

    /** 这一层高度上、身体横向范围内有没有可以爬的植物。 */
    private boolean plantAt(AABB box, double y) {
        int y0 = Mth.floor(y);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), y0, Mth.floor(box.minZ),
                Mth.floor(box.maxX), y0, Mth.floor(box.maxZ))) {
            if (isPlant(level().getBlockState(pos))) return true;
        }
        return false;
    }

    /** 能爬的植物：高草、藤蔓、卢梅莉亚（矮草不算，那点高度用不着爬）。 */
    public static boolean isPlant(BlockState state) {
        return state.is(Blocks.TALL_GRASS) || state.is(Blocks.VINE)
                || state.getBlock() instanceof LumeliaPlantBlock;
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        float previousBodyRot = this.yBodyRot;
        if (moltLockTicks > 0) moltLockTicks--;
        super.tick();
        // 原版 Silverfish.tick() 在**两边**都把 yBodyRot 硬写成 yRot（它甚至把
        // setYBodyRot 重定向到 setYRot），上一轮只在服务端平滑，客户端渲染每刻被
        // 覆盖回硬对齐——抽搐就是这么来的。这里两边都从上一刻的身体朝向往当前朝向
        // 转：服务端 45°/刻，客户端 30°/刻（渲染端更慢一点，观感更顺）。钻地突进是
        // 瞬移赶路，不参与平滑。
        if (!isDigRushing()) {
            float maxTurn = this.level().isClientSide ? 30.0F : 45.0F;
            this.yBodyRot = Mth.rotateIfNecessary(previousBodyRot, this.getYRot(), maxTurn);
            // r92：残留的「轻微左右抽搐」出在渲染插值上。
            // super.tick() 里的 LivingEntity.baseTick() 会把 yBodyRotO 写成当时的 yBodyRot，
            // 而那个值已经被原版 Silverfish.tick() 硬对齐成**未平滑**的 yRot 了。
            // 于是每帧都从「生硬的 yRot」插值回我们平滑后的值，一帧一个回弹——
            // 角度不大，但一直在抖。把 yBodyRotO 改回上一刻真正用过的身体朝向，
            // 插值才变成「上一刻平滑值 → 这一刻平滑值」，来回都顺。
            this.yBodyRotO = previousBodyRot;
        }
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        tickDigRush();
        // 吃过卢梅莉亚之后的回血（30 秒补到 24 血满）。
        tickLumeliaHeal();
        // 停栖啃食时竖直位置由啃食目标接管，这里两套升力不能打架。
        if (!isDigRushing() && !perching) climbPlants();
        // 钻地赶路的时候别让 Goal 把它拽回去走路。
        if (isDigRushing()) getNavigation().stop();
    }

    /** 客户端：身上冒一点魂火，阶数越深越淡（它越来越小，也快散了）。 */
    private void clientParticles() {
        if (random.nextInt(6) != 0) return;
        level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                getX() + random.triangle(0.0D, 0.18D),
                getY(0.3D + random.nextDouble() * 0.5D),
                getZ() + random.triangle(0.0D, 0.18D),
                0.0D, 0.01D, 0.0D);
    }

    /** 钻地时贴地走，不受摔落伤害。 */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(0.4D);
    }

    // ---------------------------------------------------------------- 音效
    @Override
    protected SoundEvent getAmbientSound() {
        return GeneratedMod.CORRUPTED_SILVERFISH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GeneratedMod.CORRUPTED_SILVERFISH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GeneratedMod.CORRUPTED_SILVERFISH_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.05F;
    }
}
