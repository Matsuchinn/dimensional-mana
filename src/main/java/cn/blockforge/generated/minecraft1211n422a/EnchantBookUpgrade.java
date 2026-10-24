package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * 附魔书升级：把附魔书放在魔晶加工台正中间，四周 8 格各放 1 份魔晶粉，书里的附魔等级 +1。
 *
 * <p>下 / 中 / 高 / 极品 / 特级魔晶粉分别对应 1→2、2→3、3→4、4→5、5→6 级，耗时都是 8 秒（160 刻），
 * 消耗 20 / 50 / 120 / 270 / 580 魔素。判定按「书里正好是这一档、而且还没到自身最高等级」来做，
 * 所以原版附魔书同样能升，一本多附魔的书只会升与粉末同档的那几项；最高 5 级的附魔用特级粉也不会超上限。</p>
 */
public final class EnchantBookUpgrade {
    /** 一次升级的耗时（8 秒）。 */
    public static final int TICKS = 160;
    private static final List<String> GRADES = List.of("lesser", "medium", "advanced", "supreme", "special");
    private static final int[] MANA_COST = {20, 50, 120, 270, 580};
    /** 展示用配方里代表「任意附魔书」的占位 id。 */
    public static final String BOOK_PREVIEW = "#enchanted_book";

    public record Job(String dustId, int level, int manaCost, int ticks) {
        public String recipeId() { return "book_upgrade_" + level; }
    }

    private EnchantBookUpgrade() { }

    /** 这一格是第几档魔晶粉（1 = 下级 … 4 = 极品）；不是魔晶粉返回 0。 */
    private static int gradeOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (int index = 0; index < GRADES.size(); index++) {
            var holder = GeneratedMod.MOD_ITEMS.get(GRADES.get(index) + "_mana_crystal_dust");
            if (holder != null && stack.is(holder.get())) return index + 1;
        }
        return 0;
    }

    /** 中间是附魔书、其余 8 格同一种魔晶粉，且书里有一项附魔正好是这一档时返回任务。 */
    public static Job findJob(List<ItemStack> slots) {
        if (slots.size() < 9) return null;
        ItemStack center = slots.get(4);
        if (!center.is(Items.ENCHANTED_BOOK)) return null;
        ItemEnchantments stored = center.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stored.isEmpty()) return null;
        int grade = 0;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 4) continue;
            int slotGrade = gradeOf(slots.get(slot));
            if (slotGrade == 0) return null;
            if (grade == 0) grade = slotGrade;
            else if (grade != slotGrade) return null;
        }
        if (grade == 0 || !hasUpgradable(stored, grade)) return null;
        return new Job(GRADES.get(grade - 1) + "_mana_crystal_dust", grade, MANA_COST[grade - 1], TICKS);
    }

    /** 书里有没有这一档、而且还没到自身最高等级的附魔。 */
    private static boolean hasUpgradable(ItemEnchantments stored, int level) {
        for (Holder<Enchantment> holder : stored.keySet()) {
            if (stored.getLevel(holder) == level && holder.value().getMaxLevel() > level) return true;
        }
        return false;
    }

    /** 把书里所有正好等于这一档的附魔各升 1 级；已经到顶的不动。 */
    public static void apply(ItemStack book, Job job) {
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(stored);
        for (Holder<Enchantment> holder : List.copyOf(stored.keySet())) {
            int level = stored.getLevel(holder);
            if (level != job.level()) continue;
            if (level >= holder.value().getMaxLevel()) continue;
            upgraded.set(holder, level + 1);
        }
        book.set(DataComponents.STORED_ENCHANTMENTS, upgraded.toImmutable());
    }

    /**
     * JEI 与图鉴展示用的升级示意配方（4 条，分别对应四种魔晶粉）。
     *
     * <p>这些配方只用于展示，不参与 {@link CrystalProcessorBlockEntity#findRecipe} 的匹配；
     * 真正生效的是 {@link #findJob}。</p>
     */
    public static List<CrystalProcessorBlockEntity.ProcessRecipe> previews() {
        List<CrystalProcessorBlockEntity.ProcessRecipe> previews = new ArrayList<>();
        for (int grade = 1; grade <= GRADES.size(); grade++) {
            String dust = GRADES.get(grade - 1) + "_mana_crystal_dust";
            List<String> pattern = List.of(dust, dust, dust, dust, BOOK_PREVIEW, dust, dust, dust, dust);
            previews.add(new CrystalProcessorBlockEntity.ProcessRecipe("book_upgrade_" + grade,
                    List.of(), pattern,
                    List.of(new CrystalProcessorBlockEntity.OutputStack("minecraft:enchanted_book", 1)),
                    MANA_COST[grade - 1], TICKS, "enchanted_book"));
        }
        return List.copyOf(previews);
    }
}
