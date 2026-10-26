package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 远程法阵轮盘：手持魔导调试棒、潜行右键空气打开，把附近属于自己的刻印法阵围成一圈。
 *
 * <p>左键点一个卡片就远程开 / 关那一个阵（服务端会复核主人、距离和名额，然后重发一份清单，
 * 卡片上的「运行中 / 已停止」当场变颜色）；中间那颗是「全部停机」，和停机键一个效果。
 * 一圈放 6 个，滚轮翻页；Esc 关掉。</p>
 */
public final class MagicCircleRemoteScreen extends Screen {
    /** 一圈放几个。 */
    private static final int PER_PAGE = 6;
    private static final int CARD_WIDTH = 96;
    private static final int CARD_HEIGHT = 30;
    private static final int STOP_WIDTH = 104;
    private static final int STOP_HEIGHT = 26;
    private static final int IMPORT_WIDTH = 88;
    private static final int IMPORT_HEIGHT = 18;

    private MagicCircleRemoteListPayload data;
    private int page;
    private int hovered = -1;
    private boolean hoverStop;
    private boolean hoverImport;

    public MagicCircleRemoteScreen(MagicCircleRemoteListPayload data) {
        super(Component.translatable("gui.dimensional_mana.magiccircle.remote.title"));
        this.data = data;
    }

    /** 服务端重发清单（开关之后、或者指令改了状态）。 */
    public void update(MagicCircleRemoteListPayload payload) {
        this.data = payload;
        int pages = pageCount();
        if (page >= pages) page = Math.max(0, pages - 1);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x9E05060C);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        int radius = radius();
        List<MagicCircleRemoteListPayload.Entry> entries = data.entries();
        hovered = -1;
        hoverStop = false;

        graphics.drawCenteredString(font, title, cx, cy - radius - 46, 0xFFB4A0FF);
        graphics.drawCenteredString(font, summary(), cx, cy - radius - 32, 0xFF8FE9FF);

        int pages = pageCount();
        int start = page * PER_PAGE;
        for (int slot = 0; slot < PER_PAGE && start + slot < entries.size(); slot++) {
            int index = start + slot;
            MagicCircleRemoteListPayload.Entry entry = entries.get(index);
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * slot / PER_PAGE;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            boolean hover = insideCard(mouseX, mouseY, x, y);
            if (hover) hovered = index;
            graphics.fill(x - CARD_WIDTH / 2, y - CARD_HEIGHT / 2,
                    x + CARD_WIDTH / 2, y + CARD_HEIGHT / 2,
                    hover ? 0xE0342050 : entry.running() ? 0xC0223A2E : 0xB01A1426);
            graphics.renderOutline(x - CARD_WIDTH / 2, y - CARD_HEIGHT / 2, CARD_WIDTH, CARD_HEIGHT,
                    hover ? 0xFFFFD86B : entry.running() ? 0xFF8FFFC1 : 0xFF5A4770);
            String name = font.plainSubstrByWidth(entry.name(), CARD_WIDTH - 10);
            graphics.drawString(font, name, x - CARD_WIDTH / 2 + 5, y - CARD_HEIGHT / 2 + 4,
                    hover ? 0xFFFFE4A8 : 0xFFE9DCFF, false);
            Component state = Component.translatable(entry.running()
                    ? "gui.dimensional_mana.magiccircle.remote.running"
                    : "gui.dimensional_mana.magiccircle.remote.stopped");
            graphics.drawString(font, state, x - CARD_WIDTH / 2 + 5, y - CARD_HEIGHT / 2 + 14,
                    entry.running() ? 0xFF8FFFC1 : 0xFF9C8FB5, false);
            Component meta = Component.translatable("gui.dimensional_mana.magiccircle.remote.entry",
                    entry.layers(), entry.distance());
            graphics.drawString(font, meta, x - CARD_WIDTH / 2 + 5, y - CARD_HEIGHT / 2 + 23,
                    0xFF8FE9FF, false);
        }

