package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;

import java.util.Set;

/** 使用原版效果保存与同步机制，死亡、喝奶和到期都能正确清理属性。 */
public final class SpellStatusEffect extends MobEffect {
    /**
     * 强制效果不接受任何 cure：牛奶、蜂蜜、不死图腾都清不掉，只能等它自己到期。
     * 光蚀（曜光裂界留下的光系侵蚀）、鸩毒（鸩羽千夜留下的毒系印记）与爆震（断魂鸣弦的声波余震）
     * 都属于这一类；鸩羽千夜顺手挂上的原版中毒也同样被强制，由 {@code PoisonSpells} 在移除事件里拦下。
     */
    private static final Set<String> UNCURABLE = Set.of("light_eclipse", "zhen_poison", "sonic_blast");

    private final boolean uncurable;

    public SpellStatusEffect(String kind, boolean beneficial, int color) {
        super(beneficial ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL, color);
        this.uncurable = UNCURABLE.contains(kind);
        double slow = switch (kind) {
            case "water_prison", "ice_coffin", "spell_stun", "deep_freeze" -> -1;
            case "frost_trail" -> -0.60;
            case "mud_swamp" -> -0.50;
            case "tinnitus", "vine_slow" -> -0.30;
            case "lightness" -> 0.25;
            // 伏特状态：移动速度 +200%。跳跃高度 +50% 走起跳事件精确换算，不在这里给跳跃属性。
            case "volt_state" -> 2.00;
            // 风爆步 +150%，风狂 +195%。
            case "wind_step" -> 1.50;
            case "wind_frenzy" -> 1.95;
            default -> 0;
        };
        if (slow != 0) addAttributeModifier(Attributes.MOVEMENT_SPEED, id(kind + "_speed"), slow,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (kind.equals("life_blessing")) addAttributeModifier(Attributes.MAX_HEALTH, id(kind + "_health"), 0.30,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /**
     * 原版在这里给效果填入“可以被哪些方式解除”的集合（牛奶、蜂蜜、图腾）。
     * 强制效果直接留空，于是牛奶一类的东西连尝试移除的入口都拿不到。
     */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
        if (uncurable) return;
        cures.addAll(EffectCures.DEFAULT_CURES);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, path);
    }
}
