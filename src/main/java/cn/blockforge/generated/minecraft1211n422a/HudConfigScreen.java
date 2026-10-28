package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * HUD 可视化编辑界面：从模组列表的「配置」按钮进入。
 *
 * <p>左列是一排开关：哪类文字提示不想看就关掉哪类（含鉴定面板的五类显示项）；
 * 屏幕本体就是预览画布——魔素球、共鸣核心、鉴定面板三个可拖拽的虚线框按当前配置
 * 画在真实位置上，按住拖动即时写回配置并保存文件。单人游戏里从暂停菜单打开时，
 * 背后的真实 HUD 会跟着动，所见即所得。</p>
 */
public final class HudConfigScreen extends Screen {
    /** 画布上可拖拽的三块 HUD。 */
    private enum Element {
        MANA_ORB("hud.edit.dimensional_mana.mana_orb"),
        RES_CORE("hud.edit.dimensional_mana.res_core"),
        APPRAISAL("hud.edit.dimensional_mana.appraisal_panel");

        private final String nameKey;
        Element(String nameKey) { this.nameKey = nameKey; }
    }

    /** 每行开关：翻译键 + 配置项，绑定同一个 CycleButton。 */
    private record ToggleEntry(String nameKey, ModConfigSpec.BooleanValue value) { }

    private static final int ROW_HEIGHT = 15;
    private static final int PANEL_WIDTH = 168;

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

    private Element dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private Button manaScaleValue;

