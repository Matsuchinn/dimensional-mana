package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class GrinderMenu extends AbstractContainerMenu {
    private final SimpleContainer container = new SimpleContainer(2);
    private final SimpleContainerData data = new SimpleContainerData(4);
    private final ItemStack grinder;
    private final Player owner;
    private final int maxTier;

    public GrinderMenu(int id, Inventory inventory) {
        this(id, inventory, ItemStack.EMPTY);
    }

    public GrinderMenu(int id, Inventory inventory, ItemStack grinder) {
        super(GeneratedMod.GRINDER_MENU.get(), id);
        this.grinder = grinder;
        this.owner = inventory.player;
        this.maxTier = grinder.getItem() instanceof GrinderItem item ? item.maxTier() : 0;
        this.data.set(1, grinder.getItem() instanceof GrinderItem item ? item.processTicks() : 360);
        this.data.set(3, this.maxTier);

        addSlot(new Slot(container, 0, 44, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return crystalTier(stack) >= 0 && crystalTier(stack) <= data.get(3); }
        });
        addSlot(new Slot(container, 1, 116, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || data.get(2) != 0 || grinder.isEmpty() || !canStart()) return false;
        data.set(0, 0);
        data.set(2, 1);
        playGrindingSound(0);
        return true;
    }

    @Override
    public void broadcastChanges() {
        if (data.get(2) == 1 && !grinder.isEmpty()) {
            if (!canContinue()) {
                data.set(0, 0);
                data.set(2, 0);
            } else {
                data.set(0, data.get(0) + 1);
                if (data.get(0) % 20 == 0) playGrindingSound(data.get(0));
                if (data.get(0) >= data.get(1)) finishGrinding();
            }
        }
        super.broadcastChanges();
    }

    private boolean canStart() {
        return canContinue();
    }

    private boolean canContinue() {
        GrindingResult result = grindingResult(container.getItem(0));
        if (result == null || result.tier() > data.get(3)) return false;
        ItemStack expected = result.stack();
        ItemStack output = container.getItem(1);
        return output.isEmpty() || (ItemStack.isSameItemSameComponents(output, expected)
                && output.getCount() + expected.getCount() <= output.getMaxStackSize());
    }

    private void finishGrinding() {
        GrindingResult result = grindingResult(container.getItem(0));
        if (result == null) return;
        ItemStack dust = result.stack();
        container.getItem(0).shrink(1);
        ItemStack output = container.getItem(1);
        if (output.isEmpty()) container.setItem(1, dust);
        else output.grow(dust.getCount());

        if (grinder.getItem() instanceof GrinderItem && grinder.getDamageValue() < grinder.getMaxDamage() &&
                slots.size() > 2 && slots.get(2).container instanceof Inventory inv && inv.player instanceof ServerPlayer serverPlayer &&
                serverPlayer.level() instanceof ServerLevel serverLevel && !serverPlayer.getAbilities().instabuild) {
            grinder.hurtAndBreak(1, serverLevel, serverPlayer, item -> {});
        }
        data.set(0, 0);
        data.set(2, 0);
        container.setChanged();
    }

    private static int crystalTier(ItemStack stack) {
        GrindingResult result = grindingResult(stack);
        return result == null ? -1 : result.tier();
    }

    private static GrindingResult grindingResult(ItemStack input) {
        GrinderRecipes.Recipe recipe = GrinderRecipes.find(input);
        return recipe == null ? null : new GrindingResult(recipe.tier(), recipe.output().copy());
    }

    private void playGrindingSound(int progress) {
        if (owner.level().isClientSide) return;
        float pitch = 0.82F + Math.min(0.28F, progress / (float)Math.max(1, data.get(1)) * 0.28F);
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.GRINDSTONE_USE,
                SoundSource.PLAYERS, 0.55F, pitch);
    }

    private record GrindingResult(int tier, ItemStack stack) {}

    public int progress() { return data.get(0); }
    public int totalProgress() { return Math.max(1, data.get(1)); }
    public boolean isGrinding() { return data.get(2) == 1; }
    public boolean canGrind() { return canStart(); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < 2) {
            if (!moveItemStackTo(source, 2, 38, true)) return ItemStack.EMPTY;
        } else if (crystalTier(source) >= 0 && crystalTier(source) <= data.get(3)) {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < 29) {
            if (!moveItemStackTo(source, 29, 38, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 2, 29, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return !grinder.isEmpty() && grinder.getItem() instanceof GrinderItem;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, container);
    }
}
