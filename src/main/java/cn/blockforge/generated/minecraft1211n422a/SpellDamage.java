package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全部法术伤害的统一出口：让魔法伤害无视原版的“受击冷却”（俗称无敌帧）。
 *
 * <p>原版 {@code LivingEntity#hurt} 会在短时间内吞掉较低的重复伤害，或只结算比上一击
 * 多出来的差值。专用伤害类型 {@code dimensional_mana:spell_pierce} 登记在
 * {@code minecraft:bypasses_cooldown} 标签中，因此大多数法术由原版规则直接放行；少数需要
 * 保留物理、冻结等原版伤害类型的法术，则由 {@link #apply} 临时移开受击计时。</p>
 *
 * <p>临时移开的计时会在结算后恢复，并保留本次伤害新产生的普通保护时间。这样连续法术
 * 每一发都能吃满，但法术不会顺带替紧随其后的普通近战或箭矢清除保护时间。</p>
 *
 * <p>所有法术伤害都必须走 {@link #apply} 或 {@link #hit}，否则仍可能被无敌帧吞掉。</p>
 *
 * <p>法术这个大类之下还按元素细分出十二条伤害类型（{@code spell_fire} … {@code spell_arcane}）。
 * 它们在数据包里挂了与 {@code spell_pierce} 完全相同的标签，所以无视护甲、无视受击冷却、
 * 狼铠与女巫抗性这些行为一模一样，只是多带一个元素归属，供戒指增伤、元素反应与死亡消息区分。
 * 不属于任何元素的法术（机制伤害、反应跳伤）继续用通用的 {@code spell_pierce}。</p>
 */
public final class SpellDamage {
    private SpellDamage() {}

    /**
     * 以原版 {@code minecraft:indirect_magic} 为基准的专用法术伤害类型，额外无视受击冷却。
     */
    private static final ResourceKey<DamageType> PIERCE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "spell_pierce"));
    /**
     * 断魂鸣弦的高频声波：单独一条伤害类型，数据包里挂了护甲、盾牌、抗性、
     * 药水效果与附魔的穿透标签；吸收血量在原版没有对应标签，由 {@link #hitSonic} 临时移开。
     * 音元素接管之后这条类型只保留给旧存档与外部数据包引用。
     */
    private static final ResourceKey<DamageType> SONIC = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "sonic_pierce"));
    /** 十一种元素 + 奥术，各对应一条法术子类型；键与 {@link ElementType#id()} 一致。 */
    private static final Map<String, ResourceKey<DamageType>> ELEMENT_TYPES = createElementTypes();

    private static Map<String, ResourceKey<DamageType>> createElementTypes() {
        Map<String, ResourceKey<DamageType>> types = new LinkedHashMap<>();
        for (ElementType element : ElementType.values()) types.put(element.id(), key("spell_" + element.id()));
        types.put(MagicEquipment.ARCANE, key("spell_arcane"));
        return Collections.unmodifiableMap(types);
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, path));
    }

    private static boolean registered(Level level, ResourceKey<DamageType> type) {
        return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).containsKey(type);
    }

    /** 法术伤害源：优先用无视冷却的专用类型，数据包缺失时退回原版间接魔法伤害。 */
    public static DamageSource source(Level level, LivingEntity caster) {
        if (registered(level, PIERCE)) return level.damageSources().source(PIERCE, caster, caster);
        return level.damageSources().indirectMagic(caster, caster);
    }

    /**
     * 某个元素的法术伤害源。
     *
     * <p>数据包里缺这条元素类型时退回通用法术伤害，宁可少一个元素归属，也不能让法术哑火。</p>
     */
    public static DamageSource elementSource(Level level, LivingEntity caster, String elementId) {
        ResourceKey<DamageType> type = elementId == null ? null : ELEMENT_TYPES.get(elementId);
        if (type != null && registered(level, type)) return level.damageSources().source(type, caster, caster);
        return source(level, caster);
    }

    /** 一次伤害落地的结果：是否真的造成伤害，以及这一下是否暴击。 */
    private record SpellHit(boolean landed, boolean critical) { }

    /** 以 {@code caster} 为施法者的法术伤害。施法者显式传入，不再依赖伤害源反查。 */
    public static boolean hit(Level level, LivingEntity caster, LivingEntity target, float amount) {
        return applyDetailed(target, source(level, caster), caster, amount, true, true).landed();
    }

    /** 以某个元素的伤害类型结算一次法术伤害，暴击与刻蚀规则与 {@link #hit} 完全一致。 */
    public static boolean hitElement(Level level, LivingEntity caster, LivingEntity target,
                                     String elementId, float amount) {
        SpellHit hit = applyDetailed(target, elementSource(level, caster, elementId), caster, amount, true, true);
        // 共鸣核心只在真正造成对应元素伤害时累积充能，且必须这一下暴击。
        if (hit.landed()) ResonanceCore.onElementDamage(caster, elementId, hit.critical());
        return hit.landed();
    }

    /** 高频声波的伤害源：音元素已经接过 sonic_pierce 的穿透标签，缺类型时再退回旧类型。 */
    public static DamageSource sonicSource(Level level, LivingEntity caster) {
        ResourceKey<DamageType> sound = ELEMENT_TYPES.get(ElementType.SOUND.id());
        if (sound != null && registered(level, sound)) return level.damageSources().source(sound, caster, caster);
        if (registered(level, SONIC)) return level.damageSources().source(SONIC, caster, caster);
        return source(level, caster);
    }

    /** 判断一次伤害是不是高频声波（用来跳过金钟罩、磐石外壳等自定义防护）。 */
    public static boolean isSonic(DamageSource source) {
        if (source == null) return false;
        if (source.is(SONIC)) return true;
        ResourceKey<DamageType> sound = ELEMENT_TYPES.get(ElementType.SOUND.id());
        return sound != null && source.is(sound);
    }

    /** 伤害源对应的元素 id；不是元素法术伤害时返回 {@code null}。 */
    public static String elementIdOf(DamageSource source) {
        if (source == null) return null;
        for (Map.Entry<String, ResourceKey<DamageType>> entry : ELEMENT_TYPES.entrySet()) {
            if (source.is(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    /** 是否属于本模组的法术伤害大类（通用法术、十二种元素或旧声波类型）。 */
    public static boolean isSpell(DamageSource source) {
        if (source == null) return false;
        return source.is(PIERCE) || source.is(SONIC) || elementIdOf(source) != null;
    }

    /**
     * 结算一次高频声波伤害。
     *
     * <p>声波按设定无视护甲、盾牌、抗性、药水减伤与金钟罩这些防护，只吃斗气减伤
     * （斗气在受击事件里统一结算）；吸收血量在原版 1.21.1 没有穿透标签，
     * 这里在结算前临时归零、结算后原样放回，既打穿金苹果又不吞掉玩家已有的吸收量。</p>
     */
    public static boolean hitSonic(Level level, LivingEntity caster, LivingEntity target, float amount) {
        if (target == null || target.level().isClientSide || amount <= 0.0F) return false;
        SpellAttributes.SpellCriticalResult critical = SpellAttributes.rollSpellCritical(caster, amount);
        float finalAmount = MagicEtchingSystem.amplifySpellDamage(caster, critical.damage())
                * (float)ResonanceCore.transparencyDamageMultiplier(caster);
        if (finalAmount <= 0.0F) return false;
        int protectionBeforeSpell = target.invulnerableTime;
        float absorptionBefore = target.getAbsorptionAmount();
        target.invulnerableTime = 0;
        target.setAbsorptionAmount(0.0F);
        try {
            boolean landed = target.hurt(sonicSource(level, caster), finalAmount);
            if (landed) ResonanceCore.onElementDamage(caster, ElementType.SOUND.id(), critical.critical());
            if (landed && caster != null && critical.critical()) SpellAttributes.showSpellCritical(caster, target);
            if (landed && caster != null) MagicEtchingSystem.afterSpellDamage(caster, target);
            return landed;
        } finally {
            target.invulnerableTime = Math.max(protectionBeforeSpell, target.invulnerableTime);
            // 期间如果目标自己补上了吸收血量（金苹果、图腾之类），保留更多的那份。
            target.setAbsorptionAmount(Math.max(absorptionBefore, target.getAbsorptionAmount()));
        }
    }

    /** 已经完成结算的反应/回响伤害：不再次触发暴击、刻蚀增伤或刻蚀附伤。 */
    public static boolean hitResolved(Level level, LivingEntity caster, LivingEntity target, float amount) {
        return applyDetailed(target, source(level, caster), caster, amount, false, false).landed();
    }

    /** 已经结算过的元素伤害（反应跳伤、剑意之类）：同样不再暴击、不再吃刻蚀。 */
    public static boolean hitResolvedElement(Level level, LivingEntity caster, LivingEntity target,
                                             String elementId, float amount) {
        return applyDetailed(target, elementSource(level, caster, elementId), caster, amount, false, false).landed();
    }

    /**
     * 同一次法术拆成不同伤害类型时共用一次暴击结果与刻蚀倍率，避免每一段分别暴击。
     */
    public static boolean hitSplit(Level level, LivingEntity caster, LivingEntity target,
                                   float spellAmount, DamageSource secondarySource, float secondaryAmount) {
        return hitSplit(level, caster, target, source(level, caster), spellAmount, secondarySource, secondaryAmount);
    }

    /** 同上，但法术那一段用指定元素的伤害类型落地。 */
    public static boolean hitSplitElement(Level level, LivingEntity caster, LivingEntity target, String elementId,
                                          float spellAmount, DamageSource secondarySource, float secondaryAmount) {
        return hitSplit(level, caster, target, elementSource(level, caster, elementId),
                spellAmount, secondarySource, secondaryAmount);
    }

    private static boolean hitSplit(Level level, LivingEntity caster, LivingEntity target, DamageSource spellSource,
                                    float spellAmount, DamageSource secondarySource, float secondaryAmount) {
        if (target == null || target.level().isClientSide) return false;
        float total = Math.max(0.0F, spellAmount) + Math.max(0.0F, secondaryAmount);
        if (total <= 0.0F) return false;
        SpellAttributes.SpellCriticalResult critical = SpellAttributes.rollSpellCritical(caster, total);
        float resolved = MagicEtchingSystem.amplifySpellDamage(caster, critical.damage())
                * (float)ResonanceCore.transparencyDamageMultiplier(caster);
        float scale = resolved / total;
        boolean spellLanded = applyDetailed(target, spellSource, caster, spellAmount * scale, false, false).landed();
        boolean secondaryLanded = applyDetailed(target, secondarySource, caster,
                secondaryAmount * scale, false, false).landed();
        boolean landed = spellLanded || secondaryLanded;
        if (landed && critical.critical()) SpellAttributes.showSpellCritical(caster, target);
        if (landed) MagicEtchingSystem.afterSpellDamage(caster, target);
        return landed;
    }

    /**
     * 结算一次法术伤害，并保证它不被受击冷却吞掉。
     *
     * @return 与原版 {@code hurt} 一致：这次伤害是否真的落了地
     */
    public static boolean apply(LivingEntity target, DamageSource source, float amount) {
        LivingEntity caster = source.getEntity() instanceof LivingEntity living ? living : null;
        return applyDetailed(target, source, caster, amount, caster != null, caster != null).landed();
    }

    private static SpellHit applyDetailed(LivingEntity target, DamageSource source, LivingEntity caster, float amount,
                                          boolean applyCasterModifiers, boolean triggerAfterDamage) {
        if (target == null || target.level().isClientSide || amount <= 0.0F) return new SpellHit(false, false);
        SpellAttributes.SpellCriticalResult critical = new SpellAttributes.SpellCriticalResult(amount, false);
        float finalAmount = amount;
        if (caster != null && applyCasterModifiers) {
            // 暴击是一次额外伤害：本次伤害 = 原伤害 + 原伤害 * 暴击伤害倍率。
            critical = SpellAttributes.rollSpellCritical(caster, finalAmount);
            finalAmount = MagicEtchingSystem.amplifySpellDamage(caster, critical.damage());
            // 共鸣核心的元素共鸣期间“魔力通透”：全部法术伤害再吃 15% 独立乘区。
            finalAmount *= (float)ResonanceCore.transparencyDamageMultiplier(caster);
        }
        int protectionBeforeSpell = target.invulnerableTime;
        // 临时移开现有保护，让保留 freeze、playerAttack 等原版伤害类型的法术也能吃满。
        target.invulnerableTime = 0;
        try {
            boolean landed = target.hurt(source, finalAmount);
            if (landed && caster != null && critical.critical()) SpellAttributes.showSpellCritical(caster, target);
            if (landed && caster != null && triggerAfterDamage) MagicEtchingSystem.afterSpellDamage(caster, target);
            return new SpellHit(landed, landed && critical.critical());
        } finally {
            // hurt 成功时保留本次攻击新产生的保护，失败时完整恢复原状态；即使其他模组的
            // 伤害事件抛出异常，也不能让法术顺带清掉目标原有的保护时间。
            target.invulnerableTime = Math.max(protectionBeforeSpell, target.invulnerableTime);
        }
    }
}
