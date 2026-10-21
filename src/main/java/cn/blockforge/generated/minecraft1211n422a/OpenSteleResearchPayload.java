package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record OpenSteleResearchPayload(BlockPos pos, int uses, int manaCost, int cooldownTicks,
                                       String statusKey, String rewardName, int rewardCount)
        implements CustomPacketPayload {
    public static final Type<OpenSteleResearchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "open_stele_research"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSteleResearchPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.uses());
                buf.writeVarInt(payload.manaCost());
                buf.writeVarInt(payload.cooldownTicks());
                buf.writeUtf(payload.statusKey(), 128);
                buf.writeUtf(payload.rewardName(), 256);
                buf.writeVarInt(payload.rewardCount());
            },
            buf -> new OpenSteleResearchPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readUtf(128), buf.readUtf(256), buf.readVarInt())
    );

    public static OpenSteleResearchPayload open(BlockPos pos, SteleResearchData.Snapshot snapshot) {
        return new OpenSteleResearchPayload(pos, snapshot.uses(), snapshot.manaCost(), snapshot.cooldownTicks(), "", "", 0);
    }

    public static OpenSteleResearchPayload result(BlockPos pos, SteleResearchData.ResearchResult result) {
        SteleResearchData.Snapshot snapshot = result.snapshot();
        return new OpenSteleResearchPayload(pos, snapshot.uses(), snapshot.manaCost(), snapshot.cooldownTicks(),
                result.statusKey(), result.rewardName().getString(), result.rewardCount());
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientStelePayloadHandler.openOrUpdate(payload);
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
