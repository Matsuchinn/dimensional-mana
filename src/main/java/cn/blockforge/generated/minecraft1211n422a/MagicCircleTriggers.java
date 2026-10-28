package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * v2.1 触发节点的全部「检测媒介」：语义槽决定法阵在什么条件下开始运转。
 *
 * <table border="1">
 *   <caption>语义槽 → 触发媒介</caption>
 *   <tr><td>红石粉 / 空</td><td>外部红石信号（走方块自身的 neighborChanged，这里不管）</td></tr>
 *   <tr><td>紫水晶粉</td><td>魔素检测：周围生物魔素百分比 ≥ 阈值</td></tr>
 *   <tr><td>糖</td><td>生命检测：周围生物生命百分比 ≤ 阈值（挑残血的打）</td></tr>
 *   <tr><td>火药</td><td>爆炸检测：周围刚发生过爆炸</td></tr>
 *   <tr><td>青金石粉</td><td>经验检测：周围有经验球</td></tr>
 *   <tr><td>烈焰粉</td><td>元素反应检测：周围刚发生过（某类）元素反应</td></tr>
 *   <tr><td>萤石粉</td><td>生物数量检测：周围 5 格内生物数量 ≥ 阈值</td></tr>
 * </table>
 *
 * <p>染料槽是阈值：不放 0，白 6.25% …… 品红 100%（16 档）；数检按个数，白 = 1 个 …… 品红 = 16 个；
 * 反应检测按类型，白 = 增幅、红 = 剧变、淡蓝 = 控制、黄 = 充能、橙 = 持续、黄绿 = 扩散、灰 = 次级，
 * 其余染料不限制反应类型。</p>
 *
 * <p>爆炸与反应是「瞬时事件」，光靠一 tick 的扫描看不见，所以这里维护两条短记忆队列
 * （5 秒内的事件），由 {@link ElementReactionSystem} 与 {@link ManaEvents} 记档，扫描时只认
 * 「上次触发之后新发生」的那些。</p>
 */
public final class MagicCircleTriggers {

    /** 检测半径（格）：默认检测上方和周边，实际按锚点向外这个范围扫。 */
    private static final double SCAN_RADIUS = 5.0;
    /** 爆炸 / 反应事件的记忆时长（刻）：5 秒之内发生的算「刚刚发生」。 */
    private static final long EVENT_TTL = 100L;
    /** 记忆队列容量：再多就丢掉最老的。 */
    private static final int EVENT_CAP = 128;
    /** 反应检测的事件半径（格）：反应离阵太远的算不到。 */
    private static final double EVENT_RADIUS = 12.0;

    private record TimedEvent(BlockPos pos, Reaction reaction, long at) {}

    private static final Deque<TimedEvent> EXPLOSIONS = new ArrayDeque<>();
    private static final Deque<TimedEvent> REACTIONS = new ArrayDeque<>();

    /** 染料阈值档位：16 档等比从 6.25% 递增到品红 100%（设计规范 v2.1 触发小节）。 */
    private static final String[] DYE_LADDER = {
            "minecraft:white_dye", "minecraft:orange_dye", "minecraft:pink_dye", "minecraft:yellow_dye",
            "minecraft:lime_dye", "minecraft:green_dye", "minecraft:cyan_dye", "minecraft:light_blue_dye",
            "minecraft:blue_dye", "minecraft:purple_dye", "minecraft:brown_dye", "minecraft:gray_dye",
            "minecraft:light_gray_dye", "minecraft:red_dye", "minecraft:black_dye", "minecraft:magenta_dye",
    };

    private MagicCircleTriggers() {}

    // ---------------------------------------------------------------- 事件记档

    /** 元素反应发生时记一笔（服务器线程调用）。 */
    public static void recordReaction(BlockPos pos, Reaction reaction, long gameTick) {
        synchronized (REACTIONS) {
            TimedEvent event = new TimedEvent(pos.immutable(), reaction, gameTick);
            REACTIONS.addLast(event);
            while (REACTIONS.size() > EVENT_CAP) REACTIONS.removeFirst();
        }
    }

