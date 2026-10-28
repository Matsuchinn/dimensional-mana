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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    /** 触发块自动检测的扫描间隔（刻）：每 4 刻看一次周围情况。 */
    private static final int SENSOR_INTERVAL = 4;
    /** 自动检测两次开火之间的最低间隔（刻）：条件持续成立时最多 2 秒一发。 */
    private static final int SENSOR_MIN_INTERVAL = 40;
    /** 召唤物身上的两个标记：一个是「这是法阵召唤物」，一个是「到期刻」。 */
    private static final String SUMMON_TAG = "dm_magic_circle_summon";
    private static final String SUMMON_OWNER_TAG = "dm_summon_anchor";
    private static final String SUMMON_UNTIL_TAG = "dm_summon_until";
    /**
     * r211 召唤物守则：所有法阵召唤物打这个实体标签并绑 OwnerUUID（持久数据键同名规范写法），
     * 之后友伤免疫、仇恨校验、守卫锚点、锚点失效处置、owner 离线停 AI 全靠这几项判断。
     */
    public static final String DM_SUMMONED = "DM_Summoned";
    private static final String SUMMON_UUID_TAG = "OwnerUUID";
    /** AI 模式：0 跟随 / 1 待机 / 2 守卫锚点 / 3 激进 / 4 被动（与召唤边界声明的枚举一致）。 */
    private static final String SUMMON_AI_TAG = "dm_summon_ai";
    /** 归属模式：0 owner+同队 / 1 公开 / 2 仅 owner。 */
    private static final String SUMMON_OWNMODE_TAG = "dm_summon_ownmode";
    /** 锚点失效处置：0 消散 / 1 停 AI / 2 保留。 */
    private static final String SUMMON_LOST_TAG = "dm_summon_lost";
    /** 守卫锚点：不得离开锚点这么多格；主动索敌只看锚点周围这么大一片。 */
    private static final double GUARD_LEASH = 16.0;
    private static final double GUARD_AGGRO = 8.0;
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
    /** 远程法阵面板扫描多远之内的锚点（格）。 */
    public static final int REMOTE_RADIUS = 96;
    /**
     * 远程法阵面板最多列几个，太多就只列最近的。
     *
     * <p>r216：原来只有 16 个，后期玩家贴几十座阵根本看不全；面板本身改成可以上下滚动的清单，
     * 这里顺势放宽到 64（扫描半径 96 格内一般也就这个量级），再多就按距离只列最近的。</p>
     */
    public static final int REMOTE_MAX_ENTRIES = 64;

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
                explainProblems(player, evaluation);
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
            // 先认主人再开：开的那一下要按主人登记进维度档案（停机键 / 远程面板要靠它）。
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
        // v2.2 信号边界：到期的强制信号在这里关掉，跟阵跑没跑无关。
        if (!SIGNAL_UNTIL.isEmpty() && level.getGameTime() % 5 == 0) {
            revertDueSignals(level, level.getGameTime());
        }
        if (!entity.running()) return;
        long now = level.getGameTime();
        if (now % UPKEEP_INTERVAL == 0) {
            // 上个版本启动的阵在维度档案里没有登记（那时候还没这东西）：每 20 刻顺手补一次，
            // 停机键和远程面板才找得到它。已经登记过的这一步什么都不做。
            MagicCircleNetworks.get(level).markRunning(pos, entity.owner());
            int upkeep = entity.evaluate().upkeepMana();
            if (upkeep > 0) payUpkeep(level, entity, upkeep);
        }
        // 触发块的自动检测（魔检 / 血检 / 爆炸 / 经验 / 反应 / 数检）：v2.1 触发节点小节。
        if (now % SENSOR_INTERVAL == 0) {
            MagicCircleTriggers.scan(level, pos, entity);
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

    /** 自动检测媒介触发（魔检 / 血检 / 爆炸 / 经验 / 反应 / 数检，由 {@link MagicCircleTriggers#scan} 调进来）。 */
    public static void triggerBySensor(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity) {
        long now = level.getGameTime();
        // 自动检测另有一条 2 秒的最低间隔：经验球、怪物这种「条件一直成立」的媒介
        // 不能像手动触发那样半秒一发，否则阵法会无脑烧魔素。
        if (entity.lastSensorTick() >= 0 && now - entity.lastSensorTick() < SENSOR_MIN_INTERVAL) return;
        if (entity.lastTriggerTick() >= 0 && now - entity.lastTriggerTick() < TRIGGER_COOLDOWN) return;
        entity.markSensor(now);
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
                    actor.displayClientMessage(MagicCircleIssue.component(problem)
                            .withStyle(ChatFormatting.RED), false);
                }
                // 规范 18：试运行遇到 E1 缺声明，直接把缺什么讲清楚，别让玩家猜。
                if (test) {
                    actor.displayClientMessage(Component.translatable(
                            "message.dimensional_mana.magiccircle.declare.fix_hint")
                            .withStyle(ChatFormatting.YELLOW), false);
                }
            }
            return;
        }
        int cost = test
                ? (int) Math.ceil(evaluation.startMana() * MagicCircleConfig.TEST_RUN_COST_RATIO.get())
                : evaluation.triggerMana();
        // 门控规范 v2.4 五 / v2.6 四 4：触发瞬间先过门控。全部终端都被门控拦下时逻辑短路——
        // 这一发干脆不发：不扣触发魔素、不上冷却（v2.6 四 4「不扣终端触发成本」）。
        if (MagicCircleConfig.DECLARE_MODE_ENABLED.get() && !test) {
            Direction preFacing = entity.getBlockState().getValue(MagicCircleProjectionBlock.FACING);
            ServerPlayer preCaster = actor != null ? actor : ownerPlayer(level, entity);
            int[] verdict = gatePrecheck(level, pos, preFacing, entity, preCaster);
            if (verdict[0] > 0 && verdict[1] == 0) {
                if (actor != null) {
                    actor.displayClientMessage(Component.translatable(
                            "message.dimensional_mana.magiccircle.gate.blocked_all",
                            verdict[2]).withStyle(ChatFormatting.RED), false);
                }
                return;
            }
        }
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
        if (MagicCircleConfig.DECLARE_MODE_ENABLED.get()) {
            settleV22(level, pos, facing, entity, actor, caster, strength, test, pvp, playerDamage,
                    outcome, evaluation);
        } else {
            for (MagicCircleNetwork.Node node : entity.nodes()) {
                if (node.type() != MagicCircleBlockType.OUTPUT) continue;
                settleOutput(level, pos, facing, entity, node, actor, caster, strength, test, pvp, playerDamage,
                        outcome, evaluation);
            }
        }
        // v2.1 触发节点元素槽：触发时给周围的目标（或触发者）附加对应元素的附着。
        applyTriggerAttachments(level, pos, facing, entity);
        visualPulse(level, pos, entity);
        if (actor != null) {
            actor.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.rite.triggered",
                    test ? 0 : cost, String.format(Locale.ROOT, "%.2f", strength))
                    .withStyle(ChatFormatting.GREEN), false);
            // 试运行 = 效果预览：不落地任何伤害 / 召唤 / 方块改动，但把「预计会发生什么」逐条打出来。
            if (test && !outcome.preview.isEmpty()) {
                actor.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.preview.header")
                        .withStyle(ChatFormatting.AQUA), false);
                for (Component line : outcome.preview) {
                    actor.displayClientMessage(line.copy().withStyle(ChatFormatting.YELLOW), false);
                }
                actor.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.preview.footer",
                        evaluation.startMana(), evaluation.upkeepMana(), evaluation.triggerMana())
                        .withStyle(ChatFormatting.GRAY), false);
            }
            if (outcome.hasAny()) {
                actor.displayClientMessage(Component.literal(outcome.describe()).withStyle(ChatFormatting.LIGHT_PURPLE),
                        false);
            }
        }
    }

    /** 启动失败时逐条解释缺哪些边界组。 */
    private static void explainProblems(ServerPlayer player, MagicCircleCost.Evaluation evaluation) {
        for (String problem : evaluation.problems()) {
            player.displayClientMessage(MagicCircleIssue.component(problem).withStyle(ChatFormatting.RED), false);
        }
    }

    /** 输出块的结算次数，最后拼成一行聊天提示；{@code preview} 是试运行逐条效果预览。 */
    private static final class Outcome {
        int hurt, heal, summon, mined, blocked, reactions;
        final List<Component> preview = new ArrayList<>();

        /** 试运行：记下「预计会发生什么」，一条终端一行。 */
        void preview(Component line) {
            if (preview.size() < 24) preview.add(line);
        }

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
                                     boolean pvp, boolean playerDamage, Outcome outcome,
                                     MagicCircleCost.Evaluation evaluation) {
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
                playerDamage, outcome, evaluation);
    }

    /** 触发节点元素槽的附着：法阵一开火，先把周围生物（含触发者）挂上这个元素，方便后续打反应。 */
    private static void applyTriggerAttachments(ServerLevel level, BlockPos pos, Direction facing,
                                                MagicCircleProjectionBlockEntity entity) {
        Vec3 center = surfaceCenter(level, pos, facing, 2.0);
        for (MagicCircleNetwork.Node node : entity.nodes()) {
            if (node.type() != MagicCircleBlockType.TRIGGER || node.element().isEmpty()) continue;
            ElementType element = MagicCirclePowders.elementOf(node.element());
            if (element == null) continue;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(center, 10.0, 10.0, 10.0))) {
                if (target.isSpectator() || target.isDeadOrDying()) continue;
                ElementReactionSystem.attach(target, element, element.attachmentTicks());
            }
        }
    }

    /** 元素输出：按元素槽决定打什么、打谁。返回命中数。 */
    private static int elemental(ServerLevel level, BlockPos pos, Direction facing, MagicCircleNetwork.Node node,
                                 ElementType element, ServerPlayer actor, ServerPlayer caster, double strength,
                                 boolean test, boolean pvp, boolean playerDamage, Outcome outcome,
                                 MagicCircleCost.Evaluation evaluation) {
        // 核心语义模式的输出幅度：增强 +25% / 超频 +50% / 节能 -20%，都只作用于与核心同元素的输出块。
        double outputFactor = 1.0;
        if (evaluation != null && evaluation.outputBonus() != 0.0
                && evaluation.coreElement().equals(node.element())) {
            outputFactor = Math.max(0.1, 1.0 + evaluation.outputBonus());
        }
        // 延时（萤石粉）模式：带时长效果延长 25%。
        double durationFactor = 1.0 + (evaluation == null ? 0.0 : evaluation.durationBonus());
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
                float damage = (float) (damageOf(element, strength) * outputFactor);
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
                applySideEffect(target, element, strength, durationFactor);
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

    /** 附带效果；{@code durationFactor} 是核心延时模式带来的带时长效果延长（默认 1.0，萤石粉 1.25）。 */
    private static void applySideEffect(LivingEntity target, ElementType element, double strength,
                                        double durationFactor) {
        if (element == null) return;
        int dur = (int) Math.round(100 * durationFactor);
        switch (element) {
            case FIRE -> target.setRemainingFireTicks((int) Math.round(80 * durationFactor));
            case ICE -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 2));
            case EARTH -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    (int) Math.round(60 * durationFactor), 1));
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON,
                    (int) Math.round(120 * durationFactor), 1));
            case WIND -> {
                Vec3 push = target.position().subtract(Vec3.atCenterOf(target.blockPosition())).normalize();
                double force = 0.4 * strength;
                target.push(push.x * force, 0.25, push.z * force);
            }
            case LIGHTNING -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0));
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
            bindSummon(created, ownerPlayer(level, entity), 2, 0, 0);
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

    /**
     * r211 召唤物守则：凡由法阵生成的召唤物（无论友好 / 敌对 / 特殊）统一
     * 打 {@code DM_Summoned} 实体标签并绑 OwnerUUID + AI 模式 / 归属 / 锚点失效处置。
     */
    private static void bindSummon(Entity created, ServerPlayer owner,
                                   int aiMode, int ownerMode, int lostPolicy) {
        created.addTag(DM_SUMMONED);
        if (created instanceof LivingEntity livingSummon) livingSummon.skipDropExperience();
        CompoundTag data = created.getPersistentData();
        data.putBoolean(SUMMON_TAG, true);
        data.putInt(SUMMON_AI_TAG, aiMode);
        data.putInt(SUMMON_OWNMODE_TAG, ownerMode);
        data.putInt(SUMMON_LOST_TAG, lostPolicy);
        if (owner != null) {
            data.putUUID(SUMMON_UUID_TAG, owner.getUUID());
            if (created instanceof net.minecraft.world.entity.TamableAnimal tame) {
                tame.setOwnerUUID(owner.getUUID());
            }
        }
    }

    /** 是不是法阵召唤物：实体标签与持久数据认一个就算（防别的系统清标签）。 */
    public static boolean isCircleSummon(Entity entity) {
        if (entity == null) return false;
        if (entity.getTags().contains(DM_SUMMONED)) return true;
        return entity.getPersistentData().getBoolean(SUMMON_TAG);
    }

    private static int summonAiMode(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(SUMMON_AI_TAG) ? data.getInt(SUMMON_AI_TAG) : 2; // 默认守卫锚点
    }

    /**
     * r211 声明「死亡掉落空」：法阵召唤物被打死时什么都不吐。
     *
     * <p>召唤是凭空借来的力量，不是打怪素材——允许掉落就等于鼓励刷。</p>
     */
    public static void onSummonDeathDrops(
            net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !isCircleSummon(mob)) return;
        event.getDrops().clear();
        mob.skipDropExperience(); // 掉落事件在经验结算之前触发，这里补一刀保证「死亡掉落空」
        mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0.0F);
    }

    /**
     * 召唤物的友伤判定：owner、同队（owner+同队模式下）、
     * 以及同一个 owner 的其它召唤物都算「自己人」。
     * ownerMode：0 = owner+同队；1 = 公开（只护 owner）；2 = 仅 owner。
     */
    private static boolean isFriendlyToSummon(Entity summon, LivingEntity candidate) {
        CompoundTag data = summon.getPersistentData();
        if (!data.hasUUID(SUMMON_UUID_TAG)) return false;
        UUID ownerId = data.getUUID(SUMMON_UUID_TAG);
        int ownerMode = data.contains(SUMMON_OWNMODE_TAG) ? data.getInt(SUMMON_OWNMODE_TAG) : 0;
        if (candidate.getUUID().equals(ownerId)) return true;                       // owner 本人
        if (isCircleSummon(candidate)
                && ownerId.equals(candidate.getPersistentData().getUUID(SUMMON_UUID_TAG))) return true; // 自家召唤物
        if (ownerMode == 1) return false;                                           // 公开：除 owner 都能打
        if (candidate instanceof Player candidatePlayer
                && candidatePlayer.level() instanceof ServerLevel serverLevel) {
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
            // owner 在线：按队伍同盟判；owner 离线：同队判定退化成「队友也算自己人」→ 离线查不到队，按不打玩家处理
            return owner == null || owner.isAlliedTo(candidatePlayer);
        }
        // 非玩家的活物：默认只把敌对生物当可打目标，其它一律不碰（防误伤村民 / 动物）
        return !(candidate instanceof net.minecraft.world.entity.monster.Enemy);
    }

    /** 召唤物挨打 / 主人挨打的联动，以及「不许打自己人」的伤害拦截。 */
    public static void onSummonIncomingDamage(
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity attackerRaw = event.getSource().getEntity();
        // ① 友伤免疫：法阵召唤物打 owner / 同队 → 直接免伤
        if (attackerRaw instanceof LivingEntity attacker && isCircleSummon(attacker)
                && isFriendlyToSummon(attacker, victim)) {
            event.setCanceled(true);
            return;
        }
        // ② 守卫反击：玩家被咬，附近守卫锚点状态、认这个玩家做主的召唤物立刻盯上攻击者
        if (!(victim instanceof Player) || !(attackerRaw instanceof LivingEntity attacker)) return;
        if (!(attackerRaw instanceof net.minecraft.world.entity.monster.Enemy)
                && !isCircleSummon(attackerRaw)) return; // 只认真正的攻击性来源
        for (Mob mob : victim.level().getEntitiesOfClass(Mob.class,
                victim.getBoundingBox().inflate(GUARD_LEASH + 8.0),
                m -> isCircleSummon(m) && summonAiMode(m) == 2 && isSummonOwns(m, (Player) victim))) {
            if (mob.getTarget() == null && mob.canAttack(attacker)) mob.setTarget(attacker);
        }
    }

    /** 这只守卫是否认这位玩家做主：owner 本人或同队。 */
    private static boolean isSummonOwns(Mob summon, Player player) {
        return isFriendlyToSummon(summon, player);
    }

    /** 召唤物的生命周期：到期消散；活着时按声明执法（友伤清空、守卫锚点、owner 离线停 AI）。 */
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        if (living.level().isClientSide) return;
        CompoundTag data = living.getPersistentData();
        long until = data.getLong(SUMMON_UNTIL_TAG);
        if (until <= 0L) return;
        if (living.level().getGameTime() >= until) {
            data.remove(SUMMON_UNTIL_TAG);
            data.remove(SUMMON_TAG);
            living.discard();
            return;
        }
        if (!(living instanceof Mob mob)) return;
        // —— 每刻仇恨校验：当前目标是自己人（owner / 同队）就强制清空 ——
        LivingEntity target = mob.getTarget();
        if (target != null && isFriendlyToSummon(mob, target)) mob.setTarget(null);
        // —— 以下按 10 刻一次降频：离线 / 锚点失效 / 守卫巡逻 ——
        if (living.tickCount % 10 != 0) return;
        ServerPlayer owner = null;
        if (data.hasUUID(SUMMON_UUID_TAG) && living.level() instanceof ServerLevel serverLevel) {
            owner = serverLevel.getServer().getPlayerList().getPlayer(data.getUUID(SUMMON_UUID_TAG));
        }
        BlockPos anchor = BlockPos.of(data.getLong(SUMMON_OWNER_TAG));
        // 锚点所在区块还没加载 ≠ 锚点没了：跳过这轮检查，别把出门远征的召唤物全判死。
        if (!living.level().isLoaded(anchor)) return;
        boolean anchorAlive = living.level().getBlockEntity(anchor)
                instanceof MagicCircleProjectionBlockEntity;
        if (!anchorAlive) {
            // 锚点失效处置：0 消散 / 1 停 AI / 2 保留
            switch (data.contains(SUMMON_LOST_TAG) ? data.getInt(SUMMON_LOST_TAG) : 0) {
                case 0 -> {
                    data.remove(SUMMON_UNTIL_TAG);
                    data.remove(SUMMON_TAG);
                    living.discard();
                    if (living.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.POOF,
                            living.getX(), living.getY() + 0.5, living.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                }
                case 1 -> {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                }
                default -> { /* 保留：什么都不做 */ }
            }
            return;
        }
        // owner 离线 → 停 AI（不追击、不巡逻，原地待机；到期照常消散）
        if (data.hasUUID(SUMMON_UUID_TAG) && owner == null) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            return;
        }
        int ai = summonAiMode(mob);
        Vec3 anchorCenter = Vec3.atCenterOf(anchor);
        if (ai == 2 || ai == 3) {
            // 守卫锚点 / 激进：不得离开锚点 16 格；主动打锚点周围（守卫 8 格 / 激进 16 格）的非友方
            double range = GUARD_LEASH;
            if (mob.position().distanceToSqr(anchorCenter) > range * range) {
                mob.setTarget(null);
                mob.getNavigation().moveTo(anchorCenter.x, anchorCenter.y, anchorCenter.z, 1.0);
                if (mob.position().distanceToSqr(anchorCenter) > (range + 8.0) * (range + 8.0)) {
                    mob.teleportTo(anchorCenter.x, anchorCenter.y, anchorCenter.z);
                }
                return;
            }
            if (mob.getTarget() == null) {
                double aggro = ai == 3 ? GUARD_LEASH : GUARD_AGGRO;
                LivingEntity nearest = null;
                double nearestSqr = aggro * aggro;
                for (LivingEntity candidate : living.level().getEntitiesOfClass(LivingEntity.class,
                        AABB.ofSize(anchorCenter, aggro * 2, aggro * 2, aggro * 2))) {
                    if (candidate == mob || candidate.isSpectator() || candidate.isDeadOrDying()) continue;
                    if (!(candidate instanceof net.minecraft.world.entity.monster.Enemy)) continue;
                    if (isFriendlyToSummon(mob, candidate)) continue;
                    double distSqr = candidate.position().distanceToSqr(mob.position());
                    if (distSqr < nearestSqr) {
                        nearestSqr = distSqr;
                        nearest = candidate;
                    }
                }
                if (nearest != null && mob.canAttack(nearest)) mob.setTarget(nearest);
            } else if (mob.getTarget().position().distanceToSqr(anchorCenter)
                    > (GUARD_LEASH + 8.0) * (GUARD_LEASH + 8.0)) {
                mob.setTarget(null); // 目标被拖出守卫范围就放弃
            }
        } else if (ai == 0 && owner != null) {
            // 跟随：离主人超过 6 格就跟上
            if (mob.position().distanceToSqr(owner.position()) > 36.0) {
                mob.getNavigation().moveTo(owner, 1.0);
            }
        } else if (ai == 1 || ai == 4) {
            // 待机 / 被动：不乱跑，被咬时的反击由 onSummonIncomingDamage 决定（被动模式永不清 target 之外的动作）
            if (ai == 4) mob.setTarget(null);
            else if (mob.position().distanceToSqr(anchorCenter) > GUARD_LEASH * GUARD_LEASH) {
                mob.getNavigation().moveTo(anchorCenter.x, anchorCenter.y, anchorCenter.z, 1.0);
            }
        }
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

    // ================================================================ v2.2 终端结算
    //
    // 输出节点两种身份（规范 1.1）：连了下游输出的「前置」编译成词缀、自身不结算；
    // 「终端」按语义槽的七大协议分支执行，元素槽决定特化，染料槽决定数值加成，
    // 前置词缀与声明模式条件节点的参数在这里全部落地。
    // 试运行（test = true）不改世界，只把「预计会发生什么」逐条写进 Outcome.preview。

    /** 信号边界：被点亮的方块 asLong → 该恢复关掉的刻。 */
    private static final Map<Long, Long> SIGNAL_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();
    /** 信号脉冲长度（刻），默认 4 秒。 */
    private static final int SIGNAL_PULSE_TICKS = 80;

    /**
     * 把到期的强制信号关掉。挂在锚点 ticker 上顺带执行：
     * 锚点卸载后没人接盘，信号随区块一起冻结，回来还能被后续触发覆盖，可接受。
     */
    /**
     * 这块方块有没有「可被红石点亮」的布尔属性：门 / 活板门 / 栅栏门看 powered，
     * 红石灯看 lit。返回空 = 点了也没用。
     */
    private static java.util.Optional<net.minecraft.world.level.block.state.properties.BooleanProperty>
            powerProperty(BlockState state) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
            return java.util.Optional.of(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED);
        }
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
            return java.util.Optional.of(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        }
        return java.util.Optional.empty();
    }

    private static void revertDueSignals(ServerLevel level, long now) {
        if (SIGNAL_UNTIL.isEmpty()) return;
        var iterator = SIGNAL_UNTIL.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() > now) continue;
            BlockPos target = BlockPos.of(entry.getKey());
            BlockState state = level.getBlockState(target);
            var property = powerProperty(state);
            if (property.isPresent() && state.getValue(property.get())) {
                level.setBlock(target, state.setValue(property.get(), false), Block.UPDATE_ALL);
            }
            iterator.remove();
        }
    }

    private static void settleV22(ServerLevel level, BlockPos pos, Direction facing,
                                  MagicCircleProjectionBlockEntity entity, ServerPlayer actor, ServerPlayer caster,
                                  double strength, boolean test, boolean pvp, boolean playerDamage,
                                  Outcome outcome, MagicCircleCost.Evaluation evaluation) {
        boolean hasStabilizer = false;
        for (MagicCircleNetwork.Node candidate : entity.nodes()) {
            if (candidate.type() == MagicCircleBlockType.STABILIZE) hasStabilizer = true;
        }
        for (MagicCircleBlueprint blueprint : entity.layers()) {
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
                if (layer.nodes().isEmpty()) continue;
                boolean coreSugar = false;
                boolean coreEco = false;
                ElementType coreElement = null;
                for (MagicCircleNetwork.Node node : layer.nodes()) {
                    if (node.type() == null || !node.type().isCore()) continue;
                    if ("minecraft:sugar".equals(node.semantic())) coreSugar = true;
                    if ("dimensional_mana:amethyst_dust".equals(node.semantic())) coreEco = true;
                    if (coreElement == null) coreElement = MagicCirclePowders.elementOf(node.element());
                }
                MagicCircleGraph.Analysis analysis = MagicCircleGraph.analyze(layer, true, coreSugar);
                Vec3 gateAnchor = surfaceCenter(level, pos, facing, 1.5);
                for (MagicCircleGraph.Plan plan : analysis.terminals) {
                    // 门控守卫（规范 v2.4 五 5 / v2.6 三）：终端上游的判定链先走一遍，
                    // 短路求值——首个 F 立刻拦下，被拦终端这一发不执行、不吃它的触发成本。
                    if (!plan.gateBranches.isEmpty()) {
                        List<GateShot> shots = test ? new ArrayList<>() : null;
                        boolean pass = planGatesPass(layer, plan, level, gateAnchor, caster, shots);
                        if (test && shots != null) {
                            showGateReadouts(level, pos, entity, layer, shots);
                            for (GateShot shot : shots) {
                                outcome.preview(Component.literal("[条件解析] #" + (shot.node() + 1) + " "
                                        + shot.verdict().mark() + " — " + shot.verdict().detail()));
                            }
                            outcome.preview(Component.literal("[条件解析] 终端#" + (plan.terminal + 1)
                                    + (pass ? "：门控放行，这一发会执行。" : "：被门控拦截，这一发不会执行。")));
                        }
                        if (!pass) {
                            outcome.blocked++;
                            if (actor != null && !test) {
                                actor.displayClientMessage(Component.translatable(
                                        "message.dimensional_mana.magiccircle.gate.blocked_one",
                                        plan.terminal + 1).withStyle(ChatFormatting.RED), false);
                            }
                            continue;
                        }
                    }
                    settleTerminal(level, pos, facing, entity, layer.nodes().get(plan.terminal), plan,
                            actor, caster, strength, test, pvp, playerDamage, outcome, evaluation,
                            coreEco, coreElement, hasStabilizer);
                }
            }
        }
    }

    /** 一个终端的结算。所有对世界的改动都被 test 拦住，换成往 preview 里写一行「预计」。 */
    private static void settleTerminal(ServerLevel level, BlockPos pos, Direction facing,
                                       MagicCircleProjectionBlockEntity entity, MagicCircleNetwork.Node node,
                                       MagicCircleGraph.Plan plan, ServerPlayer actor, ServerPlayer caster,
                                       double strength, boolean test, boolean pvp, boolean playerDamage,
                                       Outcome outcome, MagicCircleCost.Evaluation evaluation,
                                       boolean coreEco, ElementType coreElement, boolean hasStabilizer) {
        // 规范 18：运行时重校验。缺声明的终端哑火、不扣触发。
        if (!plan.missing.isEmpty()) {
            outcome.blocked++;
            if (actor != null) {
                actor.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.declare.runtime_block",
                        plan.terminal + 1, MagicCircleGraph.missingList(plan)).withStyle(ChatFormatting.RED), false);
            }
            return;
        }
        // 词缀冲突且全阵没有稳定块：有概率哑火（规范 5）。
        if (plan.dyeConflict && !hasStabilizer
                && level.random.nextFloat() < MagicCircleConfig.MISFIRE_CHANCE.get()) {
            outcome.blocked++;
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "：染料词缀互冲，本发哑火（放稳定块可避免）"));
            return;
        }

        ElementType element = MagicCirclePowders.elementOf(node.element());
        boolean arcaneElement = MagicCirclePowders.isArcane(node.element());
        MagicCircleAffixes.Protocol protocol = plan.protocol;
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        java.util.Set<String> affixIds = new java.util.LinkedHashSet<>();
        java.util.Set<String> dyeTags = new java.util.LinkedHashSet<>();
        for (MagicCircleGraph.Prefix prefix : plan.prefixes) {
            if (prefix.affix() != null) {
                tags.add(prefix.affix().tag());
                affixIds.add(prefix.affix().id());
            }
            if (prefix.dyeTag() != null) dyeTags.add(prefix.dyeTag());
        }

        // 数值乘区：终端染料 + 同意义加成 + 增幅 / 爆裂系修饰（规范 3.x / 5 / 6.3）。
        double dyeCoeff = node.dye().isEmpty() ? 0.0
                : (MagicCirclePowders.get(node.dye()) == null ? 0.0 : MagicCirclePowders.get(node.dye()).coefficient());
        double amount = 1.0 + dyeCoeff * MagicCircleConfig.DYE_TERMINAL_STEP.get();
        amount *= MagicCircleAffixes.sameTagBonus(protocol, plan.prefixes.stream()
                .map(MagicCircleGraph.Prefix::affix).filter(java.util.Objects::nonNull).toList());
        if (dyeTags.contains("amplify") || tags.contains("boost")) amount *= 1.10;
        if (dyeTags.contains("burst")) amount *= 1.20;
        double outputFactor = 1.0;
        if (evaluation != null && evaluation.outputBonus() != 0.0 && coreElement != null && coreElement == element) {
            outputFactor = Math.max(0.1, 1.0 + evaluation.outputBonus());
        }
        if (coreEco && coreElement != null && coreElement == element) outputFactor *= 0.8;
        double radius = Math.min(8.0, 2.5 + 1.2 * Math.max(0.0, strength - 1.0));
        if (tags.contains("expand") || dyeTags.contains("burst") || dyeTags.contains("flow")) {
            radius = Math.min(12.0, radius + 1.0);
        }
        double durationFactor = 1.0 + (evaluation == null ? 0.0 : evaluation.durationBonus())
                + (dyeTags.contains("delay") || dyeTags.contains("awaken") ? 0.25 : 0.0);
        Vec3 center = surfaceCenter(level, pos, facing, 1.5);

        switch (protocol) {
            case DAMAGE -> terminalDamage(level, entity, node, plan, element, arcaneElement, tags, affixIds, dyeTags,
                    amount * outputFactor, durationFactor, radius, center, pvp, playerDamage, test, outcome, strength,
                    caster);
            case LIFE -> terminalLife(level, pos, facing, entity, node, plan, element, tags, affixIds, dyeTags,
                    amount * outputFactor, durationFactor, radius, center, test, outcome, strength, pvp, caster);
            case SIGNAL -> terminalSignal(level, pos, plan, test, outcome);
            case PURIFY -> terminalPurify(level, entity, node, plan, element, dyeTags, durationFactor, radius,
                    center, test, outcome);
            case LIGHT -> terminalLight(level, node, plan, element, tags, dyeTags, radius, center,
                    durationFactor, test, outcome);
            case SCAN -> terminalScan(level, pos, entity, node, plan, element, affixIds, tags, radius, center,
                    test, outcome, strength, actor);
            case CHARGE -> terminalCharge(level, pos, entity, node, plan, element, arcaneElement, tags, radius,
                    center, test, outcome, strength, actor, caster, pvp);
            case NONE -> outcome.preview(Component.literal(
                    "终端#" + (plan.terminal + 1) + "：没有可用主协议（缺「协议指定」声明）"));
        }
    }

    /** 范围内可结算目标（跳过死亡与旁观；终端不打自己人由分支再挑）。 */
    private static List<LivingEntity> areaTargets(ServerLevel level, Vec3 center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target.isSpectator() || target.isDeadOrDying()) continue;
            if (target.distanceToSqr(center) <= radius * radius) out.add(target);
        }
        return out;
    }

    /** 火药终端：元素伤害 + 反应 + 爆炸 / 扩散 / 破甲词缀；伤害边界管真实伤害与 PvP / 友伤。 */
    private static void terminalDamage(ServerLevel level, MagicCircleProjectionBlockEntity entity,
                                       MagicCircleNetwork.Node node, MagicCircleGraph.Plan plan, ElementType element,
                                       boolean arcaneElement, java.util.Set<String> tags, java.util.Set<String> affixIds,
                                       java.util.Set<String> dyeTags, double amount, double durationFactor,
                                       double radius, Vec3 center, boolean pvp, boolean playerDamage,
                                       boolean test, Outcome outcome, double strength, ServerPlayer caster) {
        MagicCircleDecl.Entry damage = plan.declaration(MagicCircleDecl.Group.DAMAGE);
        boolean allowPvp = pvp && damage != null && damage.flag(0);
        boolean friendFire = damage != null && damage.flag(1);
        int realPct = damage == null ? 0 : damage.primary();
        float base = damageOf(element == null && !arcaneElement ? ElementType.FIRE : element, strength)
                * (float) amount;
        if (dyeTags.contains("ward")) base *= 0.8F;
        if (dyeTags.contains("bind")) base *= 0.9F;
        if (arcaneElement) base = (float) (4.0 * strength * amount); // 奥术特化：奥术伤害
        int hits = 0;
        for (LivingEntity target : areaTargets(level, center, radius)) {
            boolean isPlayer = target instanceof Player;
            boolean hostile = target instanceof net.minecraft.world.entity.monster.Enemy;
            if (isPlayer) {
                if (!(playerDamage && allowPvp)) continue;
            } else if (!hostile && !friendFire) {
                continue;
            }
            float value = base;
            if (!test) {
                if (element != null && caster != null) {
                    ElementReactionSystem.ElementalHit resolved = ElementReactionSystem.elementalHit(
                            caster, target, element, value, true, !allowPvp);
                    value = resolved.damage();
                    if (resolved.reaction() != null) outcome.reactions++;
                }
                if (value > 0) SpellDamage.apply(target, level.damageSources().magic(), value);
                if (realPct > 0 && value > 0) {
                    SpellDamage.apply(target, level.damageSources().dragonBreath(), value * realPct / 100.0F);
                }
                applySideEffect(target, element, strength, durationFactor);
                applyDamageAffixes(target, tags, dyeTags, durationFactor);
                if (tags.contains("spread") && element != null) {
                    for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                            AABB.ofSize(target.position(), 6, 6, 6))) {
                        if (nearby != target) ElementReactionSystem.attach(nearby, element, element.attachmentTicks());
                    }
                }
            }
            hits++;
            outcome.hurt++;
        }
        if (affixIds.contains("explosion") && hits > 0) {
            if (test) {
                outcome.preview(Component.literal("  ↳ 爆炸词缀：中心 1.5 格爆炸（默认不破坏方块，声明「方块破坏」才拆地）"));
            } else {
                level.explode(caster, center.x, center.y, center.z, 1.5F, false,
                        damage != null && damage.flag(2)
                                ? net.minecraft.world.level.Level.ExplosionInteraction.BLOCK
                                : net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }
        }
        StringBuilder line = new StringBuilder();
        line.append("终端#").append(plan.terminal + 1).append("（火药")
                .append(node.element().isEmpty() ? "" : "+" + MagicCirclePowders.shortName(node.element()))
                .append("）：预计每发 ").append(String.format(Locale.ROOT, "%.1f", base)).append(" 点");
        if (element != null) line.append("（").append(MagicCirclePowders.shortName(node.element())).append("系，可打反应）");
        line.append(" × 约 ").append(hits).append(" 个目标");
        if (realPct > 0) line.append(" · 附带 ").append(realPct).append("% 真实伤害");
        if (allowPvp) line.append(" · 已开PvP");
        if (friendFire) line.append(" · 已开友伤");
        if (!tags.isEmpty()) line.append(" · 词缀：").append(String.join("、", tags.stream().map(MagicCircleRites::tagName).toList()));
        outcome.preview(Component.literal(line.toString()));
    }

    /** 伤害系词缀 / 染料修饰落地。 */
    private static void applyDamageAffixes(LivingEntity target, java.util.Set<String> tags,
                                           java.util.Set<String> dyeTags, double durationFactor) {
        int dur = (int) Math.round(100 * durationFactor);
        if (tags.contains("debuff")) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, dur, 0));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 0));
        }
        if (tags.contains("pierce") || dyeTags.contains("curse")) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 1));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 1));
        }
        if (tags.contains("silence")) {
            target.removeEffect(MobEffects.GLOWING);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, dur, 0));
        }
        if (dyeTags.contains("bind")) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 3));
        }
        if (dyeTags.contains("flow")) {
            Vec3 push = target.position().subtract(target.blockPosition().getX() + 0.5,
                    target.blockPosition().getY() + 0.5, target.blockPosition().getZ() + 0.5).normalize();
            target.push(push.x * 0.6, 0.3, push.z * 0.6);
        }
    }

    /**
     * 安全落点随机传送（规范 3.7：默认安全落点、禁跨维度）：
     * 水平随机距离内，从上往下找「脚下方块能站、头顶两格空气」的位置。
     */
    private static boolean teleportSafely(ServerLevel level, LivingEntity target, Vec3 center, int maxDistance) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        double distance = 3 + level.random.nextDouble() * Math.max(1, maxDistance - 3);
        int x = (int) Math.round(center.x + Math.cos(angle) * distance);
        int z = (int) Math.round(center.z + Math.sin(angle) * distance);
        BlockPos column = new BlockPos(x, level.getMaxBuildHeight() - 2, z);
        if (!level.isLoaded(column)) return false;
        for (int y = column.getY(); y > level.getMinBuildHeight(); y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            BlockPos floor = feet.below();
            if (!level.getBlockState(floor).isAir() && level.getBlockState(floor).isSolid()) {
                target.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                return true;
            }
        }
        return false;
    }

    /** 糖终端：默认治疗；命中召唤特化表（木 / 毒 / 奥术）或吃了召唤词缀就走召唤。 */
    private static void terminalLife(ServerLevel level, BlockPos pos, Direction facing,
                                     MagicCircleProjectionBlockEntity entity, MagicCircleNetwork.Node node,
                                     MagicCircleGraph.Plan plan, ElementType element, java.util.Set<String> tags,
                                     java.util.Set<String> affixIds, java.util.Set<String> dyeTags,
                                     double amount, double durationFactor, double radius, Vec3 center,
                                     boolean test, Outcome outcome, double strength, boolean pvp,
                                     ServerPlayer caster) {
        boolean summonBranch = element == ElementType.NATURE || element == ElementType.POISON
                || affixIds.contains("summon_friendly") || affixIds.contains("summon_hostile")
                || affixIds.contains("summon_special") || dyeTags.contains("awaken");
        if (summonBranch) {
            terminalSummon(level, pos, facing, entity, node, plan, element, affixIds, test, outcome, strength, caster);
            return;
        }
        MagicCircleDecl.Entry heal = plan.declaration(MagicCircleDecl.Group.HEAL);
        float per = (float) (3.0 * strength * amount);
        if (dyeTags.contains("mend")) per *= 1.2F;
        if (tags.contains("heal")) per *= 1.1F;
        boolean healHostile = heal != null && heal.flag(0);
        boolean shieldOverflow = heal != null && heal.flag(1);
        float cap = heal == null ? Float.MAX_VALUE : heal.primary();
        float spent = 0.0F;
        int healed = 0;
        for (LivingEntity target : areaTargets(level, center, radius)) {
            boolean hostile = target instanceof net.minecraft.world.entity.monster.Enemy;
            if (hostile && !healHostile) continue;
            if (target.getHealth() >= target.getMaxHealth()) continue;
            if (spent >= cap) break;
            float apply = Math.min(per, target.getMaxHealth() - target.getHealth());
            apply = Math.min(apply, cap - spent);
            if (!test) {
                target.heal(apply);
                spent += apply;
                if (tags.contains("buff") || tags.contains("regen") || dyeTags.contains("blessing")) {
                    target.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                            (int) Math.round(80 * durationFactor), 0));
                }
                if (tags.contains("shield") || dyeTags.contains("ward") || shieldOverflow) {
                    target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                            (int) Math.round(200 * durationFactor), 0));
                }
                if (tags.contains("purify")) clearHarmful(target);
            }
            healed++;
            outcome.heal++;
        }
        outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（糖）：预计每目标 "
                + String.format(Locale.ROOT, "%.1f", per) + " 点治疗 × " + healed + " 名"
                + (heal == null ? "" : " · 本发上限 " + heal.primary() + " 点")
                + (affixIds.isEmpty() ? "" : " · 词缀：" + String.join("、", tags.stream().map(MagicCircleRites::tagName).toList()))));
    }

    /** 召唤特化：数量 / 寿命 / Boss 权限按召唤边界执法；白名单、场上上限照常。 */
    private static void terminalSummon(ServerLevel level, BlockPos pos, Direction facing,
                                       MagicCircleProjectionBlockEntity entity, MagicCircleNetwork.Node node,
                                       MagicCircleGraph.Plan plan, ElementType element, java.util.Set<String> affixIds,
                                       boolean test, Outcome outcome, double strength, ServerPlayer caster) {
        if (!MagicCircleConfig.SUMMON_ENABLED.get()) {
            outcome.preview(Component.literal("  ↳ 服务器已关闭召唤"));
            return;
        }
        MagicCircleDecl.Entry summon = plan.declaration(MagicCircleDecl.Group.SUMMON);
        String entityId;
        String kind = "友好";
        if (affixIds.contains("summon_hostile") || element == ElementType.POISON) {
            kind = "敌对";
            entityId = "minecraft:cave_spider";
        } else if (affixIds.contains("summon_special") || MagicCirclePowders.isArcane(node.element())) {
            kind = "特殊";
            entityId = "minecraft:allay"; // 特殊召唤：无害傀儡 / 展示物
        } else {
            entityId = summonIdOf(element);
            if (entityId == null) entityId = "dimensional_mana:vine_servant"; // 词缀召唤、元素槽空着
        }
        if (entityId == null) entityId = "dimensional_mana:vine_servant";
        if (isBlacklisted(MagicCircleConfig.SUMMON_BLACKLIST.get(), entityId)
                || (!MagicCircleConfig.SUMMON_WHITELIST.get().isEmpty()
                && !MagicCircleConfig.SUMMON_WHITELIST.get().contains(entityId))) {
            outcome.preview(Component.literal("  ↳ 召唤" + kind + "「" + entityId + "」被服务器名单拦截"));
            outcome.blocked++;
            return;
        }
        boolean boss = BOSS_IDS.contains(entityId);
        if (boss && (summon == null || !summon.flag(0))) {
            outcome.preview(Component.literal("  ↳ Boss 级召唤未获「Boss 权限」声明放行"));
            outcome.blocked++;
            return;
        }
        int max = summon == null ? Math.min(3, MagicCircleConfig.MAX_SUMMONS_PER_NETWORK.get()) : summon.primary();
        int life = summon == null || summon.secondary() <= 0
                ? MagicCircleConfig.SUMMON_LIFETIME_SECONDS.get() : summon.secondary();
        int onField = countSummons(level, entity);
        int room = Math.max(0, Math.min(max, MagicCircleConfig.MAX_SUMMONS_PER_NETWORK.get()) - onField);
        String aiName = summon == null ? "守卫锚点" : switch (summon.enumValue(0)) {
            case 0 -> "跟随"; case 1 -> "待机"; case 3 -> "激进"; case 4 -> "被动"; default -> "守卫锚点";
        };
        if (test) {
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（召唤" + kind + "）：预计召唤 "
                    + room + " 只 " + entityId + "，持续 " + life + " 秒，AI " + aiName
                    + (boss ? " · Boss级" : "")));
            outcome.summon += room;
            return;
        }
        EntityType<?> type = EntityType.byString(entityId).orElse(null);
        if (type == null) return;
        int spawned = 0;
        long until = level.getGameTime() + 20L * life;
        for (int i = 0; i < room; i++) {
            Entity created = type.create(level);
            if (created == null) continue;
            Vec3 at = surfaceCenter(level, pos, facing, 1.2 + i);
            created.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
            if (created instanceof Mob mob) {
                mob.setPersistenceRequired();
                if (summon != null && summon.enumValue(0) == 4) mob.setTarget(null);
            }
            CompoundTag data = created.getPersistentData();
            data.putBoolean(SUMMON_TAG, true);
            data.putLong(SUMMON_OWNER_TAG, pos.asLong());
            data.putLong(SUMMON_UNTIL_TAG, until);
            // r211：终端召唤也统一挂 DM_Summoned + OwnerUUID，AI / 归属 / 失效处置按声明来。
            bindSummon(created, caster != null ? caster : ownerPlayer(level, entity),
                    summon == null ? 2 : summon.enumValue(0),
                    summon == null ? 0 : summon.enumValue(1),
                    summon == null ? 0 : summon.enumValue(2));
            level.addFreshEntity(created);
            spawned++;
        }
        outcome.summon += spawned;
        outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "：实际召唤 " + spawned + " 只 "
                + entityId + "，存活 " + life + " 秒"));
    }

    /** 红石终端：点亮附近可受电方块（灯 / 门 / 活板门），4 秒后自动关。 */
    private static void terminalSignal(ServerLevel level, BlockPos pos, MagicCircleGraph.Plan plan,
                                       boolean test, Outcome outcome) {
        long now = level.getGameTime();
        int lit = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            if (candidate.equals(pos)) continue;
            BlockState state = level.getBlockState(candidate);
            var property = powerProperty(state);
            if (property.isEmpty() || state.getValue(property.get())) continue;
            lit++;
            if (!test) {
                level.setBlock(candidate, state.setValue(property.get(), true), Block.UPDATE_ALL);
                SIGNAL_UNTIL.put(candidate.asLong(), now + SIGNAL_PULSE_TICKS);
            }
        }
        MagicCircleDecl.Entry signal = plan.declaration(MagicCircleDecl.Group.SIGNAL);
        StringBuilder line = new StringBuilder("终端#" + (plan.terminal + 1) + "（红石）：信号强度 15，点亮 " + lit + " 个方块");
        if (signal != null) line.append(" · 无线边界：").append(signal.primary()).append(" 格（无线中继下版本实装）");
        outcome.preview(Component.literal(line.toString()));
    }

    /** 烈焰终端：净化负面与元素附着；元素决定特化（冰解冻、光杀菌、风驱散）。 */
    private static void terminalPurify(ServerLevel level, MagicCircleProjectionBlockEntity entity,
                                       MagicCircleNetwork.Node node, MagicCircleGraph.Plan plan, ElementType element,
                                       java.util.Set<String> dyeTags, double durationFactor, double radius,
                                       Vec3 center, boolean test, Outcome outcome) {
        MagicCircleDecl.Entry purify = plan.declaration(MagicCircleDecl.Group.PURIFY);
        MagicCircleDecl.Entry status = plan.declaration(MagicCircleDecl.Group.STATUS);
        int clears = purify == null ? 1 : purify.primary();
        boolean keepAttachments = status != null && status.flag(0);
        int cleaned = 0;
        for (LivingEntity target : areaTargets(level, center, radius)) {
            if (target instanceof net.minecraft.world.entity.monster.Enemy && element != ElementType.POISON) {
                continue; // 净化默认只对友方；毒特化「消毒」连敌带友一起清
            }
            boolean did = false;
            for (var effect : List.of(MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.WITHER,
                    MobEffects.BLINDNESS, MobEffects.CONFUSION, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DARKNESS)) {
                if (cleaned >= clears) break;
                if (target.hasEffect(effect)) {
                    if (!test) target.removeEffect(effect);
                    cleaned++;
                    did = true;
                }
            }
            if (!keepAttachments && element == ElementType.ICE) {
                if (!test) target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                did = true;
            }
            if (did) outcome.heal++;
        }
        outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（烈焰"
                + (node.element().isEmpty() ? "" : "+" + MagicCirclePowders.shortName(node.element()))
                + "）：预计净化最多 " + clears + " 个负面，命中 " + cleaned + " 名友方"
                + (dyeTags.contains("guard") ? " · 守护染料：额外移除 1 个" : "")));
    }

    /** 萤石终端：照明 / 显形 / 投影；元素决定特化。 */
    private static void terminalLight(ServerLevel level, MagicCircleNetwork.Node node, MagicCircleGraph.Plan plan,
                                      ElementType element, java.util.Set<String> tags, java.util.Set<String> dyeTags,
                                      double radius, Vec3 center, double durationFactor, boolean test, Outcome outcome) {
        MagicCircleDecl.Entry info = plan.declaration(MagicCircleDecl.Group.INFO);
        double lightRadius = info == null ? radius : Math.max(4.0, info.primary());
        int revealed = 0;
        for (LivingEntity target : areaTargets(level, center, lightRadius)) {
            boolean hostile = target instanceof net.minecraft.world.entity.monster.Enemy;
            if (!test) {
                if (element == ElementType.POISON) {
                    if (hostile) {
                        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                                (int) Math.round(80 * durationFactor), 0));
                        revealed++;
                    }
                } else if (hostile || dyeTags.contains("insight")) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                            (int) Math.round(160 * durationFactor), 0));
                    revealed++;
                }
                if (element == ElementType.SOUND && !hostile) target.removeEffect(MobEffects.GLOWING);
            } else if (hostile) {
                revealed++;
            }
        }
        if (MagicCirclePowders.isArcane(node.element())) {
            if (!test) {
                for (int i = 0; i < 24; i++) {
                    level.sendParticles(ParticleTypes.END_ROD,
                            center.x + level.random.nextDouble() * 2 - 1, center.y + level.random.nextDouble() * 2,
                            center.z + level.random.nextDouble() * 2 - 1, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（萤石+奥术）：全息投影特效，范围 "
                    + (int) lightRadius + " 格"));
            return;
        }
        String kind = switch (element == null ? ElementType.LIGHT : element) {
            case POISON -> "致盲领域（黑暗效果）";
            case SOUND -> "隐匿屏障（移除显形）";
            case ICE -> "冰光照明";
            default -> "照明 / 显形";
        };
        outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（萤石）：" + kind + "，范围 "
                + (int) lightRadius + " 格，预计影响 " + revealed + " 个目标"));
    }

    /** 青金终端：金 = 挖矿（精准看词缀 / 声明），木 = 收割，光 = 鉴定，其余 = 扫描上聊天。 */
    private static void terminalScan(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity,
                                     MagicCircleNetwork.Node node, MagicCircleGraph.Plan plan, ElementType element,
                                     java.util.Set<String> affixIds, java.util.Set<String> tags, double radius,
                                     Vec3 center, boolean test, Outcome outcome, double strength,
                                     ServerPlayer actor) {
        if (element == ElementType.METAL || affixIds.contains("silk_mine") || affixIds.contains("mine")) {
            MagicCircleDecl.Entry interact = plan.declaration(MagicCircleDecl.Group.INTERACT);
            int perTrigger = interact == null ? MagicCircleConfig.MAX_BLOCKS_PER_TRIGGER.get() : interact.secondary();
            boolean silk = (interact != null && interact.flag(1)) || affixIds.contains("silk_mine")
                    || POWDER_LAPIS.equals(node.semantic());
            boolean toInventory = interact != null && interact.flag(2);
            if (test) {
                int perMinute = Math.min(interact == null ? Integer.MAX_VALUE : interact.primary(),
                        MagicCircleConfig.MAX_BLOCKS_PER_MINUTE.get());
                outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（青金+金）：预计单次挖掘 ≤ "
                        + perTrigger + " 块 · 每分钟 ≤ " + perMinute + " 块"
                        + (silk ? " · 带精准采集" : "") + (toInventory ? " · 产物直接进背包" : "")
                        + (interact == null ? "（没声明交互边界，走服务器默认限额）" : "")));
                return;
            }
            outcome.mined += mineV22(level, pos, entity, strength, test, perTrigger, silk, toInventory,
                    interact == null || interact.flag(0));
            return;
        }
        if (element == ElementType.NATURE) {
            int harvested = harvestCrops(level, center, radius, test);
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（青金+木）：预计收割成熟作物 "
                    + harvested + " 格"));
            return;
        }
        int scanned = 0;
        StringBuilder names = new StringBuilder();
        MagicCircleDecl.Entry info = plan.declaration(MagicCircleDecl.Group.INFO);
        double scanRadius = info == null ? radius : Math.max(4.0, info.primary());
        for (LivingEntity target : areaTargets(level, center, scanRadius)) {
            scanned++;
            if (!test && scanned <= 5) {
                names.append(names.length() == 0 ? "" : "、").append(target.getDisplayName().getString())
                        .append("(").append(String.format(Locale.ROOT, "%.0f", target.getHealth())).append("♥)");
            }
        }
        if (!test && actor != null && names.length() > 0) {
            actor.displayClientMessage(Component.literal("扫描：" + names).withStyle(ChatFormatting.AQUA), false);
        }
        outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（青金）：扫描半径 "
                + (int) scanRadius + " 格，预计上报 " + scanned + " 个目标"
                + (element == ElementType.LIGHT ? " · 洞察：含元素附着与魔法痕迹" : "")));
    }

    /** 紫晶终端：奥术充能 / 金探测金属 / 毒随机传送 / 风定向传送 / 音共鸣。 */
    private static void terminalCharge(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity,
                                       MagicCircleNetwork.Node node, MagicCircleGraph.Plan plan, ElementType element,
                                       boolean arcaneElement, java.util.Set<String> tags, double radius, Vec3 center,
                                       boolean test, Outcome outcome, double strength, ServerPlayer actor,
                                       ServerPlayer caster, boolean pvp) {
        MagicCircleDecl.Entry energy = plan.declaration(MagicCircleDecl.Group.ENERGY);
        MagicCircleDecl.Entry teleport = plan.declaration(MagicCircleDecl.Group.TELEPORT);
        if (arcaneElement || element == null || tags.contains("charge")) {
            int mana = energy == null ? 10 : energy.primary();
            ServerPlayer target = energy != null && energy.enumValue(0) == 1 ? caster : (actor != null ? actor : caster);
            if (!test && target != null) ManaData.restoreMana(target, mana, false);
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（紫晶+奥术）：预计充能 " + mana
                    + " 点魔素 → " + (target == null ? "触发者" : target.getName().getString())));
            return;
        }
        if (element == ElementType.METAL || tags.contains("detect")) {
            int found = scanMetal(level, center, 8, test);
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（紫晶+金）：探测到金属矿脉 " + found + " 块（半径 8）"));
            return;
        }
        if (element == ElementType.POISON) {
            boolean hostileOnly = teleport == null || teleport.flag(2);
            int dist = teleport == null ? 8 : teleport.primary();
            boolean crossDim = teleport != null && teleport.flag(0);
            boolean allowPvPTp = pvp && teleport != null && teleport.flag(1);
            int moved = 0;
            for (LivingEntity target : areaTargets(level, center, 3.0)) {
                boolean hostile = target instanceof net.minecraft.world.entity.monster.Enemy;
                if (target instanceof Player player ? !allowPvPTp : (!hostile || hostileOnly)) continue;
                moved++;
                if (!test) {
                    // 安全落点：水平随机 + 从上往下找第一格「脚下有实地、头上一格是空气」的位置。
                    for (int attempt = 0; attempt < 20 && !teleportSafely(level, target, center, dist); attempt++) {
                        // 试 20 次找不到就作罢，绝不把生物塞进石头里。
                    }
                }
            }
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（紫晶+毒）：随机传送最多 " + moved
                    + " 个目标，距离 ≤ " + dist + " 格" + (crossDim ? " · 声明了跨维度（本版仍禁）" : " · 禁跨维度")));
            return;
        }
        if (element == ElementType.WIND) {
            int dist = teleport == null ? 8 : teleport.primary();
            int moved = 0;
            for (LivingEntity target : areaTargets(level, center, radius)) {
                boolean hostile = target instanceof net.minecraft.world.entity.monster.Enemy;
                if (!hostile && (teleport == null || !teleport.flag(1))) continue;
                moved++;
                if (!test) {
                    // 定向：沿「锚点 → 目标」的水平方向推，力量按声明距离折算。
                    Vec3 dir = target.position().subtract(center)
                            .with(net.minecraft.core.Direction.Axis.Y, 0).normalize();
                    target.push(dir.x * dist * 0.12, 0.25, dir.z * dist * 0.12);
                }
            }
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（紫晶+风）：定向传送 / 击退 " + moved
                    + " 个目标，距离 " + dist + " 格（默认只推敌对）"));
            return;
        }
        if (element == ElementType.SOUND) {
            float dmg = damageOf(ElementType.SOUND, strength) * (energy == null ? 1.0F : 1.0F);
            int hits = 0;
            for (LivingEntity target : areaTargets(level, center, radius)) {
                if (!(target instanceof net.minecraft.world.entity.monster.Enemy)) continue;
                if (!test) SpellDamage.apply(target, level.damageSources().magic(), dmg);
                hits++;
                outcome.hurt++;
            }
            outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（紫晶+音）：共鸣音伤 "
                    + String.format(Locale.ROOT, "%.1f", dmg) + " × " + hits + " 个敌对目标"));
            return;
        }
        outcome.preview(Component.literal("终端#" + (plan.terminal + 1) + "（紫晶）：稳固 / 相位移相 —— 本版本该特化未开放"));
    }

    // ---------------------------------------------------------------- v2.2 小工具

    /** v2.2 挖矿：预算 / 精准 / 掉落去向 / 区块检查全部按「交互边界」声明执法。 */
    private static int mineV22(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity,
                               double strength, boolean test, int perTrigger, boolean silk, boolean toInventory,
                               boolean requireChunk) {
        if (!MagicCircleConfig.MINING_ENABLED.get() || test) return 0;
        long now = level.getGameTime();
        int budget = Math.min(Math.min(perTrigger, MagicCircleConfig.MAX_BLOCKS_PER_TRIGGER.get()),
                entity.miningBudget(now));
        if (budget <= 0) return 0;
        int radius = 1 + Math.min(3, (int) Math.floor(strength));
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius),
                pos.offset(radius, radius, radius))) {
            if (targets.size() >= budget) break;
            if (candidate.equals(pos)) continue;
            if (!level.isInWorldBounds(candidate)) continue;
            if (requireChunk && !level.hasChunkAt(candidate)) continue;
            BlockState state = level.getBlockState(candidate);
            if (state.isAir()) continue;
            if (state.getBlock() instanceof MagicCircleProjectionBlock || state.getBlock() instanceof MagicCircleBaseBlock) {
                continue;
            }
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
        if (silk && MagicCircleConfig.ALLOW_SILK_TOUCH.get()) {
            tool.enchant(level.holderLookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SILK_TOUCH), 1);
        }
        int mined = 0;
        for (BlockPos target : targets) {
            BlockState state = level.getBlockState(target);
            if (state.isAir()) continue;
            BlockEntity blockEntity = level.getBlockEntity(target);
            List<ItemStack> drops = Block.getDrops(state, level, target, blockEntity, null, tool);
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            ServerPlayer owner = ownerPlayer(level, entity);
            if (toInventory && owner != null) {
                for (ItemStack drop : drops) {
                    if (!owner.getInventory().add(drop)) Block.popResource(level, target, drop);
                }
            } else {
                for (ItemStack drop : drops) Block.popResource(level, target, drop);
            }
            mined++;
        }
        entity.addMining(mined, now);
        return mined;
    }

    /** 木特化「收割」：打掉成熟作物并掉落。 */
    private static int harvestCrops(ServerLevel level, Vec3 center, double radius, boolean test) {
        int count = 0;
        BlockPos origin = BlockPos.containing(center);
        int r = (int) Math.ceil(radius);
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (!level.isLoaded(candidate)) continue;
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop
                    && crop.isMaxAge(state)) {
                count++;
                if (!test) {
                    level.destroyBlock(candidate, true); // 自带掉落（小麦 + 种子）
                }
            }
        }
        return count;
    }

    private static final Set<String> METAL_ORES = Set.of(
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:copper_ore",
            "minecraft:deepslate_copper_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "minecraft:nether_gold_ore", "minecraft:raw_iron_block", "minecraft:raw_gold_block",
            "minecraft:raw_copper_block", "minecraft:iron_block", "minecraft:gold_block");

    /** 金特化「探测金属」：数半径里的金属矿，试运行只数不播报。 */
    private static int scanMetal(ServerLevel level, Vec3 center, int radius, boolean test) {
        int found = 0;
        BlockPos origin = BlockPos.containing(center);
        int r = Math.min(16, radius);
        for (BlockPos candidate : BlockPos.withinManhattan(origin, r, r, r)) {
            if (!level.isLoaded(candidate)) continue;
            String id = builtInId(level.getBlockState(candidate).getBlock());
            if (METAL_ORES.contains(id)) found++;
        }
        return found;
    }

    /** 词缀标签的中文短名（预览行拼接用）。 */
    private static String tagName(String tag) {
        return switch (tag) {
            case "mine" -> "挖掘";
            case "silk" , "silk_mine" -> "精准采集";
            case "heal" -> "治疗";
            case "damage" -> "伤害";
            case "plant" -> "种植";
            case "silence" -> "静音";
            case "boost" -> "效果提升";
            case "debuff" -> "负面附加";
            case "buff" -> "增益附加";
            case "summon" -> "召唤";
            case "explosion" -> "爆炸";
            case "spread" -> "强扩散";
            case "purify" -> "净化";
            case "expand" -> "扩大范围";
            case "charge" -> "充能";
            case "detect" -> "探测";
            case "teleport" -> "传送";
            case "pierce" -> "破甲";
            case "freeze" -> "冻结";
            case "irrigate" -> "灌溉";
            case "resonance" -> "震荡";
            case "stabilize" -> "稳固";
            case "light" -> "照明";
            case "signal" -> "信号";
            default -> tag;
        };
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
        for (MagicCircleBlueprint blueprint : entity.layers()) {
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
                List<MagicCircleNetwork.Node> nodes = layer.nodes();
                for (int i = 0; i < nodes.size(); i++) {
                    MagicCircleNetwork.Node node = nodes.get(i);
                    if (node.type() != MagicCircleBlockType.TRIGGER) continue;
                    if (!"minecraft:redstone".equals(node.semantic())) continue;
                    // 底座形态的触发节点：判定 100% 交给上游门控，红石槽停用（v2.6 一 2）。
                    if (MagicCircleGraph.triggerBaseMode(layer, i)) continue;
                    return true;
                }
            }
        }
        return false;
    }

    /** v2.1 起染料槽在触发块上是阈值 / 反应类型，不再是「黄色染料 = 计时」；计时自动触发已移除。 */

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
     * 一键停机（停机键 / 面板底部的「全部停机」 / {@code stopall} 指令都走这里）：
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

    // ---------------------------------------------------------------- 远程法阵面板

    /** 远程面板 / 停机键要认的锚点：刻印过、有层、主人是这个人。 */
    private static boolean ownedBy(MagicCircleProjectionBlockEntity anchor, ServerPlayer player) {
        return anchor.sealed() && !anchor.isEmpty()
                && player.getUUID().equals(anchor.owner());
    }

    /** 锚点最上面那层的名字（远程面板上显示）。 */
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

    /** 把玩家附近自己的刻印锚点打包发给客户端（远程面板用）。 */
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

    /** 远程面板点了一下：校验主人 / 距离，然后走正常的开关流程。 */
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

    // ================================================================ 门控（规范 v2.4 / v2.6 / v3.0）

    /** 试运行收集的一次门控判定：节点下标 + 结果，供悬浮大字与聊天回放。 */
    public record GateShot(int node, MagicCircleGates.Verdict verdict) {
    }


    /**
     * 触发预检：走一遍每层每个终端上游的门控链（串联 AND 短路、并联 OR）。
     *
     * @return {@code int[3]}：{@code [0]}＝带门控的终端数，{@code [1]}＝放行数，{@code [2]}＝被拦数。
     *         {@code [0]==0} 表示没摆门控，预检不拦。
     */
    private static int[] gatePrecheck(ServerLevel level, BlockPos pos, Direction facing,
                                      MagicCircleProjectionBlockEntity entity, ServerPlayer caster) {
        int gated = 0;
        int passed = 0;
        int blocked = 0;
        Vec3 anchor = surfaceCenter(level, pos, facing, 1.5);
        for (MagicCircleBlueprint blueprint : entity.layers()) {
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
                if (layer.nodes().isEmpty()) continue;
                MagicCircleGraph.Analysis analysis = MagicCircleGraph.analyze(layer, false, false);
                for (MagicCircleGraph.Plan plan : analysis.terminals) {
                    if (plan.gateBranches.isEmpty()) continue;
                    gated++;
                    if (planGatesPass(layer, plan, level, anchor, caster, null)) passed++;
                    else blocked++;
                }
            }
        }
        return new int[]{gated, passed, blocked};
    }

    /**
     * 求值一个终端的全部门控链：分支内串联 AND（首个 F 立即短路，绝不查后面的条件），
     * 分支间并联 OR（首条放行即返回）。{@code shots} 非空时逐块收集判定给试运行可视化。
     */
    private static boolean planGatesPass(MagicCircleNetwork.Layer layer, MagicCircleGraph.Plan plan,
                                         ServerLevel level, Vec3 anchor, ServerPlayer caster,
                                         List<GateShot> shots) {
        List<MagicCircleNetwork.Node> nodes = layer.nodes();
        for (List<Integer> branch : plan.gateBranches) {
            List<MagicCircleNetwork.Node> chain = new ArrayList<>();
            for (int index : branch) chain.add(nodes.get(index));
            List<MagicCircleGates.Verdict> verdicts = shots == null ? null : new ArrayList<>();
            boolean pass = MagicCircleGates.evaluateBranch(chain, level, anchor, caster, verdicts);
            if (verdicts != null) {
                for (int s = 0; s < verdicts.size(); s++) {
                    shots.add(new GateShot(branch.get(s), verdicts.get(s)));
                }
            }
            if (pass) return true; // 并联 OR：一条支路放行就够，后面的支路不再查。
        }
        return false;
    }

    /**
     * 试运行门控可视化（调试规范 v2.4 六）：放行节点炸一撮绿色尘粒、拦截节点炸红色尘粒，
     * 判定明细同时走聊天数据行（{@code outcome.preview}）。用粒子代替悬浮文字实体：
     * 免维护、区块卸载零残留，红绿一眼分。
     */
    private static void showGateReadouts(ServerLevel level, BlockPos pos,
                                         MagicCircleProjectionBlockEntity entity,
                                         MagicCircleNetwork.Layer layer, List<GateShot> shots) {
        if (shots.isEmpty()) return;
        Direction facing = entity.getBlockState().getValue(MagicCircleProjectionBlock.FACING);
        for (GateShot shot : shots) {
            MagicCircleNetwork.Node node = layer.nodes().get(shot.node());
            float[] local = MagicCircleProjectionGeometry.local(facing, entity.baseW(), entity.baseH(),
                    node.x(), node.y(), 0.35F);
            double x = pos.getX() + local[0];
            double y = pos.getY() + local[1];
            double z = pos.getZ() + local[2];
            // 绿泡泡 = T（放行），红怒脸 = F（拦截）：远距离也一眼分色。
            level.sendParticles(shot.verdict().pass() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER,
                    x, y, z, 14, 0.14, 0.18, 0.14, 0.015);
            level.sendParticles(shot.verdict().pass() ? ParticleTypes.END_ROD : ParticleTypes.POOF,
                    x, y + 0.25, z, 8, 0.1, 0.15, 0.1, 0.01);
        }
        level.playSound(null, pos, shotAllPass(shots)
                        ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                SoundSource.BLOCKS, 0.5F, shotAllPass(shots) ? 1.4F : 0.6F);
    }

    private static boolean shotAllPass(List<GateShot> shots) {
        for (GateShot shot : shots) {
            if (!shot.verdict().pass()) return false;
        }
        return true;
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
