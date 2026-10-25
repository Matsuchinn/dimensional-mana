package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * 十二种元素史莱姆共用的实体基类。变种只决定元素规则，等级只决定生命、体型、伤害与掉落。
 * 继承 Slime 以保留原版蹦跳手感；remove 前强制 size=1，彻底关闭原版死亡分裂。
 */
public final class ElementalSlime extends Slime {
    private static final EntityDataAccessor<String> TIER = SynchedEntityData.defineId(ElementalSlime.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> TAMED = SynchedEntityData.defineId(ElementalSlime.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ElementalSlime.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(ElementalSlime.class, EntityDataSerializers.BOOLEAN);
    /** 被哥布林萨满驯服的个体：不是玩家的宠物，会跟着哥布林一起打架。 */
    private static final EntityDataAccessor<Boolean> GOBLIN_ALLY = SynchedEntityData.defineId(ElementalSlime.class, EntityDataSerializers.BOOLEAN);
    private static final float[] SPELL_DIFFICULTY = {0.20F, 0.60F, 1.00F, 1.40F};
    private static final float[] PHYSICAL_DAMAGE = {1.0F, 2.0F, 3.0F, 4.0F};
    private static final float[] ELEMENT_DAMAGE = {1.0F, 3.0F, 5.0F, 7.0F};
    private static final float[] REGEN_DIFFICULTY = {0.50F, 1.00F, 1.00F, 1.50F};
    /** 土史莱姆驯服门槛：10 点，泥土 1 点、圆石 2 点，所以 10 泥土和 5 圆石等价。 */
    private static final int EARTH_TAME_POINTS = 10;
    /** 音史莱姆发奖励后的冷却：设计稿要求的 5 分钟。 */
    private static final long SOUND_REWARD_COOLDOWN = 5 * 60 * 20L;
    /** 设计稿里所有「每秒回血 2.5%」都按最大生命百分比算，再乘难度恢复倍率。 */
    private static final float HEAL_FRACTION = 0.025F;
    /**
     * 初级法术的冷却倍率：2 表示施法间隔翻倍、攻击频率降到原来的一半。
     * 只作用于元素史莱姆，玩家学会同一法术时的冷却不受影响。
     */
    private static final int SPELL_COOLDOWN_MULTIPLIER = 2;

    private final ElementalSlimeVariant variant;
    private long nextSpellTick;
    private long nextGiftTick;
    private long nextDailyProductTick;
    private long soundExpiresAt;
    private long soundRewardReadyAt;
    private long replicationCooldown;
    private int replicationLimit = 4;
    private int earthFed;
    private int musicTicks;
    private boolean initialized;
    /** 音史莱姆循声而来的目标点与截止时间。 */
    private Vec3 approach;
    private long approachUntil;
    /** 水史莱姆踩水、火史莱姆踩岩浆时视为站立，MoveControl 才会照常起跳前进。 */
    private boolean fluidWalking;
    /** 骑乘时两次蹦跳之间的停顿（刻）；落地后数完再起跳，才不会像船一样平滑滑行。 */
    private int rideHopDelay;
    // ---------------------------------------------------------------- 风史莱姆飞行状态
    /** true = 正在高空漂浮/飞行；false = 在地面闲逛，倒计时结束后起飞。 */
    private boolean windFloating;
    /** 地面闲逛结束时刻（gameTime）。 */
    private long windGroundUntil;
    /** 当前要飘去的点（水平坐标 + 目标高度）。 */
    private Vec3 windTarget;
    private double windTargetY;
    /** 到达飘点后先悬停到这个时刻，再挑下一个点。 */
    private long windPauseUntil;
    // ---------------------------------------------------------------- 手持物品吸引
    /** 被玩家手持物吸引时要凑过去的点；null 表示当前没有吸引源。 */
    private Vec3 lurePoint;
    private int lureRefresh;
    /** 矿脉被挖掘时，附近多少格内的史莱姆会立刻进入战斗。 */
    private static final double MINING_ALERT_RADIUS = 16.0;

    public ElementalSlime(EntityType<? extends ElementalSlime> type, Level level, ElementalSlimeVariant variant) {
        super(type, level);
        this.variant = variant;
        xpReward = 2;
        // 换掉原版私有的 SlimeMoveControl：否则只能靠 setWantedPosition 盲猜，转向和跳跃节奏都会跟着乱。
        this.moveControl = new ElementalSlimeMoveControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Slime.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, ElementalSlimeTier.MATURE.id());
        builder.define(TAMED, false);
        builder.define(OWNER, Optional.empty());
        builder.define(SADDLED, false);
        builder.define(GOBLIN_ALLY, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ElementalSlimeAvoidHazardGoal(this));
        goalSelector.addGoal(2, new FloatGoal(this));
        goalSelector.addGoal(3, new ElementalSlimeSpellGoal(this));
        goalSelector.addGoal(4, new ElementalSlimeCombatGoal(this));
        // 结盟个体没有独立敌对行为，只跟着哥布林的目标走。
        goalSelector.addGoal(5, new ElementalSlimeGoblinAllyGoal(this));
        if (variant == ElementalSlimeVariant.POISON) goalSelector.addGoal(5, new ElementalSlimeShadeGoal(this));
        goalSelector.addGoal(6, new ElementalSlimeWanderGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, LivingEntity.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new ElementalSlimeRetaliateGoal(this));
        targetSelector.addGoal(2, new ElementalSlimeTerritoryGoal(this));
    }

    public ElementalSlimeVariant variant() { return variant; }
    public ElementalSlimeTier tier() { return ElementalSlimeTier.byId(entityData.get(TIER)); }
    public boolean isTamed() { return entityData.get(TAMED); }
    public Optional<UUID> ownerUUID() { return entityData.get(OWNER); }
    public boolean isSaddled() { return entityData.get(SADDLED); }
    /** 哥布林萨满驯服的盟友：帮哥布林打架，但不会被玩家用栓绳牵走。 */
    public boolean isGoblinAlly() { return entityData.get(GOBLIN_ALLY); }

    public void makeGoblinAlly() {
        entityData.set(GOBLIN_ALLY, true);
        setPersistenceRequired();
        setTarget(null);
        if (level() instanceof ServerLevel server)
            server.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight() * 0.8, getZ(),
                    10, 0.35, 0.35, 0.35, 0.02);
        level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, getSoundSource(), 0.8F, 1.4F);
    }
    public int replicationLimit() { return replicationLimit; }
    public void setReplicationLimit(int value) { replicationLimit = Mth.clamp(value, 0, 4); }
    public void setTier(ElementalSlimeTier value, boolean rerollHealth) {
        entityData.set(TIER, value.id());
        int size = value == ElementalSlimeTier.CUB ? 1 : value == ElementalSlimeTier.MATURE ? 2 : 4;
        setSize(size, false);
        double max = rerollHealth ? value.randomHealth(getRandom()) : value.maxHealth();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        setHealth((float)max);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ELEMENT_DAMAGE[difficultyIndex()] * value.damageScale());
        refreshDimensions();
    }

    public void tame(Player player) {
        entityData.set(TAMED, true);
        entityData.set(OWNER, Optional.of(player.getUUID()));
        setTarget(null);
        setPersistenceRequired();
        if (variant == ElementalSlimeVariant.SOUND) soundExpiresAt = level().getGameTime() + 10 * 60 * 20L;
        level().broadcastEntityEvent(this, (byte)7);
        // 1.21 的生物实体事件里没有“冒爱心”这一条，所以服务端直接补一轮爱心粒子，
        // 每个客户端都能看到驯服成功的瞬间反馈。
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART,
                    getX(), getY() + getBbHeight() * 0.8, getZ(), 14, 0.35, 0.35, 0.35, 0.02);
        }
        if (!level().isClientSide) {
            // 驯服成功必须有明确的声光与文字反馈，否则玩家会以为喂食没生效。
            level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, getSoundSource(), 0.9F, 1.5F);
            player.displayClientMessage(Component.translatable(
                    "entity.dimensional_mana.elemental_slime.tamed", getDisplayName()), false);
        }
    }

    /** 音符盒需累计 10 秒、唱片机需累计 5 秒；成功率 50%，驯服后每次完整乐曲仍会发奖（5 分钟冷却）。 */
    public void hearMusic(Player player, int ticks, int requiredTicks) {
        hearMusic(player, ticks, requiredTicks, null);
    }

    /** source 是声源位置：未驯服的音史莱姆会「循声而来」凑过去。 */
    public void hearMusic(Player player, int ticks, int requiredTicks, Vec3 source) {
        if (variant != ElementalSlimeVariant.SOUND || player == null || level().isClientSide) return;
        long now = level().getGameTime();
        if (source != null && !isTamed()) {
            approach = source;
            approachUntil = now + requiredTicks + 40L;
        }
        musicTicks = Math.min(requiredTicks, musicTicks + Math.max(1, ticks));
        if (musicTicks < requiredTicks) return;
        musicTicks = 0;
        if (!isTamed()) {
            if (getRandom().nextFloat() >= 0.50F) return;
            tame(player);
        } else if (now < soundRewardReadyAt) {
            // 已驯服的个体：碎片奖励进入 5 分钟冷却，避免一首循环曲把碎片刷爆。
            int seconds = (int)Math.ceil((soundRewardReadyAt - now) / 20.0);
            player.displayClientMessage(Component.translatable(
                    "entity.dimensional_mana.sound_elemental_slime.cooldown", seconds), true);
            return;
        }
        soundRewardReadyAt = now + SOUND_REWARD_COOLDOWN;
        rewardSoundFragments();
        // 被驯服个体下次播放会再次出现并给奖励；这里重置十分钟存在时间，等价于重新出现一轮。
        soundExpiresAt = now + 10 * 60 * 20L;
    }

    /** 非空表示正在循声而来，闲逛 Goal 会优先朝这里蹦。 */
    public Vec3 approachTarget() {
        return approach != null && level().getGameTime() < approachUntil ? approach : null;
    }

    // ---------------------------------------------------------------- 手持物品吸引
    /** 非空表示附近有拿着对应物品的玩家，闲逛 Goal 会朝这个点凑过去。 */
    Vec3 lureTarget() { return lurePoint; }

    /**
     * 每 10 刻扫一次附近的玩家：
     * 金史莱姆对铁锭、铁质工具、整套铁甲感兴趣；土史莱姆对泥土和圆石感兴趣；
     * 风史莱姆对风弹感兴趣。已经驯服、正在打架或被人骑着时不理会吸引。
     */
    private void tickLure() {
        if (isTamed() || getTarget() != null || isVehicle() || level().isClientSide) {
            lurePoint = null;
            return;
        }
        if (--lureRefresh > 0) return;
        lureRefresh = 10;
        Player best = null;
        double bestDistance = 16.0 * 16.0;
        for (Player player : level().players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            if (!isLuredBy(player)) continue;
            double distance = distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        // 已经贴到两格以内就停下来，别把玩家顶来顶去。
        lurePoint = best != null && bestDistance > 4.0 ? best.position() : null;
    }

    private boolean isLuredBy(Player player) {
        return switch (variant) {
            case METAL -> holdsIron(player);
            case EARTH -> earthFoodPoints(player.getMainHandItem()) > 0
                    || earthFoodPoints(player.getOffhandItem()) > 0;
            case WIND -> player.getMainHandItem().is(Items.WIND_CHARGE)
                    || player.getOffhandItem().is(Items.WIND_CHARGE);
            default -> false;
        };
    }

    /** 铁锭、铁质工具、或者身上穿着任意一件铁甲都算。 */
    private static boolean holdsIron(Player player) {
        if (isIronLureItem(player.getMainHandItem()) || isIronLureItem(player.getOffhandItem())) return true;
        for (ItemStack armor : player.getArmorSlots()) {
            if (armor.is(Items.IRON_HELMET) || armor.is(Items.IRON_CHESTPLATE)
                    || armor.is(Items.IRON_LEGGINGS) || armor.is(Items.IRON_BOOTS)) return true;
        }
        return false;
    }

    private static boolean isIronLureItem(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_SWORD) || stack.is(Items.IRON_PICKAXE)
                || stack.is(Items.IRON_AXE) || stack.is(Items.IRON_SHOVEL) || stack.is(Items.IRON_HOE);
    }

    // ---------------------------------------------------------------- 挖矿惊动
    /**
     * 金史莱姆护铁矿、魔素史莱姆护高级以上魔晶矿：玩家在附近挖到这两种矿，
     * 它们会立刻把玩家当成目标扑过来，不用再等领地计时。
     */
    public static void onBlockMined(Player player, BlockPos pos, BlockState state) {
        if (player == null || player.level().isClientSide) return;
        boolean iron = state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE);
        boolean crystal = isAdvancedManaOre(state);
        if (!iron && !crystal) return;
        AABB area = new AABB(pos).inflate(MINING_ALERT_RADIUS);
        for (ElementalSlime slime : player.level().getEntitiesOfClass(ElementalSlime.class, area)) {
            boolean cares = (iron && slime.variant == ElementalSlimeVariant.METAL)
                    || (crystal && slime.variant == ElementalSlimeVariant.MANA);
            if (!cares || slime.isTamed() || !slime.validTerritoryTarget(player)) continue;
            slime.setTarget(player);
            slime.showAlertParticles();
        }
    }

    private static boolean isAdvancedManaOre(BlockState state) {
        return state.is(GeneratedMod.MOD_BLOCKS.get("advanced_mana_crystal_ore").get())
                || state.is(GeneratedMod.MOD_BLOCKS.get("supreme_mana_crystal_ore").get())
                || state.is(GeneratedMod.MOD_BLOCKS.get("special_mana_crystal_cluster").get());
    }

    private void showAlertParticles() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    getX(), getY() + getBbHeight() + 0.2, getZ(), 4, 0.3, 0.1, 0.3, 0.0);
        }
    }

    private void rewardSoundFragments() {
        spawnAtLocation(new ItemStack(GeneratedMod.MOD_ITEMS.get(variant.lesserFragmentId()).get(), 1 + getRandom().nextInt(2)));
        if (getRandom().nextFloat() < 0.25F) spawnAtLocation(GeneratedMod.MOD_ITEMS.get(variant.mediumFragmentId()).get());
        if (getRandom().nextFloat() < 0.05F) spawnAtLocation(GeneratedMod.MOD_ITEMS.get(variant.advancedFragmentId()).get());
    }

    public boolean isOwnedBy(Player player) {
        return player != null && ownerUUID().filter(player.getUUID()::equals).isPresent();
    }

    // ---------------------------------------------------------------- 音效
    private SoundEvent voice() {
        return GeneratedMod.elementalSlimeSound(variant);
    }

    /** 蹦跳音效由自定义 MoveControl 主动播；受伤、死亡、落地和随机环境音走下面几个 getter。 */
    public void playSlimeJumpSound() {
        playSound(voice(), getSoundVolume(), voicePitch(0.95F));
    }

    private float voicePitch(float base) {
        return ((getRandom().nextFloat() - getRandom().nextFloat()) * 0.2F + 1.0F) * base;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return voice(); }

    @Override
    protected SoundEvent getDeathSound() { return voice(); }

    @Override
    protected SoundEvent getSquishSound() { return voice(); }

    @Override
    protected SoundEvent getJumpSound() { return voice(); }

    @Override
    protected SoundEvent getAmbientSound() { return voice(); }

    /** 12 秒左右哼一声，够有存在感又不会吵闹。 */
    @Override
    public int getAmbientSoundInterval() { return 240; }

    /** 原版 0.4 × 体型，精英体 4 格时能到 1.6，太炸；压到 0.28 × 体型再加一点底噪。 */
    @Override
    protected float getSoundVolume() {
        return 0.28F * getSize();
    }

    /** 音源分类：雷史莱姆敌对用 HOSTILE，其余中立/友好用 NEUTRAL，这样字幕和音量分组更准确。 */
    @Override
    public SoundSource getSoundSource() {
        return variant == ElementalSlimeVariant.LIGHTNING ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
    }

    /** 落地尘土粒子跟元素配色走，比原版统一的史莱姆球更贴主题。 */
    @Override
    protected ParticleOptions getParticleType() {
        return switch (variant) {
            case MANA -> ParticleTypes.ENCHANT;
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.SPLASH;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case NATURE -> ParticleTypes.COMPOSTER;
            case POISON -> ParticleTypes.ITEM_SLIME;
            case METAL -> ParticleTypes.CRIT;
            case WIND -> ParticleTypes.CLOUD;
            case EARTH -> ParticleTypes.POOF;
            case LIGHT -> ParticleTypes.END_ROD;
            case SOUND -> ParticleTypes.NOTE;
        };
    }

    /**
     * 原版落地会按「身体宽度 × 16」撒一整圈粒子：精英体 4 格宽时一次六十多颗，太吵。
     * 返回 true 跳过原版那一圈，改成按体型撒 3～5 颗，并且只在客户端生成一次（原版服务端也会广播一遍，等于翻倍）。
     */
    @Override
    protected boolean spawnCustomParticles() {
        if (level().isClientSide) {
            int count = 2 + Math.min(3, getSize());
            for (int i = 0; i < count; i++) {
                float angle = getRandom().nextFloat() * 6.2831855F;
                float radius = getBbWidth() * 0.32F;
                level().addParticle(getParticleType(),
                        getX() + Mth.sin(angle) * radius, getY() + 0.05, getZ() + Mth.cos(angle) * radius,
                        0.0, 0.0, 0.0);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 元素免疫
    /** 火史莱姆免疫火焰意味着「不会被点燃」，光靠 hurt 里返回 false 挡不住岩浆给的 15 秒燃烧。 */
    @Override
    public boolean fireImmune() {
        return variant == ElementalSlimeVariant.FIRE || super.fireImmune();
    }

    /** 冰史莱姆不会被细雪冻住。 */
    @Override
    public boolean canFreeze() {
        return variant != ElementalSlimeVariant.ICE && super.canFreeze();
    }

    /** 体型的火焰叠图太大，所有元素史莱姆都改成小火苗粒子（见 tick）。 */
    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (!initialized) {
            setTier(ElementalSlimeTier.roll(level.getRandom()), true);
            initialized = true;
        }
        if (variant == ElementalSlimeVariant.SOUND) soundExpiresAt = level.getLevel().getGameTime() + 5 * 60 * 20L;
        if (variant == ElementalSlimeVariant.WIND) initWindState();
        nextGiftTick = level.getLevel().getGameTime() + 10 * 60 * 20L;
        nextDailyProductTick = level.getLevel().getGameTime() + 24_000L;
        return result;
    }

    @Override
    public void tick() {
        super.tick();
        // 原版火焰叠图会按史莱姆体型放大，精英体 4 格宽时整只都被火盖住，所以关掉叠图，改用小火苗粒子提示。
        if (isOnFire() && tickCount % 4 == 0) {
            level().addParticle(ParticleTypes.SMALL_FLAME,
                    getRandomX(0.7), getY() + getBbHeight() * 0.45, getRandomZ(0.7),
                    0.0, 0.01, 0.0);
        }
        if (level().isClientSide || !isAlive()) return;
        tickTargetValidity();
        long now = level().getGameTime();
        if (!initialized) {
            setTier(tier(), false);
            initialized = true;
        }
        if (tickCount % 20 == 0) {
            maintainElementAttachment();
            tickConditionalHealing();
            tickRainDamage();
            tickFrostDamage();
            tickTimedLife(now);
            tickTamedProduction(now);
            tickElementEnvironment();
        }
        tickFluidWalking();
        tickLure();
        if (approach != null && now >= approachUntil) approach = null;
        if (variant == ElementalSlimeVariant.LIGHTNING && tickCount % 10 == 0
                && (isInWaterOrBubble() || level().isRainingAt(blockPosition()))) electrifyNearby();
        if (variant == ElementalSlimeVariant.WIND) tickWindFlight(now);
        else setNoGravity(false);
        if (isTamed() && getTarget() != null) setTarget(null);
        // 已驯服的个体每 10 秒冒一颗爱心：玩家一眼就能看出这只已经是伙伴了，不用反复喂食试探。
        if (isTamed() && level().isClientSide && tickCount % 200 == 0) {
            level().addParticle(ParticleTypes.HEART, getX(), getY() + getBbHeight() * 0.85, getZ(), 0.0, 0.02, 0.0);
        }
    }

    /** 自定义 MoveControl；构造函数里已经装好，拿不到时返回 null 让目标自动跳过。 */
    public ElementalSlimeMoveControl slimeMoveControl() {
        return moveControl instanceof ElementalSlimeMoveControl control ? control : null;
    }

    /** 飞行中的朝向与速度完全由 tickWindFlight 接管，MoveControl 这时不要再插一脚。 */
    public boolean isWindFloating() {
        return windFloating;
    }

    /** 生成时 50% 直接在半空漂浮；其余先落回地面闲逛 30 秒，再起飞去别的地方。 */
    private void initWindState() {
        windFloating = getRandom().nextFloat() < 0.50F;
        windGroundUntil = level().getGameTime() + 30 * 20L;
        windTarget = null;
        windPauseUntil = 0;
    }

    /**
     * 风史莱姆的飞行：不靠寻路，每刻直接喂一段速度。
     *
     * <p>漂浮阶段无视重力，朝一个「地表往上 32 格」的飘点慢慢飞过去，到了就悬停几秒再换点；
     * 落地阶段完全交回原版蹦跳，闲逛 30 秒后重新起飞。锁敌时会直接朝目标飞。</p>
     */
    private void tickWindFlight(long now) {
        if (!windFloating) {
            setNoGravity(false);
            if (now >= windGroundUntil) {
                windFloating = true;
                windTarget = null;
                windPauseUntil = 0;
            }
            return;
        }
        setNoGravity(true);
        // 偶尔会顶进方块里（树冠、山洞顶），先把自己拔出来。
        if (isInWall()) {
            setDeltaMovement(getDeltaMovement().x, 0.25, getDeltaMovement().z);
            return;
        }
        LivingEntity target = getTarget();
        boolean chasing = target != null && target.isAlive();
        if (chasing) {
            windTarget = target.position();
            windTargetY = target.getY() + target.getBbHeight() * 0.5 + 0.8;
        } else if (windTarget == null) {
            if (now < windPauseUntil) {
                // 悬停：只留一点点上下浮动，像被风托着。
                setDeltaMovement(getDeltaMovement().x * 0.55, Mth.sin(tickCount * 0.12F) * 0.012,
                        getDeltaMovement().z * 0.55);
                return;
            }
            pickWindTarget();
            if (windTarget == null) return;
        }
        double dx = windTarget.x - getX();
        double dz = windTarget.z - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = windTargetY - getY();
        if (!chasing && horizontal < 1.5 && Math.abs(dy) < 1.5) {
            windTarget = null;
            windPauseUntil = now + 60 + getRandom().nextInt(160);
            setDeltaMovement(getDeltaMovement().x * 0.4, 0.0, getDeltaMovement().z * 0.4);
            return;
        }
        double speed = Math.min(0.24, 0.05 + Math.max(horizontal, Math.abs(dy)) * 0.06);
        double vx = horizontal > 0.05 ? dx / horizontal * speed : 0.0;
        double vz = horizontal > 0.05 ? dz / horizontal * speed : 0.0;
        double vy = Mth.clamp(dy * 0.10, -0.16, 0.16);
        setDeltaMovement(vx, vy, vz);
        // 撞墙就换个飘点，别一直顶着方块飞。
        if (horizontalCollision && !chasing) {
            pickWindTarget();
        }
        if (horizontal > 0.4) {
            float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            setYRot(yaw);
            yHeadRot = yaw;
            yBodyRot = yaw;
        }
    }

    /** 在地表往上约 32 格、水平 16～40 格外挑一个飘点。 */
    private void pickWindTarget() {
        double angle = getRandom().nextDouble() * Math.PI * 2.0;
        double distance = 16.0 + getRandom().nextDouble() * 24.0;
        int x = Mth.floor(getX() + Math.cos(angle) * distance);
        int z = Mth.floor(getZ() + Math.sin(angle) * distance);
        BlockPos column = new BlockPos(x, Mth.floor(getY()), z);
        int surface = level().hasChunkAt(column)
                ? level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : Mth.floor(getY());
        double maxY = level().getMaxBuildHeight() - 6.0;
        double minY = level().getMinBuildHeight() + 12.0;
        windTargetY = Mth.clamp(surface + 32, minY, maxY);
        windTarget = new Vec3(x + 0.5, windTargetY, z + 0.5);
        windPauseUntil = 0;
    }

    /** 原版 getJumpDelay 是 protected，MoveControl 在另一个包里调不到，这里转一手。 */
    public int elementJumpDelay() {
        return getJumpDelay();
    }

    public boolean isFluidWalking() {
        return fluidWalking;
    }

    /**
     * 水史莱姆在水面、火史莱姆在岩浆面使用连续浮力，不再每刻 setPos 硬抬位置。
     * 旧实现的瞬移托举会和重力/跳跃互相争抢，表现为上下抖动且几乎不前进。
     */
    private void tickFluidWalking() {
        boolean water = variant == ElementalSlimeVariant.WATER && isInWaterOrBubble();
        boolean lava = variant == ElementalSlimeVariant.FIRE && isInLava();
        if (!water && !lava) {
            fluidWalking = false;
            return;
        }
        BlockPos pos = blockPosition();
        FluidState fluid = level().getFluidState(pos);
        if (fluid.isEmpty()) {
            fluidWalking = false;
            return;
        }
        double surface = pos.getY() + fluid.getHeight(level(), pos) - 0.04;
        double error = surface - getY();
        fluidWalking = error > -0.30;
        Vec3 movement = getDeltaMovement();
        if (error > 0.0) {
            // 越沉浮力越大，接近液面后自然减弱；保留水平速度，不再产生锯齿式上下瞬移。
            double lift = Mth.clamp(error * 0.16 + 0.025, 0.025, 0.11);
            setDeltaMovement(movement.x, Math.max(movement.y * 0.55, lift), movement.z);
        } else if (movement.y < -0.04) {
            setDeltaMovement(movement.x, -0.04, movement.z);
        }
    }

    private void maintainElementAttachment() {
        if (variant == ElementalSlimeVariant.MANA) {
            forceAddEffect(new net.minecraft.world.effect.MobEffectInstance(GeneratedMod.ARCANE_ATTACHMENT,
                    ElementReactionSystem.ARCANE_ATTACHMENT_TICKS, 0, true, true, true), this);
        } else {
            ElementReactionSystem.refresh(this, variant.element());
        }
    }

    private void tickConditionalHealing() {
        boolean healing = switch (variant) {
            // 火史莱姆免疫火焰，身上不会有火焰 tick，所以要额外判断「脚底下是不是火方块」。
            case FIRE -> isInLava() || getRemainingFireTicks() > 0 || isStandingInFire();
            // 水史莱姆：泡在水里或淋雨回血；下雪不算，雪天对它是扣血（见 tickFrostDamage）。
            case WATER -> isInWaterOrBubble() || (inRain() && !isSnowingHere());
            // 原版 isRainingAt 只认「雨」，雪原降水类型是雪，会判成 false，所以这里自己看降水。
            case ICE -> inRain() && isColdBiome();
            case LIGHT -> level().isDay() && level().canSeeSky(blockPosition());
            default -> false;
        };
        if (healing) healPercent(HEAL_FRACTION);
    }

    /** 设计稿里的回血一律是「每秒 2.5% 最大生命」，再乘难度恢复倍率（50/100/100/150%）。 */
    private void healPercent(float fraction) {
        heal(getMaxHealth() * fraction * REGEN_DIFFICULTY[difficultyIndex()]);
    }

    /** 淋雨或落雪：冰史莱姆两者都能回血，所以这里只要求天空正在降水。 */
    private boolean inRain() {
        return level().isRaining() && level().canSeeSky(blockPosition());
    }

    /** 火史莱姆只怕真正的雨；雪原降雪不会按“淋雨每秒 4 点”结算。 */
    boolean isHurtByRain() {
        return variant == ElementalSlimeVariant.FIRE && level().isRaining()
                && level().canSeeSky(blockPosition())
                && level().getBiome(blockPosition()).value().getPrecipitationAt(blockPosition())
                == net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
    }

    private void tickRainDamage() {
        if (isHurtByRain()) hurt(level().damageSources().drown(), 4.0F);
    }

    /**
     * 水史莱姆被冰雪克制：踩到 / 蹭到冰、雪方块，或者正站在雪里，每秒固定掉 1 点血。
     * 这是独立于元素反应的环境伤害，冰史莱姆的冰冻反应也碰不到水史莱姆——它只会被雪「耗」。
     *
     * <p>但只要它泡在水里或踩着水面，冰雪就碰不到它：水会隔开落雪，这也是冰原水下的
     * 水史莱姆能长期活着的原因（否则刷在水里也会被雪一点点磨死）。</p>
     */
    private void tickFrostDamage() {
        if (variant != ElementalSlimeVariant.WATER) return;
        if (isInWaterOrBubble() || fluidWalking) return;
        if (isSnowingHere() || isTouchingSnowOrIce()) hurt(level().damageSources().freeze(), 1.0F);
    }

    /** 正下方一次降水的类型是雪（雪原、冰刺、冻洋都会下雪）。 */
    boolean isSnowingHere() {
        return level().isRaining() && level().canSeeSky(blockPosition())
                && level().getBiome(blockPosition()).value().getPrecipitationAt(blockPosition())
                == net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
    }

    /** 脚下一格 + 脚边一圈有没有冰/雪方块；头顶的冰盖不算（水史莱姆本来就该躲在水下）。 */
    boolean isTouchingSnowOrIce() {
        BlockPos feet = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 0, 1))) {
            if (isSnowOrIce(level().getBlockState(pos))) return true;
        }
        return false;
    }

    static boolean isSnowOrIce(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.FROSTED_ICE);
    }

    private boolean isStandingInFire() {
        BlockState state = level().getBlockState(blockPosition());
        return state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock;
    }

    private void tickTimedLife(long now) {
        if (variant == ElementalSlimeVariant.LIGHT
                && (tickCount >= 3 * 60 * 20 || level().getRawBrightness(blockPosition(), level().getSkyDarken()) < 13)) {
            discardWithoutSplitting();
        } else if (variant == ElementalSlimeVariant.SOUND && soundExpiresAt > 0 && now >= soundExpiresAt) {
            discardWithoutSplitting();
        }
    }

    private void tickTamedProduction(long now) {
        if (!isTamed()) return;
        Player owner = ownerUUID().map(level()::getPlayerByUUID).orElse(null);
        if (variant == ElementalSlimeVariant.METAL && now >= nextGiftTick && owner != null && distanceToSqr(owner) <= 16 * 16) {
            nextGiftTick = now + 10 * 60 * 20L;
            if (getRandom().nextFloat() < 0.05F) spawnAtLocation(getRandom().nextBoolean() ? Items.GOLD_INGOT : Items.IRON_INGOT);
            else spawnAtLocation(new ItemStack(Items.GOLD_NUGGET, 1 + getRandom().nextInt(3)));
        }
        if (variant == ElementalSlimeVariant.WIND && now >= nextDailyProductTick) {
            nextDailyProductTick = now + 24_000L;
            spawnAtLocation(GeneratedMod.MOD_ITEMS.get(variant.powderId()).get());
        }
    }

    private void tickElementEnvironment() {
        BlockPos feet = blockPosition();
        if (variant == ElementalSlimeVariant.ICE) {
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 0, 1))) {
                if (level().getBlockState(pos).is(Blocks.WATER)) level().setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
            }
            if (inRain() && level().getBlockState(feet).isAir()
                    && level().getBlockState(feet.below()).isSolid()) {
                level().setBlockAndUpdate(feet, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
            }
        }
    }

    boolean isColdBiome() {
        return level().getBiome(blockPosition()).value().coldEnoughToSnow(blockPosition());
    }

    private void electrifyNearby() {
        AABB area = getBoundingBox().inflate(3.0);
        // 「周围 3 格内所有实体」：仍然照打同类，但创造/旁观模式与和平难度下的玩家照规矩放过。
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != this && entity.isAlive() && validTerritoryTarget(entity))) {
            float damage = ElementReactionSystem.elementalDamage(this, target, ElementType.LIGHTNING,
                    2.0F * tier().damageScale());
            SpellDamage.hitResolvedElement(level(), this, target, ElementType.LIGHTNING.id(), damage);
        }
    }

    /**
     * 目标栏清理。原版 Mob 自己从不调用 setTarget(null)，靠 TargetGoal.stop() 在
     * 「目标不可攻击」时顺手清掉；元素史莱姆全是自定义 Goal，没有这条路径，
     * 于是一旦目标被法术打死（或玩家进创造、史莱姆被驯服），getTarget() 会永远
     * 留着那个实体。而施法、追击、闲逛三个会动的 Goal 分别要求「目标可攻击」或
     * 「没有目标」，全都不成立 —— 表现就是放完法术原地站着不动。这里每刻兜一次。
     */
    private void tickTargetValidity() {
        LivingEntity target = getTarget();
        // 判据用 canAttack，和施法 / 追击 Goal 的启动条件完全一致，避免出现
        // 「Goal 认为不能打、但这里认为目标有效」的第三种状态。
        if (target != null && !canAttack(target)) setTarget(null);
    }

    /** 闲逛 Goal 用：目标为空或者已经不可攻击，都算可以自由活动。 */
    boolean hasAttackableTarget() {
        LivingEntity target = getTarget();
        return target != null && canAttack(target);
    }

    public boolean isSpellReady() {
        LivingEntity target = getTarget();
        if (target == null || isTamed() || level().getGameTime() < nextSpellTick) return false;
        // 必须和 SpellGoal.canUse 的射程/视线判定完全一致。旧版只判冷却，
        // 于是「冷却好了但目标在 16 格以外或被挡住」时法术 Goal 不跑、
        // 近战 Goal 又因为 !isSpellReady() 让位，史莱姆锁敌后就会站在原地看着目标。
        return distanceToSqr(target) <= 16.0 * 16.0 && getSensing().hasLineOfSight(target);
    }

    /** 元素史莱姆实际使用的施法间隔：法术冷却 × 2，频率为设计值的一半。 */
    private int spellCooldownTicks() {
        return variant.basicSpell().cooldownTicks() * SPELL_COOLDOWN_MULTIPLIER;
    }

    public void castBasicSpell(LivingEntity target) {
        if (!isSpellReady() || target == null || !validCombatTarget(target)) return;
        float damage = variant.basicSpellDamage() * SPELL_DIFFICULTY[difficultyIndex()] * tier().damageScale();
        // 这一发没有弹道、瞬间命中，招架系统照样能接：处于招架姿态且距离够近就弹开，
        // 左键窗口内还算完美招架。被弹掉时伤害和元素附着一起取消，只留碎裂特效。
        if (target instanceof ServerPlayer parryTarget
                && BattleQiParry.parryInstantSpell(parryTarget, this, variant.damageElementId(), damage)) {
            nextSpellTick = level().getGameTime() + spellCooldownTicks();
            return;
        }
        if (variant == ElementalSlimeVariant.MANA) {
            damage = ElementReactionSystem.arcaneDamage(this, target, damage);
            SpellDamage.hitResolvedElement(level(), this, target, MagicEquipment.ARCANE, damage);
        } else {
            damage = ElementReactionSystem.elementalDamage(this, target, variant.element(), damage);
            SpellDamage.hitResolvedElement(level(), this, target, variant.element().id(), damage);
        }
        SpellVisualPayload.send(this, variant.basicSpell(), SpellVisualPayload.RELEASE, target.getId(),
                getEyePosition(), target.getEyePosition(), 10);
        nextSpellTick = level().getGameTime() + spellCooldownTicks();
        // 这一发把目标打死了就当场放开，下一刻直接回闲逛，不用再等 tickTargetValidity 兜底。
        if (!target.isAlive()) setTarget(null);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        if (!(entity instanceof LivingEntity target) || !validCombatTarget(target) || isTamed()) return false;
        float tierScale = tier().damageScale();
        int index = difficultyIndex();
        boolean physical = target.hurt(level().damageSources().mobAttack(this), PHYSICAL_DAMAGE[index] * tierScale);
        float elemental = ELEMENT_DAMAGE[index] * tierScale;
        boolean magic;
        if (variant == ElementalSlimeVariant.MANA) {
            elemental = ElementReactionSystem.arcaneDamage(this, target, elemental);
            magic = SpellDamage.hitResolvedElement(level(), this, target, MagicEquipment.ARCANE, elemental);
        } else {
            elemental = ElementReactionSystem.elementalDamage(this, target, variant.element(), elemental);
            magic = SpellDamage.hitResolvedElement(level(), this, target, variant.element().id(), elemental);
        }
        if (physical || magic) {
            setLastHurtMob(target);
            // 原版接触伤害被关掉了，近战命中时自己补一声攻击音，不然打人没反馈。
            playSound(voice(), getSoundVolume(), voicePitch(1.05F));
        }
        return physical || magic;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && validCombatTarget(target);
    }

    /**
     * 原版史莱姆是「撞到就掉血」，但元素史莱姆绝大多数是中立生物：
     * 没被激怒时玩家从旁边走过去不该掉血，所以关掉接触伤害，近战统一走 doHurtTarget。
     */
    @Override
    protected boolean isDealsDamage() {
        return false;
    }

    boolean validTerritoryTarget(LivingEntity target) {
        if (target == null || target == this || !target.isAlive() || isTamed()) return false;
        if (isGoblinAlly()) return goblinAllyTarget(target);
        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (level().getDifficulty() == Difficulty.PEACEFUL) return false;
        }
        // 同类永不互相攻击；雷史莱姆只是对其他生物更暴躁，不再把同类当目标。
        if (target instanceof ElementalSlime other && other.variant() == variant) return false;
        return !ownerUUID().filter(target.getUUID()::equals).isPresent();
    }

    /**
     * 与哥布林结盟后的敌人只有哥布林的对手：玩家、铁傀儡、村民和灾厄村民。
     * 哥布林和其他结盟史莱姆永远不在名单里，避免营地内部误伤。
     */
    private boolean goblinAllyTarget(LivingEntity target) {
        if (target instanceof Goblin) return false;
        if (target instanceof ElementalSlime other && other.isGoblinAlly()) return false;
        if (target instanceof Player player)
            return !player.isCreative() && !player.isSpectator()
                    && level().getDifficulty() != Difficulty.PEACEFUL;
        return target instanceof IronGolem || target instanceof Villager || target instanceof Raider;
    }

    private boolean validCombatTarget(LivingEntity target) {
        return validTerritoryTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof ElementalSlime other && other.variant() == variant) return false;
        String element = SpellDamage.elementIdOf(source);
        if (variant == ElementalSlimeVariant.SOUND) {
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return fleeFromDamage(source, amount);
            if (ElementType.LIGHTNING.id().equals(element) || source.is(DamageTypeTags.IS_LIGHTNING))
                return fleeFromDamage(source, 1.0F);
            return false;
        }
        if (isElementImmune(element, source)) {
            if (variant == ElementalSlimeVariant.POISON && (ElementType.POISON.id().equals(element)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC))) heal(amount);
            if (variant == ElementalSlimeVariant.LIGHTNING) {
                // 雷属性法术与感电反应都恢复 2.5% 最大生命；原版闪电直击则按设计回满并给吸收。
                if (source.is(DamageTypeTags.IS_LIGHTNING)) lightningBlessing();
                else if (ElementType.LIGHTNING.id().equals(element)) healPercent(HEAL_FRACTION);
            }
            return false;
        }
        if (variant == ElementalSlimeVariant.MANA && SpellDamage.isSpell(source)) amount *= 0.20F;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) amount *= explosionMultiplier();
        // 金史莱姆的「融化」：岩浆、火焰方块、身上的火，单独按 85% 易伤结算，不再叠火焰易伤。
        boolean melting = variant == ElementalSlimeVariant.METAL && isMolten(source);
        if (melting) amount *= 1.85F;
        else if (element != null) amount *= elementalMultiplier(element);
        if (variant == ElementalSlimeVariant.METAL && source.getDirectEntity() instanceof LivingEntity
                && !source.is(DamageTypeTags.IS_PROJECTILE) && !SpellDamage.isSpell(source)) amount *= 0.25F;
        // 光「全类型抗性 50%」、土「全抗性 25%」、风「全易伤 15%」不分伤害类型，物理也吃。
        amount *= globalResistance();
        boolean hurt = super.hurt(source, amount);
        if (hurt && variant == ElementalSlimeVariant.NATURE
                && (ElementType.LIGHTNING.id().equals(element) || source.is(DamageTypeTags.IS_LIGHTNING))) tryReplicate();
        return hurt;
    }

    private boolean fleeFromDamage(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt) discardWithoutSplitting();
        return hurt;
    }

    private boolean isElementImmune(String element, DamageSource source) {
        if (variant == ElementalSlimeVariant.FIRE && (ElementType.FIRE.id().equals(element) || source.is(DamageTypeTags.IS_FIRE))) return true;
        if (variant == ElementalSlimeVariant.ICE && (ElementType.ICE.id().equals(element) || source.is(DamageTypeTags.IS_FREEZING))) return true;
        if (variant == ElementalSlimeVariant.LIGHTNING && (ElementType.LIGHTNING.id().equals(element) || source.is(DamageTypeTags.IS_LIGHTNING))) return true;
        if (variant == ElementalSlimeVariant.MANA && MagicEquipment.ARCANE.equals(element)) return true;
        return variant.element() != null && variant.element().id().equals(element);
    }

    /** 供危险躲避 AI 判断：只列设计稿明确的元素易伤；风元素的“全易伤”会躲所有其他元素。 */
    boolean isVulnerableTo(ElementType incoming) {
        if (incoming == null || incoming == variant.element()) return false;
        if (variant == ElementalSlimeVariant.WIND) return true;
        return switch (variant) {
            case FIRE -> incoming == ElementType.WATER;
            case WATER -> incoming == ElementType.ICE;
            case ICE -> incoming == ElementType.FIRE;
            case LIGHTNING -> incoming == ElementType.FIRE;
            case NATURE -> incoming == ElementType.FIRE;
            case POISON -> incoming == ElementType.LIGHT;
            case METAL -> incoming == ElementType.FIRE || incoming == ElementType.LIGHTNING;
            case SOUND -> incoming == ElementType.LIGHTNING;
            default -> false;
        };
    }

    /** 会真实造成环境伤害的方块/液体；元素免疫会把对应环境从危险清单移除。 */
    boolean isDangerousEnvironment(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE)) return true;
        // 水史莱姆会被冰雪持续磨血，所以整类冰雪方块对它都是危险区（包括薄雪层）。
        if (variant == ElementalSlimeVariant.WATER && isSnowOrIce(state)) return true;
        if (state.is(Blocks.POWDER_SNOW)) return variant != ElementalSlimeVariant.ICE;
        if (fluid.is(FluidTags.WATER)) return variant == ElementalSlimeVariant.FIRE;
        if (fluid.is(FluidTags.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(state))
            return variant != ElementalSlimeVariant.FIRE;
        return false;
    }

    private static boolean isMolten(DamageSource source) {
        return source.is(DamageTypes.LAVA) || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE);
    }

    private float globalResistance() {
        return switch (variant) {
            case LIGHT -> 0.50F;
            case EARTH -> 0.75F;
            case WIND -> 1.15F;
            default -> 1.0F;
        };
    }

    private float elementalMultiplier(String element) {
        return switch (variant) {
            case FIRE -> ElementType.WATER.id().equals(element) ? 1.75F : 1.0F;
            case WATER -> ElementType.ICE.id().equals(element) ? 1.75F : 1.0F;
            case ICE -> ElementType.FIRE.id().equals(element) ? 1.75F : 1.0F;
            case LIGHTNING -> ElementType.FIRE.id().equals(element) ? 1.45F : 1.0F;
            case POISON -> ElementType.LIGHT.id().equals(element) ? 1.75F : 1.0F;
            case METAL -> ElementType.FIRE.id().equals(element) ? 1.25F
                    : ElementType.LIGHTNING.id().equals(element) ? 1.35F : 1.0F;
            case NATURE -> ElementType.WATER.id().equals(element) ? 0.50F
                    : ElementType.FIRE.id().equals(element) ? 1.75F : 1.0F;
            default -> 1.0F;
        };
    }

    private float explosionMultiplier() {
        return switch (variant) {
            case FIRE -> 0.75F;
            case WATER -> 0.01F;
            case LIGHTNING -> 1.50F;
            case METAL -> 1.30F;
            default -> 1.0F;
        };
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        if (variant == ElementalSlimeVariant.MANA) return false;
        if (variant == ElementalSlimeVariant.POISON && effect.is(net.minecraft.world.effect.MobEffects.POISON)) {
            if (!level().isClientSide) healPercent(HEAL_FRACTION * (effect.getAmplifier() + 1));
            return false;
        }
        return super.canBeAffected(effect);
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        if (variant == ElementalSlimeVariant.LIGHTNING) {
            lightningBlessing();
            return;
        }
        super.thunderHit(level, bolt);
        if (variant == ElementalSlimeVariant.NATURE) tryReplicate();
    }

    private void lightningBlessing() {
        setHealth(getMaxHealth());
        setAbsorptionAmount(Math.max(getAbsorptionAmount(), 10.0F));
        clearFire();
    }

    /** 感电反应可能由水属性那一击触发，不能只靠伤害源里的 lightning 标签识别。 */
    void absorbElectroCharged() {
        if (variant == ElementalSlimeVariant.LIGHTNING) healPercent(HEAL_FRACTION);
    }

    private void tryReplicate() {
        long now = level().getGameTime();
        if (!(level() instanceof ServerLevel server) || replicationLimit <= 0 || now < replicationCooldown
                || getRandom().nextFloat() >= 0.50F) return;
        replicationCooldown = now + 5 * 20L;
        ElementalSlime copy = GeneratedMod.elementalSlimeType(variant).create(server);
        if (copy == null) return;
        copy.moveTo(getX() + (getRandom().nextDouble() - 0.5) * 1.5, getY(),
                getZ() + (getRandom().nextDouble() - 0.5) * 1.5, getYRot(), 0);
        copy.setTier(tier(), true);
        copy.setReplicationLimit(replicationLimit - 1);
        copy.initialized = true;
        server.addFreshEntity(copy);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isTamed()) return tamedInteract(player, hand, stack);
        // 野生史莱姆拴不住：原版会在 canBeLeashed 返回 false 时静默失败，这里补一句提醒。
        if (stack.is(Items.LEAD)) {
            if (!level().isClientSide) player.displayClientMessage(Component.translatable(
                    "entity.dimensional_mana.elemental_slime.leash_denied", getDisplayName()), true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // 特级魔晶是魔素史莱姆唯一认的东西：右键直接驯服，不需要反复尝试。
        if (variant == ElementalSlimeVariant.MANA
                && stack.is(GeneratedMod.MOD_ITEMS.get("special_mana_crystal").get())) {
            if (!level().isClientSide) {
                consumeOne(player, stack);
                tame(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (variant == ElementalSlimeVariant.METAL && stack.is(Items.IRON_INGOT)) {
            if (!level().isClientSide) {
                consumeOne(player, stack);
                if (getRandom().nextFloat() < 0.50F) tame(player);
                else player.displayClientMessage(Component.translatable(
                        "entity.dimensional_mana.metal_elemental_slime.refuse"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (variant == ElementalSlimeVariant.WIND && stack.is(Items.WIND_CHARGE)) {
            if (!level().isClientSide) {
                consumeOne(player, stack);
                tame(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (variant == ElementalSlimeVariant.EARTH) {
            int points = earthFoodPoints(stack);
            if (points > 0) {
                if (!level().isClientSide) {
                    consumeOne(player, stack);
                    earthFed = Math.min(EARTH_TAME_POINTS, earthFed + points);
                    if (earthFed >= EARTH_TAME_POINTS) tame(player);
                    else player.displayClientMessage(Component.translatable(
                            "entity.dimensional_mana.earth_elemental_slime.fed", earthFed, EARTH_TAME_POINTS), true);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        // 空手右键：把「喂什么」直接告诉玩家，不然很容易以为驯服坏了。
        if (stack.isEmpty() && !level().isClientSide) hintTaming(player);
        return super.mobInteract(player, hand);
    }

    /** 已驯服个体的交互：土史莱姆装鞍/骑乘，其余给一句状态提示，避免玩家反复喂食以为失效。 */
    private InteractionResult tamedInteract(Player player, InteractionHand hand, ItemStack stack) {
        if (variant == ElementalSlimeVariant.EARTH && isOwnedBy(player)) {
            if (!isSaddled() && stack.is(Items.SADDLE)) {
                if (!level().isClientSide) {
                    consumeOne(player, stack);
                    entityData.set(SADDLED, true);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (isSaddled() && !player.isSecondaryUseActive()) {
                if (!level().isClientSide) player.startRiding(this);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        if (stack.isEmpty() && !level().isClientSide) {
            // 魔素史莱姆额外说明它的「随身魔素恢复」光环，不然玩家不知道驯服它有什么用。
            player.displayClientMessage(variant == ElementalSlimeVariant.MANA
                    ? Component.translatable("entity.dimensional_mana.mana_elemental_slime.aura")
                    : Component.translatable("entity.dimensional_mana.elemental_slime.tamed_status",
                            getDisplayName()), true);
        }
        return super.mobInteract(player, hand);
    }

    private void hintTaming(Player player) {
        switch (variant) {
            case MANA -> player.displayClientMessage(Component.translatable(
                    "entity.dimensional_mana.mana_elemental_slime.hint"), true);
            case METAL -> player.displayClientMessage(Component.translatable(
                    "entity.dimensional_mana.metal_elemental_slime.hint"), true);
            case EARTH -> player.displayClientMessage(Component.translatable(
                    "entity.dimensional_mana.earth_elemental_slime.hint", earthFed, EARTH_TAME_POINTS), true);
            case WIND -> player.displayClientMessage(Component.translatable(
                    "entity.dimensional_mana.wind_elemental_slime.hint"), true);
            case SOUND -> player.displayClientMessage(Component.translatable(
                    "entity.dimensional_mana.sound_elemental_slime.hint"), true);
            default -> { }
        }
    }

    /** 土史莱姆的喂食点数：泥土类 1 点、圆石类 2 点，所以 10 泥土 = 5 圆石，而且可以混着喂。 */
    private static int earthFoodPoints(ItemStack stack) {
        if (stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT) || stack.is(Items.ROOTED_DIRT)
                || stack.is(Items.GRASS_BLOCK) || stack.is(Items.PODZOL) || stack.is(Items.MYCELIUM)) return 1;
        if (stack.is(Items.COBBLESTONE) || stack.is(Items.MOSSY_COBBLESTONE)) return 2;
        return 0;
    }

    private static void consumeOne(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    /** 只有被驯服的史莱姆才让人用栓绳牵走；野生的会挣脱。 */
    @Override
    public boolean canBeLeashed() {
        return isTamed();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return variant == ElementalSlimeVariant.EARTH && isTamed() && isSaddled()
                && passenger instanceof Player && getPassengers().isEmpty();
    }

    /**
     * 原版史莱姆的乘客挂点偏高；旧版实体基准尺寸又与模型不一致，幼崽和成熟体看起来都悬空。
     * 按当前真实碰撞箱高度放到身体上部，并让骑手略微陷入柔软身体，三档体型使用同一比例。
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger,
                                                net.minecraft.world.entity.EntityDimensions dimensions,
                                                float scale) {
        return new Vec3(0.0, dimensions.height() * 0.72F + 0.05F * scale, 0.0);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        if (variant == ElementalSlimeVariant.EARTH && isSaddled() && getFirstPassenger() instanceof Player player
                && isOwnedBy(player)) return player;
        return null;
    }

    @Override
    public void travel(Vec3 input) {
        LivingEntity rider = getControllingPassenger();
        if (rider != null && isAlive()) {
            rideTravel(rider);
            return;
        }
        Vec3 movement = input;
        if ((variant == ElementalSlimeVariant.FIRE && isInLava())
                || (variant == ElementalSlimeVariant.WATER && isInWaterOrBubble())
                // 「雪地速度 +50%」：寒冷群系和脚下就是雪/冰方块都算，不然雪原边缘会跑不起来。
                || (variant == ElementalSlimeVariant.ICE && (isColdBiome() || standsOnSnowOrIce()))) {
            movement = input.scale(1.5);
        }
        super.travel(movement);
    }

    /**
     * 骑乘中的土史莱姆不再像船那样每刻平滑加速，而是「蹲一下、蹦起来、落地、再蹦」。
     *
     * <p>原版只在控制端（骑手自己的客户端）调用 travel，另一端清零速度靠位置同步跟随，
     * 所以冲量只在 isControlledByLocalInstance() 时施加，不会两端各跳一次。</p>
     */
    private void rideTravel(LivingEntity rider) {
        setYRot(rider.getYRot());
        yRotO = getYRot();
        setXRot(rider.getXRot() * 0.5F);

        // 掉进水里时不硬蹦：交给原版液体推进，玩家还能把坐骑慢慢游回岸边。
        if (!onGround() && isInWaterOrBubble()) {
            setSpeed((float)(getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.7));
            super.travel(new Vec3(rider.xxa * 0.6, 0.0, rider.zza * 0.6));
            return;
        }

        if (isControlledByLocalInstance()) {
            float forward = rider.zza;
            float strafe = rider.xxa;
            boolean wantsMove = Math.abs(forward) > 0.05F || Math.abs(strafe) > 0.05F;
            if (onGround()) {
                if (rideHopDelay > 0) rideHopDelay--;
                if (wantsMove && rideHopDelay <= 0) {
                    rideHop(forward, strafe);
                } else {
                    // 蓄力/松手时把横向速度压掉，避免落地后还像船一样往前滑。
                    Vec3 motion = getDeltaMovement();
                    setDeltaMovement(motion.x * 0.3, motion.y, motion.z * 0.3);
                }
            }
        }
        // 冲量已经设好，这里只让原版处理重力、空气阻力和碰撞。
        super.travel(Vec3.ZERO);
    }

    /** 朝骑手指的方向蹦一下：水平冲量 + 原版跳跃高度，落地后由 rideHopDelay 控制下一跳。 */
    private void rideHop(float forward, float strafe) {
        float yaw = getYRot() * ((float)Math.PI / 180.0F);
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        // 和 Entity#getInputVector 同一套方向：前方 (-sin, cos)，左方 (cos, sin)。
        double dx = strafe * cos - forward * sin;
        double dz = forward * cos + strafe * sin;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) return;
        dx /= length;
        dz /= length;
        // 体型越大蹦得越远；后退只给一半多一点的力道，免得倒着飞。
        double power = (0.38 + 0.045 * getSize()) * (forward < 0.0F ? 0.55 : 1.0);
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(dx * power, motion.y, dz * power);
        jumpFromGround();
        playSlimeJumpSound();
        rideHopDelay = 3 + getRandom().nextInt(3);
    }

    private boolean standsOnSnowOrIce() {
        return isSnowOrIce(level().getBlockState(blockPosition().below()));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        // 风史莱姆被风托着，从 32 格高空落地也不掉血。
        if (variant == ElementalSlimeVariant.WIND) return false;
        boolean damaged = super.causeFallDamage(distance, multiplier, source);
        if (variant == ElementalSlimeVariant.EARTH && distance > 3 && !level().isClientSide) {
            for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.0),
                    entity -> entity != this && validCombatTarget(entity))) {
                target.hurt(level().damageSources().mobAttack(this), Math.max(2.0F, distance * 0.75F));
                target.knockback(1.0, getX() - target.getX(), getZ() - target.getZ());
            }
            level().levelEvent(2001, blockPosition(), net.minecraft.world.level.block.Block.getId(Blocks.DIRT.defaultBlockState()));
        }
        return damaged;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        ElementalSlimeTier tier = tier();
        int looting = 0;
        if (source.getEntity() instanceof LivingEntity killer) {
            var lookup = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            looting = EnchantmentHelper.getEnchantmentLevel(lookup.getOrThrow(Enchantments.LOOTING), killer);
        }
        float lootingMultiplier = 1.0F + 0.10F * looting;
        if (getRandom().nextFloat() < tier.powderChance() * lootingMultiplier)
            spawnAtLocation(new ItemStack(GeneratedMod.MOD_ITEMS.get(variant.powderId()).get(), tier.randomPowder(getRandom())));
        if (tier.lesserChance() > 0 && getRandom().nextFloat() < tier.lesserChance() * lootingMultiplier)
            spawnAtLocation(new ItemStack(GeneratedMod.MOD_ITEMS.get(variant.lesserFragmentId()).get(), tier.randomLesser(getRandom())));
        if (tier.mediumChance() > 0 && getRandom().nextFloat() < tier.mediumChance() * lootingMultiplier)
            spawnAtLocation(GeneratedMod.MOD_ITEMS.get(variant.mediumFragmentId()).get());
        if (isSaddled()) spawnAtLocation(Items.SADDLE);
    }

    private int difficultyIndex() {
        return switch (level().getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> 1;
            case NORMAL -> 2;
            case HARD -> 3;
        };
    }

    @Override
    protected boolean shouldDespawnInPeaceful() { return false; }

    private void discardWithoutSplitting() {
        if (getSize() > 1) setSize(1, false);
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (getSize() > 1) setSize(1, false);
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ElementalTier", tier().id());
        tag.putBoolean("ElementalTamed", isTamed());
        tag.putBoolean("GoblinAlly", isGoblinAlly());
        ownerUUID().ifPresent(uuid -> tag.putUUID("ElementalOwner", uuid));
        tag.putBoolean("ElementalSaddled", isSaddled());
        tag.putLong("NextSpell", nextSpellTick);
        tag.putLong("NextGift", nextGiftTick);
        tag.putLong("NextProduct", nextDailyProductTick);
        tag.putLong("SoundExpires", soundExpiresAt);
        tag.putLong("ReplicationCooldown", replicationCooldown);
        tag.putInt("ReplicationLimit", replicationLimit);
        tag.putInt("EarthFed", earthFed);
        tag.putInt("MusicTicks", musicTicks);
        tag.putLong("SoundRewardReady", soundRewardReadyAt);
        tag.putBoolean("WindFloating", windFloating);
        tag.putLong("WindGroundUntil", windGroundUntil);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TIER, tag.getString("ElementalTier").isBlank() ? ElementalSlimeTier.MATURE.id() : tag.getString("ElementalTier"));
        entityData.set(TAMED, tag.getBoolean("ElementalTamed"));
        entityData.set(GOBLIN_ALLY, tag.getBoolean("GoblinAlly"));
        entityData.set(OWNER, tag.hasUUID("ElementalOwner") ? Optional.of(tag.getUUID("ElementalOwner")) : Optional.empty());
        entityData.set(SADDLED, tag.getBoolean("ElementalSaddled"));
        nextSpellTick = tag.getLong("NextSpell");
        nextGiftTick = tag.getLong("NextGift");
        nextDailyProductTick = tag.getLong("NextProduct");
        soundExpiresAt = tag.getLong("SoundExpires");
        replicationCooldown = tag.getLong("ReplicationCooldown");
        replicationLimit = tag.contains("ReplicationLimit") ? tag.getInt("ReplicationLimit") : 4;
        // 旧存档只有分开的泥土/圆石计数，读回来时按点数换算，避免玩家喂到一半的进度作废。
        if (tag.contains("EarthFed")) earthFed = tag.getInt("EarthFed");
        else earthFed = tag.getInt("EarthDirt") + 2 * tag.getInt("EarthCobble");
        musicTicks = tag.getInt("MusicTicks");
        soundRewardReadyAt = tag.getLong("SoundRewardReady");
        if (variant == ElementalSlimeVariant.WIND) {
            // 旧存档里的风史莱姆没有飞行状态，读档时按「50% 漂浮」重新决定一次。
            if (tag.contains("WindFloating")) {
                windFloating = tag.getBoolean("WindFloating");
                windGroundUntil = tag.getLong("WindGroundUntil");
            } else {
                initWindState();
            }
            windTarget = null;
            windPauseUntil = 0;
        }
        initialized = true;
        setTier(tier(), false);
    }
}
