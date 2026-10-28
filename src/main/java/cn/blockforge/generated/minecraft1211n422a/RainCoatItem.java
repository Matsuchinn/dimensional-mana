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
 * 雨衣（背饰）：工作台用六个皮革 + 三块魔力布匹合成，穿在 Curios 的背饰（back）栏位。
 *
 * <p>两条效果：一是自己受到的冰、水元素附着时长只剩一成（-90%），和 {@link RainBootsItem}
 * 的水附着减半走同一个结算入口（{@link ElementReactionSystem#targetAttachmentTicks}）；
 * 二是受到的各种「冻结」时长减半（-50%）——冻结反应的定身、零下领域的深层冻结、
 * 以及原版细雪里的冰冻值积累（每两刻抵消一点，积累速度正好一半）都吃这个减免。</p>
 */
public final class RainCoatItem extends Item implements ICurioItem {
    public static final String ITEM_ID = "rain_coat";
    /** Curios 的背饰栏位标识，和法师披风共用。 */
    public static final String SLOT = "back";
    /** 自己受到的冰、水元素附着时长乘区：-90%，只剩一成。 */
    public static final float ICE_WATER_ATTACHMENT_MULTIPLIER = 0.10F;
    /** 受到的冻结时长乘区：-50%。 */
    public static final float FREEZE_DURATION_MULTIPLIER = 0.50F;

    public RainCoatItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return SLOT.equals(slotContext.identifier());
    }

    /** 原版细雪冰冻值：正常每刻 +1，这里每两刻抵消 1 点，积累速度正好一半。 */
    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        LivingEntity living = slotContext.entity();
        if (living.level().isClientSide || !living.isInPowderSnow || !living.canFreeze()) return;
        if (living.tickCount % 2 != 0) return;
        if (living.getTicksFrozen() > 0) living.setTicksFrozen(living.getTicksFrozen() - 1);
    }

    /** 是否穿着雨衣；所有效果判断都走这一处，保证提示框和实际效果一致。 */
    public static boolean worn(LivingEntity entity) {
        return entity != null && MagicEquipment.equippedCount(entity, ITEM_ID, SLOT) > 0;
    }

    /** 目标身上这次附着要乘的系数：只有自己受到的冰、水元素附着缩到一成。 */
    public static float attachmentMultiplier(LivingEntity target, ElementType element) {
        if (element != ElementType.ICE && element != ElementType.WATER) return 1.0F;
        return worn(target) ? ICE_WATER_ATTACHMENT_MULTIPLIER : 1.0F;
    }

    /** 这次冻结在目标身上实际持续多少刻：穿雨衣的玩家减半。 */
    public static int freezeTicks(LivingEntity target, int ticks) {
        if (!worn(target) || ticks <= 1) return ticks;
        return Math.max(1, Math.round(ticks * FREEZE_DURATION_MULTIPLIER));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.relic.header",
                        Component.translatable("tooltip.dimensional_mana.relic.slot." + SLOT))
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rain_coat.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rain_coat.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rain_coat.obtain")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
