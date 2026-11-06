package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 魔蚀凋零专用的几条渲染通道。
 *
 * <p>发光部分（光环、聚合体、射线的白 / 紫两层、拖尾、雨）复用与
 * {@link SpellRenderTypes#GLOW} 完全相同的管线：POSITION_COLOR + 加色混合，本身就是自发光通道。</p>
 *
 * <p>这里额外补三条：</p>
 * <ul>
 *   <li>{@link #beamCore()}：射线主体要求「纯黑、不反光」，加色混合下黑色等于什么也没画，
 *       所以必须换成普通半透明混合的 POSITION_COLOR 通道；</li>
 *   <li>{@link #burstGlow()}：聚合体爆炸的带贴图发光面片（POSITION_COLOR_TEX_LIGHTMAP），
 *       颜色走顶点 ARGB、图案走 UV，混合方式仍是加色；</li>
 *   <li>{@link #burstMetal()}：同一条贴图换成普通半透明混合，用来画「暗红金属裂纹环」——
 *       加色下暗色等于透明，裂纹的金属反光就出不来。</li>
 * </ul>
 *
 * <p><b>为什么单独建一套：</b>魔蚀凋零的特效原本画在「天气缓冲」里，那一层在原版
 * （不开光影）的合成顺序里排在云层和雨之后，所以光环、聚合体、射线能压在云和雨上面。
 * 后来法术光效为了兼容光影包统一挪进了半透明层，凋零的特效也跟着沉到云层下面——
 * 这就是「特效被云盖住」的原因。这里给凋零单独保留天气层：<b>没开光影</b>时照旧画进天气缓冲，
 * <b>开了光影</b>时退回半透明缓冲（天气层会被光影的 {@code gbuffers_weather} 当成实心盘子合成）。
 * 判定每帧随 {@link ShaderCompat#shaderInUse()} 走，开关光影两秒内自动跟上。</p>
 */
public final class CorruptedWitherRenderTypes extends RenderStateShard {
    /** 爆炸特效图集：左上柔光、右上四芒星、左下碎玻璃、右下竖直流光。 */
    public static final ResourceLocation BURST_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/effect/corrupted_wither_burst.png");

    // ---------------------------------------------------------------- 通用发光通道
    private static RenderType createGlow(String name, OutputStateShard layer) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(layer)
                        .createCompositeState(false));
    }

    private static final RenderType GLOW_WEATHER = createGlow("dimensional_mana_wither_glow", WEATHER_TARGET);
    private static final RenderType GLOW_COMPAT = createGlow("dimensional_mana_wither_glow_compat", TRANSLUCENT_TARGET);

    /** 凋零特效的发光通道：默认压在云 / 雨之上，开光影时自动退回半透明层。 */
    public static RenderType glow() {
        return ShaderCompat.shaderInUse() ? GLOW_COMPAT : GLOW_WEATHER;
    }

    // ---------------------------------------------------------------- 射线主体
    private static RenderType createBeamCore(OutputStateShard layer) {
        return RenderType.create("dimensional_mana_beam_core", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 65536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(layer)
                        .createCompositeState(false));
    }

    private static final RenderType BEAM_CORE_WEATHER = createBeamCore(WEATHER_TARGET);
    private static final RenderType BEAM_CORE_COMPAT = createBeamCore(TRANSLUCENT_TARGET);

    /** 纯黑不反光的柱体：普通混合、双面，同样跟着天气层走。 */
    public static RenderType beamCore() {
        return ShaderCompat.shaderInUse() ? BEAM_CORE_COMPAT : BEAM_CORE_WEATHER;
    }

    // ---------------------------------------------------------------- 爆炸面片
    private static RenderType createBurstGlow(OutputStateShard layer) {
        return RenderType.create("dimensional_mana_burst_glow", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 65536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                        .setTextureState(new TextureStateShard(BURST_TEXTURE, true, true))
                        .setLightmapState(LIGHTMAP)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(layer)
                        .createCompositeState(false));
    }

    private static RenderType createBurstMetal(OutputStateShard layer) {
        return RenderType.create("dimensional_mana_burst_metal", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 65536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                        .setTextureState(new TextureStateShard(BURST_TEXTURE, true, true))
                        .setLightmapState(LIGHTMAP)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(layer)
                        .createCompositeState(false));
    }

    private static final RenderType BURST_GLOW_WEATHER = createBurstGlow(WEATHER_TARGET);
    private static final RenderType BURST_GLOW_COMPAT = createBurstGlow(TRANSLUCENT_TARGET);
    private static final RenderType BURST_METAL_WEATHER = createBurstMetal(WEATHER_TARGET);
    private static final RenderType BURST_METAL_COMPAT = createBurstMetal(TRANSLUCENT_TARGET);

    /** 加色爆炸面片：默认在云 / 雨之上。 */
    public static RenderType burstGlow() {
        return ShaderCompat.shaderInUse() ? BURST_GLOW_COMPAT : BURST_GLOW_WEATHER;
    }

    /** 爆炸里的普通混合面片（金属裂纹环、碎玻璃暗面）。 */
    public static RenderType burstMetal() {
        return ShaderCompat.shaderInUse() ? BURST_METAL_COMPAT : BURST_METAL_WEATHER;
    }

    private CorruptedWitherRenderTypes() { super("dimensional_mana_wither_fx", () -> { }, () -> { }); }
}
