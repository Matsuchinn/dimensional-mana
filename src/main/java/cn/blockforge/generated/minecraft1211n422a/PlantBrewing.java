package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * 植物的酿造配方。
 *
 * <p>两条药剂配方是“魔素调和剂 + 植物粉末”，产物固定；强效补魔剂是多态的
 * ——产物是“原来那瓶补魔剂 + 15% 恢复量 + 魔力充盈”，所以用动态配方接口，
 * 直接把输入那瓶复制一份改数值，不再为每级补魔剂各开一个物品。</p>
 */
public final class PlantBrewing {
    public static void register(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();
        builder.addRecipe(new Draught(ManaPlants.RESTELIA_POWDER, ManaPlants.HEALING_DRAUGHT));
        builder.addRecipe(new Draught(ManaPlants.LUMELIA_POWDER, ManaPlants.FULL_HEALING_DRAUGHT));
        builder.addRecipe(new Fortify());
    }

    /** 魔素调和剂 + 粉末 → 药剂。 */
    private record Draught(String powderId, String draughtId) implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack input) {
            return input.is(GeneratedMod.MOD_ITEMS.get("mana_harmonizer").get());
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            return ingredient.is(GeneratedMod.MOD_ITEMS.get(powderId).get());
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            if (!isInput(input) || !isIngredient(ingredient)) return ItemStack.EMPTY;
            return new ItemStack(GeneratedMod.MOD_ITEMS.get(draughtId).get());
        }
    }

    /** 任意补魔剂 + 蕾薇莉亚粉末 → 同一瓶补魔剂，恢复量 +15%，多一个魔力充盈。 */
    private record Fortify() implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack input) {
            return input.getItem() instanceof ManaPotionItem && !input.has(GeneratedMod.POTION_BOOST);
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            return ingredient.is(GeneratedMod.MOD_ITEMS.get(ManaPlants.LEVILIA_POWDER).get());
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            if (!isInput(input) || !isIngredient(ingredient)) return ItemStack.EMPTY;
            return fortifiedStack(input);
        }
    }

    /** 强化后的补魔剂：同一瓶 + 15% 恢复量 + 名字后面一个“+”。JEI 展示也用这一份。 */
    public static ItemStack fortifiedStack(ItemStack input) {
        ItemStack result = input.copyWithCount(1);
        result.set(GeneratedMod.POTION_BOOST.get(), FORTIFY_BONUS);
        result.set(DataComponents.ITEM_NAME, input.getHoverName().copy().append("+"));
        return result;
    }

    /** 强化后额外恢复的比例：+15%。 */
    public static final float FORTIFY_BONUS = 0.15F;

    /** 酿造台里能当“补魔剂”底料的物品集合（用于 JEI 与提示）。 */
    public static boolean isManaPotion(Item item) {
        return item instanceof ManaPotionItem;
    }

    private PlantBrewing() {
    }
}
