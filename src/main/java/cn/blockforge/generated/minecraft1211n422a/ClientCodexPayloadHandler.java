package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;

public final class ClientCodexPayloadHandler {
    public static void openGuide(OpenCodexGuidePayload payload) {
        if (!payload.open()) return;
        Minecraft.getInstance().setScreen(ClientElementTalentState.chosen()
                ? new ArcaneCodexGuideScreen() : new ElementTalentScreen(true));
    }

    /** 战斗手册：右键直接翻到斗气界面。 */
    public static void openCombatCodex(OpenCombatCodexPayload payload) {
        if (!payload.open()) return;
        Minecraft.getInstance().setScreen(new CombatCodexScreen());
    }

    private ClientCodexPayloadHandler() { }
}
