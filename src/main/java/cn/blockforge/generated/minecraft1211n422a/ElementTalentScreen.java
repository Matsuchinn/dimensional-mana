package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumSet;

/** 首次打开万法知识之书时出现的三元素选择界面。 */
public final class ElementTalentScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 270;
    private final EnumSet<ElementType> selected = EnumSet.noneOf(ElementType.class);
    private final boolean openGuideAfter;
    private int left;
    private int top;
    private Button confirm;

    public ElementTalentScreen(boolean openGuideAfter) {
        super(Component.translatable("gui.dimensional_mana.element_resonance.title"));
        this.openGuideAfter = openGuideAfter;
    }

    @Override protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        clearWidgets();
        confirm = Button.builder(Component.translatable("gui.dimensional_mana.element_resonance.confirm"), button -> confirm())
                .bounds(left + 140, top + PANEL_HEIGHT - 29, 140, 20).build();
        confirm.active = selected.size() == ElementTalentData.INITIAL_CHOICES;
        addRenderableWidget(confirm);
    }

    private void confirm() {
        if (selected.size() != ElementTalentData.INITIAL_CHOICES) return;
        ElementType[] values = selected.toArray(ElementType[]::new);
        PacketDistributor.sendToServer(new ElementTalentActionPayload(values[0].id(), values[1].id(), values[2].id()));
        if (minecraft != null) minecraft.setScreen(openGuideAfter ? new ArcaneCodexGuideScreen() : new ArcaneCodexScreen());
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0060710);
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0181330, 0xF0081C2A);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFD6A7FF);
        graphics.drawCenteredString(font, title, width / 2, top + 11, 0xFFFFE4A8);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.element_resonance.subtitle",
                selected.size(), ElementTalentData.INITIAL_CHOICES), width / 2, top + 26, 0xFFCDB8DF);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.element_resonance.arcane_guarantee"),
                width / 2, top + 39, 0xFFB58AFF);
        for (int i = 0; i < ElementType.values().length; i++) {
            ElementType element = ElementType.values()[i];
            int x = left + 16 + (i % 4) * 99;
            int y = top + 57 + (i / 4) * 58;
            boolean hover = inside(mouseX, mouseY, x, y, 91, 50);
            boolean picked = selected.contains(element);
            graphics.fill(x, y, x + 91, y + 50, picked ? 0xB03D285D : hover ? 0x8A29384A : 0x80120F20);
            graphics.renderOutline(x, y, 91, 50, picked ? element.color() : hover ? 0xFFE5CEFF : 0xFF715B87);
            graphics.renderItem(new ItemStack(element.icon()), x + 7, y + 7);
            graphics.drawString(font, Component.translatable(element.translationKey()), x + 29, y + 7,
                    picked ? element.color() : 0xFFF4ECFF, false);
            graphics.drawWordWrap(font, Component.translatable(element.descriptionKey()), x + 29, y + 19, 57, 0xFFBFB4C8);
            if (picked) graphics.drawString(font, Component.literal("✓").withStyle(ChatFormatting.GREEN), x + 75, y + 6, 0xFF7CFF9C, false);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < ElementType.values().length; i++) {
                int x = left + 16 + (i % 4) * 99;
                int y = top + 57 + (i / 4) * 58;
                if (inside(mouseX, mouseY, x, y, 91, 50)) {
                    ElementType element = ElementType.values()[i];
                    if (!selected.remove(element) && selected.size() < ElementTalentData.INITIAL_CHOICES) selected.add(element);
                    confirm.active = selected.size() == ElementTalentData.INITIAL_CHOICES;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override public boolean isPauseScreen() { return false; }
}
