package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 植物在世界里的“活起来”的部分：血吻花牵引、原版花朵被魔素史莱姆浸染，以及
 * 区块加载时补种洞穴上方的蕾薇莉亚。
 *
 * <p>植物本身已经交给世界生成（{@link ManaPlantFeature}）在区块生成时种好，
 * 不再每两秒围绕玩家扫一遍——那样每次都要查高度图和浓度，玩家一多就白烧 CPU。</p>
 */
public final class ManaPlantSpawning {
    /** 血吻花半秒结算一次牵引。 */
    private static final int PULL_INTERVAL = 10;
    /** 魔素史莱姆浸染原版花的判定节奏。 */
    private static final int MUTATION_INTERVAL = 40;
    private static final int MUTATION_TICKS = 400;
    private static final float MUTATION_CHANCE = 0.55F;
    /** 区块加载时补种蕾薇莉亚的尝试次数。 */
    private static final int LEVILIA_ATTEMPTS = 3;
    /** 只有三成多的区块会考虑补种，避免每块地都长。 */
    private static final float LEVILIA_CHUNK_CHANCE = 0.35F;
    /** 补种队列每刻最多处理几个区块：一次批量加载几百块时不会在同一刻集中写方块。 */
    private static final int PLANT_CHUNKS_PER_TICK = 2;
    /** 排进队列的区块最多等多久（刻）：超过就丢掉，等下次加载再来。 */
    private static final long PLANT_QUEUE_TTL = 1200L;

