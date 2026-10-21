package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 魔力通透·共鸣核心的充能、元素共鸣与脱战衰减。
 *
 * <p>所有数值都写在玩家自己的持久化数据里，因此死亡、换维度、退出重进都会保留；
 * 客户端只收到一份只读快照用于 HUD 显示。</p>
 *
 * <p>充能规则：造成对应元素的伤害就充能，这一下暴击 +2.0、不暴击 +1.0，最快每 5 刻一次；
 * 切换元素立即清零。
 * 每点充能提供 0.35% 该元素伤害（独立乘区），上限 100 点即 +35%。</p>
 *
 * <p>元素共鸣期间玩家获得“魔力通透”：法术伤害 +15%（独立乘区）、法术暴击率 +10%、
 * 法术暴击伤害 +35%。这三项都由本类提供、由 {@link SpellAttributes} 与 {@link SpellDamage}
 * 在结算时读取，效果图标只负责显示。</p>
 */
public final class ResonanceCore {
    public static final String ITEM_ID = "resonance_core";
    public static final String SLOT = MagicEquipment.MAGIC_ACCESSORY_SLOT;

    public static final double MAX_CHARGE = 100.0;
    public static final double PER_CHARGE_BONUS = 0.0035;
    /** 满充后再次施展对应元素技能时消耗的充能比例。 */
    public static final double RESONANCE_COST_RATIO = 0.65;
    /** 元素共鸣持续 30 秒，每 1.5 秒释放一次冲击波。 */
    public static final int RESONANCE_TICKS = 600;
    public static final int PULSE_INTERVAL = 30;
    public static final double PULSE_RADIUS = 8.0;
    public static final float PULSE_DAMAGE = 50.0F;
    /** 每次充能的冷却，单位游戏刻：5 刻（0.25 秒）最多充能一次。 */
    public static final int CHARGE_COOLDOWN = 5;
    /** 对应元素伤害暴击时一次充入的量。 */
    public static final double CRIT_CHARGE = 2.0;
    /** 对应元素伤害没暴击时一次充入的量。 */
    public static final double NORMAL_CHARGE = 1.0;
    /** 受伤掉充能单独使用的冷却，避免和充能冷却互相顶掉。 */
    public static final int DAMAGE_LOSS_COOLDOWN = 8;
    /** 元素共鸣期间的“魔力通透”：法术伤害独立乘区。 */
    public static final double TRANSPARENCY_DAMAGE = 1.15;
    /** 元素共鸣期间的“魔力通透”：法术暴击率加成。 */
    public static final double TRANSPARENCY_CRIT_CHANCE = 0.10;
    /** 元素共鸣期间的“魔力通透”：法术暴击伤害加成。 */
    public static final double TRANSPARENCY_CRIT_DAMAGE = 0.35;
    /** 60 秒内没有造成任何伤害就开始脱战衰减，每秒掉 4 点。 */
    public static final int OUT_OF_COMBAT_TICKS = 1_200;
    public static final double DECAY_PER_SECOND = 4.0;
    /** 充能 ≥50 时受到任何伤害掉 1.5 充能。 */
    public static final double DAMAGE_LOSS = 1.5;
    public static final double DAMAGE_LOSS_THRESHOLD = 50.0;

    private static final String KEY_CHARGE = "DimensionalManaCoreCharge";
    private static final String KEY_ELEMENT = "DimensionalManaCoreElement";
    private static final String KEY_LAST_CHARGE = "DimensionalManaCoreLastCharge";
    private static final String KEY_LAST_DRAIN = "DimensionalManaCoreLastDrain";
    private static final String KEY_LAST_HIT = "DimensionalManaCoreLastHit";
    private static final String KEY_RESONANCE_END = "DimensionalManaCoreResonanceEnd";
    private static final String KEY_RESONANCE_NEXT = "DimensionalManaCoreResonanceNext";
    private static final String KEY_READY = "DimensionalManaCoreReady";

