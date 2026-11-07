package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Optional;

/**
 * 蚀魔荒原的全部方块 / 物品注册：秽蚀土与秽蚀草块、蚀纹木一族（原木、去皮、板、台阶、
 * 楼梯、栅栏、栅栏门、按钮、压力板、门、活板门）、蚀纹树叶与树苗、蚀脉石一族、秽生草，
 * 外加自定义落叶粒子 {@code erosion_leaf}。
 *
 * <p>注册风格沿用本工程习惯：方块进 {@code GeneratedMod.BLOCKS}，方块物品进 {@code ITEMS}，
 * 同时登记到 {@code MOD_BLOCKS / MOD_ITEMS}（主创造页自动收），再列进自然创造页清单。</p>
 */
public final class ErosionWasteland {
    // ── 蚀纹木专属音色（r276） ──
    /**
     * 蚀纹木的方块音色：直接在诡异菌木（{@code nether_wood}）那五段声音上把音高压到 0.72、
     * 音量略抬到 1.05，做出「空心、发闷」的木头声。放置、踩踏、破坏、敲击、落地全走这一套。
     */
    public static final SoundType EROSION_WOOD_SOUND = new SoundType(1.05F, 0.72F,
            SoundEvents.NETHER_WOOD_BREAK, SoundEvents.NETHER_WOOD_STEP, SoundEvents.NETHER_WOOD_PLACE,
            SoundEvents.NETHER_WOOD_HIT, SoundEvents.NETHER_WOOD_FALL);

    /** 门 / 活板门 / 按钮 / 压力板的开关声，同样换成诡异菌木那一套，和普通木门区分开。 */
    public static final BlockSetType EROSION_SET_TYPE = new BlockSetType("erosion",
            true, true, true, BlockSetType.PressurePlateSensitivity.EVERYTHING, EROSION_WOOD_SOUND,
            SoundEvents.NETHER_WOOD_DOOR_CLOSE, SoundEvents.NETHER_WOOD_DOOR_OPEN,
            SoundEvents.NETHER_WOOD_TRAPDOOR_CLOSE, SoundEvents.NETHER_WOOD_TRAPDOOR_OPEN,
            SoundEvents.NETHER_WOOD_PRESSURE_PLATE_CLICK_OFF, SoundEvents.NETHER_WOOD_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.NETHER_WOOD_BUTTON_CLICK_OFF, SoundEvents.NETHER_WOOD_BUTTON_CLICK_ON);

    /** 栅栏门：开门 / 关门声也换成诡异菌木的，音色同上。 */
    public static final WoodType EROSION_WOOD_TYPE = new WoodType("erosion", EROSION_SET_TYPE,
            EROSION_WOOD_SOUND, SoundType.NETHER_WOOD_HANGING_SIGN,
            SoundEvents.NETHER_WOOD_FENCE_GATE_CLOSE, SoundEvents.NETHER_WOOD_FENCE_GATE_OPEN);

    static {
        // 登记进原版的「方块套件 / 木种」表，存档和 /setblock 里出现名字时能查回来。
        BlockSetType.register(EROSION_SET_TYPE);
        WoodType.register(EROSION_WOOD_TYPE);
    }

