package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 魔蚀层数（服务端 → 客户端，只发给带着魔蚀的那个玩家自己）。
 *
 * <p>层数上限是 {@link MagicErosionEffect#MAX_LAYERS}，比原版状态等级能装的 256 级大得多，
 * 所以层数单独存在实体持久数据里、单独同步；客户端拿它只做一件事：
 * 把「侵蚀层数 / 深渊阶层 / 下一阶层还差几层」写进状态图标的悬停说明。
 * 伤害结算永远在服务端按服务端自己的层数算，客户端改不了任何数值。</p>
 */
public record ErosionDepthPayload(int layers) implements CustomPacketPayload {
    public static final Type<ErosionDepthPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "erosion_depth"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ErosionDepthPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.layers()),
            buf -> new ErosionDepthPayload(buf.readVarInt())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientErosionState.set(payload.layers())));
    }

    public static void send(ServerPlayer player, int layers) {
        PacketDistributor.sendToPlayer(player, new ErosionDepthPayload(layers));
    }

    /** 上线补一份：状态可能是上次存档里带过来的，客户端缓存是新的。 */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player, MagicErosionEffect.layersOf(player));
        }
    }

    /**
     * 重生 / 换身体：旧的层数跟着旧身体走，新身体一律重新数。
     *
     * <p>死亡时魔蚀本来就被清掉了，这里发 0 只是把客户端的缓存一起抹掉，
     * 免得下一次中招的悬停说明还留着上一辈子的层数。</p>
     */
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player, MagicErosionEffect.layersOf(player));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
