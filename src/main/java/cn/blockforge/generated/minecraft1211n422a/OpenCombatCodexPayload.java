package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 服务端下令打开战斗手册界面；界面本身只在客户端存在。 */
public record OpenCombatCodexPayload(boolean open) implements CustomPacketPayload {
    public static final Type<OpenCombatCodexPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "open_combat_codex"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCombatCodexPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.open()),
            buf -> new OpenCombatCodexPayload(buf.readBoolean())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientCodexPayloadHandler.openCombatCodex(payload);
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
