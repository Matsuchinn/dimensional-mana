package cn.blockforge.generated.minecraft1211n422a;

import java.lang.reflect.Field;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 秽蚀蜂系列的注册中枢：蜂巢方块、蜂 POI、世界生成 feature、魔蚀蜂蜜。
 *
 * <p><b>方块实例必须延迟创建（r375 崩溃修复）。</b>1.21.1 的 {@code Block} 构造函数会调用
 * {@code BuiltInRegistries.BLOCK.createIntrusiveHolder(this)} 往注册表挂 holder，而注册表在
 * 模组主类静态初始化 / 构造阶段还是冻结的，要等 NeoForge 派发 RegisterEvent 前才解冻——
 * 上一版在这里 {@code static final} 急切 {@code new CorruptedNestBlock(...)}，
 * 直接炸出 {@code IllegalStateException: Registry is already frozen}。
 * 现在实例由第一个用到它的 supplier（方块注册或 POI 注册，先后顺序不保证）在解冻窗口内创建，
 * 两边共用同一个对象，不存在「拿未注册实例」的问题。</p>
 *
 * <p>原版 {@code BeehiveBlock} 的机制（存蜂、酿蜜、烟熏、放蜂）全部白拿，
 * 代价是它的方块实体类型 {@code BlockEntityType.BEEHIVE} 的合法方块集合里没有蜂巢，
 * 区块重载时原版会把这个 BE 当成「类型不匹配」直接丢弃——四只蜂就这么没了。
 * 所以 {@link #extendBeehiveBlockEntityType()} 用反射把蜂巢塞进该集合，这是最小侵入的做法。</p>
 */
public final class CorruptedBeeContent {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("dimensional_mana/bee_content");

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, GeneratedMod.MOD_ID);

    /** 蜂巢方块实例：volatile + 同步创建，注册表、POI 状态集、世界生成三方共用同一个对象。 */
    private static volatile CorruptedNestBlock nestBlockInstance;

    /** 延迟创建蜂巢实例：只能在 RegisterEvent 解冻窗口及之后调用（详见类注释）。 */
    public static CorruptedNestBlock nestBlock() {
        CorruptedNestBlock local = nestBlockInstance;
        if (local == null) {
            synchronized (CorruptedBeeContent.class) {
                local = nestBlockInstance;
                if (local == null) {
                    local = new CorruptedNestBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE).strength(0.6F).sound(SoundType.WOOD)
                            // 蜜满时蜂房里的魔蚀蜜会发光（亮度 7），蜜被收走就自动熄灭。
                            .lightLevel(CorruptedNestBlock::honeyLight));
                    nestBlockInstance = local;
                }
            }
        }
        return local;
    }

    public static final DeferredBlock<Block> CORRUPTED_NEST =
            GeneratedMod.BLOCKS.register("corrupted_nest", CorruptedBeeContent::nestBlock);

    public static final DeferredItem<Item> CORRUPTED_NEST_ITEM =
            GeneratedMod.ITEMS.register("corrupted_nest", () -> new BlockItem(nestBlock(),
                    new Item.Properties().rarity(Rarity.RARE)));

    /**
     * 秽蚀蜂箱：玩家用 6 块木板 + 3 个魔蚀蜜脾在魔晶加工台做的「人造巢」。
     * 机制与蜂巢完全一致（都走 {@link CorruptedNestBlock} 的剪脾 / 装瓶逻辑），
     * 贴图沿用上一版蜂巢那套「人造木板」纹理。
     */
    public static final DeferredBlock<Block> CORRUPTED_HIVE =
            GeneratedMod.BLOCKS.register("corrupted_hive", () -> new CorruptedHiveBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                            .strength(0.6F).sound(SoundType.WOOD)
                            .lightLevel(CorruptedNestBlock::honeyLight)));

    public static final DeferredItem<Item> CORRUPTED_HIVE_ITEM =
            GeneratedMod.ITEMS.register("corrupted_hive", () -> new BlockItem(CORRUPTED_HIVE.get(),
                    new Item.Properties().rarity(Rarity.RARE)));

    /** 蜂巢的蜂 POI：蜜蜂靠它寻巢，容量与原版蜂箱一致。 */
    public static final DeferredHolder<PoiType, PoiType> CORRUPTED_NEST_POI =
            POI_TYPES.register("corrupted_nest", () -> {
                Set<BlockState> states = Set.copyOf(nestBlock().getStateDefinition().getPossibleStates());
                return new PoiType(states, 3, 1);
            });

    /** 蜂箱同样登记蜂 POI，否则搬进蜂箱的蜂找不到自己的家。 */
    public static final DeferredHolder<PoiType, PoiType> CORRUPTED_HIVE_POI =
            POI_TYPES.register("corrupted_hive", () -> {
                Set<BlockState> states = Set.copyOf(CORRUPTED_HIVE.get().getStateDefinition().getPossibleStates());
                return new PoiType(states, 3, 1);
            });

    /** 挂巢 feature：配置和放置都由 JSON 数据驱动，这里只出实现。 */
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> CORRUPTED_NEST_FEATURE =
            GeneratedMod.FEATURES.register("corrupted_nest", CorruptedNestFeature::new);

    /**
     * 魔蚀蜂蜜：16 堆叠，吃下回 4 饱食 + 8 饱和，给 15 秒饱和增益，
     * 代价是 3 秒 1 层魔蚀；喝完剩一只空玻璃瓶。
     */
    public static final DeferredItem<Item> CORRUPTED_HONEY =
            GeneratedMod.ITEMS.register("corrupted_honey", () -> new LoreItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
                            .food(new FoodProperties.Builder()
                                    .nutrition(4)
                                    // 1.21.1 仍是系数：饱和恢复 = 饥饿 × 系数 × 2，4×1.0×2 = 8 点。
                                    .saturationModifier(1.0F)
                                    .effect(() -> new MobEffectInstance(MobEffects.SATURATION, 300, 0), 1.0F)
                                    .effect(() -> new MobEffectInstance(GeneratedMod.MAGIC_EROSION, 60, 0), 1.0F)
                                    .usingConvertsTo(Items.GLASS_BOTTLE)
                                    .build()),
                    "item.dimensional_mana.corrupted_honey.tooltip"));

    /** 魔蚀蜜脾：只能剪满蜜的秽蚀蜂巢 / 蜂箱得到，不参与原版铜块涂蜡。 */
    public static final DeferredItem<Item> CORRUPTED_HONEYCOMB =
            GeneratedMod.ITEMS.register("corrupted_honeycomb", () -> new LoreItem(
                    new Item.Properties().stacksTo(64).rarity(Rarity.RARE),
                    "item.dimensional_mana.corrupted_honeycomb.tooltip"));

    /** 魔蚀蜂蜜块：2 倍黏性（走 0.2 / 跳 0.25 / 滑 0.025 全是原版一半）+ 接触时法术伤害独立减免。 */
    public static final DeferredBlock<Block> CORRUPTED_HONEY_BLOCK =
            GeneratedMod.BLOCKS.register("corrupted_honey_block", () -> new CorruptedHoneyBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).speedFactor(0.2F)
                            .jumpFactor(0.25F).noOcclusion().sound(SoundType.HONEY_BLOCK)));
    public static final DeferredItem<Item> CORRUPTED_HONEY_BLOCK_ITEM =
            GeneratedMod.ITEMS.register("corrupted_honey_block", () -> new BlockItem(CORRUPTED_HONEY_BLOCK.get(),
                    new Item.Properties().rarity(Rarity.RARE)));

    /** 魔蚀蜜脾块：装饰方块，也可给魔晶加工台充 800 魔素。 */
    public static final DeferredBlock<Block> CORRUPTED_HONEYCOMB_BLOCK =
            GeneratedMod.BLOCKS.register("corrupted_honeycomb_block", () -> new Block(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6F)
                            .sound(SoundType.CORAL_BLOCK)));
    public static final DeferredItem<Item> CORRUPTED_HONEYCOMB_BLOCK_ITEM =
            GeneratedMod.ITEMS.register("corrupted_honeycomb_block", () -> new BlockItem(CORRUPTED_HONEYCOMB_BLOCK.get(),
                    new Item.Properties().rarity(Rarity.RARE)));

    /** 魔法蜡烛：原版蜡烛机制 + ±0.15/根的魔素光环。 */
    public static final DeferredBlock<Block> MAGIC_CANDLE =
            GeneratedMod.BLOCKS.register("magic_candle", () -> new MagicCandleBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.1F)
                            .sound(SoundType.CANDLE).lightLevel(state ->
                                    state.getValue(net.minecraft.world.level.block.CandleBlock.LIT)
                                            ? state.getValue(net.minecraft.world.level.block.CandleBlock.CANDLES) * 3 : 0)));
    public static final DeferredItem<Item> MAGIC_CANDLE_ITEM =
            GeneratedMod.ITEMS.register("magic_candle", () -> new MagicCandleItem(MAGIC_CANDLE.get(),
                    new Item.Properties().stacksTo(64).rarity(Rarity.RARE)));

    private CorruptedBeeContent() {
    }

    /** 原版 {@code BeehiveBlock.hiveContainsBees} 是私有方法，这里等价重写：巢里此刻有没有蜂。 */
    public static boolean nestContainsBees(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive && !hive.isEmpty();
    }

    /** 在 GeneratedMod 的注册序列里调用（方块实体合法集合的修改必须在方块注册后、加载完成前）。 */
    public static void register() {
        GeneratedMod.MOD_BLOCKS.put("corrupted_nest", CORRUPTED_NEST);
        GeneratedMod.MOD_BLOCKS.put("corrupted_hive", CORRUPTED_HIVE);
        GeneratedMod.MOD_BLOCKS.put("corrupted_honey_block", CORRUPTED_HONEY_BLOCK);
        GeneratedMod.MOD_BLOCKS.put("corrupted_honeycomb_block", CORRUPTED_HONEYCOMB_BLOCK);
        GeneratedMod.MOD_BLOCKS.put("magic_candle", MAGIC_CANDLE);
        GeneratedMod.MOD_ITEMS.put("corrupted_nest", CORRUPTED_NEST_ITEM);
        GeneratedMod.MOD_ITEMS.put("corrupted_hive", CORRUPTED_HIVE_ITEM);
        GeneratedMod.MOD_ITEMS.put("corrupted_honey", CORRUPTED_HONEY);
        GeneratedMod.MOD_ITEMS.put("corrupted_honeycomb", CORRUPTED_HONEYCOMB);
        GeneratedMod.MOD_ITEMS.put("corrupted_honey_block", CORRUPTED_HONEY_BLOCK_ITEM);
        GeneratedMod.MOD_ITEMS.put("corrupted_honeycomb_block", CORRUPTED_HONEYCOMB_BLOCK_ITEM);
        GeneratedMod.MOD_ITEMS.put("magic_candle", MAGIC_CANDLE_ITEM);
        // 蜂巢衍生材料属于「次元物语：自然」；魔法蜡烛也放这里，和魔蚀蛛丝、蜂群材料相邻。
        GeneratedMod.NATURAL_ITEM_IDS.add("corrupted_nest");
        GeneratedMod.NATURAL_ITEM_IDS.add("corrupted_hive");
        GeneratedMod.NATURAL_ITEM_IDS.add("corrupted_honey");
        GeneratedMod.NATURAL_ITEM_IDS.add("corrupted_honeycomb");
        GeneratedMod.NATURAL_ITEM_IDS.add("corrupted_honey_block");
        GeneratedMod.NATURAL_ITEM_IDS.add("corrupted_honeycomb_block");
        GeneratedMod.NATURAL_ITEM_IDS.add("magic_candle");
    }

    /**
     * 把蜂巢塞进原版 {@code BlockEntityType.BEEHIVE} 的合法方块集合。
     *
     * <p>原版 builder 用 {@code ImmutableSet} 存这份集合，直接 add 会抛——所以读出来
     * 换成一个含蜂巢的可变集合再写回字段（private final 实例字段反射赋值是合法的）。
     * 不这么做的后果：区块重载时 {@code isCorrectTypeForBlock} 判定失败，
     * 蜂巢的方块实体连同里面四只蜂被原版丢弃。</p>
     */
    @SuppressWarnings("unchecked")
    public static void extendBeehiveBlockEntityType() {
        try {
            Field field = BlockEntityType.class.getDeclaredField("validBlocks");
            field.setAccessible(true);
            Set<Block> current = (Set<Block>) field.get(BlockEntityType.BEEHIVE);
            Set<Block> extended = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            extended.addAll(current);
            extended.add(nestBlock());
            extended.add(CORRUPTED_HIVE.get());
            field.set(BlockEntityType.BEEHIVE, extended);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("注入蜂巢到 BEEHIVE 方块实体合法集合失败，蜂巢重载会丢蜂", e);
        }
    }
}
