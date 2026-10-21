package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** 玩家元素天赋的服务器权威存档。 */
public final class ElementTalentData {
    private static final String ROOT = "DimensionalMana";
    private static final String TAG = "elementTalents";
    public static final int INITIAL_CHOICES = 3;

    public record Snapshot(boolean chosen, EnumSet<ElementType> selected, EnumSet<ElementType> unlocked,
                           int[] investedFragments) {
        public boolean isUnlocked(ElementType element) { return element != null && unlocked.contains(element); }
        public boolean isSelected(ElementType element) { return element != null && selected.contains(element); }
        public int invested(ElementType element) { return element == null ? 0 : investedFragments[element.ordinal()]; }
    }

    public static Snapshot get(Player player) {
        CompoundTag tag = tag(player);
        EnumSet<ElementType> selected = EnumSet.noneOf(ElementType.class);
        EnumSet<ElementType> unlocked = EnumSet.noneOf(ElementType.class);
        int[] invested = new int[ElementType.values().length];
        for (ElementType element : ElementType.values()) {
            if (tag.getBoolean("selected_" + element.id())) selected.add(element);
            if (tag.getBoolean("unlocked_" + element.id())) unlocked.add(element);
            invested[element.ordinal()] = Math.max(0, tag.getInt("invested_" + element.id()));
        }
        return new Snapshot(tag.getBoolean("chosen"), selected, unlocked, invested);
    }

    public static boolean choose(ServerPlayer player, List<ElementType> choices) {
        Snapshot data = get(player);
        if (data.chosen()) return false;
        EnumSet<ElementType> unique = EnumSet.noneOf(ElementType.class);
        for (ElementType choice : choices) if (choice != null) unique.add(choice);
        if (unique.size() != INITIAL_CHOICES) return false;
        CompoundTag tag = tag(player);
        for (ElementType element : ElementType.values()) {
            boolean picked = unique.contains(element);
            tag.putBoolean("selected_" + element.id(), picked);
            if (picked) tag.putBoolean("unlocked_" + element.id(), true);
        }
        tag.putBoolean("chosen", true);
        sync(player);
        return true;
    }

    public static boolean unlock(ServerPlayer player, ElementType element) {
        if (element == null || get(player).isUnlocked(element)) return false;
        tag(player).putBoolean("unlocked_" + element.id(), true);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.dimensional_mana.element_unlocked",
                net.minecraft.network.chat.Component.translatable(element.translationKey())), false);
        sync(player);
        return true;
    }

    /** 调试指令：开放全部元素，不改变玩家原本三选标记。 */
    public static void unlockAll(ServerPlayer player) {
        CompoundTag tag = tag(player);
        for (ElementType element : ElementType.values()) tag.putBoolean("unlocked_" + element.id(), true);
        tag.putBoolean("chosen", true);
        sync(player);
    }

    public static boolean canAccess(Player player, SpellDefinition spell) {
        ElementType element = spell == null ? null : spell.element();
        return element == null || get(player).isUnlocked(element);
    }

    public static void addInvestment(ServerPlayer player, ElementType element, int amount) {
        if (element == null || amount <= 0) return;
        CompoundTag tag = tag(player);
        String key = "invested_" + element.id();
        tag.putInt(key, Math.max(0, tag.getInt(key) + amount));
        sync(player);
    }

    public static void addInvestment(ServerPlayer player, ElementType element, int amount, String grade) {
        if (grade.equals("lesser")) { addInvestment(player, element, amount); return; }
        if (element == null || amount <= 0) return;
        String key = "invested_" + grade + "_" + element.id();
        CompoundTag data = tag(player);
        data.putInt(key, Math.max(0, data.getInt(key) + amount));
        sync(player);
    }

    /** 重置三选天赋，清除所有元素法术学习进度，并按原品级返还已投入元素碎片的一半。 */
    public static int reset(ServerPlayer player) {
        Snapshot data = get(player);
        if (!data.chosen()) return 0;
        int refunded = 0;
        CompoundTag tag = tag(player);
        for (ElementType element : ElementType.values()) {
            int count = (data.invested(element) + 1) / 2;
            if (count > 0) {
                ItemStack stack = new ItemStack(GeneratedMod.MOD_ITEMS.get(element.fragmentId("lesser")).get(), count);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                refunded += count;
            }
            tag.putBoolean("selected_" + element.id(), false);
            // 特殊途径永久解锁的元素继续保留；本轮三选带来的开放则关闭。
            if (data.isSelected(element)) tag.putBoolean("unlocked_" + element.id(), false);
            tag.putInt("invested_" + element.id(), 0);
            for (String grade : GeneratedMod.FRAGMENT_GRADES) {
                if (grade.equals("lesser")) continue;
                String key = "invested_" + grade + "_" + element.id();
                int higherCount = (Math.max(0, tag.getInt(key)) + 1) / 2;
                if (higherCount > 0) {
                    ItemStack stack = new ItemStack(GeneratedMod.MOD_ITEMS.get(element.fragmentId(grade)).get(), higherCount);
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                    refunded += higherCount;
                }
                tag.remove(key);
            }
        }
        tag.putBoolean("chosen", false);
        SpellData.resetElemental(player);
        sync(player);
        return refunded;
    }

    private static CompoundTag tag(Player player) {
        ManaData.init(player);
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        if (!root.contains(TAG)) root.put(TAG, new CompoundTag());
        return root.getCompound(TAG);
    }

    public static void sync(ServerPlayer player) {
        ElementTalentSyncPayload.send(player);
        SpellSyncPayload.send(player);
    }

    public static List<ElementType> fromIds(String[] ids) {
        List<ElementType> result = new ArrayList<>();
        for (String id : ids) {
            ElementType value = ElementType.byId(id);
            if (value != null && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private ElementTalentData() { }
}
