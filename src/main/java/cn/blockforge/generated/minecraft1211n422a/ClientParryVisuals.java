package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashSet;
import java.util.Set;

/**
 * 招架的客户端表现：姿态姿势（第一/第三人称）、剑身斗气光晕，以及弹反瞬间的屏幕特效。
 *
 * <p>粒子与音效由服务端广播，这里只负责只属于自己的那一层：准心白圈、完美招架的白闪与
 * 金色光晕、斗气不足时屏幕边缘泛红，以及进入姿态时视角略微下沉。</p>
 *
 * <p>姿态下原版的挥刀动画会被按住零点几秒地「锁」住：右键按着不放时游戏每几刻就想再挥一次手，
 * 不锁的话剑会一直在身前抽动。按左键弹反的那一刀例外，留 7 刻让这一下看得见。</p>
 *
 * <p>举刀、收刀都不是一瞬间切过去，而是照原版挥刀的做法按帧插值：刻里推进过渡值，
 * 渲染时用插值刻数求值，再套一条两头慢中间快的曲线。所以刀是从右下角「划」上胸前的，
 * 和原版挥手一个顺滑度，镜头下沉、准心提示也一起淡入淡出。</p>
 */
public final class ClientParryVisuals {
    /** 准心白圈、弹反冲击波都在 10 刻以内放完，打起来才跟手。 */
    private static final int NORMAL_RING_TICKS = 6;
    private static final int PROJECTILE_RING_TICKS = 6;
    private static final int PERFECT_RING_TICKS = 10;
    private static final int PERFECT_FLASH_TICKS = 3;
    private static final int PERFECT_GOLD_TICKS = 10;
    private static final int FAIL_TICKS = 12;
    /** 完美招架时贴在怪物身上的 4 帧闪光：8 刻播完，每帧 2 刻。 */
    private static final int MONSTER_FLASH_TICKS = 8;
    private static final int MONSTER_FLASH_FRAMES = 4;
    /** 按下左键那一下之后，这几刻内不锁挥刀，让玩家看得到自己这一刀。 */
    private static final int SWING_GRACE_TICKS = 7;

    /**
     * 举刀 / 收刀各用几刻走完。
     *
     * <p>原版一刀挥手正好 6 刻，举刀也用 6 刻，手感和挥刀一致：太快像闪过去，太慢像卡住。</p>
     */
    private static final int RAISE_TICKS = 6;
    private static final int LOWER_TICKS = 5;
    /** 过渡到一半时刀额外转过去的角度：让刀是「划」过去，不是直挺挺地挪过去。 */
    private static final float SWOOP_YAW = 14.0F;
    private static final float SWOOP_PITCH = 7.0F;
    /** 进入姿态时镜头下沉多少度，也跟着同一条曲线慢慢沉下去。 */
    private static final float CAMERA_DIP = 5.0F;

