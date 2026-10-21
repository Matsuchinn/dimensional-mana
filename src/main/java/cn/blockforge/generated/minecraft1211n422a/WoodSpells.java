package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 木系追加法术：藤条穿刺与不息卍解。
 *
 * <p>藤条穿刺（中级·伤害）：读条三秒后，在施法者身后立起三根十格长的带刺藤条，持续 45 秒。
 * 每根藤条各自每 18 刻抽打一次够得着的敌人，造成 10 点木属性伤害。</p>
 *
 * <p>不息卍解（高级·增益）：在身前立起树干三格宽、四十八格高的生命之树，领域半径 32 格、
 * 最多维持 90 秒。树每 64 刻释放一次卍之波：波扫到施法者时给无敌、治疗、清除减益并回满饱和度；
 * 扫到敌人时造成 16 点木伤并种下“植入寄生”。除施法者外的生物穿过领域边界会被减速 75%。</p>
 */
public final class WoodSpells {
    // ───────────────────────── 藤条穿刺 ─────────────────────────
    private static final int VINE_COUNT = 3;
    private static final double VINE_LENGTH = 10.0;
    private static final int VINE_DURATION = 900;
    private static final int VINE_ATTACK_INTERVAL = 18;
    private static final float VINE_DAMAGE = 10.0F;
    /** 藤条够得着的横向距离（会整根甩过去抽打），再加上目标自身的半宽。 */
    private static final double VINE_REACH = 3.4;
    private static final double VINE_SPREAD = Math.toRadians(21.0);
    private static final double VINE_START_BACK = 1.2;
    private static final List<ThornVine> VINES = new ArrayList<>();

    // ───────────────────────── 不息卍解 ─────────────────────────
    private static final int BANKAI_DURATION = 1800;
    private static final double BANKAI_RADIUS = 32.0;
    private static final int WAVE_INTERVAL = 64;
    /** 6.6 格/秒 ÷ 20 刻 = 每刻 0.33 格。 */
    private static final double WAVE_SPEED = 6.6 / 20.0;
    private static final double WAVE_COST = 100.0;
    private static final float WAVE_DAMAGE = 16.0F;
    private static final int PARASITE_TICKS = 60;
    /** 植入寄生：每次元素反应额外炸出该反应伤害的 55%。 */
    private static final double PARASITE_RATIO = 0.55;
    private static final double PARASITE_EXECUTE_LOW = 0.15;
    private static final double PARASITE_EXECUTE_HIGH = 0.06;
    private static final double PARASITE_STUN_CHANCE = 0.33;
    private static final int PARASITE_STUN_TICKS = 16;
    private static final String PARASITE_OWNER = "DimensionalManaParasiteOwner";
    /** 卍之波提供的 8 刻无敌；写进持久数据，由 {@link NewSpellSystem#incoming} 读取。 */
    public static final String INVULNERABLE_UNTIL = "DimensionalManaBankaiInvuln";

