package cn.blockforge.generated.minecraft1211n422a;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组自己那几套野外刷怪的开关（配置文件 {@code dimensional_mana-spawns.toml}）。
 *
 * <p>原版的世界设置 / 游戏规则 {@code doMobSpawning} 只关得掉原版的自然刷怪，
 * 管不到模组自己往世界里塞的哥布林巡逻队、营地居民、魔晶洞穴史莱姆和祭坛守卫；
 * 玩家想把整个世界的生物关掉时，就会觉得「关了还是刷」。
 * 这里给一个总开关，并默认跟随原版规则，具体判定见 {@link SpawnControl}。</p>
 */
public final class SpawnConfig {
    public static final ModConfigSpec SPEC;

    /** 模组自己的野外刷怪总开关；关掉后巡逻、营地、洞穴、守卫都不再生成。 */
    public static final ModConfigSpec.BooleanValue MOD_SPAWNING_ENABLED;

    /** 是否跟随原版的刷怪规则（doMobSpawning 与和平难度）。 */
    public static final ModConfigSpec.BooleanValue FOLLOW_VANILLA_SPAWNING_RULES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("模组自己的野外刷怪：哥布林巡逻、哥布林营地居民、魔晶洞穴史莱姆、祭坛守卫与冒险者")
                .push("world_spawning");
        MOD_SPAWNING_ENABLED = b.comment("模组刷怪总开关。false = 这些生物只在法术召唤、刷怪蛋、结构命令里出现，"
                        + "不再随世界自动生成")
                .translation("config.dimensional_mana.spawn.mod_spawning_enabled")
                .define("modSpawningEnabled", true);
        FOLLOW_VANILLA_SPAWNING_RULES = b.comment("跟随原版规则：/gamerule doMobSpawning false 时模组也停止刷怪，"
                        + "和平难度下不生成敌对生物。true = 想彻底关掉模组刷怪，建议连同上一项一起关闭")
                .translation("config.dimensional_mana.spawn.follow_vanilla_spawning_rules")
                .define("followVanillaSpawningRules", true);
        b.pop();
        SPEC = b.build();
    }

    private SpawnConfig() { }
}
