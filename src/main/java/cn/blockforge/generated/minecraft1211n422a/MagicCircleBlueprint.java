package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一份「蓝图」的纯数据：画布尺寸 + 若干层功能块图 + 作者 / 名字。
 *
 * <p>对应设计文档第 8、9 节。蓝图不绑定具体阵盘，所以既能贴在方块表面变成
 * {@link MagicCircleProjectionBlock 投影锚点}，也能存进物品（纸 / 魔力布匹 / 魔法绸缎 /
 * 残破的蓝图），还能直接被 {@link MagicCircleCost} 算出一模一样的消耗。</p>
 *
 * <p>物品上的蓝图数据放在 {@code minecraft:custom_data} 的 {@code MagicCircleBlueprint}
 * 子标签里，不额外注册数据组件；这样纸、魔力布匹、魔法绸缎这些「已有物品」也能直接承载蓝图，
 * 不用再复制出三套新物品。</p>
 */
public final class MagicCircleBlueprint {
    /** 物品 NBT 里的子标签名。 */
    public static final String DATA_KEY = "MagicCircleBlueprint";
    private static final int VERSION = 1;
    /** 防脏数据的硬上限：单边 64 个基座、单层 512 块、8 层，和配置上限同量级。 */
    private static final int MAX_SIDE_BASES = 64;
    private static final int MAX_NODES_PER_LAYER = 512;
    private static final int MAX_LAYERS = 8;

    /** 一个功能块的配色，和绘制台界面里的方块颜色一致，投影上也用同一套。 */
    private static final int[] TYPE_COLORS = {
            0xFFFFD86B, 0xFF9BE8FF, 0xFFFF9B6B, 0xFFFF6B8B, 0xFF8FE2A0,
            0xFFB4A0FF, 0xFFE0B4FF, 0xFF9BB8FF, 0xFFFFA0D0, 0xFF9BFFCE,
            0xFFA8FF9B, 0xFFFFE49B, 0xFF9BD0FF
    };

    /**
     * 载体：决定「能装多少块、能叠几层、能不能贴到方块上」。
     *
     * <p>纸 = 草稿（16 块 / 1 层，不能放），魔力布匹 = 单层蓝图（32 块），
     * 魔法绸缎 = 多层蓝图（64 块 / 3 层），残破的蓝图是掉落 / 交易来的成品，按多层算。</p>
     */
    public enum Carrier {
        PAPER("paper", 16, 1, false, "纸"),
        CLOTH("cloth", 32, 1, true, "魔力布匹"),
        SILK("silk", 64, 3, true, "魔法绸缎"),
        WORN("worn", 64, 3, true, "残破的蓝图");

        private final String id;
        private final int maxNodes;
        private final int maxLayers;
        private final boolean placeable;
        private final String fallbackName;

        Carrier(String id, int maxNodes, int maxLayers, boolean placeable, String fallbackName) {
            this.id = id;
            this.maxNodes = maxNodes;
            this.maxLayers = maxLayers;
            this.placeable = placeable;
            this.fallbackName = fallbackName;
        }

        public String id() { return id; }
        public int maxNodes() { return maxNodes; }
        public int maxLayers() { return maxLayers; }
        public boolean placeable() { return placeable; }
        public String fallbackName() { return fallbackName; }
        public String translationKey() { return "gui.dimensional_mana.magiccircle.carrier." + id; }

        /** 这种载体对应的物品；残破蓝图走自己的物品 id，所以这里返回空。 */
        public ItemStack stack() {
            return switch (this) {
                case PAPER -> new ItemStack(Items.PAPER);
                case CLOTH -> itemById(MagicEquipment.MAGIC_CLOTH);
                case SILK -> itemById(MagicEquipment.MAGIC_SILK);
                case WORN -> ItemStack.EMPTY;
            };
        }

        private static ItemStack itemById(String id) {
            var holder = GeneratedMod.MOD_ITEMS.get(id);
            return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get());
        }

