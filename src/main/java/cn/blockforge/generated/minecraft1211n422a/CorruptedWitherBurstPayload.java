package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 【虚无反噬】聚合体命中的爆炸同步包（服务端 → 客户端，命中那一刻发一次）。
 *
 * <p>只带「炸在哪、地面在哪、是红还是蓝」四样东西：伤害和实体搜索全部在服务端
 * 那一 tick 算完（见 {@link CorruptedWitherAggregate#detonate}），客户端只负责放特效，
 * 渲染线程里不做任何 AABB 判定。</p>
 *
 * <p>地面高度由服务端提前射线求出来一起发过来，客户端画裂纹环时就不必再查世界。</p>
 */
public record CorruptedWitherBurstPayload(Vec3 position, float groundY, boolean blue)
        implements CustomPacketPayload {
    public static final Type<CorruptedWitherBurstPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "corrupted_wither_burst"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CorruptedWitherBurstPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVec3(payload.position());
                        buf.writeFloat(payload.groundY());
                        buf.writeBoolean(payload.blue());
                    },
                    buf -> new CorruptedWitherBurstPayload(buf.readVec3(), buf.readFloat(), buf.readBoolean()));

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> CorruptedWitherBurstFx.accept(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
