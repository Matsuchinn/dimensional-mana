package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * 模组列表里点「配置」先落在这里：想调 HUD 就进可视化编辑器，
 * 想调数值（魔素、战斗、暴击、刷怪……）就进 NeoForge 的标准配置界面。
 *
 * <p>r295 之前这个入口被 HUD 编辑器独占，玩家在游戏里再也翻不到别的配置文件，
 * 所以把「模组配置文件」这一项加回来——列出的就是 {@code config/} 下本模组的全部 toml。</p>
 */
public final class ModConfigHubScreen extends Screen {
    private static final int BUTTON_WIDTH = 240;

    private final ModContainer mod;
    private final Screen parent;

    public ModConfigHubScreen(ModContainer mod, Screen parent) {
        super(Component.translatable("config.dimensional_mana.hub.title"));
        this.mod = mod;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = 58;
        addRenderableWidget(Button.builder(Component.translatable("config.dimensional_mana.hub.layout"),
                button -> minecraft.setScreen(new HudConfigScreen(this)))
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.translatable("config.dimensional_mana.hub.toggles"),
                button -> minecraft.setScreen(HudConfigScreen.toggles(this)))
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.translatable("config.dimensional_mana.hub.files"),
                button -> minecraft.setScreen(new ConfigurationScreen(mod, this)))
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        y += 34;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                button -> onClose())
                .bounds(x + 60, y, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        TextFit.centered(graphics, font, title, width / 2, 20, width - 20, 0xFFFFFFFF);
        TextFit.centered(graphics, font, Component.translatable("config.dimensional_mana.hub.body"),
                width / 2, 36, width - 20, 0xFFB8C8E8);
        // 提示行最长，窄窗口下按窗口宽度截断，不会画到屏幕外。
        TextFit.centered(graphics, font, Component.translatable("config.dimensional_mana.hub.files_hint"),
                width / 2, 166, width - 20, 0xFF8A96A8);
    }

    /** 从模组列表进来时不该额外暂停游戏。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
