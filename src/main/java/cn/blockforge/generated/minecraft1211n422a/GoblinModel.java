package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * 哥布林的自定义骨骼模型。
 *
 * <p>不通用的地方：头身比、胳膊长度、肚子的宽度都由 {@link GoblinShape} 生成的几何决定，
 * 所以这里不继承 {@code HumanoidModel}，而是把自己的摆姿势代码整段写出来——
 * 这样摇摆动作、手持物、技能动作都能按哥布林的短腿大肚子单独调，不会被原版的人形比例绑死。</p>
 *
 * <p>护甲是挂在头 / 身 / 腿下面的子骨骼，平时隐藏，由 {@link GoblinArmorLayer} 单独打开再画。</p>
 */
public final class GoblinModel extends EntityModel<Goblin> implements ArmedModel {
    private static final float DEG = (float)(Math.PI / 180.0);

    public final ModelPart root;
    public final ModelPart head;
    public final ModelPart body;
    public final ModelPart rightArm;
    public final ModelPart leftArm;
    public final ModelPart rightLeg;
    public final ModelPart leftLeg;

    public final ModelPart helmet;
    public final ModelPart helmetRim;
    public final ModelPart chestPlate;
    public final ModelPart bellyPlate;
    public final ModelPart rightLeggings;
    public final ModelPart leftLeggings;
    public final ModelPart rightBoot;
    public final ModelPart leftBoot;

    private final PartPose headPose;
    private final PartPose bodyPose;
    private final PartPose rightArmPose;
    private final PartPose leftArmPose;
    private final PartPose rightLegPose;
    private final PartPose leftLegPose;

