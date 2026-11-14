package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.List;

/**
 * 本模组生物「刷多少」的统一权重表。
 *
 * <p>每种生物在 {@link SpawnConfig} 里有一个 0～1000 的整数权重（默认 100）。这里提供三种用法：</p>
 *
 * <ul>
 *   <li>{@link #enabled(String)}：权重是不是 0。0 表示这种生物彻底不再自动生成，
 *       各生成器在「定额补刷」（洞穴、营地、矿簇守卫）这类不走概率的地方用它一刀切断。</li>
 *   <li>{@link #roll(RandomSource, float, String)}：把原来的固定概率乘以权重百分比。
 *       权重 50 时，原本 60% 的概率变成 30%。环境刷怪、遗迹补刷、原版生物替换都走它。</li>
 *   <li>{@link #pick(RandomSource, List)}：几种生物里「抽哪一种」的加权抽取。
 *       权重按比例分配，权重 0 的候选项永远抽不到；全被调到 0 时返回 {@code null}，调用方跳过这一次。</li>
 * </ul>
 *
 * <p>另外 {@link #onFinalizeSpawn} 兜住「数据驱动」那一路：荒原群系 JSON 里登记的
 * 晶化行尸 / 魔蚀骸骨 / 秽蚀蠹是原版刷怪器按 JSON 权重刷的，代码里没有抽取点，
 * 所以在生成落地的最后一刻按权重再放行一次——权重 50 就等于把 JSON 里的 weight 砍半。</p>
 *
 * <p>{@link #bypassing} 用于「我们自己已经判过权重、又要把生物造出来」的场合：
 * 包在里面的 {@code finalizeSpawn} 不会再被上面那道门二次衰减。</p>
 */
public final class MobSpawnWeights {
    /** 默认权重，和配置文件里的默认值一致。 */
    public static final int DEFAULT = SpawnConfig.DEFAULT_WEIGHT;

    /** 当前线程是否正在「我们自己生成生物」，是的话跳过 NATURAL 权重门。 */
    private static final ThreadLocal<Boolean> BYPASSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MobSpawnWeights() { }

    /** 某种生物的权重；配置文件还没加载时按 100 处理，未知生物也按 100。 */
    public static int weight(String mobKey) {
        return SpawnConfig.weightOf(mobKey);
    }

    /** 这种生物是否还允许自动生成（权重不为 0）。 */
    public static boolean enabled(String mobKey) {
        return weight(mobKey) > 0;
    }

    /** 权重换算成倍率：100 → 1.0，50 → 0.5，200 → 2.0。 */
    public static float scale(String mobKey) {
        return weight(mobKey) / (float) DEFAULT;
    }

    /**
     * 按权重缩放一次概率判定。
     *
     * <p>权重 0 直接失败；权重 ≥100 时把概率放大并截到 1（100 时就是原来那个概率本身）。
     * 原本就有固定概率的地方（环境刷怪、遗迹补刷、原版生物替换）全用这个，不另算。</p>
     */
    public static boolean roll(RandomSource random, float baseChance, String mobKey) {
        int value = weight(mobKey);
        if (value <= 0 || baseChance <= 0.0F) return false;
        if (value == DEFAULT) return random.nextFloat() < baseChance;
        return random.nextFloat() < Math.min(1.0F, baseChance * value / (float) DEFAULT);
    }

    /**
     * 在几个候选之间按权重抽取一个。
     *
     * <p>实际权重 = 候选自带的基准份额 × 配置权重。配置权重为 0 的候选直接跳过；
     * 所有候选都被关掉时返回 {@code null}，调用方应当把这一次生成整个放弃。</p>
     */
    public static <T> T pick(RandomSource random, List<Candidate<T>> pool) {
        long total = 0L;
        for (Candidate<T> candidate : pool) total += candidate.effectiveWeight();
        if (total <= 0L) return null;
        long roll = random.nextLong() % total;
        if (roll < 0L) roll += total;
        for (Candidate<T> candidate : pool) {
            roll -= candidate.effectiveWeight();
            if (roll < 0L) return candidate.value();
        }
        return null;
    }

    /** 加权抽取的候选项：{@code baseWeight} 是原来的比例，配置权重再乘上去。 */
    public record Candidate<T>(T value, String mobKey, int baseWeight) {
        public long effectiveWeight() {
            int configured = weight(mobKey);
            return configured <= 0 ? 0L : (long) baseWeight * configured;
        }
    }

    /** 候选的简写构造。 */
    public static <T> Candidate<T> candidate(T value, String mobKey, int baseWeight) {
        return new Candidate<>(value, mobKey, baseWeight);
    }

    /**
     * 数据驱动生成的兜底门：本模组生物走原版自然刷怪（{@link MobSpawnType#NATURAL}）时，
     * 按权重决定放不放行。
     *
     * <p>权重 100 恒放行、0 恒取消、50 取消一半——效果等同于把群系 JSON 里的 weight 乘上配置值。
     * 刷怪蛋、指令、结构、法术召唤都不经过这里。</p>
     */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled() || BYPASSING.get()) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        Mob mob = event.getEntity();
        String key = keyOf(mob);
        if (!SpawnConfig.hasWeight(key)) return;
        if (!roll(mob.getRandom(), 1.0F, key)) event.setSpawnCancelled(true);
    }

    /** 实体类型的注册 id（去掉 {@code dimensional_mana:} 前缀），与本模组配置项一一对应。 */
    public static String keyOf(Mob mob) {
        return keyOf(mob.getType());
    }

    public static String keyOf(net.minecraft.world.entity.EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return "";
        return GeneratedMod.MOD_ID.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    /** 在这个动作里生成的生物不再过 NATURAL 权重门（我们自己已经判过了）。 */
    public static void bypassing(Runnable action) {
        boolean previous = BYPASSING.get();
        BYPASSING.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            BYPASSING.set(previous);
        }
    }
}
