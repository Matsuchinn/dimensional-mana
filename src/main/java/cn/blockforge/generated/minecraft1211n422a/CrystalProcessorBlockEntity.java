package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CrystalProcessorBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int INPUT_START = 0;
    public static final int INPUT_END = 9;
    public static final int OUTPUT_START = 9;
    public static final int OUTPUT_END = 13;
    public static final int FUEL_SLOT = 13;
    public static final int SLOT_COUNT = 14;
    public static final int MAX_MANA = 20_000;
    public static final int FUEL_DIGEST_TICKS = 100;
    public static final int DEFAULT_PROCESS_TICKS = 80;

    private static final List<ProcessRecipe> RECIPES = createRecipes();

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int processProgress;
    private int processTotal = DEFAULT_PROCESS_TICKS;
    private int fuelProgress;
    private int mana;
    private String activeRecipe = "";

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> processProgress;
                case 1 -> processTotal;
                case 2 -> fuelProgress;
                case 3 -> FUEL_DIGEST_TICKS;
                case 4 -> mana;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> processProgress = value;
                case 1 -> processTotal = Math.max(1, value);
                case 2 -> fuelProgress = value;
                case 4 -> mana = Math.max(0, Math.min(MAX_MANA, value));
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public CrystalProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(GeneratedMod.CRYSTAL_PROCESSOR_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrystalProcessorBlockEntity processor) {
        boolean changed = processor.digestFuel();
        ProcessRecipe recipe = processor.findRecipe();
        String recipeId = recipe == null ? "" : recipe.id();
        if (!processor.activeRecipe.equals(recipeId)) {
            processor.activeRecipe = recipeId;
            processor.processProgress = 0;
            processor.processTotal = recipe == null ? DEFAULT_PROCESS_TICKS : recipe.processTicks();
            changed = true;
        }
        if (recipe != null && processor.mana >= recipe.manaCost() && processor.canFitOutputs(recipe)) {
            processor.processTotal = recipe.processTicks();
            processor.processProgress++;
            changed = true;
            if (processor.processProgress >= recipe.processTicks()) {
                processor.finish(recipe);
                if (!processor.isRemoved()) {
                    level.playSound(null, pos, GeneratedMod.CRYSTAL_PROCESS_COMPLETE.get(), SoundSource.BLOCKS, 0.9F,
                            0.92F + level.random.nextFloat() * 0.16F);
                }
            }
        } else if (processor.processProgress != 0) {
            processor.processProgress = 0;
            changed = true;
        }
        if (changed) processor.setChanged();
    }

    private boolean digestFuel() {
        ItemStack fuel = items.get(FUEL_SLOT);
        int value = fuelValue(fuel);
        if (value <= 0 || mana >= MAX_MANA) {
            if (fuelProgress != 0) {
                fuelProgress = 0;
                return true;
            }
            return false;
        }
        fuelProgress++;
        if (fuelProgress >= FUEL_DIGEST_TICKS) {
            fuel.shrink(1);
            mana = Math.min(MAX_MANA, mana + value);
            fuelProgress = 0;
        }
        return true;
    }

    private ProcessRecipe findRecipe() {
        for (ProcessRecipe recipe : RECIPES) if (matches(recipe)) return recipe;
        return null;
    }

    private boolean matches(ProcessRecipe recipe) {
        if (!recipe.pattern().isEmpty()) {
            for (int index = 0; index < 9; index++) {
                String expected = recipe.pattern().get(index);
                ItemStack stack = items.get(INPUT_START + index);
                if (expected.isBlank()) {
                    if (!stack.isEmpty()) return false;
                } else if (stack.isEmpty() || !matchesIngredient(stack, expected)) return false;
            }
            return true;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (IngredientStack ingredient : recipe.inputs()) counts.put(ingredient.itemId(), 0);
        for (int slot = INPUT_START; slot < INPUT_END; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            String matchedId = null;
            for (IngredientStack ingredient : recipe.inputs()) {
                if (matchesIngredient(stack, ingredient.itemId())) {
                    matchedId = ingredient.itemId();
                    break;
                }
            }
            if (matchedId == null) return false;
            counts.put(matchedId, counts.get(matchedId) + stack.getCount());
        }
        for (IngredientStack ingredient : recipe.inputs()) {
            if (counts.getOrDefault(ingredient.itemId(), 0) < ingredient.count()) return false;
        }
        return true;
    }

    private static boolean matchesIngredient(ItemStack stack, String ingredientId) {
        if (ingredientId == null || stack.isEmpty()) return false;
        if ("#magic_fragment".equals(ingredientId)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String path = id.getPath();
            return id.getNamespace().equals(GeneratedMod.MOD_ID) && stack.getItem() instanceof MagicFragmentItem
                    && !path.endsWith("_arcane_fragment");
        }
        // 元素结晶的“引子”：对应元素的任意非特级碎片，特级碎片不参与元素结晶合成。
        if (ingredientId.startsWith("#element_fragment:")) {
            String elementId = ingredientId.substring("#element_fragment:".length());
            if (!isElementFragment(stack, elementId)) return false;
            return !BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("special_");
        }
        if ("#special_element_fragment".equals(ingredientId)) {
            if (!(stack.getItem() instanceof MagicFragmentItem)) return false;
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            for (ElementType element : ElementType.values()) {
                if (path.equals("special_" + element.id() + "_fragment")) return true;
            }
            return false;
        }
        if ("#special_school_fragment".equals(ingredientId)) {
            if (!(stack.getItem() instanceof MagicFragmentItem)) return false;
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            for (String school : GeneratedMod.SCHOOL_IDS) {
                if (path.equals("special_" + school + "_school_fragment")) return true;
            }
            return false;
        }
        if ("#element_crystal".equals(ingredientId)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id.getNamespace().equals(GeneratedMod.MOD_ID) && stack.getItem() instanceof ElementCrystalItem;
        }
        if ("#enchanted_book_thorns_3".equals(ingredientId)) return isThornsThreeBook(stack);
        Item item = ingredientItem(ingredientId);
        return item != null && stack.is(item);
    }

    /** 该物品是不是指定元素（含奥术）的碎片；元素 id 与物品命名一一对应。 */
    private static boolean isElementFragment(ItemStack stack, String elementId) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(GeneratedMod.MOD_ID)
                && stack.getItem() instanceof MagicFragmentItem
                && ("arcane".equals(elementId)
                        ? id.getPath().endsWith("_arcane_fragment")
                        : id.getPath().endsWith("_" + elementId + "_fragment"));
    }

    /** 荆棘 Ⅲ 及以上的附魔书；作为伤害偏转披风的材料。 */
    private static boolean isThornsThreeBook(ItemStack stack) {
        if (!stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) return false;
        var stored = stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var holder : stored.keySet()) {
            boolean thorns = holder.unwrapKey()
                    .map(key -> key.location().equals(ResourceLocation.withDefaultNamespace("thorns")))
                    .orElse(false);
            if (thorns && stored.getLevel(holder) >= 3) return true;
        }
        return false;
    }

    /** 产物 id 既可以是本模组物品，也可以写成 minecraft: 前缀的原版物品。 */
    private static Item outputItem(String outputId) {
        if (outputId.startsWith("minecraft:"))
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(outputId));
        var holder = GeneratedMod.MOD_ITEMS.get(outputId);
        return holder == null ? null : holder.get();
    }

    private static Item ingredientItem(String ingredientId) {
        if (ingredientId.startsWith("minecraft:"))
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredientId));
        var holder = GeneratedMod.MOD_ITEMS.get(ingredientId);
        return holder == null ? null : holder.get();
    }

    private boolean canFitOutputs(ProcessRecipe recipe) {
        NonNullList<ItemStack> simulated = NonNullList.withSize(OUTPUT_END - OUTPUT_START, ItemStack.EMPTY);
        for (int i = OUTPUT_START; i < OUTPUT_END; i++) simulated.set(i - OUTPUT_START, items.get(i).copy());
        for (OutputStack output : recipe.outputs()) {
            Item item = outputItem(output.itemId());
            if (item == null || !insertInto(simulated, new ItemStack(item, output.count()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean insertInto(NonNullList<ItemStack> outputs, ItemStack addition) {
        int remaining = addition.getCount();
        for (ItemStack output : outputs) {
            if (!ItemStack.isSameItemSameComponents(output, addition)) continue;
            int moved = Math.min(remaining, output.getMaxStackSize() - output.getCount());
            output.grow(moved);
            remaining -= moved;
            if (remaining <= 0) return true;
        }
        for (int i = 0; i < outputs.size(); i++) {
            if (!outputs.get(i).isEmpty()) continue;
            int moved = Math.min(remaining, addition.getMaxStackSize());
            ItemStack placed = addition.copy();
            placed.setCount(moved);
            outputs.set(i, placed);
            remaining -= moved;
            if (remaining <= 0) return true;
        }
        return false;
    }

    private void finish(ProcessRecipe recipe) {
        if (!recipe.pattern().isEmpty()) {
            for (int index = 0; index < 9; index++) {
                if (!recipe.pattern().get(index).isBlank()) items.get(INPUT_START + index).shrink(1);
            }
        } else {
            for (IngredientStack ingredient : recipe.inputs()) consumeIngredient(ingredient.itemId(), ingredient.count());
        }
        for (OutputStack output : recipe.outputs()) {
            Item item = outputItem(output.itemId());
            if (item != null) insertOutput(new ItemStack(item, output.count()));
        }
        if ("special_mana_crystal".equals(recipe.primaryProduct()) && level != null && level.random.nextFloat() < 0.005F) {
            ElementType element = ElementType.values()[level.random.nextInt(ElementType.values().length)];
            insertOutput(new ItemStack(GeneratedMod.MOD_ITEMS.get(element.scrollId()).get()));
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    SoundSource.BLOCKS, 0.7F, 1.65F);
        }
        mana -= recipe.manaCost();
        processProgress = 0;
        activeRecipe = "";
        setChanged();
        if (recipe.destroysProcessor()) destroyProcessor();
    }

    /**
     * 超导魔晶这种“过载合成”会让加工台当场损坏：铁砧损坏音效、方块消失，
     * 箱内剩余内容物与三块铁锭、五根木棍一起掉在地上，产物也在其中。
     */
    private void destroyProcessor() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        BlockPos pos = worldPosition.immutable();
        List<ItemStack> spill = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) spill.add(stack.copy());
        }
        items.clear();
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.0F, 0.85F);
        server.removeBlock(pos, false);
        for (ItemStack stack : spill) net.minecraft.world.level.block.Block.popResource(level, pos, stack);
        net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(Items.IRON_INGOT, 3));
        net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(Items.STICK, 5));
    }

    private void consumeIngredient(String ingredientId, int amount) {
        int remaining = amount;
        for (int slot = INPUT_START; slot < INPUT_END && remaining > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (!matchesIngredient(stack, ingredientId)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
    }

    private void insertOutput(ItemStack addition) {
        NonNullList<ItemStack> outputs = NonNullList.withSize(OUTPUT_END - OUTPUT_START, ItemStack.EMPTY);
        for (int i = OUTPUT_START; i < OUTPUT_END; i++) outputs.set(i - OUTPUT_START, items.get(i));
        insertInto(outputs, addition);
        for (int i = OUTPUT_START; i < OUTPUT_END; i++) items.set(i, outputs.get(i - OUTPUT_START));
    }

    private static List<ProcessRecipe> createRecipes() {
        List<ProcessRecipe> recipes = new ArrayList<>();
        recipes.add(simpleRecipe("crystal_lesser", "lesser_mana_crystal_dust", "lapis_dust",
                "lesser_mana_crystal", "lapis_dust", 20));
        recipes.add(simpleRecipe("crystal_medium", "medium_mana_crystal_dust", "amethyst_dust",
                "medium_mana_crystal", "amethyst_dust", 120));
        recipes.add(simpleRecipe("crystal_advanced", "advanced_mana_crystal_dust", "medium_mana_crystal_dust",
                "advanced_mana_crystal", "lesser_mana_crystal_dust", 550));
        recipes.add(simpleRecipe("crystal_supreme", "supreme_mana_crystal_dust", "advanced_mana_crystal_dust",
                "supreme_mana_crystal", "medium_mana_crystal_dust", 2_000));
        recipes.add(simpleRecipe("crystal_special", "special_mana_crystal_dust", "supreme_mana_crystal_dust",
                "special_mana_crystal", "advanced_mana_crystal_dust", 5_000));

        int[] times = {80, 120, 160, 200};
        int[] manaCosts = {50, 220, 840, 2_080};
        for (int grade = 0; grade < GeneratedMod.FRAGMENT_GRADES.size() - 1; grade++) {
            String lower = GeneratedMod.FRAGMENT_GRADES.get(grade);
            String higher = GeneratedMod.FRAGMENT_GRADES.get(grade + 1);
            String higherDust = higher + "_mana_crystal_dust";
            String lowerDust = lower + "_mana_crystal_dust";
            for (ElementType element : ElementType.values()) {
                recipes.add(fragmentRecipe("element_" + element.id() + "_" + lower,
                        element.fragmentId(lower), element.fragmentId(higher), higherDust, lowerDust,
                        manaCosts[grade], times[grade]));
            }
            for (String school : GeneratedMod.SCHOOL_IDS) {
                recipes.add(fragmentRecipe("school_" + school + "_" + lower,
                        lower + "_" + school + "_school_fragment",
                        higher + "_" + school + "_school_fragment", higherDust, lowerDust,
                        manaCosts[grade], times[grade]));
            }
            recipes.add(fragmentRecipe("arcane_" + lower, lower + "_arcane_fragment",
                    higher + "_arcane_fragment", higherDust, lowerDust,
                    manaCosts[grade], times[grade]));
        }
        recipes.add(patternRecipe("rough_magic_component", List.of(
                "advanced_mana_crystal_dust", "minecraft:netherite_scrap", "advanced_mana_crystal_dust",
                "#magic_fragment", "medium_mana_crystal", "#magic_fragment",
                "advanced_mana_crystal_dust", "minecraft:netherite_scrap", "advanced_mana_crystal_dust"),
                "rough_magic_component", 300, 1_200));
        recipes.add(patternRecipe("refined_magic_component", List.of(
                "supreme_mana_crystal_dust", "minecraft:nether_star", "supreme_mana_crystal_dust",
                "minecraft:netherite_ingot", "rough_magic_component", "minecraft:netherite_ingot",
                "supreme_mana_crystal_dust", "", "supreme_mana_crystal_dust"),
                "refined_magic_component", 1_800, 1_800));
        recipes.add(patternRecipe("ancient_casket", List.of(
                "", "advanced_mana_crystal", "",
                "", "minecraft:diamond", "",
                "", "cracked_ancient_casket", ""),
                "ancient_casket", 8_000, 2_400));
        recipes.add(patternRecipe("greater_magic_etching", List.of(
                "special_mana_crystal_dust", "minecraft:netherite_scrap", "special_mana_crystal_dust",
                "minecraft:netherite_scrap", "refined_magic_component", "minecraft:netherite_scrap",
                "special_mana_crystal_dust", "minecraft:netherite_scrap", "special_mana_crystal_dust"),
                "greater_magic_etching", 8_000, 2_000));

        addElementCrystalRecipes(recipes);
        addElementRingRecipes(recipes);
        addEquipmentRecipes(recipes);
        addAncientMaterialRecipes(recipes);
        return List.copyOf(recipes);
    }

    /**
     * 远古材料线：超导魔晶、远古魔钢钢胚与魔力通透·共鸣核心。
     *
     * <p>超导魔晶需要把整个加工台的 20000 点魔素一次性抽干，合成完成后加工台当场损坏。</p>
     */
    private static void addAncientMaterialRecipes(List<ProcessRecipe> recipes) {
        recipes.add(patternRecipe("superconducting_crystal", List.of(
                "minecraft:blaze_powder", "superconducting_crystal_dust", "minecraft:blaze_powder",
                "superconducting_crystal_dust", "#element_crystal", "superconducting_crystal_dust",
                "minecraft:blaze_powder", "superconducting_crystal_dust", "minecraft:blaze_powder"),
                List.of(new OutputStack("superconducting_crystal", 1),
                        new OutputStack("special_mana_crystal_dust", 3),
                        new OutputStack("minecraft:blaze_rod", 1)),
                20_000, 40, "superconducting_crystal", true));
        recipes.add(patternRecipe("ancient_magic_steel_billet", List.of(
                "special_mana_crystal_dust", "ancient_magic_fragment", "special_mana_crystal_dust",
                "ancient_magic_fragment", "minecraft:netherite_ingot", "ancient_magic_fragment",
                "special_mana_crystal_dust", "ancient_magic_fragment", "special_mana_crystal_dust"),
                List.of(new OutputStack("ancient_magic_steel_billet", 1),
                        new OutputStack("supreme_mana_crystal_dust", 4)),
                10_000, 800, "ancient_magic_steel_billet", false));
        recipes.add(patternRecipe("resonance_core", List.of(
                "minecraft:sculk_catalyst", "superconducting_crystal", "minecraft:sculk_catalyst",
                "minecraft:netherite_ingot", "special_magic_steel", "minecraft:netherite_ingot",
                MagicEquipment.MAGIC_SILK, "minecraft:netherite_ingot", MagicEquipment.MAGIC_SILK),
                List.of(new OutputStack(ResonanceCore.ITEM_ID, 1)),
                10_000, 3_000, ResonanceCore.ITEM_ID, false));
    }

    /**
     * 元素结晶：九 / 七 / 五 / 二颗对应品级魔晶，外加一枚对应元素的碎片作引子。
     * 每次产出 1 枚结晶，并副产 1 颗同品级魔晶、1 份同品级魔晶粉与 1 份紫水晶粉末。
     * 特级碎片不参与元素结晶合成，因此引子只接受非特级碎片。
     */
    private static void addElementCrystalRecipes(List<ProcessRecipe> recipes) {
        String[] grades = {"lesser", "medium", "advanced", "supreme"};
        int[] counts = {9, 7, 5, 2};
        int[] manaCosts = {1_000, 500, 250, 125};
        int[] ticks = {1_200, 600, 300, 150};
        List<String> elementIds = new ArrayList<>();
        for (ElementType element : ElementType.values()) elementIds.add(element.id());
        elementIds.add(MagicEquipment.ARCANE);
        for (int index = 0; index < grades.length; index++) {
            String grade = grades[index];
            for (String elementId : elementIds) {
                String crystalId = MagicEquipment.crystalId(elementId);
                recipes.add(new ProcessRecipe("element_crystal_" + elementId + "_" + grade,
                        List.of(new IngredientStack(grade + "_mana_crystal", counts[index]),
                                new IngredientStack("#element_fragment:" + elementId, 1)),
                        List.of(),
                        List.of(new OutputStack(crystalId, 1),
                                new OutputStack(grade + "_mana_crystal", 1),
                                new OutputStack(grade + "_mana_crystal_dust", 1),
                                new OutputStack("amethyst_dust", 1)),
                        manaCosts[index], ticks[index], crystalId));
            }
        }
    }

    /** 元素结晶戒指：中级魔晶粉四角、金锭四边，中间放对应元素结晶。 */
    private static void addElementRingRecipes(List<ProcessRecipe> recipes) {
        List<String> elementIds = new ArrayList<>();
        for (ElementType element : ElementType.values()) elementIds.add(element.id());
        elementIds.add(MagicEquipment.ARCANE);
        for (String elementId : elementIds) {
            recipes.add(patternRecipe("element_ring_" + elementId, List.of(
                    "medium_mana_crystal_dust", "minecraft:gold_ingot", "medium_mana_crystal_dust",
                    "minecraft:gold_ingot", MagicEquipment.crystalId(elementId), "minecraft:gold_ingot",
                    "medium_mana_crystal_dust", "minecraft:gold_ingot", "medium_mana_crystal_dust"),
                    MagicEquipment.ringId(elementId), 400, 280));
        }
    }

    /** 魔力布匹、通用魔构件、魔法绸缎、三件披风与魔素增强护符。 */
    private static void addEquipmentRecipes(List<ProcessRecipe> recipes) {
        recipes.add(patternRecipe("magic_cloth", List.of(
                "lesser_mana_crystal_dust", "lapis_dust", "lesser_mana_crystal_dust",
                "lapis_dust", "minecraft:leather", "lapis_dust",
                "lesser_mana_crystal_dust", "lapis_dust", "lesser_mana_crystal_dust"),
                MagicEquipment.MAGIC_CLOTH, 50, 100));
        recipes.add(patternRecipe("magic_silk", List.of(
                "advanced_mana_crystal_dust", "amethyst_dust", "advanced_mana_crystal_dust",
                "amethyst_dust", MagicEquipment.MAGIC_CLOTH, "amethyst_dust",
                "advanced_mana_crystal_dust", "amethyst_dust", "advanced_mana_crystal_dust"),
                MagicEquipment.MAGIC_SILK, 50, 100));
        recipes.add(patternRecipe("universal_magic_component", List.of(
                MagicEquipment.MAGIC_CLOTH, "minecraft:lapis_lazuli", MagicEquipment.MAGIC_CLOTH,
                "minecraft:lapis_lazuli", "rough_magic_component", "minecraft:lapis_lazuli",
                MagicEquipment.MAGIC_CLOTH, "minecraft:lapis_lazuli", MagicEquipment.MAGIC_CLOTH),
                MagicEquipment.UNIVERSAL_COMPONENT, 500, 200));
        recipes.add(patternRecipe("magic_focus_cloak", List.of(
                "minecraft:gold_ingot", MagicEquipment.MAGIC_CLOTH, "minecraft:gold_ingot",
                MagicEquipment.MAGIC_CLOTH, MagicEquipment.MAGIC_CLOTH, MagicEquipment.MAGIC_CLOTH,
                MagicEquipment.MAGIC_CLOTH, MagicEquipment.MAGIC_CLOTH, MagicEquipment.MAGIC_CLOTH),
                MagicEquipment.FOCUS_CLOAK, 100, 500));
        recipes.add(patternRecipe("damage_deflect_cloak", List.of(
                MagicEquipment.MAGIC_SILK, "#enchanted_book_thorns_3", MagicEquipment.MAGIC_SILK,
                MagicEquipment.MAGIC_SILK, "supreme_mana_crystal", MagicEquipment.MAGIC_SILK,
                "minecraft:phantom_membrane", "minecraft:phantom_membrane", "minecraft:phantom_membrane"),
                MagicEquipment.DEFLECT_CLOAK, 800, 900));
        recipes.add(patternRecipe("magic_master_cloak", List.of(
                "special_mana_crystal", "special_mana_crystal", "special_mana_crystal",
                MagicEquipment.MAGIC_SILK, MagicEquipment.MAGIC_SILK, MagicEquipment.MAGIC_SILK,
                "#special_element_fragment", MagicEquipment.MAGIC_SILK, "#special_school_fragment"),
                MagicEquipment.MASTER_CLOAK, 900, 1_100));
        recipes.add(patternRecipe("mana_enhance_amulet", List.of(
                "minecraft:amethyst_shard", "minecraft:sculk_shrieker", "minecraft:amethyst_shard",
                "minecraft:end_crystal", MagicEquipment.UNIVERSAL_COMPONENT, "minecraft:end_crystal",
                "minecraft:amethyst_shard", "supreme_mana_crystal", "minecraft:amethyst_shard"),
                MagicEquipment.MANA_AMULET, 400, 200));
        addGloveRecipes(recipes);
    }

    /**
     * 两副施法手套的加工台配方（九宫格里随便摆，按材料数量匹配）。
     *
     * <p>迅捷施法手套：4 魔力布匹 + 3 魔法绸缎 + 1 魔素调和剂，把倒空的玻璃瓶当副产物还回来。
     * 大师施法手套：3 魔法绸缎 + 3 超导魔晶粉末 + 2 紫水晶碎片 + 1 通用魔构件，
     * 副产物是 2 份紫水晶粉与 1 份特级魔晶粉。两者都是 12 秒（240 刻）。</p>
     */
    private static void addGloveRecipes(List<ProcessRecipe> recipes) {
        recipes.add(new ProcessRecipe("swift_casting_gloves",
                List.of(new IngredientStack(MagicEquipment.MAGIC_CLOTH, 4),
                        new IngredientStack(MagicEquipment.MAGIC_SILK, 3),
                        new IngredientStack("mana_harmonizer", 1)), List.of(),
                List.of(new OutputStack(CastingGloveItem.Kind.SWIFT.itemId(), 1),
                        new OutputStack("minecraft:glass_bottle", 1)),
                300, 240, CastingGloveItem.Kind.SWIFT.itemId()));
        recipes.add(new ProcessRecipe("master_casting_gloves",
                List.of(new IngredientStack(MagicEquipment.MAGIC_SILK, 3),
                        new IngredientStack("superconducting_crystal_dust", 3),
                        new IngredientStack("minecraft:amethyst_shard", 2),
                        new IngredientStack(MagicEquipment.UNIVERSAL_COMPONENT, 1)), List.of(),
                List.of(new OutputStack(CastingGloveItem.Kind.MASTER.itemId(), 1),
                        new OutputStack("amethyst_dust", 2),
                        new OutputStack("special_mana_crystal_dust", 1)),
                600, 240, CastingGloveItem.Kind.MASTER.itemId()));
    }

    private static ProcessRecipe simpleRecipe(String id, String primary, String catalyst, String product,
                                              String remainder, int manaCost) {
        return new ProcessRecipe(id,
                List.of(new IngredientStack(primary, 4), new IngredientStack(catalyst, 1)), List.of(),
                List.of(new OutputStack(product, 1), new OutputStack(remainder, 1)),
                manaCost, DEFAULT_PROCESS_TICKS, product);
    }

    private static ProcessRecipe patternRecipe(String id, List<String> pattern, String product,
                                               int manaCost, int processTicks) {
        return patternRecipe(id, pattern, List.of(new OutputStack(product, 1)), manaCost, processTicks, product, false);
    }

    /** 带多种产物（含副产物）与“是否损坏加工台”标记的九宫格配方。 */
    private static ProcessRecipe patternRecipe(String id, List<String> pattern, List<OutputStack> outputs,
                                               int manaCost, int processTicks, String primaryProduct,
                                               boolean destroysProcessor) {
        if (pattern.size() != 9) throw new IllegalArgumentException("processor pattern must contain 9 slots");
        return new ProcessRecipe(id, List.of(), List.copyOf(pattern), List.copyOf(outputs),
                manaCost, processTicks, primaryProduct, destroysProcessor);
    }

    private static ProcessRecipe fragmentRecipe(String id, String fragment, String upgradedFragment,
                                                String higherDust, String lowerDust, int manaCost, int ticks) {
        return new ProcessRecipe("fragment_" + id,
                List.of(new IngredientStack(fragment, 1), new IngredientStack(higherDust, 3),
                        new IngredientStack("amethyst_dust", 1), new IngredientStack("lapis_dust", 2)), List.of(),
                List.of(new OutputStack(upgradedFragment, 1), new OutputStack(lowerDust, 3),
                        new OutputStack("lapis_dust", 1)),
                manaCost, ticks, upgradedFragment);
    }

    public static int fuelValue(ItemStack stack) {
        if (stack.is(GeneratedMod.MOD_ITEMS.get("lesser_mana_crystal").get())) return 100;
        if (stack.is(GeneratedMod.MOD_ITEMS.get("medium_mana_crystal").get())) return 400;
        if (stack.is(GeneratedMod.MOD_ITEMS.get("advanced_mana_crystal").get())) return 1_600;
        if (stack.is(GeneratedMod.MOD_ITEMS.get("supreme_mana_crystal").get())) return 4_000;
        if (stack.is(GeneratedMod.MOD_ITEMS.get("special_mana_crystal").get())) return 12_000;
        return 0;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        processProgress = tag.getInt("ProcessProgress");
        processTotal = Math.max(1, tag.getInt("ProcessTotal"));
        if (!tag.contains("ProcessTotal")) processTotal = DEFAULT_PROCESS_TICKS;
        fuelProgress = tag.getInt("FuelProgress");
        mana = Math.max(0, Math.min(MAX_MANA, tag.getInt("Mana")));
        activeRecipe = tag.getString("ActiveRecipe");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("ProcessProgress", processProgress);
        tag.putInt("ProcessTotal", processTotal);
        tag.putInt("FuelProgress", fuelProgress);
        tag.putInt("Mana", mana);
        tag.putString("ActiveRecipe", activeRecipe);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.dimensional_mana.crystal_processor");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrystalProcessorMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize(stack)) stack.setCount(getMaxStackSize(stack));
        setChanged();
    }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= OUTPUT_START && slot < OUTPUT_END) return false;
        return slot != FUEL_SLOT || fuelValue(stack) > 0;
    }
    @Override public void clearContent() { items.clear(); }

    public ContainerData dataAccess() { return data; }

    /** 图鉴与加工逻辑共用同一份只读配方，新增加工产物时无需再维护第二份清单。 */
    public static List<ProcessRecipe> recipes() { return RECIPES; }

    public record IngredientStack(String itemId, int count) { }
    public record OutputStack(String itemId, int count) { }
    public record ProcessRecipe(String id, List<IngredientStack> inputs, List<String> pattern,
                                List<OutputStack> outputs, int manaCost, int processTicks,
                                String primaryProduct, boolean destroysProcessor) {
        /** 普通配方：不损坏加工台。 */
        public ProcessRecipe(String id, List<IngredientStack> inputs, List<String> pattern,
                             List<OutputStack> outputs, int manaCost, int processTicks, String primaryProduct) {
            this(id, inputs, pattern, outputs, manaCost, processTicks, primaryProduct, false);
        }
    }
}
