package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

/**
 * HUD 的显示开关与每个部件的位置 / 大小（{@code config/dimensional_mana-hud.toml}）。
 *
 * <p>r295 起 HUD 拆成可以<b>单独拖动、单独缩放</b>的七个部件：魔素球图标、魔素数字、
 * 法术信息块、共鸣核心图标、共鸣核心文字、鉴定面板、鉴定扫描小窗。
 * 每个部件存的是「相对默认位置的偏移 dx/dy」和「缩放比例」，所以换分辨率、
 * 换 GUI 缩放比例都不会跑位；默认值 0/1 就是原来的排版。
 * 配套 {@link HudConfigScreen} 可视化编辑器：在模组列表里点「配置」就能拖拽调整。</p>
 */
public final class HudConfig {
    public static final ModConfigSpec SPEC;

    // ------------------------------------------------------------------ 显示开关
    public static final ModConfigSpec.BooleanValue SHOW_MANA_NUMBER;
    public static final ModConfigSpec.BooleanValue SHOW_SPELL_HUD;
    public static final ModConfigSpec.BooleanValue SHOW_REACTION_TEXT;
    public static final ModConfigSpec.BooleanValue SHOW_RES_LABEL;
    public static final ModConfigSpec.BooleanValue SHOW_PARRY_STANCE_LABEL;

    // ------------------------------------------------------------------ 布局
    /** 可以单独调位置和大小的 HUD 部件。 */
    public enum Part {
        MANA_ICON("mana_icon", "hud.edit.dimensional_mana.part.mana_icon", 1.0D, 0.5D),
        MANA_TEXT("mana_text", "hud.edit.dimensional_mana.part.mana_text", 0.65D, 0.25D),
        SPELL_HUD("spell_hud", "hud.edit.dimensional_mana.part.spell_hud", 1.0D, 0.5D),
        RES_ICON("res_icon", "hud.edit.dimensional_mana.part.res_icon", 1.0D, 0.5D),
        RES_LABEL("res_label", "hud.edit.dimensional_mana.part.res_label", 1.0D, 0.5D),
        APPRAISAL("appraisal", "hud.edit.dimensional_mana.part.appraisal", 1.0D, 0.5D),
        SCANLINE("scanline", "hud.edit.dimensional_mana.part.scanline", 1.0D, 0.5D);

        final String id;
        final String nameKey;
        final double defaultScale;
        final double minScale;

        Part(String id, String nameKey, double defaultScale, double minScale) {
            this.id = id;
            this.nameKey = nameKey;
            this.defaultScale = defaultScale;
            this.minScale = minScale;
        }
    }

    public static final ModConfigSpec.IntValue[] DX = new ModConfigSpec.IntValue[Part.values().length];
    public static final ModConfigSpec.IntValue[] DY = new ModConfigSpec.IntValue[Part.values().length];
    public static final ModConfigSpec.DoubleValue[] SCALE = new ModConfigSpec.DoubleValue[Part.values().length];

    // ------------------------------------------------------------------ 默认排版
    /** 魔素球中心：距屏幕左缘 / 距屏幕底缘。这是 r295 之前的默认位置。 */
    private static final int MANA_X = 35;
    private static final int MANA_Y_FROM_BOTTOM = 62;
    /** 法术信息块图标中心：默认正好贴在魔素球右边。 */
    private static final int SPELL_X = 75;
    private static final int SPELL_Y_FROM_BOTTOM = 64;
    /** 共鸣核心中心：距屏幕右缘 / 距屏幕底缘。 */
    private static final int RES_X_FROM_RIGHT = 30;
    private static final int RES_Y_FROM_BOTTOM = 30;
    /** 共鸣核心文字默认在核心正上方 32 像素。 */
    private static final int RES_LABEL_UP = 32;
    /** 鉴定面板中心：相对屏幕中心向右 76 像素，刚好让面板贴在准心右边。 */
    private static final int APPRAISAL_X = 76;
    /** 扫描小窗中心：距屏幕右缘 54 像素。 */
    private static final int SCANLINE_X_FROM_RIGHT = 54;
    /** 魔素球的半径（未缩放）。 */
    public static final int ORB_RADIUS = 17;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("HUD 文字提示开关与排版（只影响自己的客户端）")
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
        builder.pop();

