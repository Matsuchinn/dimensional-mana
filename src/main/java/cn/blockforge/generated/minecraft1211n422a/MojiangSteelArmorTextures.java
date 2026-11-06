package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 穿在身上的魔精钢护甲动态贴图。
 *
 * <p>原版护甲层是独立贴图，不在图集里，所以 {@code .png.mcmeta} 的逐帧动画对它不起作用。
 * 这里把帧图读成 {@link NativeImage}，注册成一个会自己翻页的 {@link DynamicTexture}，
 * 直接顶替护甲层原本要取的那个纹理位置。</p>
 *
 * <p>r291 起帧图改成“同一套底图 + 几处错相呼吸光”：12 帧的轮廓、配色、花纹完全一致，
 * 只有胸口、护肩、护腿等几个固定点位按错开的相位亮暗起伏，因此穿在身上是稳定的光波，
 * 不再是整套护甲换样子。每 3 刻换一帧，一轮约 1.8 秒。</p>
 *
 * <p>资源包重载（F3+T、切存档）会把纹理清空，所以每客户端刻检查一次，发现旧纹理不在了就重新登记。</p>
 */
public final class MojiangSteelArmorTextures {
    private static final int FRAMES = 12;
    private static final int FRAME_TICKS = 3;
    private static final String INNER = "mojing_steel_layer_1";
    private static final String OUTER = "mojing_steel_layer_2";
    private static final ResourceLocation INNER_TEXTURE = armorTexture(INNER);
    private static final ResourceLocation OUTER_TEXTURE = armorTexture(OUTER);

    public static void tick(ClientTickEvent.Post event) {
        ensure(INNER_TEXTURE, INNER);
        ensure(OUTER_TEXTURE, OUTER);
    }

    private static ResourceLocation armorTexture(String name) {
        // 护甲层纹理键是带 .png 的完整路径（ArmorMaterial.Layer 内部就是这么拼的），不能省后缀。
        return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
                "textures/models/armor/" + name + ".png");
    }

    private static void ensure(ResourceLocation target, String frameBase) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureManager textures = minecraft.getTextureManager();
        AbstractTexture current = textures.getTexture(target, null);
        // 已经是本类的动态纹理就不必重复登记。
        if (current instanceof AnimatedArmorTexture) return;
        // 纹理被清空说明刚重载过资源包，允许重新尝试一次。
        if (current == null) framesMissing = false;
        // 上一次读帧图失败过就别每刻重试了；退回静态帧保证护甲不会变紫黑格。
        if (framesMissing) return;
        List<NativeImage> frames = new ArrayList<>(FRAMES);
        try {
            for (int i = 0; i < FRAMES; i++) {
                ResourceLocation frame = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
                        "textures/models/armor/" + frameBase + "_" + i + ".png");
                try (InputStream stream = minecraft.getResourceManager().open(frame)) {
                    frames.add(NativeImage.read(stream));
                }
            }
            textures.register(target, new AnimatedArmorTexture(frames));
        } catch (Exception exception) {
            // 帧图缺了也不能让护甲变成紫黑格：退回第一帧的静态贴图。
            // 注意 SimpleTexture 自己不会补 textures/ 前缀和 .png 后缀，这里要给完整路径。
            framesMissing = true;
            for (NativeImage image : frames) image.close();
            ResourceLocation still = ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID,
                    "textures/models/armor/" + frameBase + ".png");
            textures.register(target, new SimpleTexture(still));
        }
    }

    /** 帧图读取失败过；资源包重载后清空重试。 */
    private static boolean framesMissing;

    /** 自己会翻页的动态纹理；由 {@link TextureManager#tick()} 自动驱动。 */
    private static final class AnimatedArmorTexture extends DynamicTexture implements Tickable {
        private final List<NativeImage> frames;
        private int frame;
        private int ticks;

        AnimatedArmorTexture(List<NativeImage> frames) {
            super(copyOf(frames.get(0)));
            this.frames = frames;
        }

        @Override
        public void tick() {
            if (frames.size() < 2) return;
            if (++ticks < FRAME_TICKS) return;
            ticks = 0;
            frame = (frame + 1) % frames.size();
            NativeImage pixels = getPixels();
            if (pixels == null) return;
            pixels.copyFrom(frames.get(frame));
            upload();
        }

        @Override
        public void close() {
            super.close();
            for (NativeImage image : frames) image.close();
        }

        private static NativeImage copyOf(NativeImage source) {
            NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false);
            copy.copyFrom(source);
            return copy;
        }
    }

    private MojiangSteelArmorTextures() { }
}