    private ResonanceCore() { }

    public static boolean equipped(LivingEntity entity) {
        return MagicEquipment.equippedCount(entity, ITEM_ID, SLOT) > 0;
    }

    /** 当前充能对应的元素 id；未装备或没有充能记录时返回空串。 */
    public static String element(LivingEntity entity) {
        return entity == null ? "" : entity.getPersistentData().getString(KEY_ELEMENT);
    }

    public static double charge(LivingEntity entity) {
        return entity == null ? 0.0 : entity.getPersistentData().getDouble(KEY_CHARGE);
    }

    public static int resonanceTicksLeft(LivingEntity entity) {
        if (entity == null) return 0;
        long left = entity.getPersistentData().getLong(KEY_RESONANCE_END) - entity.level().getGameTime();
        return (int) Math.max(0, left);
    }

    public static boolean inResonance(LivingEntity entity) {
        return resonanceTicksLeft(entity) > 0;
    }

    /** 对应元素的伤害乘区（独立乘算）；其他元素与未装备时都是 1.0。 */
    public static double spellDamageMultiplier(LivingEntity caster, String elementId) {
        if (caster == null || elementId == null || !equipped(caster)) return 1.0;
        CompoundTag data = caster.getPersistentData();
        if (!elementId.equals(data.getString(KEY_ELEMENT))) return 1.0;
        double charge = Math.min(MAX_CHARGE, Math.max(0.0, data.getDouble(KEY_CHARGE)));
        return charge <= 0.0 ? 1.0 : 1.0 + charge * PER_CHARGE_BONUS;
    }

    /** 元素共鸣期间“魔力通透”的法术伤害乘区（独立乘区，对全部法术生效）；否则 1.0。 */
    public static double transparencyDamageMultiplier(LivingEntity caster) {
        return transparencyActive(caster) ? TRANSPARENCY_DAMAGE : 1.0;
    }

    /** 元素共鸣期间“魔力通透”的法术暴击率加成；否则 0。 */
    public static double transparencyCritChance(LivingEntity caster) {
        return transparencyActive(caster) ? TRANSPARENCY_CRIT_CHANCE : 0.0;
    }

    /** 元素共鸣期间“魔力通透”的法术暴击伤害加成；否则 0。 */
    public static double transparencyCritDamage(LivingEntity caster) {
        return transparencyActive(caster) ? TRANSPARENCY_CRIT_DAMAGE : 0.0;
    }

    /** 魔力通透是否生效：装备共鸣核心且处于元素共鸣期间。 */
    public static boolean transparencyActive(LivingEntity caster) {
        return caster != null && equipped(caster) && inResonance(caster);
    }

