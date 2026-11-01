package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 魔蚀蛛网（CorruptedWebBlock）：魔蚀织网者吐出来的魔晶化蛛网。
 *
 * <p>行为接近原版蛛网（{@code makeStuckInBlock} 同款减速），额外做这些事：</p>
 * <ul>
 *   <li>碰到的生物每 64 刻被附加一次【魔蚀】；自己的网不咬织网者；</li>
 *   <li>织网者吐出的网 30 秒（600 刻）后自动消散；</li>
 *   <li>碰到水立刻消失；被光系法术波及立刻消失（见 {@link #dissolveNearLight}）；</li>
 *   <li>玩家拿剑砍 3 下、拿剪刀剪 1 下就破，破掉掉 1 个魔蚀蛛丝
 *       （判定在 {@link CorruptedWeaverEvents#onLeftClickBlock}）；</li>
 *   <li>每只织网者最多同时存在 5 张网，超出时先抹掉最早那张。</li>
 * </ul>
 *
 * <p>落点安全：生成前一律走 {@code canBeReplaced()}，基岩、箱子这类不可替换的方块
 * 永远不会被网顶掉。</p>
 */
public final class CorruptedWebBlock extends Block {
    /** 方块 id。 */
    public static final String BLOCK_ID = "corrupted_web";
    /** 织网者吐出的网存在 600 刻（30 秒）。 */
    public static final int LIFETIME_TICKS = 600;
    /** 每只织网者最多同时存在 5 张网。 */
    public static final int MAX_PER_WEAVER = 5;
    /** 实体踩在网里每 64 刻被咬一次魔蚀。 */
    public static final int EROSION_INTERVAL = 64;
    /** 检查水 / 光 / 到期的频率：20 刻（1 秒）。 */
    private static final int CHECK_INTERVAL = 20;
    /** 光系法术波及半径（格）。 */
    private static final double LIGHT_DISSOLVE_RADIUS = 4.0D;

    private static final DustParticleOptions WEB_DUST =
            new DustParticleOptions(new Vector3f(0.62F, 0.28F, 0.85F), 0.9F);
    /** 实体身上记录「下一次被网咬」的游戏刻。 */
    private static final String EROSION_NEXT_TAG = "DmWeaverWebErosionNext";

    /** 每张网的服务端记录：主人、到期时刻、已挨了几剑。 */
    private static final class WebRecord {
        final UUID owner;
        final long expireAt;
        int swordHits;

        WebRecord(UUID owner, long expireAt) {
            this.owner = owner;
            this.expireAt = expireAt;
        }
    }

    /** 按维度分桶的网记录。只在服务端主线程访问。 */
    private static final Map<ResourceKey<Level>, Map<BlockPos, WebRecord>> TRACKERS = new HashMap<>();

    public CorruptedWebBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(CorruptedWebBlock::new);
    }

    private static Map<BlockPos, WebRecord> tracker(Level level) {
        return TRACKERS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
    }

    // ---------------------------------------------------------------- 生成 / 清理

    /**
     * 在落点铺一张网。
     *
     * @param owner 织网者的 UUID；{@code null} 表示玩家手动放置（不限数量、不会过期）。
     * @return 是否真的铺下去了。
     */
    public static boolean place(ServerLevel level, BlockPos pos, UUID owner) {
        BlockPos at = pos.immutable();
        BlockState existing = level.getBlockState(at);
        // 防坑：不可替换的方块（基岩、箱子、已经存在的网）一律不动。
        if (!existing.canBeReplaced()) return false;
        if (existing.getBlock() instanceof CorruptedWebBlock) return false;
        Map<BlockPos, WebRecord> records = tracker(level);
        if (owner != null) {
            prune(level, records);
            // 数量上限：先抹掉一张已有的，再铺新的（30 秒后本来也会自己消失）。
            while (countOwner(records, owner) >= MAX_PER_WEAVER) {
                BlockPos oldest = null;
                for (Map.Entry<BlockPos, WebRecord> entry : records.entrySet()) {
                    if (!owner.equals(entry.getValue().owner)) continue;
                    oldest = entry.getKey();
                    break;
                }
                if (oldest == null) break;
                dissolve(level, oldest, false);
            }
        }
        BlockState placedState = GeneratedMod.MOD_BLOCKS.get(BLOCK_ID).get().defaultBlockState();
        level.setBlock(at, placedState, Block.UPDATE_ALL);
        if (!(level.getBlockState(at).getBlock() instanceof CorruptedWebBlock)) return false;
        records.put(at, new WebRecord(owner, owner == null ? 0L : level.getGameTime() + LIFETIME_TICKS));
        level.scheduleTick(at, level.getBlockState(at).getBlock(), CHECK_INTERVAL);
        level.playSound(null, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D,
                SoundEvents.COBWEB_PLACE, SoundSource.HOSTILE, 0.9F, 0.75F);
        return true;
    }

    /** 某种子还没消散的网还剩几张。 */
    public static int countOwner(Level level, UUID owner) {
        if (owner == null || level.isClientSide) return 0;
        Map<BlockPos, WebRecord> records = tracker(level);
        prune(level, records);
        return countOwner(records, owner);
    }

    private static int countOwner(Map<BlockPos, WebRecord> records, UUID owner) {
        int count = 0;
        for (WebRecord record : records.values()) if (owner.equals(record.owner)) count++;
        return count;
    }

    /** 织网者死亡：把它留下的网全部抹掉。 */
    public static void clearOwner(ServerLevel level, UUID owner) {
        if (owner == null) return;
        Map<BlockPos, WebRecord> records = tracker(level);
        List<BlockPos> owned = new ArrayList<>();
        for (Map.Entry<BlockPos, WebRecord> entry : records.entrySet()) {
            if (owner.equals(entry.getValue().owner)) owned.add(entry.getKey());
        }
        for (BlockPos pos : owned) dissolve(level, pos, false);
    }

    /** 光系法术波及：半径内的网直接消散。 */
    public static void dissolveNearLight(Level level, Vec3 center, double radius) {
        if (level.isClientSide) return;
        Map<BlockPos, WebRecord> records = tracker(level);
        List<BlockPos> hit = new ArrayList<>();
        for (BlockPos pos : records.keySet()) {
            if (pos.getCenter().distanceToSqr(center) <= radius * radius) hit.add(pos);
        }
        for (BlockPos pos : hit) dissolve(level, pos, false);
    }

    /** 把网换成空气；{@code dropSilk} 为真时掉 1 个魔蚀蛛丝。 */
    public static void dissolve(Level level, BlockPos pos, boolean dropSilk) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CorruptedWebBlock)) {
            tracker(level).remove(pos);
            return;
        }
        if (dropSilk && !level.isClientSide) {
            Block.popResource(level, pos, new ItemStack(GeneratedMod.MOD_ITEMS.get(GeneratedMod.CORRUPTED_SILK_ID).get()));
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(WEB_DUST, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    6, 0.25D, 0.25D, 0.25D, 0.01D);
        }
        tracker(level).remove(pos);
        level.removeBlock(pos, false);
    }

    /** 清掉记录里已经不存在的网。 */
    private static void prune(Level level, Map<BlockPos, WebRecord> records) {
        Iterator<Map.Entry<BlockPos, WebRecord>> it = records.entrySet().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next().getKey();
            if (!(level.getBlockState(pos).getBlock() instanceof CorruptedWebBlock)) it.remove();
        }
    }

    // ---------------------------------------------------------------- 方块生命周期

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide) return;
        tracker(level).putIfAbsent(pos.immutable(), new WebRecord(null, 0L));
        level.scheduleTick(pos, state.getBlock(), CHECK_INTERVAL);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) tracker(level).remove(pos.immutable());
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 水一旦流到旁边，网立刻化掉（不等下次计划刻）。 */
    /** 网里能走：寻路不会因为这一格是「方块」就绕开，织网者才能自己回到网里。 */
    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return true;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) return;
        if (level.getBlockState(pos).getBlock() instanceof CorruptedWebBlock && touchesWater(level, pos)) {
            dissolve(level, pos, false);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockState(pos).getBlock() instanceof CorruptedWebBlock)) {
            tracker(level).remove(pos.immutable());
            return;
        }
        WebRecord record = tracker(level).get(pos.immutable());
        if (record != null && record.expireAt > 0 && level.getGameTime() >= record.expireAt) {
            dissolve(level, pos, false);
            return;
        }
        if (touchesWater(level, pos)) {
            dissolve(level, pos, false);
            return;
        }
        if (nearSpellLight(level, pos)) {
            dissolve(level, pos, false);
            return;
        }
        level.scheduleTick(pos, state.getBlock(), CHECK_INTERVAL);
    }

    private static boolean touchesWater(Level level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) return true;
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /** 半径 2 格内有没有光系法术留下的临时光源方块。 */
    private static boolean nearSpellLight(Level level, BlockPos pos) {
        for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            Block block = level.getBlockState(probe).getBlock();
            if (block instanceof TemporarySpellLight) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 踩网

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // 织网者自己在网里行动自由；其它生物和原版蛛网一样黏住（水平 25%、垂直 5%）。
        if (entity instanceof CorruptedWeaverEntity) return;
        entity.makeStuckInBlock(state, new Vec3(0.25D, 0.05D, 0.25D));
        if (level.isClientSide || !(entity instanceof LivingEntity living)) return;
        if (!living.isAlive() || living.isSpectator()) return;
        long now = level.getGameTime();
        CompoundTag data = living.getPersistentData();
        if (now < data.getLong(EROSION_NEXT_TAG)) return;
        data.putLong(EROSION_NEXT_TAG, now + EROSION_INTERVAL);
        MagicErosionEffect.apply(living, MagicErosionEffect.DEFAULT_DURATION);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    living.getX(), living.getY() + living.getBbHeight() * 0.6D, living.getZ(),
                    6, 0.25D, 0.3D, 0.25D, 0.0D);
        }
    }

    /** 剑 / 剪刀的破坏结算，由事件调用。 */
    static void hitByTool(ServerLevel level, BlockPos pos, int hits) {
        WebRecord record = tracker(level).get(pos.immutable());
        if (record == null) {
            dissolve(level, pos, true);
            return;
        }
        record.swordHits += hits;
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                SoundEvents.COBWEB_BREAK, SoundSource.PLAYERS, 0.8F, 1.15F);
        if (record.swordHits >= 3) dissolve(level, pos, true);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(7) != 0) return;
        level.addParticle(WEB_DUST,
                pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble() * 0.6D,
                pos.getZ() + random.nextDouble(), 0.0D, 0.002D, 0.0D);
    }
}
