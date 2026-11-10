package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 魔蚀织网者的【虫群意识】（Hive Mind）：第一次咬到 / 蛛丝球黏到目标时，把 64 格内所有节肢动物
 * 一起叫过来围殴这一个目标。
 *
 * <p><b>触发：</b>近战毒牙噬咬命中、或蛛丝球黏住一个活着的生物。每次呼叫后这只织网者进
 * {@link #COOLDOWN_TICKS}（30 秒）冷却，写在它自己的 NBT 里（{@code WeaverHiveReady}），
 * 重进存档也不会白赚一次。</p>
 *
 * <p><b>呼叫谁：</b>以织网者为中心 {@link #CALL_RADIUS} 格内、原版 {@code #minecraft:arthropod}
 * 标签里的生物（蜘蛛、洞穴蜘蛛、蠹虫、末影螨、蜜蜂……，再加上本模组的魔蚀织网者——
 * 它自己的实体类型由 {@code data/minecraft/tags/entity_type/arthropod.json} 补进标签）。
 * 只叫最近的 {@link #MAX_CALLED} 只：加上动手的那只织网者，最多 16 个仇恨源，
 * 刷怪笼一次爆出几十只时也不会把服务器拖垮。</p>
 *
 * <p><b>被叫来的生物获得：</b></p>
 * <ul>
 *   <li>仇恨强制锁到目标身上（{@link #onLevelTick} 每 5 刻补一次，别的目标 Goal 抢不走）；</li>
 *   <li>{@link HiveFrenzyEffect 狂化} 10 秒：移速 +50%、受伤 −25%（{@link #onIncomingDamage}）；</li>
 *   <li>一撮暗红粒子 + 一声节肢摩擦声，玩家能立刻看出「这一群被叫过来了」。</li>
 * </ul>
 *
 * <p><b>秽蚀蠹（{@link CorruptedSilverfishEntity}）另算：</b>一次呼救最多叫 2~3 只，
 * 且被叫来的一律回到一阶（24 血）入场、直接钻地冲到目标脚边破土（
 * {@link CorruptedSilverfishEntity#beginDigRush}）。数量卡死是防秒杀：一阶就有 2 物理 + 2 奥术
 * + 1 毒和三层剧毒，一叫十五只玩家连反应的时间都没有。</p>
 *
 * <p>蜜蜂不在呼叫名单里：它确实是节肢动物，但让它去蜇人等于白白弄死玩家蜂箱里的蜂，
 * 这种「帮倒忙」的召唤不算增援。</p>
 */
public final class HiveMind {
    /** 每只织网者的呼叫冷却：30 秒。 */
    public static final int COOLDOWN_TICKS = 600;
    /** 搜索半径：64 格。 */
    public static final double CALL_RADIUS = 64.0D;
    /** 最多额外呼叫多少只（加上织网者本身封顶 16 个仇恨源）。 */
    public static final int MAX_CALLED = 15;
    /** 狂化持续 10 秒。 */
    public static final int FRENZY_TICKS = 200;
    /** 仇恨锁的补写间隔（刻）。 */
    private static final int LOCK_CHECK_INTERVAL = 5;
    /** 被锁住的生物多久补一次暗红粒子（刻）。 */
    private static final int PARTICLE_INTERVAL = 10;

    /** 暗红虫尘。 */
    private static final ParticleOptions HIVE_DUST =
            new DustParticleOptions(new Vector3f(0.52F, 0.06F, 0.06F), 1.15F);

    /** 一次呼叫里的一个被叫生物：只记 UUID + 目标，过期或目标没了就丢。 */
    private record Call(ResourceKey<Level> dimension, UUID victim, long expireAt) { }

    /** 还处在仇恨锁里的生物（键是生物 UUID）。 */
    private static final Map<UUID, Call> CALLS = new HashMap<>();

    private HiveMind() { }

    // ---------------------------------------------------------------- 触发
    /**
     * 织网者命中目标时调用（近战噬咬 / 蛛丝球都走这里）。
     *
     * <p>冷却没好、目标已经死了、或者不在服务端，都直接返回。</p>
     */
    public static void onHiveStrike(CorruptedWeaverEntity weaver, LivingEntity victim) {
        if (weaver == null || victim == null || !victim.isAlive()) return;
        if (!(weaver.level() instanceof ServerLevel level)) return;
        if (!weaver.hiveMindReady()) return;
        weaver.cooldownHiveMind();

        List<Mob> candidates = new ArrayList<>(level.getEntitiesOfClass(Mob.class,
                weaver.getBoundingBox().inflate(CALL_RADIUS),
                mob -> mob != weaver && mob != victim && mob.isAlive() && !mob.isSpectator()
                        && mob.distanceToSqr(weaver) <= CALL_RADIUS * CALL_RADIUS
                        && isSwarmMember(mob)));
        // 由近到远：先叫最近的一圈，远处的留给下一次呼叫。
        candidates.sort(Comparator.comparingDouble(weaver::distanceToSqr));

        long now = level.getGameTime();
        int called = 0;
        // 秽蚀蠹每次最多只叫 2~3 只：它一阶就有 24 血、还带三层剧毒，一叫一大群会直接秒人。
        int silverfishQuota = 2 + level.getRandom().nextInt(2);
        for (Mob mob : candidates) {
            if (called >= MAX_CALLED) break;
            if (!mob.canAttack(victim)) continue;
            boolean silverfish = mob instanceof CorruptedSilverfishEntity;
            if (silverfish) {
                // 名额用完了就跳过，把剩下的位置留给蜘蛛 / 蠹虫这些普通援军。
                if (silverfishQuota <= 0) continue;
                silverfishQuota--;
            }
            mob.setTarget(victim);
            mob.addEffect(new MobEffectInstance(GeneratedMod.HIVE_FRENZY, FRENZY_TICKS, 0,
                    true, true, true));
            CALLS.put(mob.getUUID(), new Call(level.dimension(), victim.getUUID(), now + FRENZY_TICKS));
            if (silverfish) {
                // 被召唤的秽蚀蠹一律从一阶（24 血）重新入场，并且钻地从地下赶到目标脚边。
                CorruptedSilverfishEntity bug = (CorruptedSilverfishEntity) mob;
                bug.resetToStageOne();
                bug.beginDigRush(victim);
            } else {
                spawnSummonParticles(level, mob);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        GeneratedMod.CORRUPTED_WEAVER_HIVE.get(), SoundSource.HOSTILE,
                        0.45F, 1.05F + level.getRandom().nextFloat() * 0.7F);
            }
            called++;
        }

        // 织网者本身那一声：低沉的摩擦 + 一声尖响，盖住整片虫群。
        level.playSound(null, weaver.getX(), weaver.getY(), weaver.getZ(),
                GeneratedMod.CORRUPTED_WEAVER_HIVE.get(), SoundSource.HOSTILE, 1.35F, 0.62F);
        level.playSound(null, weaver.getX(), weaver.getY(), weaver.getZ(),
                GeneratedMod.CORRUPTED_WEAVER_HIVE.get(), SoundSource.HOSTILE, 0.9F, 1.65F);
        Vec3 center = weaver.getBoundingBox().getCenter();
        level.sendParticles(HIVE_DUST, center.x, center.y, center.z,
                24, 1.1D, 0.6D, 1.1D, 0.05D);
    }

    /** 这一只是不是「虫群」成员：原版节肢动物标签，外加所有蜘蛛 / 蠹虫子类（覆盖没打标签的模组蜘蛛）。 */
    private static boolean isSwarmMember(Mob mob) {
        // 蜜蜂排除：叫它来蜇人只会白白送掉玩家的蜂。
        if (mob instanceof Bee) return false;
        if (mob.getType().is(EntityTypeTags.ARTHROPOD)) return true;
        return mob instanceof Spider || mob instanceof Silverfish;
    }

    private static void spawnSummonParticles(ServerLevel level, Mob mob) {
        Vec3 center = mob.getBoundingBox().getCenter();
        level.sendParticles(HIVE_DUST, center.x, center.y, center.z,
                14, mob.getBbWidth() * 0.55D, mob.getBbHeight() * 0.55D, mob.getBbWidth() * 0.55D, 0.02D);
    }

    // ---------------------------------------------------------------- 仇恨锁 + 持续粒子
    /**
     * 每 5 刻把被叫生物的仇恨重新按回目标身上，并补一点暗红粒子。
     *
     * <p>蜘蛛有 {@code HurtByTargetGoal}、铁傀儡有 {@code NearestAttackableTargetGoal}，
     * 不补的话它们被谁碰一下就会掉头，虫群看起来就散了。</p>
     */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (CALLS.isEmpty()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now % LOCK_CHECK_INTERVAL != 0) return;
        boolean particles = now % PARTICLE_INTERVAL == 0;

        Iterator<Map.Entry<UUID, Call>> iterator = CALLS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Call> entry = iterator.next();
            Call call = entry.getValue();
            if (!call.dimension().equals(level.dimension())) continue;
            if (now > call.expireAt()) {
                iterator.remove();
                continue;
            }
            if (!(level.getEntity(entry.getKey()) instanceof Mob mob) || !mob.isAlive()) {
                iterator.remove();
                continue;
            }
            if (!(level.getEntity(call.victim()) instanceof LivingEntity victim) || !victim.isAlive()) {
                iterator.remove();
                continue;
            }
            if (mob.getTarget() != victim) mob.setTarget(victim);
            if (particles) {
                Vec3 center = mob.getBoundingBox().getCenter();
                level.sendParticles(HIVE_DUST, center.x, center.y, center.z,
                        2, mob.getBbWidth() * 0.45D, mob.getBbHeight() * 0.45D, mob.getBbWidth() * 0.45D, 0.0D);
            }
        }
    }

    // ---------------------------------------------------------------- 减伤
    /** 狂化期间受到的所有伤害 ×0.75；必杀伤害（/kill）不参与任何乘区。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (victim.getEffect(GeneratedMod.HIVE_FRENZY) == null) return;
        if (LethalDamage.isLethal(event.getSource(), event.getAmount())) return;
        event.setAmount(event.getAmount() * HiveFrenzyEffect.DAMAGE_TAKEN_MULTIPLIER);
    }
}
