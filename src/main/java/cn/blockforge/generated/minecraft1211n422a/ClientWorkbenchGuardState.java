package cn.blockforge.generated.minecraft1211n422a;

/**
 * 客户端记住的「武备加工台防手滑保护是否生效」。
 *
 * <p>只跟当前打开的那个菜单走：{@code containerId} 对不上就当没有保护（换了界面、
 * 关了界面都不会把旧状态带过去）。服务端随时会覆盖它，所以这里不做任何推断。</p>
 */
public final class ClientWorkbenchGuardState {
    private static int containerId = -1;
    private static boolean active;

    private ClientWorkbenchGuardState() { }

    public static void set(int id, boolean value) {
        containerId = id;
        active = value;
    }

    public static boolean active(int id) {
        return containerId == id && active;
    }

    /** 打开新界面时先清掉，避免上一台的保护状态闪一下。 */
    public static void clear() {
        containerId = -1;
        active = false;
    }
}