    public GoblinModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.helmet = head.getChild("helmet");
        this.helmetRim = head.getChild("helmet_rim");
        this.chestPlate = body.getChild("chest_plate");
        this.bellyPlate = body.getChild("belly_plate");
        this.rightLeggings = rightLeg.getChild("right_leggings");
        this.leftLeggings = leftLeg.getChild("left_leggings");
        this.rightBoot = rightLeg.getChild("right_boot");
        this.leftBoot = leftLeg.getChild("left_boot");
        this.headPose = head.storePose();
        this.bodyPose = body.storePose();
        this.rightArmPose = rightArm.storePose();
        this.leftArmPose = leftArm.storePose();
        this.rightLegPose = rightLeg.storePose();
        this.leftLegPose = leftLeg.storePose();
        // 护甲骨骼只在 GoblinArmorLayer 里打开，平时不画
        setArmorVisible(false);
    }

    private void setArmorVisible(boolean visible) {
        helmet.visible = visible;
        helmetRim.visible = visible;
        chestPlate.visible = visible;
        bellyPlate.visible = visible;
        rightLeggings.visible = visible;
        leftLeggings.visible = visible;
        rightBoot.visible = visible;
        leftBoot.visible = visible;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        root.render(poseStack, consumer, light, overlay, color);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        ModelPart part = arm == HumanoidArm.RIGHT ? rightArm : leftArm;
        root.translateAndRotate(poseStack);
        part.translateAndRotate(poseStack);
        // 本模型的手臂以中心线建模，不能沿用原版偏一像素的握点。
        // 把柄收回掌心，幼崽按八像素短臂补偿；不再靠整条胳膊举平纠正持物。
        poseStack.translate(arm == HumanoidArm.RIGHT ? 1.0 / 16.0 : -1.0 / 16.0,
                (headPose.y > 5.0F ? -3.0 : 1.0) / 16.0, -0.5 / 16.0);
    }

    @Override
    public void setupAnim(Goblin entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        head.loadPose(headPose);
        body.loadPose(bodyPose);
        rightArm.loadPose(rightArmPose);
        leftArm.loadPose(leftArmPose);
        rightLeg.loadPose(rightLegPose);
        leftLeg.loadPose(leftLegPose);

        // 走路：短腿迈得快，胳膊摆幅收着点，免得像大风车
        float step = Mth.cos(limbSwing * 0.7F) * 1.3F * limbSwingAmount;
        rightLeg.xRot = step;
        leftLeg.xRot = -step;
        rightArm.xRot = -step * 0.7F;
        leftArm.xRot = step * 0.7F;
        rightArm.zRot = 0.06F;
        leftArm.zRot = -0.06F;

        head.yRot = netHeadYaw * DEG;
        head.xRot = headPitch * DEG;
        body.xRot = 0.05F + Mth.sin(ageInTicks * 0.12F) * 0.02F;
        if (entity.variant().shape() == GoblinShape.CHIEF) {
            // 首领驼背、探头，显得更凶
            body.xRot += 0.10F;
            head.xRot += 0.08F;
        } else if (entity.variant().shape() == GoblinShape.YOUNG) {
            // 幼崽站不稳，脑袋一直在晃
            head.zRot = Mth.sin(ageInTicks * 0.22F) * 0.08F;
        }

        applyHeldPose(entity, ageInTicks);
        applySkillPose(entity, ageInTicks);
        applyAttackSwing(entity);
    }

    /** 持物不等于攻击：待机垂手、战斗轻抬，只有真正拉弓/施法才举到肩高。 */
    private void applyHeldPose(Goblin entity, float ageInTicks) {
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        boolean bow = main.getItem() instanceof BowItem;
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart mainArm = rightHanded ? rightArm : leftArm;
        ModelPart offArm = rightHanded ? leftArm : rightArm;
        float sign = rightHanded ? 1.0F : -1.0F;
        if (bow && entity.isUsingItem()) {
            mainArm.yRot = -0.1F * sign + head.yRot;
            offArm.yRot = 0.5F * sign + head.yRot;
            mainArm.xRot = -1.5707964F + head.xRot;
            offArm.xRot = -1.5707964F + head.xRot;
            mainArm.zRot = 0.0F;
            offArm.zRot = 0.0F;
            return;
        }
        float mainSwing = mainArm.xRot;
        float offSwing = offArm.xRot;
        // isAggressive 是服务端同步给客户端的战斗状态；客户端 getTarget 不可靠。
        boolean fighting = entity.isAggressive();
        float breathe = Mth.sin(ageInTicks * 0.09F) * 0.025F;
        if (!main.isEmpty()) {
            mainArm.xRot = (bow ? -0.10F : fighting ? -0.52F : -0.14F) + mainSwing * 0.55F + breathe;
            mainArm.yRot = -0.03F * sign;
            mainArm.zRot = 0.09F * sign;
        }
        if (!off.isEmpty()) {
            offArm.xRot = (fighting ? -0.65F : -0.16F) + offSwing * 0.45F - breathe;
            offArm.yRot = 0.06F * sign;
            offArm.zRot = -0.13F * sign;
        }
        if (entity.variant() == GoblinVariant.SHAMAN && entity.isCasting()) {
            float wave = Mth.sin(ageInTicks * 0.35F);
            rightArm.xRot = -1.25F + wave * 0.12F;
            leftArm.xRot = -1.15F - wave * 0.10F;
            rightArm.zRot = 0.18F;
            leftArm.zRot = -0.18F;
        }
    }

    /** 首领的技能动作：战吼、跳劈、蓄力斩击各有一套，靠服务端同步的动画状态驱动。 */
    private void applySkillPose(Goblin entity, float ageInTicks) {
        int ticks = entity.animationTicks();
        switch (entity.animation()) {
            case Goblin.ANIM_ROAR -> {
                float progress = 1.0F - Math.min(1.0F, ticks / 40.0F);
                float burst = Mth.sin(progress * Mth.PI);
                head.xRot = -0.18F - burst * 0.22F;
                head.zRot = 0.0F;
                body.xRot = -0.12F - burst * 0.10F;
                rightArm.xRot = -2.15F;
                leftArm.xRot = -2.15F;
                rightArm.zRot = 0.95F + burst * 0.2F;
                leftArm.zRot = -0.95F - burst * 0.2F;
                rightArm.yRot = 0.0F;
                leftArm.yRot = 0.0F;
            }
            case Goblin.ANIM_LEAP -> {
                rightArm.xRot = -2.45F;
                leftArm.xRot = -2.45F;
                rightArm.zRot = 0.45F;
                leftArm.zRot = -0.45F;
                rightLeg.xRot = 0.85F;
                leftLeg.xRot = 0.85F;
                body.xRot = -0.22F;
                head.xRot = -0.25F;
            }
            case Goblin.ANIM_SLAM -> {
                rightArm.xRot = 1.05F;
                leftArm.xRot = 1.05F;
                rightArm.zRot = 0.22F;
                leftArm.zRot = -0.22F;
                body.xRot = 0.38F;
                head.xRot = 0.30F;
                rightLeg.xRot = -0.28F;
                leftLeg.xRot = 0.28F;
            }
            case Goblin.ANIM_CHARGE -> {
                float progress = 1.0F - Math.min(1.0F, ticks / 80.0F);
                float tremble = Mth.sin(ageInTicks * (1.2F + progress * 1.6F)) * (0.03F + progress * 0.05F);
                boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
                ModelPart weapon = right ? rightArm : leftArm;
                ModelPart support = right ? leftArm : rightArm;
                float sign = right ? 1.0F : -1.0F;
                weapon.xRot = -2.55F + tremble;
                weapon.zRot = (0.30F + tremble * 0.5F) * sign;
                weapon.yRot = -0.25F * sign;
                support.xRot = -0.55F;
                support.zRot = -0.45F * sign;
                body.xRot = -0.10F - progress * 0.08F;
                head.xRot = -0.18F;
            }
            default -> { }
        }
    }

    /**
     * 挥臂关键帧时间点：0 → 30% 抬手，30% → 46% 劈下，46% → 100% 收回。
     *
     * <p>角度不再是「相对站姿的偏移」，而是四个绝对角度：站姿 → 抬手 → 劈到底 → 站姿。
     * 偏移写法在拿弓的哥布林身上会把胳膊甩到身体后面（负角是身前、正角是身后），
     * 换成绝对角度后，劈到底一定停在垂直偏前的方向，不会翻到背后去。</p>
     */
    private static final float[] SWING_TIMES = {0.0F, 0.30F, 0.46F, 1.0F};

    /**
     * 普通攻击的挥手臂：抬手 → 下劈 → 收回，节奏照着玩家挥空拳来，幅度按哥布林的短胳膊调。
     *
     * <p>只由 {@code attackTime}（一次挥动 0 → 1）驱动，不额外同步状态。
     * 原版那条曲线是「一帧抬手、五帧落回」，在短胳膊上几乎看不见；
     * 这里改成三段关键帧，抬手多留一帧，看得清是「举起来砍下去」。
     * 主手是哪只手就挥哪条胳膊，另一只手往后收当配重，腰跟着拧一下、脑袋跟着点一下。</p>
     */
    private void applyAttackSwing(Goblin entity) {
        if (attackTime <= 0.0F) return;
        // 技能动作、施法、拉弓期间不叠普通攻击，两套姿势会打架
        if (entity.animation() != Goblin.ANIM_NONE || entity.isCasting() || entity.isUsingItem()) return;
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? rightArm : leftArm;
        ModelPart offArm = right ? leftArm : rightArm;
        float sign = right ? 1.0F : -1.0F;

        float f = attackTime;
        // 首领抡得更高更狠，成年哥布林收着一点
        float lift = entity.variant().shape() == GoblinShape.CHIEF ? 1.55F : 1.35F;
        float rest = arm.xRot;
        // 负角是身前：抬手再往上抬一段；劈到底收掉大部分备战角度，停在垂直偏前，绝不越过身体
        float raised = rest - lift;
        float down = rest * 0.82F;
        float swing = keyframe(f, SWING_TIMES, new float[]{rest, raised, down, rest});
        float raise = Mth.sin(f * Mth.PI); // 0 → 1 → 0，挥到一半时最明显

        arm.xRot = swing;
        arm.zRot += -0.28F * raise * sign;
        arm.yRot += -0.10F * raise * sign;
        // 另一只手往后收，像真人挥拳时的配重
        offArm.xRot += 0.32F * raise;
        offArm.zRot += 0.18F * raise * sign;
        // 腰拧一点、身前倾一点、头往下点，动作才不是一条胳膊在动
        body.yRot += -0.16F * raise * sign;
        body.xRot += 0.10F * raise;
        head.xRot += 0.08F * raise;
    }

    /** 关键帧插值：给定 0～1 的进度，在两帧之间做平滑过渡，免得一帧一个折角。 */
    private static float keyframe(float progress, float[] times, float[] values) {
        for (int i = 1; i < times.length; i++) {
            if (progress <= times[i]) {
                float t = (progress - times[i - 1]) / (times[i] - times[i - 1]);
                t = t * t * (3.0F - 2.0F * t);
                return values[i - 1] + (values[i] - values[i - 1]) * t;
            }
        }
        return values[values.length - 1];
    }

    /** 护甲渲染层用：把护甲骨骼打开画一遍，然后立刻关掉。 */
    void renderArmorPart(ModelPart parent, ModelPart piece, PoseStack poseStack, VertexConsumer consumer,
                         int light, int overlay) {
        poseStack.pushPose();
        parent.translateAndRotate(poseStack);
        piece.visible = true;
        piece.render(poseStack, consumer, light, overlay);
        piece.visible = false;
        poseStack.popPose();
    }
}
