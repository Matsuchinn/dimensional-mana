package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.Locale;

/** 魔法等级、技能点、技能树和魔素成长的持久数据。 */
public final class MagicProgression {
    public static final int MAX_MAGIC_LEVEL = 15;
    public static final int MAX_SKILL_LEVEL = 20;
    public static final int INITIAL_SKILL_POINTS = 6;
    private static final String ROOT = "DimensionalMana";
    private static final String SKILLS = "magicSkills";
    private static final int[] MANA_MILESTONES = {200, 500, 1000, 2000, 5000, 10000};
    private static final int[] MANA_MILESTONE_XP = {150, 300, 1000, 3000, 9000, 30000};

    public enum Skill {
        MANA("mana", "gui.dimensional_mana.skill.mana"),
        AURA("aura", "gui.dimensional_mana.skill.aura"),
        SPELL_RESISTANCE("spell_resistance", "gui.dimensional_mana.skill.spell_resistance"),
        SPELL_POWER("spell_power", "gui.dimensional_mana.skill.spell_power"),
        SPELL_HASTE("spell_haste", "gui.dimensional_mana.skill.spell_haste"),
        SPELL_CRIT_CHANCE("spell_crit_chance", "gui.dimensional_mana.skill.spell_crit_chance"),
        SPELL_CRIT_DAMAGE("spell_crit_damage", "gui.dimensional_mana.skill.spell_crit_damage"),
        MANA_COST_REDUCTION("mana_cost_reduction", "gui.dimensional_mana.skill.mana_cost_reduction");

        private final String id;
        private final String translation;
        Skill(String id, String translation) {
            this.id = id;
            this.translation = translation;
        }
        public String id() { return id; }
        public String translation() { return translation; }

        public static Skill byId(String id) {
            return Arrays.stream(values()).filter(skill -> skill.id.equals(id.toLowerCase(Locale.ROOT))).findFirst().orElse(null);
        }
    }

    public record Snapshot(int magicLevel, long magicExperience, int skillPoints, int spentSkillPoints,
                           int[] skills, int xpRemainder, int dailyExhaustionGrowths, long exhaustionDay,
                           int manaMilestoneMask) {
        public int skill(Skill skill) { return skills[skill.ordinal()]; }
    }

    public static Snapshot get(Player player) {
        CompoundTag t = tag(player);
        int[] levels = new int[Skill.values().length];
        CompoundTag skills = t.getCompound(SKILLS);
        for (Skill skill : Skill.values()) levels[skill.ordinal()] = skills.getInt(skill.id());
        return new Snapshot(t.getInt("magicLevel"), t.getLong("magicExperience"), t.getInt("skillPoints"),
                t.getInt("spentSkillPoints"), levels, t.getInt("vanillaXpRemainder"),
                t.getInt("dailyExhaustionGrowths"), t.getLong("exhaustionDay"), t.getInt("manaMilestoneMask"));
    }

    public static void init(Player player) {
        CompoundTag t = tagRaw(player);
        if (!t.contains("magicProgressInitialized")) {
            t.putBoolean("magicProgressInitialized", true);
            t.putInt("magicLevel", 0);
            t.putLong("magicExperience", 0L);
            t.putInt("skillPoints", INITIAL_SKILL_POINTS);
            t.putInt("spentSkillPoints", 0);
            t.putInt("vanillaXpRemainder", 0);
            t.putInt("dailyExhaustionGrowths", 0);
            t.putLong("exhaustionDay", Long.MIN_VALUE);
            t.putInt("manaMilestoneMask", 0);
            t.put(SKILLS, new CompoundTag());
        }
        applySkillEffects(player);
    }