        public static Carrier byId(String id) {
            for (Carrier carrier : values()) if (carrier.id.equals(id)) return carrier;
            return null;
        }
    }

    /** 一格功能块的位置与粉末，直接复用 {@link MagicCircleNetwork.Node}，保证两边算法一致。 */
    private final String id;
    private final String name;
    private final String author;
    private final int baseW;
    private final int baseH;
    private final Carrier carrier;
    private final List<MagicCircleNetwork.Layer> layers;

    public MagicCircleBlueprint(String id, String name, String author, int baseW, int baseH, Carrier carrier,
                                List<MagicCircleNetwork.Layer> layers) {
        this.id = id == null || id.isEmpty() ? "custom" : id;
        this.name = name == null || name.isBlank() ? "无名法阵" : name;
        this.author = author == null ? "" : author;
        this.baseW = clamp(baseW);
        this.baseH = clamp(baseH);
        this.carrier = carrier == null ? Carrier.PAPER : carrier;
        this.layers = new ArrayList<>();
        for (MagicCircleNetwork.Layer layer : layers) {
            if (this.layers.size() >= MAX_LAYERS) break;
            this.layers.add(copyLayer(layer, canvasWidth(), canvasHeight()));
        }
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(MAX_SIDE_BASES, value));
    }

    /** 复制一层，顺便把坐标夹回画布内，避免坏 NBT 把渲染 / 界面画到屏幕外。 */
    private static MagicCircleNetwork.Layer copyLayer(MagicCircleNetwork.Layer source, int width, int height) {
        MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
        if (source == null) return layer;
        int count = Math.min(MAX_NODES_PER_LAYER, source.nodes().size());
        for (int i = 0; i < count; i++) {
            MagicCircleNetwork.Node node = source.nodes().get(i);
            if (node == null || node.type() == null) continue;
            layer.add(new MagicCircleNetwork.Node(node.type(),
                    Math.max(0, Math.min(width - 1, node.x())),
                    Math.max(0, Math.min(height - 1, node.y())),
                    node.element(), node.semantic(), node.power(), node.dye(),
                    node.variant(), node.handDrawn()));
        }
        for (MagicCircleNetwork.Link link : source.links()) {
            if (link.from() < 0 || link.to() < 0 || link.from() >= count || link.to() >= count) continue;
            layer.addLink(link.from(), link.to());
        }
        return layer;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String author() { return author; }
    public int baseW() { return baseW; }
    public int baseH() { return baseH; }
    public Carrier carrier() { return carrier; }
    public int canvasWidth() { return baseW * MagicCircleNetwork.TILE; }
    public int canvasHeight() { return baseH * MagicCircleNetwork.TILE; }
    public List<MagicCircleNetwork.Layer> layers() { return Collections.unmodifiableList(layers); }
    public int baseCount() { return baseW * baseH; }

    public int nodeCount() {
        int count = 0;
        for (MagicCircleNetwork.Layer layer : layers) count += layer.nodes().size();
        return count;
    }

    public int filledLayerCount() {
        int count = 0;
        for (MagicCircleNetwork.Layer layer : layers) if (!layer.nodes().isEmpty()) count++;
        return count;
    }

    public MagicCircleCost.Evaluation evaluate() {
        return MagicCircleCost.evaluate(layers, baseCount());
    }

    /** 这个载体装得下这份蓝图吗：块数和层数都要够。 */
    public boolean fits(Carrier carrier) {
        if (carrier == null) return false;
        return nodeCount() <= carrier.maxNodes() && filledLayerCount() <= carrier.maxLayers();
    }

    /**
     * 换一个载体重建同一份阵图。
     *
     * <p>法阵码里不带载体（谁都能导进纸 / 布 / 绸），导入时按手里真正用掉的那张写回去，
     * 免得物品数据里写着「魔法绸缎」却装在一张纸上，后面贴面 / 叠层 / 容量判断全乱。</p>
     */
    public MagicCircleBlueprint withCarrier(Carrier carrier) {
        if (carrier == null || carrier == this.carrier) return this;
        return new MagicCircleBlueprint(id, name, author, baseW, baseH, carrier, layers);
    }

    /** 蓝图上的主要元素（用于投影配色和试运行粒子），没有元素粉就返回 null。 */
    public ElementType primaryElement() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MagicCircleNetwork.Layer layer : layers) {
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                if (node.element().isEmpty()) continue;
                counts.merge(node.element(), 1, Integer::sum);
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return MagicCirclePowders.elementOf(best);
    }

    /** 一个功能块在投影上的颜色：有元素粉就用元素色，否则按功能块类型上色。 */
    public static int colorOf(MagicCircleNetwork.Node node) {
        if (node == null) return 0xFFFFFFFF;
        ElementType element = MagicCirclePowders.elementOf(node.element());
        if (element != null) return element.color() | 0xFF000000;
        MagicCircleBlockType type = node.type();
        return type == null ? 0xFFFFFFFF : TYPE_COLORS[type.ordinal() % TYPE_COLORS.length];
    }

    /** 抄录 / 拆解时列出来的材料清单：粉末物品 id → 数量。 */
    public Map<String, Integer> powderNeeds() {
        Map<String, Integer> needs = new LinkedHashMap<>();
        for (MagicCircleNetwork.Layer layer : layers) {
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                for (String powder : new String[]{node.element(), node.semantic(), node.power(), node.dye()}) {
                    if (powder == null || powder.isEmpty()) continue;
                    needs.merge(powder, 1, Integer::sum);
                }
            }
        }
        return needs;
    }

    // ------------------------------------------------------------------ 物品承载

    /** 抄录时的自动名字：主要元素 + 法阵，没有元素就叫「无名法阵」。 */
    public static String suggestName(MagicCircleNetwork network) {
        ElementType element = null;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MagicCircleNetwork.Layer layer : network.layers()) {
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                if (!node.element().isEmpty()) counts.merge(node.element(), 1, Integer::sum);
            }
        }
        int best = 0;
        String bestId = null;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                bestId = entry.getKey();
            }
        }
        element = MagicCirclePowders.elementOf(bestId);
        if (element == null) return "无名法阵";
        return elementName(element) + "法阵";
    }

    /** 元素的中文短名（服务端也能用，不依赖语言文件）。 */
    public static String elementName(ElementType element) {
        if (element == null) return "奥术";
        return switch (element) {
            case FIRE -> "炎";
            case WATER -> "水";
            case ICE -> "冰";
            case LIGHTNING -> "雷";
            case WIND -> "风";
            case EARTH -> "土";
            case NATURE -> "木";
            case METAL -> "金";
            case LIGHT -> "光";
            case POISON -> "毒";
            case SOUND -> "音";
        };
    }

    /** 把整个阵盘抄成一份蓝图。只抄有内容的层，最多 8 层。 */
    public static MagicCircleBlueprint fromNetwork(MagicCircleNetwork network, Carrier carrier,
                                                  String author, String name) {
        List<MagicCircleNetwork.Layer> copy = new ArrayList<>();
        for (MagicCircleNetwork.Layer layer : network.layers()) {
            if (layer.nodes().isEmpty()) continue;
            copy.add(layer);
        }
        if (copy.isEmpty()) copy.add(new MagicCircleNetwork.Layer());
        return new MagicCircleBlueprint("custom", name, author, network.baseWidth(), network.baseHeight(),
                carrier, copy);
    }

    /** 这个物品能不能当抄录载体（纸 / 魔力布匹 / 魔法绸缎）。 */
    public static Carrier carrierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.PAPER)) return Carrier.PAPER;
        if (stack.getItem() instanceof WornBlueprintItem) return Carrier.WORN;
        var holder = GeneratedMod.MOD_ITEMS.get(MagicEquipment.MAGIC_CLOTH);
        if (holder != null && stack.is(holder.get())) return Carrier.CLOTH;
        holder = GeneratedMod.MOD_ITEMS.get(MagicEquipment.MAGIC_SILK);
        if (holder != null && stack.is(holder.get())) return Carrier.SILK;
        // 兜底：按注册 id 比一次，防止有人用别的模组的纸 / 布。
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (id.endsWith(":" + MagicEquipment.MAGIC_CLOTH)) return Carrier.CLOTH;
        if (id.endsWith(":" + MagicEquipment.MAGIC_SILK)) return Carrier.SILK;
        return null;
    }

    /** 物品里有没有抄录过的蓝图数据。 */
    public static boolean has(ItemStack stack) {
        CustomData data = stack == null || stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(DATA_KEY);
    }

    /** 读出物品上的蓝图；没有就返回 null（残破的蓝图走 {@link MagicCircleBlueprints}）。 */
    public static MagicCircleBlueprint of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(DATA_KEY)) {
            MagicCircleBlueprint fromData = fromTag(data.copyTag().getCompound(DATA_KEY));
            if (fromData != null) return fromData;
        }
        if (stack.getItem() instanceof WornBlueprintItem worn) return MagicCircleBlueprints.worn(worn.blueprintId());
        return null;
    }

    /** 生成一份带蓝图数据的新物品（抄录的产物）。 */
    public static ItemStack createStack(Carrier carrier, MagicCircleBlueprint blueprint) {
        ItemStack stack = carrier == null ? ItemStack.EMPTY : carrier.stack().copy();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        blueprint.writeTo(stack);
        return stack;
    }

    /** 把蓝图写进这个物品堆（就地改）。 */
    public void writeTo(ItemStack stack) {
        CompoundTag root = new CompoundTag();
        root.put(DATA_KEY, toTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    // ------------------------------------------------------------------ 序列化

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("V", VERSION);
        tag.putString("Id", id);
        tag.putString("Name", name);
        tag.putString("Author", author);
        tag.putInt("W", baseW);
        tag.putInt("H", baseH);
        tag.putString("Carrier", carrier.id());
        ListTag list = new ListTag();
        for (MagicCircleNetwork.Layer layer : layers) list.add(layer.toTag());
        tag.put("Layers", list);
        return tag;
    }

    public static MagicCircleBlueprint fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        int version = tag.getInt("V");
        if (version < 0 || version > VERSION) return null; // 不认识的版本：宁可不加载，也不要读一半
        int baseW = tag.getInt("W");
        int baseH = tag.getInt("H");
        if (baseW < 1 || baseH < 1 || baseW > MAX_SIDE_BASES || baseH > MAX_SIDE_BASES) return null;
        Carrier carrier = Carrier.byId(tag.getString("Carrier"));
        List<MagicCircleNetwork.Layer> parsed = new ArrayList<>();
        ListTag list = tag.getList("Layers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < MAX_LAYERS; i++) {
            parsed.add(MagicCircleNetwork.Layer.fromTag(list.getCompound(i)));
        }
        if (parsed.isEmpty()) parsed.add(new MagicCircleNetwork.Layer());
        return new MagicCircleBlueprint(tag.getString("Id"), tag.getString("Name"), tag.getString("Author"),
                baseW, baseH, carrier, parsed);
    }

    // ------------------------------------------------------------------ 显示

    /** 工具提示 / 聊天栏里共用的一行摘要：尺寸 · 层数 · 块数 · 消耗。 */
    public List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        MagicCircleCost.Evaluation evaluation = evaluate();
        lines.add(Component.translatable("tooltip.dimensional_mana.blueprint.size",
                        baseW, baseH, canvasWidth(), canvasHeight(), filledLayerCount(), nodeCount())
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.dimensional_mana.blueprint.cost",
                        evaluation.startMana(), evaluation.upkeepMana(), evaluation.triggerMana(),
                        String.format(java.util.Locale.ROOT, "%.2f", evaluation.strength()))
                .withStyle(ChatFormatting.GOLD));
        if (!author.isEmpty()) {
            lines.add(Component.translatable("tooltip.dimensional_mana.blueprint.author", author)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** 主要功能块清单，比如「核心 ×1、触发 ×1、输出 ×2」。 */
    public String blockSummary() {
        Map<MagicCircleBlockType, Integer> counts = new LinkedHashMap<>();
        for (MagicCircleNetwork.Layer layer : layers) {
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                if (node.type() == null) continue;
                counts.merge(node.type(), 1, Integer::sum);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<MagicCircleBlockType, Integer> entry : counts.entrySet()) {
            if (builder.length() > 0) builder.append("、");
            builder.append(entry.getKey().fallbackName()).append(" ×").append(entry.getValue());
        }
        return builder.length() == 0 ? "空" : builder.toString();
    }

    /**
     * 逐块列出四个粉槽，物品提示 / 调试棒预览 / 绘制台悬停都用它。
     *
     * <p>顺序固定是「元素 / 语义 / 功率 / 染料」，空槽显示「-」。超过 {@code maxLines} 块时
     * 最后补一行「……还有 N 块」，免得大阵的工具提示顶出屏幕。</p>
     */
    public List<Component> nodeDetailLines(int maxLines) {
        List<Component> lines = new ArrayList<>();
        int limit = Math.max(0, maxLines);
        int total = 0;
        int shown = 0;
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            for (MagicCircleNetwork.Node node : layers.get(layerIndex).nodes()) {
                if (node.type() == null) continue;
                total++;
                if (shown >= limit) continue;
                lines.add(Component.translatable("tooltip.dimensional_mana.magiccircle.node.line",
                                layerIndex + 1, node.type().fallbackName(), node.x(), node.y(),
                                MagicCirclePowders.shortName(node.element()),
                                MagicCirclePowders.shortName(node.semantic()),
                                MagicCirclePowders.shortName(node.power()),
                                MagicCirclePowders.shortName(node.dye()))
                        .withStyle(ChatFormatting.GRAY));
                shown++;
            }
        }
        if (total > shown) {
            lines.add(Component.translatable("tooltip.dimensional_mana.magiccircle.node.more", total - shown)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** 材料清单，拆解 / 一键备料时用：「火元素粉 ×2、红石粉 ×1」。 */
    public List<Component> powderLines() {
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : powderNeeds().entrySet()) {
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(entry.getKey());
            if (powder == null) continue;
            Item item = powder.item();
            if (item == Items.AIR) continue;
            lines.add(Component.literal("· ").append(powder.name()).append(Component.literal(" ×" + entry.getValue()))
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
