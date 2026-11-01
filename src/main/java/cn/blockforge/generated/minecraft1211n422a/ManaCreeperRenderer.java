package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;

/**
 * 聚魔爆裂体的渲染：沿用原版苦力怕骨架，换两张贴图。
 *
 * <ul>
 *   <li>平时用 {@code mana_creeper.png}：整只被紫黑魔晶覆盖；</li>
 *   <li>魔核冷却（{@link ManaCreeper#isCoreCooling()}）期间换成去色的
 *       {@code mana_creeper_cooling.png}，再叠一层半透明的灰白霜壳
 *       （{@code mana_creeper_frost.png}），保证一眼就能看出「结霜了」；</li>
 *   <li>蓄力 / 引信期间靠 {@link #getWhiteOverlayProgress} 让模型持续闪白，
 *       膨胀动画由 {@link ManaCreeper#getSwelling(float)} 驱动。</li>
 * </ul>
 *
 * <p>灰白状态读的是实体同步字段（{@link ManaCreeper#isCoreCooling()}），
 * 和晶化行尸的晶爆抖动同一套写法，客户端一定拿得到。</p>
 */
public final class ManaCreeperRenderer extends CreeperRenderer {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("dimensional_mana/mana_creeper");

    private static final ResourceLocation NORMAL = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/mana_creeper.png");
    private static final ResourceLocation COOLING = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/mana_creeper_cooling.png");
    /** 冷却期盖在身上的灰白霜壳（半透明，霜层不挡住晶体细节）。 */
    private static final ResourceLocation FROST = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/mana_creeper_frost.png");

    /** 上次画的时候是不是灰白，只在切换的那一帧打一行日志，避免刷屏。 */
    private boolean lastCooling;

    public ManaCreeperRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new FrostShellLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Creeper creeper) {
        boolean cooling = creeper instanceof ManaCreeper manaCreeper && manaCreeper.isCoreCooling();
        if (cooling != lastCooling) {
            lastCooling = cooling;
            // 一行诊断：真的换上了就记一笔，万一还有问题，看日志就知道卡在哪一侧。
            LOGGER.info("聚魔爆裂体渲染切换到{}", cooling ? "灰白冷却造型" : "常态紫晶造型");
        }
        return cooling ? COOLING : NORMAL;
    }

    /** 蓄力全程高频闪白，越接近释放闪得越狠；冷却期则完全不闪。 */
    @Override
    protected float getWhiteOverlayProgress(Creeper creeper, float partialTick) {
        if (!(creeper instanceof ManaCreeper manaCreeper)) return super.getWhiteOverlayProgress(creeper, partialTick);
        if (manaCreeper.isCoreCooling()) return 0.0F;
        float swelling = manaCreeper.getSwelling(partialTick);
        if (swelling <= 0.0F) return super.getWhiteOverlayProgress(creeper, partialTick);
        // 每 2 刻闪一次；后半段把强度拉满。
        float pulse = (int) (swelling * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(swelling, 0.0F, 1.0F);
        return Math.max(pulse, swelling > 0.85F ? 0.85F : 0.0F);
    }

    /** 蓄力时整个人（整只怪）轻微上浮抖动，读起来像核在憋。 */
    @Override
    public void render(Creeper creeper, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        if (creeper instanceof ManaCreeper manaCreeper && manaCreeper.isCoreCharging()) {
            float t = (creeper.tickCount + partialTick) * 0.9F;
            float amplitude = 0.012F + 0.03F * manaCreeper.getSwelling(partialTick);
            pose.translate(Math.sin(t * 2.3F) * amplitude, Math.abs(Math.sin(t * 3.1F)) * amplitude,
                    Math.cos(t * 1.7F) * amplitude);
        }
        super.render(creeper, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /**
     * 灰白霜壳：冷却时把同一副骨架再画一遍，贴半透明的灰白霜层、整体往外撑 2%。
     *
     * <p>这是一层「保险」——哪怕哪天贴图切换那条路又出岔子，这层壳也会把整只怪压成灰白。</p>
     */
    private static final class FrostShellLayer extends RenderLayer<Creeper, CreeperModel<Creeper>> {
        private static final float INFLATE = 1.02F;

        FrostShellLayer(ManaCreeperRenderer parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, Creeper creeper,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!(creeper instanceof ManaCreeper manaCreeper) || !manaCreeper.isCoreCooling()) return;
            pose.pushPose();
            pose.scale(INFLATE, INFLATE, INFLATE);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(FROST));
            this.getParentModel().renderToBuffer(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            pose.popPose();
        }
    }
}
