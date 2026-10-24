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
    private final ElementalSlime slime;
    private final Map<UUID, Long> exposureSince = new HashMap<>();
    private LivingEntity selected;

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
        AABB area = slime.getBoundingBox().inflate(RANGE);
        var nearby = slime.level().getEntitiesOfClass(LivingEntity.class, area, slime::validTerritoryTarget);
        exposureSince.keySet().removeIf(id -> nearby.stream().noneMatch(entity -> entity.getUUID().equals(id)));
        long now = slime.level().getGameTime();
        for (LivingEntity entity : nearby) {
            if (entity instanceof Player && slime.level().getDifficulty() == Difficulty.PEACEFUL) continue;
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
    }
}
