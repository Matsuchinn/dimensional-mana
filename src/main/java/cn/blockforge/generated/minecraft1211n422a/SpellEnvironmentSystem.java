package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 冰、火法术与原版水和可燃方块的环境互动。 */
public final class SpellEnvironmentSystem {
    private static final Map<UUID, Long> SUSTAINED_ICE = new HashMap<>();
    /** 法术造出的霜冰最长存在 8 秒（160 刻），到点由本系统换回水。 */
    private static final int FROSTED_ICE_LIFETIME = 160;
    private static final List<FrostedMelt> FROSTED_ICE = new ArrayList<>();
    private record FrostedMelt(ServerLevel level, BlockPos pos, long at, boolean source) { }
    /** 零下领域一次最多扫描这么多竖列，避免大范围施法把一帧卡死。 */
    private static final int DOMAIN_COLUMNS_PER_TICK = 128;
    private static final List<DomainFreeze> DOMAIN_FREEZES = new ArrayList<>();
    /** 领域改造地形是逐步进行的：只记录游标，跨刻继续扫描。 */
    private static final class DomainFreeze {
        final ServerLevel level; final double centerX, centerY, centerZ, radius;
        final int minX, maxX, minZ, maxZ, bottom, top;
        int x, z;
        boolean done;
        DomainFreeze(ServerLevel level, Vec3 center, double radius) {
            this.level = level;
            this.centerX = center.x; this.centerY = center.y; this.centerZ = center.z; this.radius = radius;
            int r = (int)Math.ceil(radius);
            minX = (int)Math.floor(center.x) - r; maxX = (int)Math.floor(center.x) + r;
            minZ = (int)Math.floor(center.z) - r; maxZ = (int)Math.floor(center.z) + r;
            bottom = (int)Math.floor(center.y - radius); top = (int)Math.floor(center.y + radius);
            x = minX; z = minZ;
        }
    }

    /** 冰盾、冰棺等持续冰法每秒在当前位置检查一次。 */
    public static void sustainIce(LivingEntity anchor, int durationTicks) {
        if (!anchor.level().isClientSide) {
            SUSTAINED_ICE.merge(anchor.getUUID(), anchor.level().getGameTime() + durationTicks, Math::max);
        }
    }

    public static void tickLiving(LivingEntity living) {
        if (!(living.level() instanceof ServerLevel level)) return;
        Long until = SUSTAINED_ICE.get(living.getUUID());
        if (until == null) return;
        long now = level.getGameTime();
        if (!living.isAlive() || now > until) {
            SUSTAINED_ICE.remove(living.getUUID());
            return;
        }
        if (living.tickCount % 20 == 0) freezeNear(level, living.getBoundingBox().getCenter());
    }

    /** 瞬发冰法沿实际轨迹检查；只要碰到水就一定结冰。 */
    public static void freezeAlong(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        int steps = Math.max(1, (int)Math.ceil(delta.length() * 2.0));
        for (int i = 0; i <= steps; i++) freezeWater(level, start.lerp(end, i / (double)steps));
    }

    /** 持续冰法每秒将一格范围内的水变成冰，不再有几率判定。 */
    public static void freezeNear(ServerLevel level, Vec3 center) {
        freezeWater(level, center);
    }

