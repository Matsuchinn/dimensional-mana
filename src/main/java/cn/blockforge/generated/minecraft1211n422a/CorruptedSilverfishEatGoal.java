package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * 秽蚀蠹在蚀魔荒原上的日常：找一株<b>比一格还高</b>的卢梅莉亚，
 * <b>爬到顶端、从最上面一节开始啃，啃掉一节顺着重力慢慢下滑到下一节</b>，
 * 一路吃到根部（r91 重写，之前是趴在地面往顶端跳着啃，画面很怪）。
 *
 * <p>三段式行为：</p>
 * <ol>
 *   <li><b>走位</b>：寻路走到植株脚下（水平距离 &lt;1.5 格）就关掉寻路；</li>
 *   <li><b>上树</b>：这段时间开 noGravity，竖直速度完全由本目标按「离顶端还差几格」
 *       线性给——爬升快（≤0.16/刻）、下落慢（≤0.10/刻），横向始终朝茎柱中心收拢，
 *       看起来就是贴着茎往上爬；</li>
 *   <li><b>啃食</b>：悬停在顶端格上沿 0.3 格处，每 12 刻啃掉当前最高一节；
 *       植株矮一格，它的停靠高度就跟着降一格——「边吃边下滑」。啃到根部整株清零，
 *       进 30 秒冷却（{@link CorruptedSilverfishEntity#EAT_COOLDOWN_TICKS}）。</li>
 * </ol>
 *
 * <p><b>r92 修「看起来不吃」</b>：</p>
 * <ol>
 *   <li><b>刚生成就带着 30 秒冷却</b>：很多生成路径（刷怪笼替换、虫群召唤、补刷）会先给
 *       {@code eatReadyAt} 写一个未来时刻，落地的虫于是先站三十秒。现在
 *       {@link CorruptedSilverfishEntity#finalizeSpawn} 在生成流程最后把冷却显式清零，
 *       刚刷出来的一只立刻就能啃。</li>
 *   <li><b>必须寻路走到脚下才肯上树</b>：寻路一旦因为地形（贴着崖壁、站在水里、被别的虫挤住）
 *       到不了位，它就永远站在 1 格外干等。现在<b>离茎柱 1.5 格内就交给悬停控制</b>，
 *       横向收拢由悬停自己完成，不再依赖寻路成功。</li>
 *   <li><b>必须精确停稳才下嘴</b>：悬停的纵向容差只有 0.12 格，被挤一下、卡在茎上就永远差一点。
 *       现在悬停 3 秒还没停稳，只要顶端那一节<b>够得着</b>（2 格内）就照啃；12 秒还够不着才放弃。</li>
 * </ol>
 *
 * <p><b>r92 打架不再抢走一切</b>：以前只要「有活人目标」就不吃草，而目标选择半径是 40 格——
 * 玩家站在荒原上看着它，它永远在追人，看起来就像「不吃草了」。现在只有<b>8 格内</b>的活人
 * 才优先处理；玩家隔远观察时它照常吃草。挨了一下（{@code hurtTime > 0}）立刻中断进食去打架。
 * 因此本目标的优先级放在近战（3）之前：远处的目标拦不住它吃草，近处的目标照常由近战接手。</p>
 *
 * <p>停栖期间实体自身的 {@code climbPlants} 升力让位（{@code perching} 标记），
 * 两套竖直控制不打架，也就不会在草尖上一抽一抽。被打、被虫群征召、植株被拆都会
 * 立刻收尾并恢复重力。</p>
 *
 * <p>性能：真正扫方块的只有每 2 秒一次、半径 12 格、隔格采样的那一轮；
 * 悬停每刻只写一次速度向量，啃食只是按刻 {@code destroyBlock}。</p>
 */
public final class CorruptedSilverfishEatGoal extends Goal {
    /** 找植株的间隔（刻）：2 秒，别每刻都扫世界。 */
    private static final int SCAN_INTERVAL = 40;
    /** 找植株的水平半径（格）。 */
    private static final int SEARCH_RADIUS = 12;
    /** 竖直方向的搜索范围（格）。 */
    private static final int SEARCH_UP = 4;
    private static final int SEARCH_DOWN = 3;
    /** 走到离茎柱中心几格以内就交给悬停（1.5 格，不再死等寻路到位）。 */
    private static final double PARK_DIST_SQR = 2.25D;
    /** 悬停时被挤到离茎柱这么远（5 格）就收工，2 秒后重新找一株。 */
    private static final double GIVE_UP_DIST_SQR = 25.0D;
    /** 每啃一节之间的间隔（刻）：和下滑速度匹配，一节刚好一口。 */
    private static final int CHEW_INTERVAL = 12;
    /** 一株最多数到几格（防止奇怪的方块链把循环拖死）。 */
    private static final int MAX_HEIGHT = 8;
    /** 停靠高度：脚底离顶端格底面的偏移（格）。 */
    private static final double HOVER_OFFSET = 0.30D;
    /** 爬升 / 下滑的速度上限。 */
    private static final double RISE_SPEED = 0.16D;
    private static final double SINK_SPEED = 0.10D;
    /** 离停靠点多近算「到位可以下嘴」。 */
    private static final double SETTLED_TOLERANCE = 0.12D;
    /** 悬停这么多刻（3 秒）还没停稳就不再苛求，够得着就啃。 */
    private static final int PATIENCE_TICKS = 60;
    /** 兜底啃食的「够得着」距离（格，到方块中心的平方）。 */
    private static final double CHEW_REACH_SQR = 4.0D;
    /** 悬停这么多刻（12 秒）还够不着，就放弃这一株。 */
    private static final int GIVE_UP_TICKS = 240;
    /** 这么近（8 格）的活人才优先打；再远就先吃草，玩家能隔着看它啃。 */
    private static final double COMBAT_RANGE_SQR = 64.0D;

    private final CorruptedSilverfishEntity bug;
    /** 目标这一株最底下那一格；null = 现在没有目标。 */
    private BlockPos base;
    /** 下一次可以扫世界的时间（刻）。 */
    private int scanCooldown;
    /** 离下一次「啃一口」还有几刻。 */
    private int chewCooldown;
    /** true = 已进入爬藤 / 停栖阶段（地面寻路结束）。 */
    private boolean perched;
    /** 悬停开始之后过了几刻（用来判断「等太久了」）。 */
    private int perchTicks;

    public CorruptedSilverfishEatGoal(CorruptedSilverfishEntity bug) {
        this.bug = bug;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (bug.level().isClientSide || !bug.hasBurrowedOut()) return false;
        if (bug.isDigRushing()) return false;
        // 只有 8 格内的活人才优先处理，远处的目标拦不住它吃草（见类注释）。
        if (mustFight()) return false;
        if (!bug.canEat()) return false;
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_INTERVAL;
        return locateStalk();
    }

    @Override
    public boolean canContinueToUse() {
        if (!bug.canEat() || bug.isDigRushing()) return false;
        if (mustFight()) return false;
        if (base == null) return false;
        // 植株被玩家提前拆了就不再惦记。
        return topOf(base) != null;
    }

    /** 现在该不该放下嘴边的草去打架：刚挨打，或者 8 格内有个活人目标。 */
    private boolean mustFight() {
        if (bug.hurtTime > 0) return true;
        LivingEntity target = bug.getTarget();
        return target != null && target.isAlive() && bug.distanceToSqr(target) <= COMBAT_RANGE_SQR;
    }

    @Override
    public void start() {
        chewCooldown = CHEW_INTERVAL;
        perched = false;
        perchTicks = 0;
        if (base != null) {
            bug.getNavigation().moveTo(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D, 1.1D);
        }
    }

    @Override
    public void stop() {
        leaveStalk();
    }

    @Override
    public void tick() {
        if (base == null) return;
        BlockPos top = topOf(base);
        if (top == null) {
            leaveStalk();
            return;
        }
        Level level = bug.level();
        double centerX = base.getX() + 0.5D;
        double centerZ = base.getZ() + 0.5D;
        double dx = centerX - bug.getX();
        double dz = centerZ - bug.getZ();
        double horizontalSqr = dx * dx + dz * dz;
        if (!perched) {
            if (horizontalSqr > PARK_DIST_SQR) {
                if (bug.getNavigation().isDone()) {
                    bug.getNavigation().moveTo(centerX, base.getY(), centerZ, 1.1D);
                }
                return;
            }
            // 到位：停寻路、关重力，交给下面的悬停控制。
            perch();
        } else if (horizontalSqr > GIVE_UP_DIST_SQR) {
            // 被别的虫挤走 / 被水冲走：别再半空里追，收工，2 秒后重新找一株。
            leaveStalk();
            return;
        }
        perchTicks++;
        // 停栖期间把移动控制按住，别让它在半空里还往前推。
        bug.haltMoveControl();
        // ---- 悬停控制：横向收拢到茎柱中心，纵向朝“顶端停靠高度”走 ----
        double targetY = top.getY() + HOVER_OFFSET;
        double dy = targetY - bug.getY();
        double vx = Mth.clamp(dx * 0.2D, -0.08D, 0.08D);
        double vz = Mth.clamp(dz * 0.2D, -0.08D, 0.08D);
        double vy = 0.0D;
        if (dy > SETTLED_TOLERANCE) {
            vy = Math.min(dy * 0.2D, RISE_SPEED);      // 往上爬：快
        } else if (dy < -SETTLED_TOLERANCE) {
            vy = Math.max(dy * 0.2D, -SINK_SPEED);     // 往下滑：慢，这就是「慢慢下滑」
        }
        bug.setDeltaMovement(vx, vy, vz);
        // 头和身体都朝着茎，啃的时候看得见它对着哪一口。
        float wantedRot = (float) (Mth.atan2(centerZ - bug.getZ(), centerX - bug.getX())
                * 180.0D / Math.PI) - 90.0F;
        bug.setYRot(Mth.rotateIfNecessary(bug.getYRot(), wantedRot, 30.0F));
        bug.getLookControl().setLookAt(centerX, top.getY() + 0.5D, centerZ);

        if (Math.abs(dy) > SETTLED_TOLERANCE) {
            // 没停稳：先老实爬。爬了三秒还差一点、但顶端那一节已经够得着，就照啃；
            // 十二秒连够都够不着（被卡住 / 被挤开），放弃这一株。
            if (perchTicks < PATIENCE_TICKS) return;
            if (bug.distanceToSqr(top.getX() + 0.5D, top.getY() + 0.5D, top.getZ() + 0.5D)
                    > CHEW_REACH_SQR) {
                if (perchTicks > GIVE_UP_TICKS) leaveStalk();
                return;
            }
        }
        if (chewCooldown-- > 0) return;
        chewCooldown = CHEW_INTERVAL;
        chew(level, top);
    }

    /** 开始停栖：关重力、停寻路、把移动控制按住。 */
    private void perch() {
        perched = true;
        perchTicks = 0;
        chewCooldown = CHEW_INTERVAL;
        bug.setPerching(true);
        bug.setNoGravity(true);
        bug.getNavigation().stop();
        bug.haltMoveControl();
    }

    /** 啃掉当前最高的那一节；整株吃完就进 30 秒冷却、离开。 */
    private void chew(Level level, BlockPos top) {
        if (!(level.getBlockState(top).getBlock() instanceof LumeliaPlantBlock)) return;
        BlockState eaten = level.getBlockState(top);
        level.destroyBlock(top, false);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, eaten),
                    top.getX() + 0.5D, top.getY() + 0.5D, top.getZ() + 0.5D,
                    10, 0.25D, 0.25D, 0.25D, 0.03D);
            server.sendParticles(ParticleTypes.COMPOSTER,
                    top.getX() + 0.5D, top.getY() + 0.5D, top.getZ() + 0.5D,
                    4, 0.2D, 0.2D, 0.2D, 0.02D);
            server.playSound(null, top, GeneratedMod.CORRUPTED_SILVERFISH_BITE.get(),
                    SoundSource.HOSTILE, 0.45F, 1.55F);
        }
        // 剩下根部最后一格也照啃：这一株就算被它吃干净了。
        if (top.equals(base) || topOf(base) == null) {
            // 进冷却；蜕过皮的还会把身体补回 24 血满状态（见 CorruptedSilverfishEntity#eatLumelia）。
            bug.eatLumelia();
            leaveStalk();
        }
    }

    /** 收尾：恢复重力、清目标、停寻路（从打架 / 钻地切走也走这里）。 */
    private void leaveStalk() {
        if (perched) {
            perched = false;
            bug.setPerching(false);
            bug.setNoGravity(false);
        }
        perchTicks = 0;
        base = null;
        bug.getNavigation().stop();
    }

    /** 以蠹虫为中心扫一圈，挑最近的一株「≥2 格高」的卢梅莉亚。 */
    private boolean locateStalk() {
        BlockPos center = bug.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx += 2) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz += 2) {
                for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
                    cursor.setWithOffset(center, dx, dy, dz);
                    if (!bug.level().isLoaded(cursor)) continue;
                    if (!(bug.level().getBlockState(cursor).getBlock() instanceof LumeliaPlantBlock)) continue;
                    // 只从最底下一格开始算，免得把同一株的上段当成好几株。
                    if (bug.level().getBlockState(cursor.below()).getBlock() instanceof LumeliaPlantBlock) continue;
                    if (height(cursor) < 2) continue;   // 「超过 1 格高」
                    double distance = bug.distanceToSqr(cursor.getX() + 0.5D, cursor.getY() + 0.5D, cursor.getZ() + 0.5D);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        base = best;
        return best != null;
    }

    /** 这一株有几格。 */
    private int height(BlockPos basePos) {
        int height = 1;
        while (height < MAX_HEIGHT
                && bug.level().getBlockState(basePos.above(height)).getBlock() instanceof LumeliaPlantBlock) {
            height++;
        }
        return height;
    }

    /** 这一株现在最上面那一格；被拆光了返回 null。 */
    private BlockPos topOf(BlockPos basePos) {
        if (basePos == null) return null;
        if (!(bug.level().getBlockState(basePos).getBlock() instanceof LumeliaPlantBlock)) return null;
        BlockPos top = basePos;
        for (int i = 1; i < MAX_HEIGHT; i++) {
            BlockState above = bug.level().getBlockState(top.above());
            if (!(above.getBlock() instanceof LumeliaPlantBlock)) break;
            top = top.above();
        }
        return top;
    }

    /** 现在是不是正盯着一株草（给实体做动画 / 调试用）。 */
    public boolean eating() {
        return base != null;
    }
}
