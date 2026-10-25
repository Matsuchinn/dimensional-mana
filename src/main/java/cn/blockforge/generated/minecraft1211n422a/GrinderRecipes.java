package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 研磨器的输入输出表。游戏里的研磨逻辑和 JEI 配方页共用这一份数据，
 * 以后调整研磨配方只需要改这里，避免两边对不上。
 */
public final class GrinderRecipes {
    /** 单条研磨配方；tier 表示需要的研磨器等级（0 表示木研磨器即可）。 */
    public record Recipe(int tier, ItemStack input, ItemStack output) {
        public Recipe copy() {
            return new Recipe(tier, input.copy(), output.copy());
        }
    }

    private static final String[] GRADES = {"lesser", "medium", "advanced", "supreme", "special"};

    private static final List<Recipe> RECIPES = createRecipes();

    private static List<Recipe> createRecipes() {
        List<Recipe> recipes = new ArrayList<>();
        recipes.add(new Recipe(0, new ItemStack(Items.LAPIS_LAZULI),
                new ItemStack(GeneratedMod.MOD_ITEMS.get("lapis_dust").get(), 6)));
        recipes.add(new Recipe(0, new ItemStack(Items.AMETHYST_SHARD),
                new ItemStack(GeneratedMod.MOD_ITEMS.get("amethyst_dust").get(), 2)));
        for (int tier = 0; tier < GRADES.length; tier++) {
            String grade = GRADES[tier];
            recipes.add(new Recipe(tier,
                    new ItemStack(GeneratedMod.MOD_ITEMS.get(grade + "_mana_crystal").get()),
                    new ItemStack(GeneratedMod.MOD_ITEMS.get(grade + "_mana_crystal_dust").get(), 4)));
        }
        // 植物粉末：三种植物的叶子在任意研磨器里都能磨成粉，木研磨器就够用。
        recipes.add(new Recipe(0, new ItemStack(GeneratedMod.MOD_ITEMS.get(ManaPlants.Kind.RESTELIA.id()).get()),
                new ItemStack(GeneratedMod.MOD_ITEMS.get(ManaPlants.RESTELIA_POWDER).get())));
        recipes.add(new Recipe(0, new ItemStack(GeneratedMod.MOD_ITEMS.get(ManaPlants.Kind.LEVILIA.id()).get()),
                new ItemStack(GeneratedMod.MOD_ITEMS.get(ManaPlants.LEVILIA_POWDER).get())));
        return List.copyOf(recipes);
    }

    public static List<Recipe> all() {
        return RECIPES;
    }

    public static Recipe find(ItemStack input) {
        if (input.isEmpty()) return null;
        for (Recipe recipe : RECIPES) {
            if (input.is(recipe.input().getItem())) return recipe;
        }
        return null;
    }

    private GrinderRecipes() {
    }
}