        builder.comment("HUD 排版：每个部件的位置偏移（像素）与缩放比例，改完即时生效。",
                        "HUD layout: per-part offset in gui pixels and scale factor.")
                .push("hud_layout");
        for (Part part : Part.values()) {
            int slot = part.ordinal();
            builder.comment("部件：" + part.nameKey).push(part.id);
            DX[slot] = builder.comment("水平偏移：正数往右、负数往左")
                    .translation("config.dimensional_mana.hud_layout." + part.id + ".x")
                    .defineInRange("x", 0, -2000, 2000);
            DY[slot] = builder.comment("垂直偏移：正数往下、负数往上")
                    .translation("config.dimensional_mana.hud_layout." + part.id + ".y")
                    .defineInRange("y", 0, -2000, 2000);
            SCALE[slot] = builder.comment("缩放比例，1.0 为原始大小")
                    .translation("config.dimensional_mana.hud_layout." + part.id + ".scale")
                    .defineInRange("scale", part.defaultScale, part.minScale, 2.0D);
            builder.pop();
        }
        builder.pop();
        SPEC = builder.build();
    }

    private HudConfig() {}

    // ------------------------------------------------------------------ 读写
    public static int dx(Part part) {
        return DX[part.ordinal()].get();
    }

    public static int dy(Part part) {
        return DY[part.ordinal()].get();
    }

    public static double scale(Part part) {
        return SCALE[part.ordinal()].get();
    }

    public static void setDx(Part part, int value) {
        DX[part.ordinal()].set(Mth.clamp(value, -2000, 2000));
    }

    public static void setDy(Part part, int value) {
        DY[part.ordinal()].set(Mth.clamp(value, -2000, 2000));
    }

    public static void setScale(Part part, double value) {
        double clamped = Mth.clamp(value, part.minScale, 2.0D);
        SCALE[part.ordinal()].set(Math.round(clamped * 100.0D) / 100.0D);
    }

    /** 把所有部件恢复成默认排版。 */
    public static void resetLayout() {
        for (Part part : Part.values()) {
            DX[part.ordinal()].set(0);
            DY[part.ordinal()].set(0);
            SCALE[part.ordinal()].set(part.defaultScale);
        }
    }

    public static String formatScale(Part part) {
        return String.format(Locale.ROOT, "%.2f", scale(part));
    }

    // ------------------------------------------------------------------ 默认锚点（各部件的中心点）
    /** 部件默认中心的横坐标（不含玩家的位置偏移）。 */
    public static int baseX(Part part, int width) {
        return switch (part) {
            case MANA_ICON, MANA_TEXT -> MANA_X;
            case SPELL_HUD -> SPELL_X;
            case RES_ICON, RES_LABEL -> width - RES_X_FROM_RIGHT;
            case APPRAISAL -> width / 2 + APPRAISAL_X;
            case SCANLINE -> width - SCANLINE_X_FROM_RIGHT;
        };
    }

    /** 部件默认中心的纵坐标（不含玩家的位置偏移）。 */
    public static int baseY(Part part, int height) {
        return switch (part) {
            case MANA_ICON, MANA_TEXT -> height - MANA_Y_FROM_BOTTOM;
            case SPELL_HUD -> height - SPELL_Y_FROM_BOTTOM;
            case RES_ICON -> height - RES_Y_FROM_BOTTOM;
            case RES_LABEL -> height - RES_Y_FROM_BOTTOM - RES_LABEL_UP;
            case APPRAISAL, SCANLINE -> height / 2;
        };
    }

    /**
     * 部件实际中心的横坐标。所有渲染都用这里算出来的同一个点，
     * 编辑器预览和实际 HUD 才不会对不上。
     */
    public static int anchorX(Part part, int width, int height) {
        return Mth.clamp(baseX(part, width) + dx(part), 4, Math.max(4, width - 4));
    }

    public static int anchorY(Part part, int width, int height) {
        return Mth.clamp(baseY(part, height) + dy(part), 4, Math.max(4, height - 4));
    }

    // ------------------------------------------------------------------ 缩放画布
    /**
     * 以 {@code (x, y)} 为中心按 {@code scale} 缩放后续绘制。
     *
     * <p>渲染代码仍然用原来的绝对坐标画，只是画布被绕中心点放大/缩小，
     * 所以部件看起来是「在原地变大」，不会一边变大一跑。</p>
     */
    public static void pushScaled(GuiGraphics graphics, int x, int y, double scale) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale((float)scale, (float)scale, 1.0F);
        pose.translate(-x, -y, 0.0F);
    }

    public static void popScaled(GuiGraphics graphics) {
        graphics.pose().popPose();
    }
}
