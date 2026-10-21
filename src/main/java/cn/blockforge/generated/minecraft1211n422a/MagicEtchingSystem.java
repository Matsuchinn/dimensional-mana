package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/** 高等魔法刻蚀的独立增伤、额外真伤和十秒生命缺口锁定。 */
public final class MagicEtchingSystem {
    private static final String DATA = "DimensionalManaGreaterEtching";
    private static final String WINDOW = "ProcWindow";
    private static final String COUNT = "ProcCount";
    private static final String HEAL_LOCK_UNTIL = "HealLockUntil";
    private static final String LOCKED_HEALTH_LOSS = "LockedHealthLoss";
    private static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "etching_true"));

    public static boolean equipped(LivingEntity entity) {
        return MagicTrinketItem.isEquipped(entity, "greater_magic_etching");
    }

    /** 全部法术伤害在最终结算前进入独立乘区；同名饰品重复佩戴也只计算一次。 */
    public static float amplifySpellDamage(LivingEntity caster, float amount) {
        return equipped(caster) ? amount * 1.35F : amount;
    }

    /** 每次法术伤害成功落地后尝试触发，单个施法者每 20 刻最多两次。 */
    public static void afterSpellDamage(LivingEntity caster, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || !equipped(caster)) return;
        long now = level.getGameTime();
        CompoundTag data = caster.getPersistentData().getCompound(DATA);
        long window = data.getLong(WINDOW);
        int count = data.getInt(COUNT);
        if (now - window >= 20L || now < window) {
            window = now;
            count = 0;
        }
        if (count >= 2 || caster.getRandom().nextFloat() >= 0.20F) return;
        data.putLong(WINDOW, window);
        data.putInt(COUNT, count + 1);
        caster.getPersistentData().put(DATA, data);

        float amount = Math.max(0.0F, target.getMaxHealth() * 0.0225F);
        if (amount <= 0.0F) return;
        var registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        if (!registry.containsKey(TRUE_DAMAGE)) return;
        float before = target.getHealth();
        boolean hurt = target.hurt(level.damageSources().source(TRUE_DAMAGE, caster, caster), amount);
        if (!hurt) return;
        float after = target.getHealth();
        if (after >= before) return;
        CompoundTag lock = target.getPersistentData().getCompound(DATA);
        float previousLockedLoss = lock.getLong(HEAL_LOCK_UNTIL) > now ? lock.getFloat(LOCKED_HEALTH_LOSS) : 0.0F;
        lock.putLong(HEAL_LOCK_UNTIL, now + 200L);
        lock.putFloat(LOCKED_HEALTH_LOSS, previousLockedLoss + (before - after));
        target.getPersistentData().put(DATA, lock);
    }

    public static boolean isTrueDamage(DamageSource source) {
        return source.is(TRUE_DAMAGE);
    }

    /** 每刻兜底钳制生命值，连直接 setHealth 的治疗也不能绕过锁疗。 */
    public static void tickLiving(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        CompoundTag lock = entity.getPersistentData().getCompound(DATA);
        long now = entity.level().getGameTime();
        if (lock.getLong(HEAL_LOCK_UNTIL) <= now) return;
        float ceiling = Math.max(0.0F, entity.getMaxHealth() - lock.getFloat(LOCKED_HEALTH_LOSS));
        if (entity.getHealth() > ceiling) entity.setHealth(ceiling);
    }

    /** 锁定真伤造成的生命缺口：其他伤害仍可正常治疗，但十秒内不能越过锁定生命线。 */
    public static void limitHealing(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        CompoundTag lock = entity.getPersistentData().getCompound(DATA);
        long now = entity.level().getGameTime();
        if (lock.getLong(HEAL_LOCK_UNTIL) <= now) {
            lock.remove(HEAL_LOCK_UNTIL);
            lock.remove(LOCKED_HEALTH_LOSS);
            entity.getPersistentData().put(DATA, lock);
            return;
        }
        float ceiling = Math.max(0.0F, entity.getMaxHealth() - lock.getFloat(LOCKED_HEALTH_LOSS));
        float allowed = Math.max(0.0F, ceiling - entity.getHealth());
        event.setAmount(Math.min(event.getAmount(), allowed));
    }

    private MagicEtchingSystem() { }
}
