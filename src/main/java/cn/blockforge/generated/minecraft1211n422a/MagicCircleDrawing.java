package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
    /** 会改动画布内容的动作：别人的草稿层只许草稿主人改。 */
    private static final java.util.Set<String> MUTATING = java.util.Set.of(
            "submit", "confirm", "manual", "delete", "clear", "undo", "transcribe", "cut", "deleteMany",
            "paste", "edit", "declare", "gate", "link", "linkChain", "unlink", "move", "bpMount");
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
        /**
         * r211 蓝图修改模式：玩家手持蓝图右键基座后<b>收进台面槽位</b>的那一份。
         * 关界面 / 打包带走都会原样退回背包；只有按「复制」并付齐粉末，
         * 画布上那层才从草样转成正式样本留在台上。
         */
        ItemStack editBlueprint = ItemStack.EMPTY;
        /** 这份蓝图的显示名（同步给界面画在槽位旁边）。 */
        String editBlueprintName = "";
        /** 草样铺在第几层（-1 = 台面上现在没有自己在改的草样）。 */
        int editLayer = -1;
        /** 这份蓝图是不是已经按「复制」付过粉、样本留在台面上了。 */
        boolean blueprintCopied;
        /** r218：槽位里这份是玩家点空槽位装进来的<b>空白载体</b>（还没有蓝图数据）。 */
        boolean bpBlank;
        /** r218：槽位里的空白载体已经「抄录」成成品蓝图，正等玩家打包带走。 */
        boolean bpDone;

        Session(ServerLevel level, String networkId, boolean editable) {
            this.level = level;
            this.networkId = networkId;
            this.editable = editable;
        }
    }

    /** 撤销快照：整层功能块与连线的副本 + 这次改动退掉的粉（undo 时要按单收回来）。 */
    private record Snapshot(List<MagicCircleNetwork.Node> nodes, List<MagicCircleNetwork.Link> links,
                            List<String> refunds) {

        Snapshot(List<MagicCircleNetwork.Node> nodes, List<MagicCircleNetwork.Link> links) {
            this(nodes, links, List.of());
        }
    }

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

    // ================================================================ r211 蓝图修改模式

    /**
     * 手持蓝图右键绘制台基座的真正入口（r211 重做）：<b>不再一键复制</b>，而是「上稿修改」——
     *
     * <ul>
     *   <li>蓝图收进台面右下角的槽位（从手上拿 1 张），阵图铺到第一张空层，标成这个玩家的<b>草样层</b>；</li>
     *   <li>草样层是免费的草稿：放块 / 换粉不扣粉末，怎么改都行；</li>
     *   <li>按<b>打包带走</b>（或直接关界面）：草样整层抹掉、蓝图退回手上——台面不留任何痕迹；</li>
     *   <li>按<b>复制</b>：按草样层现在的粉末清单从背包扣一次（创造免扣），草样转正——
     *       之后拿走蓝图，绘制台上仍留一份样本。</li>
     * </ul>
     */
    public static void beginBlueprintEdit(ServerLevel level, BlockPos base, ServerPlayer player, ItemStack held) {
        MagicCircleNetworks data = MagicCircleNetworks.get(level);
        MagicCircleNetwork network = data.byBase(base);
        if (network == null && level.getBlockEntity(base) instanceof MagicCircleBaseBlockEntity entity) {
            network = data.byId(entity.networkId());
        }
        if (network == null) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.no_network").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (!MagicCircleNetworks.canEdit(network, player)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.readonly").withStyle(ChatFormatting.RED), false);
            return;
        }
        sweepOrphanScratch(level, network);
        MagicCircleBlueprint blueprint = MagicCircleBlueprint.of(held);
        if (blueprint == null) return;
        // 这份阵图已经是台上某层的正式样本：直接打开那一层给槽位挂上蓝图，不再铺草稿层。
        int sampleLayer = MagicCircleProjectionEvents.findExistingLayer(network, blueprint);
        if (sampleLayer >= 0) {
            ItemStack already = held.copyWithCount(1);
            takeIntoSlot(held);
            open(level, base, player);
            Session exist = SESSIONS.get(player.getUUID());
            if (exist != null) {
                exist.editBlueprint = already;
                exist.editBlueprintName = blueprint.name();
                exist.editLayer = sampleLayer;
                exist.blueprintCopied = true;
                exist.bpBlank = false;
                exist.bpDone = false;
                exist.layerIndex = sampleLayer;
            }
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.already_studied",
                    sampleLayer + 1, blueprint.name()).withStyle(ChatFormatting.YELLOW), false);
            if (exist != null) send(player, network, exist);
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            if (!session.networkId.equals(network.id())) {
                // 先处理掉开在别的阵盘上的会话，不然上稿会挂错台。
                abandonEdit(session, player, false);
                SESSIONS.remove(player.getUUID());
                session = null;
            } else if (!session.editBlueprint.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.blueprint.edit_occupied")
                        .withStyle(ChatFormatting.YELLOW), false);
                return;
            }
        }
        int targetLayer = -1;
        for (int i = 0; i < Math.max(1, MagicCircleConfig.MAX_LAYERS.get()); i++) {
            if (network.layer(i).nodes().isEmpty() && !network.layer(i).isScratch()) {
                targetLayer = i;
                break;
            }
        }
        if (targetLayer < 0) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.canvas_full",
                    network.baseWidth(), network.baseHeight()).withStyle(ChatFormatting.RED), false);
            return;
        }
        MagicCircleNetwork.Layer layer = network.layer(targetLayer);
        int copied = MagicCircleProjectionEvents.copyIntoCanvas(blueprint, layer,
                network.canvasWidth(), network.canvasHeight());
        if (copied <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.nothing_to_copy")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        layer.setScratchOwner(player.getUUID());
        MagicCircleNetworks.markDirty(level);
        // 蓝图从手上收进台面槽位（关界面 / 打包都会原样退回，丢不了）。
        ItemStack taken = held.copyWithCount(1);
        takeIntoSlot(held);
        open(level, base, player);
        Session fresh = SESSIONS.get(player.getUUID());
        if (fresh == null) return;
        fresh.editBlueprint = taken;
        fresh.editBlueprintName = blueprint.name();
        fresh.editLayer = targetLayer;
        fresh.blueprintCopied = false;
        fresh.bpBlank = false;
        fresh.bpDone = false;
        fresh.layerIndex = targetLayer;
        MagicCircleProjectionEvents.firstStudyReward(player, blueprint);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.edit_mode",
                blueprint.name(), copied, targetLayer + 1).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.edit_mode_hint")
                .withStyle(ChatFormatting.GRAY), false);
        send(player, network, fresh);
    }

    /** 右下角「复制」：按草样层现有粉末清单扣一次背包，草样转正、样本留在台面。 */
    private static void blueprintCopy(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                                      Session session) {
        if (!requireEditable(player, session)) return;
        if (session.editBlueprint.isEmpty()) return;
        // r218：「复制留样」只属于上稿形态；空白载体 / 槽位成品没有留样一说（界面早灰掉了，这里防手滑）。
        if (session.bpBlank || session.bpDone) return;
        if (session.editLayer < 0 || !network.layer(session.editLayer).isScratch()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.already_copied")
                    .withStyle(ChatFormatting.YELLOW), false);
            session.blueprintCopied = true;
            send(player, network, session);
            return;
        }
        MagicCircleNetwork.Layer layer = network.layer(session.editLayer);
        if (layer.nodes().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.nothing_to_copy")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        List<String> powders = new ArrayList<>();
        for (MagicCircleNetwork.Node node : layer.nodes()) {
            powders.add(node.element());
            powders.add(node.semantic());
            powders.add(node.power());
            powders.add(node.dye());
        }
        if (!consume(player, powders)) return; // 缺粉会自己报「差多少」；创造模式自动免扣
        layer.setScratchOwner(null);
        session.blueprintCopied = true;
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.copied",
                powdersSize(powders), session.editLayer + 1)
                .withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.copied_hint")
                .withStyle(ChatFormatting.GRAY), false);
        send(player, network, session);
    }

    /** 右下角「打包带走」：蓝图退回背包；没复制过的草样整层抹掉——台面不留痕迹。 */
    private static void blueprintTake(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                                      Session session) {
        if (session.editBlueprint.isEmpty()) return;
        abandonEdit(session, player, true);
        send(player, network, session);
    }

    /**
     * r218：右下角槽位卡现在是<b>常驻位</b>——空手打开绘制台也能用。
     * 点空着的槽位 = 从背包收一张空白蓝图载体（手上优先，其次背包里第一张）放上台面，
     * 之后画完阵按「抄录成蓝图」直接写进这张载体，再按「打包带走」拿走。
     */
    private static void bpMount(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                                Session session) {
        if (!requireEditable(player, session)) return;
        if (!session.editBlueprint.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.edit_occupied")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        ItemStack source = findBlankCarrier(player);
        if (source.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.no_blank")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        // 两种模式口径一致：先拿走放上槽位（关界面 / 打包都会原样退回，不会变两张）。
        ItemStack mounted = source.copyWithCount(1);
        mounted.setCount(1);
        source.shrink(1);
        session.editBlueprint = mounted;
        session.editBlueprintName = "";
        session.editLayer = -1;
        session.blueprintCopied = false;
        session.bpBlank = true;
        session.bpDone = false;
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.mounted",
                mounted.getHoverName().getString()).withStyle(ChatFormatting.AQUA), false);
        send(player, network, session);
    }

    /**
     * 找一张<b>空白</b>蓝图载体：主手 → 副手 → 背包从头找。
     * 只认没写过蓝图数据的纸 / 魔力布匹 / 魔法绸缎；成品蓝图得走「手持右键基座」的上稿流程。
     */
    private static ItemStack findBlankCarrier(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isBlankCarrier(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isBlankCarrier(off)) return off;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isBlankCarrier(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** 是不是「没写过蓝图数据」的空白载体：纸 / 魔力布匹 / 魔法绸缎。 */
    private static boolean isBlankCarrier(ItemStack stack) {
        if (stack.isEmpty()) return false;
        MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(stack);
        return carrier != null && carrier != MagicCircleBlueprint.Carrier.WORN
                && MagicCircleBlueprint.of(stack) == null;
    }

    /**
     * 把手上的这份蓝图实际收进台面槽位。
     *
     * <p>以前这里写的是「创造模式不 shrink」，结果创造模式下手上那张还在、槽位里又挂了一张，
     * 关界面 / 打包带走时槽位那张再退回背包——一张蓝图变两张（用户报的复制 bug）。
     * 现在两种模式口径完全一致：<b>打开 = 从手上拿走放上台面，打包带走 / 关界面 = 原样退回</b>。</p>
     */
    private static void takeIntoSlot(ItemStack held) {
        // 创造模式也照收：这里扣的只是「手上这一张」，不是消耗，退台时会原样还回去。
        if (!held.isEmpty()) held.shrink(1);
    }

    /**
     * 结束一次上稿：蓝图退回玩家（关界面、打包、下线、换维度都走这里）；
     * 草样还没转正就连层带稿一起抹掉。
     */
    private static void abandonEdit(Session session, ServerPlayer player, boolean message) {
        if (session.editBlueprint.isEmpty()) return;
        ItemStack back = session.editBlueprint;
        session.editBlueprint = ItemStack.EMPTY;
        session.editBlueprintName = "";
        // r218：空白载体 / 槽位成品也走这条退路——原样还回背包，槽位卡回到空的。
        session.bpBlank = false;
        session.bpDone = false;
        int layerIndex = session.editLayer;
        session.editLayer = -1;
        session.blueprintCopied = false;
        if (!player.getInventory().add(back)) player.drop(back, false);
        MagicCircleNetwork network = MagicCircleNetworks.get(session.level).byId(session.networkId);
        if (network != null && layerIndex >= 0) {
            // 基座被挖了也走这条路：network 这时已经不存在，什么都不用抹。
            MagicCircleNetwork.Layer layer = network.layer(layerIndex);
            if (layer.isScratch() && player.getUUID().equals(layer.scratchOwner())) {
                layer.clear();
                layer.setScratchOwner(null);
                MagicCircleNetworks.markDirty(session.level);
            }
        }
        if (message) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.took_back", back.getHoverName().getString())
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /** 当前编辑的层是不是「自己这份蓝图的草稿层」——草稿期间放块 / 换粉一律免扣。 */
    private static boolean draftMode(Session session, MagicCircleNetwork network) {
        return !session.editBlueprint.isEmpty() && session.editLayer >= 0
                && session.layerIndex == session.editLayer
                && network.layer(session.editLayer).isScratch();
    }

    /** 别人（或自己没有上稿的会话）想动一张草样层：拦下。 */
    private static boolean scratchBlocked(Session session, MagicCircleNetwork network) {
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        return layer.isScratch() && !draftMode(session, network);
    }

    /**
     * 清掉「主人已经不在这台绘制台上」的孤儿草样层（宕机 / 强退留下的草稿）。
     * 草样没付过粉，直接抹掉即可。
     */
    public static void sweepOrphanScratch(ServerLevel level, MagicCircleNetwork network) {
        for (int i = 0; i < network.layers().size(); i++) {
            MagicCircleNetwork.Layer layer = network.layer(i);
            if (!layer.isScratch()) continue;
            Session holder = SESSIONS.get(layer.scratchOwner());
            if (holder != null && holder.networkId.equals(network.id()) && holder.editLayer == i) continue;
            layer.clear();
            layer.setScratchOwner(null);
            MagicCircleNetworks.markDirty(level);
        }
    }

    /**
     * 挖掉带绘制的绘制台基座：把所有<b>正式层</b>投进去的粉末原样爆出来（草样层没付粉，不爆）。
     * 玩家处于创造时什么都不掉——粉末本来就没从背包扣过。
     */
    public static void spillNetworkPowders(ServerLevel level, MagicCircleNetwork network,
                                           BlockPos at, Player breaker) {
        if (breaker != null && (breaker.isCreative() || breaker.isSpectator())) return;
        Map<String, Integer> drops = new LinkedHashMap<>();
        for (MagicCircleNetwork.Layer layer : network.layers()) {
            if (layer.isScratch()) continue;
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                for (String powder : new String[]{node.element(), node.semantic(), node.power(), node.dye()}) {
                    if (powder == null || powder.isEmpty()) continue;
                    drops.merge(powder, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> entry : drops.entrySet()) {
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(entry.getKey());
            if (powder == null) continue;
            ItemStack stack = powder.stack();
            if (stack.isEmpty()) continue;
            int remaining = entry.getValue();
            while (remaining > 0) {
                int amount = Math.min(remaining, stack.getMaxStackSize());
                remaining -= amount;
                ItemStack drop = stack.copyWithCount(amount);
                net.minecraft.world.entity.item.ItemEntity entity =
                        new net.minecraft.world.entity.item.ItemEntity(level,
                                at.getX() + 0.5 + level.random.nextGaussian() * 0.4,
                                at.getY() + 0.9, at.getZ() + 0.5 + level.random.nextGaussian() * 0.4, drop);
                entity.setPickUpDelay(20);
                level.addFreshEntity(entity);
            }
        }
        if (!drops.isEmpty() && breaker instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.base.spilled", drops.size())
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static int powdersSize(List<String> powders) {
        int count = 0;
        for (String powder : powders) if (powder != null && !powder.isEmpty()) count++;
        return count;
    }

    /** 玩家下线 / 被踢：会话直接收掉，蓝图退回背包、草稿层抹干净。 */
    public static void onPlayerLeft(net.minecraft.world.entity.player.Player player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null && player instanceof ServerPlayer serverPlayer) {
            abandonEdit(session, serverPlayer, false);
        }
    }

    /** 服务器关帐前把所有还开着的会话收干净：槽里的蓝图得跟着人存进档里。 */
    public static void flushAll() {
        for (java.util.Map.Entry<UUID, Session> entry : new java.util.HashMap<>(SESSIONS).entrySet()) {
            Session session = SESSIONS.remove(entry.getKey());
            if (session == null) continue;
            ServerPlayer player = session.level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) abandonEdit(session, player, false);
        }
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
            Session closing = SESSIONS.remove(player.getUUID());
            if (closing != null) abandonEdit(closing, player, false);
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!(player.level() instanceof ServerLevel level) || level != session.level) {
            Session stale = SESSIONS.remove(player.getUUID());
            if (stale != null) abandonEdit(stale, player, false);
            return;
        }
        MagicCircleNetwork network = MagicCircleNetworks.get(level).byId(session.networkId);
        if (network == null) {
            Session dangling = SESSIONS.remove(player.getUUID());
            if (dangling != null) abandonEdit(dangling, player, false);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.no_network").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!nearAnyBase(network, player)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.too_far").withStyle(ChatFormatting.RED), false);
            return;
        }
        // 别人正在「上稿修改」的草稿层，除主人外谁都别想动（浏览、换层不受限）。
        if (MUTATING.contains(action) && scratchBlocked(session, network)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.scratch_locked")
                    .withStyle(ChatFormatting.RED), false);
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
            case "bpCopy" -> blueprintCopy(level, player, network, session);
            case "bpTake" -> blueprintTake(level, player, network, session);
            case "bpMount" -> bpMount(level, player, network, session);
            case "layer" -> switchLayer(player, network, session, payload);
            case "copy" -> copy(player, network, session, payload);
            case "cut" -> cut(level, player, network, session, payload);
            case "deleteMany" -> deleteMany(level, player, network, session, payload);
            case "paste" -> paste(level, player, network, session, payload);
            case "edit" -> edit(level, player, network, session, payload);
            case "declare" -> declare(level, player, network, session, payload);
            case "gate" -> gate(level, player, network, session, payload);
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
        if (!draftMode(session, network) && !consumePowders(player, session)) return;

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
        if (!draftMode(session, network) && !consumePowders(player, session)) return;

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
        // 删一块 = 把它投过的粉退回来（草稿层没付过粉，不退）。
        List<String> refunds = draftMode(session, network)
                ? List.of() : refundNodes(player, List.of(layer.nodes().get(index)));
        pushUndo(session, layer, refunds);
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
        List<MagicCircleNetwork.Node> removed = new ArrayList<>();
        for (int index : indices) removed.add(layer.nodes().get(index));
        List<String> refunds = draftMode(session, network)
                ? List.of() : refundNodes(player, removed);
        pushUndo(session, layer, refunds);
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
        List<MagicCircleNetwork.Node> removed = new ArrayList<>();
        for (int index : indices) removed.add(layer.nodes().get(index));
        List<String> refunds = draftMode(session, network)
                ? List.of() : refundNodes(player, removed);
        pushUndo(session, layer, refunds);
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
        if (!draftMode(session, network) && !consume(player, needed)) {
            send(player, network, session);
            return;
        }

        pushUndo(session, layer);
        int base = layer.nodes().size();
        for (MagicCircleNetwork.Node node : clipboard.nodes()) {
            layer.add(new MagicCircleNetwork.Node(node.type(), node.x() + dx, node.y() + dy,
                    node.element(), node.semantic(), node.power(), node.dye(), node.variant(),
                    node.handDrawn(), node.declare(), node.gate()));
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
        boolean draft = draftMode(session, network);
        if (!draft && !consume(player, itemId.isEmpty() ? List.of() : List.of(itemId))) {
            send(player, network, session);
            return;
        }
        // 草稿层没付过粉，退回旧粉会白送；创造模式本来就「不耗粉」，也不往背包塞东西。
        if (!draft && !MagicCirclePowders.creativeFree(player)) refund(player, current);
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
        // 门控规范 v2.4 四 2：粉末一装上 / 改动，立刻发一句大白话解析，玩家不用猜。
        MagicCircleNetwork.Node after = layer.nodes().get(index);
        if (after.type() == MagicCircleBlockType.CONDITION
                && MagicCircleGates.isGateMode(after.semantic(), after.gate())) {
            player.displayClientMessage(Component.literal("[条件解析] " + MagicCircleGates.describe(after))
                    .withStyle(ChatFormatting.AQUA), false);
        } else if (after.type() == MagicCircleBlockType.TRIGGER) {
            String base = MagicCircleGraph.triggerBaseMode(layer, index) ? "触发解析] 魔力起点已就绪：判定交给上游门控条件，"
                    + MagicCircleGraph.baseGateSummary(layer, index) + "。"
                    : "触发解析] 当外部" + triggerMediumName(after) + "到来时，法阵启动。";
            player.displayClientMessage(Component.literal("[" + base).withStyle(ChatFormatting.AQUA), false);
        }
        send(player, network, session);
    }

    /** 触发节点语义粉对应的检测媒介大白话（解析句用）。 */
    private static String triggerMediumName(MagicCircleNetwork.Node node) {
        return switch (node.semantic()) {
            case "dimensional_mana:amethyst_dust" -> "魔素检测";
            case "minecraft:sugar" -> "生命检测";
            case "minecraft:glowstone_dust" -> "生物数量检测";
            case "minecraft:gunpowder" -> "爆炸检测";
            case "dimensional_mana:lapis_dust" -> "经验检测";
            case "minecraft:blaze_powder" -> "元素反应检测";
            default -> "红石信号";
        };
    }

    /**
     * v2.2 条件节点「声明模式」的参数写入：客户端面板每次改动发一串新的 {@code c1|…}，
     * 服务端<b>全部重新解析 + 夹一遍</b>再落盘——大类不符、越精度、超组数的条目就地夹回或删除，
     * 手改包最多只能得到「夹到合法范围内」的声明，改不出越权参数。
     */
    private static void declare(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                                Session session, MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int index = payload.x1();
        if (index < 0 || index >= layer.nodes().size()) return;
        MagicCircleNetwork.Node node = layer.nodes().get(index);
        if (node.type() != MagicCircleBlockType.CONDITION) return;
        if (MagicCircleGates.isGateMode(node.semantic(), node.gate())) {
            return; // 门控模式没有声明可写
        }
        List<MagicCircleDecl.Entry> entries = MagicCircleDecl.parse(payload.powder());
        // 只保留这份语义能声明的大类组，并按染料档 + 功率精度重夹参数。
        int tier = MagicCircleDecl.dyeTier(node.dye());
        var precision = MagicCircleDecl.precisionOf(node.power());
        List<MagicCircleDecl.Entry> kept = new ArrayList<>();
        for (MagicCircleDecl.Entry entry : entries) {
            if (!entry.group().allows(node.semantic())) continue;
            kept.add(entry.clampTo(tier, precision));
        }
        int capacity = MagicCircleDecl.groupCapacity(node.power());
        while (kept.size() > capacity) kept.remove(kept.size() - 1);
        String encoded = MagicCircleDecl.encode(kept);
        if (encoded.equals(node.declare())) {
            send(player, network, session);
            return;
        }
        pushUndo(session, layer);
        layer.setNode(index, node.withDeclare(encoded));
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.declare.applied", kept.size())
                .withStyle(ChatFormatting.GREEN), false);
        send(player, network, session);
    }

    /**
     * 门控规范 v2.4 的条件节点状态切换：客户端面板只发两种意图——
     * {@code "mode"}＝门控 ⇄ 声明，{@code "op"}＝⇌ 翻转比较符。
     *
     * <p><b>底层防坑</b>：翻转写回的是「比较符枚举」（LESS_THAN ↔ GREATER_THAN），
     * 数据层从头到尾没有一个「是否反转」布尔——不存在双重否定把逻辑绕晕的可能。
     * 声明模式下比较符位清掉；切回门控时旧声明作废（与 {@code withSlot} 的口径一致）。</p>
     */
    private static void gate(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                             Session session, MagicCircleActionPayload payload) {
        if (!requireEditable(player, session)) return;
        MagicCircleNetwork.Layer layer = network.layer(session.layerIndex);
        int index = payload.x1();
        if (index < 0 || index >= layer.nodes().size()) return;
        MagicCircleNetwork.Node node = layer.nodes().get(index);
        if (node.type() != MagicCircleBlockType.CONDITION) return;
        String intent = payload.powder() == null ? "" : payload.powder();
        MagicCircleNetwork.Node updated;
        String message;
        if (intent.equals("mode")) {
            String gate = MagicCircleGates.toggleMode(node.gate(), node.semantic());
            boolean toGate = gate.startsWith(MagicCircleGates.MODE_GATE);
            updated = node.withGate(gate).withDeclare(toGate ? "" : node.declare());
            message = toGate ? "条件节点切到门控模式：判定装语义粉，边界声明已清空"
                    : "条件节点切到声明模式：门控判定停用，回到边界声明编辑器";
        } else if (intent.equals("op")) {
            String gate = MagicCircleGates.toggleOperator(node.gate(), node.semantic());
            updated = node.withGate(gate);
            MagicCircleGates.CompareOperator op = MagicCircleGates.operatorOf(
                    MagicCircleGates.domainOf(node.semantic()), gate);
            message = "比较符已翻转为 " + op.symbol() + "（底层只存枚举，没有隐藏的反转标记）";
        } else {
            return;
        }
        if (updated.gate().equals(node.gate()) && updated.declare().equals(node.declare())) {
            send(player, network, session);
            return;
        }
        pushUndo(session, layer);
        layer.setNode(index, updated);
        MagicCircleNetworks.markDirty(level);
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GREEN), false);
        if (MagicCircleGates.isGateMode(updated.semantic(), updated.gate())) {
            player.displayClientMessage(Component.literal("[条件解析] " + MagicCircleGates.describe(updated))
                    .withStyle(ChatFormatting.AQUA), false);
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
                node.element(), node.semantic(), node.power(), node.dye(), node.variant(), node.handDrawn(),
                node.declare(), node.gate()));
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
        // r211：清空本层把投进去的粉末全部退回背包（草稿层没付过粉，不退；创造模式不退）。
        List<String> refunds = draftMode(session, network)
                ? List.of() : refundNodes(player, layer.nodes());
        pushUndo(session, layer, refunds);
        layer.clear();
        MagicCircleNetworks.markDirty(level);
        if (!refunds.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.clear.refunded", refunds.size())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        send(player, network, session);
    }

    private static void undo(ServerLevel level, ServerPlayer player, MagicCircleNetwork network, Session session) {
        if (!requireEditable(player, session)) return;
        Snapshot snapshot = session.undo.peekLast();
        if (snapshot == null) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.undo_empty").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        // 这一步当初退过粉：撤销 = 把那份粉再扣回来，扣不齐就不撤（不然删了退、退了撤成无限刷粉）。
        if (!snapshot.refunds().isEmpty() && !consume(player, snapshot.refunds())) {
            return;
        }
        session.undo.pollLast();
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
        // r218 路线 A：槽位里挂着点空槽位装进来的<b>空白载体</b>——直接写进槽位那张，
        // 不再伸手掏背包；成品留在槽位，按「打包带走」拿走。画布上的粉在落块时已经付过，
        // 抄录这一步不再重复计费（用户拍板的口径：不消耗额外的粉末打包带走）。
        if (!session.editBlueprint.isEmpty() && session.bpBlank) {
            transcribeIntoSlot(level, player, network, session, requestedName);
            return;
        }
        // 路线 B（原有玩法）：手里的载体抄一份带走。上稿模式下草样层的粉是<b>免扣</b>铺的，
        // 不付粉就能把白嫖的改动抄成新蓝图是个漏洞（用户报的 bug4）——这里先把草样层的
        // 粉末清单扣掉、草样转正，再写蓝图；缺粉整笔不抄。
        if (session.editLayer >= 0 && network.layer(session.editLayer).isScratch()
                && player.getUUID().equals(network.layer(session.editLayer).scratchOwner())) {
            List<String> powders = new ArrayList<>();
            for (MagicCircleNetwork.Node node : network.layer(session.editLayer).nodes()) {
                powders.add(node.element());
                powders.add(node.semantic());
                powders.add(node.power());
                powders.add(node.dye());
            }
            if (!consume(player, powders)) return;
            network.layer(session.editLayer).setScratchOwner(null);
            session.blueprintCopied = true;
            MagicCircleNetworks.markDirty(level);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.transcribed_paid",
                    powdersSize(powders)).withStyle(ChatFormatting.YELLOW), false);
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

    /** r218：把整张阵抄进<b>槽位里的空白载体</b>——成品挂回槽位，等玩家「打包带走」。 */
    private static void transcribeIntoSlot(ServerLevel level, ServerPlayer player, MagicCircleNetwork network,
                                           Session session, String requestedName) {
        MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(session.editBlueprint);
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
        written.setCount(1);
        session.editBlueprint = written;
        session.editBlueprintName = blueprint.name();
        session.bpBlank = false;
        session.bpDone = true;
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.transcribed_slot",
                blueprint.name(), carrier.fallbackName(), blueprint.nodeCount(), blueprint.filledLayerCount())
                .withStyle(ChatFormatting.GREEN), false);
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
        MagicCircleNetwork.Node swapped = new MagicCircleNetwork.Node(node.type(), node.x(), node.y(),
                element, semantic, power, dye, node.variant(), node.handDrawn(), node.declare(), node.gate());
        // v2.2 声明模式：条件节点的语义 / 染料 / 功率一动，声明串就得按新的「大类 + 档位 + 精度」重夹。
        // 门控规范 v2.4：模式判定改读 gate 串（显式 G/D 优先，空串退回旧推断）。
        if (node.type() == MagicCircleBlockType.CONDITION && !node.declare().isEmpty()) {
            if (MagicCircleGates.isGateMode(semantic, node.gate())) {
                swapped = swapped.withDeclare(""); // 退回门控模式：旧声明作废
            } else {
                List<MagicCircleDecl.Entry> kept = new ArrayList<>();
                int tier = MagicCircleDecl.dyeTier(dye);
                var precision = MagicCircleDecl.precisionOf(power);
                for (MagicCircleDecl.Entry entry : MagicCircleDecl.parse(node.declare())) {
                    if (!entry.group().allows(semantic)) continue; // 换了大类，不兼容的组直接掉
                    kept.add(entry.clampTo(tier, precision));
                }
                int capacity = MagicCircleDecl.groupCapacity(power);
                while (kept.size() > capacity) kept.remove(kept.size() - 1);
                swapped = swapped.withDeclare(MagicCircleDecl.encode(kept));
            }
        }
        return swapped;
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

    /** 带退粉清单的撤销快照：undo 这一步 = 把当初退掉的粉再扣回来。 */
    private static void pushUndo(Session session, MagicCircleNetwork.Layer layer, List<String> refunds) {
        session.undo.addLast(new Snapshot(List.copyOf(layer.nodes()), List.copyOf(layer.links()), refunds));
        while (session.undo.size() > MAX_UNDO) session.undo.pollFirst();
    }

    /**
     * 把被删功能块投过的粉逐份退回玩家背包，并回一份「退了哪些」的清单（撤销要照单收回）。
     * 创造模式粉末本来就没扣过，直接空手退。
     */
    private static List<String> refundNodes(ServerPlayer player, List<MagicCircleNetwork.Node> nodes) {
        if (MagicCirclePowders.creativeFree(player)) return List.of();
        List<String> refunded = new ArrayList<>();
        for (MagicCircleNetwork.Node node : nodes) {
            for (String powder : new String[]{node.element(), node.semantic(), node.power(), node.dye()}) {
                if (powder == null || powder.isEmpty()) continue;
                refund(player, powder);
                refunded.add(powder);
            }
        }
        return refunded;
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
                    node.element(), node.semantic(), node.power(), node.dye(), node.variant(),
                    node.declare(), node.gate()));
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
                List.copyOf(candidates), MagicCircleStroke.encode(session.stroke),
                session.editBlueprint.isEmpty() ? ""
                        : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(session.editBlueprint.getItem())),
                session.editBlueprintName, session.blueprintCopied,
                // r218：槽位卡形态——空 / 上稿 / 空白载体 / 已抄录成品。
                session.editBlueprint.isEmpty() ? ""
                        : session.bpDone ? "done" : session.bpBlank ? "blank" : "edit");
    }
}
