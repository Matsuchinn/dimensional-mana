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
import net.neoforged.bus.api.EventPriority;
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
    /**
     * 群系来源类型注册表。r286 用它挂 {@link WastelandOverlayBiomeSource}——
     * 世界预设里 {@code "type": "dimensional_mana:wasteland_overlay"} 那句就是在这里查到的。
     */
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(BuiltInRegistries.BIOME_SOURCE, MOD_ID);
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>,
            com.mojang.serialization.MapCodec<WastelandOverlayBiomeSource>> WASTELAND_OVERLAY_BIOME_SOURCE =
            BIOME_SOURCES.register("wasteland_overlay", () -> WastelandOverlayBiomeSource.CODEC);

    public static final Map<String, DeferredBlock<Block>> MOD_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<? extends Item>> MOD_ITEMS = new LinkedHashMap<>();
    public static final List<String> MAGIC_ITEM_IDS = new ArrayList<>();
    /** 「次元物语：自然」创造页的摆放顺序：刷怪蛋 → 粗糙装备 → 掉落物。 */
    public static final List<String> NATURAL_ITEM_IDS = new ArrayList<>();
    /** 魂火残渣：魔蚀骸骨的死亡掉落，也是它身上那点魂火留下的唯一实物。 */
    public static final String SOUL_FIRE_RESIDUE_ID = "soul_fire_residue";
    /** 魔蚀蜘蛛眼：魔蚀织网者的掉落物。 */
    public static final String CORRUPTED_SPIDER_EYE_ID = "corrupted_spider_eye";
    /** 魔蚀蛛丝：破坏魔蚀蛛网掉 1 个。 */
    public static final String CORRUPTED_SILK_ID = "corrupted_silk";
    /** 畸变下界之星：魔蚀凋零的必掉材料。 */
    public static final String DISTORTED_NETHER_STAR_ID = "distorted_nether_star";
    /** 「次元物语：斗气」创造页的摆放顺序：饰品 → 手册 → 技能书。 */
    public static final List<String> QI_ITEM_IDS = new ArrayList<>();
    /** 「次元物语：魔导」创造页自己收着的新东西，主创造页不再重复放一遍。 */
    public static final List<String> ARCANE_ITEM_IDS = new ArrayList<>();
    /**
     * 魔精钢装备：按需求直接摆进原版对应的创造页（战斗 / 工具 / 材料），
     * 所以主创造页不再重复收一份。
     */
    public static final List<String> VANILLA_TAB_ITEM_IDS = new ArrayList<>();
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
    /** 聚魔爆裂体的魔核冷却：200 刻不能动、灰白、受伤 +50%。 */
    public static final DeferredHolder<MobEffect, MobEffect> CORE_COOLDOWN = EFFECTS.register("core_cooldown", CoreCooldownEffect::new);
    /** 魔蚀：魔蚀织网者留下的强制侵蚀（每 30 刻 2 点真伤 + 20 魔素）。 */
    public static final DeferredHolder<MobEffect, MobEffect> MAGIC_EROSION = EFFECTS.register("magic_erosion", MagicErosionEffect::new);
    /** 植物线：魔力充盈（亲和 ×2.5）、“？”与维斯珀德索恩诅咒。 */
    public static final DeferredHolder<MobEffect, MobEffect> MANA_FULLNESS = EFFECTS.register("mana_fullness",
            PlantEffects.Fullness::new);
    public static final DeferredHolder<MobEffect, MobEffect> VESPER_UNKNOWN = EFFECTS.register("vesper_unknown",
            PlantEffects.Unknown::new);
    public static final DeferredHolder<MobEffect, MobEffect> VESPER_CURSE = EFFECTS.register("vesper_curse",
            PlantEffects.Curse::new);
    /** 元素精通强化：元素精通药水的属性型效果，Ⅰ级 +8，每升一级再多 +8。 */
    public static final DeferredHolder<MobEffect, MobEffect> ELEMENTAL_MASTERY = EFFECTS.register("elemental_mastery",
            ElementalMasteryEffect::new);
    /** 畏缩：史莱姆待在驱逐火把范围里时挂上的隐形标记（不显示粒子 / 图标）。 */
    public static final DeferredHolder<MobEffect, MobEffect> SLIME_DREAD = EFFECTS.register("slime_dread",
            SlimeDreadEffect::new);
    /** 【敬而远之】：击杀哥布林营地首领后获得的 2 小时威压，哥布林见了你掉头就跑。 */
    public static final DeferredHolder<MobEffect, MobEffect> GOBLIN_DREAD = EFFECTS.register("goblin_dread",
            GoblinDreadEffect::new);
    /** 【狂化】：魔蚀织网者的虫群意识叫来的节肢动物，10 秒内移速 +50%、受到伤害 −25%。 */
    public static final DeferredHolder<MobEffect, MobEffect> HIVE_FRENZY = EFFECTS.register("hive_frenzy",
            HiveFrenzyEffect::new);
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
    /** 蔽魔：一层骗过魔素感知的假面，敌对生物不会主动锁定持有者；攻击、施法、挨打即碎。 */
    public static final DeferredHolder<MobEffect, MobEffect> PSEUDOMANA_VEIL = EFFECTS.register("pseudomana_veil",
            PseudomanaVeilEffect::new);
    /** 魔石病：无变种生物在高魔素区滞留 120 秒中招；金苹果喂食可治愈（见 {@link ManaStoneSystem}）。 */
    public static final DeferredHolder<MobEffect, MobEffect> MANA_STONE_DISEASE = EFFECTS.register("mana_stone_disease",
            ManaStoneDiseaseEffect::new);
    /** 升华药水的六个状态：生命跃迁、神圣庇护、破灭之力、生命涌动、死斗意志、药效反噬。 */
    public static final DeferredHolder<MobEffect, MobEffect> ASCENSION = EFFECTS.register("ascension",
            AscensionEffect.Ascension::new);
    public static final DeferredHolder<MobEffect, MobEffect> ASCENSION_WARD = EFFECTS.register("ascension_ward",
            AscensionEffect.Ward::new);
    public static final DeferredHolder<MobEffect, MobEffect> ASCENSION_MIGHT = EFFECTS.register("ascension_might",
            AscensionEffect.Might::new);
    public static final DeferredHolder<MobEffect, MobEffect> ASCENSION_REBIRTH = EFFECTS.register("ascension_rebirth",
            AscensionEffect.Rebirth::new);
    public static final DeferredHolder<MobEffect, MobEffect> ASCENSION_DEATHWARD = EFFECTS.register("ascension_deathward",
            AscensionEffect.Deathward::new);
    public static final DeferredHolder<MobEffect, MobEffect> ASCENSION_BACKLASH = EFFECTS.register("ascension_backlash",
            AscensionEffect.Backlash::new);
    /**
     * 剧毒Ⅲ药水：中毒 III，持续 18 秒（360 tick）。
     * 名称显式写成 poison_iii，客户端就会去找 item.minecraft.potion.effect.poison_iii 这几个翻译键，
     * 喷溅、滞留和药箭版本也共用同一个后缀。
     */
    public static final DeferredHolder<Potion, Potion> POISON_III = POTIONS.register("poison_iii",
            () -> new Potion("poison_iii", new MobEffectInstance(MobEffects.POISON, 18 * 20, 2)));

    // ── 魔蚀药水线（r268）───────────────────────────────────────────────────
    // 四档等级对应魔蚀效果的 amplifier 0~3（发作间隔 30/26/22/18 刻），
    // 普通版 1 分钟、红石延长版 2 分钟；喷溅 / 滞留走原版容器配方，自动套用。

    /** 魔蚀药水等级的展示名后缀：Ⅰ~Ⅳ。 */
    public static final List<String> EROSION_TIERS = List.of("i", "ii", "iii", "iv");

    /** 普通版魔蚀药水Ⅰ~Ⅳ（60 秒）。 */
    public static final List<DeferredHolder<Potion, Potion>> MAGIC_EROSION_POTIONS = registerErosionPotions(false);
    /** 红石延长版魔蚀药水Ⅰ~Ⅳ（120 秒）。 */
    public static final List<DeferredHolder<Potion, Potion>> LONG_MAGIC_EROSION_POTIONS = registerErosionPotions(true);

    private static List<DeferredHolder<Potion, Potion>> registerErosionPotions(boolean extended) {
        List<DeferredHolder<Potion, Potion>> out = new ArrayList<>(4);
        for (int tier = 0; tier < 4; tier++) {
            String id = (extended ? "long_magic_erosion_" : "magic_erosion_") + EROSION_TIERS.get(tier);
            // 循环变量不能直接被 lambda 捕获，拷一份定值进去。
            final int level = tier;
            out.add(POTIONS.register(id, () -> new Potion(id,
                    new MobEffectInstance(MAGIC_EROSION, (extended ? 120 : 60) * 20, level))));
        }
        return List.copyOf(out);
    }

    /** 蔽魔药水（60 秒）与红石延长版（180 秒）。 */
    public static final DeferredHolder<Potion, Potion> PSEUDOMANA_VEIL_POTION = POTIONS.register("pseudomana_veil",
            () -> new Potion("pseudomana_veil", new MobEffectInstance(PSEUDOMANA_VEIL, 60 * 20)));
    public static final DeferredHolder<Potion, Potion> LONG_PSEUDOMANA_VEIL_POTION = POTIONS.register("long_pseudomana_veil",
            () -> new Potion("long_pseudomana_veil", new MobEffectInstance(PSEUDOMANA_VEIL, 180 * 20)));

    // ── 元素精通药水线 ──────────────────────────────────────────────────────
    // Ⅰ~Ⅵ 六档，基础 15 秒、红石延长版 30 秒；Ⅰ级 +8 精通，每升一级再多 +8。
    // 药水本身用原版药水瓶，所以火药喷溅、龙息滞留这些原版规则天然通用。

    /** 普通版元素精通药水Ⅰ~Ⅵ（15 秒）。 */
    public static final List<DeferredHolder<Potion, Potion>> ELEMENTAL_MASTERY_POTIONS =
            registerElementalMasteryPotions(false);
    /** 红石延长版元素精通药水Ⅰ~Ⅵ（30 秒）。 */
    public static final List<DeferredHolder<Potion, Potion>> LONG_ELEMENTAL_MASTERY_POTIONS =
            registerElementalMasteryPotions(true);

    private static List<DeferredHolder<Potion, Potion>> registerElementalMasteryPotions(boolean extended) {
        List<DeferredHolder<Potion, Potion>> out = new ArrayList<>(ElementalMasteryPotions.MAX_LEVEL);
        for (int level = 1; level <= ElementalMasteryPotions.MAX_LEVEL; level++) {
            String id = ElementalMasteryPotions.id(level, extended);
            final int amplifier = level - 1;
            final int ticks = extended ? ElementalMasteryPotions.LONG_TICKS : ElementalMasteryPotions.BASE_TICKS;
            out.add(POTIONS.register(id, () -> new Potion(id,
                    new MobEffectInstance(ELEMENTAL_MASTERY, ticks, amplifier))));
        }
        return List.copyOf(out);
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> MANA_FORGE_BURN = SOUNDS.register("mana_forge_burn",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "mana_forge_burn")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PROFESSIONAL_FURNACE_BURN = SOUNDS.register("professional_furnace_burn",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "professional_furnace_burn")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PURE_LIQUID_MANA_BUBBLE = SOUNDS.register("pure_liquid_mana_bubble",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pure_liquid_mana_bubble")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL_PROCESS_COMPLETE = SOUNDS.register("crystal_process_complete",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "crystal_process_complete")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_CRYSTAL_ANVIL_BREAK = SOUNDS.register("magic_crystal_anvil_break",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "magic_crystal_anvil_break")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_CRYSTAL_ANVIL_PLACE = SOUNDS.register("magic_crystal_anvil_place",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "magic_crystal_anvil_place")));
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
     * 反虚空领域的三个史诗级转场音效：第一次降临、之后每次回到反虚空、离开反虚空。
     *
     * <p>音源是 CC0 素材（Kenney Sci-Fi Sounds）叠层合成的，许可记录在 {@code SOUND_SOURCES.json}。
     * 播放逻辑在 {@link AntiVoidSystem}：换维度时客户端还在读图，所以由服务端排队、
     * 等玩家真正落地之后再发 {@code playNotifySound}。</p>
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> ANTI_VOID_ARRIVE = SOUNDS.register("anti_void_arrive",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "anti_void_arrive")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ANTI_VOID_RETURN = SOUNDS.register("anti_void_return",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "anti_void_return")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ANTI_VOID_LEAVE = SOUNDS.register("anti_void_leave",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "anti_void_leave")));
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
    /**
     * r230 晶化行尸的四套音效：低哑呻吟的僵尸 + 紫晶碎裂。sounds.json 里由
     * 原版僵尸叫声（降调）与魔晶 / 玻璃碎裂音叠加，音色比原版僵尸更破败恐怖。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTALLIZED_ZOMBIE_AMBIENT =
            crystalZombieSound("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTALLIZED_ZOMBIE_HURT =
            crystalZombieSound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTALLIZED_ZOMBIE_DEATH =
            crystalZombieSound("death");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTALLIZED_ZOMBIE_SHATTER =
            crystalZombieSound("shatter");
    /** r238：狂暴啃咬每一口的晶石牙啃咬声（紫晶碎屑 + 咬合）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTALLIZED_ZOMBIE_BITE =
            crystalZombieSound("bite");

    private static DeferredHolder<SoundEvent, SoundEvent> crystalZombieSound(String id) {
        return SOUNDS.register("crystallized_zombie_" + id,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "crystallized_zombie_" + id)));
    }
    /**
     * 聚魔爆裂体的五套音效：闷在晶体壳里的低吼、受伤、死亡，加上蓄力的「更响的 primed」
     * 与魔核炸开的爆响。sounds.json 里全部由原版音效叠层（压低音高 + 龙吟 / 监守者心跳 /
     * 紫晶碎裂），不需要额外的音频文件。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> MANA_CREEPER_AMBIENT =
            manaCreeperSound("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANA_CREEPER_HURT =
            manaCreeperSound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANA_CREEPER_DEATH =
            manaCreeperSound("death");
    /** 蓄力音：比原版 creeper.primed 更响、更低的充能嘶鸣。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> MANA_CREEPER_PRIMED =
            manaCreeperSound("primed");
    /** 魔核爆裂 / 紊乱爆炸的爆响。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> MANA_CREEPER_BURST =
            manaCreeperSound("burst");

    private static DeferredHolder<SoundEvent, SoundEvent> manaCreeperSound(String id) {
        return SOUNDS.register("mana_creeper_" + id,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "mana_creeper_" + id)));
    }
    /**
     * 魔蚀骸骨的五套音效：骨头里漏出魂火的低哑呻吟、受伤、死亡，蓄力时越来越尖的魂火啸叫，
     * 以及巨型箭离弦的爆响。sounds.json 里由原版音效叠层（乞丐骷髅 / 监守者 / 凋灵骷髅 /
     * 灵魂沙谷的环境音），不需要额外的音频文件。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SKELETON_AMBIENT =
            corruptedSkeletonSound("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SKELETON_HURT =
            corruptedSkeletonSound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SKELETON_DEATH =
            corruptedSkeletonSound("death");
    /** 4 秒蓄力时的魂火啸叫。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SKELETON_CHARGE =
            corruptedSkeletonSound("charge");
    /** 巨型魂火箭矢离弦。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SKELETON_CAST =
            corruptedSkeletonSound("cast");
    /** 魂火箭矢离弦（普通射击与死亡弹幕共用）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUL_FIRE_ARROW_SHOOT = SOUNDS.register(
            "soul_fire_arrow_shoot", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "soul_fire_arrow_shoot")));
    /** 魂火箭矢命中 / 消散。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUL_FIRE_ARROW_HIT = SOUNDS.register(
            "soul_fire_arrow_hit", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "soul_fire_arrow_hit")));

    private static DeferredHolder<SoundEvent, SoundEvent> corruptedSkeletonSound(String id) {
        return SOUNDS.register("corrupted_skeleton_" + id,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "corrupted_skeleton_" + id)));
    }
    /**
     * 魔蚀织网者的六套音效：甲壳摩擦的低频嘶鸣、受伤、死亡，吐丝、毒液喷射和毒牙咬合。
     * 和魔蚀骸骨一样全部由原版音效叠层（蜘蛛降调 + 紫晶碎裂 + 监守者 / 凋灵调门），
     * 不需要额外音频文件。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_WEAVER_AMBIENT =
            corruptedWeaverSound("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_WEAVER_HURT =
            corruptedWeaverSound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_WEAVER_DEATH =
            corruptedWeaverSound("death");
    /** 蛛丝球离体。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_WEAVER_WEB =
            corruptedWeaverSound("web");
    /** 毒液射线喷射。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_WEAVER_VENOM =
            corruptedWeaverSound("venom");
    /** 毒牙噬咬。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_WEAVER_BITE =
            corruptedWeaverSound("bite");
    /** 虫群意识：一片极速的节肢摩擦声。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_WEAVER_HIVE =
            corruptedWeaverSound("hive");

    private static DeferredHolder<SoundEvent, SoundEvent> corruptedWeaverSound(String id) {
        return SOUNDS.register("corrupted_weaver_" + id,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "corrupted_weaver_" + id)));
    }
    /**
     * 秽蚀蠹的六套音效：粘液撕裂 + 甲壳摩擦（两段自制音频，见 sounds/corrupted_silverfish/），
     * 再叠上降调的原版蠹虫叫声。蜕皮、钻地各用一套，玩家分得清「它变小了」和「它从地里来了」。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SILVERFISH_AMBIENT =
            corruptedSilverfishSound("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SILVERFISH_HURT =
            corruptedSilverfishSound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SILVERFISH_DEATH =
            corruptedSilverfishSound("death");
    /** 死亡蜕皮：一层旧壳被撕下来的粘液声。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SILVERFISH_MOLT =
            corruptedSilverfishSound("molt");
    /** 咬合 / 啃食植物。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SILVERFISH_BITE =
            corruptedSilverfishSound("bite");
    /** 钻地、破土而出。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_SILVERFISH_BURROW =
            corruptedSilverfishSound("burrow");

    private static DeferredHolder<SoundEvent, SoundEvent> corruptedSilverfishSound(String id) {
        return SOUNDS.register("corrupted_silverfish_" + id,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "corrupted_silverfish_" + id)));
    }

    /**
     * 秽蚀蜂的三套音效：循环嗡鸣（链锯低频抖 + 黑板刮高频刺）、受击短啸、死亡坠鸣。
     * 见 sounds/corrupted_bee/ 下的自制 ogg；循环那套由 CorruptedBeeHumInstance 常驻播放。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_BEE_HUM = corruptedBeeSound("hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_BEE_HURT = corruptedBeeSound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORRUPTED_BEE_DEATH = corruptedBeeSound("death");

    private static DeferredHolder<SoundEvent, SoundEvent> corruptedBeeSound(String id) {
        return SOUNDS.register("corrupted_bee_" + id,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "corrupted_bee_" + id)));
    }
    /**
     * 虚空蚀影的五套音效：比原版末影人更低更黏的呼吸、受伤、死亡，蓄力时的虚空啸叫，
     * 以及魔能爆发射 / 眼睛成型的爆响。和前面几只魔素变异体一样，sounds.json 里全部由
     * 原版音效叠层（末影人 / 监守者 / 凋灵 / 紫晶共鸣，统一降调），不需要额外音频文件。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_SHADE_AMBIENT =
            voidShadeSound("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_SHADE_HURT =
            voidShadeSound("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_SHADE_DEATH =
            voidShadeSound("death");
    /** 蓄力：两段蓄力时那阵越拧越紧的啸叫。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_SHADE_CHARGE =
            voidShadeSound("charge");
    /** 魔能爆发射 / 魔蚀末影之眼成型的爆响。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_SHADE_CAST =
            voidShadeSound("cast");

    private static DeferredHolder<SoundEvent, SoundEvent> voidShadeSound(String id) {
        return SOUNDS.register("void_shade_" + id,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_shade_" + id)));
    }
    public static final DeferredHolder<MenuType<?>, MenuType<GrinderMenu>> GRINDER_MENU = MENUS.register("grinder",
            () -> new MenuType<>(GrinderMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<ManaFurnaceMenu>> MANA_FORGE_MENU = MENUS.register("mana_forge",
            () -> new MenuType<>((id, inventory) -> new ManaFurnaceMenu(id, inventory, ManaCookingRecipe.FurnaceKind.MANA_FORGE), FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<ManaFurnaceMenu>> PROFESSIONAL_FURNACE_MENU = MENUS.register("professional_crystal_furnace",
            () -> new MenuType<>((id, inventory) -> new ManaFurnaceMenu(id, inventory, ManaCookingRecipe.FurnaceKind.PROFESSIONAL), FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<CrystalProcessorMenu>> CRYSTAL_PROCESSOR_MENU = MENUS.register("crystal_processor",
            () -> new MenuType<>(CrystalProcessorMenu::new, FeatureFlags.DEFAULT_FLAGS));
    /** 武备加工台：两个格子 + 锻造按钮，强化与洗练都靠菜单的 clickMenuButton 结算。 */
    public static final DeferredHolder<MenuType<?>, MenuType<WeaponWorkbenchMenu>> WEAPON_WORKBENCH_MENU = MENUS.register(
            WeaponWorkbenchBlock.BLOCK_ID,
            () -> new MenuType<>(WeaponWorkbenchMenu::new, FeatureFlags.DEFAULT_FLAGS));
    /** 魔晶砂轮：两个输入格 + 一个成品格 + 一个「模式切换」按钮。 */
    public static final DeferredHolder<MenuType<?>, MenuType<ManaGrindstoneMenu>> MANA_GRINDSTONE_MENU = MENUS.register(
            ManaGrindstoneBlock.BLOCK_ID,
            () -> new MenuType<>(ManaGrindstoneMenu::new, FeatureFlags.DEFAULT_FLAGS));
    /** 冒险者交易界面：窗口 id + 实体 id，客户端靠实体 id 找回那只冒险者。 */
    public static final DeferredHolder<MenuType<?>, MenuType<AdventurerTradeMenu>> ADVENTURER_TRADE_MENU = MENUS.register(
            "adventurer_trade", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id, inventory, buffer) -> new AdventurerTradeMenu(id, inventory, buffer.readVarInt())));
    public static final DeferredHolder<RecipeType<?>, RecipeType<ManaCookingRecipe>> MANA_FORGE_RECIPE_TYPE = RECIPE_TYPES.register("mana_forge_smelting", RecipeType::simple);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ManaCookingRecipe>> PROFESSIONAL_RECIPE_TYPE = RECIPE_TYPES.register("professional_smelting", RecipeType::simple);
    /** 加料食物（卢梅莉亚粉末 + 任意食物）：产物要按食物现场生成，只能走特制配方。 */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PlantFood>> PLANT_FOOD_SERIALIZER =
            RECIPE_SERIALIZERS.register("plant_food", PlantFood.Serializer::new);
    /** 合成方格修装备：两件同类装备拼耐久，惩罚清零并丢掉附魔与词条。 */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<EquipmentRecombineRecipe>> EQUIPMENT_RECOMBINE_SERIALIZER =
            RECIPE_SERIALIZERS.register("equipment_recombine",
                    () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(EquipmentRecombineRecipe::new));
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
    /** r271：蚀魔荒原地表改造——把表层土 / 石染成秽蚀土、秽蚀草块与蚀脉石。 */
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> EROSION_SURFACE = FEATURES.register(
            "erosion_surface", ErosionSurfaceFeature::new);
    /**
     * r277：蚀纹木的树特征——先查地面和周围有没有别的树，再交给原版树特征摆放，
     * 顺便把树高抬到 7～12 格。原来的 JSON 树配置还留在
     * {@code data/dimensional_mana/worldgen/configured_feature/erosion_tree.json}，只有 type 变了。
     */
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> EROSION_TREE_FEATURE = FEATURES.register(
            "erosion_tree", ErosionTreeFeature::new);
    /** r91：树装饰器注册表——蚀纹木挂秽蚀蜂巢用的（原版橡树的 BeehiveDecorator 同款机制）。 */
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType<?>> TREE_DECORATORS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.TREE_DECORATOR_TYPE, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType<?>,
            net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType<CorruptedNestTreeDecorator>>
            CORRUPTED_NEST_TREE_DECORATOR = TREE_DECORATORS.register("corrupted_nest",
            () -> new net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType<>(
                    CorruptedNestTreeDecorator.CODEC));
    /** r271：粒子注册表（荒原落叶 erosion_leaf）。 */
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.PARTICLE_TYPE, MOD_ID);
    /**
     * r291：膜面水波粒子（改在这里注册，修 r290 启动崩溃）。
     *
     * <p>原先这个登记写在 {@link AntiVoidSystem} 的静态字段里，而那个类要等游戏事件
     * （或客户端粒子 provider 注册）才被 JVM 加载——那时 NeoForge 的 RegisterEvent
     * 早已触发、注册窗口关闭，于是抛出 "Cannot register new entries to
     * DeferredRegister after RegisterEvent has been fired." 直接崩溃。
     * 所有 DeferredRegister 登记必须发生在模组主类加载期间，这里就是那个时机。</p>
     *
     * <p>粒子本身：贴图是一张 8 帧的竖向动画条
     * （assets/dimensional_mana/particles/membrane_ripple.json +
     * textures/particle/membrane_ripple.png.mcmeta），由 {@link MembraneRippleParticle}
     * 一边播放帧动画、一边放大淡出，形成扩散的同心圆涟漪。</p>
     */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,
            net.minecraft.core.particles.SimpleParticleType> MEMBRANE_RIPPLE =
            PARTICLE_TYPES.register("membrane_ripple",
                    () -> new net.minecraft.core.particles.SimpleParticleType(true));
    public static final DeferredHolder<EntityType<?>, EntityType<WanderingWitch>> WANDERING_WITCH = ENTITY_TYPES.register(
            "wandering_witch", () -> EntityType.Builder.of(WanderingWitch::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build(MOD_ID + ":wandering_witch"));
    public static final DeferredHolder<EntityType<?>, EntityType<Adventurer>> ADVENTURER = ENTITY_TYPES.register(
            "adventurer", () -> EntityType.Builder.of(Adventurer::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build(MOD_ID + ":adventurer"));
    public static final DeferredHolder<EntityType<?>, EntityType<ManaOrb>> MANA_ORB = ENTITY_TYPES.register(
            "mana_orb", () -> EntityType.Builder.<ManaOrb>of(ManaOrb::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).build(MOD_ID + ":mana_orb"));
    /** r230：晶化行尸——高魔素区块里普通僵尸的 20% 替换者。 */
    public static final DeferredHolder<EntityType<?>, EntityType<CrystallizedZombie>> CRYSTALLIZED_ZOMBIE = ENTITY_TYPES.register(
            "crystallized_zombie", () -> EntityType.Builder.<CrystallizedZombie>of(CrystallizedZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build(MOD_ID + ":crystallized_zombie"));
    /** r230：晶爆尸潮抛射的畸变魔晶片弹体。 */
    public static final DeferredHolder<EntityType<?>, EntityType<DistortedCrystalShardEntity>> DISTORTED_CRYSTAL_SHARD = ENTITY_TYPES.register(
            "distorted_crystal_shard", () -> EntityType.Builder.<DistortedCrystalShardEntity>of(DistortedCrystalShardEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(1).build(MOD_ID + ":distorted_crystal_shard"));
    /** 聚魔爆裂体：魔晶化的苦力怕，尺寸沿用原版苦力怕。 */
    public static final DeferredHolder<EntityType<?>, EntityType<ManaCreeper>> MANA_CREEPER = ENTITY_TYPES.register(
            "mana_creeper", () -> EntityType.Builder.<ManaCreeper>of(ManaCreeper::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F).clientTrackingRange(10).build(MOD_ID + ":mana_creeper"));
    /** 魔蚀骸骨：魔晶化的骷髅，尺寸沿用原版骷髅。 */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedSkeleton>> CORRUPTED_SKELETON = ENTITY_TYPES.register(
            "corrupted_skeleton", () -> EntityType.Builder.<CorruptedSkeleton>of(CorruptedSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F).clientTrackingRange(10).build(MOD_ID + ":corrupted_skeleton"));
    /** 魂火箭矢：魔蚀骸骨的普通远程攻击，同时用于死亡弹幕与巨型箭裂开后的小箭。 */
    public static final DeferredHolder<EntityType<?>, EntityType<SoulFireArrowEntity>> SOUL_FIRE_ARROW = ENTITY_TYPES.register(
            "soul_fire_arrow", () -> EntityType.Builder.<SoulFireArrowEntity>of(SoulFireArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(2).build(MOD_ID + ":soul_fire_arrow"));
    /** 巨型魂火箭矢：4 秒蓄力大招射出的那一发，飞满 8 格会裂成 4 根小箭。 */
    public static final DeferredHolder<EntityType<?>, EntityType<GiantSoulFireArrowEntity>> GIANT_SOUL_FIRE_ARROW = ENTITY_TYPES.register(
            "giant_soul_fire_arrow", () -> EntityType.Builder.<GiantSoulFireArrowEntity>of(GiantSoulFireArrowEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F).clientTrackingRange(10).updateInterval(1).build(MOD_ID + ":giant_soul_fire_arrow"));
    /** 流浪剑士：村庄常驻的中立剑术大师，动作动画走 GeckoLib。 */
    public static final DeferredHolder<EntityType<?>, EntityType<WanderingSwordmaster>> WANDERING_SWORDMASTER = ENTITY_TYPES.register(
            "wandering_swordmaster", () -> EntityType.Builder.<WanderingSwordmaster>of(WanderingSwordmaster::new, MobCategory.CREATURE)
                    .sized(0.65F, 1.9F).clientTrackingRange(10).build(MOD_ID + ":wandering_swordmaster"));
    /**
     * 魔蚀织网者：魔晶化的蜘蛛。
     *
     * <p>r285：碰撞箱从原版蜘蛛的 1.4 缩到 1.2（高度不变）。1.4 宽的身体在矿簇那种
     * 一格一格拼出来的地形里很容易一半卡进矿柱或粗制魔晶块，一落地就窒息掉血；
     * 缩窄之后 1 格宽的缝也挤得过去，模型仍是原版蜘蛛骨架，1.2 的箱子看着并不瘦。</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedWeaverEntity>> CORRUPTED_WEAVER = ENTITY_TYPES.register(
            "corrupted_weaver", () -> EntityType.Builder.<CorruptedWeaverEntity>of(CorruptedWeaverEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 0.9F).clientTrackingRange(10).build(MOD_ID + ":corrupted_weaver"));
    /** 蛛丝球：抛物线的黏丝团，落点铺网。 */
    public static final DeferredHolder<EntityType<?>, EntityType<WebProjectileEntity>> WEB_PROJECTILE = ENTITY_TYPES.register(
            "web_projectile", () -> EntityType.Builder.<WebProjectileEntity>of(WebProjectileEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(1).build(MOD_ID + ":web_projectile"));
    /**
     * 元素史莱姆的「魔法子弹」：看得见、飞得慢（玩家投射速度的一半）、能被打掉的弹体。
     *
     * <p>r400 之前史莱姆的初级法术是瞬间命中，玩家只看到目标身上闪一下，既分不清是哪只史莱姆
     * 在打人，也没有反应时间。现在改成这颗真正的弹体：带对应元素颜色的拖尾，命中才结算，
     * 举招架姿态左键可以把它打掉。</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SlimeSpellBolt>> SLIME_SPELL_BOLT = ENTITY_TYPES.register(
            "slime_spell_bolt", () -> EntityType.Builder.<SlimeSpellBolt>of(SlimeSpellBolt::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(1).build(MOD_ID + ":slime_spell_bolt"));
    /**
     * 秽蚀蠹：四阶蜕皮的蠹虫，生成在末地传送门遗迹 / 高魔素魔晶矿 / 蚀魔荒原地表。
     *
     * <p>它被登记成动物类（Creature），所以走的是原版「生物」那套刷怪上限与白天生成，
     * 不跟满地的怪物抢名额。碰撞箱沿用原版蠹虫的 0.4×0.3；每蜕一层皮只是渲染缩小，
     * 判定框不动，免得打着打着突然打不着。</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedSilverfishEntity>> CORRUPTED_SILVERFISH =
            ENTITY_TYPES.register("corrupted_silverfish", () -> EntityType.Builder
                    .<CorruptedSilverfishEntity>of(CorruptedSilverfishEntity::new, MobCategory.CREATURE)
                    .sized(0.4F, 0.3F).clientTrackingRange(10).build(MOD_ID + ":corrupted_silverfish"));
    /** 秽蚀蜂：住秽蚀蜂巢的变异蜜蜂，尺寸沿用原版蜜蜂，20 点生命在属性里加。 */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedBeeEntity>> CORRUPTED_BEE =
            ENTITY_TYPES.register("corrupted_bee", () -> EntityType.Builder
                    .<CorruptedBeeEntity>of(CorruptedBeeEntity::new, MobCategory.CREATURE)
                    .sized(0.7F, 0.6F).clientTrackingRange(10).build(MOD_ID + ":corrupted_bee"));
    /** 虚空蚀影：高魔素区块里顶替原版末影人的精英变异体，尺寸沿用原版末影人。 */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidShadeEntity>> VOID_SHADE = ENTITY_TYPES.register(
            "void_shade", () -> EntityType.Builder.<VoidShadeEntity>of(VoidShadeEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.9F).clientTrackingRange(10).build(MOD_ID + ":void_shade"));
    /** 虚无幻影：虚空蚀影挨打时撕下来的分身，只近战、会自爆回血。 */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidPhantomEntity>> VOID_PHANTOM = ENTITY_TYPES.register(
            "void_phantom", () -> EntityType.Builder.<VoidPhantomEntity>of(VoidPhantomEntity::new, MobCategory.MONSTER)
                    .sized(0.4F, 1.9F).clientTrackingRange(10).build(MOD_ID + ":void_phantom"));
    /** 魔蚀末影之眼：绕本体旋转的浮游炮，无碰撞、不寻路。 */
    public static final DeferredHolder<EntityType<?>, EntityType<MagicEyeEntity>> MAGIC_EYE = ENTITY_TYPES.register(
            "magic_eye", () -> EntityType.Builder.<MagicEyeEntity>of(MagicEyeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1)
                    .build(MOD_ID + ":magic_eye"));
    /** r301 魔蚀凋零：继承原版凋零的 Boss，1000 血 / 30 万魔素 / 5000 斗气。 */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedWither>> CORRUPTED_WITHER = ENTITY_TYPES.register(
            "corrupted_wither", () -> EntityType.Builder.<CorruptedWither>of(CorruptedWither::new, MobCategory.MONSTER)
                    .sized(0.9F, 3.5F).clientTrackingRange(16).updateInterval(2).fireImmune()
                    .build(MOD_ID + ":corrupted_wither"));
    /** 魔蚀凋零的【侵蚀凋零】骷髅头弹。 */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedWitherSkull>> CORRUPTED_WITHER_SKULL =
            ENTITY_TYPES.register("corrupted_wither_skull", () -> EntityType.Builder
                    .<CorruptedWitherSkull>of(CorruptedWitherSkull::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F).clientTrackingRange(8).updateInterval(1)
                    .build(MOD_ID + ":corrupted_wither_skull"));
    /** 虚无聚合体：蓝 / 红两种晶体头颅。 */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedWitherAggregate>> CORRUPTED_WITHER_AGGREGATE =
            ENTITY_TYPES.register("corrupted_wither_aggregate", () -> EntityType.Builder
                    .<CorruptedWitherAggregate>of(CorruptedWitherAggregate::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F).clientTrackingRange(10).updateInterval(1)
                    .build(MOD_ID + ":corrupted_wither_aggregate"));
    /** 虚无凋零射线。 */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedWitherBeam>> CORRUPTED_WITHER_BEAM =
            ENTITY_TYPES.register("corrupted_wither_beam", () -> EntityType.Builder
                    .<CorruptedWitherBeam>of(CorruptedWitherBeam::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(16).updateInterval(1).noSummon()
                    .build(MOD_ID + ":corrupted_wither_beam"));
    /** 凋零幻影：二阶段受击分裂出来的假身。 */
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedWitherPhantom>> CORRUPTED_WITHER_PHANTOM =
            ENTITY_TYPES.register("corrupted_wither_phantom", () -> EntityType.Builder
                    .<CorruptedWitherPhantom>of(CorruptedWitherPhantom::new, MobCategory.MONSTER)
                    .sized(0.9F, 3.5F).clientTrackingRange(16).updateInterval(2)
                    .build(MOD_ID + ":corrupted_wither_phantom"));
    /** 魔蚀凋零骷髅头（方块）：召唤阵的三颗头。 */
    public static final DeferredBlock<Block> CORRUPTED_WITHER_SKULL_BLOCK = BLOCKS.register(
            CorruptedWitherSkullBlock.BLOCK_ID, () -> new CorruptedWitherSkullBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(1.0F, 3.0F)
                    .sound(SoundType.BONE_BLOCK).noOcclusion().dynamicShape()));
    public static final DeferredHolder<StructureType<?>, StructureType<AncientMagicAltarStructure>> ANCIENT_MAGIC_ALTAR_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("ancient_magic_altar", () -> () -> AncientMagicAltarStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> ANCIENT_MAGIC_ALTAR_PIECE =
            STRUCTURE_PIECES.register("ancient_magic_altar", () -> (StructurePieceType.ContextlessType)AncientMagicAltarPiece::new);

    public static final DeferredHolder<StructureType<?>, StructureType<GoblinCampStructure>> GOBLIN_CAMP_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("goblin_camp", () -> () -> GoblinCampStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> GOBLIN_CAMP_PIECE =
            STRUCTURE_PIECES.register("goblin_camp", () -> (StructurePieceType.ContextlessType)GoblinCampPiece::new);

    /** r282：露天魔晶矿簇（四种形态、浓度 ≥14 才生成，/locate 可以直接搜到）。 */
    public static final DeferredHolder<StructureType<?>, StructureType<OpenAirManaCrystalClusterStructure>>
            OPEN_AIR_MANA_CRYSTAL_CLUSTER_STRUCTURE_TYPE = STRUCTURE_TYPES.register(
            "open_air_mana_crystal_cluster", () -> () -> OpenAirManaCrystalClusterStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> OPEN_AIR_MANA_CRYSTAL_CLUSTER_PIECE =
            STRUCTURE_PIECES.register("open_air_mana_crystal_cluster",
                    () -> (StructurePieceType.ContextlessType)OpenAirManaCrystalClusterPiece::new);

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
    /**
     * 史莱姆驱逐火把：原版火把同款 14 级光照，把 5×5 区块划成史莱姆禁刷区，16 格内把已有的史莱姆熏跑。
     * 物品在静态块里注册（要用自定义 BlockItem 写说明栏），这里先摆出方块本身给数据类和渲染层引用。
     */
    public static final DeferredBlock<Block> SLIME_REPELLENT_TORCH = BLOCKS.register(SlimeRepellentTorchBlock.BLOCK_ID,
            () -> new SlimeRepellentTorchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN).noCollission().instabreak()
                    .lightLevel(state -> 14).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY)));

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
        // r282：魔晶块分两套，和原版「粗铁块 / 铁块」一一对应——
        //   raw_*_mana_crystal_block：9 颗原矿压成，**自然生成在露天魔晶矿簇里的就是它**；
        //   *_mana_crystal_block：9 颗炼好的成品魔晶压成，只能由玩家合成。
        rawCrystalBlock("raw_lesser_mana_crystal_block", 3.5F, 3, Rarity.COMMON);
        rawCrystalBlock("raw_medium_mana_crystal_block", 4.0F, 4, Rarity.UNCOMMON);
        rawCrystalBlock("raw_advanced_mana_crystal_block", 4.5F, 5, Rarity.RARE);
        rawCrystalBlock("raw_supreme_mana_crystal_block", 5.0F, 6, Rarity.EPIC);
        crystalBlock("lesser_mana_crystal_block", 4.0F, 5, Rarity.COMMON);
        crystalBlock("medium_mana_crystal_block", 4.5F, 7, Rarity.UNCOMMON);
        crystalBlock("advanced_mana_crystal_block", 5.0F, 9, Rarity.RARE);
        crystalBlock("supreme_mana_crystal_block", 5.5F, 11, Rarity.EPIC);
        crystalBlock("special_mana_crystal_block", 6.0F, 13, Rarity.EPIC);
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
        // 魔晶铁砧沿用原版铁砧界面，永不损坏；词条合成由武备事件接管。
        // 归到「次元物语：魔素」页，紧跟在上面几台工作方块之后（注册顺序就是创造页顺序）。
        device("magic_crystal_anvil", () -> new MagicCrystalAnvilBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F)
                        .requiresCorrectToolForDrops().noOcclusion()), Rarity.EPIC);
        // 武备加工台：强化装备、敲击升级、词条洗练，排在魔晶铁砧后面。
        device("weapon_workbench", () -> new WeaponWorkbenchBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 600.0F)
                        .requiresCorrectToolForDrops().sound(SoundType.METAL)
                        .lightLevel(state -> 6)), Rarity.EPIC);
        // 蚀纹木木棍：魔晶加工台里由 2 块蚀纹木木板竖着压出来的基础材料，
        // 也是魔晶砂轮的三根木构件之一。它归「自然」页，跟蚀纹木这一家子摆一起
        // （见 ErosionWasteland.NATURAL_IDS），主创造页会自动跳过它。
        simple("erosion_stick", Rarity.COMMON);
        // 史莱姆驱逐火把：自动生成在「次元物语：自然」页，跟植物材料摆在一起。
        MOD_BLOCKS.put(SlimeRepellentTorchBlock.BLOCK_ID, SLIME_REPELLENT_TORCH);
        MOD_ITEMS.put(SlimeRepellentTorchBlock.BLOCK_ID, ITEMS.register(SlimeRepellentTorchBlock.BLOCK_ID,
                () -> new SlimeRepellentTorchItem(SLIME_REPELLENT_TORCH.get(),
                        new Item.Properties().rarity(Rarity.UNCOMMON))));
        NATURAL_ITEM_IDS.add(SlimeRepellentTorchBlock.BLOCK_ID);
        // 魔晶砂轮：原版砂轮的全部功能 + 可自由切换的两种剥离方向（洗附魔 / 洗词条）。
        // noOcclusion 不能省：模型只占一格的一部分，少了它会把四周方块贴着的面全部剔掉（看起来像透明）。
        device(ManaGrindstoneBlock.BLOCK_ID, () -> new ManaGrindstoneBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.5F, 600.0F)
                        .requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion()
                        .lightLevel(state -> 5)), Rarity.RARE);
        // 武备强化石：武备加工台的第二种材料，满级武备用它把副词条一路顶到满。
        MOD_ITEMS.put(ArmamentStoneLoot.ITEM_ID, ITEMS.register(ArmamentStoneLoot.ITEM_ID,
                () -> new ArmamentStoneItem(new Item.Properties().rarity(Rarity.EPIC)
                        .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))));
        // 武备强化石粉：强化石在下界合金研磨器里磨 10 秒的产物（2～4 份），词条重铸符的核心材料。
        simple(ArmamentForge.ENHANCE_STONE_DUST, Rarity.EPIC);
        // 词条重铸符：武备加工台的新材料，品级不变、只重抽主副词条。
        MOD_ITEMS.put(ArmamentForge.REFORGE_TALISMAN, ITEMS.register(ArmamentForge.REFORGE_TALISMAN,
                () -> new AffixReforgeTalismanItem(new Item.Properties().rarity(Rarity.EPIC)
                        .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))));

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
        // 粗制法杖系列：耐久统一 100，施法磨损按法术品级 1/4/16 结算（SpellDefinition#staffWearCost）。
        // 这批是入门货，后续版本会另开一档正式的魔杖工艺。
        magicStaff("lesser_general_staff", 100, Rarity.COMMON, 2, 8, 0.05, 0.04, 0.05);
        magicStaff("medium_general_staff", 100, Rarity.UNCOMMON, 6, 16, 0.10, 0.08, 0.10);
        magicStaff("advanced_general_staff", 100, Rarity.RARE, 12, 32, 0.15, 0.12, 0.15);
        magicStaff("supreme_general_staff", 100, Rarity.EPIC, 24, 64, 0.20, 0.16, 0.20);
        magicStaff("special_general_staff", 100, Rarity.EPIC, 48, 128, 0.25, 0.20, 0.25);
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
        // 升华药水：酿造台里用「附魔金苹果 + 3 瓶完全回复药剂」一次酿三瓶，进「次元物语：魔法」页。
        magicItem(AscensionPotionItem.ITEM_ID, ITEMS.register(AscensionPotionItem.ITEM_ID, () ->
                new AscensionPotionItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC))));
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
        // 高阶斗气铭蚀属于「次元物语：斗气」；从魔法页移出后加入斗气页。
        MAGIC_ITEM_IDS.remove(RelicEffects.ADVANCED_BATTLE_QI_ETCHING);
        QI_ITEM_IDS.add(RelicEffects.ADVANCED_BATTLE_QI_ETCHING);
        // 元素精通护符：通用饰品 / 魔法饰品 / 护符三个栏位都能戴，佩戴 +50 元素精通。
        magicItem(ElementalMasteryCharmItem.ITEM_ID, ITEMS.register(ElementalMasteryCharmItem.ITEM_ID,
                () -> new ElementalMasteryCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
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
        // 两枚只从掉落表里来的核心材料：说明栏要写清「哪来的、能做什么」，
        // 所以用带说明栏的物品类，而不是 simple() 的白板物品。
        MOD_ITEMS.put("purification_core", ITEMS.register("purification_core", () ->
                new LoreMaterialItem(new Item.Properties().rarity(Rarity.RARE), "purification_core", 1)));
        MOD_ITEMS.put("radiant_core", ITEMS.register("radiant_core", () ->
                new LoreMaterialItem(new Item.Properties().rarity(Rarity.EPIC), "radiant_core", 1)));
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
        // 「次元物语：自然」的主角：五种魔法植物、植物粉末与植物药剂，排在最前面。
        ManaPlants.register();
        // r265：魔法页里的刷怪蛋全部搬到自然页，统一排在自然页的刷怪蛋区。
        registerMovedSpawnEggs();
        registerElementalSlimes();
        registerGoblins();
        // r317 魔精钢：四件护甲、五把工具与锻造模板，进原版战斗 / 工具 / 材料创造页。
        MojiangSteelEquipment.register();
        registerMagicCircle();
        registerCrystalZombie();
        registerManaCreeper();
        registerCorruptedSkeleton();
        registerCorruptedWeaver();
        registerCorruptedSilverfish();
        registerCorruptedBee();
        registerSwordmaster();
        registerVoidShade();
        registerCorruptedWither();
        // r271：蚀魔荒原——方块 / 物品 / 粒子一次挂齐。
        ErosionWasteland.registerItems();
        // 水球最后注册：自然页里排在所有刷怪蛋、装备和掉落物之后。
        registerWaterBall();
    }

    /**
     * 把刷怪蛋放进「次元物语：自然」，统一排在自然页最后一枚刷怪蛋之后。
     *
     * <p>自然页的既定顺序是 魔法植物 → 刷怪蛋 → 粗糙装备 → 掉落物，
     * 所以插的时候只认 {@code _spawn_egg} 结尾的条目，不会插到植物或装备中间去。</p>
     */
    private static void naturalEgg(String name, DeferredItem<? extends Item> item) {
        MOD_ITEMS.put(name, item);
        int lastEgg = -1;
        for (int i = 0; i < NATURAL_ITEM_IDS.size(); i++) {
            if (NATURAL_ITEM_IDS.get(i).endsWith("_spawn_egg")) lastEgg = i;
        }
        if (lastEgg >= 0) NATURAL_ITEM_IDS.add(lastEgg + 1, name);
        else NATURAL_ITEM_IDS.add(name);
    }

    /**
     * r265：把原本摆在「次元物语：魔法」里的刷怪蛋挪到「次元物语：自然」。
     *
     * <p>以前刷怪蛋和法术卷轴、法杖挤在魔法页，找一只生物要翻半天；现在模组里所有刷怪蛋
     * 都集中在自然页的同一段位置上。物品 id 不变，旧存档里的蛋、已经生成的生物都不受影响。</p>
     */
    private static void registerMovedSpawnEggs() {
        naturalEgg("wandering_witch_spawn_egg", ITEMS.register("wandering_witch_spawn_egg", () ->
                new DeferredSpawnEggItem(WANDERING_WITCH, 0x24172F, 0x574066,
                        new Item.Properties().rarity(Rarity.RARE))));
        naturalEgg("adventurer_spawn_egg", ITEMS.register("adventurer_spawn_egg", () ->
                new DeferredSpawnEggItem(ADVENTURER, 0x3D536C, 0xC59A55,
                        new Item.Properties().rarity(Rarity.RARE))));
    }

    /**
     * r265 虚空蚀影：刷怪蛋放进「次元物语：自然」，排在自然页最后一枚刷怪蛋之后。
     *
     * <p>它的掉落物（畸变魔晶片、末影之眼、奥术结晶）都是已有物品，所以这里只补一枚蛋。</p>
     */
    private static void registerVoidShade() {
        naturalEgg("void_shade_spawn_egg", ITEMS.register("void_shade_spawn_egg", () ->
                new DeferredSpawnEggItem(VOID_SHADE, 0x0D0818, 0x7B3BD6,
                        new Item.Properties().rarity(Rarity.EPIC))));
    }

    /**
     * r301 魔蚀凋零的整条内容线：
     * 魔蚀凋零骷髅头（方块 + 物品）、畸变下界之星、刷怪蛋。
     *
     * <p>全部放进「次元物语：自然」创造页：召唤材料和掉落物排在自然页末尾，
     * 刷怪蛋走和别的魔素变异体一样的「插到最后一枚蛋后面」的规则。</p>
     */
    private static void registerCorruptedWither() {
        MOD_BLOCKS.put(CorruptedWitherSkullBlock.BLOCK_ID, CORRUPTED_WITHER_SKULL_BLOCK);
        MOD_ITEMS.put(CorruptedWitherSkullBlock.BLOCK_ID, ITEMS.register(CorruptedWitherSkullBlock.BLOCK_ID, () ->
                new BlockItem(CORRUPTED_WITHER_SKULL_BLOCK.get(), new Item.Properties().rarity(Rarity.EPIC))));
        MOD_ITEMS.put(DISTORTED_NETHER_STAR_ID, ITEMS.register(DISTORTED_NETHER_STAR_ID, () ->
                new LoreItem(new Item.Properties().rarity(Rarity.EPIC),
                        "tooltip.dimensional_mana.distorted_nether_star")));
        naturalEgg("corrupted_wither_spawn_egg", ITEMS.register("corrupted_wither_spawn_egg", () ->
                new DeferredSpawnEggItem(CORRUPTED_WITHER, 0x1A0B22, 0x9B59FF,
                        new Item.Properties().rarity(Rarity.EPIC))));
        NATURAL_ITEM_IDS.add(CorruptedWitherSkullBlock.BLOCK_ID);
        NATURAL_ITEM_IDS.add(DISTORTED_NETHER_STAR_ID);
    }

    /**
     * r230 晶化行尸一行：刷怪蛋放进「次元物语：自然」（排在哥布林刷怪蛋之后），
     * 畸变魔晶片是它的死亡掉落，走自然页的「掉落物」位置。
     */
    private static void registerCrystalZombie() {
        MOD_ITEMS.put(DistortedCrystalShardEntity.ITEM_ID, ITEMS.register(DistortedCrystalShardEntity.ITEM_ID,
                () -> new Item(new Item.Properties().rarity(Rarity.RARE))));
        DeferredItem<Item> egg = ITEMS.register("crystallized_zombie_spawn_egg", () ->
                new DeferredSpawnEggItem(CRYSTALLIZED_ZOMBIE, 0x2A1438, 0x8B4FD8,
                        new Item.Properties().rarity(Rarity.RARE)));
        MOD_ITEMS.put("crystallized_zombie_spawn_egg", egg);
        // 自然页的既定顺序是 刷怪蛋 → 粗糙装备 → 掉落物：把蛋插到最后一枚刷怪蛋的后面。
        int lastEgg = -1;
        for (int i = 0; i < NATURAL_ITEM_IDS.size(); i++) {
            if (NATURAL_ITEM_IDS.get(i).endsWith("_spawn_egg")) lastEgg = i;
        }
        if (lastEgg >= 0) NATURAL_ITEM_IDS.add(lastEgg + 1, "crystallized_zombie_spawn_egg");
        else NATURAL_ITEM_IDS.add("crystallized_zombie_spawn_egg");
        NATURAL_ITEM_IDS.add(DistortedCrystalShardEntity.ITEM_ID);
    }

    /**
     * 聚魔爆裂体：刷怪蛋同样放进「次元物语：自然」，排在自然页最后一枚刷怪蛋之后。
     *
     * <p>它没有新掉落物物品（掉的是畸变魔晶片与元素结晶粉末，两样都已存在），
     * 所以这里只补一枚蛋。</p>
     */
    private static void registerManaCreeper() {
        DeferredItem<Item> egg = ITEMS.register("mana_creeper_spawn_egg", () ->
                new DeferredSpawnEggItem(MANA_CREEPER, 0x2B0E45, 0xA86BFF,
                        new Item.Properties().rarity(Rarity.EPIC)));
        MOD_ITEMS.put("mana_creeper_spawn_egg", egg);
        int lastEgg = -1;
        for (int i = 0; i < NATURAL_ITEM_IDS.size(); i++) {
            if (NATURAL_ITEM_IDS.get(i).endsWith("_spawn_egg")) lastEgg = i;
        }
        if (lastEgg >= 0) NATURAL_ITEM_IDS.add(lastEgg + 1, "mana_creeper_spawn_egg");
        else NATURAL_ITEM_IDS.add("mana_creeper_spawn_egg");
    }

    /**
     * 魔蚀骸骨：刷怪蛋放进「次元物语：自然」（排在自然页最后一枚刷怪蛋之后），
     * 它的死亡掉落「魂火残渣」是全新的物品，和畸变魔晶片一起摆在自然页的掉落物位置。
     */
    private static void registerCorruptedSkeleton() {
        MOD_ITEMS.put(SOUL_FIRE_RESIDUE_ID, ITEMS.register(SOUL_FIRE_RESIDUE_ID,
                () -> new Item(new Item.Properties().rarity(Rarity.RARE))));
        DeferredItem<Item> egg = ITEMS.register("corrupted_skeleton_spawn_egg", () ->
                new DeferredSpawnEggItem(CORRUPTED_SKELETON, 0x1B1330, 0x7C5CFF,
                        new Item.Properties().rarity(Rarity.EPIC)));
        MOD_ITEMS.put("corrupted_skeleton_spawn_egg", egg);
        int lastEgg = -1;
        for (int i = 0; i < NATURAL_ITEM_IDS.size(); i++) {
            if (NATURAL_ITEM_IDS.get(i).endsWith("_spawn_egg")) lastEgg = i;
        }
        if (lastEgg >= 0) NATURAL_ITEM_IDS.add(lastEgg + 1, "corrupted_skeleton_spawn_egg");
        else NATURAL_ITEM_IDS.add("corrupted_skeleton_spawn_egg");
        // 畸变魔晶片在 registerCrystalZombie 里已经进过自然页，这里只补魂火残渣。
        NATURAL_ITEM_IDS.add(SOUL_FIRE_RESIDUE_ID);
    }

    /**
     * 魔蚀织网者：刷怪蛋放进「次元物语：自然」（排在最后一枚刷怪蛋之后），
     * 另外把魔蚀蛛网、魔蚀蛛丝、魔蚀蜘蛛眼三样东西摆在自然页的掉落物位置。
     *
     * <p>蛛网的方块本体不进掉落表：它会被斗剑砍破 / 剪刀剪破，由
     * {@link CorruptedWeaverEvents#onLeftClickBlock} 手动掉 1 个魔蚀蛛丝。</p>
     */
    /**
     * r250 流浪剑士一线：专属长剑、剑士徽记与刷怪蛋，全部放进「次元物语：斗气」创造页。
     *
     * <p>长剑的数值写在 {@link SwordmasterWeaponItem}（攻击 12、4 格、0.8 倍攻速、耐久 III + 锋利 V）；
     * 徽记是它的死亡掉落；刷怪蛋用于让玩家自己摆一只到村庄里。</p>
     */
    private static void registerSwordmaster() {
        MOD_ITEMS.put(SwordmasterWeaponItem.ITEM_ID, ITEMS.register(SwordmasterWeaponItem.ITEM_ID,
                () -> new SwordmasterWeaponItem(SwordmasterWeaponItem.bladeProperties())));
        MOD_ITEMS.put(SwordmasterSigilItem.ITEM_ID, ITEMS.register(SwordmasterSigilItem.ITEM_ID,
                () -> new SwordmasterSigilItem(new Item.Properties().rarity(Rarity.RARE))));
        DeferredItem<Item> egg = ITEMS.register("wandering_swordmaster_spawn_egg", () ->
                new DeferredSpawnEggItem(WANDERING_SWORDMASTER, 0x2A3550, 0xD8B85A,
                        new Item.Properties().rarity(Rarity.RARE)));
        MOD_ITEMS.put("wandering_swordmaster_spawn_egg", egg);
        QI_ITEM_IDS.add(SwordmasterWeaponItem.ITEM_ID);
        QI_ITEM_IDS.add(SwordmasterSigilItem.ITEM_ID);
        QI_ITEM_IDS.add("wandering_swordmaster_spawn_egg");
    }

    private static void registerCorruptedWeaver() {
        MOD_ITEMS.put(CORRUPTED_SPIDER_EYE_ID, ITEMS.register(CORRUPTED_SPIDER_EYE_ID, () ->
                new Item(new Item.Properties().rarity(Rarity.RARE))));
        MOD_ITEMS.put(CORRUPTED_SILK_ID, ITEMS.register(CORRUPTED_SILK_ID, () ->
                new Item(new Item.Properties().rarity(Rarity.RARE))));
        // 蛛网方块：硬度给得很高，正常挖掘磨不动，只认剑砍 3 下 / 剪刀 1 下。
        DeferredBlock<Block> web = BLOCKS.register(CorruptedWebBlock.BLOCK_ID, () -> new CorruptedWebBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                        .strength(50.0F, 1200.0F).sound(SoundType.COBWEB)
                        .noCollission().noOcclusion().dynamicShape()
                        .pushReaction(PushReaction.DESTROY).noLootTable()));
        MOD_BLOCKS.put(CorruptedWebBlock.BLOCK_ID, web);
        MOD_ITEMS.put(CorruptedWebBlock.BLOCK_ID, ITEMS.register(CorruptedWebBlock.BLOCK_ID, () ->
                new BlockItem(web.get(), new Item.Properties().rarity(Rarity.UNCOMMON))));
        DeferredItem<Item> egg = ITEMS.register("corrupted_weaver_spawn_egg", () ->
                new DeferredSpawnEggItem(CORRUPTED_WEAVER, 0x1A0F26, 0x9A4BFF,
                        new Item.Properties().rarity(Rarity.EPIC)));
        MOD_ITEMS.put("corrupted_weaver_spawn_egg", egg);
        int lastEgg = -1;
        for (int i = 0; i < NATURAL_ITEM_IDS.size(); i++) {
            if (NATURAL_ITEM_IDS.get(i).endsWith("_spawn_egg")) lastEgg = i;
        }
        if (lastEgg >= 0) NATURAL_ITEM_IDS.add(lastEgg + 1, "corrupted_weaver_spawn_egg");
        else NATURAL_ITEM_IDS.add("corrupted_weaver_spawn_egg");
        NATURAL_ITEM_IDS.add(CorruptedWebBlock.BLOCK_ID);
        NATURAL_ITEM_IDS.add(CORRUPTED_SILK_ID);
        NATURAL_ITEM_IDS.add(CORRUPTED_SPIDER_EYE_ID);
    }

    /**
     * 秽蚀蠹：只要一枚刷怪蛋，放进「次元物语：自然」的刷怪蛋区（和织网者的蛋排在一起）。
     *
     * <p>掉落物（各级魔晶粉、魂火残渣）都是现成物品，由
     * {@link CorruptedSilverfishEntity} 在蜕皮 / 死亡时直接吐出来，不额外注册东西。</p>
     */
    private static void registerCorruptedSilverfish() {
        naturalEgg("corrupted_silverfish_spawn_egg", ITEMS.register("corrupted_silverfish_spawn_egg", () ->
                new DeferredSpawnEggItem(CORRUPTED_SILVERFISH, 0x241A38, 0x5FD7FF,
                        new Item.Properties().rarity(Rarity.RARE))));
    }

    /**
     * 秽蚀蜂系列（第 19 条需求）：蜂巢方块 / 物品 / POI / feature 在 CorruptedBeeContent 里声明，
     * 这里只挂刷怪蛋、把蜂巢并进自然创造页，并把蜂巢塞进原版 BEEHIVE 方块实体合法集合。
     */
    private static void registerCorruptedBee() {
        CorruptedBeeContent.register();
        naturalEgg("corrupted_bee_spawn_egg", ITEMS.register("corrupted_bee_spawn_egg", () ->
                new DeferredSpawnEggItem(CORRUPTED_BEE, 0x241A38, 0x8A4FBF,
                        new Item.Properties().rarity(Rarity.RARE))));
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

        // r289：反虚空领域。反虚空方块和两种门没有物品形态，膜与星穹裂隙框只能通过指令/稀有途径获得。
        DeferredBlock<Block> antiVoid = BLOCKS.register("anti_void_block", () -> new AntiVoidBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.NONE).lightLevel(state -> 0)
                        .strength(-1.0F, 3_600_000.0F).noLootTable().noOcclusion().sound(SoundType.GLASS)));
        MOD_BLOCKS.put("anti_void_block", antiVoid);

        DeferredBlock<Block> membrane = BLOCKS.register("membrane", () -> new MembraneBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).lightLevel(state -> 8)
                        .strength(-1.0F, 1_200.0F).noLootTable().noOcclusion().sound(SoundType.GLASS)));
        MOD_BLOCKS.put("membrane", membrane);
        MOD_ITEMS.put("membrane", ITEMS.register("membrane", () -> new BlockItem(membrane.get(),
                new Item.Properties().rarity(Rarity.EPIC))));

        // r290：星之间隙（原名“星穹裂隙框”）——门本身：16 帧流光动画贴图、发光 12 级、无碰撞直接穿行。
        DeferredBlock<Block> astralFrame = BLOCKS.register("astral_rift_frame", () -> new AstralRiftFrameBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).lightLevel(state -> 12)
                        .strength(20.0F, 720.0F)
                        .requiresCorrectToolForDrops().noOcclusion().sound(SoundType.METAL)));
        MOD_BLOCKS.put("astral_rift_frame", astralFrame);
        MOD_ITEMS.put("astral_rift_frame", ITEMS.register("astral_rift_frame", () -> new BlockItem(astralFrame.get(),
                new Item.Properties().rarity(Rarity.EPIC))));

        DeferredBlock<Block> crimsonGate = BLOCKS.register("rift_gate_crimson", () -> new RiftGateBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).lightLevel(state -> 6)
                        .strength(-1.0F, 3_600_000.0F).noLootTable().noOcclusion().sound(SoundType.AMETHYST),
                RiftGateBlock.Kind.CRIMSON));
        MOD_BLOCKS.put("rift_gate_crimson", crimsonGate);
        DeferredBlock<Block> azureGate = BLOCKS.register("rift_gate_azure", () -> new RiftGateBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).lightLevel(state -> 8)
                        .strength(-1.0F, 3_600_000.0F).noLootTable().noOcclusion().sound(SoundType.AMETHYST),
                RiftGateBlock.Kind.AZURE));
        MOD_BLOCKS.put("rift_gate_azure", azureGate);
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
    /** 武备加工台：只存两个格子，没有 tick。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WeaponWorkbenchBlockEntity>> WEAPON_WORKBENCH_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(WeaponWorkbenchBlock.BLOCK_ID, () -> BlockEntityType.Builder.of(
                    WeaponWorkbenchBlockEntity::new, MOD_BLOCKS.get(WeaponWorkbenchBlock.BLOCK_ID).get()).build(null));
    /** 魔晶砂轮：存三个格子和当前模式，每个 tick 重算一次成品。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManaGrindstoneBlockEntity>> MANA_GRINDSTONE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(ManaGrindstoneBlock.BLOCK_ID, () -> BlockEntityType.Builder.of(
                    ManaGrindstoneBlockEntity::new, MOD_BLOCKS.get(ManaGrindstoneBlock.BLOCK_ID).get()).build(null));
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
                        && !QI_ITEM_IDS.contains(id) && !ARCANE_ITEM_IDS.contains(id)
                        && !VANILLA_TAB_ITEM_IDS.contains(id)) output.accept(item.get());
            })).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAGIC_CREATIVE_TAB = CREATIVE_TABS.register("magic", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dimensional_mana.magic"))
            .icon(() -> new ItemStack(MOD_ITEMS.get("element_resonance_stone").get()))
            .displayItems((parameters, output) -> {
                orderedMagicItemIds().forEach(id -> {
                    DeferredItem<? extends Item> item = MOD_ITEMS.get(id);
                    if (item != null) output.accept(item.get());
                });
                // r268 起，酿造出来的药水物品统一摆这一页：普通 → 喷溅 → 滞留，按配方顺序排。
                for (net.minecraft.core.Holder<Potion> potion : brewedPotionLine()) {
                    output.accept(PotionContents.createItemStack(Items.POTION, potion));
                    output.accept(PotionContents.createItemStack(Items.SPLASH_POTION, potion));
                    output.accept(PotionContents.createItemStack(Items.LINGERING_POTION, potion));
                }
            })
            .build());

    /** 魔法创造页收尾的药水清单：魔蚀Ⅰ~Ⅳ、长效魔蚀Ⅰ~Ⅳ、蔽魔、长效蔽魔、元素精通Ⅰ~Ⅵ 与长效版。 */
    public static List<net.minecraft.core.Holder<Potion>> brewedPotionLine() {
        List<net.minecraft.core.Holder<Potion>> line = new ArrayList<>();
        line.addAll(MAGIC_EROSION_POTIONS);
        line.addAll(LONG_MAGIC_EROSION_POTIONS);
        line.add(PSEUDOMANA_VEIL_POTION);
        line.add(LONG_PSEUDOMANA_VEIL_POTION);
        line.addAll(ELEMENTAL_MASTERY_POTIONS);
        line.addAll(LONG_ELEMENTAL_MASTERY_POTIONS);
        return line;
    }

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
        // 魔精钢装备紧跟法杖：护甲 → 剑 → 斧 → 镐 → 锹 → 锄；锻造模板属于材料，留在后面的杂项里。
        for (String id : MojiangSteelEquipment.EQUIPMENT_IDS) {
            if (MAGIC_ITEM_IDS.contains(id)) ordered.add(id);
        }
        for (String id : MAGIC_ITEM_IDS) if (id.endsWith("_spawn_egg")) ordered.add(id);
        for (String id : MAGIC_ITEM_IDS) if (!ordered.contains(id)) ordered.add(id);
        return ordered;
    }

    /** 饰品的物品属性：t0/t1 带附魔光效，其余按梯度给普通稀有度。 */
    private static Item.Properties relicProperties(Relics.Relic relic) {
        Item.Properties properties = new Item.Properties().stacksTo(1).rarity(relic.tier().rarity());
        return relic.glint()
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
        if (ElementalMasteryCharmItem.ITEM_ID.equals(id)) return 45;
        if (MagicEquipment.MANA_AMULET.equals(id)) return 50;
        if (MagicEquipment.FOCUS_CLOAK.equals(id) || MagicEquipment.DEFLECT_CLOAK.equals(id)
                || MagicEquipment.MASTER_CLOAK.equals(id)) return 60;
        if (id.endsWith("_casting_gloves")) return 70;
        if ("ancient_casket".equals(id) || "greater_magic_etching".equals(id)) return 80;
        if (ResonanceCore.ITEM_ID.equals(id)) return 90;
        if (RelicEffects.AMULET_CASKET.equals(id)) return 82;
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
        // 内置属性上限修复：先抬一次（原版属性这时候已就位），注册完成后再在实体属性注册前补一次。
        AttributeLimits.apply();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FLUIDS.register(modBus);
        FLUID_TYPES.register(modBus);
        EFFECTS.register(modBus);
        POTIONS.register(modBus);
        SpellSounds.register();
        GoblinSounds.register();
        CorruptedWitherSounds.register();
        // r212 冒险者男声台词（右键搭话/成交/拒绝），不再用村民音。
        AdventurerSounds.register();
        SOUNDS.register(modBus);
        MENUS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        RECIPE_TYPES.register(modBus);
        RoughEquipment.ARMOR_MATERIALS.register(modBus);
        MojiangSteelEquipment.ARMOR_MATERIALS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        LOOT_CONDITIONS.register(modBus);
        CREATIVE_TABS.register(modBus);
        FEATURES.register(modBus);
        // r91：树装饰器（蚀纹木挂巢）。
        TREE_DECORATORS.register(modBus);
        // 蜂巢 POI：让蜜蜂把秽蚀蜂巢认成可入住的家（配套 #minecraft:bee_home 标签）。
        CorruptedBeeContent.POI_TYPES.register(modBus);
        // 通用初始化时把蜂巢方块并进原版 BEEHIVE 方块实体的合法方块集合，区块重载才不丢蜂。
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(CorruptedBeeContent::extendBeehiveBlockEntityType));
        PARTICLE_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        BIOME_SOURCES.register(modBus);
        SpellAttributes.ATTRIBUTES.register(modBus);
        // r271：变异生物白天也能自然刷出（SpawnPlacements 用「不看光照」的怪物规则）。
        modBus.addListener(ErosionWastelandSpawning::registerSpawnPlacements);
        // 秽蚀蠹：荒原地表的动物类生成（踩实心地面、不看光照）。
        modBus.addListener(CorruptedSilverfishEvents::registerSpawnPlacements);
        modBus.addListener(this::createEntityAttributes);
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> MobProfile.invalidate());
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> MobProfile.invalidate());
        modBus.addListener(SpellAttributes::attachToLivingEntities);
        modBus.addListener(ManaSyncPayload::register);
        modBus.addListener(AppraisalSyncPayload::register);
        modBus.addListener(ProAppraisalPayload::register);
        modBus.addListener(ManaRadarPayload::register);
        modBus.addListener(AltarAuraPayload::register);
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
        modBus.addListener(AnvilManaPayload::register);
        // 武备加工台：词条锁定（勾选下发 + 防手滑状态回推）。
        modBus.addListener(WorkbenchLockPayload::register);
        modBus.addListener(WorkbenchGuardPayload::register);
        modBus.addListener(OpenCombatCodexPayload::register);
        modBus.addListener(MagicCircleSyncPayload::register);
        modBus.addListener(MagicCircleActionPayload::register);
        modBus.addListener(MagicCircleNetActionPayload::register);
        modBus.addListener(MagicCircleRemoteListPayload::register);
        modBus.addListener(MagicCircleCodeResultPayload::register);
        modBus.addListener(CorruptedWitherFxPayload::register);
        modBus.addListener(CorruptedWitherBurstPayload::register);
        // r370 深渊侵蚀阶层：魔蚀层数单独同步给带着状态的玩家自己（只用于悬停说明）。
        modBus.addListener(ErosionDepthPayload::register);
        // r317：魔精钢装备摆进原版战斗 / 工具 / 材料创造页。
        modBus.addListener(GeneratedMod::addVanillaTabItems);
        if (FMLEnvironment.dist == Dist.CLIENT) ClientModEvents.register(modBus);
        ManaEvents events = new ManaEvents();
        FragmentLootEvents fragmentLoot = new FragmentLootEvents();
        AccessoryLootEvents accessoryLoot = new AccessoryLootEvents();
        NeoForge.EVENT_BUS.addListener(ManaCommands::register);
        // r317 魔精钢：护甲独立减伤、附魔结算 +1、附魔台高等级相性、末地宝箱模板。
        NeoForge.EVENT_BUS.addListener(MojiangSteelEvents::onDamagePre);
        // 《次元物语：武备》：自然掉落生成词条、Tooltip、铁砧合成和装备属性结算。
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onTooltip);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onAnvilUpdate);
        // 铁砧折扣放在最低优先级：等合成改完价再打折。
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, EquipmentAffixSystem::onAnvilCost);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onEquipmentChange);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onAnvilRepair);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onGrindstonePlace);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onItemEntityJoin);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onLootTableLoad);
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onContainerOpen);
        // 弓、弩、投掷三叉戟命中时补算物理伤害词条（弹射物不吃近战那条攻击伤害属性）。
        NeoForge.EVENT_BUS.addListener(EquipmentAffixSystem::onRangedIncomingDamage);
        // 三叉戟专属词条【戳刺】：激流冲刺的撞击不是弹射物，只能在受击阶段按「玩家正在激流」认出来。
        NeoForge.EVENT_BUS.addListener(ShieldTridentEffects::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(MojiangSteelEvents::onGetEnchantmentLevel);
        NeoForge.EVENT_BUS.addListener(MojiangSteelEvents::onEnchantmentLevelSet);
        NeoForge.EVENT_BUS.addListener(MojiangSteelEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(MojiangSteelEvents::addChestLoot);
        NeoForge.EVENT_BUS.addListener(MojiangSteelEvents::onTooltip);
        NeoForge.EVENT_BUS.addListener(ManaFurnaceRecipeBridge::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ManaFurnaceRecipeBridge::onDatapackSync);
        // 末影龙复活：死亡拦截排在其它死亡处理之前，取消之后别人才看得到 isCanceled()。
        NeoForge.EVENT_BUS.addListener(DragonRevival::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DragonRevival::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DragonRevival::onHeal);
        NeoForge.EVENT_BUS.addListener(DragonRevival::onEntityTick);
        // r289：反虚空领域的首次降生、重生门、膜波纹与边界封印。
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onRespawnPosition);
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onClone);
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onEntityTick);
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onFinalizeSpawn);
        // r290：最后一道闸——任何经任何途径进入反虚空的生物都在这里被拦下（含土史莱姆 bug 的旁路）。
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(AntiVoidSystem::onBlockBreak);
        // 史莱姆驱逐火把：5×5 区块内禁刷史莱姆，16 格内把已经刷出来的史莱姆熏走。
        NeoForge.EVENT_BUS.addListener(SlimeRepellentTorchEvents::onFinalizeSpawn);
        // r105：本模组生物的自然生成权重。数据驱动的群系刷怪（荒原里的晶化行尸 / 魔蚀骸骨 / 秽蚀蠹）
        // 没有代码里的抽取点，只能在这里按权重放行或取消；权重在 dimensional_mana-spawns.toml 里调。
        NeoForge.EVENT_BUS.addListener(MobSpawnWeights::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(SlimeRepellentTorchEvents::onEntityJoin);
        // 进度：击杀类与法阵投影的摆放事件（每秒轮询覆盖不到的那些）。
        NeoForge.EVENT_BUS.addListener(AdvancementTriggers::onLivingDeath);
        // 【敬而远之】：击杀营地首领给威压，献祭 3 只幼崽可解除，玩家死亡则一并消散。
        NeoForge.EVENT_BUS.addListener(GoblinDread::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(AdvancementTriggers::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(events::onJoin);
        NeoForge.EVENT_BUS.addListener(events::onEquipmentChange);
        NeoForge.EVENT_BUS.addListener(events::onLogin);
        NeoForge.EVENT_BUS.addListener(events::onLogout);
        NeoForge.EVENT_BUS.addListener(events::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onServerStopped);
        NeoForge.EVENT_BUS.addListener(events::onClone);
        NeoForge.EVENT_BUS.addListener(events::onVanillaExperience);
        NeoForge.EVENT_BUS.addListener(events::onWake);
        // 资源修补：魔法修补 / 斗气修补的每 10 刻结算、附魔台 1% 命中、图书管理员交易与宝箱书。
        NeoForge.EVENT_BUS.addListener(ResourceMendingEnchantments::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ResourceMendingEnchantments::onPlayerEnchantItem);
        NeoForge.EVENT_BUS.addListener(ResourceMendingEnchantments::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(ResourceMendingEnchantments::onLootTableLoad);
        NeoForge.EVENT_BUS.addListener(CriticalSystem::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(events::onIncomingDamage);
        // r378 魔蚀蜂蜜块：身体接触蜜砖时受到的法术类伤害 −45%（独立乘区，见 ManaEvents.onIncomingDamage）。
        // 接线点就在 events::onIncomingDamage 内部，这里不再单独注册监听。
        // r378 魔法蜡烛：熔炉燃料——魔蚀蜜脾块一份烧 80 秒（与加工台 800 魔素同一档材料）。
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent fuelEvent) -> {
            if (fuelEvent.getItemStack().is(CorruptedBeeContent.CORRUPTED_HONEYCOMB_BLOCK_ITEM.get())) {
                fuelEvent.setBurnTime(1600);
            }
        });
        // r378 魔法蜡烛：蜡烛光环缓存按服务器实例组织，关服清一次，避免跨存档残留。
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent stopEvent) ->
                MagicCandleAura.clear());
        // r107 老刷怪配置迁移：服务器起来（配置已加载完）时检查一次 dimensional_mana-spawns.toml，
        // 如果那六项还全是 r106 的默认值（= 玩家从没动过），就整体翻新成本版默认值。
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent startEvent) ->
                SpawnConfig.migrateLegacyDefaults());
        // r107 卡顿看门狗：服务器刻卡住超过 8 秒时自己把线程栈写进 logs/，下次好定位是谁卡住的。
        NeoForge.EVENT_BUS.addListener(TickWatchdog::onTick);
        NeoForge.EVENT_BUS.addListener(TickWatchdog::onServerStarted);
        NeoForge.EVENT_BUS.addListener(TickWatchdog::onServerStopping);
        // r332 物理词条扩充：受击阶段记下破甲 / 连击 / 处决 / 见切加成（要排在斗气减伤之后、
        // 护甲结算之前，破甲才反推得准）。
        NeoForge.EVENT_BUS.addListener(PhysicalAffixEffects::onIncomingDamage);
        // r230 晶化行尸：致命伤拦截要排在 ManaEvents 的受击链之后（/kill 先被收窄成有限值才拦得住）。
        NeoForge.EVENT_BUS.addListener(CrystallizedZombieEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CrystallizedZombieEvents::onDamagePost);
        NeoForge.EVENT_BUS.addListener(CrystallizedZombieEvents::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(CrystallizedZombieEvents::onLivingDeath);
        // 聚魔爆裂体：魔核冷却期受伤 +50%，死亡时照常引爆紊乱爆炸。
        NeoForge.EVENT_BUS.addListener(ManaCreeperEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ManaCreeperEvents::onLivingDeath);
        // 魔蚀骸骨：死亡瞬间炸出一轮 8 根魂火箭矢的死亡弹幕（刷怪替换走上面那条 onFinalizeSpawn）。
        NeoForge.EVENT_BUS.addListener(CorruptedSkeletonEvents::onLivingDeath);
        // 魔蚀织网者：元素抗性 / 易伤、光系化网、魔蚀的净化限制、死亡清网、自然刷新、剑剪破网。
        NeoForge.EVENT_BUS.addListener(CorruptedWeaverEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CorruptedWeaverEvents::onLightDamage);
        NeoForge.EVENT_BUS.addListener(CorruptedWeaverEvents::onEffectRemove);
        NeoForge.EVENT_BUS.addListener(CorruptedWeaverEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(CorruptedWeaverEvents::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(CorruptedWeaverEvents::onLeftClickBlock);
        // 虫群意识：狂化减伤 + 每 5 刻重按一次仇恨锁（被叫来的蜘蛛被谁碰一下也不会掉头）。
        NeoForge.EVENT_BUS.addListener(HiveMind::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(HiveMind::onLevelTick);
        // 秽蚀蠹：前三次致命伤蜕皮保命、末地传送门遗迹（刷怪笼 + 自己爬出来）、挖魔晶矿钻出。
        NeoForge.EVENT_BUS.addListener(CorruptedSilverfishEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CorruptedSilverfishEvents::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(CorruptedSilverfishEvents::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(CorruptedSilverfishEvents::tickLevel);
        // 深渊侵蚀阶层：任何途径施加魔蚀都记一层（药水 / 指令 / 别的模组也算），
        // 到期不清零、只在没魔蚀的时候慢慢退；上线 / 重生各补一份层数给客户端。
        NeoForge.EVENT_BUS.addListener(MagicErosionEffect::onEffectApplied);
        NeoForge.EVENT_BUS.addListener(MagicErosionEffect::onExpired);
        NeoForge.EVENT_BUS.addListener(ErosionDepthPayload::onLogin);
        NeoForge.EVENT_BUS.addListener(ErosionDepthPayload::onClone);
        // 虚空蚀影：受击分身、眼睛反击、幻影自爆回血、本体死亡清理召唤物。
        NeoForge.EVENT_BUS.addListener(VoidShadeEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(VoidShadeEvents::onDamagePost);
        NeoForge.EVENT_BUS.addListener(VoidShadeEvents::onLivingDeath);
        // r301 魔蚀凋零：受击链路（光易伤 / 全属性减伤 / 聚合体吸收 / 2% 限伤 / 幻影分裂）、
        // 禁疗封印、击杀回魔，以及「魔蚀骷髅头 + 灵魂沙 + 特级魔晶块」的召唤仪式。
        NeoForge.EVENT_BUS.addListener(CorruptedWitherEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CorruptedWitherEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(CorruptedWitherEvents::onHeal);
        NeoForge.EVENT_BUS.addListener(CorruptedWitherEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(CorruptedWitherEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CorruptedWitherEvents::onTooltip);
        NeoForge.EVENT_BUS.addListener(CorruptedWitherEvents::onLevelTick);
        // 流浪剑士：雇佣交互、友伤豁免、完美格挡、远程格挡、瞬身闪避、喝药/集气打断、死亡清理。
        NeoForge.EVENT_BUS.addListener(SwordmasterEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SwordmasterEvents::onProjectileImpact);
        // r406 斗气招架：箭矢、火球这类弹射物飞到身上的那一刻结算。
        // 前 3 刻内按下左键的算完美格挡（原路弹回去、不花斗气），之后按普通格挡（打碎、扣斗气）。
        NeoForge.EVENT_BUS.addListener(BattleQiParry::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(SwordmasterEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(SwordmasterEvents::onLivingDeath);
        // 玩家举着掉落的长剑格挡：完美格挡全免、普通格挡七成减伤。
        NeoForge.EVENT_BUS.addListener(SwordmasterVillageSpawning::onChunkLoad);
        // r400 死锁修复：村庄判定从区块加载回调挪到服务器刻（回调里只记坐标）。
        NeoForge.EVENT_BUS.addListener(SwordmasterVillageSpawning::tickLevel);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onIncomingDamage);
        // 天使庇护的保命兜底要排在所有伤害乘区之后；死亡兜底同理，排在其它死亡处理之后。
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RelicEffects::onAngelShelterFinalGuard);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RelicEffects::onAdvancedBattleQiFinalGuard);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RelicEffects::onAngelShelterDeathGuard);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onDamagePre);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onDamagePost);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onHeal);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onEffectApplicable);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onLivingChangeTarget);
        // r268 蔽魔药水：挡主动索敌（含魔素变异体）+ 攻击 / 受伤即破隐。
        NeoForge.EVENT_BUS.addListener(PseudomanaVeilEvents::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(PseudomanaVeilEvents::onIncomingDamage);
        // r285 铁傀儡不再主动打没有领地意识的元素史莱姆。
        NeoForge.EVENT_BUS.addListener(ElementalSlime::onChangeTarget);
        // r285 魔素变异体之间不误伤：被波及的那只改为按伤害 25% 回血。
        NeoForge.EVENT_BUS.addListener(ManaMutantAlliance::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onVillagerInteract);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onItemEntityJoin);
        NeoForge.EVENT_BUS.addListener(RelicEffects::onLevelTick);
        NeoForge.EVENT_BUS.addListener(RelicLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(RelicLoot::addEntityDrops);
        // r332：先把破甲多出来的伤害与连击 / 处决 / 见切乘区算进去，再走 ManaEvents 的
        // 抗性、诅咒等乘法，保证这些增伤和其它乘区叠加方式一致。
        NeoForge.EVENT_BUS.addListener(PhysicalAffixEffects::onDamagePre);
        NeoForge.EVENT_BUS.addListener(events::onDamagePre);
        NeoForge.EVENT_BUS.addListener(events::onStruckByLightning);
        NeoForge.EVENT_BUS.addListener(events::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(events::onTick);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::tickLevel);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::tickLevel);
        NeoForge.EVENT_BUS.addListener(GoblinSpawning::tickLevel);
        // r276：蚀魔荒原白天的小股变异体补刷（原版刷怪器白天基本不出手）。
        NeoForge.EVENT_BUS.addListener(ErosionWastelandSpawning::tickLevel);
        // r280：高魔素区驻留机制——有变种的 10 秒变异、没变种的 120 秒得魔石病；
        // 金苹果喂食治疗（救村民拿交易折扣）、病死额外掉魔晶粉。
        NeoForge.EVENT_BUS.addListener(ManaStoneSystem::tickLevel);
        NeoForge.EVENT_BUS.addListener(ManaStoneSystem::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ManaStoneSystem::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(GoblinSpawning::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(GoblinSpawning::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GoblinSpawning::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RoughGearEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RoughGearEvents::onShieldBlock);
        // 盾牌专属词条【弹反】：成功格挡后按百分比把被挡下的伤害反弹给攻击者。
        NeoForge.EVENT_BUS.addListener(ShieldTridentEffects::onShieldBlock);
        NeoForge.EVENT_BUS.addListener(RoughGearEvents::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::onExplosion);
        NeoForge.EVENT_BUS.addListener(ElementalSlimeSpawning::onSound);
        NeoForge.EVENT_BUS.addListener(AdventurerSpawning::onChunkLoad);
        // r400 死锁修复：祭坛之间冒险者的落点高度也挪到服务器刻再查。
        NeoForge.EVENT_BUS.addListener(AdventurerSpawning::tickLevel);
        NeoForge.EVENT_BUS.addListener(AncientAltarData::onChunkLoad);
        // r282：露天魔晶矿簇——区块加载补登记（浓度 +4.00 / 粒子 / 变异体回血都靠这张表）。
        NeoForge.EVENT_BUS.addListener(CrystalClusterData::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(CrystalClusterData::tickLevel);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::heal);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::effectRemoved);
        NeoForge.EVENT_BUS.addListener(NewSpellSystem::effectExpired);
        NeoForge.EVENT_BUS.addListener(PoisonSpells::allowForcedPoison);
        // 元素附着强制存在：免疫药水的怪（凋零 / 末影龙 / 监守者 / 别的模组自定义免疫）也吃得到附着。
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ElementReactionSystem::allowForcedAttachment);
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
        // 武备强化石：普通宝箱 2%、幽匿古城 40%（每处最多 4 颗）、凋零 20%、监守者 8%。
        NeoForge.EVENT_BUS.addListener(ArmamentStoneLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(ArmamentStoneLoot::addEntityDrops);
        // r211：法阵案例残片进三处宝箱（祭坛 55%、哥布林 25%、村庄 40%，各有每处上限）。
        NeoForge.EVENT_BUS.addListener(BlueprintFragmentLoot::addChestLoot);
        NeoForge.EVENT_BUS.addListener(accessoryLoot::onLootTableLoad);
        NeoForge.EVENT_BUS.addListener(accessoryLoot::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(events::onDamagePost);
        // r332：落地之后结算物理吸血与元素附加（必须在 events::onDamagePost 之后，拿到的是最终伤害）。
        NeoForge.EVENT_BUS.addListener(PhysicalAffixEffects::onDamagePost);
        NeoForge.EVENT_BUS.addListener(events::onDeath);
        NeoForge.EVENT_BUS.addListener(events::onHeal);
        NeoForge.EVENT_BUS.addListener(events::onEffectAdded);
        // 元素精通药水生效 / 到期 / 被清：三种情况都要让精通缓存失效，状态页才不会停在旧数值。
        NeoForge.EVENT_BUS.addListener(events::onEffectExpired);
        NeoForge.EVENT_BUS.addListener(events::onEffectRemoved);
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
        // 升华药水：神圣庇护拦负面、破灭之力加伤害、死斗意志拒绝死亡、药效到期转反噬。
        NeoForge.EVENT_BUS.addListener(AscensionEvents::onEffectApplicable);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, AscensionEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AscensionEvents::onFatalDamage);
        NeoForge.EVENT_BUS.addListener(AscensionEvents::onEffectExpired);
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
        // 法阵渲染同步：换服清指纹缓存，防旧世界的阵被新世界同 id 顶掉不刷新。
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent stopEvent) -> MagicCircleRenderSync.clearAll());
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
        // 注册阶段的第二次上限修复：把本模组自己注册的属性（法术强度、暴击率……）也一起抬起来，
        // 这样任何实体在建属性表时读到的都已经是修好后的上限。
        AttributeLimits.apply();
        event.put(WANDERING_WITCH.get(), WanderingWitch.createAttributes().build());
        event.put(ADVENTURER.get(), Adventurer.createAttributes().build());
        for (ElementalSlimeVariant variant : ElementalSlimeVariant.values())
            event.put(elementalSlimeType(variant), ElementalSlime.createAttributes().build());
        event.put(NewSpellContent.VINE_SERVANT.get(), SpellSummon.attributes(10, 3, 0.18, 0, 0).build());
        event.put(NewSpellContent.FLYING_SWORD.get(), SpellSummon.attributes(20, 10, 0.05, 20, 8).build());
        event.put(NewSpellContent.POISON_TENTACLE.get(), SpellSummon.attributes(8, 4, 0, 0, 0).build());
        for (GoblinVariant variant : GoblinVariant.values())
            event.put(goblinType(variant), Goblin.createAttributes().build());
        // r230 晶化行尸：80 生命、半速、20 甲、完全免击退。
        event.put(CRYSTALLIZED_ZOMBIE.get(), CrystallizedZombie.createAttributes().build());
        // 聚魔爆裂体：80 生命、原版苦力怕 1.5 倍移速（0.375）、爆炸免疫。
        event.put(MANA_CREEPER.get(), ManaCreeper.createAttributes().build());
        // r244 魔蚀骸骨：80 生命、原版骷髅 1.35 倍移速（0.3375）、4000 魔素。
        event.put(CORRUPTED_SKELETON.get(), CorruptedSkeleton.createAttributes().build());
        // 魔蚀织网者：80 生命、原版蜘蛛 1.5 倍移速（0.45）、跳跃高度 3 倍、5000 魔素。
        event.put(CORRUPTED_WEAVER.get(), CorruptedWeaverEntity.createAttributes().build());
        // 秽蚀蠹：一阶 24 生命、原版蠹虫 1.15 倍移速（0.2875），蜕皮后由实体自己换属性。
        event.put(CORRUPTED_SILVERFISH.get(), CorruptedSilverfishEntity.createAttributes().build());
        // 秽蚀蜂：原版蜜蜂属性 + 20 点生命上限（飞行速度沿用原版 FLY_SPEED 0.3）。
        event.put(CORRUPTED_BEE.get(), CorruptedBeeEntity.createAttributes().build());
        // 流浪剑士：150 生命、20 护甲、走速 0.3 / 追击 0.45、1000 魔素 / 6000 斗气。
        event.put(WANDERING_SWORDMASTER.get(), WanderingSwordmaster.createAttributes().build());
        // r265 虚空蚀影：200 生命、0.375 移速（原版末影人 1.25 倍）、8 物理 + 36 奥术近战、10000 魔素 / 2000 斗气。
        event.put(VOID_SHADE.get(), VoidShadeEntity.createAttributes().build());
        event.put(VOID_PHANTOM.get(), VoidPhantomEntity.createAttributes().build());
        // 寒冰傀儡：35 生命、与雪傀儡一致的 0.2 移速，本身不近战，只射冰锥。
        event.put(NewSpellContent.FROST_GOLEM.get(), SpellSummon.attributes(35, 0, 0.2, 0, 0).build());
        // r301 魔蚀凋零：1000 生命、原版凋零 1.25 倍移速、64 格索敌、完全免击退。
        event.put(CORRUPTED_WITHER.get(), CorruptedWither.createAttributes().build());
        // 凋零幻影：20 生命、不动、不索敌，砍一下就炸。
        event.put(CORRUPTED_WITHER_PHANTOM.get(), CorruptedWitherPhantom.createAttributes().build());
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
            naturalEgg(variant.spawnEggId(), ITEMS.register(variant.spawnEggId(), () ->
                    new DeferredSpawnEggItem(type, variant.color() & 0xFFFFFF,
                            (variant.color() ^ 0x6A4C7A) & 0xFFFFFF, new Item.Properties().rarity(Rarity.RARE))));
        }
    }

    /**
     * 水球：水元素史莱姆法术弹体的外观物品（见 {@link ElementalSlimeVariant#projectileIcon()}）。
     *
     * <p>本体没有功能，摆在「次元物语：自然」页末尾的掉落物区，跟元素结晶粉末、史莱姆掉落物
     * 摆在一起；在注册流程最后调用，这样它不会插到刷怪蛋那一段中间去。</p>
     */
    private static void registerWaterBall() {
        MOD_ITEMS.put(WaterBallItem.ITEM_ID, ITEMS.register(WaterBallItem.ITEM_ID, () ->
                new WaterBallItem(new Item.Properties().rarity(Rarity.COMMON))));
        NATURAL_ITEM_IDS.add(WaterBallItem.ITEM_ID);
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

    /**
     * 魔精钢装备按需求进原版创造页：四件护甲、剑、斧进「战斗」，
     * 镐、锹、锄进「工具与实用物品」，锻造模板进「原材料」。
     */
    private static void addVanillaTabItems(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        List<String> ids;
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.COMBAT)) {
            ids = MojiangSteelEquipment.COMBAT_TAB_IDS;
        } else if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            ids = MojiangSteelEquipment.TOOLS_TAB_IDS;
        } else if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)) {
            ids = MojiangSteelEquipment.INGREDIENT_TAB_IDS;
        } else {
            return;
        }
        for (String id : ids) {
            DeferredItem<? extends Item> item = MOD_ITEMS.get(id);
            if (item != null) event.accept(new ItemStack(item.get()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
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

    /**
     * r282：魔晶块。整块就是一块可以当建筑材料的结晶方块，用紫水晶的声音和挖取要求，
     * 掉落自己、能熔炼成对应成品魔晶（配方在 data 里）。
     */
    private static void crystalBlock(String name, float hardness, int light, Rarity rarity) {
        block(name, BlockBehaviour.Properties.of().strength(hardness, 6.0F).requiresCorrectToolForDrops()
                .sound(SoundType.AMETHYST).lightLevel(s -> light), rarity);
    }

    /**
     * r282：粗制魔晶块。还没炼过的原矿压成的整块，硬度低一点、光也弱一档，
     * 音效沿用石质（原版粗铁块也是石头声音）。
     */
    private static void rawCrystalBlock(String name, float hardness, int light, Rarity rarity) {
        block(name, BlockBehaviour.Properties.of().strength(hardness, 6.0F).requiresCorrectToolForDrops()
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
        // 结构宝箱通用池：原版与其它模组新增结构的宝箱都算，判定见 StructureChestLoot。
        if (!StructureChestLoot.isForeignStructureChest(event.getName())) return;
        LootPool.Builder pool = LootPool.lootPool().name("dimensional_mana_talent_scroll")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(0.00005F));
        for (ElementType element : ElementType.values()) pool.add(LootItem.lootTableItem(MOD_ITEMS.get(element.scrollId()).get()));
        StructureChestLoot.addPool(event.getTable(), pool.build());
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

        // ── r268：魔蚀药水线 ──
        // 基础：1 个魔蚀蜘蛛眼 + 剧毒药水 → 魔蚀药水Ⅰ；带红石底（延长的剧毒药水）直接出延长版Ⅰ。
        Item erosionEye = MOD_ITEMS.get(CORRUPTED_SPIDER_EYE_ID).get();
        event.getBuilder().addMix(Potions.POISON, erosionEye, MAGIC_EROSION_POTIONS.get(0));
        // 剧毒Ⅱ药水（强化版）也算「剧毒药水」打底，别让玩家换瓶子重酿。
        event.getBuilder().addMix(Potions.STRONG_POISON, erosionEye, MAGIC_EROSION_POTIONS.get(0));
        event.getBuilder().addMix(Potions.LONG_POISON, erosionEye, LONG_MAGIC_EROSION_POTIONS.get(0));
        // 升级：继续加魔蚀蜘蛛眼，Ⅰ→Ⅱ→Ⅲ→Ⅳ（最高 4 级）；延长版同样逐级升。
        for (int tier = 0; tier + 1 < 4; tier++) {
            event.getBuilder().addMix(MAGIC_EROSION_POTIONS.get(tier), erosionEye, MAGIC_EROSION_POTIONS.get(tier + 1));
            event.getBuilder().addMix(LONG_MAGIC_EROSION_POTIONS.get(tier), erosionEye,
                    LONG_MAGIC_EROSION_POTIONS.get(tier + 1));
        }
        // 红石：任何等级延长到 2 分钟；喷溅 / 滞留走原版火药、龙息容器配方，自动套用。
        for (int tier = 0; tier < 4; tier++) {
            event.getBuilder().addMix(MAGIC_EROSION_POTIONS.get(tier), Items.REDSTONE,
                    LONG_MAGIC_EROSION_POTIONS.get(tier));
        }

        // ── r268：蔽魔药水线 ──
        // 基础：1 个畸变魔晶片 + 魔素调和剂 → 1 分钟蔽魔药水（调和剂不是药水，走通用配方接口）。
        event.getBuilder().addRecipe(
                Ingredient.of(MOD_ITEMS.get("mana_harmonizer").get()),
                Ingredient.of(MOD_ITEMS.get(DistortedCrystalShardEntity.ITEM_ID).get()),
                PotionContents.createItemStack(Items.POTION, PSEUDOMANA_VEIL_POTION));
        // 红石：延长到 3 分钟；喷溅 / 滞留同样自动。
        event.getBuilder().addMix(PSEUDOMANA_VEIL_POTION, Items.REDSTONE, LONG_PSEUDOMANA_VEIL_POTION);

        // ── 元素精通药水线 ──
        // 基础：上方 1 份超导魔晶粉 + 下方最多 3 瓶魔素调和剂 → 一次酿 3 瓶元素精通Ⅰ。
        // 调和剂不是药水，走通用配方接口；三格底部会各自产出一瓶，正好“一次酿三瓶”。
        event.getBuilder().addRecipe(
                Ingredient.of(MOD_ITEMS.get("mana_harmonizer").get()),
                Ingredient.of(MOD_ITEMS.get("superconducting_crystal_dust").get()),
                ElementalMasteryPotions.stack(ElementalMasteryPotions.token(1, false), 1));
        // 红石：15 秒翻倍成 30 秒；喷溅 / 滞留同理自动套用。
        for (int level = 1; level <= ElementalMasteryPotions.MAX_LEVEL; level++) {
            event.getBuilder().addMix(ELEMENTAL_MASTERY_POTIONS.get(level - 1), Items.REDSTONE,
                    LONG_ELEMENTAL_MASTERY_POTIONS.get(level - 1));
        }
    }
}
