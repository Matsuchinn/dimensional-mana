package cn.blockforge.generated.minecraft1211n422a;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v2.2 输出节点重构的<b>图分析器</b>：把一层的连线图切成「终端 / 前置 / 声明条件 / 门控条件」，
 * 并按《输出节点设计总则》1.2 / 1.2.1 / 1.3 / 6.x / 11 / 15 节做合法性校验。
 *
 * <p>方向判定不依赖连线的书写顺序：从这一层的源头（核心，没有核心就退到供能）出发做无向 BFS
 * 拿「连接距离」，两个输出块之间有连线时，<b>离源头近的当前置、远的当终端</b>；前置可以经过
 * 0~2 个声明条件节点再到达终端，条件节点只做守卫、不构成修饰关系（规范 1.2.1）。</p>
 *
 * <p>分析结果同时被三处使用：{@link MagicCircleCost}（复杂度税 / 节点基础值 / E1 报错）、
 * {@link MagicCircleRites}（终端按协议结算）与绘制界面（终端 / 前置角标、缺声明红标）。
 * 纯数据、无服务端依赖，客户端可以直接跑。</p>
 */
public final class MagicCircleGraph {

    /** 功能块在这一层里的角色。{@code TRIGGER_BASE}＝底座模式触发节点（判定交给上游门控条件）。 */
    public enum Role { NORMAL, TERMINAL, PREFIX, DECL, GATE, TRIGGER_BASE }

    /** 一条挂在终端上的前置词缀。 */
    public record Prefix(int node, MagicCircleAffixes.Affix affix, String dyeTag) {}

    /** 一个终端的完整执行计划。 */
    public static final class Plan {
        public final int terminal;
        public MagicCircleAffixes.Protocol protocol;
        public final List<Prefix> prefixes = new ArrayList<>();
        /** 路径上所有声明条件节点（含直连终端的）。 */
        public final List<Integer> condNodes = new ArrayList<>();
        /**
         * 门控规范 v2.4 的守卫分支表：外层每一条是一个「并联分支」（满足其一即放行），
         * 分支内部是沿连线串起来的「串联条件链」（必须全满足）。链上节点按离源头由近到远排，
         * 结算时逐条链短路求值（一个 false 立刻换下一条，绝不查后面的条件）。空表 = 没有门控守卫。
         */
        public final List<List<Integer>> gateBranches = new ArrayList<>();
        /** 合并后的边界组参数（同组重复声明取最严）。 */
        public final Map<MagicCircleDecl.Group, MagicCircleDecl.Entry> declarations =
                new EnumMap<>(MagicCircleDecl.Group.class);
        /** 缺的必需边界组（规范 11）。 */
        public final Set<MagicCircleDecl.Group> missing = EnumSet.noneOf(MagicCircleDecl.Group.class);
        /** 染料互冲（规范 5），运行时有概率哑火。 */
        public boolean dyeConflict;
        /** 被染料修饰加了紊乱的条数（蓝 / 品红 / 黑）。 */
        public int disorderDyes;

        Plan(int terminal) { this.terminal = terminal; }

        public MagicCircleDecl.Entry declaration(MagicCircleDecl.Group group) {
            return declarations.get(group);
        }

        /** 该组的参数：没声明时给推荐值（非严格模式与预览兜底用）。 */
        public MagicCircleDecl.Entry paramOrRecommended(MagicCircleDecl.Group group) {
            MagicCircleDecl.Entry entry = declarations.get(group);
            return entry != null ? entry : MagicCircleDecl.recommended(group, -1,
                    MagicCircleDecl.Precision.FINE);
        }

        public boolean declared(MagicCircleDecl.Group group) {
            return declarations.containsKey(group);
        }

        /** 「终端#3（糖+木·友好召唤）」这种描述串（规范 17）。 */
        public String describe(MagicCircleNetwork.Layer layer) {
            MagicCircleNetwork.Node node = layer.nodes().get(terminal);
            String sem = MagicCirclePowders.shortName(node.semantic());
            String el = MagicCirclePowders.shortName(node.element());
            String main = "";
            if (protocol != MagicCircleAffixes.Protocol.NONE) {
                main = switch (protocol) {
                    case LIFE -> el.equals("木") ? "友好召唤" : "治疗";
                    case DAMAGE -> "伤害";
                    case SIGNAL -> "红石信号";
                    case PURIFY -> "净化";
                    case LIGHT -> "照明/显形";
                    case SCAN -> el.equals("金") ? "精准采集挖掘" : "扫描鉴定";
                    case CHARGE -> el.equals("金") ? "探测金属" : "充能";
                    case NONE -> "";
                };
            }
            if (!prefixes.isEmpty()) {
                for (Prefix prefix : prefixes) {
                    MagicCircleDecl.Group need = prefix.affix() == null ? null
                            : MagicCircleDecl.requiredGroupForTag(prefix.affix().tag());
                    if (need == MagicCircleDecl.Group.SUMMON) main = "召唤";
                }
            }
            return sem + "+" + el + (main.isEmpty() ? "" : "·" + main);
        }
    }

