package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 已生成的「露天魔晶矿簇」登记表（每个存档一份，落盘保存）。
 *
 * <p>登记来源和古代魔法祭坛一样有两条，按位置去重、互不冲突：</p>
 * <ul>
 *   <li><b>新结构</b>：矿簇构件生成时经 {@link WorldgenSpawnQueue} 排队，等区块真正加载后
 *       进服务器主线程登记（世界生成阶段不能写存档数据）；</li>
 *   <li><b>补录</b>：区块加载时查本区块的结构起始点，是矿簇就按包围盒换算回中心补登记，
 *       万一队列被挤掉了也不会漏。</li>
 * </ul>
 *
 * <p>登记后驱动四件事：{@link ManaConcentration} 读取 {@link #isEmpowered} 给 8 格内的
 * 方块 +4.00 魔素浓度（不可叠加）；服务器每秒在范围内撒一轮紫色魔素粒子；
 * 「浓度 ≥ 19 的变异体每秒回 4% 血」——矿簇正好把附近从 15 顶上 19，
 * 所以守卫站在矿簇里会回血，跑出 8 格就没了；以及 r284 加的<b>守卫兜底</b>：
 * 玩家靠近时核对中心附近有没有 {@link #guardQuota} 只变异体，缺了当场补上。</p>
 */
public final class CrystalClusterData extends SavedData {
    /** 影响半径：结构中心水平 8 格内，垂直同样限 8 格（不贯穿整根区块柱）。 */
    public static final int AURA_RADIUS = 8;
    /** 范围内统一 +4.00；判定只问"在不在任一矿簇范围内"，多座重叠也只加一次，不可叠加。 */
    public static final double AURA_BONUS = 4.0;
    /** 变异体在此浓度以上开始每秒回血。 */
    public static final double REGEN_CONCENTRATION = 19.0;
    /** 每秒恢复的最大生命值占比（4%）。 */
    public static final double REGEN_FRACTION = 0.04;
    /** 守卫数量：每座矿簇 3～7 只（设计稿给的取值范围）。 */
    public static final int MIN_GUARDS = 3;
    /** 守卫数量上限。 */
    public static final int MAX_GUARDS = 7;
    /** 粒子与回血只在玩家附近处理，离人太远的矿簇不去算。 */
    private static final int ACTIVE_RANGE = 96;
    /**
     * 变异体被"高浓度吸引"的最大距离。
     *
     * <p>r285：从 40 缩到 24。40 格是把半个荒原的变异体都往矿簇这边吸，玩家站在矿簇里
     * 看到的就是乌泱泱一大群（其中大多是野外自然刷出来、被吸过来的），和设计稿里
     * "3～7 只守卫在附近游荡"的观感差太远。</p>
     */
    private static final double LURE_RANGE = 24.0;

    private static final String FILE_NAME = GeneratedMod.MOD_ID + "_mana_crystal_clusters";
    private static final ResourceKey<Structure> CLUSTER_KEY = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "open_air_mana_crystal_cluster"));
    private static final Factory<CrystalClusterData> FACTORY = new Factory<>(
            CrystalClusterData::new,
            CrystalClusterData::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final List<BlockPos> clusters = new ArrayList<>();
    /**
     * 按区块索引的同一批矿簇，给 {@link #contains} 用：矿簇的影响半径只有 8 格，
     * 所以查一个坐标只需要看它自己所在区块和周围 8 个区块，不用把整张登记表扫一遍。
     * r284 起普通群系里也会长矿簇，登记表会变长很多，这个索引就是为那一步加的。
     */
    private final java.util.Map<Long, BlockPos> byChunk = new java.util.HashMap<>();
    /**
     * 本次会话里已经核对过守卫的矿簇。
     *
     * <p>只放在内存里、不进存档：重新进存档会再核对一次，旧存档里空着的矿簇也能补上；
     * 但同一局里不会再补——守卫被打光就真的没了，不至于变成可以挂机刷的刷怪点。</p>
     */
    private final java.util.Set<BlockPos> guardsChecked = new java.util.HashSet<>();
    /** 每座矿簇补守卫的尝试次数：连试几次都凑不齐就不再纠缠，免得每秒刷一遍日志。 */
    private final java.util.Map<BlockPos, Integer> guardAttempts = new java.util.HashMap<>();
    /**
     * 本次会话里每座矿簇<b>已经刷出过多少只守卫</b>。
     *
     * <p>r285 加的硬上限：以前"数不到就补"，而守卫被刷进石缝里窒息死掉、或者掉到
     * 判定框外面，数不到就当没刷，一秒补一次、一次最多补一整份配额，玩家看到的
     * 就是乌泱泱一大群。现在同一座矿簇最多只会刷出 {@link #guardQuota} 只，刷过就不再补。</p>
     */
    private final java.util.Map<BlockPos, Integer> guardsSpawned = new java.util.HashMap<>();
    /**
     * 本次会话里已经清理过晶簇朝向的矿簇。
     *
     * <p>r285：旧存档里已经生成的矿簇，晶簇可能贴在会被后续方块覆盖的位置上，
     * 生成完就悬空、朝向也歪。玩家第一次走近时把周围一圈的特级魔晶簇按真实支撑
     * 重新对一次方向，没有支撑的直接清掉，每座矿簇只做一次。</p>
     */
    private final java.util.Set<BlockPos> blocksFixed = new java.util.HashSet<>();
    /** 本次会话里已经"收过一次多余守卫"的矿簇（老存档里刷多了的那些）。 */
    private final java.util.Set<BlockPos> guardsTrimmed = new java.util.HashSet<>();

    public static CrystalClusterData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public synchronized boolean isEmpty() {
        return clusters.isEmpty();
    }

    /** 只读快照，方便遍历时不用一直持锁。 */
    public synchronized List<BlockPos> snapshot() {
        return List.copyOf(clusters);
    }

    /**
     * 登记一座矿簇中心；±6 格内已有记录就跳过，两条登记路径不会重复。
     *
     * @return 这次是否真的新登记了一座
     */
    public synchronized boolean add(BlockPos center) {
        for (BlockPos existing : clusters) {
            if (existing.closerThan(center, 6.0)) return false;
        }
        index(center);
        setDirty();
        return true;
    }

    /** 同时写进列表和区块索引；读档时也走这里。 */
    private void index(BlockPos center) {
        BlockPos immutable = center.immutable();
        clusters.add(immutable);
        byChunk.put(ChunkPos.asLong(immutable.getX() >> 4, immutable.getZ() >> 4), immutable);
    }

    /**
     * 这座矿簇固定该站几只守卫：由中心坐标算出来，3～7 只。
     *
     * <p>用坐标散列而不是每次掷骰子，是为了同一座矿簇无论排队几次、`/locate` 找到几次、
     * 重进几次存档，数量都一致，玩家不会看到"这次 3 只、下次 7 只"。</p>
     */
    public static int guardQuota(BlockPos center) {
        double roll = ManaConcentration.unitHash(0x5EEDC1A57EL, center.getX(), center.getZ());
        int span = MAX_GUARDS - MIN_GUARDS + 1;
        return MIN_GUARDS + Math.min(span - 1, (int) Math.floor(roll * span));
    }

    /** 该位置是否落在任一矿簇的 8 格影响圈内。 */
    public synchronized boolean contains(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        // 影响半径 8 格 < 一个区块，最多跨到相邻区块，所以只查 3×3 就够。
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos cluster = byChunk.get(ChunkPos.asLong(chunkX + dx, chunkZ + dz));
                if (cluster != null && withinAura(cluster, pos)) return true;
            }
        }
        return false;
    }

    /** 便捷入口：世界某位置是否受矿簇浓度加成。 */
    public static boolean isEmpowered(ServerLevel level, BlockPos pos) {
        return get(level).contains(pos);
    }

    /** 离该点最近的矿簇中心；太远（超过 {@value #LURE_RANGE} 格）返回 null。 */
    public synchronized BlockPos nearest(BlockPos pos, double maxDistance) {
        BlockPos best = null;
        double bestSqr = maxDistance * maxDistance;
        for (BlockPos cluster : clusters) {
            double dx = pos.getX() + 0.5 - (cluster.getX() + 0.5);
            double dz = pos.getZ() + 0.5 - (cluster.getZ() + 0.5);
            double sqr = dx * dx + dz * dz;
            if (sqr <= bestSqr) {
                bestSqr = sqr;
                best = cluster;
            }
        }
        return best;
    }

    /** 水平与垂直都按 8 格算的"矿簇周围"：判定口径与粒子、回血范围完全一致。 */
    private static boolean withinAura(BlockPos cluster, BlockPos pos) {
        int dx = pos.getX() - cluster.getX();
        int dz = pos.getZ() - cluster.getZ();
        return dx * dx + dz * dz <= AURA_RADIUS * AURA_RADIUS
                && Math.abs(pos.getY() - cluster.getY()) <= AURA_RADIUS;
    }

    /** 区块加载补录：按结构起始点的包围盒反推矿簇中心。 */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Structure cluster = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CLUSTER_KEY);
        if (cluster == null) return;
        StructureStart start = event.getChunk().getStartForStructure(cluster);
        if (start == null || !start.isValid()) return;
        BlockPos center = centerOf(start.getBoundingBox());
        // 队列被挤掉、或者存档是在更新前开的时候，这里补登记。
        get(level).add(center);
        // 守卫也顺手补一次：以前只有"这次刚好是新登记"才补，已经登记过的矿簇
        // 一旦守卫没刷出来就永远空着，所以现在每次区块加载都排一次队（排队那边会按
        // 附近已有的数量去重，不会重复刷）。
        WorldgenSpawnQueue.queueCrystalCluster(level, center);
    }

    /** 从结构包围盒换算矿簇中心（地表那一格）。构件的包围盒就是按这个中心生成的。 */
    static BlockPos centerOf(BoundingBox box) {
        return new BlockPos((box.minX() + box.maxX()) / 2, box.minY()
                + OpenAirManaCrystalClusterPiece.BELOW, (box.minZ() + box.maxZ()) / 2);
    }

    /** 每秒一次的矿簇维护：粒子、守卫兜底、变异体回血、变异体被浓度吸引。 */
    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        CrystalClusterData data = get(level);
        if (data.isEmpty()) return;
        List<BlockPos> active = activeCenters(level, data);
        if (active.isEmpty()) return;
        long time = level.getGameTime();
        if (time % 10 == 0) spawnParticles(level, active);
        if (time % 20 == 0) {
            data.fixClusterBlocks(level, active);
            data.trimSurplusGuards(level, active);
            data.ensureGuards(level, active);
            tickMutants(level, active);
        }
    }

    /**
     * 把这座矿簇的守卫补到 {@link #guardQuota} 只，<b>本次会话最多刷这么多，一只都不多给</b>。
     *
     * <p>"已经算过几只"取两边的较大值：本次会话刷出去的累计数，以及中心附近现在实际站着
     * 的变异体数（含野外自然刷出来的）。所以一片已经很热闹的矿簇不会再补兵，
     * 守卫被打光之后也不会再凭空冒出来。</p>
     *
     * @return 这次实际新刷出来的只数
     */
    public synchronized int topUpGuards(ServerLevel level, BlockPos center) {
        if (!SpawnControl.hostileSpawningAllowed(level)) return 0;
        int quota = guardQuota(center);
        int recorded = guardsSpawned.getOrDefault(center, 0);
        int present = Math.max(recorded, Math.min(quota, WorldgenSpawnQueue.countCrystalClusterGuards(level, center)));
        int missing = quota - present;
        if (missing <= 0) {
            guardsSpawned.put(center, present);
            return 0;
        }
        int made = WorldgenSpawnQueue.spawnCrystalClusterGuards(level, center, missing);
        guardsSpawned.put(center, present + made);
        return made;
    }

    /**
     * 守卫兜底：玩家靠近一座还没核对过的矿簇时，把守卫补到配额。
     *
     * <p>r284 加这一条是因为原来只有"世界生成 → 排队 → 区块加载"一条路会刷守卫：
     * 队列被挤掉、存档在机制上线之前就开好、或者守卫被玩家清掉了，那座矿簇就永远
     * 空着，没有任何补救。r285 起每次补刷都过 {@link #topUpGuards} 的记账，
     * 不会再出现"一秒钟补一份、越补越多"。</p>
     */
    private void ensureGuards(ServerLevel level, List<BlockPos> active) {
        if (!SpawnControl.hostileSpawningAllowed(level)) return;
        for (BlockPos cluster : active) {
            if (guardsChecked.contains(cluster)) continue;
            int attempts = guardAttempts.getOrDefault(cluster, 0);
            if (attempts >= 3) continue; // 试过三次还是不行就不再纠缠，免得每秒刷日志
            guardAttempts.put(cluster, attempts + 1);
            int quota = guardQuota(cluster);
            topUpGuards(level, cluster);
            // 凑够配额（不管是这次刷的、还是本来就在附近的）就收手。
            if (guardsSpawned.getOrDefault(cluster, 0) >= quota
                    || WorldgenSpawnQueue.countCrystalClusterGuards(level, cluster) >= quota) {
                guardsChecked.add(cluster);
            }
        }
    }

    /**
     * 把老存档里"刷多了"的守卫收回到配额数：每座矿簇本次会话只做一次。
     *
     * <p>r285 之前的逻辑是"数不到就补"，坏版本在玩家存档里留下了成堆的变异体
     * （有的矿簇周围十几二十只）。它们都是常驻的，不会自己消失，光靠修代码
     * 玩家眼前那一堆还在。这里在玩家走到 48 格以内、区块确实加载出来的时候，
     * 把离矿簇中心<b>最远</b>的那些收到配额数为止：</p>
     *
     * <ul>
     *   <li>正在打架的（有目标的）不动；</li>
     *   <li>离任何玩家 12 格以内的不动——绝不会从玩家眼前把怪抽走；</li>
     *   <li>只多不少地收，收完不会再补回来（配合 {@link #guardsSpawned} 的记账）。</li>
     * </ul>
     */
    private void trimSurplusGuards(ServerLevel level, List<BlockPos> active) {
        if (!SpawnControl.hostileSpawningAllowed(level)) return;
        for (BlockPos cluster : active) {
            if (guardsTrimmed.contains(cluster)) continue;
            // 只有玩家够近、区块肯定加载了才动手，否则容易"看起来很多、其实数不到"。
            boolean close = false;
            for (ServerPlayer player : level.players()) {
                if (player.blockPosition().closerThan(cluster, 48.0D)) {
                    close = true;
                    break;
                }
            }
            if (!close) continue;
            guardsTrimmed.add(cluster);
            int quota = guardQuota(cluster);
            List<Monster> mutants = level.getEntitiesOfClass(Monster.class,
                    new net.minecraft.world.phys.AABB(cluster).inflate(16.0, 12.0, 16.0),
                    mob -> mob.isAlive() && isManaMutant(mob));
            if (mutants.size() <= quota) continue;
            // 留下离中心最近的 quota 只，其余从远到近收掉。
            mutants.sort(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(
                    cluster.getX() + 0.5D, cluster.getY() + 0.5D, cluster.getZ() + 0.5D)));
            for (int i = quota; i < mutants.size(); i++) {
                Monster mob = mutants.get(i);
                if (mob.getTarget() != null) continue;
                boolean nearPlayer = false;
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(mob) < 12.0D * 12.0D) {
                        nearPlayer = true;
                        break;
                    }
                }
                if (nearPlayer) continue;
                level.sendParticles(ParticleTypes.POOF,
                        mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(),
                        8, 0.3D, 0.3D, 0.3D, 0.02D);
                mob.discard();
            }
        }
    }

    /**
     * 把矿簇周围的特级魔晶簇按<b>真实支撑面</b>重新对一次方向，悬空没有支撑的直接清掉。
     *
     * <p>r285：玩家截图里能看到晶簇悬在矿柱外面、或者朝向歪着贴进石头里。原因是老版本
     * 放晶簇时只检查"背后那格不是空气"，而背后那格可能在别的区块里还没铺好、或者之后
     * 被同一座结构的柱子覆盖掉，晶簇就变成一颗飘在半空的紫晶。这里在玩家第一次走近时
     * 用晶簇自己的存活规则（{@code isFaceSturdy}）重算一遍：能换个面贴住就换面，
     * 一个能撑住的面都没有就拆掉。<b>每座矿簇只做一次</b>，每次最多处理一座，
     * 避免玩家飞到矿簇边上时一下卡住。</p>
     */
    private void fixClusterBlocks(ServerLevel level, List<BlockPos> active) {
        net.minecraft.world.level.block.Block crystalBlock =
                GeneratedMod.MOD_BLOCKS.get("special_mana_crystal_cluster").get();
        for (BlockPos cluster : active) {
            if (blocksFixed.contains(cluster)) continue;
            int half = OpenAirManaCrystalClusterPiece.HALF;
            // 四个角所在区块都加载好了才动手，否则修一半就记成"修过了"，剩下的永远没人管。
            if (!cornersLoaded(level, cluster, half)) continue;
            int maxY = Math.min(level.getMaxBuildHeight() - 1, cluster.getY() + 40);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    for (int y = cluster.getY() - 1; y <= maxY; y++) {
                        cursor.set(cluster.getX() + dx, y, cluster.getZ() + dz);
                        if (!level.isLoaded(cursor)) continue;
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
                        if (state.getBlock() != crystalBlock) continue;
                        net.minecraft.core.Direction facing = state.getValue(
                                net.minecraft.world.level.block.AmethystClusterBlock.FACING);
                        if (isSupported(level, cursor, facing)) continue;
                        net.minecraft.core.Direction fixed = supportedFace(level, cursor);
                        if (fixed == null) {
                            // 四面全空：这颗真的是飞在半空里的，拆掉。
                            level.setBlock(cursor, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                        } else if (fixed != facing) {
                            level.setBlock(cursor, state.setValue(
                                    net.minecraft.world.level.block.AmethystClusterBlock.FACING, fixed), 2);
                        }
                    }
                }
            }
            blocksFixed.add(cluster);
            // 一次只修一座，剩下的等下一秒。
            return;
        }
    }

    /** 矿簇包围盒四个角所在的区块是不是都加载好了（矩形是连续的，四角齐了就全齐）。 */
    private static boolean cornersLoaded(ServerLevel level, BlockPos center, int half) {
        return level.hasChunkAt(center.offset(half, 0, half))
                && level.hasChunkAt(center.offset(half, 0, -half))
                && level.hasChunkAt(center.offset(-half, 0, half))
                && level.hasChunkAt(center.offset(-half, 0, -half));
    }

    /** 这个朝向的支撑面撑不撑得住（和 {@code AmethystClusterBlock#canSurvive} 同一套判定）。 */
    private static boolean isSupported(ServerLevel level, BlockPos pos, net.minecraft.core.Direction facing) {
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    /** 从六个方向里挑一个撑得住的面（地面上优先，其次天花板，最后才是四面墙）。 */
    private static net.minecraft.core.Direction supportedFace(ServerLevel level, BlockPos pos) {
        net.minecraft.core.Direction[] order = {
                net.minecraft.core.Direction.UP, net.minecraft.core.Direction.DOWN,
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};
        for (net.minecraft.core.Direction direction : order) {
            if (isSupported(level, pos, direction)) return direction;
        }
        return null;
    }

    /** 玩家附近的矿簇（离人太远的一律不算，避免满世界算账）。 */
    private static List<BlockPos> activeCenters(ServerLevel level, CrystalClusterData data) {
        List<BlockPos> active = new ArrayList<>();
        for (BlockPos cluster : data.snapshot()) {
            for (ServerPlayer player : level.players()) {
                if (player.blockPosition().closerThan(cluster, ACTIVE_RANGE)) {
                    active.add(cluster);
                    break;
                }
            }
            if (active.size() >= 8) break; // 一次最多处理 8 座，防止极端存档里卡顿
        }
        return active;
    }

    /** 紫色魔素粒子：从矿簇中心往外飘，越靠上越稀，像被矿脉吸出来的一层雾。 */
    private static void spawnParticles(ServerLevel level, List<BlockPos> active) {
        for (BlockPos cluster : active) {
            level.sendParticles(ParticleTypes.WITCH,
                    cluster.getX() + 0.5 + (level.random.nextDouble() - 0.5) * AURA_RADIUS * 2.0,
                    cluster.getY() + 0.6 + level.random.nextDouble() * 4.0,
                    cluster.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * AURA_RADIUS * 2.0,
                    2, 0.15, 0.25, 0.15, 0.02);
            if (level.random.nextFloat() < 0.5F) {
                level.sendParticles(ParticleTypes.ENCHANT,
                        cluster.getX() + 0.5 + (level.random.nextDouble() - 0.5) * AURA_RADIUS * 2.0,
                        cluster.getY() + 1.0 + level.random.nextDouble() * 3.0,
                        cluster.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * AURA_RADIUS * 2.0,
                        1, 0.1, 0.2, 0.1, 0.01);
            }
            if (level.random.nextFloat() < 0.25F) {
                level.sendParticles(ParticleTypes.PORTAL,
                        cluster.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 5.0,
                        cluster.getY() + 0.4 + level.random.nextDouble() * 2.0,
                        cluster.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 5.0,
                        1, 0.05, 0.1, 0.05, 0.01);
            }
        }
    }

    /** 变异体「往矿簇挪」多少刻才重新安排一次路（3 秒）。 */
    private static final int LURE_REPATH_INTERVAL = 60;

    /**
     * 变异体：浓度 ≥ 19 时每秒回 4% 血；周围空闲的会被高浓度吸引着往矿簇走。
     *
     * <p>r336 性能：这里原来每秒对每座矿簇做一次「24 格内所有怪物」的查询，
     * 再对每只空闲变异体直接调一次 {@code getNavigation().moveTo(...)}。
     * 一片矿簇周围蹲着十几只变异体时，等于每秒发起十几条 A*（而且同一只怪物被几座矿簇
     * 同时选中时还会重复算），服务器刻掉得很明显。现在改成：</p>
     * <ol>
     *   <li>查询时直接用谓词筛掉非变异体，不再把 24 格内所有怪物先收成一张表；</li>
     *   <li>「往矿簇走」改为每 3 秒安排一次，并且走 {@link MobPathing#moveToPoint}
     *       —— 它自带重算节流，路走不通时还会拉长间隔，不会再变成每秒一次的全量寻路。</li>
     * </ol>
     */
    private static void tickMutants(ServerLevel level, List<BlockPos> active) {
        boolean lure = level.getGameTime() % LURE_REPATH_INTERVAL == 0;
        for (BlockPos cluster : active) {
            List<Monster> monsters = level.getEntitiesOfClass(Monster.class,
                    new net.minecraft.world.phys.AABB(cluster).inflate(LURE_RANGE),
                    mob -> mob.isAlive() && isManaMutant(mob));
            for (Monster monster : monsters) {
                BlockPos pos = monster.blockPosition();
                if (ManaConcentration.at(level, pos) >= REGEN_CONCENTRATION
                        && monster.getHealth() < monster.getMaxHealth()) {
                    monster.heal(Math.max(1.0F, monster.getMaxHealth() * (float) REGEN_FRACTION));
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            monster.getX(), monster.getY() + monster.getBbHeight() * 0.6, monster.getZ(),
                            2, 0.2, 0.2, 0.2, 0.01);
                }
                // "喜欢高魔素浓度区域"：没有在打架、又离得比较远时，往矿簇方向挪。
                if (!lure || monster.getTarget() != null) continue;
                double dx = cluster.getX() + 0.5 - monster.getX();
                double dz = cluster.getZ() + 0.5 - monster.getZ();
                if (dx * dx + dz * dz < 36.0) continue;
                MobPathing.moveToPoint(monster,
                        new net.minecraft.world.phys.Vec3(cluster.getX() + 0.5, cluster.getY() + 1.0, cluster.getZ() + 0.5),
                        0.8);
            }
        }
    }

    /**
     * 魔素变异体：晶化行尸、聚魔爆裂体、魔蚀骸骨、魔蚀织网者、虚空蚀影。
     * 与 {@link ManaStoneSystem} 的变异映射保持一致。
     */
    public static boolean isManaMutant(net.minecraft.world.entity.Entity entity) {
        return entity instanceof CrystallizedZombie
                || entity instanceof ManaCreeper
                || entity instanceof CorruptedSkeleton
                || entity instanceof CorruptedWeaverEntity
                || entity instanceof VoidShadeEntity;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos cluster : clusters) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", cluster.getX());
            entry.putInt("y", cluster.getY());
            entry.putInt("z", cluster.getZ());
            list.add(entry);
        }
        tag.put("clusters", list);
        return tag;
    }

    private static CrystalClusterData load(CompoundTag tag, HolderLookup.Provider registries) {
        CrystalClusterData data = new CrystalClusterData();
        ListTag list = tag.getList("clusters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.index(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
        }
        return data;
    }

    private CrystalClusterData() {}
}
