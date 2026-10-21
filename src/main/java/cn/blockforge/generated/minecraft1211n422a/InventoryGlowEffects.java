package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * 物品栏里的“发光 + 拖影”。
 *
 * <p>发光：物品所在格子的四周画一圈会呼吸的光环，环的亮度按物品自己的亮度等级
 * （超导魔晶粉末 8、超导魔晶 10、共鸣核心 12、特级魔精钢 8）缩放，所以在物品栏里一眼能看出谁更亮。</p>
 *
 * <p>拖影：鼠标抓着这类物品在背包里移动时，指针后面会跟一串逐渐变淡的同色光点，
 * 效果和共鸣核心满充时 HUD 上的那串拖影一致。</p>
 */
final class InventoryGlowEffects {
    private static final int TRAIL_LENGTH = 14;
    /** 物品 id → 光环颜色；亮度本身统一从 {@link ItemGlow} 取，避免两边数值对不上。 */
    private static final Map<String, Integer> COLORS = Map.of(
            ResonanceCore.ITEM_ID, 0x8FE6FF,
            "superconducting_crystal", 0x74F0FF,
            "superconducting_crystal_dust", 0x8FD8FF,
            "special_magic_steel", 0xFFE7A8);
    private static final int DEFAULT_COLOR = 0xBFE9FF;
    private static final Deque<double[]> TRAIL = new ArrayDeque<>();
    private static Screen lastScreen;

    private record Glow(int luminance, int color) { }

    static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            TRAIL.clear();
            lastScreen = null;
            return;
        }
        if (screen != lastScreen) {
            TRAIL.clear();
            lastScreen = screen;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        long now = Util.getMillis();

        for (Slot slot : screen.getMenu().slots) {
            Glow glow = glowOf(slot.getItem());
            if (glow == null) continue;
            int centerX = screen.getGuiLeft() + slot.x + 8;
            int centerY = screen.getGuiTop() + slot.y + 8;
            if (centerX < 4 || centerY < 4 || centerX > screen.width - 4 || centerY > screen.height - 4) continue;
            // 鼠标正悬停的格子原版会弹物品提示，别让光环比提示框还抢眼。
            if (event.getMouseX() >= centerX - 8 && event.getMouseX() < centerX + 8
                    && event.getMouseY() >= centerY - 8 && event.getMouseY() < centerY + 8) continue;
            drawHalo(graphics, centerX, centerY, glow, now);
        }

        Glow carried = glowOf(screen.getMenu().getCarried());
        if (carried == null) {
            TRAIL.clear();
            return;
        }
        TRAIL.addLast(new double[]{event.getMouseX(), event.getMouseY()});
        while (TRAIL.size() > TRAIL_LENGTH) TRAIL.removeFirst();
        drawTrail(graphics, carried);
    }

    private static Glow glowOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals(GeneratedMod.MOD_ID)) return null;
        int luminance = ItemGlow.of(id.getPath());
        if (luminance <= 0) return null;
        return new Glow(luminance, COLORS.getOrDefault(id.getPath(), DEFAULT_COLOR));
    }

    /** 呼吸光晕：两圈描边 + 图标上一层很淡的补光。 */
    private static void drawHalo(GuiGraphics graphics, int centerX, int centerY, Glow glow, long now) {
        double level = glow.luminance / 15.0;
        double pulse = 0.5 + 0.5 * Math.sin(now / 260.0 + glow.luminance * 0.7);
        int alpha = (int)Math.round((30 + 42 * pulse) * level);
        int radius = 10 + (int)Math.round(level * 2.0);
        drawRing(graphics, centerX, centerY, radius + 1, withAlpha(glow.color, alpha / 2));
        drawRing(graphics, centerX, centerY, radius, withAlpha(glow.color, alpha));
        graphics.fill(centerX - 8, centerY - 8, centerX + 8, centerY + 8,
                withAlpha(glow.color, (int)Math.round(alpha * 0.22)));
    }

    /** 指针拖影：越旧的点越小越淡；靠近指针的点跳过，免得糊住手上的物品图标。 */
    private static void drawTrail(GuiGraphics graphics, Glow glow) {
        if (TRAIL.isEmpty()) return;
        double[] newest = TRAIL.peekLast();
        int size = TRAIL.size();
        int index = 0;
        double level = glow.luminance / 15.0;
        for (double[] point : TRAIL) {
            double dx = point[0] - newest[0];
            double dy = point[1] - newest[1];
            if (dx * dx + dy * dy < 14.0 * 14.0) {
                index++;
                continue;
            }
            double strength = (size - index) / (double)size;
            int alpha = (int)Math.round(66 * strength * strength * level);
            int radius = 1 + (int)Math.round(strength * 4.0);
            drawDisc(graphics, (int)Math.round(point[0]), (int)Math.round(point[1]), radius,
                    withAlpha(glow.color, alpha));
            index++;
        }
    }

    private static void drawRing(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if ((color >>> 24) == 0) return;
        int inner = radius * radius - radius;
        int outer = radius * radius + radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distance = dx * dx + dy * dy;
                if (distance < inner || distance > outer) continue;
                graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
            }
        }
    }

    private static void drawDisc(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if ((color >>> 24) == 0) return;
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int)Math.floor(Math.sqrt(radius * radius - dy * dy));
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    private InventoryGlowEffects() { }
}
