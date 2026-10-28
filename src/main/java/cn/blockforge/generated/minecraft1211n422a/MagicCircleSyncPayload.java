package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 绘制台界面的一次完整状态同步：阵盘尺寸、已有功能块与连线、消耗预览、
 * 粉末解锁状态、当前四个粉槽的选择、识别候选。
 *
 * <p>服务端是权威：界面每次改动（提交笔画、确认候选、框选、删除、撤销、复制粘贴）都会
 * 回一份这个包，客户端只负责画。<code>clipboardSize</code> 是服务端内部剪贴板里记住了几块，
 * 界面用它决定「粘贴」能不能点。</p>
 */
public record MagicCircleSyncPayload(
        String networkId,
        int baseW, int baseH,
        int canvasW, int canvasH,
        int layer,
        boolean editable,
        boolean freePowder,
        int clipboardSize,
        String ownerName,
        int magicLevel, int unlockedPowders, int totalPowders,
        List<NodeView> nodes,
        List<LinkView> links,
        int startMana, int upkeepMana, int triggerMana,
        double strength, int complexity, int disorder, double efficiency,
        List<String> problems,
        List<PowderView> powders,
        List<SlotView> slots,
        List<CandidateView> candidates,
        String strokeEcho,
        /** r211 蓝图修改模式：台面右下角槽位里那份蓝图（物品 id / 显示名 / 是否已复制留样）。 */
        String blueprintItem,
        String blueprintName,
        boolean blueprintCopied,
        /**
         * r218 槽位卡的形态：{@code ""}=槽位空着；{@code "edit"}=上稿修改（原有的玩法）；
         * {@code "blank"}=玩家点空槽位装进来的空白载体；{@code "done"}=已抄录进槽位的成品蓝图。
         */
        String blueprintKind) implements CustomPacketPayload {

    /** 没有上稿时的老十六~二十四参构造：蓝图槽位为空。 */
    public MagicCircleSyncPayload(String networkId, int baseW, int baseH, int canvasW, int canvasH,
                                  int layer, boolean editable, boolean freePowder, int clipboardSize,
                                  String ownerName, int magicLevel, int unlockedPowders, int totalPowders,
                                  List<NodeView> nodes, List<LinkView> links, int startMana, int upkeepMana,
                                  int triggerMana, double strength, int complexity, int disorder, double efficiency,
                                  List<String> problems, List<PowderView> powders, List<SlotView> slots,
                                  List<CandidateView> candidates, String strokeEcho) {
        this(networkId, baseW, baseH, canvasW, canvasH, layer, editable, freePowder, clipboardSize, ownerName,
                magicLevel, unlockedPowders, totalPowders, nodes, links, startMana, upkeepMana, triggerMana,
                strength, complexity, disorder, efficiency, problems, powders, slots, candidates, strokeEcho,
                "", "", false, "");
    }

    /**
     * 画布上的一个功能块。{@code variant} 里手动框选的块存着 "frame:x1:y1:x2:y2"；
     * {@code declare} 是条件节点（声明模式）的边界组参数串，别的块恒为空；
     * {@code gate} 是门控规范 v2.4 的模式 + 比较符两字符串（{@link MagicCircleGates}），别的块恒为空。
     */
    public record NodeView(String type, int x, int y, boolean handDrawn,
                           String element, String semantic, String power, String dye, String variant,
                           String declare, String gate) {
        /** 老九参签名：不带声明串的调用点照旧可用。 */
        public NodeView(String type, int x, int y, boolean handDrawn,
                        String element, String semantic, String power, String dye, String variant) {
            this(type, x, y, handDrawn, element, semantic, power, dye, variant, "", "");
        }

        /** r208~r216 的十参签名：门控串按空走旧规则推断。 */
        public NodeView(String type, int x, int y, boolean handDrawn,
                        String element, String semantic, String power, String dye, String variant,
                        String declare) {
            this(type, x, y, handDrawn, element, semantic, power, dye, variant, declare, "");
        }

        public NodeView {
            gate = gate == null ? "" : gate;
        }
    }

    /** 功能块之间的连线，用 nodes 的下标表示。 */
    public record LinkView(int from, int to) {}

    /** 一种粉末在「这个玩家」身上的解锁状态。 */
    public record PowderView(String itemId, boolean unlocked, int requiredLevel) {}

    /** 四个粉槽当前选的是什么。 */
    public record SlotView(String slot, String itemId) {}

    /** 一个识别候选。 */
    public record CandidateView(String glyph, double score) {}

    public static final Type<MagicCircleSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "magic_circle_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagicCircleSyncPayload> STREAM_CODEC = StreamCodec.of(
            MagicCircleSyncPayload::write,
            MagicCircleSyncPayload::read
    );

    private static void write(RegistryFriendlyByteBuf buf, MagicCircleSyncPayload payload) {
        buf.writeUtf(payload.networkId(), 64);
        buf.writeVarInt(payload.baseW());
        buf.writeVarInt(payload.baseH());
        buf.writeVarInt(payload.canvasW());
        buf.writeVarInt(payload.canvasH());
        buf.writeVarInt(payload.layer());
        buf.writeBoolean(payload.editable());
        buf.writeBoolean(payload.freePowder());
        buf.writeVarInt(payload.clipboardSize());
        buf.writeUtf(payload.ownerName(), 64);
        buf.writeVarInt(payload.magicLevel());
        buf.writeVarInt(payload.unlockedPowders());
        buf.writeVarInt(payload.totalPowders());
        buf.writeVarInt(payload.nodes().size());
        for (NodeView node : payload.nodes()) {
            buf.writeUtf(node.type(), 32);
            buf.writeVarInt(node.x());
            buf.writeVarInt(node.y());
            buf.writeBoolean(node.handDrawn());
            buf.writeUtf(node.element(), 128);
            buf.writeUtf(node.semantic(), 128);
            buf.writeUtf(node.power(), 128);
            buf.writeUtf(node.dye(), 128);
            buf.writeUtf(node.variant(), 64);
            buf.writeUtf(node.declare(), 200);
            buf.writeUtf(node.gate(), 4);
        }
        buf.writeVarInt(payload.links().size());
        for (LinkView link : payload.links()) {
            buf.writeVarInt(link.from());
            buf.writeVarInt(link.to());
        }
        buf.writeVarInt(payload.startMana());
        buf.writeVarInt(payload.upkeepMana());
        buf.writeVarInt(payload.triggerMana());
        buf.writeDouble(payload.strength());
        buf.writeVarInt(payload.complexity());
        buf.writeVarInt(payload.disorder());
        buf.writeDouble(payload.efficiency());
        buf.writeVarInt(payload.problems().size());
        for (String problem : payload.problems()) buf.writeUtf(problem, 512);
        buf.writeVarInt(payload.powders().size());
        for (PowderView powder : payload.powders()) {
            buf.writeUtf(powder.itemId(), 128);
            buf.writeBoolean(powder.unlocked());
            buf.writeVarInt(powder.requiredLevel());
        }
        buf.writeVarInt(payload.slots().size());
        for (SlotView slot : payload.slots()) {
            buf.writeUtf(slot.slot(), 32);
            buf.writeUtf(slot.itemId(), 128);
        }
        buf.writeVarInt(payload.candidates().size());
        for (CandidateView candidate : payload.candidates()) {
            buf.writeUtf(candidate.glyph(), 32);
            buf.writeDouble(candidate.score());
        }
        buf.writeUtf(payload.strokeEcho(), 8192);
        buf.writeUtf(payload.blueprintItem(), 256);
        buf.writeUtf(payload.blueprintName(), 128);
        buf.writeBoolean(payload.blueprintCopied());
        buf.writeUtf(payload.blueprintKind(), 8);
    }

    private static MagicCircleSyncPayload read(RegistryFriendlyByteBuf buf) {
        String networkId = buf.readUtf(64);
        int baseW = buf.readVarInt();
        int baseH = buf.readVarInt();
        int canvasW = buf.readVarInt();
        int canvasH = buf.readVarInt();
        int layer = buf.readVarInt();
        boolean editable = buf.readBoolean();
        boolean freePowder = buf.readBoolean();
        int clipboardSize = buf.readVarInt();
        String ownerName = buf.readUtf(64);
        int magicLevel = buf.readVarInt();
        int unlockedPowders = buf.readVarInt();
        int totalPowders = buf.readVarInt();

        int nodeCount = Math.max(0, Math.min(4096, buf.readVarInt()));
        List<NodeView> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(new NodeView(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readUtf(128), buf.readUtf(128), buf.readUtf(128), buf.readUtf(128), buf.readUtf(64),
                    buf.readUtf(200), buf.readUtf(4)));
        }
        int linkCount = Math.max(0, Math.min(4096, buf.readVarInt()));
        List<LinkView> links = new ArrayList<>(linkCount);
        for (int i = 0; i < linkCount; i++) links.add(new LinkView(buf.readVarInt(), buf.readVarInt()));

        int startMana = buf.readVarInt();
        int upkeepMana = buf.readVarInt();
        int triggerMana = buf.readVarInt();
        double strength = buf.readDouble();
        int complexity = buf.readVarInt();
        int disorder = buf.readVarInt();
        double efficiency = buf.readDouble();

        int problemCount = Math.max(0, Math.min(64, buf.readVarInt()));
        List<String> problems = new ArrayList<>(problemCount);
        for (int i = 0; i < problemCount; i++) problems.add(buf.readUtf(512));

        int powderCount = Math.max(0, Math.min(256, buf.readVarInt()));
        List<PowderView> powders = new ArrayList<>(powderCount);
        for (int i = 0; i < powderCount; i++) {
            powders.add(new PowderView(buf.readUtf(128), buf.readBoolean(), buf.readVarInt()));
        }
        int slotCount = Math.max(0, Math.min(16, buf.readVarInt()));
        List<SlotView> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) slots.add(new SlotView(buf.readUtf(32), buf.readUtf(128)));
        int candidateCount = Math.max(0, Math.min(8, buf.readVarInt()));
        List<CandidateView> candidates = new ArrayList<>(candidateCount);
        for (int i = 0; i < candidateCount; i++) candidates.add(new CandidateView(buf.readUtf(32), buf.readDouble()));
        String strokeEcho = buf.readUtf(8192);
        String blueprintItem = buf.readUtf(256);
        String blueprintName = buf.readUtf(128);
        boolean blueprintCopied = buf.readBoolean();
        String blueprintKind = buf.readUtf(8);

        return new MagicCircleSyncPayload(networkId, baseW, baseH, canvasW, canvasH, layer, editable, freePowder,
                clipboardSize, ownerName,
                magicLevel, unlockedPowders, totalPowders, nodes, links, startMana, upkeepMana, triggerMana,
                strength, complexity, disorder, efficiency, problems, powders, slots, candidates, strokeEcho,
                blueprintItem, blueprintName, blueprintCopied, blueprintKind);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientMagicCirclePayloadHandler.openOrUpdate(payload);
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
