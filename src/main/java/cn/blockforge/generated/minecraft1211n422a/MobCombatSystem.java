package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 原版生物的魔素/斗气双轨资源、环境恢复与元素抗性。
 *
 * <p>本模组生物默认保留自身数值，再叠加全局公式；独立开关仅影响本模组生物。
 * 通用贡献单独记录，旧存档迁移和开关切换均不会重复叠加。</p>
 */
public final class MobCombatSystem {
    private static final String TAG_APPLIED = "mobCombatApplied";
    private static final String TAG_RESTORE_DAY = "friendlyRestoreDay";
    private static final List<AntiMagicZone> ZONES = new ArrayList<>();

    private record AntiMagicZone(ResourceKey<Level> dimension, Vec3 center, double radius, long expiresAt) { }

    public static boolean enabled() {
        return MobCombatConfig.ENABLED.get();
    }

    public static boolean stacking() {
        return MobCombatConfig.STACK_WITH_EXISTING.get();
    }

    public static boolean isOwnMob(LivingEntity entity) {
        return entity instanceof Mob && entity.getType().builtInRegistryHolder().key().location()
                .getNamespace().equals(GeneratedMod.MOD_ID);
    }

    /** 本系统接管非玩家生物；本模组生物只有显式开启独立模式才退出。 */
    public static boolean isManagedMob(LivingEntity entity) {
        if (!(entity instanceof Mob)) return false;
        if (entity instanceof Player) return false;
        EntityType<?> type = entity.getType();
        if (isOwnMob(entity) && MobCombatConfig.OWN_MOBS_INDEPENDENT.get()) return false;
        MobProfile profile = MobProfile.of(type);
        if (profile != null) return !profile.disabled();
        return type.getCategory() != MobCategory.MISC;
    }

    public static boolean isFriendlyMob(LivingEntity entity) {
        if (entity instanceof Enemy) return false;
        return entity.getType().getCategory() != MobCategory.MONSTER;
    }

    public static boolean isHostileMob(LivingEntity entity) {
        return !isFriendlyMob(entity);
    }

    /** 新生物第一次进入世界时写入基础资源。 */
    public static void initialize(LivingEntity entity, CompoundTag tag) {
        if (isOwnMob(entity)) {
            ManaData.fill(tag, 100, 100, 1.0, 0, 0);
            refreshOwnPools(entity, tag);
            return;
        }
        if (!enabled() || !isManagedMob(entity)) {
            ManaData.fill(tag, 100, 100, 1.0, 0, 0);
            return;
        }
        MobProfile profile = MobProfile.of(entity.getType());
        double health = Math.max(1.0, entity.getMaxHealth());
        double mana;
        double battleQi;
        double manaRegen;
        double battleQiRegen;
        if (isFriendlyMob(entity)) {
            mana = health * MobCombatConfig.FRIENDLY_MANA_RATIO.get();
            battleQi = health * MobCombatConfig.FRIENDLY_BATTLE_QI_RATIO.get();
            manaRegen = 0.0;
            battleQiRegen = 0.0;
        } else {
            DifficultyTuning tuning = tuning(entity.level().getDifficulty());
            mana = health * tuning.manaRatio + tuning.manaFlat;
            battleQi = health * tuning.battleQiRatio + tuning.battleQiFlat;
            manaRegen = tuning.regen;
            battleQiRegen = tuning.regen;
        }
        if (profile != null) {
            double profileMana = profile.mana() == null ? 0.0 : profile.mana();
            double profileQi = profile.battleQi() == null ? 0.0 : profile.battleQi();
            double profileManaRegen = profile.manaRegen() == null ? 0.0 : profile.manaRegen();
            double profileQiRegen = profile.battleQiRegen() == null ? 0.0 : profile.battleQiRegen();
            if (stacking()) {
                mana += profileMana;
                battleQi += profileQi;
                manaRegen += profileManaRegen;
                battleQiRegen += profileQiRegen;
            } else {
                if (profile.mana() != null) mana = profileMana;
                if (profile.battleQi() != null) battleQi = profileQi;
                if (profile.manaRegen() != null) manaRegen = profileManaRegen;
                if (profile.battleQiRegen() != null) battleQiRegen = profileQiRegen;
            }
        }
        mana = clamp(mana, 0.0, ManaConfig.GLOBAL_MANA_CAP.get());
        battleQi = clamp(battleQi, 0.0, ManaConfig.GLOBAL_BATTLE_QI_CAP.get());
        ManaData.fill(tag, mana, mana, 1.0, (int)Math.round(battleQi), (int)Math.round(battleQi));
        tag.putDouble("mobManaRegen", Math.max(0.0, manaRegen));
        tag.putDouble("mobBattleQiRegen", Math.max(0.0, battleQiRegen));
        tag.putBoolean(TAG_APPLIED, true);
    }

