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
}