    /** 4 帧闪光图集：横向 4 格，每格 16x16。 */
    private static final ResourceLocation PARRY_FLASH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            GeneratedMod.MOD_ID, "textures/effect/casting/parry_flash.png");
    private static final int FLASH_TEXTURE_WIDTH = 64;
    private static final int FLASH_TEXTURE_HEIGHT = 16;
    private static final int FLASH_FRAME_SIZE = 16;
    /** 闪光在屏幕上至少/至多占多少像素：太远了也看得见，贴脸了也不会糊满整屏。 */
    private static final int FLASH_MIN_PIXELS = 34;
    private static final int FLASH_MAX_PIXELS = 380;

    /**
     * 第一人称：刀绕自己的位置反着转多少度。
     *
     * <p>原版第一人称的刀是「柄在右下、刃斜指右上」；绕 Z 轴这么一转，刃就翻到左边横起来，
     * 正好横在胸前，和第三人称举盾的姿势对得上。</p>
     */
    private static final float FIRST_PERSON_ROLL = 50.0F;
    /** 第一人称：转完之后整体往中间抬多少（相机坐标系，单位是格）。 */
    private static final float FIRST_PERSON_LIFT = 0.55F;
    private static final float FIRST_PERSON_PULL = 0.16F;

    private static boolean stance;
    private static int normalRing;
    private static int projectileRing;
    private static int perfectRing;
    private static int perfectFlash;
    private static int perfectGold;
    private static int fail;
    private static int swingGrace;
    /**
     * 举刀动作的过渡：0 = 原版手位，1 = 完整举起。
     *
     * <p>两个值一起存，是为了照原版 {@code attackAnim / oAttackAnim} 的做法渲染时插值：
     * 刻每 1/20 秒才走一格，但画面是每秒几十帧，只按刻画就会一格一格地跳。</p>
     */
    private static float poseBlend;
    private static float oPoseBlend;
    /** 怪物身上那段闪光的剩余刻数、目标实体与兜底坐标。 */
    private static int monsterFlash;
    private static int flashEntityId = -1;
    private static double flashX, flashY, flashZ;
    /** 别人（联机时）正在招架的实体 id：服务端进出姿态时通知，这里只管照着摆姿势。 */
    private static final Set<Integer> REMOTE_STANCE = new HashSet<>();
    /** 上一次见到的是哪个世界：换了世界就把别人姿态的记录清空。 */
    private static ClientLevel level;
    /** 这一帧世界渲染用的视图/投影矩阵，以及插值刻数：闪光要靠它们换算成屏幕坐标。 */
    private static final Matrix4f LAST_MODELVIEW = new Matrix4f();
    private static final Matrix4f LAST_PROJECTION = new Matrix4f();
    private static boolean hasMatrix;
    private static float partialTick;

    public static void setStance(boolean value) {
        stance = value;
    }

    public static boolean stance() {
        return stance;
    }

    /** 服务端把「谁在招架」同步过来；自己由按键驱动，别被自己的回包覆盖。 */
    public static void acceptStance(int entityId, boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getId() == entityId) return;
        if (active) REMOTE_STANCE.add(entityId);
        else REMOTE_STANCE.remove(entityId);
    }

    /** 这个玩家此刻是不是在摆招架姿势（自己看输入，别人看服务端通知）。 */
    public static boolean inStance(Player player) {
        if (player == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && player == minecraft.player) return stance;
        return REMOTE_STANCE.contains(player.getId());
    }

    /** 刚刚按了左键弹反：这一刀允许播出来，别被姿态锁掉。 */
    public static void allowSwing() {
        swingGrace = SWING_GRACE_TICKS;
    }

    public static void accept(ParryFeedbackPayload payload) {
        switch (payload.kind()) {
            case ParryFeedbackPayload.NORMAL -> normalRing = NORMAL_RING_TICKS;
            case ParryFeedbackPayload.PROJECTILE -> projectileRing = PROJECTILE_RING_TICKS;
            case ParryFeedbackPayload.PERFECT -> {
                perfectRing = PERFECT_RING_TICKS;
                perfectFlash = PERFECT_FLASH_TICKS;
                perfectGold = PERFECT_GOLD_TICKS;
                // 服务端给了被打中那只怪物的 id：闪光跟着它走，找不到就画在反馈坐标上。
                monsterFlash = MONSTER_FLASH_TICKS;
                flashEntityId = payload.entityId();
                flashX = payload.x();
                flashY = payload.y();
                flashZ = payload.z();
            }
            case ParryFeedbackPayload.FAIL -> fail = FAIL_TICKS;
            default -> { }
        }
    }

    /** 退出世界时清干净，别把上一局的闪光带到下一局。 */
    public static void clear() {
        stance = false;
        normalRing = projectileRing = perfectRing = perfectFlash = perfectGold = fail = 0;
        swingGrace = 0;
        poseBlend = 0.0F;
        oPoseBlend = 0.0F;
        monsterFlash = 0;
        flashEntityId = -1;
        REMOTE_STANCE.clear();
        hasMatrix = false;
    }

    public static void tick(ClientTickEvent.Post event) {
        if (normalRing > 0) normalRing--;
        if (projectileRing > 0) projectileRing--;
        if (perfectRing > 0) perfectRing--;
        if (perfectFlash > 0) perfectFlash--;
        if (perfectGold > 0) perfectGold--;
        if (fail > 0) fail--;
        if (monsterFlash > 0) monsterFlash--;
        if (swingGrace > 0) swingGrace--;

        Minecraft minecraft = Minecraft.getInstance();
        // 举起 / 放下都按刻推进，渲染时再按帧插值：原版一刀 6 刻，抬手也 6 刻。
        oPoseBlend = poseBlend;
        poseBlend = stance
                ? Math.min(1.0F, poseBlend + 1.0F / RAISE_TICKS)
                : Math.max(0.0F, poseBlend - 1.0F / LOWER_TICKS);
        // 换维度后实体 id 会重新分配，上一局记下的「谁在招架」不能带过去。
        if (minecraft.level != level) {
            level = minecraft.level;
            REMOTE_STANCE.clear();
        }
        if (!stance || minecraft.player == null || minecraft.level == null) return;
        // 姿态期间把原版的挥刀动画按住：右键按着不放时游戏会反复想挥手，不压住剑就一直在抽。
        if (swingGrace <= 0) freezeSwing(minecraft.player);
        // 斗气过半时剑身附着斗气光晕：在身前右手侧撒一层微微发亮的粒子。
        double ratio = ClientManaState.battleQi() / (double)Math.max(1, ClientManaState.maxBattleQi());
        if (ratio <= 0.5) return;
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 at = minecraft.player.getEyePosition().add(look.scale(0.85)).add(right.scale(0.3)).add(0, -0.3, 0);
        double spread = 0.12;
        minecraft.level.addParticle(ParticleTypes.ENCHANTED_HIT,
                at.x + (Math.random() - 0.5) * spread, at.y + (Math.random() - 0.5) * 0.3,
                at.z + (Math.random() - 0.5) * spread, 0.0, 0.005, 0.0);
        if ((minecraft.level.getGameTime() & 3L) == 0L) {
            minecraft.level.addParticle(ParticleTypes.END_ROD,
                    at.x + (Math.random() - 0.5) * 0.2, at.y + (Math.random() - 0.5) * 0.45,
                    at.z + (Math.random() - 0.5) * 0.2, 0.0, 0.0, 0.0);
        }
    }

    /** 把正在播的挥刀动画直接归零：手臂回到持剑待机姿势，不再来回抽动。 */
    private static void freezeSwing(Player player) {
        if (!player.swinging && player.swingTime == 0 && player.attackAnim == 0.0F && player.oAttackAnim == 0.0F) {
            return;
        }
        player.swinging = false;
        player.swingTime = 0;
        player.attackAnim = 0.0F;
        player.oAttackAnim = 0.0F;
    }

    /**
     * 这一刻举手举到哪儿了：先按帧插值，再套一条「两头慢、中间快」的平滑曲线。
     *
     * <p>第一步和原版 {@code Mth.lerp(partialTick, oAttackAnim, attackAnim)} 一个意思，
     * 把 20Hz 的刻格子连成连续的动作；第二步让起手和落位都没有突然的加速度，
     * 看上去就是原版挥刀那种顺滑感，而不是「咔」地一下摆到位。</p>
     */
    private static float blend(float partialTick) {
        float t = Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), oPoseBlend, poseBlend);
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** HUD 那一层拿不到插值刻数，直接问主循环要一个。 */
    private static float framePartialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    /** 进入招架姿态时视角略微下沉，剑横在胸前的压迫感；下沉也跟着举刀一起过渡。 */
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float dip = CAMERA_DIP * blend((float)event.getPartialTick());
        if (dip != 0.0F) event.setPitch(event.getPitch() + dip);
    }

    // ---------------------------------------------------------------- 姿态姿势

    /**
     * 第三人称：把持剑那条手臂换成原版的「举盾」姿势（ArmPose.BLOCK）。
     *
     * <p>{@code PlayerRenderer} 先算好模型属性再发这个事件，所以这里改下去的姿势会一直留到
     * {@code setupAnim} 读它，手臂就横在胸前、剑竖着举起来了。别人的姿态由服务端通知。</p>
     */
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!inStance(player)) return;
        if (!player.getMainHandItem().is(ItemTags.SWORDS)) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        } else {
            model.leftArmPose = HumanoidModel.ArmPose.BLOCK;
        }
    }

    /**
     * 第一人称：把刀从右下角抬到胸前横住，看得见「举刀架势」。
     *
     * <p>原版那次渲染直接拦掉，自己按同一套手部变换重新画一遍：先绕刀自身的位置转到横，
     * 再整体往中间抬。这样刀不会因为旋转被甩出画面，挥刀时照样有那一下横劈。</p>
     *
     * <p>过渡值按帧取值，所以抬手、收手都是连续的一段动作；曲线两头速度为 0，
     * 起手不突兀、落位不回弹，和原版挥刀一个顺滑度。</p>
     */
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(ItemTags.SWORDS)) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isScoping()) return;

        // 已经收干净（这一刻和上一刻都是原版手位）就直接交回原版，别多画一遍。
        float blend = blend(event.getPartialTick());
        if (blend <= 0.001F) return;

        boolean rightHand = player.getMainArm() == HumanoidArm.RIGHT;
        float side = rightHand ? 1.0F : -1.0F;
        float equip = event.getEquipProgress();
        float swing = event.getSwingProgress();
        // 过渡到一半时多扫出去一点角度：刀是从侧前方划上来的，不是直着挪过去。
        float swoop = Mth.sin(blend * (float)Math.PI);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // ① 举起：以刀自身的位置为轴转到横，中途带一点横扫，再整体往中间、往上抬。
        float anchorX = side * 0.56F;
        float anchorY = -0.52F + equip * -0.6F;
        pose.translate(anchorX, anchorY, -0.72F);
        pose.mulPose(Axis.ZP.rotationDegrees(FIRST_PERSON_ROLL * side * blend));
        pose.mulPose(Axis.YP.rotationDegrees(SWOOP_YAW * side * swoop));
        pose.mulPose(Axis.XP.rotationDegrees(-SWOOP_PITCH * swoop));
        pose.translate(-anchorX, -anchorY, 0.72F);
        pose.translate(-FIRST_PERSON_PULL * side * blend, FIRST_PERSON_LIFT * blend, 0.0F);
        // ② 原版第一人称那套：挥手偏移 + 手部锚点 + 挥砍，保证弹反那一下还会挥出去。
        //    挥手偏移和原版一样分左右手，不然左手持剑时会突然横跳一下。
        float bobX = side * -0.4F * Mth.sin(Mth.sqrt(swing) * (float)Math.PI);
        float bobY = 0.2F * Mth.sin(Mth.sqrt(swing) * ((float)Math.PI * 2.0F));
        float bobZ = -0.2F * Mth.sin(swing * (float)Math.PI);
        pose.translate(bobX, bobY, bobZ);
        pose.translate(anchorX, anchorY, -0.72F);
        applyAttackSwing(pose, side, swing);

        event.setCanceled(true);
        ItemDisplayContext context = rightHand
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        minecraft.getItemRenderer().renderStatic(player, stack, context, !rightHand, pose,
                event.getMultiBufferSource(), player.level(), event.getPackedLight(),
                OverlayTexture.NO_OVERLAY, player.getId() + context.ordinal());
        pose.popPose();
    }

    /** 原版第一人称挥砍的那几个旋转，抄一份好让弹反的左键还看得见挥刀。 */
    private static void applyAttackSwing(PoseStack pose, float side, float swing) {
        float f = Mth.sin(swing * swing * (float)Math.PI);
        float f1 = Mth.sin(Mth.sqrt(swing) * (float)Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45.0F + f * -20.0F)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
    }

    // ---------------------------------------------------------------- 屏幕特效

    /**
     * 世界渲染阶段只记这一帧的矩阵：闪光放到 HUD 层按屏幕坐标画，才不会被别的渲染目标吞掉。
     */
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        LAST_MODELVIEW.set(event.getModelViewMatrix());
        LAST_PROJECTION.set(event.getProjectionMatrix());
        partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        hasMatrix = true;
    }

    /**
     * 完美招架时贴在怪物身上的 4 帧闪光。
     *
     * <p>把怪物的位置换算成屏幕坐标，再在这上面贴一格 16x16 的图：跟着怪物的头肩走，
     * 远处会小一点、近处会大一点，8 刻播完自动消失。</p>
     */
    private static void drawMonsterFlash(GuiGraphics graphics, Minecraft minecraft) {
        if (monsterFlash <= 0 || !hasMatrix || minecraft.level == null) return;
        ClientLevel level = minecraft.level;
        Entity target = level.getEntity(flashEntityId);
        Vec3 origin = target != null
                ? target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.55, 0.0)
                : new Vec3(flashX, flashY, flashZ);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (origin.distanceToSqr(camera) > 48.0 * 48.0) return;
        float[] center = new float[2];
        if (!project(origin, camera, center)) return;
        double height = target != null ? target.getBbHeight() : 1.8;
        float[] top = new float[2];
        // 头顶那一点未必在画面里，取不到就按距离估一个。
        boolean hasTop = project(origin.add(0.0, Math.max(0.6, height * 0.45), 0.0), camera, top);
        float guiWidth = graphics.guiWidth();
        float guiHeight = graphics.guiHeight();
        float centerX = (center[0] * 0.5F + 0.5F) * guiWidth;
        float centerY = (0.5F - center[1] * 0.5F) * guiHeight;
        float pixelHeight;
        if (hasTop) {
            pixelHeight = Math.abs(center[1] - top[1]) * 0.5F * guiHeight * 1.9F;
        } else {
            double distance = Math.sqrt(origin.distanceToSqr(camera));
            pixelHeight = (float)(guiHeight * 0.9 * height / Math.max(1.5, distance));
        }
        int size = Math.round(Mth.clamp(pixelHeight, FLASH_MIN_PIXELS, FLASH_MAX_PIXELS));
        int elapsed = MONSTER_FLASH_TICKS - monsterFlash;
        int frame = Math.min(MONSTER_FLASH_FRAMES - 1, elapsed * MONSTER_FLASH_FRAMES / MONSTER_FLASH_TICKS);
        // 前 4 刻满格，后 4 刻淡出：够亮才看得出是「弹开了」。
        float alpha = Mth.clamp((MONSTER_FLASH_TICKS - elapsed) / 4.0F, 0.0F, 1.0F);
        float pulse = 1.0F + elapsed * 0.07F;

        // 贴图只有 16x16 一格：按原尺寸贴上去，再靠缩放放大到怪物的个头，UV 才不会串到隔壁帧。
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        float scale = size / (float)FLASH_FRAME_SIZE * pulse;
        pose.scale(scale, scale, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(PARRY_FLASH_TEXTURE, -FLASH_FRAME_SIZE / 2, -FLASH_FRAME_SIZE / 2,
                (float)(frame * FLASH_FRAME_SIZE), 0.0F,
                FLASH_FRAME_SIZE, FLASH_FRAME_SIZE, FLASH_TEXTURE_WIDTH, FLASH_TEXTURE_HEIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    /** 世界坐标 → 归一化设备坐标；在相机背后或算不出数就返回 false。 */
    private static boolean project(Vec3 point, Vec3 camera, float[] out) {
        Vec3 relative = point.subtract(camera);
        Vector4f clip = new Vector4f((float)relative.x, (float)relative.y, (float)relative.z, 1.0F);
        LAST_MODELVIEW.transform(clip);
        LAST_PROJECTION.transform(clip);
        if (clip.w <= 0.05F) return false;
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) return false;
        if (Math.abs(ndcX) > 1.6F || Math.abs(ndcY) > 1.6F) return false;
        out[0] = ndcX;
        out[1] = ndcY;
        return true;
    }

    public static void renderHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        // 怪物身上那段 4 帧闪光画在最底下，白闪和光晕盖在它上面。
        drawMonsterFlash(graphics, minecraft);
        if (perfectFlash > 0) {
            int alpha = (int)Math.round(110.0 * perfectFlash / PERFECT_FLASH_TICKS);
            graphics.fill(0, 0, width, height, (alpha << 24) | 0xFFFFFF);
        }
        if (perfectGold > 0) {
            vignette(graphics, width, height, 0xFFD868, perfectGold / (double)PERFECT_GOLD_TICKS, 0.42);
        }
        if (fail > 0) {
            vignette(graphics, width, height, 0xFF3B3B, fail / (double)FAIL_TICKS, 0.55);
        }
        if (perfectRing > 0) {
            double t = 1.0 - perfectRing / (double)PERFECT_RING_TICKS;
            int alpha = (int)Math.round(220.0 * (1.0 - t));
            ring(graphics, centerX, centerY, 7.0 + t * 58.0, (alpha << 24) | 0xFFE48A, 2);
            ring(graphics, centerX, centerY, 4.0 + t * 40.0, (alpha << 24) | 0xFFFFFF, 1);
        }
        if (normalRing > 0) {
            double t = 1.0 - normalRing / (double)NORMAL_RING_TICKS;
            int alpha = (int)Math.round(200.0 * (1.0 - t));
            ring(graphics, centerX, centerY, 6.0 + t * 34.0, (alpha << 24) | 0xFFFFFF, 1);
        }
        if (projectileRing > 0) {
            double t = 1.0 - projectileRing / (double)PROJECTILE_RING_TICKS;
            int alpha = (int)Math.round(200.0 * (1.0 - t));
            ring(graphics, centerX, centerY, 5.0 + t * 28.0, (alpha << 24) | 0xBFE9FF, 1);
        }
        // 姿态提示跟着举刀一起淡入淡出，不会「啪」地冒出来 / 消失。
        float stanceBlend = blend(framePartialTick());
        if (stanceBlend <= 0.02F) return;
        int fade = (int)Math.round(0xE0 * stanceBlend);
        int textFade = (int)Math.round(0xF0 * stanceBlend);
        // 姿态提示：准心下方两道金色括弧，不挡视野。
        int color = (fade << 24) | 0xFFD35A;
        graphics.fill(centerX - 12, centerY + 9, centerX - 5, centerY + 11, color);
        graphics.fill(centerX + 5, centerY + 9, centerX + 12, centerY + 11, color);
        graphics.fill(centerX - 6, centerY + 10, centerX - 4, centerY + 13, color);
        graphics.fill(centerX + 4, centerY + 10, centerX + 6, centerY + 13, color);
        Component label = Component.translatable("hud.dimensional_mana.parrying");
        graphics.drawString(minecraft.font, label, centerX - minecraft.font.width(label) / 2,
                centerY + 17, (textFade << 24) | 0xFFE7A6, true);
    }

    /** 屏幕边缘的光晕 / 泛红：由外向内一层层减淡。 */
    private static void vignette(GuiGraphics graphics, int width, int height, int rgb,
                                 double strength, double maxAlpha) {
        int layers = 9;
        for (int i = 0; i < layers; i++) {
            double falloff = 1.0 - i / (double)layers;
            int alpha = (int)Math.round(255.0 * maxAlpha * strength * falloff * falloff * 0.35);
            if (alpha <= 0) continue;
            int color = (alpha << 24) | (rgb & 0xFFFFFF);
            int band = 3 + i * 5;
            graphics.fill(0, 0, width, band, color);
            graphics.fill(0, height - band, width, height, color);
            graphics.fill(0, 0, band, height, color);
            graphics.fill(width - band, 0, width, height, color);
        }
    }

    /** 准心处扩散的一圈圆环，用短横线拼出来。 */
    private static void ring(GuiGraphics graphics, int centerX, int centerY, double radius,
                             int color, int thickness) {
        int steps = Math.max(36, (int)Math.round(radius * 6.0));
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2.0 * i / steps;
            int x = (int)Math.round(centerX + Math.cos(angle) * radius);
            int y = (int)Math.round(centerY + Math.sin(angle) * radius);
            graphics.fill(x - thickness, y - thickness, x + thickness + 1, y + thickness + 1, color);
        }
    }

    private ClientParryVisuals() { }
}
