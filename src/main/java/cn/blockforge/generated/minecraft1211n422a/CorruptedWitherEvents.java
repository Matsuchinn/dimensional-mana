package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 魔蚀凋零的全部事件接线：
 *
 * <ul>
 *   <li><b>受击链路</b>：光易伤 / 全属性减伤 → 虚无暴食的魔法、物理吸收 → 2% 限伤
 *       → 二阶段分裂幻影；</li>
 *   <li><b>禁疗</b>：【侵蚀凋零】命中的 10 秒内目标无法被治疗；</li>
 *   <li><b>召唤仪式</b>：三颗魔蚀凋零骷髅头 + 灵魂沙 + 特级魔晶块的 T 字形结构；</li>
 *   <li><b>腐化雨防护</b>：雨衣或带奥术镀层的装备可以免疫魔蚀叠加。</li>
 * </ul>
 */
public final class CorruptedWitherEvents {
    /** 治疗封印在实体持久化数据里的键。 */
    private static final String HEAL_SEAL_KEY = "CorruptedWitherHealSeal";
    /** 召唤阵原料。 */
    private static final String CRYSTAL_BLOCK = "special_mana_crystal_block";

    private CorruptedWitherEvents() { }

    // ---------------------------------------------------------------- 受击：抗性 / 吸收 / 限伤
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // 先处理「自己人」：本体射出的原版凋零头爆炸 / 弹体命中，不能伤到自己的幻影和聚合体。
        if (protectOwnSummons(event)) return;
        if (!(event.getEntity() instanceof CorruptedWither wither)) return;
        if (wither.level().isClientSide) return;
        // 必杀伤害不参与任何乘区。
        if (LethalDamage.isLethal(event.getSource(), event.getAmount())) return;
        float amount = LethalDamage.finite(event.getAmount());
        if (amount <= 0.0F) return;
        DamageSource source = event.getSource();

        // 1) 元素 / 全属性抗性：【光】易伤，其余（含物理、远程、爆炸）统一减伤。
        boolean light = CombatElement.elementOf(source) == CombatElement.LIGHT;
        amount *= (float)(light
                ? (wither.isPhaseTwo() ? CorruptedWither.PHASE2_LIGHT_VULNERABILITY : CorruptedWither.PHASE1_LIGHT_VULNERABILITY)
                : (wither.isPhaseTwo() ? CorruptedWither.PHASE2_RESIST : CorruptedWither.PHASE1_RESIST));

        // 2) 虚无暴食：魔法 / 物理各再减 25%，代价是聚合体存伤害（满了这次减伤就失效）。
        boolean magic = isMagicDamage(source);
        boolean physical = CombatElement.kindOf(source) == CombatElement.Kind.PHYSICAL;
        if (magic && !physical) {
            float share = amount * (float)CorruptedWither.ABSORB_REDUCTION;
            float stored = wither.absorb(true, share);
            if (stored > 0.0F) amount -= stored;
        } else if (physical) {
            float share = amount * (float)CorruptedWither.ABSORB_REDUCTION;
            float stored = wither.absorb(false, share);
            if (stored > 0.0F) amount -= stored;
        }

        // 3) 【魔法凋零之心】：不在冷却里时，这一下最多掉 2% 最大生命。
        if (wither.damageCapReady()) {
            amount = Math.min(amount, CorruptedWither.DAMAGE_CAP);
            wither.triggerDamageCapCooldown();
        }

        event.setAmount(Math.max(0.0F, amount));

