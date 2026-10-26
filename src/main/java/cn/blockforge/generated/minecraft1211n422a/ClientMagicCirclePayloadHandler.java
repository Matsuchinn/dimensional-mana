package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;

/** 把服务端同步的绘制台状态交给界面；界面已经开着同一个阵盘时就地刷新。 */
public final class ClientMagicCirclePayloadHandler {
    public static void openOrUpdate(MagicCircleSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MagicCircleDrawingScreen screen
                && screen.networkId().equals(payload.networkId())) {
            screen.update(payload);
        } else {
            minecraft.setScreen(new MagicCircleDrawingScreen(payload));
        }
    }

    private ClientMagicCirclePayloadHandler() {}

    /**
     * 法阵码导入的结果到了：面板还开着就显示在面板里（失败时面板留着、码不清，换载体再按回车）；
     * 面板已经被玩家关掉，就把这句话打进聊天栏，别让结果凭空消失。
     */
    public static void codeResult(MagicCircleCodeResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MagicCircleCodeScreen screen) {
            screen.onResult(payload.ok(), payload.message());
            return;
        }
        if (minecraft.player != null) minecraft.player.displayClientMessage(payload.message(), false);
    }

    /** 客户端刚请求过「打开轮盘」：清单到了才自动弹窗，免得玩家关了之后又被硬弹回来。 */
    private static boolean pendingRemoteOpen;

    /** 调试棒那边请求打开轮盘时先置位，等服务端的清单回来再真正开界面。 */
    static void expectRemoteScreen() {
        pendingRemoteOpen = true;
    }

    /** 退出世界时清掉「等清单开轮盘」的标记，免得下次进服被一个迟到的包弹出界面。 */
    static void clearPendingRemote() {
        pendingRemoteOpen = false;
    }

    /**
     * 远程法阵轮盘的清单到了：轮盘已经开着就地刷新（点一个开关后服务端会重发一份），
     * 还没开、而且确实是玩家刚请求过的时候才顺手打开。
     */
    public static void openRemote(MagicCircleRemoteListPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MagicCircleRemoteScreen screen) {
            screen.update(payload);
            return;
        }
        if (!pendingRemoteOpen) return;
        pendingRemoteOpen = false;
        minecraft.setScreen(new MagicCircleRemoteScreen(payload));
    }
}
