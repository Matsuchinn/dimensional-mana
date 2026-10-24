package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗气技能轮盘：三个槽，默认按大小写锁定键（Caps Lock）打开，松开就关。
 *
 * <p>操作和法术轮盘一致：左键点有技能的槽直接选中并关界面，右键清空该槽，
 * 点空槽会弹出已经学会的斗气能力列表，选一个装进去。所有改动都发回服务端保存。</p>
 */
public final class QiWheelScreen extends Screen {
    private static final int SLOT_COUNT = QiProgression.SLOT_COUNT;
    private static final double RING_RADIUS = 62.0;
    private static final int CARD_WIDTH = 96;
    private static final int CARD_HEIGHT = 44;
    private static final int CARD_STEP = 104;
    private int hoveredSlot = -1;
    /** 正在给哪个槽挑能力；{@link Integer#MIN_VALUE} 表示没在挑。 */
    private int assigningSlot = Integer.MIN_VALUE;
    private boolean wheelBindingReleased;

    public QiWheelScreen() {
        super(Component.translatable("gui.dimensional_mana.qi_wheel.title"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x800A0506);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        hoveredSlot = slotAt(mouseX, mouseY, cx, cy);
        graphics.drawCenteredString(font, title, cx, cy - 107, 0xFFFFE3B0);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.qi_wheel.hint"),
                cx, cy + 100, 0xFFC9A98E);
        for (int i = 0; i < SLOT_COUNT; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / SLOT_COUNT;
            int x = cx + (int)Math.round(Math.cos(angle) * RING_RADIUS);
            int y = cy + (int)Math.round(Math.sin(angle) * RING_RADIUS);
            boolean selected = i == ClientQiState.selectedSlot();
            boolean hovered = i == hoveredSlot;
            graphics.fill(x - 20, y - 20, x + 20, y + 20, hovered ? 0xE04A2418 : 0xC02A1410);
            graphics.renderOutline(x - 20, y - 20, 40, 40,
                    selected ? 0xFFFFC46B : hovered ? 0xFFE8B87A : 0xFF6E4A38);
            QiProgression.Ability ability = ClientQiState.abilityAt(i);
            if (ability != null) {
                ItemStack icon = iconOf(ability);
                if (!icon.isEmpty()) graphics.renderItem(icon, x - 8, y - 8);
                if (selected) graphics.drawCenteredString(font, Component.literal("◆"), x, y + 24, 0xFFFFD66B);
            } else {
                graphics.drawCenteredString(font, Component.literal(Integer.toString(i + 1)), x, y - 4, 0xFF8A7568);
            }
        }
        if (hoveredSlot >= 0) {
            QiProgression.Ability hovered = ClientQiState.abilityAt(hoveredSlot);
            Component line = hovered == null
                    ? Component.translatable("gui.dimensional_mana.qi_wheel.empty", hoveredSlot + 1)
                    : Component.translatable("gui.dimensional_mana.qi_wheel.slot", hoveredSlot + 1,
                    Component.translatable(hovered.translation()), hovered.cost());
            graphics.drawCenteredString(font, line, cx, cy + 78, 0xFFFFFFFF);
        } else {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.qi_wheel.select_hint"),
                    cx, cy + 78, 0xFFC9A98E);
        }
        if (assigningSlot != Integer.MIN_VALUE) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            renderPicker(graphics, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    /** 已学会能力的挑选面板：横排卡片，一张一个能力。 */
    private void renderPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        List<QiProgression.Ability> abilities = availableAbilities();
        int columns = Math.max(1, abilities.size());
        int gridWidth = columns * CARD_STEP - (CARD_STEP - CARD_WIDTH);
        int panelWidth = Math.max(240, gridWidth + 36);
        int panelHeight = 112;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        graphics.fill(0, 0, width, height, 0xE6000000);
        graphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF34181A, 0xFF1A0C0F);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, 0xFFE0B478);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.qi_wheel.assign_title",
                (assigningSlot >= 0 ? assigningSlot : 0) + 1), width / 2, panelY + 9, 0xFFFFE8A6);
        if (abilities.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.qi_wheel.no_abilities"),
                    width / 2, panelY + 47, 0xFFC9B7CE);
            return;
        }
        int gridX = (width - gridWidth) / 2;
        int gridY = panelY + 32;
        for (int index = 0; index < abilities.size(); index++) {
            QiProgression.Ability ability = abilities.get(index);
            int cardX = gridX + index * CARD_STEP;
            boolean hover = mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                    && mouseY >= gridY && mouseY < gridY + CARD_HEIGHT;
            graphics.fill(cardX, gridY, cardX + CARD_WIDTH, gridY + CARD_HEIGHT,
                    hover ? 0xD06A3A22 : 0xB0341B16);
            graphics.renderOutline(cardX, gridY, CARD_WIDTH, CARD_HEIGHT, hover ? 0xFFFFD678 : 0xFF9D6E4B);
            ItemStack icon = iconOf(ability);
            if (!icon.isEmpty()) graphics.renderItem(icon, cardX + 6, gridY + 4);
            graphics.drawString(font, Component.translatable(ability.translation()), cardX + 26, gridY + 6,
                    hover ? 0xFFFFFFFF : 0xFFE6D2BE, false);
            graphics.drawString(font, Component.translatable("gui.dimensional_mana.qi_manual.cost", ability.cost()),
                    cardX + 26, gridY + 17, 0xFF9FE3FF, false);
            String summary = Component.translatable("gui.dimensional_mana.qi_manual." + ability.id() + ".1").getString();
            graphics.drawString(font, font.plainSubstrByWidth(summary, CARD_WIDTH - 12),
                    cardX + 6, gridY + 30, 0xFFB99A80, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!wheelBindingReleased && ClientModEvents.matchesQiWheelMouse(button)) return true;
        if (assigningSlot != Integer.MIN_VALUE) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                assigningSlot = Integer.MIN_VALUE;
                if (wheelBindingReleased) onClose();
                return true;
            }
            if (button != InputConstants.MOUSE_BUTTON_LEFT) return true;
            List<QiProgression.Ability> abilities = availableAbilities();
            int columns = Math.max(1, abilities.size());
            int gridWidth = columns * CARD_STEP - (CARD_STEP - CARD_WIDTH);
            int panelWidth = Math.max(240, gridWidth + 36);
            int panelY = (height - 112) / 2;
            int gridX = (width - gridWidth) / 2;
            int gridY = panelY + 32;
            for (int index = 0; index < abilities.size(); index++) {
                int cardX = gridX + index * CARD_STEP;
                if (mouseX >= cardX && mouseX < cardX + CARD_WIDTH
                        && mouseY >= gridY && mouseY < gridY + CARD_HEIGHT) {
                    PacketDistributor.sendToServer(new QiActionPayload("assign", abilities.get(index).id(),
                            Math.max(0, assigningSlot)));
                    assigningSlot = Integer.MIN_VALUE;
                    if (wheelBindingReleased) onClose();
                    return true;
                }
            }
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT && hoveredSlot >= 0) {
            PacketDistributor.sendToServer(new QiActionPayload("clear", "", hoveredSlot));
            return true;
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;
        QiProgression.Ability hovered = hoveredSlot < 0 ? null : ClientQiState.abilityAt(hoveredSlot);
        if (hovered != null) {
            PacketDistributor.sendToServer(new QiActionPayload("select", "", hoveredSlot));
            if (wheelBindingReleased) onClose();
            return true;
        }
        // 空槽：先把空槽按下去，再在弹窗里挑一个能力装进去。
        assigningSlot = hoveredSlot >= 0 ? hoveredSlot : 0;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // KeyMapping 打开界面时会被游戏清空按住状态，因此直接接收真实的释放事件。
        if (ClientModEvents.matchesQiWheelMouse(button)) {
            wheelBindingReleased = true;
            if (assigningSlot == Integer.MIN_VALUE) onClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientModEvents.matchesQiWheelKey(keyCode, scanCode)) {
            wheelBindingReleased = true;
            if (assigningSlot == Integer.MIN_VALUE) onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private static List<QiProgression.Ability> availableAbilities() {
        List<QiProgression.Ability> result = new ArrayList<>();
        for (QiProgression.Ability ability : QiProgression.Ability.values()) {
            if (ClientQiState.learned(ability)) result.add(ability);
        }
        return result;
    }

    private static ItemStack iconOf(QiProgression.Ability ability) {
        var holder = GeneratedMod.MOD_ITEMS.get(ability.manualId());
        return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get());
    }

    private static int slotAt(double mouseX, double mouseY, int cx, int cy) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / SLOT_COUNT;
            double x = cx + Math.cos(angle) * RING_RADIUS;
            double y = cy + Math.sin(angle) * RING_RADIUS;
            if ((mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y) <= 22.0 * 22.0) return i;
        }
        return -1;
    }

    @Override
    public void removed() {
        ClientModEvents.releaseQiWheelInput();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
