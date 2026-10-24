package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ManaData {
    /** 玩家/生物身上存魔素与斗气的根标签；跨类读取时用同一个常量，避免拼错。 */
    public static final String ROOT_TAG = "DimensionalMana";
    private static final String ROOT = ROOT_TAG;

    public record Snapshot(double mana, double maxMana, double affinity, int battleQi, int maxBattleQi, int permanentBattleQiBonus, int exhaustionTicks) {}

    public static Snapshot get(LivingEntity entity) {
        init(entity);
        CompoundTag t = entity.getPersistentData().getCompound(ROOT);
        double maxMana = t.getDouble("baseMaxMana");
        int maxBattleQi = t.getInt("baseMaxBattleQi");
        if (entity instanceof Player player) {
            int amulets = MagicAccessoryItem.equippedCount(player, "magic_amulet", "charm");
            double casketMultiplier = MagicTrinketItem.isEquipped(player, "ancient_casket") ? 1.16 : 1.0;
            maxMana *= MagicProgression.maxManaMultiplier(player) * (1.0 + amulets * 0.15) * casketMultiplier;
            // 披风的魔素上限加成是独立乘区：伤害偏转 +8%、魔法大师 +25%，各自再乘一次。
            maxMana *= MagicEquipment.maxManaMultiplier(player);
            maxBattleQi = (int)Math.round(maxBattleQi * MagicProgression.maxBattleQiMultiplier(player) * casketMultiplier);
            // 斗气进阶的「斗气强化」：每级直接在最终上限上加 10 点（不吃百分比乘区）。
            maxBattleQi += t.getCompound("qiSkills").getInt("battle_qi") * QiProgression.BATTLE_QI_PER_RANK;
        }
        maxMana = Math.min(maxMana, ManaConfig.GLOBAL_MANA_CAP.get());
        maxBattleQi = Math.min(maxBattleQi, ManaConfig.GLOBAL_BATTLE_QI_CAP.get());
        return new Snapshot(t.getDouble("mana"), maxMana, t.getDouble("affinity"),
                t.getInt("battleQi"), maxBattleQi, t.getInt("permanentBattleQiBonus"), t.getInt("exhaustionTicks"));
    }

    public static void init(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (root.contains(ROOT)) {
            CompoundTag existing = root.getCompound(ROOT);
            // 旧存档迁移：斗气（Battle Qi）的字段名从 aura 系列改为 battleQi 系列，数值原样搬过来。
            migrateLegacyBattleQiKeys(existing);
            // 旧存档迁移：技能加成改为实时计算，基础上限单独保存。
            if (!existing.contains("baseMaxMana")) existing.putDouble("baseMaxMana", existing.getDouble("maxMana"));
            if (!existing.contains("baseMaxBattleQi")) existing.putInt("baseMaxBattleQi", existing.getInt("maxBattleQi"));
            // 叠加制：已经被赋过自定义数值的生物额外追加通用数值，不覆盖原值。
            if (!(entity instanceof Player)) MobCombatSystem.stackOntoExisting(entity, existing);
            return;
        }
        CompoundTag t = new CompoundTag();
        if (entity instanceof Player) {
            double affinity = 0.75 + entity.getRandom().nextDouble() * 0.5;
            double initialMana = ManaConfig.PLAYER_INITIAL_MANA.get();
            fill(t, initialMana, initialMana, affinity, 100, 100);
        } else {
            // 原版生物交给生物系统按难度/血量/专配计算；不归它管的生物保留固定基础值。
            MobCombatSystem.initialize(entity, t);
        }
        root.put(ROOT, t);
    }

    static void fill(CompoundTag t, double mana, double maxMana, double affinity, int battleQi, int maxBattleQi) {
        t.putDouble("mana", mana);
        t.putDouble("maxMana", maxMana);
        t.putDouble("baseMaxMana", maxMana);
        t.putDouble("affinity", affinity);
        t.putInt("battleQi", battleQi);
        t.putInt("maxBattleQi", maxBattleQi);
        t.putInt("baseMaxBattleQi", maxBattleQi);
        t.putInt("permanentBattleQiBonus", 0);
        t.putInt("exhaustionTicks", 0);
    }

    /**
     * 旧版本把斗气存成 aura 系列键；进存档时一次性搬成 battleQi 系列，
     * 升级后斗气当前值、上限和绝境凝聚次数都不会丢。
     */
    private static void migrateLegacyBattleQiKeys(CompoundTag t) {
        moveLegacyInt(t, "aura", "battleQi");
        moveLegacyInt(t, "maxAura", "maxBattleQi");
        moveLegacyInt(t, "baseMaxAura", "baseMaxBattleQi");
        moveLegacyInt(t, "permanentAuraBonus", "permanentBattleQiBonus");
        moveLegacyInt(t, "auraBreakthroughs", "battleQiBreakthroughs");
        moveLegacyLong(t, "auraBreakthroughDay", "battleQiBreakthroughDay");
    }

    private static void moveLegacyInt(CompoundTag t, String legacyKey, String newKey) {
        if (t.contains(legacyKey) && !t.contains(newKey)) t.putInt(newKey, t.getInt(legacyKey));
        t.remove(legacyKey);
    }

    private static void moveLegacyLong(CompoundTag t, String legacyKey, String newKey) {
        if (t.contains(legacyKey) && !t.contains(newKey)) t.putLong(newKey, t.getLong(legacyKey));
        t.remove(legacyKey);
    }

    private static CompoundTag tag(LivingEntity entity) {
        init(entity);
        return entity.getPersistentData().getCompound(ROOT);
    }

    public static void setMana(LivingEntity entity, double mana, boolean allowOvercharge) {
        CompoundTag t = tag(entity);
        double previous = t.getDouble("mana");
        double max = get(entity).maxMana();
        double cap = allowOvercharge ? Double.MAX_VALUE : max;
        double clamped = Math.max(0, Math.min(mana, cap));
        t.putDouble("mana", clamped);
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            MagicProgression.checkManaMilestones(player, clamped);
        }
        if (allowOvercharge && clamped > max * ManaConfig.OVERCHARGE_SAFE_RATIO.get()) {
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 2));
        }
        // 魔素被耗尽（烧空或被汲取到 0）：进入「魔力枯竭」，期间无法施法，只能等状态结束再自然恢复。
        if (previous > 0.0 && clamped <= 0.0001 && entity.isAlive()) enterExhaustion(entity);
    }

    public static void restoreMana(LivingEntity entity, double amount, boolean allowOvercharge) {
        Snapshot d = get(entity);
        setMana(entity, d.mana + amount, allowOvercharge);
    }

    /** 恢复魔素但限制在指定上限倍率内；用于可控的法术超量恢复。 */
    public static void restoreManaToRatio(LivingEntity entity, double amount, double maxRatio) {
        Snapshot d = get(entity);
        double cap = d.maxMana() * Math.max(1.0, maxRatio);
        CompoundTag t = tag(entity);
        double value = Math.max(0.0, Math.min(cap, d.mana() + Math.max(0.0, amount)));
        t.putDouble("mana", value);
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            MagicProgression.checkManaMilestones(player, value);
        }
    }

    /** 为有固定设定值的模组生物设置基础魔素与斗气池。 */
    public static void setMobPools(LivingEntity entity, double maxMana, int maxBattleQi) {
        CompoundTag t = tag(entity);
        double mana = Math.max(0.0, Math.min(maxMana, ManaConfig.GLOBAL_MANA_CAP.get()));
        int battleQi = Math.max(0, Math.min(maxBattleQi, ManaConfig.GLOBAL_BATTLE_QI_CAP.get()));
        if (MobCombatSystem.isOwnMob(entity)) {
            t.putDouble("ownIntrinsicMana", mana);
            t.putInt("ownIntrinsicQi", battleQi);
        }
        t.putDouble("baseMaxMana", mana);
        t.putDouble("maxMana", mana);
        t.putDouble("mana", mana);
        t.putInt("baseMaxBattleQi", battleQi);
        t.putInt("maxBattleQi", battleQi);
        t.putInt("battleQi", battleQi);
        if (MobCombatSystem.isOwnMob(entity)) MobCombatSystem.refreshOwnPools(entity, t);
    }

    public static void setMaxMana(Player player, double maxMana) {
        CompoundTag t = tag(player);
        double multiplier = Math.max(0.01, MagicProgression.maxManaMultiplier(player));
        t.putDouble("baseMaxMana", Math.max(1.0, Math.min(maxMana, ManaConfig.GLOBAL_MANA_CAP.get())) / multiplier);
        t.putDouble("maxMana", get(player).maxMana());
        setMana(player, t.getDouble("mana"), true);
    }

    public static void setMaxBattleQi(Player player, int maxBattleQi) {
        CompoundTag t = tag(player);
        int clamped = Math.max(1, Math.min(maxBattleQi, ManaConfig.GLOBAL_BATTLE_QI_CAP.get()));
        double multiplier = Math.max(0.01, MagicProgression.maxBattleQiMultiplier(player));
        t.putInt("baseMaxBattleQi", Math.max(1, (int)Math.round(clamped / multiplier)));
        t.putInt("maxBattleQi", get(player).maxBattleQi());
        t.putInt("permanentBattleQiBonus", Math.max(0, clamped - 100));
        setBattleQi(player, t.getInt("battleQi"));
    }

    /** 永久提高斗气上限（绝境凝聚渠道）；提高多少就补给多少当前斗气。 */
    public static void increaseMaxBattleQi(Player player, int points) {
        if (points <= 0) return;
        CompoundTag t = tag(player);
        int oldBase = t.getInt("baseMaxBattleQi");
        int cap = ManaConfig.GLOBAL_BATTLE_QI_CAP.get();
        int newBase = Math.min(cap, oldBase + points);
        int gained = newBase - oldBase;
        if (gained <= 0) return;
        t.putInt("baseMaxBattleQi", newBase);
        t.putInt("permanentBattleQiBonus", t.getInt("permanentBattleQiBonus") + gained);
        t.putInt("maxBattleQi", get(player).maxBattleQi());
        setBattleQi(player, Math.min(cap, t.getInt("battleQi") + gained));
        if (player instanceof ServerPlayer serverPlayer) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new ManaSyncPayload(get(player).mana(), get(player).maxMana(), get(player).battleQi(),
                            get(player).maxBattleQi(), get(player).exhaustionTicks() > 0,
                            ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
        }
    }

    public static boolean spendMana(LivingEntity entity, double amount) {
        // 作弊开关：关闭魔素消耗时直接放行，不扣魔素也不会进魔力枯竭。
        if (!ManaCheats.manaCostEnabled(entity)) return true;
        double finalAmount = SpellAttributes.reducedManaCost(entity, Math.max(0.0, amount));
        Snapshot d = get(entity);
        if (d.mana >= finalAmount) {
            double remaining = d.mana - finalAmount;
            setMana(entity, remaining, true);
            if (finalAmount > 0.0 && remaining <= 0.0001) enterExhaustion(entity);
            return true;
        }
        setMana(entity, 0, true);
        enterExhaustion(entity);
        return false;
    }

    /**
     * 装备类常驻消耗：按秒从魔素池里扣，不够就扣到 0，但<b>不会</b>触发「魔力枯竭」。
     *
     * <p>专业鉴定眼镜这类「戴着就一直在耗」的饰品，如果走 {@link #spendMana}，
     * 玩家戴着一顶眼镜路过就会被打进枯竭状态，代价和收益完全不成比例。
     * 所以这里直接改数值，只是单纯把魔素用掉。</p>
     */
    public static void drainMana(LivingEntity entity, double amount) {
        if (!ManaCheats.manaCostEnabled(entity) || amount <= 0.0) return;
        CompoundTag t = tag(entity);
        double current = t.getDouble("mana");
        if (current <= 0.0) return;
        t.putDouble("mana", Math.max(0.0, current - amount));
    }

    private static void enterExhaustion(LivingEntity entity) {
        CompoundTag t = tag(entity);
        // 同一次枯竭状态只掷一次成长，不因状态刷新重复触发。
        if (t.getInt("exhaustionTicks") > 0) return;
        t.putInt("exhaustionTicks", 260);
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(GeneratedMod.MANA_EXHAUSTION, 260, 0, false, true));
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            double growth = MagicProgression.rollExhaustionGrowth(player);
            if (growth > 0) {
                increaseBaseMaxMana(player, growth);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dimensional_mana.exhaustion_growth", (int)growth), false);
                MagicProgression.awardAdvancement(player, "exhaustion_growth");
            }
        }
    }

    /**
     * 主动花掉斗气但<b>不</b>触发「斗气溃散」：招架成功时会把斗气正好花到 0，
     * 那是玩家自己选择付的代价，不该按「斗气被打空」处理。
     */
    public static void spendBattleQiQuiet(LivingEntity entity, int amount) {
        if (amount <= 0) return;
        CompoundTag t = tag(entity);
        int current = t.getInt("battleQi");
        t.putInt("battleQi", Math.max(0, current - amount));
    }

    public static void setBattleQi(LivingEntity entity, int battleQi) {
        int clamped = Math.max(0, Math.min(battleQi, ManaConfig.GLOBAL_BATTLE_QI_CAP.get()));
        CompoundTag t = tag(entity);
        int previous = t.getInt("battleQi");
        t.putInt("battleQi", clamped);
        // 一次伤害把斗气打空，或技能消耗把斗气用到 0：进入 4 秒「斗气溃散」。
        if (previous > 0 && clamped == 0 && entity.isAlive()) {
            BattleQiCollapseEffect.apply(entity);
            // 部分生物把「斗气被击破」当作专属技能的触发条件（烈焰新星、空间折跃）。
            MobSkills.onBattleQiBroken(entity);
        }
    }

    /**
     * 凝聚斗气：玩家站着不动时按住 Alt+Z，每 10 刻消耗 1 点魔素换 1 点斗气。
     * 服务端会自己复核“静止”，客户端发来的请求只是节奏提示。
     *
     * <p>「回气」技能只在这里生效：每级让这一次凝聚额外恢复 0.1 点，多恢复的斗气同样按
     * 1 点斗气 = 1 点魔素扣魔素，所以回气加速的是「凝聚速度」，兑换率始终是 1:1。</p>
     */
    public static boolean condenseBattleQi(ServerPlayer player) {
        Snapshot d = get(player);
        if (d.battleQi() >= d.maxBattleQi()) return false;
        if (!player.onGround() || player.getDeltaMovement().horizontalDistanceSqr() > 0.01) return false;
        CompoundTag t = tag(player);
        long now = player.level().getGameTime();
        // 服务端限流：无论客户端怎么连点，都是每 10 刻最多凝聚 1 点。
        if (now < t.getLong("nextCondenseTick")) return false;
        if (!spendMana(player, 1.0)) return false;
        t.putLong("nextCondenseTick", now + 10);
        int condensed = Math.min(d.maxBattleQi(), d.battleQi() + 1);
        // 回气：按等级多攒一点零头，攒满整点就补上；斗气快满时只补到上限为止。
        int extra = condenseExtra(t, player, Math.max(0, d.maxBattleQi() - condensed));
        condensed = Math.min(d.maxBattleQi(), condensed + extra);
        setBattleQi(player, condensed);
        condenseVisuals(player, condensed, d.maxBattleQi());
        return true;
    }

    /**
     * 回气在这一次凝聚里额外补的整点斗气。
     *
     * <p>小数存在玩家身上，下次凝聚接着攒；额外斗气同样 1:1 扣魔素，
     * 魔素不够付的这一部分直接作废，不会欠着以后补。</p>
     */
    private static int condenseExtra(CompoundTag t, ServerPlayer player, int room) {
        double bonus = QiProgression.condenseBonusPerPoint(player);
        if (bonus <= 0.0 || room <= 0) return 0;
        double carry = t.getDouble("qiCondenseCarry") + bonus;
        double fraction = Math.floor(carry);
        int whole = Math.min((int)fraction, room);
        t.putDouble("qiCondenseCarry", carry - fraction);
        if (whole <= 0 || !ManaCheats.hasMana(player, whole)) return 0;
        drainMana(player, whole);
        return whole;
    }

    /**
     * 凝聚斗气的聚气表现：身体周围一圈金光向中心收束，配合音调随斗气比例升高的清亮钟声。
     * 粒子每凝聚 1 点（10 刻）撒一次，钟声每两秒一次，持续按住时听感连贯但不会吵。
     */
    private static void condenseVisuals(ServerPlayer player, int battleQi, int maxBattleQi) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        double centerX = player.getX();
        double centerY = player.getY() + 1.0;
        double centerZ = player.getZ();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                centerX, centerY, centerZ, 6, 0.55, 0.8, 0.55, 0.015);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                centerX, centerY + 0.35, centerZ, 10, 0.85, 0.6, 0.85, 0.4);
        // 脚下再铺一圈向内收的斗气环。
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                centerX, player.getY() + 0.15, centerZ, 8, 0.7, 0.05, 0.7, 0.02);
        // 每 20 刻响一次，音调随斗气比例升高，越接近满槽越高。
        if ((level.getGameTime() / 10L) % 2L != 0L) return;
        float ratio = Math.max(0.0F, Math.min(1.0F, battleQi / (float)Math.max(1, maxBattleQi)));
        level.playSound(null, centerX, player.getY(), centerZ,
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.1F + ratio * 0.6F);
    }

    public static void setExhaustion(LivingEntity entity, int ticks) {
        tag(entity).putInt("exhaustionTicks", Math.max(0, ticks));
    }

    public static void increaseMaxMana(Player player, double points) {
        increaseBaseMaxMana(player, points);
    }

    public static void increaseBaseMaxMana(Player player, double points) {
        CompoundTag t = tag(player);
        double oldMax = t.getDouble("baseMaxMana");
        double newMax = Math.min(ManaConfig.GLOBAL_MANA_CAP.get(), oldMax + Math.max(0.0, points));
        t.putDouble("baseMaxMana", newMax);
        t.putDouble("maxMana", get(player).maxMana());
        if (oldMax >= 256 || newMax >= 256) {
            int attempts = Math.max(1, (int)Math.floor(points));
            int bonus = t.getInt("permanentBattleQiBonus");
            for (int i = 0; i < attempts && bonus < 1000; i++) {
                if (player.getRandom().nextDouble() < 0.01) bonus++;
            }
            t.putInt("permanentBattleQiBonus", bonus);
            t.putInt("baseMaxBattleQi", 100 + bonus);
            t.putInt("maxBattleQi", get(player).maxBattleQi());
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            MagicProgression.checkManaMilestones(serverPlayer, get(player).maxMana());
        }
    }

    public static void copy(Player from, Player to) {
        if (from.getPersistentData().contains(ROOT)) {
            to.getPersistentData().put(ROOT, from.getPersistentData().getCompound(ROOT).copy());
        }
    }

    private ManaData() {}
}
