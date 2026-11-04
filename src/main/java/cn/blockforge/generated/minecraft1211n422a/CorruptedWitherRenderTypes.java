package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 魔蚀凋零专用的几条渲染通道。
 *
 * <p>发光部分（光环、聚合体、射线的白 / 紫两层、拖尾、雨）直接复用
 * {@link SpellRenderTypes#GLOW}：POSITION_COLOR + 加色混合，本身就是自发光通道。</p>
 *
 * <p>这里额外补三条：</p>
 * <ul>
 *   <li>{@link #BEAM_CORE}：射线主体要求「纯黑、不反光」，加色混合下黑色等于什么也没画，
 *       所以必须换成普通半透明混合的 POSITION_COLOR 通道；</li>
 *   <li>{@link #BURST_GLOW}：聚合体爆炸的带贴图发光面片（POSITION_COLOR_TEX_LIGHTMAP），
 *       颜色走顶点 ARGB、图案走 UV，混合方式仍是加色；</li>
 *   <li>{@link #BURST_METAL}：同一条贴图换成普通半透明混合，用来画「暗红金属裂纹环」——
 *       加色下暗色等于透明，裂纹的金属反光就出不来。</li>
 * </ul>
 */
public final class CorruptedWitherRenderTypes extends RenderStateShard {
    /** 爆炸特效图集：左上柔光、右上四芒星、左下碎玻璃、右下竖直流光。 */
    public static final ResourceLocation BURST_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/effect/corrupted_wither_burst.png");

    /** 纯黑不反光的柱体：普通混合、不写深度之前的额外缓冲，双面。 */
    public static final RenderType BEAM_CORE = RenderType.create("dimensional_mana_beam_core",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * 聚合体爆炸的发光面片：加色混合 + 全亮光照，顶点色决定 ARGB，UV 决定图集里的图案。
     *
     * <p>用 {@code POSITION_COLOR_TEX_LIGHTMAP} 而不是带法线的实体格式：爆炸特效是纯广告牌
     * 面片，不需要法线；光照用 {@code setLight(0xF000F0)} 拉满，爆炸本身就该是自发光。</p>
     */
    public static final RenderType BURST_GLOW = RenderType.create("dimensional_mana_burst_glow",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 65536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTextureState(new TextureStateShard(BURST_TEXTURE, true, true))
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    /** 爆炸里需要「暗下去」的部分（金属裂纹环、碎玻璃的暗面）：普通半透明混合。 */
    public static final RenderType BURST_METAL = RenderType.create("dimensional_mana_burst_metal",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 65536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTextureState(new TextureStateShard(BURST_TEXTURE, true, true))
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    private CorruptedWitherRenderTypes() { super("dimensional_mana_beam_core", () -> { }, () -> { }); }
}
