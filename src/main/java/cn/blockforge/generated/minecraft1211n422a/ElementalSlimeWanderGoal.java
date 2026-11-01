package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Slime 专用闲逛 Goal：每 2～5 秒换个朝向，速度交给 MoveControl 的跳跃节奏。
 * 音史莱姆「循声而来」也走这里——听到乐曲就把朝向对准声源，而不是原地发呆。
 */
public final class ElementalSlimeWanderGoal extends Goal {
    private final ElementalSlime slime;
    private int nextTurn;
    private float direction;
    /** 撞墙检测：上一帧位置 + 连续几次「10 刻挪不动」。 */
    private double lastX;
    private double lastY;
    private double lastZ;
    private int stuckChecks;

    public ElementalSlimeWanderGoal(ElementalSlime slime) {
        this.slime = slime;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 目标死了 / 变成不可攻击时也要照常闲逛：只判断 getTarget() 是否为空的话，
        // 残留的死亡目标会让闲逛、施法、追击三个 Goal 同时停摆，史莱姆就地发呆。
        return !slime.hasAttackableTarget() && !slime.isVehicle();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        lastX = slime.getX();
        lastY = slime.getY();
        lastZ = slime.getZ();
        stuckChecks = 0;
    }

    @Override
    public void tick() {
        ElementalSlimeMoveControl move = slime.slimeMoveControl();
        if (move == null) return;
        Vec3 sound = slime.approachTarget();
        if (sound != null) {
            direction = (float)(Mth.atan2(sound.z - slime.getZ(), sound.x - slime.getX()) * (180.0 / Math.PI)) - 90.0F;
            move.setHeading(direction, false);
            move.setWantedMovement(1.15);
            return;
        }
        // 金/土/风史莱姆会被玩家手里的特定物品吸引，优先朝玩家蹦过去。
        Vec3 lure = slime.lureTarget();
        if (lure != null) {
            direction = (float)(Mth.atan2(lure.z - slime.getZ(), lure.x - slime.getX()) * (180.0 / Math.PI)) - 90.0F;
            move.setHeading(direction, false);
            move.setWantedMovement(1.10);
            return;
        }
        if (--nextTurn <= 0) {
            nextTurn = 40 + slime.getRandom().nextInt(60);
            direction = slime.getRandom().nextFloat() * 360.0F;
        }
        // r264：撞墙检测。史莱姆不会寻路，只会朝一个方向一直往前蹦 ——
        // 被围墙围住时它会整段时间贴着同一面墙蹭。连续 30 刻挪不动就立刻换个方向。
        if (slime.tickCount % 10 == 0) {
            double moved = slime.distanceToSqr(lastX, lastY, lastZ);
            lastX = slime.getX();
            lastY = slime.getY();
            lastZ = slime.getZ();
            stuckChecks = moved < 0.04D ? stuckChecks + 1 : 0;
            if (stuckChecks >= 3) {
                stuckChecks = 0;
                // 掉头再偏 90°~180°：比「继续往同一个方向蹦」更容易找到空档。
                direction += 90.0F + slime.getRandom().nextFloat() * 90.0F;
                nextTurn = 30 + slime.getRandom().nextInt(30);
            }
        }
        move.setHeading(direction, false);
        move.setWantedMovement(0.85);
    }
}
