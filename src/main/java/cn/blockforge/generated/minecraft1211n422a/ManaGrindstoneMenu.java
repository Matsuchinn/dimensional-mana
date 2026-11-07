package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 魔晶砂轮的菜单：三个格子（上 / 下两个输入，右边一个成品）加一个「模式切换」按钮。
 *
 * <p>界面上看到的成品由服务端的方块实体每个 tick 算好推过来，客户端只负责画；
 * 真正扣料、给经验、返还材料都发生在 {@link #settleTake(Player)} 里，
 * 也就是玩家把成品拿走的那一下，且只在服务端执行。</p>
 */
public final class ManaGrindstoneMenu extends AbstractContainerMenu {
    /** 「模式切换」按钮的 id。 */
    public static final int BUTTON_TOGGLE_MODE = 0;

    public static final int IMAGE_WIDTH = 200;
    public static final int IMAGE_HEIGHT = 206;
    public static final int INPUT_TOP_X = 42;
    public static final int INPUT_TOP_Y = 18;
    public static final int INPUT_BOTTOM_X = 42;
    public static final int INPUT_BOTTOM_Y = 42;
    public static final int RESULT_X = 116;
    public static final int RESULT_Y = 30;
    public static final int BUTTON_X = 39;
    public static final int BUTTON_Y = 66;
    public static final int BUTTON_WIDTH = 98;
    public static final int BUTTON_HEIGHT = 18;
    public static final int HINT_Y = 86;
    public static final int HINT2_Y = 96;
    public static final int INVENTORY_X = 19;
    public static final int INVENTORY_Y = 124;
    public static final int HOTBAR_Y = 182;

    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public ManaGrindstoneMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(ManaGrindstoneBlockEntity.SLOT_COUNT),
                new SimpleContainerData(1), ContainerLevelAccess.NULL);
    }

    public ManaGrindstoneMenu(int id, Inventory inventory, Container container, ContainerData data) {
        this(id, inventory, container, data, container instanceof ManaGrindstoneBlockEntity grindstone
                ? ContainerLevelAccess.create(grindstone.getLevel(), grindstone.getBlockPos())
                : ContainerLevelAccess.NULL);
    }

    private ManaGrindstoneMenu(int id, Inventory inventory, Container container, ContainerData data,
                               ContainerLevelAccess access) {
        super(GeneratedMod.MANA_GRINDSTONE_MENU.get(), id);
        checkContainerSize(container, ManaGrindstoneBlockEntity.SLOT_COUNT);
        this.container = container;
        this.data = data;
        this.access = access;

        addSlot(new Slot(container, ManaGrindstoneBlockEntity.INPUT_TOP, INPUT_TOP_X, INPUT_TOP_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return ManaGrindstoneLogic.canAccept(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(container, ManaGrindstoneBlockEntity.INPUT_BOTTOM, INPUT_BOTTOM_X, INPUT_BOTTOM_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return ManaGrindstoneLogic.canAccept(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(container, ManaGrindstoneBlockEntity.RESULT, RESULT_X, RESULT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public void onTake(Player player, ItemStack stack) { settleTake(player); }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    /** 当前模式，取自同步过来的方块数据。 */
    public int mode() {
        return ManaGrindstoneLogic.clampMode(data.get(0));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_TOGGLE_MODE) return false;
        if (player.level().isClientSide) return true;
        int next = ManaGrindstoneLogic.otherMode(mode());
        // 方块实体自己带着同一份模式数据，直接让它切（它会顺手重算成品）；
        // 万一容器不是方块实体（理论上只在客户端出现），退回写同步数据。
        if (container instanceof ManaGrindstoneBlockEntity grindstone) grindstone.setMode(next);
        else data.set(0, next);
        // 切换模式后成品的「可拿性」会变，立刻同步一次，界面不用等下一 tick。
        broadcastChanges();
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(),
                SoundSource.BLOCKS, 0.6F, next == ManaGrindstoneLogic.MODE_AFFIXES ? 1.35F : 0.85F));
        return true;
    }

    /**
     * 拿走成品的那一刻做的全部结算：给经验、发返还材料、清掉两个输入槽。
     *
     * <p>经验只在「模式 A 单件剥离」和「两件合并修复」时给，且数量照抄原版砂轮的算法；
     * 模式 B 的回报是材料而不是经验，所以不给经验，免得两种收益叠一起。</p>
     */
    private void settleTake(Player player) {
        if (player.level().isClientSide) return;
        ItemStack top = container.getItem(ManaGrindstoneBlockEntity.INPUT_TOP);
        ItemStack bottom = container.getItem(ManaGrindstoneBlockEntity.INPUT_BOTTOM);
        boolean merged = !top.isEmpty() && !bottom.isEmpty();
        int mode = mode();
        Level level = player.level();
        // 模式 B 的词条回收：清单要在成品被算出来之前取（那时词条还在输入槽里）。
        if (!merged && mode == ManaGrindstoneLogic.MODE_AFFIXES) {
            ItemStack source = top.isEmpty() ? bottom : top;
            for (ItemStack refund : ManaGrindstoneLogic.refunds(source)) {
                if (!player.getInventory().add(refund)) player.drop(refund, false);
            }
        } else {
            int xp = ManaGrindstoneLogic.experience(top, bottom, level.random);
            if (xp > 0) {
                if (level instanceof ServerLevel server) {
                    Vec3 at = player.position();
                    if (container instanceof ManaGrindstoneBlockEntity grindstone) {
                        at = Vec3.atCenterOf(grindstone.getBlockPos().above());
                    }
                    ExperienceOrb.award(server, at, xp);
                } else {
                    player.giveExperiencePoints(xp);
                }
            }
        }
        if (container instanceof ManaGrindstoneBlockEntity grindstone) {
            grindstone.consumeInputs();
        } else {
            container.setItem(ManaGrindstoneBlockEntity.INPUT_TOP, ItemStack.EMPTY);
            container.setItem(ManaGrindstoneBlockEntity.INPUT_BOTTOM, ItemStack.EMPTY);
            container.setItem(ManaGrindstoneBlockEntity.RESULT, ItemStack.EMPTY);
        }
        container.setChanged();
        playFinishSound(player, merged, mode);
    }

    /**
     * 「磨完了」的音效：底子照原版砂轮用 {@link SoundEvents#GRINDSTONE_USE}，再按这次磨出什么做点变化。
     *
     * <ul>
     *   <li>单件剥离附魔：音高压到 0.88 左右，听起来更沉、像磨掉一层；</li>
     *   <li>两件合并修复：音高抬到 1.05 左右，像「咔」一下拼合成功；</li>
     *   <li>词条回收（模式 B）：音高最高，再叠一层紫水晶清响，强调这次是用魔力把词条洗出来。</li>
     * </ul>
     *
     * <p>每次再叠一点点随机音高（0~0.05），连着磨好几件不会像复读机。</p>
     */
    private void playFinishSound(Player player, boolean merged, int mode) {
        if (!(container instanceof ManaGrindstoneBlockEntity grindstone)) return;
        Level level = player.level();
        BlockPos pos = grindstone.getBlockPos();
        boolean affix = mode == ManaGrindstoneLogic.MODE_AFFIXES;
        float pitch = (affix ? 1.18F : merged ? 1.05F : 0.88F) + level.random.nextFloat() * 0.05F;
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.9F, pitch);
        if (affix) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    0.7F, 1.3F + level.random.nextFloat() * 0.1F);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        int machineSlots = ManaGrindstoneBlockEntity.SLOT_COUNT;
        if (index == ManaGrindstoneBlockEntity.RESULT) {
            // 成品槽 shift 点击：先真的塞进背包，塞进去了才算结算，背包满了就当没点。
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
            settleTake(player);
            // 输入和成品都已经结算清空，这里直接返回，避免末尾再走一次 onTake 重复结算。
            slot.set(ItemStack.EMPTY);
            // 合并修复产出的是 2 件、背包又只剩一格时，moveItemStackTo 只会塞进去一部分：
            // 剩下的直接掉在脚边，不能因为「已经结算了」就凭空吞掉。
            if (!source.isEmpty()) {
                player.drop(source, false);
                source = ItemStack.EMPTY;
            }
            return copy;
        } else if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (ManaGrindstoneLogic.canAccept(source)) {
            int target = container.getItem(ManaGrindstoneBlockEntity.INPUT_TOP).isEmpty()
                    ? ManaGrindstoneBlockEntity.INPUT_TOP : ManaGrindstoneBlockEntity.INPUT_BOTTOM;
            if (!moveItemStackTo(source, target, target + 1, false)) return ItemStack.EMPTY;
        } else {
            int inventoryStart = machineSlots;
            int hotbarStart = inventoryStart + 27;
            if (index < hotbarStart) {
                if (!moveItemStackTo(source, hotbarStart, slots.size(), false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(source, inventoryStart, hotbarStart, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> AbstractContainerMenu.stillValid(
                ContainerLevelAccess.create(level, pos), player,
                GeneratedMod.MOD_BLOCKS.get(ManaGrindstoneBlock.BLOCK_ID).get()), true);
    }

    /** 界面读输入槽用：直接拿服务端同步过来的槽位内容。 */
    public ItemStack topItem() {
        return container.getItem(ManaGrindstoneBlockEntity.INPUT_TOP);
    }

    public ItemStack bottomItem() {
        return container.getItem(ManaGrindstoneBlockEntity.INPUT_BOTTOM);
    }

    public ItemStack resultItem() {
        return container.getItem(ManaGrindstoneBlockEntity.RESULT);
    }

    /** 模式名（界面按钮上的那行字）。 */
    public Component modeName() {
        return Component.translatable(mode() == ManaGrindstoneLogic.MODE_AFFIXES
                ? "gui.dimensional_mana.mana_grindstone.mode_affix"
                : "gui.dimensional_mana.mana_grindstone.mode_enchant");
    }
}
