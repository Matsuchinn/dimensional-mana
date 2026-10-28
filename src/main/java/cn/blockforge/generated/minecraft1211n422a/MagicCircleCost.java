package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 魔法阵的消耗与强度计算，严格照《次元物语：魔导》设计规范 v2.0 / v2.1：
 *
 * <pre>
 * 最终节点消耗 = 节点基础魔素
 *              × (1 - 功率槽提供的效率)      本节点功率槽：下1 / 中2 / 高4 / 极6 / 特8 / 超导12 (%)
 *              × (1 - 总效率减免)            核心语义(糖+5%) + 核心元素(奥术+4%/8%) + 稳定块/超导连线加成，上限 75%
 *              × (1 + 总紊乱值 × 10%)        紊乱来自输出块与核心块元素槽之间的元素反应配对
 *              × (1 + 全局魔素损耗)          核心超频(烈焰粉)模式 +25%，其余 0
 *
 * 规模 S = 1 + 0.15 × (N - 1)       N = 基座数量
 * 层数 D = 1 + 0.30 × (L - 1)       L = 有内容的层数
 * 重叠 O = 1 + 0.20 × 重叠块数
 * 启动 = ceil(Σ节点启动 × S × D × O)；维持 = ceil(Σ节点维持 × S × D)（不吃重叠）；
 * 触发 = ceil(Σ(输出+触发)节点 × S × D × O)
 * 强度 P = (1 + 0.10×(N-1)) × (1 + 0.15×(L-1)) × (1 + 0.05×增幅块数)
 * </pre>
 *
 * <p><b>紊乱值（v2.1 修正）</b>：只扫描<b>核心块与输出块</b>的元素槽。能触发元素反应的两个槽位
 * 配成一对，一个槽位至多与一个槽位成对（多个候选时取连接距离最近的那个）——2 火 1 冰只会算
 * 1 火 1 冰的一对紊乱。每对按反应类别记紊乱：增幅（蒸发、融化）2、剧变（超载、感电、超导、
 * 激发）3、控制 / 充能 / 持续 / 扩散 / 次级各 1。核心火药（增强）模式给剧变、增幅对 +1，
 * 烈焰粉（超频）模式给剧变、增幅对 +2、其余对 +1；稳定块每块再压制固定点数的紊乱。</p>
 *
 * <p>系数全部来自 {@link MagicCircleConfig}，服务器可以整体调整。</p>
 */
public final class MagicCircleCost {

    /** 一次计算的全部结果，界面与调试棒都直接读它。 */
    public record Evaluation(int startMana, int upkeepMana, int triggerMana, double strength,
                             int complexity, int disorder, int nodeCount, int filledLayers,
                             int baseCount, double efficiency, List<String> problems,
                             String coreElement, double outputBonus, double durationBonus) {
        public boolean runnable() { return problems.isEmpty(); }
    }

    /**
     * 一层的「核心」上下文：语义槽定模式，元素槽定属性偏好。
     * 语义槽七档——红石(默认)/糖(效率)/火药(增强)/烈焰粉(超频)/萤石粉(延时)/青金石(经验)/紫水晶(节能)。
     */
    private record CoreContext(String semantic, String element) {
        boolean sugar() { return "minecraft:sugar".equals(semantic); }
        boolean enhance() { return "minecraft:gunpowder".equals(semantic); }
        boolean overclock() { return "minecraft:blaze_powder".equals(semantic); }
        boolean delay() { return "minecraft:glowstone_dust".equals(semantic); }
        boolean experience() { return "dimensional_mana:lapis_dust".equals(semantic); }
        boolean eco() { return "dimensional_mana:amethyst_dust".equals(semantic); }

        /** 核心元素槽里的具体元素；空槽或奥术返回 null（奥术只走全局加成那条线）。 */
        ElementType elementOf() {
            return MagicCirclePowders.isArcane(element) ? null : MagicCirclePowders.elementOf(element);
        }

        boolean arcane() { return MagicCirclePowders.isArcane(element); }

        /** 奥术元素的全局效率加成：默认 / 红石模式 +4%，效率（糖）模式 +8%。 */
        double arcaneBonus() {
            if (!arcane()) return 0.0;
            return sugar() ? 0.08 : 0.04;
        }
    }

    /** 参与紊乱配对的一个元素槽：所在层、层内节点下标、画布坐标与粉末 id。 */
    private record ElementSlot(int layer, int node, int x, int y, String itemId) {}

