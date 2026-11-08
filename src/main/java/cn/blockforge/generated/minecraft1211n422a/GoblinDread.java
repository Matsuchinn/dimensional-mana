package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 【敬而远之】（哥布林克星）的全部服务端逻辑：发放、刷新、献祭解除、禁刷判定。
 *
 * <p>玩法链条：</p>
 * <ol>
 *   <li>击杀哥布林营地的首领 → 获得 2 小时威压（再杀一只新首领就整段刷新）；</li>
 *   <li>状态期间 64 格内不再自然刷出哥布林，成年哥布林见你就跑，幼崽照旧慢吞吞；</li>
 *   <li>想提前解除：亲手连杀 3 只【哥布林幼崽】。每杀一只在聊天栏报一句进度，
 *       第 3 只时状态清空并全服播报。</li>
 * </ol>
 *
 * <p>献祭进度写在玩家持久数据的 {@code DimensionalMana} 根标签里
 * （{@code goblinDreadSacrifices}），掉线重连、换维度都不会清零；
 * 玩家一死就随状态一起抹掉。</p>
 */
public final class GoblinDread {
    /** 状态持续时间：2 小时。 */
    public static final int DURATION_TICKS = 144_000;
    /** 要献祭几只幼崽才能主动解除。 */
    public static final int SACRIFICE_TARGET = 3;
    /** 威压半径：这个范围内的哥布林自然生成全部掐掉。 */
    public static final double SUPPRESS_RADIUS = 64.0;

    private static final String DATA_ROOT = "DimensionalMana";
    private static final String SACRIFICE_KEY = "goblinDreadSacrifices";

    /** 击杀首领：挂上威压（已有就整段刷新），并顺手记进度。 */
    public static void apply(ServerPlayer player) {
        if (player == null) return;
        boolean refresh = player.hasEffect(GeneratedMod.GOBLIN_DREAD);
        // 先摘再挂：同等级的状态只会「取较长的剩余时间」，刷新必须走这一步。
        player.removeEffect(GeneratedMod.GOBLIN_DREAD);
        player.addEffect(new MobEffectInstance(GeneratedMod.GOBLIN_DREAD, DURATION_TICKS, 0,
                false, false, true));
        // 刷新只重置时钟，已经献祭的幼崽数量保留，免得玩家白杀两只。
        if (!refresh) setSacrifices(player, 0);
        player.displayClientMessage(Component.translatable(refresh
                ? "message.dimensional_mana.goblin_dread.refreshed"
                : "message.dimensional_mana.goblin_dread.applied"), false);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        MagicProgression.awardAdvancement(player, "goblin_dread");
    }

    /**
     * 哥布林死亡时结算：首领给威压，幼崽算献祭。
     *
     * @param dead   死掉的那只哥布林
     * @param killer 亲手补刀并拿到击杀归属的玩家；不是玩家杀的传 {@code null}
     */
    public static void onGoblinKilled(Goblin dead, ServerPlayer killer) {
        if (killer == null) return;
        if (dead.variant() == GoblinVariant.CHIEF) {
            apply(killer);
            return;
        }
        if (dead.variant() != GoblinVariant.YOUNG) return;
        // 只有「带着威压杀幼崽」才算献祭；平时打猎幼崽不计数。
        if (!killer.hasEffect(GeneratedMod.GOBLIN_DREAD)) return;

        int count = sacrifices(killer) + 1;
        if (killer.level() instanceof ServerLevel level) {
            level.playSound(null, dead.blockPosition(), GoblinSounds.get("young"),
                    SoundSource.HOSTILE, 1.0F, 1.7F);
        }
        if (count < SACRIFICE_TARGET) {
            setSacrifices(killer, count);
            killer.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.goblin_dread.sacrifice", count, SACRIFICE_TARGET), false);
            return;
        }
        setSacrifices(killer, 0);
        killer.removeEffect(GeneratedMod.GOBLIN_DREAD);
        killer.displayClientMessage(Component.translatable(
                "message.dimensional_mana.goblin_dread.lifted"), false);
        MinecraftServer server = killer.getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable(
                    "message.dimensional_mana.goblin_dread.lifted_broadcast", killer.getDisplayName()), false);
        }
        MagicProgression.awardAdvancement(killer, "goblin_dread_lift");
    }

    /** 事件总线入口：玩家死亡清空威压，哥布林死亡结算首领 / 幼崽。 */
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        net.minecraft.world.entity.LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;
        if (dead instanceof ServerPlayer player) {
            onPlayerDeath(player);
            return;
        }
        if (!(dead instanceof Goblin goblin)) return;
        onGoblinKilled(goblin, event.getSource().getEntity() instanceof ServerPlayer killer ? killer : null);
    }

    /** 玩家死亡：威压随尸体一起消散，献祭计数一并清空。 */
    public static void onPlayerDeath(Player player) {
        if (player == null || player.level().isClientSide) return;
        player.removeEffect(GeneratedMod.GOBLIN_DREAD);
        setSacrifices(player, 0);
    }

    /** 这个位置现在是不是被某个带威压的玩家罩着（64 格内禁刷哥布林）。 */
    public static boolean suppressesSpawn(ServerLevel level, BlockPos pos) {
        double limit = SUPPRESS_RADIUS * SUPPRESS_RADIUS;
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.hasEffect(GeneratedMod.GOBLIN_DREAD)) continue;
            if (player.distanceToSqr(center) <= limit) return true;
        }
        return false;
    }

    /** 附近有没有带威压的玩家（给巡逻队判断要不要白扫一遍地面）。 */
    public static boolean playerNearbyHasDread(ServerPlayer player) {
        return player != null && player.hasEffect(GeneratedMod.GOBLIN_DREAD);
    }

    /** 当前已经献祭了几只幼崽。 */
    public static int sacrifices(Player player) {
        ManaData.init(player);
        return player.getPersistentData().getCompound(DATA_ROOT).getInt(SACRIFICE_KEY);
    }

    private static void setSacrifices(Player player, int value) {
        ManaData.init(player);
        CompoundTag root = player.getPersistentData().getCompound(DATA_ROOT);
        if (root.getInt(SACRIFICE_KEY) == value) return;
        root.putInt(SACRIFICE_KEY, value);
        player.getPersistentData().put(DATA_ROOT, root);
    }

    private GoblinDread() { }
}
