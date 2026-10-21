package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

/**
 * 魔力布匹、魔法绸缎与通用魔构件的额外获取途径：
 * 宝箱按类别给不同概率，猪灵交易按概率出布匹/构件，大师牧师出售通用魔构件。
 */
public final class AccessoryLootEvents {
    private static final float CLOTH_SPECIAL_CHANCE = 0.0825F;
    private static final float CLOTH_OTHER_CHANCE = 0.0125F;
    private static final float SILK_SPECIAL_CHANCE = 0.0125F;
    private static final float SILK_OTHER_CHANCE = 0.0025F;
    private static final float BARTER_CLOTH_CHANCE = 0.035F;
    private static final float BARTER_COMPONENT_CHANCE = 0.015F;

    public void onLootTableLoad(LootTableLoadEvent event) {
        if (!event.getName().getNamespace().equals("minecraft")) return;
        String path = event.getName().getPath();
        if (path.equals("gameplay/piglin_bartering")) {
            addBarterPools(event);
            return;
        }
        if (!path.startsWith("chests/")) return;
        boolean special = isSpecialChest(path);
        addPool(event, "dimensional_mana_magic_cloth",
                special ? CLOTH_SPECIAL_CHANCE : CLOTH_OTHER_CHANCE,
                MagicEquipment.MAGIC_CLOTH, 1, 1);
        addPool(event, "dimensional_mana_magic_silk",
                special ? SILK_SPECIAL_CHANCE : SILK_OTHER_CHANCE,
                MagicEquipment.MAGIC_SILK, 1, 1);
    }

    /** 村庄、沉船、地牢属于“特殊宝箱”，给更高的开出概率。 */
    private static boolean isSpecialChest(String path) {
        return path.startsWith("chests/village/") || path.startsWith("chests/shipwreck")
                || path.contains("dungeon");
    }

    private void addBarterPools(LootTableLoadEvent event) {
        LootPool cloth = LootPool.lootPool().name("dimensional_mana_barter_cloth")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(BARTER_CLOTH_CHANCE))
                .add(LootItem.lootTableItem(item(MagicEquipment.MAGIC_CLOTH))
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 4.0F))))
                .build();
        event.getTable().addPool(cloth);
        LootPool component = LootPool.lootPool().name("dimensional_mana_barter_component")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(BARTER_COMPONENT_CHANCE))
                .add(LootItem.lootTableItem(item(MagicEquipment.UNIVERSAL_COMPONENT)))
                .build();
        event.getTable().addPool(component);
    }

    private static void addPool(LootTableLoadEvent event, String name, float chance,
                                String itemId, int min, int max) {
        LootPool.Builder pool = LootPool.lootPool().name(name)
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(min == max ? LootItem.lootTableItem(item(itemId))
                        : LootItem.lootTableItem(item(itemId))
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max))));
        event.getTable().addPool(pool.build());
    }

    private static Item item(String id) {
        return GeneratedMod.MOD_ITEMS.get(id).get();
    }

    /** 大师牧师（五级牧师）新增一笔 32 绿宝石换 1 个通用魔构件的交易。 */
    public void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CLERIC) return;
        List<VillagerTrades.ItemListing> master = event.getTrades().get(5);
        if (master == null) return;
        master.add(new BasicItemListing(32, new ItemStack(item(MagicEquipment.UNIVERSAL_COMPONENT)),
                12, 30, 0.05F));
    }

    public AccessoryLootEvents() { }
}
