package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 巨型魂火箭矢：魔蚀骸骨那个 4 秒蓄力大招射出去的东西。
 *
 * <p>它是 {@link SoulFireArrowEntity} 的加强版，区别只有三条：</p>
 * <ul>
 *   <li><b>伤害</b>：16 点奥术 + 16 点火焰（没有物理段）；</li>
 *   <li><b>裂开</b>：一边飞一边记里程，满 8 格就自毁并裂成 4 根魂火箭矢 —— 四根先朝四个斜上方
 *       弹开四散（{@link #SCATTER_TICKS} 刻），再掉头追着目标咬，所以离得越远越难躲；
 *       8 格以内直接命中则挂 160 刻奥术沉默与 160 刻凋零；</li>
 *   <li><b>格挡</b>：盾牌挡不住（{@link #piercesShield()} 为 true，命中前先把盾打落），
 *       但斗气招架照旧能弹——弹开时按「完美格挡」的动画结算，见 {@link BattleQiParry}。</li>
 * </ul>
 */
public final class GiantSoulFireArrowEntity extends SoulFireArrowEntity {
    /** 16 点奥术。 */
    public static final float GIANT_ARCANE_DAMAGE = 16.0F;
    /** 16 点火焰。 */
    public static final float GIANT_FIRE_DAMAGE = 16.0F;
    /** 飞满 8 格就裂成 4 根小箭。 */
    public static final double SPLIT_DISTANCE = 8.0D;
    /** 裂开时生成的箭数。 */
    public static final int SPLIT_COUNT = 4;
    /** 分裂出来的小箭先四散这么多刻，再掉头追目标（9 刻 ≈ 0.45 秒）。 */
    public static final int SCATTER_TICKS = 9;
    /** 四散时的速度（比正常追踪慢一点，才看得出「先炸开、再扑上来」）。 */
    public static final double SCATTER_SPEED = SPEED * 0.85D;
    /** 命中时挂的减益时长：160 刻 = 8 秒。 */
    public static final int DEBUFF_TICKS = 160;

    /** 已经飞过的距离（格）。 */
    private double travelled;

    public GiantSoulFireArrowEntity(EntityType<? extends GiantSoulFireArrowEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            clientTrail();
            super.tick();
            return;
        }
        if (age > 0) travelled += getDeltaMovement().length();
        if (travelled >= SPLIT_DISTANCE) {
            split();
            return;
        }
        super.tick();
    }

    /** 里程满 8 格：本箭消散，裂成 4 根追踪目标的小箭。 */
    private void split() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            discard();
            return;
        }
        LivingEntity target = target();
        LivingEntity owner = shooter();
        Vec3 origin = position();
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z,
                26, 0.4, 0.4, 0.4, 0.05);
        serverLevel.sendParticles(ParticleTypes.SOUL, origin.x, origin.y, origin.z,
                18, 0.35, 0.35, 0.35, 0.03);
        serverLevel.playSound(null, origin.x, origin.y, origin.z,
                GeneratedMod.SOUL_FIRE_ARROW_HIT.get(), SoundSource.HOSTILE, 1.2F, 0.85F);
        for (int i = 0; i < SPLIT_COUNT; i++) {
            double angle = Math.PI * 2.0D * i / SPLIT_COUNT;
            Vec3 offset = new Vec3(Math.cos(angle) * 0.45D, 0.15D, Math.sin(angle) * 0.45D);
            // 先四散：朝各自的方向斜着弹开，SCATTER_TICKS 刻之后才掉头扑向目标。
            Vec3 scatter = new Vec3(Math.cos(angle), 0.45D, Math.sin(angle)).normalize();
            SoulFireArrowEntity arrow = SoulFireArrowEntity.shoot(serverLevel, owner,
                    origin.add(offset), scatter, SCATTER_SPEED, target, SCATTER_TICKS);
            if (arrow != null) {
                arrow.setAimedTarget(target);
                // 四根小箭本身就是第二波了，不再各自升空冲刺，不然满屏乱窜看不出层次。
                arrow.setSecondAttackEnabled(false);
            }
        }
        discard();
    }

    @Override
    protected boolean piercesShield() {
        return true;
    }

    /**
     * 被完美格挡弹回来：除了掉头，还要把「已飞里程」清零。
     *
     * <p>巨型箭飞满 {@link #SPLIT_DISTANCE} 格就会裂成四根小箭；那颗弹体飞到你面前时里程早就满了，
     * 不清零的话它在掉头的下一帧就当场裂开，四根小箭继续扑向玩家 —— 那就不是「弹回去」了。</p>
     */
    @Override
    protected void onReflected(Vec3 back) {
        this.travelled = 0;
    }

    @Override
    protected void onHitTarget(LivingEntity victim) {
        LivingEntity owner = shooter();
        victim.invulnerableTime = 0;
        if (owner != null) {
            SpellDamage.hitResolvedElement(level(), owner, victim, MagicEquipment.ARCANE, GIANT_ARCANE_DAMAGE);
            SpellDamage.hitResolvedElement(level(), owner, victim, ElementType.FIRE.id(), GIANT_FIRE_DAMAGE);
        } else {
            victim.hurt(level().damageSources().magic(), GIANT_ARCANE_DAMAGE + GIANT_FIRE_DAMAGE);
        }
        victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 100));
        // 奥术沉默（本模组「乱魔」那套禁施法状态）：160 刻内放不出任何法术；再叠一层凋零（160 刻）。
        victim.addEffect(new MobEffectInstance(NewSpellContent.ARCANE_SILENCE,
                DEBUFF_TICKS, 0, false, true, true));
        victim.addEffect(new MobEffectInstance(MobEffects.WITHER, DEBUFF_TICKS, 0, false, true, true));
    }

    /** 巨型箭的拖尾更粗、更亮，远远就看得出「这一发不一样」。 */
    @Override
    protected void clientTrail() {
        for (int i = 0; i < 3; i++) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX() + random.triangle(0.0D, 0.14D),
                    getY() + random.triangle(0.0D, 0.14D),
                    getZ() + random.triangle(0.0D, 0.14D),
                    0.0D, 0.0D, 0.0D);
        }
        level().addParticle(ParticleTypes.SOUL, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void writeExtraData(CompoundTag tag) {
        tag.putDouble("GiantTravelled", travelled);
    }

    @Override
    protected void readExtraData(CompoundTag tag) {
        travelled = tag.getDouble("GiantTravelled");
    }
}
