package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 法术学习进度、快捷盘与当前选择，随玩家存档保存。 */
public final class SpellData {
    public static final int SLOT_COUNT = 12;
    public static final int REQUIRED_SUCCESSES = 3;
    private static final String ROOT = "DimensionalMana";
    private static final String SPELLS = "spells";
    private static final String PROGRESS = "progress";
    private static final String SLOTS = "slots";
    private static final String SKILL_REWARDED_SPELLS = "skillPointRewardedSpells";
    private static final String SKILL_REWARD_COUNT = "skillPointRewardedSpellCount";
    private static final String SKILL_REWARD_MIGRATION = "spellSkillRewardMigrationDone";

    public record Snapshot(int[] progress, String learningSpell, String[] slots, int selectedSlot) {
        public int progress(SpellDefinition spell) { return progress[spell.ordinal()]; }
        public boolean learned(SpellDefinition spell) { return progress(spell) >= REQUIRED_SUCCESSES; }
        public SpellDefinition learning() { return SpellDefinition.byId(learningSpell); }
        public SpellDefinition selected() {
            return selectedSlot >= 0 && selectedSlot < slots.length ? SpellDefinition.byId(slots[selectedSlot]) : null;
        }
    }

    public static Snapshot get(Player player) {
        CompoundTag tag = tag(player);
        CompoundTag progressTag = tag.getCompound(PROGRESS);
        int[] progress = new int[SpellDefinition.values().length];
        for (SpellDefinition spell : SpellDefinition.values()) {
            progress[spell.ordinal()] = Math.max(0, Math.min(REQUIRED_SUCCESSES, progressTag.getInt(spell.id())));
        }
        CompoundTag slotsTag = tag.getCompound(SLOTS);
        String[] slots = new String[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) slots[i] = slotsTag.getString(Integer.toString(i));
        return new Snapshot(progress, tag.getString("learning"), slots,
                Math.max(0, Math.min(SLOT_COUNT - 1, tag.getInt("selectedSlot"))));
    }

