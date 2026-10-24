package cn.blockforge.generated.minecraft1211n422a;

public final class ClientManaState {
    private static double mana;
    private static double maxMana = 100;
    private static int battleQi;
    private static int maxBattleQi = 100;
    private static boolean exhausted;
    private static double overchargeSafeRatio = 1.30;

    public static void update(ManaSyncPayload payload) {
        mana = payload.mana();
        maxMana = payload.maxMana();
        battleQi = payload.battleQi();
        maxBattleQi = payload.maxBattleQi();
        exhausted = payload.exhausted();
        overchargeSafeRatio = payload.overchargeSafeRatio();
    }

    public static double mana() { return mana; }
    public static double maxMana() { return maxMana; }
    public static int battleQi() { return battleQi; }
    public static int maxBattleQi() { return maxBattleQi; }
    public static boolean exhausted() { return exhausted; }
    public static double overchargeSafeRatio() { return overchargeSafeRatio; }

    private ClientManaState() {}
}
