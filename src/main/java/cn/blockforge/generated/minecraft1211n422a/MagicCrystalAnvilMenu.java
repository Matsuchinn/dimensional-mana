package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 魔晶铁砧菜单。
 *
 * <p>和原版铁砧的分工：<b>结果物品</b>仍然交给原版 {@link AnvilMenu#createResult()} 去算
 * （修理、材料修复、附魔合并、重命名、词条合成全部保留），但<b>货币换成魔素</b>：</p>
 * <ul>
 *   <li>计算前把左右两槽的「累积惩罚」临时清零、并把经验临时灌满，原版就只会老老实实算物品，
 *       不会因为经验不足或“过于昂贵”把结果清空；算完再把惩罚值和经验都还原。</li>
 *   <li>真正的价格按 4.2 的公式算：
 *       {@code 基础魔素 × (1 + 0.5 × 非仅重命名操作次数)²}，再乘玩家的魔素消耗减免。
 *       重命名单列 20 点基础、不计入累积惩罚。</li>
 *   <li>结果物品的累积惩罚写回 {@code REPAIR_COST}：非重命名操作 +1，重命名保持原值。</li>
 *   <li>魔素不足时结果照常显示但标红，点它“强行合成”会失败并让玩家陷入魔素枯竭，装备原地退还。</li>
 * </ul>
 */
public final class MagicCrystalAnvilMenu extends AnvilMenu {
    /** 修复 / 合成 / 合词条的基础魔素。 */
    public static final int COMBINE_BASE_MANA = 100;
    /** 仅重命名的基础魔素（不参与累积惩罚）。 */
    public static final int RENAME_BASE_MANA = 20;
    /** 强行合成失败时施加的魔素枯竭刻数，和自然枯竭一致。 */
    private static final int FAIL_EXHAUSTION_TICKS = 260;

    private int manaCost;
    private boolean renameOnly;
    private String pendingName;
    private int lastSentCost = -1;
    private boolean lastSentAffordable;
    private boolean lastSentRename;

    public MagicCrystalAnvilMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(containerId, inventory, access);
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(GeneratedMod.MOD_BLOCKS.get("magic_crystal_anvil").get());
    }

    /** 记录输入框里的名字，用来区分“仅重命名”与真正的合成操作。 */
    @Override
    public boolean setItemName(String name) {
        this.pendingName = name;
        return super.setItemName(name);
    }

    @Override
    public void createResult() {
        ItemStack left = inputSlots.getItem(0);
        ItemStack right = inputSlots.getItem(1);
        int penalty = Math.max(penaltyOf(left), penaltyOf(right));
        boolean renaming = isRenameOnly(left, right);

        // 让原版只负责“算出物品”：惩罚清零 + 经验灌满，避免它按经验或 40 级上限拒绝。
        int leftPenalty = penaltyOf(left);
        int rightPenalty = penaltyOf(right);
        setPenalty(left, 0);
        setPenalty(right, 0);
        int savedLevel = player.experienceLevel;
        player.experienceLevel = 10_000;
        try {
            super.createResult();
        } finally {
            setPenalty(left, leftPenalty);
            setPenalty(right, rightPenalty);
            player.experienceLevel = savedLevel;
        }

        ItemStack output = resultSlots.getItem(0);
        if (output.isEmpty()) {
            // 原版这条路没算出东西时，自己再试一次词条合成（事件没触发也不至于白放）。
            ItemStack fallback = EquipmentAffixSystem.mergeForAnvil(left, right, player.getRandom());
            if (!fallback.isEmpty()) {
                resultSlots.setItem(0, fallback);
                output = fallback;
            }
        }
        if (output.isEmpty()) {
            this.manaCost = 0;
            this.renameOnly = false;
            publish();
            return;
        }
        // 结果物品的累积惩罚：只有非重命名操作才会 +1。
        output.set(DataComponents.REPAIR_COST, renaming ? penalty : penalty + 1);
        resultSlots.setItem(0, output);
        this.renameOnly = renaming;
        this.manaCost = manaCost(player, penalty, renaming);
        publish();
    }

    /** 4.2 公式：基础魔素 × (1 + 0.5 × 惩罚次数)² × (1 - 魔素消耗减免)。 */
    public static int manaCost(Player player, int penalty, boolean renameOnly) {
        double base = renameOnly ? RENAME_BASE_MANA : COMBINE_BASE_MANA;
        double raw = base * Math.pow(1.0 + 0.5 * Math.max(0, penalty), 2.0);
        if (player == null) return (int)Math.max(1, Math.round(raw));
        return (int)Math.max(1, Math.round(SpellAttributes.reducedManaCost(player, raw)));
    }

    /** 仅重命名：右槽空着、而且名字确实被改过。 */
    private boolean isRenameOnly(ItemStack left, ItemStack right) {
        if (left.isEmpty() || !right.isEmpty()) return false;
        String name = pendingName;
        if (name == null || name.isBlank()) return false;
        return !name.equals(left.getHoverName().getString());
    }

    private static int penaltyOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return Math.max(0, stack.getOrDefault(DataComponents.REPAIR_COST, 0));
    }

    private static void setPenalty(ItemStack stack, int value) {
        if (stack.isEmpty()) return;
        if (value <= 0) stack.remove(DataComponents.REPAIR_COST);
        else stack.set(DataComponents.REPAIR_COST, value);
    }

    private double availableMana(Player player) {
        return ManaData.get(player).mana();
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        if (!hasStack || manaCost <= 0) return false;
        return player.getAbilities().instabuild || availableMana(player) >= manaCost;
    }

    /** 魔素不够时点结果格 = 强行合成：合成失败、装备原地退还、玩家进入魔素枯竭。 */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == RESULT_SLOT && manaCost > 0 && !resultSlots.getItem(0).isEmpty()
                && !player.getAbilities().instabuild && availableMana(player) < manaCost) {
            reject(player);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void reject(Player player) {
        if (player.level().isClientSide) return;
        ManaData.setExhaustion(player, FAIL_EXHAUSTION_TICKS);
        player.addEffect(new MobEffectInstance(GeneratedMod.MANA_EXHAUSTION, FAIL_EXHAUSTION_TICKS, 0, false, true));
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magic_crystal_anvil.no_mana", manaCost), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS,
                0.7F, 0.55F);
        if (player instanceof ServerPlayer serverPlayer) sendMana(serverPlayer);
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        // 进度「词条锻造师」：真的在铁砧上完成一次修理 / 合并 / 改名，取走结果才算。
        // 「铁砧也讲规矩」只管把铁砧做出来，两条不再靠同一个条件点亮。
        if (player instanceof ServerPlayer smithPlayer && !stack.isEmpty()) {
            MagicProgression.awardAdvancement(smithPlayer, "affix_forge");
        }
        // 进度「融合与继承」：两件都带词条的同类装备并成了一件，取走才算。
        if (player instanceof ServerPlayer mergePlayer && !stack.isEmpty()
                && EquipmentAffixSystem.hasAffix(stack)
                && EquipmentAffixSystem.hasAffix(getSlot(0).getItem())
                && EquipmentAffixSystem.hasAffix(getSlot(1).getItem())) {
            MagicProgression.awardAdvancement(mergePlayer, "affix_merge");
        }
        // 原版会照经验价格扣等级；这里把三件经验数据原样备份，调用完立刻还原。
        float progress = player.experienceProgress;
        int level = player.experienceLevel;
        int total = player.totalExperience;
        int cost = manaCost;
        boolean renaming = renameOnly;
        super.onTake(player, stack);
        // 原版在 super 里扣过经验等级了，这里连经验条和总经验一起还原，并给客户端补一次同步包。
        player.experienceLevel = level;
        player.experienceProgress = progress;
        player.totalExperience = total;
        if (player instanceof ServerPlayer experiencePlayer) {
            experiencePlayer.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(progress, total, level));
        }
        if (!player.getAbilities().instabuild && cost > 0) {
            ManaData.setMana(player, Math.max(0.0, availableMana(player) - cost), true);
            if (player instanceof ServerPlayer serverPlayer) sendMana(serverPlayer);
        }
        player.level().playSound(null, player.blockPosition(),
                renaming ? SoundEvents.ENCHANTMENT_TABLE_USE : GeneratedMod.MAGIC_CRYSTAL_ANVIL_PLACE.get(),
                SoundSource.BLOCKS, 0.6F, renaming ? 1.4F : 1.15F);
        this.manaCost = 0;
        publish();
    }

    private static void sendMana(ServerPlayer player) {
        ManaData.Snapshot mana = ManaData.get(player);
        PacketDistributor.sendToPlayer(player, new ManaSyncPayload(mana.mana(), mana.maxMana(),
                mana.battleQi(), mana.maxBattleQi(), mana.exhaustionTicks() > 0,
                ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
    }

    /** 价格/是否付得起有变化时才往客户端推，避免每次槽位变动都发包。 */
    private void publish() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        boolean affordable = player.getAbilities().instabuild || availableMana(player) >= manaCost;
        if (manaCost == lastSentCost && affordable == lastSentAffordable && renameOnly == lastSentRename) return;
        lastSentCost = manaCost;
        lastSentAffordable = affordable;
        lastSentRename = renameOnly;
        PacketDistributor.sendToPlayer(serverPlayer, new AnvilManaPayload(manaCost, affordable, renameOnly));
    }
}
