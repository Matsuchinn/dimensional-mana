package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * 远古魔导残片的全部获取途径。
 *
 * <ul>
 *   <li>幽匿古城宝箱：4% 生成一枚，同一处古城最多两枚；</li>
 *   <li>远古守卫者：18% 掉落一枚；</li>
 *   <li>末影龙：必定掉落四枚；</li>
 *   <li>末地船宝箱：1% 生成 1～2 枚。</li>
 * </ul>
 */
public final class AncientFragmentLoot {
    public static final String ITEM_ID = "ancient_magic_fragment";
    private static final String ANCIENT_CITY_CHEST = "chests/ancient_city";
    private static final String END_SHIP_CHEST = "chests/end_city_treasure";

    public static void addChestLoot(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!name.getNamespace().equals("minecraft")) return;
        if (name.getPath().equals(ANCIENT_CITY_CHEST)) {
            event.getTable().addPool(LootPool.lootPool()
                    .name("dimensional_mana_ancient_magic_fragment")
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(fragment())
                            .when(LootItemRandomChanceCondition.randomChance(0.04F))
                            .when(AncientFragmentLimitCondition::new))
                    .build());
        } else if (name.getPath().equals(END_SHIP_CHEST)) {
            event.getTable().addPool(LootPool.lootPool()
                    .name("dimensional_mana_end_ship_ancient_magic_fragment")
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(fragment())
                            .when(LootItemRandomChanceCondition.randomChance(0.01F))
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F))))
                    .build());
        }
    }

    public static void addEntityDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity instanceof ElderGuardian && entity.getRandom().nextFloat() < 0.18F) {
            addDrop(event, entity, new ItemStack(fragment(), 1));
        } else if (entity instanceof EnderDragon) {
            addDrop(event, entity, new ItemStack(fragment(), 4));
        }
    }

    private static net.minecraft.world.item.Item fragment() {
        return GeneratedMod.MOD_ITEMS.get(ITEM_ID).get();
    }

    private static void addDrop(LivingDropsEvent event, LivingEntity entity, ItemStack stack) {
        ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY() + 1.0, entity.getZ(), stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    private AncientFragmentLoot() { }
}