        // 中间：全部停机。和停机键一样的效果，省得为了关阵再去找键位。
        boolean stopHover = mouseX >= cx - STOP_WIDTH / 2 && mouseX < cx + STOP_WIDTH / 2
                && mouseY >= cy - STOP_HEIGHT / 2 && mouseY < cy + STOP_HEIGHT / 2;
        hoverStop = stopHover;
        graphics.fill(cx - STOP_WIDTH / 2, cy - STOP_HEIGHT / 2, cx + STOP_WIDTH / 2, cy + STOP_HEIGHT / 2,
                stopHover ? 0xE06A2030 : 0xC02A1420);
        graphics.renderOutline(cx - STOP_WIDTH / 2, cy - STOP_HEIGHT / 2, STOP_WIDTH, STOP_HEIGHT,
                stopHover ? 0xFFFF9BA8 : 0xFF7A4A5A);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.magiccircle.remote.stop_all"),
                cx, cy - 4, stopHover ? 0xFFFFC0C8 : 0xFFE9A8B4);

        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.magiccircle.remote.empty"),
                    cx, cy + radius + 14, 0xFF9C8FB5);
            graphics.drawCenteredString(font,
                    Component.translatable("gui.dimensional_mana.magiccircle.remote.empty_hint"),
                    cx, cy + radius + 28, 0xFF7C6F94);
        } else if (pages > 1) {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.magiccircle.remote.page",
                    page + 1, pages, entries.size()), cx, cy + radius + 14, 0xFF8FE9FF);
        }
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.magiccircle.remote.hint"),
                cx, cy + radius + (entries.isEmpty() || pages > 1 ? 30 : 16), 0xFFB4A0FF);

        // 右上角：顺手打开法阵码导入面板（不用为了导入先退出轮盘、再摸出一张空白纸）。
        int importX = width - 8 - IMPORT_WIDTH;
        int importY = 8;
        hoverImport = mouseX >= importX && mouseX < importX + IMPORT_WIDTH
                && mouseY >= importY && mouseY < importY + IMPORT_HEIGHT;
        graphics.fill(importX, importY, importX + IMPORT_WIDTH, importY + IMPORT_HEIGHT,
                hoverImport ? 0xE04A2E6E : 0xB0120F20);
        graphics.renderOutline(importX, importY, IMPORT_WIDTH, IMPORT_HEIGHT,
                hoverImport ? 0xFFFFD86B : 0xFF5A4770);
        Component importLabel = Component.translatable("gui.dimensional_mana.magiccircle.remote.import");
        graphics.drawString(font, importLabel, importX + (IMPORT_WIDTH - font.width(importLabel)) / 2,
                importY + 5, hoverImport ? 0xFFFFE4A8 : 0xFFE9DCFF, false);
    }

    private Component summary() {
        return Component.translatable("gui.dimensional_mana.magiccircle.remote.summary",
                data.running(), data.limit(), data.levelBonus(), data.manaBonus());
    }

    private static boolean insideCard(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - CARD_WIDTH / 2.0 && mouseX < x + CARD_WIDTH / 2.0
                && mouseY >= y - CARD_HEIGHT / 2.0 && mouseY < y + CARD_HEIGHT / 2.0;
    }

    private int radius() {
        return (int) Math.max(70, Math.min(96, Math.min(width, height) * 0.34));
    }

    private int pageCount() {
        int count = data.entries().size();
        return count <= 0 ? 1 : (count + PER_PAGE - 1) / PER_PAGE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (hoverImport) {
            ClientMagicCircleScreens.openCodeImport();
            return true;
        }
        if (hoverStop) {
            PacketDistributor.sendToServer(MagicCircleNetActionPayload.stopAll());
            // 停机之后让卡片跟着变灰：单独要一份新清单，别指望服务端主动推。
            PacketDistributor.sendToServer(MagicCircleNetActionPayload.remoteRefresh());
            return true;
        }
        if (hovered >= 0 && hovered < data.entries().size()) {
            MagicCircleRemoteListPayload.Entry entry = data.entries().get(hovered);
            PacketDistributor.sendToServer(MagicCircleNetActionPayload.remoteToggle(
                    new BlockPos(entry.x(), entry.y(), entry.z())));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double verticalDelta) {
        int pages = pageCount();
        if (pages > 1) {
            page = Math.floorMod(page + (verticalDelta > 0 ? -1 : 1), pages);
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
