package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Locale;

/** 学派决定“做什么”，媒介决定“用什么形式的能量”。 */
public enum SpellDefinition {
    ARCANE_RAY("arcane_ray", "destruction", "arcane", 76, 45, 40),
    ARCANE_WARD("arcane_ward", "protection", "arcane", 90, 55, 200),
    ARCANE_SURGE("arcane_surge", "blessing", "arcane", 140, 80, 240),

    FIREBALL("fireball", "destruction", "fire", 40, 20, 24),
    FLAME_BLESSING("flame_blessing", "blessing", "fire", 30, 25, 80, true),
    WATER_BOLT("water_bolt", "destruction", "water", 36, 18, 20),
    MOISTURE("moisture", "healing", "water", 50, 35, 80, true),
    ICE_SPIKE("ice_spike", "destruction", "ice", 44, 22, 28),
    ICE_SHIELD("ice_shield", "protection", "ice", 30, 28, 120),
    ELECTRIC_SHOCK("electric_shock", "destruction", "lightning", 50, 25, 44),
    STATIC_FIELD("static_field", "control", "lightning", 60, 30, 90, true),
    WIND_BLADE("wind_blade", "destruction", "wind", 30, 16, 18),
    GALE_DASH("gale_dash", "movement", "wind", 20, 20, 50),
    ROCKFALL("rockfall", "destruction", "earth", 60, 35, 80, true),
    STONE_ARMOR("stone_armor", "protection", "earth", 40, 45, 160),
    THORNS("thorns", "curse", "nature", 40, 20, 36),
    REJUVENATION("rejuvenation", "healing", "nature", 60, 30, 100),
    SHARP_EDGE("sharp_edge", "destruction", "metal", 40, 24, 34),
    IRON_WALL("iron_wall", "protection", "metal", 50, 32, 140),
    LIGHT_BOLT("light_bolt", "destruction", "light", 40, 22, 28),
    PURIFYING_LIGHT("purifying_light", "healing", "light", 60, 35, 100),
    POISON_BOLT("poison_bolt", "destruction", "poison", 40, 20, 30),
    CORROSION("corrosion", "curse", "poison", 50, 28, 80, true),
    SONIC_WAVE("sonic_wave", "destruction", "sound", 50, 28, 60),
    RESONANCE("resonance", "control", "sound", 60, 32, 140, true),

