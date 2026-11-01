package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * 法阵的「渲染数据包」：服务端（或蓝图数据）在数据变化时算好一次，
 * 客户端渲染器<b>每帧只读它，不做任何遍历换算</b>——这是防卡顿的第一道闸门。
 *
 * <p>坐标全部存「画布比例 0~1」（毫单位 int，×1000），不存世界坐标：
 * 绘制台按基座个数还原成格，石板锚点按贴面朝向还原成方块局部坐标，
 * 一份包两边都能用。</p>
 *
 * <p>颜色是 0xAARRGGBB，来源 {@link MagicCircleBlueprint#colorOf}：
 * 有元素粉用元素色（火红水蓝奥术紫……），没粉末按功能块类型给色。</p>
 */
public final class MagicCircleRenderPacket {
    /** 单基座画布边长的倒数：16 像素 = 1 格，比例换算用。 */
    private static final float INV = 1.0F / MagicCircleNetwork.TILE;

    public static final MagicCircleRenderPacket EMPTY = new MagicCircleRenderPacket(2, 2,
            new float[0], new float[0], new int[0], new int[0], new int[0], new int[0], new int[0]);

    public final int baseW;
    public final int baseH;
    /** 节点：画布比例坐标（0~1）、颜色、图集格号、所属层号。 */
    public final float[] nodeFx;
    public final float[] nodeFz;
    public final int[] nodeColor;
    public final int[] nodeGlyph;
    public final int[] nodeLayer;
    /** 连线：两端是摊平后的节点下标。 */
    public final int[] linkFrom;
    public final int[] linkTo;

    private MagicCircleRenderPacket(int baseW, int baseH, float[] nodeFx, float[] nodeFz, int[] nodeColor,
                                    int[] nodeGlyph, int[] nodeLayer, int[] linkFrom, int[] linkTo) {
        this.baseW = baseW;
        this.baseH = baseH;
        this.nodeFx = nodeFx;
        this.nodeFz = nodeFz;
        this.nodeColor = nodeColor;
        this.nodeGlyph = nodeGlyph;
        this.nodeLayer = nodeLayer;
        this.linkFrom = linkFrom;
        this.linkTo = linkTo;
    }

    public boolean isEmpty() { return nodeFx.length == 0; }

    public int nodeCount() { return nodeFx.length; }

    // ------------------------------------------------------------------ 构建

    /** 功能块枚举序 → 图集格号（枚举顺序和美术排布不同，靠这张表桥接）。 */
    private static final int[] GLYPH_BY_TYPE = {0, 5, 1, 2, 6, 7, 4, 8, 3, 9, 10, 11, 12};

    public static int glyphOf(MagicCircleBlockType type) {
        if (type == null) return 0;
        int ordinal = type.ordinal();
        return ordinal >= 0 && ordinal < GLYPH_BY_TYPE.length ? GLYPH_BY_TYPE[ordinal] : 0;
    }

    /** 连线上限：一个层里节点全连线的情况极少，摊平后统一截断，防脏数据撑爆缓冲。 */
    private static final int MAX_LINKS = 512;

    /** 从「层列表」摊平出渲染包；绘制台阵盘与石板蓝图共用。 */
    public static MagicCircleRenderPacket build(List<MagicCircleNetwork.Layer> layers, int baseW, int baseH) {
        int totalNodes = 0;
        for (MagicCircleNetwork.Layer layer : layers) totalNodes += layer.nodes().size();
        float[] fx = new float[totalNodes];
        float[] fz = new float[totalNodes];
        int[] color = new int[totalNodes];
        int[] glyph = new int[totalNodes];
        int[] layerArr = new int[totalNodes];
        int[] from = new int[Math.min(MAX_LINKS, totalNodes * 4 + 16)];
        int[] to = new int[from.length];
        int nodeCursor = 0;
        int linkCursor = 0;
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            MagicCircleNetwork.Layer layer = layers.get(layerIndex);
            if (layer.nodes().isEmpty()) continue;
            int base = nodeCursor;
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                fx[nodeCursor] = node.x() * INV / Math.max(1, baseW);
                fz[nodeCursor] = node.y() * INV / Math.max(1, baseH);
                color[nodeCursor] = MagicCircleBlueprint.colorOf(node);
                glyph[nodeCursor] = glyphOf(node.type());
                layerArr[nodeCursor] = layerIndex;
                nodeCursor++;
            }
            for (MagicCircleNetwork.Link link : layer.links()) {
                int a = link.from(), b = link.to();
                if (a < 0 || b < 0 || a >= layer.nodes().size() || b >= layer.nodes().size()) continue;
                if (linkCursor < from.length) {
                    from[linkCursor] = base + a;
                    to[linkCursor] = base + b;
                    linkCursor++;
                }
            }
        }
        return new MagicCircleRenderPacket(Math.max(1, baseW), Math.max(1, baseH),
                fx, fz, color, glyph, layerArr,
                java.util.Arrays.copyOf(from, linkCursor), java.util.Arrays.copyOf(to, linkCursor));
    }

    /** 结构指纹：服务端每 tick 先算这个，没变就不重发渲染包（省带宽也省客户端重排）。 */
    public long signature() {
        long hash = 1469598103934665603L; // FNV-1a 64
        hash = mix(hash, baseW);
        hash = mix(hash, baseH);
        for (int i = 0; i < nodeFx.length; i++) {
            hash = mix(hash, Float.floatToIntBits(nodeFx[i]));
            hash = mix(hash, Float.floatToIntBits(nodeFz[i]));
            hash = mix(hash, nodeColor[i]);
            hash = mix(hash, nodeGlyph[i]);
            hash = mix(hash, nodeLayer[i]);
        }
        for (int i = 0; i < linkFrom.length; i++) {
            hash = mix(hash, linkFrom[i]);
            hash = mix(hash, linkTo[i]);
        }
        return hash;
    }

    private static long mix(long hash, int value) {
        return (hash ^ (value & 0xFFFFFFFFL)) * 1099511628211L;
    }

    // ------------------------------------------------------------------ NBT 编解码
    // 说明：数组都用 IntArray；坐标 ×1000 存毫格，读回即得浮点，零依赖零解析成本。

    private static final String TAG_W = "W", TAG_H = "H";
    private static final String TAG_FX = "FX", TAG_FZ = "FZ", TAG_COL = "CO", TAG_GLY = "GL", TAG_LAY = "LA";
    private static final String TAG_FROM = "FA", TAG_TO = "TA";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_W, baseW);
        tag.putInt(TAG_H, baseH);
        tag.putIntArray(TAG_FX, scaleToInts(nodeFx));
        tag.putIntArray(TAG_FZ, scaleToInts(nodeFz));
        tag.putIntArray(TAG_COL, nodeColor);
        tag.putIntArray(TAG_GLY, nodeGlyph);
        tag.putIntArray(TAG_LAY, nodeLayer);
        tag.putIntArray(TAG_FROM, linkFrom);
        tag.putIntArray(TAG_TO, linkTo);
        return tag;
    }

    public static MagicCircleRenderPacket fromTag(Tag raw) {
        if (!(raw instanceof CompoundTag tag)) return EMPTY;
        int w = Math.max(1, tag.getInt(TAG_W));
        int h = Math.max(1, tag.getInt(TAG_H));
        int[] fx = tag.getIntArray(TAG_FX);
        int[] fz = tag.getIntArray(TAG_FZ);
        int[] col = tag.getIntArray(TAG_COL);
        int[] gly = tag.getIntArray(TAG_GLY);
        int[] lay = tag.getIntArray(TAG_LAY);
        int n = Math.min(fx.length, Math.min(fz.length, Math.min(col.length, Math.min(gly.length, lay.length))));
        float[] fxA = new float[n];
        float[] fzA = new float[n];
        for (int i = 0; i < n; i++) {
            fxA[i] = fx[i] / 1000.0F;
            fzA[i] = fz[i] / 1000.0F;
        }
        int[] from = tag.getIntArray(TAG_FROM);
        int[] to = tag.getIntArray(TAG_TO);
        int m = Math.min(from.length, to.length);
        return new MagicCircleRenderPacket(w, h, fxA, fzA,
                java.util.Arrays.copyOf(col, n), java.util.Arrays.copyOf(gly, n),
                java.util.Arrays.copyOf(lay, n), java.util.Arrays.copyOf(from, m),
                java.util.Arrays.copyOf(to, m));
    }

    private static int[] scaleToInts(float[] values) {
        int[] out = new int[values.length];
        for (int i = 0; i < values.length; i++) out[i] = Math.round(values[i] * 1000.0F);
        return out;
    }
}
