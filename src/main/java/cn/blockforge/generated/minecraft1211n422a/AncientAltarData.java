package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 已发现的古代魔法祭坛登记表（每个存档一份，落盘保存）。
 *
 * <p>登记来源两条，按位置去重、互不冲突：</p>
 * <ul>
 *   <li><b>新结构</b>：祭坛构件生成时经 {@link WorldgenSpawnQueue} 排队，
 *       等区块真正加载后进服务器主线程登记（世界生成阶段不能写存档数据）；</li>
 *   <li><b>旧存档补录</b>：区块加载时查本区块的结构起始点，是祭坛就按包围盒
 *       换算回中心补登记——更新前生成的祭坛因此也能获得加成与光晕。</li>
 * </ul>
 *
 * <p>登记后驱动两件事：{@link ManaConcentration} 读取 {@link #isEmpowered} 给范围内
 * 区块 +1.00 魔素浓度（不可叠加）；并同步给客户端画"中央到边界"的蓝色魔素光晕
 * （{@link AltarAuraPayload} / {@link ClientAltarAuraState}）。</p>
 */
public final class AncientAltarData extends SavedData {
    /** 影响半径：祭坛中央水平 12 格内（垂直同样限 12 格，不贯穿整根区块柱）。 */
    public static final int AURA_RADIUS = 12;
    /** 范围内统一 +1.00；判定只问"在不在任一祭坛范围内"，多座重叠也只加一次，不可叠加。 */
    public static final double AURA_BONUS = 1.0;
    /** 客户端光晕的推送半径：比影响圈大一截，玩家走近前画面就已就位。 */
    private static final int SYNC_RANGE = 96;

    private static final String FILE_NAME = GeneratedMod.MOD_ID + "_ancient_altars";
    private static final ResourceKey<Structure> ALTAR_KEY = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "ancient_magic_altar"));
    private static final Factory<AncientAltarData> FACTORY = new Factory<>(
            AncientAltarData::new,
            AncientAltarData::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final List<BlockPos> altars = new ArrayList<>();

    public static AncientAltarData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public synchronized boolean isEmpty() {
        return altars.isEmpty();
    }

    /** 只读快照，供同步与光晕遍历（不持有锁交给调用方慢慢用）。 */
    public synchronized List<BlockPos> snapshot() {
        return List.copyOf(altars);
    }

    /** 登记一座祭坛中心；±8 格内已有记录就跳过，两条登记路径不会重复。 */
    public synchronized void add(BlockPos center) {
        for (BlockPos existing : altars) {
            if (existing.closerThan(center, 8.0)) return;
        }
        altars.add(center.immutable());
        setDirty();
    }

    /** 该位置是否落在任一祭坛的 12 格影响圈内。 */
    public synchronized boolean contains(BlockPos pos) {
        for (BlockPos altar : altars) {
            if (withinAura(altar, pos)) return true;
        }
        return false;
    }

    /** 便捷入口：世界某位置是否受祭坛浓度加成。 */
    public static boolean isEmpowered(ServerLevel level, BlockPos pos) {
        return get(level).contains(pos);
    }

    /** 水平与垂直都按 12 格算的"祭坛周围"：判定口径与光晕画的范围完全一致。 */
    private static boolean withinAura(BlockPos altar, BlockPos pos) {
        int dx = pos.getX() - altar.getX();
        int dy = pos.getY() - altar.getY();
        int dz = pos.getZ() - altar.getZ();
        return dx * dx + dz * dz <= AURA_RADIUS * AURA_RADIUS && Math.abs(dy) <= AURA_RADIUS;
    }

    /**
     * 把玩家附近（{@value #SYNC_RANGE} 格内）的祭坛列表推给客户端画光晕；
     * 每 100 刻补发一次，空列表也发——客户端按列表整表替换，天然幂等。
     */
    public static void syncTo(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        AncientAltarData data = get(level);
        if (data.isEmpty()) return;
        List<BlockPos> nearby = new ArrayList<>();
        for (BlockPos altar : data.snapshot()) {
            if (altar.closerThan(player.blockPosition(), SYNC_RANGE + AURA_RADIUS)) nearby.add(altar);
        }
        if (!nearby.isEmpty()) AltarAuraPayload.send(level, player, nearby);
    }

    /** 区块加载补录：旧存档里的祭坛没有队列记录，靠结构起始点现场找回中心。 */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Structure altar = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ALTAR_KEY);
        if (altar == null) return;
        // 祭坛结构的起始点就存在它中心所在的区块里；直接读本区块的起始点，
        // 不用探针（区块刚加载时高度图还没预热，查高度会刷 Unprimed heightmap 报错）。
        StructureStart start = event.getChunk().getStartForStructure(altar);
        if (start == null || !start.isValid()) return;
        get(level).add(centerOf(start.getBoundingBox()));
    }

    /**
     * 从结构包围盒换算祭坛中心（平台面高度）。
     *
     * <p>构件的包围盒由中心 ±7 / y-4..y+8 生成，这里做同一个换算的逆运算，
     * 保证队列路径（生成时直接拿中心坐标）和补录路径登记到同一个点。</p>
     */
    static BlockPos centerOf(BoundingBox box) {
        return new BlockPos((box.minX() + box.maxX()) / 2, box.minY() + 4, (box.minZ() + box.maxZ()) / 2);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos altar : altars) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", altar.getX());
            entry.putInt("y", altar.getY());
            entry.putInt("z", altar.getZ());
            list.add(entry);
        }
        tag.put("altars", list);
        return tag;
    }

    private static AncientAltarData load(CompoundTag tag, HolderLookup.Provider registries) {
        AncientAltarData data = new AncientAltarData();
        ListTag list = tag.getList("altars", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.altars.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
        }
        return data;
    }
}
