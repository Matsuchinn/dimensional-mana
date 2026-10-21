package cn.blockforge.generated.minecraft1211n422a;

/** 客户端保存的共鸣核心状态；HUD 每帧从这里读取。 */
public final class ClientResonanceCoreState {
    private static double charge;
    private static String element = "";
    private static int resonanceTicks;
    private static boolean ready;
    private static boolean equipped;

    public static void update(ResonanceCoreSyncPayload payload) {
        charge = payload.charge();
        element = payload.element() == null ? "" : payload.element();
        resonanceTicks = payload.resonanceTicks();
        ready = payload.ready();
        equipped = payload.equipped();
    }

    public static void clear() {
        charge = 0.0;
        element = "";
        resonanceTicks = 0;
        ready = false;
        equipped = false;
    }

    public static double charge() { return charge; }
    public static String element() { return element; }
    public static int resonanceTicks() { return resonanceTicks; }
    public static boolean ready() { return ready; }
    public static boolean equipped() { return equipped; }
    public static double ratio() { return Math.max(0.0, Math.min(1.0, charge / ResonanceCore.MAX_CHARGE)); }

    private ClientResonanceCoreState() { }
}
