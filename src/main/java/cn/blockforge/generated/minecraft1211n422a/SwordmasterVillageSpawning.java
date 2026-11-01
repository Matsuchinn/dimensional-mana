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
 * 村庄自然生成：村庄结构所在的区块加载时，确认这个村庄还没有常驻剑士，就排队补 1 名。
 *
 * <p>1.21.1 的 NeoForge 没有通用的 {@code StructureSpawnEvent}，所以用
 * {@link ServerLevel#structureManager()#getStructureWithPieceAt} 查区块是否落在村庄结构里；
 * 查到后交给 {@link WorldgenSpawnQueue} 在服务器刻生成，不在区块加载回调里直接塞实体，
 * 避免和异步区块生成打架（这是本工程从魔晶洞穴 / 祭坛那里沿用的同一套做法）。</p>
 *
 * <p>去重交给 {@link SwordmasterVillageData}：同一座村庄 20 分钟内只刷一次，
 * 区块反复加载不会刷出一排剑士；剑士死掉后 10 分钟才会补充。</p>
 */
public final class SwordmasterVillageSpawning {
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
        int centerX = (box.minX() + box.maxX()) / 2;
        int centerZ = (box.minZ() + box.maxZ()) / 2;
        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(centerX, 0, centerZ));
        if (level.getBlockState(spawn.below()).isAir()) return;
        long villageKey = new ChunkPos(spawn).toLong();
        long now = level.getGameTime();
        SwordmasterVillageData data = SwordmasterVillageData.get(level);
        if (!data.canSpawn(villageKey, now)) return;
        // 附近 48 格已经有一位在巡逻就不重复刷，只把冷却往后推。
        if (!level.getEntitiesOfClass(WanderingSwordmaster.class, new net.minecraft.world.phys.AABB(spawn.getX() - 48.0D, spawn.getY() - 48.0D, spawn.getZ() - 48.0D, spawn.getX() + 48.0D, spawn.getY() + 48.0D, spawn.getZ() + 48.0D),
                swordmaster -> true).isEmpty()) {
            data.markSpawned(villageKey, now);
            return;
        }
        WorldgenSpawnQueue.queueSwordmaster(level, spawn, villageKey);
    }
}
