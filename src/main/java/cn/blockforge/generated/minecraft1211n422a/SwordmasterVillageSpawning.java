package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 村庄自然生成：村庄结构所在的区块加载时，按村庄大小决定这座村要不要常驻剑士，
 * 要的话确认村里还没有活着的剑士，再排队补 1 名。
 *
 * <p><b>r400 死锁修复：区块加载回调里不再碰世界。</b>这个回调发生在区块「补成完整状态」的
 * 那一步，而这一步本身就排在服务器主线程的任务队列上。以前这里会查地表高度图、再用
 * {@code getStructureWithPieceAt} 顺着结构引用去读<b>相邻区块</b>的结构数据——只要有一个相邻
 * 区块还没加载完，游戏就会让主线程停下来同步生成它，而那块区块也排在同一个主线程队列里，
 * 于是世界定格。现在回调里只登记一个区块坐标，真正判定挪到服务器刻
 * （{@link #tickLevel}），而且只读<b>本区块自己记着的结构起始点</b>
 * （{@link LevelChunk#getAllStarts()}），不再顺着引用去摸别的区块。
 * 查到以后仍然交给 {@link WorldgenSpawnQueue} 在服务器刻生成实体。</p>
 *
 * <p><b>一个村庄一辈子只有一名</b>。以前是「刷过之后冷却 20 分钟、剑士被杀死再等 10 分钟补人」，
 * 结果玩家杀掉一个、过一会儿转角又遇见一个，村庄永远在补人。现在三道闸一起上：</p>
 * <ol>
 *   <li><b>按整座村庄的包围盒数人</b>：村里（含外围 16 格）已经站着一名就作罢；</li>
 *   <li><b>排队即记账</b>：一旦排进队列就在 {@link SwordmasterVillageData} 里永久登记，
 *     区块连着加载几Tick也只排一次；{@link WorldgenSpawnQueue#queueSwordmaster} 里再按村庄 key 去重一次；</li>
 *   <li><b>小村庄可能没有</b>：大村（≥96 格宽）必有，中村（≥64 格）七成，小村两成半。
 *     判定用种子 + 村庄坐标做哈希——同一座村庄在世界里永远同一个答案，
 *     不会今天有明天没有。</li>
 * </ol>
 * <p>剑士死了、被雇走、走丢了都不会再补：这座村庄已经刷过剑士，账本永久有效。</p>
 */
public final class SwordmasterVillageSpawning {
    /** 「这座村庄决定有常驻剑士」的哈希盐，避免和其它结构的种子计算撞车。 */
    private static final long PRESENCE_SALT = 0x570F2E1D93A7L;
    /** 每刻最多处理几个待查区块：批量加载村庄周围时不会在同一刻集中查完。 */
    private static final int CHUNKS_PER_TICK = 2;
    /** 排队最多的区块数；等太久还没加载好就丢掉，等下次加载再来。 */
    private static final long QUEUE_TTL = 1200L;

    /** 待判定区块：键为区块坐标，值为排进来的游戏刻。 */
    private static final Map<Long, Long> PENDING = new ConcurrentHashMap<>();

    private SwordmasterVillageSpawning() { }

    /**
     * 区块加载回调：只登记坐标。
     *
     * <p>这里不读高度图、不查结构、不数实体——任何一次方块/结构读取都可能落在还没加载完的
     * 区块上，把主线程卡在同步加载里。判定全部挪到 {@link #tickLevel}。</p>
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        ChunkPos chunk = event.getChunk().getPos();
        if (PENDING.size() > 512) PENDING.clear();
        PENDING.put(chunk.toLong(), level.getGameTime());
    }

    /** 服务器刻里结算待判定区块：区块已经加载好才往下走。 */
    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (PENDING.isEmpty()) return;
        if (!SpawnControl.mobSpawningAllowed(level)) return;
        long now = level.getGameTime();
        int budget = CHUNKS_PER_TICK;
        Iterator<Map.Entry<Long, Long>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext() && budget > 0) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (now - entry.getValue() > QUEUE_TTL) {
                iterator.remove();
                continue;
            }
            ChunkPos pos = new ChunkPos(entry.getKey());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            iterator.remove();
            budget--;
            processChunk(level, chunk);
        }
    }

    private static void processChunk(ServerLevel level, LevelChunk chunk) {
        StructureStart start = villageStart(chunk);
        if (start == null || !start.isValid()) return;
        BoundingBox box = start.getBoundingBox();

        // ── 闸三：小村庄可能没有 ──
        // 用村庄中心坐标 + 世界种子做确定性哈希：这座村有没有常驻剑士，从生成那刻就定了。
        int centerX = (box.minX() + box.maxX()) / 2;
        int centerZ = (box.minZ() + box.maxZ()) / 2;
        int span = Math.max(box.getXSpan(), box.getZSpan());
        // r105 权重：大 / 中 / 小村原本是 100% / 70% / 25%，再乘配置里的流浪剑士权重。
        // 调低 → 更多村庄一辈子没有常驻剑士；调到 0 → 全世界的村庄都不安排。
        double presenceChance = Math.min(1.0,
                (span >= 96 ? 1.0 : span >= 64 ? 0.7 : 0.25) * MobSpawnWeights.scale("wandering_swordmaster"));
        if (presenceChance <= 0.0) return;
        if (ManaConcentration.unitHash(level.getSeed() ^ PRESENCE_SALT, centerX, centerZ) > presenceChance) return;

        // 村庄中心可能落在隔壁区块：那里还没加载好就先放弃，下次那块区块加载时会自己再排一次。
        BlockPos centerColumn = new BlockPos(centerX, 0, centerZ);
        if (!ChunkSafety.ready(level, centerColumn)) return;
        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerColumn);
        if (level.getBlockState(spawn.below()).isAir()) return;
        long villageKey = new ChunkPos(spawn).toLong();
        long now = level.getGameTime();
        SwordmasterVillageData data = SwordmasterVillageData.get(level);
        // 这座村庄已经刷过剑士（哪怕它已经死了）：永远不再补第二个。
        if (!data.canSpawn(villageKey)) return;

        // ── 闸一：整座村数人 ──
        // 村里（含外围 16 格）已经有活着的剑士（可能是刷新蛋 / 召回符咒招来的），
        // 就把这座村永久记为「刷过」，省得以后每个区块加载一次就全图搜一遍实体。
        if (!level.getEntitiesOfClass(WanderingSwordmaster.class,
                new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(),
                        box.maxX() + 1.0D, box.maxY() + 1.0D, box.maxZ() + 1.0D).inflate(16.0D)).isEmpty()) {
            data.markSpawned(villageKey, now);
            return;
        }

        // ── 闸二：排队即记账 ──
        // 记账在「排进队列」时就写死，而不是等实体真的落地才算——同一刻连着加载几个
        // 区块时，后面的几次 canSpawn 直接读到「已刷过」，不会再排第二份。
        data.markSpawned(villageKey, now);
        WorldgenSpawnQueue.queueSwordmaster(level, spawn, villageKey);
    }

    /**
     * 本区块自己记着的村庄结构起始点。
     *
     * <p>不用 {@code structureManager().getStructureWithPieceAt(...)}：那个方法会顺着结构引用
     * 去查相邻区块的结构数据，在区块刚加载的时刻正好会踩到「同步加载区块」的坑。
     * 区块自带的 {@code getAllStarts()} 只是它自己存档里的表，读它不需要加载任何别的区块。</p>
     */
    private static StructureStart villageStart(LevelChunk chunk) {
        Registry<Structure> registry = chunk.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            Holder<Structure> holder = registry.wrapAsHolder(entry.getKey());
            if (!holder.is(StructureTags.VILLAGE)) continue;
            StructureStart start = entry.getValue();
            if (start != null && start.isValid()) return start;
        }
        return null;
    }
}
