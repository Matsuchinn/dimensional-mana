package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 炽阳之怒：前摇结束后先悬停在身前充能，再次施法时沿视线发射。
 * 火球按层数改变半径、伤害、爆炸范围、射程与速度，并负责破坏地形和携带生物。
 */
public final class BlazingSun extends Entity {
    /** 充能层数上限：18 层，其余每层增益不变。 */
    public static final int MAX_CHARGE = 18;
    public static final int AUTO_RELEASE_TICKS = 20 * 30;
    public static final double BASE_RADIUS = 1.0;
    public static final double BASE_DAMAGE = 55.0;
    public static final double BASE_EXPLOSION = 5.0;
    public static final double BASE_RANGE = 30.0;
    public static final double BASE_SPEED = 24.0;
    private static final float OBSIDIAN_HARDNESS = 50.0F;
    /**
     * 爆炸破坏方块的最大半径。18 层时爆炸半径会到 36 格，整球炸掉要处理二十多万个方块，
     * 服务器会明显卡顿；这里把真正炸掉的体积限制在 12 格内，超出的部分只结算伤害。
     */
    private static final double MAX_DESTRUCTION_RADIUS = 12.0;
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(BlazingSun.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RELEASED = SynchedEntityData.defineId(BlazingSun.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(BlazingSun.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(BlazingSun.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(BlazingSun.class, EntityDataSerializers.FLOAT);
    private static final String OWNER_TAG = "Owner";
    private UUID ownerId;
    private final Set<UUID> carried = new HashSet<>();
    private int age;
    private int chargingTicks;
    private double traveled;
    private BlockPos lightPos;
    private boolean exploded;

    public BlazingSun(EntityType<? extends BlazingSun> type, Level level) { super(type, level); noPhysics = true; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE, 0);
        builder.define(RELEASED, false);
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, 0.0F);
        builder.define(DIR_Z, 1.0F);
    }

    public void configure(ServerPlayer owner, Vec3 at, Vec3 direction) {
        ownerId = owner.getUUID();
        setPos(at);
        setDirection(direction);
        entityData.set(CHARGE, 0);
        entityData.set(RELEASED, false);
    }

    public int charge() { return entityData.get(CHARGE); }
    public boolean isReleased() { return entityData.get(RELEASED); }
    public double radius() { return BASE_RADIUS + 0.20 * charge(); }
    public double criticalDamage() { return BASE_DAMAGE * (1.0 + 0.25 * charge()); }
    public double explosionRadius() { return BASE_EXPLOSION * (1.0 + 0.35 * charge()); }
    public double range() { return BASE_RANGE + charge(); }
    public double speed() { return BASE_SPEED * Math.pow(0.88, charge()); }
    public int remainingChargeTicks() { return Math.max(0, AUTO_RELEASE_TICKS - chargingTicks); }
    public Vec3 direction() { return new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z)); }

    private void setDirection(Vec3 direction) {
        Vec3 value = direction.lengthSqr() < 0.0001 ? new Vec3(0, 0, 1) : direction.normalize();
        entityData.set(DIR_X, (float)value.x);
        entityData.set(DIR_Y, (float)value.y);
        entityData.set(DIR_Z, (float)value.z);
    }

