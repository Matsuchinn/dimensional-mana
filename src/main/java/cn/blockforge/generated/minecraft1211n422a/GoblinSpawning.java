package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 保留野外巡逻；营地建筑改走正式结构生成，仅在主线程补刷一次居民。 */
public final class GoblinSpawning {
    private record PendingCamp(ServerLevel level, BlockPos center, GoblinCampPiece camp) {}
    private static final Set<PendingCamp> PENDING_CAMPS = new LinkedHashSet<>();
    /**
     * 野外巡逻的生成概率：白天 12%、夜晚 18%（旧版是 25% / 34%）。
     * 旧数值大约每分钟就刷一队 2～5 只，新手前期还在砍树就会被成队哥布林围住；
     * 现在大约两分钟一队，且队伍缩到 1～3 只。
     */
    private static final float PATROL_CHANCE_DAY = 0.12F;
    private static final float PATROL_CHANCE_NIGHT = 0.18F;
    /** 玩家 64 格内最多同时存在这么多只野生哥布林，防止几轮巡逻叠成一大群。 */
    private static final int PATROL_LOCAL_CAP = 6;
    /**
     * r105：巡逻队成员的比例（2 : 1 : 1，和原来的「四选一」一致），
     * 再乘以配置里的权重；某个变体调到 0 就永远抽不到，三个都调到 0 这一队就不刷。
     */
    private static final List<MobSpawnWeights.Candidate<GoblinVariant>> PATROL_PICKS = List.of(
            MobSpawnWeights.candidate(GoblinVariant.WARRIOR, "goblin_warrior", 2),
            MobSpawnWeights.candidate(GoblinVariant.ARCHER, "goblin_archer", 1),
            MobSpawnWeights.candidate(GoblinVariant.SHAMAN, "goblin_shaman", 1));
    /** 营地居民的基准比例：幼崽 4、战士 3、弓手 2、萨满 1，同样再乘配置权重。 */
    private static final List<MobSpawnWeights.Candidate<GoblinVariant>> CAMP_PICKS = List.of(
            MobSpawnWeights.candidate(GoblinVariant.YOUNG, "goblin_young", 4),
            MobSpawnWeights.candidate(GoblinVariant.WARRIOR, "goblin_warrior", 3),
            MobSpawnWeights.candidate(GoblinVariant.ARCHER, "goblin_archer", 2),
            MobSpawnWeights.candidate(GoblinVariant.SHAMAN, "goblin_shaman", 1));

    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        tickPendingCamps(level);
        // 世界关了「生成生物」（或和平难度）时不再放巡逻队，野外就真的没有哥布林。
        if (!SpawnControl.hostileSpawningAllowed(level)) return;
        if (level.dimension()!=Level.OVERWORLD || level.getGameTime()%200!=0) return;
        for (ServerPlayer player:level.players()) {
            if(player.isSpectator()) continue;
            // 带着【敬而远之】的玩家 64 格内根本不刷巡逻队，连抽签都省了。
            if(GoblinDread.playerNearbyHasDread(player)) continue;
            RandomSource random=player.getRandom();
            // r106：巡逻概率再乘「开局爬坡 × 哥布林总倍率」，新存档头几天巡逻队明显变少。
            // r107：按玩家所在位置的魔素浓度判断——高浓度区域不吃开局爬坡。
            if(!SpawnPacing.roll(level,random,
                    level.isDay()?PATROL_CHANCE_DAY:PATROL_CHANCE_NIGHT,
                    SpawnPacing.goblinRate(),player.blockPosition())) continue;
            if(level.getEntitiesOfClass(Goblin.class,player.getBoundingBox().inflate(64),Goblin::isAlive).size()>=PATROL_LOCAL_CAP) continue;
            spawnPatrol(level,player,random);
        }
    }

    private static void spawnPatrol(ServerLevel level,ServerPlayer player,RandomSource random) {
        for(int attempt=0;attempt<8;attempt++) {
            // r106：巡逻队落点改成可配的环带（默认 40～128 格），不再出现在玩家院子边上。
            double angle=random.nextDouble()*Math.PI*2;
            double distance=SpawnPacing.pickDistance(random,
                    SpawnPacing.goblinMinDistance(),SpawnPacing.goblinMaxDistance());
            BlockPos probe=BlockPos.containing(player.getX()+Math.cos(angle)*distance,0,
                    player.getZ()+Math.sin(angle)*distance);
            // r107：落点区块没加载就换一个方向。以前直接问高度图，落在没生成的区块上时
            // 游戏会在服务器主线程里当场生成那块地形——巡逻队每 10 秒就可能替玩家预生成一次地形。
            if(!SectionScan.loadedColumn(level,probe.getX(),probe.getZ())) continue;
            BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,probe);
            if(!isGoblinBiome(level,pos)||!hasRoom(level,pos)) continue;
            int count=1+random.nextInt(3);
            for(int i=0;i<count;i++) {
                BlockPos spot=pos.offset(random.nextInt(5)-2,0,random.nextInt(5)-2);
                if(!hasRoom(level,spot)) spot=pos;
                // r105 权重：战士 / 弓手 / 萨满按配置比例抽，全被调到 0 时整队不刷。
                GoblinVariant v=MobSpawnWeights.pick(random,PATROL_PICKS);
                if(v==null) break;
                spawn(level,spot,v,MobSpawnType.EVENT);
            }
            return;
        }
    }

    /** 营地居民抽一个变体；四种都被权重调到 0 时返回 {@code null}，这座营地就不放居民。 */
    private static GoblinVariant campVariant(RandomSource random) {
        return MobSpawnWeights.pick(random,CAMP_PICKS);
    }

    /**
     * 【敬而远之】的禁刷闸门。
     *
     * <p>刷怪蛋、指令召唤、幼崽成年转职、其它模组主动召唤，这四类都是玩家 / 脚本直接触发的，
     * 不算「自然生成」，照常放行；巡逻队（EVENT）与营地（STRUCTURE）则整片掐掉。</p>
     */
    private static boolean blockedByDread(ServerLevel level, BlockPos pos, MobSpawnType reason) {
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND
                || reason == MobSpawnType.CONVERSION || reason == MobSpawnType.MOB_SUMMONED) return false;
        return GoblinDread.suppressesSpawn(level, pos);
    }

    private static boolean isGoblinBiome(ServerLevel level,BlockPos pos) {
        var biome=level.getBiome(pos);
        return biome.is(BiomeTags.IS_FOREST)||biome.is(BiomeTags.IS_TAIGA)
                ||biome.is(Biomes.PLAINS)||biome.is(Biomes.SUNFLOWER_PLAINS);
    }
    private static boolean hasRoom(ServerLevel level,BlockPos pos) {
        // 巡逻队的落点会在 ±2 格内偏移，可能正好跨到隔壁还没加载完的区块；
        // 读那里的方块会让主线程同步去生成地形（死锁），所以先整体确认一圈区块都加载好了。
        if(!ChunkSafety.readyAround(level,pos,2)) return false;
        return level.getBlockState(pos.below()).isSolid()&&level.isEmptyBlock(pos)
                &&level.isEmptyBlock(pos.above())&&level.isEmptyBlock(pos.above(2));
    }
    public static Goblin spawn(ServerLevel level,BlockPos pos,GoblinVariant variant,MobSpawnType reason) {
        // 野外刷怪的统一闸门：关掉后巡逻队、营地居民、首领全都不生成。
        if(!SpawnControl.hostileSpawningAllowed(level)) return null;
        // 【敬而远之】：带威压的玩家 64 格内不自然刷哥布林（巡逻队与营地都算）。
        if(blockedByDread(level,pos,reason)) return null;
        EntityType<Goblin> type=GeneratedMod.goblinType(variant);
        if(type==null) return null;
        Goblin goblin=type.create(level);
        if(goblin==null) return null;
        goblin.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,level.random.nextFloat()*360,0);
        goblin.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),reason,null);
        return level.addFreshEntity(goblin)?goblin:null;
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level)||level.dimension()!=Level.OVERWORLD) return;
        // 回调可能来自加载线程；存档数据和队列只在服务器主线程访问。
        var chunkPos=event.getChunk().getPos();
        level.getServer().execute(()->{
            var chunk=level.getChunkSource().getChunkNow(chunkPos.x,chunkPos.z);
            if(chunk==null) return;
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(
                    ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,"goblin_camp"));
            if(structure==null) return;
            var start=chunk.getStartForStructure(structure);
            if(start==null||!start.isValid()) return;
            for(var piece:start.getPieces()) if(piece instanceof GoblinCampPiece camp) {
                BlockPos center=camp.campCenter();
                if(!GoblinCampData.get(level).contains(center) && PENDING_CAMPS.size()<512) {
                    PENDING_CAMPS.add(new PendingCamp(level,center,camp));
                }
            }
        });
    }

    private static void tickPendingCamps(ServerLevel level) {
        if(level.getGameTime()%20!=0) return;
        // 关掉生物生成时营地先原样留在队列里（不标记已处理），以后重新打开开关还能补上居民。
        if(!SpawnControl.hostileSpawningAllowed(level)) return;
        int budget=2;
        var iterator=PENDING_CAMPS.iterator();
        while(iterator.hasNext()&&budget>0) {
            PendingCamp pending=iterator.next();
            if(pending.level()!=level) continue;
            BlockPos center=pending.center();
            // 营地正好落在带威压玩家的 64 格内：先留在队列里，等状态散了再安置居民，
            // 不能直接 mark，否则这座营地会被永久记成「已经安置过」，从此空无一人。
            if(GoblinDread.suppressesSpawn(level,center)) continue;
            GoblinCampPiece camp = pending.camp();
            var bounds = camp.getBoundingBox();
            boolean ready = true;
            for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4 && ready; cx++)
                for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++)
                    if (level.getChunkSource().getChunkNow(cx, cz) == null) { ready = false; break; }
            if (!ready) continue;
            iterator.remove();budget--;
            GoblinCampData data=GoblinCampData.get(level);
            if(data.contains(center)) continue;
            // 等全营地实际加载后再安置居民和垃圾；卸载重载不复制奖励。
            BlockPos gather = camp.gatheringPos();
            BlockPos chiefPos=camp.chiefSpawnPos();
            // r105 权重：首领单独一项，调到 0 时营地照样成村，只是没有首领。
            if(MobSpawnWeights.enabled("goblin_chief") && hasRoom(level,chiefPos)) {
                Goblin chief = spawn(level,chiefPos,GoblinVariant.CHIEF,MobSpawnType.STRUCTURE);
                if (chief != null) chief.setHome(center, gather);
            }
            RandomSource random=RandomSource.create(level.getSeed()^center.asLong());
            var spots = camp.residentPositions();
            int count = Math.min(spots.size(), 10 + camp.sizeTier() * 7);
            for(int i=0;i<count;i++) {
                BlockPos spot = spots.get(i);
                GoblinVariant residentVariant = campVariant(random);
                if (residentVariant == null) continue;
                if (hasRoom(level, spot)) {
                    Goblin resident = spawn(level, spot, residentVariant, MobSpawnType.STRUCTURE);
                    // 登记营地：每天 0 点会自己聚到聚集棚。
                    if (resident != null) resident.setHome(center, gather);
                }
            }
            // 地上跟着哥布林混的元素史莱姆，地下牢笼里关着的俘虏。
            spawnAllySlimes(level, camp, random);
            spawnLairPrisoners(level, camp, random);
            if (camp.isLegacy()) {
                for (int i=0;i<8;i++) {
                    BlockPos spot=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            center.offset(random.nextInt(15)-7,0,random.nextInt(9)-1));
                    GoblinVariant looseVariant = campVariant(random);
                    if(looseVariant != null && hasRoom(level,spot))
                        spawn(level,spot,looseVariant,MobSpawnType.STRUCTURE);
                }
            }
            var trash = new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.BONE,
                    net.minecraft.world.item.Items.ROTTEN_FLESH, net.minecraft.world.item.Items.SHORT_GRASS,
                    net.minecraft.world.item.Items.STICK, net.minecraft.world.item.Items.WHEAT_SEEDS};
            int index = 0;
            for (BlockPos spot : camp.rubbishPositions()) {
                if (!level.isEmptyBlock(spot)) continue;
                var item = new net.minecraft.world.entity.item.ItemEntity(level,
                        spot.getX()+0.5,spot.getY()+0.12,spot.getZ()+0.5,
                        new ItemStack(trash[index++ % trash.length], 1+random.nextInt(3)),0,0,0);
                // 少量持久散落物，玩家可捡取；只生成一次，不做无限补货刷物品。
                item.setUnlimitedLifetime();
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
            data.mark(center);
        }
    }
    public static void onServerStopped(ServerStoppedEvent event) { PENDING_CAMPS.clear(); }

    /** 哥布林萨满驯服的四种元素史莱姆：毒、木、风、水，在营地里帮哥布林打架。 */
    private static void spawnAllySlimes(ServerLevel level, GoblinCampPiece camp, RandomSource random) {
        if (camp.isLegacy() || !SpawnControl.hostileSpawningAllowed(level)) return;
        // r105 权重：被调到 0 的元素史莱姆不再成为营地盟友，四种全关时营地就没有史莱姆。
        List<ElementalSlimeVariant> pool = new ArrayList<>(4);
        for (ElementalSlimeVariant candidate : List.of(ElementalSlimeVariant.POISON,
                ElementalSlimeVariant.NATURE, ElementalSlimeVariant.WIND, ElementalSlimeVariant.WATER)) {
            if (MobSpawnWeights.enabled(candidate.entityId())) pool.add(candidate);
        }
        if (pool.isEmpty()) return;
        int count = 1 + camp.sizeTier() + random.nextInt(2);
        var spots = camp.residentPositions();
        for (int i = 0; i < count && !spots.isEmpty(); i++) {
            BlockPos spot = spots.get(random.nextInt(spots.size()));
            if (!hasRoom(level, spot)) continue;
            ElementalSlimeVariant variant = pool.get(random.nextInt(pool.size()));
            EntityType<ElementalSlime> type = GeneratedMod.elementalSlimeType(variant);
            if (type == null) continue;
            ElementalSlime slime = type.create(level);
            if (slime == null) continue;
            slime.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            slime.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null);
            slime.setTier(ElementalSlimeTier.MATURE, true);
            slime.makeGoblinAlly();
            level.addFreshEntity(slime);
        }
    }

    /** 地下牢笼里的俘虏：村民、铁傀儡，以及悦灵、狗、猫、鹦鹉和随机家畜。 */
    private static void spawnLairPrisoners(ServerLevel level, GoblinCampPiece camp, RandomSource random) {
        if (!SpawnControl.mobSpawningAllowed(level)) return;
        var cells = camp.cagePositions();
        if (cells.isEmpty()) return;
        int count = Math.min(cells.size(), 2 + random.nextInt(2) + camp.sizeTier() / 2);
        for (int i = 0; i < count; i++) {
            BlockPos spot = cells.get(i);
            if (!hasRoom(level, spot)) continue;
            Mob prisoner = createPrisoner(level, random, i);
            if (prisoner == null) continue;
            prisoner.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            prisoner.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null);
            prisoner.setPersistenceRequired();
            // 会飞的囚徒关掉 AI，免得它们贴着笼顶扑腾或卡进栅栏。
            if (prisoner instanceof Allay || prisoner instanceof Parrot) prisoner.setNoAi(true);
            level.addFreshEntity(prisoner);
        }
    }

    private static Mob createPrisoner(ServerLevel level, RandomSource random, int index) {
        EntityType<? extends Mob> type = switch (index) {
            case 0 -> EntityType.VILLAGER;
            case 1 -> EntityType.IRON_GOLEM;
            default -> switch (random.nextInt(7)) {
                case 0 -> EntityType.ALLAY;
                case 1 -> EntityType.WOLF;
                case 2 -> EntityType.CAT;
                case 3 -> EntityType.PARROT;
                case 4 -> EntityType.CHICKEN;
                case 5 -> EntityType.SHEEP;
                default -> EntityType.PIG;
            };
        };
        return type.create(level);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if(!(event.getEntity() instanceof Ravager ravager)||!(ravager.level() instanceof ServerLevel level)) return;
        if(!(event.getSource().getEntity() instanceof Goblin goblin)||goblin.variant()!=GoblinVariant.CHIEF) return;
        var item=GeneratedMod.MOD_ITEMS.get(RoughEquipment.AXE);
        if(item==null) return;
        ItemStack axe=new ItemStack(item.get());
        axe.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),1);
        ravager.spawnAtLocation(axe);
    }
    private GoblinSpawning() {}
}
