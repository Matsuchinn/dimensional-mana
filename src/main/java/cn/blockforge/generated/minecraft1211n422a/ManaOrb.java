package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 魔法大师披风在施法时召唤的魔素凝聚球。
 *
 * <p>持续 12 秒、环绕并跟随施法者，每 2 秒朝施法者最近攻击的目标打出一道奥术射线：
 * 伤害为施法者最大魔素的 7.5%，并附加奥术附着。同一名玩家同时只存在一个球。</p>
 */
public final class ManaOrb extends Entity {
    private static final int LIFETIME = 240;
    private static final int SHOT_INTERVAL = 40;
    private static final double ORBIT_RADIUS = 1.15;
    private static final double MAX_RANGE = 24.0;
    private static final float ARCANE_COLOR = 0.68F;

    private UUID ownerId;
    private double phase;

    public ManaOrb(EntityType<? extends ManaOrb> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setOwner(Player owner) {
        ownerId = owner.getUUID();
        setPos(orbitPoint(owner));
    }

    public UUID ownerId() { return ownerId; }

    private Player owner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        return player;
    }

    private Vec3 orbitPoint(Player owner) {
        double angle = phase;
        return owner.position().add(Math.cos(angle) * ORBIT_RADIUS,
                owner.getBbHeight() * 0.7 + Math.sin(angle * 1.7) * 0.18,
                Math.sin(angle) * ORBIT_RADIUS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        phase = tag.getDouble("Phase");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putDouble("Phase", phase);
    }

    @Override
    public void tick() {
        super.tick();
        phase += 0.06;
        if (level().isClientSide) {
            if (tickCount % 3 == 0) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        getX(), getY(), getZ(), 0, 0.01, 0);
            }
            return;
        }
        Player owner = owner();
        if (owner == null || !owner.isAlive() || tickCount > LIFETIME) {
            discard();
            return;
        }
        setPos(position().lerp(orbitPoint(owner), 0.35));
        if (tickCount % SHOT_INTERVAL == 0) fire(owner);
    }

    private void fire(Player owner) {
        LivingEntity target = validTarget(owner, owner.getLastHurtMob()) ? owner.getLastHurtMob() : null;
        if (target == null && validTarget(owner, owner.getLastHurtByMob())) target = owner.getLastHurtByMob();
        if (target == null || !(level() instanceof ServerLevel level)) return;
        double maxMana = ManaData.get(owner).maxMana();
        NewSpellSystem.damage(owner, target, (float)(maxMana * 0.075), null);
        Vec3 start = position();
        Vec3 end = target.getBoundingBox().getCenter();
        Vector3f rgb = new Vector3f(ARCANE_COLOR, 0.48F, 1.0F);
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.9F);
        for (int i = 0; i <= 16; i++) {
            Vec3 point = start.lerp(end, i / 16.0);
            level.sendParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    private boolean validTarget(Player owner, LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level()
                && target != owner && distanceToSqr(target) <= MAX_RANGE * MAX_RANGE
                && NewSpellSystem.enemy(owner, target);
    }

    /** 同一个施法者只保留一个魔素凝聚球；再次触发时只刷新旧球的寿命。 */
    public static void summon(Player owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        for (ManaOrb orb : level.getEntitiesOfClass(ManaOrb.class, owner.getBoundingBox().inflate(12.0))) {
            if (owner.getUUID().equals(orb.ownerId)) {
                orb.tickCount = 0;
                orb.setPos(orb.orbitPoint(owner));
                return;
            }
        }
        ManaOrb orb = GeneratedMod.MANA_ORB.get().create(level);
        if (orb == null) return;
        orb.setOwner(owner);
        level.addFreshEntity(orb);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isAttackable() { return false; }
}
