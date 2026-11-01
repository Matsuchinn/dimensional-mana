package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 战术撤退：血量低于 30%，或者刚被光 / 火属性打中时，回自己的魔蚀蛛网里蹲着恢复。
 *
 * <p>找网顺序（关键改动）：</p>
 * <ol>
 *   <li>24 格内有自己的网 → 跑过去；</li>
 *   <li><b>没有网 → 就地自己织一张</b>蹲进去（{@link CorruptedWeaverEntity#weaveRecoveryWeb()}）。
 *       以前这一步不存在，于是它在没有网的地形里会一直往远处跑，跑到撤退时限用完才停，
 *       玩家看到的就是「血少了就一直跑路」；</li>
 *   <li>脚下织不了网（站在基岩 / 箱子上、泡在水里…）→ 找最近的墙爬上去；</li>
 *   <li>连墙都没有 → 背对目标退开，同时每 20 刻再试一次织网。</li>
 * </ol>
 *
 * <p>贴着网时每 2 刻回 1 血、2 魔素（结算在 {@link CorruptedWeaverEntity#tick()}）。
 * 撤离时长有两种：血量撑不住时最多 15 秒，只是被光 / 火烫到的话只躲 2 秒
 * （{@link CorruptedWeaverEntity#wantsToRetreat()}），免得玩家拿火焰武器点一下它就杵着不动。</p>
 */
public final class CorruptedWeaverRetreatGoal extends Goal {
    /** 找网的半径（格）。 */
    private static final double WEB_SEARCH_RADIUS = 24.0D;
    /** 撤退结束后至少 5 秒才会再躲。 */
    private static final int RECHECK_COOLDOWN = 100;
    /** 没网的时候多久尝试织一次网。 */
    private static final int WEAVE_RETRY_TICKS = 20;

    private final CorruptedWeaverEntity weaver;
    private int repathTicks;
    private long nextAllowedAt;

    public CorruptedWeaverRetreatGoal(CorruptedWeaverEntity weaver) {
        this.weaver = weaver;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = weaver.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (weaver.level().getGameTime() < nextAllowedAt) return false;
        if (!weaver.wantsToRetreat()) return false;
        return weaver.retreatTicks() < CorruptedWeaverEntity.RETREAT_MAX_TICKS;
    }

    @Override
    public void start() {
        repathTicks = 0;
        weaver.resetRetreat();
        navigateToSafety();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = weaver.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (weaver.retreatTicks() >= CorruptedWeaverEntity.RETREAT_MAX_TICKS) return false;
        return weaver.wantsToRetreat();
    }

    @Override
    public void tick() {
        repathTicks++;
        LivingEntity target = weaver.getTarget();
        if (target != null) weaver.getLookControl().setLookAt(target, 30.0F, 30.0F);
        weaver.tickRetreat();
        // 已经蹲在网里了：停住享受回血回蓝。
        if (weaver.standingInWeb()) {
            weaver.getNavigation().stop();
            return;
        }
        if (repathTicks % WEAVE_RETRY_TICKS == 0) navigateToSafety();
    }

    @Override
    public void stop() {
        weaver.getNavigation().stop();
        // 被更高优先级的战术（比如招架识破）打断时，只要还想躲就允许下一刻接着躲；
        // 只有「真正躲完」或「不想躲了」才进 5 秒冷却，避免低血量时无限躲着不出来。
        boolean finished = weaver.retreatTicks() >= CorruptedWeaverEntity.RETREAT_MAX_TICKS
                || !weaver.wantsToRetreat();
        weaver.resetRetreat();
        nextAllowedAt = finished ? weaver.level().getGameTime() + RECHECK_COOLDOWN : 0L;
    }

    /** 有网回网里；没网就地织一张；织不了就找墙爬；连墙都没有才往后退。 */
    private void navigateToSafety() {
        BlockPos web = nearestWeb();
        if (web != null) {
            weaver.getNavigation().moveTo(web.getX() + 0.5D, web.getY(), web.getZ() + 0.5D, 1.15D);
            return;
        }
        // 附近没有现成的网：自己织一张，蹲进去恢复。这就是「找不到窝就自己搭一个」。
        if (weaver.weaveRecoveryWeb()) {
            weaver.getNavigation().stop();
            return;
        }
        Vec3 wall = weaver.findClimbPoint();
        if (wall != null) {
            weaver.getNavigation().moveTo(wall.x, wall.y, wall.z, 1.15D);
            return;
        }
        LivingEntity target = weaver.getTarget();
        if (target == null) return;
        Vec3 away = weaver.position().subtract(target.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-4) return;
        Vec3 point = weaver.position().add(away.normalize().scale(8.0D));
        weaver.getNavigation().moveTo(point.x, point.y, point.z, 1.2D);
    }

    /** 记账列表里最近、且确实还是魔蚀蛛网的那一张。 */
    private BlockPos nearestWeb() {
        BlockPos best = null;
        double bestDistance = WEB_SEARCH_RADIUS * WEB_SEARCH_RADIUS;
        for (BlockPos pos : weaver.knownWebs()) {
            if (!(weaver.level().getBlockState(pos).getBlock() instanceof CorruptedWebBlock)) continue;
            double distance = pos.distSqr(weaver.blockPosition());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }
}
