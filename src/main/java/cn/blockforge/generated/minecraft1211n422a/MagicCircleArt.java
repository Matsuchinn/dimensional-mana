package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * 法阵分层渲染引擎（纯客户端）：底盘层 → 连线层 → 节点层，全部走 PoseStack 矩阵变换。
 *
 * <p>三层结构（对应需求）：</p>
 * <ul>
 *   <li><b>Layer 1 底盘</b>：按基座数量（2×2 / ≥3×3）切换贴图，极慢顺时针自转，
 *       另叠一圈反向的细环，视觉上拉开层次；</li>
 *   <li><b>Layer 2 节点</b>：按数据包里的画布比例坐标 translate 到对应位置，
 *       每个节点按类型转自己的速度 / 方向（见 {@link #SPIN}），增幅带呼吸缩放、
 *       条件带极慢摆幅；</li>
 *   <li><b>Layer 3 连线</b>：一段段流光从上游游到下游（UV 沿流动方向滚动），
 *       颜色取上游节点染色。</li>
 * </ul>
 *
 * <p>颜色全部走顶点 {@code setColor}：贴图是全白线稿，元素粉染什么就是什么色。
 * 伪辉光（Bloom 错觉）：节点底下多画一张放大 2.1 倍的柔光贴片，和主体同走加色混合通道，
 * 叠在一起自然发亮。原版没有真 Bloom，这是标准做法。</p>
 *
 * <p><b>光影包适配</b>（见 {@link ShaderCompat}）：上面那套「加色混合 + 天气层缓冲」只在原版管线成立。
 * 开 Iris / Oculus / OptiFine 光影后，天气层缓冲会被光影自己的程序重新合成，加色混合被丢弃、
 * 顶点 alpha 也常常不再相乘，柔光贴片于是变成一块块<b>不透明盘子</b>。检测到光影时整套换通道：</p>
 * <ul>
 *   <li>混合换成普通半透明（{@code TRANSLUCENT_TRANSPARENCY}）——所有光影包都老实执行它；</li>
 *   <li>输出层从「天气缓冲」换成主画面，不让光影再合成第二遍；</li>
 *   <li>伪辉光垫 alpha 压到三分之一、尺寸收紧；贴图本身的渐变峰值也已从 255 压到 205，
 *       万一某个光影包完全不理会顶点 alpha，最差也只是一团边缘透明的柔雾，不会成盘子；</li>
 *   <li>流光和基准线改用图集里专门的柔边贴图（STREAK / RAIL），四边全淡出，
 *       任何混合模式下都不会出现矩形硬边。</li>
 * </ul>
 *
 * <p>性能：整帧最多开 3 个 RenderType（两张底盘 + 一张图集），所有节点共用图集
 * 一次批量提交；每帧零对象分配——数据包是 {@link MagicCircleRenderPacket} 预先摊平的数组。</p>
 */
public final class MagicCircleArt {
    /** 全亮光照：法阵自带发光，不看天光和方块光。 */
    private static final int FULL_LIGHT = 0x00F000F0;

    private static final String TEX = "textures/effect/";
    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, TEX + name);
    }

    // ------------------------------------------------------------------ 渲染通道（静态缓存，全程复用）

    /** 图集 4×4 里额外占用的两张柔边图：流光短带、连线基准轨道。 */
    static final int TILE_FLARE = 13;
    static final int TILE_STREAK = 14;
    static final int TILE_RAIL = 15;

    /** 节点 / 流光：加色混合，叠着会发光。一张图集装 13 种图形 + 柔光 + 流光带。 */
    static final RenderType GLYPHS = channel("dimensional_mana_mc_glyphs", tex("mc_glyphs.png"),
            RenderStateShard.ADDITIVE_TRANSPARENCY, RenderStateShard.WEATHER_TARGET, true);
    /** 底盘：普通半透明（大面积加色在亮背景会糊成一片）。 */
    static final RenderType PLATE_2 = channel("dimensional_mana_mc_plate_2", tex("mc_plate_2.png"),
            RenderStateShard.TRANSLUCENT_TRANSPARENCY, RenderStateShard.WEATHER_TARGET, false);
    static final RenderType PLATE_3 = channel("dimensional_mana_mc_plate_3", tex("mc_plate_3.png"),
            RenderStateShard.TRANSLUCENT_TRANSPARENCY, RenderStateShard.WEATHER_TARGET, false);

    // 光影兼容通道：普通半透明混合 + 直接画进主画面，不进任何会被光影重合成的分层缓冲。
    static final RenderType GLYPHS_SAFE = safeChannel("dimensional_mana_mc_glyphs_safe", tex("mc_glyphs.png"));
    static final RenderType PLATE_2_SAFE = safeChannel("dimensional_mana_mc_plate_2_safe", tex("mc_plate_2.png"));
    static final RenderType PLATE_3_SAFE = safeChannel("dimensional_mana_mc_plate_3_safe", tex("mc_plate_3.png"));

    private static RenderType channel(String name, ResourceLocation texture,
                                      RenderStateShard.TransparencyStateShard blend,
                                      RenderStateShard.OutputStateShard target, boolean sort) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 262144, sort, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setTransparencyState(blend)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(target)
                        .createCompositeState(false));
    }

    /** 光影友好通道：着色器与原版半透明方块完全一致（光影包必认），混合为常规 SRC_ALPHA。 */
    private static RenderType safeChannel(String name, ResourceLocation texture) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 262144, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .createCompositeState(false));
    }

    // ------------------------------------------------------------------ 图形映射

    /** 自转速度表（图集格号 → 度/刻，负=顺时针，正=逆时针）——需求里的映射，数据驱动。 */
    private static final float[] SPIN = {
            -0.6f,  // 0 CORE 大双圆：近乎静止，极慢
            -2.4f,  // 1 TRIGGER 钟表盘：顺时针，中速
            4.8f,   // 2 OUTPUT 锐利三角：逆时针，快速
            3.0f,   // 3 AMPLIFY 六芒星：逆时针，中速（另有呼吸缩放）
            0.0f,   // 4 CONDITION 菱形沙漏：不恒转，走摆幅
            3.6f,   // 5 SUPPLY 闪电环：逆时针
            -1.2f,  // 6 AREA 扩散环：顺时针慢
            1.5f,   // 7 DURATION 循环箭：逆时针
            -1.8f,  // 8 FILTER 漏斗：顺时针
            2.0f,   // 9 LINK 双环：逆时针
            -0.3f,  // 10 STABILIZE 三角撑：几乎不动
            0.9f,   // 11 STORAGE 六边仓：缓
            -3.0f,  // 12 DIRECTION 罗盘：顺时针快
            0.0f,   // 13 FLARE 柔光：不转
            0.0f,   // 14 STREAK 流光带：不转
            0.0f,   // 15 RAIL 基准轨道：不转
    };

    // ------------------------------------------------------------------ 主入口

    /**
     * 画一整座阵。
     *
     * @param packet     预计算好的渲染数据包（只读）
     * @param facing     阵面法线朝哪：绘制台恒 UP；石板按贴面方向
     * @param sizeX      画布横向铺多少格（绘制台=基座数；石板≈1）
     * @param sizeZ      画布纵向铺多少格
     * @param t          已经做过距离降频处理的时间轴（游戏刻 + partialTick 插值）
     * @param active     正式运行（石板启动态）：更亮、呼吸更快
     * @param baseLift   底盘离参考面的高度（绘制台用 +0.2 悬空，石板用贴面厚度）
     */
    public static void render(MagicCircleRenderPacket packet, PoseStack poseStack,
                              MultiBufferSource buffers, Direction facing,
                              float sizeX, float sizeZ, double t, boolean active, float baseLift) {
        if (packet == null || packet.isEmpty()) return;
        renderInner(packet, poseStack, buffers, facing, sizeX, sizeZ, t, active, baseLift, true);
    }

    /** 远距（>32 格）只画底盘层的入口。 */
    public static void renderPlateOnly(MagicCircleRenderPacket packet, PoseStack poseStack,
                                       MultiBufferSource buffers, Direction facing,
                                       float sizeX, float sizeZ, double t, boolean active, float baseLift) {
        if (packet == null || packet.isEmpty()) return;
        renderInner(packet, poseStack, buffers, facing, sizeX, sizeZ, t, active, baseLift, false);
    }

    private static void renderInner(MagicCircleRenderPacket packet, PoseStack poseStack,
                                    MultiBufferSource buffers, Direction facing,
                                    float sizeX, float sizeZ, double t, boolean active,
                                    float baseLift, boolean detail) {
        // 老服务器游戏刻能跑到千万级，float 装不下这么大的角度，先卷回 200 万刻一圈
        // （约 28 小时连续盯着看才回绕一次，肉眼察觉不到）。
        t = t % 2_000_000.0;
        // 光影包接管混合时走「兼容」一套：不用加色、不进分层缓冲、伪辉光收成薄雾。
        final boolean safe = ShaderCompat.compatActive();
        float pulse = active ? 0.80F + 0.20F * Mth.sin((float) (t * 0.14))
                : 0.62F + 0.12F * Mth.sin((float) (t * 0.05));

        poseStack.pushPose();
        applyFacing(poseStack, facing);

        // ---------------- Layer 1：底盘，极慢顺时针 + 反向细环 ----------------
        drawPlate(poseStack, buffers, packet, sizeX, sizeZ, t, baseLift, pulse, active, safe);

        if (detail) {
            // ---------------- Layer 3：连线流光（先画，被节点盖住接头更自然） ----------------
            VertexConsumer glyphBuf = buffers.getBuffer(safe ? GLYPHS_SAFE : GLYPHS);
            drawLinks(glyphBuf, poseStack, packet, sizeX, sizeZ, t, baseLift, pulse, active, safe);
            // ---------------- Layer 2：节点图案 ----------------
            drawNodes(glyphBuf, poseStack, packet, sizeX, sizeZ, t, baseLift, pulse, active, safe);
        }
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ 底盘

    private static void drawPlate(PoseStack poseStack, MultiBufferSource buffers,
                                  MagicCircleRenderPacket packet, float sizeX, float sizeZ,
                                  double t, float baseLift, float pulse, boolean active, boolean safe) {
        RenderType plateType = safe
                ? (Math.max(packet.baseW, packet.baseH) >= 3 ? PLATE_3_SAFE : PLATE_2_SAFE)
                : (Math.max(packet.baseW, packet.baseH) >= 3 ? PLATE_3 : PLATE_2);
        float span = Math.max(sizeX, sizeZ);

        // 外圈柔光垫：底盘底下先铺一张淡光，贴脸看有“光从阵纹里透出来”的错觉。
        // 只取图集里 13 号 FLARE 那格——早先误用了整张图集的 UV，才会画出一整块方形的杂讯盘子
        // （开光影后那块方形就是玩家看到的「不透明盘子」）。FLARE 的可见光晕只占格子中间约一半，
        // 所以贴片要铺到 span*1.5 才有原先那种大面积氛围光。
        if (!safe) {
            PoseStack.Pose pose = poseStack.last();
            VertexConsumer halo = buffers.getBuffer(GLYPHS);
            float hy = baseLift - 0.012F;
            float hs = span * 1.5F;
            int haloAlpha = (int) ((active ? 46 : 26) * pulse);
            tileUv(TILE_FLARE, UV);
            quadUv(halo, pose, -hs, hy, -hs, hs, hy, -hs, hs, hy, hs, -hs, hy, hs,
                    0.62F, 0.80F, 1.00F, haloAlpha, UV[0], UV[2], UV[1], UV[3]);
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, baseLift, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(
                (float) (-t * 0.35 * Mth.DEG_TO_RAD))); // 极慢顺时针：一整圈约 17 分钟*，看得出在转但不吵
        float px = span * 0.56F, pz = px * (sizeZ / Math.max(1e-3F, sizeX));
        VertexConsumer plateBuf = buffers.getBuffer(plateType);
        PoseStack.Pose platePose = poseStack.last();
        // 兼容模式下线稿本体要更实一点：没有加色叠加，靠 alpha 撑住亮度。
        int plateAlpha = clamp255((int) ((active ? 150 : 96) * pulse * (safe ? 1.35F : 1.0F)));
        quadUv(plateBuf, platePose, -px, 0, -pz, px, 0, -pz, px, 0, pz, -px, 0, pz,
                0.75F, 0.88F, 1.0F, plateAlpha, 0, 0, 1, 1);
        // 第二圈：反向、快一档的细内环（同贴图缩到 62%），两层环反向咬合，华丽感全靠它
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(0.0F, baseLift + 0.016F, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(
                (float) (t * 0.55 * Mth.DEG_TO_RAD)));
        float ix = px * 0.62F, iz = pz * 0.62F;
        quadUv(plateBuf, poseStack.last(), -ix, 0, -iz, ix, 0, -iz, ix, 0, iz, -ix, 0, iz,
                0.75F, 0.88F, 1.0F, (int) (plateAlpha * 0.66F), 0, 0, 1, 1);
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ 节点层

    private static void drawNodes(VertexConsumer buf, PoseStack poseStack,
                                  MagicCircleRenderPacket packet, float sizeX, float sizeZ,
                                  double t, float baseLift, float pulse, boolean active, boolean safe) {
        float tile = 0.55F * (sizeX / Math.max(1, packet.baseW)); // 节点边长：台上≈1.1 格，石板上按比例缩
        for (int i = 0; i < packet.nodeCount(); i++) {
            float x = (packet.nodeFx[i] - 0.5F) * sizeX;
            float z = (0.5F - packet.nodeFz[i]) * sizeZ;
            float y = baseLift + 0.05F + packet.nodeLayer[i] * 0.055F;
            int argb = packet.nodeColor[i];
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            int glyph = packet.nodeGlyph[i] & 15;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            float angle = nodeSpin(glyph, t);
            if (angle != 0.0F) poseStack.mulPose(Axis.YP.rotation(angle));
            float scale = 1.0F;
            if (glyph == 3) scale = 1.0F + 0.13F * Mth.sin((float) (t * 0.09)); // 增幅：呼吸缩放
            if (active) scale *= 1.0F + 0.05F * Mth.sin((float) (t * 0.21 + i)); // 运行时整阵轻微起伏

            // ---------------- 伪辉光：两套做法 ----------------
            // 原版：节点底下铺一张放大 2.1 倍的柔光贴片 + 主体 + 高亮内芯，三层同走加色通道，
            //       叠在一起自然发亮（原版没有真 Bloom，这是标准做法）。
            // 光影：加色会被光影丢掉、贴片会读成实心盘子，于是改成「同一张线稿放大几层重画」当光晕——
            //       线稿本体是细笔画 + 真透明，哪怕某个光影包完全不理会顶点 alpha，也只是多出几圈淡线，
            //       绝不可能成盘子。
            float half = tile * scale;
            if (safe) {
                quadTile(buf, poseStack.last(), half * 1.34F, glyph, r, g, b, clamp255((int) (a * 0.15F * pulse)));
                quadTile(buf, poseStack.last(), half * 1.17F, glyph, r, g, b, clamp255((int) (a * 0.30F * pulse)));
                quadTile(buf, poseStack.last(), half, glyph, r, g, b, clamp255((int) (a * 1.25F * pulse)));
            } else {
                quadTile(buf, poseStack.last(), tile * 2.1F * scale, glyph == TILE_FLARE ? 0 : TILE_FLARE,
                        r, g, b, (int) (a * 0.30F * pulse));
                quadTile(buf, poseStack.last(), half, glyph, r, g, b, (int) (a * pulse));
                quadTile(buf, poseStack.last(), half * 0.55F, glyph,
                        Math.min(255, r + 110), Math.min(255, g + 110), Math.min(255, b + 110),
                        (int) (a * 0.72F * pulse));
            }
            poseStack.popPose();
        }
    }

    /** 单个节点的旋转角（弧度）。数据驱动：查 SPIN 表，条件节点走正弦摆幅。 */
    private static float nodeSpin(int glyph, double t) {
        if (glyph == 4) return (float) Math.sin(t * 0.018) * 1.15F; // 条件：极慢往复摆
        double deg = (SPIN[glyph] * t) % 360.0; // 角度先卷到一整圈内再收 float，长期运行不抖
        return (float) (deg * Mth.DEG_TO_RAD);
    }

    // ------------------------------------------------------------------ 连线层

    private static void drawLinks(VertexConsumer buf, PoseStack poseStack,
                                  MagicCircleRenderPacket packet, float sizeX, float sizeZ,
                                  double t, float baseLift, float pulse, boolean active, boolean safe) {
        tileUv(TILE_STREAK, UV);
        float su0 = UV[0], su1 = UV[1], sv0 = UV[2], sv1 = UV[3];
        tileUv(TILE_RAIL, UV);
        float ru0 = UV[0], ru1 = UV[1], rv0 = UV[2], rv1 = UV[3];
        for (int i = 0; i < packet.linkFrom.length; i++) {
            int fa = packet.linkFrom[i], fb = packet.linkTo[i];
            if (fa < 0 || fb < 0 || fa >= packet.nodeCount() || fb >= packet.nodeCount()) continue;
            float ax = (packet.nodeFx[fa] - 0.5F) * sizeX;
            float az = (0.5F - packet.nodeFz[fa]) * sizeZ;
            float bx = (packet.nodeFx[fb] - 0.5F) * sizeX;
            float bz = (0.5F - packet.nodeFz[fb]) * sizeZ;
            float dx = bx - ax, dz = bz - az;
            float len = Mth.sqrt(dx * dx + dz * dz);
            if (len < 0.02F) continue;
            float yaw = (float) Mth.atan2(-dz, dx); // 局部 +X 旋到线段方向（z 取反是因为 MC 的 +Z 朝南）
            int argb = packet.nodeColor[fa];
            int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
            float y = baseLift + 0.04F + packet.nodeLayer[fa] * 0.055F;

            poseStack.pushPose();
            poseStack.translate(ax, y, az);
            poseStack.mulPose(Axis.YP.rotation(yaw));
            int dashes = Mth.clamp(Mth.floor(len / 0.7F), 1, 12);
            double scroll = (t * 0.03) % 1.0; // UV 滚动相位：流光沿 from→to 走
            PoseStack.Pose pose = poseStack.last();
            float halfW = 0.085F;
            float dashHalf = 0.16F;
            for (int dIdx = 0; dIdx < dashes; dIdx++) {
                float u = (float) (((scroll + (double) dIdx / dashes) % 1.0));
                float cx = (u * len) - dashHalf - 0.02F;
                if (cx < -dashHalf || cx > len - dashHalf) continue; // 端点外的半截直接裁掉，clamp 贴图不会拖尾
                float a0 = Math.max(-dashHalf + 0.001F, cx - dashHalf);
                float b0 = Math.min(len - dashHalf, cx + dashHalf) ;
                if (b0 <= a0) continue;
                quadXZ(buf, pose, a0, b0, -halfW, halfW, r, g, b,
                        clamp255((int) (110 * pulse * (safe ? 1.15F : 1.0F))), su0, su1, sv0, sv1);
            }
            // 常亮的基准细线：让连线“本来就在”，流光只是上面跑的高亮段
            quadXZ(buf, pose, 0.0F, len, -halfW * 0.45F, halfW * 0.45F,
                    Math.min(255, r + 60), Math.min(255, g + 60), Math.min(255, b + 60),
                    (int) ((active ? 58 : 34) * pulse * (safe ? 1.4F : 1.0F)), ru0, ru1, rv0, rv1);
            poseStack.popPose();
        }
    }

    // ------------------------------------------------------------------ 工具：面片提交

    /** 亮度增益后要把 alpha 夹回 0~255：顶点色是无符号字节，超了会翻成别的值。 */
    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** 图集格子的 UV（半像素内缩，防止采样到邻格）。 */
    private static void tileUv(int glyph, float[] out) {
        float col = glyph % 4, row = glyph / 4;
        float s = 0.25F, inset = 0.5F / 512.0F;
        out[0] = col * s + inset;
        out[1] = col * s + s - inset;
        out[2] = row * s + inset;
        out[3] = row * s + s - inset;
    }

    private static final float[] UV = new float[4];

    private static void quadTile(VertexConsumer buf, PoseStack.Pose pose, float half, int glyph,
                                 int r, int g, int b, int a) {
        if (a <= 0) return;
        tileUv(glyph, UV);
        float y = 0.0F;
        buf.addVertex(pose, -half, y, -half).setColor(r, g, b, a).setUv(UV[0], UV[2]).setLight(FULL_LIGHT);
        buf.addVertex(pose, half, y, -half).setColor(r, g, b, a).setUv(UV[1], UV[2]).setLight(FULL_LIGHT);
        buf.addVertex(pose, half, y, half).setColor(r, g, b, a).setUv(UV[1], UV[3]).setLight(FULL_LIGHT);
        buf.addVertex(pose, -half, y, half).setColor(r, g, b, a).setUv(UV[0], UV[3]).setLight(FULL_LIGHT);
    }

    /** XZ 平面上一条沿 X 的带子（连线用）；UV 由调用方给定的图块范围提供。 */
    private static void quadXZ(VertexConsumer buf, PoseStack.Pose pose,
                               float x0, float x1, float z0, float z1, int r, int g, int b, int a,
                               float u0, float u1, float v0, float v1) {
        if (a <= 0) return;
        float y = 0.0F;
        buf.addVertex(pose, x0, y, z0).setColor(r, g, b, a).setUv(u0, v0).setLight(FULL_LIGHT);
        buf.addVertex(pose, x1, y, z0).setColor(r, g, b, a).setUv(u1, v0).setLight(FULL_LIGHT);
        buf.addVertex(pose, x1, y, z1).setColor(r, g, b, a).setUv(u1, v1).setLight(FULL_LIGHT);
        buf.addVertex(pose, x0, y, z1).setColor(r, g, b, a).setUv(u0, v1).setLight(FULL_LIGHT);
    }

    /** 任意四角的面片（底盘 / 柔光垫用），UV 由调用方给定。 */
    private static void quadUv(VertexConsumer buf, PoseStack.Pose pose,
                               float x1, float y1, float z1, float x2, float y2, float z2,
                               float x3, float y3, float z3, float x4, float y4, float z4,
                               float r, float g, float b, int a,
                               float u0, float v0, float u1, float v1) {
        if (a <= 0) return;
        buf.addVertex(pose, x1, y1, z1).setColor((int) (r * 255), (int) (g * 255), (int) (b * 255), a).setUv(u0, v0).setLight(FULL_LIGHT);
        buf.addVertex(pose, x2, y2, z2).setColor((int) (r * 255), (int) (g * 255), (int) (b * 255), a).setUv(u1, v0).setLight(FULL_LIGHT);
        buf.addVertex(pose, x3, y3, z3).setColor((int) (r * 255), (int) (g * 255), (int) (b * 255), a).setUv(u1, v1).setLight(FULL_LIGHT);
        buf.addVertex(pose, x4, y4, z4).setColor((int) (r * 255), (int) (g * 255), (int) (b * 255), a).setUv(u0, v1).setLight(FULL_LIGHT);
    }

    // ------------------------------------------------------------------ 朝向

    /**
     * 把「水平面（法线 +Y，画布 x→东、下→北）」的局部系转到阵面实际朝向。
     * 与 {@link MagicCircleProjectionGeometry} 的光点映射保持同向，石板换态时图案不打转。
     */
    private static void applyFacing(PoseStack poseStack, Direction facing) {
        if (facing == null) facing = Direction.UP;
        switch (facing) {
            case UP -> { }
            case DOWN -> poseStack.mulPose(Axis.XP.rotation(Mth.PI));
            case NORTH -> poseStack.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
            case SOUTH -> {
                poseStack.mulPose(Axis.YP.rotation(Mth.PI));
                poseStack.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
            }
            case EAST -> {
                poseStack.mulPose(Axis.YP.rotation(Mth.HALF_PI));
                poseStack.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
            }
            case WEST -> {
                poseStack.mulPose(Axis.YP.rotation(-Mth.HALF_PI));
                poseStack.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
            }
        }
    }

    private MagicCircleArt() {}
}
