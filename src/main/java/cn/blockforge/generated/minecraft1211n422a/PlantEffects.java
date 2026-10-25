package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;

/**
 * 三种植物的状态效果：魔力充盈（增益）、未解的“？”与维斯珀德索恩诅咒、血吻花啃咬。
 *
 * <p>“？”只是一个名字看不出来的占位状态：它到期的那一刻会换成真正的诅咒，
 * 也就是需求里的“次日揭示”。两者共用一层数值，诅咒期间每秒吸 1 点魔素，
 * 魔素见底之后改成每秒 1 点生命，直到三个游戏日后自己结束。</p>
 */
public final class PlantEffects {
    /** 植物造成的“真实伤害”：无视护甲、抗性、附魔、盾牌。 */
    private static final ResourceKey<DamageType> PLANT_TRUE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "plant_true"));

    /** 魔力充盈：60 秒内魔素亲和 ×2.5。 */
    public static final int FULLNESS_TICKS = 1200;
    public static final double FULLNESS_AFFINITY = 2.5;
    /** “？”持续一整个游戏日，第二天揭示。 */
    public static final int UNKNOWN_TICKS = 24000;
    /** 诅咒持续三个游戏日。 */
    public static final int CURSE_TICKS = 72000;

    /**
     * 植物的真实伤害源。
     *
     * <p>需求里血吻花的啃咬和暮刺荆棘的抽血都写着“真实伤害”，所以这里用自带的
     * {@code plant_true} 伤害类型，而不是会被保护附魔、抗性提升削一刀的魔法伤害。</p>
     */
    public static DamageSource trueDamage(Level level) {
        return level.damageSources().source(PLANT_TRUE);
    }

    /** 魔力充盈：只做标记，亲和乘区在 {@link ManaEvents} 的回魔计算里读取。 */
    public static final class Fullness extends MobEffect {
        public Fullness() {
            super(MobEffectCategory.BENEFICIAL, 0x4FB3FF);
        }
    }

    /** 未解的“？”：名字就是一个问号，看不出任何信息。 */
    public static final class Unknown extends MobEffect {
        public Unknown() {
            super(MobEffectCategory.HARMFUL, 0x241436);
        }
    }

    /** 维斯珀德索恩诅咒：每秒抽 1 点魔素，抽干后每秒扣 1 点生命。 */
    public static final class Curse extends MobEffect {
        public Curse() {
            super(MobEffectCategory.HARMFUL, 0x6B1A4A);
        }

        @Override
        public boolean applyEffectTick(LivingEntity living, int amplifier) {
            if (living.level().isClientSide || living.tickCount % 20 != 0) return true;
            ManaData.Snapshot snapshot = ManaData.get(living);
            if (snapshot.mana() >= 1.0) {
                ManaData.drainMana(living, 1.0);
            } else {
                ManaData.drainMana(living, snapshot.mana());
                living.hurt(trueDamage(living.level()), 1.0F);
            }
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }
    }

    /** 把“？”换成真正的诅咒；由效果到期事件调用。 */
    public static void revealCurse(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel)) return;
        entity.addEffect(new MobEffectInstance(GeneratedMod.VESPER_CURSE, CURSE_TICKS, 0, false, true));
        if (entity instanceof Player player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.dimensional_mana.vesper_curse").withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
        }
    }

    /** 血吻花蔓延：击杀发生在花朵附近时，向周围补种 1~3 株。 */
    public static void spreadBloodKiss(ServerLevel level, BlockPos death) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        java.util.List<BlockPos> flowers = new java.util.ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    cursor.setWithOffset(death, x, y, z);
                    if (!level.isLoaded(cursor)) continue;
                    if (ManaPlants.isPlant(level.getBlockState(cursor), ManaPlants.Kind.BLOOD_KISS)) {
                        flowers.add(cursor.immutable());
                    }
                }
            }
        }
        if (flowers.isEmpty()) return;
        for (BlockPos flower : flowers) {
            int amount = 1 + level.getRandom().nextInt(3);
            ManaPlants.placeCluster(level, ManaPlants.Kind.BLOOD_KISS, flower, level.getRandom(), amount);
        }
    }

    /** 加了“+”标签的食物：吃下去再额外补 10% 魔素与 10% 生命。 */
    public static void fortifiedFoodEaten(LivingEntity living, ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(PlantFood.TAG)) return;
        ManaData.restoreMana(living, ManaData.get(living).maxMana() * 0.10, false);
        if (living.getHealth() < living.getMaxHealth()) living.heal(living.getMaxHealth() * 0.10F);
    }

    private PlantEffects() {
    }
}
