package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

/**
 * 秽蚀蜂的「找新家」兜底目标（r91）。
 *
 * <p>原版蜜蜂找巢走的是 POI 系统（{@code Bee.BeeLocateHiveGoal} 去 POI 表里查
 * {@code #minecraft:bee_home}），而 POI 记录只在「方块被正常放进已加载的服务端世界」
 * 那一刻写入——老存档里已存在的蜂巢没有记录、结构摆放等旁路也可能漏，
 * 结果就是玩家放好的秽蚀蜂巢 / 蜂箱，蜂看得到进不去。这个目标绕开 POI，
 * 直接按方块标签扫周围一圈：找到没住满的巢就写进 {@code hivePos}，
 * 后面的「飞回去 / 钻门 / 记黑名单」全部交给原版那套 Goal 收尾。</p>
 *
 * <p>触发时机和原版一致：带蜜、下雨、天黑才想回家；没找到就把下次扫描
 * 推迟 200 刻，找到就交给原版，不再扫。</p>
 */
public final class CorruptedBeeLocateHiveGoal extends Goal {
    /** 扫描半径（格）：原版 POI 查询是 20，这里 14 够用还省（约 3 万格里查标签）。 */
    private static final int RADIUS = 14;
    /** 扫描没结果时的再试间隔（刻）。 */
    private static final int EMPTY_RETRY = 200;
    /** 常规扫描节流（刻）：平时 5 秒一轮。 */
    private static final int SCAN_COOLDOWN = 100;

    private final Bee bee;
    private int cooldown;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public CorruptedBeeLocateHiveGoal(Bee bee) {
        this.bee = bee;
    }

    @Override
    public boolean canUse() {
        if (bee.level().isClientSide) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (bee.hasHive()) return false;
        Level level = bee.level();
        // 原版语义：带蜜要回家酿、下雨要进屋、天黑要睡觉；白天闲逛的蜂不许钻巢。
        if (!bee.hasNectar() && !level.isRaining() && !level.isNight()) return false;
        return locate();
    }

    @Override
    public boolean canContinueToUse() {
        return false;   // 一次性：写进 hivePos 就交棒给原版回巢 / 入巢目标
    }

    @Override
    public void tick() {
        if (this.cooldown > 0) this.cooldown--;
    }

    /** 扫一圈，找离蜂最近、还住得下、且没被拉黑的巢。 */
    private boolean locate() {
        Level level = bee.level();
        BlockPos center = bee.blockPosition();
        BlockPos best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    cursor.setWithOffset(center, dx, dy, dz);
                    if (!level.isLoaded(cursor)) continue;
                    if (!level.getBlockState(cursor).is(BlockTags.BEEHIVES)) continue;
                    double distanceSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distanceSq >= bestDistanceSq) continue;
                    if (bee.getBlacklistedHives().contains(cursor)) continue;
                    if (!(level.getBlockEntity(cursor) instanceof BeehiveBlockEntity hive)) continue;
                    if (hive.isFull()) continue;
                    best = cursor.immutable();
                    bestDistanceSq = distanceSq;
                }
            }
        }
        if (best == null) {
            this.cooldown = EMPTY_RETRY;
            return false;
        }
        this.cooldown = SCAN_COOLDOWN;
        bee.setHivePos(best);
        return true;
    }
}
