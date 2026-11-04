package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 魔蚀凋零的「炸地形」工具：骷髅头命中、聚合体爆炸、本体撞地形都走这里。
 *
 * <p>以前骷髅头和聚合体的命中为了不把地形啃烂，完全绕开了方块判定；结果在洞里飞的时候
 * 本体和弹道都被墙卡住，看起来又笨又不动。现在统一交回这个工具：</p>
 *
 * <ul>
 *   <li>按球半径一格一格扫，只炸离爆心足够近的方块；</li>
 *   <li>跳过空气、流体、基岩（硬度 {@code -1}）和抗爆值太高的方块（黑曜石、远古残骸等）；</li>
 *   <li>不产生掉落物（{@code destroyBlock(pos, false)}），避免 36 连发时满地物品；</li>
 *   <li>每次调用有方块预算，连发时不会一口气刷掉半座山；</li>
 *   <li>尊重 {@code mobGriefing} 游戏规则：服务器关掉生物破坏时只伤人不伤地形。</li>
 * </ul>
 */
public final class CorruptedWitherBlast {
    /** 抗爆值超过这个数的方块不炸：黑曜石 1200、远古残骸 1200、基岩 3.6E7 都会留下。 */
    private static final float MAX_EXPLOSION_RESISTANCE = 50.0F;

    private CorruptedWitherBlast() { }

    /**
     * 在 {@code at} 周围炸出一个半径 {@code radius} 的球形坑。
     *
     * @param budget 这一次最多破坏多少格方块；小于等于 0 时什么也不做。
     * @return 实际破坏的方块数。
     */
    public static int breakBlocks(ServerLevel level, Vec3 at, double radius, int budget) {
        if (budget <= 0 || radius <= 0.0D) return 0;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return 0;
        int minX = (int)Math.floor(at.x - radius);
        int maxX = (int)Math.floor(at.x + radius);
        int minY = (int)Math.floor(at.y - radius);
        int maxY = (int)Math.floor(at.y + radius);
        int minZ = (int)Math.floor(at.z - radius);
        int maxZ = (int)Math.floor(at.z + radius);
        double radiusSqr = radius * radius;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = x + 0.5D - at.x;
                    double dy = y + 0.5D - at.y;
                    double dz = z + 0.5D - at.z;
                    if (dx * dx + dy * dy + dz * dz > radiusSqr) continue;
                    pos.set(x, y, z);
                    if (!canBreak(level, pos)) continue;
                    if (level.destroyBlock(pos.immutable(), false)) {
                        broken++;
                        if (broken >= budget) return broken;
                    }
                }
            }
        }
        return broken;
    }

    private static boolean canBreak(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        // 水 / 岩浆不炸，免得炸完一圈全被流体灌满。
        if (!state.getFluidState().isEmpty()) return false;
        // 基岩这类「硬度 -1」的方块永远不动。
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;
        // 黑曜石、远古残骸、附魔台之类的高抗爆方块留下，给玩家一点掩体。
        if (state.getBlock().getExplosionResistance() > MAX_EXPLOSION_RESISTANCE) return false;
        // 召唤阵的骷髅头方块是装饰 / 仪式用品，不要顺手炸掉。
        return !(state.getBlock() instanceof CorruptedWitherSkullBlock);
    }

    /** 只有声音：聚合体爆炸的观感由客户端几何特效负责，不再额外撒原版粒子。 */
    public static void playBlastSound(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, CorruptedWitherSounds.get("blast"),
                SoundSource.HOSTILE, volume, pitch);
    }

    /**
     * 骷髅头命中点的爆炸观感：一小撮原版爆炸粒子 + 自制的低频爆炸声。
     *
     * <p>骷髅头没有专属的客户端几何特效，这里用少量粒子补一下「墙被炸开」的碎屑感；
     * 聚合体那边走 {@link #playBlastSound}，画面交给红蓝几何爆炸。</p>
     */
    public static void playImpact(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.35D, 0.35D, 0.35D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 6, 0.4D, 0.4D, 0.4D, 0.02D);
        playBlastSound(level, at, volume, pitch);
    }
}
