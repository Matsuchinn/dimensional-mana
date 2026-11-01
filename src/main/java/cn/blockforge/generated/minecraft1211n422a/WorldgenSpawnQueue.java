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

    private enum Kind { MANA_CAVE, WANDERING_WITCH, ADVENTURER, SWORDMASTER }

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
        add(level, pos, Kind.SWORDMASTER, false, villageKey);
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
            // 区块还没真正加载就先留着，等玩家靠近或出生点区块就绪后再补。
            if (!level.hasChunkAt(pending.pos())) continue;
            // 世界里关了生物生成时，祭坛守卫和冒险者先留在队列里，等开关打开了再补，
            // 免得结构在关着的期间白刷一次、以后永远空着。魔晶洞穴照常登记（洞穴数据要留），
            // 只是里面的史莱姆由 ElementalSlimeSpawning 那边的闸门挡住。
            if (pending.kind() == Kind.WANDERING_WITCH && !SpawnControl.hostileSpawningAllowed(level)) continue;
            if (pending.kind() == Kind.ADVENTURER && !SpawnControl.mobSpawningAllowed(level)) continue;
            if (pending.kind() == Kind.SWORDMASTER && !SpawnControl.mobSpawningAllowed(level)) continue;
            if (!PENDING.remove(pending)) continue;
            switch (pending.kind()) {
                case MANA_CAVE -> {
                    ManaCaveData.get(level).add(pending.pos(), pending.largeManaCave());
                    ElementalSlimeSpawning.spawnManaCave(level, pending.pos(), level.random, pending.largeManaCave());
                }
                case WANDERING_WITCH -> spawnWitch(level, pending.pos());
                case ADVENTURER -> spawnAdventurer(level, pending.pos());
                case SWORDMASTER -> spawnSwordmaster(level, pending.pos(), pending.villageKey());
            }
            budget--;
        }
    }

    /** 祭坛守卫：和原来结构里的逻辑一致，只是改到区块加载后执行。 */
    private static void spawnWitch(ServerLevel level, BlockPos pos) {
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
        if (level.getBlockState(pos.below()).isAir() || !level.getBlockState(pos).isAir()) return;
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
