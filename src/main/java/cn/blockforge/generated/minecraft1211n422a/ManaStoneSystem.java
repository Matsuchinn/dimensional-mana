package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 高魔素地区的「变异 / 魔石病」机制（r280）。
 *
 * <p>每 20 刻扫一遍玩家周围 64 格内的活体生物，读所在位置的 {@link ManaConcentration}：</p>
 * <ul>
 *   <li><b>有魔素变异体变种的</b>（僵尸→晶化行尸、苦力怕→聚魔爆裂体、骷髅→魔蚀骸骨、
 *       蜘蛛→魔蚀织网者、末影人→虚空蚀影）：在浓度 &gt; 15 的地区连续待满
 *       {@value #MUTATE_AFTER_TICKS} 刻（10 秒）就地变异，装备与血量按比例带走；</li>
 *   <li><b>没有变种的</b>：同一块地方连续待满 {@value #DISEASE_AFTER_TICKS} 刻（120 秒）
 *       得上「{@link ManaStoneDiseaseEffect 魔石病}」——之后每 10 秒加一级缓慢，
 *       缓慢 10 级封顶后每秒扣 1% 最大生命，死亡时额外撒 1～4 份随机等级的魔晶粉。</li>
 * </ul>
 *
 * <p>中途离开高魔素区，驻留计时清零；得病变慢的走表在效果自己身上，不靠本系统续。</p>
 *
 * <p><b>豁免</b>：玩家，以及所有"有技能会消耗魔素"的生物——冒险者、流浪剑士、哥布林这些
 * 本模组生物，还有 {@link MobSkills} 里那些带专属技能的原版生物（女巫、烈焰人、末影人……）。
 * 它们的魔素循环本身就是"抗体"。判定见 {@link #exemptFromDisease}。</p>
 *
 * <p><b>治疗</b>：对患病的生物右键喂一颗金苹果（附魔金苹果也认）——病和缓慢一起清，
 * 再送一段再生。若救的是村民，走原版"治好僵尸村民"的同一条声望通道
 * （{@code ZOMBIE_VILLAGER_CURED}），交易价格立刻拿到折扣。</p>
 */
public final class ManaStoneSystem {
    /** 触发浓度线：严格大于 15。 */
    public static final double THRESHOLD = 15.0;
    /** 有变种的生物：连续滞留 10 秒变异。 */
    public static final int MUTATE_AFTER_TICKS = 200;
    /** 没变种的生物：连续滞留 120 秒得魔石病。 */
    public static final int DISEASE_AFTER_TICKS = 2400;
    /** 扫描半径：玩家周围 4 个区块。 */
    private static final int SCAN_RADIUS = 64;
    /** 驻留计时存在实体持久化数据里的标签名。 */
    private static final String DATA_KEY = "ManaStone";
    private static final String DATA_TICKS = "highManaTicks";

    /** 死亡掉落用的三档魔晶粉，第一次用到时从注册表里取。 */
    private static Item[] powderItems;

    private ManaStoneSystem() {}

    // ---------------------------------------------------------------- 驻留扫描

    /** 由 GeneratedMod 注册：每 20 刻扫一次玩家周边的生物。 */
    public static void tickLevel(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 20 != 0) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        Set<java.util.UUID> seen = new HashSet<>();
        List<Mob> mobs = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player.isSpectator()) continue;
            for (Mob mob : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(SCAN_RADIUS), LivingEntity::isAlive)) {
                if (mob.isRemoved() || !seen.add(mob.getUUID())) continue;
                mobs.add(mob);
            }
            if (mobs.size() >= 512) break; // 超大农场兜底：一轮最多处理这么多只
        }
        for (Mob mob : mobs) tickMob(level, mob);
    }

    private static void tickMob(ServerLevel level, Mob mob) {
        // 已经在生病的：病程由效果自己走，这里不再计时。
        if (mob.hasEffect(GeneratedMod.MANA_STONE_DISEASE)) return;

        CompoundTag root = mob.getPersistentData();
        CompoundTag data = root.getCompound(DATA_KEY);
        int ticks = data.getInt(DATA_TICKS);

        if (ManaConcentration.at(level, mob.blockPosition()) <= THRESHOLD) {
            if (ticks != 0) {
                data.putInt(DATA_TICKS, 0);
                root.put(DATA_KEY, data);
            }
            return;
        }

        ticks += 20;
        EntityType<? extends Mob> variant = variantOf(mob);
        if (variant != null) {
            // 有变种：先抢 10 秒的变异窗口，走不到 120 秒的病线。
            if (ticks >= MUTATE_AFTER_TICKS) {
                mutate(level, mob, variant);
                return;
            }
        } else {
            if (exemptFromDisease(mob)) return;
            if (ticks >= DISEASE_AFTER_TICKS) {
                ManaStoneDiseaseEffect.apply(mob);
                data.putInt(DATA_TICKS, 0);
                root.put(DATA_KEY, data);
                level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5,
                        mob.getZ(), 8, 0.25, 0.3, 0.25, 0.05);
                return;
            }
        }
        data.putInt(DATA_TICKS, ticks);
        root.put(DATA_KEY, data);
    }

    // ---------------------------------------------------------------- 变异

    /** 原版五只对应的魔素变异体；只认精确类型，小僵尸/尸壳/僵尸村民这些都不算。 */
    private static EntityType<? extends Mob> variantOf(Mob mob) {
        Class<?> type = mob.getClass();
        if (type == Zombie.class) return GeneratedMod.CRYSTALLIZED_ZOMBIE.get();
        if (type == Creeper.class) return GeneratedMod.MANA_CREEPER.get();
        if (type == Skeleton.class) return GeneratedMod.CORRUPTED_SKELETON.get();
        if (type == Spider.class) return GeneratedMod.CORRUPTED_WEAVER.get();
        if (type == EnderMan.class) return GeneratedMod.VOID_SHADE.get();
        return null;
    }

    /** 就地变异：convertTo 带走位置与装备，再按原比例把血量搬过去，然后补一次出生结算。 */
    private static void mutate(ServerLevel level, Mob mob, EntityType<? extends Mob> variant) {
        float healthRatio = Math.min(1.0F, mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()));
        Mob mutated;
        try {
            mutated = mob.convertTo(variant, true);
        } catch (RuntimeException ignored) {
            return; // 极端情况（数据竞争等）下放弃这次变异，下一轮还能再来
        }
        if (mutated == null || mutated.isRemoved()) return;
        mutated.finalizeSpawn(level, level.getCurrentDifficultyAt(mutated.blockPosition()),
                MobSpawnType.CONVERSION, null);
        // 变异体通常更厚血：按比例搬过去，别让一只残血的僵尸换个皮就满血复活。
        mutated.setHealth(Math.max(1.0F, mutated.getMaxHealth() * healthRatio));
        // 变异的光尘与嚎叫：让玩家知道刚才那只怪已经"不是原来的怪"了。
        for (int i = 0; i < 24; i++) {
            level.sendParticles(ParticleTypes.PORTAL,
                    mutated.getX() + (level.random.nextDouble() - 0.5) * 1.2,
                    mutated.getY() + level.random.nextDouble() * mutated.getBbHeight(),
                    mutated.getZ() + (level.random.nextDouble() - 0.5) * 1.2,
                    1, 0.0, 0.0, 0.0, 0.02);
        }
        mutated.playAmbientSound();
    }

    // ---------------------------------------------------------------- 豁免

    /**
     * "有技能会消耗魔素"的生物豁免魔石病：玩家、本模组全部生物（冒险者/剑士/哥布林/史莱姆/变异体……），
     * 以及 {@link MobSkills} 里挂了专属技能的原版生物。有变种的怪走变异线，与这里无关。
     */
    private static boolean exemptFromDisease(Mob mob) {
        // 玩家不在这里判：扫描只收 Mob，玩家从头到尾不会进这张表，自然也不会得病。
        if (MobCombatSystem.isOwnMob(mob)) return true;
        return MobCombatConfig.SPECIAL_SKILLS_ENABLED.get() && hasManaSkill(mob);
    }

    /** 这些原版生物的技能会消耗魔素（见 {@link MobSkills}），和冒险者一样自带"抗体"。 */
    private static boolean hasManaSkill(Mob mob) {
        return mob instanceof Witch || mob instanceof Blaze || mob instanceof EnderMan
                || mob instanceof Phantom || mob instanceof Guardian || mob instanceof ElderGuardian
                || mob instanceof IronGolem || mob instanceof Piglin || mob instanceof PiglinBrute
                || mob instanceof ZombifiedPiglin || mob instanceof Ravager
                || mob instanceof SpellcasterIllager || mob instanceof Vindicator
                || mob instanceof Shulker || mob instanceof Ghast || mob instanceof Warden
                || mob instanceof WitherBoss || mob instanceof EnderDragon || mob instanceof Breeze
                || mob instanceof Bogged;
    }

    // ---------------------------------------------------------------- 金苹果治疗

    /** 由 GeneratedMod 注册：对患病生物右键喂金苹果。 */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (!target.hasEffect(GeneratedMod.MANA_STONE_DISEASE)) return;
        ItemStack held = event.getItemStack();
        if (!held.is(Items.GOLDEN_APPLE) && !held.is(Items.ENCHANTED_GOLDEN_APPLE)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        held.consume(1, player);
        cure(target);

        if (target instanceof Villager villager) {
            // 走原版"治好僵尸村民"的同一条声望通道：玩家拿交易折扣。
            ((ServerLevel) target.level()).onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, player, villager);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.mana_stone.villager_saved"), true);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.mana_stone.cured", target.getDisplayName()), true);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(false));
    }

    /** 治愈：清掉病与缓慢，补一段再生作为金苹果的药力；救回已倒地的不在此列（死亡就是死亡）。 */
    private static void cure(LivingEntity target) {
        target.removeEffect(GeneratedMod.MANA_STONE_DISEASE);
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                MobEffects.REGENERATION, 100, 1, false, true, true));
        // 病愈：驻留计时清零，下次进高魔素区重新算 120 秒。
        CompoundTag root = target.getPersistentData();
        CompoundTag data = root.getCompound(DATA_KEY);
        data.putInt(DATA_TICKS, 0);
        root.put(DATA_KEY, data);
        if (target.level() instanceof ServerLevel level) {
            for (int i = 0; i < 10; i++) {
                level.sendParticles(ParticleTypes.END_ROD,
                        target.getX() + (level.random.nextDouble() - 0.5),
                        target.getY() + level.random.nextDouble() * target.getBbHeight(),
                        target.getZ() + (level.random.nextDouble() - 0.5),
                        1, 0.0, 0.01, 0.0, 0.02);
            }
            level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                    target.getSoundSource(), 0.8F, 1.1F);
        }
    }

    // ---------------------------------------------------------------- 死亡掉粉

    /** 由 GeneratedMod 注册：带着魔石病死亡的生物额外掉 1～4 份随机等级（下/中/高）魔晶粉。 */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || level.isClientSide) return;
        if (!victim.hasEffect(GeneratedMod.MANA_STONE_DISEASE)) return;

        Item[] powders = powders(level);
        Item powder = powders[level.getRandom().nextInt(powders.length)];
        int count = 1 + level.getRandom().nextInt(4);
        ItemStack stack = new ItemStack(powder, count);
        // 直接生成掉落物，绕开花絮判定，死亡位置就在脚下。
        ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY() + 0.1, victim.getZ(), stack);
        drop.setPickUpDelay(30);
        level.addFreshEntity(drop);
    }

    private static Item[] powders(ServerLevel level) {
        Item[] local = powderItems;
        if (local == null) {
            local = new Item[] {
                    GeneratedMod.MOD_ITEMS.get("lesser_mana_crystal_dust").value(),
                    GeneratedMod.MOD_ITEMS.get("medium_mana_crystal_dust").value(),
                    GeneratedMod.MOD_ITEMS.get("advanced_mana_crystal_dust").value(),
            };
            powderItems = local;
        }
        return local;
    }
}
