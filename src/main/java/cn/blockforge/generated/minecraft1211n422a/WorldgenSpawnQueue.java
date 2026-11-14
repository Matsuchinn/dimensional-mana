package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 世界生成阶段（区块特征、结构）先记下要刷的怪，等服务器刻、区块真正加载后再生成。
 *
 * <p>区块生成时直接往 ServerLevel 塞实体、触发 EntityJoinLevelEvent 或写存档数据，
 * 在整合包的异步/多线程区块生成环境下可能与实体管理系统打架，表现成创建世界时卡在某个百分比。
 * 这里只入队，真正的生成放到服务器主线程、区块加载之后。</p>
 */
public final class WorldgenSpawnQueue {
    private static final int LIMIT = 512;
    private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    private enum Kind { MANA_CAVE, WANDERING_WITCH, ADVENTURER, SWORDMASTER, ANCIENT_ALTAR, CRYSTAL_CLUSTER }

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, Kind kind, boolean largeManaCave,
                           long villageKey) { }

    private WorldgenSpawnQueue() { }

    /** 魔晶洞穴：洞挖好后补刷精英/随机等级的魔素史莱姆；大型洞穴数量翻倍。 */
    public static void queueManaCave(ServerLevel level, BlockPos center, boolean large) {
        add(level, center, Kind.MANA_CAVE, large, 0L);
    }

    /** 古代魔法祭坛：补刷守卫女巫。 */
    public static void queueWitch(ServerLevel level, BlockPos pos) {
        add(level, pos, Kind.WANDERING_WITCH, false, 0L);
    }

    /** 祭坛之间的冒险者；原来在 ChunkEvent.Load 里直接生成，现在也挪到服务器刻。 */
    public static void queueAdventurer(ServerLevel level, BlockPos pos) {
        add(level, pos, Kind.ADVENTURER, false, 0L);
    }

    /** 村庄剑士：区块加载时发现村庄还没有常驻剑士，就排队补一位。 */
    public static void queueSwordmaster(ServerLevel level, BlockPos pos, long villageKey) {
        // r286：同一座村同一时间只排一条。村庄横跨几个区块、一次连着加载几块，
        // 记账之前每个区块都会走到这里——以前就是在这里排出了第二第三份。
        for (Pending pending : PENDING) {
            if (pending.kind() == Kind.SWORDMASTER && pending.villageKey() == villageKey) return;
        }
        add(level, pos, Kind.SWORDMASTER, false, villageKey);
    }

    /** 古代魔法祭坛：生成时只排队，区块加载后写进 {@link AncientAltarData} 登记表。 */
    public static void queueAncientAltar(ServerLevel level, BlockPos center) {
        add(level, center, Kind.ANCIENT_ALTAR, false, 0L);
    }

    /**
     * r282：露天魔晶矿簇——登记到 {@link CrystalClusterData}（浓度 +4.00、粒子、回血全靠它），
     * 并补刷守卫魔素变异体。
     *
     * <p>要刷几只由 {@link CrystalClusterData#guardQuota} 按中心坐标算出来（3～7 只），
     * 同一座矿簇无论入队几次、重进几次存档，数量都是同一个，不会这次 3 只下次 7 只。</p>
     *
     * <p>r285：入队只负责"登记 + 补一次守卫"，具体刷几只、有没有刷过，全部记在
     * {@link CrystalClusterData#topUpGuards} 里。构件会被多个区块分别调用、区块也会反复
     * 加载，所以这里入队会重复很多次——记账之后重复入队就不会再多刷人。</p>
     */
    public static void queueCrystalCluster(ServerLevel level, BlockPos center) {
        add(level, center, Kind.CRYSTAL_CLUSTER, false, 0L);
    }

    private static void add(ServerLevel level, BlockPos pos, Kind kind, boolean largeManaCave, long villageKey) {
        // 队列只用于跨几十刻的过渡；真有大量区块同时生成时丢掉最旧的，避免无限增长。
        if (PENDING.size() >= LIMIT) PENDING.poll();
        PENDING.add(new Pending(level.dimension(), pos.immutable(), kind, largeManaCave, villageKey));
    }

    /** 每刻最多处理 2 个，避免出生点一次生成很多结构时在同一刻集中刷怪。 */
    public static void tick(ServerLevel level) {
        int budget = 2;
        for (Pending pending : PENDING) {
            if (budget <= 0) break;
            if (!pending.dimension().equals(level.dimension())) continue;
            // 区块还没真正加载完就先留着，等玩家靠近或出生点区块就绪后再补。
            // 这里必须问 getChunkNow：hasChunkAt 只反映「有没有票据」，区块可能还在生成中，
            // 此时对里面读方块会让游戏在主线程上同步等它生成完 —— 那就是死锁。
            if (!ChunkSafety.ready(level, pending.pos())) continue;
            // 世界里关了生物生成时，祭坛守卫和冒险者先留在队列里，等开关打开了再补，
            // 免得结构在关着的期间白刷一次、以后永远空着。魔晶洞穴照常登记（洞穴数据要留），
            // 只是里面的史莱姆由 ElementalSlimeSpawning 那边的闸门挡住。
            if (pending.kind() == Kind.WANDERING_WITCH && !SpawnControl.hostileSpawningAllowed(level)) continue;
            if (pending.kind() == Kind.ADVENTURER && !SpawnControl.mobSpawningAllowed(level)) continue;
            if (pending.kind() == Kind.SWORDMASTER && !SpawnControl.mobSpawningAllowed(level)) continue;
            // 矿簇的守卫是"必定刷 3～7 只"：怪物生成关着就先留在队列里，别白刷一次。
            if (pending.kind() == Kind.CRYSTAL_CLUSTER && !SpawnControl.hostileSpawningAllowed(level)) continue;
            if (!PENDING.remove(pending)) continue;
            switch (pending.kind()) {
                case MANA_CAVE -> {
                    ManaCaveData.get(level).add(pending.pos(), pending.largeManaCave());
                    ElementalSlimeSpawning.spawnManaCave(level, pending.pos(), level.random, pending.largeManaCave());
                }
                case ANCIENT_ALTAR -> AncientAltarData.get(level).add(pending.pos());
                case CRYSTAL_CLUSTER -> {
                    CrystalClusterData data = CrystalClusterData.get(level);
                    data.add(pending.pos());
                    // r285：刷几只交给 CrystalClusterData 记账（每座矿簇本次会话最多刷配额数），
                    // 以前这里直接把配额全丢进去，配合下面"数不到就补"会越补越多。
                    data.topUpGuards(level, pending.pos());
                }
                case WANDERING_WITCH -> spawnWitch(level, pending.pos());
                case ADVENTURER -> spawnAdventurer(level, pending.pos());
                case SWORDMASTER -> spawnSwordmaster(level, pending.pos(), pending.villageKey());
            }
            budget--;
        }
    }

    /**
     * 矿簇守卫：在中心周围 3～10 格找能站人的地面，最多再刷 {@code maxNew} 只随机魔素变异体。
     *
     * <p>r285 三处改动，都是为了不再"刷一堆"：</p>
     * <ul>
     *   <li><b>能刷几只由调用方决定</b>，{@link CrystalClusterData#topUpGuards} 按"每座矿簇本次
     *       会话最多刷配额数"记账，这里只负责把它要的那几只刷出来；</li>
     *   <li><b>落点用生物自己的碰撞箱复核</b>（{@link net.minecraft.world.level.Level#noCollision}）。
     *       以前只检查脚下中心那一列是不是空气，1.4 宽的织网者塞进矿柱缝里会窒息掉血，
     *       死了又被当成"没刷够"再刷一只——这才是变异体数量暴涨的主因；</li>
     *   <li><b>找不到落点就跳过</b>，不再退到"中心上方那一格"硬塞（那等于刷在石头里）。</li>
     * </ul>
     *
     * @return 这次实际新刷出来的只数
     */
    public static int spawnCrystalClusterGuards(ServerLevel level, BlockPos center, int maxNew) {
        if (!SpawnControl.hostileSpawningAllowed(level)) return 0;
        int want = Math.min(CrystalClusterData.MAX_GUARDS, Math.max(0, maxNew));
        if (want == 0) return 0;
        net.minecraft.util.RandomSource random = level.getRandom();
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            try {
                net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type =
                        pickGuardType(random);
                // r105 权重：五种变异体全被调到 0 时这座矿簇这一轮不补守卫。
                if (type == null) continue;
                net.minecraft.world.entity.Mob mob = type.create(level);
                if (mob == null) continue;
                // 在中心周围 3～10 格找一块脚下是实心、整个碰撞箱都放得下的地。
                // 这里不用高度图：区块刚加载时高度图还没预热，直接扫实体方块更稳。
                BlockPos spot = findGuardSpot(level, center, random, mob);
                if (spot == null) continue;
                mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                        random.nextFloat() * 360.0F, 0.0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                        net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
                // 常驻：守卫不会因为离玩家远而自然消失，矿簇周围始终有人。
                mob.setPersistenceRequired();
                level.addFreshEntityWithPassengers(mob);
                spawned++;
            } catch (RuntimeException exception) {
                // 某一种变异体生成失败不能拖垮整个刻，也不能让剩下的守卫刷不出来。
                org.slf4j.LoggerFactory.getLogger("dimensional_mana").warn(
                        "露天魔晶矿簇守卫生成失败 @ {}", center, exception);
            }
        }
        return spawned;
    }

    /**
     * 数一下矿簇中心附近（水平 16 格、垂直 12 格）现在站着几只魔素变异体。
     *
     * <p>判定范围和 {@link #findGuardSpot} 的搜索范围对齐（以前垂直只有 ±10，
     * 而落点能扫到 -12，掉到下面的守卫数不到，于是被反复补刷）。</p>
     */
    public static int countCrystalClusterGuards(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                new net.minecraft.world.phys.AABB(center).inflate(16.0, 12.0, 16.0),
                mob -> mob.isAlive() && CrystalClusterData.isManaMutant(mob)).size();
    }

    /**
     * 守卫的兵种：晶化行尸、聚魔爆裂体最常见，魔蚀骸骨次之，织网者和虚空蚀影偶尔客串。
     *
     * <p>r105：34 / 28 / 20 / 10 / 8 是基准份额，再乘以配置里各生物的权重；
     * 权重 0 的兵种抽不到，五种全关时返回 {@code null}（调用方跳过这一只）。</p>
     */
    private static net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> pickGuardType(
            net.minecraft.util.RandomSource random) {
        java.util.List<MobSpawnWeights.Candidate<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>>> pool =
                java.util.List.of(
                        MobSpawnWeights.candidate(GeneratedMod.CRYSTALLIZED_ZOMBIE.get(), "crystallized_zombie", 34),
                        MobSpawnWeights.candidate(GeneratedMod.MANA_CREEPER.get(), "mana_creeper", 28),
                        MobSpawnWeights.candidate(GeneratedMod.CORRUPTED_SKELETON.get(), "corrupted_skeleton", 20),
                        MobSpawnWeights.candidate(GeneratedMod.CORRUPTED_WEAVER.get(), "corrupted_weaver", 10),
                        MobSpawnWeights.candidate(GeneratedMod.VOID_SHADE.get(), "void_shade", 8));
        return MobSpawnWeights.pick(random, pool);
    }

    /**
     * 在矿簇中心附近 3～10 格找一块能站的地：脚下实心、身体两格空气，
     * 而且生物的**真实碰撞箱**放得下（宽体生物在矿柱缝里会窒息）。
     *
     * <p>最多往上抬 2 格再试；实在地上找不到就返回 null，让调用方跳过这只。</p>
     */
    private static BlockPos findGuardSpot(ServerLevel level, BlockPos center,
                                          net.minecraft.util.RandomSource random,
                                          net.minecraft.world.entity.Mob mob) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 3.0 + random.nextDouble() * 7.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            for (int dy = 8; dy >= -12; dy--) {
                cursor.set(x, center.getY() + dy, z);
                if (!level.isLoaded(cursor)) continue;
                if (!level.getBlockState(cursor).isSolid()) continue;
                BlockPos feet = cursor.above().immutable();
                if (!level.isEmptyBlock(feet) || !level.isEmptyBlock(feet.above())) continue;
                // 再用真实碰撞箱复核一次：1 格宽的缝里塞不下 1.2 宽的织网者。
                for (int lift = 0; lift <= 2; lift++) {
                    BlockPos candidate = feet.above(lift);
                    if (lift > 0 && !level.isEmptyBlock(candidate)) break;
                    mob.moveTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5,
                            random.nextFloat() * 360.0F, 0.0F);
                    if (level.noCollision(mob)) return candidate;
                }
            }
        }
        return null;
    }

    /** 祭坛守卫：和原来结构里的逻辑一致，只是改到区块加载后执行。 */
    private static void spawnWitch(ServerLevel level, BlockPos pos) {
        // r105 权重：女巫调到 0 时祭坛保持空置，调到 50 时只有一半的祭坛有守卫。
        if (!MobSpawnWeights.roll(level.getRandom(), 1.0F, "wandering_witch")) return;
        AABB area = new AABB(pos).inflate(1.5);
        if (!level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(WanderingWitch.class), area,
                entity -> true).isEmpty()) return;
        WanderingWitch witch = GeneratedMod.WANDERING_WITCH.get().create(level);
        if (witch == null) return;
        witch.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        witch.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        witch.setPersistenceRequired();
        level.addFreshEntityWithPassengers(witch);
    }

    /** 村庄剑士：地面检查通过就生成 1 名，并把这座村庄的刷新冷却记进存档。 */
    private static void spawnSwordmaster(ServerLevel level, BlockPos pos, long villageKey) {
        // r105 权重：剑士权重压低时，更多村庄干脆不安排常驻剑士（记账已在排队时写入，不会补第二次）。
        if (!MobSpawnWeights.roll(level.getRandom(), 1.0F, "wandering_swordmaster")) return;
        if (level.getBlockState(pos.below()).isAir() || !level.getBlockState(pos).isAir()) return;
        // r286 最后一道闸：排队到落地之间隔了几十刻，期间这座村可能已经补好了人
        // （比如玩家刚用符咒招过一名）。中心 96 格内已经有活剑士就不再塞第二份。
        if (!level.getEntitiesOfClass(WanderingSwordmaster.class,
                new AABB(pos.getX() - 96.0D, pos.getY() - 48.0D, pos.getZ() - 96.0D,
                        pos.getX() + 96.0D, pos.getY() + 48.0D, pos.getZ() + 96.0D)).isEmpty()) {
            if (villageKey != 0L) SwordmasterVillageData.get(level).markSpawned(villageKey, level.getGameTime());
            return;
        }
        WanderingSwordmaster swordmaster = GeneratedMod.WANDERING_SWORDMASTER.get().create(level);
        if (swordmaster == null) return;
        swordmaster.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(swordmaster)) {
            swordmaster.discard();
            return;
        }
        swordmaster.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        swordmaster.setHome(pos);
        swordmaster.setPersistenceRequired();
        level.addFreshEntityWithPassengers(swordmaster);
        if (villageKey != 0L) SwordmasterVillageData.get(level).markSpawned(villageKey, level.getGameTime());
    }

    /** 祭坛之间的冒险者：落地检查与原来一样，只是晚一点、在服务器刻里生成。 */
    private static void spawnAdventurer(ServerLevel level, BlockPos pos) {
        // r105 权重：冒险者同理，权重越低在祭坛之间越少碰到。
        if (!MobSpawnWeights.roll(level.getRandom(), 1.0F, "adventurer")) return;
        if (!level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos).isAir()) return;
        Adventurer adventurer = GeneratedMod.ADVENTURER.get().create(level);
        if (adventurer == null) return;
        adventurer.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(adventurer)) {
            adventurer.discard();
            return;
        }
        adventurer.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        adventurer.setPersistenceRequired();
        level.addFreshEntityWithPassengers(adventurer);
    }
}
