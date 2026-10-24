package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.Locale;

/**
 * 斗气进阶：独立的斗气等级、斗气技能点、六项斗气技能与三项斗气能力。
 *
 * <p>斗气等级和魔法等级完全是两套进度：升级标准照抄魔法等级（同样的经验曲线、
 * 同样的 15 级上限），但经验只来自近身战斗——造成 1 点近战伤害给 1 点、击杀生物给 10 点、
 * 完美格挡给 20 点。升级发的是斗气技能点，只能花在下面的物理 / 剑士技能上，
 * 不会和魔法技能点混用。</p>
 *
 * <p>数据存在玩家根标签 {@link ManaData#ROOT_TAG} 里，与魔法进度共用同一个存档根，
 * 但键名各自带 {@code qi} 前缀，互不覆盖。所有判定都在服务端完成，客户端只读同步下来的数值。</p>
 */
public final class QiProgression {
    /** 斗气等级上限，和魔法等级一样是 15 级。 */
    public static final int MAX_QI_LEVEL = 15;
    /** 单项斗气技能上限。 */
    public static final int MAX_SKILL_LEVEL = 20;
    /** 新建角色直接给的斗气技能点，和魔法技能一样给 6 点起步。 */
    public static final int INITIAL_SKILL_POINTS = 6;
    /** 斗气技能快捷盘槽位数：目前三本技能书，正好三个槽。 */
    public static final int SLOT_COUNT = 3;

    /** 「斗气强化」每级给的斗气上限。 */
    public static final int BATTLE_QI_PER_RANK = 10;
    /** 近战伤害换斗气经验的换算：每 1 点伤害 1 点经验。 */
    public static final int MELEE_EXPERIENCE_PER_DAMAGE = 1;
    /** 击杀生物给的斗气经验。 */
    public static final int KILL_EXPERIENCE = 10;
    /** 完美格挡给的斗气经验。 */
    public static final int PERFECT_PARRY_EXPERIENCE = 20;

    private static final String ROOT = ManaData.ROOT_TAG;
    private static final String SKILLS = "qiSkills";
    private static final String ABILITIES = "qiAbilities";
    private static final String SLOTS = "qiSlots";
    private static final String SELECTED_SLOT = "qiSelectedSlot";
    private static final String INITIALIZED = "qiProgressInitialized";

    /** 六项斗气技能。每项都只加一条效果，数值写死在 {@link #effect} 里。 */
    public enum Skill {
        BATTLE_QI("battle_qi", "gui.dimensional_mana.qi_skill.battle_qi"),
        IRON_WALL("iron_wall", "gui.dimensional_mana.qi_skill.iron_wall"),
        MIKIRI("mikiri", "gui.dimensional_mana.qi_skill.mikiri"),
        RECOVERY("recovery", "gui.dimensional_mana.qi_skill.recovery"),
        SWORD_AURA("sword_aura", "gui.dimensional_mana.qi_skill.sword_aura"),
        EXECUTE("execute", "gui.dimensional_mana.qi_skill.execute");

        private final String id;
        private final String translation;

        Skill(String id, String translation) {
            this.id = id;
            this.translation = translation;
        }

        public String id() { return id; }
        public String translation() { return translation; }

