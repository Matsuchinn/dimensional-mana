package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 流浪剑士的 GeckoLib 模型：玩家比例的人形几何 + 一把作为右臂子骨骼的专属长剑。
 *
 * <p>贴图是独立绘制的 128×64 皮肤（左半 64×64 是标准玩家皮肤布局，右半放剑与披风细节），
 * 和玩家默认皮肤不共用。模型正面朝 -Z，脚底 y=0，总高 32（和玩家模型一致）。</p>
 */
public final class SwordmasterModel extends GeoModel<WanderingSwordmaster> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "geo/wandering_swordmaster.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/wandering_swordmaster.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "animations/wandering_swordmaster.animation.json");

    @Override
    public ResourceLocation getModelResource(WanderingSwordmaster animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WanderingSwordmaster animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(WanderingSwordmaster animatable) {
        return ANIMATION;
    }

    @Override
    public boolean crashIfBoneMissing() {
        // 掉落物/旧资源包缺骨骼时只警告，不要让客户端崩在渲染线程里。
        return false;
    }
}
