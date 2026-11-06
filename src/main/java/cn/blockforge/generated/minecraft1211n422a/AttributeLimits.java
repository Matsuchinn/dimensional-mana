package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.lang.reflect.Field;

/**
 * 模组内置的「属性上限修复」。
 *
 * <p>原版每一条属性的<b>最大值</b>都写死在 {@link RangedAttribute} 里（最大生命 1024、
 * 攻击力 2048、护甲 30……），{@code AttributeInstance#getValue} 结算完会拿这个上限再夹一次。
 * 所以哪怕把魔蚀凋零的血量写成 5120，读出来也只有 1024。</p>
 *
 * <p>官方没有给「改上限」的事件，这里用反射把每个 {@link RangedAttribute} 实例的
 * {@code maxValue} 抬到 2147483647（和常见属性修复模组同一量级）。不需要玩家额外装属性修复模组；
 * 装了也不冲突——两边都只是把同一个上限往高改。</p>
 *
 * <p>只在初始化阶段调用（模组构造 + 实体属性注册前各一次），运行期零开销。</p>
 */
public final class AttributeLimits {

    /** 新的属性上限：2147483647，比任何玩法数值都高，又不会让 float 血量溢出。 */
    public static final double MAX_VALUE = (double) Integer.MAX_VALUE;

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("dimensional_mana/attribute_limits");
    private static boolean reported;

    /**
     * 把所有会夹数值的属性上限抬到 {@link #MAX_VALUE}。
     *
     * <p>可以重复调用：第一次在模组构造里跑一遍（原版属性这时候已经就位），
     * 注册完成后再跑一遍（把本模组自己注册的属性也一起接上）。</p>
     */
    public static void apply() {
        int raised = 0;
        int failed = 0;
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            if (!(attribute instanceof RangedAttribute ranged)) continue;
            if (raiseMax(ranged)) raised++;
            else failed++;
        }
        if (failed > 0 && !reported) {
            reported = true;
            LOGGER.warn("属性上限修复：{} 条属性抬不动（反射被拦截），这些属性仍按原版上限夹取", failed);
        }
        if (raised > 0 && !reported) {
            reported = true;
            LOGGER.info("属性上限修复：{} 条属性的最大值已抬到 {}", raised, (long) MAX_VALUE);
        }
    }

    /** 单条属性：把字段 {@code maxValue} 改掉；字段名对不上就按「类型 + 当前值」找一次兜底。 */
    private static boolean raiseMax(RangedAttribute attribute) {
        Field field = findMaxField(attribute);
        if (field == null) return false;
        try {
            field.setAccessible(true);
            field.setDouble(attribute, MAX_VALUE);
            return attribute.getMaxValue() >= MAX_VALUE - 1.0D;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findMaxField(RangedAttribute attribute) {
        try {
            return RangedAttribute.class.getDeclaredField("maxValue");
        } catch (NoSuchFieldException ignored) {
            // 映射名变了（或换了加载器）：退一步，找「double 类型、当前值等于 getMaxValue()」的字段。
        }
        double current = attribute.getMaxValue();
        Field fallback = null;
        for (Field field : RangedAttribute.class.getDeclaredFields()) {
            if (field.getType() != double.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                if (Double.compare(field.getDouble(attribute), current) == 0) {
                    if (fallback != null) return null; // 两个都像，宁可不改，也不猜错字段
                    fallback = field;
                }
            } catch (Throwable ignored) {
                // 读不到的字段直接跳过。
            }
        }
        return fallback;
    }

    private AttributeLimits() {}
}
