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

    private enum Kind { MANA_CAVE, WANDERING_WITCH, ADVENTURER }

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, Kind kind, boolean largeManaCave) { }

    private WorldgenSpawnQueue() { }

    /** 魔晶洞穴：洞挖好后补刷精英/随机等级的魔素史莱姆；大型洞穴数量翻倍。 */
    public static void queueManaCave(ServerLevel level, BlockPos center, boolean large) {
        add(level, center, Kind.MANA_CAVE, large);
    }

    /** 古代魔法祭坛：补刷守卫女巫。 */
    public static void queueWitch(ServerLevel level, BlockPos pos) {
        add(level, pos, Kind.WANDERING_WITCH, false);
    }

    /** 祭坛之间的冒险者；原来在 ChunkEvent.Load 里直接生成，现在也挪到服务器刻。 */
    public static void queueAdventurer(ServerLevel level, BlockPos pos) {
        add(level, pos, Kind.ADVENTURER, false);
    }

    private static void add(ServerLevel level, BlockPos pos, Kind kind, boolean largeManaCave) {
        // 队列只用于跨几十刻的过渡；真有大量区块同时生成时丢掉最旧的，避免无限增长。
        if (PENDING.size() >= LIMIT) PENDING.poll();
        PENDING.add(new Pending(level.dimension(), pos.immutable(), kind, largeManaCave));
    }

    /** 每刻最多处理 2 个，避免出生点一次生成很多结构时在同一刻集中刷怪。 */
    public static void tick(ServerLevel level) {
        int budget = 2;
        for (Pending pending : PENDING) {
            if (budget <= 0) break;
            if (!pending.dimension().equals(level.dimension())) continue;
            // 区块还没真正加载就先留着，等玩家靠近或出生点区块就绪后再补。
            if (!level.hasChunkAt(pending.pos())) continue;
            if (!PENDING.remove(pending)) continue;
            switch (pending.kind()) {
                case MANA_CAVE -> {
                    ManaCaveData.get(level).add(pending.pos(), pending.largeManaCave());
                    ElementalSlimeSpawning.spawnManaCave(level, pending.pos(), level.random, pending.largeManaCave());
                }
                case WANDERING_WITCH -> spawnWitch(level, pending.pos());
                case ADVENTURER -> spawnAdventurer(level, pending.pos());
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
