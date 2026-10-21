package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 魔力通透·共鸣核心的只读快照：充能值、当前元素、共鸣剩余刻数。 */
public record ResonanceCoreSyncPayload(double charge, String element, int resonanceTicks,
                                       boolean ready, boolean equipped) implements CustomPacketPayload {
    public static final Type<ResonanceCoreSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "resonance_core_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResonanceCoreSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.charge());
                buf.writeUtf(payload.element(), 32);
                buf.writeVarInt(payload.resonanceTicks());
                buf.writeBoolean(payload.ready());
                buf.writeBoolean(payload.equipped());
            },
            buf -> new ResonanceCoreSyncPayload(buf.readDouble(), buf.readUtf(32), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientResonanceCoreState.update(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
