package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.level.block.Block;

/**
 * 蚀脉石：荒原地下的主力岩石，石纹里渗着暗紫色的魔素脉络。
 *
 * <p>掉落走的是 1.21 的数据驱动战利品表（data/dimensional_mana/loot_table/blocks/erosion_stone.json）：
 * 普通挖掘掉<b>蚀脉圆石</b>，精准采集掉本体，两种情况都额外有 1% 概率崩出一枚<b>畸变魔晶片</b>。
 * 1.21 已没有 {@code getDrops} 运行时覆写点，战利品表就是它的等价实现。</p>
 */
public class ErosionStoneBlock extends Block {
    public ErosionStoneBlock(Properties properties) {
        super(properties);
    }
}