    private static void freezeWater(ServerLevel level, Vec3 center) {
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            BlockPos fixed = pos.immutable();
            // 只冻真正的液体方块；waterlogged 的固体方块（栅栏、台阶等）跳过，免得被整块换成冰。
            if (!(level.getBlockState(fixed).getBlock() instanceof LiquidBlock)) continue;
            FluidState fluid = level.getFluidState(fixed);
            if (!fluid.is(FluidTags.WATER)) continue;
            if (!level.setBlockAndUpdate(fixed, Blocks.FROSTED_ICE.defaultBlockState())) continue;
            // 原版霜冰只在光照足够时才计划刻融化，水下或夜晚会永久结冰，所以这里自己登记 8 秒到期。
            scheduleFrostedMelt(level, fixed, fluid.isSource());
        }
    }

    /** 同一格只登记一次：反复施法不会把霜冰的存在时间无限延长。 */
    private static void scheduleFrostedMelt(ServerLevel level, BlockPos pos, boolean source) {
        for (FrostedMelt melt : FROSTED_ICE)
            if (melt.level() == level && melt.pos().equals(pos)) return;
        FROSTED_ICE.add(new FrostedMelt(level, pos, level.getGameTime() + FROSTED_ICE_LIFETIME, source));
    }

    /** 霜冰到期：原本是水源就变回水，原本是流动水就清成空气让四周的水自然补回。 */
    public static void tickLevel(ServerLevel level, long now) {
        tickDomainFreezes(level);
        Iterator<FrostedMelt> melts = FROSTED_ICE.iterator();
        while (melts.hasNext()) {
            FrostedMelt melt = melts.next();
            if (melt.level() != level) continue;
            if (!level.hasChunkAt(melt.pos())) continue;
            if (!level.getBlockState(melt.pos()).is(Blocks.FROSTED_ICE)) { melts.remove(); continue; }
            if (now < melt.at()) continue;
            melts.remove();
            level.setBlockAndUpdate(melt.pos(), melt.source()
                    ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }

    /** 零下领域：水变蓝冰、岩浆变玄武岩，露天方块顶面覆雪；整片改造分多刻完成。 */
    public static void freezeDomain(ServerLevel level, Vec3 center, double radius) {
        if (!level.isClientSide) DOMAIN_FREEZES.add(new DomainFreeze(level, center, Math.max(1.0, radius)));
    }

    private static void tickDomainFreezes(ServerLevel level) {
        Iterator<DomainFreeze> iterator = DOMAIN_FREEZES.iterator();
        while (iterator.hasNext()) {
            DomainFreeze job = iterator.next();
            if (job.level != level) continue;
            int processed = 0;
            while (!job.done && processed < DOMAIN_COLUMNS_PER_TICK) {
                if (job.z > job.maxZ) { job.done = true; break; }
                double dx = job.x + 0.5 - job.centerX, dz = job.z + 0.5 - job.centerZ;
                if (dx * dx + dz * dz <= job.radius * job.radius) {
                    freezeColumn(job, job.x, job.z);
                    processed++;
                }
                job.x++;
                if (job.x > job.maxX) { job.x = job.minX; job.z++; }
            }
            if (job.done) iterator.remove();
        }
    }

    private static void freezeColumn(DomainFreeze job, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean surfaceFound = false;
        for (int y = job.top; y >= job.bottom; y--) {
            pos.set(x, y, z);
            // 施法者脚下留一个安全气泡，避免把自己封进蓝冰或玄武岩里。
            double dx = x + 0.5 - job.centerX, dy = y + 0.5 - job.centerY, dz = z + 0.5 - job.centerZ;
            if (dx * dx + dy * dy + dz * dz <= 3.24) continue;
            if (!job.level.hasChunkAt(pos)) continue;
            BlockState state = job.level.getBlockState(pos);
            if (state.isAir()) continue;
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                if (!(state.getBlock() instanceof LiquidBlock)) continue;
                if (fluid.is(FluidTags.WATER)) job.level.setBlockAndUpdate(pos, Blocks.BLUE_ICE.defaultBlockState());
                else if (fluid.is(FluidTags.LAVA)) job.level.setBlockAndUpdate(pos, Blocks.BASALT.defaultBlockState());
                continue;
            }
            if (surfaceFound) continue;
            surfaceFound = true;
            BlockPos above = pos.above();
            if (!job.level.isEmptyBlock(above)) continue;
            BlockState snow = Blocks.SNOW.defaultBlockState();
            if (snow.canSurvive(job.level, above)) job.level.setBlockAndUpdate(above, snow);
        }
    }


    public static void igniteAlong(LivingEntity caster, ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.01) return;
        // 跳过施法者周围 2.25 格，避免烈火/火球刚离手就在自己脚下放火。
        double safeDistance = 2.25;
        int steps = Math.max(1, (int)Math.ceil(length * 2.0));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = start.lerp(end, i / (double)steps);
            if (point.distanceToSqr(caster.position()) >= safeDistance * safeDistance) igniteAt(level, point, caster);
        }
    }

    public static void igniteAt(ServerLevel level, Vec3 point, LivingEntity caster) {
        if (caster != null && point.distanceToSqr(caster.position()) < 2.25 * 2.25) return;
        igniteAt(level, point);
    }

    public static void igniteAt(ServerLevel level, Vec3 point) {
        BlockPos contact = BlockPos.containing(point);
        int placed = 0;

        // 先点燃真正接触到的地面：轨迹在空气中时取脚下，撞进方块时取其上方。
        BlockPos groundFire = level.isEmptyBlock(contact) && !level.isEmptyBlock(contact.below())
                ? contact : !level.isEmptyBlock(contact) ? contact.above() : null;
        if (groundFire != null && placeFire(level, groundFire)) placed++;

        // 再为附近可燃面寻找一个相邻空气格，避免隔空点燃两格之外的方块。
        for (BlockPos pos : BlockPos.betweenClosed(contact.offset(-1, -1, -1), contact.offset(1, 1, 1))) {
            if (placed >= 3) break;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            for (Direction direction : Direction.values()) {
                if (!state.isFlammable(level, pos, direction)) continue;
                if (placeFire(level, pos.relative(direction))) {
                    placed++;
                    break;
                }
            }
        }
    }

    private static boolean placeFire(ServerLevel level, BlockPos pos) {
        if (!level.isEmptyBlock(pos) || !level.getFluidState(pos).isEmpty()) return false;
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (!fire.canSurvive(level, pos)) return false;
        level.setBlockAndUpdate(pos, fire);
        return true;
    }

    public static void clearAll() {
        SUSTAINED_ICE.clear();
        FROSTED_ICE.clear();
        DOMAIN_FREEZES.clear();
    }

    private SpellEnvironmentSystem() { }
}
