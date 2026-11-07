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
    /**
     * 单条研磨配方；tier 表示需要的研磨器等级（0 表示木研磨器即可）。
     *
     * <p>{@code output} 里的数量是「一次最多出多少」，{@code minCount}～{@code maxCount}
     * 才是真正掷的范围。普通配方三个数一样，出货数量固定；批量出货的配方（强化石 → 石粉）
     * 才会写成区间，让同样的投入有一点随机手感。</p>
     */
    public record Recipe(int tier, ItemStack input, ItemStack output, int minCount, int maxCount) {
        public Recipe(int tier, ItemStack input, ItemStack output) {
            this(tier, input, output, output.getCount(), output.getCount());
        }

        public Recipe copy() {
            return new Recipe(tier, input.copy(), output.copy(), minCount, maxCount);
        }

        /** 真正发货的数量：闭区间里掷一次。 */
        public int rollCount(net.minecraft.util.RandomSource random) {
            if (maxCount <= minCount) return minCount;
            return minCount + random.nextInt(maxCount - minCount + 1);
        }

        /** 真正发货的物品栈：数量按 {@link #rollCount} 掷，物品本身照配方的来。 */
        public ItemStack roll(net.minecraft.util.RandomSource random) {
            ItemStack stack = output.copy();
            stack.setCount(Math.max(1, rollCount(random)));
            return stack;
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
        // 武备强化石磨成粉：只有下界合金研磨器（tier 4，正好 10 秒 = 200 刻）磨得动，
        // 一颗强化石出 2～4 份粉。粉是词条重铸符的核心材料，所以这条配方是整条重铸线的入口。
        recipes.add(new Recipe(4,
                new ItemStack(GeneratedMod.MOD_ITEMS.get(ArmamentForge.ENHANCE_STONE).get()),
                new ItemStack(GeneratedMod.MOD_ITEMS.get(ArmamentForge.ENHANCE_STONE_DUST).get(), 4),
                2, 4));
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
