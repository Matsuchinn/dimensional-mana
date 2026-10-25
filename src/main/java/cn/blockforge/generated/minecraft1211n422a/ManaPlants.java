package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 「次元物语：自然」新增的五种魔法植物与三份植物粉末、两瓶植物药剂。
 *
 * <p>植物以“魔素浓度”为第一生长条件，现在改由世界生成（{@link ManaPlantFeature}）在区块生成时种好，
 * 只用地形阶段能算出的地表浓度；靠近洞穴的蕾薇莉亚由 {@link ManaPlantSpawning} 在区块加载时补种。
 * 玩家家附近不会再每两秒钟扫一遍，密度也不再随玩家移动而膨胀。</p>
 */
public final class ManaPlants {
    public static final String RESTELIA_POWDER = "restelia_powder";
    public static final String LEVILIA_POWDER = "levilia_powder";
    public static final String LUMELIA_POWDER = "lumelia_powder";
    public static final String HEALING_DRAUGHT = "healing_draught";
    public static final String FULL_HEALING_DRAUGHT = "full_healing_draught";
    /** 暮刺花：黄昏开花时采集暮刺荆棘的额外掉落。 */
    public static final String VESPER_BLOOM = "vesper_bloom";

    /** 五种植物。浓度区间两端是否含端点单独标注：卢梅莉亚写的是“12.0＜浓度＜14.0”。 */
    public enum Kind {
        RESTELIA("restelia", 10, 300, 12.0, Double.MAX_VALUE, true, false, 4, 0x74D66A),
        LEVILIA("levilia", 20, 200, 15.0, Double.MAX_VALUE, true, false, 6, 0xB46BFF),
        LUMELIA("lumelia", 100, 180, 12.0, 14.0, false, false, 10, 0x8CE0B0),
        BLOOD_KISS("blood_kiss", 50, 90, 10.0, Double.MAX_VALUE, false, false, 0, 0x8E1B2E),
        // 暮刺荆棘：只长在魔素 8～11 的交界带（两端都含）。
        VESPER_THORN("vesper_thorn", 60, 200, 8.0, 11.0, true, true, 0, 0xC98CE8);

        private final String id;
        private final int minY;
        private final int maxY;
        private final double minMana;
        private final double maxMana;
        private final boolean minInclusive;
        private final boolean maxInclusive;
        private final int light;
        private final int glowColor;

        Kind(String id, int minY, int maxY, double minMana, double maxMana,
             boolean minInclusive, boolean maxInclusive, int light, int glowColor) {
            this.id = id;
            this.minY = minY;
            this.maxY = maxY;
            this.minMana = minMana;
            this.maxMana = maxMana;
            this.minInclusive = minInclusive;
            this.maxInclusive = maxInclusive;
            this.light = light;
            this.glowColor = glowColor;
        }

        public String id() { return id; }
        public int minY() { return minY; }
        public int maxY() { return maxY; }
        public int light() { return light; }
        public int glowColor() { return glowColor; }

        /** 这一格的魔素浓度是否落在该植物的生长区间内。 */
        public boolean manaFits(double mana) {
            boolean above = minInclusive ? mana >= minMana : mana > minMana;
            boolean below = maxInclusive ? mana <= maxMana : mana < maxMana;
            return above && below;
        }

        public boolean heightFits(int y) {
            return y >= minY && y <= maxY;
        }

        public String translationKey() {
            return "block.dimensional_mana." + id;
        }

        public String tooltipKey() {
            return "block.dimensional_mana." + id + ".tooltip";
        }
    }

