package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 史莱姆专用危险躲避：远离克制自己的元素生物、刚伤到自己的攻击者、爆炸物和伤害方块。
 * 不走原版寻路，而是给史莱姆 MoveControl 一个持续的逃跑朝向，保留蹦跳移动手感。
 */
public final class ElementalSlimeAvoidHazardGoal extends Goal {
    private static final int SCAN_INTERVAL = 10;
    private static final double ENTITY_RADIUS = 10.0;
    private static final int BLOCK_RADIUS = 5;

    private final ElementalSlime slime;
    private Vec3 fleeFrom;
    private Vec3 shelter;
    private int scanCooldown;
    private int fleeTicks;
    /** 水史莱姆在冰原找水坑的缓存，避免每 10 刻大范围扫方块。 */
    private Vec3 waterCache;
    private long waterScanAt;

    public ElementalSlimeAvoidHazardGoal(ElementalSlime slime) {
        this.slime = slime;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (slime.isVehicle()) return false;
        if (--scanCooldown > 0) return false;
        scanCooldown = SCAN_INTERVAL;
        scanHazards();
        return fleeFrom != null || shelter != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !slime.isVehicle() && fleeTicks > 0 && (fleeFrom != null || shelter != null);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        fleeTicks = 60;
        scanCooldown = 0;
    }

    @Override
    public void tick() {
        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            scanHazards();
        }
        fleeTicks--;
        Vec3 destination = shelter != null ? shelter : awayPoint(fleeFrom);
        if (destination == null) return;
        ElementalSlimeMoveControl move = slime.slimeMoveControl();
        if (move == null) return;
        double dx = destination.x - slime.getX();
        double dz = destination.z - slime.getZ();
        move.setHeading((float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F, true);
        move.setWantedMovement(1.35);
        // 火史莱姆若已经落水，最高优先级躲避 Goal 会压过 FloatGoal；主动上浮才能真正逃到岸边。
        if (slime.isInWaterOrBubble()) slime.getJumpControl().jump();
    }

    @Override
    public void stop() {
        fleeFrom = null;
        shelter = null;
        fleeTicks = 0;
    }

    private void scanHazards() {
        shelter = null;
        fleeFrom = null;

        // 火史莱姆淋雨时先寻找有完整顶棚的落脚点，而不是只随机背着雨跳。
        if (slime.isHurtByRain()) {
            shelter = findShelter();
            if (shelter == null) fleeFrom = slime.position().add(0.0, 0.0, 0.0);
            fleeTicks = Math.max(fleeTicks, 60);
            return;
        }

        // 水史莱姆踩在冰/雪上或正在下雪时，优先往附近的水里钻：
        // 既满足「远离冰雪」，也符合冰原上水史莱姆尽量待在水面/水下。
        if (slime.variant() == ElementalSlimeVariant.WATER && !slime.isInWaterOrBubble()
                && (slime.isSnowingHere() || slime.isTouchingSnowOrIce())) {
            Vec3 water = findWaterRefuge();
            if (water != null) {
                shelter = water;
                fleeTicks = Math.max(fleeTicks, 80);
                return;
            }
        }

        LivingEntity recent = slime.getLastHurtByMob();
        if (recent != null && recent.isAlive() && slime.tickCount - slime.getLastHurtByMobTimestamp() <= 80) {
            fleeFrom = recent.position();
            fleeTicks = Math.max(fleeTicks, 50);
            return;
        }

        Entity dangerousEntity = nearestDangerousEntity();
        if (dangerousEntity != null) {
            fleeFrom = dangerousEntity.position();
            fleeTicks = Math.max(fleeTicks, 50);
            return;
        }

        BlockPos dangerousBlock = nearestDangerousBlock();
        if (dangerousBlock != null) {
            fleeFrom = dangerousBlock.getCenter();
            fleeTicks = Math.max(fleeTicks, 50);
        }
    }

    private Entity nearestDangerousEntity() {
        AABB area = slime.getBoundingBox().inflate(ENTITY_RADIUS, 5.0, ENTITY_RADIUS);
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : slime.level().getEntities(slime, area, this::isDangerousEntity)) {
            double distance = slime.distanceToSqr(entity);
            if (distance < best) {
                best = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    private boolean isDangerousEntity(Entity entity) {
        if (entity instanceof ElementalSlime other) {
            return other.variant() != slime.variant() && !other.isTamed()
                    && slime.isVulnerableTo(other.variant().element());
        }
        if (entity instanceof PrimedTnt) return true;
        return entity instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0);
    }

    private BlockPos nearestDangerousBlock() {
        BlockPos origin = slime.blockPosition();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -BLOCK_RADIUS; x <= BLOCK_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -BLOCK_RADIUS; z <= BLOCK_RADIUS; z++) {
                    cursor.setWithOffset(origin, x, y, z);
                    if (!slime.isDangerousEnvironment(cursor)) continue;
                    double distance = cursor.distSqr(origin);
                    if (distance < best) {
                        best = distance;
                        nearest = cursor.immutable();
                    }
                }
            }
        }
        return nearest;
    }

    /** 在 24 格内找最近的水面（上方没有实心方块），找不到返回 null；结果缓存 3 秒。 */
    private Vec3 findWaterRefuge() {
        long now = slime.level().getGameTime();
        if (now < waterScanAt) return waterCache;
        waterScanAt = now + 60;
        waterCache = null;
        BlockPos origin = slime.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int x = -12; x <= 12; x += 2) {
            for (int z = -12; z <= 12; z += 2) {
                for (int y = -3; y <= 3; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!slime.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) continue;
                    BlockPos above = pos.above();
                    if (!slime.level().getBlockState(above).getCollisionShape(slime.level(), above).isEmpty()) continue;
                    double score = pos.distSqr(origin) + (y > 0 ? 4.0 : 0.0);
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos.immutable();
                    }
                }
            }
        }
        if (best != null) waterCache = best.getCenter();
        return waterCache;
    }

    private Vec3 findShelter() {
        BlockPos origin = slime.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            BlockPos candidate = origin.offset(slime.getRandom().nextInt(25) - 12,
                    slime.getRandom().nextInt(9) - 4, slime.getRandom().nextInt(25) - 12);
            if (slime.level().canSeeSky(candidate) || !slime.level().getBlockState(candidate).isAir()
                    || !slime.level().getBlockState(candidate.above()).isAir()
                    || !slime.level().getBlockState(candidate.below()).isFaceSturdy(
                    slime.level(), candidate.below(), net.minecraft.core.Direction.UP)
                    || slime.isDangerousEnvironment(candidate)
                    || slime.isDangerousEnvironment(candidate.below())) continue;
            double score = candidate.distSqr(origin);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best == null ? null : best.getCenter();
    }

    private Vec3 awayPoint(Vec3 danger) {
        if (danger == null) return null;
        double dx = slime.getX() - danger.x;
        double dz = slime.getZ() - danger.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.05) {
            double angle = slime.getRandom().nextDouble() * Math.PI * 2.0;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0;
        }
        return slime.position().add(dx / length * 9.0, 0.0, dz / length * 9.0);
    }
}
