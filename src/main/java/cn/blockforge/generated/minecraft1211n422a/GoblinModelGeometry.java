package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** 西幻哥布林几何；与美术 UV 同步生成，盒子尺寸全部为整数。 */
public final class GoblinModelGeometry {
    public static LayerDefinition create(GoblinShape shape) {
        return switch (shape) {
            case YOUNG -> createYoung();
            case ADULT -> createAdult();
            case CHIEF -> createChief();
        };
    }
    private static LayerDefinition createYoung() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7F, -16F, -6F, 14F, 16F, 10F)
                .texOffs(28, 28).addBox(-1F, -9F, -9F, 2F, 4F, 4F)
                .texOffs(70, 28).addBox(-10F, -13F, -1.7F, 3F, 5F, 2F)
                .texOffs(94, 28).addBox(-11.7F, -12F, -1.58F, 2F, 3F, 2F)
                .texOffs(114, 28).addBox(-12.4F, -11F, -1.46F, 1F, 1F, 2F)
                .texOffs(82, 28).addBox(7F, -13F, -1.7F, 3F, 5F, 2F)
                .texOffs(104, 28).addBox(9.7F, -12F, -1.58F, 2F, 3F, 2F)
                .texOffs(122, 28).addBox(11.4F, -11F, -1.46F, 1F, 1F, 2F)
                , PartPose.offset(0F, 8F, 0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(42, 28).addBox(-4.5F, 0F, -1.8F, 9F, 4F, 4F)
                .texOffs(50, 0).addBox(-5.5F, 2F, -3.1F, 11F, 6F, 7F)
                , PartPose.offset(0F, 8F, 0F));
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(0, 28).addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F)
                , PartPose.offset(-5.8F, 8F, 0F));
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(88, 0).addBox(-1.5F, 0F, -1.8F, 3F, 8F, 4F)
                , PartPose.offset(-2.8F, 16F, 0F));
        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(14, 28).addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F)
                , PartPose.offset(5.8F, 8F, 0F));
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(104, 0).addBox(-1.5F, 0F, -1.8F, 3F, 8F, 4F)
                , PartPose.offset(2.8F, 16F, 0F));
        PartDefinition helmet = head.addOrReplaceChild("helmet", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.5F, -17F, -6.5F, 15F, 4F, 11F)
                , PartPose.ZERO);
        PartDefinition helmet_rim = head.addOrReplaceChild("helmet_rim", CubeListBuilder.create()
                .texOffs(0, 17).addBox(-7.3F, -13.2F, -6.8F, 15F, 1F, 12F)
                , PartPose.ZERO);
        PartDefinition chest_plate = body.addOrReplaceChild("chest_plate", CubeListBuilder.create()
                .texOffs(96, 17).addBox(-5F, -0.2F, -2.35F, 10F, 4F, 5F)
                , PartPose.ZERO);
        PartDefinition belly_plate = body.addOrReplaceChild("belly_plate", CubeListBuilder.create()
                .texOffs(54, 0).addBox(-6F, 2.1F, -3.6F, 12F, 6F, 8F)
                , PartPose.ZERO);
        PartDefinition right_leggings = right_leg.addOrReplaceChild("right_leggings", CubeListBuilder.create()
                .texOffs(56, 17).addBox(-2F, -0.35F, -2.3F, 4F, 5F, 5F)
                , PartPose.ZERO);
        PartDefinition right_boot = right_leg.addOrReplaceChild("right_boot", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-2F, 5.15F, -2.6F, 4F, 3F, 5F)
                , PartPose.ZERO);
        PartDefinition left_leggings = left_leg.addOrReplaceChild("left_leggings", CubeListBuilder.create()
                .texOffs(76, 17).addBox(-2F, -0.35F, -2.3F, 4F, 5F, 5F)
                , PartPose.ZERO);
        PartDefinition left_boot = left_leg.addOrReplaceChild("left_boot", CubeListBuilder.create()
                .texOffs(20, 32).addBox(-2F, 5.15F, -2.6F, 4F, 3F, 5F)
                , PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition createAdult() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.5F, -11F, -5F, 11F, 11F, 8F)
                .texOffs(70, 21).addBox(-1.5F, -7F, -8F, 3F, 4F, 4F)
                .texOffs(86, 21).addBox(-8.5F, -8F, -1.7F, 3F, 5F, 2F)
                .texOffs(110, 21).addBox(-10.2F, -7F, -1.58F, 2F, 3F, 2F)
                .texOffs(12, 36).addBox(-10.9F, -6F, -1.46F, 1F, 1F, 2F)
                .texOffs(0, 36).addBox(-3.5F, -3.7F, -5.8F, 1F, 3F, 1F)
                .texOffs(98, 21).addBox(5.5F, -8F, -1.7F, 3F, 5F, 2F)
                .texOffs(120, 21).addBox(8.2F, -7F, -1.58F, 2F, 3F, 2F)
                .texOffs(20, 36).addBox(9.9F, -6F, -1.46F, 1F, 1F, 2F)
                .texOffs(6, 36).addBox(2.5F, -3.7F, -5.8F, 1F, 3F, 1F)
                , PartPose.offset(0F, 3F, 0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(68, 0).addBox(-4.5F, 0F, -1.8F, 9F, 9F, 4F)
                .texOffs(0, 21).addBox(-5.5F, 7F, -3.1F, 11F, 6F, 7F)
                , PartPose.offset(0F, 3F, 0F));
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-1.5F, 0F, -1.5F, 3F, 12F, 3F)
                , PartPose.offset(-6.4F, 3F, 0F));
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(38, 21).addBox(-1.5F, 0F, -1.8F, 3F, 8F, 4F)
                , PartPose.offset(-2.8F, 16F, 0F));
        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(54, 0).addBox(-1.5F, 0F, -1.5F, 3F, 12F, 3F)
                , PartPose.offset(6.4F, 3F, 0F));
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(54, 21).addBox(-1.5F, 0F, -1.8F, 3F, 8F, 4F)
                , PartPose.offset(2.8F, 16F, 0F));
        PartDefinition helmet = head.addOrReplaceChild("helmet", CubeListBuilder.create()
                .texOffs(74, 0).addBox(-6F, -12F, -5.5F, 12F, 3F, 9F)
                , PartPose.ZERO);
        PartDefinition helmet_rim = head.addOrReplaceChild("helmet_rim", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-5.8F, -9.2F, -5.8F, 12F, 1F, 10F)
                , PartPose.ZERO);
        PartDefinition chest_plate = body.addOrReplaceChild("chest_plate", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -0.2F, -2.35F, 10F, 9F, 5F)
                , PartPose.ZERO);
        PartDefinition belly_plate = body.addOrReplaceChild("belly_plate", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-6F, 7.1F, -3.6F, 12F, 6F, 8F)
                , PartPose.ZERO);
        PartDefinition right_leggings = right_leg.addOrReplaceChild("right_leggings", CubeListBuilder.create()
                .texOffs(46, 16).addBox(-2F, -0.35F, -2.3F, 4F, 5F, 5F)
                , PartPose.ZERO);
        PartDefinition right_boot = right_leg.addOrReplaceChild("right_boot", CubeListBuilder.create()
                .texOffs(86, 16).addBox(-2F, 5.15F, -2.6F, 4F, 3F, 5F)
                , PartPose.ZERO);
        PartDefinition left_leggings = left_leg.addOrReplaceChild("left_leggings", CubeListBuilder.create()
                .texOffs(66, 16).addBox(-2F, -0.35F, -2.3F, 4F, 5F, 5F)
                , PartPose.ZERO);
        PartDefinition left_boot = left_leg.addOrReplaceChild("left_boot", CubeListBuilder.create()
                .texOffs(106, 16).addBox(-2F, 5.15F, -2.6F, 4F, 3F, 5F)
                , PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }
    private static LayerDefinition createChief() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.5F, -11F, -6F, 13F, 11F, 10F)
                .texOffs(64, 23).addBox(-1.5F, -7F, -9F, 3F, 4F, 4F)
                .texOffs(80, 23).addBox(-9.5F, -8F, -1.7F, 3F, 5F, 2F)
                .texOffs(104, 23).addBox(-11.2F, -7F, -1.58F, 2F, 3F, 2F)
                .texOffs(6, 38).addBox(-11.9F, -6F, -1.46F, 1F, 1F, 2F)
                .texOffs(124, 23).addBox(-4F, -3.7F, -6.8F, 1F, 3F, 1F)
                .texOffs(92, 23).addBox(6.5F, -8F, -1.7F, 3F, 5F, 2F)
                .texOffs(114, 23).addBox(9.2F, -7F, -1.58F, 2F, 3F, 2F)
                .texOffs(14, 38).addBox(10.9F, -6F, -1.46F, 1F, 1F, 2F)
                .texOffs(0, 38).addBox(3F, -3.7F, -6.8F, 1F, 3F, 1F)
                , PartPose.offset(0F, 3F, 0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 23).addBox(-4.5F, 0F, -1.8F, 9F, 9F, 4F)
                .texOffs(80, 0).addBox(-7F, 7F, -3.1F, 14F, 7F, 7F)
                , PartPose.offset(0F, 3F, 0F));
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-2F, 0F, -1.5F, 4F, 12F, 3F)
                , PartPose.offset(-7.2F, 3F, 0F));
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(28, 23).addBox(-2F, 0F, -1.8F, 4F, 7F, 4F)
                , PartPose.offset(-3.4F, 17F, 0F));
        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2F, 0F, -1.5F, 4F, 12F, 3F)
                , PartPose.offset(7.2F, 3F, 0F));
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(46, 23).addBox(-2F, 0F, -1.8F, 4F, 7F, 4F)
                , PartPose.offset(3.4F, 17F, 0F));
        PartDefinition helmet = head.addOrReplaceChild("helmet", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-7F, -12F, -6.5F, 14F, 3F, 11F)
                , PartPose.ZERO);
        PartDefinition helmet_rim = head.addOrReplaceChild("helmet_rim", CubeListBuilder.create()
                .texOffs(32, 17).addBox(-6.8F, -9.2F, -6.8F, 14F, 1F, 12F)
                , PartPose.ZERO);
        PartDefinition chest_plate = body.addOrReplaceChild("chest_plate", CubeListBuilder.create()
                .texOffs(0, 17).addBox(-5F, -0.2F, -2.35F, 10F, 9F, 5F)
                , PartPose.ZERO);
        PartDefinition belly_plate = body.addOrReplaceChild("belly_plate", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.5F, 7.1F, -3.6F, 15F, 7F, 8F)
                , PartPose.ZERO);
        PartDefinition right_leggings = right_leg.addOrReplaceChild("right_leggings", CubeListBuilder.create()
                .texOffs(86, 17).addBox(-2.5F, -0.35F, -2.3F, 5F, 4F, 5F)
                , PartPose.ZERO);
        PartDefinition right_boot = right_leg.addOrReplaceChild("right_boot", CubeListBuilder.create()
                .texOffs(0, 33).addBox(-2.5F, 4.15F, -2.6F, 5F, 3F, 5F)
                , PartPose.ZERO);
        PartDefinition left_leggings = left_leg.addOrReplaceChild("left_leggings", CubeListBuilder.create()
                .texOffs(108, 17).addBox(-2.5F, -0.35F, -2.3F, 5F, 4F, 5F)
                , PartPose.ZERO);
        PartDefinition left_boot = left_leg.addOrReplaceChild("left_boot", CubeListBuilder.create()
                .texOffs(22, 33).addBox(-2.5F, 4.15F, -2.6F, 5F, 3F, 5F)
                , PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }
    private GoblinModelGeometry() {}
}
