package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

/** 随机选三档营地，占地分别为 3×2、4×3、5×4 个区块。 */
public final class GoblinCampStructure extends Structure {
    public static final MapCodec<GoblinCampStructure> CODEC = simpleCodec(GoblinCampStructure::new);
    public GoblinCampStructure(StructureSettings settings) { super(settings); }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // 四成小型、三成中型、三成大型；以前是五/三/二，再叠上偏袒小型的选址门槛，
        // 中型和大型几乎见不到，开新档一串全是小型。
        int roll = context.random().nextInt(10);
        GoblinCampLayout.Size size = roll < 4 ? GoblinCampLayout.Size.SMALL
                : roll < 7 ? GoblinCampLayout.Size.MEDIUM : GoblinCampLayout.Size.LARGE;
        long seed = context.random().nextLong();
        // 以区块边界对齐总占地，确保小型确实只占 6 个区块，不因中心偏移多占一圈。
        int originX = context.chunkPos().getMinBlockX() - (size.width / 32) * 16;
        int originZ = context.chunkPos().getMinBlockZ() - (size.depth / 32) * 16;
        ChunkGenerator generator = context.chunkGenerator();
        int grid = GoblinCampPiece.GRID;
        int[] heights = new int[grid * grid];
        int wet = 0;
        for (int gz = 0; gz < grid; gz++) for (int gx = 0; gx < grid; gx++) {
            int x = originX + Math.round(gx * (size.width - 1F) / (grid - 1));
            int z = originZ + Math.round(gz * (size.depth - 1F) / (grid - 1));
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState());
            // 水面和淹没点只记数不直接否掉：整片水域仍会被拒，零星小水洼交给营地自己整平。
            if (surface - floor > 1 || floor <= generator.getSeaLevel()) wet++;
            // 高度图返回地表上方空气：减一才是真正铺地的方块高度。
            heights[gz * grid + gx] = surface - 1;
        }
        // 陡崖、绝壁和整片水域不建营地；缓坡、浅洼和小水洼保留。
        if (!GoblinCampTerrainGate.accepts(heights, grid, wet, size)) return Optional.empty();
        BlockPos origin = new BlockPos(originX, heights[(grid * grid) / 2], originZ);
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
                builder -> builder.addPiece(new GoblinCampPiece(origin, heights, size, seed)));
    }
    @Override
    public StructureType<?> type() { return GeneratedMod.GOBLIN_CAMP_STRUCTURE_TYPE.get(); }
}
