package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Arrays;
import java.util.Locale;

/**
 * 本模组战斗数值使用的 12 元素池：奥术、金、木、水、火、土、冰、雷、毒、音、光、风。
 *
 * <p>{@link ElementType} 是十一种可附着元素媒介，奥术单独走 {@link MagicEquipment#ARCANE}；
 * 这个枚举把两者合成一套统一的战斗元素，供生物抗性表、技能伤害与配置字符串使用。</p>
 */
public enum CombatElement {
    ARCANE("arcane", "奥术"),
    METAL("metal", "金"),
    NATURE("nature", "木"),
    WATER("water", "水"),
    FIRE("fire", "火"),
    EARTH("earth", "土"),
    ICE("ice", "冰"),
    LIGHTNING("lightning", "雷"),
    POISON("poison", "毒"),
    SOUND("sound", "音"),
    LIGHT("light", "光"),
    WIND("wind", "风");

    private final String id;
    private final String chineseName;

    CombatElement(String id, String chineseName) {
        this.id = id;
        this.chineseName = chineseName;
    }

    public String id() {
        return id;
    }

    public String chineseName() {
        return chineseName;
    }

    /** 直接复用现成的元素译名：奥术走「高级媒介」那条，其余走十一元素。 */
    public String translationKey() {
        return this == ARCANE ? "gui.dimensional_mana.medium.arcane" : "gui.dimensional_mana.element." + id;
    }

    /** 对应可附着元素；奥术没有 ElementType，返回 null。 */
    public ElementType elementType() {
        return this == ARCANE ? null : ElementType.byId(id);
    }

    public static CombatElement byId(String id) {
        if (id == null) return null;
        String normalized = id.toLowerCase(Locale.ROOT);
        for (CombatElement element : values()) if (element.id.equals(normalized)) return element;
        return null;
    }

    /** 伤害种类：元素、物理、爆炸、其他（环境/真伤之类）。 */
    public enum Kind { ELEMENT, PHYSICAL, EXPLOSION, OTHER }

    /** 一次伤害对应的元素；不带元素归属时返回 null。 */
    public static CombatElement elementOf(DamageSource source) {
        if (source == null) return null;
        String id = SpellDamage.elementIdOf(source);
        if (id != null) return byId(id);
        // 爆炸类伤害走独立的爆炸抗性，不再重复计算火元素抗性。
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return null;
        if (source.is(DamageTypeTags.IS_FIRE)) return FIRE;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return LIGHTNING;
        return null;
    }

    /** 伤害种类；物理包含近战、箭矢与其他直接投掷物。 */
    public static Kind kindOf(DamageSource source) {
        if (source == null) return Kind.OTHER;
        if (elementOf(source) != null) return Kind.ELEMENT;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return Kind.EXPLOSION;
        if (source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity()) {
            // 箭矢、投掷物、火球：直接实体与来源实体不同的实体伤害按物理算。
            if (source.getEntity() != null || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
                return Kind.PHYSICAL;
            }
        }
        if (source.getEntity() != null) return Kind.PHYSICAL;
        return Kind.OTHER;
    }

    public static String ids() {
        return Arrays.stream(values()).map(CombatElement::id).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
