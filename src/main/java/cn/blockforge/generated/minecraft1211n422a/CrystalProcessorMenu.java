package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CrystalProcessorMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = CrystalProcessorBlockEntity.SLOT_COUNT;
    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public CrystalProcessorMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(MACHINE_SLOTS), new SimpleContainerData(5), ContainerLevelAccess.NULL);
    }

    public CrystalProcessorMenu(int id, Inventory inventory, Container container, ContainerData data) {
        this(id, inventory, container, data, container instanceof CrystalProcessorBlockEntity processor
                ? ContainerLevelAccess.create(processor.getLevel(), processor.getBlockPos()) : ContainerLevelAccess.NULL);
    }

    private CrystalProcessorMenu(int id, Inventory inventory, Container container, ContainerData data,
                                 ContainerLevelAccess access) {
        super(GeneratedMod.CRYSTAL_PROCESSOR_MENU.get(), id);
        checkContainerSize(container, MACHINE_SLOTS);
        checkContainerDataCount(data, 5);
        this.container = container;
        this.data = data;
        this.access = access;
        container.startOpen(inventory.player);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(container, col + row * 3, 17 + col * 18, 25 + row * 18));
            }
        }
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                addSlot(new Slot(container, CrystalProcessorBlockEntity.OUTPUT_START + col + row * 2,
                        125 + col * 18, 25 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
        }
        addSlot(new Slot(container, CrystalProcessorBlockEntity.FUEL_SLOT, 92, 69) {
            @Override public boolean mayPlace(ItemStack stack) { return CrystalProcessorBlockEntity.fuelValue(stack) > 0; }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 109 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 167));
        addDataSlots(data);
    }

    public int processProgress() { return data.get(0); }
    public int processTotal() { return Math.max(1, data.get(1)); }
    public int fuelProgress() { return data.get(2); }
    public int fuelTotal() { return Math.max(1, data.get(3)); }
    public int mana() { return data.get(4); }
    public int maxMana() { return CrystalProcessorBlockEntity.MAX_MANA; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(source, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (CrystalProcessorBlockEntity.fuelValue(source) > 0) {
            if (!moveItemStackTo(source, CrystalProcessorBlockEntity.FUEL_SLOT,
                    CrystalProcessorBlockEntity.FUEL_SLOT + 1, false)
                    && !moveItemStackTo(source, CrystalProcessorBlockEntity.INPUT_START,
                    CrystalProcessorBlockEntity.INPUT_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, CrystalProcessorBlockEntity.INPUT_START,
                CrystalProcessorBlockEntity.INPUT_END, false)) {
            int inventoryStart = MACHINE_SLOTS;
            int hotbarStart = inventoryStart + 27;
            if (index < hotbarStart) {
                if (!moveItemStackTo(source, hotbarStart, slots.size(), false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(source, inventoryStart, hotbarStart, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> AbstractContainerMenu.stillValid(
                ContainerLevelAccess.create(level, pos), player, GeneratedMod.MOD_BLOCKS.get("crystal_processor").get()), true);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
