package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

/**
 * 资源修补系列：魔法修补（Magic Mending）与斗气修补（Battle Qi Mending）。
 *
 * <p>两条附魔都是「每 10 刻结算一次」的被动：把身上的魔素 / 斗气烧成耐久。
 * 结算只针对主手、副手（仅魔法修补）和四件护甲，背包里的东西不参与。</p>
 *
 * <ul>
 *   <li><b>魔法修补</b>：每级每 10 刻消耗 1 点魔素、修 1 点耐久；所在地环境魔素浓度 &gt; 15 时
 *       消耗不变、修复量翻倍。</li>
 *   <li><b>斗气修补</b>：每级每 10 刻消耗 1 点斗气、修 2 点耐久；完美格挡本来会返还的斗气，
 *       改成直接修主手武器（1 点斗气 = 2 点耐久），修不下的零头才回到斗气条。</li>
 * </ul>
 *
 * <p>三条修补（经验修补 / 魔法修补 / 斗气修补）通过 {@code exclusive_set} 标签绝对互斥：
 * 附魔台出其中一个就不会再出另外两个，铁砧也合不到一起。</p>
 *
 * <p>附魔台不把这俩放进 {@code #minecraft:in_enchanting_table}（那样概率不可控、还会和别的附魔
 * 抢位置），而是在玩家真正付经验附魔之后掷一次 1% 的独立判定：命中才往物品上补一条。</p>
 */
