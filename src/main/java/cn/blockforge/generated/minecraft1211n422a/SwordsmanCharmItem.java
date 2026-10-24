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
 * 剑士护符（护符）：把斗气从「站着凝聚」变成「随时自己长」。
 *
 * <p>戴上以后每秒自然恢复 1 点斗气，同时消耗等量魔素（1 点斗气 1 点魔素）；
 * 每次完美格挡再给 2 秒的 +25% 移速，方便弹开以后立刻贴身追击。
 * 加速用的是临时属性修正，到期自动摘掉，不会偷走玩家的移速。</p>
 */
public final class SwordsmanCharmItem extends Item implements ICurioItem {
    public static final String ITEM_ID = "swordsman_charm";
    /** Curios 的护符栏位标识。 */
    public static final String SLOT = "charm";
    /** 每秒自然恢复的斗气。 */
    public static final double REGEN_PER_SECOND = 1.0;
    /** 完美格挡后加速持续多久（2 秒）。 */
    public static final int SPEED_TICKS = 40;

    public SwordsmanCharmItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return SLOT.equals(slotContext.identifier());
    }

    /** 是否戴着剑士护符。 */
    public static boolean worn(LivingEntity entity) {
        return entity != null && MagicEquipment.equippedCount(entity, ITEM_ID, SLOT) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.relic.header",
                        Component.translatable("tooltip.dimensional_mana.relic.slot." + SLOT))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordsman_charm.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordsman_charm.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordsman_charm.3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.swordsman_charm.obtain")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
