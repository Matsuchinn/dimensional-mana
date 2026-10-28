package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.event.LootTableLoadEvent;

import java.util.List;

/**
 * r211 法阵案例残片（五张教学蓝图）的宝箱注入：
 *
 * <ul>
 *   <li><b>古代魔法祭坛</b>：55% 概率出 1~2 个随机残片，同一处祭坛最多 2 个；</li>
 *   <li><b>哥布林聚落</b>：每个宝箱 25% 概率 1 个，同一处聚落最多 2 个；</li>
 *   <li><b>村庄</b>：每个宝箱 40% 概率 1 个，同一处村庄最多 2 个。</li>
 * </ul>
 *
 * <p>「随机」是五张残片等权重抽一张；「最多几份」走
 * {@link BlueprintFragmentLimitCondition}——按结构起点记数，翻遍整个村庄也不会刷出一背包。</p>
 */
public final class BlueprintFragmentLoot {

    private static final String ALTAR_TABLE = "chests/ancient_magic_altar";
    private static final List<String> GOBLIN_TABLES = List.of(
            "chests/goblin_chief", "chests/goblin_tent", "chests/goblin_shaman",
            "chests/goblin_supplies", "chests/goblin_larder");

    public static void addChestLoot(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (name.getNamespace().equals("minecraft")) {
            // 所有村庄系宝箱：village_plains_house / village_weaponsmith / …… 统一 40% + 每处最多 2。
            if (name.getPath().startsWith("village/")) {
                event.getTable().addPool(pool("dimensional_mana_village_blueprint_fragment",
                        ConstantValue.exactly(1), 0.40F, limit("minecraft:village", 2)));
            }
            return;
        }
        if (!name.getNamespace().equals(GeneratedMod.MOD_ID)) return;
        if (name.getPath().equals(ALTAR_TABLE)) {
            // 祭坛：55% 一次，出 1~2 个随机残片（rolls 1~2 就是「1~2 个」，每 roll 各抽一张）。
            event.getTable().addPool(pool("dimensional_mana_altar_blueprint_fragment",
                    UniformGenerator.between(1.0F, 2.0F), 0.55F,
                    limit("dimensional_mana:ancient_magic_altar", 2)));
        } else if (GOBLIN_TABLES.contains(name.getPath())) {
            event.getTable().addPool(pool("dimensional_mana_goblin_blueprint_fragment",
                    ConstantValue.exactly(1), 0.25F, limit("dimensional_mana:goblin_camp", 2)));
        }
    }

    /** 一份残片池：五张教学蓝图等权重抽一张，整池过「概率 + 每处限额」两道条件。 */
    private static LootPool pool(String name, NumberProvider rolls, float chance, LootItemCondition.Builder limit) {
        LootPoolEntryContainer.Builder<?>[] entries = new LootPoolEntryContainer.Builder<?>[5];
        String[] items = {
                MagicCircleBlueprints.SPARK_ITEM, MagicCircleBlueprints.FIREBALL_ITEM,
                MagicCircleBlueprints.HEALING_ITEM, MagicCircleBlueprints.GUARD_ITEM,
                MagicCircleBlueprints.MINE_ITEM};
        for (int i = 0; i < items.length; i++) {
            Item item = GeneratedMod.MOD_ITEMS.get(items[i]) == null ? null
                    : GeneratedMod.MOD_ITEMS.get(items[i]).get();
            if (item == null) {
                // 物品没注册上（理论上不会）：整池放弃，别让战利品表挂上空气条目。
                return LootPool.lootPool().name(name).setRolls(ConstantValue.exactly(0)).build();
            }
            entries[i] = LootItem.lootTableItem(item).setWeight(1);
        }
        LootPool.Builder builder = LootPool.lootPool().name(name).setRolls(rolls)
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .when(limit);
        for (LootPoolEntryContainer.Builder<?> entry : entries) builder.add(entry);
        return builder.build();
    }

    private static LootItemCondition.Builder limit(String structureId, int max) {
        return () -> new BlueprintFragmentLimitCondition(structureId, max);
    }

    private BlueprintFragmentLoot() {}
}
