package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 魔素感知的扫描结果：把这一次标记到的魔晶矿坐标与高魔素生物 UUID 发给施法者。
 *
 * <p>原版发光效果只在实体本身露在画面里时才描边，隔着一层石头就看不见；客户端拿到这份
 * 列表后自己画一层不做深度测试的线框，才能做到“隔着方块看到外轮廓”。</p>
 */
public record ManaSensePayload(List<BlockPos> ores, List<UUID> mobs, int ticks) implements CustomPacketPayload {
    public static final Type<ManaSensePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "mana_sense"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManaSensePayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.ores.size());
                for (BlockPos pos : value.ores) buf.writeBlockPos(pos);
                buf.writeVarInt(value.mobs.size());
                for (UUID id : value.mobs) buf.writeUUID(id);
                buf.writeVarInt(value.ticks);
            },
            buf -> {
                int oreCount = buf.readVarInt();
                List<BlockPos> ores = new ArrayList<>(oreCount);
                for (int i = 0; i < oreCount; i++) ores.add(buf.readBlockPos());
                int mobCount = buf.readVarInt();
                List<UUID> mobs = new ArrayList<>(mobCount);
                for (int i = 0; i < mobCount; i++) mobs.add(buf.readUUID());
                return new ManaSensePayload(ores, mobs, buf.readVarInt());
            });

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientManaSenseState.accept(payload)));
    }

    public static void send(ServerPlayer player, List<BlockPos> ores, List<UUID> mobs, int ticks) {
        PacketDistributor.sendToPlayer(player, new ManaSensePayload(List.copyOf(ores), List.copyOf(mobs), ticks));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