    /** 每次充能提高一层；返回是否成功增加。 */
    public boolean addCharge() {
        if (isReleased() || charge() >= MAX_CHARGE) return false;
        entityData.set(CHARGE, charge() + 1);
        if (level() instanceof ServerLevel serverLevel) {
            double r = radius();
            serverLevel.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 18,
                    r * 0.55, r * 0.55, r * 0.55, 0.04);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_BURN,
                    SoundSource.PLAYERS, 0.9F, 1.05F + charge() * 0.025F);
        }
        return true;
    }

    /** 再次按下施法键或充能超时后，沿当前视线方向发射。 */
    public void release() {
        if (isReleased()) return;
        entityData.set(RELEASED, true);
        NewSpellSystem.forgetSun(ownerId, this);
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 dir = direction();
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.PLAYERS, 1.2F, 0.82F);
            serverLevel.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 32,
                    radius() * 0.7, radius() * 0.7, radius() * 0.7, 0.12);
            for (int i = 0; i < 12; i++) {
                double f = i / 12.0;
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        getX() + dir.x * f * 4.0, getY() + dir.y * f * 4.0, getZ() + dir.z * f * 4.0,
                        2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel serverLevel = (ServerLevel)level();
        age++;
        if (age > 20 * 60 * 3) { discard(); return; }
        LivingEntity owner = owner();
        if (owner == null || !owner.isAlive() || owner.level() != serverLevel) { discard(); return; }
        if (!isReleased()) {
            if (++chargingTicks > AUTO_RELEASE_TICKS) {
                release();
            } else {
                Vec3 dir = owner.getViewVector(1.0F);
                setDirection(dir);
                Vec3 eye = owner.getEyePosition();
                Vec3 at = eye.add(dir.scale(4.0));
                Vec3 hit = NewSpellSystem.blockEnd(owner, eye, at);
                double distance = eye.distanceTo(hit);
                if (distance < 3.9) at = eye.add(dir.scale(Math.max(0.6, distance - 0.35)));
                setPos(at);
                if (age % 5 == 0) refreshLight(serverLevel);
                if (age % 10 == 0 && owner instanceof ServerPlayer player) SpellSyncPayload.send(player);
                return;
            }
        }
        // 发射后逐小步推进，避免高速穿墙或漏掉方块与生物。
        Vec3 dir = direction();
        double step = Math.max(0.02, speed() / 20.0);
        double remaining = step;
        while (remaining > 0.0001 && !exploded) {
            double move = Math.min(0.5, remaining);
            remaining -= move;
            Vec3 from = position();
            Vec3 next = from.add(dir.scale(move));
            if (!serverLevel.hasChunkAt(BlockPos.containing(next))) { explode(serverLevel, from); return; }
            BlockPos hard = hardBlock(serverLevel, from, next);
            if (hard != null) {
                setPos(hard.getX() + 0.5, hard.getY() + 0.5, hard.getZ() + 0.5);
                explode(serverLevel, position());
                return;
            }
            destroyPath(serverLevel, next);
            pushCreatures(serverLevel, next, dir, owner);
            setPos(next);
            traveled += move;
            if (traveled >= range()) { explode(serverLevel, next); return; }
        }
        if (age % 5 == 0) refreshLight(serverLevel);
        if (age % 10 == 0 && owner instanceof ServerPlayer player) SpellSyncPayload.send(player);
    }

    private LivingEntity owner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(ownerId);
        return entity instanceof LivingEntity living ? living : null;
    }

    private void refreshLight(ServerLevel serverLevel) {
        BlockPos now = blockPosition();
        if (lightPos != null && !lightPos.equals(now)) {
            if (serverLevel.getBlockState(lightPos).is(NewSpellContent.BLAZING_LIGHT.get()))
                serverLevel.setBlock(lightPos, Blocks.AIR.defaultBlockState(), 3);
            lightPos = null;
        }
        if (serverLevel.hasChunkAt(now) && serverLevel.isEmptyBlock(now)) {
            serverLevel.setBlock(now, NewSpellContent.BLAZING_LIGHT.get().defaultBlockState(), 3);
            lightPos = now;
        }
    }

    private void clearLight(ServerLevel serverLevel) {
        if (lightPos != null && serverLevel.getBlockState(lightPos).is(NewSpellContent.BLAZING_LIGHT.get()))
            serverLevel.setBlock(lightPos, Blocks.AIR.defaultBlockState(), 3);
        lightPos = null;
    }

    private BlockPos hardBlock(ServerLevel serverLevel, Vec3 from, Vec3 to) {
        double radius = radius();
        int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 0.5));
        for (int sample = 1; sample <= samples; sample++) {
            Vec3 center = from.lerp(to, sample / (double)samples);
            BlockPos found = hardBlockNear(serverLevel, center, radius);
            if (found != null) return found;
        }
        return null;
    }

    private BlockPos hardBlockNear(ServerLevel serverLevel, Vec3 center, double radius) {
        int r = (int)Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (x * x + y * y + z * z > radius * radius) continue;
            BlockPos pos = origin.offset(x, y, z);
            if (!serverLevel.hasChunkAt(pos)) continue;
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            float hardness = state.getDestroySpeed(serverLevel, pos);
            if (hardness < 0.0F || hardness >= OBSIDIAN_HARDNESS) return pos;
        }
        return null;
    }

    /** 破坏火球球体内的方块，把外圈石头烧成岩浆，并点燃半径 3 格内的可燃表面。 */
    private void destroyPath(ServerLevel serverLevel, Vec3 center) {
        double radius = radius();
        int r = (int)Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (x * x + y * y + z * z > radius * radius) continue;
            BlockPos pos = origin.offset(x, y, z);
            if (!serverLevel.hasChunkAt(pos)) continue;
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir()) continue;
            // 水、岩浆这类液体没有硬度可比，火球本体扫过时直接蒸干，不留水塘或岩浆池。
            if (isLiquidBlock(state)) { vaporize(serverLevel, pos); continue; }
            float hardness = state.getDestroySpeed(serverLevel, pos);
            if (hardness < 0.0F || hardness >= OBSIDIAN_HARDNESS) continue;
            serverLevel.removeBlock(pos, false);
        }
        int shell = (int)Math.ceil(radius + 1.2);
        double shellRadius = radius + 1.2;
        List<BlockPos> scorched = new ArrayList<>();
        for (int x = -shell; x <= shell; x++) for (int y = -shell; y <= shell; y++) for (int z = -shell; z <= shell; z++) {
            if (x * x + y * y + z * z > shellRadius * shellRadius) continue;
            BlockPos pos = origin.offset(x, y, z);
            if (!serverLevel.hasChunkAt(pos)) continue;
            if (!stoneLike(serverLevel.getBlockState(pos))) continue;
            serverLevel.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
            scorched.add(pos);
        }
        // 记下这批岩浆，10 秒后由法术系统统一冷却成黑石。
        NewSpellSystem.coolLavaLater(serverLevel, scorched);
        // 点燃工作每两刻执行一次，避免大半径时每刻重复扫描。
        if (age % 2 == 0) igniteAround(serverLevel, center, radius + 3.0);
    }

    private static boolean stoneLike(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }

    private void igniteAround(ServerLevel serverLevel, Vec3 center, double radius) {
        int r = (int)Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (x * x + y * y + z * z > radius * radius) continue;
            BlockPos pos = origin.offset(x, y, z);
            if (!serverLevel.hasChunkAt(pos) || !serverLevel.isEmptyBlock(pos)) continue;
            BlockPos below = pos.below();
            if (!serverLevel.getBlockState(below).isSolidRender(serverLevel, below)) continue;
            serverLevel.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
        }
    }

    /** 撞到的敌人会被火球带着前进，并在此过程中每秒受到 16 点灼烧伤害。 */
    private void pushCreatures(ServerLevel serverLevel, Vec3 center, Vec3 dir, LivingEntity owner) {
        double reach = radius() + 0.8;
        boolean burn = age % 20 == 0;
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(reach),
                entity -> entity != owner && entity.isAlive() && NewSpellSystem.enemy(owner, entity))) {
            carried.add(victim.getUUID());
            Vec3 push = center.add(dir.scale(radius() * 0.55));
            moveCarried(victim, push);
        }
        Iterator<UUID> iterator = carried.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity entity = serverLevel.getEntity(id);
            if (!(entity instanceof LivingEntity victim) || !victim.isAlive()
                    || victim.distanceToSqr(center) > 64.0) {
                iterator.remove();
                continue;
            }
            if (victim.distanceToSqr(center) > reach * reach) {
                moveCarried(victim, center.add(dir.scale(radius() * 0.55)));
            }
            if (burn && NewSpellSystem.enemy(owner, victim)) NewSpellSystem.damage(owner, victim, 16.0F, ElementType.FIRE);
        }
    }

    private static void moveCarried(LivingEntity victim, Vec3 at) {
        if (victim instanceof ServerPlayer player) {
            player.connection.teleport(at.x, at.y - 0.6, at.z, player.getYRot(), player.getXRot());
        } else {
            victim.teleportTo(at.x, at.y - 0.6, at.z);
        }
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;
        victim.resetFallDistance();
    }

    private void explode(ServerLevel serverLevel, Vec3 at) {
        if (exploded) return;
        exploded = true;
        LivingEntity owner = owner();
        double radius = explosionRadius();
        double damage = criticalDamage();
        if (owner != null) {
            // 爆炸按范围结算，不要求隔着地形也能看见目标。
            for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class,
                    new AABB(at, at).inflate(radius),
                    entity -> entity.isAlive() && NewSpellSystem.enemy(owner, entity)
                            && entity.getBoundingBox().distanceToSqr(at) <= radius * radius))
                NewSpellSystem.damage(owner, victim, (float)damage, ElementType.FIRE);
            SpellEnvironmentSystem.igniteAt(serverLevel, at, owner);
        } else {
            SpellEnvironmentSystem.igniteAt(serverLevel, at);
        }
        // 最终爆炸同样炸掉地形，黑曜石级及以上硬度的方块会留下来。
        destroyExplosion(serverLevel, at);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z,
                Math.max(2, (int)Math.ceil(radius * 0.35)), radius * 0.22, radius * 0.22, radius * 0.22, 0.0);
        serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.5F, 0.72F + (float)Math.min(0.35, radius * 0.01));
        SpellVisualPayload.send(this, SpellDefinition.BLAZING_SUN, SpellVisualPayload.RELEASE, -1, at, at, 32);
        clearLight(serverLevel);
        NewSpellSystem.forgetSun(ownerId, this);
        discard();
    }

    /** 炸掉爆炸半径内的方块：球形范围、不掉落物品，硬度和基岩一样挡住破坏的会保留。 */
    private void destroyExplosion(ServerLevel serverLevel, Vec3 at) {
        double radius = explosionRadius();
        // 液体不受硬度限制，按完整爆炸半径蒸干（扫描比破坏方块便宜，不需要 12 格上限）。
        evaporateLiquids(serverLevel, at, radius);
        double limit = Math.min(radius, MAX_DESTRUCTION_RADIUS);
        double limitSqr = limit * limit;
        int r = (int)Math.ceil(limit);
        BlockPos origin = BlockPos.containing(at);
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (x * x + y * y + z * z > limitSqr) continue;
            BlockPos pos = origin.offset(x, y, z);
            if (!serverLevel.hasChunkAt(pos)) continue;
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir()) continue;
            float hardness = state.getDestroySpeed(serverLevel, pos);
            if (hardness < 0.0F || hardness >= OBSIDIAN_HARDNESS) continue;
            serverLevel.removeBlock(pos, false);
        }
    }

    /** 整数格是否是液体本身（水、流动水、岩浆、流动岩浆），水logged 的固体方块不算。 */
    private static boolean isLiquidBlock(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && !state.getFluidState().isEmpty();
    }

    /**
     * 把液体换成空气。注意不能用 {@code removeBlock}：它对液体方块等于把水重新放回原地
     * （原版实现是 setBlock(流体自身的方块状态)），所以以前爆炸完水面纹丝不动。
     * 这里也不通知邻居：否则整片湖的水会在下一帧立刻回灌，看起来同样像没炸开。
     */
    private static void vaporize(ServerLevel serverLevel, BlockPos pos) {
        serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** 爆炸范围内的水与岩浆直接蒸发消失；水顺带冒一点白烟，岩浆什么都不留。 */
    private static void evaporateLiquids(ServerLevel serverLevel, Vec3 at, double radius) {
        int r = (int)Math.ceil(radius);
        BlockPos origin = BlockPos.containing(at);
        double radiusSqr = radius * radius;
        int steam = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (x * x + y * y + z * z > radiusSqr) continue;
            BlockPos pos = origin.offset(x, y, z);
            if (!serverLevel.hasChunkAt(pos)) continue;
            BlockState state = serverLevel.getBlockState(pos);
            if (!isLiquidBlock(state)) continue;
            boolean water = state.getFluidState().is(FluidTags.WATER);
            vaporize(serverLevel, pos);
            // 白烟只在少量格子上放，避免大范围蒸发时粒子数量爆掉。
            if (water && steam < 96 && serverLevel.getRandom().nextInt(6) == 0) {
                steam++;
                serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        3, 0.25, 0.12, 0.25, 0.02);
            }
        }
    }

    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) clearLight(serverLevel);
        NewSpellSystem.forgetSun(ownerId, this);
        super.remove(reason);
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID(OWNER_TAG, ownerId);
        tag.putInt("Charge", charge());
        tag.putBoolean("Released", isReleased());
        tag.putFloat("Dx", (float)direction().x);
        tag.putFloat("Dy", (float)direction().y);
        tag.putFloat("Dz", (float)direction().z);
        tag.putInt("Age", age);
        tag.putInt("ChargingTicks", chargingTicks);
        tag.putDouble("Traveled", traveled);
        CompoundTag carriedTag = new CompoundTag();
        for (UUID id : carried) carriedTag.putBoolean(id.toString(), true);
        tag.put("Carried", carriedTag);
        if (lightPos != null) tag.putLong("LightPos", lightPos.asLong());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        entityData.set(CHARGE, Math.max(0, Math.min(MAX_CHARGE, tag.getInt("Charge"))));
        entityData.set(RELEASED, tag.getBoolean("Released"));
        setDirection(new Vec3(tag.getFloat("Dx"), tag.getFloat("Dy"), tag.getFloat("Dz")));
        age = tag.getInt("Age");
        chargingTicks = tag.getInt("ChargingTicks");
        traveled = tag.getDouble("Traveled");
        carried.clear();
        CompoundTag carriedTag = tag.getCompound("Carried");
        for (String key : carriedTag.getAllKeys()) {
            try { carried.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) { }
        }
        lightPos = tag.contains("LightPos") ? BlockPos.of(tag.getLong("LightPos")) : null;
    }
}
