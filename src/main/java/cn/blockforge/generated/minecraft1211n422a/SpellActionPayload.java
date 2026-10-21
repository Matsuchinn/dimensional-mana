package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SpellActionPayload(String action, String spellId, int slot) implements CustomPacketPayload {
    public static final Type<SpellActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "spell_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.action, 24);
                buf.writeUtf(payload.spellId == null ? "" : payload.spellId, 32);
                buf.writeVarInt(payload.slot);
            },
            buf -> new SpellActionPayload(buf.readUtf(24), buf.readUtf(32), buf.readVarInt())
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            SpellDefinition spell = SpellDefinition.byId(payload.spellId);
            switch (payload.action) {
                case "learn" -> {
                    if (!ArcaneCodexItem.hasAccess(player)) return;
                    if (SpellData.beginLearning(player, spell)) {
                        player.displayClientMessage(Component.translatable("message.dimensional_mana.learning_started",
                                Component.translatable(spell.translationKey()),
                                Component.keybind("key.dimensional_mana.cast_spell")), false);
                    }
                }
                case "assign" -> SpellData.assign(player, spell, payload.slot);
                case "select" -> SpellData.selectSlot(player, payload.slot);
                case "clear" -> SpellData.clearSlot(player, payload.slot);
                case "cast" -> SpellCasting.startSelected(player);
                case "barrier_add" -> NewSpellSystem.addBarrier(player);
                case "sun_charge" -> NewSpellSystem.addSunCharge(player);
                case "domain_layer" -> NewSpellSystem.addSubzeroLayer(player);
                case "tempest_add" -> NewSpellSystem.addTempestBall(player);
                case "light_charge" -> LightSpells.addRadiantCharge(player);
                case "sonic_add" -> SonicSpells.addOrb(player);
                case "meteor_add" -> EarthSpells.addMeteorStone(player);
                case "target_select" -> SpellCasting.selectTarget(player);
                case "target_confirm" -> SpellCasting.confirmTarget(player);
                case "target_cancel" -> SpellCasting.cancelTarget(player);
                // 主手持魔法大师披风时按 Shift+O：切换“施法生成魔素凝聚球”功能。
                case "toggle_orb" -> MagicCloakItem.toggleOrb(player);
                default -> { }
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
