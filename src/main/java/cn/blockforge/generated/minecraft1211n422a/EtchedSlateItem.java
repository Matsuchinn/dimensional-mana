package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * 刻印石板：把「已经抄录好的蓝图」烧进石头里，得到一块<b>能正式运行</b>的实体法阵锚点。
 *
 * <p>和蓝图投影的区别只有一句话：投影只能免费看、半价试；刻印石板贴出去以后能启动，
 * 启动后每 5 秒扣维持、触发时真打伤害 / 治疗 / 召唤 / 挖矿。</p>
 *
 * <p>用法：拿着它右键任意方块表面（点哪面就贴哪面，地面 / 墙面 / 天花板都行），
 * 也可以右键已有的刻印锚点再叠一层。挖掉会原样退回刻印石板，蓝图不丢。
 * 真正的放 / 叠 / 卸都在 {@link MagicCircleProjectionEvents} 里，和蓝图共用同一条路径。</p>
 */
public final class EtchedSlateItem extends Item {
    public static final String ITEM_ID = "etched_slate";
    /** 和蓝图书同一个数据键，这样一份数据既能塞进布、也能塞进石板。 */
    private static final String DATA_KEY = MagicCircleBlueprint.DATA_KEY;

    public EtchedSlateItem(Properties properties) {
        super(properties);
    }

    /** 把一份蓝图做成刻印石板。 */
    public static ItemStack stackFor(MagicCircleBlueprint blueprint) {
        var holder = GeneratedMod.MOD_ITEMS.get(ITEM_ID);
        if (holder == null || blueprint == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(holder.get());
        blueprint.writeTo(stack);
        return stack;
    }

    /** 读出石板里的蓝图；不是石板 / 没数据就返回 null。 */
    public static MagicCircleBlueprint blueprintOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof EtchedSlateItem)) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(DATA_KEY)) return null;
        return MagicCircleBlueprint.fromTag(data.copyTag().getCompound(DATA_KEY));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        MagicCircleBlueprint blueprint = blueprintOf(stack);
        if (blueprint != null) {
            tooltip.add(Component.literal(blueprint.name()).withStyle(ChatFormatting.GOLD));
            tooltip.addAll(blueprint.describe());
            tooltip.add(Component.literal(blueprint.blockSummary()).withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.dimensional_mana.magiccircle.node.title")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.addAll(blueprint.nodeDetailLines(8));
        } else {
            tooltip.add(Component.translatable("tooltip.dimensional_mana.etched_slate.blank")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.dimensional_mana.etched_slate.usage")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.etched_slate.run")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.etched_slate.trigger")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
