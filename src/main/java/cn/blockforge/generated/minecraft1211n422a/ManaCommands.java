package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ManaCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dimensional_mana")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> showStatus(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                // 武备词条的实际数值：一条条打在聊天栏，方便核对「属性查得到、也真的在生效」。
                .then(Commands.literal("affixes")
                        .executes(context -> showAffixes(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> showAffixes(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(antiVoidCommand())
                .then(Commands.literal("combat")
                        .executes(context -> showCombat(context.getSource()))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> showCombat(context.getSource(),
                                        EntityArgument.getEntity(context, "target"))))
                        // 自己的伤害减免三段拆解：护甲值 → 保护附魔 → 魔精钢独立减伤。
                        .then(Commands.literal("reduction")
                                .executes(context -> showReduction(context.getSource(), 6.0F))
                                .then(Commands.argument("damage", FloatArgumentType.floatArg(0.1F, 1000.0F))
                                        .executes(context -> showReduction(context.getSource(),
                                                FloatArgumentType.getFloat(context, "damage"))))))
                .then(mobCommand())
                // r105：本模组生物的自然生成权重（dimensional_mana-spawns.toml 的 [spawn_weights]）。
                .then(spawnWeightsCommand())
                // 魔蚀层数 / 深渊阶层：对着生物用看那只生物，空着用看自己。
                // 生物身上没有状态说明可看，想知道「它到底叠了几层、每跳掉几点」只能靠这一条。
                .then(Commands.literal("erosion")
                        .executes(context -> showErosion(context.getSource()))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> showErosion(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("mana")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1_000_000_000.0))
                                        .executes(context -> setMana(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("battle_qi")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100_000_000))
                                        .executes(context -> setBattleQi(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(Commands.literal("max_mana")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 100_000_000.0))
                                        .executes(context -> setMaxMana(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("max_battle_qi")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 100_000_000))
                                        .executes(context -> setMaxBattleQi(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(debugPlayerCommand("unlock_spells", ManaCommands::unlockSpells))
                .then(debugPlayerCommand("unlock_elements", ManaCommands::unlockElements))
                .then(debugPlayerCommand("max_skills", ManaCommands::maxSkills))
                .then(debugPlayerCommand("unlock_all", ManaCommands::unlockAll))
                .then(magicCircleCommand())
                .then(Commands.literal("locate_cluster")
                        .executes(context -> locateCluster(context.getSource(), CrystalClusterLocator.DEFAULT_CELLS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, CrystalClusterLocator.MAX_CELLS))
                                .executes(context -> locateCluster(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(cooldownCommand())
                .then(manaCostCommand())
                .then(spellCritCommand())
                 .then(elementalMasteryCommand()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> antiVoidCommand() {
        return Commands.literal("antvoid")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("enter").executes(context -> enterAntiVoid(context.getSource())))
                .then(Commands.literal("deathgate").executes(context -> testDeathGate(context.getSource())))
                .then(Commands.literal("resetintro").executes(context -> resetAntiVoidIntro(context.getSource())));
    }

    private static int enterAntiVoid(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getServer().getLevel(AntiVoidSystem.ANTI_VOID);
        if (level == null) return 0;
        player.teleportTo(level, 0.5D, AntiVoidSystem.FLOOR_Y, 0.5D, 0.0F, 0.0F);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.antvoid.enter"), false);
        return 1;
    }

    private static int testDeathGate(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.getPersistentData().putInt("AntiVoidGatePending", 1);
        ServerLevel level = source.getServer().getLevel(AntiVoidSystem.ANTI_VOID);
        if (level == null) return 0;
        player.teleportTo(level, 0.5D, AntiVoidSystem.FLOOR_Y, 0.5D, 0.0F, 0.0F);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.antvoid.deathgate"), false);
        return 1;
    }

    private static int resetAntiVoidIntro(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.getPersistentData().remove("AntiVoidIntroDone");
        player.getPersistentData().putInt("AntiVoidIntroPending", 1);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.antvoid.reset"), false);
        return 1;
    }

    // ------------------------------------------------------------ 露天魔晶矿簇

    /**
     * /dimensional_mana locate_cluster [range]：找最近的露天魔晶矿簇并报坐标。
     *
     * <p>r283：不再调用原版的 {@code findNearestMapStructure}。那套搜索每试一个候选区块
     * 都要读一次区块存档、够条件就直接生成区块，对这种只在荒原上出现的稀有结构来说
     * 一次就能卡住好几秒。现在改走 {@link CrystalClusterLocator}：同一张结构网格，
     * 但只在内存里算噪声，不碰存档、不生成区块。</p>
     *
     * <p>原版 {@code /locate structure dimensional_mana:open_air_mana_crystal_cluster}
     * 也能用，只是它仍然走那套慢搜索 —— 想快就用这条。</p>
     */
    private static int locateCluster(CommandSourceStack source, int radiusCells) {
        ServerLevel level = source.getLevel();
        BlockPos origin;
        try {
            origin = source.getPlayerOrException().blockPosition();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            origin = BlockPos.containing(source.getPosition());
        }
        int radius = Math.max(1, Math.min(CrystalClusterLocator.MAX_CELLS, radiusCells));
        CrystalClusterLocator.Result result = CrystalClusterLocator.find(level, origin, radius);
        if (!result.found()) {
            if (result.timedOut()) {
                source.sendFailure(Component.translatable("command.dimensional_mana.locate_cluster.timeout",
                        radius).withStyle(ChatFormatting.GRAY));
            } else {
                source.sendFailure(Component.translatable("command.dimensional_mana.locate_cluster.none",
                        radius).withStyle(ChatFormatting.GRAY));
            }
            return 0;
        }
        BlockPos found = result.pos();
        int distance = (int) Math.sqrt(origin.distSqr(found));
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.locate_cluster.found",
                found.getX(), found.getY(), found.getZ(), distance).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        // r285：顺手报一句形态。形态由种子 + 坐标定死，和真正生成时一致，
        // 玩家跑过去看到的就是这一种，四种各 25%。
        OpenAirManaCrystalClusterStructure.Variant variant =
                OpenAirManaCrystalClusterStructure.Variant.at(level.getSeed(), found.getX(), found.getZ());
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.locate_cluster.form",
                variant.displayName()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    // ------------------------------------------------------------ 魔法阵绘制台

    /**
     * /dimensional_mana magiccircle：在游戏里验证「阵盘注册」这条链路的兜底指令。
     *
     * <p>绘制台基座只有 3 像素厚，正常玩法是手持魔导调试棒右键它；万一视角没点中、
     * 或者想确认注册到底写进了哪个阵盘，用这里的指令可以直接对着前后左右的基座操作。</p>
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> magicCircleCommand() {
        return Commands.literal("magiccircle")
                .executes(context -> magicCircleHelp(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> magicCircleHelp(context.getSource())))
                .then(Commands.literal("register")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> magicCircleAct(context.getSource(), true)))
                .then(Commands.literal("status")
                        .executes(context -> magicCircleAct(context.getSource(), false)))
                .then(Commands.literal("list")
                        .executes(context -> magicCircleList(context.getSource())))
                .then(Commands.literal("unregister")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> magicCircleUnregister(context.getSource())))
                .then(Commands.literal("code")
                        .executes(context -> magicCircleCodeExport(context.getSource()))
                        .then(Commands.literal("export")
                                .executes(context -> magicCircleCodeExport(context.getSource())))
                        .then(Commands.literal("import")
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> magicCircleCodeImport(context.getSource(),
                                                StringArgumentType.getString(context, "code"))))))
                .then(Commands.literal("stopall")
                        .executes(context -> magicCircleStopAll(context.getSource())))
                .then(Commands.literal("limit")
                        .executes(context -> magicCircleLimit(context.getSource())));
    }

    /** /dimensional_mana magiccircle code [export]：把手里的阵图导成法阵码，点聊天栏那行就能复制。 */
    private static int magicCircleCodeExport(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_player"));
            return 0;
        }
        var stack = player.getMainHandItem();
        MagicCircleBlueprint blueprint = stack.getItem() instanceof EtchedSlateItem
                ? EtchedSlateItem.blueprintOf(stack)
                : MagicCircleBlueprint.of(stack);
        if (blueprint == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.code.no_blueprint"));
            return 0;
        }
        MagicCircleProjectionEvents.exportCode(player, blueprint);
        return 1;
    }

    /**
     * /dimensional_mana magiccircle code import &lt;码&gt;：把手边的一张纸 / 布 / 绸换成一份导入的阵图。
     *
     * <p>只载入结构，不扣粉末；缺什么材料会在聊天栏列出来（设计文档 9.2 的「一键备料」前半段）。
     * 注意原版聊天框最多只能敲 256 个字符，长码请用「潜行 + 右键空气」打开的导入面板粘贴。</p>
     */
    private static int magicCircleCodeImport(CommandSourceStack source, String code) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_player"));
            return 0;
        }
        return MagicCircleCodeImport.apply(player, code) ? 1 : 0;
    }

    /** /dimensional_mana magiccircle stopall：一键停掉自己所有在跑的法阵。 */
    private static int magicCircleStopAll(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_player"));
            return 0;
        }
        MagicCircleRites.stopAllAndReport(player);
        return 1;
    }

    /** /dimensional_mana magiccircle limit：我现在最多能同时开几个法阵、名额从哪来。 */
    private static int magicCircleLimit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_player"));
            return 0;
        }
        source.sendSuccess(() -> MagicCircleRites.limitSummary(player).copy()
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int magicCircleHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.magiccircle.help.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (String line : new String[]{
                "/dimensional_mana magiccircle register",
                "/dimensional_mana magiccircle status",
                "/dimensional_mana magiccircle list",
                "/dimensional_mana magiccircle unregister",
                "/dimensional_mana magiccircle code [export]",
                "/dimensional_mana magiccircle code import <码>",
                "/dimensional_mana magiccircle stopall",
                "/dimensional_mana magiccircle limit"}) {
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.magiccircle.help.hint")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** 找到玩家正在看 / 站着 / 旁边的基座；注册或报状态都从它出发。 */
    private static BlockPos magicCircleTarget(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        net.minecraft.world.phys.HitResult hit = player.pick(6.0, 0.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && MagicCircleNetworks.isBase(level, blockHit.getBlockPos())) {
            return blockHit.getBlockPos();
        }
        BlockPos under = MagicCircleNetworks.baseUnderPlayer(level, player);
        if (under != null) return under;
        return MagicCircleNetworks.nearestBase(level, player.blockPosition(), 6);
    }

    private static int magicCircleAct(CommandSourceStack source, boolean register) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_player"));
            return 0;
        }
        BlockPos base = magicCircleTarget(player);
        if (base == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_base"));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (register) {
            if (MagicCircleNetworks.registerOrReport(level, base, player, true)) {
                // 视角点不中基座的兜底：指令注册成功后直接把绘制台界面打开。
                MagicCircleDrawing.open(level, base, player);
            }
        } else {
            MagicCircleNetworks.reportStatus(level, base, player);
        }
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.magiccircle.used",
                base.getX(), base.getY(), base.getZ()).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int magicCircleList(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_player"));
            return 0;
        }
        java.util.List<MagicCircleNetwork> networks = MagicCircleNetworks.snapshot((ServerLevel) player.level());
        if (networks.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.magiccircle.list_empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.magiccircle.list_header",
                networks.size()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        int shown = 0;
        for (MagicCircleNetwork network : networks) {
            if (shown++ >= 12) break;
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.magiccircle.list_entry",
                    network.baseWidth(), network.baseHeight(),
                    network.canvasWidth(), network.canvasHeight(),
                    network.baseCount(), network.nodeCount()).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int magicCircleUnregister(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_player"));
            return 0;
        }
        BlockPos base = magicCircleTarget(player);
        if (base == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.magiccircle.no_base"));
            return 0;
        }
        MagicCircleNetwork removed = MagicCircleNetworks.unregister((ServerLevel) player.level(), base);
        if (removed == null) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.magiccircle.unregister_none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.magiccircle.unregister",
                removed.baseWidth(), removed.baseHeight()).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    /** /dimensional_mana cooldown on|off|reset|status [玩家]：开关技能冷却。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> cooldownCommand() {
        return Commands.literal("cooldown")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> cooldownStatus(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> cooldownStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> cooldownStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("on")
                        .executes(context -> setGlobalCooldown(context.getSource(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> setPlayerCooldown(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), true))))
                .then(Commands.literal("off")
                        .executes(context -> setGlobalCooldown(context.getSource(), false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> setPlayerCooldown(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), false))))
                .then(Commands.literal("reset")
                        .executes(context -> resetCooldown(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> resetCooldown(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    /** /dimensional_mana manacost on|off|status [玩家]：开关施法的魔素消耗。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> manaCostCommand() {
        return Commands.literal("manacost")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> manaCostStatus(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> manaCostStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> manaCostStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("on")
                        .executes(context -> setGlobalManaCost(context.getSource(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> setPlayerManaCost(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), true))))
                .then(Commands.literal("off")
                        .executes(context -> setGlobalManaCost(context.getSource(), false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> setPlayerManaCost(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), false))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> debugPlayerCommand(
            String name, DebugAction action) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> action.run(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> action.run(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.help.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.status")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.mana")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.battle_qi")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.max_mana")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.max_battle_qi")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.unlock")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.cooldown")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.manacost")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.spell_crit")
                .withStyle(ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.elemental_mastery")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana magiccircle register|status|list|unregister")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.locate_cluster")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.affixes")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.combat")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.usage.erosion")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana mob list|show|set|remove|reset|refresh")
                .withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.help.admin")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int showStatus(CommandSourceStack source, ServerPlayer player) {
        ManaData.Snapshot data = ManaData.get(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.status",
                player.getDisplayName(), format(data.mana()), format(data.maxMana()), data.battleQi(), data.maxBattleQi()), false);
        return 1;
    }

    /**
     * /dimensional_mana affixes [玩家]：把词条给出的实际数值逐条打在聊天栏。
     *
     * <p>这些数值全部来自真实注册的属性（见 {@link SpellAttributes}），所以这里读到的就是战斗里用的那一份：
     * 面板上显示、外部属性模组能读到、实际结算也按它算，三者一致。</p>
     */
    private static int showAffixes(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.affixes.header",
                player.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.armor_penetration"),
                format(EquipmentAffixSystem.armorPenetration(player)));
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.lifesteal_percent"),
                format(EquipmentAffixSystem.lifestealPercent(player)) + "%");
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.battle_qi_regen"),
                format(EquipmentAffixSystem.battleQiRegenBonus(player)) + "/s");
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.battle_qi_skill_power"),
                format(EquipmentAffixSystem.battleQiSkillMultiplier(player) * 100.0) + "%");
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.combo_damage_percent"),
                format(EquipmentAffixSystem.comboDamagePercent(player)) + "%/层");
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.execute_damage_percent"),
                format(EquipmentAffixSystem.executeDamagePercent(player)) + "%");
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.mikiri_damage_percent"),
                format(EquipmentAffixSystem.mikiriDamagePercent(player)) + "%");
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.parry_window_bonus"),
                Integer.toString(EquipmentAffixSystem.parryWindowBonus(player)));
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.arrow_damage_flat"),
                format(EquipmentAffixSystem.arrowDamageFlat(player)));
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.arrow_damage_percent"),
                format(EquipmentAffixSystem.arrowDamagePercent(player)) + "%");
        // 专属词条：盾牌弹反与三叉戟戳刺。
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.parry_reflect_percent"),
                format(EquipmentAffixSystem.parryReflectPercent(player)) + "%");
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.trident_thrust_percent"),
                format(EquipmentAffixSystem.tridentThrustPercent(player)) + "%");
        // 护甲值 / 盔甲韧性直接读原版属性（包含装备本身给的那部分）。
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.armor_value"),
                format(player.getAttributeValue(Attributes.ARMOR)));
        line(source, "command.dimensional_mana.affixes.line",
                Component.translatable("attribute.name.dimensional_mana.armor_toughness"),
                format(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
        EquipmentAffixSystem.elementalAddition(player).forEach((element, value) ->
                line(source, "command.dimensional_mana.affixes.line",
                        Component.translatable("attribute.name.dimensional_mana.element_addition_" + element),
                        format(value)));
        return 1;
    }

    private static void line(CommandSourceStack source, String key, Component name, String value) {
        source.sendSuccess(() -> Component.translatable(key, name, value).withStyle(ChatFormatting.GRAY), false);
    }

    // ------------------------------------------------------------ 战斗结算自检

    /** 准心 12 格内对着的生物；没有就返回 null。 */
    /**
     * /dimensional_mana combat reduction [伤害]：把自己挨打时的减伤拆成三段打在聊天栏。
     *
     * <p>顺序与实战完全一致：原版护甲值与韧性 → 原版保护附魔（EPF）→
     * {@link MojiangSteelEvents} 的魔精钢独立减伤。哪一段没吃到，数字一眼就能看出来。</p>
     */
    private static int showReduction(CommandSourceStack source, float rawDamage) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.combat.no_target"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        // 用「通用伤害」取样：它不吃护甲穿透，也不属于保护附魔的免疫类型，适合当基准。
        DamageSource sample = level.damageSources().generic();
        float armor = player.getArmorValue();
        float toughness = (float)player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float afterArmor = CombatRules.getDamageAfterAbsorb(player, rawDamage, sample, armor, toughness);
        float epf = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getDamageProtection(level, player, sample);
        float afterProtection = CombatRules.getDamageAfterMagicAbsorb(afterArmor, epf);
        double independent = MojiangSteelEvents.independentMultiplier(player);
        float finalDamage = afterProtection * (float)independent;

        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.reduction.header",
                format(rawDamage)).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.reduction.armor",
                format(armor), format(toughness), format(afterArmor),
                percent(1.0 - afterArmor / rawDamage)).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.reduction.protection",
                format(epf), format(afterProtection),
                percent(1.0 - afterProtection / Math.max(0.0001F, afterArmor)))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.reduction.independent",
                percent(1.0 - independent), format(finalDamage)).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.reduction.total",
                percent(1.0 - finalDamage / rawDamage), format(finalDamage))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int showCombat(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.combat.no_target"));
            return 0;
        }
        net.minecraft.world.phys.HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil
                .getHitResultOnViewVector(player, entity -> entity instanceof LivingEntity, 12.0);
        if (!(hit instanceof net.minecraft.world.phys.EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable("command.dimensional_mana.combat.no_target"));
            return 0;
        }
        return showCombat(source, living);
    }

    private static int showCombat(CommandSourceStack source, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable("command.dimensional_mana.combat.no_target"));
            return 0;
        }
        return showCombat(source, living);
    }

    /**
     * /dimensional_mana combat：把一只生物的斗气减伤与元素抗性打在聊天栏里。
     * 斗气减伤是第一顺位、抗性在它之后，这样能直接看到两步各自扣掉多少。
     */
    private static int showCombat(CommandSourceStack source, LivingEntity target) {
        ManaData.Snapshot data = ManaData.get(target);
        double reduction = ManaEvents.battleQiReduction(target);
        double sample = 20.0;
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.header",
                target.getDisplayName(), format(target.getHealth()), format(target.getMaxHealth()),
                data.battleQi(), data.maxBattleQi()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.battle_qi",
                percent(reduction), percent(ManaConfig.BATTLE_QI_REDUCTION_CAP.get()),
                format(sample * (1.0 - reduction))).withStyle(ChatFormatting.GOLD), false);
        double damagePerQi = ManaConfig.BASE_BATTLE_QI_COST.get() <= 0.0 ? 0.0
                : ManaConfig.DAMAGE_PER_BATTLE_QI_COST.get() / ManaConfig.BASE_BATTLE_QI_COST.get();
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.cost",
                format(damagePerQi), ManaConfig.MIN_BATTLE_QI_COST.get()).withStyle(ChatFormatting.GRAY), false);

        MobProfile profile = MobProfile.of(target.getType());
        if (profile == null) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.no_profile")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        if (!MobCombatSystem.resistancesActive(target)) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.inactive")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        java.util.List<Component> entries = new java.util.ArrayList<>();
        for (CombatElement element : CombatElement.values()) {
            resistanceEntry(entries, Component.translatable(element.translationKey()), profile.resistance(element));
        }
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.physical"),
                profile.typeResistance("physical"));
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.explosion"),
                profile.typeResistance("explosion"));
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.no_profile")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        Component joined = entries.get(0);
        for (int i = 1; i < entries.size(); i++) {
            joined = Component.literal("")
                    .append(joined).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(entries.get(i));
        }
        final Component line = joined;
        source.sendSuccess(() -> Component.empty()
                .append(Component.translatable("command.dimensional_mana.combat.resist_label")
                        .withStyle(ChatFormatting.AQUA))
                .append(line), false);
        // 空手一下只有 1 点基础伤害，护甲会先把它砍到 0.2 左右，斗气和抗性再各乘一次；
        // 最终只有零点几点时，血条/伤害数字会四舍五入成 1，很容易让人以为减伤没生效。
        // 这里把完整的结算链算给玩家看，直接确认斗气与物理抗性到底有没有参与。
        DamageSource physical = target.damageSources().mobAttack(target);
        float afterQi = (float)(1.0 - reduction);
        float armorToughness = target.getAttributes().hasAttribute(Attributes.ARMOR_TOUGHNESS)
                ? (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0.0F;
        float afterArmor = CombatRules.getDamageAfterAbsorb(target, afterQi, physical,
                target.getArmorValue(), armorToughness);
        float afterResist = afterArmor * (float)MobCombatSystem.damageMultiplier(target, physical);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.combat.simulation",
                format(afterQi), format(afterArmor), format(afterResist))
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    /** 抗性为正写成「抗性」，为负写成「易伤」，0 直接跳过。 */
    private static void resistanceEntry(java.util.List<Component> entries, Component name, double value) {
        if (value == 0.0) return;
        entries.add(Component.translatable(value > 0.0
                ? "command.dimensional_mana.combat.resist_entry"
                : "command.dimensional_mana.combat.vulnerable_entry", name, resistancePercent(Math.abs(value)))
                .withStyle(value > 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    /** 百分比先格式化成字符串再交给翻译：语言文件只认 %s。 */
    private static String percent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", ratio * 100.0);
    }

    private static String resistancePercent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", ratio * 100.0);
    }

    // ------------------------------------------------------------ 生物专配（profiles 列表）

    /**
     * /dimensional_mana mob：在游戏里看和改 special_mobs / resistances 两条配置列表。
     *
     * <p>之所以单独做一套指令，是因为游戏内配置界面的输入框只放得下 32 个字符，
     * 稍微复杂的生物一行就写不下；走指令没有字数限制，改完还会直接写回配置文件。</p>
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mobCommand() {
        return Commands.literal("mob")
                .executes(context -> mobHelp(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> mobList(context.getSource())))
                .then(Commands.literal("show")
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .executes(context -> mobShow(context.getSource(),
                                        ResourceLocationArgument.getId(context, "mob")))))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value",
                                                        DoubleArgumentType.doubleArg(-1_000_000_000.0, 1_000_000_000.0))
                                                .executes(context -> mobSet(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "mob"),
                                                        StringArgumentType.getString(context, "key"),
                                                        DoubleArgumentType.getDouble(context, "value")))))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(context -> mobRemove(context.getSource(),
                                                ResourceLocationArgument.getId(context, "mob"),
                                                StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .executes(context -> mobReset(context.getSource(),
                                        ResourceLocationArgument.getId(context, "mob")))))
                .then(Commands.literal("refresh")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> mobRefresh(context.getSource())))
                .then(Commands.literal("compact")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> mobCompact(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spawnWeightsCommand() {
        return Commands.literal("spawnweights")
                .executes(context -> spawnWeightsSummary(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> spawnWeightsList(context.getSource())))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mob", StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(SpawnConfig.WEIGHTS.keySet(), builder))
                                .then(Commands.argument("weight", IntegerArgumentType.integer(0, 1000))
                                        .executes(context -> spawnWeightSet(context.getSource(),
                                                StringArgumentType.getString(context, "mob"),
                                                IntegerArgumentType.getInteger(context, "weight"))))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> spawnWeightsReset(context.getSource())));
    }

    /** 只看被改过的那些；全都还是 100 时给一句提示，免得聊天栏刷满一屏。 */
    private static int spawnWeightsSummary(CommandSourceStack source) {
        if (!SpawnConfig.SPEC.isLoaded()) return configNotReady(source);
        // 先把「现在到底刷多少」的几个总开关摆出来，玩家不用翻 toml 也能确认。
        ServerLevel level = source.getLevel();
        long day = SpawnPacing.daysPlayed(level) + 1L;
        int percent = Math.round(SpawnPacing.intensity(level) * 100.0F);
        String curve = SpawnPacing.curveEnabled()
                ? "第 " + day + " 天，当前强度 " + percent + "%（玩满 "
                        + SpawnConfig.PACING_FULL_STRENGTH_DAYS.get() + " 天恢复正常；"
                        + "魔素浓度 > 12 的地方不受影响）"
                : "已关闭，当前强度 100%";
        source.sendSuccess(() -> Component.literal("开局节奏：" + curve).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("整体倍率：元素史莱姆 "
                        + format(SpawnPacing.elementalRate()) + "，哥布林巡逻 "
                        + format(SpawnPacing.goblinRate()) + "，荒原补刷 "
                        + format(SpawnPacing.wastelandRate()))
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("刷怪距离：元素史莱姆 "
                        + format(SpawnPacing.slimeMinDistance()) + "～" + format(SpawnPacing.slimeMaxDistance())
                        + " 格，哥布林巡逻 " + format(SpawnPacing.goblinMinDistance()) + "～"
                        + format(SpawnPacing.goblinMaxDistance()) + " 格")
                .withStyle(ChatFormatting.AQUA), false);
        int changed = 0;
        for (String key : SpawnConfig.WEIGHTS.keySet()) {
            int value = SpawnConfig.weightOf(key);
            if (value == SpawnConfig.DEFAULT_WEIGHT) continue;
            changed++;
            String name = SpawnConfig.NAMES.getOrDefault(key, key);
            source.sendSuccess(() -> Component.literal("  " + name + "（" + key + "）= " + value)
                    .withStyle(value == 0 ? ChatFormatting.RED : ChatFormatting.YELLOW), false);
        }
        if (changed == 0) {
            source.sendSuccess(() -> Component.literal("本模组 " + SpawnConfig.WEIGHTS.size()
                            + " 种生物的自然生成权重全部保持默认 100。"
                            + "用 /dimensional_mana spawnweights list 看完整表，set <生物> <权重> 改其中一个。")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            int total = changed;
            source.sendSuccess(() -> Component.literal("有 " + total + " 种生物的权重被改过（共 "
                            + SpawnConfig.WEIGHTS.size() + " 种）。100 = 默认，50 = 只刷一半，0 = 不再自然生成。")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int spawnWeightsList(CommandSourceStack source) {
        if (!SpawnConfig.SPEC.isLoaded()) return configNotReady(source);
        source.sendSuccess(() -> Component.literal("本模组生物的自然生成权重（100 = 默认，0 = 不再自然生成）：")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (String key : SpawnConfig.WEIGHTS.keySet()) {
            int value = SpawnConfig.weightOf(key);
            String name = SpawnConfig.NAMES.getOrDefault(key, key);
            source.sendSuccess(() -> Component.literal("  " + name + "（" + key + "）= " + value)
                    .withStyle(value == 0 ? ChatFormatting.RED
                            : value == SpawnConfig.DEFAULT_WEIGHT ? ChatFormatting.GRAY : ChatFormatting.YELLOW), false);
        }
        source.sendSuccess(() -> Component.literal("也可以直接编辑 config/dimensional_mana-spawns.toml 的 [spawn_weights] 段。")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int spawnWeightSet(CommandSourceStack source, String key, int weight) {
        if (!SpawnConfig.SPEC.isLoaded()) return configNotReady(source);
        if (!SpawnConfig.setWeight(key, weight)) {
            source.sendFailure(Component.literal("没有这个生物 id：" + key
                    + "。用 /dimensional_mana spawnweights list 看可用的 id（不带 dimensional_mana: 前缀）。"));
            return 0;
        }
        String name = SpawnConfig.NAMES.getOrDefault(key, key);
        source.sendSuccess(() -> Component.literal(name + " 的生成权重已改为 " + weight
                + "（100 = 默认，50 = 只刷一半，0 = 不再自然生成），立刻生效。").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * 一键恢复默认：不只是生物权重，倍率、距离、开局爬坡也一起拉回本版默认值。
     *
     * <p>玩家手改过 toml 又改乱了、或者从旧版本升上来想统一到新默认值时，用这一条最省事。</p>
     */
    private static int spawnWeightsReset(CommandSourceStack source) {
        if (!SpawnConfig.SPEC.isLoaded()) return configNotReady(source);
        int total = SpawnConfig.resetAll();
        source.sendSuccess(() -> Component.literal("已恢复默认：" + SpawnConfig.WEIGHTS.size()
                        + " 种生物权重 100、三套总倍率 1.0、史莱姆 "
                        + format(SpawnConfig.DEFAULT_SLIME_MIN) + "～" + format(SpawnConfig.DEFAULT_SLIME_MAX)
                        + " 格、哥布林 " + format(SpawnConfig.DEFAULT_GOBLIN_MIN) + "～"
                        + format(SpawnConfig.DEFAULT_GOBLIN_MAX) + " 格、开局第 "
                        + SpawnConfig.DEFAULT_PACING_FULL_DAYS + " 天满强度。共 " + total + " 项，立刻生效。")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int mobHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.help.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (String line : new String[]{
                "/dimensional_mana mob list",
                "/dimensional_mana mob show <生物>",
                "/dimensional_mana mob set <生物> <键> <数值>",
                "/dimensional_mana mob remove <生物> <键>",
                "/dimensional_mana mob reset <生物>",
                "/dimensional_mana mob refresh",
                "/dimensional_mana mob compact"}) {
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.help.keys")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int mobList(CommandSourceStack source) {
        java.util.Set<String> ids = MobProfile.configuredIds();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.list_empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        String joined = String.join(", ", ids);
        int size = ids.size();
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.list", size, joined)
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int mobShow(CommandSourceStack source, ResourceLocation id) {
        String mobId = MobProfile.normalizeId(id.toString());
        MobProfile profile = MobProfile.byId(mobId);
        if (profile == null) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.no_profile", mobId)
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.title", mobId)
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.stats",
                statValue(profile.mana()), statValue(profile.battleQi()),
                statValue(profile.manaRegen()), statValue(profile.battleQiRegen()))
                .withStyle(ChatFormatting.AQUA), false);
        java.util.List<Component> entries = new java.util.ArrayList<>();
        for (CombatElement element : CombatElement.values()) {
            resistanceEntry(entries, Component.translatable(element.translationKey()), profile.resistance(element));
        }
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.physical"),
                profile.typeResistance("physical"));
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.explosion"),
                profile.typeResistance("explosion"));
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.magic_crit"),
                profile.special("magic_crit"));
        resistanceEntry(entries, Component.translatable("hud.dimensional_mana.appraisal.trait.melt"),
                profile.special("melt"));
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.resist_none")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        Component joined = entries.get(0);
        for (int i = 1; i < entries.size(); i++) {
            joined = Component.literal("")
                    .append(joined).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(entries.get(i));
        }
        final Component line = joined;
        source.sendSuccess(() -> Component.empty()
                .append(Component.translatable("command.dimensional_mana.mob.resist_label")
                        .withStyle(ChatFormatting.AQUA))
                .append(line), false);
        return 1;
    }

    /** 配置里没写的值显示成「按难度公式」，免得玩家以为被设成了 0。 */
    private static Component statValue(Double value) {
        return value == null
                ? Component.translatable("command.dimensional_mana.mob.value_default")
                : Component.literal(format(value));
    }

    private static int mobSet(CommandSourceStack source, ResourceLocation id, String key, double value) {
        if (!MobCombatConfig.SPEC.isLoaded()) return configNotReady(source);
        if (!MobProfile.knownKey(key)) {
            source.sendFailure(Component.translatable("command.dimensional_mana.mob.bad_key", key));
            return 0;
        }
        String mobId = MobProfile.normalizeId(id.toString());
        boolean stat = MobProfile.isStatKey(key);
        String shortKey = MobProfile.shortKey(key);
        MobCombatConfig.writeMobValue(stat, mobId, shortKey, value);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.set",
                mobId, shortKey, format(value)).withStyle(ChatFormatting.GREEN), true);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.set_hint")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int mobRemove(CommandSourceStack source, ResourceLocation id, String key) {
        if (!MobCombatConfig.SPEC.isLoaded()) return configNotReady(source);
        if (!MobProfile.knownKey(key)) {
            source.sendFailure(Component.translatable("command.dimensional_mana.mob.bad_key", key));
            return 0;
        }
        String mobId = MobProfile.normalizeId(id.toString());
        boolean stat = MobProfile.isStatKey(key);
        String shortKey = MobProfile.shortKey(key);
        boolean removed = MobCombatConfig.removeMobValue(stat, mobId, shortKey);
        source.sendSuccess(() -> Component.translatable(removed
                ? "command.dimensional_mana.mob.remove"
                : "command.dimensional_mana.mob.remove_none", mobId, shortKey)
                .withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return removed ? 1 : 0;
    }

    private static int mobReset(CommandSourceStack source, ResourceLocation id) {
        if (!MobCombatConfig.SPEC.isLoaded()) return configNotReady(source);
        String mobId = MobProfile.normalizeId(id.toString());
        MobCombatConfig.resetMob(mobId);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.reset", mobId)
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    /** 配置改完之后让已经刷出来的生物立刻按新数值重算，不用重开存档。 */
    private static int mobRefresh(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int count = 0;
        for (Object candidate : level.getAllEntities()) {
            if (!(candidate instanceof Mob mob)) continue;
            if (!MobCombatSystem.isManagedMob(mob)) continue;
            mob.getPersistentData().remove(ManaData.ROOT_TAG);
            ManaData.init(mob);
            count++;
        }
        int refreshed = count;
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mob.refresh", refreshed)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int configNotReady(CommandSourceStack source) {
        source.sendFailure(Component.translatable("command.dimensional_mana.mob.not_loaded"));
        return 0;
    }

    /** 老配置里的超长行在游戏内配置界面里改不动，这里一次性拆成短行并保存。 */
    private static int mobCompact(CommandSourceStack source) {
        if (!MobCombatConfig.SPEC.isLoaded()) return configNotReady(source);
        int lists = MobCombatConfig.compactLists();
        source.sendSuccess(() -> Component.translatable(lists > 0
                ? "command.dimensional_mana.mob.compact"
                : "command.dimensional_mana.mob.compact_none", lists)
                .withStyle(lists > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }

    private static int setMana(CommandSourceStack source, ServerPlayer player, double value) {
        ManaData.setMana(player, value, true);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mana.success",
                player.getDisplayName(), format(ManaData.get(player).mana())), true);
        return 1;
    }

    private static int setBattleQi(CommandSourceStack source, ServerPlayer player, int value) {
        ManaData.setBattleQi(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.battle_qi.success",
                player.getDisplayName(), ManaData.get(player).battleQi()), true);
        return 1;
    }

    private static int setMaxMana(CommandSourceStack source, ServerPlayer player, double value) {
        ManaData.setMaxMana(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.max_mana.success",
                player.getDisplayName(), format(value)), true);
        return 1;
    }

    private static int setMaxBattleQi(CommandSourceStack source, ServerPlayer player, int value) {
        ManaData.setMaxBattleQi(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.max_battle_qi.success",
                player.getDisplayName(), ManaData.get(player).maxBattleQi()), true);
        return 1;
    }

    private static int unlockSpells(CommandSourceStack source, ServerPlayer player) {
        SpellCasting.clearTransientState(player);
        SpellData.unlockAll(player);
        return debugSuccess(source, player, "command.dimensional_mana.unlock_spells.success");
    }

    private static int unlockElements(CommandSourceStack source, ServerPlayer player) {
        ElementTalentData.unlockAll(player);
        return debugSuccess(source, player, "command.dimensional_mana.unlock_elements.success");
    }

    private static int maxSkills(CommandSourceStack source, ServerPlayer player) {
        MagicProgression.maxAllSkills(player);
        ProgressSyncPayload.send(player);
        sync(player);
        return debugSuccess(source, player, "command.dimensional_mana.max_skills.success");
    }

    private static int unlockAll(CommandSourceStack source, ServerPlayer player) {
        ElementTalentData.unlockAll(player);
        SpellCasting.clearTransientState(player);
        SpellData.unlockAll(player);
        MagicProgression.maxAllSkills(player);
        // 斗气侧：等级、六项技能全满，三项能力解锁并放好轮盘槽。
        // 必须在补斗气之前做——「斗气强化」满级会把斗气上限抬高，后补才能补到新上限。
        QiProgression.maxAll(player);
        ManaData.setMana(player, ManaData.get(player).maxMana(), false);
        ManaData.setBattleQi(player, ManaData.get(player).maxBattleQi());
        ProgressSyncPayload.send(player);
        sync(player);
        return debugSuccess(source, player, "command.dimensional_mana.unlock_all.success");
    }

    private static int setGlobalCooldown(CommandSourceStack source, boolean enabled) {
        ManaCheats.setGlobalCooldown(enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "command.dimensional_mana.cooldown.global_on"
                : "command.dimensional_mana.cooldown.global_off").withStyle(ChatFormatting.AQUA), true);
        resyncAll(source);
        return 1;
    }

    private static int setPlayerCooldown(CommandSourceStack source, ServerPlayer player, boolean enabled) {
        ManaCheats.setPlayerCooldown(player, enabled);
        SpellSyncPayload.send(player);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "command.dimensional_mana.cooldown.player_on"
                : "command.dimensional_mana.cooldown.player_off", player.getDisplayName())
                .withStyle(ChatFormatting.AQUA), true);
        if (enabled && !ManaCheats.globalCooldown()) hintGlobalOff(source);
        return 1;
    }

    private static int resetCooldown(CommandSourceStack source, ServerPlayer player) {
        SpellCasting.clearCooldowns(player);
        SpellSyncPayload.send(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.cooldown.reset",
                player.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int cooldownStatus(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.cooldown.status",
                player.getDisplayName(), toggle(ManaCheats.cooldownEnabled(player)),
                toggle(ManaCheats.globalCooldown())).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int setGlobalManaCost(CommandSourceStack source, boolean enabled) {
        ManaCheats.setGlobalManaCost(enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "command.dimensional_mana.manacost.global_on"
                : "command.dimensional_mana.manacost.global_off").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int setPlayerManaCost(CommandSourceStack source, ServerPlayer player, boolean enabled) {
        ManaCheats.setPlayerManaCost(player, enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "command.dimensional_mana.manacost.player_on"
                : "command.dimensional_mana.manacost.player_off", player.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        if (enabled && !ManaCheats.globalManaCost()) hintGlobalOff(source);
        return 1;
    }

    /** 全局还是关闭状态时提醒一句，避免管理员以为单开没生效。 */
    private static void hintGlobalOff(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.hint.global_off")
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static int manaCostStatus(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.manacost.status",
                player.getDisplayName(), toggle(ManaCheats.manaCostEnabled(player)),
                toggle(ManaCheats.globalManaCost())).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    /** /dimensional_mana spell_crit set|clear|status [玩家]：作弊改法术暴击率，方便验证暴击表现。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spellCritCommand() {
        return Commands.literal("spell_crit")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> spellCritStatus(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> spellCritStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> spellCritStatus(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(context -> setSpellCrit(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "percent"))))))
                .then(Commands.literal("clear")
                        .executes(context -> clearSpellCrit(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> clearSpellCrit(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private static int setSpellCrit(CommandSourceStack source, ServerPlayer player, double percent) {
        SpellAttributes.setCheatCritChance(player, percent / 100.0);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.spell_crit.set",
                player.getDisplayName(), critPercent(SpellAttributes.critChance(player)))
                .withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int clearSpellCrit(CommandSourceStack source, ServerPlayer player) {
        boolean removed = SpellAttributes.clearCheatCritChance(player);
        source.sendSuccess(() -> Component.translatable(removed
                ? "command.dimensional_mana.spell_crit.clear"
                : "command.dimensional_mana.spell_crit.clear_none", player.getDisplayName())
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int spellCritStatus(CommandSourceStack source, ServerPlayer player) {
        double total = SpellAttributes.critChance(player);
        double cheat = SpellAttributes.cheatCritChance(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.spell_crit.status",
                player.getDisplayName(), critPercent(total), critPercent(total - cheat), critPercent(cheat))
                .withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> elementalMasteryCommand() {
        return Commands.literal("elemental_mastery")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> elementalMasteryStatus(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> elementalMasteryStatus(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> elementalMasteryStatus(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1_000_000.0F))
                                .executes(context -> setElementalMastery(context.getSource(), context.getSource().getPlayerOrException(),
                                        FloatArgumentType.getFloat(context, "value"))))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1_000_000.0F))
                                        .executes(context -> setElementalMastery(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "value"))))))
                .then(Commands.literal("clear")
                        .executes(context -> clearElementalMastery(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> clearElementalMastery(context.getSource(), EntityArgument.getPlayer(context, "player")))));
    }

    private static int setElementalMastery(CommandSourceStack source, ServerPlayer player, float value) {
        ElementMastery.setCheat(player, value);
        ProgressSyncPayload.send(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.elemental_mastery.set",
                player.getDisplayName(), format(value), ElementMastery.level(ElementMastery.get(player)))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int clearElementalMastery(CommandSourceStack source, ServerPlayer player) {
        boolean removed = ElementMastery.clearCheat(player);
        ProgressSyncPayload.send(player);
        source.sendSuccess(() -> Component.translatable(removed
                        ? "command.dimensional_mana.elemental_mastery.clear"
                        : "command.dimensional_mana.elemental_mastery.clear_none",
                player.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int elementalMasteryStatus(CommandSourceStack source, ServerPlayer player) {
        float value = ElementMastery.get(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.elemental_mastery.status",
                player.getDisplayName(), format(value), ElementMastery.level(value), format(ElementMastery.cheatValue(player)))
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    /** 百分比先格式化成字符串再交给翻译：语言文件只认 %s，写 %.1f 会让整行退回未替换的原文。 */
    private static String critPercent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    private static Component toggle(boolean enabled) {
        return Component.translatable(enabled
                ? "command.dimensional_mana.toggle.on" : "command.dimensional_mana.toggle.off");
    }

    /** 全局开关改动后同步所有在线玩家的 HUD，避免冷却数字残留。 */
    private static void resyncAll(CommandSourceStack source) {
        if (source.getServer() == null) return;
        for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) SpellSyncPayload.send(online);
    }

    private static int debugSuccess(CommandSourceStack source, ServerPlayer player, String key) {
        source.sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
        return 1;
    }

    private static void sync(ServerPlayer player) {
        ManaData.Snapshot data = ManaData.get(player);
        PacketDistributor.sendToPlayer(player, new ManaSyncPayload(
                data.mana(), data.maxMana(), data.battleQi(), data.maxBattleQi(), data.exhaustionTicks() > 0,
                ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
    }

    @FunctionalInterface
    private interface DebugAction { int run(CommandSourceStack source, ServerPlayer player); }

    /**
     * /dimensional_mana erosion：不带参数时看自己，看着生物时看那只生物。
     *
     * <p>为什么要这条命令：魔蚀的悬停说明只有玩家本人看得到，生物身上叠了多少层
     * 在游戏里没有任何界面能读，想验证「叠层真的加伤害了」就只能把它打出来。</p>
     */
    private static int showErosion(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.dimensional_mana.erosion.no_target"));
            return 0;
        }
        net.minecraft.world.phys.HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil
                .getHitResultOnViewVector(player, entity -> entity instanceof LivingEntity, 12.0);
        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living) {
            return showErosion(source, living);
        }
        return showErosion(source, player);
    }

    private static int showErosion(CommandSourceStack source, net.minecraft.world.entity.Entity entity) {
        if (entity instanceof LivingEntity living) return showErosion(source, living);
        source.sendFailure(Component.translatable("command.dimensional_mana.erosion.no_target"));
        return 0;
    }

    private static int showErosion(CommandSourceStack source, LivingEntity target) {
        int layers = MagicErosionEffect.layersOf(target);
        int tier = MagicErosionEffect.tierOf(layers);
        float damage = MagicErosionEffect.damageOf(layers);
        boolean active = target.hasEffect(GeneratedMod.MAGIC_EROSION);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.erosion.header",
                target.getDisplayName(), group(layers), group(MagicErosionEffect.MAX_LAYERS), group(tier))
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.erosion.damage",
                MagicErosionEffect.damageText(damage)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        int toNext = MagicErosionEffect.layersToNextTier(layers);
        if (toNext <= 0) {
            source.sendSuccess(() -> Component.translatable("effect.dimensional_mana.magic_erosion.max")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            final int need = toNext;
            final String nextDamage = MagicErosionEffect.damageText(MagicErosionEffect.damageOf(layers + need));
            source.sendSuccess(() -> Component.translatable("command.dimensional_mana.erosion.next",
                    group(need), nextDamage).withStyle(ChatFormatting.GRAY), false);
        }
        source.sendSuccess(() -> Component.translatable(active
                ? "command.dimensional_mana.erosion.active"
                : "command.dimensional_mana.erosion.decaying").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** 千分位：层数动辄六位数，不加逗号看不出量级。 */
    private static String group(int value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.format("%.0f", value) : String.format("%.2f", value);
    }

    private ManaCommands() {}
}
