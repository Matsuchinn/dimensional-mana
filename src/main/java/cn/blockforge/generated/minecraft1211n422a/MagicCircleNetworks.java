package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 每个维度一份的阵盘档案：保存所有已注册阵面的基座位置、层与功能块图。
 *
 * <p>这里同时也负责「扫描连通基座 → 注册成阵盘 → 给玩家报状态」这三件事，
 * 绘制台基座方块与魔导调试棒都调用这里的静态方法。</p>
 */
public final class MagicCircleNetworks extends SavedData {
    private static final String DATA_NAME = "dimensional_mana_magic_circles";
    private static final BlockPos[] NEIGHBOURS = {
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
    };
    /** 报「缺哪一格」时最多列几个坐标，再多就省略，免得刷屏。 */
    private static final int MAX_REPORTED_CELLS = 6;

    private final Map<String, MagicCircleNetwork> networks = new LinkedHashMap<>();
    /**
     * 正在运行的投影锚点：坐标 → 主人 UUID。
     *
     * <p>方块实体只在所在区块加载时才存在，玩家走远之后就点不到自己开着哪些阵了。
     * 这里随维度存档记一份：停机键、远程面板和「最多同时开几个」都靠它，
     * 锚点被挖掉或维持费付不起时由 {@code MagicCircleProjectionBlockEntity#setRunning} 同步删掉。</p>
     */
    private final Map<BlockPos, UUID> runningAnchors = new LinkedHashMap<>();

