package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 武备加工台的菜单：两个格子 + 一个「锻造」按钮。
 *
 * <p>所有结算都在服务端做，客户端只负责画：点按钮时调
 * {@link #clickMenuButton(Player, int)}，真正的扣料、升级、找零都在这里完成。</p>
 *
 * <p>词条锁定（防手滑）也在这里：勾选状态只活在当前这一次打开的菜单里，关界面就清空；
 * 是否「已出目标词条」不存布尔量，而是每次拿槽里的武备现算 —— 客户端和服务端用同一套
 * 判断，勾选一变、武备一换，保护自动跟着失效，不会出现两边状态对不上。</p>
 */
public final class WeaponWorkbenchMenu extends AbstractContainerMenu {
    /** 「锻造」按钮的 id。 */
    public static final int BUTTON_FORGE = 0;
    /** 「放弃本次结果」按钮的 id：把武备还原成这次洗练之前的样子。 */
    public static final int BUTTON_DISCARD = 1;

    /** 锁定面板最多勾 4 条副词条。 */
    public static final int MAX_LOCKED_SUBS = 4;

    public static final int IMAGE_WIDTH = 272;
    public static final int IMAGE_HEIGHT = 240;
    public static final int EQUIP_X = 43;
    public static final int EQUIP_Y = 20;
    public static final int MATERIAL_X = 43;
    public static final int MATERIAL_Y = 64;
    public static final int BUTTON_X = 14;
    public static final int BUTTON_Y = 96;
    public static final int BUTTON_WIDTH = 76;
    public static final int BUTTON_HEIGHT = 18;
    public static final int INVENTORY_X = 55;
    public static final int INVENTORY_Y = 162;
    public static final int HOTBAR_Y = 220;

    private final Container container;
    private final ContainerLevelAccess access;

    /** 勾选的主词条锁定键（空串 = 没勾）。 */
    private String lockedMain = "";
    /** 勾选的副词条锁定键，最多 {@link #MAX_LOCKED_SUBS} 条。 */
    private final List<String> lockedSubs = new ArrayList<>();
    /** 上一次洗练是不是洗出了全部目标词条（勾选一变就作废，所以只有「刚洗完」才可能为真）。 */
    private boolean armed;
    /** 这一次洗练之前的武备，「放弃本次结果」就是还原成它。 */
    private ItemStack beforeReroll = ItemStack.EMPTY;
    /** 这一次洗练产出的武备，用来确认槽里的东西还是我们发出去的那件，防止误还原别的装备。 */
    private ItemStack lastResult = ItemStack.EMPTY;

    public WeaponWorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(WeaponWorkbenchBlockEntity.SLOT_COUNT), ContainerLevelAccess.NULL);
    }

    public WeaponWorkbenchMenu(int id, Inventory inventory, Container container) {
        this(id, inventory, container, container instanceof WeaponWorkbenchBlockEntity workbench
                ? ContainerLevelAccess.create(workbench.getLevel(), workbench.getBlockPos()) : ContainerLevelAccess.NULL);
    }

    private WeaponWorkbenchMenu(int id, Inventory inventory, Container container, ContainerLevelAccess access) {
        super(GeneratedMod.WEAPON_WORKBENCH_MENU.get(), id);
        checkContainerSize(container, WeaponWorkbenchBlockEntity.SLOT_COUNT);
        this.container = container;
        this.access = access;

        addSlot(new Slot(container, WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT, EQUIP_X, EQUIP_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return EquipmentAffixSystem.isEquipment(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(container, WeaponWorkbenchBlockEntity.MATERIAL_SLOT, MATERIAL_X, MATERIAL_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return ArmamentForge.acceptsMaterial(stack); }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    /** 材料格这一格「只吃一颗」：不管叠了多少个，锻造时只消耗 1 个。 */
    private void consumeOneMaterial() {
        ItemStack material = container.getItem(WeaponWorkbenchBlockEntity.MATERIAL_SLOT);
        if (material.isEmpty()) return;
        material.shrink(1);
        container.setItem(WeaponWorkbenchBlockEntity.MATERIAL_SLOT,
                material.isEmpty() ? ItemStack.EMPTY : material);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_DISCARD) {
            if (player.level().isClientSide) return true;
            discardResult(player);
            return true;
        }
        if (id != BUTTON_FORGE) return false;
        if (player.level().isClientSide) return true;
        forge(player);
        return true;
    }

    /**
     * 点「锻造」时的完整结算。
     *
     * <p>进来第一件事就是查防手滑保护：槽里的武备已经命中了全部勾选目标时，这一次点击
     * 直接吞掉、什么都不做。这样即使玩家在网络延迟下手速极快连点两下，第二次请求也会被
     * 服务端挡在结算之前，不会把刚洗出来的好词条又洗掉。</p>
     */
    private void forge(Player player) {
        ItemStack equipment = container.getItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT);
        ItemStack material = container.getItem(WeaponWorkbenchBlockEntity.MATERIAL_SLOT);
        if (equipment.isEmpty() || !EquipmentAffixSystem.isEquipment(equipment)) {
            message(player, "message.dimensional_mana.weapon_workbench.need_equipment");
            return;
        }
        if (material.isEmpty()) {
            message(player, "message.dimensional_mana.weapon_workbench.need_material");
            return;
        }
        if (protectionActive()) {
            message(player, "message.dimensional_mana.weapon_workbench.locked_guard");
            return;
        }
        // 锻造模板：只用于白板装备，给它赋一整套武备词条（这套词条以后升级要多花 50% 魔素）。
        if (ArmamentForge.isSmithingTemplate(material)) {
            applyTemplate(player, equipment);
            return;
        }
        if (!EquipmentAffixSystem.hasAffix(equipment)) {
            message(player, "message.dimensional_mana.weapon_workbench.no_affix");
            return;
        }
        // 武备强化石：武备满级之后才解锁，一颗强制成长一条副词条（直到四条副词条全满）。
        if (ArmamentForge.isEnhanceStone(material)) {
            growSub(player, equipment);
            return;
        }
        if (ArmamentForge.isRerollMaterial(material)) {
            reroll(player, equipment);
            return;
        }
        // 词条重铸符：品级保持不变，只把主副词条按当前品级重抽一遍。
        if (ArmamentForge.isReforgeTalisman(material)) {
            reforge(player, equipment);
            return;
        }
        int value = ArmamentForge.crystalValue(material);
        if (value <= 0) {
            message(player, "message.dimensional_mana.weapon_workbench.invalid_material");
            return;
        }
        int level = EquipmentAffixSystem.mainLevel(equipment);
        if (level >= ArmamentForge.MAX_LEVEL) {
            message(player, "message.dimensional_mana.weapon_workbench.max_level", ArmamentForge.MAX_LEVEL);
            return;
        }
        int cost = ArmamentForge.upgradeCost(equipment, level + 1);
        if (value < cost) {
            message(player, "message.dimensional_mana.weapon_workbench.not_enough_mana", value, cost);
            return;
        }
        consumeOneMaterial();
        ItemStack upgraded = EquipmentAffixSystem.upgradeMainLevel(equipment, player.getRandom());
        container.setItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT, upgraded);
        // 进度「千锤百炼」：主词条喂到 Lv.12 满级。
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && EquipmentAffixSystem.mainLevel(upgraded) >= ArmamentForge.MAX_LEVEL) {
            MagicProgression.awardAdvancement(serverPlayer, "affix_max");
        }
        // 溢出部分打五折，像现实找零一样按面额返还；不足 25 的零头抹掉。
        int refund = ArmamentForge.refundValue(value - cost);
        for (ItemStack change : ArmamentForge.changeFor(value - cost)) give(player, change);
        EquipmentAffixSystem.refreshPlayer(player);
        playSound(player, SoundEvents.ANVIL_USE, 1.08F);
        message(player, "message.dimensional_mana.weapon_workbench.upgraded",
                EquipmentAffixSystem.mainLevel(upgraded), refund);
    }

    /** 武备强化石：满级武备专用的「副词条强制成长」，一颗一次，直到四条副词条都到顶。 */
    private void growSub(Player player, ItemStack equipment) {
        if (EquipmentAffixSystem.mainLevel(equipment) < ArmamentForge.MAX_LEVEL) {
            message(player, "message.dimensional_mana.weapon_workbench.stone_need_max", ArmamentForge.MAX_LEVEL);
            return;
        }
        if (!EquipmentAffixSystem.hasGrowableSub(equipment)) {
            message(player, "message.dimensional_mana.weapon_workbench.stone_subs_maxed");
            return;
        }
        consumeOneMaterial();
        ItemStack grown = EquipmentAffixSystem.growOneSub(equipment, player.getRandom());
        container.setItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT, grown);
        EquipmentAffixSystem.refreshPlayer(player);
        playSound(player, SoundEvents.ANVIL_USE, 1.32F);
        message(player, "message.dimensional_mana.weapon_workbench.stone_grown");
    }

    /** 锻造模板：给白板装备按品质权重抽一次武备，已经有了词条就不给重复赋。 */
    private void applyTemplate(Player player, ItemStack equipment) {
        if (EquipmentAffixSystem.hasAffix(equipment)) {
            message(player, "message.dimensional_mana.weapon_workbench.template_already");
            return;
        }
        consumeOneMaterial();
        ItemStack forged = EquipmentAffixSystem.applyTemplateAffix(equipment, player.getRandom());
        container.setItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT, forged);
        EquipmentAffixSystem.refreshPlayer(player);
        playSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 0.85F);
        message(player, "message.dimensional_mana.weapon_workbench.template_applied",
                EquipmentAffixSystem.view(forged).gradeName());
    }

    private void reroll(Player player, ItemStack equipment) {
        beforeReroll = equipment.copy();
        consumeOneMaterial();
        ItemStack rerolled = EquipmentAffixSystem.rerollAffixes(equipment, player.getRandom());
        container.setItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT, rerolled);
        lastResult = rerolled.copy();
        EquipmentAffixSystem.refreshPlayer(player);
        playSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.35F);
        EquipmentAffixSystem.View view = EquipmentAffixSystem.view(rerolled);
        message(player, "message.dimensional_mana.weapon_workbench.rerolled",
                view.gradeName(), EquipmentAffixSystem.rerollCount(rerolled));
        // 进度「赌徒的狂热」：超导魔晶洗练搏出 SSS。
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && "SSS".equals(view.gradeName())) {
            MagicProgression.awardAdvancement(serverPlayer, "affix_sss");
        }
        announceProtection(player);
    }

    /** 词条重铸符：不碰品级与流派，只换主词条 / 副词条的内容。 */
    private void reforge(Player player, ItemStack equipment) {
        if (!EquipmentAffixSystem.hasAffix(equipment)) {
            message(player, "message.dimensional_mana.weapon_workbench.no_affix");
            return;
        }
        beforeReroll = equipment.copy();
        consumeOneMaterial();
        ItemStack reforged = EquipmentAffixSystem.reforgeAffixes(equipment, player.getRandom());
        container.setItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT, reforged);
        lastResult = reforged.copy();
        EquipmentAffixSystem.refreshPlayer(player);
        playSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.12F);
        EquipmentAffixSystem.View view = EquipmentAffixSystem.view(reforged);
        message(player, "message.dimensional_mana.weapon_workbench.reforged", view.gradeName());
        announceProtection(player);
    }

    /** 洗出目标词条时给一声提示音 + 一条飘字，让玩家立刻知道「锻造已经被锁住了」。 */
    private void announceProtection(Player player) {
        ItemStack equipment = container.getItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT);
        armed = !lastResult.isEmpty() && ItemStack.matches(equipment, lastResult) && protectedNow(equipment);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            WorkbenchGuardPayload.send(serverPlayer, containerId, protectionActive());
        }
        if (!armed) return;
        playSound(player, SoundEvents.PLAYER_LEVELUP, 1.45F);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.weapon_workbench.lock_hit"), false);
    }

    /**
     * 找零去向：优先回到材料格（玩家下次还要用魔晶），塞不进去再进背包，背包也满就掉在脚边。
     */
    private void give(Player player, ItemStack stack) {
        ItemStack material = container.getItem(WeaponWorkbenchBlockEntity.MATERIAL_SLOT);
        if (material.isEmpty()) {
            container.setItem(WeaponWorkbenchBlockEntity.MATERIAL_SLOT, stack.copy());
            return;
        }
        if (ItemStack.isSameItemSameComponents(material, stack) && material.getCount() < material.getMaxStackSize()) {
            material.grow(1);
            container.setItem(WeaponWorkbenchBlockEntity.MATERIAL_SLOT, material);
            return;
        }
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    // ------------------------------------------------------------------
    // 词条锁定（防手滑）：勾选状态 + 命中判定 + 放弃本次结果
    // ------------------------------------------------------------------

    /** 勾选的主词条键；没勾就是空串。 */
    public String lockedMain() {
        return lockedMain;
    }

    /** 勾选的副词条键（只读）。 */
    public List<String> lockedSubs() {
        return List.copyOf(lockedSubs);
    }

    /** 有没有勾过东西。 */
    public boolean hasLocks() {
        return !lockedMain.isEmpty() || !lockedSubs.isEmpty();
    }

    /**
     * 更新勾选目标。客户端在勾选框变化时先本地改一份再发同一份给服务端，两边永远一致。
     *
     * <p>这里顺手做一次校验：主词条只留一条、副词条最多 {@link #MAX_LOCKED_SUBS} 条、
     * 认不出来的 id 一律丢掉，所以客户端发什么上来都不会把服务端状态弄脏。</p>
     */
    public void applyLocks(String mainKey, List<String> subKeys) {
        lockedMain = mainKey != null && EquipmentAffixSystem.isKnownLockKey(mainKey) ? mainKey : "";
        lockedSubs.clear();
        if (subKeys != null) {
            for (String key : subKeys) {
                if (lockedSubs.size() >= MAX_LOCKED_SUBS) break;
                if (key == null || lockedSubs.contains(key)) continue;
                if (!EquipmentAffixSystem.isKnownLockKey(key)) continue;
                lockedSubs.add(key);
            }
        }
        // 勾选一变，上一次的命中就不作数了：这正是「手动点掉勾选」解除保护的入口。
        armed = false;
    }

    /**
     * 防手滑保护是否生效。
     *
     * <p>三个条件缺一不可：上一次洗练确实洗出了全部目标、槽里还是那次洗出来的武备、
     * 勾选还是原来那些。任何一个不成立保护就自动失效，所以玩家拿走装备、改勾选、
     * 或者点了「放弃本次结果」，锻造按钮都会立刻恢复。</p>
     */
    public boolean protectionActive() {
        if (!armed || lastResult.isEmpty()) return false;
        ItemStack current = equipment();
        if (current.isEmpty() || !ItemStack.matches(current, lastResult)) return false;
        return protectedNow(current);
    }

    private boolean protectedNow(ItemStack equipment) {
        if (equipment == null || equipment.isEmpty()) return false;
        return EquipmentAffixSystem.matchesLocks(equipment, lockedMain, lockedSubs);
    }

    /** 这条词条键是不是本次勾选的目标（界面里标红加粗用）。 */
    public boolean isLockedTarget(String key) {
        if (key == null) return false;
        return key.equals(lockedMain) || lockedSubs.contains(key);
    }

    /**
     * 「放弃本次结果」：把武备还原成这次洗练之前的样子。
     *
     * <p>材料不退（已经消耗掉了），这样「洗坏了就退回原样」的代价始终是那颗材料；
     * 洗练次数也跟着还原，所以放弃一次不会白赚保底。勾选保留不动：玩家多半还想要
     * 那几条词条，而且保留勾选也保证客户端与服务端的状态永远一致。</p>
     */
    private void discardResult(Player player) {
        ItemStack current = container.getItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT);
        if (!protectionActive()) {
            message(player, "message.dimensional_mana.weapon_workbench.discard_not_protected");
            return;
        }
        if (beforeReroll.isEmpty() || current.isEmpty() || !ItemStack.matches(current, lastResult)) {
            // 槽里的东西已经不是我们洗出来的那件了：宁可什么都不做，也不要把玩家的装备冲掉。
            armed = false;
            message(player, "message.dimensional_mana.weapon_workbench.discard_stale");
            syncGuard(player);
            return;
        }
        container.setItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT, beforeReroll.copy());
        beforeReroll = ItemStack.EMPTY;
        lastResult = ItemStack.EMPTY;
        armed = false;
        EquipmentAffixSystem.refreshPlayer(player);
        playSound(player, SoundEvents.ANVIL_LAND, 0.72F);
        message(player, "message.dimensional_mana.weapon_workbench.discarded");
        syncGuard(player);
    }

    /** 把「保护是否生效」推给客户端；客户端只拿它决定按钮灰不灰、感叹号亮不亮。 */
    private void syncGuard(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            WorkbenchGuardPayload.send(serverPlayer, containerId, protectionActive());
        }
    }

    /**
     * 关界面：勾选与还原快照都跟着丢掉（不存盘、不跨界面泄漏）。
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        lockedMain = "";
        lockedSubs.clear();
        armed = false;
        beforeReroll = ItemStack.EMPTY;
        lastResult = ItemStack.EMPTY;
    }

    private static void playSound(Player player, net.minecraft.sounds.SoundEvent event, float pitch) {
        player.level().playSound(null, player.blockPosition(), event, SoundSource.BLOCKS, 0.8F, pitch);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        int machineSlots = WeaponWorkbenchBlockEntity.SLOT_COUNT;
        if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (EquipmentAffixSystem.isEquipment(source)) {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else if (ArmamentForge.acceptsMaterial(source)) {
            if (!moveItemStackTo(source, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            int inventoryStart = machineSlots;
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
                ContainerLevelAccess.create(level, pos), player,
                GeneratedMod.MOD_BLOCKS.get(WeaponWorkbenchBlock.BLOCK_ID).get()), true);
    }

    /** 界面里读装备词条用：直接拿服务端同步过来的槽位内容，不需要额外的数据包。 */
    public ItemStack equipment() {
        return container.getItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT);
    }

    public ItemStack material() {
        return container.getItem(WeaponWorkbenchBlockEntity.MATERIAL_SLOT);
    }
}
