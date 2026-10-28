package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * r211 砂轮新功能：把<b>抄录过内容</b>的魔法蓝图（纸 / 魔力布匹 / 魔法绸缎上的 custom_data 阵图）
 * 打磨回原本的样子——阵图整个磨掉，物品变回一张干净载体，并按蓝图里的粉末清单
 * <b>返还 25% 的随机粉末</b>（每一份粉独立 25% 概率回手，量大就接近 25%）。
 *
 * <p>五张教学用的「残破的蓝图」本身不带 custom_data（图样钉死在物品注册表里），
 * 没有可磨的东西，砂轮对它们不起作用，也不会被误毁。</p>
 *
 * <p>和 {@link ElementPlating} 的砂轮镀层拆除共用原版砂轮：放上时改输出（OnPlaceItem），
 * 取走时才结算返还（OnTakeItem），中途反悔把蓝图拿回去不会白送粉末。</p>
 *
 * <p>r212 修复「蓝图根本放不进砂轮」：原版砂轮输入槽的 {@code mayPlace} 只放行
 * 可损伤 / 带附魔 / 可砂轮修复的物品，纸和布一张都不占，之前接的两个事件根本没机会触发。
 * 现在在玩家打开砂轮界面的那一刻（{@link PlayerContainerEvent.Open}，服务端），
 * 把两个输入槽换成 {@link BlueprintGrindSlot}——放行规则 = 原版三判据 + 带内容的蓝图。</p>
 *
 * <p>r216 修「刻印石板磨了等于没磨」：石板存蓝图的键和纸 / 布完全一样，所以能放进砂轮、
 * 也能触发返还，但它<b>不是载体</b>（{@code Carrier} 只有纸 / 布 / 绸 / 残图四档），
 * 当初 {@code blankOf} 走到兜底分支，把原石板连数据一起复制出来当成品——内容一点没少，
 * 而且输入 = 输出，还能反复领粉末。现在石板单独走一条：磨完变回<b>空白石板</b>
 * （{@link EtchedSlateItem#blankStack()}，能重新刻新阵），并在
 * {@link #onGrindstonePlace} 里加了一道硬保险：成品如果还是「可磨的」，干脆不给成品。</p>
 */
public final class BlueprintGrinding {

    /** 每份粉末的返还概率（规范：25%）。 */
    private static final float REFUND_RATIO = 0.25F;

    /** 打开砂轮：把两个输入槽换成放行蓝图的版本（只动服务端实例，判定是服务端权威）。 */
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof GrindstoneMenu menu)) return;
        relaxSlot(menu, GrindstoneMenu.INPUT_SLOT);
        relaxSlot(menu, GrindstoneMenu.ADDITIONAL_SLOT);
    }

    private static void relaxSlot(GrindstoneMenu menu, int index) {
        Slot old = menu.slots.get(index);
        if (old instanceof BlueprintGrindSlot) return;
        BlueprintGrindSlot replacement =
                new BlueprintGrindSlot(old.container, old.getSlotIndex(), old.x, old.y);
        replacement.index = old.index;
        menu.slots.set(index, replacement);
    }

    /** 砂轮输入槽宽松版：原版三判据照旧，额外放行「带内容、可磨」的蓝图。 */
    static final class BlueprintGrindSlot extends Slot {
        BlueprintGrindSlot(net.minecraft.world.Container container, int slotIndex, int x, int y) {
            super(container, slotIndex, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isGrindable(stack) || stack.isDamageableItem()
                    || EnchantmentHelper.hasAnyEnchantments(stack)
                    || stack.canGrindstoneRepair();
        }
    }

    /** 放上砂轮：带内容的蓝图 → 输出换成干净载体（阵图磨掉），经验不给。 */
    public static void onGrindstonePlace(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem();
        if (!isGrindable(top)) return;
        if (event.getBottomItem() != null && !event.getBottomItem().isEmpty()) return; // 双槽不掺和
        ItemStack blank = blankOf(top);
        // 硬保险：成品必须真的「没内容了」。万一遇到没登记过的新载体（以前刻印石板就是这样），
        // blankOf 会退回原物，那样输入 = 输出，石板 / 蓝图白拿回去还能反复领粉末。
        if (blank.isEmpty() || isGrindable(blank)) return;
        blank.setCount(Math.min(top.getCount(), blank.getMaxStackSize())); // 叠几张磨几张
        event.setOutput(blank);
        event.setXp(0);
    }

    /** 取走成品：这时才按原蓝图粉末清单掷 25% 返还（放回去再拿不会白送）。 */
    public static void onGrindstoneTake(GrindstoneEvent.OnTakeItem event) {
        ItemStack top = event.getTopItem();
        if (!isGrindable(top)) return;
        if (event.getBottomItem() != null && !event.getBottomItem().isEmpty()) return;
        Player player = event.getPlayer();
        if (player == null) return;
        MagicCircleBlueprint blueprint = MagicCircleBlueprint.of(top);
        if (blueprint == null) return;
        int copies = Math.max(1, top.getCount()); // 一格叠几张就结算几份（放几张磨几张）
        Map<String, Integer> returned = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : blueprint.powderNeeds().entrySet()) {
            MagicCirclePowders.Powder powder = MagicCirclePowders.get(entry.getKey());
            if (powder == null) continue;
            ItemStack stack = powder.stack();
            if (stack.isEmpty()) continue;
            int units = 0;
            for (int i = 0; i < entry.getValue().intValue() * copies; i++) {
                if (player.getRandom().nextFloat() < REFUND_RATIO) units++;
            }
            // 返还可能超过一组上限：按组拆着发，剩下的掉在脚下。
            while (units > 0) {
                int hand = Math.min(powder.stack().getMaxStackSize(), units);
                ItemStack refund = powder.stack().copy();
                refund.setCount(hand);
                if (!player.getInventory().add(refund)) player.drop(refund, false);
                units -= hand;
                returned.merge(entry.getKey(), hand, Integer::sum);
            }
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        // 石板单独报一句：确认「石板里的阵图真的被磨掉了，变回空白石板」。
        if (top.getItem() instanceof EtchedSlateItem) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.blueprint.ground_slate").withStyle(ChatFormatting.GREEN), false);
        }
        if (!returned.isEmpty()) {
            StringBuilder line = new StringBuilder();
            for (Map.Entry<String, Integer> entry : returned.entrySet()) {
                if (line.length() > 0) line.append("、");
                line.append(MagicCirclePowders.shortName(entry.getKey())).append('×').append(entry.getValue());
            }
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.blueprint.ground", line.toString())
                    .withStyle(ChatFormatting.GREEN), false);
        }
    }

    /** 这物品是不是「带着可磨内容的蓝图」：custom_data 里有阵图且真有功能块。 */
    public static boolean isGrindable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof WornBlueprintItem) return false; // 教学残图钉在注册表里，没得磨
        if (!MagicCircleBlueprint.has(stack)) return false;
        MagicCircleBlueprint blueprint = MagicCircleBlueprint.of(stack);
        return blueprint != null && blueprint.nodeCount() > 0;
    }

    /** 磨掉阵图后的样子：纸就是干净纸、绸就是干净绸、刻印石板就是<b>空白石板</b>。 */
    private static ItemStack blankOf(ItemStack stack) {
        // 石板本身不是「载体」（载体只有纸 / 布 / 绸 / 残图四档），以前走到下面兜底那行，
        // 直接把原石板连数据一起复制出来，所以「磨了跟没磨一样」。这里单独处理：
        // 石板上刻着的阵图磨掉 → 变回一块能重新刻内容的空白石板。
        if (stack.getItem() instanceof EtchedSlateItem) {
            return EtchedSlateItem.blankStack().copyWithCount(1);
        }
        MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(stack);
        if (carrier == null) return stack.copyWithCount(1);
        ItemStack blank = carrier.stack().copyWithCount(1);
        return blank.isEmpty() ? stack.copyWithCount(1) : blank;
    }

    private BlueprintGrinding() {}
}
