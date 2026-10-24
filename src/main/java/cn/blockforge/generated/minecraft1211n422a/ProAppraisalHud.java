package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 专业鉴定眼镜的 HUD。
 *
 * <p>所有目标信息收成准心旁边的一块「数据面板」：名称 → 生命 / 魔素 / 斗气三条状态条 →
 * 每秒恢复 → 抗性 → 易伤 → 技能状态。面板内部从左到右固定三列（标签列、状态条、数值列），
 * 标签宽度按最长的那个算、数值列统一右对齐，所以三行条的起点和数字都对齐；
 * 整块以准心为中心上下居中，右边放不下会自动翻到准心左侧。</p>
 *
 * <p>以前恢复 / 抗性那两行是居中画在准心正下方的，正好压到状态条的魔素行上，
 * 长抗性行还会横穿准心，所以现在把它们都收进面板，准心下方只留一句解析失败提示。
 * 每个条目依旧能在 {@link AppraisalConfig} 里单独关掉。</p>
 */
public final class ProAppraisalHud {
    /** 状态条统一宽度：76 像素大约能放下 19 个数字。 */
    private static final int BAR_WIDTH = 76;
    private static final int BAR_HEIGHT = 3;
    private static final int SCANLINE_WIDTH = 84;
    private static final int SCANLINE_HEIGHT = 46;

    /** 行高：文字本身 9 像素高，留 1 像素行距；标题行再高 2 像素，和状态条拉开一点。 */
    private static final int ROW_HEIGHT = 10;
    private static final int TITLE_ROW_HEIGHT = 12;
    /** 标签列、状态条、数值列之间的固定间距。 */
    private static final int COLUMN_GAP = 4;
    /** 面板内边距：底板比内容每边宽这么多。 */
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 4;
    /** 面板内容的宽度范围：抗性这类长行按这个宽度截断，绝不横穿整个屏幕。 */
    private static final int CONTENT_MAX_WIDTH = 226;
    private static final int CONTENT_MIN_WIDTH = 92;
    /** 面板离屏幕边缘至少留这么多像素。 */
    private static final int SCREEN_MARGIN = 6;
    private static final int PANEL_BACKGROUND = 0x6610141C;
    private static final int PANEL_BORDER = 0x5579E8FF;

