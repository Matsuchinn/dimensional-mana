package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;

/** 铁砧沿用原版改名行为；输入框与流程箭头从实际槽位推导。 */
public final class MagicCrystalAnvilScreen extends AnvilScreen {
    private final ArcaneLayout.Layout layout;
    public MagicCrystalAnvilScreen(AnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        layout = ArcaneGui.layout(menu, ArcaneLayout.Kind.ANVIL);
        ClientAnvilState.clear();
    }

    @Override
    protected void subInit() {
        super.subInit();
        ArcaneLayout.Box b = layout.name();
        for (var child : children()) {
            if (child instanceof EditBox field) {
                field.setX(leftPos + b.x() + 2); field.setY(topPos + b.y() + 2);
                field.setWidth(b.width() - 4); field.setHeight(b.height() - 4);
                field.setTextColor(ArcaneGui.TEXT); field.setTextColorUneditable(ArcaneGui.TEXT_FAINT);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ArcaneGui.panel(g, leftPos, topPos, imageWidth, imageHeight, partialTick);
        ArcaneGui.zones(g, layout, leftPos, topPos);
        ArcaneLayout.Box b = layout.name();
        ArcaneGui.field(g, leftPos + b.x(), topPos + b.y(), b.width(), b.height());
        var a = menu.getSlot(0); var second = menu.getSlot(1);
        TextFit.centered(g, font, Component.literal("+"), leftPos + (a.x + 16 + second.x) / 2,
                topPos + a.y + 4, 16, ArcaneGui.TEXT_FAINT);
        boolean ready = menu.getSlot(2).hasItem();
        if (ClientAnvilState.cost() <= 0 || ClientAnvilState.affordable())
            ArcaneGui.progressArrow(g, layout.arrow(), leftPos, topPos, ready ? 1 : 0, ready);
        ArcaneGui.slots(g, menu, leftPos, topPos, 2);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        TextFit.left(g, font, title, 8, 6, imageWidth - 16, ArcaneGui.TEXT, false);
        int x = layout.inventoryLabelX(), y = layout.inventoryLabelY(), right = layout.inventory().right() - 4;
        TextFit.left(g, font, playerInventoryTitle, x, y, 54, ArcaneGui.TEXT_DIM, false);
        int cost = ClientAnvilState.cost();
        if (cost <= 0) return;
        Component label = Component.translatable(ClientAnvilState.renameOnly()
                ? "gui.dimensional_mana.magic_crystal_anvil.rename_cost"
                : "gui.dimensional_mana.magic_crystal_anvil.mana_cost", cost);
        int color = ClientAnvilState.affordable() ? ArcaneGui.MANA : ArcaneGui.WARN;
        Component fitted = TextFit.clamp(font, label, right - x - 58);
        g.drawString(font, fitted, right - font.width(fitted), y, color, false);
    }

    @Override
    public void renderFg(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderFg(g, mouseX, mouseY, partialTick);
        // 自绘底板不走原版 renderBg，所以错误提示在前景明确调用，避免漏画。
        renderErrorIcon(g, mouseX, mouseY);
    }

    @Override
    protected void renderErrorIcon(GuiGraphics g, int mouseX, int mouseY) {
        if (ClientAnvilState.cost() <= 0 || ClientAnvilState.affordable()) return;
        ArcaneLayout.Box b = layout.arrow();
        TextFit.centered(g, font, Component.literal("!"), leftPos + b.cx(), topPos + b.cy() - 4,
                b.width(), ArcaneGui.WARN);
        if (b.contains(mouseX - leftPos, mouseY - topPos))
            g.renderTooltip(font, Component.translatable("gui.dimensional_mana.magic_crystal_anvil.no_mana"), mouseX, mouseY);
    }
}
