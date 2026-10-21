package cn.blockforge.generated.minecraft1211n422a;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * JEI 可选接口：把模组自己的研磨器、魔晶加工台、魔锻炉和专业魔晶锻造炉
 * 注册成独立的配方分类，这样 JEI 里能直接查，Just Enough Crafting Tree（JECT）
 * 也能顺着“材料 → 产物”把这些机器画进合成树里。
 * 模组不强制要求安装 JEI；没有 JEI 时这个类不会被加载。
 */
@JeiPlugin
public final class DimensionalManaJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "jei_plugin");

    /** 研磨器（木/石/金/铁/钻石/下界合金共用一张配方表）。 */
    public static final RecipeType<GrinderRecipes.Recipe> GRINDER_TYPE = RecipeType.create(
            GeneratedMod.MOD_ID, "grinding", GrinderRecipes.Recipe.class);
    /** 魔晶加工台。 */
    public static final RecipeType<CrystalProcessorBlockEntity.ProcessRecipe> CRYSTAL_PROCESSING_TYPE =
            RecipeType.create(GeneratedMod.MOD_ID, "crystal_processing",
                    CrystalProcessorBlockEntity.ProcessRecipe.class);
    /** 魔锻炉；路径带 smelting，合成树会把它归到“冶炼类”。 */
    public static final RecipeType<ManaCookingRecipe> MANA_FORGE_TYPE = RecipeType.create(
            GeneratedMod.MOD_ID, "mana_forge_smelting", ManaCookingRecipe.class);
    /** 专业魔晶锻造炉。 */
    public static final RecipeType<ManaCookingRecipe> PROFESSIONAL_TYPE = RecipeType.create(
            GeneratedMod.MOD_ID, "professional_smelting", ManaCookingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new DimensionalManaJeiCategories.GrinderCategory(guiHelper),
                new DimensionalManaJeiCategories.CrystalProcessorCategory(guiHelper),
                new DimensionalManaJeiCategories.CookingCategory(guiHelper, MANA_FORGE_TYPE,
                        "jei.dimensional_mana.mana_forge", "mana_forge"),
                new DimensionalManaJeiCategories.CookingCategory(guiHelper, PROFESSIONAL_TYPE,
                        "jei.dimensional_mana.professional_furnace", "professional_crystal_furnace"));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(GRINDER_TYPE, GrinderRecipes.all());
        registration.addRecipes(CRYSTAL_PROCESSING_TYPE, CrystalProcessorBlockEntity.recipes());
        registration.addRecipes(MANA_FORGE_TYPE,
                ownCookingRecipes(GeneratedMod.MANA_FORGE_RECIPE_TYPE.get()));
        registration.addRecipes(PROFESSIONAL_TYPE,
                ownCookingRecipes(GeneratedMod.PROFESSIONAL_RECIPE_TYPE.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 两台自定义炉子同时作为原版熔炼的机器入口，其他模组的矿石也能在 JEI 里看到它们。
        registration.addRecipeCatalyst(new ItemStack(GeneratedMod.MOD_ITEMS.get("mana_forge").get()),
                RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(new ItemStack(GeneratedMod.MOD_ITEMS.get("professional_crystal_furnace").get()),
                RecipeTypes.SMELTING);

        for (String grinder : List.of("wooden_grinder", "stone_grinder", "golden_grinder",
                "iron_grinder", "diamond_grinder", "netherite_grinder")) {
            registration.addRecipeCatalyst(new ItemStack(GeneratedMod.MOD_ITEMS.get(grinder).get()), GRINDER_TYPE);
        }
        registration.addRecipeCatalyst(
                new ItemStack(GeneratedMod.MOD_ITEMS.get("crystal_processor").get()), CRYSTAL_PROCESSING_TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(GeneratedMod.MOD_ITEMS.get("mana_forge").get()), MANA_FORGE_TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(GeneratedMod.MOD_ITEMS.get("professional_crystal_furnace").get()), PROFESSIONAL_TYPE);
    }

    /**
     * 只取数据包里真正由本模组定义的炉子配方。
     * 兼容层会为所有原版熔炼配方动态生成 compat/standard_smelting 镜像，
     * 那些配方已经通过“原版熔炼 + 机器入口”的方式展示，不再重复塞进专属分类。
     */
    private static List<ManaCookingRecipe> ownCookingRecipes(
            net.minecraft.world.item.crafting.RecipeType<ManaCookingRecipe> type) {
        RecipeManager manager = recipeManager();
        if (manager == null) return List.of();
        return manager.getAllRecipesFor(type).stream()
                .filter(DimensionalManaJeiPlugin::isOwnRecipe)
                .map(RecipeHolder::value)
                .toList();
    }

    private static boolean isOwnRecipe(RecipeHolder<?> holder) {
        ResourceLocation id = holder.id();
        return id.getNamespace().equals(GeneratedMod.MOD_ID) && !id.getPath().startsWith("compat/");
    }

    /**
     * 客户端用当前世界的配方表（单人和联机都能拿到服务器同步下来的配方）；
     * 专用服务器上则退回服务器自己的配方表。都没有时返回 null，注册空列表即可。
     */
    private static RecipeManager recipeManager() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.level != null) {
                return minecraft.level.getRecipeManager();
            }
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getRecipeManager();
    }
}
