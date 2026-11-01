package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 魔蚀织网者的事件接线：
 *
 * <ul>
 *   <li>元素抗性 / 易伤：奥术 −35%、音 −65%、光 +35%、火 +65%（受击管线里直接乘倍率）；</li>
 *   <li>光系法术波及蛛网时立刻把网化掉；</li>
 *   <li>【魔蚀】是强制状态：牛奶、{@code /effect clear} 一律否决，只有
 *       {@link MagicErosionEffect#cleanse} 能清；</li>
 *   <li>死亡时清空自己留下的全部蛛网与还在飞的蛛丝球；</li>
 *   <li>自然刷新：高魔素区块里把普通蜘蛛换成织网者（与晶化行尸 / 聚魔爆裂体 /
 *       魔蚀骸骨同一套「魔素变异体」规则）；</li>
 *   <li>玩家用剑砍 3 下、剪刀剪 1 下破坏魔蚀蛛网。</li>
 * </ul>
 *
 * <p>注：1.21.1 的 NeoForge 用 {@link LivingIncomingDamageEvent}（护甲结算之前）取代了旧版
 * {@code LivingHurtEvent}，抗性就在这里结算。</p>
 */
public final class CorruptedWeaverEvents {
    /** 区块魔素浓度阈值：和另外三只魔素变异体一致。 */
    static final double REPLACE_CONCENTRATION = CrystallizedZombieEvents.REPLACE_CONCENTRATION;
    /** 替换概率 50%。 */
    static final float REPLACE_CHANCE = CrystallizedZombieEvents.REPLACE_CHANCE;

    private CorruptedWeaverEvents() { }

    // ---------------------------------------------------------------- 抗性 / 易伤
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof CorruptedWeaverEntity weaver)) return;
        if (weaver.level().isClientSide) return;
        if (LethalDamage.isLethal(event.getSource(), event.getAmount())) return;
        CombatElement element = CombatElement.elementOf(event.getSource());
        if (element == null) return;
        double multiplier = switch (element) {
            // 抗性：奥术 35%、音 65%。
            case ARCANE -> 1.0D - 0.35D;
            case SOUND -> 1.0D - 0.65D;
            // 易伤：光 +35%、火 +65%。
            case LIGHT -> 1.0D + 0.35D;
            case FIRE -> 1.0D + 0.65D;
            default -> 1.0D;
        };
        if (element == CombatElement.LIGHT || element == CombatElement.FIRE) {
            // 吃到光 / 火就记一笔，接下来 10 秒它会往网里躲。
            weaver.markLightOrFireHurt();
        }
        if (multiplier != 1.0D) event.setAmount((float)(event.getAmount() * multiplier));
    }

    /** 光系伤害波及：半径内所有魔蚀蛛网立刻消散。 */
    public static void onLightDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity().level().isClientSide) return;
        if (CombatElement.elementOf(event.getSource()) != CombatElement.LIGHT) return;
        CorruptedWebBlock.dissolveNearLight(event.getEntity().level(), event.getEntity().position(), 4.0D);
    }

    // ---------------------------------------------------------------- 魔蚀的净化限制
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (event.isCanceled()) return;
        if (!event.getEffect().is(GeneratedMod.MAGIC_EROSION.getKey())) return;
        LivingEntity entity = event.getEntity();
        // 死亡时随身体一起消失是允许的；存活期间的任何移除都要走白名单净化。
        if (!entity.isAlive()) return;
        if (MagicErosionEffect.cleansing()) return;
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- 死亡清理
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof CorruptedWeaverEntity weaver)) return;
        if (!(weaver.level() instanceof ServerLevel level)) return;
        CorruptedWebBlock.clearOwner(level, weaver.getUUID());
        for (java.util.UUID shot : weaver.knownShots()) {
            if (level.getEntity(shot) instanceof WebProjectileEntity projectile) projectile.discard();
        }
    }

    // ---------------------------------------------------------------- 自然刷新
    /**
     * 自然刷新替换：主世界、魔素浓度 ≥ 12 的区块里刷普通蜘蛛时，50% 换成魔蚀织网者。
     *
     * <p>只认 {@link Spider} 的精确类型（洞穴蜘蛛、织网者自己都不会被二次替换），
     * 规则与晶化行尸 / 聚魔爆裂体 / 魔蚀骸骨完全一致。</p>
     */
    public static void onFinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled()) return;
        Mob mob = event.getEntity();
        if (mob.getClass() != Spider.class) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (ManaConcentration.at(level, mob.blockPosition()) < REPLACE_CONCENTRATION) return;
        if (level.getRandom().nextFloat() >= REPLACE_CHANCE) return;
        Mob replacement = GeneratedMod.CORRUPTED_WEAVER.get().create(level);
        if (replacement == null) return;
        event.setSpawnCancelled(true);
        replacement.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        replacement.finalizeSpawn(level, event.getDifficulty(), MobSpawnType.NATURAL, null);
        level.addFreshEntity(replacement);
    }

    // ---------------------------------------------------------------- 破坏蛛网
    /**
     * 玩家左键魔蚀蛛网：剑 3 下、剪刀 1 下，其它工具完全砍不动。
     *
     * <p>方块本身硬度设得很高，所以「必须靠这个事件」才能在合理时间内破网；
     * 事件取消掉的正是那种慢慢磨的挖掘进度。</p>
     */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CorruptedWebBlock)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        event.setCanceled(true);
        // 创造模式直接抠掉，别让建造玩家被自己放的网卡住。
        if (player.getAbilities().instabuild || stack.is(Items.SHEARS)) {
            CorruptedWebBlock.dissolve(level, event.getPos(), true);
            return;
        }
        if (stack.is(ItemTags.SWORDS)) {
            CorruptedWebBlock.hitByTool(level, event.getPos(), 1);
        }
    }
}