    /**
     * 生物已有自定义数值（本模组 setMobPools 或其他模组写入）时，按「叠加」追加通用数值。
     * 只追加一次，之后不再重复。
     */
    public static void stackOntoExisting(LivingEntity entity, CompoundTag tag) {
        if (isOwnMob(entity)) {
            refreshOwnPools(entity, tag);
            return;
        }
        if (!enabled() || !stacking() || tag.getBoolean(TAG_APPLIED) || !isManagedMob(entity)) return;
        tag.putBoolean(TAG_APPLIED, true);
        double health = Math.max(1.0, entity.getMaxHealth());
        double mana;
        double battleQi;
        if (isFriendlyMob(entity)) {
            mana = health * MobCombatConfig.FRIENDLY_MANA_RATIO.get();
            battleQi = health * MobCombatConfig.FRIENDLY_BATTLE_QI_RATIO.get();
        } else {
            DifficultyTuning tuning = tuning(entity.level().getDifficulty());
            mana = health * tuning.manaRatio + tuning.manaFlat;
            battleQi = health * tuning.battleQiRatio + tuning.battleQiFlat;
        }
        MobProfile profile = MobProfile.of(entity.getType());
        if (profile != null) {
            mana += profile.mana() == null ? 0.0 : profile.mana();
            battleQi += profile.battleQi() == null ? 0.0 : profile.battleQi();
        }
        double oldBaseMana = tag.contains("baseMaxMana") ? tag.getDouble("baseMaxMana") : tag.getDouble("maxMana");
        int oldBaseQi = tag.contains("baseMaxBattleQi") ? tag.getInt("baseMaxBattleQi") : tag.getInt("maxBattleQi");
        double newBaseMana = clamp(oldBaseMana + mana, 1.0, ManaConfig.GLOBAL_MANA_CAP.get());
        int newBaseQi = (int)clamp(oldBaseQi + battleQi, 0.0, ManaConfig.GLOBAL_BATTLE_QI_CAP.get());
        tag.putDouble("baseMaxMana", newBaseMana);
        tag.putDouble("maxMana", newBaseMana);
        tag.putDouble("mana", clamp(tag.getDouble("mana") + mana, 0.0, newBaseMana));
        tag.putInt("baseMaxBattleQi", newBaseQi);
        tag.putInt("maxBattleQi", newBaseQi);
        tag.putInt("battleQi", clampInt(tag.getInt("battleQi") + (int)Math.round(battleQi), 0, newBaseQi));
        // 旧存档里的生物没有恢复速度字段：按当前难度补一份，否则它们永远不会自然恢复。
        if (!tag.contains("mobManaRegen") || !tag.contains("mobBattleQiRegen")) {
            double regen = isFriendlyMob(entity) ? 0.0 : tuning(entity.level().getDifficulty()).regen;
            if (profile != null) {
                regen += profile.manaRegen() == null ? 0.0 : profile.manaRegen();
            }
            tag.putDouble("mobManaRegen", Math.max(0.0, regen));
            double qiRegen = isFriendlyMob(entity) ? 0.0 : tuning(entity.level().getDifficulty()).regen;
            if (profile != null) {
                qiRegen += profile.battleQiRegen() == null ? 0.0 : profile.battleQiRegen();
            }
            tag.putDouble("mobBattleQiRegen", Math.max(0.0, qiRegen));
        }
    }

