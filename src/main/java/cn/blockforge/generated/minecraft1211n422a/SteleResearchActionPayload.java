package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SteleResearchActionPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<SteleResearchActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "stele_research_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SteleResearchActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.pos()),
            buf -> new SteleResearchActionPayload(buf.readBlockPos())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = payload.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0
                    || !player.level().getBlockState(pos).is(GeneratedMod.MOD_BLOCKS.get("ancient_magic_stele").get())) {
                return;
            }
            SteleResearchData.ResearchResult result = SteleResearchData.research(player);
            if (result.success()) {
                MagicProgression.awardAdvancement(player, "first_stele_research");
                player.serverLevel().sendParticles(ParticleTypes.ENCHANT,
                        pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5,
                        48, 0.55, 0.8, 0.55, 0.08);
                player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL,
                        pos.getX() + 0.5, pos.getY() + 1.45, pos.getZ() + 0.5,
                        24, 0.35, 0.65, 0.35, 0.03);
            }
            PacketDistributor.sendToPlayer(player, OpenSteleResearchPayload.result(pos, result));
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
