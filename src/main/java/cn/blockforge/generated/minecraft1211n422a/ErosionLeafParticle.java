package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * 荒原落叶粒子：紫灰色的蚀纹树叶从树冠上慢慢旋下来。
 *
 * <p>运动模型照搬原版樱花落叶（CherryParticle）：初速近零、受重力缓慢下坠、
 * 沿余弦曲线横向漂移、全程自旋加速。寿命 300 tick，落地即消失。</p>
 *
 * <p><b>r276 修复「粒子没有材质」</b>：贴图本身是一张 16×16、四周透明的镂空叶子，
 * 而这里原来返回的是 {@link ParticleRenderType#PARTICLE_SHEET_OPAQUE}——那个渲染类型
 * 不混合、也不做透明剔除，透明像素会被当成实心像素画出来。于是叶子没画出来，
 * 屏幕上全是紫色的方片（贴图里几颗 alpha=0 的纯洋红像素更是直接显示成亮粉色小方块）。
 * 改成 {@link ParticleRenderType#PARTICLE_SHEET_TRANSLUCENT}（原版樱花落叶用的就是它）
 * 之后，透明的地方才真的透明。</p>
 */
public final class ErosionLeafParticle extends TextureSheetParticle {
    private float rotSpeed;
    private final float particleRandom;
    private final float spinAcceleration;

    ErosionLeafParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        // 图集里只有一帧，直接按 SpriteSet 取，不用再猜帧号。
        this.setSprite(sprites.get(this.random));
        this.rotSpeed = (float) Math.toRadians(this.random.nextBoolean() ? -30.0 : 30.0);
        this.particleRandom = this.random.nextFloat();
        this.spinAcceleration = (float) Math.toRadians(this.random.nextBoolean() ? -5.0 : 5.0);
        this.lifetime = 300;
        this.gravity = 7.5E-4F;
        float size = this.random.nextBoolean() ? 0.05F : 0.075F;
        this.quadSize = size;
        this.setSize(size, size);
        this.friction = 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.lifetime-- <= 0) {
            this.remove();
        }
        if (this.removed) return;

        float age = 300 - this.lifetime;
        float progress = Math.min(age / 300.0F, 1.0F);
        double driftX = Math.cos(Math.toRadians(this.particleRandom * 60.0F)) * 2.0 * Math.pow(progress, 1.25);
        double driftZ = Math.sin(Math.toRadians(this.particleRandom * 60.0F)) * 2.0 * Math.pow(progress, 1.25);
        this.xd += driftX * 0.0025F;
        this.zd += driftZ * 0.0025F;
        this.yd -= this.gravity;
        this.rotSpeed += this.spinAcceleration / 20.0F;
        this.oRoll = this.roll;
        this.roll += this.rotSpeed / 20.0F;
        this.move(this.xd, this.yd, this.zd);
        if (this.onGround || (this.lifetime < 299 && (this.xd == 0.0 || this.zd == 0.0))) {
            this.remove();
        }
        if (!this.removed) {
            this.xd *= this.friction;
            this.yd *= this.friction;
            this.zd *= this.friction;
        }
    }
}
