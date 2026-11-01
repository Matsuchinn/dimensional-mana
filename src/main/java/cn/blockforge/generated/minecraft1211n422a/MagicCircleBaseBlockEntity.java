package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 绘制台基座：只记住自己属于哪个阵盘，真正的阵图存在
 * {@link MagicCircleNetworks} 的维度存档里。
 *
 * <p>渲染相关的两件事也挂在这里（性能设计，对应「别在 render 里算」）：</p>
 * <ul>
 *   <li>{@link #renderPacket()} 是服务端预先摊平好的法阵渲染数据包，由
 *       {@link MagicCircleRenderSync} 在阵图变化时写进<b>原点基座</b>（西北角那块）并广播；
 *       客户端渲染器每帧只读这个对象，零遍历零换算。</li>
 *   <li>{@link #getRenderBoundingBox()} 按画布尺寸放大，让整片悬空法阵都在视锥剔除的
 *       判定框里，远处不会出现「转着转着整阵消失」。</li>
 * </ul>
 */
public final class MagicCircleBaseBlockEntity extends BlockEntity {
    private static final String NETWORK_TAG = "MagicCircleNetwork";
    private static final String RENDER_TAG = "MagicCircleRender";

    private String networkId = "";
    /** 渲染数据包：只在原点基座上非空；客户端读、服务端写。 */
    private MagicCircleRenderPacket renderPacket = MagicCircleRenderPacket.EMPTY;
    /** 缓存的渲染包围盒，尺寸变了才重算。 */
    private AABB renderBox;

    public MagicCircleBaseBlockEntity(BlockPos pos, BlockState state) {
        super(GeneratedMod.MAGIC_CIRCLE_BASE_BLOCK_ENTITY.get(), pos, state);
    }

    public String networkId() { return networkId; }

    public void setNetworkId(String networkId) {
        String next = networkId == null ? "" : networkId;
        if (next.equals(this.networkId)) return;
        this.networkId = next;
        if (next.isEmpty()) renderPacket = MagicCircleRenderPacket.EMPTY; // 退阵顺手清画面
        setChanged();
    }

    /** 客户端渲染入口：拿现成的包直接画，不做任何计算。 */
    public MagicCircleRenderPacket renderPacket() { return renderPacket; }

    /** 服务端：写入渲染包并广播给周围客户端（{@link MagicCircleRenderSync} 变化时才调）。 */
    public void publishRenderPacket(MagicCircleRenderPacket packet) {
        if (level != null && level.isClientSide) return;
        MagicCircleRenderPacket next = packet == null ? MagicCircleRenderPacket.EMPTY : packet;
        renderPacket = next;
        renderBox = null;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /** 整片悬空法阵的渲染包围盒（渲染器做视锥剔除与 NeoForge 扩展盒都用它）。 */
    public AABB renderBounds() {
        AABB box = renderBox;
        if (box == null) {
            int w = Math.max(1, renderPacket.baseW);
            int h = Math.max(1, renderPacket.baseH);
            // 悬空最高约 0.2 + 7 层 ×0.055 < 0.6，再放一点余量给辉光
            box = new AABB(worldPosition.getX(), worldPosition.getY() - 1.0, worldPosition.getZ(),
                    worldPosition.getX() + w + 1.0, worldPosition.getY() + 2.5, worldPosition.getZ() + h + 1.0);
            renderBox = box;
        }
        return box;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!networkId.isEmpty()) tag.putString(NETWORK_TAG, networkId);
        if (!renderPacket.isEmpty()) tag.put(RENDER_TAG, renderPacket.toTag());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        networkId = tag.getString(NETWORK_TAG);
        renderPacket = tag.contains(RENDER_TAG)
                ? MagicCircleRenderPacket.fromTag(tag.get(RENDER_TAG))
                : MagicCircleRenderPacket.EMPTY;
        renderBox = null;
    }

    /** 客户端要整份渲染数据，默认 updateTag 已含 saveAdditional，这里只补一句语义注释。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
