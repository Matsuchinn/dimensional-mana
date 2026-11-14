package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 魂火箭矢：魔蚀骸骨射出的紫焰骨箭。
 *
 * <p>设计稿里的三件事都写在这里：</p>
 * <ul>
 *   <li><b>伤害</b>：4 点物理 + 8 点奥术 + 8 点火焰，一段一段分开结算，
 *       才不会被受击无敌帧吞掉后面两段；</li>
 *   <li><b>追踪</b>：飞行中每刻把速度方向往目标身上拧一点，速度恒为 24 m/s（1.2 格/刻）；</li>
 *   <li><b>二次袭击</b>：<b>真的从目标身边掠过之后</b>才「升空」——竖直上冲 8 刻，然后按那一刻
 *       锁定的方向以 44 m/s 直冲回去（这一段不再跟踪，躲得掉）。没飞到目标身上之前不会提前变招，
 *       详见 {@link #canBeginSecondAttack()}。</li>
 * </ul>
 *
 * <p><b>误伤</b>：{@link #onHitEntity} 先看撞到的是不是发射者认定的目标，
 * 不是就直接穿过去，伤害一点不结算（见 {@link #isIntendedTarget}）。</p>
 *
 * <p><b>格挡</b>：普通魂火箭矢会被盾牌挡下（{@link #piercesShield()} 为 false）；
 * 招架（斗气弹反）走 {@link BattleQiParry} 那套「左键打掉弹射物」，因为本类就是 {@link AbstractArrow}，
 * 天然在可弹范围内，不需要额外接线。</p>
 */
public class SoulFireArrowEntity extends AbstractArrow {
    /** 4 点物理。 */
    public static final float PHYSICAL_DAMAGE = 4.0F;
    /** 8 点奥术。 */
    public static final float ARCANE_DAMAGE = 8.0F;
    /** 8 点火焰。 */
    public static final float FIRE_DAMAGE = 8.0F;

    /** 飞行速度 24 m/s = 1.2 格/刻（原来是 16 m/s，出手太慢追不上跑动的玩家）。 */
    public static final double SPEED = 1.2;
    /** 刚出手的这么多刻内绝不升空，免得贴脸时刚出门就变招。 */
    public static final int MIN_FLY_TICKS = 8;
    /** 没有锁定目标时：飞满这么多刻就升空（原来的「第 20 刻」行为）。 */
    public static final int SECOND_ATTACK_TICK = 20;
    /** 锁定了目标却一直没掠过的兜底：飞满这么多刻也升空，免得绕着目标打转。 */
    public static final int MAX_FLY_TICKS = 45;
    /** 升空持续 8 刻。 */
    public static final int ASCEND_TICKS = 8;
    /** 升空的竖直速度（设计稿的 motion Y = +1.5）。 */
    public static final double ASCEND_SPEED = 1.5;
    /** 二次袭击的冲刺速度：44 m/s = 2.2 格/刻（比直飞快将近一倍）。 */
    public static final double DASH_SPEED = 2.2;
    /** 追踪时每刻把方向往目标拧过去的比例，越大越「粘人」。 */
    protected static final double HOMING_RATE = 0.62;
    /** 预判目标位置时最多往前看多少刻（防止预判过头绕着人画圈）。 */
    protected static final double MAX_LEAD_TICKS = 24.0;
    /** 预判只用目标速度的八成，剩下两成交给追踪。 */
    protected static final double LEAD_FACTOR = 0.8;
    /** 最长存活刻数：10 秒后自动消散，免得残留的箭在世界上乱飞。 */
    protected static final int MAX_AGE = 200;

    /** 飞行阶段：直飞（带追踪）→ 升空 → 冲刺（不追踪）。 */
    protected enum Phase { FLY, ASCEND, DASH }

    protected Phase phase = Phase.FLY;
    /** 自己的年龄：原版 {@code tickCount} 从 1 开始且会被其它逻辑读到，这里单独留一份。 */
    protected int age;
    /** 死亡弹幕用：先直飞这么多刻再开始追踪（每刻一格，16 刻 ≈ 16 格）。 */
    protected int trackDelay;
    /** 升空剩余刻数。 */
    private int ascendTicks;
    /** 冲刺方向在升空结束那一刻锁定，之后不再改。 */
    private Vec3 dashDir;
    /** 已经触发过二次袭击（只触发一次）。 */
    private boolean secondAttackUsed;
    /** 这只箭会不会用「升空 → 冲刺」的二次袭击（分裂出来的小箭关掉，它们已经是第二波了）。 */
    private boolean secondAttackEnabled = true;
    /** 开始追踪之后飞了多少刻（用来判断「有没有真的掠过」）。 */
    private int homingTicks;
    /** 追踪开始后离目标最近的平方距离，用来判断是不是已经从目标身边飞过去了。 */
    private double closestDistSqr = Double.MAX_VALUE;
    /** 连续几刻在远离目标。 */
    private int awayTicks;

    /** 这只箭认定的目标。 */
    private UUID targetId;
    private transient LivingEntity aimedTarget;

    public SoulFireArrowEntity(EntityType<? extends SoulFireArrowEntity> type, Level level) {
        super(type, level);
        // 掉在地上也不会变成可拾取的箭：它是怪物召唤出来的能量箭。
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    // ---------------------------------------------------------------- 发射
    /**
     * 从 {@code owner} 手上射出一根魂火箭矢。
     *
     * @param direction   初始方向（不必是单位向量）
     * @param speed       速度，单位格/刻
     * @param target      这只箭认定的攻击目标（可以为 null，表示谁挡路打谁）
     * @param trackDelay  先直飞多少刻再开始追踪（死亡弹幕用 16，普通射击用 0）
     */
    public static SoulFireArrowEntity shoot(ServerLevel level, LivingEntity owner, Vec3 origin,
                                            Vec3 direction, double speed, LivingEntity target, int trackDelay) {
        SoulFireArrowEntity arrow = GeneratedMod.SOUL_FIRE_ARROW.get().create(level);
        if (arrow == null) return null;
        arrow.setPos(origin.x, origin.y, origin.z);
        arrow.launch(owner, direction, speed, target, trackDelay);
        level.addFreshEntity(arrow);
        return arrow;
    }

    /** 设置归属、初速、目标；不负责加进世界，子类分裂时先摆好位置再走这里。 */
    public void launch(LivingEntity owner, Vec3 direction, double speed, LivingEntity target, int trackDelay) {
        if (owner != null) setOwner(owner);
        Vec3 dir = direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        setDeltaMovement(dir.scale(speed));
        applyRotation(dir);
        this.targetId = target == null ? null : target.getUUID();
        this.aimedTarget = target;
        this.trackDelay = Math.max(0, trackDelay);
        this.phase = Phase.FLY;
        this.age = 0;
        this.secondAttackUsed = false;
        this.homingTicks = 0;
        this.closestDistSqr = Double.MAX_VALUE;
        this.awayTicks = 0;
    }

    /** 关掉「升空 → 冲刺」的二次袭击（分裂出来的小箭用）。 */
    public void setSecondAttackEnabled(boolean enabled) {
        this.secondAttackEnabled = enabled;
    }

    /** 用速度方向写 yaw/pitch（和原版箭一样：xRot 向上为正，yRot = atan2(dx, dz)）。 */
    protected void applyRotation(Vec3 dir) {
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        setYRot((float) Math.toDegrees(Math.atan2(dir.x, dir.z)));
        setXRot((float) (Math.toDegrees(Math.atan2(dir.y, horizontal))));
    }

    /**
     * 被斗气完美格挡弹回来：掉头飞回去，归属换成弹反的玩家。
     *
     * <p>魂火箭矢和原版箭不一样，它自带<b>锁定目标</b>与「升空 → 冲刺」二次袭击：
     * 光把速度取反的话，它下一秒就会顺着 {@code target()} 拐回玩家身上，
     * 于是「完美格挡」变成了自杀。所以这里把锁定目标、二次袭击、追踪计时一起清掉，
     * 并把归属换成玩家 —— 这样它只会沿原路飞回去，撞上谁算谁的（打中原来的射手也不为过）。</p>
     */
    public void reflectBy(LivingEntity parrier) {
        setOwner(parrier);
        // setAimedTarget(null) 会把 UUID 与缓存的目标一起清掉。
        setAimedTarget(null);
        this.secondAttackEnabled = false;
        this.secondAttackUsed = false;
        this.phase = Phase.FLY;
        this.age = 0;
        this.trackDelay = 0;
        this.homingTicks = 0;
        this.closestDistSqr = Double.MAX_VALUE;
        this.awayTicks = 0;
        Vec3 motion = getDeltaMovement();
        Vec3 back = motion.lengthSqr() < 1.0E-4 ? getLookAngle().scale(-1.0) : motion.scale(-1.0);
        setDeltaMovement(back);
        applyRotation(back.normalize());
        onReflected(back);
        setPos(position().add(back.normalize().scale(0.5)));
        hasImpulse = true;
    }

    /** 子类在反弹之后补自己的账（巨型箭要把「已飞里程」清零，否则下一帧就裂开）。 */
    protected void onReflected(Vec3 back) { }

    // ---------------------------------------------------------------- 目标
    /** 这只箭认定的目标；已经死掉或换维度就返回 null。 */
    public LivingEntity target() {
        if (aimedTarget != null && aimedTarget.isAlive() && aimedTarget.level() == level()) return aimedTarget;
        aimedTarget = null;
        if (targetId != null && level() instanceof ServerLevel serverLevel) {
            Entity found = serverLevel.getEntity(targetId);
            if (found instanceof LivingEntity living && living.isAlive()) aimedTarget = living;
        }
        if (aimedTarget == null) {
            LivingEntity ownerTarget = desiredTargetOf(shooter());
            if (ownerTarget != null && ownerTarget.isAlive()) aimedTarget = ownerTarget;
        }
        return aimedTarget;
    }

    protected LivingEntity shooter() {
        return getOwner() instanceof LivingEntity living ? living : null;
    }

    /** 发射者当前锁定的攻击目标（只有 {@link net.minecraft.world.entity.Mob} 才有目标）。 */
    protected static LivingEntity desiredTargetOf(LivingEntity owner) {
        return owner instanceof net.minecraft.world.entity.Mob mob ? mob.getTarget() : null;
    }

    public void setAimedTarget(LivingEntity target) {
        this.aimedTarget = target;
        this.targetId = target == null ? null : target.getUUID();
    }

    // ---------------------------------------------------------------- 主循环
    @Override
    public void tick() {
        if (level().isClientSide) {
            clientTrail();
            super.tick();
            return;
        }
        age++;
        if (age > MAX_AGE || !level().hasChunkAt(blockPosition())) {
            impact();
            discard();
            return;
        }
        flightStep();
        super.tick();
    }

    /** 每刻的弹道：直飞（带追踪）/ 升空 / 冲刺。 */
    protected void flightStep() {
        switch (phase) {
            case FLY -> {
                if (trackDelay > 0) {
                    // 直飞段（死亡弹幕、巨型箭分裂出来的四散）：保持出手方向，不追踪也不变招。
                    trackDelay--;
                    if (trackDelay == 0) steer(SPEED);
                    return;
                }
                steer(SPEED);
                homingTicks++;
                if (canBeginSecondAttack()) beginAscend();
            }
            case ASCEND -> {
                setDeltaMovement(0.0, ASCEND_SPEED, 0.0);
                if (--ascendTicks <= 0) beginDash();
            }
            case DASH -> {
                if (dashDir != null) setDeltaMovement(dashDir.scale(DASH_SPEED));
            }
        }
    }

    /**
     * 现在该不该「升空」变招。
     *
     * <p>原来只要飞满 20 刻就升空，于是目标在 20 格开外时，箭离人还有一大截就先上天再折返，
     * 看着像「没碰着人就开始表演」。现在的判断是：</p>
     * <ol>
     *   <li>刚出手的 {@link #MIN_FLY_TICKS} 刻内一律不升空；</li>
     *   <li>锁定了目标：必须<b>真的掠过</b>（飞到目标身后，或连续几刻越飞越远）才升空；</li>
     *   <li>没锁定目标：飞满 {@link #SECOND_ATTACK_TICK} 刻照旧升空；</li>
     *   <li>一直绕不过去（比如目标贴着墙来回挪）的兜底：{@link #MAX_FLY_TICKS} 刻后升空。</li>
     * </ol>
     */
    protected boolean canBeginSecondAttack() {
        if (secondAttackUsed || !secondAttackEnabled) return false;
        if (age < MIN_FLY_TICKS || homingTicks < 2) return false;
        LivingEntity target = target();
        if (target == null) return age >= SECOND_ATTACK_TICK;
        if (hasPassedTarget(target)) return true;
        return age >= MAX_FLY_TICKS;
    }

    /** 这只箭有没有从目标身边（或身后）飞过去。 */
    private boolean hasPassedTarget(LivingEntity target) {
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(position());
        double distSqr = toTarget.lengthSqr();
        Vec3 motion = getDeltaMovement();
        // 目标已经在身后：速度和「指向目标」的点积为负。
        boolean behind = motion.lengthSqr() > 1.0E-6 && motion.dot(toTarget) < 0.0;
        // 越飞越远（掠过时擦着边过去的）连续三刻就算过去了。
        boolean farther = distSqr > closestDistSqr;
        closestDistSqr = Math.min(closestDistSqr, distSqr);
        awayTicks = farther ? awayTicks + 1 : 0;
        return behind || awayTicks >= 3;
    }

    /**
     * 追踪：把当前速度方向往目标（预判后的位置）拧一点，再恢复成固定速度。
     * 没有目标就只是保持速度（不会自己减速）。
     */
    protected void steer(double speed) {
        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6) motion = new Vec3(0.0, 0.0, 1.0);
        motion = motion.normalize().scale(speed);
        LivingEntity target = target();
        if (target == null) {
            setDeltaMovement(motion);
            return;
        }
        Vec3 desired = predictedAimPoint(target).subtract(position());
        if (desired.lengthSqr() < 1.0E-6) {
            setDeltaMovement(motion);
            return;
        }
        desired = desired.normalize().scale(speed);
        Vec3 next = motion.lerp(desired, HOMING_RATE);
        if (next.lengthSqr() < 1.0E-6) next = desired;
        setDeltaMovement(next.normalize().scale(speed));
        applyRotation(getDeltaMovement());
    }

    /**
     * 目标的预判位置：按它现在的速度乘上「箭飞过去还要多久」，先往它要去的方向拐。
     *
     * <p>没有这一层，箭只会咬着目标的当前位置，横向跑动的玩家永远差半个身位；
     * 有了它，30 格外的移动目标也能真的打中。</p>
     */
    protected Vec3 predictedAimPoint(LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 velocity = target.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-6) return center;
        double eta = Math.min(position().distanceTo(center) / SPEED, MAX_LEAD_TICKS);
        return center.add(velocity.scale(eta * LEAD_FACTOR));
    }

    /** 升空：竖直上冲 10 刻，给玩家一个「它要变招了」的信号。 */
    protected void beginAscend() {
        secondAttackUsed = true;
        phase = Phase.ASCEND;
        ascendTicks = ASCEND_TICKS;
        setDeltaMovement(0.0, ASCEND_SPEED, 0.0);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.02);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 1.35F);
        }
    }

    /** 冲刺：按升空结束那一刻锁定的方向以 32 m/s 直冲，这一段不再跟踪。 */
    protected void beginDash() {
        phase = Phase.DASH;
        LivingEntity target = target();
        Vec3 dir = target == null ? null : target.getBoundingBox().getCenter().subtract(position());
        if (dir == null || dir.lengthSqr() < 1.0E-6) {
            Vec3 motion = getDeltaMovement();
            dir = motion.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : motion;
        }
        dashDir = dir.normalize();
        setDeltaMovement(dashDir.scale(DASH_SPEED));
        applyRotation(dashDir);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    getX(), getY(), getZ(), 10, 0.15, 0.15, 0.15, 0.01);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 0.65F, 1.7F);
        }
    }

    // ---------------------------------------------------------------- 命中
    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Entity hit = result.getEntity();
        // 打到自己（发射者）不算：直接穿过去。
        if (hit == getOwner()) return;
        if (!(hit instanceof LivingEntity victim)) return;
        // 误伤逻辑：不是这只箭的目标就不结算伤害，继续飞。
        if (!isIntendedTarget(victim)) return;

        DamageSource source = level().damageSources().arrow(this, getOwner());
        if (victim.isDamageSourceBlocked(source)) {
            if (!piercesShield()) {
                blockedByShield(victim);
                return;
            }
            pierceShield(victim);
        }
        onHitTarget(victim);
        impact();
        discard();
    }

    /** 撞到的是不是「发射者的攻击目标」：目标死了/没指定时就按谁挡路打谁处理。 */
    protected boolean isIntendedTarget(LivingEntity victim) {
        if (victim == null || victim == getOwner()) return false;
        LivingEntity target = target();
        if (target == null) return true;
        if (victim == target) return true;
        // 发射者在飞行途中换了目标：以它当前的目标为准，也算「是目标」。
        return desiredTargetOf(shooter()) == victim;
    }

    /** 有没有穿盾的本事：普通魂火箭矢没有，巨型魂火箭矢重写成 true。 */
    protected boolean piercesShield() {
        return false;
    }

    /** 伤害结算：物理、奥术、火焰各算一段，最后补一点燃烧。 */
    protected void onHitTarget(LivingEntity victim) {
        LivingEntity owner = shooter();
        victim.invulnerableTime = 0;
        victim.hurt(level().damageSources().arrow(this, owner), PHYSICAL_DAMAGE);
        if (owner != null) {
            SpellDamage.hitResolvedElement(level(), owner, victim, MagicEquipment.ARCANE, ARCANE_DAMAGE);
            SpellDamage.hitResolvedElement(level(), owner, victim, ElementType.FIRE.id(), FIRE_DAMAGE);
        } else {
            // 没有发射者（理论上不会发生）：直接按魔法伤害落地，别让整发箭哑火。
            victim.invulnerableTime = 0;
            victim.hurt(level().damageSources().magic(), ARCANE_DAMAGE + FIRE_DAMAGE);
        }
        // 火焰伤害之外再补 3 秒真实着火（免疫火焰的生物会自己忽略）。
        victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 60));
    }

    /** 被盾牌挡下：箭碎、盾牌掉 1 点耐久、放一声格挡音。 */
    protected void blockedByShield(LivingEntity victim) {
        if (level() instanceof ServerLevel serverLevel) {
            ItemStack shield = victim.getUseItem();
            if (!shield.isEmpty() && shield.isDamageableItem()) {
                EquipmentSlot slot = victim.getUsedItemHand() == InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                shield.hurtAndBreak(1, victim, slot);
            }
            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.2F);
            serverLevel.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.18);
        }
        discard();
    }

    /** 穿盾：把目标举着的盾强行打落（这是巨型魂火箭矢的特权）。 */
    protected void pierceShield(LivingEntity victim) {
        if (victim.isUsingItem()) victim.stopUsingItem();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1.1F, 0.85F);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide) {
            impact();
            discard();
            return;
        }
        super.onHitBlock(result);
    }

    /** 消散表现：紫焰与魂火在命中点炸开，再补一声魂火熄灭的闷响。 */
    protected void impact() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(),
                16, 0.25, 0.25, 0.25, 0.03);
        serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY(), getZ(),
                12, 0.25, 0.25, 0.25, 0.02);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                GeneratedMod.SOUL_FIRE_ARROW_HIT.get(), SoundSource.HOSTILE, 0.9F, 1.15F);
    }

    /** 客户端拖尾：紫白魂火一路飘，巨型箭矢在子类里加量。 */
    protected void clientTrail() {
        if (tickCount % 2 != 0) return;
        level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                getX() + random.triangle(0.0D, 0.06D),
                getY() + random.triangle(0.0D, 0.06D),
                getZ() + random.triangle(0.0D, 0.06D),
                0.0D, 0.0D, 0.0D);
        if (random.nextFloat() < 0.4F) {
            level().addParticle(ParticleTypes.SOUL,
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 存档
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SoulPhase", phase.name());
        tag.putInt("SoulAge", age);
        tag.putInt("SoulTrackDelay", trackDelay);
        tag.putInt("SoulAscendTicks", ascendTicks);
        tag.putBoolean("SoulSecondAttack", secondAttackUsed);
        tag.putBoolean("SoulSecondAttackEnabled", secondAttackEnabled);
        tag.putInt("SoulHomingTicks", homingTicks);
        if (dashDir != null) {
            tag.putDouble("SoulDashX", dashDir.x);
            tag.putDouble("SoulDashY", dashDir.y);
            tag.putDouble("SoulDashZ", dashDir.z);
        }
        if (targetId != null) tag.putUUID("SoulTarget", targetId);
        writeExtraData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            phase = Phase.valueOf(tag.getString("SoulPhase"));
        } catch (IllegalArgumentException ignored) {
            phase = Phase.FLY;
        }
        age = tag.getInt("SoulAge");
        trackDelay = tag.getInt("SoulTrackDelay");
        ascendTicks = tag.getInt("SoulAscendTicks");
        secondAttackUsed = tag.getBoolean("SoulSecondAttack");
        secondAttackEnabled = !tag.contains("SoulSecondAttackEnabled") || tag.getBoolean("SoulSecondAttackEnabled");
        homingTicks = tag.getInt("SoulHomingTicks");
        closestDistSqr = Double.MAX_VALUE;
        awayTicks = 0;
        if (tag.contains("SoulDashX")) {
            dashDir = new Vec3(tag.getDouble("SoulDashX"), tag.getDouble("SoulDashY"), tag.getDouble("SoulDashZ"));
        }
        targetId = tag.hasUUID("SoulTarget") ? tag.getUUID("SoulTarget") : null;
        aimedTarget = null;
        readExtraData(tag);
    }

    /** 子类追加自己的存档字段。 */
    protected void writeExtraData(CompoundTag tag) {
    }

    /** 子类读回自己的存档字段。 */
    protected void readExtraData(CompoundTag tag) {
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    /** 无重力：飞行方向全部由我们自己拧。 */
    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    /**
     * 误伤穿透：不是这只箭认定的目标，就**从碰撞检测层面**排除掉。
     *
     * <p>只重写 {@link #onHitEntity} 是不够的 —— 原版 {@code AbstractArrow.tick()} 一旦
     * 检测到「碰到了实体」，且穿透等级为 0，就会结束这一帧的移动；箭会停在半路变成一根插在
     * 别人身上的死箭，而不是穿过去。所以这里在 {@code ProjectileUtil} 找碰撞目标时就把它筛掉，
     * 箭从物理上根本「看不见」非目标生物。</p>
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (target == getOwner()) return false;
        if (target instanceof LivingEntity living && !isIntendedTarget(living)) return false;
        return super.canHitEntity(target);
    }
}
