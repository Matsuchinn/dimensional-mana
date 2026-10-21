package cn.blockforge.generated.minecraft1211n422a;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 两副施法手套，装备在 Curios 的手饰（hands）栏位。
 *
 * <p><b>迅捷施法手套</b>：施法速度 +8%（真实属性修正）、法术冷却 -15%（在
 * {@link SpellCasting#startCooldown} 里按乘区结算）。</p>
 *
 * <p><b>大师施法手套</b>：法术暴击率 +10%；并且每施法 4 次之后，第 5 次施法的伤害必定暴击。
 * 计数写在玩家自己的持久数据里（跟着玩家走、换世界也还在），客户端的物品描述通过
 * {@link MagicToggleSyncPayload} 拿到当前计数，所以物品栏里能直接看到“已施法几次”。</p>
 */
public final class CastingGloveItem extends Item implements ICurioItem {
    /** 迅捷施法手套：法术冷却 -15%。 */
    public static final double SWIFT_COOLDOWN_REDUCTION = 0.15;
    /** 每 5 次施法为一个小循环：第 5 次必定暴击，然后计数清零。 */
    public static final int CASTS_PER_GUARANTEE = 5;

    private static final String CAST_COUNT_KEY = "DimensionalManaMasterGloveCasts";
    private static final String GUARANTEED_KEY = "DimensionalManaMasterGloveGuaranteed";

    public enum Kind {
        /** 迅捷施法手套：施法速度 +8%，法术冷却 -15%。 */
        SWIFT("swift_casting_gloves", 0.08, 0.0),
        /** 大师施法手套：法术暴击率 +10%，第 5 次施法必定暴击。 */
        MASTER("master_casting_gloves", 0.0, 0.10);

        private final String itemId;
        private final double spellHaste;
        private final double critChance;

        Kind(String itemId, double spellHaste, double critChance) {
            this.itemId = itemId;
            this.spellHaste = spellHaste;
            this.critChance = critChance;
        }

        public String itemId() { return itemId; }
    }

    private final Kind kind;

    public CastingGloveItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        CuriosApi.registerCurio(this, this);
    }

    public Kind kind() { return kind; }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return MagicEquipment.HANDS_SLOT.equals(slotContext.identifier());
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create(
                ICurioItem.super.getAttributeModifiers(slotContext, id, stack));
        if (!MagicEquipment.HANDS_SLOT.equals(slotContext.identifier())) return modifiers;
        if (kind.spellHaste > 0) modifiers.put(SpellAttributes.SPELL_HASTE,
                new AttributeModifier(id, kind.spellHaste, AttributeModifier.Operation.ADD_VALUE));
        if (kind.critChance > 0) modifiers.put(SpellAttributes.SPELL_CRIT_CHANCE,
                new AttributeModifier(id, kind.critChance, AttributeModifier.Operation.ADD_VALUE));
        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana." + kind.itemId() + ".line1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana." + kind.itemId() + ".line2")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (kind == Kind.MASTER) {
            // 计数由服务器同步过来；物品描述里直接写“已经施法几次”，攒满 4 次就提示下一次必定暴击。
            int casts = ClientMagicToggleState.casts();
            if (casts >= CASTS_PER_GUARANTEE - 1) {
                tooltip.add(Component.translatable("tooltip.dimensional_mana.master_casting_gloves.ready",
                        casts, CASTS_PER_GUARANTEE - 1).withStyle(ChatFormatting.GOLD));
            } else {
                tooltip.add(Component.translatable("tooltip.dimensional_mana.master_casting_gloves.progress",
                        casts, CASTS_PER_GUARANTEE - 1).withStyle(ChatFormatting.YELLOW));
            }
        }
        tooltip.add(Component.translatable("tooltip.dimensional_mana.gloves.slots")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 是否戴着指定种类的手套。 */
    public static boolean wearing(LivingEntity entity, Kind kind) {
        return MagicEquipment.equippedCount(entity, kind.itemId(), MagicEquipment.HANDS_SLOT) > 0;
    }

    /**
     * 一次成功的施法开始后调用：戴着大师施法手套时推进计数。
     * 第 5 次施法会把“必定暴击”标记挂到玩家身上，等真正掷暴击时消费掉。
     */
    public static void onCast(ServerPlayer player) {
        if (player == null || !wearing(player, Kind.MASTER)) return;
        var data = player.getPersistentData();
        int casts = Math.max(0, Math.min(CASTS_PER_GUARANTEE - 1, data.getInt(CAST_COUNT_KEY)));
        boolean guaranteed = casts >= CASTS_PER_GUARANTEE - 1;
        data.putInt(CAST_COUNT_KEY, guaranteed ? 0 : casts + 1);
        // 上一次的“必定暴击”如果还没被伤害消费掉（比如第 5 次施放的是增益法术），就继续留着。
        if (guaranteed) data.putBoolean(GUARANTEED_KEY, true);
        MagicToggleSyncPayload.send(player);
    }

    /** 当前已经攒了几次施法（0 ~ 4）。 */
    public static int castCount(ServerPlayer player) {
        return Math.max(0, Math.min(CASTS_PER_GUARANTEE - 1,
                player.getPersistentData().getInt(CAST_COUNT_KEY)));
    }

    /** 取用一次“必定暴击”；返回 true 表示这一次法术伤害应当直接判定为暴击。 */
    public static boolean consumeGuaranteedCrit(LivingEntity caster) {
        if (caster == null || caster.level().isClientSide || !wearing(caster, Kind.MASTER)) return false;
        var data = caster.getPersistentData();
        if (!data.getBoolean(GUARANTEED_KEY)) return false;
        data.putBoolean(GUARANTEED_KEY, false);
        return true;
    }
}
