package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;

public final class ClientStelePayloadHandler {
    public static void openOrUpdate(OpenSteleResearchPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SteleResearchScreen screen && screen.isFor(payload.pos())) {
            screen.update(payload);
        } else {
            minecraft.setScreen(new SteleResearchScreen(payload));
        }
    }

    private ClientStelePayloadHandler() { }
}
