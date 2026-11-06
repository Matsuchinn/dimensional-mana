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
 * 元素精通护符：三个栏位通用的护符型饰品。
 *
 * <p>栏位按需求给足三种——通用饰品（{@code universal}）、魔法饰品（{@code magic_accessory}）
 * 与护符（{@code charm}），任意一个栏位都能戴。佩戴时的 +50 元素精通由
 * {@link ElementMastery} 统一结算，装备/卸下时让缓存失效即可立刻生效。</p>
 */
public final class ElementalMasteryCharmItem extends Item implements ICurioItem {
    public static final String ITEM_ID = "element_mastery_charm";
    /** 每件提供的元素精通。 */
    public static final float BONUS = 50.0F;
    /** 能戴这三个栏位。 */
    public static final List<String> SLOTS = List.of("universal", "magic_accessory", "charm");

    public ElementalMasteryCharmItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return SLOTS.contains(slotContext.identifier());
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack previousStack, ItemStack stack) {
        refresh(slotContext.entity());
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        refresh(slotContext.entity());
    }

    /**
     * 只让缓存失效，不当场重算：卸下时槽位里的物品还没被拿走，
     * 这时候算出来的值会是旧的。下一次查询或每秒的同步比对会自动把它改成正确值。
     */
    private static void refresh(LivingEntity entity) {
        if (entity != null) ElementMastery.invalidate(entity);
    }

    /** 已装备的件数：同一件戴在两个栏位就是两份，和效果描述一致。 */
    public static int equippedCount(LivingEntity entity) {
        return RelicEffects.count(entity, ITEM_ID);
    }

    public static float bonusOf(LivingEntity entity) {
        return BONUS * equippedCount(entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.relic.header",
                        Component.translatable("tooltip.dimensional_mana.element_mastery_charm.slots"))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_mastery_charm.effect",
                String.format(java.util.Locale.ROOT, "%.0f", BONUS)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_mastery_charm.detail")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.element_mastery_charm.obtain")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
