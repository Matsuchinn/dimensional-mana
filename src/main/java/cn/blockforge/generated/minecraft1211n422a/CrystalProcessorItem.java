package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 魔晶加工台的方块物品。
 *
 * <p>拆下来的加工台会把里面的魔素一起带走：破坏时把存量写进物品的自定义数据，
 * 重新放下时再读回来。九宫格、出货槽和燃料槽的物品不跟着走，它们在破坏瞬间就散落在原地。</p>
 */
public final class CrystalProcessorItem extends BlockItem {
    private static final String MANA_TAG = "Mana";

    public CrystalProcessorItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /** 把魔素存量写进这份掉落物。 */
    public static void storeMana(ItemStack stack, int mana) {
        if (mana <= 0) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(MANA_TAG));
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MANA_TAG, mana));
    }

    /** 读回这份物品记着的魔素；普通加工台返回 0。 */
    public static int storedMana(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        return Math.max(0, Math.min(CrystalProcessorBlockEntity.MAX_MANA, tag.getInt(MANA_TAG)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int mana = storedMana(stack);
        if (mana <= 0) return;
        tooltip.add(Component.translatable("tooltip.dimensional_mana.processor_stored_mana",
                mana, CrystalProcessorBlockEntity.MAX_MANA).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.processor_stored_hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
