package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 小僵尸比例的藤蔓人、分节毒藤、悬浮铁剑，以及透明冰块身体加南瓜头的寒冰傀儡。 */
public final class SpellSummonRenderer extends EntityRenderer<SpellSummon> {
    private static final ResourceLocation BARK = texture("vine_bark"), FACE = texture("vine_face");
    /** 傀儡身体直接借用原版冰块的半透明材质，不额外画一套贴图。 */
    private static final ResourceLocation ICE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/ice.png");
    private final ItemRenderer items;
    public SpellSummonRenderer(EntityRendererProvider.Context context) { super(context); items = context.getItemRenderer(); shadowRadius = 0.25F; }
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "textures/entity/" + name + ".png"); }
    @Override public ResourceLocation getTextureLocation(SpellSummon entity) { return entity.golem() ? ICE : BARK; }
    @Override public void render(SpellSummon entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float age = entity.tickCount + partial;
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        if (entity.golem()) {
            renderGolem(entity, partial, pose, buffers, light);
        } else if (entity.sword()) {
            float attack = entity.getAttackAnim(partial);
            float slash = (float)Math.sin(attack * Math.PI);
            pose.translate(0, 0.6 + Math.sin(age * 0.08) * 0.06, slash * 0.28);
            pose.mulPose(Axis.XP.rotationDegrees(-slash * 105));
            pose.mulPose(Axis.ZP.rotationDegrees(-45 + slash * 24)); pose.scale(1.4F, 1.4F, 1.4F);
            items.renderStatic(new ItemStack(Items.IRON_SWORD), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, entity.level(), entity.getId());
        } else {
            float scale = entity.tentacle() ? 1 / 16F : 0.8F / 16F;
            pose.scale(scale, scale, scale);
            if (entity.tentacle()) {
                cube(pose, buffers, light, -4, 0, -4, 4, 3, 4, false, 0xB1B86A);
                for (int i = 0; i < 4; i++) {
                    pose.translate(0, i == 0 ? 3 : 4.6, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees((float)Math.sin(age * 0.08 + i * 0.65) * 9));
                    double width = 2.6 - i * 0.45;
                    cube(pose, buffers, light, -width, 0, -width, width, 5, width, i == 3, i == 3 ? 0xD7B3EF : 0xB5CF82);
                    cube(pose, buffers, light, width, 2, -0.5, width + 1.4, 3.3, 0.5, false, 0xDDB3EE);
                }
            } else {
                float walk = (float)Math.cos(entity.walkAnimation.position(partial) * 0.9) * Math.min(1, entity.walkAnimation.speed(partial)) * 38;
                float attack = entity.getAttackAnim(partial);
                float strike = (float)Math.sin(attack * Math.PI) * 105;
                cube(pose, buffers, light, -2.4, 5.8, -1.4, 2.4, 11.6, 1.4, false, 0xFFFFFF);
                cube(pose, buffers, light, -3.5, 11.6, -3.1, 3.5, 18.6, 3.1, true, 0xFFFFFF);
                limb(pose, buffers, light, -1.2, 5.8, walk, 1.1, 5.8);
                limb(pose, buffers, light, 1.2, 5.8, -walk, 1.1, 5.8);
                limb(pose, buffers, light, -3.4, 11.1, -55 - walk * 0.4F - strike, 0.95, 5.7);
                limb(pose, buffers, light, 3.4, 11.1, -55 + walk * 0.4F - strike * 0.65F, 0.95, 5.7);
                cube(pose, buffers, light, -4, 16.2, -0.3, -3.4, 18.2, 1.6, false, 0xA1E76B);
                cube(pose, buffers, light, 1, 18.6, -0.2, 3.4, 19.3, 1.4, false, 0xB0F06D);
            }
        }
        pose.popPose(); super.render(entity, yaw, partial, pose, buffers, light);
    }

    /** 透明冰块身体 + 原版雕刻南瓜头，比例参照雪傀儡。 */
    private void renderGolem(SpellSummon entity, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float walk = (float)Math.cos(entity.walkAnimation.position(partial) * 0.9) * Math.min(1, entity.walkAnimation.speed(partial));
        float bob = (float)Math.sin((entity.tickCount + partial) * 0.09) * 0.02F;
        pose.pushPose();
        pose.translate(0, bob, 0);
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        // 双腿与躯干：冰块材质让内部轮廓若隐若现，符合“透明冰块”。
        iceCube(pose, buffers, light, -5.0, 0, -4.0, -1.0, 6.0, 4.0);
        iceCube(pose, buffers, light, 1.0, 0, -4.0, 5.0, 6.0, 4.0);
        iceCube(pose, buffers, light, -7.0, 6.0, -5.0, 7.0, 18.0, 5.0);
        // 两条冰臂随步伐前后摆动。
        pose.pushPose();
        pose.translate(-7.0, 16.0, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(walk * 9));
        iceCube(pose, buffers, light, -3.0, -10.0, -2.5, 0.0, 0.0, 2.5);
        pose.popPose();
        pose.pushPose();
        pose.translate(7.0, 16.0, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-walk * 9));
        iceCube(pose, buffers, light, 0.0, -10.0, -2.5, 3.0, 0.0, 2.5);
        pose.popPose();
        pose.popPose();
        // 南瓜头用物品模型，正面自然带雕刻出的脸。
        pose.pushPose();
        pose.translate(0, 1.26, 0);
        pose.mulPose(Axis.YP.rotationDegrees((float)Math.sin((entity.tickCount + partial) * 0.02) * 4.0F));
        pose.scale(0.62F, 0.62F, 0.62F);
        items.renderStatic(new ItemStack(Items.CARVED_PUMPKIN), ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
    }
    private void limb(PoseStack pose, MultiBufferSource buffers, int light, double x, double y, float angle, double width, double length) {
        pose.pushPose(); pose.translate(x, y, 0); pose.mulPose(Axis.XP.rotationDegrees(angle));
        cube(pose, buffers, light, -width, -length, -width, width, 0, width, false, 0xFFFFFF); pose.popPose();
    }
    private static void cube(PoseStack pose, MultiBufferSource buffers, int light, double x0, double y0, double z0,
                             double x1, double y1, double z1, boolean face, int tint) {
        box(pose, buffers, light, x0, y0, z0, x1, y1, z1, tint,
                RenderType.entityCutoutNoCull(face ? FACE : BARK));
    }
    /** 半透明冰块方块：六面都用原版冰贴图，不剔除背面，能看见内部结构。 */
    private static void iceCube(PoseStack pose, MultiBufferSource buffers, int light, double x0, double y0, double z0,
                                double x1, double y1, double z1) {
        box(pose, buffers, light, x0, y0, z0, x1, y1, z1, 0xE8FAFF, RenderType.entityTranslucent(ICE));
    }
    private static void box(PoseStack pose, MultiBufferSource buffers, int light, double x0, double y0, double z0,
                            double x1, double y1, double z1, int tint, RenderType type) {
        float[][] p = {{(float)x0,(float)y0,(float)z0},{(float)x1,(float)y0,(float)z0},{(float)x1,(float)y1,(float)z0},{(float)x0,(float)y1,(float)z0},
                {(float)x0,(float)y0,(float)z1},{(float)x1,(float)y0,(float)z1},{(float)x1,(float)y1,(float)z1},{(float)x0,(float)y1,(float)z1}};
        int[][] sides = {{1,0,3,2},{4,5,6,7},{0,4,7,3},{5,1,2,6},{3,7,6,2},{0,1,5,4}};
        float[][] normals = {{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
        float[][] uv = {{0,1},{1,1},{1,0},{0,0}};
        VertexConsumer out = buffers.getBuffer(type);
        for (int f = 0; f < 6; f++) {
            for (int n = 0; n < 4; n++) {
                float[] point = p[sides[f][n]], normal = normals[f];
                out.addVertex(pose.last().pose(), point[0], point[1], point[2]).setColor(tint >> 16 & 255, tint >> 8 & 255, tint & 255, 255)
                        .setUv(uv[n][0], uv[n][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose.last(), normal[0], normal[1], normal[2]);
            }
        }
    }
}
