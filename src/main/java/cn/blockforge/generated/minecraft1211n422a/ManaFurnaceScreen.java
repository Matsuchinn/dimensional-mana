package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 两种魔素熔炉共用：真实槽位中轴上的完整火苗，低燃料平滑缩小并淡出。 */
public final class ManaFurnaceScreen extends AbstractContainerScreen<ManaFurnaceMenu> {
    private final ArcaneLayout.Layout layout;
    public ManaFurnaceScreen(ManaFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        layout = ArcaneGui.layout(menu, ArcaneLayout.Kind.FURNACE);
        titleLabelX = 8; titleLabelY = 6;
        inventoryLabelX = layout.inventoryLabelX(); inventoryLabelY = layout.inventoryLabelY();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ArcaneGui.panel(g, leftPos, topPos, imageWidth, imageHeight, partialTick);
        ArcaneGui.zones(g, layout, leftPos, topPos);
        ArcaneGui.flameBetween(g, menu, 0, 1, leftPos, topPos,
                menu.isLit() ? menu.getLitProgress() : 0.0F, partialTick);
        float progress = (float) menu.cookingProgressTicks() / Math.max(1, menu.cookingTotalTicks());
        ArcaneGui.progressArrow(g, layout.arrow(), leftPos, topPos, progress, progress > 0);
        ArcaneGui.slots(g, menu, leftPos, topPos, 2);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        TextFit.left(g, font, title, titleLabelX, titleLabelY, imageWidth - 16, ArcaneGui.TEXT, false);
        TextFit.left(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                layout.inventory().width() - 8, ArcaneGui.TEXT_DIM, false);
        if (menu.getSlot(0).hasItem() && menu.cookingTotalTicks() > 0) {
            String text = Component.translatable("gui.dimensional_mana.remaining_time",
                    String.format(java.util.Locale.ROOT, "%.1f", menu.remainingTicks() / 20.0)).getString();
            int right = imageWidth - 8;
            if (font.width(text) + 10 < right - font.width(title.getString()) - titleLabelX)
                g.drawString(font, text, right - font.width(text), titleLabelY, ArcaneGui.MANA, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
