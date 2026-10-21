package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class GrinderItem extends Item {
    private static final int[] PROCESS_TICKS = {360, 320, 160, 280, 240, 200};
    private final int maxTier;
    private final int processTicks;

    public GrinderItem(Properties properties, int maxTier, int grinderTier) {
        super(properties);
        this.maxTier = maxTier;
        this.processTicks = PROCESS_TICKS[grinderTier];
    }

    public int maxTier() {
        return maxTier;
    }

    public int processTicks() {
        return processTicks;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack grinder = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, menuPlayer) -> new GrinderMenu(id, inventory, grinder),
                    Component.translatable("container.dimensional_mana.grinder")
            ));
        }
        return InteractionResultHolder.sidedSuccess(grinder, level.isClientSide);
    }
}
