package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 土系追加法术：磐石与万法千陨。
 *
 * <p>磐石（中级·防护）最多标记三个目标（含自己），为其套上持续 28 秒的护甲外壳：
 * 外壳有 100 点生命，替目标吸收伤害；同时提供护甲 +20、韧性 +12 与击退免疫。</p>
 *
 * <p>万法千陨（高级·伤害）在施法者身边升起三块巨石并悬停十二秒，期间可按 Alt+左键
 * 追加石块；十二秒后石块沿准心抛出，自行校准抛物线砸向目标或准心附近。石块在空中
 * 不会自行碎裂，只有真正砸到生物或落到地面才结算：落地以落点为中心炸出半径三格的
 * 坑，另有 30% 概率溅出 2~6 块小碎石。</p>
 */
public final class EarthSpells {
    // ───────────────────────── 磐石 ─────────────────────────

    private static final String SHELL_KEY = "DimensionalManaStoneShell";
    private static final ResourceLocation SHELL_ARMOR = id("stone_shell_armor");
    private static final ResourceLocation SHELL_TOUGHNESS = id("stone_shell_toughness");
    private static final ResourceLocation SHELL_KNOCKBACK = id("stone_shell_knockback");
    private static final int SHELL_TICKS = 560;
    private static final float SHELL_HEALTH = 100.0F;
    private static final double SHELL_ARMOR_VALUE = 20.0;
    private static final double SHELL_TOUGHNESS_VALUE = 12.0;
    private static final double SHELL_KNOCKBACK_VALUE = 1.0;
    private static final Map<UUID, Long> SHELL_NOTICE = new HashMap<>();

    // ───────────────────────── 万法千陨 ─────────────────────────

    private static final int METEOR_BASE = 3;
    public static final int METEOR_MAX = 48;
    private static final double METEOR_EXTRA_COST = 85.0;
    /** 基础冷却 9 秒；每追加一块再加 2 秒。 */
    private static final int METEOR_COOLDOWN = 180;
    private static final int METEOR_EXTRA_COOLDOWN = 40;
    /** 石块出现后悬停 12 秒再投掷。 */
    private static final int METEOR_SUMMON_TICKS = 240;
    private static final int METEOR_RISE_TICKS = 12;
    private static final double METEOR_RANGE = 64.0;
    private static final float METEOR_DAMAGE = 100.0F;
    /** 落地爆炸：砸掉落点半径 3 格内硬度低于 6 的方块。 */
    private static final int METEOR_CRATER_RADIUS = 3;
    private static final int METEOR_CRATER_LIMIT = 96;
    private static final float METEOR_MAX_HARDNESS = 6.0F;
    private static final double METEOR_IMPACT_RADIUS = 3.0;
    /** 每次落地有 30% 概率溅出 2~6 块小碎石，每块砸中造成 8 点土属性伤害。 */
    private static final float METEOR_DEBRIS_CHANCE = 0.30F;
    private static final float METEOR_DEBRIS_DAMAGE = 8.0F;
    private static final int METEOR_DEBRIS_MIN = 2;
    private static final int METEOR_DEBRIS_MAX = 6;
    private static final double METEOR_AIM_RADIUS = 10.0;

    private static final Map<UUID, MeteorCharge> CHARGES = new HashMap<>();
    private static final List<MeteorFlight> FLIGHTS = new ArrayList<>();
    private static final List<Debris> DEBRIS = new ArrayList<>();
    private static final Map<ServerLevel, Long> LAST_IMPACT_SOUND = new HashMap<>();

    private static final class MeteorCharge {
        private final ServerLevel level;
        private final UUID owner;
        private final long born;
        private final List<MeteorRock> rocks = new ArrayList<>();
        private long throwAt;
        private int extras;

        private MeteorCharge(ServerLevel level, UUID owner, long born) {
            this.level = level;
            this.owner = owner;
            this.born = born;
            this.throwAt = born + METEOR_SUMMON_TICKS;
        }
    }

