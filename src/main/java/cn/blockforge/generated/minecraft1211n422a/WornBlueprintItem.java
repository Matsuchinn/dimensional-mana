package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.Item;

/**
 * 「残破的蓝图」：从创造物品栏 / 村子箱子 / 哥布林营地宝箱 / 冒险者交易里拿到的成品阵图。
 *
 * <p>图形来自 {@link MagicCircleBlueprints}，所以物品本身不存 NBT，抄多少次内容都一样。
 * 右键绘制台基座 = 拆解研究进画布；右键其它方块表面 = 贴面投影先看效果；
 * 右键投影锚点 = 再叠一层。</p>
 */
public final class WornBlueprintItem extends Item {
    private final String blueprintId;

    public WornBlueprintItem(Properties properties, String blueprintId) {
        super(properties);
        this.blueprintId = blueprintId;
    }

    /** 对应 {@link MagicCircleBlueprints#worn(String)} 的键，就是自己的注册 id。 */
    public String blueprintId() { return blueprintId; }
}