    /** 爆炸发生时记一笔。 */
    public static void recordExplosion(ServerLevel level, double x, double y, double z) {
        synchronized (EXPLOSIONS) {
            push(EXPLOSIONS, new TimedEvent(new BlockPos((int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.floor(z)), null, level.getGameTime()));
        }
    }

    private static void push(Deque<TimedEvent> queue, TimedEvent event) {
        queue.addLast(event);
        while (queue.size() > EVENT_CAP) queue.removeFirst();
    }

    /** 清掉过期事件并整体清空（世界卸载 / 服务器停止时调用，避免残留旧刻导致误判）。 */
    public static void clearEvents() {
        synchronized (EXPLOSIONS) {
            EXPLOSIONS.clear();
        }
        synchronized (REACTIONS) {
            REACTIONS.clear();
        }
    }

    // ---------------------------------------------------------------- 阈值换算

    /** 染料 → 百分比阈值（0.0 ~ 1.0）：不放 0，白 6.25% 递增到品红 100%。 */
    public static double dyeThreshold(String dye) {
        int index = dyeIndex(dye);
        return index < 0 ? 0.0 : (index + 1) / 16.0;
    }

    /** 染料 → 生物个数阈值（0 ~ 16）：不放 0 个，白 1 个递增到品红 16 个。 */
    public static int dyeCount(String dye) {
        int index = dyeIndex(dye);
        return index < 0 ? 0 : index + 1;
    }

    /** 染料 → 反应类型过滤（反应检测用）：认不出的染料不限制。 */
    public static Reaction.Category dyeReactionCategory(String dye) {
        if (dye == null || dye.isEmpty()) return null;
        return switch (dye) {
            case "minecraft:white_dye" -> Reaction.Category.AMPLIFY;
            case "minecraft:red_dye" -> Reaction.Category.DRAMATIC;
            case "minecraft:light_blue_dye" -> Reaction.Category.CONTROL;
            case "minecraft:yellow_dye" -> Reaction.Category.CHARGE;
            case "minecraft:orange_dye" -> Reaction.Category.SUSTAIN;
            case "minecraft:lime_dye" -> Reaction.Category.DIFFUSE;
            case "minecraft:gray_dye" -> Reaction.Category.SECONDARY;
            default -> null;
        };
    }

    private static int dyeIndex(String dye) {
        if (dye == null) return -1;
        for (int i = 0; i < DYE_LADDER.length; i++) {
            if (DYE_LADDER[i].equals(dye)) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- 扫描

    /**
     * 运行中的锚点每几刻调一次：看这块阵里的触发块用了哪种检测媒介，条件满足就打一发。
     * 红石媒介不在这里管（走方块自己的 neighborChanged）；手动右键随时可触发，也不算这里。
     */
    public static void scan(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity) {
        if (!entity.running()) return;
        long now = level.getGameTime();
        long last = entity.lastTriggerTick();

        // v2.6 一 2 底座模式：上游挂着门控条件的触发节点自身判定全部停用，
        // 改由这里每 20 刻轮询一次上游门控——绿了才开火（性能防线二：统一节律）。
        if (now % 20 == 0 && pollBaseGates(level, pos, entity)) return;

        List<MagicCircleNetwork.Node> triggers = new ArrayList<>();
        for (MagicCircleBlueprint blueprint : entity.layers()) {
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
                List<MagicCircleNetwork.Node> nodes = layer.nodes();
                for (int i = 0; i < nodes.size(); i++) {
                    MagicCircleNetwork.Node node = nodes.get(i);
                    if (node.type() != MagicCircleBlockType.TRIGGER) continue;
                    if (MagicCircleGraph.triggerBaseMode(layer, i)) continue; // 底座：走上游轮询
                    triggers.add(node);
                }
            }
        }
        if (triggers.isEmpty()) return;

        AABB box = null;
        List<LivingEntity> nearbyLiving = null;
        for (MagicCircleNetwork.Node trigger : triggers) {
            String semantic = trigger.semantic();
            if (semantic.isEmpty() || "minecraft:redstone".equals(semantic)) continue;
            if (box == null) {
                box = AABB.ofSize(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
                nearbyLiving = level.getEntitiesOfClass(LivingEntity.class, box);
            }
            boolean fired = switch (semantic) {
                case "dimensional_mana:amethyst_dust" -> manaCheck(nearbyLiving, trigger.dye());
                case "minecraft:sugar" -> healthCheck(nearbyLiving, trigger.dye());
                case "minecraft:glowstone_dust" -> mobCountCheck(nearbyLiving, trigger.dye());
                case "minecraft:gunpowder" -> explosionCheck(pos, now, last);
                case "dimensional_mana:lapis_dust" -> experienceCheck(level, box);
                case "minecraft:blaze_powder" -> reactionCheck(pos, now, last, trigger.dye());
                default -> false;
            };
            if (fired) {
                MagicCircleRites.triggerBySensor(level, pos, entity);
                return;
            }
        }
    }

    /** 底座门控轮询：任一底座触发的上游门控链放行，就走传感触发（后面还有冷却兜底）。 */
    private static boolean pollBaseGates(ServerLevel level, BlockPos pos,
                                         MagicCircleProjectionBlockEntity entity) {
        boolean sawBase = false;
        for (MagicCircleBlueprint blueprint : entity.layers()) {
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
                List<MagicCircleNetwork.Node> nodes = layer.nodes();
                for (int i = 0; i < nodes.size(); i++) {
                    if (nodes.get(i).type() != MagicCircleBlockType.TRIGGER) continue;
                    if (!MagicCircleGraph.triggerBaseMode(layer, i)) continue;
                    sawBase = true;
                    List<MagicCircleNetwork.Node> chain = upstreamGateChain(layer, i);
                    if (chain.isEmpty()) continue;
                    net.minecraft.core.Direction facing = entity.getBlockState()
                            .getValue(MagicCircleProjectionBlock.FACING);
                    net.minecraft.world.phys.Vec3 anchor = net.minecraft.world.phys.Vec3.atCenterOf(pos).add(
                            facing.getStepX() * 1.5, facing.getStepY() * 1.5, facing.getStepZ() * 1.5);
                    if (MagicCircleGates.evaluateBranch(chain, level, anchor, null, null)) {
                        MagicCircleRites.triggerBySensor(level, pos, entity);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 底座触发上游串着的门控条件链（沿连线倒着走；只穿门控条件，按源头→触发排序）。 */
    private static List<MagicCircleNetwork.Node> upstreamGateChain(MagicCircleNetwork.Layer layer,
                                                                   int triggerIndex) {
        List<MagicCircleNetwork.Node> nodes = layer.nodes();
        List<MagicCircleNetwork.Node> chain = new ArrayList<>();
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        boolean[] seen = new boolean[nodes.size()];
        seen[triggerIndex] = true;
        queue.add(triggerIndex);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (MagicCircleNetwork.Link link : layer.links()) {
                if (link.to() != current || link.from() < 0 || link.from() >= nodes.size()) continue;
                if (seen[link.from()]) continue;
                MagicCircleNetwork.Node up = nodes.get(link.from());
                if (up.type() == MagicCircleBlockType.CONDITION
                        && MagicCircleGates.isGateMode(up.semantic(), up.gate())) {
                    seen[link.from()] = true;
                    chain.add(up);
                    queue.add(link.from());
                }
            }
        }
        java.util.Collections.reverse(chain); // 从最上游开始短路求值（贵的一律排在后面）
        return chain;
    }

    /** 魔检：周围有生物（含玩家）的魔素百分比达到阈值。 */
    private static boolean manaCheck(List<LivingEntity> nearby, String dye) {
        double threshold = dyeThreshold(dye);
        for (LivingEntity living : nearby) {
            if (living.isSpectator() || living.isDeadOrDying()) continue;
            ManaData.Snapshot snapshot = ManaData.get(living);
            if (!(snapshot.maxMana() > 0.0)) continue;
            if (snapshot.mana() / snapshot.maxMana() >= threshold) return true;
        }
        return false;
    }

    /** 血检：周围有生物的生命百分比掉到阈值以下（不放染料 = 0%，等于不触发）。 */
    private static boolean healthCheck(List<LivingEntity> nearby, String dye) {
        double threshold = dyeThreshold(dye);
        if (threshold <= 0.0) return false;
        for (LivingEntity living : nearby) {
            if (living.isSpectator() || living.isDeadOrDying()) continue;
            float max = living.getMaxHealth();
            if (max <= 0.0F) continue;
            if (living.getHealth() / max <= threshold) return true;
        }
        return false;
    }

    /** 数检：周围 5 格内生物数量达到阈值（玩家不算生物；至少要有 1 只，没放染料 = 1 只起判）。 */
    private static boolean mobCountCheck(List<LivingEntity> nearby, String dye) {
        int required = Math.max(1, dyeCount(dye));
        int count = 0;
        for (LivingEntity living : nearby) {
            if (living instanceof Player || living.isSpectator() || living.isDeadOrDying()) continue;
            count++;
            if (count >= required) return true;
        }
        return false;
    }

    /** 爆炸检测：上次触发之后，附近刚炸过。 */
    private static boolean explosionCheck(BlockPos pos, long now, long lastTrigger) {
        synchronized (EXPLOSIONS) {
            evict(EXPLOSIONS, now);
            for (TimedEvent event : EXPLOSIONS) {
                if (event.at() <= lastTrigger) continue;
                if (pos.closerThan(event.pos(), EVENT_RADIUS)) return true;
            }
        }
        return false;
    }

    /** 经验检测：范围内飘着经验球。 */
    private static boolean experienceCheck(ServerLevel level, AABB box) {
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box);
        for (ExperienceOrb orb : orbs) {
            if (orb.value > 0) return true;
        }
        return false;
    }

    /** 反应检测：上次触发之后，附近发生过（染料指定类别的）元素反应。 */
    private static boolean reactionCheck(BlockPos pos, long now, long lastTrigger, String dye) {
        Reaction.Category wanted = dyeReactionCategory(dye);
        synchronized (REACTIONS) {
            evict(REACTIONS, now);
            for (TimedEvent event : REACTIONS) {
                if (event.at() <= lastTrigger) continue;
                if (event.reaction() == null) continue;
                if (wanted != null && event.reaction().category() != wanted) continue;
                if (pos.closerThan(event.pos(), EVENT_RADIUS)) return true;
            }
        }
        return false;
    }

    private static void evict(Deque<TimedEvent> queue, long now) {
        while (!queue.isEmpty() && now - queue.peekFirst().at() > EVENT_TTL) {
            queue.removeFirst();
        }
    }
}
