package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 绘制台基座：只记住自己属于哪个阵盘，真正的阵图存在
 * {@link MagicCircleNetworks} 的维度存档里。
 */
public final class MagicCircleBaseBlockEntity extends BlockEntity {
    private static final String NETWORK_TAG = "MagicCircleNetwork";

    private String networkId = "";

    public MagicCircleBaseBlockEntity(BlockPos pos, BlockState state) {
        super(GeneratedMod.MAGIC_CIRCLE_BASE_BLOCK_ENTITY.get(), pos, state);
    }

    public String networkId() { return networkId; }

    public void setNetworkId(String networkId) {
        String next = networkId == null ? "" : networkId;
        if (next.equals(this.networkId)) return;
        this.networkId = next;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!networkId.isEmpty()) tag.putString(NETWORK_TAG, networkId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        networkId = tag.getString(NETWORK_TAG);
    }
}
