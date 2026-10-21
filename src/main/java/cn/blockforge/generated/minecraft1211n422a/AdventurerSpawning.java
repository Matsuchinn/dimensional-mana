package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** 在相邻古代魔法祭坛布点区域之间，稀疏且可复现地生成冒险者。 */
public final class AdventurerSpawning {
    private static final int ALTAR_SPACING_CHUNKS = 56;
    private static final int MIDPOINT = ALTAR_SPACING_CHUNKS / 2;
    private static final long SALT = 0x41D7E917A11L;

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) return;
        ChunkPos chunk = event.getChunk().getPos();
        int localX = Math.floorMod(chunk.x, ALTAR_SPACING_CHUNKS);
        int localZ = Math.floorMod(chunk.z, ALTAR_SPACING_CHUNKS);
        // 一类位于东西相邻祭坛区域之间，另一类位于南北相邻区域之间。
        boolean betweenEastWest = localX == 0 && localZ == MIDPOINT;
        boolean betweenNorthSouth = localZ == 0 && localX == MIDPOINT;
        if (!betweenEastWest && !betweenNorthSouth) return;

        RandomSource random = RandomSource.create(level.getSeed() ^ chunk.toLong() ^ SALT);
        if (random.nextFloat() >= 0.60F) return;
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            int x = chunk.getMinBlockX() + 4 + random.nextInt(8);
            int z = chunk.getMinBlockZ() + 4 + random.nextInt(8);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos).isAir()) continue;
            Adventurer adventurer = GeneratedMod.ADVENTURER.get().create(level);
            if (adventurer == null) continue;
            adventurer.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(adventurer)) {
                adventurer.discard();
                continue;
            }
            adventurer.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
            adventurer.setPersistenceRequired();
            level.addFreshEntityWithPassengers(adventurer);
        }
    }

    private AdventurerSpawning() { }
}
