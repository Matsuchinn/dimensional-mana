package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Rarity;

import java.util.List;

/**
 * 二十三件新饰品的定义表：栏位、梯度、配色、属性修正、词条行。
 *
 * <p>梯度决定稀有度与名字样式：t0 最高（金色、加粗、带附魔光），t1 传说（紫色加粗），
 * t2 稀有（青色），t3 精良（绿色）。同一张底图靠染色层区分颜色，
 * 所以每件饰品只需要一条染色值和一张共用底图。</p>
 *
 * <p>数值与触发逻辑不在这里，集中在 {@link RelicEffects}；这里只描述「长什么样、写什么」。</p>
 */
public final class Relics {
    /** 饰品挂在哪个 Curios 栏位。BROOCH 复用 Curios 原有的 body（中文「胸饰」）栏位。 */
    public enum Slot {
        RING("ring"), CHARM("charm"), NECKLACE("necklace"), BRACELET("bracelet"),
        BROOCH("body"), BELT("belt"), HEAD("head");

        private final String id;
        Slot(String id) { this.id = id; }
        public String id() { return id; }
    }

    /** 梯度：稀有度、名字颜色、是否加粗、是否有附魔光。 */
    public enum Tier {
        T0(Rarity.EPIC, ChatFormatting.GOLD, true, true),
        T1(Rarity.EPIC, ChatFormatting.LIGHT_PURPLE, true, false),
        T2(Rarity.RARE, ChatFormatting.AQUA, false, false),
        T3(Rarity.UNCOMMON, ChatFormatting.GREEN, false, false);

        private final Rarity rarity;
        private final ChatFormatting color;
        private final boolean bold;
        private final boolean glint;

        Tier(Rarity rarity, ChatFormatting color, boolean bold, boolean glint) {
            this.rarity = rarity;
            this.color = color;
            this.bold = bold;
            this.glint = glint;
        }

        public Rarity rarity() { return rarity; }
        public ChatFormatting color() { return color; }
        public boolean bold() { return bold; }
        public boolean glint() { return glint; }
        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
        public String translationKey() { return "tooltip.dimensional_mana.relic.tier." + id(); }
    }

    /** 一件饰品的静态定义。 */
    public record Relic(String id, Slot slot, Tier tier, int tint, List<AttributeSpec> attributes) {
        public String nameKey() { return "item.dimensional_mana." + id; }
        public String lineKey(int index) { return "tooltip.dimensional_mana.relic." + id + "." + index; }
        public String obtainKey() { return "tooltip.dimensional_mana.relic." + id + ".obtain"; }
    }

    /** 装备时由 Curios 属性修正直接生效的加成。 */
    public record AttributeSpec(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) { }

    private static final AttributeSpec SPEED_25 = new AttributeSpec(Attributes.MOVEMENT_SPEED, 0.25,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeSpec HEALTH_MINUS_25 = new AttributeSpec(Attributes.MAX_HEALTH, -0.25,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeSpec HEALTH_MINUS_35 = new AttributeSpec(Attributes.MAX_HEALTH, -0.35,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeSpec SPELL_CRIT_35 = new AttributeSpec(SpellAttributes.SPELL_CRIT_CHANCE, 0.35,
            AttributeModifier.Operation.ADD_VALUE);

    /** 注册顺序就是创造页里的显示顺序：t3 → t2 → t1 → t0。 */
    public static final List<Relic> ALL = List.of(
            // ── t3 ────────────────────────────────────────────────────────────
            new Relic("green_amber_ring", Slot.RING, Tier.T3, 0xFF3FBF7F, List.of(SPEED_25)),
            new Relic("smuggler_ring", Slot.RING, Tier.T3, 0xFFC8A24A, List.of()),
            new Relic("venom_ring", Slot.RING, Tier.T3, 0xFF9ACD45, List.of()),
            new Relic("strength_charm", Slot.CHARM, Tier.T3, 0xFFD8D8D8, List.of()),
            new Relic("moonlight_blessing", Slot.RING, Tier.T3, 0xFFA8ECFF, List.of()),

            // ── t2 ────────────────────────────────────────────────────────────
            new Relic("blazing_blessing", Slot.RING, Tier.T2, 0xFFFFC24A, List.of()),
            new Relic("risk_bracelet", Slot.BRACELET, Tier.T2, 0xFFE8B23A, List.of()),
            new Relic("choice_ring", Slot.RING, Tier.T2, 0xFFB366E8, List.of()),
            new Relic("nameless_charm", Slot.CHARM, Tier.T2, 0xFF8E8E8E, List.of()),
            new Relic("lost_charm", Slot.CHARM, Tier.T2, 0xFF5E6E8E, List.of()),
            new Relic("bloodthirst_brooch", Slot.BROOCH, Tier.T2, 0xFFB01818, List.of(HEALTH_MINUS_25)),
            new Relic("serene_bracelet", Slot.BRACELET, Tier.T2, 0xFF6FD6C8, List.of()),

            // ── t1 ────────────────────────────────────────────────────────────
            new Relic("seraphis_charm", Slot.NECKLACE, Tier.T1, 0xFFF0E6C8, List.of()),
            new Relic("cosetas_charm", Slot.NECKLACE, Tier.T1, 0xFF7FE0B0, List.of()),
            new Relic("aurora_charm", Slot.CHARM, Tier.T1, 0xFFE8C8FF, List.of(HEALTH_MINUS_35)),
            new Relic("seal_blindfold", Slot.HEAD, Tier.T1, 0xFF3A2A5A, List.of(SPELL_CRIT_35)),
            new Relic("mage_delight", Slot.RING, Tier.T1, 0xFFC85BFF, List.of()),
            new Relic("element_absorption_charm", Slot.NECKLACE, Tier.T1, 0xFF86C8FF, List.of()),
            new Relic("detective_charm", Slot.CHARM, Tier.T1, 0xFFB08A55, List.of()),

            // ── t0 ────────────────────────────────────────────────────────────
            new Relic("universal_belt", Slot.BELT, Tier.T0, 0xFFE8E8E8, List.of()),
            new Relic("scarlet_spirit_charm", Slot.CHARM, Tier.T0, 0xFFE03030, List.of()),
            new Relic("angel_crown", Slot.HEAD, Tier.T0, 0xFFFFE88A, List.of()),
            new Relic("omniscient_shard", Slot.CHARM, Tier.T0, 0xFF9FE8FF, List.of()));

    private Relics() { }
}
