package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.world.level.block.Block;

/**
 * 膜：反虚空维度正中央（Y=96）的超平坦大陆表面。
 *
 * <p>r290 定位：完全透明无色的“镜面薄膜”。贴图画的是抛光镜面的反光条纹，
 * 生成脚本把整体 alpha 压到很低——肉眼近乎看不见膜本身，只看到一条条缓慢
 * 漂移的高光（8 帧动画），像一面映着纯白天穹的镜子。渲染走客户端
 * translucent 层（见 {@code ClientModEvents}），这样光影（Iris/Oculus）
 * 会按玻璃/水面材质处理它，装光影时反光会更真实。</p>
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
