package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ElementTalentSyncPayload(boolean chosen, int selectedMask, int unlockedMask, int[] invested)
        implements CustomPacketPayload {
    public static final Type<ElementTalentSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "element_talent_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ElementTalentSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.chosen);
                buf.writeVarInt(value.selectedMask);
                buf.writeVarInt(value.unlockedMask);
                buf.writeVarInt(value.invested.length);
                for (int count : value.invested) buf.writeVarInt(count);
            },
            buf -> {
                boolean chosen = buf.readBoolean();
                int selected = buf.readVarInt();
                int unlocked = buf.readVarInt();
                int length = Math.min(ElementType.values().length, buf.readVarInt());
                int[] invested = new int[length];
                for (int i = 0; i < length; i++) invested[i] = buf.readVarInt();
                return new ElementTalentSyncPayload(chosen, selected, unlocked, invested);
            });

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientElementTalentState.update(payload)));
    }

    public static void send(ServerPlayer player) {
        ElementTalentData.Snapshot data = ElementTalentData.get(player);
        int selected = 0;
        int unlocked = 0;
        for (ElementType element : ElementType.values()) {
            if (data.isSelected(element)) selected |= 1 << element.ordinal();
            if (data.isUnlocked(element)) unlocked |= 1 << element.ordinal();
        }
        PacketDistributor.sendToPlayer(player, new ElementTalentSyncPayload(data.chosen(), selected, unlocked,
                data.investedFragments().clone()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
