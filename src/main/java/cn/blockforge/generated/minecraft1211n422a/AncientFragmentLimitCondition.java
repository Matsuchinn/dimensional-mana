package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
 * 幽匿古城宝箱的残片上限条件：同一处古城最多生成两枚远古魔导残片。
 *
 * <p>战利品表本身拿不到箱子所在的结构，这里用开箱位置反查古城的结构起点，
 * 再交给 {@link AncientFragmentSavedData} 记数。</p>
 */
public final class AncientFragmentLimitCondition implements LootItemCondition {
    private static final ResourceKey<Structure> ANCIENT_CITY = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.withDefaultNamespace("ancient_city"));
    public static final MapCodec<AncientFragmentLimitCondition> CODEC = Codec.EMPTY.xmap(
            unit -> new AncientFragmentLimitCondition(),
            condition -> com.mojang.datafixers.util.Unit.INSTANCE);

    @Override
    public LootItemConditionType getType() {
        return GeneratedMod.ANCIENT_FRAGMENT_LIMIT_CONDITION.get();
    }

    @Override
    public boolean test(LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return false;
        ServerLevel level = context.getLevel();
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ANCIENT_CITY);
        if (structure == null) return false;
        StructureStart start = level.structureManager().getStructureAt(BlockPos.containing(origin), structure);
        if (start == null || !start.isValid()) return false;
        return AncientFragmentSavedData.get(level).tryClaim(start.getChunkPos().toLong());
    }
}
