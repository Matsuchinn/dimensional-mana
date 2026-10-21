package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class ManaFurnaceScreen extends AbstractContainerScreen<ManaFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");

    public ManaFurnaceScreen(ManaFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        if (menu.isLit()) {
            int litHeight = Math.max(1, (int)Math.ceil(menu.getLitProgress() * 13.0F));
            graphics.blit(TEXTURE, leftPos + 56, topPos + 36 + 12 - litHeight,
                    176.0F, 12.0F - litHeight, 14, litHeight, 256, 256);
        }
        int progressWidth = menu.cookingTotalTicks() <= 0 ? 0
                : Math.min(24, (int)Math.ceil(menu.cookingProgressTicks() * 24.0F / menu.cookingTotalTicks()));
        if (progressWidth > 0) {
            graphics.blit(TEXTURE, leftPos + 79, topPos + 34, 176.0F, 14.0F,
                    progressWidth, 16, 256, 256);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.getSlot(0).hasItem() && menu.cookingTotalTicks() > 0) {
            double seconds = menu.remainingTicks() / 20.0;
            String text = Component.translatable("gui.dimensional_mana.remaining_time", String.format("%.1f", seconds)).getString();
            graphics.drawString(font, text, imageWidth - 8 - font.width(text), 6, 0xFF6B3E9E, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
