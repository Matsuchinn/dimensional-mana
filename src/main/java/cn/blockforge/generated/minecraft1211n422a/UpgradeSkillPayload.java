package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record UpgradeSkillPayload(String skillId) implements CustomPacketPayload {
    public static final Type<UpgradeSkillPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "upgrade_skill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeSkillPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.skillId(), 32),
            buf -> new UpgradeSkillPayload(buf.readUtf(32))
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ArcaneCodexItem.hasAccess(player)) return;
            MagicProgression.Skill skill = MagicProgression.Skill.byId(payload.skillId());
            if (MagicProgression.upgradeSkill(player, skill)) ProgressSyncPayload.send(player);
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
