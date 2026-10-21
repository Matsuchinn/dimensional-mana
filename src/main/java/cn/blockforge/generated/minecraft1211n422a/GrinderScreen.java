package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class GrinderScreen extends AbstractContainerScreen<GrinderMenu> {
    private Button grindButton;

    public GrinderScreen(GrinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        grindButton = addRenderableWidget(Button.builder(Component.translatable("gui.dimensional_mana.grind"), button -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 70, topPos + 55, 38, 18).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        grindButton.active = menu.canGrind() && !menu.isGrinding();
        grindButton.setMessage(Component.translatable(menu.isGrinding() ? "gui.dimensional_mana.grinding" : "gui.dimensional_mana.grind"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0181028);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF956BCE);
        slot(graphics, leftPos + 43, topPos + 34);
        slot(graphics, leftPos + 115, topPos + 34);
        graphics.drawCenteredString(Minecraft.getInstance().font, "→", leftPos + 88, topPos + 39, 0xFFBCEBFF);
        graphics.fill(leftPos + 53, topPos + 75, leftPos + 123, topPos + 80, 0xFF241B38);
        int width = (int)(70.0 * menu.progress() / menu.totalProgress());
        graphics.fill(leftPos + 53, topPos + 75, leftPos + 53 + width, topPos + 80, 0xFF4BB8FF);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(graphics, leftPos + 7 + col * 18, topPos + 83 + row * 18);
        for (int col = 0; col < 9; col++) slot(graphics, leftPos + 7 + col * 18, topPos + 141);
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF0E0A18);
        graphics.renderOutline(x, y, 18, 18, 0xFF6C5591);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