    FLAME_SLASH("flame_slash", "destruction", "fire", 50, 55, 40),
    PYROBLAST("pyroblast", "destruction", "fire", 80, 120, 80),
    WATER_PRISON("water_prison", "control", "water", 60, 35, 80, true),
    WATER_PYTHON("water_python", "destruction", "water", 84, 95, 80),
    ICE_SLIDE("ice_slide", "movement", "ice", 24, 25, 50),
    ICE_COFFIN("ice_coffin", "control", "ice", 90, 110, 120, true),
    LIGHTNING_BLINK("lightning_blink", "movement", "lightning", 20, 28, 50),
    THUNDER_STRIKE("thunder_strike", "destruction", "lightning", 90, 120, 90, true),
    LIGHTNESS("lightness", "blessing", "wind", 30, 22, 60, true),
    GALE_SLASHES("gale_slashes", "destruction", "wind", 80, 105, 100),
    MUD_SWAMP("mud_swamp", "curse", "earth", 50, 26, 100, true),
    ROCK_ORBS("rock_orbs", "destruction", "earth", 120, 115, 100),
    VINE_SERVANTS("vine_servants", "summon", "nature", 70, 40, 80),
    LIFE_BLESSING("life_blessing", "blessing", "nature", 100, 130, 120, true),
    METAL_DASH("metal_dash", "movement", "metal", 24, 24, 50),
    SWORD_SUMMON("sword_summon", "summon", "metal", 90, 125, 100),
    GLIMMER("glimmer", "blessing", "light", 30, 20, 60, true),
    HOLY_BEAM("holy_beam", "destruction", "light", 80, 100, 80),
    POISON_VINE("poison_vine", "summon", "poison", 56, 28, 60),
    PLAGUE("plague", "curse", "poison", 90, 110, 140, true),
    MUSICAL_INSPIRATION("musical_inspiration", "blessing", "sound", 40, 25, 60, true),
    SHATTERING_SOUND("shattering_sound", "destruction", "sound", 80, 115, 100),
    MULTI_BARRIER_RAY("multi_barrier_ray", "destruction", "arcane", 40, 100, 160),
    FLAME_CAGE("flame_cage", "control", "fire", 50, 90, 360, true),
    BLAZING_SUN("blazing_sun", "destruction", "fire", 110, 150, 900),
    /** 水疗养术：最多标记三个目标，每个目标脚下涌出持续 12 秒的治疗泉水。 */
    SPRING_RETURN("spring_return", "healing", "water", 60, 85, 400, true),
    /** 水破坏术：前摇结束后持续喷射 36 格高压水柱，结束后才开始算 40 秒冷却。 */
    ABYSS_DRAGON_STRIKE("abyss_dragon_strike", "destruction", "water", 100, 190, 800),
    /** 冰召唤术：召唤一只寒冰傀儡，每 3 秒射一次冰锥，最多 10 次。 */
    ICE_GOLEM("ice_golem", "summon", "ice", 60, 110, 440),
    /** 冰控制术：前摇后叠高级法阵，释放时冻结整片区域。 */
    SUBZERO_DOMAIN("subzero_domain", "control", "ice", 180, 155, 900),
    /** 雷移动术：进入 12 秒伏特状态，极速移动并把触碰的敌人电开。 */
    VOLT_STATE("volt_state", "movement", "lightning", 50, 120, 600),
    /** 雷减益术：头顶展开多重法阵与大型雷电球，持续天雷轰炸后引发电磁爆炸。 */
    THUNDER_TEMPEST("thunder_tempest", "curse", "lightning", 300, 550, 2400),
    /** 风移动术：身上缠绕狂风，触碰到的敌人被击退七格并获得风袭。 */
    WIND_BURST_STEP("wind_burst_step", "movement", "wind", 40, 75, 300),
    /** 风伤害术：进入风狂持续生成风暴场叠层，再按施法键挥出风十字斩与风葬斩击。 */
    WIND_BURIAL_CORRIDOR("wind_burial_corridor", "destruction", "wind", 90, 170, 760),
    /** 土防护术：最多为三个目标套上能抵挡 100 点伤害的磐石外壳，护甲 +20、韧性 +12、免疫击退。 */
    BOULDER("boulder", "protection", "earth", 50, 85, 800, true),
    /** 土伤害术：升起最多 48 块巨石，五秒后沿准心抛出并砸碎沿途方块。 */
    MYRIAD_METEORS("myriad_meteors", "destruction", "earth", 40, 350, 180),
    /** 奥术削弱术：身前 30° 扇形喷出乱魔粒子，命中的生物奥术沉默 60 秒、增幅被随机削掉一半。 */
    CHAOS_MAGIC("chaos_magic", "curse", "arcane", 50, 100, 600),
    /** 奥术预言术：侦测所在区块的高浓度魔素事物，并高亮 30 秒。 */
    MANA_SENSE("mana_sense", "divination", "arcane", 100, 90, 240),
    /** 奥术混沌术：脚下放下混乱魔力锚点，16 秒内不定时脉冲，随机元素伤害并附着。 */
    MANA_TIDE("mana_tide", "chaos", "arcane", 200, 100, 600),
    /** 木伤害术：在身后立起三根十格长的带刺藤条，持续 45 秒，每根每 18 刻各自抽打一次敌人。 */
    THORN_IMPALE("thorn_impale", "destruction", "nature", 60, 90, 400),
    /** 木增益术：在身前立起巨大的生命之树，领域持续 90 秒，每 64 刻释放一次卍之波。 */
    ENDLESS_BANKAI("endless_bankai", "blessing", "nature", 60, 200, 840),
    /** 金防护术：给自己罩上金钟罩，吸收 250 点伤害并免疫破甲，持续 50 秒。 */
    GOLDEN_BELL("golden_bell", "protection", "metal", 50, 125, 400),
    /** 金伤害术：在高空劈开一道 64×24 的裂缝，任意攻击都会从裂缝里落下十六把飞剑。 */
    TEN_THOUSAND_SWORDS("ten_thousand_swords", "destruction", "metal", 100, 195, 1200),
    /** 光增益术：自身与周围六格内的友方获得光的庇护，受伤 -22% 并持续回复生命。 */
    LIGHT_SHELTER("light_shelter", "blessing", "light", 60, 90, 440),
    /** 光伤害术：读条十五秒凝聚光球，期间可 Alt+左键蓄力六层，爆发后附加光蚀。 */
    RADIANT_RIFT("radiant_rift", "destruction", "light", 300, 230, 800),
    /** 毒伤害术：向前冲刺两格后射出一根贯穿毒刺，命中的敌人中毒并挂上“猛毒”。 */
    VENOM_HEART("venom_heart", "destruction", "poison", 60, 110, 400),
    /** 毒削弱术：持续释放不断扩散的毒雾，雾中的敌人反复中毒并叠“鸩毒”，冷却从毒雾消散时开始。 */
    VENOM_NIGHT("venom_night", "curse", "poison", 110, 140, 1100),
    /** 音控制术：发出一圈次声波，半径八格内的生物受到音伤并被眩晕八秒。 */
    INFRASOUND("infrasound", "control", "sound", 56, 80, 360),
    /** 音伤害术：身边悬浮最多三个震荡鸣球，每二十刻放出一圈穿透方块的高频声波。 */
    SOUL_CHORD("soul_chord", "destruction", "sound", 140, 700, 800);

    private final String id;
    private final String school;
    private final String medium;
    private final int castTicks;
    private final double manaCost;
    private final int cooldownTicks;
    private final boolean requiresTarget;

    SpellDefinition(String id, String school, String medium, int castTicks, double manaCost, int cooldownTicks) {
        this(id, school, medium, castTicks, manaCost, cooldownTicks, false);
    }

