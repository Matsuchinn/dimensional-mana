package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * r290：反虚空领域的环境滤镜。
 *
 * <p>以前这个维度借用 {@code minecraft:the_end} 的特效：末影天空是黑色星空，
 * 于是超出渲染距离、没有被加载的边缘区域全部露出黑底，远处看过去漆黑一片。
 * 这套自定义特效关闭天空层（{@code SkyType.NONE}），让背景直接使用纯白清屏色，
 * 避免光影重新画出蓝天白云；客户端事件再把雾色和雾距离固定为白色近距离雾幕。
 * 光照不受方块亮度影响（constantAmbientLight + forceBrightLightmap +
 * 维度 ambient_light=1.0），也不画晨昏红晕。</p>
 *
 * <p>{@code hasGround=true} 会让原版直接跳过云层渲染（末地/下界同款逻辑），
 * 免得云絮在白幕上留下若有若无的影子。</p>
 */
public final class AntiVoidDimensionEffects extends DimensionSpecialEffects {
    public AntiVoidDimensionEffects() {
        // 参数顺序：cloudLevel、hasGround、skyType、forceBrightLightmap、constantAmbientLight。
        super(0.0F, true, SkyType.NONE, true, true);
    }

    /** 雾色不做亮度衰减：白的就是白的，越远越不发灰。 */
    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    /** 不要下界那种贴地黄雾，保持纯净。 */
    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    /** 关掉原版日出/日落的暖色地平线渐变。 */
    @Override
    public float[] getSunriseColor(float skyAngle, float tickDelta) {
        return null;
    }
}
