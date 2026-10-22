package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.util.RandomSource;

/** 幼崽 30%、成熟体 60%、精英体 10%；每档保存血量、伤害与掉落的精确区间。 */
public enum ElementalSlimeTier {
    CUB("cub", 30, 8, 16, 0.50F, 0.40F, 1, 1, 0.0F, 0, 0, 0.0F),
    MATURE("mature", 60, 25, 50, 1.00F, 0.60F, 1, 2, 0.15F, 1, 1, 0.0F),
    ELITE("elite", 10, 60, 100, 1.50F, 0.80F, 2, 4, 0.35F, 1, 3, 0.05F);

    private final String id;
    private final int weight;
    private final int minHealth;
    private final int maxHealth;
    private final float damageScale;
    private final float powderChance;
    private final int minPowder;
    private final int maxPowder;
    private final float lesserChance;
    private final int minLesser;
    private final int maxLesser;
    private final float mediumChance;

    ElementalSlimeTier(String id, int weight, int minHealth, int maxHealth, float damageScale,
                       float powderChance, int minPowder, int maxPowder, float lesserChance,
                       int minLesser, int maxLesser, float mediumChance) {
        this.id = id;
        this.weight = weight;
        this.minHealth = minHealth;
        this.maxHealth = maxHealth;
        this.damageScale = damageScale;
        this.powderChance = powderChance;
        this.minPowder = minPowder;
        this.maxPowder = maxPowder;
        this.lesserChance = lesserChance;
        this.minLesser = minLesser;
        this.maxLesser = maxLesser;
        this.mediumChance = mediumChance;
    }

    public String id() { return id; }
    public int weight() { return weight; }
    public int minHealth() { return minHealth; }
    public int maxHealth() { return maxHealth; }
    public float damageScale() { return damageScale; }
    public float powderChance() { return powderChance; }
    public int minPowder() { return minPowder; }
    public int maxPowder() { return maxPowder; }
    public float lesserChance() { return lesserChance; }
    public int minLesser() { return minLesser; }
    public int maxLesser() { return maxLesser; }
    public float mediumChance() { return mediumChance; }
    public int randomHealth(RandomSource random) { return minHealth + random.nextInt(maxHealth - minHealth + 1); }
    public int randomPowder(RandomSource random) { return minPowder + random.nextInt(maxPowder - minPowder + 1); }
    public int randomLesser(RandomSource random) { return minLesser + random.nextInt(maxLesser - minLesser + 1); }

    public static ElementalSlimeTier roll(RandomSource random) {
        int roll = random.nextInt(100);
        return roll < CUB.weight ? CUB : roll < CUB.weight + MATURE.weight ? MATURE : ELITE;
    }

    public static ElementalSlimeTier byId(String id) {
        if (id != null) for (ElementalSlimeTier value : values()) if (value.id.equals(id)) return value;
        return MATURE;
    }
}
