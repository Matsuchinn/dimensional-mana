package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 服务端 → 客户端：这一次「法阵码导入」的结果。
 *
 * <p>以前导入失败只往聊天栏甩一行红字，面板已经关掉了，玩家既看不到原因、也丢了刚才粘进来的码，
 * 只能回聊天栏重新复制一遍。现在结果直接回给还开着的面板：成功就显示绿字并自动关掉，
 * 失败就显示红字、面板留着、码也留着，换一张容量够的载体再按一次回车即可。</p>
 *
 * <p>消息本身是「未翻译的 {@link Component}」，到客户端才按玩家自己的语言渲染。</p>
 */
public record MagicCircleCodeResultPayload(boolean ok, Component message) implements CustomPacketPayload {

    public static final Type<MagicCircleCodeResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "magic_circle_code_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MagicCircleCodeResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.ok());
                        ComponentSerialization.STREAM_CODEC.encode(buf, payload.message());
                    },
                    buf -> new MagicCircleCodeResultPayload(buf.readBoolean(),
                            ComponentSerialization.STREAM_CODEC.decode(buf))
            );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientMagicCirclePayloadHandler.codeResult(payload);
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
