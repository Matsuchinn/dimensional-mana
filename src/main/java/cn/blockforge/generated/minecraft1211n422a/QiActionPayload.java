package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 斗气界面的动作包：加点、装槽、选槽、清槽、施放。
 *
 * <p>客户端只发意图，能不能加点、斗气够不够、能力有没有解锁全部由服务端复核。
 * 和法术动作包一样走 {@code playToServer}，action 用短字符串区分。</p>
 */
public record QiActionPayload(String action, String targetId, int slot) implements CustomPacketPayload {
    public static final Type<QiActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "qi_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QiActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.action, 24);
                buf.writeUtf(payload.targetId == null ? "" : payload.targetId, 32);
                buf.writeVarInt(payload.slot);
            },
            buf -> new QiActionPayload(buf.readUtf(24), buf.readUtf(32), buf.readVarInt())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (payload.action) {
                case "upgrade" -> QiProgression.upgradeSkill(player, QiProgression.Skill.byId(payload.targetId));
                case "assign" -> QiProgression.assign(player, QiProgression.Ability.byId(payload.targetId), payload.slot);
                case "select" -> QiProgression.selectSlot(player, payload.slot);
                case "clear" -> QiProgression.clearSlot(player, payload.slot);
                case "cast" -> QiSkills.castSelected(player);
                default -> { }
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
