package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class AppraisalGlassesItem extends Item implements ICurioItem {
    public AppraisalGlassesItem(Properties properties) {
        super(properties);
        CuriosApi.registerCurio(this, this);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return "head".equals(slotContext.identifier());
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!slotContext.entity().level().isClientSide && slotContext.entity().tickCount % 20 == 0) {
            ManaData.spendMana(slotContext.entity(), 0.4);
        }
    }

    public static boolean isEquipped(net.minecraft.world.entity.LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> {
                    var stacksHandler = handler.getStacksHandler("head").orElse(null);
                    if (stacksHandler != null) {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (stacks.getStackInSlot(i).is(GeneratedMod.MOD_ITEMS.get("appraisal_glasses").get())) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .orElse(false);
    }
}