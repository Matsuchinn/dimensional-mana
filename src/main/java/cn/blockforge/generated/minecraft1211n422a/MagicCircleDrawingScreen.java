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
    private static final int SIDE_BUTTON_HEIGHT = 17;
    /** r214：右列顶部常驻的「节点详情」区高度（标题 + 四行内容）。 */
    private static final int DETAIL_H = 56;
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
    /** r212：右侧一列改成滚动列表，这是当前滚动位置（像素）。 */
    private int sideScroll;
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
    /**
     * r218：三块边栏的<b>临时收起</b>开关（纯客户端显示状态，不动任何数据）。
     * 面板外沿各有一颗小箭头钮，点一下收起来、再点一下放回去，方便玩家干净地看整张图。
     */
    private boolean hidePalette;
    private boolean hideSide;
    private boolean hideFooter;
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
    /** v2.2：编辑器当前页 —— 0 = 选粉末，1 = 边界声明（条件节点声明模式才有第 2 页）。 */
    private int editorDeclPage;
    /** v2.2：声明页每行按钮的命中区，渲染时收集、点击时消费。 */
    private final List<DeclHit> declHits = new ArrayList<>();
    /** 本层客户端图分析（终端 / 前置角标用），状态同步时重算。 */
    private MagicCircleGraph.Analysis clientAnalysis;

    /** 声明页一个可点控件：矩形 + 目标边界组 + 动作码 + 附加参数。 */
    private record DeclHit(int x, int y, int w, int h, MagicCircleDecl.Group group, String action, int arg) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
    /** 最近一次渲染时的鼠标位置：Ctrl+V 之类没有鼠标事件的快捷键要靠它定粘贴位置。 */
    private int pointerX, pointerY;

    private final Deque<String> recentPowders = new ArrayDeque<>();
    private final List<Component> hoverPowder = new ArrayList<>();
    /** r214：鼠标正压在哪个功能块上，明细钉在右列顶部的常驻区，不再跟着鼠标飘。-1 = 没有。 */
    private int detailHover = -1;
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
        clientAnalysis = computeClientAnalysis(payload);
    }

    /**
     * 把同步状态里的功能块拼成一个内层网络，交给图分析器认脸：
     * 只为画布上「终端 / 前置 / 声明 / 门」角标服务，出错就退回无角标，不影响编辑。
     */
    private MagicCircleGraph.Analysis computeClientAnalysis(MagicCircleSyncPayload payload) {
        if (payload.nodes().isEmpty()) return null;
        try {
            MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
            boolean coreSugar = false;
            for (MagicCircleSyncPayload.NodeView view : payload.nodes()) {
                MagicCircleBlockType type = MagicCircleBlockType.byId(view.type());
                if (type == null) continue;
                layer.add(new MagicCircleNetwork.Node(type, view.x(), view.y(), view.element(),
                        view.semantic(), view.power(), view.dye(), view.variant(), view.handDrawn(),
                        view.declare(), view.gate()));
                if (type.isCore() && "minecraft:sugar".equals(view.semantic())) coreSugar = true;
            }
            for (MagicCircleSyncPayload.LinkView link : payload.links()) {
                layer.addLink(link.from(), link.to());
            }
            return MagicCircleGraph.analyze(layer, MagicCircleConfig.DECLARE_MODE_ENABLED.get(), coreSugar);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    @Override
    protected void init() {
        panelW = Math.max(320, Math.min(PANEL_MAX_WIDTH, width - 16));
        panelH = Math.max(220, Math.min(PANEL_MAX_HEIGHT, height - 16));
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        // r218：收起的边栏宽度 / 高度直接归零，画布吃掉腾出来的地方。
        int contentTop = top + HEADER_HEIGHT;
        int footerTop = top + panelH - (hideFooter ? 0 : FOOTER_HEIGHT);
        int contentHeight = Math.max(120, footerTop - contentTop);
        paletteW = hidePalette ? 0 : Mth.clamp(panelW * 28 / 100, 112, 158);
        int sideWidth = hideSide ? 0 : Mth.clamp(panelW * 24 / 100, 100, 152);
        paletteX = left + (hidePalette ? 0 : GAP);
        paletteY = contentTop;
        paletteH = contentHeight;
        sideX = left + panelW - (hideSide ? 0 : GAP + sideWidth);
        sideY = contentTop;
        sideW = sideWidth;
        sideH = contentHeight;
        canvasAreaX = paletteX + paletteW + (hidePalette ? 0 : GAP);
        toolbarY = contentTop;
        canvasAreaY = contentTop + TOOLBAR_HEIGHT;
        canvasAreaW = Math.max(60, sideX - (hideSide ? 0 : GAP) - canvasAreaX);
        canvasAreaH = Math.max(60, contentHeight - TOOLBAR_HEIGHT);
        footerY = footerTop;
        // 窗口大小变了（F11 / 拖动窗口）时，正开着的命名框要跟着挪，输入的字不丢。
        if (naming) layoutNameBox();
    }

    /** 左列粉盘外沿的收起 / 展开小钮（r218）。 */
    private Rect paletteToggleRect() {
        return new Rect(left - 1, top + panelH / 2 - 12, 12, 24);
    }

    /** 右列外沿的收起 / 展开小钮。 */
    private Rect sideToggleRect() {
        return new Rect(left + panelW - 11, top + panelH / 2 - 12, 12, 24);
    }

    /** 底栏上沿正中的收起 / 展开小钮。 */
    private Rect footerToggleRect() {
        return new Rect(left + panelW / 2 - 14, top + panelH - (hideFooter ? 12 : FOOTER_HEIGHT + 2), 28, 12);
    }

    /** 画三颗收起小钮：压在面板边线上，收起后仍在原地，点回去就展开。 */
    private void renderPanelToggles(GuiGraphics graphics, int mouseX, int mouseY) {
        toggleButton(graphics, paletteToggleRect(), hidePalette ? "▶" : "◀",
                "gui.dimensional_mana.magiccircle.toggle.palette", mouseX, mouseY);
        toggleButton(graphics, sideToggleRect(), hideSide ? "◀" : "▶",
                "gui.dimensional_mana.magiccircle.toggle.side", mouseX, mouseY);
        toggleButton(graphics, footerToggleRect(), hideFooter ? "▲" : "▼",
                "gui.dimensional_mana.magiccircle.toggle.footer", mouseX, mouseY);
    }

    private void toggleButton(GuiGraphics graphics, Rect rect, String arrow, String tipKey,
                              int mouseX, int mouseY) {
        boolean hover = rect.contains(mouseX, mouseY);
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, hover ? 0xE6432C63 : 0xC01A1428);
        graphics.renderOutline(rect.x, rect.y, rect.w, rect.h, hover ? 0xFFFFD86B : 0xFF5A4770);
        graphics.drawString(font, arrow, rect.x + (rect.w - font.width(arrow)) / 2,
                rect.y + (rect.h - font.lineHeight) / 2 + 1, hover ? 0xFFFFE4A8 : COLOR_TEXT, false);
        if (hover) {
            hoverPowder.add(Component.translatable(tipKey).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
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
        detailHover = -1;
        renderHeader(graphics, mouseX, mouseY);
        renderToolbar(graphics, mouseX, mouseY);
        renderCanvas(graphics, mouseX, mouseY);
        if (!hidePalette) renderPalette(graphics, mouseX, mouseY);
        if (!hideSide) renderSide(graphics, mouseX, mouseY);
        if (!hideFooter) renderFooter(graphics);
        renderPanelToggles(graphics, mouseX, mouseY);
        renderOverlays(graphics, mouseX, mouseY);
        // 弹框盖住整块面板时，底下的粉末提示就别再冒头了。
        if (naming) return;
        if (!hoverPowder.isEmpty()) {
            graphics.renderComponentTooltip(font, hoverPowder, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, left + GAP + 2, top + 4, COLOR_GOLD, false);
        String subtitle = Component.translatable("gui.dimensional_mana.magiccircle.subtitle",
                state.baseW(), state.baseH(), state.canvasW(), state.canvasH(), state.layer() + 1).getString();
        graphics.drawString(font, subtitle, left + GAP + 2, top + 15, COLOR_DIM, false);
        // r218：右列收起后 sideX 贴到面板右沿，这两行要让开右上角的关闭钮（面板右沿 -24）。
        int headerRight = hideSide ? left + panelW - 24 : sideX - 4;
        String level = Component.translatable(state.freePowder()
                        ? "gui.dimensional_mana.magiccircle.level.creative"
                        : "gui.dimensional_mana.magiccircle.level",
                state.magicLevel(), state.unlockedPowders(), state.totalPowders()).getString();
        graphics.drawString(font, level, headerRight - font.width(level), top + 4,
                state.freePowder() ? COLOR_GOLD : COLOR_AQUA, false);
        String owner = Component.translatable("gui.dimensional_mana.magiccircle.owner",
                state.ownerName().isEmpty() ? "-" : state.ownerName()).getString();
        graphics.drawString(font, owner, headerRight - font.width(owner), top + 15, COLOR_DIM, false);
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
            // v2.2 角色角标：终端=黄、前置=青、声明=紫、门=灰，标在块的右下角。
            String roleMark = roleMark(i, type);
            if (roleMark != null && size >= 12) {
                graphics.drawString(font, roleMark, cx + size / 2 - 8, cy + size / 2 - 8,
                        roleMarkColor(i), false);
            }
            // 图分析报了问题的块：左上角打红点，跟页脚红字对得上号。
            if (clientAnalysis != null && clientAnalysis.flagged.contains(i)) {
                drawIssueBadge(graphics, cx - size / 2 + 3, cy - size / 2 + 3);
            }
            // 鼠标压在功能块上：明细交给右列顶部的常驻「节点详情」区（r214，不再飘 tooltip）。
            if (!typeMenuOpen && !drawing && !manualMode && !editorOpen && !contextMenu && !draggingNode
                    && !moveMode
                    && mouseX >= cx - size / 2 - 2 && mouseX <= cx + size / 2 + 2
                    && mouseY >= cy - size / 2 - 2 && mouseY <= cy + size / 2 + 2) {
                detailHover = i;
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
                switch (powder.category()) {
                    // 魔晶粉是功率槽：v2.1 起它直接换算成「每点效率降 1% 消耗」。
                    case MANA_CRYSTAL -> hoverPowder.add(Component.translatable(
                            "gui.dimensional_mana.magiccircle.powder.efficiency",
                            Math.round(MagicCirclePowders.powerEfficiency(powder.itemId()) * 100))
                            .copy().withStyle(net.minecraft.ChatFormatting.GOLD));
                    // 其余三类在 v2.1 里不再是消耗乘数，改成说明各自的作用。
                    case VANILLA -> hoverPowder.add(Component.translatable(
                            "gui.dimensional_mana.magiccircle.powder.role.semantic")
                            .copy().withStyle(net.minecraft.ChatFormatting.GOLD));
                    case ELEMENT -> hoverPowder.add(Component.translatable(
                            "gui.dimensional_mana.magiccircle.powder.role.element")
                            .copy().withStyle(net.minecraft.ChatFormatting.GOLD));
                    case DYE -> hoverPowder.add(Component.translatable(
                            "gui.dimensional_mana.magiccircle.powder.role.dye")
                            .copy().withStyle(net.minecraft.ChatFormatting.GOLD));
                }
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

    /**
     * 右侧一列：上头「识别候选 + 功能块管理」整段做成<b>可滚轮上下滚</b>的列表，
     * 底下六个动作按钮和蓝图槽位固定不动，两段用剪裁线隔开，内容再多也不会叠字。
     */
    private void renderSide(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0x33120F20);
        graphics.renderOutline(sideX, sideY, sideW, sideH, 0xFF4A3B60);

        renderDetailBox(graphics);

        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.candidate.title"),
                sideX + 4, sideY + DETAIL_H + 6, COLOR_GOLD, false);
        int viewTop = sideViewportTop();
        int viewBottom = sideViewportBottom();
        int viewportH = Math.max(24, viewBottom - viewTop);
        viewBottom = viewTop + viewportH;
        int contentH = sideContentHeight();
        int maxScroll = Math.max(0, contentH - viewportH);
        // 选中的块滚出视野时，顺手把它滚回来，不用手动找。
        if (selectedNode >= 0 && selectedNode < state.nodes().size()) {
            int relTop = sideCandidatesH() + 6 + NODE_ROW_HEIGHT + selectedNode * NODE_ROW_HEIGHT;
            if (relTop - sideScroll < 0) sideScroll = relTop;
            if (relTop + NODE_ROW_HEIGHT - sideScroll > viewportH) sideScroll = relTop + NODE_ROW_HEIGHT - viewportH;
        }
        sideScroll = Mth.clamp(sideScroll, 0, maxScroll);

        graphics.enableScissor(sideX + 1, viewTop, sideX + sideW - 2, viewBottom);
        if (!state.candidates().isEmpty()) {
            for (int i = 0; i < state.candidates().size(); i++) {
                Rect rect = candidateRect(i);
                MagicCircleSyncPayload.CandidateView view = state.candidates().get(i);
                MagicCircleGlyph glyph = MagicCircleGlyph.byId(view.glyph());
                if (glyph == null) continue;
                boolean hover = sideVisible(rect) && rect.contains(mouseX, mouseY);
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
            graphics.drawWordWrap(font, message, sideX + 5, viewTop + 2 - sideScroll, sideW - 10,
                    failed ? 0xFFFFD86B : COLOR_DIM);
        }

        int nodeTitleY = viewTop + sideCandidatesH() + 6 - sideScroll;
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.nodes.title",
                state.nodes().size()), sideX + 4, nodeTitleY, COLOR_GOLD, false);
        if (state.nodes().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.nodes.empty"),
                    sideX + 4, nodeTitleY + 13, COLOR_DIM, false);
        } else {
            // 列表不再只显示 4 行：全部铺进滚动区，块多就滚轮翻。
            for (int index = 0; index < state.nodes().size(); index++) {
                Rect rect = nodeRowRect(index);
                MagicCircleSyncPayload.NodeView node = state.nodes().get(index);
                MagicCircleBlockType type = MagicCircleBlockType.byId(node.type());
                boolean hover = sideVisible(rect) && rect.contains(mouseX, mouseY);
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
                    detailHover = index;
                }
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            // 细滚动条：告诉你这列还能滚、现在滚到哪儿了。
            int trackTop = viewTop + 1;
            int trackH = viewBottom - trackTop - 2;
            int thumbH = Math.max(20, trackH * viewportH / Math.max(1, contentH));
            int thumbY = trackTop + (int) ((long) (trackH - thumbH) * sideScroll / maxScroll);
            graphics.fill(sideX + sideW - 4, trackTop, sideX + sideW - 2, trackTop + trackH, 0x33FFFFFF);
            graphics.fill(sideX + sideW - 4, thumbY, sideX + sideW - 2, thumbY + thumbH, 0xBBAE8FD8);
        }

        renderBlueprintSlot(graphics, mouseX, mouseY);
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

    /** 滚动视口顶部：让开顶部常驻的「节点详情」区，再紧挨「识别候选」标题下沿。 */
    private int sideViewportTop() {
        return sideY + DETAIL_H + 17;
    }

    /**
     * 滚动视口底部：刚好卡在 6 颗动作按钮上方 2 像素处。
     *
     * <p>r215 修正：底下这摞固定件原来算错了一层——蓝图槽位卡按「按钮顶部 + 2」定位，
     * 正压在「重画 / 撤销」上（用户截图里糊成一坨的就是它）。现在右列自下而上严格排队：
     * <b>蓝图槽位卡 → 6 颗按钮 → 滚动列表</b>，谁也不压谁。</p>
     */
    private int sideViewportBottom() {
        return sideButtonsTop() - 2;
    }

    /** 候选区占用高度：有候选按每个 26 像素，没有就留两行提示字的高度。 */
    private int sideCandidatesH() {
        return state.candidates().isEmpty() ? 28 : state.candidates().size() * 26;
    }

    /** 滚动内容总高：候选区 + 功能块标题 + 所有块（空列表也留一行占位）。 */
    private int sideContentHeight() {
        return sideCandidatesH() + 6 + NODE_ROW_HEIGHT + Math.max(1, state.nodes().size()) * NODE_ROW_HEIGHT + 4;
    }

    /** 这块矩形现在是不是真的露在滚动视口里（滚出去的不许当悬停 / 点击目标）。 */
    private boolean sideVisible(Rect rect) {
        int top = sideViewportTop();
        int bottom = top + Math.max(24, sideViewportBottom() - top);
        return rect.y + rect.h > top && rect.y < bottom;
    }

    /**
     * r214：右列顶部的常驻「节点详情」区。原来是跟着鼠标飘的 tooltip，悬停列表行时
     * 一大块正好盖住右列的按钮（用户截图里"还是重叠"就是这个）；现在改成固定 UI 区域：
     * 悬停画布块 / 列表行立刻显示在这里，没悬停时兜底显示当前选中的块，字永远压不到按钮。
     */
    private void renderDetailBox(GuiGraphics graphics) {
        int x = sideX + 3;
        int y = sideY + 2;
        int w = sideW - 6;
        graphics.fill(x, y, x + w, y + DETAIL_H, 0xE6140F22);
        graphics.renderOutline(x, y, w, DETAIL_H, detailHover >= 0 ? 0xFFAE8FD8 : 0xFF4A3B60);
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.detail.title"),
                x + 3, y + 3, COLOR_GOLD, false);
        int index = detailHover >= 0 ? detailHover : selectedNode;
        if (index < 0 || index >= state.nodes().size()) {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                            "gui.dimensional_mana.magiccircle.detail.none").getString(), w - 6),
                    x + 3, y + 17, COLOR_DIM, false);
            return;
        }
        MagicCircleSyncPayload.NodeView node = state.nodes().get(index);
        MagicCircleBlockType type = MagicCircleBlockType.byId(node.type());
        // 第一行：第几层 · 什么块 (坐标)；右侧带角色角标（终/缀/declare/门），手绘的补一个"·手"。
        String head = Component.translatable("tooltip.dimensional_mana.magiccircle.node.header",
                state.layer() + 1, type == null ? node.type() : type.fallbackName(), node.x(), node.y()).getString()
                + (node.handDrawn() ? "·手" : "");
        String role = roleMark(index, type);
        graphics.drawString(font, font.plainSubstrByWidth(head, role == null ? w - 6 : w - 18),
                x + 3, y + 14, COLOR_GOLD, false);
        if (role != null) {
            graphics.drawString(font, role, x + w - 11, y + 14, roleMarkColor(index), false);
        }
        // 第二行：触发顺序；同层并列的补一句，免得看见两个「2」以为显示错了。
        int order = flow().order(index);
        if (order > 0) {
            int siblings = layerSiblings(order);
            String text = Component.translatable("tooltip.dimensional_mana.magiccircle.node.order", order).getString()
                    + (siblings > 1 ? "·并列" + siblings : "");
            graphics.drawString(font, font.plainSubstrByWidth(text, w - 6), x + 3, y + 24, 0xFF8FE3FF, false);
        } else {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                            "gui.dimensional_mana.magiccircle.detail.order.none").getString(), w - 6),
                    x + 3, y + 24, 0xFFFF9BA8, false);
        }
        // 第三/四行：四个粉槽的短名（和列表行同一口径，全名会撑爆 150 像素的列宽）。
        int nx = drawDetailPair(graphics, x + 3, y + 34, "element", node.element(), x + w - 66);
        drawDetailPair(graphics, nx + 4, y + 34, "semantic", node.semantic(), x + w - 4);
        nx = drawDetailPair(graphics, x + 3, y + 44, "power", node.power(), x + w - 64);
        drawDetailPair(graphics, nx + 4, y + 44, "dye", node.dye(), x + w - 4);
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

    /** 详情区里「标签:短名」一段：画完返回下一个标签该开始的 x 坐标。 */
    private int drawDetailPair(GuiGraphics graphics, int x, int y, String slotKey, String itemId, int maxX) {
        String label = Component.translatable("gui.dimensional_mana.magiccircle.slot." + slotKey).getString() + ":";
        if (x >= maxX) return x;
        graphics.drawString(font, label, x, y, 0xFFBDA9DE, false);
        x += font.width(label);
        MagicCirclePowders.Powder powder = MagicCirclePowders.get(itemId);
        String value = powder == null
                ? Component.translatable("tooltip.dimensional_mana.magiccircle.node.empty").getString()
                : MagicCirclePowders.shortName(itemId);
        value = font.plainSubstrByWidth(value, Math.max(0, maxX - x));
        graphics.drawString(font, value, x, y, powder == null ? 0xFF8A8194 : 0xFF8FE3FF, false);
        return x + font.width(value);
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
                builder.append(MagicCircleIssue.component(key).getString());
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
        int h = editorHeight();
        int x = canvasAreaX + (canvasAreaW - EDITOR_WIDTH) / 2;
        int y = canvasAreaY + (canvasAreaH - h) / 2;
        return new int[]{
                Mth.clamp(x, left + 4, Math.max(left + 4, left + panelW - EDITOR_WIDTH - 4)),
                Mth.clamp(y, top + 4, Math.max(top + 4, top + panelH - h - 4))};
    }

    private Rect editorRect() {
        int[] origin = editorOrigin();
        return new Rect(origin[0], origin[1], EDITOR_WIDTH, editorHeight());
    }

    private Rect editorSlotRect(int index) {
        int[] origin = editorOrigin();
        return new Rect(origin[0] + 4, origin[1] + 27 + index * EDITOR_ROW_HEIGHT,
                EDITOR_WIDTH - 8, EDITOR_ROW_HEIGHT - 1);
    }

    /** 当前编辑的是不是「声明模式」的条件节点（渲染与点击判定必须用同一个口径）。 */
    private boolean editorDeclMode() {
        if (editorNode < 0 || editorNode >= state.nodes().size()) return false;
        MagicCircleSyncPayload.NodeView view = state.nodes().get(editorNode);
        return MagicCircleBlockType.byId(view.type()) == MagicCircleBlockType.CONDITION
                && !MagicCircleGates.isGateMode(view.semantic(), view.gate())
                && !MagicCircleDecl.Group.familyOf(view.semantic()).isEmpty();
    }

    /** 门控模式的条件节点（门控规范 v2.4）：走「门控页」——⇄ 模式钮 + ⇌ 比较方向钮 + 阈值预览。 */
    private boolean editorGateMode() {
        if (editorNode < 0 || editorNode >= state.nodes().size()) return false;
        MagicCircleSyncPayload.NodeView view = state.nodes().get(editorNode);
        return MagicCircleBlockType.byId(view.type()) == MagicCircleBlockType.CONDITION
                && MagicCircleGates.isGateMode(view.semantic(), view.gate());
    }

    /** 底座形态的触发节点（v2.6 一 2）：槽位行罩红纱 + 黄字提示，只当逻辑起点。 */
    private boolean editorBaseTrigger() {
        if (editorNode < 0 || editorNode >= state.nodes().size() || clientAnalysis == null) return false;
        MagicCircleSyncPayload.NodeView view = state.nodes().get(editorNode);
        return MagicCircleBlockType.byId(view.type()) == MagicCircleBlockType.TRIGGER
                && clientAnalysis.isTriggerBase(editorNode);
    }

    /** 面板停在「边界声明」页：这一页不画粉末网格，命中判定也得跟着跳过，否则点空气会误换粉。 */
    private boolean editorOnDeclPage() {
        return editorDeclMode() && editorDeclPage == 1;
    }

    /** 网格顶端：声明 / 门控模式面板在 y+84~y+96 留了一行页签，网格从 88 让到 98。 */
    private int editorGridTop() {
        // r218 重排：门控 / 底座形态在网格上方留出一整块**提示带**，
        // 「[系统提示]」「当周围 N 格内…放行」这类动态文案全画在带里，
        // 绝不再压到网格行或底部按钮上（用户图 11 / 图 12 圈出来的就是提示字叠进网格）。
        if (editorGateMode()) return 130;   // 页签 84..96 + 提示三行 97..127
        if (editorBaseTrigger()) return 120; // 提示三行 84..117
        return editorDeclMode() ? 98 : 88;
    }

    /** 网格行高：让出页签后每行压 1 像素，保证染料 16 格还是放得下。 */
    private int editorCellH() {
        return editorGridTop() != 88 ? 18 : EDITOR_CELL_HEIGHT;
    }

    /** 「拿下来」按钮顶端，跟着网格底部走。 */
    private int editorClearTop() {
        return editorGridTop() + 4 * editorCellH() + 2;
    }

    /** 弹窗总高（r218）：门控 / 底座带提示带时面板整体加长，提示字不再溢出到底栏上。 */
    private int editorHeight() {
        return Math.max(EDITOR_HEIGHT, editorClearTop() + 14 + 15);
    }

    private Rect editorCellRect(int index) {
        int[] origin = editorOrigin();
        int cellWidth = (EDITOR_WIDTH - 8) / EDITOR_COLUMNS;
        int row = index / EDITOR_COLUMNS;
        int column = index % EDITOR_COLUMNS;
        int cellHeight = editorCellH();
        return new Rect(origin[0] + 4 + column * cellWidth,
                origin[1] + editorGridTop() + row * cellHeight, cellWidth - 1, cellHeight - 1);
    }

    private Rect editorClearRect() {
        int[] origin = editorOrigin();
        return new Rect(origin[0] + 4, origin[1] + editorClearTop(), EDITOR_WIDTH - 8, 14);
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
        // 鼠标落在面板里：面板底下粉末盘收集的悬浮提示作废（节点详情不飘窗了，不怕挡）。
        int h = editorHeight();
        if (mouseX >= x - 5 && mouseX <= x + EDITOR_WIDTH + 5
                && mouseY >= y - 3 && mouseY <= y + h + 5) {
            hoverPowder.clear();
            hoveredPowder = -1;
        }
        // 投影一圈 + 全不透明底色：r209 底色只有 0xF0，画布上的白字会透进面板里乱视线。
        graphics.fill(x - 5, y - 1, x + EDITOR_WIDTH + 5, y + h + 5, 0x80000000);
        graphics.fill(x - 3, y - 3, x + EDITOR_WIDTH + 3, y + h + 3, 0xFF0F0B1C);
        graphics.renderOutline(x - 3, y - 3, EDITOR_WIDTH + 6, h + 6, 0xFFD9A8FF);
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

        MagicCircleSyncPayload.NodeView editorNodeView = state.nodes().get(editorNode);
        boolean declMode = editorDeclMode();
        declHits.clear();
        if (editorBaseTrigger()) {
            renderBaseTriggerMask(graphics, x, y, editorNodeView);
        }
        if (editorGateMode()) {
            renderGateTabs(graphics, x, y + 84, mouseX, mouseY, editorNodeView);
        }
        if (declMode) {
            renderDeclTabs(graphics, x, y + 84, mouseX, mouseY);
            if (editorDeclPage == 1) {
                renderDeclPage(graphics, x, y, editorNodeView);
                graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.decl.hint"),
                        x + 4, y + 186, COLOR_DIM, false);
                return;
            }
        }

        List<MagicCircleSyncPayload.PowderView> powders = editorPowders();
        for (int i = 0; i < powders.size(); i++) {
            Rect rect = editorCellRect(i);
            if (rect.y + editorCellH() > y + editorClearTop()) break;
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
                x + 4, y + editorHeight() - 15, COLOR_DIM, false);
    }

    /**
     * 按像素宽自动折行画一段动态文案（r218）：门控解析句 / 上游条件摘要长短不定，
     * 单行截断经常把「…时放行」这种关键尾巴吃掉，超过的行数末尾补省略号。
     */
    private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxW,
                             int maxLines, int color) {
        int done = 0;
        int n = text.length();
        for (int line = 0; line < maxLines && done < n; line++) {
            int take = 0;
            int w = 0;
            while (done + take < n) {
                int cw = font.width(text.substring(done + take, done + take + 1));
                if (w + cw > maxW - (done + take + 1 < n ? font.width("…") : 0)) break;
                w += cw;
                take++;
            }
            if (take == 0) take = 1;
            boolean more = done + take < n;
            String segment = text.substring(done, done + take) + (more ? "…" : "");
            graphics.drawString(font, segment, x, y + line * 10, color, false);
            done += take;
        }
    }

    private Rect editorCloseRect() {
        int[] origin = editorOrigin();
        return new Rect(origin[0] + EDITOR_WIDTH - 16, origin[1] + 3, 12, 12);
    }

    /** v2.2 角色角标文字；普通块返回 null 不标。 */
    private String roleMark(int index, MagicCircleBlockType type) {
        if (clientAnalysis == null || type == null) return null;
        MagicCircleGraph.Role role = clientAnalysis.roles.get(index);
        if (role == null) return null;
        return switch (role) {
            case TERMINAL -> "终";
            case PREFIX -> "缀";
            case DECL -> "宣";
            case GATE -> "门";
            case TRIGGER_BASE -> "座";
            case NORMAL -> null;
        };
    }

    private int roleMarkColor(int index) {
        if (clientAnalysis == null) return COLOR_TEXT;
        MagicCircleGraph.Role role = clientAnalysis.roles.get(index);
        if (role == null) return COLOR_TEXT;
        return switch (role) {
            case TERMINAL -> 0xFFFFD86B;
            case PREFIX -> 0xFF7BE0FF;
            case DECL -> 0xFFD9A8FF;
            case GATE -> 0xFF9A93A8;
            case TRIGGER_BASE -> 0xFFFF9B6B;
            case NORMAL -> COLOR_TEXT;
        };
    }

    // ------------------------------------------------------------------ v2.2 声明页

    /** 页签：「≡ 边界声明」/「▦ 选粉末」/「⛊ 门控模式」。声明模式的条件节点才画。 */
    private void renderDeclTabs(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        MagicCircleSyncPayload.NodeView node = state.nodes().get(editorNode);
        int count = MagicCircleDecl.parse(node.declare()).size();
        declTab(graphics, "≡ " + Component.translatable("gui.dimensional_mana.magiccircle.decl.tab")
                .getString() + " " + count, x + 4, y, 112, 12, editorDeclPage == 1, "tab_decl", mouseX, mouseY);
        declTab(graphics, "▦ " + Component.translatable("gui.dimensional_mana.magiccircle.decl.tab.powder")
                .getString(), x + 120, y, 88, 12, editorDeclPage == 0, "tab_powder", mouseX, mouseY);
        // 门控规范 v2.4 三：声明模式一键切回门控（⇄ 同一颗钮，两个方向共用服务端 gate/mode）。
        declTab(graphics, "⛊ 门控", x + 212, y, 56, 12, false, "gate_mode", mouseX, mouseY);
    }

    /**
     * 门控页签行（规范 v2.4 三）：「⛊ 声明模式」切换钮 + 「⇌ ≤/≥」比较方向钮 + 阈值预览。
     * 比较方向按钮上直接显示当前符号；切模式不清声明串（服务端决定留不留）。
     */
    private void renderGateTabs(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                                MagicCircleSyncPayload.NodeView node) {
        declTab(graphics, "⇄ 声明", x + 4, y, 64, 12, false, "gate_mode", mouseX, mouseY);
        MagicCircleGates.Domain domain = MagicCircleGates.domainOf(node.semantic());
        if (domain == null) {
            declTab(graphics, "⇌ ?", x + 72, y, 44, 12, false, "gate_op", mouseX, mouseY);
            graphics.drawString(font, "语义槽空：装一种原版粉选判定域", x + 122, y + 3, 0xFFFF9BA8, false);
            return;
        }
        MagicCircleGates.CompareOperator op = MagicCircleGates.operatorOf(domain, node.gate());
        declTab(graphics, "⇌ " + op.symbol(), x + 72, y, 64, 12, false, "gate_op", mouseX, mouseY);
        int tier = MagicCircleDecl.dyeTier(node.dye());
        String preview = domain.label() + " " + op.symbol() + " "
                + MagicCircleGates.thresholdText(domain, MagicCircleGates.threshold(domain, tier));
        graphics.drawString(font, font.plainSubstrByWidth(preview, 146), x + 142, y + 3,
                0xFFD9A8FF, false);
        // 黄字提示（v2.6 界面约定）：这块门控直接喂着某个触发节点 = 触发链的第一步判定。
        // r218：提示行画进页签下方的**提示带**（97~127），解析句超长自动折行，
        // 不再像图 11 那样叠到「拿下来」按钮和底栏上。
        if (clientAnalysis != null && clientAnalysis.baseGates.containsValue(editorNode)) {
            graphics.drawString(font, "[系统提示] 这是触发链的第一步判定。", x + 4, y + 13, 0xFFFFD86B, false);
        }
        drawWrapped(graphics, MagicCircleGates.describe(node), x + 4, y + 23,
                EDITOR_WIDTH - 8, 2, COLOR_DIM);
    }

    /**
     * 底座形态触发节点的编辑器遮罩（v2.6 一 2）：元素 / 语义 / 染料三行罩红纱——里面的粉不再
     * 参与判定、也不额外计费；功率槽行保持可用（它还管本地效率与起爆半径）。
     */
    private void renderBaseTriggerMask(GuiGraphics graphics, int x, int y,
                                       MagicCircleSyncPayload.NodeView node) {
        MagicCirclePowders.Slot[] slots = MagicCirclePowders.Slot.values();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == MagicCirclePowders.Slot.POWER) continue; // 功率槽照常生效
            Rect rect = editorSlotRect(i);
            graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0x66C03030);
        }
        // r218：两行提示住进 84~117 的提示带，网格从 120 起画——图 12 里
        // 「系统提示」压进元素按钮那一行的事不会再发生。
        graphics.drawString(font, "[系统提示] 当前节点判定已停用，仅作为逻辑起点与效率底座。",
                x + 4, y + 85, 0xFFFFD86B, false);
        Integer gateIdx = clientAnalysis == null ? null : clientAnalysis.baseGates.get(editorNode);
        if (gateIdx != null && gateIdx >= 0 && gateIdx < state.nodes().size()) {
            String summary = MagicCircleGates.describe(state.nodes().get(gateIdx));
            drawWrapped(graphics, "真正触发需满足上游条件：" + summary, x + 4, y + 95,
                    EDITOR_WIDTH - 8, 2, 0xFF9AE08B);
        }
    }

    /**
     * 页签专用按钮：两个页签**永远可点**（r209 误把"当前停在的页"当成 enabled 登记命中区，
     * 结果没停在的那一页反而点不动，切不过去也切不回来）。选中的那个黄框高亮，悬停亮紫描边。
     */
    private void declTab(GuiGraphics graphics, String label, int x, int y, int w, int h,
                         boolean active, String action, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, active ? 0xC03D285D : hover ? 0xA042325C : 0x991A1428);
        graphics.renderOutline(x, y, w, h, active ? 0xFFFFD86B : hover ? 0xFFD9A8FF : 0xFF5A4770);
        graphics.drawString(font, font.plainSubstrByWidth(label, w - 4), x + 2,
                y + (h - font.lineHeight) / 2 + 1, active ? 0xFFFFE4A8 : COLOR_TEXT, false);
        declHits.add(new DeclHit(x, y, w, h, null, action, 0));
    }

    /**
     * 声明页：本语义能声明的每个边界组一行。已声明的给出 −/＋、枚举循环、标志翻转、×删除；
     * 未声明的给一个「+ 声明」按钮（功率容量用完就按灰）。改动只发意图，档位 / 精度夹取由服务端说了算。
     */
    private void renderDeclPage(GuiGraphics graphics, int x, int y, MagicCircleSyncPayload.NodeView node) {
        List<MagicCircleDecl.Entry> entries = MagicCircleDecl.parse(node.declare());
        List<MagicCircleDecl.Group> family = MagicCircleDecl.Group.familyOf(node.semantic());
        int capacity = MagicCircleDecl.groupCapacity(node.power());
        graphics.drawString(font, Component.translatable("gui.dimensional_mana.magiccircle.decl.capacity",
                entries.size(), capacity), x + 4, y + 98,
                entries.size() >= capacity ? 0xFFFF9BA8 : COLOR_DIM, false);
        String precisionName = MagicCircleDecl.precisionOf(node.power()).name();
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(
                "gui.dimensional_mana.magiccircle.decl.precision." + precisionName).getString(), 150),
                x + 118, y + 98, COLOR_DIM, false);
        int rowY = y + 110;
        for (MagicCircleDecl.Group group : family) {
            if (rowY > y + 150) {
                graphics.drawString(font, "…", x + 6, rowY, COLOR_DIM, false);
                break;
            }
            MagicCircleDecl.Entry entry = null;
            for (MagicCircleDecl.Entry candidate : entries) {
                if (candidate.group() == group) entry = candidate;
            }
            if (entry == null) {
                if (entries.size() < capacity) {
                    declButton(graphics, "+ " + Component.translatable(group.translationKey()).getString(),
                            x + 6, rowY, 150, 11, true, "add", group, 0);
                } else {
                    graphics.drawString(font, Component.translatable(group.translationKey()).getString(),
                            x + 6, rowY, 0xFF6A5F7E, false);
                }
                rowY += 12;
                continue;
            }
            // 第一行：名字 + 主参数 −/＋（＋第二参数）+ 删除。
            graphics.drawString(font, Component.translatable(group.translationKey()).getString(),
                    x + 4, rowY, COLOR_GOLD, false);
            int px = x + 58;
            px = declStepper(graphics, px, rowY, entry, group, entry.primary(), "minus", "plus", 0);
            if (group.secondaryMax > 0) {
                graphics.drawString(font, "次", x + 150, rowY, COLOR_DIM, false);
                declStepper(graphics, x + 160, rowY, entry, group, entry.secondary(), "secminus", "secplus", 0);
            }
            declButton(graphics, "×", x + 246, rowY, 11, 11, true, "remove", group, 0);
            rowY += 12;
            // 第二行：枚举循环 + 标志开关。
            int ex = x + 8;
            for (int e = 0; e < group.enumCount(); e++) {
                String[] options = group.enumOptions(e);
                String current = options.length == 0 ? "-"
                        : Component.translatable(options[Math.min(entry.enumValue(e), options.length - 1)]).getString();
                if (ex + 62 > x + EDITOR_WIDTH - 26) break;
                declButton(graphics, "⟨" + font.plainSubstrByWidth(current, 32) + "⟩", ex, rowY, 60, 11,
                        options.length > 0, "enum", group, e);
                ex += 63;
            }
            int fb = 0;
            for (String flag : group.flags) {
                boolean on = entry.flag(fb);
                if (ex + 52 > x + EDITOR_WIDTH - 26) break;
                declButton(graphics, (on ? "✓" : "✗") + font.plainSubstrByWidth(
                        Component.translatable(flag).getString(), 24), ex, rowY, 50, 11, true,
                        "flag", group, fb);
                ex += 52;
                fb++;
            }
            rowY += 12;
        }
        // 底部两个批量操作。
        declButton(graphics, Component.translatable("gui.dimensional_mana.magiccircle.decl.recommend").getString(),
                x + 4, y + 168, 128, 13, true, "recommend", null, 0);
        declButton(graphics, Component.translatable("gui.dimensional_mana.magiccircle.decl.clearall").getString(),
                x + 136, y + 168, 128, 13, true, "clearall", null, 0);
    }

    /** − 值 ＋ 三连；返回下一个可用的 x。 */
    private int declStepper(GuiGraphics graphics, int px, int py, MagicCircleDecl.Entry entry,
                            MagicCircleDecl.Group group, int value, String minus, String plus, int arg) {
        declButton(graphics, "−", px, py, 11, 11, true, minus, group, arg);
        String text = String.valueOf(value);
        if (group.primaryUnit() != null && group.primaryUnit().length() <= 3 && value > 0) text += group.primaryUnit();
        int width = Math.max(20, font.width(text) + 6);
        graphics.drawString(font, text, px + 13, py, COLOR_TEXT, false);
        int next = px + 14 + font.width(text) + 2;
        declButton(graphics, "＋", next, py, 11, 11, true, plus, group, arg);
        return next + 13 + width;
    }

    /** 画一个小按钮并登记命中区。 */
    private void declButton(GuiGraphics graphics, String label, int x, int y, int w, int h,
                            boolean enabled, String action, MagicCircleDecl.Group group, int arg) {
        graphics.fill(x, y, x + w, y + h, enabled ? 0x66302A46 : 0x33120F20);
        graphics.renderOutline(x, y, w, h, enabled ? 0xFF5A4770 : 0xFF332A44);
        graphics.drawString(font, font.plainSubstrByWidth(label, w - 4), x + 2, y + (h - font.lineHeight) / 2 + 1,
                enabled ? COLOR_TEXT : 0xFF6A5F7E, false);
        if (enabled) declHits.add(new DeclHit(x, y, w, h, group, action, arg));
    }

    /** 点声明页按钮：改条目表 → 整串编码发回服务端（服务端负责夹取与过滤）。 */
    private void declAction(DeclHit hit) {
        // 翻页签不受只读限制：查看别人布的法阵也得能看声明、看粉末。
        switch (hit.action()) {
            case "tab_decl" -> {
                editorDeclPage = 1;
                return;
            }
            case "tab_powder" -> {
                editorDeclPage = 0;
                return;
            }
            case "gate_mode" -> {
                if (!state.editable()) {
                    hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                    return;
                }
                // ⇄ 切门控 / 声明模式：服务端翻 gate 串的形态位，界面下一帧自己重画。
                PacketDistributor.sendToServer(MagicCircleActionPayload.gate(editorNode, "mode"));
                return;
            }
            case "gate_op" -> {
                if (!state.editable()) {
                    hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                    return;
                }
                // ⇌ 翻转比较方向：底层只换枚举 LESS_THAN⇄GREATER_THAN，绝不存「反转标记」布尔。
                PacketDistributor.sendToServer(MagicCircleActionPayload.gate(editorNode, "op"));
                return;
            }
        }
        if (!state.editable()) {
            hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
            return;
        }
        MagicCircleSyncPayload.NodeView node = state.nodes().get(editorNode);
        List<MagicCircleDecl.Entry> entries = new ArrayList<>(MagicCircleDecl.parse(node.declare()));
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).group() == hit.group()) index = i;
        }
        switch (hit.action()) {
            case "recommend" -> {
                entries = new ArrayList<>();
                int tier = MagicCircleDecl.dyeTier(node.dye());
                MagicCircleDecl.Precision precision = MagicCircleDecl.precisionOf(node.power());
                int cap = Math.max(1, MagicCircleDecl.groupCapacity(node.power()));
                for (MagicCircleDecl.Group group : MagicCircleDecl.Group.familyOf(node.semantic())) {
                    if (entries.size() >= cap) break;
                    entries.add(MagicCircleDecl.recommended(group, tier, precision));
                }
                break;
            }
            case "clearall" -> {
                entries.clear();
                break;
            }
            case "add" -> {
                if (hit.group() != null && entries.size() < MagicCircleDecl.groupCapacity(node.power())) {
                    entries.add(MagicCircleDecl.recommended(hit.group(), MagicCircleDecl.dyeTier(node.dye()),
                            MagicCircleDecl.precisionOf(node.power())));
                }
                break;
            }
            case "remove" -> {
                if (index >= 0) entries.remove(index);
            }
            default -> {
                if (index < 0 || hit.group() == null) return;
                MagicCircleDecl.Entry old = entries.get(index);
                int step = Math.max(1, old.primaryStep());
                int secStep = Math.max(1, old.secondaryStep());
                MagicCircleDecl.Entry updated = switch (hit.action()) {
                    case "minus" -> new MagicCircleDecl.Entry(old.group(), Math.max(0, old.primary() - step),
                            old.secondary(), old.enums(), old.flags());
                    case "plus" -> new MagicCircleDecl.Entry(old.group(), old.primary() + step,
                            old.secondary(), old.enums(), old.flags());
                    case "secminus" -> new MagicCircleDecl.Entry(old.group(), old.primary(),
                            Math.max(0, old.secondary() - secStep), old.enums(), old.flags());
                    case "secplus" -> new MagicCircleDecl.Entry(old.group(), old.primary(),
                            old.secondary() + secStep, old.enums(), old.flags());
                    case "enum" -> {
                        int size = Math.max(1, old.group().enumSize(hit.arg()));
                        int[] enums = old.enums().clone();
                        if (hit.arg() < enums.length) enums[hit.arg()] = (enums[hit.arg()] + 1) % size;
                        yield new MagicCircleDecl.Entry(old.group(), old.primary(), old.secondary(),
                                enums, old.flags());
                    }
                    default -> new MagicCircleDecl.Entry(old.group(), old.primary(), old.secondary(),
                            old.enums(), old.flags() ^ (1 << hit.arg()));
                };
                entries.set(index, updated);
            }
        }
        PacketDistributor.sendToServer(MagicCircleActionPayload.declare(editorNode, MagicCircleDecl.encode(entries)));
        hint = Component.translatable("gui.dimensional_mana.magiccircle.status.editing");
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
        // r218：面板边线上的三颗收起 / 展开小钮（放在关闭钮之后、工具条之前认，压不到画线）。
        if (button == 0 && paletteToggleRect().contains(mouseX, mouseY)) {
            hidePalette = !hidePalette;
            init();
            hint = Component.translatable("gui.dimensional_mana.magiccircle.toggle.hint."
                    + (hidePalette ? "off" : "on"));
            return true;
        }
        if (button == 0 && sideToggleRect().contains(mouseX, mouseY)) {
            hideSide = !hideSide;
            init();
            hint = Component.translatable("gui.dimensional_mana.magiccircle.toggle.hint."
                    + (hideSide ? "off" : "on"));
            return true;
        }
        if (button == 0 && footerToggleRect().contains(mouseX, mouseY)) {
            hideFooter = !hideFooter;
            init();
            hint = Component.translatable("gui.dimensional_mana.magiccircle.toggle.hint."
                    + (hideFooter ? "off" : "on"));
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
            if (!hidePalette && handlePaletteClick(mouseX, mouseY)) return true;
            if (!hideSide && handleSideClick(mouseX, mouseY)) return true;
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
            editorDeclPage = 0;
            return true;
        }
        if (editorNode < 0 || editorNode >= state.nodes().size()) return true;
        if (button == 1) {
            // 右键槽位行 = 直接把那一格粉拿下来（不用先选中再点「拿下来」，少两步）。
            MagicCirclePowders.Slot[] rightSlots = MagicCirclePowders.Slot.values();
            MagicCircleSyncPayload.NodeView rightNode = state.nodes().get(editorNode);
            for (int i = 0; i < rightSlots.length; i++) {
                if (!editorSlotRect(i).contains(mouseX, mouseY)) continue;
                if (state.editable() && !nodeSlotValue(rightNode, rightSlots[i]).isEmpty()) {
                    PacketDistributor.sendToServer(
                            MagicCircleActionPayload.edit(editorNode, rightSlots[i].id(), ""));
                    hint = Component.translatable("gui.dimensional_mana.magiccircle.status.editing");
                }
                return true;
            }
            return true;
        }
        if (button != 0) return true;
        // v2.2 声明页：页签与每行控件都靠渲染时登记的命中区，优先于槽位判定。
        for (DeclHit hit : declHits) {
            if (hit.contains(mouseX, mouseY)) {
                declAction(hit);
                return true;
            }
        }
        MagicCirclePowders.Slot[] slots = MagicCirclePowders.Slot.values();
        for (int i = 0; i < slots.length; i++) {
            if (!editorSlotRect(i).contains(mouseX, mouseY)) continue;
            editorSlot = slots[i];
            // 停在声明页时点槽位 = 八成想换粉：直接翻到「选粉末」页，网格当场露出来。
            editorDeclPage = 0;
            return true;
        }
        // 声明页根本不画网格和「拿下来」，这两块判定也必须跳过——
        // r208 就是漏了这一步：点声明页的空档会打到隐形的粉末格，莫名其妙把粉换了。
        if (editorOnDeclPage()) return true;
        List<MagicCircleSyncPayload.PowderView> powders = editorPowders();
        for (int i = 0; i < powders.size(); i++) {
            Rect rect = editorCellRect(i);
            if (rect.y + editorCellH() > editorOrigin()[1] + editorClearTop()) break;
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
        // v2.2：声明模式的条件节点，打开就停在「边界声明」页（规范 14：先声明后调粉）。
        // r217：门控模式的条件节点停在「选粉末」页——它没有声明条目，页签行换成门控钮。
        MagicCircleSyncPayload.NodeView opened = state.nodes().get(index);
        boolean openCond = MagicCircleBlockType.byId(opened.type()) == MagicCircleBlockType.CONDITION;
        editorDeclPage = openCond && !MagicCircleGates.isGateMode(opened.semantic(), opened.gate())
                && !MagicCircleDecl.Group.familyOf(opened.semantic()).isEmpty() ? 1 : 0;
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
            Rect rect = candidateRect(i);
            if (!sideVisible(rect) || !rect.contains(mouseX, mouseY)) continue;
            if (!state.editable()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                return true;
            }
            PacketDistributor.sendToServer(MagicCircleActionPayload.confirm(state.candidates().get(i).glyph()));
            hint = Component.translatable("gui.dimensional_mana.magiccircle.status.placing");
            return true;
        }
        for (int index = 0; index < state.nodes().size(); index++) {
            Rect rect = nodeRowRect(index);
            if (!sideVisible(rect) || !rect.contains(mouseX, mouseY)) continue;
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
        // r218：槽位空着也常驻——点空槽位 = 从背包收一张空白蓝图载体放上台面。
        if (state.editable() && !blueprintActive() && bpSlotRect().contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(MagicCircleActionPayload.blueprintMount());
            hint = Component.translatable("gui.dimensional_mana.magiccircle.status.bp_mount");
            return true;
        }
        if (blueprintActive() && bpTakeRect().contains(mouseX, mouseY)) {
            PacketDistributor.sendToServer(MagicCircleActionPayload.blueprintTake());
            hint = Component.translatable("gui.dimensional_mana.magiccircle.status.bp_take");
            return true;
        }
        if (blueprintActive() && bpCopyRect().contains(mouseX, mouseY)) {
            if (!state.editable()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.readonly");
                return true;
            }
            // 复制留样只属于上稿形态；空白载体 / 成品蓝图按它没有意义（灰按钮也挡一层）。
            if (!"edit".equals(bpKind())) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.bp_not_edit");
                return true;
            }
            if (state.blueprintCopied()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.bp_done");
                return true;
            }
            if (state.nodes().isEmpty()) {
                hint = Component.translatable("gui.dimensional_mana.magiccircle.status.bp_empty");
                return true;
            }
            PacketDistributor.sendToServer(MagicCircleActionPayload.blueprintCopy());
            hint = Component.translatable("gui.dimensional_mana.magiccircle.status.bp_copy");
            return true;
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
        // 滚轮压在右侧一列上：滚动列表，别去动画笔粗细。
        if (mouseX >= sideX && mouseX < sideX + sideW
                && mouseY >= sideY && mouseY < sideY + sideH) {
            int viewportH = Math.max(24, sideViewportBottom() - sideViewportTop());
            int max = Math.max(0, sideContentHeight() - viewportH);
            sideScroll = Mth.clamp(sideScroll - (int) Math.round(verticalDelta * 14), 0, max);
            return true;
        }
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
        return new Rect(sideX + 3, sideViewportTop() + index * 26 - sideScroll, sideW - 6, 25);
    }

    private Rect nodeRowRect(int row) {
        int top = sideViewportTop() + sideCandidatesH() + 6 + NODE_ROW_HEIGHT - sideScroll;
        return new Rect(sideX + 3, top + row * NODE_ROW_HEIGHT, sideW - 6, NODE_ROW_HEIGHT - 1);
    }

    /**
     * 6 颗动作按钮的最上沿：从列底往上算，先给蓝图槽位卡（仅挂蓝图时）和它上面的缝留位置。
     */
    private int sideButtonsTop() {
        return sideY + sideH - SIDE_BUTTON_HEIGHT * 6 - 4 - bpAreaHeight();
    }

    private Rect sideButtonRect(int index) {
        return new Rect(sideX + 3, sideButtonsTop() + index * SIDE_BUTTON_HEIGHT,
                sideW - 6, SIDE_BUTTON_HEIGHT - 1);
    }

    // ---------------------------------------------------------------- r211 蓝图修改模式

    /** 台面右下角槽位里现在挂着一张蓝图（上稿修改模式开着）。 */
    private boolean blueprintActive() {
        return state != null && !state.blueprintItem().isEmpty();
    }

    /** 蓝图槽位卡的高度：一格物品槽 + 一行两个按钮（「复制留样」「打包带走」）。 */
    private static final int BP_AREA_HEIGHT = 36;

    /**
     * 蓝图区占用的高度：槽位卡 + 它上面的缝。r218 起<b>只要台子能编辑就常显</b>——
     * 槽位空着也是一张可用的卡片（点它装空白蓝图），不再"没蓝图就整块消失"。
     */
    private int bpAreaHeight() {
        return state != null && state.editable() ? BP_AREA_HEIGHT : 0;
    }

    /** 槽位卡的形态：空 / 上稿(edit) / 空白载体(blank) / 成品(done)。老服务端没发 kind 时按上稿推断。 */
    private String bpKind() {
        if (state == null || !blueprintActive()) return "";
        String kind = state.blueprintKind();
        if (kind == null || kind.isEmpty()) return "edit";
        return kind;
    }

    /**
     * 蓝图槽位卡的上沿：<b>贴在右列最底下</b>，卡片落在按钮区下面，不再和按钮抢同一块地方。
     *
     * <p>r215：以前这里是「按钮顶部 + 2」——也就是画在按钮身上，卡里的
     * 「已上样（粉已付）」「复制留样」「打包带走」跟按钮文字叠成一团（用户圈出来的就是这块）。
     * 现在卡片占据列底最后 36 像素，按钮整体上移让位，两段各画各的。</p>
     */
    private int bpTop() {
        // 卡片实际占据 [top-1, top+34]（含 1 像素外框），所以上沿再退 1 像素，正好贴住列底。
        return sideY + sideH - BP_AREA_HEIGHT + 1;
    }

    private Rect bpSlotRect() {
        return new Rect(sideX + 4, bpTop() + 1, 18, 18);
    }

    private Rect bpCopyRect() {
        return new Rect(sideX + 4, bpTop() + 21, (sideW - 11) / 2, 13);
    }

    private Rect bpTakeRect() {
        return new Rect(sideX + 8 + (sideW - 11) / 2, bpTop() + 21, (sideW - 11) / 2, 13);
    }

    /** 槽位里那张蓝图的图标（按同步来的物品 id 现取，取不到就画空位）。 */
    private ItemStack blueprintStack() {
        if (!blueprintActive()) return ItemStack.EMPTY;
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse(state.blueprintItem()));
        return item == null || item == net.minecraft.world.item.Items.AIR
                ? ItemStack.EMPTY : new ItemStack(item);
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

    /**
     * r211 右下角蓝图槽：挂着「上稿修改」的蓝图时画在侧栏按钮上方——
     * 左边槽位放物品图标，右边写名字 / 状态，下面一排「复制留样」「打包带走」。
     * 复制的粉末清单按当前画布上的块现算，悬停槽位就能看见。
     */
    private void renderBlueprintSlot(GuiGraphics graphics, int mouseX, int mouseY) {
        // r218：只有读态（别人围观）才整块不画；能编辑的台面这张卡**常驻**。
        if (state == null || !state.editable()) return;
        int top = bpTop();
        String kind = bpKind();
        boolean mounted = blueprintActive();
        int frameColor = mounted ? 0xFFD9A8FF : 0xFF5A4770;
        graphics.fill(sideX + 3, top - 1, sideX + sideW - 3, top + 34, 0xE6140F22);
        graphics.renderOutline(sideX + 3, top - 1, sideW - 6, 35, frameColor);
        Rect slot = bpSlotRect();
        graphics.fill(slot.x, slot.y, slot.x + slot.w, slot.y + slot.h, 0xFF241A33);
        if (mounted) {
            graphics.renderOutline(slot.x, slot.y, slot.w, slot.h, 0xFF8A7BA5);
            ItemStack icon = blueprintStack();
            if (!icon.isEmpty()) graphics.renderItem(icon, slot.x + 1, slot.y + 1);
        } else {
            // 空槽位：虚线框 + 一个大「＋」，摆明了"点我装一张空白蓝图"。
            boolean slotHover = slot.contains(mouseX, mouseY);
            for (int px = slot.x; px < slot.x + slot.w; px += 3) {
                graphics.fill(px, slot.y, Math.min(px + 2, slot.x + slot.w), slot.y + 1,
                        slotHover ? 0xFFFFD86B : 0xFF8A7BA5);
                graphics.fill(px, slot.y + slot.h - 1, Math.min(px + 2, slot.x + slot.w), slot.y + slot.h,
                        slotHover ? 0xFFFFD86B : 0xFF8A7BA5);
            }
            for (int py = slot.y; py < slot.y + slot.h; py += 3) {
                graphics.fill(slot.x, py, slot.x + 1, Math.min(py + 2, slot.y + slot.h),
                        slotHover ? 0xFFFFD86B : 0xFF8A7BA5);
                graphics.fill(slot.x + slot.w - 1, py, slot.x + slot.w, Math.min(py + 2, slot.y + slot.h),
                        slotHover ? 0xFFFFD86B : 0xFF8A7BA5);
            }
            graphics.drawString(font, "+", slot.x + (slot.w - font.width("+")) / 2,
                    slot.y + (slot.h - font.lineHeight) / 2, slotHover ? 0xFFFFD86B : 0xFF8A7BA5, false);
        }
        String name = switch (kind) {
            case "blank" -> font.plainSubstrByWidth(
                    blueprintStack().getHoverName().getString(), sideW - 40);
            case "done" -> font.plainSubstrByWidth(state.blueprintName(), sideW - 40);
            case "edit" -> font.plainSubstrByWidth(state.blueprintName(), sideW - 40);
            default -> Component.translatable("gui.dimensional_mana.magiccircle.bp.empty_name").getString();
        };
        graphics.drawString(font, Component.literal(name), slot.x + 22, top + 3,
                mounted ? COLOR_GOLD : 0xFF6A5F7E, false);
        String status = switch (kind) {
            case "blank" -> "gui.dimensional_mana.magiccircle.bp.status_blank";
            case "done" -> "gui.dimensional_mana.magiccircle.bp.status_done";
            case "edit" -> state.blueprintCopied()
                    ? "gui.dimensional_mana.magiccircle.bp.status_copied"
                    : "gui.dimensional_mana.magiccircle.bp.status_draft";
            default -> "gui.dimensional_mana.magiccircle.bp.status_empty";
        };
        int statusColor = switch (kind) {
            case "done" -> COLOR_OK;
            case "blank" -> COLOR_AQUA;
            case "" -> 0xFF6A5F7E;
            default -> state.blueprintCopied() ? COLOR_OK : COLOR_AQUA;
        };
        graphics.drawString(font, Component.translatable(status), slot.x + 22, top + 12, statusColor, false);
        // 「复制留样」只有上稿形态、还没付粉时可用；空白 / 成品 / 空槽位都灰着。
        boolean copyEnabled = state.editable() && "edit".equals(kind)
                && !state.blueprintCopied() && !state.nodes().isEmpty();
        drawSideButton(graphics, bpCopyRect(),
                Component.translatable("edit".equals(kind) && state.blueprintCopied()
                        ? "gui.dimensional_mana.magiccircle.bp.copied"
                        : "gui.dimensional_mana.magiccircle.bp.copy"),
                mouseX, mouseY, copyEnabled);
        // 「打包带走」：槽位上有东西就能按（空白 / 成品 / 上稿都原样回背包）。
        drawSideButton(graphics, bpTakeRect(),
                Component.translatable("gui.dimensional_mana.magiccircle.bp.take"),
                mouseX, mouseY, mounted);
        if (slot.contains(mouseX, mouseY) || bpCopyRect().contains(mouseX, mouseY)) {
            hoverPowder.clear();
            if (!mounted) {
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.bp.empty_title")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.bp.empty_line1")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.bp.empty_line2")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                return;
            }
            hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.bp.tip.title")
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            java.util.Map<String, Integer> need = new java.util.LinkedHashMap<>();
            for (MagicCircleSyncPayload.NodeView node : state.nodes()) {
                for (String powder : new String[]{node.element(), node.semantic(), node.power(), node.dye()}) {
                    if (powder == null || powder.isEmpty()) continue;
                    need.merge(powder, 1, Integer::sum);
                }
            }
            if (need.isEmpty()) {
                hoverPowder.add(Component.translatable("gui.dimensional_mana.magiccircle.bp.tip.empty")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            for (java.util.Map.Entry<String, Integer> entry : need.entrySet()) {
                hoverPowder.add(Component.literal("· " + MagicCirclePowders.shortName(entry.getKey())
                        + " ×" + entry.getValue()).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }
}
