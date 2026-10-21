package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 用组合翻译生成“品级＋元素/学派＋之碎片”，避免任何未翻译英文码。 */
public final class MagicFragmentItem extends Item {
    private final String grade;
    private final String subjectTranslation;

    public MagicFragmentItem(Properties properties, String grade, String subjectTranslation) {
        super(properties);
        this.grade = grade;
        this.subjectTranslation = subjectTranslation;
    }

    @Override public Component getName(ItemStack stack) {
        return Component.translatable("item.dimensional_mana.magic_fragment.name",
                Component.translatable("item.dimensional_mana.fragment.grade." + grade),
                Component.translatable(subjectTranslation));
    }
}
