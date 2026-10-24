package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;

/**
 * 「沼泽群系的结构宝箱」判定：箱子所在位置必须落在沼泽或红树林沼泽。
 *
 * <p>战利品表加载时还看不到世界，所以没法按结构名字挑表；这里改成开箱时读箱子坐标的生物群系，
 * 再把同一个条件挂到所有 {@code chests/} 表上，凡是在沼泽里开的箱子都能开出剧毒指环。</p>
 *
 * <p>群系用 id 里是否带 {@code swamp} 判断，不依赖群系标签，模组群系同样适用。</p>
 */
public final class SwampBiomeCondition implements LootItemCondition {
    public static final MapCodec<SwampBiomeCondition> CODEC = Codec.EMPTY.xmap(
            unit -> new SwampBiomeCondition(),
            condition -> com.mojang.datafixers.util.Unit.INSTANCE);

    @Override
    public LootItemConditionType getType() {
        return GeneratedMod.SWAMP_BIOME_CONDITION.get();
    }

    @Override
    public boolean test(LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return false;
        ServerLevel level = context.getLevel();
        Holder<Biome> biome = level.getBiome(BlockPos.containing(origin));
        return biome.unwrapKey()
                .map(key -> key.location().getPath().contains("swamp"))
                .orElse(false);
    }

    public SwampBiomeCondition() { }
}
