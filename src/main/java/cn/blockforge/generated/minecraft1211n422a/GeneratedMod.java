package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCookingSerializer;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "dimensional_mana";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> LOOT_CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType,
            net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> ANCIENT_FRAGMENT_LIMIT_CONDITION =
            LOOT_CONDITIONS.register("ancient_fragment_limit", () -> new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(
                    AncientFragmentLimitCondition.CODEC));
    public static final DeferredHolder<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType,
            net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> ELEMENTAL_SLIME_LOOT_CHANCE =
            LOOT_CONDITIONS.register("elemental_slime_loot_chance", () -> new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(
                    ElementalSlimeLootChance.CODEC));
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MOD_ID);

    public static final Map<String, DeferredBlock<Block>> MOD_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<? extends Item>> MOD_ITEMS = new LinkedHashMap<>();
    public static final List<String> MAGIC_ITEM_IDS = new ArrayList<>();
    public static final List<String> FRAGMENT_GRADES = List.of("lesser", "medium", "advanced", "supreme", "special");
    public static final List<String> SCHOOL_IDS = List.of("destruction", "protection", "healing", "blessing", "curse",
            "control", "summon", "movement", "divination", "psychic", "time", "space", "chaos");
    private static final Map<ElementType, DeferredHolder<MobEffect, MobEffect>> ELEMENT_EFFECTS = new EnumMap<>(ElementType.class);
    private static final Map<ElementalSlimeVariant, DeferredHolder<EntityType<?>, EntityType<ElementalSlime>>> ELEMENTAL_SLIMES =
            new EnumMap<>(ElementalSlimeVariant.class);

    public static final DeferredHolder<MobEffect, MobEffect> MANA_EXHAUSTION = EFFECTS.register("mana_exhaustion", ManaExhaustionEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> ARCANE_SURGE = EFFECTS.register("arcane_surge", ArcaneSurgeEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> ARCANE_ATTACHMENT = EFFECTS.register("arcane_attachment",
            () -> new ArcaneReactionEffect(MobEffectCategory.NEUTRAL, 0xC85BFF));
    public static final DeferredHolder<MobEffect, MobEffect> ARCANE_ECHO = EFFECTS.register("arcane_echo",
            () -> new ArcaneReactionEffect(MobEffectCategory.NEUTRAL, 0xF074FF));
    /**
     * 剧毒Ⅲ药水：中毒 III，持续 18 秒（360 tick）。
     * 名称显式写成 poison_iii，客户端就会去找 item.minecraft.potion.effect.poison_iii 这几个翻译键，
     * 喷溅、滞留和药箭版本也共用同一个后缀。
     */
    public static final DeferredHolder<Potion, Potion> POISON_III = POTIONS.register("poison_iii",
            () -> new Potion("poison_iii", new MobEffectInstance(MobEffects.POISON, 18 * 20, 2)));
    public static final DeferredHolder<SoundEvent, SoundEvent> MANA_FORGE_BURN = SOUNDS.register("mana_forge_burn",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "mana_forge_burn")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PROFESSIONAL_FURNACE_BURN = SOUNDS.register("professional_furnace_burn",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "professional_furnace_burn")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PURE_LIQUID_MANA_BUBBLE = SOUNDS.register("pure_liquid_mana_bubble",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pure_liquid_mana_bubble")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL_PROCESS_COMPLETE = SOUNDS.register("crystal_process_complete",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "crystal_process_complete")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CODEX_PAGE_TURN = SOUNDS.register("codex_page_turn",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "codex_page_turn")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CODEX_SPACE_OPEN = SOUNDS.register("codex_space_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "codex_space_open")));
    /**
     * 十二种元素史莱姆各一套叫声：蹦跳、落地、受伤、死亡和随机环境音共用。
     * 音色在 sounds.json 里由「原版史莱姆软音 + 对应元素法术音」叠加，无需额外音频文件。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_MANA = slimeVoice("mana");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_FIRE = slimeVoice("fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_WATER = slimeVoice("water");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_ICE = slimeVoice("ice");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_LIGHTNING = slimeVoice("lightning");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_NATURE = slimeVoice("nature");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_POISON = slimeVoice("poison");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_METAL = slimeVoice("metal");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_WIND = slimeVoice("wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_EARTH = slimeVoice("earth");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_LIGHT = slimeVoice("light");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_VOICE_SOUND = slimeVoice("sound");

    private static DeferredHolder<SoundEvent, SoundEvent> slimeVoice(String id) {
        return SOUNDS.register("elemental_slime_" + id,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "elemental_slime_" + id)));
    }
    public static final DeferredHolder<MenuType<?>, MenuType<GrinderMenu>> GRINDER_MENU = MENUS.register("grinder",
            () -> new MenuType<>(GrinderMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<ManaFurnaceMenu>> MANA_FORGE_MENU = MENUS.register("mana_forge",
            () -> new MenuType<>((id, inventory) -> new ManaFurnaceMenu(id, inventory, ManaCookingRecipe.FurnaceKind.MANA_FORGE), FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<ManaFurnaceMenu>> PROFESSIONAL_FURNACE_MENU = MENUS.register("professional_crystal_furnace",
            () -> new MenuType<>((id, inventory) -> new ManaFurnaceMenu(id, inventory, ManaCookingRecipe.FurnaceKind.PROFESSIONAL), FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<CrystalProcessorMenu>> CRYSTAL_PROCESSOR_MENU = MENUS.register("crystal_processor",
            () -> new MenuType<>(CrystalProcessorMenu::new, FeatureFlags.DEFAULT_FLAGS));
    /** 冒险者交易界面：窗口 id + 实体 id，客户端靠实体 id 找回那只冒险者。 */
    public static final DeferredHolder<MenuType<?>, MenuType<AdventurerTradeMenu>> ADVENTURER_TRADE_MENU = MENUS.register(
            "adventurer_trade", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id, inventory, buffer) -> new AdventurerTradeMenu(id, inventory, buffer.readVarInt())));
    public static final DeferredHolder<RecipeType<?>, RecipeType<ManaCookingRecipe>> MANA_FORGE_RECIPE_TYPE = RECIPE_TYPES.register("mana_forge_smelting", RecipeType::simple);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ManaCookingRecipe>> PROFESSIONAL_RECIPE_TYPE = RECIPE_TYPES.register("professional_smelting", RecipeType::simple);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ManaCookingRecipe>> MANA_FORGE_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register(
            "mana_forge_smelting", () -> new SimpleCookingSerializer<>((group, category, ingredient, result, experience, cookingTime) ->
                    new ManaCookingRecipe(ManaCookingRecipe.FurnaceKind.MANA_FORGE, group, category, ingredient, result, experience, cookingTime), 200));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ManaCookingRecipe>> PROFESSIONAL_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register(
            "professional_smelting", () -> new SimpleCookingSerializer<>((group, category, ingredient, result, experience, cookingTime) ->
                    new ManaCookingRecipe(ManaCookingRecipe.FurnaceKind.PROFESSIONAL, group, category, ingredient, result, experience, cookingTime), 200));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> MANA_CRYSTAL_ORES = FEATURES.register(
            "mana_crystal_ores", ManaCrystalOreFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> MANA_CRYSTAL_CAVE = FEATURES.register(
            "mana_crystal_cave", ManaCrystalCaveFeature::new);
    public static final DeferredHolder<EntityType<?>, EntityType<WanderingWitch>> WANDERING_WITCH = ENTITY_TYPES.register(
            "wandering_witch", () -> EntityType.Builder.of(WanderingWitch::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build(MOD_ID + ":wandering_witch"));
    public static final DeferredHolder<EntityType<?>, EntityType<Adventurer>> ADVENTURER = ENTITY_TYPES.register(
            "adventurer", () -> EntityType.Builder.of(Adventurer::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build(MOD_ID + ":adventurer"));
    public static final DeferredHolder<EntityType<?>, EntityType<ManaOrb>> MANA_ORB = ENTITY_TYPES.register(
            "mana_orb", () -> EntityType.Builder.<ManaOrb>of(ManaOrb::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).build(MOD_ID + ":mana_orb"));
    public static final DeferredHolder<StructureType<?>, StructureType<AncientMagicAltarStructure>> ANCIENT_MAGIC_ALTAR_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("ancient_magic_altar", () -> () -> AncientMagicAltarStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> ANCIENT_MAGIC_ALTAR_PIECE =
            STRUCTURE_PIECES.register("ancient_magic_altar", () -> (StructurePieceType.ContextlessType)AncientMagicAltarPiece::new);

    public static final DeferredHolder<FluidType, FluidType> PURE_LIQUID_MANA_TYPE = FLUID_TYPES.register("pure_liquid_mana",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.dimensional_mana.pure_liquid_mana")
                    .density(3000).viscosity(6000).temperature(360).lightLevel(8)
                    .canDrown(false).canSwim(false).supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL_LAVA)
                    .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_LAVA)));
    public static final DeferredHolder<Fluid, FlowingFluid> PURE_LIQUID_MANA = FLUIDS.register("pure_liquid_mana",
            () -> new BaseFlowingFluid.Source(pureLiquidManaProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_PURE_LIQUID_MANA = FLUIDS.register("flowing_pure_liquid_mana",
            () -> new BaseFlowingFluid.Flowing(pureLiquidManaProperties()));
    public static final DeferredBlock<PureLiquidManaBlock> PURE_LIQUID_MANA_BLOCK = BLOCKS.register("pure_liquid_mana",
            () -> new PureLiquidManaBlock(PURE_LIQUID_MANA.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).replaceable().noCollission().strength(100.0F)
                    .lightLevel(state -> 8).pushReaction(PushReaction.DESTROY).noLootTable().liquid()));
    public static final DeferredItem<BucketItem> PURE_LIQUID_MANA_BUCKET = ITEMS.register("pure_liquid_mana_bucket",
            () -> new BucketItem(PURE_LIQUID_MANA.get(), new Item.Properties().craftRemainder(Items.BUCKET)
                    .stacksTo(1).rarity(Rarity.RARE)));

    static {
        for (ElementType element : ElementType.values()) {
            ELEMENT_EFFECTS.put(element, EFFECTS.register(element.attachmentEffectId(),
                    () -> new ElementAttachmentEffect(element)));
        }
        ore("lesser_mana_crystal_ore", 3.0F, 3, 6, Rarity.COMMON);
        ore("medium_mana_crystal_ore", 4.0F, 4, 7, Rarity.UNCOMMON);
        ore("advanced_mana_crystal_ore", 5.0F, 5, 9, Rarity.RARE);
        ore("supreme_mana_crystal_ore", 6.0F, 6, 11, Rarity.EPIC);
        cluster("special_mana_crystal_cluster", 7.0F, 13, Rarity.EPIC);
        device("mana_forge", () -> new ManaFurnaceBlock(BlockBehaviour.Properties.of().strength(8.0F, 1200.0F)
                .sound(SoundType.DEEPSLATE).lightLevel(state -> state.getValue(AbstractFurnaceBlock.LIT) ? 10 : 0),
                ManaCookingRecipe.FurnaceKind.MANA_FORGE), Rarity.RARE);
        device("professional_crystal_furnace", () -> new ManaFurnaceBlock(BlockBehaviour.Properties.of().strength(10.0F, 1200.0F)
                .sound(SoundType.METAL).lightLevel(state -> state.getValue(AbstractFurnaceBlock.LIT) ? 13 : 0),
                ManaCookingRecipe.FurnaceKind.PROFESSIONAL), Rarity.EPIC);
        // 加工台的方块物品要自己实现：拆下来时得把里面的魔素一并带走。
        DeferredBlock<Block> crystalProcessor = BLOCKS.register("crystal_processor",
                () -> new CrystalProcessorBlock(BlockBehaviour.Properties.of().strength(5.0F, 10.0F)
                        .requiresCorrectToolForDrops().sound(SoundType.METAL)));
        MOD_BLOCKS.put("crystal_processor", crystalProcessor);
        MOD_ITEMS.put("crystal_processor", ITEMS.register("crystal_processor",
                () -> new CrystalProcessorItem(crystalProcessor.get(), new Item.Properties().rarity(Rarity.RARE))));

        simple("raw_lesser_mana_crystal", Rarity.COMMON);
        simple("raw_medium_mana_crystal", Rarity.UNCOMMON);
        simple("raw_advanced_mana_crystal", Rarity.RARE);
        simple("raw_supreme_mana_crystal", Rarity.EPIC);
        simple("lesser_mana_crystal", Rarity.COMMON);
        simple("medium_mana_crystal", Rarity.UNCOMMON);
        simple("advanced_mana_crystal", Rarity.RARE);
        simple("supreme_mana_crystal", Rarity.EPIC);
        // 特级魔晶是闪电转化的原料，必须扛得住火、岩浆和雷击后的火焰。
        MOD_ITEMS.put("special_mana_crystal", ITEMS.register("special_mana_crystal",
                () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant())));
        simple("lesser_mana_crystal_dust", Rarity.COMMON);
        simple("medium_mana_crystal_dust", Rarity.UNCOMMON);
        simple("advanced_mana_crystal_dust", Rarity.RARE);
        simple("supreme_mana_crystal_dust", Rarity.EPIC);
        simple("special_mana_crystal_dust", Rarity.EPIC);
        simple("lapis_dust", Rarity.COMMON);
        simple("amethyst_dust", Rarity.UNCOMMON);
        MOD_ITEMS.put("mana_harmonizer", ITEMS.register("mana_harmonizer", () -> new ManaHarmonizerItem(
                new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))));
        simple("stone_pestle", Rarity.COMMON);
        simple("iron_pestle", Rarity.COMMON);
        simple("gold_pestle", Rarity.UNCOMMON);
        simple("diamond_pestle", Rarity.RARE);

        MOD_ITEMS.put("wooden_grinder", ITEMS.register("wooden_grinder", () -> new GrinderItem(new Item.Properties().durability(20), 0, 0)));
        MOD_ITEMS.put("stone_grinder", ITEMS.register("stone_grinder", () -> new GrinderItem(new Item.Properties().durability(120), 1, 1)));
        MOD_ITEMS.put("golden_grinder", ITEMS.register("golden_grinder", () -> new GrinderItem(new Item.Properties().durability(40).rarity(Rarity.UNCOMMON), 1, 2)));
        MOD_ITEMS.put("iron_grinder", ITEMS.register("iron_grinder", () -> new GrinderItem(new Item.Properties().durability(400), 2, 3)));
        MOD_ITEMS.put("diamond_grinder", ITEMS.register("diamond_grinder", () -> new GrinderItem(new Item.Properties().durability(1200).rarity(Rarity.RARE), 3, 4)));
        MOD_ITEMS.put("netherite_grinder", ITEMS.register("netherite_grinder", () -> new GrinderItem(new Item.Properties().durability(2400).fireResistant().rarity(Rarity.EPIC), 4, 5)));

        MOD_ITEMS.put("appraisal_orb", ITEMS.register("appraisal_orb", () -> new AppraisalOrbItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        MOD_ITEMS.put("mana_detector", ITEMS.register("mana_detector", () -> new ManaDetectorItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        MOD_ITEMS.put("appraisal_glasses", ITEMS.register("appraisal_glasses", () -> new AppraisalGlassesItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        MOD_ITEMS.put("arcane_codex", ITEMS.register("arcane_codex", () -> new ArcaneCodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))));
        MOD_ITEMS.put("skill_repentance_stone", ITEMS.register("skill_repentance_stone", () -> new SkillRepentanceStoneItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC))));
        simple("rough_magic_component", Rarity.RARE);
        simple("refined_magic_component", Rarity.EPIC);
        simple("cracked_ancient_casket", Rarity.EPIC);
        magicItem("ancient_casket", ITEMS.register("ancient_casket", () -> new MagicTrinketItem(
                new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), MagicTrinketItem.Kind.ANCIENT_CASKET)));
        magicItem("greater_magic_etching", ITEMS.register("greater_magic_etching", () -> new MagicTrinketItem(
                new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), MagicTrinketItem.Kind.GREATER_MAGIC_ETCHING)));
        magicStaff("lesser_general_staff", 100, Rarity.COMMON, 2, 8, 0.05, 0.04, 0.05);
        magicStaff("medium_general_staff", 200, Rarity.UNCOMMON, 6, 16, 0.10, 0.08, 0.10);
        magicStaff("advanced_general_staff", 300, Rarity.RARE, 12, 32, 0.15, 0.12, 0.15);
        magicStaff("supreme_general_staff", 400, Rarity.EPIC, 24, 64, 0.20, 0.16, 0.20);
        magicStaff("special_general_staff", 500, Rarity.EPIC, 48, 128, 0.25, 0.20, 0.25);
        magicItem("magic_amulet", ITEMS.register("magic_amulet", () -> new MagicAccessoryItem(
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), MagicAccessoryItem.Kind.AMULET)));
        magicItem("wisdom_ring", ITEMS.register("wisdom_ring", () -> new MagicAccessoryItem(
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), MagicAccessoryItem.Kind.RING)));
        MOD_ITEMS.put("lesser_mana_potion", ITEMS.register("lesser_mana_potion", () -> new ManaPotionItem(new Item.Properties().stacksTo(16), 0.025, 64, 20, false)));
        MOD_ITEMS.put("medium_mana_potion", ITEMS.register("medium_mana_potion", () -> new ManaPotionItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), 0.0678, 128, 20, false)));
        MOD_ITEMS.put("advanced_mana_potion", ITEMS.register("advanced_mana_potion", () -> new ManaPotionItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), 0.174, 256, 20, false)));
        MOD_ITEMS.put("special_mana_potion", ITEMS.register("special_mana_potion", () -> new ManaPotionItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 0.411, 512, 40, false)));
        MOD_ITEMS.put("pure_liquid_mana", ITEMS.register("pure_liquid_mana", () -> new ManaPotionItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3.0, 0, 40, true)));
        MOD_ITEMS.put("pure_liquid_mana_bucket", PURE_LIQUID_MANA_BUCKET);

        MAGIC_ITEM_IDS.add("arcane_codex");
        // 空白法术卷轴：冒险者出售的小卷轴，右键能把正在研读的法术往前推一次。
        magicItem("blank_spell_scroll", ITEMS.register("blank_spell_scroll", () ->
                new BlankSpellScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.COMMON))));
        magicItem("element_resonance_stone", ITEMS.register("element_resonance_stone", () ->
                new ElementResonanceStoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))));
        for (ElementType element : ElementType.values()) {
            for (String grade : FRAGMENT_GRADES) magicFragment(element.fragmentId(grade), grade, element.translationKey());
            magicItem(element.scrollId(), ITEMS.register(element.scrollId(), () ->
                    new ElementTalentScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), element)));
        }
        for (String school : SCHOOL_IDS) {
            for (String grade : FRAGMENT_GRADES) magicFragment(grade + "_" + school + "_school_fragment", grade,
                    "gui.dimensional_mana.school.single." + school);
        }
        for (String grade : FRAGMENT_GRADES) magicFragment(grade + "_arcane_fragment", grade,
                "gui.dimensional_mana.medium.arcane");

        // 新材料：魔力布匹、魔法绸缎与通用魔构件（主创造页即可，不需要进魔法页）。
        simple(MagicEquipment.MAGIC_CLOTH, Rarity.UNCOMMON);
        simple(MagicEquipment.MAGIC_SILK, Rarity.RARE);
        simple(MagicEquipment.UNIVERSAL_COMPONENT, Rarity.RARE);
        // 元素结晶与元素结晶戒指：十一种元素 + 奥术。
        for (ElementType element : ElementType.values()) {
            registerElementAccessories(element);
        }
        registerElementAccessories(null);
        // 远古材料线：远古魔导残片 → 远古魔钢钢胚 → 特级魔精钢，以及超导魔晶与共鸣核心。
        MOD_ITEMS.put(AncientFragmentLoot.ITEM_ID, ITEMS.register(AncientFragmentLoot.ITEM_ID,
                () -> new Item(new Item.Properties().rarity(Rarity.EPIC))));
        MOD_ITEMS.put("superconducting_crystal_dust", ITEMS.register("superconducting_crystal_dust",
                () -> new GlowingItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant())));
        MOD_ITEMS.put("superconducting_crystal", ITEMS.register("superconducting_crystal",
                () -> new GlowingItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant())));
        MOD_ITEMS.put("ancient_magic_steel_billet", ITEMS.register("ancient_magic_steel_billet",
                () -> new Item(new Item.Properties().rarity(Rarity.EPIC))));
        MOD_ITEMS.put("special_magic_steel", ITEMS.register("special_magic_steel",
                () -> new GlowingItem(new Item.Properties().rarity(Rarity.EPIC))));
        // 传说品质的共鸣核心带附魔光效，物品栏里会有一层流动的光。
        magicItem(ResonanceCore.ITEM_ID, ITEMS.register(ResonanceCore.ITEM_ID, () ->
                new ResonanceCoreItem(new Item.Properties().stacksTo(1)
                        .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))));
        for (MagicCloakItem.Kind kind : MagicCloakItem.Kind.values()) {
            magicItem(kind.itemId(), ITEMS.register(kind.itemId(), () -> new MagicCloakItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), kind)));
        }
        magicItem(MagicEquipment.MANA_AMULET, ITEMS.register(MagicEquipment.MANA_AMULET, () ->
                new ManaEnhanceAmuletItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))));
        // 元素强化戒指：由奥术元素结晶戒指在锻造台上升级而来，全元素通用加成。
        magicItem(MagicEquipment.ENHANCE_RING, ITEMS.register(MagicEquipment.ENHANCE_RING, () ->
                new ElementEnhanceRingItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))));
        // 两副施法手套：迅捷（速度/冷却）与大师（暴击/必定暴击）。
        for (CastingGloveItem.Kind kind : CastingGloveItem.Kind.values()) {
            magicItem(kind.itemId(), ITEMS.register(kind.itemId(), () -> new CastingGloveItem(
                    new Item.Properties().stacksTo(1).rarity(kind == CastingGloveItem.Kind.MASTER
                            ? Rarity.EPIC : Rarity.RARE), kind)));
        }
        DeferredBlock<Block> stele = BLOCKS.register("ancient_magic_stele", () -> new AncientMagicSteleBlock(BlockBehaviour.Properties.of()
                .strength(5.0F, 8.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE).noOcclusion().dynamicShape()));
        MOD_BLOCKS.put("ancient_magic_stele", stele);
        magicItem("ancient_magic_stele", ITEMS.register("ancient_magic_stele", () ->
                new BlockItem(stele.get(), new Item.Properties().rarity(Rarity.EPIC))));
        magicItem("wandering_witch_spawn_egg", ITEMS.register("wandering_witch_spawn_egg", () ->
                new DeferredSpawnEggItem(WANDERING_WITCH, 0x24172F, 0x574066,
                        new Item.Properties().rarity(Rarity.RARE))));
        magicItem("adventurer_spawn_egg", ITEMS.register("adventurer_spawn_egg", () ->
                new DeferredSpawnEggItem(ADVENTURER, 0x3D536C, 0xC59A55,
                        new Item.Properties().rarity(Rarity.RARE))));
        registerElementalSlimes();
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManaFurnaceBlockEntity>> MANA_FORGE_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "mana_forge", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ManaFurnaceBlockEntity(ManaCookingRecipe.FurnaceKind.MANA_FORGE, pos, state),
                    MOD_BLOCKS.get("mana_forge").get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManaFurnaceBlockEntity>> PROFESSIONAL_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "professional_crystal_furnace", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ManaFurnaceBlockEntity(ManaCookingRecipe.FurnaceKind.PROFESSIONAL, pos, state),
                    MOD_BLOCKS.get("professional_crystal_furnace").get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalProcessorBlockEntity>> CRYSTAL_PROCESSOR_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "crystal_processor", () -> BlockEntityType.Builder.of(CrystalProcessorBlockEntity::new,
                    MOD_BLOCKS.get("crystal_processor").get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOD_CREATIVE_TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dimensional_mana.main"))
            .icon(() -> new ItemStack(MOD_ITEMS.get("special_mana_crystal").get()))
            .displayItems((parameters, output) -> MOD_ITEMS.forEach((id, item) -> {
                if (!MAGIC_ITEM_IDS.contains(id)) output.accept(item.get());
            })).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAGIC_CREATIVE_TAB = CREATIVE_TABS.register("magic", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dimensional_mana.magic"))
            .icon(() -> new ItemStack(MOD_ITEMS.get("element_resonance_stone").get()))
            .displayItems((parameters, output) -> orderedMagicItemIds().forEach(id -> {
                DeferredItem<? extends Item> item = MOD_ITEMS.get(id);
                if (item != null) output.accept(item.get());
            }))
            .build());

    /**
     * 魔法创造页的摆放顺序：先按大类分组——饰品 → 法杖 → 刷怪蛋 → 其余
     * （碎片、天赋卷轴、材料、方块）。组内保持注册顺序，饰品再按戒指 / 护符 /
     * 披风 / 手套 / 匣子 / 共鸣核心细分。
     */
    private static List<String> orderedMagicItemIds() {
        List<String> ordered = new ArrayList<>(MAGIC_ITEM_IDS.size());
        List<String> accessories = new ArrayList<>();
        for (String id : MAGIC_ITEM_IDS) if (accessoryRank(id) >= 0) accessories.add(id);
        accessories.sort(Comparator.comparingInt(GeneratedMod::accessoryRank));
        ordered.addAll(accessories);
        for (String id : MAGIC_ITEM_IDS) if (id.endsWith("_general_staff")) ordered.add(id);
        for (String id : MAGIC_ITEM_IDS) if (id.endsWith("_spawn_egg")) ordered.add(id);
        for (String id : MAGIC_ITEM_IDS) if (!ordered.contains(id)) ordered.add(id);
        return ordered;
    }

    /** 饰品的细分排序值；不是饰品就返回 -1。 */
    private static int accessoryRank(String id) {
        if (id.endsWith("_element_ring")) return 10;
        if (MagicEquipment.ENHANCE_RING.equals(id)) return 20;
        if ("wisdom_ring".equals(id)) return 30;
        if ("magic_amulet".equals(id)) return 40;
        if (MagicEquipment.MANA_AMULET.equals(id)) return 50;
        if (MagicEquipment.FOCUS_CLOAK.equals(id) || MagicEquipment.DEFLECT_CLOAK.equals(id)
                || MagicEquipment.MASTER_CLOAK.equals(id)) return 60;
        if (id.endsWith("_casting_gloves")) return 70;
        if ("ancient_casket".equals(id) || "greater_magic_etching".equals(id)) return 80;
        if (ResonanceCore.ITEM_ID.equals(id)) return 90;
        return -1;
    }

    public static Holder<MobEffect> elementEffect(ElementType element) {
        return ELEMENT_EFFECTS.get(element);
    }

    public static EntityType<ElementalSlime> elementalSlimeType(ElementalSlimeVariant variant) {
        return ELEMENTAL_SLIMES.get(variant).get();
    }

    /** 变种 -> 音效事件。只有实体 tick 里会调用，那时枚举早已初始化。 */
    public static SoundEvent elementalSlimeSound(ElementalSlimeVariant variant) {
        return switch (variant) {
            case MANA -> SLIME_VOICE_MANA.get();
            case FIRE -> SLIME_VOICE_FIRE.get();
            case WATER -> SLIME_VOICE_WATER.get();
            case ICE -> SLIME_VOICE_ICE.get();
            case LIGHTNING -> SLIME_VOICE_LIGHTNING.get();
            case NATURE -> SLIME_VOICE_NATURE.get();
            case POISON -> SLIME_VOICE_POISON.get();
            case METAL -> SLIME_VOICE_METAL.get();
            case WIND -> SLIME_VOICE_WIND.get();
            case EARTH -> SLIME_VOICE_EARTH.get();
            case LIGHT -> SLIME_VOICE_LIGHT.get();
            case SOUND -> SLIME_VOICE_SOUND.get();
        };
    }

    private static BaseFlowingFluid.Properties pureLiquidManaProperties() {
        return new BaseFlowingFluid.Properties(PURE_LIQUID_MANA_TYPE, PURE_LIQUID_MANA, FLOWING_PURE_LIQUID_MANA)
                .bucket(PURE_LIQUID_MANA_BUCKET).block(PURE_LIQUID_MANA_BLOCK)
                .slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(30).explosionResistance(100.0F);
    }

    public GeneratedMod(IEventBus modBus, ModContainer container) {
        NewSpellContent.init();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FLUIDS.register(modBus);
        FLUID_TYPES.register(modBus);
        EFFECTS.register(modBus);
        POTIONS.register(modBus);
        SpellSounds.register();
        SOUNDS.register(modBus);
        MENUS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        LOOT_CONDITIONS.register(modBus);
        CREATIVE_TABS.register(modBus);
        FEATURES.register(modBus);
        ENTITY_TYPES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        SpellAttributes.ATTRIBUTES.register(modBus);
        modBus.addListener(this::createEntityAttributes);
        modBus.addListener(SpellAttributes::attachToLivingEntities);
        modBus.addListener(ManaSyncPayload::register);
        modBus.addListener(AppraisalSyncPayload::register);
        modBus.addListener(ProgressSyncPayload::register);
        modBus.addListener(UpgradeSkillPayload::register);
        modBus.addListener(OpenCodexGuidePayload::register);
        modBus.addListener(SpellSyncPayload::register);
        modBus.addListener(SpellVisualPayload::register);
        modBus.addListener(ReactionVisualPayload::register);
        modBus.addListener(FrozenVisualPayload::register);
        modBus.addListener(ManaSensePayload::register);
        modBus.addListener(SpellActionPayload::register);
        modBus.addListener(ElementTalentSyncPayload::register);
        modBus.addListener(ElementTalentActionPayload::register);
        modBus.addListener(OpenSteleResearchPayload::register);
        modBus.addListener(SteleResearchActionPayload::register);
        modBus.addListener(ResonanceCoreSyncPayload::register);
        modBus.addListener(MagicToggleSyncPayload::register);
        if (FMLEnvironment.dist == Dist.CLIENT) ClientModEvents.register(modBus);
        ManaEvents events = new ManaEvents();
        FragmentLootEvents fragmentLoot = new FragmentLootEvents();
        AccessoryLootEvents accessoryLoot = new AccessoryLootEvents();
        NeoForge.EVENT_BUS.addListener(ManaCommands::register);
        NeoForge.EVENT_BUS.addListener(ManaFurnaceRecipeBridge::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ManaFurnaceRecipeBridge::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(events::onJoin);
        NeoForge.EVENT_BUS.addListener(events::onLogin);
        NeoForge.EVENT_BUS.addListener(events::onLogout);
        NeoForge.EVENT_BUS.addListener(events::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onServerStopped);
        NeoForge.EVENT_BUS.addListener(events::onClone);
        NeoForge.EVENT_BUS.addListener(events::onVanillaExperience);
        NeoForge.EVENT_BUS.addListener(events::onWake);
        NeoForge.EVENT_BUS.addListener(events::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(events::onDamagePre);
        NeoForge.EVENT_BUS.addListener(events::onStruckByLightning);
        NeoForge.EVENT_BUS.addListener(events::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(events::onTick);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::tickLevel);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::tickLevel);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::onExplosion);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::onSound);
        NeoForge.EVENT_BUS.addListener(AdventurerSpawning::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::heal);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::effectRemoved);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::effectExpired);
        NeoForge.EVENT_BUS.addListener(PoisonSpells::allowForcedPoison);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::afterDamage);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::attack);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::jump);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::use);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::useBlock);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::startItem);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::useEntity);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::useEntitySpecific);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::breakBlock);
        NeoForge.EVENT_BUS.addListener(WoodSpells::onExplode);
        NeoForge.EVENT_BUS.addListener(this::addTalentScrollChestLoot);
        NeoForge.EVENT_BUS.addListener(fragmentLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(fragmentLoot::addWitchDrops);
        NeoForge.EVENT_BUS.addListener(AncientFragmentLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(AncientFragmentLoot::addEntityDrops);
        NeoForge.EVENT_BUS.addListener(accessoryLoot::onLootTableLoad);
        NeoForge.EVENT_BUS.addListener(accessoryLoot::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(events::onDamagePost);
        NeoForge.EVENT_BUS.addListener(ElementPlating::onTooltip);
        NeoForge.EVENT_BUS.addListener(ElementPlating::onGrindstonePlace);
        NeoForge.EVENT_BUS.addListener(ElementPlating::onGrindstoneTake);
        NeoForge.EVENT_BUS.addListener(this::brewingRecipes);
        container.registerConfig(ModConfig.Type.COMMON, ManaConfig.SPEC, "dimensional_mana-common.toml");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, ClientManaConfig.SPEC, "dimensional_mana-client.toml");
        }
    }

    private void createEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(WANDERING_WITCH.get(), WanderingWitch.createAttributes().build());
        event.put(ADVENTURER.get(), Adventurer.createAttributes().build());
        for (ElementalSlimeVariant variant : ElementalSlimeVariant.values())
            event.put(elementalSlimeType(variant), ElementalSlime.createAttributes().build());
        event.put(NewSpellContent.VINE_SERVANT.get(), SpellSummon.attributes(10, 3, 0.18, 0, 0).build());
        event.put(NewSpellContent.FLYING_SWORD.get(), SpellSummon.attributes(20, 10, 0.05, 20, 8).build());
        event.put(NewSpellContent.POISON_TENTACLE.get(), SpellSummon.attributes(8, 4, 0, 0, 0).build());
        // 寒冰傀儡：35 生命、与雪傀儡一致的 0.2 移速，本身不近战，只射冰锥。
        event.put(NewSpellContent.FROST_GOLEM.get(), SpellSummon.attributes(35, 0, 0.2, 0, 0).build());
    }

    private static void registerElementalSlimes() {
        for (ElementalSlimeVariant variant : ElementalSlimeVariant.values()) {
            DeferredHolder<EntityType<?>, EntityType<ElementalSlime>> type = ENTITY_TYPES.register(variant.entityId(), () ->
                    EntityType.Builder.<ElementalSlime>of((entityType, level) -> new ElementalSlime(entityType, level, variant),
                                    MobCategory.CREATURE)
                            // Slime#getDefaultDimensions 会把注册尺寸再乘一次体型（size 1/2/4），
                            // 所以这里必须用原版史莱姆的 0.52：碰撞箱才和「0.5×体型」的模型一样大。
                            // 旧值 2.04 会让精英体变成 8 格宽的隐形墙，玩家看到模型只有 2 格。
                            .sized(0.52F, 0.52F).clientTrackingRange(10).updateInterval(3)
                            .build(MOD_ID + ":" + variant.entityId()));
            ELEMENTAL_SLIMES.put(variant, type);
            MOD_ITEMS.put(variant.powderId(), ITEMS.registerSimpleItem(variant.powderId(),
                    new Item.Properties().rarity(Rarity.UNCOMMON)));
            magicItem(variant.spawnEggId(), ITEMS.register(variant.spawnEggId(), () ->
                    new DeferredSpawnEggItem(type, variant.color() & 0xFFFFFF,
                            (variant.color() ^ 0x6A4C7A) & 0xFFFFFF, new Item.Properties().rarity(Rarity.RARE))));
        }
    }

    private static void simple(String name, Rarity rarity) {
        MOD_ITEMS.put(name, ITEMS.registerSimpleItem(name, new Item.Properties().rarity(rarity)));
    }

    private static void magicFragment(String name, String grade, String subjectTranslation) {
        magicItem(name, ITEMS.register(name, () -> new MagicFragmentItem(
                new Item.Properties().rarity(rarityForGrade(grade)), grade, subjectTranslation)));
    }

    private static void magicItem(String name, DeferredItem<? extends Item> item) {
        MOD_ITEMS.put(name, item);
        MAGIC_ITEM_IDS.add(name);
    }

    /** 元素结晶与对应戒指；element 传 null 表示奥术。结晶是材料，放主创造页。 */
    private static void registerElementAccessories(ElementType element) {
        String elementId = element == null ? MagicEquipment.ARCANE : element.id();
        String crystalId = MagicEquipment.crystalId(elementId);
        MOD_ITEMS.put(crystalId, ITEMS.register(crystalId, () -> new ElementCrystalItem(
                new Item.Properties().stacksTo(16).rarity(Rarity.RARE), element)));
        String ringId = MagicEquipment.ringId(elementId);
        magicItem(ringId, ITEMS.register(ringId, () -> new ElementRingItem(
                new Item.Properties().stacksTo(1).rarity(Rarity.RARE), element)));
    }

    private static void magicStaff(String name, int durability, Rarity rarity, double spellDamage,
                                   double spellPower, double spellHaste, double manaCostReduction,
                                   double manaRegeneration) {
        magicItem(name, ITEMS.register(name, () -> new MagicStaffItem(
                new Item.Properties().durability(durability).rarity(rarity)
                        .attributes(MagicStaffItem.meleeAttributes()),
                new MagicStaffItem.StaffStats(spellDamage, spellPower, spellHaste,
                        manaCostReduction, manaRegeneration))));
    }

    private static Rarity rarityForGrade(String grade) {
        return switch (grade) {
            case "medium" -> Rarity.UNCOMMON;
            case "advanced" -> Rarity.RARE;
            case "supreme", "special" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    private static void ore(String name, float hardness, int resistance, int light, Rarity rarity) {
        block(name, BlockBehaviour.Properties.of().strength(hardness, resistance).requiresCorrectToolForDrops()
                .sound(SoundType.STONE).lightLevel(s -> light), rarity);
    }

    private static void device(String name, Supplier<Block> factory, Rarity rarity) {
        DeferredBlock<Block> holder = BLOCKS.register(name, factory);
        MOD_BLOCKS.put(name, holder);
        MOD_ITEMS.put(name, ITEMS.register(name, () -> new BlockItem(holder.get(), new Item.Properties().rarity(rarity))));
    }

    private static void cluster(String name, float hardness, int light, Rarity rarity) {
        DeferredBlock<Block> holder = BLOCKS.register(name, () -> new AmethystClusterBlock(7.0F, 3.0F,
                BlockBehaviour.Properties.of().strength(hardness).requiresCorrectToolForDrops()
                        .lightLevel(state -> light).sound(SoundType.AMETHYST_CLUSTER).noOcclusion()));
        MOD_BLOCKS.put(name, holder);
        MOD_ITEMS.put(name, ITEMS.register(name, () -> new BlockItem(holder.get(), new Item.Properties().rarity(rarity))));
    }

    private static void block(String name, BlockBehaviour.Properties properties, Rarity rarity) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, properties);
        MOD_BLOCKS.put(name, block);
        MOD_ITEMS.put(name, ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().rarity(rarity))));
    }

    private void addTalentScrollChestLoot(net.neoforged.neoforge.event.LootTableLoadEvent event) {
        if (!event.getName().getNamespace().equals("minecraft") || !event.getName().getPath().startsWith("chests/")) return;
        LootPool.Builder pool = LootPool.lootPool().name("dimensional_mana_talent_scroll")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(0.00005F));
        for (ElementType element : ElementType.values()) pool.add(LootItem.lootTableItem(MOD_ITEMS.get(element.scrollId()).get()));
        event.getTable().addPool(pool.build());
    }

    private void brewingRecipes(RegisterBrewingRecipesEvent event) {
        ItemStack thickPotion = PotionContents.createItemStack(Items.POTION, Potions.THICK);
        event.getBuilder().addRecipe(
                Ingredient.of(thickPotion),
                Ingredient.of(MOD_ITEMS.get("lesser_mana_crystal_dust").get()),
                new ItemStack(MOD_ITEMS.get("mana_harmonizer").get())
        );
        // 剧毒Ⅱ药水 + 特级魔晶粉 → 剧毒Ⅲ药水（中毒 III，18 秒）。
        event.getBuilder().addMix(Potions.STRONG_POISON,
                MOD_ITEMS.get("special_mana_crystal_dust").get(), POISON_III);
    }
}
