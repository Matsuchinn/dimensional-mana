package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 元素、学派与奥术碎片的公共索引；三类碎片走同一套宝箱、女巫、石碑与加工台升品渠道。 */
public final class MagicFragments {
    public static List<String> idsForGrade(String grade) {
        List<String> ids = new ArrayList<>();
        for (ElementType element : ElementType.values()) ids.add(element.fragmentId(grade));
        for (String school : GeneratedMod.SCHOOL_IDS) ids.add(grade + "_" + school + "_school_fragment");
        // 中级及以上的奥术法术需要奥术碎片，因此奥术碎片也要能正常掉落与研发获得。
        ids.add(grade + "_arcane_fragment");
        return List.copyOf(ids);
    }

    public static ItemStack randomStack(String grade, int minCount, int maxCount, RandomSource random) {
        List<String> ids = idsForGrade(grade);
        String id = ids.get(random.nextInt(ids.size()));
        int count = minCount + random.nextInt(Math.max(1, maxCount - minCount + 1));
        return new ItemStack(GeneratedMod.MOD_ITEMS.get(id).get(), count);
    }

    private MagicFragments() { }
}
