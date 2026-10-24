package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

/** 双面、带遮挡的加色光效，不写入深度，避免透明外壳遮掉内部光芯。 */
public final class SpellRenderTypes extends RenderStateShard {
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
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));
    public static final RenderType GLOW = RenderType.create("dimensional_mana_spell_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(WEATHER_TARGET)
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
                            .setOutputState(WEATHER_TARGET)
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
        /** 完美招架时贴在怪物身上的闪光：横向并排 4 帧，每帧 16x16，共 8 刻播完。 */
        PARRY_FLASH("parry_flash", 64, 16, 16);

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
                            .setOutputState(WEATHER_TARGET)
                            .createCompositeState(false));
        }
    }
    private SpellRenderTypes() { super("dimensional_mana_spell_glow", () -> { }, () -> { }); }
}
