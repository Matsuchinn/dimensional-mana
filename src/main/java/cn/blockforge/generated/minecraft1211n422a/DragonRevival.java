package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 末影龙「拒绝死亡」：血条被打空时不再直接倒下，而是先飞回龙巢上空进入复活。
 *
 * <p>整条流程分两段，状态存在龙自己的持久化数据里（存档、退出重进都能接上）：</p>
 * <ol>
 *   <li><b>飞回龙巢</b>：致命伤被拦下，龙回血到「复活血量」、暂时无敌，并朝末地传送门（龙巢）上方飞。</li>
 *   <li><b>复活破绽</b>：到巢后只在这段时间里能被打，血量固定为复活血量。
 *       破绽第一次 1 秒，之后每复活过一次 +2 秒；复活血量第一次 100 点，之后每次少 15%。
 *       破绽里把血打空就是真死（传送门、龙蛋、掉落照常）；没打死就满血复活，且生命上限 +15%（可叠加）。</li>
 * </ol>
 *
 * <p>复活全程关闭末地水晶的回血：{@code heal()} 走治疗事件直接取消，水晶每 10 刻的
 * {@code setHealth(+1)} 则被破绽期每刻的「血量只许降不许涨」压回去，
 * 否则柱子上剩下的水晶会把破绽血量补回去，「复活期间固定 100 血」就名存实亡了。</p>
 */
public final class DragonRevival {
    /** 挂在龙身上的存档键，和别的系统分开，不会被玩家数据迁移误伤。 */
    private static final String TAG = "DimensionalManaDragon";

    private static final int STAGE_NONE = 0;
    private static final int STAGE_FLYING = 1;
    private static final int STAGE_WINDOW = 2;

    /** 第一次复活时的破绽血量。 */
    public static final double FIRST_REVIVAL_HEALTH = 100.0;
    /** 之后每复活一次，破绽血量乘这个系数（-15%）。 */
    public static final double REVIVAL_HEALTH_DECAY = 0.85;
    /** 第一次破绽 1 秒 = 20 刻。 */
    public static final int FIRST_WINDOW_TICKS = 20;
    /** 之后每复活一次，破绽多 2 秒 = 40 刻。 */
    public static final int WINDOW_TICKS_PER_REVIVAL = 40;
    /** 每次复活后生命上限乘这个系数（+15%）。 */
    public static final double MAX_HEALTH_GROWTH = 1.15;

    /** 飞回龙巢最多给 4 秒，防止龙离得太远时一直飞不到。 */
    private static final int MAX_FLIGHT_TICKS = 80;
    /** 停在龙巢上方多高：末地传送门顶部往上两格，站在传送门上还够得着。 */
    private static final double HOVER_HEIGHT = 2.0;
    private static final double MIN_FLIGHT_STEP = 0.6;
    private static final double MAX_FLIGHT_STEP = 1.8;
    private static final double FLIGHT_STEP_DIVISOR = 30.0;

