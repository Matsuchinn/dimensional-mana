package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 饰品相关的“纯显示”状态同步，只给客户端拼物品描述用：
 *
 * <ul>
 *   <li>{@code casts}：大师施法手套已经施法几次（0～4）；</li>
 *   <li>{@code orbEnabled}：魔法大师披风的魔素凝聚球开关是否打开。</li>
 * </ul>
 *
 * <p>真正的判定都在服务器端（玩家持久数据），这里只是把结果送过去显示，
 * 客户端就算改了这个数字也不会影响战斗结算。</p>
 */
public record MagicToggleSyncPayload(int casts, boolean orbEnabled) implements CustomPacketPayload {
    public static final Type<MagicToggleSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "magic_toggle_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagicToggleSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.casts());
                buf.writeBoolean(payload.orbEnabled());
            },
            buf -> new MagicToggleSyncPayload(buf.readVarInt(), buf.readBoolean())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientMagicToggleState.update(payload.casts(), payload.orbEnabled())));
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new MagicToggleSyncPayload(
                CastingGloveItem.castCount(player), MagicEquipment.orbEnabled(player)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
