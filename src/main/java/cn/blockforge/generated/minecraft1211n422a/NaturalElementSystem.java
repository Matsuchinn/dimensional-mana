package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 生物与自然环境互动时产生元素附着；玩家同样属于 LivingEntity。 */
public final class NaturalElementSystem {
    private static final String DATA_KEY = "DimensionalManaNaturalElements";
    private static final String ACTIVE_KEY = "activeConditions";
    private static final String INITIALIZED_KEY = "initialized";
    private static final String GROUNDED_KEY = "grounded";
    private static final String AIRBORNE_TICKS_KEY = "airborneTicks";
    private static final String JUMPED_KEY = "jumped";
    private static final String GRASS_TICKS_KEY = "grassTicks";
    private static final String SUN_TICKS_KEY = "sunTicks";
    private static final String GRASS_READY_AT_KEY = "grassReadyAt";
    private static final String SUN_READY_AT_KEY = "sunReadyAt";

    private static final int WATER = 1 << 0;
    private static final int LAVA = 1 << 1;
    private static final int GRASS = 1 << 2;
    private static final int SUN = 1 << 3;
    private static final int WIND_SPEED = 1 << 4;
    private static final int SOUND_SPEED = 1 << 5;
    private static final int RAIN = 1 << 6;
    private static final int SNOW = 1 << 7;

    private static final int GRASS_REQUIRED_TICKS = 12 * 20;
    private static final int SUN_REQUIRED_TICKS = 30 * 20;
    /** 草地与日光环境附着各自每 120 秒最多触发一次（法术/攻击附着不受影响）。 */
    private static final long PASSIVE_ATTACHMENT_COOLDOWN = 120L * 20L;
    private static final double WIND_BLOCKS_PER_TICK = 13.9 / 20.0;
    private static final double SOUND_BLOCKS_PER_TICK = 340.0 / 20.0;

    public static void tick(LivingEntity living) {
        if (!(living.level() instanceof ServerLevel level) || !living.isAlive()) return;

        CompoundTag data = living.getPersistentData().getCompound(DATA_KEY);
        int previousConditions = data.getInt(ACTIVE_KEY);
        int currentConditions = 0;

        boolean inWater = living.isInWater();
        boolean inLava = living.isInLava();
        currentConditions = updateCondition(living, previousConditions, currentConditions,
                WATER, inWater, ElementType.WATER);
        currentConditions = updateCondition(living, previousConditions, currentConditions,
                LAVA, inLava, ElementType.FIRE);

        BlockPos feet = living.blockPosition();
        BlockState ground = level.getBlockState(feet.below());
        boolean grounded = living.onGround();

        int grassTicks = grounded && ground.is(Blocks.GRASS_BLOCK)
                ? Math.min(GRASS_REQUIRED_TICKS, data.getInt(GRASS_TICKS_KEY) + 1) : 0;
        data.putInt(GRASS_TICKS_KEY, grassTicks);
        currentConditions = updateCooledCondition(living, data, currentConditions,
                GRASS, grassTicks >= GRASS_REQUIRED_TICKS, ElementType.NATURE, GRASS_READY_AT_KEY);

        BlockPos weatherPos = BlockPos.containing(living.getEyePosition());
        boolean exposedToSky = level.canSeeSky(weatherPos);
        Biome.Precipitation precipitation = level.getBiome(weatherPos).value().getPrecipitationAt(weatherPos);
        boolean precipitationFalling = level.isRaining() && exposedToSky;
        boolean raining = precipitationFalling && precipitation == Biome.Precipitation.RAIN;
        boolean snowing = precipitationFalling && precipitation == Biome.Precipitation.SNOW;
        currentConditions = updateCondition(living, previousConditions, currentConditions,
                RAIN, raining, ElementType.WATER);
        currentConditions = updateCondition(living, previousConditions, currentConditions,
                SNOW, snowing, ElementType.ICE);

        boolean inSunlight = level.dimensionType().hasSkyLight() && level.isDay()
                && exposedToSky && !raining && !snowing;
        int sunTicks = inSunlight
                ? Math.min(SUN_REQUIRED_TICKS, data.getInt(SUN_TICKS_KEY) + 1) : 0;
        data.putInt(SUN_TICKS_KEY, sunTicks);
        currentConditions = updateCooledCondition(living, data, currentConditions,
                SUN, sunTicks >= SUN_REQUIRED_TICKS, ElementType.LIGHT, SUN_READY_AT_KEY);

        double speedSquared = living.getDeltaMovement().lengthSqr();
        currentConditions = updateCondition(living, previousConditions, currentConditions,
                WIND_SPEED, speedSquared >= WIND_BLOCKS_PER_TICK * WIND_BLOCKS_PER_TICK, ElementType.WIND);
        currentConditions = updateCondition(living, previousConditions, currentConditions,
                SOUND_SPEED, speedSquared >= SOUND_BLOCKS_PER_TICK * SOUND_BLOCKS_PER_TICK, ElementType.SOUND);

        trackLanding(living, data, grounded, ground);
        data.putInt(ACTIVE_KEY, currentConditions);
        living.getPersistentData().put(DATA_KEY, data);
    }

