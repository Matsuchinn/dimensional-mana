package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.List;

/**
 * 界面文字的「排版保险」：所有塞进固定宽度的文字都从这里过一遍。
 *
 * <p>中文一个字约 9 像素、英文一个字母约 6 像素，同一句话的英文往往比中文长一半以上。
 * 早期界面按中文宽度写死坐标，换成英文就会顶出卡片、按钮和书页边框 —— 这是发行后
 * 「文字看起来是坏的」最直接的原因。这里提供三种兜底：</p>
 *
 * <ul>
 *   <li>{@link #clamp}：单行，放不下就截断并补省略号；</li>
 *   <li>{@link #clampLines}：多行，先按宽度自动折行，超出限定行数时在最后一行补省略号；</li>
 *   <li>{@link #centered}/{@link #left}/{@link #right}：先截断再按原对齐方式绘制，
 *       居中、左对齐、右对齐都能用。</li>
 * </ul>
 *
 * <p>只做「别画出去」这一件事，不改变文字内容：能放下的照原样画，排版不会因为语言切换而跳动。</p>
 */
public final class TextFit {
    /** 省略号：原版字体里就是一个字宽，截断后补上不会再把文字顶出去。 */
    public static final String ELLIPSIS = "…";

    /** 自动缩字时允许的最小倍率：再小就看不清了，宁可截断。 */
    public static final float MIN_SCALE = 0.62F;

    private TextFit() { }

    /** 单行截断：放得下原样返回，放不下截到 maxWidth 以内再补省略号。 */
    public static String clamp(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        int ellipsisWidth = font.width(ELLIPSIS);
        if (maxWidth <= ellipsisWidth) return font.plainSubstrByWidth(ELLIPSIS, maxWidth);
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ELLIPSIS;
    }

    /** 单行截断（Component 版）：保留原来的颜色与样式。 */
    public static Component clamp(Font font, Component text, int maxWidth) {
        if (text == null) return Component.empty();
        if (font.width(text) <= maxWidth) return text;
        return Component.literal(clamp(font, text.getString(), maxWidth)).withStyle(text.getStyle());
    }

    /**
     * 多行截断：按 maxWidth 折行，最多 maxLines 行；放不下的内容在第 maxLines 行末尾补省略号。
     *
     * <p>返回的每一行都已经是 String，调用方按行高逐行绘制即可。</p>
     */
    public static List<String> clampLines(Font font, String text, int maxWidth, int maxLines) {
        return clampLines(font, Component.literal(text == null ? "" : text), maxWidth, maxLines);
    }

    /** 多行截断（Component 版），样式按行保留。 */
    public static List<String> clampLines(Font font, Component text, int maxWidth, int maxLines) {
        if (text == null || maxWidth <= 0 || maxLines <= 0) return List.of();
        // splitLines 在字节码里是裸 List，这里显式收窄成 FormattedText。
        @SuppressWarnings("unchecked")
        List<FormattedText> lines = (List<FormattedText>) (List<?>) font.getSplitter()
                .splitLines(text, maxWidth, Style.EMPTY);
        if (lines.size() <= maxLines) {
            return lines.stream().map(FormattedText::getString).toList();
        }
        List<String> out = new java.util.ArrayList<>(maxLines);
        for (int i = 0; i < maxLines; i++) out.add(lines.get(i).getString());
        int last = maxLines - 1;
        out.set(last, clamp(font, out.get(last), maxWidth));
        return out;
    }

    /** 居中绘制并截断到 maxWidth。 */
    public static void centered(GuiGraphics graphics, Font font, Component text, int centerX, int y,
                                int maxWidth, int color) {
        centered(graphics, font, text, centerX, y, maxWidth, color, false);
    }

    /** 居中绘制并截断到 maxWidth（可带阴影）。 */
    public static void centered(GuiGraphics graphics, Font font, Component text, int centerX, int y,
                                int maxWidth, int color, boolean shadow) {
        Component fitted = clamp(font, text, maxWidth);
        graphics.drawString(font, fitted, centerX - font.width(fitted) / 2, y, color, shadow);
    }

