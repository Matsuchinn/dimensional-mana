package cn.blockforge.generated.minecraft1211n422a;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JEI 里的四个模组专属加工分类：研磨器、魔晶加工台、魔锻炉、专业魔晶锻造炉。
 * 配方槽位按 INPUT / OUTPUT 两种角色摆放，JEI 和合成树（JECT）都靠这个判断“谁产出谁”。
 */
public final class DimensionalManaJeiCategories {
    private DimensionalManaJeiCategories() {
    }

    private static ItemStack modStack(String id) {
        return new ItemStack(GeneratedMod.MOD_ITEMS.get(id).get());
    }

    private static String seconds(int ticks) {
        double value = ticks / 20.0D;
        if (value == Math.floor(value)) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 研磨器：物品打开菜单，配方是固定表。 */
    public static final class GrinderCategory extends AbstractRecipeCategory<GrinderRecipes.Recipe> {
        public GrinderCategory(IGuiHelper guiHelper) {
            super(DimensionalManaJeiPlugin.GRINDER_TYPE,
                    Component.translatable("jei.dimensional_mana.grinder"),
                    guiHelper.createDrawableItemStack(modStack("iron_grinder")), 100, 62);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, GrinderRecipes.Recipe recipe, IFocusGroup focuses) {
            builder.addInputSlot(8, 14).setStandardSlotBackground().addItemStack(recipe.input().copy());
            builder.addOutputSlot(68, 14).setOutputSlotBackground().addItemStack(recipe.output().copy());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, GrinderRecipes.Recipe recipe,
                                       IFocusGroup focuses) {
            builder.addRecipeArrow().setPosition(34, 13);
            builder.addText(Component.translatable("jei.dimensional_mana.grinder_time"), 4, 40);
            builder.addText(Component.translatable("jei.dimensional_mana.grinder_tier", recipe.tier() + 1), 4, 50);
        }
    }

    /** 魔晶加工台：有九宫格固定摆法和无序配方两种。 */
    public static final class CrystalProcessorCategory
            extends AbstractRecipeCategory<CrystalProcessorBlockEntity.ProcessRecipe> {
        public CrystalProcessorCategory(IGuiHelper guiHelper) {
            super(DimensionalManaJeiPlugin.CRYSTAL_PROCESSING_TYPE,
                    Component.translatable("jei.dimensional_mana.crystal_processor"),
                    guiHelper.createDrawableItemStack(modStack("crystal_processor")), 168, 78);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CrystalProcessorBlockEntity.ProcessRecipe recipe,
                              IFocusGroup focuses) {
            if (recipe.pattern().isEmpty()) {
                builder.setShapeless();
                int index = 0;
                for (CrystalProcessorBlockEntity.IngredientStack ingredient : recipe.inputs()) {
                    builder.addInputSlot(6 + (index % 3) * 18, 6 + (index / 3) * 18)
                            .setStandardSlotBackground()
                            .addItemStacks(ingredientStacks(ingredient.itemId(), ingredient.count()));
                    index++;
                }
            } else {
                for (int index = 0; index < 9; index++) {
                    String id = recipe.pattern().get(index);
                    if (id.isBlank()) continue;
                    builder.addInputSlot(6 + (index % 3) * 18, 6 + (index / 3) * 18)
                            .setStandardSlotBackground()
                            .addItemStacks(ingredientStacks(id, 1));
                }
            }
            int[] outputX = {86, 108, 130, 152};
            for (int index = 0; index < recipe.outputs().size() && index < outputX.length; index++) {
                CrystalProcessorBlockEntity.OutputStack output = recipe.outputs().get(index);
                builder.addOutputSlot(outputX[index], 22)
                        .setOutputSlotBackground()
                        .addItemStack(outputStack(output.itemId(), output.count()));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, CrystalProcessorBlockEntity.ProcessRecipe recipe,
                                       IFocusGroup focuses) {
            builder.addRecipeArrow().setPosition(64, 21);
            builder.addText(Component.translatable("jei.dimensional_mana.process_time",
                    seconds(recipe.processTicks())), 6, 58);
            builder.addText(Component.translatable("jei.dimensional_mana.mana_cost", recipe.manaCost()), 6, 68);
        }

        private static ItemStack outputStack(String id, int count) {
            return id.startsWith("minecraft:")
                    ? new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count)
                    : new ItemStack(GeneratedMod.MOD_ITEMS.get(id).get(), count);
        }

        private static List<ItemStack> ingredientStacks(String id, int count) {
            if (id == null || id.isBlank()) return List.of();
            if ("#magic_fragment".equals(id)) {
                List<ItemStack> stacks = new ArrayList<>();
                for (String grade : GeneratedMod.FRAGMENT_GRADES) {
                    for (ElementType element : ElementType.values()) {
                        stacks.add(modStack(element.fragmentId(grade)));
                    }
                    for (String school : GeneratedMod.SCHOOL_IDS) {
                        stacks.add(modStack(grade + "_" + school + "_school_fragment"));
                    }
                }
                return stacks;
            }
            if (id.startsWith("#element_fragment:")) {
                String elementId = id.substring("#element_fragment:".length());
                List<ItemStack> stacks = new ArrayList<>();
                for (String grade : List.of("lesser", "medium", "advanced", "supreme")) {
                    String fragmentId = "arcane".equals(elementId) ? grade + "_arcane_fragment"
                            : grade + "_" + elementId + "_fragment";
                    stacks.add(new ItemStack(GeneratedMod.MOD_ITEMS.get(fragmentId).get(), count));
                }
                return stacks;
            }
            if ("#special_element_fragment".equals(id)) {
                List<ItemStack> stacks = new ArrayList<>();
                for (ElementType element : ElementType.values()) {
                    stacks.add(new ItemStack(GeneratedMod.MOD_ITEMS.get(element.fragmentId("special")).get(), count));
                }
                return stacks;
            }
            if ("#special_school_fragment".equals(id)) {
                List<ItemStack> stacks = new ArrayList<>();
                for (String school : GeneratedMod.SCHOOL_IDS) {
                    stacks.add(new ItemStack(
                            GeneratedMod.MOD_ITEMS.get("special_" + school + "_school_fragment").get(), count));
                }
                return stacks;
            }
            if ("#element_crystal".equals(id)) {
                List<ItemStack> stacks = new ArrayList<>();
                for (ElementType element : ElementType.values()) stacks.add(modStack(element.id() + "_element_crystal"));
                stacks.add(modStack(MagicEquipment.ARCANE + "_element_crystal"));
                return stacks;
            }
            if ("#enchanted_book_thorns_3".equals(id)) {
                return List.of(new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK, count));
            }
            ItemStack stack = id.startsWith("minecraft:")
                    ? new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count)
                    : new ItemStack(GeneratedMod.MOD_ITEMS.get(id).get(), count);
            return List.of(stack);
        }
    }

    /** 魔锻炉与专业魔晶锻造炉共用同一套布局，只是标题、图标和产物来源不同。 */
    public static final class CookingCategory extends AbstractRecipeCategory<ManaCookingRecipe> {
        public CookingCategory(IGuiHelper guiHelper, RecipeType<ManaCookingRecipe> recipeType,
                               String titleKey, String iconItem) {
            super(recipeType, Component.translatable(titleKey),
                    guiHelper.createDrawableItemStack(modStack(iconItem)), 110, 62);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ManaCookingRecipe recipe, IFocusGroup focuses) {
            builder.addInputSlot(8, 14).setStandardSlotBackground().addIngredients(recipe.getIngredients().getFirst());
            builder.addOutputSlot(62, 14).setOutputSlotBackground().addItemStack(recipe.getResultItem(null).copy());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, ManaCookingRecipe recipe, IFocusGroup focuses) {
            builder.addRecipeArrow().setPosition(34, 13);
            builder.addText(Component.translatable("jei.dimensional_mana.process_time",
                    seconds(recipe.getCookingTime())), 6, 40);
            builder.addText(Component.translatable("jei.dimensional_mana.cooking_fuel"), 6, 50);
        }
    }
}
