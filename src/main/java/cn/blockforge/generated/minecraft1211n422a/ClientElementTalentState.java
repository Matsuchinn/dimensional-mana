package cn.blockforge.generated.minecraft1211n422a;

public final class ClientElementTalentState {
    private static boolean chosen;
    private static int selectedMask;
    private static int unlockedMask;
    private static int[] invested = new int[ElementType.values().length];

    public static void update(ElementTalentSyncPayload payload) {
        chosen = payload.chosen();
        selectedMask = payload.selectedMask();
        unlockedMask = payload.unlockedMask();
        invested = new int[ElementType.values().length];
        System.arraycopy(payload.invested(), 0, invested, 0, Math.min(invested.length, payload.invested().length));
    }

    public static boolean chosen() { return chosen; }
    public static boolean selected(ElementType element) { return element != null && (selectedMask & (1 << element.ordinal())) != 0; }
    public static boolean unlocked(ElementType element) { return element == null || (unlockedMask & (1 << element.ordinal())) != 0; }
    public static boolean canAccess(SpellDefinition spell) { return spell == null || spell.element() == null || unlocked(spell.element()); }
    public static int invested(ElementType element) { return element == null ? 0 : invested[element.ordinal()]; }
    public static int fingerprint() { return (chosen ? 1 : 0) * 31 * 31 + selectedMask * 31 + unlockedMask; }

    private ClientElementTalentState() { }
}
