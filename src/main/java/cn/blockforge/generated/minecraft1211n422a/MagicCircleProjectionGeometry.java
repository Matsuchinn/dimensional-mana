package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Direction;

/**
 * 把「画布像素坐标」换算成方块内的局部坐标（0~1），投影渲染和试运行粒子共用。
 *
 * <p>投影锚点是贴在某个方块表面上的：{@code FACING} 记的是它朝向哪边（也就是表面朝外的方向）。
 * 六个方向各自有一个 1 像素厚的底板，这里按同样的朝向把画布铺在底板前面一点点，
 * 保证光点 / 连线不会扎进墙里，也不会被底板自己挡住。</p>
 */
public final class MagicCircleProjectionGeometry {
    /** 底板厚度（格），和方块模型里的 1 像素一致。 */
    private static final float PLATE = 1.0F / 16.0F;
    /** 画布离底板再往外一点点，避免和底板共面闪烁。 */
    private static final float SURFACE = 2.0F / 16.0F;

    /**
     * @param px 画布像素 X（0 ~ baseW*16）
     * @param py 画布像素 Y（0 ~ baseH*16）
     * @param lift 往表面外侧再挪多少格（叠层时每层挪一点点）
     * @return {x, y, z}，单位是格，范围 0~1
     */
    public static float[] local(Direction facing, int baseW, int baseH, float px, float py, float lift) {
        int tile = MagicCircleNetwork.TILE;
        float fx = clamp01(px / (float) Math.max(1, baseW * tile));
        float fy = clamp01(py / (float) Math.max(1, baseH * tile));
        float out = Math.max(PLATE, SURFACE) + Math.max(0.0F, lift);
        if (facing == null) facing = Direction.UP;
        return switch (facing) {
            // 贴地：底板在最下面，画布朝上；画布的「下」朝 +Z。
            case UP -> new float[]{fx, out, 1.0F - fy};
            // 贴天花板：底板在最上面，画布朝下。
            case DOWN -> new float[]{fx, 1.0F - out, fy};
            // 贴北墙（表面朝 -Z）：底板在 z 的大值侧，人从北往南看。
            case NORTH -> new float[]{fx, 1.0F - fy, 1.0F - out};
            // 贴南墙（表面朝 +Z）：左右镜像一下，从南往北看才不反。
            case SOUTH -> new float[]{1.0F - fx, 1.0F - fy, out};
            // 贴西墙（表面朝 -X）。
            case WEST -> new float[]{1.0F - out, 1.0F - fy, fx};
            // 贴东墙（表面朝 +X）。
            case EAST -> new float[]{out, 1.0F - fy, 1.0F - fx};
        };
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private MagicCircleProjectionGeometry() {}
}
