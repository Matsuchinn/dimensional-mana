package cn.blockforge.generated.minecraft1211n422a;

/**
 * 客户端缓存的饰品显示状态：大师施法手套的施法计数、魔法大师披风的凝聚球开关。
 * 由 {@link MagicToggleSyncPayload} 更新，只用于物品描述里的那两行字。
 */
final class ClientMagicToggleState {
    private static int casts;
    private static boolean orbEnabled = true;

    static void update(int castCount, boolean enabled) {
        casts = Math.max(0, Math.min(CastingGloveItem.CASTS_PER_GUARANTEE - 1, castCount));
        orbEnabled = enabled;
    }

    static int casts() {
        return casts;
    }

    static boolean orbEnabled() {
        return orbEnabled;
    }

    static void clear() {
        casts = 0;
        orbEnabled = true;
    }

    private ClientMagicToggleState() { }
}
