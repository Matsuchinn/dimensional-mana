package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 回溯之门的永久代价：魔素基础上限减少 1%。
 *
 * <p>这里故意把“恢复”也留成公开接口。后续加入净化、赎罪或特殊祭坛时，
 * 直接调用 {@link #relieveLawDebts(ServerPlayer, int)} 即可，不用再改死亡传送流程。</p>
 */
public final class DeathLawPenalty {
    private static final String DEBT_DELTAS = "AntiVoidLawDebtDeltas";

    private DeathLawPenalty() {}

    public static void applyLawDebt(ServerPlayer player) {
        ManaData.init(player);
        double before = ManaData.get(player).maxMana();
        double delta = ManaData.reduceBaseMaxManaPercent(player, 0.01D);
        if (delta <= 0.0D) return;

        ListTag deltas = player.getPersistentData().getList(DEBT_DELTAS, 6);
        deltas.add(net.minecraft.nbt.DoubleTag.valueOf(delta));
        player.getPersistentData().put(DEBT_DELTAS, deltas);
        double after = ManaData.get(player).maxMana();
        player.displayClientMessage(Component.translatable("message.dimensional_mana.law_debt_applied"), false);
        player.displayClientMessage(Component.translatable("message.dimensional_mana.law_debt_values",
                (int) Math.ceil(before), (int) Math.floor(after)), false);
        sync(player);
    }

    /** 后续恢复玩法的接口：按最近的债务逐笔回补，count 传 1 就是恢复一笔。 */
    public static void relieveLawDebts(ServerPlayer player, int count) {
        if (count <= 0) return;
        ManaData.init(player);
        ListTag deltas = player.getPersistentData().getList(DEBT_DELTAS, 6);
        int restored = Math.min(count, deltas.size());
        for (int i = 0; i < restored; i++) {
            int index = deltas.size() - 1;
            double delta = deltas.getDouble(index);
            ManaData.increaseBaseMaxMana(player, delta);
            deltas.remove(index);
        }
        player.getPersistentData().put(DEBT_DELTAS, deltas);
        if (restored > 0) sync(player);
    }

    public static int debtCount(ServerPlayer player) {
        return player.getPersistentData().getList(DEBT_DELTAS, 6).size();
    }

    private static void sync(ServerPlayer player) {
        ManaData.Snapshot data = ManaData.get(player);
        PacketDistributor.sendToPlayer(player, new ManaSyncPayload(data.mana(), data.maxMana(), data.battleQi(),
                data.maxBattleQi(), data.exhaustionTicks() > 0, ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
    }
}
