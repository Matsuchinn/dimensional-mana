package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 元素史莱姆发射的「魔法子弹」：一颗看得见、飞得慢、能被打掉的法术弹体。
 *
 * <p>以前元素史莱姆的初级法术是<b>瞬间命中</b>：抬手那一刻伤害已经结算完了，画面上只闪一下，
 * 玩家既看不出它在施法，也没有任何反应时间，所谓「格挡」全靠距离判定施舍。现在改成真正飞行的
 * 弹体：施法者先蓄力约半秒（身上冒对应颜色的粒子 + 释放音效），然后弹体沿直线飞向目标，
 * <b>速度只有玩家投射法术的一半</b>（玩家弹道 2.0 格/刻，这里 1.0 格/刻），十六格要飞一秒半，
 * 足够看清、走位，或者举招架姿态把这一发弹掉。</p>
 *
 * <p>「荆棘」「落石」这类轨迹本来完全看不见的法术，弹体改成<b>贴地飞行</b>：沿路从地面冒出
 * 藤蔓 / 碎石的粒子，像有什么东西从地里朝你蔓延过来，命中前一秒玩家就能看出它从哪来、
 * 什么时候会到。</p>
 *
 * <p>所有坐标判断都用 {@code ChunkSafety} 的思路：区块没加载就自我销毁，绝不为了一颗弹体
 * 让游戏去同步加载地形（详见 {@link ChunkSafety} 的死锁说明）。</p>
 */
public final class SlimeSpellBolt extends Projectile {
    private static final EntityDataAccessor<String> VARIANT =
            SynchedEntityData.defineId(SlimeSpellBolt.class, EntityDataSerializers.STRING);

    /**
     * 飞行速度：玩家投射法术的一半。
     *
     * <p>玩家那边的基础投射是 {@link SpellRange#BASIC_SPEED} = 2.0 格/刻；这里取一半，
     * 十六格约飞 1.6 秒（80 刻/秒的一半左右），看得清、躲得开。</p>
     */
    public static final double SPEED = SpellRange.BASIC_SPEED * 0.5;
    /** 贴地蔓延类（荆棘、落石）再慢一点：地面上的东西本来就该「爬」过去。 */
    public static final double GROUND_SPEED = 0.7;
    /** 飞太久（目标一直跑、或者绕开了）就自己散掉，免得变成天上的流弹。 */
    private static final int MAX_AGE = 100;
    /** 命中判定半径。 */
    private static final double HIT_RADIUS = 0.45;

    private UUID casterId;
    /** 被完美格挡弹回来之后，这只弹体归谁（打中怪物算谁的击杀）。普通飞行时为 null。 */
    private UUID reflectorId;
    private float damage;
    private String damageElement = "arcane";
    private boolean arcane;
    private double range = 24.0;
    private double traveled;
    private Vec3 direction = Vec3.ZERO;

