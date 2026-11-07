package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 魔晶砂轮的界面。
 *
 * <p>左边两个输入格（上面放要处理的那件、下面可以再叠一件同类装备做合并修复），
 * 右边一格成品，中间一个「模式切换」按钮；按钮下面两行字说清
 * <b>当前模式做什么</b>和<b>这一下能拿回什么</b>。</p>
 *
 * <p>画面上：底板和格子全部来自 {@link ArcaneGui}，格子按菜单真实坐标画；
 * 成品格用灰银内沿区分，不再叠金边和外扩光环；按钮和提示随槽位布局移动。</p>
 */
public final class ManaGrindstoneScreen extends AbstractContainerScreen<ManaGrindstoneMenu> {
    private static final int RESULT_SLOT_INDEX = 2;
    private final ArcaneLayout.Layout layout;

    public ManaGrindstoneScreen(ManaGrindstoneMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ManaGrindstoneMenu.IMAGE_WIDTH;
        imageHeight = ManaGrindstoneMenu.IMAGE_HEIGHT;
        titleLabelX = 8;
        titleLabelY = 6;
        layout = ArcaneGui.layout(menu, ArcaneLayout.Kind.GRINDSTONE);
        inventoryLabelX = layout.inventoryLabelX();
        inventoryLabelY = layout.inventoryLabelY();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        ArcaneGui.panel(graphics, x, y, imageWidth, imageHeight, partialTick);
        ArcaneGui.zones(graphics, layout, x, y);
        boolean ready = !menu.resultItem().isEmpty();
        ArcaneGui.progressArrow(graphics, layout.arrow(), x, y, ready ? 1 : 0, ready);

        drawModeButton(graphics, mouseX, mouseY);
        ArcaneGui.slots(graphics, menu, x, y, RESULT_SLOT_INDEX);
    }

    private void drawModeButton(GuiGraphics graphics, int mouseX, int mouseY) {
        ArcaneLayout.Box b = layout.button();
        int x = leftPos + b.x();
        int y = topPos + b.y();
        int w = b.width();
        int h = b.height();
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        boolean affixMode = menu.mode() == ManaGrindstoneLogic.MODE_AFFIXES;
        ArcaneGui.button(graphics, x, y, w, h, hovered, true, affixMode ? ArcaneGui.GOLD : ArcaneGui.MANA);
        TextFit.centeredShrunk(graphics, font, menu.modeName(), x + w / 2, y + 5, w - 8, ArcaneGui.TEXT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        TextFit.leftShrunk(graphics, font, title, titleLabelX, titleLabelY, 120, ArcaneGui.TEXT);
        TextFit.left(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                imageWidth - inventoryLabelX * 2, ArcaneGui.TEXT_DIM, false);
        renderModeHint(graphics);
        renderResultHint(graphics);
    }

    /** 模式说明：当前这一档到底剥什么、保什么。 */
    private void renderModeHint(GuiGraphics graphics) {
        boolean affixMode = menu.mode() == ManaGrindstoneLogic.MODE_AFFIXES;
        Component hint = Component.translatable(affixMode
                ? "gui.dimensional_mana.mana_grindstone.mode_hint_affix"
                : "gui.dimensional_mana.mana_grindstone.mode_hint_enchant");
        TextFit.leftShrunk(graphics, font, hint, layout.inventoryLabelX(), layout.button().bottom() + 3,
                layout.inventory().width() - 8, affixMode ? ArcaneGui.ACCENT : ArcaneGui.MANA);
    }

    /** 成品说明：能不能磨、磨完拿回什么。 */
    private void renderResultHint(GuiGraphics graphics) {
        ItemStack top = menu.topItem();
        ItemStack bottom = menu.bottomItem();
        Component hint;
        int color = ArcaneGui.TEXT_FAINT;
        if (top.isEmpty() && bottom.isEmpty()) {
            hint = Component.translatable("gui.dimensional_mana.mana_grindstone.empty");
        } else if (!top.isEmpty() && !bottom.isEmpty()) {
            if (menu.resultItem().isEmpty()) {
                hint = Component.translatable("gui.dimensional_mana.mana_grindstone.nothing");
                color = ArcaneGui.WARN;
            } else {
                hint = Component.translatable("gui.dimensional_mana.mana_grindstone.repair");
                color = ArcaneGui.MANA;
            }
        } else if (menu.resultItem().isEmpty()) {
            hint = Component.translatable("gui.dimensional_mana.mana_grindstone.nothing");
            color = ArcaneGui.WARN;
        } else if (menu.mode() == ManaGrindstoneLogic.MODE_AFFIXES) {
            Component refund = refundText(menu.topItem().isEmpty() ? menu.bottomItem() : menu.topItem());
            hint = refund == null
                    ? Component.translatable("gui.dimensional_mana.mana_grindstone.no_refund")
                    : Component.translatable("gui.dimensional_mana.mana_grindstone.refund", refund);
            color = refund == null ? ArcaneGui.TEXT_FAINT : ArcaneGui.GOLD;
        } else {
            hint = Component.translatable("gui.dimensional_mana.mana_grindstone.gain_xp");
            color = ArcaneGui.OK;
        }
        TextFit.leftShrunk(graphics, font, hint, layout.inventoryLabelX(), layout.button().bottom() + 14,
                layout.inventory().width() - 8, color);
    }

    /**
     * 把返还清单压成一行「武备强化石粉 ×2 · 特级魔晶粉 ×1」。
     * 同一件物品只出现一次、数量合并；一件都不返时返回 {@code null}。
     */
    private Component refundText(ItemStack source) {
        List<ItemStack> refunds = ManaGrindstoneLogic.refunds(source);
        if (refunds.isEmpty()) return null;
        Map<Component, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : refunds) {
            counts.merge(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE), 1, Integer::sum);
        }
        Component line = Component.empty();
        boolean first = true;
        for (Map.Entry<Component, Integer> entry : counts.entrySet()) {
            if (!first) line = line.copy().append(Component.literal(" · "));
            first = false;
            line = line.copy().append(entry.getKey().copy())
                    .append(Component.literal(" ×" + entry.getValue()));
        }
        return line;
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (layout.button().contains(mouseX - leftPos, mouseY - topPos)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.dimensional_mana.mana_grindstone.tooltip_mode")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("gui.dimensional_mana.mana_grindstone.mode_enchant")
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("gui.dimensional_mana.mana_grindstone.mode_hint_enchant")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.dimensional_mana.mana_grindstone.mode_affix")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("gui.dimensional_mana.mana_grindstone.mode_hint_affix")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.dimensional_mana.mana_grindstone.penalty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && layout.button().contains(mouseX - leftPos, mouseY - topPos)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ManaGrindstoneMenu.BUTTON_TOGGLE_MODE);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
