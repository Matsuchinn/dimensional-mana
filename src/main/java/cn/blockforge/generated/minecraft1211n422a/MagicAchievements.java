package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;

/**
 * 除“升级 / 触发元素反应”以外的成就判定。
 *
 * <p>这些成就没有对应的原版触发器（物品获得、着装、交易成功都不是原版进度能直接观察的事件），
 * 统一在服务端每秒检查一次：背包里有没有某件物品、Curios 栏位上有没有某件饰品。
 * 检查到就调用 {@link MagicProgression#awardAdvancement}，已完成的成就重复调用只会被忽略，
 * 所以掉线重连、换装备都不会漏掉。</p>
 *
 * <p>需要“历史上做过几次”的成就（集齐十二种戒指、穿齐三件披风、五个品级都买过）
 * 把记录写进玩家持久数据的位掩码里，与魔法成长数据放在同一个根标签下。</p>
 */
public final class MagicAchievements {
    private static final String DATA_ROOT = "DimensionalMana";
    private static final String RING_MASK_KEY = "advancementElementRingMask";
    private static final String CLOAK_MASK_KEY = "advancementCloakMask";
    private static final String TRADE_MASK_KEY = "advancementTradeTierMask";

    private static final String[] MANA_CRYSTALS = {
            "lesser_mana_crystal", "medium_mana_crystal", "advanced_mana_crystal",
            "supreme_mana_crystal", "special_mana_crystal"
    };
    private static final String[] RAW_MANA_CRYSTALS = {
            "raw_lesser_mana_crystal", "raw_medium_mana_crystal",
            "raw_advanced_mana_crystal", "raw_supreme_mana_crystal"
    };
    private static final String[] MANA_CRYSTAL_DUSTS = {
            "lesser_mana_crystal_dust", "medium_mana_crystal_dust", "advanced_mana_crystal_dust",
            "supreme_mana_crystal_dust", "special_mana_crystal_dust"
    };
    /** 四个品级的魔素药水（没有极品那一档）：酿出任意一瓶就算进了「炉火与草药」这一节。 */
    private static final String[] MANA_POTIONS = {
            "lesser_mana_potion", "medium_mana_potion", "advanced_mana_potion", "special_mana_potion"
    };
    private static final String[] GRINDERS = {
            "wooden_grinder", "stone_grinder", "golden_grinder",
            "iron_grinder", "diamond_grinder", "netherite_grinder"
    };
    private static final String[] GENERAL_STAFFS = {
            "lesser_general_staff", "medium_general_staff", "advanced_general_staff",
            "supreme_general_staff", "special_general_staff"
    };
    private static final String[] CLOAK_IDS = {
            MagicEquipment.FOCUS_CLOAK, MagicEquipment.DEFLECT_CLOAK, MagicEquipment.MASTER_CLOAK
    };

    /** 十二枚元素戒指：十一种初级元素 + 奥术。 */
    private static final int ALL_RINGS = (1 << (ElementType.values().length + 1)) - 1;
    private static final int ALL_CLOAKS = (1 << CLOAK_IDS.length) - 1;
    private static final int ALL_TRADE_TIERS = (1 << AdventurerTrades.Tier.values().length) - 1;

    /** 每秒调用一次（由 {@link ManaEvents#onTick} 统一调度）。 */
    public static void tick(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return;
        ManaData.init(player);
        CompoundTag data = player.getPersistentData().getCompound(DATA_ROOT);
        checkInventory(player);
        checkAccessories(player, data);
        // 击杀、维度、破绽次数、魔石病这些靠事件与状态判定的，交给 AdvancementTriggers。
        AdvancementTriggers.tick(player);
    }

