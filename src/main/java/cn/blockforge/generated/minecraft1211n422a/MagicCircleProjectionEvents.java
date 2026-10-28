package cn.blockforge.generated.minecraft1211n422a;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.Locale;

/**
 * 蓝图 / 刻印石板的交互总入口。
 *
 * <ul>
 *   <li>手里拿着蓝图或刻印石板右键绘制台基座 → <b>上稿修改</b>（r211：蓝图收进右下角槽位，
 *       不复制就没痕迹；按「复制」付粉才把样本留在台上）；</li>
 *   <li>右键普通方块表面 → 贴面锚点（蓝图 = 只能试运行的投影；刻印石板 = 能正式运行的实体锚点）；</li>
 *   <li>右键已有同类锚点 → 再叠一层；</li>
 *   <li>潜行 + 右键锚点 → 卸下最上面一层（蓝图 / 石板退回手上）；</li>
 *   <li>潜行 + 右键空气 → 把手里这份阵图导成<strong>法阵码</strong>（点聊天栏那行就能复制）；</li>
 *   <li>空载体（纸 / 布 / 绸）潜行 + 右键空气<strong>或方块</strong> → 打开法阵码导入面板
 *       （另外还有一个固定按键 P，见 {@code ClientModEvents}）。</li>
 * </ul>
 *
 * <p>纸只能当草稿，不能贴面：这条是设计文档第 8 节写死的，这里如实拦住并提示换布或绸。</p>
 */
public final class MagicCircleProjectionEvents {
    /** 试运行冷却，避免按住 Shift 连点把魔素刷光。 */
    private static final int TEST_COOLDOWN_TICKS = 20;
    /** 玩家离锚点太远就拒绝试运行，和绘制台的 8 格一致。 */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    // ------------------------------------------------------------------ 蓝图 / 石板右键方块

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        // 手持超导魔晶粉右键锚点 = 连线模式（两次点击连一条，潜行点击拆掉全部）。
        if (isLinkDust(stack)) {
            Level dustLevel = event.getLevel();
            BlockPos dustPos = event.getPos();
            if (!(dustLevel.getBlockState(dustPos).getBlock() instanceof MagicCircleProjectionBlock)) return;
            if (dustLevel.isClientSide) return;
            event.setCanceled(true);
            if (dustLevel instanceof ServerLevel serverLevel
                    && event.getEntity() instanceof ServerPlayer player) {
                handleLinkDust(serverLevel, dustPos, player, stack);
            }
            return;
        }
        boolean slate = stack.getItem() instanceof EtchedSlateItem;
        MagicCircleBlueprint blueprint = slate
                ? EtchedSlateItem.blueprintOf(stack)
                : MagicCircleBlueprint.of(stack);
        // 空白的刻印石板也要放行：它是给投影锚点「刻印」用的钥匙。
        if (blueprint == null && !slate) {
            // 空载体（纸 / 布 / 绸）潜行 + 右键方块：也开法阵码导入面板。
            // 之前只有「潜行右键空气」这一个入口，对着地面右键就什么都不发生，玩家会以为导入坏了。
            if (event.getEntity().isShiftKeyDown() && MagicCircleBlueprint.carrierOf(stack) != null) {
                if (event.getLevel().isClientSide) {
                    event.setCanceled(true);
                    ClientMagicCircleScreens.openCodeImport();
                }
            }
            return;
        }
        Level level = event.getLevel();
        // 客户端这里故意不取消：原版是「客户端先发包、服务端再算」，客户端一取消服务端就收不到这次右键了。
        // 真正接管交互放在服务端做，客户端只负责把这次点击照常发出去。
        if (level.isClientSide) return;
        // 服务端：拿着蓝图 / 石板点方块，这次交互全部由它接管，免得顺手打开箱子或把物品垫在方块上。
        event.setCanceled(true);
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos pos = event.getPos();
        BlockState state = serverLevel.getBlockState(pos);

