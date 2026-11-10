package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 卢梅莉亚专用方块：比其它四种多一套「贴墙」形态，而且地上那株可以往上叠成 1～3 格。
 *
 * <p>同一株草药有两种长相和状态：站在地上时是十字模型（{@code wall_mounted=false}），
 * 贴着岩壁长的时候换成从墙面<b>伸出来</b>的立体模型，并记下它朝向哪一面（{@code facing}）。</p>
 *
 * <p><b>叠格与分段（r276）</b>：地上的卢梅莉亚能往上长 1～3 格。以前每一格都画完整的一株，
 * 叠起来就是两三株重合的重影。现在方块记两个状态——{@link #ABOVE}（头顶上还有没有一段）、
 * {@link #BELOW}（脚底下还有没有一段）——模型据此换成 <b>底段 / 中段 / 顶段</b> 三张图，
 * 叠起来是一株连贯的高草，不再重复。</p>
 *
 * <p><b>贴墙那一株会爬（r281）</b>：贴墙形态不再只长一格，而会顺着岩壁一段段往上爬，
 * 最多爬四段，一直爬到崖顶（背面不再有实心墙）为止；顶段的模型里还有一截翻过崖沿的茎，
 * 顺着崖顶铺到地栽那株的根部 —— 这样「墙上的」和「崖顶的」看起来就是同一株，而不是断成两截。</p>
 *
 * <p>两个分段状态是「由邻居算出来」的：放置、长高、拆掉邻居时都会重算一次
 * （{@link #updateShape} / {@link #randomTick}），旧存档里已经叠起来但状态还是 solo 的植株，
 * 也会在随机刻里自己修正过来，不用重新生成世界。</p>
 */
public class LumeliaPlantBlock extends ManaPlantBlock {
    /** 是否为贴墙形态。 */
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    /** 贴墙时朝向哪一面，也就是叶片伸出来的方向。 */
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    /** 头顶上还有一段卢梅莉亚（自己不是最上面那一格）。 */
    public static final BooleanProperty ABOVE = BooleanProperty.create("above");
    /** 脚底下还有一段卢梅莉亚（自己不是最下面那一格）。 */
    public static final BooleanProperty BELOW = BooleanProperty.create("below");

    /** 一株卢梅莉亚最多叠三格（底 → 中 → 顶）。 */
    private static final int MAX_STACK = 3;
    /** 贴墙的一株最多爬四段（崖壁矮的地方在崖顶自己就停了，到不了这个上限）。 */
    private static final int MAX_WALL_STACK = 4;
    /** 每次随机刻往上长一格的概率；魔素浓度 15～19 的「100%」档就是它。 */
    private static final float GROW_CHANCE = 0.35F;
    /** 贴墙形态顺着岩壁往上爬的概率。 */
    private static final float CLIMB_CHANCE = 0.5F;
    /** 魔素浓度低于这个值，卢梅莉亚完全不生长。 */
    private static final double MIN_GROWTH_MANA = 12.0;
    /** 一轮随机刻最多长两格（浓度 ≥19 的「150%」档）。 */
    private static final int MAX_GROWTH_PER_TICK = 2;

    private static final VoxelShape GROUND_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);
    // 贴墙形态是从墙面往外伸出约 8 格的立体植株，判定框跟着加深（原来只有 3 格，薄得像张纸）。
    private static final VoxelShape NORTH_SHAPE = Block.box(2.0, 0.0, 8.0, 14.0, 15.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(2.0, 0.0, 0.0, 14.0, 15.0, 8.0);
    private static final VoxelShape WEST_SHAPE = Block.box(8.0, 0.0, 2.0, 16.0, 15.0, 14.0);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 0.0, 2.0, 8.0, 15.0, 14.0);

    public LumeliaPlantBlock(ManaPlants.Kind kind, BlockBehaviour.Properties properties) {
        super(kind, properties);
        registerDefaultState(defaultBlockState()
                .setValue(WALL_MOUNTED, false).setValue(FACING, Direction.NORTH)
                .setValue(ABOVE, false).setValue(BELOW, false));
    }

    public LumeliaPlantBlock(BlockBehaviour.Properties properties) {
        this(ManaPlants.Kind.LUMELIA, properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return simpleCodec(LumeliaPlantBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WALL_MOUNTED, FACING, ABOVE, BELOW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(WALL_MOUNTED)) return GROUND_SHAPE;
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> GROUND_SHAPE;
        };
    }

    /** 贴墙时看背后的墙，站在地上时照旧看脚下的土。 */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(WALL_MOUNTED)) {
            Direction facing = state.getValue(FACING);
            BlockPos support = pos.relative(facing.getOpposite());
            return level.getBlockState(support).isFaceSturdy(level, support, facing);
        }
        return super.canSurvive(state, level, pos);
    }

    /**
     * 邻居变了先检查自己还站不站得住：土被挖了、背后的墙没了、下面那一段被打碎，
     * 这一节就跟着掉（掉一个物品再变空气，和玩家亲手拆一个效果一致）。
     *
     * <p>r286 修复：以前的写法在垂直邻居变化时先重算「底/中/顶」分段状态、
     * 一算出新状态就 <b>直接 return</b>，父类那套「撑不住就变空气」的判定根本没机会跑——
     * 挖掉最底下的茎，上面几节的状态被改成「不是中段」之后继续悬在半空。
     * 现在存活判定排在最前，并且补上了掉落。</p>
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            if (level instanceof ServerLevel server) {
                popResource(server, pos, new ItemStack(state.getBlock()));
            }
            return Blocks.AIR.defaultBlockState();
        }
        if (direction.getAxis().isVertical()) {
            BlockState fixed = stackState(state, level, pos);
            if (fixed != state) return fixed;
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * 玩家碰掉任何一节，整株卢梅莉亚一起掉落：连着它上方、下方的所有段全部拆散，
     * 每一节各掉各的物品（创造模式不掉落）。
     *
     * <p>「连着」按竖直方向一路数过去就是同一株——贴墙的串到顶变成地栽的帽段也算。
     * 从最顶上一节往下拆，保证每一节都走一次完整的「粒子 + 掉落」，
     * 而不是靠上面的节悬空后自己超时消失。</p>
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player != null) {
            java.util.List<BlockPos> chain = new java.util.ArrayList<>();
            BlockPos cursor = pos.above();
            while (isLumelia(level.getBlockState(cursor))) {
                chain.add(cursor.immutable());
                cursor = cursor.above();
            }
            cursor = pos.below();
            while (isLumelia(level.getBlockState(cursor))) {
                chain.add(cursor.immutable());
                cursor = cursor.below();
            }
            // 从最高的一节开始往下收，越拆越安静，不会出现「下面先没、上面靠悬空判定掉」。
            chain.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
            boolean drop = !player.isCreative();
            BlockPos.MutableBlockPos walk = new BlockPos.MutableBlockPos();
            for (BlockPos segment : chain) {
                walk.set(segment);
                BlockState current = level.getBlockState(walk);
                if (!isLumelia(current)) continue; // 前面拆上层的连锁里已经掉过了
                level.setBlock(walk, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE);
                level.levelEvent(player, 2001, walk, Block.getId(current));
                if (drop) popResource(level, walk, new ItemStack(current.getBlock()));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 竖直方向上连着的卢梅莉亚都算同一株（不管地栽还是贴墙那一段）。 */
    private boolean isLumelia(BlockState state) {
        return state.is(this);
    }

    /** 玩家往岩壁上放：脚下放不了、背后有实心墙，就自动贴墙；放上去就算好分段。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState placed = defaultBlockState();
        if (!ManaPlants.canPlantAt(level, pos)) {
            Direction wall = ManaPlants.wallFacing(level, pos);
            if (wall != null) placed = placed.setValue(WALL_MOUNTED, true).setValue(FACING, wall);
        }
        return stackState(placed, level, pos);
    }

    /** 往别人头上放一株时，下面那一段要立刻知道自己上面多了一格。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
                               ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        if (isSegment(below, state)) {
            BlockState fixed = stackState(below, level, belowPos);
            if (fixed != below) level.setBlock(belowPos, fixed, Block.UPDATE_ALL);
        }
    }

    /**
     * 随机刻：先把分段状态修正一遍（旧存档里的叠格植株在这里自愈），再让最上面那一格往上长。
     * 贴墙形态不往天上长，只顺着岩壁往上爬。
     *
     * <p><b>长多快由魔素浓度决定</b>（{@link #growthMultiplier}）：浓度不到 12 完全不长，
     * 12～19 之间 25% / 50% / 75% / 100%，19 以上 150%（有几率一轮长两格）。
     * 判定只挂在随机刻上，绝不每 tick 去查环境浓度。</p>
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // r308：随机刻里要反复改邻居，改的过程会触发连锁更新，自己可能已经被拆成空气了。
        // 所以每次动手之前都重新从世界里读一遍，读到的不是卢梅莉亚就直接收手，
        // 绝不拿空气去读 wall_mounted（那会抛 IllegalArgumentException 把整世界 tick 打断）。
        state = level.getBlockState(pos);
        if (!state.is(this)) return;
        BlockState fixed = stackState(state, level, pos);
        if (fixed != state) {
            level.setBlock(pos, fixed, Block.UPDATE_ALL);
            state = level.getBlockState(pos);
            if (!state.is(this)) return;
        }
        if (state.getValue(WALL_MOUNTED)) {
            climbWall(state, level, pos, random);
            return;
        }
        if (state.getValue(ABOVE)) return;
        // 生长速度完全由脚下的魔素浓度决定（见 growthMultiplier）。这里只在随机刻里查一次浓度：
        // 原版每格平均一分多钟才来一次随机刻，绝不会像每 tick 判定那样把服务器压垮。
        float multiplier = growthMultiplier(ManaConcentration.at(level, pos));
        if (multiplier <= 0.0F) return;
        if (random.nextFloat() >= GROW_CHANCE * Math.min(1.0F, multiplier)) return;
        // 150%：在「这一轮长一节」之外再掷一次 50% 的额外生长，成功就一次长两格。
        int segments = multiplier > 1.0F && random.nextFloat() < multiplier - 1.0F
                ? MAX_GROWTH_PER_TICK : 1;
        BlockPos top = pos;
        for (int i = 0; i < segments; i++) {
            if (!growOneSegment(level, top)) break;
            top = top.above();
        }
    }

    /**
     * 这一格卢梅莉亚的生长速度倍率，完全按魔素浓度分档：
     *
     * <ul>
     *   <li>浓度 &lt; 12：0，完全不生长；</li>
     *   <li>[12, 13)：25%；</li>
     *   <li>[13, 14)：50%；</li>
     *   <li>[14, 15)：75%；</li>
     *   <li>[15, 19)：100%，也就是 {@link #GROW_CHANCE} 本身；</li>
     *   <li>≥ 19：150%，即 100% 再有 50% 的额外生长，表现为有几率一次长两格。</li>
     * </ul>
     *
     * <p>只会被随机刻调用（每格平均一分多钟一次）。环境浓度要算噪声、洞穴、蜡烛光环，
     * 每 tick 查一次会直接把服务器拖垮——这条规则的全部意义就是「用低频判定换密度」。</p>
     */
    private static float growthMultiplier(double concentration) {
        if (concentration < MIN_GROWTH_MANA) return 0.0F;
        if (concentration < 13.0) return 0.25F;
        if (concentration < 14.0) return 0.50F;
        if (concentration < 15.0) return 0.75F;
        if (concentration < 19.0) return 1.00F;
        return 1.50F;
    }

    /** 在 top（当前最上面那一格）之上再长一节；已经叠满或者头上被占了就返回 false。 */
    private boolean growOneSegment(ServerLevel level, BlockPos top) {
        int height = 1;
        while (height < MAX_STACK && level.getBlockState(top.below(height)).is(this)) height++;
        if (height >= MAX_STACK) return false;
        BlockPos above = top.above();
        if (!level.getBlockState(above).canBeReplaced()) return false;
        // 新的一段自带「下面还有」；下面那一段的「上面还有」由 updateShape 自动补上。
        level.setBlock(above, defaultBlockState().setValue(BELOW, true), Block.UPDATE_ALL);
        return true;
    }

    /**
     * 贴墙那一株顺着岩壁往上爬一段。
     *
     * <p>只要这一格上面还是空的、背后还是实心墙，就再长一段。
     * r286：爬到墙顶（背后不再是实心墙）不再就此停住——植株<b>翻过崖沿</b>，
     * 在墙顶最上方那一格换成地栽形态落地生根，之后照普通卢梅莉亚的随机刻继续往上叠。
     * 拆掉贴墙的任意一段，整株（包括墙顶这部分）会一起掉落。
     * 旧存档里已经卡在半腰的贴着墙的植株，也会在这里一格一格自己爬上去，不用重开世界。</p>
     */
    private void climbWall(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.is(this)) return;                       // 已经不是卢梅莉亚了（被连锁更新拆掉）
        if (state.getValue(ABOVE)) return;                 // 上面还有一段，交给最顶上那格去爬
        // 爬墙速度同样吃魔素浓度，查法和地上那株一样：只在随机刻里查一次。
        float multiplier = growthMultiplier(ManaConcentration.at(level, pos));
        if (multiplier <= 0.0F) return;
        if (random.nextFloat() > CLIMB_CHANCE * Math.min(1.0F, multiplier)) return;
        // 150%：多出来的 50% 让这一轮有几率一次爬两段。
        int segments = multiplier > 1.0F && random.nextFloat() < multiplier - 1.0F
                ? MAX_GROWTH_PER_TICK : 1;
        BlockPos cursor = pos;
        for (int i = 0; i < segments; i++) {
            if (!climbOneSegment(level, cursor)) return;
            cursor = cursor.above();
        }
    }

    /** 把 cursor 这一格（当前最上面一段贴墙株）顺着岩壁往上再爬一段，成功返回 true。 */
    private boolean climbOneSegment(ServerLevel level, BlockPos cursor) {
        BlockState state = level.getBlockState(cursor);
        if (!state.is(this) || !state.getValue(WALL_MOUNTED) || state.getValue(ABOVE)) return false;
        int height = 1;
        while (height < MAX_WALL_STACK && isSegment(level.getBlockState(cursor.below(height)), state)) height++;
        if (height >= MAX_WALL_STACK) return false;        // 爬满了，剩下的高度交给崖顶那株
        BlockPos above = cursor.above();
        if (!level.getBlockState(above).canBeReplaced() || !level.getFluidState(above).isEmpty()) return false;
        Direction facing = state.getValue(FACING);
        BlockPos wall = above.relative(facing.getOpposite());
        // 背后还是墙：继续贴着爬一段；到墙顶了就在崖沿上换栽成地栽形态（脚下踩着的正是
        // 最后一段贴墙株，站得住）。它的上下分段照常由 stackState 标好，长出来就是一株
        // 完整的「崖顶草」。
        BlockState grown = level.getBlockState(wall).isFaceSturdy(level, wall, facing)
                ? defaultBlockState().setValue(WALL_MOUNTED, true).setValue(FACING, facing)
                : defaultBlockState();
        level.setBlock(above, stackState(grown, level, above), Block.UPDATE_ALL);
        // r308（崩溃修复）：上面那一段落位会触发邻居更新，脚下这一格有可能当场被判定
        // 「撑不住」而变回空气。以前这里直接把 level.getBlockState(pos) 读出来喂给
        // stackState——读到空气时，stackState 里“空气 == 空气”照样通过方块比较，
        // 接着 getValue(WALL_MOUNTED) 就抛 IllegalArgumentException，整世界 tick 崩掉。
        // 现在先确认这一格还是卢梅莉亚，不是就直接收手。
        BlockState self = level.getBlockState(cursor);
        if (!self.is(this)) return true;
        BlockState fixed = stackState(self, level, cursor);
        if (fixed != self) level.setBlock(cursor, fixed, Block.UPDATE_ALL);
        return true;
    }

    /** 按上下邻居重新标出这一格是「底 / 中 / 顶」里的哪一种。 */
    public static BlockState stackState(BlockState state, LevelReader level, BlockPos pos) {
        // 传进来的不是卢梅莉亚（最常见的是空气）就没有 above/below 这两个属性，原样返回，
        // 免得在空气上 setValue 抛 IllegalArgumentException。世界生成那边也会走这个方法。
        if (!state.hasProperty(ABOVE) || !state.hasProperty(BELOW)) return state;
        boolean above = isSegment(level.getBlockState(pos.above()), state);
        boolean below = isSegment(level.getBlockState(pos.below()), state);
        return state.setValue(ABOVE, above).setValue(BELOW, below);
    }

    /** 同一种形态（都在地上，或者都贴墙）的卢梅莉亚才算同一株的上下两段。 */
    private static boolean isSegment(BlockState candidate, BlockState self) {
        // 先查属性有没有注册，再取值。
        // r308（崩溃修复）：以前只比 getBlock()，两格都是空气时“空气 == 空气”成立，
        // 接着 candidate.getValue(WALL_MOUNTED) 就在空气上抛 IllegalArgumentException。
        if (!candidate.hasProperty(WALL_MOUNTED) || !self.hasProperty(WALL_MOUNTED)) return false;
        return candidate.getBlock() == self.getBlock()
                && candidate.getValue(WALL_MOUNTED) == self.getValue(WALL_MOUNTED);
    }
}
