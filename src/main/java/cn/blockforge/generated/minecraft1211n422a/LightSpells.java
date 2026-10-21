package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 光系追加法术：光之庇护与曜光裂界。
 *
 * <p>光之庇护（中级·增益）：读条三秒后，施法者自己与周围六格内的友方同时获得光的庇护，
 * 持续 40 秒。庇护期间目标发光、受到的伤害减少 22%，每秒回复 2 点生命；
 * 被毒属性攻击时立刻清除中毒，并把 15 点光属性伤害弹回攻击者。</p>
 *
 * <p>曜光裂界（高级·伤害）：前摇长达十五秒，其中后 60%（九秒）无敌，整段前摇移速减半，
 * 身前悬着一颗方块大小的光球、身上缠着多重法阵。前摇期间按住 Alt + 左键可以额外投入
 * 190 魔素蓄力一次，最多六层，每一层都会让法阵向外延展并独立旋转。
 * 光球爆裂时半径 16 格内的敌人受到 100 点光属性伤害并附着光蚀Ⅰ；
 * 每层蓄力让半径 +2 格、伤害独立乘算 ×1.33、光蚀等级 +1、光蚀持续 +2 秒。</p>
 *
 * <p>光蚀（最高七级）：每 7 刻造成 1 点光属性伤害并附加光附着，每提升一级间隔 -1 刻、伤害 +1，
 * 间隔最低 5 刻。多次命中同一目标时等级取最高、持续刷新，不叠加伤害。</p>
 */
public final class LightSpells {
    // ───────────────────────── 光之庇护 ─────────────────────────
    private static final int SHELTER_TICKS = 800;
    private static final double SHELTER_RADIUS = 6.0;
    private static final int SHELTER_HEAL_INTERVAL = 20;
    private static final float SHELTER_HEAL = 2.0F;
    /** 受到伤害 -22%：乘算 0.78。 */
    private static final float SHELTER_DAMAGE_TAKEN = 0.78F;
    private static final float SHELTER_REFLECT = 15.0F;
    private static final int SHELTER_VISUAL_REFRESH = 40;
    private static final int SHELTER_VISUAL_TICKS = 60;

    // ───────────────────────── 曜光裂界 ─────────────────────────
    /** 蓄力层数上限。 */
    public static final int RIFT_MAX_CHARGE = 6;
    private static final double RIFT_CHARGE_COST = 190.0;
    private static final float RIFT_BASE_DAMAGE = 100.0F;
    private static final double RIFT_BASE_RADIUS = 16.0;
    private static final double RIFT_RADIUS_PER_CHARGE = 2.0;
    /** 每层伤害独立乘算 ×1.33：六层为 1.33^6 ≈ 5.56 倍。 */
    private static final float RIFT_DAMAGE_MULTIPLIER = 1.33F;
    private static final int RIFT_BURST_VISUAL_TICKS = 40;
    private static final int RIFT_FLASH_TICKS = 16;
    /** 后 60% 的前摇无敌；前摇长度不受蓄力影响。 */
    private static final double RIFT_INVULNERABLE_WINDOW = 0.60;
    private static final double RIFT_CAST_SLOW = -0.5;
    private static final int RIFT_ORB_DISTANCE = 2;
    /** 蓄力法阵每 40 刻刷新一包，单包留 16 秒寿命足够覆盖整段前摇。 */
    private static final int RIFT_CHARGE_VISUAL_TICKS = 320;

    // ───────────────────────── 光蚀 ─────────────────────────
    public static final int ECLIPSE_MAX_LEVEL = 7;
    private static final int ECLIPSE_BASE_INTERVAL = 7;
    private static final int ECLIPSE_MIN_INTERVAL = 5;
    private static final int ECLIPSE_BASE_TICKS = 60;
    private static final int ECLIPSE_TICKS_PER_LEVEL = 40;
    private static final String ECLIPSE_CASTER = "DimensionalManaLightEclipseCaster";
    /** 强制状态：到期刻与等级记在实体自身数据里，效果被外力抹掉时按它原样补回。 */
    private static final String ECLIPSE_UNTIL = "DimensionalManaLightEclipseUntil";
    private static final String ECLIPSE_FORCED_LEVEL = "DimensionalManaLightEclipseForcedLevel";

