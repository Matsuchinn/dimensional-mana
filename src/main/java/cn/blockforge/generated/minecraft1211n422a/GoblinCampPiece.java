package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cn.blockforge.generated.minecraft1211n422a.GoblinCampLayout.*;

/** 完整营地：预规划路网及互不重叠的地块，地面微下沉，建筑各自找平。 */
public final class GoblinCampPiece extends StructurePiece {
    public static final int GRID = 9;
    private final int[] heights;
    private final GoblinCampLayout layout;
    private final boolean legacy;
    private static final ResourceKey<LootTable> CHIEF_LOOT = loot("goblin_chief");
    private static final ResourceKey<LootTable> TENT_LOOT = loot("goblin_tent");
    private static final ResourceKey<LootTable> SHAMAN_LOOT = loot("goblin_shaman");
    private static final ResourceKey<LootTable> SUPPLY_LOOT = loot("goblin_supplies");
    private static final ResourceKey<LootTable> LARDER_LOOT = loot("goblin_larder");
    // ---------------------------------------------------------------- 地下巢穴尺寸
    /** 巢穴地板所在的局部高度（以包围盒下沿为 0）。 */
    private static final int LAIR_FLOOR = 2;
    /** 巢穴顶板所在的局部高度；地板到顶板之间是 6 格净空。 */
    private static final int LAIR_CEIL = 9;
    /** 地形适配在包围盒下沿向上约 12 格内会挖出空腔；顶板上再压一层夯土把空隙封死。 */
    private static final int LAIR_SEAL = 10;
    /** 牢笼地板的局部高度，实体直接站在这一层。 */
    private static final int LAIR_STAND = LAIR_FLOOR + 1;

    public GoblinCampPiece(BlockPos origin, int[] heights, Size size, long seed) {
        super(GeneratedMod.GOBLIN_CAMP_PIECE.get(), 0, bounds(origin, heights, size));
        // 不使用 SOUTH 的隐式镜像；所有局部坐标、宝箱和居民使用相同的正向坐标。
        setOrientation(null);
        this.heights = heights.clone();
        this.layout = new GoblinCampLayout(size, seed);
        this.legacy = false;
    }

    public GoblinCampPiece(CompoundTag tag) {
        super(GeneratedMod.GOBLIN_CAMP_PIECE.get(), tag);
        legacy = !tag.contains("camp_layout_version");
        Size size = Size.values()[Mth.clamp(tag.getInt("camp_size"), 0, Size.values().length - 1)];
        heights = tag.getIntArray("heights");
        layout = new GoblinCampLayout(size, tag.getLong("layout_seed"));
        setOrientation(null);
    }

