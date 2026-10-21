package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 通知附近客户端：这个生物进入了“冰晶状态”，身上要套冰壳。 */
public record FrozenVisualPayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final Type<FrozenVisualPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "frozen_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FrozenVisualPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId);
                buf.writeVarInt(value.ticks);
            }, buf -> new FrozenVisualPayload(buf.readVarInt(), buf.readVarInt()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientFrozenVisuals.accept(payload)));
    }

    public static void send(LivingEntity target, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new FrozenVisualPayload(target.getId(), ticks));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
