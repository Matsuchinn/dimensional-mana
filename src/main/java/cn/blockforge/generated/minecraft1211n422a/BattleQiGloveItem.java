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
 * 斗气手套（手饰）：新手剑士的第一件斗气饰品。
 *
 * <p>三条效果都围绕「近身打架」：近战伤害 +15%（独立乘区，和锋利、力量、暴击都不冲突），
 * 每次近战命中回 2 点斗气，打死生物的那一下回 4 点。数值本身写在物品上，
 * 结算统一在 {@link ManaEvents} 的伤害链里完成，保证提示框和实际效果永远一致。</p>
 */
public final class BattleQiGloveItem extends Item implements ICurioItem {
    public static final String ITEM_ID = "battle_qi_gloves";
    /** Curios 的手饰栏位标识。 */
    public static final String SLOT = "hands";
    /** 近战伤害的独立乘区：×1.15。 */
    public static final float MELEE_DAMAGE_MULTIPLIER = 1.15F;
    /** 近战命中恢复的斗气。 */
    public static final int QI_ON_HIT = 2;
    /** 近战击杀恢复的斗气（命中那一下翻倍）。 */
    public static final int QI_ON_KILL = 4;

    public BattleQiGloveItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return SLOT.equals(slotContext.identifier());
    }

    /** 是否戴着斗气手套；伤害加成和回气都走这一处。 */
    public static boolean worn(LivingEntity entity) {
        return entity != null && MagicEquipment.equippedCount(entity, ITEM_ID, SLOT) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.relic.header",
                        Component.translatable("tooltip.dimensional_mana.relic.slot." + SLOT))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.battle_qi_gloves.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.battle_qi_gloves.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.battle_qi_gloves.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.battle_qi_gloves.obtain")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
