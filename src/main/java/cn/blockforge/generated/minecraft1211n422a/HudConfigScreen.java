package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * HUD 可视化编辑器：从模组列表的「配置」→「HUD 位置与大小」进入。
 *
 * <p>r295 重做：HUD 被拆成七个可以<b>单独拖动、单独缩放</b>的部件（魔素球图标、魔素数字、
 * 法术信息块、共鸣核心图标、共鸣核心文字、鉴定面板、鉴定扫描小窗）。左边先点一个部件，
 * 再在画布上拖虚线框改位置、用「−/＋」（或滚轮）改大小；另一页是各类文字提示的显示开关。
 * 所有改动即时写回 {@link HudConfig} 并保存到 {@code config/dimensional_mana-hud.toml}。</p>
 *
 * <p>预览和真实 HUD 用的是同一套锚点（{@link HudConfig#anchorX}/{@link HudConfig#anchorY}），
 * 所以画布上拖到哪，游戏里就在哪——鉴定面板以前拖了没反应，就是因为两边各算各的。</p>
 */
public final class HudConfigScreen extends Screen {
    /** 两个页面：拖位置/调大小、开关文字提示。 */
    private enum Page { LAYOUT, TOGGLES }

    /** 每行开关：翻译键 + 配置项，绑定同一个 CycleButton。 */
    private record ToggleEntry(String nameKey, ModConfigSpec.BooleanValue value) { }

    private static final int PANEL_X = 8;
    private static final int PANEL_WIDTH = 168;
    private static final int ROW_HEIGHT = 15;

    private final Screen parent;
    private final List<ToggleEntry> toggles = List.of(
            new ToggleEntry("hud.edit.dimensional_mana.show_mana_number", HudConfig.SHOW_MANA_NUMBER),
            new ToggleEntry("hud.edit.dimensional_mana.show_spell_hud", HudConfig.SHOW_SPELL_HUD),
            new ToggleEntry("hud.edit.dimensional_mana.show_reaction_text", HudConfig.SHOW_REACTION_TEXT),
            new ToggleEntry("hud.edit.dimensional_mana.show_res_label", HudConfig.SHOW_RES_LABEL),
            new ToggleEntry("hud.edit.dimensional_mana.show_parry_label", HudConfig.SHOW_PARRY_STANCE_LABEL),
            new ToggleEntry("hud.edit.dimensional_mana.show_target_bars", AppraisalConfig.SHOW_TARGET_BARS),
            new ToggleEntry("hud.edit.dimensional_mana.show_regen_line", AppraisalConfig.SHOW_REGEN_LINE),
            new ToggleEntry("hud.edit.dimensional_mana.show_resist_line", AppraisalConfig.SHOW_RESISTANCE_LINE),
            new ToggleEntry("hud.edit.dimensional_mana.show_skill_line", AppraisalConfig.SHOW_SKILL_LINE),
            new ToggleEntry("hud.edit.dimensional_mana.show_scanline", AppraisalConfig.SHOW_SCANLINE));

    private final Page page;
    private HudConfig.Part selected = HudConfig.Part.MANA_ICON;
    private HudConfig.Part dragging;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private int dragStartDx;
    private int dragStartDy;
    private Button scaleButton;

    public HudConfigScreen(Screen parent) {
        this(parent, Page.LAYOUT);
    }

    private HudConfigScreen(Screen parent, Page page) {
        super(Component.translatable("hud.edit.dimensional_mana.title"));
        this.parent = parent;
        this.page = page;
    }

    /** 直接打开「显示开关」那一页。 */
    public static HudConfigScreen toggles(Screen parent) {
        return new HudConfigScreen(parent, Page.TOGGLES);
    }

    @Override
    protected void init() {
        if (page == Page.LAYOUT) initLayoutPage();
        else initTogglePage();
    }

    // ---------------------------------------------------------------- 页面搭建

    private void initLayoutPage() {
        // 部件按钮排成两列，尽量只占左上角：默认排版里魔素球在左下角，
        // 面板要是铺满整条左边，就会把要拖的东西压在按钮下面点不到。
        HudConfig.Part[] parts = HudConfig.Part.values();
        for (int i = 0; i < parts.length; i++) {
            HudConfig.Part target = parts[i];
            int x = PANEL_X + (i % 2) * 86;
            int y = 14 + (i / 2) * 16;
            addRenderableWidget(Button.builder(Component.translatable(target.nameKey), button -> {
                selected = target;
                refreshScaleLabel();
            }).bounds(x, y, 82, 14).build());
        }
        int y = 14 + 4 * 16 + 2;
        addRenderableWidget(Button.builder(Component.literal("−"), button -> adjustScale(-0.05D))
                .bounds(PANEL_X, y, 20, 16).build());
        scaleButton = Button.builder(Component.empty(), button -> { }).bounds(PANEL_X + 24, y, 100, 16).build();
        scaleButton.active = false;
        addRenderableWidget(scaleButton);
        addRenderableWidget(Button.builder(Component.literal("＋"), button -> adjustScale(0.05D))
                .bounds(PANEL_X + 128, y, 20, 16).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("hud.edit.dimensional_mana.reset"),
                button -> {
                    HudConfig.resetLayout();
                    refreshScaleLabel();
                    saveAll();
                }).bounds(PANEL_X, y, PANEL_WIDTH, 16).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("hud.edit.dimensional_mana.page_toggles"),
                button -> switchPage(Page.TOGGLES)).bounds(PANEL_X, y, 82, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                button -> onClose()).bounds(PANEL_X + 86, y, 82, 16).build());
        refreshScaleLabel();
    }

    private void initTogglePage() {
        int y = 14;
        for (ToggleEntry entry : toggles) {
            addRenderableWidget(CycleButton.booleanBuilder(
                            Component.translatable("hud.edit.dimensional_mana.off"),
                            Component.translatable("hud.edit.dimensional_mana.on"))
                    .withInitialValue(entry.value().get())
                    .create(PANEL_X, y, PANEL_WIDTH, 14, Component.translatable(entry.nameKey()),
                            (button, value) -> {
                                entry.value().set(value);
                                saveAll();
                            }));
            y += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(Component.translatable("hud.edit.dimensional_mana.page_layout"),
                button -> switchPage(Page.LAYOUT)).bounds(PANEL_X, 176, 82, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                button -> onClose()).bounds(PANEL_X + 86, 176, 82, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.edit.dimensional_mana.reset_toggles"),
                button -> {
                    for (ToggleEntry entry : toggles) entry.value().set(true);
                    saveAll();
                    rebuildWidgets();
                }).bounds(PANEL_X, 196, PANEL_WIDTH, 16).build());
    }

    private void switchPage(Page next) {
        saveAll();
        if (next == page) return;
        minecraft.setScreen(next == Page.LAYOUT ? new HudConfigScreen(parent) : HudConfigScreen.toggles(parent));
    }

    private void adjustScale(double delta) {
        HudConfig.setScale(selected, HudConfig.scale(selected) + delta);
        saveAll();
        refreshScaleLabel();
    }

    private void refreshScaleLabel() {
        if (scaleButton == null) return;
        scaleButton.setMessage(Component.translatable("hud.edit.dimensional_mana.scale_value",
                Component.translatable(selected.nameKey), HudConfig.formatScale(selected)));
    }

    private static void saveAll() {
        HudConfig.SPEC.save();
        AppraisalConfig.SPEC.save();
    }

    // ---------------------------------------------------------------- 部件几何

    /** 部件预览框的半宽（未缩放）。 */
    private static int halfWidth(HudConfig.Part part) {
        return switch (part) {
            case MANA_ICON -> 22;
            case MANA_TEXT -> 27;
            case SPELL_HUD -> 34;
            case RES_ICON -> 21;
            case RES_LABEL -> 34;
            case APPRAISAL -> 63;
            case SCANLINE -> 42;
        };
    }

    private static int halfHeight(HudConfig.Part part) {
        return switch (part) {
            case MANA_ICON -> 22;
            case MANA_TEXT -> 11;
            case SPELL_HUD -> 16;
            case RES_ICON -> 21;
            case RES_LABEL -> 8;
            case APPRAISAL -> 26;
            case SCANLINE -> 23;
        };
    }

    /** 部件当前占据的矩形：{left, top, right, bottom}。 */
    private int[] box(HudConfig.Part part) {
        int cx = HudConfig.anchorX(part, width, height);
        int cy = HudConfig.anchorY(part, width, height);
        double scale = HudConfig.scale(part);
        int hw = (int)Math.round(halfWidth(part) * scale);
        int hh = (int)Math.round(halfHeight(part) * scale);
        return new int[]{cx - hw, cy - hh, cx + hw, cy + hh};
    }

    private HudConfig.Part partAt(double mouseX, double mouseY) {
        HudConfig.Part[] values = HudConfig.Part.values();
        for (int i = values.length - 1; i >= 0; i--) {
            int[] b = box(values[i]);
            if (mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3]) return values[i];
        }
        return null;
    }

    /** 这个部件当前是不是被显示开关关掉了（关掉的部件在画布上画淡一点）。 */
    private static boolean enabled(HudConfig.Part part) {
        return switch (part) {
            case MANA_TEXT -> HudConfig.SHOW_MANA_NUMBER.get();
            case SPELL_HUD -> HudConfig.SHOW_SPELL_HUD.get();
            case RES_LABEL -> HudConfig.SHOW_RES_LABEL.get();
            case APPRAISAL -> AppraisalConfig.SHOW_TARGET_BARS.get();
            case SCANLINE -> AppraisalConfig.SHOW_SCANLINE.get();
            case MANA_ICON, RES_ICON -> true;
        };
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (page != Page.LAYOUT || button != 0) return false;
        HudConfig.Part hit = partAt(mouseX, mouseY);
        if (hit == null) return false;
        selected = hit;
        refreshScaleLabel();
        dragging = hit;
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;
        dragStartDx = HudConfig.dx(hit);
        dragStartDy = HudConfig.dy(hit);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            // 偏移直接按「鼠标相对按下时的位移」累加，屏幕边缘夹紧也不会粘住。
            HudConfig.setDx(dragging, dragStartDx + (int)Math.round(mouseX - dragStartMouseX));
            HudConfig.setDy(dragging, dragStartDy + (int)Math.round(mouseY - dragStartMouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            saveAll();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == Page.LAYOUT && scrollY != 0.0D) {
            HudConfig.Part hovered = partAt(mouseX, mouseY);
            if (hovered != null) {
                selected = hovered;
                adjustScale(scrollY > 0 ? 0.05D : -0.05D);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** 从暂停菜单打开时不额外接管暂停状态。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 只压暗一层半透明、不画模糊背景：背后的真实 HUD 要能看见。
        graphics.fill(0, 0, width, height, 0x99101010);
        if (page != Page.LAYOUT) return;
        // 画布画在按钮下面：按钮永远盖住虚线框，不会点不到。
        graphics.fill(width / 2 - 5, height / 2, width / 2 + 6, height / 2 + 1, 0x55FFFFFF);
        graphics.fill(width / 2, height / 2 - 5, width / 2 + 1, height / 2 + 6, 0x55FFFFFF);
        HudConfig.Part hover = partAt(mouseX, mouseY);
        for (HudConfig.Part part : HudConfig.Part.values()) {
            renderPart(graphics, part, part == dragging || part == hover, part == selected);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        Component hint = Component.translatable(page == Page.LAYOUT
                ? "hud.edit.dimensional_mana.hint_layout"
                : "hud.edit.dimensional_mana.hint_toggles");
        graphics.drawCenteredString(font, hint, width / 2, 5, 0xFFE8D8A0);
    }

    private void renderPart(GuiGraphics graphics, HudConfig.Part part, boolean highlight, boolean selected) {
        int[] b = box(part);
        int cx = (b[0] + b[2]) / 2;
        int cy = (b[1] + b[3]) / 2;
        double scale = HudConfig.scale(part);
        boolean on = enabled(part);
        int alpha = on ? 0xFF : 0x60;
        int boxColor = selected ? 0xF0FFD060 : highlight ? 0xD0FFE9A0 : 0x80B8C8E8;
        dashedRect(graphics, b[0], b[1], b[2], b[3], boxColor);
        preview(graphics, part, cx, cy, scale, alpha);
        Component name = Component.translatable(part.nameKey);
        int nameY = Math.max(2, b[1] - 10);
        graphics.drawCenteredString(font, name, cx, nameY, (selected ? 0xFFFFE08A : 0xB0C8D8E8) & (on ? 0xFFFFFFFF : 0x80FFFFFF));
    }

    /** 每个部件的示意图形：只求一眼能认出来，不追求和真实 HUD 完全一致。 */
    private void preview(GuiGraphics graphics, HudConfig.Part part, int cx, int cy, double scale, int alpha) {
        int body = (alpha << 24) | 0x00BED5FF;
        int fill = (0x40 * alpha / 0xFF) << 24 | 0x00203250;
        int accent = (alpha << 24) | 0x00FFC84A;
        switch (part) {
            case MANA_ICON -> {
                disc(graphics, cx, cy, (int)Math.round(17 * scale), (0x90 * alpha / 0xFF) << 24 | 0x00101629);
                ring(graphics, cx, cy, (int)Math.round(21 * scale), accent);
            }
            case MANA_TEXT -> {
                graphics.drawCenteredString(font, "128", cx, cy - 8, body);
                graphics.drawCenteredString(font, "/ 300", cx, cy + 1, body);
            }
            case SPELL_HUD -> {
                int side = (int)Math.round(20 * scale);
                graphics.fill(cx - side, cy - side / 2 - 2, cx - side + 2 * side, cy + side / 2 + 2, fill);
                graphics.renderOutline(cx - side, cy - side / 2 - 2, 2 * side, side + 4, body);
                graphics.drawString(font, "火球术", cx + side - (int)Math.round(6 * scale), cy - 4, body, false);
            }
            case RES_ICON -> {
                disc(graphics, cx, cy, (int)Math.round(13 * scale), (0x90 * alpha / 0xFF) << 24 | 0x00121A33);
                ring(graphics, cx, cy, (int)Math.round(15 * scale), body);
            }
            case RES_LABEL -> graphics.drawCenteredString(font, "75%", cx, cy - 4, body);
            case APPRAISAL -> {
                int hw = (int)Math.round(60 * scale);
                int hh = (int)Math.round(23 * scale);
                graphics.fill(cx - hw, cy - hh, cx + hw, cy + hh, (0x66 * alpha / 0xFF) << 24 | 0x0010141C);
                graphics.renderOutline(cx - hw, cy - hh, 2 * hw, 2 * hh, body);
                graphics.fill(cx - hw + 5, cy - hh + 6, cx - hw + 5 + (int)Math.round(46 * scale), cy - hh + 9, body);
                graphics.fill(cx - hw + 5, cy - hh + 16, cx - hw + 5 + (int)Math.round(38 * scale), cy - hh + 19, body);
                graphics.fill(cx - hw + 5, cy - hh + 26, cx - hw + 5 + (int)Math.round(54 * scale), cy - hh + 29, body);
            }
            case SCANLINE -> {
                int hw = (int)Math.round(42 * scale);
                int hh = (int)Math.round(23 * scale);
                graphics.fill(cx - hw, cy - hh, cx + hw, cy + hh, (0x88 * alpha / 0xFF) << 24);
                graphics.renderOutline(cx - hw, cy - hh, 2 * hw, 2 * hh, body);
                for (int i = 0; i < 3; i++) {
                    int y = cy - hh + 7 + i * 7;
                    graphics.fill(cx - hw + 4, y, cx + hw - 4, y + 1, body);
                }
            }
        }
    }

    @Override
    public void onClose() {
        saveAll();
        minecraft.setScreen(parent);
    }

    // ---------------------------------------------------------------- 小工具

    private static void dashedRect(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        for (int x = left; x < right; x += 4) {
            graphics.fill(x, top, Math.min(x + 2, right), top + 1, color);
            graphics.fill(x, bottom, Math.min(x + 2, right), bottom + 1, color);
        }
        for (int y = top; y < bottom; y += 4) {
            graphics.fill(left, y, left + 1, Math.min(y + 2, bottom), color);
            graphics.fill(right, y, right + 1, Math.min(y + 2, bottom), color);
        }
    }

    private static void disc(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        if (radius <= 0) return;
        for (int y = -radius; y <= radius; y++) {
            int half = (int)Math.floor(Math.sqrt((double)radius * radius - y * y));
            graphics.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
    }

    private static void ring(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        if (radius <= 0) return;
        int steps = Math.max(36, radius * 6);
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2.0 * i / steps;
            int x = (int)Math.round(cx + Math.cos(angle) * radius);
            int y = (int)Math.round(cy + Math.sin(angle) * radius);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }
}