    /**
     * 致命伤拦截：除了「破绽期」和 /kill 这类管理员手段，一律取消死亡并开始复活。
     *
     * <p>这个监听器必须排在其它死亡处理之前注册，取消后 {@code ManaEvents#onDeath}
     * 会看到 {@code isCanceled()} 直接返回，不会白给击杀者斗气经验。</p>
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || dragon.level().isClientSide) return;
        CompoundTag state = state(dragon);
        int stage = state.getInt("stage");
        // 破绽期本来就能被打死；generic_kill（/damage 的即死伤害）留给管理员。
        if (stage == STAGE_WINDOW || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            clear(dragon);
            if (stage == STAGE_WINDOW) {
                announce(dragon, Component.translatable("message.dimensional_mana.dragon_vanquished"), false);
            }
            return;
        }
        event.setCanceled(true);
        startRevival(dragon, state);
    }

    /** 飞回龙巢途中无敌：只有破绽期吃伤害，平时照常战斗。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (!(dragon.level() instanceof ServerLevel level)) return;
        if (event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        if (state(dragon).getInt("stage") != STAGE_FLYING) return;
        event.setCanceled(true);
        if (level.getGameTime() % 5L == 0L) {
            level.sendParticles(ParticleTypes.END_ROD, dragon.getX(), dragon.getY() + 4.0, dragon.getZ(),
                    2, 1.6, 2.0, 1.6, 0.01);
        }
    }

    /** 复活期间锁血：末地水晶的回血一律不生效，只让玩家的伤害改变血量。 */
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (!(dragon.level() instanceof ServerLevel)) return;
        if (state(dragon).getInt("stage") != STAGE_NONE) event.setCanceled(true);
    }

    /** 复活状态机：每刻推一下位置、粒子、倒计时。 */
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (!(dragon.level() instanceof ServerLevel level)) return;
        CompoundTag state = state(dragon);
        int stage = state.getInt("stage");
        if (stage == STAGE_NONE) return;
        Vec3 anchor = new Vec3(state.getDouble("anchorX"), state.getDouble("anchorY"), state.getDouble("anchorZ"));
        if (stage == STAGE_FLYING) {
            tickFlight(dragon, level, state, anchor);
        } else if (stage == STAGE_WINDOW) {
            tickWindow(dragon, level, state, anchor);
        }
    }

    /** 第 {@code revivals} 次复活时，破绽期应该有多少血。 */
    public static double revivalHealth(int revivals) {
        double health = FIRST_REVIVAL_HEALTH * Math.pow(REVIVAL_HEALTH_DECAY, Math.max(0, revivals));
        return Math.max(1.0, health);
    }

    /** 第 {@code revivals} 次复活时，破绽持续多少刻。 */
    public static int windowTicks(int revivals) {
        return FIRST_WINDOW_TICKS + WINDOW_TICKS_PER_REVIVAL * Math.max(0, revivals);
    }

    /**
     * 这只生物是不是正处在末影龙复活破绽期。
     *
     * <p>破绽期里伤害按原始值结算（见 {@code ManaEvents#onIncomingDamage} / {@code #onDamagePre}），
     * 斗气、总抗性和受击无敌帧都不生效，这样「复活期间 100 血」才是实打实的 100 点伤害，
     * 高爆发才有机会抢下第一次那 1 秒。</p>
     */
    public static boolean isExposed(LivingEntity entity) {
        return entity instanceof EnderDragon dragon && state(dragon).getInt("stage") == STAGE_WINDOW;
    }

    private static void startRevival(EnderDragon dragon, CompoundTag state) {
        if (!(dragon.level() instanceof ServerLevel level)) return;
        int revivals = Math.max(0, state.getInt("revivals"));
        Vec3 anchor = anchorFor(level, dragon);
        state.putInt("stage", STAGE_FLYING);
        state.putInt("timer", MAX_FLIGHT_TICKS);
        state.putDouble("anchorX", anchor.x);
        state.putDouble("anchorY", anchor.y);
        state.putDouble("anchorZ", anchor.z);
        save(dragon, state);

        dragon.setHealth((float) revivalHealth(revivals));
        dragon.setTarget(null);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        dragon.setDeltaMovement(Vec3.ZERO);

        announce(dragon, Component.translatable("message.dimensional_mana.dragon_revival_start"), false);
        level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.55F);
        level.sendParticles(ParticleTypes.END_ROD, dragon.getX(), dragon.getY() + 4.0, dragon.getZ(),
                40, 2.5, 2.5, 2.5, 0.05);
    }

    /** 龙巢（末地传送门）上方两格；不在末地时就在原地往上三格悬停。 */
    private static Vec3 anchorFor(ServerLevel level, EnderDragon dragon) {
        if (Level.END.equals(level.dimension())) {
            BlockPos origin = dragon.getFightOrigin();
            if (origin == null) origin = BlockPos.ZERO;
            BlockPos nest = EndPodiumFeature.getLocation(origin);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nest);
            return Vec3.atBottomCenterOf(top).add(0.0, HOVER_HEIGHT, 0.0);
        }
        return dragon.position().add(0.0, 3.0, 0.0);
    }

    private static void tickFlight(EnderDragon dragon, ServerLevel level, CompoundTag state, Vec3 anchor) {
        int timer = state.getInt("timer") - 1;
        Vec3 pos = dragon.position();
        Vec3 to = anchor.subtract(pos);
        double distance = to.length();
        boolean arrived = distance <= 1.5 || timer <= 0;
        if (arrived) {
            dragon.setPos(anchor.x, anchor.y, anchor.z);
        } else {
            double step = Mth.clamp(distance / FLIGHT_STEP_DIVISOR, MIN_FLIGHT_STEP, MAX_FLIGHT_STEP);
            pos = pos.add(to.scale(Math.min(distance, step) / distance));
            dragon.setPos(pos.x, pos.y, pos.z);
            double dx = anchor.x - pos.x;
            double dz = anchor.z - pos.z;
            if (dx * dx + dz * dz > 0.01) {
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                dragon.setYRot(yaw);
                dragon.setYBodyRot(yaw);
            }
            if (dragon.tickCount % 10 == 0) {
                level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                        SoundSource.HOSTILE, 2.0F, 0.9F);
            }
        }
        dragon.setDeltaMovement(Vec3.ZERO);
        // 飞行途中无敌，血量锁在复活血量上（末地水晶的 +1 也压回去）。
        dragon.setHealth((float) revivalHealth(state.getInt("revivals")));
        level.sendParticles(ParticleTypes.END_ROD, dragon.getX(), dragon.getY() + 3.5, dragon.getZ(),
                3, 1.8, 2.2, 1.8, 0.02);
        if (arrived) {
            save(dragon, state);
            startWindow(dragon, level);
            return;
        }
        state.putInt("timer", timer);
        save(dragon, state);
    }

    private static void startWindow(EnderDragon dragon, ServerLevel level) {
        CompoundTag state = state(dragon);
        int revivals = Math.max(0, state.getInt("revivals"));
        int ticks = windowTicks(revivals);
        float health = (float) revivalHealth(revivals);
        state.putInt("stage", STAGE_WINDOW);
        state.putInt("timer", ticks);
        state.putFloat("hp", health);
        save(dragon, state);

        dragon.setHealth(health);
        dragon.setDeltaMovement(Vec3.ZERO);

        Vec3 anchor = new Vec3(state.getDouble("anchorX"), state.getDouble("anchorY"), state.getDouble("anchorZ"));
        announce(dragon, Component.translatable("message.dimensional_mana.dragon_revival_window", ticks / 20), false);
        level.playSound(null, BlockPos.containing(anchor), SoundEvents.END_PORTAL_SPAWN,
                SoundSource.HOSTILE, 1.4F, 1.2F);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, anchor.x, anchor.y + 3.0, anchor.z,
                40, 2.2, 2.0, 2.2, 0.05);
    }

    private static void tickWindow(EnderDragon dragon, ServerLevel level, CompoundTag state, Vec3 anchor) {
        // 破绽血量只许往下掉：末地水晶每 10 刻直接 setHealth(+1)，不走治疗事件，这里压回原值。
        float expected = state.getFloat("hp");
        float actual = dragon.getHealth();
        if (actual > expected) {
            dragon.setHealth(expected);
        } else if (actual < expected) {
            state.putFloat("hp", actual);
        }
        int timer = state.getInt("timer");
        double bob = Math.sin((dragon.tickCount % 40) * 0.157) * 0.6;
        dragon.setPos(anchor.x, anchor.y + bob, anchor.z);
        dragon.setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, dragon.getX(), dragon.getY() + 3.0, dragon.getZ(),
                2, 2.0, 1.6, 2.0, 0.01);

        timer--;
        if (timer <= 0) {
            save(dragon, state);
            revive(dragon, level);
            return;
        }
        if (timer % 20 == 0) {
            announce(dragon, Component.translatable("message.dimensional_mana.dragon_revival_countdown", timer / 20), true);
        }
        state.putInt("timer", timer);
        save(dragon, state);
    }

    /** 破绽没打死：满血复活，生命上限再涨 15%，回到正常战斗。 */
    private static void revive(EnderDragon dragon, ServerLevel level) {
        CompoundTag state = state(dragon);
        int revivals = Math.max(0, state.getInt("revivals")) + 1;
        state.putInt("revivals", revivals);
        state.putInt("stage", STAGE_NONE);
        state.putInt("timer", 0);
        save(dragon, state);

        AttributeInstance maxHealth = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(maxHealth.getBaseValue() * MAX_HEALTH_GROWTH);
        dragon.setHealth(dragon.getMaxHealth());
        dragon.setTarget(null);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);

        announce(dragon, Component.translatable("message.dimensional_mana.dragon_revived",
                Math.round(dragon.getMaxHealth())), false);
        level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 3.5F, 0.75F);
        level.sendParticles(ParticleTypes.END_ROD, dragon.getX(), dragon.getY() + 4.0, dragon.getZ(),
                80, 3.5, 3.5, 3.5, 0.1);
    }

    private static CompoundTag state(EnderDragon dragon) {
        return dragon.getPersistentData().getCompound(TAG);
    }

    private static void save(EnderDragon dragon, CompoundTag state) {
        dragon.getPersistentData().put(TAG, state);
    }

    private static void clear(EnderDragon dragon) {
        dragon.getPersistentData().remove(TAG);
    }

    private static void announce(EnderDragon dragon, Component message, boolean actionBar) {
        if (!(dragon.level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, actionBar);
        }
    }

    private DragonRevival() {
    }
}
