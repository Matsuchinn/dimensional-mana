package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 进度页背后的「呼吸法阵」。
 *
 * <p>原版进度页只有一张 16×16 的平铺底图，还是静态的，所以这里换了个画法：
 * 底图换成半透明的石墨底（{@code textures/gui/advancement_background.png}），
 * 再在 {@link ScreenEvent.BackgroundRendered}（底图铺完、窗口和节点还没画的那一刻）
 * 往下面垫几枚大小、转向、转速都不一样的法阵。半透明底图把它们压暗一层，
 * 于是法阵看起来是「长在墙里」的，不会盖住图标和文字。</p>
 *
 * <p>图案全部来自时间轴公式，不累加帧状态，所以掉帧、暂停、切窗口都不会跑偏；
 * 大小和转速各写一份常量，想加一枚环只要往 {@link #RINGS} 里再添一行。</p>
 */
final class AdvancementBackground {
    /** 一圈法阵：中心（按窗口内区比例）、直径（像素）、角速度（弧度/秒，负数是反向）、呼吸频率、相位、配色。 */
    private record Ring(float cx, float cy, float size, float spin, float breathSpeed, float phase,
                        int rgb, float alpha) { }

    /** 五枚：大的压场、小的点缀，三个顺时针两个逆时针，呼吸节奏彼此错开。 */
    private static final Ring[] RINGS = {
            new Ring(0.16F, 0.22F, 232.0F, 0.13F, 0.42F, 0.00F, 0xBFC7DA, 0.78F),
            new Ring(0.84F, 0.18F, 168.0F, -0.21F, 0.61F, 1.90F, 0xA9BCD2, 0.70F),
            new Ring(0.50F, 0.86F, 268.0F, 0.09F, 0.33F, 3.40F, 0xB7C3DC, 0.63F),
            new Ring(0.27F, 0.72F, 132.0F, -0.34F, 0.78F, 0.70F, 0x8FD8FF, 0.85F),
            new Ring(0.70F, 0.58F, 184.0F, 0.24F, 0.52F, 2.40F, 0xC6BEE0, 0.67F),
    };

    /** 像素坐标直接照抄原版 AdvancementScreen 的常量，窗口尺寸变了这里要跟着改。 */
    private static final int WINDOW_WIDTH = 252;
    private static final int WINDOW_HEIGHT = 140;
    private static final int INSIDE_X = 9;
    private static final int INSIDE_Y = 18;
    private static final int INSIDE_WIDTH = 234;
    private static final int INSIDE_HEIGHT = 113;

    static void onBackgroundRendered(ScreenEvent.BackgroundRendered event) {
        if (!(event.getScreen() instanceof AdvancementsScreen screen)) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int left = (screen.width - WINDOW_WIDTH) / 2 + INSIDE_X;
        int top = (screen.height - WINDOW_HEIGHT) / 2 + INSIDE_Y;
        // 只看得到窗口内区，其余地方被窗口边框压住，所以先裁一刀省得算漏。
        graphics.enableScissor(left, top, left + INSIDE_WIDTH, top + INSIDE_HEIGHT);
        try {
            double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
            for (Ring ring : RINGS) {
                draw(graphics, ring, left, top, seconds);
            }
        } finally {
            graphics.disableScissor();
            // ArcaneGui 画完会把混合关掉（它自己的界面随后会重开）；原版进度页后面还要画
            // 半透明的窗口与节点，这里必须把状态还回去，否则那些地方会变成不透明。
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void draw(GuiGraphics graphics, Ring ring, int left, int top, double seconds) {
        // 呼吸：0.93～1.07 倍大小来回，透明度同步起伏，看起来像「吸气、呼气」。
        float breath = (float) Math.sin(seconds * ring.breathSpeed() + ring.phase());
        float size = ring.size() * (1.0F + 0.07F * breath);
        float alpha = ring.alpha() * (0.78F + 0.22F * breath);
        float cx = left + INSIDE_WIDTH * ring.cx();
        float cy = top + INSIDE_HEIGHT * ring.cy();
        float angle = (float) (seconds * ring.spin() + ring.phase());
        // 外环顺时针、内芯反向慢转：两层叠在一起才不会像一张图在原地打转。
        ArcaneGui.textured(graphics, ArcaneGui.SIGIL_RING, cx, cy, size, size, angle, ring.rgb(), alpha);
        ArcaneGui.textured(graphics, ArcaneGui.SIGIL_CORE, cx, cy, size * 0.74F, size * 0.74F,
                -angle * 0.8F, ring.rgb(), alpha * 0.62F);
    }

    private AdvancementBackground() { }
}
