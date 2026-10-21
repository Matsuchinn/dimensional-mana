package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public final class ManaCookingRecipe extends AbstractCookingRecipe {
    public enum FurnaceKind {
        MANA_FORGE,
        PROFESSIONAL
    }

    private final FurnaceKind kind;

    public ManaCookingRecipe(FurnaceKind kind, String group, CookingBookCategory category, Ingredient ingredient,
                             ItemStack result, float experience, int cookingTime) {
        super(recipeType(kind), group, category, ingredient, result, experience, cookingTime);
        this.kind = kind;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(kind == FurnaceKind.MANA_FORGE
                ? GeneratedMod.MOD_BLOCKS.get("mana_forge").get()
                : GeneratedMod.MOD_BLOCKS.get("professional_crystal_furnace").get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return kind == FurnaceKind.MANA_FORGE
                ? GeneratedMod.MANA_FORGE_RECIPE_SERIALIZER.get()
                : GeneratedMod.PROFESSIONAL_RECIPE_SERIALIZER.get();
    }

    public static RecipeType<ManaCookingRecipe> recipeType(FurnaceKind kind) {
        return kind == FurnaceKind.MANA_FORGE
                ? GeneratedMod.MANA_FORGE_RECIPE_TYPE.get()
                : GeneratedMod.PROFESSIONAL_RECIPE_TYPE.get();
    }
}
