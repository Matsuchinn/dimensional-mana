package cn.blockforge.generated.minecraft1211n422a;

/**
 * 投射类法术的射程与画面飞行速度。
 *
 * <p>服务端判定、客户端动画和命中声延迟都读这一张表，三边不会各写一个数字：
 * 以前射程抬到八个区块以后，客户端还按旧射程的四刻/十六格算飞行，声音也在第四刻就响，
 * 于是「远处那一发」在画面里要么一闪而过，要么整包被当成异常坐标丢掉。</p>
 */
final class SpellRange {
    /** 基础投射法术的射程：八个区块。 */
    static final double BASIC = 8 * 16;
    /** 中级投射法术的射程：不少于五个区块。 */
    static final double INTERMEDIATE = 5 * 16;
    /**
     * 投射物在画面里的飞行速度，单位是格/刻（乘 20 就是每秒格数）。
     *
     * <p>原来基础投射按十六格一刻飞，一百多格只飞半秒，看上去像瞬移；
     * 现在放缓到两格一刻（每秒四十格），一百二十八格约三点二秒，能看清轨迹。</p>
     */
    static final double BASIC_SPEED = 2.0;
    /** 弹头抵达后，爆散与残影在终点继续播放的刻数。 */
    static final int AFTERMATH = 22;
    /** 基础投射至少播放的刻数，近距离也留出完整的命中演出。 */
    static final int MIN_VISUAL_TICKS = 24;
    /**
     * 画面允许的最长起止距离。
     *
     * <p>只用来挡住真正异常的坐标（整个区块坐标出错、NaN 之类），
     * 不是射程上限；射程以内再远的一发都应该照常画出来。</p>
     */
    static final double MAX_VISUAL_SPAN = 256.0;

    /** 一段航程在画面里要飞几刻，与客户端 {@code ClientSpellSprites.flightTicks} 同一公式。 */
    static int flightTicks(double distance) {
        return (int)Math.ceil(Math.clamp(distance, 0, MAX_VISUAL_SPAN) / BASIC_SPEED);
    }

    /** 发射视觉的总时长：飞过去的时间 + 终点余韵，至少播放 {@link #MIN_VISUAL_TICKS} 刻。 */
    static int visualTicks(double distance) {
        return Math.max(MIN_VISUAL_TICKS, flightTicks(distance) + AFTERMATH);
    }

    private SpellRange() { }
}
