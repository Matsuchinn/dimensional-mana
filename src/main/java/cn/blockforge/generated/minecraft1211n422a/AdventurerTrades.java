package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 冒险者交易池：五层品级（初级 / 中级 / 高级 / 稀有 / 传说），
 * 每层按权重抽条目、条目内部再按权重抽，一名冒险者一次刷新共 10 笔交易。
 *
 * <p>刷新时机：冒险者生成时抽一次；走到村庄住下之后，每过一天（24000 刻）
 * 重新抽一次并重置每一笔的次数。</p>
 *
 * <p>交易数据只存在服务器端的冒险者身上（{@link Adventurer#tradeOffers()}），
 * 客户端只是把服务端同步过来的物品和剩余次数画出来，点“成品”时由服务端校验材料并扣货。</p>
 */
public final class AdventurerTrades {
    /** 一名冒险者同时挂着多少笔交易。 */
    public static final int OFFER_COUNT = 10;

    private static final int WEIGHT_HIGH = 6;
    private static final int WEIGHT_MEDIUM = 3;
    private static final int WEIGHT_LOW = 1;

    /** 交易层：概率、单笔次数区间、品级颜色。 */
    public enum Tier {
        // 10 笔交易的期望：初级约 4 笔、中级约 3 笔、高级约 2 笔、稀有约 0.8 笔、传说约 0.2 笔。
        T1(0.400F, 5, 8, 0xFF9AA0A8, ChatFormatting.GRAY),
        T2(0.300F, 4, 6, 0xFF5FBF6B, ChatFormatting.GREEN),
        T3(0.200F, 2, 4, 0xFF4F8BE8, ChatFormatting.BLUE),
        T4(0.080F, 1, 2, 0xFFB366E8, ChatFormatting.LIGHT_PURPLE),
        T5(0.020F, 1, 1, 0xFFE8B23A, ChatFormatting.GOLD);

        private final float chance;
        private final int minUses;
        private final int maxUses;
        private final int color;
        private final ChatFormatting formatting;

        Tier(float chance, int minUses, int maxUses, int color, ChatFormatting formatting) {
            this.chance = chance;
            this.minUses = minUses;
            this.maxUses = maxUses;
            this.color = color;
            this.formatting = formatting;
        }

        public float chance() { return chance; }
        public int color() { return color; }
        public ChatFormatting formatting() { return formatting; }

        public int rollUses(RandomSource random) {
            return minUses + random.nextInt(maxUses - minUses + 1);
        }

        public String translationKey() {
            return "gui.dimensional_mana.adventurer_trade.tier." + name().toLowerCase(Locale.ROOT);
        }

        public static Tier byName(String name) {
            for (Tier tier : values()) if (tier.name().equals(name)) return tier;
            return null;
        }

        public static Tier byOrdinal(int ordinal) {
            Tier[] tiers = values();
            return ordinal >= 0 && ordinal < tiers.length ? tiers[ordinal] : T1;
        }
    }

    /** 一笔具体交易：标价（1～2 种物品）、成品、总次数与已用次数。 */
    public static final class Offer {
        private final Tier tier;
        private final List<ItemStack> costs;
        private final ItemStack result;
        private final int maxUses;
        private int uses;

        public Offer(Tier tier, List<ItemStack> costs, ItemStack result, int maxUses, int uses) {
            this.tier = tier;
            this.costs = List.copyOf(costs);
            this.result = result.copy();
            this.maxUses = Math.max(1, maxUses);
            this.uses = Math.max(0, Math.min(this.maxUses, uses));
        }

        public Tier tier() { return tier; }
        public List<ItemStack> costs() { return costs; }
        public ItemStack cost(int index) { return index >= 0 && index < costs.size() ? costs.get(index) : ItemStack.EMPTY; }
        public ItemStack result() { return result; }
        public int maxUses() { return maxUses; }
        public int uses() { return uses; }
        public int remaining() { return Math.max(0, maxUses - uses); }
        public boolean soldOut() { return remaining() <= 0; }

        public void addUse() { uses = Math.min(maxUses, uses + 1); }

        public CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Tier", tier.name());
            ListTag costList = new ListTag();
            for (ItemStack cost : costs) if (!cost.isEmpty()) costList.add(cost.save(registries));
            tag.put("Costs", costList);
            tag.put("Result", result.save(registries));
            tag.putInt("MaxUses", maxUses);
            tag.putInt("Uses", uses);
            return tag;
        }

        public static Offer load(HolderLookup.Provider registries, CompoundTag tag) {
            Tier tier = Tier.byName(tag.getString("Tier"));
            if (tier == null) return null;
            List<ItemStack> costs = new ArrayList<>(2);
            ListTag costList = tag.getList("Costs", Tag.TAG_COMPOUND);
            for (int i = 0; i < costList.size(); i++) {
                ItemStack cost = ItemStack.parseOptional(registries, costList.getCompound(i));
                if (!cost.isEmpty()) costs.add(cost);
            }
            ItemStack result = ItemStack.parseOptional(registries, tag.getCompound("Result"));
            if (costs.isEmpty() || result.isEmpty()) return null;
            return new Offer(tier, costs, result, Math.max(1, tag.getInt("MaxUses")), tag.getInt("Uses"));
        }
    }

    /** 条目生成出来的一笔“草稿”：标价与成品，次数由抽取时按层级随机。 */
    private record Draft(List<ItemStack> costs, ItemStack result) { }

    @FunctionalInterface
    private interface Maker {
        Draft make(ServerLevel level, RandomSource random);
    }

    private record Entry(Tier tier, int weight, Maker maker) { }

    private static final List<Entry> CATALOG = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static ResourceKey<Enchantment>[] enchants(ResourceKey<Enchantment>... keys) {
        return keys;
    }

    private static final ResourceKey<Enchantment>[] LOW_BOOKS = enchants(
            Enchantments.PROTECTION, Enchantments.SHARPNESS, Enchantments.EFFICIENCY, Enchantments.UNBREAKING);
    private static final ResourceKey<Enchantment>[] WEAPON_ENCHANTS = enchants(
            Enchantments.SHARPNESS, Enchantments.FIRE_ASPECT, Enchantments.UNBREAKING,
            Enchantments.MENDING, Enchantments.LOOTING);
    private static final ResourceKey<Enchantment>[] ARMOR_ENCHANTS = enchants(
            Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.THORNS, Enchantments.MENDING);

    static {
        // ── 初级交易（T1）：下级 / 中级魔晶换基础资源 ─────────────────────────────
        add(Tier.T1, WEIGHT_HIGH, (level, random) -> draft(
                modItem("lesser_mana_crystal", 2), modItem(randomElementFragment(random, "lesser"), 1)));
        add(Tier.T1, WEIGHT_HIGH, (level, random) -> draft(
                modItem("lesser_mana_crystal", 2), modItem(randomSchoolFragment(random, "lesser"), 1)));
        add(Tier.T1, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("lesser_mana_crystal", 3), modItem("lesser_arcane_fragment", 1)));
        add(Tier.T1, WEIGHT_HIGH, (level, random) -> draft(
                modItem("lesser_mana_crystal", 1), modItem("lesser_mana_crystal_dust", 2)));
        add(Tier.T1, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("lesser_mana_crystal", 1), modItem("lapis_dust", 4)));
        add(Tier.T1, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("medium_mana_crystal", 1), modItem("amethyst_dust", 3)));
        add(Tier.T1, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("medium_mana_crystal", 1), modItem(MagicEquipment.MAGIC_CLOTH, 2)));
        add(Tier.T1, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("lesser_mana_crystal", 2), modItem("lesser_mana_potion", 2)));
        add(Tier.T1, WEIGHT_LOW, (level, random) -> draft(
                modItem("lesser_mana_crystal", 3), modItem("blank_spell_scroll", 1)));
        add(Tier.T1, WEIGHT_LOW, (level, random) -> draft(
                costs(modItem("lesser_mana_crystal", 4), vanilla(Items.REDSTONE, 2)), modItem("mana_detector", 1)));

        // ── 中级交易（T2）：中级 / 高级魔晶换中级资源与初级饰品 ─────────────────
        add(Tier.T2, WEIGHT_HIGH, (level, random) -> draft(
                modItem("medium_mana_crystal", 2), modItem(randomElementFragment(random, "medium"), 1)));
        add(Tier.T2, WEIGHT_HIGH, (level, random) -> draft(
                modItem("medium_mana_crystal", 2), modItem(randomSchoolFragment(random, "medium"), 1)));
        add(Tier.T2, WEIGHT_MEDIUM, (level, random) -> {
            String elementId = randomElementId(random, true);
            String fragmentId = MagicEquipment.ARCANE.equals(elementId) ? "lesser_arcane_fragment"
                    : ElementType.byId(elementId).fragmentId("lesser");
            return draft(costs(modItem("medium_mana_crystal", 4), modItem(fragmentId, 3)),
                    modItem(MagicEquipment.crystalId(elementId), 1));
        });
        add(Tier.T2, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("medium_mana_crystal", 8),
                modItem(random.nextBoolean() ? "wisdom_ring" : "magic_amulet", 1)));
        add(Tier.T2, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("medium_mana_crystal", 6), modItem(MagicEquipment.MAGIC_SILK, 1)));
        add(Tier.T2, WEIGHT_LOW, (level, random) -> draft(
                costs(modItem("advanced_mana_crystal", 4), vanilla(Items.LAPIS_LAZULI, 16)),
                modItem(MagicEquipment.UNIVERSAL_COMPONENT, 1)));
        add(Tier.T2, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("medium_mana_crystal", 4), enchantedBook(level, random, LOW_BOOKS, 1, 2)));
        add(Tier.T2, WEIGHT_LOW, (level, random) -> draft(
                modItem("medium_mana_crystal", 1), randomBeneficialPotion(random)));

        // ── 高级交易（T3）：高级 / 极品魔晶换高级资源、天赋卷轴与中级饰品 ───────
        add(Tier.T3, WEIGHT_HIGH, (level, random) -> draft(
                modItem("advanced_mana_crystal", 2), modItem(randomElementFragment(random, "advanced"), 1)));
        add(Tier.T3, WEIGHT_HIGH, (level, random) -> draft(
                modItem("advanced_mana_crystal", 2), modItem(randomSchoolFragment(random, "advanced"), 1)));
        add(Tier.T3, WEIGHT_MEDIUM, (level, random) -> draft(
                costs(modItem("advanced_mana_crystal", 4), vanilla(Items.AMETHYST_SHARD, 1)),
                modItem(randomTalentScroll(random), 1)));
        add(Tier.T3, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("advanced_mana_crystal", 3),
                modItem(random.nextFloat() < 0.55F ? randomElementRing(random) : "swift_casting_gloves", 1)));
        add(Tier.T3, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("advanced_mana_crystal", 3), random.nextFloat() < 0.85F
                        ? enchantedBook(level, random, LOW_BOOKS, 3, 3)
                        : enchantedBook(level, random, enchants(Enchantments.FIRE_ASPECT), 2, 2)));
        add(Tier.T3, WEIGHT_LOW, (level, random) -> {
            boolean weapon = random.nextBoolean();
            Item[] bases = weapon
                    ? new Item[]{Items.IRON_SWORD, Items.DIAMOND_SWORD}
                    : new Item[]{Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE};
            return draft(modItem("advanced_mana_crystal", 5),
                    randomGear(level, random, bases, weapon ? WEAPON_ENCHANTS : ARMOR_ENCHANTS, 1, 2, 2, 3, false));
        });
        add(Tier.T3, WEIGHT_LOW, (level, random) -> draft(
                costs(modItem("advanced_mana_crystal", 3), vanilla(Items.BLAZE_POWDER, 4)),
                modItem("superconducting_crystal_dust", 1)));
        add(Tier.T3, WEIGHT_MEDIUM, (level, random) -> draft(
                costs(modItem("advanced_mana_crystal", 2), vanilla(Items.GLASS_BOTTLE, 1)),
                modItem("mana_harmonizer", 1)));

        // ── 稀有交易（T4）：极品魔晶换稀有资源与高级饰品 ───────────────────────
        add(Tier.T4, WEIGHT_HIGH, (level, random) -> draft(
                modItem("supreme_mana_crystal", 2), modItem(randomElementFragment(random, "supreme"), 1)));
        add(Tier.T4, WEIGHT_HIGH, (level, random) -> draft(
                modItem("supreme_mana_crystal", 2), modItem(randomSchoolFragment(random, "supreme"), 1)));
        add(Tier.T4, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("supreme_mana_crystal", 3), randomAdventureBundle(random)));
        add(Tier.T4, WEIGHT_MEDIUM, (level, random) -> {
            boolean weapon = random.nextBoolean();
            Item[] bases = weapon
                    ? new Item[]{Items.DIAMOND_SWORD, Items.NETHERITE_SWORD}
                    : new Item[]{Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE};
            return draft(modItem("supreme_mana_crystal", 6),
                    randomGear(level, random, bases, weapon ? WEAPON_ENCHANTS : ARMOR_ENCHANTS, 2, 3, 3, 4, false));
        });
        add(Tier.T4, WEIGHT_MEDIUM, (level, random) -> draft(
                modItem("supreme_mana_crystal", 3), modItem("special_mana_crystal_dust", 1)));
        add(Tier.T4, WEIGHT_LOW, (level, random) -> draft(
                costs(modItem("supreme_mana_crystal", 8), modItem("superconducting_crystal_dust", 4),
                        modItem(randomElementCrystal(random), 1)),
                modItem("superconducting_crystal", 1)));
        add(Tier.T4, WEIGHT_LOW, (level, random) -> draft(
                modItem("supreme_mana_crystal", 5), modItem("cracked_ancient_casket", 1)));
        add(Tier.T4, WEIGHT_LOW, (level, random) -> draft(
                costs(modItem("supreme_mana_crystal", 6), vanilla(Items.NETHERITE_SCRAP, 2)),
                modItem(AncientFragmentLoot.ITEM_ID, 1)));

        // ── 传说交易（T5）：特级魔晶换传说资源与顶级饰品 ───────────────────────
        add(Tier.T5, WEIGHT_HIGH, (level, random) -> draft(
                modItem("special_mana_crystal", 2), modItem(randomElementFragment(random, "special"), 1)));
        add(Tier.T5, WEIGHT_HIGH, (level, random) -> draft(
                modItem("special_mana_crystal", 2), modItem(randomSchoolFragment(random, "special"), 1)));
        add(Tier.T5, WEIGHT_LOW, (level, random) -> {
            boolean weapon = random.nextBoolean();
            Item[] bases = weapon ? new Item[]{Items.NETHERITE_SWORD}
                    : new Item[]{Items.NETHERITE_CHESTPLATE};
            return draft(costs(modItem("special_mana_crystal", 6), vanilla(Items.NETHERITE_INGOT, 2)),
                    randomGear(level, random, bases, weapon ? WEAPON_ENCHANTS : ARMOR_ENCHANTS, 3, 4, 0, 0, true));
        });
        add(Tier.T5, WEIGHT_LOW, (level, random) -> draft(
                costs(modItem("special_mana_crystal", 4), modItem("advanced_mana_crystal", 2),
                        vanilla(Items.DIAMOND, 2)),
                modItem("ancient_casket", 1)));
    }

    private static void add(Tier tier, int weight, Maker maker) {
        CATALOG.add(new Entry(tier, weight, maker));
    }

    private static Draft draft(ItemStack cost, ItemStack result) {
        return new Draft(List.of(cost), result);
    }

    private static Draft draft(List<ItemStack> costs, ItemStack result) {
        return new Draft(costs, result);
    }

    private static List<ItemStack> costs(ItemStack... stacks) {
        List<ItemStack> list = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) if (!stack.isEmpty()) list.add(stack);
        return list;
    }

    private static ItemStack modItem(String id, int count) {
        var holder = GeneratedMod.MOD_ITEMS.get(id);
        return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get(), count);
    }

    private static ItemStack vanilla(Item item, int count) {
        return new ItemStack(item, count);
    }

    /** 11 种元素的随机碎片。 */
    private static String randomElementFragment(RandomSource random, String grade) {
        ElementType[] elements = ElementType.values();
        return elements[random.nextInt(elements.length)].fragmentId(grade);
    }

    /** 13 个学派的随机碎片。 */
    private static String randomSchoolFragment(RandomSource random, String grade) {
        List<String> schools = GeneratedMod.SCHOOL_IDS;
        return grade + "_" + schools.get(random.nextInt(schools.size())) + "_school_fragment";
    }

    /** 随机元素 id；allowArcane 时把奥术也算进去。 */
    private static String randomElementId(RandomSource random, boolean allowArcane) {
        ElementType[] elements = ElementType.values();
        int index = random.nextInt(elements.length + (allowArcane ? 1 : 0));
        return index == elements.length ? MagicEquipment.ARCANE : elements[index].id();
    }

    private static String randomElementCrystal(RandomSource random) {
        return MagicEquipment.crystalId(randomElementId(random, true));
    }

    private static String randomElementRing(RandomSource random) {
        return MagicEquipment.ringId(randomElementId(random, true));
    }

    private static String randomTalentScroll(RandomSource random) {
        ElementType[] elements = ElementType.values();
        return elements[random.nextInt(elements.length)].scrollId();
    }

    private static ItemStack randomBeneficialPotion(RandomSource random) {
        List<Holder<net.minecraft.world.item.alchemy.Potion>> potions = List.of(Potions.HEALING,
                Potions.STRONG_HEALING, Potions.NIGHT_VISION, Potions.REGENERATION, Potions.SWIFTNESS);
        return PotionContents.createItemStack(Items.POTION, potions.get(random.nextInt(potions.size())));
    }

    private static ItemStack randomAdventureBundle(RandomSource random) {
        return switch (random.nextInt(3)) {
            case 0 -> vanilla(Items.NETHERITE_INGOT, 1);
            case 1 -> vanilla(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1);
            default -> modItem(randomElementCrystal(random), 1);
        };
    }

    private static Holder<Enchantment> enchantment(ServerLevel level, ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack enchantedBook(ServerLevel level, RandomSource random,
                                           ResourceKey<Enchantment>[] pool, int minLevel, int maxLevel) {
        ResourceKey<Enchantment> key = pool[random.nextInt(pool.length)];
        int level2 = minLevel + random.nextInt(maxLevel - minLevel + 1);
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment(level, key), level2));
    }

    /**
     * 随机一件带附魔的装备。topProfile 为真时用“顶级附魔”的固定等级
     * （保护 IV、锋利 V、效率 V、耐久 III、经验修补 I），否则在 [min,max] 里随机。
     */
    private static ItemStack randomGear(ServerLevel level, RandomSource random, Item[] bases,
                                        ResourceKey<Enchantment>[] pool, int minCount, int maxCount,
                                        int minLevel, int maxLevel, boolean topProfile) {
        ItemStack stack = new ItemStack(bases[random.nextInt(bases.length)]);
        List<ResourceKey<Enchantment>> remaining = new ArrayList<>(List.of(pool));
        int count = minCount + random.nextInt(maxCount - minCount + 1);
        while (count-- > 0 && !remaining.isEmpty()) {
            ResourceKey<Enchantment> key = remaining.remove(random.nextInt(remaining.size()));
            int level2 = topProfile ? topLevel(key) : minLevel + random.nextInt(maxLevel - minLevel + 1);
            stack.enchant(enchantment(level, key), level2);
        }
        return stack;
    }

    private static int topLevel(ResourceKey<Enchantment> key) {
        if (key == Enchantments.PROTECTION) return 4;
        if (key == Enchantments.SHARPNESS || key == Enchantments.EFFICIENCY) return 5;
        if (key == Enchantments.UNBREAKING) return 3;
        return 1;
    }

    /** 抽 10 笔交易：先按层级概率定层，再在该层里按条目权重挑一个不重复的条目。 */
    public static List<Offer> roll(ServerLevel level, RandomSource random) {
        List<Offer> offers = new ArrayList<>(OFFER_COUNT);
        List<Entry> used = new ArrayList<>();
        for (int i = 0; i < OFFER_COUNT; i++) {
            Tier tier = pickTier(random);
            List<Entry> candidates = new ArrayList<>();
            for (Entry entry : CATALOG) if (entry.tier() == tier) candidates.add(entry);
            if (candidates.isEmpty()) continue;
            List<Entry> unused = new ArrayList<>();
            for (Entry entry : candidates) if (!used.contains(entry)) unused.add(entry);
            Entry entry = pickWeighted(unused.isEmpty() ? candidates : unused, random);
            used.add(entry);
            Draft draft = entry.maker().make(level, random);
            if (draft == null || draft.costs().isEmpty() || draft.result().isEmpty()) continue;
            offers.add(new Offer(entry.tier(), draft.costs(), draft.result(), entry.tier().rollUses(random), 0));
        }
        return offers;
    }

    private static Tier pickTier(RandomSource random) {
        float value = random.nextFloat();
        float cumulative = 0.0F;
        for (Tier tier : Tier.values()) {
            cumulative += tier.chance();
            if (value < cumulative) return tier;
        }
        return Tier.T1;
    }

    private static Entry pickWeighted(List<Entry> pool, RandomSource random) {
        int total = 0;
        for (Entry entry : pool) total += entry.weight();
        int value = random.nextInt(Math.max(1, total));
        for (Entry entry : pool) {
            value -= entry.weight();
            if (value < 0) return entry;
        }
        return pool.get(pool.size() - 1);
    }

    /** 玩家身上够不够这一笔的标价（创造模式永远算够）。 */
    public static boolean hasCosts(Player player, List<ItemStack> costs) {
        if (player.getAbilities().instabuild) return true;
        Inventory inventory = player.getInventory();
        for (ItemStack cost : costs) {
            if (cost.isEmpty()) continue;
            if (!hasCost(inventory, costs, cost)) return false;
        }
        return true;
    }

    /** 同一种物品出现在两条标价里时按总数一起算，避免误判。 */
    private static boolean hasCost(Inventory inventory, List<ItemStack> costs, ItemStack cost) {
        int required = 0;
        for (ItemStack other : costs) if (ItemStack.isSameItemSameComponents(other, cost)) required += other.getCount();
        int available = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, cost)) available += stack.getCount();
        }
        return available >= required;
    }

    /** 扣掉标价；创造模式不扣。 */
    public static void pay(Player player, List<ItemStack> costs) {
        if (player.getAbilities().instabuild) return;
        Inventory inventory = player.getInventory();
        for (ItemStack cost : costs) {
            int remaining = cost.getCount();
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!ItemStack.isSameItemSameComponents(stack, cost)) continue;
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
        }
    }

    /** 服务端执行一笔交易：校验库存、扣材料、给成品、记次数。 */
    public static boolean execute(Adventurer adventurer, Player player, int index) {
        List<Offer> offers = adventurer.tradeOffers();
        if (player == null || index < 0 || index >= offers.size()) return false;
        Offer offer = offers.get(index);
        if (offer.soldOut()) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.trade_sold_out"), false);
            playDeny(player);
            return false;
        }
        if (!hasCosts(player, offer.costs())) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.trade_not_enough"), false);
            playDeny(player);
            return false;
        }
        pay(player, offer.costs());
        offer.addUse();
        ItemStack result = offer.result().copy();
        player.getInventory().add(result);
        if (!result.isEmpty()) player.drop(result, false);
        player.giveExperiencePoints((offer.tier().ordinal() + 1) * 2);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            MagicAchievements.onTrade(serverPlayer, offer.tier());
        }
        player.level().playSound(null, adventurer.blockPosition(), SoundEvents.VILLAGER_TRADE,
                SoundSource.NEUTRAL, 0.9F, 1.0F);
        return true;
    }

    private static void playDeny(Player player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO,
                SoundSource.NEUTRAL, 0.8F, 1.0F);
    }

    public static Component tierName(Tier tier) {
        return Component.translatable(tier.translationKey());
    }

    private AdventurerTrades() { }
}
