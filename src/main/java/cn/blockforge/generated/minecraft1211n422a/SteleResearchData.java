package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Function;

/** 石碑研读的每日次数、递增消耗、冷却与权重奖励均由服务端判定。 */
public final class SteleResearchData {
    public static final int MAX_DAILY_USES = 7;
    public static final int COOLDOWN_TICKS = 160;
    private static final String ROOT = "DimensionalMana";
    private static final String RESEARCH = "steleResearch";

    private static final List<Reward> REWARDS = List.of(
            vanilla(200, Items.LAPIS_LAZULI, 2, 6),
            vanilla(150, Items.AMETHYST_SHARD, 2, 4),
            vanilla(35, Items.DIAMOND, 1, 2),
            vanilla(40, Items.GOLD_INGOT, 3, 5),
            vanilla(20, Items.NETHERITE_SCRAP, 1, 6),
            modItem(20, "lesser_mana_crystal_dust", 3, 7),
            modItem(19, "medium_mana_crystal_dust", 2, 6),
            modItem(18, "advanced_mana_crystal_dust", 1, 5),
            modItem(17, "supreme_mana_crystal_dust", 1, 4),
            modItem(16, "special_mana_crystal_dust", 1, 3),
            fragment(16, "lesser", 2, 4),
            fragment(8, "medium", 2, 3),
            fragment(4, "advanced", 1, 2),
            fragment(2, "supreme", 1, 1),
            fragment(1, "special", 1, 1)
    );

    public record Snapshot(int uses, int manaCost, int cooldownTicks) { }
    public record ResearchResult(Snapshot snapshot, String statusKey, Component rewardName, int rewardCount,
                                 boolean success) { }

    public static Snapshot get(ServerPlayer player) {
        CompoundTag tag = tag(player);
        refreshDay(player, tag);
        int uses = Math.max(0, Math.min(MAX_DAILY_USES, tag.getInt("uses")));
        long now = gameTime(player);
        int cooldown = (int)Math.max(0L, Math.min(Integer.MAX_VALUE, tag.getLong("cooldownUntil") - now));
        return new Snapshot(uses, manaCost(uses), cooldown);
    }

    public static ResearchResult research(ServerPlayer player) {
        Snapshot snapshot = get(player);
        if (snapshot.uses() >= MAX_DAILY_USES) {
            return failure(snapshot, "message.dimensional_mana.stele.daily_limit");
        }
        if (snapshot.cooldownTicks() > 0) {
            return failure(snapshot, "message.dimensional_mana.stele.cooldown");
        }
        if (ManaData.get(player).mana() < snapshot.manaCost()) {
            return failure(snapshot, "message.dimensional_mana.stele.no_mana");
        }

        ManaData.spendMana(player, snapshot.manaCost());
        CompoundTag tag = tag(player);
        tag.putInt("uses", snapshot.uses() + 1);
        tag.putLong("cooldownUntil", gameTime(player) + COOLDOWN_TICKS);

        Reward reward = chooseReward(player.serverLevel());
        ItemStack stack = reward.factory().apply(player.serverLevel());
        ItemStack granted = stack.copy();
        player.getInventory().add(granted);
        if (!granted.isEmpty()) player.drop(granted, false);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.9F, 0.75F + player.getRandom().nextFloat() * 0.25F);
        return new ResearchResult(get(player), "message.dimensional_mana.stele.success",
                stack.getHoverName(), stack.getCount(), true);
    }

    private static ResearchResult failure(Snapshot snapshot, String key) {
        return new ResearchResult(snapshot, key, Component.empty(), 0, false);
    }

    private static int manaCost(int uses) {
        return 8 << Math.min(MAX_DAILY_USES - 1, Math.max(0, uses));
    }

    private static void refreshDay(ServerPlayer player, CompoundTag tag) {
        long day = Math.floorDiv(player.serverLevel().getDayTime(), 24_000L);
        if (!tag.contains("day") || tag.getLong("day") != day) {
            tag.putLong("day", day);
            tag.putInt("uses", 0);
        }
    }

    private static long gameTime(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private static CompoundTag tag(ServerPlayer player) {
        ManaData.init(player);
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        if (!root.contains(RESEARCH)) root.put(RESEARCH, new CompoundTag());
        return root.getCompound(RESEARCH);
    }

    private static Reward chooseReward(ServerLevel level) {
        int total = REWARDS.stream().mapToInt(Reward::weight).sum();
        int roll = level.random.nextInt(total);
        for (Reward reward : REWARDS) {
            roll -= reward.weight();
            if (roll < 0) return reward;
        }
        return REWARDS.getFirst();
    }

    private static Reward vanilla(int weight, net.minecraft.world.item.Item item, int min, int max) {
        return new Reward(weight, level -> new ItemStack(item, randomCount(level, min, max)));
    }

    private static Reward modItem(int weight, String itemId, int min, int max) {
        return new Reward(weight, level -> new ItemStack(GeneratedMod.MOD_ITEMS.get(itemId).get(),
                randomCount(level, min, max)));
    }

    private static Reward fragment(int weight, String grade, int min, int max) {
        return new Reward(weight, level -> MagicFragments.randomStack(grade, min, max, level.random));
    }

    private static int randomCount(ServerLevel level, int min, int max) {
        return min + level.random.nextInt(Math.max(1, max - min + 1));
    }

    private record Reward(int weight, Function<ServerLevel, ItemStack> factory) { }

    private SteleResearchData() { }
}
