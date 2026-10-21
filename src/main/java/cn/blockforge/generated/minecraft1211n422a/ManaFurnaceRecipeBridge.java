package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Mirrors every standard smelting recipe into both custom furnace recipe types.
 * This also covers ores added by other mods, provided they use the normal
 * minecraft:smelting recipe type.
 */
public final class ManaFurnaceRecipeBridge {
    private static final double MANA_FORGE_TIME_MULTIPLIER = 0.8D;
    private static final double PROFESSIONAL_TIME_MULTIPLIER = 0.3D;

    private ManaFurnaceRecipeBridge() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        rebuild(event.getServer().getRecipeManager(), event.getServer().registryAccess());
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        rebuild(event.getPlayerList().getServer().getRecipeManager(),
                event.getPlayerList().getServer().registryAccess());
    }

    private static void rebuild(RecipeManager manager, net.minecraft.core.HolderLookup.Provider registries) {
        Collection<RecipeHolder<?>> loaded = new ArrayList<>(manager.getRecipes());
        List<RecipeHolder<?>> rebuilt = new ArrayList<>(loaded.size() * 3);

        for (RecipeHolder<?> holder : loaded) {
            ResourceLocation id = holder.id();
            if (id.getNamespace().equals(GeneratedMod.MOD_ID)
                    && id.getPath().startsWith("compat/standard_smelting/")) {
                continue;
            }
            rebuilt.add(holder);
        }

        List<RecipeHolder<SmeltingRecipe>> standardRecipes = manager.getAllRecipesFor(RecipeType.SMELTING);
        for (RecipeHolder<SmeltingRecipe> holder : standardRecipes) {
            SmeltingRecipe recipe = holder.value();
            addMirroredRecipe(rebuilt, holder.id(), recipe, ManaCookingRecipe.FurnaceKind.MANA_FORGE,
                    MANA_FORGE_TIME_MULTIPLIER, registries);
            addMirroredRecipe(rebuilt, holder.id(), recipe, ManaCookingRecipe.FurnaceKind.PROFESSIONAL,
                    PROFESSIONAL_TIME_MULTIPLIER, registries);
        }

        manager.replaceRecipes(rebuilt);
    }

    private static void addMirroredRecipe(List<RecipeHolder<?>> recipes, ResourceLocation sourceId,
                                          AbstractCookingRecipe source, ManaCookingRecipe.FurnaceKind kind,
                                          double timeMultiplier, net.minecraft.core.HolderLookup.Provider registries) {
        String furnacePath = kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE ? "mana_forge" : "professional";
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
                "compat/standard_smelting/" + furnacePath + "/" + sourceId.getNamespace() + "/" + sourceId.getPath());
        int cookingTime = Math.max(1, (int) Math.ceil(source.getCookingTime() * timeMultiplier));
        ManaCookingRecipe mirrored = new ManaCookingRecipe(
                kind,
                source.getGroup(),
                source.category(),
                source.getIngredients().getFirst(),
                source.getResultItem(registries).copy(),
                source.getExperience(),
                cookingTime
        );
        recipes.add(new RecipeHolder<>(id, mirrored));
    }
}
