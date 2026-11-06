package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 魔晶铁砧的魔素价格同步包。
 *
 * <p>客户端拿到的菜单是原版 {@code AnvilMenu}（菜单类型就是原版铁砧），它的同步槽里只有
 * 经验价格，没有魔素价格；所以服务端每次重算结果时把「要多少魔素 + 现在付不付得起」
 * 单独推过来，界面据此显示数字与红/绿颜色。</p>
 */
public record AnvilManaPayload(int cost, boolean affordable, boolean renameOnly) implements CustomPacketPayload {
    public static final Type<AnvilManaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "anvil_mana"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnvilManaPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.cost());
                buf.writeBoolean(payload.affordable());
                buf.writeBoolean(payload.renameOnly());
            },
            buf -> new AnvilManaPayload(buf.readVarInt(), buf.readBoolean(), buf.readBoolean())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientAnvilState.update(payload.cost(), payload.affordable(),
                        payload.renameOnly())));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
