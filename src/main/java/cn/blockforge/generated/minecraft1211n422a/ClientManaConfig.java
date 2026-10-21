package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientManaConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue HUD_X_FROM_LEFT;
    public static final ModConfigSpec.IntValue HUD_Y_FROM_BOTTOM;
    public static final ModConfigSpec.DoubleValue MANA_TEXT_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("魔素与斗气 HUD 设置").push("mana_hud");
        HUD_X_FROM_LEFT = builder.comment("魔素 HUD 中心距离屏幕左边的像素数")
                .translation("config.dimensional_mana.client.hud_x_from_left")
                .defineInRange("x_from_left", 35, 0, 10000);
        HUD_Y_FROM_BOTTOM = builder.comment("魔素 HUD 中心距离屏幕底边的像素数")
                .translation("config.dimensional_mana.client.hud_y_from_bottom")
                .defineInRange("y_from_bottom", 62, 0, 10000);
        MANA_TEXT_SCALE = builder.comment("魔素球内数字的缩放比例；1.0 为原版字体大小")
                .translation("config.dimensional_mana.client.mana_text_scale")
                .defineInRange("mana_text_scale", 0.65, 0.25, 2.0);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientManaConfig() {}
}
