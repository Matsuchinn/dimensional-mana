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
    private Vec3 aim = Vec3.ZERO;
    private Vec3 clientTarget;
    private int clientSteps;
    static final double ROCK_GRAVITY = 0.003;

    /** 位置包只修正显示轨迹；碰撞和伤害仍完全由服务端决定。 */
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        clientTarget = new Vec3(x, y, z);
        // 此实体每刻同步一次，不能套三刻缓动，否则画面会落后真实碰撞两刻。
        clientSteps = 1;
        setYRot(yaw); setXRot(pitch);
    }
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
        setDeltaMovement(spell == SpellDefinition.ROCK_ORBS ? Vec3.ZERO : this.direction.scale(speed()));
        Vec3 eye = owner.getEyePosition();
        aim = NewSpellSystem.blockEnd(owner, eye, eye.add(this.direction.scale(range())));
        if (spell == SpellDefinition.ROCK_ORBS) {
            var targets = NewSpellSystem.pathTargets(owner, eye, aim, 0.12);
            if (!targets.isEmpty()) aim = targets.getFirst().getBoundingBox().inflate(0.12).clip(eye, aim).orElse(aim);
        }
        setYRot((float)Math.toDegrees(Math.atan2(-this.direction.x, this.direction.z)));
        setXRot((float)-Math.toDegrees(Math.asin(Math.clamp(this.direction.y, -1, 1))));
    }
    /**
     * 像箭矢一样飞出去、碰到才结算的基础投射法术。
     *
     * <p>这些法术以前在施法瞬间用一条射线把伤害全算完，画面上的弹体只是演出，
     * 于是「瞄准远处生物抬手就掉血」。现在它们是真正的实体：沿准心飞行，撞到生物或方块才生效。</p>
     */
    static boolean ballistic(SpellDefinition spell) {
        return switch (spell) {
            case FIREBALL, WATER_BOLT, ICE_SPIKE, WIND_BLADE, THORNS, LIGHT_BOLT, POISON_BOLT, SHARP_EDGE -> true;
            default -> false;
        };
    }

    /** 弹体自身的碰撞半径：风刃扁而宽，冰锥细长，其余是圆球。 */
    private double hitRadius() {
        return switch (spell()) {
            case WIND_BLADE -> 0.42;
            case FIREBALL -> 0.36;
            case ICE_SPIKE -> 0.30;
            default -> 0.28;
        };
    }

    /**
     * 弹体的飞行速度（格/刻）。
     *
     * <p>中级投射射程五个区块，速度放慢后八十格大约飞五秒，能看清弹体、拖尾和命中点；
     * 基础投射按八格一刻的老速度会像瞬移，统一压到每刻两格。</p>
     */
    private double speed() {
        if (ballistic(spell())) return SpellRange.BASIC_SPEED;
        return switch (spell()) {
            case ROCK_ORBS -> 0.50;
            case WATER_PYTHON -> 0.62;
            case PYROBLAST -> 0.80;
            case FLAME_SLASH -> 0.85;   // 烈焰斩是近身斩击，不走远程射程
            default -> 0.78;
        };
    }

    /** 射程；烈焰斩是近身六格，基础投射八个区块，其余中级统一五个区块。 */
    private double range() {
        if (ballistic(spell())) return SpellRange.BASIC;
        return switch (spell()) {
            case FLAME_SLASH -> 6;
            default -> SpellRange.INTERMEDIATE;
        };
    }
    /**
     * 弹体每刻按实际运动方向转向。
     *
     * <p>参考实现里 {@code MagicProjectile} 每刻重算 yaw/pitch，所以朝斜下打的火球
     * 会顺着弧线低头，而不是保持出手那一帧的角度。这里只改朝向，不改轨迹和判定。</p>
     */
    private void rotateWithMotion() {
        Vec3 motion = getDeltaMovement();
        double horizontal = motion.horizontalDistance();
        if (motion.lengthSqr() < 1.0E-6) return;
        float yaw = (float)(Math.toDegrees(Math.atan2(-motion.x, motion.z)));
        float pitch = (float)(-Math.toDegrees(Math.atan2(motion.y, horizontal)));
        setYRot(yaw); setXRot(pitch);
        // 首刻两边一起初始化，避免 lerp 从 0 度扫到目标角。
        yRotO = yaw; xRotO = pitch;
    }

    @Override public void tick() {
        super.tick(); age++;
        if (level().isClientSide) {
            // 不让低频位置包直接瞬移，也不在客户端重复施加重力。
            if (clientSteps > 0 && clientTarget != null) {
                setPos(position().lerp(clientTarget, 1.0 / clientSteps));
                clientSteps--;
            }
            rotateWithMotion();
            return;
        }
        if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || age > 240) { discard(); return; }
        ServerLevel level = (ServerLevel)level();
        if (spell() == SpellDefinition.ROCK_ORBS && age <= 16 + index() * 10) {
            setPos(launch.add(0, Math.min(1, age / 16.0) * 1.7, 0));
            setDeltaMovement(Vec3.ZERO); return;
        }
        if (spell() == SpellDefinition.ROCK_ORBS && !level.noCollision(this,
                new AABB(position().add(-0.46, -0.46, -0.46), position().add(0.46, 0.46, 0.46)))) {
            shatter(); return;
        }
        Vec3 velocity;
        if (spell() == SpellDefinition.ROCK_ORBS) {
            if (age == 17 + index() * 10) {
                // 三颗石球从各自升起的位置汇向同一个准心落点；离散重力补偿保留下坠弧线。
                Vec3 delta = aim.subtract(position());
                double ticks = Math.clamp(Math.ceil(delta.length() / speed()), 4.0, 160.0);
                setDeltaMovement(delta.scale(1.0 / ticks).add(0, ROCK_GRAVITY * (ticks - 1) * 0.5, 0));
            }
            velocity = getDeltaMovement();
            setDeltaMovement(velocity.add(0, -ROCK_GRAVITY, 0));
        } else {
            velocity = direction.scale(Math.max(0, Math.min(speed(), range() - traveled)));
            setDeltaMovement(velocity);
        }
        Vec3 start = position(), intended = start.add(velocity), end = NewSpellSystem.blockEnd(owner, start, intended);
        if (spell().element() == ElementType.FIRE) SpellEnvironmentSystem.igniteAlong(owner, level, start, end);
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(end))) { discard(); return; }
        boolean block = end.distanceToSqr(intended) > 0.0001;
        if (spell() == SpellDefinition.ROCK_ORBS) {
            // 沿石球表面扫过整段位移，避免末点检测漏过薄墙或在墙后伤人。
            double nearest = start.distanceToSqr(end);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                Vec3 offset = new Vec3(x * 0.46, y * 0.46, z * 0.46);
                Vec3 clipped = NewSpellSystem.blockEnd(owner, start.add(offset), intended.add(offset)).subtract(offset);
                if (clipped.distanceToSqr(intended) > 0.0001 && start.distanceToSqr(clipped) <= nearest) {
                    end = clipped; nearest = start.distanceToSqr(clipped); block = true;
                }
            }
        }
        if (ballistic(spell())) {
            // 箭矢式判定：只检查这一小段位移里有没有碰到生物，碰到了才算命中。
            // 风刃可以穿过三个目标，其余弹体碰一下就消散。
            double radius = hitRadius();
            int pierce = spell() == SpellDefinition.WIND_BLADE ? 3 : 1;
            for (LivingEntity target : NewSpellSystem.pathTargets(owner, start, end, radius)) {
                if (hit.size() >= pierce) break;
                if (hit.contains(target.getUUID())) continue;
                Vec3 impact = target.getBoundingBox().inflate(radius).clip(start, end).orElse(start);
                if (!NewSpellSystem.clear(owner, impact, target.getBoundingBox().getCenter())) continue;
                hit.add(target.getUUID());
                if (owner instanceof ServerPlayer player) {
                    SpellCasting.ballisticHit(player, target, spell());
                    SpellCasting.ballisticImpact(player, spell(), impact);
                }
            }
            if (hit.size() >= pierce) { discard(); return; }
            setPos(end);
            traveled += start.distanceTo(end);
            if (block || traveled >= range() - 0.01) {
                if (owner instanceof ServerPlayer player) SpellCasting.ballisticImpact(player, spell(), end);
                discard();
            }
            return;
        }
        if (spell() == SpellDefinition.PYROBLAST || spell() == SpellDefinition.ROCK_ORBS) {
            var targets = NewSpellSystem.pathTargets(owner, start, end, spell() == SpellDefinition.ROCK_ORBS ? 0.5 : 0.4);
            targets.removeIf(target -> !NewSpellSystem.clear(owner, start, target.getBoundingBox().getCenter()));
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
            // 三连斩全部横切，伤害宽轴与客户端刀锋使用同一水平侧轴。
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
        rotateWithMotion();
        // 弧线比直线长，石球按距起点的空间距离限制，不把上抛额外路程提前扣光。
        boolean exhausted = spell() == SpellDefinition.ROCK_ORBS
                ? position().distanceToSqr(launch) >= (range() + 3) * (range() + 3)
                : traveled >= range() - 0.01;
        if (block || exhausted) {
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
        if (owner instanceof ServerPlayer player) SpellVisualPayload.send(player, SpellDefinition.PYROBLAST,
                SpellVisualPayload.PROJECTILE_IMPACT, -1, position(), position(), 24);
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
        tag.putDouble("AimX", aim.x); tag.putDouble("AimY", aim.y); tag.putDouble("AimZ", aim.z);
        CompoundTag hits = new CompoundTag(); for (UUID id : hit) hits.putBoolean(id.toString(), true); tag.put("Hits", hits);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); entityData.set(SPELL, tag.getString("Spell")); entityData.set(INDEX, tag.getInt("Index"));
        age = tag.getInt("Age"); traveled = tag.getDouble("Traveled");
        direction = new Vec3(tag.getDouble("Dx"), tag.getDouble("Dy"), tag.getDouble("Dz"));
        launch = new Vec3(tag.getDouble("Lx"), tag.getDouble("Ly"), tag.getDouble("Lz"));
        aim = tag.contains("AimX") ? new Vec3(tag.getDouble("AimX"), tag.getDouble("AimY"), tag.getDouble("AimZ"))
                : launch.add(direction.scale(range()));
        for (String key : tag.getCompound("Hits").getAllKeys()) { try { hit.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) { } }
    }
}