    private static final String FLOWER_TIMERS = "dmFlowerTimers";
    /**
     * 等服务器刻去补种的区块。
     *
     * <p>键是区块坐标，值是「排进来的那一刻」的游戏刻。用并发集合是因为区块加载回调
     * 可能来自加载线程（整合包把区块生成放到工作线程时就是这样），而真正补种只在服务器主线程做。</p>
     */
    private static final Map<Long, Long> PENDING_PLANTS = new ConcurrentHashMap<>();
    private static final ResourceLocation ESCAPE_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "blood_kiss_escape");
    /** 生命值低于最大生命一半时，血吻花的藤蔓会死死缠住生物：移动速度 -90%。 */
    private static final float WEAK_SLOW_THRESHOLD = 0.5F;
    private static final ResourceLocation WEAK_SLOW_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "blood_kiss_weak_slow");

    private ManaPlantSpawning() {
    }

    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long time = level.getGameTime();
        // 区块加载时只记坐标、不碰世界；真正的补种在这里、按每刻固定预算慢慢做。
        plantPendingChunks(level, time);
        if (time % PULL_INTERVAL == 0) {
            for (ServerPlayer player : level.players()) tickBloodKiss(level, player);
        }
        if (time % MUTATION_INTERVAL == 0) {
            mutateFlowersNearManaSlimes(level);
        }
    }

    // ---------------------------------------------------------------- 区块加载时的补种队列

    /**
     * 区块加载时只登记坐标，绝不在这里读方块、写方块、查高度图。
     *
     * <p><b>这是本轮死锁修复的核心。</b>这个回调是在区块「补成完整状态」的那一步里发出的，
     * 而这一步本身就排在服务器主线程的任务队列上。回调里只要有一次 {@code getBlockState}
     * 落在还没加载的区块上，游戏就会让主线程<b>停下来等那块区块生成完</b>——可那块区块正好
     * 也排在同一个主线程队列里，于是谁都推进不了：世界定格、TPS 归零、没有 crash-report，
     * 退出存档还会因为区块/实体数据没写完而丢档。以前「读高度图 + 扫 7×7×7 找空地 + 写方块」
     * 一次能跨好几个相邻区块，地形模组把区块生成拖慢之后几乎必中。</p>
     *
     * <p>现在这里只往队列里放一个区块坐标，连随机数都不掷；补种挪到 {@link #tickLevel}，
     * 那时区块早就加载完了，而且每刻只处理两个区块。</p>
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ChunkPos chunk = event.getChunk().getPos();
        // 队列只用于「加载后一分钟内补一株」：堆到上限先清空，不会无限长。
        if (PENDING_PLANTS.size() > 512) PENDING_PLANTS.clear();
        PENDING_PLANTS.put(chunk.toLong(), level.getGameTime());
    }

    /** 每刻处理最多 {@link #PLANT_CHUNKS_PER_TICK} 个区块；区块或邻块还没就绪就留到下一刻。 */
    private static void plantPendingChunks(ServerLevel level, long now) {
        if (PENDING_PLANTS.isEmpty()) return;
        int budget = PLANT_CHUNKS_PER_TICK;
        Iterator<Map.Entry<Long, Long>> iterator = PENDING_PLANTS.entrySet().iterator();
        while (iterator.hasNext() && budget > 0) {
            Map.Entry<Long, Long> entry = iterator.next();
            // 等太久的直接丢掉：玩家可能早就走远了，等下次加载区块时会重新排队。
            if (now - entry.getValue() > PLANT_QUEUE_TTL) {
                iterator.remove();
                continue;
            }
            ChunkPos chunk = new ChunkPos(entry.getKey());
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) continue;
            iterator.remove();
            budget--;
            plantLevilia(level, chunk);
        }
    }

    /**
     * 在已经加载好的区块里补种一株蕾薇莉亚。
     *
     * <p>蕾薇莉亚要求“魔晶洞穴正上方、浓度 ≥15”，洞穴表是运行时才有的数据，世界生成阶段读不到，
     * 所以这一种只能等区块加载后用浓度判定补。每个区块最多一株，而且只在洞穴影响圈内，
     * 没有洞穴的区块查几次方块就直接跳过，开销可以忽略。</p>
     */
    private static void plantLevilia(ServerLevel level, ChunkPos chunk) {
        RandomSource random = level.getRandom();
        if (random.nextFloat() > LEVILIA_CHUNK_CHANCE) return;
        for (int attempt = 0; attempt < LEVILIA_ATTEMPTS; attempt++) {
            int x = chunk.getMinBlockX() + random.nextInt(16);
            int z = chunk.getMinBlockZ() + random.nextInt(16);
            if (!ChunkSafety.readyColumn(level, x, z)) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
            if (!ManaPlants.Kind.LEVILIA.heightFits(pos.getY())) continue;
            // 这一株周围 4 格（含相邻区块）必须都加载好了：写方块会触发邻块更新。
            if (!ChunkSafety.readyAround(level, pos, 4)) continue;
            if (!ManaPlants.canPlantAt(level, pos)) continue;
            // 洞穴在附近时浓度一定被抬到 15，所以这里只查洞穴表，不用再算一遍噪声浓度。
            if (!ManaCaveData.get(level).isNearCave(pos, 48)) continue;
            if (countNearby(level, pos, 3) >= 2) continue;
            ManaPlants.place(level, ManaPlants.Kind.LEVILIA, pos);
            return;
        }
    }

    /** 周围 5 格内已经有多少株本模组的植物，防止同一种铺成地毯。 */
    private static int countNearby(ServerLevel level, BlockPos center, int radius) {
        // 扫一圈之前先确认覆盖到的区块都加载好了，否则 getBlockState 会把主线程卡在区块加载上。
        if (!ChunkSafety.readyAround(level, center, radius)) return Integer.MAX_VALUE;
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.setWithOffset(center, x, y, z);
                    if (level.getBlockState(cursor).getBlock() instanceof ManaPlantBlock) count++;
                }
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- 血吻花

    /** 血吻花把附近未满血的生物往花心拽；想跑的人速度和跳跃都被压一半，受伤过半的生物再额外减速 90%。 */
    private static void tickBloodKiss(ServerLevel level, ServerPlayer player) {
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(7.0), LivingEntity::isAlive);
        for (LivingEntity entity : entities) {
            if (entity.isSpectator()) continue;
            BlockPos flower = nearestBloodKiss(level, entity.blockPosition(), 4);
            if (flower == null || entity.getHealth() >= entity.getMaxHealth()) {
                clearEscapePenalty(entity);
                clearWeakSlow(entity);
                continue;
            }
            Vec3 center = Vec3.atCenterOf(flower);
            Vec3 offset = new Vec3(center.x - entity.getX(), 0.0, center.z - entity.getZ());
            double distance = offset.length();
            // 生命值低于一半：藤蔓缠得更紧，移动速度 -90%。
            // 这时不再叠加逃跑的 -50%，否则两个乘数叠成负速度，生物会卡在原地抽搐。
            boolean weak = entity.getHealth() < entity.getMaxHealth() * WEAK_SLOW_THRESHOLD;
            // 先看“原来”的运动方向是否背着花跑：以前是先加牵引再看方向，
            // 我们自己那一拽会把向外的运动抵消掉，逃跑减速几乎永远判不出来。
            Vec3 motion = entity.getDeltaMovement();
            boolean fleeing = motion.x * offset.x + motion.z * offset.z < 0.0;
            if (weak) {
                applyWeakSlow(entity);
                clearEscapePenalty(entity);
            } else {
                clearWeakSlow(entity);
                if (fleeing) applyEscapePenalty(entity);
                else clearEscapePenalty(entity);
            }
            // 离花心两格以内不再拽，免得把人拉进方块里原地摩擦。
            if (distance <= 2.0) continue;
            double pull = fleeing ? 0.22 : 0.15;
            Vec3 dragged = entity.getDeltaMovement().add(
                    offset.x / distance * pull, 0.0, offset.z / distance * pull);
            // 别把人拽得比走路还快：横向速度封顶在 0.38。
            double horizontal = Math.sqrt(dragged.x * dragged.x + dragged.z * dragged.z);
            if (horizontal > 0.38) {
                double scale = 0.38 / horizontal;
                dragged = new Vec3(dragged.x * scale, dragged.y, dragged.z * scale);
            }
            entity.setDeltaMovement(dragged);
            // 玩家是客户端权威的，必须标记速度变化才会真被拉动。
            entity.hurtMarked = true;
        }
    }

    private static BlockPos nearestBloodKiss(ServerLevel level, BlockPos center, int radius) {
        // 先在区块段的调色板里筛一遍：附近根本没有血吻花时（绝大多数地方），一格都不用读。
        if (!SectionScan.anyNear(level, center, radius, 1,
                state -> ManaPlants.isPlant(state, ManaPlants.Kind.BLOOD_KISS))) return null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos found = null;
        double best = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.setWithOffset(center, x, y, z);
                    if (!ManaPlants.isPlant(level.getBlockState(cursor), ManaPlants.Kind.BLOOD_KISS)) continue;
                    double distance = cursor.distSqr(center);
                    if (distance < best) {
                        best = distance;
                        found = cursor.immutable();
                    }
                }
            }
        }
        return found;
    }

    private static void applyEscapePenalty(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.hasModifier(ESCAPE_MODIFIER)) return;
        speed.addTransientModifier(new AttributeModifier(ESCAPE_MODIFIER, -0.5,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void clearEscapePenalty(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(ESCAPE_MODIFIER)) speed.removeModifier(ESCAPE_MODIFIER);
    }

    /** 生命值低于一半的生物被血吻花缠住：速度只剩一成。 */
    private static void applyWeakSlow(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.hasModifier(WEAK_SLOW_MODIFIER)) return;
        speed.addTransientModifier(new AttributeModifier(WEAK_SLOW_MODIFIER, -0.9,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void clearWeakSlow(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(WEAK_SLOW_MODIFIER)) speed.removeModifier(WEAK_SLOW_MODIFIER);
    }

    /** 逃跑惩罚生效时，起跳速度只给 70.7%：跳起来的高度正好少一半。 */
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || !speed.hasModifier(ESCAPE_MODIFIER)) return;
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, motion.y * 0.7071, motion.z);
    }

    /** 血吻花附近死了生物：花丛向周围蔓延 1~3 株。 */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        PlantEffects.spreadBloodKiss(level, event.getEntity().blockPosition());
    }

    // ---------------------------------------------------------------- 蕾薇莉亚异变

    /** 魔素史莱姆附近的原版花朵，被浸染满 400 刻后有一半多一些的概率变成蕾薇莉亚。 */
    private static void mutateFlowersNearManaSlimes(ServerLevel level) {
        Set<Integer> handled = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            List<ElementalSlime> slimes = level.getEntitiesOfClass(ElementalSlime.class,
                    player.getBoundingBox().inflate(48.0),
                    slime -> slime.variant() == ElementalSlimeVariant.MANA);
            for (ElementalSlime slime : slimes) {
                if (handled.add(slime.getId())) mutateAround(level, slime);
            }
        }
    }

    private static void mutateAround(ServerLevel level, ElementalSlime slime) {
        BlockPos center = slime.blockPosition();
        // 史莱姆本身一定在已加载的区块里，但它周围 2 格可能已经跨到隔壁还没加载的区块；
        // 这时整只跳过，下一个结算周期再看，绝不为了一次浸染去同步加载区块。
        if (!ChunkSafety.readyAround(level, center, 3)) return;
        CompoundTag tag = slime.getPersistentData();
        CompoundTag timers = tag.getCompound(FLOWER_TIMERS);
        if (timers.size() > 48) timers = new CompoundTag();
        // 先问区块段的调色板：附近一朵小黄花都没有时（绝大多数位置），下面 100 格一次都不读。
        if (!SectionScan.anyNear(level, center, 2, 1, state -> state.is(BlockTags.SMALL_FLOWERS))) {
            if (!timers.isEmpty()) tag.put(FLOWER_TIMERS, new CompoundTag());
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    cursor.setWithOffset(center, x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    String key = cursor.getX() + "," + cursor.getY() + "," + cursor.getZ();
                    if (!state.is(BlockTags.SMALL_FLOWERS)) {
                        timers.remove(key);
                        continue;
                    }
                    int ticks = timers.getInt(key) + MUTATION_INTERVAL;
                    if (ticks < MUTATION_TICKS) {
                        timers.putInt(key, ticks);
                        continue;
                    }
                    if (slime.getRandom().nextFloat() >= MUTATION_CHANCE) {
                        // 这次没成功：计时归零，过一会儿再试，不会每刻都掷骰子。
                        timers.putInt(key, 0);
                        continue;
                    }
                    level.setBlock(cursor, GeneratedMod.MOD_BLOCKS.get(ManaPlants.Kind.LEVILIA.id())
                            .get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    timers.remove(key);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5,
                            12, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
        tag.put(FLOWER_TIMERS, timers);
    }
}
