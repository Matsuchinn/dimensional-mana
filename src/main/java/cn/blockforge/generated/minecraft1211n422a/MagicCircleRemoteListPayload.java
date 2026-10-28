package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：玩家附近的「自己的刻印法阵」清单，给远程法阵面板用。
 *
 * <p>服务端同时把当前名额也带上（正在跑几个 / 最多几个 / 其中等级和魔素各给了几个），
 * 面板顶上那一行直接用，不用再单独发一个包。</p>
 */
public record MagicCircleRemoteListPayload(
        List<Entry> entries,
        int running, int limit, int levelBonus, int manaBonus, int total) implements CustomPacketPayload {

    /** 一个法阵：坐标、名字、是否在跑、层数、离玩家多远。 */
    public record Entry(int x, int y, int z, String name, boolean running, int layers, int distance) {}

    public static final Type<MagicCircleRemoteListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "magic_circle_remote_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MagicCircleRemoteListPayload> STREAM_CODEC =
            StreamCodec.of(MagicCircleRemoteListPayload::write, MagicCircleRemoteListPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, MagicCircleRemoteListPayload payload) {
        buf.writeVarInt(payload.running());
        buf.writeVarInt(payload.limit());
        buf.writeVarInt(payload.levelBonus());
        buf.writeVarInt(payload.manaBonus());
        buf.writeVarInt(payload.total());
        buf.writeVarInt(payload.entries().size());
        for (Entry entry : payload.entries()) {
            buf.writeVarInt(entry.x());
            buf.writeVarInt(entry.y());
            buf.writeVarInt(entry.z());
            buf.writeUtf(entry.name(), 48);
            buf.writeBoolean(entry.running());
            buf.writeVarInt(entry.layers());
            buf.writeVarInt(entry.distance());
        }
    }

    private static MagicCircleRemoteListPayload read(RegistryFriendlyByteBuf buf) {
        int running = buf.readVarInt();
        int limit = buf.readVarInt();
        int levelBonus = buf.readVarInt();
        int manaBonus = buf.readVarInt();
        int total = buf.readVarInt();
        int count = Math.max(0, Math.min(MagicCircleRites.REMOTE_MAX_ENTRIES, buf.readVarInt()));
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(48), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
        }
        return new MagicCircleRemoteListPayload(entries, running, limit, levelBonus, manaBonus, total);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientMagicCirclePayloadHandler.openRemote(payload);
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
