package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 金系追加法术：金钟罩与万剑归一。
 *
 * <p>金钟罩（中级·防护）：读条两秒半后，在施法者身上罩起一个能吸收 250 点伤害的金色钟罩，
 * 持续 50 秒。钟罩存在期间免疫破甲：腐化一类的降护甲效果无法生效，斗气破除也无法穿透斗气。</p>
 *
 * <p>万剑归一（高级·伤害）：读条五秒后，在高空劈开一道长 64 格、宽 24 格的天空裂缝，持续 60 秒，
 * 裂缝存在期间再按一次施法键可以提前关闭。此后施法者每一次近战或远程命中，都会让裂缝里的
 * 随机位置落下十六把飞剑（每刻一把，两轮之间至少间隔 20 刻）：每把剑消耗 6 魔素，以 99 米/秒
 * 射向目标当时所在的位置，带轻微追踪、无视方块障碍，沿途生物都会被它穿透并受到 10 点金伤。
 * 每把剑命中敌人后，施法者会叠加一层“剑意”：近战/远程额外造成 2% 金属性伤害，持续 10 秒，
 * 最多叠到 18 层（+36%），叠加时刷新持续时间。</p>
 */
public final class MetalSpells {
    // ───────────────────────── 金钟罩 ─────────────────────────
    private static final String BELL_KEY = "DimensionalManaGoldenBell";
    private static final int BELL_TICKS = 1000;
    private static final float BELL_HEALTH = 250.0F;
    private static final int BELL_VISUAL_REFRESH = 40;
    private static final int BELL_VISUAL_TICKS = 60;
    private static final Map<UUID, Long> BELL_NOTICE = new HashMap<>();

    // ───────────────────────── 万剑归一 ─────────────────────────
    private static final int RIFT_DURATION = 1200;
    /** 长 4 个区块（64 格）→ 半长 32 格；宽 1.5 个区块（24 格）→ 半宽 12 格。 */
    private static final double RIFT_HALF_LENGTH = 32.0;
    private static final double RIFT_HALF_WIDTH = 12.0;
    /** 裂缝开在高度限制附近：距世界最高可建造高度 12 格。 */
    private static final int RIFT_SKY_MARGIN = 12;
    private static final int RIFT_VISUAL_REFRESH = 40;
    private static final int RIFT_VISUAL_TICKS = 60;
    private static final int VOLLEY_SWORDS = 16;
    /** 两轮首发剑之间最短间隔：20 刻（1 秒）。 */
    private static final int VOLLEY_INTERVAL = 20;
    /** 99 米/秒 ÷ 20 刻 = 每刻 4.95 格。 */
    private static final double SWORD_SPEED = 99.0 / 20.0;
    private static final double SWORD_MANA = 6.0;
    private static final float SWORD_DAMAGE = 10.0F;
    /** 剑身判定半径；飞剑 99 米/秒掠过时给目标留一点余量，避免贴着边擦过去算没中。 */
    private static final double SWORD_HIT_RADIUS = 0.9;
    /** 飞行上限：从世界高度上限落到最深处约 300 格，留足余量，保证飞剑一定飞得到。 */
    private static final int SWORD_MAX_TICKS = 160;
    private static final int SWORD_STACK_MAX = 18;
    private static final int SWORD_STACK_TICKS = 200;
    private static final float SWORD_STACK_BONUS = 0.02F;
    private static final Map<UUID, Long> SWORD_MANA_NOTICE = new HashMap<>();

    private static final Map<UUID, SwordRift> RIFTS = new HashMap<>();
    private static final List<FlyingSword> SWORDS = new ArrayList<>();

    /** 一道已经劈开的天空裂缝；position 固定在施法时的天空中。 */
    private static final class SwordRift {
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final Vec3 direction;
        final Vec3 side;
        final long born;
        final long until;
        long nextVolleyAt;
        long nextSwordAt;
        long lastVisualAt;
        int pending;
        Vec3 aim = Vec3.ZERO;
        UUID targetId;

