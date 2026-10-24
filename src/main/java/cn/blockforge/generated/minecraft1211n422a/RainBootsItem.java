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
 * 雨靴（脚饰）：工作台用两个水元素结晶粉末 + 两个皮革合成，穿在 Curios 的脚饰（feet）栏位。
 *
 * <p>两条效果都不在物品里自己算，而是交给 {@link ElementReactionSystem} 统一结算：
 * 一是自己受到的水元素附着时长 ×0.5；二是泥沼（水 + 土反应）与土泥沼法术的减速、跳跃压制
 * 都挂不上身。穿在身上时还会每刻复查一次，已经挂着的泥沼减速会被立刻摘掉。</p>
 */
public final class RainBootsItem extends Item implements ICurioItem {
    public static final String ITEM_ID = "rain_boots";
    /** Curios 的脚饰栏位标识。 */
    public static final String SLOT = "feet";
    /** 自己受到的水元素附着时长乘区：减半。 */
    public static final float WATER_ATTACHMENT_MULTIPLIER = 0.5F;

    public RainBootsItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return SLOT.equals(slotContext.identifier());
    }

    /** 穿在身上时每刻复查：已经挂上的泥沼减速立刻摘掉，不用脱下来再穿一次。 */
    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof LivingEntity living && !living.level().isClientSide) {
            ElementReactionSystem.stripMudSlow(living);
        }
    }

    /** 是否穿着雨靴；所有效果判断都走这一处，保证提示框和实际效果一致。 */
    public static boolean worn(LivingEntity entity) {
        return entity != null && MagicEquipment.equippedCount(entity, ITEM_ID, SLOT) > 0;
    }

    /** 目标身上这次附着要乘的系数：只有自己受到的水元素附着减半，别人的不受影响。 */
    public static float attachmentMultiplier(LivingEntity target, ElementType element) {
        if (element != ElementType.WATER || !worn(target)) return 1.0F;
        return WATER_ATTACHMENT_MULTIPLIER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.relic.header",
                        Component.translatable("tooltip.dimensional_mana.relic.slot." + SLOT))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rain_boots.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rain_boots.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rain_boots.obtain")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
