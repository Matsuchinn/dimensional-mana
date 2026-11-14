package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 晶化行尸的事件接线：致命伤害拦截、晶爆反伤、高魔素区块的自然刷怪替换。
 *
 * <p>监听顺序：本类必须注册在 {@link ManaEvents} 的对应监听之后，
 * 这样 /kill 这类必杀伤害已经先被收窄成有限值，判定才不会算出 NaN。</p>
 *
 * <p>刷怪替换同时管三种怪：普通僵尸 → 晶化行尸，普通苦力怕 → 聚魔爆裂体，
 * 普通骷髅 → 魔蚀骸骨，条件都是「主世界 + 区块魔素浓度 ≥ 12 + 50% 概率」。</p>
 */
public final class CrystallizedZombieEvents {
    /** 区块魔素浓度阈值：达到它才可能把普通僵尸 / 苦力怕换成魔晶化的版本。 */
    static final double REPLACE_CONCENTRATION = 12.0;
    /** 替换概率：50%。 */
    static final float REPLACE_CHANCE = 0.50F;

    private CrystallizedZombieEvents() { }

    /**
     * 致命伤害拦截：把「直接死」换成「原地僵直颤抖 4 秒后晶爆」。
     * 颤抖期间一切伤害取消 —— 这就是设计稿说的无敌。
     */
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof CrystallizedZombie zombie) || zombie.level().isClientSide) return;
        if (zombie.isCrystalShock()) {
            event.setCanceled(true);
            return;
        }
        if (event.getAmount() >= zombie.getHealth()) {
            event.setCanceled(true);
            zombie.beginCrystalDetonation();
        }
    }

    /** 被动「晶爆反伤」：真正掉血（非环境伤害）后炸一圈晶体冲击。 */
    public static void onDamagePost(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof CrystallizedZombie zombie)) return;
        if (event.getNewDamage() <= 0.0F) return;
        if (isEnvironmental(event.getSource())) return;
        zombie.retaliateBurst();
    }

    /**
     * 自然刷怪替换：主世界、浓度 ≥12 的区块里刷普通僵尸 / 普通苦力怕时，50% 换成对应的魔晶化版本
     * （僵尸 → 晶化行尸，苦力怕 → 聚魔爆裂体）。
     *
     * <p>只认 {@link Zombie}、{@link Creeper}、{@link Skeleton} 的精确类型（小僵尸、僵尸村民、尸壳、
     * 流浪者、沼骸都是别的实体，不动；高压苦力怕仍然是 Creeper，照常参与替换）；
     * 三只魔晶化生物虽然继承它们，但类型不同，永远不会被二次替换。</p>
     *
     * <p>r244 起加入魔蚀骸骨：普通骷髅 → 魔蚀骸骨，条件和另外两只完全一样。</p>
     */
    public static void onFinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled()) return;
        Mob mob = event.getEntity();
        boolean zombie = mob.getClass() == Zombie.class;
        boolean creeper = mob.getClass() == Creeper.class;
        boolean skeleton = mob.getClass() == Skeleton.class;
        // r265：原版末影人也会被换成虚空蚀影，条件与其它魔素变异体完全一致。
        boolean enderman = mob.getClass() == net.minecraft.world.entity.monster.EnderMan.class;
        if (!zombie && !creeper && !skeleton && !enderman) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (ManaConcentration.at(level, mob.blockPosition()) < REPLACE_CONCENTRATION) return;
        // r105 权重：替换概率 = 50% × 目标变异体在配置里的权重。调到 0 时这种变异体
        // 不再通过「替换普通怪」出现，原版怪保持原样。
        String weightKey = zombie ? "crystallized_zombie"
                : creeper ? "mana_creeper"
                : skeleton ? "corrupted_skeleton"
                : "void_shade";
        if (!MobSpawnWeights.roll(level.getRandom(), REPLACE_CHANCE, weightKey)) return;
        // 先造替换者，造得出来才取消原生成。
        Mob replacement = zombie
                ? GeneratedMod.CRYSTALLIZED_ZOMBIE.get().create(level)
                : creeper ? GeneratedMod.MANA_CREEPER.get().create(level)
                : skeleton ? GeneratedMod.CORRUPTED_SKELETON.get().create(level)
                : GeneratedMod.VOID_SHADE.get().create(level);
        if (replacement == null) return;
        event.setSpawnCancelled(true);
        replacement.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        // 替换概率上面已经乘过一次权重，这里不再让 NATURAL 权重门二次衰减。
        MobSpawnWeights.bypassing(() -> {
            replacement.finalizeSpawn(level, event.getDifficulty(), MobSpawnType.NATURAL, null);
            level.addFreshEntity(replacement);
        });
    }

    /**
     * 兜底：万一是没走受击管线就把血量清零的奇怪死法，至少晶爆尸潮照常上演，
     * 免得这只怪「安静地死掉」让整套设计凭空蒸发。
     */
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof CrystallizedZombie zombie && !zombie.isCrystalShock()) {
            zombie.detonate();
        }
    }

    /** 环境伤害：没有参与战斗的实体来源（坠落、岩浆、溺水、窒息、虚空……）。 */
    static boolean isEnvironmental(DamageSource source) {
        if (source.getEntity() != null || source.getDirectEntity() != null) return false;
        // 无主爆炸（TNT、水晶）按战斗伤害处理，晶体应该炸得起来。
        return !source.is(DamageTypeTags.IS_EXPLOSION);
    }
}
