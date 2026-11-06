package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;

/**
 * 魔晶铁砧界面：整块换掉原版铁砧贴图，改成紫晶面板 + 青色符文 + 金色结果槽，
 * 并且把右下角的“附魔花费（经验）”换成魔素消耗。魔素不够时价格标红、结果格旁边
 * 出现红色提示，点结果格会按“强行合成”处理。
 */
public final class MagicCrystalAnvilScreen extends AnvilScreen {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/gui/magic_crystal_anvil.png");
    /** 贴图实际尺寸。必须显式传：6 参 blit 按 256x256 采样，会把 176x166 的图放大并裁掉下半截。 */
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 166;
    private static final int TITLE_COLOR = 0xFFE8DCFF;
    private static final int LABEL_COLOR = 0xFF9C8ABF;
    private static final int MANA_OK = 0xFF8CF3FF;
    private static final int MANA_SHORT = 0xFFFF5A5A;

    public MagicCrystalAnvilScreen(AnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // 上一次开砧台的价格不能残留到这一次（放在构造里，窗口缩放重跑 init 时不会误清）。
        ClientAnvilState.clear();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        // 标题下方的呼吸青光：纯装饰，跟着玩家刻慢慢明暗，呼应护甲上的光波。
        Minecraft minecraft = Minecraft.getInstance();
        int ticks = minecraft.player == null ? 0 : minecraft.player.tickCount;
        float pulse = 0.5F + 0.5F * Mth.sin((ticks + partialTick) * 0.08F);
        int alpha = (int)(40 + 70 * pulse);
        int cyan = (alpha << 24) | 0x55E8FF;
        graphics.hLine(this.leftPos + 6, this.leftPos + this.imageWidth - 7, this.topPos + 17, cyan);
        graphics.hLine(this.leftPos + 6, this.leftPos + this.imageWidth - 7, this.topPos + 18, (cyan & 0x00FFFFFF) | ((alpha / 2) << 24));
        if (!ClientAnvilState.affordable() && ClientAnvilState.cost() > 0) {
            // 魔素不足：结果槽外的菱形光环换成红色，一眼能看出“现在合不了”。
            int red = (int)(120 + 80 * pulse) << 24 | 0xFF4A4A;
            int cx = this.leftPos + 142;
            int cy = this.topPos + 55;
            for (int offset = 12; offset <= 13; offset++) {
                graphics.hLine(cx - 2, cx + 2, cy - offset, red);
                graphics.hLine(cx - 2, cx + 2, cy + offset, red);
                graphics.vLine(cx - offset, cy - 2, cy + 2, red);
                graphics.vLine(cx + offset, cy - 2, cy + 2, red);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 不调用 super：原版这一层画的是“附魔花费（经验）”。
        graphics.drawString(this.font, this.title, 8, 6, TITLE_COLOR, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 8, 72, LABEL_COLOR, false);
        int cost = ClientAnvilState.cost();
        if (cost <= 0) return;
        Component label = Component.translatable(ClientAnvilState.renameOnly()
                ? "gui.dimensional_mana.magic_crystal_anvil.rename_cost"
                : "gui.dimensional_mana.magic_crystal_anvil.mana_cost", cost);
        int color = ClientAnvilState.affordable() ? MANA_OK : MANA_SHORT;
        graphics.drawString(this.font, label, this.imageWidth - 8 - this.font.width(label), 58, color, false);
        if (!ClientAnvilState.affordable()) {
            Component short_ = Component.translatable("gui.dimensional_mana.magic_crystal_anvil.no_mana");
            graphics.drawString(this.font, short_, this.imageWidth - 8 - this.font.width(short_), 67, MANA_SHORT, false);
        }
    }

    @Override
    protected void renderErrorIcon(GuiGraphics graphics, int mouseX, int mouseY) {
        // 原版这里画的是“过于昂贵”的红色叉；换成魔素不足的提示点。
        if (ClientAnvilState.cost() <= 0 || ClientAnvilState.affordable()) return;
        int x = this.leftPos + 97;
        int y = this.topPos + 41;
        graphics.fill(x, y, x + 13, y + 13, 0xC0200A12);
        graphics.hLine(x, x + 12, y, 0xFFFF5A5A);
        graphics.hLine(x, x + 12, y + 12, 0xFFFF5A5A);
        graphics.vLine(x, y, y + 12, 0xFFFF5A5A);
        graphics.vLine(x + 12, y, y + 12, 0xFFFF5A5A);
        graphics.fill(x + 5, y + 3, x + 8, y + 8, 0xFFFF5A5A);
        graphics.fill(x + 5, y + 9, x + 8, y + 11, 0xFFFF5A5A);
    }
}
