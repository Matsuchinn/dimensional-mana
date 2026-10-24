package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record AppraisalSyncPayload(boolean glassesActive, double concentration, int entityId, String targetName,
                                   double health, double maxHealth, double mana, double maxMana,
                                   int battleQi, int maxBattleQi) implements CustomPacketPayload {
    public static final Type<AppraisalSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "appraisal_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AppraisalSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.glassesActive());
                buf.writeDouble(payload.concentration());
                buf.writeVarInt(payload.entityId());
                buf.writeUtf(payload.targetName());
                buf.writeDouble(payload.health());
                buf.writeDouble(payload.maxHealth());
                buf.writeDouble(payload.mana());
                buf.writeDouble(payload.maxMana());
                buf.writeVarInt(payload.battleQi());
                buf.writeVarInt(payload.maxBattleQi());
            },
            buf -> new AppraisalSyncPayload(buf.readBoolean(), buf.readDouble(), buf.readVarInt(), buf.readUtf(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readVarInt(), buf.readVarInt())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientAppraisalState.update(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
