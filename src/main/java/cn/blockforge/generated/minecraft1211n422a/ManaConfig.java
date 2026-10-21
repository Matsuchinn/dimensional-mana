package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ManaConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue AURA_REDUCTION_PER_LAYER;
    public static final ModConfigSpec.DoubleValue AURA_REDUCTION_CAP;
    public static final ModConfigSpec.IntValue GLOBAL_AURA_CAP;
    public static final ModConfigSpec.DoubleValue DAMAGE_PER_AURA_COST;
    public static final ModConfigSpec.DoubleValue BASE_AURA_COST;
    public static final ModConfigSpec.IntValue MIN_AURA_COST;
    public static final ModConfigSpec.DoubleValue OVERCAP_AURA_DECAY;
    public static final ModConfigSpec.DoubleValue PLAYER_INITIAL_MANA;
    public static final ModConfigSpec.DoubleValue GLOBAL_MANA_CAP;
    public static final ModConfigSpec.DoubleValue PLAYER_MANA_REGEN;
    public static final ModConfigSpec.DoubleValue SLEEP_MANA_RESTORE_RATIO;
    public static final ModConfigSpec.DoubleValue OVERCHARGE_SAFE_RATIO;
    public static final ModConfigSpec.DoubleValue OVERCHARGE_DECAY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("mana");
        PLAYER_INITIAL_MANA = b.comment("玩家初始魔素与初始魔素上限")
                .translation("config.dimensional_mana.common.player_initial_mana")
                .defineInRange("player_initial_mana", 100.0, 1.0, 100_000_000.0);
        GLOBAL_MANA_CAP = b.comment("玩家魔素上限可被指令设置到的全局硬上限")
                .translation("config.dimensional_mana.common.global_mana_cap")
                .defineInRange("global_mana_cap", 100_000_000.0, 1.0, 1_000_000_000.0);
        PLAYER_MANA_REGEN = b.comment("玩家每秒自然恢复的基础魔素；实际恢复量还会加上魔素亲和度")
                .translation("config.dimensional_mana.common.player_mana_regen")
                .defineInRange("player_mana_regen", 1.0, 0.0, 1_000_000.0);
        SLEEP_MANA_RESTORE_RATIO = b.comment("完成一次睡眠恢复的最大魔素比例；0.5 代表 50%")
                .translation("config.dimensional_mana.common.sleep_mana_restore_ratio")
                .defineInRange("sleep_mana_restore_ratio", 0.5, 0.0, 1000.0);
        OVERCHARGE_SAFE_RATIO = b.comment("超量魔素安全阈值倍率；1.30 代表魔素上限的 130%")
                .translation("config.dimensional_mana.common.overcharge_safe_ratio")
                .defineInRange("overcharge_safe_ratio", 1.30, 1.0, 1000.0);
        OVERCHARGE_DECAY = b.comment("超出正常魔素上限时，每秒流失自身上限的比例")
                .translation("config.dimensional_mana.common.overcharge_decay")
                .defineInRange("overcharge_decay", 0.04, 0.0, 1000.0);
        b.pop();

        b.push("battle_aura");
        AURA_REDUCTION_PER_LAYER = b.comment("单层斗气减伤比例，0.0001 = 0.01%")
                .translation("config.dimensional_mana.common.reduction_per_layer")
                .defineInRange("reduction_per_layer", 0.0001, 0.0, 1.0);
        AURA_REDUCTION_CAP = b.comment("斗气减伤最高比例")
                .translation("config.dimensional_mana.common.reduction_cap")
                .defineInRange("reduction_cap", 0.80, 0.0, 1.0);
        GLOBAL_AURA_CAP = b.comment("斗气层数全局硬上限")
                .translation("config.dimensional_mana.common.global_layer_cap")
                .defineInRange("global_layer_cap", 10_000_000, 1, 100_000_000);
        DAMAGE_PER_AURA_COST = b.comment("每多少点伤害作为一次斗气消耗单位")
                .translation("config.dimensional_mana.common.damage_threshold")
                .defineInRange("damage_threshold", 1.0, 0.01, 10000.0);
        BASE_AURA_COST = b.comment("伤害消耗斗气基础系数")
                .translation("config.dimensional_mana.common.base_cost_multiplier")
                .defineInRange("base_cost_multiplier", 1.0, 0.0, 1000.0);
        MIN_AURA_COST = b.comment("受到有效伤害时最低消耗层数")
                .translation("config.dimensional_mana.common.minimum_cost")
                .defineInRange("minimum_cost", 1, 0, 100000);
        OVERCAP_AURA_DECAY = b.comment("超限斗气每秒流失自身上限的比例")
                .translation("config.dimensional_mana.common.overcap_decay")
                .defineInRange("overcap_decay", 0.05, 0.0, 1.0);
        DIMENSION_WHITELIST = b.comment("允许常规生物生成斗气的维度；留空代表全部")
                .translation("config.dimensional_mana.common.dimension_whitelist")
                .defineListAllowEmpty("dimension_whitelist", List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), o -> o instanceof String);
        ENTITY_BLACKLIST = b.comment("禁止生成斗气的实体 ID")
                .translation("config.dimensional_mana.common.entity_blacklist")
                .defineListAllowEmpty("entity_blacklist", List.of(), o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private ManaConfig() {}
}
