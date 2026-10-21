package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** 按住玩家所绑定按键打开的十二槽法术快捷盘。 */
public final class SpellWheelScreen extends Screen {
    private static final int SLOT_COUNT = SpellData.SLOT_COUNT;
    private static final long DOUBLE_CLICK_MILLIS = 350L;
    private static final int CARD_STEP_X = 72;
    private static final int CARD_STEP_Y = 60;
    private static final int CARD_WIDTH = 60;
    private static final int CARD_HEIGHT = 51;
    private int hoveredSlot = -1;
    private int assigningSlot = Integer.MIN_VALUE;
    private int lastBlankTarget = Integer.MIN_VALUE;
    private long lastBlankClickTime;
    private int pickerPage;
    private boolean wheelBindingReleased;

    public SpellWheelScreen() {
        super(Component.translatable("gui.dimensional_mana.spell_wheel.title"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用原版模糊背景：保留战斗视野，仅压暗世界。
        graphics.fill(0, 0, width, height, 0x780B0714);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        hoveredSlot = slotAt(mouseX, mouseY, cx, cy);
        graphics.drawCenteredString(font, title, cx, cy - 8, 0xFFEBD9FF);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.spell_wheel.hint"),
                cx, cy + 7, 0xFFBDA7D1);
        for (int i = 0; i < SLOT_COUNT; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / SLOT_COUNT;
            int x = cx + (int)Math.round(Math.cos(angle) * 72.0);
            int y = cy + (int)Math.round(Math.sin(angle) * 72.0);
            boolean selected = i == ClientSpellState.selectedSlot();
            boolean hovered = i == hoveredSlot;
            int color = selected ? 0xFFBE77FF : hovered ? 0xFFE8C6FF : 0xFF62506F;
            graphics.fill(x - 12, y - 12, x + 12, y + 12, hovered ? 0xE0382450 : 0xC0181225);
            graphics.renderOutline(x - 12, y - 12, 24, 24, color);
            SpellDefinition spell = ClientSpellState.slot(i);
            if (spell != null) {
                graphics.blit(spell.icon(), x - 8, y - 8, 0, 0, 16, 16, 16, 16);
                if (selected) graphics.drawCenteredString(font, Component.literal("◆"), x, y + 14, 0xFFFFD66B);
            } else {
                graphics.drawCenteredString(font, Component.literal(Integer.toString(i + 1)), x, y - 4, 0xFF7D708A);
            }
        }
        if (hoveredSlot >= 0) {
            SpellDefinition hovered = ClientSpellState.slot(hoveredSlot);
            Component line = hovered == null
                    ? Component.translatable("gui.dimensional_mana.spell_wheel.empty", hoveredSlot + 1)
                    : Component.translatable("gui.dimensional_mana.spell_wheel.slot", hoveredSlot + 1,
                            Component.translatable(hovered.translationKey()));
            graphics.drawCenteredString(font, line, cx, cy + 28, 0xFFFFFFFF);
        } else {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.spell_wheel.double_click_hint"),
                    cx, cy + 28, 0xFFD2B9DF);
        }
        if (assigningSlot != Integer.MIN_VALUE) {
            // 子界面使用不透明底板，并提高绘制层级，彻底盖住上一层轮盘文字。
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            renderSpellPicker(graphics, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    private void renderSpellPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        List<SpellDefinition> spells = availableSpells();
        PickerLayout layout = pickerLayout(spells.size());
        pickerPage = Math.min(pickerPage, layout.pageCount() - 1);
        graphics.fill(0, 0, width, height, 0xE6000000);
        graphics.fillGradient(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(),
                0xFF2B1840, 0xFF101B31);
        graphics.renderOutline(layout.x(), layout.y(), layout.width(), layout.height(), 0xFFE4B9FF);
        Component heading = assigningSlot >= 0
                ? Component.translatable("gui.dimensional_mana.spell_wheel.assign_title", assigningSlot + 1)
                : Component.translatable("gui.dimensional_mana.spell_wheel.assign_title_auto");
        graphics.drawCenteredString(font, heading, width / 2, layout.y() + 10, 0xFFFFE8A6);
        if (spells.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.spell_wheel.no_spells"),
                    width / 2, layout.y() + 47, 0xFFC9B7CE);
            return;
        }

        int from = pickerPage * layout.pageSize();
        int to = Math.min(spells.size(), from + layout.pageSize());
        boolean anyHovered = false;
        for (int index = from; index < to; index++) {
            int local = index - from;
            int cardX = layout.gridX() + local % layout.columns() * CARD_STEP_X;
            int cardY = layout.gridY() + local / layout.columns() * CARD_STEP_Y;
            SpellDefinition spell = spells.get(index);
            boolean hover = mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                    && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;
            anyHovered |= hover;
            graphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT,
                    hover ? 0xD0523470 : 0xB01A1228);
            graphics.renderOutline(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, hover ? 0xFFFFD678 : 0xFF9D79BF);
            graphics.blit(spell.icon(), cardX + 22, cardY + 5, 0, 0, 16, 16, 16, 16);
            graphics.drawCenteredString(font, Component.translatable(spell.translationKey()), cardX + 30, cardY + 28,
                    hover ? 0xFFFFFFFF : 0xFFD6C8E1);
        }

        int footerY = layout.y() + layout.height() - 14;
        if (layout.pageCount() > 1) {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.spell_wheel.page",
                    pickerPage + 1, layout.pageCount()), width / 2, footerY, 0xFFD2B9DF);
            graphics.drawCenteredString(font, Component.literal("‹"), layout.x() + 14, footerY, 0xFFFFE8A6);
            graphics.drawCenteredString(font, Component.literal("›"), layout.x() + layout.width() - 14,
                    footerY, 0xFFFFE8A6);
        } else if (anyHovered) {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.spell_wheel.click_to_assign"),
                    width / 2, footerY, 0xFFFFE8A6);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 若轮盘本身绑定在鼠标上，按下绑定键不应同时触发轮盘内的选择操作。
        if (!wheelBindingReleased && ClientModEvents.matchesSpellWheelMouse(button)) return true;
        if (assigningSlot != Integer.MIN_VALUE) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                assigningSlot = Integer.MIN_VALUE;
                pickerPage = 0;
                if (wheelBindingReleased) onClose();
                return true;
            }
            if (button != InputConstants.MOUSE_BUTTON_LEFT) return true;
            List<SpellDefinition> spells = availableSpells();
            PickerLayout layout = pickerLayout(spells.size());
            if (layout.pageCount() > 1 && mouseY >= layout.y() + layout.height() - 24) {
                if (mouseX < layout.x() + 32) {
                    pickerPage = Math.floorMod(pickerPage - 1, layout.pageCount());
                    return true;
                }
                if (mouseX >= layout.x() + layout.width() - 32) {
                    pickerPage = (pickerPage + 1) % layout.pageCount();
                    return true;
                }
            }
            int index = spellIndexAt(mouseX, mouseY, spells.size(), layout);
            if (index >= 0) {
                PacketDistributor.sendToServer(new SpellActionPayload("assign", spells.get(index).id(), assigningSlot));
                closeAfterSelection();
            }
            return true;
        }

        if (button == InputConstants.MOUSE_BUTTON_RIGHT && hoveredSlot >= 0) {
            PacketDistributor.sendToServer(new SpellActionPayload("clear", "", hoveredSlot));
            return true;
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;

        SpellDefinition hovered = hoveredSlot < 0 ? null : ClientSpellState.slot(hoveredSlot);
        if (hovered != null) {
            PacketDistributor.sendToServer(new SpellActionPayload("select", "", hoveredSlot));
            closeAfterSelection();
            return true;
        }

        // 空槽会精准装入该槽；轮盘空白区域则自动寻找第一个空槽。
        int target = hoveredSlot >= 0 ? hoveredSlot : -1;
        long now = System.currentTimeMillis();
        if (lastBlankTarget == target && now - lastBlankClickTime <= DOUBLE_CLICK_MILLIS) {
            assigningSlot = target;
            pickerPage = 0;
            lastBlankTarget = Integer.MIN_VALUE;
            return true;
        }
        lastBlankTarget = target;
        lastBlankClickTime = now;
        return true;
    }

    /**
     * 点击选择发生在轮盘键释放之前时，先保留界面，让真实释放事件完成修饰键清理。
     * 这能避免默认 Alt 或玩家自定义的修饰键残留，进而让整合包误判并拦截普通滚轮。
     */
    private void closeAfterSelection() {
        assigningSlot = Integer.MIN_VALUE;
        pickerPage = 0;
        if (wheelBindingReleased) onClose();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // KeyMapping 打开界面时会被游戏清空按住状态，因此直接接收真实的释放事件。
        if (ClientModEvents.matchesSpellWheelMouse(button)) {
            wheelBindingReleased = true;
            if (assigningSlot == Integer.MIN_VALUE) onClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientModEvents.matchesSpellWheelKey(keyCode, scanCode)) {
            wheelBindingReleased = true;
            if (assigningSlot == Integer.MIN_VALUE) onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (assigningSlot == Integer.MIN_VALUE || scrollY == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        PickerLayout layout = pickerLayout(availableSpells().size());
        if (layout.pageCount() <= 1) return true;
        pickerPage = Math.floorMod(pickerPage + (scrollY < 0.0 ? 1 : -1), layout.pageCount());
        return true;
    }

    private PickerLayout pickerLayout(int spellCount) {
        int columns = Math.max(1, Math.min(Math.max(1, spellCount), Math.max(1, (width - 40) / CARD_STEP_X)));
        int rows = Math.max(1, Math.min(3, Math.max(1, (height - 70) / CARD_STEP_Y)));
        int pageSize = columns * rows;
        int pageCount = Math.max(1, (spellCount + pageSize - 1) / pageSize);
        int gridWidth = columns * CARD_STEP_X - (CARD_STEP_X - CARD_WIDTH);
        int panelWidth = Math.max(220, gridWidth + 32);
        int panelHeight = rows * CARD_STEP_Y + 46;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        return new PickerLayout(x, y, panelWidth, panelHeight, (width - gridWidth) / 2, y + 31,
                columns, pageSize, pageCount);
    }

    private int spellIndexAt(double mouseX, double mouseY, int spellCount, PickerLayout layout) {
        int from = pickerPage * layout.pageSize();
        int to = Math.min(spellCount, from + layout.pageSize());
        for (int index = from; index < to; index++) {
            int local = index - from;
            int cardX = layout.gridX() + local % layout.columns() * CARD_STEP_X;
            int cardY = layout.gridY() + local / layout.columns() * CARD_STEP_Y;
            if (mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                    && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT) return index;
        }
        return -1;
    }

    private static List<SpellDefinition> availableSpells() {
        List<SpellDefinition> result = new ArrayList<>();
        for (SpellDefinition spell : SpellDefinition.values()) {
            if (ClientSpellState.learned(spell)) result.add(spell);
        }
        return result;
    }

    private static int slotAt(double mouseX, double mouseY, int cx, int cy) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / SLOT_COUNT;
            double x = cx + Math.cos(angle) * 72.0;
            double y = cy + Math.sin(angle) * 72.0;
            if ((mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y) <= 14.0 * 14.0) return i;
        }
        return -1;
    }

    @Override
    public void removed() {
        ClientModEvents.releaseSpellWheelInput();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private record PickerLayout(int x, int y, int width, int height, int gridX, int gridY,
                                int columns, int pageSize, int pageCount) { }
}
