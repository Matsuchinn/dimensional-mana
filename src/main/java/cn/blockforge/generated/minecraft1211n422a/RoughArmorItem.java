package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.Holder;

import java.util.List;

/**
 * 粗糙护甲：数值与原版护甲一样走材质表，额外只挂一个「套装」标记，
 * 玩家穿齐整套时的惩罚由 {@link RoughGearEvents} 负责施加。
 */
public final class RoughArmorItem extends ArmorItem {
    private final RoughSet set;

    public RoughArmorItem(RoughSet set, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.set = set;
    }

    public RoughSet set() {
        return set;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rough_gear").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(set.penaltyKey()).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.rough_repair").withStyle(ChatFormatting.DARK_GREEN));
    }
}
