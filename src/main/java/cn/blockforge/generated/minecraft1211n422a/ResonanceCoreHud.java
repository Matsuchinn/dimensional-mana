package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 魔力通透·共鸣核心的 HUD：核心图标 + 充能环，满充时按当前元素颜色发光；
 * 满充期间鼠标移动会在指针后面留一串同色拖影。
 */
public final class ResonanceCoreHud {
    private static final int TRAIL_LENGTH = 14;
    private static final Deque<double[]> TRAIL = new ArrayDeque<>();

    public static void renderHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
        if (!ClientResonanceCoreState.equipped()) {
            TRAIL.clear();
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width - HudConfig.RES_X_FROM_RIGHT.get();
        int centerY = height - HudConfig.RES_Y_FROM_BOTTOM.get();
        int elementColor = ResonanceCore.elementColor(ClientResonanceCoreState.element());
        boolean full = ClientResonanceCoreState.ready();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();

        // 底环：深蓝底盘 + 充能进度环。满充时改为元素色，并加一圈呼吸光晕。
        drawDisc(graphics, centerX, centerY, 15, 0x9A0B1024);
        drawDisc(graphics, centerX, centerY, 13, 0xCC121A33);
        int dimColor = 0xFF1E2A4A;
        drawRing(graphics, centerX, centerY, 15, ClientResonanceCoreState.ratio(),
                dimColor, full ? elementColor : 0xFF6FD3FF);
        if (full) {
            int pulse = 0x30 + (int)Math.round(0x50 * (0.5 + 0.5 * Math.sin(time * 0.35)));
            drawRing(graphics, centerX, centerY, 18, 1.0, 0, (pulse << 24) | (elementColor & 0xFFFFFF));
        }

        // 图标亮度随充能提高：在图标上叠一层元素色的半透明光。
        ItemStack stack = new ItemStack(GeneratedMod.MOD_ITEMS.get(ResonanceCore.ITEM_ID).get());
        graphics.renderItem(stack, centerX - 8, centerY - 8);
        double ratio = ClientResonanceCoreState.ratio();
        if (ratio > 0.0) {
            int alpha = (int)Math.round((full ? 0x88 : 0x66) * ratio);
            graphics.fill(centerX - 8, centerY - 8, centerX + 8, centerY + 8, (alpha << 24) | (elementColor & 0xFFFFFF));
        }

        int resonanceTicks = ClientResonanceCoreState.resonanceTicks();
        if (resonanceTicks > 0) {
            drawRing(graphics, centerX, centerY, 21, resonanceTicks / (double)ResonanceCore.RESONANCE_TICKS,
                    0x55000000, elementColor);
            if (HudConfig.SHOW_RES_LABEL.get()) {
                String seconds = String.format("%.0f", resonanceTicks / 20.0);
                graphics.drawCenteredString(minecraft.font, seconds, centerX, centerY + 22, elementColor);
            }
        }

        // HUD 配置里关掉文字时只剩图标与充能环。
        if (HudConfig.SHOW_RES_LABEL.get()) {
            String label;
            if (resonanceTicks > 0) {
                // 共鸣期间显示“魔力通透”，提示玩家四种增伤正在生效。
                label = Component.translatable("hud.dimensional_mana.resonance_core.transparency").getString();
            } else if (full) {
                label = Component.translatable("hud.dimensional_mana.resonance_core.full").getString();
            } else {
                label = String.format("%.0f%%", ClientResonanceCoreState.charge());
            }
            int labelColor = resonanceTicks > 0 || full ? elementColor : 0xFF9FC7FF;
            graphics.drawString(minecraft.font, label, centerX - 24, centerY - 26, labelColor, true);
        }

        // 满充时在鼠标指针后面画同色拖影。
        if (full) renderMouseTrail(graphics, minecraft, width, height, elementColor);
        else TRAIL.clear();
    }

    private static void renderMouseTrail(GuiGraphics graphics, Minecraft minecraft, int guiWidth, int guiHeight,
                                         int color) {
        if (minecraft.getWindow() == null) return;
        double scaleX = guiWidth / (double)minecraft.getWindow().getScreenWidth();
        double scaleY = guiHeight / (double)minecraft.getWindow().getScreenHeight();
        double mouseX = minecraft.mouseHandler.xpos() * scaleX;
        double mouseY = minecraft.mouseHandler.ypos() * scaleY;
        TRAIL.addLast(new double[]{mouseX, mouseY});
        while (TRAIL.size() > TRAIL_LENGTH) TRAIL.removeFirst();

        int index = 0;
        int size = TRAIL.size();
        for (double[] point : TRAIL) {
            double strength = (index + 1) / (double)size;
            int radius = 1 + (int)Math.round(strength * 4.0);
            int alpha = (int)Math.round(0x66 * strength * strength);
            drawDisc(graphics, (int)Math.round(point[0]), (int)Math.round(point[1]), radius,
                    (alpha << 24) | (color & 0xFFFFFF));
            index++;
        }
    }

    private static void drawDisc(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int half = (int)Math.floor(Math.sqrt(radius * radius - y * y));
            graphics.fill(centerX - half, centerY + y, centerX + half + 1, centerY + y + 1, color);
        }
    }

    private static void drawRing(GuiGraphics graphics, int centerX, int centerY, int radius,
                                 double progress, int backgroundColor, int fillColor) {
        int steps = 96;
        int filledSteps = (int)Math.round(Math.max(0.0, Math.min(1.0, progress)) * steps);
        for (int i = 0; i < steps; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / steps;
            int x = centerX + (int)Math.round(Math.cos(angle) * radius);
            int y = centerY + (int)Math.round(Math.sin(angle) * radius);
            int color = i < filledSteps ? fillColor : backgroundColor;
            if ((color >>> 24) == 0) continue;
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private ResonanceCoreHud() { }
}
