package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 哥布林阵营的统一实体实现。
 *
 * <p>五种哥布林共用这一份 AI，具体数值与外观由 {@link GoblinVariant} 决定。核心玩法是
 * 「拾取背包」：它们会把地上的食物、铁、煤（首领还收金与钻石）塞进背包，血量低于一半时吃背包里的东西回血，
 * 捡到更好的武器会换下手中的粗制武器，死亡时背包会全部散落出来。</p>
 *
 * <p>所有技能只在服务端结算：斗气 / 魔素走 {@link ManaData}，控制效果走 {@link NewSpellSystem}。
 * 近战伤害统一按难度缩放（和平 25% / 简单 50% / 普通 75% / 困难 100%）。</p>
 */
public final class Goblin extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> CASTING =
            SynchedEntityData.defineId(Goblin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHARGING =
            SynchedEntityData.defineId(Goblin.class, EntityDataSerializers.BOOLEAN);
    /** 技能动作编号：0 无、1 战吼、2 跳劈起跳、3 落地砸击、4 蓄力斩击。客户端只读它来摆姿势。 */
    private static final EntityDataAccessor<Byte> ANIMATION =
            SynchedEntityData.defineId(Goblin.class, EntityDataSerializers.BYTE);
    /** 当前动作剩余刻数；归零时动作结束。 */
    private static final EntityDataAccessor<Integer> ANIMATION_TICKS =
            SynchedEntityData.defineId(Goblin.class, EntityDataSerializers.INT);
    public static final byte ANIM_NONE = 0;
    public static final byte ANIM_ROAR = 1;
    public static final byte ANIM_LEAP = 2;
    public static final byte ANIM_SLAM = 3;
    public static final byte ANIM_CHARGE = 4;
    private static final ResourceLocation GREED_ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "goblin_greed_armor");
    private static final ResourceLocation GREED_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "goblin_greed_speed");
    private static final ResourceLocation SLASH_ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "goblin_charge_exposed");
    private static final ResourceLocation DIFFICULTY_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "goblin_difficulty_damage");
    /** 首领贪婪最多吃 8 件金 / 钻石的加成，免得背包塞满后移速失控。 */
    private static final int GREED_CAP = 8;
    /** 蓄力斩击期间挨到这么多伤害就会被打断。 */
    private static final float SLASH_INTERRUPT_DAMAGE = 20.0F;
    /** 一只哥布林挨打后，求援半径内来支援的同族数量上限。 */
    private static final int SUPPORT_CAP = 24;
    /** 首领参战后，100 格内被召集的同族数量上限。 */
    private static final int RALLY_CAP = 48;
    /** 求援半径：一只哥布林挨打，附近 32 格的同族都会赶来。 */
    private static final double SUPPORT_RADIUS = 32.0;
    /** 首领召集半径：首领参战后，100 格内的同族都会来作战。 */
    private static final double CHIEF_RADIUS = 100.0;
    /** 萨满一次回血回复目标最大生命的 30%。 */
    private static final float HEAL_FRACTION = 0.30F;
    /** 与哥布林结盟的四种元素史莱姆：毒、木（自然）、风、水。 */
    private static final Set<ElementalSlimeVariant> ALLY_SLIME_TYPES = EnumSet.of(
            ElementalSlimeVariant.POISON, ElementalSlimeVariant.NATURE,
            ElementalSlimeVariant.WIND, ElementalSlimeVariant.WATER);
    private static final long DAY_TICKS = 24000L;
    /** 每天 0 点后的第一个小时是聚集、交换物品的时间。 */
    private static final long GATHER_WINDOW = 1200L;

    private final GoblinVariant variant;
    /** 拾取背包：只存在服务端，死亡时全部散落。 */
    private final List<ItemStack> backpack = new ArrayList<>();
    private int meleeCooldown;
    private int skillCooldown;
    private int eatCooldown;
    private int pickupCooldown;
    private int panicTicks;
    private int companionDeaths;
    private int companionCount = 1;
    private int companionScanCooldown;
    private int qiBoostTicks;
    private int rushTicks;
    private int chargeTicks;
    private float chargeDamageTaken;
    private int leapTicks;
    private Vec3 leapTarget;
    /** 弓箭手拉弓的剩余刻数；拉满才真正放箭，客户端才看得到「箭在弦上」。 */
    private int bowDrawTicks;
    private int castPoseTicks;
    private boolean selfDestructed;
    private boolean initialized;
    /** 求援冷却：被打一次最多喊一次人，免得连击时每下都扫一遍周围实体。 */
    private int alertCooldown;
    /** 首领召集冷却，比普通求援长，避免 100 格扫描被反复触发。 */
    private int rallyCooldown;
    /** 萨满驯服史莱姆的冷却。 */
    private int tameCooldown;
    /** 所属营地中心与每天 0 点的聚集点；野外巡逻队没有营地。 */
    private BlockPos homeCamp;
    private BlockPos gatherPoint;
    /** 幼崽成年的游戏时刻；新生成的幼崽在 4～8 天后长大。 */
    private long growUpAt;

    public Goblin(EntityType<? extends Goblin> type, Level level, GoblinVariant variant) {
        super(type, level);
        this.variant = variant;
        this.xpReward = switch (variant) {
            case YOUNG -> 3;
            case CHIEF -> 60;
            default -> 12;
        };
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0);
    }

    public GoblinVariant variant() {
        return variant;
    }

    public boolean isCasting() {
        return entityData.get(CASTING);
    }

    public boolean isChargingSlash() {
        return entityData.get(CHARGING);
    }

    /** 正在播放的技能动作编号，见 {@code ANIM_*} 常量。 */
    public byte animation() {
        return entityData.get(ANIMATION);
    }

    /** 技能动作剩余刻数，模型靠它算动作进度。 */
    public int animationTicks() {
        return entityData.get(ANIMATION_TICKS);
    }

    /** 当前变种的体型（幼崽 / 成年 / 首领），渲染器与护甲层按它选模型与贴图。 */
    public GoblinShape shape() {
        return variant.shape();
    }

    public List<ItemStack> backpack() {
        return backpack;
    }

    /** 把这只哥布林登记到某座营地：它会去聚集棚碰头，也算有家的哥布林。 */
    public void setHome(BlockPos camp, BlockPos gather) {
        this.homeCamp = camp == null ? null : camp.immutable();
        this.gatherPoint = gather == null ? null : gather.immutable();
    }

    public boolean hasHome() {
        return homeCamp != null;
    }

    /** 当前血量占最大生命的比例；萨满用它挑最该救的同伴。 */
    public float healthRatio() {
        return getMaxHealth() <= 0.0F ? 0.0F : getHealth() / getMaxHealth();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CASTING, false);
        builder.define(CHARGING, false);
        builder.define(ANIMATION, ANIM_NONE);
        builder.define(ANIMATION_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new CombatGoal());
        goalSelector.addGoal(3, new EatGoal());
        goalSelector.addGoal(4, new GatherGoal());
        goalSelector.addGoal(5, new PickupGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.85));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Raider.class, true));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        // 与哥布林结盟的元素史莱姆是同伴，不能误伤。
        if (target instanceof ElementalSlime slime && slime.isGoblinAlly()) return false;
        return !(target instanceof Goblin) && super.canAttack(target);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof ElementalSlime slime && slime.isGoblinAlly()) return true;
        return other instanceof Goblin || super.isAlliedTo(other);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        RandomSource random = level.getRandom();
        int health = variant.minHealth() + random.nextInt(variant.maxHealth() - variant.minHealth() + 1);
        if (getAttribute(Attributes.MAX_HEALTH) != null) getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        if (getAttribute(Attributes.ATTACK_DAMAGE) != null)
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(variant.attackDamage());
        if (getAttribute(Attributes.MOVEMENT_SPEED) != null)
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(variant.moveSpeed());
        setHealth(health);
        ManaData.setMobPools(this, Math.max(0.0, variant.mana()), Math.max(0, variant.battleQi()));
        equip(level);
        initialized = true;
        // 幼崽在 4～8 个游戏日后成年，时长按个体随机。
        if (variant == GoblinVariant.YOUNG)
            growUpAt = level.getLevel().getGameTime() + (4 + random.nextInt(5)) * DAY_TICKS;
        if (reason == MobSpawnType.STRUCTURE || variant == GoblinVariant.CHIEF) setPersistenceRequired();
        return result;
    }

    /** 按变种发装备；玩家的缴获率比原版高，方便收集这套粗糙装备。 */
    private void equip(ServerLevelAccessor level) {
        RandomSource random = getRandom();
        switch (variant) {
            // 幼崽、成年兵和萨满都会随机「裸装 / 布甲 / 铁甲」，还会随机缺一两件，
            // 这样模型库里那套没装备的基础哥布林和穿满装备的版本都会出现在营地里。
            case YOUNG -> {
                wearRandomArmor(random, 0.45F, 0.0F, 0.75F);
                setDropChance(EquipmentSlot.MAINHAND, 0.35F);
            }
            case WARRIOR -> {
                wearRandomArmor(random, 0.15F, 0.30F, 0.80F);
                setItemSlot(EquipmentSlot.MAINHAND, stack(RoughEquipment.CLEAVER));
                if (random.nextFloat() < 0.50F) setItemSlot(EquipmentSlot.OFFHAND, stack(RoughEquipment.BUCKLER));
                dropChances(0.45F);
            }
            case ARCHER -> {
                wearRandomArmor(random, 0.20F, 0.0F, 0.80F);
                setItemSlot(EquipmentSlot.MAINHAND, stack(RoughEquipment.BROKEN_BOW));
                backpack.add(new ItemStack(Items.ARROW, 6 + random.nextInt(10)));
                dropChances(0.35F);
            }
            case SHAMAN -> {
                wearRandomArmor(random, 0.15F, 0.0F, 0.75F);
                setItemSlot(EquipmentSlot.MAINHAND, stack(RoughEquipment.SKELETON_STAFF));
                dropChances(0.30F);
            }
            case CHIEF -> {
                wearSet(RoughSet.IRON);
                enchantArmor(level);
                ItemStack axe = stack(RoughEquipment.AXE);
                var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                axe.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 3);
                setItemSlot(EquipmentSlot.MAINHAND, axe);
                setDropChance(EquipmentSlot.MAINHAND, 0.60F);
                // 首领开局就带一点金子在背包里，贪婪被动一上来就有意义。
                backpack.add(new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(3)));
                if (random.nextFloat() < 0.35F) backpack.add(new ItemStack(Items.DIAMOND, 1));
            }
        }
    }

    /**
     * 随机穿一套粗糙护甲。
     *
     * @param noneChance 完全不穿的概率（此时显示基础哥布林模型）
     * @param ironChance 穿铁甲的概率；其余情况穿布甲
     * @param pieceChance 每件装备单独保留的概率，用来做出「只戴头盔」「只有胸甲」这类残装
     */
    private void wearRandomArmor(RandomSource random, float noneChance, float ironChance, float pieceChance) {
        if (random.nextFloat() < noneChance) return;
        RoughSet set = random.nextFloat() < ironChance ? RoughSet.IRON : RoughSet.CLOTH;
        String prefix = set == RoughSet.CLOTH ? "rough_cloth" : "rough_iron";
        boolean any = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (random.nextFloat() > pieceChance) continue;
            String piece = switch (slot) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                default -> "boots";
            };
            setItemSlot(slot, stack(prefix + "_" + piece));
            any = true;
        }
        // 极小概率四件全被跳过时补一件胸甲，避免「决定穿甲」的个体看起来完全没变。
        if (!any) setItemSlot(EquipmentSlot.CHEST, stack(prefix + "_chestplate"));
    }

    private void wearSet(RoughSet set) {
        String prefix = set == RoughSet.CLOTH ? "rough_cloth" : "rough_iron";
        setItemSlot(EquipmentSlot.HEAD, stack(prefix + "_helmet"));
        setItemSlot(EquipmentSlot.CHEST, stack(prefix + "_chestplate"));
        setItemSlot(EquipmentSlot.LEGS, stack(prefix + "_leggings"));
        setItemSlot(EquipmentSlot.FEET, stack(prefix + "_boots"));
    }

    private void enchantArmor(ServerLevelAccessor level) {
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = getItemBySlot(slot);
            if (piece.isEmpty()) continue;
            piece.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 2);
            setItemSlot(slot, piece);
        }
    }

    private void dropChances(float chance) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) setDropChance(slot, chance);
        }
        setDropChance(EquipmentSlot.MAINHAND, chance + 0.15F);
        setDropChance(EquipmentSlot.OFFHAND, chance + 0.15F);
    }

    private static ItemStack stack(String id) {
        var item = GeneratedMod.MOD_ITEMS.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
    }

    // ------------------------------------------------------------ 每刻逻辑

    /**
     * 推进挥臂计时。
     *
     * <p>1.21.1 里 {@code swingTime} / {@code attackAnim} 只在 {@code Monster.aiStep} 和
     * {@code Player.serverAiStep} 里推进；哥布林直接继承 {@code PathfinderMob}，少这一句的话
     * {@code swing()} 只是点亮了「正在挥」的标志位，动画进度永远停在 0，
     * 模型里写好的挥臂姿势一帧都不会出现。</p>
     */
    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    /** 一次挥臂要几刻：成年哥布林比原版默认的 6 刻稍慢，首领更沉、更慢。 */
    @Override
    public int getCurrentSwingDuration() {
        return switch (variant) {
            case CHIEF -> 13;
            case WARRIOR, ARCHER -> 9;
            default -> 6;
        };
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (!initialized) {
            // 从存档读回来的哥布林：装备、生命与斗气都已在 NBT 里，只在确实空手时补一次装备，
            // 免得每读一次档就凭空多出一套护甲、背包物品翻倍。
            initialized = true;
            if (getItemBySlot(EquipmentSlot.HEAD).isEmpty() && getMainHandItem().isEmpty()) equip(level);
        }
        if (meleeCooldown > 0) meleeCooldown--;
        if (skillCooldown > 0) skillCooldown--;
        if (eatCooldown > 0) eatCooldown--;
        if (pickupCooldown > 0) pickupCooldown--;
        if (qiBoostTicks > 0) qiBoostTicks--;
        if (rushTicks > 0) rushTicks--;
        if (panicTicks > 0) panicTicks--;
        if (alertCooldown > 0) alertCooldown--;
        if (rallyCooldown > 0) rallyCooldown--;
        if (tameCooldown > 0) tameCooldown--;
        if (companionScanCooldown > 0) companionScanCooldown--;
        tickAnimation();
        if (castPoseTicks > 0 && --castPoseTicks == 0 && chargeTicks <= 0) entityData.set(CASTING, false);
        if (animation() == ANIM_NONE && castPoseTicks == 0 && chargeTicks <= 0 && bowDrawTicks <= 0)
            entityData.set(CASTING, false);
        tickBowDraw(level);

        if (variant == GoblinVariant.YOUNG && tickCount % 20 == 0) {
            if (growUpAt <= 0) growUpAt = level.getGameTime() + (4 + getRandom().nextInt(5)) * DAY_TICKS;
            else if (level.getGameTime() >= growUpAt) {
                growUp(level);
                return;
            }
        }
        if (variant == GoblinVariant.SHAMAN && tickCount % 40 == 0 && tameCooldown <= 0) tryTameSlime(level);
        // 没在打架的萨满也会去救重伤的同伴，不用等进入战斗。
        if (variant == GoblinVariant.SHAMAN && tickCount % 20 == 0 && skillCooldown <= 0
                && getTarget() == null && mana() >= 100.0) healAllies(level);

        if (tickCount % 20 == 0) {
            regenerate();
            if (variant == GoblinVariant.CHIEF) applyGreed();
            scanCompanions(level);
        }
        if (chargeTicks > 0) {
            tickChargedSlash(level);
            return;
        }
        if (leapTicks > 0) {
            tickLeap(level);
            return;
        }
        tickShamanSelfDestruct(level);
        tickYoungPanic(level);
    }

    /** 播一个技能动作：服务端写同步数据，客户端模型照着摆姿势。 */
    private void playAnimation(byte animation, int ticks) {
        entityData.set(ANIMATION, animation);
        entityData.set(ANIMATION_TICKS, ticks);
    }

    private void clearAnimation() {
        entityData.set(ANIMATION, ANIM_NONE);
        entityData.set(ANIMATION_TICKS, 0);
    }

    private void tickAnimation() {
        int ticks = animationTicks();
        if (ticks <= 0) return;
        ticks--;
        entityData.set(ANIMATION_TICKS, ticks);
        if (ticks == 0) entityData.set(ANIMATION, ANIM_NONE);
    }

    /**
     * 拉弓：先举弓 12 刻再放箭。
     *
     * <p>这样客户端能通过 {@code isUsingItem()} 把破损弓切成拉弓贴图，玩家才看得见弦上的箭；
     * 之前是瞬间射箭，弓永远停在「空弓」那一帧。</p>
     */
    private void cancelBowDraw() {
        bowDrawTicks = 0;
        if (isUsingItem() && getUseItem().getItem() instanceof BowItem) stopUsingItem();
        entityData.set(CASTING, false);
    }

    private void tickBowDraw(ServerLevel level) {
        if (bowDrawTicks <= 0) return;
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || target.level() != level || !canAttack(target)
                || !(getMainHandItem().getItem() instanceof BowItem) || !isUsingItem()
                || NewSpellSystem.immobilized(this) || panickingNow()
                || distanceToSqr(target) >= 400.0 || !hasLineOfSight(target)) {
            cancelBowDraw();
            return;
        }
        if (--bowDrawTicks > 0) return;
        cancelBowDraw();
        shootArrow(target);
    }

    private void startBowDraw() {
        if (bowDrawTicks > 0 || !(getMainHandItem().getItem() instanceof BowItem)) return;
        bowDrawTicks = 12;
        startUsingItem(InteractionHand.MAIN_HAND);
    }

    private void regenerate() {
        if (variant == GoblinVariant.SHAMAN) {
            if (variant.regenPerSecond() > 0.0) ManaData.restoreMana(this, variant.regenPerSecond(), false);
        } else if (variant.battleQi() > 0 && variant.regenPerSecond() > 0.0) {
            ManaData.Snapshot snapshot = ManaData.get(this);
            double multiplier = qiBoostTicks > 0 ? 4.0 : 1.0;
            int gained = (int)Math.round(variant.regenPerSecond() * multiplier);
            if (gained > 0) ManaData.setBattleQi(this, Math.min(snapshot.maxBattleQi(), snapshot.battleQi() + gained));
        }
    }

    /** 首领被动「贪婪」：背包里每 1 件金 / 钻石，护甲与移速各 +10%。 */
    private void applyGreed() {
        int treasures = Math.min(GREED_CAP, countTreasure());
        applyModifier(getAttribute(Attributes.ARMOR), GREED_ARMOR_ID, treasures * 0.10);
        applyModifier(getAttribute(Attributes.MOVEMENT_SPEED), GREED_SPEED_ID, treasures * 0.10);
    }

    private int countTreasure() {
        int count = 0;
        for (ItemStack stack : backpack) {
            if (stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD) || stack.is(Items.GOLD_BLOCK)
                    || stack.is(Items.GOLD_NUGGET) || stack.is(Items.DIAMOND) || stack.is(Items.DIAMOND_BLOCK)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void applyModifier(AttributeInstance instance, ResourceLocation id, double amount) {
        if (instance == null) return;
        if (Math.abs(amount) < 0.0001) {
            if (instance.getModifier(id) != null) instance.removeModifier(id);
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void scanCompanions(ServerLevel level) {
        companionCount = Math.max(1, level.getEntitiesOfClass(Goblin.class,
                getBoundingBox().inflate(24.0), GOBLIN_ALIVE).size());
    }

    private static final java.util.function.Predicate<Goblin> GOBLIN_ALIVE = LivingEntity::isAlive;

    // ------------------------------------------------------------ 群体行为

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && level() instanceof ServerLevel server
                && source.getEntity() instanceof LivingEntity attacker && attacker != this
                && !(attacker instanceof Goblin)) {
            callForHelp(server, attacker);
        }
        return hurt;
    }

    /** 挨打后喊人：32 格内的同族都会扑向攻击者；首领在场就召集 100 格。 */
    private void callForHelp(ServerLevel level, LivingEntity attacker) {
        if (alertCooldown > 0) return;
        alertCooldown = 60;
        level.playSound(null, blockPosition(),
                GoblinSounds.get(variant == GoblinVariant.CHIEF ? "roar" : "panic"),
                SoundSource.HOSTILE, 1.3F, variant == GoblinVariant.YOUNG ? 1.5F : 1.1F);
        int count = 0;
        for (Goblin ally : level.getEntitiesOfClass(Goblin.class,
                getBoundingBox().inflate(SUPPORT_RADIUS, 16.0, SUPPORT_RADIUS), GOBLIN_ALIVE)) {
            if (ally == this || !ally.canAttack(attacker)) continue;
            if (count++ >= SUPPORT_CAP) break;
            ally.joinFight(attacker);
        }
        Goblin chief = variant == GoblinVariant.CHIEF ? this : nearestChief(level);
        if (chief != null) chief.rally(level, attacker);
        alertAlliedSlimes(level, SUPPORT_RADIUS, attacker);
    }

    /** 被同伴喊来打架：锁定目标，并压住自己的求援冷却，避免连锁刷屏。 */
    private void joinFight(LivingEntity attacker) {
        if (panickingNow() || !canAttack(attacker)) return;
        setTarget(attacker);
        setLastHurtByMob(attacker);
        alertCooldown = Math.max(alertCooldown, 40);
    }

    private Goblin nearestChief(ServerLevel level) {
        return level.getEntitiesOfClass(Goblin.class,
                        getBoundingBox().inflate(SUPPORT_RADIUS, 24.0, SUPPORT_RADIUS),
                        goblin -> goblin != this && goblin.variant == GoblinVariant.CHIEF && goblin.isAlive())
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    /** 首领召集：100 格内的同族与结盟史莱姆一起作战。 */
    private void rally(ServerLevel level, LivingEntity attacker) {
        if (rallyCooldown > 0) return;
        rallyCooldown = 120;
        level.playSound(null, blockPosition(), GoblinSounds.get("roar"), SoundSource.HOSTILE, 1.8F, 0.85F);
        int count = 0;
        for (Goblin ally : level.getEntitiesOfClass(Goblin.class,
                getBoundingBox().inflate(CHIEF_RADIUS, 32.0, CHIEF_RADIUS), GOBLIN_ALIVE)) {
            if (ally == this || !ally.canAttack(attacker)) continue;
            if (count++ >= RALLY_CAP) break;
            ally.joinFight(attacker);
            ally.rallyCooldown = Math.max(ally.rallyCooldown, 60);
        }
        alertAlliedSlimes(level, CHIEF_RADIUS, attacker);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.6, getZ(), 36, 4.0, 1.0, 4.0, 0.2);
    }

    /** 结盟的史莱姆跟着一起上：哥布林喊人时也会叫上它们。 */
    private void alertAlliedSlimes(ServerLevel level, double radius, LivingEntity attacker) {
        for (ElementalSlime slime : level.getEntitiesOfClass(ElementalSlime.class,
                getBoundingBox().inflate(radius, 24.0, radius),
                slime -> slime.isAlive() && slime.isGoblinAlly() && slime.canAttack(attacker))) {
            if (slime.getTarget() == null) slime.setTarget(attacker);
        }
    }

    // ------------------------------------------------------------ 幼崽成长

    /** 幼崽成年：换成近战 / 弓箭手 / 萨满中的一个，背包里的东西一起带过去。 */
    private void growUp(ServerLevel level) {
        GoblinVariant adult = switch (getRandom().nextInt(10)) {
            case 0, 1 -> GoblinVariant.SHAMAN;
            case 2, 3, 4 -> GoblinVariant.ARCHER;
            default -> GoblinVariant.WARRIOR;
        };
        Goblin grown = GoblinSpawning.spawn(level, blockPosition(), adult, MobSpawnType.CONVERSION);
        if (grown == null) {
            growUpAt = level.getGameTime() + 1200L;
            return;
        }
        for (ItemStack stack : backpack) {
            if (!stack.isEmpty()) grown.backpack.add(stack.copy());
        }
        grown.setHome(homeCamp, gatherPoint);
        if (isPersistenceRequired()) grown.setPersistenceRequired();
        grown.setHealth(Math.max(1.0F, grown.getMaxHealth() * 0.5F));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0, getZ(),
                14, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, blockPosition(), GoblinSounds.get("young"), SoundSource.HOSTILE, 1.0F, 1.3F);
        discard();
    }

    // ------------------------------------------------------------ 萨满与史莱姆

    /** 萨满把附近的野生元素史莱姆驯服成帮哥布林打架的伙伴。 */
    private void tryTameSlime(ServerLevel level) {
        if (mana() < 200.0) return;
        if (level.getEntitiesOfClass(ElementalSlime.class, getBoundingBox().inflate(40.0),
                slime -> slime.isAlive() && slime.isGoblinAlly()).size() >= 5) return;
        List<ElementalSlime> wild = level.getEntitiesOfClass(ElementalSlime.class,
                getBoundingBox().inflate(10.0, 6.0, 10.0),
                slime -> slime.isAlive() && !slime.isTamed() && !slime.isGoblinAlly()
                        && ALLY_SLIME_TYPES.contains(slime.variant()));
        if (wild.isEmpty()) return;
        ElementalSlime slime = wild.get(getRandom().nextInt(wild.size()));
        if (!ManaData.spendMana(this, 200.0)) return;
        slime.makeGoblinAlly();
        tameCooldown = 600;
        castPoseTicks = 20;
        entityData.set(CASTING, true);
        level.sendParticles(ParticleTypes.HEART, slime.getX(), slime.getY() + 0.8, slime.getZ(),
                10, 0.35, 0.35, 0.35, 0.02);
        level.playSound(null, slime.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.HOSTILE, 0.9F, 1.3F);
    }

    // ------------------------------------------------------------ 每日聚集

    /** 每天 0 点后的第一个小时是聚集、交换物品的时间。 */
    private boolean isGatherTime() {
        long time = level().getDayTime() % DAY_TICKS;
        return time >= 0 && time < GATHER_WINDOW;
    }

    private void heartParticles(ServerLevel level, LivingEntity entity) {
        level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + 1.2, entity.getZ(),
                4, 0.3, 0.3, 0.3, 0.02);
    }

    private void tickYoungPanic(ServerLevel level) {
        if (variant != GoblinVariant.YOUNG) return;
        boolean hurt = getHealth() < getMaxHealth() * 0.30F;
        boolean lostTooMany = companionDeaths > 0 && companionDeaths * 2 >= companionCount;
        if (hurt || lostTooMany) {
            if (panicTicks <= 0) {
                level.playSound(null, blockPosition(), GoblinSounds.get("panic"), SoundSource.HOSTILE, 1.0F, 1.6F);
                panicTicks = 200;
            }
        }
    }

    private boolean panickingNow() {
        return variant == GoblinVariant.YOUNG
                && (panicTicks > 0 || getHealth() < getMaxHealth() * 0.30F
                || companionDeaths > 0 && companionDeaths * 2 >= companionCount);
    }

    // ------------------------------------------------------------ 战斗

    @Override
    public boolean doHurtTarget(Entity entity) {
        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        boolean already = attack != null && attack.getModifier(DIFFICULTY_DAMAGE_ID) != null;
        if (attack != null && !already) {
            attack.addOrUpdateTransientModifier(new AttributeModifier(DIFFICULTY_DAMAGE_ID,
                    difficultyScale() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        boolean result = super.doHurtTarget(entity);
        if (attack != null && !already) attack.removeModifier(DIFFICULTY_DAMAGE_ID);
        return result;
    }

    /** 伤害随难度缩放：和平 25% / 简单 50% / 普通 75% / 困难（含极限）100%。 */
    public float difficultyScale() {
        Difficulty difficulty = level().getDifficulty();
        return switch (difficulty) {
            case PEACEFUL -> 0.25F;
            case EASY -> 0.50F;
            case NORMAL -> 0.75F;
            case HARD -> 1.0F;
        };
    }

    private int battleQi() {
        return ManaData.get(this).battleQi();
    }

    private double mana() {
        return ManaData.get(this).mana();
    }

    private void spendBattleQi(int amount) {
        ManaData.setBattleQi(this, Math.max(0, battleQi() - amount));
    }

    private void meleeAttack(LivingEntity target) {
        if (meleeCooldown > 0 || !hasLineOfSight(target)) return;
        getNavigation().stop();
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        swing(InteractionHand.MAIN_HAND, true);
        doHurtTarget(target);
        meleeCooldown = variant == GoblinVariant.CHIEF ? 26 : 32;
    }

    private void approach(LivingEntity target, double speed) {
        getNavigation().moveTo(target, speed);
    }

    /** 成年哥布林的【野蛮冲撞】：斗气 > 200 时消耗 200，短距离高速冲撞。 */
    private void startRush(LivingEntity target) {
        spendBattleQi(200);
        skillCooldown = 240;
        rushTicks = 20;
        getNavigation().moveTo(target, 1.6);
        if (level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), GoblinSounds.get("attack"), SoundSource.HOSTILE, 1.0F, 1.3F);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.8, getZ(), 14, 0.4, 0.4, 0.4, 0.1);
        }
    }

    /** 弓箭手的【后撤步】：近身时消耗 50 斗气，往后跳开再补一箭。 */
    private void backstep(LivingEntity target) {
        spendBattleQi(50);
        skillCooldown = 120;
        Vec3 away = DefaultRandomPos.getPosAway(this, 7, 5, target.position());
        if (away != null) {
            getNavigation().moveTo(away.x, away.y, away.z, 1.4);
            Vec3 dir = position().subtract(target.position()).multiply(1, 0, 1).normalize();
            setDeltaMovement(dir.x * 0.5, 0.35, dir.z * 0.5);
            hurtMarked = true;
        }
        if (level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 0.6F, 1.8F);
        }
    }

    private void shootArrow(LivingEntity target) {
        if (!(level() instanceof ServerLevel level)) return;
        ItemStack weapon = getMainHandItem();
        if (!(weapon.getItem() instanceof BowItem) || !target.isAlive() || target.level() != level) return;
        // 1.21.1 的武器参数不能是 EMPTY，否则原版直接抛出 Invalid weapon firing an arrow。
        Arrow arrow = new Arrow(level, this, new ItemStack(Items.ARROW), weapon.copy());
        double dx = target.getX() - getX();
        double dy = target.getEyeY() - arrow.getY() - 0.15;
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.18, dz, 1.6F, 6.0F);
        arrow.setBaseDamage(3.0 + difficultyScale() * 2.0);
        level.addFreshEntity(arrow);
        level.playSound(null, blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
        meleeCooldown = 32;
        entityData.set(CASTING, false);
    }

    /** 萨满的毒弹 / 风弹：消耗 50 魔素，沿着射线打一发。 */
    private void castBolt(LivingEntity target, boolean poison) {
        if (!(level() instanceof ServerLevel level)) return;
        if (!ManaData.spendMana(this, 50.0)) return;
        skillCooldown = 70;
        castPoseTicks = 12;
        entityData.set(CASTING, true);
        float damage = (float)(4.0 * difficultyScale() + 2.0);
        NewSpellSystem.damage(this, target, damage, poison ? ElementType.POISON : ElementType.WIND);
        if (poison) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
        } else {
            NewSpellSystem.knock(position(), target, 3);
        }
        drawBolt(level, target, poison ? 0x66CC33 : 0xCCFFEE);
    }

    private void drawBolt(ServerLevel level, LivingEntity target, int color) {
        Vec3 start = getEyePosition();
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 step = end.subtract(start).scale(1.0 / 12.0);
        for (int i = 0; i < 12; i++) {
            Vec3 at = start.add(step.scale(i));
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(((color >> 16) & 0xFF) / 255.0F,
                            ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F), 0.8F),
                    at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
        level.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8F, 1.4F);
    }

    /**
     * 萨满的辅助法术：优先救血量低于 50% 的同伴，一次回复目标 30% 最大生命。
     * 没有重伤的哥布林时，才会去给结盟的史莱姆补血。
     */
    private boolean healAllies(ServerLevel level) {
        List<Goblin> wounded = new ArrayList<>(level.getEntitiesOfClass(Goblin.class,
                getBoundingBox().inflate(16.0),
                goblin -> goblin != this && goblin.isAlive()
                        && goblin.getHealth() < goblin.getMaxHealth() * 0.5F));
        wounded.sort(Comparator.comparingDouble(Goblin::healthRatio));
        if (wounded.size() > 3) wounded = new ArrayList<>(wounded.subList(0, 3));
        List<ElementalSlime> slimes = new ArrayList<>();
        if (wounded.isEmpty()) {
            slimes.addAll(level.getEntitiesOfClass(ElementalSlime.class, getBoundingBox().inflate(16.0),
                    slime -> slime.isAlive() && slime.isGoblinAlly()
                            && slime.getHealth() < slime.getMaxHealth() * 0.5F));
            if (slimes.size() > 2) slimes = new ArrayList<>(slimes.subList(0, 2));
        }
        if (wounded.isEmpty() && slimes.isEmpty()) return false;
        if (!ManaData.spendMana(this, 100.0)) return false;
        skillCooldown = 100;
        castPoseTicks = 16;
        entityData.set(CASTING, true);
        for (Goblin ally : wounded) {
            ally.heal(ally.getMaxHealth() * HEAL_FRACTION);
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            heartParticles(level, ally);
        }
        for (ElementalSlime slime : slimes) {
            slime.heal(slime.getMaxHealth() * HEAL_FRACTION);
            heartParticles(level, slime);
        }
        level.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.1F);
        return true;
    }

    /** 萨满濒死自爆：火环把周围一切炸开，自己也随之倒下。 */
    private void tickShamanSelfDestruct(ServerLevel level) {
        if (variant != GoblinVariant.SHAMAN || selfDestructed) return;
        if (getHealth() > getMaxHealth() * 0.25F) return;
        selfDestructed = true;
        for (LivingEntity victim : NewSpellSystem.enemies(this, position(), 4.5)) {
            victim.hurt(damageSources().explosion(this, this), 8.0F * difficultyScale() + 2.0F);
            victim.setRemainingFireTicks(80);
        }
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.8, getZ(), 60, 1.0, 0.6, 1.0, 0.08);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 6, 1.2, 0.4, 1.2, 0.02);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.9F);
        hurt(damageSources().magic(), 4.0F);
    }

    /** 首领【战吼】：消耗 150 斗气，周围 5 格玩家虚弱 + 缓慢，自身斗气恢复 ×4 持续 10 秒。 */
    private void warCry(ServerLevel level, List<Player> players) {
        spendBattleQi(150);
        skillCooldown = 200;
        qiBoostTicks = 200;
        entityData.set(CASTING, true);
        playAnimation(ANIM_ROAR, 40);
        for (Player player : players) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
        }
        for (int ring = 0; ring < 3; ring++) {
            double radius = 1.5 + ring * 1.6;
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2.0 * i / 24.0;
                level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                                new org.joml.Vector3f(0.85F, 0.1F, 0.1F), 1.2F),
                        getX() + Math.cos(angle) * radius, getY() + 0.8, getZ() + Math.sin(angle) * radius,
                        1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        level.playSound(null, blockPosition(), GoblinSounds.get("roar"), SoundSource.HOSTILE, 1.4F, 1.0F);
    }

    /** 首领【跳劈】：消耗 300 斗气，跃起后砸地，对 4 格内造成无视 50% 护甲的范围伤害。 */
    private void jumpSmash(LivingEntity target) {
        spendBattleQi(300);
        skillCooldown = 160;
        leapTicks = 16;
        leapTarget = target.position();
        playAnimation(ANIM_LEAP, 16);
        Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
        setDeltaMovement(dir.x * 0.55, 0.55, dir.z * 0.55);
        hurtMarked = true;
        if (level() instanceof ServerLevel level)
            level.playSound(null, blockPosition(), GoblinSounds.get("attack"), SoundSource.HOSTILE, 1.2F, 0.9F);
    }

    private void tickLeap(ServerLevel level) {
        leapTicks--;
        if (leapTarget != null && leapTicks > 6) {
            Vec3 dir = leapTarget.subtract(position()).multiply(1, 0, 1);
            if (dir.length() > 0.6) {
                Vec3 step = dir.normalize().scale(0.4);
                setDeltaMovement(step.x, getDeltaMovement().y, step.z);
            }
        }
        // 起跳当刻还站在地上（物理还没跑），所以前 10 刻一律不判定落地，免得「原地砸」。
        boolean landed = leapTicks <= 6 && onGround();
        if (!landed && leapTicks > 0) return;
        leapTicks = 0;
        leapTarget = null;
        if (landed) landingSlam(level);
        else clearAnimation(); // 被铁傀儡抛起或落入深坑时，不在空中凭空结算地面冲击。
    }

    private void landingSlam(ServerLevel level) {
        float base = (float)getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F * difficultyScale();
        for (LivingEntity victim : NewSpellSystem.enemies(this, position(), 4.0)) {
            // 一半照常吃护甲，另一半走魔法伤害直接穿甲，合计无视约 50% 护甲。
            victim.hurt(damageSources().mobAttack(this), base * 0.5F);
            if (victim.isAlive() && !victim.isRemoved()) {
                victim.invulnerableTime = 0;
                victim.hurt(damageSources().indirectMagic(this, this), base * 0.5F);
            }
        }
        playAnimation(ANIM_SLAM, 12);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.4, getZ(), 8, 1.4, 0.3, 1.4, 0.02);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.8F);
    }

    /** 首领【蓄力斩击】：消耗 300 斗气蓄力 4 秒，命中造成六倍伤害并有一半概率眩晕 2 秒。 */
    private void startChargedSlash() {
        spendBattleQi(300);
        skillCooldown = 120;
        chargeTicks = 80;
        chargeDamageTaken = 0.0F;
        entityData.set(CHARGING, true);
        entityData.set(CASTING, true);
        playAnimation(ANIM_CHARGE, 80);
        getNavigation().stop();
        applyModifier(getAttribute(Attributes.ARMOR), SLASH_ARMOR_ID, -0.5);
        if (level() instanceof ServerLevel level)
            level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.2F, 0.8F);
    }

    private void tickChargedSlash(ServerLevel level) {
        LivingEntity target = getTarget();
        getNavigation().stop();
        if (target != null) getLookControl().setLookAt(target, 40.0F, 40.0F);
        chargeTicks--;
        if (tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.2, getZ(), 6, 0.5, 0.5, 0.5, 0.05);
            level.playSound(null, blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 0.5F, 0.7F);
        }
        if (chargeTicks > 0) return;
        finishChargedSlash(level, target);
    }

    private void finishChargedSlash(ServerLevel level, LivingEntity target) {
        chargeTicks = 0;
        entityData.set(CHARGING, false);
        entityData.set(CASTING, false);
        clearAnimation();
        applyModifier(getAttribute(Attributes.ARMOR), SLASH_ARMOR_ID, 0.0);
        if (target == null || !target.isAlive() || target.isRemoved() || target.level() != level
                || !canAttack(target) || distanceToSqr(target) > 12.0 || !hasLineOfSight(target)) {
            level.playSound(null, blockPosition(), SoundEvents.VINDICATOR_AMBIENT, SoundSource.HOSTILE, 0.8F, 0.6F);
            return;
        }
        swing(InteractionHand.MAIN_HAND, true);
        float damage = (float)getAttributeValue(Attributes.ATTACK_DAMAGE) * 6.0F * difficultyScale();
        boolean hit = target.hurt(damageSources().mobAttack(this), damage);
        if (hit && target.isAlive() && !target.isRemoved() && getRandom().nextFloat() < 0.50F)
            NewSpellSystem.control(this, target, NewSpellContent.STUN, 40);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(),
                6, 0.5, 0.3, 0.5, 0.0);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.8F);
    }

    private void interruptCharge() {
        if (chargeTicks <= 0) return;
        chargeTicks = 0;
        entityData.set(CHARGING, false);
        entityData.set(CASTING, false);
        clearAnimation();
        applyModifier(getAttribute(Attributes.ARMOR), SLASH_ARMOR_ID, 0.0);
        addEffect(new MobEffectInstance(NewSpellContent.STUN, 40, 0));
        if (level() instanceof ServerLevel level) {
            level.playSound(null, blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 1.2F, 1.0F);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.2, getZ(), 18, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (amount > 0.0F && chargeTicks > 0) {
            chargeDamageTaken += amount;
            if (chargeDamageTaken >= SLASH_INTERRUPT_DAMAGE) interruptCharge();
        }
    }

    // ------------------------------------------------------------ 背包

    private boolean canStore(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.get(DataComponents.FOOD) != null) return true;
        if (stack.is(Items.IRON_INGOT) || stack.is(Items.RAW_IRON) || stack.is(Items.IRON_ORE)
                || stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return true;
        if (variant == GoblinVariant.CHIEF) {
            return stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD) || stack.is(Items.GOLD_NUGGET)
                    || stack.is(Items.GOLD_BLOCK) || stack.is(Items.DIAMOND) || stack.is(Items.DIAMOND_BLOCK);
        }
        return false;
    }

    private boolean backpackFull() {
        return backpack.size() >= variant.capacity();
    }

    private void absorb(ItemStack stack) {
        // 捡到更好的武器就换下手中的粗制武器。
        if (isWeapon(stack) && (getMainHandItem().isEmpty() || isWeapon(getMainHandItem())
                && weaponDamage(stack) > weaponDamage(getMainHandItem()))) {
            ItemStack old = getMainHandItem();
            setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
            if (!old.isEmpty() && !backpackFull()) backpack.add(old);
            if (stack.getCount() > 1 && !backpackFull()) backpack.add(stack.copyWithCount(stack.getCount() - 1));
            return;
        }
        if (backpackFull()) return;
        for (ItemStack stored : backpack) {
            if (ItemStack.isSameItemSameComponents(stored, stack) && stored.getCount() < stored.getMaxStackSize()) {
                int room = stored.getMaxStackSize() - stored.getCount();
                int moved = Math.min(room, stack.getCount());
                stored.grow(moved);
                stack.shrink(moved);
                return;
            }
        }
        backpack.add(stack.copy());
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.get(DataComponents.ATTRIBUTE_MODIFIERS) != null
                && weaponDamage(stack) > 1.0;
    }

    private static double weaponDamage(ItemStack stack) {
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return 0.0;
        return modifiers.compute(0.0, EquipmentSlot.MAINHAND);
    }

    /** 血量低于一半时吃背包里的东西回血。 */
    private boolean tryEat() {
        if (variant == GoblinVariant.YOUNG && getHealth() > getMaxHealth() * 0.30F) return false;
        if (getHealth() > getMaxHealth() * 0.50F) return false;
        for (int i = 0; i < backpack.size(); i++) {
            ItemStack stack = backpack.get(i);
            var food = stack.get(DataComponents.FOOD);
            if (food == null) continue;
            stack.shrink(1);
            if (stack.isEmpty()) backpack.remove(i);
            heal(Math.max(2.0F, food.nutrition() * 1.0F));
            eatCooldown = 60;
            if (level() instanceof ServerLevel level) {
                level.playSound(null, blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 0.8F, 1.0F);
                level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.2, getZ(), 5, 0.2, 0.2, 0.2, 0.02);
            }
            return true;
        }
        return false;
    }

    private ItemEntity findLoot(ServerLevel level) {
        return level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8.0),
                        entity -> entity.isAlive() && canStore(entity.getItem())).stream()
                .min(java.util.Comparator.comparingInt((ItemEntity entity) -> -lootPriority(entity.getItem()))
                        .thenComparingDouble(this::distanceToSqr)).orElse(null);
    }

    /** 肉类优先：食物按回血多少排在矿物前面。 */
    private int lootPriority(ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        if (food != null) return 100 + food.nutrition();
        return 1;
    }

    // ------------------------------------------------------------ 死亡

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) dropBackpack();
        onGoblinDeath(this);
        super.die(source);
    }

    private void dropBackpack() {
        for (ItemStack stack : backpack) {
            if (!stack.isEmpty()) spawnAtLocation(stack.copy());
        }
        backpack.clear();
    }

    /** 有哥布林倒下时，附近同伴会记录一笔；幼崽靠这个判断「同伴死亡过半」。 */
    public static void onGoblinDeath(LivingEntity dead) {
        if (!(dead.level() instanceof ServerLevel level)) return;
        for (Goblin goblin : level.getEntitiesOfClass(Goblin.class, dead.getBoundingBox().inflate(16.0), GOBLIN_ALIVE)) {
            if (goblin == dead) continue;
            goblin.companionDeaths++;
            if (goblin.variant == GoblinVariant.YOUNG) goblin.panicTicks = Math.max(goblin.panicTicks, 240);
        }
    }

    // ------------------------------------------------------------ 存读档

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("MeleeCooldown", meleeCooldown);
        tag.putInt("SkillCooldown", skillCooldown);
        tag.putInt("PanicTicks", panicTicks);
        tag.putInt("CompanionDeaths", companionDeaths);
        tag.putInt("QiBoostTicks", qiBoostTicks);
        tag.putBoolean("SelfDestructed", selfDestructed);
        tag.putLong("GrowUpAt", growUpAt);
        if (homeCamp != null) tag.putLong("HomeCamp", homeCamp.asLong());
        if (gatherPoint != null) tag.putLong("GatherPoint", gatherPoint.asLong());
        ListTag stored = new ListTag();
        for (ItemStack stack : backpack) {
            if (!stack.isEmpty()) stored.add(stack.save(registryAccess()));
        }
        tag.put("GoblinBackpack", stored);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        meleeCooldown = tag.getInt("MeleeCooldown");
        skillCooldown = tag.getInt("SkillCooldown");
        panicTicks = tag.getInt("PanicTicks");
        companionDeaths = tag.getInt("CompanionDeaths");
        qiBoostTicks = tag.getInt("QiBoostTicks");
        selfDestructed = tag.getBoolean("SelfDestructed");
        growUpAt = tag.getLong("GrowUpAt");
        homeCamp = tag.contains("HomeCamp") ? BlockPos.of(tag.getLong("HomeCamp")) : null;
        gatherPoint = tag.contains("GatherPoint") ? BlockPos.of(tag.getLong("GatherPoint")) : null;
        initialized = true;
        backpack.clear();
        ListTag stored = tag.getList("GoblinBackpack", Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registryAccess(), stored.getCompound(i));
            if (!stack.isEmpty()) backpack.add(stack);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return variant != GoblinVariant.CHIEF;
    }

    // ------------------------------------------------------------ 音效

    @Override
    protected SoundEvent getAmbientSound() {
        return switch (variant) {
            case SHAMAN -> GoblinSounds.get("shaman");
            case CHIEF -> GoblinSounds.get("chief");
            case YOUNG -> GoblinSounds.get("young");
            default -> GoblinSounds.get("ambient");
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GoblinSounds.get("hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GoblinSounds.get("death");
    }

    @Override
    public float getVoicePitch() {
        float pitch = variant == GoblinVariant.YOUNG ? 1.3F : variant == GoblinVariant.CHIEF ? 0.78F : 1.0F;
        return pitch + (random.nextFloat() - random.nextFloat()) * 0.08F;
    }

    // ------------------------------------------------------------ 目标行为

    private final class FleeGoal extends Goal {
        private int screamCooldown;

        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return panickingNow();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            setTarget(null);
            LivingEntity threat = getLastHurtByMob();
            Vec3 away = threat == null ? null : DefaultRandomPos.getPosAway(Goblin.this, 12, 6, threat.position());
            if (away == null) away = DefaultRandomPos.getPosAway(Goblin.this, 12, 6, position());
            if (away != null && (getNavigation().isDone() || tickCount % 20 == 0)) {
                getNavigation().moveTo(away.x, away.y, away.z, 1.4);
            }
            setSprinting(true);
            if (screamCooldown-- <= 0) {
                screamCooldown = 30;
                Goblin.this.playSound(GoblinSounds.get("panic"), 1.2F, 1.7F);
            }
        }

        @Override
        public void stop() {
            setSprinting(false);
        }
    }

    private final class CombatGoal extends Goal {
        CombatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return variant.fights() && target != null && target.isAlive()
                    && !panickingNow() && chargeTicks <= 0 && leapTicks <= 0
                    && !NewSpellSystem.immobilized(Goblin.this);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            setAggressive(true);
        }

        @Override
        public void stop() {
            setAggressive(false);
            if (bowDrawTicks > 0) cancelBowDraw();
            if (chargeTicks <= 0 && animation() == ANIM_NONE && castPoseTicks <= 0)
                entityData.set(CASTING, false);
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = distanceTo(target);
            switch (variant) {
                case WARRIOR -> tickWarrior(target, distance);
                case ARCHER -> tickArcher(target, distance);
                case SHAMAN -> tickShaman(target, distance);
                case CHIEF -> tickChief(target, distance);
                default -> { }
            }
        }

        private void tickWarrior(LivingEntity target, double distance) {
            if (rushTicks > 0) {
                getNavigation().moveTo(target, 1.6);
                if (distance <= 2.4) {
                    meleeAttack(target);
                    if (target.isAlive()) {
                        NewSpellSystem.knock(position(), target, 4);
                        target.hurt(damageSources().mobAttack(Goblin.this), 4.0F * difficultyScale());
                    }
                    rushTicks = 0;
                }
                return;
            }
            if (skillCooldown <= 0 && distance >= 4.0 && distance <= 10.0 && battleQi() >= 200) {
                startRush(target);
                return;
            }
            if (distance > 2.2) approach(target, 1.0);
            else meleeAttack(target);
        }

        private void tickArcher(LivingEntity target, double distance) {
            if (!(getMainHandItem().getItem() instanceof BowItem)) {
                cancelBowDraw();
                if (distance > 2.2) approach(target, 1.0); else meleeAttack(target);
                return;
            }
            if (skillCooldown <= 0 && distance <= 5.0 && battleQi() >= 50) {
                backstep(target);
                return;
            }
            if (distance > 16.0) {
                approach(target, 1.0);
                return;
            }
            if (distance < 6.0) {
                Vec3 away = DefaultRandomPos.getPosAway(Goblin.this, 8, 4, target.position());
                if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                else getNavigation().stop();
                return;
            }
            getNavigation().stop();
            if (meleeCooldown <= 0 && hasLineOfSight(target)) startBowDraw();
            // 拉弓只由 isUsingItem 驱动，不复用萨满施法标记。
        }

        private void tickShaman(LivingEntity target, double distance) {
            if (skillCooldown <= 0 && mana() >= 100.0 && level() instanceof ServerLevel level
                    && healAllies(level)) return;
            if (skillCooldown <= 0 && distance <= 12.0 && hasLineOfSight(target) && mana() >= 50.0) {
                castBolt(target, getRandom().nextBoolean());
                return;
            }
            if (distance > 10.0) approach(target, 0.9);
            else if (distance < 5.0) {
                Vec3 away = DefaultRandomPos.getPosAway(Goblin.this, 8, 4, target.position());
                if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.0);
            } else getNavigation().stop();
            if (castPoseTicks <= 0) entityData.set(CASTING, false);
        }

        private void tickChief(LivingEntity target, double distance) {
            if (skillCooldown <= 0 && battleQi() >= 150 && level() instanceof ServerLevel level) {
                List<Player> players = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(5.0),
                        player -> !player.isSpectator() && !player.isCreative());
                if (!players.isEmpty()) {
                    warCry(level, players);
                    return;
                }
            }
            if (skillCooldown <= 0 && battleQi() >= 300 && distance >= 6.0 && distance <= 14.0) {
                jumpSmash(target);
                return;
            }
            if (skillCooldown <= 0 && battleQi() >= 300 && distance <= 3.0) {
                startChargedSlash();
                return;
            }
            if (distance > 2.6) approach(target, 1.0);
            else meleeAttack(target);
        }
    }

    /**
     * 每天 0 点聚到聚集棚：先走过去，到了就互相看、冒爱心，并不时把背包里的一件东西递给同伴。
     * 只有登记过营地的哥布林才会参加，野外巡逻队照常游荡。
     */
    private final class GatherGoal extends Goal {
        private int socializeCooldown;

        GatherGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return gatherPoint != null && isGatherTime() && getTarget() == null
                    && chargeTicks <= 0 && leapTicks <= 0 && !panickingNow()
                    && distanceToSqr(Vec3.atCenterOf(gatherPoint)) < 110.0 * 110.0;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Vec3 spot = Vec3.atCenterOf(gatherPoint);
            if (distanceToSqr(spot) > 9.0) {
                if (getNavigation().isDone())
                    getNavigation().moveTo(gatherPoint.getX() + 0.5, gatherPoint.getY(),
                            gatherPoint.getZ() + 0.5, 0.9);
                return;
            }
            getNavigation().stop();
            Goblin partner = nearestGatherer();
            if (partner != null) getLookControl().setLookAt(partner, 20.0F, 20.0F);
            if (--socializeCooldown > 0) return;
            socializeCooldown = 60 + getRandom().nextInt(80);
            if (!(level() instanceof ServerLevel level)) return;
            if (partner != null && exchange(level, partner)) return;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.4, getZ(),
                    3, 0.3, 0.2, 0.3, 0.01);
        }

        private Goblin nearestGatherer() {
            return level().getEntitiesOfClass(Goblin.class, getBoundingBox().inflate(6.0),
                            goblin -> goblin != Goblin.this && goblin.isAlive() && goblin.gatherPoint != null)
                    .stream().min(Comparator.comparingDouble(Goblin.this::distanceToSqr)).orElse(null);
        }

        /** 把背包里的一件东西递给同伴：同伴的拾取背包就是「交换物品」的载体。 */
        private boolean exchange(ServerLevel level, Goblin partner) {
            if (backpack.isEmpty() || partner.backpack.size() >= partner.variant.capacity()) return false;
            int index = getRandom().nextInt(backpack.size());
            ItemStack stack = backpack.get(index);
            ItemStack moved = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) backpack.remove(index);
            partner.backpack.add(moved);
            level.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.6F, 1.2F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.3, getZ(),
                    5, 0.3, 0.3, 0.3, 0.02);
            return true;
        }
    }

    private final class EatGoal extends Goal {
        EatGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return eatCooldown <= 0 && chargeTicks <= 0
                    && getHealth() < getMaxHealth() * 0.50F && hasFood();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            getNavigation().stop();
            tryEat();
        }

        private boolean hasFood() {
            for (ItemStack stack : backpack) if (stack.get(DataComponents.FOOD) != null) return true;
            return false;
        }
    }

    private final class PickupGoal extends Goal {
        private ItemEntity target;

        PickupGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (pickupCooldown > 0 || !(level() instanceof ServerLevel level)) return false;
            if (getTarget() != null || chargeTicks > 0 || leapTicks > 0 || backpackFull()) return false;
            target = findLoot(level);
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && !backpackFull() && canStore(target.getItem());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (target == null) return;
            if (distanceToSqr(target) > 1.6) {
                getNavigation().moveTo(target, 1.2);
                return;
            }
            ItemStack stack = target.getItem();
            absorb(stack);
            if (stack.isEmpty()) target.discard();
            else target.setItem(stack);
            pickupCooldown = 12;
            if (level() instanceof ServerLevel level) {
                level.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.5F, 1.4F);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0, getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            }
        }

        @Override
        public void stop() {
            target = null;
            getNavigation().stop();
        }
    }
}
