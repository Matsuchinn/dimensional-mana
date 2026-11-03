package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * 蚀魔荒原的地表改造：世界生成阶段把整块「主世界默认地表」染成荒原该有的样子。
 *
 * <p>原版的 surface rule 是全维度共享的一张规则表，动它就是动所有群系；这里改用生成期
 * feature，在 surface_structures 一步（洞穴雕刻之后、植被之前）按列改写：
 * <ul>
 *   <li>表层 → <b>秽蚀草块</b>，其下数格 → <b>秽蚀土</b>；沙 / 砂砾 / 黏土这些「水色」
 *       的表层一并替换——荒原不长沙子；</li>
 *   <li>再往下的一截石头（含花岗岩 / 闪长岩 / 安山岩 / 凝灰岩 / 深板岩）→ <b>蚀脉石</b>，
 *       让断崖和浅洞都露出魔素侵蚀过的岩面；裸露岩石的顶面本身也直接换成蚀脉石；</li>
 *   <li>顶面泡水或贴着水的列不长草皮，只出秽蚀土——这是和邻群系水域交界处的
 *       「泥土被侵蚀」过渡带；</li>
 *   <li><b>群系边界过渡</b>：靠近别的群系时，草皮不会一刀切到边界上。表层按距离依次退成
 *       秽蚀土 → 原版砂砾 → 原版石块，越靠外越「荒」，和邻居的绿草之间隔出一圈碎石劣地；
 *       荒原内部也零星掺一点砂砾 / 蚀脉石 / 裸土斑块，免得大片纯色。</li>
 * </ul>
 * 只对判定为荒原的列生效，跨界那一栏不会被染过去。</p>
 */
public final class ErosionSurfaceFeature extends Feature<NoneFeatureConfiguration> {
    /** 表层土壤替换深度。 */
    private static final int SOIL_DEPTH = 4;
    /** 表层以下再多深的一截石头换成蚀脉石（够覆盖断崖立面，再深交给矿脉特征）。 */
    private static final int ROCK_DEPTH = 20;

    // ── 群系边界过渡：区块外扩 8 格、每 4 格问一次群系，拼出一张「附近哪里还是荒原」的粗图 ──
    private static final int BORDER_MARGIN = 8;
    private static final int BORDER_STEP = 4;
    private static final int BORDER_GRID = (16 + BORDER_MARGIN * 2) / BORDER_STEP + 1;
    /** 粗图格点状态：这块采样点是不是荒原（未知 = 当时读不到区块，当荒原处理，不制造假边界）。 */
    private static final int WAS_NONE = -1;
    private static final int WAS_OTHER = 0;
    private static final int WAS_WASTELAND = 1;

    /** 过渡带总宽度（格）：从纯碎石到纯草皮的距离。 */
    private static final float TRANSITION_WIDTH = 11.0F;

    public ErosionSurfaceFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int baseX = origin.getX() & ~15;
        int baseZ = origin.getZ() & ~15;
        int changed = 0;

        // 先采样一张边界粗图，后面每列直接查距离，不用反复问群系。
        int[] borderMap = sampleBorder(level, baseX, baseZ, origin.getY());

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                // 从高度图往下跳过悬空的空气格，找到第一块实心顶面。
                while (topY > level.getMinBuildHeight() && level.getBlockState(cursor.set(x, topY, z)).isAir()) {
                    topY--;
                }
                cursor.set(x, topY, z);
                // 只管本群系的列；相邻群系越界进来的那一列留给它自己的地表。
                if (!CorruptedWastelandBiome.is(level.getBiome(cursor))) continue;
                BlockState top = level.getBlockState(cursor);
                if (top.isAir()) continue;
                if (isRock(top) && topY < 40 && !nearSurfaceOpenness(level, cursor)) continue; // 深处洞壁交给下面的岩层环

                // 到最近一个「非荒原」采样点的距离（格）；整张粗图都是荒原时是极大值，即群系深处。
                float border = borderDistance(borderMap,
                        x - (baseX - BORDER_MARGIN), z - (baseZ - BORDER_MARGIN));

                // 泡在水里的表层不长草皮：只出「被侵蚀的土」。
                boolean wet = level.getFluidState(cursor).is(FluidTags.WATER) || touchesWater(level, cursor);
                if (isRock(top)) {
                    set(cursor, ErosionWasteland.EROSION_STONE.get().defaultBlockState(), level);
                    changed++;
                } else if (!wet && (isSoilLike(top) || isGrassLike(top) || isSandLike(top))) {
                    // 草皮：靠近边界时按距离退成土 / 砂砾 / 石块，形成渐变过渡。
                    set(cursor, surfaceState(x, z, border), level);
                    changed++;
                } else if (isSoilLike(top) || isSandLike(top)) {
                    set(cursor, ErosionWasteland.EROSION_DIRT.get().defaultBlockState(), level);
                    changed++;
                }

