package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
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
        BlockState chiseled = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState carpet = Blocks.PURPLE_CARPET.defaultBlockState();
        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState();

        // ---------- 地基：半径七格的圆形平台 ----------
        // 旧版是方形切角 + 两条悬在半空的地毯；这里改成真正的圆形露天祭坛，
        // 地毯一律铺在地面方块上，结构里不会再出现任何浮空装饰。
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                int dx = Math.abs(x - 7), dz = Math.abs(z - 7);
                int radius = dx * dx + dz * dz;
                if (radius > 50) continue;
                BlockState floor;
                if (radius >= 40) {
                    // 最外圈抛光深板岩镶边，勾出圆形轮廓。
                    floor = polished;
                } else if (radius >= 33) {
                    // 紧贴镶边的一圈哭泣黑曜石，就是祭坛的“魔法阵”光环。
                    floor = crying;
                } else if (dx <= 1 || dz <= 1) {
                    // 十字大道：四向入口到中央台座的主路。
                    floor = polished;
                } else {
                    floor = (x * 7 + z * 13) % 17 == 0 ? cracked : tile;
                }
                placeBlock(level, floor, x, 4, z, chunkBox);
                fillColumnDown(level, brick, x, 3, z, chunkBox);
            }
        }

        // ---------- 中央三层台座 ----------
        // 第一层削角、第二层内收、第三层托起碑体；由外到内逐层变亮，视觉上像被托起的祭品。
        for (int x = 4; x <= 10; x++) {
            for (int z = 4; z <= 10; z++) {
                boolean edge = x == 4 || x == 10 || z == 4 || z == 10;
                placeBlock(level, edge ? chiseled : polished, x, 5, z, chunkBox);
            }
        }
        generateBox(level, chunkBox, 5, 6, 5, 9, 6, 9, tile, tile, false);
        generateBox(level, chunkBox, 6, 7, 6, 8, 7, 8, brick, brick, false);
        placeBlock(level, crying, 7, 7, 7, chunkBox);
        placeBlock(level, GeneratedMod.MOD_BLOCKS.get("ancient_magic_stele").get().defaultBlockState()
                .setValue(AncientMagicSteleBlock.FACING, Direction.SOUTH), 7, 8, 7, chunkBox);
        // 碑体四角的魂灯，夜里从四面都能看见祭坛的位置。
        placeBlock(level, lantern, 6, 8, 7, chunkBox);
        placeBlock(level, lantern, 8, 8, 7, chunkBox);
        placeBlock(level, lantern, 7, 8, 6, chunkBox);
        placeBlock(level, lantern, 7, 8, 8, chunkBox);

        // ---------- 四向门楼 ----------
        // 每侧入口两侧各立一根短柱，柱顶魂灯；入口中间铺紫色地毯直通台座。
        int[][] southPosts = {{5, 1}, {9, 1}};
        int[][] northPosts = {{5, 13}, {9, 13}};
        int[][] westPosts = {{1, 5}, {1, 9}};
        int[][] eastPosts = {{13, 5}, {13, 9}};
        for (int[][] pair : new int[][][]{southPosts, northPosts, westPosts, eastPosts}) {
            for (int[] post : pair) buildGatePost(level, chunkBox, post[0], post[1], chiseled, brick, lantern);
        }
        carpetRunner(level, chunkBox, carpet, 6, 1, 8, 3);
        carpetRunner(level, chunkBox, carpet, 6, 11, 8, 13);
        carpetRunner(level, chunkBox, carpet, 1, 6, 3, 8);
        carpetRunner(level, chunkBox, carpet, 11, 6, 13, 8);

        // ---------- 四角尖塔 ----------
        // 比门楼高，形成向上的收势；中间夹一段哭泣黑曜石作为“封印纹”。
        int[][] spires = {{2, 2}, {12, 2}, {2, 12}, {12, 12}};
        for (int[] spire : spires) {
            int x = spire[0], z = spire[1];
            placeBlock(level, chiseled, x, 5, z, chunkBox);
            placeBlock(level, brick, x, 6, z, chunkBox);
            placeBlock(level, crying, x, 7, z, chunkBox);
            placeBlock(level, brick, x, 8, z, chunkBox);
            placeBlock(level, chiseled, x, 9, z, chunkBox);
            placeBlock(level, lantern, x, 10, z, chunkBox);
        }

        // ---------- 宝箱 ----------
        // 南北各一口，正对入口、背靠台座，两座祭坛左右对称。
        createLootChest(level, random, chunkBox, 4, 5, 2, Direction.NORTH);
        if (random.nextBoolean()) createLootChest(level, random, chunkBox, 10, 5, 12, Direction.SOUTH);

        int witchCount = 1 + random.nextInt(3);
        int[][] spawnPoints = {{4, 5, 3}, {10, 5, 11}, {3, 5, 10}};
        for (int i = 0; i < witchCount; i++) spawnWitch(level, spawnPoints[i][0], spawnPoints[i][1], spawnPoints[i][2]);
    }

    /** 门柱：底座、柱身、柱头、魂灯，四格高，站在入口两侧。 */
    private void buildGatePost(WorldGenLevel level, BoundingBox box, int x, int z,
                               BlockState chiseled, BlockState brick, BlockState lantern) {
        placeBlock(level, chiseled, x, 5, z, box);
        placeBlock(level, brick, x, 6, z, box);
        placeBlock(level, chiseled, x, 7, z, box);
        placeBlock(level, lantern, x, 8, z, box);
    }

    /** 地毯铺在平台上表面（局部 y=5），只在真正有地面的位置铺，避免再次出现悬空地毡。 */
    private void carpetRunner(WorldGenLevel level, BoundingBox box, BlockState carpet,
                              int fromX, int fromZ, int toX, int toZ) {
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                if (!level.getBlockState(worldPos(x, 4, z)).isSolid()) continue;
                if (!level.getBlockState(worldPos(x, 5, z)).isAir()) continue;
                placeBlock(level, carpet, x, 5, z, box);
            }
        }
    }

    private void createLootChest(WorldGenLevel level, RandomSource random, BoundingBox box,
                                 int x, int y, int z, Direction facing) {
        BlockPos pos = worldPos(x, y, z);
        if (!box.isInside(pos)) return;
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        RandomizableContainer.setBlockEntityLootTable(level, random, pos, ALTAR_LOOT);
    }

    /**
     * 世界生成阶段不能直接刷实体：先排队，等区块真正加载后由 {@link WorldgenSpawnQueue} 补刷。
     * 女巫的最终生成逻辑（含去重、持久化）也在那里。
     */
    private void spawnWitch(WorldGenLevel level, int x, int y, int z) {
        WorldgenSpawnQueue.queueWitch(level.getLevel(), worldPos(x, y, z));
    }

    private BlockPos worldPos(int x, int y, int z) {
        return new BlockPos(boundingBox.minX() + x, boundingBox.minY() + y, boundingBox.minZ() + z);
    }
}
