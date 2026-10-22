package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 魔锻炉与专业魔晶锻造炉共用的方块实体。
 *
 * <p>除了原版的烧炼流程，这里多一条“机器联动”：炉子的背面紧贴着魔晶加工台时，
 * 烧炼完成的产物不再结算原版经验，而是按产物等级折算成魔素，直接充进那台加工台。</p>
 */
public final class ManaFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
    /** 联动充能：普通产物与下/中/高/极/特魔晶分别对应的一格魔素。 */
    private static final int[] FORGE_LINK_MANA = {25, 100, 400, 1_000, 4_000};
    private static final int[] PROFESSIONAL_LINK_MANA = {50, 200, 800, 2_000, 8_000};
    private static final int FORGE_PLAIN_MANA = 1;
    private static final int PROFESSIONAL_PLAIN_MANA = 3;
    private static final int LINK_PARTICLE_COLOR = 0xAE69FF;

    private final ManaCookingRecipe.FurnaceKind kind;

    public ManaFurnaceBlockEntity(ManaCookingRecipe.FurnaceKind kind, BlockPos pos, BlockState state) {
        super(blockEntityType(kind), pos, state, recipeType(kind));
        this.kind = kind;
    }

    private static BlockEntityType<?> blockEntityType(ManaCookingRecipe.FurnaceKind kind) {
        return kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE
                ? GeneratedMod.MANA_FORGE_BLOCK_ENTITY.get()
                : GeneratedMod.PROFESSIONAL_BLOCK_ENTITY.get();
    }

    private static RecipeType<ManaCookingRecipe> recipeType(ManaCookingRecipe.FurnaceKind kind) {
        return ManaCookingRecipe.recipeType(kind);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(kind == ManaCookingRecipe.FurnaceKind.MANA_FORGE
                ? "container.dimensional_mana.mana_forge"
                : "container.dimensional_mana.professional_crystal_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ManaFurnaceMenu(containerId, inventory, this, dataAccess, kind);
    }

    /**
     * 原版在每次烧炼完成时都会调用这里，把“这一炉出了什么”记进 {@code recipesUsed}；
     * 取货时正是拿这本账去结算经验。联动时我们改充魔素、并且故意不记账，
     * 于是取货时无账可结 —— 正好对上“不再产出原版经验”，也不会多出没用的残留记录。
     */
    @Override
    public void setRecipeUsed(RecipeHolder<?> recipe) {
        Level level = this.level;
        if (recipe != null && level != null && !level.isClientSide) {
            CrystalProcessorBlockEntity processor = linkedProcessor();
            if (processor != null) {
                int mana = linkedMana(kind, recipe.value().getResultItem(level.registryAccess()));
                if (mana > 0) {
                    processor.addMana(mana);
                    chargeFeedback(processor);
                    return;
                }
            }
        }
        super.setRecipeUsed(recipe);
    }

    /** 炉子背面（正面的反方向）紧贴着的魔晶加工台；没有就返回 null。 */
    public CrystalProcessorBlockEntity linkedProcessor() {
        Level level = this.level;
        if (level == null) return null;
        BlockState state = getBlockState();
        if (!state.hasProperty(AbstractFurnaceBlock.FACING)) return null;
        BlockPos behind = worldPosition.relative(state.getValue(AbstractFurnaceBlock.FACING).getOpposite());
        if (!level.hasChunkAt(behind)) return null;
        return level.getBlockEntity(behind) instanceof CrystalProcessorBlockEntity processor ? processor : null;
    }

    /**
     * 联动时一炉产物折算成多少魔素：五级魔晶按下表，其余烧炼产物一律给固定值。
     *
     * @return 需要充入的魔素
     */
    public static int linkedMana(ManaCookingRecipe.FurnaceKind kind, ItemStack result) {
        boolean professional = kind == ManaCookingRecipe.FurnaceKind.PROFESSIONAL;
        int plain = professional ? PROFESSIONAL_PLAIN_MANA : FORGE_PLAIN_MANA;
        if (result.isEmpty()) return plain;
        int tier = crystalTier(result);
        if (tier < 0) return plain;
        return (professional ? PROFESSIONAL_LINK_MANA : FORGE_LINK_MANA)[tier];
    }

    /** 0~4 对应下/中/高/极/特级魔晶，普通物品返回 -1。 */
    public static int crystalTier(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals(GeneratedMod.MOD_ID)) return -1;
        return switch (id.getPath()) {
            case "lesser_mana_crystal" -> 0;
            case "medium_mana_crystal" -> 1;
            case "advanced_mana_crystal" -> 2;
            case "supreme_mana_crystal" -> 3;
            case "special_mana_crystal" -> 4;
            default -> -1;
        };
    }

    /** 从炉子飘向加工台的一串紫色光点：一眼就能看出这两台机器连上了。 */
    private void chargeFeedback(CrystalProcessorBlockEntity processor) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 from = Vec3.atCenterOf(worldPosition).add(0.0, 0.55, 0.0);
        Vec3 to = Vec3.atCenterOf(processor.getBlockPos()).add(0.0, 0.55, 0.0);
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(((LINK_PARTICLE_COLOR >> 16) & 255) / 255F,
                        ((LINK_PARTICLE_COLOR >> 8) & 255) / 255F,
                        (LINK_PARTICLE_COLOR & 255) / 255F), 1.0F);
        for (int step = 0; step <= 6; step++) {
            Vec3 at = from.lerp(to, step / 6.0);
            server.sendParticles(dust, at.x, at.y, at.z, 1, 0.08, 0.08, 0.08, 0.0);
        }
        server.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 0.25, to.z,
                3, 0.15, 0.15, 0.15, 0.01);
    }
}
