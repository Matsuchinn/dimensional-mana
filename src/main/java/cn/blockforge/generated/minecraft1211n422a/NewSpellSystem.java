package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.util.*;

/** 新法术的服务器结算；持续区域按世界时间清理，状态效果随实体存档。 */
public final class NewSpellSystem {
    private record Zone(ServerLevel level, UUID owner, Vec3 center, double radius, long until, SpellDefinition spell, long born, Set<UUID> periodicHits) { }
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    private static final List<Volley> VOLLEYS = new ArrayList<>();
    private static final int BARRIER_MAX = 9;
    private static final int BARRIER_LIFETIME = 300;
    private static final int BARRIER_INTERVAL = 60;
    private static final double BARRIER_RANGE = 50.0;
    private static final double BARRIER_MANA_COST = 150.0;
    private static final double BARRIER_DISTANCE = 3.2;
    private static final double BARRIER_RING = 1.85;
    private static final double BARRIER_CORE_RADIUS = 0.85;
    private static final double BARRIER_SEAL_RADIUS = 0.48;
    private static final List<Barrier> BARRIERS = new ArrayList<>();
    private static final Map<UUID, BarrierField> FIELDS = new HashMap<>();
    private static final double CAGE_RADIUS = 3.0;
    private static final int CAGE_DURATION = 240;
    private static final double CAGE_HEALTH = 20.0;
    private static final List<FlameCage> CAGES = new ArrayList<>();
    private static final List<CageBreak> PENDING_CAGE_BREAKS = new ArrayList<>();
    private static final Map<UUID, BlazingSun> SUNS = new HashMap<>();
    private static final double SUN_CHARGE_COST = 90.0;
    /** 炽阳之怒烧出的岩浆在 10 秒后凝固成黑石。 */
    private static final List<LavaScorch> LAVA_SCORCHES = new ArrayList<>();
    private static final int LAVA_COOL_TICKS = 200;
    private static final int LAVA_COOL_PER_TICK = 512;
    /** 涌泉之报：半径 4 格内友方每秒恢复 4 点生命，持续 12 秒，多座泉水可叠加。 */
    private static final double SPRING_RADIUS = 4.0;
    private static final int SPRING_DURATION = 240;
    private static final float SPRING_HEAL = 4.0F;
    private static final int SPRING_HEAL_INTERVAL = 20;
    private static final List<Spring> SPRINGS = new ArrayList<>();
    /** 深渊龙击：高压水柱的几何与结算参数。 */
    private static final double BEAM_WIDTH = 0.675;
    private static final double BEAM_RANGE = 36.0;
    private static final double BEAM_MANA_PER_TICK = 4.5;
    private static final int BEAM_MAX_TICKS = 600;
    private static final float BEAM_INITIAL_DAMAGE = 48.0F;
    private static final float BEAM_PULSE_DAMAGE = 20.0F;
    private static final int BEAM_PULSE_INTERVAL = 10;
    private static final double BEAM_TURN_DEGREES_PER_TICK = 30.0 / 20.0;
    /** 水柱贴住方块 1 秒后开始破拆，之后每 0.5 秒再打掉一批。 */
    private static final int BEAM_BREAK_WARMUP = 20;
    private static final int BEAM_BREAK_INTERVAL = 10;
    /** 水柱离开方块后的宽限：挖开一层时命中点会短暂落在空气里，进度不能立刻清零。 */
    private static final int BEAM_BREAK_GRACE = 20;
    /** 每一轮破坏以命中点为中心、半径 2 格范围内的方块。 */
    private static final double BEAM_BREAK_RADIUS = 2.0;
    /** 单轮最多打掉多少格，避免一次掏空一大片导致卡顿。 */
    private static final int BEAM_BREAK_PER_CYCLE = 16;
    private static final float BEAM_MAX_BLOCK_HARDNESS = 3.0F;
    /** 花草、树叶这类一冲就散的东西：水柱扫过直接清掉，不等压制。 */
    private static final int BEAM_FOLIAGE_INTERVAL = 2;
    private static final int BEAM_FOLIAGE_LIMIT = 32;
    private static final double BEAM_FOLIAGE_RADIUS = 0.95;
    /** 淬火判定比水柱本体略宽一点，让岩浆池表面也能被扫到。 */
    private static final double BEAM_QUENCH_RADIUS = BEAM_WIDTH + 0.55;
    private static final int BEAM_QUENCH_INTERVAL = 2;
    private static final int BEAM_QUENCH_LIMIT = 64;
    private static final double BEAM_SIGIL_DISTANCE = 1.35;
    private static final Map<UUID, DragonBeam> DRAGONS = new HashMap<>();
    private static final ResourceLocation DRAGON_SLOW = id("abyss_dragon_slow");
    /** 零下领域：前摇后先出现一层高级法阵，Alt+左键可继续向上叠层。 */
    private static final int DOMAIN_MAX_LAYERS = 12;
    private static final double DOMAIN_LAYER_COST = 144.0;
    private static final double DOMAIN_BASE_RADIUS = 12.0;
    private static final float DOMAIN_BASE_DAMAGE = 55.0F;
    private static final double DOMAIN_BASE_FREEZE_HEALTH = 100.0;
    private static final int DOMAIN_BASE_FREEZE_TICKS = 400;
    /** 释放后等待再次按施法键的时间，超时自动释放。 */
    private static final int DOMAIN_FIELD_LIFETIME = 600;
    private static final double DOMAIN_LAYER_DAMAGE = 0.18;
    private static final double DOMAIN_LAYER_HEALTH = 0.18;
    private static final double DOMAIN_LAYER_DURATION = 0.08;
    private static final float DOMAIN_BASE_DAMAGE_TAKEN = 0.25F;
    private static final float DOMAIN_LAYER_DAMAGE_TAKEN = 0.03F;
    private static final float DOMAIN_BASE_REACTION = 0.35F;
    private static final float DOMAIN_LAYER_REACTION = 0.07F;
    private static final Map<UUID, DomainField> DOMAINS = new HashMap<>();
    private static final ResourceLocation DOMAIN_SLOW = id("subzero_domain_slow");
    /** 伏特状态：12 秒极速带电，每 0.5 秒电击贴到身上的敌人。 */
    private static final int VOLT_DURATION = 240;
    private static final int VOLT_ZAP_INTERVAL = 10;
    private static final float VOLT_ZAP_DAMAGE = 10.0F;
    /** 判定半径按“贴到身上”算，比本体略宽一点，跑过身边就能电到。 */
    private static final double VOLT_ZAP_RADIUS = 1.9;
    private static final float VOLT_DAMAGE_TAKEN = 0.5F;
    private static final Map<UUID, Long> VOLT_NEXT_ZAP = new HashMap<>();
    /** 天雷滚滚：大型雷电球跟随施法者 30 秒，每 8 刻由每颗雷球各自电击最多三个随机目标。 */
    private static final int TEMPEST_DURATION = 600;
    private static final int TEMPEST_STRIKE_INTERVAL = 8;
    private static final int TEMPEST_MAX_TARGETS = 3;
    private static final double TEMPEST_RANGE = 9.0;
    private static final float TEMPEST_STRIKE_DAMAGE = 8.0F;
    /** 雷击残影时长：1 秒。 */
    private static final int TEMPEST_STRIKE_TRACE = 20;
    private static final float TEMPEST_POLARITY_CHANCE = 0.55F;
    private static final float TEMPEST_POLARITY_REFRESH = 0.25F;
    private static final int TEMPEST_POLARITY_TICKS = 300;
    private static final float TEMPEST_POLARITY_WEAKNESS = 1.34F;
    private static final float TEMPEST_POLARITY_OUTPUT = 0.58F;
    /** 前摇期间 Alt+左键追加：每颗 555 魔素，最多两颗，各多一颗略小的雷球。 */
    private static final double TEMPEST_EXTRA_COST = 555.0;
    private static final int TEMPEST_MAX_EXTRA = 2;
    /** 雷球结束后先吸附 4 秒，再电磁爆炸。 */
    private static final int TEMPEST_PULL_TICKS = 80;
    private static final double TEMPEST_BLAST_RANGE = 16.0;
    private static final float TEMPEST_BLAST_DAMAGE = 88.0F;
    /** 雷球比头顶再高一点，光球不会糊住宿主的视野。 */
    private static final double TEMPEST_BALL_HEIGHT = 2.15;
    private static final Map<UUID, TempestCharge> TEMPEST_CHARGES = new HashMap<>();
    private static final Map<UUID, TempestField> TEMPESTS = new HashMap<>();
    private static final Map<UUID, TempestBlast> TEMPEST_BLASTS = new HashMap<>();
    private static final class LavaScorch {
        final ServerLevel level; final long at; final List<BlockPos> positions; int cursor;
        LavaScorch(ServerLevel level, long at, List<BlockPos> positions) {
            this.level = level; this.at = at; this.positions = positions;
        }
    }
    private static final class Barrier {
        final ServerLevel level; final UUID owner; final Vec3 position; final Vec3 normal;
        final double radius; final long until; long nextShot;
        Barrier(ServerLevel level, UUID owner, Vec3 position, Vec3 normal, double radius, long until, long nextShot) {
            this.level = level; this.owner = owner; this.position = position; this.normal = normal;
            this.radius = radius; this.until = until; this.nextShot = nextShot;
        }
    }
    /** 一次施法最多展开九个法阵；法阵各自计时，法阵场只负责继续加阵与结束施法。 */
    private static final class BarrierField {
        final ServerLevel level; final UUID owner; final Vec3 origin; final Vec3 forward; final Vec3 right;
        final long born; final long expiresAt;
        int count;
        BarrierField(ServerLevel level, UUID owner, Vec3 origin, Vec3 forward, Vec3 right, long born, long expiresAt) {
            this.level = level; this.owner = owner; this.origin = origin; this.forward = forward; this.right = right;
            this.born = born; this.expiresAt = expiresAt;
        }
    }
    private record Volley(ServerLevel level, UUID owner, Vec3 start, Vec3 direction, long at, int index) { }
    /** 烈焰囚笼的服务器状态；trapped 记录被关进笼子、需要拉回边界的敌人。 */
    private static final class FlameCage {
        final ServerLevel level; final UUID owner; final Vec3 center; final long born; final long until;
        final Set<UUID> trapped = new HashSet<>();
        double health = CAGE_HEALTH;
        boolean broken;
        FlameCage(ServerLevel level, UUID owner, Vec3 center, long born, long until) {
            this.level = level; this.owner = owner; this.center = center; this.born = born; this.until = until;
        }
    }
    private record CageBreak(FlameCage cage, long at) { }
    /** 涌泉之报留下的一眼泉水；多座泉水各自计时，重叠时治疗自然叠加。 */
    private static final class Spring {
        final ServerLevel level; final UUID owner; final Vec3 center; final long born; final long until;
        Spring(ServerLevel level, UUID owner, Vec3 center, long born, long until) {
            this.level = level; this.owner = owner; this.center = center; this.born = born; this.until = until;
        }
    }
    /** 深渊龙击的持续喷射状态：转向、耗魔、伤害节拍与破方块进度都记在这里。 */
    private static final class DragonBeam {
        final ServerLevel level; final UUID owner; final long born;
        Vec3 aim;
        Vec3 start; Vec3 end;
        boolean blocked;
        /** 水柱正压在哪个方块上，以及已经连续压了多久（满 1 秒就开始破拆）。 */
        BlockPos heatPos;
        int heatTicks;
        /** 上一次真正压住可破坏方块的时刻：短暂放空时靠它保住挖掘进度。 */
        long lastDig;
        /** 压在打不穿的方块上多久，以及是否已经提示过。 */
        int hardTicks;
        boolean hardNotified;
        boolean breachNotified;
        long nextBreak;
        /** 持续破拆时动作栏提示的节流时刻。 */
        long nextDigNotice;
        long nextPulse;
        long nextVisual;
        long nextQuenchNotice;
        final Map<UUID, Long> contact = new HashMap<>();
        DragonBeam(ServerLevel level, UUID owner, Vec3 aim, long born) {
            this.level = level; this.owner = owner; this.aim = aim; this.born = born;
            this.nextBreak = born; this.nextPulse = born; this.nextVisual = born;
            this.lastDig = born;
        }
    }
    /** 零下领域的叠层状态：释放前记层数，超时自动释放。 */
    private static final class DomainField {
        final ServerLevel level; final UUID owner; final long born; final long expiresAt;
        int layers;
        DomainField(ServerLevel level, UUID owner, long born, long expiresAt) {
            this.level = level; this.owner = owner; this.born = born; this.expiresAt = expiresAt;
        }
    }
    private static final class Dash {
        final ServerLevel level; final SpellDefinition spell; final Vec3 direction; final Set<UUID> hit = new HashSet<>();
        double remaining;
        Dash(ServerPlayer player, SpellDefinition spell, double distance) {
            level = player.serverLevel(); this.spell = spell; direction = player.getLookAngle().normalize(); remaining = distance;
        }
    }
    /** 天雷滚滚的前摇追加状态：前摇结束才按 extras 生成对应数量的额外雷球。 */
    private static final class TempestCharge {
        final ServerLevel level; final UUID owner;
        int extras;
        TempestCharge(ServerLevel level, UUID owner) { this.level = level; this.owner = owner; }
    }
    /** 天雷滚滚一轮内排队等待结算的单次攻击；ballIndex 0 为主球，1、2 为附加球。 */
    private record TempestStrike(UUID target, int ballIndex, long at) { }
    /** 一枚已释放的雷球：跟随施法者、定期电击，到期后转成电磁吸附。 */
    private static final class TempestField {
        final ServerLevel level; final UUID owner; final long born; final long until;
        final ArrayDeque<TempestStrike> pendingStrikes = new ArrayDeque<>();
        int extras; long nextStrike; long nextVisual;
        TempestField(ServerLevel level, UUID owner, long born, long until, int extras) {
            this.level = level; this.owner = owner; this.born = born; this.until = until; this.extras = extras;
            this.nextStrike = born + TEMPEST_STRIKE_INTERVAL; this.nextVisual = born;
        }
    }
    /** 电磁爆炸前的 4 秒吸附：中心固定，到期爆炸并移除。 */
    private static final class TempestBlast {
        final ServerLevel level; final UUID owner; final Vec3 center; final long until;
        TempestBlast(ServerLevel level, UUID owner, Vec3 center, long until) {
            this.level = level; this.owner = owner; this.center = center; this.until = until;
        }
    }
    private static final String LOCK = "DimensionalManaSpellLock";
    private static final String COFFIN = "DimensionalManaIceCoffin";