    public SlimeSpellBolt(EntityType<? extends SlimeSpellBolt> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VARIANT, ElementalSlimeVariant.MANA.id());
    }

    /** 这只弹体属于哪种元素（决定颜色、粒子与伤害元素）。 */
    public ElementalSlimeVariant variant() {
        return ElementalSlimeVariant.byId(entityData.get(VARIANT));
    }

    /** 伤害属性名（弹反碎裂特效按它配色）。 */
    public String damageElement() {
        return damageElement;
    }

    /** 贴地飞行：荆棘、落石这类「从地里过来」的法术。 */
    public boolean groundSpell() {
        return groundKind(variant().basicSpell());
    }

    private static boolean groundKind(SpellDefinition spell) {
        return spell == SpellDefinition.THORNS || spell == SpellDefinition.ROCKFALL;
    }

    /**
     * 发射。
     *
     * @param caster 施法的史莱姆
     * @param target 当时锁定的目标（弹体只朝那一刻的位置飞，目标跑开就打空）
     * @param damage 已经算好的基础伤害（施法者死亡后仍按这个数结算）
     */
    public void launch(ElementalSlime caster, LivingEntity target, float damage) {
        setOwner(caster);
        this.casterId = caster.getUUID();
        this.damage = damage;
        this.damageElement = caster.variant().damageElementId();
        this.arcane = caster.variant() == ElementalSlimeVariant.MANA;
        entityData.set(VARIANT, caster.variant().id());

        Vec3 from = launchOrigin(caster);
        Vec3 to = groundSpell() ? target.position().add(0, 0.35, 0) : target.getEyePosition();
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) dir = caster.getLookAngle();
        this.direction = dir.normalize();
        this.range = Math.max(4.0, Math.min(24.0, dir.length() + 4.0));
        setPos(from);
        setDeltaMovement(this.direction.scale(speed()));
        setYRot((float) Math.toDegrees(Math.atan2(-this.direction.x, this.direction.z)));
        setXRot((float) -Math.toDegrees(Math.asin(Math.clamp(this.direction.y, -1, 1))));
    }

    /** 贴地法术从脚边冒出来，其余的从眼睛高度出手。 */
    private Vec3 launchOrigin(ElementalSlime caster) {
        return groundSpell()
                ? caster.position().add(0, 0.25, 0)
                : caster.getEyePosition().add(this.direction.scale(0.4));
    }

    private double speed() {
        return groundSpell() ? GROUND_SPEED : SPEED;
    }

    /** 施法的史莱姆；已经被打死或卸载了就返回 null。 */
    public ElementalSlime caster() {
        if (!(level() instanceof ServerLevel serverLevel) || casterId == null) return null;
        Entity entity = serverLevel.getEntity(casterId);
        return entity instanceof ElementalSlime slime ? slime : null;
    }

    /** 把这只弹体完美格挡弹回来的玩家；没被弹回来过就返回 null。 */
    private ServerPlayer reflector() {
        if (reflectorId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getEntity(reflectorId) instanceof ServerPlayer player ? player : null;
    }

    /**
     * 被完美格挡弹回来：原地掉头，按原路径飞回去。
     *
     * <p>和玩家法术的反弹是同一套做法：速度取反、主人换成弹反的玩家、清掉原来的施法者，
     * 里程与存活时间重新开始算。掉头之后 {@link #firstHit} 会跳过「主人」，
     * 所以它不会再碰到弹反的人；原来那只史莱姆、以及路径上的怪物才会挨这一下。
     * 伤害、元素配色、拖尾全部照旧 —— 弹回去的仍是同一发法术。</p>
     */
    public void reflect(ServerPlayer parrier) {
        Vec3 back = getDeltaMovement().lengthSqr() < 1.0E-4
                ? getLookAngle().scale(-1.0)
                : getDeltaMovement().scale(-1.0);
        this.direction = back.normalize();
        setDeltaMovement(back);
        setOwner(parrier);
        this.reflectorId = parrier.getUUID();
        // 原来的施法者关系作废：弹体已经「换了主人」，不再是那只史莱姆的法术。
        this.casterId = null;
        this.traveled = 0;
        this.range = Math.max(this.range, 24.0);
        // 存活计时也归零：一颗飞了大半程才被弹回来的弹体不该刚掉头就散掉。
        this.tickCount = 0;
        setYRot((float) Math.toDegrees(Math.atan2(-this.direction.x, this.direction.z)));
        setXRot((float) -Math.toDegrees(Math.asin(Math.clamp(this.direction.y, -1, 1))));
        // 顺着反弹方向挪出玩家的碰撞箱，免得下一段轨迹从人身体里出发。
        setPos(position().add(this.direction.scale(0.5)));
        hasImpulse = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spawnClientTrail();
            return;
        }
        if (!(level() instanceof ServerLevel level)) return;
        // 区块卸载了就直接散掉：绝不为了一颗流弹让游戏去同步加载地形。
        if (!ChunkSafety.ready(level, blockPosition())) {
            discard();
            return;
        }
        if (tickCount > MAX_AGE) {
            burst(null);
            return;
        }
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        // 贴地法术（荆棘、落石）沿地面爬：每刻把高度压回地表，遇到土坡会爬上去，而不是一头钻进土里。
        if (groundSpell()) end = new Vec3(end.x, groundY(level, end) + 0.12, end.z);

        // 这一段要横跨到还没加载的区块时直接散掉：level.clip 会读方块，
        // 落在未加载区块上就会让主线程同步去生成地形（死锁那一条，见 ChunkSafety）。
        if (!ChunkSafety.readyColumn(level, Mth.floor(end.x), Mth.floor(end.z))) {
            burst(null);
            return;
        }

        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
        // 先看这一段路里最远能飞到哪：撞墙就只扫到墙面前，墙后的人不会被“隔墙命中”。
        Vec3 stop = hitBlock ? blockHit.getLocation() : end;

        LivingEntity victim = firstHit(level, start, stop);
        if (victim != null) {
            // 命中点取「弹体真正碰到碰撞箱」的位置，而不是目标身体中心：
            // 这样矮小的目标、贴地的法术也能正常算碰到。
            Vec3 at = hitPoint(start, stop, victim);
            setPos(at);
            impact(victim);
            return;
        }
        setPos(stop);
        traveled += start.distanceTo(stop);
        sendTrail(level, stop);
        if (hitBlock || traveled >= range) {
            burst(null);
        }
    }

    /** 弹体这一段轨迹真正扫到目标身上的那一点（拿不到就退回目标身体中心）。 */
    private Vec3 hitPoint(Vec3 start, Vec3 stop, LivingEntity victim) {
        return victim.getBoundingBox().inflate(HIT_RADIUS).clip(start, stop)
                .orElseGet(() -> victim.getBoundingBox().getCenter());
    }

    /**
     * 找出这一段路径上<b>最先真正碰到</b>的生物：跳过施法者自己、旁观者与已经死亡的。
     *
     * <p>r401 校正：原来是「扫到一个算一个」，同一段里站着好几个人时侯谁先被遍历到就谁挨打，
     * 而且被墙挡住的人也可能被算进来。现在用 {@code AABB#clip} 求弹道与每个碰撞箱的真实交点，
     * 取距离起点最近的那一个——离得更远、隔着一堵墙的都不会被碰到。</p>
     */
    private LivingEntity firstHit(ServerLevel level, Vec3 start, Vec3 end) {
        AABB sweep = new AABB(start, end).inflate(HIT_RADIUS);
        ElementalSlime caster = caster();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, sweep,
                entity -> entity.isAlive() && !entity.isSpectator())) {
            if (caster != null && candidate == caster) continue;
            if (getOwner() == candidate) continue;
            // 被完美格挡弹回来的弹体不碰任何玩家：只找怪物算账，免得联机时糊自己队友一脸。
            if (reflectorId != null && candidate instanceof Player) continue;
            if (caster != null && !caster.canAttack(candidate)) continue;
            Vec3 hit = candidate.getBoundingBox().inflate(HIT_RADIUS).clip(start, end).orElse(null);
            if (hit == null) continue;
            double distance = start.distanceToSqr(hit);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 命中结算：招架优先，其次才是元素反应与伤害。 */
    private void impact(LivingEntity victim) {
        ElementalSlime caster = caster();
        if (victim instanceof ServerPlayer player) {
            // 招架分三档：完美格挡把这颗弹体掉头送回去（不花斗气）、普通格挡把它打碎（扣斗气）、
            // 斗气不够就照常挨打。只有完美格挡会把弹体留下，所以这里要按结果分开处理。
            BattleQiParry.ProjectileParry parried =
                    BattleQiParry.parryIncomingSpell(player, this, caster, damageElement, damage, position());
            if (parried == BattleQiParry.ProjectileParry.REFLECTED) return;
            if (parried == BattleQiParry.ProjectileParry.NORMAL) {
                discard();
                return;
            }
        }
        float dealt = damage;
        if (caster != null) {
            if (arcane) {
                dealt = ElementReactionSystem.arcaneDamage(caster, victim, dealt);
                SpellDamage.hitResolvedElement(level(), caster, victim, MagicEquipment.ARCANE, dealt);
            } else {
                dealt = ElementReactionSystem.elementalDamage(caster, victim, caster.variant().element(), dealt);
                SpellDamage.hitResolvedElement(level(), caster, victim, damageElement, dealt);
            }
        } else if (reflector() != null) {
            // 被完美格挡弹回来的一发：算弹反玩家的攻击，打中怪物照旧是这份伤害。
            victim.hurt(level().damageSources().playerAttack(reflector()), Math.max(0.5F, dealt));
        } else {
            // 施法者已经被打死或卸载：弹体照旧飞完，伤害按普通魔法落地（没有元素反应加成）。
            victim.hurt(level().damageSources().magic(), Math.max(0.5F, dealt));
        }
        burst(victim);
    }

    /** 命中或散掉的收尾：一团对应颜色的碎屑，然后消失。 */
    private void burst(LivingEntity victim) {
        if (level() instanceof ServerLevel level) {
            ElementalSlimeVariant variant = variant();
            int color = variant.color();
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            Vec3 at = position();
            level.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), 1.2F),
                    at.x, at.y, at.z, 18, 0.3, 0.3, 0.3, 0.05);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.2);
            if (groundSpell()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                variant == ElementalSlimeVariant.EARTH
                                        ? Blocks.DIRT.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState()),
                        at.x, at.y, at.z, 14, 0.35, 0.15, 0.35, 0.05);
            }
        }
        discard();
    }

    /** 服务端每刻补一层拖尾：贴地法术贴地冒，其它法术沿弹道冒。 */
    private void sendTrail(ServerLevel level, Vec3 at) {
        ElementalSlimeVariant variant = variant();
        int color = variant.color();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        if (groundSpell()) {
            double y = groundY(level, at);
            worldParticle(level, new DustParticleOptions(new Vector3f(r, g, b), 1.0F), at.x, y + 0.1, at.z, 3, 0.18, 0.02, 0.18, 0.0);
            if (tickCount % 3 == 0) {
                // 荆棘从地里长出来 / 碎石从地上翻起来。
                if (variant == ElementalSlimeVariant.EARTH) {
                    worldParticle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                            at.x, y + 0.15, at.z, 4, 0.2, 0.05, 0.2, 0.05);
                } else {
                    worldParticle(level, ParticleTypes.SPORE_BLOSSOM_AIR, at.x, y + 0.3, at.z, 3, 0.2, 0.15, 0.2, 0.0);
                }
            }
            return;
        }
        worldParticle(level, new DustParticleOptions(new Vector3f(r, g, b), 0.9F), at.x, at.y, at.z, 2, 0.08, 0.08, 0.08, 0.0);
        if (tickCount % 2 == 0) worldParticle(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** 只有服务端有 sendParticles(ParticleOptions, ...) 这个「广播给附近玩家」的重载。 */
    private static void worldParticle(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle,
                                      double x, double y, double z, int count,
                                      double dx, double dy, double dz, double speed) {
        level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
    }

    /** 贴地弹体的地面高度：脚下实心方块的上表面；踩空就沿用弹体自己的高度。 */
    private double groundY(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos check = pos.below(dy);
            if (!ChunkSafety.ready(level, check)) return at.y;
            if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
                return check.getY() + level.getBlockState(check).getCollisionShape(level, check).max(
                        net.minecraft.core.Direction.Axis.Y);
            }
        }
        return at.y;
    }

    /** 客户端拖尾：颜色和这只史莱姆对得上，远处也能看出子弹在哪。 */
    private void spawnClientTrail() {
        ElementalSlimeVariant variant = variant();
        int color = variant.color();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        level().addParticle(new DustParticleOptions(new Vector3f(r, g, b), 1.1F),
                getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SlimeVariant", entityData.get(VARIANT));
        tag.putFloat("BoltDamage", damage);
        tag.putString("DamageElement", damageElement);
        tag.putBoolean("Arcane", arcane);
        tag.putDouble("Range", range);
        tag.putDouble("Traveled", traveled);
        if (casterId != null) tag.putUUID("CasterId", casterId);
        if (reflectorId != null) tag.putUUID("ReflectorId", reflectorId);
        tag.putDouble("DirX", direction.x);
        tag.putDouble("DirY", direction.y);
        tag.putDouble("DirZ", direction.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, ElementalSlimeVariant.byId(tag.getString("SlimeVariant")).id());
        damage = tag.getFloat("BoltDamage");
        damageElement = tag.contains("DamageElement") ? tag.getString("DamageElement") : "arcane";
        arcane = tag.getBoolean("Arcane");
        range = tag.contains("Range") ? tag.getDouble("Range") : 24.0;
        traveled = tag.getDouble("Traveled");
        if (tag.hasUUID("CasterId")) casterId = tag.getUUID("CasterId");
        if (tag.hasUUID("ReflectorId")) reflectorId = tag.getUUID("ReflectorId");
        direction = new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ"));
    }
}
