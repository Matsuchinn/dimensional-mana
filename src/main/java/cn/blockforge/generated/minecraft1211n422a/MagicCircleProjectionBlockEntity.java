package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 投影锚点里存的蓝图层（最多 {@link MagicCircleConfig#MAX_LAYERS} 层）。
 *
 * <p>叠层规则：同一锚点上的蓝图必须同一个画布尺寸，不同尺寸直接拒绝（避免光点跑到墙外面）；
 * 每层的功能块单独算，但层数系数 D 是合并算的，所以叠得越多越强、也越贵。</p>
 *
 * <p>第 4 阶段在这个方块实体上又加了三样东西：</p>
 * <ul>
 *   <li><b>刻印（sealed）</b>：由刻印石板贴出来的锚点才是「实体锚点」，能正式运行；
 *       蓝图投影出来的锚点只能预览和试运行。这个标记同时存在方块状态里，用来换模型。</li>
 *   <li><b>运行（running）</b>：正式阵的开关，启动时扣启动成本，之后按时扣维持。</li>
 *   <li><b>连线（links）</b>：异面连线存的是另一个锚点的坐标，双方各存一份，
 *       渲染光脉冲和「整组启动 / 整组触发」都靠它。</li>
 * </ul>
 */
public final class MagicCircleProjectionBlockEntity extends BlockEntity {
    private static final String LAYERS_TAG = "Layers";
    private static final String LINKS_TAG = "Links";
    private static final String RUNNING_TAG = "Running";
    private static final String OWNER_TAG = "Owner";
    private static final String MINING_WINDOW_TAG = "MiningWindow";
    private static final String MINING_COUNT_TAG = "MiningCount";

    private final List<MagicCircleBlueprint> layers = new ArrayList<>();
    /** 异面连线：连到的其他锚点坐标（双向各存一份，删的时候两边都要删）。 */
    private final List<BlockPos> links = new ArrayList<>();
    /** 正式阵是否在运行（只有刻印锚点能开）。 */
    private boolean running;
    /** 谁是这座阵的主任；判断能不能启停、定时触发向谁扣魔素都用它。 */
    private UUID owner;
    /**
     * 上次试运行 / 触发的游戏刻（-1 = 还没跑过），用来做冷却，不进存档。
     *
     * <p>这里千万不能用 {@link Long#MIN_VALUE} 当「没跑过」：判断式是 {@code now - last < 冷却}，
     * 游戏刻减掉 MIN_VALUE 会整型溢出成负数，结果「第一次触发」永远被当成冷却中——
     * 阵启动成功却一发都打不出来，正是这个问题。</p>
     */
    private long lastTestTick = -1L;
    private long lastTriggerTick = -1L;
    /** 自动检测媒介上一次开火的刻（与手动 / 红石各自的 10 刻冷却分开，防条件一直满足时刷屏）。 */
    private long lastSensorTick = -1L;
    /** 挖矿阵每分钟限额用的窗口起点与计数。 */
    private long miningWindowStart = Long.MIN_VALUE;
    private int miningCount;

    public MagicCircleProjectionBlockEntity(BlockPos pos, BlockState state) {
        super(GeneratedMod.MAGIC_CIRCLE_PROJECTION_BLOCK_ENTITY.get(), pos, state);
    }

    public List<MagicCircleBlueprint> layers() {
        return Collections.unmodifiableList(layers);
    }

    public boolean isEmpty() { return layers.isEmpty(); }

    public int layerCount() { return layers.size(); }

    /** 画布尺寸由第一层决定，空锚点先按 2×2 画。 */
    public int baseW() { return layers.isEmpty() ? 2 : layers.get(0).baseW(); }

    public int baseH() { return layers.isEmpty() ? 2 : layers.get(0).baseH(); }

    public int maxLayers() { return MagicCircleConfig.MAX_LAYERS.get(); }

    /** 合并所有层算一次消耗，界面 / 调试棒 / 试运行 / 正式运行都用这一个结果。 */
    public MagicCircleCost.Evaluation evaluate() {
        if (layers.isEmpty()) return MagicCircleCost.evaluate(List.of(), 1);
        List<MagicCircleNetwork.Layer> merged = new ArrayList<>();
        for (MagicCircleBlueprint blueprint : layers) merged.addAll(blueprint.layers());
        return MagicCircleCost.evaluate(merged, baseW() * baseH());
    }

    /** 所有层里的功能块摊平，正式运行结算输出时用。 */
    public List<MagicCircleNetwork.Node> nodes() {
        List<MagicCircleNetwork.Node> all = new ArrayList<>();
        for (MagicCircleBlueprint blueprint : layers) {
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) all.addAll(layer.nodes());
        }
        return all;
    }

    public boolean sameSize(MagicCircleBlueprint blueprint) {
        return blueprint != null && blueprint.baseW() == baseW() && blueprint.baseH() == baseH();
    }

    public boolean full() { return layers.size() >= maxLayers(); }

    /** 加一层；返回失败原因由调用方决定怎么提示，这里只回 false。 */
    public boolean addLayer(MagicCircleBlueprint blueprint) {
        if (blueprint == null || full()) return false;
        if (!layers.isEmpty() && !sameSize(blueprint)) return false;
        layers.add(blueprint);
        sync();
        return true;
    }

    /** 卸掉最上面一层，把它退回去（潜行右键锚点 = 拆一层）。 */
    public MagicCircleBlueprint removeTopLayer() {
        if (layers.isEmpty()) return null;
        MagicCircleBlueprint removed = layers.remove(layers.size() - 1);
        if (layers.isEmpty()) setRunning(false);
        sync();
        return removed;
    }

    public void clearLayers() {
        if (layers.isEmpty()) return;
        layers.clear();
        setRunning(false);
        sync();
    }

    // ------------------------------------------------------------------ 刻印 / 运行

    /** 这个锚点是不是刻印石板贴出来的实体锚点（方块状态里的 sealed）。 */
    public boolean sealed() {
        return getBlockState().hasProperty(MagicCircleProjectionBlock.SEALED)
                && getBlockState().getValue(MagicCircleProjectionBlock.SEALED);
    }

    public boolean running() { return running && sealed() && !layers.isEmpty(); }

    public void setRunning(boolean value) {
        boolean next = value && sealed() && !layers.isEmpty();
        if (running == next) return;
        running = next;
        // 启停顺手记进维度档案：停机键 / 远程面板 / 运行上限要靠它找到「谁开着哪些阵」，
        // 因为方块实体只在区块加载时才存在，玩家一走远就点不到了。
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MagicCircleNetworks networks = MagicCircleNetworks.get(serverLevel);
            if (next) networks.markRunning(worldPosition, owner);
            else networks.markStopped(worldPosition);
        }
        sync();
    }

    /** 返回切换后的状态。 */
    public boolean toggleRunning() {
        setRunning(!running);
        return running;
    }

    public UUID owner() { return owner; }

    public void setOwner(UUID owner) { this.owner = owner; }

    // ------------------------------------------------------------------ 异面连线

    public List<BlockPos> links() { return Collections.unmodifiableList(links); }

    public boolean linkedTo(BlockPos pos) { return links.contains(pos); }

    public int linkCount() { return links.size(); }

    public boolean addLink(BlockPos pos) {
        if (pos == null || pos.equals(worldPosition) || links.contains(pos)) return false;
        if (links.size() >= MagicCircleConfig.MAX_LINKS_PER_ANCHOR.get()) return false;
        links.add(pos.immutable());
        sync();
        return true;
    }

    public boolean removeLink(BlockPos pos) {
        if (!links.remove(pos)) return false;
        sync();
        return true;
    }

    public void clearLinks() {
        if (links.isEmpty()) return;
        links.clear();
        sync();
    }

    // ------------------------------------------------------------------ 计时

    public long lastTestTick() { return lastTestTick; }

    public void markTestRun(long gameTick) { lastTestTick = gameTick; }

    public long lastTriggerTick() { return lastTriggerTick; }

    public void markTrigger(long gameTick) { lastTriggerTick = gameTick; }

    public long lastSensorTick() { return lastSensorTick; }

    public void markSensor(long gameTick) { lastSensorTick = gameTick; }

    /** 挖矿阵的每分钟限额：返回这一分钟里还能挖几块（顺便滚窗口）。 */
    public int miningBudget(long gameTick) {
        long windowLength = 20L * 60L;
        if (miningWindowStart == Long.MIN_VALUE || gameTick - miningWindowStart >= windowLength) {
            miningWindowStart = gameTick;
            miningCount = 0;
        }
        return Math.max(0, MagicCircleConfig.MAX_BLOCKS_PER_MINUTE.get() - miningCount);
    }

    public void addMining(int amount, long gameTick) {
        miningBudget(gameTick);
        miningCount += Math.max(0, amount);
    }

    /** 改完数据要让客户端重画：方块实体数据包 + 存档标脏一起走。 */
    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (MagicCircleBlueprint blueprint : layers) list.add(blueprint.toTag());
        tag.put(LAYERS_TAG, list);
        ListTag linkList = new ListTag();
        for (BlockPos pos : links) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", pos.getX());
            entry.putInt("Y", pos.getY());
            entry.putInt("Z", pos.getZ());
            linkList.add(entry);
        }
        tag.put(LINKS_TAG, linkList);
        tag.putBoolean(RUNNING_TAG, running);
        if (owner != null) tag.putUUID(OWNER_TAG, owner);
        tag.putLong(MINING_WINDOW_TAG, miningWindowStart == Long.MIN_VALUE ? -1L : miningWindowStart);
        tag.putInt(MINING_COUNT_TAG, miningCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        layers.clear();
        ListTag list = tag.getList(LAYERS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < Math.max(1, maxLayers()); i++) {
            MagicCircleBlueprint blueprint = MagicCircleBlueprint.fromTag(list.getCompound(i));
            if (blueprint != null) layers.add(blueprint);
        }
        links.clear();
        ListTag linkList = tag.getList(LINKS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < linkList.size() && i < 64; i++) {
            CompoundTag entry = linkList.getCompound(i);
            links.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        running = tag.getBoolean(RUNNING_TAG);
        owner = tag.contains(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        long window = tag.getLong(MINING_WINDOW_TAG);
        miningWindowStart = window <= 0L ? Long.MIN_VALUE : window;
        miningCount = tag.getInt(MINING_COUNT_TAG);
    }

    /** 客户端渲染要拿到层数据与连线，所以整份存档发过去。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