    /** 一次整层分析的结果。 */
    public static final class Analysis {
        public final List<Plan> terminals = new ArrayList<>();
        public final Map<Integer, Role> roles = new HashMap<>();
        /** 编码后的问题串（{@link MagicCircleIssue}），画布红标与页脚共用。 */
        public final List<String> issues = new ArrayList<>();
        /** 出问题的节点下标（画布角标）。 */
        public final Set<Integer> flagged = new LinkedHashSet<>();
        /** 这一层里声明过「紊乱策略」的前置词缀节点下标集合（网络级检查用）。 */
        public boolean disorderDeclaredSomewhere;
        /**
         * 底座形态的触发节点下标 → 接管它判定的上游门控条件节点下标
         * （v2.6 一 2）。界面据此画红色禁用遮罩、结算据此扣费豁免与轮询接管。
         */
        public final Map<Integer, Integer> baseGates = new LinkedHashMap<>();

        Role role(int index) { return roles.getOrDefault(index, Role.NORMAL); }

        /** 公开的查角色入口（界面角标与遮罩用）。 */
        public Role roleOf(int index) { return role(index); }

        /** 这块触发是不是底座形态（自身判定停用，只当逻辑起点）。 */
        public boolean isTriggerBase(int index) { return role(index) == Role.TRIGGER_BASE; }

        public boolean hasTerminalIssues() { return !issues.isEmpty(); }
    }

    /**
     * 分析一层。
     *
     * @param strict      v2.2 严格模式（缺声明 = E1）；false = 只分角色不报错
     * @param coreSugar   本层核心语义是不是糖（词缀容量 +1）
     */
    public static Analysis analyze(MagicCircleNetwork.Layer layer, boolean strict, boolean coreSugar) {
        Analysis analysis = new Analysis();
        List<MagicCircleNetwork.Node> nodes = layer.nodes();
        int size = nodes.size();
        if (size == 0) return analysis;

        // ---- 无向 BFS：每块到源头的连接距离 ----
        List<List<Integer>> neighbours = new ArrayList<>(size);
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < size; i++) neighbours.add(new ArrayList<>());
        for (MagicCircleNetwork.Link link : layer.links()) {
            int a = link.from();
            int b = link.to();
            if (a < 0 || b < 0 || a >= size || b >= size || a == b) continue;
            if (!neighbours.get(a).contains(b)) neighbours.get(a).add(b);
            if (!neighbours.get(b).contains(a)) neighbours.get(b).add(a);
            edges.add(new int[]{a, b});
        }
        int[] dist = new int[size];
        java.util.Arrays.fill(dist, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < size; i++) {
            MagicCircleBlockType type = nodes.get(i).type();
            if (type != null && (type.isCore() || type == MagicCircleBlockType.SUPPLY)) {
                dist[i] = 0;
                queue.add(i);
            }
        }
        if (queue.isEmpty()) {
            // 没源头也照跑：所有没有上游的块当并列入口，免得界面上一片空白。
            for (int i = 0; i < size; i++) {
                dist[i] = 0;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next : neighbours.get(current)) {
                if (dist[next] < 0) {
                    dist[next] = dist[current] + 1;
                    queue.add(next);
                }
            }
        }

        // ---- 条件节点：门控 / 声明三态（门控规范 v2.4：显式 gate 串优先，空串退回 r208 旧推断）----
        boolean[] declCond = new boolean[size];
        boolean[] gateCond = new boolean[size];
        for (int i = 0; i < size; i++) {
            MagicCircleNetwork.Node node = nodes.get(i);
            if (node.type() != MagicCircleBlockType.CONDITION) continue;
            if (MagicCircleGates.isGateMode(node.semantic(), node.gate())) {
                gateCond[i] = true;
                analysis.roles.put(i, Role.GATE);
            } else {
                declCond[i] = true;
                analysis.roles.put(i, Role.DECL);
                if (MagicCircleDecl.find(node.declare(), MagicCircleDecl.Group.DISORDER) != null) {
                    analysis.disorderDeclaredSomewhere = true;
                }
            }
        }

