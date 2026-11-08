package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 第二章以后那些「必须靠事件才知道」的进度判定。
 *
 * <p>{@link MagicAchievements} 每秒扫一次背包与饰品栏，能覆盖「拿到某件东西」类的进度；
 * 但击杀、进入某个维度、走回溯之门、法阵打中怪这些事件没有对应的原版触发器，
 * 也不该每秒拿实体列表去猜，就在这里集中接事件总线。</p>
 *
 * <p>所有「历史上累计过几次」的记录都写进玩家持久数据的 {@code DimensionalMana} 根标签，
 * 和魔素、技能、天赋放在一起，掉线重连与换维度都不会清零。</p>
 */
public final class AdvancementTriggers {
    private static final String DATA_ROOT = "DimensionalMana";
    /** 有没有真的被榨干过魔素 / 被打空过斗气。 */
    private static final String EXHAUSTED_KEY = "advancementManaExhausted";
    private static final String QI_BROKEN_KEY = "advancementQiCollapsed";
    /** 打倒过多少个处在破绽里的生物。 */
    private static final String STAGGER_KILLS_KEY = "advancementStaggerKills";
    /** 百战不殆的门槛。 */
    public static final int STAGGER_KILL_TARGET = 100;

    private static final ResourceKey<Biome> EROSION_BIOME = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "corrupted_wasteland"));

    /** 每秒调用一次（由 {@link MagicAchievements#tick} 统一调度）。 */
    public static void tick(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return;
        ManaData.init(player);
        CompoundTag data = player.getPersistentData().getCompound(DATA_ROOT);

        // 「血肉与钢铁」：魔力枯竭与斗气溃散都尝过一次，两个状态持续只有几秒，得先记下来。
        boolean changed = false;
        if (player.hasEffect(GeneratedMod.MANA_EXHAUSTION)) {
            data.putBoolean(EXHAUSTED_KEY, true);
            changed = true;
        }
        if (player.hasEffect(GeneratedMod.BATTLE_QI_COLLAPSE)) {
            data.putBoolean(QI_BROKEN_KEY, true);
            changed = true;
        }
        if (data.getBoolean(EXHAUSTED_KEY) && data.getBoolean(QI_BROKEN_KEY)) {
            MagicProgression.awardAdvancement(player, "body_and_steel");
        }

        if (changed) player.getPersistentData().put(DATA_ROOT, data);

        // 蚀魔荒原：站在群里就算到过。
        if (player.level().getBiome(player.blockPosition()).is(EROSION_BIOME)) {
            MagicProgression.awardAdvancement(player, "erosion_biome");
        }
        // 反虚空领域本身不单独设节点：那里的仪式由 {@link AntiVoidSystem#completeIntro} 判定。
        // 世界的主宰：四个护甲格全是 SSS。
        if (fullSssArmor(player)) {
            MagicProgression.awardAdvancement(player, "all_sss");
        }
    }

    /** 头盔 / 胸甲 / 护腿 / 靴子四件都带词条、评级 SSS、主词条还都喂到了 Lv.12。 */
    private static boolean fullSssArmor(Player player) {
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !EquipmentAffixSystem.hasAffix(stack)) return false;
            if (!"SSS".equals(EquipmentAffixSystem.gradeName(stack))) return false;
            if (EquipmentAffixSystem.mainLevel(stack) < EquipmentAffixSystem.MAIN_MAX_LEVEL) return false;
        }
        return true;
    }

    /**
     * 击杀类进度：哥布林、首领、剑士、魔素变异体、虚空暗影、魔蚀凋零与强化末影龙，
     * 以及「滴血不漏」（破绽窗口里用近战收尾）与「百战不殆」（累计 100 个）。
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead == null || dead.level().isClientSide) return;
        Player killer = null;
        if (event.getSource().getEntity() instanceof Player player) killer = player;
        if (!(killer instanceof ServerPlayer serverPlayer)) return;

        // 破绽里被近战打死：见切窗口默认 1 秒，用「直接点名的实体就是玩家」区分近战与弹道。
        if (dead.hasEffect(NewSpellContent.PARRY_VULNERABLE)
                && event.getSource().getDirectEntity() == serverPlayer) {
            MagicProgression.awardAdvancement(serverPlayer, "stagger_kill");
            ManaData.init(serverPlayer);
            CompoundTag data = serverPlayer.getPersistentData().getCompound(DATA_ROOT);
            int kills = data.getInt(STAGGER_KILLS_KEY) + 1;
            data.putInt(STAGGER_KILLS_KEY, kills);
            serverPlayer.getPersistentData().put(DATA_ROOT, data);
            if (kills >= STAGGER_KILL_TARGET) {
                MagicProgression.awardAdvancement(serverPlayer, "stagger_hunter");
            }
        }

        if (dead instanceof Goblin goblin) {
            MagicProgression.awardAdvancement(serverPlayer, "goblin_first");
            if (goblin.variant() == GoblinVariant.CHIEF) {
                MagicProgression.awardAdvancement(serverPlayer, "goblin_chief");
            }
        }
        if (dead instanceof WanderingSwordmaster) {
            MagicProgression.awardAdvancement(serverPlayer, "swordmaster_duel");
        }
        if (dead instanceof CrystallizedZombie || dead instanceof CorruptedSkeleton
                || dead instanceof CorruptedWeaverEntity || dead instanceof ManaCreeper) {
            MagicProgression.awardAdvancement(serverPlayer, "corrupted_mob");
        }
        if (dead instanceof WanderingWitch) {
            MagicProgression.awardAdvancement(serverPlayer, "altar_witch");
        }
        if (dead instanceof CorruptedWither) {
            MagicProgression.awardAdvancement(serverPlayer, "wither_kill");
        }
        // 魔蚀凋零的复活与「假死」不走这里：能死到这里就说明那一发是真伤。
        if (dead instanceof EnderDragon) {
            MagicProgression.awardAdvancement(serverPlayer, "dragon_empowered");
        }
    }

    /** 完美招架（弹反）成功：由 {@link BattleQiParry#perfectParry} 直接调用。 */
    public static void onPerfectParry(ServerPlayer player) {
        if (player == null) return;
        MagicProgression.awardAdvancement(player, "perfect_parry");
    }

    /**
     * 放下魔法阵投影方块：投影贴着墙面 / 天花板展开，是「墙面上的仪式」的判定口。
     */
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var holder = GeneratedMod.MOD_BLOCKS.get(MagicCircleProjectionBlock.BLOCK_ID);
        if (holder == null) return;
        if (event.getPlacedBlock().is(holder.get())) {
            MagicProgression.awardAdvancement(player, "circle_projection");
        }
    }

    private AdvancementTriggers() { }
}
