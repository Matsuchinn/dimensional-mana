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
     * <p>本设置保留为旧通道的输出层选项。光影检测启用后，辉光动态切换为
     * 普通 alpha 通道；完整核心和毒膜始终使用实体透明材质，不走天气混合。</p>
     */
    private static OutputStateShard pickLayer() {
        try {
            return RenderCompatConfig.SPELL_EFFECTS_COMPAT.get() ? TRANSLUCENT_TARGET : WEATHER_TARGET;
        } catch (Throwable ignored) {
            return TRANSLUCENT_TARGET;   // 配置还没加载：按兼容处理，宁可普通透明也不要盘子
        }
    }

    /** 类初始化时确定旧通道的输出层；动态兼容通道另行选用半透明目标。 */
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
    /** 光影开启时几何光效用普通 alpha，不把多层加色交给天气合成。 */
    public static final RenderType GLOW_COMPAT = RenderType.create("dimensional_mana_spell_glow_compat",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, true,
            RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false));
    public static RenderType glow() { return ShaderCompat.compatActive() ? GLOW_COMPAT : GLOW; }

    /** 带法线的完整表面，使用实体半透明着色器，透明膜不写深度。 */
    public enum Surface {
        VENOM_MEMBRANE;
        public final RenderType type;
        Surface() {
            String id = name().toLowerCase(java.util.Locale.ROOT);
            type = RenderType.create("dimensional_mana_surface_" + id, DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 65536, false, true,
                    RenderType.CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    GeneratedMod.MOD_ID, "textures/effect/surface/" + id + ".png"), false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(TRANSLUCENT_TARGET)
                            .createCompositeState(false));
        }
    }

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
        SIGILS("sigils", 128, 128, 64),
        FRAMES("frames_smooth", 896, 192, 16),
        /** 十二种元素和四种尾迹，32 像素原画，24 帧像素位移补间；最近邻保持像素边缘。 */
        MOTION("motion", 768, 512, 32),
        PARTICLES("particles", 128, 64, 8),
        /** 高级法阵：左格外阵、右格内阵，64 像素一格。 */
        SIGIL_ADVANCED("sigil_advanced", 128, 64, 64),
        /** 冰系高级法阵：左格大阵、右格内阵，零下领域的叠层仪式用。 */
        SIGIL_FROST("sigil_frost", 128, 64, 64),
        /** 万剑归一的天空裂缝：横向并排 8 帧动画，每帧 128x64。 */
        SKY_RIFT("sky_rift", 1024, 64, 128, 64),
        /** 完美招架时贴在怪物身上的闪光：横向并排 4 帧，每帧 32x32，共 8 刻播完。 */
        PARRY_FLASH("parry_flash", 128, 32, 32),
        /**
         * 斩击刀光：4 帧 x 2 行，每格 128x64；上行风、下行火。
         *
         * <p>一帧就是完整的一道弯月弧光（四帧从短到满长），整张图贴在一个平面上，
         * 看到的是一整段刀光划过，而不是一圈小碎粒。原画自带颜色，所以不走加色通道。</p>
         */
        SLASH("slash", 512, 128, 128, 64),
        /**
         * 4x4 格、每格 64 像素的柔光图集：径向光、光环、星芒、日轮、六边形、
         * 斜光、月牙、火星、双环、三角、雪花、旋涡、横竖光带、云团、符文圈。
         *
         * <p>这一张是渐变图，放大使用，所以开双线性过滤、四周留 4 像素透明边
         * （{@code pad}），并用加色混合——叠在场景上只会变亮，不会挡住后面的方块。</p>
         */
        GLOW("glow", 256, 256, 64, 64, 4, true);

        public final RenderType type;
        public final RenderType compatType;
        public RenderType renderType() { return additive && ShaderCompat.compatActive() ? compatType : type; }
        public final int width, height, tile, tileWidth, tileHeight;
        /** 图集内缩的像素数，UV 取格时往里收一点，防止采样到邻居格。 */
        public final int pad;
        /** 是否按「柔光」处理：双线性过滤 + 加色混合。 */
        public final boolean additive;

        SpriteAtlas(String file, int width, int height, int tile) {
            this(file, width, height, tile, tile, 0, false);
        }

        SpriteAtlas(String file, int width, int height, int tileWidth, int tileHeight) {
            this(file, width, height, tileWidth, tileHeight, 0, false);
        }

        SpriteAtlas(String file, int width, int height, int tileWidth, int tileHeight,
                    int pad, boolean additive) {
            this.width = width;
            this.height = height;
            this.tile = tileWidth;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.pad = pad;
            this.additive = additive;
            type = RenderType.create("dimensional_mana_sprite_" + file,
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 65536, false, !additive,
                    RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                            .setTextureState(new TextureStateShard(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    GeneratedMod.MOD_ID, "textures/effect/casting/" + file + ".png"),
                                    additive, false))
                            .setLightmapState(LIGHTMAP)
                            .setTransparencyState(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(LAYER)
                            .createCompositeState(false));
            compatType = additive ? RenderType.create("dimensional_mana_sprite_" + file + "_compat",
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 65536, false, true,
                    RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                            .setTextureState(new TextureStateShard(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    GeneratedMod.MOD_ID, "textures/effect/casting/" + file + ".png"), true, false))
                            .setLightmapState(LIGHTMAP).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(TRANSLUCENT_TARGET)
                            .createCompositeState(false)) : type;
        }
    }

    private SpellRenderTypes() { super("dimensional_mana_spell_glow", () -> { }, () -> { }); }
}
