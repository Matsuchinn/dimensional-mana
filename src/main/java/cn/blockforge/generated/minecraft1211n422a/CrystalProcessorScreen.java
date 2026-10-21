package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CrystalProcessorScreen extends AbstractContainerScreen<CrystalProcessorMenu> {
    public CrystalProcessorScreen(CrystalProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 191;
        inventoryLabelY = 97;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181225);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF8E68C7);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) slot(graphics, leftPos + 16 + col * 18, topPos + 24 + row * 18);
        }
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) slot(graphics, leftPos + 124 + col * 18, topPos + 24 + row * 18);
        }
        slot(graphics, leftPos + 91, topPos + 68);

        graphics.fill(leftPos + 73, topPos + 34, leftPos + 121, topPos + 43, 0xFF241A37);
        int processWidth = Math.min(48, menu.processProgress() * 48 / menu.processTotal());
        graphics.fill(leftPos + 73, topPos + 34, leftPos + 73 + processWidth, topPos + 43, 0xFFBA78FF);
        graphics.renderOutline(leftPos + 73, topPos + 34, 48, 9, 0xFFDFC8FF);
        graphics.drawString(font, "➜", leftPos + 92, topPos + 24, 0xFFE9D7FF, false);

        graphics.fill(leftPos + 82, topPos + 57, leftPos + 120, topPos + 62, 0xFF241A37);
        int fuelWidth = Math.min(38, menu.fuelProgress() * 38 / menu.fuelTotal());
        graphics.fill(leftPos + 82, topPos + 57, leftPos + 82 + fuelWidth, topPos + 62, 0xFF63D9FF);
        graphics.renderOutline(leftPos + 82, topPos + 57, 38, 5, 0xFFA9EEFF);

        graphics.fill(leftPos + 16, topPos + 90, leftPos + 160, topPos + 97, 0xFF161023);
        int manaWidth = Math.min(144, menu.mana() * 144 / menu.maxMana());
        graphics.fillGradient(leftPos + 16, topPos + 90, leftPos + 16 + manaWidth, topPos + 97,
                0xFF226BC8, 0xFF7E5BFF);
        graphics.renderOutline(leftPos + 16, topPos + 90, 144, 7, 0xFF8BCBFF);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) slot(graphics, leftPos + 7 + col * 18, topPos + 108 + row * 18);
        }
        for (int col = 0; col < 9; col++) slot(graphics, leftPos + 7 + col * 18, topPos + 166);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFEAD9FF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFBDB0CE, false);
        String manaText = Component.translatable("gui.dimensional_mana.processor.mana", menu.mana(), menu.maxMana()).getString();
        graphics.drawString(font, manaText, imageWidth - 8 - font.width(manaText), 6, 0xFF74D8FF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= leftPos + 16 && mouseX < leftPos + 160 && mouseY >= topPos + 90 && mouseY < topPos + 97) {
            graphics.renderTooltip(font, Component.translatable("gui.dimensional_mana.processor.mana_tooltip",
                    menu.mana(), menu.maxMana()), mouseX, mouseY);
        }
        if (mouseX >= leftPos + 82 && mouseX < leftPos + 120 && mouseY >= topPos + 57 && mouseY < topPos + 62) {
            graphics.renderTooltip(font, Component.translatable("gui.dimensional_mana.processor.fuel_tooltip"), mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF0C0913);
        graphics.renderOutline(x, y, 18, 18, 0xFF6B558D);
    }
}