                for (int dy = 1; dy <= SOIL_DEPTH + ROCK_DEPTH; dy++) {
                    cursor.set(x, topY - dy, z);
                    BlockState below = level.getBlockState(cursor);
                    if (below.isAir()) continue;
                    if (dy <= SOIL_DEPTH) {
                        if (isSoilLike(below) || isSandLike(below) || isGrassLike(below)) {
                            set(cursor, ErosionWasteland.EROSION_DIRT.get().defaultBlockState(), level);
                            changed++;
                            continue;
                        }
                    }
                    if (isRock(below)) {
                        set(cursor, ErosionWasteland.EROSION_STONE.get().defaultBlockState(), level);
                        changed++;
                    }
                }
            }
        }
        return changed > 0;
    }

    // ── 边界粗图 ──

    private static int[] sampleBorder(WorldGenLevel level, int baseX, int baseZ, int sampleY) {
        int[] map = new int[BORDER_GRID * BORDER_GRID];
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int gi = 0; gi < BORDER_GRID; gi++) {
            int sx = baseX - BORDER_MARGIN + gi * BORDER_STEP;
            for (int gj = 0; gj < BORDER_GRID; gj++) {
                int sz = baseZ - BORDER_MARGIN + gj * BORDER_STEP;
                probe.set(sx, sampleY, sz);
                int state;
                if (!WorldgenReads.biomeReadable(level, probe)) {
                    state = WAS_NONE;
                } else {
                    state = CorruptedWastelandBiome.is(level.getBiome(probe)) ? WAS_WASTELAND : WAS_OTHER;
                }
                map[gi * BORDER_GRID + gj] = state;
            }
        }
        return map;
    }

    /** 这一列到最近「非荒原」采样点的距离；附近全是荒原（或读不到）时返回极大值。 */
    private static float borderDistance(int[] map, int relX, int relZ) {
        float best = Float.MAX_VALUE;
        for (int gi = 0; gi < BORDER_GRID; gi++) {
            int gx = gi * BORDER_STEP;
            for (int gj = 0; gj < BORDER_GRID; gj++) {
                if (map[gi * BORDER_GRID + gj] != WAS_OTHER) continue;
                int gz = gj * BORDER_STEP;
                int ddx = relX - gx;
                int ddz = relZ - gz;
                float d = (float) Math.sqrt(ddx * ddx + ddz * ddz);
                if (d < best) best = d;
            }
        }
        return best;
    }

    // ── 地表材质挑选 ──

    private static BlockState surfaceState(int x, int z, float border) {
        if (border < Float.MAX_VALUE) {
            // 给距离加一点抖动，让过渡带边缘参差，而不是拿尺子画出来的一条直线。
            float d = border + (rand01(x, z, 17) - 0.5F) * 6.0F;
            if (d <= 2.0F) {
                // 最外圈：碎石带，和邻居的草皮之间隔一层踏实的东西。
                return rand01(x, z, 7) < 0.6F
                        ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.STONE.defaultBlockState();
            }
            if (d <= 5.0F) {
                // 砂砾为主，偶尔夹一块还没烂透的土。
                return rand01(x, z, 11) < 0.75F
                        ? Blocks.GRAVEL.defaultBlockState()
                        : ErosionWasteland.EROSION_DIRT.get().defaultBlockState();
            }
            if (d <= 8.0F) {
                // 草皮被啃掉大半，剩下零星的紫草斑。
                return rand01(x, z, 13) < 0.6F
                        ? ErosionWasteland.EROSION_DIRT.get().defaultBlockState()
                        : ErosionWasteland.EROSION_GRASS.get().defaultBlockState();
            }
            if (d <= TRANSITION_WIDTH) {
                // 快进荒原了：草多土少。
                return rand01(x, z, 19) < 0.25F
                        ? ErosionWasteland.EROSION_DIRT.get().defaultBlockState()
                        : ErosionWasteland.EROSION_GRASS.get().defaultBlockState();
            }
        }
        return innerState(x, z);
    }

    /** 荒原内部：以草皮为主，零星掺砂砾 / 蚀脉石 / 裸土，避免整片一个颜色。 */
    private static BlockState innerState(int x, int z) {
        float r = rand01(x, z, 31);
        if (r < 0.030F) return Blocks.GRAVEL.defaultBlockState();
        if (r < 0.055F) return ErosionWasteland.EROSION_STONE.get().defaultBlockState();
        if (r < 0.130F) return ErosionWasteland.EROSION_DIRT.get().defaultBlockState();
        return ErosionWasteland.EROSION_GRASS.get().defaultBlockState();
    }

    /** 坐标哈希伪随机：同一条世界种子下每次生成结果一致，不会存档一重开就换样。 */
    private static float rand01(int x, int z, int salt) {
        int h = x * 0x27d4eb2d + z * 0x165667b1 + salt * 0x9e3779b9;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }

    // ── 方块判定 ──

    /** 该洞壁上方不远处就是开阔空间：这种「浅层洞顶 / 洞口岩面」也要染色。 */
    private static boolean nearSurfaceOpenness(WorldGenLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos up = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= 6; dy++) {
            up.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (level.getBlockState(up).isAir()) return true;
        }
        return false;
    }

    private static void set(BlockPos pos, BlockState state, WorldGenLevel level) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** 这一格是否紧贴水面（用于水域交界的侵蚀过渡）。 */
    private static boolean touchesWater(WorldGenLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                if (!WorldgenReads.chunkLoaded(level, c)) continue;
                if (level.getFluidState(c).is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }

    private static boolean isGrassLike(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(ErosionWasteland.EROSION_GRASS.get());
    }

    private static boolean isSoilLike(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.MUD)
                || state.is(Blocks.CLAY) || state.is(ErosionWasteland.EROSION_DIRT.get());
    }

    private static boolean isSandLike(BlockState state) {
        return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    private static boolean isRock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF) || state.is(Blocks.DEEPSLATE)
                || state.is(ErosionWasteland.EROSION_STONE.get());
    }
}
