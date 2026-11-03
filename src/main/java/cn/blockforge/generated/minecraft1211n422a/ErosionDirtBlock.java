package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 秽蚀土：荒原的表层土。踩着它走的人会被污浊的魔素拖慢脚步——
 * 非潜行实体每次踩踏获得 20 tick 减速 I（−10% 移速）。潜行通过不受影响。
 */
public class ErosionDirtBlock extends Block {
    /** 减速效果的持续与强度：20 tick，等级 0（即 −10%）。 */
    private static final int SLOWDOWN_TICKS = 20;
    private static final int SLOWDOWN_AMPLIFIER = 0;

    public ErosionDirtBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        // 潜行的玩家（以及理论上会潜行的其他生物）免疫这片脏土的拖拽。
        if (living instanceof Player player && player.isCrouching()) return;
        if (living.isInWaterOrRain()) return;
        if (level.isClientSide) {
            // 客户端不预写效果：服务端同步为准，避免两端时长打架。
            return;
        }
        MobEffectInstance current = living.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        // 已经被减速且剩余时间还够，就不反复刷新（省同步包）。
        if (current != null && current.getAmplifier() >= SLOWDOWN_AMPLIFIER && current.getDuration() > 10) return;
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWDOWN_TICKS, SLOWDOWN_AMPLIFIER,
                false, true));
    }
}
