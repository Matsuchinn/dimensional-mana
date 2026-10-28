package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * HUD 文字提示与共鸣核心位置的客户端配置（{@code config/dimensional_mana-hud.toml}）。
 *
 * <p>这里的开关全部对应屏幕上的一类文字提示，改动即时生效、只影响自己；
 * 配套 {@link HudConfigScreen} 可视化编辑器：在模组合集里点「配置」就能拖位置、
 * 逐项开关，不必手改 toml。鉴定面板的显示开关复用 {@link AppraisalConfig}，
 * 魔素球位置复用 {@link ClientManaConfig}。</p>
 */
public final class HudConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_MANA_NUMBER;
    public static final ModConfigSpec.BooleanValue SHOW_SPELL_HUD;
    public static final ModConfigSpec.BooleanValue SHOW_REACTION_TEXT;
    public static final ModConfigSpec.BooleanValue SHOW_RES_LABEL;
    public static final ModConfigSpec.BooleanValue SHOW_PARRY_STANCE_LABEL;
    public static final ModConfigSpec.IntValue RES_X_FROM_RIGHT;
    public static final ModConfigSpec.IntValue RES_Y_FROM_BOTTOM;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("HUD 文字提示开关与共鸣核心位置（只影响自己的客户端）")
                .push("hud_text");
        SHOW_MANA_NUMBER = builder.comment("魔素球中心的数字（当前值 / 上限）；关掉后球还在，只是不显示数字")
                .translation("config.dimensional_mana.hud.show_mana_number")
                .define("show_mana_number", true);
        SHOW_SPELL_HUD = builder.comment("魔素球右侧的法术信息：选中法术名、施法按键、吟唱条与各状态倒计时")
                .translation("config.dimensional_mana.hud.show_spell_hud")
                .define("show_spell_hud", true);
        SHOW_REACTION_TEXT = builder.comment("反应跳字：目标头顶弹出的「冻结」「感电」等反应名")
                .translation("config.dimensional_mana.hud.show_reaction_text")
                .define("show_reaction_text", true);
        SHOW_RES_LABEL = builder.comment("共鸣核心旁的文字：充能百分比、「共鸣就绪」与「魔力通透」")
                .translation("config.dimensional_mana.hud.show_res_label")
                .define("show_res_label", true);
        SHOW_PARRY_STANCE_LABEL = builder.comment("招架姿态提示：准心下方的金色括弧与「招架中」文字")
                .translation("config.dimensional_mana.hud.show_parry_stance_label")
                .define("show_parry_stance_label", true);
        RES_X_FROM_RIGHT = builder.comment("共鸣核心中心距屏幕右缘的像素数")
                .translation("config.dimensional_mana.hud.res_x_from_right")
                .defineInRange("res_x_from_right", 30, 0, 10000);
        RES_Y_FROM_BOTTOM = builder.comment("共鸣核心中心距屏幕底缘的像素数")
                .translation("config.dimensional_mana.hud.res_y_from_bottom")
                .defineInRange("res_y_from_bottom", 30, 0, 10000);
        builder.pop();
        SPEC = builder.build();
    }

    private HudConfig() {}
}