        if (state.getBlock() instanceof MagicCircleBaseBlock) {
            if (blueprint == null) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.slate.seal_hint")
                        .withStyle(ChatFormatting.YELLOW), false);
                return;
            }
            // r211：手持蓝图 / 石板右键绘制台 = 「上稿修改」，不再一键复制。
            // 蓝图收进界面右下角槽位，想留在台上得按「复制」并付粉。
            MagicCircleDrawing.beginBlueprintEdit(serverLevel, pos, player, stack);
            return;
        }
        if (state.getBlock() instanceof MagicCircleProjectionBlock) {
            if (slate && blueprint == null) {
                // 空白刻印石板：给投影锚点「刻印」，让它从只能试运行变成能正式运行。
                sealAnchor(serverLevel, pos, player, stack);
                return;
            }
            if (player.isShiftKeyDown()) {
                unstackLayer(serverLevel, pos, player, slate);
            } else {
                stackLayer(serverLevel, pos, player, stack, blueprint, slate);
            }
            return;
        }
        if (blueprint == null) {
            // 空白石板贴到普通方块上：先说明它该怎么用。
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.slate.seal_hint")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (!slate && !blueprint.carrier().placeable()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.not_placeable")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        placeProjection(serverLevel, pos, event.getFace(), player, stack, blueprint, slate);
    }

    /**
     * 潜行 + 右键空气，看手里拿的是什么：
     *
     * <ul>
     *   <li>带阵图的蓝图 / 刻印石板 → 导成法阵码（点聊天栏那行就能复制）；</li>
     *   <li>一张空白的纸 / 布 / 绸 → 打开法阵码<strong>导入面板</strong>。</li>
     * </ul>
     *
     * <p>导入之所以不能走指令：原版聊天输入框最多只收 256 个字符，法阵码根本粘不进去。
     * 面板里一次能吞下 4096 个字符，粘完回车就行。</p>
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().isShiftKeyDown()) return;
        ItemStack stack = event.getItemStack();
        MagicCircleBlueprint blueprint = stack.getItem() instanceof EtchedSlateItem
                ? EtchedSlateItem.blueprintOf(stack)
                : MagicCircleBlueprint.of(stack);
        if (blueprint != null) {
            if (event.getLevel().isClientSide) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            event.setCanceled(true);
            exportCode(player, blueprint);
            return;
        }
        // 空载体：开导入面板。面板是纯客户端的，服务端这边只把这次右键吃掉。
        if (MagicCircleBlueprint.carrierOf(stack) == null) return;
        event.setCanceled(true);
        if (event.getLevel().isClientSide) ClientMagicCircleScreens.openCodeImport();
    }

    /** 把阵图编码成法阵码并发到聊天栏（那一行是「点击复制」）。 */
    public static void exportCode(ServerPlayer player, MagicCircleBlueprint blueprint) {
        String code = MagicCircleCode.encode(blueprint);
        if (code.isEmpty() || code.length() > MagicCircleCode.MAX_CHARS) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.code.export_failed")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.code.exported", blueprint.name(), code.length())
                .withStyle(ChatFormatting.GOLD), false);
        MutableComponent clickable = Component.literal(code)
                .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code)));
        player.displayClientMessage(clickable, false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.code.export_hint").withStyle(ChatFormatting.GRAY), false);
    }

    /** 贴面：在点击的那一面上生成锚点，并把这份蓝图放进第一层。 */
    private static void placeProjection(ServerLevel level, BlockPos clicked, Direction face,
                                        ServerPlayer player, ItemStack stack, MagicCircleBlueprint blueprint,
                                        boolean sealed) {
        Direction facing = face == null ? Direction.UP : face;
        BlockPos anchor = clicked.relative(facing);
        if (!level.isInWorldBounds(anchor) || !level.getBlockState(anchor).canBeReplaced()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.surface_blocked")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        var block = GeneratedMod.MOD_BLOCKS.get(MagicCircleProjectionBlock.BLOCK_ID);
        if (block == null) return;
        BlockState state = block.get().defaultBlockState()
                .setValue(MagicCircleProjectionBlock.FACING, facing)
                .setValue(MagicCircleProjectionBlock.SEALED, sealed);
        level.setBlockAndUpdate(anchor, state);
        if (!(level.getBlockEntity(anchor) instanceof MagicCircleProjectionBlockEntity entity)
                || !entity.addLayer(blueprint)) {
            level.removeBlock(anchor, false);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.layer_failed")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        entity.setOwner(player.getUUID());
        entity.sync();
        if (!player.isCreative()) stack.shrink(1);
        MagicCircleCost.Evaluation evaluation = entity.evaluate();
        if (sealed) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.slate.placed",
                    blueprint.name(), blueprint.baseW(), blueprint.baseH(), evaluation.nodeCount(),
                    evaluation.startMana(), evaluation.upkeepMana(), evaluation.triggerMana(),
                    String.format(Locale.ROOT, "%.2f", evaluation.strength()))
                    .withStyle(ChatFormatting.AQUA), false);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.slate.start_hint").withStyle(ChatFormatting.GRAY), false);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.projected",
                    blueprint.name(), blueprint.baseW(), blueprint.baseH(), evaluation.nodeCount(),
                    evaluation.startMana()).withStyle(ChatFormatting.AQUA), false);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.test_hint")
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    /**
     * 刻印：拿一块<b>空白刻印石板</b>右键蓝图投影锚点，把它升级成实体锚点。
     *
     * <p>这是第 4 阶段的转折点：先在投影上免费看、半价试，觉得可以了再花一块石板把它
     * 变成能正式运行的阵。蓝图（也就是那几层数据）原样留着，不用重画。</p>
     */
    private static void sealAnchor(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) return;
        if (entity.sealed()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.slate.already_sealed")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (entity.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.anchor_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        BlockState state = level.getBlockState(pos)
                .setValue(MagicCircleProjectionBlock.SEALED, Boolean.TRUE);
        level.setBlockAndUpdate(pos, state);
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity sealed)) return;
        sealed.setOwner(player.getUUID());
        sealed.setRunning(false);
        sealed.sync();
        if (!player.isCreative()) stack.shrink(1);
        MagicCircleCost.Evaluation evaluation = sealed.evaluate();
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.slate.sealed",
                sealed.layerCount(), evaluation.nodeCount(), evaluation.startMana(),
                evaluation.upkeepMana(), evaluation.triggerMana(),
                String.format(Locale.ROOT, "%.2f", evaluation.strength()))
                .withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.slate.start_hint").withStyle(ChatFormatting.GRAY), false);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 0.8F);
    }

    /** 往已有锚点再叠一层。刻印锚点只收刻印石板，投影锚点只收蓝图，两类不混。 */
    public static void stackLayer(ServerLevel level, BlockPos pos, ServerPlayer player,
                                  ItemStack stack, MagicCircleBlueprint blueprint, boolean slate) {
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) return;
        if (entity.sealed() != slate) {
            player.displayClientMessage(Component.translatable(
                    entity.sealed()
                            ? "message.dimensional_mana.magiccircle.slate.mixed_sealed"
                            : "message.dimensional_mana.magiccircle.slate.mixed_projection")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (!slate && !blueprint.carrier().placeable()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.not_placeable")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (entity.full()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.layer_full", entity.maxLayers())
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!entity.sameSize(blueprint)) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.layer_mismatch",
                    entity.baseW(), entity.baseH(), blueprint.baseW(), blueprint.baseH())
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!entity.addLayer(blueprint)) return;
        if (!player.isCreative()) stack.shrink(1);
        MagicCircleCost.Evaluation evaluation = entity.evaluate();
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.layer_added",
                blueprint.name(), entity.layerCount(), entity.maxLayers(), evaluation.startMana())
                .withStyle(ChatFormatting.AQUA), false);
    }

    /** 潜行右键锚点：卸下最上面一层，原样退回成物品。 */
    public static void unstackLayer(ServerLevel level, BlockPos pos, ServerPlayer player, boolean slate) {
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) return;
        if (entity.sealed() != slate) {
            player.displayClientMessage(Component.translatable(
                    entity.sealed()
                            ? "message.dimensional_mana.magiccircle.slate.mixed_sealed"
                            : "message.dimensional_mana.magiccircle.slate.mixed_projection")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        MagicCircleBlueprint removed = entity.removeTopLayer();
        if (removed == null) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.anchor_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        ItemStack drop = slate ? EtchedSlateItem.stackFor(removed) : MagicCircleBlueprints.stackFor(removed);
        if (!drop.isEmpty() && !player.getInventory().add(drop)) player.drop(drop, false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.layer_removed",
                removed.name(), entity.layerCount()).withStyle(ChatFormatting.YELLOW), false);
    }

    // ------------------------------------------------------------------ 异面连线

    /** 玩家持久数据里存「已经选中的第一个锚点」，存的是 BlockPos.asLong()。 */
    private static final String LINK_FROM_KEY = "dmMagicCircleLinkFrom";

    /** 手里拿的是不是超导魔晶粉（连线的耗材）。 */
    private static boolean isLinkDust(ItemStack stack) {
        MagicCirclePowders.Powder powder =
                MagicCirclePowders.get("dimensional_mana:superconducting_crystal_dust");
        return powder != null && !powder.stack().isEmpty() && stack.is(powder.item());
    }

    /**
     * 超导魔晶粉的两次点击连线：第一次记下起点，第二次连到终点。
     *
     * <p>潜行 + 右键 = 把这个锚点的连线全拆掉。连线本身扣 1 份超导粉 + 10 魔素，
     * 距离上限、每个锚点的连线上限都由 {@link MagicCircleConfig} 管。</p>
     */
    private static void handleLinkDust(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) return;
        CompoundTag data = player.getPersistentData();
        if (player.isShiftKeyDown()) {
            if (entity.linkCount() == 0) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.link.none").withStyle(ChatFormatting.GRAY), false);
                return;
            }
            int count = entity.linkCount();
            MagicCircleRites.unlinkAll(level, pos, entity);
            data.remove(LINK_FROM_KEY);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.cleared", count)
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (!data.contains(LINK_FROM_KEY)) {
            data.putLong(LINK_FROM_KEY, pos.asLong());
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.selected", pos.toShortString())
                    .withStyle(ChatFormatting.AQUA), false);
            return;
        }
        BlockPos from = BlockPos.of(data.getLong(LINK_FROM_KEY));
        if (from.equals(pos)) {
            data.remove(LINK_FROM_KEY);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.cancelled").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (!(level.getBlockEntity(from) instanceof MagicCircleProjectionBlockEntity)) {
            data.remove(LINK_FROM_KEY);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.lost").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (MagicCircleRites.link(level, from, pos, player)) {
            data.remove(LINK_FROM_KEY);
            if (!player.isCreative()) stack.shrink(1);
        }
    }


    /** 右键绘制台基座：把蓝图铺进画布（只往空层放，不覆盖已画的东西，也不消耗蓝图）。 */
    // ------------------------------------------------------------------ 拆解研究

    /** 右键绘制台基座：把蓝图铺进画布（只往空层放，不覆盖已画的东西，也不消耗蓝图）。 */
    /** 首次拆解给一次魔法经验，同一个蓝图只给一次（存在玩家持久数据里）。 */
    static void firstStudyReward(ServerPlayer player, MagicCircleBlueprint blueprint) {
        CompoundTag persist = player.getPersistentData();
        String key = "magicBlueprintStudied." + blueprint.id();
        if (persist.getBoolean(key)) return;
        persist.putBoolean(key, true);
        MagicProgression.addMagicExperience(player, 32);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.first_study").withStyle(ChatFormatting.GOLD), false);
    }

    /**
     * 这份蓝图（只比单层蓝图）是不是已经铺在画布某一层了；返回层下标，没有就返回 -1。
     *
     * <p>比较的是「铺到这张画布上之后的样子」，所以不同尺寸的基座也能正确认出来。</p>
     */
    static int findExistingLayer(MagicCircleNetwork network, MagicCircleBlueprint blueprint) {
        if (blueprint == null || blueprint.layers().size() != 1) return -1;
        MagicCircleNetwork.Layer probe = new MagicCircleNetwork.Layer();
        if (copyIntoCanvas(blueprint, probe, network.canvasWidth(), network.canvasHeight()) <= 0) return -1;
        for (int i = 0; i < Math.max(1, MagicCircleConfig.MAX_LAYERS.get()); i++) {
            MagicCircleNetwork.Layer layer = network.layer(i);
            if (layer.nodes().isEmpty()) continue;
            if (sameLayer(layer, probe)) return i;
        }
        return -1;
    }

    /** 两层功能块是不是一模一样（类型、坐标、四个粉槽、变体都要对得上）。 */
    private static boolean sameLayer(MagicCircleNetwork.Layer a, MagicCircleNetwork.Layer b) {
        if (a.nodes().size() != b.nodes().size()) return false;
        for (int i = 0; i < a.nodes().size(); i++) {
            MagicCircleNetwork.Node left = a.nodes().get(i);
            MagicCircleNetwork.Node right = b.nodes().get(i);
            if (left.type() != right.type() || left.x() != right.x() || left.y() != right.y()) return false;
            if (!left.element().equals(right.element()) || !left.semantic().equals(right.semantic())) return false;
            if (!left.power().equals(right.power()) || !left.dye().equals(right.dye())) return false;
            if (!left.variant().equals(right.variant())) return false;
        }
        return true;
    }

    /**
     * 把蓝图铺进一层画布：尺寸一样就原样放，画布比蓝图小就等比缩放并居中，画布更大就居中。
     *
     * @return 实际放进去的功能块数
     */
    static int copyIntoCanvas(MagicCircleBlueprint blueprint, MagicCircleNetwork.Layer target,
                              int canvasW, int canvasH) {
        float scale = Math.min(1.0F, Math.min(canvasW / (float) blueprint.canvasWidth(),
                canvasH / (float) blueprint.canvasHeight()));
        float offsetX = (canvasW - blueprint.canvasWidth() * scale) * 0.5F;
        float offsetY = (canvasH - blueprint.canvasHeight() * scale) * 0.5F;
        int added = 0;
        for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
            int start = target.nodes().size();
            for (MagicCircleNetwork.Node node : layer.nodes()) {
                int x = clamp(Math.round(node.x() * scale + offsetX), canvasW);
                int y = clamp(Math.round(node.y() * scale + offsetY), canvasH);
                target.add(new MagicCircleNetwork.Node(node.type(), x, y, node.element(), node.semantic(),
                        node.power(), node.dye(), node.variant(), node.handDrawn(), node.declare(), node.gate()));
                added++;
            }
            int size = target.nodes().size();
            for (MagicCircleNetwork.Link link : layer.links()) {
                int from = start + link.from();
                int to = start + link.to();
                if (from < size && to < size) target.addLink(from, to);
            }
            // 同一次拆解里的多层，层与层之间不自动连线：要连得靠「连接符文」，这是设计文档 10.1。
        }
        return added;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(Math.max(0, size - 1), value));
    }

    // ------------------------------------------------------------------ 调试棒：预览 / 试运行

    /** 免费预览：锚点类型、每一层是什么、连线几条、总共多少消耗、缺哪块。 */
    public static void preview(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) return;
        if (entity.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.anchor_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        MagicCircleCost.Evaluation evaluation = entity.evaluate();
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.anchor_header",
                entity.layerCount(), entity.maxLayers(), entity.baseW(), entity.baseH(),
                entity.baseW() * MagicCircleNetwork.TILE, entity.baseH() * MagicCircleNetwork.TILE,
                evaluation.nodeCount()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(entity.sealed()
                        ? Component.translatable(entity.running()
                                ? "message.dimensional_mana.magiccircle.slate.state_running"
                                : "message.dimensional_mana.magiccircle.slate.state_idle")
                        .withStyle(entity.running() ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                        : Component.translatable("message.dimensional_mana.magiccircle.slate.state_projection")
                        .withStyle(ChatFormatting.DARK_AQUA),
                false);
        for (int i = 0; i < entity.layers().size(); i++) {
            MagicCircleBlueprint blueprint = entity.layers().get(i);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.anchor_layer",
                    i + 1, blueprint.name(), blueprint.nodeCount(), blueprint.filledLayerCount())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            // 每个功能块用了哪四种粉，直接跟在层后面列出来（每层最多 6 行，大阵不至于刷屏）。
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.anchor_node_title")
                    .withStyle(ChatFormatting.GRAY), false);
            for (Component line : blueprint.nodeDetailLines(6)) {
                player.displayClientMessage(line, false);
            }
        }
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.status.cost",
                evaluation.startMana(), evaluation.upkeepMana(), evaluation.triggerMana())
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.status.strength",
                String.format(Locale.ROOT, "%.2f", evaluation.strength()), evaluation.complexity(),
                evaluation.disorder(), String.format(Locale.ROOT, "%.2f", evaluation.efficiency()))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (entity.linkCount() > 0) {
            int groupSize = MagicCircleRites.group(level, pos, entity).size();
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.link.status",
                    entity.linkCount(), groupSize).withStyle(ChatFormatting.DARK_AQUA), false);
        }
        // 顺手报一下「现在开了几个 / 最多能开几个」，省得启动时才被卡住。
        player.displayClientMessage(MagicCircleRites.limitSummary(player).copy()
                .withStyle(ChatFormatting.DARK_AQUA), false);
        for (String problem : evaluation.problems()) {
            player.displayClientMessage(Component.empty().append(MagicCircleIssue.component(problem)).withStyle(ChatFormatting.RED), false);
        }
        if (entity.sealed()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.slate.preview_hint",
                    MagicCircleConfig.MAX_SUMMONS_PER_NETWORK.get(),
                    MagicCircleConfig.MAX_BLOCKS_PER_TRIGGER.get(),
                    MagicCircleConfig.MAX_BLOCKS_PER_MINUTE.get()).withStyle(ChatFormatting.GRAY), false);
            player.displayClientMessage(Component.translatable(
                    entity.running()
                            ? "message.dimensional_mana.magiccircle.slate.hint_running"
                            : "message.dimensional_mana.magiccircle.slate.hint_off")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            int testCost = testCost(entity);
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.test_cost",
                    testCost, Math.round(MagicCircleConfig.TEST_RUN_COST_RATIO.get() * 100))
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** 半价试运行（只对投影锚点有意义）：扣一半启动成本，打一次光效脉冲，不产生战利品。 */
    public static void testRun(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof MagicCircleProjectionBlockEntity entity)) return;
        if (entity.sealed()) {
            // 实体锚点不搞半价了：要么启动正式跑，要么别跑。
            MagicCircleRites.toggleRun(level, pos, player);
            return;
        }
        if (entity.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.anchor_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.too_far").withStyle(ChatFormatting.RED), false);
            return;
        }
        long now = level.getGameTime();
        if (entity.lastTestTick() >= 0 && now - entity.lastTestTick() < TEST_COOLDOWN_TICKS) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.test_cooldown")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        MagicCircleCost.Evaluation evaluation = entity.evaluate();
        if (evaluation.nodeCount() == 0 || !evaluation.problems().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.dimensional_mana.magiccircle.blueprint.test_invalid")
                    .withStyle(ChatFormatting.RED), false);
            for (String problem : evaluation.problems()) {
                player.displayClientMessage(Component.empty().append(MagicCircleIssue.component(problem)).withStyle(ChatFormatting.RED), false);
            }
            return;
        }
        int cost = testCost(entity);
        boolean free = player.isCreative() && MagicCircleConfig.CREATIVE_FREE_POWDER.get();
        if (!free) {
            double mana = ManaData.get(player).mana();
            if (mana < cost) {
                player.displayClientMessage(Component.translatable(
                        "message.dimensional_mana.magiccircle.blueprint.test_no_mana",
                        (int) Math.ceil(cost - mana), cost).withStyle(ChatFormatting.RED), false);
                return;
            }
            ManaData.spendMana(player, cost);
        }
        entity.markTestRun(now);
        entity.sync();
        pulse(level, pos, entity);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.test_done",
                free ? 0 : cost, evaluation.startMana(), evaluation.upkeepMana(), evaluation.triggerMana(),
                String.format(Locale.ROOT, "%.2f", evaluation.strength()))
                .withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.translatable(
                "message.dimensional_mana.magiccircle.blueprint.test_note").withStyle(ChatFormatting.DARK_GRAY),
                false);
    }

    private static int testCost(MagicCircleProjectionBlockEntity entity) {
        return (int) Math.ceil(entity.evaluate().startMana() * MagicCircleConfig.TEST_RUN_COST_RATIO.get());
    }

    /** 试运行的光效：每个功能块的位置按它的元素炸一小撮粒子。 */
    private static void pulse(ServerLevel level, BlockPos pos, MagicCircleProjectionBlockEntity entity) {
        Direction facing = entity.getBlockState().hasProperty(MagicCircleProjectionBlock.FACING)
                ? entity.getBlockState().getValue(MagicCircleProjectionBlock.FACING)
                : Direction.UP;
        for (MagicCircleBlueprint blueprint : entity.layers()) {
            for (MagicCircleNetwork.Layer layer : blueprint.layers()) {
                for (MagicCircleNetwork.Node node : layer.nodes()) {
                    float[] local = MagicCircleProjectionGeometry.local(facing, entity.baseW(), entity.baseH(),
                            node.x(), node.y(), 0.03F);
                    ParticleOptions particle = particleFor(MagicCirclePowders.elementOf(node.element()));
                    level.sendParticles(particle, pos.getX() + local[0], pos.getY() + local[1],
                            pos.getZ() + local[2], 2, 0.06, 0.06, 0.06, 0.01);
                }
            }
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.7F, 1.35F);
    }

    /** 元素粉 → 和它气质相符的原版粒子。 */
    private static ParticleOptions particleFor(ElementType element) {
        if (element == null) return ParticleTypes.ENCHANT;
        return switch (element) {
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.SPLASH;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case WIND -> ParticleTypes.GUST;
            case EARTH -> ParticleTypes.POOF;
            case NATURE -> ParticleTypes.HAPPY_VILLAGER;
            case METAL -> ParticleTypes.CRIT;
            case LIGHT -> ParticleTypes.END_ROD;
            case POISON -> ParticleTypes.ITEM_SLIME;
            case SOUND -> ParticleTypes.NOTE;
        };
    }

    // ------------------------------------------------------------------ 物品提示

    /** 任何带着阵图数据的物品（纸 / 布 / 绸 / 残破的蓝图 / 刻印石板）都显示尺寸、消耗、用法。 */
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof EtchedSlateItem) return; // 石板有自己的一套提示
        MagicCircleBlueprint blueprint = MagicCircleBlueprint.of(stack);
        if (blueprint == null) return;
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.translatable("tooltip.dimensional_mana.blueprint.title", blueprint.name())
                .withStyle(ChatFormatting.GOLD));
        tooltip.addAll(blueprint.describe());
        tooltip.add(Component.literal(blueprint.blockSummary()).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.magiccircle.node.title")
                .withStyle(ChatFormatting.GOLD));
        tooltip.addAll(blueprint.nodeDetailLines(8));
        tooltip.add(Component.translatable(blueprint.carrier().placeable()
                        ? "tooltip.dimensional_mana.blueprint.usage.placeable"
                        : "tooltip.dimensional_mana.blueprint.usage.draft")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.blueprint.usage.study")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.dimensional_mana.blueprint.usage.code")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private MagicCircleProjectionEvents() {}
}
