package cn.blockforge.generated.minecraft1211n422a;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
 * 三件魔法披风：只能放入背饰槽，胸饰（body）栏位不再接受披风。
 *
 * <p>魔素强度、施法速度、减耗、暴击率与暴击伤害都写成 Curios 真实属性修正，
 * 装备与卸下时状态页数值会自动同步；其余触发型效果由 {@link MagicEquipment} 处理。</p>
 */
public final class MagicCloakItem extends Item implements ICurioItem {
    /** 施法时长减免 18% 换算成施法速度倍率：1 / 0.82 ≈ 1.2195，即 +21.95%。 */
    private static final double FOCUS_HASTE = 1.0 / 0.82 - 1.0;

    public enum Kind {
        /** 魔法集中披风：强度 +32、施法时长 -18%、减耗 +8%、暴击率 +5%。 */
        FOCUS("magic_focus_cloak", 32, FOCUS_HASTE, 0.08, 0.05, 0.0),
        /** 伤害偏转披风：强度 +64、魔素上限 +8%、魔法受伤 -25%、反射 45%。 */
        DEFLECT("damage_deflect_cloak", 64, 0.0, 0.0, 0.0, 0.0),
        /** 魔法大师披风：强度 +128、魔素上限 +25%、暴击伤害 +45%、施法生成魔素凝聚球。 */
        MASTER("magic_master_cloak", 128, 0.0, 0.0, 0.0, 0.45);

        private final String itemId;
        private final double spellPower;
        private final double spellHaste;
        private final double costReduction;
        private final double critChance;
        private final double critDamage;

        Kind(String itemId, double spellPower, double spellHaste, double costReduction,
             double critChance, double critDamage) {
            this.itemId = itemId;
            this.spellPower = spellPower;
            this.spellHaste = spellHaste;
            this.costReduction = costReduction;
            this.critChance = critChance;
            this.critDamage = critDamage;
        }

        public String itemId() { return itemId; }
        public String tooltipGroup() { return itemId; }
    }

    private final Kind kind;

    public MagicCloakItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        CuriosApi.registerCurio(this, this);
    }

    public Kind kind() { return kind; }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return MagicEquipment.CLOAK_SLOTS.contains(slotContext.identifier());
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create(
                ICurioItem.super.getAttributeModifiers(slotContext, id, stack));
        if (!MagicEquipment.CLOAK_SLOTS.contains(slotContext.identifier())) return modifiers;
        if (kind.spellPower > 0) modifiers.put(SpellAttributes.SPELL_POWER,
                new AttributeModifier(id, kind.spellPower, AttributeModifier.Operation.ADD_VALUE));
        if (kind.spellHaste > 0) modifiers.put(SpellAttributes.SPELL_HASTE,
                new AttributeModifier(id, kind.spellHaste, AttributeModifier.Operation.ADD_VALUE));
        if (kind.costReduction > 0) modifiers.put(SpellAttributes.MANA_COST_REDUCTION,
                new AttributeModifier(id, kind.costReduction, AttributeModifier.Operation.ADD_VALUE));
        if (kind.critChance > 0) modifiers.put(SpellAttributes.SPELL_CRIT_CHANCE,
                new AttributeModifier(id, kind.critChance, AttributeModifier.Operation.ADD_VALUE));
        if (kind.critDamage > 0) modifiers.put(SpellAttributes.SPELL_CRIT_DAMAGE,
                new AttributeModifier(id, kind.critDamage, AttributeModifier.Operation.ADD_VALUE));
        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dimensional_mana." + kind.tooltipGroup() + ".line1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dimensional_mana." + kind.tooltipGroup() + ".line2")
                .withStyle(ChatFormatting.AQUA));
        if (kind == Kind.DEFLECT || kind == Kind.MASTER) {
            tooltip.add(Component.translatable("tooltip.dimensional_mana." + kind.tooltipGroup() + ".line3")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (kind == Kind.MASTER) {
            tooltip.add(Component.translatable("tooltip.dimensional_mana." + kind.tooltipGroup() + ".line4")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.dimensional_mana." + kind.tooltipGroup() + ".line5")
                    .withStyle(ChatFormatting.DARK_PURPLE));
            // 凝聚球开关是玩家级设置，服务器同步过来后这里直接显示当前状态。
            boolean enabled = ClientMagicToggleState.orbEnabled();
            tooltip.add(Component.translatable(enabled
                            ? "tooltip.dimensional_mana.magic_master_cloak.orb_on"
                            : "tooltip.dimensional_mana.magic_master_cloak.orb_off",
                            Component.keybind("key.dimensional_mana.toggle_orb"))
                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.dimensional_mana.cloak.slots")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 主手持魔法大师披风时按 Shift+O 切换凝聚球功能。
     *
     * <p>开关是玩家级设置：手持披风只是“钥匙”，切开之后这件功能对这个人一直有效，
     * 换成别的披风、重新登录都保持原状态；关掉后凝聚球完全不生成。</p>
     */
    public static void toggleOrb(ServerPlayer player) {
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof MagicCloakItem cloak) || cloak.kind != Kind.MASTER) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.master_cloak_required").withStyle(ChatFormatting.RED), true);
            return;
        }
        boolean enabled = MagicEquipment.setOrbEnabled(player, !MagicEquipment.orbEnabled(player));
        MagicAchievements.onOrbToggled(player);
        player.displayClientMessage(Component.translatable(enabled
                        ? "message.dimensional_mana.orb_enabled" : "message.dimensional_mana.orb_disabled",
                (int)Math.round(MagicEquipment.MASTER_ORB_CHANCE * 100.0))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        // 让客户端马上刷新物品描述里的开关状态与手套计数。
        MagicToggleSyncPayload.send(player);
        player.level().playSound(null, player.blockPosition(),
                enabled ? net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME
                        : net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, enabled ? 1.35F : 0.85F);
    }
}
