package cn.blockforge.generated.minecraft1211n422a;

public final class ClientProgressState {
    private static int magicLevel;
    private static long magicExperience;
    private static int requiredExperience = 64;
    private static int skillPoints = 6;
    private static int spentSkillPoints;
    private static int[] skills = new int[MagicProgression.Skill.values().length];
    private static double affinity = 1.0;
    private static double concentration = 10.0;
    private static int dailyGrowths;

    public static void update(ProgressSyncPayload payload) {
        magicLevel = payload.magicLevel();
        magicExperience = payload.magicExperience();
        requiredExperience = payload.requiredExperience();
        skillPoints = payload.skillPoints();
        spentSkillPoints = payload.spentSkillPoints();
        skills = payload.skillLevels().clone();
        affinity = payload.affinity();
        concentration = payload.concentration();
        dailyGrowths = payload.dailyGrowths();
    }

    public static int magicLevel() { return magicLevel; }
    public static long magicExperience() { return magicExperience; }
    public static int requiredExperience() { return requiredExperience; }
    public static int skillPoints() { return skillPoints; }
    public static int spentSkillPoints() { return spentSkillPoints; }
    public static int skill(MagicProgression.Skill skill) {
        return skill.ordinal() < skills.length ? skills[skill.ordinal()] : 0;
    }
    public static double affinity() { return affinity; }
    public static double concentration() { return concentration; }
    public static int dailyGrowths() { return dailyGrowths; }

    private ClientProgressState() {}
}
