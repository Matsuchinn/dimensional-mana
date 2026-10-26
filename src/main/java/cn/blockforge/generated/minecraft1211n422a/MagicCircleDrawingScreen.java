package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 魔法阵绘制台界面（设计文档 4.2 / 4.3 / 5.4）：
 *
 * <ul>
 *   <li>中央网格画布：左键画线、右键擦块、滚轮调笔刷、Shift+滚轮换粉、Ctrl+滚轮缩放；</li>
 *   <li>左侧粉末盘：原版粉 / 魔晶粉 / 元素粉 / 染料 四个标签，未解锁灰显并提示等级，
 *       标签角上写「已解锁 / 总数」，还能切「常用 / 全部」；</li>
 *   <li>右侧识别候选：每次画完一笔，服务端识别出最多 3 个候选，点一下放置；</li>
 *   <li>识别失败黄条 + 手动框选降级：拖一个矩形，再指定功能块类型；</li>
 *   <li>底部消耗预览：启动 / 维持 / 触发、强度、复杂度、紊乱、效率、缺什么块。</li>
 * </ul>
 *
 * <p>画布上方有一条工具条，把操作分成两种模式：</p>
 *
 * <ul>
 *   <li><b>绘制模式</b>：左键画线、右键擦块，和以前一样；</li>
 *   <li><b>选择模式</b>：左键点一下选中一块、Shift+左键加选 / 取消，空白处拖框 = 框选一片，
 *       右键 = 复制 / 剪切 / 粘贴 / 编辑粉末 / 删除 / 全选 / 取消选择的菜单；
 *       快捷键跟常见编辑器一致：Ctrl+C / X / V / A / Z、Delete、E、Tab 切模式、Esc 取消。</li>
 * </ul>
 *
 * <p>编辑面板能改一块功能块四个粉槽里的粉：点「拿下来」把粉退回背包，点别的粉装上去。</p>
 *
 * <p>界面本身不做任何权威判断，所有改动都发给服务端，等服务端回同步包再刷新。</p>
 */
public final class MagicCircleDrawingScreen extends Screen {

    private static final int PANEL_MAX_WIDTH = 566;
    private static final int PANEL_MAX_HEIGHT = 344;
    private static final int HEADER_HEIGHT = 26;
    private static final int FOOTER_HEIGHT = 46;
    private static final int GAP = 6;
    private static final int TAB_HEIGHT = 13;
    private static final int POWDER_CELL_HEIGHT = 24;
    private static final int POWDER_COLUMNS = 4;
    private static final int NODE_ROW_HEIGHT = 13;
    private static final int MAX_VISIBLE_NODES = 4;
    private static final int SIDE_BUTTON_HEIGHT = 17;
    private static final int TOOLBAR_HEIGHT = 18;
    private static final int TOOLBAR_BUTTON_WIDTH = 40;
    private static final int MENU_WIDTH = 118;
    private static final int MENU_ITEM_HEIGHT = 13;
    private static final int EDITOR_WIDTH = 272;
    private static final int EDITOR_HEIGHT = 204;
    private static final int EDITOR_ROW_HEIGHT = 14;
    private static final int EDITOR_COLUMNS = 4;
    private static final int EDITOR_CELL_HEIGHT = 19;
    /** 法阵名最多多少个字（服务端同样会截断到 24）。 */
    private static final int MAX_NAME_CHARS = 24;
    private static final int NAMING_WIDTH = 244;
    private static final int NAMING_HEIGHT = 78;

    private static final int COLOR_TEXT = 0xFFE9DCFF;
    private static final int COLOR_DIM = 0xFF9C8FB5;
    private static final int COLOR_GOLD = 0xFFFFD86B;
    private static final int COLOR_AQUA = 0xFF8FE9FF;
    private static final int COLOR_DANGER = 0xFFFF7B7B;
    private static final int COLOR_OK = 0xFF8FFFC1;
    /** 连线：冷色 = 从核心走得通；深红 = 走不通；亮粉 = 按住 K 悬停到的那条（点一下断开）。 */
    private static final int COLOR_FLOW = 0xCC7FE3FF;
    private static final int COLOR_FLOW_BAD = 0xFFA8273C;
    private static final int COLOR_FLOW_CUT = 0xFFFF7B93;

    private static final int[] TYPE_COLORS = {
            0xFFFFD86B, 0xFF9BE8FF, 0xFFFF9B6B, 0xFFFF6B8B, 0xFF8FE2A0,
            0xFFB4A0FF, 0xFFE0B4FF, 0xFF9BB8FF, 0xFFFFA0D0, 0xFF9BFFCE,
            0xFFA8FF9B, 0xFFFFE49B, 0xFF9BD0FF
    };

    private final String networkId;
    private MagicCircleSyncPayload state;

    private int left, top, panelW, panelH;
    private int toolbarY;
    private int paletteX, paletteY, paletteW, paletteH;
    private int canvasAreaX, canvasAreaY, canvasAreaW, canvasAreaH;
    private int sideX, sideY, sideW, sideH;
    private int footerY;

    /** 画布能被 Ctrl+滚轮缩到多小 / 放多大。上限给到 8 倍：大阵（64×64 格）放大了才看得清一格。 */
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 8.0;
    /** 每滚一格乘 / 除多少：等比缩放，放大缩小来回滚不会越滚越慢。 */
    private static final double ZOOM_STEP = 1.25;
    /** 缩放 / 平移时，网格最少要有这么多像素留在画布区里，不会一下把阵甩出视野。 */
    private static final int VIEW_MARGIN = 24;

    private int categoryIndex;
    private boolean commonOnly;
    private int brush = 1;
    private double zoom = 1.0;
    private int panX, panY;
    private boolean panning;
    private double panStartX, panStartY;
    private int panOriginX, panOriginY;

    private boolean drawing;
    private final List<MagicCircleStroke.Point> raw = new ArrayList<>();
    private List<MagicCircleStroke.Point> preview = List.of();
    private boolean previewPatched;

    private boolean manualMode;
    private boolean manualDragging;
    private int frameX1, frameY1, frameX2, frameY2;
    private boolean typeMenuOpen;

    private int selectedNode = -1;
    private boolean clearArmed;

    // ---- 抄录前的命名弹框 ----
    /** 弹框开着时，界面其它输入全部让路，只收名字。 */
    private boolean naming;
    /**
     * 名字输入框。用原版 {@link EditBox}（聊天栏、铁砧改名用的是同一个），
     * 输入法打的中文、Ctrl+V 粘贴、方向键/选区都由它自己处理，不再手搓字符拼接。
     */
    private EditBox nameBox;

    // ---- 选择模式 ----
    private boolean selectMode;
    private final Set<Integer> selection = new LinkedHashSet<>();
    private boolean boxSelecting;
    private int boxX1, boxY1, boxX2, boxY2;
    private boolean contextMenu;
    private int contextMenuX, contextMenuY;
    /** 选择模式下按住一块：拖的是第几块、拖到画布哪儿、按下时鼠标相对块中心的偏移。 */
    private boolean draggingNode;
    private int dragIndex = -1;
    private int dragX, dragY;
    private double dragOffsetX, dragOffsetY;
    /** 左键按在一块上、但还没开始拖：超过 {@link #DRAG_THRESHOLD_PX} 像素才真的拖。 */
    private int pressNodeIndex = -1;
    private double pressNodeX, pressNodeY;
    private static final double DRAG_THRESHOLD_PX = 3.0;
    // ---- M 键移动模式 ----
    /**
     * 选择模式下点中一块、按 M：这一块就吸附到鼠标所指的格子上，鼠标移到哪儿它跟到哪儿，
     * 左键放下（真的挪过去），右键 / Esc 取消。整条路不按「拖动」算，只认鼠标位置和一次点击，
     * 所以缩放、平移、手速都不影响。
     */
    private boolean moveMode;
    /** 正跟着鼠标走的是第几块、当前吸附到哪一格、从哪一格出发。 */
    private int moveIndex = -1;
    private int moveX, moveY;
    private int moveFromX, moveFromY;
    /** 双击判定：上一次点在同一块上的时刻与下标。 */
    private long lastNodeClickAt;
    private int lastNodeClickIndex = -1;
    /** 两次点击落在同一块上、间隔小于这个毫秒数就算双击（双击也能直接开始拖）。 */
    private static final long DOUBLE_CLICK_MS = 320L;
    private int contextCellX = -1, contextCellY = -1;
    private boolean editorOpen;
    private int editorNode = -1;
    private MagicCirclePowders.Slot editorSlot = MagicCirclePowders.Slot.ELEMENT;
    /** 最近一次渲染时的鼠标位置：Ctrl+V 之类没有鼠标事件的快捷键要靠它定粘贴位置。 */
    private int pointerX, pointerY;

    private final Deque<String> recentPowders = new ArrayDeque<>();
    private final List<Component> hoverPowder = new ArrayList<>();
    /** 悬停功能块时显示的四槽粉末明细；优先级高于粉末盘提示。 */
    private final List<Component> hoverNode = new ArrayList<>();
    private int hoveredPowder = -1;
    private Component hint = Component.translatable("gui.dimensional_mana.magiccircle.hint.draw");

    // ---- 触发顺序 / 连线通不通（每帧按当前状态算一遍，状态没换就复用） ----
    private MagicCircleFlow.Result flow;
    private MagicCircleSyncPayload flowState;

    public MagicCircleDrawingScreen(MagicCircleSyncPayload payload) {
        super(Component.translatable("gui.dimensional_mana.magiccircle.title"));
        this.networkId = payload.networkId();
        this.state = payload;
        this.preview = MagicCircleStroke.decode(payload.strokeEcho());
    }

    public String networkId() { return networkId; }

    /** 服务端回了一份新状态：就地刷新，不要把界面关掉重开。 */
    public void update(MagicCircleSyncPayload payload) {
        int previousLayer = this.state.layer();
        this.state = payload;
        if (payload.layer() != previousLayer) {
            selection.clear();
            selectedNode = -1;
            boxSelecting = false;
            contextMenu = false;
            editorOpen = false;
            editorNode = -1;
        }
        this.preview = MagicCircleStroke.decode(payload.strokeEcho());
        this.previewPatched = false;
        if (this.preview.isEmpty()) this.raw.clear();
        if (selectedNode >= payload.nodes().size()) selectedNode = -1;
        selection.removeIf(index -> index < 0 || index >= payload.nodes().size());
        if (editorNode >= payload.nodes().size()) {
            editorNode = -1;
            editorOpen = false;
        }
        // 服务端回了一份新状态：正在拖的那一块下标可能已经不对了，拖动就此收手。
        draggingNode = false;
        dragIndex = -1;
        pressNodeIndex = -1;
        // 移动模式也一样：新状态下标可能变了，直接收手（放下时本来就已经退出）。
        moveMode = false;
        moveIndex = -1;
        lastNodeClickIndex = -1;
        clearArmed = false;
        flow = null;
        flowState = null;
    }

    @Override
    protected void init() {
        panelW = Math.max(320, Math.min(PANEL_MAX_WIDTH, width - 16));
        panelH = Math.max(220, Math.min(PANEL_MAX_HEIGHT, height - 16));
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        int contentTop = top + HEADER_HEIGHT;
        int footerTop = top + panelH - FOOTER_HEIGHT;
        int contentHeight = Math.max(120, footerTop - contentTop);
        paletteW = Mth.clamp(panelW * 28 / 100, 112, 158);
        int sideWidth = Mth.clamp(panelW * 24 / 100, 100, 152);
        paletteX = left + GAP;
        paletteY = contentTop;
        paletteH = contentHeight;
        sideX = left + panelW - GAP - sideWidth;
        sideY = contentTop;
        sideW = sideWidth;
        sideH = contentHeight;
        canvasAreaX = paletteX + paletteW + GAP;
        toolbarY = contentTop;
        canvasAreaY = contentTop + TOOLBAR_HEIGHT;
        canvasAreaW = Math.max(60, sideX - GAP - canvasAreaX);
        canvasAreaH = Math.max(60, contentHeight - TOOLBAR_HEIGHT);
        footerY = footerTop;
        // 窗口大小变了（F11 / 拖动窗口）时，正开着的命名框要跟着挪，输入的字不丢。
        if (naming) layoutNameBox();
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0040309);
        graphics.fillGradient(left, top, left + panelW, top + panelH, 0xF0161026, 0xF0061520);
        graphics.renderOutline(left, top, panelW, panelH, 0xFF9E70D1);
        graphics.renderOutline(left + 3, top + 3, panelW - 6, panelH - 6, 0xFF3F7B89);
        graphics.fill(left + 4, top + 4, left + panelW - 4, top + HEADER_HEIGHT, 0x80291A3F);
        graphics.fill(left + GAP, footerY - 2, left + panelW - GAP, footerY - 1, 0xFF3F7B89);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        pointerX = mouseX;
        pointerY = mouseY;
        hoveredPowder = -1;
        hoverPowder.clear();
        hoverNode.clear();
        renderHeader(graphics, mouseX, mouseY);
        renderToolbar(graphics, mouseX, mouseY);
        renderCanvas(graphics, mouseX, mouseY);
        renderPalette(graphics, mouseX, mouseY);
        renderSide(graphics, mouseX, mouseY);
        renderFooter(graphics);
        renderOverlays(graphics, mouseX, mouseY);
        // 弹框盖住整块面板时，底下的粉末 / 功能块提示就别再冒头了。
        if (naming) return;
        if (!hoverNode.isEmpty()) {
            graphics.renderComponentTooltip(font, hoverNode, mouseX, mouseY);
        } else if (!hoverPowder.isEmpty()) {
            graphics.renderComponentTooltip(font, hoverPowder, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, left + GAP + 2, top + 4, COLOR_GOLD, false);
        String subtitle = Component.translatable("gui.dimensional_mana.magiccircle.subtitle",
                state.baseW(), state.baseH(), state.canvasW(), state.canvasH(), state.layer() + 1).getString();
        graphics.drawString(font, subtitle, left + GAP + 2, top + 15, COLOR_DIM, false);
        String level = Component.translatable(state.freePowder()
                        ? "gui.dimensional_mana.magiccircle.level.creative"
                        : "gui.dimensional_mana.magiccircle.level",
                state.magicLevel(), state.unlockedPowders(), state.totalPowders()).getString();
        graphics.drawString(font, level, sideX - 4 - font.width(level), top + 4,
                state.freePowder() ? COLOR_GOLD : COLOR_AQUA, false);
        String owner = Component.translatable("gui.dimensional_mana.magiccircle.owner",
                state.ownerName().isEmpty() ? "-" : state.ownerName()).getString();
        graphics.drawString(font, owner, sideX - 4 - font.width(owner), top + 15, COLOR_DIM, false);
        Rect close = closeRect();
        boolean hover = close.contains(mouseX, mouseY);
        graphics.fill(close.x, close.y, close.x + close.w, close.y + close.h, hover ? 0xFF5A2340 : 0x662A1B3F);
        graphics.renderOutline(close.x, close.y, close.w, close.h, hover ? 0xFFFF9BA8 : 0xFF7A5A8A);
        graphics.drawCenteredString(font, Component.literal("×"), close.x + close.w / 2, close.y + 2,
                hover ? 0xFFFFC0C8 : COLOR_DIM);
        if (!state.editable()) {
            String readonly = Component.translatable("gui.dimensional_mana.magiccircle.readonly").getString();
            graphics.drawString(font, readonly, left + panelW - GAP - close.w - 6 - font.width(readonly),
                    top + 7, COLOR_DANGER, false);
        }
    }

