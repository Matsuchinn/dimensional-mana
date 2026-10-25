package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 加料食物：卢梅莉亚粉末 + 任意一种食物 → 带上“+”的食物。
 *
 * <p>原版配方没法表达“任意食物”这种动态产物，所以这是个特制配方：只看格子里
 * 是不是“恰好一份粉末 + 恰好一份食物”，产物按那份食物现场复制出来，
 * 饱食度与饱和度各翻一倍，并打上一个隐藏标记，吃的时候再补 10% 魔素与 10% 生命。</p>
 */
public final class PlantFood extends CustomRecipe {
    /** 食物上的隐藏标记：吃了要额外补魔素与生命。 */
    public static final String TAG = "dmFortifiedFood";

    public PlantFood(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return scan(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack food = scan(input);
        return food == null ? ItemStack.EMPTY : fortify(food);
    }

    /** 格子里恰好“一份粉末 + 一份食物”时返回那份食物，否则返回 null。 */
    private static ItemStack scan(CraftingInput input) {
        ItemStack powder = ItemStack.EMPTY;
        ItemStack food = ItemStack.EMPTY;
        int filled = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (++filled > 2) return null;
            if (stack.is(GeneratedMod.MOD_ITEMS.get(ManaPlants.LUMELIA_POWDER).get())) {
                if (!powder.isEmpty()) return null;
                powder = stack;
            } else if (stack.has(DataComponents.FOOD) && !isFortified(stack)) {
                if (!food.isEmpty()) return null;
                food = stack;
            } else {
                return null;
            }
        }
        return powder.isEmpty() || food.isEmpty() ? null : food;
    }

    /** 已经加过料的不再重复加。 */
    private static boolean isFortified(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(TAG);
    }

    /** 把一份食物变成“+”版本：饱食度、饱和度翻倍，名字加后缀，并打上隐藏标记。 */
    public static ItemStack fortify(ItemStack food) {
        FoodProperties properties = food.get(DataComponents.FOOD);
        if (properties == null) return ItemStack.EMPTY;
        ItemStack result = food.copyWithCount(1);
        result.set(DataComponents.FOOD, new FoodProperties(
                properties.nutrition() * 2,
                properties.saturation() * 2.0F,
                properties.canAlwaysEat(),
                properties.eatSeconds(),
                properties.usingConvertsTo(),
                properties.effects()));
        result.set(DataComponents.ITEM_NAME, food.getHoverName().copy().append("+"));
        CompoundTag tag = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(TAG, true);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return GeneratedMod.PLANT_FOOD_SERIALIZER.get();
    }

    /** 特制配方不需要任何字段，只要一个能读能同步的空壳。 */
    public static final class Serializer implements RecipeSerializer<PlantFood> {
        public static final MapCodec<PlantFood> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CraftingBookCategory.CODEC.optionalFieldOf("category", CraftingBookCategory.MISC)
                        .forGetter(PlantFood::category)
        ).apply(instance, PlantFood::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, PlantFood> STREAM_CODEC =
                StreamCodec.composite(CraftingBookCategory.STREAM_CODEC, PlantFood::category, PlantFood::new);

        @Override
        public MapCodec<PlantFood> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PlantFood> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
