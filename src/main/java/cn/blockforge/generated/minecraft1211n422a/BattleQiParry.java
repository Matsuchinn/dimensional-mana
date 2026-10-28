package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 斗气招架（Parry）。
 *
 * <p>玩家的主手是剑、副手为空时，长按右键 5 刻进入「招架姿态」：受到的伤害 ×0.9，
 * 每挨一下扣主手剑 1 点耐久。姿态下按左键发动弹反，没有冷却，按一次算一次：</p>
 *
 * <ul>
 *   <li>3.6 格内有敌对弹射物：消耗 2 点斗气，弹射物当场消散。</li>
 *   <li>近战：按下后 11 刻内挡下近战伤害就算弹反成功，其中 5 刻内到达的判定为完美招架。</li>
 *   <li>斗气不足以支付怪物攻击力时弹反失败：硬吃伤害、斗气清零并进入斗气溃散。</li>
 *   <li>窗口到点也没挡住任何东西（空挥）：扣 1 点斗气；只扣这一笔，不会因此进溃散。</li>
 * </ul>
 *
 * <p>姿态期间移速降到 65%、跳跃高度降到 65%：举着刀不该还健步如飞、跳来跳去。斗气过半的完美格挡
 * 爆闪会用最早那套米字余火贴图，这是客户端 {@link ClientParryVisuals} 的事。</p>
 *
 * <p>所有判定都在服务端完成，客户端只负责按键与画面；姿态按玩家 UUID 存在内存里，
 * 退出、死亡或换手时立刻清掉。</p>
 */
public final class BattleQiParry {
    /** 长按右键多少刻进入姿态。 */
    public static final int STANCE_HOLD_TICKS = 5;
    /** 按下左键后，近战弹反的有效窗口。r204 起收紧 1 刻：普通格挡的判定减少 1 tick。 */
    public static final int GUARD_WINDOW_TICKS = 11;
    /** 窗口内前 5 刻到达的伤害算完美招架。 */
    public static final int PERFECT_WINDOW_TICKS = 5;
    /** 远程/法术弹反的判定半径。 */
    public static final double PROJECTILE_RANGE = 3.6;
    /** 近战弹反的判定半径。 */
    public static final double MELEE_RANGE = 3.0;
    /** 瞬发法术弹反的判定半径：没有弹道可躲，给得比近战宽一些。 */
    public static final double SPELL_RANGE = 8.0;
    /** 招架姿态下的减伤系数：受到伤害 -10%。 */
    public static final float STANCE_DAMAGE_MULTIPLIER = 0.90F;
    /** 完美招架把怪物定住多久。 */
    public static final int STUN_TICKS = 20;
    /** 招架破绽持续多久：和眩晕一样是 1 秒。 */
    public static final int VULNERABILITY_TICKS = 20;
    /** 招架破绽期间受到的所有伤害 ×1.75，也就是 +75%。 */
    public static final float VULNERABILITY_MULTIPLIER = 1.75F;
    /** 弹反一个弹射物要几点斗气。 */
    public static final int PROJECTILE_QI_COST = 2;
    /** 空挥（窗口到点什么都没挡住）扣的斗气。静默扣，不会因此触发溃散。 */
    public static final int WHIFF_QI_COST = 1;
    /** 姿态期间移速与跳跃初速的惩罚比例：都只剩 65%。 */
    public static final double STANCE_MOVE_PENALTY = 0.35;
    /** 斗气满时完美招架溢出的部分换成 2 秒速度 II。 */
    public static final int OVERFLOW_SPEED_TICKS = 40;
    /**
     * 完美招架后的「招架余势」：下一次近战攻击不用等攻击冷却，直接按满蓄力结算。
     * 2 秒内没打出去就自动消失，不会留着白赚。
     */
    public static final int FULL_POWER_TICKS = 40;
    /** 招架余势临时加的攻击速度：换算成冷却只有 0.1 刻，所以攻击指示器（蓄力条）会立刻满。 */
    private static final double FULL_POWER_ATTACK_SPEED = 200.0;

