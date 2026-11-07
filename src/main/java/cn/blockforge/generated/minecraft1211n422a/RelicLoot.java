package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * 新饰品的全部获取途径：宝箱、生物掉落，以及无名护符的骷髅计数。
 *
 * <p>宝箱走 {@link LootTableLoadEvent} 往结构宝箱表里追加独立池子，互相不影响概率；
 * 生物掉落走 {@link LivingDropsEvent}。表格里的概率就是需求里写的百分比。</p>
 *
 * <p>r333：判定统一交给 {@link StructureChestLoot}，原版宝箱与其它模组新增结构的通用宝箱都算。
 * 原版那几张有名有姓的表照旧按主题出对应饰品；其它模组的结构没有固定主题，
 * 只给一份通用的「迷失护符」，另外沼泽群系里的任何宝箱都照旧 10% 出剧毒指环。</p>
 */
public final class RelicLoot {
    private static final String NAMELESS_KILLS = "DimensionalManaNamelessCharmKills";
    private static final int NAMELESS_KILL_TARGET = 100;

    public static void addChestLoot(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        String path = name.getPath();
        boolean ownMod = name.getNamespace().equals(GeneratedMod.MOD_ID);
        // 本模组自己的表：只给远古祭坛挂一枚抉择戒指，其余内容是自己设计的。
        if (ownMod) {
            if (path.equals("chests/ancient_magic_altar")) addPool(event, "choice_ring", 0.25F, false);
            return;
        }
        if (!StructureChestLoot.isForeignStructureChest(name)) return;
        boolean vanilla = name.getNamespace().equals("minecraft");

        if (vanilla) {
            // 村庄与沉船：走私之戒 10%
            if (path.startsWith("chests/village/") || path.equals("chests/shipwreck_treasure")) {
                addPool(event, "smuggler_ring", 0.10F, false);
            }
            if (path.equals("chests/igloo_chest")) addPool(event, "moonlight_blessing", 0.12F, false);
            if (path.equals("chests/desert_pyramid")) addPool(event, "blazing_blessing", 0.08F, false);
            if (path.equals("chests/abandoned_mineshaft") || path.equals("chests/simple_dungeon")) {
                addPool(event, "lost_charm", 0.08F, false);
            }
            if (path.equals("chests/nether_bridge")) addPool(event, "bloodthirst_brooch", 0.08F, false);
            if (path.equals("chests/ancient_city")) addPool(event, "omniscient_page", 0.33F, false);
        } else {
            // 其它模组新增的结构：没有固定主题，给一份通用的迷失护符（捡到就是赚到的那种）。
            addPool(event, "lost_charm", 0.06F, false);
        }
        // 剧毒指环：沼泽群系里任何结构的宝箱 10%
        addPool(event, "venom_ring", 0.10F, true);
    }

    private static void addPool(LootTableLoadEvent event, String itemId, float chance, boolean swamp) {
        LootPool.Builder pool = LootPool.lootPool().name("dimensional_mana_relic_" + itemId)
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance));
        if (swamp) pool = pool.when(SwampBiomeCondition::new);
        pool = pool.add(LootItem.lootTableItem(item(itemId)));
        StructureChestLoot.addPool(event.getTable(), pool.build());
    }

    public static void addEntityDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        // 每一件掉落独立掷骰：同一只生物可以同时掉多件，不会因为前面的分支
        // 用掉同一个随机数而让后面的掉落永远拿不到（奥萝菈、净化核心以前就是这样失效的）。
        if (entity instanceof Goblin goblin && goblin.variant() == GoblinVariant.CHIEF
                && entity.getRandom().nextFloat() < 0.15F) {
            drop(event, entity, "green_amber_ring");
        }
        if (entity instanceof ElementalSlime slime && slime.variant() == ElementalSlimeVariant.POISON
                && entity.getRandom().nextFloat() < 0.01F) {
            drop(event, entity, "venom_ring");
        }
        if ((entity instanceof IronGolem || entity instanceof Ravager) && entity.getRandom().nextFloat() < 0.10F) {
            drop(event, entity, "strength_charm");
        }
        if (entity instanceof Phantom && !entity.level().isDay() && entity.getRandom().nextFloat() < 0.05F) {
            drop(event, entity, "moonlight_blessing");
        }
        if (entity instanceof Goblin goblin && goblin.variant() == GoblinVariant.SHAMAN
                && entity.getRandom().nextFloat() < 0.10F) {
            drop(event, entity, "risk_bracelet");
        }
        if (entity instanceof Witch && entity.getRandom().nextFloat() < 0.15F) {
            drop(event, entity, "risk_bracelet");
        }
        if (entity instanceof PiglinBrute && entity.getRandom().nextFloat() < 0.01F) {
            drop(event, entity, "bloodthirst_brooch");
        }
        if (entity instanceof Witch && entity.getRandom().nextFloat() < 0.05F) {
            drop(event, entity, "purification_core");
        }
        if (entity instanceof ElderGuardian && entity.getRandom().nextFloat() < 0.05F) {
            drop(event, entity, "purification_core");
        }
        if (entity instanceof WitherBoss) {
            drop(event, entity, "seraphis_charm");
        }
        if (entity instanceof EnderDragon) {
            drop(event, entity, "cosetas_charm");
        }
        if (entity instanceof ElderGuardian && entity.getRandom().nextFloat() < 0.05F) {
            drop(event, entity, "aurora_charm");
        }
        if (entity instanceof Warden && entity.getRandom().nextFloat() < 0.10F) {
            drop(event, entity, "seal_blindfold");
        }
        if (entity instanceof ElementalSlime slime && slime.variant() == ElementalSlimeVariant.LIGHT
                && slime.tier() == ElementalSlimeTier.ELITE && entity.getRandom().nextFloat() < 0.10F) {
            drop(event, entity, "radiant_core");
        }
        rollNamelessCharm(event);
    }

    /**
     * 无名护符：玩家每击杀 100 个处于「招架破绽」状态的生物，立即必定掉落 1 个。
     *
     * <p>计数写在击杀者的持久数据中，因此跨维度、跨死亡和重进游戏都不会清零；
     * 破绽状态由完美招架挂上的 {@link NewSpellContent#PARRY_VULNERABLE} 标记判断。</p>
     */
    private static void rollNamelessCharm(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(NewSpellContent.PARRY_VULNERABLE)) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        CompoundTag data = killer.getPersistentData();
        int kills = data.getInt(NAMELESS_KILLS) + 1;
        if (kills >= NAMELESS_KILL_TARGET) {
            data.putInt(NAMELESS_KILLS, kills - NAMELESS_KILL_TARGET);
            drop(event, entity, "nameless_charm");
        } else {
            data.putInt(NAMELESS_KILLS, kills);
        }
    }

    private static void drop(LivingDropsEvent event, LivingEntity entity, String itemId) {
        ItemStack stack = new ItemStack(item(itemId));
        ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY() + 1.0, entity.getZ(), stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    private static Item item(String id) {
        return GeneratedMod.MOD_ITEMS.get(id).get();
    }

    private RelicLoot() { }
}
