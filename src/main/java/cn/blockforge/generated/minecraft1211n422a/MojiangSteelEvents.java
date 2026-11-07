package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔精钢装备的三条特殊规则：
 *
 * <ul>
 *   <li><b>独立伤害减免</b>：在护甲、附魔、药水减伤都算完之后（{@link LivingDamageEvent.Pre}）
 *       再按穿着的件数做一次独立乘算，不占原版护甲减伤的名额。</li>
 *   <li><b>附魔等级 +1</b>：物品进背包时就把每条附魔真加一级写进物品数据（保护 IV → V），
 *       面板、铁砧、结算全部按新等级走；原版读取路径再用 {@link GetEnchantmentLevelEvent}
 *       给还没写过的物品兜底。因此原版的等级上限在这里直接失效。</li>
 *   <li><b>附魔相性</b>：附魔台给三行等级时额外提高一截，配合 40 点附魔值，
 *       更容易出现高等级、多条附魔。</li>
 * </ul>
 */
public final class MojiangSteelEvents {
    /** 附魔台每次额外抬高的等级。原版三行最高约 30，抬到 40 左右就能明显多出词条。 */
    private static final int ENCHANT_LEVEL_BONUS = 10;
    private static final int MAX_ENCHANT_LEVEL = 60;
    /** 末地城（含末地船）宝箱开出模板的概率。 */
    private static final float END_CITY_TEMPLATE_CHANCE = 0.15F;

