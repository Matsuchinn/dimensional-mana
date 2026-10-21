package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.Map;

/** 鉴定眼镜使用的统一魔素含量估值。数值代表物体内蕴含、但不一定可直接吸收的魔素。 */
public final class ManaContent {
    private static final Map<String, Double> MOD_BASE_VALUES = Map.ofEntries(
            Map.entry("lesser", 100.0),
            Map.entry("medium", 400.0),
            Map.entry("advanced", 1600.0),
            Map.entry("supreme", 4000.0),
            Map.entry("special", 12000.0)
    );

    public static double item(ItemStack stack, double concentration) {
        if (stack.isEmpty()) return 0.0;
        ResourceLocation id = stack.getItem().builtInRegistryHolder().key().location();
        double value;
        if (id.getNamespace().equals(GeneratedMod.MOD_ID)) {
            value = modItem(id.getPath());
        } else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            value = 600.0;
        } else if (stack.is(Items.AMETHYST_SHARD)) {
            value = 45.0;
        } else if (stack.is(Items.AMETHYST_BLOCK) || stack.is(Items.BUDDING_AMETHYST)) {
            value = 60.0;
        } else if (stack.is(Items.LAPIS_LAZULI)) {
            value = 18.0;
        } else if (stack.is(Items.LAPIS_BLOCK)) {
            value = 60.0;
        } else if (stack.getItem() instanceof BlockItem blockItem) {
            value = blockBase(blockItem.getBlock().defaultBlockState()) * concentrationFactor(concentration);
        } else {
            double ability = Math.min(8.0, Math.max(0.1, stack.getItem().getEnchantmentValue() * 0.35));
            value = ability * concentrationFactor(concentration);
        }
        value += enchantmentMana(stack);
        return value * stack.getCount();
    }

    public static double block(Level level, BlockPos pos, double concentration) {
        BlockState state = level.getBlockState(pos);
        String id = state.getBlock().builtInRegistryHolder().key().location().toString();
        for (Map.Entry<String, Double> tier : MOD_BASE_VALUES.entrySet()) {
            if (id.contains(tier.getKey() + "_mana_crystal_ore")) return tier.getValue();
        }
        if (id.equals(GeneratedMod.MOD_ID + ":special_mana_crystal_cluster")) return 12000.0;
        if (id.equals(GeneratedMod.MOD_ID + ":pure_liquid_mana")) return 12000.0;
        if (state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST)
                || state.is(Blocks.AMETHYST_CLUSTER)) return 60.0;
        if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE) || state.is(Blocks.LAPIS_BLOCK)) return 60.0;
        double enchantPower = Math.max(0.0, state.getEnchantPowerBonus(level, pos));
        return Math.min(60.0, (blockBase(state) + enchantPower * 20.0) * concentrationFactor(concentration));
    }

    private static double modItem(String path) {
        for (Map.Entry<String, Double> tier : MOD_BASE_VALUES.entrySet()) {
            if (!path.contains(tier.getKey())) continue;
            if (path.endsWith("_dust")) return tier.getValue() / 4.0;
            // 原矿、矿块、成品魔晶、补魔剂均按对应级别计量。
            return tier.getValue();
        }
        if (path.equals("pure_liquid_mana") || path.equals("pure_liquid_mana_bucket")) return 12000.0;
        if (path.equals("mana_harmonizer")) return 25.0;
        if (path.equals("appraisal_orb")) return 80.0;
        if (path.equals("mana_detector")) return 110.0;
        if (path.equals("appraisal_glasses")) return 180.0;
        if (path.equals("mana_forge")) return 900.0;
        if (path.equals("professional_crystal_furnace")) return 2600.0;
        if (path.equals("crystal_processor")) return 1800.0;
        if (path.equals("lapis_dust")) return 18.0;
        if (path.equals("amethyst_dust")) return 45.0;
        return 5.0;
    }

    private static double enchantmentMana(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        double total = enchantmentMana(enchantments) + enchantmentMana(stored);
        if (stack.getItem() instanceof EnchantedBookItem) total *= 1.25;
        return total;
    }

    private static double enchantmentMana(ItemEnchantments enchantments) {
        double total = 0.0;
        for (var holder : enchantments.keySet()) {
            int level = enchantments.getLevel(holder);
            total += 35.0 * level * level;
        }
        return total;
    }

    private static double blockBase(BlockState state) {
        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness < 0) return 10.0;
        return Math.max(0.1, Math.min(10.0, 0.25 + hardness * 0.8));
    }

    private static double concentrationFactor(double concentration) {
        return Math.max(0.25, concentration / 10.0);
    }

    private ManaContent() {}
}
