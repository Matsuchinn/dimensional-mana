package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 晶化行尸：沿用原版僵尸骨架，换一张「更破败腐坏 + 遍布紫黑魔晶」的贴图。
 *
 * <p>两段表现都写在渲染这边（纯客户端，服务端只同步状态位）：
 * 晶爆尸潮的颤抖阶段（{@link CrystallizedZombie#isCrystalShock()}）整体抽搐抖动；
 * 狂暴啃咬期间（{@link CrystallizedZombie#isBiting()}）头部一顿一顿地上下啃。</p>
 */
public final class CrystallizedZombieRenderer extends ZombieRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/entity/crystallized_zombie.png");

    public CrystallizedZombieRenderer(EntityRendererProvider.Context context) {
        super(context);
        // 换成带「啃咬点头」的模型。原版僵尸的骨架、装备层都照旧挂着：
        // 那些渲染层每次都通过 getModel() 取父模型，所以换掉不会掉层。
        this.model = new BitingZombieModel(context.bakeLayer(ModelLayers.ZOMBIE));
    }

    /** 原版僵尸模型 + 啃咬点头：只在狂暴啃咬期间生效，其余时刻和原版一模一样。 */
    private static final class BitingZombieModel extends ZombieModel {
        /** 咬下去的幅度（弧度）：约 24 度，足以看出「上下啃」。 */
        private static final float CHOMP_ANGLE = 0.42F;

        BitingZombieModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(Monster entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (!(entity instanceof CrystallizedZombie zombie) || !zombie.isBiting()) return;
            // 每 10 刻一口 → 大约每 0.5 秒点一次头：正半周猛咬下去，负半周抬起来。
            float chomp = (float) Math.sin((double) ageInTicks * 1.55D) * CHOMP_ANGLE;
            this.head.xRot += chomp;
            // 帽子层是头的兄弟节点（原版在 setupAnim 末尾从 head 复制姿态），要一起动。
            this.hat.xRot += chomp;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return TEXTURE;
    }

    /** 晶爆尸潮颤抖：走原版的「发抖」通道（转换溺尸那套），配合下面的抖动位移。 */
    @Override
    protected boolean isShaking(Zombie entity) {
        return entity instanceof CrystallizedZombie zombie && zombie.isCrystalShock();
    }

    @Override
    public void render(Zombie entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        if (entity instanceof CrystallizedZombie zombie && zombie.isCrystalShock()) {
            // 高频率小幅抖动：越临近爆炸摆幅越大。
            float t = (zombie.tickCount + partialTick) * 1.9F;
            float amplitude = 0.03F + 0.05F * (1.0F - Math.min(1.0F, t % 80.0F / 80.0F));
            pose.translate(Math.sin(t * 1.37F) * amplitude, 0.0D, Math.cos(t * 1.71F) * amplitude);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (Math.sin(t * 0.9) * 4.0)));
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