    private static final Map<UUID, Integer> CHARGES = new HashMap<>();
    /** 当前被庇护的生物与它所在的世界、到期刻；光环每 40 刻按它刷新一次。 */
    private static final Map<UUID, Shelter> SHELTERS = new HashMap<>();
    private static final Set<UUID> RIFT_CASTING = new HashSet<>();
    private static final Map<UUID, Long> SHELTER_NOTICE = new HashMap<>();
    private static final Map<UUID, Long> CHARGE_NOTICE = new HashMap<>();
    private static final List<PendingReflect> REFLECTS = new ArrayList<>();
    private static final ResourceLocation CAST_SLOW = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "radiant_rift_slow");

    /** 庇护反伤延后一刻结算，避免在别人的受击事件里嵌套 hurt。 */
    private record PendingReflect(ServerLevel level, UUID caster, UUID target, long at) { }
    private record Shelter(ServerLevel level, long until) { }

    // ───────────────────────── 光之庇护 ─────────────────────────

    /** 读条结束：自己与周围六格内的友方一起获得光的庇护。 */
    public static void beginLightShelter(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> sheltered = new ArrayList<>();
        sheltered.add(player);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SHELTER_RADIUS), entity -> friendly(player, entity))) {
            if (other != player) sheltered.add(other);
        }
        for (LivingEntity target : sheltered) {
            applyShelter(target);
            SpellVisualPayload.send(target, SpellDefinition.LIGHT_SHELTER, SpellVisualPayload.LIGHT_SHELTER,
                    target.getId(), target.position(), target.position(), SHELTER_VISUAL_TICKS);
        }
        player.displayClientMessage(Component.translatable("message.dimensional_mana.light_shelter_cast",
                sheltered.size(), SHELTER_TICKS / 20), true);
        Vec3 at = player.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 46, 1.4, 1.8, 1.4, 0.06);
        level.sendParticles(ParticleTypes.GLOW, at.x, at.y, at.z, 30, 1.6, 1.9, 1.6, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.55F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.60F);
        SpellSyncPayload.send(player);
    }

    private static void applyShelter(LivingEntity target) {
        target.addEffect(new MobEffectInstance(NewSpellContent.LIGHT_SHELTER, SHELTER_TICKS, 0, false, true, true));
        // 发光让庇护对象在黑暗里也一眼可见；时长与庇护同步，到期一起消失。
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, SHELTER_TICKS, 0, false, false, true));
        if (target.level() instanceof ServerLevel level)
            SHELTERS.put(target.getUUID(), new Shelter(level, level.getGameTime() + SHELTER_TICKS));
    }

    /** 友方判定：自己、其他玩家、非敌对生物与自己的召唤物都能吃到庇护。 */
    private static boolean friendly(LivingEntity owner, LivingEntity target) {
        if (target == null || !target.isAlive() || target.isSpectator()) return false;
        if (target == owner) return true;
        if (target instanceof Player player) return !player.isSpectator() && !NewSpellSystem.enemy(owner, target);
        if (target instanceof Enemy) return false;
        if (target instanceof SpellSummon summon) return owner.getUUID().equals(summon.ownerId());
        if (target instanceof TamableAnimal tame && tame.getOwnerUUID() != null) return true;
        return !NewSpellSystem.enemy(owner, target);
    }

    /** 庇护的减伤：在 {@link NewSpellSystem#incoming} 里与其它减伤一起乘算。 */
    public static float damageTakenMultiplier(LivingEntity victim) {
        return victim.hasEffect(NewSpellContent.LIGHT_SHELTER) ? SHELTER_DAMAGE_TAKEN : 1.0F;
    }

    /** 提前结束庇护（下线、死亡、再次施法时由清理流程调用）。 */
    public static void clearShelter(LivingEntity target) {
        SHELTERS.remove(target.getUUID());
        if (!target.hasEffect(NewSpellContent.LIGHT_SHELTER)) return;
        target.removeEffect(NewSpellContent.LIGHT_SHELTER);
        target.removeEffect(MobEffects.GLOWING);
        SpellVisualPayload.send(target, SpellDefinition.LIGHT_SHELTER, SpellVisualPayload.LIGHT_SHELTER_END,
                target.getId(), target.position(), target.position(), 1);
    }

    /**
     * 被攻击时的庇护结算：挨了毒属性攻击就清掉中毒，并把 15 点光伤弹回去。
     *
     * <p>“毒属性攻击”按本模组的元素语义判定：攻击者身上带着毒附着，或者攻击者自己正在中毒
     * （蜘蛛、洞穴蜘蛛、女巫一类）；原版毒伤害本身也认作毒属性，但那种伤害没有攻击者，只清毒不反伤。</p>
     */
    public static void onIncomingDamage(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide || !victim.hasEffect(NewSpellContent.LIGHT_SHELTER)) return;
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        LivingEntity attacker = source != null && source.getEntity() instanceof LivingEntity living ? living : null;
        if (!poisonAttack(victim, source, attacker)) return;
        clearPoison(victim);
        if (attacker == null || attacker == victim || !(victim.level() instanceof ServerLevel level)) return;
        REFLECTS.add(new PendingReflect(level, victim.getUUID(), attacker.getUUID(), level.getGameTime() + 1));
        Vec3 at = victim.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 18, 0.7, 0.9, 0.7, 0.10);
        level.playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.85F);
        if (victim instanceof ServerPlayer player) notifyShelter(player);
    }

    private static boolean poisonAttack(LivingEntity victim, DamageSource source, LivingEntity attacker) {
        if (attacker != null && (attacker.hasEffect(MobEffects.POISON) || poisonAttached(attacker))) return true;
        if (source == null) return false;
        String message = source.getMsgId();
        // 本模组自己的法术伤害已按元素细分，这里只认原版的毒类伤害来源；
        // 否则毒系法术的每一次持续跳伤都会触发清毒与反伤，等于把庇护变成了毒伤收割机。
        return message != null && message.contains("poison") && !SpellDamage.isSpell(source);
    }

    private static boolean poisonAttached(LivingEntity entity) {
        return entity.hasEffect(GeneratedMod.elementEffect(ElementType.POISON));
    }

    private static void clearPoison(LivingEntity victim) {
        victim.removeEffect(MobEffects.POISON);
        victim.removeEffect(GeneratedMod.elementEffect(ElementType.POISON));
    }

    private static void notifyShelter(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = SHELTER_NOTICE.get(player.getUUID());
        if (last != null && now - last < 10) return;
        SHELTER_NOTICE.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.light_shelter_purify",
                (int)SHELTER_REFLECT), true);
    }

    /** 反伤结算：延后一刻执行，目标还在就吃 15 点光伤。 */
    private static void tickReflects(ServerLevel level, long now) {
        Iterator<PendingReflect> iterator = REFLECTS.iterator();
        while (iterator.hasNext()) {
            PendingReflect pending = iterator.next();
            if (pending.level() != level || pending.at() > now) continue;
            iterator.remove();
            if (!(level.getEntity(pending.caster()) instanceof LivingEntity caster)) continue;
            if (!(level.getEntity(pending.target()) instanceof LivingEntity target)) continue;
            if (!target.isAlive() || target.isSpectator()) continue;
            NewSpellSystem.damage(caster, target, SHELTER_REFLECT, ElementType.LIGHT);
            Vec3 at = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 16, 0.45, 0.6, 0.45, 0.16);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.65F, 1.70F);
        }
    }

    // ───────────────────────── 曜光裂界 ─────────────────────────

    /** Alt + 左键：前摇期间追加一层蓄力，每层消耗 190 魔素。 */
    public static void addRadiantCharge(ServerPlayer player) {
        if (!castingRadiantRift(player)) return;
        int charge = CHARGES.getOrDefault(player.getUUID(), 0);
        if (charge >= RIFT_MAX_CHARGE) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.radiant_rift_full",
                    RIFT_MAX_CHARGE), true);
            return;
        }
        double cost = SpellAttributes.reducedManaCost(player, RIFT_CHARGE_COST);
        if (!ManaCheats.hasMana(player, cost)) {
            notifyChargeMana(player, (int)Math.ceil(cost));
            return;
        }
        if (!ManaData.spendMana(player, RIFT_CHARGE_COST)) return;
        SpellCasting.syncMana(player);
        CHARGES.put(player.getUUID(), charge + 1);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.radiant_rift_charge",
                charge + 1, RIFT_MAX_CHARGE), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.7F, 0.90F + charge * 0.06F);
        player.playNotifySound(SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS,
                0.55F, 0.85F + charge * 0.05F);
        // 每蓄一层把法阵的最新层数同步给客户端，法阵会立刻向外多延展一层。
        sendChargeVisual(player, charge + 1);
        SpellSyncPayload.send(player);
    }

    private static void notifyChargeMana(ServerPlayer player, int cost) {
        long now = player.level().getGameTime();
        Long last = CHARGE_NOTICE.get(player.getUUID());
        if (last != null && now - last < 10) return;
        CHARGE_NOTICE.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.radiant_rift_no_mana", cost), true);
    }

    /** 蓄力层数（客户端 HUD 与同步包读它）。 */
    public static int radiantCharge(ServerPlayer player) {
        return castingRadiantRift(player) ? CHARGES.getOrDefault(player.getUUID(), 0) : 0;
    }

    private static boolean castingRadiantRift(ServerPlayer player) {
        SpellCasting.CastSnapshot cast = SpellCasting.snapshot(player);
        return SpellDefinition.RADIANT_RIFT.id().equals(cast.spellId()) && cast.totalTicks() > 0;
    }

    /**
     * 前摇后 60% 的无敌。
     *
     * <p>按施法进度算：走完 40% 的前摇之后、直到读条结束都不会受到任何伤害；
     * 虚空、/kill 这类穿透无敌的伤害不受影响。</p>
     */
    public static boolean invulnerable(LivingEntity victim) {
        if (!(victim instanceof ServerPlayer player)) return false;
        SpellCasting.CastSnapshot cast = SpellCasting.snapshot(player);
        if (!SpellDefinition.RADIANT_RIFT.id().equals(cast.spellId()) || cast.totalTicks() <= 0) return false;
        int elapsed = cast.totalTicks() - cast.remainingTicks();
        return elapsed >= Math.ceil(cast.totalTicks() * (1.0 - RIFT_INVULNERABLE_WINDOW)) - 1;
    }

    /** 光球爆裂：按蓄力层数结算半径、伤害与光蚀。 */
    public static void castRadiantRift(ServerPlayer player) {
        int charge = Math.clamp(CHARGES.getOrDefault(player.getUUID(), 0), 0, RIFT_MAX_CHARGE);
        CHARGES.remove(player.getUUID());
        setCastSlow(player, false);
        ServerLevel level = player.serverLevel();
        Vec3 center = radiantOrbCenter(player);
        double radius = RIFT_BASE_RADIUS + RIFT_RADIUS_PER_CHARGE * charge;
        float damage = RIFT_BASE_DAMAGE * (float)Math.pow(RIFT_DAMAGE_MULTIPLIER, charge);
        int eclipseLevel = Math.clamp(1 + charge, 1, ECLIPSE_MAX_LEVEL);
        int eclipseTicks = ECLIPSE_BASE_TICKS + ECLIPSE_TICKS_PER_LEVEL * charge;

        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius),
                entity -> NewSpellSystem.enemy(player, entity)
                        && entity.getBoundingBox().distanceToSqr(center) <= radius * radius);
        for (LivingEntity victim : victims) {
            NewSpellSystem.damage(player, victim, damage, ElementType.LIGHT);
            applyEclipse(player, victim, eclipseLevel, eclipseTicks);
        }
        // 黑白闪光与世界层的爆裂分开：闪光固定 16 刻，爆裂光环持续 40 刻。
        SpellVisualPayload.send(player, SpellDefinition.RADIANT_RIFT, SpellVisualPayload.RADIANT_BURST, -1,
                center, center.add(0, radius, 0), RIFT_BURST_VISUAL_TICKS);
        sendFlash(level, center);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 120,
                radius * 0.35, radius * 0.30, radius * 0.35, 0.18);
        level.sendParticles(ParticleTypes.GLOW, center.x, center.y, center.z, 80,
                radius * 0.30, radius * 0.26, radius * 0.30, 0.05);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.35F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.85F, 1.45F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.1F, 0.65F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.15F);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.radiant_rift_burst",
                charge, (int)radius, (int)damage, eclipseLevel, eclipseTicks / 20), true);
        SpellSyncPayload.send(player);
    }

    /** 光球位置：身前两格、与眼睛同高；爆裂与客户端画的球是同一个点。 */
    public static Vec3 radiantOrbCenter(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, look.y, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        return player.getEyePosition().add(flat.normalize().scale(RIFT_ORB_DISTANCE));
    }

    private static void sendFlash(ServerLevel level, Vec3 center) {
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.RADIANT_RIFT.id(),
                SpellVisualPayload.RADIANT_FLASH, -1, -1, center, center, RIFT_FLASH_TICKS);
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 96.0, payload);
    }

    /** 蓄力时的法阵视觉：层数编码在 end.y 与 start.y 的差值里，被同一个包原地刷新。 */
    private static void sendChargeVisual(ServerPlayer player, int layers) {
        Vec3 feet = player.position();
        SpellVisualPayload payload = new SpellVisualPayload(SpellDefinition.RADIANT_RIFT.id(),
                SpellVisualPayload.RADIANT_CHARGE, player.getId(), -1, feet,
                feet.add(0, layers, 0), RIFT_CHARGE_VISUAL_TICKS);
        PacketDistributor.sendToPlayer(player, payload);
        PacketDistributor.sendToPlayersNear(player.serverLevel(), player, player.getX(), player.getY(), player.getZ(),
                64.0, payload);
    }

    // ───────────────────────── 光蚀 ─────────────────────────

    /** 给目标挂上光蚀：等级取最高、持续刷新，不叠加伤害。 */
    public static void applyEclipse(LivingEntity caster, LivingEntity victim, int level, int ticks) {
        if (victim == null || victim.level().isClientSide || !victim.isAlive()) return;
        int wanted = Math.clamp(level, 1, ECLIPSE_MAX_LEVEL);
        MobEffectInstance old = victim.getEffect(NewSpellContent.LIGHT_ECLIPSE);
        int finalLevel = old == null ? wanted
                : Math.max(wanted, Math.clamp(old.getAmplifier() + 1, 1, ECLIPSE_MAX_LEVEL));
        int duration = Math.max(ticks, old == null ? 0 : old.getDuration());
        victim.addEffect(new MobEffectInstance(NewSpellContent.LIGHT_ECLIPSE, duration, finalLevel - 1,
                false, true, true));
        // 强制状态记在实体自身数据里（到期刻 + 等级）：牛奶、/effect clear 或别的模组
        // 把效果抹掉后，下一个游戏刻就照这份记录原样补回来，等级与剩余时长都不缩水。
        CompoundTag data = victim.getPersistentData();
        long until = victim.level().getGameTime() + duration;
        data.putLong(ECLIPSE_UNTIL, Math.max(until, data.getLong(ECLIPSE_UNTIL)));
        data.putInt(ECLIPSE_FORCED_LEVEL, Math.max(finalLevel, data.getInt(ECLIPSE_FORCED_LEVEL)));
        if (caster != null && caster != victim) data.putUUID(ECLIPSE_CASTER, caster.getUUID());
        if (victim.level() instanceof ServerLevel server) {
            Vec3 at = victim.getBoundingBox().getCenter();
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.4, 0.5, 0.4, 0.08);
        }
    }

    /**
     * 光蚀的强制维护：外力把效果抹掉时就按记录补回。
     *
     * <p>光蚀本身的 cure 集合是空的（见 {@link SpellStatusEffect}），牛奶一类根本进不了移除流程；
     * 这里再兜一层，覆盖 {@code /effect clear}、死亡重置之外的强行清除，保证它只能自然到期。</p>
     */
    private static void restoreEclipse(LivingEntity entity, CompoundTag data, long now) {
        long until = data.getLong(ECLIPSE_UNTIL);
        if (!entity.isAlive() || now >= until) {
            // 目标死亡或正常到期：撤掉强制记录，效果交给原版计时器收尾。
            data.remove(ECLIPSE_UNTIL);
            data.remove(ECLIPSE_FORCED_LEVEL);
            return;
        }
        int forcedLevel = Math.clamp(data.getInt(ECLIPSE_FORCED_LEVEL), 1, ECLIPSE_MAX_LEVEL);
        MobEffectInstance current = entity.getEffect(NewSpellContent.LIGHT_ECLIPSE);
        int remain = (int)Math.min(Integer.MAX_VALUE, until - now);
        if (current != null && current.getAmplifier() + 1 >= forcedLevel && current.getDuration() + 2 >= remain)
            return;
        entity.addEffect(new MobEffectInstance(NewSpellContent.LIGHT_ECLIPSE, remain, forcedLevel - 1,
                false, true, true));
    }

    /** 光蚀的伤害间隔：每升一级 -1 刻，最低 5 刻。 */
    public static int eclipseInterval(int level) {
        return Math.max(ECLIPSE_MIN_INTERVAL, ECLIPSE_BASE_INTERVAL - (Math.clamp(level, 1, ECLIPSE_MAX_LEVEL) - 1));
    }

    /** 光蚀的每跳伤害：等级即伤害，Ⅰ 级 1 点。 */
    public static int eclipseDamage(int level) {
        return Math.clamp(level, 1, ECLIPSE_MAX_LEVEL);
    }

    /** 光蚀每刻维护：先把被外力清掉的效果补回，再按等级结算跳伤。 */
    private static void tickEclipse(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        CompoundTag data = entity.getPersistentData();
        if (data.contains(ECLIPSE_UNTIL)) restoreEclipse(entity, data, now);
        MobEffectInstance eclipse = entity.getEffect(NewSpellContent.LIGHT_ECLIPSE);
        if (eclipse == null) {
            data.remove(ECLIPSE_CASTER);
            return;
        }
        int eclipseLevel = Math.clamp(eclipse.getAmplifier() + 1, 1, ECLIPSE_MAX_LEVEL);
        if (now % eclipseInterval(eclipseLevel) != 0) return;
        UUID casterId = entity.getPersistentData().hasUUID(ECLIPSE_CASTER)
                ? entity.getPersistentData().getUUID(ECLIPSE_CASTER) : null;
        LivingEntity caster = casterId == null ? null
                : level.getEntity(casterId) instanceof LivingEntity living ? living : null;
        // 施法者已经死亡或离开维度时，光蚀仍按等级继续跳伤，只是不再归属到某个玩家。
        if (caster == null) caster = entity;
        float base = eclipseDamage(eclipseLevel);
        float value = SpellAttributes.calculateSecondarySpellDamage(caster, entity, base, false);
        value = ElementReactionSystem.elementalDamage(caster, entity, ElementType.LIGHT, value);
        if (value <= 0.0F) return;
        SpellDamage.hitElement(level, caster, entity, ElementType.LIGHT.id(), value);
        Vec3 at = entity.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 3, 0.28, 0.34, 0.28, 0.04);
        if (now % 20 == 0)
            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.20F, 1.95F);
    }

    // ───────────────────────── 事件入口 ─────────────────────────

    /** 每刻维护：光蚀结算与庇护回复。 */
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        tickEclipse(entity);
        if (!entity.hasEffect(NewSpellContent.LIGHT_SHELTER)) return;
        if (entity.tickCount % SHELTER_HEAL_INTERVAL == 0 && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(SHELTER_HEAL);
            if (entity.level() instanceof ServerLevel level) {
                Vec3 at = entity.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.HEART, at.x, at.y + 0.5, at.z, 1, 0.35, 0.3, 0.35, 0.02);
            }
        }
    }

    /** 每刻维护施法者的减速与蓄力记录。 */
    public static void tickPlayer(ServerPlayer player) {
        boolean casting = castingRadiantRift(player);
        setCastSlow(player, casting);
        if (!casting) {
            CHARGES.remove(player.getUUID());
            RIFT_CASTING.remove(player.getUUID());
            return;
        }
        // 读条一开始就把光球与多重法阵放出来，之后每 40 刻刷新一次；
        // 蓄力时 addRadiantCharge 会立刻补一包新的层数。
        if (RIFT_CASTING.add(player.getUUID()) || player.tickCount % 40 == 0)
            sendChargeVisual(player, CHARGES.getOrDefault(player.getUUID(), 0));
        if (invulnerable(player) && player.tickCount % 6 == 0) {
            ServerLevel level = player.serverLevel();
            Vec3 at = player.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.6, 1.0, 0.6, 0.04);
        }
    }

    private static void setCastSlow(ServerPlayer player, boolean active) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(CAST_SLOW, RIFT_CAST_SLOW,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(CAST_SLOW);
        }
    }

    /** 每个世界刻：反伤结算、庇护光环刷新与提示表清理。 */
    public static void tickLevel(ServerLevel level, long now) {
        tickReflects(level, now);
        tickShelters(level, now);
        if (now % 200 != 0) return;
        SHELTER_NOTICE.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null);
        CHARGE_NOTICE.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null);
    }

    /** 庇护光环维护：每 40 刻刷新一次；被牛奶清掉或到期时补一包结束事件。 */
    private static void tickShelters(ServerLevel level, long now) {
        if (SHELTERS.isEmpty()) return;
        boolean refresh = now % SHELTER_VISUAL_REFRESH == 0;
        Iterator<Map.Entry<UUID, Shelter>> iterator = SHELTERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Shelter> entry = iterator.next();
            Shelter shelter = entry.getValue();
            if (shelter.level() != level) continue;
            Entity entity = level.getEntity(entry.getKey());
            boolean alive = entity instanceof LivingEntity living && living.isAlive()
                    && living.hasEffect(NewSpellContent.LIGHT_SHELTER);
            if (!alive || now >= shelter.until()) {
                iterator.remove();
                if (entity instanceof LivingEntity living)
                    SpellVisualPayload.send(living, SpellDefinition.LIGHT_SHELTER,
                            SpellVisualPayload.LIGHT_SHELTER_END, living.getId(), living.position(),
                            living.position(), 1);
                continue;
            }
            if (!refresh) continue;
            SpellVisualPayload.send((LivingEntity)entity, SpellDefinition.LIGHT_SHELTER,
                    SpellVisualPayload.LIGHT_SHELTER, entity.getId(), entity.position(), entity.position(),
                    SHELTER_VISUAL_TICKS);
        }
    }

    /** 下线、死亡或换维度：撤掉减速、蓄力与庇护。 */
    public static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        CHARGES.remove(id);
        RIFT_CASTING.remove(id);
        SHELTER_NOTICE.remove(id);
        CHARGE_NOTICE.remove(id);
        setCastSlow(player, false);
        clearShelter(player);
    }

    public static void clearAll() {
        CHARGES.clear();
        SHELTERS.clear();
        RIFT_CASTING.clear();
        SHELTER_NOTICE.clear();
        CHARGE_NOTICE.clear();
        REFLECTS.clear();
    }

    private LightSpells() { }
}