    public static MagicCircleNetworks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MagicCircleNetworks::new, MagicCircleNetworks::load),
                DATA_NAME);
    }

    public MagicCircleNetwork byId(String id) {
        return id == null || id.isEmpty() ? null : networks.get(id);
    }

    /** 按基座坐标反查阵盘：方块实体没记上 id 时用它兜底。 */
    public MagicCircleNetwork byBase(BlockPos pos) {
        for (MagicCircleNetwork network : networks.values()) {
            if (network.bases().contains(pos)) return network;
        }
        return null;
    }

    public Collection<MagicCircleNetwork> all() {
        return List.copyOf(networks.values());
    }

    public void put(MagicCircleNetwork network) {
        networks.put(network.id(), network);
        setDirty();
    }

    public void remove(String id) {
        if (networks.remove(id) != null) setDirty();
    }

    // ------------------------------------------------------------------ 运行中的锚点

    /** 记一个「这个锚点正在跑，主人是 owner」；同一位置重复登记只更新主人。 */
    public void markRunning(BlockPos pos, UUID owner) {
        if (pos == null || owner == null) return;
        UUID previous = runningAnchors.put(pos.immutable(), owner);
        if (!owner.equals(previous)) setDirty();
    }

    /** 这个锚点停了（或已经不存在）；没登记过就什么也不做。 */
    public void markStopped(BlockPos pos) {
        if (pos == null) return;
        if (runningAnchors.remove(pos.immutable()) != null) setDirty();
    }

    /** 现在登记着几个属于这个玩家的运行中锚点。 */
    public int countRunning(UUID owner) {
        if (owner == null) return 0;
        int count = 0;
        for (UUID value : runningAnchors.values()) if (owner.equals(value)) count++;
        return count;
    }

    /** 这个玩家登记在案的所有运行中锚点坐标（停机键与远程面板用）。 */
    public List<BlockPos> runningAnchorsOf(UUID owner) {
        List<BlockPos> found = new ArrayList<>();
        if (owner == null) return found;
        for (Map.Entry<BlockPos, UUID> entry : runningAnchors.entrySet()) {
            if (owner.equals(entry.getValue())) found.add(entry.getKey());
        }
        return found;
    }

    /** 这个位置是不是绘制台基座。 */
    public static boolean isBase(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MagicCircleBaseBlock;
    }

    /**
     * 在 {@code center} 附近找一块基座：先看中心本身，再按距离由近到远扫。
     *
     * <p>基座只有 3 像素厚，视角稍微偏一点就会点到旁边的方块；调试棒和指令都用它
     * 把「差一点点没点中」救回来。</p>
     */
    public static BlockPos nearestBase(Level level, BlockPos center, int radius) {
        if (isBase(level, center)) return center;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    if (!isBase(level, candidate)) continue;
                    double distance = candidate.distSqr(center);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    /** 玩家脚下的那一片基座（站在 3 像素厚的板上时，脚下这一格就是基座）。 */
    public static BlockPos baseUnderPlayer(Level level, Player player) {
        BlockPos feet = player.blockPosition();
        if (isBase(level, feet)) return feet;
        if (isBase(level, feet.below())) return feet.below();
        if (isBase(level, feet.below(2))) return feet.below(2);
        return null;
    }

    /** 一次连通扫描的结果：基座列表 + 外框尺寸（以基座个数计）。 */
    public record Scan(List<BlockPos> bases, int width, int height) {
        public int missing() {
            return Math.max(0, width * height - bases.size());
        }

        public int minX() {
            int min = Integer.MAX_VALUE;
            for (BlockPos pos : bases) min = Math.min(min, pos.getX());
            return min;
        }

        public int minZ() {
            int min = Integer.MAX_VALUE;
            for (BlockPos pos : bases) min = Math.min(min, pos.getZ());
            return min;
        }

        public int y() {
            return bases.isEmpty() ? 0 : bases.get(0).getY();
        }
    }

    /** 从任意一块基座出发，找到同一 Y 层、正交连通的一整片基座。 */
    public static Scan scan(ServerLevel level, BlockPos start) {
        if (!isBase(level, start)) return null;
        int maxSide = MagicCircleConfig.MAX_BASES_PER_SIDE.get();
        // 多收几倍是为了把「明显超大」的区域完整框出来，好如实报「太大了」而不是「不是矩形」。
        int limit = Math.max(1024, maxSide * maxSide * 4);
        Set<BlockPos> visited = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) continue;
            if (visited.size() > limit) break;
            for (BlockPos offset : NEIGHBOURS) {
                BlockPos next = pos.offset(offset);
                if (next.getY() != start.getY() || visited.contains(next)) continue;
                if (isBase(level, next)) queue.add(next);
            }
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : visited) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new Scan(List.copyOf(visited), maxX - minX + 1, maxZ - minZ + 1);
    }

    /** 这块基座是不是已经属于某个阵盘。 */
    public static boolean isRegistered(ServerLevel level, BlockPos pos) {
        if (!networkIdAt(level, pos).isEmpty()) return true;
        return get(level).byBase(pos) != null;
    }

    /**
     * 调试棒点中基座时的统一入口：
     * 没注册过 → 直接注册（不再要求先潜行）；注册过 → 打开绘制台界面并报状态；
     * 潜行 → 按现在的形状重新注册。
     */
    public static void handleWandUse(ServerLevel level, BlockPos pos, ServerPlayer player, boolean force) {
        if (!isBase(level, pos)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.not_base").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (!force && isRegistered(level, pos)) {
            reportStatus(level, pos, player);
            MagicCircleDrawing.open(level, pos, player);
            return;
        }
        if (registerOrReport(level, pos, player, force)) {
            MagicCircleDrawing.open(level, pos, player);
        }
    }

    /** 调试棒右键：检查连通性、注册阵盘，并把结果告诉玩家。 */
    public static boolean registerOrReport(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return registerOrReport(level, pos, player, false);
    }

    /**
     * @param force true 时无视「已经注册过」，按当前形状重新注册一遍（潜行右键走这条路）
     * @return 是否真的注册成功（界面只在成功时打开）
     */
    public static boolean registerOrReport(ServerLevel level, BlockPos pos, ServerPlayer player, boolean force) {
        if (!isBase(level, pos)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.not_base").withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        Scan scan = scan(level, pos);
        if (scan == null || scan.bases().isEmpty()) return false;
        int maxSide = Math.max(scan.width(), scan.height());
        if (maxSide > MagicCircleConfig.MAX_BASES_PER_SIDE.get()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.too_large",
                    MagicCircleConfig.MAX_BASES_PER_SIDE.get()).withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (scan.missing() > 0) {
            reportShapeProblem(level, scan, player);
            return false;
        }

        MagicCircleNetworks data = get(level);
        Set<String> previousIds = new LinkedHashSet<>();
        for (BlockPos base : scan.bases()) {
            String previous = networkIdAt(level, base);
            if (!previous.isEmpty()) previousIds.add(previous);
            else {
                MagicCircleNetwork byPosition = data.byBase(base);
                if (byPosition != null) previousIds.add(byPosition.id());
            }
        }
        if (previousIds.size() == 1) {
            MagicCircleNetwork existing = data.byId(previousIds.iterator().next());
            boolean sameShape = existing != null
                    && existing.bases().size() == scan.bases().size()
                    && new LinkedHashSet<>(existing.bases()).equals(new LinkedHashSet<>(scan.bases()));
            if (sameShape) {
                // 形状没变：保留已经画好的阵图，只把基座重新挂到这个阵盘上。
                for (BlockPos base : scan.bases()) {
                    if (level.getBlockEntity(base) instanceof MagicCircleBaseBlockEntity entity
                            && !existing.id().equals(entity.networkId())) {
                        entity.setNetworkId(existing.id());
                    }
                }
                player.displayClientMessage(Component.translatable(force
                                ? "message.dimensional_mana.magiccircle.re_registered"
                                : "message.dimensional_mana.magiccircle.already_registered"), false);
                reportStatus(level, pos, player);
                return true;
            }
        }
        for (String previous : previousIds) data.remove(previous);

        MagicCircleNetwork network = new MagicCircleNetwork(UUID.randomUUID().toString(), player.getUUID(),
                scan.width(), scan.height(), scan.bases());
        data.put(network);
        for (BlockPos base : scan.bases()) {
            if (level.getBlockEntity(base) instanceof MagicCircleBaseBlockEntity entity) {
                entity.setNetworkId(network.id());
            }
        }
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.registered",
                network.baseWidth(), network.baseHeight(), network.canvasWidth(), network.canvasHeight(),
                MagicCircleConfig.MAX_FUNCTIONAL_BLOCKS.get()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.status.powders",
                MagicCirclePowders.unlockedCount(player), MagicCirclePowders.all().size())
                .withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.stage_hint").withStyle(ChatFormatting.DARK_GRAY), false);
        // 注册成功之后直接把状态（含缺核心 / 缺触发这类提示）一并报出来，省得玩家再右键一次。
        reportStatus(level, pos, player);
        return true;
    }

    /** 谁能改这个阵盘：所有者本人，或者有管理员权限的人；其他人只能看。 */
    public static boolean canEdit(MagicCircleNetwork network, ServerPlayer player) {
        if (network == null || player == null) return false;
        if (network.owner() == null) return true;
        return network.owner().equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** 阵图被界面改过之后要把维度存档标脏，不然重启会丢。 */
    public static void markDirty(ServerLevel level) {
        get(level).setDirty();
    }

    /** 形状不合格时把「差在哪」讲清楚：缺的格子坐标 + 是不是有基座铺在别的高度。 */
    private static void reportShapeProblem(ServerLevel level, Scan scan, ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.not_rectangular",
                scan.width(), scan.height(), scan.missing()).withStyle(ChatFormatting.RED), false);
        List<BlockPos> missing = missingCells(scan);
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.missing_cells",
                    formatCells(missing)).withStyle(ChatFormatting.GRAY), false);
        }
        int others = countBasesAtOtherHeights(level, scan);
        if (others > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.other_height", others)
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** 外框里还空着的格子。 */
    private static List<BlockPos> missingCells(Scan scan) {
        int minX = scan.minX();
        int minZ = scan.minZ();
        int y = scan.y();
        List<BlockPos> missing = new ArrayList<>();
        Set<BlockPos> bases = new LinkedHashSet<>(scan.bases());
        for (int dx = 0; dx < scan.width(); dx++) {
            for (int dz = 0; dz < scan.height(); dz++) {
                BlockPos candidate = new BlockPos(minX + dx, y, minZ + dz);
                if (!bases.contains(candidate)) missing.add(candidate);
            }
        }
        return missing;
    }

    /** 外框附近、但高度不一样的基座数量：这是新手最常见的失败原因（地面不平）。 */
    private static int countBasesAtOtherHeights(ServerLevel level, Scan scan) {
        int y = scan.y();
        int minX = scan.minX() - 1;
        int minZ = scan.minZ() - 1;
        int maxX = scan.minX() + scan.width();
        int maxZ = scan.minZ() + scan.height();
        int count = 0;
        Set<BlockPos> visited = new LinkedHashSet<>();
        for (int dy = -2; dy <= 2; dy++) {
            if (dy == 0) continue;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos candidate = new BlockPos(x, y + dy, z);
                    if (visited.add(candidate) && isBase(level, candidate)) count++;
                }
            }
        }
        return count;
    }

    private static String formatCells(List<BlockPos> cells) {
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (BlockPos pos : cells) {
            if (shown >= MAX_REPORTED_CELLS) {
                builder.append(" …");
                break;
            }
            if (shown > 0) builder.append("、");
            builder.append('(').append(pos.getX()).append(", ").append(pos.getY())
                    .append(", ").append(pos.getZ()).append(')');
            shown++;
        }
        return builder.toString();
    }

    private static String networkIdAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MagicCircleBaseBlockEntity entity) return entity.networkId();
        return "";
    }

    /** 调试棒普通右键：免费预览当前阵盘的消耗、复杂度与缺失项。 */
    public static void reportStatus(ServerLevel level, BlockPos pos, ServerPlayer player) {
        MagicCircleBaseBlockEntity entity = level.getBlockEntity(pos) instanceof MagicCircleBaseBlockEntity found
                ? found : null;
        String id = entity == null ? "" : entity.networkId();
        MagicCircleNetworks data = get(level);
        MagicCircleNetwork network = data.byId(id);
        if (network == null) network = data.byBase(pos);
        if (network == null) {
            if (entity != null) entity.setNetworkId("");
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.no_network").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        // 方块实体丢了 id（老存档 / 中途换过版本）时顺手补回来，以后就不用再按坐标兜底。
        if (entity != null && !network.id().equals(entity.networkId())) entity.setNetworkId(network.id());
        MagicCircleCost.Evaluation evaluation = MagicCircleCost.evaluate(network);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.status.header",
                network.baseWidth(), network.baseHeight(), network.canvasWidth(), network.canvasHeight(),
                evaluation.nodeCount(), evaluation.filledLayers()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.status.cost",
                evaluation.startMana(), evaluation.upkeepMana(), evaluation.triggerMana())
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.status.strength",
                String.format("%.2f", evaluation.strength()), evaluation.complexity(),
                evaluation.disorder(), String.format("%.2f", evaluation.efficiency()))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (String problem : evaluation.problems()) {
            player.displayClientMessage(Component.empty().append(MagicCircleIssue.component(problem)).withStyle(ChatFormatting.RED), false);
        }
    }

    /** 拆掉一块基座时让整个阵盘失效，避免留下指向空气的锚点。 */
    public static void invalidate(ServerLevel level, String networkId) {
        if (networkId == null || networkId.isEmpty()) return;
        MagicCircleNetworks data = get(level);
        MagicCircleNetwork network = data.byId(networkId);
        if (network == null) return;
        for (BlockPos base : network.bases()) {
            if (level.getBlockEntity(base) instanceof MagicCircleBaseBlockEntity entity
                    && networkId.equals(entity.networkId())) {
                entity.setNetworkId("");
            }
        }
        data.remove(networkId);
    }

    /** 注销一块基座所在的整个阵盘：方块实体与维度存档一起清掉。 */
    public static MagicCircleNetwork unregister(ServerLevel level, BlockPos pos) {
        MagicCircleNetworks data = get(level);
        MagicCircleNetwork network = data.byId(networkIdAt(level, pos));
        if (network == null) network = data.byBase(pos);
        if (network == null) {
            if (level.getBlockEntity(pos) instanceof MagicCircleBaseBlockEntity entity) entity.setNetworkId("");
            return null;
        }
        for (BlockPos base : network.bases()) {
            if (level.getBlockEntity(base) instanceof MagicCircleBaseBlockEntity entity
                    && network.id().equals(entity.networkId())) {
                entity.setNetworkId("");
            }
        }
        data.remove(network.id());
        return network;
    }

    /** 把当前维度已注册的阵盘数量与尺寸报给界面 / 指令。 */
    public static List<MagicCircleNetwork> snapshot(ServerLevel level) {
        return get(level).snapshot();
    }

    public static MagicCircleNetworks load(CompoundTag tag, HolderLookup.Provider registries) {
        MagicCircleNetworks data = new MagicCircleNetworks();
        ListTag list = tag.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MagicCircleNetwork network = MagicCircleNetwork.fromTag(list.getCompound(i));
            if (network.id() != null && !network.id().isEmpty()) data.networks.put(network.id(), network);
        }
        // 老的维度存档没有这一项，读不到就当「没有阵在跑」，不影响其它数据。
        ListTag running = tag.getList("RunningAnchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < running.size(); i++) {
            CompoundTag entry = running.getCompound(i);
            try {
                UUID owner = UUID.fromString(entry.getString("Owner"));
                data.runningAnchors.put(BlockPos.of(entry.getLong("Pos")), owner);
            } catch (RuntimeException ignored) {
                // 坐标或主人坏了就丢掉这一条，别让整份存档读不出来
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MagicCircleNetwork network : networks.values()) list.add(network.toTag());
        tag.put("Networks", list);
        ListTag running = new ListTag();
        for (Map.Entry<BlockPos, UUID> entry : runningAnchors.entrySet()) {
            CompoundTag record = new CompoundTag();
            record.putLong("Pos", entry.getKey().asLong());
            record.putString("Owner", entry.getValue().toString());
            running.add(record);
        }
        tag.put("RunningAnchors", running);
        return tag;
    }

    /** 给外部（界面 / 指令）用的一份不可变快照。 */
    public List<MagicCircleNetwork> snapshot() {
        return new ArrayList<>(networks.values());
    }
}
