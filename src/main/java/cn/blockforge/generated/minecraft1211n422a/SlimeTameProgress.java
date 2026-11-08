package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「元素史莱姆驯服图鉴」：记录玩家一共驯服过几种不同变种的元素史莱姆。
 *
 * <p>12 个变种（魔力、火、水、冰、雷、木、毒、金、风、土、光、音）各占持久数据里的一个二进制位，
 * 同一变种驯服多次不会重复计数。第一次凑够 5 种发进度「五色同伴」，
 * 12 种全收齐发进度「万物皆友」。</p>
 */
public final class SlimeTameProgress {
    private static final String DATA_ROOT = "DimensionalMana";
    private static final String VARIANTS_KEY = "elementalSlimeVariants";
    /** 第一个里程碑：5 种不同元素。 */
    public static final int MILESTONE_FIVE = 5;
    /** 全图鉴：12 种。 */
    public static final int MILESTONE_ALL = ElementalSlimeVariant.values().length;

    /** 驯服成功时调用；只有「新变种」才写盘和发提示。 */
    public static void onTame(ServerPlayer player, ElementalSlimeVariant variant) {
        if (player == null || variant == null) return;
        ManaData.init(player);
        CompoundTag root = player.getPersistentData().getCompound(DATA_ROOT);
        int oldMask = root.getInt(VARIANTS_KEY);
        int mask = oldMask | (1 << variant.ordinal());
        if (mask == oldMask) return;
        root.putInt(VARIANTS_KEY, mask);
        player.getPersistentData().put(DATA_ROOT, root);

        int count = Integer.bitCount(mask);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.slime_tame_progress", count, MILESTONE_ALL), true);
        if (count >= MILESTONE_FIVE) MagicProgression.awardAdvancement(player, "slime_tame_five");
        if (count >= MILESTONE_ALL) MagicProgression.awardAdvancement(player, "slime_tame_all");
    }

    /** 已经驯服过几种（给图鉴 / 调试用）。 */
    public static int tamedVariants(net.minecraft.world.entity.player.Player player) {
        ManaData.init(player);
        return Integer.bitCount(player.getPersistentData().getCompound(DATA_ROOT).getInt(VARIANTS_KEY));
    }

    private SlimeTameProgress() { }
}
