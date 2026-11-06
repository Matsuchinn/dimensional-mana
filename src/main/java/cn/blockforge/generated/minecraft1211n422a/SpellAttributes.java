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
    public static final DeferredHolder<Attribute, Attribute> SPELL_PENETRATION = register("spell_penetration", 0.0, 0.0, 0.95);
    public static final DeferredHolder<Attribute, Attribute> PHYSICAL_CRIT_CHANCE = register("physical_crit_chance", 0.0, 0.0, 1.0);
    public static final DeferredHolder<Attribute, Attribute> PHYSICAL_CRIT_DAMAGE = register("physical_crit_damage", 0.0, 0.0, 100.0);
    public static final DeferredHolder<Attribute, Attribute> MANA_COST_REDUCTION = register("mana_cost_reduction", 0.0, 0.0, 0.95);
    public static final DeferredHolder<Attribute, Attribute> SPELL_RESISTANCE = register("spell_resistance", 0.0, 0.0, 0.95);

    // ------------------------------------------------------------------
    // 词条属性：以前这些数值只存在物品 NBT 里、由 EquipmentAffixSystem 现场求和，
    // 外部（Apothic Attributes 这类属性面板/检测模组）完全看不到。现在每条词条都对应
    // 一个真实注册的 Attribute，词条刷新时写成属性修正，于是面板能显示、其它模组也能读到。
    // 单位统一用“百分数点数”（3.5 就是 +3.5%），只有 ratio 类属性仍用 0~1。
    // ------------------------------------------------------------------
    /** 魔素上限百分比。 */
    public static final DeferredHolder<Attribute, Attribute> MANA_MAX_PERCENT = register("mana_max_percent", 0.0, 0.0, 1000.0);
    /** 魔素上限固定值。 */
    public static final DeferredHolder<Attribute, Attribute> MANA_MAX_FLAT = register("mana_max_flat", 0.0, 0.0, 100000.0);
    /** 魔素亲和。 */
    public static final DeferredHolder<Attribute, Attribute> MANA_AFFINITY = register("mana_affinity", 0.0, 0.0, 10000.0);
    /** 元素精通。 */
    public static final DeferredHolder<Attribute, Attribute> ELEMENTAL_MASTERY = register("elemental_mastery", 0.0, 0.0, 10000.0);
    /** 通用元素伤害加成（百分数）。 */
    public static final DeferredHolder<Attribute, Attribute> ELEMENTAL_DAMAGE = register("elemental_damage", 0.0, 0.0, 1000.0);
    /** 斗气上限百分比。 */
    public static final DeferredHolder<Attribute, Attribute> BATTLE_QI_PERCENT = register("battle_qi_percent", 0.0, 0.0, 1000.0);
    /** 斗气上限固定值。 */
    public static final DeferredHolder<Attribute, Attribute> BATTLE_QI_FLAT = register("battle_qi_flat", 0.0, 0.0, 100000.0);

    /** 11 种初级元素的单元素伤害加成（百分数），键就是 {@link ElementType#id()}。 */
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_FIRE = register("element_fire", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_WATER = register("element_water", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ICE = register("element_ice", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_LIGHTNING = register("element_lightning", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_WIND = register("element_wind", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_EARTH = register("element_earth", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_NATURE = register("element_nature", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_METAL = register("element_metal", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_LIGHT = register("element_light", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_POISON = register("element_poison", 0.0, 0.0, 1000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_SOUND = register("element_sound", 0.0, 0.0, 1000.0);
    /** 奥术不属于 11 种初级元素，但词条池里有，也单独挂一条。 */
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ARCANE = register("element_arcane", 0.0, 0.0, 1000.0);

    // ------------------------------------------------------------------
    // r332 物理词条扩充：破甲、吸血、斗气恢复 / 技强、连击、处决、见切、招架窗口、箭矢伤害。
    // 和上面一样，每条都是真实注册的属性，词条刷新时写成属性修正，外部属性面板读得到。
    // 单位统一：百分数点数（12 表示 +12%）、点数、每秒点数，注释逐条写清。
    // ------------------------------------------------------------------
    /** 破甲：攻击时无视目标这个点数的护甲值。 */
    public static final DeferredHolder<Attribute, Attribute> ARMOR_PENETRATION = register("armor_penetration", 0.0, 0.0, 10000.0);
    /** 物理吸血：造成的物理伤害按这个百分数转化为自身生命。 */
    public static final DeferredHolder<Attribute, Attribute> LIFESTEAL = register("lifesteal_percent", 0.0, 0.0, 100.0);
    /** 斗气额外恢复：每秒多恢复多少点斗气。 */
    public static final DeferredHolder<Attribute, Attribute> BATTLE_QI_REGEN = register("battle_qi_regen", 0.0, 0.0, 1000.0);
    /** 斗气技能强度：每点让拔刀斩、剑气的伤害 +2.5%。 */
    public static final DeferredHolder<Attribute, Attribute> BATTLE_QI_SKILL_POWER = register("battle_qi_skill_power", 0.0, 0.0, 10000.0);
    /** 连击伤害：连续命中同一目标时逐层增伤，最多 4 层，单位是百分数。 */
    public static final DeferredHolder<Attribute, Attribute> COMBO_DAMAGE = register("combo_damage_percent", 0.0, 0.0, 1000.0);
    /** 处决伤害：目标进入处决线时的额外伤害，单位是百分数。 */
    public static final DeferredHolder<Attribute, Attribute> EXECUTE_DAMAGE = register("execute_damage_percent", 0.0, 0.0, 1000.0);
    /** 见切伤害：对处在招架破绽中的目标额外的伤害，单位是百分数。 */
    public static final DeferredHolder<Attribute, Attribute> MIKIRI_DAMAGE = register("mikiri_damage_percent", 0.0, 0.0, 1000.0);
    /** 招架特化：完美格挡判定窗口额外延长多少刻。 */
    public static final DeferredHolder<Attribute, Attribute> PARRY_WINDOW = register("parry_window_bonus", 0.0, 0.0, 100.0);
    /** 箭矢伤害固定值：弓 / 弩命中的额外伤害。 */
    public static final DeferredHolder<Attribute, Attribute> ARROW_DAMAGE_FLAT = register("arrow_damage_flat", 0.0, 0.0, 10000.0);
    /** 箭矢伤害百分比：弓 / 弩命中的额外伤害，单位是百分数。 */
    public static final DeferredHolder<Attribute, Attribute> ARROW_DAMAGE_PERCENT = register("arrow_damage_percent", 0.0, 0.0, 1000.0);

    /**
     * 元素附加（12 系）：物理命中时额外附带这个点数的对应元素伤害。
     *
     * <p>「元素附加」在词条池里只占一个坑位，抽到之后再随机决定是哪一系，
     * 所以这里按元素各挂一条属性，属性和面板上都能看到实际附加的是哪一系、多少点。</p>
     */
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_FIRE = register("element_addition_fire", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_WATER = register("element_addition_water", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_ICE = register("element_addition_ice", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_LIGHTNING = register("element_addition_lightning", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_WIND = register("element_addition_wind", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_EARTH = register("element_addition_earth", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_NATURE = register("element_addition_nature", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_METAL = register("element_addition_metal", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_LIGHT = register("element_addition_light", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_POISON = register("element_addition_poison", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_SOUND = register("element_addition_sound", 0.0, 0.0, 10000.0);
    public static final DeferredHolder<Attribute, Attribute> ELEMENT_ADDITION_ARCANE = register("element_addition_arcane", 0.0, 0.0, 10000.0);

    private static final java.util.List<DeferredHolder<Attribute, Attribute>> AFFIX_ATTRIBUTES = java.util.List.of(
            MANA_MAX_PERCENT, MANA_MAX_FLAT, MANA_AFFINITY, ELEMENTAL_MASTERY, ELEMENTAL_DAMAGE,
            BATTLE_QI_PERCENT, BATTLE_QI_FLAT,
            ARMOR_PENETRATION, LIFESTEAL, BATTLE_QI_REGEN, BATTLE_QI_SKILL_POWER, COMBO_DAMAGE,
            EXECUTE_DAMAGE, MIKIRI_DAMAGE, PARRY_WINDOW, ARROW_DAMAGE_FLAT, ARROW_DAMAGE_PERCENT,
            ELEMENT_ADDITION_FIRE, ELEMENT_ADDITION_WATER, ELEMENT_ADDITION_ICE, ELEMENT_ADDITION_LIGHTNING,
            ELEMENT_ADDITION_WIND, ELEMENT_ADDITION_EARTH, ELEMENT_ADDITION_NATURE, ELEMENT_ADDITION_METAL,
            ELEMENT_ADDITION_LIGHT, ELEMENT_ADDITION_POISON, ELEMENT_ADDITION_SOUND, ELEMENT_ADDITION_ARCANE,
            ELEMENT_FIRE, ELEMENT_WATER, ELEMENT_ICE, ELEMENT_LIGHTNING, ELEMENT_WIND, ELEMENT_EARTH,
            ELEMENT_NATURE, ELEMENT_METAL, ELEMENT_LIGHT, ELEMENT_POISON, ELEMENT_SOUND, ELEMENT_ARCANE);

    /** 按 {@link ElementType#id()}（以及 {@code arcane}）取单元素伤害附加属性。 */
    public static Holder<Attribute> elementAddition(String elementId) {
        if (elementId == null) return ELEMENT_ADDITION_FIRE;
        return switch (elementId) {
            case "fire" -> ELEMENT_ADDITION_FIRE;
            case "water" -> ELEMENT_ADDITION_WATER;
            case "ice" -> ELEMENT_ADDITION_ICE;
            case "lightning" -> ELEMENT_ADDITION_LIGHTNING;
            case "wind" -> ELEMENT_ADDITION_WIND;
            case "earth" -> ELEMENT_ADDITION_EARTH;
            case "nature" -> ELEMENT_ADDITION_NATURE;
            case "metal" -> ELEMENT_ADDITION_METAL;
            case "light" -> ELEMENT_ADDITION_LIGHT;
            case "poison" -> ELEMENT_ADDITION_POISON;
            case "sound" -> ELEMENT_ADDITION_SOUND;
            case "arcane" -> ELEMENT_ADDITION_ARCANE;
            default -> ELEMENT_ADDITION_FIRE;
        };
    }

    /** 按 {@link ElementType#id()}（以及 {@code arcane}）取单元素伤害属性。 */
    public static Holder<Attribute> elementDamage(String elementId) {
        if (elementId == null) return ELEMENTAL_DAMAGE;
        return switch (elementId) {
            case "fire" -> ELEMENT_FIRE;
            case "water" -> ELEMENT_WATER;
            case "ice" -> ELEMENT_ICE;
            case "lightning" -> ELEMENT_LIGHTNING;
            case "wind" -> ELEMENT_WIND;
            case "earth" -> ELEMENT_EARTH;
            case "nature" -> ELEMENT_NATURE;
            case "metal" -> ELEMENT_METAL;
            case "light" -> ELEMENT_LIGHT;
            case "poison" -> ELEMENT_POISON;
            case "sound" -> ELEMENT_SOUND;
            case "arcane" -> ELEMENT_ARCANE;
            default -> ELEMENTAL_DAMAGE;
        };
    }

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
            add(event, type, SPELL_PENETRATION);
            add(event, type, PHYSICAL_CRIT_CHANCE);
            add(event, type, PHYSICAL_CRIT_DAMAGE);
            add(event, type, MANA_COST_REDUCTION);
            add(event, type, SPELL_RESISTANCE);
            for (DeferredHolder<Attribute, Attribute> attribute : AFFIX_ATTRIBUTES) add(event, type, attribute);
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

    /** 施法者的魔力通透会穿透目标法术抗性；穿透值按百分比词条换算为 0~0.95。 */
    public static double applySpellResistance(LivingEntity caster, LivingEntity target, double damage) {
        double resistance = value(target, SPELL_RESISTANCE, 0.0);
        double penetration = Math.max(0.0, Math.min(0.95, value(caster, SPELL_PENETRATION, 0.0)));
        return Math.max(0.0, damage * (1.0 - Math.max(0.0, resistance - penetration)));
    }

    /** 供本模组法术统一调用：直接伤害吃完整法杖固定增伤，持续/机制伤害仅第一次吃 50%。 */
    public static float calculateSpellDamage(LivingEntity caster, LivingEntity target, float baseDamage) {
        return (float)applySpellResistance(caster, target, calculateRawSpellDamage(caster, target, baseDamage, 1.0));
    }

    public static float calculateSecondarySpellDamage(LivingEntity caster, LivingEntity target, float baseDamage,
                                                       boolean firstInstance) {
        return (float)applySpellResistance(caster, target,
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
