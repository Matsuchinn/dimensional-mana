package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;

/**
 * 统一的「现在到底允不允许模组自己刷怪」判定。
 *
 * <p>模组里的哥布林巡逻队、哥布林营地居民、魔晶洞穴史莱姆、祭坛守卫和祭坛之间的冒险者，
 * 都是我们自己在服务器刻里 {@code addFreshEntity} 塞进世界的，不走原版的自然刷怪器，
 * 所以原版的「禁止生物生成」（{@code /gamerule doMobSpawning false}）根本管不到它们。
 * 所有野外刷怪入口都先过这里，玩家关掉开关后模组就不再往世界里加生物。</p>
 *
 * <p>法术召唤、刷怪蛋、命令召唤这些由玩家主动触发的生成不经过这里，不受影响。</p>
 */
public final class SpawnControl {
    private SpawnControl() { }

    /** 模组自己的野外刷怪是否开着（配置没加载完时按默认开着处理）。 */
    public static boolean modSpawningEnabled() {
        return !SpawnConfig.SPEC.isLoaded() || SpawnConfig.MOD_SPAWNING_ENABLED.get();
    }

    /** 是否跟随原版的刷怪规则。 */
    private static boolean followsVanillaRules() {
        return !SpawnConfig.SPEC.isLoaded() || SpawnConfig.FOLLOW_VANILLA_SPAWNING_RULES.get();
    }

    /** 允许生成任何生物（含村民、冒险者这类非敌对单位）。 */
    public static boolean mobSpawningAllowed(ServerLevel level) {
        if (!modSpawningEnabled()) return false;
        return !followsVanillaRules() || level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }

    /** 允许生成敌对生物：除了上面的开关，和平难度下也不生成。 */
    public static boolean hostileSpawningAllowed(ServerLevel level) {
        if (!mobSpawningAllowed(level)) return false;
        return !followsVanillaRules() || level.getDifficulty() != Difficulty.PEACEFUL;
    }
}
