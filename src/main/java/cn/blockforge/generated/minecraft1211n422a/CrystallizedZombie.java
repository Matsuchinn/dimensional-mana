package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 晶化行尸：被高浓度魔素侵蚀到晶体化的僵尸。
 *
 * <p>与原版僵尸完全分家的一套数值与 AI：80 生命、半速、20 甲、1000 魔素；
 * 被动「晶爆反伤」受击后向 2 格内生物炸出奥术+物理伤害（8 秒冷却）；
 * 主动「狂暴啃咬」由 {@link CrystallizedZombieBiteGoal} 施放；
 * 死亡时不直接消失，而是原地僵直颤抖 4 秒（期间无敌），
 * 然后引爆一次半径 4 格、35 点爆炸伤害的晶爆，并向四周抛射 8~16 片
 * {@link DistortedCrystalShardEntity}，最后掉落 0~4 个畸变魔晶片再消散。</p>
 *
 * <p>元素抗性走生物专配（{@link MobProfile}，默认值登记在 {@link MobCombatConfig}）：
 * 奥术 75% / 水 50% / 冰 35% / 火 25%，音、金易伤 50%，光易伤 75%。</p>
 */
public final class CrystallizedZombie extends Zombie {
    /** 0 = 正常；1 = 晶爆尸潮的僵直颤抖阶段（同步给客户端放红色粒子与抖动动画）。 */
    private static final EntityDataAccessor<Byte> CRYSTAL_STATE =
            SynchedEntityData.defineId(CrystallizedZombie.class, EntityDataSerializers.BYTE);
    /** 狂暴啃咬进行中；同步给客户端放「头部上下啃咬」动画（{@link CrystallizedZombieRenderer}）。 */
    private static final EntityDataAccessor<Boolean> BITE_STATE =
            SynchedEntityData.defineId(CrystallizedZombie.class, EntityDataSerializers.BOOLEAN);
    /** 狂暴啃咬期间的加速修饰符 id：啃咬一开始挂上、松口就摘掉。 */
    private static final ResourceLocation BITE_FRENZY_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "crystallized_zombie_bite_frenzy");
    /** 啃咬期间的移速加成：+50%（乘法叠加在基础移速上）。 */
    private static final double BITE_FRENZY_AMOUNT = 0.5;

    // ---------------------------------------------------------------- 设计稿数值
    public static final float BITE_MANA_COST = 50.0F;
    public static final int BITE_TOTAL_TICKS = 100;
    public static final int BITE_INTERVAL = 10;
    public static final int BITE_COOLDOWN_TICKS = 240;
    public static final int RETALIATION_COOLDOWN_TICKS = 160;
    public static final int SHOCK_TICKS = 80;
    public static final double MANA_POOL = 1000.0;

    /** 紫色魔晶粒子（奥术配色，和 ManaOrb 的射线同一系）。 */
    static final ParticleOptions CRYSTAL_DUST =
            new DustParticleOptions(new Vector3f(0.68F, 0.36F, 1.0F), 1.25F);
    /** 颤抖阶段的红色警告粒子。 */
    static final ParticleOptions WARNING_DUST =
            new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.12F), 1.6F);

    /** 颤抖倒计时（服务端）；>0 即处于晶爆尸潮阶段。 */
    private int shockTicks;
    /** 被动反伤的下一次可用时刻（gameTime）。 */
    private long nextRetaliationTick;
    /** 狂暴啃咬的下一次可用时刻（gameTime）。 */
    private long biteCooldownUntil;
    /**  detonate 只允许执行一次。 */
    private boolean detonated;

    public CrystallizedZombie(EntityType<? extends CrystallizedZombie> type, Level level) {
        super(type, level);
        // 独立 AI：不要僵尸的破门与召唤同类，也不要下界传送门里带装备进场的那套。
        setCanBreakDoors(false);
    }

    // ---------------------------------------------------------------- 属性
    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                // 原版僵尸移速 0.23 的 1.25 倍（r236 起从半速改为快于原版）。
                .add(Attributes.MOVEMENT_SPEED, 0.2875)
                .add(Attributes.ARMOR, 20.0)
                // 设计稿给 2.0；原版属性上限 1.0，实际按完全免疫击退处理。
                .add(Attributes.KNOCKBACK_RESISTANCE, 2.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 35.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new CrystallizedZombieBiteGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // 会看「走不到」黑名单的版本（见 MobPathing）：隔着墙 / 围栏的目标不会每 10 刻被重选一次。
        this.targetSelector.addGoal(2, new PathAwareTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new PathAwareTargetGoal<>(this, Villager.class, true));
        this.targetSelector.addGoal(3, new PathAwareTargetGoal<>(this, IronGolem.class, true));
    }

    // ---------------------------------------------------------------- 生成与存档
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        // 完全独立的一只怪：不当小僵尸、不转化溺尸、不拿默认装备。
        setBaby(false);
        setLeftHanded(false);
        ManaData.setMobPools(this, MANA_POOL, 0);
        return result;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    /** r271：蚀魔荒原白天也要成群刷——晶化体质不怕太阳，和魔蚀骸骨同一套逻辑。 */
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomSource, DifficultyInstance difficulty) {
        // 晶化行尸不生成自带装备。
    }

    /** 咬死村民不做僵尸村民转化。 */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        return false;
    }

    /**
     * 普通攻击：命中必带负面效果，中毒与凋零五五开、必有其一（r236 需求）。
     * 各 2 秒 I 级，和狂暴啃咬的单口效果同一档位。
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide && target instanceof LivingEntity victim
                && !(victim instanceof CrystallizedZombie)) {
            victim.addEffect(random.nextBoolean()
                    ? new MobEffectInstance(MobEffects.POISON, 40, 0)
                    : new MobEffectInstance(MobEffects.WITHER, 40, 0));
        }
        return hit;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CRYSTAL_STATE, (byte)0);
        builder.define(BITE_STATE, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CrystalShockTicks", shockTicks);
        tag.putLong("CrystalRetaliationAt", nextRetaliationTick);
        tag.putLong("CrystalBiteCooldownAt", biteCooldownUntil);
        tag.putBoolean("CrystalDetonated", detonated);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        shockTicks = tag.getInt("CrystalShockTicks");
        nextRetaliationTick = tag.getLong("CrystalRetaliationAt");
        biteCooldownUntil = tag.getLong("CrystalBiteCooldownAt");
        detonated = tag.getBoolean("CrystalDetonated");
        if (shockTicks > 0) entityData.set(CRYSTAL_STATE, (byte)1);
    }

    // ---------------------------------------------------------------- 状态查询
    public boolean isCrystalShock() {
        return entityData.get(CRYSTAL_STATE) == (byte)1;
    }

    /** 狂暴啃咬是否正在进行（客户端动画用它）。 */
    public boolean isBiting() {
        return entityData.get(BITE_STATE);
    }

    public void setBiting(boolean biting) {
        entityData.set(BITE_STATE, biting);
    }

    /**
     * 狂暴啃咬开始：挂上 +50% 移速的临时修饰符。
     * 咬住时它基本钉在原地，只有被目标拉开一点才会迈步追上去——那时才看得出这个加速。
     */
    public void beginBiteFrenzy() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || isCrystalShock()) return;
        speed.addOrUpdateTransientModifier(new AttributeModifier(
                BITE_FRENZY_ID, BITE_FRENZY_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 松口 / 被打断 / 进入晶爆：摘掉加速修饰符。 */
    public void endBiteFrenzy() {
        setBiting(false);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(BITE_FRENZY_ID);
    }

    public long nextRetaliationTick() { return nextRetaliationTick; }
    public void setNextRetaliationTick(long tick) { this.nextRetaliationTick = tick; }
    public long biteCooldownUntil() { return biteCooldownUntil; }
    public void setBiteCooldownUntil(long tick) { this.biteCooldownUntil = tick; }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // 颤抖阶段：每刻撒红色警告粒子，越临近爆炸撒得越密。
            if (isCrystalShock()) {
                double angle = random.nextFloat() * Math.PI * 2.0;
                double radius = 0.3 + random.nextFloat() * 0.35;
                level().addParticle(WARNING_DUST,
                        getX() + Math.cos(angle) * radius,
                        getY(0.2 + random.nextDouble() * 1.6),
                        getZ() + Math.sin(angle) * radius,
                        0.0, 0.02, 0.0);
                if (random.nextFloat() < 0.25F) {
                    level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                            getX() + random.triangle(0.0, 0.4),
                            getY(0.2 + random.nextDouble() * 1.7),
                            getZ() + random.triangle(0.0, 0.4),
                            0.0, 0.0, 0.0);
                }
            }
            return;
        }
        if (shockTicks > 0) {
            shockTicks--;
            if (shockTicks % 20 == 0) {
                level().playSound(null, getX(), getY(), getZ(),
                        GeneratedMod.CRYSTALLIZED_ZOMBIE_SHATTER.get(), SoundSource.HOSTILE,
                        1.1F, 0.6F + (SHOCK_TICKS - shockTicks) * 0.004F);
            }
            if (shockTicks <= 0) detonate();
        }
    }

    /** Mob#serverAiStep 是 final，能挂僵直逻辑的入口是 customServerAiStep。 */
    @Override
    protected void customServerAiStep() {
        if (shockTicks > 0) {
            // 僵直：不跑僵尸的自定义 AI、不移动、不看人（移速已被清零，见 beginCrystalDetonation）。
            getNavigation().stop();
            setTarget(null);
            endBiteFrenzy();
            return;
        }
        super.customServerAiStep();
    }

    // ---------------------------------------------------------------- 死亡技能：晶爆尸潮
    /** 致命伤害被拦截后进入 80 刻的无敌僵直颤抖。 */
    public void beginCrystalDetonation() {
        if (level().isClientSide || shockTicks > 0 || detonated || !isAlive()) return;
        shockTicks = SHOCK_TICKS;
        entityData.set(CRYSTAL_STATE, (byte)1);
        // 啃咬中被打死的话，先把啃咬姿态和加速摘掉，免得僵直阶段还挂着 1.5 倍速。
        endBiteFrenzy();
        getNavigation().stop();
        setTarget(null);
        // 硬性钉死：把移速清零，颤抖期间任何寻路都动不了（反正本体马上就炸了）。
        if (getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        }
        // 颤抖期间不能被距离清理，否则爆炸会凭空消失。
        setPersistenceRequired();
        noActionTime = 0;
        level().playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CRYSTALLIZED_ZOMBIE_SHATTER.get(), SoundSource.HOSTILE, 1.4F, 0.65F);
        if (level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 26; i++) {
                double angle = serverLevel.getRandom().nextFloat() * Math.PI * 2.0;
                double radius = serverLevel.getRandom().nextFloat() * 0.7;
                serverLevel.sendParticles(CRYSTAL_DUST,
                        getX() + Math.cos(angle) * radius, getY(0.4 + serverLevel.getRandom().nextDouble() * 1.5),
                        getZ() + Math.sin(angle) * radius, 1, 0.04, 0.04, 0.04, 0.0);
            }
        }
    }

    /** 颤抖结束：晶爆 + 抛射魔晶片 + 掉落，然后本体消散。 */
    public void detonate() {
        if (level().isClientSide || detonated || !(level() instanceof ServerLevel level)) return;
        detonated = true;
        Vec3 center = getBoundingBox().getCenter();

        // 半径 4 格、固定 35 点爆炸伤害；同类不吃自己的晶爆，不破坏方块（免得炸玩家的家）。
        level.explode(this, level.damageSources().explosion((Explosion)null),
                new ExplosionDamageCalculator() {
                    @Override
                    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                        return entity instanceof LivingEntity && !(entity instanceof CrystallizedZombie);
                    }

                    @Override
                    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
                        return 35.0F;
                    }
                },
                center.x, center.y, center.z, 4.0F, false, Level.ExplosionInteraction.NONE);

        // 8~16 片畸变魔晶片，速度 16 m/s（0.8 格/刻），向四周带仰角抛射。
        RandomSource random = level.getRandom();
        int shards = 8 + random.nextInt(9);
        for (int i = 0; i < shards; i++) {
            DistortedCrystalShardEntity shard = GeneratedMod.DISTORTED_CRYSTAL_SHARD.get().create(level);
            if (shard == null) break;
            double yaw = random.nextDouble() * Math.PI * 2.0;
            double pitch = Math.toRadians(6.0 + random.nextDouble() * 58.0);
            Vec3 direction = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            shard.setPos(center.add(direction.scale(0.55)));
            shard.setOwner(this);
            shard.launch(this, direction);
            level.addFreshEntity(shard);
        }

        // 死亡掉落：0~4 个畸变魔晶片。
        int dropCount = random.nextInt(5);
        if (dropCount > 0) {
            ItemEntity drops = new ItemEntity(level, center.x, center.y, center.z,
                    new ItemStack(GeneratedMod.MOD_ITEMS.get(DistortedCrystalShardEntity.ITEM_ID).get(), dropCount));
            drops.setPickUpDelay(20);
            drops.setDeltaMovement(random.triangle(0.0, 0.1), 0.15, random.triangle(0.0, 0.1));
            level.addFreshEntity(drops);
        }

        // 视觉收尾：一圈紫色晶尘 + 碎裂巨响。
        for (int i = 0; i < 60; i++) {
            double angle = random.nextFloat() * Math.PI * 2.0;
            double radius = random.nextFloat() * 3.2;
            level.sendParticles(CRYSTAL_DUST,
                    center.x + Math.cos(angle) * radius, center.y - 0.8 + random.nextDouble() * 1.8,
                    center.z + Math.sin(angle) * radius, 1, 0.06, 0.06, 0.06, 0.03);
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 20, 1.0, 1.0, 1.0, 0.05);
        level.playSound(null, center.x, center.y, center.z,
                GeneratedMod.CRYSTALLIZED_ZOMBIE_SHATTER.get(), SoundSource.HOSTILE, 2.0F, 0.75F);
        discard();
    }

    // ---------------------------------------------------------------- 被动：晶爆反伤
    /**
     * 受击后的晶体爆裂：对 2 格内所有生物（除自身和同类）造成 10 点奥术 + 5 点物理，
     * 8 秒内部冷却。
     */
    public void retaliateBurst() {
        if (level().isClientSide || isCrystalShock()) return;
        long now = level().getGameTime();
        if (now < nextRetaliationTick) return;
        nextRetaliationTick = now + RETALIATION_COOLDOWN_TICKS;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(2.0),
                entity -> entity != this && !(entity instanceof CrystallizedZombie)
                        && entity.isAlive() && !entity.isInvulnerable())) {
            SpellDamage.hitSplitElement(level(), this, target, MagicEquipment.ARCANE, 10.0F,
                    level().damageSources().mobAttack(this), 5.0F);
        }
        level().playSound(null, getX(), getY(), getZ(),
                GeneratedMod.CRYSTALLIZED_ZOMBIE_SHATTER.get(), SoundSource.HOSTILE, 1.15F, 1.35F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(CRYSTAL_DUST, getX(), getY(1.0), getZ(), 28, 1.0, 1.0, 1.0, 0.02);
            serverLevel.sendParticles(ParticleTypes.END_ROD, getX(), getY(1.0), getZ(), 10, 0.9, 0.8, 0.9, 0.02);
        }
    }

    // ---------------------------------------------------------------- 声音：更破败恐怖的僵尸
    @Override
    protected SoundEvent getAmbientSound() {
        return GeneratedMod.CRYSTALLIZED_ZOMBIE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GeneratedMod.CRYSTALLIZED_ZOMBIE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GeneratedMod.CRYSTALLIZED_ZOMBIE_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.85F;
    }

    /** 整体压低的嗓音：半死不活的低频呻吟。 */
    @Override
    public float getVoicePitch() {
        return 0.62F;
    }
}
