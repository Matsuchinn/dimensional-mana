package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 真正逐刻飞行的元素弹体；方块遮挡与受击目标按每段路径计算，不用瞬间射线冒充。 */
public final class SpellProjectile extends Projectile {
    private static final EntityDataAccessor<String> SPELL = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> INDEX = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.INT);
    private final Set<UUID> hit = new HashSet<>();
    private Vec3 direction = new Vec3(0, 0, 1);
    private Vec3 launch = Vec3.ZERO;
    private double traveled;
    private int age;
    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(SPELL, "pyroblast"); builder.define(INDEX, 0); }
    public SpellDefinition spell() {
        SpellDefinition spell = SpellDefinition.byId(entityData.get(SPELL));
        return spell == null ? SpellDefinition.PYROBLAST : spell;
    }
    public int index() { return entityData.get(INDEX); }
    public void configure(ServerPlayer owner, SpellDefinition spell, Vec3 at, Vec3 direction, int index) {
        setOwner(owner); entityData.set(SPELL, spell.id()); entityData.set(INDEX, index);
        setPos(at); launch = at; this.direction = direction.normalize();
        setDeltaMovement(this.direction.scale(speed()));
        setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        setXRot((float)-Math.toDegrees(Math.asin(direction.y)));
    }
    private double speed() {
        return switch (spell()) { case ROCK_ORBS -> 0.42; case WATER_PYTHON -> 0.65; case PYROBLAST -> 0.7; default -> 0.85; };
    }
    private double range() {
        return switch (spell()) { case FLAME_SLASH -> 6; case WATER_PYTHON -> 16; case ROCK_ORBS -> 13; default -> 12; };
    }
    @Override public void tick() {
        super.tick(); age++;
        if (level().isClientSide) return;
        if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || age > 160) { discard(); return; }
        ServerLevel level = (ServerLevel)level();
        if (spell() == SpellDefinition.ROCK_ORBS && age <= 16 + index() * 10) {
            setPos(launch.add(0, Math.min(1, age / 16.0) * 1.7, 0));
            setDeltaMovement(Vec3.ZERO); return;
        }
        Vec3 velocity;
        if (spell() == SpellDefinition.ROCK_ORBS) {
            if (age == 17 + index() * 10) setDeltaMovement(direction.scale(speed()).add(0, 0.20, 0));
            velocity = getDeltaMovement().add(0, -0.025, 0); setDeltaMovement(velocity);
        } else velocity = direction.scale(Math.min(speed(), range() - traveled));
        Vec3 start = position(), intended = start.add(velocity), end = NewSpellSystem.blockEnd(owner, start, intended);
        if (spell().element() == ElementType.FIRE) SpellEnvironmentSystem.igniteAlong(owner, level, start, end);
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(end))) { discard(); return; }
        boolean block = end.distanceToSqr(intended) > 0.0001;
        if (spell() == SpellDefinition.ROCK_ORBS && !level.noCollision(this, new AABB(end.add(-0.48, -0.48, -0.48), end.add(0.48, 0.48, 0.48)))) block = true;
        if (spell() == SpellDefinition.PYROBLAST || spell() == SpellDefinition.ROCK_ORBS) {
            var targets = NewSpellSystem.pathTargets(owner, start, end, spell() == SpellDefinition.ROCK_ORBS ? 0.5 : 0.4);
            if (!targets.isEmpty()) {
                LivingEntity target = targets.getFirst();
                Vec3 impact = target.getBoundingBox().inflate(spell() == SpellDefinition.ROCK_ORBS ? 0.5 : 0.4).clip(start, end).orElse(start);
                setPos(impact);
                if (spell() == SpellDefinition.PYROBLAST) explode(owner);
                else { NewSpellSystem.damage(owner, target, 30, ElementType.EARTH); shatter(); }
                return;
            }
        } else if (spell() == SpellDefinition.WATER_PYTHON) {
            Vec3 tail = start.subtract(direction.scale(Math.min(5, traveled)));
            for (LivingEntity target : NewSpellSystem.pathTargets(owner, tail, end, 0.65)) {
                if (!NewSpellSystem.clear(owner, start, target.getBoundingBox().getCenter()) || !hit.add(target.getUUID())) continue;
                NewSpellSystem.damage(owner, target, 28, ElementType.WATER);
                NewSpellSystem.knock(start.subtract(direction), target, 4);
                NewSpellSystem.control(owner, target, NewSpellContent.STUN, 40);
            }
        } else {
            Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize();
            if (side.lengthSqr() < 0.01) side = new Vec3(1, 0, 0);
            if (spell() == SpellDefinition.GALE_SLASHES && index() == 1) side = direction.cross(side).normalize();
            for (double offset = -2.5; offset <= 2.501; offset += 0.4) {
                Vec3 a = start.add(side.scale(offset)), b = end.add(side.scale(offset));
                Vec3 tip = NewSpellSystem.blockEnd(owner, a, b);
                // 每条刃线从发射位置检查遮挡，防止侧翼穿墙后在墙后重新造成伤害。
                if (!NewSpellSystem.clear(owner, launch.add(side.scale(offset)), a)
                        || !NewSpellSystem.clear(owner, start, a)) continue;
                NewSpellSystem.pathDamage(owner, a, tip, 0.3,
                        spell() == SpellDefinition.FLAME_SLASH ? 8 : index() == 0 ? 5 : index() == 1 ? 24 : 8,
                        spell().element(), hit, 0);
            }
        }
        setPos(end); traveled += start.distanceTo(end);
        if (block || traveled >= range() - 0.01) {
            if (spell() == SpellDefinition.PYROBLAST) explode(owner);
            else if (spell() == SpellDefinition.ROCK_ORBS) shatter();
            else discard();
        }
    }
    private void explode(LivingEntity owner) {
        for (LivingEntity target : NewSpellSystem.enemies(owner, position(), 4)) NewSpellSystem.damage(owner, target, 32, ElementType.FIRE);
        ServerLevel level = (ServerLevel)level();
        SpellEnvironmentSystem.igniteAt(level, position(), owner);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 8, 1.5, 1.5, 1.5, 0);
        if (owner instanceof ServerPlayer player) NewSpellSystem.visual(player, SpellDefinition.PYROBLAST, position(), position(), -1, 24);
        discard();
    }
    private void shatter() {
        ServerLevel level = (ServerLevel)level();
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), getX(), getY(), getZ(), 35, 0.4, 0.4, 0.4, 0.12);
        level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.STONE_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1, 0.8F);
        discard();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putString("Spell", spell().id()); tag.putInt("Index", index());
        tag.putInt("Age", age); tag.putDouble("Traveled", traveled);
        tag.putDouble("Dx", direction.x); tag.putDouble("Dy", direction.y); tag.putDouble("Dz", direction.z);
        tag.putDouble("Lx", launch.x); tag.putDouble("Ly", launch.y); tag.putDouble("Lz", launch.z);
        CompoundTag hits = new CompoundTag(); for (UUID id : hit) hits.putBoolean(id.toString(), true); tag.put("Hits", hits);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); entityData.set(SPELL, tag.getString("Spell")); entityData.set(INDEX, tag.getInt("Index"));
        age = tag.getInt("Age"); traveled = tag.getDouble("Traveled");
        direction = new Vec3(tag.getDouble("Dx"), tag.getDouble("Dy"), tag.getDouble("Dz"));
        launch = new Vec3(tag.getDouble("Lx"), tag.getDouble("Ly"), tag.getDouble("Lz"));
        for (String key : tag.getCompound("Hits").getAllKeys()) { try { hit.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) { } }
    }
}