    SpellDefinition(String id, String school, String medium, int castTicks, double manaCost,
                    int cooldownTicks, boolean requiresTarget) {
        this.id = id;
        this.school = school;
        this.medium = medium;
        this.castTicks = castTicks;
        this.manaCost = manaCost;
        this.cooldownTicks = cooldownTicks;
        this.requiresTarget = requiresTarget;
    }

    public String id() { return id; }
    public String school() { return school; }
    public String schoolFragmentId(String grade) { return grade + "_" + school + "_school_fragment"; }
    public String medium() { return medium; }
    public ElementType element() { return ElementType.fromMedium(medium); }
    public int castTicks() { return castTicks; }
    public double manaCost() { return manaCost; }
    public int cooldownTicks() { return cooldownTicks; }
    public boolean requiresTarget() { return requiresTarget; }
    public boolean isArcane() { return element() == null; }
    public boolean isNewSpell() { return ordinal() >= FLAME_SLASH.ordinal(); }
    public boolean isIntermediate() {
        return switch (this) {
            case PYROBLAST, WATER_PYTHON, ICE_COFFIN, THUNDER_STRIKE, GALE_SLASHES, ROCK_ORBS,
                    LIFE_BLESSING, SWORD_SUMMON, HOLY_BEAM, PLAGUE, SHATTERING_SOUND, FLAME_CAGE,
                    SPRING_RETURN, ICE_GOLEM, VOLT_STATE, WIND_BURST_STEP, BOULDER,
                    CHAOS_MAGIC, MANA_SENSE, MANA_TIDE, THORN_IMPALE, GOLDEN_BELL, LIGHT_SHELTER,
                    VENOM_HEART, INFRASOUND -> true;
            default -> false;
        };
    }
    /** 高级法术：多重结界射线、炽阳之怒、深渊龙击、零下领域、天雷滚滚、风葬回廊、万法千陨、不息卍解、万剑归一、曜光裂界、鸩羽千夜与断魂鸣弦。 */
    public boolean isAdvanced() {
        return this == MULTI_BARRIER_RAY || this == BLAZING_SUN || this == ABYSS_DRAGON_STRIKE
                || this == SUBZERO_DOMAIN || this == THUNDER_TEMPEST || this == WIND_BURIAL_CORRIDOR
                || this == MYRIAD_METEORS || this == ENDLESS_BANKAI || this == TEN_THOUSAND_SWORDS
                || this == RADIANT_RIFT || this == VENOM_NIGHT || this == SOUL_CHORD;
    }
    public String grade() { return isIntermediate() ? "medium" : isAdvanced() ? "advanced" : "lesser"; }
    /** 奥术没有元素媒介：中级及以上改用同品级奥术碎片，初级奥术仍不消耗碎片。 */
    public String elementFragmentId() {
        ElementType element = element();
        if (element != null) return element.fragmentId(grade());
        return isIntermediate() || isAdvanced() ? grade() + "_arcane_fragment" : null;
    }
    public double targetRange() { return isNewSpell() ? 16.0 : 12.0; }
    public boolean friendlyTarget() {
        return this == MOISTURE || this == LIGHTNESS || this == LIFE_BLESSING
                || this == GLIMMER || this == MUSICAL_INSPIRATION || this == SPRING_RETURN
                || this == BOULDER;
    }
    /** 一次施法可标记的目标数量；涌泉之报与磐石最多选择三个目标（含自己）。 */
    public int maxTargets() { return this == SPRING_RETURN || this == BOULDER ? 3 : 1; }
    /** 新法术复用同元素素材，不越界读取旧粒子图集。 */
    public SpellDefinition visualBase() {
        if (!isNewSpell()) return this;
        return switch (medium) {
            case "fire" -> FIREBALL;
            case "water" -> WATER_BOLT;
            case "ice" -> ICE_SPIKE;
            case "lightning" -> ELECTRIC_SHOCK;
            case "wind" -> WIND_BLADE;
            case "earth" -> ROCKFALL;
            case "nature" -> THORNS;
            case "metal" -> SHARP_EDGE;
            case "light" -> LIGHT_BOLT;
            case "poison" -> POISON_BOLT;
            case "arcane" -> ARCANE_RAY;
            default -> SONIC_WAVE;
        };
    }
    public String translationKey() { return "gui.dimensional_mana.spell." + id; }
    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/gui/spell/" + id + ".png");
    }

    public int adjustedCastTicks(net.minecraft.world.entity.LivingEntity caster) {
        double haste = Math.max(0.05, SpellAttributes.hasteMultiplier(caster));
        // 侦探护符：预言学派的前摇 -50%。
        double relic = RelicEffects.divinationCastMultiplier(caster, this);
        return Math.max(1, (int)Math.ceil(castTicks / haste * relic));
    }

    public static SpellDefinition byId(String id) {
        if (id == null || id.isBlank()) return null;
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(spell -> spell.id.equals(normalized)).findFirst().orElse(null);
    }
}
