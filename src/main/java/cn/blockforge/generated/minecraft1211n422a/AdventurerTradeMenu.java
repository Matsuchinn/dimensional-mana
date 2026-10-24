package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 冒险者交易界面：每一笔交易占 3 个“展示格”（标价 1、标价 2、成品），
 * 展示格不能拿也不能放，点成品格就成交 —— 真正的校验与扣货都在服务端
 * {@link AdventurerTrades#execute} 里做。
 *
 * <p>剩余次数、总次数和品级通过 {@link SimpleContainerData} 同步给客户端，
 * 所以物品只走原版的格子同步，不用另加自定义包。</p>
 */
public final class AdventurerTradeMenu extends AbstractContainerMenu {
    public static final int OFFER_COUNT = AdventurerTrades.OFFER_COUNT;
    private static final int PARTS = 3;
    private static final int DISPLAY_SLOTS = OFFER_COUNT * PARTS;
    private static final int DATA_SIZE = OFFER_COUNT * PARTS;

    static final int GRID_X = 8;
    static final int GRID_Y = 18;
    static final int COLUMN_WIDTH = 92;
    static final int ROW_HEIGHT = 22;
    static final int INVENTORY_Y = 140;
    static final int HOTBAR_Y = 198;

    private final SimpleContainer display = new SimpleContainer(DISPLAY_SLOTS);
    private final SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
    private final Player owner;
    private final int adventurerId;
    private Adventurer adventurer;

    public AdventurerTradeMenu(int id, Inventory inventory, Adventurer adventurer) {
        this(id, inventory, adventurer, adventurer == null ? -1 : adventurer.getId());
    }

    /** 客户端用：只带着实体 id 回来，实体从自己的世界里找。 */
    public AdventurerTradeMenu(int id, Inventory inventory, int entityId) {
        this(id, inventory, inventory.player.level().getEntity(entityId) instanceof Adventurer found ? found : null,
                entityId);
    }

    private AdventurerTradeMenu(int id, Inventory inventory, Adventurer adventurer, int entityId) {
        super(GeneratedMod.ADVENTURER_TRADE_MENU.get(), id);
        this.owner = inventory.player;
        this.adventurer = adventurer;
        this.adventurerId = entityId;

        for (int index = 0; index < OFFER_COUNT; index++) {
            for (int part = 0; part < PARTS; part++) {
                addSlot(new DisplaySlot(display, index * PARTS + part, partX(index, part), partY(index)));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + col + row * 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
        refresh();
        if (adventurer != null && !owner.level().isClientSide()) adventurer.beginTrading(owner);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (adventurer != null && !player.level().isClientSide()) adventurer.endTrading(player);
    }

    static int cellX(int index) { return GRID_X + (index % 2) * COLUMN_WIDTH; }
    static int cellY(int index) { return GRID_Y + (index / 2) * ROW_HEIGHT; }

    static int partX(int index, int part) {
        return cellX(index) + switch (part) {
            case 0 -> 0;
            case 1 -> 19;
            default -> 48;
        };
    }

    static int partY(int index) { return cellY(index); }

    private Adventurer adventurer() {
        if (adventurer == null && adventurerId >= 0 && owner != null
                && owner.level().getEntity(adventurerId) instanceof Adventurer found) {
            adventurer = found;
        }
        return adventurer;
    }

    private List<AdventurerTrades.Offer> offers() {
        Adventurer target = adventurer();
        return target == null ? List.of() : target.tradeOffers();
    }

    /** 服务端每刻把冒险者身上的交易抄进展示格；客户端不要调，否则会盖掉同步下来的物品。 */
    private void refresh() {
        if (owner == null || owner.level().isClientSide()) return;
        List<AdventurerTrades.Offer> offers = offers();
        for (int index = 0; index < OFFER_COUNT; index++) {
            if (index < offers.size()) {
                AdventurerTrades.Offer offer = offers.get(index);
                setShown(index * PARTS, AdventurerTrades.discounted(owner, offer.cost(0)));
                setShown(index * PARTS + 1, AdventurerTrades.discounted(owner, offer.cost(1)));
                setShown(index * PARTS + 2, offer.result());
                data.set(index * PARTS, offer.remaining());
                data.set(index * PARTS + 1, offer.maxUses());
                data.set(index * PARTS + 2, offer.tier().ordinal());
            } else {
                for (int part = 0; part < PARTS; part++) setShown(index * PARTS + part, ItemStack.EMPTY);
                data.set(index * PARTS, 0);
                data.set(index * PARTS + 1, 0);
                data.set(index * PARTS + 2, 0);
            }
        }
    }

    private void setShown(int slot, ItemStack stack) {
        if (ItemStack.matches(display.getItem(slot), stack)) return;
        display.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)) return false;
        Adventurer target = adventurer();
        if (target == null || !AdventurerTrades.execute(target, player, id)) return false;
        refresh();
        super.broadcastChanges();
        return true;
    }

    /** 展示格不参与原版搬运；点成品格就当作一次交易请求（只在服务端执行）。 */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < DISPLAY_SLOTS) {
            if (clickType == ClickType.PICKUP && slotId % PARTS == 2
                    && player instanceof net.minecraft.server.level.ServerPlayer) {
                clickMenuButton(player, slotId / PARTS);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < DISPLAY_SLOTS) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        int mainStart = DISPLAY_SLOTS;
        int mainEnd = mainStart + 27;
        int hotbarEnd = mainEnd + 9;
        if (index < mainEnd) {
            if (!moveItemStackTo(source, mainEnd, hotbarEnd, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, mainStart, mainEnd, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        Adventurer target = adventurer();
        return target != null && target.isAlive() && !target.isRemoved() && player.distanceToSqr(target) <= 64.0;
    }

    public int remaining(int index) {
        return index >= 0 && index < OFFER_COUNT ? data.get(index * PARTS) : 0;
    }

    public int maxUses(int index) {
        return index >= 0 && index < OFFER_COUNT ? data.get(index * PARTS + 1) : 0;
    }

    public int tierOrdinal(int index) {
        return index >= 0 && index < OFFER_COUNT ? data.get(index * PARTS + 2) : 0;
    }

    public ItemStack shownItem(int index, int part) {
        if (index < 0 || index >= OFFER_COUNT || part < 0 || part >= PARTS) return ItemStack.EMPTY;
        return display.getItem(index * PARTS + part);
    }

    /** 客户端判断“材料够不够”，只用于把格子画暗一点；真正的判定在服务端。 */
    public boolean affordable(int index, Player player) {
        if (index < 0 || index >= OFFER_COUNT || player == null || remaining(index) <= 0) return false;
        List<ItemStack> costs = new ArrayList<>(2);
        for (int part = 0; part < PARTS - 1; part++) {
            ItemStack cost = shownItem(index, part);
            if (!cost.isEmpty()) costs.add(cost);
        }
        return !costs.isEmpty() && AdventurerTrades.hasCosts(player, AdventurerTrades.discounted(player, costs));
    }

    /** 只做展示的格子：不能拿、不能放。 */
    private static final class DisplaySlot extends Slot {
        DisplaySlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
