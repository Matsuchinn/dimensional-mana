package cn.blockforge.generated.minecraft1211n422a;

/**
 * 客户端记住的「我身上叠了多少层魔蚀」。
 *
 * <p>只用于状态图标的悬停说明（层数 / 阶层 / 还差几层），不参与任何伤害计算：
 * 真正的跳伤永远由服务端按 {@link MagicErosionEffect#damageOf(int)} 算。
 * 服务端每次层数变化都会发一份过来，这里不做任何推断。</p>
 */
public final class ClientErosionState {
    private static int layers;

    private ClientErosionState() { }

    public static void set(int value) {
        layers = Math.max(0, Math.min(MagicErosionEffect.MAX_LAYERS, value));
    }

    /** 最近一次同步下来的层数；还没收到过就是 0。 */
    public static int layers() {
        return layers;
    }

    /**
     * 悬停说明要显示的层数：服务端同步值和「状态等级至少能推出的层数」取大的那个。
     *
     * <p>这样即使同步包还没到（刚上线、刚被网咬到的那一瞬间），也不会显示成 1 层。</p>
     */
    public static int layersFor(int amplifier) {
        return Math.max(layers, MagicErosionEffect.layersFromAmplifier(amplifier));
    }
}