    /** 自身基础值与通用贡献分开保存；可反复调用且可以安全切换独立模式。 */
    static void refreshOwnPools(LivingEntity entity, CompoundTag tag) {
        if (!tag.contains("ownIntrinsicMana")) {
            // 旧版从未给本模组生物叠加，因此旧上限就是自身基础值。
            tag.putDouble("ownIntrinsicMana", tag.getDouble("baseMaxMana"));
            tag.putInt("ownIntrinsicQi", tag.getInt("baseMaxBattleQi"));
        }
        boolean active = enabled() && isManagedMob(entity);
        double mana = 0.0, qi = 0.0, mr = 0.0, qr = 0.0;
        if (active) {
            double health = Math.max(1.0, entity.getMaxHealth());
            if (isFriendlyMob(entity)) {
                mana = health * MobCombatConfig.FRIENDLY_MANA_RATIO.get();
                qi = health * MobCombatConfig.FRIENDLY_BATTLE_QI_RATIO.get();
            } else {
                DifficultyTuning t = tuning(entity.level().getDifficulty());
                mana = health * t.manaRatio + t.manaFlat;
                qi = health * t.battleQiRatio + t.battleQiFlat;
                mr = t.regen;
                qr = t.regen;
            }
            MobProfile p = MobProfile.of(entity.getType());
            if (p != null) {
                mana += p.mana() == null ? 0.0 : p.mana();
                qi += p.battleQi() == null ? 0.0 : p.battleQi();
                mr += p.manaRegen() == null ? 0.0 : p.manaRegen();
                qr += p.battleQiRegen() == null ? 0.0 : p.battleQiRegen();
            }
        }
        double oldMana = tag.getDouble("baseMaxMana");
        int oldQi = tag.getInt("baseMaxBattleQi");
        double newMana = clamp(tag.getDouble("ownIntrinsicMana") + mana, 0.0, ManaConfig.GLOBAL_MANA_CAP.get());
        int newQi = (int)Math.round(clamp(tag.getInt("ownIntrinsicQi") + qi, 0.0, ManaConfig.GLOBAL_BATTLE_QI_CAP.get()));
        // 切换配置只保留当前充盈比例，不能利用开关反复免费补满。
        if (Math.abs(oldMana - newMana) > 0.00001) {
            double fraction = oldMana > 0 ? clamp(tag.getDouble("mana") / oldMana, 0, 1) : 1;
            tag.putDouble("mana", newMana * fraction);
        }
        if (oldQi != newQi) {
            double fraction = oldQi > 0 ? clamp((double)tag.getInt("battleQi") / oldQi, 0, 1) : 1;
            tag.putInt("battleQi", (int)Math.round(newQi * fraction));
        }
        tag.putDouble("baseMaxMana", newMana);
        tag.putDouble("maxMana", newMana);
        tag.putInt("baseMaxBattleQi", newQi);
        tag.putInt("maxBattleQi", newQi);
        tag.putDouble("mobManaRegen", Math.max(0, mr));
        tag.putDouble("mobBattleQiRegen", Math.max(0, qr));
        tag.putBoolean(TAG_APPLIED, active);
    }

