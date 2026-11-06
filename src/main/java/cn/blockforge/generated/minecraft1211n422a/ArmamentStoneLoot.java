package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * 武备强化石的全部获取途径。
 *
 * <ul>
 *   <li>普通宝箱：2% 出现一颗；</li>
 *   <li>幽匿古城宝箱（含冰窖）：40% 出现一颗，同一处古城最多四颗；</li>
 *   <li>凋零 / 魔蚀凋零：20% 掉一颗；</li>
 *   <li>监守者：8% 掉一颗；</li>
 *   <li>冒险者 T2 交易池里也能换到（见 {@link AdventurerTrades}）。</li>
 * </ul>
 *
 * <p>宝箱那一份是往结构宝箱表里加池子（原版与其它模组新增结构都算，见 {@link StructureChestLoot}），
 * 所以 Lootr 这类按玩家复制战利品表的模组也会照常生成；
 * 古城限额复用 {@link BlueprintFragmentLimitCondition}（结构 id + 上限都是字段，一份条件通吃）。</p>
 */
public final class ArmamentStoneLoot {
    public static final String ITEM_ID = ArmamentForge.ENHANCE_STONE;

    private static final String ANCIENT_CITY = "chests/ancient_city";
    private static final String ANCIENT_CITY_ICE_BOX = "chests/ancient_city_ice_box";
    private static final String ANCIENT_CITY_ID = "minecraft:ancient_city";
    /** 一处幽匿古城最多几颗强化石。 */
    public static final int MAX_PER_ANCIENT_CITY = 4;

    private static final float CHEST_CHANCE = 0.02F;
    private static final float ANCIENT_CITY_CHANCE = 0.40F;
    private static final float WITHER_CHANCE = 0.20F;
    private static final float WARDEN_CHANCE = 0.08F;

    public static void addChestLoot(LootTableLoadEvent event) {
        String path = event.getName().getPath();
        // 结构宝箱通用池：原版与其它模组新增结构的宝箱都算（见 StructureChestLoot）。
        if (!StructureChestLoot.isStructureChest(event.getName())) return;
        boolean ancientCity = event.getName().getNamespace().equals("minecraft")
                && (path.equals(ANCIENT_CITY) || path.equals(ANCIENT_CITY_ICE_BOX));
        LootPool.Builder pool = LootPool.lootPool()
                .name("dimensional_mana_armament_enhancement_stone")
                .setRolls(ConstantValue.exactly(1));
        if (ancientCity) {
            pool.add(LootItem.lootTableItem(stone())
                    .when(LootItemRandomChanceCondition.randomChance(ANCIENT_CITY_CHANCE))
                    .when(() -> new BlueprintFragmentLimitCondition(ANCIENT_CITY_ID, MAX_PER_ANCIENT_CITY)));
        } else {
            pool.add(LootItem.lootTableItem(stone())
                    .when(LootItemRandomChanceCondition.randomChance(CHEST_CHANCE)));
        }
        StructureChestLoot.addPool(event.getTable(), pool.build());
    }

    /** 凋零（含魔蚀凋零，它是凋零的子类）20%、监守者 8%，各掉一颗。 */
    public static void addEntityDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        float chance = entity instanceof WitherBoss ? WITHER_CHANCE
                : entity instanceof Warden ? WARDEN_CHANCE : 0.0F;
        if (chance <= 0.0F || entity.getRandom().nextFloat() >= chance) return;
        ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY() + 1.0D, entity.getZ(),
                new ItemStack(stone()));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    private static Item stone() {
        return GeneratedMod.MOD_ITEMS.get(ITEM_ID).get();
    }

    private ArmamentStoneLoot() { }
}
