package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「魔法阵绘制台」的服务端会话：打开界面、收笔画、识别、放置功能块、扣粉、撤销。
 *
 * <p>对应设计文档第 4、5 节。客户端只发原始笔画与粉末选择，识别、合法性、
 * 上限、扣粉全部在这边做；每次处理完都回一份 {@link MagicCircleSyncPayload}。</p>
 *
 * <p>选择模式的复制 / 剪切 / 粘贴也由这里执行：剪贴板存在服务端会话里（跟着玩家走、
 * 换层也能粘），粘贴按剪贴板里每块用过的粉照价扣，避免复制出来的块变成白送。</p>
 */
public final class MagicCircleDrawing {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** 撤销栈保留最近 20 步，对应文档 5.4。 */
    private static final int MAX_UNDO = 20;
    /** 玩家自己起的法阵名最长多少字（按字符算，中文一个字算一个）。 */
    private static final int MAX_BLUEPRINT_NAME = 24;
    /** 玩家离阵盘太远就拒绝编辑。 */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private MagicCircleDrawing() {}

    /** 一个玩家当前打开的绘制台。 */
    private static final class Session {
        final ServerLevel level;
        final String networkId;
        final boolean editable;
        int layerIndex;
        final Deque<Snapshot> undo = new ArrayDeque<>();
        List<MagicCircleStroke.Point> stroke = List.of();
        List<MagicCircleGlyph.Match> candidates = List.of();
        final EnumMap<MagicCirclePowders.Slot, String> selected = new EnumMap<>(MagicCirclePowders.Slot.class);
        /** 复制 / 剪切到这里的块，换层、关界面重开都还在（会话还在时）。 */
        Clipboard clipboard;

        Session(ServerLevel level, String networkId, boolean editable) {
            this.level = level;
            this.networkId = networkId;
            this.editable = editable;
        }
    }

    /** 撤销快照：整层功能块与连线的副本。 */
    private record Snapshot(List<MagicCircleNetwork.Node> nodes, List<MagicCircleNetwork.Link> links) {}

