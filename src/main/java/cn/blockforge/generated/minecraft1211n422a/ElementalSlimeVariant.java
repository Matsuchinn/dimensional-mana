package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** 十二种元素史莱姆的唯一数据源：元素、初级法术、图鉴颜色与基础法术伤害都从这里读取。 */
public enum ElementalSlimeVariant {
    MANA("mana", null, "arcane", 16.0F, SpellDefinition.ARCANE_RAY, 0xB96CFF, Items.AMETHYST_SHARD),
    FIRE("fire", ElementType.FIRE, "fire", 8.0F, SpellDefinition.FIREBALL, 0xFF5A2E, Items.BLAZE_POWDER),
    WATER("water", ElementType.WATER, "water", 6.0F, SpellDefinition.WATER_BOLT, 0x3C9DFF, Items.WATER_BUCKET),
    ICE("ice", ElementType.ICE, "ice", 7.0F, SpellDefinition.ICE_SPIKE, 0xB9F3FF, Items.PACKED_ICE),
    LIGHTNING("lightning", ElementType.LIGHTNING, "lightning", 10.0F, SpellDefinition.ELECTRIC_SHOCK, 0xFFE35A, Items.LIGHTNING_ROD),
    NATURE("nature", ElementType.NATURE, "nature", 6.0F, SpellDefinition.THORNS, 0x58BE4B, Items.OAK_SAPLING),
    POISON("poison", ElementType.POISON, "poison", 4.0F, SpellDefinition.POISON_BOLT, 0x8FCB3F, Items.SPIDER_EYE),
    METAL("metal", ElementType.METAL, "metal", 9.0F, SpellDefinition.SHARP_EDGE, 0xC9D0D8, Items.IRON_INGOT),
    WIND("wind", ElementType.WIND, "wind", 5.0F, SpellDefinition.WIND_BLADE, 0xB9FFE2, Items.WIND_CHARGE),
    EARTH("earth", ElementType.EARTH, "earth", 12.0F, SpellDefinition.ROCKFALL, 0x9A704C, Items.DRIPSTONE_BLOCK),
    LIGHT("light", ElementType.LIGHT, "light", 7.0F, SpellDefinition.LIGHT_BOLT, 0xFFF4A8, Items.GLOWSTONE_DUST),
    SOUND("sound", ElementType.SOUND, "sound", 6.0F, SpellDefinition.SONIC_WAVE, 0xE89BFF, Items.NOTE_BLOCK);

    private final String id;
    private final ElementType element;
    private final String damageElementId;
    private final float basicSpellDamage;
    private final SpellDefinition basicSpell;
    private final int color;
    private final Item icon;

    ElementalSlimeVariant(String id, ElementType element, String damageElementId, float basicSpellDamage,
                          SpellDefinition basicSpell, int color, Item icon) {
        this.id = id;
        this.element = element;
        this.damageElementId = damageElementId;
        this.basicSpellDamage = basicSpellDamage;
        this.basicSpell = basicSpell;
        this.color = color;
        this.icon = icon;
    }

    public String id() { return id; }
    public ElementType element() { return element; }
    public String damageElementId() { return damageElementId; }
    public float basicSpellDamage() { return basicSpellDamage; }
    public SpellDefinition basicSpell() { return basicSpell; }
    public int color() { return color; }
    public Item icon() { return icon; }
    public String entityId() { return id + "_elemental_slime"; }
    public String spawnEggId() { return entityId() + "_spawn_egg"; }
    public String powderId() { return id + "_element_crystal_dust"; }
    public String crystalId() { return MagicEquipment.crystalId(damageElementId); }
    public String lesserFragmentId() {
        return this == MANA ? "lesser_arcane_fragment" : element.fragmentId("lesser");
    }
    public String mediumFragmentId() {
        return this == MANA ? "medium_arcane_fragment" : element.fragmentId("medium");
    }
    public String advancedFragmentId() {
        return this == MANA ? "advanced_arcane_fragment" : element.fragmentId("advanced");
    }
    public String translationKey() { return "entity.dimensional_mana." + entityId(); }

    public static ElementalSlimeVariant byId(String id) {
        if (id != null) for (ElementalSlimeVariant value : values()) if (value.id.equals(id)) return value;
        return MANA;
    }
}
