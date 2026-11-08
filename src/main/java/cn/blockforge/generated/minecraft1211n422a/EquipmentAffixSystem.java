package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 《次元物语：武备》词条系统的运行时实现。
 *
 * <p>词条存放在 1.21.1 的 {@link DataComponents#CUSTOM_DATA} 中，因而不会污染原版装备
 * 的其它组件。掉落、Tooltip、铁砧合成和装备属性都集中在这里，后续增加定向洗练时只需扩展
 * {@link #AFFIXES} 和 {@link #merge}。</p>
 */
public final class EquipmentAffixSystem {
    private static final String ROOT = "DimensionalManaAffix";
    private static final String GRADE = "grade";
    private static final String SCHOOL = "school";
    private static final String MAIN = "main";
    private static final String SUBS = "subs";
    private static final String ID = "id";
    private static final String LEVEL = "level";
    private static final String VALUE = "value";
    private static final String START = "start";
    private static final String PLAYER_DROP_MARKER = "DimensionalManaPlayerDrop";
    /** 「元素附加」具体是哪一系元素（fire / water / … / arcane）。 */
    private static final String ELEMENT = "element";
    // ---- r342 评级修正：耐久上限、基础面板、专属特性 ----
    /** 物品的原始耐久上限，写进 CUSTOM_DATA 里，评级来回变也不会把基础值算错。 */
    private static final String BASE_MAX_DAMAGE = "DimensionalManaBaseMaxDamage";
    /** 评级专属特性已经掷过（不管中没中），避免每 20 刻重复抽。 */
    private static final String TRAIT_ROLLED = "grade_trait_rolled";
    /** 评级写进词条根节点的修正值，别的模组 / 调试命令能直接读到。 */
    private static final String GRADE_DURABILITY_FIELD = "grade_durability_pct";
    private static final String GRADE_PANEL_FIELD = "grade_panel_scale";
    /**
     * 评级修正前的基础数值：按「属性修正 id → 原始数值」记在 CUSTOM_DATA 里。
     *
     * <p>有这份账本，同一件装备无论洗练、重铸、升级多少次，都只在原版基础值上乘一次评级倍率，
     * 不会出现 1.05 的 N 次方。老存档里没有这份账本的装备会以当前数值当基础值补记。</p>
     */
    private static final String GRADE_PANEL_BASE = "grade_panel_base_amounts";
    /** 弓弩专用物品标签：原版弓、弩都在里面，模组弓只要加进这个标签也认。 */
    private static final net.minecraft.tags.TagKey<Item> BOW_LIKE_TAG = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "bow_like"));

    private static final ResourceLocation SPELL_POWER_MOD = id("affix_spell_power");
    private static final ResourceLocation SPELL_HASTE_MOD = id("affix_spell_haste");
    private static final ResourceLocation SPELL_CRIT_MOD = id("affix_spell_crit");
    private static final ResourceLocation SPELL_CRIT_DAMAGE_MOD = id("affix_spell_crit_damage");
    private static final ResourceLocation SPELL_PENETRATION_MOD = id("affix_spell_penetration");
    private static final ResourceLocation PHYSICAL_CRIT_MOD = id("affix_physical_crit");
    private static final ResourceLocation PHYSICAL_CRIT_DAMAGE_MOD = id("affix_physical_crit_damage");
    private static final ResourceLocation MANA_REDUCTION_MOD = id("affix_mana_reduction");
    private static final ResourceLocation ATTACK_DAMAGE_MOD = id("affix_attack_damage");
    private static final ResourceLocation ATTACK_DAMAGE_PERCENT_MOD = id("affix_attack_damage_percent");
    private static final ResourceLocation ATTACK_SPEED_MOD = id("affix_attack_speed");
    private static final ResourceLocation MAX_HEALTH_MOD = id("affix_max_health");
    private static final ResourceLocation MAX_HEALTH_PERCENT_MOD = id("affix_max_health_percent");
    // 词条属性（可被 Apothic Attributes 等外部面板检测）用的修正标识。
    private static final ResourceLocation AFFIX_MANA_PERCENT_MOD = id("affix_mana_max_percent");
    private static final ResourceLocation AFFIX_MANA_FLAT_MOD = id("affix_mana_max_flat");
    private static final ResourceLocation AFFIX_MANA_AFFINITY_MOD = id("affix_mana_affinity");
    private static final ResourceLocation AFFIX_MASTERY_MOD = id("affix_elemental_mastery");
    private static final ResourceLocation AFFIX_ELEMENT_DAMAGE_MOD = id("affix_elemental_damage");
    private static final ResourceLocation AFFIX_QI_PERCENT_MOD = id("affix_battle_qi_percent");
    private static final ResourceLocation AFFIX_QI_FLAT_MOD = id("affix_battle_qi_flat");
    // r332 物理词条扩充的属性修正标识。
    private static final ResourceLocation AFFIX_ARMOR_PEN_MOD = id("affix_armor_penetration");
    private static final ResourceLocation AFFIX_LIFESTEAL_MOD = id("affix_lifesteal");
    private static final ResourceLocation AFFIX_QI_REGEN_MOD = id("affix_battle_qi_regen");
    private static final ResourceLocation AFFIX_QI_SKILL_POWER_MOD = id("affix_battle_qi_skill_power");
    private static final ResourceLocation AFFIX_COMBO_MOD = id("affix_combo_damage");
    private static final ResourceLocation AFFIX_EXECUTE_MOD = id("affix_execute_damage");
    private static final ResourceLocation AFFIX_MIKIRI_MOD = id("affix_mikiri_damage");
    private static final ResourceLocation AFFIX_PARRY_WINDOW_MOD = id("affix_parry_window");
    private static final ResourceLocation AFFIX_ARROW_FLAT_MOD = id("affix_arrow_damage_flat");
    private static final ResourceLocation AFFIX_ARROW_PERCENT_MOD = id("affix_arrow_damage_percent");
    private static final ResourceLocation AFFIX_PARRY_REFLECT_MOD = id("affix_parry_reflect");
    private static final ResourceLocation AFFIX_TRIDENT_THRUST_MOD = id("affix_trident_thrust");
    private static final ResourceLocation ARMOR_VALUE_MOD = id("affix_armor_value");
    private static final ResourceLocation ARMOR_TOUGHNESS_MOD = id("affix_armor_toughness");

    private static ResourceLocation affixElementModifier(String element) {
        return id("affix_element_" + element);
    }

    /** 元素附加（点数额外元素伤害）的属性修正标识。 */
    private static ResourceLocation affixElementAdditionModifier(String element) {
        return id("affix_element_addition_" + element);
    }

    /**
     * 武备评级。除词条数量之外，评级还带三项修正（r342 起）：
     *
     * <table border="1">
     *   <caption>评级修正</caption>
     *   <tr><th>评级</th><th>耐久上限</th><th>基础面板</th><th>专属特性</th></tr>
     *   <tr><td>C</td><td>-5%</td><td>×0.90</td><td>—</td></tr>
     *   <tr><td>B</td><td>±0%</td><td>×1.00</td><td>—</td></tr>
     *   <tr><td>A</td><td>+5%</td><td>×1.05</td><td>附魔时 10% 多 1 条中等级</td></tr>
     *   <tr><td>S</td><td>+10%</td><td>×1.10</td><td>附魔时 25% 多 1～2 条中等级</td></tr>
     *   <tr><td>SS</td><td>+15%</td><td>×1.15</td><td>必定 1 条高等级；铁砧 / 加工台消耗 -10%</td></tr>
     *   <tr><td>SSS</td><td>+20%</td><td>×1.20</td><td>必定 2 条高等级；铁砧 / 加工台消耗 -20%</td></tr>
     * </table>
     *
     * <p>基础面板修正只乘「物品自己的基础数值」（武器基础攻击力 / 防具基础护甲），
     * 词条加成与魔精钢的独立减伤都不受影响；耐久修正按向下取整算。</p>
     */
    private enum Grade {
        C(ChatFormatting.GRAY, 1, 2, 0, 2, -5, 0.90D),
        B(ChatFormatting.WHITE, 1, 3, 0, 2, 0, 1.00D),
        A(ChatFormatting.GREEN, 1, 4, 1, 3, 5, 1.05D),
        S(ChatFormatting.BLUE, 1, 6, 1, 4, 10, 1.10D),
        SS(ChatFormatting.LIGHT_PURPLE, 1, 8, 2, 4, 15, 1.15D),
        SSS(ChatFormatting.GOLD, 1, 10, 2, 4, 20, 1.20D);

        final ChatFormatting color;
        final int mainMin;
        final int mainMax;
        final int subMin;
        final int subMax;
        /** 耐久上限修正，百分数；-5 就是 -5%。 */
        final int durabilityPct;
        /** 基础面板修正倍率：0.90 ～ 1.20。 */
        final double panelScale;

        Grade(ChatFormatting color, int mainMin, int mainMax, int subMin, int subMax,
              int durabilityPct, double panelScale) {
            this.color = color;
            this.mainMin = mainMin;
            this.mainMax = mainMax;
            this.subMin = subMin;
            this.subMax = subMax;
            this.durabilityPct = durabilityPct;
            this.panelScale = panelScale;
        }

        static Grade parse(String value) {
            try { return valueOf(value); } catch (IllegalArgumentException ignored) { return C; }
        }

        /** 按修正后的倍率算耐久上限：向下取整，最少留 1 点。 */
        int durabilityOf(int base) {
            return Math.max(1, (int)Math.floor(base * (1.0D + durabilityPct / 100.0D)));
        }

        /** 铁砧 / 武备加工台消耗的减免比例。 */
        double discount() {
            if (this == SS) return 0.10D;
            if (this == SSS) return 0.20D;
            return 0.0D;
        }

        /** 附魔时「额外多出几条」与抽中的概率；0 表示这个评级没有附魔特性。 */
        int extraEnchantMin() {
            return switch (this) {
                case A, SS -> 1;
                case S -> 1;
                case SSS -> 2;
                default -> 0;
            };
        }

        int extraEnchantMax() {
            return switch (this) {
                case A -> 1;
                case S -> 2;
                case SS -> 1;
                case SSS -> 2;
                default -> 0;
            };
        }

        /** 附魔特性触发概率；1.0 表示必定触发。 */
        double extraEnchantChance() {
            return switch (this) {
                case A -> 0.10D;
                case S -> 0.25D;
                case SS, SSS -> 1.0D;
                default -> 0.0D;
            };
        }

        /** 附魔特性的等级区间：A / S 是中等级 2～3，SS / SSS 是高等级 3～5。 */
        int extraEnchantLevelMin() {
            return this == A || this == S ? 2 : 3;
        }

        int extraEnchantLevelMax() {
            return this == A || this == S ? 3 : 5;
        }
    }

    private enum School { MAGIC, PHYSICAL }

    /** 词条允许出现在哪类装备上：剑专属、弓弩专属、盾牌专属、三叉戟专属、任意。 */
    private enum SlotKind { ANY, SWORD, BOW, SHIELD, TRIDENT }

    /**
     * 一条词条定义。
     *
     * <p>{@code group} 是「去重族」：同一件装备的主副词条不能落在同一族里，所以
     * 「物理伤害 +数值」和「物理伤害 +百分比」这种同属性两种写法会互相排斥
     * （以前只按 id 去重，于是出现过主词条是物理伤害百分比、副词条又给物理伤害固定值）。</p>
     *
     * <p>{@code slot} 是武器类型硬隔离：剑专属的见切 / 招架、弓弩专属的箭矢伤害，
     * 只会在对应 ItemTags 的装备上生成，斧、镐、胸甲等直接不参与这两组池子。</p>
     *
     * <p>{@code elemental} 标记「元素附加」：词条池里只占一个坑位，
     * 抽中之后才随机决定是哪一系元素，元素写在物品数据的 {@link #ELEMENT} 里。</p>
     */
    private record Affix(String id, String name, boolean percent, double mainStart, double mainStep,
                         double subMin, double subMax, double subStep, School school,
                         String group, SlotKind slot, boolean elemental) {
        /** 绝大多数词条：去重族就是自己的 id，任意装备可出，非元素附加。 */
        Affix(String id, String name, boolean percent, double mainStart, double mainStep,
              double subMin, double subMax, double subStep, School school) {
            this(id, name, percent, mainStart, mainStep, subMin, subMax, subStep, school, id, SlotKind.ANY, false);
        }

        double mainValue(int level) { return mainStart + mainStep * Math.max(0, level - 1); }
        double subValue(double start, int level) { return start + subStep * Math.max(0, level - 1); }
        /** 能不能当主词条（主词条一定有等级曲线）。 */
        boolean hasMain() { return mainStep > 0; }
        /** 能不能当副词条。 */
        boolean hasSub() { return subStep > 0; }
    }

    /** 只做副词条的词条：没有主词条曲线，只能从副词条池里出。 */
    private static Affix subOnly(String id, String name, boolean percent,
                                 double subMin, double subMax, double subStep, School school, String group) {
        return new Affix(id, name, percent, 0, 0, subMin, subMax, subStep, school, group, SlotKind.ANY, false);
    }

    /** 只做主语条的词条：副词条池里不会出现。 */
    private static Affix mainOnly(String id, String name, boolean percent, double start, double step,
                                  School school, SlotKind slot) {
        return new Affix(id, name, percent, start, step, 0, 0, 0, school, id, slot, false);
    }

    private static final List<Affix> AFFIXES = List.of(
            new Affix("mana_percent", "affix.dimensional_mana.mana_percent", true, 3.5, 3.5, .25, 1.25, 1.75, School.MAGIC, "mana", SlotKind.ANY, false),
            new Affix("mana_flat", "affix.dimensional_mana.mana_flat", false, 16, 16, 2, 8, 8, School.MAGIC, "mana", SlotKind.ANY, false),
            new Affix("spell_power_percent", "affix.dimensional_mana.spell_power_percent", true, 3.5, 3.5, .25, 1.25, 1.75, School.MAGIC, "spell_power", SlotKind.ANY, false),
            new Affix("spell_power_flat", "affix.dimensional_mana.spell_power_flat", false, 16, 16, 2, 8, 8, School.MAGIC, "spell_power", SlotKind.ANY, false),
            new Affix("spell_crit_chance", "affix.dimensional_mana.spell_crit_chance", true, 1.25, 1.25, .5, 3.25, 1, School.MAGIC),
            new Affix("spell_crit_damage", "affix.dimensional_mana.spell_crit_damage", true, 3.5, 3.5, 2, 8, 2, School.MAGIC),
            new Affix("elemental_mastery", "affix.dimensional_mana.elemental_mastery", false, 8, 8, 4, 8, 6, School.MAGIC),
            new Affix("element_damage", "affix.dimensional_mana.element_damage", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("mana_affinity", "affix.dimensional_mana.mana_affinity", false, 0, 0, .01, .15, .1, School.MAGIC),
            new Affix("mana_efficiency", "affix.dimensional_mana.mana_efficiency", false, 0, 0, 2, 8, 8, School.MAGIC),
            new Affix("mana_penetration", "affix.dimensional_mana.mana_penetration", false, 0, 0, 2, 8, 8, School.MAGIC),
            new Affix("battle_qi_percent", "affix.dimensional_mana.battle_qi_percent", true, 3.5, 3.5, .25, 1.25, 1.75, School.PHYSICAL, "battle_qi", SlotKind.ANY, false),
            new Affix("battle_qi_flat", "affix.dimensional_mana.battle_qi_flat", false, 16, 16, 2, 8, 8, School.PHYSICAL, "battle_qi", SlotKind.ANY, false),
            new Affix("physical_damage_flat", "affix.dimensional_mana.physical_damage_flat", false, 1, 1, .5, 1.5, .5, School.PHYSICAL, "physical_damage", SlotKind.ANY, false),
            new Affix("physical_damage_percent", "affix.dimensional_mana.physical_damage_percent", true, 3.5, 3.5, .25, 1.25, 1.75, School.PHYSICAL, "physical_damage", SlotKind.ANY, false),
            new Affix("physical_crit_chance", "affix.dimensional_mana.physical_crit_chance", true, 1.25, 1.25, .5, 3.25, 1, School.PHYSICAL),
            new Affix("physical_crit_damage", "affix.dimensional_mana.physical_crit_damage", true, 3.5, 3.5, 2, 8, 2, School.PHYSICAL),
            new Affix("attack_speed", "affix.dimensional_mana.attack_speed", true, 0, 0, 4, 8, 2, School.PHYSICAL),
            new Affix("max_health_flat", "affix.dimensional_mana.max_health_flat", false, 1.5, 1.5, 1, 1, 1, School.PHYSICAL, "max_health", SlotKind.ANY, false),
            new Affix("max_health_percent", "affix.dimensional_mana.max_health_percent", true, 3.5, 3.5, .25, 1.25, 1.75, School.PHYSICAL, "max_health", SlotKind.ANY, false),
            new Affix("element_fire", "affix.dimensional_mana.element_fire", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_water", "affix.dimensional_mana.element_water", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_ice", "affix.dimensional_mana.element_ice", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_lightning", "affix.dimensional_mana.element_lightning", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_wind", "affix.dimensional_mana.element_wind", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_earth", "affix.dimensional_mana.element_earth", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_nature", "affix.dimensional_mana.element_nature", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_poison", "affix.dimensional_mana.element_poison", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_metal", "affix.dimensional_mana.element_metal", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_light", "affix.dimensional_mana.element_light", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_sound", "affix.dimensional_mana.element_sound", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),
            new Affix("element_arcane", "affix.dimensional_mana.element_arcane", true, 3.5, 3.5, 0, 0, 0, School.MAGIC),

            // ---- r332 物理词条扩充：通用机制与增伤（主词条 1～12 级） ----
            // 破甲：初始 4，每级 +4，12 级 48。
            new Affix("armor_penetration", "affix.dimensional_mana.armor_penetration", false, 4, 4, 1, 4, 1, School.PHYSICAL),
            // 物理吸血：初始 1%，每级 +1%，12 级 12%（主词条专属）。
            mainOnly("lifesteal", "affix.dimensional_mana.lifesteal", true, 1, 1, School.PHYSICAL, SlotKind.ANY),
            // 斗气额外恢复：初始 0.15/s，每级 +0.15，12 级 1.8/s（主词条专属）。
            mainOnly("battle_qi_regen", "affix.dimensional_mana.battle_qi_regen", false, .15, .15, School.PHYSICAL, SlotKind.ANY),
            // 斗气技能强度：初始 1，每级 +1，12 级 12（每点 +2.5% 斗气技能伤害，主词条专属）。
            mainOnly("battle_qi_skill_power", "affix.dimensional_mana.battle_qi_skill_power", false, 1, 1, School.PHYSICAL, SlotKind.ANY),
            // 连击伤害：初始 2%，每级 +2%，12 级 24%。
            new Affix("combo_damage", "affix.dimensional_mana.combo_damage", true, 2, 2, 1, 4, 1, School.PHYSICAL),
            // 处决伤害：初始 3%，每级 +3%，12 级 36%。
            new Affix("execute_damage", "affix.dimensional_mana.execute_damage", true, 3, 3, 1, 4, 1, School.PHYSICAL),
            // 12 系元素附加：主 1～12，副 0.5～2 +0.5（最高 4），池子里只占一个坑位。
            new Affix("elemental_addition", "affix.dimensional_mana.elemental_addition", false, 1, 1, .5, 2, .5, School.PHYSICAL, "elemental_addition", SlotKind.ANY, true),

            // ---- 专属武器特化（武器类型硬隔离） ----
            // 见切伤害：仅剑类，初始 3%，每级 +3%，12 级 36%。
            mainOnly("mikiri_damage", "affix.dimensional_mana.mikiri_damage", true, 3, 3, School.PHYSICAL, SlotKind.SWORD),
            // 招架特化：仅剑类，初始 1，每级 +1，12 级 12（每点完美格挡窗口 +1 刻）。
            mainOnly("parry_window", "affix.dimensional_mana.parry_window", false, 1, 1, School.PHYSICAL, SlotKind.SWORD),
            // 箭矢伤害 +数值：仅弓弩，初始 1，每级 +1，12 级 12。
            mainOnly("arrow_damage_flat", "affix.dimensional_mana.arrow_damage_flat", false, 1, 1, School.PHYSICAL, SlotKind.BOW),
            // 箭矢伤害 +百分比：仅弓弩，初始 4%，每级 +4%，12 级 48%。
            mainOnly("arrow_damage_percent", "affix.dimensional_mana.arrow_damage_percent", true, 4, 4, School.PHYSICAL, SlotKind.BOW),
            // 弹反：仅盾牌，主词条初始 2%、每级 +2%、12 级 24%；副词条 0.5～1.5、每级 +1.5（最高 +4 = 7.5）。
            // 效果见 ShieldTridentEffects#onShieldBlock：成功格挡后把这一下被格挡伤害的百分比反弹给攻击者。
            new Affix("parry_reflect", "affix.dimensional_mana.parry_reflect", true, 2, 2, .5, 1.5, 1.5,
                    School.PHYSICAL, "parry_reflect", SlotKind.SHIELD, false),
            // 戳刺：仅三叉戟，主词条初始 4%、每级 +4%、12 级 48%；副词条 2～4、每级 +2（最高 +4 = 12）。
            // 只在投掷命中与激流冲刺撞击时结算，近战平A 不生效（见 EquipmentAffixSystem#onRangedIncomingDamage
            // 与 ShieldTridentEffects#onIncomingDamage）。
            new Affix("trident_thrust", "affix.dimensional_mana.trident_thrust", true, 4, 4, 2, 4, 2,
                    School.PHYSICAL, "trident_thrust", SlotKind.TRIDENT, false),

            // ---- r332 物理词条扩充：新增副词条（初始无等级，最多 +4） ----
            // 护甲值：初始 0.5～2，每级 +1，最高 6。
            subOnly("armor_value", "affix.dimensional_mana.armor_value", false, .5, 2, 1, School.PHYSICAL, "armor_value"),
            // 盔甲韧性：初始 0.5～1，每级 +0.5，最高 3。
            subOnly("armor_toughness", "affix.dimensional_mana.armor_toughness", false, .5, 1, .5, School.PHYSICAL, "armor_toughness")
    );

    /** 宝箱等非生物掉落通过 ItemEntity 进入世界时补上词条；已读档物品不重复处理。 */
    public static void onItemEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk() || !(event.getEntity() instanceof ItemEntity itemEntity)) return;
        ItemStack stack = itemEntity.getItem();
        if (!isEquipment(stack) || hasAffix(stack) || itemEntity.getOwner() != null
                || stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(PLAYER_DROP_MARKER)) return;
        Grade grade = chestGrade(event.getLevel().dimension(), event.getLevel().random);
        apply(stack, grade, schoolFor(stack, event.getLevel().random), event.getLevel().random, null);
        itemEntity.setItem(stack);
    }

    /**
     * 原版与模组宝箱的装备池：权重只决定是否出现哪类胚子，评级在开箱时按环境权重抽取。
     *
     * <p>判定走 {@link StructureChestLoot}，所以别的模组新增结构里的通用宝箱（{@code othermod:chests/xxx}）
     * 也会出带词条的装备胚子；开箱补词条那一步在 {@link #onContainerOpen}，与表是谁加的无关。</p>
     */
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!StructureChestLoot.isStructureChest(event.getName())) return;
        String path = event.getName().getPath();
        boolean end = path.contains("end_city") || path.contains("ancient_city");
        boolean nether = path.contains("nether") || path.contains("bastion") || path.contains("fortress");
        LootPool.Builder pool = LootPool.lootPool().name("dimensional_mana_weighted_equipment")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(end ? 0.18F : nether ? 0.12F : 0.08F));
        if (end) {
            pool.add(LootItem.lootTableItem(Items.DIAMOND_SWORD).setWeight(18));
            pool.add(LootItem.lootTableItem(Items.DIAMOND_CHESTPLATE).setWeight(16));
            pool.add(LootItem.lootTableItem(Items.NETHERITE_SWORD).setWeight(8));
            pool.add(LootItem.lootTableItem(Items.NETHERITE_CHESTPLATE).setWeight(8));
        } else if (nether) {
            pool.add(LootItem.lootTableItem(Items.IRON_SWORD).setWeight(16));
            pool.add(LootItem.lootTableItem(Items.GOLDEN_SWORD).setWeight(16));
            pool.add(LootItem.lootTableItem(Items.DIAMOND_SWORD).setWeight(8));
            pool.add(LootItem.lootTableItem(Items.GOLDEN_CHESTPLATE).setWeight(18));
            pool.add(LootItem.lootTableItem(Items.DIAMOND_CHESTPLATE).setWeight(8));
        } else {
            pool.add(LootItem.lootTableItem(Items.IRON_SWORD).setWeight(20));
            pool.add(LootItem.lootTableItem(Items.BOW).setWeight(12));
            pool.add(LootItem.lootTableItem(Items.LEATHER_CHESTPLATE).setWeight(18));
            pool.add(LootItem.lootTableItem(Items.IRON_CHESTPLATE).setWeight(15));
            pool.add(LootItem.lootTableItem(Items.GOLDEN_CHESTPLATE).setWeight(5));
        }
        StructureChestLoot.addPool(event.getTable(), pool.build());
    }

    /** 宝箱开出来的物品还在容器里，不会经过 ItemEntity；打开箱子时按环境权重补词条。 */
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof net.minecraft.world.inventory.ChestMenu chest)) return;
        net.minecraft.world.Container container = chest.getContainer();
        net.minecraft.util.RandomSource random = player.serverLevel().random;
        Grade grade = chestGrade(player.level().dimension(), random);
        boolean granted = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!isEquipment(stack) || hasAffix(stack)) continue;
            apply(stack, grade, schoolFor(stack, random), random, null);
            container.setItem(slot, stack);
            granted = true;
        }
        // 进度「第一滴血」：箱子里的装备真的被赋上评级词条了才算。
        if (granted) MagicProgression.awardAdvancement(player, "affix_chest");
    }

    private static Grade chestGrade(net.minecraft.resources.ResourceKey<Level> dimension,
                                    net.minecraft.util.RandomSource random) {
        if (dimension == Level.END) return weighted(random, Grade.A, Grade.SSS, 0.60);
        if (dimension == Level.NETHER) return weighted(random, Grade.C, Grade.A, 0.40);
        return weighted(random, Grade.C, Grade.B, 0.50);
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity instanceof Player) {
            for (ItemEntity itemEntity : event.getDrops()) markPlayerDrop(itemEntity.getItem());
            return;
        }
        // 先判「这是不是会掉保底装备的 Boss」，再决定要不要去做 32 格玩家查询：
        // 以前是每只生物死亡都查一次，刷怪塔里一秒几十只的时候这笔开销纯属白花。
        boolean boss = isBossEntity(entity);
        if (!event.getDrops().isEmpty()) {
            Grade grade = gradeFor(entity);
            School forced = forcedSchool(entity);
            for (ItemEntity itemEntity : event.getDrops()) {
                ItemStack stack = itemEntity.getItem();
                if (!isEquipment(stack) || hasAffix(stack)) continue;
                apply(stack, grade, forced == null ? schoolFor(stack, entity.getRandom()) : forced, entity.getRandom(), entity);
                itemEntity.setItem(stack);
            }
        }
        if (boss && killedByPlayer(event)) addBossEquipmentDrop(event);
    }

    /** 会有「保底高品质装备」的四种 Boss；普通生物一律直接跳过那条判定。 */
    private static boolean isBossEntity(LivingEntity entity) {
        return entity instanceof CorruptedWither || entity instanceof WanderingSwordmaster
                || entity instanceof EnderDragon || entity instanceof WitherBoss;
    }

    /**
     * 这一下算不算「玩家击杀」。
     *
     * <p>原版的 {@code recentlyHit} 只在伤害源直接挂着玩家时才会置位（{@code lastHurtByPlayerTime}），
     * 而本模组的 Boss 基本都是被法术、法阵和射线打死的 —— 这些伤害源没有实体、或者只有一层
     * 投射物实体，于是 {@code recentlyHit} 一路都是 false，Boss 的保底装备就一直掉不出来
     * （材料走战利品表照掉，所以看起来像「只不掉装备」）。这里补三条兜底：</p>
     * <ul>
     *   <li>原版的 {@code recentlyHit}；</li>
     *   <li>伤害源属于玩家（直接实体或归属实体是玩家）；</li>
     *   <li>死亡位置 32 格内有生存模式玩家（法术打死时伤害源往往是空的）。</li>
     * </ul>
     */
    private static boolean killedByPlayer(LivingDropsEvent event) {
        if (event.isRecentlyHit()) return true;
        net.minecraft.world.damagesource.DamageSource source = event.getSource();
        if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player) return true;
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return false;
        return !level.getEntitiesOfClass(ServerPlayer.class, entity.getBoundingBox().inflate(32.0D),
                player -> !player.isCreative() && !player.isSpectator()).isEmpty();
    }

    /** Boss 战利品表只负责材料；这里保证规范中的一件高品质装备真正进入掉落集合。 */
    private static void addBossEquipmentDrop(LivingDropsEvent event) {
        LivingEntity boss = event.getEntity();
        boolean corrupted = boss.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("corruptedwither");
        boolean swordmaster = boss.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("swordmaster");
        boolean dragon = boss instanceof EnderDragon;
        boolean wither = boss instanceof WitherBoss && !corrupted;
        if (!corrupted && !dragon && !wither && !swordmaster) return;
        net.minecraft.util.RandomSource random = boss.getRandom();
        Item item;
        Grade grade;
        if (swordmaster) {
            // 剑圣是物理系的单挑 BOSS：保底一把带词条的高级剑，品质 A～SS。
            grade = weighted(random, Grade.A, Grade.SS, 0.60);
            item = random.nextBoolean() ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD;
        } else if (corrupted) {
            grade = Grade.values()[3 + random.nextInt(3)];
            double roll = random.nextDouble();
            if (roll < 0.40) {
                item = random.nextBoolean() ? Items.NETHERITE_SWORD : GeneratedMod.MOD_ITEMS.get(MojiangSteelEquipment.SWORD).get();
            } else if (roll < 0.70) {
                String[] staffs = {"advanced_general_staff", "supreme_general_staff", "special_general_staff"};
                item = GeneratedMod.MOD_ITEMS.get(staffs[random.nextInt(staffs.length)]).get();
            } else {
                item = random.nextBoolean() ? Items.NETHERITE_CHESTPLATE : GeneratedMod.MOD_ITEMS.get(MojiangSteelEquipment.CHESTPLATE).get();
            }
        } else if (dragon) {
            grade = Grade.values()[3 + random.nextInt(3)];
            if (random.nextDouble() < 0.30) {
                item = random.nextBoolean() ? Items.NETHERITE_SWORD : Items.BOW;
            } else {
                item = random.nextBoolean() ? Items.NETHERITE_CHESTPLATE : Items.NETHERITE_LEGGINGS;
            }
        } else {
            grade = weighted(random, Grade.B, Grade.S, 0.55);
            double roll = random.nextDouble();
            if (roll < 0.30) {
                item = Items.BOW;
            } else if (roll < 0.50) {
                item = Items.DIAMOND_SWORD;
            } else {
                item = Items.DIAMOND_CHESTPLATE;
            }
        }
        ItemStack stack = new ItemStack(item);
        // 流派按装备类别的概率表现掷（剑 80/20、甲 60/40、工具全物理、法杖全魔法、
        // 魔精钢 50/50），所以 Boss 剑也有小概率出「魔剑士」极品；只有剑圣保留
        // 物理系人设，保底武器固定物理流派。
        apply(stack, grade, swordmaster ? School.PHYSICAL : schoolFor(stack, random), random, boss);
        event.getDrops().add(new ItemEntity(boss.level(), boss.getX(), boss.getY() + 0.8D, boss.getZ(), stack));
    }

    private static void markPlayerDrop(ItemStack stack) {
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = old == null ? new CompoundTag() : old.copyTag();
        data.putBoolean(PLAYER_DROP_MARKER, true);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
    }

    /** 自然掉落以外由其它系统塞入掉落集合时也能调用。 */
    public static void applyDrop(ItemStack stack, LivingEntity source) {
        if (stack.isEmpty() || hasAffix(stack) || !isEquipment(stack)) return;
        apply(stack, gradeFor(source), forcedSchool(source) == null ? schoolFor(stack, source.getRandom()) : forcedSchool(source),
                source.getRandom(), source);
    }

    private static void apply(ItemStack stack, Grade grade, School school, net.minecraft.util.RandomSource random,
                              LivingEntity source) {
        CompoundTag root = buildRoot(stack, grade, school, random, source != null && isBoss(source));
        writeRoot(stack, root, grade);
    }

    /**
     * 按评级与流派随机出一整套词条。
     *
     * <p>掉落生成（{@link #apply}）和超导魔晶洗练（{@link #rerollAffixes}）共用这一份规则，
     * 保证「洗出来的装备」和「打出来的装备」是同一套数值分布。</p>
     *
     * <p>三条硬规则都在这一个方法里落实：</p>
     * <ol>
     *   <li><b>武器类型硬隔离</b>：先读物品的 ItemTags，剑类才把见切 / 招架放进池子，
     *       弓弩才放箭矢伤害，斧、镐、胸甲之类根本不参与这两组池子；</li>
     *   <li><b>元素附加只占一个坑位</b>：12 系共用一个 {@code elemental_addition} 词条，
     *       抽中之后才在 12 系里随机决定是哪一系（{@link #pickElementalType}），不稀释其它词条的出货率；</li>
     *   <li><b>主副去重</b>：按去重族（{@link Affix#group()}）排除，同属性的数值型和百分比型
     *       不会再同时出现（例如主词条物理伤害%、副词条物理伤害固定值）。</li>
     * </ol>
     */
    private static CompoundTag buildRoot(ItemStack stack, Grade grade, School school,
                                        net.minecraft.util.RandomSource random, boolean bossDrop) {
        CompoundTag root = new CompoundTag();
        root.putString(GRADE, grade.name());
        root.putString(SCHOOL, school.name().toLowerCase(Locale.ROOT));
        if (bossDrop) root.putBoolean("boss_drop", true);

        Set<String> usedGroups = new java.util.HashSet<>();
        List<Affix> pool = mainPool(stack, school);
        Affix main = pool.get(random.nextInt(pool.size()));
        int mainLevel = random.nextInt(Math.max(1, grade.mainMax - grade.mainMin + 1)) + grade.mainMin;
        CompoundTag mainTag = affixTag(main, mainLevel, main.mainValue(mainLevel));
        String element = main.elemental() ? pickElementalType(random, usedGroups) : null;
        if (element != null) mainTag.putString(ELEMENT, element);
        collectGroups(mainTag, usedGroups);
        root.put(MAIN, mainTag);

        ListTag subs = new ListTag();
        int count = grade.subMin + (grade.subMax > grade.subMin ? random.nextInt(grade.subMax - grade.subMin + 1) : 0);
        for (int i = 0; i < count; i++) {
            CompoundTag sub = rollSubTag(stack, school, usedGroups, random);
            if (sub == null) break;
            collectGroups(sub, usedGroups);
            subs.add(sub);
        }
        root.put(SUBS, subs);
        return root;
    }

    /** 主语条池：流派一致、有主词条曲线、并且这件装备允许出（剑 / 弓弩专属词条在这里被挡掉）。 */
    private static List<Affix> mainPool(ItemStack stack, School school) {
        List<Affix> pool = AFFIXES.stream()
                .filter(a -> a.school() == school && a.hasMain() && allowedOn(a, stack))
                .toList();
        // 极端情况下（比如某个只有弓弩专属词条的流派 + 一把斧）池子不能为空：退回该流派的全部主语条。
        return pool.isEmpty()
                ? AFFIXES.stream().filter(a -> a.school() == school && a.hasMain()).toList()
                : pool;
    }

    /**
     * 抽一条副词条；池子空了返回 {@code null}。
     *
     * <p>元素附加在这里同样只算一个坑位：{@link #pickElementalType} 从 12 系里挑一个
     * 还没被同元素词条占用的系，挑不出来就整条跳过。</p>
     */
    private static CompoundTag rollSubTag(ItemStack stack, School school, Set<String> usedGroups,
                                          net.minecraft.util.RandomSource random) {
        List<Affix> available = new ArrayList<>();
        for (Affix affix : AFFIXES) {
            if (affix.school() != school || !affix.hasSub() || !allowedOn(affix, stack)) continue;
            if (usedGroups.contains(affix.group())) continue;
            available.add(affix);
        }
        // 打乱后逐个尝试：元素附加可能因为 12 系都被占满而抽不出来，这时候继续试下一条。
        while (!available.isEmpty()) {
            Affix sub = available.remove(random.nextInt(available.size()));
            double initial = sub.subMin() + random.nextDouble() * (sub.subMax() - sub.subMin());
            CompoundTag tag = affixTag(sub, 1, initial);
            if (sub.elemental()) {
                String element = pickElementalType(random, usedGroups);
                if (element == null) continue;
                tag.putString(ELEMENT, element);
            }
            return tag;
        }
        return null;
    }

    /**
     * 「元素附加」抽元素：从 12 系里随机挑一系，跳过已经被同元素词条（如火元素伤害%）占用的系。
     * 12 系全被占用时返回 {@code null}，这条词条本次不出现。
     */
    private static String pickElementalType(net.minecraft.util.RandomSource random, Set<String> usedGroups) {
        List<String> candidates = new ArrayList<>();
        for (String element : ELEMENT_IDS) {
            if (!usedGroups.contains("element_" + element)) candidates.add(element);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 把一条词条占用的去重族记进集合。
     *
     * <p>普通词条记它自己的族（同属性的数值型与百分比型共用一个族）；
     * 「元素附加」同时记两个：{@code elemental_addition} 保证整套武备只出这一条附加，
     * {@code element_<系>} 保证不会和同系的百分比元素词条撞车。</p>
     */
    private static void collectGroups(CompoundTag tag, Set<String> used) {
        Affix affix = findAffix(tag.getString(ID));
        if (affix == null) return;
        if (affix.elemental()) {
            used.add("elemental_addition");
            used.add("element_" + tag.getString(ELEMENT));
        } else {
            used.add(affix.group());
        }
    }

    /** 武器类型硬隔离：剑类才出剑专属词条，弓弩才出箭矢专属词条，盾牌 / 三叉戟各有自己的池子。 */
    private static boolean allowedOn(Affix affix, ItemStack stack) {
        return switch (affix.slot()) {
            case ANY -> true;
            case SWORD -> isSwordLike(stack);
            case BOW -> isBowLike(stack);
            case SHIELD -> isShieldLike(stack);
            case TRIDENT -> isTridentLike(stack);
        };
    }

    /** 剑类判定先读 {@code minecraft:swords} 标签，再兜一层类型判断（模组剑不会再手动加标签）。 */
    public static boolean isSwordLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem;
    }

    /** 盾牌判定：原版盾牌、模组小圆盾都继承 {@link ShieldItem}。 */
    public static boolean isShieldLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof ShieldItem;
    }

    /** 三叉戟判定：原版三叉戟直接继承 {@code Item}，所以要单独认一次类型。 */
    public static boolean isTridentLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof TridentItem;
    }

    /**
     * 弓弩判定：先读本模组的 {@code dimensional_mana:bow_like} 标签（数据包随 jar 一起发），
     * 再兜一层类型判断（原版弓、弩，以及模组里继承它们的东西）。
     */
    public static boolean isBowLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(BOW_LIKE_TAG) || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    /** 把词条根节点写进物品：只替换自己这一份自定义数据，别把其它模组的 NBT 一起冲掉。 */
    private static ItemStack writeRoot(ItemStack stack, CompoundTag root, Grade grade) {
        // 修正值随词条一起写进根节点：工具提示、调试命令、别的模组都能读到这件武备的评级加成。
        root.putInt(GRADE_DURABILITY_FIELD, grade.durabilityPct);
        root.putDouble(GRADE_PANEL_FIELD, grade.panelScale);
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = old == null ? new CompoundTag() : old.copyTag();
        data.put(ROOT, root);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
        stack.set(DataComponents.CUSTOM_NAME, prefixedName(stack, grade));
        if (grade == Grade.SSS) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        else stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        applyGradeDurability(stack, grade);
        applyGradePanel(stack, grade);
        return stack;
    }

    // ------------------------------------------------------------------
    // r342 评级修正：耐久上限、基础面板、专属特性
    // ------------------------------------------------------------------

    /**
     * 把评级修正里的「耐久上限」写进物品。
     *
     * <p>原版耐久上限是物品组件 {@link DataComponents#MAX_DAMAGE}，这里按物品的原始上限
     * 乘上评级系数，再向下取整。原始上限单独存在 {@link #BASE_MAX_DAMAGE} 里，
     * 所以同一件装备无论洗练、升级多少次，耐久都只按基础值算一遍，不会 1.05 的 N 次方。</p>
     */
    private static void applyGradeDurability(ItemStack stack, Grade grade) {
        if (!stack.isDamageableItem()) return;
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = old == null ? new CompoundTag() : old.copyTag();
        boolean changed = false;
        int base = data.getInt(BASE_MAX_DAMAGE);
        if (base <= 0) {
            // 第一次遇到这件装备：现在的上限就是原版基础值。
            base = stack.getMaxDamage();
            if (base <= 0) return;
            data.putInt(BASE_MAX_DAMAGE, base);
            changed = true;
        }
        int target = grade.durabilityOf(base);
        if (stack.getOrDefault(DataComponents.MAX_DAMAGE, 0) != target) {
            stack.set(DataComponents.MAX_DAMAGE, target);
            // 耐久上限变低了就把已损耐久压回上限内，别出现 “-1/-1” 的怪状态。
            int damage = stack.getDamageValue();
            if (damage > target - 1) stack.setDamageValue(Math.max(0, target - 1));
            changed = true;
        }
        // 没有变化就不写组件，避免每秒给每件装备刷一次数据。
        if (changed) CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
    }

    /** 词条被剥掉（砂轮磨皮）时把耐久上限还原成原版数值。 */
    private static void restoreBaseDurability(ItemStack stack) {
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        if (old == null) return;
        CompoundTag data = old.copyTag();
        int base = data.getInt(BASE_MAX_DAMAGE);
        if (base <= 0) return;
        data.remove(BASE_MAX_DAMAGE);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
        if (stack.isDamageableItem()) {
            stack.set(DataComponents.MAX_DAMAGE, base);
            int damage = stack.getDamageValue();
            if (damage > base - 1) stack.setDamageValue(Math.max(0, base - 1));
        }
    }

    /** 这件武备的评级；没有词条返回 {@code null}。 */
    public static String gradeName(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        return root == null ? null : root.getString(GRADE);
    }

    /** 只把词条根节点写回物品，不动名字 / 耐久这些已经写好的东西。 */
    private static void writeRootTag(ItemStack stack, CompoundTag root) {
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = old == null ? new CompoundTag() : old.copyTag();
        data.put(ROOT, root);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
    }

    /**
     * 评级专属附魔特性（A / S / SS / SSS）：这件武备**第一次**被附魔时结算一次。
     *
     * <p>原版没有「附魔台附魔完成」的事件，所以这里按「物品身上已经有附魔」来判断：
     * 每秒扫一次身上和手上的装备，第一次发现有附魔就按评级的概率掷一次，
     * 结果写进 {@link #TRAIT_ROLLED}，不管中没中都不再重掷（不会因为反复穿脱而无限刷附魔）。</p>
     */
    private static void applyGradeEnchantTrait(ItemStack stack, Grade grade, Player player) {
        if (grade.extraEnchantMax() <= 0) return;
        CompoundTag root = affixRoot(stack);
        if (root == null || root.getBoolean(TRAIT_ROLLED)) return;
        if (!EnchantmentHelper.hasAnyEnchantments(stack)) return;
        net.minecraft.util.RandomSource random = player.getRandom();
        boolean hit = random.nextDouble() < grade.extraEnchantChance();
        root = root.copy();
        root.putBoolean(TRAIT_ROLLED, true);
        writeRootTag(stack, root);
        if (!hit) return;
        int min = grade.extraEnchantMin();
        int max = grade.extraEnchantMax();
        int count = max <= min ? min : min + random.nextInt(max - min + 1);
        List<Holder<Enchantment>> pool = gradeEnchantPool(stack, player);
        if (pool.isEmpty()) return;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
                stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        List<Holder<Enchantment>> present = new ArrayList<>(mutable.keySet());
        int added = 0;
        while (added < count && !pool.isEmpty()) {
            Holder<Enchantment> holder = pool.remove(random.nextInt(pool.size()));
            // 和已有的附魔互斥（锋利 / 亡灵杀手这类）就跳过，不造出原版不可能出现的组合。
            if (!compatibleWithAll(holder, present)) continue;
            int level = grade.extraEnchantLevelMin()
                    + random.nextInt(grade.extraEnchantLevelMax() - grade.extraEnchantLevelMin() + 1);
            level = Math.max(1, Math.min(level, holder.value().getMaxLevel()));
            // 已经有更高等级的附魔时保留原来的，不降级。
            if (mutable.getLevel(holder) >= level) continue;
            mutable.set(holder, level);
            present.add(holder);
            added++;
        }
        if (added > 0) stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    /** 新附魔和身上已有的每一条都不互斥才算能加。 */
    private static boolean compatibleWithAll(Holder<Enchantment> candidate, List<Holder<Enchantment>> present) {
        for (Holder<Enchantment> existing : present) {
            if (!Enchantment.areCompatible(candidate, existing)) return false;
        }
        return true;
    }

    /** 这件装备能附上、且不是诅咒的附魔池。 */
    private static List<Holder<Enchantment>> gradeEnchantPool(ItemStack stack, Player player) {
        List<Holder<Enchantment>> pool = new ArrayList<>();
        player.level().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .holders()
                .filter(holder -> !holder.is(net.minecraft.tags.EnchantmentTags.CURSE))
                .filter(holder -> holder.value().canEnchant(stack))
                .forEach(pool::add);
        return pool;
    }

    /**
     * 评级「基础面板修正」：把物品自己注册的基础攻击力 / 基础护甲按评级倍率写回物品自己的
     * {@link DataComponents#ATTRIBUTE_MODIFIERS}。
     *
     * <p>以前这份加成挂在玩家属性上，伤害确实变了，但物品 Tooltip 上的攻击伤害 / 护甲值还是
     * 白板数值，玩家根本看不出 C 和 SSS 差在哪。写进物品自己的属性组件之后：手持时 Tooltip
     * 显示的就是修正后的攻击伤害，穿上时显示的就是修正后的护甲值，掉在地上、放进箱子、
     * 被别的属性检查模组读到的也都是修正后的数值，而且拿在手上 / 穿在身上就真的按它结算。</p>
     *
     * <p>只乘 {@link AttributeModifier.Operation#ADD_VALUE} 的攻击伤害与护甲值——也就是物品的
     * 基础数值；词条加成是另外挂在玩家身上的独立来源，不在这里，不会被评级再乘一次。</p>
     */
    private static void applyGradePanel(ItemStack stack, Grade grade) {
        ItemAttributeModifiers current = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (current.modifiers().isEmpty()) return;
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = old == null ? new CompoundTag() : old.copyTag();
        CompoundTag stored = data.getCompound(GRADE_PANEL_BASE);
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(current.modifiers().size());
        CompoundTag next = new CompoundTag();
        boolean changed = false;
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            AttributeModifier modifier = entry.modifier();
            boolean scale = modifier.operation() == AttributeModifier.Operation.ADD_VALUE
                    && (entry.attribute().is(Attributes.ATTACK_DAMAGE) || entry.attribute().is(Attributes.ARMOR));
            if (!scale) {
                entries.add(entry);
                continue;
            }
            String key = modifier.id().toString();
            // 第一次遇到这件装备时，它现在的数值就是原版基础值。
            double base = stored.contains(key) ? stored.getDouble(key) : modifier.amount();
            next.putDouble(key, base);
            double amount = round2(base * grade.panelScale);
            if (Math.abs(amount - modifier.amount()) > 1.0E-6D) {
                changed = true;
                entries.add(new ItemAttributeModifiers.Entry(entry.attribute(),
                        new AttributeModifier(modifier.id(), amount, modifier.operation()), entry.slot()));
            } else {
                entries.add(entry);
            }
        }
        // 没有变化就不写组件，避免每秒给每件装备刷一次数据。
        if (!changed && next.equals(stored)) return;
        data.put(GRADE_PANEL_BASE, next);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
        if (changed) {
            // 是否在 Tooltip 里显示属性行沿用物品原本的设置，不能因为评级被强行打开。
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    new ItemAttributeModifiers(entries, current.showInTooltip()));
        }
    }

    /** 词条被剥掉（砂轮磨皮）时把基础攻击力 / 护甲值还原成原版数值。 */
    private static void restoreBasePanel(ItemStack stack) {
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        if (old == null) return;
        CompoundTag data = old.copyTag();
        if (!data.contains(GRADE_PANEL_BASE)) return;
        CompoundTag stored = data.getCompound(GRADE_PANEL_BASE);
        data.remove(GRADE_PANEL_BASE);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
        ItemAttributeModifiers current = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(current.modifiers().size());
        boolean changed = false;
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            AttributeModifier modifier = entry.modifier();
            String key = modifier.id().toString();
            if (stored.contains(key) && Math.abs(stored.getDouble(key) - modifier.amount()) > 1.0E-6D) {
                entries.add(new ItemAttributeModifiers.Entry(entry.attribute(),
                        new AttributeModifier(modifier.id(), stored.getDouble(key), modifier.operation()),
                        entry.slot()));
                changed = true;
            } else {
                entries.add(entry);
            }
        }
        if (changed) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    new ItemAttributeModifiers(entries, current.showInTooltip()));
        }
    }

    /** 一件装备某条属性的「评级前基础值」：优先读写入时存下的原值，老装备就按当前值算。 */
    private static double gradeBaseAmount(ItemStack stack, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag stored = data == null ? new CompoundTag()
                : data.copyTag().getCompound(GRADE_PANEL_BASE);
        double total = 0.0D;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) continue;
            if (!entry.attribute().is(attribute)) continue;
            String key = entry.modifier().id().toString();
            total += stored.contains(key) ? stored.getDouble(key) : entry.modifier().amount();
        }
        return total;
    }

    /** 保留两位小数，避免 8 × 0.9 这种乘法把 7.2 变成 7.199999999999999。 */
    private static double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static CompoundTag affixTag(Affix affix, int level, double value) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID, affix.id());
        tag.putInt(LEVEL, level);
        tag.putDouble(VALUE, value);
        tag.putDouble(START, value);
        return tag;
    }

    /**
     * 装备名 = 评级前缀 + 物品名。两者都用可翻译组件，客户端按自己的语言显示，
     * 所以存档里的装备换语言后名字也会跟着变，不再被烙成中文。
     */
    private static Component prefixedName(ItemStack stack, Grade grade) {
        String prefixKey = "item.dimensional_mana.affix.prefix." + grade.name().toLowerCase(Locale.ROOT);
        return Component.translatable(prefixKey).withStyle(grade.color)
                .append(Component.literal(" ").withStyle(grade.color))
                .append(Component.translatable(stack.getItem().getDescriptionId()).withStyle(grade.color));
    }

    /**
     * 旧版本把评级前缀直接写死成中文字面量存进了物品名，这类老装备改语言也不会变。
     * 只在显示 Tooltip 时识别并换成可翻译版本，不动存档里的数据，避免背包扫描的开销。
     */
    private static final List<String> LEGACY_CN_PREFIXES =
            List.of("[破损的]", "[普通的]", "[精良的]", "[稀有的]", "[史诗的]", "[传说的]");

    private static boolean hasLegacyChinesePrefix(Component name) {
        if (!(name.getContents() instanceof PlainTextContents plain)) return false;
        String text = plain.text();
        for (String prefix : LEGACY_CN_PREFIXES) {
            if (text.startsWith(prefix)) return true;
        }
        return false;
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        CompoundTag root = affixRoot(stack);
        if (root == null) return;
        Grade grade = Grade.parse(root.getString(GRADE));
        Component storedName = stack.get(DataComponents.CUSTOM_NAME);
        if (storedName != null && !event.getToolTip().isEmpty() && hasLegacyChinesePrefix(storedName)) {
            event.getToolTip().set(0, prefixedName(stack, grade));
        }
        event.getToolTip().add(Component.translatable("tooltip.dimensional_mana.affix_grade", grade.name())
                .withStyle(grade.color));
        // 评级修正值：耐久上限一行，基础面板再按装备类别单独一行，玩家一眼能看出差距。
        String durabilityText = (grade.durabilityPct > 0 ? "+" : "") + grade.durabilityPct + "%";
        event.getToolTip().add(Component.translatable("tooltip.dimensional_mana.grade_bonus", durabilityText)
                .withStyle(ChatFormatting.AQUA));
        appendGradePanelLine(event.getToolTip(), stack, grade);
        if (grade.extraEnchantMax() > 0) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.dimensional_mana.grade_trait." + grade.name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        String schoolKey = "tooltip.dimensional_mana.affix_school."
                + ("magic".equals(root.getString(SCHOOL)) ? "magic" : "physical");
        event.getToolTip().add(Component.translatable("tooltip.dimensional_mana.affix_school",
                Component.translatable(schoolKey)).withStyle(ChatFormatting.DARK_AQUA));
        if (root.getBoolean(TEMPLATE_FORGED)) {
            event.getToolTip().add(Component.translatable("tooltip.dimensional_mana.template_forged")
                    .withStyle(ChatFormatting.GOLD));
        }
        CompoundTag main = root.getCompound(MAIN);
        appendAffixLine(event.getToolTip(), main, ChatFormatting.GOLD, true);
        ListTag subs = root.getList(SUBS, 10);
        for (int i = 0; i < subs.size(); i++) appendAffixLine(event.getToolTip(), subs.getCompound(i), ChatFormatting.GRAY, false);
    }

    /**
     * Tooltip 里的「基础面板修正」一行：按装备类别说清楚这份修正体现在哪。
     *
     * <ul>
     *   <li>武器：写出攻击伤害的前后对比。修正已经乘进物品自己的攻击伤害属性，
     *       所以物品 Tooltip 上「主手持有：X 攻击伤害」显示的就是修正后的数值；</li>
     *   <li>弓弩：弓没有攻击伤害属性，箭矢伤害在命中时按倍率结算，所以直接写箭矢伤害倍率；</li>
     *   <li>护甲：写出护甲值的前后对比，穿上后属性面板与减伤都按修正后的值算。</li>
     * </ul>
     */
    private static void appendGradePanelLine(List<Component> tooltip, ItemStack stack, Grade grade) {
        if (grade.panelScale == 1.0D) return;
        if (isBowLike(stack)) {
            String scaleText = String.format(Locale.ROOT, "%.2f", grade.panelScale);
            String percentText = (grade.panelScale >= 1.0D ? "+" : "")
                    + Math.round((grade.panelScale - 1.0D) * 100.0D) + "%";
            tooltip.add(Component.translatable("tooltip.dimensional_mana.grade_panel_arrow",
                    scaleText, percentText).withStyle(ChatFormatting.AQUA));
            return;
        }
        double attackBase = gradeBaseAmount(stack, Attributes.ATTACK_DAMAGE);
        if (attackBase > 0.0D) {
            // 玩家基础攻击力是 1，Tooltip 上的「攻击伤害」= 1 + 物品自己的攻击力修正。
            tooltip.add(Component.translatable("tooltip.dimensional_mana.grade_panel_damage",
                    format(1.0D + attackBase), format(1.0D + round2(attackBase * grade.panelScale)))
                    .withStyle(ChatFormatting.AQUA));
            return;
        }
        double armorBase = gradeBaseAmount(stack, Attributes.ARMOR);
        if (armorBase > 0.0D) {
            tooltip.add(Component.translatable("tooltip.dimensional_mana.grade_panel_armor",
                    format(armorBase), format(round2(armorBase * grade.panelScale)))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * 一行词条。主词条按「（N级）」显示；副词条内部 1 级是初始「无等级」，
     * 提升后按内部等级 -1 写成 +1～+4，中英一致。
     */
    private static void appendAffixLine(List<Component> tooltip, CompoundTag tag, ChatFormatting color, boolean main) {
        if (!tag.contains(ID)) return;
        Affix affix = findAffix(tag.getString(ID));
        if (affix == null) return;
        double value = tag.getDouble(VALUE);
        String text = format(value) + (affix.percent() ? "%" : "");
        int level = tag.getInt(LEVEL);
        Component name = affixName(affix, tag);
        Component line;
        if (main) {
            line = Component.translatable("tooltip.dimensional_mana.affix_line", name, text, level);
        } else if (level <= 1) {
            line = Component.translatable("tooltip.dimensional_mana.affix_line_sub_plain", name, text);
        } else {
            line = Component.translatable("tooltip.dimensional_mana.affix_line_sub", name, text, level - 1);
        }
        tooltip.add(Component.literal("  ").append(line).withStyle(color));
    }

    /**
     * 词条名。元素附加要带上是哪一系（如「火焰元素附加」），
     * 这样同名的两条附加也能在主副词条里区分开。
     */
    private static Component affixName(Affix affix, CompoundTag tag) {
        if (!affix.elemental()) return Component.translatable(affix.name());
        String element = tag.getString(ELEMENT);
        if (element.isEmpty()) element = ELEMENT_IDS[0];
        return Component.translatable(affix.name(),
                Component.translatable("gui.dimensional_mana.element." + element));
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) return Integer.toString((int) Math.rint(value));
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** 同类装备合成：主词条相同升一级，副词条同类升一级，超过 4 条随机截断。 */
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty() || !ItemStack.isSameItem(left, right)
                || !hasAffix(left) || !hasAffix(right)) return;
        ItemStack output = merge(left, right, event.getPlayer().getRandom());
        event.setOutput(output);
        event.setCost(Math.max(1L, Math.min(39L, event.getCost() <= 0 ? 1L : event.getCost())));
        event.setMaterialCost(1);
    }

    /**
     * 供魔晶铁砧直接调用的词条合成。
     *
     * <p>正常情况下走 NeoForge 的 {@code AnvilUpdateEvent}（见 {@link #onAnvilUpdate}）；
     * 这里再留一个直接入口，万一事件没被触发，菜单也能自己把两件装备的词条合出来。</p>
     *
     * @return 合成结果；两件里只要有一件没有词条就返回空，交回原版逻辑处理
     */
    public static ItemStack mergeForAnvil(ItemStack left, ItemStack right, net.minecraft.util.RandomSource random) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return ItemStack.EMPTY;
        if (!ItemStack.isSameItem(left, right) || !hasAffix(left) || !hasAffix(right)) return ItemStack.EMPTY;
        return merge(left, right, random);
    }

    /**
     * 同类装备合成（4.3）：
     * <ul>
     *   <li>主词条：两件相同 → 等级 +1；不同 → 取左槽（第一件）的主词条；已满 12 级 → 额外触发一次副词条成长。</li>
     *   <li>副词条：左槽原样保留，右槽同名词条在左槽基础上 +1 级，新词条直接加入；
     *       合并后超过 4 条时随机保留 4 条。</li>
     *   <li>主词条等级跨过 3 / 6 / 9 / 12 时，每次跨档各追加一次随机副词条成长。</li>
     * </ul>
     */
    private static ItemStack merge(ItemStack left, ItemStack right, net.minecraft.util.RandomSource random) {
        ItemStack output = left.copyWithCount(1);
        CompoundTag a = affixRoot(left).copy();
        CompoundTag b = affixRoot(right);
        Grade grade = Grade.parse(a.getString(GRADE));
        CompoundTag main = a.getCompound(MAIN);
        CompoundTag otherMain = b.getCompound(MAIN);
        boolean sameMain = main.getString(ID).equals(otherMain.getString(ID));
        boolean mainMaxed = main.getInt(LEVEL) >= MAIN_MAX_LEVEL;

        ListTag combined = new ListTag();
        ListTag leftSubs = a.getList(SUBS, 10);
        ListTag rightSubs = b.getList(SUBS, 10);
        for (int i = 0; i < leftSubs.size(); i++) combined.add(leftSubs.getCompound(i).copy());
        for (int i = 0; i < rightSubs.size(); i++) {
            CompoundTag incoming = rightSubs.getCompound(i);
            CompoundTag existing = findTag(combined, incoming.getString(ID));
            if (existing == null) combined.add(incoming.copy());
            else levelUpSub(existing);
        }

        // 主词条结算：相同升一级（满级则补一次副词条成长），不同保持左槽主词条。
        int bonusSubGrowth = 0;
        if (sameMain) {
            if (mainMaxed) {
                bonusSubGrowth += 1;
            } else {
                int before = main.getInt(LEVEL);
                levelUpMain(main);
                bonusSubGrowth += subGrowthCrossings(before, main.getInt(LEVEL));
            }
        }
        for (int i = 0; i < bonusSubGrowth; i++) {
            a.put(MAIN, main);
            growSubStage(combined, a, output, schoolOf(a, output), random);
        }

        // 两件的耐久也拼起来：词条合成都吃掉了右槽，耐久不该白丢。
        if (output.isDamageableItem()) {
            int damage = left.getDamageValue() + right.getDamageValue();
            output.setDamageValue(Math.max(0, Math.min(output.getMaxDamage() - 1, damage)));
        }
        while (combined.size() > SUB_MAX_COUNT) combined.remove(random.nextInt(combined.size()));
        a.put(MAIN, main);
        a.put(SUBS, combined);
        // 走统一的 writeRoot：名字、光效、评级耐久上限一起结算，也不再把左槽的其它物品数据冲掉。
        return writeRoot(output, a, grade);
    }

    /** 主词条等级上限。 */
    public static final int MAIN_MAX_LEVEL = 12;
    /** 副词条等级上限。内部 1 级是「无等级」的初始档，2～5 级对外显示 +1～+4。 */
    public static final int SUB_MAX_LEVEL = 5;
    /** 副词条数量上限。 */
    public static final int SUB_MAX_COUNT = 4;
    /** 主词条每到这些等级，额外让一条副词条成长。 */
    private static final int[] SUB_GROWTH_LEVELS = {3, 6, 9, 12};

    /** 主词条升级：按主词条曲线 mainStart + mainStep × (等级-1) 计算数值。 */
    private static void levelUpMain(CompoundTag tag) {
        Affix affix = findAffix(tag.getString(ID));
        if (affix == null) return;
        int level = Math.min(MAIN_MAX_LEVEL, tag.getInt(LEVEL) + 1);
        tag.putInt(LEVEL, level);
        tag.putDouble(VALUE, affix.mainValue(level));
    }

    /** 副词条升级：按副词条曲线 start + subStep × (等级-1) 计算数值，内部最多 5 级（对外最多 +4）。 */
    private static void levelUpSub(CompoundTag tag) {
        Affix affix = findAffix(tag.getString(ID));
        if (affix == null) return;
        int level = Math.min(SUB_MAX_LEVEL, tag.getInt(LEVEL) + 1);
        tag.putInt(LEVEL, level);
        tag.putDouble(VALUE, affix.subValue(tag.getDouble(START), level));
    }

    /** 主词条从 {@code before} 升到 {@code after}，跨过了几个 3/6/9/12 档位。 */
    private static int subGrowthCrossings(int before, int after) {
        int count = 0;
        for (int threshold : SUB_GROWTH_LEVELS) if (before < threshold && after >= threshold) count++;
        return count;
    }

    /** 随机挑一条还没满级的副词条 +1 级。 */
    private static void growRandomSub(ListTag subs, net.minecraft.util.RandomSource random) {
        List<CompoundTag> candidates = new ArrayList<>();
        for (int i = 0; i < subs.size(); i++) {
            CompoundTag tag = subs.getCompound(i);
            Affix affix = findAffix(tag.getString(ID));
            if (affix == null || affix.subStep() <= 0) continue;
            if (tag.getInt(LEVEL) >= SUB_MAX_LEVEL) continue;
            candidates.add(tag);
        }
        if (candidates.isEmpty()) return;
        levelUpSub(candidates.get(random.nextInt(candidates.size())));
    }

    /**
     * 一次「副词条成长环节」，升级跨档、满级额外成长、武备强化石走的都是这里。
     *
     * <ol>
     *   <li>副词条为空：必定补 1～3 条（以前空词条装备一路升级都长不出副词条）；</li>
     *   <li>否则 50% 概率新增一条副词条（不足 4 条时），另 50% 把一条已有的升一级；</li>
     *   <li>抽中的那一支做不了（没有可选词条 / 已经全满）就自动走另一支。</li>
     * </ol>
     */
    private static void growSubStage(ListTag subs, CompoundTag root, ItemStack stack, School school,
                                     net.minecraft.util.RandomSource random) {
        CompoundTag mainTag = root.getCompound(MAIN);
        if (subs.isEmpty()) {
            int count = 1 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                if (!addRandomSub(subs, mainTag, stack, school, random)) break;
            }
            return;
        }
        boolean canAdd = subs.size() < SUB_MAX_COUNT && hasAffixAvailable(subs, mainTag, stack, school);
        boolean canGrow = hasNotMaxedSub(subs);
        if (canAdd && (!canGrow || random.nextDouble() < 0.5D)) {
            addRandomSub(subs, mainTag, stack, school, random);
        } else {
            growRandomSub(subs, random);
        }
    }

    /** 副词条池里还有没有能加的新词条（排除主词条、已有词条、以及这件装备不允许出的专属词条）。 */
    private static boolean hasAffixAvailable(ListTag subs, CompoundTag mainTag, ItemStack stack, School school) {
        return !availableSubs(subs, mainTag, stack, school).isEmpty();
    }

    /** 当前装备还能新加哪些副词条：流派一致、有副词条曲线、装备允许、主副去重族不撞车。 */
    private static List<Affix> availableSubs(ListTag subs, CompoundTag mainTag, ItemStack stack, School school) {
        Set<String> usedGroups = usedGroups(subs, mainTag);
        List<Affix> available = new ArrayList<>();
        for (Affix affix : AFFIXES) {
            if (affix.school() != school || !affix.hasSub()) continue;
            if (!allowedOn(affix, stack)) continue;
            if (usedGroups.contains(affix.group())) continue;
            available.add(affix);
        }
        return available;
    }

    /** 主词条与现有副词条一共占了哪些去重族；元素附加按「具体哪一系」记账。 */
    private static Set<String> usedGroups(ListTag subs, CompoundTag mainTag) {
        Set<String> used = new java.util.HashSet<>();
        if (mainTag != null && mainTag.contains(ID)) collectGroups(mainTag, used);
        for (int i = 0; i < subs.size(); i++) collectGroups(subs.getCompound(i), used);
        return used;
    }

    /** 新加一条副词条（1 级，初始值在该词条的起始区间里随机）。 */
    private static boolean addRandomSub(ListTag subs, CompoundTag mainTag, ItemStack stack, School school,
                                        net.minecraft.util.RandomSource random) {
        if (subs.size() >= SUB_MAX_COUNT) return false;
        CompoundTag tag = rollSubTag(stack, school, usedGroups(subs, mainTag), random);
        if (tag == null) return false;
        subs.add(tag);
        return true;
    }

    /** 这组副词条里还有没有能升的（没满 4 级）。 */
    private static boolean hasNotMaxedSub(ListTag subs) {
        for (int i = 0; i < subs.size(); i++) {
            CompoundTag tag = subs.getCompound(i);
            Affix affix = findAffix(tag.getString(ID));
            if (affix == null || affix.subStep() <= 0) continue;
            if (tag.getInt(LEVEL) < SUB_MAX_LEVEL) return true;
        }
        return false;
    }

    /** 读词条根节点里记录的流派；没记或者记坏了就按装备类别取概率更高的一档兜底。 */
    private static School schoolOf(CompoundTag root, ItemStack stack) {
        String stored = root.getString(SCHOOL);
        if ("magic".equals(stored)) return School.MAGIC;
        if ("physical".equals(stored)) return School.PHYSICAL;
        Item item = stack.getItem();
        if (item instanceof MagicStaffItem) return School.MAGIC;
        ResourceLocation registryId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        String path = registryId == null ? "" : registryId.getPath();
        boolean modded = registryId != null && registryId.getNamespace().equals(GeneratedMod.MOD_ID);
        if (modded && (path.startsWith("rough_cloth_") || path.startsWith("magic_cloth_") || path.endsWith("_robe"))) {
            return School.MAGIC;
        }
        return School.PHYSICAL;
    }

    private static CompoundTag findTag(ListTag list, String id) {
        for (int i = 0; i < list.size(); i++) if (id.equals(list.getCompound(i).getString(ID))) return list.getCompound(i);
        return null;
    }

    // ------------------------------------------------------------------
    // 武备加工台：读等级、敲击升级、超导魔晶洗练
    // ------------------------------------------------------------------

    /** 洗练次数存在词条根节点里，跟着装备走（换台机器、换存档都不会丢）。 */
    private static final String REROLL = "rerolls";

    /**
     * 超导魔晶洗练的品质概率表，行 = 第几次重置（第 7 次及以后沿用最后一行），列 = C / B / A / S / SS / SSS。
     * 单位是百分数，每行合计 100。
     */
    private static final double[][] REROLL_TABLE = {
            {50.00, 40.00, 5.00, 2.50, 1.50, 1.00},
            {40.00, 50.00, 5.00, 2.50, 1.50, 1.00},
            {30.00, 50.00, 15.00, 2.50, 1.50, 1.00},
            {23.39, 52.63, 17.54, 3.51, 1.76, 1.17},
            {12.99, 38.96, 25.97, 16.23, 3.25, 2.60},
            {5.26, 21.05, 31.58, 21.05, 10.53, 10.53}
    };

    /** 界面上的一条词条：只带渲染需要的东西，不暴露内部的 {@link Grade}/{@link School}。 */
    public record AffixLine(String id, String nameKey, boolean percent, double value, int level, boolean main,
                            String element) {
        /** 普通词条可用的构造器：没有「哪一系元素」这个说法。 */
        public AffixLine(String id, String nameKey, boolean percent, double value, int level, boolean main) {
            this(id, nameKey, percent, value, level, main, null);
        }

        /** 锁定比对用的键：普通词条是 id，元素附加要带上具体是哪一系。 */
        public String lockKey() {
            return element == null || element.isEmpty() ? id : id + "#" + element;
        }

        public Component name() {
            if (element == null || element.isEmpty()) return Component.translatable(nameKey);
            return Component.translatable(nameKey, Component.translatable("gui.dimensional_mana.element." + element));
        }

        public String displayValue() {
            return format(value) + (percent ? "%" : "");
        }

        /**
         * 和装备 Tooltip 同一种写法：主词条「攻击速度 8%（2级）」按 LV 显示；
         * 副词条初始 1 级算「无等级」只写名字和数值，提升后按 +1～+4 标。
         */
        public Component asLine() {
            if (main) return Component.translatable("tooltip.dimensional_mana.affix_line", name(), displayValue(), level);
            if (level <= 1) return Component.translatable("tooltip.dimensional_mana.affix_line_sub_plain", name(), displayValue());
            return Component.translatable("tooltip.dimensional_mana.affix_line_sub", name(), displayValue(), level - 1);
        }
    }

    /** 一件武备的词条面板数据；{@link #empty()} 为真表示这件装备还没有词条。 */
    public record View(String gradeName, String schoolId, int mainLevel, AffixLine main, List<AffixLine> subs,
                       int rerolls) {
        public static View none() {
            return new View("", "", 0, null, List.of(), 0);
        }

        public boolean empty() {
            return main == null;
        }
    }

    /** 读出装备的词条面板数据；没有词条时返回 {@link View#none()}。 */
    public static View view(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        if (root == null) return View.none();
        CompoundTag mainTag = root.getCompound(MAIN);
        Affix mainAffix = findAffix(mainTag.getString(ID));
        AffixLine mainLine = mainAffix == null ? null
                : new AffixLine(mainAffix.id(), mainAffix.name(), mainAffix.percent(), mainTag.getDouble(VALUE),
                mainTag.getInt(LEVEL), true, mainTag.getString(ELEMENT));
        List<AffixLine> lines = new ArrayList<>();
        ListTag subs = root.getList(SUBS, 10);
        for (int i = 0; i < subs.size() && lines.size() < SUB_MAX_COUNT; i++) {
            CompoundTag tag = subs.getCompound(i);
            Affix affix = findAffix(tag.getString(ID));
            if (affix == null) continue;
            lines.add(new AffixLine(affix.id(), affix.name(), affix.percent(), tag.getDouble(VALUE),
                    tag.getInt(LEVEL), false, tag.getString(ELEMENT)));
        }
        return new View(root.getString(GRADE), root.getString(SCHOOL), mainTag.getInt(LEVEL), mainLine, lines,
                Math.max(0, root.getInt(REROLL)));
    }

    /** 武备等级 = 主词条等级。 */
    public static int mainLevel(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        return root == null ? 0 : root.getCompound(MAIN).getInt(LEVEL);
    }

    // ------------------------------------------------------------------
    // 武备加工台：目标词条锁定（防手滑）用的候选表与比对
    // ------------------------------------------------------------------

    /**
     * 锁定面板里的一条候选词条。
     *
     * <p>{@code key} 是锁定与比对的唯一凭据（元素附加带具体是哪一系）；{@code schoolOk} 表示
     * 它和这件装备当前的物理 / 魔法流派一致，{@code itemOk} 表示这件装备（剑 / 弓 / 盾 / 三叉戟）
     * 允许出这条词条。两个都为真才允许勾选，否则界面上灰掉。</p>
     */
    public record AffixOption(String key, String id, String nameKey, String element, String schoolId,
                              boolean schoolOk, boolean itemOk, boolean percent) {
        /** 能不能勾选：流派对、且这件装备允许出。 */
        public boolean selectable() { return schoolOk && itemOk; }

        /** 类型标签：「百分比」还是「数值」。同名的一对词条（斗气 / 斗气%）就靠它区分。 */
        public Component typeTag() { return EquipmentAffixSystem.typeTag(percent); }

        public Component name() {
            if (element == null || element.isEmpty()) return Component.translatable(nameKey);
            return Component.translatable(nameKey, Component.translatable("gui.dimensional_mana.element." + element));
        }
    }

    /** 词条类型标签的语言键。 */
    public static final String TAG_PERCENT = "gui.dimensional_mana.weapon_workbench.tag_percent";
    public static final String TAG_FLAT = "gui.dimensional_mana.weapon_workbench.tag_flat";

    /**
     * 「这条词条是百分比还是固定数值」的短标签。
     *
     * <p>同名的一对词条（斗气 +3.5% / 斗气 +12）光看名字是一样的，界面里必须标出来。</p>
     */
    public static Component typeTag(boolean percent) {
        return Component.translatable(percent ? TAG_PERCENT : TAG_FLAT);
    }

    /** 这件装备当前记在物品数据里的流派 id（{@code magic} / {@code physical}）；拿不准就按物品类型兜底。 */
    public static String schoolIdOf(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        if (root == null) return "";
        return schoolOf(root, stack).name().toLowerCase(Locale.ROOT);
    }

    /**
     * 主词条候选表（锁定面板左栏）：列出全部能做主词条的词条，元素附加按 12 系分别列出。
     *
     * <p>不从 {@link #mainPool} 取，因为池子已经过滤掉流派和装备类型了；面板要「灰掉」而不是
     * 「看不见」，玩家才知道为什么勾不上。可以勾的排在前面，稳定排序保持客户端与服务端一致。</p>
     */
    public static List<AffixOption> mainOptions(ItemStack stack) {
        return options(stack, true);
    }

    /** 副词条候选表（锁定面板右栏），规则与 {@link #mainOptions} 相同。 */
    public static List<AffixOption> subOptions(ItemStack stack) {
        return options(stack, false);
    }

    private static List<AffixOption> options(ItemStack stack, boolean main) {
        String school = schoolIdOf(stack);
        List<AffixOption> list = new ArrayList<>();
        for (Affix affix : AFFIXES) {
            if (main ? !affix.hasMain() : !affix.hasSub()) continue;
            String affixSchool = affix.school().name().toLowerCase(Locale.ROOT);
            boolean schoolOk = school.isEmpty() || affixSchool.equals(school);
            boolean itemOk = allowedOn(affix, stack);
            if (affix.elemental()) {
                // 元素附加：12 系各占一条，勾哪一系就只认哪一系。
                for (String element : ELEMENT_IDS) {
                    list.add(new AffixOption(affix.id() + "#" + element, affix.id(), affix.name(), element,
                            affixSchool, schoolOk, itemOk, affix.percent()));
                }
            } else {
                list.add(new AffixOption(affix.id(), affix.id(), affix.name(), "", affixSchool, schoolOk, itemOk,
                        affix.percent()));
            }
        }
        // 能勾的排前面；同档保持定义顺序（List.sort 是稳定排序）。
        list.sort((a, b) -> Boolean.compare(b.selectable(), a.selectable()));
        return list;
    }

    /**
     * 词条 id → 拼音（按词分开写，中间用空格）。
     *
     * <p>面板上的词条名是中文，可「游戏窗口里能不能调出输入法」是系统和输入法说了算：
     * 不少玩家在游戏里根本打不出中文。这里给每条词条备一份拼音，搜索框就能用全拼
     * （{@code mosu}）、连写（{@code wulishanghai}）或首字母（{@code wlsh}）找到中文词条，
     * 完全不依赖输入法。</p>
     */
    private static final Map<String, String> AFFIX_PINYIN = Map.ofEntries(
            Map.entry("mana_percent", "mo su"),
            Map.entry("mana_flat", "mo su"),
            Map.entry("spell_power_percent", "mo su qiang du"),
            Map.entry("spell_power_flat", "mo su qiang du"),
            Map.entry("spell_crit_chance", "fa shu bao ji lv"),
            Map.entry("spell_crit_damage", "fa shu bao ji shang hai"),
            Map.entry("elemental_mastery", "yuan su jing tong"),
            Map.entry("element_damage", "yuan su shang hai"),
            Map.entry("mana_affinity", "mo su qin he"),
            Map.entry("mana_efficiency", "mo fa xiao lv"),
            Map.entry("mana_penetration", "mo li tong tou"),
            Map.entry("battle_qi_percent", "dou qi"),
            Map.entry("battle_qi_flat", "dou qi"),
            Map.entry("physical_damage_flat", "wu li shang hai"),
            Map.entry("physical_damage_percent", "wu li shang hai"),
            Map.entry("physical_crit_chance", "wu li bao ji lv"),
            Map.entry("physical_crit_damage", "wu li bao ji shang hai"),
            Map.entry("attack_speed", "gong ji su du"),
            Map.entry("max_health_flat", "zui da sheng ming zhi"),
            Map.entry("max_health_percent", "zui da sheng ming zhi"),
            Map.entry("element_fire", "huo yuan su shang hai"),
            Map.entry("element_water", "shui yuan su shang hai"),
            Map.entry("element_ice", "bing yuan su shang hai"),
            Map.entry("element_lightning", "lei yuan su shang hai"),
            Map.entry("element_wind", "feng yuan su shang hai"),
            Map.entry("element_earth", "tu yuan su shang hai"),
            Map.entry("element_nature", "mu yuan su shang hai"),
            Map.entry("element_poison", "du yuan su shang hai"),
            Map.entry("element_metal", "jin yuan su shang hai"),
            Map.entry("element_light", "guang yuan su shang hai"),
            Map.entry("element_sound", "yin yuan su shang hai"),
            Map.entry("element_arcane", "ao shu yuan su shang hai"),
            Map.entry("armor_penetration", "po jia"),
            Map.entry("combo_damage", "lian ji shang hai"),
            Map.entry("execute_damage", "chu jue shang hai"),
            Map.entry("elemental_addition", "yuan su fu jia"),
            Map.entry("parry_reflect", "tan fan"),
            Map.entry("trident_thrust", "chuo ci"),
            Map.entry("lifesteal", "wu li xi xue"),
            Map.entry("battle_qi_regen", "dou qi hui fu"),
            Map.entry("battle_qi_skill_power", "dou qi ji neng qiang du"),
            Map.entry("mikiri_damage", "jian qie shang hai"),
            Map.entry("parry_window", "zhao jia te hua"),
            Map.entry("arrow_damage_flat", "jian shi shang hai"),
            Map.entry("arrow_damage_percent", "jian shi shang hai"),
            Map.entry("armor_value", "hu jia zhi"),
            Map.entry("armor_toughness", "kui jia ren xing"));

    /** 元素 id → 拼音，让「火」也能用 {@code huo} 搜到。 */
    private static final Map<String, String> ELEMENT_PINYIN = Map.ofEntries(
            Map.entry("fire", "huo"),
            Map.entry("water", "shui"),
            Map.entry("ice", "bing"),
            Map.entry("lightning", "lei"),
            Map.entry("wind", "feng"),
            Map.entry("earth", "tu"),
            Map.entry("nature", "mu"),
            Map.entry("metal", "jin"),
            Map.entry("light", "guang"),
            Map.entry("poison", "du"),
            Map.entry("sound", "yin"),
            Map.entry("arcane", "ao shu"));

    /**
     * 一条词条在搜索框里能被匹配到的全部文字（已转小写）。
     *
     * <p>包含：界面上显示的名字、英文 id / 词条键（{@code mana_percent}、{@code element_damage#fire}）、
     * 元素 id 与元素中文名、拼音。拼音同时给出「分开」和「连写」两种：连着打 {@code wulishanghai}
     * 命中连写那一份，单独打 {@code shanghai} 也能命中；{@code wlsh} 这种首字母同样认。</p>
     */
    public static String searchText(AffixOption option) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(option.name().getString().toLowerCase(Locale.ROOT)).append(' ');
        sb.append(option.key().toLowerCase(Locale.ROOT)).append(' ');
        sb.append(option.nameKey().toLowerCase(Locale.ROOT)).append(' ');
        // 按类型搜：搜「百分比 / baifenbi / percent」只留下百分比词条，搜「数值 / shuzhi / flat」只留下固定值。
        sb.append(typeTag(option.percent()).getString().toLowerCase(Locale.ROOT)).append(' ');
        sb.append(option.percent() ? "percent baifenbi bfb" : "flat shuzhi sz").append(' ');
        if (!option.element().isEmpty()) sb.append(option.element().toLowerCase(Locale.ROOT)).append(' ');
        String pinyin = AFFIX_PINYIN.get(option.id());
        if (pinyin != null) appendPinyin(sb, pinyin);
        if (!option.element().isEmpty()) {
            String elementPinyin = ELEMENT_PINYIN.get(option.element());
            if (elementPinyin != null) appendPinyin(sb, elementPinyin);
        }
        return sb.toString();
    }

    /** 拼音的三种写法一起塞进去：带空格、连写、首字母。 */
    private static void appendPinyin(StringBuilder sb, String pinyin) {
        sb.append(pinyin).append(' ');
        sb.append(pinyin.replace(" ", "")).append(' ');
        String[] words = pinyin.split(" ");
        for (String word : words) {
            if (!word.isEmpty()) sb.append(word.charAt(0));
        }
        sb.append(' ');
    }

    /**
     * 本次结果是否命中了玩家勾选的全部目标词条。
     *
     * <p>只有「一条不落地全中」才算命中：只出了物伤没出暴击、或者只中了两条副词条，
     * 都不触发防手滑，玩家可以继续洗。</p>
     *
     * @param mainKey 勾选的主词条键（空串表示没勾主词条）
     * @param subKeys 勾选的副词条键
     * @return 勾了东西、且全部命中时返回 {@code true}
     */
    public static boolean matchesLocks(ItemStack stack, String mainKey, java.util.Collection<String> subKeys) {
        boolean hasMainLock = mainKey != null && !mainKey.isEmpty();
        boolean hasSubLock = subKeys != null && !subKeys.isEmpty();
        if (!hasMainLock && !hasSubLock) return false;
        View view = view(stack);
        if (view.empty()) return false;
        if (hasMainLock && !mainKey.equals(view.main().lockKey())) return false;
        if (hasSubLock) {
            for (String key : subKeys) {
                boolean found = false;
                for (AffixLine line : view.subs()) {
                    if (key.equals(line.lockKey())) { found = true; break; }
                }
                if (!found) return false;
            }
        }
        return true;
    }

    /** 这条锁定键是不是一个真实存在的词条（服务端收包时校验，客户端塞乱七八糟的 id 不会误判命中）。 */
    public static boolean isKnownLockKey(String key) {
        if (key == null || key.isEmpty()) return false;
        int split = key.indexOf('#');
        String id = split < 0 ? key : key.substring(0, split);
        String element = split < 0 ? "" : key.substring(split + 1);
        Affix affix = findAffix(id);
        if (affix == null) return false;
        if (element.isEmpty()) return true;
        if (!affix.elemental()) return false;
        for (String candidate : ELEMENT_IDS) if (candidate.equals(element)) return true;
        return false;
    }

    /** 这件装备被超导魔晶洗练过几次。 */
    public static int rerollCount(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        return root == null ? 0 : Math.max(0, root.getInt(REROLL));
    }

    /**
     * 敲击升级：主词条 +1 级，跨过 3 / 6 / 9 / 12 级时追加一次随机副词条成长。
     *
     * <p>和魔晶铁砧「同类装备合成」用的是同一套等级曲线与成长规则，
     * 所以打铁升上来的武器和合出来的武器数值完全一致。</p>
     */
    public static ItemStack upgradeMainLevel(ItemStack stack, net.minecraft.util.RandomSource random) {
        CompoundTag root = affixRoot(stack);
        if (root == null || root.getCompound(MAIN).getInt(LEVEL) >= MAIN_MAX_LEVEL) return stack;
        root = root.copy();
        CompoundTag main = root.getCompound(MAIN);
        int before = main.getInt(LEVEL);
        levelUpMain(main);
        ListTag subs = root.getList(SUBS, 10);
        School school = schoolOf(root, stack);
        for (int i = 0; i < subGrowthCrossings(before, main.getInt(LEVEL)); i++) {
            root.put(MAIN, main);
            growSubStage(subs, root, stack, school, random);
        }
        root.put(MAIN, main);
        root.put(SUBS, subs);
        return writeRoot(stack, root, Grade.parse(root.getString(GRADE)));
    }

    /**
     * 超导魔晶洗练：品质、流派、主词条与副词条全部重抽，重置次数 +1。
     *
     * <p>概率随重置次数变化（见 {@link #REROLL_TABLE}），越洗越容易出高品质。
     * 流派按 {@link #schoolFor} 的类别概率表重掷（武器 80/20、防具 60/40、工具全物理、
     * 法杖布甲全魔法、魔精钢 50/50），不会出现物理武器配魔法词条。</p>
     */
    public static ItemStack rerollAffixes(ItemStack stack, net.minecraft.util.RandomSource random) {
        if (affixRoot(stack) == null) return stack;
        int count = rerollCount(stack) + 1;
        int row = Math.min(REROLL_TABLE.length - 1, count - 1);
        double roll = random.nextDouble() * 100.0D;
        Grade[] grades = Grade.values();
        Grade grade = grades[grades.length - 1];
        double accumulated = 0.0D;
        for (int i = 0; i < grades.length; i++) {
            accumulated += REROLL_TABLE[row][i];
            if (roll < accumulated) {
                grade = grades[i];
                break;
            }
        }
        CompoundTag root = buildRoot(stack, grade, schoolFor(stack, random), random, false);
        root.putInt(REROLL, count);
        return writeRoot(stack, root, grade);
    }

    /**
     * 词条重铸符：**品级不变**，只把主词条与副词条按当前品级的规则重新抽一遍。
     *
     * <p>和超导魔晶洗练的区别就在这里：洗练连品质、流派一起重掷（可能掉级），
     * 重铸只换「词条长什么样」，S 级洗完还是 S 级。条数与等级区间仍然由品级决定，
     * 所以 SSS 依旧是 2～4 条副词条、主词条更高等级。已经掷过的评级附魔特性
     * 与锻造模板标记都会保留，不会因为重铸而白送一次附魔。</p>
     */
    public static ItemStack reforgeAffixes(ItemStack stack, net.minecraft.util.RandomSource random) {
        CompoundTag root = affixRoot(stack);
        if (root == null) return stack;
        Grade grade = Grade.parse(root.getString(GRADE));
        CompoundTag fresh = buildRoot(stack, grade, schoolOf(root, stack), random, false);
        if (root.contains(REROLL)) fresh.putInt(REROLL, root.getInt(REROLL));
        if (root.getBoolean(TEMPLATE_FORGED)) fresh.putBoolean(TEMPLATE_FORGED, true);
        if (root.getBoolean("boss_drop")) fresh.putBoolean("boss_drop", true);
        if (root.getBoolean(TRAIT_ROLLED)) fresh.putBoolean(TRAIT_ROLLED, true);
        return writeRoot(stack, fresh, grade);
    }

    /** 评级给的铁砧 / 武备加工台消耗减免（SS 10%、SSS 20%，其余 0）。 */
    public static double gradeDiscount(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        return root == null ? 0.0D : Grade.parse(root.getString(GRADE)).discount();
    }

    /**
     * 铁砧消耗减免：SS / SSS 的武备在铁砧上修、合并、附魔书都便宜 10% / 20%。
     *
     * <p>注册时用最低优先级，保证在「同类装备合成」那个监听器改完价格之后
     * 再打折，不会被打工顺序吃掉。</p>
     */
    public static void onAnvilCost(AnvilUpdateEvent event) {
        double discount = Math.max(gradeDiscount(event.getLeft()), gradeDiscount(event.getRight()));
        if (discount <= 0.0D) return;
        long cost = event.getCost();
        if (cost <= 0L) return;
        event.setCost(Math.max(1L, (long)Math.ceil(cost * (1.0D - discount))));
    }

    // ------------------------------------------------------------------
    // 武备加工台：武备强化石（副词条强制成长）与锻造模板（白板赋词条）
    // ------------------------------------------------------------------

    /** 用锻造模板打造出来的武备，升级魔素消耗要额外 +50%，标记跟着装备走。 */
    private static final String TEMPLATE_FORGED = "template_forged";

    /** 锻造模板赋予词条的品质权重，顺序与 {@link Grade} 一致：C / B / A / S / SS / SSS。 */
    private static final double[] TEMPLATE_WEIGHTS = {40.00, 38.00, 20.00, 1.25, 0.50, 0.25};

    /** 这件武备是不是用锻造模板赋予出来的（升级要贵 50%）。 */
    public static boolean isTemplateForged(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        return root != null && root.getBoolean(TEMPLATE_FORGED);
    }

    /**
     * 锻造模板赋予：给一件白板装备按 C40% / B38% / A20% / S1.25% / SS0.5% / SSS0.25% 抽一次武备。
     *
     * <p>流派按 {@link #schoolFor} 的类别概率表自动掷（武器 80% 物理、防具 60% 物理、
     * 工具与法杖各走一边、魔精钢五五开），词条条数与等级沿用该品质的常规规则；
     * 唯一区别是根节点里会留下 {@link #TEMPLATE_FORGED} 标记，之后每次敲击升级多花 50% 魔素。</p>
     */
    public static ItemStack applyTemplateAffix(ItemStack stack, net.minecraft.util.RandomSource random) {
        Grade grade = rollTemplateGrade(random);
        CompoundTag root = buildRoot(stack, grade, schoolFor(stack, random), random, false);
        root.putBoolean(TEMPLATE_FORGED, true);
        ItemStack output = stack.copyWithCount(1);
        return writeRoot(output, root, grade);
    }

    private static Grade rollTemplateGrade(net.minecraft.util.RandomSource random) {
        Grade[] grades = Grade.values();
        double roll = random.nextDouble() * 100.0D;
        double accumulated = 0.0D;
        for (int i = 0; i < grades.length; i++) {
            accumulated += TEMPLATE_WEIGHTS[Math.min(i, TEMPLATE_WEIGHTS.length - 1)];
            if (roll < accumulated) return grades[i];
        }
        return Grade.C;
    }

    /**
     * 这件武备的副词条还能不能「成长」：有没满 4 级的，或者还没凑齐 4 条（可以再加新的）。
     */
    public static boolean hasGrowableSub(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        if (root == null) return false;
        ListTag subs = root.getList(SUBS, 10);
        if (hasNotMaxedSub(subs)) return true;
        return subs.size() < SUB_MAX_COUNT
                && hasAffixAvailable(subs, root.getCompound(MAIN), stack, schoolOf(root, stack));
    }

    /**
     * 武备强化石：强制一次副词条成长。
     *
     * <p>走的是和升级跨档同一个 {@link #growSubStage}：空副词条必定补 1～3 条，
     * 否则 50% 加新词条、50% 升已有词条。已经 4 条全满时原样返回，由菜单那边提示玩家。</p>
     */
    public static ItemStack growOneSub(ItemStack stack, net.minecraft.util.RandomSource random) {
        CompoundTag root = affixRoot(stack);
        if (root == null || !hasGrowableSub(stack)) return stack;
        root = root.copy();
        ListTag subs = root.getList(SUBS, 10);
        growSubStage(subs, root, stack, schoolOf(root, stack), random);
        root.put(SUBS, subs);
        return writeRoot(stack, root, Grade.parse(root.getString(GRADE)));
    }

    /** 装备改完立刻重算属性：正穿在身上的装备不用再等下一次玩家 tick。 */
    public static void refreshPlayer(Player player) {
        if (player != null && !player.level().isClientSide) refresh(player);
    }

    // ------------------------------------------------------------------
    // 砂轮剥皮（4.2）：砂轮修复把累积惩罚设成 2^(诅咒数-1)，非诅咒附魔与词条全部丢失。
    // ------------------------------------------------------------------

    /** 砂轮磨装备：剥掉词条与非诅咒附魔，并把累积惩罚改写成 2^(诅咒附魔数量-1)。 */
    public static void onGrindstonePlace(net.neoforged.neoforge.event.GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem();
        if (top.isEmpty() || !isEquipment(top)) return;
        ItemStack bottom = event.getBottomItem();
        if (bottom != null && !bottom.isEmpty()) return;   // 双槽是“合成修复”，交给原版
        if (ElementPlating.hasPlating(top) || BlueprintGrinding.isGrindable(top)) return;
        // 只有“真的带了东西可剥”（词条或附魔）才接管；单纯的累积惩罚不给免费重置。
        boolean enchanted = EnchantmentHelper.hasAnyEnchantments(top);
        if (!hasAffix(top) && !enchanted) return;
        ItemStack output = top.copyWithCount(top.getCount());
        stripAffixes(output);
        int curses = countCurses(output);
        stripNonCurseEnchantments(output);
        int penalty = cursePenalty(curses);
        if (penalty <= 0) output.remove(net.minecraft.core.component.DataComponents.REPAIR_COST);
        else output.set(net.minecraft.core.component.DataComponents.REPAIR_COST, penalty);
        event.setOutput(output);
    }

    /** 累积惩罚 = 2^(诅咒魔咒数量 - 1)；没有诅咒就清零。 */
    public static int cursePenalty(int curses) {
        if (curses <= 0) return 0;
        return 1 << Math.min(20, curses - 1);
    }

    private static int countCurses(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int count = 0;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(net.minecraft.tags.EnchantmentTags.CURSE)) count++;
        }
        return count;
    }

    /** 只留下诅咒附魔，其余全部抹掉（砂轮剥皮的代价）。 */
    public static void stripNonCurseEnchantments(ItemStack stack) {
        if (!EnchantmentHelper.hasAnyEnchantments(stack)) return;
        ItemEnchantments current = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.removeIf(enchantment -> !enchantment.is(net.minecraft.tags.EnchantmentTags.CURSE));
        stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, mutable.toImmutable());
        // 魔精钢装备的「附魔 +1」是按已有附魔记账的：附魔被磨光后账本必须作废，不然重新附魔不会再 +1。
        MojiangSteelEvents.clearBoostIfUnenchanted(stack);
    }

    /** 抹掉装备上的整套词条数据（含词条前缀名与传说光效）。 */
    public static void stripAffixes(ItemStack stack) {
        if (!hasAffix(stack)) return;
        // 先把评级给的耐久上限与基础攻击 / 护甲还原成原版，再抹数据。
        restoreBaseDurability(stack);
        restoreBasePanel(stack);
        CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.remove(ROOT);
        if (tag.isEmpty()) stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        else CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag);
        stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        stack.remove(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
    }

    /** 当前累积惩罚（非仅重命名操作次数）。 */
    public static int penaltyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return Math.max(0, stack.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, 0));
    }

    /** 魔晶铁砧不损坏；普通铁砧仍保留原版损坏概率。 */
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (hasAffix(event.getOutput())) event.setBreakChance(0.0F);
    }

    /** 每次装备变化立即重算；Tick 只是兜底，避免旧存档或换手漏刷新。 */
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof Player player) refresh(player);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 20 != 0) return;
        refresh(player);
        // 每秒扫一遍身上和背包里的武备：补上耐久修正（老存档的装备也能自动补正），
        // 顺便检查「附魔时多出几条附魔」的评级特性有没有该结算的。
        // 只做几次标签判断，没词条的物品在第一行就返回，开销可以忽略。
        for (ItemStack stack : player.getInventory().items) ensureGradeState(stack, player);
        for (ItemStack stack : equippedAndHeld(player)) ensureGradeState(stack, player);
    }

    /** 身上穿戴的六格装备（护甲四件 + 主手 + 副手）。 */
    private static List<ItemStack> equippedAndHeld(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            stacks.add(player.getItemBySlot(slot));
        }
        return stacks;
    }

    /**
     * 让一件武备的评级修正保持最新：耐久上限按当前评级重算，专属附魔特性在
     * 「这件装备已经有了附魔、还没掷过特性」时结算一次。
     */
    private static void ensureGradeState(ItemStack stack, Player player) {
        CompoundTag root = affixRoot(stack);
        if (root == null) return;
        Grade grade = Grade.parse(root.getString(GRADE));
        applyGradeDurability(stack, grade);
        // 老存档的装备在旧版本里只记了耐久修正，这一行按当前评级把基础攻击 / 护甲补正上。
        applyGradePanel(stack, grade);
        applyGradeEnchantTrait(stack, grade, player);
    }

    /**
     * 词条提供的斗气百分比加成，单位是百分数，例如 3.5 表示 +3.5%。
     *
     * <p>r291 起这些数值来自真实注册的属性（{@link SpellAttributes#BATTLE_QI_PERCENT}），
     * 词条刷新时以属性修正的形式写进玩家，所以 Apothic Attributes 一类面板也能读到。</p>
     */
    public static double battleQiPercentBonus(Player player) {
        return SpellAttributes.value(player, SpellAttributes.BATTLE_QI_PERCENT, 0.0);
    }

    /** 词条提供的斗气固定值加成。 */
    public static int battleQiFlatBonus(Player player) {
        return (int)Math.round(SpellAttributes.value(player, SpellAttributes.BATTLE_QI_FLAT, 0.0));
    }

    /** 词条提供的魔素上限百分比，单位是百分数。 */
    public static double manaPercentBonus(Player player) {
        return SpellAttributes.value(player, SpellAttributes.MANA_MAX_PERCENT, 0.0);
    }

    /** 词条提供的魔素上限固定值。 */
    public static double manaFlatBonus(Player player) {
        return SpellAttributes.value(player, SpellAttributes.MANA_MAX_FLAT, 0.0);
    }

    /** 词条提供的魔素亲和固定值。 */
    public static double manaAffinityBonus(Player player) {
        return SpellAttributes.value(player, SpellAttributes.MANA_AFFINITY, 0.0);
    }

    /** 词条提供的元素精通固定值。 */
    public static float elementalMasteryBonus(Player player) {
        return (float)SpellAttributes.value(player, SpellAttributes.ELEMENTAL_MASTERY, 0.0);
    }

    /** 指定元素词条提供的独立元素伤害乘区（通用元素伤害 + 单元素伤害）。 */
    public static double elementalDamageMultiplier(Player player, String elementId) {
        double percent = SpellAttributes.value(player, SpellAttributes.ELEMENTAL_DAMAGE, 0.0)
                + SpellAttributes.value(player, SpellAttributes.elementDamage(elementId), 0.0);
        return 1.0 + percent / 100.0;
    }

    // ------------------------------------------------------------------
    // r332 物理词条扩充：运行时读这些值。全部走真实注册的属性，所以面板能查到、别的模组也读得到。
    // ------------------------------------------------------------------

    /** 破甲：攻击时无视目标多少点护甲值。 */
    public static double armorPenetration(Player player) {
        return SpellAttributes.value(player, SpellAttributes.ARMOR_PENETRATION, 0.0);
    }

    /** 物理吸血百分比：造成的物理伤害按这个百分数回血。 */
    public static double lifestealPercent(Player player) {
        return SpellAttributes.value(player, SpellAttributes.LIFESTEAL, 0.0);
    }

    /** 斗气额外恢复：每秒多恢复多少点斗气。 */
    public static double battleQiRegenBonus(Player player) {
        return SpellAttributes.value(player, SpellAttributes.BATTLE_QI_REGEN, 0.0);
    }

    /** 斗气技能强度：每点让斗气技能（拔刀斩、剑气）伤害 +2.5%，这里直接返回倍率。 */
    public static double battleQiSkillMultiplier(Player player) {
        return 1.0 + SpellAttributes.value(player, SpellAttributes.BATTLE_QI_SKILL_POWER, 0.0) * 0.025;
    }

    /** 连击伤害百分比：每层给多少增伤。 */
    public static double comboDamagePercent(Player player) {
        return SpellAttributes.value(player, SpellAttributes.COMBO_DAMAGE, 0.0);
    }

    /** 处决伤害百分比：目标进入处决线时的额外增伤。 */
    public static double executeDamagePercent(Player player) {
        return SpellAttributes.value(player, SpellAttributes.EXECUTE_DAMAGE, 0.0);
    }

    /** 见切伤害百分比：对招架破绽中的目标的额外增伤。 */
    public static double mikiriDamagePercent(Player player) {
        return SpellAttributes.value(player, SpellAttributes.MIKIRI_DAMAGE, 0.0);
    }

    /** 招架特化：完美格挡判定窗口额外延长多少刻。 */
    public static int parryWindowBonus(Player player) {
        return (int)Math.round(SpellAttributes.value(player, SpellAttributes.PARRY_WINDOW, 0.0));
    }

    /** 箭矢伤害固定值。 */
    public static double arrowDamageFlat(Player player) {
        return SpellAttributes.value(player, SpellAttributes.ARROW_DAMAGE_FLAT, 0.0);
    }

    /** 箭矢伤害百分比。 */
    public static double arrowDamagePercent(Player player) {
        return SpellAttributes.value(player, SpellAttributes.ARROW_DAMAGE_PERCENT, 0.0);
    }

    /** 弹反百分比：成功格挡后把这一下被格挡伤害的这个百分比反弹给攻击者。 */
    public static double parryReflectPercent(Player player) {
        return SpellAttributes.value(player, SpellAttributes.PARRY_REFLECT, 0.0);
    }

    /** 戳刺百分比：投掷 / 激流冲刺命中时的额外伤害，单位是百分数。 */
    public static double tridentThrustPercent(Player player) {
        return SpellAttributes.value(player, SpellAttributes.TRIDENT_THRUST, 0.0);
    }

    /**
     * 直接读某一件物品自己的戳刺词条值。
     *
     * <p>投掷出去的三叉戟已经不在手上，属性汇总里读不到它，只能从
     * {@code ThrownTrident#getWeaponItem()} 那一份物品数据里取。</p>
     */
    public static double tridentThrustPercentOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0.0 : affixValue(stack, "trident_thrust");
    }

    /**
     * 元素附加：某一件装备/整套装备附带的「哪一系、多少点」元素伤害。
     *
     * <p>返回 {@code 元素 id → 点数}；12 系都试一遍，只把有值的放进结果。
     * 物理命中时按这份表逐系结算附加的元素伤害。</p>
     */
    public static java.util.Map<String, Double> elementalAddition(Player player) {
        java.util.Map<String, Double> map = new java.util.LinkedHashMap<>();
        if (player == null) return map;
        for (String element : ELEMENT_IDS) {
            double value = SpellAttributes.value(player, SpellAttributes.elementAddition(element), 0.0);
            if (value > 0.0001) map.put(element, value);
        }
        return map;
    }

    private static double totalAffixValue(Player player, String id) {
        double total = 0.0;
        for (ItemStack stack : relevantEquipment(player)) total += affixValue(stack, id);
        return total;
    }

    private static double affixValue(ItemStack stack, String id) {
        CompoundTag root = affixRoot(stack);
        if (root == null) return 0.0;
        double total = 0.0;
        CompoundTag main = root.getCompound(MAIN);
        if (id.equals(main.getString(ID))) total += main.getDouble(VALUE);
        ListTag subs = root.getList(SUBS, 10);
        for (int i = 0; i < subs.size(); i++) {
            CompoundTag sub = subs.getCompound(i);
            if (id.equals(sub.getString(ID))) total += sub.getDouble(VALUE);
        }
        return total;
    }

    /** 12 个可吃单元素伤害词条的元素 id（11 种初级元素 + 奥术）。 */
    private static final String[] ELEMENT_IDS = {
            "fire", "water", "ice", "lightning", "wind", "earth",
            "nature", "metal", "light", "poison", "sound", "arcane"};

    private static int elementIndex(String id) {
        for (int i = 0; i < ELEMENT_IDS.length; i++) if (ELEMENT_IDS[i].equals(id)) return i;
        return -1;
    }

    private static void refresh(Player player) {
        clearModifier(player, SpellAttributes.SPELL_POWER, SPELL_POWER_MOD);
        clearModifier(player, SpellAttributes.SPELL_HASTE, SPELL_HASTE_MOD);
        clearModifier(player, SpellAttributes.SPELL_CRIT_CHANCE, SPELL_CRIT_MOD);
        clearModifier(player, SpellAttributes.SPELL_CRIT_DAMAGE, SPELL_CRIT_DAMAGE_MOD);
        clearModifier(player, SpellAttributes.SPELL_PENETRATION, SPELL_PENETRATION_MOD);
        clearModifier(player, SpellAttributes.PHYSICAL_CRIT_CHANCE, PHYSICAL_CRIT_MOD);
        clearModifier(player, SpellAttributes.PHYSICAL_CRIT_DAMAGE, PHYSICAL_CRIT_DAMAGE_MOD);
        clearModifier(player, SpellAttributes.MANA_COST_REDUCTION, MANA_REDUCTION_MOD);
        clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MOD);
        clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_PERCENT_MOD);
        clearModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MOD);
        clearModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_MOD);
        clearModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_PERCENT_MOD);
        // 护甲值 / 盔甲韧性也必须先清，否则卸下装备后旧数值会一直挂着。
        clearModifier(player, Attributes.ARMOR, ARMOR_VALUE_MOD);
        clearModifier(player, Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_MOD);
        // 词条专用的“可被外部面板读到”的属性：先清掉上一轮，再按当前装备重新写。
        clearModifier(player, SpellAttributes.MANA_MAX_PERCENT, AFFIX_MANA_PERCENT_MOD);
        clearModifier(player, SpellAttributes.MANA_MAX_FLAT, AFFIX_MANA_FLAT_MOD);
        clearModifier(player, SpellAttributes.MANA_AFFINITY, AFFIX_MANA_AFFINITY_MOD);
        clearModifier(player, SpellAttributes.ELEMENTAL_MASTERY, AFFIX_MASTERY_MOD);
        clearModifier(player, SpellAttributes.ELEMENTAL_DAMAGE, AFFIX_ELEMENT_DAMAGE_MOD);
        clearModifier(player, SpellAttributes.BATTLE_QI_PERCENT, AFFIX_QI_PERCENT_MOD);
        clearModifier(player, SpellAttributes.BATTLE_QI_FLAT, AFFIX_QI_FLAT_MOD);
        // r332 物理词条扩充：破甲、吸血、连击、处决、见切、招架窗口、箭矢、斗气恢复 / 技强、元素附加。
        clearModifier(player, SpellAttributes.ARMOR_PENETRATION, AFFIX_ARMOR_PEN_MOD);
        clearModifier(player, SpellAttributes.LIFESTEAL, AFFIX_LIFESTEAL_MOD);
        clearModifier(player, SpellAttributes.BATTLE_QI_REGEN, AFFIX_QI_REGEN_MOD);
        clearModifier(player, SpellAttributes.BATTLE_QI_SKILL_POWER, AFFIX_QI_SKILL_POWER_MOD);
        clearModifier(player, SpellAttributes.COMBO_DAMAGE, AFFIX_COMBO_MOD);
        clearModifier(player, SpellAttributes.EXECUTE_DAMAGE, AFFIX_EXECUTE_MOD);
        clearModifier(player, SpellAttributes.MIKIRI_DAMAGE, AFFIX_MIKIRI_MOD);
        clearModifier(player, SpellAttributes.PARRY_WINDOW, AFFIX_PARRY_WINDOW_MOD);
        clearModifier(player, SpellAttributes.ARROW_DAMAGE_FLAT, AFFIX_ARROW_FLAT_MOD);
        clearModifier(player, SpellAttributes.ARROW_DAMAGE_PERCENT, AFFIX_ARROW_PERCENT_MOD);
        clearModifier(player, SpellAttributes.PARRY_REFLECT, AFFIX_PARRY_REFLECT_MOD);
        clearModifier(player, SpellAttributes.TRIDENT_THRUST, AFFIX_TRIDENT_THRUST_MOD);
        for (String element : ELEMENT_IDS) {
            clearModifier(player, SpellAttributes.elementDamage(element), affixElementModifier(element));
            clearModifier(player, SpellAttributes.elementAddition(element), affixElementAdditionModifier(element));
        }

        double spellPower = 0, haste = 0, crit = 0, critDamage = 0, penetration = 0, efficiency = 0;
        double physicalCrit = 0, physicalCritDamage = 0;
        double attackDamage = 0, attackDamagePercent = 0, attackSpeed = 0, maxHealth = 0, maxHealthPercent = 0;
        double manaPercent = 0, manaFlat = 0, affinity = 0, mastery = 0, elementDamage = 0;
        double qiPercent = 0, qiFlat = 0;
        double armorPen = 0, lifesteal = 0, qiRegen = 0, qiSkillPower = 0;
        double comboDamage = 0, executeDamage = 0, mikiriDamage = 0, parryWindow = 0;
        double arrowFlat = 0, arrowPercent = 0, armorValue = 0, armorToughness = 0;
        double parryReflect = 0, tridentThrust = 0;
        double[] elementValues = new double[ELEMENT_IDS.length];
        double[] elementAdditions = new double[ELEMENT_IDS.length];
        for (ItemStack stack : relevantEquipment(player)) {
            CompoundTag root = affixRoot(stack);
            if (root == null) continue;
            // 评级的基础面板修正不在这里：它写在物品自己的属性组件里（见 applyGradePanel），
            // 拿在手上 / 穿在身上就自动生效，这里再加一遍会变成双倍。
            List<CompoundTag> all = new ArrayList<>();
            all.add(root.getCompound(MAIN));
            ListTag subs = root.getList(SUBS, 10);
            for (int i = 0; i < subs.size(); i++) all.add(subs.getCompound(i));
            for (CompoundTag tag : all) {
                Affix affix = findAffix(tag.getString(ID));
                if (affix == null) continue;
                double value = tag.getDouble(VALUE);
                switch (affix.id()) {
                    // 法术强度百分比按最终法术伤害百分比换算；固定值仍按属性点数结算（1 点 = 0.21%）。
                    case "spell_power_percent" -> spellPower += value / 0.21;
                    case "spell_power_flat" -> spellPower += value;
                    case "mana_penetration" -> penetration += value * .0025;
                    case "spell_crit_chance" -> crit += value / 100.0;
                    case "spell_crit_damage" -> critDamage += value / 100.0;
                    case "mana_efficiency" -> efficiency += value * .0025;
                    case "physical_damage_flat" -> attackDamage += value;
                    case "physical_damage_percent" -> attackDamagePercent += value / 100.0;
                    case "physical_crit_chance" -> physicalCrit += value / 100.0;
                    case "physical_crit_damage" -> physicalCritDamage += value / 100.0;
                    case "attack_speed" -> attackSpeed += value / 100.0;
                    case "max_health_flat" -> maxHealth += value;
                    case "max_health_percent" -> maxHealthPercent += value / 100.0;
                    case "mana_percent" -> manaPercent += value;
                    case "mana_flat" -> manaFlat += value;
                    case "mana_affinity" -> affinity += value;
                    case "elemental_mastery" -> mastery += value;
                    case "element_damage" -> elementDamage += value;
                    case "battle_qi_percent" -> qiPercent += value;
                    case "battle_qi_flat" -> qiFlat += value;
                    case "armor_penetration" -> armorPen += value;
                    case "lifesteal" -> lifesteal += value;
                    case "battle_qi_regen" -> qiRegen += value;
                    case "battle_qi_skill_power" -> qiSkillPower += value;
                    case "combo_damage" -> comboDamage += value;
                    case "execute_damage" -> executeDamage += value;
                    case "mikiri_damage" -> mikiriDamage += value;
                    case "parry_window" -> parryWindow += value;
                    case "arrow_damage_flat" -> arrowFlat += value;
                    case "arrow_damage_percent" -> arrowPercent += value;
                    case "parry_reflect" -> parryReflect += value;
                    case "trident_thrust" -> tridentThrust += value;
                    case "armor_value" -> armorValue += value;
                    case "armor_toughness" -> armorToughness += value;
                    case "elemental_addition" -> {
                        int added = elementIndex(tag.getString(ELEMENT));
                        if (added >= 0) elementAdditions[added] += value;
                    }
                    default -> {
                        int element = elementIndex(affix.id().startsWith("element_")
                                ? affix.id().substring("element_".length()) : "");
                        if (element >= 0) elementValues[element] += value;
                    }
                }
            }
        }
        addModifier(player.getAttribute(SpellAttributes.SPELL_POWER), SPELL_POWER_MOD, spellPower, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.SPELL_HASTE), SPELL_HASTE_MOD, haste, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.SPELL_CRIT_CHANCE), SPELL_CRIT_MOD, crit, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.SPELL_CRIT_DAMAGE), SPELL_CRIT_DAMAGE_MOD, critDamage, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.SPELL_PENETRATION), SPELL_PENETRATION_MOD, Math.min(.95, penetration), AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.PHYSICAL_CRIT_CHANCE), PHYSICAL_CRIT_MOD, physicalCrit, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.PHYSICAL_CRIT_DAMAGE), PHYSICAL_CRIT_DAMAGE_MOD, physicalCritDamage, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.MANA_COST_REDUCTION), MANA_REDUCTION_MOD, Math.min(.95, efficiency), AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_DAMAGE_MOD, attackDamage, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_DAMAGE_PERCENT_MOD, attackDamagePercent, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addModifier(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_MOD, attackSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addModifier(player.getAttribute(Attributes.MAX_HEALTH), MAX_HEALTH_MOD, maxHealth, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(Attributes.MAX_HEALTH), MAX_HEALTH_PERCENT_MOD, maxHealthPercent, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addModifier(player.getAttribute(SpellAttributes.MANA_MAX_PERCENT), AFFIX_MANA_PERCENT_MOD, manaPercent, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.MANA_MAX_FLAT), AFFIX_MANA_FLAT_MOD, manaFlat, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.MANA_AFFINITY), AFFIX_MANA_AFFINITY_MOD, affinity, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.ELEMENTAL_MASTERY), AFFIX_MASTERY_MOD, mastery, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.ELEMENTAL_DAMAGE), AFFIX_ELEMENT_DAMAGE_MOD, elementDamage, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.BATTLE_QI_PERCENT), AFFIX_QI_PERCENT_MOD, qiPercent, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.BATTLE_QI_FLAT), AFFIX_QI_FLAT_MOD, qiFlat, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.ARMOR_PENETRATION), AFFIX_ARMOR_PEN_MOD, armorPen, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.LIFESTEAL), AFFIX_LIFESTEAL_MOD, lifesteal, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.BATTLE_QI_REGEN), AFFIX_QI_REGEN_MOD, qiRegen, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.BATTLE_QI_SKILL_POWER), AFFIX_QI_SKILL_POWER_MOD, qiSkillPower, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.COMBO_DAMAGE), AFFIX_COMBO_MOD, comboDamage, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.EXECUTE_DAMAGE), AFFIX_EXECUTE_MOD, executeDamage, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.MIKIRI_DAMAGE), AFFIX_MIKIRI_MOD, mikiriDamage, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.PARRY_WINDOW), AFFIX_PARRY_WINDOW_MOD, parryWindow, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.ARROW_DAMAGE_FLAT), AFFIX_ARROW_FLAT_MOD, arrowFlat, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.ARROW_DAMAGE_PERCENT), AFFIX_ARROW_PERCENT_MOD, arrowPercent, AttributeModifier.Operation.ADD_VALUE);
        // 弹反 / 戳刺：同样写成真实属性，外部属性面板与检测模组都读得到。
        addModifier(player.getAttribute(SpellAttributes.PARRY_REFLECT), AFFIX_PARRY_REFLECT_MOD, parryReflect, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(SpellAttributes.TRIDENT_THRUST), AFFIX_TRIDENT_THRUST_MOD, tridentThrust, AttributeModifier.Operation.ADD_VALUE);
        // 护甲值与盔甲韧性直接写进原版属性：原版面板、其它模组都直接读得到，护甲减伤也真的按它算。
        addModifier(player.getAttribute(Attributes.ARMOR), ARMOR_VALUE_MOD, armorValue, AttributeModifier.Operation.ADD_VALUE);
        addModifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), ARMOR_TOUGHNESS_MOD, armorToughness, AttributeModifier.Operation.ADD_VALUE);
        for (int i = 0; i < ELEMENT_IDS.length; i++) {
            addModifier(player.getAttribute(SpellAttributes.elementDamage(ELEMENT_IDS[i])),
                    affixElementModifier(ELEMENT_IDS[i]), elementValues[i], AttributeModifier.Operation.ADD_VALUE);
            addModifier(player.getAttribute(SpellAttributes.elementAddition(ELEMENT_IDS[i])),
                    affixElementAdditionModifier(ELEMENT_IDS[i]), elementAdditions[i], AttributeModifier.Operation.ADD_VALUE);
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        ElementMastery.invalidate(player);
        if (player instanceof ServerPlayer serverPlayer) {
            ManaData.Snapshot mana = ManaData.get(serverPlayer);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new ManaSyncPayload(mana.mana(), mana.maxMana(), mana.battleQi(), mana.maxBattleQi(),
                            mana.exhaustionTicks() > 0, ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
            ProgressSyncPayload.send(serverPlayer);
        }
    }

    private static void clearModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                      ResourceLocation id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    private static void addModifier(AttributeInstance instance, ResourceLocation id, double value, AttributeModifier.Operation operation) {
        if (instance != null && Math.abs(value) > 0.00001) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, value, operation));
        }
    }

    private static List<ItemStack> relevantEquipment(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : player.getArmorSlots()) stacks.add(stack);
        stacks.add(player.getMainHandItem());
        stacks.add(player.getOffhandItem());
        return stacks;
    }

    private static boolean isBoss(LivingEntity entity) {
        String name = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return entity instanceof EnderDragon || entity instanceof WitherBoss || name.contains("swordmaster");
    }

    public static boolean isBossAffix(ItemStack stack) {
        CompoundTag root = affixRoot(stack);
        return root != null && root.getBoolean("boss_drop");
    }

    private static Grade gradeFor(LivingEntity entity) {
        String name = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("corruptedwither")) return Grade.values()[2 + entity.getRandom().nextInt(4)];
        if (entity instanceof EnderDragon) return Grade.values()[3 + entity.getRandom().nextInt(3)];
        if (entity instanceof WitherBoss) return weighted(entity.getRandom(), Grade.B, Grade.S, .55);
        if (name.contains("swordmaster")) return weighted(entity.getRandom(), Grade.A, Grade.SS, .60);
        if (entity.level().dimension() == Level.END) return weighted(entity.getRandom(), Grade.A, Grade.SSS, .50);
        if (entity.level().dimension() == Level.NETHER) return weighted(entity.getRandom(), Grade.C, Grade.A, .70);
        return weighted(entity.getRandom(), Grade.C, Grade.B, .90);
    }

    private static Grade weighted(net.minecraft.util.RandomSource random, Grade low, Grade high, double lowChance) {
        return random.nextDouble() < lowChance ? low : high;
    }

    private static School forcedSchool(LivingEntity entity) {
        String name = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("swordmaster") || entity instanceof WitherBoss) return School.PHYSICAL;
        if (name.contains("ancientmagic") || name.contains("magicguard")) return School.MAGIC;
        return null;
    }

    /**
     * 装备的流派偏好，按装备类别掷概率：
     *
     * <table border="1">
     *   <caption>流派概率</caption>
     *   <tr><th>类别</th><th>物理</th><th>魔法</th></tr>
     *   <tr><td>原版武器（剑、斧、弓、弩）</td><td>80%</td><td>20%（罕见的「魔剑士」极品）</td></tr>
     *   <tr><td>盾牌、三叉戟</td><td>100%</td><td>—（专属词条弹反 / 戳刺都在物理池里）</td></tr>
     *   <tr><td>原版防具（头盔、胸甲、护腿、靴子）</td><td>60%</td><td>40%</td></tr>
     *   <tr><td>原版工具（镐、锹、锄）</td><td>100%</td><td>—</td></tr>
     *   <tr><td>模组法杖、布甲系防具</td><td>—</td><td>100%</td></tr>
     *   <tr><td>模组魔精钢装备</td><td>50%</td><td>50%</td></tr>
     * </table>
     *
     * <p>布甲以前是看物品名里有没有「布」字，英文环境下名字是 “Rough Cloth Chestplate”，
     * 判定就翻了车（同一件装备换语言就换流派）。现在优先按注册 id 判定，名字只留作兜底。</p>
     */
    private static School schoolFor(ItemStack stack, net.minecraft.util.RandomSource random) {
        Item item = stack.getItem();
        if (item instanceof MagicStaffItem) return School.MAGIC;
        // 盾牌与三叉戟固定物理流派：弹反 / 戳刺都挂在物理词条池里，
        // 不固定的话「洗了十次都没见过专属词条」——池子里本来就没有。
        if (item instanceof ShieldItem || item instanceof TridentItem) return School.PHYSICAL;
        ResourceLocation registryId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        boolean modded = registryId != null && registryId.getNamespace().equals(GeneratedMod.MOD_ID);
        String path = registryId == null ? "" : registryId.getPath();
        if (modded && (path.startsWith("rough_cloth_") || path.startsWith("magic_cloth_") || path.endsWith("_robe"))) {
            return School.MAGIC;
        }
        // 魔精钢装备：物理、魔法各一半（工具也不例外，它本身就是魔法与锻造的产物）。
        if (modded && path.startsWith("mojing_steel_")) {
            return random.nextDouble() < 0.50 ? School.PHYSICAL : School.MAGIC;
        }
        // 兜底：旧存档里用「布」字命名的布甲仍然固定魔法。
        if (item instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.CHESTPLATE
                && stack.getHoverName().getString().contains("布")) return School.MAGIC;
        // 防具 60/40。
        if (item instanceof ArmorItem) {
            return random.nextDouble() < 0.60 ? School.PHYSICAL : School.MAGIC;
        }
        // 镐、锹、锄这类纯工具：固定物理。
        if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof HoeItem) {
            return School.PHYSICAL;
        }
        // 剑、斧、三叉戟、弓、弩与其余武器：80/20。
        return random.nextDouble() < 0.80 ? School.PHYSICAL : School.MAGIC;
    }

    public static boolean isEquipment(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof ArmorItem || item instanceof TieredItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof MagicStaffItem
                // 盾牌与三叉戟也能刷词条：弹反只出在盾牌上，戳刺只出在三叉戟上。
                || item instanceof ShieldItem || item instanceof TridentItem;
    }

    /**
     * 物理加伤词条同样吃弓、弩与投掷三叉戟：原版弹射物伤害只按弹体自己的初速结算，
     * 不读射手的攻击伤害属性，所以穿戴与主手装备上的「物理伤害」对射击一直是空的。
     * 这里在命中入伤时按近战同一公式补一次：(射击伤害 + 固定值) × (1 + 百分比)。
     *
     * <p>只取词条自己那两个属性修正（{@link #ATTACK_DAMAGE_MOD} / {@link #ATTACK_DAMAGE_PERCENT_MOD}），
     * 不读属性总值，避免把武器底伤重复算进弹道。</p>
     */
    public static void onRangedIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0.0F) return;
        net.minecraft.world.damagesource.DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow)
                && !(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.ThrownTrident)) return;
        if (!(source.getEntity() instanceof Player shooter) || shooter == event.getEntity()) return;
        AttributeInstance attack = shooter.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;
        AttributeModifier flat = attack.getModifier(ATTACK_DAMAGE_MOD);
        AttributeModifier percent = attack.getModifier(ATTACK_DAMAGE_PERCENT_MOD);
        double bonusFlat = flat == null ? 0.0 : flat.amount();
        double bonusPercent = percent == null ? 0.0 : percent.amount();
        // 弓弩专属词条：只在箭矢上生效，单独一个乘区（和通用物理加伤相乘）。
        boolean arrow = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow;
        double arrowFlat = arrow ? arrowDamageFlat(shooter) : 0.0;
        double arrowPercent = arrow ? arrowDamagePercent(shooter) / 100.0 : 0.0;
        // 弓弩的评级基础面板修正：弓没有攻击伤害属性，只能按「命中时的箭矢伤害 × 倍率」结算。
        double bowScale = arrow ? heldBowGradeScale(shooter) : 1.0D;
        // 三叉戟专属词条「戳刺」：投掷命中时单独一个乘区。激流冲刺的撞击不是弹射物，
        // 由 ShieldTridentEffects#onIncomingDamage 单独结算。
        // 投出去的三叉戟已经不在手上，所以除了装备汇总值，还要读它自己那一份词条数据。
        double thrust = 0.0;
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.ThrownTrident thrown) {
            thrust = (tridentThrustPercent(shooter) + tridentThrustPercentOf(thrown.getWeaponItem())) / 100.0;
        }
        if (bonusFlat <= 0.0 && bonusPercent <= 0.0 && arrowFlat <= 0.0 && arrowPercent <= 0.0
                && bowScale == 1.0D && thrust <= 0.0) return;
        event.setAmount((float) ((event.getAmount() + bonusFlat + arrowFlat)
                * (1.0 + bonusPercent) * (1.0 + arrowPercent) * bowScale * (1.0 + thrust)));
    }

    /**
     * 射手手里那张弓 / 弩的评级基础面板倍率；没拿弓弩或弓弩没有词条时返回 1。
     *
     * <p>主手优先——蓄力射箭时弓一定在手里；副手再兜一层，兼容把弓放副手的情况。</p>
     */
    private static double heldBowGradeScale(Player shooter) {
        for (ItemStack held : List.of(shooter.getMainHandItem(), shooter.getOffhandItem())) {
            if (!isBowLike(held)) continue;
            CompoundTag root = affixRoot(held);
            if (root == null) continue;
            return Grade.parse(root.getString(GRADE)).panelScale;
        }
        return 1.0D;
    }

    public static boolean hasAffix(ItemStack stack) { return affixRoot(stack) != null; }

    private static CompoundTag affixRoot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(ROOT)) return null;
        return data.copyTag().getCompound(ROOT);
    }

    private static Affix findAffix(String id) {
        for (Affix affix : AFFIXES) if (affix.id().equals(id)) return affix;
        return null;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, path);
    }

    private EquipmentAffixSystem() { }
}
