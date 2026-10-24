package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.Minecraft;

/**
 * 斗气进度的客户端只读镜像：等级、经验、技能点、六项技能等级、三项能力与快捷盘。
 *
 * <p>服务端每秒同步一次，加点 / 换槽 / 升级时会立即补发。界面与 HUD 都只读这份缓存，
 * 不做任何判定。</p>
 */
public final class ClientQiState {
    private static int level;
    private static long experience;
    private static int requiredExperience;
    private static int skillPoints;
    private static int spentSkillPoints;
    private static int[] skills = new int[QiProgression.Skill.values().length];
    private static boolean[] abilities = new boolean[QiProgression.Ability.values().length];
    private static String[] slots = new String[QiProgression.SLOT_COUNT];
    private static int selectedSlot;

    public static void update(QiSyncPayload payload) {
        level = payload.level();
        experience = payload.experience();
        requiredExperience = payload.requiredExperience();
        skillPoints = payload.skillPoints();
        spentSkillPoints = payload.spentSkillPoints();
        skills = payload.skills().clone();
        abilities = payload.abilities().clone();
        slots = new String[QiProgression.SLOT_COUNT];
        System.arraycopy(payload.slots(), 0, slots, 0, Math.min(slots.length, payload.slots().length));
        selectedSlot = Math.max(0, Math.min(QiProgression.SLOT_COUNT - 1, payload.selectedSlot()));
    }

    public static void clear() {
        level = 0;
        experience = 0;
        requiredExperience = 0;
        skillPoints = 0;
        spentSkillPoints = 0;
        skills = new int[QiProgression.Skill.values().length];
        abilities = new boolean[QiProgression.Ability.values().length];
        slots = new String[QiProgression.SLOT_COUNT];
        selectedSlot = 0;
    }

    public static int level() { return level; }
    public static long experience() { return experience; }
    public static int requiredExperience() { return requiredExperience; }
    public static int skillPoints() { return skillPoints; }
    public static int spentSkillPoints() { return spentSkillPoints; }

    public static int skill(QiProgression.Skill skill) {
        return skill != null && skill.ordinal() < skills.length ? skills[skill.ordinal()] : 0;
    }

    public static boolean learned(QiProgression.Ability ability) {
        return ability != null && ability.ordinal() < abilities.length && abilities[ability.ordinal()];
    }

    public static int learnedCount() {
        int count = 0;
        for (boolean value : abilities) if (value) count++;
        return count;
    }

    public static String slotId(int index) {
        return index >= 0 && index < slots.length ? slots[index] : "";
    }

    public static QiProgression.Ability abilityAt(int index) {
        return QiProgression.Ability.byId(slotId(index));
    }

    public static int selectedSlot() { return selectedSlot; }

    public static QiProgression.Ability selected() { return abilityAt(selectedSlot); }

    /** 界面重建用的指纹：任何一项数值变化都会让它变。 */
    public static int fingerprint() {
        int value = level * 31 + skillPoints;
        for (int skill : skills) value = value * 31 + skill;
        for (boolean ability : abilities) value = value * 31 + (ability ? 1 : 0);
        for (String slot : slots) value = value * 31 + (slot == null ? 0 : slot.hashCode());
        value = value * 31 + selectedSlot;
        value = value * 31 + (int)experience;
        return value;
    }

    /** 界面上「斗气值」读的是实时同步的魔素池，这里顺手提供便利方法。 */
    public static int battleQi() { return ClientManaState.battleQi(); }
    public static int maxBattleQi() { return ClientManaState.maxBattleQi(); }

    /** 玩家有没有佩戴某件 Curios 饰品（界面展示用，判定仍在服务端）。 */
    public static boolean wearing(Class<?> itemClass) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(minecraft.player)
                .map(handler -> handler.isEquipped(itemClass::isInstance))
                .orElse(false);
    }

    private ClientQiState() {}
}