        public static Skill byId(String id) {
            if (id == null) return null;
            return Arrays.stream(values()).filter(skill -> skill.id.equals(id.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
        }
    }

    /** 三项斗气能力；消耗的是斗气本身，没有冷却。 */
    public enum Ability {
        IAI_SLASH("iai_slash", 30),
        FLASH_STEP("flash_step", 20),
        SWORD_QI("sword_qi", 40);

        private final String id;
        private final int cost;

        Ability(String id, int cost) {
            this.id = id;
            this.cost = cost;
        }

        public String id() { return id; }
        /** 这一招要花多少斗气。 */
        public int cost() { return cost; }
        public String translation() { return "gui.dimensional_mana.qi_ability." + id; }
        /** 对应的技能书物品 id。 */
        public String manualId() { return id + "_manual"; }

        public static Ability byId(String id) {
            if (id == null) return null;
            return Arrays.stream(values()).filter(ability -> ability.id.equals(id.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
        }
    }

    /** 一份完整的斗气进度快照，界面与战斗结算都从这里读。 */
    public record Snapshot(int level, long experience, int skillPoints, int spentSkillPoints,
                           int[] skills, boolean[] abilities, String[] slots, int selectedSlot) {
        public int skill(Skill skill) { return skills[skill.ordinal()]; }
        public boolean learned(Ability ability) { return abilities[ability.ordinal()]; }
        public Ability selected() { return abilityAt(selectedSlot); }
        public Ability abilityAt(int slot) {
            return slot >= 0 && slot < slots.length ? Ability.byId(slots[slot]) : null;
        }
        public int slotOf(Ability ability) {
            if (ability == null) return -1;
            for (int i = 0; i < slots.length; i++) if (ability.id().equals(slots[i])) return i;
            return -1;
        }
    }

    public static Snapshot get(Player player) {
        CompoundTag t = tag(player);
        int[] skills = new int[Skill.values().length];
        CompoundTag skillTag = t.getCompound(SKILLS);
        for (Skill skill : Skill.values()) {
            skills[skill.ordinal()] = Math.max(0, Math.min(MAX_SKILL_LEVEL, skillTag.getInt(skill.id())));
        }
        boolean[] abilities = new boolean[Ability.values().length];
        CompoundTag abilityTag = t.getCompound(ABILITIES);
        for (Ability ability : Ability.values()) abilities[ability.ordinal()] = abilityTag.getBoolean(ability.id());
        String[] slots = new String[SLOT_COUNT];
        CompoundTag slotTag = t.getCompound(SLOTS);
        for (int i = 0; i < SLOT_COUNT; i++) slots[i] = slotTag.getString(Integer.toString(i));
        int selected = Math.max(0, Math.min(SLOT_COUNT - 1, t.getInt(SELECTED_SLOT)));
        return new Snapshot(t.getInt("qiLevel"), t.getLong("qiExperience"), t.getInt("qiSkillPoints"),
                t.getInt("qiSpentSkillPoints"), skills, abilities, slots, selected);
    }

    public static void init(Player player) {
        // 先让 {@link ManaData#init} 把根标签建好：getCompound 拿不到键时会返回一个游离的副本，
        // 里面的修改不会回到玩家身上。
        ManaData.init(player);
        CompoundTag t = tagRaw(player);
        if (!t.getBoolean(INITIALIZED)) {
            t.putBoolean(INITIALIZED, true);
            t.putInt("qiLevel", 0);
            t.putLong("qiExperience", 0L);
            t.putInt("qiSkillPoints", INITIAL_SKILL_POINTS);
            t.putInt("qiSpentSkillPoints", 0);
            t.putInt(SELECTED_SLOT, 0);
            t.put(SKILLS, new CompoundTag());
            t.put(ABILITIES, new CompoundTag());
            t.put(SLOTS, new CompoundTag());
        }
    }

    /** 升到下一级需要多少斗气经验；和魔法等级同一条曲线（64 × 2^level）。 */
    public static int requirementForNextLevel(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= MAX_QI_LEVEL) return 0;
        return 64 << currentLevel;
    }

    /** 单项技能升下一级要几点；规则与魔法技能一致（每 3 级贵 1 点，封顶 4 点）。 */
    public static int skillCostForNextRank(int currentRank) {
        if (currentRank < 0 || currentRank >= MAX_SKILL_LEVEL) return 0;
        return Math.min(4, currentRank / 3 + 1);
    }

    /** 升到某一级发多少斗气技能点；与魔法等级的发放档位一致。 */
    public static int pointsAwardedForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 3) return 2;
        if (level <= 6) return 4;
        if (level <= 9) return 8;
        if (level <= 12) return 16;
        return 32;
    }

    /**
     * 增加斗气经验并结算升级。
     *
     * <p>一次连升多级时逐级发点，中间每一级都会提示，不会把好几级合成一条消息。
     * 满级后多出来的经验直接丢弃：斗气等级没有「满级转普通经验」那种出口。</p>
     */
    public static void addExperience(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return;
        CompoundTag t = tag(player);
        int level = t.getInt("qiLevel");
        long experience = t.getLong("qiExperience") + amount;
        boolean changed = false;
        while (level < MAX_QI_LEVEL) {
            int required = requirementForNextLevel(level);
            if (required <= 0 || experience < required) break;
            experience -= required;
            level++;
            int points = pointsAwardedForLevel(level);
            t.putInt("qiSkillPoints", t.getInt("qiSkillPoints") + points);
            changed = true;
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.qi_level_up", level, points), false);
        }
        t.putInt("qiLevel", level);
        t.putLong("qiExperience", level >= MAX_QI_LEVEL ? 0L : experience);
        if (changed) sync(player);
    }

