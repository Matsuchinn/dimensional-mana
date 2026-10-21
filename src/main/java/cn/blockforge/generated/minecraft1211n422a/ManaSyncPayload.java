package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ManaSyncPayload(double mana, double maxMana, int aura, int maxAura, boolean exhausted,
                              double overchargeSafeRatio) implements CustomPacketPayload {
    public static final Type<ManaSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "mana_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManaSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.mana());
                buf.writeDouble(payload.maxMana());
                buf.writeVarInt(payload.aura());
                buf.writeVarInt(payload.maxAura());
                buf.writeBoolean(payload.exhausted());
                buf.writeDouble(payload.overchargeSafeRatio());
            },
            buf -> new ManaSyncPayload(buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readDouble())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientManaState.update(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
