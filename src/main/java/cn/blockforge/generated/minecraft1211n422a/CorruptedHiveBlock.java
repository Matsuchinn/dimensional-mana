package cn.blockforge.generated.minecraft1211n422a;

/**
 * 玩家制作的秽蚀蜂箱：木板围出来的紫黑蜂房，机制与秽蚀蜂巢完全一致。
 *
 * <p>直接继承 {@link CorruptedNestBlock}：「蜜满剪脾掉魔蚀蜜脾」「蜜满装瓶出魔蚀蜂蜜」
 * 两条交互原样拿过来；装蜂、酿蜜、烟熏、放蜂这些本来就是原版蜂箱机制。
 * 单独一个类是为了让方块 id、模型和创造页条目各自独立。</p>
 */
public final class CorruptedHiveBlock extends CorruptedNestBlock {
    public CorruptedHiveBlock(Properties properties) {
        super(properties);
    }
}
