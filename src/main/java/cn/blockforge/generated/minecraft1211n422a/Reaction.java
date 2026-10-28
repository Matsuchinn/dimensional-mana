package cn.blockforge.generated.minecraft1211n422a;

import java.util.List;

/** 元素反应；元素组合无方向，多个组合同时可用时按 priorityOrder 匹配。 */
public enum Reaction {
    VAPORIZE("vaporize", 0xFF8FDBFF, Category.AMPLIFY),
    MELT("melt", 0xFFFFB45C, Category.AMPLIFY),
    OVERLOAD("overload", 0xFFFF7043, Category.DRAMATIC),
    ELECTRO_CHARGED("electro_charged", 0xFFB6A1FF, Category.DRAMATIC),
    SUPERCONDUCT("superconduct", 0xFFA9E8FF, Category.DRAMATIC),
    CATALYZE("catalyze", 0xFFB7F45D, Category.DRAMATIC),
    FREEZE("freeze", 0xFFD8FAFF, Category.CONTROL),
    CHARGE("charge", 0xFFE46CFF, Category.CHARGE),
    BURNING("burning", 0xFFFF7B36, Category.SUSTAIN),
    CORRUPTION("corruption", 0xFFA3C63A, Category.SUSTAIN),
    SWIRL("swirl", 0xFFA8FFE1, Category.DIFFUSE),
    MIRE("mire", 0xFF8A6B4D, Category.SECONDARY),
    SPREAD("spread", 0xFF75D867, Category.SECONDARY),
    SMELT("smelt", 0xFFFFC25C, Category.SECONDARY),
    PURIFY("purify", 0xFFFFF2A6, Category.SECONDARY),
    SHOCKWAVE("shockwave", 0xFFEFB1FF, Category.SECONDARY),
    BLIND("blind", 0xFFFFFFC9, Category.SECONDARY);

    /** 反应类别，对应设计规范 v2.0 的七档。 */
    public enum Category {
        /** 增幅：蒸发、融化。 */
        AMPLIFY("增幅", 2),
        /** 剧变：超载、感电、超导、激发。 */
        DRAMATIC("剧变", 3),
        /** 控制：冻结。 */
        CONTROL("控制", 1),
        /** 充能：充能。 */
        CHARGE("充能", 1),
        /** 持续：燃烧、腐化。 */
        SUSTAIN("持续", 1),
        /** 扩散：扩散。 */
        DIFFUSE("扩散", 1),
        /** 次级：泥沼、蔓延、熔炼、净化、震荡、致盲。 */
        SECONDARY("次级", 1);

        private final String fallbackName;
        private final int disorder;

        Category(String fallbackName, int disorder) {
            this.fallbackName = fallbackName;
            this.disorder = disorder;
        }

        public String fallbackName() { return fallbackName; }

        /** 该类别每一对元素反应贡献的紊乱值（增幅 2、剧变 3、其余 1）。 */
        public int disorder() { return disorder; }
    }

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
    private final Category category;

    Reaction(String id, int color, Category category) {
        this.id = id;
        this.color = color;
        this.category = category;
    }

    public String id() { return id; }
    public int color() { return color; }
    public Category category() { return category; }

    /** 法阵紊乱值：增幅 2、剧变 3、其余类别 1。 */
    public int disorder() { return category.disorder(); }

    /** 增幅与剧变两档，核心「增强 / 超频」模式会对它们额外加紊乱。 */
    public boolean isVolatile() { return category == Category.AMPLIFY || category == Category.DRAMATIC; }

    public String translationKey() { return "reaction.dimensional_mana." + id; }
    public static List<Reaction> priorityOrder() { return PRIORITY_ORDER; }
}
