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
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> LOOT_CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType,
            net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> ANCIENT_FRAGMENT_LIMIT_CONDITION =
            LOOT_CONDITIONS.register("ancient_fragment_limit", () -> new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(
                    AncientFragmentLimitCondition.CODEC));
    /** r211：法阵案例残片的「每处结构限额」条件（村庄 / 哥布林聚落 / 古代魔法祭坛共用）。 */
    public static final DeferredHolder<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType,
            net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> BLUEPRINT_FRAGMENT_LIMIT_CONDITION =
            LOOT_CONDITIONS.register("blueprint_fragment_limit", () -> new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(
                    BlueprintFragmentLimitCondition.CODEC));
    public static final DeferredHolder<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType,
            net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> ELEMENTAL_SLIME_LOOT_CHANCE =
            LOOT_CONDITIONS.register("elemental_slime_loot_chance", () -> new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(
                    ElementalSlimeLootChance.CODEC));
    /** 沼泽群系结构宝箱的判定：剧毒指环只在这里开得出来。 */
    public static final DeferredHolder<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType,
            net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> SWAMP_BIOME_CONDITION =
            LOOT_CONDITIONS.register("swamp_biome", () -> new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(
                    SwampBiomeCondition.CODEC));
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MOD_ID);

    public static final Map<String, DeferredBlock<Block>> MOD_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<? extends Item>> MOD_ITEMS = new LinkedHashMap<>();
    public static final List<String> MAGIC_ITEM_IDS = new ArrayList<>();
    /** 「次元物语：自然」创造页的摆放顺序：刷怪蛋 → 粗糙装备 → 掉落物。 */
    public static final List<String> NATURAL_ITEM_IDS = new ArrayList<>();
    /** 「次元物语：斗气」创造页的摆放顺序：饰品 → 手册 → 技能书。 */
    public static final List<String> QI_ITEM_IDS = new ArrayList<>();
    /** 「次元物语：魔导」创造页自己收着的新东西，主创造页不再重复放一遍。 */
    public static final List<String> ARCANE_ITEM_IDS = new ArrayList<>();
    public static final List<String> FRAGMENT_GRADES = List.of("lesser", "medium", "advanced", "supreme", "special");
    public static final List<String> SCHOOL_IDS = List.of("destruction", "protection", "healing", "blessing", "curse",
            "control", "summon", "movement", "divination", "psychic", "time", "space", "chaos");
    private static final Map<ElementType, DeferredHolder<MobEffect, MobEffect>> ELEMENT_EFFECTS = new EnumMap<>(ElementType.class);
    private static final Map<ElementalSlimeVariant, DeferredHolder<EntityType<?>, EntityType<ElementalSlime>>> ELEMENTAL_SLIMES =
            new EnumMap<>(ElementalSlimeVariant.class);
    private static final Map<GoblinVariant, DeferredHolder<EntityType<?>, EntityType<Goblin>>> GOBLINS =
            new EnumMap<>(GoblinVariant.class);

    public static final DeferredHolder<MobEffect, MobEffect> MANA_EXHAUSTION = EFFECTS.register("mana_exhaustion", ManaExhaustionEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> BATTLE_QI_COLLAPSE = EFFECTS.register("battle_qi_collapse", BattleQiCollapseEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> MAGIC_RETORT = EFFECTS.register("magic_retort", MagicRetortEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> WARDEN_MARK = EFFECTS.register("warden_mark", WardenMarkEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> ANCIENT_CURSE = EFFECTS.register("ancient_curse", AncientCurseEffect::new);
    /** 植物线：魔力充盈（亲和 ×2.5）、“？”与维斯珀德索恩诅咒。 */
    public static final DeferredHolder<MobEffect, MobEffect> MANA_FULLNESS = EFFECTS.register("mana_fullness",
            PlantEffects.Fullness::new);
    public static final DeferredHolder<MobEffect, MobEffect> VESPER_UNKNOWN = EFFECTS.register("vesper_unknown",
            PlantEffects.Unknown::new);
    public static final DeferredHolder<MobEffect, MobEffect> VESPER_CURSE = EFFECTS.register("vesper_curse",
            PlantEffects.Curse::new);
    /** 强化补魔剂多回的那 15%：存在物品组件上，不需要为每一级补魔剂各开一个物品。 */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<Float>> POTION_BOOST = DATA_COMPONENTS.register("potion_boost",
            () -> net.minecraft.core.component.DataComponentType.<Float>builder()
                    .persistent(com.mojang.serialization.Codec.FLOAT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.FLOAT)
                    .build());
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
     * 完美招架的剑鸣：sounds.json 里的 parry_perfect 挂了十四段素材（最早六段 + 重画六段
     * + 两段只狼式震刀采样），播放时由原版随机挑一段。
     * 必须和其他音效一样在注册阶段登记，不能在第一次弹刀时才注册（那会撞上注册表已冻结，直接崩游戏）。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_PERFECT = SOUNDS.register("parry_perfect",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "parry_perfect")));
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
    /** 加料食物（卢梅莉亚粉末 + 任意食物）：产物要按食物现场生成，只能走特制配方。 */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PlantFood>> PLANT_FOOD_SERIALIZER =
            RECIPE_SERIALIZERS.register("plant_food", PlantFood.Serializer::new);
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
    /** 五种魔法植物：区块生成时就按群系、高度与地表浓度种下。 */
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> MANA_PLANTS = FEATURES.register(
            "mana_plants", ManaPlantFeature::new);
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

    public static final DeferredHolder<StructureType<?>, StructureType<GoblinCampStructure>> GOBLIN_CAMP_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("goblin_camp", () -> () -> GoblinCampStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> GOBLIN_CAMP_PIECE =
            STRUCTURE_PIECES.register("goblin_camp", () -> (StructurePieceType.ContextlessType)GoblinCampPiece::new);

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
        // 专业鉴定眼镜：基础鉴定眼镜的升级版，装在同一个「首饰」栏位，靠魔素续航换取深层数据与雷达。
        MOD_ITEMS.put("professional_appraisal_glasses", ITEMS.register("professional_appraisal_glasses",
                () -> new ProfessionalAppraisalGlassesItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))));
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
        // 二十三类新饰品（按梯度 t3→t0 注册，创造页里也按这个顺序排）与三件合成材料。
        for (Relics.Relic relic : Relics.ALL) {
            magicItem(relic.id(), ITEMS.register(relic.id(), () -> new RelicAccessoryItem(
                    relicProperties(relic), relic)));
        }
        // 雨靴：脚饰栏位，工作台用两个水元素结晶粉末 + 两个皮革合成。
        magicItem(RainBootsItem.ITEM_ID, ITEMS.register(RainBootsItem.ITEM_ID,
                () -> new RainBootsItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
        // 雨衣：背饰栏位，工作台用六个皮革 + 三块魔力布匹合成。
        magicItem(RainCoatItem.ITEM_ID, ITEMS.register(RainCoatItem.ITEM_ID,
                () -> new RainCoatItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
        // 斗气系：手套与护符两件饰品、战斗手册一本、技能书三本（走自己的创造页）。
        MOD_ITEMS.put(BattleQiGloveItem.ITEM_ID, ITEMS.register(BattleQiGloveItem.ITEM_ID,
                () -> new BattleQiGloveItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
        MOD_ITEMS.put(SwordsmanCharmItem.ITEM_ID, ITEMS.register(SwordsmanCharmItem.ITEM_ID,
                () -> new SwordsmanCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        MOD_ITEMS.put(CombatCodexItem.ITEM_ID, ITEMS.register(CombatCodexItem.ITEM_ID,
                () -> new CombatCodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        for (QiProgression.Ability ability : QiProgression.Ability.values()) {
            MOD_ITEMS.put(ability.manualId(), ITEMS.register(ability.manualId(), () -> new QiSkillBookItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE), ability)));
        }
        QI_ITEM_IDS.add(BattleQiGloveItem.ITEM_ID);
        QI_ITEM_IDS.add(SwordsmanCharmItem.ITEM_ID);
        QI_ITEM_IDS.add(CombatCodexItem.ITEM_ID);
        for (QiProgression.Ability ability : QiProgression.Ability.values()) QI_ITEM_IDS.add(ability.manualId());
        simple("purification_core", Rarity.RARE);
        simple("radiant_core", Rarity.EPIC);
        simple("omniscient_page", Rarity.EPIC);
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
        // 「次元物语：自然」的主角：五种魔法植物、植物粉末与植物药剂，排在最前面。
        ManaPlants.register();
        registerElementalSlimes();
        registerGoblins();
        registerMagicCircle();
    }

    /**
     * 「次元物语：魔导」第 1 阶段的内容：刻印石胚、魔法阵绘制台基座、魔导调试棒。
     *
     * <p>粉末（41 种）、魔力布匹、魔法绸缎、魔晶加工台都是现有体系里已经有的东西，
     * 这一栏只负责把它们摆到一起，不再重复注册新物品。</p>
     */
    private static void registerMagicCircle() {
        MOD_ITEMS.put("carving_stone_blank", ITEMS.register("carving_stone_blank", () ->
                new LoreItem(new Item.Properties().rarity(Rarity.COMMON),
                        "tooltip.dimensional_mana.carving_stone_blank")));
        ARCANE_ITEM_IDS.add("carving_stone_blank");

        device("magic_circle_base", () -> new MagicCircleBaseBlock(BlockBehaviour.Properties.of()
                .strength(2.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)
                .noOcclusion().lightLevel(state -> 4)), Rarity.UNCOMMON);
        ARCANE_ITEM_IDS.add("magic_circle_base");

        MOD_ITEMS.put(MagicCircleWandItem.ITEM_ID, ITEMS.register(MagicCircleWandItem.ITEM_ID, () ->
                new MagicCircleWandItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        ARCANE_ITEM_IDS.add(MagicCircleWandItem.ITEM_ID);

        // 第 3 阶段：贴面投影锚点。它不是「合成出来再摆」的方块，而是拿着蓝图右键方块表面生成，
        // 所以不注册物品形态，只注册方块本体 + 方块实体。
        DeferredBlock<Block> projection = BLOCKS.register(MagicCircleProjectionBlock.BLOCK_ID,
                () -> new MagicCircleProjectionBlock(BlockBehaviour.Properties.of()
                        .strength(0.6F).sound(SoundType.AMETHYST).noOcclusion().dynamicShape()
                        .lightLevel(state -> 10)));
        MOD_BLOCKS.put(MagicCircleProjectionBlock.BLOCK_ID, projection);

        // 教学蓝图：创造物品栏就能拿，生存里靠箱子 / 交易（后续阶段接掉落表）。
        MOD_ITEMS.put(MagicCircleBlueprints.SPARK_ITEM, ITEMS.register(MagicCircleBlueprints.SPARK_ITEM,
                () -> new WornBlueprintItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                        MagicCircleBlueprints.SPARK_ITEM)));
        ARCANE_ITEM_IDS.add(MagicCircleBlueprints.SPARK_ITEM);
        MOD_ITEMS.put(MagicCircleBlueprints.FIREBALL_ITEM, ITEMS.register(MagicCircleBlueprints.FIREBALL_ITEM,
                () -> new WornBlueprintItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                        MagicCircleBlueprints.FIREBALL_ITEM)));
        ARCANE_ITEM_IDS.add(MagicCircleBlueprints.FIREBALL_ITEM);
        MOD_ITEMS.put(MagicCircleBlueprints.HEALING_ITEM, ITEMS.register(MagicCircleBlueprints.HEALING_ITEM,
                () -> new WornBlueprintItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                        MagicCircleBlueprints.HEALING_ITEM)));
        ARCANE_ITEM_IDS.add(MagicCircleBlueprints.HEALING_ITEM);
        // v2.2：掘矿阵 / 守望阵两张进阶教学蓝图（终端协议 + 边界声明的示范）。
        MOD_ITEMS.put(MagicCircleBlueprints.MINE_ITEM, ITEMS.register(MagicCircleBlueprints.MINE_ITEM,
                () -> new WornBlueprintItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                        MagicCircleBlueprints.MINE_ITEM)));
        ARCANE_ITEM_IDS.add(MagicCircleBlueprints.MINE_ITEM);
        MOD_ITEMS.put(MagicCircleBlueprints.GUARD_ITEM, ITEMS.register(MagicCircleBlueprints.GUARD_ITEM,
                () -> new WornBlueprintItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                        MagicCircleBlueprints.GUARD_ITEM)));
        ARCANE_ITEM_IDS.add(MagicCircleBlueprints.GUARD_ITEM);

        // 第 4 阶段：刻印石板。刻印石胚 + 已抄录载体 + 超导粉在魔晶加工台里烧出来，
        // 贴到方块表面就是能正式运行的实体法阵锚点。
        MOD_ITEMS.put(EtchedSlateItem.ITEM_ID, ITEMS.register(EtchedSlateItem.ITEM_ID, () ->
                new EtchedSlateItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))));
        ARCANE_ITEM_IDS.add(EtchedSlateItem.ITEM_ID);
    }

    /** 魔导调试棒的物品实例；方块交互时用来判断手里拿的是不是它。 */
    public static Item arcaneDebugWandItem() {
        return MOD_ITEMS.get(MagicCircleWandItem.ITEM_ID).get();
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagicCircleBaseBlockEntity>> MAGIC_CIRCLE_BASE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("magic_circle_base", () -> BlockEntityType.Builder.of(MagicCircleBaseBlockEntity::new,
                    MOD_BLOCKS.get("magic_circle_base").get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagicCircleProjectionBlockEntity>>
            MAGIC_CIRCLE_PROJECTION_BLOCK_ENTITY = BLOCK_ENTITIES.register(MagicCircleProjectionBlock.BLOCK_ID,
            () -> BlockEntityType.Builder.of(MagicCircleProjectionBlockEntity::new,
                    MOD_BLOCKS.get(MagicCircleProjectionBlock.BLOCK_ID).get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOD_CREATIVE_TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dimensional_mana.main"))
            .icon(() -> new ItemStack(MOD_ITEMS.get("special_mana_crystal").get()))
            .displayItems((parameters, output) -> MOD_ITEMS.forEach((id, item) -> {
                if (!MAGIC_ITEM_IDS.contains(id) && !NATURAL_ITEM_IDS.contains(id)
                        && !QI_ITEM_IDS.contains(id) && !ARCANE_ITEM_IDS.contains(id)) output.accept(item.get());
            })).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAGIC_CREATIVE_TAB = CREATIVE_TABS.register("magic", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dimensional_mana.magic"))
            .icon(() -> new ItemStack(MOD_ITEMS.get("element_resonance_stone").get()))
            .displayItems((parameters, output) -> orderedMagicItemIds().forEach(id -> {
                DeferredItem<? extends Item> item = MOD_ITEMS.get(id);
                if (item != null) output.accept(item.get());
            }))
            .build());

    /** 「次元物语：斗气」：物理 / 剑士路线的全部物品。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BATTLE_QI_CREATIVE_TAB = CREATIVE_TABS.register(
            "battle_qi", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dimensional_mana.battle_qi"))
                    .icon(() -> new ItemStack(MOD_ITEMS.get(CombatCodexItem.ITEM_ID).get()))
                    .displayItems((parameters, output) -> {
                        for (String id : QI_ITEM_IDS) {
                            DeferredItem<? extends Item> item = MOD_ITEMS.get(id);
                            if (item != null) output.accept(item.get());
                        }
                    }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NATURAL_CREATIVE_TAB = CREATIVE_TABS.register(
            "natural", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dimensional_mana.natural"))
                    .icon(() -> new ItemStack(MOD_ITEMS.get(GoblinVariant.WARRIOR.spawnEggId()).get()))
                    .displayItems((parameters, output) -> {
                        for (String id : NATURAL_ITEM_IDS) {
                            DeferredItem<? extends Item> item = MOD_ITEMS.get(id);
                            if (item != null) output.accept(item.get());
                        }
                    }).build());

    /**
     * 「次元物语：魔导」创造页：绘制台、调试棒、刻印石胚，以及魔法阵要用的 41 种粉末。
     *
     * <p>粉末顺序就是绘制台粉末盘四个标签页的顺序：原版粉 → 魔晶粉 → 元素粉 → 染料；
     * 最后是三种蓝图载体（纸 = 草稿，魔力布匹 = 单层，魔法绸缎 = 多层）。</p>
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARCANE_CREATIVE_TAB = CREATIVE_TABS.register(
            "arcane", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dimensional_mana.arcane"))
                    .icon(() -> new ItemStack(MOD_ITEMS.get(MagicCircleWandItem.ITEM_ID).get()))
                    .displayItems((parameters, output) -> {
                        output.accept(MOD_ITEMS.get("magic_circle_base").get());
                        output.accept(MOD_ITEMS.get(MagicCircleWandItem.ITEM_ID).get());
                        output.accept(MOD_ITEMS.get("carving_stone_blank").get());
                        output.accept(MOD_ITEMS.get(EtchedSlateItem.ITEM_ID).get());
                        output.accept(MOD_ITEMS.get(MagicCircleBlueprints.SPARK_ITEM).get());
                        output.accept(MOD_ITEMS.get(MagicCircleBlueprints.FIREBALL_ITEM).get());
                        output.accept(MOD_ITEMS.get(MagicCircleBlueprints.HEALING_ITEM).get());
                        output.accept(MOD_ITEMS.get(MagicCircleBlueprints.MINE_ITEM).get());
                        output.accept(MOD_ITEMS.get(MagicCircleBlueprints.GUARD_ITEM).get());
                        for (MagicCirclePowders.Category category : MagicCirclePowders.Category.values()) {
                            for (MagicCirclePowders.Powder powder : MagicCirclePowders.byCategory(category)) {
                                ItemStack stack = powder.stack();
                                if (!stack.isEmpty()) output.accept(stack);
                            }
                        }
                        output.accept(Items.PAPER);
                        output.accept(MOD_ITEMS.get(MagicEquipment.MAGIC_CLOTH).get());
                        output.accept(MOD_ITEMS.get(MagicEquipment.MAGIC_SILK).get());
                    }).build());

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

    /** 饰品的物品属性：t0/t1 带附魔光效，其余按梯度给普通稀有度。 */
    private static Item.Properties relicProperties(Relics.Relic relic) {
        Item.Properties properties = new Item.Properties().stacksTo(1).rarity(relic.tier().rarity());
        return relic.tier().glint()
                ? properties.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                : properties;
    }

    /** 饰品的细分排序值；不是饰品就返回 -1。 */
    private static int accessoryRank(String id) {
        for (Relics.Relic relic : Relics.ALL) if (relic.id().equals(id)) return 35;
        if (RainBootsItem.ITEM_ID.equals(id)) return 36;
        if (RainCoatItem.ITEM_ID.equals(id)) return 37;
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

    public static EntityType<Goblin> goblinType(GoblinVariant variant) {
        DeferredHolder<EntityType<?>, EntityType<Goblin>> holder = GOBLINS.get(variant);
        return holder == null ? null : holder.get();
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
        GoblinSounds.register();
        // r212 冒险者男声台词（右键搭话/成交/拒绝），不再用村民音。
        AdventurerSounds.register();
        SOUNDS.register(modBus);
        MENUS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        RECIPE_TYPES.register(modBus);
        RoughEquipment.ARMOR_MATERIALS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        LOOT_CONDITIONS.register(modBus);
        CREATIVE_TABS.register(modBus);
        FEATURES.register(modBus);
        ENTITY_TYPES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        SpellAttributes.ATTRIBUTES.register(modBus);
        modBus.addListener(this::createEntityAttributes);
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> MobProfile.invalidate());
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> MobProfile.invalidate());
        modBus.addListener(SpellAttributes::attachToLivingEntities);
        modBus.addListener(ManaSyncPayload::register);
        modBus.addListener(AppraisalSyncPayload::register);
        modBus.addListener(ProAppraisalPayload::register);
        modBus.addListener(ManaRadarPayload::register);
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
        modBus.addListener(ParryFeedbackPayload::register);
        modBus.addListener(ParryStancePayload::register);
        modBus.addListener(QiSyncPayload::register);
        modBus.addListener(QiActionPayload::register);
        modBus.addListener(OpenCombatCodexPayload::register);
        modBus.addListener(MagicCircleSyncPayload::register);
        modBus.addListener(MagicCircleActionPayload::register);
        modBus.addListener(MagicCircleNetActionPayload::register);
        modBus.addListener(MagicCircleRemoteListPayload::register);
        modBus.addListener(MagicCircleCodeResultPayload::register);
        if (FMLEnvironment.dist == Dist.CLIENT) ClientModEvents.register(modBus);
        ManaEvents events = new ManaEvents();
        FragmentLootEvents fragmentLoot = new FragmentLootEvents();
        AccessoryLootEvents accessoryLoot = new AccessoryLootEvents();
        NeoForge.EVENT_BUS.addListener(ManaCommands::register);
        NeoForge.EVENT_BUS.addListener(ManaFurnaceRecipeBridge::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ManaFurnaceRecipeBridge::onDatapackSync);
        // 末影龙复活：死亡拦截排在其它死亡处理之前，取消之后别人才看得到 isCanceled()。
        NeoForge.EVENT_BUS.addListener(DragonRevival::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DragonRevival::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DragonRevival::onHeal);
        NeoForge.EVENT_BUS.addListener(DragonRevival::onEntityTick);
        NeoForge.EVENT_BUS.addListener(events::onJoin);
        NeoForge.EVENT_BUS.addListener(events::onLogin);
        NeoForge.EVENT_BUS.addListener(events::onLogout);
        NeoForge.EVENT_BUS.addListener(events::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onServerStopped);
        NeoForge.EVENT_BUS.addListener(events::onClone);
        NeoForge.EVENT_BUS.addListener(events::onVanillaExperience);
        NeoForge.EVENT_BUS.addListener(events::onWake);
        NeoForge.EVENT_BUS.addListener(CriticalSystem::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(events::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onDamagePre);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onDamagePost);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onHeal);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onEffectApplicable);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onLivingChangeTarget);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onVillagerInteract);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onItemEntityJoin);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onLevelTick);
        NeoForge.EVENT_BUS.addListener(RelicLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(RelicLoot::addEntityDrops);
        NeoForge.EVENT_BUS.addListener(events::onDamagePre);
        NeoForge.EVENT_BUS.addListener(events::onStruckByLightning);
        NeoForge.EVENT_BUS.addListener(events::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(events::onTick);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::tickLevel);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::tickLevel);
        NeoForge.EVENT_BUS.addListener(GoblinSpawning::tickLevel);
        NeoForge.EVENT_BUS.addListener(GoblinSpawning::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(GoblinSpawning::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GoblinSpawning::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RoughGearEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RoughGearEvents::onShieldBlock);
        NeoForge.EVENT_BUS.addListener(RoughGearEvents::onAnvilUpdate);
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
        // 斗气招架：姿态下右键不再交互；左键弹反由客户端发动作包、服务端结算。
        NeoForge.EVENT_BUS.addListener(BattleQiParry::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(BattleQiParry::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(BattleQiParry::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(BattleQiParry::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(WoodSpells::onExplode);
        NeoForge.EVENT_BUS.addListener(this::addTalentScrollChestLoot);
        NeoForge.EVENT_BUS.addListener(fragmentLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(fragmentLoot::addWitchDrops);
        NeoForge.EVENT_BUS.addListener(AncientFragmentLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(AncientFragmentLoot::addEntityDrops);
        // r211：法阵案例残片进三处宝箱（祭坛 55%、哥布林 25%、村庄 40%，各有每处上限）。
        NeoForge.EVENT_BUS.addListener(BlueprintFragmentLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(accessoryLoot::onLootTableLoad);
        NeoForge.EVENT_BUS.addListener(accessoryLoot::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(events::onDamagePost);
        NeoForge.EVENT_BUS.addListener(events::onDeath);
        NeoForge.EVENT_BUS.addListener(events::onHeal);
        NeoForge.EVENT_BUS.addListener(events::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(events::onMobEntityJoin);
        NeoForge.EVENT_BUS.addListener(events::onExplosion);
        NeoForge.EVENT_BUS.addListener(events::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ManaPlantSpawning::tickLevel);
        NeoForge.EVENT_BUS.addListener(ManaPlantSpawning::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ManaPlantSpawning::onJump);
        NeoForge.EVENT_BUS.addListener(ManaPlantSpawning::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(PlantEvents::onEffectExpired);
        NeoForge.EVENT_BUS.addListener(PlantEvents::onUseFinish);
        NeoForge.EVENT_BUS.addListener(ElementPlating::onTooltip);
        NeoForge.EVENT_BUS.addListener(MagicCircleProjectionEvents::onTooltip);
        NeoForge.EVENT_BUS.addListener(MagicCircleProjectionEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(MagicCircleProjectionEvents::onRightClickItem);
        // 法阵召唤物的生命周期：到期自动消散（文档 14.2 第 4 条）。
        NeoForge.EVENT_BUS.addListener(MagicCircleRites::onEntityTick);
        // r211 召唤物守则：友伤免疫 + 玩家被咬时守卫锚点反击。
        NeoForge.EVENT_BUS.addListener(MagicCircleRites::onSummonIncomingDamage);
        // r211 声明「死亡掉落空」：法阵召唤物被打死什么都不吐。
        NeoForge.EVENT_BUS.addListener(MagicCircleRites::onSummonDeathDrops);
        // r211 蓝图修改模式：下线 / 关服都要把台面上的蓝图退到人身上。
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent leftEvent) ->
                        MagicCircleDrawing.onPlayerLeft(leftEvent.getEntity()));
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent stopEvent) -> MagicCircleDrawing.flushAll());
        // r217 门控保持帧（规范 v3.0 二）：跳跃 / 攻击 / 受击这类 1 刻瞬时动作记 0.5 秒宽限，
        // 20 刻一轮的判定也抓得住；停服清缓存防旧刻号误判。
        NeoForge.EVENT_BUS.addListener(MagicCircleGates::onJump);
        NeoForge.EVENT_BUS.addListener(MagicCircleGates::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent stopEvent) -> MagicCircleGates.clearActions());
        NeoForge.EVENT_BUS.addListener(ElementPlating::onGrindstonePlace);
        NeoForge.EVENT_BUS.addListener(ElementPlating::onGrindstoneTake);
        // r211 砂轮新功能：把带内容的蓝图磨回空白，返还 25% 随机粉末。
        NeoForge.EVENT_BUS.addListener(BlueprintGrinding::onGrindstonePlace);
        NeoForge.EVENT_BUS.addListener(BlueprintGrinding::onGrindstoneTake);
        // r212 修「蓝图放不进砂轮」：原版输入槽拒收纸类物品，打开界面时换成放行版槽位。
        NeoForge.EVENT_BUS.addListener(BlueprintGrinding::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(this::brewingRecipes);
        container.registerConfig(ModConfig.Type.COMMON, ManaConfig.SPEC, "dimensional_mana-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, MobCombatConfig.SPEC, "dimensional_mana-mobs.toml");
        container.registerConfig(ModConfig.Type.COMMON, AppraisalConfig.SPEC, "dimensional_mana-appraisal.toml");
        container.registerConfig(ModConfig.Type.COMMON, CriticalConfig.SPEC, "dimensional_mana-critical.toml");
        container.registerConfig(ModConfig.Type.COMMON, MagicCircleConfig.SPEC, "dimensional_mana-magiccircle.toml");
        container.registerConfig(ModConfig.Type.COMMON, SpawnConfig.SPEC, "dimensional_mana-spawns.toml");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // 客户端配置与 HUD 可视化编辑器的入口登记在 ClientModEvents 里：
            // 主类构造函数在专用服务器上也会执行，任何对 Screen 子类的直接引用都会让它起不来。
            ClientModEvents.registerConfigs(container);
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
        for (GoblinVariant variant : GoblinVariant.values())
            event.put(goblinType(variant), Goblin.createAttributes().build());
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

    /** 五种哥布林：实体、刷怪蛋，以及整套粗糙装备与掉落物。 */
    private static void registerGoblins() {
        for (GoblinVariant variant : GoblinVariant.values()) {
            String entityId = variant.entityId();
            DeferredHolder<EntityType<?>, EntityType<Goblin>> type = ENTITY_TYPES.register(entityId, () ->
                    EntityType.Builder.<Goblin>of((entityType, level) -> new Goblin(entityType, level, variant),
                                    MobCategory.MONSTER)
                            .sized(variant.width(), variant.height()).clientTrackingRange(10)
                            .build(MOD_ID + ":" + entityId));
            GOBLINS.put(variant, type);
            DeferredItem<Item> egg = ITEMS.register(variant.spawnEggId(), () -> new DeferredSpawnEggItem(type,
                    spawnEggPrimary(variant), spawnEggSecondary(variant),
                    new Item.Properties().rarity(Rarity.UNCOMMON)));
            MOD_ITEMS.put(variant.spawnEggId(), egg);
            NATURAL_ITEM_IDS.add(variant.spawnEggId());
        }
        RoughEquipment.register();
        NATURAL_ITEM_IDS.addAll(RoughEquipment.ORDER);
    }

    private static int spawnEggPrimary(GoblinVariant variant) {
        return switch (variant) {
            case YOUNG -> 0x8FA659;
            case WARRIOR -> 0x6E8C3A;
            case ARCHER -> 0x7A9A46;
            case SHAMAN -> 0x5C8C6E;
            case CHIEF -> 0x3F5A24;
        };
    }

    private static int spawnEggSecondary(GoblinVariant variant) {
        return switch (variant) {
            case YOUNG -> 0x3B4A12;
            case WARRIOR -> 0x4B3A22;
            case ARCHER -> 0x2F4F2F;
            case SHAMAN -> 0x6A3FA0;
            case CHIEF -> 0x8B1A1A;
        };
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
        // 植物线：魔素调和剂 + 粉末 → 植物药剂；补魔剂 + 蕾薇莉亚粉末 → 强化补魔剂。
        PlantBrewing.register(event);
        // 剧毒Ⅱ药水 + 特级魔晶粉 → 剧毒Ⅲ药水（中毒 III，18 秒）。
        event.getBuilder().addMix(Potions.STRONG_POISON,
                MOD_ITEMS.get("special_mana_crystal_dust").get(), POISON_III);
    }
}