    public HudConfigScreen(Screen parent) {
        super(Component.translatable("hud.edit.dimensional_mana.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = 16;
        for (ToggleEntry entry : toggles) {
            // r218 修复崩溃：原来用 CycleButton.builder(显示函数) 建按钮却<b>没登记候选值</b>，
            // 原版构造器校验值列表非空，一进界面就抛 IllegalStateException: No values for cycle button
            // （用户在模组列表点「配置」看到的那一屏报错）。改成 booleanBuilder：开/关两个值齐全。
            addRenderableWidget(CycleButton.booleanBuilder(
                            Component.translatable("hud.edit.dimensional_mana.off"),
                            Component.translatable("hud.edit.dimensional_mana.on"))
                    .withInitialValue(entry.value().get())
                    .create(8, y, PANEL_WIDTH, 14, Component.translatable(entry.nameKey()),
                            (button, value) -> {
                                entry.value().set(value);
                                saveAll();
                            }));
            y += ROW_HEIGHT;
        }
        y += 4;
        // 魔素数字大小：-/+ 两个步进按钮夹一个只读的当前值。
        addRenderableWidget(Button.builder(Component.literal("−"), button -> adjustManaScale(-0.05))
                .bounds(8, y, 20, 16).build());
        manaScaleValue = Button.builder(Component.empty(), button -> { }).bounds(32, y, 68, 16).build();
        manaScaleValue.active = false;
        addRenderableWidget(manaScaleValue);
        addRenderableWidget(Button.builder(Component.literal("＋"), button -> adjustManaScale(0.05))
                .bounds(104, y, 20, 16).build());
        y += 20;
        // 恢复默认与完成并排一行，小屏（高度 216）也不会溢出。
        addRenderableWidget(Button.builder(Component.translatable("hud.edit.dimensional_mana.reset"),
                button -> resetPositions()).bounds(8, y, 82, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                button -> onClose()).bounds(94, y, 82, 16).build());
        refreshManaScaleLabel();
    }

    private void adjustManaScale(double delta) {
        double next = Mth.clamp(ClientManaConfig.MANA_TEXT_SCALE.get() + delta, 0.25, 2.0);
        ClientManaConfig.MANA_TEXT_SCALE.set(Math.round(next * 100.0) / 100.0);
        ClientManaConfig.SPEC.save();
        refreshManaScaleLabel();
    }

    private void refreshManaScaleLabel() {
        if (manaScaleValue != null) {
            manaScaleValue.setMessage(Component.translatable("hud.edit.dimensional_mana.mana_scale_value",
                    String.format(java.util.Locale.ROOT, "%.2f", ClientManaConfig.MANA_TEXT_SCALE.get())));
        }
    }

    private static void saveAll() {
        HudConfig.SPEC.save();
        ClientManaConfig.SPEC.save();
        AppraisalConfig.SPEC.save();
    }

    // ---------------------------------------------------------------- 位置换算

    /** 元素中心点（GUI 像素坐标）；与各处 HUD 渲染的取位置方式一一对应。 */
    private int[] centerOf(Element element) {
        return switch (element) {
            case MANA_ORB -> new int[]{
                    Mth.clamp(ClientManaConfig.HUD_X_FROM_LEFT.get(), 28, Math.max(28, width - 28)),
                    height - Mth.clamp(ClientManaConfig.HUD_Y_FROM_BOTTOM.get(), 30, Math.max(30, height - 30))};
            case RES_CORE -> new int[]{
                    width - Mth.clamp(HudConfig.RES_X_FROM_RIGHT.get(), 20, Math.max(20, width - 20)),
                    height - Mth.clamp(HudConfig.RES_Y_FROM_BOTTOM.get(), 20, Math.max(20, height - 20))};
            case APPRAISAL -> new int[]{
                    width / 2 + Mth.clamp(AppraisalConfig.HUD_OFFSET_X.get(),
                            -width / 2 + 20, Math.max(-width / 2 + 20, width / 2 - 20)),
                    height / 2 + Mth.clamp(AppraisalConfig.HUD_OFFSET_Y.get(),
                            -height / 2 + 12, Math.max(-height / 2 + 12, height / 2 - 12))};
        };
    }

    private int halfWidthOf(Element element) {
        return switch (element) {
            case MANA_ORB -> 26;
            case RES_CORE -> 24;
            case APPRAISAL -> 68;
        };
    }

    private int halfHeightOf(Element element) {
        return switch (element) {
            case MANA_ORB -> 26;
            case RES_CORE -> 24;
            case APPRAISAL -> 24;
        };
    }

    private void storeCenter(Element element, int cx, int cy) {
        switch (element) {
            case MANA_ORB -> {
                ClientManaConfig.HUD_X_FROM_LEFT.set(Mth.clamp(cx, 28, Math.max(28, width - 28)));
                ClientManaConfig.HUD_Y_FROM_BOTTOM.set(
                        Mth.clamp(height - cy, 30, Math.max(30, height - 30)));
            }
            case RES_CORE -> {
                HudConfig.RES_X_FROM_RIGHT.set(Mth.clamp(width - cx, 20, Math.max(20, width - 20)));
                HudConfig.RES_Y_FROM_BOTTOM.set(Mth.clamp(height - cy, 20, Math.max(20, height - 20)));
            }
            case APPRAISAL -> {
                AppraisalConfig.HUD_OFFSET_X.set(
                        Mth.clamp(cx - width / 2, -width / 2 + 20, Math.max(-width / 2 + 20, width / 2 - 20)));
                AppraisalConfig.HUD_OFFSET_Y.set(
                        Mth.clamp(cy - height / 2, -height / 2 + 12, Math.max(-height / 2 + 12, height / 2 - 12)));
            }
        }
    }

    /** 命中测试：重叠时优先小块（鉴定面板 > 共鸣核心 > 魔素球）。 */
    private Element elementAt(double mouseX, double mouseY) {
        for (Element element : new Element[]{Element.APPRAISAL, Element.RES_CORE, Element.MANA_ORB}) {
            int[] c = centerOf(element);
            if (Math.abs(mouseX - c[0]) <= halfWidthOf(element)
                    && Math.abs(mouseY - c[1]) <= halfHeightOf(element)) {
                return element;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让按钮吃掉点击；落在空白处又压住虚线框时开始拖拽。
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            Element hit = elementAt(mouseX, mouseY);
            if (hit != null) {
                dragging = hit;
                int[] c = centerOf(hit);
                dragOffsetX = (int)mouseX - c[0];
                dragOffsetY = (int)mouseY - c[1];
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            storeCenter(dragging, (int)mouseX - dragOffsetX, (int)mouseY - dragOffsetY);
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

    private void resetPositions() {
        ClientManaConfig.HUD_X_FROM_LEFT.set(35);
        ClientManaConfig.HUD_Y_FROM_BOTTOM.set(62);
        HudConfig.RES_X_FROM_RIGHT.set(30);
        HudConfig.RES_Y_FROM_BOTTOM.set(30);
        AppraisalConfig.HUD_OFFSET_X.set(12);
        AppraisalConfig.HUD_OFFSET_Y.set(0);
        ClientManaConfig.MANA_TEXT_SCALE.set(0.65);
        saveAll();
        refreshManaScaleLabel();
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 只压暗一层半透明、不画模糊背景：背后的真实 HUD 要能看见。
        graphics.fill(0, 0, width, height, 0x99101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        // 准心参考点。
        graphics.fill(width / 2 - 5, height / 2, width / 2 + 6, height / 2 + 1, 0x55FFFFFF);
        graphics.fill(width / 2, height / 2 - 5, width / 2 + 1, height / 2 + 6, 0x55FFFFFF);
        Element hover = elementAt(mouseX, mouseY);
        for (Element element : Element.values()) {
            renderElementFrame(graphics, element, element == dragging || element == hover);
        }
        graphics.drawCenteredString(font, Component.translatable("hud.edit.dimensional_mana.hint"),
                width / 2, 6, 0xFFE8D8A0);
    }

    private void renderElementFrame(GuiGraphics graphics, Element element, boolean highlight) {
        int[] c = centerOf(element);
        int hw = halfWidthOf(element);
        int hh = halfHeightOf(element);
        int left = c[0] - hw, top = c[1] - hh, right = c[0] + hw, bottom = c[1] + hh;
        int color = highlight ? 0xE0FFD060 : 0x90B8C8E8;
        dashedRect(graphics, left, top, right, bottom, color);
        if (element == Element.MANA_ORB) {
            // 魔素球示意：蓝底圆盘 + 金色斗气圈；数字随开关走。
            disc(graphics, c[0], c[1], 17, 0x55203A6E);
            ring(graphics, c[0], c[1], 21, 0x66FFC84A);
            if (HudConfig.SHOW_MANA_NUMBER.get()) {
                graphics.drawCenteredString(font, "128", c[0], c[1] - 6, 0xA0FFFFFF);
                graphics.drawCenteredString(font, "/ 300", c[0], c[1] + 3, 0x80BED5FF);
            }
        } else if (element == Element.RES_CORE) {
            disc(graphics, c[0], c[1], 15, 0x55121A33);
            ring(graphics, c[0], c[1], 15, 0x666FD3FF);
            if (HudConfig.SHOW_RES_LABEL.get()) {
                graphics.drawString(font, "75%", c[0] - 24, c[1] - 26, 0x909FC7FF, false);
            }
        } else {
            // 鉴定面板示意：名称行 + 三条进度条。
            graphics.fill(left + 4, top + 4, right - 4, top + 9, 0x40202A44);
            graphics.fill(left + 4, top + 12, left + 60, top + 16, 0x40202A44);
            graphics.fill(left + 4, top + 19, left + 48, top + 23, 0x40202A44);
            graphics.fill(left + 4, top + 26, left + 64, top + 30, 0x40202A44);
            graphics.drawString(font, "88 / 100", left + 8, top + 12, 0x60FFFFFF, false);
        }
        graphics.drawCenteredString(font, Component.translatable(element.nameKey), c[0],
                Math.max(2, top - 11), highlight ? 0xFFFFE08A : 0xB0C8D8E8);
    }

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
        for (int y = -radius; y <= radius; y++) {
            int half = (int)Math.floor(Math.sqrt((double)radius * radius - y * y));
            graphics.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
    }

    private static void ring(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        int steps = Math.max(36, radius * 6);
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2.0 * i / steps;
            int x = (int)Math.round(cx + Math.cos(angle) * radius);
            int y = (int)Math.round(cy + Math.sin(angle) * radius);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    @Override
    public void onClose() {
        saveAll();
        minecraft.setScreen(parent);
    }

    /** 从暂停菜单打开时不额外接管暂停状态。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
