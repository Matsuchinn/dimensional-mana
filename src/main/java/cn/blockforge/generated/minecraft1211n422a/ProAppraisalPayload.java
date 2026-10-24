package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 专业鉴定眼镜的解析结果：一只目标生物的全部可读数据。
 *
 * <p>{@code equipped} 是「有没有戴着眼镜」，{@code active} 是「玩家魔素还够不够解析」。
 * 魔素耗尽时服务端只发名称与生命值，其余字段留空，客户端据此把进阶条目换成
 * 「数据解析失败：魔素不足」，而不是直接关掉整个 HUD。</p>
 */
public record ProAppraisalPayload(
        boolean equipped,
        boolean active,
        double playerMana,
        int entityId,
        String targetName,
        float health,
        float maxHealth,
        double mana,
        double maxMana,
        int battleQi,
        int maxBattleQi,
        double manaRegen,
        double battleQiRegen,
        List<String> resistances,
        List<String> weaknesses,
        String skillId,
        int skillTotalTicks,
        int skillRemainingTicks) implements CustomPacketPayload {

    public static final Type<ProAppraisalPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "pro_appraisal_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProAppraisalPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.equipped());
                buf.writeBoolean(payload.active());
                buf.writeDouble(payload.playerMana());
                buf.writeVarInt(payload.entityId());
                buf.writeUtf(payload.targetName());
                buf.writeFloat(payload.health());
                buf.writeFloat(payload.maxHealth());
                buf.writeDouble(payload.mana());
                buf.writeDouble(payload.maxMana());
                buf.writeVarInt(payload.battleQi());
                buf.writeVarInt(payload.maxBattleQi());
                buf.writeDouble(payload.manaRegen());
                buf.writeDouble(payload.battleQiRegen());
                writeStrings(buf, payload.resistances());
                writeStrings(buf, payload.weaknesses());
                buf.writeUtf(payload.skillId());
                buf.writeVarInt(payload.skillTotalTicks());
                buf.writeVarInt(payload.skillRemainingTicks());
            },
            buf -> new ProAppraisalPayload(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    readStrings(buf),
                    readStrings(buf),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt())
    );

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> values) {
        List<String> safe = values == null ? List.of() : values;
        buf.writeVarInt(safe.size());
        for (String value : safe) buf.writeUtf(value, 48);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(buf.readUtf(48));
        return values;
    }

    /** 没戴眼镜时发的空包：客户端收到后立刻清掉 HUD 与历史曲线。 */
    public static ProAppraisalPayload unequipped() {
        return new ProAppraisalPayload(false, false, 0.0, -1, "",
                0.0F, 0.0F, 0.0, 0.0, 0, 0, 0.0, 0.0,
                List.of(), List.of(), "", 0, 0);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientProAppraisalState.update(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
