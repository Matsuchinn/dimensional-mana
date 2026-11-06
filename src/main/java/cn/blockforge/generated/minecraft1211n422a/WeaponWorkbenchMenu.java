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

import java.util.List;

/**
 * 武备加工台的菜单：两个格子 + 一个「锻造」按钮。
 *
 * <p>所有结算都在服务端做，客户端只负责画：点按钮时调
 * {@link #clickMenuButton(Player, int)}，真正的扣料、升级、找零都在这里完成。</p>
 */
public final class WeaponWorkbenchMenu extends AbstractContainerMenu {
    /** 「锻造」按钮的 id。 */
    public static final int BUTTON_FORGE = 0;

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
        if (id != BUTTON_FORGE) return false;
        if (player.level().isClientSide) return true;
        forge(player);
        return true;
    }

    /** 点「锻造」时的完整结算。 */
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
        consumeOneMaterial();
        ItemStack rerolled = EquipmentAffixSystem.rerollAffixes(equipment, player.getRandom());
        container.setItem(WeaponWorkbenchBlockEntity.EQUIPMENT_SLOT, rerolled);
        EquipmentAffixSystem.refreshPlayer(player);
        playSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.35F);
        EquipmentAffixSystem.View view = EquipmentAffixSystem.view(rerolled);
        message(player, "message.dimensional_mana.weapon_workbench.rerolled",
                view.gradeName(), EquipmentAffixSystem.rerollCount(rerolled));
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