    /**
     * 剪贴板：选中的几块功能块（原坐标）＋它们在选区内部的连线，加上包围盒。
     *
     * <p>保存的是绝对坐标，粘贴时按包围盒中心对齐到鼠标，再整体平移夹回画布，
     * 所以复制一块 3×3 的阵不会散架。</p>
     */
    private record Clipboard(List<MagicCircleNetwork.Node> nodes, List<MagicCircleNetwork.Link> links,
                             int minX, int minY, int maxX, int maxY) {

        int size() { return nodes.size(); }

        static Clipboard of(MagicCircleNetwork.Layer layer, List<Integer> indices) {
            int[] remap = new int[layer.nodes().size()];
            java.util.Arrays.fill(remap, -1);
            List<MagicCircleNetwork.Node> nodes = new ArrayList<>(indices.size());
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int i = 0; i < indices.size(); i++) {
                int index = indices.get(i);
                remap[index] = i;
                MagicCircleNetwork.Node node = layer.nodes().get(index);
                nodes.add(node);
                minX = Math.min(minX, node.x());
                minY = Math.min(minY, node.y());
                maxX = Math.max(maxX, node.x());
                maxY = Math.max(maxY, node.y());
            }
            List<MagicCircleNetwork.Link> links = new ArrayList<>();
            for (MagicCircleNetwork.Link link : layer.links()) {
                if (link.from() < 0 || link.to() < 0
                        || link.from() >= remap.length || link.to() >= remap.length) continue;
                int from = remap[link.from()];
                int to = remap[link.to()];
                if (from >= 0 && to >= 0 && from != to) links.add(new MagicCircleNetwork.Link(from, to));
            }
            return new Clipboard(List.copyOf(nodes), List.copyOf(links), minX, minY, maxX, maxY);
        }
    }

    /** 打开绘制台界面：找阵盘、判权限、把完整状态发给客户端。 */
    public static void open(ServerLevel level, BlockPos base, ServerPlayer player) {
        MagicCircleNetwork network = find(level, base);
        if (network == null) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.no_network").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        boolean editable = MagicCircleNetworks.canEdit(network, player);
        Session existing = SESSIONS.get(player.getUUID());
        if (existing == null || existing.level != level || !existing.networkId.equals(network.id())) {
            existing = new Session(level, network.id(), editable);
            SESSIONS.put(player.getUUID(), existing);
        }
        send(player, network, existing);
        if (!editable) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.readonly").withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** 打开绘制台并直接切到某一层（拆解蓝图进第 N 层后，界面要停在第 N 层）。 */
    public static void openAtLayer(ServerLevel level, BlockPos base, ServerPlayer player, int layerIndex) {
        open(level, base, player);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        session.layerIndex = Math.max(0, Math.min(MagicCircleConfig.MAX_LAYERS.get() - 1, layerIndex));
        MagicCircleNetwork network = find(level, base);
        if (network != null) send(player, network, session);
    }

    private static MagicCircleNetwork find(ServerLevel level, BlockPos base) {
        MagicCircleNetworks data = MagicCircleNetworks.get(level);
        if (level.getBlockEntity(base) instanceof MagicCircleBaseBlockEntity entity) {
            MagicCircleNetwork byId = data.byId(entity.networkId());
            if (byId != null) return byId;
        }
        return data.byBase(base);
    }

    /** 界面发来的所有动作都从这里进。 */
    public static void handle(ServerPlayer player, MagicCircleActionPayload payload) {
        String action = payload.action();
        if ("close".equals(action)) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!(player.level() instanceof ServerLevel level) || level != session.level) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        MagicCircleNetwork network = MagicCircleNetworks.get(level).byId(session.networkId);
        if (network == null) {
            SESSIONS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.no_network").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!nearAnyBase(network, player)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.too_far").withStyle(ChatFormatting.RED), false);
            return;
        }
        switch (action) {
            case "submit" -> submit(level, player, network, session, payload);
            case "cancel" -> {
                session.stroke = List.of();
                session.candidates = List.of();
                send(player, network, session);
            }
            case "confirm" -> confirm(level, player, network, session, payload);
            case "manual" -> manual(level, player, network, session, payload);
            case "delete" -> delete(level, player, network, session, payload);
            case "clear" -> clear(level, player, network, session);
            case "undo" -> undo(level, player, network, session);
            case "select" -> select(player, network, session, payload);
            case "transcribe" -> transcribe(level, player, network, session, payload.name());
            case "layer" -> switchLayer(player, network, session, payload);
            case "copy" -> copy(player, network, session, payload);
            case "cut" -> cut(level, player, network, session, payload);
            case "deleteMany" -> deleteMany(level, player, network, session, payload);
            case "paste" -> paste(level, player, network, session, payload);
            case "edit" -> edit(level, player, network, session, payload);
            case "link" -> link(level, player, network, session, payload);
            case "linkChain" -> linkChain(level, player, network, session, payload);
            case "unlink" -> unlink(level, player, network, session, payload);
            case "move" -> move(level, player, network, session, payload);
            default -> { }
        }
    }

    /** 收到笔画：重新吸附（不信客户端）+ 简化 + 识别出最多 3 个候选。 */
    private static void submit(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                               Session session, MagicCircleActionPayload payload) {
        List<MagicCircleStroke.Point> raw = MagicCircleStroke.decode(payload.stroke());
        if (raw.size() < 2) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.stroke_short").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        MagicCircleStroke.Snapped snapped = MagicCircleStroke.snap(raw,
                network.canvasWidth(), network.canvasHeight());
        // 识别用完整的吸附链（DP 简化会吃掉自交点和端点，特征就不准了）；
        // 存下来给界面回显的那份才做简化，保证网络包够小。
        session.stroke = MagicCircleStroke.simplifyForNetwork(snapped.points(),
                Math.min(network.canvasWidth(), network.canvasHeight()));
        session.candidates = MagicCircleGlyph.recognize(snapped.points(),
                network.canvasWidth(), network.canvasHeight());
        if (session.candidates.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.recognize_failed").withStyle(ChatFormatting.YELLOW), false);
        }
        send(player, network, session);
    }

    /** 玩家点了一个候选：检查上限与粉末，真的放下一个功能块。 */
    private static void confirm(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                                Session session, MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleGlyph glyph = null;
        for (MagicCircleGlyph.Match match : session.candidates) {
            if (match.glyph().id().equals(payload.glyph())) {
                glyph = match.glyph();
                break;
            }
        }
        if (glyph == null || session.stroke.size() < 2) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.recognize_failed").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        if (layer.nodes().size() >= MagicCircleConfig.MAX_FUNCTIONAL_BLOCKS.get()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.node_limit",
                    MagicCircleConfig.MAX_FUNCTIONAL_BLOCKS.get()).withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!consumePowders(player, session)) return;

        pushUndo(session, layer);
        int[] bounds = MagicCircleStroke.bounds(session.stroke);
        int centerX = (bounds[0] + bounds[2]) / 2;
        int centerY = (bounds[1] + bounds[3]) / 2;
        int index = layer.nodes().size();
        layer.add(new MagicCircleNetwork.Node(glyph.blockType(), centerX, centerY,
                selected(session, MagicCirclePowders.Slot.ELEMENT),
                selected(session, MagicCirclePowders.Slot.SEMANTIC),
                selected(session, MagicCirclePowders.Slot.POWER),
                selected(session, MagicCirclePowders.Slot.DYE),
                glyph.id(), false));
        autoConnect(layer, index);
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.placed",
                Component.translatable(glyph.blockType().translationKey())).withStyle(ChatFormatting.GREEN), false);
        session.stroke = List.of();
        session.candidates = List.of();
        send(player, network, session);
    }

    /** 识别失败后的手动框选：框一块区域，直接指定功能块类型（复杂度 +1、贵 5%）。 */
    private static void manual(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                               Session session, MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleBlockType type = MagicCircleBlockType.byId(payload.blockType());
        if (type == null) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        if (layer.nodes().size() >= MagicCircleConfig.MAX_FUNCTIONAL_BLOCKS.get()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.node_limit",
                    MagicCircleConfig.MAX_FUNCTIONAL_BLOCKS.get()).withStyle(ChatFormatting.RED), false);
            return;
        }
        int x1 = clamp(Math.min(payload.x1(), payload.x2()), network.canvasWidth());
        int y1 = clamp(Math.min(payload.y1(), payload.y2()), network.canvasHeight());
        int x2 = clamp(Math.max(payload.x1(), payload.x2()), network.canvasWidth());
        int y2 = clamp(Math.max(payload.y1(), payload.y2()), network.canvasHeight());
        if (!consumePowders(player, session)) return;

        pushUndo(session, layer);
        int centerX = (x1 + x2) / 2;
        int centerY = (y1 + y2) / 2;
        String variant = "frame:" + x1 + ":" + y1 + ":" + x2 + ":" + y2;
        int index = layer.nodes().size();
        layer.add(new MagicCircleNetwork.Node(type, centerX, centerY,
                selected(session, MagicCirclePowders.Slot.ELEMENT),
                selected(session, MagicCirclePowders.Slot.SEMANTIC),
                selected(session, MagicCirclePowders.Slot.POWER),
                selected(session, MagicCirclePowders.Slot.DYE),
                variant, true));
        autoConnect(layer, index);
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.manual_placed",
                Component.translatable(type.translationKey())).withStyle(ChatFormatting.GREEN), false);
        send(player, network, session);
    }

    private static void delete(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                               Session session, MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int index = payload.x1();
        if (index < 0 || index >= layer.nodes().size()) return;
        pushUndo(session, layer);
        layer.removeNodeAt(index);
        MagicCircleNetworks.markDirty(level);
        send(player, network, session);
    }

    /**
     * 复制：把选中的块（含它们之间的连线）记进剪贴板。
     *
     * <p>只读玩家也能复制——复制不改画布、不花粉末，只是把东西记下来。</p>
     */
    private static void copy(ServerPlayer player, MagicCircleNetwork network, Session session,
                             MagicCircleActionPayload payload) {
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        List<Integer> indices = selectedIndices(layer, payload);
        if (indices.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.copy_empty").withStyle(ChatFormatting.YELLOW), false);
            send(player, network, session);
            return;
        }
        session.clipboard = Clipboard.of(layer, indices);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.copied", indices.size()).withStyle(ChatFormatting.AQUA), false);
        send(player, network, session);
    }

    /** 剪切：先记进剪贴板，再把选中的块从本层删掉（一步撤销能全捞回来）。 */
    private static void cut(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session,
                            MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        List<Integer> indices = selectedIndices(layer, payload);
        if (indices.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.copy_empty").withStyle(ChatFormatting.YELLOW), false);
            send(player, network, session);
            return;
        }
        session.clipboard = Clipboard.of(layer, indices);
        pushUndo(session, layer);
        removeNodes(layer, indices);
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.cut", indices.size()).withStyle(ChatFormatting.AQUA), false);
        send(player, network, session);
    }

    /** 批量删除：选择模式下框一片再删，一次撤销全部还原。 */
    private static void deleteMany(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session,
                                   MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        List<Integer> indices = selectedIndices(layer, payload);
        if (indices.isEmpty()) {
            send(player, network, session);
            return;
        }
        pushUndo(session, layer);
        removeNodes(layer, indices);
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.deleted_many", indices.size())
                .withStyle(ChatFormatting.GRAY), false);
        send(player, network, session);
    }

    /**
     * 粘贴：把剪贴板里的块整体搬到鼠标位置。
     *
     * <p>粉照价扣——剪贴板里每块用过的粉都要在背包里（创造模式除外），
     * 不然复制粘贴就成了免费造块。位置按包围盒中心对齐目标格，整体夹回画布内。</p>
     */
    private static void paste(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session,
                              MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        Clipboard clipboard = session.clipboard;
        if (clipboard == null || clipboard.nodes().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.paste_empty").withStyle(ChatFormatting.YELLOW), false);
            send(player, network, session);
            return;
        }
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int nodeLimit = MagicCircleConfig.MAX_FUNCTIONAL_BLOCKS.get();
        int room = nodeLimit - layer.nodes().size();
        if (clipboard.size() > room) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.paste_no_room",
                    clipboard.size(), layer.nodes().size(), nodeLimit).withStyle(ChatFormatting.RED), false);
            send(player, network, session);
            return;
        }
        if (layer.links().size() + clipboard.links().size() > MagicCircleConfig.MAX_CONNECTIONS.get()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.problem.too_many_links").withStyle(ChatFormatting.RED), false);
            send(player, network, session);
            return;
        }

        int canvasW = network.canvasWidth();
        int canvasH = network.canvasHeight();
        if (clipboard.maxX() - clipboard.minX() >= canvasW || clipboard.maxY() - clipboard.minY() >= canvasH) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.paste_out_of_bounds").withStyle(ChatFormatting.RED), false);
            send(player, network, session);
            return;
        }
        int targetX = payload.x1() < 0 ? canvasW / 2 : payload.x1();
        int targetY = payload.y1() < 0 ? canvasH / 2 : payload.y1();
        int centerX = (clipboard.minX() + clipboard.maxX()) / 2;
        int centerY = (clipboard.minY() + clipboard.maxY()) / 2;
        int dx = clampRange(targetX - centerX, -clipboard.minX(), canvasW - 1 - clipboard.maxX());
        int dy = clampRange(targetY - centerY, -clipboard.minY(), canvasH - 1 - clipboard.maxY());
        if (dx == Integer.MIN_VALUE || dy == Integer.MIN_VALUE) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.paste_out_of_bounds").withStyle(ChatFormatting.RED), false);
            send(player, network, session);
            return;
        }

        List<String> needed = new ArrayList<>();
        for (MagicCircleNetwork.Node node : clipboard.nodes()) {
            needed.add(node.element());
            needed.add(node.semantic());
            needed.add(node.power());
            needed.add(node.dye());
        }
        if (!consume(player, needed)) {
            send(player, network, session);
            return;
        }

        pushUndo(session, layer);
        int base = layer.nodes().size();
        for (MagicCircleNetwork.Node node : clipboard.nodes()) {
            layer.add(new MagicCircleNetwork.Node(node.type(), node.x() + dx, node.y() + dy,
                    node.element(), node.semantic(), node.power(), node.dye(), node.variant(), node.handDrawn()));
        }
        for (MagicCircleNetwork.Link link : clipboard.links()) {
            layer.addLink(base + link.from(), base + link.to());
        }
        if (base > 0) {
            // 粘进来的第一块连到本层最近的老块，免得新块「从核心走不到」。
            autoConnect(layer, base);
        }
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.pasted", clipboard.size(), session.layerIndex + 1)
                .withStyle(ChatFormatting.GREEN), false);
        send(player, network, session);
    }

    /**
     * 编辑一块功能块的粉槽：装上新粉或把旧粉拿下来。
     *
     * <p>换下来的粉退回背包，装上去的粉从背包扣，所以「拆一块」不会白丢材料。</p>
     */
    private static void edit(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session,
                             MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int index = payload.x1();
        if (index < 0 || index >= layer.nodes().size()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.edit_node_missing").withStyle(ChatFormatting.RED), false);
            send(player, network, session);
            return;
        }
        MagicCirclePowders.Slot slot = slotById(payload.slot());
        if (slot == null) return;
        MagicCircleNetwork.Node node = layer.nodes().get(index);
        String current = slotValue(node, slot);
        String itemId = payload.powder() == null ? "" : payload.powder();
        if (itemId.equals(current)) {
            send(player, network, session);
            return;
        }
        MagicCirclePowders.Powder powder = MagicCirclePowders.get(itemId);
        if (!itemId.isEmpty()) {
            if (powder == null || powder.slot() != slot) return;
            if (!MagicCirclePowders.unlocked(powder, player)) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.powder_locked",
                        powder.name(), powder.requiredLevel()).withStyle(ChatFormatting.RED), false);
                return;
            }
        }
        if (!consume(player, itemId.isEmpty() ? List.of() : List.of(itemId))) {
            send(player, network, session);
            return;
        }
        // 创造模式本来就「不耗粉」，也就不往背包里塞东西，免得换一次粉多出一堆材料。
        if (!MagicCirclePowders.creativeFree(player)) refund(player, current);
        pushUndo(session, layer);
        layer.setNode(index, withSlot(node, slot, itemId));
        MagicCircleNetworks.markDirty(level);
        Component slotName = Component.translatable(slot.translationKey());
        if (itemId.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.edit_removed", slotName).withStyle(ChatFormatting.GRAY),
                    false);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.edit_applied",
                    slotName, powder.name()).withStyle(ChatFormatting.GREEN), false);
        }
        send(player, network, session);
    }

    /**
     * 把两块功能块连起来（选择模式下选中两块按 L）。
     *
     * <p>连线的意义在「从核心可达」：没有连线的块在试运行 / 启动时会被判成孤立块。
     * 这里和 {@link #autoConnect} 走同一条 {@code Layer#addLink}，重复连线自动去重，
     * 条数受 {@link MagicCircleConfig#MAX_CONNECTIONS} 限制。</p>
     */
    private static void link(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session,
                             MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int from = payload.x1();
        int to = payload.y1();
        if (from < 0 || to < 0 || from == to
                || from >= layer.nodes().size() || to >= layer.nodes().size()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.node.link.node_missing").withStyle(ChatFormatting.RED), false);
            send(player, network, session);
            return;
        }
        for (MagicCircleNetwork.Link existing : layer.links()) {
            if ((existing.from() == from && existing.to() == to)
                    || (existing.from() == to && existing.to() == from)) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.node.link.already").withStyle(ChatFormatting.GRAY), false);
                send(player, network, session);
                return;
            }
        }
        int max = MagicCircleConfig.MAX_CONNECTIONS.get();
        if (layer.links().size() >= max) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.node.link.limit", max).withStyle(ChatFormatting.RED), false);
            send(player, network, session);
            return;
        }
        pushUndo(session, layer);
        layer.addLink(from, to);
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.node.link.done", from + 1, to + 1)
                .withStyle(ChatFormatting.GREEN), false);
        send(player, network, session);
    }

    /**
     * 按选择顺序把一串功能块连成一条链（选择模式下选中三块以上按 L）。
     *
     * <p>顺序就是玩家点的先后：第 1 个 → 第 2 个 → 第 3 个……所以「先点上游、再点下游」
     * 这条规则在两块和三块以上时是同一个。已经连着的同方向跳过，方向反了的就地翻过来；
     * 整条链只记一条撤销，Ctrl+Z 一次撤干净。</p>
     */
    private static void linkChain(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                                  Session session, MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int size = layer.nodes().size();
        int[] raw = MagicCircleActionPayload.decodeIndices(payload.indices());
        List<Integer> chain = new ArrayList<>();
        for (int index : raw) {
            if (index < 0 || index >= size) continue;
            // 同一个块重复出现（点重了 / 转回自己）就跳过，免得画出环来。
            if (chain.contains(index)) continue;
            chain.add(index);
        }
        if (chain.size() < 2) {
            player.displayClientMessage(Component.translatable(
                            "message.dimensional_mana.magiccircle.node.link.node_missing")
                    .withStyle(ChatFormatting.RED), false);
            send(player, network, session);
            return;
        }
        int max = MagicCircleConfig.MAX_CONNECTIONS.get();
        // 撤销快照只在真的要动连线时才拍一张：全都已经连好了就别往撤销栈里塞空记录。
        boolean undoPushed = false;
        int added = 0;
        int flipped = 0;
        boolean hitLimit = false;
        for (int i = 0; i + 1 < chain.size(); i++) {
            int from = chain.get(i);
            int to = chain.get(i + 1);
            boolean reversed = false;
            boolean exists = false;
            for (MagicCircleNetwork.Link link : layer.links()) {
                if (link.from() == from && link.to() == to) {
                    exists = true;
                    break;
                }
                if (link.from() == to && link.to() == from) {
                    reversed = true;
                    break;
                }
            }
            if (exists) continue;
            if (reversed) {
                // 反着连的：拆掉按新方向重连，条数不变。
                if (!undoPushed) {
                    pushUndo(session, layer);
                    undoPushed = true;
                }
                layer.removeLink(from, to);
                layer.addLink(from, to);
                flipped++;
                continue;
            }
            if (layer.links().size() >= max) {
                hitLimit = true;
                break;
            }
            if (!undoPushed) {
                pushUndo(session, layer);
                undoPushed = true;
            }
            layer.addLink(from, to);
            added++;
        }
        MagicCircleNetworks.markDirty(level);
        if (added == 0 && flipped == 0) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.node.link.already").withStyle(ChatFormatting.GRAY), false);
            send(player, network, session);
            return;
        }
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) order.append(" → ");
            order.append(chain.get(i) + 1);
        }
        player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.node.link.chain_done",
                        order.toString(), added, flipped)
                .withStyle(ChatFormatting.GREEN), false);
        if (hitLimit) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.node.link.limit", max)
                    .withStyle(ChatFormatting.RED), false);
        }
        send(player, network, session);
    }

    /** 拆掉一条连线（按住 K 点击那条线）。方向无所谓，两层校验都认。 */
    private static void unlink(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session,
                               MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int from = payload.x1();
        int to = payload.y1();
        if (from < 0 || to < 0 || from == to
                || from >= layer.nodes().size() || to >= layer.nodes().size()) {
            send(player, network, session);
            return;
        }
        if (!layer.removeLink(from, to)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.node.unlink.missing").withStyle(ChatFormatting.GRAY), false);
            send(player, network, session);
            return;
        }
        pushUndo(session, layer);
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.node.unlink.done", from + 1, to + 1)
                .withStyle(ChatFormatting.AQUA), false);
        send(player, network, session);
    }

    /**
     * 拖动一块功能块到新位置（选择模式下双击一块再拖）。
     *
     * <p>只改坐标，粉末、类型、已连的线一律照旧：连线存的是下标，不存坐标。
     * 位置夹在画布内，改完进撤销栈，Ctrl+Z 能拖回去。</p>
     */
    private static void move(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session,
                             MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int index = payload.x1();
        if (index < 0 || index >= layer.nodes().size()) {
            send(player, network, session);
            return;
        }
        int x = clamp(payload.y1(), network.canvasWidth());
        int y = clamp(payload.y2(), network.canvasHeight());
        MagicCircleNetwork.Node node = layer.nodes().get(index);
        if (node.x() == x && node.y() == y) {
            send(player, network, session);
            return;
        }
        pushUndo(session, layer);
        layer.setNode(index, new MagicCircleNetwork.Node(node.type(), x, y,
                node.element(), node.semantic(), node.power(), node.dye(), node.variant(), node.handDrawn()));
        MagicCircleNetworks.markDirty(level);
        send(player, network, session);
    }

    private static void clear(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        if (layer.nodes().isEmpty()) {
            send(player, network, session);
            return;
        }
        pushUndo(session, layer);
        layer.clear();
        MagicCircleNetworks.markDirty(level);
        send(player, network, session);
    }

    private static void undo(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session) {
        if (!requireEditable(player, session)) return;
        Snapshot snapshot = session.undo.pollLast();
        if (snapshot == null) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.undo_empty").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        layer.restore(snapshot.nodes(), snapshot.links());
        MagicCircleNetworks.markDirty(level);
        send(player, network, session);
    }

    /** 换一层编辑：多层叠加时每层各是一张阵图，玩家得能翻到第 2、3、4 层去看和改。 */
    private static void switchLayer(ServerPlayer player, MagicCircleNetwork network,
                                    Session session, MagicCircleActionPayload payload) {
        int max = Math.max(1, MagicCircleConfig.MAX_LAYERS.get());
        int target = Math.max(0, Math.min(max - 1, session.layerIndex + payload.x1()));
        if (target != session.layerIndex) {
            session.layerIndex = target;
            session.stroke = List.of();
            session.candidates = List.of();
        }
        send(player, network, session);
    }

    /** 切换四个粉槽里某槽的选择；只接受该槽自己的粉，且必须已解锁。 */
    private static void select(ServerPlayer player, MagicCircleNetwork network, Session session,
                               MagicCircleActionPayload payload) {
        MagicCirclePowders.Slot slot = slotById(payload.slot());
        if (slot == null) return;
        String itemId = payload.powder();
        if (itemId == null || itemId.isEmpty()) {
            session.selected.remove(slot);
            send(player, network, session);
            return;
        }
        MagicCirclePowders.Powder powder = MagicCirclePowders.get(itemId);
        if (powder == null || powder.slot() != slot) return;
        if (!MagicCirclePowders.unlocked(powder, player)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.powder_locked",
                    powder.name(), powder.requiredLevel()).withStyle(ChatFormatting.RED), false);
            return;
        }
        session.selected.put(slot, itemId);
        send(player, network, session);
    }

    /**
     * 抄录：把整张画布抄成一份蓝图，写进手里拿着的载体（纸 / 魔力布匹 / 魔法绸缎）。
     *
     * <p>载体是「消耗品」：抄一次掉一个，换回来一个带蓝图数据的同类物品。纸只能存草稿
     * （16 块 / 1 层，不能贴面），布是单层蓝图（32 块），绸是多层蓝图（64 块 / 3 层）。
     * 容量不够时整笔不抄，先把差多少讲清楚，免得抄一半。</p>
     *
     * @param requestedName 界面上弹框里输入的法阵名；留空就用 {@link MagicCircleBlueprint#suggestName}
     *                      自动起的默认名（按阵里用得最多的元素，比如「炎法阵」）
     */
    private static void transcribe(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                                   Session session, String requestedName) {
        if (!requireEditable(player, session)) return;
        if (network.nodeCount() == 0) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.nothing_to_copy")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        ItemStack carrierStack = findCarrier(player);
        if (carrierStack.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.no_carrier")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(carrierStack);
        if (carrier == null || carrier == MagicCircleBlueprint.Carrier.WORN) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.wrong_carrier")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        MagicCircleBlueprint blueprint = MagicCircleBlueprint.fromNetwork(network, carrier,
                player.getGameProfile().getName(), blueprintName(requestedName, network));
        if (!blueprint.fits(carrier)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.capacity_full",
                    carrier.fallbackName(), carrier.maxNodes(), blueprint.nodeCount(),
                    carrier.maxLayers(), blueprint.filledLayerCount())
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        ItemStack written = MagicCircleBlueprint.createStack(carrier, blueprint);
        if (written.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.wrong_carrier")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        carrierStack.shrink(1);
        if (!player.getInventory().add(written)) player.drop(written, false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.transcribed",
                blueprint.name(), carrier.fallbackName(), blueprint.nodeCount(), blueprint.filledLayerCount())
                .withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.transcribed_hint")
                .withStyle(ChatFormatting.GRAY), false);
        send(player, network, session);
    }

    /**
     * 抄录用哪个名字：清洗玩家输入；空的、全是空白或被洗掉，就退回按元素自动起名。
     *
     * <p>控制字符和 {@code §} 颜色符一律不收——法阵名会出现在物品提示和聊天栏里，
     * 放进去会把字染色或者把整行吃掉。手改包发来的超长名字也在这里截断。</p>
     */
    private static String blueprintName(String requested, MagicCircleNetwork network) {
        if (requested != null && !requested.isBlank()) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < requested.length() && builder.length() < MAX_BLUEPRINT_NAME; i++) {
                char c = requested.charAt(i);
                if (c < 0x20 || c == 0x7F || c == '§') continue;
                builder.append(c);
            }
            String cleaned = builder.toString().trim();
            if (!cleaned.isEmpty()) return cleaned;
        }
        return MagicCircleBlueprint.suggestName(network);
    }

    /** 抄录用哪个载体：先看主手，再看副手。 */
    private static ItemStack findCarrier(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        MagicCircleBlueprint.Carrier mainCarrier = MagicCircleBlueprint.carrierOf(main);
        if (mainCarrier != null && mainCarrier != MagicCircleBlueprint.Carrier.WORN) return main;
        ItemStack off = player.getOffhandItem();
        MagicCircleBlueprint.Carrier offCarrier = MagicCircleBlueprint.carrierOf(off);
        if (offCarrier != null && offCarrier != MagicCircleBlueprint.Carrier.WORN) return off;
        return ItemStack.EMPTY;
    }

    private static boolean requireEditable(ServerPlayer player, Session session) {
        if (session.editable) return true;
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.readonly").withStyle(ChatFormatting.RED), false);
        return false;
    }

    /** 检查并扣除「这一笔」四个粉槽要用的粉；缺任何一种就整笔不落，避免扣一半。 */
    private static boolean consumePowders(ServerPlayer player, Session session) {
        List<String> itemIds = new ArrayList<>(MagicCirclePowders.Slot.values().length);
        for (MagicCirclePowders.Slot slot : MagicCirclePowders.Slot.values()) {
            itemIds.add(session.selected.getOrDefault(slot, ""));
        }
        return consume(player, itemIds);
    }

    /**
     * 按一份「用了哪些粉」的清单扣粉（放置、粘贴、换粉共用）。
     *
     * <p>先整份检查解锁与背包数量，全够才扣——不会出现扣了一半又失败。
     * 创造模式直接用创造物品栏的粉，背包一点不掉。</p>
     */
    private static boolean consume(ServerPlayer player, List<String> itemIds) {
        // 创造模式：直接用创造物品栏里的粉，背包一点不掉（也就不用检查够不够）。
        if (MagicCirclePowders.creativeFree(player)) return true;
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (String itemId : itemIds) {
            if (itemId == null || itemId.isEmpty()) continue;
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(itemId);
            if (powder == null) continue;
            if (!MagicCirclePowders.unlocked(powder, player)) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.powder_locked",
                        powder.name(), powder.requiredLevel()).withStyle(ChatFormatting.RED), false);
                return false;
            }
            Item item = powder.item();
            if (item == Items.AIR) continue;
            needed.merge(item, 1, Integer::sum);
        }
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int have = count(player, entry.getKey());
            if (have < entry.getValue()) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.powder_missing",
                        Component.translatable(entry.getKey().getDescriptionId()),
                        entry.getValue() - have).withStyle(ChatFormatting.RED), false);
                return false;
            }
        }
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            remove(player, entry.getKey(), entry.getValue());
        }
        return true;
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void remove(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    /** 把界面送来的下标清一遍：只留本层真实存在的，去重并按升序排好。 */
    private static List<Integer> selectedIndices(MagicCircleNetwork.Layer layer, MagicCircleActionPayload payload) {
        List<Integer> out = new ArrayList<>();
        for (int index : MagicCircleActionPayload.decodeIndices(payload.indices())) {
            if (index < 0 || index >= layer.nodes().size() || out.contains(index)) continue;
            out.add(index);
        }
        out.sort(Integer::compareTo);
        return out;
    }

    /** 一次删掉多块：从大到小删，下标才不会边删边错位。 */
    private static void removeNodes(MagicCircleNetwork.Layer layer, List<Integer> indices) {
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort(Comparator.reverseOrder());
        for (int index : sorted) layer.removeNodeAt(index);
    }

    /** 把一块粉退回玩家背包；放不下就掉在脚边。 */
    private static void refund(ServerPlayer player, String itemId) {
        MagicCirclePowders.Powder powder = MagicCirclePowders.get(itemId);
        if (powder == null) return;
        Item item = powder.item();
        if (item == Items.AIR) return;
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static String slotValue(MagicCircleNetwork.Node node, MagicCirclePowders.Slot slot) {
        return switch (slot) {
            case ELEMENT -> node.element();
            case SEMANTIC -> node.semantic();
            case POWER -> node.power();
            case DYE -> node.dye();
        };
    }

    private static MagicCircleNetwork.Node withSlot(MagicCircleNetwork.Node node, MagicCirclePowders.Slot slot,
                                                    String itemId) {
        String element = node.element();
        String semantic = node.semantic();
        String power = node.power();
        String dye = node.dye();
        switch (slot) {
            case ELEMENT -> element = itemId;
            case SEMANTIC -> semantic = itemId;
            case POWER -> power = itemId;
            case DYE -> dye = itemId;
        }
        return new MagicCircleNetwork.Node(node.type(), node.x(), node.y(),
                element, semantic, power, dye, node.variant(), node.handDrawn());
    }

    /** 夹到 [min, max]；min > max（画布比剪贴板还小）时返回 Integer.MIN_VALUE 让调用方拒绝。 */
    private static int clampRange(int value, int min, int max) {
        if (min > max) return Integer.MIN_VALUE;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 新放下的块自动连到最近的一个已有块——不连的话「从核心可达」永远不成立。
     *
     * <p>方向是「已有块 → 新块」（上游 → 下游）。输出块是链条的终点，从它往外接线走不通，
     * 所以有别的块可选时先跳过输出块，实在只剩输出块才照旧连。</p>
     */
    private static void autoConnect(MagicCircleNetwork.Layer layer, int index) {
        if (index <= 0 || layer.links().size() >= MagicCircleConfig.MAX_CONNECTIONS.get()) return;
        MagicCircleNetwork.Node newest = layer.nodes().get(index);
        int best = nearestSource(layer, index, newest, true);
        if (best < 0) best = nearestSource(layer, index, newest, false);
        if (best >= 0) layer.addLink(best, index);
    }

    /** 在已经放下的块里找离新块最近的一个；{@code skipOutput} 时跳过输出块（输出是终点）。 */
    private static int nearestSource(MagicCircleNetwork.Layer layer, int index,
                                     MagicCircleNetwork.Node newest, boolean skipOutput) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < index; i++) {
            MagicCircleNetwork.Node other = layer.nodes().get(i);
            if (skipOutput && other.type() != null && other.type().isOutput()) continue;
            double distance = Math.hypot(other.x() - newest.x(), other.y() - newest.y());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static void pushUndo(Session session, MagicCircleNetwork.Layer layer) {
        session.undo.addLast(new Snapshot(List.copyOf(layer.nodes()), List.copyOf(layer.links())));
        while (session.undo.size() > MAX_UNDO) session.undo.pollFirst();
    }

    private static String selected(Session session, MagicCirclePowders.Slot slot) {
        return session.selected.getOrDefault(slot, "");
    }

    private static MagicCirclePowders.Slot slotById(String id) {
        for (MagicCirclePowders.Slot slot : MagicCirclePowders.Slot.values()) {
            if (slot.id().equals(id)) return slot;
        }
        return null;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static boolean nearAnyBase(MagicCircleNetwork network, ServerPlayer player) {
        for (BlockPos base : network.bases()) {
            if (player.distanceToSqr(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5) <= MAX_DISTANCE_SQR) {
                return true;
            }
        }
        return false;
    }

    private static void send(ServerPlayer player, MagicCircleNetwork network, Session session) {
        PacketDistributor.sendToPlayer(player, buildPayload(player, network, session));
    }

    private static MagicCircleSyncPayload buildPayload(ServerPlayer player, MagicCircleNetwork network,
                                                       Session session) {
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        MagicCircleCost.Evaluation evaluation = MagicCircleCost.evaluate(network);

        List<MagicCircleSyncPayload.NodeView> nodes = new ArrayList<>();
        for (MagicCircleNetwork.Node node : layer.nodes()) {
            nodes.add(new MagicCircleSyncPayload.NodeView(node.type() == null ? "" : node.type().id(),
                    node.x(), node.y(), node.handDrawn(),
                    node.element(), node.semantic(), node.power(), node.dye(), node.variant()));
        }
        List<MagicCircleSyncPayload.LinkView> links = new ArrayList<>();
        for (MagicCircleNetwork.Link link : layer.links()) {
            links.add(new MagicCircleSyncPayload.LinkView(link.from(), link.to()));
        }
        List<MagicCircleSyncPayload.PowderView> powders = new ArrayList<>();
        for (MagicCirclePowders.Powder powder : MagicCirclePowders.all()) {
            powders.add(new MagicCircleSyncPayload.PowderView(powder.itemId(),
                    MagicCirclePowders.unlocked(powder, player), powder.requiredLevel()));
        }
        List<MagicCircleSyncPayload.SlotView> slots = new ArrayList<>();
        for (MagicCirclePowders.Slot slot : MagicCirclePowders.Slot.values()) {
            slots.add(new MagicCircleSyncPayload.SlotView(slot.id(), selected(session, slot)));
        }
        List<MagicCircleSyncPayload.CandidateView> candidates = new ArrayList<>();
        for (MagicCircleGlyph.Match match : session.candidates) {
            candidates.add(new MagicCircleSyncPayload.CandidateView(match.glyph().id(), match.score()));
        }

        String ownerName = "";
        if (network.owner() != null) {
            ServerPlayer owner = player.server.getPlayerList().getPlayer(network.owner());
            ownerName = owner != null ? owner.getGameProfile().getName() : "";
        }

        return new MagicCircleSyncPayload(network.id(), network.baseWidth(), network.baseHeight(),
                network.canvasWidth(), network.canvasHeight(), session.layerIndex, session.editable,
                MagicCirclePowders.creativeFree(player), session.clipboard == null ? 0 : session.clipboard.size(),
                ownerName,
                MagicProgression.get(player).magicLevel(), MagicCirclePowders.unlockedCount(player),
                MagicCirclePowders.all().size(), List.copyOf(nodes), List.copyOf(links),
                evaluation.startMana(), evaluation.upkeepMana(), evaluation.triggerMana(),
                evaluation.strength(), evaluation.complexity(), evaluation.disorder(), evaluation.efficiency(),
                List.copyOf(evaluation.problems()), List.copyOf(powders), List.copyOf(slots),
                List.copyOf(candidates), MagicCircleStroke.encode(session.stroke));
    }
}
