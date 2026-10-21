package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class ManaFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
    private final ManaCookingRecipe.FurnaceKind kind;

    public ManaFurnaceBlockEntity(ManaCookingRecipe.FurnaceKind kind, BlockPos pos, BlockState state) {
        super(blockEntityType(kind), pos, state, recipeType(kind));
        this.kind = kind;
    }

    private static BlockEntityType<?> blockEntityType(ManaCookingRecipe.FurnaceKind kind) {
        return kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE
                ? GeneratedMod.MANA_FORGE_BLOCK_ENTITY.get()
                : GeneratedMod.PROFESSIONAL_BLOCK_ENTITY.get();
    }

    private static RecipeType<ManaCookingRecipe> recipeType(ManaCookingRecipe.FurnaceKind kind) {
        return ManaCookingRecipe.recipeType(kind);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE
                ? "container.dimensional_mana.mana_forge"
                : "container.dimensional_mana.professional_crystal_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ManaFurnaceMenu(containerId, inventory, this, dataAccess, kind);
    }
}
