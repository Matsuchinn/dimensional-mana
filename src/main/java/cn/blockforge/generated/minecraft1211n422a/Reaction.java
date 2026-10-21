package cn.blockforge.generated.minecraft1211n422a;

import java.util.List;

/** 元素反应；元素组合无方向，多个组合同时可用时按 priorityOrder 匹配。 */
public enum Reaction {
    VAPORIZE("vaporize", 0xFF8FDBFF),
    MELT("melt", 0xFFFFB45C),
    OVERLOAD("overload", 0xFFFF7043),
    ELECTRO_CHARGED("electro_charged", 0xFFB6A1FF),
    SUPERCONDUCT("superconduct", 0xFFA9E8FF),
    CATALYZE("catalyze", 0xFFB7F45D),
    FREEZE("freeze", 0xFFD8FAFF),
    CHARGE("charge", 0xFFE46CFF),
    BURNING("burning", 0xFFFF7B36),
    CORRUPTION("corruption", 0xFFA3C63A),
    SWIRL("swirl", 0xFFA8FFE1),
    MIRE("mire", 0xFF8A6B4D),
    SPREAD("spread", 0xFF75D867),
    SMELT("smelt", 0xFFFFC25C),
    PURIFY("purify", 0xFFFFF2A6),
    SHOCKWAVE("shockwave", 0xFFEFB1FF),
    BLIND("blind", 0xFFFFFFC9);

    /**
     * 用户指定的反应优先级：增幅、剧变、控制、充能、持续、扩散、次级。
     * 同一类别内部保持需求原文中的排列顺序。
     */
    private static final List<Reaction> PRIORITY_ORDER = List.of(
            VAPORIZE, MELT,
            OVERLOAD, ELECTRO_CHARGED, SUPERCONDUCT, CATALYZE,
            FREEZE,
            CHARGE,
            BURNING, CORRUPTION,
            SWIRL,
            MIRE, SPREAD, SMELT, PURIFY, SHOCKWAVE, BLIND
    );

    private final String id;
    private final int color;

    Reaction(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() { return id; }
    public int color() { return color; }
    public String translationKey() { return "reaction.dimensional_mana." + id; }
    public static List<Reaction> priorityOrder() { return PRIORITY_ORDER; }
}
