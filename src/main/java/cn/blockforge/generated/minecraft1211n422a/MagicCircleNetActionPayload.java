package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 客户端 → 服务端的一张「杂事单」：法阵码导入、远程轮盘刷新 / 开关、一键停机。
 *
 * <p>这些动作都跟绘制台那把椅子无关，所以单独开一个包，不塞进
 * {@link MagicCircleActionPayload}（那个包的前提是「玩家正坐在绘制台上」）。
 * 服务端做完全部校验：码要合法、轮盘里的锚点得是自己的、距离不能太远。</p>
 */
public record MagicCircleNetActionPayload(String action, int x, int y, int z, String text)
        implements CustomPacketPayload {

    /** 法阵码最长 {@link MagicCircleCode#MAX_CHARS}，加上 {@code DM1-} 前缀再留一点余量。 */
    public static final int MAX_CODE_LENGTH = MagicCircleCode.MAX_CHARS + 16;

    public static final Type<MagicCircleNetActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "magic_circle_net_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MagicCircleNetActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.action(), 32);
                        buf.writeVarInt(payload.x());
                        buf.writeVarInt(payload.y());
                        buf.writeVarInt(payload.z());
                        buf.writeUtf(payload.text() == null ? "" : payload.text(), MAX_CODE_LENGTH);
                    },
                    buf -> new MagicCircleNetActionPayload(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readUtf(MAX_CODE_LENGTH))
            );

    /** 导入一段法阵码（会把手边一张纸 / 布 / 绸换成阵图）。 */
    public static MagicCircleNetActionPayload importCode(String code) {
        return new MagicCircleNetActionPayload("import_code", 0, 0, 0, code == null ? "" : code);
    }

    /** 请服务端把远程轮盘要的清单重发一份。 */
    public static MagicCircleNetActionPayload remoteRefresh() {
        return new MagicCircleNetActionPayload("remote_refresh", 0, 0, 0, "");
    }

    /** 远程开 / 关一个法阵。 */
    public static MagicCircleNetActionPayload remoteToggle(BlockPos pos) {
        return new MagicCircleNetActionPayload("remote_toggle", pos.getX(), pos.getY(), pos.getZ(), "");
    }

    /** 一键停掉自己所有在跑的法阵。 */
    public static MagicCircleNetActionPayload stopAll() {
        return new MagicCircleNetActionPayload("stop_all", 0, 0, 0, "");
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (payload.action()) {
                case "import_code" -> MagicCircleCodeImport.applyFromPanel(player, payload.text());
                case "remote_refresh" -> MagicCircleRites.sendRemoteList(player);
                case "remote_toggle" -> MagicCircleRites.remoteToggle(player,
                        new BlockPos(payload.x(), payload.y(), payload.z()));
                case "stop_all" -> MagicCircleRites.stopAllAndReport(player);
                default -> { }
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