    /** 一个候选配对：距离（越小越优先）、反应优先级、两端槽位下标。 */
    private record PairCandidate(int distance, int priority, int a, int b, Reaction reaction) {}

    public static Evaluation evaluate(MagicCircleNetwork network) {
        return evaluate(network.layers(), network.baseCount());
    }

    /**
     * 不含「阵盘」也能算消耗：蓝图（还没贴出去的投影、手里的蓝图物品）只有层列表和画布尺寸，
     * 走的是同一套公式，所以预览 / 试运行 / 界面里显示的数字永远对得上。
     *
     * <p><b>每层是独立的一张阵图</b>（设计文档 10.1）：核心、触发、输出、连线都在层内判断，
     * 层与层之间默认不连线。所以「有 4 层、每层一个核心」是合法的四层阵，不是「多个核心」；
     * 真正非法的只有「同一层里有两个核心」。多层之间只按层数系数 D 提高消耗与强度。</p>
     */
    public static Evaluation evaluate(List<MagicCircleNetwork.Layer> layerList, int baseCount) {
        double sumStart = 0.0;
        double sumUpkeep = 0.0;
        double sumTrigger = 0.0;
        int complexity = 0;
        int amplify = 0;
        int stabilizers = 0;
        int superconductors = 0;
        int nodeCount = 0;
        int overlap = 0;
        int linkCount = 0;
        int filledLayers = 0;

        Set<String> problems = new LinkedHashSet<>();
        Map<Long, Integer> positions = new HashMap<>();

        // ---- 第一遍：结构统计、每层核心、紊乱配对用的元素槽 ----
        List<CoreContext> layerCores = new ArrayList<>();
        List<ElementSlot> slots = new ArrayList<>();
        for (int layerIndex = 0; layerIndex < layerList.size(); layerIndex++) {
            MagicCircleNetwork.Layer layer = layerList.get(layerIndex);
            if (layer.nodes().isEmpty()) {
                layerCores.add(null);
                continue;
            }
            filledLayers++;
            linkCount += layer.links().size();

            int coreCount = 0;
            boolean layerSupply = false;
            boolean layerTrigger = false;
            boolean layerOutput = false;
            int layerNodes = 0;
            CoreContext core = null;
            for (int i = 0; i < layer.nodes().size(); i++) {
                MagicCircleNetwork.Node node = layer.nodes().get(i);
                MagicCircleBlockType type = node.type();
                if (type == null) continue;
                nodeCount++;
                layerNodes++;
                complexity += type.complexity()
                        + (node.handDrawn() ? MagicCircleConfig.HAND_DRAWN_COMPLEXITY.get() : 0);
                if (type == MagicCircleBlockType.AMPLIFY) amplify++;
                if (type == MagicCircleBlockType.STABILIZE) stabilizers++;
                if (MagicCirclePowders.isSuperconductor(node.power())) superconductors++;
                if (type.isCore()) {
                    coreCount++;
                    if (core == null) core = new CoreContext(node.semantic(), node.element());
                }
                if (type == MagicCircleBlockType.SUPPLY) layerSupply = true;
                if (type.isTrigger()) layerTrigger = true;
                if (type.isOutput()) layerOutput = true;
                // v2.1：只有核心块与输出块的元素槽参与紊乱配对。
                if ((type.isCore() || type.isOutput()) && !node.element().isEmpty()) {
                    slots.add(new ElementSlot(layerIndex, i, node.x(), node.y(), node.element()));
                }
                long key = (long) node.x() * 4096L + node.y();
                if (positions.merge(key, 1, Integer::sum) > 1) overlap++;
            }
            layerCores.add(core);
            if (layerNodes == 0) continue;
            // 源头可以是核心，也可以是一块「供能」：供能 = 这一层的输入，
            // 没有核心、但有一块供能当头，照样算这一层有源头，不再报「缺少核心块」。
            boolean source = coreCount > 0 || layerSupply;
            if (!source) problems.add("message.dimensional_mana.magiccircle.problem.no_core");
            if (coreCount > 1) problems.add("message.dimensional_mana.magiccircle.problem.multi_core");
            if (!layerTrigger) problems.add("message.dimensional_mana.magiccircle.problem.no_trigger");
            if (!layerOutput) problems.add("message.dimensional_mana.magiccircle.problem.no_output");
            if (source && !allReachable(layer, coreCount == 0)) {
                problems.add("message.dimensional_mana.magiccircle.problem.unreachable");
            }
        }

        if (nodeCount == 0) {
            problems.clear();
            problems.add("message.dimensional_mana.magiccircle.problem.empty");
        }
        if (nodeCount > MagicCircleConfig.MAX_FUNCTIONAL_BLOCKS.get()) {
            problems.add("message.dimensional_mana.magiccircle.problem.too_many_blocks");
        }
        if (linkCount > MagicCircleConfig.MAX_CONNECTIONS.get()) {
            problems.add("message.dimensional_mana.magiccircle.problem.too_many_links");
        }

        // ---- 核心模式的汇总：增强 / 超频抬紊乱，超频抬全局损耗，延时拉长带时长效果 ----
        boolean anyEnhance = false;
        boolean anyOverclock = false;
        boolean anyDelay = false;
        String firstCoreElement = "";
        for (CoreContext core : layerCores) {
            if (core == null) continue;
            if (core.enhance()) anyEnhance = true;
            if (core.overclock()) anyOverclock = true;
            if (core.delay()) anyDelay = true;
            if (firstCoreElement.isEmpty() && !core.element().isEmpty()) firstCoreElement = core.element();
        }
        // 输出的增强幅度：超频 +50% > 增强 +25% > 节能 -20%（核心元素匹配的输出块才吃，结算时判）。
        double outputBonus = 0.0;

        // ---- 紊乱值：元素槽两两配对（每槽至多配一个对手，取最近连接距离）----
        int disorder = pairDisorder(layerList, slots, anyEnhance, anyOverclock);

        // ---- v2.2：逐层图分析（角色判定 + 合法性）；染料修饰紊乱、缺声明 E1 都在这里进账 ----
        boolean strict = MagicCircleConfig.DECLARE_MODE_ENABLED.get();
        Map<Long, MagicCircleGraph.Plan> planByNodeKey = new HashMap<>();
        Map<Long, Integer> taxByNodeKey = new HashMap<>();
        if (strict) {
            boolean anyDisorderDeclared = false;
            for (int layerIndex = 0; layerIndex < layerList.size(); layerIndex++) {
                MagicCircleNetwork.Layer layer = layerList.get(layerIndex);
                if (layer.nodes().isEmpty()) continue;
                boolean coreSugar = layerIndex < layerCores.size() && layerCores.get(layerIndex) != null
                        && layerCores.get(layerIndex).sugar();
                MagicCircleGraph.Analysis analysis = MagicCircleGraph.analyze(layer, true, coreSugar);
                disorder += dyeDisorderOf(analysis);
                if (analysis.disorderDeclaredSomewhere) anyDisorderDeclared = true;
                for (MagicCircleGraph.Plan plan : analysis.terminals) {
                    long key = ((long) layerIndex << 32) | (plan.terminal & 0xFFFFL);
                    planByNodeKey.put(key, plan);
                    taxByNodeKey.put(key, MagicCircleAffixes.complexityTax(
                            plan.prefixes.stream().map(MagicCircleGraph.Prefix::affix).toList()));
                }
                problems.addAll(analysis.issues);
            }
            // 规范 11 末行：总紊乱超阈值还想跑，网络里必须有人声明「紊乱策略」。
            if (!anyDisorderDeclared && MagicCircleDecl.disorderNeedsDeclaration(disorder) && nodeCount > 0) {
                problems.add(MagicCircleIssue.encode(
                        "message.dimensional_mana.magiccircle.declare.disorder_needed", disorder,
                        MagicCircleConfig.DISORDER_DECLARE_THRESHOLD.get()));
            }
        }
        disorder = Math.max(0, disorder
                - MagicCircleConfig.DISORDER_SUPPRESS_PER_STABILIZER.get() * stabilizers);

        // ---- 第二遍：逐节点套 5 乘区公式 ----
        double globalSpecial = MagicCircleConfig.EFFICIENCY_PER_STABILIZER.get() * stabilizers
                + MagicCircleConfig.EFFICIENCY_PER_SUPERCONDUCTOR.get() * superconductors;
        double efficiencyCap = MagicCircleConfig.EFFICIENCY_CAP.get();
        double disorderFactor = 1.0 + MagicCircleConfig.CONFLICT_PENALTY.get() * disorder;
        double drain = anyOverclock ? MagicCircleConfig.OVERCLOCK_MANA_DRAIN.get() : 0.0;
        boolean anyEco = false;
        for (CoreContext core : layerCores) {
            if (core != null && core.eco()) anyEco = true;
        }
        if (anyOverclock) outputBonus = 0.50;
        else if (anyEnhance) outputBonus = 0.25;
        else if (anyEco) outputBonus = -0.20;

        double effSum = 0.0;
        int effNodes = 0;
        double efficiencyFloor = MagicCircleConfig.MIN_TOTAL_EFFICIENCY.get();
        for (int layerIndex = 0; layerIndex < layerList.size(); layerIndex++) {
            MagicCircleNetwork.Layer layer = layerList.get(layerIndex);
            if (layer.nodes().isEmpty()) continue;
            CoreContext core = layerIndex < layerCores.size() ? layerCores.get(layerIndex) : null;
            double coreGlobal = (core == null ? 0.0 : core.arcaneBonus())
                    + (core != null && core.sugar() ? 0.05 : 0.0);
            ElementType coreElement = core == null ? null : core.elementOf();
            // 底座形态的触发节点（上游直接挂着门控条件）：里面的粉不参与判定，扣费豁免。
            Set<Integer> baseGateNodes = new java.util.HashSet<>();
            for (MagicCircleNetwork.Link link : layer.links()) {
                int up = link.from();
                int down = link.to();
                if (up < 0 || down < 0 || up >= layer.nodes().size() || down >= layer.nodes().size()) continue;
                MagicCircleNetwork.Node gate = layer.nodes().get(up);
                MagicCircleNetwork.Node trigger = layer.nodes().get(down);
                if (trigger.type() == MagicCircleBlockType.TRIGGER
                        && gate.type() == MagicCircleBlockType.CONDITION
                        && MagicCircleGates.isGateMode(gate.semantic(), gate.gate())) {
                    baseGateNodes.add(down);
                }
            }
            for (int nodeIndex = 0; nodeIndex < layer.nodes().size(); nodeIndex++) {
                MagicCircleNetwork.Node node = layer.nodes().get(nodeIndex);
                MagicCircleBlockType type = node.type();
                if (type == null) continue;
                double eff = coreGlobal + globalSpecial;
                // 核心元素偏好：同元素节点 +10%/+16%，异元素节点 -4%/-8%（奥术节点不吃罚）。
                if (coreElement != null && !node.element().isEmpty()
                        && !MagicCirclePowders.isArcane(node.element())) {
                    ElementType nodeElement = MagicCirclePowders.elementOf(node.element());
                    if (nodeElement == coreElement) eff += core.sugar() ? 0.16 : 0.10;
                    else eff -= core.sugar() ? 0.08 : 0.04;
                }
                // 延时（萤石粉）：全阵持续节点效率 +15%。
                if (core != null && core.delay() && type == MagicCircleBlockType.DURATION) eff += 0.15;
                // 经验（青金石粉）：全局使用青金石的节点效率 +30%。
                if (core != null && core.experience()
                        && "dimensional_mana:lapis_dust".equals(node.semantic())) eff += 0.30;
                // 节能（紫水晶粉）：使用该元素的节点消耗 -10%。
                if (core != null && core.eco() && coreElement != null
                        && coreElement == MagicCirclePowders.elementOf(node.element())) eff += 0.10;
                // v2.2：E_total 上限 75%、下限 -100%——负减免 = 加价，核心异元素罚项照常生效。
                eff = Math.max(efficiencyFloor, Math.min(efficiencyCap, eff));
                double power = MagicCirclePowders.powerEfficiency(node.power());
                double m = (1.0 - power) * (1.0 - eff) * disorderFactor * (1.0 + drain);
                // 手动框选出来的块是系统代劳的：贵 5%（文档 5.4）。
                if (node.handDrawn()) m *= MagicCircleConfig.HAND_DRAWN_COST_FACTOR.get();
                // v2.2 节点基础值：终端 = 输出基础值 + 词缀复杂度税；
                // 声明模式的条件节点 = (4 + 2×(组数−1)) × 档位系数。
                double base = type.startCost();
                double baseTrigger = type.triggerCost();
                if (strict) {
                    long key = ((long) layerIndex << 32) | (nodeIndex & 0xFFFFL);
                    if (type == MagicCircleBlockType.OUTPUT) {
                        Integer tax = taxByNodeKey.get(key);
                        if (tax != null) {
                            base += tax;
                            baseTrigger += tax;
                        }
                    } else if (type == MagicCircleBlockType.CONDITION) {
                        if (MagicCircleGates.isGateMode(node.semantic(), node.gate())) {
                            // 门控规范 v2.4 五：门控节点基础值 4，装了染料再乘档位系数 (1 + 0.03 × 档位)；
                            // 不参与维持（本来 upkeep 就是 0），只在触发瞬间判定一次。
                            int tier = MagicCircleDecl.dyeTier(node.dye());
                            base = 4.0 * (1.0 + 0.03 * Math.max(0, tier));
                        } else {
                            base = MagicCircleDecl.conditionBase(node.declare(),
                                    MagicCircleDecl.dyeTier(node.dye()));
                        }
                        baseTrigger = 0.0; // 「声明 / 门控模式启动计一次，触发不计额外费」（规范 1.2.1 五）
                    } else if (type == MagicCircleBlockType.TRIGGER && nodeIndex >= 0
                            && baseGateNodes.contains(nodeIndex)) {
                        // v3.0 三 3 扣费豁免：底座模式的触发节点里那些不再参与判定的粉，
                        // 不再额外加价——按基础值 8 收，功率槽效率照常生效。
                        base = type.startCost();
                        baseTrigger = type.triggerCost();
                    }
                }
                sumStart += base * m;
                sumUpkeep += type.upkeepCost() * m;
                sumTrigger += baseTrigger * m;
                effSum += (1.0 - power) * (1.0 - eff);
                effNodes++;
            }
        }

        int bases = Math.max(1, baseCount);
        int layers = Math.max(1, filledLayers);
        double scale = 1.0 + MagicCircleConfig.SCALE_PER_EXTRA_BASE.get() * (bases - 1);
        double layerFactor = 1.0 + MagicCircleConfig.LAYER_STEP.get() * (layers - 1);
        double overlapFactor = 1.0 + MagicCircleConfig.OVERLAP_STEP.get() * overlap;

        int startMana = (int) Math.ceil(sumStart * scale * layerFactor * overlapFactor);
        int upkeepMana = (int) Math.ceil(sumUpkeep * scale * layerFactor);
        int triggerMana = (int) Math.ceil(sumTrigger * scale * layerFactor * overlapFactor);
        double strength = (1.0 + MagicCircleConfig.STRENGTH_PER_BASE.get() * (bases - 1))
                * (1.0 + MagicCircleConfig.STRENGTH_PER_LAYER.get() * (layers - 1))
                * (1.0 + MagicCircleConfig.STRENGTH_PER_AMPLIFIER.get() * amplify);
        // 界面里那个「效率」：所有节点 (1-功率效率)×(1-总减免) 的平均值，越小说明整体越省。
        double efficiency = effNodes == 0 ? 1.0 : effSum / effNodes;

        return new Evaluation(startMana, upkeepMana, triggerMana, strength, complexity, disorder,
                nodeCount, layers, bases, efficiency, List.copyOf(problems),
                firstCoreElement, outputBonus, anyDelay ? 0.25 : 0.0);
    }

