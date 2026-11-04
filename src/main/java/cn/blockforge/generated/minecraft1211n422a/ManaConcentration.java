package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;

/**
 * 世界魔素浓度。大尺度平滑噪声保证同一群系内不会突变，群系盐值允许跨群系边界出现台阶。
 */
public final class ManaConcentration {
    public static double at(ServerLevel level, BlockPos pos) {
        DimensionProfile profile = profile(level);
        double smoothNoise = smoothValueNoise(level.getSeed() ^ profile.salt, pos.getX() / 16.0, pos.getZ() / 16.0);
        double value = baseValue(level.getSeed(), profile, smoothNoise, level.getBiome(pos), pos);

        ManaCaveData caveData = ManaCaveData.get(level);
        double caveFloor = caveData.caveFloorAt(pos, 48, level.getSeed());
        if (caveFloor >= 0.0) value = Math.max(caveFloor, value);
        else if (isInsideAmethystGeode(level, pos)) value = Math.max(12.0, value);
        // 古代魔法祭坛：中央水平 12 格半径内再 +1.00。判定只看"在不在范围内"，
        // 范围重叠的两座祭坛也只加这一次——该效果不可叠加。
        if (AncientAltarData.isEmpowered(level, pos)) value += AncientAltarData.AURA_BONUS;
        // r282：露天魔晶矿簇：8 格内 +4.00，同样是"在不在圈内"的判定，重叠不叠加。
        if (CrystalClusterData.isEmpowered(level, pos)) value += CrystalClusterData.AURA_BONUS;
        // r301：被【虚无凋零射线】的扭曲光环扫过的区域，浓度在 10 分钟内只剩一半。
        value *= CorruptedZoneData.get(level).factorAt(pos, level.getGameTime());
        return Math.max(0.0, value);
    }

    /**
     * 不含洞穴、紫水晶加成的“地表浓度”。
     *
     * <p>世界生成阶段还没法读洞穴数据，所以地形生成只能用这一版。它和 {@link #at} 用同一套噪声与群系偏移，
     * 只少了“靠近洞穴至少 15”那一段加成，两块地算出的浓度因此不会互相打架，只是洞穴上方会偏低。</p>
     */
    public static double surfaceAt(long seed, ResourceKey<Level> dimension, Holder<Biome> biome, BlockPos pos) {
        DimensionProfile profile = profile(dimension);
        double smoothNoise = smoothValueNoise(seed ^ profile.salt, pos.getX() / 16.0, pos.getZ() / 16.0);
        return Math.max(0.0, baseValue(seed, profile, smoothNoise, biome, pos));
    }

    /**
     * r271：环境基准浓度。蚀魔荒原不走「维度均值 × 群系偏移」那套，直接钉在
     * 13.50 ± 2.00 —— 平滑噪声在这里当 ±2 的起伏用，保证同一张荒原里浓度
     * 渐变但绝不超过设计稿给的区间；其他群系维持原公式。
     */
    private static double baseValue(long seed, DimensionProfile profile, double smoothNoise,
                                    Holder<Biome> biome, BlockPos pos) {
        if (CorruptedWastelandBiome.is(biome)) {
            return CorruptedWastelandBiome.BASE_CONCENTRATION + smoothNoise * CorruptedWastelandBiome.VARIATION;
        }
        return profile.average * (1.0 + smoothNoise * profile.variation + biomeShift(biome, profile.variation));
    }

    public static double regenerationMultiplier(double concentration) {
        return Math.max(0.0, 1.0 + (concentration - 10.0) * 0.10);
    }

    private static DimensionProfile profile(ServerLevel level) {
        return profile(level.dimension());
    }

    private static DimensionProfile profile(ResourceKey<Level> dimension) {
        if (dimension == Level.NETHER) return new DimensionProfile(8.0, 0.35, 0x4E45544845524CL);
        if (dimension == Level.END) return new DimensionProfile(1.0, 0.05, 0x454E444D414E41L);
        return new DimensionProfile(10.0, 0.25, 0x4F564552574F52L);
    }

    private static double biomeShift(Holder<Biome> biome, double variation) {
        long hash = biome.unwrapKey().map(ResourceKey::location).map(Object::toString).map(ManaConcentration::hashString).orElse(0L);
        return ((toUnit(hash) * 2.0) - 1.0) * variation * 0.32;
    }

    private static double smoothValueNoise(long seed, double chunkX, double chunkZ) {
        // 12 个区块为一个控制网格；相邻区块间使用 smoothstep 插值。
        double gridX = chunkX / 12.0;
        double gridZ = chunkZ / 12.0;
        int x0 = Mth.floor(gridX);
        int z0 = Mth.floor(gridZ);
        double tx = smoothstep(gridX - x0);
        double tz = smoothstep(gridZ - z0);
        double a = signed(seed, x0, z0);
        double b = signed(seed, x0 + 1, z0);
        double c = signed(seed, x0, z0 + 1);
        double d = signed(seed, x0 + 1, z0 + 1);
        return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
    }

    private static boolean isInsideAmethystGeode(ServerLevel level, BlockPos center) {
        int amethyst = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -8; x <= 8; x += 4) {
            for (int y = -8; y <= 8; y += 4) {
                for (int z = -8; z <= 8; z += 4) {
                    cursor.setWithOffset(center, x, y, z);
                    if (!level.isLoaded(cursor)) continue;
                    var state = level.getBlockState(cursor);
                    if (state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST)
                            || state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.LARGE_AMETHYST_BUD)) {
                        if (++amethyst >= 4) return true;
                    }
                }
            }
        }
        return false;
    }

    private static double smoothstep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double signed(long seed, int x, int z) {
        return unitHash(seed, x, z) * 2.0 - 1.0;
    }

    /**
     * 按 (seed, x, z) 掷一个稳定的 [0,1) 随机数。
     *
     * <p>r284 起露天魔晶矿簇"普通群系也低概率生成"用它掷骰子：同一个世界、同一格的
     * 结果永远一样，所以世界生成和 {@code /dimensional_mana locate_cluster} 算出来的
     * 是同一个答案，不会出现"指令说这里有、跑过去却空着"。</p>
     */
    public static double unitHash(long seed, int x, int z) {
        long mixed = seed ^ ((long)x * 341873128712L) ^ ((long)z * 132897987541L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return toUnit(mixed);
    }

    private static double toUnit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long hashString(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private record DimensionProfile(double average, double variation, long salt) {}

    private ManaConcentration() {}
}