    public static boolean enemy(LivingEntity owner, LivingEntity target) {
        if (owner == target || !target.isAlive() || target.isSpectator() || target.isAlliedTo(owner) || owner.isAlliedTo(target)) return false;
        if (target instanceof Player p && (p.isCreative() || (owner instanceof Player player && !player.canHarmPlayer(p)))) return false;
        if (target instanceof SpellSummon summon && owner.getUUID().equals(summon.ownerId())) return false;
        if (target instanceof TamableAnimal tame && owner.getUUID().equals(tame.getOwnerUUID())) return false;
        return owner.canAttack(target);
    }
    public static boolean immobilized(LivingEntity target) {
        return target.hasEffect(NewSpellContent.WATER_PRISON) || target.hasEffect(NewSpellContent.ICE_COFFIN)
                || target.hasEffect(NewSpellContent.STUN) || ElementReactionSystem.deepFrozen(target);
    }
    public static boolean cannotCast(LivingEntity target) {
        // 奥术沉默期间任何法术都放不出来，包括正在维持的持续法术。
        // 反魔法区域（末影龙特殊龙息落点）内同样无法施法。
        return immobilized(target) || target.hasEffect(NewSpellContent.TINNITUS)
                || target.hasEffect(NewSpellContent.ARCANE_SILENCE)
                || MobCombatSystem.inAntiMagicZone(target);
    }
    public static void effect(LivingEntity entity, Holder<MobEffect> effect, int ticks) {
        entity.addEffect(new MobEffectInstance(effect, ticks, 0, false, true, true));
    }
    /** 带等级的版本：招架破绽会把「见切」的加成写进效果等级里。 */
    public static void effect(LivingEntity entity, Holder<MobEffect> effect, int ticks, int amplifier) {
        entity.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amplifier), false, true, true));
    }
    public static void control(LivingEntity caster, LivingEntity target, Holder<MobEffect> effect, int ticks) {
        effect(target, effect, ticks);
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", target.getX()); tag.putDouble("y", target.getY()); tag.putDouble("z", target.getZ());
        target.getPersistentData().put(LOCK, tag);
        target.stopUsingItem();
        if (target instanceof Mob mob) {
            if (!target.getPersistentData().contains("DimensionalManaSpellOldNoAI"))
                target.getPersistentData().putBoolean("DimensionalManaSpellOldNoAI", mob.isNoAi());
            mob.setNoAi(true); mob.getNavigation().stop();
        }
    }
    public static void cast(ServerPlayer player, SpellDefinition spell, List<LivingEntity> targets) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        LivingEntity target = targets.isEmpty() ? null : targets.getFirst();
        switch (spell) {
            case FLAME_SLASH -> projectile(player, spell, player.position().add(0, 1, 0), player.getLookAngle(), 0);
            case PYROBLAST, WATER_PYTHON -> projectile(player, spell, player.getEyePosition(), player.getLookAngle(), 0);
            case WATER_PRISON -> {
                control(player, target, NewSpellContent.WATER_PRISON, 60);
                ElementReactionSystem.apply(player, target, ElementType.WATER);
                visual(player, spell, target, 60);
            }
            case ICE_SLIDE, METAL_DASH -> {
                DASHES.put(player.getUUID(), new Dash(player, spell, spell == SpellDefinition.ICE_SLIDE ? 8 : 10));
                visual(player, spell, player.position(), safeEnd(player, player.getLookAngle(), spell == SpellDefinition.ICE_SLIDE ? 8 : 10), -1, 16);
            }
            case LIGHTNING_BLINK -> {
                Vec3 start = player.position();
                Vec3 end = safeEnd(player, player.getLookAngle(), 8);
                pathDamage(player, start.add(0, 0.9, 0), end.add(0, 0.9, 0), 0.65, 4, ElementType.LIGHTNING, new HashSet<>(), 0);
                player.teleportTo(end.x, end.y, end.z); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
                visual(player, spell, start.add(0, 0.8, 0), end.add(0, 0.8, 0), -1, 16);
            }
            case ICE_COFFIN -> {
                control(player, target, NewSpellContent.ICE_COFFIN, 100);
                CompoundTag tag = new CompoundTag(); tag.putUUID("owner", player.getUUID()); tag.putFloat("shell", 20);
                target.getPersistentData().put(COFFIN, tag);
                ElementReactionSystem.apply(player, target, ElementType.ICE);
                SpellEnvironmentSystem.sustainIce(target, 100);
                visual(player, spell, target, 100);
            }
            case THUNDER_STRIKE -> {
                for (LivingEntity victim : enemies(player, target.position(), 3)) damage(player, victim, 26, ElementType.LIGHTNING);
                if (target.isAlive() && player.getRandom().nextFloat() < 0.60F) control(player, target, NewSpellContent.STUN, 80);
                visual(player, spell, target.position().add(0, 10, 0), target.position(), -1, 28);
            }
            case LIGHTNESS -> {
                effect(target, NewSpellContent.LIGHTNESS, 800);
                target.addEffect(new MobEffectInstance(MobEffects.JUMP, 800, 1));
                visual(player, spell, target, 100);
            }
            case GALE_SLASHES -> {
                for (int i = 0; i < 3; i++) VOLLEYS.add(new Volley(level, player.getUUID(), player.getEyePosition(), player.getLookAngle(), now + i * 12, i));
                visual(player, spell, player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(12)), -1, 36);
            }
            case MUD_SWAMP -> {
                zone(player, target.position(), 3, 160, spell);
                visual(player, spell, player.getEyePosition(), target.position(), -1, 160);
            }
            case ROCK_ORBS -> {
                Vec3 dir = player.getLookAngle(); Vec3 right = new Vec3(-dir.z, 0, dir.x).normalize();
                if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
                Vec3 forward = new Vec3(dir.x, 0, dir.z).normalize();
                if (forward.lengthSqr() < 0.01) forward = new Vec3(0, 0, 1);
                for (int i = 0; i < 3; i++) {
                    Vec3 p = ground(level, player.position().add(forward).add(right.scale((i - 1) * 1.15)));
                    projectile(player, spell, p.add(0, -0.5, 0), dir, i);
                }
                visual(player, spell, player.getEyePosition(), player.position().add(forward), -1, 24);
            }
            case VINE_SERVANTS -> { SpellSummon.summon(player, NewSpellContent.VINE_SERVANT.get(), 2); visual(player, spell, player, 40); }
            case SWORD_SUMMON -> { SpellSummon.summon(player, NewSpellContent.FLYING_SWORD.get(), 1); visual(player, spell, player, 40); }
            case POISON_VINE -> { SpellSummon.summon(player, NewSpellContent.POISON_TENTACLE.get(), 1); visual(player, spell, player, 30); }
            case LIFE_BLESSING -> {
                effect(target, NewSpellContent.LIFE_BLESSING, 800);
                visual(player, spell, target, 100);
            }
            case GLIMMER -> {
                effect(target, NewSpellContent.GLIMMER, 1200);
                target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0));
                TemporarySpellLight.illuminate(target);
                visual(player, spell, target, 100);
            }
            case HOLY_BEAM -> {
                Vec3 start = player.getEyePosition(), end = blockEnd(player, start, start.add(player.getLookAngle().scale(20)));
                List<LivingEntity> hits = pathTargets(player, start, end, 0.3);
                if (!hits.isEmpty()) {
                    LivingEntity hit = hits.getFirst(); end = hit.getBoundingBox().getCenter();
                    damage(player, hit, 24, ElementType.LIGHT); hit.setRemainingFireTicks(Math.max(60, hit.getRemainingFireTicks()));
                }
                visual(player, spell, start, end, -1, 24);
            }
            case PLAGUE -> {
                for (LivingEntity victim : enemies(player, target.position(), 6)) damage(player, victim, 12, ElementType.POISON);
                zone(player, target.position(), 6, 280, spell);
                visual(player, spell, player.getEyePosition(), target.position(), -1, 280);
            }
            case MUSICAL_INSPIRATION -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400, 0));
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0));
                visual(player, spell, target, 100);
            }
            case SHATTERING_SOUND -> {
                Vec3 forward = player.getLookAngle().normalize();
                for (LivingEntity victim : enemies(player, player.getEyePosition(), 10)) {
                    Vec3 diff = victim.getBoundingBox().getCenter().subtract(player.getEyePosition());
                    if (diff.normalize().dot(forward) < Math.cos(Math.PI / 6)) continue;
                    damage(player, victim, 20, ElementType.SOUND); knock(player.position(), victim, 2);
                    effect(victim, NewSpellContent.TINNITUS, 120);
                }
                visual(player, spell, player.getEyePosition(), player.getEyePosition().add(forward.scale(10)), -1, 28);
            }
            case MULTI_BARRIER_RAY -> beginBarrierField(player);
            case FLAME_CAGE -> beginFlameCage(player, target);
            case BLAZING_SUN -> beginBlazingSun(player);
            case SPRING_RETURN -> beginSpringReturn(player, targets);
            case ABYSS_DRAGON_STRIKE -> beginDragonBeam(player);
            case ICE_GOLEM -> {
                SpellSummon.summon(player, NewSpellContent.FROST_GOLEM.get(), 1);
                visual(player, spell, player, 60);
                level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 1.15F);
                player.displayClientMessage(Component.translatable("message.dimensional_mana.ice_golem_summoned"), true);
            }
            case SUBZERO_DOMAIN -> beginSubzeroDomain(player);
            case VOLT_STATE -> beginVoltState(player);
            case THUNDER_TEMPEST -> beginThunderTempest(player);
            case WIND_BURST_STEP -> WindSpells.beginBurstStep(player);
            case WIND_BURIAL_CORRIDOR -> WindSpells.beginBurialCorridor(player);
            case BOULDER -> EarthSpells.beginBoulderWard(player, targets);
            case MYRIAD_METEORS -> EarthSpells.beginMyriadMeteors(player);
            case CHAOS_MAGIC -> ArcaneSpells.castChaosMagic(player);
            case MANA_SENSE -> ArcaneSpells.castManaSense(player);
            case MANA_TIDE -> ArcaneSpells.castManaTide(player);
            case THORN_IMPALE -> WoodSpells.beginThornImpale(player);
            case ENDLESS_BANKAI -> WoodSpells.beginBankai(player);
            case GOLDEN_BELL -> MetalSpells.beginGoldenBell(player);
            case TEN_THOUSAND_SWORDS -> MetalSpells.beginTenThousandSwords(player);
            case LIGHT_SHELTER -> LightSpells.beginLightShelter(player);
            case RADIANT_RIFT -> LightSpells.castRadiantRift(player);
            case VENOM_HEART -> PoisonSpells.beginVenomHeart(player);
            case VENOM_NIGHT -> PoisonSpells.beginVenomNight(player);
            case INFRASOUND -> SonicSpells.beginInfrasound(player);
            case SOUL_CHORD -> SonicSpells.beginSoulChord(player);
            default -> { }
        }
    }
    public static void projectile(ServerPlayer player, SpellDefinition spell, Vec3 at, Vec3 direction, int index) {
        SpellProjectile entity = new SpellProjectile(NewSpellContent.PROJECTILE.get(), player.level());
        entity.configure(player, spell, at, direction, index);
        player.serverLevel().addFreshEntity(entity);
        if (spell != SpellDefinition.ROCK_ORBS && spell != SpellDefinition.GALE_SLASHES)
            visual(player, spell, at, at, -1, 6);
    }

    public static void beginBarrierField(ServerPlayer player) {
        // 重复施法先清掉旧场，避免同一玩家同时维护两套法阵。
        FIELDS.remove(player.getUUID());
        removePlayerBarriers(player.getUUID());
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 forward = player.getLookAngle();
        forward = new Vec3(forward.x, 0, forward.z);
        if (forward.lengthSqr() < 0.0001) forward = new Vec3(0, 0, 1);
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        BarrierField field = new BarrierField(level, player.getUUID(), player.getEyePosition(), forward, right,
                now, now + BARRIER_LIFETIME);
        FIELDS.put(player.getUUID(), field);
        Barrier first = spawnBarrier(player, field, 0);
        field.count = 1;
        if (first != null)
            visual(player, SpellDefinition.MULTI_BARRIER_RAY, player.getEyePosition(), first.position, -1, 20);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.barrier_field_start",
                BARRIER_MAX, (int)BARRIER_MANA_COST), true);
        SpellSyncPayload.send(player);
    }

    /** Alt+左键：每多展开一个法阵消耗 150 魔素，最多九个。 */
    public static void addBarrier(ServerPlayer player) {
        BarrierField field = FIELDS.get(player.getUUID());
        if (field == null) return;
        if (field.count >= BARRIER_MAX) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.barrier_field_full",
                    BARRIER_MAX), true);
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, BARRIER_MANA_COST);
        if (!ManaCheats.hasMana(player, cost)) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.barrier_no_mana",
                    (int)Math.ceil(cost)), true);
            return;
        }
        if (!ManaData.spendMana(player, BARRIER_MANA_COST)) return;
        SpellCasting.syncMana(player);
        spawnBarrier(player, field, field.count);
        field.count++;
        player.displayClientMessage(Component.translatable("message.dimensional_mana.barrier_added",
                field.count, BARRIER_MAX), true);
        player.playNotifySound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.15F + field.count * 0.04F);
        SpellSyncPayload.send(player);
    }

    /** 再次按下施法键结束本次施法；已展开的法阵保留各自的剩余时间。 */
    public static boolean endBarrierField(ServerPlayer player) {
        BarrierField field = FIELDS.remove(player.getUUID());
        if (field == null) return false;
        player.displayClientMessage(Component.translatable("message.dimensional_mana.barrier_field_end",
                field.count), true);
        SpellSyncPayload.send(player);
        return true;
    }

    public static int barrierFieldCount(ServerPlayer player) {
        BarrierField field = FIELDS.get(player.getUUID());
        return field == null ? 0 : field.count;
    }

    public static int barrierFieldTicks(ServerPlayer player) {
        BarrierField field = FIELDS.get(player.getUUID());
        return field == null ? 0 : (int)Math.max(0L, field.expiresAt - player.level().getGameTime());
    }

    private static void beginFlameCage(ServerPlayer player, LivingEntity target) {
        if (target == null) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 center = target.position();
        CAGES.add(new FlameCage(level, player.getUUID(), center, now, now + CAGE_DURATION));
        SpellVisualPayload.send(player, SpellDefinition.FLAME_CAGE, SpellVisualPayload.CAGE, -1,
                center, center, CAGE_DURATION);
        level.playSound(null, center.x, center.y, center.z,
                net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 0.7F);
        SpellSyncPayload.send(player);
    }

    /** 水或冰属性攻击命中笼中敌人时削减囚笼边缘的 20 点生命。 */
    public static void damageFlameCage(LivingEntity target, float damage) {
        if (damage <= 0.0F || !(target.level() instanceof ServerLevel level)) return;
        Vec3 point = target.getBoundingBox().getCenter();
        for (FlameCage cage : CAGES) {
            if (cage.level != level || cage.broken) continue;
            if (cage.center.distanceToSqr(point) > (CAGE_RADIUS + 1.0) * (CAGE_RADIUS + 1.0)) continue;
            cage.health -= damage;
            if (cage.health <= 0.0) {
                cage.broken = true;
                PENDING_CAGE_BREAKS.add(new CageBreak(cage, level.getGameTime() + 1));
            }
            break;
        }
    }

    private static void tickCages(ServerLevel level, long now) {
        Iterator<CageBreak> breaks = PENDING_CAGE_BREAKS.iterator();
        while (breaks.hasNext()) {
            CageBreak pending = breaks.next();
            if (pending.cage().level != level || pending.at() > now) continue;
            breaks.remove();
            CAGES.remove(pending.cage());
            breakFlameCage(pending.cage());
        }
        Iterator<FlameCage> cages = CAGES.iterator();
        while (cages.hasNext()) {
            FlameCage cage = cages.next();
            if (cage.level != level) continue;
            Entity ownerEntity = level.getEntity(cage.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive()) {
                cages.remove();
                if (ownerEntity instanceof ServerPlayer owner2)
                    SpellVisualPayload.send(owner2, SpellDefinition.FLAME_CAGE, SpellVisualPayload.CAGE_END, -1,
                            cage.center, cage.center, 1);
                continue;
            }
            if (now > cage.until) { cages.remove(); continue; }
            // 把已经被关进笼子的敌人拉回半径内；外力把他们打出 12 格才视为脱离。
            Iterator<UUID> trapped = cage.trapped.iterator();
            while (trapped.hasNext()) {
                Entity entity = level.getEntity(trapped.next());
                if (!(entity instanceof LivingEntity victim) || !victim.isAlive()) { trapped.remove(); continue; }
                Vec3 offset = victim.position().subtract(cage.center);
                double distance = offset.length();
                if (distance > CAGE_RADIUS + 12.0) { trapped.remove(); continue; }
                if (distance > CAGE_RADIUS - 0.15 && distance > 0.001) {
                    Vec3 clamped = cage.center.add(offset.scale((CAGE_RADIUS - 0.2) / distance));
                    teleport(victim, clamped);
                }
            }
            List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(cage.center, cage.center).inflate(CAGE_RADIUS),
                    entity -> entity.isAlive() && enemy(owner, entity)
                            && entity.position().distanceToSqr(cage.center) <= CAGE_RADIUS * CAGE_RADIUS);
            for (LivingEntity victim : inside) cage.trapped.add(victim.getUUID());
            if (now > cage.born && (now - cage.born) % 20 == 0) {
                for (LivingEntity victim : inside) {
                    damage(owner, victim, 6.0F, ElementType.FIRE);
                    effect(victim, NewSpellContent.FIRE_VULNERABILITY, 400);
                }
                for (int i = 0; i < 18; i++) {
                    double angle = i * Math.PI * 2.0 / 18.0;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                            cage.center.x + Math.cos(angle) * CAGE_RADIUS, cage.center.y + 0.25,
                            cage.center.z + Math.sin(angle) * CAGE_RADIUS, 1, 0, 0, 0, 0.01);
                }
            }
        }
    }

    private static void breakFlameCage(FlameCage cage) {
        ServerLevel level = cage.level;
        Entity ownerEntity = level.getEntity(cage.owner);
        LivingEntity owner = ownerEntity instanceof LivingEntity living ? living : null;
        if (owner != null) {
            List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(cage.center, cage.center).inflate(CAGE_RADIUS),
                    entity -> entity.isAlive() && enemy(owner, entity)
                            && entity.position().distanceToSqr(cage.center) <= CAGE_RADIUS * CAGE_RADIUS);
            for (LivingEntity victim : inside) damage(owner, victim, 15.0F, ElementType.FIRE);
            SpellVisualPayload.send(owner, SpellDefinition.FLAME_CAGE, SpellVisualPayload.CAGE_END, -1,
                    cage.center, cage.center, 1);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, cage.center.x,
                cage.center.y + 0.8, cage.center.z, 6, 1.2, 1.0, 1.2, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, cage.center.x,
                cage.center.y + 0.8, cage.center.z, 45, 1.8, 1.2, 1.8, 0.08);
        level.playSound(null, cage.center.x, cage.center.y, cage.center.z,
                net.minecraft.sounds.SoundEvents.BLAZE_DEATH, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
    }

    private static void teleport(LivingEntity victim, Vec3 at) {
        if (victim instanceof ServerPlayer player)
            player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
        else victim.teleportTo(at.x, at.y, at.z);
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;
    }

    public static void beginBlazingSun(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlazingSun old = SUNS.remove(player.getUUID());
        if (old != null) old.discard();
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 at = eye.add(dir.scale(4.0));
        Vec3 hit = blockEnd(player, eye, at);
        double distance = eye.distanceTo(hit);
        // 贴墙时把火球放在墙面前方，避免整颗球埋进方块里。
        if (distance < 3.9) at = eye.add(dir.scale(Math.max(0.6, distance - 0.35)));
        BlazingSun sun = new BlazingSun(NewSpellContent.BLAZING_SUN.get(), level);
        sun.configure(player, at, dir);
        if (!level.addFreshEntity(sun)) return;
        SUNS.put(player.getUUID(), sun);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.blazing_sun_ready",
                BlazingSun.MAX_CHARGE, (int)SUN_CHARGE_COST), true);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.1F, 0.75F);
        SpellVisualPayload.send(player, SpellDefinition.BLAZING_SUN, SpellVisualPayload.CANCEL, -1, at, at, 1);
        SpellSyncPayload.send(player);
    }

    /** Alt+左键：为悬停的火球增加一层充能，每层消耗 90 魔素，上限 18 层。 */
    public static void addSunCharge(ServerPlayer player) {
        BlazingSun sun = SUNS.get(player.getUUID());
        if (sun == null || sun.isReleased()) return;
        if (sun.charge() >= BlazingSun.MAX_CHARGE) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.blazing_sun_full",
                    BlazingSun.MAX_CHARGE), true);
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, SUN_CHARGE_COST);
        if (!ManaCheats.hasMana(player, cost)) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.blazing_sun_no_mana",
                    (int)Math.ceil(cost)), true);
            return;
        }
        if (!ManaData.spendMana(player, SUN_CHARGE_COST)) return;
        SpellCasting.syncMana(player);
        if (!sun.addCharge()) return;
        player.displayClientMessage(Component.translatable("message.dimensional_mana.blazing_sun_charged",
                sun.charge(), BlazingSun.MAX_CHARGE), true);
        SpellSyncPayload.send(player);
    }

    /** 再次按下施法键：取消悬停并沿当前方向发射。 */
    public static boolean releaseBlazingSun(ServerPlayer player) {
        BlazingSun sun = SUNS.get(player.getUUID());
        if (sun == null || sun.isReleased()) return false;
        SUNS.remove(player.getUUID());
        sun.release();
        player.displayClientMessage(Component.translatable("message.dimensional_mana.blazing_sun_released",
                sun.charge()), true);
        SpellSyncPayload.send(player);
        return true;
    }

    public static int blazingSunCharge(ServerPlayer player) {
        BlazingSun sun = SUNS.get(player.getUUID());
        return sun == null || sun.isReleased() ? 0 : sun.charge();
    }

    public static int blazingSunTicks(ServerPlayer player) {
        BlazingSun sun = SUNS.get(player.getUUID());
        return sun == null || sun.isReleased() ? 0 : sun.remainingChargeTicks();
    }

    /** 登记一批被炽阳之怒烧成岩浆的方块，10 秒后自动凝固成黑石。 */
    public static void coolLavaLater(ServerLevel level, List<BlockPos> positions) {
        if (positions.isEmpty()) return;
        LAVA_SCORCHES.add(new LavaScorch(level, level.getGameTime() + LAVA_COOL_TICKS, List.copyOf(positions)));
    }

    /** 岩浆冷却：到期后分批把仍然是岩浆的方块换成黑石，避免整片一起替换时卡顿。 */
    private static void tickLavaCooling(ServerLevel level, long now) {
        Iterator<LavaScorch> scorches = LAVA_SCORCHES.iterator();
        while (scorches.hasNext()) {
            LavaScorch scorch = scorches.next();
            if (scorch.level != level || now < scorch.at) continue;
            int end = Math.min(scorch.positions.size(), scorch.cursor + LAVA_COOL_PER_TICK);
            for (; scorch.cursor < end; scorch.cursor++) {
                BlockPos pos = scorch.positions.get(scorch.cursor);
                if (!level.hasChunkAt(pos)) continue;
                if (!level.getBlockState(pos).is(Blocks.LAVA)) continue;
                level.setBlockAndUpdate(pos, Blocks.BLACKSTONE.defaultBlockState());
            }
            if (scorch.cursor >= scorch.positions.size()) scorches.remove();
        }
    }

    public static void forgetSun(UUID owner, BlazingSun sun) {
        if (owner != null && SUNS.get(owner) == sun) SUNS.remove(owner);
    }

    // ───────────────────────── 涌泉之报 ─────────────────────────

    /** 泉水只治疗友方：自己、队友、驯服生物与召唤物，不会给敌对生物回血。 */
    private static boolean friendlyTo(LivingEntity owner, LivingEntity target) {
        if (target == null || !target.isAlive() || target.isSpectator()) return false;
        if (target.getUUID().equals(owner.getUUID())) return true;
        if (target instanceof net.minecraft.world.entity.monster.Enemy) return false;
        return !enemy(owner, target);
    }

    private static void beginSpringReturn(ServerPlayer player, List<LivingEntity> targets) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int placed = 0;
        for (LivingEntity target : targets) {
            // 泉水落在目标脚下，贴地才看得出“涌出”的位置。
            Vec3 center = floorAt(level, target.position());
            SPRINGS.add(new Spring(level, player.getUUID(), center, now, now + SPRING_DURATION));
            SpellVisualPayload.send(player, SpellDefinition.SPRING_RETURN, SpellVisualPayload.RELEASE, -1,
                    center, center, SPRING_DURATION);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BUCKET_EMPTY,
                    SoundSource.PLAYERS, 0.9F, 1.05F);
            placed++;
        }
        if (placed > 0)
            player.displayClientMessage(Component.translatable("message.dimensional_mana.spring_return_placed",
                    placed, SPRING_DURATION / 20), true);
    }

    /** 从脚边往下找地面，起点只抬高 0.5 格，避免在低矮洞里把泉水放到头顶的方块上。 */
    private static Vec3 floorAt(ServerLevel level, Vec3 at) {
        HitResult hit = level.clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -3.0, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }

    private static void tickSprings(ServerLevel level, long now) {
        Iterator<Spring> springs = SPRINGS.iterator();
        while (springs.hasNext()) {
            Spring spring = springs.next();
            if (spring.level != level) continue;
            Entity ownerEntity = level.getEntity(spring.owner);
            if (now > spring.until || !(ownerEntity instanceof LivingEntity owner) || !owner.isAlive()) {
                springs.remove();
                continue;
            }
            Vec3 center = spring.center;
            level.sendParticles(ParticleTypes.SPLASH, center.x, center.y + 0.18, center.z,
                    2, 0.30, 0.08, 0.30, 0.05);
            level.sendParticles(ParticleTypes.DRIPPING_WATER, center.x, center.y + 1.35, center.z,
                    2, 0.35, 0.15, 0.35, 0.0);
            if (now % 12 == 0)
                level.playSound(null, center.x, center.y, center.z, SoundEvents.BUCKET_EMPTY,
                        SoundSource.PLAYERS, 0.30F, 1.55F);
            if ((now - spring.born) % SPRING_HEAL_INTERVAL != 0) continue;
            List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(center, center).inflate(SPRING_RADIUS),
                    entity -> friendlyTo(owner, entity)
                            && entity.getBoundingBox().distanceToSqr(center) <= SPRING_RADIUS * SPRING_RADIUS);
            for (LivingEntity ally : allies) {
                ally.heal(SPRING_HEAL);
                level.sendParticles(ParticleTypes.BUBBLE, ally.getX(), ally.getY() + ally.getBbHeight() * 0.6,
                        ally.getZ(), 3, 0.28, 0.2, 0.28, 0.02);
            }
        }
    }

    // ───────────────────────── 深渊龙击 ─────────────────────────

    public static void beginDragonBeam(ServerPlayer player) {
        DragonBeam old = DRAGONS.remove(player.getUUID());
        if (old != null) setDragonSlow(player, false);
        Vec3 aim = player.getLookAngle();
        if (aim.lengthSqr() < 1.0E-4) aim = new Vec3(0, 0, 1);
        DragonBeam beam = new DragonBeam(player.serverLevel(), player.getUUID(), aim.normalize(),
                player.level().getGameTime());
        beam.nextPulse = beam.born + BEAM_PULSE_INTERVAL;
        updateDragonGeometry(player, beam);
        DRAGONS.put(player.getUUID(), beam);
        setDragonSlow(player, true);
        SpellVisualPayload.send(player, SpellDefinition.ABYSS_DRAGON_STRIKE, SpellVisualPayload.BEAM, -1,
                beam.start, beam.end, BEAM_MAX_TICKS);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.abyss_dragon_start",
                BEAM_MAX_TICKS / 20), true);
        // 开场三层叠加：低频龙吼定气势，声波充能给蓄力感，水下轰鸣铺出整个水体的厚度。
        beam.level.playSound(null, beam.start.x, beam.start.y, beam.start.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.4F, 0.45F);
        beam.level.playSound(null, beam.start.x, beam.start.y, beam.start.z,
                SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.6F, 0.58F);
        beam.level.playSound(null, beam.start.x, beam.start.y, beam.start.z,
                SoundEvents.AMBIENT_UNDERWATER_LOOP, SoundSource.PLAYERS, 1.8F, 0.50F);
        SpellSyncPayload.send(player);
    }

    /** 施法键在喷射期间改为“取消施法”；返回是否确实取消了正在进行的喷射。 */
    public static boolean cancelDragonBeam(ServerPlayer player) {
        if (!DRAGONS.containsKey(player.getUUID())) return false;
        endDragonBeam(player, "message.dimensional_mana.abyss_dragon_cancelled");
        return true;
    }

    public static int dragonBeamTicks(ServerPlayer player) {
        DragonBeam beam = DRAGONS.get(player.getUUID());
        if (beam == null) return 0;
        return (int)Math.max(0L, BEAM_MAX_TICKS - (player.level().getGameTime() - beam.born));
    }

    public static void tickPlayer(ServerPlayer player) {
        // 曜光裂界的前摇减速、蓄力记录与无敌提示。
        LightSpells.tickPlayer(player);
        // 伏特状态与天雷滚滚的追加记录都按玩家各自维护，先在这里跑掉再处理深渊龙击。
        tickVoltState(player, player.level().getGameTime());
        // 风爆步的触碰判定与风葬回廊的风狂状态各自维护。
        WindSpells.tickPlayer(player, player.level().getGameTime());
        // 万法千陨的悬停石块与五秒投掷窗口。
        EarthSpells.tickPlayer(player, player.level().getGameTime());
        pruneTempestCharge(player);
        DomainField domain = DOMAINS.get(player.getUUID());
        if (domain == null) {
            // 死亡、换维度等异常路径可能已把状态摘掉，这里兜底确保减速一定被移除。
            setDomainSlow(player, false);
        } else if (domain.level != player.serverLevel() || !player.isAlive()) {
            DOMAINS.remove(player.getUUID());
            setDomainSlow(player, false);
            SpellSyncPayload.send(player);
        }
        DragonBeam beam = DRAGONS.get(player.getUUID());
        if (beam == null) return;
        if (beam.level != player.serverLevel()) {
            endDragonBeam(player, "message.dimensional_mana.abyss_dragon_cancelled");
            return;
        }
        ServerLevel level = beam.level;
        long now = level.getGameTime();
        if (!player.isAlive() || player.isSpectator() || cannotCast(player)) {
            endDragonBeam(player, "message.dimensional_mana.abyss_dragon_interrupted");
            return;
        }
        if (now - beam.born >= BEAM_MAX_TICKS) {
            endDragonBeam(player, "message.dimensional_mana.abyss_dragon_expired");
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, BEAM_MANA_PER_TICK);
        if (!ManaCheats.hasMana(player, cost) || !ManaData.spendMana(player, BEAM_MANA_PER_TICK)) {
            endDragonBeam(player, "message.dimensional_mana.abyss_dragon_no_mana");
            return;
        }
        if (now % 4 == 0) SpellCasting.syncMana(player);
        // 炮口每秒最多转 30 度：镜头可以甩，水柱要慢慢跟过来。
        beam.aim = rotateToward(beam.aim, player.getLookAngle(), Math.toRadians(BEAM_TURN_DEGREES_PER_TICK));
        updateDragonGeometry(player, beam);
        damageAndBreak(player, beam, now);
        beamAmbient(player, beam, now);
        if (now >= beam.nextVisual) {
            // 每 2 刻发一次端点，客户端在两包之间插值，转向就不会一顿一顿。
            beam.nextVisual = now + 2;
            SpellVisualPayload.send(player, SpellDefinition.ABYSS_DRAGON_STRIKE, SpellVisualPayload.BEAM, -1,
                    beam.start, beam.end, BEAM_MAX_TICKS);
        }
        if (now % 10 == 0) SpellSyncPayload.send(player);
    }

    private static void updateDragonGeometry(ServerPlayer player, DragonBeam beam) {
        Vec3 eye = player.getEyePosition();
        Vec3 start = eye.add(beam.aim.scale(BEAM_SIGIL_DISTANCE)).add(0, -0.15, 0);
        Vec3 reach = start.add(beam.aim.scale(BEAM_RANGE));
        HitResult hit = beam.level.clip(new ClipContext(start, reach, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        beam.start = start;
        beam.end = hit.getType() == HitResult.Type.MISS ? reach : hit.getLocation();
        beam.blocked = hit.getType() == HitResult.Type.BLOCK;
    }

    /**
     * 首次命中 48 点；之后每 10 刻 20 点水伤并刷新水附着；每 0.5 秒把目标推开 0.5 格。
     * 水柱的伤害无视目标的受击无敌帧，脉冲和首次命中都按全额结算。
     */
    private static void damageAndBreak(ServerPlayer player, DragonBeam beam, long now) {
        List<LivingEntity> hits = pathTargets(player, beam.start, beam.end, BEAM_WIDTH);
        for (LivingEntity victim : hits) {
            // 水柱浇在燃烧的目标身上先灭火，再结算伤害。
            if (victim.isOnFire()) victim.extinguishFire();
            // 每刻刷新“最后停留时间”，离开水柱超过一秒才允许重新按首次命中结算。
            boolean fresh = !beam.contact.containsKey(victim.getUUID());
            beam.contact.put(victim.getUUID(), now);
            if (!fresh) continue;
            damage(player, victim, BEAM_INITIAL_DAMAGE, ElementType.WATER);
            knock(beam.start, victim, 0.5);
        }
        beam.contact.entrySet().removeIf(entry -> now - entry.getValue() > 20);
        if (now >= beam.nextPulse) {
            beam.nextPulse = now + BEAM_PULSE_INTERVAL;
            for (LivingEntity victim : hits) {
                damage(player, victim, BEAM_PULSE_DAMAGE, ElementType.WATER);
                knock(beam.start, victim, 0.5);
            }
        }
        // 水柱先把沿途的岩浆淬成黑曜石/圆石、把火焰浇灭，再决定能不能击穿挡路的方块。
        quenchBeam(player, beam, now);
        clearFoliage(player, beam, now);
        heatAndBreak(player, beam, now);
    }

    /**
     * 水柱扫过花草、树叶这类一冲就散的东西：不等压制、不进破坏队列，碰到就清掉。
     * 水柱有粗细，所以沿射线取点后按半径包住附近的方块。
     */
    private static void clearFoliage(ServerPlayer player, DragonBeam beam, long now) {
        if (now % BEAM_FOLIAGE_INTERVAL != 0) return;
        ServerLevel level = beam.level;
        Vec3 delta = beam.end.subtract(beam.start);
        double length = delta.length();
        if (length < 0.3) return;
        int steps = Math.min(64, (int)(length / 0.7) + 1);
        int removed = 0;
        int rustle = 0;
        for (int i = 0; i <= steps && removed < BEAM_FOLIAGE_LIMIT; i++) {
            Vec3 point = beam.start.lerp(beam.end, i / (double)steps);
            BlockPos center = BlockPos.containing(point);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (removed >= BEAM_FOLIAGE_LIMIT) break;
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (!level.hasChunkAt(pos)) continue;
                        if (distanceToSegment(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                beam.start, beam.end) > BEAM_FOLIAGE_RADIUS) continue;
                        BlockState state = level.getBlockState(pos);
                        if (!foliage(state)) continue;
                        level.destroyBlock(pos, false, player);
                        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5,
                                pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.06);
                        removed++;
                        if (rustle++ < 2)
                            level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.55F, 1.35F);
                    }
        }
    }

    /** 花草、树叶、藤蔓这类没有抗压结构的东西。 */
    private static boolean foliage(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof BushBlock;
    }

    /**
     * 水柱贴住方块满 1 秒后开始破拆：以命中点为中心、半径 2 格内的方块全部进入射程，
     * 每 0.5 秒打掉一批（靠近命中点的先碎），冷却结束继续打，一路往里钻。
     *
     * <p>关键在于“同一个地方持续破坏”：挖开一层之后命中点会短暂落在空气里，
     * 所以进度不会立刻清零，而是保留 1 秒宽限；只要水柱还压着方块，节奏就一直是
     * 1 秒起手 + 每 0.5 秒一批，不会出现“炸过一次就再也不动了”。
     */
    private static void heatAndBreak(ServerPlayer player, DragonBeam beam, long now) {
        ServerLevel level = beam.level;
        BlockPos pos = beam.blocked ? probeImpact(beam) : null;
        boolean lostContact = now - beam.lastDig > BEAM_BREAK_GRACE;
        if (pos == null) {
            // 水柱前面没有可压的方块（放空或只剩液体）：宽限期过后才清掉进度。
            if (lostContact) {
                beam.heatPos = null;
                beam.heatTicks = 0;
            }
            beam.hardTicks = 0;
            return;
        }
        if (!breakable(level, pos)) {
            // 压在黑曜石、基岩这类硬方块上：只提示，不清进度，
            // 免得挖开一层撞到硬块就把刚刚攒的压制全部作废。
            if (lostContact) {
                beam.heatPos = null;
                beam.heatTicks = 0;
            }
            beam.hardTicks++;
            if (!beam.hardNotified && beam.hardTicks >= 40) {
                beam.hardNotified = true;
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.abyss_dragon_too_hard"), true);
            }
            return;
        }
        beam.hardTicks = 0;
        beam.heatPos = pos;
        beam.lastDig = now;
        beam.heatTicks++;
        if (beam.heatTicks < BEAM_BREAK_WARMUP) {
            if (beam.heatTicks % 4 == 0) heatFeedback(level, pos, beam.heatTicks / (float)BEAM_BREAK_WARMUP);
            // 每 10 刻在动作栏显示一次压制进度，1 秒的等待不会像“没反应”。
            if (beam.heatTicks % 10 == 0)
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.abyss_dragon_breach_progress",
                        beam.heatTicks * 100 / BEAM_BREAK_WARMUP), true);
            return;
        }
        if (now < beam.nextBreak) return;
        beam.nextBreak = now + BEAM_BREAK_INTERVAL;
        int broken = carve(level, pos, beam.aim, player);
        if (broken > 0 && !beam.breachNotified) {
            beam.breachNotified = true;
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.abyss_dragon_breached"), true);
        }
        if (broken <= 0) return;
        // 每 1 秒在动作栏报一次进度，玩家能确认水柱还在同一个地方持续破拆。
        if (now >= beam.nextDigNotice) {
            beam.nextDigNotice = now + 20;
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.abyss_dragon_tunnelling"), true);
        }
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                14, 0.6, 0.6, 0.6, 0.14);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                6, 0.5, 0.4, 0.5, 0.04);
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.2F, 0.85F);
    }

    /**
     * 打掉命中点周围半径 2 格内的一批合格方块。只挖向水柱推进的方向，
     * 不会把施法者脚边和身后的方块一起掏空。
     */
    private static int carve(ServerLevel level, BlockPos center, Vec3 direction, ServerPlayer player) {
        int reach = (int)Math.ceil(BEAM_BREAK_RADIUS);
        double radiusSqr = BEAM_BREAK_RADIUS * BEAM_BREAK_RADIUS;
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++)
            for (int dy = -reach; dy <= reach; dy++)
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSqr) continue;
                    // 方位向量点积：明显在命中点后方的方块跳过。
                    if (dx * direction.x + dy * direction.y + dz * direction.z < -0.9) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!breakable(level, pos)) continue;
                    candidates.add(pos);
                }
        if (candidates.isEmpty()) return 0;
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        int broken = 0;
        for (BlockPos pos : candidates) {
            if (broken >= BEAM_BREAK_PER_CYCLE) break;
            if (!level.destroyBlock(pos, false, player)) continue;
            broken++;
            level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    3, 0.34, 0.34, 0.34, 0.10);
        }
        return broken;
    }

    /**
     * 水柱扫过岩浆：静止岩浆凝成黑曜石，流动岩浆凝成圆石；扫过火焰直接浇灭。
     * 岩浆不是碰撞方块，水柱会直接穿过，所以这里沿整条射线找“被水柱粗细包住的”岩浆/火焰格子。
     */
    private static void quenchBeam(ServerPlayer player, DragonBeam beam, long now) {
        if (now % BEAM_QUENCH_INTERVAL != 0) return;
        ServerLevel level = beam.level;
        Vec3 delta = beam.end.subtract(beam.start);
        double length = delta.length();
        if (length < 0.5) return;
        int steps = Math.min(96, (int)(length / 0.75) + 1);
        int converted = 0;
        boolean lavaTouched = false, fireTouched = false;
        boolean lavaSound = false, fireSound = false;
        for (int i = 0; i <= steps && converted < BEAM_QUENCH_LIMIT; i++) {
            Vec3 point = beam.start.lerp(beam.end, i / (double)steps);
            BlockPos center = BlockPos.containing(point);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (converted >= BEAM_QUENCH_LIMIT) break;
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (distanceToSegment(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                beam.start, beam.end) > BEAM_QUENCH_RADIUS) continue;
                        BlockState state = level.getBlockState(pos);
                        boolean lava = state.getFluidState().is(FluidTags.LAVA);
                        boolean fire = state.getBlock() instanceof BaseFireBlock;
                        if (!lava && !fire) continue;
                        if (fire) {
                            level.removeBlock(pos, false);
                            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5,
                                    pos.getZ() + 0.5, 6, 0.28, 0.28, 0.28, 0.03);
                            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.35,
                                    pos.getZ() + 0.5, 3, 0.22, 0.12, 0.22, 0.02);
                            if (!fireSound) {
                                fireSound = true;
                                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.1F, 1.45F);
                            }
                            fireTouched = true;
                            converted++;
                            continue;
                        }
                        // 静止岩浆（源头）淬成黑曜石，流动岩浆淬成圆石。
                        // 施法者脚边 2 格内不淬火，免得把自己封进黑曜石里闷死。
                        if (pos.distSqr(player.blockPosition()) <= 4) continue;
                        boolean source = state.getFluidState().isSource();
                        level.setBlockAndUpdate(pos, (source ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.9,
                                pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
                        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.55,
                                pos.getZ() + 0.5, 5, 0.32, 0.18, 0.32, 0.03);
                        if (!lavaSound) {
                            lavaSound = true;
                            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS,
                                    1.3F, source ? 0.78F : 1.05F);
                        }
                        lavaTouched = true;
                        converted++;
                    }
        }
        if (now < beam.nextQuenchNotice) return;
        if (lavaTouched) {
            beam.nextQuenchNotice = now + 40;
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.abyss_dragon_quench_lava"), true);
        } else if (fireTouched) {
            beam.nextQuenchNotice = now + 40;
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.abyss_dragon_quench_fire"), true);
        }
    }

    /** 点到线段的距离，用于判断一个方块是否被水柱包住；纯标量运算，避免每刻生成一堆临时向量。 */
    private static double distanceToSegment(double px, double py, double pz, Vec3 a, Vec3 b) {
        double ax = a.x, ay = a.y, az = a.z;
        double bx = b.x - ax, by = b.y - ay, bz = b.z - az;
        double len2 = bx * bx + by * by + bz * bz;
        if (len2 < 1.0E-6) {
            double dx = px - ax, dy = py - ay, dz = pz - az;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        double t = ((px - ax) * bx + (py - ay) * by + (pz - az) * bz) / len2;
        t = t < 0.0 ? 0.0 : Math.min(t, 1.0);
        double dx = px - (ax + bx * t), dy = py - (ay + by * t), dz = pz - (az + bz * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 找到水柱真正压在哪个方块上。
     *
     * <p>命中点几乎总是正好贴在方块表面上，直接取那一格有可能落到表面外的空气里
     * （俯射地面落在顶面 y = 方块顶时最常见），所以先沿水柱前进方向探进去一点点，
     * 再往回退着找，跳过空气和液体。这样挖开一层、命中点落进坑里之后，
     * 也能立刻找到新的坑壁继续破坏，而不是空转。
     */
    private static BlockPos probeImpact(DragonBeam beam) {
        Vec3 aim = beam.aim;
        Vec3 point = beam.end;
        for (int i = 0; i < 12; i++) {
            Vec3 probe = i == 0 ? point.add(aim.scale(0.03)) : point;
            BlockPos pos = BlockPos.containing(probe);
            if (beam.level.hasChunkAt(pos)) {
                BlockState state = beam.level.getBlockState(pos);
                if (!state.isAir() && state.getFluidState().isEmpty()) return pos;
            }
            point = point.subtract(aim.scale(0.08));
        }
        return null;
    }

    /** 只破坏硬度不超过 3 的方块：基岩（硬度 -1）与黑曜石（硬度 50）都会挡住水柱。 */
    private static boolean breakable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F && hardness <= BEAM_MAX_BLOCK_HARDNESS;
    }

    /** 击穿前的“水压啃咬”：越接近 1 秒，气泡和蒸汽越密。 */
    private static void heatFeedback(ServerLevel level, BlockPos pos, float progress) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 1 + (int)(progress * 4), 0.32, 0.32, 0.32, 0.03);
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1 + (int)(progress * 3), 0.26, 0.26, 0.26, 0.02);
    }

    private static void beamAmbient(ServerPlayer player, DragonBeam beam, long now) {
        ServerLevel level = beam.level;
        Vec3 delta = beam.end.subtract(beam.start);
        for (int i = 1; i <= 5; i++) {
            Vec3 point = beam.start.add(delta.scale(i / 6.0));
            level.sendParticles(ParticleTypes.SPLASH, point.x, point.y, point.z, 1, 0.09, 0.09, 0.09, 0.02);
            if (i % 2 == 0)
                level.sendParticles(ParticleTypes.DRIPPING_WATER, point.x, point.y - 0.1, point.z,
                        1, 0.12, 0.06, 0.12, 0.0);
        }
        level.sendParticles(ParticleTypes.BUBBLE, beam.start.x, beam.start.y, beam.start.z,
                2, 0.18, 0.18, 0.18, 0.02);
        if (beam.blocked) {
            level.sendParticles(ParticleTypes.SPLASH, beam.end.x, beam.end.y, beam.end.z,
                    6, 0.35, 0.25, 0.35, 0.08);
            // 压在方块上时加一层闷响，水压咬石头的声音更厚。
            if (now % 10 == 0)
                level.playSound(null, beam.end.x, beam.end.y, beam.end.z, SoundEvents.AMBIENT_UNDERWATER_ENTER,
                        SoundSource.PLAYERS, 0.55F, 0.55F);
        }
        // 持续低频水流轰鸣：每 3 秒续一次，和龙吼叠成“深渊在咆哮”的底色。
        if (now % 60 == 0)
            level.playSound(null, beam.start.x, beam.start.y, beam.start.z, SoundEvents.AMBIENT_UNDERWATER_LOOP,
                    SoundSource.PLAYERS, 1.50F, 0.48F);
        if (now % 8 == 0)
            level.playSound(null, beam.start.x, beam.start.y, beam.start.z, SoundEvents.BUCKET_EMPTY,
                    SoundSource.PLAYERS, 0.60F, 1.50F);
        if (now % 6 == 0)
            level.playSound(null, beam.end.x, beam.end.y, beam.end.z, SoundEvents.GENERIC_SPLASH,
                    SoundSource.PLAYERS, 0.80F, 1.15F);
        // 每隔 5 秒远处再传来一声龙吟，喷得越久越压场。
        if (now % 100 == 0)
            level.playSound(null, beam.end.x, beam.end.y, beam.end.z, SoundEvents.ENDER_DRAGON_AMBIENT,
                    SoundSource.PLAYERS, 1.25F, 0.48F);
    }

    private static void endDragonBeam(ServerPlayer player, String messageKey) {
        DragonBeam beam = DRAGONS.remove(player.getUUID());
        if (beam == null) return;
        setDragonSlow(player, false);
        if (beam.start != null && beam.end != null)
            SpellVisualPayload.send(player, SpellDefinition.ABYSS_DRAGON_STRIKE, SpellVisualPayload.BEAM_END, -1,
                    beam.start, beam.start, 1);
        // 水柱收束时在末端砸出一记重响；玩家主动取消时不放，免得打断很突兀。
        if (beam.end != null && !"message.dimensional_mana.abyss_dragon_cancelled".equals(messageKey)) {
            beam.level.playSound(null, beam.end.x, beam.end.y, beam.end.z, SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, 1.7F, 0.78F);
            beam.level.playSound(null, beam.start.x, beam.start.y, beam.start.z, SoundEvents.GENERIC_SPLASH,
                    SoundSource.PLAYERS, 1.4F, 0.62F);
        }
        if (messageKey != null) player.displayClientMessage(Component.translatable(messageKey), true);
        SpellCasting.startCooldown(player, SpellDefinition.ABYSS_DRAGON_STRIKE);
        SpellCasting.syncMana(player);
        SpellSyncPayload.send(player);
    }

    /** 登录时兜底清除：减速只是临时属性，绝不能跟着玩家数据一直留着。 */
    public static void clearDragonSlow(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(DRAGON_SLOW);
        speed.removeModifier(DOMAIN_SLOW);
    }

    private static void setDragonSlow(ServerPlayer player, boolean active) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) speed.addOrUpdateTransientModifier(new AttributeModifier(DRAGON_SLOW,
                -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else speed.removeModifier(DRAGON_SLOW);
    }

    /** 把当前朝向按最大角速度转向目标朝向（罗德里格斯旋转）。 */
    private static Vec3 rotateToward(Vec3 from, Vec3 to, double maxRadians) {
        Vec3 a = from.normalize();
        Vec3 b = to.normalize();
        double angle = Math.acos(Math.clamp(a.dot(b), -1.0, 1.0));
        if (angle <= maxRadians || angle < 1.0E-5) return b;
        Vec3 axis = a.cross(b);
        if (axis.lengthSqr() < 1.0E-8) {
            // 正好掉头 180 度时叉积退化为零，任选一条垂直轴，让水柱仍然按角速度慢慢转过去。
            axis = a.cross(Math.abs(a.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
            if (axis.lengthSqr() < 1.0E-8) return b;
        }
        axis = axis.normalize();
        double sin = Math.sin(maxRadians), cos = Math.cos(maxRadians);
        return a.scale(cos).add(axis.cross(a).scale(sin)).add(axis.scale(axis.dot(a) * (1.0 - cos))).normalize();
    }

    // ───────────────────────── 零下领域 ─────────────────────────

    public static void beginSubzeroDomain(ServerPlayer player) {
        DomainField old = DOMAINS.remove(player.getUUID());
        if (old != null) setDomainSlow(player, false);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        DomainField field = new DomainField(level, player.getUUID(), now, now + DOMAIN_FIELD_LIFETIME);
        DOMAINS.put(player.getUUID(), field);
        setDomainSlow(player, true);
        sendDomainField(player, field);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.subzero_field_start",
                DOMAIN_MAX_LAYERS, (int)DOMAIN_LAYER_COST), true);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 0.62F);
        // 开阵：冰晶共鸣铺底，远处压一声低沉龙吼，再补一记冰层凝结的脆响。
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.15F, 0.58F);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 0.85F, 0.40F);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 0.55F, 0.45F);
        SpellSyncPayload.send(player);
    }

    /** 刷新叠层外观：层数编码在 end.y 与 start.y 的差值里，客户端据此向上堆阵。 */
    private static void sendDomainField(ServerPlayer player, DomainField field) {
        Vec3 feet = player.position();
        SpellVisualPayload.send(player, SpellDefinition.SUBZERO_DOMAIN, SpellVisualPayload.DOMAIN_FIELD,
                player.getId(), feet, feet.add(0, field.layers, 0), DOMAIN_FIELD_LIFETIME);
    }

    /** Alt+左键：每层消耗 144 魔素，最多 12 层；伤害与范围按乘算逐层提高。 */
    public static void addSubzeroLayer(ServerPlayer player) {
        DomainField field = DOMAINS.get(player.getUUID());
        if (field == null) return;
        if (field.layers >= DOMAIN_MAX_LAYERS) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.subzero_field_full",
                    DOMAIN_MAX_LAYERS), true);
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, DOMAIN_LAYER_COST);
        if (!ManaCheats.hasMana(player, cost)) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.subzero_no_mana",
                    (int)Math.ceil(cost)), true);
            return;
        }
        if (!ManaData.spendMana(player, DOMAIN_LAYER_COST)) return;
        SpellCasting.syncMana(player);
        field.layers++;
        sendDomainField(player, field);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.subzero_added",
                field.layers, DOMAIN_MAX_LAYERS), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.7F, 0.95F + field.layers * 0.05F);
        // 每叠四层敲一记更厚的钟，层数越高越像整座法阵在嗡鸣。
        if (field.layers % 4 == 0) {
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                    0.85F, 0.60F + field.layers * 0.02F);
            player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS,
                    0.45F, 0.55F + field.layers * 0.02F);
        }
        SpellSyncPayload.send(player);
    }

    /** 再次按下施法键：释放领域并开始结算。 */
    public static boolean releaseSubzeroDomain(ServerPlayer player) {
        DomainField field = DOMAINS.remove(player.getUUID());
        if (field == null) return false;
        releaseDomain(player, field);
        return true;
    }

    private static void releaseDomain(ServerPlayer player, DomainField field) {
        setDomainSlow(player, false);
        ServerLevel level = field.level;
        int layers = field.layers;
        // 伤害与范围按层数乘算，冻结血量、时长与两种易伤按层数线性成长。
        double damage = DOMAIN_BASE_DAMAGE * Math.pow(1.0 + DOMAIN_LAYER_DAMAGE, layers);
        double radius = DOMAIN_BASE_RADIUS + 2.0 * layers;
        double healthLimit = DOMAIN_BASE_FREEZE_HEALTH * (1.0 + DOMAIN_LAYER_HEALTH * layers);
        int freezeTicks = (int)Math.round(DOMAIN_BASE_FREEZE_TICKS * (1.0 + DOMAIN_LAYER_DURATION * layers));
        float damageTaken = DOMAIN_BASE_DAMAGE_TAKEN + DOMAIN_LAYER_DAMAGE_TAKEN * layers;
        float reaction = DOMAIN_BASE_REACTION + DOMAIN_LAYER_REACTION * layers;
        Vec3 center = player.position();
        SpellVisualPayload.send(player, SpellDefinition.SUBZERO_DOMAIN, SpellVisualPayload.DOMAIN_END,
                player.getId(), center, center, 1);
        SpellVisualPayload.send(player, SpellDefinition.SUBZERO_DOMAIN, SpellVisualPayload.DOMAIN_BURST,
                -1, center, center.add(0, radius, 0), 60);
        // 领域内的水变蓝冰、岩浆变玄武岩，露天的地面覆一层雪。
        SpellEnvironmentSystem.freezeDomain(level, center, radius);
        // 释放：一记闷雷般的气爆打底，冰层炸开，钟鸣拖尾，再向四个方向撒出回声，
        // 听起来才像“整片空间在一瞬间被冻住”，而不是简单地碎个玻璃。
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.9F, 0.58F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 2.0F, 0.48F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 1.5F, 0.38F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.8F, 0.52F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_HURT_FREEZE,
                SoundSource.PLAYERS, 1.3F, 0.50F);
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0;
            double x = center.x + Math.cos(angle) * radius * 0.62;
            double z = center.z + Math.sin(angle) * radius * 0.62;
            level.playSound(null, x, center.y + 0.8, z, SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, 0.85F, 0.62F);
            level.playSound(null, x, center.y + 0.4, z, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.55F, 0.46F);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 1.0, center.z,
                90, radius * 0.35, 2.0, radius * 0.35, 0.08);
        int frozen = 0;
        int halved = 0;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - radius, center.y - radius, center.z - radius,
                        center.x + radius, center.y + radius, center.z + radius),
                entity -> entity.isAlive() && enemy(player, entity)
                        && entity.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius);
        for (LivingEntity victim : victims) {
            // 冻结判定看释放瞬间的血量，避免先吃伤害后门槛被变相放宽。
            float healthBefore = victim.getHealth();
            damage(player, victim, (float)damage, ElementType.ICE);
            if (!victim.isAlive()) continue;
            // 血量在阈值内：冻结满时长；超出血量阈值：同样必定冻结，但时长减半。
            boolean overLimit = healthBefore > healthLimit;
            int ticks = overLimit ? Math.max(20, freezeTicks / 2) : freezeTicks;
            ElementReactionSystem.deepFreeze(player, victim, ticks, damageTaken, reaction);
            frozen++;
            if (overLimit) halved++;
            level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                    victim.getZ(), 12, victim.getBbWidth() * 0.5, victim.getBbHeight() * 0.4,
                    victim.getBbWidth() * 0.5, 0.03);
        }
        player.displayClientMessage(Component.translatable("message.dimensional_mana.subzero_released",
                layers, Math.round((float)damage), (int)Math.round(radius), freezeTicks / 20), true);
        if (frozen > 0)
            player.displayClientMessage(Component.translatable("message.dimensional_mana.subzero_frozen",
                    frozen, halved), true);
        SpellSyncPayload.send(player);
    }

    /** 到期自动释放；玩家死亡、换维度或下线时只清状态，不结算伤害。 */
    private static void tickDomains(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, DomainField>> iterator = DOMAINS.entrySet().iterator();
        while (iterator.hasNext()) {
            DomainField field = iterator.next().getValue();
            if (field.level != level) continue;
            Entity entity = level.getEntity(field.owner);
            if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
                iterator.remove();
                if (entity instanceof ServerPlayer player) {
                    setDomainSlow(player, false);
                    SpellSyncPayload.send(player);
                }
                continue;
            }
            if (now >= field.expiresAt) {
                iterator.remove();
                releaseDomain(player, field);
                continue;
            }
            if ((now - field.born) % 20 == 0) sendDomainField(player, field);
            // 叠层期间每 3 秒一声低沉的嗡鸣：法阵在蓄能，周围的玩家也能感觉到压迫感。
            if ((now - field.born) % 60 == 30)
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 0.55F, 0.48F + field.layers * 0.02F);
            if ((now - field.born) % 10 == 0) SpellSyncPayload.send(player);
        }
    }

    public static int domainLayers(ServerPlayer player) {
        DomainField field = DOMAINS.get(player.getUUID());
        return field == null ? 0 : field.layers;
    }

    public static int domainTicks(ServerPlayer player) {
        DomainField field = DOMAINS.get(player.getUUID());
        return field == null ? 0 : (int)Math.max(0L, field.expiresAt - player.level().getGameTime());
    }

    private static void setDomainSlow(ServerPlayer player, boolean active) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) speed.addOrUpdateTransientModifier(new AttributeModifier(DOMAIN_SLOW,
                -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else speed.removeModifier(DOMAIN_SLOW);
    }

    // ───────────────────────── 伏特状态 ─────────────────────────

    /** 伏特状态：上 12 秒 buff；带电外观由客户端按效果自动绘制，不需要每个客人单独发包。 */
    private static void beginVoltState(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        effect(player, NewSpellContent.VOLT_STATE, VOLT_DURATION);
        VOLT_NEXT_ZAP.put(player.getUUID(), now + VOLT_ZAP_INTERVAL);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.volt_state_cast",
                VOLT_DURATION / 20), true);
        // 起手：一记雷杆炸响定调，再补两声高频电弧，听起来像整具身体被电流灌满。
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(),
                SoundSource.PLAYERS, 1.15F, 1.35F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.0F, 1.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.55F, 1.6F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
                60, 0.6, 1.0, 0.6, 0.35);
    }

    /** 每刻维护：伏特状态期间每 0.5 秒电击一次贴到身上的敌人，伤害无视受击无敌帧。 */
    private static void tickVoltState(ServerPlayer player, long now) {
        if (!player.hasEffect(NewSpellContent.VOLT_STATE)) {
            VOLT_NEXT_ZAP.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();
        double half = player.getBbHeight() * 0.5;
        if (now % 4 == 0) {
            // 身上的电弧：少量高速电火花绕身飞，客户端另有缠绕闪电本体。
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(),
                    player.getY() + half, player.getZ(), 4, 0.45, player.getBbHeight() * 0.42, 0.45, 0.16);
        }
        long next = VOLT_NEXT_ZAP.getOrDefault(player.getUUID(), 0L);
        if (now < next) return;
        VOLT_NEXT_ZAP.put(player.getUUID(), now + VOLT_ZAP_INTERVAL);
        Vec3 center = player.position().add(0, half, 0);
        for (LivingEntity victim : enemies(player, center, VOLT_ZAP_RADIUS + player.getBbWidth())) {
            damage(player, victim, VOLT_ZAP_DAMAGE, ElementType.LIGHTNING);
            Vec3 at = victim.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.35, 0.5, 0.35, 0.28);
        }
        if (now % 20 == 0)
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.35F, 1.85F);
    }

    // ───────────────────────── 天雷滚滚 ─────────────────────────

    /** 前摇开始：清掉上一轮的追加记录；15 秒读条期间可以 Alt+左键补雷球。 */
    public static void beginTempestCharge(ServerPlayer player) {
        TEMPEST_CHARGES.put(player.getUUID(), new TempestCharge(player.serverLevel(), player.getUUID()));
    }

    /** Alt+左键：前摇期间追加一颗略小的雷球，每次 555 魔素，最多两次。 */
    public static void addTempestBall(ServerPlayer player) {
        TempestCharge charge = TEMPEST_CHARGES.get(player.getUUID());
        if (charge == null) return;
        if (!SpellDefinition.THUNDER_TEMPEST.id().equals(SpellCasting.snapshot(player).spellId())) {
            TEMPEST_CHARGES.remove(player.getUUID());
            return;
        }
        if (charge.extras >= TEMPEST_MAX_EXTRA) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.thunder_tempest_full",
                    TEMPEST_MAX_EXTRA), true);
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, TEMPEST_EXTRA_COST);
        if (!ManaCheats.hasMana(player, cost)) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.thunder_tempest_no_mana",
                    (int)Math.ceil(cost)), true);
            return;
        }
        if (!ManaData.spendMana(player, TEMPEST_EXTRA_COST)) return;
        SpellCasting.syncMana(player);
        charge.extras++;
        player.displayClientMessage(Component.translatable("message.dimensional_mana.thunder_tempest_extra",
                charge.extras, TEMPEST_MAX_EXTRA), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                0.8F, 1.05F + charge.extras * 0.12F);
        player.playNotifySound(SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                0.35F, 1.45F + charge.extras * 0.1F);
        SpellSyncPayload.send(player);
    }

    /** 前摇被打断或结束后丢弃追加记录，避免下一次施法带上旧数据。 */
    private static void pruneTempestCharge(ServerPlayer player) {
        if (!TEMPEST_CHARGES.containsKey(player.getUUID())) return;
        if (!SpellDefinition.THUNDER_TEMPEST.id().equals(SpellCasting.snapshot(player).spellId()))
            TEMPEST_CHARGES.remove(player.getUUID());
    }

    /** 雷球中心：跟着施法者头顶走。 */
    public static Vec3 tempestBallCenter(ServerPlayer player) {
        return player.position().add(0, player.getBbHeight() + TEMPEST_BALL_HEIGHT, 0);
    }

    /** 前摇结束：放出主雷球与追加雷球，开始 30 秒天雷轰炸。 */
    private static void beginThunderTempest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        TempestCharge charge = TEMPEST_CHARGES.remove(player.getUUID());
        int extras = charge == null ? 0 : Math.max(0, Math.min(TEMPEST_MAX_EXTRA, charge.extras));
        TEMPESTS.remove(player.getUUID());
        TempestField field = new TempestField(level, player.getUUID(), now, now + TEMPEST_DURATION, extras);
        TEMPESTS.put(player.getUUID(), field);
        sendTempestBall(player, field);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.thunder_tempest_start",
                TEMPEST_DURATION / 20, extras), true);
        Vec3 ball = tempestBallCenter(player);
        // 开场：雷电球成形的爆响 + 低频轰鸣铺底 + 金属共振，听起来像天空被撕开一道口子。
        level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 1.9F, 0.72F);
        level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.TRIDENT_THUNDER.value(),
                SoundSource.PLAYERS, 1.5F, 1.05F);
        level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 1.1F, 0.55F);
        level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.4F, 0.85F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ball.x, ball.y, ball.z,
                90, 1.4, 1.2, 1.4, 0.5);
        level.sendParticles(ParticleTypes.FLASH, ball.x, ball.y, ball.z, 3, 0, 0, 0, 0);
        SpellSyncPayload.send(player);
    }

    /** 把雷球状态同步给客户端；追加数量编码在 end.y 与 start.y 的差值里。 */
    private static void sendTempestBall(ServerPlayer player, TempestField field) {
        Vec3 ball = tempestBallCenter(player);
        // 持续时间固定发满 30 秒：客户端靠“超过 5 秒没刷新就收起”清理，避免刷新后时长被越改越短。
        SpellVisualPayload.send(player, SpellDefinition.THUNDER_TEMPEST, SpellVisualPayload.TEMPEST_BALL,
                player.getId(), ball, ball.add(0, field.extras, 0), TEMPEST_DURATION);
    }

    /** 移除雷球视觉（结束时由 TEMPEST_END 统一收起）。 */
    private static void sendTempestEnd(ServerPlayer player) {
        Vec3 ball = tempestBallCenter(player);
        SpellVisualPayload.send(player, SpellDefinition.THUNDER_TEMPEST, SpellVisualPayload.TEMPEST_END,
                player.getId(), ball, ball, 1);
    }

    /**
     * 每 8 刻开始一轮攻击：主球立即攻击，已有的附加球分别延后 1、2 刻攻击。
     * 每颗球都独立随机挑选最多三个不同目标；不同雷球之间允许抽中同一目标。
     */
    private static void beginTempestStrikeRound(ServerPlayer player, TempestField field, long now) {
        Vec3 center = tempestBallCenter(player);
        List<LivingEntity> enemies = enemies(player, center, TEMPEST_RANGE);
        if (enemies.isEmpty()) return;
        int ballCount = 1 + field.extras;
        for (int ballIndex = 0; ballIndex < ballCount; ballIndex++) {
            List<LivingEntity> pool = new ArrayList<>(enemies);
            // RandomSource 不是 java.util.Random，这里逐颗球独立洗牌，保证单颗球不会重复攻击同一目标。
            for (int i = pool.size() - 1; i > 0; i--) {
                int j = player.getRandom().nextInt(i + 1);
                LivingEntity swap = pool.get(i);
                pool.set(i, pool.get(j));
                pool.set(j, swap);
            }
            int attacks = Math.min(TEMPEST_MAX_TARGETS, pool.size());
            for (int targetIndex = 0; targetIndex < attacks; targetIndex++)
                field.pendingStrikes.addLast(new TempestStrike(pool.get(targetIndex).getUUID(), ballIndex,
                        now + ballIndex));
        }
        tickTempestStrikeRound(player, field, now);
    }

    /** 结算这一刻轮到的雷球；目标已死亡、离开范围或被方块挡住时，本发攻击作废。 */
    private static void tickTempestStrikeRound(ServerPlayer player, TempestField field, long now) {
        while (!field.pendingStrikes.isEmpty() && field.pendingStrikes.peekFirst().at() <= now) {
            TempestStrike strike = field.pendingStrikes.removeFirst();
            Entity entity = field.level.getEntity(strike.target());
            if (!(entity instanceof LivingEntity victim) || !enemy(player, victim)) continue;
            Vec3 center = tempestBallCenter(player);
            if (victim.getBoundingBox().distanceToSqr(center) > TEMPEST_RANGE * TEMPEST_RANGE
                    || !clear(player, center.add(0, 0.4, 0), victim.getBoundingBox().getCenter())) continue;
            strikeOneTarget(player, victim, tempestStrikeOrigin(field, center, strike.ballIndex(), now), field.level);
        }
    }

    /** 与客户端公转轨迹一致的攻击起点，主球从中心出手，附加球从各自所在位置出手。 */
    private static Vec3 tempestStrikeOrigin(TempestField field, Vec3 center, int ballIndex, long now) {
        if (ballIndex <= 0) return center;
        int extraIndex = ballIndex - 1;
        double age = now - field.born;
        double angle = age * 0.07 + extraIndex * Math.PI + Math.PI / 2.0;
        double radius = 1.15;
        double orbit = radius * 1.85;
        return center.add(Math.cos(angle) * orbit, Math.sin(angle * 1.3) * radius * 0.45,
                Math.sin(angle) * orbit);
    }

    /** 单发电击：8 点雷伤、无视无敌帧、留 1 秒残影，命中后按概率挂极性紊乱。 */
    private static void strikeOneTarget(ServerPlayer player, LivingEntity victim, Vec3 ball, ServerLevel level) {
        // 雷击无视护甲与法术抗性的通用减免，但施法者一侧的强度、法杖固定增伤和暴击仍须完整结算。
        float raw = SpellAttributes.calculateUnresistedSpellDamage(player, victim, TEMPEST_STRIKE_DAMAGE);
        raw = ElementReactionSystem.elementalDamage(player, victim, ElementType.LIGHTNING, raw);
        hurt(player, victim, ElementType.LIGHTNING.id(), raw);
        applyPolarity(player, victim);
        SpellVisualPayload.send(player, SpellDefinition.THUNDER_TEMPEST, SpellVisualPayload.TEMPEST_STRIKE,
                victim.getId(), ball, victim.getBoundingBox().getCenter(), TEMPEST_STRIKE_TRACE);
        Vec3 at = victim.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 18, 0.4, 0.7, 0.4, 0.35);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, victim.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.55F, 1.30F + player.getRandom().nextFloat() * 0.25F);
    }

    /** 命中后 55% 挂 15 秒极性紊乱；已经紊乱的目标每次命中 25% 刷新时长。 */
    private static void applyPolarity(ServerPlayer player, LivingEntity victim) {
        if (victim.hasEffect(NewSpellContent.POLARITY_DISORDER)) {
            if (player.getRandom().nextFloat() >= TEMPEST_POLARITY_REFRESH) return;
            // 先摘掉再重挂，保证刷新一定是完整的 15 秒。
            victim.removeEffect(NewSpellContent.POLARITY_DISORDER);
        } else if (player.getRandom().nextFloat() >= TEMPEST_POLARITY_CHANCE) {
            return;
        }
        effect(victim, NewSpellContent.POLARITY_DISORDER, TEMPEST_POLARITY_TICKS);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.polarity_disorder",
                victim.getName()), true);
        if (victim.level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
    }

    /** 30 秒结束：雷球塌缩成电磁漩涡，先吸 4 秒再爆炸。 */
    private static void beginTempestPull(ServerPlayer player, TempestField field) {
        ServerLevel level = field.level;
        long now = level.getGameTime();
        Vec3 center = tempestBallCenter(player);
        TEMPEST_BLASTS.put(player.getUUID(), new TempestBlast(level, player.getUUID(), center, now + TEMPEST_PULL_TICKS));
        sendTempestEnd(player);
        SpellVisualPayload.send(player, SpellDefinition.THUNDER_TEMPEST, SpellVisualPayload.TEMPEST_PULL,
                player.getId(), center, center, TEMPEST_PULL_TICKS);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.thunder_tempest_pull"), true);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.PLAYERS, 1.7F, 0.62F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 1.2F, 0.55F);
        SpellSyncPayload.send(player);
    }

    /** 吸附：把 16 格内的敌人一点点往漩涡中心拖。 */
    private static void pullIntoVortex(ServerPlayer owner, TempestBlast blast, long now) {
        ServerLevel level = blast.level;
        Vec3 center = blast.center;
        boolean spark = now % 4 == 0;
        for (LivingEntity victim : enemies(owner, center, TEMPEST_BLAST_RANGE)) {
            Vec3 offset = center.subtract(victim.position());
            double distance = offset.length();
            if (distance < 1.4 || distance > TEMPEST_BLAST_RANGE) continue;
            Vec3 dir = offset.normalize();
            double step = Math.min(0.26, 0.05 + distance * 0.022);
            CompoundTag push = new CompoundTag();
            push.putDouble("x", dir.x * step);
            push.putDouble("z", dir.z * step);
            push.putInt("ticks", 1);
            victim.getPersistentData().put("DimensionalManaSpellPush", push);
            victim.hurtMarked = true;
            if (spark) {
                Vec3 at = victim.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.1);
            }
        }
        if (now % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = owner.getRandom().nextDouble() * Math.PI * 2.0;
                double radius = 2.0 + owner.getRandom().nextDouble() * (TEMPEST_BLAST_RANGE - 2.0);
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                double y = center.y + (owner.getRandom().nextDouble() - 0.5) * 2.4;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.05, 0.05, 0.05, 0.02);
            }
        }
    }

    /** 电磁爆炸：88 点雷伤，无视护甲/法术抗性/无敌帧，并把范围内的敌人炸开。 */
    private static void detonateTempest(ServerPlayer player, TempestBlast blast) {
        ServerLevel level = blast.level;
        Vec3 center = blast.center;
        SpellVisualPayload.send(player, SpellDefinition.THUNDER_TEMPEST, SpellVisualPayload.TEMPEST_BLAST,
                player.getId(), center, center.add(0, TEMPEST_BLAST_RANGE, 0), 40);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                4, 1.4, 1.0, 1.4, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z,
                180, 2.6, 1.8, 2.6, 0.42);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 4, 0, 0, 0, 0);
        // 收尾：滚雷 + 监守者声波 + 爆炸，三层叠上去，远处也能听到。
        level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 2.4F, 0.60F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.9F, 0.55F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.7F, 0.65F);
        int hit = 0;
        for (LivingEntity victim : enemies(player, center, TEMPEST_BLAST_RANGE)) {
            float raw = SpellAttributes.calculateUnresistedSpellDamage(player, victim, TEMPEST_BLAST_DAMAGE);
            raw = ElementReactionSystem.elementalDamage(player, victim, ElementType.LIGHTNING, raw);
            hurt(player, victim, ElementType.LIGHTNING.id(), raw);
            knock(center, victim, 4.0);
            hit++;
        }
        player.displayClientMessage(Component.translatable("message.dimensional_mana.thunder_tempest_blast", hit), true);
        SpellSyncPayload.send(player);
    }

    /** 每刻维护所有雷球与收束中的漩涡；施法者离开或死亡时安静收起。 */
    private static void tickTempests(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, TempestField>> fieldIterator = TEMPESTS.entrySet().iterator();
        while (fieldIterator.hasNext()) {
            TempestField field = fieldIterator.next().getValue();
            if (field.level != level) continue;
            Entity entity = level.getEntity(field.owner);
            if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
                fieldIterator.remove();
                TEMPEST_CHARGES.remove(field.owner);
                if (entity instanceof ServerPlayer player) {
                    sendTempestEnd(player);
                    SpellSyncPayload.send(player);
                }
                continue;
            }
            // 换维度后旧世界的雷球不可能再跟上，直接摘掉状态，避免跨世界残留。
            if (player.serverLevel() != field.level) {
                fieldIterator.remove();
                TEMPEST_CHARGES.remove(field.owner);
                continue;
            }
            if (now >= field.until) {
                fieldIterator.remove();
                beginTempestPull(player, field);
                continue;
            }
            // 先结算上一轮排队的附加球，再按 8 刻间隔开启下一轮。
            tickTempestStrikeRound(player, field, now);
            if (now >= field.nextStrike) {
                field.nextStrike = now + TEMPEST_STRIKE_INTERVAL;
                beginTempestStrikeRound(player, field, now);
            }
            if (now >= field.nextVisual) {
                field.nextVisual = now + 20;
                sendTempestBall(player, field);
                SpellSyncPayload.send(player);
            }
            Vec3 ball = tempestBallCenter(player);
            if (now % 3 == 0) {
                double angle = now * 0.45 + player.getRandom().nextDouble() * 0.8;
                double radius = 0.9 + field.extras * 0.25;
                double x = ball.x + Math.cos(angle) * radius;
                double z = ball.z + Math.sin(angle) * radius;
                double y = ball.y + Math.sin(angle * 1.7) * 0.55;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.15, 0.15, 0.15, 0.06);
            }
            if (now % 40 == 0)
                level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 0.6F, 0.90F);
            // 每 6 秒远处滚一声闷雷，让 30 秒里一直有天在翻涌的感觉。
            if (now % 120 == 0)
                level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER, 0.55F, 0.50F);
        }
        Iterator<Map.Entry<UUID, TempestBlast>> blastIterator = TEMPEST_BLASTS.entrySet().iterator();
        while (blastIterator.hasNext()) {
            TempestBlast blast = blastIterator.next().getValue();
            if (blast.level != level) continue;
            Entity entity = level.getEntity(blast.owner);
            if (!(entity instanceof ServerPlayer player) || !player.isAlive()) {
                blastIterator.remove();
                continue;
            }
            if (now >= blast.until) {
                blastIterator.remove();
                detonateTempest(player, blast);
                continue;
            }
            pullIntoVortex(player, blast, now);
            if (now % 20 == 0) {
                SpellVisualPayload.send(player, SpellDefinition.THUNDER_TEMPEST, SpellVisualPayload.TEMPEST_PULL,
                        player.getId(), blast.center, blast.center, TEMPEST_PULL_TICKS);
            }
        }
    }

    /** 雷球剩余时长；前摇/吸附阶段也一并汇报给 HUD。 */
    public static int tempestTicks(ServerPlayer player) {
        TempestField field = TEMPESTS.get(player.getUUID());
        if (field != null) return (int)Math.max(0L, field.until - player.level().getGameTime());
        TempestBlast blast = TEMPEST_BLASTS.get(player.getUUID());
        return blast == null ? 0 : (int)Math.max(0L, blast.until - player.level().getGameTime());
    }

    /** 追加雷球数量：前摇期间读追加记录，释放后读雷球本体。 */
    public static int tempestExtras(ServerPlayer player) {
        TempestField field = TEMPESTS.get(player.getUUID());
        if (field != null) return field.extras;
        TempestCharge charge = TEMPEST_CHARGES.get(player.getUUID());
        if (charge == null) return 0;
        if (!SpellDefinition.THUNDER_TEMPEST.id().equals(SpellCasting.snapshot(player).spellId())) return 0;
        return charge.extras;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, path);
    }

    private static Barrier spawnBarrier(ServerPlayer player, BarrierField field, int index) {
        ServerLevel level = field.level;
        long now = level.getGameTime();
        Vec3 center = barrierCenter(level, field, index);
        double radius = index == 0 ? BARRIER_CORE_RADIUS : BARRIER_SEAL_RADIUS;
        Barrier barrier = new Barrier(level, player.getUUID(), center, field.forward, radius,
                now + BARRIER_LIFETIME, now);
        BARRIERS.add(barrier);
        SpellVisualPayload.send(player, SpellDefinition.MULTI_BARRIER_RAY, SpellVisualPayload.BARRIER, -1,
                center, center.add(field.forward.scale(radius)), BARRIER_LIFETIME);
        return barrier;
    }

    /** 九个法阵排成一圈环，中心阵更大；贴墙时整体前移，避免阵纹埋进方块。 */
    private static Vec3 barrierCenter(ServerLevel level, BarrierField field, int index) {
        Vec3 point = field.origin;
        if (index == 0) point = point.add(field.forward.scale(BARRIER_DISTANCE));
        else {
            double angle = (index - 1) * Math.PI * 2.0 / (BARRIER_MAX - 1) + Math.PI / 2.0;
            point = point.add(field.forward.scale(BARRIER_DISTANCE))
                    .add(field.right.scale(Math.cos(angle) * BARRIER_RING))
                    .add(0, Math.sin(angle) * BARRIER_RING, 0);
        }
        return clearPoint(level, field.origin, point);
    }

    private static Vec3 clearPoint(ServerLevel level, Vec3 from, Vec3 to) {
        if (clear(level, from, to)) return to;
        Vec3 offset = to.subtract(from);
        for (double scale = 0.8; scale > 0.2; scale -= 0.15) {
            Vec3 candidate = from.add(offset.scale(scale));
            if (clear(level, from, candidate)) return candidate;
        }
        return from.add(offset.scale(0.2));
    }

    private static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    private static void removePlayerBarriers(UUID owner) {
        BARRIERS.removeIf(barrier -> barrier.owner.equals(owner));
    }

    /** 优先打玩家刚攻击过的生物，其次敌对生物，最后才是最近的普通目标。 */
    private static LivingEntity barrierTarget(ServerPlayer owner, Vec3 from) {
        LivingEntity attacked = owner.getLastHurtMob();
        if (validBarrierTarget(owner, from, attacked)) return attacked;
        List<LivingEntity> candidates = owner.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(from, from).inflate(BARRIER_RANGE), target -> validBarrierTarget(owner, from, target));
        LivingEntity best = null;
        for (LivingEntity candidate : candidates) {
            if (best == null) { best = candidate; continue; }
            boolean candidateEnemy = candidate instanceof net.minecraft.world.entity.monster.Enemy;
            boolean bestEnemy = best instanceof net.minecraft.world.entity.monster.Enemy;
            if (candidateEnemy != bestEnemy) {
                if (candidateEnemy) best = candidate;
                continue;
            }
            if (candidate.distanceToSqr(from) < best.distanceToSqr(from)) best = candidate;
        }
        return best;
    }

    private static boolean validBarrierTarget(ServerPlayer owner, Vec3 from, LivingEntity target) {
        return target != null && target.isAlive() && target.distanceToSqr(from) <= BARRIER_RANGE * BARRIER_RANGE
                && enemy(owner, target) && clear(owner, from, target.getBoundingBox().getCenter());
    }

    private static void fireBarrier(ServerPlayer owner, Barrier barrier) {
        LivingEntity target = barrierTarget(owner, barrier.position);
        if (target == null) return;
        Vec3 start = barrier.position;
        Vec3 aim = target.getBoundingBox().getCenter();
        Vec3 direction = aim.subtract(start);
        if (direction.lengthSqr() < 0.0001) return;
        direction = direction.normalize();
        Vec3 end = blockEnd(owner, start, start.add(direction.scale(BARRIER_RANGE)));
        Set<UUID> pierced = new HashSet<>();
        int hits = 0;
        Vec3 last = null;
        for (LivingEntity victim : pathTargets(owner, start, end, 0.55)) {
            if (hits >= 5) break;
            Vec3 center = victim.getBoundingBox().getCenter();
            if (!pierced.add(victim.getUUID()) || !clear(owner, start, center)) continue;
            damage(owner, victim, 32.0F, null);
            if (owner.getRandom().nextFloat() < 0.10F) barrierDrain(owner, victim);
            hits++;
            last = center;
        }
        if (last != null)
            SpellVisualPayload.send(owner, SpellDefinition.MULTI_BARRIER_RAY, SpellVisualPayload.SHOT, -1,
                    start, last, 14);
    }

    /** 十次射击里约有一次：抽走目标 1% 最大魔素，并追加 1.25% 最大生命伤害。 */
    private static void barrierDrain(ServerPlayer owner, LivingEntity victim) {
        ManaData.Snapshot mana = ManaData.get(victim);
        double stolen = mana.maxMana() * 0.01;
        if (stolen > 0) {
            ManaData.setMana(victim, mana.mana() - stolen, true);
            if (victim instanceof ServerPlayer player) SpellCasting.syncMana(player);
        }
        hurt(owner, victim, victim.getMaxHealth() * 0.0125F);
        Vec3 center = victim.getBoundingBox().getCenter();
        SpellVisualPayload.send(owner, SpellDefinition.MULTI_BARRIER_RAY, SpellVisualPayload.SHOT, -1,
                center, center.add(0, 0.01, 0), 16);
        if (owner.level() instanceof ServerLevel level)
            level.playSound(null, victim.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.35F);
    }
    public static void damage(LivingEntity caster, LivingEntity target, float base, ElementType element) {
        if (!(caster.level() instanceof ServerLevel) || !enemy(caster, target)) return;
        float value = SpellAttributes.calculateSpellDamage(caster, target, base);
        value = element == null ? ElementReactionSystem.arcaneDamage(caster, target, value)
                : ElementReactionSystem.elementalDamage(caster, target, element, value);
        hurt(caster, target, element == null ? MagicEquipment.ARCANE : element.id(), value);
    }

    /**
     * 万剑归一飞剑的伤害入口。
     *
     * <p>和 {@link #damage} 的唯一区别是不再二次判断敌对关系：目标是出剑时按“航线 + 玩家刚打中的目标”
     * 筛好的，所以飞剑只要飞到了就一定会落地成伤，不会因为假人 / 召唤物的 {@code canAttack}
     * 判定差异被整段吞掉。返回这次伤害是否真的落了地。</p>
     */
    public static boolean swordDamage(LivingEntity caster, LivingEntity target, float base, ElementType element) {
        if (target == null || target.level().isClientSide || !(caster.level() instanceof ServerLevel level))
            return false;
        float value = SpellAttributes.calculateSpellDamage(caster, target, base);
        value = element == null ? ElementReactionSystem.arcaneDamage(caster, target, value)
                : ElementReactionSystem.elementalDamage(caster, target, element, value);
        if (value <= 0.0F) return false;
        boolean landed = SpellDamage.hitElement(level, caster, target,
                element == null ? MagicEquipment.ARCANE : element.id(), value);
        if (landed) caster.setLastHurtMob(target);
        if (target.hasEffect(NewSpellContent.POLARITY_DISORDER)) target.invulnerableTime = 0;
        return landed;
    }

    /**
     * 所有法术伤害最终都落在这里，由 {@link SpellDamage} 统一走“无视受击冷却”的结算；
     * 同一目标连续吃法术或紧跟元素反应时，每一发都能结算全额。
     */
    private static void hurt(LivingEntity caster, LivingEntity target, float value) {
        if (SpellDamage.hit(caster.level(), caster, target, value)) caster.setLastHurtMob(target);
        // 极性紊乱期间不允许留下任何受击保护，否则同一轮落雷之后的其他伤害会被无敌帧吞掉。
        if (target.hasEffect(NewSpellContent.POLARITY_DISORDER)) target.invulnerableTime = 0;
    }

    /** 带元素归属的落地入口：增伤数值已经在外面算好，这里只决定这次命中算哪种元素伤害。 */
    private static void hurt(LivingEntity caster, LivingEntity target, String elementId, float value) {
        if (SpellDamage.hitElement(caster.level(), caster, target, elementId, value)) caster.setLastHurtMob(target);
        // 极性紊乱期间不允许留下任何受击保护，否则同一轮落雷之后的其他伤害会被无敌帧吞掉。
        if (target.hasEffect(NewSpellContent.POLARITY_DISORDER)) target.invulnerableTime = 0;
    }
    public static List<LivingEntity> enemies(LivingEntity owner, Vec3 center, double radius) {
        return owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> enemy(owner, e) && e.getBoundingBox().distanceToSqr(center) <= radius * radius
                        && clear(owner, center.add(0, 0.4, 0), e.getBoundingBox().getCenter()));
    }
    public static boolean clear(LivingEntity caster, Vec3 start, Vec3 end) {
        return caster.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getType() == HitResult.Type.MISS;
    }
    public static Vec3 blockEnd(LivingEntity caster, Vec3 start, Vec3 end) {
        HitResult hit = caster.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }
    public static List<LivingEntity> pathTargets(LivingEntity caster, Vec3 start, Vec3 end, double width) {
        List<LivingEntity> result = new ArrayList<>(caster.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(width + 1), e -> enemy(caster, e)
                        && (e.getBoundingBox().inflate(width).contains(start) || e.getBoundingBox().inflate(width).clip(start, end).isPresent())));
        result.sort(Comparator.comparingDouble(e -> e.distanceToSqr(start)));
        return result;
    }
    public static void pathDamage(LivingEntity player, Vec3 start, Vec3 end, double width, float amount,
                                  ElementType element, Set<UUID> hit, int knock) {
        for (LivingEntity target : pathTargets(player, start, end, width)) {
            if (!hit.add(target.getUUID()) || !clear(player, start, target.getBoundingBox().getCenter())) continue;
            damage(player, target, amount, element);
            if (knock > 0) knock(start, target, knock);
        }
    }
    public static void knock(Vec3 start, LivingEntity target, double blocks) {
        Vec3 dir = target.position().subtract(start).multiply(1, 0, 1).normalize();
        if (dir.lengthSqr() < 0.01) dir = new Vec3(0, 0, 1);
        CompoundTag push = new CompoundTag();
        push.putDouble("x", dir.x * blocks / 10); push.putDouble("z", dir.z * blocks / 10); push.putInt("ticks", 10);
        target.getPersistentData().put("DimensionalManaSpellPush", push);
        target.setDeltaMovement(0, 0.12, 0); target.hurtMarked = true;
    }
    private static Vec3 safeEnd(ServerPlayer player, Vec3 dir, double range) {
        Vec3 start = player.position(), last = start;
        for (double step = 0.2; step <= range + 0.01; step += 0.2) {
            Vec3 next = start.add(dir.scale(step));
            if (!player.serverLevel().hasChunkAt(BlockPos.containing(next))
                    || !player.level().getWorldBorder().isWithinBounds(BlockPos.containing(next))
                    || !player.level().noCollision(player, player.getBoundingBox().move(next.subtract(start)))) break;
            last = next;
        }
        return last;
    }
    public static Vec3 ground(ServerLevel level, Vec3 at) {
        HitResult hit = level.clip(new ClipContext(at.add(0, 2, 0), at.add(0, -5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }
    private static void zone(ServerPlayer owner, Vec3 center, double radius, int duration, SpellDefinition spell) {
        long now = owner.level().getGameTime();
        ZONES.add(new Zone(owner.serverLevel(), owner.getUUID(), center, radius, now + duration, spell, now, new HashSet<>()));
    }
    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        tickLavaCooling(level, now);
        tickSprings(level, now);
        tickDomains(level, now);
        tickTempests(level, now);
        WindSpells.tickLevel(level, now);
        EarthSpells.tickLevel(level, now);
        ArcaneSpells.tickLevel(level, now);
        WoodSpells.tickLevel(level, now);
        MetalSpells.tickLevel(level, now);
        LightSpells.tickLevel(level, now);
        PoisonSpells.tickLevel(level, now);
        SonicSpells.tickLevel(level, now);
        // 霜冰有固定寿命（最多 8 秒），到期由环境系统换回水。
        SpellEnvironmentSystem.tickLevel(level, now);
        Iterator<Volley> volleys = VOLLEYS.iterator();
        while (volleys.hasNext()) {
            Volley volley = volleys.next(); if (volley.level != level || volley.at > now) continue;
            volleys.remove(); Entity owner = level.getEntity(volley.owner);
            if (owner instanceof ServerPlayer player && player.isAlive() && !cannotCast(player))
                projectile(player, SpellDefinition.GALE_SLASHES, volley.start, volley.direction, volley.index);
        }
        Iterator<Map.Entry<UUID, Dash>> dashes = DASHES.entrySet().iterator();
        while (dashes.hasNext()) {
            var entry = dashes.next(); Dash dash = entry.getValue(); if (dash.level != level) continue;
            Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof ServerPlayer player) || !player.isAlive() || immobilized(player)) { dashes.remove(); continue; }
            Vec3 start = player.position(), end = safeEnd(player, dash.direction, Math.min(1.0, dash.remaining));
            double moved = start.distanceTo(end);
            if (moved < 0.05) { dashes.remove(); continue; }
            if (dash.spell == SpellDefinition.METAL_DASH)
                pathDamage(player, start.add(0, 0.8, 0), end.add(0, 0.8, 0), 0.7, 6, ElementType.METAL, dash.hit, 2);
            else {
                Vec3 floor = ground(level, end);
                if (Math.abs(floor.y - end.y) < 1.3) {
                    zone(player, floor.add(0, 0.03, 0), 0.85, 40, SpellDefinition.ICE_SLIDE);
                    visual(player, SpellDefinition.ICE_SLIDE, floor, floor, -1, 40);
                }
            }
            player.teleportTo(end.x, end.y, end.z); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            dash.remaining -= moved; if (dash.remaining < 0.05) dashes.remove();
        }
        Iterator<Zone> zones = ZONES.iterator();
        while (zones.hasNext()) {
            Zone z = zones.next(); if (z.level != level) continue;
            if (now > z.until) { zones.remove(); continue; }
            if ((now - z.born) % 5 != 0) continue;
            Entity entity = level.getEntity(z.owner);
            if (!(entity instanceof LivingEntity owner) || !owner.isAlive()) continue;
            if (z.spell == SpellDefinition.ICE_SLIDE && now > z.born && (now - z.born) % 20 == 0) {
                SpellEnvironmentSystem.freezeNear(level, z.center);
            }
            for (LivingEntity victim : enemies(owner, z.center, z.radius)) {
                if (z.spell == SpellDefinition.MUD_SWAMP) {
                    // 雨靴：泥沼减速免疫，连效果都不挂，省得每刻挂上又被摘掉。
                    if (!RainBootsItem.worn(victim)) effect(victim, NewSpellContent.MUD, 7);
                } else if (z.spell == SpellDefinition.ICE_SLIDE) {
                    if (victim.getY() >= z.center.y - 0.4 && victim.getY() <= z.center.y + 0.9) effect(victim, NewSpellContent.FROST, 7);
                } else if (z.spell == SpellDefinition.PLAGUE) {
                    effect(victim, NewSpellContent.PLAGUE, 7);
                    if (now > z.born && (now - z.born) % 20 == 0) {
                        float periodic = SpellAttributes.calculateSecondarySpellDamage(owner, victim, 3, z.periodicHits.add(victim.getUUID()));
                        hurt(owner, victim, ElementType.POISON.id(),
                                ElementReactionSystem.elementalDamage(owner, victim, ElementType.POISON, periodic));
                    }
                }
            }
        }
        tickCages(level, now);
        Iterator<Barrier> barriers = BARRIERS.iterator();
        while (barriers.hasNext()) {
            Barrier barrier = barriers.next(); if (barrier.level != level) continue;
            if (now >= barrier.until) { barriers.remove(); continue; }
            if (now < barrier.nextShot) continue;
            barrier.nextShot = now + BARRIER_INTERVAL;
            if (!(level.getEntity(barrier.owner) instanceof ServerPlayer owner) || !owner.isAlive()) {
                barriers.remove();
                continue;
            }
            fireBarrier(owner, barrier);
        }
        Iterator<Map.Entry<UUID, BarrierField>> fields = FIELDS.entrySet().iterator();
        while (fields.hasNext()) {
            BarrierField field = fields.next().getValue(); if (field.level != level) continue;
            Entity fieldOwner = level.getEntity(field.owner);
            boolean alive = fieldOwner instanceof ServerPlayer player && player.isAlive();
            if (now > field.expiresAt || !alive) {
                fields.remove();
                if (alive) {
                    ServerPlayer player = (ServerPlayer)fieldOwner;
                    player.displayClientMessage(Component.translatable("message.dimensional_mana.barrier_field_end",
                            field.count), true);
                    SpellSyncPayload.send(player);
                }
                continue;
            }
            if ((now - field.born) % 10 == 0) SpellSyncPayload.send((ServerPlayer)fieldOwner);
        }
    }
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        if (data.contains("DimensionalManaSpellPush")) {
            CompoundTag push = data.getCompound("DimensionalManaSpellPush");
            Vec3 before = entity.position();
            entity.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(push.getDouble("x"), 0, push.getDouble("z")));
            Vec3 after = entity.position();
            if (entity instanceof ServerPlayer p) p.connection.teleport(after.x, after.y, after.z, p.getYRot(), p.getXRot());
            if (data.contains(LOCK)) {
                CompoundTag lock = data.getCompound(LOCK);
                lock.putDouble("x", after.x); lock.putDouble("y", after.y); lock.putDouble("z", after.z);
            }
            entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
            int left = push.getInt("ticks") - 1;
            if (left <= 0 || before.distanceToSqr(after) < 0.0001) data.remove("DimensionalManaSpellPush");
            else push.putInt("ticks", left);
        }
        if (immobilized(entity)) {
            if (!data.contains(LOCK)) {
                CompoundTag lock = new CompoundTag(); lock.putDouble("x", entity.getX()); lock.putDouble("y", entity.getY()); lock.putDouble("z", entity.getZ());
                data.put(LOCK, lock);
            }
            CompoundTag lock = data.getCompound(LOCK);
            Vec3 pos = new Vec3(lock.getDouble("x"), lock.getDouble("y"), lock.getDouble("z"));
            if (entity.distanceToSqr(pos) > 0.0025) entity.teleportTo(pos.x, pos.y, pos.z);
            entity.setDeltaMovement(Vec3.ZERO); entity.hurtMarked = true; entity.resetFallDistance(); entity.stopUsingItem();
            if (entity instanceof Mob mob) {
                if (!data.contains("DimensionalManaSpellOldNoAI")) data.putBoolean("DimensionalManaSpellOldNoAI", mob.isNoAi());
                mob.setNoAi(true); mob.getNavigation().stop(); mob.setTarget(null);
            }
        } else {
            data.remove(LOCK);
            if (entity instanceof Mob mob && data.contains("DimensionalManaSpellOldNoAI")) {
                mob.setNoAi(data.getBoolean("DimensionalManaSpellOldNoAI")); data.remove("DimensionalManaSpellOldNoAI");
            }
        }
        if (entity.hasEffect(NewSpellContent.LIGHTNESS)) entity.resetFallDistance();
        if (entity.hasEffect(NewSpellContent.LIFE_BLESSING) && entity.tickCount % 20 == 0) entity.heal(1);
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        if (data.contains(COFFIN) && !entity.hasEffect(NewSpellContent.ICE_COFFIN)) data.remove(COFFIN);
        if (entity.hasEffect(NewSpellContent.GLIMMER) && entity.tickCount % 20 == 0) TemporarySpellLight.illuminate(entity);
        // 磐石外壳的属性和残留数据每秒兜底核对一次。
        EarthSpells.tickLiving(entity);
        // 植入寄生：血量掉到斩杀线以下就直接处决。
        WoodSpells.tickParasite(entity);
        // 金钟罩：被牛奶清掉或到期时收回吸收数据，期间每秒清一次破甲。
        MetalSpells.tickLiving(entity);
        // 光的庇护：每秒回复生命；光蚀：按等级定期造成光属性伤害。
        LightSpells.tickLiving(entity);
        // 毒系：猛毒补一次中毒伤害、鸩毒维护层数与生命上限、强制中毒补回。
        PoisonSpells.tickLiving(entity);
        // 音系：爆震每两刻结算一次音伤。
        SonicSpells.tickLiving(entity);
    }
    public static float incoming(LivingEntity victim, DamageSource source, float amount) {
        // 断魂鸣弦的高频声波无视除斗气之外的一切防护：自定义减伤、金钟罩、磐石外壳、
        // 光之庇护、伏特状态与读条无敌都不参与，只保留后面统一结算的斗气减伤。
        if (SonicSpells.isSonic(source)) return amount;
        // 卍之波的 8 刻无敌：任何来源的伤害都不结算。
        if (victim.getPersistentData().getLong(WoodSpells.INVULNERABLE_UNTIL) > victim.level().getGameTime())
            return -1;
        // 曜光裂界读条的后 60%：施法者无敌，虚空与 /kill 这类穿透无敌的伤害除外。
        if ((source == null || !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
                && LightSpells.invulnerable(victim)) return -1;
        // 光的庇护：受到的伤害 -22%，被毒属性攻击时清毒并反伤。
        LightSpells.onIncomingDamage(victim, source);
        if (source.getEntity() instanceof LivingEntity attacker && immobilized(attacker)) return -1;
        if (victim.hasEffect(NewSpellContent.LIGHTNESS) && source.is(DamageTypeTags.IS_FALL)) return -1;
        if (victim.hasEffect(NewSpellContent.LIFE_BLESSING)) amount *= 0.8F;
        // 断魂鸣弦的鸣球：受伤 -8%，并把一半伤害沿着攻击来向反射回去。
        amount = SonicSpells.onIncomingDamage(victim, source, amount);
        // 光的庇护：受到的所有伤害 -22%。
        amount *= LightSpells.damageTakenMultiplier(victim);
        // 伏特状态：受到的所有伤害减半。
        if (victim.hasEffect(NewSpellContent.VOLT_STATE)) amount *= VOLT_DAMAGE_TAKEN;
        // 极性紊乱：受到的伤害提高 34%，自己造成的伤害降低 42%（全来源，近战与法术都算）。
        if (victim.hasEffect(NewSpellContent.POLARITY_DISORDER)) amount *= TEMPEST_POLARITY_WEAKNESS;
        if (source.getEntity() instanceof LivingEntity attacker
                && attacker.hasEffect(NewSpellContent.POLARITY_DISORDER)) amount *= TEMPEST_POLARITY_OUTPUT;
        if (victim.hasEffect(NewSpellContent.ICE_COFFIN)) {
            CompoundTag coffin = victim.getPersistentData().getCompound(COFFIN);
            float shell = coffin.getFloat("shell") - amount;
            coffin.putFloat("shell", shell);
            amount *= 0.5F;
            if (shell <= 0) {
                // 延迟到本次伤害结算后再爆裂，避免嵌套 hurt 改写主伤害。
                victim.getPersistentData().putBoolean("DimensionalManaBreakCoffin", true);
            }
        }
        return amount;
    }
    public static void afterDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        // 极性紊乱期间没有受击无敌帧：每次结算后直接清零，下一发照样吃满。
        if (victim.hasEffect(NewSpellContent.POLARITY_DISORDER)) victim.invulnerableTime = 0;
        // 万剑归一：玩家每次近战/远程命中都会触发出剑，并按剑意层数补一段金属性伤害。
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && MetalSpells.isPlayerAttack(attacker, event.getSource()))
            MetalSpells.onPlayerAttack(attacker, victim, event.getSource(), event.getNewDamage());
        if (!victim.getPersistentData().getBoolean("DimensionalManaBreakCoffin")) return;
        victim.getPersistentData().remove("DimensionalManaBreakCoffin");
        CompoundTag tag = victim.getPersistentData().getCompound(COFFIN);
        UUID ownerId = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        victim.removeEffect(NewSpellContent.ICE_COFFIN); victim.getPersistentData().remove(COFFIN);
        if (!(victim.level() instanceof ServerLevel level)) return;
        Entity entity = ownerId == null ? null : level.getEntity(ownerId);
        if (entity instanceof LivingEntity owner) {
            for (LivingEntity other : enemies(owner, victim.position(), 3)) if (other != victim) damage(owner, other, 15, ElementType.ICE);
            if (owner instanceof ServerPlayer p) visual(p, SpellDefinition.ICE_COFFIN, victim.position(), victim.position(), -1, 16);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + 1, victim.getZ(), 55, 1, 1, 1, 0.15);
    }
    public static void effectRemoved(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove event) {
        // 光蚀是强制状态：牛奶、蜂蜜、不死图腾、/effect clear 以及别的模组的清除都不放行，
        // 只有目标死亡才允许它跟着身体一起消失（存活期间只能等它自己到期）。
        if (!event.isCanceled() && event.getEntity().isAlive()
                && event.getEffect().is(NewSpellContent.LIGHT_ECLIPSE.getKey())) {
            event.setCanceled(true);
            return;
        }
        // 鸩羽千夜给出的中毒与鸩毒同样是强制状态：存活期间只允许自然到期。
        if (!event.isCanceled() && PoisonSpells.vetoRemoval(event.getEntity(), event.getEffect())) {
            event.setCanceled(true);
            return;
        }
        // 断魂鸣弦的爆震也是强制状态：四刻余震只能自己走完，牛奶与 /effect clear 一律不认。
        if (!event.isCanceled() && event.getEffect().is(NewSpellContent.SONIC_BLAST.getKey())
                && SonicSpells.blastForced(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (!event.isCanceled()) endAttached(event.getEntity(), event.getEffect());
    }
    public static void effectExpired(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired event) {
        if (!event.isCanceled() && event.getEffectInstance() != null) endAttached(event.getEntity(), event.getEffectInstance().getEffect());
    }
    private static void endAttached(LivingEntity target, Holder<MobEffect> effect) {
        if (target.level().isClientSide) return;
        SpellDefinition spell = effect.is(NewSpellContent.WATER_PRISON.getKey()) ? SpellDefinition.WATER_PRISON
                : effect.is(NewSpellContent.ICE_COFFIN.getKey()) ? SpellDefinition.ICE_COFFIN : null;
        if (spell != null) SpellVisualPayload.send(target, spell, SpellVisualPayload.END_ATTACHED, target.getId(), target.position(), target.position(), 1);
    }
    public static void heal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        if (event.getEntity().hasEffect(NewSpellContent.PLAGUE)) event.setAmount(event.getAmount() * 0.5F);
        // 鸩毒：任何方式的回血速率减半（与原版中毒的“回血”无关，这里只压治疗量）。
        if (event.getEntity().hasEffect(NewSpellContent.ZHEN_POISON)) event.setAmount(event.getAmount() * 0.5F);
        MagicEtchingSystem.limitHealing(event);
    }
    public static void jump(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        // 深渊龙击喷射期间无法跳跃。
        if (DRAGONS.containsKey(entity.getUUID())) { entity.setDeltaMovement(Vec3.ZERO); return; }
        if (immobilized(entity)) { entity.setDeltaMovement(Vec3.ZERO); return; }
        Vec3 motion = entity.getDeltaMovement();
        if (motion.y <= 0) return;
        // 伏特状态：跳跃高度 +50%；和泥沼一样按重力/阻力反求所需起跳速度，不做线性近似。
        if (entity.hasEffect(NewSpellContent.VOLT_STATE)) {
            rescaleJump(entity, motion, 1.5);
            return;
        }
        if (!entity.hasEffect(NewSpellContent.MUD)) return;
        // 跳跃高度与起跳速度并非线性关系，按原版重力/阻力反求25%高度的起跳速度。
        rescaleJump(entity, motion, 0.25);
    }
    /** 把本次起跳的竖直速度改成能达到原高度 ×scale 的速度。 */
    private static void rescaleJump(LivingEntity entity, Vec3 motion, double scale) {
        double gravity = Math.max(0.001, entity.getGravity());
        double desired = jumpHeight(motion.y, gravity) * scale;
        double low = 0, high = motion.y;
        for (int i = 0; i < 20; i++) {
            double middle = (low + high) * 0.5;
            if (jumpHeight(middle, gravity) > desired) high = middle; else low = middle;
        }
        entity.setDeltaMovement(motion.x, (low + high) * 0.5, motion.z);
    }
    private static double jumpHeight(double velocity, double gravity) {
        double height = 0;
        for (int tick = 0; tick < 200 && velocity > 0; tick++) { height += velocity; velocity = (velocity - gravity) * 0.98; }
        return height;
    }
    public static void attack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (immobilized(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        // 万剑归一：近战命中就直接召剑雨，不再依赖伤害事件，别的模组把伤害改成 0 也不会哑火。
        if (event.isCanceled()) return;
        // 招架余势（完美招架后的下一击）：这一刻的伤害已经按满蓄力算完了，这里只是把它收回去。
        if (event.getEntity() instanceof ServerPlayer attacker) BattleQiParry.consumeFullPower(attacker);
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity victim)
            MetalSpells.onMeleeHit(player, victim);
    }
    public static void use(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (immobilized(event.getEntity())) event.setCanceled(true);
    }
    public static void startItem(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (immobilized(event.getEntity())) event.setCanceled(true);
    }
    public static void useEntity(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (immobilized(event.getEntity())) event.setCanceled(true);
    }
    public static void useEntitySpecific(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
        if (immobilized(event.getEntity())) event.setCanceled(true);
    }
    public static void useBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (immobilized(event.getEntity())) event.setCanceled(true);
    }
    public static void breakBlock(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (immobilized(event.getPlayer())) event.setCanceled(true);
        // 生命之树存在期间不可被破坏。
        if (WoodSpells.protects(event.getPos())) event.setCanceled(true);
    }
    public static void visual(ServerPlayer player, SpellDefinition spell, LivingEntity target, int ticks) {
        visual(player, spell, player.getEyePosition(), target.getBoundingBox().getCenter(), target.getId(), ticks);
    }
    public static void visual(ServerPlayer player, SpellDefinition spell, Vec3 start, Vec3 end, int anchor, int ticks) {
        SpellVisualPayload.send(player, spell, SpellVisualPayload.RELEASE, anchor, start, end, ticks);
    }
    public static void clear(ServerPlayer player) {
        DASHES.remove(player.getUUID()); VOLLEYS.removeIf(v -> v.owner.equals(player.getUUID()));
        FIELDS.remove(player.getUUID());
        removePlayerBarriers(player.getUUID());
        BlazingSun sun = SUNS.remove(player.getUUID());
        if (sun != null) sun.discard();
        DragonBeam beam = DRAGONS.remove(player.getUUID());
        if (beam != null) {
            setDragonSlow(player, false);
            if (beam.start != null)
                SpellVisualPayload.send(player, SpellDefinition.ABYSS_DRAGON_STRIKE, SpellVisualPayload.BEAM_END,
                        -1, beam.start, beam.start, 1);
            SpellCasting.startCooldown(player, SpellDefinition.ABYSS_DRAGON_STRIKE);
        }
        SPRINGS.removeIf(spring -> spring.owner.equals(player.getUUID()) && spring.level == player.serverLevel());
        CAGES.removeIf(cage -> {
            boolean owned = cage.owner.equals(player.getUUID()) && cage.level == player.serverLevel();
            if (owned) SpellVisualPayload.send(player, SpellDefinition.FLAME_CAGE, SpellVisualPayload.CAGE_END,
                    -1, cage.center, cage.center, 1);
            return owned;
        });
        DomainField domain = DOMAINS.remove(player.getUUID());
        setDomainSlow(player, false);
        if (domain != null)
            SpellVisualPayload.send(player, SpellDefinition.SUBZERO_DOMAIN, SpellVisualPayload.DOMAIN_END,
                    player.getId(), player.position(), player.position(), 1);
        // 下线、死亡或换维度：追加记录、雷球与吸附漩涡一并收起，避免跨世界残留。
        TEMPEST_CHARGES.remove(player.getUUID());
        VOLT_NEXT_ZAP.remove(player.getUUID());
        boolean hadTempest = TEMPESTS.remove(player.getUUID()) != null || TEMPEST_BLASTS.remove(player.getUUID()) != null;
        if (hadTempest) sendTempestEnd(player);
        WindSpells.clear(player);
        EarthSpells.clear(player);
        ArcaneSpells.clear(player);
        WoodSpells.clear(player);
        MetalSpells.clear(player);
        LightSpells.clear(player);
        PoisonSpells.clear(player);
        SonicSpells.clear(player);
        player.getPersistentData().remove(LOCK);
        player.getPersistentData().remove("DimensionalManaSpellPush");
    }
    public static void clearAll() {
        ZONES.clear(); DASHES.clear(); VOLLEYS.clear(); BARRIERS.clear(); FIELDS.clear();
        CAGES.clear(); PENDING_CAGE_BREAKS.clear();
        SPRINGS.clear(); DRAGONS.clear(); DOMAINS.clear();
        TEMPEST_CHARGES.clear(); TEMPESTS.clear(); TEMPEST_BLASTS.clear(); VOLT_NEXT_ZAP.clear();
        WindSpells.clearAll();
        EarthSpells.clearAll();
        ArcaneSpells.clearAll();
        WoodSpells.clearAll();
        MetalSpells.clearAll();
        LightSpells.clearAll();
        PoisonSpells.clearAll();
        SonicSpells.clearAll();
        for (BlazingSun sun : new ArrayList<>(SUNS.values())) sun.discard();
        SUNS.clear();
    }
    private NewSpellSystem() { }
}
