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
        double biomeShift = biomeShift(level.getBiome(pos), profile.variation);
        double value = profile.average * (1.0 + smoothNoise * profile.variation + biomeShift);

        ManaCaveData caveData = ManaCaveData.get(level);
        if (caveData.isNearCave(pos, 48)) value = Math.max(15.0, value);
        else if (isInsideAmethystGeode(level, pos)) value = Math.max(12.0, value);
        return Math.max(0.0, value);
    }

    public static double regenerationMultiplier(double concentration) {
        return Math.max(0.0, 1.0 + (concentration - 10.0) * 0.10);
    }

    private static DimensionProfile profile(ServerLevel level) {
        if (level.dimension() == Level.NETHER) return new DimensionProfile(8.0, 0.35, 0x4E45544845524CL);
        if (level.dimension() == Level.END) return new DimensionProfile(1.0, 0.05, 0x454E444D414E41L);
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
        long mixed = seed ^ ((long)x * 341873128712L) ^ ((long)z * 132897987541L);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return toUnit(mixed) * 2.0 - 1.0;
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