    public static void struckByLightning(LivingEntity living) {
        if (!living.level().isClientSide && living.isAlive()) {
            ElementReactionSystem.apply(living, living, ElementType.LIGHTNING);
        }
    }

    private static int updateCondition(LivingEntity living, int previousConditions, int currentConditions,
                                       int condition, boolean active, ElementType element) {
        if (!active) return currentConditions;
        currentConditions |= condition;
        if ((previousConditions & condition) == 0) {
            ElementReactionSystem.apply(living, living, element);
        } else {
            ElementReactionSystem.refresh(living, element);
        }
        return currentConditions;
    }

    /**
     * 草地/日光属于低频被动来源：达到蓄积时间后只触发一次，不在条件持续时刷新附着。
     * readyAt 写进实体持久数据，所以每只生物独立计时，重进世界也不会绕过 120 秒冷却。
     */
    private static int updateCooledCondition(LivingEntity living, CompoundTag data, int currentConditions,
                                             int condition, boolean active, ElementType element, String readyAtKey) {
        if (!active) return currentConditions;
        currentConditions |= condition;
        long now = living.level().getGameTime();
        if (now >= data.getLong(readyAtKey)) {
            ElementReactionSystem.apply(living, living, element);
            data.putLong(readyAtKey, now + PASSIVE_ATTACHMENT_COOLDOWN);
        }
        return currentConditions;
    }

    private static void trackLanding(LivingEntity living, CompoundTag data, boolean grounded, BlockState ground) {
        boolean initialized = data.getBoolean(INITIALIZED_KEY);
        boolean wasGrounded = data.getBoolean(GROUNDED_KEY);
        int airborneTicks = grounded ? data.getInt(AIRBORNE_TICKS_KEY)
                : Math.min(20, data.getInt(AIRBORNE_TICKS_KEY) + 1);
        boolean jumped = data.getBoolean(JUMPED_KEY)
                || (!grounded && wasGrounded && living.getDeltaMovement().y > 0.0);

        if (initialized && grounded && !wasGrounded && jumped && airborneTicks >= 2
                && isEarthLandingBlock(ground) && living.getRandom().nextFloat() < 0.10F) {
            ElementReactionSystem.apply(living, living, ElementType.EARTH);
        }

        data.putBoolean(INITIALIZED_KEY, true);
        data.putBoolean(GROUNDED_KEY, grounded);
        data.putBoolean(JUMPED_KEY, grounded ? false : jumped);
        data.putInt(AIRBORNE_TICKS_KEY, grounded ? 0 : airborneTicks);
    }

    private static boolean isEarthLandingBlock(BlockState ground) {
        return ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL);
    }

    private NaturalElementSystem() { }
}
