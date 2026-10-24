package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 斗气进阶的同步包：等级、经验、斗气技能点、六项技能等级、已解锁的三项能力与快捷盘。
 *
 * <p>和魔法进度一样每秒推一次，升级 / 加点 / 换槽时再立即补发一次，
 * 所以打开战斗手册看到的一定是最新数值。</p>
 */
public record QiSyncPayload(int level, long experience, int requiredExperience, int skillPoints,
                            int spentSkillPoints, int[] skills, boolean[] abilities,
                            String[] slots, int selectedSlot) implements CustomPacketPayload {
    public static final Type<QiSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "qi_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QiSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.level);
                buf.writeVarLong(payload.experience);
                buf.writeVarInt(payload.requiredExperience);
                buf.writeVarInt(payload.skillPoints);
                buf.writeVarInt(payload.spentSkillPoints);
                buf.writeVarInt(payload.skills.length);
                for (int value : payload.skills) buf.writeVarInt(value);
                buf.writeVarInt(payload.abilities.length);
                for (boolean value : payload.abilities) buf.writeBoolean(value);
                buf.writeVarInt(payload.slots.length);
                for (String slot : payload.slots) buf.writeUtf(slot == null ? "" : slot, 32);
                buf.writeVarInt(payload.selectedSlot);
            },
            buf -> {
                int level = buf.readVarInt();
                long experience = buf.readVarLong();
                int required = buf.readVarInt();
                int points = buf.readVarInt();
                int spent = buf.readVarInt();
                int skillLength = Math.min(32, buf.readVarInt());
                int[] skills = new int[skillLength];
                for (int i = 0; i < skillLength; i++) skills[i] = buf.readVarInt();
                int abilityLength = Math.min(32, buf.readVarInt());
                boolean[] abilities = new boolean[abilityLength];
                for (int i = 0; i < abilityLength; i++) abilities[i] = buf.readBoolean();
                int slotLength = Math.min(32, buf.readVarInt());
                String[] slots = new String[slotLength];
                for (int i = 0; i < slotLength; i++) slots[i] = buf.readUtf(32);
                return new QiSyncPayload(level, experience, required, points, spent, skills, abilities,
                        slots, buf.readVarInt());
            }
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientQiState.update(payload)));
    }

    public static QiSyncPayload from(ServerPlayer player) {
        QiProgression.Snapshot data = QiProgression.get(player);
        return new QiSyncPayload(data.level(), data.experience(),
                QiProgression.requirementForNextLevel(data.level()), data.skillPoints(),
                data.spentSkillPoints(), data.skills().clone(), data.abilities().clone(),
                data.slots().clone(), data.selectedSlot());
    }

    public static void send(ServerPlayer player) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, from(player));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
