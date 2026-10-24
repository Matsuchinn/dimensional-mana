package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力雷达的扫描结果：范围内所有怪物 + 它们该显示的元素轮廓颜色。
 *
 * <p>颜色取「最明显的易伤元素」，没有易伤就取「最明显的抗性元素」，
 * 都没有则用默认紫色。{@code vulnerable} 为 true 的目标，客户端会让轮廓闪烁，
 * 混战里一眼就能挑出该用哪个元素招呼。</p>
 */
public record ManaRadarPayload(List<Target> targets, int ticks) implements CustomPacketPayload {
    /** 单个雷达目标：实体 id、24 位 RGB、是否闪烁。 */
    public record Target(int entityId, int color, boolean vulnerable) { }

    public static final Type<ManaRadarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "mana_radar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManaRadarPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.targets().size());
                for (Target target : payload.targets()) {
                    buf.writeVarInt(target.entityId());
                    buf.writeVarInt(target.color() & 0xFFFFFF);
                    buf.writeBoolean(target.vulnerable());
                }
                buf.writeVarInt(payload.ticks());
            },
            buf -> {
                int count = buf.readVarInt();
                List<Target> targets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    targets.add(new Target(buf.readVarInt(), 0xFF000000 | buf.readVarInt(), buf.readBoolean()));
                }
                return new ManaRadarPayload(targets, buf.readVarInt());
            });

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientManaRadarState.accept(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
