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
import java.util.List;
import java.util.Set;

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

    private static final String FLOWER_TIMERS = "dmFlowerTimers";
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
        if (time % PULL_INTERVAL == 0) {
            for (ServerPlayer player : level.players()) tickBloodKiss(level, player);
        }
        if (time % MUTATION_INTERVAL == 0) {
            mutateFlowersNearManaSlimes(level);
        }
    }

    // ---------------------------------------------------------------- 洞穴上方的蕾薇莉亚

    /**
     * 区块加载时补种一株蕾薇莉亚。
     *
     * <p>蕾薇莉亚要求“魔晶洞穴正上方、浓度 ≥15”，洞穴表是运行时才有的数据，世界生成阶段读不到，
     * 所以这一种只能等区块加载后用浓度判定补。每个区块最多一株，而且只在洞穴影响圈内，
     * 没有洞穴的区块查几次方块就直接跳过，开销可以忽略。</p>
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        RandomSource random = level.getRandom();
        if (random.nextFloat() > LEVILIA_CHUNK_CHANCE) return;
        ChunkPos chunk = event.getChunk().getPos();
        for (int attempt = 0; attempt < LEVILIA_ATTEMPTS; attempt++) {
            int x = chunk.getMinBlockX() + random.nextInt(16);
            int z = chunk.getMinBlockZ() + random.nextInt(16);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
            if (!ManaPlants.Kind.LEVILIA.heightFits(pos.getY())) continue;
            if (!level.isLoaded(pos) || !ManaPlants.canPlantAt(level, pos)) continue;
            // 洞穴在附近时浓度一定被抬到 15，所以这里只查洞穴表，不用再算一遍噪声浓度。
            if (!ManaCaveData.get(level).isNearCave(pos, 48)) continue;
            if (countNearby(level, pos, 3) >= 2) continue;
            ManaPlants.place(level, ManaPlants.Kind.LEVILIA, pos);
            return;
        }
    }

    /** 周围 5 格内已经有多少株本模组的植物，防止同一种铺成地毯。 */
    private static int countNearby(ServerLevel level, BlockPos center, int radius) {
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
        CompoundTag tag = slime.getPersistentData();
        CompoundTag timers = tag.getCompound(FLOWER_TIMERS);
        if (timers.size() > 48) timers = new CompoundTag();
        BlockPos center = slime.blockPosition();
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
