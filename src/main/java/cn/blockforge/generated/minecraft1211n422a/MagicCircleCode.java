package cn.blockforge.generated.minecraft1211n422a;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 法阵码：把一份蓝图压成一行可以复制粘贴的文本。
 *
 * <p>格式照设计文档 9.2：{@code DM1-<base64url(deflate(json))>}，最长 16384 个字符。
 * 码里只有白名单字段（尺寸、层、功能块、粉末、连线、名字和作者），不接受指令、NBT 之类
 * 任何会被原样写回存档的东西，所以别人发来的码最多只能得到一张阵图。</p>
 *
 * <p>导入时所有数值都夹在合法范围内：尺寸 1~64 个基座、单层 512 块、最多 8 层；
 * 认不出的功能块类型直接丢，认不出的粉末当成「没放粉」，坏码一律返回带原因的失败。</p>
 */
public final class MagicCircleCode {
    public static final String PREFIX = "DM1-";
    /**
     * 一行的字符上限。原来写的是 4096，但蓝图本身允许 64×64 基座 / 单层 512 块 / 8 层，
     * 大阵的码轻松超过 4096，于是「导得出去、导不回来」。这里放宽到 16384，和蓝图的上限对齐。
     */
    public static final int MAX_CHARS = 16384;
    /** 第 2 版：块上多了 {@code dc}（声明模式参数串）；v1 的码照常能解，条件节点当无声明。 */
    /** 第 3 版：门控规范 v2.4 的 {@code gm}（模式 + 比较符两字符串）；v1/v2 的码按旧规则推断。 */
    private static final int VERSION = 3;
    /** 和 {@link MagicCircleBlueprint} 的硬上限保持一致，避免导出成功、导入却被判「尺寸不合法」。 */
    private static final int MAX_SIDE = 64;
    private static final int MAX_NODES_PER_LAYER = 512;
    private static final int MAX_LAYERS = 8;
    private static final Gson GSON = new Gson();

    /** 解析结果：成功时 blueprint 非空，失败时 error 是人话。 */
    public record Result(MagicCircleBlueprint blueprint, String error) {
        public boolean ok() { return blueprint != null; }

        static Result fail(String error) { return new Result(null, error); }

        static Result ok(MagicCircleBlueprint blueprint) { return new Result(blueprint, ""); }
    }

    // ------------------------------------------------------------------ 导出

