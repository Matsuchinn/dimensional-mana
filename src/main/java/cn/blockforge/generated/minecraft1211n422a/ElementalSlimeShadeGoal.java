package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** 毒史莱姆白天寻找附近最暗且能站立的位置。 */
public final class ElementalSlimeShadeGoal extends Goal {
    private final ElementalSlime slime;
    private BlockPos shade;

    public ElementalSlimeShadeGoal(ElementalSlime slime) {
        this.slime = slime;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!slime.level().isDay() || !slime.level().canSeeSky(slime.blockPosition())) return false;
        int best = 16;
        BlockPos origin = slime.blockPosition();
        for (int i = 0; i < 24; i++) {
            BlockPos candidate = origin.offset(slime.getRandom().nextInt(17) - 8,
                    slime.getRandom().nextInt(7) - 3, slime.getRandom().nextInt(17) - 8);
            int light = slime.level().getRawBrightness(candidate, slime.level().getSkyDarken());
            if (light < best && slime.level().getBlockState(candidate).isAir()
                    && slime.level().getBlockState(candidate.below()).isSolid()) {
                best = light;
                shade = candidate;
            }
        }
        return shade != null;
    }

    @Override
    public boolean canContinueToUse() { return shade != null && slime.distanceToSqr(shade.getCenter()) > 1.5; }

    @Override
    public void start() { move(); }

    @Override
    public void tick() { move(); }

    @Override
    public void stop() { shade = null; }

    private void move() {
        if (shade == null) return;
        ElementalSlimeMoveControl move = slime.slimeMoveControl();
        if (move == null) return;
        double dx = shade.getX() + 0.5 - slime.getX();
        double dz = shade.getZ() + 0.5 - slime.getZ();
        move.setHeading((float)(net.minecraft.util.Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F, false);
        move.setWantedMovement(1.25);
    }
}
