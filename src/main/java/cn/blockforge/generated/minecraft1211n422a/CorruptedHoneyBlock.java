package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 魔蚀蜂蜜块：四瓶魔蚀蜂蜜在魔晶加工台压成的一块稠黑蜜砖。
 *
 * <p><b>2 倍黏着物理（r92 重做）</b>：r91 说要「走得更慢、跳得更矮」，但代码里压根没写
 * {@link #getSpeedFactor()} / {@link #getJumpFactor()} 这两个覆写，注释吹的牛没落地；
 * 同时「咬住边缘」的阈值被从原版 7/16 改成 9/16，方向搞反了——玩家贴着侧面时身体中心
 * 离方块中心 0.8 格，9/16+0.3 = 0.8625 够不着，于是<b>侧面完全不粘</b>（r92 用户报的就是这个）。
 * 现在：贴着侧面照样咬住往下溜（判定改回欧氏距离、抓取半径 0.3 格，比原版还宽一点），
 * 站上去走得慢（speedFactor 0.3）、跳得矮（jumpFactor 0.7）、滑落封顶 0.025（原版一半）。</p>
 *
 * <p><b>粒子换色</b>：原版蜂蜜块的滑蜜 / 坠蜜粒子走 {@code broadcastEntityEvent(53/54)}
 * → 客户端打 HONEY_BLOCK 方块碎屑（橙黄）；这里整套事件都不发，改为客户端直接喷
 * 蓝紫色的魔素尘 + 一缕魂火，从这块蜜砖上滑下来的人一眼就是「魔蚀」的。</p>
 *
 * <p>魔法：身体接触这块蜜砖的生物，受到的<b>法术类伤害</b>进入独立乘区 ×0.55（−45%），
 * 与元素镀层、护甲、斗气这些乘区全部乘算，不会叠加成免疫
 * （判定见 {@link #isTouching}，接线点在 {@code ManaEvents.onIncomingDamage}）。</p>
 */
public final class CorruptedHoneyBlock extends HoneyBlock {
    /** 原版滑落封顶 0.05 → 一半：2 倍黏着的「挂不住也下不来」。 */
    private static final double SLIDE_CAP = 0.025D;
    /** 原版 MIN_FALL_SPEED 0.08 → 0.06：碰一下就进滑落，更黏。 */
    private static final double SLIDE_MIN_FALL = 0.06D;
    /**
     * 「咬住」半径：原版是 7/16 = 0.4375 格（加身体半宽），这里放宽到 0.3。
     *
     * <p>贴墙站的玩家身体中心离方块中心 0.5 + 半宽，半径越大越难咬住；
     * 0.3 比原版宽，侧面、贴角都能挂住，正好补回 r91 弄丢的「侧面不粘」。</p>
     */
    private static final double SLIDE_GRAB = 0.3D;
    /** 站在蜜砖上的移动速度系数（原版蜂蜜块是 1.0，这里压到 0.3，比灵魂沙 0.4 还黏）。 */
    private static final float STICKY_SPEED_FACTOR = 0.3F;
    /** 站在蜜砖上的起跳系数（原版 1.0，这里 0.7：跳得起来，但明显发沉）。 */
    private static final float STICKY_JUMP_FACTOR = 0.7F;
    /** 魔素尘粒子颜色：蓝紫。 */
    private static final DustParticleOptions EROSION_DUST =
            new DustParticleOptions(new Vector3f(0.42F, 0.28F, 0.95F), 1.3F);

    public CorruptedHoneyBlock(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- 黏着物理
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // 不叫 super：原版这里会 broadcastEntityEvent(53) 让客户端打黄色蜂蜜碎屑。
        if (isSlidingDown(pos, entity)) {
            if (entity instanceof ServerPlayer player && level.getGameTime() % 20L == 0L) {
                CriteriaTriggers.HONEY_BLOCK_SLIDE.trigger(player, state);
            }
            throttleSlide(entity);
            slideEffects(level, entity);
        }
    }

    /** 「正顺着这块蜜砖往下滑」：悬空、下落够快、身体咬进方块边缘（贴侧面也算）。 */
    private static boolean isSlidingDown(BlockPos pos, Entity entity) {
        if (entity.onGround()) return false;
        if (entity.getY() > pos.getY() + 0.9375D - 1.0E-7D) return false;
        if (entity.getDeltaMovement().y >= -SLIDE_MIN_FALL) return false;
        // 欧氏距离（与原版同口径）：判定的是「身体中心离方块中心够不够远」，
        // 也就是身体已经挂在方块外侧——贴着侧面往下掉、扒着边角都落在这一档。
        double dx = pos.getX() + 0.5D - entity.getX();
        double dz = pos.getZ() + 0.5D - entity.getZ();
        double reach = entity.getBbWidth() / 2.0D + SLIDE_GRAB;
        return dx * dx + dz * dz > reach * reach;
    }

    /** 踩在上面走不快：黏糊糊的手感主要来自这一条。 */
    @Override
    public float getSpeedFactor() {
        return STICKY_SPEED_FACTOR;
    }

    /** 踩在上面跳不高：黏得拽脚。 */
    @Override
    public float getJumpFactor() {
        return STICKY_JUMP_FACTOR;
    }

    /** 把下滑速度夹到 {@link #SLIDE_CAP}（原版是夹到 0.05），横向同比例收。 */
    private static void throttleSlide(Entity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.y < -0.13D) {
            double scale = -SLIDE_CAP / velocity.y;
            entity.setDeltaMovement(new Vec3(velocity.x * scale, -SLIDE_CAP, velocity.z * scale));
        } else {
            entity.setDeltaMovement(new Vec3(velocity.x, -SLIDE_CAP, velocity.z));
        }
        entity.resetFallDistance();
    }

    private static void slideEffects(Level level, Entity entity) {
        if (!(entity instanceof LivingEntity || entity instanceof AbstractMinecart
                || entity instanceof PrimedTnt || entity instanceof Boat)) return;
        if (level.isClientSide) {
            if (level.random.nextInt(5) == 0) showErosionParticles(entity, 4);
        } else if (level.random.nextInt(5) == 0) {
            entity.playSound(SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 0.9F);
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        // 原版这里 broadcastEntityEvent(54) → 客户端黄色碎屑；换成自喷蓝紫粒子。
        entity.playSound(SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 0.9F);
        if (level.isClientSide) showErosionParticles(entity, 8);
        if (entity.causeFallDamage(fallDistance, 0.2F, level.damageSources().fall())) {
            entity.playSound(this.soundType.getFallSound(),
                    this.soundType.getVolume() * 0.5F, this.soundType.getPitch() * 0.75F);
        }
    }

    /** 魔蚀版的黏着碎屑：蓝紫魔素尘为主、偶尔一缕魂火。 */
    public static void showErosionParticles(Entity entity, int count) {
        Level level = entity.level();
        if (!level.isClientSide) return;
        for (int i = 0; i < count; i++) {
            level.addParticle(EROSION_DUST,
                    entity.getX() + level.random.triangle(0.0D, 0.25D),
                    entity.getY() + 0.1D + level.random.nextDouble() * 0.6D,
                    entity.getZ() + level.random.triangle(0.0D, 0.25D),
                    0.0D, -0.01D, 0.0D);
            if (level.random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        entity.getX(), entity.getY() + 0.3D, entity.getZ(), 0.0D, 0.02D, 0.0D);
            }
        }
    }

    // ---------------------------------------------------------------- 触蜜减伤
    /**
     * 实体此刻有没有「接触」这块蜜砖：身体泡在蜜砖那一格里，或者正好踩在它的顶面。
     *
     * <p>只查脚下两格，不做 AABB 全扫描——受击链每一发都要问一次，必须便宜。</p>
     */
    public static boolean isTouching(Entity entity) {
        Level level = entity.level();
        BlockPos feet = entity.blockPosition();
        if (isCorruptedHoney(level, feet)) return true;
        return isCorruptedHoney(level, feet.below());
    }

    private static boolean isCorruptedHoney(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(CorruptedBeeContent.CORRUPTED_HONEY_BLOCK.get());
    }
}
