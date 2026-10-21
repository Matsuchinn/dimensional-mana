package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 漂浮未知符号的石碑研读界面。 */
public final class SteleResearchScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 238;
    private static final String[] RUNES = {
            "ᚠ ᛉ ᚱ ᛟ ᚾ", "ᛞ ᚲ ᛇ ᛒ", "ᛏ ᛃ ᚹ ᛗ ᛝ", "ᚦ ᛚ ᛜ ᛈ"
    };

    private final BlockPos pos;
    private int uses;
    private int manaCost;
    private int cooldownTicks;
    private String statusKey;
    private String rewardName;
    private int rewardCount;
    private int left;
    private int top;
    private int animationTicks;
    private boolean awaitingResponse;
    private Button researchButton;

    public SteleResearchScreen(OpenSteleResearchPayload payload) {
        super(Component.translatable("gui.dimensional_mana.stele.title"));
        this.pos = payload.pos();
        apply(payload);
    }

    public boolean isFor(BlockPos otherPos) {
        return pos.equals(otherPos);
    }

    public void update(OpenSteleResearchPayload payload) {
        apply(payload);
        awaitingResponse = false;
        refreshButton();
    }

    private void apply(OpenSteleResearchPayload payload) {
        uses = payload.uses();
        manaCost = payload.manaCost();
        cooldownTicks = payload.cooldownTicks();
        statusKey = payload.statusKey();
        rewardName = payload.rewardName();
        rewardCount = payload.rewardCount();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        clearWidgets();
        researchButton = Button.builder(Component.translatable("gui.dimensional_mana.stele.research"), button -> research())
                .bounds(left + 120, top + PANEL_HEIGHT - 34, 120, 20).build();
        addRenderableWidget(researchButton);
        refreshButton();
    }

    private void research() {
        if (awaitingResponse || cooldownTicks > 0 || uses >= SteleResearchData.MAX_DAILY_USES) return;
        awaitingResponse = true;
        refreshButton();
        PacketDistributor.sendToServer(new SteleResearchActionPayload(pos));
    }

    @Override
    public void tick() {
        animationTicks++;
        if (cooldownTicks > 0) cooldownTicks--;
        refreshButton();
    }

    private void refreshButton() {
        if (researchButton == null) return;
        researchButton.active = !awaitingResponse && cooldownTicks <= 0 && uses < SteleResearchData.MAX_DAILY_USES;
        if (awaitingResponse) {
            researchButton.setMessage(Component.translatable("gui.dimensional_mana.stele.reading"));
        } else if (cooldownTicks > 0) {
            researchButton.setMessage(Component.translatable("gui.dimensional_mana.stele.cooldown_button",
                    Math.max(1, (cooldownTicks + 19) / 20)));
        } else if (uses >= SteleResearchData.MAX_DAILY_USES) {
            researchButton.setMessage(Component.translatable("gui.dimensional_mana.stele.exhausted"));
        } else {
            researchButton.setMessage(Component.translatable("gui.dimensional_mana.stele.research"));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0030209);
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0140C24, 0xF0021520);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF9E70D1);
        graphics.renderOutline(left + 5, top + 5, PANEL_WIDTH - 10, PANEL_HEIGHT - 10, 0xFF3F7B89);

        graphics.drawCenteredString(font, title, width / 2, top + 13, 0xFFFFE4A8);
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.stele.subtitle"),
                width / 2, top + 29, 0xFFA78CC2);

        for (int i = 0; i < RUNES.length; i++) {
            double wave = Math.sin((animationTicks + partialTick) * 0.065 + i * 1.7);
            int x = left + 48 + i * 72 + (int)Math.round(wave * 7.0);
            int y = top + 62 + (i % 2) * 42 + (int)Math.round(Math.cos((animationTicks + partialTick) * 0.05 + i) * 8.0);
            int alpha = 150 + (int)Math.round((wave + 1.0) * 35.0);
            int color = (Math.min(255, alpha) << 24) | (i % 2 == 0 ? 0x8EDCFF : 0xD79AFF);
            graphics.drawCenteredString(font, RUNES[i], x, y, color);
        }

        int infoY = top + 137;
        graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.stele.uses",
                uses, SteleResearchData.MAX_DAILY_USES), width / 2, infoY, 0xFFD9C6E9);
        if (uses < SteleResearchData.MAX_DAILY_USES) {
            graphics.drawCenteredString(font, Component.translatable("gui.dimensional_mana.stele.cost", manaCost),
                    width / 2, infoY + 14, 0xFF78D8FF);
        }
        if (statusKey != null && !statusKey.isBlank()) {
            Component status = statusComponent();
            int statusColor = statusKey.endsWith("success") ? 0xFF8FFFC1 : 0xFFFF9D9D;
            graphics.drawCenteredString(font, status, width / 2, infoY + 32, statusColor);
        }
    }

    private Component statusComponent() {
        if (statusKey.endsWith("success")) {
            return Component.translatable(statusKey, rewardName, rewardCount);
        }
        if (statusKey.endsWith("cooldown")) {
            return Component.translatable(statusKey, Math.max(1, (cooldownTicks + 19) / 20));
        }
        if (statusKey.endsWith("no_mana")) return Component.translatable(statusKey, manaCost);
        return Component.translatable(statusKey);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
