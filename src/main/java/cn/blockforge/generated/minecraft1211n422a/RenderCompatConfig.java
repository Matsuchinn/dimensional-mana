package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 光影包兼容设置（纯客户端，只影响「怎么看」，不影响任何游戏逻辑）。
 *
 * <p>原版管线里，法阵的辉光靠<b>加色混合</b>（画上去的颜色叠在场景上、暗处等于透明）
 * 实现；开了 Iris / Oculus / OptiFine 的光影包后，这些绘制会落进光影自己的分层缓冲里，
 * 混合方式被光影接管，加色就退化成一团不透明的「盘子」。
 * {@link ShaderCompat} 会在检测到光影时自动改用普通半透明混合，这个开关用来覆盖自动判断。</p>
 */
public final class RenderCompatConfig {
    public static final ModConfigSpec SPEC;

    /** 法阵光影兼容模式：0=自动，1=强制兼容，2=强制原版加色辉光。 */
    public static final ModConfigSpec.IntValue MAGIC_CIRCLE_MODE;
    /** 法术光效（符阵贴图、岩石/冰/烟雾等）是否也改用半透明缓冲。 */
    public static final ModConfigSpec.BooleanValue SPELL_EFFECTS_COMPAT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("光影包（Iris / Oculus / OptiFine Shaders）兼容设置，只影响客户端画面。")
                .push("shader_compat");
        MAGIC_CIRCLE_MODE = b.comment("法阵在世界里的渲染模式：",
                        "  0 = 自动：检测到光影包（或开了「华丽」图形）就切到兼容模式，关掉光影立刻还原；",
                        "  1 = 强制兼容：一律用普通半透明混合，伪辉光垫片收成一层淡光晕；",
                        "  2 = 强制原版：一律用加色辉光（画面最艳，但开光影时会看到不透明盘子）。")
                .translation("config.dimensional_mana.shader_compat.magic_circle_mode")
                .defineInRange("magic_circle_mode", 0, 0, 2);
        SPELL_EFFECTS_COMPAT = b.comment("法术光效（符阵、岩石、冰、烟雾、流光贴图）也改用光影友好的透明缓冲。",
                        "默认开。若某个光影包下法术光效变暗或消失，可以关掉它试对比。")
                .translation("config.dimensional_mana.shader_compat.spell_effects_compat")
                .define("spell_effects_compat", true);
        b.pop();
        SPEC = b.build();
    }

    private RenderCompatConfig() {}
}
