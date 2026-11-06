package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 魔精钢护甲。护甲值、韧性、击退抗性都由 {@link MojiangSteelEquipment#MATERIAL} 提供，
 * 这里只额外挂两样原版属性系统装不下的东西：
 * 每件的「独立伤害减免」（事件里乘算）与附魔相性的说明文字。
 */
public final class MojiangSteelArmorItem extends ArmorItem {
    public MojiangSteelArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    /** 每件装备的独立伤害减免，四件按乘法叠加。 */
    public float independentDamageReduction() {
        return switch (getType()) {
            case HELMET -> 0.08F;
            case CHESTPLATE -> 0.16F;
            case LEGGINGS -> 0.12F;
            case BOOTS -> 0.08F;
            default -> 0.0F;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dimensional_mana.mojing_steel_armor",
                Math.round(independentDamageReduction() * 100.0F)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.mojing_steel_affinity")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        // 附魔 +1 会直接写进物品数据（面板上就是 +1 后的等级），这里只是说明来源。
        tooltip.add(Component.translatable("tooltip.dimensional_mana.mojing_steel_enchant_bonus")
                .withStyle(ChatFormatting.GOLD));
    }
}
