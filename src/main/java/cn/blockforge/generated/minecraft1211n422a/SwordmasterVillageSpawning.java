package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * 村庄自然生成：村庄结构所在的区块加载时，按村庄大小决定这座村要不要常驻剑士，
 * 要的话确认村里还没有活着的剑士，再排队补 1 名。
 *
 * <p>1.21.1 的 NeoForge 没有通用的 {@code StructureSpawnEvent}，所以用
 * {@link ServerLevel#structureManager()#getStructureWithPieceAt} 查区块是否落在村庄结构里；
 * 查到后交给 {@link WorldgenSpawnQueue} 在服务器刻生成，不在区块加载回调里直接塞实体，
 * 避免和异步区块生成打架（这是本工程从魔晶洞穴 / 祭坛那里沿用的同一套做法）。</p>
 *
 * <p><b>r286 限流</b>：一个村庄<b>最多一名</b>常驻剑士。以前只数「中心点周围 48 格」，
 * 剑士追着猎物绕远、或冷却一过（约 17 分钟）区块一重载，就又多出一名——
 * 玩家在一个村子里见到五六名剑士就是这么来的。现在三道闸一起上：</p>
 * <ol>
 *   <li><b>按整座村庄的包围盒数人</b>：村里（含外围 16 格）已经站着一名就作罢；</li>
 *   <li><b>排队即记账</b>：冷却从排进队列那一刻起算，区块连着加载几Tick也只排一次；
 *     {@link WorldgenSpawnQueue#queueSwordmaster} 里再按村庄 key 去重一次；</li>
 *   <li><b>小村庄可能没有</b>：大村（≥96 格宽）必有，中村（≥64 格）七成，小村两成半。
 *     判定用种子 + 村庄坐标做哈希——同一座村庄在世界里永远同一个答案，
 *     不会今天有明天没有。</li>
 * </ol>
 * <p>剑士死后仍走 {@link SwordmasterVillageData} 的 10 分钟补充冷却。</p>
 */
public final class SwordmasterVillageSpawning {
    /** 「这座村庄决定有常驻剑士」的哈希盐，避免和其它结构的种子计算撞车。 */
    private static final long PRESENCE_SALT = 0x570F2E1D93A7L;

    private SwordmasterVillageSpawning() { }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (!SpawnControl.mobSpawningAllowed(level)) return;
        ChunkPos chunk = event.getChunk().getPos();
        // 必须用地表高度探测：村庄结构在山顶时，海平面高度不落在结构包围盒里。
        BlockPos probe = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(chunk.getMinBlockX() + 8, 0, chunk.getMinBlockZ() + 8));
        StructureStart start = level.structureManager().getStructureWithPieceAt(probe, StructureTags.VILLAGE);
        if (start == null || !start.isValid()) return;
        BoundingBox box = start.getBoundingBox();

        // ── 闸三：小村庄可能没有 ──
        // 用村庄中心坐标 + 世界种子做确定性哈希：这座村有没有常驻剑士，从生成那刻就定了。
        int centerX = (box.minX() + box.maxX()) / 2;
        int centerZ = (box.minZ() + box.maxZ()) / 2;
        int span = Math.max(box.getXSpan(), box.getZSpan());
        double presenceChance = span >= 96 ? 1.0 : span >= 64 ? 0.7 : 0.25;
        if (ManaConcentration.unitHash(level.getSeed() ^ PRESENCE_SALT, centerX, centerZ) > presenceChance) return;

        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(centerX, 0, centerZ));
        if (level.getBlockState(spawn.below()).isAir()) return;
        long villageKey = new ChunkPos(spawn).toLong();
        long now = level.getGameTime();
        SwordmasterVillageData data = SwordmasterVillageData.get(level);
        if (!data.canSpawn(villageKey, now)) return;

        // ── 闸一：整座村数人 ──
        // 村里（含外围 16 格）已经有活着的剑士就不补；顺手把冷却往后推，
        // 省得这座村每个区块加载一次就全图搜一遍实体。
        if (!level.getEntitiesOfClass(WanderingSwordmaster.class,
                new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(),
                        box.maxX() + 1.0D, box.maxY() + 1.0D, box.maxZ() + 1.0D).inflate(16.0D)).isEmpty()) {
            data.markSpawned(villageKey, now);
            return;
        }

        // ── 闸二：排队即记账 ──
        // 冷却从「排进队列」起算，而不是等实体真的落地才算——同一刻连着加载几个
        // 区块时，后面的几次 canSpawn 直接撞在冷却上，不会再排第二份。
        data.markSpawned(villageKey, now);
        WorldgenSpawnQueue.queueSwordmaster(level, spawn, villageKey);
    }
}
