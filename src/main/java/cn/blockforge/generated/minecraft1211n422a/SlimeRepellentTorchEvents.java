package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Slime;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * 史莱姆驱逐火把的两件事：不让史莱姆刷出来、让已经在里面的史莱姆掉头。
 *
 * <p><b>禁刷</b>挂在生成事件的最后一刻（{@link FinalizeSpawnEvent}）。这里能拦住原版史莱姆、
 * 熔岩史莱姆（两者都是 {@link Slime} 的子类）、还有模组自己的元素史莱姆——哥布林营地那种
 * 「召唤来当宠物」的也是走这条流程。刷怪蛋和 {@code /summon} 是玩家主动放的，故意网开一面，
 * 免得调试时连自己都放不出来。模组里绕过 {@code finalizeSpawn} 直接
 * {@code addFreshEntity} 的两条旁路（环境刷新、自然史莱姆自我复制）另外在源头补了闸门。</p>
 *
 * <p><b>掉头</b>不给每个史莱姆每刻扫实体：实体进入世界时给它挂一个
 * {@link SlimeDreadGoal}（一次性的），之后由那个 Goal 自己每 20 刻问一次火把数据。</p>
 */
public final class SlimeRepellentTorchEvents {
    private SlimeRepellentTorchEvents() {
    }

    /** 这个位置现在是不是被驱逐火把管着（区块级判定，见 {@link SlimeRepellentData}）。 */
    public static boolean spawnBlocked(ServerLevel level, BlockPos pos) {
        return SlimeRepellentData.get(level).isWarded(level, pos);
    }

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled()) return;
        Mob mob = event.getEntity();
        if (!(mob instanceof Slime)) return;
        MobSpawnType type = event.getSpawnType();
        // 刷怪蛋、指令：玩家自己的行为，不拦。
        if (type == MobSpawnType.SPAWN_EGG || type == MobSpawnType.COMMAND) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (spawnBlocked(level, mob.blockPosition())) event.setSpawnCancelled(true);
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Slime slime)) return;
        // 从存档读出来的老史莱姆也要挂（Goal 不写进 NBT），同一只只挂一次。
        for (WrappedGoal wrapped : slime.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof SlimeDreadGoal) return;
        }
        slime.goalSelector.addGoal(0, new SlimeDreadGoal(slime));
    }
}
