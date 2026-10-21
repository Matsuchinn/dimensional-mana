package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;

public final class ClientCodexPayloadHandler {
    public static void openGuide(OpenCodexGuidePayload payload) {
        if (!payload.open()) return;
        Minecraft.getInstance().setScreen(ClientElementTalentState.chosen()
                ? new ArcaneCodexGuideScreen() : new ElementTalentScreen(true));
    }

    private ClientCodexPayloadHandler() { }
}
