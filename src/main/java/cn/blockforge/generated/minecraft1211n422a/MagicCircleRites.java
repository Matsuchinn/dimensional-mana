package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 第 4 阶段的「正式运行」执行器：刻印石板贴出来的实体锚点在这里真正干活。
 *
 * <p>一条链路是：<b>启动</b>（扣启动成本，整组连着一起开）→ 每 5 秒<b>维持</b>扣一次 →
 * 触发（空手右键 / 红石沿 / 计时）扣触发成本 → 逐块<b>结算输出</b>。</p>
 *
 * <p>输出块的语义按设计文档第 3 节定的三档走，不需要玩家额外记按钮：</p>
 * <ul>
 *   <li>染料槽放<b>黄绿色染料</b>（生长 / 唤灵）→ 召唤，召唤什么由元素槽决定；</li>
 *   <li>元素槽放<b>金元素粉</b> → 挖矿，语义槽放青金石粉就带精准采集；</li>
 *   <li>其它情况按元素槽打伤害 / 治疗 / 上火 / 减速 / 清负面效果。</li>
 * </ul>
 *
 * <p>所有硬限制都从 {@link MagicCircleConfig} 读：召唤黑白名单、Boss 超高标准、
 * 同时在场上限与生命周期、挖矿黑白名单、区块加载、单次与每分钟限额、PvP 与试运行伤害开关。</p>
 *
 * <p>带元素粉的输出块打出去的是<b>真正带元素的法术伤害</b>：命中前先在目标身上挂这个元素的附着，
 * 再按 {@link Reaction#priorityOrder()} 判定反应（蒸发、融化、超载、感电、冻结……），反应算完的
 * 伤害接在同一发上落地。法阵的反应不受「同一目标 0.5 秒内只反应一次」的全局冷却限制，
 * 同一刻结算的每一块输出都会各自按优先级重新判定一次。</p>
 */
public final class MagicCircleRites {
    /** 维持成本多久扣一次（刻）：20 刻 = 1 秒。 */
    private static final int UPKEEP_INTERVAL = 20;
    /** 两次触发之间的最短间隔，防止连点。 */
    private static final int TRIGGER_COOLDOWN = 10;
    /** 计时触发块的默认周期（刻）：200 刻 = 10 秒。 */
    private static final int TIMER_PERIOD = 200;
    /** 召唤物身上的两个标记：一个是「这是法阵召唤物」，一个是「到期刻」。 */
    private static final String SUMMON_TAG = "dm_magic_circle_summon";
    private static final String SUMMON_OWNER_TAG = "dm_summon_anchor";
    private static final String SUMMON_UNTIL_TAG = "dm_summon_until";
    /** 黄绿色染料 = 唤灵；金元素粉 = 挖矿；青金石粉 = 精准采集。 */
    private static final String DYE_SUMMON = "minecraft:lime_dye";
    private static final String POWDER_LAPIS = "dimensional_mana:lapis_dust";
    /** 需要「超高标准」才允许召唤的 Boss（文档 14.1）。 */
    private static final Set<String> BOSS_IDS = Set.of(
            "minecraft:wither", "minecraft:ender_dragon", "minecraft:warden", "minecraft:elder_guardian");

    // ---- 同时运行的法阵上限：基础 1，从第 5 级起每级 +1（等级部分最多 +10），
    //      另外玩家魔素上限每 1 万再 +1，魔素部分不封顶。 ----
    /** 谁都没有时的保底数量：一个人至少能开 1 个阵。 */
    public static final int BASE_RUNNING_LIMIT = 1;
    /** 从第几级开始，每升一级多一个法阵名额。 */
    public static final int LEVEL_LIMIT_FROM = 5;
    /** 魔法等级最多能提供多少个名额（满级 = 10 个，加上基础 1 共 11 个）。 */
    public static final int LEVEL_LIMIT_CAP = 10;
    /** 玩家魔素上限每这么多点，再多一个名额。 */
    public static final double MANA_PER_EXTRA_LIMIT = 10_000.0;
    /** 远程轮盘扫描多远之内的锚点（格）。 */
    public static final int REMOTE_RADIUS = 96;
    /** 远程轮盘最多列几个，太多就只列最近的。 */
    public static final int REMOTE_MAX_ENTRIES = 16;

    private MagicCircleRites() {}

    // ================================================================ 开关：启动 / 停止

    /** 启动或停止整组（沿异面连线连在一起的刻印锚点一起开）。 */
    public static void toggleRun(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) return;
        if (!entity.sealed()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.slate.need_slate").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (entity.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.anchor_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        List<MagicCircleProjectionBlockEntity> group = group(level, pos, entity);
        if (entity.running()) {
            for (MagicCircleProjectionBlockEntity member : group) member.setRunning(false);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.rite.stopped", group.size())
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        // 「这一下真正要开起来几个」：组里已经在跑的不能算进名额、也不能再收一次启动费，
        // 否则连着的两个阵只要有一个还开着，另一个就永远启动失败。
        int adding = 0;
        int total = 0;
        for (MagicCircleProjectionBlockEntity member : group) {
            if (member.running() || member.isEmpty()) continue;
            MagicCircleCost.Evaluation evaluation = member.evaluate();
            if (!evaluation.problems().isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.rite.cannot_start", member.getBlockPos().toShortString())
                        .withStyle(ChatFormatting.RED), false);
                for (String problem : evaluation.problems()) {
                    player.displayClientMessage(Component.translatable(problem).withStyle(ChatFormatting.RED), false);
                }
                return;
            }
            adding++;
            total += evaluation.startMana();
        }
        int limit = runningLimit(player);
        if (runningCount(level, player) + adding > limit) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.rite.too_many_running",
                    limit, levelLimitBonus(player), manaLimitBonus(player), magicLevel(player))
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!payMana(level, entity, player, total, true)) return;
        for (MagicCircleProjectionBlockEntity member : group) {
            // 先认主人再开：开的那一下要按主人登记进维度档案（停机键 / 远程轮盘要靠它）。
            member.setOwner(player.getUUID());
            member.setRunning(true);
            member.sync();
        }
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.rite.started", adding, total,
                entity.evaluate().upkeepMana()).withStyle(ChatFormatting.GREEN), false);
        // 启动后立刻演示一发：只回一句「启动成功」的话，玩家会以为阵没跑起来。
        // 这一发走正常的触发结算（该扣的触发成本照扣），所以看到的光效 / 伤害都是真的。
        trigger(level, pos, entity, player, false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.rite.trigger_hint").withStyle(ChatFormatting.GRAY), false);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 1.4F);
    }

    // ================================================================ tick：维持 / 计时 / 红石

    /** 方块实体的 ticker：只管刻印 + 运行中的锚点，其它立刻返回。 */
    public static void tickAnchor(ServerLevel level, BlockPos pos, BlockState state,
                                  MagicCircleProjectionBlockEntity entity) {
        if (!entity.running()) return;
        long now = level.getGameTime();
        if (now % UPKEEP_INTERVAL == 0) {
            // 上个版本启动的阵在维度档案里没有登记（那时候还没这东西）：每 20 刻顺手补一次，
            // 停机键和远程轮盘才找得到它。已经登记过的这一步什么都不做。
            MagicCircleNetworks.get(level).markRunning(pos, entity.owner());
            int upkeep = entity.evaluate().upkeepMana();
            if (upkeep > 0) payUpkeep(level, entity, upkeep);
        }
        if (usesTimer(entity) && now % TIMER_PERIOD == 0) {
            trigger(level, pos, entity, null, false);
        }
    }

    /** 维持成本：主人不在线就停止运行，免得没人付账的阵一直转。 */
    private static void payUpkeep(ServerLevel level, MagicCircleProjectionBlockEntity entity, int upkeep) {
        ServerPlayer owner = ownerPlayer(level, entity);
        if (owner == null) {
            entity.setRunning(false);
            return;
        }
        if (owner.isCreative() && MagicCircleConfig.CREATIVE_FREE_POWDER.get()) return;
        if (!ManaData.spendMana(owner, upkeep)) {
            entity.setRunning(false);
            owner.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.rite.upkeep_failed", upkeep)
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    /** 红石沿触发（由方块的 neighborChanged 调进来）。 */
    public static void triggerBySignal(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity) {
        long now = level.getGameTime();
        if (entity.lastTriggerTick() >= 0 && now - entity.lastTriggerTick() < TRIGGER_COOLDOWN) return;
        trigger(level, pos, entity, null, false);
    }

    // ================================================================ 触发：结算输出

    /**
     * 打一发。
     *
     * @param actor 触发的人（手动触发时是玩家；红石 / 计时触发是 null，改由主人付账）
     * @param test  试运行：只放光效，不产生战利品、不真的召唤、不破坏方块
     */
    public static void trigger(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity,
                               ServerPlayer actor, boolean test) {
        if (!test && !entity.running()) {
            if (actor != null) {
                actor.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.rite.not_running").withStyle(ChatFormatting.YELLOW),
                        false);
            }
            return;
        }
        long now = level.getGameTime();
        if (entity.lastTriggerTick() >= 0 && now - entity.lastTriggerTick() < TRIGGER_COOLDOWN) {
            if (actor != null) {
                actor.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.rite.cooldown").withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }
        MagicCircleCost.Evaluation evaluation = entity.evaluate();
        if (evaluation.nodeCount() == 0 || !evaluation.problems().isEmpty()) {
            if (actor != null) {
                for (String problem : evaluation.problems()) {
                    actor.displayClientMessage(Component.translatable(problem).withStyle(ChatFormatting.RED), false);
                }
            }
            return;
        }
        int cost = test
                ? (int) Math.ceil(evaluation.startMana() * MagicCircleConfig.TEST_RUN_COST_RATIO.get())
                : evaluation.triggerMana();
        if (!test && !payMana(level, entity, actor, cost, actor != null)) return;
        entity.markTrigger(now);
        entity.markTestRun(now);
        entity.sync();

        Direction facing = entity.getBlockState().getValue(MagicCircleProjectionBlock.FACING);
        double strength = evaluation.strength();
        boolean pvp = MagicCircleConfig.ALLOW_PVP.get();
        boolean playerDamage = !test || MagicCircleConfig.ALLOW_PLAYER_DAMAGE_IN_TEST.get();
        Outcome outcome = new Outcome();
        // 元素反应得记在一个「施法者」头上：手动触发算触发的人，红石 / 计时触发算阵的主人
        // （所以阵主身上的元素戒指、共鸣核心这些增伤/加成也按他算）。两个都没有就只打裸伤害。
        ServerPlayer caster = actor != null ? actor : ownerPlayer(level, entity);
        for (MagicCircleNetwork.Node node : entity.nodes()) {
            if (node.type() != MagicCircleBlockType.OUTPUT) continue;
            settleOutput(level, pos, facing, entity, node, actor, caster, strength, test, pvp, playerDamage,
                    outcome);
        }
        visualPulse(level, pos, entity);
        if (actor != null) {
            actor.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.rite.triggered",
                    test ? 0 : cost, String.format(Locale.ROOT, "%.2f", strength))
                    .withStyle(ChatFormatting.GREEN), false);
            if (outcome.hasAny()) {
                actor.displayClientMessage(Component.literal(outcome.describe()).withStyle(ChatFormatting.LIGHT_PURPLE),
                        false);
            }
        }
    }

    /** 输出块的结算次数，最后拼成一行聊天提示。 */
    private static final class Outcome {
        int hurt, heal, summon, mined, blocked, reactions;

        boolean hasAny() { return hurt + heal + summon + mined + blocked + reactions > 0; }

        String describe() {
            StringBuilder builder = new StringBuilder();
            if (hurt > 0) builder.append("伤害 ×").append(hurt);
            if (reactions > 0) builder.append(builder.length() > 0 ? "、" : "").append("反应 ×").append(reactions);
            if (heal > 0) builder.append(builder.length() > 0 ? "、" : "").append("治疗 ×").append(heal);
            if (summon > 0) builder.append(builder.length() > 0 ? "、" : "").append("召唤 ×").append(summon);
            if (mined > 0) builder.append(builder.length() > 0 ? "、" : "").append("挖掘 ×").append(mined);
            if (blocked > 0) builder.append(builder.length() > 0 ? "、" : "").append("被拦截 ×").append(blocked);
            return builder.toString();
        }
    }

    private static void settleOutput(ServerLevel level, BlockPos pos, Direction facing,
                                     MagicCircleProjectionBlockEntity entity, MagicCircleNetwork.Node node,
                                     ServerPlayer actor, ServerPlayer caster, double strength, boolean test,
                                     boolean pvp, boolean playerDamage, Outcome outcome) {
        ElementType element = MagicCirclePowders.elementOf(node.element());
        if (DYE_SUMMON.equals(node.dye())) {
            outcome.summon += summon(level, pos, facing, entity, node, element, strength, test);
            return;
        }
        if (element == ElementType.METAL) {
            outcome.mined += mine(level, pos, entity, node, strength, test);
            return;
        }
        outcome.hurt += elemental(level, pos, facing, node, element, actor, caster, strength, test, pvp,
                playerDamage, outcome);
    }

    /** 元素输出：按元素槽决定打什么、打谁。返回命中数。 */
    private static int elemental(ServerLevel level, BlockPos pos, Direction facing, MagicCircleNetwork.Node node,
                                 ElementType element, ServerPlayer actor, ServerPlayer caster, double strength,
                                 boolean test, boolean pvp, boolean playerDamage, Outcome outcome) {
        double radius = Math.min(8.0, 2.5 + 1.2 * Math.max(0.0, strength - 1.0));
        Vec3 center = surfaceCenter(level, pos, facing, 1.5);
        AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        boolean healing = element == ElementType.WATER || element == ElementType.NATURE
                || element == ElementType.LIGHT;
        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target.isSpectator() || target.isDeadOrDying()) continue;
            if (target.distanceToSqr(center) > radius * radius) continue;
            boolean isPlayer = target instanceof Player;
            if (isPlayer && !playerDamage) continue;
            if (isPlayer && target != actor && !pvp) continue;
            if (isPlayer && target == actor) {
                if (healing) {
                    target.heal((float) (3.0 * strength));
                    outcome.heal++;
                    hits++;
                }
                continue;
            }
            if (healing) {
                if (target instanceof Player || !(target instanceof Mob)) {
                    target.heal((float) (3.0 * strength));
                    if (element == ElementType.NATURE) {
                        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
                    }
                    if (element == ElementType.LIGHT) clearHarmful(target);
                    outcome.heal++;
                    hits++;
                }
                continue;
            }
            if (!test || playerDamage) {
                float damage = damageOf(element, strength);
                if (damage > 0) {
                    // 元素槽有粉时先过元素系统：给目标挂上这个元素的附着、按反应优先级结算反应，
                    // 反应（蒸发、融化、超载、感电……）算完的伤害再接在下面这一发上。
                    // 法阵一次触发会连着打好几块，所以这里显式绕过「同一目标 0.5 秒只反应一次」
                    // 的全局冷却：每一块都按反应优先级重新判定，频率只受法阵自己的触发限制。
                    if (element != null && caster != null) {
                        // 最后一个参数是 PvP 保护：关闭 PvP 时，超载的爆炸和感电的连锁也不波及玩家。
                        ElementReactionSystem.ElementalHit resolved = ElementReactionSystem.elementalHit(
                                caster, target, element, damage, true, !pvp);
                        damage = resolved.damage();
                        if (resolved.reaction() != null) outcome.reactions++;
                    }
                    // 伤害类型仍是原版魔法伤害（护甲、保护、抗性照常算），
                    // 走 SpellDamage.apply 只是为了临时让开受击冷却，让每一块都吃满。
                    if (damage > 0 && SpellDamage.apply(target, level.damageSources().magic(), damage)) hits++;
                }
                applySideEffect(target, element, strength);
            }
            outcome.hurt++;
        }
        return hits;
    }

    private static float damageOf(ElementType element, double strength) {
        double base = switch (element == null ? ElementType.FIRE : element) {
            case FIRE -> 5.0;
            case ICE -> 3.0;
            case LIGHTNING -> 6.0;
            case WIND -> 2.5;
            case EARTH -> 4.0;
            case SOUND -> 3.5;
            case POISON -> 2.0;
            default -> 4.0;
        };
        return (float) (base * strength);
    }

    private static void applySideEffect(LivingEntity target, ElementType element, double strength) {
        if (element == null) return;
        switch (element) {
            case FIRE -> target.setRemainingFireTicks(80);
            case ICE -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            case EARTH -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
            case WIND -> {
                Vec3 push = target.position().subtract(Vec3.atCenterOf(target.blockPosition())).normalize();
                double force = 0.4 * strength;
                target.push(push.x * force, 0.25, push.z * force);
            }
            case LIGHTNING -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            default -> { }
        }
    }

    private static void clearHarmful(LivingEntity target) {
        for (var effect : List.of(MobEffects.POISON, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS,
                MobEffects.WITHER, MobEffects.BLINDNESS, MobEffects.CONFUSION)) {
            target.removeEffect(effect);
        }
    }

    // ================================================================ 召唤输出

    /** 召唤：黑名单直接哑火，白名单非空只放白名单，Boss 走超高标准。返回实际召唤数。 */
    private static int summon(ServerLevel level, BlockPos pos, Direction facing,
                              MagicCircleProjectionBlockEntity entity, MagicCircleNetwork.Node node,
                              ElementType element, double strength, boolean test) {
        if (!MagicCircleConfig.SUMMON_ENABLED.get()) return 0;
        String entityId = summonIdOf(element);
        if (entityId == null) return 0;
        if (isBlacklisted(MagicCircleConfig.SUMMON_BLACKLIST.get(), entityId)) {
            if (ownerPlayer(level, entity) != null) {
                ownerPlayer(level, entity).displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.rite.summon_banned", entityId)
                        .withStyle(ChatFormatting.RED), false);
            }
            return 0;
        }
        List<? extends String> whitelist = MagicCircleConfig.SUMMON_WHITELIST.get();
        if (!whitelist.isEmpty() && !whitelist.contains(entityId)) {
            if (ownerPlayer(level, entity) != null) {
                ownerPlayer(level, entity).displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.rite.summon_not_allowed", entityId)
                        .withStyle(ChatFormatting.RED), false);
            }
            return 0;
        }
        if (BOSS_IDS.contains(entityId) && !bossRequirementMet(level, entity)) {
            if (ownerPlayer(level, entity) != null) {
                ownerPlayer(level, entity).displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.rite.summon_boss_locked", entityId)
                        .withStyle(ChatFormatting.RED), false);
            }
            return 0;
        }
        if (countSummons(level, entity) >= MagicCircleConfig.MAX_SUMMONS_PER_NETWORK.get()) {
            if (ownerPlayer(level, entity) != null) {
                ownerPlayer(level, entity).displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.rite.summon_full",
                        MagicCircleConfig.MAX_SUMMONS_PER_NETWORK.get()).withStyle(ChatFormatting.RED), false);
            }
            return 0;
        }
        if (test) return 0; // 试运行不真的召唤，避免刷怪
        EntityType<?> type = EntityType.byString(entityId).orElse(null);
        if (type == null) return 0;
        int amount = Math.max(1, Math.min(3, (int) Math.round(strength / 1.5)));
        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            Entity created = type.create(level);
            if (created == null) continue;
            Vec3 at = surfaceCenter(level, pos, facing, 1.2 + i);
            created.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
            if (created instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            CompoundTag data = created.getPersistentData();
            data.putBoolean(SUMMON_TAG, true);
            data.putLong(SUMMON_OWNER_TAG, pos.asLong());
            data.putLong(SUMMON_UNTIL_TAG,
                    level.getGameTime() + 20L * MagicCircleConfig.SUMMON_LIFETIME_SECONDS.get());
            level.addFreshEntity(created);
            spawned++;
        }
        return spawned;
    }

    private static String summonIdOf(ElementType element) {
        if (element == null) return "minecraft:zombie";
        return switch (element) {
            case FIRE -> "minecraft:blaze";
            case WATER -> "minecraft:drowned";
            case ICE -> "minecraft:stray";
            case LIGHTNING -> "minecraft:husk";
            case WIND -> "minecraft:breeze";
            case EARTH -> "minecraft:iron_golem";
            case NATURE -> "dimensional_mana:vine_servant";
            case LIGHT -> "minecraft:allay";
            case POISON -> "minecraft:cave_spider";
            case SOUND -> "minecraft:vex";
            case METAL -> "minecraft:zombie";
        };
    }

    /** Boss 的超高标准：基座数、层数、启动魔素、超导粉数量四项都要够（文档 14.1）。 */
    private static boolean bossRequirementMet(ServerLevel level, MagicCircleProjectionBlockEntity entity) {
        MagicCircleCost.Evaluation evaluation = entity.evaluate();
        if (entity.baseW() * entity.baseH() < 6) return false;
        if (evaluation.filledLayers() < 3) return false;
        if (evaluation.startMana() < 50_000) return false;
        int superconductors = 0;
        for (MagicCircleNetwork.Node node : entity.nodes()) {
            if ("dimensional_mana:superconducting_crystal_dust".equals(node.power())) superconductors++;
        }
        return superconductors >= 16;
    }

    private static int countSummons(ServerLevel level, MagicCircleProjectionBlockEntity entity) {
        AABB box = AABB.ofSize(Vec3.atCenterOf(entity.getBlockPos()), 64, 64, 64);
        int count = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            CompoundTag data = living.getPersistentData();
            if (!data.getBoolean(SUMMON_TAG)) continue;
            if (data.getLong(SUMMON_OWNER_TAG) == entity.getBlockPos().asLong()) count++;
        }
        return count;
    }

    /** 召唤物的生命周期：到期就消散（文档 14.2 第 4 条）。 */
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        CompoundTag data = living.getPersistentData();
        long until = data.getLong(SUMMON_UNTIL_TAG);
        if (until <= 0L) return;
        if (living.level().getGameTime() < until) return;
        data.remove(SUMMON_UNTIL_TAG);
        data.remove(SUMMON_TAG);
        living.discard();
    }

    // ================================================================ 挖矿输出

    /** 挖矿：以锚点为中心挖一小片，黑白名单 / 区块 / 单次 / 每分钟限额都在这里拦。 */
    private static int mine(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity,
                           MagicCircleNetwork.Node node, double strength, boolean test) {
        if (!MagicCircleConfig.MINING_ENABLED.get() || test) return 0;
        long now = level.getGameTime();
        int budget = Math.min(MagicCircleConfig.MAX_BLOCKS_PER_TRIGGER.get(), entity.miningBudget(now));
        if (budget <= 0) return 0;
        int radius = 1 + Math.min(3, (int) Math.floor(strength));
        boolean silk = MagicCircleConfig.ALLOW_SILK_TOUCH.get() && POWDER_LAPIS.equals(node.semantic());
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius),
                pos.offset(radius, radius, radius))) {
            if (targets.size() >= budget) break;
            if (candidate.equals(pos)) continue;
            if (!level.isInWorldBounds(candidate)) continue;
            if (MagicCircleConfig.REQUIRE_LOADED_CHUNK.get() && !level.hasChunkAt(candidate)) continue;
            BlockState state = level.getBlockState(candidate);
            if (state.isAir()) continue;
            if (state.getBlock() instanceof MagicCircleProjectionBlock
                    || state.getBlock() instanceof MagicCircleBaseBlock) continue;
            String id = builtInId(state.getBlock());
            if (isBlacklisted(MagicCircleConfig.MINING_BLOCK_BLACKLIST.get(), id)) continue;
            List<? extends String> whitelist = MagicCircleConfig.MINING_BLOCK_WHITELIST.get();
            if (!whitelist.isEmpty() && !whitelist.contains(id)) continue;
            if (state.getDestroySpeed(level, candidate) < 0.0F) continue;
            if (state.hasBlockEntity()) continue;
            targets.add(candidate.immutable());
        }
        if (targets.isEmpty()) return 0;
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if (silk) tool.enchant(level.holderLookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        boolean toInventory = MagicCircleConfig.DROP_TO_INVENTORY.get();
        int mined = 0;
        for (BlockPos target : targets) {
            BlockState state = level.getBlockState(target);
            if (state.isAir()) continue;
            BlockEntity blockEntity = level.getBlockEntity(target);
            List<ItemStack> drops = Block.getDrops(state, level, target, blockEntity, null, tool);
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (toInventory) {
                ServerPlayer owner = ownerPlayer(level, entity);
                for (ItemStack drop : drops) {
                    if (owner == null || !owner.getInventory().add(drop)) Block.popResource(level, target, drop);
                }
            } else {
                for (ItemStack drop : drops) Block.popResource(level, target, drop);
            }
            mined++;
        }
        entity.addMining(mined, now);
        return mined;
    }

    // ================================================================ 异面连线

    /** 连一条线：扣 1 份超导粉 + 10 魔素，双向都记。调用方负责先检查手持物。 */
    public static boolean link(ServerLevel level, BlockPos from, BlockPos to, ServerPlayer player) {
        if (from.equals(to)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.same").withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        double distance = Math.sqrt(from.distSqr(to));
        int maxDistance = MagicCircleConfig.MAX_LINK_DISTANCE.get();
        if (distance > maxDistance) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.too_far",
                    String.format(Locale.ROOT, "%.1f", distance), maxDistance)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!(level.getBlockEntity(from) instanceof MagicCircleProjectionBlockEntity a)
                || !(level.getBlockEntity(to) instanceof MagicCircleProjectionBlockEntity b)) {
            return false;
        }
        if (a.linkedTo(to)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.exists").withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        int maxLinks = MagicCircleConfig.MAX_LINKS_PER_ANCHOR.get();
        if (a.linkCount() >= maxLinks || b.linkCount() >= maxLinks) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.full", maxLinks).withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!player.isCreative() && !ManaData.spendMana(player, LINK_MANA)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.no_mana", LINK_MANA)
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        a.addLink(to);
        b.addLink(from);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.link.created",
                String.format(Locale.ROOT, "%.1f", distance),
                from.toShortString(), to.toShortString()).withStyle(ChatFormatting.AQUA), false);
        level.playSound(null, from, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.6F);
        return true;
    }

    private static final int LINK_MANA = 10;

    /** 从某个锚点断开所有连线（两边都删）。 */
    public static void unlinkAll(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity) {
        for (BlockPos other : List.copyOf(entity.links())) {
            if (level.getBlockEntity(other) instanceof MagicCircleProjectionBlockEntity neighbor) {
                neighbor.removeLink(pos);
            }
        }
        entity.clearLinks();
    }

    /** 断开与一个锚点的连线。 */
    public static boolean unlink(ServerLevel level, BlockPos from, BlockPos to, ServerPlayer player) {
        if (!(level.getBlockEntity(from) instanceof MagicCircleProjectionBlockEntity a)
                || !(level.getBlockEntity(to) instanceof MagicCircleProjectionBlockEntity b)) {
            return false;
        }
        if (!a.linkedTo(to)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.none").withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        a.removeLink(to);
        b.removeLink(from);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.link.removed").withStyle(ChatFormatting.YELLOW), false);
        return true;
    }

    /** 连同异面连线连在一起的整组刻印锚点（广度优先，最多 64 个，防走飞）。 */
    public static List<MagicCircleProjectionBlockEntity> group(ServerLevel level, BlockPos start,
                                                               MagicCircleProjectionBlockEntity root) {
        List<MagicCircleProjectionBlockEntity> group = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && group.size() < 64) {
            BlockPos current = queue.poll();
            if (!(level.getBlockEntity(current) instanceof MagicCircleProjectionBlockEntity entity)) continue;
            group.add(entity);
            for (BlockPos next : entity.links()) {
                if (seen.add(next) && level.getBlockEntity(next) instanceof MagicCircleProjectionBlockEntity) {
                    queue.add(next);
                }
            }
        }
        if (group.isEmpty()) group.add(root);
        return group;
    }

    // ================================================================ 触发方式识别

    /** 这一组功能块里有没有「红石触发」：触发块的语义槽放红石粉。 */
    public static boolean usesRedstone(MagicCircleProjectionBlockEntity entity) {
        for (MagicCircleNetwork.Node node : entity.nodes()) {
            if (node.type() == MagicCircleBlockType.TRIGGER && "minecraft:redstone".equals(node.semantic())) {
                return true;
            }
        }
        return false;
    }

    /** 这一组功能块里有没有「计时触发」：触发块的染料槽放黄色染料。 */
    public static boolean usesTimer(MagicCircleProjectionBlockEntity entity) {
        for (MagicCircleNetwork.Node node : entity.nodes()) {
            if (node.type() == MagicCircleBlockType.TRIGGER && "minecraft:yellow_dye".equals(node.dye())) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ 工具

    private static ServerPlayer ownerPlayer(ServerLevel level, MagicCircleProjectionBlockEntity entity) {
        if (entity.owner() == null) return null;
        return level.getServer().getPlayerList().getPlayer(entity.owner());
    }

    /** 当前有几个法阵在跑（按主人算），用来卡「最多同时开几个」。 */
    private static int runningCount(ServerLevel level, ServerPlayer player) {
        return MagicCircleNetworks.get(level).countRunning(player.getUUID());
    }

    // ---------------------------------------------------------------- 同时运行的法阵上限

    /** 玩家的魔法等级（0 = 还没入门）。 */
    public static int magicLevel(ServerPlayer player) {
        return Math.max(0, MagicProgression.get(player).magicLevel());
    }

    /** 魔法等级提供的名额：第 5 级起每级 +1，最多 10 个（满级 15 级正好拿到 10 个）。 */
    public static int levelLimitBonus(ServerPlayer player) {
        return Math.min(LEVEL_LIMIT_CAP, Math.max(0, magicLevel(player) - (LEVEL_LIMIT_FROM - 1)));
    }

    /** 魔素上限提供的名额：每 1 万点 +1，不封顶。 */
    public static int manaLimitBonus(ServerPlayer player) {
        double maxMana = ManaData.get(player).maxMana();
        if (!(maxMana > 0.0)) return 0;
        return (int) Math.floor(maxMana / MANA_PER_EXTRA_LIMIT);
    }

    /**
     * 这个玩家最多能同时开几个法阵：基础 1 + 魔法等级（第 5 级起每级 +1，最多 +10）
     * + 魔素上限每 1 万 +1。魔素那部分不设上限，所以后期可以开到很多个。
     */
    public static int runningLimit(ServerPlayer player) {
        return BASE_RUNNING_LIMIT + levelLimitBonus(player) + manaLimitBonus(player);
    }

    /** 屏幕 / 聊天栏里那句「现在 2 / 5（基础 1 + 等级 1 + 魔素 3）」。 */
    public static Component limitSummary(ServerPlayer player) {
        return Component.translatable("message.dimensional_mana.magiccircle.rite.limit_summary",
                runningCount(player.serverLevel(), player), runningLimit(player),
                BASE_RUNNING_LIMIT, levelLimitBonus(player), manaLimitBonus(player));
    }

    /**
     * 一键停机（停机键 / 轮盘中间那颗 / {@code stopall} 指令都走这里）：
     * <b>所有维度</b>里登记在这个玩家名下的锚点全关——在主世界按一下，下界、末地开着的阵也停。
     *
     * <p>为什么按维度一份存档却要跨维度找：运行记录本来就分维度存，
     * 但「拍急停」的意思是「我现在不想让任何阵继续跑」，让玩家为了停机跑去下界毫无道理。</p>
     *
     * @return 真正关掉了几组锚点
     */
    public static int stopAll(ServerPlayer player) {
        int stopped = 0;
        for (ServerLevel level : player.serverLevel().getServer().getAllLevels()) {
            stopped += stopAll(level, player);
        }
        return stopped;
    }

    /**
     * 一键停机：把当前维度里登记在案的、属于这个玩家的锚点全部关掉（不管它现在离得多远）。
     *
     * <p>区块没加载时方块实体根本不存在，所以先按坐标把区块读出来再关；读不出来的
     * （锚点已经被挖掉 / 存档被删）就把登记清掉，别留脏数据。</p>
     *
     * @return 真正关掉了几组锚点
     */
    public static int stopAll(ServerLevel level, ServerPlayer player) {
        MagicCircleNetworks networks = MagicCircleNetworks.get(level);
        int stopped = 0;
        for (BlockPos pos : networks.runningAnchorsOf(player.getUUID())) {
            if (!level.isLoaded(pos)) {
                // 强制读一下区块：不读就改不到里面的方块实体，玩家走回来时阵会自己接着跑。
                try {
                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                } catch (RuntimeException ignored) {
                    // 区块读不出来（世界边界 / 存档问题）就当它已经不在
                }
            }
            if (level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity anchor) {
                if (!anchor.running()) {
                    networks.markStopped(pos);
                    continue;
                }
                anchor.setRunning(false);
                anchor.sync();
                stopped++;
            } else {
                networks.markStopped(pos);
            }
        }
        return stopped;
    }

    /** 停机键的入口：关掉所有在跑的阵（含其它维度），并在聊天栏回一句结果。 */
    public static void stopAllAndReport(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int stopped = stopAll(player);
        if (stopped <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.rite.stop_all_none")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.rite.stop_all", stopped)
                .withStyle(ChatFormatting.YELLOW), false);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    // ---------------------------------------------------------------- 远程法阵轮盘

    /** 远程轮盘 / 停机键要认的锚点：刻印过、有层、主人是这个人。 */
    private static boolean ownedBy(MagicCircleProjectionBlockEntity anchor, ServerPlayer player) {
        return anchor.sealed() && !anchor.isEmpty()
                && player.getUUID().equals(anchor.owner());
    }

    /** 锚点最上面那层的名字（远程轮盘上显示）。 */
    private static String anchorName(MagicCircleProjectionBlockEntity anchor) {
        if (anchor.layers().isEmpty()) return "无名法阵";
        return anchor.layers().get(anchor.layers().size() - 1).name();
    }

    /** 锚点中心到某个位置的距离平方。 */
    private static double centerDistanceSqr(BlockPos pos, Vec3 point) {
        double dx = pos.getX() + 0.5 - point.x;
        double dy = pos.getY() + 0.5 - point.y;
        double dz = pos.getZ() + 0.5 - point.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 把玩家附近自己的刻印锚点打包发给客户端（远程轮盘用）。 */
    public static void sendRemoteList(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.position();
        List<BlockPos> positions = new ArrayList<>();
        for (BlockEntity blockEntity : loadedAnchors(level, player.blockPosition(), REMOTE_RADIUS)) {
            if (!(blockEntity instanceof MagicCircleProjectionBlockEntity anchor)) continue;
            if (!ownedBy(anchor, player)) continue;
            positions.add(anchor.getBlockPos());
        }
        positions.sort((a, b) -> Double.compare(centerDistanceSqr(a, eye), centerDistanceSqr(b, eye)));
        List<MagicCircleRemoteListPayload.Entry> entries = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (entries.size() >= REMOTE_MAX_ENTRIES) break;
            if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity anchor)) continue;
            entries.add(new MagicCircleRemoteListPayload.Entry(pos.getX(), pos.getY(), pos.getZ(),
                    anchorName(anchor), anchor.running(), anchor.layerCount(),
                    (int) Math.round(Math.sqrt(centerDistanceSqr(pos, eye)))));
        }
        PacketDistributor.sendToPlayer(player, new MagicCircleRemoteListPayload(entries,
                runningCount(level, player), runningLimit(player),
                levelLimitBonus(player), manaLimitBonus(player), positions.size()));
    }

    /** 远程轮盘点了一下：校验主人 / 距离，然后走正常的开关流程。 */
    public static void remoteToggle(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity anchor)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.remote.gone").withStyle(ChatFormatting.RED), false);
            sendRemoteList(player);
            return;
        }
        if (!ownedBy(anchor, player)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.remote.not_yours").withStyle(ChatFormatting.RED), false);
            sendRemoteList(player);
            return;
        }
        double maxDistance = REMOTE_RADIUS;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistance * maxDistance) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.remote.too_far", REMOTE_RADIUS)
                    .withStyle(ChatFormatting.RED), false);
            sendRemoteList(player);
            return;
        }
        toggleRun(level, pos, player);
        sendRemoteList(player);
    }

    /** 扫玩家周围已加载区块里的投影锚点方块实体（找主人用）；找不到就返回空列表。 */
    private static List<BlockEntity> loadedAnchors(ServerLevel level, BlockPos center, int radius) {
        List<BlockEntity> found = new ArrayList<>();
        int chunkRadius = (radius >> 4) + 1;
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                var chunk = level.getChunkSource().getChunkNow((center.getX() >> 4) + cx, (center.getZ() >> 4) + cz);
                if (chunk == null) continue;
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof MagicCircleProjectionBlockEntity) found.add(entry.getValue());
                }
            }
        }
        return found;
    }

    /** 扣魔素：优先扣触发的人，其次扣主人；创造模式（且开了免费粉）不扣。 */
    private static boolean payMana(ServerLevel level, MagicCircleProjectionBlockEntity entity,
                                   ServerPlayer actor, int cost, boolean notify) {
        ServerPlayer payer = actor != null ? actor : ownerPlayer(level, entity);
        if (payer == null) return false;
        if (payer.isCreative() && MagicCircleConfig.CREATIVE_FREE_POWDER.get()) return true;
        if (cost <= 0) return true;
        double mana = ManaData.get(payer).mana();
        if (mana < cost) {
            if (notify) {
                payer.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.rite.no_mana", (int) Math.ceil(cost - mana), cost)
                        .withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        ManaData.spendMana(payer, cost);
        return true;
    }

    private static boolean isBlacklisted(List<? extends String> list, String id) {
        if (list == null) return false;
        for (String entry : list) {
            if (entry != null && entry.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    private static String builtInId(Block block) {
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return key == null ? "" : key.toString();
    }

    /** 锚点表面往外 1.5 格的位置，伤害 / 召唤都从这里算中心。 */
    private static Vec3 surfaceCenter(ServerLevel level, BlockPos pos, Direction facing, double distance) {
        Vec3 outward = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()).scale(distance);
        return Vec3.atCenterOf(pos).add(outward);
    }

    /** 触发时的光效：每个功能块位置炸一撮粒子，并在一整组连线上打一道光脉冲。 */
    private static void visualPulse(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity) {
        Direction facing = entity.getBlockState().getValue(MagicCircleProjectionBlock.FACING);
        for (MagicCircleNetwork.Node node : entity.nodes()) {
            float[] local = MagicCircleProjectionGeometry.local(facing, entity.baseW(), entity.baseH(),
                    node.x(), node.y(), 0.03F);
            ElementType element = MagicCirclePowders.elementOf(node.element());
            var particle = switch (element == null ? ElementType.FIRE : element) {
                case FIRE -> ParticleTypes.FLAME;
                case WATER -> ParticleTypes.SPLASH;
                case ICE -> ParticleTypes.SNOWFLAKE;
                case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
                case WIND -> ParticleTypes.GUST;
                case EARTH -> ParticleTypes.POOF;
                case NATURE -> ParticleTypes.HAPPY_VILLAGER;
                case METAL -> ParticleTypes.CRIT;
                case LIGHT -> ParticleTypes.END_ROD;
                case POISON -> ParticleTypes.ITEM_SLIME;
                case SOUND -> ParticleTypes.NOTE;
            };
            level.sendParticles(particle, pos.getX() + local[0], pos.getY() + local[1], pos.getZ() + local[2],
                    2, 0.06, 0.06, 0.06, 0.01);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.7F, 1.35F);
        for (BlockPos other : entity.links()) {
            pulseAlong(level, entity, other);
        }
    }

    /** 沿一条连线从头流到尾的一串粒子（服务端版；渲染层还有一层发光线）。 */
    private static void pulseAlong(ServerLevel level, MagicCircleProjectionBlockEntity from, BlockPos to) {
        Vec3 a = fromCenter(level, from.getBlockPos());
        Vec3 b = Vec3.atCenterOf(to);
        if (a.distanceTo(b) > 32) return;
        Set<Vec3> points = new LinkedHashSet<>();
        for (int i = 0; i <= 8; i++) {
            Vec3 point = a.lerp(b, i / 8.0);
            points.add(point);
        }
        for (Vec3 point : points) {
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static Vec3 fromCenter(ServerLevel level, BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
