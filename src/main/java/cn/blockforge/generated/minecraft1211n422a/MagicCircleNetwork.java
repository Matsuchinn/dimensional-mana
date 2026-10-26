package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 一个已注册的「阵盘」：由若干绘制台基座拼成，最多存 4 层功能块图。
 *
 * <p>一个基座提供 16×16 的子画布，所以 2×2 个基座就是 32×32 像素。
 * 功能块坐标用的是整张画布的像素坐标，跨基座也能连成一条线。</p>
 */
public final class MagicCircleNetwork {
    /** 单个基座提供的子画布边长（像素）。 */
    public static final int TILE = 16;

    private final String id;
    private UUID owner;
    private final int baseWidth;
    private final int baseHeight;
    private final List<BlockPos> bases = new ArrayList<>();
    private final List<Layer> layers = new ArrayList<>();

    public MagicCircleNetwork(String id, UUID owner, int baseWidth, int baseHeight, List<BlockPos> bases) {
        this.id = id;
        this.owner = owner;
        this.baseWidth = Math.max(1, baseWidth);
        this.baseHeight = Math.max(1, baseHeight);
        this.bases.addAll(bases);
    }

    public String id() { return id; }
    public UUID owner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public int baseWidth() { return baseWidth; }
    public int baseHeight() { return baseHeight; }
    public int baseCount() { return baseWidth * baseHeight; }
    public int canvasWidth() { return baseWidth * TILE; }
    public int canvasHeight() { return baseHeight * TILE; }
    public List<BlockPos> bases() { return Collections.unmodifiableList(bases); }
    public List<Layer> layers() { return Collections.unmodifiableList(layers); }

    public Layer layer(int index) {
        while (layers.size() <= index) layers.add(new Layer());
        return layers.get(index);
    }

    /** 有内容的层数；空阵面按 1 层算，避免层数系数出现 0。 */
    public int filledLayerCount() {
        int count = 0;
        for (Layer layer : layers) if (!layer.nodes().isEmpty()) count++;
        return Math.max(1, count);
    }

    public int nodeCount() {
        int count = 0;
        for (Layer layer : layers) count += layer.nodes().size();
        return count;
    }

    public int linkCount() {
        int count = 0;
        for (Layer layer : layers) count += layer.links().size();
        return count;
    }

