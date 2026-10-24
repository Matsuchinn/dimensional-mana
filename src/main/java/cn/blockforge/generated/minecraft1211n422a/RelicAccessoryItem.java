package cn.blockforge.generated.minecraft1211n422a;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Map;

/**
 * 新饰品的通用物品实现：一件物品等于一条 {@link Relics.Relic} 定义。
 *
 * <p>名字按梯度上色（t0 金色加粗、t1 紫色加粗、t2 青色、t3 绿色），提示框先写梯度与栏位，
 * 再写效果行，最后一行写获取途径。物品本身不带逻辑，效果统一在 {@link RelicEffects} 里结算。</p>
 */
public final class RelicAccessoryItem extends Item implements ICurioItem {
    /** 每件饰品的效果行数（提示框里的第 1～n 行）；写在代码里就不必查语言文件是否存在。 */
    private static final Map<String, Integer> LINE_COUNTS = Map.ofEntries(
            Map.entry("green_amber_ring", 1),
            Map.entry("smuggler_ring", 2),
            Map.entry("venom_ring", 1),
            Map.entry("strength_charm", 2),
            Map.entry("moonlight_blessing", 2),
            Map.entry("blazing_blessing", 2),
            Map.entry("risk_bracelet", 2),
            Map.entry("choice_ring", 3),
            Map.entry("nameless_charm", 2),
            Map.entry("lost_charm", 1),
            Map.entry("bloodthirst_brooch", 1),
            Map.entry("serene_bracelet", 2),
            Map.entry("seraphis_charm", 2),
            Map.entry("cosetas_charm", 1),
            Map.entry("aurora_charm", 2),
            Map.entry("seal_blindfold", 1),
            Map.entry("mage_delight", 2),
            Map.entry("element_absorption_charm", 1),
            Map.entry("detective_charm", 1),
            Map.entry("universal_belt", 1),
            Map.entry("scarlet_spirit_charm", 2),
            Map.entry("angel_crown", 4),
            Map.entry("omniscient_shard", 2));

    private final Relics.Relic relic;

    public RelicAccessoryItem(Properties properties, Relics.Relic relic) {
        super(properties);
        this.relic = relic;
        CuriosApi.registerCurio(this, this);
    }

    public Relics.Relic relic() { return relic; }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return relic.slot().id().equals(slotContext.identifier())
                || RelicEffects.UNIVERSAL_SLOT.equals(slotContext.identifier());
    }

    /** 脱下天使王冠时收回它累计的最大生命加成，避免靠换装保留白嫖。 */
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (RelicEffects.ANGEL_CROWN.equals(relic.id())
                && slotContext.entity() instanceof Player player && !player.level().isClientSide) {
            RelicEffects.clearAngelBonus(player);
        }
    }

    /**
     * 面板加成走 Curios 的真实属性修正：装备、卸下、状态页显示都会自动同步。
     * 万用腰带额外把通用饰品栏加 1 格，这是 Curios 的「栏位修正」。
     */
    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create(
                ICurioItem.super.getAttributeModifiers(slotContext, id, stack));
        if (!canEquip(slotContext, stack)) return modifiers;
        for (Relics.AttributeSpec spec : relic.attributes()) {
            modifiers.put(spec.attribute(), new AttributeModifier(id, spec.amount(), spec.operation()));
        }
        if (RelicEffects.UNIVERSAL_BELT.equals(relic.id())) {
            CuriosApi.addSlotModifier(modifiers, RelicEffects.UNIVERSAL_SLOT, id, 1.0,
                    AttributeModifier.Operation.ADD_VALUE);
        }
        return modifiers;
    }

    @Override
    public Component getName(ItemStack stack) {
        ChatFormatting color = relic.tier().color();
        if (relic.tier().bold()) return Component.translatable(relic.nameKey()).withStyle(color, ChatFormatting.BOLD);
        return Component.translatable(relic.nameKey()).withStyle(color);
    }

    /** 抉择戒指：拿在手上右键切换形态，切换结果存在玩家自己的持久数据里。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!RelicEffects.CHOICE_RING.equals(relic.id())) return InteractionResultHolder.pass(held);
        if (!level.isClientSide) {
            boolean aggressive = RelicEffects.toggleChoiceMode(held);
            player.displayClientMessage(Component.translatable(aggressive
                    ? "message.dimensional_mana.relic.choice_ring.aggressive"
                    : "message.dimensional_mana.relic.choice_ring.defensive"), true);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT,
                    SoundSource.PLAYERS, 0.7F, aggressive ? 1.45F : 1.05F);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana.relic.header",
                        Component.translatable("tooltip.dimensional_mana.relic.slot." + relic.slot().id()))
                .withStyle(relic.tier().color()));
        int lines = LINE_COUNTS.getOrDefault(relic.id(), 1);
        for (int index = 1; index <= lines; index++) {
            tooltip.add(Component.translatable(relic.lineKey(index)).withStyle(ChatFormatting.GRAY));
        }
        if (RelicEffects.CHOICE_RING.equals(relic.id()) && context.registries() != null) {
            tooltip.add(Component.translatable("message.dimensional_mana.relic.choice_ring.current",
                            Component.translatable(RelicEffects.choiceAggressive(stack)
                                    ? "message.dimensional_mana.relic.choice_ring.aggressive_name"
                                    : "message.dimensional_mana.relic.choice_ring.defensive_name"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable(relic.obtainKey()).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 提示框里显示当前形态要读玩家数据；物品本身没有玩家上下文，所以按最近一次写入的标记走。 */
    public static boolean equippedBy(LivingEntity entity, String itemId) {
        return RelicEffects.worn(entity, itemId);
    }
}
