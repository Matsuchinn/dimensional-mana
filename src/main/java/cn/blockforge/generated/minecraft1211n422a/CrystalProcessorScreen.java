package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 魔晶加工台：三个进度指示的位置、宽度和悬停区域都从槽位推导。 */
public final class CrystalProcessorScreen extends AbstractContainerScreen<CrystalProcessorMenu> {
    private final ArcaneLayout.Layout layout;
    public CrystalProcessorScreen(CrystalProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 199;
        layout = ArcaneGui.layout(menu, ArcaneLayout.Kind.PROCESSOR);
        titleLabelX = 8; titleLabelY = 6;
        inventoryLabelX = layout.inventoryLabelX(); inventoryLabelY = layout.inventoryLabelY();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ArcaneGui.panel(g, leftPos, topPos, imageWidth, imageHeight, partialTick);
        ArcaneGui.zones(g, layout, leftPos, topPos);
        float process = (float) menu.processProgress() / Math.max(1, menu.processTotal());
        ArcaneGui.progressArrow(g, layout.arrow(), leftPos, topPos, process, process > 0);
        ArcaneGui.bar(g, layout.fuel(), leftPos, topPos, (float) menu.fuelProgress() / Math.max(1, menu.fuelTotal()));
        ArcaneGui.bar(g, layout.mana(), leftPos, topPos, (float) menu.mana() / Math.max(1, menu.maxMana()));
        ArcaneGui.slots(g, menu, leftPos, topPos, 9, 10, 11, 12);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        TextFit.left(g, font, title, titleLabelX, titleLabelY, imageWidth - 16, ArcaneGui.TEXT, false);
        TextFit.left(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                54, ArcaneGui.TEXT_DIM, false);
        // 数值放背包标签行右侧，长标题不再与魔素数值互相挤压。
        Component mana = Component.translatable("gui.dimensional_mana.processor.mana", menu.mana(), menu.maxMana());
        int right = layout.inventory().right() - 4;
        Component fitted = TextFit.clamp(font, mana, right - inventoryLabelX - 58);
        g.drawString(font, fitted, right - font.width(fitted), inventoryLabelY, ArcaneGui.MANA, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (layout.mana().contains(mouseX - leftPos, mouseY - topPos))
            g.renderTooltip(font, Component.translatable("gui.dimensional_mana.processor.mana_tooltip",
                    menu.mana(), menu.maxMana()), mouseX, mouseY);
        if (layout.fuel().contains(mouseX - leftPos, mouseY - topPos))
            g.renderTooltip(font, Component.translatable("gui.dimensional_mana.processor.fuel_tooltip"), mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }
}
