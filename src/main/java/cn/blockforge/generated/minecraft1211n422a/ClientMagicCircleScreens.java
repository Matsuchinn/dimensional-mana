package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 打开两个法阵客户端的弹窗。单独放一个类，是为了让两边共用的代码（调试棒、蓝图交互）
 * 只在客户端分支里引用它，专用服务器上不会把客户端类加载进来。
 */
public final class ClientMagicCircleScreens {

    /** 法阵码导入面板。 */
    public static void openCodeImport() {
        Minecraft.getInstance().setScreen(new MagicCircleCodeScreen());
    }

    /**
     * 远程法阵轮盘：先向服务端要一份清单，包一到客户端就自动把轮盘打开
     * （见 {@link ClientMagicCirclePayloadHandler#openRemote}）。
     */
    public static void openRemoteWheel() {
        ClientMagicCirclePayloadHandler.expectRemoteScreen();
        PacketDistributor.sendToServer(MagicCircleNetActionPayload.remoteRefresh());
    }

    private ClientMagicCircleScreens() {}
}
