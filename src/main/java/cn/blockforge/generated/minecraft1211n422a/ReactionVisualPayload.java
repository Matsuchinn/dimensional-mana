package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 把反应名和服务器确认的头顶坐标同步给附近客户端，目标消失后也能完成跳字动画。 */
public record ReactionVisualPayload(int entityId, String reactionId, int color,
                                    double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ReactionVisualPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "reaction_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReactionVisualPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId);
                buf.writeUtf(value.reactionId, 24);
                buf.writeInt(value.color);
                buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
            }, buf -> new ReactionVisualPayload(buf.readVarInt(), buf.readUtf(24), buf.readInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientReactionText.accept(payload)));
    }

    public static void send(LivingEntity target, Reaction reaction) {
        Vec3 head = target.position().add(0, target.getBbHeight(), 0);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                new ReactionVisualPayload(target.getId(), reaction.id(), reaction.color(), head.x, head.y, head.z));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
