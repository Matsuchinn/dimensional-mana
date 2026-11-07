package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 魔晶砂轮的方块实体：两个输入槽 + 一个成品槽，外加一个「当前模式」。
 *
 * <p>和原版砂轮不一样的地方就一点：剥离方向由玩家选，不写死在代码里。
 * 模式存在方块实体里（也写进 NBT），所以拆了重放、退出重进都还记得选的哪一档。</p>
 *
 * <p>成品槽每个 tick 重算一次：输入两格里的东西、或模式一变，成品立刻跟着变，
 * 不需要玩家关掉界面再打开。计算本身是纯函数（{@link ManaGrindstoneLogic}），
 * 没有随机、不扣料也不扣经验，所以只有「拿走成品」那一下才真正结算。</p>
 */
public final class ManaGrindstoneBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int INPUT_TOP = 0;
    public static final int INPUT_BOTTOM = 1;
    public static final int RESULT = 2;
    public static final int SLOT_COUNT = 3;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int mode = ManaGrindstoneLogic.MODE_ENCHANTMENTS;
    /** 重算成品时防止 setItem → refreshResult 的递归。 */
    private boolean refreshing;
    /** 上一次算成品时的输入快照：输入和模式都没动就不重算，省掉每 tick 一次的剥离运算。 */
    private ItemStack lastTop = ItemStack.EMPTY;
    private ItemStack lastBottom = ItemStack.EMPTY;
    private int lastMode = -1;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? mode : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) mode = ManaGrindstoneLogic.clampMode(value);
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    public ManaGrindstoneBlockEntity(BlockPos pos, BlockState state) {
        super(GeneratedMod.MANA_GRINDSTONE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManaGrindstoneBlockEntity grindstone) {
        grindstone.refreshResult();
    }

    /**
     * 按当前输入与模式重算成品槽；只有输入或模式变了才重算，
     * 而且算出来的东西跟槽里一样时不写数据，避免每 tick 刷一遍 NBT。
     */
    public void refreshResult() {
        if (refreshing) return;
        ItemStack top = items.get(INPUT_TOP);
        ItemStack bottom = items.get(INPUT_BOTTOM);
        if (mode == lastMode && ItemStack.matches(top, lastTop) && ItemStack.matches(bottom, lastBottom)) return;
        lastMode = mode;
        lastTop = top.copy();
        lastBottom = bottom.copy();
        ItemStack expected = ManaGrindstoneLogic.compute(top, bottom, mode);
        ItemStack current = items.get(RESULT);
        if (ItemStack.matches(current, expected)) return;
        refreshing = true;
        try {
            items.set(RESULT, expected);
            setChanged();
        } finally {
            refreshing = false;
        }
    }

    /** 当前模式：{@link ManaGrindstoneLogic#MODE_ENCHANTMENTS} 或 {@link ManaGrindstoneLogic#MODE_AFFIXES}。 */
    public int mode() {
        return mode;
    }

    public void setMode(int value) {
        int next = ManaGrindstoneLogic.clampMode(value);
        if (next == mode) return;
        mode = next;
        refreshResult();
        setChanged();
    }

    public void toggleMode() {
        setMode(ManaGrindstoneLogic.otherMode(mode));
    }

    public ContainerData dataAccess() {
        return data;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Mode", mode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        mode = ManaGrindstoneLogic.clampMode(tag.getInt("Mode"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.dimensional_mana.mana_grindstone");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ManaGrindstoneMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize(stack)) stack.setCount(getMaxStackSize(stack));
        if (slot != RESULT) refreshResult();
        setChanged();
    }

    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == RESULT) return false;
        return ManaGrindstoneLogic.canAccept(stack);
    }

    @Override public void clearContent() { items.clear(); }

    /** 输入格里的成品清空（取走成品时用）。 */
    public void consumeInputs() {
        refreshing = true;
        try {
            items.set(INPUT_TOP, ItemStack.EMPTY);
            items.set(INPUT_BOTTOM, ItemStack.EMPTY);
            items.set(RESULT, ItemStack.EMPTY);
            setChanged();
        } finally {
            refreshing = false;
        }
    }
}
