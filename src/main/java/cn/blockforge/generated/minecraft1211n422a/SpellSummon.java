package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;

/** 有主人、有生命和存档的召唤物；不复用僵尸 AI，因而不会烧死或袭击村民。 */
public final class SpellSummon extends PathfinderMob {
    /** 寒冰傀儡每 3 秒射一次冰锥，最多 10 次后消散。 */
    private static final int GOLEM_SHOT_INTERVAL = 60;
    private static final int GOLEM_MAX_SHOTS = 10;
    private static final float GOLEM_SPIKE_DAMAGE = 7.0F;
    private UUID owner;
    private long expiresAt = Long.MAX_VALUE;
    private int attackDelay;
    private int golemCooldown;
    private int golemShots;
    public SpellSummon(EntityType<? extends SpellSummon> type, Level level) {
        super(type, level); setPersistenceRequired();
    }
    public boolean sword() { return getType() == NewSpellContent.FLYING_SWORD.get(); }
    public boolean tentacle() { return getType() == NewSpellContent.POISON_TENTACLE.get(); }
    public boolean golem() { return getType() == NewSpellContent.FROST_GOLEM.get(); }
    public UUID ownerId() { return owner; }
    public static AttributeSupplier.Builder attributes(double health, double attack, double speed, double armor, double toughness) {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.ATTACK_DAMAGE, attack)
                .add(Attributes.MOVEMENT_SPEED, speed).add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.ARMOR, armor).add(Attributes.ARMOR_TOUGHNESS, toughness);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override public boolean canUse() { return !sword() && !tentacle() && !golem() && !NewSpellSystem.immobilized(SpellSummon.this) && super.canUse(); }
            @Override public boolean canContinueToUse() { return !NewSpellSystem.immobilized(SpellSummon.this) && super.canContinueToUse(); }
        });
        goalSelector.addGoal(4, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE)); }
            @Override public boolean canUse() { LivingEntity p = ownerEntity(); return !sword() && !tentacle() && p != null
                    && getTarget() == null && distanceToSqr(p) > 9 && !NewSpellSystem.immobilized(SpellSummon.this); }
            @Override public void tick() { LivingEntity p = ownerEntity(); if (p != null) getNavigation().moveTo(p, 1.0); }
            @Override public void stop() { getNavigation().stop(); }
        });
    }
    private LivingEntity ownerEntity() {
        return owner == null || !(level() instanceof ServerLevel level) ? null : level.getServer().getPlayerList().getPlayer(owner);
    }
    private boolean valid(LivingEntity target, LivingEntity master) {
        return target != null && target.level() == level() && NewSpellSystem.enemy(master, target)
                && (!(target instanceof SpellSummon s) || !owner.equals(s.owner)) && distanceToSqr(target) < (tentacle() ? 36 : 576);
    }
    private static Vec3 closestPoint(net.minecraft.world.phys.AABB box, Vec3 point) {
        return new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (NewSpellSystem.immobilized(this)) { setTarget(null); getNavigation().stop(); return; }
        LivingEntity master = ownerEntity();
        if (master == null || !master.isAlive() || master.level() != level()) { setTarget(null); getNavigation().stop(); return; }
        if (tickCount % 5 == 0) {
            LivingEntity chosen = master.getLastHurtMob();
            if (!valid(chosen, master)) chosen = master.getLastHurtByMob();
            if (!valid(chosen, master)) chosen = getLastHurtByMob();
            if (!valid(chosen, master)) chosen = getTarget();
            if (!valid(chosen, master)) chosen = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(tentacle() ? 5 : 14),
                    e -> e instanceof Monster && valid(e, master) && hasLineOfSight(e)).stream()
                    .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            setTarget(chosen);
        }
        LivingEntity target = getTarget();
        if (golem()) { golemStep(target, master); return; }
        if (sword()) {
            setNoGravity(true); getNavigation().stop();
            // 追向目标碰撞箱中离飞剑最近的位置，而不是追高大生物的中心点后停在攻击距离外。
            Vec3 destination = target != null ? closestPoint(target.getBoundingBox(), position())
                    : master.position().add(0, 1.5, 0);
            Vec3 diff = destination.subtract(position());
            Vec3 move = diff.length() > (target != null ? 0.35 : 2.2) ? diff.normalize().scale(0.05) : Vec3.ZERO;
            if (level().noCollision(this, getBoundingBox().move(move))) setDeltaMovement(move);
            else {
                // 小障碍前先上浮，不让飞剑永远卡在一格高的台阶上。
                Vec3 rise = new Vec3(0, 0.05, 0);
                setDeltaMovement(level().noCollision(this, getBoundingBox().move(rise)) ? rise : Vec3.ZERO);
            }
            if (diff.lengthSqr() > 0.1) setYRot((float)Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        } else if (tentacle()) { getNavigation().stop(); setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0); }
        if (attackDelay > 0) attackDelay--;
        boolean swordContact = sword() && target != null && getBoundingBox().inflate(0.65).intersects(target.getBoundingBox());
        boolean tentacleReach = tentacle() && target != null && distanceToSqr(target) <= 16 && hasLineOfSight(target);
        if ((swordContact || tentacleReach) && attackDelay == 0) {
            if (doHurtTarget(target)) attackDelay = 20;
        }
    }
    /** 寒冰傀儡站定射击：每 3 秒朝目标射一发冰锥，满 10 发就消散；没有目标时照常跟随主人。 */
    private void golemStep(LivingEntity target, LivingEntity master) {
        if (golemCooldown > 0) golemCooldown--;
        if (target == null || !target.isAlive() || !valid(target, master)) {
            setTarget(null);
            return;
        }
        // 进入射击状态后原地站定，避免边追边打时冰锥全部打空。
        getNavigation().stop();
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 自定义渲染器只读身体朝向，这里同步转身，让南瓜头始终对着目标。
        Vec3 diff = target.position().subtract(position());
        if (diff.x * diff.x + diff.z * diff.z > 0.0001) {
            float face = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
            setYRot(face);
            setYHeadRot(face);
        }
        if (golemCooldown > 0) return;
        if (distanceToSqr(target) > 16.0 * 16.0 || !hasLineOfSight(target)) return;
        shootIceSpike(master, target);
        golemCooldown = GOLEM_SHOT_INTERVAL;
    }

    /** 初级冰锥：7 点冰伤并减速，命中时沿轨迹把水冻成霜冰。 */
    private void shootIceSpike(LivingEntity master, LivingEntity target) {
        Vec3 start = getEyePosition();
        Vec3 end = target.getBoundingBox().getCenter();
        float damage = SpellAttributes.calculateSpellDamage(master, target, GOLEM_SPIKE_DAMAGE);
        damage = ElementReactionSystem.elementalDamage(master, target, ElementType.ICE, damage);
        // 独立弹体的每一发都应结算全额伤害，不能被上一发的受击保护吞掉。
        boolean hit = SpellDamage.hitElement(level(), master, target, ElementType.ICE.id(), damage);
        if (hit) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            master.setLastHurtMob(target);
            setLastHurtMob(target);
            if (target instanceof net.minecraft.world.entity.Mob mob && mob.canAttack(this)) mob.setTarget(this);
        }
        swing(InteractionHand.MAIN_HAND, true);
        SpellVisualPayload.send(this, SpellDefinition.ICE_SPIKE, SpellVisualPayload.RELEASE, -1, start, end, 16);
        if (level() instanceof ServerLevel serverLevel) {
            SpellEnvironmentSystem.freezeAlong(serverLevel, start, end);
            serverLevel.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.55F, 1.7F);
        }
        golemShots++;
        if (golemShots >= GOLEM_MAX_SHOTS) {
            if (level() instanceof ServerLevel serverLevel)
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                        getX(), getY() + 0.9, getZ(), 30, 0.45, 0.7, 0.45, 0.05);
            discard();
        }
    }

    @Override public boolean doHurtTarget(Entity entity) {
        LivingEntity master = ownerEntity();
        if (!(entity instanceof LivingEntity target) || master == null || !valid(target, master) || NewSpellSystem.immobilized(this)) return false;
        float amount = (float)getAttributeValue(Attributes.ATTACK_DAMAGE);
        ElementType swingElement = sword() ? ElementType.METAL : tentacle() ? ElementType.POISON : ElementType.NATURE;
        amount = ElementReactionSystem.elementalDamage(master, target, swingElement, amount);
        boolean success = SpellDamage.hitElement(level(), master, target, swingElement.id(), amount);
        if (success) {
            swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            // 间接魔法伤害默认会把玩家主人视为真凶，部分中立生物因此不会向召唤物还手。
            // 明确记录召唤物本体并设置目标，让所有具备战斗 AI 的生物都能正常反击。
            target.setLastHurtByMob(this);
            if (target instanceof net.minecraft.world.entity.Mob mob && mob.canAttack(this)) mob.setTarget(this);
            if (!sword() && !tentacle()) NewSpellSystem.effect(target, NewSpellContent.VINE_SLOW, 40);
        }
        return success;
    }
    @Override public void move(net.minecraft.world.entity.MoverType type, Vec3 motion) {
        if (type == net.minecraft.world.entity.MoverType.SELF && !tentacle()) {
            double limit = sword() ? 0.05 : golem() ? 0.2 : 0.15;
            double length = sword() ? motion.length() : motion.horizontalDistance();
            if (length > limit) motion = sword() ? motion.scale(limit / length)
                    : new Vec3(motion.x * limit / length, motion.y, motion.z * limit / length);
        }
        super.move(type, motion);
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (owner == null || level().getGameTime() >= expiresAt) { discard(); return; }
        LivingEntity master = ownerEntity();
        if (!tentacle() && master instanceof ServerPlayer p && !roster(p, rosterKey()).contains(getUUID().toString())) { discard(); return; }
        if (!sword() && !tentacle()) {
            Vec3 motion = getDeltaMovement(); double h = motion.horizontalDistance();
            double limit = golem() ? 0.2 : 0.15;
            if (h > limit) setDeltaMovement(motion.x * limit / h, motion.y, motion.z * limit / h);
        }
    }
    @Override public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && !tentacle()) {
            LivingEntity master = ownerEntity();
            if (master instanceof ServerPlayer player) roster(player, rosterKey()).remove(getUUID().toString());
        }
        super.remove(reason);
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); if (owner != null) tag.putUUID("SpellOwner", owner); tag.putLong("ExpiresAt", expiresAt);
        tag.putInt("GolemShots", golemShots); tag.putInt("GolemCooldown", golemCooldown);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); owner = tag.hasUUID("SpellOwner") ? tag.getUUID("SpellOwner") : null;
        expiresAt = tag.contains("ExpiresAt") ? tag.getLong("ExpiresAt") : 0;
        golemShots = tag.getInt("GolemShots"); golemCooldown = tag.getInt("GolemCooldown");
    }
    /** 三类召唤物各自计数：每个玩家最多 4 只藤蔓仆从、4 把飞剑、4 只寒冰傀儡。 */
    private static String rosterKey(EntityType<?> type) {
        if (type == NewSpellContent.FLYING_SWORD.get()) return "swordSummons";
        if (type == NewSpellContent.FROST_GOLEM.get()) return "frostGolemSummons";
        return "vineSummons";
    }
    private String rosterKey() { return rosterKey(getType()); }
    private static CompoundTag roster(ServerPlayer player, String key) {
        CompoundTag root = player.getPersistentData().getCompound("DimensionalMana");
        if (!root.contains(key)) root.put(key, new CompoundTag());
        return root.getCompound(key);
    }
    public static void summon(ServerPlayer player, EntityType<SpellSummon> type, int count) {
        ServerLevel level = player.serverLevel();
        boolean sword = type == NewSpellContent.FLYING_SWORD.get(), tentacle = type == NewSpellContent.POISON_TENTACLE.get();
        String keyName = rosterKey(type);
        for (int i = 0; i < count; i++) {
            SpellSummon summon = type.create(level); if (summon == null) continue;
            Vec3 at;
            if (tentacle) {
                Vec3 end = NewSpellSystem.blockEnd(player, player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(5)));
                at = NewSpellSystem.ground(level, end);
                if (at.distanceToSqr(player.position()) > 25) at = NewSpellSystem.ground(level, player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(4)));
            } else at = NewSpellSystem.ground(level, player.position().add(Math.cos(i * Math.PI) * 1.3, 0, 1.2));
            if (sword) at = at.add(0, 1, 0);
            summon.setPos(at.add(0, 0.05, 0));
            if (!level.noCollision(summon, summon.getBoundingBox())) {
                summon.setPos(player.position().add(0, sword ? 1.2 : 0, 0));
                if (!level.noCollision(summon, summon.getBoundingBox())) { summon.discard(); continue; }
            }
            summon.owner = player.getUUID(); summon.expiresAt = tentacle ? Long.MAX_VALUE : level.getGameTime() + 2400;
            summon.setHealth(summon.getMaxHealth());
            if (!level.addFreshEntity(summon)) continue;
            ManaData.setAura(summon, 0);
            if (!tentacle) {
                CompoundTag roster = roster(player, keyName);
                for (String key : SetCopy.keys(roster)) if (roster.getLong(key) <= level.getGameTime()) roster.remove(key);
                while (roster.getAllKeys().size() >= 4) {
                    String oldest = roster.getAllKeys().stream().min(Comparator.comparingLong(roster::getLong)).orElseThrow();
                    roster.remove(oldest);
                    try {
                        UUID id = UUID.fromString(oldest);
                        for (ServerLevel world : level.getServer().getAllLevels()) { Entity old = world.getEntity(id); if (old instanceof SpellSummon) old.discard(); }
                    } catch (IllegalArgumentException ignored) { }
                }
                roster.putLong(summon.getUUID().toString(), summon.expiresAt);
            }
        }
    }
    private static final class SetCopy { static java.util.Set<String> keys(CompoundTag tag) { return new java.util.HashSet<>(tag.getAllKeys()); } }
}
