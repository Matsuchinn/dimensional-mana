package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 专业鉴定眼镜的服务端逻辑。
 *
 * <p>三件事：一是按秒扣「佩戴」与「锁定目标」的魔素；二是把目标生物的
 * 生命 / 魔素 / 斗气 / 恢复 / 抗性 / 技能冷却打包同步给客户端；
 * 三是处理主动扫描（V 键）并把它换算成一份带元素颜色的雷达数据。</p>
 *
 * <p>所有消耗都在服务端结算，客户端只负责画；这样玩家改客户端也骗不到免费解析。</p>
 */
public final class ProfessionalAppraisalSystem {
    /** 两次主动扫描之间至少隔多少刻，防止按住按键把魔素一下抽干。 */
    private static final int SCAN_COOLDOWN_TICKS = 10;
    /** 雷达最多标记多少只生物：刷怪塔里一屏几百只，全发过去既卡又没必要。 */
    private static final int RADAR_LIMIT = 160;
    /** 没有抗性表可读的怪物（本模组生物、其他模组的怪）用默认紫色描边。 */
    private static final int DEFAULT_RADAR_COLOR = 0xFF9C7BFF;
    /** 奥术没有对应的 ElementType 颜色，单独给一个亮紫。 */
    private static final int ARCANE_RADAR_COLOR = 0xFFB98CFF;

    private static final Map<UUID, Long> LAST_SCAN = new HashMap<>();

    // ---------------------------------------------------------------- 每刻维护

    public static void tick(ServerPlayer player) {
        int syncInterval = Math.max(1, AppraisalConfig.SYNC_INTERVAL_TICKS.get());
        if (!ProfessionalAppraisalGlassesItem.isEquipped(player)) {
            // 没戴眼镜时不用那么勤：每秒提醒一次「当前没有数据」就够了。
            if (player.tickCount % 20 == 0) {
                PacketDistributor.sendToPlayer(player, ProAppraisalPayload.unequipped());
            }
            return;
        }
        LivingEntity target = findLockedTarget(player);
        if (player.tickCount % 20 == 0) {
            double cost = AppraisalConfig.IDLE_MANA_COST.get()
                    + (target != null ? AppraisalConfig.LOCK_MANA_COST.get() : 0.0);
            ManaData.drainMana(player, cost);
        }
        if (player.tickCount % syncInterval == 0) {
            PacketDistributor.sendToPlayer(player, build(player, target));
        }
    }

    /** 准心对准的最近一只生物；没有就返回 null。 */
    private static LivingEntity findLockedTarget(ServerPlayer player) {
        double range = AppraisalConfig.LOCK_RANGE.get();
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof LivingEntity living && living != player && living.isAlive(), range);
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    // ---------------------------------------------------------------- 数据打包

    private static ProAppraisalPayload build(ServerPlayer player, LivingEntity target) {
        double playerMana = ManaData.get(player).mana();
        // 魔素为 0 时眼镜还能用，只是退回「只看得到生命值」的基础模式。
        boolean active = playerMana > 0.0;
        if (target == null) {
            return new ProAppraisalPayload(true, active, playerMana, -1, "",
                    0.0F, 0.0F, 0.0, 0.0, 0, 0, 0.0, 0.0,
                    List.of(), List.of(), "", 0, 0);
        }
        ManaData.Snapshot data = ManaData.get(target);
        List<String> resistances = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        if (active) collectTraits(target, resistances, weaknesses);
        MobSkills.SkillStatus skill = active ? MobSkills.mainSkill(target) : null;
        return new ProAppraisalPayload(true, active, playerMana,
                target.getId(), target.getDisplayName().getString(),
                target.getHealth(), target.getMaxHealth(),
                active ? data.mana() : 0.0,
                active ? data.maxMana() : 0.0,
                active ? data.battleQi() : 0,
                active ? data.maxBattleQi() : 0,
                active ? manaRegenPerSecond(target) : 0.0,
                active ? battleQiRegenPerSecond(target) : 0.0,
                resistances, weaknesses,
                skill == null ? "" : skill.id(),
                skill == null ? 0 : skill.totalTicks(),
                skill == null ? 0 : skill.remainingTicks());
    }

    /** 目标每秒实际能回的魔素：原版生物读难度恢复，玩家按亲和与法杖加成算。 */
    private static double manaRegenPerSecond(LivingEntity entity) {
        if (MobCombatSystem.enabled() && MobCombatSystem.isManagedMob(entity)) {
            return Math.max(0.0, entity.getPersistentData()
                    .getCompound(ManaData.ROOT_TAG).getDouble("mobManaRegen"));
        }
        if (entity instanceof Player player) {
            ManaData.Snapshot data = ManaData.get(player);
            double regen = ManaConfig.PLAYER_MANA_REGEN.get()
                    + data.affinity() * MagicEquipment.affinityMultiplier(player);
            MagicStaffItem.StaffStats staff = MagicStaffItem.heldStats(player);
            regen *= 1.0 + staff.manaRegeneration();
            if (player.level() instanceof ServerLevel level) {
                regen *= ManaConcentration.regenerationMultiplier(
                        ManaConcentration.at(level, player.blockPosition()));
            }
            return Math.max(0.0, regen);
        }
        return 0.0;
    }

