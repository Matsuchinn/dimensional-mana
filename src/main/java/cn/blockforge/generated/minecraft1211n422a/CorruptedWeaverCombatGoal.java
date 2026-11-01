package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 魔蚀织网者的主战斗 Goal：一条「按距离换招 + 连招」的状态机。
 *
 * <p><b>为什么不再拆成好几个 Goal：</b>以前是远程 / 空袭 / 近战三个独立 Goal，每个都只在很窄的
 * 距离段 {@code canUse}，于是出现了两段谁都不管的空档：</p>
 * <ul>
 *   <li>4~8 格：近战要 &lt; 4、空袭要 8~16，中间没人管；</li>
 *   <li>16 格以外：只有蛛丝球，一旦它在冷却、或者魔素不够、或者没有视线，就地发愣；</li>
 *   <li>每个 Goal 结束时都会 {@code getNavigation().stop()}，几个 Goal 来回抢，动作一顿一顿的。</li>
 * </ul>
 *
 * <p>现在只留这一个 Goal 全程握着 MOVE/LOOK：内部按距离挑招，<b>永远有一条「先压上去」的兜底路</b>，
 * 任何时刻都在移动或出手，不会再有空档。这个 Goal 对<b>任何目标</b>都生效（玩家、铁傀儡、任何还手的
 * 生物都一样），只有「招架识破」那一条是玩家专属。</p>
 *
 * <p><b>连招怎么串：</b></p>
 * <ol>
 *   <li><b>远距</b>（&gt; 16 格）：蛛丝球封走位 → 立刻前压；</li>
 *   <li><b>中距</b>（8~16 格，站地上）：高跳 → 顶点喷毒液射线 → 落地不停顿直接压上；</li>
 *   <li><b>近身</b>：噬咬 → 后跳 → 脚下吐网 → 20 刻内回扑<b>二段噬咬</b>（伤害 ×1.5、魔蚀升一级）
 *       → 绕圈拉开；</li>
 *   <li>追不到就前扑（{@link CorruptedWeaverEntity#leapToward}），不会站着等人过来。</li>
 * </ol>
 *
 * <p>性能：路径每 4~9 刻重算一次（失败再多等 8~10 刻），距离判断用 {@link CorruptedWeaverEntity}
 * 每 10 刻刷新的缓存，不在每刻做范围检索。</p>
 */
public final class CorruptedWeaverCombatGoal extends Goal {
    /** 咬得到的距离（格）。 */
    private static final double BITE_REACH = 2.7D;
    /** 出牙那一刻再放宽一点，手感才不粘手。 */
    private static final double BITE_STRIKE_REACH = 3.6D;
    /** 追身速度倍率。 */
    private static final double CHASE_SPEED = 1.3D;
    /** 绕圈速度倍率。 */
    private static final double ORBIT_SPEED = 1.05D;

    /** 噬咬前摇：5 刻的「后坐 + 紫尘聚口器」，玩家看得见它要下嘴。 */
    private static final int BITE_WINDUP = 5;
    /** 咬完的收招（同时是后跳滞空）。 */
    private static final int BITE_RECOVER = 9;
    /** 绕圈拉开多久。 */
    private static final int ORBIT_TICKS = 12;
    /** 蛛丝球前摇。 */
    private static final int WEB_WINDUP = 5;
    /** 吐完网停几刻再压上去。 */
    private static final int WEB_RECOVER = 5;
    /** 一次空袭最长持续 5 秒。 */
    private static final int BOMB_MAX_TICKS = 100;
    /** 追了多久还贴不上就换招。 */
    private static final int PRESS_PATIENCE = 26;
    /** 路径重算间隔（刻）。 */
    private static final int REPATH_MIN = 4;
    private static final int REPATH_MAX = 9;

    /** 现在在干什么。 */
    private enum Act {
        /** 压上去：追身 / 侧切，贴脸自动转噬咬。 */
        PRESS,
        /** 出手后的恢复：绕着目标侧移，绝不站桩。 */
        ORBIT,
        /** 近身噬咬（连招里会连着来两段）。 */
        BITE,
        /** 远距离蛛丝球。 */
        WEB,
        /** 高跳空袭。 */
        BOMB
    }

    private final CorruptedWeaverEntity weaver;
    /** 当前动作；{@code null} = 该重新挑招。 */
    private Act act;
    /** 当前动作已经进行了几刻。 */
    private int ticks;
    /** 距离下次重算路径还剩几刻。 */
    private int pathTicks;
    /** 本轮动作的主效果是否已结算。 */
    private boolean fired;
    /** 绕圈方向，每次绕完换一边，看起来像在找角度。 */
    private int orbitSign = 1;
    /** 连续几次追不上，用来决定要不要前扑。 */
    private int pressFailures;

    public CorruptedWeaverCombatGoal(CorruptedWeaverEntity weaver) {
        this.weaver = weaver;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = weaver.getTarget();
        return target != null && target.isAlive() && target.level() == weaver.level();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        act = null;
        ticks = 0;
        pathTicks = 0;
        fired = false;
        pressFailures = 0;
    }

    @Override
    public void stop() {
        act = null;
        // 只有真正脱离战斗才收脚；被撤退 / 识破打断时 navigation 由它们自己接管。
        weaver.getNavigation().stop();
        weaver.finishBombJump();
    }

    @Override
    public void tick() {
        LivingEntity target = weaver.getTarget();
        if (target == null) {
            act = null;
            return;
        }
        weaver.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (act == null) {
            choose(target);
            if (act == null) return;
        }
        ticks++;
        switch (act) {
            case PRESS -> tickPress(target);
            case ORBIT -> tickOrbit(target);
            case BITE -> tickBite(target);
            case WEB -> tickWeb(target);
            case BOMB -> tickBomb(target);
        }
    }

    // ---------------------------------------------------------------- 挑招
    /** 按距离和手头资源挑下一招；挑不出特殊招就一律「压上去」。 */
    private void choose(LivingEntity target) {
        ticks = 0;
        fired = false;
        double distance = Math.sqrt(weaver.distanceToSqr(target));
        boolean sight = weaver.hasLineOfSight(target);

        // ① 贴脸：直接开咬。近战不要魔素，什么时候都比远程划算。
        if (distance <= BITE_REACH && sight && weaver.ready(weaver.hitRunReadyAt())) {
            beginBite();
            return;
        }

        // ② 远距：先吐一发蛛丝球封住走位，吐完再压上去。
        if (distance > CorruptedWeaverEntity.SNIPER_RANGE && sight
                && weaver.ready(weaver.sniperReadyAt())
                && weaver.hasMana(CorruptedWeaverEntity.SNIPER_COST)) {
            act = Act.WEB;
            pathTicks = 0;
            return;
        }

        // ③ 中距且站在地上：高跳空袭，落点顺势贴上去。
        if (distance >= CorruptedWeaverEntity.BOMBARD_MIN_RANGE
                && distance <= CorruptedWeaverEntity.BOMBARD_MAX_RANGE
                && weaver.onGround()
                && weaver.ready(weaver.bombardReadyAt())
                && weaver.hasMana(CorruptedWeaverEntity.BOMBARD_COST)) {
            beginBomb();
            return;
        }

        // ④ 兜底：压上去。贴到 2.7 格内自动转噬咬，永远不会没招可用。
        act = Act.PRESS;
        pathTicks = 0;
    }

    // ---------------------------------------------------------------- 压上去
    private void tickPress(LivingEntity target) {
        double distance = Math.sqrt(weaver.distanceToSqr(target));
        if (distance <= BITE_REACH && weaver.hasLineOfSight(target) && weaver.ready(weaver.hitRunReadyAt())) {
            beginBite();
            return;
        }
        // 追了一阵还贴不上：先给远程 / 空袭一次机会，还不行就前扑，绝不干等。
        if (ticks >= PRESS_PATIENCE) {
            pressFailures++;
            if (pressFailures >= 2) {
                pressFailures = 0;
                weaver.leapToward(target);
            }
            act = null;
            return;
        }
        repathTo(target, CHASE_SPEED);
    }

    // ---------------------------------------------------------------- 绕圈
    private void tickOrbit(LivingEntity target) {
        if (ticks >= ORBIT_TICKS) {
            orbitSign = -orbitSign;
            act = null;
            return;
        }
        double dx = target.getX() - weaver.getX();
        double dz = target.getZ() - weaver.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01D) {
            act = null;
            return;
        }
        // 目标身边的斜侧点：始终斜着切入，既不站桩，也不会和它正撞。
        double radius = Math.min(length, 3.0D);
        double x = target.getX() - dx / length * radius - dz / length * orbitSign * 2.0D;
        double z = target.getZ() - dz / length * radius + dx / length * orbitSign * 2.0D;
        repathToPoint(x, target.getY(), z, ORBIT_SPEED);
    }

    // ---------------------------------------------------------------- 噬咬连招
    private void beginBite() {
        act = Act.BITE;
        ticks = 0;
        fired = false;
        weaver.getNavigation().stop();
        weaver.playBiteWindup();
    }

    private void tickBite(LivingEntity target) {
        if (!fired) {
            if (ticks < BITE_WINDUP) return;
            fired = true;
            if (weaver.distanceToSqr(target) <= BITE_STRIKE_REACH * BITE_STRIKE_REACH) {
                weaver.bite(target);
            } else {
                // 扑空：给一声空咬，补一个短冷却再收招，免得每 15 刻空咬一次看起来像抽风。
                weaver.playBiteWhiff();
                weaver.cooldownHitRun(8);
            }
            return;
        }
        if (ticks < BITE_WINDUP + BITE_RECOVER) return;
        // 连招没断：立刻回扑接二段；连招已经打完就绕圈拉开。
        act = weaver.chainPending() ? Act.PRESS : Act.ORBIT;
        ticks = 0;
        fired = false;
        pathTicks = 0;
    }

    // ---------------------------------------------------------------- 蛛丝球
    private void tickWeb(LivingEntity target) {
        weaver.getNavigation().stop();
        if (!fired && ticks >= WEB_WINDUP) {
            fired = true;
            if (weaver.spend(CorruptedWeaverEntity.SNIPER_COST)) {
                weaver.shootWeb(target);
                weaver.cooldownSniper();
            }
        }
        if (fired && ticks >= WEB_WINDUP + WEB_RECOVER) {
            act = Act.PRESS;
            ticks = 0;
            fired = false;
            pathTicks = 0;
        }
    }

    // ---------------------------------------------------------------- 空袭
    private void beginBomb() {
        act = Act.BOMB;
        ticks = 0;
        fired = false;
        pathTicks = 0;
        if (!weaver.spend(CorruptedWeaverEntity.BOMBARD_COST)) {
            act = Act.PRESS;
            return;
        }
        weaver.cooldownBombard();
        weaver.performBombJump();
    }

    private void tickBomb(LivingEntity target) {
        if (!fired) {
            // 到顶点（垂直速度开始变小）就喷；低矮空间跳不高时，10 刻后也照喷不误。
            boolean apex = !weaver.onGround() && weaver.getDeltaMovement().y <= 0.06D;
            if (apex || ticks > 10) {
                weaver.venomSpray(target);
                fired = true;
            }
        }
        boolean landed = fired && weaver.onGround() && ticks > 6;
        if (ticks > BOMB_MAX_TICKS || landed) {
            weaver.finishBombJump();
            // 落地不作停顿，直接压上去贴身。
            act = Act.PRESS;
            ticks = 0;
            fired = false;
            pathTicks = 0;
        }
    }

    // ---------------------------------------------------------------- 移动工具
    private void repathTo(LivingEntity target, double speed) {
        if (--pathTicks > 0) return;
        pathTicks = REPATH_MIN + weaver.getRandom().nextInt(REPATH_MAX - REPATH_MIN + 1);
        // 交给 MobPathing：失败照样拉长间隔，而且连败三次会放弃走不到的目标，
        // 不会一直贴着围墙磨（它以前是「绕不过去就少算几次」，但永远不放手）。
        if (!MobPathing.chase(weaver, target, speed)) pathTicks += 8;
    }

    private void repathToPoint(double x, double y, double z, double speed) {
        if (--pathTicks > 0) return;
        pathTicks = REPATH_MIN + weaver.getRandom().nextInt(REPATH_MAX - REPATH_MIN + 1);
        if (!MobPathing.moveToPoint(weaver, new Vec3(x, y, z), speed)) pathTicks += 8;
    }
}