    private static final class MeteorRock {
        private final MeteorStone entity;
        private final Vec3 home;
        private final double angle;
        private final double radius;
        private final double height;
        private int rise;

        private MeteorRock(MeteorStone entity, Vec3 home, double angle, double radius, double height) {
            this.entity = entity;
            this.home = home;
            this.angle = angle;
            this.radius = radius;
            this.height = height;
        }
    }

    private static final class MeteorFlight {
        private final ServerLevel level;
        private final UUID owner;
        private final MeteorStone entity;
        private final long born;
        private final int duration;
        private final Vec3 start;
        private Vec3 aim;
        private UUID target;
        private final Set<UUID> hit = new HashSet<>();

        private MeteorFlight(ServerLevel level, UUID owner, MeteorStone entity, long born, int duration,
                             Vec3 start, Vec3 aim, UUID target) {
            this.level = level;
            this.owner = owner;
            this.entity = entity;
            this.born = born;
            this.duration = duration;
            this.start = start;
            this.aim = aim;
            this.target = target;
        }
    }

    /** 落地溅出的小碎石：只飞一小段、撞到生物就消失，不破坏方块。 */
    private static final class Debris {
        private final ServerLevel level;
        private final UUID owner;
        private final MeteorStone entity;
        private Vec3 velocity;
        private int life;

        private Debris(ServerLevel level, UUID owner, MeteorStone entity, Vec3 velocity, int life) {
            this.level = level;
            this.owner = owner;
            this.entity = entity;
            this.velocity = velocity;
            this.life = life;
        }
    }

    // ───────────────────────── 磐石结算 ─────────────────────────

