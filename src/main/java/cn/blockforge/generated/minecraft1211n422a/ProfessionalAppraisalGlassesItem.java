package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 专业鉴定眼镜：比基础鉴定眼镜强一大截的数据终端，装备在 Curios 的「首饰」（head）栏位。
 *
 * <p>它本身不带任何属性修正，全部价值都在服务端实时解析 + HUD 呈现上：
 * 生命、魔素、斗气、每秒恢复、抗性易伤、专属技能冷却，以及按 V 键释放的魔力雷达。</p>
 *
 * <p>消耗与显示开关由 {@link AppraisalConfig} 控制，解析与发包由
 * {@link ProfessionalAppraisalSystem} 负责；本类只管「能不能装备」「有没有戴着」。</p>
 */
public final class ProfessionalAppraisalGlassesItem extends Item implements ICurioItem {
    public static final String ITEM_ID = "professional_appraisal_glasses";
    /** Curios 的 head 栏位在中文里就叫「首饰」，与基础鉴定眼镜同一个槽。 */
    public static final String SLOT = "head";

    public ProfessionalAppraisalGlassesItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return SLOT.equals(slotContext.identifier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.professional_appraisal_glasses.hint")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.professional_appraisal_glasses.scan",
                        Component.keybind("key.dimensional_mana.appraisal_scan"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.professional_appraisal_glasses.cost",
                        String.format("%.1f", AppraisalConfig.IDLE_MANA_COST.get()),
                        String.format("%.1f", AppraisalConfig.LOCK_MANA_COST.get()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.professional_appraisal_glasses.scan_cost",
                        String.format("%.0f", AppraisalConfig.SCAN_MANA_COST.get()),
                        String.format("%.0f", AppraisalConfig.SCAN_RADIUS.get()))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /** 玩家是否把专业鉴定眼镜戴在首饰栏上。 */
    public static boolean isEquipped(LivingEntity entity) {
        if (entity == null) return false;
        var holder = GeneratedMod.MOD_ITEMS.get(ITEM_ID);
        if (holder == null) return false;
        Item item = holder.get();
        
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> {
                    var stacksHandler = handler.getStacksHandler(SLOT).orElse(null);
                    if (stacksHandler != null) {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (stacks.getStackInSlot(i).is(item)) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .orElse(false);
    }
}