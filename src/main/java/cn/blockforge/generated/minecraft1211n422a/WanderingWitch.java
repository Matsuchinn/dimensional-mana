package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 古代祭坛的守卫：保留女巫基础 AI，并使用本模组的魔素与初级法术。 */
public final class WanderingWitch extends Witch {
    private static final SpellDefinition[] COMBAT_SPELLS = {
            SpellDefinition.FIREBALL, SpellDefinition.WATER_BOLT, SpellDefinition.ICE_SPIKE,
            SpellDefinition.ELECTRIC_SHOCK, SpellDefinition.WIND_BLADE, SpellDefinition.ROCKFALL,
            SpellDefinition.THORNS, SpellDefinition.SHARP_EDGE, SpellDefinition.LIGHT_BOLT,
            SpellDefinition.POISON_BOLT, SpellDefinition.SONIC_WAVE, SpellDefinition.STATIC_FIELD,
            SpellDefinition.CORROSION, SpellDefinition.RESONANCE
    };

    private static final int SPELL_LOADOUT_VERSION = 2;

    private final List<SpellDefinition> knownSpells = new ArrayList<>();
    private final Map<SpellDefinition, Integer> spellUses = new EnumMap<>(SpellDefinition.class);
    private SpellDefinition chargingSpell;
    private SpellDefinition lastSpell;
    private UUID chargingTarget;
    private int chargeTicks;
    private int lostSightTicks;
    private int spellCooldown = 30;
    private int manaPotionTicks;
    private int spellLoadoutVersion;

    public WanderingWitch(EntityType<? extends Witch> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Witch.createAttributes().add(Attributes.MAX_HEALTH, 50.0);
    }

