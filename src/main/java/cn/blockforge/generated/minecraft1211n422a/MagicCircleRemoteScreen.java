package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 远程法阵面板：手持魔导调试棒、潜行右键空气打开，把附近属于自己的刻印法阵列成一叠，上下随便翻。
 *
 * <p>r216 改版：原来是把 6 个法阵摆成一圈、滚轮翻页，第 7 个起要翻页才看得到，阵一多就很难用。
 * 现在改成<b>一列可以上下滚动的清单</b>：一行一个法阵，行里写名字、运行状态、层数和距离，
 * 左键点哪行就远程开 / 关哪一个；底部固定「全部停机」和「导入法阵码」两颗按钮不跟着滚，
 * 右边一条滚动条显示自己在哪一段。滚轮 / 拖动滚动条 / ↑↓ / PgUp PgDn 都能翻，Esc 关掉。</p>
 *
 * <p>服务端在 {@link MagicCircleRites#sendRemoteList} 里按距离从近到远排好，最多
 * {@link MagicCircleRites#REMOTE_MAX_ENTRIES} 个；清单超过上限时底部会写一句「只列了最近的 N 个」。</p>
 */
public final class MagicCircleRemoteScreen extends Screen {
    /** 一行的高度（含上下留白）。 */
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_INNER = 24;
    private static final int PANEL_MAX_WIDTH = 380;
    private static final int PANEL_MAX_HEIGHT = 300;
    private static final int HEADER_HEIGHT = 46;
    private static final int FOOTER_HEIGHT = 30;
    private static final int STOP_WIDTH = 96;
    private static final int STOP_HEIGHT = 20;
    private static final int IMPORT_WIDTH = 96;
    private static final int IMPORT_HEIGHT = 20;
    private static final int SCROLLBAR_WIDTH = 5;

    private MagicCircleRemoteListPayload data;
    /** 清单往上滚了多少像素。 */
    private double scroll;
    private int hovered = -1;
    private boolean hoverStop;
    private boolean hoverImport;
    /** 正在拖滚动条；{@link #dragOffset} 是按下时鼠标离滑块顶端的距离。 */
    private boolean draggingScrollbar;
    private double dragOffset;

    public MagicCircleRemoteScreen(MagicCircleRemoteListPayload data) {
        super(Component.translatable("gui.dimensional_mana.magiccircle.remote.title"));
        this.data = data;
    }

    /** 服务端重发清单（开关之后、或者指令改了状态）：就地刷新，滚动位置保持不动。 */
    public void update(MagicCircleRemoteListPayload payload) {
        this.data = payload;
        this.scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x9E05060C);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        hovered = -1;
        hoverStop = false;
        hoverImport = false;

        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();

        // 面板底
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xC8120F1E);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF5A4770);

        graphics.drawCenteredString(font, title, left + panelWidth / 2, top + 8, 0xFFB4A0FF);
        graphics.drawCenteredString(font, summary(), left + panelWidth / 2, top + 22, 0xFF8FE9FF);
        if (!data.entries().isEmpty() && panelWidth >= 300) {
            // 右上角：附近一共扫到几个（清单可能只是其中最近的 64 个）。
            Component count = Component.translatable(
                    "gui.dimensional_mana.magiccircle.remote.count", data.total());
            graphics.drawString(font, count, left + panelWidth - 12 - font.width(count), top + 8,
                    0xFF7C6F94, false);
        }
        graphics.fill(left + 10, top + HEADER_HEIGHT - 6, left + panelWidth - 10, top + HEADER_HEIGHT - 5,
                0xFF3A2B50);

        List<MagicCircleRemoteListPayload.Entry> entries = data.entries();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        int listLeft = listLeft();
        int listTop = listTop();
        int listRight = listRight();
        int listBottom = listBottom();

        if (entries.isEmpty()) {
            int midY = (listTop + listBottom) / 2;
            graphics.drawCenteredString(font, Component.translatable(
                    "gui.dimensional_mana.magiccircle.remote.empty"),
                    (listLeft + listRight) / 2, midY - 10, 0xFF9C8FB5);
            graphics.drawCenteredString(font, Component.translatable(
                    "gui.dimensional_mana.magiccircle.remote.empty_hint"),
                    (listLeft + listRight) / 2, midY + 4, 0xFF7C6F94);
        } else {
            graphics.enableScissor(listLeft, listTop, listRight, listBottom);
            for (int index = 0; index < entries.size(); index++) {
                int rowY = rowTop(index);
                if (rowY + ROW_INNER < listTop || rowY > listBottom) continue; // 滚出视野就不画
                drawRow(graphics, entries.get(index), index, listLeft, rowY, listRight, mouseX, mouseY);
            }
            graphics.disableScissor();
        }

        drawScrollbar(graphics, entries.size(), mouseX, mouseY);
        drawFooter(graphics, left, top, panelWidth, panelHeight, entries, mouseX, mouseY);
    }

    /** 一行：名字 + 运行状态 + 「几层 · 距几格」，悬停高亮，点击整行有效。 */
    private void drawRow(GuiGraphics graphics, MagicCircleRemoteListPayload.Entry entry, int index,
                         int left, int top, int right, int mouseX, int mouseY) {
        boolean hover = mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + ROW_INNER
                && mouseY >= listTop() && mouseY < listBottom();
        if (hover) hovered = index;
        int background = hover ? 0xE0342050 : entry.running() ? 0xC0223A2E : 0xB01A1426;
        int outline = hover ? 0xFFFFD86B : entry.running() ? 0xFF8FFFC1 : 0xFF5A4770;
        graphics.fill(left, top, right, top + ROW_INNER, background);
        graphics.renderOutline(left, top, right - left, ROW_INNER, outline);

        Component state = Component.translatable(entry.running()
                ? "gui.dimensional_mana.magiccircle.remote.running"
                : "gui.dimensional_mana.magiccircle.remote.stopped");
        int stateWidth = font.width(state);
        int nameRoom = Math.max(24, right - left - 16 - stateWidth - 6);
        String name = font.plainSubstrByWidth(entry.name(), nameRoom);
        graphics.drawString(font, name, left + 6, top + 4, hover ? 0xFFFFE4A8 : 0xFFE9DCFF, false);
        graphics.drawString(font, state, right - 6 - stateWidth, top + 4,
                entry.running() ? 0xFF8FFFC1 : 0xFF9C8FB5, false);

        Component meta = Component.translatable("gui.dimensional_mana.magiccircle.remote.entry",
                entry.layers(), entry.distance());
        // 悬停时把坐标补在后面：阵一多，光看名字不一定找得到是哪一座。
        String metaText = hover
                ? meta.getString() + " · " + entry.x() + ", " + entry.y() + ", " + entry.z()
                : meta.getString();
        graphics.drawString(font, font.plainSubstrByWidth(metaText, Math.max(24, right - left - 12)),
                left + 6, top + 14, hover ? 0xFFFFD86B : 0xFF8FE9FF, false);
    }

    /** 右边那条滚动条：有内容才画，滑块位置 = 当前滚到哪。 */
    private void drawScrollbar(GuiGraphics graphics, int entryCount, int mouseX, int mouseY) {
        if (entryCount <= 0) return;
        int trackX = scrollbarX();
        int trackTop = listTop();
        int trackBottom = listBottom();
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom, 0x60120F20);
        int thumbHeight = thumbHeight();
        int thumbTop = thumbTop(thumbHeight);
        boolean hover = mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                && mouseY >= trackTop && mouseY < trackBottom;
        graphics.fill(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight,
                draggingScrollbar ? 0xFFFFD86B : hover ? 0xFFB4A0FF : 0xFF7A66A0);
    }

    /** 标题下面那行：运行中几个 / 名额多少，清单超上限时补一句「只列了最近的 N 个」。 */
    private Component summary() {
        Component base = Component.translatable("gui.dimensional_mana.magiccircle.remote.summary",
                data.running(), data.limit(), data.levelBonus(), data.manaBonus());
        int total = data.total();
        int shown = data.entries().size();
        if (total > shown) {
            return base.copy().append(Component.literal("  ")).append(Component.translatable(
                    "gui.dimensional_mana.magiccircle.remote.truncated", total, shown));
        }
        return base;
    }

    /** 底部固定栏：全部停机 + 导入法阵码（不随清单滚动）。 */
    private void drawFooter(GuiGraphics graphics, int left, int top, int panelWidth, int panelHeight,
                            List<MagicCircleRemoteListPayload.Entry> entries, int mouseX, int mouseY) {
        int buttonY = top + panelHeight - FOOTER_HEIGHT + 4;
        int stopX = left + 10;
        hoverStop = mouseX >= stopX && mouseX < stopX + STOP_WIDTH
                && mouseY >= buttonY && mouseY < buttonY + STOP_HEIGHT;
        graphics.fill(stopX, buttonY, stopX + STOP_WIDTH, buttonY + STOP_HEIGHT,
                hoverStop ? 0xE06A2030 : 0xC02A1420);
        graphics.renderOutline(stopX, buttonY, STOP_WIDTH, STOP_HEIGHT,
                hoverStop ? 0xFFFF9BA8 : 0xFF7A4A5A);
        Component stopLabel = Component.translatable("gui.dimensional_mana.magiccircle.remote.stop_all");
        graphics.drawString(font, stopLabel, stopX + (STOP_WIDTH - font.width(stopLabel)) / 2, buttonY + 6,
                hoverStop ? 0xFFFFC0C8 : 0xFFE9A8B4, false);

        int importX = left + panelWidth - 10 - IMPORT_WIDTH;
        hoverImport = mouseX >= importX && mouseX < importX + IMPORT_WIDTH
                && mouseY >= buttonY && mouseY < buttonY + IMPORT_HEIGHT;
        graphics.fill(importX, buttonY, importX + IMPORT_WIDTH, buttonY + IMPORT_HEIGHT,
                hoverImport ? 0xE04A2E6E : 0xB0120F20);
        graphics.renderOutline(importX, buttonY, IMPORT_WIDTH, IMPORT_HEIGHT,
                hoverImport ? 0xFFFFD86B : 0xFF5A4770);
        Component importLabel = Component.translatable("gui.dimensional_mana.magiccircle.remote.import");
        graphics.drawString(font, importLabel, importX + (IMPORT_WIDTH - font.width(importLabel)) / 2,
                buttonY + 6, hoverImport ? 0xFFFFE4A8 : 0xFFE9DCFF, false);

        if (!entries.isEmpty()) {
            // 底部中间那条提示：只画在两颗按钮之间的空档里，放不下就按宽度截断，绝不压到按钮上。
            int gapLeft = stopX + STOP_WIDTH + 8;
            int gapRight = importX - 8;
            int room = gapRight - gapLeft;
            if (room > 40) {
                String hint = Component.translatable(
                        "gui.dimensional_mana.magiccircle.remote.hint").getString();
                hint = font.plainSubstrByWidth(hint, room);
                graphics.drawString(font, hint, gapLeft + (room - font.width(hint)) / 2, buttonY + 6,
                        0xFFB4A0FF, false);
            }
        }
    }

    // ------------------------------------------------------------------ 几何

    private int panelWidth() {
        return Math.max(160, Math.min(PANEL_MAX_WIDTH, width - 40));
    }

    private int panelHeight() {
        return Math.max(120, Math.min(PANEL_MAX_HEIGHT, height - 40));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (height - panelHeight()) / 2;
    }

    private int listLeft() {
        return panelLeft() + 10;
    }

    private int listTop() {
        return panelTop() + HEADER_HEIGHT;
    }

    private int listRight() {
        return panelLeft() + panelWidth() - 18;
    }

    private int listBottom() {
        return panelTop() + panelHeight() - FOOTER_HEIGHT;
    }

    private int scrollbarX() {
        return panelLeft() + panelWidth() - 13;
    }

    private int rowTop(int index) {
        return listTop() + index * ROW_HEIGHT - (int) scroll;
    }

    /** 清单整体比可视区高多少像素 = 最多能滚这么远。 */
    private int maxScroll() {
        int content = data.entries().size() * ROW_HEIGHT;
        return Math.max(0, content - Math.max(0, listBottom() - listTop()));
    }

    private int thumbHeight() {
        int view = Math.max(1, listBottom() - listTop());
        int content = Math.max(view, data.entries().size() * ROW_HEIGHT);
        return Math.max(16, view * view / content);
    }

    private int thumbTop(int thumbHeight) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return listTop();
        int travel = Math.max(1, listBottom() - listTop() - thumbHeight);
        return listTop() + (int) Math.round(travel * (scroll / maxScroll));
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (hoverImport) {
            ClientMagicCircleScreens.openCodeImport();
            return true;
        }
        if (hoverStop) {
            PacketDistributor.sendToServer(MagicCircleNetActionPayload.stopAll());
            // 停机之后让清单跟着变灰：单独要一份新清单，别指望服务端主动推。
            PacketDistributor.sendToServer(MagicCircleNetActionPayload.remoteRefresh());
            return true;
        }
        if (hovered >= 0 && hovered < data.entries().size()) {
            MagicCircleRemoteListPayload.Entry entry = data.entries().get(hovered);
            PacketDistributor.sendToServer(MagicCircleNetActionPayload.remoteToggle(
                    new BlockPos(entry.x(), entry.y(), entry.z())));
            return true;
        }
        // 点滚动条：按到滑块上就开始拖，按到空处就直接跳到那个位置。
        if (data.entries().size() > 0 && maxScroll() > 0 && mouseX >= scrollbarX()
                && mouseX < scrollbarX() + SCROLLBAR_WIDTH
                && mouseY >= listTop() && mouseY < listBottom()) {
            int thumbHeight = thumbHeight();
            int thumbTop = thumbTop(thumbHeight);
            if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight) {
                dragOffset = mouseY - thumbTop;
            } else {
                dragOffset = thumbHeight / 2.0;
                dragScrollbarTo(mouseY);
            }
            draggingScrollbar = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 把滑块拖到鼠标所在的比例位置。 */
    private void dragScrollbarTo(double mouseY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            scroll = 0;
            return;
        }
        int travel = Math.max(1, listBottom() - listTop() - thumbHeight());
        double position = (mouseY - dragOffset - listTop()) / (double) travel;
        scroll = Math.max(0, Math.min(maxScroll, Math.round(position * maxScroll)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double verticalDelta) {
        scrollBy(verticalDelta > 0 ? -ROW_HEIGHT : ROW_HEIGHT);
        return true;
    }

    private void scrollBy(int pixels) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll + pixels));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        int page = Math.max(ROW_HEIGHT, listBottom() - listTop() - ROW_HEIGHT);
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> scrollBy(-ROW_HEIGHT);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> scrollBy(ROW_HEIGHT);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-page);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(page);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> scroll = 0;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> scroll = maxScroll();
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
