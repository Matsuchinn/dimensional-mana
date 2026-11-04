package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 魔蚀凋零的屏幕空间特效同步包（服务端 → 客户端，每 5 刻一次）。
 *
 * <p>只带三个数：蓄力进度、镜头抖动强度、是否在降腐化雨。客户端
 * {@link CorruptedWitherClientFx} 据此画紫色暗角、抖镜头、下雨；两秒收不到包就自动清屏，
 * 所以 Boss 死了、玩家跑远了都不会留残影。</p>
 */
public record CorruptedWitherFxPayload(float charge, float shake, boolean rain) implements CustomPacketPayload {
    public static final Type<CorruptedWitherFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "corrupted_wither_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CorruptedWitherFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeFloat(payload.charge());
                buf.writeFloat(payload.shake());
                buf.writeBoolean(payload.rain());
            },
            buf -> new CorruptedWitherFxPayload(buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> CorruptedWitherClientFx.accept(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