    public static String encode(MagicCircleBlueprint blueprint) {
        if (blueprint == null) return "";
        JsonObject root = new JsonObject();
        root.addProperty("v", VERSION);
        root.addProperty("n", blueprint.name());
        root.addProperty("a", blueprint.author());
        root.addProperty("w", blueprint.baseW());
        root.addProperty("h", blueprint.baseH());
        JsonArray layers = new JsonArray();
        for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
            JsonObject layerJson = new JsonObject();
            JsonArray blocks = new JsonArray();
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                JsonObject block = new JsonObject();
                block.addProperty("t", node.type() == null ? "" : node.type().id());
                block.addProperty("x", node.x());
                block.addProperty("y", node.y());
                block.addProperty("e", node.element());
                block.addProperty("s", node.semantic());
                block.addProperty("p", node.power());
                block.addProperty("d", node.dye());
                block.addProperty("v", node.variant());
                block.addProperty("h", node.handDrawn() ? 1 : 0);
                if (node.declare() != null && !node.declare().isEmpty()) {
                    block.addProperty("dc", node.declare());
                }
                if (node.gate() != null && !node.gate().isEmpty()) {
                    block.addProperty("gm", node.gate());
                }
                blocks.add(block);
            }
            layerJson.add("blocks", blocks);
            JsonArray links = new JsonArray();
            for (MagicCircleNetwork.Link link : layer.links()) {
                JsonArray pair = new JsonArray();
                pair.add(link.from());
                pair.add(link.to());
                links.add(pair);
            }
            layerJson.add("links", links);
            layers.add(layerJson);
        }
        root.add("layers", layers);

        byte[] json = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        byte[] packed = deflate(json);
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        String code = PREFIX + body;
        // 超长的码宁可不给：给出去也是导入端直接拒绝，不如让调用方说清楚「太大，压不进一行」。
        return code.length() > MAX_CHARS ? "" : code;
    }

    // ------------------------------------------------------------------ 导入

    public static Result decode(String code) {
        if (code == null) return Result.fail("error.dimensional_mana.magiccircle.code.empty");
        String trimmed = code.trim();
        // 服务器限额（配置 importMaxChars）与硬上限取小的那个（规范 20 maxImportSize）。
        int limit = Math.min(MAX_CHARS, Math.max(256, MagicCircleConfig.IMPORT_MAX_CHARS.get()));
        if (trimmed.length() > limit) {
            return Result.fail("error.dimensional_mana.magiccircle.code.too_long");
        }
        if (!trimmed.startsWith(PREFIX)) {
            return Result.fail("error.dimensional_mana.magiccircle.code.prefix");
        }
        String body = trimmed.substring(PREFIX.length());
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(pad(body));
        } catch (IllegalArgumentException error) {
            return Result.fail("error.dimensional_mana.magiccircle.code.base64");
        }
        String json;
        try {
            json = new String(inflate(packed), StandardCharsets.UTF_8);
        } catch (DataFormatException | RuntimeException error) {
            return Result.fail("error.dimensional_mana.magiccircle.code.corrupt");
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return Result.fail("error.dimensional_mana.magiccircle.code.corrupt");
            root = parsed.getAsJsonObject();
        } catch (RuntimeException error) {
            return Result.fail("error.dimensional_mana.magiccircle.code.corrupt");
        }
        int version = optInt(root, "v", 0);
        if (version < 0 || version > VERSION) {
            return Result.fail("error.dimensional_mana.magiccircle.code.version");
        }
        int baseW = optInt(root, "w", 0);
        int baseH = optInt(root, "h", 0);
        if (baseW < 1 || baseH < 1 || baseW > MAX_SIDE || baseH > MAX_SIDE) {
            return Result.fail("error.dimensional_mana.magiccircle.code.size");
        }
        int canvasW = baseW * MagicCircleNetwork.TILE;
        int canvasH = baseH * MagicCircleNetwork.TILE;
        String name = optString(root, "n", "导入的法阵");
        String author = optString(root, "a", "");
        if (name.isBlank()) name = "导入的法阵";
        if (name.length() > 48) name = name.substring(0, 48);
        if (author.length() > 32) author = author.substring(0, 32);

        List<MagicCircleNetwork.Layer> layers = new ArrayList<>();
        JsonArray layerArray = root.has("layers") && root.get("layers").isJsonArray()
                ? root.getAsJsonArray("layers") : new JsonArray();
        for (int i = 0; i < layerArray.size() && layers.size() < MAX_LAYERS; i++) {
            JsonElement element = layerArray.get(i);
            if (!element.isJsonObject()) continue;
            MagicCircleNetwork.Layer layer = new MagicCircleNetwork.Layer();
            JsonObject layerJson = element.getAsJsonObject();
            JsonArray blocks = layerJson.has("blocks") && layerJson.get("blocks").isJsonArray()
                    ? layerJson.getAsJsonArray("blocks") : new JsonArray();
            for (int b = 0; b < blocks.size() && layer.nodes().size() < MAX_NODES_PER_LAYER; b++) {
                JsonElement blockElement = blocks.get(b);
                if (!blockElement.isJsonObject()) continue;
                JsonObject block = blockElement.getAsJsonObject();
                MagicCircleBlockType type = MagicCircleBlockType.byId(optString(block, "t", ""));
                if (type == null) continue;
                int x = clamp(optInt(block, "x", 0), canvasW);
                int y = clamp(optInt(block, "y", 0), canvasH);
                String declare = optString(block, "dc", "");
                String gate = "";
                if (type == MagicCircleBlockType.CONDITION) {
                    // 规范 19：导入时按大类 / 染料档 / 功率精度重夹一遍，越档参数落回档内，
                    // 不会因为一串手改的码就凭空多出越权数值。
                    declare = MagicCircleDecl.reconcile(declare,
                            powderOrEmpty(optString(block, "d", "")),
                            powderOrEmpty(optString(block, "p", "")));
                    // 门控串只认白名单组合（G/D 模式 + 可选 L/R 比较符）；其余一律当没带，退回旧规则。
                    String wanted = optString(block, "gm", "");
                    if (wanted.equals("G") || wanted.equals("D") || wanted.equals("GL")
                            || wanted.equals("GR") || wanted.equals("DL") || wanted.equals("DR")) {
                        gate = wanted;
                    }
                } else {
                    declare = "";
                }
                layer.add(new MagicCircleNetwork.Node(type, x, y,
                        powderOrEmpty(optString(block, "e", "")),
                        powderOrEmpty(optString(block, "s", "")),
                        powderOrEmpty(optString(block, "p", "")),
                        powderOrEmpty(optString(block, "d", "")),
                        optString(block, "v", "").length() > 16 ? "" : optString(block, "v", ""),
                        optInt(block, "h", 0) != 0, declare, gate));
            }
            int size = layer.nodes().size();
            JsonArray links = layerJson.has("links") && layerJson.get("links").isJsonArray()
                    ? layerJson.getAsJsonArray("links") : new JsonArray();
            for (int l = 0; l < links.size(); l++) {
                JsonElement linkElement = links.get(l);
                if (!linkElement.isJsonArray()) continue;
                JsonArray pair = linkElement.getAsJsonArray();
                if (pair.size() < 2) continue;
                int from = optInt(pair.get(0), 0);
                int to = optInt(pair.get(1), 0);
                if (from < 0 || to < 0 || from >= size || to >= size || from == to) continue;
                layer.addLink(from, to);
            }
            layers.add(layer);
        }
        if (layers.isEmpty()) layers.add(new MagicCircleNetwork.Layer());
        return Result.ok(new MagicCircleBlueprint("imported", name, author, baseW, baseH,
                MagicCircleBlueprint.Carrier.SILK, layers));
    }

    /** 空列表 / 非法粉末 id 一律当「这格没放粉」，保证不会出现指不到物品的粉。 */
    private static String powderOrEmpty(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        return MagicCirclePowders.get(itemId) == null ? "" : itemId;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(Math.max(0, size - 1), value));
    }

    private static int optInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key)) return fallback;
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    private static int optInt(JsonElement element, int fallback) {
        try {
            return element == null ? fallback : element.getAsInt();
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    private static String optString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key)) return fallback;
        try {
            String value = object.get(key).getAsString();
            return value == null ? fallback : value;
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    private static String pad(String body) {
        int rest = body.length() % 4;
        return rest == 0 ? body : body + "=".repeat(4 - rest);
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int written = deflater.deflate(buffer);
            out.write(buffer, 0, written);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] packed) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int written = inflater.inflate(buffer);
                if (written == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new DataFormatException("truncated deflate stream");
                    }
                    break;
                }
                out.write(buffer, 0, written);
                if (out.size() > 256 * 1024) throw new DataFormatException("payload too large");
            }
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    private MagicCircleCode() {}
}
