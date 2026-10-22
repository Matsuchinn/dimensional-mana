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

    public record Snapshot(double mana, double maxMana, double affinity, int aura, int maxAura, int permanentAuraBonus, int exhaustionTicks) {}

    public static Snapshot get(LivingEntity entity) {
        init(entity);
        CompoundTag t = entity.getPersistentData().getCompound(ROOT);
        double maxMana = t.getDouble("baseMaxMana");
        int maxAura = t.getInt("baseMaxAura");
        if (entity instanceof Player player) {
            int amulets = MagicAccessoryItem.equippedCount(player, "magic_amulet", "charm");
            double casketMultiplier = MagicTrinketItem.isEquipped(player, "ancient_casket") ? 1.16 : 1.0;
            maxMana *= MagicProgression.maxManaMultiplier(player) * (1.0 + amulets * 0.15) * casketMultiplier;
            // 披风的魔素上限加成是独立乘区：伤害偏转 +8%、魔法大师 +25%，各自再乘一次。
            maxMana *= MagicEquipment.maxManaMultiplier(player);
            maxAura = (int)Math.round(maxAura * MagicProgression.maxAuraMultiplier(player) * casketMultiplier);
        }
        maxMana = Math.min(maxMana, ManaConfig.GLOBAL_MANA_CAP.get());
        maxAura = Math.min(maxAura, ManaConfig.GLOBAL_AURA_CAP.get());
        return new Snapshot(t.getDouble("mana"), maxMana, t.getDouble("affinity"),
                t.getInt("aura"), maxAura, t.getInt("permanentAuraBonus"), t.getInt("exhaustionTicks"));
    }

    public static void init(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (root.contains(ROOT)) {
            CompoundTag existing = root.getCompound(ROOT);
            // 旧存档迁移：技能加成改为实时计算，基础上限单独保存。
            if (!existing.contains("baseMaxMana")) existing.putDouble("baseMaxMana", existing.getDouble("maxMana"));
            if (!existing.contains("baseMaxAura")) existing.putInt("baseMaxAura", existing.getInt("maxAura"));
            return;
        }
        CompoundTag t = new CompoundTag();
        if (entity instanceof WitherBoss) {
            fill(t, 66_666, 66_666, 1.0, 6_666, 6_666);
        } else if (entity instanceof EnderDragon) {
            fill(t, 500_000, 500_000, 1.0, 2_026, 2_026);
        } else if (entity instanceof Player) {
            double affinity = 0.75 + entity.getRandom().nextDouble() * 0.5;
            double initialMana = ManaConfig.PLAYER_INITIAL_MANA.get();
            fill(t, initialMana, initialMana, affinity, 100, 100);
        } else {
            int aura = initialMobAura(entity);
            fill(t, 100, 100, 1.0, aura, aura);
        }
        root.put(ROOT, t);
    }

    private static void fill(CompoundTag t, double mana, double maxMana, double affinity, int aura, int maxAura) {
        t.putDouble("mana", mana);
        t.putDouble("maxMana", maxMana);
        t.putDouble("baseMaxMana", maxMana);
        t.putDouble("affinity", affinity);
        t.putInt("aura", aura);
        t.putInt("maxAura", maxAura);
        t.putInt("baseMaxAura", maxAura);
        t.putInt("permanentAuraBonus", 0);
        t.putInt("exhaustionTicks", 0);
    }

    private static int initialMobAura(LivingEntity entity) {
        String dimension = entity.level().dimension().location().toString();
        String entityId = entity.getType().builtInRegistryHolder().key().location().toString();
        if (ManaConfig.ENTITY_BLACKLIST.get().contains(entityId)) return 0;
        var dimensions = ManaConfig.DIMENSION_WHITELIST.get();
        if (!dimensions.isEmpty() && !dimensions.contains(dimension)) return 0;
        if (dimension.equals("minecraft:the_end")) return random(entity, 500, 2000);
        if (dimension.equals("minecraft:the_nether")) return random(entity, 50, 500);
        if (entity.getType().getCategory().isFriendly()) return 0;
        return random(entity, 1, 50);
    }

    private static int random(LivingEntity e, int min, int max) {
        return min + e.getRandom().nextInt(max - min + 1);
    }

    private static CompoundTag tag(LivingEntity entity) {
        init(entity);
        return entity.getPersistentData().getCompound(ROOT);
    }

    public static void setMana(LivingEntity entity, double mana, boolean allowOvercharge) {
        CompoundTag t = tag(entity);
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
    public static void setMobPools(LivingEntity entity, double maxMana, int maxAura) {
        CompoundTag t = tag(entity);
        double mana = Math.max(1.0, Math.min(maxMana, ManaConfig.GLOBAL_MANA_CAP.get()));
        int aura = Math.max(1, Math.min(maxAura, ManaConfig.GLOBAL_AURA_CAP.get()));
        t.putDouble("baseMaxMana", mana);
        t.putDouble("maxMana", mana);
        t.putDouble("mana", mana);
        t.putInt("baseMaxAura", aura);
        t.putInt("maxAura", aura);
        t.putInt("aura", aura);
    }

    public static void setMaxMana(Player player, double maxMana) {
        CompoundTag t = tag(player);
        double multiplier = Math.max(0.01, MagicProgression.maxManaMultiplier(player));
        t.putDouble("baseMaxMana", Math.max(1.0, Math.min(maxMana, ManaConfig.GLOBAL_MANA_CAP.get())) / multiplier);
        t.putDouble("maxMana", get(player).maxMana());
        setMana(player, t.getDouble("mana"), true);
    }

    public static void setMaxAura(Player player, int maxAura) {
        CompoundTag t = tag(player);
        int clamped = Math.max(1, Math.min(maxAura, ManaConfig.GLOBAL_AURA_CAP.get()));
        double multiplier = Math.max(0.01, MagicProgression.maxAuraMultiplier(player));
        t.putInt("baseMaxAura", Math.max(1, (int)Math.round(clamped / multiplier)));
        t.putInt("maxAura", get(player).maxAura());
        t.putInt("permanentAuraBonus", Math.max(0, clamped - 100));
        setAura(player, t.getInt("aura"));
    }

    /** 永久提高斗气上限（绝境凝聚渠道）；提高多少就补给多少当前斗气。 */
    public static void increaseMaxAura(Player player, int points) {
        if (points <= 0) return;
        CompoundTag t = tag(player);
        int oldBase = t.getInt("baseMaxAura");
        int cap = ManaConfig.GLOBAL_AURA_CAP.get();
        int newBase = Math.min(cap, oldBase + points);
        int gained = newBase - oldBase;
        if (gained <= 0) return;
        t.putInt("baseMaxAura", newBase);
        t.putInt("permanentAuraBonus", t.getInt("permanentAuraBonus") + gained);
        t.putInt("maxAura", get(player).maxAura());
        setAura(player, Math.min(cap, t.getInt("aura") + gained));
        if (player instanceof ServerPlayer serverPlayer) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new ManaSyncPayload(get(player).mana(), get(player).maxMana(), get(player).aura(),
                            get(player).maxAura(), get(player).exhaustionTicks() > 0,
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

    public static void setAura(LivingEntity entity, int aura) {
        tag(entity).putInt("aura", Math.max(0, Math.min(aura, ManaConfig.GLOBAL_AURA_CAP.get())));
    }

    /**
     * 凝聚斗气：玩家站着不动时按住 Alt+Z，每 10 刻消耗 1 点魔素换 1 点斗气。
     * 服务端会自己复核“静止”，客户端发来的请求只是节奏提示。
     */
    public static boolean condenseAura(ServerPlayer player) {
        Snapshot d = get(player);
        if (d.aura() >= d.maxAura()) return false;
        if (!player.onGround() || player.getDeltaMovement().horizontalDistanceSqr() > 0.01) return false;
        CompoundTag t = tag(player);
        long now = player.level().getGameTime();
        // 服务端限流：无论客户端怎么连点，都是每 10 刻最多凝聚 1 点。
        if (now < t.getLong("nextCondenseTick")) return false;
        if (!spendMana(player, 1.0)) return false;
        t.putLong("nextCondenseTick", now + 10);
        setAura(player, Math.min(d.maxAura(), d.aura() + 1));
        return true;
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
            int bonus = t.getInt("permanentAuraBonus");
            for (int i = 0; i < attempts && bonus < 1000; i++) {
                if (player.getRandom().nextDouble() < 0.01) bonus++;
            }
            t.putInt("permanentAuraBonus", bonus);
            t.putInt("baseMaxAura", 100 + bonus);
            t.putInt("maxAura", get(player).maxAura());
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
