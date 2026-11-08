package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 武备加工台：防手滑保护的开关状态（服务端 → 客户端）。
 *
 * <p>只有服务端说了算。客户端拿它决定「锻造」按钮灰不灰、侧边的锁定提示亮不亮；
 * 即使客户端画面被改，服务端在 {@link WeaponWorkbenchMenu#clickMenuButton} 里
 * 照样会把命中目标词条之后的锻造请求全部挡掉。</p>
 */
public record WorkbenchGuardPayload(int containerId, boolean active) implements CustomPacketPayload {
    public static final Type<WorkbenchGuardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "workbench_guard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkbenchGuardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.containerId());
                buf.writeBoolean(payload.active());
            },
            buf -> new WorkbenchGuardPayload(buf.readVarInt(), buf.readBoolean())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientWorkbenchGuardState.set(payload.containerId(), payload.active())));
    }

    public static void send(ServerPlayer player, int containerId, boolean active) {
        PacketDistributor.sendToPlayer(player, new WorkbenchGuardPayload(containerId, active));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
