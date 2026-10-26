package cn.blockforge.generated.minecraft1211n422a;

/**
 * 13 类功能块的基础数值，直接对应设计文档第 6.1 节。
 *
 * <p>启动 / 维持 / 触发三项是「未乘粉末系数」的基础消耗，复杂度用于界面显示与
 * 蓝图容量判断。{@code CORE}、{@code TRIGGER}、{@code OUTPUT} 三类还是一个可运行
 * 网络的最低要求。</p>
 */
public enum MagicCircleBlockType {
    CORE("core", 20, 0, 0, 5, "核心"),
    SUPPLY("supply", 6, 1, 0, 3, "供能"),
    TRIGGER("trigger", 8, 0, 0, 2, "触发"),
    OUTPUT("output", 12, 0, 12, 4, "输出"),
    AREA("area", 6, 2, 0, 2, "范围"),
    DURATION("duration", 6, 2, 0, 2, "持续"),
    CONDITION("condition", 4, 0, 0, 1, "条件"),
    FILTER("filter", 4, 0, 0, 1, "过滤"),
    AMPLIFY("amplify", 10, 1, 0, 3, "增幅"),
    LINK("link", 2, 0, 0, 1, "连接"),
    STABILIZE("stabilize", 8, 0, 0, 2, "稳定"),
    STORAGE("storage", 10, 0, 0, 3, "存储"),
    DIRECTION("direction", 1, 0, 0, 1, "方向");

    private final String id;
    private final int startCost;
    private final int upkeepCost;
    private final int triggerCost;
    private final int complexity;
    private final String fallbackName;

    MagicCircleBlockType(String id, int startCost, int upkeepCost, int triggerCost, int complexity,
                         String fallbackName) {
        this.id = id;
        this.startCost = startCost;
        this.upkeepCost = upkeepCost;
        this.triggerCost = triggerCost;
        this.complexity = complexity;
        this.fallbackName = fallbackName;
    }

    public String id() { return id; }
    public int startCost() { return startCost; }
    public int upkeepCost() { return upkeepCost; }
    public int triggerCost() { return triggerCost; }
    public int complexity() { return complexity; }
    public String fallbackName() { return fallbackName; }
    public String translationKey() { return "gui.dimensional_mana.magiccircle.block." + id; }

    public boolean isCore() { return this == CORE; }
    public boolean isTrigger() { return this == TRIGGER; }
    public boolean isOutput() { return this == OUTPUT; }

    public static MagicCircleBlockType byId(String id) {
        for (MagicCircleBlockType type : values()) if (type.id.equals(id)) return type;
        return null;
    }
}
