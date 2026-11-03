package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * 蚀魔荒原（Corrupted Wasteland）群系的键与判定入口。
 *
 * <p>群系本体是数据包注册表里的 JSON（data/dimensional_mana/worldgen/biome/corrupted_wasteland.json），
 * 这里只持有它的 ResourceKey，供魔素浓度（{@link ManaConcentration}）、地表特征
 * （{@link ErosionSurfaceFeature}）与白昼刷怪（{@link ErosionWastelandSpawning}）判定使用。</p>
 */
public final class CorruptedWastelandBiome {
    /** 群系键：JSON 文件名必须和这里的路径一致。 */
    public static final ResourceKey<Biome> KEY = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "corrupted_wasteland"));

    /** 蚀纹木的树配置键：树苗生长与群系特征共用同一棵「自定义 TreeConfiguration」。 */
    public static final ResourceKey<ConfiguredFeature<?, ?>> EROSION_TREE = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "erosion_tree"));

    /** 群系环境魔素浓度基准：13.50。 */
    public static final double BASE_CONCENTRATION = 13.50;
    /** 群系内浓度浮动幅度：±2.00。 */
    public static final double VARIATION = 2.00;

    /** 这个群系是不是蚀魔荒原。 */
    public static boolean is(Holder<Biome> biome) {
        return biome.is(KEY);
    }

    private CorruptedWastelandBiome() { }
}
