package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 法阵码导入面板：把一整段 {@code DM1-...} 的码粘进来，换成一张阵图。
 *
 * <p>为什么需要这个面板：原版聊天输入框最多只收 256 个字符（{@code ChatScreen} 里写死的），
 * 而法阵码动辄几百上千字，所以 {@code /dimensional_mana magiccircle code import <码>}
 * 这条指令根本粘不进去。</p>
 *
 * <p>这一版把「导入到底卡在哪」直接摆在面板上：</p>
 * <ul>
 *   <li>粘进来就<strong>本地试解一次</strong>，当场显示「码有效：《炎爆阵》32 块 · 2 层」，
 *       或者「还读不出来：法阵码解压失败」；</li>
 *   <li>显示<strong>手里那张载体的容量</strong>（纸 16 块 / 1 层、绸 64 块 / 3 层），
 *       一眼看出是不是载体太小；</li>
 *   <li>回车之后服务端的结果<strong>回到面板上</strong>：成功绿字、自动关掉；
 *       失败红字、<strong>面板不关、码不清</strong>，换一张容量够的载体再按一次回车就行。</li>
 * </ul>
 *
 * <p>操作：点「读剪贴板」或 Ctrl+V 读一次剪贴板，回车导入，Esc 或「取消」关掉。
 * 自动过滤掉分行、引号、{@code §} 颜色符之类的东西，只留法阵码用得上的字符。</p>
 */
public final class MagicCircleCodeScreen extends Screen {
    private static final int MAX_CHARS = MagicCircleCode.MAX_CHARS;
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 176;
    private static final int BOX_HEIGHT = 34;
    private static final int BUTTON_WIDTH = 92;
    private static final int BUTTON_HEIGHT = 18;
    /** 导入成功后停多少刻再自动关掉，让玩家看得到那句绿字。 */
    private static final int CLOSE_DELAY_TICKS = 25;
    /** 发出去之后等多久没回音就放开「正在导入」，免得丢包时面板像卡死一样。 */
    private static final int WAIT_TIMEOUT_TICKS = 120;
    private static final int COLOR_DIM = 0xFF9C8FB5;
    private static final int COLOR_OK = 0xFF8FFFC1;
    private static final int COLOR_WARN = 0xFFFFD86B;
    private static final int COLOR_BAD = 0xFFFF7B7B;

    private String text = "";
    /** 本地试解出来的结论（码有效 / 还差什么），每敲一个字符刷新一次。 */
    private Component preview;
    private boolean previewOk;
    /** 服务端回来的那句话；null 表示还没发过。 */
    private Component result;
    private boolean resultOk;
    /** 已经发给服务端、正在等回复：这期间不再重复发包。 */
    private boolean waiting;
    private int waitingTicks;
    private int closeIn;

