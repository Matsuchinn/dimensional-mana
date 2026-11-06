package cn.blockforge.generated.minecraft1211n422a;

import java.util.Map;

/**
 * 物品的“亮度”等级（0～15 的亮度刻度，和方块光照等级同一套单位）。
 *
 * <p>物品在原版里没有真正的光照等级：亮度只有方块才有，物品拿在手里、掉在地上都不会照明。
 * 所以这里的数字代表这件物品图标的<b>自发光强度</b>：物品栏里的光环多亮、贴图里的光晕多强、
 * 动起来有多醒目，都按这个数值缩放。想让物品真的照亮周围，得给它做方块形态。</p>
 */
public final class ItemGlow {
    /** 超导魔晶粉末。 */
    public static final int SUPERCONDUCTING_DUST = 8;
    /** 超导魔晶。 */
    public static final int SUPERCONDUCTING_CRYSTAL = 10;
    /** 魔力通透·共鸣核心。 */
    public static final int RESONANCE_CORE = 12;
    /** 特级魔精钢。 */
    public static final int SPECIAL_MAGIC_STEEL = 8;
    /** 畸变下界之心：黑紫色腐化光环。 */
    public static final int DISTORTED_NETHER_HEART = 15;
    /** 武备强化石：暖金色的锻造辉光。 */
    public static final int ARMAMENT_STONE = 7;

    private static final Map<String, Integer> LEVELS = Map.of(
            "superconducting_crystal_dust", SUPERCONDUCTING_DUST,
            "superconducting_crystal", SUPERCONDUCTING_CRYSTAL,
            ResonanceCore.ITEM_ID, RESONANCE_CORE,
            "special_magic_steel", SPECIAL_MAGIC_STEEL,
            GeneratedMod.DISTORTED_NETHER_STAR_ID, DISTORTED_NETHER_HEART,
            ArmamentStoneLoot.ITEM_ID, ARMAMENT_STONE);

    /** 物品 id 的亮度；没有设定的物品返回 0（不发光）。 */
    public static int of(String itemId) {
        return itemId == null ? 0 : LEVELS.getOrDefault(itemId, 0);
    }

    private ItemGlow() { }
}