    public static boolean beginLearning(ServerPlayer player, SpellDefinition spell) {
        if (spell == null || get(player).learned(spell) || !ElementTalentData.canAccess(player, spell)) return false;
        ElementType element = spell.element();
        String elementFragmentId = spell.elementFragmentId();
        if (elementFragmentId != null) {
            Item elementFragment = GeneratedMod.MOD_ITEMS.get(elementFragmentId).get();
            Item schoolFragment = GeneratedMod.MOD_ITEMS.get(spell.schoolFragmentId(spell.grade())).get();
            boolean hasElement = player.getInventory().countItem(elementFragment) >= 1;
            boolean hasSchool = player.getInventory().countItem(schoolFragment) >= 1;
            if (!player.getAbilities().instabuild && (!hasElement || !hasSchool)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dimensional_mana.spell_need_fragment_pair",
                        new ItemStack(elementFragment).getHoverName(), new ItemStack(schoolFragment).getHoverName()), true);
                return false;
            }
            if (!player.getAbilities().instabuild) {
                removeItems(player, elementFragment, 1);
                removeItems(player, schoolFragment, 1);
            }
            if (!player.getAbilities().instabuild && element != null)
                ElementTalentData.addInvestment(player, element, 1, spell.grade());
        }
        tag(player).putString("learning", spell.id());
        assign(player, spell, -1);
        sync(player);
        return true;
    }

    private static void removeItems(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
    }

    public static boolean assign(ServerPlayer player, SpellDefinition spell, int requestedSlot) {
        Snapshot snapshot = get(player);
        if (spell == null || (!snapshot.learned(spell) && snapshot.learning() != spell)) return false;
        CompoundTag slots = tag(player).getCompound(SLOTS);
        int slot = requestedSlot;
        if (slot < 0 || slot >= SLOT_COUNT) {
            slot = 0;
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (slots.getString(Integer.toString(i)).isBlank()) { slot = i; break; }
            }
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots.getString(Integer.toString(i)).equals(spell.id())) slots.remove(Integer.toString(i));
        }
        slots.putString(Integer.toString(slot), spell.id());
        tag(player).put(SLOTS, slots);
        tag(player).putInt("selectedSlot", slot);
        sync(player);
        return true;
    }

    public static void selectSlot(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        SpellCasting.discardTarget(player);
        tag(player).putInt("selectedSlot", slot);
        sync(player);
    }

    public static void clearSlot(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        SpellCasting.discardTarget(player);
        tag(player).getCompound(SLOTS).remove(Integer.toString(slot));
        sync(player);
    }

    /** 成功学习一次；返回新的成功次数。 */
    public static int recordLearningSuccess(ServerPlayer player, SpellDefinition spell) {
        CompoundTag tag = tag(player);
        CompoundTag progress = tag.getCompound(PROGRESS);
        int old = Math.max(0, Math.min(REQUIRED_SUCCESSES, progress.getInt(spell.id())));
        int value = Math.min(REQUIRED_SUCCESSES, old + 1);
        progress.putInt(spell.id(), value);
        tag.put(PROGRESS, progress);
        if (value >= REQUIRED_SUCCESSES) {
            boolean firstMasteryOfThisSpell = old < REQUIRED_SUCCESSES && markInsightAwarded(player, spell);
            if (firstMasteryOfThisSpell && spell.isIntermediate()) {
                MagicProgression.applyInsight(player, "medium");
                MagicProgression.awardAdvancement(player, "intermediate_spell");
            } else if (firstMasteryOfThisSpell && spell.isAdvanced()) {
                MagicProgression.applyInsight(player, "advanced");
                MagicProgression.awardAdvancement(player, "advanced_spell");
            } else if (firstMasteryOfThisSpell) {
                MagicProgression.applyInsight(player, "lesser");
            }
            if (firstMasteryOfThisSpell) awardSpellSkillPoints(player, spell);
            tag.remove("learning");
            assign(player, spell, -1);
        }
        sync(player);
        return value;
    }

    /** 记录该法术是否已经发过顿悟奖励，防止洗掉法术再学时重复刷同一个法术。 */
    private static boolean markInsightAwarded(ServerPlayer player, SpellDefinition spell) {
        CompoundTag spellTag = tag(player);
        CompoundTag awarded = spellTag.getCompound("insightAwardedSpells");
        if (awarded.getBoolean(spell.id())) return false;
        awarded.putBoolean(spell.id(), true);
        spellTag.put("insightAwardedSpells", awarded);
        return true;
    }

    /** 每个初级/中级/高级新法术分别奖励 1/2/3 点；累计每三个再额外奖励 3 点。 */
    private static void awardSpellSkillPoints(ServerPlayer player, SpellDefinition spell) {
        CompoundTag spellTag = tag(player);
        CompoundTag rewarded = spellTag.getCompound(SKILL_REWARDED_SPELLS);
        if (rewarded.getBoolean(spell.id())) return;
        rewarded.putBoolean(spell.id(), true);
        spellTag.put(SKILL_REWARDED_SPELLS, rewarded);
        int learnedCount = Math.max(0, spellTag.getInt(SKILL_REWARD_COUNT)) + 1;
        spellTag.putInt(SKILL_REWARD_COUNT, learnedCount);
        int points = spell.isAdvanced() ? 3 : spell.isIntermediate() ? 2 : 1;
        MagicProgression.addSkillPoints(player, points);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.spell_skill_points", points, learnedCount), false);
        if (learnedCount % 3 == 0) {
            MagicProgression.addSkillPoints(player, 3);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.spell_skill_points_bonus", learnedCount), false);
        }
    }

    /**
     * 给更新前已经学会的法术补发技能点。优先读取永久的顿悟领奖记录，
     * 因此即使玩家曾用忏悔石重置元素法术，也不会漏发或重复发放。
     */
    public static void migrateSpellSkillRewards(ServerPlayer player) {
        CompoundTag spellTag = tag(player);
        if (spellTag.getBoolean(SKILL_REWARD_MIGRATION)) return;
        CompoundTag progress = spellTag.getCompound(PROGRESS);
        CompoundTag insightAwarded = spellTag.getCompound("insightAwardedSpells");
        CompoundTag rewarded = spellTag.getCompound(SKILL_REWARDED_SPELLS);
        int learnedCount = Math.max(0, spellTag.getInt(SKILL_REWARD_COUNT));
        int totalPoints = 0;
        for (SpellDefinition spell : SpellDefinition.values()) {
            boolean learnedBefore = insightAwarded.getBoolean(spell.id())
                    || progress.getInt(spell.id()) >= REQUIRED_SUCCESSES;
            if (!learnedBefore || rewarded.getBoolean(spell.id())) continue;
            rewarded.putBoolean(spell.id(), true);
            learnedCount++;
            totalPoints += spell.isAdvanced() ? 3 : spell.isIntermediate() ? 2 : 1;
            if (learnedCount % 3 == 0) totalPoints += 3;
        }
        spellTag.put(SKILL_REWARDED_SPELLS, rewarded);
        spellTag.putInt(SKILL_REWARD_COUNT, learnedCount);
        spellTag.putBoolean(SKILL_REWARD_MIGRATION, true);
        if (totalPoints > 0) {
            MagicProgression.addSkillPoints(player, totalPoints);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.spell_skill_points_migrated", totalPoints, learnedCount), false);
        }
    }

    public static double learningChance(Player player, SpellDefinition spell) {
        int successes = get(player).progress(spell);
        return Math.min(1.0, 0.40 + successes * 0.20);
    }

    /** 调试指令：直接习得全部法术，并把前十二个法术装入快捷盘。 */
    public static void unlockAll(ServerPlayer player) {
        CompoundTag spellTag = tag(player);
        CompoundTag progress = spellTag.getCompound(PROGRESS);
        CompoundTag slots = spellTag.getCompound(SLOTS);
        SpellDefinition[] spells = SpellDefinition.values();
        CompoundTag awarded = spellTag.getCompound("insightAwardedSpells");
        for (SpellDefinition spell : spells) {
            progress.putInt(spell.id(), REQUIRED_SUCCESSES);
            awarded.putBoolean(spell.id(), true);
        }
        spellTag.put("insightAwardedSpells", awarded);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i < spells.length) slots.putString(Integer.toString(i), spells[i].id());
            else slots.remove(Integer.toString(i));
        }
        spellTag.put(PROGRESS, progress);
        spellTag.put(SLOTS, slots);
        spellTag.remove("learning");
        spellTag.putInt("selectedSlot", 0);
        sync(player);
    }

    public static void resetElemental(ServerPlayer player) {
        CompoundTag tag = tag(player);
        CompoundTag progress = tag.getCompound(PROGRESS);
        for (SpellDefinition spell : SpellDefinition.values()) {
            if (spell.element() != null) progress.remove(spell.id());
        }
        tag.put(PROGRESS, progress);
        tag.remove("learning");
        CompoundTag slots = tag.getCompound(SLOTS);
        for (int i = 0; i < SLOT_COUNT; i++) {
            SpellDefinition spell = SpellDefinition.byId(slots.getString(Integer.toString(i)));
            if (spell != null && spell.element() != null) slots.remove(Integer.toString(i));
        }
        tag.put(SLOTS, slots);
        sync(player);
    }

    private static CompoundTag tag(Player player) {
        ManaData.init(player);
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        if (!root.contains(SPELLS)) {
            CompoundTag spells = new CompoundTag();
            spells.put(PROGRESS, new CompoundTag());
            spells.put(SLOTS, new CompoundTag());
            spells.putInt("selectedSlot", 0);
            root.put(SPELLS, spells);
        }
        CompoundTag spells = root.getCompound(SPELLS);
        if (!spells.getBoolean("insightAwardMigrationDone")) {
            CompoundTag progress = spells.getCompound(PROGRESS);
            CompoundTag awarded = spells.getCompound("insightAwardedSpells");
            for (SpellDefinition spell : SpellDefinition.values()) {
                if (progress.getInt(spell.id()) >= REQUIRED_SUCCESSES) awarded.putBoolean(spell.id(), true);
            }
            spells.put("insightAwardedSpells", awarded);
            spells.putBoolean("insightAwardMigrationDone", true);
        }
        return spells;
    }

    public static void sync(ServerPlayer player) {
        SpellSyncPayload.send(player);
    }

    private SpellData() {}
}
