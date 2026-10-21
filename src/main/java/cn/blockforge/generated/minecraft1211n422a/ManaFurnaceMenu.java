package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.crafting.RecipeType;

public final class ManaFurnaceMenu extends AbstractFurnaceMenu {
    private final ManaCookingRecipe.FurnaceKind kind;
    private final ContainerData furnaceData;

    public ManaFurnaceMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ManaCookingRecipe.FurnaceKind.MANA_FORGE);
    }

    public ManaFurnaceMenu(int containerId, Inventory inventory, ManaCookingRecipe.FurnaceKind kind) {
        this(containerId, inventory, new SimpleContainer(3), new SimpleContainerData(4), kind);
    }

    public ManaFurnaceMenu(int containerId, Inventory inventory, Container container, ContainerData data,
                           ManaCookingRecipe.FurnaceKind kind) {
        super(menuType(kind), recipeType(kind), RecipeBookType.FURNACE, containerId, inventory, container, data);
        this.kind = kind;
        this.furnaceData = data;
    }

    private static net.minecraft.world.inventory.MenuType<ManaFurnaceMenu> menuType(ManaCookingRecipe.FurnaceKind kind) {
        return kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE
                ? GeneratedMod.MANA_FORGE_MENU.get()
                : GeneratedMod.PROFESSIONAL_FURNACE_MENU.get();
    }

    private static RecipeType<ManaCookingRecipe> recipeType(ManaCookingRecipe.FurnaceKind kind) {
        return ManaCookingRecipe.recipeType(kind);
    }

    public ManaCookingRecipe.FurnaceKind kind() {
        return kind;
    }

    public int cookingProgressTicks() {
        return furnaceData.get(2);
    }

    public int cookingTotalTicks() {
        return furnaceData.get(3);
    }

    public int remainingTicks() {
        return Math.max(0, cookingTotalTicks() - cookingProgressTicks());
    }
}