    public MagicCircleCodeScreen() {
        super(Component.translatable("gui.dimensional_mana.magiccircle.code.title"));
        this.preview = Component.translatable("gui.dimensional_mana.magiccircle.code.note_idle");
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99040309);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        graphics.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT,
                0xF0161026, 0xF0061520);
        graphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF9E70D1);
        graphics.drawString(font, title, panelX + 8, panelY + 6, 0xFFFFD86B, false);
        String count = Component.translatable("gui.dimensional_mana.magiccircle.code.count",
                text.length(), MAX_CHARS).getString();
        graphics.drawString(font, count, panelX + PANEL_WIDTH - 8 - font.width(count), panelY + 6,
                0xFF9C8FB5, false);

        // 码太长，只画头尾：头看得见 DM1- 前缀，尾看得见刚粘进来的最后一个字符。
        int boxX = panelX + 8;
        int boxY = panelY + 18;
        int boxW = PANEL_WIDTH - 16;
        graphics.fill(boxX, boxY, boxX + boxW, boxY + BOX_HEIGHT, 0xFF120E1E);
        graphics.renderOutline(boxX, boxY, boxW, BOX_HEIGHT, 0xFF4A3B60);
        for (int line = 0; line < 2; line++) {
            String shown = displayLine(boxW - 8, line);
            graphics.drawString(font, shown, boxX + 4, boxY + 4 + line * 12, 0xFFE9DCFF, false);
        }

        // 服务端的结果（最多两行）：成功绿、失败红，失败时下面还会提示怎么补救。
        int lineY = panelY + 58;
        Component body = statusLine();
        int color = statusColor();
        for (FormattedCharSequence line : font.split(body, PANEL_WIDTH - 16)) {
            graphics.drawString(font, line, panelX + 8, lineY, color, false);
            lineY += 11;
        }

        graphics.drawString(font, carrierLine(), panelX + 8, panelY + 126, 0xFF8FE9FF, false);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.code.hint"),
                panelX + 8, panelY + 140, COLOR_DIM, false);

        int buttonY = panelY + PANEL_HEIGHT - BUTTON_HEIGHT - 8;
        drawButton(graphics, pasteRect(panelX, buttonY), mouseX, mouseY, waiting
                ? Component.translatable("gui.dimensional_mana.magiccircle.code.clear")
                : Component.translatable("gui.dimensional_mana.magiccircle.code.read_clipboard"));
        drawButton(graphics, importRect(panelX, buttonY), mouseX, mouseY,
                Component.translatable(waiting
                        ? "gui.dimensional_mana.magiccircle.code.importing"
                        : "gui.dimensional_mana.magiccircle.code.import"));
        drawButton(graphics, cancelRect(panelX, buttonY), mouseX, mouseY,
                Component.translatable("gui.dimensional_mana.magiccircle.code.cancel"));
    }

    /** 面板中间那两三行字：等回复时显示「正在导入」，有结果时显示结果，否则显示本地试解。 */
    private Component statusLine() {
        if (waiting) return Component.translatable("gui.dimensional_mana.magiccircle.code.importing");
        if (result != null) return result;
        return preview;
    }

    private int statusColor() {
        if (waiting) return COLOR_WARN;
        if (result != null) return resultOk ? COLOR_OK : COLOR_BAD;
        return previewOk ? COLOR_OK : COLOR_WARN;
    }

    /** 手里那张载体能装多少：新手最常卡在这里。 */
    private Component carrierLine() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(stack);
        if (carrier == null) {
            return Component.translatable("gui.dimensional_mana.magiccircle.code.hand_none");
        }
        if (carrier == MagicCircleBlueprint.Carrier.WORN) {
            return Component.translatable("gui.dimensional_mana.magiccircle.code.hand_worn");
        }
        return Component.translatable("gui.dimensional_mana.magiccircle.code.hand_carrier",
                carrier.fallbackName(), carrier.maxNodes(), carrier.maxLayers());
    }

    /** 第一行从头显示（看得见 DM1- 前缀），第二行从尾往前截（刚粘进来的最后一段一定看得见）。 */
    private String displayLine(int maxWidth, int line) {
        if (text.isEmpty()) {
            return line == 0 ? Component.translatable("gui.dimensional_mana.magiccircle.code.empty").getString() : "";
        }
        if (line == 0) {
            String head = text.length() > 60 ? text.substring(0, 60) + "…" : text;
            return font.plainSubstrByWidth(head, maxWidth);
        }
        if (text.length() <= 60) return "";
        String tail = text.substring(text.length() - 60);
        while (tail.length() > 1 && font.width(tail) > maxWidth) tail = tail.substring(1);
        return "…" + tail;
    }

    private void drawButton(GuiGraphics graphics, Rect rect, int mouseX, int mouseY, Component label) {
        boolean hover = rect.contains(mouseX, mouseY);
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, hover ? 0xAA4A2E6E : 0x66120F20);
        graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, hover ? 0xFFFFD86B : 0xFF5A4770);
        graphics.drawString(font, label, rect.x + (rect.w - font.width(label)) / 2, rect.y + 5,
                hover ? 0xFFFFE4A8 : 0xFFE9DCFF, false);
    }

    private Rect pasteRect(int panelX, int buttonY) {
        return new Rect(panelX + 8, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private Rect importRect(int panelX, int buttonY) {
        return new Rect(panelX + PANEL_WIDTH / 2 - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private Rect cancelRect(int panelX, int buttonY) {
        return new Rect(panelX + PANEL_WIDTH - 8 - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    @Override
    public void tick() {
        if (closeIn > 0 && --closeIn == 0) {
            onClose();
            return;
        }
        if (waiting && --waitingTicks <= 0) {
            // 服务端一直没回话（丢包 / 掉线）：别让面板永远停在「正在导入」。
            waiting = false;
            result = Component.translatable("gui.dimensional_mana.magiccircle.code.no_reply");
            resultOk = false;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int panelX = (width - PANEL_WIDTH) / 2;
        int buttonY = (height - PANEL_HEIGHT) / 2 + PANEL_HEIGHT - BUTTON_HEIGHT - 8;
        if (pasteRect(panelX, buttonY).contains(mouseX, mouseY)) {
            if (waiting) clearText();
            else readClipboard();
            return true;
        }
        if (importRect(panelX, buttonY).contains(mouseX, mouseY)) {
            submit();
            return true;
        }
        if (cancelRect(panelX, buttonY).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            submit();
            return true;
        }
        if (keyCode == InputConstants.KEY_BACKSPACE) {
            setText(backspace(text));
            return true;
        }
        if (Screen.hasControlDown() && keyCode == InputConstants.KEY_V) {
            readClipboard();
            return true;
        }
        if (Screen.hasControlDown() && keyCode == InputConstants.KEY_A) {
            clearText();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isCodeChar(codePoint) && text.length() < MAX_CHARS) {
            setText(text + codePoint);
        }
        return true;
    }

    /** 服务端把导入结果送回来了：成功显示绿字并稍后自动关掉，失败留着面板和码。 */
    public void onResult(boolean ok, Component message) {
        waiting = false;
        result = message;
        resultOk = ok;
        // 失败了就别再显示「正在导入」；成功时下面这几行会被绿字顶掉，码也没用了。
        if (ok) closeIn = CLOSE_DELAY_TICKS;
    }

    /** 读剪贴板并把码洗干净：只留 base64url 用得上的字符（分行、引号、{@code §} 颜色符全丢掉）。 */
    private void readClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        String cleaned = clean(clip);
        if (cleaned.isEmpty()) {
            result = Component.translatable("gui.dimensional_mana.magiccircle.code.clipboard_empty");
            resultOk = false;
            return;
        }
        if (cleaned.length() > MAX_CHARS) {
            result = Component.translatable("gui.dimensional_mana.magiccircle.code.clipboard_too_long", MAX_CHARS);
            resultOk = false;
            return;
        }
        result = null;
        setText(cleaned);
        if (!text.startsWith(MagicCircleCode.PREFIX)) {
            preview = Component.translatable("gui.dimensional_mana.magiccircle.code.note_prefix");
            previewOk = false;
        }
    }

    /** 洗掉不属于法阵码的字符（A-Z a-z 0-9 - _）。 */
    private static String clean(String raw) {
        if (raw == null) return "";
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean keep = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (keep) builder.append(c);
        }
        return builder.toString();
    }

    private void submit() {
        if (waiting) return;
        if (text.isEmpty()) {
            preview = Component.translatable("gui.dimensional_mana.magiccircle.code.empty");
            previewOk = false;
            return;
        }
        // 先在本地解一次：码本身坏了就当场说清楚，不用绕一圈服务端再回一句「解压失败」。
        MagicCircleCode.Result local = MagicCircleCode.decode(text);
        if (!local.ok()) {
            preview = Component.translatable("gui.dimensional_mana.magiccircle.code.note_invalid",
                    Component.translatable(local.error()));
            previewOk = false;
            result = null;
            return;
        }
        result = null;
        waiting = true;
        waitingTicks = WAIT_TIMEOUT_TICKS;
        PacketDistributor.sendToServer(MagicCircleNetActionPayload.importCode(text));
    }

    private void setText(String value) {
        text = value;
        result = null;
        refreshPreview();
    }

    private void clearText() {
        result = null;
        waiting = false;
        closeIn = 0;
        setText("");
        preview = Component.translatable("gui.dimensional_mana.magiccircle.code.note_idle");
        previewOk = false;
    }

    /** 本地试解：能解出来就报「多少块 / 几层 / 叫什么」，解不出来就报还缺什么。 */
    private void refreshPreview() {
        if (text.isEmpty()) {
            preview = Component.translatable("gui.dimensional_mana.magiccircle.code.note_idle");
            previewOk = false;
            return;
        }
        if (!text.startsWith(MagicCircleCode.PREFIX)) {
            preview = Component.translatable("gui.dimensional_mana.magiccircle.code.note_prefix");
            previewOk = false;
            return;
        }
        MagicCircleCode.Result local = MagicCircleCode.decode(text);
        if (local.ok()) {
            MagicCircleBlueprint blueprint = local.blueprint();
            preview = Component.translatable("gui.dimensional_mana.magiccircle.code.note_valid",
                    blueprint.name(), blueprint.nodeCount(), blueprint.filledLayerCount());
            previewOk = true;
        } else {
            preview = Component.translatable("gui.dimensional_mana.magiccircle.code.note_invalid",
                    Component.translatable(local.error()));
            previewOk = false;
        }
    }

    private static boolean isCodeChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_';
    }

    private static String backspace(String value) {
        if (value == null || value.isEmpty()) return "";
        int cut = Character.isLowSurrogate(value.charAt(value.length() - 1)) && value.length() >= 2
                ? value.length() - 2 : value.length() - 1;
        return value.substring(0, Math.max(0, cut));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** 和绘制台里一样的轻量矩形，省一个自己的类。 */
    private record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
}
