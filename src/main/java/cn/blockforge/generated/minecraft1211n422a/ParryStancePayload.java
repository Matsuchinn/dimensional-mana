package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 招架姿态的同步包：谁摆出了举刀架势、谁收刀了。
 *
 * <p>姿态本身只在服务端判定，但「举刀」这个动作别的玩家也要看得见，所以进出姿态时
 * 广播给同一个世界里能看到这个玩家的客户端（含他自己，他那边按按键优先，会忽略这条）。</p>
 */
public record ParryStancePayload(int entityId, boolean active) implements CustomPacketPayload {
    public static final Type<ParryStancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "parry_stance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ParryStancePayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId);
                buf.writeBoolean(value.active);
            },
            buf -> new ParryStancePayload(buf.readVarInt(), buf.readBoolean()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientParryVisuals.acceptStance(payload.entityId(), payload.active())));
    }

    /** 进出姿态各发一次，只发给看得见这个玩家的人。 */
    public static void send(Player player, boolean active) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ParryStancePayload(player.getId(), active));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
