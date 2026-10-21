package cn.blockforge.generated.minecraft1211n422a;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 作弊开关：技能冷却与魔素消耗。
 *
 * <p>两套开关都分“全局”和“单人”两级：全局关闭时对所有人生效，
 * 单人开关只影响被点名的那个玩家。默认全部保持原样（冷却与消耗都开启），
 * 只有管理员用 /dimensional_mana 指令主动改过才会变。
 */
public final class ManaCheats {
    private static boolean globalCooldown = true;
    private static boolean globalManaCost = true;
    private static final Set<UUID> cooldownOff = new HashSet<>();
    private static final Set<UUID> manaFree = new HashSet<>();

    /** 该玩家的法术冷却是否仍然生效。 */
    public static boolean cooldownEnabled(ServerPlayer player) {
        if (!globalCooldown) return false;
        return player == null || !cooldownOff.contains(player.getUUID());
    }

    /** 该玩家施法是否仍然要花魔素。 */
    public static boolean manaCostEnabled(LivingEntity entity) {
        if (!globalManaCost) return false;
        return !(entity instanceof ServerPlayer player) || !manaFree.contains(player.getUUID());
    }

    /** 魔素是否够付这笔钱；关闭消耗时永远算够。 */
    public static boolean hasMana(LivingEntity entity, double cost) {
        return !manaCostEnabled(entity) || ManaData.get(entity).mana() >= cost;
    }

    public static boolean globalCooldown() {
        return globalCooldown;
    }

    public static boolean globalManaCost() {
        return globalManaCost;
    }

    public static void setGlobalCooldown(boolean enabled) {
        globalCooldown = enabled;
    }

    public static void setGlobalManaCost(boolean enabled) {
        globalManaCost = enabled;
    }

    public static void setPlayerCooldown(ServerPlayer player, boolean enabled) {
        if (enabled) cooldownOff.remove(player.getUUID());
        else cooldownOff.add(player.getUUID());
    }

    public static void setPlayerManaCost(ServerPlayer player, boolean enabled) {
        if (enabled) manaFree.remove(player.getUUID());
        else manaFree.add(player.getUUID());
    }

    public static void clear() {
        globalCooldown = true;
        globalManaCost = true;
        cooldownOff.clear();
        manaFree.clear();
    }

    private ManaCheats() {}
}