        // 4) 二阶段：挨打就分出一道幻影，并且让分身一出生就正对「打它的那个人」。
        wither.trySpawnPhantom(source.getEntity());
    }

    /**
     * 保护本体自己的召唤物（幻影 / 聚合体）以及本体自己。
     *
     * <p>普通攻击现在会额外甩出 1～3 颗原版凋零头，分身也会每 1.25 秒甩一颗，
     * 它们的爆炸是原版 {@code Level.explode}，会无差别扫到旁边的幻影和本体。
     * 这里只在「伤害来源是同一只本体（或它的分身）拥有的 {@link WitherSkull}」时取消，
     * 别的生物（包括真正的敌人）射来的骷髅头照常生效。</p>
     *
     * @return true 表示这一下已经取消，调用方不要再往下走。
     */
    private static boolean protectOwnSummons(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return false;
        CorruptedWither owner;
        if (victim instanceof CorruptedWither wither) {
            owner = wither;
        } else if (victim instanceof CorruptedWitherPhantom phantom) {
            owner = phantom.owner();
        } else {
            // 聚合体不是 LivingEntity，本来就收不到受击事件。
            return false;
        }
        if (owner == null) return false;
        DamageSource source = event.getSource();
        if (ownedBy(source.getDirectEntity(), owner) || ownedBy(source.getEntity(), owner)) {
            event.setCanceled(true);
            return true;
        }
        return false;
    }

    /** 这颗骷髅头是不是这只本体 / 它的分身打出来的。 */
    private static boolean ownedBy(Entity entity, CorruptedWither owner) {
        if (!(entity instanceof WitherSkull skull)) return false;
        Entity shooter = skull.getOwner();
        if (shooter == owner) return true;
        return shooter instanceof CorruptedWitherPhantom phantom && phantom.isOwnedBy(owner);
    }

    /** 这次伤害算不算「魔法伤害」：本模组的元素法术、原版 magic / 凋零都算。 */
    private static boolean isMagicDamage(DamageSource source) {
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return true;
        }
        CombatElement element = CombatElement.elementOf(source);
        return element != null;
    }

    // ---------------------------------------------------------------- 禁疗封印
    /** 让目标在 {@code ticks} 刻内无法被治疗（【侵蚀凋零】的真实伤害状态）。 */
    public static void sealHealing(LivingEntity target, int ticks) {
        if (target == null || target.level().isClientSide) return;
        target.getPersistentData().putLong(HEAL_SEAL_KEY, target.level().getGameTime() + Math.max(1, ticks));
    }

    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        long until = entity.getPersistentData().getLong(HEAL_SEAL_KEY);
        if (until > entity.level().getGameTime()) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- 击杀回魔 / 清理
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!dead.level().isClientSide) dead.getPersistentData().remove(HEAL_SEAL_KEY);
        if (event.getSource().getEntity() instanceof CorruptedWither wither && dead != wither) {
            wither.onKillManaRestore();
        }
    }

    // ---------------------------------------------------------------- 腐化雨防护
    /** 雨衣，或者任意一件带奥术镀层的装备，都算「有防护」。 */
    public static boolean protectedFromCorruptionRain(LivingEntity entity) {
        if (RainCoatItem.worn(entity)) return true;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (ElementPlating.hasElement(stack, MagicEquipment.ARCANE)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 召唤仪式
    /**
     * 方块的摆放事件：只要摆的是本模组的魔蚀骷髅头、灵魂沙或特级魔晶块，
     * 就以落点为中心找一次召唤阵。
     */
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState placed = event.getPlacedBlock();
        if (!isRitualBlock(placed)) return;
        // 先按当前世界状态试一次；这个事件有可能在方块真正写进世界之前发出，
        // 所以再排一次下一刻的重查（PENDING），两种情况都覆盖。
        trySummon(level, event.getPos());
        PENDING.add(new PendingCheck(level.dimension(), event.getPos().immutable(), level.getGameTime() + 4));
    }

    /** 待重查的落点：方块摆放事件与真正写进世界可能差一刻。 */
    private record PendingCheck(ResourceKey<Level> dimension, BlockPos pos, long expireAt) { }

    private static final List<PendingCheck> PENDING = new ArrayList<>();

    /** 每个世界刻把还没查完的落点补查一次，最多留 4 刻。 */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (PENDING.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        PENDING.removeIf(check -> check.expireAt() < now);
        for (Iterator<PendingCheck> iterator = PENDING.iterator(); iterator.hasNext(); ) {
            PendingCheck check = iterator.next();
            if (!check.dimension().equals(level.dimension())) continue;
            iterator.remove();
            trySummon(level, check.pos());
        }
    }

    private static boolean isRitualBlock(BlockState state) {
        if (state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) return true;
        if (state.is(GeneratedMod.CORRUPTED_WITHER_SKULL_BLOCK.get())) return true;
        var crystal = GeneratedMod.MOD_BLOCKS.get(CRYSTAL_BLOCK);
        return crystal != null && state.is(crystal.get());
    }

    /**
     * 右键阵里的任意一块：再查一次阵型。
     *
     * <p>给「摆好了但没反应」的情况留一条出路——用指令或结构方块搭的阵、
     * 或者摆的顺序比较奇怪时，右键一下就能重新判定。</p>
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        trySummon(level, event.getPos());
    }

    /** 一个完整的阵：要拆掉的方块（三颗头 + 基座）和 Boss 出生点。 */
    private record Ritual(List<BlockPos> parts, BlockPos spawn) { }

    /** 在落点周围 2 格内找阵型；找到就召唤。 */
    private static void trySummon(ServerLevel level, BlockPos origin) {
        Ritual ritual = findRitual(level, origin);
        if (ritual != null) summon(level, ritual);
    }

    private static Ritual findRitual(ServerLevel level, BlockPos origin) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos middle = origin.offset(dx, dy, dz);
                    for (int axis = 0; axis < 2; axis++) {
                        BlockPos left = axis == 0 ? middle.west() : middle.north();
                        BlockPos right = axis == 0 ? middle.east() : middle.south();
                        Ritual ritual = matchRitual(level, left, middle, right);
                        if (ritual != null) return ritual;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 阵型：三颗魔蚀头摆成一排，脚下是原版凋零的 T 字基座——
     *
     * <pre>
     *   [魔蚀头] [魔蚀头] [魔蚀头]
     *   [灵魂沙] [基座] [灵魂沙]
     *              ↑ 前后再补 1 块灵魂沙
     * </pre>
     *
     * <p>要求：三块基座里<b>恰好 1 块是特级魔晶块</b>（另外至少 1 块是灵魂沙 / 灵魂土），
     * 而且这块魔晶块要么正好在中间那颗头的下面，要么是 T 字多出来的那一块——
     * 玩家按「中间的灵魂沙换成魔晶块」怎么理解都算数。</p>
     *
     * <p>多出来的那块灵魂沙：<b>平铺</b>在基座前后（原版摆法）或者<b>竖叠</b>在基座正下方
     * （老版本说明里的摆法）都认。</p>
     */
    private static Ritual matchRitual(ServerLevel level, BlockPos left, BlockPos middle, BlockPos right) {
        if (!isSkull(level, left) || !isSkull(level, middle) || !isSkull(level, right)) return null;
        BlockPos underLeft = left.below();
        BlockPos underMiddle = middle.below();
        BlockPos underRight = right.below();
        if (!isRitualBase(level, underLeft) || !isRitualBase(level, underRight)) return null;

        // 基座那一格、它的正下方、前后两格（左右两格是骷髅脚下的灵魂沙，不参与）。
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(underMiddle);
        candidates.add(underMiddle.below());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = underMiddle.relative(direction);
            if (pos.equals(underLeft) || pos.equals(underRight)) continue;
            candidates.add(pos);
        }

        BlockPos crystal = null;
        BlockPos extraBase = null;
        for (BlockPos pos : candidates) {
            if (isCrystal(level, pos)) {
                if (crystal != null) return null;   // 两块魔晶块：不算召唤阵
                crystal = pos;
            } else if (extraBase == null && isRitualBase(level, pos)) {
                extraBase = pos;
            }
        }
        if (crystal == null || extraBase == null) return null;

        List<BlockPos> parts = new ArrayList<>(List.of(left, middle, right, underLeft, underRight));
        for (BlockPos pos : candidates) {
            if (isCrystal(level, pos) || isRitualBase(level, pos)) parts.add(pos);
        }
        return new Ritual(parts, underMiddle);
    }

    /** 灵魂沙和灵魂土都算数：两个方块长得几乎一样，新手很容易拿错。 */
    private static boolean isRitualBase(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    private static boolean isCrystal(ServerLevel level, BlockPos pos) {
        var crystal = GeneratedMod.MOD_BLOCKS.get(CRYSTAL_BLOCK);
        return crystal != null && level.getBlockState(pos).is(crystal.get());
    }

    private static boolean isSkull(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(GeneratedMod.CORRUPTED_WITHER_SKULL_BLOCK.get());
    }

    /** 拆掉阵型、召唤本体、压暗天空。 */
    private static void summon(ServerLevel level, Ritual ritual) {
        BlockPos spawn = ritual.spawn();
        for (BlockPos pos : ritual.parts()) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        CorruptedWither wither = GeneratedMod.CORRUPTED_WITHER.get().create(level);
        if (wither == null) return;
        wither.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        wither.setHealth(wither.getMaxHealth());
        wither.setInvulnerableTicks(220);
        wither.beginCorruptedRain();
        level.addFreshEntity(wither);
        // 进度「深渊的狂宴」：召唤仪式成功，记在离祭坛最近的那位玩家头上。
        if (level.getNearestPlayer(spawn.getX(), spawn.getY(), spawn.getZ(), 48.0D, false)
                instanceof ServerPlayer summoner) {
            MagicProgression.awardAdvancement(summoner, "wither_summon");
        }
        level.playSound(null, spawn.getX(), spawn.getY(), spawn.getZ(),
                CorruptedWitherSounds.get("spawn"), SoundSource.HOSTILE, 4.0F, 1.0F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 2, 0.6D, 0.8D, 0.6D, 0.0D);
    }

    // ---------------------------------------------------------------- 物品提示
    /**
     * 魔蚀骷髅头是方块物品，名字走 {@code block.dimensional_mana.*}，
     * 提示行得自己补上去，不然说明文字不会显示。
     */
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(GeneratedMod.CORRUPTED_WITHER_SKULL_BLOCK.get().asItem())) return;
        event.getToolTip().add(Component.translatable("tooltip.dimensional_mana.corrupted_wither_skull")
                .withStyle(ChatFormatting.GRAY));
    }

    /** 方便调试：这个生物是不是魔蚀凋零（含幻影）。
     *
     * <p>留着给 {@link CorruptedWither} 的索敌与将来的指令/成就判断共用。</p>
     */
    public static boolean isCorruptedWitherFamily(Entity entity) {
        return entity instanceof CorruptedWither || entity instanceof CorruptedWitherPhantom;
    }
}
