package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 冒险者交易界面：10 笔交易排成两列，每笔按“标价 → 成品”横向摆开。
 * 每一笔的外框颜色就是它的品级，右侧写着还能买几次；点成品格成交。
 */
public final class AdventurerTradeScreen extends AbstractContainerScreen<AdventurerTradeMenu> {
    private static final int CELL_WIDTH = 92;
    private static final int CELL_HEIGHT = 22;

    public AdventurerTradeScreen(AdventurerTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 196;
        imageHeight = 222;
        inventoryLabelY = 130;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181028);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF956BCE);

        for (int index = 0; index < AdventurerTradeMenu.OFFER_COUNT; index++) {
            int cellX = leftPos + AdventurerTradeMenu.cellX(index);
            int cellY = topPos + AdventurerTradeMenu.cellY(index);
            AdventurerTrades.Tier tier = AdventurerTrades.Tier.byOrdinal(menu.tierOrdinal(index));
            boolean hasOffer = menu.maxUses(index) > 0;
            boolean soldOut = menu.remaining(index) <= 0;
            boolean affordable = minecraft != null && minecraft.player != null
                    && menu.affordable(index, minecraft.player);

            graphics.fill(cellX - 3, cellY - 2, cellX + CELL_WIDTH - 3, cellY + CELL_HEIGHT - 2,
                    hasOffer && !soldOut ? 0x401F1533 : 0x30000000);
            graphics.renderOutline(cellX - 3, cellY - 2, CELL_WIDTH, CELL_HEIGHT,
                    hasOffer ? tier.color() : 0xFF40344F);
            for (int part = 0; part < 3; part++) {
                slotBox(graphics, leftPos + AdventurerTradeMenu.partX(index, part) - 1,
                        topPos + AdventurerTradeMenu.partY(index) - 1);
            }
            graphics.drawString(font, "→", leftPos + AdventurerTradeMenu.partX(index, 1) + 20, cellY + 5,
                    0xFFBCEBFF, false);
            if (hasOffer) {
                int color = soldOut ? 0xFF7A6070 : affordable ? 0xFFC9F0D0 : 0xFFE8A0A0;
                graphics.drawString(font, menu.remaining(index) + "/" + menu.maxUses(index),
                        leftPos + AdventurerTradeMenu.partX(index, 2) + 20, cellY + 5, color, false);
            }
        }

        graphics.drawString(font, Component.translatable("gui.dimensional_mana.adventurer_trade.hint"),
                leftPos + 74, topPos + 6, 0xFF9A86C4, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotBox(graphics, leftPos + 7 + col * 18,
                        topPos + AdventurerTradeMenu.INVENTORY_Y - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotBox(graphics, leftPos + 7 + col * 18, topPos + AdventurerTradeMenu.HOTBAR_Y - 1);
        }
    }

    private static void slotBox(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF0E0A18);
        graphics.renderOutline(x, y, 18, 18, 0xFF6C5591);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        Slot hovered = hoveredSlot;
        if (hovered != null && hovered.index >= 0 && hovered.index < AdventurerTradeMenu.OFFER_COUNT * 3) {
            int index = hovered.index / 3;
            AdventurerTrades.Tier tier = AdventurerTrades.Tier.byOrdinal(menu.tierOrdinal(index));
            if (menu.maxUses(index) <= 0) return lines;
            lines.add(Component.translatable(tier.translationKey()).withStyle(tier.formatting()));
            lines.add(Component.translatable("gui.dimensional_mana.adventurer_trade.uses",
                    menu.remaining(index), menu.maxUses(index)).withStyle(ChatFormatting.GRAY));
            if (hovered.index % 3 == 2) {
                boolean soldOut = menu.remaining(index) <= 0;
                lines.add(Component.translatable(soldOut
                        ? "gui.dimensional_mana.adventurer_trade.sold_out"
                        : "gui.dimensional_mana.adventurer_trade.click_to_trade")
                        .withStyle(soldOut ? ChatFormatting.RED : ChatFormatting.YELLOW));
            }
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            int index = offerAt(mouseX, mouseY);
            if (index >= 0) {
                // 走“按钮点击”通道，服务端在 AdventurerTradeMenu#clickMenuButton 里校验并成交。
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 鼠标落在第几笔交易的格子里；不在格子里返回 -1。 */
    private int offerAt(double mouseX, double mouseY) {
        for (int index = 0; index < AdventurerTradeMenu.OFFER_COUNT; index++) {
            int x = leftPos + AdventurerTradeMenu.cellX(index) - 3;
            int y = topPos + AdventurerTradeMenu.cellY(index) - 2;
            if (mouseX >= x && mouseX < x + CELL_WIDTH && mouseY >= y && mouseY < y + CELL_HEIGHT) return index;
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
