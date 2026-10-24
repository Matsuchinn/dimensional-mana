package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 服务器权威的施法计时、目标选择、状态持续与元素视觉。 */
public final class SpellCasting {
    private static final Map<UUID, CastState> CASTS = new HashMap<>();
    private static final Map<UUID, Map<SpellDefinition, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, TargetState> TARGETING = new HashMap<>();
    private static final int TARGETING_TIMEOUT_TICKS = 20 * 15;
    private static final Map<UUID, Long> STUNNED = new HashMap<>();
    private static final Map<UUID, Long> NO_FALL = new HashMap<>();
    private static final Map<UUID, FlameBlessingState> FLAME_BLESSED = new HashMap<>();
    private static final Map<UUID, ThornState> THORNED = new HashMap<>();
    private static final List<HealState> HEALS = new ArrayList<>();
    private static final List<SecondaryDamageState> SECONDARY_DAMAGE = new ArrayList<>();
    private static final ResourceLocation STONE_ARMOR = id("stone_armor");
    private static final ResourceLocation STONE_TOUGHNESS = id("stone_toughness");
    private static final ResourceLocation STONE_SLOW = id("stone_slow");
    private static final ResourceLocation IRON_ARMOR = id("iron_armor");
    private static final ResourceLocation IRON_SLOW = id("iron_slow");
    private static final ResourceLocation CORROSION_ARMOR = id("corrosion_armor");
    private static final ResourceLocation CORROSION_TOUGHNESS = id("corrosion_toughness");

    public record CastSnapshot(String spellId, int remainingTicks, int totalTicks) { }
    private record CastState(SpellDefinition spell, List<UUID> targets, int remainingTicks, int totalTicks) {
        CastState tick() { return new CastState(spell, targets, remainingTicks - 1, totalTicks); }
    }
    private record TargetState(SpellDefinition spell, List<UUID> selected, long expiresAt) {
        TargetState withAdded(UUID target) {
            List<UUID> list = new ArrayList<>(selected);
            if (spell.maxTargets() <= 1) list.clear();
            list.add(target);
            return new TargetState(spell, List.copyOf(list), expiresAt);
        }
    }
    private record FlameBlessingState(UUID target, long expiresAt, double staffDamage, boolean staffBonusAvailable) {
        FlameBlessingState useStaffBonus() { return new FlameBlessingState(target, expiresAt, staffDamage, false); }
    }
    private record ThornState(UUID caster, long expiresAt, double staffDamage, boolean staffBonusAvailable) {
        ThornState useStaffBonus() { return new ThornState(caster, expiresAt, staffDamage, false); }
    }
    private record HealState(UUID caster, UUID target, ServerLevel level, float amountPerTick, int interval, long nextAt, long expiresAt) { }
    private record SecondaryDamageState(UUID caster, UUID target, ServerLevel level, int interval,
                                        long nextAt, long expiresAt, double staffDamage, boolean staffBonusAvailable) { }