    private static final int HEALTH_COLOR = 0xFFFF6B6B;
    private static final int MANA_COLOR = 0xFF70D8FF;
    private static final int BATTLE_QI_COLOR = 0xFFFFCD59;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFBFC7D5;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int REGEN_COLOR = 0xFF9EE6FF;
    private static final int TRAIT_COLOR = 0xFFFFE08A;
    private static final int TRAIT_INACTIVE_COLOR = 0xFF8A8A8A;
    private static final int FAIL_COLOR = 0xFFFF6B6B;
    private static final int READY_COLOR = 0xFF6BE86B;
    private static final int COOLDOWN_COLOR = 0xFFFF9A9A;

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientProAppraisalState.equipped() || minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui) return;
        Font font = minecraft.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        if (!ClientProAppraisalState.active()) {
            // 魔素不够：整块面板都不画，只在准心下面留一句失败提示，别挡住准心。
            drawCentered(graphics, font,
                    Component.translatable("hud.dimensional_mana.appraisal.analysis_failed").getString(),
                    centerX, centerY + 14, FAIL_COLOR);
            return;
        }
        if (ClientProAppraisalState.entityId() < 0) return;
        renderTargetPanel(graphics, font, screenWidth, screenHeight, centerX, centerY);
        if (AppraisalConfig.SHOW_SCANLINE.get()) {
            renderScanline(graphics, font, screenWidth, screenHeight);
        }
    }

    /**
     * 目标数据面板：一块半透明底板 + 左对齐的若干行，行与行之间固定行距。
     *
     * <p>先算出「标签列多宽 / 数值列多宽 / 整块多宽多高」，再决定放在准心右边还是左边，
     * 最后才开始画，这样每一列都能对齐，也不会跑出屏幕。</p>
     */
    private static void renderTargetPanel(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                          int centerX, int centerY) {
        boolean bars = AppraisalConfig.SHOW_TARGET_BARS.get();
        String title = ClientProAppraisalState.targetName();

        String healthLabel = Component.translatable("hud.dimensional_mana.appraisal.health").getString();
        String manaLabel = Component.translatable("hud.dimensional_mana.appraisal.mana").getString();
        String qiLabel = Component.translatable("hud.dimensional_mana.appraisal.battle_qi").getString();
        String healthValue = formatPair(ClientProAppraisalState.health(), ClientProAppraisalState.maxHealth());
        String manaValue = formatPair(ClientProAppraisalState.mana(), ClientProAppraisalState.maxMana());
        String qiValue = formatPair(ClientProAppraisalState.battleQi(), ClientProAppraisalState.maxBattleQi());

        // 三行状态条共用同一列：标签取最长的一个，数值也取最宽的一个，条和数字才会对齐。
        int labelWidth = bars ? Math.max(font.width(healthLabel), Math.max(font.width(manaLabel), font.width(qiLabel))) : 0;
        int valueWidth = bars ? Math.max(font.width(healthValue), Math.max(font.width(manaValue), font.width(qiValue))) : 0;
        int barOffset = labelWidth + COLUMN_GAP;
        int valueOffset = barOffset + BAR_WIDTH + COLUMN_GAP;
        int barBlockWidth = bars ? valueOffset + valueWidth : 0;

        List<Row> rows = new ArrayList<>();
        if (bars) rows.add(Row.header(title));

        if (AppraisalConfig.SHOW_REGEN_LINE.get()) {
            rows.add(Row.line(Component.translatable("hud.dimensional_mana.appraisal.regen",
                    formatRate(ClientProAppraisalState.manaRegen()),
                    formatRate(ClientProAppraisalState.battleQiRegen())).getString(), REGEN_COLOR));
        }
        if (AppraisalConfig.SHOW_RESISTANCE_LINE.get()) {
            // 抗性、易伤各占一行：挤在一行里长度翻倍，还会压到准心上。
            String resist = join(ClientProAppraisalState.resistances());
            if (!resist.isEmpty()) {
                rows.add(Row.line(Component.translatable(
                        "hud.dimensional_mana.appraisal.resist", resist).getString(), traitColor()));
            }
            String weakness = join(ClientProAppraisalState.weaknesses());
            if (!weakness.isEmpty()) {
                rows.add(Row.line(Component.translatable(
                        "hud.dimensional_mana.appraisal.weakness", weakness).getString(), traitColor()));
            }
            if (!resist.isEmpty() || !weakness.isEmpty()) {
                // 普通难度下这些抗性并不生效，单独用灰色小字注一句，免得玩家按错元素。
                if (!traitsActive(Minecraft.getInstance().level)) {
                    rows.add(Row.line(Component.translatable(
                            "hud.dimensional_mana.appraisal.trait_inactive").getString(), TRAIT_INACTIVE_COLOR));
                }
            }
        }
        if (AppraisalConfig.SHOW_SKILL_LINE.get() && !ClientProAppraisalState.skillId().isEmpty()) {
            String skillName = Component.translatable(
                    "skill.dimensional_mana." + ClientProAppraisalState.skillId()).getString();
            boolean ready = ClientProAppraisalState.skillRemainingTicks() <= 0;
            rows.add(Row.line(ready
                    ? Component.translatable("hud.dimensional_mana.appraisal.skill_ready", skillName).getString()
                    : Component.translatable("hud.dimensional_mana.appraisal.skill_cooldown", skillName,
                            String.format(Locale.ROOT, "%.1f",
                                    ClientProAppraisalState.skillRemainingTicks() / 20.0)).getString(),
                    ready ? READY_COLOR : COOLDOWN_COLOR));
        }

        int contentWidth = barBlockWidth;
        for (Row row : rows) contentWidth = Math.max(contentWidth, row.width(font));
        contentWidth = Math.max(CONTENT_MIN_WIDTH, Math.min(CONTENT_MAX_WIDTH, contentWidth));

        int contentHeight = 0;
        for (Row row : rows) contentHeight += row.height();
        if (bars) contentHeight += 3 * ROW_HEIGHT;

        int panelWidth = contentWidth + PADDING_X * 2;
        int panelHeight = contentHeight + PADDING_Y * 2;

        int panelX = placeX(centerX, screenWidth, panelWidth);
        int panelY = centerY + AppraisalConfig.HUD_OFFSET_Y.get() - panelHeight / 2;
        panelY = clamp(panelY, SCREEN_MARGIN, screenHeight - panelHeight - SCREEN_MARGIN);

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BACKGROUND);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);

        int x = panelX + PADDING_X;
        int y = panelY + PADDING_Y;
        if (bars) {
            graphics.drawString(font, fit(font, title, contentWidth), x, y, TITLE_COLOR, true);
            y += TITLE_ROW_HEIGHT;
            y = drawStatRow(graphics, font, x, y, barOffset, valueOffset, healthLabel, healthValue,
                    ratio(ClientProAppraisalState.health(), ClientProAppraisalState.maxHealth()), HEALTH_COLOR);
            y = drawStatRow(graphics, font, x, y, barOffset, valueOffset, manaLabel, manaValue,
                    ratio(ClientProAppraisalState.mana(), ClientProAppraisalState.maxMana()), MANA_COLOR);
            y = drawStatRow(graphics, font, x, y, barOffset, valueOffset, qiLabel, qiValue,
                    ratio(ClientProAppraisalState.battleQi(), ClientProAppraisalState.maxBattleQi()), BATTLE_QI_COLOR);
        }
        for (Row row : rows) {
            // 标题和三条状态条已经画过了，剩下的都是一行文字。
            if (row.title()) continue;
            graphics.drawString(font, fit(font, row.text(), contentWidth), x, y, row.color(), true);
            y += row.height();
        }
    }

    /**
     * 面板横向位置：默认贴在准心右侧，右侧没地方就翻到左侧，两边都不够就夹在屏幕内。
     * 开了扫描小窗时右边还要再让出小窗的宽度。
     */
    private static int placeX(int centerX, int screenWidth, int panelWidth) {
        int offset = Math.max(0, AppraisalConfig.HUD_OFFSET_X.get());
        int reservedRight = AppraisalConfig.SHOW_SCANLINE.get() ? SCANLINE_WIDTH + 14 : 0;
        int maxLeft = screenWidth - panelWidth - SCREEN_MARGIN;
        int x = centerX + offset;
        if (x + panelWidth > maxLeft - reservedRight) {
            x = centerX - offset - panelWidth;
        }
        return clamp(x, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, maxLeft));
    }

    private static int drawStatRow(GuiGraphics graphics, Font font, int x, int y, int barOffset, int valueOffset,
                                   String label, String value, double ratio, int barColor) {
        graphics.drawString(font, label, x, y, LABEL_COLOR, true);
        drawBar(graphics, x + barOffset, y + 1, BAR_WIDTH, ratio, barColor);
        graphics.drawString(font, value, x + valueOffset, y, VALUE_COLOR, true);
        return y + ROW_HEIGHT;
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int width, double ratio, int color) {
        graphics.fill(x, y, x + width, y + BAR_HEIGHT, 0x66000000);
        int filled = (int)Math.round(width * Math.max(0.0, Math.min(1.0, ratio)));
        if (filled > 0) graphics.fill(x, y, x + filled, y + BAR_HEIGHT, color);
    }

    /** 屏幕右侧的时间轴小窗：三条曲线 + 一条循环扫过的竖线。 */
    private static void renderScanline(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        int left = screenWidth - SCANLINE_WIDTH - 12;
        int top = screenHeight / 2 - SCANLINE_HEIGHT / 2;
        graphics.fill(left, top, left + SCANLINE_WIDTH, top + SCANLINE_HEIGHT, 0x88000000);
        graphics.renderOutline(left, top, SCANLINE_WIDTH, SCANLINE_HEIGHT, 0xFF79E8FF);
        int innerLeft = left + 3;
        int innerTop = top + 3;
        int innerWidth = SCANLINE_WIDTH - 6;
        int innerHeight = SCANLINE_HEIGHT - 6;
        drawHistoryLine(graphics, innerLeft, innerTop, innerWidth, innerHeight, 0, HEALTH_COLOR);
        drawHistoryLine(graphics, innerLeft, innerTop, innerWidth, innerHeight, 1, MANA_COLOR);
        drawHistoryLine(graphics, innerLeft, innerTop, innerWidth, innerHeight, 2, BATTLE_QI_COLOR);
        int cursorX = innerLeft + (int)Math.round(innerWidth * ClientProAppraisalState.scanlineCursor());
        graphics.fill(cursorX, innerTop, cursorX + 1, innerTop + innerHeight, 0xCCFFFFFF);
    }

    private static void drawHistoryLine(GuiGraphics graphics, int left, int top, int width, int height,
                                        int channel, int color) {
        List<float[]> history = ClientProAppraisalState.history();
        if (history.size() < 2) return;
        int span = Math.max(1, ClientProAppraisalState.SCANLINE_SAMPLES - 1);
        for (int i = 0; i < history.size(); i++) {
            float[] sample = history.get(i);
            int x = left + i * width / span;
            int y = top + height - 1 - (int)Math.round(sample[channel] * (height - 1));
            graphics.fill(x, y, x + 1, y + 2, color);
        }
    }

    /** 抗性表是否在当前难度真正生效（跟服务端 resistances_hard_only 用同一套判断）。 */
    private static boolean traitsActive(net.minecraft.client.multiplayer.ClientLevel level) {
        if (!MobCombatConfig.ENABLED.get()) return false;
        if (!MobCombatConfig.RESISTANCES_HARD_ONLY.get()) return true;
        if (level == null) return true;
        return level.getDifficulty() == net.minecraft.world.Difficulty.HARD;
    }

    private static int traitColor() {
        return traitsActive(Minecraft.getInstance().level) ? TRAIT_COLOR : TRAIT_INACTIVE_COLOR;
    }

    /** 把服务端的 key=value 条目翻译成「冰 100%」这种短标签。 */
    private static String traitText(String encoded) {
        int split = encoded.indexOf('=');
        if (split <= 0) return encoded;
        String key = encoded.substring(0, split);
        double value;
        try {
            value = Double.parseDouble(encoded.substring(split + 1));
        } catch (NumberFormatException invalid) {
            return encoded;
        }
        CombatElement element = CombatElement.byId(key);
        String name = element != null
                ? Component.translatable(element.translationKey()).getString()
                : Component.translatable("hud.dimensional_mana.appraisal.trait." + key).getString();
        int percent = (int)Math.round(Math.abs(value) * 100.0);
        return name + " " + percent + "%";
    }

    /** 一行的抗性 / 易伤清单：条数按配置限制，超出部分用「+N」表示。 */
    private static String join(List<String> entries) {
        if (entries == null || entries.isEmpty()) return "";
        int max = Math.max(1, AppraisalConfig.MAX_RESISTANCE_ENTRIES.get());
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (String entry : entries) {
            if (shown >= max) break;
            if (shown > 0) builder.append(Component.translatable(
                    "hud.dimensional_mana.appraisal.trait_separator").getString());
            builder.append(traitText(entry));
            shown++;
        }
        int remainder = entries.size() - shown;
        if (remainder > 0) builder.append(" +").append(remainder);
        return builder.toString();
    }

    /** 太长的行截断成「…」，保证面板宽度固定、不会横穿屏幕。 */
    private static String fit(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(4, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static double ratio(double current, double max) {
        if (max <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(1.0, current / max));
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static String formatPair(double current, double max) {
        return formatNumber(current) + "/" + formatNumber(max);
    }

    private static String formatRate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, value >= 1000.0 ? "%.0f" : "%.1f", value);
    }

    private static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, true);
    }

    /** 面板里的一行：标题行比普通行高 2 像素。 */
    private record Row(String text, int color, boolean title) {
        static Row header(String text) {
            return new Row(text, TITLE_COLOR, true);
        }

        static Row line(String text, int color) {
            return new Row(text, color, false);
        }

        int height() {
            return title ? TITLE_ROW_HEIGHT : ROW_HEIGHT;
        }

        int width(Font font) {
            return font.width(text);
        }
    }

    private ProAppraisalHud() {}
}