    /** 目标每秒实际能回的斗气：原版敌对生物在初始化时写了 mobBattleQiRegen，其余为 0。 */
    private static double battleQiRegenPerSecond(LivingEntity entity) {
        if (MobCombatSystem.isManagedMob(entity)) {
            return Math.max(0.0, entity.getPersistentData()
                    .getCompound(ManaData.ROOT_TAG).getDouble("mobBattleQiRegen"));
        }
        return 0.0;
    }

    /**
     * 收集该生物登记过的抗性与易伤。
     *
     * <p>这里<b>不</b>按难度过滤：普通难度下这些数值确实不生效，但玩家仍然想看生物登记了什么，
     * 客户端会用灰色加「未生效」标注来区分，而不是干脆什么都不显示。</p>
     */
    private static void collectTraits(LivingEntity target, List<String> resistances, List<String> weaknesses) {
        MobProfile profile = MobProfile.of(target.getType());
        if (profile == null || profile.disabled()) return;
        for (CombatElement element : CombatElement.values()) {
            addTrait(profile.resistance(element), element.id(), resistances, weaknesses);
        }
        // 潜影贝的 99% 物理减伤只在闭壳时成立，开壳后不该骗玩家。
        if (target instanceof Shulker shulker && !MobCombatSystem.shulkerClosed(shulker)) {
            addTrait(0.0, "physical", resistances, weaknesses);
        } else {
            addTrait(profile.typeResistance("physical"), "physical", resistances, weaknesses);
        }
        addTrait(profile.typeResistance("explosion"), "explosion", resistances, weaknesses);
        addTrait(profile.special("magic_crit"), "magic_crit", resistances, weaknesses);
        addTrait(profile.special("melt"), "melt", resistances, weaknesses);
    }

    private static void addTrait(double value, String key, List<String> resistances, List<String> weaknesses) {
        if (Math.abs(value) < 1.0E-4) return;
        String line = key + "=" + String.format(Locale.ROOT, "%.4f", value);
        if (value > 0.0) resistances.add(line);
        else weaknesses.add(line);
    }

    // ---------------------------------------------------------------- 主动扫描

    public static void scan(ServerPlayer player) {
        if (!AppraisalConfig.SHOW_RADAR.get()) return;
        if (!ProfessionalAppraisalGlassesItem.isEquipped(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.appraisal_scan_requires_glasses").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long last = LAST_SCAN.get(player.getUUID());
        if (last != null && now - last < SCAN_COOLDOWN_TICKS) return;
        double cost = AppraisalConfig.SCAN_MANA_COST.get();
        if (ManaData.get(player).mana() < cost) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.appraisal_scan_no_mana").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!ManaData.spendMana(player, cost)) return;
        LAST_SCAN.put(player.getUUID(), now);

        double radius = AppraisalConfig.SCAN_RADIUS.get();
        List<ManaRadarPayload.Target> targets = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                mob -> mob.isAlive() && !mob.isSpectator())) {
            if (targets.size() >= RADAR_LIMIT) break;
            targets.add(radarTarget(mob));
        }
        PacketDistributor.sendToPlayer(player,
                new ManaRadarPayload(targets, AppraisalConfig.SCAN_DURATION_TICKS.get()));
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.6F, 1.7F);
    }

    /** 一只怪物的雷达条目：优先亮易伤元素，其次亮抗性元素，都没有就默认紫色。 */
    private static ManaRadarPayload.Target radarTarget(Mob mob) {
        MobProfile profile = MobProfile.of(mob.getType());
        if (profile != null && !profile.disabled() && MobCombatSystem.resistancesActive(mob)) {
            CombatElement vulnerableElement = null;
            CombatElement resistantElement = null;
            double worst = 0.0;
            double best = 0.0;
            for (CombatElement element : CombatElement.values()) {
                double value = profile.resistance(element);
                if (value < worst) {
                    worst = value;
                    vulnerableElement = element;
                }
                if (value > best) {
                    best = value;
                    resistantElement = element;
                }
            }
            if (vulnerableElement != null) {
                return new ManaRadarPayload.Target(mob.getId(), colorOf(vulnerableElement), true);
            }
            if (resistantElement != null) {
                return new ManaRadarPayload.Target(mob.getId(), colorOf(resistantElement), false);
            }
        }
        return new ManaRadarPayload.Target(mob.getId(), DEFAULT_RADAR_COLOR, false);
    }

    private static int colorOf(CombatElement element) {
        ElementType type = element.elementType();
        return type == null ? ARCANE_RADAR_COLOR : type.color();
    }

    /** 玩家下线或换维度时清掉节流记录，避免 UUID 越积越多。 */
    public static void clear(UUID playerId) {
        if (playerId == null) {
            LAST_SCAN.clear();
        } else {
            LAST_SCAN.remove(playerId);
        }
    }

    private ProfessionalAppraisalSystem() {}
}
