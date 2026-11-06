package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 领地意识：只有雷、火、金、毒、魔素五种元素史莱姆有强领地意识，
 * 其他史莱姆（水、冰、木、风、土、光、音）彻底取消「在旁边待久了就开打」的机制。
 *
 * <p>各变种的忍耐度（连续待在 12 格内多久才会被当成入侵者）：
 * 雷 15 秒、火 30 秒、金 45 秒、毒 35 秒、魔素 25 秒。
 * 离开范围立刻清空计时；已驯服的个体不参与领地判定。</p>
 */
public final class ElementalSlimeTerritoryGoal extends Goal {
    private static final double RANGE = 12.0;
    /**
     * 多少刻重新扫一次周围。
     *
     * <p>以前 {@link #canUse()} 每刻都对 12 格内所有活体实体做一次查询，领地范围里的史莱姆一多，
     * 光是这一项就是实打实的服务器刻开销。忍耐度最短也有 15 秒，10 刻（半秒）的采样间隔
     * 对判定结果没有任何可见影响。</p>
     */
    private static final int SCAN_INTERVAL = 10;

    private final ElementalSlime slime;
    private final Map<UUID, Long> exposureSince = new HashMap<>();
    private LivingEntity selected;
    private int scanCooldown;
    private boolean usable;

    public ElementalSlimeTerritoryGoal(ElementalSlime slime) {
        this.slime = slime;
    }

    /** 各变种的忍耐刻数；返回 0 表示这个变种没有领地意识。 */
    static int toleranceTicks(ElementalSlimeVariant variant) {
        return switch (variant) {
            case LIGHTNING -> 15 * 20;
            case FIRE -> 30 * 20;
            case METAL -> 45 * 20;
            case POISON -> 35 * 20;
            case MANA -> 25 * 20;
            default -> 0;
        };
    }

    @Override
    public boolean canUse() {
        int tolerance = toleranceTicks(slime.variant());
        // 结盟个体只跟着哥布林的目标打，不自己圈地盘。
        if (tolerance <= 0 || slime.isTamed() || slime.isGoblinAlly()) return false;
        // 没到点就沿用上次的扫描结论，不再每刻重查一遍周围实体。
        if (--scanCooldown > 0) return usable;
        scanCooldown = SCAN_INTERVAL;
        usable = scan(tolerance);
        return usable;
    }

    private boolean scan(int tolerance) {
        AABB area = slime.getBoundingBox().inflate(RANGE);
        var nearby = slime.level().getEntitiesOfClass(LivingEntity.class, area, slime::validTerritoryTarget);
        exposureSince.keySet().removeIf(id -> nearby.stream().noneMatch(entity -> entity.getUUID().equals(id)));
        long now = slime.level().getGameTime();
        for (LivingEntity entity : nearby) {
            if (entity instanceof Player && slime.level().getDifficulty() == Difficulty.PEACEFUL) continue;
            // 刚被判定「够不着」的人先放过 10 秒，不然它刚放弃又被拉回去，继续贴墙。
            if (MobPathing.isUnreachable(slime, entity)) continue;
            long since = exposureSince.computeIfAbsent(entity.getUUID(), ignored -> now);
            if (now - since >= tolerance) {
                selected = entity;
                break;
            }
        }
        return selected != null;
    }

    @Override
    public void start() {
        if (selected != null) slime.setTarget(selected);
        selected = null;
        usable = false;
    }
}