    /** 弹反结果：无、成功、失败（失败会继续吃伤害）。 */
    public enum Outcome { NONE, PARRIED, FAILED }

    private static final class State {
        boolean stance;
        long pressTick = -1;
        long guardUntil = -1;
        /** 左键按下去开了窗口、还没挡住任何东西：窗口收掉时扣 {@link #WHIFF_QI_COST}。 */
        boolean whiffPending;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final ResourceLocation FULL_POWER_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "parry_full_power");
    private static final AttributeModifier FULL_POWER_SPEED = new AttributeModifier(FULL_POWER_ID,
            FULL_POWER_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE);
    private static final ResourceLocation STANCE_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "parry_stance_speed");
    private static final ResourceLocation STANCE_JUMP_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "parry_stance_jump");
    /** 举刀时移速 -35%：ADD_MULTIPLIED_TOTAL 直接乘在最终值上，和药水、护符都能正常叠加。 */
    private static final AttributeModifier STANCE_SPEED = new AttributeModifier(STANCE_SPEED_ID,
            -STANCE_MOVE_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    /**
     * 举刀时跳跃高度 -35%（换算到初速只有 -19.4%：弹跳高度和初速的平方成正比，
     * √0.65 ≈ 0.806，直接把初速砍 35% 会让高度掉掉近六成，那就不是 -35% 了）。
     * jump_strength 是同步属性，客户端手感一致。
     */
    private static final AttributeModifier STANCE_JUMP = new AttributeModifier(STANCE_JUMP_ID,
            Math.sqrt(1.0 - STANCE_MOVE_PENALTY) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    /** 玩家 UUID → 招架余势失效的那一刻；用完（打出这一刀）就改成下一刻失效。 */
    private static final Map<UUID, Long> FULL_POWER = new HashMap<>();

    /** 主手剑、副手空、活着、没被定身，才能摆出招架姿态。客户端也用这一条做按键判断。 */
    public static boolean canParry(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        return player.getMainHandItem().is(ItemTags.SWORDS) && player.getOffhandItem().isEmpty()
                && !NewSpellSystem.immobilized(player);
    }

    public static boolean inStance(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.stance;
    }

    /** 客户端长按右键 5 刻后发来的「进入姿态」。 */
    public static void begin(ServerPlayer player) {
        if (!canParry(player)) {
            STATES.remove(player.getUUID());
            // 防一手时序问题：条件不满足时顺手把可能挂着的姿态惩罚还回去，别留残。
            removeStancePenalty(player);
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), key -> new State());
        if (state.stance) return;
        state.stance = true;
        state.guardUntil = -1;
        state.pressTick = -1;
        state.whiffPending = false;
        applyStancePenalty(player);
        if (player.level() instanceof ServerLevel level) {
            // 举刀出鞘：清脆的金属摩擦声，音调抬高一档。
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.85F, 1.9F);
        }
        // 周围玩家的客户端要把手臂举起来，不能只有自己看得见。
        ParryStancePayload.send(player, true);
    }

    /** 松开右键或换手：收回姿态。窗口还开着就按空挥结算一次。 */
    public static void end(ServerPlayer player) {
        State state = STATES.remove(player.getUUID());
        removeStancePenalty(player);
        if (state == null || !state.stance) return;
        if (state.whiffPending) billWhiff(player, state);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.35F, 1.35F);
        }
        ParryStancePayload.send(player, false);
    }

    /** 掉线、换维度、死亡时的清理：不追空挥的账，只把属性还回去。 */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        State state = STATES.remove(player.getUUID());
        removeStancePenalty(player);
        if (state != null && state.stance) ParryStancePayload.send(player, false);
        clearFullPower(player);
    }

    /** 每刻复核姿态前置：换手、副手满了、被定身都会自动收刀（这种情况不罚空挥）。 */
    public static void tick(ServerPlayer player) {
        expireFullPower(player);
        State state = STATES.get(player.getUUID());
        if (state == null) return;
        if (!canParry(player)) {
            STATES.remove(player.getUUID());
            removeStancePenalty(player);
            // 换手、副手满了、被定身：别人那边也要把举起来的刀放下。
            if (state.stance) ParryStancePayload.send(player, false);
            return;
        }
        long now = player.level().getGameTime();
        if (state.guardUntil >= 0 && now > state.guardUntil) {
            // 窗口到点还挂着「没挡住任何东西」：这一刀算空挥，扣 1 点斗气。
            if (state.whiffPending) billWhiff(player, state);
            state.guardUntil = -1;
        }
    }

    /** 空挥结算：静默扣 {@link #WHIFF_QI_COST} 点斗气（不够就静默跳过，绝不触发溃散）。 */
    private static void billWhiff(ServerPlayer player, State state) {
        state.whiffPending = false;
        state.guardUntil = -1;
        if (ManaData.get(player).battleQi() < WHIFF_QI_COST) return;
        ManaData.spendBattleQiQuiet(player, WHIFF_QI_COST);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.parry_whiff", WHIFF_QI_COST), true);
    }

    /** 进入姿态：挂上移速 / 跳跃 -35%。属性是同步属性，客户端一起变沉。 */
    private static void applyStancePenalty(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(STANCE_SPEED);
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) jump.addOrUpdateTransientModifier(STANCE_JUMP);
    }

    /** 离开姿态：把两个惩罚摘回去（没挂过就是空操作，随处可调）。 */
    private static void removeStancePenalty(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(STANCE_SPEED_ID) != null) speed.removeModifier(STANCE_SPEED_ID);
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null && jump.getModifier(STANCE_JUMP_ID) != null) jump.removeModifier(STANCE_JUMP_ID);
    }

    // ---------------------------------------------------------------- 招架余势

    /**
     * 完美招架成功：把「下一次攻击满伤害」挂到玩家身上。
     *
     * <p>原理是临时把攻击速度拉得极高，攻击冷却（准心下面那条蓄力条）一刻内就走满，
     * 于是原版结算伤害时读到的是满蓄力，打出来就是完整的满伤害，不需要另算伤害。</p>
     */
    public static void grantFullPower(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return;
        FULL_POWER.put(player.getUUID(), player.level().getGameTime() + FULL_POWER_TICKS);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.addOrUpdateTransientModifier(FULL_POWER_SPEED);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.parry_full_power"), true);
    }

    /**
     * 玩家打出一刀：这一次就是「下一次攻击」，用完就收回。
     *
     * <p>伤害要在本刻内按满蓄力算，所以这里只把失效时间挪到下一刻，
     * 真正的属性移除交给 {@link #tick}，不会因为事件顺序把这一刀算成没蓄力。</p>
     */
    public static void consumeFullPower(Player player) {
        if (player == null || player.level().isClientSide) return;
        if (!FULL_POWER.containsKey(player.getUUID())) return;
        FULL_POWER.put(player.getUUID(), player.level().getGameTime() + 1);
    }

    /** 招架余势还在生效时返回 true；界面与提示用得到。 */
    public static boolean hasFullPower(Player player) {
        return player != null && FULL_POWER.containsKey(player.getUUID());
    }

    private static void expireFullPower(ServerPlayer player) {
        Long until = FULL_POWER.get(player.getUUID());
        if (until == null || player.level().getGameTime() < until) return;
        clearFullPower(player);
    }

    private static void clearFullPower(ServerPlayer player) {
        if (FULL_POWER.remove(player.getUUID()) == null) return;
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null && attackSpeed.getModifier(FULL_POWER_ID) != null) {
            attackSpeed.removeModifier(FULL_POWER_ID);
        }
    }

    /** 姿态下按左键：先看弹射物，没有就开一个近战弹反窗口。没有冷却，客户端按一次发一次。 */
    public static void attempt(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null || !state.stance || !canParry(player)) return;
        long now = player.level().getGameTime();
        state.guardUntil = -1;
        state.pressTick = now;

        Projectile projectile = findHostileProjectile(player);
        if (projectile != null) {
            // 弹掉弹射物就算这一按有了交代，上一个没结算的窗口不再追账。
            state.whiffPending = false;
            if (!parryProjectile(player, projectile)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dimensional_mana.parry_no_qi"), true);
            }
            return;
        }
        // 没有弹射物：开一个 11 刻的近战招架窗口，等伤害落下来再结算；到点没挡住就按空挥扣 1 点斗气。
        state.guardUntil = now + GUARD_WINDOW_TICKS;
        state.whiffPending = true;
        if (player.level() instanceof ServerLevel level) {
            // 空挥一声，告诉玩家「这一刀已经按下去了」。
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.45F, 1.4F);
        }
    }

    /**
     * 近战伤害到达时结算弹反。
     *
     * @return {@link Outcome#PARRIED} 表示这一下已经被弹开，调用方要取消伤害；
     *         {@link Outcome#FAILED} 表示斗气不足、弹反失败，调用方照常结算伤害。
     */
    public static Outcome onIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        State state = STATES.get(player.getUUID());
        if (state == null || !state.stance) return Outcome.NONE;
        long now = player.level().getGameTime();
        if (state.guardUntil < 0 || now > state.guardUntil || state.pressTick < 0) return Outcome.NONE;
        // 只接贴脸近战：直接来源和攻击者必须是同一个生物。
        Entity direct = source.getDirectEntity();
        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker) || direct != attacker) return Outcome.NONE;
        // 玩家之间不互相弹刀：默认关闭 PvP 弹反，避免多人联机时你弹我、我弹你，双方都被定身。
        if (attacker instanceof Player && !ManaConfig.PVP_PARRY.get()) return Outcome.NONE;
        if (!NewSpellSystem.enemy(player, attacker)) return Outcome.NONE;
        if (!nearEnough(player, attacker)) return Outcome.NONE;

        boolean perfect = now - state.pressTick <= PERFECT_WINDOW_TICKS;
        state.guardUntil = -1;
        state.pressTick = -1;
        state.whiffPending = false;
        Vec3 middle = player.position().add(attacker.position()).scale(0.5).add(0, 0.9, 0);
        return settle(player, attacker, perfect, amount, middle, null) ? Outcome.PARRIED : Outcome.FAILED;
    }

    /**
     * 「没有弹道、瞬间命中」的法术（元素史莱姆的初级魔法）也能弹。
     *
     * <p>这类法术没有飞行物可打，玩家看不到弹道、也没有反应时间，所以判定放宽一点：
     * 只要处在招架姿态、对方是敌对生物、距离够近，就按普通招架结算；
     * 如果正好按下了左键（11 刻窗口内），照旧算完美招架。</p>
     *
     * @param flavor 法术元素，用来放对应的碎裂特效；传 {@code null} 表示不放。
     * @return true 表示这一下已经被弹开，调用方不要再结算伤害。
     */
    public static boolean parryInstantSpell(ServerPlayer player, LivingEntity caster, String flavor, float amount) {
        State state = STATES.get(player.getUUID());
        if (state == null || !state.stance) return false;
        // 玩家对玩家同样不弹刀。
        if (caster instanceof Player && !ManaConfig.PVP_PARRY.get()) return false;
        if (!canParry(player) || !NewSpellSystem.enemy(player, caster)) return false;
        if (!spellNearEnough(player, caster)) return false;
        long now = player.level().getGameTime();
        boolean perfect = state.guardUntil >= 0 && now <= state.guardUntil && state.pressTick >= 0
                && now - state.pressTick <= PERFECT_WINDOW_TICKS;
        state.guardUntil = -1;
        state.pressTick = -1;
        // 法术被弹开了，之前那一次左键按下去就有了交代，不再追空挥的账。
        state.whiffPending = false;
        Vec3 middle = player.position().add(caster.position()).scale(0.5).add(0, 0.9, 0);
        return settle(player, caster, perfect, amount, middle, flavor);
    }

    /**
     * 弹反结算：付斗气、放特效、给反馈。
     *
     * @param flavor 非空时额外放一层对应元素的法术碎裂粒子与音效（法术弹反用）。
     * @return false 表示斗气不够，调用方照常结算伤害。
     */
    private static boolean settle(ServerPlayer player, LivingEntity attacker, boolean perfect, float amount,
                                  Vec3 at, String flavor) {
        int cost = Math.max(1, (int)Math.ceil(amount));
        ManaData.Snapshot qi = ManaData.get(player);
        if (qi.battleQi() < cost) {
            // 斗气不够：硬吃这一下、斗气清零并进入斗气溃散。
            ManaData.setBattleQi(player, 0);
            BattleQiCollapseEffect.apply(player);
            feedback(player, ParryFeedbackPayload.FAIL, at);
            return false;
        }
        // 成功弹反是「主动花掉斗气」，不该把斗气花光也算成溃散，所以这里直接改数值。
        ManaData.spendBattleQiQuiet(player, cost);
        if (flavor != null && player.level() instanceof ServerLevel level) spellBurst(level, at, flavor);
        if (perfect) perfectParry(player, attacker, cost, qi.maxBattleQi(), at);
        else normalParry(player, attacker, at);
        return true;
    }

    /**
     * 招架姿态下的普通减伤：基础 10%，「钢铁壁垒」每级再加 2%，最多 50%。
     * 只改数值，耐久在伤害真正落地后扣。
     */
    public static float reduceDamage(ServerPlayer player, float amount) {
        double reduction = (1.0 - STANCE_DAMAGE_MULTIPLIER) + QiProgression.parryReductionBonus(player);
        return amount * (float)Math.max(0.1, 1.0 - Math.min(0.9, reduction));
    }

    /** 每挨一下真实伤害扣主手剑 1 点耐久。 */
    public static void wearSword(ServerPlayer player) {
        if (!inStance(player)) return;
        ItemStack sword = player.getMainHandItem();
        if (!sword.is(ItemTags.SWORDS) || !sword.isDamageableItem()) return;
        sword.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

    /** 姿态下右键不再触发放置方块、开村民交易这类交互。 */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (shouldCancel(event)) event.setCanceled(true);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (shouldCancel(event)) event.setCanceled(true);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (shouldCancel(event)) event.setCanceled(true);
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (shouldCancel(event)) event.setCanceled(true);
    }

    private static boolean shouldCancel(PlayerInteractEvent event) {
        return event.getEntity() instanceof ServerPlayer player && inStance(player);
    }

    // ---------------------------------------------------------------- 远程弹反

    private static Projectile findHostileProjectile(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(PROJECTILE_RANGE);
        Projectile best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, area,
                candidate -> hostileProjectile(player, candidate))) {
            double distance = projectile.distanceToSqr(player);
            if (distance < bestDistance) { bestDistance = distance; best = projectile; }
        }
        return best;
    }

    private static boolean hostileProjectile(ServerPlayer player, Projectile projectile) {
        if (projectile == null || !projectile.isAlive() || projectile.isRemoved()) return false;
        Entity owner = projectile.getOwner();
        if (owner == player) return false;
        // 玩家射来的弹射物也不弹（默认关闭 PvP 弹刀）。
        if (owner instanceof Player && !ManaConfig.PVP_PARRY.get()) return false;
        if (owner instanceof LivingEntity living) return NewSpellSystem.enemy(player, living);
        // 发射器打出来的箭没有主人，同样算敌对弹射物。
        return !(owner instanceof Player);
    }

    private static boolean parryProjectile(ServerPlayer player, Projectile projectile) {
        ManaData.Snapshot qi = ManaData.get(player);
        if (qi.battleQi() < PROJECTILE_QI_COST) return false;
        ManaData.spendBattleQiQuiet(player, PROJECTILE_QI_COST);
        Vec3 center = projectile.getBoundingBox().getCenter();
        if (player.level() instanceof ServerLevel level) projectileBurst(level, center, projectileFlavor(projectile));
        projectile.discard();
        feedback(player, ParryFeedbackPayload.PROJECTILE, center);
        return true;
    }

    private static String projectileFlavor(Projectile projectile) {
        if (projectile instanceof SmallFireball || projectile instanceof LargeFireball
                || projectile instanceof DragonFireball) return "fire";
        if (projectile instanceof Snowball) return "ice";
        if (projectile instanceof WitherSkull || projectile instanceof ShulkerBullet) return "arcane";
        if (projectile instanceof AbstractArrow) return "arrow";
        if (projectile instanceof ThrownPotion) return "water";
        return "arcane";
    }

    private static void projectileBurst(ServerLevel level, Vec3 at, String flavor) {
        switch (flavor) {
            case "arrow" -> {
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 18, 0.3, 0.3, 0.3, 0.22);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.12);
                play(level, at, SoundEvents.ARROW_HIT, 1.0F, 1.5F);
                play(level, at, SoundEvents.ANVIL_PLACE, 0.4F, 1.85F);
            }
            case "fire" -> {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 22, 0.35, 0.35, 0.35, 0.04);
                level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.03);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 4, 0.25, 0.25, 0.25, 0.0);
                play(level, at, SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.5F);
            }
            case "ice" -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 24, 0.35, 0.35, 0.35, 0.06);
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.12);
                play(level, at, SoundEvents.GLASS_BREAK, 0.9F, 1.7F);
            }
            case "water" -> {
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 26, 0.35, 0.35, 0.35, 0.1);
                level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.05);
                play(level, at, SoundEvents.GENERIC_SPLASH, 0.8F, 1.4F);
            }
            default -> {
                level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 26, 0.35, 0.35, 0.35, 0.18);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.05);
                play(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.9F, 1.35F);
                play(level, at, SoundEvents.ENCHANTMENT_TABLE_USE, 0.45F, 1.5F);
            }
        }
        // 空中留下一圈向外扩散的波纹。
        ring(level, at, ParticleTypes.ENCHANTED_HIT, 18, 0.7);
    }

    // ---------------------------------------------------------------- 近战弹反

    private static void normalParry(ServerPlayer player, LivingEntity attacker, Vec3 at) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 22, 0.35, 0.3, 0.35, 0.25);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.1);
            play(level, at, SoundEvents.ANVIL_PLACE, 0.55F, 1.45F);
            play(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 0.9F, 0.9F);
        }
        // 普通格挡的屏幕反馈：准心白圈 + 被打的那只怪物身上一段小闪光（和完美格挡同一套画法，
        // 只是小一号）。带上实体 id，闪光是跟着怪物走的，不是钉在一个坐标上。
        ParryFeedbackPayload.sendFlash(player, ParryFeedbackPayload.NORMAL, at, attacker.getId());
    }

    private static void perfectParry(ServerPlayer player, LivingEntity attacker, int cost, int maxQi, Vec3 at) {
        if (player.level() instanceof ServerLevel level) {
            // 巨大的十字冲击波：三记横扫粒子交叉叠成十字，再补一圈金色冲击环。
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 3, 0.45, 0.45, 0.45, 0.0);
            // 火花往四面八方甩：速度给大一点，才是「迸开」；再补一层烟花火星，
            // 就是那种细碎、会往下掉的橙色小点，和贴身闪光里的火星对得上。
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.55, 0.45, 0.55, 0.62);
            level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 20, 0.50, 0.45, 0.50, 0.32);
            level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.3, at.z, 16, 0.6, 0.5, 0.6, 0.45);
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            ring(level, at, ParticleTypes.END_ROD, 24, 1.0);
            // 再随机补一撮金色碎光：每次完美招架的画面也不完全一样。
            extraFlourish(level, at);
            // 「铛——！」六段剑鸣随机挑一段，再叠随机余韵、随机回响和落地闷响。
            ParrySounds.perfect(level, at, level.getRandom());
            // 打断怪物当前动作并眩晕 1 秒；定身、停 AI 都在 NewSpellSystem 里统一处理。
            NewSpellSystem.control(player, attacker, NewSpellContent.STUN, STUN_TICKS);
            // 眩晕的这一秒同时是破绽窗口：期间对它打出的所有伤害都 +75%，「见切」每级再加 5%。
            applyVulnerability(attacker, player);
            stunStars(level, attacker);
        }

        // 返还 1.5 倍消耗：先扣后还，净赚一半；返还后超过上限的部分换成 2 秒速度 II。
        int refund = (int)Math.round(cost * 1.5);
        ManaData.Snapshot now = ManaData.get(player);
        int target = now.battleQi() + refund;
        ManaData.setBattleQi(player, Math.min(maxQi, target));
        if (target > maxQi && player.level() instanceof ServerLevel level) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, OVERFLOW_SPEED_TICKS, 1, false, true, true));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(),
                    10, 0.4, 0.4, 0.4, 0.05);
        }
        // 屏幕反馈：准心金圈 + 白闪 + 金色光晕，怪物身上再贴一段 8 刻/4 帧的爆闪，
        // 让「这一下被弹开了」看得更明白。带上实体 id，爆闪跟着那只怪物走。
        ParryFeedbackPayload.sendFlash(player, ParryFeedbackPayload.PERFECT, at, attacker.getId());
        // 招架余势：下一次近战攻击不用等蓄力条，直接按满伤害打出去。
        grantFullPower(player);
        // 斗气经验 +20；戴着剑士护符再给 2 秒的 +25% 移速。
        QiSkills.onPerfectParry(player);
    }

    /**
     * 招架破绽：完美招架把怪物打晕的同时，给它挂上 1 秒易伤。
     *
     * <p>「不可叠加、反复触发刷新时间」：每次触发都先摘掉旧状态再重挂，
     * 所以身上永远只有一层，持续时间也会重新回到完整的 1 秒。</p>
     */
    public static void applyVulnerability(LivingEntity mob, Player player) {
        if (mob == null || mob.level().isClientSide) return;
        // 先摘后挂：旧计时比新计时长时，直接 addEffect 不一定能刷新时间。
        mob.removeEffect(NewSpellContent.PARRY_VULNERABLE);
        // 「见切」的等级写进效果等级里：易伤倍率 = 1.75 + 0.05 × 等级。
        int rank = QiProgression.get(player).skill(QiProgression.Skill.MIKIRI);
        NewSpellSystem.effect(mob, NewSpellContent.PARRY_VULNERABLE, VULNERABILITY_TICKS, rank);
    }

    /** 招架破绽生效时返回对应的易伤倍率（基础 1.75，见切每级 +0.05），否则返回 1。 */
    public static float vulnerabilityMultiplier(LivingEntity entity) {
        MobEffectInstance instance = entity == null ? null : entity.getEffect(NewSpellContent.PARRY_VULNERABLE);
        if (instance == null) return 1.0F;
        return VULNERABILITY_MULTIPLIER + instance.getAmplifier() * 0.05F;
    }

    /** 怪物头顶的眩晕星星与惊叹号。 */
    private static void stunStars(ServerLevel level, LivingEntity mob) {
        double x = mob.getX();
        double y = mob.getY() + mob.getBbHeight() + 0.45;
        double z = mob.getZ();
        level.sendParticles(ParticleTypes.EFFECT, x, y, z, 14, 0.35, 0.2, 0.35, 0.02);
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 8, 0.3, 0.2, 0.3, 0.02);
        level.sendParticles(ParticleTypes.NOTE, x, y + 0.2, z, 4, 0.25, 0.15, 0.25, 0.0);
        play(level, new Vec3(x, y, z), SoundEvents.ANVIL_PLACE, 0.7F, 0.6F);
    }

    // ---------------------------------------------------------------- 工具

    private static boolean nearEnough(ServerPlayer player, LivingEntity attacker) {
        double reach = MELEE_RANGE + (player.getBbWidth() + attacker.getBbWidth()) * 0.5;
        return player.distanceToSqr(attacker) <= reach * reach;
    }

    private static boolean spellNearEnough(ServerPlayer player, LivingEntity caster) {
        double reach = SPELL_RANGE + (player.getBbWidth() + caster.getBbWidth()) * 0.5;
        return player.distanceToSqr(caster) <= reach * reach;
    }

    /** 瞬发法术被弹开：按元素放一撮碎裂粒子配对应的音色，颜色和那只史莱姆对得上。 */
    private static void spellBurst(ServerLevel level, Vec3 at, String flavor) {
        switch (flavor) {
            case "fire" -> {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 20, 0.32, 0.32, 0.32, 0.04);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 4, 0.22, 0.22, 0.22, 0.0);
                play(level, at, SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.5F);
            }
            case "water" -> {
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 24, 0.35, 0.35, 0.35, 0.1);
                level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.05);
                play(level, at, SoundEvents.GENERIC_SPLASH, 0.85F, 1.4F);
            }
            case "ice" -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 22, 0.35, 0.35, 0.35, 0.06);
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.12);
                play(level, at, SoundEvents.GLASS_BREAK, 0.9F, 1.7F);
            }
            case "lightning" -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 28, 0.4, 0.4, 0.4, 0.2);
                play(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 1.85F);
            }
            case "wind" -> {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 3, 0.4, 0.3, 0.4, 0.0);
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 12, 0.35, 0.35, 0.35, 0.08);
                play(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.25F);
            }
            case "earth" -> {
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 24, 0.4, 0.35, 0.4, 0.18);
                play(level, at, SoundEvents.STONE_BREAK, 0.9F, 1.2F);
            }
            case "nature" -> {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 20, 0.35, 0.35, 0.35, 0.05);
                play(level, at, SoundEvents.GRASS_BREAK, 0.9F, 1.35F);
            }
            case "metal" -> {
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.32, 0.3, 0.32, 0.22);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.1);
                play(level, at, SoundEvents.ANVIL_PLACE, 0.9F, 1.8F);
            }
            case "light" -> {
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 18, 0.3, 0.3, 0.3, 0.06);
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                play(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.9F);
            }
            case "poison" -> {
                level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 20, 0.35, 0.3, 0.35, 0.1);
                play(level, at, SoundEvents.SLIME_SQUISH, 0.9F, 1.5F);
            }
            case "sound" -> {
                level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 16, 0.4, 0.35, 0.4, 0.05);
                play(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
            }
            default -> {
                // 奥术：紫色碎屑加一圈波纹。
                level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 24, 0.35, 0.35, 0.35, 0.16);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.05);
                play(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.9F, 1.35F);
            }
        }
        ring(level, at, ParticleTypes.ENCHANTED_HIT, 16, 0.65);
    }

    /**
     * 完美招架每次多撒一撮金粉，三种样式随机：
     * 上升的星火、炸开的烟花、贴地扩散的光尘——画面和音效一起变化，才不容易腻。
     */
    private static void extraFlourish(ServerLevel level, Vec3 at) {
        switch (level.getRandom().nextInt(3)) {
            case 0 -> level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.2, at.z, 14, 0.3, 0.5, 0.3, 0.06);
            case 1 -> level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 0.2, at.z, 16, 0.35, 0.35, 0.35, 0.12);
            default -> {
                level.sendParticles(ParticleTypes.GLOW, at.x, at.y + 0.2, at.z, 18, 0.45, 0.35, 0.45, 0.08);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 0.2, at.z, 6, 0.25, 0.3, 0.25, 0.15);
            }
        }
    }

    private static void ring(ServerLevel level, Vec3 center, SimpleParticleType particle, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(particle, x, center.y, z, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void feedback(ServerPlayer player, int kind, Vec3 at) {
        ParryFeedbackPayload.send(player, kind, at);
        if (kind == ParryFeedbackPayload.FAIL) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.parry_collapse"), true);
        }
    }

    private BattleQiParry() { }
}