    /**
     * 调试指令「解锁全部」用：斗气等级直接拉满，六项斗气技能全满，三项能力全部解锁并放进轮盘槽。
     *
     * <p>和魔法那边的 {@code MagicProgression.maxAllSkills} 对齐：技能点清零、已花点数按满级总价记账，
     * 斗气经验清零。解锁完立刻同步一次，战斗手册和轮盘不用重开界面就能看到。</p>
     */
    public static void maxAll(ServerPlayer player) {
        if (player == null) return;
        CompoundTag t = tag(player);
        CompoundTag skills = new CompoundTag();
        int spent = 0;
        for (Skill skill : Skill.values()) {
            skills.putInt(skill.id(), MAX_SKILL_LEVEL);
            for (int rank = 0; rank < MAX_SKILL_LEVEL; rank++) spent += skillCostForNextRank(rank);
        }
        t.put(SKILLS, skills);
        t.putInt("qiSpentSkillPoints", spent);
        t.putInt("qiSkillPoints", 0);
        t.putInt("qiLevel", MAX_QI_LEVEL);
        t.putLong("qiExperience", 0L);
        // 三项能力全部解锁，并按顺序塞满轮盘槽，按大小写锁定键就能直接挑技能。
        CompoundTag abilities = new CompoundTag();
        CompoundTag slots = new CompoundTag();
        Ability[] values = Ability.values();
        for (int i = 0; i < values.length; i++) {
            abilities.putBoolean(values[i].id(), true);
            if (i < SLOT_COUNT) slots.putString(Integer.toString(i), values[i].id());
        }
        t.put(ABILITIES, abilities);
        t.put(SLOTS, slots);
        t.putInt(SELECTED_SLOT, 0);
        sync(player);
    }

    /** 花斗气技能点升一级；点数不够或已满级返回 false。 */
    public static boolean upgradeSkill(ServerPlayer player, Skill skill) {
        if (player == null || skill == null) return false;
        CompoundTag t = tag(player);
        CompoundTag skillTag = t.getCompound(SKILLS);
        int current = Math.max(0, Math.min(MAX_SKILL_LEVEL, skillTag.getInt(skill.id())));
        int cost = skillCostForNextRank(current);
        if (cost <= 0 || t.getInt("qiSkillPoints") < cost) return false;
        skillTag.putInt(skill.id(), current + 1);
        t.put(SKILLS, skillTag);
        t.putInt("qiSkillPoints", t.getInt("qiSkillPoints") - cost);
        t.putInt("qiSpentSkillPoints", t.getInt("qiSpentSkillPoints") + cost);
        sync(player);
        return true;
    }

    /** 读技能书：把对应能力永久解锁。已经会了返回 false，由调用方提示不重复消耗。 */
    public static boolean learn(ServerPlayer player, Ability ability) {
        if (player == null || ability == null) return false;
        CompoundTag tag = tag(player);
        if (tag.getCompound(ABILITIES).getBoolean(ability.id())) return false;
        CompoundTag abilities = tag.getCompound(ABILITIES);
        abilities.putBoolean(ability.id(), true);
        tag.put(ABILITIES, abilities);
        // 新解锁的能力自动放进第一个空槽，学会就能直接用，不用先翻一遍轮盘。
        autoAssign(tag, ability);
        sync(player);
        return true;
    }

    /** 卸下某槽；选中的槽空掉时，选中的能力也跟着消失。 */
    public static void clearSlot(ServerPlayer player, int slot) {
        if (player == null || slot < 0 || slot >= SLOT_COUNT) return;
        CompoundTag t = tag(player);
        CompoundTag slots = t.getCompound(SLOTS);
        slots.remove(Integer.toString(slot));
        t.put(SLOTS, slots);
        sync(player);
    }

