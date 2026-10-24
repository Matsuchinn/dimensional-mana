package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;

/**
 * 哥布林阵营的五种成员。
 *
 * <p>五种哥布林共用同一套实体代码（{@link Goblin}），差异全部写在这张表里：
 * 生命区间、斗气 / 魔素池、背包格数、体型、攻防与外观贴图。
 * 这样巡逻队和聚落只要按比例挑变种即可，不用维护五份重复的 AI。</p>
 */
public enum GoblinVariant {
    /** 幼崽：3 格背包，穿布甲，见势不妙就尖叫逃跑。 */
    YOUNG("young", 16, 24, 100, 0, 0.0, 3, 0.55F, 0.33F, 0.99F, 2.0, 0.30,
            GoblinShape.YOUNG, "textures/entity/goblin_young.png"),
    /** 成年哥布林：6 格背包，30% 全套粗铁甲、50% 副手盾，主手粗铁砍刀。 */
    WARRIOR("warrior", 50, 90, 800, 0, 4.0, 6, 0.85F, 0.51F, 1.53F, 5.0, 0.27,
            GoblinShape.ADULT, "textures/entity/goblin_warrior.png"),
    /** 弓箭手：布甲 + 破损弓，玩家贴身后消耗斗气后撤。 */
    ARCHER("archer", 50, 90, 800, 0, 0.0, 6, 0.85F, 0.51F, 1.53F, 3.0, 0.27,
            GoblinShape.ADULT, "textures/entity/goblin_archer.png"),
    /** 萨满：魔素 1800，毒弹 / 风弹、治疗与加速友军，濒死自爆火环。 */
    SHAMAN("shaman", 40, 70, 0, 1800, 5.0, 6, 0.85F, 0.51F, 1.53F, 3.0, 0.25,
            GoblinShape.ADULT, "textures/entity/goblin_shaman.png"),
    /** 首领：1.5 倍体型、12 格背包、贪婪 / 战吼 / 跳劈 / 蓄力斩击。 */
    CHIEF("chief", 300, 500, 1600, 0, 10.0, 12, 1.28F, 0.77F, 2.30F, 12.0, 0.26,
            GoblinShape.CHIEF, "textures/entity/goblin_chief.png");

    private final String id;
    private final int minHealth;
    private final int maxHealth;
    private final int battleQi;
    private final int mana;
    private final double regenPerSecond;
    private final int capacity;
    private final float scale;
    private final float width;
    private final float height;
    private final double attackDamage;
    private final double moveSpeed;
    private final GoblinShape shape;
    private final String texturePath;

    GoblinVariant(String id, int minHealth, int maxHealth, int battleQi, int mana, double regenPerSecond,
                  int capacity, float scale, float width, float height, double attackDamage, double moveSpeed,
                  GoblinShape shape, String texturePath) {
        this.id = id;
        this.minHealth = minHealth;
        this.maxHealth = maxHealth;
        this.battleQi = battleQi;
        this.mana = mana;
        this.regenPerSecond = regenPerSecond;
        this.capacity = capacity;
        this.scale = scale;
        this.width = width;
        this.height = height;
        this.attackDamage = attackDamage;
        this.moveSpeed = moveSpeed;
        this.shape = shape;
        this.texturePath = texturePath;
    }

    public String id() { return id; }
    public int minHealth() { return minHealth; }
    public int maxHealth() { return maxHealth; }
    public int battleQi() { return battleQi; }
    public int mana() { return mana; }
    public double regenPerSecond() { return regenPerSecond; }
    public int capacity() { return capacity; }
    public float scale() { return scale; }
    public float width() { return width; }
    public float height() { return height; }
    public double attackDamage() { return attackDamage; }
    public double moveSpeed() { return moveSpeed; }
    public GoblinShape shape() { return shape; }

    public String entityId() { return "goblin_" + id; }
    public String spawnEggId() { return entityId() + "_spawn_egg"; }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, texturePath);
    }

    /** 会不会自己主动打架；幼崽不会，只会跑。 */
    public boolean fights() { return this != YOUNG; }

    public static GoblinVariant byId(String id) {
        for (GoblinVariant variant : values()) if (variant.id.equals(id)) return variant;
        return WARRIOR;
    }
}
