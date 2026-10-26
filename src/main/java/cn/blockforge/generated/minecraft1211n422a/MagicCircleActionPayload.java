package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 绘制台界面发给服务端的动作。所有写操作都在服务端复核（画笔、粉末、权限、上限）。
 *
 * <p>字段全部固定写满，方便以后加动作时不用改包格式。{@code indices} 是「选中了第几块」
 * 的逗号分隔列表（选择模式的多选、复制 / 剪切 / 批量删除都用它），服务端只认自己层里
 * 真实存在的下标，越界的直接扔掉。</p>
 */
public record MagicCircleActionPayload(
        String action,
        String stroke,
        String glyph,
        String blockType,
        String slot,
        String powder,
        String indices,
        String name,
        int x1, int y1, int x2, int y2) implements CustomPacketPayload {

    public static final Type<MagicCircleActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "magic_circle_action"));
    /** 选中下标列表的编码上限：128 块 × 5 位数字都够放。 */
    public static final int MAX_INDICES_LENGTH = 1024;
    /** 抄录时给蓝图起的名字上限（字符数）；服务端还会再清洗一遍。 */
    public static final int MAX_NAME_LENGTH = 64;

    /** 不带名字的老签名：除「抄录」以外的动作都不关心名字。 */
    public MagicCircleActionPayload(String action, String stroke, String glyph, String blockType, String slot,
                                    String powder, String indices, int x1, int y1, int x2, int y2) {
        this(action, stroke, glyph, blockType, slot, powder, indices, "", x1, y1, x2, y2);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MagicCircleActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.action(), 32);
                buf.writeUtf(payload.stroke(), MagicCircleStroke.MAX_ENCODED_LENGTH);
                buf.writeUtf(payload.glyph(), 32);
                buf.writeUtf(payload.blockType(), 32);
                buf.writeUtf(payload.slot(), 16);
                buf.writeUtf(payload.powder(), 128);
                buf.writeUtf(payload.indices(), MAX_INDICES_LENGTH);
                buf.writeUtf(payload.name(), MAX_NAME_LENGTH);
                buf.writeVarInt(payload.x1());
                buf.writeVarInt(payload.y1());
                buf.writeVarInt(payload.x2());
                buf.writeVarInt(payload.y2());
            },
            buf -> new MagicCircleActionPayload(buf.readUtf(32), buf.readUtf(MagicCircleStroke.MAX_ENCODED_LENGTH),
                    buf.readUtf(32), buf.readUtf(32), buf.readUtf(16), buf.readUtf(128), buf.readUtf(MAX_INDICES_LENGTH),
                    buf.readUtf(MAX_NAME_LENGTH), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
    );

    public static MagicCircleActionPayload close() {
        return new MagicCircleActionPayload("close", "", "", "", "", "", "", 0, 0, 0, 0);
    }

    public static MagicCircleActionPayload submit(String stroke) {
        return new MagicCircleActionPayload("submit", stroke == null ? "" : stroke, "", "", "", "", "", 0, 0, 0, 0);
    }

    public static MagicCircleActionPayload cancel() {
        return new MagicCircleActionPayload("cancel", "", "", "", "", "", "", 0, 0, 0, 0);
    }

    public static MagicCircleActionPayload confirm(String glyphId) {
        return new MagicCircleActionPayload("confirm", "", glyphId == null ? "" : glyphId, "", "", "", "",
                0, 0, 0, 0);
    }

    public static MagicCircleActionPayload manual(int x1, int y1, int x2, int y2, String blockType) {
        return new MagicCircleActionPayload("manual", "", "", blockType == null ? "" : blockType, "", "", "",
                x1, y1, x2, y2);
    }

    public static MagicCircleActionPayload delete(int index) {
        return new MagicCircleActionPayload("delete", "", "", "", "", "", "", index, 0, 0, 0);
    }

    public static MagicCircleActionPayload clear() {
        return new MagicCircleActionPayload("clear", "", "", "", "", "", "", 0, 0, 0, 0);
    }

    public static MagicCircleActionPayload undo() {
        return new MagicCircleActionPayload("undo", "", "", "", "", "", "", 0, 0, 0, 0);
    }

    /**
     * 把当前画布抄成蓝图：扣掉手里的载体（纸 / 布 / 绸），得到一份带数据的蓝图物品。
     *
     * @param name 玩家在抄录弹框里输入的法阵名；留空表示「用默认名」，由服务端按元素自动起名
     */
    public static MagicCircleActionPayload transcribe(String name) {
        return new MagicCircleActionPayload("transcribe", "", "", "", "", "", "",
                name == null ? "" : name, 0, 0, 0, 0);
    }

    /** 切换绘制台正在编辑的层：delta = +1 / -1，服务端负责夹在合法范围内。 */
    public static MagicCircleActionPayload layer(int delta) {
        return new MagicCircleActionPayload("layer", "", "", "", "", "", "", delta, 0, 0, 0);
    }

    public static MagicCircleActionPayload select(String slot, String itemId) {
        return new MagicCircleActionPayload("select", "", "", "", slot == null ? "" : slot,
                itemId == null ? "" : itemId, "", 0, 0, 0, 0);
    }

    /** 复制选中的功能块到服务端的内部剪贴板（不花粉末、不改画布）。 */
    public static MagicCircleActionPayload copy(String indices) {
        return new MagicCircleActionPayload("copy", "", "", "", "", "", safeIndices(indices), 0, 0, 0, 0);
    }

    /** 剪切：复制进剪贴板，然后把选中的块从本层删掉。 */
    public static MagicCircleActionPayload cut(String indices) {
        return new MagicCircleActionPayload("cut", "", "", "", "", "", safeIndices(indices), 0, 0, 0, 0);
    }

    /** 批量删除选中的块（一次撤销能全部还原）。 */
    public static MagicCircleActionPayload deleteMany(String indices) {
        return new MagicCircleActionPayload("deleteMany", "", "", "", "", "", safeIndices(indices), 0, 0, 0, 0);
    }

    /**
     * 把两块功能块连起来（选择模式下选中两块按 L）。
     *
     * @param from 起点下标（本层功能块下标，服务端越界直接丢弃）
     * @param to   终点下标
     */
    public static MagicCircleActionPayload link(int from, int to) {
        return new MagicCircleActionPayload("link", "", "", "", "", "", "", from, to, 0, 0);
    }

    /** 拆掉两块之间的连线（按住 K 点击那条线）。方向无所谓，服务端两头都认。 */
    public static MagicCircleActionPayload unlink(int from, int to) {
        return new MagicCircleActionPayload("unlink", "", "", "", "", "", "", from, to, 0, 0);
    }

    /**
     * 按选择顺序把一串功能块串成一条链（选择模式下选中三块以上按 L）。
     *
     * <p>{@code indices} 就是玩家点的先后顺序，服务端依次连「第 1 个 → 第 2 个 → 第 3 个 …」，
     * 整条链只记一条撤销，Ctrl+Z 一按全撤。</p>
     */
    public static MagicCircleActionPayload linkChain(String indices) {
        return new MagicCircleActionPayload("linkChain", "", "", "", "", "", safeIndices(indices), "",
                0, 0, 0, 0);
    }

    /**
     * 拖动一块功能块到画布上的新位置（选择模式下双击一块再拖）。
     *
     * @param index 功能块下标
     * @param x     新的画布 x（0 ~ canvasWidth-1，服务端会夹一遍）
     * @param y     新的画布 y
     */
    public static MagicCircleActionPayload move(int index, int x, int y) {
        // 四个 int 的槽位是 (x1, y1, x2, y2)，服务端读的是 x1 = 块下标、y1 = 画布 x、y2 = 画布 y。
        // 以前这里把 y 塞进了 x2、y2 写死 0：服务端只认 y2，夹一下就成了第 0 行，
        // 于是每挪一次块都「飞」到画布最上面那条边——x 还在，y 全没了。x2 恒为 0。
        return new MagicCircleActionPayload("move", "", "", "", "", "", "", "", index, x, 0, y);
    }

    /** 把剪贴板粘到 (x, y)；传 -1 就让服务端放在画布中央。 */
    public static MagicCircleActionPayload paste(int x, int y) {
        return new MagicCircleActionPayload("paste", "", "", "", "", "", "", x, y, 0, 0);
    }

    /**
     * 改一块功能块上某个粉槽用的粉：{@code powder} 留空 = 把这个粉拿下来。
     * 换下来的粉退回背包，新装上的粉从背包扣。
     */
    public static MagicCircleActionPayload edit(int index, String slot, String powder) {
        return new MagicCircleActionPayload("edit", "", "", "", slot == null ? "" : slot,
                powder == null ? "" : powder, "", index, 0, 0, 0);
    }

    private static String safeIndices(String indices) {
        if (indices == null) return "";
        return indices.length() > MAX_INDICES_LENGTH ? indices.substring(0, MAX_INDICES_LENGTH) : indices;
    }

    /** 把界面里的选中集合编成 "3,5,9" 这样的串；顺序不重要，服务端会去重排序。 */
    public static String encodeIndices(Iterable<Integer> indices) {
        StringBuilder builder = new StringBuilder();
        for (int index : indices) {
            if (index < 0) continue;
            if (builder.length() > 0) builder.append(',');
            builder.append(index);
        }
        return builder.toString();
    }

    /** 解码选中下标；非法片段直接忽略，最多收 256 个（远大于功能块上限）。 */
    public static int[] decodeIndices(String raw) {
        if (raw == null || raw.isEmpty()) return new int[0];
        String[] parts = raw.split(",");
        int[] out = new int[Math.min(parts.length, 256)];
        int count = 0;
        for (String part : parts) {
            if (count >= out.length) break;
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 0) out[count++] = value;
            } catch (NumberFormatException ignored) {
                // 手改包或旧版本残留，跳过即可
            }
        }
        return java.util.Arrays.copyOf(out, count);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // 第 2 版：抄录动作多了「法阵名」字段，旧客户端会被拒连而不是解包出错。
        event.registrar("2").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) MagicCircleDrawing.handle(player, payload);
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
