package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SpellSyncPayload(int[] progress, String learningSpell, String[] slots, int selectedSlot,
                               String castingSpell, int castTicksRemaining, int castTicksTotal,
                               int selectedCooldownTicks, boolean targeting, boolean targetSelected,
                               int barrierTicks, int barrierCount, int blazingSunCharge,
                               int blazingSunTicks, int dragonBeamTicks,
                               int subzeroLayers, int subzeroTicks,
                               int tempestExtras, int tempestTicks, int windStacks,
                               int meteorRocks, int meteorTicks,
                               int tideTicks, int senseTicks, int bankaiTicks,
                               int swordRiftTicks, int lightCharge, int soulChordExtras) implements CustomPacketPayload {
    public static final Type<SpellSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "spell_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.progress.length);
                for (int value : payload.progress) buf.writeVarInt(value);
                buf.writeUtf(payload.learningSpell, 32);
                buf.writeVarInt(payload.slots.length);
                for (String slot : payload.slots) buf.writeUtf(slot == null ? "" : slot, 32);
                buf.writeVarInt(payload.selectedSlot);
                buf.writeUtf(payload.castingSpell, 32);
                buf.writeVarInt(payload.castTicksRemaining);
                buf.writeVarInt(payload.castTicksTotal);
                buf.writeVarInt(payload.selectedCooldownTicks);
                buf.writeBoolean(payload.targeting);
                buf.writeBoolean(payload.targetSelected);
                buf.writeVarInt(payload.barrierTicks);
                buf.writeVarInt(payload.barrierCount);
                buf.writeVarInt(payload.blazingSunCharge);
                buf.writeVarInt(payload.blazingSunTicks);
                buf.writeVarInt(payload.dragonBeamTicks);
                buf.writeVarInt(payload.subzeroLayers);
                buf.writeVarInt(payload.subzeroTicks);
                buf.writeVarInt(payload.tempestExtras);
                buf.writeVarInt(payload.tempestTicks);
                buf.writeVarInt(payload.windStacks);
                buf.writeVarInt(payload.meteorRocks);
                buf.writeVarInt(payload.meteorTicks);
                buf.writeVarInt(payload.tideTicks);
                buf.writeVarInt(payload.senseTicks);
                buf.writeVarInt(payload.bankaiTicks);
                buf.writeVarInt(payload.swordRiftTicks);
                buf.writeVarInt(payload.lightCharge);
                buf.writeVarInt(payload.soulChordExtras);
            },
            buf -> {
                int progressLength = buf.readVarInt();
                if (progressLength < 0 || progressLength > 256) throw new IllegalArgumentException("Invalid spell progress length: " + progressLength);
                int[] progress = new int[progressLength];
                for (int i = 0; i < progressLength; i++) progress[i] = buf.readVarInt();
                String learning = buf.readUtf(32);
                int slotLength = buf.readVarInt();
                if (slotLength < 0 || slotLength > SpellData.SLOT_COUNT) throw new IllegalArgumentException("Invalid spell slot length: " + slotLength);
                String[] slots = new String[slotLength];
                for (int i = 0; i < slotLength; i++) slots[i] = buf.readUtf(32);
                return new SpellSyncPayload(progress, learning, slots, buf.readVarInt(), buf.readUtf(32),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            }
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientSpellState.update(payload)));
    }

    public static void send(ServerPlayer player) {
        SpellData.Snapshot data = SpellData.get(player);
        SpellCasting.CastSnapshot cast = SpellCasting.snapshot(player);
        SpellDefinition selected = data.selected();
        PacketDistributor.sendToPlayer(player, new SpellSyncPayload(data.progress().clone(), data.learningSpell(),
                data.slots().clone(), data.selectedSlot(), cast.spellId(), cast.remainingTicks(), cast.totalTicks(),
                selected == null ? 0 : SpellCasting.cooldownRemaining(player, selected),
                SpellCasting.isTargeting(player), SpellCasting.hasSelectedTarget(player),
                NewSpellSystem.barrierFieldTicks(player), NewSpellSystem.barrierFieldCount(player),
                NewSpellSystem.blazingSunCharge(player), NewSpellSystem.blazingSunTicks(player),
                NewSpellSystem.dragonBeamTicks(player), NewSpellSystem.domainLayers(player),
                NewSpellSystem.domainTicks(player),
                NewSpellSystem.tempestExtras(player), NewSpellSystem.tempestTicks(player),
                WindSpells.frenzyStacks(player),
                EarthSpells.meteorRocks(player), EarthSpells.meteorTicks(player),
                ArcaneSpells.tideTicks(player), ArcaneSpells.senseTicks(player),
                WoodSpells.bankaiTicks(player),
                MetalSpells.riftTicks(player), LightSpells.radiantCharge(player),
                SonicSpells.extras(player)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