    /** 每秒（20 刻）调用一次的资源恢复。 */
    public static void tick(LivingEntity entity, ManaData.Snapshot snapshot) {
        if (!enabled() || !isManagedMob(entity)) return;
        CompoundTag tag = entity.getPersistentData().getCompound(ManaData.ROOT_TAG);
        if (isFriendlyMob(entity)) {
            long day = entity.level().getDayTime() / Math.max(20L, MobCombatConfig.FRIENDLY_FULL_RESTORE_TICKS.get()) ;
            long lastDay = tag.getLong(TAG_RESTORE_DAY);
            if (lastDay != day) {
                tag.putLong(TAG_RESTORE_DAY, day);
                // 第一次生成的那一天不回满，避免刚刷出来就白送一次恢复。
                if (lastDay != 0L) {
                    ManaData.setMana(entity, snapshot.maxMana(), false);
                    ManaData.setBattleQi(entity, snapshot.maxBattleQi());
                }
            }
            return;
        }
        if (snapshot.exhaustionTicks() > 0) return;
        double manaRegen = tag.getDouble("mobManaRegen");
        double qiRegen = tag.getDouble("mobBattleQiRegen");
        if (manaRegen > 0.0 && snapshot.mana() < snapshot.maxMana()) {
            double multiplier = 1.0;
            if (entity.level() instanceof ServerLevel level) {
                double concentration = ManaConcentration.at(level, entity.blockPosition());
                double environment = ManaConcentration.regenerationMultiplier(concentration);
                multiplier = 1.0 + (environment - 1.0) * MobCombatConfig.ENVIRONMENT_REGEN_SCALE.get();
            }
            if (inAntiMagicZone(entity)) multiplier = 0.0;
            if (multiplier > 0.0) ManaData.restoreMana(entity, manaRegen * multiplier, false);
        }
        // 斗气溃散期间不能靠自然恢复续上，必须等状态结束。
        if (entity.hasEffect(GeneratedMod.BATTLE_QI_COLLAPSE)) return;
        if (qiRegen > 0.0 && snapshot.battleQi() < snapshot.maxBattleQi()) {
            // 简单难度每秒 0.1 也应累计生效，不能每次四舍五入成零。
            double accrued = tag.getDouble("mobQiRegenRemainder") + qiRegen;
            int restored = (int)Math.floor(accrued);
            tag.putDouble("mobQiRegenRemainder", accrued - restored);
            if (restored > 0) ManaData.setBattleQi(entity,
                    Math.min(snapshot.maxBattleQi(), snapshot.battleQi() + restored));
        }
    }

    /**
     * 生物专配里的元素 / 物理 / 爆炸抗性与易伤是否生效。
     *
     * <p>默认 <b>任何难度</b> 都生效，只受总开关 {@code enabled} 与专配开关 {@code off} 影响；
     * 想要「只有困难难度才有元素克制」可以把 {@code resistances_hard_only} 打开。</p>
     */
    public static boolean resistancesActive(LivingEntity entity) {
        if (!enabled()) return false;
        MobProfile profile = MobProfile.of(entity.getType());
        if (profile == null || profile.disabled()) return false;
        return !MobCombatConfig.RESISTANCES_HARD_ONLY.get()
                || entity.level().getDifficulty() == Difficulty.HARD;
    }

    /** 伤害倍率：1.0 = 无变化；抗性为正则减伤，为负则易伤。 */
    public static double damageMultiplier(LivingEntity victim, DamageSource source) {
        MobProfile profile = MobProfile.of(victim.getType());
        if (profile == null || !resistancesActive(victim)) return 1.0;
        CombatElement.Kind kind = CombatElement.kindOf(source);
        CombatElement element = CombatElement.elementOf(source);
        double resist;
        if (element != null) {
            resist = profile.resistance(element);
        } else if (kind == CombatElement.Kind.EXPLOSION) {
            // 爆炸与物理同样吃 all 总抗性；单独写了 explosion 就以单独值为准。
            resist = profile.typeResistance("explosion");
        } else if (kind == CombatElement.Kind.PHYSICAL) {
            // 空手、剑砍、箭矢都算物理：all=0.25 必须在这里真正生效。
            resist = profile.typeResistance("physical");
        } else {
            resist = 0.0;
        }
        // 潜影贝：只有闭壳时才吃 99% 物理抗性，开壳后没有任何减免。
        if (victim instanceof Shulker shulker && kind == CombatElement.Kind.PHYSICAL) {
            resist = shulkerClosed(shulker) ? profile.typeResistance("physical") : 0.0;
        }
        if (!Double.isFinite(resist)) return 1.0;
        double multiplier = 1.0 - resist;
        return Math.max(0.0, Math.min(20.0, multiplier));
    }

    /** 潜影贝闭壳判定：客户端可见的开口量接近 0。 */
    public static boolean shulkerClosed(Shulker shulker) {
        return shulker.getClientPeekAmount(1.0F) <= 0.01F;
    }

    /** 女巫这类「魔法暴击抗性」：命中时按概率把这次法术暴击降级为普通命中。 */
    public static boolean cancelsSpellCritical(LivingEntity target) {
        MobProfile profile = MobProfile.of(target.getType());
        if (profile == null || !resistancesActive(target)) return false;
        double resistance = profile.special("magic_crit");
        return resistance > 0.0 && target.getRandom().nextDouble() < Math.min(1.0, resistance);
    }