    /** 前摇结束：为每个选中的目标套上磐石外壳。 */
    public static void beginBoulderWard(ServerPlayer player, List<LivingEntity> targets) {
        ServerLevel level = player.serverLevel();
        int applied = 0;
        for (LivingEntity target : targets) {
            if (target == null || !target.isAlive()) continue;
            target.addEffect(new MobEffectInstance(NewSpellContent.STONE_SHELL, SHELL_TICKS, 0, false, true, true));
            CompoundTag tag = new CompoundTag();
            tag.putFloat("hp", SHELL_HEALTH);
            tag.putUUID("owner", player.getUUID());
            target.getPersistentData().put(SHELL_KEY, tag);
            applyShellModifiers(target);
            Vec3 center = target.getBoundingBox().getCenter();
            NewSpellSystem.visual(player, SpellDefinition.BOULDER, player.getEyePosition(), center,
                    target.getId(), SHELL_TICKS);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    center.x, center.y, center.z, 22, 0.55, 0.75, 0.55, 0.08);
            level.playSound(null, target.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 0.9F, 0.72F);
            applied++;
        }
        if (applied > 0) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.boulder_cast",
                    applied, (int)SHELL_HEALTH, SHELL_TICKS / 20), true);
            level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.55F, 0.65F);
        }
    }

    /**
     * 磐石外壳吸收伤害：返回剩余要落给目标生命的伤害。
     * 外壳只替目标吃下被打折后的实际伤害，因此护甲和韧性仍然有收益。
     */
    public static float absorbShell(LivingEntity victim, float damage, DamageSource source) {
        if (damage <= 0.0F || victim.level().isClientSide) return damage;
        // 虚空、/kill 之类穿透无敌的伤害不归外壳管，避免出现“杀不死”的异常。
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;
        if (!victim.hasEffect(NewSpellContent.STONE_SHELL)) return damage;
        CompoundTag tag = victim.getPersistentData().getCompound(SHELL_KEY);
        float shell = tag.getFloat("hp");
        if (tag.isEmpty() || shell <= 0.0F) return damage;
        float absorbed = Math.min(shell, damage);
        shell -= absorbed;
        tag.putFloat("hp", shell);
        victim.getPersistentData().put(SHELL_KEY, tag);
        ServerLevel level = victim.level() instanceof ServerLevel server ? server : null;
        if (shell <= 0.001F) {
            breakShell(victim, level);
        } else {
            if (level != null) {
                Vec3 at = victim.getBoundingBox().getCenter();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        at.x, at.y, at.z, 9, 0.45, 0.55, 0.45, 0.07);
                if (level.getGameTime() % 4 == 0)
                    level.playSound(null, victim.blockPosition(), SoundEvents.STONE_HIT, SoundSource.PLAYERS,
                            0.55F, 1.25F);
            }
            if (victim instanceof ServerPlayer player) notifyShell(player, shell);
        }
        return damage - absorbed;
    }

    /** 每刻兜底：外壳到时、被牛奶清掉或被击碎时，属性与残留数据一起收回。 */
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if (!entity.hasEffect(NewSpellContent.STONE_SHELL)) {
            if (entity.getPersistentData().contains(SHELL_KEY)) {
                entity.getPersistentData().remove(SHELL_KEY);
                removeShellModifiers(entity);
                SpellVisualPayload.send(entity, SpellDefinition.BOULDER, SpellVisualPayload.END_ATTACHED,
                        entity.getId(), entity.position(), entity.position(), 1);
            }
            return;
        }
        // 重新登录或其它模组清理属性后，每隔一秒补回外壳的三项属性。
        if (entity.tickCount % 20 == 0) applyShellModifiers(entity);
    }

    private static void applyShellModifiers(LivingEntity target) {
        modify(target, Attributes.ARMOR, SHELL_ARMOR, SHELL_ARMOR_VALUE);
        modify(target, Attributes.ARMOR_TOUGHNESS, SHELL_TOUGHNESS, SHELL_TOUGHNESS_VALUE);
        modify(target, Attributes.KNOCKBACK_RESISTANCE, SHELL_KNOCKBACK, SHELL_KNOCKBACK_VALUE);
    }

    private static void modify(LivingEntity target, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) return;
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeShellModifiers(LivingEntity target) {
        for (AttributeInstance instance : target.getAttributes().getSyncableAttributes()) {
            instance.removeModifier(SHELL_ARMOR);
            instance.removeModifier(SHELL_TOUGHNESS);
            instance.removeModifier(SHELL_KNOCKBACK);
        }
    }

    private static void breakShell(LivingEntity victim, ServerLevel level) {
        victim.getPersistentData().remove(SHELL_KEY);
        victim.removeEffect(NewSpellContent.STONE_SHELL);
        removeShellModifiers(victim);
        if (level != null) {
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    at.x, at.y, at.z, 30, 0.7, 0.85, 0.7, 0.12);
            level.playSound(null, victim.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.75F);
            if (victim instanceof ServerPlayer player)
                player.displayClientMessage(Component.translatable("message.dimensional_mana.boulder_broken"), true);
        }
        SpellVisualPayload.send(victim, SpellDefinition.BOULDER, SpellVisualPayload.END_ATTACHED,
                victim.getId(), victim.position(), victim.position(), 1);
    }

    private static void notifyShell(ServerPlayer player, float shell) {
        long now = player.level().getGameTime();
        Long last = SHELL_NOTICE.get(player.getUUID());
        if (last != null && now - last < 10) return;
        SHELL_NOTICE.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.boulder_shell_left",
                (int)Math.ceil(shell)), true);
    }

    // ───────────────────────── 万法千陨 ─────────────────────────

    /** 前摇结束：三块巨石从地面升起，开始十二秒的追加窗口。 */
    public static void beginMyriadMeteors(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        MeteorCharge old = CHARGES.remove(player.getUUID());
        if (old != null) discardRocks(old);
        MeteorCharge charge = new MeteorCharge(level, player.getUUID(), now);
        CHARGES.put(player.getUUID(), charge);
        for (int i = 0; i < METEOR_BASE; i++) spawnRock(player, charge, i);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.meteor_start",
                charge.rocks.size(), METEOR_MAX, METEOR_SUMMON_TICKS / 20), true);
        level.playSound(null, player.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.15F, 0.72F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                player.getX(), player.getY() + 0.4, player.getZ(), 40, 1.8, 0.2, 1.8, 0.1);
        SpellSyncPayload.send(player);
    }

    /** Alt+左键：消耗 85 魔素追加一块巨石，最多追加 45 块（总数 48），每块让冷却 +2 秒。 */
    public static void addMeteorStone(ServerPlayer player) {
        MeteorCharge charge = CHARGES.get(player.getUUID());
        if (charge == null) return;
        if (charge.level != player.serverLevel()) {
            CHARGES.remove(player.getUUID());
            discardRocks(charge);
            return;
        }
        if (charge.rocks.size() >= METEOR_MAX) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.meteor_full", METEOR_MAX), true);
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, METEOR_EXTRA_COST);
        if (!ManaCheats.hasMana(player, cost)) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.meteor_no_mana",
                    (int)Math.ceil(cost)), true);
            return;
        }
        if (!ManaData.spendMana(player, METEOR_EXTRA_COST)) return;
        charge.extras++;
        spawnRock(player, charge, charge.rocks.size());
        SpellCasting.syncMana(player);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.meteor_added",
                charge.rocks.size(), METEOR_MAX, cooldownSeconds(charge.extras)), true);
        player.playNotifySound(SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 0.75F, 1.35F);
        SpellSyncPayload.send(player);
    }

    /**
     * 再按施法键：立刻停止召唤并投出已经升起的石块（不浪费已经花掉的魔素），
     * 随后按当前追加数量进入冷却。
     *
     * @return 玩家当前是否正在召唤巨石（是则这次按键被消耗掉）
     */
    public static boolean stopMyriadMeteors(ServerPlayer player) {
        MeteorCharge charge = CHARGES.remove(player.getUUID());
        if (charge == null) return false;
        if (charge.level != player.serverLevel()) {
            discardRocks(charge);
            return true;
        }
        throwRocks(player, charge);
        return true;
    }

    public static int meteorRocks(ServerPlayer player) {
        MeteorCharge charge = CHARGES.get(player.getUUID());
        return charge == null ? 0 : charge.rocks.size();
    }

    public static int meteorTicks(ServerPlayer player) {
        MeteorCharge charge = CHARGES.get(player.getUUID());
        if (charge == null) return 0;
        return (int)Math.max(0L, charge.throwAt - player.level().getGameTime());
    }

    public static void tickPlayer(ServerPlayer player, long now) {
        MeteorCharge charge = CHARGES.get(player.getUUID());
        if (charge == null) return;
        if (charge.level != player.serverLevel() || !player.isAlive() || player.isSpectator()) {
            CHARGES.remove(player.getUUID());
            discardRocks(charge);
            return;
        }
        // 被冻结、眩晕等打断时直接把已经升起的石块投出去，避免白白损失魔素。
        if (NewSpellSystem.cannotCast(player)) {
            CHARGES.remove(player.getUUID());
            throwRocks(player, charge);
            return;
        }
        if (now >= charge.throwAt) {
            CHARGES.remove(player.getUUID());
            throwRocks(player, charge);
            return;
        }
        updateHover(player, charge, now);
        if (now % 10 == 0) SpellSyncPayload.send(player);
    }

    private static void spawnRock(ServerPlayer player, MeteorCharge charge, int index) {
        ServerLevel level = charge.level;
        int slot = charge.rocks.size();
        double angle = slot * 2.399963229728653;
        double radius = 1.9 + (slot % 3) * 0.55;
        double height = 2.4 + (slot % 2) * 0.55;
        Vec3 ground = NewSpellSystem.ground(level, player.position().add(Math.cos(angle) * radius, 0.0,
                Math.sin(angle) * radius));
        MeteorStone stone = new MeteorStone(NewSpellContent.METEOR_STONE.get(), level);
        stone.configure(player, ground.add(0, 0.55, 0));
        if (!level.addFreshEntity(stone)) return;
        charge.rocks.add(new MeteorRock(stone, ground.add(0, 0.55, 0), angle, radius, height));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                ground.x, ground.y + 0.18, ground.z, 16, 0.42, 0.15, 0.42, 0.07);
        if (index % 3 == 0)
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS,
                    0.55F, 0.65F);
    }

    /** 悬停阶段：石块绕着施法者缓慢旋转并上下浮动，彼此靠得太近时弹开 0.1 格。 */
    private static void updateHover(ServerPlayer player, MeteorCharge charge, long now) {
        ServerLevel level = charge.level;
        for (int i = 0; i < charge.rocks.size(); i++) {
            MeteorRock rock = charge.rocks.get(i);
            if (rock.rise < METEOR_RISE_TICKS) rock.rise++;
            double angle = rock.angle + now * 0.012;
            Vec3 desired = player.position().add(Math.cos(angle) * rock.radius,
                    rock.height + Math.sin(now * 0.08 + i * 1.7) * 0.14,
                    Math.sin(angle) * rock.radius);
            desired = findOpen(level, desired);
            Vec3 from = rock.home.lerp(desired, ease(rock.rise / (double)METEOR_RISE_TICKS));
            Vec3 current = rock.entity.position();
            Vec3 next = current.lerp(from, 0.35);
            rock.entity.setPos(next.x, next.y, next.z);
            rock.entity.setDeltaMovement(Vec3.ZERO);
            if (now % 8 == 0)
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        next.x, next.y - 0.35, next.z, 2, 0.22, 0.1, 0.22, 0.02);
        }
        // 石块彼此碰撞只弹开 0.1 米，不破坏也不合并。
        for (int a = 0; a < charge.rocks.size(); a++) {
            for (int b = a + 1; b < charge.rocks.size(); b++) {
                MeteorStone first = charge.rocks.get(a).entity;
                MeteorStone second = charge.rocks.get(b).entity;
                Vec3 delta = second.position().subtract(first.position());
                double distance = delta.length();
                if (distance >= 1.0) continue;
                Vec3 push = distance < 1.0E-4 ? new Vec3(1, 0, 0) : delta.scale(1.0 / distance);
                // 两块各退 0.05 格，合起来正好弹开 0.1 格。
                Vec3 shift = push.scale(0.05);
                first.setPos(first.position().subtract(shift));
                second.setPos(second.position().add(shift));
            }
        }
    }

    private static Vec3 findOpen(ServerLevel level, Vec3 desired) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Vec3 point = desired.add(0, attempt * 0.5, 0);
            BlockPos pos = BlockPos.containing(point);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
            if (level.noCollision(new AABB(point.x - 0.4, point.y - 0.4, point.z - 0.4,
                    point.x + 0.4, point.y + 0.4, point.z + 0.4))) return point;
        }
        return desired;
    }

    /** 投掷：准心所指生物优先，否则在准心落点半径 10 格内随机选落点。 */
    private static void throwRocks(ServerPlayer player, MeteorCharge charge) {
        ServerLevel level = charge.level;
        LivingEntity focused = focusedTarget(player);
        Vec3 crosshair = crosshairPoint(player);
        for (MeteorRock rock : charge.rocks) {
            Vec3 aim;
            UUID target = null;
            if (focused != null) {
                aim = focused.getBoundingBox().getCenter().add(
                        (level.random.nextDouble() - 0.5) * 0.6,
                        (level.random.nextDouble() - 0.5) * 0.4,
                        (level.random.nextDouble() - 0.5) * 0.6);
                target = focused.getUUID();
            } else {
                aim = randomLanding(level, crosshair);
            }
            // 随机落点可能落在准心点外 10 格，这里统一夹回 64 格射程内。
            Vec3 eye = player.getEyePosition();
            if (aim.distanceTo(eye) > METEOR_RANGE)
                aim = eye.add(aim.subtract(eye).normalize().scale(METEOR_RANGE));
            Vec3 start = rock.entity.position();
            int duration = Mth.clamp((int)(start.distanceTo(aim) * 1.0) + 10, 14, 70);
            FLIGHTS.add(new MeteorFlight(level, player.getUUID(), rock.entity, level.getGameTime(), duration,
                    start, aim, target));
        }
        player.displayClientMessage(Component.translatable("message.dimensional_mana.meteor_throw",
                charge.rocks.size()), true);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                1.0F, 0.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                0.55F, 0.55F);
        SpellCasting.startCooldown(player, SpellDefinition.MYRIAD_METEORS, cooldownTicks(charge.extras));
        SpellCasting.syncMana(player);
        SpellSyncPayload.send(player);
    }

    private static LivingEntity focusedTarget(ServerPlayer player) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof LivingEntity living && living.isAlive() && living != player
                        && NewSpellSystem.enemy(player, living), METEOR_RANGE);
        return hit instanceof EntityHitResult result && result.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static Vec3 crosshairPoint(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(METEOR_RANGE));
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static Vec3 randomLanding(ServerLevel level, Vec3 focus) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        // sqrt 让落点在圆内均匀分布，而不是全挤在准心附近。
        double radius = Math.sqrt(level.random.nextDouble()) * METEOR_AIM_RADIUS;
        double x = focus.x + Math.cos(angle) * radius;
        double z = focus.z + Math.sin(angle) * radius;
        Vec3 from = new Vec3(x, focus.y + 12.0, z);
        HitResult hit = level.clip(new ClipContext(from, from.add(0, -28.0, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? new Vec3(x, focus.y, z) : hit.getLocation();
    }

    private static int cooldownTicks(int extras) {
        return METEOR_COOLDOWN + extras * METEOR_EXTRA_COOLDOWN;
    }

    private static String cooldownSeconds(int extras) {
        return String.format(java.util.Locale.ROOT, "%.0f", cooldownTicks(extras) / 20.0);
    }

    // ───────────────────────── 飞行与命中 ─────────────────────────

    public static void tickLevel(ServerLevel level, long now) {
        tickFlights(level, now);
        tickDebris(level);
    }

    private static void tickFlights(ServerLevel level, long now) {
        Iterator<MeteorFlight> iterator = FLIGHTS.iterator();
        while (iterator.hasNext()) {
            MeteorFlight flight = iterator.next();
            if (flight.level != level) continue;
            if (!(level.getEntity(flight.owner) instanceof ServerPlayer owner) || !owner.isAlive()) {
                flight.entity.discard();
                iterator.remove();
                continue;
            }
            // 准心锁定的生物还活着就继续校準落点，让抛物线跟着目标走。
            if (flight.target != null) {
                Entity locked = level.getEntity(flight.target);
                if (locked instanceof LivingEntity living && living.isAlive() && NewSpellSystem.enemy(owner, living))
                    flight.aim = living.getBoundingBox().getCenter();
                else
                    flight.target = null;
            }
            double t = Math.clamp((now - flight.born) / (double)flight.duration, 0.0, 1.0);
            double distance = flight.start.distanceTo(flight.aim);
            double lift = Math.min(12.0, 2.0 + distance * 0.18);
            Vec3 pos = flight.start.lerp(flight.aim, t).add(0, 4.0 * t * (1.0 - t) * lift, 0);
            Vec3 last = flight.entity.position();
            flight.entity.setPos(pos.x, pos.y, pos.z);
            flight.entity.setDeltaMovement(Vec3.ZERO);
            // 飞行途中只认生物：撞到谁就在谁身上结算，撞不到就一直飞到落点。
            // 沿途方块不再让石块碎裂，所以目标远、滞空久也不会半路炸掉。
            LivingEntity direct = contact(owner, last, pos);
            if (direct != null) {
                impact(owner, flight, direct.getBoundingBox().getCenter());
                iterator.remove();
                continue;
            }
            if (t >= 1.0) {
                impact(owner, flight, flight.aim);
                iterator.remove();
            }
        }
    }

    /** 这一帧从 from 飞到 to 的路上撞到的最近生物；没撞到返回 null。 */
    private static LivingEntity contact(ServerPlayer owner, Vec3 from, Vec3 to) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity victim : owner.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(1.1), entity -> NewSpellSystem.enemy(owner, entity))) {
            Optional<Vec3> hit = victim.getBoundingBox().inflate(0.3).clip(from, to);
            if (hit.isEmpty()) continue;
            double distance = from.distanceTo(hit.get());
            if (distance < best) {
                best = distance;
                nearest = victim;
            }
        }
        return nearest;
    }

    private static void impact(ServerPlayer owner, MeteorFlight flight, Vec3 at) {
        ServerLevel level = flight.level;
        for (LivingEntity victim : NewSpellSystem.enemies(owner, at, METEOR_IMPACT_RADIUS)) {
            if (!flight.hit.add(victim.getUUID())) continue;
            NewSpellSystem.damage(owner, victim, METEOR_DAMAGE, ElementType.EARTH);
        }
        crater(level, owner, at);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                at.x, at.y, at.z, 34, 1.0, 0.55, 1.0, 0.14);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 3, 0.7, 0.4, 0.7, 0.0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.4, at.z, 16, 1.1, 0.5, 1.1, 0.2);
        long now = level.getGameTime();
        long last = LAST_IMPACT_SOUND.getOrDefault(level, -100L);
        if (now - last >= 2) {
            LAST_IMPACT_SOUND.put(level, now);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    0.95F, 0.72F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        // 三成概率从坑口再崩出一把碎石子，砸中谁就给谁补 8 点土伤。
        if (level.random.nextFloat() < METEOR_DEBRIS_CHANCE) {
            int count = METEOR_DEBRIS_MIN + level.random.nextInt(METEOR_DEBRIS_MAX - METEOR_DEBRIS_MIN + 1);
            sprayDebris(level, owner, at, count);
        }
        SpellVisualPayload.send(owner, SpellDefinition.MYRIAD_METEORS, SpellVisualPayload.METEOR_IMPACT,
                -1, at, at, 20);
        flight.entity.discard();
    }

    /**
     * 落地小爆炸：以落点为中心砸掉半径 3 格内的方块，形成一个坑。
     * 基岩、黑曜石之类硬度 ≥6 的方块与液体不受影响；受保护的区域只跳过，不会报错。
     */
    private static void crater(ServerLevel level, ServerPlayer owner, Vec3 at) {
        BlockPos center = BlockPos.containing(at);
        int radius = METEOR_CRATER_RADIUS;
        double rangeSq = (radius + 0.5) * (radius + 0.5);
        List<BlockPos> targets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness < 0.0F || hardness >= METEOR_MAX_HARDNESS) continue;
                    targets.add(pos);
                }
            }
        }
        // 名额不够时优先炸坑心，坑的形状才不会歪。
        targets.sort(Comparator.comparingDouble(pos -> {
            double ex = pos.getX() + 0.5 - at.x;
            double ey = pos.getY() + 0.5 - at.y;
            double ez = pos.getZ() + 0.5 - at.z;
            return ex * ex + ey * ey + ez * ez;
        }));
        int broken = 0;
        for (BlockPos pos : targets) {
            if (broken >= METEOR_CRATER_LIMIT) break;
            BlockState state = level.getBlockState(pos);
            if (!level.destroyBlock(pos, false, owner)) continue;
            broken++;
            if (broken <= 16)
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.04);
        }
    }

    /** 从坑口朝四面八方崩出 count 块小碎石。 */
    private static void sprayDebris(ServerLevel level, ServerPlayer owner, Vec3 at, int count) {
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            Vec3 direction = new Vec3(Math.cos(angle) * (0.5 + level.random.nextDouble() * 0.6),
                    0.55 + level.random.nextDouble() * 0.55,
                    Math.sin(angle) * (0.5 + level.random.nextDouble() * 0.6)).normalize();
            Vec3 start = at.add(0, 0.35, 0);
            MeteorStone stone = new MeteorStone(NewSpellContent.METEOR_STONE.get(), level);
            stone.configureDebris(owner, start);
            if (!level.addFreshEntity(stone)) continue;
            DEBRIS.add(new Debris(level, owner.getUUID(), stone, direction.scale(0.62),
                    14 + level.random.nextInt(7)));
        }
    }

    /** 小碎石每刻前进一步：撞到生物就结算 8 点土伤并消失，撞到方块则只是消失。 */
    private static void tickDebris(ServerLevel level) {
        Iterator<Debris> iterator = DEBRIS.iterator();
        while (iterator.hasNext()) {
            Debris debris = iterator.next();
            if (debris.level != level) continue;
            if (!(level.getEntity(debris.owner) instanceof ServerPlayer owner) || !owner.isAlive()) {
                debris.entity.discard();
                iterator.remove();
                continue;
            }
            if (debris.life-- <= 0) {
                debris.entity.discard();
                iterator.remove();
                continue;
            }
            Vec3 next = debris.entity.position().add(debris.velocity);
            debris.velocity = debris.velocity.add(0, -0.035, 0);
            LivingEntity victim = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(next, next).inflate(0.9), entity -> NewSpellSystem.enemy(owner, entity))) {
                AABB box = candidate.getBoundingBox().inflate(0.3);
                if (!box.contains(next)) continue;
                double distance = box.distanceToSqr(next);
                if (distance >= best) continue;
                best = distance;
                victim = candidate;
            }
            if (victim != null) {
                NewSpellSystem.damage(owner, victim, METEOR_DEBRIS_DAMAGE, ElementType.EARTH);
                debrisPuff(level, next);
                debris.entity.discard();
                iterator.remove();
                continue;
            }
            if (!level.noCollision(new AABB(next.x - 0.16, next.y - 0.16, next.z - 0.16,
                    next.x + 0.16, next.y + 0.16, next.z + 0.16))) {
                debrisPuff(level, next);
                debris.entity.discard();
                iterator.remove();
                continue;
            }
            debris.entity.setPos(next.x, next.y, next.z);
            debris.entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void debrisPuff(ServerLevel level, Vec3 at) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                at.x, at.y, at.z, 6, 0.18, 0.18, 0.18, 0.04);
    }

    private static void discardRocks(MeteorCharge charge) {
        for (MeteorRock rock : charge.rocks) rock.entity.discard();
        charge.rocks.clear();
    }

    private static double ease(double value) {
        double t = Math.clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, path);
    }

    // ───────────────────────── 生命周期 ─────────────────────────

    public static void clear(ServerPlayer player) {
        MeteorCharge charge = CHARGES.remove(player.getUUID());
        if (charge != null) discardRocks(charge);
        Iterator<MeteorFlight> flights = FLIGHTS.iterator();
        while (flights.hasNext()) {
            MeteorFlight flight = flights.next();
            if (!flight.owner.equals(player.getUUID())) continue;
            flight.entity.discard();
            flights.remove();
        }
        Iterator<Debris> debris = DEBRIS.iterator();
        while (debris.hasNext()) {
            Debris piece = debris.next();
            if (!piece.owner.equals(player.getUUID())) continue;
            piece.entity.discard();
            debris.remove();
        }
        SHELL_NOTICE.remove(player.getUUID());
        // 下线、死亡或换维度时不把外壳带去别的世界。
        if (player.hasEffect(NewSpellContent.STONE_SHELL))
            breakShell(player, player.level() instanceof ServerLevel level ? level : null);
    }

    public static void clearAll() {
        for (MeteorCharge charge : CHARGES.values()) discardRocks(charge);
        CHARGES.clear();
        for (MeteorFlight flight : FLIGHTS) flight.entity.discard();
        FLIGHTS.clear();
        for (Debris piece : DEBRIS) piece.entity.discard();
        DEBRIS.clear();
        SHELL_NOTICE.clear();
        LAST_IMPACT_SOUND.clear();
    }

    private EarthSpells() { }
}
