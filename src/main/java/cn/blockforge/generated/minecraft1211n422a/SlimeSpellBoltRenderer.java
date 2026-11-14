package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 元素史莱姆的法术弹体：一颗边飞边转、带元素颜色的结晶（直接用各元素自己的图鉴物品当模型）。
 *
 * <p>渲染出来的是玩家一眼就能认出的东西——火史莱姆是一颗烈焰粉、冰史莱姆是一块浮冰，
 * 颜色和那只施法的史莱姆完全一致。配合每刻的粒子拖尾，即使在光影、雨夜或者远处也能看清
 * 「子弹在哪、从哪来」，玩家才谈得上躲开或者举招架把它打掉。</p>
 *
 * <p>水史莱姆是个例外：它的弹体不再借用原版水桶，而是自己的一颗水球
 * （{@link ElementalSlimeVariant#projectileIcon()} 会给出这个物品），看上去才像「水弹」，
 * 而不是「把桶丢过来」。</p>
 */
public final class SlimeSpellBoltRenderer extends EntityRenderer<SlimeSpellBolt> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/item/arcane_element_crystal.png");
    private final ItemRenderer items;

    public SlimeSpellBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(SlimeSpellBolt entity) {
        return TEXTURE;
    }

    @Override
    public void render(SlimeSpellBolt entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partial;
        ItemStack stack = new ItemStack(entity.variant().projectileIcon());
        pose.pushPose();
        // 上下轻微浮动，再转两轴：远处也像一颗「在飞的法术」而不是掉在地上的物品。
        pose.translate(0.0F, Math.sin(age * 0.35F) * 0.05F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(age * 26.0F));
        pose.mulPose(Axis.XP.rotationDegrees(age * 19.0F));
        pose.scale(0.55F, 0.55F, 0.55F);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
