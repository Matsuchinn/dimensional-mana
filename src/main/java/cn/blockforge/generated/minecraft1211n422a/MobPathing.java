package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 本模组生物的「追击寻路小管家」。
 *
 * <p><b>修的是什么：</b>以前很多自定义 Goal 每刻都调一次
 * {@code getNavigation().moveTo(目标)}。目标被围墙 / 围栏 / 玻璃挡在外面时，A* 每次都会
 * 算出「无路可走」，但下一 tick 又从头再算一遍 —— 表现就是怪贴着墙来回蹭、蹭到天荒地老，
 * 战斗、放技能、回营地这些事全都不干了。原版 {@code MeleeAttackGoal} 至少在失败时把重算
 * 间隔拉长 15 刻，自定义 Goal 连这一步都没有。</p>
 *
 * <p><b>怎么治：</b></p>
 * <ol>
 *   <li><b>节流</b>：正常追击 10 刻才重算一次路径，失败后歇 14 刻，不再每刻跑一遍 A*；</li>
 *   <li><b>认命</b>：连续 3 次都找不到路（约 0.7 秒，中间隔着 14 刻），就判定「这个目标
 *       走不到」，立刻停手、清空目标，并把这条「怪 → 目标」的路线记进黑名单；</li>
 *   <li><b>记性</b>：黑名单 10 秒。这 10 秒里目标选择 Goal 会跳过它（{@link PathAwareTargetGoal}），
 *       怪不会刚放手又被同一个够不着的人拉回去，而是老老实实回去巡逻 / 采东西 / 闲逛。</li>
 * </ol>
 *
 * <p>记录按实体 UUID 存放，{@link #tickLevel} 每 20 刻清一次过期条目，长期开服也不会越积越多。</p>
 */
public final class MobPathing {
    /** 正常追击时，两次重算路径之间的最小间隔（刻）。 */
    private static final int REPATH_INTERVAL = 10;
    /** 一次寻路失败后先歇多久再试（刻）：约等于原版 MeleeAttackGoal 的 15 刻惩罚。 */
    private static final int FAILED_REPATH_DELAY = 14;
    /** 走到固定坐标（巡逻点、后撤点）时，两次重算之间的间隔（刻）。 */
    private static final int POINT_REPATH_INTERVAL = 20;
    /** 连续几次找不到路就判定目标走不到。 */
    private static final int MAX_FAILURES = 3;
    /** 判定走不到之后，多久不再理它（刻）= 10 秒。 */
    public static final int UNREACHABLE_TICKS = 200;
    /** 一条记录多久没被用到就丢掉（刻）= 60 秒。 */
    private static final int IDLE_EXPIRE = 1200;

    /** 追击时每隔多少刻检查一次「有没有真的在靠近」。 */
    private static final int STALL_CHECK_TICKS = 20;
    /** 20 刻里挪动不超过 0.5 格，就算原地磨。 */
    private static final double STALL_MOVE_SQR = 0.25D;
    /** 连续几次「原地磨」就放弃（3 次 = 60 刻 = 3 秒）。 */
    private static final int MAX_STALLS = 3;
    /** 离目标这么近（5 格）就不算卡住：贴身缠斗时人本来就不怎么动。 */
    private static final double STALL_MIN_DISTANCE_SQR = 25.0D;
    /** 走固定点时，新目标超过这个距离就立刻重算，不等节流。 */
    private static final double POINT_CHANGE_SQR = 1.0D;

    private static final Map<UUID, Track> TARGET_TRACKS = new HashMap<>();
    private static final Map<UUID, PointTrack> POINT_TRACKS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> UNREACHABLE = new HashMap<>();

    private MobPathing() { }

    /** 追实体用的状态：这次追的是谁、连败几次、下次什么时候才允许重算、有没有在原地磨。 */
    private static final class Track {
        private UUID targetId;
        private int failures;
        private long nextPath;
        private long lastUse;
        private long nextStallCheck;
        private int stalls;
        private double lastX;
        private double lastZ;
        private double lastDistance;

        private void retarget(UUID id, Mob mob, double distance) {
            targetId = id;
            failures = 0;
            nextPath = 0L;
            stalls = 0;
            nextStallCheck = 0L;
            lastX = mob.getX();
            lastZ = mob.getZ();
            lastDistance = distance;
        }
    }

    /** 走固定坐标用的状态：下次什么时候允许重算 + 上次去的点。 */
    private static final class PointTrack {
        private long nextPath;
        private long lastUse;
        private Vec3 point;
    }

    // ---------------------------------------------------------------- 追击

    /**
     * 朝目标追过去：节流重算 + 连败 / 原地磨判定，代替直接调 {@code getNavigation().moveTo}。
     *
     * <p>返回 {@code true} = 路是通的（在走或者已经到了）；{@code false} = 这一下没走成，
     * 要么还在等下次重算，要么目标已被判定为走不到。调用方按普通「移动成功 / 失败」处理即可。</p>
     */
    public static boolean chase(Mob mob, Entity target, double speed) {
        if (mob == null || target == null || mob.level().isClientSide) return false;
        PathNavigation navigation = mob.getNavigation();
        // 已经被判定走不到：这 10 秒里一次路都不要算，省 CPU 也不会再贴墙。
        if (isUnreachable(mob, target)) {
            navigation.stop();
            return false;
        }
        long now = mob.level().getGameTime();
        double distance = mob.distanceToSqr(target);
        Track track = TARGET_TRACKS.computeIfAbsent(mob.getUUID(), ignored -> new Track());
        if (!target.getUUID().equals(track.targetId)) {
            // 换目标了：失败次数、原地磨计数都重新数。
            track.retarget(target.getUUID(), mob, distance);
        }
        track.lastUse = now;
        // ① 路是通的但一步都挪不动（贴着围栏、卡在 1 格缝里）：也算走不到。
        if (trackStall(mob, target, track, navigation, now, distance)) {
            giveUp(mob, target, navigation);
            return false;
        }
        if (now < track.nextPath) return !navigation.isDone();
        boolean ok = navigation.moveTo(target, speed);
        if (ok) {
            track.failures = 0;
            track.nextPath = now + REPATH_INTERVAL;
            return true;
        }
        track.nextPath = now + FAILED_REPATH_DELAY;
        if (++track.failures < MAX_FAILURES) return false;
        // ② 连着三次都找不到路：被墙 / 围栏挡死了，认命放手。
        track.failures = 0;
        giveUp(mob, target, navigation);
        return false;
    }

    /**
     * 原地磨检测：A* 有时能算出一小段路（比如绕到围栏另一侧的门口再折回来），
     * 但怪实际上一格都不前进。这种情况路径一直是「通的」，靠 {@code moveTo} 的返回值看不出来，
     * 只能按位置采样：20 刻挪不到半格、而且离目标还远，就记一次磨蹭，连记三次就放弃。
     */
    private static boolean trackStall(Mob mob, Entity target, Track track, PathNavigation navigation,
                                      long now, double distance) {
        if (now < track.nextStallCheck) return false;
        track.nextStallCheck = now + STALL_CHECK_TICKS;
        // 没在沿路径走 = 调用方自己让它站住的（蓄力、施法），不算卡住。
        if (!navigation.isInProgress()) {
            track.stalls = 0;
            track.lastX = mob.getX();
            track.lastZ = mob.getZ();
            track.lastDistance = distance;
            return false;
        }
        double moved = mob.position().distanceToSqr(track.lastX, mob.getY(), track.lastZ);
        track.lastX = mob.getX();
        track.lastZ = mob.getZ();
        boolean closing = distance < track.lastDistance - 0.05D;
        track.lastDistance = distance;
        if (moved > STALL_MOVE_SQR || closing || distance < STALL_MIN_DISTANCE_SQR) {
            track.stalls = 0;
            return false;
        }
        if (++track.stalls < MAX_STALLS) return false;
        track.stalls = 0;
        return true;
    }

    /** 走到某个坐标（巡逻点、后撤点、集合点）：同样节流，失败就多等一会儿再试。 */
    public static boolean moveToPoint(Mob mob, Vec3 point, double speed) {
        if (mob == null || point == null || mob.level().isClientSide) return false;
        PathNavigation navigation = mob.getNavigation();
        long now = mob.level().getGameTime();
        PointTrack track = POINT_TRACKS.computeIfAbsent(mob.getUUID(), ignored -> new PointTrack());
        track.lastUse = now;
        // 换了一个明显不同的点（比如巡逻点刚更新）：立刻重算，不要被上一个点的节流挡住。
        if (track.point == null || track.point.distanceToSqr(point) > POINT_CHANGE_SQR) {
            track.point = point;
            track.nextPath = 0L;
        }
        if (now < track.nextPath) return !navigation.isDone();
        boolean ok = navigation.moveTo(point.x, point.y, point.z, speed);
        track.nextPath = now + (ok ? POINT_REPATH_INTERVAL : FAILED_REPATH_DELAY * 2);
        return ok;
    }

    // ---------------------------------------------------------------- 黑名单

    /** 这只怪最近是不是已经判定「追不到这个目标」（10 秒内）。 */
    public static boolean isUnreachable(Mob mob, Entity target) {
        if (mob == null || target == null) return false;
        Map<UUID, Long> blocked = UNREACHABLE.get(mob.getUUID());
        if (blocked == null) return false;
        Long expiresAt = blocked.get(target.getUUID());
        if (expiresAt == null) return false;
        if (mob.level().getGameTime() >= expiresAt) {
            blocked.remove(target.getUUID());
            return false;
        }
        return true;
    }

    /** 手动把「这只怪追不到这个目标」记一笔（史莱姆这类不寻路、只会往前蹦的生物用）。 */
    public static void markUnreachable(Mob mob, Entity target, int ticks) {
        if (mob == null || target == null || ticks <= 0) return;
        UNREACHABLE.computeIfAbsent(mob.getUUID(), ignored -> new HashMap<>())
                .put(target.getUUID(), mob.level().getGameTime() + ticks);
    }

    /** 放弃当前目标：停手、清空目标、记 10 秒黑名单。 */
    private static void giveUp(Mob mob, Entity target, PathNavigation navigation) {
        navigation.stop();
        markUnreachable(mob, target, UNREACHABLE_TICKS);
        if (mob.getTarget() == target) mob.setTarget(null);
    }

    /** 丢掉这只怪的全部寻路记录（死亡、被移除、换目标时随手清一下）。 */
    public static void forget(Mob mob) {
        if (mob == null) return;
        TARGET_TRACKS.remove(mob.getUUID());
        POINT_TRACKS.remove(mob.getUUID());
        UNREACHABLE.remove(mob.getUUID());
    }

    // ---------------------------------------------------------------- 可达性 / 清理

    /**
     * 这个坐标现在走得到吗？给巡逻、闲逛这类「挑一个点再过去」的 Goal 用。
     *
     * <p>内部真的跑一次 A*（{@code createPath}），只在挑点的时候调用 ——
     * 巡逻几秒才挑一次，开销可以忽略；换来的好处是绝不会挑到围墙外面的死点。</p>
     */
    public static boolean isReachable(Mob mob, Vec3 point) {
        if (mob == null || point == null || mob.level().isClientSide) return false;
        return mob.getNavigation().createPath(point.x, point.y, point.z, 1) != null;
    }

    /** 每 20 刻清一次过期记录（由 {@link MobSkills#tickLevel} 调用）。 */
    public static void tickLevel(ServerLevel level) {
        long now = level.getGameTime();
        TARGET_TRACKS.entrySet().removeIf(entry -> now - entry.getValue().lastUse > IDLE_EXPIRE);
        POINT_TRACKS.entrySet().removeIf(entry -> now - entry.getValue().lastUse > IDLE_EXPIRE);
        Iterator<Map.Entry<UUID, Map<UUID, Long>>> iterator = UNREACHABLE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, Long>> entry = iterator.next();
            entry.getValue().entrySet().removeIf(blocked -> blocked.getValue() <= now);
            if (entry.getValue().isEmpty()) iterator.remove();
        }
    }

    /** 服务器关闭时清空（和别的静态表一样，换世界不串数据）。 */
    public static void clearAll() {
        TARGET_TRACKS.clear();
        POINT_TRACKS.clear();
        UNREACHABLE.clear();
    }
}
