package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 魔法阵的消耗与强度计算，严格照设计文档第 7 节：
 *
 * <pre>
 * 单块成本 c = 基础值 × K元素 × K语义 × K功率 × K染料
 * 规模 S = 1 + 0.15 × (N - 1)       N = 基座数量
 * 层数 D = 1 + 0.30 × (L - 1)       L = 有内容的层数
 * 重叠 O = 1 + 0.20 × 重叠块数
 * 效率 E = 1 + 0.04 × 超导粉数 + 0.02 × 稳定块数，上限 1.60
 * 启动 = ceil(Σ启动 × S × D × O / E)   ← 每点紊乱再 +10%
 * 维持 = ceil(Σ维持 × S × D / E)    每秒
 * 触发 = ceil(Σ触发 × S × D × O / E)
 * 强度 P = (1 + 0.10×(N-1)) × (1 + 0.15×(L-1)) × (1 + 0.05×增幅块数)
 * </pre>
 *
 * <p>系数全部来自 {@link MagicCircleConfig}，服务器可以整体调整。</p>
 */
public final class MagicCircleCost {

    /** 一次计算的全部结果，界面与调试棒都直接读它。 */
    public record Evaluation(int startMana, int upkeepMana, int triggerMana, double strength,
                             int complexity, int disorder, int nodeCount, int filledLayers,
                             int baseCount, double efficiency, List<String> problems) {
        public boolean runnable() { return problems.isEmpty(); }
    }

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

        Set<String> elements = new LinkedHashSet<>();
        Set<String> problems = new LinkedHashSet<>();
        Map<Long, Integer> positions = new HashMap<>();

        for (MagicCircleNetwork.Layer layer : layerList) {
            if (layer.nodes().isEmpty()) continue;
            filledLayers++;
            linkCount += layer.links().size();

            // 这一层自己的统计：核心 / 触发 / 输出 / 可达性都只看本层。
            int layerCores = 0;
            boolean layerSupply = false;
            boolean layerTrigger = false;
            boolean layerOutput = false;
            int layerNodes = 0;
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                MagicCircleBlockType type = node.type();
                if (type == null) continue;
                nodeCount++;
                layerNodes++;
                double k = MagicCirclePowders.coefficient(node.element())
                        * MagicCirclePowders.coefficient(node.semantic())
                        * MagicCirclePowders.coefficient(node.power())
                        * MagicCirclePowders.coefficient(node.dye());
                // 手动框选出来的块是系统代劳的：贵 5%、复杂度 +1（文档 5.4）。
                if (node.handDrawn()) k *= MagicCircleConfig.HAND_DRAWN_COST_FACTOR.get();
                sumStart += type.startCost() * k;
                sumUpkeep += type.upkeepCost() * k;
                sumTrigger += type.triggerCost() * k;
                complexity += type.complexity()
                        + (node.handDrawn() ? MagicCircleConfig.HAND_DRAWN_COMPLEXITY.get() : 0);
                if (type == MagicCircleBlockType.AMPLIFY) amplify++;
                if (type == MagicCircleBlockType.STABILIZE) stabilizers++;
                if (isSuperconductor(node.power())) superconductors++;
                if (type.isCore()) layerCores++;
                if (type == MagicCircleBlockType.SUPPLY) layerSupply = true;
                if (type.isTrigger()) layerTrigger = true;
                if (type.isOutput()) layerOutput = true;
                if (!node.element().isEmpty()) elements.add(node.element());
                long key = (long) node.x() * 4096L + node.y();
                if (positions.merge(key, 1, Integer::sum) > 1) overlap++;
            }
            if (layerNodes == 0) continue;
            // 源头可以是核心，也可以是一块「供能」：供能 = 这一层的输入，
            // 没有核心、但有一块供能当头，照样算这一层有源头，不再报「缺少核心块」。
            boolean layerSource = layerCores > 0 || layerSupply;
            if (!layerSource) problems.add("message.dimensional_mana.magiccircle.problem.no_core");
            if (layerCores > 1) problems.add("message.dimensional_mana.magiccircle.problem.multi_core");
            if (!layerTrigger) problems.add("message.dimensional_mana.magiccircle.problem.no_trigger");
            if (!layerOutput) problems.add("message.dimensional_mana.magiccircle.problem.no_output");
            if (layerSource && !allReachable(layer, layerCores == 0)) {
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

        int disorder = stabilizers == 0 ? conflicts(elements) : 0;

        int bases = Math.max(1, baseCount);
        int layers = Math.max(1, filledLayers);
        double scale = 1.0 + MagicCircleConfig.SCALE_PER_EXTRA_BASE.get() * (bases - 1);
        double layerFactor = 1.0 + MagicCircleConfig.LAYER_STEP.get() * (layers - 1);
        double overlapFactor = 1.0 + MagicCircleConfig.OVERLAP_STEP.get() * overlap;
        double efficiency = Math.min(MagicCircleConfig.EFFICIENCY_CAP.get(),
                1.0 + MagicCircleConfig.EFFICIENCY_PER_SUPERCONDUCTOR.get() * superconductors
                        + MagicCircleConfig.EFFICIENCY_PER_STABILIZER.get() * stabilizers);
        double disorderFactor = 1.0 + MagicCircleConfig.CONFLICT_PENALTY.get() * disorder;

        int startMana = (int) Math.ceil(sumStart * scale * layerFactor * overlapFactor / efficiency * disorderFactor);
        int upkeepMana = (int) Math.ceil(sumUpkeep * scale * layerFactor / efficiency);
        int triggerMana = (int) Math.ceil(sumTrigger * scale * layerFactor * overlapFactor / efficiency);
        double strength = (1.0 + MagicCircleConfig.STRENGTH_PER_BASE.get() * (bases - 1))
                * (1.0 + MagicCircleConfig.STRENGTH_PER_LAYER.get() * (layers - 1))
                * (1.0 + MagicCircleConfig.STRENGTH_PER_AMPLIFIER.get() * amplify);

        return new Evaluation(startMana, upkeepMana, triggerMana, strength, complexity, disorder,
                nodeCount, layers, bases, efficiency, List.copyOf(problems));
    }

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

    private static boolean isSuperconductor(String powerId) {
        return "dimensional_mana:superconducting_crystal_dust".equals(powerId);
    }

    /** 元素冲突：火+冰、光+毒、奥术+任意其他元素。 */
    private static int conflicts(Set<String> elements) {
        int count = 0;
        MagicCirclePowders.Powder fire = MagicCirclePowders.ofElement(ElementType.FIRE);
        MagicCirclePowders.Powder ice = MagicCirclePowders.ofElement(ElementType.ICE);
        MagicCirclePowders.Powder light = MagicCirclePowders.ofElement(ElementType.LIGHT);
        MagicCirclePowders.Powder poison = MagicCirclePowders.ofElement(ElementType.POISON);
        MagicCirclePowders.Powder arcane = MagicCirclePowders.get("dimensional_mana:mana_element_crystal_dust");
        if (fire != null && ice != null && elements.contains(fire.itemId()) && elements.contains(ice.itemId())) count++;
        if (light != null && poison != null && elements.contains(light.itemId()) && elements.contains(poison.itemId())) count++;
        if (arcane != null && elements.contains(arcane.itemId()) && elements.size() > 1) count++;
        return count;
    }

    private MagicCircleCost() {}
}