    public static int requirementForNextLevel(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= MAX_MAGIC_LEVEL) return 0;
        return 64 << currentLevel;
    }

    public static int skillCostForNextRank(int currentRank) {
        if (currentRank < 0 || currentRank >= MAX_SKILL_LEVEL) return 0;
        // 10~12 级原本每级消耗 4 点；新增的 13~20 级继续固定为 4 点。
        return Math.min(4, currentRank / 3 + 1);
    }

    public static int pointsAwardedForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 3) return 2;
        if (level <= 6) return 4;
        if (level <= 9) return 8;
        if (level <= 12) return 16;
        return 32;
    }

    public static boolean upgradeSkill(ServerPlayer player, Skill skill) {
        if (skill == null) return false;
        CompoundTag t = tag(player);
        CompoundTag skills = t.getCompound(SKILLS);
        int current = Math.max(0, Math.min(MAX_SKILL_LEVEL, skills.getInt(skill.id())));
        int cost = skillCostForNextRank(current);
        if (cost <= 0 || t.getInt("skillPoints") < cost) return false;
        skills.putInt(skill.id(), current + 1);
        t.put(SKILLS, skills);
        t.putInt("skillPoints", t.getInt("skillPoints") - cost);
        t.putInt("spentSkillPoints", t.getInt("spentSkillPoints") + cost);
        applySkillEffects(player);
        awardAdvancement(player, "first_skill");
        awardSkillAdvancements(player);
        return true;
    }

    /** 任一技能满 20 级、八项技能全部至少 1 级时的成就。 */
    private static void awardSkillAdvancements(ServerPlayer player) {
        CompoundTag skills = tag(player).getCompound(SKILLS);
        boolean anyMaxed = false;
        boolean allStarted = true;
        for (Skill skill : Skill.values()) {
            int level = skills.getInt(skill.id());
            if (level >= MAX_SKILL_LEVEL) anyMaxed = true;
            if (level < 1) allStarted = false;
        }
        if (anyMaxed) awardAdvancement(player, "skill_maxed");
        if (allStarted) awardAdvancement(player, "all_skills");
    }

    /** 调试指令：将全部技能直接设为满级，并同步永久属性。 */
    public static void maxAllSkills(ServerPlayer player) {
        CompoundTag t = tag(player);
        CompoundTag skills = new CompoundTag();
        int spent = 0;
        for (Skill skill : Skill.values()) {
            skills.putInt(skill.id(), MAX_SKILL_LEVEL);
            for (int rank = 0; rank < MAX_SKILL_LEVEL; rank++) spent += skillCostForNextRank(rank);
        }
        t.put(SKILLS, skills);
        t.putInt("spentSkillPoints", spent);
        t.putInt("skillPoints", 0);
        t.putInt("magicLevel", MAX_MAGIC_LEVEL);
        t.putLong("magicExperience", 0L);
        applySkillEffects(player);
    }

    public static boolean resetSkills(ServerPlayer player) {
        CompoundTag t = tag(player);
        int spent = t.getInt("spentSkillPoints");
        if (spent <= 0) return false;
        t.put(SKILLS, new CompoundTag());
        t.putInt("skillPoints", t.getInt("skillPoints") + spent);
        t.putInt("spentSkillPoints", 0);
        applySkillEffects(player);
        return true;
    }

    public static void addMagicExperience(ServerPlayer player, long amount) {
        if (amount <= 0) return;
        CompoundTag t = tag(player);
        int level = t.getInt("magicLevel");
        long experience = t.getLong("magicExperience") + amount;
        while (level < MAX_MAGIC_LEVEL) {
            int required = requirementForNextLevel(level);
            if (experience < required) break;
            experience -= required;
            level++;
            t.putInt("skillPoints", t.getInt("skillPoints") + pointsAwardedForLevel(level));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.magic_level_up", level, pointsAwardedForLevel(level)), false);
            awardLevelAdvancements(player, level);
        }
        t.putInt("magicLevel", level);
        if (level >= MAX_MAGIC_LEVEL && experience > 0) {
            int vanillaExperience = (int)Math.min(Integer.MAX_VALUE, experience);
            experience = 0;
            ManaEvents.addConvertedVanillaExperience(player, vanillaExperience);
        }
        t.putLong("magicExperience", experience);
    }

    /** 每跨过一个等级就补发对应里程碑成就；一次连升多级时不会漏掉中间几档。 */
    private static void awardLevelAdvancements(ServerPlayer player, int level) {
        if (level >= 1) awardAdvancement(player, "magic_level_1");
        if (level >= 5) awardAdvancement(player, "magic_level_5");
        if (level >= 10) awardAdvancement(player, "magic_level_10");
        if (level >= MAX_MAGIC_LEVEL) awardAdvancement(player, "magic_level_15");
    }

    public static void recordVanillaExperience(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        CompoundTag t = tag(player);
        int total = t.getInt("vanillaXpRemainder") + amount;
        int attempts = total / 4;
        t.putInt("vanillaXpRemainder", total % 4);
        int awarded = 0;
        for (int i = 0; i < attempts; i++) if (player.getRandom().nextDouble() < 0.956) awarded++;
        addMagicExperience(player, awarded);
    }

    /** 每次法术成功施放后调用。 */
    public static void recordSpellCast(ServerPlayer player) {
        addMagicExperience(player, 1);
    }

    /** 学会新法术时增加可分配技能点。 */
    public static void addSkillPoints(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        CompoundTag t = tag(player);
        t.putInt("skillPoints", t.getInt("skillPoints") + amount);
    }

    /** 魔素首次达到指定里程碑时奖励魔法经验。 */
    public static void checkManaMilestones(ServerPlayer player, double mana) {
        CompoundTag t = tag(player);
        int mask = t.getInt("manaMilestoneMask");
        int newMask = mask;
        for (int i = 0; i < MANA_MILESTONES.length; i++) {
            int bit = 1 << i;
            if (mana >= MANA_MILESTONES[i] && (mask & bit) == 0) {
                newMask |= bit;
                addMagicExperience(player, MANA_MILESTONE_XP[i]);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dimensional_mana.mana_milestone", MANA_MILESTONES[i], MANA_MILESTONE_XP[i]), false);
                switch (MANA_MILESTONES[i]) {
                    case 200 -> awardAdvancement(player, "mana_200");
                    case 500 -> awardAdvancement(player, "mana_500");
                    case 1000 -> awardAdvancement(player, "mana_1000");
                    case 2000 -> awardAdvancement(player, "mana_2000");
                    case 5000 -> awardAdvancement(player, "mana_5000");
                    case 10000 -> awardAdvancement(player, "mana_10000");
                    default -> { }
                }
            }
        }
        t.putInt("manaMilestoneMask", newMask);
    }

    /** 每个游戏日最多触发四次，且三个成长事件互斥。 */
    public static double rollExhaustionGrowth(ServerPlayer player) {
        CompoundTag t = tag(player);
        long day = player.level().getDayTime() / 24000L;
        if (t.getLong("exhaustionDay") != day) {
            t.putLong("exhaustionDay", day);
            t.putInt("dailyExhaustionGrowths", 0);
        }
        int count = t.getInt("dailyExhaustionGrowths");
        if (count >= 4) return 0;
        double roll = player.getRandom().nextDouble();
        double growth = roll < 0.005 ? 16 : roll < 0.295 ? 4 : roll < 0.795 ? 1 : 0;
        if (growth > 0) t.putInt("dailyExhaustionGrowths", count + 1);
        return growth;
    }

    public static void applyInsight(ServerPlayer player, String tier) {
        CompoundTag t = tag(player);
        String normalized = tier.toLowerCase(Locale.ROOT);
        String oldKey = "insight_" + normalized;
        String countKey = "insight_count_" + normalized;
        // 旧存档只记录“该品级触发过一次”；迁移成次数后，后续新法术继续拿重复顿悟。
        int count = t.contains(countKey) ? t.getInt(countKey) : (t.getBoolean(oldKey) ? 1 : 0);
        int amount = switch (normalized) {
            case "lesser" -> count == 0 ? 48 : 24;
            case "medium" -> count == 0 ? 96 : 64;
            case "advanced" -> count == 0 ? 192 : 144;
            default -> 0;
        };
        if (amount <= 0) return;
        t.putBoolean(oldKey, true);
        t.putInt(countKey, count + 1);
        ManaData.increaseBaseMaxMana(player, amount);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.spell_insight", amount), false);
    }

    public static void applySkillEffects(Player player) {
        Snapshot data = getWithoutInit(player);
        SpellAttributes.setSkillBase(player, SpellAttributes.SPELL_RESISTANCE, data.skill(Skill.SPELL_RESISTANCE) * 0.02);
        SpellAttributes.setSkillBase(player, SpellAttributes.SPELL_POWER, 1.0 + data.skill(Skill.SPELL_POWER) * 6.0);
        SpellAttributes.setSkillBase(player, SpellAttributes.SPELL_HASTE, 1.0 + data.skill(Skill.SPELL_HASTE) * 0.03);
        SpellAttributes.setSkillBase(player, SpellAttributes.SPELL_CRIT_CHANCE, 0.005 + data.skill(Skill.SPELL_CRIT_CHANCE) * 0.01);
        SpellAttributes.setSkillBase(player, SpellAttributes.SPELL_CRIT_DAMAGE, 0.5 + data.skill(Skill.SPELL_CRIT_DAMAGE) * 0.025);
        SpellAttributes.setSkillBase(player, SpellAttributes.MANA_COST_REDUCTION, data.skill(Skill.MANA_COST_REDUCTION) * 0.02);
    }

    public static double maxManaMultiplier(Player player) {
        return 1.0 + get(player).skill(Skill.MANA) * 0.02;
    }

    public static double maxAuraMultiplier(Player player) {
        return 1.0 + get(player).skill(Skill.AURA) * 0.02;
    }

    private static Snapshot getWithoutInit(Player player) {
        CompoundTag t = tagRaw(player);
        int[] levels = new int[Skill.values().length];
        CompoundTag skills = t.getCompound(SKILLS);
        for (Skill skill : Skill.values()) levels[skill.ordinal()] = skills.getInt(skill.id());
        return new Snapshot(t.getInt("magicLevel"), t.getLong("magicExperience"), t.getInt("skillPoints"),
                t.getInt("spentSkillPoints"), levels, t.getInt("vanillaXpRemainder"),
                t.getInt("dailyExhaustionGrowths"), t.getLong("exhaustionDay"), t.getInt("manaMilestoneMask"));
    }

    public static void awardAdvancement(ServerPlayer player, String path) {
        awardAdvancementCriterion(player, path, "done");
    }

    public static void awardAdvancementCriterion(ServerPlayer player, String path, String criterion) {
        var holder = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(
                GeneratedMod.MOD_ID, path));
        if (holder != null) player.getAdvancements().award(holder, criterion);
    }

    private static CompoundTag tag(Player player) {
        ManaData.init(player);
        initIfNeeded(player);
        return tagRaw(player);
    }

    private static void initIfNeeded(Player player) {
        CompoundTag t = tagRaw(player);
        if (!t.contains("magicProgressInitialized")) init(player);
    }

    private static CompoundTag tagRaw(Player player) {
        ManaData.init(player);
        return player.getPersistentData().getCompound(ROOT);
    }

    private MagicProgression() {}
}