    /** 某个基座在阵面里的相对格子坐标（左上前方为 0,0）。 */
    public int[] tileOf(BlockPos pos) {
        if (bases.isEmpty()) return new int[]{0, 0};
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos base : bases) {
            minX = Math.min(minX, base.getX());
            minZ = Math.min(minZ, base.getZ());
        }
        return new int[]{pos.getX() - minX, pos.getZ() - minZ};
    }

    /** 一个功能块：图案识别后的产物，带最多四个粉末槽。 */
    public record Node(MagicCircleBlockType type, int x, int y,
                       String element, String semantic, String power, String dye,
                       String variant, boolean handDrawn) {
        public Node {
            element = element == null ? "" : element;
            semantic = semantic == null ? "" : semantic;
            power = power == null ? "" : power;
            dye = dye == null ? "" : dye;
            variant = variant == null ? "" : variant;
        }

        public Node(MagicCircleBlockType type, int x, int y, String element, String semantic, String power) {
            this(type, x, y, element, semantic, power, "", "", false);
        }

        public Node withHandDrawn(boolean handDrawn) {
            return new Node(type, x, y, element, semantic, power, dye, variant, handDrawn);
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("T", type == null ? "" : type.id());
            tag.putInt("X", x);
            tag.putInt("Y", y);
            tag.putString("E", element);
            tag.putString("S", semantic);
            tag.putString("P", power);
            tag.putString("D", dye);
            tag.putString("V", variant);
            tag.putBoolean("H", handDrawn);
            return tag;
        }

        static Node fromTag(CompoundTag tag) {
            MagicCircleBlockType type = MagicCircleBlockType.byId(tag.getString("T"));
            if (type == null) return null;
            return new Node(type, tag.getInt("X"), tag.getInt("Y"),
                    tag.getString("E"), tag.getString("S"), tag.getString("P"), tag.getString("D"),
                    tag.getString("V"), tag.getBoolean("H"));
        }
    }

    /** 功能块之间的连线：同一层里的两个功能块下标。 */
    public record Link(int from, int to) {
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("A", from);
            tag.putInt("B", to);
            return tag;
        }

        static Link fromTag(CompoundTag tag) {
            return new Link(tag.getInt("A"), tag.getInt("B"));
        }
    }

    /** 一层蓝图：功能块图 + 连线。 */
    public static final class Layer {
        private final List<Node> nodes = new ArrayList<>();
        private final List<Link> links = new ArrayList<>();

        public List<Node> nodes() { return Collections.unmodifiableList(nodes); }
        public List<Link> links() { return Collections.unmodifiableList(links); }

        public Node add(Node node) {
            nodes.add(node);
            return node;
        }

        /** 原地换掉一块（绘制台里「编辑粉末」用，连线下标不受影响）。 */
        public void setNode(int index, Node node) {
            if (index < 0 || index >= nodes.size()) return;
            nodes.set(index, node);
        }

        public void addLink(int from, int to) {
            if (from == to) return;
            for (Link link : links) {
                if ((link.from() == from && link.to() == to) || (link.from() == to && link.to() == from)) return;
            }
            links.add(new Link(from, to));
        }

        /**
         * 拆一条连线（绘制台里「按住 K 点一下线」走这里）。
         *
         * @return 真的拆掉了才返回 true；本来就没有这条也返回 false，让调用方决定要不要动撤销栈
         */
        public boolean removeLink(int from, int to) {
            return links.removeIf(link ->
                    (link.from() == from && link.to() == to) || (link.from() == to && link.to() == from));
        }

        public void clear() {
            nodes.clear();
            links.clear();
        }

        /**
         * 删掉一个功能块，并把剩下的连线索引往前挪——否则删掉第 1 块后，
         * 所有连线都会指向错误的块。这是界面里「右键擦掉一块」的核心。
         */
        public void removeNodeAt(int index) {
            if (index < 0 || index >= nodes.size()) return;
            nodes.remove(index);
            List<Link> kept = new ArrayList<>(links.size());
            for (Link link : links) {
                if (link.from() == index || link.to() == index) continue;
                kept.add(new Link(remap(link.from(), index), remap(link.to(), index)));
            }
            links.clear();
            links.addAll(kept);
        }

        private static int remap(int value, int removed) {
            return value > removed ? value - 1 : value;
        }

        /** 撤销用：整层恢复到某个快照。 */
        public void restore(List<Node> restoredNodes, List<Link> restoredLinks) {
            nodes.clear();
            nodes.addAll(restoredNodes);
            links.clear();
            links.addAll(restoredLinks);
        }

        /** 离某个格点最近的功能块下标，找不到返回 -1（界面上点一下/擦一下用）。 */
        public int nodeNear(int x, int y, int radius) {
            int best = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                int dx = Math.abs(node.x() - x);
                int dy = Math.abs(node.y() - y);
                if (dx > radius || dy > radius) continue;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            return best;
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            ListTag nodeList = new ListTag();
            for (Node node : nodes) nodeList.add(node.toTag());
            tag.put("N", nodeList);
            ListTag linkList = new ListTag();
            for (Link link : links) linkList.add(link.toTag());
            tag.put("L", linkList);
            return tag;
        }

        static Layer fromTag(CompoundTag tag) {
            Layer layer = new Layer();
            ListTag nodeList = tag.getList("N", Tag.TAG_COMPOUND);
            for (int i = 0; i < nodeList.size(); i++) {
                Node node = Node.fromTag(nodeList.getCompound(i));
                if (node != null) layer.nodes.add(node);
            }
            ListTag linkList = tag.getList("L", Tag.TAG_COMPOUND);
            for (int i = 0; i < linkList.size(); i++) layer.links.add(Link.fromTag(linkList.getCompound(i)));
            return layer;
        }
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putUUID("Owner", owner == null ? new UUID(0L, 0L) : owner);
        tag.putInt("BaseW", baseWidth);
        tag.putInt("BaseH", baseHeight);
        ListTag baseList = new ListTag();
        for (BlockPos base : bases) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", base.getX());
            entry.putInt("Y", base.getY());
            entry.putInt("Z", base.getZ());
            baseList.add(entry);
        }
        tag.put("Bases", baseList);
        ListTag layerList = new ListTag();
        for (Layer layer : layers) layerList.add(layer.toTag());
        tag.put("Layers", layerList);
        return tag;
    }

    static MagicCircleNetwork fromTag(CompoundTag tag) {
        UUID owner = tag.contains("Owner") ? tag.getUUID("Owner") : null;
        List<BlockPos> bases = new ArrayList<>();
        ListTag baseList = tag.getList("Bases", Tag.TAG_COMPOUND);
        for (int i = 0; i < baseList.size(); i++) {
            CompoundTag entry = baseList.getCompound(i);
            bases.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        MagicCircleNetwork network = new MagicCircleNetwork(tag.getString("Id"), owner,
                tag.getInt("BaseW"), tag.getInt("BaseH"), bases);
        ListTag layerList = tag.getList("Layers", Tag.TAG_COMPOUND);
        for (int i = 0; i < layerList.size(); i++) network.layers.add(Layer.fromTag(layerList.getCompound(i)));
        return network;
    }
}
