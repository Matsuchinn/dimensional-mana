package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

/**
 * 双面、带遮挡的光效通道，不写入深度，避免透明外壳遮掉内部光芯。
 *
 * <p><b>关于输出层</b>：这些通道原本画在「天气缓冲」里（为了让光效盖在水面、雨后面仍然可见）。
 * 开光影包时，天气缓冲会被光影自己的 {@code gbuffers_weather} 程序重新合成——那个程序是给雨滴写的，
 * 它按「预乘 alpha」处理这一层，加色混合叠出来的颜色会被除回不透明，光效就变成一块块实心盘子。
 * 关掉「天气层」改画进标准的半透明层之后，光影包会按普通透明体处理，alpha 正常生效。
 * 原版（不开光影）下这两条路径没有区别：分层缓冲只在「华丽」图形或光影开启时才存在。</p>
 *
 * <p>这一层选择由 {@code dimensional_mana-rendercompat.toml} 的 {@code spell_effects_compat}
 * 决定，<b>改完要重启游戏</b>才生效（渲染通道是静态常量，整局只建一次）。
 * 法阵那一套的兼容判定则是每帧动态的，见 {@link ShaderCompat}。</p>
 */
public final class SpellRenderTypes extends RenderStateShard {

    /**
     * 光效落在哪一层：兼容光影的半透明层，还是原版的天气层。
     *
     * <p>只挪层、不动混合方式：加色混合在原版下是法术光效好看的原因，
     * 改成半透明会让它变淡。若某个光影包下光效仍然发白 / 变成色块，
     * 把 {@code spell_effects_compat} 设成 false 退回天气层再对比。</p>
     */
    private static OutputStateShard pickLayer() {
        try {
            return RenderCompatConfig.SPELL_EFFECTS_COMPAT.get() ? TRANSLUCENT_TARGET : WEATHER_TARGET;
        } catch (Throwable ignored) {
            return TRANSLUCENT_TARGET;   // 配置还没加载：按兼容处理，宁可普通透明也不要盘子
        }
    }

    /** 类初始化时定一次：所有光效通道共用这一层。 */
    private static final OutputStateShard LAYER = pickLayer();

    /**
     * 魔素感知的穿墙轮廓线：不做深度测试，因此被土石埋住的魔晶矿和躲在山体后的生物
     * 也能把外框画到屏幕上。线宽交给线条着色器按屏幕空间展开，远距离也看得清。
     */
    public static final RenderType SENSE_OUTLINE = RenderType.create("dimensional_mana_sense_outline",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 65536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(2.5)))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(LAYER)
                    .createCompositeState(false));
    public static final RenderType GLOW = RenderType.create("dimensional_mana_spell_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(LAYER)
                    .createCompositeState(false));
    /** 普通透明混合和环境光照；岩石、钢铁、烟雾不再走加色发光通道。 */
    public enum Material {
        STONE, ICE, STEEL, VINE, SMOKE, LEAF, LIQUID;
        public final RenderType type;
        Material() {
            String id = name().toLowerCase(java.util.Locale.ROOT);
            type = RenderType.create("dimensional_mana_spell_" + id,
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 65536, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                            .setTextureState(new TextureStateShard(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    GeneratedMod.MOD_ID, "textures/effect/" + id + ".png"), false, false))
                            .setLightmapState(LIGHTMAP)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(LAYER)
                            .createCompositeState(false));
        }
    }
    /** 图集共用三条渲染通道，切换帧只移动 UV，不为每张小粒子创建缓冲。 */
    public enum SpriteAtlas {
        SIGILS("sigils", 64, 64, 32),
        FRAMES("frames_smooth", 896, 192, 16),
        PARTICLES("particles", 128, 64, 8),
        /** 高级法阵：左格外阵、右格内阵，64 像素一格。 */
        SIGIL_ADVANCED("sigil_advanced", 128, 64, 64),
        /** 冰系高级法阵：左格大阵、右格内阵，零下领域的叠层仪式用。 */
        SIGIL_FROST("sigil_frost", 128, 64, 64),
        /** 万剑归一的天空裂缝：横向并排 8 帧动画，每帧 128x64。 */
        SKY_RIFT("sky_rift", 1024, 64, 128, 64),
        /** 完美招架时贴在怪物身上的闪光：横向并排 4 帧，每帧 32x32，共 8 刻播完。 */
        PARRY_FLASH("parry_flash", 128, 32, 32);

        public final RenderType type;
        public final int width, height, tile, tileWidth, tileHeight;

        SpriteAtlas(String file, int width, int height, int tile) {
            this(file, width, height, tile, tile);
        }

        SpriteAtlas(String file, int width, int height, int tileWidth, int tileHeight) {
            this.width = width;
            this.height = height;
            this.tile = tileWidth;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            type = RenderType.create("dimensional_mana_sprite_" + file,
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 65536, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                            .setTextureState(new TextureStateShard(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    GeneratedMod.MOD_ID, "textures/effect/casting/" + file + ".png"), false, false))
                            .setLightmapState(LIGHTMAP)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(LAYER)
                            .createCompositeState(false));
        }
    }
    private SpellRenderTypes() { super("dimensional_mana_spell_glow", () -> { }, () -> { }); }
}
