package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * 哥布林的三套骨骼体型。
 *
 * <p>幼崽是 2 头身（头 16 像素、全身 32 像素），成年体头更大、肚子更圆，首领则是窄肩大肚的梨形。
 * 五个变种共用这三套体格，只换贴图与配色。</p>
 */
public enum GoblinShape {
    /** 幼崽：脑袋占一半身高，短短的小胳膊小腿。 */
    YOUNG("young"),
    /** 成年哥布林（战士 / 弓箭手 / 萨满）。 */
    ADULT("adult"),
    /** 首领：梨形身材，肩窄、腰腹宽。 */
    CHIEF("chief");

    private final String id;

    GoblinShape(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** 这套体格对应的模型层键，客户端注册层定义时用同一个键。 */
    public ModelLayerLocation layer() {
        return new ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "goblin_" + id), "main");
    }

    /** 专属护甲贴图路径；布甲与粗铁甲各一套。 */
    public ResourceLocation armorTexture(String material) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
                "textures/models/armor/goblin_" + id + "_" + material + ".png");
    }
}
