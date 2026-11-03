package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * 古代魔法祭坛的光晕同步包：服务端把玩家附近（约 96 格内）已登记的祭坛中心
 * 整表推给客户端，{@link ClientAltarAuraState} 据此在地面上画出
 * "从祭坛中央到影响范围边界"的蓝色魔素光晕。
 *
 * <p>只在玩家靠近祭坛期间每 100 刻发一次；客户端有超时清理，
 * 走远或换维度后画面不会留残影。</p>
 */
public record AltarAuraPayload(List<BlockPos> altars) implements CustomPacketPayload {
    public static final Type<AltarAuraPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "altar_aura"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AltarAuraPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.altars().size());
                for (BlockPos pos : payload.altars()) buf.writeBlockPos(pos);
            },
            buf -> {
                int count = buf.readVarInt();
                List<BlockPos> altars = new ArrayList<>(count);
                for (int i = 0; i < count; i++) altars.add(buf.readBlockPos());
                return new AltarAuraPayload(altars);
            });

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientAltarAuraState.accept(payload)));
    }

    public static void send(ServerLevel level, ServerPlayer player, List<BlockPos> altars) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new AltarAuraPayload(altars));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
