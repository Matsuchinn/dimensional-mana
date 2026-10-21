package cn.blockforge.generated.minecraft1211n422a;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
                .then(Commands.literal("mana")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1_000_000_000.0))
                                        .executes(context -> setMana(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("aura")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100_000_000))
                                        .executes(context -> setAura(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(Commands.literal("max_mana")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 100_000_000.0))
                                        .executes(context -> setMaxMana(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("max_aura")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 100_000_000))
                                        .executes(context -> setMaxAura(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(debugPlayerCommand("unlock_spells", ManaCommands::unlockSpells))
                .then(debugPlayerCommand("unlock_elements", ManaCommands::unlockElements))
                .then(debugPlayerCommand("max_skills", ManaCommands::maxSkills))
                .then(debugPlayerCommand("unlock_all", ManaCommands::unlockAll))
                .then(cooldownCommand())
                .then(manaCostCommand())
                .then(spellCritCommand()));
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
        source.sendSuccess(() -> Component.literal("/dimensional_mana status [玩家]")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana mana <玩家> <数值>")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana aura <玩家> <数值>")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana max_mana <玩家> <数值>")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana max_aura <玩家> <数值>")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana unlock_spells|unlock_elements|max_skills|unlock_all [玩家]")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana cooldown on|off|reset|status [玩家]")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana manacost on|off|status [玩家]")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.literal("/dimensional_mana spell_crit set|clear|status [玩家] [百分比]")
                .withStyle(ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.help.admin")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int showStatus(CommandSourceStack source, ServerPlayer player) {
        ManaData.Snapshot data = ManaData.get(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.status",
                player.getDisplayName(), format(data.mana()), format(data.maxMana()), data.aura(), data.maxAura()), false);
        return 1;
    }

    private static int setMana(CommandSourceStack source, ServerPlayer player, double value) {
        ManaData.setMana(player, value, true);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.mana.success",
                player.getDisplayName(), format(ManaData.get(player).mana())), true);
        return 1;
    }

    private static int setAura(CommandSourceStack source, ServerPlayer player, int value) {
        ManaData.setAura(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.aura.success",
                player.getDisplayName(), ManaData.get(player).aura()), true);
        return 1;
    }

    private static int setMaxMana(CommandSourceStack source, ServerPlayer player, double value) {
        ManaData.setMaxMana(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.max_mana.success",
                player.getDisplayName(), format(value)), true);
        return 1;
    }

    private static int setMaxAura(CommandSourceStack source, ServerPlayer player, int value) {
        ManaData.setMaxAura(player, value);
        sync(player);
        source.sendSuccess(() -> Component.translatable("command.dimensional_mana.max_aura.success",
                player.getDisplayName(), ManaData.get(player).maxAura()), true);
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
        ManaData.setMana(player, ManaData.get(player).maxMana(), false);
        ManaData.setAura(player, ManaData.get(player).maxAura());
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
                data.mana(), data.maxMana(), data.aura(), data.maxAura(), data.exhaustionTicks() > 0,
                ManaConfig.OVERCHARGE_SAFE_RATIO.get()));
    }

    @FunctionalInterface
    private interface DebugAction { int run(CommandSourceStack source, ServerPlayer player); }

    private static String format(double value) {
        return value == Math.rint(value) ? String.format("%.0f", value) : String.format("%.2f", value);
    }

    private ManaCommands() {}
}