        SwordRift(ServerLevel level, UUID owner, Vec3 center, Vec3 direction, Vec3 side, long born) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.direction = direction;
            this.side = side;
            this.born = born;
            this.until = born + RIFT_DURATION;
            this.lastVisualAt = born;
        }
    }

    /**
     * 一把正在飞行的飞剑。
     *
     * <p>命中在出剑那一刻就按整条航线算好：每个目标记录“飞剑掠到它的刻数”，
     * 到点再结算伤害。这样每一把出鞘的剑都一定会有结算，不会因为某一刻的线段检测
     * 时机、实体追踪范围或目标小幅走位而白飞一趟。</p>
     */
    private static final class FlyingSword {
        final ServerLevel level;
        final UUID owner;
        /** 目标 UUID → 飞剑掠过它的刻数。 */
        final Map<UUID, Integer> impacts = new LinkedHashMap<>();
        Vec3 position;
        final Vec3 aim;
        final int landAge;
        int age;

        FlyingSword(ServerLevel level, UUID owner, Vec3 position, Vec3 aim, int landAge) {
            this.level = level;
            this.owner = owner;
            this.position = position;
            this.aim = aim;
            this.landAge = landAge;
        }
    }

    // ───────────────────────── 金钟罩 ─────────────────────────

    /** 读条结束：给自己罩上金钟罩。 */
    public static void beginGoldenBell(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        player.addEffect(new MobEffectInstance(NewSpellContent.GOLDEN_BELL, BELL_TICKS, 0, false, true, true));
        CompoundTag tag = new CompoundTag();
        tag.putFloat("hp", BELL_HEALTH);
        tag.putUUID("owner", player.getUUID());
        player.getPersistentData().put(BELL_KEY, tag);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.golden_bell_cast",
                (int)BELL_HEALTH, BELL_TICKS / 20), true);
        SpellVisualPayload.send(player, SpellDefinition.GOLDEN_BELL, SpellVisualPayload.GOLDEN_BELL,
                player.getId(), player.position(), player.position(), BELL_VISUAL_TICKS);
        Vec3 at = player.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 34, 0.85, 1.05, 0.85, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 18, 0.7, 0.9, 0.7, 0.22);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.85F, 0.65F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.35F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.62F);
        SpellSyncPayload.send(player);
    }

    /**
     * 金钟罩吸收伤害：返回还要落到生命值上的伤害。
     * 与磐石外壳一致，只接住经过护甲、斗气等减免之后的实际伤害。
     */
    public static float absorbBell(LivingEntity victim, float damage, DamageSource source) {
        if (damage <= 0.0F || victim.level().isClientSide) return damage;
        // 虚空、/kill 这类穿透无敌的伤害不归钟罩管，避免出现“杀不死”的异常。
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;
        if (!victim.hasEffect(NewSpellContent.GOLDEN_BELL)) return damage;
        CompoundTag tag = victim.getPersistentData().getCompound(BELL_KEY);
        float shell = tag.isEmpty() ? BELL_HEALTH : tag.getFloat("hp");
        if (shell <= 0.0F) return damage;
        float absorbed = Math.min(shell, damage);
        shell -= absorbed;
        tag.putFloat("hp", shell);
        victim.getPersistentData().put(BELL_KEY, tag);
        ServerLevel level = victim.level() instanceof ServerLevel server ? server : null;
        if (shell <= 0.001F) {
            breakBell(victim, level);
        } else if (level != null) {
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 7, 0.5, 0.6, 0.5, 0.06);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.45, 0.55, 0.45, 0.18);
            if (level.getGameTime() % 4 == 0)
                level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.30F, 1.55F);
            if (victim instanceof ServerPlayer player) notifyBell(player, shell);
        }
        return damage - absorbed;
    }

    /** 金钟罩期间免疫破甲：腐化等降护甲效果无法生效，斗气破除也无法穿透斗气。 */
    public static boolean immuneToArmorBreak(LivingEntity entity) {
        return entity != null && entity.hasEffect(NewSpellContent.GOLDEN_BELL);
    }

    /** 每刻兜底：钟罩被牛奶清掉、到期或被打破时，残留数据一起收回。 */
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if (!entity.hasEffect(NewSpellContent.GOLDEN_BELL)) {
            if (entity.getPersistentData().contains(BELL_KEY)) {
                entity.getPersistentData().remove(BELL_KEY);
                SpellVisualPayload.send(entity, SpellDefinition.GOLDEN_BELL, SpellVisualPayload.GOLDEN_BELL_END,
                        entity.getId(), entity.position(), entity.position(), 1);
            }
            return;
        }
        // 钟罩还在：每秒兜底清掉任何已经附着上的破甲（腐化、超导、熔蚀）。
        if (entity.tickCount % 20 == 0) SpellCasting.clearArmorBreaks(entity);
    }

    private static void breakBell(LivingEntity victim, ServerLevel level) {
        victim.getPersistentData().remove(BELL_KEY);
        victim.removeEffect(NewSpellContent.GOLDEN_BELL);
        if (level != null) {
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 42, 0.8, 1.0, 0.8, 0.12);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 22, 0.8, 1.0, 0.8, 0.32);
            level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_BREAK, SoundSource.PLAYERS, 1.0F, 1.15F);
            level.playSound(null, victim.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 0.72F);
            if (victim instanceof ServerPlayer player)
                player.displayClientMessage(Component.translatable("message.dimensional_mana.golden_bell_broken"), true);
        }
        SpellVisualPayload.send(victim, SpellDefinition.GOLDEN_BELL, SpellVisualPayload.GOLDEN_BELL_END,
                victim.getId(), victim.position(), victim.position(), 1);
    }

    private static void notifyBell(ServerPlayer player, float shell) {
        long now = player.level().getGameTime();
        Long last = BELL_NOTICE.get(player.getUUID());
        if (last != null && now - last < 10) return;
        BELL_NOTICE.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.golden_bell_left",
                (int)Math.ceil(shell)), true);
    }

    // ───────────────────────── 万剑归一 ─────────────────────────

    /** 读条结束：在高空劈开天空裂缝。 */
    public static void beginTenThousandSwords(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        SwordRift old = RIFTS.get(player.getUUID());
        if (old != null) endRift(player, old, false);
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
        double sky = level.getMaxBuildHeight() - RIFT_SKY_MARGIN;
        Vec3 center = new Vec3(player.getX(), sky, player.getZ());
        SwordRift rift = new SwordRift(level, player.getUUID(), center, look, side, now);
        RIFTS.put(player.getUUID(), rift);
        // 一条动作栏提示写清楚尺寸、时长与提前关闭方式，避免第二条把第一条顶掉。
        player.displayClientMessage(Component.translatable("message.dimensional_mana.ten_thousand_swords_start",
                        (int)(RIFT_HALF_LENGTH * 2), (int)(RIFT_HALF_WIDTH * 2), RIFT_DURATION / 20)
                .append(Component.literal("　"))
                .append(Component.translatable("message.dimensional_mana.ten_thousand_swords_hint")), true);
        // 开裂缝：沿裂缝扫一道金光，同时给施法者脚下补一圈金属性火花。
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            Vec3 p = center.add(look.scale((t - 0.5) * RIFT_HALF_LENGTH * 2.0));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 5, 0.6, 1.2, 0.6, 0.03);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.7, 1.4, 0.7, 0.28);
        }
        Vec3 at = player.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 1.6, 2.2, 1.6, 0.08);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 1.8, 2.4, 1.8, 0.25);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.15F, 0.48F);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.72F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.55F);
        sendRiftVisual(player, rift, SpellVisualPayload.SKY_RIFT, RIFT_VISUAL_TICKS);
        SpellSyncPayload.send(player);
    }

    /** 施法键提前关闭裂缝；返回 false 表示当前没有裂缝。 */
    public static boolean cancelRift(ServerPlayer player) {
        SwordRift rift = RIFTS.get(player.getUUID());
        if (rift == null) return false;
        endRift(player, rift, true);
        return true;
    }

    /**
     * 近战挥击命中时的入口。
     *
     * <p>剑雨不依赖“这次伤害到底结算了多少”：别的模组把伤害改成 0、目标带无敌帧、
     * 假人 / 召唤物的受击逻辑特殊，都不应该让一轮剑雨凭空消失。挥击命中就先出剑，
     * 真正的金伤由飞剑自己落地时结算。</p>
     */
    public static void onMeleeHit(ServerPlayer player, LivingEntity victim) {
        if (player == null || victim == null || player == victim) return;
        if (player.level().isClientSide || victim.isRemoved()) return;
        startVolley(player, victim);
    }

    /** 一次攻击命中后：先从裂缝里开始下一轮十六把飞剑，再结算剑意的额外金伤。 */
    public static void onPlayerAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        // 致死一击也要照常出剑，所以这里不要求目标还活着；只排除已经被移除的实体。
        if (player.level().isClientSide || victim == null || victim.isRemoved()) return;
        startVolley(player, victim);
        applySwordIntent(player, victim, damage);
    }

    /** 起一轮剑雨；同一轮没出完、或者距上一轮不足 1 秒时直接忽略，避免重复触发。 */
    private static void startVolley(ServerPlayer player, LivingEntity victim) {
        long now = player.level().getGameTime();
        SwordRift rift = RIFTS.get(player.getUUID());
        if (rift == null || rift.level != player.serverLevel() || now >= rift.until
                || rift.pending != 0 || now < rift.nextVolleyAt) return;
        rift.nextVolleyAt = now + VOLLEY_INTERVAL;
        rift.pending = VOLLEY_SWORDS;
        rift.nextSwordAt = now;
        rift.aim = victim.getBoundingBox().getCenter();
        rift.targetId = victim.getUUID();
        player.playNotifySound(SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS, 0.65F, 0.62F);
    }

    /** 剑意：每层让这次近战/远程额外造成 2% 的金属性伤害（最高 18 层）。 */
    private static void applySwordIntent(ServerPlayer player, LivingEntity victim, float damage) {
        MobEffectInstance intent = player.getEffect(NewSpellContent.SWORD_INTENT);
        if (intent == null || damage <= 0.0F || victim.level().isClientSide) return;
        int stacks = Math.clamp(intent.getAmplifier() + 1, 1, SWORD_STACK_MAX);
        float extra = ElementReactionSystem.elementalDamage(player, victim, ElementType.METAL,
                damage * SWORD_STACK_BONUS * stacks);
        if (extra <= 0.0F) return;
        if (SpellDamage.hitResolvedElement(victim.level(), player, victim, ElementType.METAL.id(), extra)) {
            if (victim.level() instanceof ServerLevel level) {
                Vec3 at = victim.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.32, 0.4, 0.32, 0.14);
            }
        }
    }

    /** 飞剑命中敌人：给施法者叠一层剑意，持续 10 秒并刷新。 */
    private static void addSwordIntent(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(NewSpellContent.SWORD_INTENT);
        int stacks = current == null ? 1 : Math.min(SWORD_STACK_MAX, current.getAmplifier() + 2);
        player.addEffect(new MobEffectInstance(NewSpellContent.SWORD_INTENT, SWORD_STACK_TICKS,
                stacks - 1, false, true, true));
    }

    /** 从裂缝里的随机位置放出一把飞剑；魔素不够就停掉这一轮。 */
    private static boolean spawnSword(ServerPlayer owner, SwordRift rift) {
        double cost = SpellAttributes.reducedManaCost(owner, SWORD_MANA);
        if (ManaCheats.manaCostEnabled(owner) && !ManaCheats.hasMana(owner, cost)) {
            notifySwordMana(owner, (int)Math.ceil(cost));
            return false;
        }
        if (!ManaData.spendMana(owner, SWORD_MANA)) {
            notifySwordMana(owner, (int)Math.ceil(cost));
            return false;
        }
        SpellCasting.syncMana(owner);
        ServerLevel level = rift.level;
        // 裂缝长 64、宽 24：沿水平长轴 ±32 格、横向 ±12 格铺开，和客户端画出的水平裂口一致。
        double along = (owner.getRandom().nextDouble() * 2.0 - 1.0) * RIFT_HALF_LENGTH;
        double across = (owner.getRandom().nextDouble() * 2.0 - 1.0) * RIFT_HALF_WIDTH;
        double jitter = (owner.getRandom().nextDouble() - 0.5) * 1.2;
        Vec3 spawn = rift.center.add(rift.direction.scale(along)).add(rift.side.scale(across)).add(0, jitter, 0);
        Vec3 aim = rift.aim;
        LivingEntity target = rift.targetId == null ? null
                : level.getEntity(rift.targetId) instanceof LivingEntity living ? living : null;
        if (target != null && target.isAlive()) aim = target.getBoundingBox().getCenter();
        double distance = spawn.distanceTo(aim);
        int landAge = Math.clamp((int)Math.ceil(distance / SWORD_SPEED) + 1, 3, SWORD_MAX_TICKS);
        FlyingSword sword = new FlyingSword(level, owner.getUUID(), spawn, aim, landAge);
        planSwordImpacts(sword, owner, rift.targetId);
        SWORDS.add(sword);
        sendSwordVisual(owner, spawn, aim, landAge);
        return true;
    }

    /**
     * 出剑时就把整条航线扫一遍：航线经过的每个敌对生物都登记一个命中刻数。
     *
     * <p>判定用“从出剑点到落点的整段”，而不是逐刻的小线段，所以飞剑一定能打到目标；
     * 玩家刚刚亲手打中的那个目标无条件算数，避免个别假人 / 召唤物的敌对判定差异把整把剑吞掉。</p>
     */
    private static void planSwordImpacts(FlyingSword sword, ServerPlayer owner, UUID confirmed) {
        Vec3 from = sword.position, to = sword.aim;
        double distance = from.distanceTo(to);
        if (distance < 1.0E-4) return;
        AABB corridor = new AABB(from, to).inflate(SWORD_HIT_RADIUS + 1.6);
        for (LivingEntity victim : sword.level.getEntitiesOfClass(LivingEntity.class, corridor,
                entity -> swordVictim(owner, entity, confirmed))) {
            AABB bounds = victim.getBoundingBox().inflate(SWORD_HIT_RADIUS);
            Optional<Vec3> clip = bounds.clip(from, to);
            double along;
            if (clip.isPresent()) along = clip.get().distanceTo(from);
            else if (bounds.contains(to)) along = distance;
            else continue;
            sword.impacts.put(victim.getUUID(), Math.clamp((int)Math.ceil(along / SWORD_SPEED), 1, sword.landAge));
        }
    }

    /** 飞剑能打的目标：玩家刚打中的那个一定算，其余生物照旧按敌对判定。 */
    private static boolean swordVictim(ServerPlayer owner, LivingEntity entity, UUID confirmed) {
        if (!entity.isAlive() || entity.isSpectator() || entity == owner) return false;
        if (confirmed != null && confirmed.equals(entity.getUUID())) return true;
        return NewSpellSystem.enemy(owner, entity);
    }

    private static void notifySwordMana(ServerPlayer player, int cost) {
        long now = player.level().getGameTime();
        Long last = SWORD_MANA_NOTICE.get(player.getUUID());
        if (last != null && now - last < 20) return;
        SWORD_MANA_NOTICE.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.ten_thousand_swords_no_mana", cost), true);
    }

    /** 飞剑每刻推进：视觉上沿航线冲刺，命中按出剑时算好的刻数结算。 */
    private static void tickSwords(ServerLevel level, long now) {
        Iterator<FlyingSword> iterator = SWORDS.iterator();
        while (iterator.hasNext()) {
            FlyingSword sword = iterator.next();
            if (sword.level != level) continue;
            Entity ownerEntity = level.getEntity(sword.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive() || owner.isSpectator()
                    || owner.serverLevel() != level) {
                iterator.remove();
                continue;
            }
            if (++sword.age >= sword.landAge) {
                // 落地这一帧把还没结算的命中一次补齐，剑雨不会因为目标挪了两步就白飞一趟。
                resolveSwordHits(owner, sword, Integer.MAX_VALUE);
                iterator.remove();
                continue;
            }
            Vec3 delta = sword.aim.subtract(sword.position);
            double distance = delta.length();
            if (distance > 1.0E-4)
                sword.position = distance <= SWORD_SPEED ? sword.aim
                        : sword.position.add(delta.scale(SWORD_SPEED / distance));
            resolveSwordHits(owner, sword, sword.age);
        }
    }

    /** 到点的目标各吃一次 10 点金伤；每命中一个，施法者叠一层剑意。 */
    private static void resolveSwordHits(ServerPlayer owner, FlyingSword sword, int age) {
        if (sword.impacts.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> iterator = sword.impacts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (entry.getValue() > age) continue;
            iterator.remove();
            if (!(sword.level.getEntity(entry.getKey()) instanceof LivingEntity victim)) continue;
            if (!victim.isAlive() || victim.isSpectator()) continue;
            if (!NewSpellSystem.swordDamage(owner, victim, SWORD_DAMAGE, ElementType.METAL)) continue;
            addSwordIntent(owner);
            Vec3 at = victim.getBoundingBox().getCenter();
            sword.level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.4, 0.55, 0.4, 0.22);
            sword.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.35, 0.5, 0.35, 0.04);
            sword.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.35, 0.5, 0.35, 0.3);
            sword.level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 0.75F, 1.35F);
            sword.level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 0.32F, 1.85F);
        }
    }

    /** 裂缝每刻维护：放剑、刷新视觉、到期收起。 */
    private static void tickRifts(ServerLevel level, long now) {
        for (SwordRift rift : new ArrayList<>(RIFTS.values())) {
            if (rift.level != level) continue;
            Entity ownerEntity = level.getEntity(rift.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive() || owner.isSpectator()
                    || owner.serverLevel() != level) {
                endRift(ownerEntity instanceof ServerPlayer player ? player : null, rift, false);
                continue;
            }
            if (now >= rift.until) {
                endRift(owner, rift, true);
                continue;
            }
            if (rift.pending > 0 && now >= rift.nextSwordAt) {
                rift.nextSwordAt = now + 1;
                if (spawnSword(owner, rift)) rift.pending--;
                else rift.pending = 0;
            }
            if (now >= rift.lastVisualAt + RIFT_VISUAL_REFRESH) {
                rift.lastVisualAt = now;
                sendRiftVisual(owner, rift, SpellVisualPayload.SKY_RIFT, RIFT_VISUAL_TICKS);
            }
            if (now % 10 == 0) {
                // 裂缝周围偶尔补一点电弧，站在地面上抬头也能看到它的位置。
                Vec3 p = rift.center.add(rift.direction.scale((owner.getRandom().nextDouble() * 2.0 - 1.0)
                        * RIFT_HALF_LENGTH)).add(rift.side.scale((owner.getRandom().nextDouble() * 2.0 - 1.0)
                        * RIFT_HALF_WIDTH));
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 1.2, 2.0, 1.2, 0.02);
            }
        }
    }

    /** 金钟罩的可视化：每 40 刻向施法者周围刷新一次，走远的人也能看到。 */
    private static void tickBellVisuals(ServerLevel level, long now) {
        for (ServerPlayer player : level.players()) {
            if (!player.hasEffect(NewSpellContent.GOLDEN_BELL)) continue;
            if (now % BELL_VISUAL_REFRESH != 0) continue;
            SpellVisualPayload.send(player, SpellDefinition.GOLDEN_BELL, SpellVisualPayload.GOLDEN_BELL,
                    player.getId(), player.position(), player.position(), BELL_VISUAL_TICKS);
        }
    }

    private static void sendRiftVisual(ServerPlayer owner, SwordRift rift, int phase, int ticks) {
        Vec3 center = rift.center;
        Vec3 end = center.add(rift.direction.scale(RIFT_HALF_LENGTH)).add(0, RIFT_HALF_WIDTH, 0);
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.TEN_THOUSAND_SWORDS.id(), phase,
                owner.getId(), -1, center, end, ticks);
        PacketDistributor.sendToPlayersNear(rift.level, null, owner.getX(), owner.getY(), owner.getZ(), 128.0, payload);
    }

    /** 飞剑的飞行视觉：发给施法者，以及落点周围可能看到的人。 */
    private static void sendSwordVisual(ServerPlayer owner, Vec3 from, Vec3 to, int ticks) {
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.TEN_THOUSAND_SWORDS.id(),
                SpellVisualPayload.FLYING_SWORD, owner.getId(), -1, from, to, ticks);
        PacketDistributor.sendToPlayer(owner, payload);
        if (owner.level() instanceof ServerLevel level)
            PacketDistributor.sendToPlayersNear(level, owner, to.x, to.y, to.z, 96.0, payload);
    }

    /** 裂缝收起：停止出剑并移除可视化；已经放出的飞剑继续飞完。 */
    private static void endRift(ServerPlayer player, SwordRift rift, boolean message) {
        if (RIFTS.get(rift.owner) == rift) RIFTS.remove(rift.owner);
        rift.pending = 0;
        if (player != null) {
            if (message) player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.ten_thousand_swords_end"), true);
            sendRiftVisual(player, rift, SpellVisualPayload.SKY_RIFT_END, 1);
            player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 0.8F);
            SpellSyncPayload.send(player);
        }
    }

    public static int riftTicks(ServerPlayer player) {
        SwordRift rift = RIFTS.get(player.getUUID());
        if (rift == null) return 0;
        return (int)Math.max(0L, rift.until - player.level().getGameTime());
    }

    // ───────────────────────── 事件入口 ─────────────────────────

    /** 只有玩家自己的近战或远程（箭、投掷物）攻击才算“任意攻击”，法术伤害不会反复触发剑雨。 */
    public static boolean isPlayerAttack(ServerPlayer player, DamageSource source) {
        if (source == null) return false;
        if (source.is(DamageTypeTags.IS_PLAYER_ATTACK)) return true;
        return source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() == player;
    }

    public static void tickLevel(ServerLevel level, long now) {
        tickRifts(level, now);
        tickSwords(level, now);
        tickBellVisuals(level, now);
        cleanupNotices(level, now);
    }

    private static void cleanupNotices(ServerLevel level, long now) {
        // 提示节流表只需要保留几秒，避免长时间开服堆积。
        if (now % 200 != 0) return;
        BELL_NOTICE.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null);
        SWORD_MANA_NOTICE.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null);
    }

    /** 下线、死亡或换维度：收起裂缝，并把还在飞的剑一并清掉。 */
    public static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        SwordRift rift = RIFTS.remove(id);
        if (rift != null) sendRiftVisual(player, rift, SpellVisualPayload.SKY_RIFT_END, 1);
        SWORDS.removeIf(sword -> sword.owner.equals(id));
        BELL_NOTICE.remove(id);
        SWORD_MANA_NOTICE.remove(id);
        if (player.hasEffect(NewSpellContent.GOLDEN_BELL)) breakBell(player, player.serverLevel());
    }

    public static void clearAll() {
        RIFTS.clear();
        SWORDS.clear();
        BELL_NOTICE.clear();
        SWORD_MANA_NOTICE.clear();
    }

    private MetalSpells() { }
}
