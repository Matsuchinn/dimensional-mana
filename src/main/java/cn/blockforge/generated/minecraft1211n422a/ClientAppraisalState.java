package cn.blockforge.generated.minecraft1211n422a;

public final class ClientAppraisalState {
    private static boolean glassesActive;
    private static double concentration;
    private static int entityId = -1;
    private static String targetName = "";
    private static double health;
    private static double maxHealth;
    private static double mana;
    private static double maxMana;
    private static int aura;
    private static int maxAura;

    public static void update(AppraisalSyncPayload payload) {
        glassesActive = payload.glassesActive();
        concentration = payload.concentration();
        entityId = payload.entityId();
        targetName = payload.targetName();
        health = payload.health();
        maxHealth = payload.maxHealth();
        mana = payload.mana();
        maxMana = payload.maxMana();
        aura = payload.aura();
        maxAura = payload.maxAura();
    }

    public static boolean glassesActive() { return glassesActive; }
    public static double concentration() { return concentration; }
    public static int entityId() { return entityId; }
    public static String targetName() { return targetName; }
    public static double health() { return health; }
    public static double maxHealth() { return maxHealth; }
    public static double mana() { return mana; }
    public static double maxMana() { return maxMana; }
    public static int aura() { return aura; }
    public static int maxAura() { return maxAura; }

    private ClientAppraisalState() {}
}