    /**
     * 一次元素法术真正落地后调用：累积充能，并在满充后再次施法时触发元素共鸣。
     *
     * @param critical 这一下是否法术暴击；暴击 +2.0，不暴击 +1.0
     */
    public static void onElementDamage(LivingEntity caster, String elementId, boolean critical) {
        if (!(caster instanceof ServerPlayer player) || elementId == null || elementId.isEmpty()) return;
        if (player.level().isClientSide || !equipped(player)) return;
        long now = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();

        // 切换元素：当前充能立即清零，新元素从 0 重新开始。
        if (!elementId.equals(data.getString(KEY_ELEMENT))) {
            data.putString(KEY_ELEMENT, elementId);
            data.putDouble(KEY_CHARGE, 0.0);
            data.putBoolean(KEY_READY, false);
        }
        data.putLong(KEY_LAST_HIT, now);

        // 共鸣期间无法充能。
        if (now < data.getLong(KEY_RESONANCE_END)) {
            sync(player);
            return;
        }

        if (data.getBoolean(KEY_READY)) {
            // 满充之后“再次施展”对应元素技能才触发共鸣，这一次本身不再充能。
            double remaining = Math.min(MAX_CHARGE, data.getDouble(KEY_CHARGE)) * (1.0 - RESONANCE_COST_RATIO);
            data.putDouble(KEY_CHARGE, Math.max(0.0, remaining));
            data.putBoolean(KEY_READY, false);
            data.putLong(KEY_RESONANCE_END, now + RESONANCE_TICKS);
            data.putLong(KEY_RESONANCE_NEXT, now);
            applyTransparency(player);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.resonance_core.resonance",
                    elementName(elementId)).withStyle(ChatFormatting.AQUA), true);
            if (player.level() instanceof ServerLevel level) {
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.25F);
            }
            sync(player);
            return;
        }

        if (now - data.getLong(KEY_LAST_CHARGE) < CHARGE_COOLDOWN) {
            sync(player);
            return;
        }
        data.putLong(KEY_LAST_CHARGE, now);
        // 暴击充得多、不暴击也照常充，只是慢一半。
        double gain = critical ? CRIT_CHARGE : NORMAL_CHARGE;
        double charge = Math.min(MAX_CHARGE, Math.max(0.0, data.getDouble(KEY_CHARGE)) + gain);
        data.putDouble(KEY_CHARGE, charge);
        if (charge >= MAX_CHARGE) {
            data.putBoolean(KEY_READY, true);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.resonance_core.full")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
        sync(player);
    }

    /** 受到任何伤害（环境、摔落、DOT 都算）时掉充能。 */
    public static void onDamaged(LivingEntity victim) {
        if (!(victim instanceof ServerPlayer player) || player.level().isClientSide || !equipped(player)) return;
        CompoundTag data = player.getPersistentData();
        double charge = data.getDouble(KEY_CHARGE);
        if (charge < DAMAGE_LOSS_THRESHOLD) return;
        long now = player.level().getGameTime();
        if (now - data.getLong(KEY_LAST_DRAIN) < DAMAGE_LOSS_COOLDOWN) return;
        data.putLong(KEY_LAST_DRAIN, now);
        charge = Math.max(0.0, charge - DAMAGE_LOSS);
        data.putDouble(KEY_CHARGE, charge);
        if (charge < MAX_CHARGE) data.putBoolean(KEY_READY, false);
        sync(player);
    }

    /** 每个服务端刻：脱战衰减、共鸣脉冲与右下角 HUD 的轻量同步。 */
    public static void tick(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (!equipped(player)) {
            boolean hasState = data.getDouble(KEY_CHARGE) != 0.0 || data.getLong(KEY_RESONANCE_END) != 0L
                    || !data.getString(KEY_ELEMENT).isEmpty() || data.getBoolean(KEY_READY);
            if (hasState) reset(player);
            // 没装备时也定期回一次“未装备”，避免客户端残留上一件饰品或上一个存档的 HUD。
            else if (now % 20L == 0L) sync(player);
            return;
        }

        // 脱战 60 秒后每秒掉 4 点（上限的 4%）。
        if (data.getDouble(KEY_CHARGE) > 0.0 && now - data.getLong(KEY_LAST_HIT) > OUT_OF_COMBAT_TICKS
                && now - data.getLong(KEY_LAST_DRAIN) >= 20) {
            data.putLong(KEY_LAST_DRAIN, now);
            double charge = Math.max(0.0, data.getDouble(KEY_CHARGE) - DECAY_PER_SECOND);
            data.putDouble(KEY_CHARGE, charge);
            if (charge < MAX_CHARGE) data.putBoolean(KEY_READY, false);
        }

        long end = data.getLong(KEY_RESONANCE_END);
        if (end > now) {
            long next = data.getLong(KEY_RESONANCE_NEXT);
            if (now >= next) {
                pulse(player, data.getString(KEY_ELEMENT));
                data.putLong(KEY_RESONANCE_NEXT, now + PULSE_INTERVAL);
            }
            // “魔力通透”图标随共鸣持续；死亡、喝牛奶等原因被清掉时下一瞬补回来，
            // 真正的数值判定始终看共鸣计时，不会因为图标丢失而少加成。
            applyTransparency(player);
        } else if (end != 0L) {
            data.putLong(KEY_RESONANCE_END, 0L);
            data.putLong(KEY_RESONANCE_NEXT, 0L);
            clearTransparency(player);
        }

        if (now % 5L == 0L) sync(player);
    }

    /** 元素共鸣冲击波：半径 8 格、伤害 50、附着对应元素。 */
    private static void pulse(ServerPlayer player, String elementId) {
        if (!(player.level() instanceof ServerLevel level) || elementId.isEmpty()) return;
        ElementType element = ElementType.byId(elementId);
        int color = element == null ? 0xC85BFF : (element.color() & 0xFFFFFF);
        Vec3 center = player.position();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(PULSE_RADIUS, 4.0, PULSE_RADIUS),
                target -> target != player && target.isAlive()
                        && target.distanceToSqr(center) <= PULSE_RADIUS * PULSE_RADIUS);
        for (LivingEntity target : targets) {
            NewSpellSystem.damage(player, target, PULSE_DAMAGE, element);
        }
        level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(
                        ((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F),
                        1.6F),
                center.x, center.y + 0.6, center.z, 90, PULSE_RADIUS * 0.55, 0.7, PULSE_RADIUS * 0.55, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.PLAYERS, 1.0F, 0.85F);
    }

    /**
     * 把“魔力通透”图标补到玩家身上。图标只用于显示，数值加成由共鸣计时判定；
     * 已有足够时长的图标不会反复刷新，避免每刻发一次效果包。
     */
    private static void applyTransparency(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel)) return;
        int remaining = resonanceTicksLeft(player);
        if (remaining <= 0) return;
        net.minecraft.world.effect.MobEffectInstance current =
                player.getEffect(NewSpellContent.MANA_TRANSPARENCY);
        // 图标时长只允许“落后”一小段（死亡、牛奶等情况才会发生），避免每刻刷新效果包。
        if (current != null && current.getDuration() >= remaining - 20) return;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                NewSpellContent.MANA_TRANSPARENCY, remaining, 0, true, false, true));
    }

    /** 共鸣结束或卸下核心时清掉“魔力通透”图标。 */
    private static void clearTransparency(ServerPlayer player) {
        if (player.hasEffect(NewSpellContent.MANA_TRANSPARENCY))
            player.removeEffect(NewSpellContent.MANA_TRANSPARENCY);
    }

    public static void reset(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putDouble(KEY_CHARGE, 0.0);
        data.putString(KEY_ELEMENT, "");
        data.putLong(KEY_LAST_CHARGE, 0L);
        data.putLong(KEY_LAST_DRAIN, 0L);
        data.putLong(KEY_LAST_HIT, 0L);
        data.putLong(KEY_RESONANCE_END, 0L);
        data.putLong(KEY_RESONANCE_NEXT, 0L);
        data.putBoolean(KEY_READY, false);
        clearTransparency(player);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        double charge = Math.max(0.0, Math.min(MAX_CHARGE, data.getDouble(KEY_CHARGE)));
        PacketDistributor.sendToPlayer(player, new ResonanceCoreSyncPayload(
                charge,
                data.getString(KEY_ELEMENT),
                resonanceTicksLeft(player),
                data.getBoolean(KEY_READY),
                equipped(player)));
    }

    public static Component elementName(String elementId) {
        if (elementId == null || elementId.isEmpty()) return Component.translatable("gui.dimensional_mana.medium.arcane");
        ElementType element = ElementType.byId(elementId);
        return element == null ? Component.translatable("gui.dimensional_mana.medium.arcane")
                : Component.translatable(element.translationKey());
    }

    /** 元素 id 对应的 HUD 颜色。 */
    public static int elementColor(String elementId) {
        ElementType element = elementId == null || elementId.isEmpty() ? null : ElementType.byId(elementId);
        return element == null ? 0xFFC85BFF : 0xFF000000 | (element.color() & 0xFFFFFF);
    }
}