public final class ResourceMendingEnchantments {
    public static final ResourceLocation MAGIC_MENDING_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "magic_mending");
    public static final ResourceLocation BATTLE_QI_MENDING_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "battle_qi_mending");

    /** 结算间隔：10 刻 = 半秒。 */
    public static final int INTERVAL_TICKS = 10;
    /** 魔法修补的浓度特化门槛：环境魔素浓度严格大于这个数时修复量 ×2。 */
    public static final double MAGIC_MENDING_RICH_CONCENTRATION = 15.0;
    /** 魔法修补的修复倍数（浓度达标时）。 */
    public static final double MAGIC_MENDING_RICH_MULTIPLIER = 2.0;
    /** 斗气修补每点斗气修多少耐久。完美格挡返还的斗气也按这个比例折算。 */
    public static final int BATTLE_QI_DURABILITY_PER_POINT = 2;
    /** 附魔台独立命中概率。 */
    public static final float ENCHANTING_TABLE_CHANCE = 0.01F;
    /** 附魔台命中的附魔等级范围：1 ~ 4（5 级只通过魔精钢装备结算）。 */
    private static final int TABLE_MAX_LEVEL = 4;

    private static final ResourceKey<Enchantment> MAGIC_MENDING_KEY =
            ResourceKey.create(Registries.ENCHANTMENT, MAGIC_MENDING_ID);
    private static final ResourceKey<Enchantment> BATTLE_QI_MENDING_KEY =
            ResourceKey.create(Registries.ENCHANTMENT, BATTLE_QI_MENDING_ID);

    /** 魔法修补：主手、副手、四件护甲。 */
    private static final EquipmentSlot[] MAGIC_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** 斗气修补：主手、四件护甲（没有副手）。 */
    private static final EquipmentSlot[] BATTLE_QI_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // ---------------------------------------------------------------- 半秒结算

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % INTERVAL_TICKS != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // 浓度一次查询给整轮结算用，别每件装备各查一次。
        double concentration = ManaConcentration.at(level, player.blockPosition());
        // 魔素 / 斗气也只取一次快照，后面的扣除在本地记账，不每件装备重算一次上限。
        ManaData.Snapshot snapshot = ManaData.get(player);
        boolean freeMana = !ManaCheats.manaCostEnabled(player);
        double mana = snapshot.mana();
        int battleQi = snapshot.battleQi();
        repairWithMana(player, level, concentration, mana, freeMana);
        repairWithBattleQi(player, level, battleQi);
    }

    private static void repairWithMana(ServerPlayer player, ServerLevel level, double concentration,
                                       double mana, boolean freeMana) {
        boolean doubled = concentration > MAGIC_MENDING_RICH_CONCENTRATION;
        for (EquipmentSlot slot : MAGIC_SLOTS) {
            if (!freeMana && mana <= 0.0) return;
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isDamaged()) continue;
            int enchantLevel = MojiangSteelEvents.effectiveEnchantmentLevel(stack, MAGIC_MENDING_ID);
            if (enchantLevel <= 0) continue;
            double cost = enchantLevel;
            // 魔素不够这一件就跳过，不修也不扣；其他件继续单独判定。
            if (!freeMana && mana < cost) continue;
            ManaData.drainMana(player, cost);
            if (!freeMana) mana -= cost;
            int repair = doubled
                    ? (int)Math.round(enchantLevel * MAGIC_MENDING_RICH_MULTIPLIER)
                    : enchantLevel;
            applyRepair(level, player, stack, repair, ParticleTypes.ENCHANT);
        }
    }

    private static void repairWithBattleQi(ServerPlayer player, ServerLevel level, int battleQi) {
        for (EquipmentSlot slot : BATTLE_QI_SLOTS) {
            if (battleQi <= 0) return;
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isDamaged()) continue;
            int enchantLevel = MojiangSteelEvents.effectiveEnchantmentLevel(stack, BATTLE_QI_MENDING_ID);
            if (enchantLevel <= 0) continue;
            if (battleQi < enchantLevel) continue;
            // 主动花斗气：走静默扣法，不能因为修装备把斗气扣到 0 就触发「斗气溃散」。
            ManaData.spendBattleQiQuiet(player, enchantLevel);
            battleQi -= enchantLevel;
            applyRepair(level, player, stack, enchantLevel * BATTLE_QI_DURABILITY_PER_POINT,
                    ParticleTypes.ENCHANTED_HIT);
        }
    }

    /** 实际修耐久并补一点对应的粒子；返回真正修掉的点数。 */
    private static int applyRepair(ServerLevel level, ServerPlayer player, ItemStack stack,
                                   int amount, net.minecraft.core.particles.SimpleParticleType particle) {
        int damage = stack.getDamageValue();
        if (damage <= 0 || amount <= 0) return 0;
        int repaired = Math.min(damage, amount);
        stack.setDamageValue(damage - repaired);
        if (particle != null) {
            level.sendParticles(particle,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    3, 0.35, 0.45, 0.35, 0.02);
        }
        return repaired;
    }

    // ---------------------------------------------------------------- 完美格挡联动

    /**
     * 完美格挡的斗气返还改走这里。
     *
     * <p>主手有斗气修补时，返还的斗气不进斗气条，而是按 1 点 = 2 点耐久直接补给主手武器；
     * 武器已经满耐久、或者没有这条附魔时，照旧返回到斗气条。修不下的零头也会退回斗气条，
     * 不会因为「差 1 点耐久」白丢半管返还。</p>
     *
     * @param refundQi 本来要返还的斗气点数
     * @return 实际被换成耐久的斗气点数（剩余部分由调用方加回斗气条）
     */
    public static int convertParryRefundToDurability(ServerPlayer player, int refundQi) {
        if (refundQi <= 0) return 0;
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.isDamaged()) return 0;
        if (MojiangSteelEvents.effectiveEnchantmentLevel(weapon, BATTLE_QI_MENDING_ID) <= 0) return 0;
        int needed = weapon.getDamageValue();
        int qiUsed = Math.min(refundQi, (needed + BATTLE_QI_DURABILITY_PER_POINT - 1) / BATTLE_QI_DURABILITY_PER_POINT);
        int repaired = Math.min(needed, qiUsed * BATTLE_QI_DURABILITY_PER_POINT);
        weapon.setDamageValue(needed - repaired);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    player.getX(), player.getY() + 1.1, player.getZ(), 10, 0.35, 0.4, 0.35, 0.08);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55F, 1.35F);
        }
        return qiUsed;
    }

    // ---------------------------------------------------------------- 附魔台 1%

    /**
     * 附魔台独立命中：玩家真正完成一次附魔后掷 1%。
     *
     * <p>命中时随机给魔法修补或斗气修补（给得起的那条），等级 1~4。物品必须支持这条附魔、
     * 而且身上没有三条修补中的任何一条——互斥靠这里的显式检查 + JSON 里的 exclusive_set 双保险。
     * 事件触发时原版已经把附魔写进槽里的同一个 {@code ItemStack} 对象，直接改它即可，
     * 随后的 {@code setChanged()} 会把新组件同步给客户端。</p>
     */
    public static void onPlayerEnchantItem(PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getEnchantedItem();
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        RandomSource random = player.getRandom();
        if (random.nextFloat() >= ENCHANTING_TABLE_CHANCE) return;
        Holder<Enchantment> magic = enchantment(player.level(), MAGIC_MENDING_KEY);
        Holder<Enchantment> battleQi = enchantment(player.level(), BATTLE_QI_MENDING_KEY);
        boolean magicOk = canApply(stack, magic);
        boolean battleQiOk = canApply(stack, battleQi);
        if (!magicOk && !battleQiOk) return;
        Holder<Enchantment> chosen;
        if (magicOk && battleQiOk) chosen = random.nextBoolean() ? magic : battleQi;
        else chosen = magicOk ? magic : battleQi;
        int level = 1 + random.nextInt(TABLE_MAX_LEVEL);
        stack.enchant(chosen, level);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.6, 0.5, 0.6, 0.6);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7F, 1.4F);
        }
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.resource_mending_gained",
                Enchantment.getFullname(chosen, level)), true);
    }

    private static boolean canApply(ItemStack stack, Holder<Enchantment> enchantment) {
        if (enchantment == null || !enchantment.value().canEnchant(stack)) return false;
        // 原版附魔台本来就会挡住互斥组合，这里是自己补的那一条，必须再查一次。
        return EnchantmentHelper.isEnchantmentCompatible(stack.getEnchantments().keySet(), enchantment);
    }

    // ---------------------------------------------------------------- 村民交易

    /** 大师级图书管理员：两种修补书各一笔交易。 */
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) return;
        List<VillagerTrades.ItemListing> master = event.getTrades().get(5);
        if (master == null) return;
        master.add(new EnchantedBookListing(MAGIC_MENDING_ID, 34, 2, 30));
        master.add(new EnchantedBookListing(BATTLE_QI_MENDING_ID, 34, 2, 30));
    }

    /**
     * 按需生成附魔书的交易条目：书的附魔等级在村民实际生成交易时掷（1~3 级），
     * 不依赖事件触发时有没有注册表可用。
     */
    private record EnchantedBookListing(ResourceLocation enchantmentId, int emeraldCost,
                                        int maxUses, int villagerXp) implements VillagerTrades.ItemListing {
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            Holder<Enchantment> holder = enchantment(trader.level(),
                    ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
            if (holder != null) book.enchant(holder, 1 + random.nextInt(3));
            return new MerchantOffer(new ItemCost(Items.EMERALD, emeraldCost), book,
                    maxUses, villagerXp, 0.05F);
        }
    }

    // ---------------------------------------------------------------- 战利品箱

    /** 魔法修补书：末地城 / 末地船（两者共用 end_city_treasure）。 */
    private static final List<String> MAGIC_MENDING_CHESTS = List.of("chests/end_city_treasure");
    /** 斗气修补书：林地府邸、沉船、海底神殿（原版神殿无箱，连带海底废墟与埋藏宝藏）。 */
    private static final List<String> BATTLE_QI_MENDING_CHESTS = List.of(
            "chests/woodland_mansion", "chests/shipwreck_treasure", "chests/shipwreck_supply",
            "chests/monument", "chests/underwater_ruins", "chests/buried_treasure");

    private static final float MAGIC_MENDING_LOOT_CHANCE = 0.10F;
    private static final float BATTLE_QI_MENDING_LOOT_CHANCE = 0.08F;

    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!event.getName().getNamespace().equals("minecraft")) return;
        String path = event.getName().getPath();
        if (MAGIC_MENDING_CHESTS.contains(path)) {
            addBookPool(event, "dimensional_mana_magic_mending_book", MAGIC_MENDING_KEY,
                    MAGIC_MENDING_LOOT_CHANCE);
        }
        if (BATTLE_QI_MENDING_CHESTS.contains(path)) {
            addBookPool(event, "dimensional_mana_battle_qi_mending_book", BATTLE_QI_MENDING_KEY,
                    BATTLE_QI_MENDING_LOOT_CHANCE);
        }
    }

    private static void addBookPool(LootTableLoadEvent event, String name,
                                    ResourceKey<Enchantment> enchantmentKey, float chance) {
        Holder<Enchantment> holder = event.getRegistries().lookupOrThrow(Registries.ENCHANTMENT)
                .get(enchantmentKey).orElse(null);
        if (holder == null) return;
        LootPool pool = LootPool.lootPool().name(name)
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(new SetEnchantmentsFunction.Builder()
                                .withEnchantment(holder, UniformGenerator.between(1.0F, (float)TABLE_MAX_LEVEL))))
                .build();
        StructureChestLoot.addPool(event.getTable(), pool);
    }

    // ---------------------------------------------------------------- 工具

    private static Holder<Enchantment> enchantment(Level level, ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
    }

    private ResourceMendingEnchantments() { }
}
