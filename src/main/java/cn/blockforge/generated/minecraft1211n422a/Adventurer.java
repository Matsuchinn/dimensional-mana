package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 会在近战与施法间切换的中立探索者。 */
public final class Adventurer extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> CASTING = SynchedEntityData.defineId(Adventurer.class, EntityDataSerializers.BOOLEAN);
    private static final double WALK_SPEED = 1.5 / 43.17;
    private static final double RUN_SPEED = 7.0 / 43.17;
    private static final SpellDefinition[] NOVICE_POOL = java.util.Arrays.stream(SpellDefinition.values())
            .filter(spell -> !spell.isIntermediate() && !spell.isAdvanced()).toArray(SpellDefinition[]::new);
    private static final SpellDefinition[] INTERMEDIATE_POOL = java.util.Arrays.stream(SpellDefinition.values())
            .filter(SpellDefinition::isIntermediate)
            // 烈焰囚笼、涌泉之报、寒冰傀儡、风爆步、磐石、三个奥术新术、两记金系新术与光之庇护
            // 依赖玩家侧的目标、区块、区域、锚点或整段前摇蓄力结算，冒险者不参与随机。
            .filter(spell -> spell != SpellDefinition.FLAME_CAGE
                    && spell != SpellDefinition.SPRING_RETURN
                    && spell != SpellDefinition.ICE_GOLEM
                    && spell != SpellDefinition.WIND_BURST_STEP
                    && spell != SpellDefinition.BOULDER
                    && spell != SpellDefinition.CHAOS_MAGIC
                    && spell != SpellDefinition.MANA_SENSE
                    && spell != SpellDefinition.MANA_TIDE
                    && spell != SpellDefinition.THORN_IMPALE
                    && spell != SpellDefinition.GOLDEN_BELL
                    && spell != SpellDefinition.LIGHT_SHELTER
                    // 猛毒攻心依赖玩家侧的冲刺与贯穿毒刺结算，冒险者不参与随机。
                    && spell != SpellDefinition.VENOM_HEART
                    // 次声走的是玩家侧的音系结算与眩晕锁位，冒险者不参与随机。
                    && spell != SpellDefinition.INFRASOUND).toArray(SpellDefinition[]::new);
    private static final int SPELL_LOADOUT_VERSION = 3;
    private static final int MELEE_INTERVAL_TICKS = 36; // 20 刻 = 1 秒，即每 1.8 秒一刀。

    private final List<SpellDefinition> knownSpells = new ArrayList<>();
    private final Map<SpellDefinition, Integer> spellUses = new EnumMap<>(SpellDefinition.class);
    private ItemStack sword = ItemStack.EMPTY;
    private ItemStack staff = ItemStack.EMPTY;
    private SpellDefinition chargingSpell;
    private java.util.UUID chargingTarget;
    private int chargeTicks;
    private int spellCooldown = 30;
    private int meleeCooldown;
    private int retreatTicks;
    private int supplyTicks;
    private int supplyKind;
    private int supplyCooldown;
    private int speedPotionCooldown;
    private int lastDamageTick = -100;
    private BlockPos villageTarget;
    private BlockPos lastVillage;
    private long villageStayUntil;
    private int villageSearchCooldown;
    private boolean usedGoldenApple;
    private int spellLoadoutVersion;
    private SpellDefinition lastSpell;
    /** 这名冒险者身上挂着的 10 笔交易，只在服务端存在。 */
    private final List<AdventurerTrades.Offer> tradeOffers = new ArrayList<>();
    /** 下一次换货的游戏刻；0 表示没在村庄里住下、不排换货。 */
    private long tradeRestockAt;
    /** 正在看交易界面的玩家 id，用来让冒险者站住别走开。 */
    private final java.util.Set<Integer> tradingPlayers = new java.util.HashSet<>();

    public Adventurer(EntityType<? extends Adventurer> type, Level level) {
        super(type, level);
        xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, RUN_SPEED)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CASTING, false);
    }

    public boolean isCastingSpell() {
        return entityData.get(CASTING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RangedPositionGoal());
        goalSelector.addGoal(5, new VillageTravelGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, WALK_SPEED / RUN_SPEED));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, Adventurer.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                target -> !(target instanceof Adventurer)));
    }

    private boolean canAct() {
        return supplyTicks <= 0 && chargeTicks <= 0 && !NewSpellSystem.immobilized(this);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Adventurer) && super.canAttack(target);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof Adventurer || super.isAlliedTo(other);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData);
        initializeEquipment(level);
        rollSpells();
        ManaData.setMobPools(this, 1000.0, 1000);
        setHealth(getMaxHealth());
        if (reason == MobSpawnType.STRUCTURE) setPersistenceRequired();
        return result;
    }

    private void initializeEquipment(ServerLevelAccessor level) {
        if (sword.isEmpty()) {
            sword = new ItemStack(Items.IRON_SWORD);
            var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            sword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 3);
            sword.enchant(enchantments.getOrThrow(Enchantments.KNOCKBACK), 2);
        }
        if (staff.isEmpty()) staff = new ItemStack(GeneratedMod.MOD_ITEMS.get("medium_general_staff").get());
        setItemSlot(EquipmentSlot.MAINHAND, staff.copy());
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /** 冒险者身上挂着的交易；只有服务端会填内容，客户端拿到的是同步下来的展示格。 */
    public List<AdventurerTrades.Offer> tradeOffers() {
        return tradeOffers;
    }

    /** 有玩家打开了交易界面：这个人在看货期间先别走开。 */
    public void beginTrading(Player player) {
        if (level().isClientSide || player == null) return;
        tradingPlayers.add(player.getId());
    }

    public void endTrading(Player player) {
        if (player == null) return;
        tradingPlayers.remove(player.getId());
    }

    /** 老存档里的冒险者没有交易数据，第一次碰到时补抽一批。 */
    private void ensureTrades() {
        if (!(level() instanceof ServerLevel level) || !tradeOffers.isEmpty()) return;
        tradeOffers.addAll(AdventurerTrades.roll(level, getRandom()));
    }

    /**
     * 村庄里每住满一天（24000 刻）换一批交易并重置次数；离开村庄就不再排换货。
     * 换货时刻在到村时排好（见 {@link #beginVillageStay(ServerLevel)}）。
     */
    private void tickTradeRestock(ServerLevel level) {
        if (tradeRestockAt <= 0L) return;
        long now = level.getGameTime();
        if (now >= villageStayUntil) {
            tradeRestockAt = 0L;
            return;
        }
        if (now < tradeRestockAt) return;
        tradeOffers.clear();
        tradeOffers.addAll(AdventurerTrades.roll(level, getRandom()));
        tradeRestockAt = now + 24000L;
        level.playSound(null, blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.7F, 1.0F);
    }

    /** 右键冒险者打开交易界面（正在打你的冒险者不卖东西）。 */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || getTarget() == player) return InteractionResult.PASS;
        ensureTrades();
        serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inventory, owner) -> new AdventurerTradeMenu(id, inventory, this),
                        Component.translatable("gui.dimensional_mana.adventurer_trade")),
                buffer -> buffer.writeVarInt(getId()));
        level().playSound(null, blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.0F);
        return InteractionResult.CONSUME;
    }

    private void rollSpells() {
        if (!knownSpells.isEmpty() && spellLoadoutVersion >= SPELL_LOADOUT_VERSION) return;
        // 旧池子只抽 4～8 个初级、1～3 个中级，具体法术可能整只冒险者都不会。
        // 冒险者设定为掌握全部法术，战斗差异改由公平轮换、魔素和战况决定。
        knownSpells.clear();
        Collections.addAll(knownSpells, NOVICE_POOL);
        Collections.addAll(knownSpells, INTERMEDIATE_POOL);
        spellLoadoutVersion = SPELL_LOADOUT_VERSION;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        rollSpells();
        if (sword.isEmpty() || staff.isEmpty()) initializeEquipment(level);
        ensureTrades();
        tickTradeRestock(level);
        if (supplyCooldown > 0) supplyCooldown--;
        if (speedPotionCooldown > 0) speedPotionCooldown--;
        if (villageSearchCooldown > 0) villageSearchCooldown--;
        if (spellCooldown > 0) spellCooldown--;
        if (meleeCooldown > 0) meleeCooldown--;

        if (NewSpellSystem.immobilized(this)) {
            cancelCharge();
            getNavigation().stop();
            return;
        }
        if (tickSupply(level)) return;
        if (tryStartSupply(level)) return;

        LivingEntity target = getTarget();
        if (target instanceof Adventurer || target != null && !target.isAlive()) {
            setTarget(null);
            target = null;
        }
        if (target != null) {
            double distance = distanceToSqr(target);
            setItemSlot(EquipmentSlot.MAINHAND, (distance <= 4.0 ? sword : staff).copy());
            setAggressive(true);
            // 冒险者会主动迎战；同时让敌对生物真正把它作为攻击目标，而不是只挨打法术。
            if (target instanceof Monster monster && monster.getTarget() == null && monster.canAttack(this)) monster.setTarget(this);
            // 不依赖 MeleeAttackGoal 的启动节流：敌人进入两格后当刻中断施法、切剑并尝试挥砍。
            if (distance <= 4.0) {
                if (chargeTicks > 0) cancelCharge();
                if (canAct()) tickMelee(target);
            }
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, staff.copy());
            setAggressive(false);
            if (!tradingPlayers.isEmpty()) {
                // 有玩家正在翻交易界面：先站住，别走远把界面关掉（被攻击时照常迎战）。
                getNavigation().stop();
                setSprinting(false);
            }
        }
        // sprinting 只控制奔跑姿态，不会自动移动；仅在寻路确实进行时开启，避免原地跑步。
        setSprinting(getNavigation().isInProgress());

        if (chargeTicks > 0) {
            LivingEntity locked = lockedTarget(level);
            if (chargingSpell == null || !validSpellTarget(locked)) {
                cancelCharge();
                return;
            }
            getNavigation().stop();
            getLookControl().setLookAt(locked, 30.0F, 30.0F);
            if (--chargeTicks <= 0) finishSpell(level, locked);
            return;
        }
        entityData.set(CASTING, false);
        if (target == null || distanceToSqr(target) <= 4.0 || distanceToSqr(target) > 16.0 * 16.0
                || !hasLineOfSight(target) || spellCooldown > 0 || NewSpellSystem.cannotCast(this)) return;
        startSpell(target);
    }

    private boolean tryStartSupply(ServerLevel level) {
        if (supplyTicks > 0 || supplyCooldown > 0 || chargeTicks > 0) return false;
        if (!usedGoldenApple && getHealth() <= getMaxHealth() * 0.20F) {
            usedGoldenApple = true;
            beginSupply(2, new ItemStack(Items.GOLDEN_APPLE), 32);
            return true;
        }
        // 脱战并连续安全 5 秒后，只要没满血就反复喝治疗 II，直至恢复满血。
        if (getHealth() < getMaxHealth() && safeToHeal(level)) {
            beginSupply(1, PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING), 32);
            return true;
        }
        double mana = ManaData.get(this).mana();
        if (mana < 100.0) {
            beginSupply(4, new ItemStack(GeneratedMod.MOD_ITEMS.get("medium_mana_potion").get()), 32);
            return true;
        }
        if (mana < 600.0) {
            beginSupply(3, new ItemStack(GeneratedMod.MOD_ITEMS.get("lesser_mana_potion").get()), 32);
            return true;
        }
        return false;
    }

    private void beginSupply(int kind, ItemStack stack, int ticks) {
        cancelCharge();
        supplyKind = kind;
        supplyTicks = ticks;
        setItemSlot(EquipmentSlot.MAINHAND, stack);
        startUsingItem(InteractionHand.MAIN_HAND);
        getNavigation().stop();
    }

    private boolean safeToHeal(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) return false;
        if (tickCount - Math.max(lastDamageTick, getLastHurtByMobTimestamp()) < 100) return false;
        return level.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(12.0), monster ->
                monster.isAlive() && monster.getTarget() == this && hasLineOfSight(monster)).isEmpty();
    }

    private boolean tickSupply(ServerLevel level) {
        if (supplyTicks <= 0) return false;
        // 治疗和旅行药水都不能压过战斗；中途遇敌或挨打则立即收药迎战。
        if (supplyKind == 1 && !safeToHeal(level) || supplyKind == 5 && getTarget() != null && getTarget().isAlive()) {
            stopUsingItem();
            supplyTicks = 0;
            supplyKind = 0;
            supplyCooldown = 20;
            setItemSlot(EquipmentSlot.MAINHAND, staff.copy());
            return false;
        }
        getNavigation().stop();
        if (--supplyTicks > 0) return true;
        stopUsingItem();
        switch (supplyKind) {
            case 1 -> heal(8.0F);
            case 2 -> {
                addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
            }
            case 3 -> ManaData.restoreMana(this, ManaData.get(this).maxMana() * 0.025 + 64.0, false);
            case 4 -> ManaData.restoreMana(this, ManaData.get(this).maxMana() * 0.0678 + 128.0, false);
            case 5 -> {
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
                speedPotionCooldown = 1200;
            }
            default -> { }
        }
        level.broadcastEntityEvent(this, (byte)9);
        int completedKind = supplyKind;
        supplyKind = 0;
        // 治疗药水只短暂停顿，随后继续检查生命值；其他补给保留原有间隔。
        supplyCooldown = completedKind == 1 ? 10 : 60;
        setItemSlot(EquipmentSlot.MAINHAND, staff.copy());
        return true;
    }

    private boolean ensureVillageTarget(ServerLevel level) {
        if (villageTarget != null) return true;
        if (villageSearchCooldown > 0) return false;
        villageSearchCooldown = 600;
        BlockPos origin = blockPosition();
        BlockPos found = null;
        if (lastVillage == null) found = level.findNearestMapStructure(StructureTags.VILLAGE, origin, 80, false);
        // 离开当前村庄后，从几个远处方向探查，避免定位结果反复指回刚住过的村庄。
        for (int attempt = 0; found == null && attempt < 4; attempt++) {
            double angle = getRandom().nextDouble() * Math.PI * 2.0;
            BlockPos probe = origin.offset(Mth.floor(Math.cos(angle) * 512.0), 0, Mth.floor(Math.sin(angle) * 512.0));
            BlockPos candidate = level.findNearestMapStructure(StructureTags.VILLAGE, probe, 64, false);
            if (candidate != null && (lastVillage == null || !candidate.closerThan(lastVillage, 128.0))) found = candidate;
        }
        if (found == null || lastVillage != null && found.closerThan(lastVillage, 128.0)) return false;
        villageTarget = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found).immutable();
        return true;
    }

    private boolean arrivedAtVillage() {
        if (villageTarget == null) return false;
        double dx = getX() - (villageTarget.getX() + 0.5);
        double dz = getZ() - (villageTarget.getZ() + 0.5);
        return dx * dx + dz * dz <= 48.0 * 48.0;
    }

    private void beginVillageStay(ServerLevel level) {
        lastVillage = villageTarget;
        villageTarget = null;
        villageStayUntil = level.getGameTime() + (3L + getRandom().nextInt(7)) * 24000L;
        // 到村后的第二天（24000 刻后）换一批交易，之后每天再换。
        tradeRestockAt = level.getGameTime() + 24000L;
        villageSearchCooldown = 600;
        getNavigation().stop();
        setSprinting(false);
    }

    private void startSpell(LivingEntity target) {
        double mana = ManaData.get(this).mana();
        List<SpellDefinition> affordable = knownSpells.stream().filter(spell -> mana >= spell.manaCost()).toList();
        if (affordable.isEmpty()) {
            spellCooldown = 20;
            return;
        }

        List<SpellDefinition> candidates = affordable;
        if (mana > 400.0) {
            List<SpellDefinition> medium = affordable.stream().filter(SpellDefinition::isIntermediate).toList();
            if (!medium.isEmpty() && getRandom().nextFloat() < 0.75F) candidates = medium;
        }

        List<SpellDefinition> offensive = candidates.stream().filter(spell -> !beneficial(spell)).toList();
        List<SpellDefinition> reactions = offensive.stream()
                .filter(spell -> spell.isArcane() ? ElementReactionSystem.wouldReactArcane(target)
                        : ElementReactionSystem.wouldReact(target, spell.element())).toList();
        List<SpellDefinition> usefulBuffs = candidates.stream()
                .filter(this::beneficial).filter(this::needsBuff).toList();
        // 能接元素反应时优先完成组合；否则最多三成机会补增益，避免连续给自己套盾而不攻击。
        List<SpellDefinition> selection = !reactions.isEmpty() ? reactions
                : !usefulBuffs.isEmpty() && (offensive.isEmpty() || getRandom().nextFloat() < 0.30F) ? usefulBuffs
                : !offensive.isEmpty() ? offensive : usefulBuffs;
        if (selection.isEmpty()) {
            spellCooldown = 20;
            return;
        }
        SpellDefinition spell = chooseFairly(selection);
        chargingSpell = spell;
        chargingTarget = target.getUUID();
        chargeTicks = Mth.clamp((int)Math.ceil(spell.castTicks() * 0.60), 12, 60);
        entityData.set(CASTING, true);
        setItemSlot(EquipmentSlot.MAINHAND, staff.copy());
        SpellVisualPayload.send(this, spell, SpellVisualPayload.CHARGE, target.getId(), getEyePosition(), target.getBoundingBox().getCenter(), chargeTicks);
    }

    private SpellDefinition chooseFairly(List<SpellDefinition> choices) {
        List<SpellDefinition> usable = choices.size() > 1
                ? choices.stream().filter(spell -> spell != lastSpell).toList() : choices;
        if (usable.isEmpty()) usable = choices;
        int leastUses = usable.stream().mapToInt(spell -> spellUses.getOrDefault(spell, 0)).min().orElse(0);
        List<SpellDefinition> leastUsed = usable.stream()
                .filter(spell -> spellUses.getOrDefault(spell, 0) == leastUses).toList();
        return leastUsed.get(getRandom().nextInt(leastUsed.size()));
    }

    private boolean beneficial(SpellDefinition spell) {
        return switch (spell) {
            case ARCANE_WARD, ARCANE_SURGE, FLAME_BLESSING, MOISTURE, ICE_SHIELD, STATIC_FIELD,
                    GALE_DASH, STONE_ARMOR, REJUVENATION, IRON_WALL, PURIFYING_LIGHT, LIGHTNESS,
                    LIFE_BLESSING, GLIMMER, MUSICAL_INSPIRATION -> true;
            default -> false;
        };
    }

    private boolean needsBuff(SpellDefinition spell) {
        return switch (spell) {
            case MOISTURE, REJUVENATION, PURIFYING_LIGHT -> getHealth() <= getMaxHealth() * 0.85F;
            case ARCANE_WARD, ICE_SHIELD, STONE_ARMOR, IRON_WALL -> effectEndsWithin(MobEffects.DAMAGE_RESISTANCE, 60);
            case ARCANE_SURGE -> getHealth() < getMaxHealth() && effectEndsWithin(MobEffects.REGENERATION, 60);
            case FLAME_BLESSING -> effectEndsWithin(MobEffects.FIRE_RESISTANCE, 80);
            case LIGHTNESS, GALE_DASH, STATIC_FIELD -> effectEndsWithin(MobEffects.MOVEMENT_SPEED, 60);
            case LIFE_BLESSING -> effectEndsWithin(MobEffects.REGENERATION, 100)
                    || effectEndsWithin(MobEffects.ABSORPTION, 100);
            case GLIMMER -> effectEndsWithin(MobEffects.INVISIBILITY, 60);
            case MUSICAL_INSPIRATION -> effectEndsWithin(MobEffects.MOVEMENT_SPEED, 60)
                    || effectEndsWithin(MobEffects.DAMAGE_BOOST, 60);
            default -> false;
        };
    }

    private boolean effectEndsWithin(Holder<net.minecraft.world.effect.MobEffect> effect, int ticks) {
        MobEffectInstance instance = getEffect(effect);
        return instance == null || instance.endsWithin(ticks);
    }

    private LivingEntity lockedTarget(ServerLevel level) {
        Entity entity = chargingTarget == null ? null : level.getEntity(chargingTarget);
        return entity instanceof LivingEntity living ? living : null;
    }

    private boolean validSpellTarget(LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Adventurer)
                && distanceToSqr(target) <= 18.0 * 18.0;
    }

    private void finishSpell(ServerLevel level, LivingEntity target) {
        SpellDefinition spell = chargingSpell;
        chargingSpell = null;
        chargingTarget = null;
        chargeTicks = 0;
        entityData.set(CASTING, false);
        if (spell == null || !validSpellTarget(target) || !ManaData.spendMana(this, spell.manaCost())) {
            spellCooldown = 20;
            return;
        }
        spellCooldown = Math.max(30, spell.cooldownTicks());
        retreatTicks = 28;
        lastSpell = spell;
        spellUses.merge(spell, 1, Integer::sum);
        if (spellUses.values().stream().mapToInt(Integer::intValue).min().orElse(0) >= 20) {
            spellUses.replaceAll((known, uses) -> uses - 20);
        }
        Vec3 start = getEyePosition().add(getLookAngle().scale(0.35));
        Vec3 end = target.getBoundingBox().getCenter();
        boolean beneficial = beneficial(spell);
        int anchor = beneficial ? getId() : attachedVisual(spell) ? target.getId() : -1;
        // 先同步释放动画再结算伤害，保证致死一击不会让客户端错过落石等命中特效。
        SpellVisualPayload.send(this, spell, SpellVisualPayload.RELEASE, anchor, start,
                beneficial ? getBoundingBox().getCenter() : end, spell.isIntermediate() ? 42 : 28);
        if (beneficial) applySelfBuff(spell);
        else castOffensive(spell, target);
        level.playSound(null, blockPosition(), SpellSounds.get("spell_" + spell.visualBase().id()),
                net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 0.9F + getRandom().nextFloat() * 0.2F);
    }

    private boolean attachedVisual(SpellDefinition spell) {
        return switch (spell) {
            case THORNS, STATIC_FIELD, CORROSION, RESONANCE, WATER_PRISON, ICE_COFFIN, PLAGUE -> true;
            default -> false;
        };
    }

    private void applySelfBuff(SpellDefinition spell) {
        switch (spell) {
            case MOISTURE, REJUVENATION, PURIFYING_LIGHT -> heal(spell == SpellDefinition.REJUVENATION ? 12.0F : 8.0F);
            case ARCANE_WARD, ICE_SHIELD, STONE_ARMOR, IRON_WALL ->
                    addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, spell.isIntermediate() ? 400 : 240, 0));
            case ARCANE_SURGE -> addEffect(new MobEffectInstance(MobEffects.REGENERATION, 360, 1));
            case FLAME_BLESSING -> addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
            case LIGHTNESS, GALE_DASH -> addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            case LIFE_BLESSING -> {
                addEffect(new MobEffectInstance(MobEffects.REGENERATION, 800, 1));
                addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 800, 1));
            }
            case GLIMMER -> addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0));
            case MUSICAL_INSPIRATION -> {
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
                addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0));
            }
            case STATIC_FIELD -> addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0));
            default -> { }
        }
    }

    private void castOffensive(SpellDefinition spell, LivingEntity target) {
        float damage = switch (spell) {
            case ARCANE_RAY -> 10.0F;
            case FIREBALL, ICE_SPIKE, ELECTRIC_SHOCK, ROCKFALL, SHARP_EDGE, LIGHT_BOLT -> 8.0F;
            case WATER_BOLT, WIND_BLADE, THORNS, POISON_BOLT, SONIC_WAVE -> 6.0F;
            case PYROBLAST, THUNDER_STRIKE, ROCK_ORBS, HOLY_BEAM -> 22.0F;
            case WATER_PYTHON, GALE_SLASHES, SHATTERING_SOUND -> 18.0F;
            case ICE_COFFIN, PLAGUE -> 14.0F;
            case SWORD_SUMMON -> 16.0F;
            default -> 7.0F;
        };
        ElementType element = spell.element();
        if (spell == SpellDefinition.PYROBLAST || spell == SpellDefinition.THUNDER_STRIKE || spell == SpellDefinition.PLAGUE
                || spell == SpellDefinition.SHATTERING_SOUND) {
            for (LivingEntity victim : NewSpellSystem.enemies(this, target.position(), 3.5))
                NewSpellSystem.damage(this, victim, damage, element);
        } else NewSpellSystem.damage(this, target, damage, element);

        switch (spell) {
            case FIREBALL, PYROBLAST -> target.setRemainingFireTicks(80);
            case ICE_SPIKE, ICE_COFFIN -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            case THORNS, WATER_PRISON -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            case POISON_BOLT, PLAGUE -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 0));
            case ELECTRIC_SHOCK, THUNDER_STRIKE, RESONANCE -> NewSpellSystem.control(this, target, NewSpellContent.STUN, 40);
            case WATER_BOLT, WATER_PYTHON, SONIC_WAVE, SHATTERING_SOUND -> NewSpellSystem.knock(position(), target, 3);
            default -> { }
        }
    }

    private void cancelCharge() {
        if (chargingSpell != null) SpellVisualPayload.send(this, chargingSpell, SpellVisualPayload.CANCEL, -1,
                position(), position(), 1);
        chargingSpell = null;
        chargingTarget = null;
        chargeTicks = 0;
        entityData.set(CASTING, false);
    }

    private void tickMelee(LivingEntity target) {
        getNavigation().stop();
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (meleeCooldown > 0 || !hasLineOfSight(target)) return;
        swing(InteractionHand.MAIN_HAND, true);
        doHurtTarget(target);
        meleeCooldown = MELEE_INTERVAL_TICKS;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
        return super.doHurtTarget(entity);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (amount > 0.0F) lastDamageTick = tickCount;
    }

    // 掉落改由数据包战利品表 data/dimensional_mana/loot_table/entities/adventurer.json 提供，
    // 这样 JEI 的附属 Just Enough Resources 才能读取并在 JEI 里展示。

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("KnownSpells", knownSpells.stream().map(SpellDefinition::id).collect(Collectors.joining(",")));
        tag.putInt("SpellLoadoutVersion", spellLoadoutVersion);
        tag.putBoolean("UsedGoldenApple", usedGoldenApple);
        tag.putInt("SpellCooldown", spellCooldown);
        tag.putInt("SupplyCooldown", supplyCooldown);
        tag.putInt("SpeedPotionCooldown", speedPotionCooldown);
        tag.putLong("VillageStayUntil", villageStayUntil);
        if (villageTarget != null) tag.putLong("VillageTarget", villageTarget.asLong());
        if (lastVillage != null) tag.putLong("LastVillage", lastVillage.asLong());
        ListTag trades = new ListTag();
        for (AdventurerTrades.Offer offer : tradeOffers) trades.add(offer.save(registryAccess()));
        tag.put("TradeOffers", trades);
        tag.putLong("TradeRestockAt", tradeRestockAt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        knownSpells.clear();
        String saved = tag.getString("KnownSpells");
        if (!saved.isBlank()) for (String id : saved.split(",")) {
            SpellDefinition spell = SpellDefinition.byId(id);
            if (spell != null) knownSpells.add(spell);
        }
        spellLoadoutVersion = tag.getInt("SpellLoadoutVersion");
        usedGoldenApple = tag.getBoolean("UsedGoldenApple");
        spellCooldown = tag.getInt("SpellCooldown");
        supplyCooldown = tag.getInt("SupplyCooldown");
        speedPotionCooldown = tag.getInt("SpeedPotionCooldown");
        villageStayUntil = tag.getLong("VillageStayUntil");
        villageTarget = tag.contains("VillageTarget") ? BlockPos.of(tag.getLong("VillageTarget")) : null;
        lastVillage = tag.contains("LastVillage") ? BlockPos.of(tag.getLong("LastVillage")) : null;
        tradeOffers.clear();
        ListTag trades = tag.getList("TradeOffers", Tag.TAG_COMPOUND);
        for (int i = 0; i < trades.size() && tradeOffers.size() < AdventurerTrades.OFFER_COUNT; i++) {
            AdventurerTrades.Offer offer = AdventurerTrades.Offer.load(registryAccess(), trades.getCompound(i));
            if (offer != null) tradeOffers.add(offer);
        }
        tradeRestockAt = tag.getLong("TradeRestockAt");
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }

    private final class VillageTravelGoal extends Goal {
        VillageTravelGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            if (!(level() instanceof ServerLevel level) || !canAct() || getTarget() != null) return false;
            if (level.getGameTime() < villageStayUntil) return false;
            if (villageTarget == null && lastVillage == null && level.isVillage(blockPosition())) {
                villageTarget = blockPosition().immutable();
                beginVillageStay(level);
                return false;
            }
            return ensureVillageTarget(level);
        }

        @Override public boolean canContinueToUse() { return canUse() && villageTarget != null; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            if (speedPotionCooldown <= 0 && !hasEffect(MobEffects.MOVEMENT_SPEED)) {
                beginSupply(5, PotionContents.createItemStack(Items.POTION, Potions.STRONG_SWIFTNESS), 32);
            }
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level) || villageTarget == null) return;
            if (arrivedAtVillage() || level.isVillage(blockPosition()) && blockPosition().closerThan(villageTarget, 64.0)) {
                beginVillageStay(level);
                return;
            }
            if (getNavigation().isDone() || tickCount % 20 == 0) {
                Vec3 destination = villageTarget.getCenter();
                Vec3 waypoint = DefaultRandomPos.getPosTowards(Adventurer.this, 24, 8, destination, Math.PI / 3.0);
                if (waypoint != null) getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 1.0);
                else getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
            }
            setSprinting(getNavigation().isInProgress());
        }
    }

    private final class RangedPositionGoal extends Goal {
        RangedPositionGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            LivingEntity target = getTarget();
            return canAct() && target != null && target.isAlive() && distanceToSqr(target) > 4.0;
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = distanceToSqr(target);
            if (retreatTicks > 0 || distance < 6.0 * 6.0) {
                retreatTicks = Math.max(0, retreatTicks - 1);
                if (getNavigation().isDone() || tickCount % 8 == 0) {
                    Vec3 away = DefaultRandomPos.getPosAway(Adventurer.this, 10, 5, target.position());
                    if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.0);
                }
            } else if (distance > 10.0 * 10.0 || spellCooldown > 0 || !hasLineOfSight(target)) {
                // 冷却期间主动追击；setSprinting 本身不会移动，必须持续给导航目标。
                getNavigation().moveTo(target, 1.0);
            } else getNavigation().stop();
        }
    }
}