    // ── 土 / 草 ──
    public static final DeferredBlock<ErosionDirtBlock> EROSION_DIRT =
            GeneratedMod.BLOCKS.register("erosion_dirt", () -> new ErosionDirtBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                            .instrument(NoteBlockInstrument.BASEDRUM)
                            .strength(0.6F).sound(SoundType.GRAVEL)));
    public static final DeferredBlock<ErosionGrassBlock> EROSION_GRASS =
            GeneratedMod.BLOCKS.register("erosion_grass_block", () -> new ErosionGrassBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).randomTicks()
                            .strength(0.65F).sound(SoundType.GRASS)));

    // ── 蚀纹木一族 ──
    public static final DeferredBlock<RotatedPillarBlock> EROSION_LOG =
            GeneratedMod.BLOCKS.register("erosion_log", () -> new RotatedPillarBlock(wood()));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_EROSION_LOG =
            GeneratedMod.BLOCKS.register("stripped_erosion_log", () -> new RotatedPillarBlock(wood()));
    public static final DeferredBlock<Block> EROSION_PLANKS =
            GeneratedMod.BLOCKS.register("erosion_planks", () -> new Block(wood()));
    public static final DeferredBlock<SlabBlock> EROSION_SLAB =
            GeneratedMod.BLOCKS.register("erosion_slab", () -> new SlabBlock(wood()));
    public static final DeferredBlock<StairBlock> EROSION_STAIRS =
            GeneratedMod.BLOCKS.register("erosion_stairs",
                    () -> new StairBlock(EROSION_PLANKS.get().defaultBlockState(), wood()));
    public static final DeferredBlock<FenceBlock> EROSION_FENCE =
            GeneratedMod.BLOCKS.register("erosion_fence", () -> new FenceBlock(wood()));
    public static final DeferredBlock<FenceGateBlock> EROSION_FENCE_GATE =
            GeneratedMod.BLOCKS.register("erosion_fence_gate", () -> new FenceGateBlock(EROSION_WOOD_TYPE, wood()));
    public static final DeferredBlock<PressurePlateBlock> EROSION_PRESSURE_PLATE =
            GeneratedMod.BLOCKS.register("erosion_pressure_plate", () -> new PressurePlateBlock(
                    EROSION_SET_TYPE, wood().noCollission().strength(0.5F)));
    public static final DeferredBlock<ButtonBlock> EROSION_BUTTON =
            GeneratedMod.BLOCKS.register("erosion_button", () -> new ButtonBlock(
                    EROSION_SET_TYPE, 30, wood().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<DoorBlock> EROSION_DOOR =
            GeneratedMod.BLOCKS.register("erosion_door", () -> new ErosionDoorBlock(
                    EROSION_SET_TYPE, wood().noOcclusion().strength(3.0F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<TrapDoorBlock> EROSION_TRAPDOOR =
            GeneratedMod.BLOCKS.register("erosion_trapdoor", () -> new TrapDoorBlock(
                    EROSION_SET_TYPE, wood().noOcclusion().strength(3.0F)));

    public static final DeferredBlock<ErosionLeavesBlock> EROSION_LEAVES =
            GeneratedMod.BLOCKS.register("erosion_leaves", () -> new ErosionLeavesBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().randomTicks()
                            .strength(0.2F).sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));
    /** 树苗长成的树：指向 JSON 配置特征 {@code dimensional_mana:erosion_tree}。 */
    static final TreeGrower EROSION_TREE_GROWER = new TreeGrower("erosion",
            Optional.empty(), Optional.of(CorruptedWastelandBiome.EROSION_TREE), Optional.empty());

    public static final DeferredBlock<SaplingBlock> EROSION_SAPLING =
            GeneratedMod.BLOCKS.register("erosion_sapling", () -> new SaplingBlock(EROSION_TREE_GROWER,
                    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().noOcclusion()
                            .randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));

    // ── 蚀脉石一族 ──
    public static final DeferredBlock<ErosionStoneBlock> EROSION_STONE =
            GeneratedMod.BLOCKS.register("erosion_stone", () -> new ErosionStoneBlock(stone()));
    public static final DeferredBlock<Block> SMOOTH_EROSION_STONE =
            GeneratedMod.BLOCKS.register("smooth_erosion_stone", () -> new Block(stone().strength(2.0F, 6.0F)));
    public static final DeferredBlock<Block> EROSION_STONE_BRICKS =
            GeneratedMod.BLOCKS.register("erosion_stone_bricks", () -> new Block(stone()));
    public static final DeferredBlock<Block> EROSION_COBBLESTONE =
            GeneratedMod.BLOCKS.register("erosion_cobblestone", () -> new Block(stone().strength(2.0F, 6.0F)));
    public static final DeferredBlock<SlabBlock> EROSION_STONE_SLAB =
            GeneratedMod.BLOCKS.register("erosion_stone_slab", () -> new SlabBlock(stone()));
    public static final DeferredBlock<SlabBlock> SMOOTH_EROSION_STONE_SLAB =
            GeneratedMod.BLOCKS.register("smooth_erosion_stone_slab", () -> new SlabBlock(stone()));
    public static final DeferredBlock<SlabBlock> EROSION_STONE_BRICK_SLAB =
            GeneratedMod.BLOCKS.register("erosion_stone_brick_slab", () -> new SlabBlock(stone()));
    public static final DeferredBlock<SlabBlock> EROSION_COBBLESTONE_SLAB =
            GeneratedMod.BLOCKS.register("erosion_cobblestone_slab", () -> new SlabBlock(stone()));
    public static final DeferredBlock<StairBlock> EROSION_STONE_STAIRS =
            GeneratedMod.BLOCKS.register("erosion_stone_stairs",
                    () -> new StairBlock(EROSION_STONE.get().defaultBlockState(), stone()));
    public static final DeferredBlock<StairBlock> SMOOTH_EROSION_STONE_STAIRS =
            GeneratedMod.BLOCKS.register("smooth_erosion_stone_stairs",
                    () -> new StairBlock(SMOOTH_EROSION_STONE.get().defaultBlockState(), stone()));
    public static final DeferredBlock<StairBlock> EROSION_STONE_BRICK_STAIRS =
            GeneratedMod.BLOCKS.register("erosion_stone_brick_stairs",
                    () -> new StairBlock(EROSION_STONE_BRICKS.get().defaultBlockState(), stone()));
    public static final DeferredBlock<StairBlock> EROSION_COBBLESTONE_STAIRS =
            GeneratedMod.BLOCKS.register("erosion_cobblestone_stairs",
                    () -> new StairBlock(EROSION_COBBLESTONE.get().defaultBlockState(), stone()));
    public static final DeferredBlock<WallBlock> EROSION_COBBLESTONE_WALL =
            GeneratedMod.BLOCKS.register("erosion_cobblestone_wall", () -> new WallBlock(stone()));
    public static final DeferredBlock<WallBlock> EROSION_STONE_BRICK_WALL =
            GeneratedMod.BLOCKS.register("erosion_stone_brick_wall", () -> new WallBlock(stone()));

    // ── 秽生草 ──
    public static final DeferredBlock<WitheredGrassBlock> WITHERED_GRASS =
            GeneratedMod.BLOCKS.register("withered_grass_block", () -> new WitheredGrassBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak()
                            .sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));

    /** 自定义落叶粒子：紫色版樱花落叶（贴图 assets/dimensional_mana/particle/erosion_leaf.png）。 */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> EROSION_LEAF =
            GeneratedMod.PARTICLE_TYPES.register("erosion_leaf", () -> new SimpleParticleType(false));

    /** 自然创造页要收的物品 ID（顺序即页内顺序）。 */
    static final String[] NATURAL_IDS = {
            "erosion_dirt", "erosion_grass_block", "withered_grass_block",
            "erosion_log", "stripped_erosion_log", "erosion_planks", "erosion_slab", "erosion_stairs",
            "erosion_fence", "erosion_fence_gate", "erosion_pressure_plate", "erosion_button",
            "erosion_door", "erosion_trapdoor", "erosion_leaves", "erosion_sapling",
            // 蚀纹木木棍跟蚀纹木这一家子摆一起（以前落在「魔素」页，和木板隔了一整页）。
            "erosion_stick",
            "erosion_stone", "smooth_erosion_stone", "erosion_stone_bricks", "erosion_cobblestone",
            "erosion_stone_slab", "smooth_erosion_stone_slab", "erosion_stone_brick_slab", "erosion_cobblestone_slab",
            "erosion_stone_stairs", "smooth_erosion_stone_stairs", "erosion_stone_brick_stairs", "erosion_cobblestone_stairs",
            "erosion_cobblestone_wall", "erosion_stone_brick_wall",
    };

    /** 由 GeneratedMod 静态块调用：给每个方块补方块物品，并把自然页清单灌进去。 */
    static void registerItems() {
        item(EROSION_DIRT); item(EROSION_GRASS); item(WITHERED_GRASS);
        item(EROSION_LOG); item(STRIPPED_EROSION_LOG); item(EROSION_PLANKS); item(EROSION_SLAB);
        item(EROSION_STAIRS); item(EROSION_FENCE); item(EROSION_FENCE_GATE); item(EROSION_PRESSURE_PLATE);
        item(EROSION_BUTTON); item(EROSION_DOOR); item(EROSION_TRAPDOOR); item(EROSION_LEAVES);
        item(EROSION_SAPLING);
        item(EROSION_STONE); item(SMOOTH_EROSION_STONE); item(EROSION_STONE_BRICKS); item(EROSION_COBBLESTONE);
        item(EROSION_STONE_SLAB); item(SMOOTH_EROSION_STONE_SLAB); item(EROSION_STONE_BRICK_SLAB);
        item(EROSION_COBBLESTONE_SLAB); item(EROSION_STONE_STAIRS); item(SMOOTH_EROSION_STONE_STAIRS);
        item(EROSION_STONE_BRICK_STAIRS); item(EROSION_COBBLESTONE_STAIRS);
        item(EROSION_COBBLESTONE_WALL); item(EROSION_STONE_BRICK_WALL);
        GeneratedMod.NATURAL_ITEM_IDS.addAll(java.util.List.of(NATURAL_IDS));
    }

    /** 注册方块物品并登记到主创造页映射（MOD_BLOCKS/MOD_ITEMS）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DeferredItem<BlockItem> item(DeferredBlock<? extends Block> block) {
        String id = ((DeferredHolder) block).getKey().location().getPath();
        GeneratedMod.MOD_BLOCKS.put(id, (DeferredBlock<Block>) (DeferredBlock) block);
        DeferredItem<BlockItem> item = GeneratedMod.ITEMS.register(id,
                () -> new BlockItem(block.value(), new Item.Properties()));
        // 必须同时登记进 MOD_ITEMS：自然页与主创造页都从这个映射里取物品，漏了就直接不显示。
        GeneratedMod.MOD_ITEMS.put(id, item);
        return item;
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                .strength(2.0F, 3.0F).sound(EROSION_WOOD_SOUND).ignitedByLava();
    }

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(1.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    private ErosionWasteland() { }
}