    /** 注册全部植物、粉末与药剂；由 {@link GeneratedMod} 的静态初始化块调用。 */
    public static void register() {
        for (Kind kind : Kind.values()) registerPlant(kind);
        for (Kind kind : Kind.values()) GeneratedMod.NATURAL_ITEM_IDS.add(kind.id());

        powder(RESTELIA_POWDER, Rarity.UNCOMMON);
        powder(LEVILIA_POWDER, Rarity.UNCOMMON);
        powder(LUMELIA_POWDER, Rarity.RARE);

        GeneratedMod.MOD_ITEMS.put(HEALING_DRAUGHT, GeneratedMod.ITEMS.register(HEALING_DRAUGHT,
                () -> new PlantDraughtItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
                        0.5, 1, false)));
        GeneratedMod.NATURAL_ITEM_IDS.add(HEALING_DRAUGHT);
        GeneratedMod.MOD_ITEMS.put(FULL_HEALING_DRAUGHT, GeneratedMod.ITEMS.register(FULL_HEALING_DRAUGHT,
                () -> new PlantDraughtItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
                        1.0, 2, true)));
        GeneratedMod.NATURAL_ITEM_IDS.add(FULL_HEALING_DRAUGHT);

        // 暮刺花：只有黄昏开花时采集暮刺荆棘才会掉落，闭花时没有。
        GeneratedMod.MOD_ITEMS.put(VESPER_BLOOM, GeneratedMod.ITEMS.register(VESPER_BLOOM,
                () -> new PlantPowderItem(new Item.Properties().rarity(Rarity.RARE),
                        "item.dimensional_mana.vesper_bloom.tooltip")));
        GeneratedMod.NATURAL_ITEM_IDS.add(VESPER_BLOOM);
    }

    private static void registerPlant(Kind kind) {
        DeferredBlock<net.minecraft.world.level.block.Block> block = GeneratedMod.BLOCKS.register(kind.id(),
                () -> switch (kind) {
                    case LUMELIA -> new LumeliaPlantBlock(kind, properties(kind));
                    case VESPER_THORN -> new VesperThornBlock(properties(kind));
                    default -> new ManaPlantBlock(kind, properties(kind));
                });
        GeneratedMod.MOD_BLOCKS.put(kind.id(), block);
        GeneratedMod.MOD_ITEMS.put(kind.id(), GeneratedMod.ITEMS.register(kind.id(),
                () -> new BlockItem(block.get(), new Item.Properties().rarity(rarityOf(kind)))));
    }

    private static BlockBehaviour.Properties properties(Kind kind) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .instabreak()
                .sound(SoundType.GRASS)
                .pushReaction(PushReaction.DESTROY)
                .noOcclusion();
        // 贴墙的卢梅莉亚不能带随机位置偏移，否则会一半陷进墙里。
        if (kind != Kind.LUMELIA) properties.offsetType(BlockBehaviour.OffsetType.XZ);
        if (kind.light() > 0) properties.lightLevel(state -> kind.light());
        // 卢梅莉亚靠随机刻往上爬；暮刺荆棘靠随机刻把“开不开花”校准回来（世界生成时排不了计划刻）。
        if (kind == Kind.LUMELIA || kind == Kind.VESPER_THORN) properties.randomTicks();
        return properties;
    }

    private static Rarity rarityOf(Kind kind) {
        return switch (kind) {
            case LUMELIA -> Rarity.RARE;
            case LEVILIA, BLOOD_KISS, VESPER_THORN -> Rarity.UNCOMMON;
            default -> Rarity.COMMON;
        };
    }

    private static void powder(String id, Rarity rarity) {
        GeneratedMod.MOD_ITEMS.put(id, GeneratedMod.ITEMS.register(id, () -> new PlantPowderItem(
                new Item.Properties().rarity(rarity), "item.dimensional_mana." + id + ".tooltip")));
        GeneratedMod.NATURAL_ITEM_IDS.add(id);
    }

    /** 能否在这块方块上扎根：草、土、苔、砂、石都行，沙漠与沼泽的沙也得算。 */
    public static boolean supported(BlockState below) {
        return below.is(BlockTags.DIRT)
                || below.is(BlockTags.SAND)
                || below.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)
                || below.is(net.minecraft.world.level.block.Blocks.STONE)
                || below.is(net.minecraft.world.level.block.Blocks.ANDESITE)
                || below.is(net.minecraft.world.level.block.Blocks.GRANITE)
                || below.is(net.minecraft.world.level.block.Blocks.DIORITE)
                || below.is(net.minecraft.world.level.block.Blocks.TUFF)
                || below.is(net.minecraft.world.level.block.Blocks.CLAY)
                || below.is(net.minecraft.world.level.block.Blocks.MUD)
                || below.is(net.minecraft.world.level.block.Blocks.MANGROVE_ROOTS)
                || below.is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE);
    }

    /** 目标位置能不能落一株植物：脚下有支撑、当前位置是空的且不是水。 */
    public static boolean canPlantAt(BlockGetter level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (!here.canBeReplaced()) return false;
        BlockState below = level.getBlockState(pos.below());
        return supported(below);
    }

    /** 附近 4 格内的花朵与野草数量，瑞斯特莉亚只在“有点热闹”的草地里扎根。 */
    public static int neighbourFlora(LevelReader level, BlockPos pos, int radius) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -1; y <= 2; y++) {
                    cursor.setWithOffset(pos, x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS)
                            || state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)
                            || state.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)
                            || state.is(net.minecraft.world.level.block.Blocks.FERN)
                            || state.is(net.minecraft.world.level.block.Blocks.LARGE_FERN)) {
                        if (++count >= 12) return count;
                    }
                }
            }
        }
        return count;
    }

    /** 把一株植物放进世界；放不下就返回 false，交给调用方换位置。 */
    public static boolean place(LevelAccessor level, Kind kind, BlockPos pos) {
        if (kind == Kind.LUMELIA) {
            // 卢梅莉亚两种活法：脚下有土就照常长，脚下没有但背后有岩壁就贴墙。
            if (canPlantAt(level, pos)) return setPlant(level, pos, defaultState(kind));
            Direction wall = wallFacing(level, pos);
            if (wall == null) return false;
            return setPlant(level, pos, defaultState(kind)
                    .setValue(LumeliaPlantBlock.WALL_MOUNTED, true)
                    .setValue(LumeliaPlantBlock.FACING, wall));
        }
        if (!canPlantAt(level, pos)) return false;
        return setPlant(level, pos, defaultState(kind));
    }

    private static BlockState defaultState(Kind kind) {
        return GeneratedMod.MOD_BLOCKS.get(kind.id()).get().defaultBlockState();
    }

    private static boolean setPlant(LevelAccessor level, BlockPos pos, BlockState state) {
        boolean placed = level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        // 暮刺荆棘靠计划刻在花开花闭之间切换；世界生成阶段排不进计划刻，
        // 那种情况由方块自己的随机刻兜底，这里只处理真正落在服务端世界里的种植。
        if (placed && state.getBlock() instanceof VesperThornBlock
                && level instanceof net.minecraft.server.level.ServerLevel server) {
            server.scheduleTick(pos, state.getBlock(), 40);
        }
        return placed;
    }

    /** 卢梅莉亚能贴哪面墙：这一格得是空的，四个水平方向里找一块脸朝这里的实心方块。 */
    public static Direction wallFacing(BlockGetter level, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()) return null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos support = pos.relative(direction.getOpposite());
            BlockState state = level.getBlockState(support);
            if (state.isFaceSturdy(level, support, direction)) return direction;
        }
        return null;
    }

    /** 草原与森林的交汇处：本格是一边，十六格外或同一区块里的另一处是另一边。 */
    public static boolean atBiomeBorder(LevelReader level, BlockPos pos) {
        Holder<Biome> here = safeBiome(level, pos);
        if (here == null) return false;
        boolean forest = here.is(BiomeTags.IS_FOREST);
        boolean plains = isPlains(here);
        if (!forest && !plains) return false;

        // 群系边界可能刚好落在隔壁区块：十六格外看一眼。
        // 世界生成阶段那块区块常常还没有，safeBiome 会返回 null 直接跳过——
        // 以前这里硬查会抛 “Requested chunk unavailable during world generation”，把游戏弄崩。
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (isOtherSide(safeBiome(level, pos.relative(direction, 16)), forest)) return true;
        }

        // 边界也可能正好从本区块中间穿过：在 pos 所在区块里取几个采样点。
        // 这些点都在正在生成的那块区块里，什么阶段都读得到。
        int baseX = pos.getX() & ~15;
        int baseZ = pos.getZ() & ~15;
        for (int dx = 3; dx <= 13; dx += 5) {
            for (int dz = 3; dz <= 13; dz += 5) {
                if (isOtherSide(safeBiome(level, new BlockPos(baseX + dx, pos.getY(), baseZ + dz)), forest)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPlains(Holder<Biome> biome) {
        return biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS)
                || biome.is(Biomes.MEADOW) || biome.is(Biomes.SNOWY_PLAINS);
    }

    /** 本格是森林时，对侧得是草原类；本格是草原类时，对侧得是森林。 */
    private static boolean isOtherSide(Holder<Biome> other, boolean forest) {
        if (other == null) return false;
        if (forest) {
            return other.is(Biomes.PLAINS) || other.is(Biomes.SUNFLOWER_PLAINS)
                    || other.is(Biomes.MEADOW);
        }
        return other.is(BiomeTags.IS_FOREST);
    }

    /** 世界生成阶段只能读已经装进生成区域的区块，读不到就返回 null，绝不去硬查。 */
    private static Holder<Biome> safeBiome(LevelReader level, BlockPos pos) {
        if (!WorldgenReads.biomeReadable(level, pos)) return null;
        try {
            return level.getBiome(pos);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 随机偏移一株同种植物，用于瑞斯特莉亚“2~5 株扎堆”。 */
    public static void placeCluster(Level level, Kind kind, BlockPos origin, RandomSource random, int amount) {
        int placed = 0;
        for (int tries = 0; tries < 12 && placed < amount; tries++) {
            BlockPos target = origin.offset(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
            if (target.equals(origin) || place(level, kind, target)) placed++;
        }
    }

    /** 玩家/生物脚下这格是不是某一类植物。 */
    public static boolean isPlant(BlockState state, Kind kind) {
        return state.is(GeneratedMod.MOD_BLOCKS.get(kind.id()).get());
    }

    /** 世界时间是否处于“黄昏”：日落开始后的那一段，太阳刚落、天还没黑。 */
    public static boolean isDusk(Level level) {
        long time = level.getDayTime() % 24000L;
        return time >= 12000L && time < 13800L;
    }

    private ManaPlants() {
    }
}
