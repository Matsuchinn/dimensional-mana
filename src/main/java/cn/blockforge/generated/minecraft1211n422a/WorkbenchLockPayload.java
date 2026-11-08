package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 武备加工台：把玩家在锁定面板里勾的目标词条送到服务端（客户端 → 服务端）。
 *
 * <p>为什么非要发这一趟：防手滑必须在服务端判定，否则改客户端就能绕过；而服务端
 * 没有勾选信息就没法比对。包里只带「勾了哪几条」，不带任何结果，服务端收到后
 * 一律重新校验 id（见 {@link EquipmentAffixSystem#isKnownLockKey}），脏数据进不来。</p>
 *
 * <p>{@code containerId} 用来挡住「玩家已经关了界面，包里指的却是别的东西」这种串台。</p>
 */
public record WorkbenchLockPayload(int containerId, String mainKey, List<String> subKeys)
        implements CustomPacketPayload {
    public static final Type<WorkbenchLockPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "workbench_lock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkbenchLockPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.containerId());
                buf.writeUtf(payload.mainKey(), 64);
                buf.writeVarInt(payload.subKeys().size());
                for (String key : payload.subKeys()) buf.writeUtf(key, 64);
            },
            buf -> {
                int containerId = buf.readVarInt();
                String mainKey = buf.readUtf(64);
                int size = Math.min(buf.readVarInt(), WeaponWorkbenchMenu.MAX_LOCKED_SUBS);
                List<String> subKeys = new ArrayList<>(size);
                for (int i = 0; i < size; i++) subKeys.add(buf.readUtf(64));
                return new WorkbenchLockPayload(containerId, mainKey, subKeys);
            }
    );

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof WeaponWorkbenchMenu menu)) return;
            if (menu.containerId != payload.containerId()) return;
            if (!menu.stillValid(player)) return;
            menu.applyLocks(payload.mainKey(), payload.subKeys());
            // 勾选变了，保护状态可能跟着变：立刻回推一次，别让按钮的灰/亮靠客户端猜。
            WorkbenchGuardPayload.send(player, menu.containerId, menu.protectionActive());
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
