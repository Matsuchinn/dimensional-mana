package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 魔蚀凋零骷髅头（方块形态）。
 *
 * <p>它不是原版那种带方块实体的头颅方块，而是普通方块：外观由一个手写的骷髅模型 JSON 决定。
 * 这么做有两个好处——</p>
 * <ul>
 *   <li>不会像原版凋零骷髅头那样一摆成 T 字形就自己召唤凋零，玩家摆出来的永远是魔蚀凋零；</li>
 *   <li>不需要客户端加载骷髅方块实体渲染器，存档里也不会残留玩家头颅数据。</li>
 * </ul>
 *
 * <p>三个一起摆在灵魂沙上、中间那块灵魂沙换成特级魔晶块，就会触发
 * {@link CorruptedWitherEvents} 里的召唤仪式。</p>
 */
public final class CorruptedWitherSkullBlock extends Block {
    public static final String BLOCK_ID = "corrupted_wither_skull";

    public CorruptedWitherSkullBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