    /**
     * 原版女巫会把带 witch_resistant_to 标签的魔法伤害压到 15%。游离女巫本身也是
     * 施法生物，但不能因此近乎免疫本模组法术；仅跳过女巫特有减免，其余保护仍交给
     * LivingEntity 的标准伤害流程处理。
     */
    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float damage) {
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) return damage;
        return super.getDamageAfterMagicAbsorb(source, damage);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData);
        rollSpells();
        ManaData.setMobPools(this, 1500.0, 300);
        setHealth(getMaxHealth());
        if (reason == MobSpawnType.STRUCTURE) setPersistenceRequired();
        return result;
    }

    private void rollSpells() {
        if (!knownSpells.isEmpty() && spellLoadoutVersion >= SPELL_LOADOUT_VERSION) return;
        // 原池子只抽 2～4 个法术，导致单个法术和元素组合在多数女巫身上根本不存在。
        // 游离女巫现在掌握完整战斗池，施法时用公平轮换保留随机感。
        knownSpells.clear();
        knownSpells.addAll(List.of(COMBAT_SPELLS));
        spellLoadoutVersion = SPELL_LOADOUT_VERSION;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        rollSpells();
        if (NewSpellSystem.cannotCast(this)) {
            if (chargeTicks > 0) cancelCharge();
            if (NewSpellSystem.immobilized(this)) getNavigation().stop();
            return;
        }
        tickManaPotion(level);
        if (manaPotionTicks > 0) return;

        LivingEntity target = lockedTarget(level);
        if (chargeTicks > 0) {
            if (!validTarget(target)) {
                cancelCharge();
                return;
            }
            // 前摇锁定启动时的目标；短暂被方块遮挡不立刻重置，避免反复卡在第一帧。
            lostSightTicks = hasLineOfSight(target) ? 0 : lostSightTicks + 1;
            if (lostSightTicks > 12) {
                cancelCharge();
                return;
            }
            getNavigation().stop();
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--chargeTicks == 0) finishSpell(level, target);
            return;
        }
        target = getTarget();
        if (spellCooldown > 0) {
            spellCooldown--;
            return;
        }
        if (!validTarget(target) || !hasLineOfSight(target)) return;

        LivingEntity spellTarget = target;
        double mana = ManaData.get(this).mana();
        List<SpellDefinition> affordable = knownSpells.stream().filter(spell -> mana >= spell.manaCost()).toList();
        if (affordable.isEmpty()) {
            spellCooldown = 20;
            return;
        }
        List<SpellDefinition> reactions = affordable.stream()
                .filter(spell -> ElementReactionSystem.wouldReact(spellTarget, spell.element())).toList();
        SpellDefinition spell = chooseFairly(reactions.isEmpty() ? affordable : reactions);
        chargingSpell = spell;
        chargingTarget = target.getUUID();
        lostSightTicks = 0;
        chargeTicks = Mth.clamp(spell.castTicks() / 2, 16, 42);
        SpellVisualPayload.send(this, spell, SpellVisualPayload.CHARGE, getId(),
                getEyePosition(), getEyePosition(), chargeTicks);
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

    private LivingEntity lockedTarget(ServerLevel level) {
        if (chargingTarget == null) return null;
        return level.getEntity(chargingTarget) instanceof LivingEntity living ? living : null;
    }

    private boolean validTarget(LivingEntity target) {
        return target != null && target.isAlive() && distanceToSqr(target) <= 14.0 * 14.0;
    }

    private void finishSpell(ServerLevel level, LivingEntity target) {
        SpellDefinition spell = chargingSpell;
        chargingSpell = null;
        chargingTarget = null;
        lostSightTicks = 0;
        spellCooldown = 45 + getRandom().nextInt(46);
        if (spell == null || !ManaData.spendMana(this, spell.manaCost()) || !validTarget(target)) return;
        lastSpell = spell;
        spellUses.merge(spell, 1, Integer::sum);
        if (spellUses.values().stream().mapToInt(Integer::intValue).min().orElse(0) >= 20) {
            spellUses.replaceAll((known, uses) -> uses - 20);
        }

        Vec3 start = getEyePosition().add(getLookAngle().scale(0.35));
        Vec3 end = target.getBoundingBox().getCenter();
        int duration = switch (spell) {
            case ROCKFALL -> 36;
            case STATIC_FIELD -> 44;
            case CORROSION, RESONANCE -> 80;
            default -> 24;
        };
        int anchor = switch (spell) {
            // 落石固定在施法时记录的落点，不能因目标死亡或移动而丢失动画。
            case THORNS, STATIC_FIELD, CORROSION, RESONANCE -> target.getId();
            default -> -1;
        };
        // 先同步动画再伤害；致死一击也能完整播放。
        SpellVisualPayload.send(this, spell, SpellVisualPayload.RELEASE, anchor, start, end, duration);
        float damage = switch (spell) {
            case FIREBALL -> 8.0F;
            case WATER_BOLT, THORNS, SONIC_WAVE -> 6.0F;
            case ICE_SPIKE, LIGHT_BOLT -> 7.0F;
            case ELECTRIC_SHOCK -> 10.0F;
            case WIND_BLADE -> 5.0F;
            case ROCKFALL -> 12.0F;
            case SHARP_EDGE -> 9.0F;
            case POISON_BOLT -> 4.0F;
            default -> 0.0F;
        };
        ElementType element = spell.element();
        if (damage > 0.0F) NewSpellSystem.damage(this, target, damage, element);
        else ElementReactionSystem.apply(this, target, element);

        switch (spell) {
            case FIREBALL -> target.setRemainingFireTicks(60);
            case WATER_BOLT, SONIC_WAVE -> knockAway(target, spell == SpellDefinition.SONIC_WAVE ? 1.5 : 0.9);
            case ICE_SPIKE -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            case ROCKFALL, STATIC_FIELD, RESONANCE -> {
                int ticks = spell == SpellDefinition.RESONANCE ? 80 : spell == SpellDefinition.STATIC_FIELD ? 44 : 20;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 4));
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 3));
            }
            case THORNS -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            case POISON_BOLT, CORROSION -> target.addEffect(new MobEffectInstance(MobEffects.POISON,
                    spell == SpellDefinition.CORROSION ? 160 : 120, 0));
            default -> { }
        }
    }

    private void knockAway(LivingEntity target, double power) {
        Vec3 horizontal = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
        if (horizontal.lengthSqr() < 0.001) horizontal = getLookAngle().multiply(1.0, 0.0, 1.0);
        horizontal = horizontal.normalize().scale(power);
        target.push(horizontal.x, 0.18, horizontal.z);
        target.hurtMarked = true;
    }

    private void tickManaPotion(ServerLevel level) {
        ManaData.Snapshot mana = ManaData.get(this);
        if (manaPotionTicks <= 0 && mana.mana() < 300.0 && getRandom().nextInt(20) == 0) {
            manaPotionTicks = 32;
            setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(GeneratedMod.MOD_ITEMS.get("lesser_mana_potion").get()));
            setUsingItem(true);
        }
        if (manaPotionTicks <= 0) return;
        getNavigation().stop();
        if (--manaPotionTicks == 0) {
            ManaData.restoreMana(this, ManaData.get(this).maxMana() * 0.025 + 64.0, true);
            setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            setUsingItem(false);
            level.broadcastEntityEvent(this, (byte)9);
            spellCooldown = Math.max(spellCooldown, 20);
        }
    }

    private void cancelCharge() {
        if (chargingSpell != null) SpellVisualPayload.send(this, chargingSpell, SpellVisualPayload.CANCEL, -1,
                position(), position(), 1);
        chargingSpell = null;
        chargingTarget = null;
        chargeTicks = 0;
        lostSightTicks = 0;
        spellCooldown = 20;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("DimensionalManaSpells", knownSpells.stream().map(SpellDefinition::id)
                .collect(java.util.stream.Collectors.joining(",")));
        tag.putInt("DimensionalManaSpellLoadoutVersion", spellLoadoutVersion);
        tag.putInt("DimensionalManaSpellCooldown", spellCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        knownSpells.clear();
        String saved = tag.getString("DimensionalManaSpells");
        if (!saved.isBlank()) for (String id : saved.split(",")) {
            SpellDefinition spell = SpellDefinition.byId(id);
            if (spell != null) knownSpells.add(spell);
        }
        spellLoadoutVersion = tag.getInt("DimensionalManaSpellLoadoutVersion");
        spellCooldown = Math.max(0, tag.getInt("DimensionalManaSpellCooldown"));
    }
}
