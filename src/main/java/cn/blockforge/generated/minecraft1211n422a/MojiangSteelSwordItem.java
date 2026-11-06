package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 魔精钢剑。
 *
 * <p>除了正常的剑类伤害，它还是一把「清障工具」：树叶、南瓜、西瓜、垂滴叶、紫颂、
 * 可可果、地衣、苔藓和藤蔓这类植物按 6.0 的速度挖，蜘蛛网 20.0，
 * 竹子与竹笋直接当成无限速度（一碰就断）。其余方块仍按原版剑的 1.0 处理。</p>
 */
public final class MojiangSteelSwordItem extends SwordItem {
    public MojiangSteelSwordItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (isPlant(state)) return 6.0F;
        if (state.is(Blocks.COBWEB)) return 20.0F;
        if (state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING)) return 1_000_000.0F;
        return super.getDestroySpeed(stack, state);
    }

    private static boolean isPlant(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.JACK_O_LANTERN)
                || state.is(Blocks.MELON)
                || state.is(Blocks.BIG_DRIPLEAF)
                || state.is(Blocks.CHORUS_FLOWER)
                || state.is(Blocks.CHORUS_PLANT)
                || state.is(Blocks.COCOA)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.VINE);
    }
}
