package cn.blockforge.generated.minecraft1211n422a;

/** 客户端侧记住“魔晶铁砧这一下要多少魔素”，只在砧台界面里用。 */
public final class ClientAnvilState {
    private static int cost;
    private static boolean affordable = true;
    private static boolean renameOnly;

    public static void update(int newCost, boolean canAfford, boolean renaming) {
        cost = newCost;
        affordable = canAfford;
        renameOnly = renaming;
    }

    /** 打开界面时先清空，避免上一次开砧台的价格残留。 */
    public static void clear() {
        cost = 0;
        affordable = true;
        renameOnly = false;
    }

    public static int cost() { return cost; }
    public static boolean affordable() { return affordable; }
    public static boolean renameOnly() { return renameOnly; }

    private ClientAnvilState() { }
}
