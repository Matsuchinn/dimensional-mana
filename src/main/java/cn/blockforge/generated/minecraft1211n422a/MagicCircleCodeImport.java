package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 法阵码导入：把一段 {@code DM1-...} 的码变成手里的一份阵图。
 *
 * <p>聊天框最多只能敲 / 粘 256 个字符（原版 {@code ChatScreen} 写死的），而法阵码动辄几百上千字，
 * 所以「把码粘进指令」这条路根本走不通。现在改成界面里粘贴：客户端把整段码发给服务端，
 * 服务端在这里统一校验 → 选载体 → 生成物品。指令 {@code /dimensional_mana magiccircle code import}
 * 也走同一份逻辑，短码照样能用。</p>
 *
 * <p>导入只还原结构，<b>不扣粉末</b>；缺什么材料会在聊天栏列出来。载体容量不够就直接拒绝，
 * 不会把阵图截断成半个。</p>
 *
 * <p>面板走 {@link #applyFromPanel}：结果会用 {@link MagicCircleCodeResultPayload} 发回客户端，
 * 面板当场显示「导入成功 / 卡在哪一步」。失败时面板不关、码不清，换一张容量够的载体再按一次
 * 回车就行——不用再回聊天栏重新复制一遍。</p>
 */
public final class MagicCircleCodeImport {

    /** 一次导入的结果：成功 / 失败 + 给人看的一句话 + 额外的补充行（材料清单、下一步提示）。 */
    public record Outcome(boolean ok, Component message, List<Component> extra) {
        static Outcome fail(Component message, Component... extra) {
            return new Outcome(false, message, List.of(extra));
        }
    }

    /**
     * 指令用：直接把结果打到聊天栏。
     *
     * @return 成功返回 true（已经扣掉载体、把阵图塞进背包）
     */
    public static boolean apply(ServerPlayer player, String code) {
        Outcome outcome = run(player, code);
        player.displayClientMessage(outcome.message(), false);
        for (Component line : outcome.extra()) player.displayClientMessage(line, false);
        return outcome.ok();
    }

    /**
     * 面板用：结果发回客户端，让面板当场显示；面板已经关掉时客户端会把它打进聊天栏。
     * 这里不直接写聊天栏，避免面板开着的时候同一句话出现两遍。
     */
    public static void applyFromPanel(ServerPlayer player, String code) {
        Outcome outcome = run(player, code);
        // 失败时把第一句补救提示也摆进面板（面板里红字一句话往往不够用），其余照旧进聊天栏。
        Component panelMessage = outcome.message();
        if (!outcome.ok() && !outcome.extra().isEmpty()) {
            panelMessage = outcome.message().copy()
                    .append(Component.literal("\n"))
                    .append(outcome.extra().get(0));
        }
        PacketDistributor.sendToPlayer(player, new MagicCircleCodeResultPayload(outcome.ok(), panelMessage));
        for (int i = outcome.ok() ? 0 : 1; i < outcome.extra().size(); i++) {
            player.displayClientMessage(outcome.extra().get(i), false);
        }
    }

    // ------------------------------------------------------------------ 真正的导入

    private static Outcome run(ServerPlayer player, String code) {
        MagicCircleCode.Result result = MagicCircleCode.decode(code == null ? "" : code.trim());
        if (!result.ok()) {
            return Outcome.fail(Component.translatable(result.error()).withStyle(ChatFormatting.RED),
                    Component.translatable("command.dimensional_mana.magiccircle.code.bad_code_hint")
                            .withStyle(ChatFormatting.GRAY));
        }
        MagicCircleBlueprint blueprint = result.blueprint();
        ItemStack carrierStack = findCarrier(player, blueprint);
        if (carrierStack == null) {
            return reportNoCarrier(player, blueprint);
        }
        MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(carrierStack);
        if (carrier == null) {
            return reportNoCarrier(player, blueprint);
        }
        // 码里没记载体（谁都能导进纸 / 布 / 绸），所以按实际用的载体重写一份，
        // 不然物品上会写着「绸缎」却装在一张纸里，贴面 / 叠层的判断全乱。
        MagicCircleBlueprint shaped = blueprint.withCarrier(carrier);
        ItemStack out = MagicCircleBlueprint.createStack(carrier, shaped);
        if (out.isEmpty()) {
            // 正常情况下 findCarrier 已经保证装得下，走到这里多半是载体物品本身没拿到。
            // 别一律报「装不下」，那会把一个罕见故障说成容量问题，玩家换十张纸也没用。
            if (!blueprint.fits(carrier)) return noCapacity(carrier, blueprint);
            return Outcome.fail(Component.translatable(
                    "command.dimensional_mana.magiccircle.code.stack_failed").withStyle(ChatFormatting.RED));
        }
        if (!player.isCreative()) carrierStack.shrink(1);
        if (!player.getInventory().add(out)) player.drop(out, false);
        List<Component> extra = new ArrayList<>(shaped.powderLines());
        extra.add(Component.translatable("command.dimensional_mana.magiccircle.code.powder_hint")
                .withStyle(ChatFormatting.GRAY));
        return new Outcome(true, Component.translatable(
                "command.dimensional_mana.magiccircle.code.imported",
                shaped.name(), shaped.baseW(), shaped.baseH(), shaped.nodeCount())
                .withStyle(ChatFormatting.AQUA), extra);
    }

    /**
     * 找一张装得下的载体：主手优先，其次副手，再按背包顺序找容量最大的那个。
     * 一张都没有 / 都装不下就返回 null（由 {@link #reportNoCarrier} 说清楚原因）。
     */
    private static ItemStack findCarrier(ServerPlayer player, MagicCircleBlueprint blueprint) {
        ItemStack main = player.getMainHandItem();
        if (fits(main, blueprint)) return main;
        ItemStack off = player.getOffhandItem();
        if (fits(off, blueprint)) return off;
        ItemStack best = null;
        int bestCapacity = -1;
        for (ItemStack stack : player.getInventory().items) {
            if (!fits(stack, blueprint)) continue;
            MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(stack);
            if (carrier == null) continue;
            if (carrier.maxNodes() > bestCapacity) {
                bestCapacity = carrier.maxNodes();
                best = stack;
            }
        }
        return best;
    }

    private static boolean fits(ItemStack stack, MagicCircleBlueprint blueprint) {
        MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(stack);
        // 残破的蓝图只用来「拆解研究」，不拿来装导入的阵。
        return carrier != null && carrier != MagicCircleBlueprint.Carrier.WORN && blueprint.fits(carrier);
    }

    /** 说清楚是「一张载体都没有」还是「有载体但都装不下」，并给出下一步怎么办。 */
    private static Outcome reportNoCarrier(ServerPlayer player, MagicCircleBlueprint blueprint) {
        MagicCircleBlueprint.Carrier biggest = null;
        for (ItemStack stack : player.getInventory().items) {
            MagicCircleBlueprint.Carrier carrier = MagicCircleBlueprint.carrierOf(stack);
            if (carrier == null || carrier == MagicCircleBlueprint.Carrier.WORN) continue;
            if (biggest == null || carrier.maxNodes() > biggest.maxNodes()) biggest = carrier;
        }
        MagicCircleBlueprint.Carrier offhand = MagicCircleBlueprint.carrierOf(player.getOffhandItem());
        if (offhand != null && offhand != MagicCircleBlueprint.Carrier.WORN
                && (biggest == null || offhand.maxNodes() > biggest.maxNodes())) {
            biggest = offhand;
        }
        if (biggest == null) {
            return Outcome.fail(Component.translatable(
                    "command.dimensional_mana.magiccircle.code.need_carrier").withStyle(ChatFormatting.RED),
                    Component.translatable("command.dimensional_mana.magiccircle.code.need_carrier_hint")
                            .withStyle(ChatFormatting.GRAY));
        }
        return noCapacity(biggest, blueprint);
    }

    /** 装不下：把「要多大」和「你手上最大的是多大」摆在一起，再给一句下一步。 */
    private static Outcome noCapacity(MagicCircleBlueprint.Carrier carrier, MagicCircleBlueprint blueprint) {
        return Outcome.fail(Component.translatable(
                        "command.dimensional_mana.magiccircle.code.no_capacity",
                        carrier.fallbackName(), carrier.maxNodes(), carrier.maxLayers(),
                        blueprint.nodeCount(), blueprint.filledLayerCount()).withStyle(ChatFormatting.RED),
                Component.translatable("command.dimensional_mana.magiccircle.code.capacity_hint",
                        MagicCircleBlueprint.Carrier.SILK.fallbackName(),
                        MagicCircleBlueprint.Carrier.SILK.maxNodes(),
                        MagicCircleBlueprint.Carrier.SILK.maxLayers()).withStyle(ChatFormatting.GRAY));
    }

    private MagicCircleCodeImport() {}
}
