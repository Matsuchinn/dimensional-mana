package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/** 掉落概率按每级抢夺独立乘 1.1，而不是原版随机增加掉落数量。 */
public record ElementalSlimeLootChance(float chance) implements LootItemCondition {
    public static final MapCodec<ElementalSlimeLootChance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("chance").forGetter(ElementalSlimeLootChance::chance)
    ).apply(instance, ElementalSlimeLootChance::new));

    @Override
    public LootItemConditionType getType() { return GeneratedMod.ELEMENTAL_SLIME_LOOT_CHANCE.get(); }

    @Override
    public boolean test(LootContext context) {
        LivingEntity killer = context.getParamOrNull(LootContextParams.ATTACKING_ENTITY) instanceof LivingEntity living
                ? living : null;
        int looting = 0;
        if (killer != null) {
            var lookup = context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            looting = EnchantmentHelper.getEnchantmentLevel(lookup.getOrThrow(Enchantments.LOOTING), killer);
        }
        return context.getRandom().nextFloat() < chance * (1.0F + 0.10F * looting);
    }
}