    /** 元素反应易伤（目前用于铁傀儡的「融化反应易伤」）。 */
    public static double reactionVulnerability(LivingEntity target, String reaction) {
        MobProfile profile = MobProfile.of(target.getType());
        if (profile == null || !resistancesActive(target)) return 0.0;
        return profile.special(reaction);
    }

    /** 监守者标记：受到声波/音元素伤害时额外 +15 点。 */
    public static float markSonicBonus(LivingEntity target, DamageSource source, float damage) {
        if (!target.hasEffect(GeneratedMod.WARDEN_MARK)) return damage;
        CombatElement element = CombatElement.elementOf(source);
        if (element != CombatElement.SOUND && !SpellDamage.isSonic(source)) return damage;
        return damage + 15.0F;
    }

    /** 远古诅咒：来自远古守卫者的伤害提高 200%。 */
    public static float curseMultiplier(LivingEntity target, DamageSource source) {
        if (!target.hasEffect(GeneratedMod.ANCIENT_CURSE)) return 1.0F;
        return source.getEntity() instanceof net.minecraft.world.entity.monster.ElderGuardian ? 3.0F : 1.0F;
    }

    // ---- 反魔法区域 ----

    public static void addAntiMagicZone(ServerLevel level, Vec3 center, double radius, int ticks) {
        long expiresAt = level.getGameTime() + ticks;
        ZONES.removeIf(zone -> zone.dimension().equals(level.dimension()) && zone.center().distanceToSqr(center) < 1.0);
        ZONES.add(new AntiMagicZone(level.dimension(), center, radius, expiresAt));
    }

    public static boolean inAntiMagicZone(LivingEntity entity) {
        if (ZONES.isEmpty()) return false;
        long now = entity.level().getGameTime();
        for (AntiMagicZone zone : ZONES) {
            if (zone.expiresAt() <= now) continue;
            if (!zone.dimension().equals(entity.level().dimension())) continue;
            if (zone.center().distanceToSqr(entity.position()) <= zone.radius() * zone.radius()) return true;
        }
        return false;
    }

    public static void tickLevel(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<AntiMagicZone> iterator = ZONES.iterator();
        while (iterator.hasNext()) {
            AntiMagicZone zone = iterator.next();
            if (zone.expiresAt() <= now) iterator.remove();
        }
    }

    public static void clearAll() {
        ZONES.clear();
    }

    private record DifficultyTuning(double manaRatio, double manaFlat, double battleQiRatio,
                                    double battleQiFlat, double regen) { }

    private static DifficultyTuning tuning(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> new DifficultyTuning(MobCombatConfig.PEACE_MANA_RATIO.get(), 0.0,
                    MobCombatConfig.PEACE_BATTLE_QI_RATIO.get(), 0.0, MobCombatConfig.PEACE_MANA_REGEN.get());
            case EASY -> new DifficultyTuning(MobCombatConfig.EASY_MANA_RATIO.get(), MobCombatConfig.EASY_MANA_FLAT.get(),
                    MobCombatConfig.EASY_BATTLE_QI_RATIO.get(), MobCombatConfig.EASY_BATTLE_QI_FLAT.get(),
                    MobCombatConfig.EASY_REGEN.get());
            case NORMAL -> new DifficultyTuning(MobCombatConfig.NORMAL_MANA_RATIO.get(), MobCombatConfig.NORMAL_MANA_FLAT.get(),
                    MobCombatConfig.NORMAL_BATTLE_QI_RATIO.get(), MobCombatConfig.NORMAL_BATTLE_QI_FLAT.get(),
                    MobCombatConfig.NORMAL_REGEN.get());
            case HARD -> new DifficultyTuning(MobCombatConfig.HARD_MANA_RATIO.get(), MobCombatConfig.HARD_MANA_FLAT.get(),
                    MobCombatConfig.HARD_BATTLE_QI_RATIO.get(), MobCombatConfig.HARD_BATTLE_QI_FLAT.get(),
                    MobCombatConfig.HARD_REGEN.get());
        };
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private MobCombatSystem() {}
}