    /** 把能力装进指定槽；同一能力不会同时出现在两个槽里。 */
    public static boolean assign(ServerPlayer player, Ability ability, int slot) {
        if (player == null || ability == null || slot < 0 || slot >= SLOT_COUNT) return false;
        CompoundTag t = tag(player);
        if (!t.getCompound(ABILITIES).getBoolean(ability.id())) return false;
        CompoundTag slots = t.getCompound(SLOTS);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (ability.id().equals(slots.getString(Integer.toString(i)))) slots.remove(Integer.toString(i));
        }
        slots.putString(Integer.toString(slot), ability.id());
        t.put(SLOTS, slots);
        t.putInt(SELECTED_SLOT, slot);
        sync(player);
        return true;
    }

    /** 选中一个槽；槽里没有能力时不动选中的槽，避免按一下就把当前技能弄丢。 */
    public static boolean selectSlot(ServerPlayer player, int slot) {
        if (player == null || slot < 0 || slot >= SLOT_COUNT) return false;
        CompoundTag t = tag(player);
        if (t.getCompound(SLOTS).getString(Integer.toString(slot)).isBlank()) return false;
        t.putInt(SELECTED_SLOT, slot);
        sync(player);
        return true;
    }

    private static void autoAssign(CompoundTag tag, Ability ability) {
        CompoundTag slots = tag.getCompound(SLOTS);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (ability.id().equals(slots.getString(Integer.toString(i)))) return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots.getString(Integer.toString(i)).isBlank()) {
                slots.putString(Integer.toString(i), ability.id());
                tag.put(SLOTS, slots);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- 战斗加成

    /** 「斗气强化」：每级 +10 点斗气上限。 */
    public static int bonusMaxBattleQi(Player player) {
        return player == null ? 0 : get(player).skill(Skill.BATTLE_QI) * BATTLE_QI_PER_RANK;
    }

    /** 「钢铁壁垒」：每级让招架姿态的减伤再多 2%（基础 10%）。 */
    public static double parryReductionBonus(Player player) {
        return player == null ? 0.0 : get(player).skill(Skill.IRON_WALL) * 0.02;
    }

    /** 「见切」：每级让完美格挡后挂给敌人的易伤再多 5%（基础 +75%）。 */
    public static double vulnerabilityBonus(Player player) {
        return player == null ? 0.0 : get(player).skill(Skill.MIKIRI) * 0.05;
    }

    /**
     * 「回气」：只在凝聚斗气时生效——每凝聚 1 点斗气，按等级额外恢复 0.1 点（满级多 2 点）。
     *
     * <p>原来这份加成是挂在自然恢复上的，现在自然恢复只剩剑士护符；回气改成加快凝聚速度，
     * 多出来的斗气同样按 1 点斗气 = 1 点魔素扣魔素，兑换率不变。</p>
     */
    public static double condenseBonusPerPoint(Player player) {
        return player == null ? 0.0 : get(player).skill(Skill.RECOVERY) * 0.1;
    }

    /** 「剑气精通」：每级让剑气类技能伤害 +5%，独立乘区。 */
    public static double swordAuraMultiplier(Player player) {
        return player == null ? 1.0 : 1.0 + get(player).skill(Skill.SWORD_AURA) * 0.05;
    }

    /** 「处决」：对生命值低于这个比例的生物必定暴击，每级 +1%。 */
    public static double executeThreshold(Player player) {
        return player == null ? 0.0 : get(player).skill(Skill.EXECUTE) * 0.01;
    }

    /** 玩家身上选中的斗气能力；没有选中或槽是空的返回 null。 */
    public static Ability selectedAbility(Player player) {
        if (player == null) return null;
        return get(player).selected();
    }

    public static void sync(ServerPlayer player) {
        QiSyncPayload.send(player);
    }

    private static CompoundTag tag(Player player) {
        ManaData.init(player);
        CompoundTag t = tagRaw(player);
        if (!t.getBoolean(INITIALIZED)) init(player);
        return t;
    }

    private static CompoundTag tagRaw(Player player) {
        // 根标签由 ManaData.init 保证存在；存在时 getCompound 返回的就是内部引用，改动会落盘。
        return player.getPersistentData().getCompound(ROOT);
    }

    private QiProgression() {}
}