    /**
     * 背包里的成品成就：魔晶、超导材料、远古钢坯、法杖与剧毒Ⅲ药水。
     *
     * <p>另外还负责主线里「材料 → 工作方块」那几节：魔晶原矿、研磨器、魔晶粉、
     * 魔晶加工台、魔锻炉、专业炉、元素结晶、元素镀层与魔法阵基座。
     * 这些事件在 1.21.1 里没有现成的原版触发器，统一每秒扫一次背包最省事，
     * 而且掉线重连、把东西放箱子再拿回来都不会漏判。</p>
     */
    private static void checkInventory(ServerPlayer player) {
        boolean crystal = false;
        boolean rawCrystal = false;
        boolean grinder = false;
        boolean crystalDust = false;
        boolean processor = false;
        boolean manaForge = false;
        boolean professionalForge = false;
        boolean elementCrystal = false;
        boolean plating = false;
        boolean circleBase = false;
        boolean dust = false;
        boolean superconducting = false;
        boolean billet = false;
        boolean staff = false;
        boolean supremeStaff = false;
        boolean poison = false;
        boolean bossAffix = false;
        // 第二章起的「工作方块 / 工具」类进度：拿到手里就点亮，不必先摆出来。
        boolean manaGrindstone = false;
        boolean crystalAnvil = false;
        boolean workbench = false;
        boolean circleWand = false;
        boolean etchedSlate = false;
        boolean slimeTorch = false;
        boolean liquidMana = false;
        boolean appraisal = false;
        boolean manaPotion = false;
        boolean erosionWood = false;
        boolean distortedStar = false;
        boolean mojiangSteel = false;
        boolean superconductingPlating = false;
        boolean manaPlant = false;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (matches(item, MANA_CRYSTALS)) crystal = true;
            if (matches(item, RAW_MANA_CRYSTALS)) rawCrystal = true;
            if (matches(item, GRINDERS)) grinder = true;
            if (matches(item, MANA_CRYSTAL_DUSTS)) crystalDust = true;
            if (matches(item, "crystal_processor")) processor = true;
            if (matches(item, "mana_forge")) manaForge = true;
            if (matches(item, "professional_crystal_furnace")) professionalForge = true;
            if (item instanceof ElementCrystalItem) elementCrystal = true;
            if (ElementPlating.hasPlating(stack)) plating = true;
            if (matches(item, "magic_circle_base")) circleBase = true;
            if (matches(item, "superconducting_crystal_dust")) dust = true;
            if (matches(item, "superconducting_crystal")) superconducting = true;
            if (matches(item, "ancient_magic_steel_billet")) billet = true;
            if (matches(item, GENERAL_STAFFS)) staff = true;
            if (matches(item, "special_general_staff")) supremeStaff = true;
            if (EquipmentAffixSystem.hasAffix(stack)
                    && EquipmentAffixSystem.isBossAffix(stack)) bossAffix = true;
            if (matches(item, "mana_grindstone")) manaGrindstone = true;
            if (matches(item, "magic_crystal_anvil")) crystalAnvil = true;
            if (matches(item, "weapon_workbench")) workbench = true;
            if (matches(item, "arcane_debug_wand")) circleWand = true;
            if (matches(item, "etched_slate")) etchedSlate = true;
            if (matches(item, "slime_repellent_torch")) slimeTorch = true;
            if (matches(item, "pure_liquid_mana_bucket")) liquidMana = true;
            if (matches(item, "appraisal_orb", "appraisal_glasses", "professional_appraisal_glasses")) appraisal = true;
            if (matches(item, MANA_POTIONS)) manaPotion = true;
            if (matches(item, "erosion_log", "stripped_erosion_log", "erosion_planks")) erosionWood = true;
            if (matches(item, GeneratedMod.DISTORTED_NETHER_STAR_ID)) distortedStar = true;
            if (matches(item, MojiangSteelEquipment.EQUIPMENT_IDS.toArray(new String[0]))) mojiangSteel = true;
            if (ElementPlating.layers(stack, ElementPlating.SUPERCONDUCTING) > 0) superconductingPlating = true;
            if (matches(item, "levilia", "lumelia", "restelia", "vesper_thorn", "vesper_bloom")) manaPlant = true;
            if (!poison && stack.has(DataComponents.POTION_CONTENTS)) {
                PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
                if (contents != null && contents.is(GeneratedMod.POISON_III)) poison = true;
            }
        }
        if (crystal) MagicProgression.awardAdvancement(player, "first_mana_crystal");
        // 引导向主线：原矿 → 研磨器 → 魔晶粉 → 加工台 → 炉子 / 元素结晶 → 镀层 / 法阵。
        if (rawCrystal) MagicProgression.awardAdvancement(player, "first_raw_crystal");
        if (grinder) MagicProgression.awardAdvancement(player, "first_grinder");
        if (crystalDust) MagicProgression.awardAdvancement(player, "first_crystal_dust");
        if (processor) MagicProgression.awardAdvancement(player, "crystal_processor");
        if (manaForge) MagicProgression.awardAdvancement(player, "mana_forge");
        if (professionalForge) MagicProgression.awardAdvancement(player, "professional_forge");
        if (elementCrystal) MagicProgression.awardAdvancement(player, "first_element_crystal");
        if (plating) MagicProgression.awardAdvancement(player, "first_plating");
        if (circleBase) MagicProgression.awardAdvancement(player, "magic_circle_base");
        if (dust) MagicProgression.awardAdvancement(player, "superconducting_dust");
        if (superconducting) MagicProgression.awardAdvancement(player, "superconducting_crystal");
        if (billet) MagicProgression.awardAdvancement(player, "ancient_steel");
        if (staff) MagicProgression.awardAdvancement(player, "first_staff");
        if (supremeStaff) MagicProgression.awardAdvancement(player, "supreme_staff");
        if (poison) MagicProgression.awardAdvancement(player, "poison_iii");
        if (bossAffix) MagicProgression.awardAdvancement(player, "boss_affix_trophy");
        // 第二章：工作方块、工具与材料，都是「拿到手就算进门」的一节。
        if (manaGrindstone) MagicProgression.awardAdvancement(player, "mana_grindstone");
        if (crystalAnvil) MagicProgression.awardAdvancement(player, "magic_crystal_anvil");
        if (workbench) MagicProgression.awardAdvancement(player, "weapon_workbench");
        if (circleWand) MagicProgression.awardAdvancement(player, "circle_wand");
        if (etchedSlate) MagicProgression.awardAdvancement(player, "etched_slate");
        if (slimeTorch) MagicProgression.awardAdvancement(player, "slime_repellent_torch");
        if (liquidMana) MagicProgression.awardAdvancement(player, "pure_liquid_mana");
        if (appraisal) MagicProgression.awardAdvancement(player, "first_appraisal");
        if (manaPotion) MagicProgression.awardAdvancement(player, "first_brew");
        if (erosionWood) MagicProgression.awardAdvancement(player, "erosion_wood");
        if (distortedStar) MagicProgression.awardAdvancement(player, "distorted_star");
        if (mojiangSteel) MagicProgression.awardAdvancement(player, "mojiang_steel");
        if (superconductingPlating) MagicProgression.awardAdvancement(player, "superconducting_plating");
        if (manaPlant) MagicProgression.awardAdvancement(player, "mana_plants");
    }

    /** Curios 栏位上的饰品成就：首次佩戴、元素戒指图鉴、手套、披风、护符与共鸣核心。 */
    private static void checkAccessories(ServerPlayer player, CompoundTag data) {
        List<SlotResult> equipped = CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findCurios(stack -> !stack.isEmpty()))
                .orElse(List.of());
        if (equipped.isEmpty()) return;
        boolean accessory = false;
        boolean enhanceRing = false;
        boolean gloves = false;
        boolean amulet = false;
        boolean resonanceCore = false;
        boolean t0Relic = false;
        boolean worldBlessing = false;
        int ringMask = data.getInt(RING_MASK_KEY);
        int cloakMask = data.getInt(CLOAK_MASK_KEY);
        for (SlotResult result : equipped) {
            ItemStack stack = result.stack();
            Item item = stack.getItem();
            if (item instanceof ElementRingItem ring) {
                int index = ringIndex(ring.elementId());
                if (index >= 0) ringMask |= 1 << index;
                accessory = true;
            } else if (item instanceof ElementEnhanceRingItem) {
                enhanceRing = true;
                accessory = true;
            } else if (item instanceof CastingGloveItem) {
                gloves = true;
                accessory = true;
            } else if (item instanceof MagicCloakItem || item instanceof MagicTrinketItem) {
                accessory = true;
                int cloak = cloakIndex(item);
                if (cloak >= 0) cloakMask |= 1 << cloak;
            } else if (item instanceof ManaEnhanceAmuletItem || matches(item, "magic_amulet")) {
                amulet = true;
                accessory = true;
            } else if (item instanceof MagicAccessoryItem || item instanceof ResonanceCoreItem) {
                accessory = true;
                if (item instanceof ResonanceCoreItem) resonanceCore = true;
            } else if (item instanceof RelicAccessoryItem relic) {
                // 二十来件新饰品（t0～t3）：戴过任意一件就算「初次装扮」，
                // t0 点亮「禁忌的真理」，世界祝福单独一条。
                accessory = true;
                if (relic.relic().tier() == Relics.Tier.T0) t0Relic = true;
                if (RelicEffects.WORLD_BLESSING.equals(relic.relic().id())) worldBlessing = true;
            }
        }
        data.putInt(RING_MASK_KEY, ringMask);
        data.putInt(CLOAK_MASK_KEY, cloakMask);
        if (accessory) MagicProgression.awardAdvancement(player, "first_accessory");
        if (ringMask == ALL_RINGS) MagicProgression.awardAdvancement(player, "element_ring_collection");
        if (enhanceRing) MagicProgression.awardAdvancement(player, "enhance_ring");
        if (gloves) MagicProgression.awardAdvancement(player, "casting_gloves");
        if (cloakMask == ALL_CLOAKS) MagicProgression.awardAdvancement(player, "cloak_trio");
        if (amulet) MagicProgression.awardAdvancement(player, "amulet_bond");
        if (resonanceCore) MagicProgression.awardAdvancement(player, "resonance_core");
        if (t0Relic) MagicProgression.awardAdvancement(player, "relic_t0");
        if (worldBlessing) MagicProgression.awardAdvancement(player, "world_blessing");
    }

    /** 一笔冒险者交易成功后调用：记录买过的品级并补发三条交易成就。 */
    public static void onTrade(ServerPlayer player, AdventurerTrades.Tier tier) {
        if (player == null || tier == null) return;
        ManaData.init(player);
        CompoundTag data = player.getPersistentData().getCompound(DATA_ROOT);
        int mask = data.getInt(TRADE_MASK_KEY) | (1 << tier.ordinal());
        data.putInt(TRADE_MASK_KEY, mask);
        MagicProgression.awardAdvancement(player, "first_trade");
        if (tier == AdventurerTrades.Tier.T5) MagicProgression.awardAdvancement(player, "legend_trade");
        if (mask == ALL_TRADE_TIERS) MagicProgression.awardAdvancement(player, "all_trade_tiers");
    }

    /** 主手持魔法大师披风切换凝聚球开关时调用。 */
    public static void onOrbToggled(ServerPlayer player) {
        if (player == null) return;
        MagicProgression.awardAdvancement(player, "orb_toggle");
    }

    private static int ringIndex(String elementId) {
        if (MagicEquipment.ARCANE.equals(elementId)) return ElementType.values().length;
        for (ElementType element : ElementType.values()) {
            if (element.id().equals(elementId)) return element.ordinal();
        }
        return -1;
    }

    private static int cloakIndex(Item item) {
        for (int i = 0; i < CLOAK_IDS.length; i++) {
            if (matches(item, CLOAK_IDS[i])) return i;
        }
        return -1;
    }

    private static boolean matches(Item item, String... ids) {
        if (item == null) return false;
        for (String id : ids) {
            var holder = GeneratedMod.MOD_ITEMS.get(id);
            if (holder != null && holder.get() == item) return true;
        }
        return false;
    }

    private MagicAchievements() { }
}
