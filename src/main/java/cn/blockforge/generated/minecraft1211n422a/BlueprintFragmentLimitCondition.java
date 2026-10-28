package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;

/**
 * r211 法阵案例残片的「每处结构限额」条件：村庄最多 2 份、哥布林聚落最多 2 份、
 * 古代魔法祭坛最多 2 份。
 *
 * <p>战利品表本身不知道箱子属于哪一处结构，这里用开箱位置反查结构起点，
 * 再交给 {@link BlueprintFragmentSavedData} 记数——和远古魔导残片的
 * {@link AncientFragmentLimitCondition} 同一套路，但结构与上限做成字段，一份条件服务三处。</p>
 */
public final class BlueprintFragmentLimitCondition implements LootItemCondition {

    private final String structureId;
    private final int maxPerStructure;

    public static final MapCodec<BlueprintFragmentLimitCondition> CODEC =
            RecordCodecBuilder.mapCodec(builder -> builder.group(
                    com.mojang.serialization.Codec.STRING.fieldOf("structure").forGetter(c -> c.structureId),
                    com.mojang.serialization.Codec.INT.fieldOf("max").forGetter(c -> c.maxPerStructure)
            ).apply(builder, BlueprintFragmentLimitCondition::new));

    public BlueprintFragmentLimitCondition(String structureId, int maxPerStructure) {
        this.structureId = structureId;
        this.maxPerStructure = Math.max(1, maxPerStructure);
    }

    @Override
    public LootItemConditionType getType() {
        return GeneratedMod.BLUEPRINT_FRAGMENT_LIMIT_CONDITION.get();
    }

    @Override
    public boolean test(LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return false;
        ServerLevel level = context.getLevel();
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
                ResourceLocation.parse(structureId));
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        if (structure == null) return false;
        StructureStart start = level.structureManager().getStructureAt(BlockPos.containing(origin), structure);
        if (start == null || !start.isValid()) return false;
        return BlueprintFragmentSavedData.get(level)
                .tryClaim(structureId + "@" + start.getChunkPos().toLong(), maxPerStructure);
    }
}