        // ---- 触发节点双形态（v2.6 / v3.0 一）：按连线的书写方向（from＝上游 → to＝下游）判上下游 ----
        for (int[] edge : edges) {
            int up = edge[0];
            int down = edge[1];
            MagicCircleBlockType upType = nodes.get(up).type();
            MagicCircleBlockType downType = nodes.get(down).type();
            if (downType == MagicCircleBlockType.TRIGGER) {
                if (gateCond[up]) {
                    // [门控条件] -> [触发节点]：底座形态。触发节点自身判定全部停用，
                    // 真正的放行条件 100% 交给上游这块门控（轮询它，绿了就开火）。
                    analysis.roles.put(down, Role.TRIGGER_BASE);
                    analysis.baseGates.put(down, up);
                } else if (declCond[up]) {
                    // [声明节点] -> [触发节点]：声明节点不能当逻辑起点，构建期直接报错。
                    issue(analysis, up, "message.dimensional_mana.magiccircle.declare.upstream", up + 1);
                    analysis.flagged.add(down);
                }
            }
        }

        // ---- 下游可达输出（穿过 0~2 个声明条件）----
        // 对每个输出块，从它出发沿「距离严格增大」的边走，允许中途只穿声明条件节点，
        // 收集能走到的输出块 —— 非空就说明它是前置。
        Map<Integer, List<Integer>> downstreamOutputs = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            if (nodes.get(i).type() != MagicCircleBlockType.OUTPUT) continue;
            downstreamOutputs.put(i, reachOutputs(i, neighbours, dist, nodes, declCond, size));
        }

        Set<Integer> isPrefix = new LinkedHashSet<>();
        Set<Integer> servedTerminals = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<Integer>> entry : downstreamOutputs.entrySet()) {
            if (!entry.getValue().isEmpty()) isPrefix.add(entry.getKey());
            servedTerminals.addAll(entry.getValue());
        }
        // 终端 = 输出块 − 前置；一个既是前置又被别的前置接到的块 = 链式套娃，直接报错。
        for (int prefix : isPrefix) {
            if (servedTerminals.contains(prefix)) {
                issue(analysis, prefix, "message.dimensional_mana.magiccircle.declare.chain", prefix + 1);
            }
        }

        // ---- 前置 → 终端 的归属（含分叉检查）----
        Map<Integer, List<Integer>> terminalsOf = new LinkedHashMap<>();
        for (int prefix : isPrefix) {
            List<Integer> targets = new ArrayList<>();
            for (int t : downstreamOutputs.get(prefix)) {
                if (!isPrefix.contains(t)) targets.add(t);
            }
            if (targets.isEmpty()) {
                // 只连到别的「前置」：链式套娃或死路。
                issue(analysis, prefix, "message.dimensional_mana.magiccircle.declare.fork", prefix + 1);
                continue;
            }
            if (targets.size() > 1) {
                issue(analysis, prefix, "message.dimensional_mana.magiccircle.declare.fork", prefix + 1);
            }
            terminalsOf.put(prefix, targets);
            analysis.roles.put(prefix, Role.PREFIX);
        }

        // ---- 声明条件服务几个终端 / 是不是死路 ----
        Map<Integer, Set<Integer>> condTerminals = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            if (!declCond[i]) continue;
            Set<Integer> served = new LinkedHashSet<>();
            for (int next : neighbours.get(i)) {
                if (dist[next] <= dist[i]) continue;
                if (nodes.get(next).type() == MagicCircleBlockType.OUTPUT && !isPrefix.contains(next)) {
                    served.add(next);
                }
            }
            // 条件 → 条件 → 终端 也算（路径上限 2 个条件在 reachOutputs 里管）。
            if (served.isEmpty()) {
                issue(analysis, i, "message.dimensional_mana.magiccircle.declare.cond_dead", i + 1);
            } else if (served.size() > 1) {
                issue(analysis, i, "message.dimensional_mana.magiccircle.declare.cond_multi", i + 1);
            }
            condTerminals.put(i, served);
        }

        // ---- 组终端计划 ----
        Map<Integer, Plan> plans = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : terminalsOf.entrySet()) {
            int prefix = entry.getKey();
            for (int terminal : entry.getValue()) {
                Plan plan = plans.computeIfAbsent(terminal, Plan::new);
                MagicCircleAffixes.Affix affix = MagicCircleAffixes.compile(nodes.get(prefix));
                if (affix == null) {
                    issue(analysis, prefix, "message.dimensional_mana.magiccircle.declare.bad_affix",
                            prefix + 1);
                    continue;
                }
                int cap = MagicCircleAffixes.prefixComplexityCap(nodes.get(prefix).power());
                if (affix.complexity() > cap) {
                    issue(analysis, prefix, "message.dimensional_mana.magiccircle.declare.complex",
                            prefix + 1, MagicCircleAffixes.affixName(affix.id()), cap);
                    continue;
                }
                String dyeTag = MagicCircleAffixes.dyeModifierTag(nodes.get(prefix).dye());
                plan.prefixes.add(new Prefix(prefix, affix, dyeTag));
                if (MagicCircleAffixes.disorderDye(dyeTag)) plan.disorderDyes++;
            }
        }
        // 直连终端的声明条件（含「前置→条件→终端」路径上的那一段）并入终端计划。
        for (Map.Entry<Integer, Set<Integer>> entry : condTerminals.entrySet()) {
            for (int terminal : entry.getValue()) {
                if (!isAnyTerminal(terminal, nodes, isPrefix)) continue;
                Plan plan = plans.computeIfAbsent(terminal, Plan::new);
                if (!plan.condNodes.contains(entry.getKey())) plan.condNodes.add(entry.getKey());
            }
        }

        // 剩下的输出块（不是前置也没被当前置收留）都是终端。
        for (int i = 0; i < size; i++) {
            if (nodes.get(i).type() == MagicCircleBlockType.OUTPUT && !isPrefix.contains(i)) {
                plans.computeIfAbsent(i, Plan::new);
            }
        }

        for (Plan plan : plans.values()) {
            analysis.roles.put(plan.terminal, Role.TERMINAL);
            // 汇入声明组的参数（同组重复取最严：主参数更小的一侧）。
            for (int cond : plan.condNodes) {
                MagicCircleNetwork.Node node = nodes.get(cond);
                int tier = MagicCircleDecl.dyeTier(node.dye());
                for (MagicCircleDecl.Entry entry : MagicCircleDecl.parse(node.declare())) {
                    MagicCircleDecl.Entry kept = plan.declarations.get(entry.group());
                    if (kept == null) {
                        plan.declarations.put(entry.group(), entry);
                    } else {
                        String hint = strict ? "" : "";
                        if (entry.group().hasTier()) {
                            // 「同类型边界组重复声明：取最严格参数；冲突且无法取严 → E1」（规范 15.7）。
                            int strictest = Math.min(kept.primary(), entry.primary());
                            int lenient = Math.max(kept.primary(), entry.primary());
                            if (!sameEnum(kept, entry)) {
                                issue(analysis, plan.terminal,
                                        "message.dimensional_mana.magiccircle.declare.dupe_conflict",
                                        plan.terminal + 1, groupName(entry.group()));
                            }
                            plan.declarations.put(entry.group(),
                                    new MagicCircleDecl.Entry(entry.group(), strictest,
                                            Math.min(kept.secondary(), entry.secondary()),
                                            kept.enums(), kept.flags() | entry.flags()));
                            if (lenient < 0) hint = "";
                        } else {
                            plan.declarations.put(entry.group(), kept);
                        }
                    }
                }
            }
            // 染料互冲：两条前置染料互撞（规范 5）。
            outer:
            for (int a = 0; a < plan.prefixes.size(); a++) {
                for (int b = a + 1; b < plan.prefixes.size(); b++) {
                    if (MagicCircleAffixes.dyeConflict(nodes.get(plan.prefixes.get(a).node()).dye(),
                            nodes.get(plan.prefixes.get(b).node()).dye())) {
                        plan.dyeConflict = true;
                        break outer;
                    }
                }
            }
            resolveProtocol(layer, plan, analysis, strict);
            if (strict) checkRequiredGroups(layer, plan, analysis);
        }

        // ---- 门控守卫：给每个终端建「并联分支 × 串联链」（v2.4 一 3/4、五 5；v2.6 三）----
        List<List<Integer>> forward = new ArrayList<>(size);
        for (int i = 0; i < size; i++) forward.add(new ArrayList<>());
        for (int[] edge : edges) {
            if (!forward.get(edge[0]).contains(edge[1])) forward.get(edge[0]).add(edge[1]);
        }
        for (Plan plan : plans.values()) {
            buildGateBranches(plan, gateCond, forward, dist, size);
        }

        analysis.terminals.addAll(plans.values());
        analysis.terminals.sort((a, b) -> Integer.compare(a.terminal, b.terminal));
        return analysis;
    }

    /**
     * 给一个终端收集上游所有门控条件，切成「并联分支」（外层 OR：满足其一即放行）×
     * 「串联链」（内层 AND：同一条线上前后串起来，必须全满足）。
     *
     * <p>链内按离源头的距离升序排——结算时从最靠近源头那块开始短路求值，一次 {@code false}
     * 立刻结束整条链（绝不再查后面的条件）。两条门控若互相可达就是串联同一条；不可达说明在
     * 不同路径上，各起一条并联分支。</p>
     */
    private static void buildGateBranches(Plan plan, boolean[] gateCond, List<List<Integer>> forward,
                                          int[] dist, int size) {
        List<Integer> gates = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (!gateCond[i]) continue;
            if (canReach(i, plan.terminal, forward, size)) gates.add(i);
        }
        gates.sort((a, b) -> Integer.compare(distRank(a, dist), distRank(b, dist)));
        for (int g : gates) {
            List<Integer> target = null;
            for (List<Integer> branch : plan.gateBranches) {
                int tail = branch.get(branch.size() - 1);
                if (tail != g && canReach(tail, g, forward, size)) {
                    if (target == null || branch.size() > target.size()) target = branch;
                }
            }
            if (target != null) target.add(g);
            else {
                List<Integer> branch = new ArrayList<>();
                branch.add(g);
                plan.gateBranches.add(branch);
            }
        }
    }

    private static int distRank(int node, int[] dist) {
        int value = node >= 0 && node < dist.length ? dist[node] : -1;
        return value < 0 ? 1_000_000 + node : value * 1000 + node;
    }

    /** 沿连线方向从 {@code start} 能不能走到 {@code target}。 */
    private static boolean canReach(int start, int target, List<List<Integer>> forward, int size) {
        if (start == target) return true;
        boolean[] seen = new boolean[size];
        Deque<Integer> queue = new ArrayDeque<>();
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next : forward.get(current)) {
                if (next == target) return true;
                if (seen[next]) continue;
                seen[next] = true;
                queue.add(next);
            }
        }
        return false;
    }

    private static boolean isAnyTerminal(int node, List<MagicCircleNetwork.Node> nodes, Set<Integer> prefixes) {
        return nodes.get(node).type() == MagicCircleBlockType.OUTPUT && !prefixes.contains(node);
    }

    private static boolean sameEnum(MagicCircleDecl.Entry a, MagicCircleDecl.Entry b) {
        return java.util.Arrays.equals(a.enums(), b.enums());
    }

    /** 沿距离增大的边走，穿过至多 2 个声明条件，能到达哪些输出块。 */
    private static List<Integer> reachOutputs(int start, List<List<Integer>> neighbours, int[] dist,
                                              List<MagicCircleNetwork.Node> nodes, boolean[] declCond,
                                              int size) {
        List<Integer> found = new ArrayList<>();
        Deque<int[]> stack = new ArrayDeque<>(); // {node, condsUsed}
        stack.push(new int[]{start, 0});
        Map<Integer, Integer> best = new HashMap<>();
        while (!stack.isEmpty()) {
            int[] state = stack.pop();
            int current = state[0];
            int conds = state[1];
            for (int next : neighbours.get(current)) {
                if (dist[next] <= dist[current]) continue;
                MagicCircleBlockType type = nodes.get(next).type();
                if (type == MagicCircleBlockType.OUTPUT) {
                    if (next != start && !found.contains(next)) found.add(next);
                    continue;
                }
                if (declCond[next] && conds < 2) {
                    if (conds >= best.getOrDefault(next, 99)) continue;
                    best.put(next, conds);
                    stack.push(new int[]{next, conds + 1});
                }
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- 协议与缺组

    /** 确定终端主协议：语义槽优先，空语义走「协议指定」声明（规范 1.3 / 6.5 第 3 步）。 */
    private static void resolveProtocol(MagicCircleNetwork.Layer layer, Plan plan, Analysis analysis,
                                        boolean strict) {
        MagicCircleNetwork.Node node = layer.nodes().get(plan.terminal);
        MagicCircleAffixes.Protocol semantic = MagicCircleAffixes.Protocol.ofSemantic(node.semantic());
        if (semantic != MagicCircleAffixes.Protocol.NONE) {
            plan.protocol = semantic;
            return;
        }
        // 空语义终端：词缀驱动。必须有「协议指定」声明。
        MagicCircleDecl.Entry protocolDecl = plan.declarations.get(MagicCircleDecl.Group.PROTOCOL);
        if (protocolDecl == null) {
            plan.protocol = MagicCircleAffixes.Protocol.NONE;
            if (strict && !plan.prefixes.isEmpty()) {
                issue(analysis, plan.terminal, "message.dimensional_mana.magiccircle.declare.proto_missing",
                        plan.terminal + 1);
            }
            if (plan.prefixes.isEmpty() && strict) {
                issue(analysis, plan.terminal, "message.dimensional_mana.magiccircle.declare.empty_terminal",
                        plan.terminal + 1);
            }
            return;
        }
        // 指定序号指向第几条前置（1 起）；越界退回「取第一条能定协议的」。
        int index = protocolDecl.primary();
        plan.protocol = MagicCircleAffixes.Protocol.NONE;
        if (index >= 1 && index <= plan.prefixes.size()) {
            MagicCircleNetwork.Node prefix = layer.nodes().get(plan.prefixes.get(index - 1).node());
            plan.protocol = MagicCircleAffixes.Protocol.ofSemantic(prefix.semantic());
        }
        if (plan.protocol == MagicCircleAffixes.Protocol.NONE) {
            for (Prefix prefix : plan.prefixes) {
                MagicCircleAffixes.Protocol guess = protocolFromTag(prefix.affix().tag());
                if (guess != MagicCircleAffixes.Protocol.NONE) {
                    plan.protocol = guess;
                    break;
                }
            }
        }
        if (plan.protocol == MagicCircleAffixes.Protocol.NONE && strict) {
            issue(analysis, plan.terminal, "message.dimensional_mana.magiccircle.declare.proto_invalid",
                    plan.terminal + 1, index);
        }
    }

    /** 词缀标签反推协议（协议指定越界时的兜底）。 */
    private static MagicCircleAffixes.Protocol protocolFromTag(String tag) {
        return switch (tag) {
            case "damage", "explosion", "spread", "pierce" -> MagicCircleAffixes.Protocol.DAMAGE;
            case "heal", "summon" -> MagicCircleAffixes.Protocol.LIFE;
            case "signal" -> MagicCircleAffixes.Protocol.SIGNAL;
            case "purify" -> MagicCircleAffixes.Protocol.PURIFY;
            case "light" -> MagicCircleAffixes.Protocol.LIGHT;
            case "mine" -> MagicCircleAffixes.Protocol.SCAN;
            case "charge", "detect", "teleport", "stabilize", "resonance" -> MagicCircleAffixes.Protocol.CHARGE;
            default -> MagicCircleAffixes.Protocol.NONE;
        };
    }

    /** 规范 11：按协议 + 词缀标签 + 特殊情况列必需边界组，缺的报 E1。 */
    private static void checkRequiredGroups(MagicCircleNetwork.Layer layer, Plan plan, Analysis analysis) {
        if (plan.protocol == MagicCircleAffixes.Protocol.NONE) return; // 协议缺失已经报过
        MagicCircleNetwork.Node terminal = layer.nodes().get(plan.terminal);
        Set<MagicCircleDecl.Group> required = EnumSet.noneOf(MagicCircleDecl.Group.class);
        // 糖终端的必需组看它实际走哪个分支：木 / 毒 / 奥术元素或吃了召唤词缀 = 召唤执行器，
        // 要的是「召唤边界」；其余才是治疗分支，要「治疗边界」（规范 3.2 / 验收单法阵 4）。
        if (plan.protocol == MagicCircleAffixes.Protocol.LIFE && summonSpecialist(terminal, plan)) {
            required.add(MagicCircleDecl.Group.SUMMON);
        } else {
            MagicCircleDecl.Group protocolNeed = MagicCircleDecl.protocolRequirement(plan.protocol);
            if (protocolNeed != null) required.add(protocolNeed);
        }
        Map<String, Integer> tagCount = new HashMap<>();
        for (Prefix prefix : plan.prefixes) {
            MagicCircleDecl.Group need = MagicCircleDecl.requiredGroupForTag(prefix.affix().tag());
            if (need != null) required.add(need);
            tagCount.merge(prefix.affix().tag(), 1, Integer::sum);
        }
        // 同标签多个前置共存 → 词缀优先级（规范 11 行 10）。
        boolean duplicateTags = tagCount.values().stream().anyMatch(n -> n > 1);
        if (duplicateTags) required.add(MagicCircleDecl.Group.PRIORITY);
        // 不兼容词缀：严格模式直接报错；服务器放宽后要「强制兼容」声明（规范 11 行 11）。
        boolean strict = MagicCircleConfig.COMPATIBILITY_STRICT.get();
        for (Prefix prefix : plan.prefixes) {
            if (!MagicCircleAffixes.compatible(plan.protocol, prefix.affix().tag())) {
                if (strict) {
                    issue(analysis, plan.terminal, "message.dimensional_mana.magiccircle.declare.incompatible",
                            plan.terminal + 1, MagicCircleAffixes.affixName(prefix.affix().id()),
                            plan.protocol.shortName());
                } else if (!plan.declared(MagicCircleDecl.Group.COMPAT)) {
                    required.add(MagicCircleDecl.Group.COMPAT);
                }
            }
        }
        // 词缀容量超了也要报错（规范 6.1「超过容量不能连接」）。
        int capacity = MagicCircleAffixes.capacity(terminal.power(),
                MagicCircleConfig.DECLARE_MODE_ENABLED.get() && sugarCore(layer),
                MagicCirclePowders.isSuperconductor(terminal.power()));
        if (plan.prefixes.size() > capacity) {
            issue(analysis, plan.terminal, "message.dimensional_mana.magiccircle.declare.capacity",
                    plan.terminal + 1, plan.prefixes.size(), capacity);
        }
        for (MagicCircleDecl.Group group : required) {
            if (!plan.declared(group)) plan.missing.add(group);
        }
        if (!plan.missing.isEmpty()) {
            issue(analysis, plan.terminal, "message.dimensional_mana.magiccircle.declare.missing",
                    plan.terminal + 1, plan.describe(layer), missingList(plan), parameterHint(plan));
        }
    }

    /**
     * 这块糖终端是不是「召唤特化」：元素槽 = 木 / 毒 / 奥术，或吃了召唤词缀，
     * 或前置染料走唤灵（awaken）。判定口径与 {@code MagicCircleRites.terminalLife} 的分发保持一致，
     * 否则会出现「界面上走召唤、校验却逼你声明治疗边界」的错配。
     */
    private static boolean summonSpecialist(MagicCircleNetwork.Node terminal, Plan plan) {
        ElementType element = MagicCirclePowders.elementOf(terminal.element());
        if (element == ElementType.NATURE || element == ElementType.POISON) return true;
        if (MagicCirclePowders.isArcane(terminal.element())) return true;
        for (Prefix prefix : plan.prefixes) {
            MagicCircleAffixes.Affix affix = prefix.affix();
            if (affix != null && affix.id().startsWith("summon_")) return true;
            if ("awaken".equals(prefix.dyeTag())) return true;
        }
        return false;
    }

    private static boolean sugarCore(MagicCircleNetwork.Layer layer) {
        for (MagicCircleNetwork.Node node : layer.nodes()) {
            if (node.type() != null && node.type().isCore()) {
                return "minecraft:sugar".equals(node.semantic());
            }
        }
        return false;
    }

    /** 「召唤边界、治疗边界」——缺组列表文案。 */
    public static String missingList(Plan plan) {
        StringBuilder builder = new StringBuilder();
        for (MagicCircleDecl.Group group : plan.missing) {
            if (builder.length() > 0) builder.append("、");
            builder.append(groupName(group));
        }
        return builder.toString();
    }

    /** 「请声明：最大数量 / 寿命 / AI / 归属 / Boss权限…」，按缺的组拼（规范 17）。 */
    public static String parameterHint(Plan plan) {
        StringBuilder builder = new StringBuilder();
        for (MagicCircleDecl.Group group : plan.missing) {
            if (builder.length() > 0) builder.append("；");
            builder.append(groupName(group)).append("：").append(parameters(group));
        }
        return builder.toString();
    }

    /** 边界组中文名（平台固定词，和染料短名一个路子）。 */
    public static String groupName(MagicCircleDecl.Group group) {
        return switch (group) {
            case SUMMON -> "召唤边界";
            case HEAL -> "治疗边界";
            case PERMISSION -> "权限边界";
            case SIGNAL -> "信号边界";
            case PROTOCOL -> "协议指定";
            case PRIORITY -> "词缀优先级";
            case COMPAT -> "强制兼容";
            case DAMAGE -> "伤害边界";
            case INTERACT -> "交互边界";
            case PURIFY -> "净化边界";
            case DISORDER -> "紊乱策略";
            case STATUS -> "状态边界";
            case INFO -> "信息边界";
            case VISIBILITY -> "可见性边界";
            case COUNT -> "数量边界";
            case TELEPORT -> "传送边界";
            case ENERGY -> "能量边界";
            case LINK -> "连线边界";
            case PRIVACY -> "隐私边界";
        };
    }

    private static String parameters(MagicCircleDecl.Group group) {
        return switch (group) {
            case SUMMON -> "最大数量 / 寿命 / AI / 归属 / Boss权限 / 锚点失效行为";
            case INTERACT -> "方块黑名单 / 每分钟限额 / 区块加载 / 精准采集 / 掉落去向";
            case DAMAGE -> "PvP / 友伤 / 方块破坏 / 真实伤害上限";
            case HEAL -> "单次最大治疗 / 可治疗敌对亡灵 / 溢出转护盾";
            case TELEPORT -> "最大距离 / 跨维度 / PvP传送 / 安全落点";
            case PURIFY -> "单次清除数 / 冷却 / 可清Boss负面";
            case INFO -> "扫描半径 / 隔墙标记 / 玩家信息范围";
            case SIGNAL -> "无线距离 / 最大频率 / 跨维度";
            case ENERGY -> "充能目标 / 单次充能量 / 冷却";
            case PROTOCOL -> "主协议词缀（下拉选已连接前置）";
            case PRIORITY -> "同标签取最高 / 禁止共存 / 允许但只生效一个";
            case COMPAT -> "强制兼容开关（紊乱+1、哑火+10%）";
            case DISORDER -> "阈值 / 是否允许反噬 / 反噬效果";
            case PERMISSION -> "归属：仅owner / 同队 / 公开";
            case STATUS -> "状态净化强度";
            case VISIBILITY -> "显形开关";
            case COUNT -> "最大数量";
            case LINK -> "中继跳数";
            case PRIVACY -> "可读字段（魔素 / 斗气 / 背包）";
        };
    }

    private static void issue(Analysis analysis, int node, String key, Object... args) {
        analysis.issues.add(MagicCircleIssue.encode(key, args));
        analysis.flagged.add(node);
    }

    /**
     * 就地快查：这块触发节点是不是底座形态（上游有门控条件直接接进来）。
     * 只沿有向连线看一步，供编辑粉末时的即时解析用（完整判定在 {@link #analyze} 里）。
     */
    public static boolean triggerBaseMode(MagicCircleNetwork.Layer layer, int index) {
        List<MagicCircleNetwork.Node> nodes = layer.nodes();
        if (index < 0 || index >= nodes.size()) return false;
        if (nodes.get(index).type() != MagicCircleBlockType.TRIGGER) return false;
        for (MagicCircleNetwork.Link link : layer.links()) {
            if (link.to() != index) continue;
            int up = link.from();
            if (up < 0 || up >= nodes.size()) continue;
            MagicCircleNetwork.Node gate = nodes.get(up);
            if (gate.type() == MagicCircleBlockType.CONDITION
                    && MagicCircleGates.isGateMode(gate.semantic(), gate.gate())) return true;
        }
        return false;
    }

    /** 底座触发节点的上游门控摘要：「真正触发需满足上游条件：生命值 ≤ 30%」。 */
    public static String baseGateSummary(MagicCircleNetwork.Layer layer, int index) {
        List<MagicCircleNetwork.Node> nodes = layer.nodes();
        if (index < 0 || index >= nodes.size()) return "";
        for (MagicCircleNetwork.Link link : layer.links()) {
            if (link.to() != index) continue;
            int up = link.from();
            if (up < 0 || up >= nodes.size()) continue;
            MagicCircleNetwork.Node gate = nodes.get(up);
            if (gate.type() == MagicCircleBlockType.CONDITION
                    && MagicCircleGates.isGateMode(gate.semantic(), gate.gate())) {
                return MagicCircleGates.gateShort(gate);
            }
        }
        return "";
    }

    private MagicCircleGraph() {}
}
