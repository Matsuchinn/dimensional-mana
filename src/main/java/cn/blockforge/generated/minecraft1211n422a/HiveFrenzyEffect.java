package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 【狂化】：魔蚀织网者发动「虫群意识」时，被呼叫过来的节肢动物身上的 10 秒狂暴标记。
 *
 * <p>效果本身只负责两个数值（属性修饰符会在效果到期 / 死亡 / 被清除时自动撤回）：</p>
 * <ul>
 *   <li><b>移动速度 +50%</b> —— 直接乘在 {@link Attributes#MOVEMENT_SPEED} 上；</li>
 *   <li><b>受到伤害 −25%</b> —— 1.21 没有「通用减伤」属性，实际倍率写在
 *       {@link HiveMind#onIncomingDamage} 里，乘 0.75。</li>
 * </ul>
 *
 * <p>另外顺手把 {@link Attributes#FOLLOW_RANGE} 拉到 3 倍：被呼叫的蜘蛛经常在 30～60 格开外，
 * 原版蜘蛛只有 16 格寻敌距离，不加这一条它们会在半路把目标丢掉、站在远处发呆，
 * 玩家看到的就是「叫了但没来」。</p>
 *
 * <p>图标是 {@code textures/mob_effect/hive_frenzy.png}：一枚暗红色的虫颚符文。</p>
 */
public final class HiveFrenzyEffect extends MobEffect {
    /** 效果颜色：暗红（召唤时身上冒的就是这个颜色的粒子）。 */
    public static final int COLOR = 0x8E1B12;
    /** 移动速度加成：+50%。 */
    public static final double SPEED_BONUS = 0.50D;
    /** 寻敌距离倍数：在原有基础上再 +200%（合计 3 倍）。 */
    public static final double FOLLOW_RANGE_BONUS = 2.0D;
    /** 受到伤害的倍率：−25%。 */
    public static final float DAMAGE_TAKEN_MULTIPLIER = 0.75F;

    public HiveFrenzyEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, id("hive_frenzy_speed"), SPEED_BONUS,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.FOLLOW_RANGE, id("hive_frenzy_follow_range"), FOLLOW_RANGE_BONUS,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, path);
    }
}