    private static ResourceKey<LootTable> loot(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "chests/" + name));
    }
    private static BoundingBox bounds(BlockPos origin, int[] heights, Size size) {
        int min = java.util.Arrays.stream(heights).min().orElse(origin.getY());
        int max = java.util.Arrays.stream(heights).max().orElse(origin.getY());
        return new BoundingBox(origin.getX(), min - 12, origin.getZ(),
                origin.getX() + size.width - 1, max + 20, origin.getZ() + size.depth - 1);
    }
    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putIntArray("heights", heights);
        if (!legacy) {
            tag.putInt("camp_layout_version", 2);
            tag.putInt("camp_size", layout.size.ordinal());
            tag.putLong("layout_seed", layout.seed);
        }
    }

    public boolean isLegacy() { return legacy; }
    public int campWidth() { return legacy ? 41 : layout.size.width; }
    public int campDepth() { return legacy ? 41 : layout.size.depth; }
    public int sizeTier() { return legacy ? 0 : layout.size.ordinal(); }
    private BlockPos world(int x, int y, int z) {
        return new BlockPos(boundingBox.minX() + x, boundingBox.minY() + y, boundingBox.minZ() + z);
    }
    public BlockPos campCenter() {
        if (legacy) return world(20, oldGround(20, 20), 20);
        return world(layout.cx, baseY(layout.cx, layout.cz), layout.cz);
    }
    public BlockPos chiefSpawnPos() {
        if (legacy) {
            int base = 7;
            for (int x = 13; x <= 27; x++) for (int z = 8; z <= 18; z++) base = Math.max(base, oldGround(x, z));
            return world(20, base + 1, 15);
        }
        Plot p = layout.chief();
        return world(p.x() + 2, plotBase(p) + 1, p.z() + 1);
    }
    /** 居民站在房内或路面，不再使用会把居民送到屋顶的高度图。 */
    public List<BlockPos> residentPositions() {
        List<BlockPos> result = new ArrayList<>();
        if (legacy) return result;
        for (Plot p : layout.plots) {
            if (p.kind() == Kind.RUBBISH || p.kind() == Kind.TIMBER || p.kind() == Kind.CHIEF) continue;
            int[][] standing = switch (p.kind()) {
                case HUT -> new int[][]{{2, 1}, {2, 2}};
                case SHAMAN -> new int[][]{{-1, 1}, {1, 1}};
                case FIRE -> new int[][]{{-2, 2}, {2, 2}};
                default -> new int[][]{{-1, 1}, {1, 1}};
            };
            for (int[] point : standing) result.add(world(p.wx(point[0], point[1]), plotBase(p) + 1,
                    p.wz(point[0], point[1])));
        }
        for (Road r : layout.roads) {
            int x = (r.x1() + r.x2()) / 2, z = (r.z1() + r.z2()) / 2;
            result.add(world(x, baseY(x, z) + 1, z));
        }
        return result;
    }
    public List<BlockPos> rubbishPositions() {
        List<BlockPos> result = new ArrayList<>();
        if (legacy) return result;
        Plot p = layout.rubbish();
        int y = plotBase(p) - 1;
        for (int u = -2; u <= 2; u++) result.add(world(p.x() + u, y, p.z()));
        if (p.halfD() > 1) for (int u : new int[]{-1, 1}) result.add(world(p.x() + u, y, p.z() + 1));
        return result;
    }
    /** 聚集棚正中的站位：每天 0 点哥布林来这里碰头。 */
    public BlockPos gatheringPos() {
        if (legacy) return campCenter().above(2);
        for (Plot p : layout.plots)
            if (p.kind() == Kind.GATHERING) return world(p.x(), plotBase(p) + 1, p.z());
        return campCenter().above(2);
    }
    /** 世界坐标是否落在巢穴（含墙体）范围内；预览脚本用它裁剪地下切片。 */
    public boolean isLairColumn(BlockPos pos) {
        if (legacy) return false;
        List<Room> rooms = lairPlan().rooms();
        return nearLair(rooms, pos.getX() - boundingBox.minX(), pos.getZ() - boundingBox.minZ());
    }
    /** 地下牢笼里每个笼子的落脚点（世界坐标）；旧存档营地没有巢穴，返回空表。 */
    public List<BlockPos> cagePositions() {
        List<BlockPos> result = new ArrayList<>();
        if (legacy) return result;
        LairPlan plan = lairPlan();
        int cx = layout.cx, cz = layout.cz, cells = plan.cells();
        int first = cx - cells * 2, z0 = cz - 2;
        for (int i = 0; i < cells; i++) result.add(world(first + i * 4 + 2, LAIR_STAND, z0 + 2));
        return result;
    }

    private int oldGround(int x, int z) {
        if (heights.length != 25) return 7;
        return interpolated(x, z, 41, 41, 5) - boundingBox.minY();
    }
    private int interpolated(int x, int z, int width, int depth, int grid) {
        if (heights.length != grid * grid) return boundingBox.minY() + 12;
        float gx = Mth.clamp(x, 0, width - 1) * (grid - 1F) / (width - 1F);
        float gz = Mth.clamp(z, 0, depth - 1) * (grid - 1F) / (depth - 1F);
        int ix = Math.min((int)gx, grid - 2), iz = Math.min((int)gz, grid - 2);
        float tx = gx - ix, tz = gz - iz;
        float height = (1 - tx) * (1 - tz) * heights[iz * grid + ix]
                + tx * (1 - tz) * heights[iz * grid + ix + 1]
                + (1 - tx) * tz * heights[(iz + 1) * grid + ix]
                + tx * tz * heights[(iz + 1) * grid + ix + 1];
        return grid == 5 ? (int)Math.ceil(height) : Math.round(height);
    }
    private int baseY(int x, int z) {
        int edge = Math.min(Math.min(x, layout.size.width - 1 - x), Math.min(z, layout.size.depth - 1 - z));
        // 边缘不挖，院内下沉一格；仍保留缓坡起伏，不铺悬空大平台。
        return interpolated(x, z, layout.size.width, layout.size.depth, GRID) - boundingBox.minY()
                - (edge >= 4 ? 1 : 0);
    }
    private int plotBase(Plot p) {
        // 房屋用中心及四角的中位高度，不以最高角把整间房架到空中。
        int[] ys = {baseY(p.x(), p.z()), baseY(p.wx(-p.halfW(), -p.halfD()), p.wz(-p.halfW(), -p.halfD())),
                baseY(p.wx(p.halfW(), -p.halfD()), p.wz(p.halfW(), -p.halfD())),
                baseY(p.wx(-p.halfW(), p.halfD()), p.wz(-p.halfW(), p.halfD())),
                baseY(p.wx(p.halfW(), p.halfD()), p.wz(p.halfW(), p.halfD()))};
        java.util.Arrays.sort(ys);
        return ys[2];
    }
    private int hash(int x, int z, int salt, int bound) {
        long v = layout.seed ^ x * 341873128712L ^ z * 132897987521L ^ salt * 42595009L;
        v ^= v >>> 33; v *= 0xff51afd7ed558ccdL; v ^= v >>> 33;
        return (int)Math.floorMod(v, bound);
    }
    private void put(WorldGenLevel level, BoundingBox box, BlockState state, int x, int y, int z) {
        BlockPos pos = world(x, y, z);
        if (boundingBox.isInside(pos) && box.isInside(pos)) level.setBlock(pos, state, 2);
    }
    private void put(WorldGenLevel level, BoundingBox box, Block block, int x, int y, int z) {
        put(level, box, block.defaultBlockState(), x, y, z);
    }
    private static Rotation rotation(Plot p) {
        return switch (p.facing()) {
            case 1 -> Rotation.CLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90; default -> Rotation.NONE;
        };
    }
    private void at(WorldGenLevel level, BoundingBox box, Plot p, int y, BlockState state, int u, int v) {
        put(level, box, state.rotate(rotation(p)), p.wx(u, v), y, p.wz(u, v));
    }
    private void at(WorldGenLevel level, BoundingBox box, Plot p, int y, Block block, int u, int v) {
        at(level, box, p, y, block.defaultBlockState(), u, v);
    }
    private BlockState fence(boolean eastWest) {
        return Blocks.SPRUCE_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.EAST, eastWest).setValue(BlockStateProperties.WEST, eastWest)
                .setValue(BlockStateProperties.NORTH, !eastWest).setValue(BlockStateProperties.SOUTH, !eastWest);
    }
    private BlockState log(Direction.Axis axis) {
        return Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }
    private BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
    }
    private void support(WorldGenLevel level, BoundingBox box, int x, int y, int z, Block top) {
        int natural = baseY(x, z);
        for (int yy = Math.max(0, Math.min(y, natural) - 6); yy < y; yy++)
            put(level, box, yy >= y - 2 ? Blocks.DIRT : Blocks.STONE, x, yy, z);
        put(level, box, top, x, y, z);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        // 老存档已落地的营地不扩建覆盖；新版仅用于新生成的营地。
        if (legacy) return;
        terrain(level, box);
        roads(level, box);
        for (Plot p : layout.plots) {
            switch (p.kind()) {
                case CHIEF, HUT -> lodge(level, box, p);
                case SHAMAN -> shaman(level, box, p);
                case GATHERING -> gathering(level, box, p);
                case FIRE -> firepit(level, box, p);
                case RUBBISH -> rubbish(level, box, p);
                case TIMBER -> timber(level, box, p);
            }
        }
        palisade(level, box);
        // 巢穴最后开挖：地形适配在包围盒下沿留下的空腔正好当地下室。
        lair(level, box);
    }
    private void terrain(WorldGenLevel level, BoundingBox box) {
        int x0 = Math.max(0, box.minX() - boundingBox.minX()), x1 = Math.min(layout.size.width - 1, box.maxX() - boundingBox.minX());
        int z0 = Math.max(0, box.minZ() - boundingBox.minZ()), z1 = Math.min(layout.size.depth - 1, box.maxZ() - boundingBox.minZ());
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int y = baseY(x, z);
            for (int yy = y + 1; yy <= boundingBox.getYSpan() - 1; yy++) put(level, box, Blocks.AIR, x, yy, z);
            int roll = hash(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 1, 12);
            support(level, box, x, y, z, roll < 5 ? Blocks.COARSE_DIRT : roll < 8 ? Blocks.PODZOL : Blocks.GRASS_BLOCK);
        }
    }
    private void roads(WorldGenLevel level, BoundingBox box) {
        for (int x = 0; x < layout.size.width; x++) for (int z = 0; z < layout.size.depth; z++) {
            if (!layout.isRoad(x, z, 1)) continue;
            put(level, box, hash(x, z, 2, 6) == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH, x, baseY(x, z), z);
        }
    }
    /** 整块地基有挡土边和填土；室内清到屋脊，不会把自然地形包进房里。 */
    private int foundation(WorldGenLevel level, BoundingBox box, Plot p, boolean wood) {
        int y = plotBase(p);
        for (int u = -p.halfW(); u <= p.halfW(); u++) for (int v = -p.halfD(); v <= p.halfD(); v++) {
            int x = p.wx(u, v), z = p.wz(u, v);
            support(level, box, x, y, z, wood ? Blocks.SPRUCE_PLANKS : Blocks.COARSE_DIRT);
            if (Math.abs(u) == p.halfW() || Math.abs(v) == p.halfD())
                put(level, box, hash(x, z, 5, 3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE, x, y - 1, z);
            for (int yy = y + 1; yy <= y + 14; yy++) put(level, box, Blocks.AIR, x, yy, z);
        }
        return y;
    }
    private Block cloth(Plot p, int u, int v) {
        int n = hash(Math.floorDiv(p.wx(u, v), 2), Math.floorDiv(p.wz(u, v), 2), 7, 11);
        if (n < 2) return Blocks.GRAY_WOOL;
        if (n == 2) return Blocks.LIGHT_GRAY_WOOL;
        return p.style() == 0 ? Blocks.BROWN_WOOL : p.style() == 1 ? Blocks.GRAY_WOOL : Blocks.GREEN_WOOL;
    }
    /** 双坡顶逐列封闭；山墙填到坡顶，房间不会出现上一版只有屋檐没有屋面的洞。 */
    private void lodge(WorldGenLevel level, BoundingBox box, Plot p) {
        int y = foundation(level, box, p, true), w = p.halfW(), d = p.halfD();
        boolean chief = p.kind() == Kind.CHIEF;
        for (int u = -w; u <= w; u++) for (int v = -d; v <= d; v++) {
            boolean side = Math.abs(u) == w, end = Math.abs(v) == d;
            if (!side && !end) continue;
            int top = 3 + w - Math.abs(u);
            boolean door = v == d && Math.abs(u) <= (chief ? 1 : 0);
            for (int h = 1; h <= top; h++) {
                if (door && h <= 3) continue;
                BlockState state = (side && (v == -d || v == d || v == 0))
                        ? log(Direction.Axis.Y) : h == 1 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                        : cloth(p, u, v).defaultBlockState();
                at(level, box, p, y + h, state, u, v);
            }
        }
        for (int u = -w - 1; u <= w + 1; u++) {
            int roofY = y + 3 + Math.max(0, w - Math.abs(u));
            for (int v = -d - 1; v <= d + 1; v++) {
                boolean rib = v == -d - 1 || v == d + 1 || (chief && v == 0);
                BlockState state = u == 0 ? log(Direction.Axis.Z) : rib ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                        : p.style() == 2 && !chief ? Blocks.SPRUCE_PLANKS.defaultBlockState() : cloth(p, u, v).defaultBlockState();
                at(level, box, p, roofY, state, u, v);
            }
        }
        // 门框：门洞两侧立原木柱、上方再补一根横梁，把布墙上的开口收成一圈木头。
        int jamb = chief ? 2 : 1;
        for (int u : new int[]{-jamb, jamb})
            for (int h = 1; h <= 3; h++) at(level, box, p, y + h, log(Direction.Axis.Y), u, d);
        for (int u = -jamb; u <= jamb; u++) at(level, box, p, y + 4, log(Direction.Axis.X), u, d);
        // 门廊支柱：往下多插一格到土里，坡地上的门廊不会只剩一根悬空的柱子。
        for (int u : new int[]{-2, 2}) {
            int x = p.wx(u, d + 1), z = p.wz(u, d + 1);
            int bottom = Math.max(1, Math.min(y, baseY(x, z)) - 1);
            for (int h = bottom; h <= y + 3; h++) at(level, box, p, h, log(Direction.Axis.Y), u, d + 1);
        }
        for (int u = -2; u <= 2; u++) at(level, box, p, y + 4, log(Direction.Axis.X), u, d + 1);
        // 灯笼从屋脊垂下来，正对门框正中的空位；不再压在右边那根支柱顶上。
        at(level, box, p, y + 2 + w, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 0, d + 1);
        interior(level, box, p, y, chief);
        approach(level, box, p, y);
    }
    private void interior(WorldGenLevel level, BoundingBox box, Plot p, int y, boolean chief) {
        int w = p.halfW(), d = p.halfD();
        // 羊毛窝、枕头和地毯，不使用只有一层地板的空壳。
        int bedU0 = -w + 1, bedU1 = -w + 2, bedV0 = -d + 1, bedV1 = -d + 3;
        for (int u = bedU0; u <= bedU1; u++) for (int v = bedV0; v <= bedV1; v++)
            at(level, box, p, y + 1, v == bedV0 ? Blocks.LIGHT_GRAY_WOOL : Blocks.BROWN_WOOL, u, v);
        // 地毯通道从门口铺到屋子中间，但要给羊毛床让路：窄小屋的床正好压在地毯宽度里，
        // 以前会沿墙吞掉床的一列（1~2 格），看上去像床被地毯顶掉了。
        for (int u = -1; u <= 1; u++) for (int v = -1; v <= d - 1; v++) {
            if (u >= bedU0 && u <= bedU1 && v >= bedV0 && v <= bedV1) continue;
            at(level, box, p, y + 1, chief ? Blocks.RED_CARPET : Blocks.GRAY_CARPET, u, v);
        }
        chest(level, box, p, y + 1, w - 1, -d + 1, chief ? CHIEF_LOOT : TENT_LOOT);
        at(level, box, p, y + 1, Blocks.CRAFTING_TABLE, w - 1, 0);
        at(level, box, p, y + 1, Blocks.BARREL, w - 1, -d + 2);
        at(level, box, p, y + 2, Blocks.LANTERN, w - 1, 0);
        if (chief) {
            at(level, box, p, y + 1, facing(Blocks.DARK_OAK_STAIRS, Direction.NORTH), 0, -d + 1);
            for (int u : new int[]{-1, 1}) {
                at(level, box, p, y + 1, Blocks.DARK_OAK_LOG, u, -d + 1);
                at(level, box, p, y + 2, Blocks.SPRUCE_SLAB, u, -d + 1);
            }
            for (int h = 2; h <= 3; h++) at(level, box, p, y + h, Blocks.BROWN_WOOL, 0, -d + 1);
            at(level, box, p, y + 4, Blocks.SKELETON_SKULL, 0, -d + 1);
        }
    }
    private void shaman(WorldGenLevel level, BoundingBox box, Plot p) {
        int y = foundation(level, box, p, true);
        // 分层收尖帐篷，骨色补片和褪色红布，与普通双坡小屋区分。
        for (int h = 1; h <= 9; h++) {
            int r = h <= 3 ? 4 : h <= 5 ? 3 : h <= 7 ? 2 : 1;
            int next = h == 3 || h == 5 || h == 7 || h == 9 ? r - 1 : r;
            for (int u = -r; u <= r; u++) for (int v = -r; v <= r; v++) {
                if (Math.max(Math.abs(u), Math.abs(v)) < next && h < 9) continue;
                if (v == 4 && Math.abs(u) <= 1 && h <= 3) continue;
                Block material = Math.abs(u) == r && Math.abs(v) == r ? Blocks.SPRUCE_LOG
                        : hash(p.wx(u, v), p.wz(u, v), h, 7) < 2 ? Blocks.BROWN_WOOL
                        : h % 3 == 0 ? Blocks.LIGHT_GRAY_WOOL : Blocks.RED_TERRACOTTA;
                at(level, box, p, y + h, material, u, v);
            }
        }
        for (int h = 10; h <= 11; h++) at(level, box, p, y + h, Blocks.SPRUCE_FENCE, 0, 0);
        at(level, box, p, y + 12, Blocks.SKELETON_SKULL, 0, 0);
        for (int u = -2; u <= 2; u++) for (int v = -2; v <= 2; v++) {
            if (v == 1 && Math.abs(u) == 1) continue;
            at(level, box, p, y + 1, Blocks.BROWN_CARPET, u, v);
        }
        at(level, box, p, y + 1, Blocks.BREWING_STAND, -3, -2);
        at(level, box, p, y + 1, Blocks.CAULDRON, -3, 0);
        at(level, box, p, y + 1, Blocks.MOSSY_COBBLESTONE, 0, -3);
        at(level, box, p, y + 2, Blocks.SKELETON_SKULL, 0, -3);
        at(level, box, p, y + 1, Blocks.BROWN_WOOL, -3, 2);
        at(level, box, p, y + 1, Blocks.LIGHT_GRAY_WOOL, -3, 3);
        at(level, box, p, y + 2, Blocks.LANTERN, 0, -2);
        at(level, box, p, y + 1, Blocks.SPRUCE_PLANKS, 0, -2);
        chest(level, box, p, y + 1, 3, -2, SHAMAN_LOOT);
        approach(level, box, p, y);
    }
    private void gathering(WorldGenLevel level, BoundingBox box, Plot p) {
        int y = foundation(level, box, p, false);
        for (int u : new int[]{-4, 4}) for (int v : new int[]{-4, 4})
            for (int h = 1; h <= 4; h++) at(level, box, p, y + h, log(Direction.Axis.Y), u, v);
        for (int u = -5; u <= 5; u++) for (int v = -5; v <= 5; v++) {
            int h = 5 + Math.max(0, (4 - Math.abs(u)) / 2);
            at(level, box, p, y + h, Math.abs(v) == 5 ? Blocks.SPRUCE_PLANKS : cloth(p, u, v), u, v);
        }
        for (int u = -4; u <= 4; u++) at(level, box, p, y + 4, log(Direction.Axis.X), u, -4);
        for (int v = -3; v <= 3; v++) at(level, box, p, y + 1, fence(false), -4, v);
        for (int u = -2; u <= 2; u++) {
            at(level, box, p, y + 1, Blocks.SPRUCE_FENCE, u, -1);
            at(level, box, p, y + 2, Blocks.SPRUCE_PRESSURE_PLATE, u, -1);
        }
        bench(level, box, p, y, 0, -3, 2);
        bench(level, box, p, y, 0, 2, 2);
        chest(level, box, p, y + 1, 3, -3, SUPPLY_LOOT);
        at(level, box, p, y + 1, Blocks.BARREL, 3, 0);
        at(level, box, p, y + 2, Blocks.LANTERN, 3, 0);
        approach(level, box, p, y);
    }
    private void bench(WorldGenLevel level, BoundingBox box, Plot p, int y, int u, int v, int length) {
        for (int i = -length; i <= length; i++) {
            at(level, box, p, y + 1, log(Direction.Axis.X), u + i, v);
            at(level, box, p, y + 2, Blocks.SPRUCE_SLAB, u + i, v);
        }
    }
    private void firepit(WorldGenLevel level, BoundingBox box, Plot p) {
        int y = foundation(level, box, p, false);
        for (int u = -1; u <= 1; u++) for (int v = -1; v <= 1; v++) {
            at(level, box, p, y, Blocks.COBBLESTONE, u, v);
            if (u != 0 || v != 0) at(level, box, p, y + 1, Blocks.COBBLESTONE_SLAB, u, v);
        }
        at(level, box, p, y + 1, Blocks.CAMPFIRE, 0, 0);
        for (int u : new int[]{-2, 2}) for (int h = 1; h <= 4; h++) at(level, box, p, y + h, Blocks.COBBLESTONE_WALL, u, 0);
        for (int u = -2; u <= 2; u++) at(level, box, p, y + 4, log(Direction.Axis.X), u, 0);
        at(level, box, p, y + 3, Blocks.CHAIN, 0, 0);
        at(level, box, p, y + 2, Blocks.CAULDRON, 0, 0);
        bench(level, box, p, y, 0, -3, 2);
        bench(level, box, p, y, 0, 3, 2);
        at(level, box, p, y + 1, Blocks.BARREL, -4, -1);
        at(level, box, p, y + 1, Blocks.HAY_BLOCK, 4, 2);
    }
    private void rubbish(WorldGenLevel level, BoundingBox box, Plot p) {
        int y = plotBase(p);
        for (int u = -3; u <= 3; u++) for (int v = -p.halfD(); v <= p.halfD(); v++) {
            boolean edge = Math.abs(u) == 3 || Math.abs(v) == p.halfD();
            int floor = edge ? y - 1 : y - 2;
            support(level, box, p.wx(u, v), floor, p.wz(u, v), edge ? Blocks.MOSSY_COBBLESTONE : Blocks.MUD);
            for (int h = floor + 1; h <= y + 3; h++) at(level, box, p, h, Blocks.AIR, u, v);
            if (edge && hash(p.wx(u, v), p.wz(u, v), 9, 3) == 0) at(level, box, p, y, Blocks.MOSS_CARPET, u, v);
        }
        at(level, box, p, y - 1, Blocks.BONE_BLOCK, -2, 0);
        at(level, box, p, y - 2, Blocks.ROOTED_DIRT, 2, 0);
        at(level, box, p, y - 1, Blocks.SHORT_GRASS, 2, 0);
    }
    private void timber(WorldGenLevel level, BoundingBox box, Plot p) {
        int y = foundation(level, box, p, false);
        for (int u = -3; u <= 3; u++) {
            for (int v : new int[]{-1, 1}) at(level, box, p, y + 1, log(Direction.Axis.X), u, v);
            if (Math.abs(u) < 3) at(level, box, p, y + 2, log(Direction.Axis.X), u, 0);
        }
        at(level, box, p, y + 1, Blocks.OAK_LOG, -3, 0);
    }
    private void chest(WorldGenLevel level, BoundingBox box, Plot p, int y, int u, int v, ResourceKey<LootTable> loot) {
        RandomSource random = RandomSource.create(layout.seed ^ p.wx(u, v) * 73856093L ^ p.wz(u, v) * 19349663L);
        createChest(level, box, random, world(p.wx(u, v), y, p.wz(u, v)), loot,
                facing(Blocks.CHEST, Direction.SOUTH).rotate(rotation(p)));
    }
    /**
     * 建筑入口的缓坡：门口两格与室内齐平，之后每格最多抬升或下降一格，一路跟着自然地面走。
     *
     * <p>以前是固定五格、每格可以差好几格，帐篷正好落在坡脚时，门口立刻要抬两格，2.3 格高的首领
     * 就卡在门廊里出不去。而且门廊上方有横梁、只剩三格净空，站在这底下起跳必然撞头，
     * 所以抬降从第三格才开始。最多走八格，落差全部拆成一格一阶，高出来的地形顺带挖掉。</p>
     */
    private void approach(WorldGenLevel level, BoundingBox box, Plot p, int floor) {
        final int steps = 8;
        int previous = floor;
        for (int step = 1; step <= steps; step++) {
            int v = p.halfD() + step;
            int cx0 = p.wx(0, v), cz0 = p.wz(0, v);
            if (cx0 < 2 || cx0 >= layout.size.width - 2 || cz0 < 2 || cz0 >= layout.size.depth - 2) break;
            int natural = baseY(cx0, cz0);
            // 门口两格必须和室内齐平：门廊横梁下面只有三格净空，起跳顶头就出不去了。
            int y = step <= 2 ? floor : previous + Mth.clamp(natural - previous, -1, 1);
            for (int u = -1; u <= 1; u++) {
                int x = p.wx(u, v), z = p.wz(u, v);
                if (x < 2 || x >= layout.size.width - 2 || z < 2 || z >= layout.size.depth - 2) continue;
                boolean other = false;
                for (Plot q : layout.plots) if (q != p && q.contains(x, z, 0)) other = true;
                if (other) continue;
                support(level, box, x, y, z, Blocks.COARSE_DIRT);
                // 门廊顶保留，脚下三格净空，路面最多一格一阶。
                for (int h = 1; h <= 3; h++) put(level, box, Blocks.AIR, x, y + h, z);
            }
            previous = y;
        }
    }
    /** 连续围墙：3~5 格原木高柱顶加栅栏，柱间 1~3 格低栅栏网，无随机漏口。 */
    private void palisade(WorldGenLevel level, BoundingBox box) {
        for (int side = 0; side < 4; side++) {
            boolean horizontal = side % 2 == 0;
            int end = (horizontal ? layout.size.width : layout.size.depth) - 2;
            int nextPost = 1;
            for (int i = 1; i <= end; i++) {
                int x = horizontal ? i : side == 1 ? layout.size.width - 2 : 1;
                int z = horizontal ? side == 0 ? 1 : layout.size.depth - 2 : i;
                boolean gate = false;
                for (Gate g : layout.gates) if (Math.abs(x - g.x()) + Math.abs(z - g.z()) <= 2) gate = true;
                boolean post = i == nextPost || i == end;
                if (post) nextPost = i + 2 + hash(x, z, side, 3);
                if (gate) continue;
                int y = baseY(x, z);
                if (post) {
                    int h = 3 + hash(x, z, 13, 3);
                    for (int n = 1; n <= h; n++) put(level, box, log(Direction.Axis.Y), x, y + n, z);
                    put(level, box, fence(horizontal), x, y + h + 1, z);
                } else {
                    put(level, box, Blocks.SPRUCE_PLANKS, x, y + 1, z);
                    put(level, box, fence(horizontal), x, y + 2, z);
                    put(level, box, fence(horizontal), x, y + 3, z);
                }
            }
        }
        for (Gate gate : layout.gates) gate(level, box, gate);
    }
    private void gate(WorldGenLevel level, BoundingBox box, Gate gate) {
        Plot p = new Plot(Kind.HUT, gate.x(), gate.z(), 3, 1, gate.facing(), 0);
        int y = baseY(gate.x(), gate.z());
        for (int u = -3; u <= 3; u++) {
            int x = p.wx(u, 0), z = p.wz(u, 0);
            support(level, box, x, y, z, Blocks.COARSE_DIRT);
            for (int h = 1; h <= 6; h++) at(level, box, p, y + h, Blocks.AIR, u, 0);
            if (Math.abs(u) == 3) {
                for (int h = 1; h <= 5; h++) at(level, box, p, y + h, log(Direction.Axis.Y), u, 0);
                at(level, box, p, y + 6, Blocks.SPRUCE_FENCE, u, 0);
                at(level, box, p, y + 7, Blocks.TORCH, u, 0);
            } else {
                at(level, box, p, y + 5, log(Direction.Axis.X), u, 0);
                at(level, box, p, y + 1, facing(Blocks.SPRUCE_FENCE_GATE, Direction.SOUTH)
                        .setValue(BlockStateProperties.OPEN, true), u, 0);
            }
        }
        at(level, box, p, y + 6, Blocks.BROWN_WOOL, -1, 0);
        at(level, box, p, y + 6, Blocks.RED_WOOL, 0, 0);
        at(level, box, p, y + 6, Blocks.BROWN_WOOL, 1, 0);
    }

    // ------------------------------------------------------------ 地下巢穴
    /** 一个长方形房间（局部坐标）：大厅、地道或小屋底下的储藏间。 */
    private record Room(int x, int z, int halfW, int halfD) {
        boolean contains(int px, int pz) {
            return Math.abs(px - x) <= halfW && Math.abs(pz - z) <= halfD;
        }
        boolean near(int px, int pz) {
            return Math.abs(px - x) <= halfW + 1 && Math.abs(pz - z) <= halfD + 1;
        }
    }
    /** 巢穴规划：房间集合、竖井列（x、z、井口活板门的高度）与牢笼数量。 */
    private record LairPlan(List<Room> rooms, List<int[]> shafts, int cells) {}

    private int lairHalfW() {
        return switch (layout.size) {
            case SMALL -> 7;
            case MEDIUM -> 9;
            case LARGE -> 11;
        };
    }
    private int lairHalfD() {
        return switch (layout.size) {
            case SMALL -> 4;
            case MEDIUM -> 5;
            case LARGE -> 6;
        };
    }
    /**
     * 规划地下巢穴：领主营帐底下是大厅，再挖 L 形地道通到最近的一两间小屋，
     * 每间房子地板上开一个云杉活板门，梯子竖井直通大厅。
     */
    private LairPlan lairPlan() {
        int cx = layout.cx, cz = layout.cz;
        int hw = lairHalfW(), hd = lairHalfD();
        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room(cx, cz, hw, hd));
        List<int[]> shafts = new ArrayList<>();
        if (legacy) return new LairPlan(rooms, shafts, 2);
        // 领主营帐东南角：避开王座、地毯、宝箱和门廊。
        shafts.add(new int[]{cx + 4, cz + 3, plotBase(layout.chief())});
        List<Plot> huts = new ArrayList<>();
        for (Plot p : layout.plots) if (p.kind() == Kind.HUT) huts.add(p);
        huts.sort(Comparator.comparingInt((Plot p) -> Math.abs(p.x() - cx) + Math.abs(p.z() - cz))
                .thenComparingInt(p -> hash(p.x(), p.z(), 31, 1000)));
        int limit = layout.size == Size.SMALL ? 1 : 2;
        int built = 0;
        for (Plot p : huts) {
            if (built >= limit) break;
            int tx = p.wx(p.halfW() - 1, p.halfD() - 1), tz = p.wz(p.halfW() - 1, p.halfD() - 1);
            // 太远的房子不挖地道，免得地窖拉出一条几十格的走廊。
            if (Math.abs(tx - cx) + Math.abs(tz - cz) > 44) continue;
            int sx = Mth.clamp(tx, cx - hw, cx + hw), sz = Mth.clamp(tz, cz - hd, cz + hd);
            // L 形地道：先沿 z 从大厅边缘走到小屋那一排，再沿 x 走到小屋正下方。
            if (tz != sz) rooms.add(new Room(sx, (sz + tz) / 2, 1, Math.abs(tz - sz) / 2));
            if (tx != sx) rooms.add(new Room((sx + tx) / 2, tz, Math.abs(tx - sx) / 2, 1));
            rooms.add(new Room(tx, tz, 2, 2));
            shafts.add(new int[]{tx, tz, plotBase(p)});
            built++;
        }
        return new LairPlan(rooms, shafts, 2 + layout.size.ordinal());
    }
    private static boolean inLair(List<Room> rooms, int x, int z) {
        for (Room r : rooms) if (r.contains(x, z)) return true;
        return false;
    }
    private static boolean nearLair(List<Room> rooms, int x, int z) {
        for (Room r : rooms) if (r.near(x, z)) return true;
        return false;
    }
    /** 巢穴地面：砂土、沙子、腐殖土、灰化土、砂砾和泥的混合。 */
    private BlockState lairFloor(int x, int z) {
        int n = hash(x, z, 41, 12);
        return (n < 4 ? Blocks.COARSE_DIRT : n < 6 ? Blocks.SAND : n < 8 ? Blocks.ROOTED_DIRT
                : n < 10 ? Blocks.PODZOL : n == 10 ? Blocks.GRAVEL : Blocks.MUD).defaultBlockState();
    }
    private BlockState lairWall(int x, int y, int z) {
        int n = hash(x, z, 53, 12);
        if (n == 0) return log(Direction.Axis.Y);
        if (n < 5) return Blocks.COBBLESTONE.defaultBlockState();
        if (n < 9) return Blocks.PACKED_MUD.defaultBlockState();
        return Blocks.SPRUCE_PLANKS.defaultBlockState();
    }
    private BlockState lairCeiling(int x, int z) {
        int n = hash(x, z, 59, 12);
        if (n < 3) return log(Direction.Axis.X);
        if (n < 4) return Blocks.PACKED_MUD.defaultBlockState();
        return Blocks.SPRUCE_PLANKS.defaultBlockState();
    }
    private BlockState fenceAll() {
        return Blocks.SPRUCE_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true)
                .setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
    }
    private void chestAt(WorldGenLevel level, BoundingBox box, int x, int y, int z, ResourceKey<LootTable> loot) {
        RandomSource random = RandomSource.create(layout.seed ^ x * 73856093L ^ z * 19349663L);
        createChest(level, box, random, world(x, y, z), loot, facing(Blocks.CHEST, Direction.SOUTH));
    }
    /** 地道（大厅以外的房间）覆盖的格子，用来给家具让路，免得堵住门口。 */
    private static boolean inCorridor(List<Room> rooms, int x, int z) {
        for (int i = 1; i < rooms.size(); i++) if (rooms.get(i).contains(x, z)) return true;
        return false;
    }
    private void furnish(WorldGenLevel level, BoundingBox box, List<Room> rooms, BlockState state, int x, int y, int z) {
        if (inCorridor(rooms, x, z)) return;
        put(level, box, state, x, y, z);
    }
    /**
     * 开挖地下巢穴。地形适配（beard_thin）会在包围盒下沿向上约 12 格的范围内挖掉地形，
     * 这里把同一段空间明确做成地下室：地板、墙面、顶板都由本方法写死，多余的空隙用夯土封住。
     */
    private void lair(WorldGenLevel level, BoundingBox box) {
        if (legacy) return;
        LairPlan plan = lairPlan();
        List<Room> rooms = plan.rooms();
        int cx = layout.cx, cz = layout.cz, hw = lairHalfW(), hd = lairHalfD();
        // 1) 地板、墙体、顶板：所有房间取并集，房间相接处自然留出通道。
        for (int x = 0; x < layout.size.width; x++) for (int z = 0; z < layout.size.depth; z++) {
            if (!nearLair(rooms, x, z)) continue;
            boolean inside = inLair(rooms, x, z);
            put(level, box, Blocks.STONE, x, 0, z);
            put(level, box, Blocks.STONE, x, 1, z);
            put(level, box, lairFloor(x, z), x, LAIR_FLOOR, z);
            if (inside) {
                for (int y = LAIR_FLOOR + 1; y < LAIR_CEIL; y++) put(level, box, Blocks.AIR, x, y, z);
                put(level, box, lairCeiling(x, z), x, LAIR_CEIL, z);
            } else {
                for (int y = LAIR_FLOOR + 1; y <= LAIR_CEIL; y++)
                    put(level, box, lairWall(x, y, z), x, y, z);
            }
            for (int y = LAIR_CEIL + 1; y <= LAIR_SEAL; y++) put(level, box, Blocks.PACKED_MUD, x, y, z);
        }
        // 2) 大厅正中一排木栅栏牢笼，四周留出过道。
        int cells = plan.cells(), first = cx - cells * 2, z0 = cz - 2;
        for (int i = 0; i < cells; i++) {
            int x0 = first + i * 4;
            for (int u = 0; u <= 3; u++) for (int v = 0; v <= 3; v++) {
                int x = x0 + u, z = z0 + v;
                // 笼顶整片铺木板：悦灵、鹦鹉这类会飞的囚徒飞不出笼子。
                put(level, box, Blocks.SPRUCE_PLANKS, x, LAIR_FLOOR + 4, z);
                boolean edge = u == 0 || u == 3 || v == 0 || v == 3;
                if (!edge) continue;
                boolean gate = v == 3 && u == 1;
                for (int h = LAIR_STAND; h <= LAIR_FLOOR + 3; h++) {
                    if (gate && h == LAIR_STAND)
                        put(level, box, facing(Blocks.SPRUCE_FENCE_GATE, Direction.SOUTH), x, h, z);
                    else put(level, box, fenceAll(), x, h, z);
                }
            }
            put(level, box, Blocks.HAY_BLOCK, x0 + 1, LAIR_STAND, z0 + 1);
            put(level, box, Blocks.COMPOSTER, x0 + 1, LAIR_STAND, z0 + 2);
        }
        // 3) 干草堆、南瓜、西瓜和木桶：靠南墙与东墙堆放；地道口那一格留空，别把通道堵死。
        for (int i = 0; i < 3; i++) {
            int x = cx - 2 + i * 2, z = cz + hd - 1;
            furnish(level, box, rooms, Blocks.HAY_BLOCK.defaultBlockState(), x, LAIR_STAND, z);
            if (i != 1) furnish(level, box, rooms, Blocks.HAY_BLOCK.defaultBlockState(), x, LAIR_STAND + 1, z);
        }
        furnish(level, box, rooms, Blocks.PUMPKIN.defaultBlockState(), cx - hw + 2, LAIR_STAND, cz + hd - 2);
        furnish(level, box, rooms, Blocks.MELON.defaultBlockState(), cx - hw + 3, LAIR_STAND, cz + hd - 2);
        // 第二只南瓜摆在第一只旁边，不能像以前那样悬在半空。
        furnish(level, box, rooms, Blocks.PUMPKIN.defaultBlockState(), cx - hw + 2, LAIR_STAND, cz + hd - 3);
        for (int i = 0; i < 3; i++)
            furnish(level, box, rooms, Blocks.BARREL.defaultBlockState(), cx + hw - 1, LAIR_STAND, cz - 2 + i);
        if (!inCorridor(rooms, cx + hw - 1, cz - 3))
            chestAt(level, box, cx + hw - 1, LAIR_STAND, cz - 3, LARDER_LOOT);
        furnish(level, box, rooms, Blocks.CRAFTING_TABLE.defaultBlockState(), cx - hw + 1, LAIR_STAND, cz - hd + 1);
        // 支撑柱：既像地窖梁柱，也给挂灯和梯子留出实体面。
        for (int x = cx - hw + 3; x <= cx + hw - 3; x += 4) {
            if (inCorridor(rooms, x, cz + hd - 2)) continue;
            for (int h = LAIR_STAND; h < LAIR_CEIL; h++)
                put(level, box, log(Direction.Axis.Y), x, h, cz + hd - 2);
            put(level, box, Blocks.LANTERN.defaultBlockState()
                    .setValue(BlockStateProperties.HANGING, true), x, LAIR_CEIL - 1, cz + hd - 3);
        }
        // 4) 梯子竖井：梯子背面必须有实体方块，否则方块更新时梯子会掉。
        for (int[] shaft : plan.shafts()) {
            int x = shaft[0], z = shaft[1], top = shaft[2];
            for (int y = LAIR_STAND; y < top; y++) {
                put(level, box, Blocks.PACKED_MUD, x, y, z - 1);
                put(level, box, facing(Blocks.LADDER, Direction.SOUTH), x, y, z);
            }
            put(level, box, facing(Blocks.SPRUCE_TRAPDOOR, Direction.SOUTH), x, top, z);
        }
    }
}
