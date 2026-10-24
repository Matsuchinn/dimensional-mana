package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 生物暴击系统的配置文件（{@code config/dimensional_mana-critical.toml}）。
 *
 * <p>暴击按世界难度决定概率与倍率：和平不暴击，简单 5% / 1.25 倍，普通 10% / 1.5 倍，
 * 困难 15% / 1.75 倍。倍率是「这一下伤害的倍数」，写在受击结算之前，
 * 所以护甲、抗性、斗气等减伤照常叠在暴击后的数字上。</p>
 *
 * <p>四个难度的数值都能单独改，也可以整条关掉；服务端改完立即生效（COMMON 配置）。</p>
 */
public final class CriticalConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue PLAYER_ATTACKS_CAN_CRIT;
    public static final ModConfigSpec.BooleanValue SHOW_EFFECTS;

    public static final ModConfigSpec.DoubleValue PEACE_CHANCE;
    public static final ModConfigSpec.DoubleValue PEACE_DAMAGE;
    public static final ModConfigSpec.DoubleValue EASY_CHANCE;
    public static final ModConfigSpec.DoubleValue EASY_DAMAGE;
    public static final ModConfigSpec.DoubleValue NORMAL_CHANCE;
    public static final ModConfigSpec.DoubleValue NORMAL_DAMAGE;
    public static final ModConfigSpec.DoubleValue HARD_CHANCE;
    public static final ModConfigSpec.DoubleValue HARD_DAMAGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("critical");
        ENABLED = b.comment("是否启用生物暴击系统；关掉后所有暴击饰品只保留面板效果，不再掷暴击")
                .translation("config.dimensional_mana.critical.enabled")
                .define("enabled", true);
        PLAYER_ATTACKS_CAN_CRIT = b.comment("玩家的近战是否也吃这套暴击判定（原版跳劈暴击始终保留）")
                .translation("config.dimensional_mana.critical.player_attacks")
                .define("player_attacks_can_crit", true);
        SHOW_EFFECTS = b.comment("暴击命中时是否播放暴击粒子与音效")
                .translation("config.dimensional_mana.critical.show_effects")
                .define("show_effects", true);

        b.push("peace");
        PEACE_CHANCE = b.comment("和平难度的暴击率；0.0 表示不暴击")
                .translation("config.dimensional_mana.critical.peace_chance")
                .defineInRange("chance", 0.0, 0.0, 1.0);
        PEACE_DAMAGE = b.comment("和平难度的暴击伤害倍率")
                .translation("config.dimensional_mana.critical.peace_damage")
                .defineInRange("damage", 1.0, 1.0, 100.0);
        b.pop();

        b.push("easy");
        EASY_CHANCE = b.comment("简单难度的暴击率")
                .translation("config.dimensional_mana.critical.easy_chance")
                .defineInRange("chance", 0.05, 0.0, 1.0);
        EASY_DAMAGE = b.comment("简单难度的暴击伤害倍率")
                .translation("config.dimensional_mana.critical.easy_damage")
                .defineInRange("damage", 1.25, 1.0, 100.0);
        b.pop();

        b.push("normal");
        NORMAL_CHANCE = b.comment("普通难度的暴击率")
                .translation("config.dimensional_mana.critical.normal_chance")
                .defineInRange("chance", 0.10, 0.0, 1.0);
        NORMAL_DAMAGE = b.comment("普通难度的暴击伤害倍率")
                .translation("config.dimensional_mana.critical.normal_damage")
                .defineInRange("damage", 1.50, 1.0, 100.0);
        b.pop();

        b.push("hard");
        HARD_CHANCE = b.comment("困难难度的暴击率")
                .translation("config.dimensional_mana.critical.hard_chance")
                .defineInRange("chance", 0.15, 0.0, 1.0);
        HARD_DAMAGE = b.comment("困难难度的暴击伤害倍率")
                .translation("config.dimensional_mana.critical.hard_damage")
                .defineInRange("damage", 1.75, 1.0, 100.0);
        b.pop();

        b.pop();
        SPEC = b.build();
    }

    private CriticalConfig() { }
}
