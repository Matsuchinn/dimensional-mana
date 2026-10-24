package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * 与哥布林结盟的元素史莱姆：自己不主动挑事，只跟着营地里哥布林的目标走。
 * 哥布林开打就一起上；没仗打就朝最近的哥布林蹦过去，离得太近就自己逛。
 */
public final class ElementalSlimeGoblinAllyGoal extends Goal {
    private final ElementalSlime slime;
    private Goblin escort;
    private int scanCooldown;
    /** 跟在哥布林身边时的小幅乱蹦，免得看起来像卡住。 */
    private int idleCooldown;
    private int hopTicks;
    private float hopDirection;

    public ElementalSlimeGoblinAllyGoal(ElementalSlime slime) {
        this.slime = slime;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return slime.isGoblinAlly() && !slime.isVehicle() && !slime.isTamed();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--scanCooldown > 0) {
            steer();
            return;
        }
        scanCooldown = 10;
        List<Goblin> goblins = slime.level().getEntitiesOfClass(Goblin.class,
                slime.getBoundingBox().inflate(20.0, 8.0, 20.0), LivingEntity::isAlive);
        if (goblins.isEmpty()) {
            escort = null;
            return;
        }
        escort = goblins.stream().min(Comparator.comparingDouble(slime::distanceToSqr)).orElse(null);
        for (Goblin goblin : goblins) {
            LivingEntity threat = goblin.getTarget();
            if (threat == null) threat = goblin.getLastHurtByMob();
            if (threat != null && slime.canAttack(threat)) {
                slime.setTarget(threat);
                return;
            }
        }
        steer();
    }

    private void steer() {
        ElementalSlimeMoveControl move = slime.slimeMoveControl();
        if (move == null || escort == null) return;
        if (slime.distanceToSqr(escort) <= 36.0) {
            // 已经跟上了：隔几秒原地小跳一下，不抢闲逛 Goal 的活。
            if (--idleCooldown <= 0) {
                idleCooldown = 60 + slime.getRandom().nextInt(80);
                hopTicks = 20;
                hopDirection = slime.getRandom().nextFloat() * 360.0F;
            }
            if (hopTicks > 0) {
                hopTicks--;
                move.setHeading(hopDirection, false);
                move.setWantedMovement(0.6);
            }
            return;
        }
        Vec3 point = escort.position();
        float yaw = (float)(Mth.atan2(point.z - slime.getZ(), point.x - slime.getX()) * (180.0 / Math.PI)) - 90.0F;
        move.setHeading(yaw, false);
        move.setWantedMovement(1.0);
    }
}
