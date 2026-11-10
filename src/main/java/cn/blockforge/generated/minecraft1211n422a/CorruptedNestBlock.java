package cn.blockforge.generated.minecraft1211n422a;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.BeeReleaseStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * 秽蚀蜂巢：挂在花气充沛的林子里的紫黑蜂房。
 *
 * <p>整套原版蜂箱机制直接继承：装蜂、酿蜜（蜜蜂带花粉回巢涨蜜度）、
 * 剪蜜脾、被烟熏后安静取蜜、破坏时放蜂，全部照旧。只改两件事——
 * 蜜度满时拿玻璃瓶右键，出来的不是原版蜂蜜瓶，而是<b>魔蚀蜂蜜</b>；
 * 拿剪刀右键，掉的是<b>魔蚀蜜脾</b>而不是原版蜜脾（{@link #harvestWithShears}
 * 与玩家制作的 {@link CorruptedHiveBlock 秽蚀蜂箱} 共用同一份逻辑）。</p>
 */
public class CorruptedNestBlock extends BeehiveBlock {
    public CorruptedNestBlock(Properties properties) {
        super(properties);
    }

    /**
     * 蜜满时剪脾的公共流程：音效、掉魔蚀蜜脾、剪刀耐久、惊蜂判定，全部照原版剪刀分支抄，
     * 只把产出物换成模组自己的蜜脾。没触发（不是剪刀 / 蜜度不够）时返回 {@code null}，
     * 调用方继续走别的交互。
     */
    static ItemInteractionResult harvestWithShears(BeehiveBlock hive, ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand, Item honeycomb) {
        if (state.getValue(HONEY_LEVEL) != MAX_HONEY_LEVELS || !stack.canPerformAction(ItemAbilities.SHEARS_HARVEST)) {
            return null;
        }
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            Block.popResource(level, pos, new ItemStack(honeycomb));
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            level.gameEvent(player, GameEvent.SHEAR, pos);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            if (!CampfireBlock.isSmokeyPos(level, pos)) {
                // 没烟镇场：剪脾等于抄家，巢里的蜂emergency放出来，林子里的野蜂也被惊动。
                if (CorruptedBeeContent.nestContainsBees(level, pos)) {
                    angerNearbyBees(level, pos);
                }
                hive.releaseBeesAndResetHoneyLevel(level, state, pos, player, BeeReleaseStatus.EMERGENCY);
            } else {
                hive.resetHoneyLevel(level, state, pos);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult shears = harvestWithShears(this, stack, state, level, pos, player, hand,
                CorruptedBeeContent.CORRUPTED_HONEYCOMB.get());
        if (shears != null) return shears;
        // 复制原版蜂箱的装瓶流程，只把产出物换成魔蚀蜂蜜。
        if (stack.is(Items.GLASS_BOTTLE) && state.getValue(HONEY_LEVEL) == MAX_HONEY_LEVELS) {
            if (!level.isClientSide) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    ItemStack product = new ItemStack(CorruptedBeeContent.CORRUPTED_HONEY.get());
                    if (stack.isEmpty()) {
                        player.setItemInHand(hand, product);
                    } else if (!player.getInventory().add(product)) {
                        player.drop(product, false);
                    }
                }
                // 音调压得比原版低半档，装瓶声发闷，像灌进一罐稠黑的糖浆。
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
                if (!CampfireBlock.isSmokeyPos(level, pos)) {
                    // 没烟镇场：巢里的蜂emergency放出来，林子里的野蜂也被惊动，一起记仇。
                    if (CorruptedBeeContent.nestContainsBees(level, pos)) {
                        angerNearbyBees(level, pos);
                    }
                    releaseBeesAndResetHoneyLevel(level, state, pos, player, BeeReleaseStatus.EMERGENCY);
                } else {
                    // 魂营火的烟罩着：蜂被镇在巢里继续干活，只重置蜜度。
                    resetHoneyLevel(level, state, pos);
                }
                level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
                player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** 原版 {@code BeehiveBlock.angerNearbyBees} 是私有方法，这里按同样范围重写一份。 */
    static void angerNearbyBees(Level level, BlockPos pos) {
        List<net.minecraft.world.entity.animal.Bee> bees =
                level.getEntitiesOfClass(net.minecraft.world.entity.animal.Bee.class,
                        new AABB(pos).inflate(32.0D, 16.0D, 32.0D));
        if (bees.isEmpty()) return;
        List<Player> players = level.getEntitiesOfClass(Player.class,
                new AABB(pos).inflate(32.0D, 16.0D, 32.0D));
        if (players.isEmpty()) return;
        for (net.minecraft.world.entity.animal.Bee bee : bees) {
            if (bee.getTarget() == null) {
                bee.setTarget(players.get(level.random.nextInt(players.size())));
            }
        }
    }

    // ---------------------------------------------------------------- 蜜满的魔蚀蜜：发光 + 蓝紫蜜滴

    /** 蜜满时蜂房里的魔蚀蜜会发光：亮度 7（紫水晶簇是 5，足够照亮蜂房跟前一小块）。 */
    public static final int FULL_HONEY_LIGHT = 7;

    /** 蓝紫色的魔蚀蜜滴。大小 1.3 比魔素尘更「稠」，落下来像一滴一滴的蜜。 */
    private static final DustParticleOptions HONEY_DRIP =
            new DustParticleOptions(new org.joml.Vector3f(0.45F, 0.30F, 1.0F), 1.3F);

    /**
     * 注册方块时挂到 {@code Properties.lightLevel} 上：只有蜜满（honey_level=5）才发亮 7。
     *
     * <p>亮度写在方块状态属性里而不是每刻算，光照引擎只在这种状态被放下/换掉时更新一次，
     * 不产生任何持续开销；蜜被收走、蜜度被重置，方块回到 0 亮度，世界光照跟着重算。</p>
     */
    public static int honeyLight(BlockState state) {
        return state.getValue(HONEY_LEVEL) >= MAX_HONEY_LEVELS ? FULL_HONEY_LIGHT : 0;
    }

    /**
     * 蜜满时顺着正面往下滴的蜜：原版 {@link BeehiveBlock#animateTick} 喷的是琥珀色的
     * {@code DRIPPING_HONEY / FALLING_HONEY}，在这里整段换成蓝紫色的魔蚀蜜滴，
     * 偶尔再带一点魂火的冷光，和贴图、亮度 7 对得上。
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HONEY_LEVEL) < MAX_HONEY_LEVELS || random.nextInt(3) != 0) return;
        Direction facing = state.getValue(FACING);
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        // 沿正面那一片随机取点，靠下沿的位置滴得最多——和原版「蜜从下沿往下淌」一致。
        double x = pos.getX() + 0.5D + (alongX ? facing.getStepX() * 0.5D
                : (random.nextDouble() - 0.5D) * 0.8D);
        double z = pos.getZ() + 0.5D + (alongX ? (random.nextDouble() - 0.5D) * 0.8D
                : facing.getStepZ() * 0.5D);
        double y = pos.getY() + 0.1D + random.nextDouble() * 0.8D;
        level.addParticle(HONEY_DRIP, x, y, z, 0.0D, -0.012D - random.nextDouble() * 0.02D, 0.0D);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, 0.0D, -0.008D, 0.0D);
        }
    }
}