    /**
     * 魔精钢护甲按件乘算的独立减伤系数（四件全穿 ≈ 0.6256，也就是再减 37.44%）。
     *
     * <p>参数是 {@link LivingEntity} 而不是玩家：试验假人、僵尸之类只要能穿护甲都应该吃到，
     * 不然用假人测伤害时这条减伤根本不参与结算，数字会跟下界合金一模一样。</p>
     *
     * <p>{@code /dimensional_mana combat reduction} 也用这个方法，保证界面报的数字
     * 与实战里真正乘上去的是同一份公式。</p>
     */
    public static double independentMultiplier(LivingEntity entity) {
        double multiplier = 1.0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = entity.getItemBySlot(slot);
            if (armor.getItem() instanceof MojiangSteelArmorItem mojiangArmor) {
                multiplier *= 1.0 - mojiangArmor.independentDamageReduction();
            }
        }
        return multiplier;
    }

    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        float amount = event.getNewDamage();
        // 必杀伤害（/kill 等）与 NaN / 无穷大不参与乘算，避免把伤害改成 NaN 或溢出。
        if (!Float.isFinite(amount) || amount <= 0.0F) return;
        if (LethalDamage.isLethal(event.getSource(), amount)) return;
        double multiplier = independentMultiplier(event.getEntity());
        if (multiplier >= 1.0) return;
        event.setNewDamage(amount * (float) multiplier);
    }

    /**
     * 实际结算时所有附魔等级 +1。
     *
     * <p>正常情况下这条加成已经在 {@link #applyEnchantBoost} 里写进物品数据了（面板上
     * 直接显示 +1 后的等级），所以这里只兜底处理「还没被写进去」的物品，
     * 避免同一件装备被加两次。</p>
     */
    public static void onGetEnchantmentLevel(GetEnchantmentLevelEvent event) {
        ItemStack stack = event.getStack();
        if (!isMojiangSteel(stack) || isBoosted(stack)) return;
        ItemEnchantments.Mutable enchantments = event.getEnchantments();
        for (Holder<Enchantment> enchantment : new ArrayList<>(enchantments.keySet())) {
            int level = enchantments.getLevel(enchantment);
            if (level > 0) enchantments.set(enchantment, level + 1);
        }
    }

    /**
     * 把「附魔 +1」硬写进物品本身。
     *
     * <p>原版附魔有等级上限（保护最高 IV），只在读取时 +1 的话面板永远显示 IV，
     * 玩家看不到、也验证不了。这里直接改物品的附魔组件并打一个标记：
     * 保护 IV 进背包就变成保护 V，面板、铁砧、结算全都按 V 走，
     * 天然绕过原版上限，不需要去动原版的 maxLevel。</p>
     *
     * <p>标记写在 {@code CUSTOM_DATA} 里，物品被复制/合并时会一起带过去，不会重复叠加。</p>
     */
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        // 20 刻扫一次背包足够，已经加过的物品在 isBoosted 处就返回了。
        if (player.tickCount % 20 != 0) return;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            applyEnchantBoost(inventory.getItem(slot));
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.OFFHAND}) {
            applyEnchantBoost(player.getItemBySlot(slot));
        }
    }

    /**
     * 给一件魔精钢装备的附魔 +1，并把「哪些魔咒已经加过、加到了几级」记进物品数据。
     *
     * <p>旧版本只用了一个布尔标记，于是「砂轮把附魔洗光 → 重新附魔」时标记还在，
     * {@link #isBoosted} 一直为真，+1 就再也不生效了。现在改成逐条记账：</p>
     * <ul>
     *   <li>记录里的等级和物品当前等级一致 → 这条已经加过，不动；</li>
     *   <li>物品上没有这条记录（新附上的魔咒，或砂轮洗完重新附上的）→ 这条 +1 并记账；</li>
     *   <li>附魔被洗光（整个附魔组件为空）→ 账本与标记一起清掉，重新附魔能正常再 +1；</li>
     *   <li>老存档只有布尔标记、没有账本 → 按「已经加过」迁移，不会一更新就把全服装备再 +1 一次。</li>
     * </ul>
     */
    public static void applyEnchantBoost(ItemStack stack) {
        if (!isMojiangSteel(stack)) return;
        ItemEnchantments enchantments = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        CustomData data = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (enchantments.isEmpty()) {
            // 砂轮把附魔洗掉了：账本一起清，之后重新附魔才能再吃到 +1（就是这次修的 bug）。
            if (data.contains(BOOST_TAG) || data.contains(BOOST_MAP)) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        data.update(tag -> { tag.remove(BOOST_TAG); tag.remove(BOOST_MAP); }));
            }
            return;
        }
        CompoundTag recorded = data.copyTag().getCompound(BOOST_MAP);
        boolean legacy = data.contains(BOOST_TAG) && recorded.isEmpty();
        ItemEnchantments.Mutable boosted = new ItemEnchantments.Mutable(enchantments);
        CompoundTag updated = new CompoundTag();
        boolean changed = false;
        for (Holder<Enchantment> enchantment : new ArrayList<>(enchantments.keySet())) {
            int level = enchantments.getLevel(enchantment);
            if (level <= 0) continue;
            String key = enchantment.unwrapKey().map(k -> k.location().toString()).orElse("");
            if (key.isEmpty()) continue;
            int was = recorded.getInt(key);
            int target = (!legacy && was == level) ? level : level + 1;
            if (target != level) {
                boosted.set(enchantment, target);
                changed = true;
            }
            updated.putInt(key, target);
        }
        if (changed) stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, boosted.toImmutable());
        if (!updated.isEmpty() && (!updated.equals(recorded) || !data.contains(BOOST_TAG))) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, data.update(tag -> {
                tag.remove(BOOST_MAP);
                tag.put(BOOST_MAP, updated);
                tag.putBoolean(BOOST_TAG, true);
            }));
        }
    }

    /**
     * 附魔没了就把「已 +1」的标记与账本一起清掉。
     *
     * <p>砂轮磨掉附魔（本模组的砂轮接管，或原版双槽修复）之后物品的附魔组件为空，
     * 但标记还在，于是重新附魔时 {@link #isBoosted} 一直返回 true，+1 就再也不会生效。
     * 这里把「没有附魔却还带着标记」当成脏数据清掉，随后玩家再附魔就能正常吃到 +1。</p>
     *
     * @return 这件物品当前没有附魔（已经把标记清掉或本来就是空的）时为 true
     */
    public static boolean clearBoostIfUnenchanted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!data.contains(BOOST_TAG) && !data.contains(BOOST_MAP)) return false;
        ItemEnchantments enchantments = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!enchantments.isEmpty()) return false;
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                data.update(tag -> { tag.remove(BOOST_TAG); tag.remove(BOOST_MAP); }));
        return true;
    }

    /** 这件物品的附魔是不是已经被 +1 写进去过了。 */
    public static boolean isBoosted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .contains(BOOST_TAG);
    }

    /** 物品数据里标记「附魔已 +1」的键。 */
    private static final String BOOST_TAG = "MojiangEnchantBoosted";
    /** 逐条记账：魔咒 id → 写进物品后的等级。 */
    private static final String BOOST_MAP = "MojiangEnchantLevels";

    /** 附魔台三行等级一起抬高：等级越高，出高等级附魔与多条附魔的机会越大。 */
    public static void onEnchantmentLevelSet(EnchantmentLevelSetEvent event) {
        if (!isMojiangSteel(event.getItem())) return;
        int boosted = event.getEnchantLevel() + ENCHANT_LEVEL_BONUS;
        event.setEnchantLevel(Math.min(MAX_ENCHANT_LEVEL, Math.max(1, boosted)));
    }

    /** 工具类物品的说明：附魔相性，非剑类再补一条挖掘基础速度。 */
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        if (!(item instanceof TieredItem tiered) || tiered.getTier() != MojiangSteelEquipment.TIER) return;
        event.getToolTip().add(Component.translatable("tooltip.dimensional_mana.mojing_steel_affinity")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipEnchantBonus(event);
        if (!(item instanceof MojiangSteelSwordItem)) {
            event.getToolTip().add(Component.translatable("tooltip.dimensional_mana.mojing_steel_tool_speed")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** 魔精钢工具 / 武器也补一条“附魔等级 +1”的说明。 */
    private static void tooltipEnchantBonus(ItemTooltipEvent event) {
        event.getToolTip().add(Component.translatable("tooltip.dimensional_mana.mojing_steel_enchant_bonus")
                .withStyle(ChatFormatting.GOLD));
    }

    /** 末地城与末地船的宝箱：15% 概率多开出一份魔精钢锻造模板。 */
    public static void addChestLoot(LootTableLoadEvent event) {
        if (!event.getName().getNamespace().equals("minecraft")) return;
        if (!event.getName().getPath().equals("chests/end_city_treasure")) return;
        LootPool pool = LootPool.lootPool().name("dimensional_mana_mojing_steel_template")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(END_CITY_TEMPLATE_CHANCE))
                .add(LootItem.lootTableItem(GeneratedMod.MOD_ITEMS.get(MojiangSteelEquipment.TEMPLATE).get()))
                .build();
        StructureChestLoot.addPool(event.getTable(), pool);
    }

    /**
     * 魔精钢装备“附魔等级 +1”的实际生效入口。
     *
     * <p>{@link GetEnchantmentLevelEvent} 只对走 NeoForge 附魔查询的代码生效，本模组自己读
     * 附魔等级的地方（斗气缠绕、魔素回复等）都是直接翻物品组件，所以必须统一改走这里：
     * 先读物品真实等级，再给魔精钢装备 +1。没有附魔（0 级）的仍然返回 0，不会凭空变出 1 级。</p>
     */
    public static int effectiveEnchantmentLevel(ItemStack stack, ResourceLocation enchantmentId) {
        int level = rawEnchantmentLevel(stack, enchantmentId);
        if (level <= 0) return 0;
        // 已经写进物品数据的（面板上就是 V）直接用，不能再加一次。
        if (isBoosted(stack)) return level;
        return level + (isMojiangSteel(stack) ? ENCHANT_LEVEL_STEP : 0);
    }

    /** 只读物品本身的附魔等级，不加任何修正。 */
    public static int rawEnchantmentLevel(ItemStack stack, ResourceLocation enchantmentId) {
        if (stack == null || stack.isEmpty() || enchantmentId == null) return 0;
        ItemEnchantments enchantments = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        final int[] found = {0};
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.unwrapKey().map(key -> key.location().equals(enchantmentId)).orElse(false)) {
                found[0] = Math.max(found[0], enchantments.getLevel(enchantment));
            }
        }
        return found[0];
    }

    /** 魔精钢装备把每条附魔抬高的等级。 */
    private static final int ENCHANT_LEVEL_STEP = 1;

    public static boolean isMojiangSteel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof MojiangSteelArmorItem) return true;
        if (item instanceof MojiangSteelSwordItem) return true;
        return item instanceof TieredItem tiered && tiered.getTier() == MojiangSteelEquipment.TIER;
    }

    private MojiangSteelEvents() { }
}
