package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * r290：膜面水波粒子——踩在反虚空的膜上时扩散的同心圆涟漪。
 *
 * <p>贴图是一张 8 帧的动画条（mcmeta 自动逐帧播放环的“生长”），这里再叠加
 * 代码缩放（0.18 → 约 1.3 格）与平方淡出，一圈涟漪由小变大、由实变虚。
 * 粒子面片默认朝向相机：从上往下俯视膜面时正好平铺成水面波纹。</p>
 */
public final class MembraneRippleParticle extends TextureSheetParticle {
    private final int totalLife;
    private final float maxSize;

    MembraneRippleParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.setSprite(sprites.get(this.random));
        this.totalLife = 16 + this.random.nextInt(8);
        this.lifetime = this.totalLife;
        this.maxSize = 1.0F + this.random.nextFloat() * 0.6F;
        this.quadSize = 0.18F;
        this.setSize(0.6F, 0.1F);
        this.friction = 1.0F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        float progress = Math.min(1.0F, this.age / (float) this.totalLife);
        this.quadSize = 0.18F + progress * this.maxSize;
        this.alpha = 1.0F - progress * progress;
    }
}
