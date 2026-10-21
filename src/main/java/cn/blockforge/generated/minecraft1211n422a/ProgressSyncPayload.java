package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ProgressSyncPayload(int magicLevel, long magicExperience, int requiredExperience,
                                  int skillPoints, int spentSkillPoints, int[] skillLevels,
                                  double affinity, double concentration, int dailyGrowths) implements CustomPacketPayload {
    public static final Type<ProgressSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "progress_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.magicLevel());
                buf.writeVarLong(payload.magicExperience());
                buf.writeVarInt(payload.requiredExperience());
                buf.writeVarInt(payload.skillPoints());
                buf.writeVarInt(payload.spentSkillPoints());
                buf.writeVarInt(payload.skillLevels().length);
                for (int value : payload.skillLevels()) buf.writeVarInt(value);
                buf.writeDouble(payload.affinity());
                buf.writeDouble(payload.concentration());
                buf.writeVarInt(payload.dailyGrowths());
            },
            buf -> {
                int level = buf.readVarInt();
                long experience = buf.readVarLong();
                int required = buf.readVarInt();
                int points = buf.readVarInt();
                int spent = buf.readVarInt();
                int length = Math.min(32, buf.readVarInt());
                int[] skills = new int[length];
                for (int i = 0; i < length; i++) skills[i] = buf.readVarInt();
                return new ProgressSyncPayload(level, experience, required, points, spent, skills,
                        buf.readDouble(), buf.readDouble(), buf.readVarInt());
            }
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientProgressState.update(payload)));
    }

    public static ProgressSyncPayload from(ServerPlayer player) {
        MagicProgression.Snapshot progress = MagicProgression.get(player);
        ManaData.Snapshot mana = ManaData.get(player);
        double concentration = player.level() instanceof net.minecraft.server.level.ServerLevel level
                ? ManaConcentration.at(level, player.blockPosition()) : 0.0;
        return new ProgressSyncPayload(progress.magicLevel(), progress.magicExperience(),
                MagicProgression.requirementForNextLevel(progress.magicLevel()), progress.skillPoints(),
                progress.spentSkillPoints(), progress.skills().clone(),
                mana.affinity() * MagicEquipment.affinityMultiplier(player), concentration,
                progress.dailyExhaustionGrowths());
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, from(player));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
