package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * 露天魔晶矿簇的构件：四种形态都在这里一次性摆完，不刷实体、不写存档数据。
 *
 * <p>坐标约定：局部 (dx, dy, dz) 里 dy = 0 是结构中心那一格地表方块，
 * dy 为负是往地下挖，为正就是地面上方。所有柱子、树根在底部都会
 * {@link #ground} 往下补到实地，所以建在斜坡上也不会出现悬空的半截柱子。</p>
 */
public final class OpenAirManaCrystalClusterPiece extends StructurePiece {
    /** 水平半宽：陨石坑半径 11，外圈矿柱再往外几格，17 格留足余量。 */
    public static final int HALF = 17;
    /** 地面以下预留：坑深 5、埋魔晶块到 -12、柱子根基 -8，24 格足够。 */
    public static final int BELOW = 24;
    /** 地面以上预留：最高的一根矿柱 22 格，加上顶部晶簇，40 格够。 */
    private static final int ABOVE = 40;

    /** 每块魔晶矿变成同等级粗制魔晶块的概率（设计稿的 14%）。 */
    private static final float RAW_BLOCK_CHANCE = 0.14F;
    /** 有对应"粗制魔晶块"的四个等级；特级只有晶簇与成品块，没有粗制块。 */
    private static final java.util.Set<String> HAS_RAW_BLOCK = java.util.Set.of("lesser", "medium", "advanced", "supreme");

    private final OpenAirManaCrystalClusterStructure.Variant variant;
    private final long layoutSeed;

    public OpenAirManaCrystalClusterPiece(BlockPos center, OpenAirManaCrystalClusterStructure.Variant variant,
                                          long layoutSeed) {
        super(GeneratedMod.OPEN_AIR_MANA_CRYSTAL_CLUSTER_PIECE.get(), 0,
                new BoundingBox(center.getX() - HALF, center.getY() - BELOW, center.getZ() - HALF,
                        center.getX() + HALF, center.getY() + ABOVE, center.getZ() + HALF));
        // 和祭坛构件一样必须显式给朝向：没有朝向时结构片段的局部坐标会被当成世界坐标，
        // 所有方块都落在当前区块边界外、一颗都放不下去。
        setOrientation(Direction.SOUTH);
        this.variant = variant;
        this.layoutSeed = layoutSeed;
    }

    public OpenAirManaCrystalClusterPiece(CompoundTag tag) {
        super(GeneratedMod.OPEN_AIR_MANA_CRYSTAL_CLUSTER_PIECE.get(), tag);
        if (getOrientation() == null) setOrientation(Direction.SOUTH);
        this.variant = OpenAirManaCrystalClusterStructure.Variant.byId(tag.getInt("cluster_variant"));
        this.layoutSeed = tag.getLong("cluster_layout_seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("cluster_variant", variant.ordinal());
        tag.putLong("cluster_layout_seed", layoutSeed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        // 用固定种子重放布局：同一个构件在多个区块里被分别调用时，摆出来的形状必须一模一样。
        RandomSource layout = RandomSource.create(layoutSeed);
        switch (variant) {
            case SPIKE -> buildSpikes(level, layout, chunkBox);
            case PARASITE_TREE -> buildParasiteTree(level, layout, chunkBox);
            case CRATER -> buildCrater(level, layout, chunkBox);
            case RING_ALTAR -> buildRingAltar(level, layout, chunkBox);
        }
        // 守卫（3～7 只随机魔素变异体）只排队，等区块加载后由 WorldgenSpawnQueue 真正刷出来。
        // 构件会被多个区块分别调用，队列那边按"中心附近已有几只"去重，不会刷成一大群；
        // 数量由中心坐标算出来，同一座矿簇永远是同一个数。
        WorldgenSpawnQueue.queueCrystalCluster(level.getLevel(), centerPos());
    }

    // ---------------------------------------------------------------- 破土刺天型

    /**
     * 主变体：几根巨大的矿柱像利剑一样从地下直接刺出地表。
     * 柱子底部是碎裂的蚀脉石与下级魔晶矿，中间夹中级魔晶矿，
     * 顶端镶嵌数个极品魔晶矿与特级魔晶簇（每一块矿石都有 14% 是粗制魔晶块）。
     */
    private void buildSpikes(WorldGenLevel level, RandomSource random, BoundingBox box) {
        // 被顶开的一圈地皮：碎裂蚀脉石为主，混着下级魔晶矿。
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                int sqr = dx * dx + dz * dz;
                if (sqr > 81) continue;
                double radius = Math.sqrt(sqr);
                BlockState floor;
                if (radius < 3.0) {
                    floor = random.nextBoolean() ? cobble() : ore(random, "lesser");
                } else if (radius < 6.0) {
                    floor = random.nextFloat() < 0.25F ? ore(random, "lesser") : cobble();
                } else {
                    floor = random.nextFloat() < 0.15F ? cobble() : stone();
                }
                set(level, box, dx, 0, dz, floor);
                ground(level, box, dx, 0, dz, stone());
            }
        }

        int pillars = 3 + random.nextInt(3);
        double spread = random.nextDouble() * Math.PI * 2.0;
        for (int p = 0; p < pillars; p++) {
            double angle = spread + p * (Math.PI * 2.0 / pillars) + (random.nextDouble() - 0.5) * 0.6;
            int distance = 2 + random.nextInt(6);
            int height = 14 + random.nextInt(9);
            int baseRadius = 2 + random.nextInt(2);
            int originX = (int) Math.round(Math.cos(angle) * distance);
            int originZ = (int) Math.round(Math.sin(angle) * distance);
            // 从地下七格一直顶到柱顶：越高越细，越往上越朝外歪，像被地底的力顶出去的。
            for (int dy = -7; dy <= height; dy++) {
                double t = (dy + 7.0) / (height + 7.0);
                int radius = Math.max(0, (int) Math.round(baseRadius * (1.0 - t) + 0.4));
                int lean = (int) Math.round(t * t * 2.0);
                int cx = originX + (int) Math.round(Math.cos(angle) * lean);
                int cz = originZ + (int) Math.round(Math.sin(angle) * lean);
                for (int ox = -radius; ox <= radius; ox++) {
                    for (int oz = -radius; oz <= radius; oz++) {
                        if (ox * ox + oz * oz > radius * radius + Math.max(0, radius - 1)) continue;
                        set(level, box, cx + ox, dy, cz + oz, spikeMaterial(random, t));
                    }
                }
            }
            // 柱顶：极品魔晶矿压顶，旁边再扎几颗特级魔晶簇。
            int topX = originX + (int) Math.round(Math.cos(angle) * 2.0);
            int topZ = originZ + (int) Math.round(Math.sin(angle) * 2.0);
            set(level, box, topX, height + 1, topZ, ore(random, "supreme"));
            clusterOn(level, box, topX, height + 2, topZ, Direction.UP);
            clusterOn(level, box, topX + 1, height + 1, topZ, Direction.UP);
            clusterOn(level, box, topX, height + 1, topZ + 1, Direction.UP);
            clusterOn(level, box, topX - 1, height, topZ, Direction.WEST);
            clusterOn(level, box, topX, height, topZ + 1, Direction.SOUTH);
            // 柱根往地下再接一截，斜坡上也不会露出半截悬空的柱子。
            ground(level, box, originX, -7, originZ, stone());
        }
        // 柱子之间的地面再零星撒些晶簇，让这块地一眼看上去"全是矿"。
        for (int i = 0; i < 6; i++) {
            int dx = random.nextInt(13) - 6;
            int dz = random.nextInt(13) - 6;
            if (dx * dx + dz * dz > 36) continue;
            clusterOn(level, box, dx, 1, dz, Direction.UP);
        }
    }

    /**
     * 矿柱的分层材质：底部碎裂蚀脉石 + 下级矿，中间中级矿，顶部极品矿。
     *
     * <p>r285：成块的粗制魔晶不再由整座结构掷一次 14% 决定，而是<b>每一块矿石自己</b>
     * 在 {@link #ore(RandomSource, String)} 里掷 14%，所以柱子、地面、树根上都会零散地
     * 长出粗制魔晶块，而不是"这座有矿、那座一颗都没有"。</p>
     */
    private BlockState spikeMaterial(RandomSource random, double t) {
        if (t < 0.35) {
            if (random.nextFloat() < 0.12F) return ore(random, "lesser");
            return random.nextFloat() < 0.55F ? cobble() : stone();
        }
        if (t < 0.7) {
            if (random.nextFloat() < 0.10F) return ore(random, "medium");
            return random.nextFloat() < 0.4F ? cobble() : stone();
        }
        if (random.nextFloat() < 0.15F) return ore(random, "supreme");
        return stone();
    }

    // ---------------------------------------------------------------- 寄生巨树型

    /**
     * 一根巨大的蚀纹木枯树：底部是不规则树根，下粗上窄，有很多没有叶子的树枝，
     * 表面长出魔晶矿，矿柱顺着树干螺旋缠绕而上，树冠处结出巨大的魔晶块、外扎特级魔晶簇。
     *
     * <p>造型参考玩家给的那张 3D 建筑图（树干弯曲、多枝、冠层外扩），这里用方块拼一个
     * 同类骨架，只是把叶子换成了魔晶。</p>
     */
    private void buildParasiteTree(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int rootRadius = 4 + random.nextInt(3);
        // 树根：不规则圆盘，中心是蚀纹木芯，外圈压着秽蚀土。
        for (int dx = -rootRadius; dx <= rootRadius; dx++) {
            for (int dz = -rootRadius; dz <= rootRadius; dz++) {
                double radius = Math.sqrt(dx * dx + dz * dz);
                if (radius > rootRadius + (random.nextDouble() - 0.5) * 1.4) continue;
                boolean core = radius < rootRadius - 1.5;
                set(level, box, dx, 0, dz, core ? log() : (random.nextFloat() < 0.5F ? log() : dirt()));
                ground(level, box, dx, 0, dz, core ? log() : dirt());
                if (core && radius < rootRadius - 3.0) set(level, box, dx, -1, dz, log());
            }
        }

        int trunkHeight = 22 + random.nextInt(9);
        int trunkTopX = 0;
        int trunkTopZ = 0;
        for (int dy = 1; dy <= trunkHeight; dy++) {
            double t = (double) dy / trunkHeight;
            int radius = t < 0.18 ? 2 : 1;
            // 轻微弯曲：两根不同频率的正弦叠起来，树干看起来是活的。
            int cx = (int) Math.round(Math.sin(t * 4.2 + 0.6) * 1.1);
            int cz = (int) Math.round(Math.cos(t * 3.1 + 1.2) * 0.9);
            for (int ox = -radius; ox <= radius; ox++) {
                for (int oz = -radius; oz <= radius; oz++) {
                    if (radius == 2 && Math.abs(ox) == 2 && Math.abs(oz) == 2) continue;
                    set(level, box, cx + ox, dy, cz + oz, log());
                }
            }
            trunkTopX = cx;
            trunkTopZ = cz;
            // 树干表面长出的魔晶矿。
            if (dy > 2 && random.nextFloat() < 0.30F) {
                Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                int sx = trunkTopX + side.getStepX() * (radius + 1);
                int sz = trunkTopZ + side.getStepZ() * (radius + 1);
                BlockState vein = ore(random, random.nextFloat() < 0.5F ? "medium" : "lesser");
                set(level, box, sx, dy, sz, vein);
                if (random.nextFloat() < 0.35F) {
                    clusterOn(level, box, sx + side.getStepX(), dy, sz + side.getStepZ(), side);
                }
            }
        }

        // 树枝：从树干中上段伸出去，末端结一小团魔晶（没有叶子，全是矿）。
        int branches = 5 + random.nextInt(4);
        for (int b = 0; b < branches; b++) {
            int startY = 8 + random.nextInt(Math.max(1, trunkHeight - 10));
            double angle = random.nextDouble() * Math.PI * 2.0;
            int length = 4 + random.nextInt(5);
            int rise = 2 + random.nextInt(4);
            int bx = (int) Math.round(Math.sin((double) startY / trunkHeight * 4.2 + 0.6) * 1.1);
            int bz = (int) Math.round(Math.cos((double) startY / trunkHeight * 3.1 + 1.2) * 0.9);
            for (int i = 1; i <= length; i++) {
                int y = startY + (int) Math.round((double) i * rise / length);
                int x = bx + (int) Math.round(Math.cos(angle) * i);
                int z = bz + (int) Math.round(Math.sin(angle) * i);
                set(level, box, x, y, z, log());
                if (i > 1 && i < length) set(level, box, x, y + 1, z, log());
            }
            int tipX = bx + (int) Math.round(Math.cos(angle) * length);
            int tipZ = bz + (int) Math.round(Math.sin(angle) * length);
            int tipY = startY + rise;
            crystalBlob(level, box, random, tipX, tipY, tipZ, 1);
            // 枝头再压一颗粗制极品魔晶块：枯树上结的那颗"果子"。
            set(level, box, tipX, tipY + 1, tipZ, rawCrystal("supreme"));
        }
        // 主干树冠：一大团魔晶压在主枝顶上，四周再结三团小一点的，最后扎特级晶簇。
        // 需求里说的是"树冠处结出巨大的魔晶块"，所以主团给到半径 3（7×7×7 的外形）。
        crystalBlob(level, box, random, trunkTopX, trunkHeight, trunkTopZ, 3);
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int ox = (int) Math.round(Math.cos(angle) * 3.0);
            int oz = (int) Math.round(Math.sin(angle) * 3.0);
            crystalBlob(level, box, random, trunkTopX + ox, trunkHeight - 1 + random.nextInt(3),
                    trunkTopZ + oz, 2);
        }
        for (int i = 0; i < 5; i++) {
            Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            clusterOn(level, box, trunkTopX + facing.getStepX() * 3, trunkHeight + 1,
                    trunkTopZ + facing.getStepZ() * 3, facing);
        }

        // 矿柱顺着树干螺旋缠绕而上（需求里明确要求的那圈"螺旋"）。
        double turns = 2.0 + random.nextDouble() * 1.5;
        int steps = trunkHeight * 3;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double angle = t * turns * Math.PI * 2.0;
            int dy = 1 + (int) Math.round(t * (trunkHeight - 2));
            int radius = 2 + (t < 0.2 ? 1 : 0);
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            BlockState rail = random.nextFloat() < 0.45F ? brick() : cobble();
            set(level, box, dx, dy, dz, rail);
            if (random.nextFloat() < 0.22F) {
                clusterOn(level, box, dx + (int) Math.signum(dx), dy, dz, dx >= 0 ? Direction.EAST : Direction.WEST);
            }
        }
    }

    // ---------------------------------------------------------------- 陨石塌陷型

    /**
     * 地表有一个陨石坑般的凹陷，坑底全是平滑蚀脉石构成的底座，
     * 矿柱呈放射状向四周倾斜生长，像是在地底爆炸后被推出来的。
     */
    private void buildCrater(WorldGenLevel level, RandomSource random, BoundingBox box) {
        int radius = 11;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int sqr = dx * dx + dz * dz;
                if (sqr > radius * radius) continue;
                double t = Math.sqrt(sqr) / radius;
                int depth = (int) Math.round(5.0 * (1.0 - t * t));
                // 挖空：从地表往下挖出一个碗，越靠中心越深。
                for (int dy = 0; dy > -depth; dy--) {
                    set(level, box, dx, dy, dz, Blocks.AIR.defaultBlockState());
                }
                // 坑底铺平滑蚀脉石底座，再往下补实地。
                set(level, box, dx, -depth, dz, smooth());
                ground(level, box, dx, -depth, dz, smooth());
                // 坑沿一圈碎裂蚀脉石，看上去像被炸开的岩壳。
                if (t > 0.82) {
                    set(level, box, dx, 0, dz, random.nextFloat() < 0.6F ? cobble() : stone());
                }
            }
        }

        // 放射状矿柱：从坑底向外倾斜生长。
        int pillars = 5 + random.nextInt(4);
        double spread = random.nextDouble() * Math.PI * 2.0;
        for (int p = 0; p < pillars; p++) {
            double angle = spread + p * (Math.PI * 2.0 / pillars) + (random.nextDouble() - 0.5) * 0.5;
            int distance = 3 + random.nextInt(4);
            int height = 8 + random.nextInt(8);
            int originX = (int) Math.round(Math.cos(angle) * distance);
            int originZ = (int) Math.round(Math.sin(angle) * distance);
            int baseDepth = (int) Math.round(5.0 * (1.0 - Math.pow((double) distance / radius, 2)));
            for (int dy = -baseDepth; dy <= height; dy++) {
                double t = (double) (dy + baseDepth) / (height + baseDepth);
                int columnRadius = Math.max(0, (int) Math.round(2.0 * (1.0 - t) + 0.4));
                int lean = (int) Math.round(t * 3.0);
                int cx = originX + (int) Math.round(Math.cos(angle) * lean);
                int cz = originZ + (int) Math.round(Math.sin(angle) * lean);
                for (int ox = -columnRadius; ox <= columnRadius; ox++) {
                    for (int oz = -columnRadius; oz <= columnRadius; oz++) {
                        if (ox * ox + oz * oz > columnRadius * columnRadius + Math.max(0, columnRadius - 1)) continue;
                        BlockState state;
                        if (t < 0.3) state = random.nextFloat() < 0.3F ? ore(random, "lesser") : cobble();
                        else if (random.nextFloat() < 0.12F) state = ore(random, "medium");
                        else state = stone();
                        set(level, box, cx + ox, dy, cz + oz, state);
                    }
                }
            }
            int tipX = originX + (int) Math.round(Math.cos(angle) * 3.0);
            int tipZ = originZ + (int) Math.round(Math.sin(angle) * 3.0);
            set(level, box, tipX, height, tipZ, ore(random, "supreme"));
            clusterOn(level, box, tipX, height + 1, tipZ, Direction.UP);
            clusterOn(level, box, tipX + 1, height, tipZ, Direction.UP);
        }

        // 地底爆炸核心留下的几块粗制魔晶：这是"爆炸时被推上来"的造型细节，照旧埋着。
        for (int i = 0; i < 6; i++) {
            int dx = random.nextInt(7) - 3;
            int dz = random.nextInt(7) - 3;
            set(level, box, dx, -6 - random.nextInt(4), dz,
                    rawCrystal(random.nextFloat() < 0.3F ? "supreme" : "advanced"));
        }
    }

    // ---------------------------------------------------------------- 环形祭坛型

    /**
     * 6 根粗细均匀的矿柱围成一个完美的圆圈，中心是一块平坦的平滑蚀脉石，
     * 其下方埋着 3～9 个魔晶块。
     */
    private void buildRingAltar(WorldGenLevel level, RandomSource random, BoundingBox box) {
        // 中心平台：半径 4 的平滑蚀脉石圆盘。
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 16) continue;
                set(level, box, dx, 0, dz, smooth());
                ground(level, box, dx, 0, dz, smooth());
            }
        }
        // 平台下面埋着 3～9 个魔晶块，等级越高埋得越深。
        int buried = 3 + random.nextInt(7);
        String[] tiers = {"lesser", "medium", "advanced", "supreme"};
        for (int i = 0; i < buried; i++) {
            int dx = random.nextInt(7) - 3;
            int dz = random.nextInt(7) - 3;
            if (dx * dx + dz * dz > 9) continue;
            int depth = 2 + random.nextInt(5);
            set(level, box, dx, -depth, dz, rawCrystal(tiers[Math.min(tiers.length - 1, depth / 2)]));
            ground(level, box, dx, -depth - 1, dz, stone());
        }

        // 六根一模一样高的矿柱，围成正圆。
        int height = 10;
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3.0;
            int cx = (int) Math.round(Math.cos(angle) * 8);
            int cz = (int) Math.round(Math.sin(angle) * 8);
            for (int dy = 0; dy <= height; dy++) {
                boolean band = dy == height / 2; // 正中间一圈矿脉
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        if (Math.abs(ox) == 1 && Math.abs(oz) == 1) continue;
                        BlockState state = band ? ore(random, "advanced")
                                : (random.nextFloat() < 0.3F ? cobble() : brick());
                        set(level, box, cx + ox, dy, cz + oz, state);
                    }
                }
            }
            // 柱顶各自顶一颗极品魔晶矿和特级魔晶簇。
            set(level, box, cx, height + 1, cz, ore(random, "supreme"));
            clusterOn(level, box, cx, height + 2, cz, Direction.UP);
            clusterOn(level, box, cx + 1, height + 1, cz, Direction.UP);
            // 柱脚往下接实地。
            ground(level, box, cx, 0, cz, brick());
        }
    }

    // ---------------------------------------------------------------- 通用小工具

    /** 一坨魔晶团：球状，留些窟窿，最外层再扎几个特级晶簇。 */
    private void crystalBlob(WorldGenLevel level, BoundingBox box, RandomSource random,
                             int dx, int dy, int dz, int radius) {
        for (int ox = -radius; ox <= radius; ox++) {
            for (int oy = -radius; oy <= radius; oy++) {
                for (int oz = -radius; oz <= radius; oz++) {
                    if (ox * ox + oy * oy + oz * oz > radius * radius + 1) continue;
                    if (random.nextFloat() < 0.35F) continue;
                    BlockState state;
                    if (random.nextFloat() < 0.25F) state = ore(random, "supreme");
                    else if (random.nextFloat() < 0.5F) state = rawCrystal("advanced");
                    else state = rawCrystal("supreme");
                    set(level, box, dx + ox, dy + oy, dz + oz, state);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            if (facing == Direction.UP || facing == Direction.DOWN) continue;
            clusterOn(level, box, dx + facing.getStepX() * radius, dy, dz + facing.getStepZ() * radius, facing);
        }
        clusterOn(level, box, dx, dy + radius, dz, Direction.UP);
    }

    /**
     * 只在有依托的面上扎晶簇：支撑面必须真的能"贴住"，否则晶簇会当场掉下来
     * （或者像以前那样，支持块在另一个区块里还没铺好，长出来就是一颗悬空的紫晶）。
     *
     * <p>r285：判定从"背后那格不是空气"换成原版晶簇自己的存活规则
     * {@link BlockState#isFaceSturdy}，和 {@code AmethystClusterBlock#canSurvive} 完全一致，
     * 这样贴墙、贴顶的朝向一定是这条支撑面真正撑得住的方向。</p>
     */
    private void clusterOn(WorldGenLevel level, BoundingBox box, int dx, int dy, int dz, Direction facing) {
        BlockState here = at(level, box, dx, dy, dz);
        if (!here.canBeReplaced()) return;
        // r286 修复：r285 这里直接拿「构件局部坐标」去问世界坐标系的 box 和方块状态，
        // 两套坐标差着构件在世界里的位置（几千上万像素），isInside 永远不成立，
        // 整座矿簇就一颗特级晶簇都扎不下去。局部坐标必须先用 getWorldPos 换算。
        BlockPos.MutableBlockPos support =
                getWorldPos(HALF + dx - facing.getStepX(), BELOW + dy - facing.getStepY(),
                        HALF + dz - facing.getStepZ());
        // 支撑块必须在本次能看到的区块里，并且那个面上确实撑得住
        // （和 AmethystClusterBlock#canSurvive 一样：问支撑块朝向晶簇的那一面）。
        if (!box.isInside(support)) return;
        if (!level.getBlockState(support).isFaceSturdy(level, support, facing)) return;
        set(level, box, dx, dy, dz, state("special_mana_crystal_cluster")
                .setValue(AmethystClusterBlock.FACING, facing)
                .setValue(AmethystClusterBlock.WATERLOGGED, false));
    }

    private void set(WorldGenLevel level, BoundingBox box, int dx, int dy, int dz, BlockState state) {
        placeBlock(level, state, HALF + dx, BELOW + dy, HALF + dz, box);
    }

    private BlockState at(WorldGenLevel level, BoundingBox box, int dx, int dy, int dz) {
        return getBlock(level, HALF + dx, BELOW + dy, HALF + dz, box);
    }

    /** 从 dy 往下一格开始补到实地（只填空气/可替换方块），保证柱子、树根不悬空。 */
    private void ground(WorldGenLevel level, BoundingBox box, int dx, int dy, int dz, BlockState state) {
        fillColumnDown(level, state, HALF + dx, BELOW + dy - 1, HALF + dz, box);
    }

    private BlockPos centerPos() {
        return new BlockPos((boundingBox.minX() + boundingBox.maxX()) / 2,
                boundingBox.minY() + BELOW, (boundingBox.minZ() + boundingBox.maxZ()) / 2);
    }

    private static BlockState state(String id) {
        return GeneratedMod.MOD_BLOCKS.get(id).get().defaultBlockState();
    }

    /**
     * 自然生成的矿簇里长的是**粗制魔晶块**（对应原版露天矿脉里挖出来的粗铁块）：
     * 炼好的魔晶块只能由玩家拿 9 颗成品魔晶去工作台压出来。
     */
    private static BlockState rawCrystal(String tier) {
        return state("raw_" + tier + "_mana_crystal_block");
    }

    /**
     * 一块魔晶矿；r285 起每块矿自己掷 14% 变成同等级的**粗制魔晶块**
     * （下级/中级/高级/极品四级都有对应的粗制块；特级只有晶簇、没有矿石，自然不参与）。
     *
     * <p>以前是"整座结构掷一次 14%"，掷不中就满结构都是普通矿石、一颗成块的都没有，
     * 掷中了又成片长。改成逐块判定后，矿柱、地面、树根上会零散地夹杂粗制魔晶块，
     * 和原版"矿脉里偶尔一整块粗铁"的观感一致。</p>
     */
    private static BlockState ore(RandomSource random, String tier) {
        if (HAS_RAW_BLOCK.contains(tier) && random.nextFloat() < RAW_BLOCK_CHANCE) return rawCrystal(tier);
        return state(tier + "_mana_crystal_ore");
    }

    private static BlockState stone() {
        return ErosionWasteland.EROSION_STONE.get().defaultBlockState();
    }

    private static BlockState cobble() {
        return ErosionWasteland.EROSION_COBBLESTONE.get().defaultBlockState();
    }

    private static BlockState brick() {
        return ErosionWasteland.EROSION_STONE_BRICKS.get().defaultBlockState();
    }

    private static BlockState smooth() {
        return ErosionWasteland.SMOOTH_EROSION_STONE.get().defaultBlockState();
    }

    private static BlockState dirt() {
        return ErosionWasteland.EROSION_DIRT.get().defaultBlockState();
    }

    private static BlockState log() {
        return ErosionWasteland.EROSION_LOG.get().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    }
}
