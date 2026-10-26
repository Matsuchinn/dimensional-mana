package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 连线的「方向 / 顺序」判定：谁先触发、哪条线走不通。
 *
 * <p>连线本身带方向（{@code from → to} 就是「上游 → 下游」）。顺序按「层」算，不按走过的一个个
 * 名次算：从<b>入口</b>出发，入口是第 1 位；直接接在入口后面的都是第 2 位，接在第 2 位后面的都是
 * 第 3 位……<b>同一层的块拿着同一个序号、同时触发</b>，不会出现「并排的两个块一个第 2、一个第 3」
 * 这种看着别扭的编号。一块有好几个上游时等最深的那个——取 {@code max(上游层) + 1}。</p>
 *
 * <p><b>入口默认就是第 1 位触发</b>：核心自己没被别的块接进来时，核心就是入口（老画法照旧）；
 * 有块接进核心（比如 供能 → 核心）时，<b>那块才是入口、拿第 1 位</b>，核心顺延成第 2 位。
 * 一整层没有核心时，所有没有上游的块都是入口，各算第 1 位。</p>
 *
 * <p>层号用拓扑序一遍算完；同层按功能块下标排，所以同一张阵每次算出来的序号都一样，不会跳来跳去。</p>
 *
 * <p>下面几种线算「不通」：</p>
 *
 * <ul>
 *   <li><b>输出往外接</b>：输出是链条的终点，{@code 输出 → 触发} 这种方向流不过去；</li>
 *   <li><b>源头没接上</b>：这条线的上游本身没从入口走到，电根本没到这里；</li>
 *   <li><b>成环</b>：顺着这条线能绕回它自己的上游，触发顺序会死循环。</li>
 * </ul>
 *
 * <p>接进核心本身<b>不算</b>不通了（{@link Issue#INTO_CORE} 现在只在旧存档的提示里留着名字）：
 * 核心是汇总口，「供能 → 核心」是正经画法。真正会绕成环的「触发 → 核心」会被当成成环拦下。</p>
 *
 * <p>这里只做「显示用」的判定，不改存档、也不拦玩家连线：画布上标红是提醒，
 * 想让线通就把它拆了按正确方向重连（先点上游、Shift 点下游、按 L）。</p>
 */
public final class MagicCircleFlow {

    /** 一条连线走不走得通，以及走不通的原因。 */
    public enum Issue {
        OK(""),
        /** 起终点同一块 / 下标越界：脏数据，不画也不提示。 */
        BROKEN(""),
        FROM_OUTPUT("gui.dimensional_mana.magiccircle.flow.issue.from_output"),
        /** 旧版把「接进核心」判成回路。现在接进核心是正经画法（核心是汇总口），保留枚举只为旧文案不报错。 */
        INTO_CORE("gui.dimensional_mana.magiccircle.flow.issue.into_core"),
        FROM_UNREACHABLE("gui.dimensional_mana.magiccircle.flow.issue.unreachable"),
        CYCLE("gui.dimensional_mana.magiccircle.flow.issue.cycle");

        private final String key;

        Issue(String key) {
            this.key = key;
        }

        /** 空串表示「没有可说的原因」，调用方据此决定要不要显示提示。 */
        public String translationKey() { return key; }

        public boolean ok() { return this == OK || this == BROKEN; }

        public boolean broken() { return this == BROKEN; }
    }

    /**
     * 一次算完的结果。
     *
     * @param order 每块功能块是第几层（第几位触发）；同一层的块号相同、同时触发；0 表示「没接进主链」
     * @param issues 每条连线（下标跟界面拿到的 links 对齐）通不通
     * @param steps  一共有几块接到了主链上
     */
    public record Result(int[] order, Issue[] issues, int steps) {
        public int order(int index) {
            return index >= 0 && index < order.length ? order[index] : 0;
        }

        public Issue issue(int linkIndex) {
            return linkIndex >= 0 && linkIndex < issues.length ? issues[linkIndex] : Issue.BROKEN;
        }

        public boolean flows(int linkIndex) { return issue(linkIndex).ok(); }

        public static Result empty() {
            return new Result(new int[0], new Issue[0], 0);
        }
    }

    private MagicCircleFlow() {}

    public static Result compute(List<MagicCircleSyncPayload.NodeView> nodes,
                                 List<MagicCircleSyncPayload.LinkView> links) {
        int size = nodes == null ? 0 : nodes.size();
        int linkCount = links == null ? 0 : links.size();
        if (size == 0) return Result.empty();

        // out 是全部连线（找环用）；flow 只放「走得通」的连线（排顺序用）：
        // 输出往外接的、接回核心的都不算，这样红色线段下游的块也不会得到一个假的序号。
        List<List<Integer>> out = new ArrayList<>(size);
        List<List<Integer>> flow = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(new ArrayList<>());
            flow.add(new ArrayList<>());
        }
        Issue[] issues = new Issue[linkCount];
        for (int i = 0; i < linkCount; i++) {
            MagicCircleSyncPayload.LinkView link = links.get(i);
            int from = link.from();
            int to = link.to();
            if (from < 0 || to < 0 || from >= size || to >= size || from == to) {
                issues[i] = Issue.BROKEN;
                continue;
            }
            issues[i] = Issue.OK;
            out.get(from).add(to);
            // 接进核心的线也算能走：核心在这里是「汇总口」，不是终点。
            // 供能 → 核心 → 触发 → 输出 这种画法因此合法，入口（供能）是第 1 位触发。
            if (!isOutput(nodes.get(from))) flow.get(from).add(to);
        }
        // 同层按功能块下标走，序号才不会因为连线创建的先后而乱跳。
        for (List<Integer> neighbours : flow) Collections.sort(neighbours);

        // 源头 = 这一层的「输入」：没有上游、并且能走到核心的那些块。
        //   · 核心自己没被接进来 → 核心就是入口，第 1 位（老画法不受影响）；
        //   · 有块接进核心（供能之类）→ 那块才是入口，第 1 位，核心顺延成第 2 位；
        //   · 整层没有核心 → 所有没有上游的块都是入口，各算第 1 位。
        // 和核心完全断开的一串块不算入口（它们留在第 0 位、画成红色 !），这样「不通」还看得见。
        boolean[] hasUpstream = new boolean[size];
        for (List<Integer> neighbours : flow) {
            for (int next : neighbours) hasUpstream[next] = true;
        }
        boolean anyCore = false;
        for (int i = 0; i < size; i++) {
            if (isCore(nodes.get(i))) anyCore = true;
        }
        boolean[] feedsCore = anyCore ? upstreamOfCores(nodes, flow, size) : null;
        List<Integer> roots = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (hasUpstream[i]) continue;
            if (!anyCore || isCore(nodes.get(i)) || feedsCore[i]) roots.add(i);
        }
        if (roots.isEmpty()) {
            // 一整圈环、或者一条入口都没有：退回「核心当源头」，再不行就第 1 块。
            for (int i = 0; i < size; i++) {
                if (isCore(nodes.get(i))) roots.add(i);
            }
        }
        if (roots.isEmpty()) roots.add(0);

        // 层号（拓扑序一遍算完）：核心 / 源头是第 1 层；一个块的下游 = 「它自己的层 + 1」，
        // 有好几个上游时取最深的那个。并排接在同一块后面的几块因此拿到同一个层号：同时触发。
        int[] order = new int[size];
        for (int root : roots) order[root] = 1;
        int[] indegree = new int[size];
        for (List<Integer> neighbours : flow) {
            for (int next : neighbours) indegree[next]++;
        }
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < size; i++) if (indegree[i] == 0) queue.add(i);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (order[current] > 0) {
                int nextLayer = order[current] + 1;
                for (int next : flow.get(current)) {
                    if (nextLayer > order[next]) order[next] = nextLayer;
                }
            }
            for (int next : flow.get(current)) {
                if (--indegree[next] == 0) queue.add(next);
            }
        }
        // 一共有几块接到了主链上：只用来统计，不参与编号。
        int steps = 0;
        for (int value : order) if (value > 0) steps++;

        for (int i = 0; i < linkCount; i++) {
            MagicCircleSyncPayload.LinkView link = links.get(i);
            int from = link.from();
            int to = link.to();
            if (issues[i] != Issue.OK) continue;
            if (isOutput(nodes.get(from))) {
                issues[i] = Issue.FROM_OUTPUT;
                continue;
            }
            // 先看环：环上的块算不出层号（order 是 0），但这条线真正的问题是绕回来了，
            // 提示得说「成环」而不是「上游没接上」，不然玩家按提示去补上游永远补不完。
            if (canReach(to, from, out, size)) {
                issues[i] = Issue.CYCLE;
                continue;
            }
            if (order[from] == 0) {
                issues[i] = Issue.FROM_UNREACHABLE;
            }
        }
        return new Result(order, issues, steps);
    }

    /**
     * 哪些块顺着连线能走到某个核心（也就是「核心的输入侧」）。核心自己也算。
     *
     * <p>用反向图做一遍广度优先：从核心往回走，能走到的就是给核心供能 / 触发的那一串。
     * 编号时它们排在核心前面，入口那块拿第 1 位。</p>
     */
    private static boolean[] upstreamOfCores(List<MagicCircleSyncPayload.NodeView> nodes,
                                             List<List<Integer>> flow, int size) {
        List<List<Integer>> reverse = new ArrayList<>(size);
        for (int i = 0; i < size; i++) reverse.add(new ArrayList<>());
        for (int i = 0; i < size; i++) {
            for (int next : flow.get(i)) reverse.get(next).add(i);
        }
        boolean[] seen = new boolean[size];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < size; i++) {
            if (isCore(nodes.get(i))) {
                seen[i] = true;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int previous : reverse.get(current)) {
                if (seen[previous]) continue;
                seen[previous] = true;
                queue.add(previous);
            }
        }
        return seen;
    }

    /** 顺着方向从 {@code start} 能不能走到 {@code target}（用来抓成环的线）。 */
    private static boolean canReach(int start, int target, List<List<Integer>> out, int size) {
        if (start == target) return true;
        boolean[] seen = new boolean[size];
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        seen[start] = true;
        int guard = 0;
        while (!stack.isEmpty() && guard++ <= size * 4) {
            int current = stack.pop();
            for (int next : out.get(current)) {
                if (next == target) return true;
                if (seen[next]) continue;
                seen[next] = true;
                stack.push(next);
            }
        }
        return false;
    }

    private static boolean isCore(MagicCircleSyncPayload.NodeView node) {
        MagicCircleBlockType type = MagicCircleBlockType.byId(node.type());
        return type != null && type.isCore();
    }

    private static boolean isOutput(MagicCircleSyncPayload.NodeView node) {
        MagicCircleBlockType type = MagicCircleBlockType.byId(node.type());
        return type != null && type.isOutput();
    }
}