    private static final double TREE_DISTANCE = 6.0;
    /** 树干半径 1 → 3 格宽。 */
    private static final int TRUNK_RADIUS = 1;
    private static final int TRUNK_HEIGHT = 48;
    private static final int BUILD_PER_TICK = 160;
    private static final int RESTORE_PER_TICK = 480;
    private static final double BOUNDARY_SLOW = 0.75;
    private static final int BOUNDARY_SLOW_TICKS = 100;
    private static final ResourceLocation SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "bankai_boundary_slow");

    private static final Map<UUID, Bankai> BANKAIS = new HashMap<>();
    /** 已经结束、正在分批把方块换回去的树；不再参与领域结算，也不保护方块。 */
    private static final List<Bankai> RESTORING_TREES = new ArrayList<>();
    private static final Map<UUID, SlowState> BOUNDARY_SLOWS = new HashMap<>();

    private record SlowState(ServerLevel level, long until) { }

    private record Placement(BlockPos pos, BlockState state) { }

    /** 一根已经长好的带刺藤条：位置固定，每 18 刻自己抽打一次。 */
    private static final class ThornVine {
        final ServerLevel level;
        final UUID owner;
        final Vec3 start;
        final Vec3 end;
        final long born;
        final long until;
        long nextAttack;

        ThornVine(ServerLevel level, UUID owner, Vec3 start, Vec3 end, long born) {
            this.level = level;
            this.owner = owner;
            this.start = start;
            this.end = end;
            this.born = born;
            this.until = born + VINE_DURATION;
            this.nextAttack = born + VINE_ATTACK_INTERVAL;
        }
    }

    /** 卍之波的扩散状态：radius 每刻向前推进，hit 记录这一波已经结算过的目标。 */
    private static final class Wave {
        double radius;
        final Set<UUID> hit = new HashSet<>();
    }

    /** 一棵已经立起来的生命之树，以及它占用的方块与原始的方块状态。 */
    private static final class Bankai {
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final long born;
        final long until;
        long nextWave;
        long nextField;
        final List<Wave> waves = new ArrayList<>();
        final Map<UUID, Boolean> inside = new HashMap<>();
        final ArrayDeque<Placement> build = new ArrayDeque<>();
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        List<BlockPos> restore;
        int restoreCursor;
        boolean restoring;
        boolean ended;

        Bankai(ServerLevel level, UUID owner, Vec3 center, long born) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.born = born;
            this.until = born + BANKAI_DURATION;
            this.nextWave = born + WAVE_INTERVAL;
            this.nextField = born;
        }
    }

    // ───────────────────────── 藤条穿刺 ─────────────────────────

    /** 读条结束：在身后立起三根扇开的带刺藤条。 */
    public static void beginThornImpale(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 back = player.getLookAngle().multiply(1, 0, 1);
        if (back.lengthSqr() < 1.0E-4) back = new Vec3(0, 0, 1);
        back = back.normalize().scale(-1);
        Vec3 base = player.position().add(back.scale(VINE_START_BACK));
        Vec3 ground = NewSpellSystem.ground(level, base);
        double y = ground.y + 0.05;
        double reference = Math.atan2(back.z, back.x);
        for (int i = 0; i < VINE_COUNT; i++) {
            double angle = reference + (i - (VINE_COUNT - 1) / 2.0) * VINE_SPREAD;
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle)).normalize();
            Vec3 start = new Vec3(base.x, y, base.z).add(dir.scale(0.4));
            Vec3 end = start.add(dir.scale(VINE_LENGTH));
            Vec3 endGround = NewSpellSystem.ground(level, end);
            // 地面起伏超过一格半时让藤条保持同一高度，避免整根藤条斜插进山体。
            if (Math.abs(endGround.y - ground.y) <= 1.5) end = new Vec3(end.x, endGround.y + 0.05, end.z);
            VINES.add(new ThornVine(level, player.getUUID(), start, end, now));
            SpellVisualPayload.send(player, SpellDefinition.THORN_IMPALE, SpellVisualPayload.VINE,
                    player.getId(), start, end, VINE_DURATION);
            for (int step = 0; step <= 10; step++) {
                Vec3 p = start.lerp(end, step / 10.0);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 0.25, p.z, 4, 0.25, 0.25, 0.25, 0.02);
            }
        }
        player.displayClientMessage(Component.translatable("message.dimensional_mana.thorn_impale_cast",
                VINE_COUNT, (int)VINE_LENGTH, VINE_DURATION / 20), true);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.ROOTS_PLACE, SoundSource.PLAYERS, 1.25F, 0.75F);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.VINE_PLACE, SoundSource.PLAYERS, 1.0F, 0.62F);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.65F, 1.35F);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, base.x, y + 0.6, base.z, 24, 1.2, 0.5, 1.2, 0.02);
        SpellSyncPayload.send(player);
    }

    /** 藤条每刻维护：到期收起，每 18 刻各自抽打一次够得着的敌人。 */
    private static void tickVines(ServerLevel level, long now) {
        Iterator<ThornVine> iterator = VINES.iterator();
        while (iterator.hasNext()) {
            ThornVine vine = iterator.next();
            if (vine.level != level) continue;
            if (now > vine.until) {
                iterator.remove();
                Entity ownerEntity = level.getEntity(vine.owner);
                if (ownerEntity instanceof ServerPlayer owner)
                    SpellVisualPayload.send(owner, SpellDefinition.THORN_IMPALE, SpellVisualPayload.VINE_END, -1,
                            vine.start, vine.start, 1);
                continue;
            }
            Entity ownerEntity = level.getEntity(vine.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive() || owner.isSpectator()) {
                iterator.remove();
                if (ownerEntity instanceof ServerPlayer owner)
                    SpellVisualPayload.send(owner, SpellDefinition.THORN_IMPALE, SpellVisualPayload.VINE_END, -1,
                            vine.start, vine.start, 1);
                continue;
            }
            if (now < vine.nextAttack) continue;
            vine.nextAttack = now + VINE_ATTACK_INTERVAL;
            LivingEntity victim = findVineTarget(owner, vine);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, vine.start.x, vine.start.y + 0.4, vine.start.z,
                    3, 0.6, 0.3, 0.6, 0.01);
            if (victim == null) continue;
            NewSpellSystem.damage(owner, victim, VINE_DAMAGE, ElementType.NATURE);
            Vec3 at = victim.getBoundingBox().getCenter();
            // 抽打方向：从藤条上离目标最近的一点甩过去，客户端据此画出会追着目标走的鞭击。
            Vec3 from = closestOnSegment(at, vine.start, vine.end).add(0, 0.35, 0);
            SpellVisualPayload.send(owner, SpellDefinition.THORN_IMPALE, SpellVisualPayload.VINE_LASH,
                    victim.getId(), from, at, 12);
            NewSpellSystem.knock(from, victim, 0.35);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.32, 0.5, 0.32, 0.2);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 6, 0.3, 0.4, 0.3, 0.05);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                    0.75F, 1.45F);
        }
        if (now % 10 == 0) {
            for (ThornVine vine : VINES) {
                if (vine.level != level) continue;
                Vec3 p = vine.start.lerp(vine.end, 0.5);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.x, p.y + 0.5, p.z,
                        2, VINE_LENGTH * 0.4, 0.35, VINE_LENGTH * 0.4, 0.005);
            }
        }
    }

    /** 藤条挑最近的一个敌人：距离按“到藤条线段”的最近点计算。 */
    private static LivingEntity findVineTarget(ServerPlayer owner, ThornVine vine) {
        AABB box = new AABB(vine.start, vine.end).inflate(VINE_REACH + 1.5);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : vine.level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> NewSpellSystem.enemy(owner, entity))) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 nearest = closestOnSegment(center, vine.start, vine.end);
            double distance = nearest.distanceTo(center);
            if (distance > VINE_REACH + candidate.getBbWidth() * 0.5) continue;
            if (!NewSpellSystem.clear(owner, nearest.add(0, 0.5, 0), center)) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    // ───────────────────────── 不息卍解 ─────────────────────────

    /** 读条结束：在身前立起生命之树，领域开始计时。 */
    public static void beginBankai(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        // 同一个施法者只保留一棵树：旧领域直接收起（正常情况下施法键已被拦截）。
        Bankai old = BANKAIS.get(player.getUUID());
        if (old != null) endBankai(player, old, "message.dimensional_mana.endless_bankai_end");
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 at = player.position().add(look.scale(TREE_DISTANCE));
        Vec3 ground = NewSpellSystem.ground(level, at);
        int baseX = Mth.floor(at.x);
        int baseY = Mth.floor(ground.y + 0.05);
        int baseZ = Mth.floor(at.z);
        Vec3 center = new Vec3(baseX, baseY, baseZ);
        Bankai field = new Bankai(level, player.getUUID(), center, now);
        planTree(field, baseX, baseY, baseZ);
        BANKAIS.put(player.getUUID(), field);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.endless_bankai_start",
                BANKAI_DURATION / 20, (int)BANKAI_RADIUS, WAVE_INTERVAL / 20.0), true);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ROOTS_PLACE, SoundSource.PLAYERS, 1.6F, 0.45F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.15F, 0.40F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.60F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.52F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x + 0.5, center.y + 1.0, center.z + 0.5,
                60, 2.0, 1.0, 2.0, 0.06);
        sendField(player, field);
        SpellSyncPayload.send(player);
    }

    /** 规划整棵树的方块：3×3×48 的树干、四层树枝与三团圆冠。整批生成、分刻放置。 */
    private static void planTree(Bankai field, int baseX, int baseY, int baseZ) {
        for (int y = 0; y < TRUNK_HEIGHT; y++) {
            for (int dx = -TRUNK_RADIUS; dx <= TRUNK_RADIUS; dx++) {
                for (int dz = -TRUNK_RADIUS; dz <= TRUNK_RADIUS; dz++) {
                    place(field, new BlockPos(baseX + dx, baseY + y, baseZ + dz), log(), true);
                }
            }
        }
        int[] heights = {26, 32, 38, 43};
        for (int index = 0; index < heights.length; index++) {
            int height = heights[index];
            int arms = index == 3 ? 3 : 4;
            double offset = index * 0.55;
            for (int side = 0; side < arms; side++) {
                double angle = side * Math.PI * 2.0 / arms + offset;
                int length = 4 + index;
                int lastX = baseX;
                int lastZ = baseZ;
                for (int step = 1; step <= length; step++) {
                    int x = baseX + (int)Math.round(Math.cos(angle) * step);
                    int z = baseZ + (int)Math.round(Math.sin(angle) * step);
                    int y = baseY + height + step / 3;
                    place(field, new BlockPos(x, y, z), log(), false);
                    lastX = x;
                    lastZ = z;
                }
                // 枝头挂一小团叶子，树冠不会像一个光滑的球。
                int tipY = baseY + height + length / 3;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            double distance = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                            if (distance > 2.6 || distance < 1.0) continue;
                            place(field, new BlockPos(lastX + dx, tipY + dy, lastZ + dz), leaves(), false);
                        }
                    }
                }
            }
        }
        // 三团同心圆冠：主冠最大，上面两层收窄，下面一层压扁包住树枝。
        double[][] crowns = {{38.0, 10.5}, {44.5, 7.0}, {30.5, 6.5}};
        for (double[] crown : crowns) {
            double centerY = baseY + crown[0];
            double radius = crown[1];
            double inner = radius - 1.5;
            int extent = (int)Math.ceil(radius);
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    for (int dy = -extent; dy <= extent; dy++) {
                        double distance = Math.sqrt(dx * dx + dz * dz + dy * dy);
                        if (distance > radius || distance < inner) continue;
                        place(field, new BlockPos(baseX + dx, Mth.floor(centerY) + dy, baseZ + dz), leaves(), false);
                    }
                }
            }
        }
    }

    private static BlockState log() {
        return Blocks.OAK_LOG.defaultBlockState();
    }

    private static BlockState leaves() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    /**
     * 登记一个待放置的方块。force 为真时（树干）会顶掉普通地形，但绝不覆盖基岩级方块与方块实体；
     * force 为假时（树枝、树叶）只占用空气或可替换方块，避免把玩家的房子整个吃掉。
     */
    private static void place(Bankai field, BlockPos pos, BlockState state, boolean force) {
        BlockPos fixed = pos.immutable();
        if (field.original.containsKey(fixed)) return;
        if (!field.level.hasChunkAt(fixed) || !field.level.getWorldBorder().isWithinBounds(fixed)) return;
        BlockState current = field.level.getBlockState(fixed);
        if (force) {
            if (current.getDestroySpeed(field.level, fixed) < 0.0F || current.hasBlockEntity()) return;
        } else if (!current.isAir() && !current.canBeReplaced()) {
            return;
        }
        field.original.put(fixed, current);
        field.build.add(new Placement(fixed, state));
    }

    /** 分刻放置树体，避免一次性几千格方块更新造成卡顿。 */
    private static void buildStep(Bankai field) {
        int budget = BUILD_PER_TICK;
        while (budget-- > 0 && !field.build.isEmpty()) {
            Placement placement = field.build.poll();
            if (!field.level.hasChunkAt(placement.pos())) continue;
            field.level.setBlockAndUpdate(placement.pos(), placement.state());
        }
        if (field.build.isEmpty() && !field.ended) {
            // 树长成的一瞬间补一圈绿光，让“树凭空长出来”这件事看得见。
            field.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, field.center.x + 0.5, field.center.y + 24,
                    field.center.z + 0.5, 120, 9.0, 12.0, 9.0, 0.05);
        }
    }

    /** 领域每刻维护：生成树体、释放卍之波、边界减速与可视化刷新。 */
    private static void tickBankai(ServerLevel level, long now) {
        for (Bankai field : new ArrayList<>(BANKAIS.values())) {
            if (field.level != level) continue;
            Entity ownerEntity = level.getEntity(field.owner);
            if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive() || owner.isSpectator()
                    || owner.serverLevel() != level) {
                endBankai(ownerEntity instanceof ServerPlayer player ? player : null, field,
                        "message.dimensional_mana.endless_bankai_end");
                continue;
            }
            if (!field.build.isEmpty()) buildStep(field);
            if (now >= field.until) {
                endBankai(owner, field, "message.dimensional_mana.endless_bankai_end");
                continue;
            }
            if (now >= field.nextWave) {
                field.nextWave += WAVE_INTERVAL;
                releaseWave(owner, field, now);
            }
            tickWaves(owner, field, now);
            tickBoundary(owner, field, now);
            if (now >= field.nextField) {
                field.nextField = now + 40;
                sendField(owner, field);
            }
            if (now % 20 == 0) ambient(level, field);
            if (now % 40 == 0) SpellSyncPayload.send(owner);
        }
    }

    /** 分批还原已经结束的树，全部换回去后就把这条记录丢掉。 */
    private static void tickRestoring(ServerLevel level, long now) {
        Iterator<Bankai> iterator = RESTORING_TREES.iterator();
        while (iterator.hasNext()) {
            Bankai field = iterator.next();
            if (field.level != level) continue;
            if (restoreStep(field)) iterator.remove();
        }
    }

    /** 生命之树每 64 刻释放一次卍之波；魔素不够就跳过这一波。 */
    private static void releaseWave(ServerPlayer owner, Bankai field, long now) {
        double cost = SpellAttributes.reducedManaCost(owner, WAVE_COST);
        if (ManaCheats.manaCostEnabled(owner) && !ManaCheats.hasMana(owner, cost)) {
            owner.displayClientMessage(Component.translatable("message.dimensional_mana.endless_bankai_no_mana",
                    (int)Math.ceil(cost)), true);
            return;
        }
        if (!ManaData.spendMana(owner, WAVE_COST)) return;
        SpellCasting.syncMana(owner);
        Wave wave = new Wave();
        field.waves.add(wave);
        Vec3 center = field.center.add(0, 0.4, 0);
        SpellVisualPayload.send(owner, SpellDefinition.ENDLESS_BANKAI, SpellVisualPayload.BANKAI_WAVE, -1,
                center, center.add(0, BANKAI_RADIUS, 0), (int)Math.ceil(BANKAI_RADIUS / WAVE_SPEED) + 2);
        owner.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.55F, 0.62F);
    }

    /** 波的扩散与结算：每波对每个目标只生效一次，扫到施法者给增益，扫到敌人给伤害与寄生。 */
    private static void tickWaves(ServerPlayer owner, Bankai field, long now) {
        if (field.waves.isEmpty()) return;
        ServerLevel level = field.level;
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, domainBox(field),
                entity -> entity.isAlive() && !entity.isSpectator());
        Iterator<Wave> iterator = field.waves.iterator();
        while (iterator.hasNext()) {
            Wave wave = iterator.next();
            wave.radius = Math.min(BANKAI_RADIUS, wave.radius + WAVE_SPEED);
            if (!wave.hit.contains(owner.getUUID())
                    && horizontalDistance(field, owner) <= wave.radius + 0.8 && insideSpan(field, owner)) {
                wave.hit.add(owner.getUUID());
                bless(owner, field, now);
            }
            for (LivingEntity victim : nearby) {
                if (victim == owner || wave.hit.contains(victim.getUUID())) continue;
                if (!NewSpellSystem.enemy(owner, victim)) continue;
                if (!insideSpan(field, victim)) continue;
                if (horizontalDistance(field, victim) > wave.radius) continue;
                wave.hit.add(victim.getUUID());
                strike(owner, victim);
            }
            if (wave.radius >= BANKAI_RADIUS) iterator.remove();
        }
    }

    /** 卍之波扫到施法者：8 刻无敌、回复 4% 最大生命、清除全部减益、饱和度回满。 */
    private static void bless(ServerPlayer player, Bankai field, long now) {
        ServerLevel level = field.level;
        player.getPersistentData().putLong(INVULNERABLE_UNTIL, now + 8);
        player.heal(player.getMaxHealth() * 0.04F);
        List<MobEffectInstance> harmful = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) harmful.add(instance);
        }
        for (MobEffectInstance instance : harmful) player.removeEffect(instance.getEffect());
        player.getFoodData().setSaturation(20.0F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(),
                26, 0.6, 0.9, 0.6, 0.35);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.9, player.getZ(),
                12, 0.45, 0.7, 0.45, 0.04);
        player.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.85F, 1.45F);
    }

    /** 卍之波扫到敌人：16 点木属性伤害，并种下 3 秒“植入寄生”。 */
    private static void strike(ServerPlayer owner, LivingEntity victim) {
        ServerLevel level = (ServerLevel)victim.level();
        NewSpellSystem.damage(owner, victim, WAVE_DAMAGE, ElementType.NATURE);
        if (!victim.isAlive()) return;
        NewSpellSystem.effect(victim, NewSpellContent.PARASITE, PARASITE_TICKS);
        victim.getPersistentData().putUUID(PARASITE_OWNER, owner.getUUID());
        Vec3 at = victim.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 10, 0.4, 0.5, 0.4, 0.02);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.3, 0.4, 0.3, 0.15);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.VINE_PLACE, SoundSource.PLAYERS, 0.55F, 1.55F);
    }

    /** 边界减速：除施法者外，任何生物穿过领域边界都会被减速 75%（持续 5 秒）。 */
    private static void tickBoundary(ServerPlayer owner, Bankai field, long now) {
        ServerLevel level = field.level;
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, domainBox(field).inflate(4),
                entity -> entity.isAlive() && !entity.isSpectator());
        Set<UUID> seen = new HashSet<>();
        for (LivingEntity entity : nearby) {
            UUID id = entity.getUUID();
            if (id.equals(field.owner)) continue;
            boolean inside = horizontalDistance(field, entity) <= BANKAI_RADIUS && insideSpan(field, entity);
            seen.add(id);
            Boolean previous = field.inside.put(id, inside);
            if (previous == null || previous == inside) continue;
            applyBoundarySlow(level, entity, now);
        }
        field.inside.keySet().retainAll(seen);
    }

    private static void applyBoundarySlow(ServerLevel level, LivingEntity entity, long now) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, -BOUNDARY_SLOW,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            BOUNDARY_SLOWS.put(entity.getUUID(), new SlowState(level, now + BOUNDARY_SLOW_TICKS));
        }
        Vec3 at = entity.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.VINE_PLACE, SoundSource.PLAYERS, 0.4F, 0.85F);
    }

    /** 每小时（每 20 刻）在树冠撒一层绿光，让 48 格高的树在远处也看得见。 */
    private static void ambient(ServerLevel level, Bankai field) {
        double phase = (level.getGameTime() % 200) / 200.0 * Math.PI * 2.0;
        double x = field.center.x + 0.5 + Math.cos(phase) * 7.0;
        double z = field.center.z + 0.5 + Math.sin(phase) * 7.0;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, field.center.y + 36.0, z, 4, 1.2, 2.0, 1.2, 0.02);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, field.center.x + 0.5, field.center.y + 12.0,
                field.center.z + 0.5, 3, 1.6, 6.0, 1.6, 0.01);
    }

    /** 生命之树与领域的可视化：半径编码在 end.y 与 start.y 的差值里。 */
    private static void sendField(ServerPlayer owner, Bankai field) {
        Vec3 center = field.center.add(0, 0.4, 0);
        SpellVisualPayload.send(owner, SpellDefinition.ENDLESS_BANKAI, SpellVisualPayload.BANKAI_FIELD, -1,
                center, center.add(0, BANKAI_RADIUS, 0), 60);
    }

    /** 生命之树消失：把被替换的方块分批还原，并开始计算 42 秒冷却。 */
    private static void endBankai(ServerPlayer player, Bankai field, String messageKey) {
        if (field.ended) return;
        field.ended = true;
        field.build.clear();
        field.waves.clear();
        field.inside.clear();
        field.restore = new ArrayList<>(field.original.keySet());
        field.restoreCursor = 0;
        field.restoring = true;
        BANKAIS.remove(field.owner);
        RESTORING_TREES.add(field);
        if (player != null && messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey), true);
            SpellVisualPayload.send(player, SpellDefinition.ENDLESS_BANKAI, SpellVisualPayload.BANKAI_END, -1,
                    field.center, field.center, 1);
            SpellCasting.startCooldown(player, SpellDefinition.ENDLESS_BANKAI);
            SpellSyncPayload.send(player);
        } else if (field.level.getEntity(field.owner) instanceof ServerPlayer owner) {
            SpellVisualPayload.send(owner, SpellDefinition.ENDLESS_BANKAI, SpellVisualPayload.BANKAI_END, -1,
                    field.center, field.center, 1);
        }
    }

    /** 分批还原树体占用过的方块；全部还完就返回 true。 */
    private static boolean restoreStep(Bankai field) {
        int budget = RESTORE_PER_TICK;
        while (budget-- > 0 && field.restoreCursor < field.restore.size()) {
            BlockPos pos = field.restore.get(field.restoreCursor++);
            BlockState state = field.original.get(pos);
            if (state == null || !field.level.hasChunkAt(pos)) continue;
            field.level.setBlockAndUpdate(pos, state);
        }
        if (field.restoreCursor < field.restore.size()) return false;
        field.level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, field.center.x + 0.5, field.center.y + 20.0,
                field.center.z + 0.5, 90, 8.0, 16.0, 8.0, 0.03);
        return true;
    }

    /** 领域是否还在维持（还原阶段不算），用来拦住在冷却开始前重复施法。 */
    public static boolean hasBankai(ServerPlayer player) {
        return BANKAIS.containsKey(player.getUUID());
    }

    public static int bankaiTicks(ServerPlayer player) {
        Bankai field = BANKAIS.get(player.getUUID());
        if (field == null) return 0;
        return (int)Math.max(0L, field.until - player.level().getGameTime());
    }

    // ───────────────────────── 植入寄生 ─────────────────────────

    /**
     * 植入寄生被元素反应激活：额外触发一次“强制激发”，造成本次元素反应 ×0.55 的木属性伤害。
     * 这段伤害不附着木元素、也不再触发元素反应，因此不会自己连锁。
     */
    public static void parasiteReaction(LivingEntity reactionCaster, LivingEntity target, float reactionDamage) {
        if (target == null || !target.isAlive() || reactionDamage <= 0.0F) return;
        if (!target.hasEffect(NewSpellContent.PARASITE)) return;
        if (!(target.level() instanceof ServerLevel level)) return;
        LivingEntity owner = parasiteOwner(target, reactionCaster);
        double extra = SpellAttributes.applySpellResistance(target, reactionDamage * PARASITE_RATIO);
        if (extra <= 0.0) return;
        if (!SpellDamage.hitResolved(level, owner, target, (float)extra)) return;
        Vec3 at = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 12, 0.35, 0.5, 0.35, 0.04);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 8, 0.35, 0.45, 0.35, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.VINE_PLACE, SoundSource.PLAYERS, 0.5F, 1.75F);
    }

    /** 寄生的施加者；施法者已不在时退回本次反应的施法者。 */
    private static LivingEntity parasiteOwner(LivingEntity target, LivingEntity fallback) {
        if (!(target.level() instanceof ServerLevel level)) return fallback;
        UUID id = target.getPersistentData().hasUUID(PARASITE_OWNER)
                ? target.getPersistentData().getUUID(PARASITE_OWNER) : null;
        if (id == null) return fallback;
        Entity entity = level.getEntity(id);
        return entity instanceof LivingEntity living && living.isAlive() ? living : fallback;
    }

    /** 木属性伤害命中寄生目标时，有 33% 概率让它呆滞 16 刻。 */
    public static void stingOnWoodHit(LivingEntity target) {
        if (target == null || target.level().isClientSide || !target.isAlive()) return;
        if (!target.hasEffect(NewSpellContent.PARASITE)) return;
        if (target.getRandom().nextFloat() >= PARASITE_STUN_CHANCE) return;
        NewSpellSystem.effect(target, NewSpellContent.STUN, PARASITE_STUN_TICKS);
        if (target.level() instanceof ServerLevel level) {
            Vec3 at = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.4, at.z, 6, 0.3, 0.3, 0.3, 0.12);
        }
    }

    /** 每刻检查寄生目标：血量掉到斩杀线以下就直接斩杀。 */
    public static void tickParasite(LivingEntity entity) {
        // 只对挂着寄生的生物做事：绝大多数生物每刻都在这里直接走掉。
        if (!entity.hasEffect(NewSpellContent.PARASITE)) return;
        if (entity.level().isClientSide || !entity.isAlive()) return;
        double maxHealth = entity.getMaxHealth();
        double threshold = maxHealth <= 200.0 ? PARASITE_EXECUTE_LOW : PARASITE_EXECUTE_HIGH;
        if (entity.getHealth() >= maxHealth * threshold) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        Vec3 at = entity.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 24, 0.5, 0.6, 0.5, 0.06);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 0.9F, 1.6F);
        entity.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
    }

    // ───────────────────────── 事件入口 ─────────────────────────

    /** 领域方块不可被破坏：生命之树存在期间直接拦掉破坏事件。 */
    public static boolean protects(BlockPos pos) {
        if (BANKAIS.isEmpty()) return false;
        for (Bankai field : BANKAIS.values()) {
            if (field.original.containsKey(pos)) return true;
        }
        return false;
    }

    /** 爆炸同样不能炸掉生命之树。 */
    public static void onExplode(ExplosionEvent.Detonate event) {
        if (BANKAIS.isEmpty()) return;
        event.getAffectedBlocks().removeIf(block -> protects(block));
    }

    public static void tickLevel(ServerLevel level, long now) {
        tickVines(level, now);
        tickBankai(level, now);
        tickRestoring(level, now);
        cleanupSlows(level, now);
    }

    private static void cleanupSlows(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, SlowState>> iterator = BOUNDARY_SLOWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SlowState> entry = iterator.next();
            SlowState state = entry.getValue();
            if (state.level() != level) continue;
            if (now < state.until()) {
                if (level.getEntity(entry.getKey()) instanceof LivingEntity living
                        && living.getAttribute(Attributes.MOVEMENT_SPEED) != null) continue;
            }
            iterator.remove();
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living) {
                AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(SLOW_ID);
            }
        }
    }

    /** 下线、死亡或换维度：收起自己的藤条与生命之树，并把树体还原。 */
    public static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        VINES.removeIf(vine -> vine.owner.equals(id) && vine.level == player.serverLevel());
        Bankai field = BANKAIS.get(id);
        if (field != null) endBankai(player, field, "message.dimensional_mana.endless_bankai_end");
    }

    public static void clearAll() {
        VINES.clear();
        for (Bankai field : new ArrayList<>(BANKAIS.values())) endBankai(null, field, null);
        // 服务器关闭时直接把所有树体同步还原，避免下次开服留下无主的死树。
        for (Bankai field : new ArrayList<>(RESTORING_TREES)) {
            if (field.restore == null) continue;
            for (BlockPos pos : field.restore) {
                BlockState state = field.original.get(pos);
                if (state != null && field.level.hasChunkAt(pos)) field.level.setBlockAndUpdate(pos, state);
            }
        }
        BANKAIS.clear();
        RESTORING_TREES.clear();
        BOUNDARY_SLOWS.clear();
    }

    private static AABB domainBox(Bankai field) {
        Vec3 center = field.center;
        return new AABB(center.x - BANKAI_RADIUS, center.y - 4.0, center.z - BANKAI_RADIUS,
                center.x + BANKAI_RADIUS + 1.0, center.y + 54.0, center.z + BANKAI_RADIUS + 1.0);
    }

    /** 水平距离按树干中心（方块中心）计算。 */
    private static double horizontalDistance(Bankai field, LivingEntity entity) {
        double dx = entity.getX() - (field.center.x + 0.5);
        double dz = entity.getZ() - (field.center.z + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 领域的竖直范围：地面往下 4 格到树顶上方 6 格。 */
    private static boolean insideSpan(Bankai field, LivingEntity entity) {
        double y = entity.getBoundingBox().getCenter().y;
        return y >= field.center.y - 4.0 && y <= field.center.y + 54.0;
    }

    private static Vec3 closestOnSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double lengthSqr = delta.lengthSqr();
        if (lengthSqr < 1.0E-6) return start;
        double t = Math.clamp(point.subtract(start).dot(delta) / lengthSqr, 0.0, 1.0);
        return start.add(delta.scale(t));
    }

    private WoodSpells() { }
}
