package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 本模组法术系统使用的通用生物属性。 */
public final class SpellAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, GeneratedMod.MOD_ID);

    public static final DeferredHolder<Attribute, Attribute> SPELL_POWER = register("spell_power", 1.0, 0.0, 10_000.0);
    public static final DeferredHolder<Attribute, Attribute> SPELL_HASTE = register("spell_haste", 1.0, 0.05, 100.0);
    public static final DeferredHolder<Attribute, Attribute> SPELL_CRIT_CHANCE = register("spell_crit_chance", 0.005, 0.0, 1.0);
    public static final DeferredHolder<Attribute, Attribute> SPELL_CRIT_DAMAGE = register("spell_crit_damage", 0.5, 0.0, 100.0);
    public static final DeferredHolder<Attribute, Attribute> MANA_COST_REDUCTION = register("mana_cost_reduction", 0.0, 0.0, 0.95);
    public static final DeferredHolder<Attribute, Attribute> SPELL_RESISTANCE = register("spell_resistance", 0.0, 0.0, 0.95);

    /** 作弊指令写入的暴击率修正标识；clear 时按它精确移除。 */
    private static final ResourceLocation CHEAT_CRIT_CHANCE =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "cheat_spell_crit_chance");

    private static DeferredHolder<Attribute, Attribute> register(String name, double defaultValue, double min, double max) {
        return ATTRIBUTES.register(name, () -> new RangedAttribute(
                "attribute.name." + GeneratedMod.MOD_ID + "." + name, defaultValue, min, max).setSyncable(true));
    }

    public static void attachToLivingEntities(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            add(event, type, SPELL_POWER);
            add(event, type, SPELL_HASTE);
            add(event, type, SPELL_CRIT_CHANCE);
            add(event, type, SPELL_CRIT_DAMAGE);
            add(event, type, MANA_COST_REDUCTION);
            add(event, type, SPELL_RESISTANCE);
        }
    }

    private static void add(EntityAttributeModificationEvent event, EntityType<? extends LivingEntity> type,
                            Holder<Attribute> attribute) {
        if (!event.has(type, attribute)) event.add(type, attribute);
    }

    public static double value(LivingEntity entity, Holder<Attribute> attribute, double fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    public static void setSkillBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    /** 每点魔素强度令最终法术伤害增加 0.21%；法杖与饰品通过真实属性修正参与这里的统一结算。 */
    public static double powerMultiplier(LivingEntity caster) {
        // 施法前再同步一次，保证玩家刚切换法杖、尚未经历下一个实体刻时也立刻取得正确数值。
        MagicStaffItem.syncSpellPowerAttribute(caster);
        return 1.0 + value(caster, SPELL_POWER, 1.0) * 0.0021;
    }

    public static double hasteMultiplier(LivingEntity caster) {
        return value(caster, SPELL_HASTE, 1.0) + MagicStaffItem.heldStats(caster).spellHaste();
    }

    /** 带法术上下文的减耗：侦探护符对预言学派的魔素消耗再打五折。 */
    public static double reducedManaCost(LivingEntity caster, SpellDefinition spell) {
        double cost = reducedManaCost(caster, spell == null ? 0.0 : spell.manaCost());
        return "divination".equals(spell == null ? "" : spell.school())
                && RelicEffects.worn(caster, RelicEffects.DETECTIVE_CHARM) ? cost * 0.5 : cost;
    }

    public static double reducedManaCost(LivingEntity caster, double rawCost) {
        double reduction = value(caster, MANA_COST_REDUCTION, 0.0)
                + MagicStaffItem.heldStats(caster).manaCostReduction();
        double cost = Math.max(0.0, rawCost * (1.0 - Math.min(0.95, reduction)));
        // 高等魔法刻蚀是独立乘区：其他减耗结算完毕后再乘 0.62，重复佩戴不叠加。
        return MagicEtchingSystem.equipped(caster) ? cost * 0.62 : cost;
    }

    public static double applySpellResistance(LivingEntity target, double damage) {
        double resistance = value(target, SPELL_RESISTANCE, 0.0);
        return Math.max(0.0, damage * (1.0 - resistance));
    }

    /** 供本模组法术统一调用：直接伤害吃完整法杖固定增伤，持续/机制伤害仅第一次吃 50%。 */
    public static float calculateSpellDamage(LivingEntity caster, LivingEntity target, float baseDamage) {
        return (float)applySpellResistance(target, calculateRawSpellDamage(caster, target, baseDamage, 1.0));
    }

    public static float calculateSecondarySpellDamage(LivingEntity caster, LivingEntity target, float baseDamage,
                                                       boolean firstInstance) {
        return (float)applySpellResistance(target,
                calculateRawSpellDamage(caster, target, baseDamage, firstInstance ? 0.5 : 0.0));
    }

    /**
     * 结算施法者一侧的全部直接伤害增幅，但暂不扣目标的法术抗性。
     * 供“无视法术抗性”或自行处理部分穿抗的法术使用，避免这些特殊路径漏掉法术暴击伤害。
     */
    public static float calculateUnresistedSpellDamage(LivingEntity caster, LivingEntity target, float baseDamage) {
        return (float)calculateRawSpellDamage(caster, target, baseDamage, 1.0);
    }

    private static double calculateRawSpellDamage(LivingEntity caster, LivingEntity target, float baseDamage,
                                                  double staffBonusScale) {
        return baseDamage * powerMultiplier(caster)
                + MagicStaffItem.heldStats(caster).spellDamage() * staffBonusScale;
    }

    /** 一次法术暴击判定的服务器端结果；把是否暴击和最终伤害绑在一起，避免只播放特效却漏掉增伤。 */
    public record SpellCriticalResult(float damage, boolean critical) { }

    /**
     * 在伤害真正落地前掷一次法术暴击。
     * 暴击总伤害严格等于：原伤害 + 原伤害 × 法术暴击伤害倍率。
     */
    public static SpellCriticalResult rollSpellCritical(LivingEntity caster, float damage) {
        if (damage <= 0.0F || caster == null || caster.hasEffect(NewSpellContent.ARCANE_SILENCE))
            return new SpellCriticalResult(damage, false);
        // 大师施法手套：每 5 次施法里的第 5 次，这一次伤害直接判定暴击，不看暴击率。
        boolean guaranteed = CastingGloveItem.consumeGuaranteedCrit(caster);
        double chance = critChance(caster);
        if (!guaranteed && caster.getRandom().nextDouble() >= chance) return new SpellCriticalResult(damage, false);
        // 共鸣核心的元素共鸣期间“魔力通透”额外提供 35% 法术暴击伤害。
        double multiplier = Math.max(0.0,
                value(caster, SPELL_CRIT_DAMAGE, 0.5) + ResonanceCore.transparencyCritDamage(caster));
        double finalDamage = damage + damage * multiplier;
        // 魔素增强护符：法术暴击时回复最大魔素的一部分，自带 2.2 秒触发冷却。
        MagicEquipment.onSpellCritical(caster);
        MagicEquipment.onRelicSpellCritical(caster);
        return new SpellCriticalResult((float)Math.min(Float.MAX_VALUE, finalDamage), true);
    }

    /**
     * 带目标的一次法术暴击判定：女巫这类「魔法暴击抗性 50%」的目标，
     * 会在暴击成立后按概率把这次暴击降级为普通命中（伤害回到未暴击值）。
     */
    public static SpellCriticalResult rollSpellCritical(LivingEntity caster, LivingEntity target, float damage) {
        SpellCriticalResult result = rollSpellCritical(caster, damage);
        if (result.critical() && target != null && MobCombatSystem.cancelsSpellCritical(target)) {
            return new SpellCriticalResult(damage, false);
        }
        return result;
    }

    /** 当前法术暴击率（0~1），已包含技能加点、法杖/饰品与作弊指令写入的修正。 */
    public static double critChance(LivingEntity caster) {
        // 元素共鸣期间的“魔力通透”额外提供 10% 法术暴击率。
        double chance = value(caster, SPELL_CRIT_CHANCE, 0.005)
                + ResonanceCore.transparencyCritChance(caster);
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /**
     * 只在暴击伤害确实被目标接受后，按原版的方式把“这一下是暴击”广播给客户端。
     *
     * <p>原版的暴击并不靠伤害数值标识：{@code ServerPlayer#crit} 会给看得见目标的客户端发一个
     * {@code action = ClientboundAnimatePacket.CRITICAL_HIT}（4）的动画包，客户端据此在目标身上
     * 生成 {@code ParticleTypes.CRIT} 星辰粒子，并配上 {@code PLAYER_ATTACK_CRIT} 音效。伤害数字、
     * 试验假人这类模组也是靠这个包和粒子判定“这一击是暴击”。以前这里只发
     * {@code MAGIC_CRITICAL_HIT}（5，附魔命中粒子），那是原版附魔/横扫用的特效，外部不认作暴击，
     * 于是暴击伤害虽然结算了，看起来却像普通命中。
     *
     * <p>现在两种粒子都发：物理暴击包让外部系统认得出暴击，魔法暴击包保留法术观感；
     * 以目标为锚点广播，怪物等其他施法者也能正常显示。
     */
    public static void showSpellCritical(LivingEntity caster, LivingEntity target) {
        if (target == null || !(target.level() instanceof ServerLevel level)) return;
        SoundSource soundSource = caster == null ? SoundSource.PLAYERS : caster.getSoundSource();
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, soundSource, 1.0F, 1.0F);
        level.getChunkSource().broadcastAndSend(target,
                new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
        level.getChunkSource().broadcastAndSend(target,
                new ClientboundAnimatePacket(target, ClientboundAnimatePacket.MAGIC_CRITICAL_HIT));
        // 再补一层原版暴击星辰粒子：有的伤害显示 mod 是在客户端监听粒子包（handleLevelParticles）
        // 认暴击的，动画包生成的追踪粒子它们看不到，两条通道一起发才稳。
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5,
                target.getZ(), 8, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3,
                target.getBbWidth() * 0.4, 0.12);
    }

    /** 作弊指令：把法术暴击率直接设成 {@code chance}（0~1），叠加在技能加点之上而不是覆盖它。 */
    public static void setCheatCritChance(LivingEntity entity, double chance) {
        AttributeInstance instance = entity.getAttribute(SPELL_CRIT_CHANCE);
        if (instance == null) return;
        double natural = instance.getValue() - cheatCritChance(entity);
        instance.removeModifier(CHEAT_CRIT_CHANCE);
        instance.addOrUpdateTransientModifier(new AttributeModifier(CHEAT_CRIT_CHANCE,
                chance - natural, AttributeModifier.Operation.ADD_VALUE));
    }

    /** 作弊指令：清除强制的暴击率，返回是否真的移除了什么。 */
    public static boolean clearCheatCritChance(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(SPELL_CRIT_CHANCE);
        return instance != null && instance.removeModifier(CHEAT_CRIT_CHANCE);
    }

    /** 作弊指令追加的暴击率；没有时返回 0。 */
    public static double cheatCritChance(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(SPELL_CRIT_CHANCE);
        if (instance == null) return 0.0;
        AttributeModifier modifier = instance.getModifier(CHEAT_CRIT_CHANCE);
        return modifier == null ? 0.0 : modifier.amount();
    }

    private SpellAttributes() {}
}
