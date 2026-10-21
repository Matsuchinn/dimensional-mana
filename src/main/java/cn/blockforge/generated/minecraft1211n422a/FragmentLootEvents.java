package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** 把碎片加入原版宝箱表，因此 Lootr 复制原战利品表时也能按玩家独立生成。 */
public final class FragmentLootEvents {
    public void addChestLoot(LootTableLoadEvent event) {
        if (!event.getName().getNamespace().equals("minecraft") || !event.getName().getPath().startsWith("chests/")) return;
        addGradePool(event, "lesser", 0.058F);
        addGradePool(event, "medium", 0.015F);
        addGradePool(event, "advanced", 0.005F);
        LootPool brokenCasket = LootPool.lootPool().name("dimensional_mana_cracked_ancient_casket")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(0.001F))
                .add(LootItem.lootTableItem(GeneratedMod.MOD_ITEMS.get("cracked_ancient_casket").get()))
                .build();
        event.getTable().addPool(brokenCasket);
    }

    private static void addGradePool(LootTableLoadEvent event, String grade, float chance) {
        LootPool.Builder pool = LootPool.lootPool().name("dimensional_mana_" + grade + "_fragments")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance));
        for (String id : MagicFragments.idsForGrade(grade)) {
            pool.add(LootItem.lootTableItem(GeneratedMod.MOD_ITEMS.get(id).get()));
        }
        event.getTable().addPool(pool.build());
    }

    public void addWitchDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity instanceof IronGolem && entity.getRandom().nextFloat() < 0.0015F) {
            addDrop(event, entity, new net.minecraft.world.item.ItemStack(
                    GeneratedMod.MOD_ITEMS.get("rough_magic_component").get()));
        }
        if (entity instanceof Warden && entity.getRandom().nextFloat() < 0.20F) {
            addDrop(event, entity, new net.minecraft.world.item.ItemStack(
                    GeneratedMod.MOD_ITEMS.get("cracked_ancient_casket").get()));
        }
        if (!(entity instanceof Witch witch)) return;
        // 游离女巫的掉落（原版女巫物品 + 碎片 / 药水 / 魔法绸缎）已整理进数据包战利品表
        // data/dimensional_mana/loot_table/entities/wandering_witch.json，JEI 附属
        // Just Enough Resources 才能在 JEI 里读到；这里不要再追加，避免掉落翻倍。
        if (witch instanceof WanderingWitch) return;
        rollDrop(event, witch, "lesser", 0.085F, 1, 3);
        rollDrop(event, witch, "medium", 0.0095F, 1, 2);
        rollDrop(event, witch, "advanced", 0.0002F, 1, 1);
    }

    private static void rollDrop(LivingDropsEvent event, Witch witch, String grade, float chance, int min, int max) {
        if (witch.getRandom().nextFloat() >= chance) return;
        addDrop(event, witch, MagicFragments.randomStack(grade, min, max, witch.getRandom()));
    }

    private static void addDrop(LivingDropsEvent event, LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    public FragmentLootEvents() { }
}
