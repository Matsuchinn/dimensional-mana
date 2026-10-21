package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** 只负责把元素附着显示在原版状态栏；时长与反应由服务器统一管理。 */
public final class ElementAttachmentEffect extends MobEffect {
    private final ElementType element;

    public ElementAttachmentEffect(ElementType element) {
        super(MobEffectCategory.NEUTRAL, element.color() & 0xFFFFFF);
        this.element = element;
    }

    public ElementType element() {
        return element;
    }
}
