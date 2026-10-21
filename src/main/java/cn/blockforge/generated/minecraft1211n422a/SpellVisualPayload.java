package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 只同步一次动画事件；几何动画在客户端按帧插值，不向服务器逐粒子索取数据。 */
public record SpellVisualPayload(String spellId, int phase, int owner, int anchor, Vec3 start, Vec3 end,
                                 int duration) implements CustomPacketPayload {
    public static final int CHARGE = 0, RELEASE = 1, SHATTER = 2, CANCEL = 3, END_ATTACHED = 4,
            /** 多重结界法阵本体，持续到各自到期。 */ BARRIER = 5,
            /** 法阵的一次贯穿射击。 */ SHOT = 6,
            /** 烈焰囚笼本体，持续到到期或被打破。 */ CAGE = 7,
            /** 烈焰囚笼提前被打破，移除对应视觉。 */ CAGE_END = 8,
            /** 深渊龙击的高压水柱；每 2 刻用新的起止点刷新同一条视觉，客户端在两包之间插值。 */ BEAM = 9,
            /** 水柱结束，移除对应视觉。 */ BEAM_END = 10,
            /** 零下领域的叠层高级法阵，层数编码在 end.y 与 start.y 的差值里。 */ DOMAIN_FIELD = 11,
            /** 零下领域释放，移除叠层法阵。 */ DOMAIN_END = 12,
            /** 零下领域爆发；end.y 与 start.y 的差值表示冻结半径。 */ DOMAIN_BURST = 13,
            /** 天雷滚滚的雷电球；追加数量编码在 end.y 与 start.y 的差值里，雷球跟随 owner。 */ TEMPEST_BALL = 14,
            /** 一次天雷电击；start 为雷球中心，anchor 为目标，持续 1 秒残影。 */ TEMPEST_STRIKE = 15,
            /** 雷球收束成的电磁漩涡，持续吸附周围生物。 */ TEMPEST_PULL = 16,
            /** 电磁爆炸；end.y 与 start.y 的差值表示爆炸半径。 */ TEMPEST_BLAST = 17,
            /** 天雷滚滚结束，移除雷球与漩涡视觉。 */ TEMPEST_END = 18,
            /** 万法千陨的石块落地；只在客户端补碎屑，声音由服务端按节流播放。 */ METEOR_IMPACT = 19,
            /** 藤条穿刺的带刺藤条本体，持续到 45 秒到期。 */ VINE = 20,
            /** 藤条到期或被收起，移除对应藤条。 */ VINE_END = 21,
            /** 不息卍解的一次卍之波；end.y 与 start.y 的差值表示领域半径。 */ BANKAI_WAVE = 22,
            /** 生命之树领域的边界与卍纹，每 40 刻刷新一次。 */ BANKAI_FIELD = 23,
            /** 生命之树消失，移除领域可视化。 */ BANKAI_END = 24,
            /** 万剑归一的天空裂缝；start 为裂缝中心，end 的水平长度表示半长、end.y 与 start.y 的差值表示半宽。 */ SKY_RIFT = 25,
            /** 天空裂缝提前关闭，移除对应可视化。 */ SKY_RIFT_END = 26,
            /** 一把飞剑的飞行；start 为出剑点，end 为落点，持续时间为预计飞行刻数。 */ FLYING_SWORD = 27,
            /** 金钟罩本体，围绕施法者，每 40 刻刷新一次。 */ GOLDEN_BELL = 28,
            /** 金钟罩消失，移除对应可视化。 */ GOLDEN_BELL_END = 29,
            /** 藤条的一次抽打：start 为藤条根部，end 为目标身体中心，客户端画成甩向目标的鞭击。 */ VINE_LASH = 30,
            /** 曜光裂界的蓄力法阵；end.y 与 start.y 的差值表示已经蓄了几层。 */ RADIANT_CHARGE = 31,
            /** 光球爆裂的光环；end.y 与 start.y 的差值表示爆发半径。 */ RADIANT_BURST = 32,
            /** 光球爆裂的 16 刻黑白闪光，只在客户端屏幕层播放。 */ RADIANT_FLASH = 33,
            /** 光的庇护光环，围绕被庇护的生物，每 40 刻刷新一次。 */ LIGHT_SHELTER = 34,
            /** 光的庇护结束，移除对应光环。 */ LIGHT_SHELTER_END = 35,
            /** 猛毒攻心的毒刺飞行；start 为出刺点，end 为航线终点，持续时间为预计飞行刻数。 */ VENOM_NEEDLE = 36,
            /** 鸩羽千夜的毒雾；end.y 与 start.y 的差值表示当前半径，每秒原地刷新一次。 */ VENOM_MIST = 37,
            /** 毒雾消散，移除对应可视化。 */ VENOM_MIST_END = 38,
            /** 断魂鸣弦的震荡鸣球；end.y 与 start.y 的差值表示当前鸣球数量，绕 owner 缓慢旋转。 */ SONIC_ORB = 39,
            /** 鸣球结束，移除对应可视化。 */ SONIC_ORB_END = 40,
            /** 高频声波；end.y 与 start.y 的差值表示水平半径，起点是发出声波的鸣球。 */ SONIC_WAVE = 41,
            /** 沿攻击来向反射的声波；起点是施法者，终点是反射方向八格内。 */ SONIC_REFLECT = 42,
            /** 鸣球碰到生物时的小型爆震圈。 */ SONIC_TOUCH = 43;
    /** 客户端接受的最大阶段编号；以后再加视觉阶段要同步抬高，否则会被直接过滤掉。 */
    public static final int MAX_PHASE = SONIC_TOUCH;
    public static final Type<SpellVisualPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "spell_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellVisualPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.spellId, 32);
                buf.writeVarInt(value.phase);
                buf.writeVarInt(value.owner);
                buf.writeVarInt(value.anchor);
                buf.writeDouble(value.start.x); buf.writeDouble(value.start.y); buf.writeDouble(value.start.z);
                buf.writeDouble(value.end.x); buf.writeDouble(value.end.y); buf.writeDouble(value.end.z);
                buf.writeVarInt(value.duration);
            }, buf -> new SpellVisualPayload(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientSpellVisuals.accept(payload)));
    }

    public static void send(ServerPlayer player, SpellDefinition spell, int phase, int anchor,
                            Vec3 start, Vec3 end, int duration) {
        send((Entity)player, spell, phase, anchor, start, end, duration);
    }

    public static void send(Entity caster, SpellDefinition spell, int phase, int anchor,
                            Vec3 start, Vec3 end, int duration) {
        SpellVisualPayload payload = new SpellVisualPayload(spell.id(), phase, caster.getId(), anchor, start, end, duration);
        if (caster.level() instanceof ServerLevel level) {
            // 直接按施法点广播，不依赖实体追踪列表刚好已经刷新；避免 NPC 法术偶发漏动画。
            PacketDistributor.sendToPlayersNear(level, null, start.x, start.y, start.z, 96.0, payload);
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