    // ---------------------------------------------------------------- 紊乱配对

    /**
     * 元素槽两两配对算总紊乱：
     * 一个槽位至多和一个槽位成对，多个候选按「连接距离最近」优先（同层沿连线 BFS 的跳数；
     * 连不通按画布曼哈顿距离垫底，跨层再叠一层距离惩罚），再按反应优先级排。
     */
    private static int pairDisorder(List<MagicCircleNetwork.Layer> layerList, List<ElementSlot> slots,
                                    boolean enhance, boolean overclock) {
        if (slots.size() < 2) return 0;
        List<PairCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                Reaction reaction = circleReaction(slots.get(i).itemId(), slots.get(j).itemId());
                if (reaction == null) continue;
                int distance = slotDistance(layerList, slots.get(i), slots.get(j));
                candidates.add(new PairCandidate(distance, Reaction.priorityOrder().indexOf(reaction),
                        i, j, reaction));
            }
        }
        if (candidates.isEmpty()) return 0;
        candidates.sort(Comparator.comparingInt(PairCandidate::distance)
                .thenComparingInt(PairCandidate::priority)
                .thenComparingInt(PairCandidate::a)
                .thenComparingInt(PairCandidate::b));
        boolean[] used = new boolean[slots.size()];
        int total = 0;
        for (PairCandidate pair : candidates) {
            if (used[pair.a()] || used[pair.b()]) continue;
            used[pair.a()] = true;
            used[pair.b()] = true;
            int value = pair.reaction().disorder();
            // 核心增强 / 超频模式对剧变、增幅反应的额外紊乱惩罚（v2.1 元素槽小节）。
            if (overclock) value += pair.reaction().isVolatile() ? 2 : 1;
            else if (enhance && pair.reaction().isVolatile()) value += 1;
            total += value;
        }
        return total;
    }

    /**
     * v2.2 规范 5：蓝 / 品红 / 黑这三系染料修饰每条额外 +1 紊乱；
     * 互冲染料对（白+黑、红+绿、橙+灰、淡蓝+红）再 +1。只数<b>前置输出</b>的染料——
     * 终端染料是纯数值加成，不吃这条。
     */
    private static int dyeDisorderOf(MagicCircleGraph.Analysis analysis) {
        int total = 0;
        for (MagicCircleGraph.Plan plan : analysis.terminals) {
            total += plan.disorderDyes;
            if (plan.dyeConflict) total += 1;
        }
        return total;
    }

    /** 法阵元素槽之间会不会起反应；奥术 + 任意元素按「充能」算。 */
    public static Reaction circleReaction(String itemA, String itemB) {
        boolean arcaneA = MagicCirclePowders.isArcane(itemA);
        boolean arcaneB = MagicCirclePowders.isArcane(itemB);
        if (arcaneA && arcaneB) return null;
        ElementType a = MagicCirclePowders.elementOf(itemA);
        ElementType b = MagicCirclePowders.elementOf(itemB);
        if (arcaneA || arcaneB) return a == null && b == null ? null : Reaction.CHARGE;
        return ElementReactionSystem.reactionFor(a, b);
    }

    /** 两个元素槽的连接距离：同层沿连线 BFS 跳数；跨层 / 连不通退化成画布距离。 */
    private static int slotDistance(List<MagicCircleNetwork.Layer> layerList, ElementSlot a, ElementSlot b) {
        int grid = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
        if (a.layer() != b.layer()) {
            return 100_000 + Math.abs(a.layer() - b.layer()) * 1_000 + grid;
        }
        MagicCircleNetwork.Layer layer = layerList.get(a.layer());
        int hops = linkHops(layer, a.node(), b.node());
        return hops < 0 ? 10_000 + grid : hops;
    }

    /** 同一层里两个功能块之间沿连线的最少跳数；走不通返回 -1。 */
    private static int linkHops(MagicCircleNetwork.Layer layer, int from, int to) {
        int size = layer.nodes().size();
        if (from == to) return 0;
        if (from < 0 || to < 0 || from >= size || to >= size) return -1;
        List<List<Integer>> neighbours = new ArrayList<>(size);
        for (int i = 0; i < size; i++) neighbours.add(new ArrayList<>());
        for (MagicCircleNetwork.Link link : layer.links()) {
            int x = link.from();
            int y = link.to();
            if (x < 0 || y < 0 || x >= size || y >= size) continue;
            neighbours.get(x).add(y);
            neighbours.get(y).add(x);
        }
        int[] dist = new int[size];
        Arrays.fill(dist, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        dist[from] = 0;
        queue.add(from);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (current == to) return dist[current];
            for (int next : neighbours.get(current)) {
                if (dist[next] < 0) {
                    dist[next] = dist[current] + 1;
                    queue.add(next);
                }
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 结构校验

    /** 从这一层的「源头」出发，能不能沿连线走到每一个功能块。 */
    private static boolean allReachable(MagicCircleNetwork.Layer layer, boolean fromSupply) {
        int size = layer.nodes().size();
        if (size == 0) return true;
        List<List<Integer>> neighbours = new ArrayList<>(size);
        for (int i = 0; i < size; i++) neighbours.add(new ArrayList<>());
        for (MagicCircleNetwork.Link link : layer.links()) {
            int a = link.from();
            int b = link.to();
            if (a < 0 || b < 0 || a >= size || b >= size) continue;
            neighbours.get(a).add(b);
            neighbours.get(b).add(a);
        }
        boolean[] seen = new boolean[size];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < size; i++) {
            MagicCircleNetwork.Node node = layer.nodes().get(i);
            if (node.type() != null && (fromSupply ? node.type() == MagicCircleBlockType.SUPPLY : node.type().isCore())) {
                seen[i] = true;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next : neighbours.get(current)) {
                if (!seen[next]) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        for (boolean reachable : seen) if (!reachable) return false;
        return true;
    }

    private MagicCircleCost() {}
}
