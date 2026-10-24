package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayList;
import java.util.List;

/**
 * 专业鉴定眼镜在客户端的全部状态。
 *
 * <p>服务端每几刻推一次解析结果，这里除了原样存下来，还会把每次的
 * 生命 / 魔素 / 斗气比例压进一条历史曲线，<b>扫描线小窗</b>画的就是这条曲线：
 * 右侧竖线是「当前时刻」，越往左越旧，能直观看出目标的资源是在回还是在掉。</p>
 */
public final class ClientProAppraisalState {
    /** 扫描小窗里最多留多少个采样点：按 0.25 秒一次算，正好是最近 15 秒。 */
    public static final int SCANLINE_SAMPLES = 60;

    private static boolean equipped;
    private static boolean active;
    private static double playerMana;
    private static int entityId = -1;
    private static String targetName = "";
    private static float health;
    private static float maxHealth;
    private static double mana;
    private static double maxMana;
    private static int battleQi;
    private static int maxBattleQi;
    private static double manaRegen;
    private static double battleQiRegen;
    private static List<String> resistances = List.of();
    private static List<String> weaknesses = List.of();
    private static String skillId = "";
    private static int skillTotalTicks;
    private static int skillRemainingTicks;

    /** 每条采样是 {生命比例, 魔素比例, 斗气比例}，取值 0~1。 */
    private static final List<float[]> HISTORY = new ArrayList<>();
    /** 扫描线头部在 0~1 之间循环，纯客户端动画，不跟服务端对表。 */
    private static float scanlineCursor;

    public static void update(ProAppraisalPayload payload) {
        equipped = payload.equipped();
        active = payload.active();
        playerMana = payload.playerMana();
        health = payload.health();
        maxHealth = payload.maxHealth();
        mana = payload.mana();
        maxMana = payload.maxMana();
        battleQi = payload.battleQi();
        maxBattleQi = payload.maxBattleQi();
        manaRegen = payload.manaRegen();
        battleQiRegen = payload.battleQiRegen();
        resistances = payload.resistances();
        weaknesses = payload.weaknesses();
        skillId = payload.skillId();
        skillTotalTicks = payload.skillTotalTicks();
        skillRemainingTicks = payload.skillRemainingTicks();

        if (!equipped) {
            clear();
            return;
        }
        if (payload.entityId() != entityId) {
            HISTORY.clear();
            entityId = payload.entityId();
        }
        targetName = payload.targetName();
        if (payload.entityId() >= 0 && payload.active()) {
            HISTORY.add(new float[]{
                    ratio(health, maxHealth),
                    ratio(mana, maxMana),
                    ratio(battleQi, maxBattleQi)});
            while (HISTORY.size() > SCANLINE_SAMPLES) HISTORY.remove(0);
        }
    }

    public static void clear() {
        equipped = false;
        active = false;
        playerMana = 0.0;
        entityId = -1;
        targetName = "";
        health = 0.0F;
        maxHealth = 0.0F;
        mana = 0.0;
        maxMana = 0.0;
        battleQi = 0;
        maxBattleQi = 0;
        manaRegen = 0.0;
        battleQiRegen = 0.0;
        resistances = List.of();
        weaknesses = List.of();
        skillId = "";
        skillTotalTicks = 0;
        skillRemainingTicks = 0;
        HISTORY.clear();
    }

    /** 客户端每刻推进一次扫描线，速度约 1.2 秒扫完整个小窗。 */
    public static void tick() {
        scanlineCursor += 0.014F;
        if (scanlineCursor >= 1.0F) scanlineCursor -= 1.0F;
    }

    private static float ratio(double current, double max) {
        if (max <= 0.0) return 0.0F;
        return (float)Math.max(0.0, Math.min(1.0, current / max));
    }

    public static boolean equipped() { return equipped; }
    public static boolean active() { return active; }
    public static double playerMana() { return playerMana; }
    public static int entityId() { return entityId; }
    public static String targetName() { return targetName; }
    public static float health() { return health; }
    public static float maxHealth() { return maxHealth; }
    public static double mana() { return mana; }
    public static double maxMana() { return maxMana; }
    public static int battleQi() { return battleQi; }
    public static int maxBattleQi() { return maxBattleQi; }
    public static double manaRegen() { return manaRegen; }
    public static double battleQiRegen() { return battleQiRegen; }
    public static List<String> resistances() { return resistances; }
    public static List<String> weaknesses() { return weaknesses; }
    public static String skillId() { return skillId; }
    public static int skillTotalTicks() { return skillTotalTicks; }
    public static int skillRemainingTicks() { return skillRemainingTicks; }
    public static List<float[]> history() { return HISTORY; }
    public static float scanlineCursor() { return scanlineCursor; }

    private ClientProAppraisalState() {}
}