    public static void startSelected(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || CASTS.containsKey(player.getUUID())) return;
        // 深渊龙击持续喷射期间，施法键改为“取消施法”。
        if (NewSpellSystem.cancelDragonBeam(player)) {
            cancelTargetSilently(player);
            return;
        }
        // 零下领域叠层期间，施法键改为“释放领域”。
        if (NewSpellSystem.releaseSubzeroDomain(player)) {
            cancelTargetSilently(player);
            return;
        }
        // 炽阳之怒悬停期间，施法键改为“立即释放火球”。
        if (NewSpellSystem.releaseBlazingSun(player)) {
            cancelTargetSilently(player);
            return;
        }
        // 多重结界射线展开期间，施法键改为“结束本次施法”，已展开的法阵各自保留剩余时间。
        if (NewSpellSystem.endBarrierField(player)) {
            cancelTargetSilently(player);
            return;
        }
        // 风狂期间，施法键改为挥出风十字斩并结束这段风狂。
        if (WindSpells.releaseCrossSlash(player)) {
            cancelTargetSilently(player);
            return;
        }
        // 万法千陨召唤期间，施法键改为“停止召唤并立刻投出已有石块”。
        if (EarthSpells.stopMyriadMeteors(player)) {
            cancelTargetSilently(player);
            return;
        }
        // 万剑归一裂缝维持期间，施法键改为"提前关闭天空裂缝"。
        if (MetalSpells.cancelRift(player)) {
            cancelTargetSilently(player);
            return;
        }
        // 鸩羽千夜的毒雾扩散期间，施法键改为"停止扩散"（毒雾继续存在到时长结束）。
        if (SpellData.get(player).selected() == SpellDefinition.VENOM_NIGHT && PoisonSpells.stopMistSpread(player)) {
            cancelTargetSilently(player);
            return;
        }
        if (NewSpellSystem.cannotCast(player)) {
            cancelTargetSilently(player);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.spell_sealed"), true);
            return;
        }
        UUID playerId = player.getUUID();
        SpellData.Snapshot data = SpellData.get(player);
        SpellDefinition spell = data.selected();
        // 任何无效选择都必须先退出旧的选定模式，避免客户端继续拦截左右键。
        if (spell == null) {
            cancelTargetSilently(player);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.no_selected_spell"), true);
            return;
        }
        if (!data.learned(spell) && data.learning() != spell) {
            cancelTargetSilently(player);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.spell_not_learned"), true);
            return;
        }
        if (!ElementTalentData.canAccess(player, spell)) {
            cancelTargetSilently(player);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.element_locked"), true);
            return;
        }
        int cooldown = cooldownRemaining(player, spell);
        if (cooldown > 0) {
            cancelTargetSilently(player);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.spell_cooldown",
                    String.format("%.1f", cooldown / 20.0)), true);
            return;
        }
        // 生命之树维持期间冷却还没开始，这里兜底拦住第二次施法，避免同时立起两棵树。
        if (spell == SpellDefinition.ENDLESS_BANKAI && WoodSpells.hasBankai(player)) {
            cancelTargetSilently(player);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.endless_bankai_active"), true);
            return;
        }
        // 毒雾维持期间同理：冷却要等雾散才开始，这里拦住第二团毒雾。
        if (spell == SpellDefinition.VENOM_NIGHT && PoisonSpells.hasMist(player)) {
            cancelTargetSilently(player);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.venom_night_active_hint"), true);
            return;
        }
        if (ManaCheats.manaCostEnabled(player)
                && ManaData.get(player).mana() < SpellAttributes.reducedManaCost(player, spell)) {
            // 不能只拦截施法：设定要求失败时耗尽剩余魔素并进入魔力枯竭。
            // spendMana 的失败分支会原子地完成清空与枯竭状态，避免目标法术绕过该机制。
            ManaData.spendMana(player, SpellAttributes.reducedManaCost(player, spell));
            failInsufficientMana(player, spell);
            return;
        }
        if (spell.requiresTarget()) {
            TargetState old = TARGETING.get(playerId);
            if (old != null && old.spell() == spell) {
                cancelTarget(player);
            } else {
                TARGETING.put(playerId, new TargetState(spell, List.of(),
                        player.level().getGameTime() + TARGETING_TIMEOUT_TICKS));
                player.displayClientMessage(Component.translatable(spell.maxTargets() > 1
                        ? "message.dimensional_mana.targeting_started_multi"
                        : "message.dimensional_mana.targeting_started"), true);
                SpellSyncPayload.send(player);
            }
            return;
        }
        cancelTargetSilently(player);
        beginCast(player, spell, null);
    }

    public static void selectTarget(ServerPlayer player) {
        TargetState state = validTargetingState(player);
        if (state == null) return;
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof LivingEntity living && living.isAlive() && living != player,
                state.spell().targetRange());
        UUID selected = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living
                ? living.getUUID() : state.spell().friendlyTarget() ? player.getUUID() : null;
        if (selected == null) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.target_missing"), true);
            return;
        }
        Entity picked = player.serverLevel().getEntity(selected);
        // 治疗类法术只接受友方目标，避免把泉水或护盾送给敌人。
        if (picked instanceof LivingEntity living && living != player
                && state.spell().friendlyTarget() && NewSpellSystem.enemy(player, living)) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.target_not_friendly"), true);
            return;
        }
        int max = state.spell().maxTargets();
        if (max > 1) {
            if (state.selected().contains(selected)) {
                player.displayClientMessage(Component.translatable("message.dimensional_mana.target_duplicate"), true);
                return;
            }
            if (state.selected().size() >= max) {
                player.displayClientMessage(Component.translatable("message.dimensional_mana.target_full", max), true);
                return;
            }
        }
        // 单目标法术保持“再点一次就换目标”的旧手感；多目标法术逐个累加。
        TARGETING.put(player.getUUID(), state.withAdded(selected));
        if (picked instanceof LivingEntity living) living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.5F);
        if (max > 1) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.target_selected_multi",
                    state.selected().size() + 1, max,
                    picked == null ? player.getDisplayName() : picked.getDisplayName()), true);
        } else {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.target_selected",
                    picked == null ? player.getDisplayName() : picked.getDisplayName()), true);
        }
        SpellSyncPayload.send(player);
    }

    public static void confirmTarget(ServerPlayer player) {
        TargetState state = validTargetingState(player);
        if (state == null || state.selected().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.target_missing"), true);
            return;
        }
        TARGETING.remove(player.getUUID());
        beginCast(player, state.spell(), state.selected());
    }

    public static void cancelTarget(ServerPlayer player) {
        if (TARGETING.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable("message.dimensional_mana.target_cancelled"), true);
            SpellSyncPayload.send(player);
        }
    }

    private static void cancelTargetSilently(ServerPlayer player) {
        if (TARGETING.remove(player.getUUID()) != null) SpellSyncPayload.send(player);
    }

    /** 快捷槽本身马上会同步，先丢弃旧目标状态即可，避免发送两份相互覆盖的状态包。 */
    static void discardTarget(ServerPlayer player) {
        TARGETING.remove(player.getUUID());
    }

    private static TargetState validTargetingState(ServerPlayer player) {
        TargetState state = TARGETING.get(player.getUUID());
        if (state == null) return null;
        SpellData.Snapshot data = SpellData.get(player);
        boolean valid = state.expiresAt() > player.level().getGameTime() && data.selected() == state.spell()
                && (data.learned(state.spell()) || data.learning() == state.spell())
                && ElementTalentData.canAccess(player, state.spell());
        if (valid) return state;
        TARGETING.remove(player.getUUID());
        SpellSyncPayload.send(player);
        return null;
    }

    private static void beginCast(ServerPlayer player, SpellDefinition spell, List<UUID> targets) {
        if (!player.isAlive() || player.isSpectator() || CASTS.containsKey(player.getUUID())) return;
        if (NewSpellSystem.cannotCast(player)) {
            cancelTargetSilently(player);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.spell_sealed"), true);
            return;
        }
        SpellData.Snapshot data = SpellData.get(player);
        if ((!data.learned(spell) && data.learning() != spell) || !ElementTalentData.canAccess(player, spell)
                || cooldownRemaining(player, spell) > 0) {
            SpellSyncPayload.send(player);
            return;
        }
        List<UUID> chosen = new ArrayList<>();
        if (spell.requiresTarget()) {
            for (UUID id : targets) {
                LivingEntity candidate = living(player.serverLevel().getEntity(id));
                if (candidate == null || !candidate.isAlive()
                        || candidate.distanceToSqr(player) > spell.targetRange() * spell.targetRange()
                        || (!spell.friendlyTarget() && !NewSpellSystem.enemy(player, candidate))
                        || !player.hasLineOfSight(candidate)) continue;
                chosen.add(id);
                if (chosen.size() >= spell.maxTargets()) break;
            }
            // 单目标法术保持“宁缺毋滥”；多目标法术只要还剩一个有效目标就能施放。
            boolean enough = spell.maxTargets() > 1 ? !chosen.isEmpty() : chosen.size() == targets.size() && !chosen.isEmpty();
            if (!enough) {
                player.displayClientMessage(Component.translatable("message.dimensional_mana.target_missing"), true);
                SpellSyncPayload.send(player);
                return;
            }
        }
        // 必须先得到服务器扣费成功的结果，才能创建施法状态；失败绝不能进入读条或结算。
        if (!ManaData.spendMana(player, SpellAttributes.reducedManaCost(player, spell))) {
            failInsufficientMana(player, spell);
            return;
        }
        syncMana(player);
        // 魔法大师披风：施法成功时按概率在身边召出一颗魔素凝聚球（披风自己的开关关掉时不触发）。
        if (player.getRandom().nextFloat() < MagicEquipment.manaOrbChance(player)) ManaOrb.summon(player);
        // 大师施法手套：每 5 次施法为一个小循环，第 5 次的伤害必定暴击。
        CastingGloveItem.onCast(player);
        // 魔法抗拒：每层把施法前摇延长 1 秒，最多 10 层。
        int total = spell.adjustedCastTicks(player) + MobSkills.retortStacks(player) * MagicRetortEffect.TICKS_PER_STACK;
        CASTS.put(player.getUUID(), new CastState(spell, List.copyOf(chosen), total, total));
        if (spell == SpellDefinition.MULTI_BARRIER_RAY)
            player.displayClientMessage(Component.translatable("message.dimensional_mana.barrier_charging"), true);
        else if (spell == SpellDefinition.BLAZING_SUN)
            player.displayClientMessage(Component.translatable("message.dimensional_mana.blazing_sun_charging"), true);
        else if (spell == SpellDefinition.ABYSS_DRAGON_STRIKE)
            player.displayClientMessage(Component.translatable("message.dimensional_mana.abyss_dragon_charging"), true);
        else if (spell == SpellDefinition.SPRING_RETURN)
            player.displayClientMessage(Component.translatable("message.dimensional_mana.spring_return_charging",
                    chosen.size()), true);
        else if (spell == SpellDefinition.SUBZERO_DOMAIN)
            player.displayClientMessage(Component.translatable("message.dimensional_mana.subzero_charging"), true);
        else if (spell == SpellDefinition.THUNDER_TEMPEST) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.thunder_tempest_charging"), true);
            // 读条一开始就登记追加记录：前摇期间按住 Alt + 左键还能再补两颗雷球。
            NewSpellSystem.beginTempestCharge(player);
        }
        else if (spell == SpellDefinition.THORN_IMPALE)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.thorn_impale_charging"), true);
        else if (spell == SpellDefinition.ENDLESS_BANKAI)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.endless_bankai_charging"), true);
        else if (spell == SpellDefinition.GOLDEN_BELL)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.golden_bell_charging"), true);
        else if (spell == SpellDefinition.TEN_THOUSAND_SWORDS)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.ten_thousand_swords_charging"), true);
        else if (spell == SpellDefinition.LIGHT_SHELTER)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.light_shelter_charging"), true);
        else if (spell == SpellDefinition.RADIANT_RIFT)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.radiant_rift_charging"), true);
        else if (spell == SpellDefinition.VENOM_HEART)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.venom_heart_charging"), true);
        else if (spell == SpellDefinition.VENOM_NIGHT)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.venom_night_charging"), true);
        else if (spell == SpellDefinition.INFRASOUND)
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.infrasound_charging"), true);
        else if (spell == SpellDefinition.SOUL_CHORD) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.soul_chord_charging"), true);
            // 读条一开始就把追加记录清空：上一次被中断的施法不能把旧数量带进来。
            SonicSpells.beginCharge(player);
        }
        SpellVisualPayload.send(player, spell, SpellVisualPayload.CHARGE, player.getId(),
                player.getEyePosition(), player.getEyePosition(), total);
        SpellSyncPayload.send(player);
    }

    private static void failInsufficientMana(ServerPlayer player, SpellDefinition spell) {
        TARGETING.remove(player.getUUID());
        shatter(player, spell);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.spell_no_mana",
                Component.translatable(spell.translationKey()),
                String.format(java.util.Locale.ROOT, "%.1f", SpellAttributes.reducedManaCost(player, spell))), true);
        syncMana(player);
        SpellSyncPayload.send(player);
    }

    private static void shatter(ServerPlayer player, SpellDefinition spell) {
        SpellVisualPayload.send(player, spell, SpellVisualPayload.SHATTER, -1,
                player.getEyePosition().add(player.getLookAngle().scale(0.65)), player.getEyePosition(), 16);
    }

    private static void visual(ServerPlayer player, SpellDefinition spell, Vec3 start, Vec3 end, int anchor, int duration) {
        SpellVisualPayload.send(player, spell, SpellVisualPayload.RELEASE, anchor, start, end, duration);
    }

    public static void syncMana(ServerPlayer player) {
        ManaData.Snapshot mana = ManaData.get(player);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ManaSyncPayload(mana.mana(), mana.maxMana(), mana.battleQi(), mana.maxBattleQi(),
                        mana.exhaustionTicks() > 0, ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
    }

    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        // 每刻核对目标模式与当前快捷槽；超时、换槽或失去法术权限都会立即释放输入状态。
        if (TARGETING.containsKey(id)) validTargetingState(player);
        CastState cast = CASTS.get(id);
        if (cast != null) {
            if (!player.isAlive() || NewSpellSystem.cannotCast(player)) {
                CASTS.remove(id);
                shatter(player, cast.spell());
                SpellSyncPayload.send(player);
            }
            else if (cast.remainingTicks() <= 1) {
                CASTS.remove(id);
                finishCast(player, cast.spell(), cast.targets());
            } else {
                CASTS.put(id, cast.tick());
                // 天雷滚滚的前摇长达 15 秒：每两秒一记低频嗡鸣，头顶的雷球会越聚越大。
                if (cast.spell() == SpellDefinition.THUNDER_TEMPEST && cast.remainingTicks() % 40 == 0) {
                    ServerLevel level = player.serverLevel();
                    Vec3 at = player.position().add(0, player.getBbHeight() + 2.15, 0);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                            SoundSource.PLAYERS, 0.85F, 0.55F + 0.10F * ((cast.totalTicks() - cast.remainingTicks()) / 40F));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            at.x, at.y, at.z, 10, 0.8, 0.5, 0.8, 0.22);
                }
                if (cast.remainingTicks() % 10 == 0) SpellSyncPayload.send(player);
            }
        }
        // 深渊龙击没有读条，持续喷射由独立状态维护（耗魔、转向、伤害与破方块）。
        NewSpellSystem.tickPlayer(player);
        tickHeals(player);
        tickSecondaryDamage(player);
    }

    public static void tickLiving(LivingEntity living) {
        long now = living.level().getGameTime();
        Long stunUntil = STUNNED.get(living.getUUID());
        if (stunUntil != null) {
            if (now >= stunUntil || !living.isAlive()) STUNNED.remove(living.getUUID());
            else {
                living.setDeltaMovement(0, Math.min(0.0, living.getDeltaMovement().y), 0);
                living.hurtMarked = true;
                if (living.level() instanceof ServerLevel level && now % 5 == 0) ring(level,
                        living.position().add(0, living.getBbHeight() + 0.2, 0), 0.45, 10, 0xEBCBFF);
            }
        }
        Long noFallUntil = NO_FALL.get(living.getUUID());
        if (noFallUntil != null) {
            if (now >= noFallUntil) NO_FALL.remove(living.getUUID());
            else living.resetFallDistance();
        }
        cleanupModifier(living, STONE_ARMOR, now);
        cleanupModifier(living, STONE_TOUGHNESS, now);
        cleanupModifier(living, STONE_SLOW, now);
        cleanupModifier(living, IRON_ARMOR, now);
        cleanupModifier(living, IRON_SLOW, now);
        cleanupModifier(living, CORROSION_ARMOR, now);
        cleanupModifier(living, CORROSION_TOUGHNESS, now);
        ElementReactionSystem.tick(living);
    }

    private static void finishCast(ServerPlayer player, SpellDefinition spell, List<UUID> targetIds) {
        // 深渊龙击的 40 秒冷却要等持续喷射结束才开始计算，由喷射状态在结束时登记。
        // 风葬回廊、万法千陨与不息卍解同理：冷却分别从风十字斩、投出石块、生命之树消失时开始计算。
        if (spell != SpellDefinition.ABYSS_DRAGON_STRIKE && spell != SpellDefinition.WIND_BURIAL_CORRIDOR
                && spell != SpellDefinition.MYRIAD_METEORS && spell != SpellDefinition.ENDLESS_BANKAI
                && spell != SpellDefinition.VENOM_NIGHT)
            startCooldown(player, spell);
        SpellData.Snapshot data = SpellData.get(player);
        boolean learning = !data.learned(spell) && data.learning() == spell;
        if (learning && player.getRandom().nextDouble() >= SpellData.learningChance(player, spell)) {
            shatter(player, spell);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.learning_failed",
                    Component.translatable(spell.translationKey())), true);
            SpellSyncPayload.send(player);
            return;
        }
        List<LivingEntity> targets = new ArrayList<>();
        if (spell.requiresTarget()) {
            for (UUID id : targetIds) {
                LivingEntity candidate = id == null ? null : living(player.serverLevel().getEntity(id));
                if (candidate == null || !candidate.isAlive()
                        || candidate.distanceToSqr(player) > spell.targetRange() * spell.targetRange()
                        || !player.hasLineOfSight(candidate)
                        || (!spell.friendlyTarget() && !NewSpellSystem.enemy(player, candidate))) continue;
                targets.add(candidate);
                if (targets.size() >= spell.maxTargets()) break;
            }
        }
        if (spell.requiresTarget() && targets.isEmpty()) {
            shatter(player, spell);
            player.displayClientMessage(Component.translatable("message.dimensional_mana.target_missing"), true);
            SpellSyncPayload.send(player);
            return;
        }
        apply(player, spell, targets);
        MagicStaffItem.damageHeldStaff(player);
        MagicProgression.recordSpellCast(player);
        if (learning) {
            int progress = SpellData.recordLearningSuccess(player, spell);
            if (progress >= SpellData.REQUIRED_SUCCESSES) {
                MagicProgression.addMagicExperience(player, 800);
                MagicProgression.awardAdvancement(player, "first_spell");
                showLearnedTitle(player, spell);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.35F);
            } else {
                player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 0.9F + progress * 0.15F);
                player.displayClientMessage(Component.translatable("message.dimensional_mana.learning_success",
                        Component.translatable(spell.translationKey()), progress, SpellData.REQUIRED_SUCCESSES,
                        (int)Math.round(SpellData.learningChance(player, spell) * 100.0)), false);
            }
        }
        ProgressSyncPayload.send(player);
        SpellSyncPayload.send(player);
    }

    private static void apply(ServerPlayer player, SpellDefinition spell, List<LivingEntity> targets) {
        LivingEntity target = targets.isEmpty() ? null : targets.getFirst();
        if (spell.isNewSpell()) {
            NewSpellSystem.cast(player, spell, targets);
            return;
        }
        switch (spell) {
            case ARCANE_RAY -> castLine(player, spell, 12, 16, 3, null);
            case ARCANE_WARD -> castAbsorption(player, spell, 300, 2, 0xD9B2FF);
            case ARCANE_SURGE -> castSurge(player);
            case FIREBALL -> castLine(player, spell, 10, 8, 1, hit -> {
                hit.setRemainingFireTicks(60);
                queueSecondaryDamage(player, hit, 20);
            });
            case FLAME_BLESSING -> {
                FLAME_BLESSED.put(player.getUUID(),
                        new FlameBlessingState(target.getUUID(), player.level().getGameTime() + 400,
                                MagicStaffItem.heldStats(player).spellDamage(), true));
                ElementReactionSystem.apply(player, player, ElementType.FIRE);
            }
            case WATER_BOLT -> castLine(player, spell, 9, 6, 1, hit -> knockAway(player, hit, 2.0));
            case MOISTURE -> castMoisture(player, target == null ? player : target);
            case ICE_SPIKE -> castLine(player, spell, 10, 7, 1,
                    hit -> hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2)));
            case ICE_SHIELD -> castAbsorption(player, spell, 240, 1, 0xA8ECFF);
            case ELECTRIC_SHOCK -> castElectric(player);
            case STATIC_FIELD -> {
                LivingEntity enemy = validEnemyTarget(player, target);
                stun(enemy, 44);
                if (enemy != null) ElementReactionSystem.apply(player, enemy, ElementType.LIGHTNING);
            }
            case WIND_BLADE -> castLine(player, spell, 8, 5, 3, null);
            case GALE_DASH -> castDash(player);
            case ROCKFALL -> castRockfall(player, target);
            case STONE_ARMOR -> applyArmor(player, STONE_ARMOR, 10, STONE_TOUGHNESS, 2, STONE_SLOW, -0.10, 400);
            case THORNS -> castLine(player, spell, 9, 6, 1, hit -> THORNED.put(hit.getUUID(),
                    new ThornState(player.getUUID(), hit.level().getGameTime() + 100,
                            MagicStaffItem.heldStats(player).spellDamage(), true)));
            case REJUVENATION -> castRejuvenation(player);
            case SHARP_EDGE -> castSharpEdge(player);
            case IRON_WALL -> applyArmor(player, IRON_ARMOR, 12, null, 0, IRON_SLOW, -0.15, 300);
            case LIGHT_BOLT -> castLine(player, spell, 10, 7, 1, null);
            case PURIFYING_LIGHT -> castPurifyingLight(player);
            case POISON_BOLT -> castLine(player, spell, 9, 4, 1, hit -> {
                hit.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
                queueSecondaryDamage(player, hit, 25);
            });
            case CORROSION -> {
                LivingEntity enemy = validEnemyTarget(player, target);
                castCorrosion(player, enemy);
                if (enemy != null) ElementReactionSystem.apply(player, enemy, ElementType.POISON);
            }
            case SONIC_WAVE -> castSonicWave(player);
            case RESONANCE -> {
                LivingEntity enemy = validEnemyTarget(player, target);
                stun(enemy, 80);
                if (enemy != null) ElementReactionSystem.apply(player, enemy, ElementType.SOUND);
            }
        }
        // 射线类在命中计算后发送精确端点；附着类跟随真实目标，避免动画与目标脱离。
        int duration = switch (spell) {
            case ARCANE_WARD, ICE_SHIELD, STONE_ARMOR, IRON_WALL -> 60;
            case ARCANE_SURGE, FLAME_BLESSING -> 80;
            case MOISTURE, REJUVENATION, PURIFYING_LIGHT -> 60;
            case STATIC_FIELD -> 44;
            case CORROSION, RESONANCE -> 80;
            default -> 0;
        };
        if (duration > 0) {
            LivingEntity anchor = spell == SpellDefinition.FLAME_BLESSING ? player
                    : spell.requiresTarget() ? target : player;
            if (anchor != null) visual(player, spell, player.getEyePosition(), anchor.getBoundingBox().getCenter(),
                    anchor.getId(), duration);
        }
    }

    private static LivingEntity validEnemyTarget(ServerPlayer player, LivingEntity target) {
        return target != null && target != player && target.isAlive() && target.distanceToSqr(player) <= 144 ? target : null;
    }

    private static void castLine(ServerPlayer player, SpellDefinition spell, double range, float damage,
                                 int maxTargets, TargetAction action) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(0.35));
        Vec3 intendedEnd = start.add(player.getLookAngle().scale(range));
        HitResult blockHit = level.clip(new ClipContext(start, intendedEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? intendedEnd : blockHit.getLocation();
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        int visualAnchor = -1;
        if (length > 0.01) {
            Vec3 normal = direction.normalize();
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1.3),
                    target -> target != player && target.isAlive() && player.canAttack(target));
            targets.sort(Comparator.comparingDouble(target -> projection(start, normal, target.getBoundingBox().getCenter())));
            int hitCount = 0;
            for (LivingEntity target : targets) {
                Vec3 center = target.getBoundingBox().getCenter();
                double along = projection(start, normal, center);
                if (along < 0 || along > length) continue;
                Vec3 nearest = start.add(normal.scale(along));
                if (target.getBoundingBox().inflate(0.55).distanceToSqr(nearest) > 1.0) continue;
                magicDamage(player, target, damage, spell.element());
                if (action != null) action.apply(target);
                if (spell == SpellDefinition.THORNS) visualAnchor = target.getId();
                if (++hitCount >= maxTargets) {
                    end = nearest;
                    break;
                }
            }
        }
        if (spell.element() == ElementType.FIRE) SpellEnvironmentSystem.igniteAlong(player, level, start, end);
        else if (spell.element() == ElementType.ICE) SpellEnvironmentSystem.freezeAlong(level, start, end);
        visual(player, spell, start, end, visualAnchor, spell == SpellDefinition.THORNS ? 40 : 24);
    }

    private static void castElectric(ServerPlayer player) {
        LivingEntity first = firstTarget(player, 11);
        if (first == null) {
            castLine(player, SpellDefinition.ELECTRIC_SHOCK, 11, 10, 1, null);
            return;
        }
        magicDamage(player, first, 10, ElementType.LIGHTNING);
        visual(player, SpellDefinition.ELECTRIC_SHOCK, player.getEyePosition(), first.getBoundingBox().getCenter(), -1, 18);
        LivingEntity second = player.serverLevel().getEntitiesOfClass(LivingEntity.class, first.getBoundingBox().inflate(3),
                entity -> entity != player && entity != first && entity.isAlive() && player.canAttack(entity)).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(first))).orElse(null);
        if (second != null) {
            magicDamage(player, second, 5, ElementType.LIGHTNING);
            visual(player, SpellDefinition.ELECTRIC_SHOCK, first.getBoundingBox().getCenter(), second.getBoundingBox().getCenter(), -1, 18);
        }
    }

    private static LivingEntity firstTarget(ServerPlayer player, double range) {
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof LivingEntity living && living.isAlive() && living != player
                        && player.canAttack(living), range);
        return hit instanceof EntityHitResult result ? living(result.getEntity()) : null;
    }

    private static void castMoisture(ServerPlayer player, LivingEntity target) {
        target.heal(8.0F);
        ElementReactionSystem.apply(player, target, ElementType.WATER);
        long now = player.level().getGameTime();
        HEALS.add(new HealState(player.getUUID(), target.getUUID(), player.serverLevel(), 1.0F, 25, now + 25, now + 100));
        ring(player.serverLevel(), target.getBoundingBox().getCenter(), 0.7, 20, 0x4BA7FF);
    }

    private static void castRejuvenation(ServerPlayer player) {
        player.heal(10.0F);
        ElementReactionSystem.apply(player, player, ElementType.NATURE);
        long now = player.level().getGameTime();
        HEALS.add(new HealState(player.getUUID(), player.getUUID(), player.serverLevel(), 1.0F, 20, now + 20, now + 160));
        ring(player.serverLevel(), player.getBoundingBox().getCenter(), 0.8, 24, 0x67C653);
    }

    private static void castPurifyingLight(ServerPlayer player) {
        player.heal(8.0F);
        // 自身已有毒附着时优先触发“净化”反应；没有毒时则正常留下光附着。
        ElementReactionSystem.apply(player, player, ElementType.LIGHT);
        List<MobEffectInstance> harmful = player.getActiveEffects().stream()
                .filter(effect -> !effect.getEffect().value().isBeneficial()).toList();
        if (!harmful.isEmpty()) player.removeEffect(harmful.get(player.getRandom().nextInt(harmful.size())).getEffect());
        ring(player.serverLevel(), player.getBoundingBox().getCenter(), 1.0, 28, 0xFFF4A8);
    }

    private static void castAbsorption(ServerPlayer player, SpellDefinition spell, int duration, int amplifier, int color) {
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, amplifier, false, true, true));
        if (color == 0xA8ECFF) {
            ElementReactionSystem.apply(player, player, ElementType.ICE);
            SpellEnvironmentSystem.sustainIce(player, duration);
        }
        for (int layer = 0; layer < 3; layer++) ring(player.serverLevel(), player.position().add(0, 0.35 + layer * 0.55, 0),
                0.65, 18, color);
    }

    private static void castDash(ServerPlayer player) {
        Vec3 motion = player.getLookAngle().normalize().scale(1.55);
        player.setDeltaMovement(motion.x, Math.max(0.12, motion.y), motion.z);
        player.hurtMarked = true;
        player.resetFallDistance();
        NO_FALL.put(player.getUUID(), player.level().getGameTime() + 80);
        ElementReactionSystem.apply(player, player, ElementType.WIND);
        visual(player, SpellDefinition.GALE_DASH, player.position().add(0, 0.8, 0),
                targetPoint(player, 6), -1, 24);
    }

    private static void castRockfall(ServerPlayer player, LivingEntity selectedTarget) {
        if (selectedTarget == null) return;
        Vec3 center = selectedTarget.position();
        ServerLevel level = player.serverLevel();
        level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(3),
                target -> target != player && target.isAlive() && player.canAttack(target)).forEach(target -> {
            magicDamage(player, target, 12, ElementType.EARTH);
            stun(target, 20);
        });
        visual(player, SpellDefinition.ROCKFALL, player.getEyePosition(), center, selectedTarget.getId(), 36);
    }

    private static Vec3 targetPoint(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        HitResult hit = player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static void castSharpEdge(ServerPlayer player) {
        LivingEntity target = firstTarget(player, 10);
        if (target == null) { castLine(player, SpellDefinition.SHARP_EDGE, 10, 9, 1, null); return; }
        // 约三分之一使用绕甲魔法伤害；元素反应倍率同时作用于两部分。
        float armored = SpellAttributes.calculateSpellDamage(player, target, 6.0F);
        float combined = ElementReactionSystem.elementalDamage(player, target, ElementType.METAL, 3.0F + armored);
        float multiplier = combined / Math.max(0.001F, 3.0F + armored);
        // 两段属于同一次法术：共用一次暴击与刻蚀倍率，再分别按金属性/物理伤害类型落地。
        SpellDamage.hitSplitElement(player.serverLevel(), player, target, ElementType.METAL.id(),
                3.0F * multiplier, player.serverLevel().damageSources().playerAttack(player),
                armored * multiplier);
        visual(player, SpellDefinition.SHARP_EDGE, player.getEyePosition(), target.getBoundingBox().getCenter(), -1, 22);
    }

    private static void castCorrosion(ServerPlayer caster, LivingEntity target) {
        if (target == null) return;
        // 金钟罩期间免疫破甲：腐化一类降低护甲的减益完全无法附着。
        if (MetalSpells.immuneToArmorBreak(target)) {
            caster.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.golden_bell_block_break"), true);
            return;
        }
        addTimedModifier(target, Attributes.ARMOR, CORROSION_ARMOR, -0.30,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 160);
        addTimedModifier(target, Attributes.ARMOR_TOUGHNESS, CORROSION_TOUGHNESS, -0.30,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 160);
    }

    /** 金钟罩期间每秒兜底清除破甲：把已经附着的腐化降护甲效果连同残留计时一起拿掉。 */
    public static void clearArmorBreaks(LivingEntity target) {
        if (target == null || target.level().isClientSide) return;
        for (AttributeInstance instance : target.getAttributes().getSyncableAttributes()) {
            instance.removeModifier(CORROSION_ARMOR);
            instance.removeModifier(CORROSION_TOUGHNESS);
        }
        target.getPersistentData().remove("DimensionalManaModifier_corrosion_armor");
        target.getPersistentData().remove("DimensionalManaModifier_corrosion_toughness");
    }

    private static void castSonicWave(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4),
                target -> target != player && target.isAlive() && player.canAttack(target));
        for (LivingEntity target : targets) {
            magicDamage(player, target, 6, ElementType.SOUND);
            knockAway(player, target, 4.0);
        }
        visual(player, SpellDefinition.SONIC_WAVE, player.position().add(0, 1, 0), player.position().add(0, 1, 0), -1, 36);
    }

    private static void applyArmor(LivingEntity target, ResourceLocation armorId, double armor,
                                   ResourceLocation toughnessId, double toughness, ResourceLocation slowId,
                                   double slow, int duration) {
        addTimedModifier(target, Attributes.ARMOR, armorId, armor, AttributeModifier.Operation.ADD_VALUE, duration);
        if (toughnessId != null) addTimedModifier(target, Attributes.ARMOR_TOUGHNESS, toughnessId, toughness,
                AttributeModifier.Operation.ADD_VALUE, duration);
        addTimedModifier(target, Attributes.MOVEMENT_SPEED, slowId, slow,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, duration);
        if (armorId == STONE_ARMOR) ElementReactionSystem.apply(target, target, ElementType.EARTH);
        else if (armorId == IRON_ARMOR) ElementReactionSystem.apply(target, target, ElementType.METAL);
    }

    private static void addTimedModifier(LivingEntity target, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                         ResourceLocation id, double amount, AttributeModifier.Operation operation, int duration) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) return;
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        target.getPersistentData().putLong("DimensionalManaModifier_" + id.getPath(), target.level().getGameTime() + duration);
    }

    private static void cleanupModifier(LivingEntity living, ResourceLocation id, long now) {
        String key = "DimensionalManaModifier_" + id.getPath();
        long until = living.getPersistentData().getLong(key);
        if (until <= 0 || now < until) return;
        for (AttributeInstance instance : living.getAttributes().getSyncableAttributes()) instance.removeModifier(id);
        living.getPersistentData().remove(key);
    }

    private static void stun(LivingEntity target, int ticks) {
        if (target == null) return;
        STUNNED.put(target.getUUID(), target.level().getGameTime() + ticks);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 9, false, false));
    }

    private static void castSurge(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(GeneratedMod.ARCANE_SURGE, 560, 0, false, true, true));
    }

    private static void tickHeals(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        long now = level.getGameTime();
        Iterator<HealState> iterator = HEALS.iterator();
        while (iterator.hasNext()) {
            HealState state = iterator.next();
            if (state.level() != level || !state.caster().equals(owner.getUUID())) continue;
            Entity entity = level.getEntity(state.target());
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || now > state.expiresAt()) {
                iterator.remove();
                continue;
            }
            if (now >= state.nextAt()) {
                target.heal(state.amountPerTick());
                iterator.remove();
                if (now + state.interval() <= state.expiresAt()) HEALS.add(new HealState(state.caster(), state.target(), level,
                        state.amountPerTick(), state.interval(), now + state.interval(), state.expiresAt()));
                ring(level, target.getBoundingBox().getCenter(), 0.55, 12, 0x67DFA0);
                break;
            }
        }
    }

    /** 火焰、毒素等持续伤害的第一次生效只获得法杖固定增伤的 50%。 */
    private static void queueSecondaryDamage(ServerPlayer caster, LivingEntity target, int delay) {
        double staffDamage = MagicStaffItem.heldStats(caster).spellDamage();
        if (staffDamage <= 0.0) return;
        long now = caster.level().getGameTime();
        SECONDARY_DAMAGE.add(new SecondaryDamageState(caster.getUUID(), target.getUUID(), caster.serverLevel(), delay,
                now + delay, now + delay, staffDamage, true));
    }

    private static void tickSecondaryDamage(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        long now = level.getGameTime();
        Iterator<SecondaryDamageState> iterator = SECONDARY_DAMAGE.iterator();
        while (iterator.hasNext()) {
            SecondaryDamageState state = iterator.next();
            if (state.level() != level || !state.caster().equals(owner.getUUID())) continue;
            Entity entity = level.getEntity(state.target());
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || now > state.expiresAt()) {
                iterator.remove();
                continue;
            }
            if (now < state.nextAt()) continue;
            iterator.remove();
            if (!state.staffBonusAvailable()) continue;
            SpellDamage.hitElement(level, owner, target, ElementType.FIRE.id(), (float)(state.staffDamage() * 0.5));
            break;
        }
    }

    /** 处理眩晕、炎祝、荆棘、冰盾反制；负数表示取消攻击，否则返回调整后的伤害。 */
    public static float modifyIncomingAttack(LivingEntity victim, DamageSource source, float incomingDamage) {
        long now = victim.level().getGameTime();
        float adjustedDamage = incomingDamage;
        if (source.getEntity() instanceof LivingEntity attacker) {
            if (STUNNED.getOrDefault(attacker.getUUID(), 0L) > now || ElementReactionSystem.attackMisses(attacker)) return -1.0F;
            FlameBlessingState flameBlessing = FLAME_BLESSED.get(attacker.getUUID());
            if (flameBlessing != null && flameBlessing.expiresAt() <= now) {
                FLAME_BLESSED.remove(attacker.getUUID());
                flameBlessing = null;
            }
            if (flameBlessing != null && flameBlessing.target().equals(victim.getUUID())
                    && source.getDirectEntity() == attacker) {
                // 合并进本次命中的伤害容器，避免嵌套 hurt 改写无敌帧并吞掉主攻击。
                adjustedDamage += incomingDamage * 0.10F + 2.0F
                        + (flameBlessing.staffBonusAvailable() ? (float)(flameBlessing.staffDamage() * 0.5) : 0.0F);
                if (flameBlessing.staffBonusAvailable()) {
                    FLAME_BLESSED.put(attacker.getUUID(), flameBlessing.useStaffBonus());
                }
                victim.setRemainingFireTicks(60);
                ElementReactionSystem.apply(attacker, victim, ElementType.FIRE);
            }
            ThornState thorn = THORNED.get(attacker.getUUID());
            if (thorn != null && thorn.expiresAt() <= now) {
                THORNED.remove(attacker.getUUID());
                thorn = null;
            }
            if (thorn != null) {
                Entity thornCaster = victim.level() instanceof ServerLevel level ? level.getEntity(thorn.caster()) : null;
                LivingEntity caster = thornCaster instanceof LivingEntity living ? living : victim;
                float damage = 2.0F + (thorn.staffBonusAvailable() ? (float)(thorn.staffDamage() * 0.5) : 0.0F);
                SpellDamage.hit(victim.level(), caster, attacker, damage);
                if (thorn.staffBonusAvailable()) THORNED.put(attacker.getUUID(), thorn.useStaffBonus());
            }
            MobEffectInstance absorption = victim.getEffect(MobEffects.ABSORPTION);
            if (absorption != null && absorption.getAmplifier() == 1 && source.getDirectEntity() == attacker) {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                ElementReactionSystem.apply(victim, attacker, ElementType.ICE);
            }
        }
        return adjustedDamage;
    }

    /** 只有伤害来源背后存在生物攻击者时才打断。 */
    public static boolean interruptByAttack(LivingEntity caster, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == caster) return false;
        CastState interrupted = CASTS.remove(caster.getUUID());
        if (interrupted == null) return false;
        if (caster instanceof ServerPlayer player) {
            shatter(player, interrupted.spell());
            player.displayClientMessage(Component.translatable("message.dimensional_mana.cast_interrupted",
                    Component.translatable(interrupted.spell().translationKey())), true);
            SpellSyncPayload.send(player);
        }
        return true;
    }

    /** 无条件打断当前读条；反魔法区域与魔法抗拒用。 */
    public static void interruptCast(LivingEntity caster) {
        CastState interrupted = CASTS.remove(caster.getUUID());
        if (interrupted == null) return;
        if (caster instanceof ServerPlayer player) {
            shatter(player, interrupted.spell());
            player.displayClientMessage(Component.translatable("message.dimensional_mana.cast_interrupted",
                    Component.translatable(interrupted.spell().translationKey())), true);
            SpellSyncPayload.send(player);
        }
    }

    /** 退出、死亡和换维度时不允许旧读条在新世界继续结算。 */
    public static void clearTransientState(ServerPlayer player) {
        NewSpellSystem.clear(player);
        UUID id = player.getUUID();
        CastState cast = CASTS.remove(id);
        if (cast != null) SpellVisualPayload.send(player, cast.spell(), SpellVisualPayload.CANCEL, -1,
                player.position(), player.position(), 1);
        TARGETING.remove(id);
        // 保留冷却，避免通过下线或换维度绕过冷却。
        HEALS.removeIf(heal -> heal.caster().equals(id) || heal.target().equals(id));
        SECONDARY_DAMAGE.removeIf(damage -> damage.caster().equals(id) || damage.target().equals(id));
        FLAME_BLESSED.remove(id);
        NO_FALL.remove(id);
        STUNNED.remove(id);
        SpellSyncPayload.send(player);
    }

    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        CASTS.clear(); COOLDOWNS.clear(); TARGETING.clear(); STUNNED.clear();
        ManaCheats.clear();
        NO_FALL.clear(); FLAME_BLESSED.clear(); THORNED.clear(); HEALS.clear(); SECONDARY_DAMAGE.clear();
        ElementReactionSystem.clearAll();
        MobSkills.clearAll();
        MobCombatSystem.clearAll();
        NewSpellSystem.clearAll();
        SpellEnvironmentSystem.clearAll();
    }

    public static boolean isTargeting(ServerPlayer player) { return TARGETING.containsKey(player.getUUID()); }
    public static boolean hasSelectedTarget(ServerPlayer player) {
        TargetState state = TARGETING.get(player.getUUID());
        return state != null && !state.selected().isEmpty();
    }

    public static CastSnapshot snapshot(ServerPlayer player) {
        CastState cast = CASTS.get(player.getUUID());
        return cast == null ? new CastSnapshot("", 0, 0)
                : new CastSnapshot(cast.spell().id(), Math.max(0, cast.remainingTicks()), cast.totalTicks());
    }

    public static int cooldownRemaining(ServerPlayer player, SpellDefinition spell) {
        // 作弊开关：关闭冷却后所有法术都按“已就绪”处理，HUD 上的冷却数字也一起消失。
        if (!ManaCheats.cooldownEnabled(player)) return 0;
        long until = COOLDOWNS.getOrDefault(player.getUUID(), Map.of()).getOrDefault(spell, 0L);
        return (int)Math.max(0L, until - player.level().getGameTime());
    }

    /** 从当前时刻开始计算该法术的冷却；深渊龙击等持续型法术在真正结束后才调用。 */
    public static void startCooldown(ServerPlayer player, SpellDefinition spell) {
        startCooldown(player, spell, spell.cooldownTicks());
    }

    /** 指定长度的冷却：万法千陨每追加一块石块就延长 2 秒。 */
    public static void startCooldown(ServerPlayer player, SpellDefinition spell, int ticks) {
        Map<SpellDefinition, Long> mine = COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        if (!ManaCheats.cooldownEnabled(player)) { mine.remove(spell); return; }
        // 迅捷施法手套：法术冷却 -15%（独立乘区，追加的石块延长同样一并减免）。
        int adjusted = (int)Math.round(Math.max(0, ticks) * MagicEquipment.cooldownMultiplier(player)
                * RelicEffects.divinationCooldownMultiplier(player, spell));
        mine.put(spell, player.level().getGameTime() + Math.max(0, adjusted));
    }

    /** 立刻清空该玩家所有法术的冷却，用于作弊指令。 */
    public static void clearCooldowns(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
    }

    private static void magicDamage(ServerPlayer player, LivingEntity target, float damage, ElementType element) {
        float calculated = SpellAttributes.calculateSpellDamage(player, target, damage);
        calculated = element == null ? ElementReactionSystem.arcaneDamage(player, target, calculated)
                : ElementReactionSystem.elementalDamage(player, target, element, calculated);
        // 全部法术伤害统一走这里：无视目标的受击冷却（无敌帧），并按元素落地成对应的伤害类型。
        String elementId = element == null ? MagicEquipment.ARCANE : element.id();
        if (SpellDamage.hitElement(player.serverLevel(), player, target, elementId, calculated))
            player.setLastHurtMob(target);
    }

    private static void knockAway(LivingEntity source, LivingEntity target, double power) {
        Vec3 delta = target.position().subtract(source.position());
        if (delta.horizontalDistanceSqr() < 0.001) delta = source.getLookAngle();
        Vec3 horizontal = new Vec3(delta.x, 0, delta.z).normalize();
        target.knockback(power * 0.5, -horizontal.x, -horizontal.z);
    }

    private static void ring(ServerLevel level, Vec3 center, double radius, int points, int color) {
        circle(level, center, radius, points, color, 0);
    }

    private static void circle(ServerLevel level, Vec3 center, double radius, int points, int color, double phase) {
        DustParticleOptions dust = dust(color);
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0 * i / points;
            level.sendParticles(dust, center.x + Math.cos(angle) * radius, center.y,
                    center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    private static double projection(Vec3 start, Vec3 direction, Vec3 point) {
        return point.subtract(start).dot(direction);
    }

    private static LivingEntity living(Entity entity) { return entity instanceof LivingEntity value ? value : null; }
    private static DustParticleOptions dust(int rgb) {
        return dust(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }
    private static DustParticleOptions dust(float red, float green, float blue) {
        return new DustParticleOptions(new Vector3f(red, green, blue), 1.0F);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, path); }

    private static void showLearnedTitle(ServerPlayer player, SpellDefinition spell) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 5));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(
                "message.dimensional_mana.spell_learned_title", Component.translatable(spell.translationKey()))));
    }

    @FunctionalInterface private interface TargetAction { void apply(LivingEntity target); }
    private SpellCasting() { }
}
