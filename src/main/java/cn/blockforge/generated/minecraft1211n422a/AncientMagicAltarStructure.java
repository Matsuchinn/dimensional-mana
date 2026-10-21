package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/** 约一整个区块大小的露天古代祭坛。 */
public final class AncientMagicAltarStructure extends Structure {
    public static final MapCodec<AncientMagicAltarStructure> CODEC = simpleCodec(AncientMagicAltarStructure::new);

    public AncientMagicAltarStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
            BlockPos center = new BlockPos(context.chunkPos().getMiddleBlockX(),
                    context.chunkGenerator().getBaseHeight(context.chunkPos().getMiddleBlockX(),
                            context.chunkPos().getMiddleBlockZ(), Heightmap.Types.WORLD_SURFACE_WG,
                            context.heightAccessor(), context.randomState()),
                    context.chunkPos().getMiddleBlockZ());
            builder.addPiece(new AncientMagicAltarPiece(center));
        });
    }

    @Override
    public StructureType<?> type() {
        return GeneratedMod.ANCIENT_MAGIC_ALTAR_STRUCTURE_TYPE.get();
    }
}
