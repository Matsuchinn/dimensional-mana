package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 研磨器：箭头、按钮、点击区域均来自真实输入与输出槽位。 */
public final class GrinderScreen extends AbstractContainerScreen<GrinderMenu> {
    private final ArcaneLayout.Layout layout;

    public GrinderScreen(GrinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 166;
        layout = ArcaneGui.layout(menu, ArcaneLayout.Kind.GRINDER);
        titleLabelX = 8; titleLabelY = 6;
        inventoryLabelX = layout.inventoryLabelX(); inventoryLabelY = layout.inventoryLabelY();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ArcaneGui.panel(g, leftPos, topPos, imageWidth, imageHeight, partialTick);
        ArcaneGui.zones(g, layout, leftPos, topPos);
        float progress = (float) menu.progress() / Math.max(1, menu.totalProgress());
        ArcaneGui.progressArrow(g, layout.arrow(), leftPos, topPos, menu.isGrinding() ? progress : 0, menu.isGrinding());
        ArcaneLayout.Box b = layout.button();
        boolean active = menu.canGrind() && !menu.isGrinding();
        boolean hover = active && b.contains(mouseX - leftPos, mouseY - topPos);
        ArcaneGui.button(g, leftPos + b.x(), topPos + b.y(), b.width(), b.height(), hover, active, ArcaneGui.ACCENT);
        Component label = Component.translatable(menu.isGrinding() ? "gui.dimensional_mana.grinding" : "gui.dimensional_mana.grind");
        TextFit.centered(g, font, label, leftPos + b.cx(), topPos + b.cy() - 4, b.width() - 12,
                active || menu.isGrinding() ? ArcaneGui.TEXT : ArcaneGui.TEXT_FAINT);
        ArcaneGui.slots(g, menu, leftPos, topPos, 1);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        TextFit.left(g, font, title, titleLabelX, titleLabelY, imageWidth - 16, ArcaneGui.TEXT, false);
        TextFit.left(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                layout.inventory().width() - 8, ArcaneGui.TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && layout.button().contains(mouseX - leftPos, mouseY - topPos)) {
            if (menu.canGrind() && !menu.isGrinding() && minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
