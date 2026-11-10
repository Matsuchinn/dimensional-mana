package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 秽蚀蜂：紫黑色的变异蜜蜂，住蚀魔蜂巢，采四种魔法植物的花蜜。
 *
 * <p>继承原版蜜蜂，采蜜、回巢、夜间入巢、被烟安抚这些生态行为全部白拿。
 * 改动集中在攻击这一头：</p>
 * <ul>
 *   <li><b>普通攻击</b>：每下 2 点物理伤害（走原版护甲结算）。</li>
 *   <li><b>魔晶毒针</b>：物理之外再补 2 点奥术伤害和 1 层魔蚀（10 秒），
 *       毒针有 10 秒冷却——同一只蜂每次蛰击只叠一层，不能一口气叠上去。</li>
 *   <li><b>蛰后不死</b>：原版蜜蜂蛰完把肠子留在人身上、半分钟后死掉；
 *       秽蚀蜂的毒针是魔晶凝的，蛰完完好无损。实现上很简单：
 *       完全重写 {@link #doHurtTarget(Entity)}、不碰原版那条 {@code hasStung} 标记，
 *       原版「蛰后倒计时死亡」的机制就永远不会启动。</li>
 * </ul>
 *
 * <p>喂食与繁殖：拿任何一种花（含暮刺荆棘 / 血纹花 / 瑞斯特利亚 / 卢梅莉亚，
 * 见 {@code #minecraft:bee_food} 标签）右键它——正在记仇的会当场消气，
 * 两只都吃饱了就会繁殖，幼蜂长大约 20 分钟。</p>
 */
public class CorruptedBeeEntity extends Bee {
    /** 魔晶毒针冷却：10 秒。冷却好了下一口才带奥术伤害和魔蚀。 */
    public static final int STING_COOLDOWN_TICKS = 200;
    /** 魔蚀挂 10 秒（一层）。 */
    private static final int EROSION_TICKS = 200;
    /** 物理 / 奥术两段各打这么多。 */
    private static final float PHYSICAL_DAMAGE = 2.0F;
    private static final float ARCANE_DAMAGE = 2.0F;
    /** 同伴记仇半径（格）：一窝蜂一般就在这个范围里。 */
    private static final double ANGER_RADIUS = 16.0D;

    private int stingCooldown;

    public CorruptedBeeEntity(EntityType<? extends CorruptedBeeEntity> type, Level level) {
        super(type, level);
    }

    /** 20 点生命（原版蜜蜂的两倍），飞行速度与原版蜜蜂一致（FLY_SPEED 0.3 不动）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return Bee.createAttributes().add(Attributes.MAX_HEALTH, 20.0D);
    }

    /**
     * 在原版一整套蜂类目标之外追加「找新家」兜底（r91）：原版那条路只查 POI 表，
     * 玩家放的巢 / 箱在 POI 缺记录时蜂永远住不进去；这里按方块标签直接扫，
     * 找到就写 hivePos，剩下的交给原版回巢目标。
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(5, new CorruptedBeeLocateHiveGoal(this));
    }

    public int stingCooldown() {
        return stingCooldown;
    }

    // ---------------------------------------------------------------- 攻击
    /**
     * 一口两段：先咬（物理 2），毒针上好弦的话再扎（奥术 2 + 一层魔蚀）。
     *
     * <p>不调用 {@code super.doHurtTarget}：原版那套会把 {@code hasStung} 立起来、
     * 给目标挂普通毒素、还顺手给自己上 30 秒死亡倒计时——全都不是我们要的。</p>
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (level().isClientSide || !(target instanceof LivingEntity living)) return false;
        living.hurt(damageSources().mobAttack(this), PHYSICAL_DAMAGE);
        if (level() instanceof ServerLevel server) {
            if (stingCooldown <= 0) {
                stingCooldown = STING_COOLDOWN_TICKS;
                SpellDamage.hitElement(server, this, living, MagicEquipment.ARCANE, ARCANE_DAMAGE);
                // apply 一次正好记一层，防「连蛰叠层」。
                MagicErosionEffect.apply(living, EROSION_TICKS);
                BlockPos at = living.blockPosition();
                server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        living.getX(), living.getY() + living.getBbHeight() * 0.55, living.getZ(),
                        8, 0.22D, 0.26D, 0.22D, 0.015D);
                server.sendParticles(ParticleTypes.WITCH,
                        at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5,
                        6, 0.3D, 0.3D, 0.3D, 0.02D);
                server.playSound(null, getX(), getY(), getZ(),
                        GeneratedMod.CORRUPTED_BEE_HUM.get(), SoundSource.HOSTILE, 0.3F, 1.7F);
            }
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && stingCooldown > 0) stingCooldown--;
    }

    // ---------------------------------------------------------------- 同伴仇恨
    /**
     * 被打就喊人（r92）：原版蜜蜂的「同族记仇」挂在 {@code HurtByTargetGoal} 上，
     * 那只蜂得<b>活着</b>跑完这一轮目标选择才会去 alert 同伴——所以一击打死时，
     * 死的那只已经没有下一刻了，旁边的蜂根本不知道发生了什么（用户报的「击杀后
     * 同伴不管，打不死才记仇」就是这条）。
     *
     * <p>这里不看死没死，只要这一下真的伤到了，就在受伤的第一时间把周围 16 格内的
     * 秽蚀蜂全部拉进仇恨：写持久仇恨目标 + 重新起计时 + 直接挂上目标。</p>
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker
                && attacker != this && attacker.isAlive()) {
            angerKin(attacker);
        }
        return hurt;
    }

    /** 把周围同族拉进对 {@code attacker} 的仇恨（已经锁着别人的不抢目标，只补仇恨）。 */
    private void angerKin(LivingEntity attacker) {
        if (!(level() instanceof ServerLevel server)) return;
        for (CorruptedBeeEntity kin : server.getEntitiesOfClass(CorruptedBeeEntity.class,
                getBoundingBox().inflate(ANGER_RADIUS),
                bee -> bee != this && bee.isAlive() && !bee.isBaby())) {
            kin.setPersistentAngerTarget(attacker.getUUID());
            kin.startPersistentAngerTimer();
            if (kin.getTarget() == null) kin.setTarget(attacker);
        }
    }

    // ---------------------------------------------------------------- 存档
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("StingCooldown", stingCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        stingCooldown = tag.getInt("StingCooldown");
    }

    // ---------------------------------------------------------------- 喂食
    /**
     * 拿花右键：记仇的当场消气（清目标 + 清持久仇恨），剩下的交给原版——
     * 成年蜂进情期可繁殖，幼蜂喂花加速长大。
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && isFlower(player.getItemInHand(hand))
                && (isAngry() || getTarget() != null)) {
            setTarget(null);
            setPersistentAngerTarget(null);
            setRemainingPersistentAngerTime(0);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        getX(), getY() + 0.6D, getZ(), 6, 0.25D, 0.3D, 0.25D, 0.0D);
            }
        }
        return super.mobInteract(player, hand);
    }

    /** 花判定：原版花物品标签 + 我们四种植物的方块形态（BlockItem 在群系里直接丢方块也认）。 */
    private static boolean isFlower(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ItemTags.FLOWERS) || stack.is(ItemTags.BEE_FOOD)) return true;
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().is(BlockTags.FLOWERS);
    }

    /** 后代必须是秽蚀蜂，不能退化成原版蜜蜂（原版方法写死了 EntityType.BEE）。 */
    @Override
    public CorruptedBeeEntity getBreedOffspring(ServerLevel level, AgeableMob other) {
        CorruptedBeeEntity child = GeneratedMod.CORRUPTED_BEE.get().create(level);
        if (child != null) {
            child.setBaby(true);
            if (hasHive()) child.setHivePos(getHivePos());
        }
        return child;
    }

    // ---------------------------------------------------------------- 音效
    /** 嗡鸣是客户端常驻循环（见 {@link CorruptedBeeHumInstance}），这里掐掉原版的间隔式环境音避免叠两路。 */
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GeneratedMod.CORRUPTED_BEE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GeneratedMod.CORRUPTED_BEE_DEATH.get();
    }

    /** r91：玩家嫌吵，受伤 / 死亡音整体压低一档（嗡鸣本身的响度在 HumInstance 里）。 */
    @Override
    protected float getSoundVolume() {
        return 0.55F;
    }
}
