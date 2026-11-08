package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** 石墨底、灰银内沿、低饱和灰紫；背景法阵始终先于功能区和物品绘制。 */
public final class ArcaneGui {
    public static final ResourceLocation SIGIL_RING = texture("arcane_sigil_ring");
    public static final ResourceLocation SIGIL_CORE = texture("arcane_sigil_core");
    public static final ResourceLocation FLAME = texture("arcane_flame");
    public static final ResourceLocation CONDUIT = texture("arcane_conduit");
    public static final ResourceLocation SPARK = texture("arcane_spark");
    public static final int PANEL_TOP = 0xFF191C24;
    public static final int PANEL_BOTTOM = 0xFF101218;
    public static final int EDGE_OUTER = 0xFF080A0F;
    public static final int EDGE_INNER = 0xFF353945;
    public static final int GROUP_FILL = 0x28000000;
    public static final int SLOT_FILL = 0xFF101219;
    public static final int SLOT_EDGE = 0xFF363B48;
    public static final int SLOT_RESULT_FILL = 0xFF14161F;
    public static final int SLOT_RESULT_EDGE = 0xFF686779;
    public static final int TEXT = 0xFFF1F0F5;
    public static final int TEXT_DIM = 0xFFC4C7D1;
    public static final int TEXT_FAINT = 0xFF959BAA;
    public static final int ACCENT = 0xFFA8A0BA;
    public static final int GOLD = 0xFFC1B5A0;
    public static final int MANA = 0xFFA2B6C7;
    public static final int OK = 0xFFACC3B3;
    public static final int WARN = 0xFFD4A3A6;
    public static final int TRACK = 0xFF363C49;
    public static final int PROGRESS_FROM = 0xFF707C93;
    public static final int PROGRESS_TO = 0xFFA49AB6;
    private ArcaneGui() { }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/gui/" + name + ".png");
    }

    public static ArcaneLayout.Layout layout(AbstractContainerMenu menu, ArcaneLayout.Kind kind) {
        int[][] slots = new int[menu.slots.size()][2];
        for (int i = 0; i < slots.length; i++) {
            slots[i][0] = menu.getSlot(i).x;
            slots[i][1] = menu.getSlot(i).y;
        }
        return ArcaneLayout.of(kind, slots);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        g.fill(x - 1, y, x + w + 1, y + h + 1, 0x40000000);
        g.fillGradient(x, y, x + w, y + h, PANEL_TOP, PANEL_BOTTOM);
        sigils(g, x, y, w, h, System.currentTimeMillis());
        g.renderOutline(x, y, w, h, EDGE_OUTER);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, EDGE_INNER);
    }

    public static void zones(GuiGraphics g, ArcaneLayout.Layout layout, int x, int y) {
        fillBox(g, layout.machine(), x, y, GROUP_FILL);
        fillBox(g, layout.inventory(), x, y, GROUP_FILL);
    }

    public static void fillBox(GuiGraphics g, ArcaneLayout.Box b, int x, int y, int color) {
        g.fill(x + b.x(), y + b.y(), x + b.right(), y + b.bottom(), color);
    }

    /** 三个位置轮流经历八秒生命周期；不会同时存在两个高亮阵。
     * 两层的透明度上限之和为 15%，即使线条相交，合成透明度也不超过 15%。 */
    private static void sigils(GuiGraphics g, int x, int y, int width, int height, long now) {
        g.enableScissor(x + 2, y + 2, x + width - 2, y + height - 2);
        try {
            for (int i = 0; i < 3; i++) {
                ArcaneMotion.Sigil s = ArcaneMotion.sigil(now, i, width, height);
                if (s.life() <= 0.001F) continue;
                textured(g, SIGIL_RING, x + s.x(), y + s.y(), s.size(), s.size(), s.angle(),
                        0xBCB5CC, ArcaneMotion.RING_ALPHA * s.life());
                textured(g, SIGIL_CORE, x + s.x(), y + s.y(), s.size() * 0.76F, s.size() * 0.76F,
                        -s.angle() * 0.8F, 0xA6B4C5, ArcaneMotion.CORE_ALPHA * s.life());
            }
        } finally {
            g.disableScissor();
        }
    }

    /** 专用纹理使用双线性过滤；颜色、混合和矩阵状态在异常时也恢复。 */
    static void textured(GuiGraphics g, ResourceLocation texture, float cx, float cy,
                                 float width, float height, float angle, int rgb, float alpha) {
        if (alpha <= 0 || width <= 0 || height <= 0) return;
        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            g.flush();
            Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            pose.translate(cx, cy, 0.0F);
            pose.mulPose(Axis.ZP.rotation(angle));
            pose.scale(width / ArcaneMotion.TEXTURE_SIZE, height / ArcaneMotion.TEXTURE_SIZE, 1.0F);
            g.setColor(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F,
                    (rgb & 255) / 255.0F, alpha);
            g.blit(texture, -32, -32, 0.0F, 0.0F, 64, 64, 64, 64);
        } finally {
            g.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            pose.popPose();
        }
    }

    public static void slots(GuiGraphics g, AbstractContainerMenu menu, int left, int top, int... results) {
        for (Slot slot : menu.slots) {
            if (slot.x < 0 || slot.y < 0) continue;
            boolean result = false;
            for (int i : results) if (i == slot.index) result = true;
            frame(g, left + slot.x, top + slot.y, result, false);
        }
    }

    public static void frame(GuiGraphics g, int x, int y, boolean result, boolean halo) {
        g.fill(x - 1, y - 1, x + 17, y + 17, result ? SLOT_RESULT_FILL : SLOT_FILL);
        g.renderOutline(x - 1, y - 1, 18, 18, result ? SLOT_RESULT_EDGE : SLOT_EDGE);
        // 内侧 1px 暗沿产生凹槽深度，不再外扩光环。
        g.fill(x, y, x + 16, y + 1, 0x60000000);
    }

    public static void field(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, SLOT_FILL);
        g.renderOutline(x, y, width, height, SLOT_EDGE);
    }

    public static void bar(GuiGraphics g, int x, int y, int width, int height,
                           float progress, int from, int to) {
        g.fill(x, y, x + width, y + height, SLOT_FILL);
        int filled = Math.round((width - 2) * ArcaneMotion.clamp(progress));
        if (filled > 0) g.fillGradient(x + 1, y + 1, x + 1 + filled, y + height - 1, from, to);
        g.renderOutline(x, y, width, height, TRACK);
    }

    public static void bar(GuiGraphics g, ArcaneLayout.Box b, int x, int y, float progress) {
        bar(g, x + b.x(), y + b.y(), b.width(), b.height(), progress, PROGRESS_FROM, PROGRESS_TO);
    }

    /** r346 火苗：用上下两个真实槽位的中轴绘制 16 行像素火焰，不再使用方框贴图。 */
    public static void flameBetween(GuiGraphics graphics, AbstractContainerMenu menu,
                                    int upperSlotIndex, int lowerSlotIndex,
                                    int left, int top, float progress, float partialTick) {
        Slot upper = menu.getSlot(upperSlotIndex);
        Slot lower = menu.getSlot(lowerSlotIndex);
        int centerX = left + (upper.x + lower.x) / 2 + 8;
        int bottomY = top + lower.y - 2;
        int height = bottomY - (top + upper.y + 18);
        flame(graphics, centerX, bottomY, height, progress, partialTick);
    }

    private static final int[] FLAME_HALF = {4, 5, 5, 5, 5, 4, 4, 4, 4, 3, 3, 3, 2, 2, 1, 1};
    private static final int FLAME_ROWS = 16;

    private static void flame(GuiGraphics graphics, int centerX, int bottomY, int height,
                              float progress, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        float time = (minecraft.player == null ? 0 : minecraft.player.tickCount) + partialTick;
        int rows = Mth.clamp(height, 8, FLAME_ROWS);
        boolean lit = progress > 0.02F;
        float flicker = 0.93F + 0.07F * Mth.sin(time * 0.31F) * Mth.cos(time * 0.19F + 0.7F);
        int sway = Math.round(Mth.sin(time * 0.055F) * 1.7F);
        for (int i = 0; i < rows; i++) {
            float t = i / (float) (rows - 1);
            int y = bottomY - 1 - i;
            int half = FLAME_HALF[Math.min(FLAME_ROWS - 1, Math.round(t * (FLAME_ROWS - 1)))];
            int offset = Math.round(sway * t * 1.4F);
            int x0 = centerX - half + offset;
            int x1 = centerX + half + 1 + offset;
            if (!lit) {
                graphics.fill(x0, y, x1, y + 1, 0x2E2A2350);
                continue;
            }
            if (t > progress) continue;
            int color = flameColor(t);
            int alpha = flameAlpha(t);
            if (t > progress - 0.12F) alpha = Math.round(alpha * 0.55F);
            alpha = Math.round(alpha * flicker);
            graphics.fill(x0 - 2, y, x1 + 2, y + 1,
                    (Math.round(alpha * 0.15F) << 24) | (color & 0xFFFFFF));
            graphics.fill(x0, y, x1, y + 1, (alpha << 24) | (color & 0xFFFFFF));
            if (t < 0.72F) {
                int core = Math.max(1, Math.round(half * 0.42F));
                graphics.fill(centerX - core + offset, y, centerX + core + 1 + offset, y + 1,
                        (Math.round(alpha * 0.62F) << 24) | 0xFFFFF3D2);
            }
        }
    }

    private static int flameColor(float t) {
        if (t < 0.30F) return lerpColor(0xFFF1C4, 0xFFD066, t / 0.30F);
        if (t < 0.62F) return lerpColor(0xFFD066, 0xFF8A3C, (t - 0.30F) / 0.32F);
        if (t < 0.85F) return lerpColor(0xFF8A3C, 0xD96BFF, (t - 0.62F) / 0.23F);
        return lerpColor(0xD96BFF, 0x8E5BFF, (t - 0.85F) / 0.15F);
    }

    private static int flameAlpha(float t) {
        return t < 0.5F ? 255 : Math.round(255.0F - 140.0F * (t - 0.5F) / 0.5F);
    }

    private static int lerpColor(int from, int to, float f) {
        float k = Mth.clamp(f, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(k, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(k, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
        return (r << 16) | (green << 8) | blue;
    }

    public static void conduit(GuiGraphics g, ArcaneLayout.Box b, int left, int top) {
        float phase = ArcaneMotion.spark(System.currentTimeMillis());
        int x = left + b.cx(), y = top + b.y();
        // 整条连线旋转 180°，并让流光从下往上，表示魔晶能量流向上方武备。
        for (int band = 0; band < 8; band++) {
            int from = y + b.height() * band / 8;
            int to = y + b.height() * (band + 1) / 8;
            float fade = band == 7 ? 0.48F : 0.40F * Mth.sin((band + 0.5F) / 8.0F * Mth.PI);
            g.enableScissor(left + b.x(), from, left + b.right(), to);
            try {
                textured(g, CONDUIT, x, y + b.height() / 2.0F, 24, b.height(), (float)Math.PI,
                        0xAABACB, fade);
            } finally { g.disableScissor(); }
        }
        float sparkY = y + 2 + (1.0F - phase) * Math.max(0, b.height() - 6);
        textured(g, SPARK, x, sparkY, 3.5F, 5.0F, (float)Math.PI, 0xC4C8D8,
                0.72F * Mth.sin(phase * Mth.PI));
    }

    public static void plate(GuiGraphics g, int x, int y, int width, int height, int rgb) {
        g.fill(x, y, x + width, y + height, 0xB0101218);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0x164E5464);
    }

    public static void button(GuiGraphics g, int x, int y, int width, int height,
                              boolean hovered, boolean active, int accent) {
        int fill = !active ? 0xFF1A1D25 : hovered ? 0xFF353946 : 0xFF272B36;
        g.fill(x, y, x + width, y + height, fill);
        g.renderOutline(x, y, width, height, !active ? SLOT_EDGE : hovered ? ACCENT : 0xFF666777);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, active ? 0x125E6173 : 0x085E6173);
    }

    public static void progressArrow(GuiGraphics g, ArcaneLayout.Box b, int left, int top, float progress, boolean active) {
        progressArrow(g, left + b.x(), top + b.y(), b.width(), b.height(), progress,
                PROGRESS_FROM, PROGRESS_TO, active);
    }

    public static void progressArrow(GuiGraphics g, int x, int y, int width, int height,
                                     float progress, int from, int to, boolean active) {
        // 偶数高度的轨道以槽位几何中心为轴，箭头不会向下多占一行。
        int right = x + width - 5, mid = y + height / 2;
        g.fill(x, mid - 1, right, mid + 1, TRACK);
        int filled = Math.round(Math.max(0, right - x) * ArcaneMotion.clamp(progress));
        if (filled > 0) g.fillGradient(x, mid - 1, x + filled, mid + 1, from, to);
        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(right, mid, 0); pose.scale(0.25F, 0.25F, 1);
            for (int i = 0; i < 20; i++) {
                int half = Math.max(1, Math.round((20 - i) * 0.6F));
                g.fill(i, -half, i + 1, half, active ? 0xBBA8A0BA : 0xFF4D5262);
            }
        } finally { pose.popPose(); }
    }
}