    /** 画布上方的工具条：左边「绘制 / 选择」两个模式按钮，右边是选中数和剪贴板数。 */
    private void renderToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(canvasAreaX, toolbarY, canvasAreaX + canvasAreaW, toolbarY + TOOLBAR_HEIGHT, 0x55120F20);
        graphics.renderOutline(canvasAreaX, toolbarY, canvasAreaW, TOOLBAR_HEIGHT, 0xFF4A3B60);
        drawModeButton(graphics, modeDrawRect(), Component.translatable(
                "gui.dimensional_mana.magiccircle.mode.draw"), !selectMode, mouseX, mouseY);
        drawModeButton(graphics, modeSelectRect(), Component.translatable(
                "gui.dimensional_mana.magiccircle.mode.select"), selectMode, mouseX, mouseY);
        String info = Component.translatable("gui.dimensional_mana.magiccircle.selection.info",
                selection.size(), state.clipboardSize()).getString();
        // 右边先摆「缩放 ×2.0」：放大以后得能一眼看出现在多大，不然会以为节点丢了。
        String zoomLabel = Component.translatable("gui.dimensional_mana.magiccircle.zoom",
                String.format(Locale.ROOT, "×%.2f", zoom)).getString();
        int zoomWidth = font.width(zoomLabel);
        graphics.drawString(font, zoomLabel, canvasAreaX + canvasAreaW - 4 - zoomWidth, toolbarY + 5,
                zoom > 1.0 ? COLOR_GOLD : COLOR_DIM, false);
        int room = canvasAreaW - TOOLBAR_BUTTON_WIDTH * 2 - 16 - zoomWidth - 6;
        info = font.plainSubstrByWidth(info, Math.max(16, room));
        graphics.drawString(font, info, canvasAreaX + canvasAreaW - 10 - zoomWidth - font.width(info),
                toolbarY + 5, selectMode ? COLOR_AQUA : COLOR_DIM, false);
    }

    private void drawModeButton(GuiGraphics graphics, Rect rect, Component label, boolean active,
                                int mouseX, int mouseY) {
        boolean hover = rect.contains(mouseX, mouseY);
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                active ? 0xAA4A2E6E : hover ? 0x88302A46 : 0x44120F20);
        graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, active ? 0xFFFFD86B : 0xFF4A3B60);
        graphics.drawString(font, label, rect.x + (rect.w - font.width(label)) / 2, rect.y + 3,
                active ? 0xFFFFE4A8 : COLOR_DIM, false);
    }

    private void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(canvasAreaX, canvasAreaY, canvasAreaX + canvasAreaW, canvasAreaY + canvasAreaH, 0xE00A0814);
        graphics.renderOutline(canvasAreaX, canvasAreaY, canvasAreaW, canvasAreaH, 0xFF4A3B60);

        // 放大到 8 倍时网格会比画布区还大：裁一刀，别糊到左右两栏和底下的消耗预览上。
        graphics.enableScissor(canvasAreaX, canvasAreaY, canvasAreaX + canvasAreaW, canvasAreaY + canvasAreaH);
        try {
            renderCanvasContent(graphics, mouseX, mouseY);
        } finally {
            graphics.disableScissor();
        }
    }

    /** 画布里的东西（网格、连线、功能块、框选……）；外面已经套好裁剪，超出画布区的部分不显示。 */
    private void renderCanvasContent(GuiGraphics graphics, int mouseX, int mouseY) {
        double cell = cell();
        int gridLeft = gridLeft();
        int gridTop = gridTop();
        int gridRight = gridLeft + (int) Math.round(state.canvasW() * cell);
        int gridBottom = gridTop + (int) Math.round(state.canvasH() * cell);

        // 以前这里有一句「格子小于半像素就整块不画」，缩到 0.5× 以下画布直接消失。
        // 现在照画：网格线按「至少隔 3 像素一根」自动稀疏，缩得再小也还能看见阵和功能块。
        graphics.fill(gridLeft, gridTop, gridRight, gridBottom, 0xFF120E1E);

        int gridStep = 1;
        while (gridStep * cell < 3.0 && gridStep < 4096) gridStep *= 2;
        int lineColor = 0xFF231B38;
        int majorColor = 0xFF3A2C5C;
        int tileColor = 0xFF6A4E9E;
        for (int x = 0; x <= state.canvasW(); x += gridStep) {
            int px = gridLeft + (int) Math.round(x * cell);
            int color = x % MagicCircleNetwork.TILE == 0 ? tileColor : (x % 8 == 0 ? majorColor : lineColor);
            graphics.fill(px, gridTop, px + 1, gridBottom, color);
        }
        for (int y = 0; y <= state.canvasH(); y += gridStep) {
            int py = gridTop + (int) Math.round(y * cell);
            int color = y % MagicCircleNetwork.TILE == 0 ? tileColor : (y % 8 == 0 ? majorColor : lineColor);
            graphics.fill(gridLeft, py, gridRight, py + 1, color);
        }
        graphics.renderOutline(gridLeft, gridTop, Math.max(1, gridRight - gridLeft),
                Math.max(1, gridBottom - gridTop), 0xFF8A67C8);

        renderConnections(graphics, gridLeft, gridTop, cell, mouseX, mouseY);

        // 预览笔画：玩家画的时候就看到「会被识别的样子」。
        int thickness = Math.max(1, (int) Math.round(cell * 0.5)) + (brush - 1);
        int strokeColor = previewPatched ? 0xFFFF6B6B : 0xFF8FE9FF;
        for (MagicCircleStroke.Point point : preview) {
            int px = gridLeft + (int) Math.round(point.x() * cell);
            int py = gridTop + (int) Math.round(point.y() * cell);
            graphics.fill(px, py, px + thickness, py + thickness, strokeColor);
        }

        // 已放置的功能块。位置先记下来，第二遍再统一画「第几位触发」的小数字，
        // 免得后面的块把前面那块的数字盖住。
        // 移动模式里：正被 M 挪着的那一块，按鼠标所指的格（moveX / moveY）画，看起来就是跟着手走。
        // 只认「鼠标真的落在格子上」：画布区里有上下左右一圈空白边距（画布不是正方形、或者缩小观看时
        // 更明显），以前鼠标一进空白就把块硬塞到最上面那一行，看着像块自己贴到顶上了。
        if (moveMode && insideGrid(mouseX, mouseY)) {
            moveX = clampCellX(mouseX);
            moveY = clampCellY(mouseY);
        }
        int count = state.nodes().size();
        int[] centreX = new int[count];
        int[] centreY = new int[count];
        int blockSize = Math.max(6, (int) Math.round(cell * 2.0));
        for (int i = 0; i < count; i++) {
            MagicCircleSyncPayload.NodeView node = state.nodes().get(i);
            MagicCircleBlockType type = MagicCircleBlockType.byId(node.type());
            int size = blockSize;
            boolean draggingThis = draggingNode && i == dragIndex;
            boolean movingThis = moveMode && i == moveIndex;
            int drawCellX = draggingThis ? dragX : movingThis ? moveX : node.x();
            int drawCellY = draggingThis ? dragY : movingThis ? moveY : node.y();
            int cx = gridLeft + (int) Math.round(drawCellX * cell);
            int cy = gridTop + (int) Math.round(drawCellY * cell);
            centreX[i] = cx;
            centreY[i] = cy;
            int color = type == null ? 0xFFFFFFFF : TYPE_COLORS[type.ordinal() % TYPE_COLORS.length];
            boolean selected = selection.contains(i) || i == selectedNode;
            boolean inSelection = selection.contains(i);
            graphics.fill(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, 0xE6140F20);
            graphics.renderOutline(cx - size / 2, cy - size / 2, size, size, color);
            if (node.handDrawn()) {
                graphics.renderOutline(cx - size / 2 - 2, cy - size / 2 - 2, size + 4, size + 4, 0xFFFFC45C);
            }
            if (selected) {
                graphics.renderOutline(cx - size / 2 - 4, cy - size / 2 - 4, size + 8, size + 8,
                        inSelection ? 0xFFFFE49B : 0xFFFFFFFF);
            }
            if (inSelection && size >= 10) {
                graphics.fill(cx - size / 2, cy - size / 2, cx + size / 2, cy - size / 2 + 1, 0xFFFFE49B);
            }
            if (size >= 12 && type != null) {
                String label = type.fallbackName().substring(0, 1);
                graphics.drawCenteredString(font, label, cx, cy - 4, color);
            }
            // 鼠标压在功能块上：把这格四个粉槽都列出来（元素 / 语义 / 功率 / 染料）。
            if (!typeMenuOpen && !drawing && !manualMode && !editorOpen && !contextMenu && !draggingNode
                    && !moveMode
                    && mouseX >= cx - size / 2 - 2 && mouseX <= cx + size / 2 + 2
                    && mouseY >= cy - size / 2 - 2 && mouseY <= cy + size / 2 + 2) {
                hoverNode.clear();
                hoverNode.addAll(nodeTooltip(i, node));
            }
        }
        renderFlowOrders(graphics, centreX, centreY, blockSize);

        // M 键移动：目标格描绿框、原来那格留个黄框、中间连一条虚线，
        // 一眼看出「这块从哪儿挪到哪儿」，松开左键前都还能改。
        if (moveMode) {
            int tx = gridLeft + (int) Math.round(moveX * cell);
            int ty = gridTop + (int) Math.round(moveY * cell);
            int fx = gridLeft + (int) Math.round(moveFromX * cell);
            int fy = gridTop + (int) Math.round(moveFromY * cell);
            int half = Math.max(3, (int) Math.round(cell / 2));
            graphics.fill(tx - half, ty - half, tx + half, ty + half, 0x4469FF9B);
            graphics.renderOutline(tx - half, ty - half, Math.max(1, half * 2), Math.max(1, half * 2), 0xFF69FF9B);
            graphics.renderOutline(fx - blockSize / 2, fy - blockSize / 2, blockSize, blockSize, 0x99FFD86B);
            for (int step = 0; step < 14; step += 2) {
                double t0 = step / 14.0;
                double t1 = (step + 1) / 14.0;
                int ax = (int) Math.round(fx + (tx - fx) * t0);
                int ay = (int) Math.round(fy + (ty - fy) * t0);
                int bx = (int) Math.round(fx + (tx - fx) * t1);
                int by = (int) Math.round(fy + (ty - fy) * t1);
                graphics.fill(ax, ay, Math.max(ax + 1, bx), Math.max(ay + 1, by), 0xAA69FF9B);
            }
        }

        // 手动框选的矩形。
        if (manualMode && (manualDragging || frameX1 != frameX2 || frameY1 != frameY2)) {
            int x1 = gridLeft + (int) Math.round(Math.min(frameX1, frameX2) * cell);
            int y1 = gridTop + (int) Math.round(Math.min(frameY1, frameY2) * cell);
            int x2 = gridLeft + (int) Math.round((Math.max(frameX1, frameX2) + 1) * cell);
            int y2 = gridTop + (int) Math.round((Math.max(frameY1, frameY2) + 1) * cell);
            graphics.fill(x1, y1, x2, y2, 0x3346D9FF);
            graphics.renderOutline(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1), 0xFF6BE0FF);
        }

        // 框选：选择模式下拖出来的蚂蚁框。
        if (boxSelecting) {
            int x1 = gridLeft + (int) Math.round(Math.min(boxX1, boxX2) * cell);
            int y1 = gridTop + (int) Math.round(Math.min(boxY1, boxY2) * cell);
            int x2 = gridLeft + (int) Math.round((Math.max(boxX1, boxX2) + 1) * cell);
            int y2 = gridTop + (int) Math.round((Math.max(boxY1, boxY2) + 1) * cell);
            graphics.fill(x1, y1, x2, y2, 0x3333E0A0);
            graphics.renderOutline(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1), 0xFF7CFFC4);
        }

        if (manualMode) {
            String tip = Component.translatable("gui.dimensional_mana.magiccircle.manual.dragging").getString();
            graphics.drawString(font, tip, canvasAreaX + 4, canvasAreaY + 4, 0xFF9BE8FF, false);
        }
        if (moveMode) {
            // 移动模式最要紧的一句顶在画布上：左键放下、右键取消。
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.node.move.prompt")
                    .getString(), canvasAreaX + 4, canvasAreaY + 4, 0xFF69FF9B, false);
        }
        if (selectMode && !moveMode && !boxSelecting) {
            if (selection.isEmpty()) {
                // 两行小抄：一行讲怎么选，一行讲这块新加的三条手势（M 移动 / L 连线 / K 断线）。
                graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.select.tip")
                        .getString(), canvasAreaX + 4, canvasAreaY + 4, 0xFF7CFFC4, false);
                graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.select.tip2")
                        .getString(), canvasAreaX + 4, canvasAreaY + 15, 0xFFFFD86B, false);
            } else {
                // 选中块以后最该知道的一句：按 M 就能把它挪走。
                graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.select.tip3")
                        .getString(), canvasAreaX + 4, canvasAreaY + 4, 0xFFFFD86B, false);
            }
        }
        // 画布左下角图例：冷色线 = 通、深红 + ! = 不通、块右上角的数字 = 第几位触发。
        if (!state.links().isEmpty() || count > 1) {
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.flow.legend")
                    .getString(), canvasAreaX + 4, canvasAreaY + canvasAreaH - 11, 0xFF7FA0C0, false);
        }
    }

    /**
     * 画连线：先水平后垂直的 L 形，避免斜穿成一团蜘蛛网（文档 12.3）。
     *
     * <p>颜色就是「顺不顺」：冷色（青蓝）是从核心一路走下来的；深红 + 线上的小感叹号是走不通的
     * （输出往外接、接回核心、上游本来就没接上、或者绕成环）。线段上还有一个指向下游的小三角，
     * 指示这条线的方向，也就是触发顺序往哪边走。</p>
     *
     * <p>按住 K 时鼠标底下那条线变亮粉，提醒「点一下就是断开」。</p>
     */
    private void renderConnections(GuiGraphics graphics, int gridLeft, int gridTop, double cell,
                                   int mouseX, int mouseY) {
        MagicCircleFlow.Result flow = flow();
        int hoverLink = kHeld() ? nearestLink(mouseX, mouseY) : -1;
        int thickness = Math.max(1, Math.min(3, (int) Math.round(cell * 0.12)));
        for (int linkIndex = 0; linkIndex < state.links().size(); linkIndex++) {
            MagicCircleSyncPayload.LinkView link = state.links().get(linkIndex);
            if (link.from() < 0 || link.to() < 0
                    || link.from() >= state.nodes().size() || link.to() >= state.nodes().size()) continue;
            MagicCircleSyncPayload.NodeView from = state.nodes().get(link.from());
            MagicCircleSyncPayload.NodeView to = state.nodes().get(link.to());
            int x1 = nodePixelX(from, gridLeft, cell);
            int y1 = nodePixelY(from, gridTop, cell);
            int x2 = nodePixelX(to, gridLeft, cell);
            int y2 = nodePixelY(to, gridTop, cell);
            boolean flows = flow.flows(linkIndex);
            int linkColor = linkIndex == hoverLink ? COLOR_FLOW_CUT : flows ? COLOR_FLOW : COLOR_FLOW_BAD;
            int half = thickness / 2;
            graphics.fill(Math.min(x1, x2), y1 - half, Math.max(x1, x2) + 1, y1 - half + thickness, linkColor);
            graphics.fill(x2 - half, Math.min(y1, y2), x2 - half + thickness, Math.max(y1, y2) + 1, linkColor);
            // 小三角：告诉我们「水往哪边流」。放在整条 L 折线 20% 的位置。
            int[] arrow = pointOnLink(x1, y1, x2, y2, 0.2);
            if (arrow[2] != 0 || arrow[3] != 0) {
                drawFlowArrow(graphics, arrow[0], arrow[1], arrow[2], arrow[3], linkColor);
            }
            if (!flows && !flow.issue(linkIndex).broken()) {
                int[] middle = pointOnLink(x1, y1, x2, y2, 0.5);
                drawIssueBadge(graphics, middle[0], middle[1]);
            }
        }
        if (hoverLink >= 0) {
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.node.link.cut"),
                    mouseX + 8, mouseY - 12, 0xFFFF9BA8, false);
            String reason = flow.issue(hoverLink).translationKey();
            if (!reason.isEmpty()) {
                graphics.drawString(font, Component.translatable(reason).getString(),
                        mouseX + 8, mouseY - 1, 0xFFFF7B93, false);
            }
        }
    }

    /**
     * 每块功能块右上角的小数字：第几位触发（核心是 1，顺着连线往外一层层排）。
     * 同一层并排的几块拿的是同一个数字——它们同时触发，所以看到两个「2」不是写错。
     * 没接进主链的块画一个红色「!」，和它前面那条红线的意思对上。
     */
    private void renderFlowOrders(GuiGraphics graphics, int[] centreX, int[] centreY, int blockSize) {
        MagicCircleFlow.Result flow = flow();
        int size = centreX.length;
        for (int i = 0; i < size; i++) {
            int order = flow.order(i);
            String text = order > 0 ? String.valueOf(order) : "!";
            int color = order > 0 ? COLOR_FLOW : 0xFFFF5A6E;
            int width = font.width(text);
            int x = centreX[i] + blockSize / 2 + 1;
            int y = centreY[i] - blockSize / 2 - 9;
            graphics.fill(x - 1, y - 1, x + width + 1, y + 9, 0xC8100A18);
            graphics.drawString(font, text, x, y, color, false);
        }
    }

    /** 从 (x1,y1) 走到 (x2,y2) 这条 L 折线上，按长度比例取一个点，并给出该处的前进方向。 */
    private static int[] pointOnLink(int x1, int y1, int x2, int y2, double fraction) {
        double first = Math.abs(x2 - x1);
        double second = Math.abs(y2 - y1);
        double total = first + second;
        if (total < 1.0) return new int[]{x2, y2, 0, 0};
        double target = total * Mth.clamp(fraction, 0.0, 1.0);
        if (first > 0 && target <= first) {
            int dx = Integer.signum(x2 - x1);
            return new int[]{(int) Math.round(x1 + (x2 - x1) * (target / first)), y1, dx, 0};
        }
        if (second <= 0) return new int[]{x2, y2, Integer.signum(x2 - x1), 0};
        double t = Math.min(1.0, (target - first) / second);
        return new int[]{x2, (int) Math.round(y1 + (y2 - y1) * t), 0, Integer.signum(y2 - y1)};
    }

    /** 线上的小三角箭头，指向「下游」。dx / dy 只有一个非 0。 */
    private void drawFlowArrow(GuiGraphics graphics, int x, int y, int dx, int dy, int color) {
        for (int row = 0; row < 3; row++) {
            int half = 3 - row;
            if (dx != 0) {
                int px = x + dx * row;
                graphics.fill(px, y - half, px + 1, y + half + 1, color);
            } else if (dy != 0) {
                int py = y + dy * row;
                graphics.fill(x - half, py, x + half + 1, py + 1, color);
            }
        }
    }

    /** 不通的那条线中间的小感叹号：深红底 + 亮边，缩小时也一眼能看见。 */
    private void drawIssueBadge(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 3, y - 5, x + 4, y + 4, 0xF03A0D16);
        graphics.renderOutline(x - 3, y - 5, 8, 9, 0xFFFF6B82);
        graphics.drawString(font, "!", x - 1, y - 4, 0xFFFFD2D9, false);
    }

    /** 触发顺序 / 通不通：状态没换就复用上一次的结果，省得每帧重算。 */
    private MagicCircleFlow.Result flow() {
        if (flow == null || flowState != state) {
            flow = MagicCircleFlow.compute(state.nodes(), state.links());
            flowState = state;
        }
        return flow;
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(paletteX, paletteY, paletteX + paletteW, paletteY + paletteH, 0x33120F20);
        graphics.renderOutline(paletteX, paletteY, paletteW, paletteH, 0xFF4A3B60);

        MagicCirclePowders.Category[] categories = MagicCirclePowders.Category.values();
        for (int i = 0; i < categories.length; i++) {
            Rect rect = tabRect(i);
            boolean active = i == categoryIndex;
            boolean hover = rect.contains(mouseX, mouseY);
            int unlocked = unlockedCount(categories[i]);
            int total = MagicCirclePowders.byCategory(categories[i]).size();
            graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                    active ? 0xB03D285D : hover ? 0x8A29384A : 0x66120F20);
            graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, active ? 0xFFD9A8FF : 0xFF5A4770);
            String label = Component.translatable(categories[i].translationKey()).getString()
                    + " " + unlocked + "/" + total;
            graphics.drawString(font, label, rect.x + 4, rect.y + 2,
                    active ? 0xFFF4ECFF : unlocked == 0 ? 0xFF6E647E : COLOR_DIM, false);
        }

        Rect common = commonRect();
        Rect all = allRect();
        drawSmallButton(graphics, common, Component.translatable("gui.dimensional_mana.magiccircle.common"),
                !commonOnly, mouseX, mouseY);
        drawSmallButton(graphics, all, Component.translatable("gui.dimensional_mana.magiccircle.all"),
                commonOnly, mouseX, mouseY);

        List<MagicCircleSyncPayload.PowderView> visible = visiblePowders(categories[categoryIndex]);
        for (int i = 0; i < visible.size(); i++) {
            Rect rect = powderRect(i);
            if (rect.y + POWDER_CELL_HEIGHT > paletteY + paletteH - 24) break;
            MagicCircleSyncPayload.PowderView view = visible.get(i);
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(view.itemId());
            if (powder == null) continue;
            ItemStack stack = powder.stack();
            boolean selected = isSelected(powder);
            boolean hover = rect.contains(mouseX, mouseY);
            graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                    selected ? 0xAA4A2E6E : hover ? 0x88302A46 : 0x55120F20);
            graphics.renderOutline(rect.x, rect.y, rect.w, rect.h,
                    selected ? 0xFFFFD86B : hover ? 0xFFB79BDE : 0xFF3A2C52);
            if (stack.isEmpty()) {
                graphics.drawCenteredString(font, "?", rect.x + rect.w / 2, rect.y + 4, COLOR_DIM);
            } else {
                graphics.renderItem(stack, rect.x + (rect.w - 16) / 2, rect.y + 1);
                int amount = countInInventory(stack.getItem());
                if (state.freePowder()) {
                    // 创造模式：粉不消耗，格子角上画个 ∞ 表示不用备料
                    graphics.drawString(font, "∞", rect.x + rect.w - 9, rect.y + rect.h - 8, COLOR_AQUA, false);
                } else if (amount > 0) {
                    graphics.drawString(font, String.valueOf(amount), rect.x + rect.w - 8, rect.y + rect.h - 8,
                            view.unlocked() ? 0xFFE7DFFF : 0xFF7A7188, false);
                }
            }
            if (!view.unlocked()) {
                graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0x99000000);
            }
            if (hover) {
                hoveredPowder = i;
                hoverPowder.add(powder.name().copy().withStyle(view.unlocked()
                        ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.GRAY));
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.powder.slot",
                        Component.translatable(powder.slot().translationKey())).copy()
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.powder.coefficient",
                        String.format(Locale.ROOT, "%.2f", powder.coefficient()))
                        .copy().withStyle(net.minecraft.ChatFormatting.GOLD));
                hoverPowder.add((state.freePowder()
                        ? Component.translatable("gui.dimensional_mana.magiccircle.powder.free")
                        : Component.translatable("gui.dimensional_mana.magiccircle.powder.amount",
                        countInInventory(stack.getItem())))
                        .copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                if (!view.unlocked()) {
                    hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.powder.locked",
                            view.requiredLevel()).copy().withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
        }

        // 底部四个粉槽：当前笔画会用哪几种粉。
        MagicCirclePowders.Slot[] slots = MagicCirclePowders.Slot.values();
        for (int i = 0; i < slots.length; i++) {
            Rect rect = slotRect(i);
            String itemId = selectedItem(slots[i].id());
            graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0x55120F20);
            graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, 0xFF4A3B60);
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(itemId);
            if (powder != null) {
                graphics.renderItem(powder.stack(), rect.x + 1, rect.y + 1);
            } else {
                graphics.drawCenteredString(font, "-", rect.x + rect.w / 2, rect.y + 4, 0xFF5F5470);
            }
            if (rect.contains(mouseX, mouseY)) {
                hoverPowder.add(Component.translatable(slots[i].translationKey()).copy()
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                hoverPowder.add((powder == null
                        ? Component.translatable("gui.dimensional_mana.magiccircle.slot.empty")
                        : powder.name()).copy().withStyle(net.minecraft.ChatFormatting.GRAY));
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.slot.clear").copy()
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
    }

    private void renderSide(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0x33120F20);
        graphics.renderOutline(sideX, sideY, sideW, sideH, 0xFF4A3B60);

        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.candidate.title"),
                sideX + 4, sideY + 3, COLOR_GOLD, false);
        int candidateTop = sideY + 16;
        if (!state.candidates().isEmpty()) {
            for (int i = 0; i < state.candidates().size(); i++) {
                Rect rect = candidateRect(i);
                MagicCircleSyncPayload.CandidateView view = state.candidates().get(i);
                MagicCircleGlyph glyph = MagicCircleGlyph.byId(view.glyph());
                if (glyph == null) continue;
                boolean hover = rect.contains(mouseX, mouseY);
                graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, hover ? 0xAA3D2A5C : 0x88201730);
                graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, hover ? 0xFFE9C8FF : 0xFF6A4E8E);
                graphics.drawString(font, Component.translatable(glyph.translationKey()),
                        rect.x + 4, rect.y + 2, 0xFFF4ECFF, false);
                String score = Math.round(view.score() * 100) + "%";
                graphics.drawString(font, score, rect.x + rect.w - 4 - font.width(score), rect.y + 2,
                        scoreColor(view.score()), false);
                graphics.drawString(font, Component.translatable(glyph.blockType().translationKey()),
                        rect.x + 4, rect.y + 12, 0xFFBDA9DE, false);
            }
        } else {
            boolean failed = !preview.isEmpty() || !state.strokeEcho().isEmpty();
            Component message = Component.translatable(failed
                    ? "gui.dimensional_mana.magiccircle.candidate.failed"
                    : "gui.dimensional_mana.magiccircle.candidate.none");
            graphics.drawWordWrap(font, message, sideX + 5, candidateTop + 2, sideW - 10,
                    failed ? 0xFFFFD86B : COLOR_DIM);
        }

        int nodeTop = candidateTop + 84;
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.nodes.title",
                state.nodes().size()), sideX + 4, nodeTop, COLOR_GOLD, false);
        if (state.nodes().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.nodes.empty"),
                    sideX + 4, nodeTop + 13, COLOR_DIM, false);
        } else {
            int start = Math.max(0, Math.min(selectedNode - MAX_VISIBLE_NODES / 2,
                    state.nodes().size() - MAX_VISIBLE_NODES));
            for (int row = 0; row < MAX_VISIBLE_NODES && start + row < state.nodes().size(); row++) {
                int index = start + row;
                Rect rect = nodeRowRect(nodeTop, row);
                MagicCircleSyncPayload.NodeView node = state.nodes().get(index);
                MagicCircleBlockType type = MagicCircleBlockType.byId(node.type());
                boolean hover = rect.contains(mouseX, mouseY);
                boolean active = index == selectedNode || selection.contains(index);
                graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                        active ? 0xAA4A2E6E : hover ? 0x88302A46 : 0x44120F20);
                String label = (type == null ? node.type() : type.fallbackName())
                        + " " + MagicCirclePowders.shortName(node.element())
                        + "/" + MagicCirclePowders.shortName(node.semantic())
                        + "/" + MagicCirclePowders.shortName(node.power())
                        + "/" + MagicCirclePowders.shortName(node.dye())
                        + (node.handDrawn() ? "·手" : "");
                label = font.plainSubstrByWidth(label, Math.max(10, rect.w - 6));
                graphics.drawString(font, label, rect.x + 3, rect.y + 2, active ? 0xFFFFE4A8 : COLOR_DIM, false);
                if (hover) {
                    hoverNode.clear();
                    hoverNode.addAll(nodeTooltip(index, node));
                }
            }
            if (state.nodes().size() > MAX_VISIBLE_NODES) {
                String more = "+" + (state.nodes().size() - MAX_VISIBLE_NODES);
                graphics.drawString(font, more, sideX + sideW - 4 - font.width(more),
                        nodeTop + 13 + MAX_VISIBLE_NODES * NODE_ROW_HEIGHT - 2, 0xFF8A7BA5, false);
            }
        }

        int buttonTop = sideY + sideH - SIDE_BUTTON_HEIGHT * 6 - 4;
        drawSideButton(graphics, redrawRect(), Component.translatable("gui.dimensional_mana.magiccircle.button.redraw"),
                mouseX, mouseY, !raw.isEmpty() || !preview.isEmpty());
        drawSideButton(graphics, undoRect(), Component.translatable("gui.dimensional_mana.magiccircle.button.undo"),
                mouseX, mouseY, state.editable() && !state.nodes().isEmpty());
        drawSideButton(graphics, clearRect(), Component.translatable(clearArmed
                        ? "gui.dimensional_mana.magiccircle.button.clear_confirm"
                        : "gui.dimensional_mana.magiccircle.button.clear"),
                mouseX, mouseY, state.editable() && !state.nodes().isEmpty());
        drawSideButton(graphics, deleteRect(), Component.translatable("gui.dimensional_mana.magiccircle.button.delete"),
                mouseX, mouseY, state.editable() && (selectedNode >= 0 || !selection.isEmpty()));
        drawSideButton(graphics, manualRect(), Component.translatable(manualMode
                        ? "gui.dimensional_mana.magiccircle.button.manual_cancel"
                        : "gui.dimensional_mana.magiccircle.button.manual"),
                mouseX, mouseY, state.editable());
        drawSideButton(graphics, transcribeRect(),
                Component.translatable("gui.dimensional_mana.magiccircle.button.transcribe"),
                mouseX, mouseY, state.editable() && !state.nodes().isEmpty());
    }

    /** 悬停一个功能块时显示：第几层、什么块、坐标、第几位触发，以及四个粉槽各放了什么。 */
    private List<Component> nodeTooltip(int index, MagicCircleSyncPayload.NodeView node) {
        List<Component> lines = new ArrayList<>();
        MagicCircleBlockType type = MagicCircleBlockType.byId(node.type());
        lines.add(Component.translatable("tooltip.dimensional_mana.magiccircle.node.header",
                        state.layer() + 1, type == null ? node.type() : type.fallbackName(), node.x(), node.y())
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        int order = flow().order(index);
        lines.add(order > 0
                ? Component.translatable("tooltip.dimensional_mana.magiccircle.node.order", order)
                        .withStyle(net.minecraft.ChatFormatting.AQUA)
                : Component.translatable("tooltip.dimensional_mana.magiccircle.node.order.none")
                        .withStyle(net.minecraft.ChatFormatting.RED));
        // 同一层还有别的块：说一句「你们是一起触发的」，免得看见两个「2」以为是显示错了。
        if (order > 0 && layerSiblings(order) > 1) {
            lines.add(Component.translatable("tooltip.dimensional_mana.magiccircle.node.order.same_layer", order)
                    .withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        }
        lines.add(slotLine("element", node.element()));
        lines.add(slotLine("semantic", node.semantic()));
        lines.add(slotLine("power", node.power()));
        lines.add(slotLine("dye", node.dye()));
        if (node.handDrawn()) {
            lines.add(Component.translatable("tooltip.dimensional_mana.magiccircle.node.manual")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        return lines;
    }

    /** 和这块同一层（同一个序号）的块有几个：大于 1 说明它们是并列、同时触发的。 */
    private int layerSiblings(int order) {
        if (order <= 0) return 0;
        MagicCircleFlow.Result result = flow();
        int count = 0;
        for (int i = 0; i < state.nodes().size(); i++) {
            if (result.order(i) == order) count++;
        }
        return count;
    }

    private Component slotLine(String slot, String itemId) {
        MagicCirclePowders.Powder powder = MagicCirclePowders.get(itemId);
        Component value = powder == null
                ? Component.translatable("tooltip.dimensional_mana.magiccircle.node.empty")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                : powder.name().copy().withStyle(net.minecraft.ChatFormatting.AQUA);
        return Component.translatable("gui.dimensional_mana.magiccircle.slot." + slot)
                .append(Component.literal("："))
                .append(value)
                .withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    private void renderFooter(GuiGraphics graphics) {
        graphics.fill(left + GAP, footerY, left + panelW - GAP, footerY + FOOTER_HEIGHT - 2, 0x66120F20);
        graphics.renderOutline(left + GAP, footerY, panelW - GAP * 2, FOOTER_HEIGHT - 2, 0xFF3F7B89);
        String cost = Component.translatable("gui.dimensional_mana.magiccircle.footer.cost",
                state.startMana(), state.upkeepMana(), state.triggerMana()).getString();
        graphics.drawString(font, cost, left + GAP + 4, footerY + 2, COLOR_GOLD, false);
        String stats = Component.translatable("gui.dimensional_mana.magiccircle.footer.stats",
                String.format(Locale.ROOT, "%.2f", state.strength()), state.complexity(),
                state.disorder(), String.format(Locale.ROOT, "%.2f", state.efficiency())).getString();
        graphics.drawString(font, stats, left + GAP + 4, footerY + 13, 0xFFC9A8FF, false);
        if (state.problems().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.footer.ok"),
                    left + GAP + 4, footerY + 24, COLOR_OK, false);
        } else {
            StringBuilder builder = new StringBuilder();
            for (String key : state.problems()) {
                if (builder.length() > 0) builder.append("、");
                builder.append(Component.translatable(key).getString());
            }
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.footer.problem",
                    builder.toString()), left + GAP + 4, footerY + 24, COLOR_DANGER, false);
        }
        graphics.drawString(font, hint, left + GAP + 4, footerY + 35, COLOR_DIM, false);
    }

    private void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY) {
        // 命名弹框是最上面一层：开着的时候不用管别的浮层。
        if (naming) {
            renderNaming(graphics, mouseX, mouseY);
            return;
        }
        renderContextMenu(graphics, mouseX, mouseY);
        renderEditor(graphics, mouseX, mouseY);
        if (!typeMenuOpen) return;
        int menuW = 168;
        int menuH = 96;
        int menuX = canvasAreaX + (canvasAreaW - menuW) / 2;
        int menuY = canvasAreaY + (canvasAreaH - menuH) / 2;
        graphics.fill(menuX - 3, menuY - 3, menuX + menuW + 3, menuY + menuH + 3, 0xF0100C1E);
        graphics.renderOutline(menuX - 3, menuY - 3, menuW + 6, menuH + 6, 0xFFD9A8FF);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.manual.type"),
                menuX + 2, menuY + 2, COLOR_GOLD, false);
        MagicCircleBlockType[] types = MagicCircleBlockType.values();
        for (int i = 0; i < types.length; i++) {
            Rect rect = typeRect(i);
            boolean hover = rect.contains(mouseX, mouseY);
            graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, hover ? 0xAA4A2E6E : 0x55211A33);
            graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, hover ? 0xFFFFD86B : 0xFF5A4770);
            graphics.drawString(font, Component.translatable(types[i].translationKey()),
                    rect.x + 3, rect.y + 2, hover ? 0xFFFFE4A8 : COLOR_TEXT, false);
        }
    }

    // ------------------------------------------------------------------ 抄录命名弹框

    /** 点「抄录」先弹个框问名字：直接确认就用默认名，不用每次都敲字。 */
    private void openNaming() {
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        if (state.nodes().isEmpty()) {
            hint = Component.translatable("message.dimensional_mana.magiccircle.blueprint.nothing_to_copy");
            return;
        }
        naming = true;
        layoutNameBox();
        if (nameBox != null) nameBox.setValue("");
        hint = Component.translatable("gui.dimensional_mana.magiccircle.naming.hint");
    }

    /**
     * 把名字输入框摆到弹框里的输入框位置上，并让它拿到焦点。
     *
     * <p>用原版 {@link EditBox} 而不是自己拼字符串：中文是在「输入法提交」那一刻整段送进来的，
     * 原版这个控件（聊天栏、铁砧改名、告示牌都在用）把输入法、粘贴、方向键、选区全处理好了。</p>
     */
    private void layoutNameBox() {
        if (!naming) {
            nameBox = null;
            return;
        }
        String previous = nameBox == null ? "" : nameBox.getValue();
        Rect field = namingFieldRect();
        EditBox box = new EditBox(font, field.x + 3, field.y + 4, field.w - 6, 9,
                Component.translatable("gui.dimensional_mana.magiccircle.naming.placeholder"));
        box.setBordered(false);
        box.setMaxLength(MAX_NAME_CHARS);
        box.setTextColor(COLOR_TEXT);
        // 控制字符和 § 颜色符当场不收（服务端还会再洗一遍），中文、表情照收不误。
        box.setFilter(text -> {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 0x20 || c == 0x7F || c == '§') return false;
            }
            return true;
        });
        box.setValue(previous);
        box.setFocused(true);
        box.moveCursorToEnd(false);
        nameBox = box;
    }

    /** 名字框里现在写的字（去掉首尾空白）。 */
    private String namingValue() {
        String value = nameBox == null ? "" : nameBox.getValue();
        String trimmed = value.trim();
        // 只有空格时当作「留空」，交给服务端按元素起默认名。
        return trimmed.isEmpty() ? "" : trimmed;
    }

    /** 确认：把名字一起发给服务端；空名字由服务端按元素自动起名。 */
    private void confirmNaming() {
        if (!naming) return;
        String name = namingValue();
        naming = false;
        if (nameBox != null) nameBox.setFocused(false);
        nameBox = null;
        PacketDistributor.sendToServer(MagicCircleActionPayload.transcribe(name));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.transcribing");
    }

    private void cancelNaming() {
        naming = false;
        if (nameBox != null) nameBox.setFocused(false);
        nameBox = null;
        hint = Component.translatable("gui.dimensional_mana.magiccircle.hint.draw");
    }

    private void renderNaming(GuiGraphics graphics, int mouseX, int mouseY) {
        // 整块盖住底下的界面，明确「先把名字定下来」。
        graphics.fill(left, top, left + panelW, top + panelH, 0xC8040309);
        Rect box = namingRect();
        graphics.fill(box.x - 2, box.y - 2, box.x + box.w + 2, box.y + box.h + 2, 0xF0100C1E);
        graphics.renderOutline(box.x - 2, box.y - 2, box.w + 4, box.h + 4, 0xFFD9A8FF);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.naming.title"),
                box.x + 10, box.y + 8, COLOR_GOLD, false);

        Rect field = namingFieldRect();
        graphics.fill(field.x, field.y, field.x + field.w, field.y + field.h, 0xFF140E22);
        graphics.renderOutline(field.x, field.y, field.w, field.h, 0xFF7A5F9E);
        if (nameBox != null) {
            // 空的时候自己画一行灰字提示（原版输入框只在没焦点时才画 hint，这里一直有焦点）。
            if (nameBox.getValue().isEmpty()) {
                graphics.drawString(font,
                        Component.translatable("gui.dimensional_mana.magiccircle.naming.placeholder"),
                        field.x + 4, field.y + 4, COLOR_DIM, false);
            }
            nameBox.render(graphics, mouseX, mouseY, 0.0F);
        }

        // 底下的提示条被这层盖住了，操作说明得画在框里。
        graphics.drawString(font, hint, box.x + 10, box.y + 42, COLOR_DIM, false);

        Rect confirm = namingConfirmRect();
        boolean confirmHover = confirm.contains(mouseX, mouseY);
        graphics.fill(confirm.x, confirm.y, confirm.x + confirm.w, confirm.y + confirm.h,
                confirmHover ? 0xAA3A6E52 : 0x66203A2C);
        graphics.renderOutline(confirm.x, confirm.y, confirm.w, confirm.h,
                confirmHover ? 0xFF8FFFC1 : 0xFF4E7A62);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.naming.confirm"),
                confirm.x + 6, confirm.y + 4, confirmHover ? 0xFFCFFFE2 : COLOR_TEXT, false);

        Rect cancel = namingCancelRect();
        boolean cancelHover = cancel.contains(mouseX, mouseY);
        graphics.fill(cancel.x, cancel.y, cancel.x + cancel.w, cancel.y + cancel.h,
                cancelHover ? 0xAA5A2340 : 0x662C1A28);
        graphics.renderOutline(cancel.x, cancel.y, cancel.w, cancel.h,
                cancelHover ? 0xFFFF9BB0 : 0xFF7A4E62);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.naming.cancel"),
                cancel.x + 6, cancel.y + 4, cancelHover ? 0xFFFFD3DC : COLOR_TEXT, false);

        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.naming.default"),
                box.x + 10, confirm.y + 4, COLOR_DIM, false);
    }

    private Rect namingRect() {
        int w = Math.min(NAMING_WIDTH, Math.max(160, panelW - 20));
        int h = NAMING_HEIGHT;
        return new Rect(left + (panelW - w) / 2, top + (panelH - h) / 2, w, h);
    }

    private Rect namingFieldRect() {
        Rect box = namingRect();
        return new Rect(box.x + 10, box.y + 22, box.w - 20, 16);
    }

    private Rect namingConfirmRect() {
        Rect box = namingRect();
        return new Rect(box.x + box.w - 10 - 62, box.y + box.h - 22, 62, 15);
    }

    private Rect namingCancelRect() {
        Rect confirm = namingConfirmRect();
        return new Rect(confirm.x - 8 - 52, confirm.y, 52, 15);
    }

    // ------------------------------------------------------------------ 右键菜单

    /** 选择模式右键菜单里的动作。 */
    private enum MenuAction { COPY, CUT, PASTE, EDIT, DELETE, SELECT_ALL, DESELECT }

    private record MenuItem(MenuAction action, String key, boolean enabled) {}

    private List<MenuItem> menuItems() {
        boolean hasSelection = !selection.isEmpty();
        boolean single = selection.size() == 1;
        boolean editable = state.editable();
        List<MenuItem> items = new ArrayList<>(7);
        items.add(new MenuItem(MenuAction.COPY, "gui.dimensional_mana.magiccircle.menu.copy", hasSelection));
        items.add(new MenuItem(MenuAction.CUT, "gui.dimensional_mana.magiccircle.menu.cut",
                hasSelection && editable));
        items.add(new MenuItem(MenuAction.PASTE, "gui.dimensional_mana.magiccircle.menu.paste",
                state.clipboardSize() > 0 && editable));
        items.add(new MenuItem(MenuAction.EDIT, "gui.dimensional_mana.magiccircle.menu.edit",
                single && editable));
        items.add(new MenuItem(MenuAction.DELETE, "gui.dimensional_mana.magiccircle.menu.delete",
                hasSelection && editable));
        items.add(new MenuItem(MenuAction.SELECT_ALL, "gui.dimensional_mana.magiccircle.menu.select_all",
                !state.nodes().isEmpty()));
        items.add(new MenuItem(MenuAction.DESELECT, "gui.dimensional_mana.magiccircle.menu.deselect",
                hasSelection));
        return items;
    }

    private int[] menuOrigin() {
        int height = 14 + menuItems().size() * MENU_ITEM_HEIGHT;
        int x = Mth.clamp(contextMenuX, left + 4, Math.max(left + 4, left + panelW - MENU_WIDTH - 4));
        int y = Mth.clamp(contextMenuY, top + 4, Math.max(top + 4, top + panelH - height - 4));
        return new int[]{x, y};
    }

    private Rect menuItemRect(int index) {
        int[] origin = menuOrigin();
        return new Rect(origin[0] + 2, origin[1] + 14 + index * MENU_ITEM_HEIGHT,
                MENU_WIDTH - 4, MENU_ITEM_HEIGHT - 1);
    }

    private void renderContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!contextMenu) return;
        List<MenuItem> items = menuItems();
        int[] origin = menuOrigin();
        int x = origin[0];
        int y = origin[1];
        int height = 14 + items.size() * MENU_ITEM_HEIGHT;
        graphics.fill(x - 2, y - 2, x + MENU_WIDTH + 2, y + height + 2, 0xF0100C1E);
        graphics.renderOutline(x - 2, y - 2, MENU_WIDTH + 4, height + 4, 0xFFD9A8FF);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.menu.title"),
                x + 3, y + 3, COLOR_GOLD, false);
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            Rect rect = menuItemRect(i);
            boolean hover = item.enabled() && rect.contains(mouseX, mouseY);
            graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                    item.enabled() ? (hover ? 0xAA4A2E6E : 0x55211A33) : 0x330F0C18);
            String label = Component.translatable(item.key()).getString();
            graphics.drawString(font, label, rect.x + 3, rect.y + 2,
                    item.enabled() ? (hover ? 0xFFFFE4A8 : COLOR_TEXT) : 0xFF6A5F7E, false);
            String shortcut = shortcutLabel(item.action());
            if (!shortcut.isEmpty()) {
                graphics.drawString(font, shortcut, rect.x + rect.w - 3 - font.width(shortcut), rect.y + 2,
                        item.enabled() ? COLOR_DIM : 0xFF4A4159, false);
            }
        }
    }

    private String shortcutLabel(MenuAction action) {
        return switch (action) {
            case COPY -> "Ctrl+C";
            case CUT -> "Ctrl+X";
            case PASTE -> "Ctrl+V";
            case EDIT -> "E";
            case DELETE -> "Del";
            case SELECT_ALL -> "Ctrl+A";
            case DESELECT -> "Esc";
        };
    }

    // ------------------------------------------------------------------ 粉末编辑面板

    private int[] editorOrigin() {
        int x = canvasAreaX + (canvasAreaW - EDITOR_WIDTH) / 2;
        int y = canvasAreaY + (canvasAreaH - EDITOR_HEIGHT) / 2;
        return new int[]{
                Mth.clamp(x, left + 4, Math.max(left + 4, left + panelW - EDITOR_WIDTH - 4)),
                Mth.clamp(y, top + 4, Math.max(top + 4, top + panelH - EDITOR_HEIGHT - 4))};
    }

    private Rect editorRect() {
        int[] origin = editorOrigin();
        return new Rect(origin[0], origin[1], EDITOR_WIDTH, EDITOR_HEIGHT);
    }

    private Rect editorSlotRect(int index) {
        int[] origin = editorOrigin();
        return new Rect(origin[0] + 4, origin[1] + 27 + index * EDITOR_ROW_HEIGHT,
                EDITOR_WIDTH - 8, EDITOR_ROW_HEIGHT - 1);
    }

    private Rect editorCellRect(int index) {
        int[] origin = editorOrigin();
        int cellWidth = (EDITOR_WIDTH - 8) / EDITOR_COLUMNS;
        int row = index / EDITOR_COLUMNS;
        int column = index % EDITOR_COLUMNS;
        return new Rect(origin[0] + 4 + column * cellWidth,
                origin[1] + 88 + row * EDITOR_CELL_HEIGHT, cellWidth - 1, EDITOR_CELL_HEIGHT - 1);
    }

    private Rect editorClearRect() {
        int[] origin = editorOrigin();
        return new Rect(origin[0] + 4, origin[1] + 168, EDITOR_WIDTH - 8, 14);
    }

    private List<MagicCircleSyncPayload.PowderView> editorPowders() {
        List<MagicCircleSyncPayload.PowderView> list = new ArrayList<>();
        for (MagicCircleSyncPayload.PowderView view : state.powders()) {
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(view.itemId());
            if (powder != null && powder.slot() == editorSlot) list.add(view);
        }
        return list;
    }

    private static String nodeSlotValue(MagicCircleSyncPayload.NodeView node, MagicCirclePowders.Slot slot) {
        return switch (slot) {
            case ELEMENT -> node.element();
            case SEMANTIC -> node.semantic();
            case POWER -> node.power();
            case DYE -> node.dye();
        };
    }

    private void renderEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!editorOpen) return;
        if (editorNode < 0 || editorNode >= state.nodes().size()) {
            editorOpen = false;
            return;
        }
        MagicCircleSyncPayload.NodeView node = state.nodes().get(editorNode);
        MagicCircleBlockType type = MagicCircleBlockType.byId(node.type());
        int[] origin = editorOrigin();
        int x = origin[0];
        int y = origin[1];
        graphics.fill(x - 3, y - 3, x + EDITOR_WIDTH + 3, y + EDITOR_HEIGHT + 3, 0xF0100C1E);
        graphics.renderOutline(x - 3, y - 3, EDITOR_WIDTH + 6, EDITOR_HEIGHT + 6, 0xFFD9A8FF);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.editor.title"),
                x + 4, y + 4, COLOR_GOLD, false);
        Rect close = editorCloseRect();
        boolean closeHover = close.contains(mouseX, mouseY);
        graphics.drawString(font, "×", close.x + 4, close.y + 1, closeHover ? 0xFFFFC0C8 : COLOR_DIM, false);
        String header = Component.translatable("gui.dimensional_mana.magiccircle.editor.node",
                type == null ? node.type() : Component.translatable(type.translationKey()).getString(),
                node.x(), node.y()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(header, EDITOR_WIDTH - 8), x + 4, y + 15,
                COLOR_DIM, false);

        MagicCirclePowders.Slot[] slots = MagicCirclePowders.Slot.values();
        for (int i = 0; i < slots.length; i++) {
            Rect rect = editorSlotRect(i);
            boolean active = slots[i] == editorSlot;
            boolean hover = rect.contains(mouseX, mouseY);
            graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                    active ? 0xAA4A2E6E : hover ? 0x66302A46 : 0x33120F20);
            graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, active ? 0xFFFFD86B : 0xFF3A2C52);
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(nodeSlotValue(node, slots[i]));
            Component value = powder == null
                    ? Component.translatable("tooltip.dimensional_mana.magiccircle.node.empty")
                    : powder.name();
            Component label = Component.translatable(slots[i].translationKey())
                    .append(Component.literal("：")).append(value);
            graphics.drawString(font, font.plainSubstrByWidth(label.getString(), rect.w - 6),
                    rect.x + 3, rect.y + 3, active ? 0xFFFFE4A8 : COLOR_TEXT, false);
        }

        List<MagicCircleSyncPayload.PowderView> powders = editorPowders();
        for (int i = 0; i < powders.size(); i++) {
            Rect rect = editorCellRect(i);
            if (rect.y + EDITOR_CELL_HEIGHT > y + EDITOR_HEIGHT - 34) break;
            MagicCircleSyncPayload.PowderView view = powders.get(i);
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(view.itemId());
            if (powder == null) continue;
            boolean current = powder.itemId().equals(nodeSlotValue(node, editorSlot));
            boolean hover = rect.contains(mouseX, mouseY);
            graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                    current ? 0xAA4A2E6E : hover && view.unlocked() ? 0x88302A46 : 0x33120F20);
            graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, current ? 0xFFFFD86B : 0xFF3A2C52);
            graphics.renderItem(powder.stack(), rect.x + 1, rect.y + 1);
            String name = font.plainSubstrByWidth(powder.name().getString(), rect.w - 21);
            graphics.drawString(font, name, rect.x + 19, rect.y + 6, view.unlocked() ? COLOR_TEXT : 0xFF6A5F7E,
                    false);
            if (!view.unlocked()) {
                graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0x99000000);
            }
            if (hover) {
                hoverPowder.add(powder.name().copy().withStyle(view.unlocked()
                        ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.GRAY));
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.powder.slot",
                        Component.translatable(powder.slot().translationKey())).copy()
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
                if (!view.unlocked()) {
                    hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.powder.locked",
                            view.requiredLevel()).copy().withStyle(net.minecraft.ChatFormatting.RED));
                }
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.editor.install").copy()
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }

        Rect clear = editorClearRect();
        boolean clearHover = clear.contains(mouseX, mouseY);
        boolean hasValue = !nodeSlotValue(node, editorSlot).isEmpty();
        graphics.fill(clear.x, clear.y, clear.x + clear.w, clear.y + clear.h,
                hasValue ? (clearHover ? 0xAA5A2340 : 0x66211A33) : 0x33120F20);
        graphics.renderOutline(clear.x, clear.y, clear.w, clear.h,
                hasValue ? (clearHover ? 0xFFFF9BA8 : 0xFF5A4770) : 0xFF332A44);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.editor.clear"),
                clear.x + (clear.w - font.width(Component.translatable(
                        "gui.dimensional_mana.magiccircle.editor.clear"))) / 2,
                clear.y + 3, hasValue ? COLOR_TEXT : 0xFF6A5F7E, false);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.editor.hint"),
                x + 4, y + 186, COLOR_DIM, false);
    }

    private Rect editorCloseRect() {
        int[] origin = editorOrigin();
        return new Rect(origin[0] + EDITOR_WIDTH - 16, origin[1] + 3, 12, 12);
    }

    // ------------------------------------------------------------------ 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 命名弹框开着时，所有点击都归它管：只认「确认 / 取消」两个按钮。
        if (naming) {
            if (button == 0) {
                if (namingConfirmRect().contains(mouseX, mouseY)) {
                    confirmNaming();
                    return true;
                }
                if (namingCancelRect().contains(mouseX, mouseY)) {
                    cancelNaming();
                    return true;
                }
                if (namingFieldRect().contains(mouseX, mouseY)) {
                    // 点输入框 = 把光标放回去（也顺便把选区 / 光标位置交给原版控件算）。
                    if (nameBox != null) {
                        nameBox.setFocused(true);
                        nameBox.mouseClicked(mouseX, mouseY, button);
                    }
                    hint = Component.translatable("gui.dimensional_mana.magiccircle.naming.hint");
                }
            }
            // 点框外不乱关，免得敲到一半手滑丢掉；要退出请按 Esc / 取消。
            return true;
        }
        if (typeMenuOpen) {
            if (button == 0) {
                for (int i = 0; i < MagicCircleBlockType.values().length; i++) {
                    if (typeRect(i).contains(mouseX, mouseY)) {
                        chooseManualType(MagicCircleBlockType.values()[i]);
                        return true;
                    }
                }
                typeMenuOpen = false;
            }
            return true;
        }
        if (editorOpen) {
            if (handleEditorClick(mouseX, mouseY, button)) return true;
            if (button == 0 || button == 1) {
                editorOpen = false;
                editorNode = -1;
                return true;
            }
            return true;
        }
        if (contextMenu) {
            if (handleMenuClick(mouseX, mouseY, button)) return true;
            contextMenu = false;
            // 点别处 = 关菜单，这一下继续正常处理。
        }
        // M 键移动模式：鼠标这会儿就是「手上拿着的那一块」——左键放下，右键取消，
        // 别的按钮一概吃掉，免得手滑一下顺带画了一条线 / 删了一块。
        if (moveMode) {
            if (button == 0) {
                commitNodeMove(mouseX, mouseY);
            } else if (button == 1) {
                cancelNodeMove();
            }
            return true;
        }
        if (button == 0 && closeRect().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (button == 0) {
            if (modeDrawRect().contains(mouseX, mouseY)) {
                setMode(false);
                return true;
            }
            if (modeSelectRect().contains(mouseX, mouseY)) {
                setMode(true);
                return true;
            }
            if (handlePaletteClick(mouseX, mouseY)) return true;
            if (handleSideClick(mouseX, mouseY)) return true;
        }
        if (insideCanvas(mouseX, mouseY)) {
            // 按住 K 左键点一条线 = 拆掉它（任何模式都行，不用先进选择模式）。
            if (button == 0 && kHeld()) {
                return handleLinkCutClick(mouseX, mouseY);
            }
            if (button == 0) {
                // 选择模式不改画布，只读玩家也能点选、复制出来看看。
                if (selectMode) {
                    return handleSelectClick(mouseX, mouseY);
                }
                if (!state.editable()) {
                    hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                    return true;
                }
                if (manualMode) {
                    manualDragging = true;
                    frameX1 = cellX(mouseX);
                    frameY1 = cellY(mouseY);
                    frameX2 = frameX1;
                    frameY2 = frameY1;
                } else {
                    // 在绘制模式里按在一块上：这一下还是画线（老手感不变），顺嘴提醒一句
                    // 「想拖它得切到选择模式」，不然新手会一直以为块是拖不动的。
                    if (nearestNode(cellX(mouseX), cellY(mouseY), nodeHitRadius()) >= 0) {
                        hint = Component.translatable("gui.dimensional_mana.magiccircle.node.drag.need_select");
                    }
                    drawing = true;
                    raw.clear();
                    addStrokePoint(mouseX, mouseY);
                }
                return true;
            }
            if (button == 1) {
                if (selectMode) {
                    openContextMenu(mouseX, mouseY);
                    return true;
                }
                if (manualMode) {
                    manualMode = false;
                    manualDragging = false;
                    typeMenuOpen = false;
                    return true;
                }
                if (drawing || !raw.isEmpty()) {
                    drawing = false;
                    raw.clear();
                    preview = List.of();
                    return true;
                }
                if (!state.editable()) return true;
                int index = nearestNode(cellX(mouseX), cellY(mouseY), 2);
                if (index >= 0) PacketDistributor.sendToServer(MagicCircleActionPayload.delete(index));
                return true;
            }
            if (button == 2) {
                panning = true;
                panStartX = mouseX;
                panStartY = mouseY;
                panOriginX = panX;
                panOriginY = panY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 选择模式下左键按下：点在块上就选中（Shift 加选 / 取消），点在空白就开始拖框。
     *
     * <p>「按住一块往别处拖 = 移动它」：按下时先不拖，鼠标真的挪出几像素
     * （{@link #DRAG_THRESHOLD_PX}）才变成拖动，这样单纯点一下选块不会把块碰走。
     * 双击也能直接进入拖动，两条路都留着。</p>
     */
    private boolean handleSelectClick(double mouseX, double mouseY) {
        int index = nearestNode(cellX(mouseX), cellY(mouseY), nodeHitRadius());
        if (index >= 0) {
            long now = Util.getMillis();
            // 双击同一块：按住左键就能拖，松开才把新位置发给服务端。
            if (index == lastNodeClickIndex && now - lastNodeClickAt <= DOUBLE_CLICK_MS) {
                if (!state.editable()) {
                    hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                    return true;
                }
                beginNodeDrag(index, mouseX, mouseY);
                return true;
            }
            lastNodeClickIndex = index;
            lastNodeClickAt = now;
            if (Screen.hasShiftDown()) {
                toggleSelection(index);
            } else {
                selection.clear();
                selection.add(index);
            }
            selectedNode = index;
            // 单点也可能变成拖动：先记住按在哪一块上，鼠标挪出阈值再开始拖。
            pressNodeIndex = state.editable() ? index : -1;
            pressNodeX = mouseX;
            pressNodeY = mouseY;
            hint = selectionHint();
            return true;
        }
        lastNodeClickIndex = -1;
        pressNodeIndex = -1;
        if (!Screen.hasShiftDown()) selection.clear();
        boxSelecting = true;
        boxX1 = clampCellX(mouseX);
        boxY1 = clampCellY(mouseY);
        boxX2 = boxX1;
        boxY2 = boxY1;
        return true;
    }

    private void toggleSelection(int index) {
        if (!selection.remove(index)) selection.add(index);
        selectedNode = index;
    }

    // ---- 连线：按住 K 点线拆、选中两块按 L 连 ----

    /**
     * 判断玩家是不是正按着 K（绘制台里的「拆线」修饰键）。
     *
     * <p>直接问 GLFW 要键盘状态：界面开着的时候按键绑定本身不触发，{@code KeyMapping}
     * 不一定可靠，GLFW 的按下状态是实时的。K 也是「打开万法知识之书」的默认键，
     * 但那只有关着界面时才生效，两边不会打架。</p>
     */
    private static boolean kHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.KEY_K);
    }

    /** 按住 K 点画布：把鼠标下最近的那条连线拆掉。 */
    private boolean handleLinkCutClick(double mouseX, double mouseY) {
        int link = nearestLink(mouseX, mouseY);
        if (link < 0) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.node.link.hint");
            return true;
        }
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return true;
        }
        MagicCircleSyncPayload.LinkView view = state.links().get(link);
        PacketDistributor.sendToServer(MagicCircleActionPayload.unlink(view.from(), view.to()));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.node.link.cutting",
                Math.min(view.from(), view.to()) + 1, Math.max(view.from(), view.to()) + 1);
        return true;
    }

    /**
     * 选择模式下按 L 连线，两种用法：
     *
     * <ul>
     *   <li><b>只选两块</b>：连一条线，先点的是上游。已经连着但方向反了，再按一次 L 就反过来；</li>
     *   <li><b>选了三块以上</b>：<b>按你点的先后顺序一路串起来</b> A → B → C → D，
     *       一次请求发完，服务端只记一条撤销，Ctrl+Z 一按整条链一起消失。</li>
     * </ul>
     *
     * <p>{@code selection} 是 {@code LinkedHashSet}，迭代顺序就是玩家点的先后顺序，
     * 所以「先点上游、再点下游」这件事在两种用法里是一条规则。</p>
     */
    private void connectSelection() {
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        if (selection.size() < 2) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.node.link.need_two", selection.size());
            return;
        }
        // selection 是 LinkedHashSet：迭代顺序就是玩家点的先后顺序，正好拿来定上下游。
        List<Integer> picked = new ArrayList<>(selection);
        if (picked.size() > 2) {
            // 三块以上：按点的先后串成一条链。
            PacketDistributor.sendToServer(MagicCircleActionPayload.linkChain(
                    MagicCircleActionPayload.encodeIndices(picked)));
            hint = Component.translatable("gui.dimensional_mana.magiccircle.node.link.chain_connecting",
                    picked.size() - 1, blockOrderText(picked));
            return;
        }
        int from = picked.get(0);
        int to = picked.get(1);
        int existing = linkDirection(from, to);
        if (existing == 0) {
            PacketDistributor.sendToServer(MagicCircleActionPayload.link(from, to));
            hint = Component.translatable("gui.dimensional_mana.magiccircle.node.link.connecting", from + 1, to + 1);
            return;
        }
        if (existing > 0) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.node.link.already");
            return;
        }
        // 反着连着：先拆掉再按新方向连，两包发完服务端会回一份新状态。
        PacketDistributor.sendToServer(MagicCircleActionPayload.unlink(from, to));
        PacketDistributor.sendToServer(MagicCircleActionPayload.link(from, to));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.node.link.reversed", from + 1, to + 1);
    }

    private boolean linkExists(int from, int to) {
        return linkDirection(from, to) != 0;
    }

    /** 这两块之间有没有线：1 = 已经是 from → to，-1 = 连着但方向相反，0 = 没连。 */
    private int linkDirection(int from, int to) {
        for (MagicCircleSyncPayload.LinkView link : state.links()) {
            if (link.from() == from && link.to() == to) return 1;
            if (link.from() == to && link.to() == from) return -1;
        }
        return 0;
    }

    /** 链式连线用的一句「第几块 → 第几块 → …」（对玩家显示，所以下标 +1；太长就省略）。 */
    private static String blockOrderText(List<Integer> indices) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < indices.size(); i++) {
            if (i >= 6) {
                builder.append(" …");
                break;
            }
            if (builder.length() > 0) builder.append(" → ");
            builder.append(indices.get(i) + 1);
        }
        return builder.toString();
    }

    /**
     * 点选中一块的容差：写死 2 格在放大 / 缩小以后手感差太多。
     * 换算成「至少 7 个屏幕像素」的格数，双击拖动在小缩放下也点得中。
     */
    private int nodeHitRadius() {
        double cell = cell();
        if (cell <= 0) return 2;
        return Math.max(2, (int) Math.ceil(7.0 / cell));
    }

    /** 双击一块之后：记下「拖的是哪块、抓在块的哪个位置」，渲染时先把块画在手上。 */
    private void beginNodeDrag(int index, double mouseX, double mouseY) {
        double cell = cell();
        if (cell <= 0) return;
        MagicCircleSyncPayload.NodeView node = state.nodes().get(index);
        draggingNode = true;
        dragIndex = index;
        dragX = node.x();
        dragY = node.y();
        dragOffsetX = (mouseX - gridLeft()) / cell - node.x();
        dragOffsetY = (mouseY - gridTop()) / cell - node.y();
        selection.clear();
        selection.add(index);
        selectedNode = index;
        lastNodeClickIndex = -1;
        hint = Component.translatable("gui.dimensional_mana.magiccircle.node.drag.hint");
    }

    // ---- M 键移动：选中一块 → 它跟到鼠标所指的格上 → 左键放下 ----

    /** M 键：没在移动就抓起选中的那一块；正在移动就收手取消（和右键一样，不会乱放）。 */
    private void toggleNodeMove() {
        if (moveMode) {
            cancelNodeMove();
            return;
        }
        beginNodeMove();
    }

    /**
     * 抓起选中的那一块。抓起来的瞬间就贴到鼠标所指的格子上：指针在画布内就以它为准，
     * 不在画布内就先留在原格，等鼠标移进画布再跟。
     */
    private void beginNodeMove() {
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        int index = selectedNode;
        if (index < 0 || index >= state.nodes().size()) {
            if (selection.size() == 1) {
                index = selection.iterator().next();
            }
            // 兜底：什么都没选、但鼠标正停在一块上，就抓这一块，省得再点一次。
            if ((index < 0 || index >= state.nodes().size()) && insideGrid(pointerX, pointerY)) {
                index = nearestNode(clampCellX(pointerX), clampCellY(pointerY), nodeHitRadius());
            }
            if (index < 0 || index >= state.nodes().size()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.node.move.need_select");
                return;
            }
        }
        MagicCircleSyncPayload.NodeView node = state.nodes().get(index);
        moveMode = true;
        moveIndex = index;
        moveFromX = node.x();
        moveFromY = node.y();
        moveX = node.x();
        moveY = node.y();
        if (insideGrid(pointerX, pointerY)) {
            moveX = clampCellX(pointerX);
            moveY = clampCellY(pointerY);
        }
        selection.clear();
        selection.add(index);
        selectedNode = index;
        hint = Component.translatable("gui.dimensional_mana.magiccircle.node.move.hint");
    }

    /** 左键放下：把新坐标真的发给服务端；原地没动就只是收手，不白记一条撤销。 */
    private void commitNodeMove(double mouseX, double mouseY) {
        int index = moveIndex;
        if (index < 0 || index >= state.nodes().size()) {
            moveMode = false;
            moveIndex = -1;
            return;
        }
        // 鼠标不在格子上（落在画布区的空白边距里）就不硬塞到边缘那一行：这一下当没放，
        // 提醒一句「把鼠标放到格子上」，人还在移动模式里，挪回格子上再点一次就行。
        int x = cellX(mouseX);
        int y = cellY(mouseY);
        if (x < 0 || y < 0) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.node.move.offgrid");
            return;
        }
        moveMode = false;
        moveIndex = -1;
        MagicCircleSyncPayload.NodeView node = state.nodes().get(index);
        if (node.x() == x && node.y() == y) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.node.move.unchanged");
            return;
        }
        PacketDistributor.sendToServer(MagicCircleActionPayload.move(index, x, y));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.node.move.moved", x, y);
    }

    private void cancelNodeMove() {
        moveMode = false;
        moveIndex = -1;
        hint = Component.translatable("gui.dimensional_mana.magiccircle.node.move.cancelled");
    }

    /** 鼠标落在哪条连线附近（L 形折线：先横后竖，和渲染一致）。容差按屏幕像素算，缩放也不跑偏。 */
    private int nearestLink(double mouseX, double mouseY) {
        double cell = cell();
        if (cell <= 0) return -1;
        double px = (mouseX - gridLeft()) / cell;
        double py = (mouseY - gridTop()) / cell;
        double tolerance = Math.max(1.5, 6.0 / cell);
        int best = -1;
        double bestDistance = tolerance;
        for (int i = 0; i < state.links().size(); i++) {
            MagicCircleSyncPayload.LinkView link = state.links().get(i);
            if (link.from() < 0 || link.to() < 0
                    || link.from() >= state.nodes().size() || link.to() >= state.nodes().size()) continue;
            MagicCircleSyncPayload.NodeView from = state.nodes().get(link.from());
            MagicCircleSyncPayload.NodeView to = state.nodes().get(link.to());
            double distance = lShapeDistance(px, py, from.x(), from.y(), to.x(), to.y());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** 点到「先横后竖」这条 L 形折线的距离。 */
    private static double lShapeDistance(double px, double py, double x1, double y1, double x2, double y2) {
        return Math.min(distanceToSegment(px, py, x1, y1, x2, y1),
                distanceToSegment(px, py, x2, y1, x2, y2));
    }

    private static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq <= 1e-9) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0.0, Math.min(1.0, ((px - x1) * dx + (py - y1) * dy) / lengthSq));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private Component selectionHint() {
        // 正好两块时顺手提示一句「按 L 连线」：这条手势不看文档很难自己撞出来。
        if (selection.size() == 2) {
            return Component.translatable("gui.dimensional_mana.magiccircle.node.selection.count_link",
                    selection.size(), state.clipboardSize());
        }
        // 三块以上：再提示一句「按顺序串起来」，省得玩家一块一块去连。
        if (selection.size() > 2) {
            return Component.translatable("gui.dimensional_mana.magiccircle.node.selection.count_chain",
                    selection.size(), state.clipboardSize());
        }
        return Component.translatable("gui.dimensional_mana.magiccircle.selection.count",
                selection.size(), state.clipboardSize());
    }

    private void openContextMenu(double mouseX, double mouseY) {
        int index = nearestNode(cellX(mouseX), cellY(mouseY), 2);
        if (index >= 0) {
            if (!selection.contains(index)) {
                if (Screen.hasShiftDown()) {
                    selection.add(index);
                } else {
                    selection.clear();
                    selection.add(index);
                }
            }
            selectedNode = index;
        }
        contextMenu = true;
        contextMenuX = (int) Math.round(mouseX);
        contextMenuY = (int) Math.round(mouseY);
        contextCellX = cellX(mouseX);
        contextCellY = cellY(mouseY);
    }

    private boolean handleMenuClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        List<MenuItem> items = menuItems();
        for (int i = 0; i < items.size(); i++) {
            if (!menuItemRect(i).contains(mouseX, mouseY)) continue;
            MenuItem item = items.get(i);
            if (!item.enabled()) return true;
            contextMenu = false;
            runMenuAction(item.action());
            return true;
        }
        return false;
    }

    private void runMenuAction(MenuAction action) {
        switch (action) {
            case COPY -> copySelection();
            case CUT -> cutSelection();
            case PASTE -> pasteClipboard(contextCellX, contextCellY);
            case EDIT -> openEditor();
            case DELETE -> deleteSelection();
            case SELECT_ALL -> selectAll();
            case DESELECT -> {
                selection.clear();
                hint = Component.translatable("gui.dimensional_mana.magiccircle.selection.cleared");
            }
        }
    }

    private boolean handleEditorClick(double mouseX, double mouseY, int button) {
        if (!editorRect().contains(mouseX, mouseY)) return false;
        if (editorCloseRect().contains(mouseX, mouseY)) {
            editorOpen = false;
            editorNode = -1;
            return true;
        }
        if (button != 0) return true;
        if (editorNode < 0 || editorNode >= state.nodes().size()) return true;
        MagicCirclePowders.Slot[] slots = MagicCirclePowders.Slot.values();
        for (int i = 0; i < slots.length; i++) {
            if (!editorSlotRect(i).contains(mouseX, mouseY)) continue;
            editorSlot = slots[i];
            return true;
        }
        List<MagicCircleSyncPayload.PowderView> powders = editorPowders();
        for (int i = 0; i < powders.size(); i++) {
            Rect rect = editorCellRect(i);
            if (rect.y + EDITOR_CELL_HEIGHT > editorOrigin()[1] + EDITOR_HEIGHT - 34) break;
            if (!rect.contains(mouseX, mouseY)) continue;
            MagicCircleSyncPayload.PowderView view = powders.get(i);
            if (!state.editable() || !view.unlocked()) {
                hint = view.unlocked()
                        ? Component.translatable("gui.dimensional_mana.magiccircle.readonly")
                        : Component.translatable("gui.dimensional_mana.magiccircle.powder.locked",
                        view.requiredLevel());
                return true;
            }
            PacketDistributor.sendToServer(MagicCircleActionPayload.edit(editorNode, editorSlot.id(),
                    view.itemId()));
            hint = Component.translatable("gui.dimensional_mana.magiccircle.status.editing");
            return true;
        }
        if (editorClearRect().contains(mouseX, mouseY)) {
            if (state.editable()) {
                PacketDistributor.sendToServer(MagicCircleActionPayload.edit(editorNode, editorSlot.id(), ""));
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.editing");
            }
            return true;
        }
        return true;
    }

    private void setMode(boolean select) {
        selectMode = select;
        contextMenu = false;
        editorOpen = false;
        editorNode = -1;
        manualMode = false;
        manualDragging = false;
        typeMenuOpen = false;
        drawing = false;
        boxSelecting = false;
        raw.clear();
        preview = List.of();
        if (!select) selection.clear();
        hint = select
                ? Component.translatable("gui.dimensional_mana.magiccircle.hint.select")
                : Component.translatable("gui.dimensional_mana.magiccircle.hint.draw");
    }

    private void copySelection() {
        if (selection.isEmpty()) {
            hint = Component.translatable("message.dimensional_mana.magiccircle.copy_empty");
            return;
        }
        PacketDistributor.sendToServer(MagicCircleActionPayload.copy(MagicCircleActionPayload.encodeIndices(selection)));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.copying", selection.size());
    }

    private void cutSelection() {
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        if (selection.isEmpty()) {
            hint = Component.translatable("message.dimensional_mana.magiccircle.copy_empty");
            return;
        }
        PacketDistributor.sendToServer(MagicCircleActionPayload.cut(MagicCircleActionPayload.encodeIndices(selection)));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.cutting", selection.size());
        // 剪切后本层的下标全乱了，先把选区清掉，免得接着误操作到别的块。
        selection.clear();
        selectedNode = -1;
    }

    private void pasteClipboard(int cellX, int cellY) {
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        if (state.clipboardSize() <= 0) {
            hint = Component.translatable("message.dimensional_mana.magiccircle.paste_empty");
            return;
        }
        PacketDistributor.sendToServer(MagicCircleActionPayload.paste(cellX, cellY));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.pasting", state.clipboardSize());
    }

    private void deleteSelection() {
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        if (selection.isEmpty()) {
            if (selectedNode >= 0) {
                PacketDistributor.sendToServer(MagicCircleActionPayload.delete(selectedNode));
                selectedNode = -1;
            }
            return;
        }
        PacketDistributor.sendToServer(
                MagicCircleActionPayload.deleteMany(MagicCircleActionPayload.encodeIndices(selection)));
        selection.clear();
        selectedNode = -1;
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.deleting");
    }

    private void selectAll() {
        selection.clear();
        for (int i = 0; i < state.nodes().size(); i++) selection.add(i);
        hint = selectionHint();
    }

    private void openEditor() {
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        int index = selection.size() == 1 ? selection.iterator().next()
                : selection.isEmpty() ? selectedNode : -1;
        if (index < 0) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.editor.single_only");
            return;
        }
        editorNode = index;
        editorSlot = MagicCirclePowders.Slot.ELEMENT;
        editorOpen = true;
        contextMenu = false;
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.editing");
    }

    private boolean handlePaletteClick(double mouseX, double mouseY) {
        for (int i = 0; i < MagicCirclePowders.Category.values().length; i++) {
            if (tabRect(i).contains(mouseX, mouseY)) {
                categoryIndex = i;
                return true;
            }
        }
        if (commonRect().contains(mouseX, mouseY)) {
            commonOnly = true;
            return true;
        }
        if (allRect().contains(mouseX, mouseY)) {
            commonOnly = false;
            return true;
        }
        List<MagicCircleSyncPayload.PowderView> visible = visiblePowders(MagicCirclePowders.Category.values()[categoryIndex]);
        for (int i = 0; i < visible.size(); i++) {
            Rect rect = powderRect(i);
            if (rect.y + POWDER_CELL_HEIGHT > paletteY + paletteH - 24) break;
            if (!rect.contains(mouseX, mouseY)) continue;
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(visible.get(i).itemId());
            if (powder != null) pickPowder(powder);
            return true;
        }
        MagicCirclePowders.Slot[] slots = MagicCirclePowders.Slot.values();
        for (int i = 0; i < slots.length; i++) {
            if (!slotRect(i).contains(mouseX, mouseY)) continue;
            if (state.editable()) {
                PacketDistributor.sendToServer(MagicCircleActionPayload.select(slots[i].id(), ""));
            }
            return true;
        }
        return false;
    }

    private boolean handleSideClick(double mouseX, double mouseY) {
        for (int i = 0; i < state.candidates().size(); i++) {
            if (!candidateRect(i).contains(mouseX, mouseY)) continue;
            if (!state.editable()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                return true;
            }
            PacketDistributor.sendToServer(MagicCircleActionPayload.confirm(state.candidates().get(i).glyph()));
            hint = Component.translatable("gui.dimensional_mana.magiccircle.status.placing");
            return true;
        }
        int nodeTop = sideY + 16 + 84;
        if (state.nodes().size() > 0) {
            int start = Math.max(0, Math.min(selectedNode - MAX_VISIBLE_NODES / 2,
                    state.nodes().size() - MAX_VISIBLE_NODES));
            for (int row = 0; row < MAX_VISIBLE_NODES && start + row < state.nodes().size(); row++) {
                if (!nodeRowRect(nodeTop, row).contains(mouseX, mouseY)) continue;
                int index = start + row;
                if (selectMode) {
                    if (Screen.hasShiftDown()) {
                        toggleSelection(index);
                    } else {
                        selection.clear();
                        selection.add(index);
                    }
                    hint = selectionHint();
                }
                selectedNode = index;
                return true;
            }
        }
        if (redrawRect().contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(MagicCircleActionPayload.cancel());
            drawing = false;
            raw.clear();
            preview = List.of();
            hint = Component.translatable("gui.dimensional_mana.magiccircle.status.redraw");
            return true;
        }
        if (undoRect().contains(mouseX, mouseY)) {
            if (!state.editable()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                return true;
            }
            PacketDistributor.sendToServer(MagicCircleActionPayload.undo());
            return true;
        }
        if (clearRect().contains(mouseX, mouseY)) {
            if (!state.editable()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                return true;
            }
            if (!clearArmed) {
                clearArmed = true;
                return true;
            }
            clearArmed = false;
            selectedNode = -1;
            selection.clear();
            PacketDistributor.sendToServer(MagicCircleActionPayload.clear());
            return true;
        }
        if (deleteRect().contains(mouseX, mouseY)) {
            if (!state.editable()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                return true;
            }
            if (!selection.isEmpty()) {
                PacketDistributor.sendToServer(
                        MagicCircleActionPayload.deleteMany(MagicCircleActionPayload.encodeIndices(selection)));
                selection.clear();
                selectedNode = -1;
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.deleting");
                return true;
            }
            if (selectedNode < 0) return true;
            PacketDistributor.sendToServer(MagicCircleActionPayload.delete(selectedNode));
            selectedNode = -1;
            return true;
        }
        if (manualRect().contains(mouseX, mouseY)) {
            if (!state.editable()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                return true;
            }
            manualMode = !manualMode;
            manualDragging = false;
            typeMenuOpen = false;
            selectMode = false;
            contextMenu = false;
            editorOpen = false;
            selection.clear();
            frameX1 = frameX2 = frameY1 = frameY2 = 0;
            hint = manualMode
                    ? Component.translatable("gui.dimensional_mana.magiccircle.manual.hint")
                    : Component.translatable("gui.dimensional_mana.magiccircle.hint.draw");
            return true;
        }
        if (transcribeRect().contains(mouseX, mouseY)) {
            if (!state.editable()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                return true;
            }
            if (state.nodes().isEmpty()) {
                hint = Component.translatable("message.dimensional_mana.magiccircle.blueprint.nothing_to_copy");
                return true;
            }
            // 先弹框问名字：直接确认 = 用服务端按元素自动起的默认名。
            openNaming();
            return true;
        }
        return false;
    }

    /**
     * 鼠标移动。M 键移动模式就靠这里跟手：块只认鼠标现在指着哪一格，
     * 不依赖「按住拖动」的拖拽事件，所以手快、低帧率、放大到 8 倍都不会丢。
     */
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        pointerX = (int) Math.round(mouseX);
        pointerY = (int) Math.round(mouseY);
        if (moveMode) {
            // 移动模式：块只认鼠标现在指着哪一格，这里实时更新，不靠拖拽事件。
            // 鼠标跑到格子外（画布区边上的空白）就先停在最后一格，不跟着往边缘挤。
            if (insideGrid(mouseX, mouseY)) {
                moveX = clampCellX(mouseX);
                moveY = clampCellY(mouseY);
            }
            return;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 弹框开着时不拖画布、不框选，免得打字时顺手把阵改了。
        if (naming) return true;
        if (moveMode && button == 0) {
            // 万一玩家是「按住左键挪」的：位置照鼠标走，不启动旧的拖动逻辑。
            if (insideGrid(mouseX, mouseY)) {
                moveX = clampCellX(mouseX);
                moveY = clampCellY(mouseY);
            }
            return true;
        }
        if (button == 2 && panning) {
            panX = panOriginX + (int) Math.round(mouseX - panStartX);
            panY = panOriginY + (int) Math.round(mouseY - panStartY);
            clampPan();
            return true;
        }
        // 按住一块拖出去几像素 = 开始拖它。以前只认双击，手慢一点就拖不动，这一版改成一按一拖就行。
        if (button == 0 && !draggingNode && pressNodeIndex >= 0) {
            double moved = Math.hypot(mouseX - pressNodeX, mouseY - pressNodeY);
            if (moved >= DRAG_THRESHOLD_PX) {
                beginNodeDrag(pressNodeIndex, pressNodeX, pressNodeY);
                pressNodeIndex = -1;
            }
        }
        if (button == 0 && draggingNode) {
            double cell = cell();
            if (cell > 0 && dragIndex >= 0 && dragIndex < state.nodes().size()) {
                dragX = Mth.clamp((int) Math.round((mouseX - gridLeft()) / cell - dragOffsetX),
                        0, Math.max(0, state.canvasW() - 1));
                dragY = Mth.clamp((int) Math.round((mouseY - gridTop()) / cell - dragOffsetY),
                        0, Math.max(0, state.canvasH() - 1));
            }
            return true;
        }
        if (button == 0 && boxSelecting) {
            boxX2 = clampCellX(mouseX);
            boxY2 = clampCellY(mouseY);
            return true;
        }
        if (button == 0 && manualDragging) {
            frameX2 = cellX(mouseX);
            frameY2 = cellY(mouseY);
            return true;
        }
        if (button == 0 && drawing) {
            addStrokePoint(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (naming) return true;
        if (moveMode && button == 0) {
            // 按住左键一路挪过来、现在松手：就落在这儿。
            commitNodeMove(mouseX, mouseY);
            return true;
        }
        if (button == 2 && panning) {
            panning = false;
            return true;
        }
        if (button == 0 && draggingNode) {
            draggingNode = false;
            pressNodeIndex = -1;
            int index = dragIndex;
            dragIndex = -1;
            if (index >= 0 && index < state.nodes().size()) {
                MagicCircleSyncPayload.NodeView node = state.nodes().get(index);
                if (node.x() != dragX || node.y() != dragY) {
                    PacketDistributor.sendToServer(MagicCircleActionPayload.move(index, dragX, dragY));
                    hint = Component.translatable("gui.dimensional_mana.magiccircle.node.drag.moved", dragX, dragY);
                }
            }
            return true;
        }
        if (button == 0) pressNodeIndex = -1;
        if (button == 0 && boxSelecting) {
            boxSelecting = false;
            int x1 = Math.min(boxX1, boxX2);
            int x2 = Math.max(boxX1, boxX2);
            int y1 = Math.min(boxY1, boxY2);
            int y2 = Math.max(boxY1, boxY2);
            if (x1 != x2 || y1 != y2) {
                for (int i = 0; i < state.nodes().size(); i++) {
                    MagicCircleSyncPayload.NodeView node = state.nodes().get(i);
                    if (node.x() >= x1 && node.x() <= x2 && node.y() >= y1 && node.y() <= y2) {
                        selection.add(i);
                    }
                }
                if (selectedNode >= 0 && !selection.contains(selectedNode)) selectedNode = -1;
            }
            hint = selectionHint();
            return true;
        }
        if (button == 0 && manualDragging) {
            manualDragging = false;
            frameX2 = cellX(mouseX);
            frameY2 = cellY(mouseY);
            if (Math.abs(frameX1 - frameX2) + Math.abs(frameY1 - frameY2) >= 1) {
                typeMenuOpen = true;
            } else {
                manualMode = false;
            }
            return true;
        }
        if (button == 0 && drawing) {
            drawing = false;
            if (preview.size() >= 2) {
                // 发给服务端之前先压到 256 个点以内，不然长笔画会把包撑爆。
                String encoded = MagicCircleStroke.encode(MagicCircleStroke.simplifyForNetwork(preview,
                        Math.min(state.canvasW(), state.canvasH())));
                PacketDistributor.sendToServer(MagicCircleActionPayload.submit(encoded));
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.recognizing");
            } else {
                raw.clear();
                preview = List.of();
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.stroke_short");
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double verticalDelta) {
        if (naming) return true;
        if (!insideCanvas(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, horizontalDelta, verticalDelta);
        if (Screen.hasShiftDown()) {
            cyclePowder(verticalDelta > 0 ? 1 : -1);
            return true;
        }
        if (Screen.hasControlDown()) {
            // 以鼠标下的那一点为中心缩放：放大时想看的那块不会跑出屏幕。
            double target = zoom * (verticalDelta > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP);
            zoomAt(target, mouseX, mouseY);
            hint = Component.translatable("gui.dimensional_mana.magiccircle.status.zoom",
                    String.format(Locale.ROOT, "×%.2f", zoom));
            return true;
        }
        brush = Mth.clamp(brush + (verticalDelta > 0 ? 1 : -1), 1, 4);
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.brush", brush);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 命名弹框开着时，键盘只做三件事：回车确认、Esc 取消、其余交给原版输入框
        // （退格 / Delete / 方向键 / Home / End / Ctrl+A / Ctrl+C / Ctrl+V 它自己都认）；
        // 剩下的键一律吃掉，不然 Ctrl+Z / Delete 之类会绕过弹框去改底下的阵。
        if (naming) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                cancelNaming();
                return true;
            }
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                confirmNaming();
                return true;
            }
            if (nameBox != null && nameBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true;
        }
        boolean control = Screen.hasControlDown();
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (typeMenuOpen) {
                typeMenuOpen = false;
                return true;
            }
            if (editorOpen) {
                editorOpen = false;
                editorNode = -1;
                return true;
            }
            if (contextMenu) {
                contextMenu = false;
                return true;
            }
            if (moveMode) {
                cancelNodeMove();
                return true;
            }
            if (manualMode) {
                manualMode = false;
                manualDragging = false;
                return true;
            }
            if (!selection.isEmpty()) {
                selection.clear();
                hint = Component.translatable("gui.dimensional_mana.magiccircle.selection.cleared");
                return true;
            }
        }
        // Tab 切绘制 / 选择模式，和常见的编辑器切换工具一个手感。
        if (keyCode == InputConstants.KEY_TAB) {
            setMode(!selectMode);
            return true;
        }
        // L：选择模式下选中正好两块 → 把这两块连起来（按住 K 点线是拆，见鼠标点击）。
        if (keyCode == InputConstants.KEY_L && selectMode) {
            connectSelection();
            return true;
        }
        // M（move）：选中一块 → 它跟到鼠标所指的格子上，左键放下，右键 / Esc 取消。
        // 只认「选块 + 鼠标位置 + 一次点击」，不靠按住拖动，手快手慢都一样。
        if (keyCode == InputConstants.KEY_M && selectMode && !Screen.hasControlDown()) {
            toggleNodeMove();
            return true;
        }
        if (control) {
            if (keyCode == InputConstants.KEY_C) {
                copySelection();
                return true;
            }
            if (keyCode == InputConstants.KEY_X) {
                cutSelection();
                return true;
            }
            if (keyCode == InputConstants.KEY_V) {
                // 鼠标在哪就粘到哪；在画布外就交给服务端放画布中央。
                pasteClipboard(cellX(pointerX), cellY(pointerY));
                return true;
            }
            if (keyCode == InputConstants.KEY_A) {
                selectAll();
                return true;
            }
            if (keyCode == InputConstants.KEY_Z) {
                if (state.editable()) PacketDistributor.sendToServer(MagicCircleActionPayload.undo());
                return true;
            }
            // Ctrl+0：缩放 / 平移复位，放大看细节看迷路了按一下回到全图。
            if (keyCode == InputConstants.KEY_0) {
                resetView();
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.zoom_reset");
                return true;
            }
        }
        if (keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
            deleteSelection();
            return true;
        }
        // E：打开 / 收起「编辑粉末」面板（只对单块有效）。
        if (keyCode == InputConstants.KEY_E) {
            if (editorOpen) {
                editorOpen = false;
                editorNode = -1;
            } else {
                openEditor();
            }
            return true;
        }
        // PageUp / PageDown 翻层：多层阵每层各是一张图，得能翻过去看和改。
        if (keyCode == InputConstants.KEY_PAGEUP || keyCode == InputConstants.KEY_PAGEDOWN) {
            if (state.editable()) {
                PacketDistributor.sendToServer(
                        MagicCircleActionPayload.layer(keyCode == InputConstants.KEY_PAGEUP ? 1 : -1));
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.layer");
            }
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (keyCode == InputConstants.KEY_1 + i) {
                categoryIndex = slotCategory(i).ordinal();
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.slot",
                        Component.translatable(slotCategory(i).translationKey()));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 弹框里打字：原样交给原版输入框。中文是输入法「提交」那一刻整段进来的，
     * 这里不要自己拼字符串——交给 {@link EditBox} 才能和聊天栏 / 铁砧改名一个行为
     * （输入法、粘贴、方向键、选区、字数上限都由它处理）。
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (naming) {
            if (nameBox != null) nameBox.charTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(MagicCircleActionPayload.close());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ 动作

    private void addStrokePoint(double mouseX, double mouseY) {
        int cx = cellX(mouseX);
        int cy = cellY(mouseY);
        if (cx < 0 || cy < 0 || cx >= state.canvasW() || cy >= state.canvasH()) return;
        MagicCircleStroke.Point point = new MagicCircleStroke.Point(cx, cy);
        if (!raw.isEmpty() && raw.get(raw.size() - 1).equals(point)) return;
        if (raw.size() >= 4096) return;
        raw.add(point);
        MagicCircleStroke.Snapped snapped = MagicCircleStroke.snap(raw, state.canvasW(), state.canvasH());
        preview = snapped.points();
        previewPatched = snapped.patched();
    }

    private void pickPowder(MagicCirclePowders.Powder powder) {
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        if (!isUnlocked(powder.itemId())) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.powder.locked", powder.requiredLevel());
            return;
        }
        boolean already = powder.itemId().equals(selectedItem(powder.slot().id()));
        PacketDistributor.sendToServer(MagicCircleActionPayload.select(powder.slot().id(),
                already ? "" : powder.itemId()));
        if (!already) {
            recentPowders.remove(powder.itemId());
            recentPowders.addFirst(powder.itemId());
            while (recentPowders.size() > 3) recentPowders.removeLast();
        }
        categoryIndex = powder.category().ordinal();
        hint = already
                ? Component.translatable("gui.dimensional_mana.magiccircle.status.slot_cleared",
                Component.translatable(powder.slot().translationKey()))
                : Component.translatable("gui.dimensional_mana.magiccircle.status.powder_picked",
                powder.name(), Component.translatable(powder.slot().translationKey()));
    }

    private void cyclePowder(int delta) {
        List<MagicCircleSyncPayload.PowderView> visible = visiblePowders(MagicCirclePowders.Category.values()[categoryIndex]);
        if (visible.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < visible.size(); i++) {
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(visible.get(i).itemId());
            if (powder != null && powder.itemId().equals(selectedItem(powder.slot().id()))) {
                current = i;
                break;
            }
        }
        for (int step = 1; step <= visible.size(); step++) {
            int index = Math.floorMod(current + delta * step, visible.size());
            MagicCircleSyncPayload.PowderView view = visible.get(index);
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(view.itemId());
            if (powder == null || !view.unlocked()) continue;
            pickPowder(powder);
            return;
        }
    }

    private void chooseManualType(MagicCircleBlockType type) {
        typeMenuOpen = false;
        manualMode = false;
        int x1 = Math.max(0, Math.min(frameX1, frameX2));
        int y1 = Math.max(0, Math.min(frameY1, frameY2));
        int x2 = Math.min(state.canvasW() - 1, Math.max(frameX1, frameX2));
        int y2 = Math.min(state.canvasH() - 1, Math.max(frameY1, frameY2));
        PacketDistributor.sendToServer(MagicCircleActionPayload.manual(x1, y1, x2, y2, type.id()));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.placing");
    }

    // ------------------------------------------------------------------ 布局

    private static final class Rect {
        final int x;
        final int y;
        final int w;
        final int h;

        Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private Rect closeRect() {
        return new Rect(left + panelW - 20, top + 5, 14, 14);
    }

    private Rect modeDrawRect() {
        return new Rect(canvasAreaX + 2, toolbarY + 2, TOOLBAR_BUTTON_WIDTH, TOOLBAR_HEIGHT - 4);
    }

    private Rect modeSelectRect() {
        return new Rect(canvasAreaX + 2 + TOOLBAR_BUTTON_WIDTH + 2, toolbarY + 2,
                TOOLBAR_BUTTON_WIDTH, TOOLBAR_HEIGHT - 4);
    }

    private Rect tabRect(int index) {
        return new Rect(paletteX + 2, paletteY + 2 + index * TAB_HEIGHT, paletteW - 4, TAB_HEIGHT - 1);
    }

    private Rect commonRect() {
        int y = paletteY + 2 + 4 * TAB_HEIGHT;
        return new Rect(paletteX + 2, y, (paletteW - 6) / 2, 12);
    }

    private Rect allRect() {
        int y = paletteY + 2 + 4 * TAB_HEIGHT;
        int w = (paletteW - 6) / 2;
        return new Rect(paletteX + 2 + w + 2, y, w, 12);
    }

    private Rect powderRect(int index) {
        int cellW = (paletteW - 6) / POWDER_COLUMNS;
        int row = index / POWDER_COLUMNS;
        int column = index % POWDER_COLUMNS;
        int top = paletteY + 2 + 4 * TAB_HEIGHT + 15;
        return new Rect(paletteX + 3 + column * cellW, top + row * POWDER_CELL_HEIGHT,
                cellW - 1, POWDER_CELL_HEIGHT - 1);
    }

    private Rect slotRect(int index) {
        int cellW = (paletteW - 6) / 4;
        int y = paletteY + paletteH - 21;
        return new Rect(paletteX + 3 + index * cellW, y, cellW - 1, 18);
    }

    private Rect candidateRect(int index) {
        return new Rect(sideX + 3, sideY + 16 + index * 26, sideW - 6, 25);
    }

    private Rect nodeRowRect(int nodeTop, int row) {
        return new Rect(sideX + 3, nodeTop + 13 + row * NODE_ROW_HEIGHT, sideW - 6, NODE_ROW_HEIGHT - 1);
    }

    private Rect sideButtonRect(int index) {
        int top = sideY + sideH - SIDE_BUTTON_HEIGHT * 6 - 4;
        return new Rect(sideX + 3, top + index * SIDE_BUTTON_HEIGHT, sideW - 6, SIDE_BUTTON_HEIGHT - 1);
    }

    private Rect redrawRect() { return sideButtonRect(0); }
    private Rect undoRect() { return sideButtonRect(1); }
    private Rect clearRect() { return sideButtonRect(2); }
    private Rect deleteRect() { return sideButtonRect(3); }
    private Rect manualRect() { return sideButtonRect(4); }
    private Rect transcribeRect() { return sideButtonRect(5); }

    private Rect typeRect(int index) {
        int menuW = 168;
        int menuH = 96;
        int menuX = canvasAreaX + (canvasAreaW - menuW) / 2;
        int menuY = canvasAreaY + (canvasAreaH - menuH) / 2;
        int column = index / 7;
        int row = index % 7;
        int cellW = 80;
        return new Rect(menuX + 3 + column * cellW, menuY + 13 + row * 11, cellW - 3, 10);
    }

    private boolean insideCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasAreaX && mouseX < canvasAreaX + canvasAreaW
                && mouseY >= canvasAreaY && mouseY < canvasAreaY + canvasAreaH;
    }

    private double cell() {
        int maxSide = Math.max(state.canvasW(), state.canvasH());
        if (maxSide <= 0) return 1.0;
        return Math.min(canvasAreaW - 8, canvasAreaH - 8) / (double) maxSide * zoom;
    }

    /**
     * 鼠标是不是真的压在格子上。
     *
     * <p>画布区比网格大的时候（画布不是正方形，或者缩小看整张阵），网格四周会留一圈空白边距。
     * 移动功能块只认格子里的位置，鼠标进了空白就不再跟，免得块被硬塞到最上面 / 最左边那一行。</p>
     */
    private boolean insideGrid(double mouseX, double mouseY) {
        double cell = cell();
        if (cell <= 0) return false;
        double left = gridLeft();
        double top = gridTop();
        return mouseX >= left && mouseX < left + state.canvasW() * cell
                && mouseY >= top && mouseY < top + state.canvasH() * cell;
    }

    private int gridLeft() {
        return canvasAreaX + (canvasAreaW - (int) Math.round(state.canvasW() * cell())) / 2 + panX;
    }

    private int gridTop() {
        return canvasAreaY + (canvasAreaH - (int) Math.round(state.canvasH() * cell())) / 2 + panY;
    }

    private int cellX(double mouseX) {
        double cell = cell();
        if (cell <= 0) return -1;
        int value = (int) Math.floor((mouseX - gridLeft()) / cell);
        return value < 0 || value >= state.canvasW() ? -1 : value;
    }

    private int cellY(double mouseY) {
        double cell = cell();
        if (cell <= 0) return -1;
        int value = (int) Math.floor((mouseY - gridTop()) / cell);
        return value < 0 || value >= state.canvasH() ? -1 : value;
    }

    /** 框选用：网格外也夹到最近的格，拖到画布边上不会突然断掉。 */
    private int clampCellX(double mouseX) {
        double cell = cell();
        if (cell <= 0) return 0;
        return Mth.clamp((int) Math.floor((mouseX - gridLeft()) / cell), 0, Math.max(0, state.canvasW() - 1));
    }

    private int clampCellY(double mouseY) {
        double cell = cell();
        if (cell <= 0) return 0;
        return Mth.clamp((int) Math.floor((mouseY - gridTop()) / cell), 0, Math.max(0, state.canvasH() - 1));
    }

    private int nodePixelX(MagicCircleSyncPayload.NodeView node, int gridLeft, double cell) {
        return gridLeft + (int) Math.round(node.x() * cell);
    }

    private int nodePixelY(MagicCircleSyncPayload.NodeView node, int gridTop, double cell) {
        return gridTop + (int) Math.round(node.y() * cell);
    }

    // ------------------------------------------------------------------ 缩放 / 平移

    /**
     * 缩放到 {@code target}，并且让鼠标底下的那一格尽量待在原地。
     *
     * <p>做法：先记下鼠标现在指着画布上的哪个位置（按格算），改完缩放再反算平移量，
     * 把那个位置挪回鼠标下面。这样放大看细节时不用一边滚一边再挪画布。</p>
     */
    private void zoomAt(double target, double anchorX, double anchorY) {
        double next = Mth.clamp(target, MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(next - zoom) < 1e-6) return;
        double before = cell();
        double anchorCellX = before > 0 ? (anchorX - gridLeft()) / before : 0;
        double anchorCellY = before > 0 ? (anchorY - gridTop()) / before : 0;
        zoom = next;
        double after = cell();
        int baseLeft = canvasAreaX + (canvasAreaW - (int) Math.round(state.canvasW() * after)) / 2;
        int baseTop = canvasAreaY + (canvasAreaH - (int) Math.round(state.canvasH() * after)) / 2;
        panX = (int) Math.round(anchorX - anchorCellX * after - baseLeft);
        panY = (int) Math.round(anchorY - anchorCellY * after - baseTop);
        clampPan();
    }

    /** 把平移量夹住：网格至少要有 {@link #VIEW_MARGIN} 像素留在画布区里，缩到最小也不会把阵弄丢。 */
    private void clampPan() {
        double cell = cell();
        int gridW = (int) Math.round(state.canvasW() * cell);
        int gridH = (int) Math.round(state.canvasH() * cell);
        int baseLeft = canvasAreaX + (canvasAreaW - gridW) / 2;
        int baseTop = canvasAreaY + (canvasAreaH - gridH) / 2;
        int minX = canvasAreaX + VIEW_MARGIN - (baseLeft + gridW);
        int maxX = canvasAreaX + canvasAreaW - VIEW_MARGIN - baseLeft;
        int minY = canvasAreaY + VIEW_MARGIN - (baseTop + gridH);
        int maxY = canvasAreaY + canvasAreaH - VIEW_MARGIN - baseTop;
        panX = minX <= maxX ? Mth.clamp(panX, minX, maxX) : 0;
        panY = minY <= maxY ? Mth.clamp(panY, minY, maxY) : 0;
    }

    /** 回到 1 倍、居中的视角（Ctrl+滚轮滚飞了的时候用）。 */
    private void resetView() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
    }

    // ------------------------------------------------------------------ 数据小工具

    private List<MagicCircleSyncPayload.PowderView> visiblePowders(MagicCirclePowders.Category category) {
        List<MagicCircleSyncPayload.PowderView> list = new ArrayList<>();
        if (commonOnly) {
            List<MagicCircleSyncPayload.PowderView> all = powdersFor(category);
            for (String recent : recentPowders) {
                for (MagicCircleSyncPayload.PowderView view : all) {
                    if (view.itemId().equals(recent) && !list.contains(view)) list.add(view);
                }
            }
            for (MagicCircleSyncPayload.PowderView view : all) {
                if (list.size() >= 3) break;
                if (view.unlocked() && !list.contains(view)) list.add(view);
            }
            return list;
        }
        return powdersFor(category);
    }

    private List<MagicCircleSyncPayload.PowderView> powdersFor(MagicCirclePowders.Category category) {
        List<MagicCircleSyncPayload.PowderView> list = new ArrayList<>();
        for (MagicCircleSyncPayload.PowderView view : state.powders()) {
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(view.itemId());
            if (powder != null && powder.category() == category) list.add(view);
        }
        return list;
    }

    private int unlockedCount(MagicCirclePowders.Category category) {
        int count = 0;
        for (MagicCircleSyncPayload.PowderView view : state.powders()) {
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(view.itemId());
            if (powder != null && powder.category() == category && view.unlocked()) count++;
        }
        return count;
    }

    private boolean isUnlocked(String itemId) {
        for (MagicCircleSyncPayload.PowderView view : state.powders()) {
            if (view.itemId().equals(itemId)) return view.unlocked();
        }
        return false;
    }

    private String selectedItem(String slotId) {
        for (MagicCircleSyncPayload.SlotView view : state.slots()) {
            if (view.slot().equals(slotId)) return view.itemId();
        }
        return "";
    }

    private boolean isSelected(MagicCirclePowders.Powder powder) {
        return powder.itemId().equals(selectedItem(powder.slot().id()));
    }

    private int countInInventory(Item item) {
        if (minecraft == null || minecraft.player == null) return 0;
        int total = 0;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (stack.is(item)) total += stack.getCount();
        }
        for (ItemStack stack : minecraft.player.getInventory().offhand) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private int nearestNode(int x, int y, int radius) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < state.nodes().size(); i++) {
            MagicCircleSyncPayload.NodeView node = state.nodes().get(i);
            int dx = Math.abs(node.x() - x);
            int dy = Math.abs(node.y() - y);
            if (dx > radius || dy > radius) continue;
            int distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static MagicCirclePowders.Category slotCategory(int slotIndex) {
        return switch (slotIndex) {
            case 0 -> MagicCirclePowders.Category.ELEMENT;
            case 1 -> MagicCirclePowders.Category.VANILLA;
            case 2 -> MagicCirclePowders.Category.MANA_CRYSTAL;
            default -> MagicCirclePowders.Category.DYE;
        };
    }

    private static int scoreColor(double score) {
        if (score >= 0.80) return COLOR_OK;
        if (score >= 0.68) return COLOR_GOLD;
        return 0xFFFFB36B;
    }

    private void drawSmallButton(GuiGraphics graphics, Rect rect, Component label, boolean active,
                                 int mouseX, int mouseY) {
        boolean hover = rect.contains(mouseX, mouseY);
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                active ? 0xAA4A2E6E : hover ? 0x88302A46 : 0x44120F20);
        graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, active ? 0xFFFFD86B : 0xFF4A3B60);
        graphics.drawString(font, label, rect.x + (rect.w - font.width(label)) / 2, rect.y + 2,
                active ? 0xFFFFE4A8 : COLOR_DIM, false);
    }

    private void drawSideButton(GuiGraphics graphics, Rect rect, Component label, int mouseX, int mouseY,
                                boolean enabled) {
        boolean hover = rect.contains(mouseX, mouseY);
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                !enabled ? 0x33120F20 : hover ? 0xAA4A2E6E : 0x66211A33);
        graphics.renderOutline(rect.x, rect.y, rect.w, rect.h,
                !enabled ? 0xFF332A44 : hover ? 0xFFFFD86B : 0xFF5A4770);
        graphics.drawString(font, label, rect.x + (rect.w - font.width(label)) / 2, rect.y + 4,
                enabled ? (hover ? 0xFFFFE4A8 : COLOR_TEXT) : 0xFF6A5F7E, false);
    }
}
