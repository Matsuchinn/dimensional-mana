package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.List;

/**
 * 五种植物的共同方块：一朵十字模型的小花，外加各自的一句特性。
 *
 * <p>特性都写在 {@link #entityInside} 里，也就是“碰到就生效”——这也是原版仙人掌、甜浆果丛
 * 的做法，不需要给每株植物挂方块实体，也不会在区块加载时多出任何常驻开销。
 * 持续性的部分（血吻花连续触碰计时）记在生物自己的持久化数据里，生物走开就自然衰减。</p>
 */
public class ManaPlantBlock extends BushBlock {
    /** 血吻花：连续触碰的累计刻数，以及上一次记到它的时刻。 */
    public static final String BLOOD_CONTACT = "dmBloodContact";
    public static final String BLOOD_LAST_TICK = "dmBloodLastTick";

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);
    /** 触碰超过 5 秒（100 刻）之后开始每秒掉血；血越少咬得越狠。 */
    private static final int BLOOD_BITE_TICKS = 100;
    /** 卢梅莉亚最多向上攀三格。 */
    private static final int LUMELIA_MAX_HEIGHT = 3;

    private final ManaPlants.Kind kind;

    public ManaPlantBlock(ManaPlants.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    /** 序列化兜底：植物的种类由注册 id 决定，这里只需要一个能通过校验的编码器。 */
    public ManaPlantBlock(Properties properties) {
        this(ManaPlants.Kind.RESTELIA, properties);
    }

    public ManaPlants.Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return simpleCodec(ManaPlantBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState below, BlockGetter level, BlockPos pos) {
        // 卢梅莉亚可以顺着自己往上攀，所以它踩着自己也算站得稳。
        if (kind == ManaPlants.Kind.LUMELIA && below.is(this)) return true;
        return ManaPlants.supported(below);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (kind == ManaPlants.Kind.LUMELIA && level.getBlockState(pos.below()).is(this)) return true;
        return super.canSurvive(state, level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || !living.isAlive()) return;
        switch (kind) {
            case LEVILIA -> leviliaTouch(level, living);
            case LUMELIA -> lumeliaTouch(level, living);
            case BLOOD_KISS -> bloodKissTouch(level, pos, living);
            case VESPER_THORN -> vesperThornTouch(level, living);
            default -> {
                // 瑞斯特莉亚只是会发光的草：价值全在叶片里，碰到本身不产生效果。
            }
        }
    }

    /** 魔素浸染的花朵：碰到就慢慢补魔素，每秒 0.5 点。 */
    private void leviliaTouch(Level level, LivingEntity living) {
        if (living.tickCount % 20 != 0) return;
        double before = ManaData.get(living).mana();
        ManaData.restoreMana(living, 0.5, false);
        if (ManaData.get(living).mana() == before) return;
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANT,
                    living.getX(), living.getY() + living.getBbHeight() * 0.55, living.getZ(),
                    4, 0.3, 0.35, 0.3, 0.02);
        }
    }

    /** 高等魔法药草：活着回血，亡灵反被灼伤。 */
    private void lumeliaTouch(Level level, LivingEntity living) {
        if (living.tickCount % 20 != 0) return;
        if (living.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) {
            float damage = living.getMaxHealth() * 0.05F;
            living.hurt(living.damageSources().magic(), damage);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(),
                        6, 0.25, 0.3, 0.25, 0.01);
            }
        } else if (living.getHealth() < living.getMaxHealth()) {
            living.heal(living.getMaxHealth() * 0.01F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.HEART,
                        living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(),
                        2, 0.25, 0.25, 0.25, 0.01);
            }
        }
    }

    /** 血吻花：贴住超过 5 秒后，每秒 3 点无视护甲的真实伤害；生命值低于一半时伤害翻倍。 */
    private void bloodKissTouch(Level level, BlockPos pos, LivingEntity living) {
        CompoundTag tag = living.getPersistentData();
        int previous = tag.getInt(BLOOD_CONTACT);
        int last = tag.getInt(BLOOD_LAST_TICK);
        int contact = (living.tickCount - last <= 2) ? previous + 1 : 1;
        tag.putInt(BLOOD_CONTACT, contact);
        tag.putInt(BLOOD_LAST_TICK, living.tickCount);
        if (contact < BLOOD_BITE_TICKS || contact % 20 != 0) return;
        // 受伤的生物挣扎不动，血吻花咬得更凶：低于一半生命时伤害翻倍。
        boolean weakened = living.getHealth() < living.getMaxHealth() * 0.5F;
        living.hurt(PlantEffects.trueDamage(level), weakened ? 6.0F : 3.0F);
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 0.4F, 0.6F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    living.getX(), living.getY() + 0.9, living.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
        }
    }

    /**
     * 暮刺荆棘：只在黄昏开花，花开时的接触会缠上“？”——具体是什么，第二天才知道。
     * 已经中了诅咒或还带着未解开的“？”就不再叠加，避免站在花丛里刷满时长。
     */
    private void vesperThornTouch(Level level, LivingEntity living) {
        if (!ManaPlants.isDusk(level)) return;
        if (living.hasEffect(GeneratedMod.VESPER_UNKNOWN) || living.hasEffect(GeneratedMod.VESPER_CURSE)) return;
        living.addEffect(new MobEffectInstance(GeneratedMod.VESPER_UNKNOWN, 24000, 0, false, false));
        level.playSound(null, living.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.7F, 0.55F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new DustParticleOptions(new Vector3f(0.78F, 0.55F, 0.92F), 1.0F),
                    living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                    18, 0.45, 0.6, 0.45, 0.0);
        }
        if (living instanceof net.minecraft.world.entity.player.Player player) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.vesper_unknown").withStyle(ChatFormatting.DARK_PURPLE), false);
        }
    }

    /** 卢梅莉亚顺着自己往上攀：随机刻长高一格，最高三格。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (kind != ManaPlants.Kind.LUMELIA) return;
        int height = 1;
        while (height < LUMELIA_MAX_HEIGHT && level.getBlockState(pos.below(height)).is(this)) height++;
        if (height >= LUMELIA_MAX_HEIGHT) return;
        if (random.nextFloat() > 0.35F) return;
        BlockPos above = pos.above();
        if (!level.getBlockState(above).canBeReplaced()) return;
        level.setBlock(above, defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** 会发光的两种植物在夜里飘一点微光，远远就能看见。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (kind.light() <= 0 || random.nextInt(8) != 0) return;
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double y = pos.getY() + 0.25 + random.nextDouble() * 0.55;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        int color = kind.glowColor();
        level.addParticle(new DustParticleOptions(new Vector3f(
                ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F), 0.8F),
                x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(kind.tooltipKey()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.dimensional_mana.plant.worldgen").withStyle(ChatFormatting.DARK_GRAY));
    }
}
