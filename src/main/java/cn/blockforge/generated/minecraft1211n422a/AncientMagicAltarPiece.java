package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;

/** 单区块祭坛构件：环形地基、中央碑台、宝箱和守卫均在同一构件内生成。 */
public final class AncientMagicAltarPiece extends StructurePiece {
    private static final ResourceKey<net.minecraft.world.level.storage.loot.LootTable> ALTAR_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "chests/ancient_magic_altar"));

    public AncientMagicAltarPiece(BlockPos center) {
        super(GeneratedMod.ANCIENT_MAGIC_ALTAR_PIECE.get(), 0,
                new BoundingBox(center.getX() - 7, center.getY() - 4, center.getZ() - 7,
                        center.getX() + 7, center.getY() + 8, center.getZ() + 7));
        // StructurePiece 的放置方法接收构件局部坐标；没有朝向时会误将 0~14
        // 当作世界坐标，导致所有方块因落在当前区块边界外而被跳过。
        setOrientation(Direction.SOUTH);
    }

    public AncientMagicAltarPiece(CompoundTag tag) {
        super(GeneratedMod.ANCIENT_MAGIC_ALTAR_PIECE.get(), tag);
        // 兼容修复前已写入区块、但尚未成功放置建筑的构件记录。
        if (getOrientation() == null) setOrientation(Direction.SOUTH);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putBoolean("AncientMagicAltar", true);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BlockState brick = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState tile = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();

        // 压入地面的完整地基；切去四角后形成十五格宽的圆角祭坛轮廓。
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) {
            int dx = Math.abs(x - 7), dz = Math.abs(z - 7);
            if (dx + dz > 11) continue;
            BlockState floor = (x + z) % 7 == 0 ? cracked : (dx == 7 || dz == 7 ? brick : tile);
            placeBlock(level, floor, x, 4, z, chunkBox);
            fillColumnDown(level, brick, x, 3, z, chunkBox);
        }

        // 四向宽台阶与中央三层仪式台。
        generateBox(level, chunkBox, 5, 4, 0, 9, 4, 14, polished, polished, false);
        generateBox(level, chunkBox, 0, 4, 5, 14, 4, 9, polished, polished, false);
        generateBox(level, chunkBox, 4, 5, 4, 10, 5, 10, brick, brick, false);
        generateBox(level, chunkBox, 5, 6, 5, 9, 6, 9, tile, tile, false);
        generateBox(level, chunkBox, 6, 7, 6, 8, 7, 8, crying, crying, false);
        placeBlock(level, GeneratedMod.MOD_BLOCKS.get("ancient_magic_stele").get().defaultBlockState()
                .setValue(AncientMagicSteleBlock.FACING, Direction.SOUTH), 7, 8, 7, chunkBox);

        // 四座残缺的魔法柱，深色材质、灵魂灯和哭泣黑曜石强调远古祭祀感。
        int[][] pillars = {{2, 2}, {12, 2}, {2, 12}, {12, 12}};
        for (int i = 0; i < pillars.length; i++) {
            int x = pillars[i][0], z = pillars[i][1];
            int height = 3 + (i % 2);
            for (int y = 5; y <= 4 + height; y++) placeBlock(level,
                    y == 6 ? crying : (y == 4 + height ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : brick),
                    x, y, z, chunkBox);
            placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState(), x, 5 + height, z, chunkBox);
        }

        // 低矮外环保留四个入口，结构始终是露天祭坛而不是封闭房间。
        for (int i = 2; i <= 12; i++) {
            if (i >= 6 && i <= 8) continue;
            placeBlock(level, brick, i, 5, 1, chunkBox);
            placeBlock(level, brick, i, 5, 13, chunkBox);
            placeBlock(level, brick, 1, 5, i, chunkBox);
            placeBlock(level, brick, 13, 5, i, chunkBox);
        }
        placeBlock(level, Blocks.PURPLE_CARPET.defaultBlockState(), 7, 8, 5, chunkBox);
        placeBlock(level, Blocks.PURPLE_CARPET.defaultBlockState(), 7, 8, 9, chunkBox);

        createLootChest(level, random, chunkBox, 3, 5, 7, Direction.EAST);
        if (random.nextBoolean()) createLootChest(level, random, chunkBox, 11, 5, 7, Direction.WEST);

        int witchCount = 1 + random.nextInt(3);
        int[][] spawnPoints = {{4, 5, 4}, {10, 5, 10}, {10, 5, 4}};
        for (int i = 0; i < witchCount; i++) spawnWitch(level, spawnPoints[i][0], spawnPoints[i][1], spawnPoints[i][2]);
    }

    private void createLootChest(WorldGenLevel level, RandomSource random, BoundingBox box,
                                 int x, int y, int z, Direction facing) {
        BlockPos pos = worldPos(x, y, z);
        if (!box.isInside(pos)) return;
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        RandomizableContainer.setBlockEntityLootTable(level, random, pos, ALTAR_LOOT);
    }

    private void spawnWitch(WorldGenLevel level, int x, int y, int z) {
        BlockPos pos = worldPos(x, y, z);
        AABB area = new AABB(pos).inflate(1.5);
        if (!level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(WanderingWitch.class), area,
                entity -> true).isEmpty()) return;
        WanderingWitch witch = GeneratedMod.WANDERING_WITCH.get().create(level.getLevel());
        if (witch == null) return;
        witch.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        witch.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        witch.setPersistenceRequired();
        level.addFreshEntityWithPassengers(witch);
    }

    private BlockPos worldPos(int x, int y, int z) {
        return new BlockPos(boundingBox.minX() + x, boundingBox.minY() + y, boundingBox.minZ() + z);
    }
}
