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
import java.util.LinkedHashSet;
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

    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        tickPendingCamps(level);
        if (level.dimension()!=Level.OVERWORLD || level.getGameTime()%200!=0) return;
        for (ServerPlayer player:level.players()) {
            if(player.isSpectator()) continue;
            RandomSource random=player.getRandom();
            if(random.nextFloat()>=(level.isDay()?PATROL_CHANCE_DAY:PATROL_CHANCE_NIGHT)) continue;
            if(level.getEntitiesOfClass(Goblin.class,player.getBoundingBox().inflate(64),Goblin::isAlive).size()>=PATROL_LOCAL_CAP) continue;
            spawnPatrol(level,player,random);
        }
    }

    private static void spawnPatrol(ServerLevel level,ServerPlayer player,RandomSource random) {
        for(int attempt=0;attempt<8;attempt++) {
            double angle=random.nextDouble()*Math.PI*2,distance=30+random.nextDouble()*26;
            BlockPos probe=BlockPos.containing(player.getX()+Math.cos(angle)*distance,0,
                    player.getZ()+Math.sin(angle)*distance);
            BlockPos pos=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,probe);
            if(!isGoblinBiome(level,pos)||!hasRoom(level,pos)) continue;
            int count=1+random.nextInt(3);
            for(int i=0;i<count;i++) {
                BlockPos spot=pos.offset(random.nextInt(5)-2,0,random.nextInt(5)-2);
                if(!hasRoom(level,spot)) spot=pos;
                GoblinVariant v=switch(random.nextInt(4)) {
                    case 0,1 -> GoblinVariant.WARRIOR;
                    case 2 -> GoblinVariant.ARCHER;
                    default -> GoblinVariant.SHAMAN;
                };
                spawn(level,spot,v,MobSpawnType.EVENT);
            }
            return;
        }
    }

    private static GoblinVariant campVariant(RandomSource random) {
        int roll=random.nextInt(10);
        return roll<4?GoblinVariant.YOUNG:roll<7?GoblinVariant.WARRIOR:
                roll<9?GoblinVariant.ARCHER:GoblinVariant.SHAMAN;
    }
    private static boolean isGoblinBiome(ServerLevel level,BlockPos pos) {
        var biome=level.getBiome(pos);
        return biome.is(BiomeTags.IS_FOREST)||biome.is(BiomeTags.IS_TAIGA)
                ||biome.is(Biomes.PLAINS)||biome.is(Biomes.SUNFLOWER_PLAINS);
    }
    private static boolean hasRoom(ServerLevel level,BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid()&&level.isEmptyBlock(pos)
                &&level.isEmptyBlock(pos.above())&&level.isEmptyBlock(pos.above(2));
    }
    public static Goblin spawn(ServerLevel level,BlockPos pos,GoblinVariant variant,MobSpawnType reason) {
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
                if(!GoblinCampData.get(level).contains(center)) {
                    PENDING_CAMPS.add(new PendingCamp(level,center,camp));
                }
            }
        });
    }

    private static void tickPendingCamps(ServerLevel level) {
        if(level.getGameTime()%20!=0) return;
        int budget=2;
        var iterator=PENDING_CAMPS.iterator();
        while(iterator.hasNext()&&budget>0) {
            PendingCamp pending=iterator.next();
            if(pending.level()!=level) continue;
            BlockPos center=pending.center();
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
            if(hasRoom(level,chiefPos)) {
                Goblin chief = spawn(level,chiefPos,GoblinVariant.CHIEF,MobSpawnType.STRUCTURE);
                if (chief != null) chief.setHome(center, gather);
            }
            RandomSource random=RandomSource.create(level.getSeed()^center.asLong());
            var spots = camp.residentPositions();
            int count = Math.min(spots.size(), 10 + camp.sizeTier() * 7);
            for(int i=0;i<count;i++) {
                BlockPos spot = spots.get(i);
                if (hasRoom(level, spot)) {
                    Goblin resident = spawn(level, spot, campVariant(random), MobSpawnType.STRUCTURE);
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
                    if(hasRoom(level,spot)) spawn(level,spot,campVariant(random),MobSpawnType.STRUCTURE);
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
        if (camp.isLegacy()) return;
        ElementalSlimeVariant[] pool = {ElementalSlimeVariant.POISON, ElementalSlimeVariant.NATURE,
                ElementalSlimeVariant.WIND, ElementalSlimeVariant.WATER};
        int count = 1 + camp.sizeTier() + random.nextInt(2);
        var spots = camp.residentPositions();
        for (int i = 0; i < count && !spots.isEmpty(); i++) {
            BlockPos spot = spots.get(random.nextInt(spots.size()));
            if (!hasRoom(level, spot)) continue;
            ElementalSlimeVariant variant = pool[random.nextInt(pool.length)];
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
