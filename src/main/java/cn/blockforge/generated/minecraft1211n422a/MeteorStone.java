package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 万法千陨的巨石实体：本身不参与物理与碰撞，位置、悬停和飞行全部由
 * {@link EarthSpells} 在服务器上逐刻驱动，客户端只按同步位置画成石块。
 * 落地时溅出的小碎石用的是同一个实体，只是把渲染缩放调小。
 */
public final class MeteorStone extends Entity {
    private static final String OWNER_TAG = "Owner";
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MeteorStone.class,
            EntityDataSerializers.FLOAT);
    private UUID ownerId;

    public MeteorStone(EntityType<? extends MeteorStone> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void configure(ServerPlayer owner, Vec3 at) {
        ownerId = owner.getUUID();
        setPos(at);
        setDeltaMovement(Vec3.ZERO);
        setYRot(owner.getRandom().nextFloat() * 360.0F);
    }

    public UUID ownerId() {
        return ownerId;
    }

    /** 落地溅出的小碎石：同一个实体，渲染时缩小到 0.34 倍。 */
    public void configureDebris(ServerPlayer owner, Vec3 at) {
        configure(owner, at);
        entityData.set(SIZE, 0.34F);
    }

    /** 渲染缩放：普通巨石 1.0，小碎石 0.34。 */
    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 1.0F);
    }

    /** 服务器崩溃重载后没人认领的巨石会在七十秒内自行消散，避免留在世界里。 */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (ownerId == null || tickCount > 1400)) discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID(OWNER_TAG, ownerId);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(OWNER_TAG)) ownerId = tag.getUUID(OWNER_TAG);
    }
}
