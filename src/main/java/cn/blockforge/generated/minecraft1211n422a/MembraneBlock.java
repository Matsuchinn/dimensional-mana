package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.level.block.Block;

/**
 * 膜：反虚空维度正中央（Y=96）的超平坦大陆表面。
 *
 * <p>r290 定位：完全透明无色的“镜面薄膜”。方块贴图本身不再画镜面反光条纹，
 * 避免半透明边缘在角落插值出多余纹理；肉眼只通过踩上去时的水波粒子和音效
 * 感知膜面。渲染走客户端 translucent 层（见 {@code ClientModEvents}），这样光影
 * （Iris/Oculus）会按玻璃/水面材质处理它。</p>
 *
 * <p>生存模式挖不动（硬度 -1.0F），但创造模式和 {@code /fill} 可以拆掉它——
 * 所以破坏拦截只针对非创造玩家（见 {@link AntiVoidSystem#onBlockBreak}），
 * 边界巡检也只负责两层封印面，不管膜。</p>
 *
 * <p>踩上去的同心圆水波粒子（多帧动画的 {@link MembraneRippleParticle}）
 * 和风铃/水滴音效由 {@link AntiVoidSystem} 统一驱动，方块本身不留逻辑。</p>
 */
public final class MembraneBlock extends Block {
    public MembraneBlock(Properties properties) {
        super(properties);
    }
}
