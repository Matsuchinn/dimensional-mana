package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 招架结果的屏幕反馈：普通 / 完美 / 法术弹反 / 斗气不足失败。
 * 粒子与音效由服务端按位置广播，这里只把「画在谁屏幕上」的那一层送到当事人客户端。
 *
 * <p>{@code entityId} 只在完美招架时有用：客户端拿它去追被弹开的那只怪物，在它身上贴一段
 * 4 帧闪光；实体已经消失就退回 {@code x/y/z} 坐标，闪光照样画在原地。</p>
 */
public record ParryFeedbackPayload(int kind, double x, double y, double z, int entityId)
        implements CustomPacketPayload {
    public static final int NORMAL = 0, PERFECT = 1, PROJECTILE = 2, FAIL = 3;

    public static final Type<ParryFeedbackPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "parry_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ParryFeedbackPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.kind);
                buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
                buf.writeVarInt(value.entityId);
            },
            buf -> new ParryFeedbackPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readVarInt()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientParryVisuals.accept(payload)));
    }

    public static void send(ServerPlayer player, int kind, Vec3 at) {
        send(player, kind, at, -1);
    }

    /** 完美招架专用：带上被打中那只怪物的实体 id，客户端好在它身上画闪光。 */
    public static void sendFlash(ServerPlayer player, Vec3 at, int entityId) {
        send(player, PERFECT, at, entityId);
    }

    private static void send(ServerPlayer player, int kind, Vec3 at, int entityId) {
        PacketDistributor.sendToPlayer(player, new ParryFeedbackPayload(kind,
                at == null ? player.getX() : at.x, at == null ? player.getY() : at.y,
                at == null ? player.getZ() : at.z, entityId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
