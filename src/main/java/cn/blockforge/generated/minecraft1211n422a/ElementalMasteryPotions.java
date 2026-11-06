package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Optional;

/**
 * 元素精通药水的注册名、物品栈与配方标记。
 *
 * <p>药水不是独立物品（否则红石延时、火药喷溅、龙息滞留这三条原版规则全都用不上），
 * 所以这里统一按“原版药水瓶 + 药水注册名”处理。加工台与 JEI 的配方里写
 * {@code #potion:elemental_mastery_2} 这样的标记，再由本类换成真正的药水物品栈。</p>
 */
public final class ElementalMasteryPotions {
    /** 配方里的药水占位前缀。 */
    public static final String TOKEN_PREFIX = "#potion:";
    /** Ⅰ~Ⅵ 六档。 */
    public static final int MAX_LEVEL = ElementalMasteryEffect.MAX_LEVEL;
    /** 基础时长 15 秒；红石延长版 30 秒（正好翻倍）。 */
    public static final int BASE_TICKS = 15 * 20;
    public static final int LONG_TICKS = BASE_TICKS * 2;

    /** 某一档的药水注册名：{@code elemental_mastery_3} 或 {@code long_elemental_mastery_3}。 */
    public static String id(int level, boolean extended) {
        int clamped = Math.min(MAX_LEVEL, Math.max(1, level));
        return (extended ? "long_elemental_mastery_" : "elemental_mastery_") + clamped;
    }

    public static Holder<Potion> holder(String potionId) {
        if (potionId == null || potionId.isBlank()) return null;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, potionId);
        @SuppressWarnings("rawtypes")
        Optional found = BuiltInRegistries.POTION.getHolder(id);
        if (found.isPresent()) {
            @SuppressWarnings("unchecked")
            Holder<Potion> holder = (Holder<Potion>) found.get();
            return holder;
        }
        // 兜底：注册表还没冻结（或别的加载顺序）时直接按注册名在本次注册的列表里找。
        for (var candidate : GeneratedMod.ELEMENTAL_MASTERY_POTIONS) {
            if (candidate.getId().getPath().equals(potionId)) return candidate;
        }
        for (var candidate : GeneratedMod.LONG_ELEMENTAL_MASTERY_POTIONS) {
            if (candidate.getId().getPath().equals(potionId)) return candidate;
        }
        return null;
    }

    /** 配方标记：{@code #potion:elemental_mastery_2}。 */
    public static String token(int level, boolean extended) {
        return TOKEN_PREFIX + id(level, extended);
    }

    public static boolean isToken(String id) {
        return id != null && id.startsWith(TOKEN_PREFIX);
    }

    /** 标记 → 真正的药水物品栈；标记无效时返回空栈，配方会被静默跳过而不是崩服。 */
    public static ItemStack stack(String token, int count) {
        if (!isToken(token) || count <= 0) return ItemStack.EMPTY;
        Holder<Potion> holder = holder(token.substring(TOKEN_PREFIX.length()));
        if (holder == null) return ItemStack.EMPTY;
        ItemStack stack = PotionContents.createItemStack(Items.POTION, holder);
        stack.setCount(count);
        return stack;
    }

    /** 槽位里这瓶药水是不是标记指的那一瓶。 */
    public static boolean matches(ItemStack stack, String token) {
        if (!isToken(token) || stack.isEmpty() || !stack.is(Items.POTION)) return false;
        Holder<Potion> holder = holder(token.substring(TOKEN_PREFIX.length()));
        if (holder == null) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(holder);
    }

    private ElementalMasteryPotions() { }
}
