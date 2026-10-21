package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ElementTalentActionPayload(String first, String second, String third) implements CustomPacketPayload {
    public static final Type<ElementTalentActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "element_talent_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ElementTalentActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.first, 16); buf.writeUtf(value.second, 16); buf.writeUtf(value.third, 16); },
            buf -> new ElementTalentActionPayload(buf.readUtf(16), buf.readUtf(16), buf.readUtf(16)));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !ArcaneCodexItem.hasAccess(player)) return;
            if (ElementTalentData.choose(player, ElementTalentData.fromIds(new String[]{payload.first, payload.second, payload.third}))) {
                MagicProgression.awardAdvancement(player, "element_resonance");
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.dimensional_mana.element_choice_complete"), false);
            }
        }));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