    /**
     * 居中绘制，太宽时整体缩小字号而不是截断。
     *
     * <p>给「宽度很小但必须读全」的格子用：中文字一两个就很短，英文单词却很容易超宽
     * （例如 36 像素宽的媒介图标格里的 “Lightning”）。缩到 {@link #MIN_SCALE} 还放不下才截断。</p>
     */
    public static void centeredShrunk(GuiGraphics graphics, Font font, Component text, int centerX, int y,
                                      int maxWidth, int color) {
        shrink(graphics, font, text, centerX - maxWidth / 2, y, maxWidth, color, true);
    }

    /** 在 x 处左对齐绘制，太宽时整体缩小字号（最小 {@link #MIN_SCALE}）再截断。 */
    public static void leftShrunk(GuiGraphics graphics, Font font, Component text, int x, int y,
                                  int maxWidth, int color) {
        shrink(graphics, font, text, x, y, maxWidth, color, false);
    }

    private static void shrink(GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth,
                               int color, boolean center) {
        int width = font.width(text);
        float scale = width <= maxWidth ? 1.0F : Math.max(MIN_SCALE, maxWidth / (float) width);
        Component shown = scale <= MIN_SCALE ? clamp(font, text, maxWidth) : text;
        if (scale >= 0.999F) {
            int drawX = center ? x + (maxWidth - font.width(shown)) / 2 : x;
            graphics.drawString(font, shown, drawX, y, color, false);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        int scaledWidth = font.width(shown);
        int drawX = center ? Math.round((maxWidth / scale - scaledWidth) / 2.0F) : 0;
        graphics.drawString(font, shown, drawX, 0, color, false);
        pose.popPose();
    }

    /** 左对齐绘制并截断到 maxWidth。 */
    public static void left(GuiGraphics graphics, Font font, Component text, int x, int y,
                            int maxWidth, int color, boolean shadow) {
        graphics.drawString(font, clamp(font, text, maxWidth), x, y, color, shadow);
    }

    /** 右对齐绘制并截断到 maxWidth。 */
    public static void right(GuiGraphics graphics, Font font, Component text, int rightX, int y,
                             int maxWidth, int color, boolean shadow) {
        Component fitted = clamp(font, text, maxWidth);
        graphics.drawString(font, fitted, rightX - font.width(fitted), y, color, shadow);
    }

    /**
     * 右对齐绘制，太宽时整体缩小字号（最小 {@link #MIN_SCALE}）再截断。
     *
     * <p>属性面板的数值用它：位数会随等级和属性上限变化（“15” 和 “154000000”），
     * 右对齐让一列数字的末位对齐，超宽时缩字保证永远不顶出面板。</p>
     */
    public static void rightShrunk(GuiGraphics graphics, Font font, Component text, int rightX, int y,
                                   int maxWidth, int color) {
        if (text == null || maxWidth <= 0) return;
        int width = font.width(text);
        float scale = width <= maxWidth ? 1.0F : Math.max(MIN_SCALE, maxWidth / (float) width);
        Component shown = scale <= MIN_SCALE ? clamp(font, text, maxWidth) : text;
        if (scale >= 0.999F) {
            graphics.drawString(font, shown, rightX - font.width(shown), y, color, false);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(rightX, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, shown, -font.width(shown), 0, color, false);
        pose.popPose();
    }

    /**
     * 多行绘制：最多 maxLines 行，行距 lineHeight；返回实际占用的高度。
     *
     * <p>给「卡片里的一段说明」用：中文两行、英文可能四行，这里统一裁到卡片放得下的行数。</p>
     */
    public static int wrapLines(GuiGraphics graphics, Font font, Component text, int x, int y,
                                int maxWidth, int maxLines, int lineHeight, int color) {
        List<String> lines = clampLines(font, text, maxWidth, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
        }
        return lines.size() * lineHeight;
    }
}
